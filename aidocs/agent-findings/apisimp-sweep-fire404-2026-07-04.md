---
stage: concept
last-stage-change: 2026-07-04
---

# APISIMP Sweep — 2026-07-04 (fire-404)

**Scope:** Full scan of the live v2 REST surface, IO shapes, and frontend composables against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout.

**Baseline:** All four findings from the previous sweep (fire-399, `apisimp-sweep-2026-07-04.md`) have shipped this fire:
- APISIMP-STATS-NUMERIC-ID → ✅ PR #2288
- APISIMP-SHAPES-PREDICATES-FULL-PAGINATION → ✅ PR #2287
- APISIMP-SEMANTIC-CONTENT-TYPE → ✅ PR #2285
- APISIMP-DELETE-REFS-V2 → ✅ PR #2286

No new REST files added to v2/ since fire-399.

**What merged into main this fire (fire-404):** PRs #2285, #2286, #2287, #2288 (all four fire-399 sweep rows).

---

## Axes confirmed clean this sweep

| Axis | Verdict |
|---|---|
| Forbidden `@Path(Constants.SHEPARD_API + ...)` additions | ✅ zero found in v2/ |
| Per-kind endpoints NOT yet unified under `?kind=` | ✅ none new — all file/structured-data stats now re-keyed via #2288 |
| Bespoke admin `*ConfigRest` not on generic registry | ✅ all admin config routes through `AdminConfigRest` + `ConfigDescriptor` SPI |
| Numeric Neo4j id leaks in `@PathParam`/`@QueryParam` | ✅ zero in REST params; `DataObjectSummaryIO.id` is a response-body field (see F2) |
| Error envelope consistency | ✅ all six `problem()` helpers now emit `application/problem+json` (PR #2285) |
| `operationId` coverage | ✅ all REST methods confirmed to have operationId after fire-399 fixes |
| Pagination param consistency | ✅ `GET /v2/shapes/predicates` now uses `?pageSize=` (PR #2287); no remaining `?limit=` outliers |
| Frontend v1 composable callers | ⚠️ `useCreateReferences.ts` still calls v1 for URI creates; see F1 |
| v2 IO response-body numeric id fields | ⚠️ `DataObjectSummaryIO.id` (Long) still serialised on wire; see F2 |

---

## Findings (2 new rows filed)

### F1 — APISIMP-CREATE-REFS-V2 (MINOR, S)

**What:** `useCreateReferences.ts` contains three create functions. The v2 migration is partial:

| Function | Status |
|---|---|
| `addCollectionReference` | ⚠️ v1 `useShepardApi(CollectionReferenceApi)` — present as **fallback** in `AddRelationshipDialog.vue:148–153` when `dataObjectAppId` is absent |
| `addDataObjectReference` | ⚠️ v1 `useShepardApi(DataObjectReferenceApi)` — present as **fallback** in `AddRelationshipDialog.vue:166–172` when `dataObjectAppId` is absent |
| `addUriReference` | ❌ v1 `useShepardApi(UriReferenceApi)` — **no v2 path exists** for URI create; `AddRelationshipDialog.vue:180` always uses this v1 call |

`useCreateReferencesV2.ts` (added by APISIMP-REF-CREATE-NUMERIC-IDS, fire-78) already provides `addCollectionReference` + `addDataObjectReference` via `POST /v2/references?kind=collection|dataobject` and is the primary path in `AddRelationshipDialog.vue` when `dataObjectAppId` is present.

**Gap:** `addUriReference` has no v2 equivalent in `useCreateReferencesV2.ts`. `UriReferenceKindHandler` (kind=`uri`) exists in the backend and accepts `name`, `uri`, `relationship` in the POST body. The composable needs a `addUriReferenceV2(uri, name, relationship)` function and `AddRelationshipDialog.vue` needs to prefer it when `dataObjectAppId` is present.

The v1 fallback paths for collection/dataobject are a separate concern: they remain necessary until L2 migration confirms all DataObjects have an `appId`. They are not a bug but do keep `useCreateReferences.ts` alive.

**Files:**
- `frontend/composables/references/useCreateReferences.ts:27–45` — v1 URI create (primary gap)
- `frontend/composables/references/useCreateReferences.ts:59–77` — v1 collection create (fallback, lower priority)
- `frontend/composables/references/useCreateReferences.ts:91–109` — v1 dataobject create (fallback, lower priority)
- `frontend/composables/references/useCreateReferencesV2.ts` — add `addUriReferenceV2` here
- `frontend/components/context/display-components/relationships/add-dialog/AddRelationshipDialog.vue:180` — switch URI case to v2 path when `dataObjectAppId` present

**Fix:**
1. Add `addUriReferenceV2(uri, name, relationship)` to `useCreateReferencesV2.ts` using `POST /v2/references?kind=uri&dataObjectAppId=...` (same fetch pattern as existing `addCollectionReference`/`addDataObjectReference`)
2. In `AddRelationshipDialog.vue` URI case: prefer `addUriReferenceV2` when `props.dataObjectAppId` is present; fall back to `addUriReference` for legacy DataObjects
3. AC: URI references create via v2 when `dataObjectAppId` is present; `npm run typecheck` + `npm run test` green

---

### F2 — APISIMP-SUMMARY-IO-NUMERIC-ID (MINOR, M)

**What:** `DataObjectSummaryIO.java:19` declares `private Long id` — the Neo4j numeric OGM node id — which is serialised in every response that embeds a `DataObjectSummary`. The Javadoc reads: _"Neo4j OGM node id — kept for backward-compat delete flows until appId-keyed delete ships."_ That condition is now met: APISIMP-DELETE-REFS-V2 (PR #2286, fire-402) shipped the appId-keyed delete. The justification is stale.

The field appears in all `GET /v2/collections/{cid}/data-objects/{did}/predecessors|successors` responses via `PredecessorSuccessorIO`, and anywhere else `DataObjectSummaryIO` is used. On the v2 surface, numeric Neo4j ids in response bodies are a violation of the "no internal id leaks" contract.

**Frontend impact:** The `id` field is currently read by ~10 frontend components for display badges (e.g., predecessor/successor tables show the numeric id alongside the appId as a legacy "node id" label). Removing it requires auditing all frontend consumers and switching them to `appId`-only display. This is what makes the work M-sized — not the backend change (removing one field + one `@Schema` annotation), but the full frontend consumer audit.

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/DataObjectSummaryIO.java:17–21` — remove `Long id` field and its `@Schema` annotation
- Frontend consumers TBD via audit (`grep -r '\.id\b' frontend/` filtered to DataObjectSummaryIO context)

**Fix:**
1. Remove `private Long id` and its `@Schema(readOnly = true)` annotation from `DataObjectSummaryIO`
2. Audit all frontend components that reference `.id` on a `DataObjectSummary`-shaped object; switch to `.appId`
3. Update `DataObjectSummaryIO` `@Schema(description=...)` to remove the id mention
4. AC: serialised `DataObjectSummaryIO` has no `id` key; no frontend component reads `.id` from a summary; `mvn verify -pl backend` + `npm run typecheck` green

---

## Summary

| Row | Severity | Size | Status |
|---|---|---|---|
| APISIMP-CREATE-REFS-V2 | MINOR | S | queued — dispatch next fire (URI create v2 is the specific gap) |
| APISIMP-SUMMARY-IO-NUMERIC-ID | MINOR | M | queued (M — frontend consumer audit needed) |
| APISIMP-DQR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |
| APISIMP-LEDGER-ANCHOR-ORPHAN | MINOR | S | queued (decision row — operator call needed) |

**Recommended next dispatch:** APISIMP-CREATE-REFS-V2 (S — add `addUriReferenceV2` to the existing `useCreateReferencesV2.ts`, update one switch case in `AddRelationshipDialog.vue`; typecheck + test green; no backend change needed).
