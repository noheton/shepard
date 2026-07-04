---
stage: deployed
last-stage-change: 2026-07-01
---

# APISIMP Sweep — fire-353 (2026-07-01)

**Scope:** Full scan of `backend/src/main/java/de/dlr/shepard/v2/` (95 REST resource files) and all plugin REST resources against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout.

**Baseline state entering this fire:**
- APISIMP-MISSING-OPERATIONID-P3 merged (fire-352, PR #2224) — row updated below to `✅ merged`.
- APISIMP-DQR-ORPHAN: still queued (decision row — operator call needed).
- APISIMP-LEDGER-ANCHOR-ORPHAN: still queued (decision row — operator call needed).

**What SEMANTIC-ANNOTATE-BULK-UI-1 merged before this sweep (fire-353, PR #2225):**
`POST /v2/annotations/bulk` already existed (SEMANTIC-ANNOTATE-BULK-REST-1). This PR added the frontend: `BulkAnnotationDialog.vue`, multi-select checkboxes in `CollectionDataObjectsPanel`, 13 Vitest unit tests. No new REST endpoint; no wire-shape change.

---

## Axes checked

| Axis | Result |
|---|---|
| A — Numeric id leaks in `@PathParam`/`@QueryParam`/IO fields | ✅ Clean — `BulkAnnotationItemResultIO` + `BulkAnnotationResultIO` use `appId` strings only; `ContainersV2Rest Long start/end` confirmed epoch-ns timestamps (not node ids); only tracked leak = `PermissionAuditEntryIO.neo4jNodeId` (APISIMP-PERMISSION-AUDIT-NEO4J-ID, blocked on L2) |
| B — Forbidden `@Path(Constants.SHEPARD_API + ...)` additions | ✅ Clean — zero found in `v2/` or any non-exempt plugin |
| C — operationId completeness | ⚠️ **2 gaps found** — `CollectionTemplatesRest.listAllowed` + `listUsed` have `@Operation(summary=...)` but no `operationId` (filed as APISIMP-MISSING-OPERATIONID-P4) |
| D — Response verbosity (BulkAnnotation + sample IO classes) | ✅ Clean — `BulkAnnotationItemResultIO` (4 fields, all load-bearing), `BulkAnnotationResultIO` (4 fields), `AnnotationIO` legacy fields (`propertyName`/`propertyIri`/`valueName`/`valueIri`) are intentional compat carriers actively read in 6+ frontend components; not dead weight |
| E — Pagination param consistency (`page` + `pageSize`) | ✅ Clean — all list endpoints use `page` / `pageSize` consistently; `ContainersV2Rest ?size` is a thumbnail pixel-size param (unrelated to pagination) |
| F — Error envelope consistency (ProblemJson / application/problem+json) | ✅ Clean — `TemplatePortabilityRest.serverError()` correctly wraps `ProblemJson`; no raw-string or non-RFC-7807 error shapes found |
| G — Superseded render/transform endpoints | ✅ Clean — `ShapesRenderRest` uses body-level `ShapesRenderRequestIO` (appId-based dispatch, no URL/path query params); `ThermographyV2Rest` previously dissolved; no new bespoke transform endpoints |
| H — v1 frozen surface modifications | ✅ Clean — no commits in recent history touch `de.dlr.shepard.data/`, `de.dlr.shepard.auth/`, or `de.dlr.shepard.context/` REST surfaces |
| Plugin REST resources | ✅ Clean — all non-spatiotemporal plugin REST resources use `/v2/` paths, `appId` string identifiers, and have operationIds |

---

## Findings

### F1 — APISIMP-MISSING-OPERATIONID-P4 (MINOR, XS)

**What:** Two methods in `CollectionTemplatesRest.java` carry `@Operation(summary=...)` but no `operationId`, breaking generated client method naming. These were missed by prior waves (APISIMP-MISSING-OPERATIONID fire-341, APISIMP-MISSING-OPERATIONID-P3 fire-352) because the scanner only checked files that had at least one method with a full `@Operation` block containing the `operationId` field.

| File | Line | Endpoint | Gap |
|---|---|---|---|
| `CollectionTemplatesRest.java` | 87 | `GET /v2/collections/{appId}/templates/allowed` | `@Operation(summary=...)` present, no `operationId` — method `listAllowed` |
| `CollectionTemplatesRest.java` | 112 | `GET /v2/collections/{appId}/templates/used` | `@Operation(summary=...)` present, no `operationId` — method `listUsed` |

Sibling methods in the same file (`setAllowed` at line 140, `instantiate` at line 164) already carry `operationId` — so only the two GET endpoints are gaps.

**Fix:** Add `operationId = "listAllowed"` and `operationId = "listUsed"` as the first field in the respective `@Operation(...)` annotations. Annotation-only; zero wire change.

**AC:** Both methods carry `operationId` in the generated OpenAPI spec; `mvn verify -pl backend` green.

---

### F2 — APISIMP-BULK-ANNOT-CLIENT-REGEN (MINOR, S)

**What:** `BulkAnnotationDialog.vue` (shipped in fire-353, PR #2225) uses a raw `fetch()` + local `v2BaseUrl()` + `authHeaders()` helper pattern instead of calling `useV2ShepardApi()` with the generated `@dlr-shepard/backend-client`. The reason is that `POST /v2/annotations/bulk` (`operationId = bulkCreateAnnotations`) is **not yet present** in the generated `backend-client/src/apis/SemanticAnnotationsApi.ts` — that file was regenerated before the bulk endpoint was added.

This is the same class of debt as `FE-BUILD-03-REGEN` (the pending full client regen), `KIP1e` (raw fetch for publish), and the VideoStreamReference raw fetch. The workaround is correct and produces the right URL, but it bypasses the generated client contract, making future OpenAPI drift silently invisible.

**Affected file:** `frontend/components/semantic/BulkAnnotationDialog.vue:156` — `fetch(\`\${v2BaseUrl()}/v2/annotations/bulk\`, ...)`.

**Fix:** After the next `@dlr-shepard/backend-client` regeneration (FE-BUILD-03-REGEN or a targeted regen), replace the raw `fetch` with `useV2ShepardApi(SemanticAnnotationsApi).bulkCreateAnnotations(...)`. The wire shape is already stable; this is a composable-adoption fix only.

**AC:** `BulkAnnotationDialog.vue` uses `useV2ShepardApi(SemanticAnnotationsApi)` with no local `v2BaseUrl`/`authHeaders` helpers; `npm run typecheck` green; Vitest tests updated to mock the API client.

---

## Status updates to existing rows

- **APISIMP-MISSING-OPERATIONID-P3** — was `🔄 in-flight (fire-352, PR #2224)`; now `✅ merged (fire-352, PR #2224, sha: f8ef122)`.

---

## Confirmed-clean axes

All eight APISIMP axes from fire-351 remain clean:

1. Numeric id leaks (excluding the tracked L2-blocked `PermissionAuditEntryIO.neo4jNodeId`)
2. No forbidden `/shepard/api/` additions in v2
3. operationId completeness — 2 new gaps found in `CollectionTemplatesRest` (→ F1)
4. Response verbosity — BulkAnnotation IOs clean; AnnotationIO legacy fields intentional
5. Pagination param consistency — all list endpoints use `page`/`pageSize`
6. Error envelope consistency — all 4xx/5xx use ProblemJson
7. No bespoke render/transform endpoints added
8. v1 frozen surface untouched

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-MISSING-OPERATIONID-P4 | MINOR | XS | ⏳ queued — dispatch next fire |
| APISIMP-BULK-ANNOT-CLIENT-REGEN | MINOR | S | ⏳ queued — folds into FE-BUILD-03-REGEN or targeted regen |

**Recommended next dispatch:** APISIMP-MISSING-OPERATIONID-P4 (XS, annotation-only, no wire change, pure operationId fix — same shape as P3).
