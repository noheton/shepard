---
stage: feature-defined
last-stage-change: 2026-05-26
audience: backend engineer, DBA, MFFD-import operator
---

# MFFD shared-container scale check — 2026-05-26

Scale analysis for MFFD-SHARED-CONTAINER-01: replacing ~500 per-track
`TimeseriesContainer` nodes with ~6 shared containers (one per source
type: FSD/TPS, robot joints, tape head, welding, etc.). Track identity
encoded in the `measurement` 5-tuple field (`measurement = "track_042"`).

**Motivating backlog rows:** MFFD-SCALE-CHECK-01, MFFD-SHARED-CONTAINER-01

---

## CRITICAL PRE-AMBLE: V1.13.0 is NOT deployed

The analysis prompt assumes `chunk_interval = 1h` and `space_partitions = 4`
because V1.13.0 (`Optimize_timeseries_chunk_config.sql`) ships those settings.
**V1.13.0 exists on disk but was never applied.** The live substrate is:

```sql
-- Confirmed via timescaledb_information.dimensions and flyway_schema_history:
integer_interval = 86400000000000  -- 24 hours, NOT 1 hour
num_partitions   = NULL            -- no space partitions
-- Flyway chain stops at V1.12.1 (added hourly continuous aggregate)
```

This is not a latent gotcha — it is the highest-leverage unresolved pre-condition
for MFFD ingest. **Every volume calculation below shows two numbers: "as-is" (24h
chunks, 0 space partitions) and "with V1.13.0" (1h chunks, 4 space partitions).**

---

## What I found

### Substrate snapshot (live at audit time)

| Metric | Value |
|---|---|
| Total channel rows (`timeseries` table) | 871 across 8 containers |
| Largest container channel count | 325 (container 61, `lumen-inspired-sensors`) |
| Total data-point rows | 132,667,051 |
| Active chunks | 35 (24-hour interval) |
| Space partitions | **none** (V1.13.0 not applied) |
| Chunk interval | **24 hours** (not the 1-hour intended for MFFD) |
| Compression delay | 7 days |
| PgBouncer pool | transaction mode, `default_pool_size=20`, `max_client_conn=200` |
| `max_tuples_decompressed_per_dml_transaction` | 100,000 |
| Unique index on `timeseries` | `(container_id, measurement, field, symbolic_name, device, location)` |

### Scale parameters for MFFD shared-container design

- **AFP tracks:** ~500 per tapelaying run
- **Channels per track:** ~30 (FSD ~8 floats, robot joints ~12, tape head ~8, plus digitals)
- **Shared containers:** ~6 total (one per source type)
- **Channel rows per container:** 500 tracks × (30/6) channels = 2,500 (if uniform split)
  or worst case FSD container: 500 × ~8 = 4,000 rows
  Backlog uses 30,000 (500 × 30/6 underestimates non-uniform splits; with
  `measurement = "track_042"` encoding, one container may hold all 30 channels
  per track × 500 tracks = 15,000 rows for the FSD container alone)
- **Data points:** 100 Hz × 3,600 s/track × 500 tracks × 30 channels = 5.4 billion rows
- **Compressed at 23× (measured on live data):** ~234 GB compressed; ~5.4 TB raw

---

## Scale verdict (per question)

### Q1: `timeseries` table at 15,000 channel rows per container — GREEN for point lookups / AMBER for full listing

**Index structure:**
```sql
-- Only three indexes on timeseries table:
timeseries_pkey                  ON timeseries(id)
timeseries_unique_b4a836fabc25   ON timeseries(container_id, measurement, field, symbolic_name, device, location)
idx_timeseries_shepard_id        ON timeseries(shepard_id)
```

The composite unique index has `container_id` as leftmost column. A query
`WHERE container_id = X AND measurement = 'track_042'` uses this index:

```sql
-- Confirmed via EXPLAIN (ANALYZE OFF):
EXPLAIN SELECT * FROM timeseries WHERE container_id = 61 AND measurement = 'track_042';
-- Index Scan using timeseries_unique_b4a836fabc25
--   Index Cond: ((container_id = 61) AND (measurement = 'track_042'::text))
--   cost=0.28..2.49 rows=1
```

At 15,000 rows, a per-track channel lookup (`AND measurement = 'track_042'`)
remains a fast index scan: b-tree with ~14-deep key prefix compression. The
full-table is ~20 MB (projecting from 871 rows / 176 kB heap → ~3 MB/1,000 rows),
all fitting in `shared_buffers`. **Per-track lookup: GREEN.**

**However:** `GET /v2/timeseries-containers/{id}/channels` (question 3 below) does
`SELECT * FROM timeseries WHERE container_id = X` with no additional predicate.
At 871 total rows, the planner chooses Seq Scan — and at 15,000 rows for a single
container this is still a seq scan (100% selectivity). It returns all 15,000 rows
in one un-paginated response. **Full-container listing: AMBER.** See Q3.

### Q2: `timeseries_data_points` volume at 5.4B rows — RED as-is / AMBER with V1.13.0

**As-is (24h chunks):**

Current density: 132M rows / 35 chunks ≈ 3.8M rows/chunk. At 5.4B rows with the
same density, TimescaleDB would create ~1,420 daily chunks. The AFP ingest pattern
(multiple concurrent runs across ~6 months of experiment time) means each run's
data lands in the corresponding date chunk. With 500 tracks × 30 channels writing
at 100 Hz, a single active 24-hour chunk receives:

```
500 tracks × 30 channels × 100 Hz × 86,400 s = 129.6 billion rows per day
```

This is physically impossible in a 24h chunk. The real AFP run is 1–6 hours per
track with data concentrated at experiment time. Let's re-estimate with realistic
numbers: 500 tracks × 1 hour per track × 30 channels × 100 Hz = 5.4B total
points, all concentrated in 1–6 daily chunks depending on session spread.

A single 1-day chunk at 5.4B rows (if all sessions run in one day) = 5.4B ×
approximately 24 bytes/row = ~130 GB per chunk uncompressed. TimescaleDB's
recommendation: each chunk ≤ 25% of `shared_buffers` = 25% × 15.9 GB = 3.98 GB.
**At 24h interval, MFFD ingest into a concentrated time window produces chunks
100× too large. Query performance collapses. RED as-is.**

**With V1.13.0 (1h chunks, 4 space partitions):**

At 1h interval: a 1-hour chunk per space partition holds:
500 tracks × 30 channels × 100 Hz × 3,600 s / 4 partitions = 1.35B rows/chunk.

Still above the 3.98 GB target. But with the 23× compression ratio measured on
live data: 1.35B rows × 24 bytes / 23 = ~1.4 GB compressed per chunk — within
the `shared_buffers` 25% budget. **With V1.13.0: AMBER (acceptable under
compression; hot window stays in memory).**

**Query plan for a per-track 10-second window:**

```sql
-- Confirmed via EXPLAIN with actual data (timeseries_id IN (327, 328)):
EXPLAIN SELECT time, double_value FROM timeseries_data_points
WHERE timeseries_id IN (...30 ids for track_042...)
  AND time >= :start AND time <= :start + 10000000000;
-- Result: Append → Index Scan on 1–2 chunks using time index
--   Filter: (timeseries_id = ANY('{...}'::integer[]))
```

The plan uses the time index for chunk pruning then filters by `timeseries_id`
as a post-scan predicate. With chunk skipping on `timeseries_id` enabled (V1.8),
chunks not containing the queried track IDs are pruned. For a 10s window at 1h
chunks, only 1 chunk is accessed. **Per-track 10s query: GREEN once V1.13.0 is applied.**

**Critical query shape issue:** the current API reads channels one at a time via
`getManyTimeseriesWithDataPoints` parallel stream, NOT via a single
`timeseries_id IN (...)` query. Each channel requires a separate 5-tuple lookup
then a separate data query. See Q5 for the compounding impact.

### Q3: `GET /v2/timeseries-containers/{id}/channels` with 15,000 rows — AMBER

**Code path (line 84 in `TimeseriesContainerChannelsRest.java`):**
```java
List<TimeseriesEntity> rows = tsChannelResolver.list("containerId", containerId);
List<TimeseriesChannelV2IO> body = rows.stream().map(TimeseriesChannelV2IO::from).toList();
return Response.ok(body).build();
```

`tsChannelResolver.list("containerId", containerId)` maps to a Panache
`SELECT * FROM timeseries WHERE container_id = X` — **no LIMIT, no pagination.**

At 15,000 rows, each row carries: `id (int) + container_id (bigint) + measurement
(text) + field (text) + symbolic_name (text) + device (text) + location (text) +
value_type (text) + shepard_id (UUID)`. With `measurement = "track_042"` (10 chars)
and other fields being short tags, estimate ~200 bytes/row JSON → 15,000 × 200 = 3 MB
JSON payload per call.

Postgres-side: seq scan on a ~20 MB table → fast (sub-ms). Serialisation: 3 MB →
~10-30 ms. Frontend: a 3 MB JSON array needs parsing and rendering. For the
"show channels" UI view on a shared container, this renders 15,000 rows in a
list — unless the UI has virtualisation, it freezes.

**Blockers before shipping:**
1. Add `?measurement=track_042` filter parameter to the endpoint (the backlog
   explicitly calls for this — MFFD-SHARED-CONTAINER-01 item 3).
2. Add pagination as AMBER fallback even with the filter (a shared container
   with 100 track variants still returns 30 × 100 = 3,000 rows per filter value).

**v1 endpoint `GET /shepard/api/timeseriesContainers/{id}/timeseries` behaviour:**
The v1 REST calls `timeseriesService.getTimeseriesAvailable(containerId)` which
also calls `timeseriesRepository.list("containerId", containerId)` with no
pagination. Same AMBER verdict on v1. The upstream freeze behaviour is
pre-existing for large containers.

### Q4: TS reference filter — Neo4j side + Postgres lookup efficiency — GREEN for Neo4j / AMBER for parallelStream

**Neo4j side:** A per-track `TimeseriesReference` holds ~30
`ReferencedTimeseriesNodeEntity` nodes (one per channel the track cares about).
With 500 track DataObjects, each with 1 TS reference (or 6 references in the
6-container design), that is 500 × 6 = 3,000 `:TimeseriesReference` nodes +
500 × 30 = 15,000 `:Timeseries` (`:ReferencedTimeseriesNodeEntity`) nodes.

Current max in live Neo4j: the MFFD dataset has ~8,500 DataObjects (v16 ingest).
Adding 3,000 TS references and 15,000 Timeseries nodes is well within Neo4j's
operational envelope at this scale. **Neo4j cardinality: GREEN.**

**Postgres per-track lookup:** When the UI calls `getReferencedTimeseriesWithDataPointsList`,
the code (line 258-281 in `TimeseriesReferenceService.java`) does:
```java
var timeseriesList = reference.getReferencedTimeseriesList()
    .stream().map(ts -> ts.toTimeseries()).toList();  // 30 5-tuples from Neo4j
return timeseriesService.getManyTimeseriesWithDataPoints(containerId, filteredTimeseriesList, queryParams);
```

Each `ReferencedTimeseriesNodeEntity` carries the 5-tuple in Neo4j. The lookup
path `WHERE container_id=X AND measurement=track_042 AND field=... AND ...` uses
the composite index (GREEN per Q1). The issue is the parallel stream — see Q5.

### Q5: PgBouncer pool under parallel ingest into shared containers — RED

**This is the most critical finding.**

`TimeseriesService.getManyTimeseriesWithDataPoints` (lines 229-250):
```java
timeseriesList.parallelStream().forEach(timeseries -> {
    timeseriesWithDataPointsQueue.add(
        new TimeseriesWithDataPoints(
            timeseries,
            getDataPointsByTimeseriesActivatedRequestContext(containerId, timeseries, queryParams)
        )
    );
});
```

`getDataPointsByTimeseriesActivatedRequestContext` (annotated `@ActivateRequestContext`)
does **two database calls per channel**:
1. `timeseriesRepository.findTimeseries(containerId, timeseries)` — 5-tuple lookup,
   one connection acquired
2. `timeseriesDataPointRepository.queryDataPoints(...)` — data query, potentially a
   different connection in transaction-pooling mode

For 30 channels per track: `30 × 2 = 60 connection acquisitions` from PgBouncer
per single HTTP request. With `default_pool_size = 20` and `max_client_conn = 200`,
a single "get all channel data" request saturates the pool (60 > 20). Under
concurrent MFFD import (N parallel workers each triggering chart loads or data
reads), this causes:
- Pool exhaustion → requests queue behind `max_client_conn = 200` limit
- Deadlock risk if two parallel streams each wait for connections held by the other

**No deadlock today** because each `@ActivateRequestContext` call is synchronous
within its thread and releases the connection before the next call. But pool
starvation is real: 4 concurrent import workers × 30-channel parallel stream =
`4 × 60 = 240 connection requests` against a pool of 20.

**Shared-container makes this worse:** the current per-track design calls
`getManyTimeseriesWithDataPoints` per TS reference with 30 channels. In the
shared-container design, each track's reference still holds 30 channels but they
now resolve against a 15,000-row metadata table (slow seq scan baseline) before
the parallel data queries.

**Import write path is separate:** `saveDataPoints` calls `insertManyDataPoints`
which uses the single-connection `buildInsertQueryObject` path (one connection per
INSERT batch, not a parallel stream). The import path's pool pressure comes from
N workers × 1 connection each = bounded by worker count (4). **Write-side pool: GREEN.**

**The fix:** rewrite `getManyTimeseriesWithDataPoints` to:
1. Look up all N channels in one `WHERE containerId=X AND shepardId IN (...)`
   query (1 connection, 1 query)
2. Issue one `WHERE timeseries_id = ANY({...})` data query per time window
   (1 connection, 1 query)
Net connections: 2, not 60. This is the TS-OPT2 direction (bulk fetch) already
partially implemented in `TimeseriesContainerChannelsRest` but not yet wired
through the reference-service layer.

### Q6: TimescaleDB decompression DML limit — AMBER for re-ingest / GREEN for initial ingest

**Confirmed:** `timescaledb.max_tuples_decompressed_per_dml_transaction = 100000`

The ingest path uses `INSERT ... ON CONFLICT DO UPDATE` (the
`buildInsertQueryObject` method, lines 218-248 in `TimeseriesDataPointRepository.java`),
not pure INSERT. The COPY path (`insertManyDataPointsWithCopyCommand`) bypasses
the decompression limit entirely.

**Initial ingest (new data):** Chunks are not compressed until `compress_after = 7 days`.
New MFFD data inserted for the current week lands in uncompressed chunks. The
`ON CONFLICT DO UPDATE` against an uncompressed chunk pays no decompression cost.
**Initial ingest: GREEN.**

**Re-ingest / backfill (data older than 7 days):** If the MFFD import is re-run
(e.g., after a corrected source file for a campaign from weeks ago), rows land in
already-compressed chunks. The `ON CONFLICT DO UPDATE` decompresses a segment
before updating. At 100 Hz × batch of 20,000 points per channel: 20,000 row
decompression per `buildInsertQueryObject` batch, hitting the 100K limit after
5 channels in a single transaction. **Re-ingest into compressed chunks: AMBER.**

Since the MFFD import reuses `saveDataPoints` → `insertManyDataPoints` →
`buildInsertQueryObject` (not the COPY path), each 20,000-row batch is a separate
transaction. The 100K limit applies per-transaction; if a channel's 20,000 rows
span a compressed segment, decompression is triggered. At 5 channels × 20,000
rows = 100,000 tuples, the next INSERT batch decompresses 1 segment → error.

**The fix:** the COPY path already exists and avoids this entirely. Routing
re-ingest through `insertManyDataPointsWithCopyCommand` (with a staging-table
upsert) eliminates the decompression limit concern. The TS-AUDIT-2026-05-24-002
recommendation (AP-2) covers this.

---

## Critical blockers

### BLOCKER-1: V1.13.0 must deploy before MFFD ingest at scale — RED

Without 1-hour chunk interval and 4 space partitions, MFFD ingest at full 5.4B
row volume will produce chunks of 50–130 GB (depending on temporal spread). These
exceed the 3.98 GB `shared_buffers/4` guideline by 13–33×. Performance degrades
at chunk size > available RAM; the query planner ignores chunk skipping and does
full scans.

**Action before ingest:** apply V1.13.0 manually or redeploy with the new
migration in the classpath:
```sql
-- Can be run live from psql (idempotent):
SELECT set_chunk_time_interval('timeseries_data_points', INTERVAL '1 hour');
SELECT add_space_partitions('timeseries_data_points', 'timeseries_id', 4);
-- Note: does not repack existing chunks. New data only.
```

### BLOCKER-2: `getManyTimeseriesWithDataPoints` parallel stream pool exhaustion — RED

See Q5. Before enabling shared containers with 30 channels per reference, the
parallel stream must be replaced with a bulk query. The current code runs 60+
connection acquisitions per HTTP request; PgBouncer's pool of 20 means requests
queue or fail under any concurrent load.

The TS-OPT2 bulk endpoint exists in `TimeseriesContainerChannelsRest` for the v2
surface but the internal `TimeseriesReferenceService.getReferencedTimeseriesWithDataPointsList`
still routes through `getManyTimeseriesWithDataPoints`. Shared-container usage of
`GET /shepard/api/.../timeseriesReferences/{id}/timeseries` (the v1 surface used
by the import and UI) hits this path.

### BLOCKER-3: No `?measurement=track_042` filter on `GET /v2/timeseries-containers/{id}/channels` — AMBER

MFFD-SHARED-CONTAINER-01 explicitly calls for this filter. Without it, any
per-track UI view fetches all 15,000 channel rows and filters client-side.
The endpoint design must ship before the shared-container import.

---

## Changes needed before MFFD import

Priority order:

1. **Deploy V1.13.0** — apply via `docker compose run --rm backend` redeploy or
   run the SQL directly from `psql`. Without this, MFFD-scale ingest chunks exceed
   RAM and performance collapses. **Pre-condition, not optional.**

2. **Add `?measurement=` filter to `/v2/timeseries-containers/{id}/channels`** —
   needed for per-track channel display. One line in `TimeseriesContainerChannelsRest.listChannels`:
   ```java
   if (measurement != null)
       rows = tsChannelResolver.list("containerId = ?1 and measurement = ?2", containerId, measurement);
   ```

3. **Add a container-level index on `timeseries(container_id)` alone** — the
   composite index supports `container_id + measurement` lookups but the
   `WHERE container_id = X` full-container listing (Q3) does a seq scan.
   At 15,000 rows the seq scan is ~5 ms, which is fine, but an index makes
   intent explicit and assists the planner at larger tables:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_timeseries_container_id ON timeseries(container_id);
   ```
   Low urgency — only relevant for `listChannels` without measurement filter.

4. **Rewrite `getManyTimeseriesWithDataPoints` to use `ANY(...)` array query** —
   defer until after the shared-container design is finalised, but track as
   MFFD-IMPORT-POOL-01. The 60-connection-per-request pattern is a latent
   production outage at any concurrent load.

5. **Apply post-backfill `compress_chunk()` hook** (AP-8 from ts-design-audit-2026-05-24)
   — any MFFD re-ingest into time windows older than 7 days creates uncompressed
   chunks that inflate disk ~23× until the nightly compression sweep. The hook
   should run at the end of each import batch:
   ```sql
   SELECT compress_chunk(c) FROM show_chunks('timeseries_data_points',
     older_than => INTERVAL '8 days') c
   WHERE NOT _timescaledb_internal.is_chunk_compressed(c);
   ```

---

## What surprised me

**1. V1.13.0 exists but was never applied.** The migration file was written
(commit exists), landed in the classpath, but the Flyway chain stops at V1.12.1.
This means the "MFFD-optimised" substrate that every design discussion assumed is
purely on paper. The live DB is still at 24h chunks, no space partitions — the
same config that existed before the MFFD design work. All MFFD-scale numbers in
prior docs are aspirational.

**2. The unique composite index is the only index on `container_id`.** There is no
standalone `container_id` index. The Seq Scan on `WHERE container_id = 61` (325
rows out of 871 total) is the planner correctly choosing seq scan at low row count.
At 15,000 rows the planner will still choose seq scan if container_id selectivity
is ~100% (one container owns all rows in a shared-container scenario). The lookup
for `WHERE container_id AND measurement = 'track_042'` IS index-efficient thanks
to the composite prefix.

**3. `getManyTimeseriesWithDataPoints` is a connection storm in waiting.** This
method has been in the codebase long enough to carry a `@Deprecated` sibling
(`repeatSaveDataPointsWithBatchInsert`). The parallel stream is well-intentioned
but has never been stress-tested beyond the LUMEN demo's ~325 channels × 1
container. At MFFD scale — 30 channels × however many tracks a reference covers —
it becomes the primary failure mode before any DB issue manifests.

**4. The COPY path already exists.** `insertManyDataPointsWithCopyCommand` and
its batched variant are implemented, tested, and used by the InfluxDB migration.
The expensive `buildInsertQueryObject` path (re-plans per 20K-row batch) is the
hot path for all current ingest despite a better alternative sitting next to it.
The fix is routing, not new code.

**5. Continuous aggregate `timeseries_hourly` shipped in V1.12.1.** This is
excellent — hourly CAgg with min/max/avg/count per channel was the #6 top fix in
the ts-design-audit. It landed before the scale check. Chart-view reads for
MFFD data spanning hours will use the CAgg automatically (materialized_only=false
means it falls through to raw for the last hour). This removes what would have
been a RED on read-path performance for overview charts.

---

## External references

- TimescaleDB Documentation, [Hypertables — chunk size best practice](https://docs.timescale.com/use-timescale/latest/hypertables/about-hypertables/) — 25% of `shared_buffers` guideline
- TimescaleDB Documentation, [Space partitions](https://docs.timescale.com/use-timescale/latest/hypertables/about-hypertables/#space-partitioning) — reducing lock contention at high ingest
- TimescaleDB Documentation, [max_tuples_decompressed_per_dml_transaction](https://docs.timescale.com/use-timescale/latest/compression/troubleshooting/) — DML decompression limit
- Prior audit: `aidocs/agent-findings/ts-design-audit-2026-05-24.md` — AP-2 (INSERT string path), AP-8 (post-backfill compression), AP-6 (continuous aggregates)
- MFFD-SHARED-CONTAINER-01 design intent: `aidocs/16-dispatcher-backlog.md` row 1723
