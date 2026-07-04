---
stage: deployed
last-stage-change: 2026-06-03
---

# Frontend v2-only sweep — findings

Comprehensive enforcement pass for the new CLAUDE.md rule
*"this fork's frontend builds on /v2/ exclusively"* (commit `191ca0044`).
Two-rule scope: (1) every generated `/v2/...` client must be obtained via
`useV2ShepardApi(...)`, (2) every route / link / `router.push` carries
the v2 appId (UUID v7), never a numeric Neo4j id.

## What I found

### Rule 1 — v2-helper coverage: already clean

Inventoried 19 v2 generated API classes (grepped `path: \`/v2/` over
`backend-client/src/apis/*.ts`):
`AdminFeaturesApi`, `AdminMetricsApi`, `AdminPermissionAuditApi`,
`CollectionContainersApi`, `CollectionLabJournalEntriesApi`,
`CollectionSnapshotApi`, `CollectionTemplateApi`, `CollectionWatchesApi`,
`DataObjectV2Api`, `GitCredentialsApi`, `GitReferenceApi`, `MeApi`,
`MeRoleInApi`, `NotebookApi`, `ProvenanceApi`, `SemanticSparqlApi`,
`ShepardTemplateApi`, `SnapshotApi`, `TimeseriesReferenceV2Api`.

Searched every `frontend/{composables,components,pages,utils}/**/*.{ts,vue}`
for `useShepardApi(<v2-class>)` misuses. **Zero hits.** Every remaining
`useShepardApi(...)` pairs with a genuinely v1 client (CollectionApi,
DataObjectApi, FileReferenceApi, UserApi, SearchApi, etc.). The
predecessor `-006`/`-007`/`-007-PAGE` cluster already closed the v2-helper
gap; this sweep confirms no regression.

### Rule 2 — appId-route compliance: two leaks fixed, three classes filed

| File | Leak | Status |
|---|---|---|
| `frontend/components/context/collection/CollectionDataObjectsPanel.vue:57,310` | `NuxtLink :to` + `navigateTo` used `props.collectionId` (numeric) + `row.id` (numeric) | **fixed** — prefers `props.collectionAppId` + `row.appId` (DataObjectListItemV2 carries `appId`; the panel now plumbs it into `Row` and `rowHref()`) |
| `frontend/components/context/collection/CollectionLineageGraph.vue:389` | Click handler used `props.collectionId` + raw graph node `value` (numeric) | **fixed** — looks up the appId by numeric id from the loaded `dataObjects` payload; falls back to numeric only for the rare row that lacks an appId |
| `frontend/components/layout/HeaderBar.vue:618,625` | Search-result navigation uses numeric `id`/`collectionId` from `DataObjectSearchResult` | left numeric — v1 `SearchApi` returns no `appId` on the wire; filed **MISSING-V2-APPID-IN-SEARCH** |
| `frontend/components/context/display-components/relationships/relationshipTableElementMappingUtil.ts:66,73,82` | Relationship table cells use numeric ids from v1 search payload | left numeric — same source as above |
| `frontend/components/container/LinkedDataObjectRow.vue:31,39` | DataObject backref from container side | left numeric — v1 reference-list payload has no `appId`; filed **MISSING-V2-APPID-IN-REFLISTS** |
| `frontend/components/container/timeseries/ReferencedByRow.vue:78,86` | Same shape as above | same MISSING row |
| `frontend/components/context/collection/CiteThisCard.vue:54` | Cite-link uses `collection.id` (numeric) | left numeric — v1 entity payload |
| `frontend/components/context/home/PersonalDigest.vue:377` | Recent-collection card link uses `collection.id` | left numeric — same |
| `frontend/components/common/EntityNotFound.vue:11` | comment example `/collections/1787/dataobjects/1792` | not a live link — left as docstring |

Each "left numeric" site continues to resolve because `parseIdLike` accepts
both UUID v7 and numeric strings on the route param — these are
rule-conformance debt, not 404-class bugs. The backlog rows
**MISSING-V2-APPID-IN-SEARCH** and **MISSING-V2-APPID-IN-REFLISTS** track
the backend-shape fix (extend wire payloads with `appId`).

## What I fixed

1. **`CollectionDataObjectsPanel.vue`** — extended `Row` with `appId: string | null`; new `rowHref()` helper prefers `props.collectionAppId` + `row.appId`, falls back to numeric only when either is absent. `navigateTo()` takes the row instead of just the id.
2. **`CollectionLineageGraph.vue`** — graph click handler now looks up `appId` by numeric id from the loaded `dataObjects` ref; uses `props.collectionAppId ?? props.collectionId` for the collection segment.
3. **`tests/unit/useTreeviewItems.test.ts`** — fixed the stale worktree-absolute mock path (was pinned to `agent-aee408f027ac8f0b7` which left real `useOpenedItems` leaking once main moved off that worktree) and supplied the new `collectionNumericId` parameter introduced by the spinner fix.

## The infinite spinner

**Diagnosed and fixed.** Root cause: `useTreeviewItems.ts` line 67 cast
the route's `collectionId` (UUID v7 string) to `unknown as number` and
fed it to v1 `getAllDataObjects`. The v1 `DataObjectRest.getAllDataObjects`
path param is a primitive `Long`, so JAX-RS 400'd at binding before the
service ran. The catch left `treeviewItems.value` undefined; the template's
`<CenteredLoadingSpinner v-if="loading || !treeviewItems" />` therefore
spun forever. The composable's own comment (lines 21-28) acknowledged the
bug as a known issue gated on a future v2 list endpoint.

**Symptom-fix shipped this PR (BUG-COLL-APPID-ROUTE-006 in `aidocs/16`):**
- `useTreeviewItems(routeParams, collectionNumericId?)` — second arg is a `MaybeRefOrGetter<number | undefined>` resolved by the caller from the loaded v2 Collection's `.id`. v1 list calls (`fetchTreeviewItems`, `fetchChildrenOfItem`) are gated on it being defined.
- Watcher fires the v1 list as soon as the numeric id transitions undefined → number, so the deferred load completes naturally.
- New `loadError` flag flips true on v1 4xx; the template renders an explicit error sentinel (mdi-alert-outline + retry hint) instead of a permanent spinner.
- `fetchTreeviewItem`, `getPathToItem`, `fetchDataObjectV2` retyped to take a `string` collection id (the v2 endpoint's `EntityIdResolver` accepts both UUID and numeric on the wire) — disentangling the v1-numeric vs v2-string id flows.

`CollectionSidebar.vue` was updated to compute `collectionNumericId` from `collection.value?.id` and pass it in, plus render the error sentinel.

The "right shape" — extending `GET /v2/collections/{appId}/data-objects` with `parentId`/`predecessorId`/`successorId` query support so we can drop the v1 list call entirely — is filed as **BUG-COLL-APPID-ROUTE-006-V2-LIST**.

## Test coverage

| Test file | Coverage |
|---|---|
| `tests/unit/useTreeviewItemsDeferredLoad.test.ts` (NEW, 4 cases) | Deferred v1 fetch gated on numeric id; legacy `/collections/123` numeric fallback; `loadError` flip on v1 4xx; no v1 call while id undefined |
| `tests/unit/collectionDataObjectsPanelRoute.test.ts` (NEW, 4 cases) | `rowHref()` prefers both appIds; falls back to numeric collection / numeric DataObject / both numeric |
| `tests/unit/useTreeviewItems.test.ts` (UPDATED) | Stale worktree mock path fixed; supplies new numericId param so the existing -005 v2-routing test still hits `fetchTreeviewItem` |

**Six-gate state:** lint clean on changed files (84 pre-existing errors elsewhere unaffected), typecheck clean, vitest 1832/1837 pass — the 5 failures are in `useFetchRecentCollections.test.ts` and are pre-existing on `main` (confirmed via `git stash` + re-run).

## Next steps (priority order)

1. **BUG-COLL-APPID-ROUTE-006-V2-LIST** — extend `DataObjectV2Rest.list` with `parentId` / `predecessorId` / `successorId` query params; drop the v1 list call from `useTreeviewItems` and `useDataObjectSearch`. Medium effort; unblocks dropping the symptom-fix gate.
2. **MISSING-V2-APPID-IN-SEARCH** — ship `/v2/search` (or extend `BasicEntity` with `appId`) so the HeaderBar global search, `useDataObjectSearch`, `useContainerSearch`, and `relationshipTableElementMappingUtil` can navigate by appId. Closes ~4 sites left on numeric.
3. **MISSING-V2-APPID-IN-REFLISTS** — extend v1 reference-list responses (or ship v2 counterparts) so `LinkedDataObjectRow`, `ReferencedByRow`, `CiteThisCard`, `PersonalDigest` can build appId routes. Closes the remaining 4 sites.
4. **BUG-COLL-APPID-ROUTE-PERMS-1** (pre-existing) — v2 PermissionsRest so the last v1 hold-back on `useFetchCollection` (the `getCollectionRoles` call) can move.
5. **Pre-existing test failures in `useFetchRecentCollections.test.ts`** — 5/6 cases failing on `main` independent of this work; should be triaged separately.
