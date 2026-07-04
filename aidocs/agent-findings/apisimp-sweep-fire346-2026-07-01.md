---
stage: concept
last-stage-change: 2026-07-01
---

# APISIMP Sweep — 2026-07-01 (fire-346)

**Scope:** Full scan of `backend/src/main/java/de/dlr/shepard/v2/` (95 REST resource files) against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout.

**Baseline:** fire-342 filed 6 rows (F1–F6). All 6 are now merged or in-flight:
- F1 `APISIMP-MISSING-401-RESPONSES` → merged fire-345 (PR #2217)
- F2 `APISIMP-EXPORT-URL-EXCEPTION-SHAPE` → merged (APISIMP-EXCEPTION-SHAPE-1)
- F3 `APISIMP-DATAOBJECT-LIST-RUNTIME-EX` → merged (APISIMP-EXCEPTION-SHAPE-1)
- F4 `APISIMP-SEMANTIC-ADMIN-ILLEGAL-ARG` → merged fire-345 (PR #2216)
- F5 `APISIMP-TERM-SEARCH-BARE-LIST` → merged fire-345 (PR #2215)
- F6 `APISIMP-COLLECTION-CREATE-LONG-INPUT` → **merged this fire (PR #2218, sha: 9675c12a)**

This sweep was run because all named APISIMP rows in `aidocs/16` are dispatched; no queued row remains.

**Candidate findings from fire-345 sweep notes (both ruled out):**

| Candidate | Verdict |
|---|---|
| `ContainersV2Rest.java` `@QueryParam Long start/end` at line 728/730 | ✅ clean — confirmed nanosecond Unix timestamps ("Window start, nanoseconds since Unix epoch"), not Neo4j node ids |
| 4 files described as "409 throwers without 409 @APIResponse" | ✅ clean — after reading all 4 files none emit 409; `ContainerPublicationStateRest` description *mentions* 409 as a consequence of archiving but the endpoint itself never returns 409 |

---

## Axes confirmed clean (no new findings)

| Axis | Status |
|---|---|
| Numeric Neo4j id leaks (`Long`/`long` in `@PathParam`/`@QueryParam`/IO) | ✅ clean — `Long start/end` on `ContainersV2Rest` confirmed as nanosecond timestamps |
| Forbidden `@Path(Constants.SHEPARD_API + ...)` in v2 namespace | ✅ clean |
| Per-kind path fragmentation vs. `?kind=` unification | ✅ clean |
| Pagination param naming (`page` / `pageSize`) | ✅ clean |
| RFC 7807 `ProblemJson` envelope on 4xx/5xx | ✅ clean — all fire-342 F2/F3/F4 gaps fixed; `ShepardExceptionMapper` catches all remaining throw sites |
| `PagedResponseIO<T>` envelope on list endpoints | ✅ clean — all fire-342 F5 gap fixed |
| `@Tag` fragmentation | ✅ clean |
| `@Operation(operationId=...)` coverage | ✅ clean |
| Bespoke admin `*ConfigRest` outside registry | ✅ clean |
| `@APIResponse(responseCode = "401")` documentation | ⚠️ **3 new method-level gaps** — see F1 below |
| `@APIResponse(responseCode = "409")` documentation | ✅ clean — candidate ruled out after code read |

---

## MINOR (1)

### F1 — `@APIResponse(responseCode = "401")` missing from 3 authenticated method sites

**Slug:** `APISIMP-MISSING-401-RESPONSES-P2` | **Size:** XS

Three method sites across three v2 resources are authenticated but lack `@APIResponse(responseCode = "401")` documentation. Fire-342's `APISIMP-MISSING-401-RESPONSES` (merged in fire-345) fixed 10 files using a per-file scan that passed a file if ANY of its methods documented 401. These three gaps escaped because their sibling PATCH methods already have 401 doc, satisfying the per-file check.

| File | Method | Auth mechanism | Gap |
|---|---|---|---|
| `v2/admin/instance/resources/InstanceRegistryRest.java` | `PATCH /v2/admin/instances` | `@RolesAllowed("instance-admin")` | No `@APIResponse("401")` anywhere in file; only `@APIResponse("403")` on PATCH |
| `v2/collection/resources/CollectionPublicationStateRest.java` | `GET /v2/collections/{id}/publication-state` | `@Authenticated` (class level) | PATCH has 401 doc; GET has only 200 + 404 |
| `v2/container/resources/ContainerPublicationStateRest.java` | `GET /v2/containers/{id}/publication-state` | `@Authenticated` (class level) | PATCH has 401 doc; GET has only 200 + 404 |

**Fix:** Add `@APIResponse(responseCode = "401", description = "Request is not authenticated.")` to:
1. `InstanceRegistryRest.patchRegistry()` — annotate above the existing `@APIResponse("403")`.
2. `CollectionPublicationStateRest.get()` — add after the `@APIResponse("404")`.
3. `ContainerPublicationStateRest.get()` — add after the `@APIResponse("404")`.

Zero wire shape change — annotation-only.

**AC:** All three method sites document 401 in the generated OpenAPI spec; `mvn verify -pl backend` green.

---

## Not filed (ruled out)

- **`Long start/end` on `ContainersV2Rest.getChannelData()`** — `@Parameter(description = "Window start, nanoseconds since Unix epoch")` makes explicit these are time range params, not Neo4j node ids.
- **`@APIResponse("409")` gaps** — The four "candidate" files (`CollectionPublicationStateRest`, `CollectionWatchesRest`, `CollectionWatchersRest`, `ContainerPublicationStateRest`) do not emit HTTP 409 responses themselves. Mentions of "returns 409 until the state is flipped back" in operation descriptions refer to OTHER endpoints that write to archived containers.

---

## Summary

| Slug | Severity | Size | Status |
|---|---|---|---|
| APISIMP-MISSING-401-RESPONSES-P2 | MINOR | XS | ⏳ queued |

**1 new XS row filed.** Smallest dispatchable slice next fire: `APISIMP-MISSING-401-RESPONSES-P2` (annotation-only, 3 files, ~3 lines total).
