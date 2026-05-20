# TH1 — File Thumbnail SPI

Inline thumbnail generation for FileContainer payloads.
Provides a backend-rendered preview image for every stored file,
accessible via a v2 REST endpoint and surfaced in `FilesTable`.

---

## 1. Scope

| Item | Decision |
|---|---|
| Endpoint | `GET /v2/file-containers/{appId}/payload/{oid}/thumbnail?size=64\|200\|400` |
| Output | `image/png` (always PNG regardless of input format) |
| Size buckets | 64 px / 200 px / 400 px (longest side); arbitrary values → 400 |
| Cache | Filesystem; `{cache-dir}/{appId}/{oid}-{size}.png` |
| TTL | Configurable (default 72 h); lazy on access + `@Scheduled` nightly sweep |
| Orphan eviction | `deleteFile()` and `deleteContainer()` call `ThumbnailCache.evict*()` |
| Generation | Bounded `ExecutorService`; configurable worker count (default 4) |
| Timeout | Configurable (default 5 000 ms); 503 + `Retry-After: 5` on breach |
| On 404 / unsupported | HTTP 404 → frontend falls back to file-type icon (no broken image) |

---

## 2. Config keys (all deploy-time; `:ThumbnailConfig` admin-runtime deferred to TH1b)

```properties
shepard.thumbnail.cache-dir=/var/lib/shepard/thumbnail-cache
shepard.thumbnail.ttl-hours=72
shepard.thumbnail.max-cache-mb=2048
shepard.thumbnail.timeout-ms=5000
shepard.thumbnail.workers=4
shepard.thumbnail.sizes=64,200,400
```

---

## 3. SPI interface

```java
// de.dlr.shepard.data.file.thumbnail.ThumbnailProvider
public interface ThumbnailProvider {
    /** MIME types this provider handles, e.g. "image/png", "image/jpeg". */
    Set<String> supportedMimeTypes();
    /** File extensions this provider handles (lower-case, no dot), fallback when MIME unknown. */
    Set<String> supportedExtensions();
    /** Generate a square-ish thumbnail of longest-side `sizePx`. Returns PNG bytes. */
    byte[] generate(InputStream fileBytes, String filename, int sizePx) throws IOException;
}
```

CDI discovery: `@ApplicationScoped` beans implementing `ThumbnailProvider` are injected via
`Instance<ThumbnailProvider>`. Plugins provide additional implementations on the classpath.

---

## 4. Built-in providers (TH1a, in-tree)

| Class | Handles | Notes |
|---|---|---|
| `RasterImageThumbnailProvider` | png, jpeg, jpg, gif, bmp, webp | Java ImageIO; scale with BILINEAR |
| `TextThumbnailProvider` | txt, md, yml, yaml, json, toml, csv, log | AWT monospace font render of first ~20 lines; white bg |

Future plugin providers (TH1c+):
- `PdfThumbnailProvider` (PDFBox) — first page → PNG
- `HdfThumbnailProvider` (JHDF) — dataset shape heatmap
- `CadThumbnailProvider` (jCAD/OCCT) — mesh wireframe

---

## 5. Cache layout

```
{cache-dir}/
  {containerAppId}/
    {oid}-64.png
    {oid}-200.png
    {oid}-400.png
```

TTL: compare `Files.getLastModifiedTime()` against `now - ttl`. Expired entries regenerated
on next request. Nightly `@Scheduled` sweep removes all entries older than TTL.

Max-cache-mb: sweep also evicts LRU entries (by mtime) when total size exceeds cap.

---

## 6. Generation queue

`ThumbnailGenerationQueue` wraps a bounded `ThreadPoolExecutor` (core = max = `workers`).
The REST handler submits a `Callable<byte[]>` and blocks with `Future.get(timeout, MILLISECONDS)`.
On `TimeoutException`: HTTP 503, `Retry-After: 5`.
On `RejectedExecutionException`: HTTP 503, `Retry-After: 10` (queue full).

---

## 7. Endpoint contract

```
GET /v2/file-containers/{appId}/payload/{oid}/thumbnail?size=200
Authorization: Bearer <token>

200 OK
Content-Type: image/png
Cache-Control: public, max-age=3600

404 Not Found          — oid unknown or provider returned null (unsupported type)
400 Bad Request        — size not in {64, 200, 400}
503 Service Unavailable — generation timed out or queue full
```

Frontend: on 404, render the file-type icon chip instead of a broken image.

---

## 8. Orphan eviction hooks

`FileContainerService.deleteFile(containerId, oid)`
→ after removing from Neo4j: `thumbnailCache.evict(container.getAppId(), oid)`

`FileContainerService.deleteContainer(containerId)`
→ after Neo4j delete: `thumbnailCache.evictContainer(container.getAppId())`

`ThumbnailCache.evict(appId, oid)` deletes `{cache-dir}/{appId}/{oid}-*.png`.
`ThumbnailCache.evictContainer(appId)` deletes the entire `{appId}/` directory.

---

## 9. Frontend integration

- **`useFetchFileThumbnail.ts`** composable — calls v2 thumbnail endpoint with auth; returns `blobUrl | null`.
- **`FilesTable.vue`** — new thumbnail column (first column, 60 px wide); lazy-loads on row appear; shows Vuetify skeleton loader while pending; falls back to `mdi-file-outline` icon on 404/503.
- Extension points in `shepardFileMappingUtil.ts` are not changed — thumbnail availability is determined by the backend 404, not the frontend type enum.

---

## 10. Task ledger (TH1 series)

| ID | Description | Status |
|---|---|---|
| TH1a-1 | `ThumbnailProvider.java` interface | ⬜ |
| TH1a-2 | `RasterImageThumbnailProvider.java` | ⬜ |
| TH1a-3 | `TextThumbnailProvider.java` | ⬜ |
| TH1a-4 | `ThumbnailCache.java` (filesystem, TTL, sweep) | ⬜ |
| TH1a-5 | `ThumbnailGenerationQueue.java` (bounded executor) | ⬜ |
| TH1a-6 | `ThumbnailService.java` (orchestrates provider → queue → cache) | ⬜ |
| TH1a-7 | `ThumbnailRest.java` (GET /v2/...) | ⬜ |
| TH1a-8 | Orphan eviction in `FileContainerService` | ⬜ |
| TH1a-9 | Config keys in `application.properties` | ⬜ |
| TH1b-1 | Frontend `useFetchFileThumbnail.ts` | ⬜ |
| TH1b-2 | Frontend `FilesTable.vue` thumbnail column | ⬜ |
| TH1c | JUnit tests (ThumbnailService, Cache, RasterProvider) | ⬜ |
| TH1d | Vitest tests (useFetchFileThumbnail) | ⬜ |
| TH1e | Docs (aidocs/34, aidocs/42, aidocs/44, docs/reference) | ⬜ |
