---
stage: deployed
last-stage-change: 2026-07-15
---

# APISIMP Sweep — fire-612 (2026-07-15)

Sweep of v2 IO classes for remaining epoch-ms `Long`/`long` or `java.util.Date`
fields that should be ISO 8601 strings on the REST wire. Conducted by background
agent `ae755f568256b2d60` with results integrated into fire-612 implementation.

## Findings filed this fire

### Finding 1 — `WatchIO.since: Long` (epoch-ms) — **SHIPPED this fire**

- **File:** `backend/src/main/java/de/dlr/shepard/v2/watches/io/WatchIO.java:39`
- **Problem:** `Long since` was documented as "Epoch-milliseconds when this watch was created" (example `1751400000000`). Violates ISO-8601 mandate.
- **Fix:** Changed to `String since`; emits via `Instant.ofEpochMilli(w.getSince()).toString()`. Updated `@Schema`, prose in `CollectionWatchesRest:82`, and test fixture in `CollectionWatchesRestTest:43`.
- **Backlog row:** `APISIMP-WATCH-SINCE-EPOCH-MS-TO-ISO` (shipped this fire).

### Finding 2 — `CollectionWatcherIO.since: Long` (epoch-ms) — **SHIPPED this fire**

- **File:** `backend/src/main/java/de/dlr/shepard/v2/collectionwatchers/io/CollectionWatcherIO.java:23`
- **Problem:** `Long since` was documented as "Epoch-milliseconds when this watch subscription was created" (example `1751400000000`). Violates ISO-8601 mandate.
- **Fix:** Changed to `String since`; emits via `Instant.ofEpochMilli(w.getSince()).toString()`. Updated `@Schema`, prose in `CollectionWatchersRest:67,113`, test fixtures in `WatchMcpToolsTest:104` and `CollectionWatchersRestTest:223`, and `WatchDto.since?: number → since?: string` in `useWatchedContainers.ts`.
- **Backlog row:** `APISIMP-WATCH-SINCE-EPOCH-MS-TO-ISO` (shipped this fire, same PR).

### Finding 3 — `VideoStreamReferenceIO.wallClockTimestamp: Long` (nanoseconds) — **QUEUED**

- **File:** `plugins/video/src/main/java/de/dlr/shepard/context/references/videostreamreference/io/VideoStreamReferenceIO.java:85`
- **Problem:** `private Long wallClockTimestamp` holds a UTC nanosecond wall-clock instant from ffprobe's `creation_time` tag. `VideoProbeService.parseCreationTime()` converts the ISO-8601 creation_time to nanoseconds-since-epoch before storing; the IO re-exposes that raw nanosecond count on the wire. This is a metadata instant, not raw sensor data — the ISO-8601 mandate applies.
- **Fix:** Change to `String wallClockTimestamp`; convert via `Instant.ofEpochSecond(ns / 1_000_000_000L, ns % 1_000_000_000L).toString()`. Also update handler in `VideoStreamReferenceKindHandlerLogic:74` that writes the raw long into the generic map. Update `@Schema`.
- **Backlog row:** `APISIMP-VIDEO-WALL-CLOCK-TO-ISO` (queued for next fire).

## Items confirmed clean (no violations)

All other v2 and plugin IO classes with `Long/long` fields were inspected:

- **Byte/size fields:** `fileSizeBytes`, `bagSizeBytes`, `byteSize`, `maxFileSizeMb`, `maxRows` — not timestamps.
- **Count fields:** `count`, `ncrCount`, `rejectCount`, `totalDataObjects`, `filesTotal`, `deleted*`, `unread`, `subCollectionCount`, `doCount`, `shellCount` — cardinalities.
- **Duration fields:** `ProvenanceStatsIO.bucketMillis` — bucket width in ms (not a point-in-time instant).
- **`PermissionAuditEntryIO.neo4jNodeId`** — already blocked on L2e; tracked in `APISIMP-PERM-AUDIT-NEO4J-ID`.
- **All plugin IO timestamp fields** (`LegacyV1ConfigIO`, `UnhideConfigIO`, `EpicMinterConfigIO`, `DataciteMinterConfigIO`, `AasRegistrationIO`) — already convert via `Instant.ofEpochMilli(...).toString()`.
- **SpatialData fields** — blocked on SPATIAL-V6-003 / PLUGIN-V2-001; excluded per campaign rules.

No `java.util.Date` fields found in any v2 or plugin IO class on the wire format.

## Campaign status after fire-612

The APISIMP epoch-ms-to-ISO campaign on in-scope v2 IO classes is **complete** pending:
- `APISIMP-VIDEO-WALL-CLOCK-TO-ISO` — video plugin (queued, next fire)
- `APISIMP-LIVE-WINDOW-TS-POINTS-TO-ISO` — timeseries live-window (queued)
- `APISIMP-PERM-AUDIT-NEO4J-ID` — blocked on L2e

All other known epoch-ms `Long` fields in v2 IO classes have been converted.
