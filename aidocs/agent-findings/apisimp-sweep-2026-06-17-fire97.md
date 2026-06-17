---
stage: fragment
last-stage-change: 2026-06-17
---

# APISIMP surface sweep — fire-97 (2026-06-17)

**Scope:** full scan of `/v2` REST surface in `backend/src/main/java/de/dlr/shepard/v2/`
(57 REST resource files) + plugin `@Path` annotations (21 plugin REST files).

**Checklist:**
- [x] New `@Path(Constants.SHEPARD_API + ...)` additions — none found
- [x] Per-kind reference endpoints not yet unified under `?kind=` — all tracked/READY
- [x] Bespoke admin `*ConfigRest` not on generic registry — none; all features have `ConfigDescriptor`
- [x] Numeric Neo4j id leaks in `@PathParam`/`@QueryParam`/response bodies — 1 found (see APISIMP-PERMISSION-AUDIT-NEO4J-ID)
- [x] Inconsistent pagination param names — 1 found (see APISIMP-MAX-POINTS-PARAM-CASE)
- [x] Error envelopes — `ProblemJson` used consistently; APISIMP-FILECONTAINER-THUMBNAIL-BARE-STRING (PR #1973 READY) is the last outlier
- [x] Endpoints superseded by `POST /v2/shapes/render` — none new

## What I checked

### Admin config consolidation (V2CONV-A4)
All runtime-configurable features are on the generic `GET|PATCH /v2/admin/config/{feature}` registry:
`thermography`, `semantic`, `sql-timeseries`, `ror`, `jupyter` (backend descriptors) +
`aas`, `ai`, `minter-datacite`, `minter-epic`, `unhide`, `v1-compat`, `video` (plugin descriptors).

Bespoke admin REST classes (`DataciteAdminRest`, `EpicAdminRest`, `UnhideAdminRest`,
`HdfAdminRest`) retain only the credential-rotation and one-shot operational endpoints
(the "sister endpoints" pattern per CLAUDE.md). These are **not** a finding — they're
correctly structured alongside the generic config registry.

### Per-kind reference migration status
| Surface | Status |
|---|---|
| `POST /v2/files` (multipart create) | → 410 via PR #1966 READY |
| `GET/PATCH/DELETE /v2/files/{appId}*` CRUD | → 410 queued as APISIMP-FILE-PATH-RETIRE-2 (blocked on #1966) |
| `/v2/bundles/{appId}/...` | → B8 bundle-kind (blocked on #1966) |
| `GET /v2/data-objects/{doId}/git-references/{appId}/preview|check-update` | → 410 via PR #1967 READY |
| `POST /v2/data-objects/{doId}/video-stream-references` + download | → 410 via PR #1970 READY |
| `TimeseriesAnnotationRest` + `VideoAnnotationRest` JAX-RS collision | → Unified via PR #1971 READY |
| `?kind=structured-data` on `/v2/references` | → Slices 1+2 READY (#1974 #1975); slice 3 READY (#1976) |

### Frozen upstream surfaces (not findings)
- `SpatialDataReferenceRest` at `Constants.SHEPARD_API + "/collections/{collId}/dataObjects/{doId}/spatialDataReferences"` with `Long` path params — frozen upstream-byte-compat surface per CLAUDE.md exception (spatiotemporal plugin). Unchanged.
- `SqlTimeseriesRest` at `/v2/sql/timeseries` — P10c streaming SQL DSL query endpoint; not a bespoke bypass but a dedicated analytical surface. Clean.
- `CollectionContainersRest` at `/v2/collections/{collectionAppId}/referenced-containers` — cross-collection reference listing, distinct from owned-container list. Correct.

## New findings filed this sweep

### APISIMP-PERMISSION-AUDIT-NEO4J-ID (MINOR, XS)
- **File:** `backend/.../admin/io/PermissionAuditEntryIO.java:26`
- **Symptom:** `GET /v2/admin/permission-audit` returns `PermissionAuditEntryIO` containing `private Long neo4jNodeId`. The field leaks the Neo4j internal node id onto the v2 wire shape.
- **Context:** Intentionally added as a triage handle for pre-L2-migration rows where `appId` is null. The Javadoc says "exposed here as a triage handle when appId is null (pre-migration rows)." Endpoint is admin-only (`@RolesAllowed("instance-admin")`).
- **Impact:** Low — admin-only endpoint, intentional, documented. But violates the "never a numeric id on the wire" rule (CLAUDE.md) and will remain forever unless tracked.
- **AC:** After L2 migration completes and `GET /v2/admin/permission-audit` returns zero `neo4jNodeId != null` rows in production, remove `neo4jNodeId` from `PermissionAuditEntryIO`; callers triage via `appId`, `labels`, `name`. Gate: confirm L2 migration run clean (no null-appId rows) before shipping.

### APISIMP-MAX-POINTS-PARAM-CASE (MINOR, XS)
- **File:** `backend/.../containers/resources/ContainersV2Rest.java:660`; `frontend/composables/container/useFetchChannelPreview.ts:116`
- **Symptom:** `GET /v2/containers/{appId}/data` accepts `@QueryParam("max_points")` (snake_case). Every other v2 query parameter uses camelCase (`pageSize`, `windowSeconds`, `withBoundaryPoints`, `dataObjectAppId`, `shepardId`, …). Frontend composable `useFetchChannelPreview.ts:116` hard-codes `qs.set("max_points", ...)` to match.
- **Impact:** API param naming inconsistency; a caller building a generic v2 client must special-case this one snake_case param. Minor friction.
- **AC:** Rename `@QueryParam("max_points")` → `@QueryParam("maxPoints")` in `ContainersV2Rest.java:660` and update `useFetchChannelPreview.ts:116` in the same PR (coordinated, no deprecation shim needed — pre-production surface). Add 1 test assertion that the new name resolves. Update `@Operation` description string that references `?max_points=N`.

## Conclusion

Surface is in excellent shape post-convergence work. All admin configs are on the generic registry.
No new MAJOR or CRITICAL findings. Two MINOR XS cleanups filed:
`APISIMP-PERMISSION-AUDIT-NEO4J-ID` and `APISIMP-MAX-POINTS-PARAM-CASE`.
Primary blockers remain `APISIMP-FILE-PATH-RETIRE-2` and B8, both waiting on #1966 orchestrator merge.
