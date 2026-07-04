---
stage: concept
last-stage-change: 2026-07-04
---

# APISIMP Sweep — 2026-07-04 (fire-399)

**Scope:** Full scan of `backend/src/main/java/de/dlr/shepard/v2/` (~105 REST resource files, all IO shapes) against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout. Frontend v1-caller audit also run.

**Baseline:** Last sweep fire-387 (2026-07-03) filed APISIMP-REF-ANNOTATION-PAGINATION-PARAM. That row merged fire-388 (PR #2263). Remaining open rows: APISIMP-DQR-ORPHAN, APISIMP-LEDGER-ANCHOR-ORPHAN (both operator-decision rows). New REST files added since fire-387: `FileContainerStatsRest`, `StructuredDataContainerStatsRest` (UI21-SIZEBAR-DATA feature), `UserSearchV2Rest`.

**What merged into main this fire (fire-399):** PR #2283 (APISIMP-RECENT-COLLECTIONS-V2) — migrated `useFetchRecentCollections` to v2 API; PR #2284 (SEARCH-V2-5) — merged prior fire.

---

## Axes confirmed clean this sweep

| Axis | Verdict |
|---|---|
| Forbidden `@Path(Constants.SHEPARD_API + ...)` additions | ✅ zero found in v2/ |
| Per-kind endpoints NOT yet unified under `?kind=` | ⚠️ two new per-kind stats files — see F1 |
| Bespoke admin `*ConfigRest` not on generic registry | ✅ all admin config routes through `AdminConfigRest` + `ConfigDescriptor` SPI |
| Numeric Neo4j id leaks in `@PathParam`/`@QueryParam` | ⚠️ two new leaks in stats files — see F1 |
| APISIMP-REF-ANNOTATION-PAGINATION-PARAM (fire-387 F1) | ✅ CONFIRMED FIXED — `ReferenceAnnotationRest.java:157` now `@QueryParam("pageSize")` |
| Error envelope consistency | ⚠️ six local `problem()` helpers use `APPLICATION_JSON` not `application/problem+json` — see F3 |
| `operationId` coverage | ⚠️ two new files missing `operationId` — see F1 |
| Endpoints superseded by `POST /v2/shapes/render` | ✅ no new superseded endpoints found |
| Response fields with zero callers / numeric id leaks in IO | ✅ new IO classes (`FileContainerStatsIO`, `StructuredDataContainerStatsIO`) are plain POJOs |
| `UserSearchV2Rest` (new since fire-387) | ✅ clean — `?page=`/`?pageSize=` throughout, operationId present, appId-keyed |

---

## Findings (4 new rows filed)

### F1 — APISIMP-STATS-NUMERIC-ID (MINOR, S)

**What:** `FileContainerStatsRest.java` and `StructuredDataContainerStatsRest.java` (added by UI21-SIZEBAR-DATA) fail three axes:

1. **Axis 2 (per-kind path):** Standalone `@Path("/v2/file-containers")` and `@Path("/v2/structured-data-containers")` paths bypass `ContainersV2Rest` (the unified kind dispatcher). Container-kind-specific endpoints belong under `ContainersV2Rest` as kind-filtered methods or via the `ContainerHandler` SPI.
2. **Axis 4 (numeric id leak):** Both files declare `@PathParam("containerId") long containerId` — a Neo4j numeric id on the public wire surface.
3. **Axis 6 (missing operationId):** Both `@Operation` annotations carry only `summary` and `description`, no `operationId`.

| File | Path | Violations |
|---|---|---|
| `FileContainerStatsRest.java:32,40,56` | `GET /v2/file-containers/{containerId}/stats` | per-kind path, numeric id, no operationId |
| `StructuredDataContainerStatsRest.java:32,40,56` | `GET /v2/structured-data-containers/{containerId}/stats` | per-kind path, numeric id, no operationId |

**Fix:**
- Re-key from `long containerId` to `String containerAppId`; resolve the numeric id internally via `entityIdResolver.resolveLong(containerAppId)` before the service call (same pattern as all other v2 container endpoints)
- Add `operationId = "getFileContainerStats"` / `operationId = "getStructuredDataContainerStats"` to the `@Operation` annotation
- Either: (a) move both methods into `ContainersV2Rest` as kind-dispatched handler calls (preferred — unifies the surface), OR (b) add a `// V2-EXCEPTION:` comment justifying the standalone path (acceptable if merging is blocked by handler complexity)
- Update frontend caller (`frontend/composables/containers/useContainerStats.ts` or equivalent) to pass `containerAppId` string param

**AC:** `GET /v2/file-containers/{containerAppId}/stats` and `GET /v2/structured-data-containers/{containerAppId}/stats` accept UUID string; both methods have `operationId`; `mvn verify -pl backend` green.

---

### F2 — APISIMP-SHAPES-PREDICATES-FULL-PAGINATION (MINOR, XS)

**What:** `ShapesPredicatesRest.java:99` (`GET /v2/shapes/predicates`) uses `@QueryParam("limit")` for page-size and wraps the response in `PagedResponseIO` — but has no `?page=` parameter, making multi-page navigation impossible. The `PagedResponseIO` wrapper implies navigable pages; the absence of `?page=` means callers can only retrieve the first page regardless of the `total` field.

Every other paginated GET list endpoint across the v2 surface uses `?pageSize=` + `?page=`. APISIMP-SHAPES-PREDICATES-BARE-LIST (fire-361) introduced `?limit=` — the inconsistency was introduced intentionally as a quick cap but now stands out.

**Fix (option A — standard pagination):** Add `@QueryParam("page") @DefaultValue("0") @PositiveOrZero int page`; rename `@QueryParam("limit")` → `@QueryParam("pageSize")`; use proper `subList(page * pageSize, Math.min((page+1)*pageSize, total))` slicing. **Preferred** — consistent with all other paginated v2 GET lists.

**Fix (option B — drop the fake-paged wrapper):** Return `Response.ok(all.subList(0, Math.min(limit, total))).build()` with `SchemaType.ARRAY` + `X-Truncated` header, dropping the misleading `PagedResponseIO` wrapper. Consistent with the `?limit=` cap pattern used by `ImportDiagnosticsV2Rest` and `CollectionDQRRest`.

**AC:** Either (A) `GET /v2/shapes/predicates?page=1&pageSize=50` returns items 50–99; OR (B) endpoint returns plain array with `X-Truncated` header; `mvn verify -pl backend` green.

---

### F3 — APISIMP-SEMANTIC-CONTENT-TYPE (MINOR, XS)

**What:** Six files define local `problem()` helper methods that build RFC 7807 error responses using `.type(MediaType.APPLICATION_JSON)` instead of `"application/problem+json"`. This sends a technically incorrect `Content-Type` header on error responses — clients that check `Content-Type: application/problem+json` to distinguish error bodies from success bodies will misclassify these errors.

| File | Line | Helper pattern |
|---|---|---|
| `SemanticSparqlRest.java` | 480 | `.type(MediaType.APPLICATION_JSON)` |
| `SemanticTermSearchRest.java` | 340 | `.type(MediaType.APPLICATION_JSON)` |
| `VocabularyBrowseRest.java` | 222 | `.type(MediaType.APPLICATION_JSON)` |
| `SemanticPredicateStatsRest.java` | 140 | `.type(MediaType.APPLICATION_JSON)` |
| `ProvenanceRest.java` | 638 | `.type(MediaType.APPLICATION_JSON)` |
| `DataObjectV2Rest.java` | 1079 | `.type(MediaType.APPLICATION_JSON)` |

**Fix:** In each file, change `.type(MediaType.APPLICATION_JSON)` → `.type("application/problem+json")` in the local `problem()` helper. Consider consolidating into a shared `ProblemHelper` class to prevent future drift.

**AC:** All six files return `Content-Type: application/problem+json` on error paths; `mvn verify -pl backend` green.

---

### F4 — APISIMP-DELETE-REFS-V2 (MINOR, XS)

**What (frontend):** `useDeleteReferences.ts` has three delete functions:
- `deleteUriReferenceV2(appId: string)` — correctly calls `DELETE /v2/references/{appId}` ✅
- `deleteCollectionReference(collectionReferenceId: number)` — still v1 `useShepardApi(CollectionReferenceApi)` with numeric id ❌
- `deleteDataObjectReference(dataObjectReferenceId: number)` — still v1 `useShepardApi(DataObjectReferenceApi)` with numeric id ❌

`DELETE /v2/references/{appId}` is a unified endpoint that dispatches by kind — it works for URI, Collection, and DataObject references. The `information.referenceAppId` field is already populated on all three reference kinds in the `RelationshipTableElement` (confirmed in `relationshipTableElementMappingUtil.ts` via `readReferenceAppId()`).

**Fix:**
- Add `deleteReferenceV2(appId: string)` in `useDeleteReferences.ts` using `DELETE /v2/references/${encodeURIComponent(appId)}` (same pattern as `deleteUriReferenceV2`)
- In `DeleteRelationshipDialog.vue`: switch `case "Collection Reference"` and `case "Data Object Reference"` to call `deleteReferenceV2(props.tableElement.information.referenceAppId)` when `referenceAppId` is defined; keep numeric-id fallback only for v1-only entities that lack `referenceAppId`
- The `deleteUriReferenceV2` + `deleteUriReference` pair can be merged into `deleteReferenceV2` + `deleteUriReference` (keeping the legacy fallback)

**AC:** Collection reference and DataObject reference rows delete via `DELETE /v2/references/{appId}` when `referenceAppId` is available; `npm run typecheck` + `npm run test` green.

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-STATS-NUMERIC-ID | MINOR | S | queued — dispatch next fire |
| APISIMP-SHAPES-PREDICATES-FULL-PAGINATION | MINOR | XS | queued |
| APISIMP-SEMANTIC-CONTENT-TYPE | MINOR | XS | queued |
| APISIMP-DELETE-REFS-V2 | MINOR | XS | queued |
| APISIMP-DQR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |
| APISIMP-LEDGER-ANCHOR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |

**Recommended next dispatch:** APISIMP-SEMANTIC-CONTENT-TYPE (XS, 6 local one-line `problem()` helper fixes, no wire change, no frontend change) or APISIMP-DELETE-REFS-V2 (XS, pure frontend migration using the already-present `information.referenceAppId` field).
