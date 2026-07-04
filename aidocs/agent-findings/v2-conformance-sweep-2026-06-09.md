---
stage: deployed
last-stage-change: 2026-06-09
---

# V2 conformance sweep — 2026-06-09

**Scope:** Two-arm conformance audit run as part of the V2CONV+MFFD+UI
pipeline fire on 2026-06-09. Supersedes the 2026-06-03 frontend sweep
(`frontend-v2-only-sweep.md`) for the frontend arm; adds the post-V2CONV-A2/A3
gap analysis and the plugin backend arm.

## What I found

### Arm A — Frontend v1 helper usage

**Total:** 150 `useShepardApi(...)` calls across 88 files (as of 2026-06-09).

All 150 calls pair with genuinely-v1 API classes — no v2 client is
mis-routed through the v1 helper (the June 2026 FRONTEND-V2-ONLY-SWEEP
confirmed Rule 1 is clean; this sweep re-confirms no regression).

#### By API class

| API class | Count | Status |
|---|---|---|
| `DataObjectApi` | 12 | Partial v2: `DataObjectV2Api` covers list; individual CRUD uses raw `fetch` to `/v2/…`. Remaining calls: labelled-relationship reads, bulk-fetch, collection-scoped list. |
| `CollectionApi` | 11 | Partial v2: create via `POST /v2/collections`; fetch by appId via raw `fetch`. Remaining calls: `getCollectionRoles` (no v2 → named exception), `editCollectionPermissions` (no v2 → PERMS-1 hold-back), sidebar tree, search-indexed list. |
| `UserGroupApi` | 11 | No v2 counterpart yet. Valid v1 exception. **Filed: V2-SWEEP-002-USERGROUP-V2** |
| `TimeseriesContainerApi` | 11 | V2CONV-A3 shipped `/v2/containers?kind=timeseries` for create; list/get still on v1 pending client regen. **Filed: V2-SWEEP-003-CONTAINER-API-MIGRATION** |
| `FileReferenceApi` | 11 | V2CONV-A2 shipped `/v2/references?kind=file`; calls still on v1 pending client regen. **Filed: V2-SWEEP-001-CLIENT-REGEN + V2-SWEEP-004-REF-API-MIGRATION** |
| `SearchApi` | 10 | No v2 counterpart yet. Valid v1 exception. **Filed: MISSING-V2-SEARCH** (pre-existing) |
| `UserApi` | 9 | Partial v2: `MeApi` covers current-user; remaining calls are admin-list/search. Valid v1 exception for non-`/me` operations. |
| `StructuredDataContainerApi` | 9 | V2CONV-A3 shipped `/v2/containers?kind=structured-data`; list/get still on v1 pending regen. **Filed: V2-SWEEP-003** |
| `TimeseriesReferenceApi` | 8 | V2CONV-A2 shipped `/v2/references?kind=timeseries`; calls still on v1 pending regen. **Filed: V2-SWEEP-004** |
| `FileContainerApi` | 8 | V2CONV-A3 shipped `/v2/containers?kind=file`; list/get still on v1 pending regen. **Filed: V2-SWEEP-003** |
| `StructuredDataReferenceApi` | 5 | V2CONV-A2 shipped `/v2/references?kind=structured-data`; pending regen. **Filed: V2-SWEEP-004** |
| `SemanticAnnotationApi` | 5 | No v2 counterpart yet. Valid v1 exception (`aidocs/16` SEMA-V6 tracks). |
| `SpatialDataContainerApi` | 4 | Spatiotemporal plugin frozen upstream-compat paths. CLAUDE.md documented exception. |
| `DataObjectReferenceApi` | 4 | V2CONV-A2 shipped `/v2/references?kind=data-object`; pending regen. **Filed: V2-SWEEP-004** |
| `UriReferenceApi` | 3 | V2CONV-A2 shipped `/v2/references?kind=uri`; pending regen. **Filed: V2-SWEEP-004** |
| `SubscriptionApi` | 3 | No v2 counterpart yet. Valid v1 exception. |
| `SemanticRepositoryApi` | 3 | No v2 counterpart yet. Valid v1 exception. |
| `LabJournalEntryApi` | 3 | Partial v2: `CollectionLabJournalEntriesApi` covers collection-scoped list; individual-entry ops still on v1. |
| `CollectionReferenceApi` | 3 | V2CONV-A2 shipped `/v2/references?kind=collection`; pending regen. **Filed: V2-SWEEP-004** |
| `ApikeyApi` | 3 | No v2 counterpart yet. Valid v1 exception (SHEPARD-FORK-KEY-ROTATE queued). |
| `HealthzApi` | 2 | No v2 counterpart yet. Valid v1 exception. |
| `VersionzApi` | 1 | No v2 counterpart yet. Valid v1 exception. |

#### Post-V2CONV gap (primary new finding)

V2CONV-A2 (2026-06-04) shipped `/v2/references?kind=` for all reference types.
V2CONV-A3 (2026-06-04) shipped `/v2/containers?kind=` for all container types.

The TypeScript client (`@dlr-shepard/backend-client`) has **not been
regenerated** since these surfaces shipped. As a result:
- Reference APIs (FileReferenceApi, TimeseriesReferenceApi, etc.) continue
  to be called via the v1 helper even though v2 parity exists.
- Container APIs (FileContainerApi, etc.) list/get operations remain on v1
  even though `/v2/containers?kind=` is available.

The structural fix is **client regeneration (V2-SWEEP-001-CLIENT-REGEN)**.
Until that lands, the migration path is raw `fetch` to the v2 paths
(as already done for container create in `createV2Container.ts` and for
reference edit in `EditFileReferenceDialog.vue`).

### Arm B — Plugin backend conformance

#### Files with `Constants.SHEPARD_API` usage in plugin code

| File | Usage | Verdict |
|---|---|---|
| `plugins/spatiotemporal/src/…/SpatialDataPointRest.java` | `@Path(Constants.SHEPARD_API + "/" + Constants.SPATIAL_DATA_CONTAINERS)` | **DOCUMENTED EXCEPTION** — frozen upstream-byte-compat path per CLAUDE.md §4 + `openapi-5.4.0.json`. PLUGIN-V2-001 tracks the v2 sibling shelf. |
| `plugins/spatiotemporal/src/…/SpatialDataReferenceRest.java` | Same pattern for reference resource | **DOCUMENTED EXCEPTION** — same rationale. |
| `plugins/v1-compat/src/…/LegacyV1DeprecationFilter.java` | `Constants.SHEPARD_API` used as path prefix in filter | **ALLOWED** — v1-compat's entire purpose is to gate/meter the v1 surface (CLAUDE.md §5). |

**No new violations found.** The 2026-05-24 `plugin-v2-only-audit.md` findings
are unchanged: spatiotemporal is the only non-v1-compat plugin with v1 paths,
and those paths are the frozen upstream-compat carrier.

#### Numeric IDs in plugin endpoints

Checked `@PathParam` / `@QueryParam` in plugin REST classes for `Long` id params:
- `SpatialDataPointRest`: uses `Long containerId` — frozen compat, same exception.
- All other plugins use `String appId` (UUID v7) in all path/query params. ✓

### doc-stage-check fix (bonus finding)

Identified root cause of spurious CI DRIFT failures on PRs #1794 and #1795:
`regenerate-doc-stage-index.py` uses `git log -1 --format=%ad -- <file>`,
which traverses both parents of a merge commit. git 2.54.0 (CI) computes
different last-touched dates from this traversal than git 2.43.0 (local).

**Fix:** add `--first-parent` to `git_last_touched` — makes traversal
deterministic across git versions. Shipped on branches `plugin-perkind-crud-cleanup`
and `ci-baseline-4-file-migration-race`; included in this PR for main.

## Opportunities

1. **V2-SWEEP-001-CLIENT-REGEN**: Regenerate `@dlr-shepard/backend-client`
   from current OpenAPI spec. Unlocks type-safe usage of `ReferencesApi` and
   `ContainersApi` across the frontend, retiring raw `fetch` shims and the
   FE-BUILD-03-REGEN cast holdover.

2. **V2-SWEEP-002-USERGROUP-V2**: Design and ship `/v2/user-groups` surface
   (11 frontend calls, no v2 path, no tracking row). Highest-count valid
   exception with no existing fix track.

3. **V2-SWEEP-003-CONTAINER-API-MIGRATION**: Migrate FileContainerApi,
   TimeseriesContainerApi, StructuredDataContainerApi list/get calls to
   raw `fetch` against `/v2/containers?kind=` (unblocks operator benefit
   before client regen lands).

4. **V2-SWEEP-004-REF-API-MIGRATION**: Migrate reference API list/create
   calls similarly; reference delete already migrated (DataObjectDataReferencesTable).

## Gaps & blockers

- Client regeneration is the structural fix for the majority of the remaining
  v1 call sites; without it, every migration requires a raw `fetch` shim.
  The kiota pipeline (`clients-kiota.yml`) exists but only covers the
  `/v2/` surface clients — a `@dlr-shepard/backend-client` regen pipeline
  targeting the full OpenAPI spec has not yet been wired.
- `UserGroupApi` (11 calls) and `SearchApi` (10 calls) have no v2 counterpart
  at all; they cannot be migrated until new endpoints ship.

## What surprised me

- The doc-stage-check CI failures on PRs #1794/#1795 had nothing to do with
  aidocs content — they were a git version incompatibility in the `git log -1`
  traversal of synthetic merge commits. Reproducing locally (with git 2.43.0)
  was impossible; the fix is `--first-parent`.
- The V2CONV-A2/A3 surfaces shipped weeks ago but the TypeScript client has
  not been regenerated. All new reference/container call sites added since
  V2CONV use raw `fetch`, creating a growing gap between the API surface and
  the generated client.
