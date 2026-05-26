---
audience: admin
stage: deployed
last-stage-change: 2026-05-26
task: "DB-OPT6"
---

# Database optimisation runbook — post-ingest tuning pass

**Audience:** operators of downstream Shepard fork instances.  
**When to run:** after the first real-world-scale ingest — roughly once you have
>1,000 DataObjects and any meaningful timeseries data. Running it on an empty
development instance produces correct results but the numbers will be trivially fast
and the wins small; the pass is designed for real data.  
**What it does:** surfaces the same Neo4j and TimescaleDB hot-path issues found on
the MFFD production dataset (132M timeseries rows, ~43k Neo4j nodes) and gives you
template queries plus interpretation guidance so you can decide which wins to ship.
All fixes described here are already in the Shepard migration sequence (V75, V76, V1.17.0);
if your instance is on the current schema those migrations have already applied and this
runbook is a *verification* guide, not an emergency patch.

---

## 1. Pre-conditions

```bash
# Neo4j is up and accepting Bolt connections?
docker exec <neo4j-container> cypher-shell -u neo4j -p <password> \
  "RETURN 'ok' AS status"
# Expected: one row: "ok"

# TimescaleDB is up?
docker exec <timescaledb-container> psql -U shepard -c "SELECT version();"
# Expected: PostgreSQL version string with TimescaleDB

# Which Flyway version was last applied? (check no pending migrations block the analysis)
docker exec <neo4j-container> cypher-shell -u neo4j -p <password> \
  "MATCH (n:__Neo4jMigration) RETURN n.version ORDER BY n.installedOn DESC LIMIT 5"
docker exec <timescaledb-container> psql -U shepard -c \
  "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"
```

If the Neo4j migration cursor is below V76 or the SQL cursor is below V1.17.0,
the migrations have not run. Let the application start (the `MigrationsRunner` applies
them at startup) or run Flyway manually before proceeding.

---

## 2. Neo4j hot-path analysis

Run each `PROFILE` query from `cypher-shell` or the Neo4j Browser (`:server connect`).
The output you care about is the `db hits` column in the execution plan.

### 2.1 Permissions check (fires on every authenticated API request)

```cypher
PROFILE MATCH (e:BasicEntity {appId: $appId})-[:has_permissions]->(p:Permissions)
RETURN p
```

Substitute a real `appId` from your dataset for `$appId`.

**What to look for:**

| Plan operator | Interpretation | Action |
|---|---|---|
| `NodeByIndexSeek e:BasicEntity(appId)` | V76 index active. DB hits ≈ 12. | Nothing to do. |
| `NodeByLabelScan p:Permissions` followed by `Expand(All)` + `Filter` | No `:BasicEntity.appId` index. DB hits will be ≈ 5× node-count. | V76 missing — apply migration. |

**Threshold:** >1,000 DB hits/request on this query = investigate. >10,000 DB hits = ship
V76 immediately. On MFFD data before V76: **21,571 DB hits per authenticated request**,
reduced to **12 after V76** (1,800× win).

> **Note on sub-type indexes:** `DataObject.appId` UNIQUE constraint does NOT cover
> `MATCH (e:BasicEntity …)` queries. Neo4j 5.x index coverage is per-label; a query
> on a supertype label needs its own index even if all concrete subtypes are covered.

### 2.2 Activity time-range scan (provenance log queries)

```cypher
PROFILE MATCH (a:Activity)
WHERE a.startedAtMillis > $after
RETURN a.appId ORDER BY a.startedAtMillis DESC LIMIT 20
```

Substitute a recent epoch-ms value (e.g. yesterday in ms) for `$after`.

**What to look for:**

| Plan operator | Interpretation | Action |
|---|---|---|
| `NodeByIndexSeek a:Activity(startedAtMillis)` | V75 index active. DB hits = O(log n + result-set). | Nothing to do. |
| `NodeByLabelScan a:Activity` | Full scan — O(node count). On MFFD: **593,637 DB hits** for 296,808 Activity nodes. | V75 missing — apply migration. |

**Threshold:** >1,000 DB hits/request = investigate; >100,000 = ship V75 immediately.

### 2.3 DataObject list (collection browse)

```cypher
PROFILE MATCH (c:Collection {appId: $cid})-[:is_part_of]->(d:DataObject)
WHERE NOT d.deleted
RETURN d SKIP 0 LIMIT 50
```

Substitute a real Collection `appId` for `$cid`.

**What to look for:**

| Plan operator | Interpretation | Action |
|---|---|---|
| `NodeUniqueIndexSeek c:Collection(appId)` followed by `Expand(All)` | Correct — Collection lookup is index-backed. DB hits proportional to result-set, not graph size. | Nothing to do for the collection side. |
| `NodeByLabelScan` on `Collection` | UNIQUE constraint on `Collection.appId` is missing. | Check `SHOW CONSTRAINTS` — this should have been created at V1 bootstrap. |
| `Expand(All)` with very high DB hits despite index | Supernode problem — one Collection with tens of thousands of `is_part_of` edges. | Consider server-side cursor pagination (add `SKIP` / `LIMIT` earlier in the plan) or check for relationship-property index. |

**General guidance:**
- `NodeByLabelScan` → fix with an index on the filtered property.
- `Expand(All)` on a supernode (relationship count in the thousands) → check whether an
  edge property index helps, or whether the query needs restructuring.
- DB hits > 10,000 for a single-page list = investigate.

---

## 3. TimescaleDB continuous aggregate health check

Run from a `psql` session connected to the `shepard` database.

### 3.1 CAgg row count

```sql
SELECT count(*) FROM timeseries_hourly;
```

**Expected:** non-zero once any timeseries data has been ingested and V1.17.0 has applied.
If this returns 0 after data is present, the CAgg was never backfilled — see §3.3.

### 3.2 CAgg materialization status

```sql
SELECT view_name,
       materialized_only,
       compression_enabled,
       last_refresh_time
FROM timescaledb_information.continuous_aggregate_stats
WHERE view_name = 'timeseries_hourly';
```

**Healthy output example:**

| view_name | materialized_only | compression_enabled | last_refresh_time |
|---|---|---|---|
| timeseries_hourly | f | t | 2026-05-26 12:34:00+00 |

If `last_refresh_time` is NULL, the CAgg has never been refreshed; run the
backfill manually (§3.3) and check that V1.17.0 is applied.

### 3.3 Refresh policy job status

```sql
SELECT job_id,
       proc_name,
       scheduled,
       last_run_started_at,
       last_run_status,
       next_start
FROM timescaledb_information.jobs
WHERE proc_name = 'policy_refresh_continuous_aggregate';
```

**What to look for:**

| `last_run_status` | Interpretation | Action |
|---|---|---|
| `Success` | CAgg refresh running normally. | Nothing. |
| `Failed` | Refresh failed. Check `timescaledb_information.job_errors` for details. | Most common cause before V1.17.0: `integer_now function not set`. Apply V1.17.0. |
| NULL (never run) | No refresh has completed. | May be normal on a brand-new instance; trigger manually (§3.4). |

Also check the CAgg compression job separately:

```sql
SELECT job_id,
       proc_name,
       scheduled,
       last_run_status,
       last_run_started_at
FROM timescaledb_information.jobs
WHERE proc_name = 'policy_compress_chunks'
ORDER BY job_id;
```

If the CAgg compression job is failing with `integer_now function not set`, V1.17.0
has not applied. Let the application start to trigger Flyway, or run the migration
manually.

### 3.4 Manual CAgg backfill (when needed)

If the CAgg is cold (count = 0 or `last_refresh_time` IS NULL) and you need data
available immediately without waiting for the next scheduled refresh:

```sql
-- Materialize full history (NULL, NULL = from the beginning of time to now).
-- This is idempotent: already-present hourly buckets are skipped.
-- On 132M rows this completes in under 5 seconds (warm buffers).
CALL refresh_continuous_aggregate('timeseries_hourly', NULL, NULL);

-- Confirm:
SELECT count(*) FROM timeseries_hourly;
-- Expected: a non-zero row count (e.g. 32 534 on MFFD dataset).
```

---

## 4. Neo4j dead-index audit

Dead indexes waste write I/O on every node insert/update. Identify and assess them:

```cypher
SHOW INDEXES YIELD name, type, state, labelsOrTypes, properties, lastRead, readCount
ORDER BY readCount ASC;
```

**Interpretation guide:**

| Condition | Assessment | Action |
|---|---|---|
| `readCount = 0` AND `type = 'RANGE'` | Never read since last statistics reset — pure maintenance cost. | Candidate for removal. Review the query patterns first; if no code path queries this property, drop it. |
| `readCount = 0` AND `type = 'UNIQUENESS'` | Do NOT drop. UNIQUE constraints protect data integrity regardless of read statistics. They also prevent duplicate inserts that would corrupt the data model. | Leave in place. |
| `state = 'POPULATING'` | Index is still building. Wait until `state = 'ONLINE'` before making any drop decision. | Wait. |
| `lastRead` is very old compared to other indexes | Index exists but is rarely useful. | Investigate which queries were supposed to use it. May be a legacy migration artifact. |

To drop a dead RANGE index (after confirming it has no callers):

```cypher
DROP INDEX <name> IF EXISTS;
```

Roll back immediately if query plans degrade — Neo4j will rebuild a dropped index
from the Flyway migration file the next time the application starts.

---

## 5. Quick-win checklist

Validated against the MFFD production dataset (2026-05-26). Apply in order — each
win is independent but the auth-path fix (V76) compounds across every subsequent
request.

| Fix | Migration | Expected impact | How to verify |
|---|---|---|---|
| `BasicEntity.appId` RANGE index | V76 | 1,800× auth permission check: 21,571 → 12 DB hits per request | `PROFILE` §2.1 → `NodeByIndexSeek` |
| `Activity.startedAtMillis` RANGE index | V75 | Full scan (593,637 DB hits) → index seek (O(log n)) on time-range provenance queries | `PROFILE` §2.2 → `NodeByIndexSeek` |
| TimescaleDB CAgg `integer_now` registration + full backfill | V1.17.0 | 775× LTTB/timeseries speedup: 148 ms (TS-OPT1) → 0.4 ms (TS-OPT3 CAgg) for historical windows >1h | §3.1 row count non-zero; §3.3 job status `Success` |
| Drop dead `TEXT` indexes on Timeseries nodes | Manual (after §4 audit) | Reduced write amplification on every TS write — magnitude depends on how many dead indexes exist | `SHOW INDEXES` → `readCount = 0, type = 'RANGE'` candidates gone |

All four migrations are idempotent: re-running on an instance where they already
applied is safe and produces no side effects.

---

## 6. Rollback

Each migration ships a rollback comment. The rollback commands are:

**V75 (Activity.startedAtMillis RANGE index):**

```cypher
DROP INDEX Activity_startedAtMillis_idx IF EXISTS;
```

Source: `backend/src/main/resources/neo4j/migrations/V75__add_index_activity_startedAtMillis.cypher`

**V76 (BasicEntity.appId RANGE index):**

```cypher
DROP INDEX BasicEntity_appId_idx IF EXISTS;
```

Source: `backend/src/main/resources/neo4j/migrations/V76__add_index_basicentity_appId.cypher`

**V1.17.0 (CAgg integer_now + backfill):**

There is no meaningful rollback for the `integer_now` registration — removing it
re-breaks the CAgg compression job. The backfill (`refresh_continuous_aggregate`)
is additive; the CAgg can be cleared by truncating the materialized view, but
there is no operational reason to do so. If you need to revert to a pre-V1.17.0
state for testing, restore from a database snapshot.

Source: `backend/src/main/resources/db/migration/V1.17.0__fix_cagg_integer_now_and_backfill.sql`

---

## 7. Known traps

1. **Sub-type indexes do not cover parent labels.** `MATCH (e:BasicEntity {appId: …})`
   is NOT accelerated by `DataObject.appId` or `Collection.appId` UNIQUE constraints.
   Neo4j 5.x index coverage is per-label. If you add a new polymorphic query on a
   super-label, add a corresponding index on that super-label.

2. **`readCount = 0` does not mean an index is useless.** Statistics reset on Neo4j
   restart, after `CALL db.stats.clear()`, and on a fresh restore. An index that
   looked dead on Monday may have been the critical path on Tuesday's big ingest.
   Cross-check against the backend query patterns in `*DAO.java` before dropping.

3. **UNIQUE constraints are not optional.** The `SHOW INDEXES` output includes
   UNIQUENESS-type entries for UNIQUE constraints. These protect data integrity and
   must not be dropped even if `readCount = 0`. The planner uses them for index seeks
   exactly like RANGE indexes, but they also enforce uniqueness on write.

4. **CAgg query routing requires a warm CAgg.** The `TimeseriesDataPointRepository`
   `shouldUseCagg()` check in the backend queries `timeseries_hourly` before routing.
   If the CAgg is cold (count = 0 or empty for the requested time window), the backend
   falls through silently to TS-OPT1 (148 ms instead of 0.4 ms). No error is raised —
   the query succeeds but without the speedup. After any large import, trigger a
   manual backfill (§3.4) to restore the fast path immediately.

5. **V1.17.0 MAY emit a cosmetic warning.** CAgg compression can log
   `WARNING: chunk size is below the target chunk size` for very short experiment
   windows. This does not affect query correctness.

6. **Flyway ordering matters.** V75 and V76 are consecutive; V65–V74 must be applied
   before them. The application's `MigrationsRunner` handles ordering at startup.
   If you apply migrations manually, ensure the full sequence is in order. Running
   migrations out of sequence can leave the schema in an inconsistent state.
