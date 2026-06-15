---
stage: deployed
last-stage-change: 2026-06-15
---

# APISIMP Sweep — fire-55 (2026-06-15)

**Scope:** post–CONT-NS-COLLAPSE pass. The CONT-NS-COLLAPSE series (PRs #1944–#1951,
slices 1–6b) collapsed all per-kind container sub-namespaces onto `GET|DELETE
/v2/containers/{appId}/*`. This sweep checks what residual sprawl remains after that
series lands on `main` (assuming #1951 merges).

---

## Findings

### Finding 1 — APISIMP-CROSS-TS-BULK-PATH (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/CrossDoBulkDataRest.java`
**Path:** `POST /v2/timeseries/cross-data-object-bulk-data`

After the CONT-NS-COLLAPSE series, `/v2/timeseries/` retains exactly ONE endpoint.
Per [191 §2 Tier 1](../platform/191-v2-surface-convergence.md), domain sub-namespaces
dissolve into the generic core surface. A lone endpoint in a domain namespace is dead
weight: it forces callers to know the domain prefix (`timeseries`) for an operation
that takes `dataObjectAppIds[]` (appIds, not timeseries-specific concepts).

**Better path:** `POST /v2/data-objects/cross-timeseries-bulk` — the `dataObjectAppIds`
body field already signals the resource type; the domain namespace adds nothing.

**Frontend caller:** `frontend/composables/containers/useCrossDoBulkData.ts:65`
```typescript
const url = `${v2BaseUrl()}/v2/timeseries/cross-data-object-bulk-data`;
```
One-line update → `${v2BaseUrl()}/v2/data-objects/cross-timeseries-bulk`.

**AC:** `POST /v2/timeseries/cross-data-object-bulk-data` removed; `POST
/v2/data-objects/cross-timeseries-bulk` identical semantics; `useCrossDoBulkData.ts`
URL updated; `mvn verify -pl backend` + `npm run typecheck` green.

**Note:** The `/v2/timeseries/` namespace becomes empty after this change. No other
class uses it (confirmed by path scan).

---

### Finding 2 — APISIMP-VIDEO-ANNOT-PATH (S)

**File:** `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoAnnotationRest.java`
**Path:** `GET|POST|GET|PATCH|DELETE /v2/data-objects/{dataObjectAppId}/video-stream-references/{refAppId}/annotations[/{annotationAppId}]`

`TimeseriesAnnotationRest` was already migrated to `GET|POST /v2/references/{appId}/annotations`
(the generic reference path). `VideoAnnotationRest` still carries the old per-kind,
per-DO path pattern. This inconsistency forces callers to build different URL shapes
for semantically identical operations (list/create/get/update/delete annotations on a
reference) depending on the reference kind.

The `dataObjectAppId` in the path is used only for permission validation
(`PermissionsService.hasReadPermissionForDataObject`). Since the reference `appId`
resolves to a unique entity that itself has a parent DO, the DO lookup can be made
from the reference entity — the same approach `TimeseriesAnnotationRest` uses.

**Better path:** `GET|POST /v2/references/{refAppId}/annotations[/{annotationAppId}]`
(consistent with `TimeseriesAnnotationRest`).

**Frontend caller:** `frontend/composables/context/useFetchVideoAnnotations.ts:51`
```typescript
`/video-stream-references/${encodeURIComponent(refAppId)}/annotations`;
```
Update to `/v2/references/${encodeURIComponent(refAppId)}/annotations`.

**AC:** Old per-kind annotation paths removed; `/v2/references/{refAppId}/annotations`
CRUD works for video references; `VideoAnnotationRest.java` permission check resolves
DO from ref entity (not path param); `useFetchVideoAnnotations.ts` URL updated;
`mvn verify` (full plugins) + FE typecheck green.

**Size:** S (backend path restructure + permission resolution change + FE URL update).

---

## Not-a-finding notes

| Area | Verdict |
|---|---|
| `GitReferenceRest` at `/v2/data-objects/{do}/git-references` | Only 2 kind-specific actions remain (`/{appId}/preview`, `/{appId}/check-update`). CRUD already on unified `/v2/references`. JUSTIFIED exception. |
| `VideoStreamReferenceV2Rest` at `/v2/data-objects/{do}/video-stream-references` | Only `POST /upload` (multipart) + `GET /{appId}/download` remain. JUSTIFIED exceptions — can't be generic. |
| `SpatialDataReferenceRest` using `@Path(Constants.SHEPARD_API + ...)` | Frozen upstream v1 compat surface. Deferred (APISIMP-V1-PATH-RESIDUAL-1). |
| `TimeseriesChannelV2IO.containerId` | Already `@Schema(deprecated=true)` per APISIMP-TSCHANNEL-CONTAINER-ID. Gated on TS-IDb/c migration. |
| Plugin admin paths (`/v2/admin/unhide`, `/v2/admin/minters/*`, etc.) | Have justified kind-specific operations (credential rotation, ACL rebuild). Not bespoke config endpoints. |
| `FileReferenceV2Rest` at `/v2/files` | Known pre-production target for V2CONV-B6 (unified `/v2/references?kind=file`). Pre-existing, not new. |
| Pagination params | Consistent `page`/`pageSize` across all v2 list endpoints. |
| Error envelopes | Zero bare-string or empty-body 4xx found in v2 or plugins (after fire-35 series). |
| `https://shepard.dlr.de/problems/` absolute URIs | Zero occurrences in plugins (after APISIMP-PLUGIN-ABSOLUTE-PROBLEM-URIS, PR #1919). |

---

## New rows filed in `aidocs/16`

| Row | Size | Status |
|---|---|---|
| `APISIMP-CROSS-TS-BULK-PATH` | XS | queued — dispatch next fire |
| `APISIMP-VIDEO-ANNOT-PATH` | S | queued |
