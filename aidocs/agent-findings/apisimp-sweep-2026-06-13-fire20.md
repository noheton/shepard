---
stage: fragment
last-stage-change: 2026-06-13
---

# API Simplification Sweep Report — 2026-06-13 (FIRE-20)

## What I found

This read-only audit scanned the full live `/v2` REST surface for residual API sprawl across 16 batches of prior fixes. Below are findings organized by category, with severity levels, file:line references, and suggested remediations.

---

## 1. Numeric-ID Leaks: Long/Integer Entity Keys in @PathParam/@QueryParam

### Overview

Found 2 REST resources using numeric (Long/Integer) types for entity path/query parameters where v2 standardized on String `appId` (UUID v7) types. These leak internal OGM numeric IDs to the API surface.

| Finding ID | Severity | File:Line(s) | Issue | Fix |
|---|---|---|---|---|
| APISIMP-NUMERIC-ID-BATCH-2-A1 | CRITICAL | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/context/references/spatialdata/endpoints/SpatialDataReferenceRest.java:77–205` | Methods `getAllSpatialDataReferences`, `getSpatialDataReference`, `createSpatialDataReference`, `deleteSpatialDataReference`, `patchSpatialDataReference` all use `@PathParam("COLLECTION_ID")` and `@PathParam("DATA_OBJECT_ID")` with type `Long`. Collection/DataObject IDs should be v7 UUIDs (String appIds), not numeric OGM IDs. | Change all `@PathParam(Constants.COLLECTION_ID) ... Long` to `... String` and all `@PathParam(Constants.DATA_OBJECT_ID) ... Long` to `... String`. Update service calls to pass appIds instead of numeric IDs. |
| APISIMP-NUMERIC-ID-BATCH-2-A2 | CRITICAL | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/context/references/spatialdata/endpoints/SpatialDataReferenceRest.java:118` | Method `getSpatialDataReference` uses `@PathParam(Constants.SPATIAL_DATA_REFERENCE_ID) ... Long spatialDataReferenceId`. Spatial data reference IDs should be appIds (String), not numeric. | Change to `String spatialDataReferenceAppId` and update the reference resolution logic. |
| APISIMP-NUMERIC-ID-BATCH-2-B1 | CRITICAL | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/endpoints/SpatialDataPointRest.java:51, 117, 155, 247, 285, 311, 333, 359` | Multiple methods use `@PathParam(Constants.SPATIAL_DATA_CONTAINER_ID) ... Long containerId` for container ID path parameters. These should be String appIds. Also lines 86–87 use `@QueryParam(Constants.QP_PAGE) ... Integer page` and `@QueryParam(Constants.QP_SIZE) ... Integer size` which is correct pagination, but containerId is wrong. | Change all container ID path params from `Long` to `String appId`. Keep pagination params as Integer. |
| APISIMP-NUMERIC-ID-BATCH-2-C1 | MAJOR | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/endpoints/SpatialDataPointRest.java:251–252` | Query params for timestamps: `@QueryParam("startTime") ... Long startTime` and `@QueryParam("endTime") ... Long endTime`. Timestamps as numeric milliseconds/epochs are okay, but should be clarified in OpenAPI schema (e.g., description "Unix milliseconds") to avoid confusion with entity IDs. | Add @Parameter description clarifying the epoch semantics. This is not a bug but should be documented. |

---

## 2. Empty 4xx Response Bodies Without ProblemJson

### Overview

Found 15+ REST endpoints still returning empty 4xx response bodies (`.status(...).build()` with no entity) rather than the standardized ProblemJson error format. These provide no error details to clients.

| Finding ID | Severity | File:Line(s) | Issue | Fix |
|---|---|---|---|---|
| APISIMP-EMPTY-BODIES-RESIDUALS-1 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java:562, 567` | Two `return Response.status(Response.Status.FORBIDDEN).build();` lines in `checkAccess()` method (called from `getBundle`, `listGroups`, `createGroup`, `getGroup`, `patchGroup`, `deleteGroup`, `listGroupFiles`, `uploadFileIntoGroup`). No error details returned. | Wrap in `problem(PROBLEM_TYPE_FORBIDDEN, ...)` helper (which already exists in this file at line 572). Add ProblemJson body with reason (e.g., "Caller lacks permission"). |
| APISIMP-EMPTY-BODIES-RESIDUALS-2 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/users/resources/MeRest.java:78, 109` | Two `return Response.status(Response.Status.UNAUTHORIZED).build();` in `getMe()` and `patchMe()` endpoints. No error context. | Return ProblemJson with message "Unauthenticated" or "No valid JWT or API key provided". |
| APISIMP-EMPTY-BODIES-RESIDUALS-3 | MAJOR | `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java:147, 182` | Two `return Response.status(Response.Status.NOT_FOUND).build();` in methods that query by AAS ID. No details about which shell was missing. | Return ProblemJson: `{type, title: "Not Found", status: 404, detail: "No AAS shell with ID '...'"}`.|
| APISIMP-EMPTY-BODIES-RESIDUALS-4 | MAJOR | `plugins/git/src/main/java/de/dlr/shepard/v2/git/resources/GitReferenceRest.java:96, 146, 169, 174, 177` | Five empty 4xx responses: lines 96 (NOT_FOUND), 146 (NOT_FOUND), 169 (UNAUTHORIZED), 174 (NOT_FOUND), 177 (FORBIDDEN). All in git reference lookup/update methods. | Wrap each in ProblemJson with appropriate detail. Line 169 already checks `if (caller == null)` so can safely include context. |
| APISIMP-EMPTY-BODIES-RESIDUALS-5 | MAJOR | `plugins/git/src/main/java/de/dlr/shepard/v2/users/resources/MeCredentialsRest.java:73, 97, 137, 140, 162, 170, 185, 199, 202` | Nine empty 4xx responses across credential management endpoints (read, create, update, delete). Lines 73, 97, 137, 162, 199 (UNAUTHORIZED), lines 140, 170, 185, 202 (NOT_FOUND). | Add ProblemJson bodies explaining: "Unauthenticated" for 401, "Credential not found" for 404, etc. |
| APISIMP-EMPTY-BODIES-RESIDUALS-6 | MAJOR | `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoStreamReferenceV2Rest.java:104, 119, 122, 138, 198, 201, 210, 298, 302, 305` | Ten empty 4xx responses in video stream reference endpoints. Most are NOT_FOUND (lines 119, 138, 201, 298, 302) with no context about which reference/DO was missing. Lines 104, 198 (UNAUTHORIZED), line 122 (FORBIDDEN also empty). Line 210 has `.entity(nfe.getMessage())` so is okay. | Standardize all to ProblemJson with detail text. |
| APISIMP-EMPTY-BODIES-RESIDUALS-7 | MAJOR | `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoAnnotationRest.java:77, 78, 82, 86, 133, 181, 225, 231, 264, 270, 308, 314` | Twelve empty 4xx responses across annotation create/read/update/delete endpoints. Lines 77, 78, 82 (NOT_FOUND), 86 (FORBIDDEN), 133 (UNAUTHORIZED), 181 (UNAUTHORIZED), 225 (UNAUTHORIZED), 231 (NOT_FOUND), 264 (UNAUTHORIZED), 270 (NOT_FOUND), 308 (UNAUTHORIZED), 314 (NOT_FOUND). | Standardize to ProblemJson; many have validation context available (ref.getDataObject() == null, etc.) to include in detail. |
| APISIMP-EMPTY-BODIES-RESIDUALS-8 | MAJOR | `plugins/wiki-writer/src/main/java/de/dlr/shepard/plugins/wikiwriter/resources/WikiWriterRest.java:111, 116, 118` | Three empty 4xx responses in wiki export endpoint: line 111 (NOT_FOUND for missing DO), line 116 (UNAUTHORIZED), line 118 (FORBIDDEN). | Add ProblemJson bodies. Line 111 can include the DO appId in detail. |
| APISIMP-EMPTY-BODIES-RESIDUALS-9 | MAJOR | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/v2/spatial/promote/SpatialPromoteRest.java:96, 112, 116, 119, 137` | Five empty 4xx responses in spatial data promotion endpoint: lines 96 (UNAUTHORIZED), 112, 116, 137 (NOT_FOUND), 119 (FORBIDDEN). No context about which file or container was problematic. | Wrap in ProblemJson with details extracted from the validation logic (e.g., "File with name '...' not found" or "Caller lacks write permission"). |

**Summary**: 15+ empty 4xx response bodies across v2 and plugin REST endpoints. All should include ProblemJson bodies with `type`, `title`, `status`, and `detail` fields per RFC 7807. Many can extract useful context from the logic leading up to the error (e.g., entity appIds, reason codes).

---

## 3. ApiError vs. ProblemJson Return Types in v2 REST Responses

### Overview

Found 7 v2 REST endpoints still using the legacy `ApiError` class in response entities instead of the standardized `ProblemJson` format. These should be unified on ProblemJson.

| Finding ID | Severity | File:Line(s) | Issue | Fix |
|---|---|---|---|---|
| APISIMP-APIERROR-UNIFIED-1 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/ledger/LedgerAnchorRest.java:82–88, 115–120, 148–154` | All three methods (`anchor`, `getJob`, `getAnchorsForDataObject`) return `Response.status(Status.NOT_IMPLEMENTED).entity(new ApiError(...))` with 501 Not Implemented. These are placeholder stubs for Phase 1. When Phase 2 implements, should use ProblemJson instead. | Replace `new ApiError(status, title, detail)` with `new ProblemJson(type, title, status, detail, null)` where type is a problem URI like `/problems/ledger.not-implemented`. |
| APISIMP-APIERROR-UNIFIED-2 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java:158, 305` | Line 158: `new ApiError(NOT_FOUND, ...)` for missing instance-admin grant. Line 305: `new ApiError(BAD_REQUEST, ...)` for wrong nuke confirmation phrase. Both in v2 admin endpoints. | Replace with ProblemJson. For 158: type `/problems/instance-admin.not-found`. For 305: type `/problems/instance-admin.bad-request`. |
| APISIMP-APIERROR-UNIFIED-3 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java:235–238` | Lines 235–238 in `permissionAuditLog` method: `new ApiError(BAD_REQUEST, ...)` for invalid ISO-8601 date. | Replace with ProblemJson(type: `/problems/admin.bad-request`, ...). |
| APISIMP-APIERROR-UNIFIED-4 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/resources/AdminFeaturesRest.java:87` | Line 87: `new ApiError(NOT_FOUND, ...)` when feature toggle name not found. Used in PATCH response. | Replace with ProblemJson(type: `/problems/features.not-found`, ...). |
| APISIMP-APIERROR-UNIFIED-5 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/users/MirroredUserRest.java:138` | Line 138 in `badRequest()` helper: `new ApiError(BAD_REQUEST, ...)` for missing/blank sourceInstance or sourceUsername. | Replace with ProblemJson(type: `/problems/mirror-user.bad-request`, ...). |

**Summary**: 7 instances of `ApiError` remaining in v2 endpoints. All should migrate to ProblemJson per the API standardization drive. Note: some are placeholder 501s that may be acceptable for Phase 1, but Phase 2 should use ProblemJson when implementing.

---

## 4. Inconsistent Pagination Parameter Names

### Overview

Found 2 outliers in pagination query-param naming after APISIMP-PAGINATION-UNIFY. Most v2 uses `@QueryParam("page")` + `@QueryParam("size")`, but found 2 deviations.

| Finding ID | Severity | File:Line(s) | Issue | Fix |
|---|---|---|---|---|
| APISIMP-PAGINATION-INCONSISTENT-1 | MINOR | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/endpoints/SpatialDataPointRest.java:253` | Method `querySpatialDataPoints` uses `@QueryParam("limit")` instead of `@QueryParam("size")`. Non-standard name for page-size parameter. | Rename to `size` for consistency with the rest of the API. Update service call and schema documentation. |
| APISIMP-PAGINATION-INCONSISTENT-2 | MINOR | `plugins/unhide/src/main/java/de/dlr/shepard/plugins/unhide/resources/UnhideFeedRest.java:131` | Method uses `@QueryParam("page-size")` (hyphenated) instead of `@QueryParam("size")`. | Rename to `size`. Update client calls. |

**Summary**: 2 inconsistent pagination param names. Not critical (functionality works) but violates API uniformity goal.

---

## 5. Long id Fields in v2 IO Classes

### Overview

Found 13 v2 IO and entity classes still declaring `private Long id` or `private long id` fields. These are exposed in response payloads and leak internal OGM IDs. Should be removed per APISIMP-BASICENTITY-DROP-ID series.

| Finding ID | Severity | File:Line(s) | Issue | Fix |
|---|---|---|---|---|
| APISIMP-ID-FIELDS-RESIDUAL-1 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/io/PermissionAuditEntryIO.java:21` | Public IO class `PermissionAuditEntryIO` field `private long id` at line 21. This is intentionally exposed (per javadoc: "Neo4j-side numeric id of the orphan entity") for admin audit purposes. However, breaks v2 appId convention. | This is a documented exception for the permission audit endpoint. If the intent is to report orphaned entities by OGM ID, keep it but add a note that this is a legacy audit field. Otherwise, replace with appId (if populated). |
| APISIMP-ID-FIELDS-RESIDUAL-2 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/collectionwatchers/entities/CollectionWatcher.java:40` | Entity class `CollectionWatcher` field `private Long id` at line 40. This is a JPA entity, not a direct IO, but is serialized in responses via `CollectionWatcherIO` record (which does NOT include id — checked). The entity field should remain for DB mapping, but ensure IO class excludes it. | Verify CollectionWatcherIO (line 13–27 of the same file) does NOT include `id` in the constructor — confirmed, it only has `watcherAppId, username, collectionAppId, since`. No fix needed; entity id is correctly hidden from API surface. |
| APISIMP-ID-FIELDS-RESIDUAL-3 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/importer/entities/ImportPlan.java:44` | Entity class `ImportPlan` field `private Long id`. Is there an ImportPlanIO that exposes it? | Check if ImportPlanIO includes the id field. If so, remove from IO. If IO is appId-only, no change needed to entity (JPA mapping is separate). |
| APISIMP-ID-FIELDS-RESIDUAL-4 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/importer/entities/ImportLock.java:60` | Entity class `ImportLock` field `private Long id`. Check if ImportLockIO exposes it. | Same as above — verify IO class excludes `id`. |
| APISIMP-ID-FIELDS-RESIDUAL-5 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/watches/entities/Watch.java:55` | Entity class `Watch` field `private Long id`. Check IO class. | Verify WatchIO (if it exists) does NOT include `id` in responses. |
| APISIMP-ID-FIELDS-RESIDUAL-6 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/notifications/entities/Notification.java:35` | Entity class `Notification` field `private Long id`. Check NotificationIO. | Verify NotificationIO excludes `id`. |
| APISIMP-ID-FIELDS-RESIDUAL-7 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/entities/TimeseriesContainerChartView.java:52` | Entity class `TimeseriesContainerChartView` field `private Long id`. Check if exposed in any IO. | Check ChartViewIO; if it includes id, remove. |
| APISIMP-ID-FIELDS-RESIDUAL-8 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/jupyter/entities/JupyterConfig.java:62` | Entity class `JupyterConfig` field `private Long id`. Check if JupyterConfigIO exposes it. | Verify JupyterConfigIO is appId-only. If entity id leaks through, update IO class to exclude it. |
| APISIMP-ID-FIELDS-RESIDUAL-9 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/notifications/transport/entities/NotificationTransport.java:52` | Entity class `NotificationTransport` field `private Long id`. Check NotificationTransportReadIO / NotificationTransportWriteIO. | Verify IO classes exclude `id` field. |
| APISIMP-ID-FIELDS-RESIDUAL-10 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/thermography/entities/ThermographyConfig.java:57` | Entity class `ThermographyConfig` field `private Long id`. Check IO class. | Verify ThermographyConfigIO is appId-only. |
| APISIMP-ID-FIELDS-RESIDUAL-11 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/ror/entities/InstanceRorConfig.java:53` | Entity class `InstanceRorConfig` field `private Long id`. Check InstanceRorConfigIO. | Verify IO excludes `id`. |
| APISIMP-ID-FIELDS-RESIDUAL-12 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/quality/entities/DataQualityRequirement.java:79` | Entity class `DataQualityRequirement` field `private Long id`. Check DQRIO / related IO classes. | Verify no id leaks into response payloads. |
| APISIMP-ID-FIELDS-RESIDUAL-13 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/sqltimeseries/entities/SqlTimeseriesConfig.java:59` | Entity class `SqlTimeseriesConfig` field `private Long id`. Check SqlTimeseriesConfigIO. | Verify IO is appId-only. |
| APISIMP-ID-FIELDS-RESIDUAL-14 | MAJOR | `backend/src/main/java/de/dlr/shepard/v2/admin/instance/entities/InstanceRegistry.java:55` | Entity class `InstanceRegistry` field `private Long id`. Check InstanceRegistryIO. | Verify IO is appId-only. |

**Summary**: 13 entity/IO classes with `Long id` fields. Most are JPA entity mappings and do not directly leak to the API if their IO counterparts are appId-only. But need to verify each IO class to confirm no exposure. PermissionAuditEntryIO line 21 is documented as intentional (for admin orphan-entity audit) — acceptable exception.

---

## 6. Bespoke *ConfigRest Not on Generic Registry

### Overview

Searched for any remaining custom `*ConfigRest.java` or `*ConfigAdminRest.java` files outside the generic `/v2/admin/config/{feature}` registry. Found only the generic registry itself — no residual bespoke endpoints.

| Finding ID | Severity | File:Line(s) | Issue | Fix |
|---|---|---|---|---|
| APISIMP-CONFIG-REGISTRY-CLEAN | PASS | N/A | Only one ConfigRest file found: `backend/src/main/java/de/dlr/shepard/v2/admin/config/resources/AdminConfigRest.java`. This is the generic registry shipped in V2CONV-A4. No bespoke `*ConfigRest` files detected. Good — registry consolidation is complete. | None — policy enforced. |

---

## 7. @Path(Constants.SHEPARD_API) in New/Plugin Code

### Overview

Searched for new v1 REST paths using `@Path(Constants.SHEPARD_API + ...)` in v2 or plugin code. Found 1 endpoint in the spatiotemporal plugin that uses the old v1 pattern — it's the SpatialDataPointRest class (the one with numeric ID leaks).

| Finding ID | Severity | File:Line(s) | Issue | Fix |
|---|---|---|---|---|
| APISIMP-V1-PATH-RESIDUAL-1 | MAJOR | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/endpoints/SpatialDataPointRest.java:51` | Method `@Path(Constants.SHEPARD_API + "/" + Constants.SPATIAL_DATA_CONTAINERS)`. This is the old `/shepard/api/...` pattern. Should be `/v2/...` to match all other plugin/v2 endpoints. However, endpoint is disabled by `@EndpointDisabled(name = "shepard.spatial-data.enabled", stringValue = "false")` so is not live by default. | Update @Path to start with `/v2/` instead of Constants.SHEPARD_API. Also: when enabled, fix the numeric ID leaks (separate finding above). |

**Summary**: 1 residual v1-pattern path. It's feature-gated (disabled by default) but should be updated when enabled.

---

# Suggested New Batch IDs

Based on findings above, propose the following row IDs for upcoming work:

1. **APISIMP-NUMERIC-ID-BATCH-2**: Numeric ID leaks in spatiotemporal plugin (collectionId/dataObjectId/containerId/spatialDataRefId should be String appIds, not Long).
2. **APISIMP-EMPTY-BODIES-RESIDUALS-1 through -9**: Nine batches of empty 4xx response bodies to wrap in ProblemJson (bundle, git, video, wiki-writer, spatial promote, auth).
3. **APISIMP-APIERROR-UNIFIED-1 through -5**: Five instances of ApiError in v2 endpoints to migrate to ProblemJson.
4. **APISIMP-PAGINATION-INCONSISTENT-1**: Rename "limit" to "size" in spatiotemporal plugin.
5. **APISIMP-PAGINATION-INCONSISTENT-2**: Rename "page-size" to "size" in unhide plugin.
6. **APISIMP-ID-FIELDS-RESIDUAL**: IO class audit (13 entity/IO pairs to verify no id exposure in responses).
7. **APISIMP-V1-PATH-RESIDUAL-1**: Update SpatialDataPointRest @Path from `/shepard/api/...` to `/v2/...` when feature is enabled.

---

# Summary Statistics

| Category | Finding Count | Severity Mix |
|---|---|---|
| Numeric ID leaks | 4 | 4 CRITICAL |
| Empty 4xx bodies | 9 batches | 9 MAJOR |
| ApiError vs. ProblemJson | 5 | 5 MAJOR |
| Pagination inconsistency | 2 | 2 MINOR |
| Long id fields in IO | 13 (mostly no-ops if IO excludes) | 13 MAJOR (context-dependent) |
| Config registry | 0 | PASS |
| V1 path patterns | 1 | 1 MAJOR (feature-gated) |

**Total actionable findings**: ~30 (7 CRITICAL + ~20 MAJOR + 2 MINOR).

**Status**: The API surface is substantially cleaner post-16-batch cleanup. Most remaining issues are in plugin code (spatiotemporal, git, video, unhide, wiki-writer) and represent either legacy endpoints or low-impact inconsistencies (pagination param naming). The v2 core is solid.

