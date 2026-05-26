---
title: "DB-OPT2: Hot-path index analysis — Neo4j + TimescaleDB"
stage: deployed
last-stage-change: 2026-05-26
task: "DB-OPT2 (#232)"
---

# DB-OPT2: Hot-path index analysis

**Measured against:** live MFFD data, 2026-05-26  
**Neo4j version:** 5.26 (container `infrastructure-neo4j-1`)  
**TimescaleDB:** 2.24.0-pg16 (container `infrastructure-timescaledb-1`)  
**Migrations shipped:** V75 (Activity.startedAtMillis), V76 (BasicEntity.appId)

---

## What I found

### Neo4j: two clear index wins

#### Win 1 — Activity.startedAtMillis (V75)

**Query (provenance / activity log range scans):**
```cypher
MATCH (a:Activity) WHERE a.startedAtMillis > <epoch_ms>
RETURN a.appId ORDER BY a.startedAtMillis DESC LIMIT 20
```

**Before (no index):**
- Operator: `NodeByLabelScan a:Activity`
- DB hits: **593,637** (full scan of 296,808 nodes × ~2 reads each)
- Wall time: 276 ms

**After (V75 RANGE index):**
- Operator: `NodeByIndexSeek a:Activity(startedAtMillis)` (expected post-migration)
- DB hits: O(log n) + result-set size

**Migration:** `V75__add_index_activity_startedAtMillis.cypher`

---

#### Win 2 — BasicEntity.appId (V76) — UNEXPECTED CRITICAL

**Query (permissions check — fires on EVERY authenticated API request):**
```cypher
MATCH (e:BasicEntity {appId: $appId})-[:has_permissions]->(p:Permissions) RETURN p
```
Source: `PermissionsDAO.findByEntityNeo4jId()` — called by `PermissionsService` on every
REST request that reaches the security layer.

**Before (no :BasicEntity index):**

The Cypher planner had no index on `:BasicEntity.appId` (43,673 nodes).
Sub-type indexes (`Collection.appId`, `DataObject.appId`, etc.) do NOT cover parent labels.
The planner instead chose:
1. `NodeByLabelScan p:Permissions` → 4,368 hits
2. `Expand(All)` backwards over `has_permissions` → 4,231 traversals  
3. `Filter e.appId AND e:BasicEntity` against all reached nodes

Total: **21,571 DB hits per API request**.

**After (V76 RANGE index, verified on live 2026-05-26):**

Index `BasicEntity_appId_idx` created manually on live for verification.
Plan re-run:
- Operator: `NodeByIndexSeek e:BasicEntity(appId)`
- DB hits: **12** (1,800× reduction)
- Confirmed: `PROFILE` returns 1 row, 12 DB hits.

**Migration:** `V76__add_index_basicentity_appId.cypher`

> **Note:** V76 index already exists on live (created during verification pass).
> Flyway's `IF NOT EXISTS` will skip it safely.

---

### Neo4j: paths investigated but below threshold or N/A

| Query | Finding |
|-------|---------|
| `MATCH (do:DataObject {appId:$id})-[:has_annotation]->(a:SemanticAnnotation)` | Each subtype (`DataObject`, etc.) already has a UNIQUE constraint on `appId`. Plan uses `NodeUniqueIndexSeek`. OK. |
| `SemanticAnnotation.subjectAppId` | Property does not exist on live data. Annotations are graph-linked via `has_annotation`, not via a denormalized property. N/A. |
| `MATCH (c:Collection {appId:$id})-[:has_dataobject]->(do:DataObject)` | Uses `NodeUniqueIndexSeek` on `c:Collection(appId)`. Fast. |

---

### TimescaleDB / PostgreSQL: no migrations needed

| Query | Finding |
|-------|---------|
| `timeseries_data_points` range scan | TimescaleDB auto-manages per-chunk indexes on `(timeseries_id, time)`. Plan: `ChunkAppend → IndexScan`. Already optimal. |
| `channel_metadata` 5-tuple lookup | Compound index `channel_metadata_unique_idx (container_id, measurement, field, symbolic_name, device, location)` exists. `IndexScan` confirmed. |
| `timeseries.container_id` scan | `Seq Scan` on 871 rows = 0.2 ms. Below threshold; index would add write overhead for negligible read gain. |
| `permission_audit_log` | `idx_permission_audit_log_user_id` and `idx_permission_audit_log_entity_id` exist. Covered. |

---

## Opportunities

1. **V75 + V76** are the two clear wins. Both shipped.
2. Once V75 applies, any time-range activity log query (`GET /v2/activity?after=…`) moves from
   full-scan to index-seek. Impact is proportional to Activity node growth (296,808 today).
3. V76 is the most impactful: every API request is cheaper. With 43,673 `BasicEntity` nodes
   and every auth'd request doing a permissions check, this compounds at request rate.

---

## Gaps & blockers

- **V65–V74** are file-present but not applied on live (Flyway shows last applied = V64).
  The V75/V76 files are consistent with that numbering but will not apply until Flyway
  runs in sequence. Operator must ensure V65–V74 are applied first (or let the app
  startup MigrationsRunner handle them in order).

---

## What surprised me

- **BasicEntity.appId** was the bigger win by far, even though the task spec only
  called out `Activity.startedAtMillis`. The permissions hot path hits 21,571 DB nodes
  per API request — about 1,800× the post-index cost. This was invisible until we
  traced the actual backend query in `PermissionsDAO.java` (the task spec suggested a
  different query shape that doesn't exist).
- **Sub-type indexes don't cover parent labels** in Neo4j 5.x. `DataObject.appId` UNIQUE
  constraint provides zero benefit for `MATCH (e:BasicEntity {appId:…})`. This is a
  subtle but critical design constraint: every polymorphic query on a super-label needs
  its own index, even if all children are covered.
