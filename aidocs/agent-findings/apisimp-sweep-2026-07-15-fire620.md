---
stage: fragment
last-stage-change: 2026-07-15
---

# APISIMP Sweep — fire-620 (2026-07-15)

Scan of `backend/src/main/java/de/dlr/shepard/v2/**/*IO.java`,
`plugins/*/src/main/java/**/*IO.java`, and handler `toIO()` / `create()` / `patch()` call
sites for remaining violations of the APISIMP mandate:

- Nanosecond / millisecond `long`/`Long` timestamp fields on the v2 REST wire → must be ISO 8601 `String`
- Duration-as-millis `long`/`Long` fields on the v2 REST wire → must be ISO 8601 duration `String`
- Numeric internal IDs (Neo4j node IDs, serial PKs) on the v2 wire → must be removed or `@Schema(deprecated = true)`

Conducted by background agent (`a67e852af78d1eb79`) with findings integrated below.

## Scope exclusions

- `/shepard/api/` v1 wire is frozen — changes to v1 IO types are out of scope.
- `SpatialDataPointIO.timestamp` (nanosecond Long) is on the v1 `SpatialDataPointRest` surface (`@Path(Constants.SHEPARD_API + "/"...)`) — out of scope.
- `WikiWriteResponseIO.labJournalEntryId` carries `@JsonIgnore` — not on the REST wire; not a violation.
- APISIMP-TSCHANNEL-CONTAINER-ID-WIRE, APISIMP-PERM-AUDIT-NEO4J-ID, APISIMP-DQR-ORPHAN, APISIMP-LEDGER-ANCHOR-ORPHAN: blocked on L2e / operator decision — confirmed still blocked, not re-filed.

## Verified shipped before this sweep

All v2 IO `long`/`Long` fields inspected and confirmed clean:
byte/size fields (`fileSizeBytes`, `bagSizeBytes`, `byteSize`, `fileSize`, `maxFileSizeMb`, `maxRows`),
count fields (`count`, `ncrCount`, `rejectCount`, `totalDataObjects`, `total`, `filesTotal`, `unread`,
`subCollectionCount`, `doCount`, `shellCount`, `distinctAgents`, `httpRequestsTotal`, etc.),
Neo4j-deprecated `labJournalEntryId` (carries `@JsonIgnore`), `SpatialDataReferenceIO` fields
(v1 IO, v2 handler already converted by PR #2578).

---

## Finding 1 — APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS (size: XS) — **QUEUED**

- **File:** `backend/src/main/java/de/dlr/shepard/v2/references/handlers/TimeseriesReferenceKindHandler.java:94`
- **Problem:** `io.put("wallClockOffset", kindIO.getWallClockOffset())` emits a raw nanosecond-epoch `Long` for the TM1 wall-clock anchor (UTC nanoseconds of the DAQ's t=0). `start`/`end` on the same handler were converted in APISIMP-TSREF-TIMEWINDOW-NANOS (PR #2577); `wallClockOffset` was added after that sweep and was missed.
- **Fix:** `io.put("wallClockOffset", wco != null ? nanosToIso(wco) : null)` — helper already present at line 337. Add symmetric parse via `isoOrLongToNanos()` in `create()`. Update `@Schema`. Batch with Finding 2 (same handler, same PR).
- **Backlog row:** `APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS` (queued, next fire).

---

## Finding 2 — APISIMP-TSREF-LASTSCOREDAT-MS-TO-ISO (size: XS) — **QUEUED**

- **File:** `backend/src/main/java/de/dlr/shepard/v2/references/handlers/TimeseriesReferenceKindHandler.java:97`
- **Problem:** `io.put("lastScoredAt", kindIO.getLastScoredAt())` emits a raw epoch-millisecond `Long` for the AI quality-scoring timestamp (null = never scored). Response-only field — no write path. One-liner null-guarded fix.
- **Fix:** `io.put("lastScoredAt", lsa != null ? Instant.ofEpochMilli(lsa).toString() : null)`. Update `@Schema`. Batch with Finding 1.
- **Backlog row:** `APISIMP-TSREF-LASTSCOREDAT-MS-TO-ISO` (queued, next fire).

---

## Finding 3 — APISIMP-TSREF-CONTAINER-ID-DEPRECATE (size: XS) — **QUEUED**

- **File:** `backend/src/main/java/de/dlr/shepard/context/references/timeseriesreference/io/TimeseriesReferenceIO.java:35`
- **Problem:** `private long timeseriesContainerId` lacks `@Schema(deprecated = true)`. `timeseriesContainerAppId` was added in APISIMP-TSCONT-APPID-KEY-3 (PR #1845) but the numeric field was never deprecated. Distinct from APISIMP-TSCHANNEL-CONTAINER-ID-WIRE (blocked on TS-IDb/c).
- **Fix:** Add `@Schema(deprecated = true, ...)`. Update `openapi.json`. Annotation-only — no runtime change.
- **Backlog row:** `APISIMP-TSREF-CONTAINER-ID-DEPRECATE` (queued, next fire).

---

## Campaign status after fire-620

| Row | Status |
|---|---|
| `APISIMP-TSREF-WALLCLOCK-OFFSET-NANOS` | 🔲 queued (fire-621) |
| `APISIMP-TSREF-LASTSCOREDAT-MS-TO-ISO` | 🔲 queued (fire-621) |
| `APISIMP-TSREF-CONTAINER-ID-DEPRECATE` | 🔲 queued (fire-621+) |
| `APISIMP-PERM-AUDIT-NEO4J-ID` | blocked on L2e |
| `APISIMP-TSCHANNEL-CONTAINER-ID-WIRE` | blocked on TS-IDb/c |
| `APISIMP-DQR-ORPHAN` | blocked on operator decision |
| `APISIMP-LEDGER-ANCHOR-ORPHAN` | blocked on operator decision |

All other v2 and plugin IO classes with `Long/long` fields are confirmed clean (byte/size/count fields only).
