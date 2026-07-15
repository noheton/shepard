---
stage: fragment
last-stage-change: 2026-07-15
---

# APISIMP Sweep — fire-613 (2026-07-15)

Scan of `backend/src/main/java/de/dlr/shepard/v2/**/*IO.java`,
`plugins/*/src/main/java/**/*IO.java`, and handler `toIO()` / `create()` / `patch()` call
sites for remaining violations of the APISIMP mandate:

- Nanosecond / millisecond `long`/`Long` timestamp fields on the v2 REST wire → must be ISO 8601 `String`
- Duration-as-millis `long`/`Long` fields on the v2 REST wire → must be ISO 8601 duration `String`
- Numeric internal IDs (Neo4j node IDs, serial PKs) on the v2 wire → must be removed or `@Schema(deprecated = true)`

## Scope exclusions

- `/shepard/api/` v1 wire is frozen — changes to v1 IO types are out of scope.
- Finding 2 below (`APISIMP-VIDEO-WALLCLOCK-NANOS`) was discovered on the stale local working
  tree; the corresponding fix is already merged via PR #2574 (fire-613). It is recorded here for
  completeness but not filed as a new backlog row.

---

## Finding 1 — APISIMP-PROVSTATS-BUCKET-MILLIS (size: XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/provenance/io/ProvenanceStatsIO.java:44`

**What:** `private long bucketMillis` carries the bucket-width duration as raw milliseconds on
the `GET /v2/provenance/stats` wire. `@Schema` description reads "Width of one sparkline bucket
in millis (daily = 86_400_000; weekly = 604_800_000)." This is a duration-as-number violation of
the APISIMP mandate — the same mandate that required ISO 8601 for `uptimeMillis` →
`APISIMP-METRICS-UPTIME-MILLIS-TO-ISO` (fire-611). The file was last touched by the
`APISIMP-PROVENANCE-STATS-BUCKET-ARRAY` row which replaced `long[]` arrays with typed `BucketIO`
records but left `bucketMillis` unconverted.

**Fix:** Rename to `bucketDuration`, change type to `String`, emit ISO 8601 duration
(`Duration.ofMillis(bucketMs).toString()` → `"PT86400S"` for daily, `"PT604800S"` for weekly).
Update `ProvenanceStatsService.compute()` to pass an ISO 8601 string. Update `@Schema`
description and example. Update any frontend consumer that parses `bucketMillis` as a number.

**AC:** `GET /v2/provenance/stats` returns `"bucketDuration": "PT86400S"` (not
`"bucketMillis": 86400000`). OpenAPI schema shows `type: string`. Backend tests pass;
`npm run typecheck` green.

---

## Finding 2 — APISIMP-VIDEO-WALLCLOCK-NANOS (already fixed — do not file)

**File:** `plugins/video/src/main/java/de/dlr/shepard/v2/video/handlers/VideoStreamReferenceKindHandlerLogic.java:74`

Discovered on stale local working tree (pre-PR-#2574). PR #2574 (`APISIMP-VIDEO-WALL-CLOCK-TO-ISO`,
fire-613) already converted `wallClockTimestamp` from epoch-nanosecond `Long` to ISO 8601 `String`
in both `VideoStreamReferenceIO.java` and `VideoStreamReferenceKindHandlerLogic.toIO()`.
No new row needed.

---

## Finding 3 — APISIMP-TSREF-TIMEWINDOW-NANOS (size: S)

**Files:**
- `backend/src/main/java/de/dlr/shepard/context/references/timeseriesreference/io/TimeseriesReferenceIO.java:23,27`
- `backend/src/main/java/de/dlr/shepard/v2/references/handlers/TimeseriesReferenceKindHandler.java:83–84`

**What:** `TimeseriesReferenceKindHandler.toIO()` calls `io.put("start", kindIO.getStart())`
and `io.put("end", kindIO.getEnd())`, where `getStart()`/`getEnd()` return `long` nanosecond-epoch
values (`@Schema description = "Start of time window in nanoseconds since Unix epoch."`). These raw
nanosecond integers land on the v2 `ReferenceV2IO` map served at
`GET /v2/data-objects/{appId}/references?kind=timeseries`. The APISIMP rows that converted
`start`/`end` on the channel-data and bulk-data endpoints left the reference time-window fields
untouched. `TimeseriesReferenceIO.start`/`end` are `long` in the v1 IO — changing their type
would break the v1 wire; the conversion must live solely in the v2 handler.

**Fix:** In `TimeseriesReferenceKindHandler.toIO()`, emit:
`io.put("start", nanosToIso(kindIO.getStart()))` and `io.put("end", nanosToIso(kindIO.getEnd()))`.
In `create()`, parse ISO 8601 strings from the body map before converting to `TimeseriesReferenceIO`
(custom pre-processing step, or a dedicated v2-create request pojo). Leave `TimeseriesReferenceIO`
field types unchanged.

**AC:** `GET /v2/data-objects/{appId}/references?kind=timeseries` returns
`"start": "2023-11-15T04:53:20Z"` not `1700000000000000000`. `POST .../references?kind=timeseries`
accepts ISO 8601 strings for `start`/`end`. v1 wire unaffected.

---

## Finding 4 — APISIMP-SPATIAL-TIMEWINDOW-NANOS (size: S)

**Files:**
- `plugins/spatiotemporal/src/main/java/de/dlr/shepard/v2/spatial/handlers/SpatialDataReferenceKindHandler.java:90–91, 145–146`
- `plugins/spatiotemporal/src/main/java/de/dlr/shepard/v2/spatial/promote/SpatialPromoteService.java:159–160`

**What:** `SpatialDataReferenceKindHandler.toIO()` puts `io.put("startTime", ref.getStartTime())`
and `io.put("endTime", ref.getEndTime())`, where `SpatialDataReference.startTime`/`endTime` are
`Long` nanosecond-epoch values (confirmed nanoseconds via `SpatialDataPointRest` parameter
descriptions "Start timestamp in nanoseconds, inclusive"). The `create()` path at lines 145–146
reads nanosecond `Long` values from the request body map. `SpatialPromoteService.toIO()` at
lines 159–160 duplicates the same raw-Long exposure on the promote response.

**Fix:** In `SpatialDataReferenceKindHandler.toIO()` and `SpatialPromoteService.toIO()`, convert:
`io.put("startTime", nanosToIso(ref.getStartTime()))`. In `create()`, reverse-parse ISO strings
to nanoseconds before calling `toCreate.setStartTime(...)`.

**AC:** `GET /v2/data-objects/{appId}/references?kind=spatial` returns
`"startTime": "2024-03-15T10:30:00Z"` not `1710498600000000000`. `POST .../references?kind=spatial`
accepts ISO 8601 strings. `mvn verify -pl plugins/spatiotemporal` green.
