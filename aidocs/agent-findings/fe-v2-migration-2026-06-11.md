---
stage: concept
last-stage-change: 2026-06-11
---

# Frontend v2-only migration manifest — 2026-06-11 (FE-V2)

**Task:** Migrate the Shepard frontend off the legacy v1 API helper
`useShepardApi` onto the v2 helper `useV2ShepardApi`, per CLAUDE.md
"this fork's frontend builds on /v2/ exclusively". Migrate the
cleanly-swappable call sites; keep + document the legitimate v1-fallback
set; file backlog rows for anything needing a v2 endpoint that does not
exist yet. Conservative: do not break runtime behaviour.

## TL;DR

**Zero clean single-swap migrations are available in the current
generated client.** Every resource still on `useShepardApi` lacks a
generated v2 client class to swap to, so a helper swap alone would build
`/<resource>` (root, no `/v2/`, no `/shepard/api`) → **404**. The remaining
v1 surface is *already fully decomposed and tracked* across the in-flight
`V2-SWEEP-001..004` rows + the `frontend-v2-exclusivity-audit` row family
in `aidocs/16`. Forcing migrations now would duplicate in-flight raw-fetch
work and risk merge collisions — the opposite of the conservative mandate.

Baseline gates on this worktree: **lint 0 errors / 15 pre-existing
warnings; typecheck 0 errors; tests 169 files / 1889 passing.** No source
changes were required (and none were safe), so the gate deltas are zero.

## Why no clean swap exists (root cause)

The generated `@dlr-shepard/backend-client` exposes **two disjoint client
families**:

- **Pure-v2 clients** — their own path templates carry `/v2/...`
  (e.g. `MeApi` → `/v2/users/me`, `CollectionContainersApi` →
  `/v2/collections/{appId}/referenced-containers`, `DataObjectV2Api`,
  `ProvenanceApi`, `SnapshotApi`, `CollectionSnapshotApi`,
  `CollectionTemplateApi`, `CollectionWatchesApi`, `GitCredentialsApi`,
  `GitReferenceApi`, `MeRoleInApi`, `NotebookApi`, `SemanticSparqlApi`,
  `ShepardTemplateApi`, `CollectionLabJournalEntriesApi`, `AdminFeaturesApi`,
  `AdminMetricsApi`, `AdminPermissionAuditApi`, `TimeseriesReferenceV2Api`).
  These are **already** paired with `useV2ShepardApi` everywhere — there are
  **zero** mispairings (no v2 client used with the v1 helper).
- **Pure-v1 clients** — their path templates have no `/v2/` and only resolve
  under `/shepard/api/...` (e.g. `CollectionApi`, `DataObjectApi`,
  `FileContainerApi`, `FileReferenceApi`, `StructuredDataContainerApi`,
  `StructuredDataReferenceApi`, `SpatialDataContainerApi`,
  `SemanticAnnotationApi`, `SemanticRepositoryApi`, `SearchApi`, `UserApi`,
  `UserGroupApi`, `SubscriptionApi`, `ApikeyApi`, `LabJournalEntryApi`,
  `CollectionReferenceApi`, `DataObjectReferenceApi`, `UriReferenceApi`,
  `HealthzApi`, `VersionzApi`, plus the `Timeseries*` container/reference
  clients).

The intersection of "clients used with `useShepardApi`" and "pure-v2
clients" is **empty**. Several v2 *backend* surfaces now exist
(`/v2/annotations` SEMA-V6-004, `/v2/user-groups` V2-SWEEP-002,
`/v2/references?kind=` V2CONV-A2, `/v2/containers?kind=` V2CONV-A3) but the
TypeScript client **has not been regenerated since V2CONV-A2/A3**, so no
generated client exposes them. This is exactly `V2-SWEEP-001-CLIENT-REGEN`
in `aidocs/16` ("ReferencesApi and ContainersApi are absent — all new v2
call sites use raw `fetch` shims"). Until that regen lands, every migration
proceeds per-resource via raw-fetch composables (the established pattern:
`useUserGroupsV2.ts`, `createV2Container.ts`, the V2-SWEEP-003 accessor
work).

## MIGRATED (this PR)

| File | v1 client → v2 client |
| --- | --- |
| _(none)_ | No clean generated-client swap is available; see root cause above. |

A swap was evaluated and **rejected as unsafe** for the one near-candidate:

- `composables/context/useFetchUserProfile.ts` — `UserApi.getCurrentUser()`
  (returns `User`) → `MeApi.getMe()` (returns the distinct `MeIO` shape).
  The composable returns `User`, casts to read `effectiveRoles`, and hydrates
  the global role cache from `username`-keyed fields. `MeIO` is a different
  model (no `MeIO.d.ts` in the client models dir), so the swap changes the
  return type and risks the role-hydration consumers. Conservative: left v1.
  (Resolves cleanly once the client is regenerated and `MeIO`/`User` parity
  is verified.)

## KEPT-V1 (documented v1 exceptions — already tracked)

Every remaining `useShepardApi` resource family maps to an existing backlog
row. None is untracked. Grouped by owning row:

| Resource family (clients) | Representative files | Owning backlog row |
| --- | --- | --- |
| Collection roles (`CollectionApi.getCollectionRoles`) | `useFetchCollection.ts` (documented "no v2 equivalent yet"), `CollectionAccessor.ts`, `CollectionLabJournalEntryList.vue`, `DataObjectLabJournalEntryList.vue` | named fallback set (CLAUDE.md §4); no v2 roles endpoint |
| Collection CRUD/get/list (`CollectionApi`) | `useFetchRecentCollections.ts`, `useCollectionAppIdResolver.ts`, `MetadataCompletenessCard.vue`, `CollectionSidebarHeader.vue`, `useEditCollection.ts`, `CreateCollectionDialog.vue`, `pages/collections/[collectionId]/index.vue` | no v2 generated client (blocks on `V2-SWEEP-001-CLIENT-REGEN`); export → `EXPORT-V2-STREAM` |
| DataObject CRUD/predecessors/successors (`DataObjectApi`) | `useFetchRelatedEntities.ts`, `CollectionCrossTrackViewPane.vue`, `useMffdNdtGridProbe.ts`, `MffdNdtGridCard.vue`, `CreateDataObjectDialog.vue` | `PRED-V2-SHAPE` (predecessors/successors), `SIDEBAR-V2-CREATE` (create), `LINEAGE-V2` |
| Search (`SearchApi`) | `useDataObjectSearch.ts`, `useCollectionSearch.ts`, `useContainerSearch.ts`, `useSearchCollections.ts`, `useSearchContainers.ts`, `useMemberSearch.ts`, `usePermissionUserSearch.ts`, `searchService.ts` | `SEARCH-V2` (no `/v2/search`) |
| Semantic annotation (`SemanticAnnotationApi`) | `annotated.ts`, `addSemanticAnnotation.ts`, `useFetchUserProfile.ts` (indirect), `useHandleUserGroupMembers.ts` | `ANNOT-V2` (v1 triple shape ≠ SEMA-V6 `/v2/annotations` shape; needs model mapping) |
| Semantic repository (`SemanticRepositoryApi`) | `useFetchSemanticRepositories.ts`, `useCollectionSearch.ts`, `SemanticRepositoryPane.vue`, `CreateSemanticRepositoryDialog.vue` | no v2 generated client (regen + ANNOT-V2 cluster) |
| User-group CRUD + membership (`UserGroupApi`) | `useCreateUserGroup.ts`, `mapPermissions.ts`, `useHandleUserGroupMembers.ts` | `V2-SWEEP-002-3` (remaining roles/permissions V1-EXCEPTIONs; CRUD already on `useUserGroupsV2.ts`) |
| User profile (`UserApi`) | `useFetchUserProfile.ts`, `useFetchApiKeys.ts`, `useFetchSubscriptions.ts`, `useFetchCollectionPermissions.ts`, `shepardObjectAccessor.ts`, `HealthDisplay.vue`, `InterpretAsTrajectoryButton.vue` | no v2 client for `getCurrentUser`/`getUser` (regen); `MeApi.getMe` shape-mismatch (see MIGRATED note) |
| Subscriptions (`SubscriptionApi`) | `useFetchSubscriptions.ts`, `AddSubscriptionButton.vue`, `DeleteSubscriptionButton.vue`, `MffdNdtGridCard.vue` | no v2 subscriptions client (regen) |
| API keys (`ApikeyApi`) | `useFetchApiKeys.ts`, `AddApiKeyDialog.vue`, `DeleteApiKeyButton.vue` | no v2 api-key client (regen) |
| Health / version (`HealthzApi`, `VersionzApi`) | `HealthPane.vue`, `VersionPane.vue`, `HealthDisplay.vue`, `useMemberSearch.ts`, `useMffdNdtGridProbe.ts` | infra probes; no v2 counterpart needed (regen if desired) |
| Lab-journal entry CRUD (`LabJournalEntryApi`) | `LabJournalNewEntry.vue`, `LabJournalExistingEntry.vue`, `DataObjectLabJournalEntryList.vue` | collection-scoped LIST already v2 (`CollectionLabJournalEntriesApi`); per-entry CRUD has no v2 client (regen) |
| Container list/get/delete (`FileContainerApi`, `StructuredDataContainerApi`, `SpatialDataContainerApi`) | `FileContainerAccessor.ts`, `StructuredDataAccessor.ts`, `SpatialDataContainerAccessor.ts`, `ContainerListPage.vue`, `PayloadVersionHistoryDialog.vue`, `useCreateSpatialDataContainer.ts`, container pages, `CreateDataReferenceDialog.vue` | `V2-SWEEP-003-CONTAINER-API-MIGRATION` + `CONTAINER-V2-ROUTE`. Spatial = frozen upstream-compat carrier (`SPATIAL-V6-003` / `PLUGIN-V2-001`) |
| Reference list/create (`FileReferenceApi`, `StructuredDataReferenceApi`, `DataObjectReferenceApi`, `UriReferenceApi`, `CollectionReferenceApi`) | `useCreateReferences.ts`, `useCreateFileReference.ts`, `useDeleteReferences.ts`, `useFetchDataReferences.ts`, `useFetchFileReference*.ts`, `useFetchStructuredDataReference.ts`, `useFetchRelatedEntities.ts`, `useSpatialDataReferencesForDataObject.ts`, viewer dialogs, reference detail pages | `V2-SWEEP-004-REF-API-MIGRATION` + `REFS-V2-PANELS` + `COLLREF-V2-APPID` |

## EXCLUDED (timeseries — owned by the concurrent TS-container agent)

Per the scope guard, all `frontend/**/timeseries*` files,
`useTimeseriesContainer*`, `components/container/timeseries/**`, and the
TS-container page were left untouched (9 files):

| File |
| --- |
| `composables/container/TimeseriesContainerAccessor.ts` |
| `composables/context/useFetchTimeseries.ts` |
| `composables/context/useFetchTimeseriesReferences.ts` |
| `composables/context/useFetchTimeseriesReferencePayload.ts` |
| `composables/context/useFetchTimeseriesReferencesMetrics.ts` |
| `composables/context/useFetchTimeseriesAnnotations.ts` |
| `components/container/timeseries/TimeseriesAllChannelsChart.vue` |
| `components/container/timeseries/ReferencedByRow.vue` |
| `pages/collections/[collectionId]/dataobjects/[dataObjectId]/timeseriesereferences/[timeseriesReferenceId]/index.vue` |

(`useFetchChannelPreview.ts` matches `Timeseries*` via its `TimeseriesContainerApi`
import and the `useTimeseriesContainer*`/container surface; left to the TS agent.)

## Recommendation (the ordering that actually unblocks this)

1. **`V2-SWEEP-001-CLIENT-REGEN`** is the keystone. Regenerating
   `@dlr-shepard/backend-client` against the current `/v2.json` makes
   `ReferencesApi`, `ContainersApi`, `UserGroupV2Api`, the `/v2/annotations`
   surface, and a `MeApi`/`UserApi` parity check available as type-safe
   clients. After it lands, ~50 of these call sites become genuine
   one-line `useShepardApi(XApi)` → `useV2ShepardApi(XV2Api)` swaps with a
   small request-shape adaptation — exactly the "clean swap" this task
   wanted but which the un-regenerated client makes impossible today.
2. Until then, the raw-fetch per-resource composables (V2-SWEEP-002/003/004,
   ANNOT-V2, SEARCH-V2) are the sanctioned path and are already in flight.

## Backlog status

No new backlog row was needed — every remaining family is already owned by
a row in `aidocs/16` (`V2-SWEEP-001..004`, `V2-SWEEP-002-3`, `SEARCH-V2`,
`ANNOT-V2`, `REFS-V2-PANELS`, `CONTAINER-V2-ROUTE`, `COLLREF-V2-APPID`,
`PRED-V2-SHAPE`, `SIDEBAR-V2-CREATE`, `EXPORT-V2-STREAM`, `LINEAGE-V2`,
named fallback `getCollectionRoles`). A consolidating pointer row
(`FE-V2-MIGRATION-MANIFEST`) is added under the FRONTEND-V2 cluster citing
this manifest so the inventory is discoverable from the SSOT.
