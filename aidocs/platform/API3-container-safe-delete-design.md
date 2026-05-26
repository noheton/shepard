---
stage: concept
last-stage-change: 2026-05-26
---

# API3 — Safe-Delete Design for Container Endpoints

**Scope:** `DELETE /v2/{timeseries,file,structured-data}-containers/{id}`

**Problem:** All three container-kind DELETE endpoints share an identical pattern with no shared superclass, no cascade semantics specification, no force-delete path, and no SM1 orphan-notification hook. Before implementing, they need a single design that covers all three.

## Proposed design

### Soft-delete first
Default `DELETE` marks the container `deleted=true`; does not purge payloads from storage. SM1 (StorageProvider SPI) receives an orphan-notification event.

### Force-delete
`DELETE .../containers/{id}?force=true` purges payloads from storage immediately. Requires `instance-admin` role OR container write permission + `force` query param.

### Cascade semantics
Deleting a container does NOT auto-delete DataObject references pointing at it. Those references become "stale" (a future UX-REVERT-MUTATIONS / stale-ref integrity feature surfaces them). The container is not deleted if `force=false` and it has active references (400 Conflict response).

### appId vs Long migration
Until the TS-IDb appId migration ships, the endpoint accepts both `{appId}` (UUID) and `{id}` (Long), same as the other v2 endpoints. Post-migration, Long-id support is deprecated with a sunset warning header.

### SM1 hook
`StorageProvider.onContainerDeleted(containerId, payloadOids)` — SM1 SPI receives notification; default implementation schedules graceful purge after `shepard.storage.orphan.grace-period` (default: infinite per INTEGRITY rule).

## Affected endpoints
- `DELETE /v2/timeseries-containers/{id}`
- `DELETE /v2/file-containers/{id}`
- `DELETE /v2/structured-data-containers/{id}`

## Implementation note
All three share a nearly identical REST handler pattern. Once this design is approved, a single abstract `ContainerDeleteHandler` can cover all three.

## References
- `aidocs/agent-findings/api-scrutinizer.md §"1 Endpoint That Needs a Design Doc"`
- SM1 storage management design (`aidocs/platform/SM1-storage-management.md` if exists)
