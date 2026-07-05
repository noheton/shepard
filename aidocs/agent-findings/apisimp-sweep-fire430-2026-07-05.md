---
stage: deployed
last-stage-change: 2026-07-05
---

# APISIMP Sweep — fire-430 (2026-07-05)

Post-merge sweep after fire-429 closed `APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING`
(PR #2314) and corrected two stale backlog entries. No named dispatchable
APISIMP rows were queued going into this fire; per pipeline STEP 2e a fresh
sweep was run across all `/v2/` REST resources and plugin code.

Scope: `backend/src/main/java/de/dlr/shepard/v2/` (all REST resources + services),
`plugins/*/src/main/java/` (plugin REST + services), and the
`backend/src/main/java/de/dlr/shepard/context/` service layer. Searched for:

- Residual `subList`-based in-memory pagination behind a `?page=`/`?pageSize=` param
- Unbounded `findAll()` / `findAll*()` calls powering a paginated response
- Numeric-id leaks on v2 endpoints
- Inconsistent error shapes
- Remaining frontend v1 call sites (`useShepardApi`) where a v2 path now exists
- Bespoke per-kind `@Path` entries that may represent residual V2CONV sprawl

## Result: no new dispatchable rows found

All inspected sites fall into resolved categories:

| Site | Status |
|---|---|
| `FileContainerStatsRest.java` — `@Path("/v2/file-containers")` | **Already tracked**: `UI21-SIZEBAR-DATA` ✅ done. Self-documented as V2-EXCEPTION in the class javadoc; single `GET /v2/file-containers/{appId}/stats` endpoint; intentional per-kind stats path; kind-dispatcher consolidation deferred per existing backlog row. |
| `StructuredDataContainerStatsRest.java` — `@Path("/v2/structured-data-containers")` | **Already tracked**: `UI21-SIZEBAR-DATA` ✅ done. Same V2-EXCEPTION pattern; single `GET /v2/structured-data-containers/{appId}/stats` endpoint. |
| `FileBundleReferenceRest.java` — `@Path("/v2/bundles")` | **Already tracked**: bundle CRUD + group management surface (FR1a, `aidocs/53 §1.6`); paginated file listing under `MFFD-IMAGEBUNDLE-PAGINATE-1` ✅ shipped. Provides domain-specific group management operations (`FileGroup` sub-entities) not representable through the generic `/v2/references/{appId}` surface. Not redundant. |
| `ContainersV2Rest.java:727-729` — `@QueryParam("start")` / `@QueryParam("end")` Long | **Not an ID leak**: nanosecond epoch timestamps for timeseries range queries. Fine. |
| `ContainersV2Rest.java:1457` — `@QueryParam("size") Integer` | **Not a page size**: thumbnail pixel size (64/200/400). Fine. |
| `ImportDiagnosticsV2Rest.java:134` — `subList(0, limit)` with `@Max(10000)` | **Already assessed**: intentional bounded in-memory diagnostic log slice; not a paginated DB endpoint. Acceptable at this scale. |
| All v2 pagination params | Consistent `page`+`pageSize` (`@PositiveOrZero` + `@Min(1) @Max(200)`) across all paginated resources. `ProvenanceRest` correctly uses cursor-based `?limit`. No inconsistency found. |
| All v2 error shapes | RFC 7807 `ProblemJson` / `application/problem+json` uniformly applied across v2 resources (closed by `APISIMP-ERROR-ENVELOPE-UNIFY` ✅ and batch follow-ups). No plain-string 4xx bodies found. |
| Numeric-id leaks in v2 | Nanosecond timestamps and pixel sizes are the only `Long`/`Integer` query params; none are Neo4j internal IDs. All path params use `appId` (UUID v7 string). No leaks. |
| Frontend `useShepardApi` call sites | Remaining v1 call sites are all in the named exception set (lab journal CRUD — no v2 CRUD counterpart; `getCollectionRoles` — no v2 counterpart). Intentional; each has a one-line justification comment at the call site. No undocumented v1 fallback found. |
| `spatiotemporal` plugin — `Constants.SHEPARD_API` path | Frozen upstream byte-compat exception; allowed per CLAUDE.md §"plugin backends" rule. Not a finding. |

## No backlog corrections this fire

No stale status cells were found. The fire-429 corrections (`APISIMP-REFERENCES-LIST-IN-MEMORY-PAGING`
and `APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING`) already landed on main as commit `f7de593`.

## Summary

| Finding | Status |
|---|---|
| All inspected bespoke per-kind v2 paths | Not filed — already tracked (`UI21-SIZEBAR-DATA`, `MFFD-IMAGEBUNDLE-PAGINATE-1`) or intentional domain-specific surface |
| Pagination params | Consistent across all paginated resources |
| Error shapes | Uniformly RFC 7807 across v2 |
| Numeric-id leaks | None — nanosecond timestamps and pixel sizes are not Neo4j IDs |
| Frontend v1 call sites | All in named exception set with documented justification |

**All named APISIMP rows are ✅ done, ⛔ blocked/deferred, or ⏳ decision rows.**
No new dispatchable slice was found. The in-memory paging sweep series remains clean.
