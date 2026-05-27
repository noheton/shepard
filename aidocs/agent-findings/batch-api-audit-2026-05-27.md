---
stage: fragment
created: 2026-05-27
task: BATCH-API-4
author: claude-sonnet-4-6
---

# Batch API — Per-Substrate Primitives Audit (2026-05-27)

Task: BATCH-API-4 — survey batch API surface, map substrate-native capabilities vs.
what Shepard exposes, identify missing primitives, rank by ROI.

---

## What I found

### Current batch surface

| Substrate | Native batch primitive | Shepard exposes today | Gap |
|---|---|---|---|
| **Neo4j** | `UNWIND … CREATE` — single Cypher transaction, N nodes at once | `POST /v2/data-objects/batch` (MFFD-BATCH-01): sequential for-loop, 500 OGM round-trips | UNWIND-based bulk create not wired; each item = one session round-trip |
| **Neo4j** | `UNWIND … MATCH … SET` for bulk annotation / predecessor wiring | Nothing | No batch annotation create; no batch predecessor wiring |
| **TimescaleDB** | COPY protocol (3–5× faster than VALUES INSERT); `INSERT … ON CONFLICT DO NOTHING` | `POST /v2/timeseries-containers/{id}/channels/{sid}/data/ingest` (TS-OPT3-COPY, single channel) | Multi-channel COPY ingest not exposed; ON CONFLICT strategy not selectable |
| **TimescaleDB** | Bulk read: single SQL with `WHERE channel_id = ANY($1)` | `POST /v2/timeseries-containers/{id}/channels/data/bulk` (TS-OPT2, max 200 channels, shared time window) | Shipped. No per-channel time window variant |
| **MongoDB** | `insertMany()`, `bulkWrite()` | `createStructuredData()` — `insertOne()` per item; zero `insertMany`/`bulkWrite` calls in codebase | Highest headroom substrate. Every SD payload create is a serial round-trip |
| **Garage S3** | Multipart upload (per-object, S3 standard) | `POST /v2/file-containers/{id}/upload-url` — single presigned PUT URL | No batch presigned URLs; 100-file batch = 100 REST calls from client |

**Confirmed shipped batch endpoints (full inventory):**
- `POST /v2/data-objects/batch` — HTTP 207, 1–500 DOs, sequential for-loop
- `POST /v2/timeseries-containers/{id}/channels/data/bulk` — multi-channel read, max 200 channels
- `POST /v2/timeseries-containers/{id}/channels/{sid}/data/ingest` — COPY-protocol write, single channel
- `POST /v2/import/diagnostics/{runId}/events/batch` — batch import of diagnostic log events (atomic)

**Dead code identified:**
`BulkTraceRequestIO`, `BulkTraceResultIO`, and `BulkTraceChannelIO` all carry javadoc referencing path
`/v2/timeseries-containers/{id}/channels/bulk`, but NO `@Path` annotation maps to this path anywhere
in the codebase. The actual shipped bulk read path is `/channels/data/bulk` using `BulkChannelDataRequestIO`.
The BulkTrace IO classes are imported in the resource file but the corresponding endpoint is not wired.
These are orphaned IO classes — either the endpoint was removed and the IOs left behind, or the endpoint
was never completed. Action: delete `BulkTraceRequestIO`, `BulkTraceResultIO`, `BulkTraceChannelIO`
or wire the endpoint. Either resolves the confusion.

---

## Gaps by use case

### MFFD Pass 1 — 30K DataObject create (the acceptance-criteria bottleneck)

The real importer is `examples/mffd-showcase/scripts/mffd-import-v15.py` (v16.5 already uses
`/v2/data-objects/batch`). The bottleneck is that each batch of 500 DOs still issues 500 Neo4j
OGM calls sequentially inside the service. For 30K DOs at 500/batch = 60 requests, each request
serialises 500 `createDataObject()` calls. No UNWIND-based transaction.

Gap: Neo4j UNWIND bulk-create not wired into `DataObjectService.createDataObjects()`.

The UNWIND pattern is already proven in DAO read queries (`findRefCountsByAppIds`,
`findTsContainerIdsByDataObjectAppIds` both use UNWIND). The gap is exclusively in the write path.

### MFFD Pass 2 — predecessor / parent wiring

After DOs are created, the importer wires predecessor and parent relationships. There is no batch
predecessor-wiring endpoint. Each link = one REST call. For a 30K-DO DAG with O(n) edges, this
is the second major bottleneck.

Gap: no `POST /v2/data-objects/batch/predecessors` or equivalent.

### Annotation batch

`SemanticAnnotationV2Rest` is pure single-row CRUD. Annotating 1,000 DOs with a campaign tag
= 1,000 POST requests. The AI annotation workflow (auto-annotation from file content) is blocked
on this.

Gap: no `POST /v2/annotations/batch`.

### Structured data bulk ingest

MongoDB `insertMany()` is never called. Every SD payload insert is `insertOne()`. For tabular
imports (CSV → structured data rows), this is a significant throughput gap.

Gap: no bulk SD ingest path; `StructuredDataService.createStructuredData()` is single-item.

### File upload batch presign

Uploading 100 files to a file container requires 100 calls to `POST /v2/file-containers/{id}/upload-url`.
The FileStorage SPI has no multi-object presign method. This is a session-setup cost issue for
the MFFD NDT file imports.

Gap: no `POST /v2/file-containers/{id}/upload-urls/batch` returning N presigned PUT URLs in one call.

---

## Priority queue (ROI ranking)

| Rank | Item | Substrate | Effort | Impact | MFFD unblocks |
|---|---|---|---|---|---|
| 1 | **Neo4j UNWIND bulk DO create** (optimize existing `/v2/data-objects/batch` backend) | Neo4j | M (1 sprint) | Pass 1 drops from ~50 min to ~5 min | Yes — acceptance criteria |
| 2 | **`POST /v2/annotations/batch`** | Neo4j | S (days) | AI annotation workflow; MFFD attribute back-fill | Yes (Pass 3) |
| 3 | **`POST /v2/file-containers/{id}/upload-urls/batch`** | Garage S3 | S (days) | MFFD NDT file import; any multi-file workflow | Yes |
| 4 | **Batch predecessor / parent wiring** `POST /v2/data-objects/batch/links` | Neo4j | M | Pass 2 bottleneck; enables DAG reconstruction at scale | Yes |
| 5 | **MongoDB `insertMany` for SD payloads** | MongoDB | S | Tabular/CSV import; structured data batch ingest | Indirect |
| 6 | **Multi-channel COPY ingest** (extend TS-OPT3-COPY to N channels in one request) | TimescaleDB | M | TS batch ingest from MFFD sensor rows | Yes (TS pass) |
| 7 | **Dead code removal** (BulkTrace* IOs) | — | XS | Reduces confusion; no functional impact | No |

Effort key: XS = hours, S = 1–3 days, M = 1 sprint (5–10 days).

---

## Draft endpoint specs for top 3

### Spec 1 — Neo4j UNWIND bulk-create (backend optimization of existing endpoint)

**What changes:** `DataObjectService` gains a `createDataObjects(List<…>)` bulk method that
issues a single Cypher transaction using UNWIND. The REST endpoint (`POST /v2/data-objects/batch`)
wire shape stays identical (HTTP 207, same request/response IO). This is a substrate-level
optimization, not a new endpoint.

**Cypher skeleton:**
```cypher
UNWIND $items AS item
MATCH (c:Collection {appId: item.collectionAppId})
CREATE (d:DataObject {
  appId: item.appId,
  name:  item.name,
  description: item.description,
  status: item.status,
  createdAt: item.createdAt
})-[:BELONGS_TO]->(c)
RETURN d.appId AS appId, item.index AS index
```

**Implementation path:**
1. Add `createDataObjectsBulk(Long collectionOgmId, List<DataObjectIO> items)` to `DataObjectDAO`
   using `session.query()` with the UNWIND Cypher.
2. Add `createDataObjects(Long collectionOgmId, List<DataObjectIO> items)` to `DataObjectService`
   that delegates to the DAO bulk method inside one Neo4j transaction.
3. Modify `DataObjectBatchV2Rest.batch()` to group items by `collectionOgmId`, call the bulk method
   per group, and reconstruct per-item results from the returned `appId`-index pairs.
4. Error handling: per-group failures propagate to all items in that group with `INTERNAL_ERROR`;
   per-item validation errors (name blank, parent not found) remain pre-call client-side.

**Constraint:** parent resolution (parentAppId → parentOgmId) must still happen per-item before
the UNWIND call, since parent existence is a per-item precondition. Batch the parent lookups with
a second UNWIND MATCH before the CREATE transaction.

**Acceptance test:** Establish baseline first: time a 500-item batch call end-to-end on a warm instance.
Target: ≥5× wall-time reduction vs. the baseline sequential-OGM measurement. The BATCH-API acceptance
criterion (30K-DO Pass 1 in <5 min) is the integration-level gate.

---

### Spec 2 — `POST /v2/annotations/batch`

**Path:** `POST /v2/annotations/batch`  
**Auth:** Authenticated. Write permission checked per target entity's owning Collection.  
**Request body:**
```json
[
  {
    "targetAppId":    "018f…",
    "predicateAppId": "018f…",
    "value":          "LOX/LH2",
    "sourceMode":     "ai",
    "confidence":     0.92
  },
  { … }
]
```
Max 500 items. Fields mirror `POST /v2/annotations` single-create.

**Response:** HTTP 207, same envelope as DataObject batch:
```json
{
  "created": 498,
  "failed": 2,
  "results": [
    { "index": 0, "status": "created", "appId": "018f…" },
    { "index": 7, "status": "error", "errorCode": "TARGET_NOT_FOUND", "errorMessage": "…" }
  ]
}
```

**Implementation:** Single UNWIND Cypher creates N `:SemanticAnnotation` nodes and wires
`[:HAS_ANNOTATION]` relationships in one transaction. ProvenanceService records one batch
`:Activity` for the whole request (consistent with MFFD-BATCH-01). Per-item provenance and
`sourceMode` handling for mixed-mode batches are open questions deferred to PROV2 / BATCH-API-1 spec.

**Error codes:** `TARGET_NOT_FOUND`, `PREDICATE_NOT_FOUND`, `FORBIDDEN`, `INVALID_INPUT`, `INTERNAL_ERROR`.

---

### Spec 3 — `POST /v2/file-containers/{containerAppId}/upload-urls/batch`

**Path:** `POST /v2/file-containers/{containerAppId}/upload-urls/batch`  
**Auth:** Authenticated, Write permission on container.  
**Request body:**
```json
{
  "files": [
    { "filename": "scan_001.hdf5", "contentType": "application/x-hdf5", "sizeBytes": 104857600 },
    { "filename": "scan_002.hdf5", "contentType": "application/x-hdf5", "sizeBytes": 98304000 }
  ]
}
```
Max 50 files per call.

**Response:** HTTP 200 (not 207 — this is a URL generation call, not a creation call):
```json
{
  "urls": [
    {
      "filename": "scan_001.hdf5",
      "uploadUrl": "https://garage.host/…?X-Amz-Signature=…",
      "expiresAt": "2026-05-27T15:30:00Z",
      "commitToken": "abc123"
    },
    { … }
  ]
}
```

**Implementation:**
1. Loop N times over `fileStorage.presignedUploadUrl(containerOid, filename, contentType)` —
   Garage S3 has no multi-object presign native primitive; the loop is server-side, not client-side.
   Cost is cheap (HMAC signing, no I/O).
2. `commitToken` maps to the internal `oid` needed for the subsequent commit call.
3. Client uploads files in parallel against the returned URLs (can use `Promise.all()` or
   `asyncio.gather()`), then calls a single `POST /v2/file-containers/{id}/upload-urls/batch/commit`
   to register all completed uploads atomically.

**Commit body:**
```json
{ "commits": [ { "commitToken": "abc123", "filename": "scan_001.hdf5" }, … ] }
```

**FileStorage SPI extension needed:**
Add `presignedUploadUrls(List<PresignRequest>)` with a default implementation that loops over
`presignedUploadUrl()` — so existing implementations work without change; a future native
batch-presign implementation can override.

---

## Blockers

1. **Neo4j OGM UNWIND impedance.** The Quarkus Neo4j OGM layer (`neo4j-ogm`) makes raw Cypher
   execution via `session.query()` verbose and type-unsafe. The existing pattern in `DataObjectDAO`
   (`findRefCountsByAppIds`) shows it works, but the result mapping for a CREATE-returning query
   needs careful type handling. Not a blocker, but adds implementation friction vs. a pure Cypher
   driver call.

2. **Per-item provenance granularity deferred.** All three batch endpoints above record one
   `:Activity` for the whole batch. This is consistent with MFFD-BATCH-01 (explicitly noted in its
   javadoc). Full per-item provenance requires PROV2 (not yet scoped). Not a blocker for shipping,
   but must be tracked in `aidocs/16`.

3. **BulkTrace dead code decision.** Before shipping any new TS batch endpoint, the `BulkTrace*`
   IO orphans should be resolved (delete or wire). Leaving them creates confusion about what the
   TS batch surface actually is.

4. **FileStorage SPI extension.** Batch presigned URLs require adding `presignedUploadUrls()` to
   the `FileStorage` SPI interface. Any existing custom implementation must implement the default
   override. Low risk but is a SPI-breaking change (minor version bump needed if SPI is published).

5. **MongoDB `insertMany` requires service-layer surgery.** `StructuredDataService` currently
   calls `insertOne` per item; wiring `insertMany` requires a new service method signature and
   a corresponding REST endpoint. Not complex, but not a one-line change.

---

## What surprised me

- **MongoDB has zero `insertMany`/`bulkWrite` calls** in the entire backend. Every structured data
  payload is a serial `insertOne`. This is the highest-headroom substrate — easy win, no SPI risk.

- **The BulkTrace IO classes are imported in `TimeseriesContainerChannelsRest.java` but the
  endpoint they describe (`/channels/bulk`) does not exist.** This is a genuine dead-code landmine
  — any developer reading the IO class javadoc will think the endpoint exists.

- **The UNWIND pattern is already proven for Neo4j reads** (`findRefCountsByAppIds`,
  `findTsContainerIdsByDataObjectAppIds` in `DataObjectDAO`). The gap in the write path is a
  prior design choice (one-at-a-time OGM save), not a technical blocker. Closing it is
  straightforward.

- **TS-OPT2 (multi-channel read) is shipped and works** but has no per-channel time window
  — all channels share the same `[start, end]` window. This is fine for MFFD playback but
  limits AI-directed anomaly queries (where different channels have different suspect windows).
  A per-channel window variant is a useful extension but not in scope for BATCH-API-4.
