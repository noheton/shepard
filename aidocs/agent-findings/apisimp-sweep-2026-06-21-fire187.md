---
stage: fragment
last-stage-change: 2026-06-21
---

# APISIMP sweep — 2026-06-21 fire-187

Targeted re-audit of the v2 REST surface. Objective: confirm fire-153
conclusion (all @Parameter gaps are covered by in-flight PRs) and identify
any new structural findings.

## Scope

- All 88 `*Rest.java` files under `backend/src/main/java/de/dlr/shepard/v2/`
  and `plugins/*/src/main/java/`
- Structural checks: `@Path(Constants.SHEPARD_API + ...)` violations, bespoke
  admin `*ConfigRest` not on generic registry, per-kind endpoints not unified
  under `?kind=`, numeric ids in response bodies
- Pagination consistency: `@QueryParam("size")` vs `@QueryParam("pageSize")`
- New files added since fire-153: `SqlTimeseriesRest`, `CrossDoBulkDataRest`,
  `AnomalyDetectionRest`, `CollectionEventsRest`, `CollectionTimelineRest`,
  `MappingsMaterializeRest`

---

## Result: no new rows — fire-153 conclusion confirmed

### Structural checks (clean)

| Check | Findings |
|-------|----------|
| `@Path(Constants.SHEPARD_API + ...)` | Only `SpatialDataPointRest.java:51` — frozen upstream-compat; tracked APISIMP-V1-PATH-RESIDUAL-1 (deferred) |
| Bespoke `*ConfigRest` not on generic registry | None — all migrated to V2CONV-A4 generic `AdminConfigRest` |
| Per-kind endpoints not unified under `?kind=` | None new — `ContainersV2Rest` and `ReferencesV2Rest` already unified |
| Numeric Neo4j/Postgres IDs leaking in response bodies | `PermissionAuditEntryIO.neo4jNodeId` (admin-only triage field, intentional) tracked APISIMP-PERMISSION-AUDIT-NEO4J-ID (queued); `TimeseriesChannelV2IO.id`+`containerId` tracked (APISIMP-TSCHANNEL-CONTAINER-ID done/deferred) |

### New files — clean

- **`SqlTimeseriesRest.java`** — `POST /v2/sql/timeseries` body-only, no
  `@QueryParam`; well-documented `@Operation`. Clean.
- **`CrossDoBulkDataRest.java`** — `POST /v2/data-objects/cross-timeseries-bulk`
  body-only; `@Operation` complete with `x-agent-hint` extension. Clean.
- **`AnomalyDetectionRest.java`** — `@PathParam("appId")` only; `@Operation`
  complete. Clean.
- **`CollectionEventsRest.java`** — `@PathParam("collectionAppId")` only; clean.
- **`CollectionTimelineRest.java`** — `binSizeDays` has multi-line `@Parameter`
  (scanner false-positive in prior sweeps). Clean.
- **`MappingsMaterializeRest.java`** — `@PathParam("templateAppId")` only. Clean.

### Pagination name consistency

Only `@QueryParam("size")` in the codebase is
`ContainersV2Rest.java:1342` — the thumbnail pixel size, not pagination.
Intentionally `"size"` (not `"pageSize"`). All paginated list endpoints use
`"page"` + `"pageSize"` consistently.

### Previously-found issues (confirmed still covered)

| Finding | Coverage |
|---------|----------|
| `SemanticAnnotationV2Rest.java:145-150,195-198` (10 bare `@QueryParam`) | PR #2003 (✅ READY) + PR #2004 (✅ READY) |
| `ImportV2Rest.java:207-208` + `ImportDiagnosticsV2Rest.java:116-117` | PR #2001 (✅ READY) + PR #2002 (not yet checked; same batch) |
| `CollectionLabJournalEntriesRest.java:103-104` | PR #2018 (❌ NPE-red; heals after #2035 merges) |
| `CollectionV2Rest.java:160-161` | PR #2016 (❌ NPE-red; heals after #2035 merges) |

---

## NPE-red unblocking dependency

PR #2035 (F9-ANNOTATION-REFERENCE-PERMISSION) is the key dependency.
It is **11/11 GREEN** as of 14:08 UTC today (fire-187 confirmed).
The 10 in-flight PRs that currently show "Unit tests + coverage gate: FAILURE"
will all become green once #2035 is merged to main.

---

## Next dispatch

APISIMP @Parameter queue confirmed exhausted. With the NPE-blocker resolved:
- Next STEP 2 priority: **V2-SWEEP** residuals or **MFFD demonstrator** work.
- Recommended: Check if any V2-SWEEP-* rows have open PRs that need rebasing
  after the NPE-fix wave merges, then dispatch MFFD-RENDER-NDT-GRID (M).
