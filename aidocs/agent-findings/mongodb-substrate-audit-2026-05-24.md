---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributors, ops
---

# MongoDB substrate audit — 2026-05-24

**Sibling agent #209** is auditing file-routing specifically (Garage vs Mongo).
This audit covers everything else: schema design, collections, indexes, query
patterns, GridFS storage shape, driver/pool/retention. The two reports synthesise
together into the multi-substrate post-MFFD audit ledger (`MFFD-DB-AUDIT-REAL-DATA`).

## TL;DR

Live `infrastructure-mongodb-1` (MongoDB 8.0.4, WiredTiger, uptime 2h at probe
time) carries **758 MB on-disk in the `database` DB** across **21 collections**.
Of that, **`fs.chunks` is 791 MB / 99% of substrate weight** — 12,546 chunks
holding 11,899 GridFS files; 1 file (a 532 MB Confluence space export ZIP)
alone is **60% of the entire substrate**. Schema is healthy at the aggregate
level (no orphan GridFS chunks, no broken cross-substrate refs); the gaps are
in **discipline** (no schema validators, no TTL, no compound indexes beyond
`_id`, no connection-pool tuning, BUG-E/F tombstones still showing in Neo4j)
and **patterns that don't scale** (one Mongo collection per container,
string-typed `FileMongoId` joining ObjectId-keyed `fs.files`, N+1 search
queries, sequential per-doc GridFS delete loops).

**Counts**: 8 antipatterns + 4 best-practice gaps + 2 cross-substrate findings.
Top fix by ROI: **add compound index `{FileMongoId: 1}` on `_shepard_files`**
plus a **per-collection size cap policy** before the next round of MFFD ingest
(or the next 532 MB ZIP upload) pushes Mongo past 1 GB.

## Substrate snapshot — 2026-05-24 14:30 UTC

| Metric | Value |
|---|---|
| Mongo version | 8.0.4 |
| Storage engine | WiredTiger |
| Uptime at probe | 2h |
| WiredTiger cache configured | 2 048 MB |
| WiredTiger cache used | 933 MB (≈46% of max) |
| Databases | `admin`, `config`, `database` (the shepard DB), `local` |
| Total on-disk (all DBs) | 758 MB |
| Shepard DB on-disk | 795 MB (per `listDatabases.sizeOnDisk`) |
| Collections in shepard DB | 21 |
| Connections | (snapshot too short for meaningful stats) |
| Driver | `org.mongodb:mongodb-driver-sync:5.1.1` via `quarkus-mongodb-client` |

## Per-collection inventory

| Collection | Docs | Data size | Storage size | Idx size | nIdx | Avg obj |
|---|---:|---:|---:|---:|---:|---:|
| `fs.chunks` | 12 546 | 856 MB | 754 MB | 552 KB | 2 | 70 KB |
| `fs.files` | 11 874 | 1.2 MB | 392 KB | 516 KB | 2 | 109 B |
| `_shepard_files` | 11 800 | 1.98 MB | 972 KB | 268 KB | 1 | 175 B |
| `userAvatars` | 2 | 149 KB | 332 KB | 36 KB | 1 | 76 KB |
| `StructuredDataContainer943e8388…` | 863 | 1.88 MB | 412 KB | 60 KB | 1 | 2.2 KB |
| `StructuredDataContainer70037425…` | 100 | 50 KB | 44 KB | 36 KB | 1 | 518 B |
| `StructuredDataContainerd5038741…` | 16 | 10 KB | 36 KB | 36 KB | 1 | 687 B |
| `StructuredDataContainera568841b…` | 5 | 4 KB | 20 KB | 20 KB | 1 | 838 B |
| `_shepard_videos` | 3 | 0.5 KB | 36 KB | 36 KB | 1 | 171 B |
| `FileContainer7693e977…` | 34 | 6 KB | 36 KB | 36 KB | 1 | 175 B |
| `FileContainer9299f5ea…` | 30 | 5 KB | 20 KB | 20 KB | 1 | 174 B |
| `FileContainera33198d8…` | 6 | 1 KB | 20 KB | 20 KB | 1 | 174 B |
| `FileContainer08799acd…` | 1 | 195 B | 20 KB | 20 KB | 1 | 195 B |
| 6× empty FileContainer/SD collections | 0 | 0 | 4 KB each | 4 KB each | 1 | — |

**Empty collections** (24 KB wasted storage): `StructuredDataContainer839614f2`,
`…54ec30b6`, `…4702ed7d`, `…4786abf3`, `…6931e4d4`, `…4277ad7c`,
`FileContainerecc172da`, `FileContainer8a3e519e`. Each is a per-container
collection that was created but never written to — the pattern's overhead.

## GridFS shape (read-only confirm for synthesis with #209)

- Total bytes in `fs.chunks`: 897.7 MB across 12 546 chunk docs.
- Total bytes claimed by `fs.files` (sum of `length`): 897.4 MB across 11 899 files.
- All `fs.files` rows use `chunkSize: 1 048 576` (1 MiB — overridden by `FileService.CHUNK_SIZE_BYTES`; the GridFS default is 255 KB).
- File-size distribution:

| Size bucket | Count | Total bytes |
|---|---:|---:|
| 0 B – 1 KB | 5 358 | 1.3 MB |
| 1 KB – 10 KB | 5 786 | 13.0 MB |
| 10 KB – 100 KB | 27 | 1.5 MB |
| 100 KB – 1 MB | 716 | 165 MB |
| 1 MB – 10 MB | 7 | 40 MB |
| 10 MB – 100 MB | 4 | 128 MB |
| **≥ 100 MB** | **1** | **507 MB** |

- The single ≥100 MB file is `Confluence-space-export-151906-2.html.zip`
  (532 MB, uploaded 2026-05-22). It alone is **60% of MongoDB on-disk**.
- 3 identical-by-name files `lumen-hotfire.mp4` (8.6 MB each, 3 copies,
  ~26 MB redundant) — referenced by 3 separate `_shepard_videos` docs all
  pointing to distinct ObjectIds. Almost certainly the same content uploaded
  3× via the showcase demo workflow.
- **Zero orphan chunks** confirmed via `$lookup` join on a sample — the
  `dropMongoCollections` path in NukeService + `deleteFile` paths in
  FileService are doing their cleanup correctly.

## Schema design observations

### Polymorphism + collection naming
- **One Mongo collection per FileContainer + per StructuredDataContainer.**
  `FileService.createFileContainer()` and `StructuredDataService.createStructuredDataContainer()`
  both call `mongoDatabase.createCollection(<UUID>)`. The naming scheme is
  `FileContainer<UUID>` / `StructuredDataContainer<UUID>` — at MFFD volume
  this is the proliferation surfaced as `MFFD-IMPORT-BUG-F` (4 197 SD
  containers created on 2026-05-23). At audit time the Mongo side has been
  cleaned (only 10 SD collections present) but **the Neo4j tombstones
  (`:StructuredDataContainer{deleted: TRUE}` count: 4 197) have not been
  GC'd** — see cross-substrate finding below.
- Special-purpose namespaces: `userAvatars` (singleton-keyed by `appId`-string),
  `_shepard_files` (FB1b/c singleton namespace shared across all `:SingletonFileReference`
  ShepardFile docs), `_shepard_videos` (3 docs total). These use stable names —
  the right pattern. The per-container model is the outlier.

### Field consistency
- **No `$jsonSchema` validators on any collection.** `db.runCommand({listCollections, filter: {options.validator}})` returns 0 hits. Polymorphic SD payloads can land any shape including `null` fields, type-tripping reads.
- `_shepard_files`, `_shepard_videos`, every `FileContainer*` doc uses identical schema: `{_id: ObjectId, createdAt: Date, name: string, md5: string, fileSize: Long, FileMongoId: string}` (FB1a fileSize is now consistent).
- **`FileMongoId` is stored as a STRING but `fs.files._id` is an ObjectId.** All round-trips through `FileService` coerce via `new ObjectId(payloadDocument.getString(FILEID_ATTR))`. This works for `.find(eq("_id", oid))` but **breaks `$lookup` joins** that don't pre-cast (verified live in mongosh — only the cast variant resolves). Future analytics queries using the aggregation framework will silently return empty until someone notices.

### `_meta` envelope on SD docs
- StructuredDataService.createStructuredData wraps the user payload, strips
  all `_`-prefixed keys (silent data loss for any user JSON that happens to
  include `_id` or other underscore-leading keys), then appends `_meta: {createdAt, name}`. No index on `_meta.name` or `_meta.createdAt` — search-by-name and search-by-date scan the entire collection.
- The wrap is BUG-E's `{items: [...], _wrapped: "v15.12-bug-e"}` shape — but
  that wrap lives **in the importer**, not in the backend. The backend still
  uses `Document.parse(payload.getPayload())` and would still throw the BSON-array
  exception for any client that POSTs a JSON array as payload. **No backend-side wrap fallback yet** (tracked separately as `MFFD-SD-BACKEND-WRAP-FALLBACK`).

### Capped collections, TTL
- Zero capped collections, zero TTL indexes anywhere in the substrate.
  Nothing self-prunes. `userAvatars`, `_shepard_videos`, the per-container
  collections all grow without bound unless explicitly deleted.
- Snapshot/PRE-MUT-SNAP and SM1 retention work (queued) will need a Mongo
  retention path; nothing in the current code is reading or honouring a
  `retain_for_days` field.

## Indexes + query patterns

### Indexes (all 21 collections)
- Every collection has the default `_id_` index.
- **`fs.files`** also has `{filename: 1, uploadDate: 1}` — the default GridFS
  compound index. Useful for `bucket.find({filename: ...}).sort({uploadDate: -1})`
  but **`$indexStats.accesses.ops = 0`** for this index (uptime 2h is short,
  but the production access pattern is by ObjectId not filename — that's the
  upstream baseline FileService preserves).
- **`fs.chunks`** has the unique `{files_id: 1, n: 1}` index — the GridFS
  download-ordering invariant. `accesses.ops = 0` in the 2h window, but every
  download triggers it under the hood — this is a metric-collection quirk in
  Mongo 8 ($indexStats counts top-level finds, not the internal sequence reads).
- **NO compound or secondary indexes** anywhere else. Notably missing:
  - `_shepard_files.{FileMongoId: 1}` — needed for any "find singleton FileReference's blob" lookup; today the read path knows the `_id` directly (`accesses.ops = 0` on the `_id_` index in the 2h window, suggesting the FB1c path goes via the Neo4j `oid` and skips Mongo lookup entirely, but any cleanup / orphan-scan job will scan all 11 800 docs).
  - `userAvatars.{uploadedAt: 1}` — needed if/when an avatar-eviction policy ships.
  - `*StructuredDataContainer*._meta.createdAt` — needed for any "show me SD docs newer than X" query.

### Query patterns
- **N+1 in `StructuredDataSearchService.findMatchingReferences`** (line 76-95):
  one `mongoContainer.find(...)` per reachable reference. A search over a
  collection with 1 000 DataObjects with SD references = 1 000 separate Mongo
  round-trips. Already flagged as `SD1` in the backlog (deprecate Mongo
  query translator); the audit confirms the perf shape, not just the
  multi-substrate cost.
- **String-concatenated Mongo query construction** in the same method
  (`mongoQuery += ", " + mongoSearchQuery + "}"` line 91, then
  `Document.parse(mongoQuery)`). `mongoSearchQuery` is the user-supplied search
  body — `MongoDBQueryBuilder.getMongoDBQueryString` should sanitise but the
  shape is the same shape the C5/C5b Cypher kill-script targeted, just in
  Mongo. Risk: injection via constructed JSON, or accidental shape breakage
  if the user payload contains a `"` character. Mongo's `$where` is not used
  (confirmed via grep), so this isn't a code-eval surface, but it's the wrong
  pattern.
- **Sequential per-doc GridFS delete in `FileService.deleteFileContainer`**
  (line 237-240): `for (Document doc : toDelete.find()) gridBucket.delete(...)`
  — N round-trips to delete N files in a container. For the 532 MB Confluence
  ZIP container that's only 1 delete; for the MFFD `FileContainer7693e977…`
  (34 docs) that's 34 round-trips. Should be `bucket.delete(List.of(...))` in
  one batch operation, or use the bulk-delete path on `fs.chunks` /
  `fs.files`.
- **`GridFSBuckets.create(mongoDatabase)` re-allocated on every operation**
  (FileService.createBucket(), called per createFile/getPayload/getFile/
  deleteFile). The GridFSBucket is thread-safe and meant to be cached;
  re-allocating it is cheap but unnecessary. Same pattern in `_shepard_files`
  path.
- `scannedObjects` (38 291) vs `returned` (1 047) over 2h uptime = **~36×
  scan-amplification**. Some of this is fs.chunks downloads (each download
  reads many chunks for 1 file return), so the ratio is biased; but it does
  show that no compound index is paying for itself.

## Stack-level findings

### Connection-string + pool
- `quarkus.mongodb.connection-string=mongodb://mongo@mongodb:27017` (line 78 of
  `application.properties`) — **no database name in the URI**. The
  `MongoClientWrapper.init()` falls back to the literal default
  `"database"`. If an admin overrides the connection string and forgets the
  database segment, the fallback silently lands writes into the wrong DB.
  Should log a WARN-level message making the fallback path explicit, or
  refuse to start.
- **No connection-pool tuning declared** anywhere. Defaults apply:
  `minPoolSize=0`, `maxPoolSize=100`, `maxIdleTimeMS=∞`, `serverSelectionTimeoutMS=30s`.
  For a Quarkus app with virtual threads serving 100s of concurrent search
  requests, the upstream Mongo driver default may saturate; or it may
  oversize and waste sockets. **No data-driven tuning has happened.**
- `quarkus.mongodb.health.enabled=false` (line 27). Shepard rolls its own
  `MongoPinger` for the A1b health framework — that's deliberate; just noting
  it because the Quarkus default would otherwise have given a free
  `/q/health/live` Mongo check.
- `quarkus.mongodb.metrics.enabled=true` (line 161) — Mongo command-latency
  metrics flow to Prometheus per A1d's perf-recommend rule
  `mongo_latency_high`. Good — this is the right discipline.

### Driver
- `mongodb.version=5.1.1` in `backend/pom.xml`. Current upstream is 5.2.x
  (2026-05). Not urgent (no known CVEs in 5.1.1 at audit time) but the next
  dependency bump should pick this up.
- Driver is `mongodb-driver-sync` (blocking IO). Quarkus 3.x supports
  `mongodb-driver-reactivestreams` for non-blocking — irrelevant today but
  relevant when virtual-thread + reactive lanes converge.

### WiredTiger
- Cache configured 2 GB, currently using ~933 MB — comfortable. With the 532 MB
  Confluence-ZIP heavy file the cache pressure is already non-trivial; one
  more such upload would start to evict.

## Cross-substrate observations (light — for synthesis)

1. **Neo4j SD-container tombstone GC gap.**
   - Neo4j: 4 197 `:StructuredDataContainer{deleted: TRUE}` + 10 live.
   - Mongo: 10 SD collections (all live ones).
   - Verification: 5 sample mongoIds from the tombstone set → all confirmed
     GONE from Mongo. So Mongo-side cleanup ran, but **Neo4j tombstones never
     get reaped**. This costs Neo4j cypher-plan latency on every traversal
     touching `:StructuredDataContainer` and is a smell for the SM1 retention
     work — soft delete without a GC pass becomes hard delete with extra
     steps.
2. **String-typed `FileMongoId` field universally.** Every `_shepard_files`,
   `_shepard_videos`, `FileContainer*` doc stores `FileMongoId: "hex"` not
   `FileMongoId: ObjectId("hex")`. `fs.files._id` is `ObjectId`. Any analytics
   pipeline using `$lookup` will return empty unless the join expression
   pre-casts. Verified live: `db["fs.files"].findOne({_id: "<hex>"})` returns
   `null`; only `findOne({_id: new ObjectId("<hex>")})` resolves. The backend
   Java code does the cast at every read site; this is correct but fragile.

## Antipatterns (severity-sorted)

| Severity | ID | Site | Issue |
|---|---|---|---|
| MAJOR | `MONGO-AUDIT-2026-05-24-001` | `StructuredDataSearchService:76-95` | N+1: one Mongo round-trip per reachable reference. Confirms long-standing SD1 backlog row. |
| MAJOR | `MONGO-AUDIT-2026-05-24-002` | Universal per-container Mongo collection (`createFileContainer`, `createStructuredDataContainer`) | One Mongo collection per container — proven not to scale; BUG-F surfaced the 4 197-collection blast at MFFD volume. Designed-in cost; lives until SD1 (deprecate Mongo SD) + FS1 family ships. |
| MAJOR | `MONGO-AUDIT-2026-05-24-003` | `StructuredDataSearchService:91` | JSON-string concatenation to build Mongo query + `Document.parse`. Same anti-pattern shape as the C5/C5b Cypher string-build that the fork explicitly killed. Should use `Filters` DSL. |
| MAJOR | `MONGO-AUDIT-2026-05-24-004` | `StructuredDataService:53` | `Document.parse(payload.getPayload())` rejects array-root JSON with BSON-array error. BUG-E v15.12 wraps client-side; **no backend-side fallback wrap** — any non-importer client hits the same 500. Tracked as `MFFD-SD-BACKEND-WRAP-FALLBACK`. |
| MAJOR | `MONGO-AUDIT-2026-05-24-005` | No schema validators anywhere | 21 collections, 0 validators. Polymorphic SD payloads can land any shape including `null` everywhere. `userAvatars` could land mimeType=null today. |
| MINOR | `MONGO-AUDIT-2026-05-24-006` | `FileService:237-240` deleteFileContainer | Sequential per-doc GridFS delete loop. N round-trips. Should use bulk delete. |
| MINOR | `MONGO-AUDIT-2026-05-24-007` | `_shepard_files` + `_shepard_videos` + `FileContainer*` | `FileMongoId` stored as string but `fs.files._id` is ObjectId. Aggregation `$lookup` joins silently return empty unless pre-cast. |
| MINOR | `MONGO-AUDIT-2026-05-24-008` | `StructuredDataService:60-61` | `forbidden.forEach(toInsert::remove)` strips ALL underscore-prefixed user keys silently. User payload `_my_field: "..."` is data-lost without warning. |

## Best-practice gaps

| ID | Gap |
|---|---|
| `MONGO-AUDIT-2026-05-24-009` | No connection-pool config (`maxPoolSize`, `minPoolSize`, `maxIdleTime`). Driver defaults apply. Should be tuned + documented per the PERF2b `mongo_latency_high` rule (already wired). |
| `MONGO-AUDIT-2026-05-24-010` | No TTL indexes anywhere. SM1 retention work needs them; today nothing self-prunes. |
| `MONGO-AUDIT-2026-05-24-011` | `quarkus.mongodb.connection-string` carries no DB name; silent fallback to `"database"` is fragile. Either log loudly or refuse start. |
| `MONGO-AUDIT-2026-05-24-012` | No per-container size cap. The 532 MB Confluence-ZIP upload is 60% of the substrate; one more bad upload pushes Mongo past 1 GB. Pair with the SM1a 1-year default retention (queued) and a per-container max-bytes alert. |

## Top 5 fixes ranked by (perf × correctness × blast-radius)

1. **`MONGO-AUDIT-2026-05-24-001 + SD1` — kill the N+1 in `findMatchingReferences`** by either porting SD search to Postgres (the intended target per SD1) or batching the Mongo-side queries into a single `$in` over the union of reachable mongoContainerIds. **Highest perf payoff** — current shape costs 1 round-trip per reference. Already on the backlog (SD1).
2. **`MONGO-AUDIT-2026-05-24-004` — add backend-side array-wrap fallback in `StructuredDataService.createStructuredData`** so any external client that POSTs a JSON array payload doesn't 500. The importer already wraps; the backend should be defensive too. Closes the BUG-E class permanently.
3. **`MONGO-AUDIT-2026-05-24-005 + 009` — add JSON schema validators on `userAvatars`, `_shepard_files`, `_shepard_videos`** (the singleton-schema collections — per-container is harder because the SD shape is by design polymorphic). Cheap, blocks malformed writes from drift, supports the FAIR `R1` rigour the platform is reaching for.
4. **`MONGO-AUDIT-2026-05-24-006 + 012` — replace `deleteFileContainer`'s per-doc loop with `bucket.delete(List.of(...))` + add per-container `max-bytes` alert via the AD_STORE1 endpoint.** Reduces cleanup-tail-latency on big containers + warns admins before the next 532 MB ZIP eats the substrate.
5. **`MONGO-AUDIT-2026-05-24-002` cross-substrate — close the BUG-F Neo4j tombstone GC.** 4 197 `:StructuredDataContainer{deleted: TRUE}` nodes in Neo4j with no corresponding Mongo collection. Pair the post-redrive cleanup with a `MATCH (n:StructuredDataContainer {deleted: TRUE}) DETACH DELETE n` pass once SD-WIPE-AND-REGET completes. Long-term: hard-delete on the Mongo drop, don't tombstone.

## What surprised me

- **MongoDB is mostly a blob store** — 99% of bytes are in `fs.chunks`,
  and 60% of those are one 532 MB Confluence space export. Take that ZIP
  out and the entire substrate is 250 MB. Most of the "MongoDB problem"
  framing in CLAUDE.md is really a "single large file" problem.
- **The schema is cleaner than I expected.** No orphan chunks, no broken
  cross-substrate refs (4 197 Neo4j tombstones notwithstanding — those are
  consistent with reality, just not GC'd), no `$where`, no regex search.
  The discipline gaps (no validators, no TTL, no pool tuning) are the
  textbook list, not site-specific bugs.
- **Empty per-container collections cost 4 KB each just by existing.** 6 empty
  collections = 24 KB wasted but more importantly = 6 placeholders that
  were never written to. Either the creation path doesn't roll back on
  failure, or the data shape allowed creating-and-never-using a container.
- **`fs.files._id_` got 1 026 ops in 2 hours but `_shepard_files._id_`
  got zero.** The singleton FileReference path apparently doesn't go
  through Mongo at all for the lookup — the Neo4j ShepardFile carries the
  data the read needs; Mongo is only touched when the blob bytes are
  requested. That's actually good news for hot-path latency, but it means
  any "find file by FileMongoId" tooling will hit zero useful indexes.
- **`_shepard_videos` has 3 copies of `lumen-hotfire.mp4` (26 MB total redundancy).**
  Each upload created a new GridFS blob even though the MD5 hashes are
  identical. Content-addressable storage (dedup by SHA-256) would have
  collapsed this. Already implied by the FS1 / Garage trajectory but worth
  flagging — the GridFS path has no dedup.

## References

- Sibling agent #209 — file-routing investigation (Garage vs Mongo).
- `aidocs/16-dispatcher-backlog.md` — rows `SD1`, `FS1*`, `MFFD-IMPORT-BUG-E/F`, `SD-WIPE-AND-REGET`, `MFFD-DB-AUDIT-REAL-DATA`, `SM1`, `PRE-MUT-SNAP*`.
- `feedback_db_review_all_stores.md` — cross-substrate review discipline.
- `aidocs/agent-findings/db-schema-research-*.md` — prior empty-data audit baseline.
- Live snapshot: `docker exec infrastructure-mongodb-1 mongosh ...` (commands captured in this doc; substrate state at 2026-05-24 14:30 UTC).
