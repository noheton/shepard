---
stage: feature-defined
last-stage-change: 2026-05-24
---

# File-storage routing audit — Garage vs Mongo GridFS, 2026-05-24

## TL;DR

- **Garage active? Yes.** Live registry log: `FileStorageRegistry: active storage 's3' (...S3FileStorage_ClientProxy); 2 adapter(s) discovered.` Mongo holds **~898 MB of file payloads in `fs.chunks`**; Garage holds **13.6 MiB / 24 objects**.
- **Root cause of routing miss:** the FS1a SPI was wired into `FileContainerService` (the v1 `POST /shepard/api/fileContainers/{id}/payload` path), but **not into `SingletonFileReferenceService`** (the `POST /v2/files` singleton path that the MFFD v15 importer uses) **and not into `FileBundleReferenceRest.uploadIntoGroup`** (the bundle path). Both call `fileService.createFile(...)` directly — bypassing the registry and writing straight to GridFS `fs.chunks` regardless of `shepard.storage.provider`. 11788 of 11892 `:ShepardFile` rows have `providerId IS NULL` and were created via the singleton path; the latest one was written **two minutes before this audit** at 14:12:10Z.
- **Secondary cause:** `FileMigrationService.CYPHER_FETCH` only matches `(:FileContainer)-[:file_in_container]->(:ShepardFile)`. The 11788 singleton-held files would be **invisible to the existing FS1e migration** even if triggered.
- **Migration shape:** one-shot copy job that walks `:ShepardFile {providerId IS NULL OR providerId='gridfs'}`, streams each from GridFS to Garage (preserving oid as S3 object key), stamps `providerId='s3'` on the Neo4j row, verifies the copy, then deletes the GridFS row. Safe because `FileContainerService.storageForRow()` (line 578) already routes per-row, so dual-provider state is healthy indefinitely.

## Substrate snapshot — 2026-05-24 14:11Z

| Store | Object | Count | Size | Note |
|---|---|---|---|---|
| **MongoDB** `fs.files` (default GridFS bucket) | files | 11852 | 1.30 MB (metadata) | The "real" bucket where ALL upload bytes go — `FileService.createBucket()` returns `GridFSBuckets.create(mongoDatabase)` with no bucket name, so everything lands in the default `fs` bucket. |
| **MongoDB** `fs.chunks` (default GridFS bucket) | chunks | 12524 | **897.7 MB** | The 890 MB the operator was looking for. Each file averages ~1 chunk (default 1 MiB chunk size) plus a sub-chunk remainder. |
| **MongoDB** `_shepard_files` | metadata docs | 11778 | 2.07 MB | The `SHARED_FILES_NAMESPACE` (per `SingletonFileReferenceService:67`) — per-singleton bookkeeping. **Matches 11788 null-providerId rows almost exactly** (within 10 — likely in-flight uploads). |
| **MongoDB** `FileContainer<UUID>` (6 collections) | metadata docs | 71 (sum) | 12 kB (sum) | The per-container bookkeeping collections. **Tiny** — historical FileContainer-route uploads only. |
| **Mongo** `userAvatars` | avatars | 2 | 152 kB | Out of scope (different code path). |
| **Garage** `shepard-files` bucket | objects | 24 | 13.6 MiB | The "FS1c-shipped, FS1f-shipped, but only routed-through" tip of the iceberg. |
| **Neo4j** `:ShepardFile` | rows | 11892 | (see breakdown) | |
| - by `providerId` | `<null>` | 11788 | 188 MB (sum `fileSize`) | All held by `:SingletonFileReference` (per query `MATCH (anything)-->(f:ShepardFile WHERE f.providerId IS NULL)`). |
| - by `providerId` | `gridfs` | 71 | 683 MB (sum `fileSize`) | V34-backfilled + `FileContainerService` writes. |
| - by `providerId` | `s3` | 33 | 13 MB (sum `fileSize`) | `FileContainerService` writes since `SHEPARD_STORAGE_PROVIDER=s3` took effect. |

**Total `fileSize` discrepancy:** Neo4j `sum(fileSize)` for null/gridfs/s3 = 188 + 683 + 13 = **884 MB**; Mongo `fs.chunks.stats().size` = **898 MB**. The ~14 MB gap is presumably v15 importer test runs that wrote bytes but didn't complete the Neo4j-side bookkeeping (orphan chunks). Out of scope for this audit; flagged as a candidate for SM1 orphan-collect (task #74).

## Active-path trace — REST → adapter → bytes-out

Three live write paths, only one of which honours the registry today:

### Path A — `POST /shepard/api/fileContainers/{id}/payload` (v1 container upload)

`FileRest` → `FileContainerService.createFile(fileContainerId, fileName, inputStream)` → **`fileStorageRegistry.requireActive()`** (`FileContainerService.java:248`) → branch on `adapter.id()`:
- `gridfs` → `fileService.createFileWithSha256(mongoId, fileName, is)` → `GridFSBuckets.create(mongoDatabase).uploadFromStream()` → `fs.chunks`. Then `result.setProviderId("gridfs")`, persisted via `fileContainerDAO.createOrUpdate(fileContainer)`.
- `s3` → **`throw new ServiceUnavailableException("Direct upload is not supported for provider 's3'. Use the presigned upload URL endpoint: POST /v2/file-containers/{containerAppId}/upload-url")`** (`FileContainerService.java:274-278`). v1 callers get a 503; they must use the v2 presigned flow. **This is the intended fix-forward for the v1 surface, but it does break upstream wire compat (a v5 client that POSTs to `/shepard/api/fileContainers/.../payload` with `SHEPARD_STORAGE_PROVIDER=s3` set gets 503).** Flagged.

**Read path** mirrors this — `FileContainerService.getFile()` calls `storageForRow(providerId)` (line 578) and routes by the stored `providerId`, falling back to `requireActive()` for null rows. **This means existing GridFS-held rows continue to read correctly** once dual-provider mode is up.

### Path B — `POST /v2/files?parentDataObjectAppId=…&name=…` (singleton file reference)

`FileReferenceV2Rest` → `SingletonFileReferenceService.createSingleton(dataObjectAppId, name, filename, payload, ...)` → **`fileService.createFile(SHARED_FILES_NAMESPACE, filename, payload)`** (`SingletonFileReferenceService.java:149`) → `GridFSBuckets.create(mongoDatabase).uploadFromStream()` → `fs.chunks`.

**No registry consultation. No `providerId` stamping.** `SHARED_FILES_NAMESPACE = "_shepard_files"` (line 67) is hard-coded.

**This is the path the v15 MFFD importer uses** (`examples/mffd-showcase/scripts/mffd-import-v15.py:1484`: `url_v2 = f"{self._base}/v2/files"`). That's why the entire MFFD ingest landed in Mongo despite `SHEPARD_STORAGE_PROVIDER=s3` being set.

**Read path** (line 262): `fileService.getPayload(SHARED_FILES_NAMESPACE, file.getOid())` — also no registry. Direct GridFS read.

**Delete path** (line 234): `fileService.deleteFile(SHARED_FILES_NAMESPACE, fileOid)` — also no registry.

### Path C — `POST /v2/bundles/{bundleAppId}/groups/{groupAppId}/files` (bundle upload)

`FileBundleReferenceRest.uploadIntoGroup` → **`fileService.createFile(bundle.getFileContainer().getMongoId(), upload.fileName(), is)`** (`FileBundleReferenceRest.java:452`) → GridFS direct.

**No registry consultation. No `providerId` stamping.** Read paths in `FileBundleReferenceService.java:327` and `:370` also bypass.

### Path D — `POST /v2/file-containers/{containerAppId}/upload-url` (v2 presigned)

`FileContainerPresignedUrlRest` → `FileContainerService.requestUploadUrl()` → `fileStorageRegistry.requireActive()` → `adapter.presignedUploadUrl(...)`. Returns the presigned PUT URL.

Then client `PUT`s bytes to Garage directly. Then client `POST /v2/file-containers/{containerAppId}/upload-url/commit` → `FileContainerService.commitUploadedFile()` → creates a `ShepardFile` row with `providerId = adapter.id()` (`FileContainerService.java:466-468`). **Honours the registry. Used today only for the 33 s3-stamped rows.**

## Why Garage didn't pick up the load — root cause with evidence

1. **The v15 MFFD importer is hard-coded to use the `/v2/files` singleton path** (`mffd-import-v15.py:1484`), with `/shepard/api/.../fileReferences` as v1 fallback (line 1509). Both of those routes funnel into `SingletonFileReferenceService.createSingleton()` → `fileService.createFile(SHARED_FILES_NAMESPACE, ...)`. Neither calls the registry.

2. **`SingletonFileReferenceService.createSingleton()` calls `fileService.createFile(...)` directly with `SHARED_FILES_NAMESPACE` hard-coded** (line 149). The same service's read path (line 262) and delete path (line 234) do the same. The FS1a refactor changed `FileContainerService` but missed this sibling service. The class-level Javadoc (lines 30-50) describes the GridFS-only contract — it's documented behaviour, just not the behaviour anyone wanted after activating S3.

3. **`FileBundleReferenceRest.uploadIntoGroup` calls `fileService.createFile(...)` directly** (line 452) — same FS1a-missed shape.

4. **`FileMigrationService.CYPHER_FETCH` only walks `(:FileContainer)-[:file_in_container]->(:ShepardFile)`** (lines 79-84). Singleton-held files (held by `:SingletonFileReference`, an `:AbstractReference` not a `:FileContainer`) and bundle-held files would never be picked up by an FS1e migration run. The migration mechanism that ships in the backend cannot today touch the 11788 rows that need moving.

5. **V34 backfill stamps `providerId='gridfs'` only at migration time**, not at upload time on `SingletonFileReferenceService.createSingleton()`. After V34 ran, every subsequent singleton upload created a `:ShepardFile` row with `providerId=null` again — because the create path doesn't set it. **This is why the null-providerId set keeps growing**: the latest null row was created **2 minutes before this audit** at 14:12:10Z. It's not a backfill leftover; it's an ongoing leak.

The backend log entry at startup proves the registry IS picking S3:

```
10:37:59.487 INFO FileStorageRegistry: active storage 's3' (de.dlr.shepard.plugins.files3.S3FileStorage_ClientProxy); 2 adapter(s) discovered.
```

Both adapters discovered, S3 active, plugin loaded. Garage healthcheck green, 24 objects already there from the Path D / FS1f code paths that DO honour the registry. **The plumbing works for one half of the file write paths and not the other two.**

## Migration design — Mongo GridFS → Garage

Three slices, smallest-to-largest:

### Slice 1 — FS1-ROUTING-FIX-FORWARD (S/M): route Paths B and C through the registry

The fix is structural, not configurational. Three services need to call the registry the way `FileContainerService` does:

1. **`SingletonFileReferenceService.createSingleton()`** — replace the line 149 `fileService.createFile(...)` call with a registry-mediated put:
   ```java
   FileStorage adapter = fileStorageRegistry.requireActive();
   ShepardFile saved;
   if (GridFsFileStorage.ID.equals(adapter.id())) {
     saved = fileService.createFile(SHARED_FILES_NAMESPACE, filename, payload);
   } else {
     // Non-GridFS: emit 503 with the presigned-URL hint, OR
     // wrap the bytes in a StoragePutRequest and route through adapter.put().
     // The S3 adapter's put() requires the full byte stream — same shape as
     // FileContainerService's commitUploadedFile() path. The cleanest fix is
     // to expose a server-side proxy upload on /v2/files (mirror of
     // FileContainerService.createFile's gridfs branch) so v15 importers
     // don't need to change their wire shape.
     throw new ServiceUnavailableException(
       "Direct upload to /v2/files is not supported for provider '" + adapter.id() +
       "' yet. Use POST /v2/files/upload-url + PUT to S3 + POST .../commit. " +
       "(SingletonFileReference presigned flow tracked as FS1-SINGLETON-PRESIGNED.)"
     );
   }
   saved.setProviderId(adapter.id());
   ```
   Both read (line 262) and delete (line 234) get analogous registry-routed branches: read via `adapter.get(StorageLocator)`, delete via `adapter.delete(StorageLocator)`.

2. **`FileBundleReferenceRest.uploadIntoGroup`** — same shape. Replace line 452 with the registry-mediated path, mirror `FileContainerService.createFile`.

3. **`FileBundleReferenceService.getPayload`** (lines 327, 370) — registry-routed reads.

**Test obligation:** unit tests for `SingletonFileReferenceService` and `FileBundleReferenceService` that wire a stub `FileStorageRegistry` and assert the active adapter's put/get/delete is called. Mirrors the FS1a `FileMigrationServiceTest` pattern.

**Migration cost:** none for existing data. `storageForRow()` already exists in `FileContainerService`; the same pattern works for singletons once they have a `providerId` to look up. New uploads land in S3; old uploads still served from GridFS.

**Side effect to watch:** the singleton-routing change means v15 importers continue to work against `/v2/files` (200 OK on `gridfs`), but operators with `SHEPARD_STORAGE_PROVIDER=s3` see the importer fall back to the v1 fileReferences path (line 1509), which is also broken because `FileContainerService.createFile`'s s3 branch already 503s. **The proper v2 fix is to ship `POST /v2/files/upload-url` + commit endpoints** (sibling of `/v2/file-containers/.../upload-url`) — see FS1-SINGLETON-PRESIGNED below.

### Slice 2 — FS1-ROUTING-MIGRATE-BACKFILL (M): one-off script to move 890 MB of GridFS bytes to Garage

A standalone Python script under `scripts/` (or a `shepard-admin files migrate-singletons` CLI verb — symmetric to `files migrate`) that:

1. Queries Neo4j for every `:ShepardFile` held by `:SingletonFileReference` or `:FileBundleReference` with `providerId IS NULL OR providerId = 'gridfs'`. Orders by `oid` for deterministic resume. (Per `feedback_warmup_fail_fast_diagnostic.md` warmup protocol: probe Garage write-test + Mongo read-test before the main loop.)

2. For each row, stream the bytes from GridFS (the path `SingletonFileReferenceService.getPayload()` uses today — `fileService.getPayload(SHARED_FILES_NAMESPACE, oid)`) to Garage via the S3 adapter (`adapter.put(new StoragePutRequest(...assignedObjectKey=oid))`). FS1e1's `StoragePutRequest.assignedObjectKey` field exists precisely to preserve oid identity — re-use it.

3. Verify the copy: HEAD the S3 object, compare `fileSize` to the source. (If the Neo4j `:ShepardFile.md5` is non-null, request `--md5` mode to GET and re-hash — slower but the higher-confidence pass for one-time backfill.)

4. On verify success, `MATCH (f:ShepardFile {oid:$oid}) SET f.providerId = 's3'`.

5. **Do NOT delete the GridFS row in the same pass.** Per `feedback_referenced_data_infinite_retention.md` (and `feedback_mutate_after_snapshot.md`): leave GridFS as the eventual-rollback safety net. Operator runs a separate purge-old-gridfs phase **after** post-migration validation drains.

6. Take a pre-mutation snapshot of the singleton bookkeeping collection before any deletes (per `PRE-MUT-SNAP1` / `feedback_mutate_after_snapshot.md`). Garage snapshot of `shepard-files` bucket already covered by Garage's own block storage; Mongo snapshot via `mongodump --collection=_shepard_files`.

**Idempotent.** Re-run picks up where it left off — rows already stamped `providerId='s3'` are skipped. Resumable per `oid` ordering. Streaming so no OOM at 890 MB.

**Wall-clock estimate:** ~12000 files × 100 ms per copy (1 MiB average + S3 round-trip) ≈ **20 min single-threaded**; ≈ **5 min with 4 parallel workers**. Operator-friendly.

**Rollback:** flip `providerId` back to `gridfs` for any row, the read path's `storageForRow()` routes to GridFS again. The GridFS bytes stay until the explicit purge phase.

### Slice 3 — FS1-ROUTING-DIAGNOSE — this doc

Lives at `aidocs/agent-findings/file-storage-routing-audit-2026-05-24.md`. Companion ops finding: `aidocs/agent-findings/garage-activation-runbook.md` (pre-existing).

## Three-slice fix sequence

| ID | Slice | Size | Pre-req | Notes |
|---|---|---|---|---|
| **FS1-ROUTING-DIAGNOSE** | This audit doc + admin backlog rows | S (done) | — | Today. |
| **FS1-ROUTING-FIX-FORWARD** | Route SingletonFileReferenceService + FileBundleReferenceRest/Service through `FileStorageRegistry` (mirror FileContainerService pattern). New 503 envelope for `/v2/files` direct upload when provider != gridfs. | S/M (1-2 days) | FS1a (shipped) | The v15 importer needs to switch to the presigned flow for `/v2/files`. Pairs with **FS1-SINGLETON-PRESIGNED** (sibling row below) for the proper v2 wire shape — without it, FIX-FORWARD breaks v15 against the s3 provider. |
| **FS1-SINGLETON-PRESIGNED** | `POST /v2/files/upload-url` + `POST /v2/files/upload-url/commit` — sibling of `FileContainerPresignedUrlRest` for the singleton path. Plus v15 importer update to use the three-step flow. | M (2-4 days) | FS1-ROUTING-FIX-FORWARD landing concurrently | The wire-shape parity that makes FIX-FORWARD safe for v15. |
| **FS1-ROUTING-MIGRATE-BACKFILL** | One-shot script `scripts/migrate-singleton-files-gridfs-to-s3.py` (or `shepard-admin files migrate-singletons` CLI verb). Streams 890 MB / 11788 files from `_shepard_files` GridFS to Garage `shepard-files` bucket, preserving oid, verifying size+md5, flipping `providerId='s3'`. Skips delete of source rows (separate `--purge-gridfs` phase post-validation). | M (1 sprint incl. snapshot + verify) | FS1-ROUTING-FIX-FORWARD + Garage running (already up). Snapshot prerequisite per `PRE-MUT-SNAP1`. | Wall-clock ~20 min for the data we have today. |

## Cross-checks

**CRIT-WORKTREE-DOCKER-CACHE.** Confirmed not the issue here. `docker exec infrastructure-backend-1 ls /deployments/lib/main/de.dlr.shepard.plugins.shepard-plugin-file-s3-*.jar` is present; backend log line `S3FileStorage: registered (id=s3, bucket=shepard-files, endpoint=http://garage:3900, pathStyle=true)` is present in the live container. The image is fresh and the plugin is loaded — the bug is in the source paths that don't consult the registry, not in a stale cache.

**OPS-MIGRATION-HEALTHCHECK.** The newly-shipped readiness check asserts every classpath `V**` migration is applied. **Recommend adding a `file-storage-backend-readiness` SmallRye readiness check** (new row below) that:
- Calls `FileStorageRegistry.activeStorage()`. WARN-degrade if empty.
- For S3 active: HEAD the configured bucket. WARN-degrade on 4xx/5xx.
- (Optional) Asserts no `:ShepardFile {providerId:'s3'}` rows exist with a `fileSize` mismatch against the S3 HEAD — but this is O(rows) and not suited to a readiness check. Save for a periodic audit.

**Synergy with OBS-MFFD1** (the in-Shepard observability collector). It already polls `docker exec shepard-garage /garage bucket info shepard-files` for the bucket size — the natural extension is to also poll the Neo4j providerId histogram (3 channels: count_null + count_gridfs + count_s3) so the routing-drift symptom would have been **visible in the Shepard chart UI** without a manual audit. Recommended as part of the OBS-MFFD1 follow-up.

## What's safe to do without operator approval

- File this audit doc (done).
- File backlog rows in `aidocs/16-dispatcher-backlog.md` for FS1-ROUTING-DIAGNOSE, FS1-ROUTING-FIX-FORWARD, FS1-SINGLETON-PRESIGNED, FS1-ROUTING-MIGRATE-BACKFILL, FS-STORAGE-READINESS-HEALTHCHECK, OBS-MFFD1-PROVIDERID (done).
- Commit per the worktree-isolation rule.

## What needs operator approval

- **The fix itself.** Changing `SingletonFileReferenceService` is non-trivial — every singleton upload + read path on the live MFFD ingest depends on it. The change should land on a feature branch + clean redeploy + smoke-test before the next ingest pass.
- **The migration script run.** Per the operator's standing rule (`feedback_mutate_after_snapshot.md` + `feedback_referenced_data_infinite_retention.md`): no data movement before the operator approves snapshot + post-migration validation plan. The script can be written and unit-tested without running it against live data.
- **The presigned-flow change to the v15 importer.** Operator runs the importer; importer changes need their nod.
- **Decision: 503 vs proxy-fallback for `/v2/files` when provider=s3.** FIX-FORWARD as drafted emits 503. An alternative — server-side proxy fallback (backend streams from client → S3) — keeps v1/v15 importers working without a wire-shape change but defeats W1 (presigned-URL bandwidth saving). Recommend 503 with a clear hint; operator confirm.

## What surprised me

- **`SingletonFileReferenceService` was never refactored to use the registry.** The FS1a design doc (`aidocs/data/45-gridfs-to-s3-evaluation.md §3.2`) describes `FileService` as becoming a thin facade. That happened for the container path but not for the singleton or bundle paths. They were left as direct `fileService.createFile()` callers. Combined with `SingletonFileReferenceService.createSingleton()` being the ONLY write path the v15 importer uses, the practical effect is "MFFD's entire 890 MB landed in Mongo while the operator's compose override said s3."
- **The default GridFS bucket is `fs`, not per-container.** I expected per-container collections (`FileContainer<UUID>.files` / `.chunks`) but the actual bytes all live in the single default `fs.chunks` bucket — the per-container collections only hold the metadata documents. The design doc's "one MongoDB collection per FileContainer" claim is half-true: the metadata is per-container; the bytes are global. This explains why 12524 chunks → 898 MB in one collection rather than spread across the 6 `FileContainer*` collections (which together hold 71 docs, 12 kB).
- **The healthcheck didn't catch this.** Per `OPS-MIGRATION-HEALTHCHECK`'s shipping note, the readiness checks now assert migration-chain integrity. A symmetric "storage-backend-readiness" check that asserts `:ShepardFile` rows match the active provider would have surfaced this on day 1 of the MFFD ingest. Filed as FS-STORAGE-READINESS-HEALTHCHECK below.
- **Garage healthy + 24 objects already there.** The S3 adapter IS working — every `FileContainerService.createFile` write since the activation has landed in Garage correctly. The 13.6 MiB / 24 objects is from `FileContainerService` callers (UI uploads, some script paths, the FS1f frontend presigned route). The routing miss is path-shape-specific, not adapter-broken.
