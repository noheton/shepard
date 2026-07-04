---
stage: deployed
last-stage-change: 2026-06-13
---

# APISIMP Sweep — 2026-06-13 (fire #N+15)

Triggered: all named APISIMP-* rows shipped (APISIMP-AAS-PAGINATION-SIZE merged as
#1884 this fire). Scanned every file under `backend/src/main/java/de/dlr/shepard/v2/`
and `plugins/*/src/main/java/` on `origin/main` @ `121d29bb`.

## Excluded from scope

- `plugins/spatiotemporal/` — frozen v1 wire shape (upstream-byte-compat surface per
  CLAUDE.md §API-version policy; `SpatialDataPointRest` + `SpatialDataReferenceRest`
  use `Constants.SHEPARD_API` intentionally).
- `ThumbnailRest.java:69` — `@QueryParam("size")` is image pixel-size, not pagination.

## PR #1870 status

Branch `APISIMP-SNAPSHOT-RESP-SIZE` (PR #1870) has ~82 add/add conflicts against
current main — the branch predates a massive main rewrite and cannot be auto-rebased.
Filed as `APISIMP-PAGINATION-UNIFY-RECREATE` for fresh implementation on current main.

---

## Finding 1 — Pagination param `?size` still present on 7 v2 list endpoints (S)

**Slug:** APISIMP-PAGINATION-UNIFY-RECREATE

PR #1870 was supposed to rename `@QueryParam("size")` → `@QueryParam("pageSize")` on
these endpoints, but the branch has unresolvable conflicts. All 7 targets + UnhideFeedRest
still have the old param on current main:

| File | Line | Current | Fix |
|---|---|---|---|
| `v2/collection/resources/CollectionV2Rest.java` | 156 | `@QueryParam("size")` | `@QueryParam("pageSize")` |
| `v2/admin/resources/InstanceAdminRest.java` | 224 | `@QueryParam("size")` | `@QueryParam("pageSize")` |
| `v2/snapshot/resources/SnapshotListRest.java` | 132 | `@QueryParam("size")` | `@QueryParam("pageSize")` |
| `v2/snapshot/resources/CollectionSnapshotRest.java` | 184 | `@QueryParam("size")` | `@QueryParam("pageSize")` |
| `v2/bundle/resources/FileBundleReferenceRest.java` | 431 | `@QueryParam("size")` | `@QueryParam("pageSize")` |
| `v2/dataobject/resources/DataObjectV2Rest.java` | 192 | `@QueryParam("size")` | `@QueryParam("pageSize")` |
| `v2/timeseriescontainer/resources/TimeseriesContainerChannelsRest.java` | 105 | `@QueryParam("size")` | `@QueryParam("pageSize")` |
| `plugins/unhide/.../UnhideFeedRest.java` | 130–131 | `@QueryParam("page-size")` | `@QueryParam("pageSize")` |

Also update backend-client generated TypeScript and any FE composables that pass `size:`.

**AC:** No `@QueryParam("size")` for pagination on any v2 or plugin list endpoint;
`@QueryParam("page-size")` gone; `mvn verify -pl backend` + FE typecheck green.

---

## Finding 2 — Empty-body 4xx in template cluster (S)

**Slug:** APISIMP-EMPTY-BODIES-BATCH-6

| File | Lines | Bodies |
|---|---|---|
| `v2/template/resources/ShepardTemplateRest.java` | 91,115,117,198,270,294 | 6× UNAUTHORIZED/NOT_FOUND |
| `v2/template/resources/CollectionTemplatesRest.java` | 201,203,204 | 3× UNAUTHORIZED/NOT_FOUND/FORBIDDEN |
| `v2/template/resources/TemplateExcelExportRest.java` | ~132,188 | 2× UNAUTHORIZED/FORBIDDEN |
| `v2/template/resources/TemplatePortabilityRest.java` | ~117,169 | 2× UNAUTHORIZED |
| `v2/template/resources/TemplateFormRest.java` | ~110 | 1× UNAUTHORIZED |
| `v2/template/resources/TemplateInstantiationRest.java` | ~158 | 1× UNAUTHORIZED |

`ShepardTemplateRest` already has a `problem()` helper at line 328 — reuse it for all
empty-body paths. Add helpers to the other files following the `FileReferenceV2Rest` pattern.

**AC:** No `Response.status(UNAUTHORIZED/FORBIDDEN/NOT_FOUND).build()` with empty body
in these 6 files; `mvn verify` green.

---

## Finding 3 — Empty-body 4xx in publish/export cluster (S)

**Slug:** APISIMP-EMPTY-BODIES-BATCH-7

| File | Lines | Bodies |
|---|---|---|
| `v2/publish/resources/PublishRest.java` | 106,127,134,197,218,221 | 6× UNAUTHORIZED/NOT_FOUND/FORBIDDEN |
| `v2/export/rep/RepExportV2Rest.java` | 92,98,102,133,139,146 | 6× UNAUTHORIZED/NOT_FOUND/FORBIDDEN |

`PublishRest` already has a `problem()` helper at line 274. `RepExportV2Rest` needs one added.

**AC:** All 12 empty-body 4xx paths carry `ProblemJson`; `mvn verify` green.

---

## Finding 4 — Empty-body NOT_FOUND in bundle cluster (M)

**Slug:** APISIMP-EMPTY-BODIES-BATCH-8

`FileBundleReferenceRest.java` has 14+ empty-body `NOT_FOUND.build()` calls and 1
empty-body `UNAUTHORIZED.build()`. No `problem()` helper exists in this file.

Lines: 145, 188, 236, 246, 281, 286, 288, 331, 336, 345, 381, 386, 394, 435, 440,
442, 511, 516, 547, 551 (20 sites).

**AC:** All empty-body 4xx in `FileBundleReferenceRest.java` carry `ProblemJson`;
`mvn verify` green.

---

## Finding 5 — Empty-body 4xx in CollectionV2Rest (S)

**Slug:** APISIMP-COLLECTION-V2-EMPTY-BODIES

| Line | Status |
|---|---|
| 208 | `NOT_FOUND.build()` — ogmId null |
| 319 | `NOT_FOUND.build()` — ogmId null |
| 370 | `NOT_FOUND.build()` — ogmId null |
| 391 | `UNAUTHORIZED.build()` — caller null |
| 393 | `FORBIDDEN.build()` — insufficient permission |

`CollectionV2Rest` has no `problem()` helper yet. Add one, wire it to all 5 sites.

**AC:** All 5 empty-body 4xx paths in `CollectionV2Rest.java` carry `ProblemJson`;
`mvn verify` green.

---

## Dispatch order (next fire)

Smallest/first: **APISIMP-COLLECTION-V2-EMPTY-BODIES** (S, 1 file, 5 sites) or
**APISIMP-PAGINATION-UNIFY-RECREATE** (S, 8 files, 8 param renames + FE update).
