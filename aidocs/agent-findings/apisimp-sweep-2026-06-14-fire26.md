---
stage: deployed
last-stage-change: 2026-06-14
---
# APISIMP Sweep — 2026-06-14 fire-26

## Scan scope

- **v2 REST classes**: all 96 `*Rest.java` files under
  `backend/src/main/java/de/dlr/shepard/v2/`
- **Plugin REST classes**: all 21 `*Rest.java` files under `plugins/`
- **v2 IO classes**: all `*/io/*.java` under both trees
- **Checks run**: (1) non-standard pagination `@QueryParam("size")`/`@QueryParam("page-size")`;
  (2) numeric `@PathParam` (`Long`/`int`) in v2 endpoints; (3) `long id` / `Long id`
  fields in v2 IO classes; (4) `@Path(Constants.SHEPARD_API + ...)` in v2/plugin
  packages; (5) bespoke `*ConfigRest` not on the generic registry; (6) v2 response
  bodies with both numeric `id` and `appId` fields; (7) `@Deprecated` fields in v2 IO

---

## Findings

### Finding 1 — MINOR: `ContainerRefIO.containerId` and `ContainerRefIO.referenceId` are stale numeric leaks

**File**: `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/ContainerRefIO.java:38–42`

**Severity**: MINOR

`ContainerRefIO` is emitted as part of `DataObjectDetailV2IO.containers.{timeseries,files,structuredData}`
on every `GET /v2/collections/{appId}/data-objects/{appId}` response. It contains two
numeric Neo4j OGM ids on the wire:

- `containerId` (line 39) — claimed in Javadoc as "Used by
  `/v2/timeseries-containers/{containerId}/stats`". That endpoint was migrated to
  `{containerAppId}` in `APISIMP-TSCONT-APPID-KEY` (shipped 2026-06-11). The
  Javadoc claim is now stale and the containerId field has no remaining legitimate
  v2 caller. The v1 `/shepard/api/timeseriesContainers/{containerId}/available`
  path it also mentions is a frozen v1 compat surface — a v2 response field should
  not exist to serve a v1 endpoint.
- `referenceId` (line 42) — claimed as "upstream-compat. Used by
  `/shepard/api/.../timeseriesReferences/{referenceId}/payload`". Again a v1
  surface; the field itself is emitted on the v2 response wire unnecessarily.
  `referenceAppId` (line 49, nullable) is the appId-keyed equivalent.

The `containerAppId` and `referenceAppId` fields (also present) are the correct
v2-native addresses. No frontend component was found reading `containerId` or
`referenceId` from the `DataObjectDetailV2IO.containers` wire payload — the
generated backend client used by the frontend does not carry a `ContainerRef` type
(the typed `containers` field arrives as an untyped extension of `DataObjectIO`).

**Fix**: Drop `containerId` and `referenceId` from `ContainerRefIO`. The fields
have no v2 callers; the v1 payload endpoints they serve do not need a v2 response
field to function. Add `@JsonIgnore` on both or remove the fields entirely from the
class. Update the constructor signature and the 3 call sites in
`DataObjectV2Rest.buildContainersFromCypher()` (lines 907–952) and 3 more in
`DataObjectDetailV2IO` (lines 182–204).

**AC**: No `containerId` or `referenceId` field in any
`GET /v2/…/data-objects/{appId}` response body; `mvn verify -pl backend` green;
FE typecheck green.

**Proposed row ID**: `APISIMP-CONTAINERREF-DROP-NUMERIC`

---

### Finding 2 — MINOR: `WatchIO.containerOgmId` numeric field in `/v2/` watched-containers response

**File**: `backend/src/main/java/de/dlr/shepard/v2/watches/io/WatchIO.java:37`

**Severity**: MINOR

`WatchIO` is emitted by `GET /v2/collections/{appId}/watched-containers`. The record
carries `containerOgmId Long` (line 37) — a Neo4j OGM long id. The field's Javadoc
(line 30–35) explains the dependency: container detail pages
(`/containers/timeseries/{containerOgmId}`, etc.) still accept a numeric route
segment, so the frontend needs `containerOgmId` to build the "open container" link.

`WatchedContainersPanel.vue:102–103` reads `w.containerOgmId` directly:
```
v-if="w.containerAvailability === 'available' && w.containerOgmId != null"
:to="containerKindRoutes[w.containerKind] + w.containerOgmId"
```

`useWatchedContainers.ts:25` declares `containerOgmId?: number | null`.

`CONTAINER-V2-ROUTE` (shipped 2026-06-12) introduced dual-path container
accessors that accept appId, and `V2-SWEEP-003-2` flipped `ContainerRouteParams.containerId`
from `number` to `string`. However, `WatchedContainersPanel.vue` was not updated
in that pass — it still routes via the numeric OGM id.

**Fix**: (a) In `WatchedContainersPanel.vue:103` replace `w.containerOgmId` with
`w.containerAppId` (already present in `WatchIO` as `containerAppId: String`);
(b) Drop `containerOgmId` from `WatchIO.java` (field + builder helper at line 58);
(c) Remove `containerOgmId` from `useWatchedContainers.ts:25`.

Requires that container detail pages accept appId in the route, which is true for
timeseries/file/structured-data post CONTAINER-V2-ROUTE. Spatial is still numeric
(APISIMP-NUMERIC-ID-BATCH-2 deferred); `WatchIO.containerOgmId` can be kept for
spatial containers only with a `@JsonInclude(NON_NULL)` guard, or left as null for
spatial watches until SPATIAL-V6-003 ships.

**AC**: `WatchedContainersPanel.vue` navigates to container detail via `containerAppId`;
no `containerOgmId` field on the wire for timeseries/file/structured-data watches;
FE typecheck green; `mvn verify -pl backend` green.

**Proposed row ID**: `APISIMP-WATCH-DROP-OGMID`

---

## Clean areas

All other check categories came back clean for this sweep:

| Check | Result |
|---|---|
| `@QueryParam("size")` — 8 v2 pagination sites | Already tracked: `APISIMP-PAGINATION-UNIFY-RECREATE` (PR #1887 open) |
| `@QueryParam("page-size")` — `UnhideFeedRest.java:131` | Same as above — covered by PR #1887 |
| `@QueryParam("size")` in `ThumbnailRest.java:69` | Not a pagination param — image pixel dimension (64/200/400px); correctly excluded |
| `@Path(Constants.SHEPARD_API + ...)` in v2/plugin packages | Only `SpatialDataPointRest.java:51` — already tracked as `APISIMP-V1-PATH-RESIDUAL-1` (deferred, frozen upstream-compat) |
| Numeric `@PathParam` in v2/plugin packages | Only `SpatialDataReferenceRest` + `SpatialDataPointRest` — already tracked as `APISIMP-NUMERIC-ID-BATCH-2` (deferred) |
| `private long id` / `private Long id` in v2 IO classes | `PermissionAuditEntryIO.java:21` — already tracked as `APISIMP-PERMAUDIT-NUMERIC-ID` (queued). `ContainerRefIO.java` — new (Finding 1). `WatchIO.java` uses `Long containerOgmId` — new (Finding 2). |
| Bespoke `*ConfigRest` outside generic registry | None. All config endpoints go through `AdminConfigRest.java` → `ConfigDescriptorRegistry` at `/v2/admin/config/{feature}`. `AasRegistrationAdminRest` at `/v2/admin/aas/registrations` is a registration-sync endpoint, not a bespoke config registry — correctly out of scope. |
| v2 response bodies with both numeric `id` AND `appId` | `BasicEntityV2IO` suppresses `id` via `@JsonIgnoreProperties({"id"})` globally. `DataObjectDetailV2IO` has `@JsonIgnoreProperties({"id"})`. `CollectionV2IO` extends `BasicEntityV2IO`. No remaining v2 base IO class exposes numeric `id` alongside `appId`. The two `ContainerRefIO` fields (Finding 1) are named `containerId`/`referenceId`, not `id`, so they bypass the `@JsonIgnoreProperties({"id"})` guard — hence the separate finding. |
| `@Deprecated` fields in v2 IO classes | None found. The deprecated fire went through earlier batches. |
| `AasRegistrationAdminRest.java` path | `@Path("/v2/admin/aas/registrations")` — correct v2 path, not a `Constants.SHEPARD_API` violation |
