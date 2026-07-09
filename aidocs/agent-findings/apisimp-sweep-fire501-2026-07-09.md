---
stage: deployed
last-stage-change: 2026-07-09
---

# APISIMP Sweep — fire-501 (2026-07-09)

Full scan of all `/v2/**` REST resource classes and plugin REST classes for
residual sprawl not yet tracked in `aidocs/16`.

## Scan scope

- `backend/src/main/java/de/dlr/shepard/v2/**/*Rest.java` — 73 classes
- `plugins/**/src/main/java/**/*Rest.java` — 15 classes

## Already-tracked findings (verified still open)

| ID | Status |
|----|--------|
| APISIMP-ANOMALY-ACTION-PATH | queued (M, architectural, design doc needed) |
| APISIMP-CROSS-BULK-KIND-PATH | queued (M, architectural) |
| APISIMP-SQL-TIMESERIES-PATH | deferred (blocked on container kind-discriminator) |
| APISIMP-BUNDLE-TOMBSTONE-DELETE | queued (XS) |
| APISIMP-WIKI-TOMBSTONE-DELETE | queued (XS) |
| APISIMP-PAGEDFILES-SPRING-NAMING | queued (S) |
| APISIMP-BUNDLES-FILES-PAGESIZE-UNCLAMPED | queued (XS) |
| APISIMP-CONTAINERS-NAME-TO-Q | queued (S) |
| APISIMP-DO-NAME-TO-Q | queued (S) |
| APISIMP-PUBLICATIONS-KIND-410 | queued (XS) |
| APISIMP-ANOMALY-5TUPLE-ADD-UUID | queued (M) |
| APISIMP-USERGROUP-NUMERIC-PERMS-BLOCK | queued (S) |

## New findings

### F1 — `bundleAppId` path-param name in `BundleGroupsV2Rest` (XS)

`BundleGroupsV2Rest.java:82` `@Path("/v2/references/{bundleAppId}/groups")` uses
`bundleAppId` as the JAX-RS path-param variable. Every other resource mounted
under `/v2/references/{...}` uses `appId` (e.g. `GitReferenceActionsRest:92`
`@PathParam("appId") String appId`). The `bundle` qualifier is redundant because
the URL prefix `/v2/references/{...}/groups` already implies this is a
bundle-scoped reference. OpenAPI generates `bundleAppId` as the parameter name,
breaking the convention SDK consumers expect.

**Filed:** `APISIMP-BUNDLE-PATHPARAM` (XS)

### F2 — `templateAppId` path-param name in `MappingsMaterializeRest` (XS)

`MappingsMaterializeRest.java:99` `@Path("/v2/mappings/{templateAppId}/materialize")`
uses `templateAppId` as the path-param variable. The v2 convention is to use bare
`{appId}` when the resource type is already implied by the path prefix.
`/v2/mappings/` is already the mapping-template namespace; the `template` qualifier
is redundant and inconsistent with e.g. `ShepardTemplateRest` which uses `{appId}`.

**Filed:** `APISIMP-MAPPINGS-PATHPARAM` (XS)

### F3 — `GitReferenceRest` is an un-scheduled 410 tombstone (XS)

`plugins/git/.../GitReferenceRest.java` is a 2-method 410 Gone class. Both
prerequisite migrations are complete:
- CRUD: migrated by APISIMP-GIT-REF-KIND-UNIFY (✅ done, PLUGIN-PERKIND-CRUD-CLEANUP)
- Action sub-paths (preview, check-update): migrated by APISIMP-GIT-REF-PATH (✅ done)
- Location headers: added by APISIMP-TOMBSTONE-REMOVAL-WINDOW (fire-201, PR #2075)

Sister deletion rows `APISIMP-WIKI-TOMBSTONE-DELETE` and
`APISIMP-BUNDLE-TOMBSTONE-DELETE` are already queued (fire-500). This class
should follow the same pattern.

**Filed:** `APISIMP-GIT-TOMBSTONE-DELETE` (XS)

## Clean areas (no findings)

- **Pagination params**: `?page=` / `?pageSize=` consistent across all list endpoints.
- **Long `@PathParam`/`@QueryParam`**: all `Long` params are nanosecond epoch
  timestamps (`?start=`, `?end=` in `ContainersV2Rest`), not Neo4j IDs.
- **Error envelopes**: no empty-body 4xx in newly examined files (snapshot, bundle,
  AAS, git-actions, KIP, unhide, datacite, epic admin).
- **Plugin admin paths**: AAS admin at `/v2/admin/aas/...`, HDF at `/v2/admin/hdf`,
  video at `/v2/admin/video/...`, unhide at `/v2/admin/unhide`, datacite at
  `/v2/admin/minters/datacite`, epic at `/v2/admin/minters/epic` — all follow the
  established admin-namespace pattern; none expose numeric IDs.
- **AAS shells**: `AasShellsRest` at `/v2/aas/shells` — `aas` is in the plugin
  namespace allowlist (aidocs/191); path is correct.
- **KIP resolver**: `/v2/.well-known/kip/{suffix}` — well-known discovery path;
  correct.
- **VideoStreamReferenceV2Rest**: already deleted (PLUGIN-PERKIND-CRUD-CLEANUP +
  VIDEO-STREAMREF-PATH ✅ done). Not present in plugins/video.
- **`@Schema` annotations**: all IO classes in newly scanned areas carry `@Schema`.
- **`SpatialPromoteRest`**: `/v2/spatial/promote` uses `spatial` namespace — already
  tracked as SPATIAL-UNIFY-004 (architectural, needs design doc).

## Total new APISIMP rows filed this fire

3 new rows: APISIMP-BUNDLE-PATHPARAM, APISIMP-MAPPINGS-PATHPARAM,
APISIMP-GIT-TOMBSTONE-DELETE (all XS)

## Smallest dispatchable slice

`APISIMP-BUNDLE-PATHPARAM` (XS): rename `bundleAppId` → `appId` in
`BundleGroupsV2Rest.java` — 7 `@PathParam` + 1 `@Path` change, no logic change,
no migration needed.
