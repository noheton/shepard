---
stage: deployed
last-stage-change: 2026-07-05
---

# APISIMP Sweep — fire-429 (2026-07-05)

Post-merge sweep after PR #2314 closed `APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING`
(Unhide feed bounded Cypher + COUNT companion). No further named dispatchable
APISIMP rows were queued; per pipeline STEP 2e a fresh sweep was run across all
`/v2/` REST resources and plugin code.

Scope: `backend/src/main/java/de/dlr/shepard/v2/` (all REST resources + services),
`plugins/*/src/main/java/` (plugin REST + services), and the
`backend/src/main/java/de/dlr/shepard/context/` service layer. Searched for:

- Residual `subList`-based in-memory pagination behind a `?page=`/`?pageSize=` param
- Unbounded `findAll()` / `findAll*()` calls powering a paginated response
- Numeric-id leaks on v2 endpoints
- Inconsistent error shapes
- Remaining frontend v1 call sites (`useShepardApi`) where a v2 path now exists

## Result: no new dispatchable rows found

All inspected sites fall into one of four resolved categories:

| Site | Status |
|---|---|
| `ReferencesV2Rest.java:500` — `APISIMP-REFERENCES-LIST-IN-MEMORY-PAGING` | **Stale entry corrected**: was listed as "in-flight fire-424 (PR #2310)"; confirmed merged fire-425. Backlog entry updated to ✅. |
| `UnhideFeedService.java:122–153` — `APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING` | **Stale entry corrected**: was listed as "queued (fire-428 sweep F1)"; merged this fire (fire-429, PR #2314). Backlog entry updated to ✅. |
| `ReferenceAnnotationRest.java:164` — `subList` in `listAnnotations()` | Already resolved by `APISIMP-REF-ANNOTATION-LIST-NO-PAGINATION` (fire-362). The slice is properly bounded by JSR-380 `@Max`. |
| `SnapshotPinnedReadRest.java:151–154` — `subList` on `listDataObjectAppIds` | Already fixed by `APISIMP-SNAPSHOT-PINNED-DO-UNCAPPED` (fire-372, PR #2247). The `@DefaultValue("500") @Max(2000)` bound is in place; the in-memory load of string UUIDs is acceptable at this scale. |
| `OntologyAlignmentRest.java:98–103` — `findAll()` + `.limit(MAX_ALIGNMENT_ROWS)` | Already fixed by `APISIMP-ALIGNMENT-UNBOUNDED` (fire-411, PR #2295). The `MAX_ALIGNMENT_ROWS = 500` cap is the intended guard; the alignment registry is read-only and bounded by design. |
| `SnapshotDiffRest.java:207,211,215` — in-memory intersection | Diff payload is bounded by two bounded manifests (fixed fire-422 PR #2308). In-memory intersection of two bounded sets is acceptable at current snapshot sizes. |
| `CollectionDQRRest.java:102,208` | Flagged as `APISIMP-DQR-ORPHAN` (decision row — zero frontend callers; operator must decide ship vs. decommission). No new paging issue. |
| `TimeseriesContainerKindHandler.java:526,564` — in-memory list slice | Intentional SPI default fallback in `ContainerKindHandler` default methods. DB-aware handlers override both `countByDataObject` and bounded `listByDataObject(skip,limit)`. Documented at `ContainerKindHandler.java:174`. |
| `ReferenceKindHandler.java:161` — in-memory list slice | Same SPI default fallback pattern. DB-aware handlers override; documented at `ReferenceKindHandler.java:156`. |
| `HdfAdminRest.java:186` — `hdfContainerDAO.findAll()` | Admin ACL rebuild endpoint; intentionally iterates all HDF containers to rebuild permissions. Not a paginated endpoint. |
| `NotificationTransportService.java:34` — `dao.findAll()` | REST layer already addressed by `APISIMP-NOTIFICATIONS-FAKE-PAGINATION` (fire-415, PR #2298). The service-layer `findAll()` feeds a small sorted admin list; not a paged endpoint. |
| `frontend/composables/references/useCreateReferences.ts` — `useShepardApi` call sites | Intentional v1 fallbacks per `APISIMP-CREATE-REFS-V2` (fire-404, PR #2289) design: v2 path is in `useCreateReferencesV2.ts`; callers use v2 when `dataObjectAppId` is present. |
| `frontend/composables/references/useDeleteReferences.ts` — `useShepardApi` call sites | Intentional v1 fallbacks per `APISIMP-DELETE-REFS-V2` (fire-402, PR #2286) design: v2 `deleteReferenceV2` is present; v1 path is the fallback for references without `appId`. |

## Backlog corrections applied this fire

Two stale status cells updated in `aidocs/16`:

- **`APISIMP-REFERENCES-LIST-IN-MEMORY-PAGING`**: `⏳ in-flight fire-424 (PR #2310)` → `✅ merged fire-425 (PR #2310)`
- **`APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING`**: `⏳ queued (fire-428 sweep F1)` → `✅ merged fire-429 (PR #2314, sha: 7b8ee82)`

## Summary

| Finding | Status |
|---|---|
| APISIMP-REFERENCES-LIST-IN-MEMORY-PAGING | Backlog entry corrected to ✅ merged fire-425 |
| APISIMP-UNHIDE-FEED-IN-MEMORY-PAGING | Backlog entry corrected to ✅ merged fire-429 |
| All other inspected v2 sites | Not filed — either already fixed, intentional SPI default, admin-only, or operator-decision-needed |

**All named APISIMP rows are now ✅ done, ⛔ blocked/deferred, or ⏳ decision rows.**
No new dispatchable slice was found. The in-memory paging sweep series is clean.
