---
stage: deployed
last-stage-change: 2026-06-27
audience: [contributor]
---

# APISIMP Sweep — 2026-06-27 (fire-270)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` + plugin `@Path`s.

## Context

Fire-268 sweep found one finding (APISIMP-PERMISSION-AUDIT-LOG-PAGINATION,
now merged fire-270, sha eb026a54). This sweep verifies that finding is
resolved and searches for any new issues.

## What I found

### Numeric ID hygiene — CLEAN

Zero v2 `@PathParam` or `@QueryParam` carry a `Long` or `int` type.
All path params are UUID v7 strings. Known tracked exceptions unchanged:
- `PermissionAuditEntryIO.neo4jNodeId` — intentional triage handle,
  tracked as APISIMP-PERMISSION-AUDIT-NEO4J-ID (blocked on L2 clean).
- `ContainersV2Rest` / `ProvenanceRest` epoch-nanosecond/millisecond
  params confirmed NOT Neo4j IDs.

`SearchV2Rest.java:131–139` uses `Long colId` internally (Neo4j id from
`ResultTriple.getCollectionId()`) only for a DAO lookup — it is never
exposed in any `@PathParam`, `@QueryParam`, or response field; the
response carries only `appId` strings. Not a leak.

### Forbidden `SHEPARD_API` constant — CLEAN

Zero v2 resource classes use `Constants.SHEPARD_API` in a `@Path`
annotation. Known residual unchanged:
`SpatialDataReferenceRest.java:40` (frozen upstream-compat, deferred
APISIMP-V1-PATH-RESIDUAL-1).

### Bespoke admin `*ConfigRest` outside generic registry — CLEAN

All domain-specific admin configs on generic `AdminConfigRest`.
`JupyterConfigPublicRest` carve-out remains intentional.

### Per-kind endpoints not unified — CLEAN

No new per-kind paths bypassing `?kind=`.

### APISIMP-PERMISSION-AUDIT-LOG-PAGINATION — RESOLVED ✓

`InstanceAdminRest.java:208` now uses `@PositiveOrZero` on `page`,
`@Min(1) @Max(500)` on `pageSize`, and returns `PagedResponseIO` with
`X-Total-Count` header. Merged fire-270 (eb026a54).

### Pagination consistency — TWO NEW FINDINGS

All list endpoints now use consistent declarative bean-validation
annotations *except* `SearchV2Rest`.

**Finding 1 — `SearchV2Rest.GET /v2/search` — `pageSize` param
inconsistent validation (XS)**

`SearchV2Rest.java:102`:

```java
@QueryParam("pageSize") @DefaultValue("50") @PositiveOrZero int pageSize
```

The `@PositiveOrZero` constraint allows `0` as a valid value. The
Javadoc and the `@Schema(minimum = "1", maximum = "200")` on the
`@Parameter` annotation document `1–200` as the valid range, but the
bean-validation constraint enforces only `>= 0`. A caller supplying
`pageSize=0` will not get a 400 — the code clamps via
`Math.min(Math.max(pageSize, 1), 200)` at runtime, but this is a
validator/documentation mismatch and breaks the contract that
`pageSize=0` → 400 that every other paginated endpoint enforces.

Fix: replace `@PositiveOrZero` with `@Min(1) @Max(200)` (consistent
with all other paginated endpoints). Add one reflection-based regression
test confirming the annotation is present.

**Finding 2 — `SearchV2Rest.GET /v2/search` — missing `X-Total-Count`
response header (XS)**

Every other paginated v2 endpoint sets the `X-Total-Count` response
header alongside the `PagedResponseIO` (or equivalent) body. The search
endpoint returns `SearchV2ResultIO` (which includes `total`, `page`,
`pageSize`, `query`) but never sets the `X-Total-Count` header. A
generic client that expects `X-Total-Count` to be present on all paged
`/v2/` endpoints must special-case the search endpoint.

Note: `SearchV2ResultIO` is a custom wrapper (not `PagedResponseIO`)
to carry the `query` field — this is intentional. The missing header is
the inconsistency.

Fix: add `.header("X-Total-Count", total)` to the response builder in
`SearchV2Rest.search()`. Add one test asserting the header is present.

Both findings can be resolved in a single XS PR
(`APISIMP-SEARCH-PAGESIZE-HEADER`, 2 lines + 2 test assertions).

## Opportunities

1. **APISIMP-SEARCH-PAGESIZE-HEADER** (XS): Fix `SearchV2Rest.pageSize`
   validation (`@PositiveOrZero` → `@Min(1) @Max(200)`) and add the
   `X-Total-Count` response header. AC: `?pageSize=0` returns 400;
   `X-Total-Count` header present on 200; `mvn verify -pl backend` green.

## Real-world impact

LOW. `SearchV2Rest` is a read endpoint; no security or data-integrity
risk. A generic pagination client would have to special-case the search
endpoint for the missing header. The `pageSize=0` gap silently clamps
rather than rejecting — confusing but not broken.

## Gaps & blockers

None for Finding 1 and 2. They are both in `SearchV2Rest.java` and
fully self-contained.

## What surprised me

After 270 fires of APISIMP work, `SearchV2Rest` is the only remaining
endpoint with a pagination inconsistency. The surface is otherwise clean.
The two issues are minor (one annotation swap, one `.header()` call) but
complete the pagination contract across the entire v2 surface.
