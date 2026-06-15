---
stage: deployed
last-stage-change: 2026-06-15
---

# APISIMP Sweep — fire-45 (2026-06-15)

**Result: CLEAN — no new actionable findings.**

## Findings reviewed

| Finding | Verdict |
|---|---|
| `Long containerId` in `TimeseriesReferenceRest` | Spurious — internal variable, not a `@PathParam`. Endpoints already use `String containerAppId`. |
| Spatial plugin `Long collectionId` in `SpatialDataReferenceRest.java` | Frozen upstream v1 compat surface (`@Path(Constants.SHEPARD_API + ...)`). Already tracked as `APISIMP-NUMERIC-ID-BATCH-2` (deferred). Not a new violation. |
| Pagination inconsistency (`?size=` vs `?pageSize=`) | `pageSize` is the established standard; remaining `?size=` holdouts tracked under PR #1887 (RED/CodeQL). Pre-existing. |
| `WebApplicationException` in `FileMigrationRest` | Handled by global `ShepardExceptionMapper`. No new row needed. |
| `Long id` in IO classes | Not found in codebase — false positive from initial pattern search. |

## Pipeline PRs reviewed as part of fire-45 STEP 1

| PR | Branch | Status |
|---|---|---|
| #1915 | `V2CONV-A4-admin-config-generic` | READY (11/11 checks) |
| #1916 | `V2CONV-B-shapes-applicable` | READY (11/11 checks) |
| #1917 | `APISIMP-QUERY-PARAMS-1` | READY (11/11 checks) |
| #1919 | `APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS` | READY (11/11 checks) |
| #1920 | `AFP-THERMO-OVERLAY-3` | READY (11/11 checks) |
| #1921 | `UI-GAP-6` | READY (11/11 checks) |
| #1887 | `V2-SWEEP-002-4` | RED (CodeQL pre-existing alert) |
| #1922 | cherry-pick recovery branch | PENDING (CI running) |

## New slice dispatched

`PLACEHOLDER-form-preview` slice 1 — PR #1923 open.
