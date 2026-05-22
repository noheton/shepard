---
stage: fragment
last-stage-change: 2026-05-23
---

# Timeseries Schema — Migration from Upstream

**Audience:** Operators upgrading from upstream `gitlab.com/dlr-shepard/shepard 5.2.0`
to this fork, specifically for the timeseries (TimescaleDB) schema changes.

**Companion:** `aidocs/data/68-timeseries-data-model-tuning.md` (rationale and recommendations).
**Upgrade tracker row:** `aidocs/34-upstream-upgrade-path.md` §Timeseries schema.

---

## 1. Schema diff: upstream vs. this fork

Both upstream 5.2.0 and this fork use **TimescaleDB on PostgreSQL**. The base schema
(`V1.0.0` through `V1.6.0`) is identical. This fork adds:

| Flyway version | Upstream | This fork | Change |
|----------------|---------|-----------|--------|
| V1.0.0 | ✓ | ✓ | Create hypertable, 1-day chunks |
| V1.2.0 | ✓ | ✓ | Unique constraint on timeseries dimensions |
| V1.3.0 | ✓ | ✓ | Index on timeseries_id (later dropped in V1.4.0) |
| V1.4.0 | ✓ | ✓ | Compression: segmentby=timeseries_id, policy=1 day |
| V1.5.0 | ✓ | ✓ | Migration task table |
| V1.6.0 | ✓ | ✓ | `unix_now_immutable()` + `set_integer_now_func` |
| **V1.8.0** | — | ✓ | Restore composite index, relax compression to 7 days, enable chunk skipping |
| V1.9.0 | — | ✓ | Migration progress table |
| V1.10.0 | — | ✓ | Permission audit log table |
| **V1.11.0** | — | planned | `time BIGINT→TIMESTAMPTZ` (see §3) |
| **V1.12.0** | — | planned | Continuous aggregates (see §4) |

**V1.8.0 is the critical one for operators:** it reverses an upstream regression
(V1.4.0 dropped an index that V1.3.0 added, citing "useless with compression" — this
is only true for compressed chunks; the active write chunk is uncompressed and the
missing index causes full-chunk scans). V1.8.0 restores this and adds two further
optimisations.

---

## 2. Upgrading from upstream — running migrations

Flyway runs automatically on startup if `quarkus.flyway.migrate-at-start=true`
(the default). For operators who pin migrations:

```bash
# dry-run to inspect what will run
./mvnw flyway:info -pl backend

# apply
./mvnw flyway:migrate -pl backend
```

V1.8.0 is safe to apply on a live system:
- The `CREATE INDEX IF NOT EXISTS` acquires a non-blocking `SHARE` lock.
- `remove_compression_policy` + `add_compression_policy` are metadata-only.
- `enable_chunk_skipping` is metadata-only (reads chunk statistics).

Expected runtime on a dataset with < 10 M rows: < 30 seconds.
Expected runtime on a dataset with > 100 M rows: 5–15 minutes (index build dominates).

### V1.8.0 rollback

If you need to revert V1.8.0 on a live system before the fork's `MigrationsRunner`
prevents startup, run this from psql:

```sql
-- Restore the 1-day compression policy
SELECT remove_compression_policy('timeseries_data_points', if_exists => true);
SELECT add_compression_policy('timeseries_data_points', BIGINT '86400000000000');  -- 1 day ns

-- Drop the restored index
DROP INDEX IF EXISTS timeseries_data_points_id_time_idx;

-- Disable chunk skipping (TimescaleDB 2.16+)
-- No direct disable function; dropping stats is harmless and a no-op if not set:
-- SELECT disable_chunk_skipping('timeseries_data_points', 'timeseries_id');
```

Then update Flyway's history to remove V1.8.0:
```sql
DELETE FROM flyway_schema_history WHERE version = '1.8.0';
```

---

## 3. Planned migration V1.11.0 — `time BIGINT→TIMESTAMPTZ`

> **Status:** Designed, not yet implemented. See `aidocs/data/68` §3 for rationale.

This is the highest-impact planned schema change. It cannot be done with a simple
`ALTER COLUMN` — TimescaleDB does not allow changing the type of a hypertable's time
dimension. The migration creates a new hypertable, migrates data, and swaps names.

### 3.1 Prerequisites

- TimescaleDB ≥ 2.5 (present in this fork's docker-compose stack)
- Enough free disk space for a full copy of `timeseries_data_points` (~1× current table size)
- A maintenance window or the background-copy approach described below

### 3.2 Migration SQL (operator runbook)

> This will become `V1.11.0__migrate_time_to_timestamptz.sql` when landed.
> **Do not run this manually against a running production system without reading all steps.**

```sql
-- ============================================================
-- Step 1: create the new hypertable
-- ============================================================
CREATE TABLE IF NOT EXISTS timeseries_data_points_new (
    timeseries_id INTEGER NOT NULL,
    time          TIMESTAMPTZ NOT NULL,
    double_value  DOUBLE PRECISION,
    int_value     INTEGER,
    string_value  TEXT,
    boolean_value BOOLEAN
);

SELECT create_hypertable(
    'timeseries_data_points_new', 'time',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => true
);

ALTER TABLE timeseries_data_points_new SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'timeseries_id',
    timescaledb.compress_orderby   = 'time'
);

-- ============================================================
-- Step 2: copy data (can be run offline before the swap)
-- Convert: nanoseconds → TIMESTAMPTZ
-- ============================================================
INSERT INTO timeseries_data_points_new
    (timeseries_id, time, double_value, int_value, string_value, boolean_value)
SELECT  timeseries_id,
        to_timestamp(time / 1000000000.0),  -- ns → seconds → TIMESTAMPTZ (UTC)
        double_value, int_value, string_value, boolean_value
FROM    timeseries_data_points
ON CONFLICT (timeseries_id, time) DO NOTHING;   -- idempotent re-run

-- ============================================================
-- Step 3: add constraints and indices
-- ============================================================
ALTER TABLE timeseries_data_points_new
    ADD CONSTRAINT ts_new_unique UNIQUE (timeseries_id, time),
    ADD CONSTRAINT ts_new_fk FOREIGN KEY (timeseries_id)
        REFERENCES timeseries(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS ts_new_id_time_idx
    ON timeseries_data_points_new (timeseries_id, time DESC);

SELECT enable_chunk_skipping('timeseries_data_points_new', 'timeseries_id');

-- ============================================================
-- Step 4: swap (brief lock required; schedule a maintenance window
--         or use pg_try_advisory_lock to defer)
-- ============================================================
ALTER TABLE timeseries_data_points     RENAME TO timeseries_data_points_old;
ALTER TABLE timeseries_data_points_new RENAME TO timeseries_data_points;

-- Also rename the constraint and index back to expected names
ALTER INDEX IF EXISTS ts_new_unique    RENAME TO timeseries_data_points_unique;
ALTER INDEX IF EXISTS ts_new_id_time_idx RENAME TO timeseries_data_points_id_time_idx;

-- ============================================================
-- Step 5: restore policies on new table
-- ============================================================
SELECT add_compression_policy('timeseries_data_points', INTERVAL '7 days');

-- Drop the integer_now workaround — no longer needed for TIMESTAMPTZ
SELECT set_integer_now_func('timeseries_data_points_old', NULL, true);
DROP FUNCTION IF EXISTS unix_now_immutable();

-- ============================================================
-- Step 6: verify, then drop backup (operator confirms)
-- ============================================================
-- Verification query — row counts must match:
--   SELECT count(*) FROM timeseries_data_points;
--   SELECT count(*) FROM timeseries_data_points_old;
--
-- DROP TABLE timeseries_data_points_old;  -- run manually after verification
```

### 3.3 Rollback

If the swap (step 4) needs to be reversed before the backup table is dropped:

```sql
ALTER TABLE timeseries_data_points     RENAME TO timeseries_data_points_new;
ALTER TABLE timeseries_data_points_old RENAME TO timeseries_data_points;
DELETE FROM flyway_schema_history WHERE version = '1.11.0';
```

The BIGINT schema is intact; the new table can be dropped at leisure.

### 3.4 Application-layer changes (Java DAO)

After V1.11.0 runs, `TimeseriesDataPointRepository` must convert between the
nanosecond wire format and TIMESTAMPTZ storage. Changes are **confined to that
class** — no API or model changes.

**Insert (buildInsertQueryObject → stays, adjusted parameters):**
```java
// Before: query.setParameter("time" + index, entities.get(index).getTimestamp());
// After:
query.setParameter("time" + index,
    Timestamp.from(Instant.ofEpochSecond(0, entities.get(index).getTimestamp())));
```

**Insert COPY path:** send ISO timestamp strings:
```java
// Before: sb.append(",").append(entity.getTimestamp()).append(",");
// After:
sb.append(",")
  .append(Instant.ofEpochSecond(0, entity.getTimestamp()))  // ISO-8601 UTC
  .append(",");
```

**Select (buildSelectQueryObject):**
```sql
-- time column alias: convert back to nanoseconds for the Java model
EXTRACT(EPOCH FROM time)::BIGINT * 1000000000 AS time
```

**Range parameters (startTimeNano / endTimeNano):**
```java
// Before: query.setParameter("startTimeNano", queryParams.getStartTime());
// After:
query.setParameter("startTimeNano",
    Timestamp.from(Instant.ofEpochSecond(0, queryParams.getStartTime())));
```

These four changes are the complete DAO diff for V1.11.0.

---

## 4. Planned migration V1.12.0 — Continuous aggregates

> **Status:** Designed, not yet implemented. Requires V1.11.0 (TIMESTAMPTZ) first.

```sql
-- Hourly rollup for double-valued series (most common type)
CREATE MATERIALIZED VIEW IF NOT EXISTS ts_hourly_double
WITH (timescaledb.continuous) AS
SELECT  timeseries_id,
        time_bucket('1 hour', time) AS bucket,
        AVG(double_value)           AS avg_val,
        MIN(double_value)           AS min_val,
        MAX(double_value)           AS max_val,
        COUNT(*)                    AS point_count
FROM    timeseries_data_points
WHERE   double_value IS NOT NULL
GROUP BY timeseries_id, bucket
WITH NO DATA;  -- populate async on first refresh

SELECT add_continuous_aggregate_policy('ts_hourly_double',
    start_offset      => INTERVAL '7 days',
    end_offset        => INTERVAL '10 minutes',
    schedule_interval => INTERVAL '10 minutes');

-- Daily rollup (for year-range charts)
CREATE MATERIALIZED VIEW IF NOT EXISTS ts_daily_double
WITH (timescaledb.continuous) AS
SELECT  timeseries_id,
        time_bucket('1 day', time) AS bucket,
        AVG(double_value)          AS avg_val,
        MIN(double_value)          AS min_val,
        MAX(double_value)          AS max_val,
        COUNT(*)                   AS point_count
FROM    timeseries_data_points
WHERE   double_value IS NOT NULL
GROUP BY timeseries_id, bucket
WITH NO DATA;

SELECT add_continuous_aggregate_policy('ts_daily_double',
    start_offset      => INTERVAL '90 days',
    end_offset        => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 hour');
```

**Rollback:**
```sql
SELECT remove_continuous_aggregate_policy('ts_hourly_double');
SELECT remove_continuous_aggregate_policy('ts_daily_double');
DROP MATERIALIZED VIEW IF EXISTS ts_hourly_double;
DROP MATERIALIZED VIEW IF EXISTS ts_daily_double;
```

No application-layer changes needed — TimescaleDB query rewriting routes
`time_bucket('1 hour', time)` queries to the rollup automatically.

---

## 5. Operator checklist: upgrading from upstream 5.2.0

```
[ ] Confirm TimescaleDB ≥ 2.16 (required for chunk skipping in V1.8.0)
[ ] Run: SELECT timescaledb_version();  -- must be ≥ 2.16
[ ] Take a pg_dump backup before running migrations
[ ] Review free disk space: need ~1× timeseries_data_points size free for V1.11.0
[ ] Deploy this fork — Flyway auto-runs V1.8.0 through V1.10.0 on startup
[ ] Verify startup logs: "Successfully applied N migrations" with no errors
[ ] Confirm row count unchanged:
      SELECT count(*) FROM timeseries_data_points;  -- compare to pre-migration count
[ ] (V1.11.0 when released) Run the swap step during a low-traffic window
[ ] (V1.11.0) Verify row counts match, then DROP TABLE timeseries_data_points_old
[ ] (V1.12.0 when released) Allow 1 refresh cycle (10 min) before benchmarking
```

---

## 6. Backward compatibility guarantee

| Surface | Guarantee |
|---------|-----------|
| API wire format (`timestamp` field in JSON) | **Stays nanoseconds** — no client changes needed |
| `/shepard/api/timeseries/...` paths | Byte-compatible with upstream 5.2.0 |
| `/v2/timeseries-references/...` paths | This fork only; not in upstream |
| DB schema column names | All column names preserved across migrations |
| Flyway version numbering | This fork's migrations continue from V1.7.0 (V1.7.0 is absent in upstream; V1.8.0 is safe to apply regardless) |

The one breaking surface: **clients that parse raw SQL from the DB directly**
(e.g., BI tools connecting to TimescaleDB). After V1.11.0, `time` is `TIMESTAMPTZ`
instead of `BIGINT`. Grafana and similar tools handle this automatically; custom
scripts that do `time::BIGINT` casts will need updating.
