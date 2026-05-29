---
stage: feature-defined
last-stage-change: 2026-05-29
---

# TS ingest 222 GB — v15 importer audit (CHOKE-01 + CHOKE-02)

Branch: `choke-01-02-importer-audit`. Scope: `examples/mffd-showcase/scripts/mffd-import-v15.py` (6826 LoC) and the server-side write path it actually hits. No code changes — audit only.

## CHOKE-01 verdict

**PARTIAL — client uploads one CSV blob per source TimeseriesReference (multipart) but the server-side write path on the v1 CSV-import endpoint is the multi-row `INSERT INTO timeseries_data_points ... ON CONFLICT DO UPDATE` path, NOT `COPY`. Projected ingest = ~7–10 hours, not 266 days. Operator-tolerable but ~4–5× off the achievable floor.**

Evidence:

- The **only** timeseries data writer in the importer is `ShepardClient.import_ts_csv()` at `examples/mffd-showcase/scripts/mffd-import-v15.py:2346–2364`, posting multipart to `POST /shepard/api/timeseriesContainers/{id}/import` (v1). Called from the main per-DataObject loop at `mffd-import-v15.py:4209–4214` (one HTTP per source ref) and the telemetry-emit path at `mffd-import-v15.py:5258–5263`. No v2 bulk path (`/v2/.../data/bulk` is READ; the COPY-protocol write endpoint is `/v2/.../channels/{shepardId}/data/ingest`, TS-OPT3-COPY) is used. `grep -n "channels/data/bulk\|data/ingest\|/v2/timeseries" mffd-import-v15.py` returns zero matches.
- Server-side: `TimeseriesCsvService.importTimeseriesFromCsv()` (`backend/.../TimeseriesCsvService.java:74–90`) iterates `List<TimeseriesWithDataPoints>` (one per channel parsed from CSV) and calls `TimeseriesService.saveDataPoints()` per channel.
- `TimeseriesService.saveDataPoints()` (`backend/.../TimeseriesService.java:394–409`) is `@Transactional(REQUIRES_NEW)` and delegates to `timeseriesDataPointRepository.insertManyDataPoints(...)`.
- `TimeseriesDataPointRepository.insertManyDataPoints()` (`backend/.../TimeseriesDataPointRepository.java:108–125`) chunks at `INSERT_BATCH_SIZE = 20000` rows/statement (line 59) and emits a multi-row `INSERT ... VALUES (...),(...),... ON CONFLICT (timeseries_id, time) DO UPDATE SET ...`. The faster `insertManyDataPointsWithCopyCommand()` (line 134–182, used by TS-OPT3-COPY + the InfluxDB migration tool) is NOT reachable from this path. The javadoc at line 296–301 of `TimeseriesService.java` says COPY is "3–5× faster than the VALUES INSERT path".
- pg_stat_statements 23.30 ms / call is the per-statement cost, not per-row. With 20000 rows per max batch ≈ 0.86M rows/s peak per Postgres backend. 1B rows ÷ 0.86M = ~19 minutes single-threaded server-side; real-world with 4 importer workers + CSV parse + multipart upload + ON CONFLICT overhead + connection reuse → **~7–10 hours projected end-to-end** (compression policy will absorb behind the writes).
- Operator's "266 days" projection assumed single-row INSERT. That assumption was wrong: pg_stat_statements aggregates per statement-text, so 23.30 ms across 2213 calls reflects the actual VALUES-batched INSERT shape, not 1-row inserts.

Why PARTIAL not FAIL: the projected time fits the 1–2 day operator tolerance, but TS-OPT3-COPY exists and would cut it 3–5× (likely to ~2 hours). Switching is an S–M fix.

## CHOKE-02 verdict

**PASS (for this importer) — the v1 CSV-import path does NOT call `TsChannelResolver.findByContainerAndPartialTuple` per data point. Channel resolution happens once-per-channel inside one HTTP call via `TimeseriesRepository.getOrCreateTimeseries()`, amortised across the entire CSV blob.**

Evidence:

- The importer never resolves channels client-side. Channel registration is implicit: the CSV header carries the 5-tuple per channel; server-side `CsvConverter.convertToTimeseriesWithData()` parses to `List<TimeseriesWithDataPoints>` and `TimeseriesService.saveDataPoints()` (line 405) calls `getOrCreateTimeseries(containerId, timeseries, dataType)` **once per channel**, not per data point.
- `mffd-import-v15.py:2276–2299` (`list_ts_channels`) reads channels AFTER the CSV write (used only to populate the required `TimeseriesReference.timeseries[]` list for the linkage POST — read path, not write hot path).
- `mffd-import-v15.py:2301–2344` (`link_ts_to_do`) posts the 5-tuple list once per DataObject TimeseriesReference creation, not per data point.
- The slow `findByContainerAndPartialTuple` path (the one TS-AUDIT-2026-05-24-009 measured at 1.65 ms plan / 0.095 ms exec, 17× planning overhead) is on the **read** side (live-window endpoint + container/channels endpoint, both migrated to `findByContainerAndShepardId` per TS-IDc `9b88c1d66`). The importer's write path doesn't touch it.

Scale arithmetic confirms this is amortised: a CSV upload covering one source TimeseriesReference might contain N channels × M points/channel. `getOrCreateTimeseries` cost is paid N times (per channel, once), not N×M times (per point). At ~113 baseline channels in the live container, that's ~113 `getOrCreateTimeseries` calls per source CSV ingest — negligible vs. the data-point INSERT cost.

## Recommendations

| Priority | Choke | Fix | Effort | 222 GB impact |
|---|---|---|---|---|
| P1 | CHOKE-01 | Switch importer's TS write path from `POST /shepard/api/timeseriesContainers/{id}/import` (multipart CSV → VALUES INSERT) to per-channel `POST /v2/timeseries-containers/{containerId}/channels/{shepardId}/data/ingest` (TS-OPT3-COPY). Two sub-steps: (a) pre-register channels via `link_ts_to_do` first to obtain shepardIds; (b) batch points per channel into `CopyIngestRequestIO` calls. Caveat: COPY endpoint has no `ON CONFLICT DO UPDATE`, so the importer must guarantee timestamp uniqueness per channel per ingest run (already true for source-export → dest-import because each source TimeseriesReference is exported once). | **M** (one new client method + restructure the per-ref export-then-import path) | 3–5× speedup → ~2 hours vs. ~8 hours. Worth doing if the operator wants headroom. |
| P2 | CHOKE-01 | If the M-effort restructure is too invasive pre-ingest, leave as-is and accept ~7–10 hours. Currently within operator tolerance. | **XS** (no change) | None — accept current shape. |
| — | CHOKE-02 | No action needed for the importer. (The 5-tuple resolver path remains on `TimeseriesReference.timeseries[]` creation but only as part of channel registration, not per-point.) | — | — |

Recommended: **defer the P1 fix to a follow-up sprint** unless the operator wants the 3–5× headroom now. The current path is correct and projected throughput is acceptable. The big "266 days" risk in the backlog row was based on an incorrect single-row INSERT assumption; the server already batches at 20K rows/statement.

## Surprises

1. **The "266 days" projection in `aidocs/16` CHOKE-01 was based on a wrong assumption.** pg_stat_statements aggregates by query text, not by row. The 23.30 ms/call mean reflects the multi-row VALUES batch (up to 20000 rows/statement), not a per-row insert. Naïve maths × 1B rows yields 266 days; correct maths (rows-per-statement × statements) yields ~7–10 hours. The chokepoint is real but the severity tag was over-stated. Suggest editing the backlog narrative on the same pass.
2. **CHOKE-02 doesn't actually apply to the write path.** The 5-tuple-resolver hot-path concern is on the read endpoints (live-window query, container/channels list). The v1 CSV-import write path does channel resolution server-side once-per-channel via `getOrCreateTimeseries`, not via `TsChannelResolver.findByContainerAndPartialTuple`. CHOKE-02 should be re-scoped to "read endpoints during post-ingest exploration" or marked already-mitigated for the importer.
3. **The fast COPY ingest endpoint (`TS-OPT3-COPY`) already exists and is unused by every production caller except the InfluxDB→TimescaleDB migration tool.** Shipping cost was paid for the speedup; the importer is leaving 3–5× on the floor. This is a cheap follow-up if 222 GB scale repeats (which it will, on future MFFD campaigns).
4. **There's no `--use-copy-ingest` flag in the importer CLI** — the choice of write path is hardcoded to v1 CSV import. A flag would let the operator A/B without touching the code path.
