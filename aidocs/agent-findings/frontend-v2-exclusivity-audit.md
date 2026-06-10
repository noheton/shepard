---
stage: feature-defined
last-stage-change: 2026-06-10
---

# Frontend v2 / appId exclusivity audit

Read-only audit of `/opt/shepard/frontend/` against the CLAUDE.md rule
"Always: this fork's frontend builds on /v2/ exclusively". Scope: every
`useShepardApi` (v1 helper) call site, every numeric-Neo4j-id leak into a
route / link / emit / v2 request, the v2-client-with-v1-helper mispairings,
and the 3 live operator bugs.

Tooling note: ripgrep over `frontend/`; v2 route ground-truth from
`http://localhost:8080/shepard/doc/openapi/v2.json` (HTTP 200, 561 KB,
fetched with `X-API-KEY`); `git show 306896a3f` for the sidebar regression
intent. Nuxt 3 file-based routing + Vue Router `router.push`/`navigateTo`
semantics (per Nuxt docs) used to classify route-param leaks.

## What I found

The frontend is **structurally bilingual**: appId on the wire for reads
through `useV2ShepardApi` (105 call sites), but a large, *deliberately
retained* v1 substrate underneath — **144 `useShepardApi` (v1 helper) call
sites across 78 files** (excluding tests). The v1 calls are not accidental;
they are gated behind a canonical numeric-id-resolution helper
(`resolveNumericId` in `utils/collectionRouteParams.ts:71`) that re-derives
the Neo4j `Long` from the loaded v2 entity's `.id` precisely so the v1
endpoints keep working on appId-routed pages. That helper *is* the leak
surface: every consumer of it is a v1 dependency that the rule wants gone.

The good news: the v2 openapi now exposes appId-keyed equivalents for the
**highest-traffic** v1 calls — `/v2/annotations` (CRUD + find),
`/v2/collections/{collectionAppId}/data-objects` (+ `/children`,
`/predecessors`, `/successors`, `/predecessor-chain`), the per-reference
annotation shelves, and the data-object detail/patch/delete. So most of the
144 sites are now *migratable*, not blocked. The genuine remaining
exceptions are narrow: collection **roles** (no `/v2/.../roles`), **search**
(no v2 search surface), **user/user-group/api-key/subscription** admin
surfaces, and **timeseries-container** content endpoints whose v2 path is
still numeric (`{containerId} integer int64`) pending the TS-ID migration.

Counts by category (call sites, tests excluded):
- **A. v1 helper (`useShepardApi`) call sites: 144** across 78 files.
- **B. numeric-id resolution leaks: 4 detail pages** drive ~40 numeric-id
  prop/arg passes via `resolveNumericId` + `collectionNumericId` /
  `dataObjectNumericId`; plus the sidebar `useTreeviewItems`
  `collectionNumericId` path.
- **C. v2-client-with-v1-helper mispairings: 0 active** (the historical
  `listReferencedContainers 404` is fixed — `useFetchCollectionContainers.ts`
  now uses the v2 helper; only doc-comments reference the old failure).
- **D. per-page: 4 of 35 pages leak** numeric ids structurally; ~20 more
  leak transitively through v1-only composables.
- **E. 3 live bugs root-caused below.**

## The 3 live bugs (root-caused)

### Bug 1 — "Error while fetching Jupyter config: HTTP 404"
**Frontend is CORRECT; this is a BACKEND route-registration gap.**
`composables/context/admin/useJupyterConfig.ts:62` reads
`GET /v2/jupyter/config` (public) via raw fetch on the correctly-stripped
v2 base (`useJupyterConfig.ts:36-43`). I confirmed against the live v2
openapi: **no path containing `jupyter` exists in `v2.json` at all** —
neither `/v2/jupyter/config` nor `/v2/admin/config/jupyter` is registered
on the running backend. So the 404 is the backend not serving the route
(JupyterConfig REST resource not on the classpath / not scanned), not a
frontend path or helper error. Fix belongs backend-side: register the
`/v2/jupyter/config` + `/v2/admin/config/jupyter` resources; the frontend
needs **no change**. (Verify the plugin/feature that owns these routes is
actually built into the redeployed image — matches the
`minter_plugins_build_gap` class of "jar present locally, absent in CI image".)

### Bug 2 — sidebar broken in all projects — **FIXED (V2-SWEEP Wave 1, 2026-06-10)**

Fix shipped: `useTreeviewItems` now loads the whole tree from the v2
appId-keyed list (`GET /v2/collections/{collectionAppId}/data-objects`,
paged + exhaustive, `fields=id,appId,name,parentId,childrenIds`) via
`DataObjectV2Api` through `useV2ShepardApi`. The `collectionNumericId`
gate is deleted from both `useTreeviewItems.ts` and
`CollectionSidebar.vue`; treeview values, opened state, and sidebar entry
links all carry appIds. The numeric id survives only as
`TreeviewItem.numericId` feeding the still-v1-backed create dialog
(documented exception `SIDEBAR-V2-CREATE`). Original analysis below.

**Root cause: `useTreeviewItems` gates the whole tree load on a NUMERIC
collection id that no longer needs to exist.**
- `components/context/sidebar/useTreeviewItems.ts:99,109-110,360-361`:
  the initial tree load (`fetchTreeviewItems`) and child expansion
  (`fetchChildrenOfItem`) call the **v1** `DataObjectApi.getAllDataObjects({
  collectionId: <number>, parentId })`. The v1 path declares
  `@PathParam Long collectionId`, so the load is gated on
  `resolvedNumericCid()` (`useTreeviewItems.ts:91-98`) being defined.
- `components/context/sidebar/CollectionSidebar.vue:23-34` feeds that gate
  from `collection.value?.id` (the numeric id off the loaded v2 collection).
- **The failure:** commit `306896a3f` (BUG-COLL-APPID-ROUTE-006) made the
  spinner-forever into an explicit gate, but the gate still depends on a
  numeric `.id` being present on the v2 Collection payload. On the freshly
  reseeded **appId-only** data, if the v2 Collection payload doesn't surface
  a usable numeric `.id` (or it's null), `resolvedNumericCid()` returns
  `undefined` forever → `initialLoad()` early-returns (`:154-160`) →
  `treeviewItems` stays `undefined`/empty → sidebar shows the
  loading-spinner or empty tree in **every** collection.
- **The fix is now unblocked:** `GET /v2/collections/{collectionAppId}/data-objects`
  **and** `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/children`
  both exist in v2 (confirmed in openapi). Replace the two v1
  `getAllDataObjects` calls with the appId-keyed v2 list + children
  endpoints (mirroring the raw-fetch `fetchDataObjectV2` already in the same
  file at `:39-61`), drop the entire `collectionNumericId` plumbing from
  `useTreeviewItems` + `CollectionSidebar.vue`, and the numeric gate
  disappears. This is the **single highest-impact fix** (see end).

### Bug 3 — Trace3D / render URL carries a numeric id (stale-URL warning)
**Two distinct violations in `components/container/timeseries/ViewRecipeBuilderDialog.vue`:**
- `openUrdf()` (`:103-116`) puts a raw **`urdfUrl`** path/URL on the render
  query string — a direct breach of "UI never asks for paths/URLs — pulls
  from references" (CLAUDE.md). The canonical violator named in CLAUDE.md is
  exactly this `?renderer=urdf&urdfUrl=…&packagePath=…` shape; the right
  shape is `?renderer=urdf&urdfFileAppId=…`. (`pages/shapes/render.vue` still
  reads `urdfUrl`/`packagePath` query params to match.)
- `openTrace3D()` / `openUrdf()` / `openThermography()` (`:90,105,126`) all
  emit `containerId: String(props.containerId)` where `props.containerId` is
  typed `number` (`:7`). That **numeric** TS-container id lands on
  `/shapes/render`, which the render page consumes as
  `Number(containerId.value)` against the still-numeric v2 TS endpoints
  (`pages/shapes/render.vue:85` comment: "numeric TS container ID (legacy v1
  path)"). When a user reuses a **bookmarked** render URL after the wipe, the
  numeric container id is stale → the page 404s → `EntityNotFound.vue:37`
  fires `isNumericLegacyId(...)` → the stale-URL hint the operator saw.
- **Status nuance:** the numeric `containerId` is, *today*, an **allowed
  exception** — v2 TS-container content endpoints are themselves still
  numeric (`/v2/timeseries-containers/{containerId} integer int64`) pending
  the TS-ID migration. So Bug 3's *actionable* fix now is the `urdfUrl`
  path-leak (swap to `urdfFileAppId` + reference picker); the numeric
  `containerId` rides along with the documented TS-ID exception until that
  migration lands.

## v1 helper usages

144 call sites; grouped by migratability. **File:line → operation →
v2-equivalent exists?**

**MIGRATE NOW (v2 appId endpoint confirmed in openapi):**
- `composables/annotated.ts:24,54,89` — `SemanticAnnotationApi` CRUD →
  `/v2/annotations` (+ `/find`, `/{appId}`). High value: annotations are the
  fork's first-class surface.
- `components/context/semantic/annotation/addSemanticAnnotation.ts:67,85,105`
  — annotation create → `/v2/annotations`.
- `components/context/sidebar/useTreeviewItems.ts:99,360` — `getAllDataObjects`
  → `/v2/collections/{collectionAppId}/data-objects` + `/children` (Bug 2).
- `composables/context/useFetchAllDataObjects.ts:68`,
  `usePagedDataObjects.ts:76`, `useFetchDataObjectMap.ts:58` —
  `DataObjectApi` list/get → v2 data-objects list + detail.
- `composables/context/useFetchRelatedEntities.ts:169,186` — predecessors /
  successors → `/v2/.../predecessors`, `/successors`, `/predecessor-chain`.
- `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue:278`
  — `DataObjectApi` (patch/delete) → `/v2/.../data-objects/{dataObjectAppId}`.
- `composables/context/useFetchTimeseriesReferences.ts:49`,
  `useFetchTimeseriesReferencePayload.ts:35`,
  `components/container/timeseries/ReferencedByRow.vue:24` —
  `TimeseriesReferenceApi` annotations → `/v2/timeseries-references/{refAppId}/...`.
- `composables/references/useCreateFileReference.ts:34`,
  `useFetchFileReference.ts:49,100` — file reference create/fetch →
  `/v2/files` singleton (FR1b) + `/v2/files/by-data-object/{dataObjectAppId}`.

**EXCEPTION (no v2 equivalent yet — backlog each):**
- `getCollectionRoles` — no `/v2/.../roles`. **Documented exception** in
  CLAUDE.md; consumers: `useFetchCollectionPermissions.ts:16`,
  `useHandleUserGroupMembers.ts:65`. Keep v1; resolve numeric id from v2 entity.
- **Search** — no v2 search surface: `SearchApi` at
  `searchService.ts:67,147`, `useDataObjectSearch.ts:65`,
  `useCollectionSearch.ts:36`, `useContainerSearch.ts:37`,
  `useCreateUserGroup.ts:20`, `usePermissionUserSearch.ts:22`,
  `useMemberSearch.ts:33,37`, `useSearchContainers.ts:33`,
  `useSearchCollections.ts:18`. (~10 sites.) Backlog `SEARCH-V2-*`.
- **User / UserGroup / ApiKey / Subscription / Health / Version** admin
  surfaces — `UserApi`, `UserGroupApi`, `ApikeyApi`, `SubscriptionApi`,
  `HealthzApi`, `VersionzApi`: `mapPermissions.ts:101,107`,
  `shepardObjectAccessor.ts:48`, `useFetchSubscriptions.ts:11-12`,
  `useFetchApiKeys.ts:11-12`, `DeleteApiKeyButton.vue:12`,
  `AddApiKeyDialog.vue:25`, `HealthDisplay.vue:13`, `VersionPane.vue:11`,
  `HealthPane.vue:15`, `useHandleUserGroupMembers.ts` (~12 sites). Backlog.
- **Timeseries / Structured / Spatial container content + lab-journal** —
  `TimeseriesContainerApi`, `StructuredDataContainerApi`,
  `SpatialDataContainerApi`, `LabJournalEntryApi`: numeric-keyed v2 (TS-ID
  exception) or no v2 yet. ~25 sites incl. `TimeseriesContainerAccessor.ts:15`,
  `useFetchChannelPreview.ts:132`, `useFetchTimeseries.ts:13`, the four
  lab-journal components, the spatial-data pages.

## numeric-id leaks

The canonical offender is `resolveNumericId(loadedId, routeParam)`
(`utils/collectionRouteParams.ts:71-78`) — it re-derives the Neo4j `Long`
from a v2 entity's `.id` so v1 calls keep working. Every consumer is a leak:

- `pages/collections/[collectionId]/index.vue:43-44,75,98,229,519,572,582,616,653`
  — `collectionNumericId` computed, then passed as `:collection-id` to
  `CollectionLineageGraph`, `CollectionCrossTrackViewPane`,
  `CreateDataObjectDialog`, `MffdNdtGridCard`, and to v1 `getAllDataObjects` /
  `exportCollection`. **L**.
- `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue:75-88,182,312-320,412-420`
  — `collectionNumericId` + `dataObjectNumericId`, fed to v1 reference +
  annotation calls and `useSpatialDataReferencesForDataObject`. **L**.
- `.../structureddatareferences/[structuredDataReferenceId]/index.vue:31-45,70`
  — three numeric ids resolved for the v1 structured-data reference page. **M**.
- `.../timeseriesereferences/[timeseriesReferenceId]/index.vue:272,291` and
  `.../filereferences/[fileReferenceId]/index.vue:80,123` — v1 reference
  fetch/patch keyed on numeric ids. **M** each.
- `components/context/sidebar/CollectionSidebar.vue:23-34,328,398-399` +
  `useTreeviewItems.ts:80-98,154,180` — numeric collection-id gate (Bug 2). **M**.
- Broad cast pattern `as unknown as number` at v1 client boundaries
  (`CollectionSidebar.vue:328,398`, many composables) — symptom of the v1
  generated client typing path params `number` while routes carry strings.

**Route/link/emit leaks (numeric on the wire):** the render-link
`containerId` numeric (Bug 3, `ViewRecipeBuilderDialog.vue:93,111,130`) is
the only numeric id placed into a *navigable URL*. The sidebar entry links
(`CollectionSidebar.vue:316-322`, `onActivated:181-187`) route on
`item.id` — but `item.id` here is the treeview node id sourced from the v1
list, i.e. numeric. Once Bug 2 migrates the tree to the v2 list (which
returns appId), these links should carry appId; until then they are a
**numeric-id-in-route leak** (matching the 007-PAGE cluster). **M**.

## per-page status table

PAGE | v2-clean? | violations (file:line) | fix size
--- | --- | --- | ---
`index.vue` (home) | yes | — | —
`collections/index.vue` | yes | v2 list/search via helper | —
`collections/[collectionId]/index.vue` | **leaks** | `collectionNumericId` → v1 getAllDataObjects/export + numeric props (`:43-103,229,519-653`) | **L**
`collections/[collectionId]/dataobjects/[dataObjectId]/index.vue` | **leaks** | `collectionNumericId`+`dataObjectNumericId` → v1 refs/annotations (`:75-420`) | **L**
`.../filereferences/[fileReferenceId]/index.vue` | **leaks** | v1 `FileReferenceApi` numeric (`:80,123`) | **M**
`.../timeseriesereferences/[timeseriesReferenceId]/index.vue` | **leaks** | v1 `TimeseriesReferenceApi` numeric (`:272,291`) | **M**
`.../structureddatareferences/[structuredDataReferenceId]/index.vue` | **leaks** | 3× numeric ids, v1 (`:31-73`) | **M**
`containers/index.vue`, `containers/[type].vue` | partial | container content endpoints numeric (TS-ID exception) | **S** (track only)
`containers/timeseries/[containerId]/index.vue` | exception | v1 `TimeseriesContainerApi` numeric (TS-ID) | track
`containers/files|structureddata|spatialdata|hdf|video/[containerId]/index.vue` | partial/leaks | `StructuredDataContainerApi`/`SpatialDataContainerApi` v1 (`structureddata/index.vue:66,85`, `spatialdata/index.vue:56`) | **M**
`shapes/render.vue` | **leaks** | reads `urdfUrl`/`packagePath` + numeric `containerId` query (`:85,639`) | **M** (pair w/ Bug 3)
`shapes/validate.vue` | yes (appId) | — | —
`scene-graphs/play/[templateAppId].vue` | yes (templateAppId) | — | —
`semantic/*` (sparql/vocab/predicates) | yes (appId) | — | —
`snapshots/diff.vue` | yes (appId) | — | —
`tools/*`, `me/index.vue`, `about/index.vue` | yes/partial | user/health v1 (admin exception) | track
`admin/*` (index/provenance/mffd/instance-registry) | yes (appId/admin) | — | —
`search/index.vue` | exception | v1 `SearchApi` (no v2) | track
`projects/index.vue`, `help.vue`, `auth/signIn.vue`, `healthz/index.vue` | yes | — | —

Transitive leaks (via v1-only composables) touch every page that mounts the
reference panels, lab-journal lists, or the annotation dialog.

## Proposed fix waves (ordered by impact)

**Wave 0 — the live bugs (ship first):**
1. **Bug 2** — rewrite `useTreeviewItems` onto
   `GET /v2/collections/{collectionAppId}/data-objects` + `/children`; delete
   `collectionNumericId` plumbing in `useTreeviewItems.ts` +
   `CollectionSidebar.vue`. Restores the sidebar in every project AND removes
   the canonical numeric-gate leak. (Sidebar entry links then carry appId,
   closing the route leak too.)
2. **Bug 3** — `ViewRecipeBuilderDialog.openUrdf()`: replace `urdfUrl`/
   `packagePath` query with `urdfFileAppId` + a FileReference picker; update
   `pages/shapes/render.vue` to resolve URDF + mesh root from the reference.
3. **Bug 1** — backend: register `/v2/jupyter/config` + `/v2/admin/config/jupyter`
   and confirm the owning module is in the redeployed image. Frontend
   unchanged.

**Wave 1 — annotations + data-objects to v2 (biggest leak reduction):**
`annotated.ts`, `addSemanticAnnotation.ts` → `/v2/annotations`;
`useFetchAllDataObjects.ts`, `usePagedDataObjects.ts`,
`useFetchDataObjectMap.ts`, `useFetchRelatedEntities.ts` → v2 data-objects
list/detail/predecessor endpoints. Drop `dataObjectNumericId` from the DO
detail page. Eliminates ~30 v1 sites + the heaviest numeric-id plumbing.

**Wave 2 — references to v2:** file / timeseries / structured-data reference
fetch+create+patch onto `/v2/files`, `/v2/timeseries-references/{refAppId}`,
the structured-data reference shelf. Removes the 3 reference detail pages'
numeric ids.

**Wave 3 — file an exception/backlog ledger** for search, user/group/
api-key/subscription, lab-journal, and TS-container content; each gets a
`*-V2-*` row in `aidocs/16` per the CLAUDE.md exception rule (call-site
comment + backlog row + numeric id resolved from v2 entity).

## Allowed-exception set (documented)

These keep their v1 call **today** (no v2 counterpart) — each needs a
one-line call-site comment + an `aidocs/16` row:
- `getCollectionRoles` (no `/v2/.../roles`) — already named in CLAUDE.md.
- **Search** family (`SearchApi`, ~10 sites) — no v2 search surface.
- **User / UserGroup / ApiKey / Subscription / Health / Version** admin
  surfaces — no v2 equivalents.
- **Timeseries-container content** (`TimeseriesContainerApi`, channel data) —
  v2 path exists but is still **numeric** pending the TS-ID migration
  (`aidocs/platform/87`); the numeric `containerId` in render/picker URLs
  rides this exception.
- **Lab-journal** entry list/create (`LabJournalEntryApi`) — partial v2.
- Import wizard v15 endpoints (per CLAUDE.md) — out of this audit's grep set.

---

### Single highest-impact fix to do first
**Rewrite `components/context/sidebar/useTreeviewItems.ts` to load the tree
from `GET /v2/collections/{collectionAppId}/data-objects` (+ `/children`),
and delete the `collectionNumericId` gate in both `useTreeviewItems.ts` and
`CollectionSidebar.vue`.** This one change fixes Bug 2 (sidebar broken in
every project on the reseeded data), removes the canonical numeric-id
resolution leak the rest of the codebase copies, and makes the sidebar entry
links carry appId instead of numeric ids — closing a route-leak in the same
stroke. The v2 endpoints it needs already exist; nothing blocks it.
