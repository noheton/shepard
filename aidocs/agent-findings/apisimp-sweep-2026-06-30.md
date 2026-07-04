---
stage: deployed
last-stage-change: 2026-06-30
---

# API Simplification Sweep — fire-313 (2026-06-30)

## Context

APISIMP-PROV-ISO8601-TIMESTAMPS (the only queued APISIMP row from the fire-311
sweep) was shipped in fire-313 STEP 1 (PR #2191, `336af309`). All other prior
APISIMP rows are done, deferred, or blocked (see
`aidocs/16-dispatcher-backlog.md` APISIMP section). This sweep ran a fresh
full-surface scan before deciding the next slice.

Prior sweep: `aidocs/agent-findings/apisimp-sweep-2026-06-29-fire311.md`
(found 1 row: APISIMP-PROV-ISO8601-TIMESTAMPS, now shipped).

## Sweep scope

- `backend/src/main/java/de/dlr/shepard/v2/` — all v2 REST resource classes
  (96 scanned)
- `plugins/*/src/main/java/` — all plugin v2 resources
- Focus: HTTP method/semantic mismatches, pagination validation inconsistencies,
  per-kind endpoints outside `?kind=`, numeric id leaks, bespoke `*ConfigRest`
  not on generic registry, tombstone endpoints, forbidden
  `@Path(Constants.SHEPARD_API...)` in v2 namespace, response body bloat

## Findings

### Finding 1 — `SemanticAnnotationV2Rest.update()`: `@PUT` with merge-patch semantics

**File**: `backend/src/main/java/de/dlr/shepard/v2/annotations/resources/SemanticAnnotationV2Rest.java:459-538`

`update()` is annotated `@PUT` but its `@Operation` description explicitly says
"RFC 7396 merge-patch: only non-null fields in the request body are applied", and
the implementation at line 528 has the comment `// Apply merge-patch` followed by
null-guarded field assignments. `PUT` means full-replacement; `PATCH` is the
correct HTTP method for merge-patch (RFC 5789 + RFC 7396). API consumers and HTTP
caching proxies that respect HTTP semantics will be confused by a `PUT` endpoint
that performs partial updates.

**New backlog row**: `APISIMP-ANNOTATION-MERGE-PATCH-HTTP` (S, queued).

Fix: change `@PUT` to `@PATCH`; update `@APIResponse` to note `PATCH`; optionally
add `@Consumes("application/merge-patch+json")` alongside `application/json` to
signal the RFC 7396 media type.

---

### Finding 2 — `CollectionV2Rest` + `DataObjectV2Rest`: `@PositiveOrZero` + runtime clamping vs. `@Min(1) @Max(200)`

**Files**:
- `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionV2Rest.java:165,167-168`
- `backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java:197`

`CollectionV2Rest.listCollections()` uses `@PositiveOrZero` on `pageSize` (allowing
0) and then clamps at runtime: `int safeSize = Math.min(Math.max(pageSize, 1), 200)`.
`DataObjectV2Rest.listDataObjects()` uses `@PositiveOrZero` without the clamping
(the documentation says "clamped to [1, 200]" but passes the raw value downstream).

Every other v2 list endpoint uses `@Min(1) @Max(200)` with no runtime clamping:
`ContainersV2Rest`, `UserGroupV2Rest`, `SearchV2Rest`, `NotificationRest`,
`ProjectsRest`, `CollectionWatchersRest`, `ShepardTemplateRest`, and all newly-added
list endpoints from the APISIMP wave.

The `ContainersV2Rest.listChannels()` at line 640 also uses `@PositiveOrZero` with
`@DefaultValue("200")` — this is the timeseries channel list endpoint where a
caller legitimately wants "all channels at once" (default 200); this case is
acceptable as-is given its different semantics.

**New backlog row**: `APISIMP-PAGESZ-VALIDATION-INCONSISTENCY` (XS, queued).

Fix: standardize `CollectionV2Rest` + `DataObjectV2Rest` to `@Min(1) @Max(200)`;
remove runtime clamping from `CollectionV2Rest` (lines 167-168).

---

### Finding 3 — All other v2 surface

Full scan confirms:

- **No forbidden `@Path(Constants.SHEPARD_API...)`** in v2 namespace — confirmed
  zero occurrences across all 96 v2 resource classes.
- **No numeric Neo4j IDs in paths/params** — all `@PathParam`/`@QueryParam` use
  String `appId` (except legitimate time bounds `Long start`/`end` in timeseries
  channel-data queries — these are timestamps, not entity IDs).
- **No numeric IDs in response bodies** — all 166 IO classes checked; zero
  `private Long id` or `private Integer id` fields on v2 response shapes.
- **No per-kind endpoints outside `?kind=`** — `ReferencesV2Rest` fully unified;
  `FileReferenceV2Rest` retired with 410 Gone.
- **No bespoke `*ConfigRest` outside `AdminConfigRest`** — operational actions
  (`HdfAdminRest`, `AasAdminRest`) are correctly action endpoints, not config
  registry entries.
- **PagedResponseIO universal** — all list endpoints now return the standard
  envelope; the two `@PositiveOrZero` inconsistencies (Finding 2) are the last
  pagination-surface divergences.
- **RFC 7807 error shapes** — consistent across all v2 4xx/5xx responses.
- **HTTP methods** — `PUT` is correct for full-replace (`CollectionSceneGraphRest`,
  `UserAvatarRest`, binary uploads); `PATCH` is correct for merge-patch; `@PUT`
  on `SemanticAnnotationV2Rest.update()` is the only mismatch (Finding 1).

## Summary

| Finding | Severity | Action |
|---|---|---|
| 1 — SemanticAnnotationV2Rest `@PUT` with merge-patch semantics | S | **New row** `APISIMP-ANNOTATION-MERGE-PATCH-HTTP` (S, queued) |
| 2 — `@PositiveOrZero` + runtime clamping in two list endpoints | XS | **New row** `APISIMP-PAGESZ-VALIDATION-INCONSISTENCY` (XS, queued) |
| 3 — All other v2 surface | n/a | Clean: appId-only params, PagedResponseIO universal, RFC 7807 4xx, no v1 additions |

**Two new backlog rows filed.** Smallest dispatchable slice next fire:
`APISIMP-PAGESZ-VALIDATION-INCONSISTENCY` (XS — two file edits, remove 2 lines of
clamping, change 2 annotations, update 2 test stubs).
