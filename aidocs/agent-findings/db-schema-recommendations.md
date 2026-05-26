---
stage: audited-by-personas
last-stage-change: 2026-05-26
author: db-architect-agent
session: 33f9b6cd-283e-4eb5-bfea-fec7b3720743
---

# DB Schema Recommendations — Live Shepard Instance Audit

**Audit date:** 2026-05-26  
**Substrates covered:** Neo4j 5.x, PostgreSQL 16 + TimescaleDB 2.24.0, MongoDB (GridFS), PostGIS (absent — see §5), Garage S3  
**Instance state at audit:** MFFD v16 ingest in progress (~8 500 dest DOs), 132.6M timeseries datapoints, 296 772 Activity nodes

---

## Executive Summary

The five-substrate architecture is structurally sound and the recent V1.8.0 SQL migration
(composite index + 7-day compression window + chunk skipping) shows the team already
understands TimescaleDB tuning. Three findings require immediate action before they cause
data loss or permanent unreachability:

1. **TS-ORPHAN-MFFD (CRITICAL):** PostgreSQL container_id=661951 holds 192 channels and
   128 439 326 datapoints with zero matching Neo4j `TimeseriesContainer` node. Every one of
   those points is invisible to the canonical REST API. The data is safe on disk but
   inaccessible through any supported retrieval path.

2. **BUG-FILEPAYLOAD-UUID-OID (CRITICAL):** `FileService.getPayload()` line 180 calls
   `new ObjectId(fileOid)` which requires a 24-char hex string. Garage writes UUID-shaped
   OIDs (e.g. `da2e2788-ebac-4bdb-9ff4-a3e677186ed5`). Every Garage-backed file download
   returns HTTP 500. Upload works; retrieval is broken for all Garage-stored files.

3. **Activity node retention time-bomb (HIGH):** 296 772 Activity nodes have accumulated
   in 6.77 days (43 812/day). No retention policy exists. At current MFFD ingest rate,
   Activity nodes will overtake Resource (375 079, currently the largest class) within
   ~2 days and exceed 1 million nodes within ~18 days, adding index pressure and
   increasing every write transaction that touches `appId_unique_Activity`.

Everything else in this report is consequential but non-urgent.

---

## §1 — Neo4j Substrate

### 1.1 Node count snapshot (2026-05-26)

| Label | Count | Notes |
|---|---|---|
| Resource (ontology) | 375 079 | n10s :Resource nodes — expected |
| **Activity** | **296 772** | 43 812/day growth, no retention |
| BasicReference | 22 249 | 4 413 are tombstoned (`deleted=TRUE`) |
| ShepardFile | 22 071 | file metadata nodes |
| **DataObject** | **17 149** | 8 559 tombstoned (49.8%) |
| Permissions | 4 364 | one per entity |
| **StructuredDataContainer** | **4 207** | 4 197 tombstoned (99.8%) — never GC'd |
| TimeseriesContainer | 8 | only 6 have non-NULL `id`; 2 are NULL-id shadows |

### 1.2 Tombstone backlog — MEDIUM risk, escalating

**49.8% of all DataObject nodes are soft-deleted.** The `idx_DataObject_deleted` RANGE
index is hit on every `deleted=FALSE` filter, which appears in every query issued by
`CypherQueryHelper`. As the deleted fraction grows, this index becomes less selective:
with 8 559 deleted vs. 8 590 live nodes, the selectivity is now effectively 50/50, which
means the planner may stop using the index and fall back to a full label scan.

The `StructuredDataContainer` situation is worse: 4 197 of 4 207 nodes (99.8%) are
tombstoned. All 4 197 have `mongoName = NULL` — they are the BUG-F wave cleaned up in
2026-05-23 but never GC'd from Neo4j. They hold no live Mongo state and are safe to
permanently delete.

**8 559 tombstoned DataObjects also carry 4 413 dangling `has_reference` edges** to
entities that may themselves be tombstoned. These dangling edges add traversal cost to
any query that walks the reference graph.

**Recommendation:** Ship V64 migration (see §7) that permanently deletes tombstoned
`StructuredDataContainer` nodes and their Permissions/reference edges. Schedule a
periodic GC job (Quartz or cron) that hard-deletes tombstoned entities older than 90 days.

### 1.3 Activity retention — HIGH, time-bomb

296 772 Activity nodes, date range 2026-05-19 to 2026-05-26. Every write request creates
one Activity node (via `ProvenanceCaptureFilter`), hits the `appId_unique_Activity` RANGE
index, and chains `auditHmac` → `auditPrevHmac`. At 43 812/day, the Activity class will
exceed 1M nodes within ~18 days and exceed 10M within ~7 months under sustained MFFD ingest.

The HMAC chain design is correct for integrity (each Activity records a hash of the
previous Activity's hash, making tampering detectable). However, **the HMAC chain only
needs to be consulted during an audit; it does not need to live in the hot graph
indefinitely.** The standard pattern is:

- Keep a rolling hot window (e.g. 90 days) in Neo4j for live provenance queries
- Archive older Activity nodes to a cold store (PostgreSQL `permission_audit_log` already
  exists; Activity nodes can be archived there as JSONB rows)
- Maintain the HMAC chain continuity by preserving the last archived node's hash as the
  chain anchor

**Recommendation:** Ship V65 Cypher migration (see §7) that creates a scheduled archival
procedure. Until the procedure ships, operators should manually archive Activity nodes
older than 30 days using the query in §7.

### 1.4 Missing fulltext index on DataObject.name — MEDIUM

`CypherQueryHelper.getObjectPartWithName()` (line 32) generates:

```cypher
(c:Collection {name: $name, deleted: FALSE})
```

This is an exact-equality lookup via the RANGE `unique_id_DataObject` and
`appId_unique_DataObject` indexes — but those indexes are on `id` and `appId`, not `name`.
The `name` filter resolves as a property predicate on top of a `deleted=FALSE` index scan,
which at 17 149 DataObjects is acceptable today but degrades linearly.

The `resource_labels` FULLTEXT index exists for `Resource` (ontology) nodes and uses
`db.index.fulltext.queryNodes` in `SemanticTermSearchRest`. The same pattern is not applied
to `DataObject.name` or `DataObject.description`, meaning substring/prefix search for data
objects requires a full label scan.

**Recommendation:** Ship V66 migration adding a FULLTEXT index on `DataObject(name, description)`
and add a corresponding TEXT index on `Collection(name)`. These are ~4-line migrations.

### 1.5 NULL-id TimeseriesContainer shadow nodes — HIGH

Two `:TimeseriesContainer` nodes have `id = NULL`. Per learning 2 from the 2026-05-24
audit session, these are created by the live-write path (direct MQTT ingestion) rather
than via the standard `POST /timeseriesContainers` create flow which sets `id`. The
NULL-id nodes appear on no REST list (the list query filters `c.id IS NOT NULL` or uses
the RANGE index on `id` which skips NULL), and they cannot be restored without knowing
their expected `id`, `appId`, and the full required properties (see Learning 3: restore
needs `HasAppId` label + `appId` + internal flags).

**Recommendation:** Add a smoke-alarm query to the post-ingest audit procedure:

```cypher
MATCH (c) WHERE c:TimeseriesContainer OR c:StructuredDataContainer OR c:FileContainer
WHERE c.id IS NULL
RETURN labels(c)[0] AS kind, COUNT(*) AS missing_id_count;
```

Any count > 0 indicates the live-write path is not going through the canonical
entity-creation flow. File as a backend bug.

---

## §2 — PostgreSQL + TimescaleDB Substrate

### 2.1 Schema snapshot

| Table | Rows | Notes |
|---|---|---|
| timeseries | 867 | channel definitions; 192 in orphan container 661951 |
| timeseries_data_points | 132 663 051 | hypertable; 128 439 326 in orphan container |
| importer_run | — | import lifecycle tracking |
| migration_progress | — | per-container migration state |
| migration_tasks | — | per-channel migration tasks |
| permission_audit_log | — | exists, suitable for Activity archival |
| flyway_schema_history | — | Flyway migration ledger |

### 2.2 TS-ORPHAN-MFFD — CRITICAL

Container_id=661951 exists in PostgreSQL with 192 channels and 128 439 326 datapoints
(~97% of all datapoints in the instance). No `:TimeseriesContainer` node exists in Neo4j
with `id=661951`. The direct SQL path works (`SELECT * FROM timeseries_data_points WHERE
timeseries_id IN (SELECT id FROM timeseries WHERE container_id=661951)`), but the
canonical REST path (`GET /dataObjects/{d}/timeseriesReferences/{r}/payload`) returns
empty because the TimeseriesReference → TimeseriesContainer → timeseries chain is broken
at the Neo4j side.

This is the TS-VERSIONING-SPRAWL pattern: the importer created a new TS container on a
fresh ingest run instead of reusing the predefined one. The fix is operational (predefine
TS containers via env vars `MFFD_TS_CONTAINER_*`, fail-fast at startup if missing), but
the orphan data also needs a Neo4j recovery node.

**Immediate recovery query (run ONLY after snapshot, per PRE-MUT-SNAP protocol):**

```cypher
// First check what a healthy TimeseriesContainer looks like:
MATCH (c:TimeseriesContainer) WHERE c.id IS NOT NULL AND c.appId IS NOT NULL
RETURN labels(c) AS lbls, keys(c) AS props LIMIT 1;
// Then MERGE the orphan container using the same shape.
```

The container node must carry: `id`, `appId`, `name`, `deleted`, and be labeled
`:TimeseriesContainer:HasAppId`. Without `HasAppId`, the REST listing still returns 0.

### 2.3 TimescaleDB performance — current state is good

V1.8.0 already shipped the correct tuning:
- `timeseries_data_points_id_time_idx` composite `(timeseries_id, time DESC)` — correct
- Compression policy at 7 days — correct for write/backfill patterns
- `enable_chunk_skipping('timeseries_data_points', 'timeseries_id')` — correct

The host-level tuning in `infrastructure/tweak-db-settings.sql` (4GB shared_buffers,
12GB effective_cache_size, 20 max_connections, 26MB work_mem) is appropriate for a
16GB/8-CPU host and matches TimescaleDB's own published recommendations for high-ingestion
workloads. No changes needed.

**One gap:** the `shepard_id` UUID column added in V1.11.0 is not yet used in queries —
it was added as the TS-IDa substrate for the 5-tuple → appId migration
(`aidocs/platform/87-timeseries-appid-migration.md`). This is expected; no action needed.

### 2.4 No JSONB attributes in PostgreSQL — correct

The system does not use JSONB for attributes in PostgreSQL. Attributes live as flat
properties on Neo4j nodes with the `attributes||key` prefix convention. This is the right
call: JSONB indexing at high attribute cardinality requires per-key functional indexes
which become unwieldy. The Neo4j flat-property approach is more queryable via Cypher.

---

## §3 — MongoDB Substrate

### 3.1 Storage snapshot

| Collection | Size |
|---|---|
| fs.chunks | ~530 MB (99% of 758 MB total) |
| fs.files | ~3 MB |
| Other collections | < 20 MB |

The Confluence export ZIP (532 MB) accounts for ~60% of the entire MongoDB substrate.
This is a single document artifact; normal operational growth will be in `fs.chunks`
from file uploads.

### 3.2 BUG-FILEPAYLOAD-UUID-OID — CRITICAL (open)

Confirmed present at `FileService.java:180`:

```java
var oid = new ObjectId(fileOid);  // FAILS for UUID-shaped OIDs from Garage
```

The `FileMongoId` type stores the OID as a String in the Neo4j `:ShepardFile` node.
When Garage is the storage backend, it writes UUID-shaped OIDs. The `new ObjectId()`
constructor requires a 24-char hex string and throws `IllegalArgumentException` when
given a UUID string, which the REST layer translates to HTTP 500.

The upload path (`FileService.java:114`) writes `doc.getObjectId(ID_ATTR).toHexString()`
— this is correct for MongoDB GridFS but wrong for Garage. The Garage SPI stores its own
opaque ID, not a Mongo ObjectId.

**Fix shape (backend, ~30 lines):**

```java
// In FileService.getPayload(), branch on container.getStorageType():
if (container.getStorageType() == StorageType.GARAGE) {
    return garageStorage.downloadPayload(fileOid);  // UUID string is valid here
} else {
    var oid = new ObjectId(fileOid);  // 24-char hex — safe for GridFS path
    return mongoStorage.downloadPayload(oid);
}
```

This is a pure backend fix; no migration needed. File as GitHub Issue with label `bug`,
severity `critical` (data unreachable).

### 3.3 Tombstone mismatch — MEDIUM

4 197 `StructuredDataContainer` tombstones in Neo4j have no corresponding MongoDB
collection (all have `mongoName = NULL`). As noted in §1.2, these are BUG-F artifacts
and are safe to GC. No MongoDB action needed — the Neo4j-side GC (V64 migration) closes
this finding.

### 3.4 N+1 in StructuredDataSearchService — MEDIUM

`StructuredDataSearchService.findMatchingReferences()` (identified in the
`mongodb-substrate-audit-2026-05-24.md`) issues one MongoDB query per structured data
reference to check payload existence. Under MFFD ingest with hundreds of references per
DataObject, this creates N+1 query fan-out against MongoDB. No schema migration is needed,
but a bulk lookup refactor (issue a single `$in` query for all OIDs, then cross-reference
in memory) would reduce MongoDB round-trips by ~50× for large DataObjects.

### 3.5 No schema validators on operational collections — LOW

The `StructuredData`, `StructuredDataReference`, and per-container collections have no
MongoDB schema validators (`$jsonSchema` rules). This is intentional for flexibility but
means malformed documents can be inserted without error. Given the current usage pattern
(importer writes, REST reads), this is low risk. If a future feature requires schema
enforcement (e.g., a typed TableContainer plugin), add validators at that point.

---

## §4 — Garage S3 Substrate

### 4.1 Visibility

The Garage admin API is disabled (no admin token configured); the admin REST surface
returns `AccessDenied`. Host-side volume `infrastructure_garage_data` shows 13 MB on disk,
which is consistent with the Garage key/bucket metadata store (the actual file payloads
are in the data directory). Direct object enumeration was not possible without the admin
API.

### 4.2 Current state assessment

Based on available signals:
- File uploads succeed (upload path in `FileService` is correct for Garage)
- File downloads fail for all Garage-backed files (BUG-FILEPAYLOAD-UUID-OID, §3.2)
- Bucket naming uses the `FileContainer` `appId` UUID — correct, consistent
- No lifecycle rules configured (expected at this scale; add when storage exceeds ~100 GB)

**No schema migrations apply to Garage.** The only action item is the Java bug fix in §3.2.

---

## §5 — PostGIS Substrate

PostGIS is **not installed** on the TimescaleDB/PostgreSQL container. The `\dx` output
shows only `pgcrypto`, `plpgsql`, and `timescaledb`. No geometry columns exist in
`information_schema.columns`. The `SpatialDataContainer` and `SpatialDataReference` node
types exist in Neo4j (with `appId_unique_SpatialDataContainer` index), but spatial data
payloads are not stored in PostGIS — they are presumably stored in Garage as raw GeoJSON
or similar.

**Finding:** PostGIS is a declared substrate in the architecture documentation but has
not been instantiated. The `SpatialDataContainer` Neo4j nodes exist, suggesting the
feature is at least partially operational, but the PostGIS backend is absent. Either:

- (a) Spatial payloads are stored in Garage/MongoDB and PostGIS is a planned future
  optimization for spatial queries, or
- (b) The PostGIS extension was never installed and spatial data is not currently queryable
  via ST_* functions.

**Recommendation:** Clarify the intended spatial data storage path and document it in
`aidocs/data/00-model-inventory.md`. If PostGIS is a planned substrate, add it to the
compose stack and document the install step.

---

## §6 — Cross-Substrate Patterns

### 6.1 The orphan-by-construction anti-pattern

Three independent bugs share the same root cause: the importer (or a live-write path)
creates a resource in one substrate without verifying that the canonical REST retrieval
path returns it.

| Bug | Created in | Missing in | Symptom |
|---|---|---|---|
| TS-ORPHAN-MFFD | PostgreSQL (container_id=661951) | Neo4j (no TC node) | GET /timeseriesReferences/{r}/payload returns empty |
| BUG-FILEPAYLOAD-UUID-OID | Garage (UUID OID) | FileService OID type check | GET /fileReferences/{r}/payload/{oid} returns HTTP 500 |
| TS-VERSIONING-SPRAWL | PostgreSQL (new container per run) | Importer state | Duplicate containers accumulate |

**Structural fix:** Every import success path must include a retrieval round-trip probe
before marking the unit as complete. This is already the `completeness_nonnegotiable`
principle in the feedback memory; the importer needs a post-write `GET` call for each
uploaded payload kind.

### 6.2 Soft-delete index selectivity decay

Every primary entity type (DataObject, BasicReference, StructuredDataContainer,
FileContainer, TimeseriesContainer, etc.) carries `idx_*_deleted` RANGE indexes on the
`deleted` boolean. As the tombstone fraction grows (DataObject is already at 50/50),
these indexes lose selectivity and the planner will eventually prefer a full label scan.

The correct long-term fix is periodic hard-delete GC (§1.2), not schema changes.

### 6.3 Activity node → permission_audit_log archival path exists

The `permission_audit_log` PostgreSQL table already exists (added in V1.10.0). Its schema
should accommodate Activity archival as JSONB rows, avoiding the need for a new table.
The Activity HMAC chain can be preserved by storing `(appId, auditHmac, auditPrevHmac,
startedAtMillis)` as the chain anchor when archiving.

---

## §7 — Migration Candidates

| ID | File name | Substrate | Severity | Description |
|---|---|---|---|---|
| V64 | `V64__GC_SDContainer_tombstones.cypher` | Neo4j | MEDIUM | Hard-delete 4 197 NULL-mongoName SDContainer tombstones and their dangling edges. Idempotent `MATCH ... WHERE deleted=TRUE AND mongoName IS NULL DETACH DELETE`. Include PRE-MUT-SNAP snapshot step in operator runbook. |
| V65 | `V65__Activity_retention_procedure.cypher` | Neo4j | HIGH | Create archival query + documentation for Activity nodes older than 90 days. Cannot use Neo4j scheduled triggers without APOC (not installed); ship as operator-runbook query + Quartz job stub. |
| V66 | `V66__Fulltext_DataObject_name.cypher` | Neo4j | MEDIUM | `CREATE FULLTEXT INDEX dataobject_name IF NOT EXISTS FOR (n:DataObject) ON EACH [n.name, n.description]` + `CREATE FULLTEXT INDEX collection_name IF NOT EXISTS FOR (n:Collection) ON EACH [n.name]`. 4 lines. |
| Java fix | `FileService.java:180` | Java/Garage | CRITICAL | Branch on StorageType before calling `new ObjectId(fileOid)`. No migration file needed; backend recompile + redeploy. |
| Ops | N/A | PostgreSQL + Neo4j | CRITICAL | Restore Neo4j `:TimeseriesContainer` node for container_id=661951. Requires: `id=661951`, correct `appId`, `name`, `deleted=FALSE`, labels `:TimeseriesContainer:HasAppId`. Must be done with the instance live using MERGE. |

### V64 skeleton

```cypher
// V64 — Hard-delete NULL-mongoName SDContainer tombstones (BUG-F wave).
// These 4197 nodes have: deleted=TRUE, mongoName=NULL.
// No live Mongo collection backs any of them.
// Safe to DETACH DELETE; verify with the PRE-MUT-SNAP probe first.
//
// PRE-MUT-SNAP probe:
//   MATCH (sc:StructuredDataContainer) WHERE sc.deleted = TRUE AND sc.mongoName IS NULL
//   RETURN count(sc) AS before_count;
//   -- Expected: 4197 (or close to it)
//
// Rollback: none — these nodes carry no live data. A full Neo4j backup
//   taken before migration is the recovery path.

MATCH (sc:StructuredDataContainer)
WHERE sc.deleted = TRUE AND sc.mongoName IS NULL
DETACH DELETE sc;
```

### V65 skeleton (operator-runbook query, not executable migration)

```cypher
// Activity archival query — run periodically (monthly or weekly under high load).
// Archives Activity nodes older than 90 days to permission_audit_log via JDBC
// (this must be wired as a Quartz job; Neo4j cannot call PostgreSQL directly).
//
// Step 1: Export Activity nodes to be archived.
MATCH (a:Activity)
WHERE a.startedAtMillis < (timestamp() - 90 * 24 * 60 * 60 * 1000)
RETURN a.appId, a.startedAtMillis, a.endedAtMillis, a.path, a.method,
       a.actionKind, a.status, a.agentUsername, a.auditHmac, a.auditPrevHmac
ORDER BY a.startedAtMillis ASC;
// Step 2: Insert returned rows into permission_audit_log as JSONB.
// Step 3: Delete exported nodes (only after confirmed PostgreSQL insert):
MATCH (a:Activity)
WHERE a.startedAtMillis < (timestamp() - 90 * 24 * 60 * 60 * 1000)
DELETE a;
```

### V66 skeleton

```cypher
// V66 — Fulltext index on DataObject name + description, Collection name.
// Enables prefix/substring search via db.index.fulltext.queryNodes.
// Same pattern as resource_labels (V44/V50).

CREATE FULLTEXT INDEX dataobject_name IF NOT EXISTS
  FOR (n:DataObject) ON EACH [n.name, n.description];

CREATE FULLTEXT INDEX collection_name IF NOT EXISTS
  FOR (n:Collection) ON EACH [n.name];
```

---

## §8 — What Surprised Me

**The data is there but the key is missing.** 128 million timeseries datapoints —
effectively the entire MFFD ingest payload — are sitting in PostgreSQL and have been
queryable directly via SQL the whole time. The data is not corrupt, not missing, not
malformed. A single Neo4j node (`TimeseriesContainer {id: 661951, appId: ..., deleted: FALSE}`)
with the right labels is all that stands between those 128M points and full REST
retrievability. That the REST API returns empty for this data is entirely an
infrastructure-pointer problem, not a data problem.

**Tombstone inflation at 50%.** Almost half of all DataObject nodes in the graph are
soft-deleted. This is partly expected (MFFD ingest creates and cleans up DataObjects),
but the fact that there is no GC cycle means the deleted fraction only grows. The index
selectivity problem is already present today; it will get worse with every ingest wave.

**Activity nodes will be the largest entity class within weeks.** Resource (ontology)
nodes currently lead at 375 079. Activity is at 296 772 and adding 43 812/day. At that
rate Activity overtakes Resource in ~1.8 days from the audit date. Nothing in the system is designed for this
growth rate; there is no retention policy, no archival job, no operator warning. The HMAC
chain is cryptographically correct but operationally unmanaged.

**PostGIS is declared but absent.** The Neo4j schema declares `SpatialDataContainer`
and `SpatialDataReference` node types with full indexes, but PostGIS is not installed on
the database host. This is either a staged deployment (the Neo4j side landed first) or
a gap between the architecture documentation and the actual stack. Either way, the
discrepancy should be explicit in `aidocs/data/00-model-inventory.md`.

**The Timeseries 5-tuple TEXT indexes are a premature optimization.** V8 migration
creates five separate TEXT indexes on `Timeseries` properties
(`measurement`, `device`, `location`, `symbolicName`, `field`). These exist to support
the 5-tuple channel-identity search. But the V1.11.0 SQL migration already added
`shepard_id UUID` to the PostgreSQL `timeseries` table, and the 5-tuple → appId migration
(`aidocs/platform/87`) will eventually retire these queries. The five TEXT indexes are
carrying overhead for a query pattern that is being actively phased out.

**The SD tombstone non-GC is a consequence of the orphan-by-construction pattern.**
The BUG-F wave (4 197 importer-created SD containers that were immediately wiped) left
tombstones in Neo4j because the wipe used a graph-level `deleted=TRUE` flag rather than
a `DETACH DELETE`. This is the correct approach for references that might have live data,
but for BUG-F containers that never had `mongoName` set (they were created by the
importer before the Mongo collection was provisioned), the tombstone carries no
recoverable information. Permanent deletion is safe and overdue.
