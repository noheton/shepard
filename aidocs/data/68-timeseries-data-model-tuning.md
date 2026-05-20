# TimescaleDB Data Model Tuning — Recommendations

**Status:** Design doc — V1.8.0 schema fixes landed; this covers what comes next.
**Audience:** Contributors and operators tuning the timeseries backend.
**Baseline:** Post-V1.8.0 schema (composite index restored, 7-day compression window, chunk skipping enabled).

---

## 1. Current schema (V1.8.0 state)

```
timeseries (id SERIAL PK, container_id BIGINT, measurement TEXT, field TEXT,
            symbolic_name TEXT, device TEXT, location TEXT,
            value_type TEXT CHECK IN ('Boolean','Integer','Double','String'))
  UNIQUE (container_id, measurement, field, symbolic_name, device, location)

timeseries_data_points (timeseries_id INT → timeseries.id ON DELETE CASCADE,
                         time BIGINT NOT NULL,          -- ← nanoseconds since epoch
                         double_value DOUBLE PRECISION,
                         int_value    INTEGER,
                         string_value TEXT,
                         boolean_value BOOLEAN)
  UNIQUE (timeseries_id, time)
  Hypertable on `time` (BIGINT), 1-day chunks
  Compression: segmentby=timeseries_id, orderby=time, policy=7 days
  Index: (timeseries_id, time DESC)
  Chunk-skipping on timeseries_id
```

V1.8.0 fixed the three highest-impact schema problems (index, compression window, chunk skipping).
The remaining bottlenecks are at a higher architectural level.

---

## 2. Remaining bottlenecks — priority order

| # | Area | Problem | Severity | Effort | Target version |
|---|------|---------|----------|--------|----------------|
| 1 | Schema | `time` is `BIGINT` nanoseconds, not `TIMESTAMPTZ` | 🟥 HIGH | 2 d | V1.11.0 |
| 2 | Schema | No continuous aggregates — every chart query scans raw rows | 🟥 HIGH | 1 d | V1.12.0 |
| 3 | Ingest | Regular insert path uses JDBC parameter binding (O(N) params/batch) | 🟧 MED | 1 d | backend PR |
| 4 | Query | Stats endpoint aggregates without rollup — full scans for summary views | 🟧 MED | 0.5 d | after #2 |
| 5 | Schema | Polymorphic value columns (3 of 4 are NULL per row) | 🟨 LOW | 3 d+ | defer |

---

## 3. Issue 1 — `time BIGINT` vs `TIMESTAMPTZ`

### Root cause

TimescaleDB is designed around a native time column. Using `BIGINT` nanoseconds
loses multiple planner optimizations:

- **Partition pruning** — the planner can't use native `BETWEEN '2025-01-01' AND '2025-01-02'`
  operators; every range query must be expressed in raw nanoseconds, preventing
  TimescaleDB's pushdown optimizations for `TIMESTAMPTZ` ranges.
- **Continuous aggregates** — work with BIGINT but require the `integer_now_func`
  workaround (our `unix_now_immutable()`). The refresh mechanics are simpler and
  more robust with TIMESTAMPTZ.
- **`time_bucket` efficiency** — TIMESTAMPTZ buckets align with calendar units
  (hours, days, weeks) without off-by-one edge cases in the ns arithmetic.
- **Tooling compatibility** — Grafana, pgAdmin, and `psql \copy` all consume
  TIMESTAMPTZ natively; BIGINT nanoseconds require application-side conversion.

### Recommendation

Migrate `timeseries_data_points.time` from `BIGINT` (nanoseconds) to `TIMESTAMPTZ`.
The wire-format API stays nanoseconds — only the DB storage and DAO layer change.

**Conversion formula:**
```
BIGINT nanoseconds → TIMESTAMPTZ:   to_timestamp(time / 1e9)
TIMESTAMPTZ → BIGINT nanoseconds:   EXTRACT(EPOCH FROM time)::BIGINT * 1000000000
```

### Backward compatibility

| Layer | Change needed | Impact |
|-------|--------------|--------|
| API wire format | **None** — stays Long nanoseconds | Zero |
| `TimeseriesDataPoint.timestamp` | **None** — stays Long nanoseconds | Zero |
| `TimeseriesDataPointRepository` inserts | Convert ns→TIMESTAMPTZ in SQL: `to_timestamp(:time / 1e9)` | Low |
| `TimeseriesDataPointRepository` reads | Alias back to ns: `EXTRACT(EPOCH FROM time)::BIGINT * 1000000000 AS time` | Low |
| COPY insert path | Send ISO-8601 timestamp strings instead of raw integers | Low |
| `unix_now_immutable()` function | **Drop** — no longer needed for TIMESTAMPTZ hypertable | Cleanup |
| Existing client code | Zero — nanosecond round-trip preserved | Zero |

The DAO changes are mechanical: two `to_timestamp()`/`EXTRACT(EPOCH FROM ...)` wrappers
in `buildInsertQueryObject` and `buildSelectQueryObject`. The COPY path sends
`to_char(to_timestamp(time/1e9), 'YYYY-MM-DD HH24:MI:SS.US')`.

### Migration approach (V1.11.0)

TimescaleDB does not allow altering the time dimension column type in place. The
only supported path is: create new hypertable → copy → swap names. For large
deployments this is a long-running migration that must be signalled to operators.

See `aidocs/data/69-timeseries-upstream-migration.md` for the full operator runbook
covering this migration.

Key constraints for the Flyway migration script:
- **Must be idempotent**: check `IF NOT EXISTS` on new table creation.
- **Data copy** must be chunked or done before the swap to avoid long lock holds.
- Prefer a background-copyable approach: create the new table, copy data offline,
  acquire brief `ACCESS EXCLUSIVE` only for the final rename.
- Provide a rollback file `V1.11.0__R__rollback_timestamptz.sql` that renames back
  and re-inserts from the backup table (operator-runnable from psql).

---

## 4. Issue 2 — Missing continuous aggregates

### Root cause

Every call to `queryDataPoints()` with an aggregate function or time-bucket
parameter executes a `time_bucket()` query against raw `timeseries_data_points`
rows. For a series with one year of 1 Hz data (≈31 M rows), a "show me the daily
average for the past year" query reads all 31 M rows every time — with no caching.

The chart view endpoint (`TimeseriesContainerChartViewRest`) and the stats endpoint
(`TimeseriesContainerStatsRest`) are the biggest callers of this pattern.

### Recommendation

Add **continuous aggregates** (TimescaleDB materialised views with automatic refresh)
for the two most common rollup granularities:

```sql
-- Hourly rollup — serves charts spanning days to weeks
CREATE MATERIALIZED VIEW ts_hourly_double
WITH (timescaledb.continuous) AS
SELECT  timeseries_id,
        time_bucket('1 hour', time)  AS bucket,
        AVG(double_value)            AS avg_val,
        MIN(double_value)            AS min_val,
        MAX(double_value)            AS max_val,
        COUNT(*)                     AS point_count
FROM    timeseries_data_points
WHERE   double_value IS NOT NULL
GROUP BY timeseries_id, bucket;

-- Refresh every 10 minutes, covering the rolling 7-day hot window
SELECT add_continuous_aggregate_policy('ts_hourly_double',
    start_offset => INTERVAL '7 days',
    end_offset   => INTERVAL '10 minutes',
    schedule_interval => INTERVAL '10 minutes');
```

Repeat for `int_value`. (String and Boolean series don't benefit from numeric aggregates.)

The Java query layer does **not** change — TimescaleDB's query rewriting picks the
rollup automatically when the `time_bucket` interval is ≥ 1 hour. For finer
intervals (sub-hourly) the planner falls back to raw data.

### Backward compatibility

Zero API impact. Continuous aggregates are transparent to callers. The first-time
materialisation on deploy may take minutes on large datasets; this runs in the
background and does not block reads or writes.

### Interaction with issue 1

Continuous aggregates work with BIGINT hypertables but the `start_offset` /
`end_offset` policies must be expressed in nanoseconds, which is awkward. The clean
path is: land issue 1 (TIMESTAMPTZ) first, then add continuous aggregates using
native `INTERVAL` offsets. If scheduling forces them in the other order, BIGINT CAGs
are still valid — just use nanosecond arithmetic in the policy.

---

## 5. Issue 3 — JDBC parameter binding for bulk ingest

### Root cause

`buildInsertQueryObject()` generates a native SQL insert with one `(:timeseriesId,
:timeN, :valueN)` triple per row. For a 20 k-row batch this binds 60 001 parameters
— each parsed, allocated, and garbage-collected separately. The `COPY` path
(`insertManyDataPointsWithCopyCommand()`) already exists but is only wired up for the
InfluxDB→TimescaleDB migration.

### Recommendation

**Route all regular ingest through the COPY path.** The `insertManyDataPoints()`
method can be rewritten to call `insertManyDataPointsWithCopyCommand()` for batches
above a configurable threshold (default: any batch ≥ 1 row). The COPY path is
already ON CONFLICT-safe (it uses upsert semantics via a staging table or deduplication
pre-copy).

Caveat: the current COPY path does not enforce the `ON CONFLICT DO UPDATE` semantic
(it uses raw COPY, not INSERT … ON CONFLICT). For append-only ingest this is fine.
For sensor back-fill (duplicate timestamps) a staging-table approach is needed:

```sql
-- Staging table (session-scoped, unlogged)
CREATE TEMP TABLE ts_ingest_staging (LIKE timeseries_data_points INCLUDING DEFAULTS);
COPY ts_ingest_staging FROM STDIN WITH (FORMAT csv);
INSERT INTO timeseries_data_points
    SELECT * FROM ts_ingest_staging
    ON CONFLICT (timeseries_id, time) DO UPDATE
        SET double_value  = EXCLUDED.double_value,
            int_value     = EXCLUDED.int_value,
            string_value  = EXCLUDED.string_value,
            boolean_value = EXCLUDED.boolean_value;
DROP TABLE ts_ingest_staging;
```

Expected speedup: 5–15× for batches ≥ 1 000 rows.

---

## 6. Issue 5 — Polymorphic value columns (deferred)

The current schema uses four nullable value columns (`double_value`, `int_value`,
`string_value`, `boolean_value`). Three are NULL per row. Reasons this is **not**
an immediate priority:

- TimescaleDB's columnar compression collapses NULL-dominated columns to near-zero
  bytes; the storage cost is trivial.
- Splitting to type-specific tables would require migrating all existing data and
  updating the full DAO/service/REST stack. The effort is 3–5 days minimum.
- The `value_type` column on `timeseries` already encodes the type; the query layer
  never reads the wrong column.

**Revisit when:** continuous aggregates become painful to maintain across four
type-variant views, or if schema-less multi-type series (a future feature request)
require a different representation.

---

## 7. Summary table

| Issue | Action | Target | Blocks |
|-------|--------|--------|--------|
| `time` as BIGINT | Migrate to TIMESTAMPTZ (V1.11.0) | Next sprint | — |
| No continuous aggregates | Add hourly CAGs (V1.12.0) | After V1.11.0 | Charts, stats |
| JDBC batch ingest | Swap to COPY path | Backend PR | — |
| Stats full scans | Use CAG rollups in stats queries | After CAG | — |
| Polymorphic columns | Defer | Post-v6 | — |
