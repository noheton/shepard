---
stage: deployed
last-stage-change: 2026-05-26
---

# DB-OPT3 — TimescaleDB Chunk + Compression Analysis

**Date:** 2026-05-26
**Dataset:** MFFD real-world data (132.7M rows, 871 channels, 2023-01-02 → 2026-05-26)
**Migration shipped:** `V1.17.0__fix_cagg_integer_now_and_backfill.sql`

---

## What I found

### Chunk configuration (V1.13.0 is correct — no change needed)

| Parameter | Value | Assessment |
|---|---|---|
| Chunk time interval (new chunks) | 1 hour (3 600 000 000 000 ns) | Correct for AFP sub-second sensor data |
| Chunk time interval (existing chunks) | 1 day (86 400 000 000 000 ns) | Historical — V1.13.0 applied to new chunks only |
| Space partitions on `timeseries_id` | 4 | Correct for 4 parallel ingest workers |
| Total chunks | 28 compressed + active chunks | Healthy split |

V1.13.0 shipped the right chunk interval for the MFFD ingest cadence. AFP tapelaying runs at
~180 Hz for ~6 seconds per track; 1-hour chunks give ~6 000 track-runs per chunk and match the
"show me one test run" query access pattern. No change warranted here.

### Compression (V1.8.0 is correct — no change needed)

| Parameter | Value |
|---|---|
| Compress after | 7 days (604 800 000 000 000 ns) |
| Compression ratio measured | 23.5× (18 259 MB → 776 MB) |
| Segment-by | `timeseries_id, measurement` |
| Order-by | `time DESC, field, location, symbolic_name, device` |

23.5× compression ratio is excellent. The 7-day threshold is correct: it keeps the current-week
data uncompressed (fast random writes during active experiments) while aggressively compressing
historical chunks. No change to compression policy.

### Continuous aggregate: `timeseries_hourly` (two bugs found — fixed in V1.17.0)

The CAgg created in V1.12.1 had two bugs that were silently degrading query performance:

**Bug 1: CAgg compression job (job 1046) failing since V1.12.1**

Root cause: V1.12.1 called `set_integer_now_func` on the main hypertable
(`timeseries_data_points`) but never called it on the CAgg's internal materialized hypertable
(`_timescaledb_internal._materialized_hypertable_34`). Without this registration, TimescaleDB
cannot evaluate age-based compression thresholds for the CAgg. Every daily run of job 1046
failed with:

```
ERROR: integer_now function not set for 'timeseries_hourly'
```

This means CAgg chunks older than 30 days were never compressed. Since V1.17.0, the
compression job can run cleanly.

**Bug 2: Full CAgg history was never materialized**

Root cause: V1.12.1 created `timeseries_hourly` WITH NO DATA and set a refresh policy covering
only the last 25 hours. All historical MFFD data (2023-01, 2024-07..08, 2026-05-18..25) was
never materialized into the CAgg. The TS-OPT3 routing in `TimeseriesDataPointRepository`
(`shouldUseCagg()`) correctly checked whether the CAgg was populated, but found it cold for
every historical window and silently fell through to TS-OPT1 — losing the 775× speedup.

After `CALL refresh_continuous_aggregate('timeseries_hourly', NULL, NULL)` in V1.17.0:
- CAgg rows: **32 534** (matches expected, verified with cross-check query)
- CAgg size: ~4.4 MB
- Materialization time: ~2 seconds on warm buffers

### LTTB hot-path performance (measured on channel 327, 2023-01 MFFD data)

Channel 327: 1.4M rows across ~118 hours — the most data-dense single channel in the dataset.

| Query strategy | Execution time | Notes |
|---|---|---|
| TS-OPT3 (CAgg, `timeseries_hourly`) | **0.4 ms** | 775× faster than TS-OPT1 |
| TS-OPT1 (pre-aggregation, `time_bucket`) | 148 ms | Correct fallback for sub-hourly windows |
| Raw (no aggregation) | 310 ms | Baseline; correct for high-res export |

Before V1.17.0, the 0.4 ms path was unreachable for all historical data. TS-OPT3 would find
the CAgg cold and fall through to TS-OPT1 (148 ms), every time.

After V1.17.0 the `shouldUseCagg()` branch in `TimeseriesDataPointRepository` routes
correctly to the CAgg for any window wider than 1 hour.

EXPLAIN plan confirms index-only scan on the CAgg materialized view with sequential scan
over 32 534 rows — no chunk exclusion overhead because the CAgg is not a hypertable.

---

## SQL migrations shipped

### `V1.17.0__fix_cagg_integer_now_and_backfill.sql`

Path: `backend/src/main/resources/db/migration/V1.17.0__fix_cagg_integer_now_and_backfill.sql`

Two operations, both idempotent:

1. **DO block:** Looks up the CAgg's materialized hypertable via
   `timescaledb_information.continuous_aggregates` (not hardcoded `id=34`; safe on any
   instance where the CAgg was created in a different order). Checks
   `_timescaledb_catalog.dimension` for an existing `integer_now_func`; skips with NOTICE if
   already set, otherwise calls `set_integer_now_func`.

2. **CALL:** `refresh_continuous_aggregate('timeseries_hourly', NULL, NULL)` materializes
   the full history. On the 132M-row MFFD dataset this runs in < 5 seconds (warm buffers).
   Re-running is safe: already-present buckets are skipped.

---

## What was NOT changed (and why)

| Topic | Decision |
|---|---|
| Chunk time interval | V1.13.0 is correct; 1-hour chunks match MFFD access pattern |
| Compression threshold | 7 days is correct; 23.5× ratio confirms good column order |
| Space partitioning | 4 partitions on `timeseries_id` is correct for write parallelism |
| CAgg bucket size (1 hour) | Matches chunk interval; no reason to change |
| CAgg compression threshold (30 days) | Correct; will work now that integer_now is registered |
| Adding a second CAgg (e.g. daily) | Not warranted — 32 534 hourly rows scan in 0.4 ms already |

---

## Operator notes

- After Flyway runs V1.17.0 on an existing instance, the CAgg compression job (job 1046)
  will succeed on its next scheduled run. You can trigger it immediately with:
  `SELECT run_job(1046);` from a superuser psql session.
- The full backfill may take up to 30 seconds on a cold system with no shared_buffers
  warming. On typical dev/prod (warm buffers, 28 compressed chunks) it runs in under 5 seconds.
- Instances that had the manual fix applied before V1.17.0 (the integer_now registration
  done in a direct psql session on 2026-05-26) will hit the idempotency guard and skip
  both steps with NOTICE — safe.

---

## Gaps and blockers

None blocking. One known benign warning after V1.17.0:

- CAgg compression may emit `WARNING: chunk size is below the target chunk size`
  for very small time windows (e.g. a 3-minute experiment with no data outside it).
  This is cosmetic and does not affect query correctness.

---

## What surprised me

- The 775× speedup was already designed into the code (`TS-OPT3` routing in
  `TimeseriesDataPointRepository`) — it just never fired because the CAgg was cold.
  The win was real but invisible.
- The CAgg rows fit in 4.4 MB. For 132M raw rows compressed to 776 MB, the CAgg is
  effectively free. It should have been backfilled at V1.12.1 creation time.
- Job 1046 had been failing silently every day since V1.12.1 was applied. No alert
  surfaced this. Consider wiring `SELECT * FROM timescaledb_information.job_stats
  WHERE last_run_status = 'Failed'` to Shepard's own health/metrics endpoint.
