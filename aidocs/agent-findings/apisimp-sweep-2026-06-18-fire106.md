---
stage: fragment
last-stage-change: 2026-06-18
---

# APISIMP surface sweep — fire-106 (2026-06-18)

**Scope:** targeted scan of `/v2` REST surface in `backend/src/main/java/de/dlr/shepard/v2/`
and plugin REST files. Post-fire-105 state: APISIMP-TSCHANNEL-CONTAINER-ID slice 1 just healed
(PR #1984); #1982 + #1983 READY for orchestrator merge.

**Checklist:**
- [x] New `@Path(Constants.SHEPARD_API + ...)` additions — none found
- [x] Per-kind reference endpoints not yet unified under `?kind=` — all migrated; tombstones in place
- [x] Bespoke admin `*ConfigRest` not on generic registry — none; all on generic registry
- [x] Numeric Neo4j id leaks in `@PathParam`/`@QueryParam`/response bodies — 1 new (APISIMP-TYPED-PRED-ID)
- [x] Inconsistent pagination param names — 1 minor (APISIMP-PAGINATION-PARAM-NAMING)
- [x] Empty-body 4xx responses — none; all v2 use ProblemJson
- [x] Endpoints superseded by `POST /v2/shapes/render` — none new
- [x] Plugin @Path sprawl — clean

## What I checked

### Deprecated numeric ID fields already handled
| Field | Status |
|---|---|
| `TimeseriesChannelV2IO.containerId` | ✓ tracked (APISIMP-TSCHANNEL-CONTAINER-ID, PR #1984) |
| `PermissionAuditEntryIO.neo4jNodeId` | ✓ tracked (APISIMP-PERMISSION-AUDIT-NEO4J-ID, blocked) |
| `WikiWriteResponseIO.labJournalEntryId` | ✓ shipped (fire-33, PR #1911) |

### Admin config surface
All features on `GET|PATCH /v2/admin/config/{feature}`. Clean.

### Error envelopes
All v2 resources return `ProblemJson` for 4xx/5xx. Clean.

### SHEPARD_API usage in v2
No `Constants.SHEPARD_API` references in v2 directory. Clean.

## New findings filed this sweep

### APISIMP-TYPED-PRED-ID (CRITICAL, XS)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/TypedPredecessorSummaryIO.java:27-35`
- **Symptom:** `long predecessorId` is marked `@Deprecated` and `@Schema(deprecated=true)` but
  has no `@JsonIgnore` — it is still serialized to the wire in every `DataObjectDetailV2IO`
  response (returned by `GET /v2/collections/{cid}/data-objects/{did}`). The description says
  "Deprecated — join on predecessorAppId (UUID v7) instead." Yet the field continues to appear
  in responses, allowing callers to build a dependency on the numeric Neo4j ID despite the
  deprecation warning.
- **Impact:** Violates the v2 "no numeric internal IDs on wire" contract. Any JSON client that
  reads `predecessorId` can silently depend on a Neo4j node id that will change under any
  graph migration. A deprecated field with no `@JsonIgnore` is an invitation for accidental
  dependency.
- **AC:** `predecessorId` is NOT serialized in `TypedPredecessorSummaryIO` JSON output (response
  has `predecessorAppId` + `predecessorName` + `predecessorStatus` + `relationshipType` only);
  `mvn verify -pl backend` green; a regression test asserts the field is absent from serialized
  output.
- **Proposed fix:** Add `@JsonIgnore` (from `com.fasterxml.jackson.annotation`) to the
  `predecessorId` record component. Since `@JsonIgnore` targets `FIELD` + `METHOD`, it propagates
  to the backing field and accessor method — Jackson 2.15+ will suppress it during serialization.

### APISIMP-PAGINATION-PARAM-NAMING (MINOR, XS)
- **File:** `backend/src/main/java/de/dlr/shepard/v2/users/resources/UserGroupV2Rest.java:88`
- **Symptom:** `@QueryParam("pageSize") @PositiveOrZero Integer size` — method parameter named
  `size` does not match the query param name `pageSize`. All other v2 list endpoints use the
  same name for method param and query param (e.g. `Integer page` for `@QueryParam("page")`).
  Also, line 83 hard-codes `@Parameter(name = "pageSize")` as a string literal instead of
  using `Constants.QP_PAGESIZE` (which does not yet exist).
- **Impact:** Minor readability issue; no functional bug. A developer reading the method signature
  cannot immediately see that `size` corresponds to the `pageSize` query param.
- **AC:** Method parameter renamed from `size` to `pageSize`; optionally extract
  `Constants.QP_PAGESIZE = "pageSize"` constant; `mvn verify -pl backend` green.

## Summary
- **Clean sections:** 6 of 8
- **Already-covered findings confirmed:** 3
- **New findings:** 2
  - 1 CRITICAL (APISIMP-TYPED-PRED-ID — deprecated numeric ID still on wire)
  - 1 MINOR (APISIMP-PAGINATION-PARAM-NAMING — method param naming inconsistency)
