---
stage: fragment
last-stage-change: 2026-07-14
---

# APISIMP Sweep — fire-608 (2026-07-14)

Routine sweep of all v2 IO classes (`de.dlr.shepard.v2.**/*IO.java` and
`plugins/**/*IO.java`) for remaining nanosecond/epoch-ms timestamp fields and
numeric-ID leaks after the PR #2569 wave (APISIMP-REST-CHANNEL-DATA-NANOS-TO-ISO
+ APISIMP-CROSS-DO-BULK-START-END-NANOS).

---

## What I found

Grep pattern used:
```
(?:Long|long)\s+(?:start|end|timestamp|windowStart|windowEnd|createdAt|updatedAt|time|nanos|millis)
```
across `backend/src/main/java/de/dlr/shepard/v2/**/*IO.java` and
`plugins/**/*IO.java`.

Also scanned for `private Long neo4jNodeId` (numeric Neo4j ID leaks) and
`long containerId` / `int id` in v2 IO wire shapes.

---

## Findings

### Finding 1 — `BulkChannelDataRequestIO.start`/`.end` — nanosecond Longs in bulk-channel request body

| Field | File | Line |
|-------|------|------|
| `Long start` | `backend/.../v2/timeseriescontainer/io/BulkChannelDataRequestIO.java` | 40 |
| `Long end` | same | 45 |

OpenAPI `description`: "Window start, nanoseconds since epoch." Endpoint:
`POST /v2/containers/{appId}/channels/data/bulk`.

Sister of the single-channel `GET` endpoint covered by PR #2569.  Having the
bulk endpoint accept nanosecond longs while the single-channel sibling accepts
ISO 8601 strings post-#2569 creates a split convention for callers using both.

**New backlog row filed:** `APISIMP-BULK-CHANNEL-REQ-NANOS-TO-ISO` (XS–S)

---

### Finding 2 — `LiveWindowResponseIO.windowStart`/`.windowEnd` and `LiveWindowPointIO.timestamp` — epoch-ms Longs in live-window response

| Field | File | Line |
|-------|------|------|
| `long windowStart` | `backend/.../v2/timeseriescontainer/io/LiveWindowResponseIO.java` | 15 |
| `long windowEnd` | same | 18 |
| `long timestamp` | `backend/.../v2/timeseriescontainer/io/LiveWindowPointIO.java` | 13 |

Javadoc: "epoch milliseconds (UTC)". These are **milliseconds** not nanoseconds,
but they are still raw numeric epoch timestamps on an otherwise ISO-8601-converging
surface. `Instant.ofEpochMilli(ms).toString()` is the conversion. Response-only
(no parse needed server-side).

**New backlog row filed:** `APISIMP-LIVE-WINDOW-MS-TO-ISO` (XS)

---

### Finding 3 — `PermissionAuditEntryIO.neo4jNodeId` — Neo4j internal ID on wire

| Field | File | Line |
|-------|------|------|
| `Long neo4jNodeId` | `backend/.../v2/admin/io/PermissionAuditEntryIO.java` | 28 |

Already `@Schema(deprecated = true)` with Javadoc noting removal post-L2.
Intentional triage handle for pre-migration rows that lack an `appId`. Not
removable until L2e migration completes and all rows carry non-null `appId`.

**New backlog row filed:** `APISIMP-PERM-AUDIT-NEO4J-ID` (XS, blocked on L2e)

---

### Finding 4 — `AdminMetricsSummaryIO.uptimeMillis` — duration as raw milliseconds

| Field | File | Line |
|-------|------|------|
| `long uptimeMillis` | `backend/.../v2/admin/io/AdminMetricsSummaryIO.java` | 33 |

JVM uptime expressed in milliseconds. Low priority — duration not timestamp —
but `Duration.ofMillis(v).toString()` produces the self-describing ISO 8601
`"PT3600.123S"` form. Eliminates unit ambiguity.

**New backlog row filed:** `APISIMP-METRICS-UPTIME-MILLIS-TO-ISO` (XS, low priority)

---

## Not filed (out of scope)

| Item | Reason |
|------|--------|
| `SpatialDataPointIO.timestamp` (ns) | v1 surface (`@Path(Constants.SHEPARD_API + ...)`) — frozen; tracked under SPATIAL-V6-003/PLUGIN-V2-001 for a v2 sibling shelf |
| `SpatialDataReferenceIO.startTime`/`endTime` | same v1 surface, same reason |
| `TimeseriesChannelV2IO.containerId` (long) | already `APISIMP-TSCHANNEL-CONTAINER-ID` (existing backlog row, blocked on TS-IDb/c migration) |
| `TimeseriesChannelV2IO.id` (int) | already `APISIMP-TSCHANNEL-INT-ID-DEPRECATE` (existing backlog row) |
| `CrossDoBulkDataRequestIO.start`/`end` | covered by PR #2569 (in-flight) |
| `DataObjectListItemV2IO.timeseriesCount` | count, not a timestamp |
| `AdminMetricsSummaryIO.jvmHeapUsedBytes` etc. | byte/count metrics — not timestamps |
| `DataciteMinterConfigIO.updatedAtMillis` | local variable only, not a wire field |
| `EpicMinterConfigIO.updatedAtMillis` | local variable only, not a wire field |

---

## In-flight status update

Two rows previously marked `🔲 queued` were already addressed by PR #2569
(branch `APISIMP-REST-CHANNEL-NANOS-TO-ISO-crossdo`, opened fire-607).
Both have been updated to `🔄 in PR #2569` in the backlog:

- `APISIMP-REST-CHANNEL-DATA-NANOS-TO-ISO`
- `APISIMP-CROSS-DO-BULK-START-END-NANOS`

---

## Summary

| Row ID | Size | Status |
|--------|------|--------|
| APISIMP-BULK-CHANNEL-REQ-NANOS-TO-ISO | XS–S | 🔲 queued |
| APISIMP-LIVE-WINDOW-MS-TO-ISO | XS | 🔲 queued |
| APISIMP-PERM-AUDIT-NEO4J-ID | XS | 🔲 queued (blocked L2e) |
| APISIMP-METRICS-UPTIME-MILLIS-TO-ISO | XS | 🔲 queued (low priority) |

**Recommended next pick:** `APISIMP-BULK-CHANNEL-REQ-NANOS-TO-ISO` — natural
companion to PR #2569 and the simplest possible follow-on (same pattern, same
service layer, same frontend timestamp-format utility).
