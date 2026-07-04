---
stage: deployed
last-stage-change: 2026-06-26
audience: [contributor]
---

# APISIMP Sweep — 2026-06-26 (fire-241)

Automated scan of the live `/v2` REST surface for residual sprawl.
Scope: `backend/src/main/java/de/dlr/shepard/v2/` + plugin `@Path`s.
Verification fire: follows fire-238 (same day, confirms no regression from
fire-239/240 changes).

Sweep agent scanned 96 v2 REST files, ~261 `@Path` annotations (class + method level).

## What I found

### Numeric ID hygiene — CLEAN

Zero numeric-id leaks confirmed. Known tracked exceptions remain:
- `TimeseriesChannelV2IO.id` / `containerId` — `@Schema(deprecated=true)`.
- `PermissionAuditEntryIO.neo4jNodeId` — intentional triage handle, tracked as
  APISIMP-PERMISSION-AUDIT-NEO4J-ID (blocked on L2 clean). No change.
- `ContainersV2Rest` / `ProvenanceRest` `Long` params — epoch-millisecond
  timestamps, confirmed NOT Neo4j IDs. Legitimate.

No new leaks.

### Forbidden `SHEPARD_API` constant — CLEAN

Zero v2 resource classes use `Constants.SHEPARD_API` in a `@Path` annotation.
All occurrences are comments only.

### Bespoke admin endpoints — CLEAN

`AdminConfigRest` at `/v2/admin/config` is the only generic config registry
resource. All domain-specific admin resources are intentional:
- `AdminFeaturesRest` — feature toggle registry
- `SemanticAdminRest` — semantic ontology admin (mutative operations)
- `FileMigrationRest` — storage migration lifecycle
- `InstanceAdminRest` — instance-admin management
- `AdminStorageOverviewRest` — cross-DB disk usage metrics (TimescaleDB + MongoDB)
- `StorageAdminRest` — file-storage adapter listing (FileStorageRegistry)

**False-positive clarification (sweep agent initially flagged):** `/v2/admin/storage`
and `/v2/admin/storage-overview` are NOT redundant — they serve entirely different
purposes:
- `GET /v2/admin/storage` (FS1e1) — lists `FileStorage` adapters (Garage/S3,
  GridFS, etc.) from `FileStorageRegistry` with active/enabled state.
- `GET /v2/admin/storage-overview` (AD_STORE1) — returns cross-DB disk usage
  metrics: TimescaleDB hypertable size + compression ratio, MongoDB dbStats.

No consolidation needed.

### Pagination style — CLEAN

Uniform `?page=0&pageSize=50` request params and `PagedResponseIO` envelope
across all offset-paginated list endpoints. Known intentional deviations
(FileBundleReferenceRest clamping, ProvenanceRest time-cursor,
DataObjectV2Rest predecessors plain array) remain documented inline.

### Per-kind endpoints — CLEAN

No new per-kind paths not yet unified under `?kind=`. Confirmed intentional:
- `PublishRest` `/v2/{kind}/{appId}/publish` — kind-in-path is intentional KIP
  design; the kind dispatches to the right `PublishableKind` handler.
- `PublicationsListRest` `/v2/{kind}/{appId}/publications` — marked
  `deprecated=true` (OpenAPI annotation); `FlatPublicationsRest` at
  `/v2/publications` is the replacement.

### Render/export endpoints — CLEAN

All render/export endpoints are justified:
- `POST /v2/shapes/render` — canonical stateless render engine (V2CONV-A1)
- `GET /v2/lab-journal/{appId}/render` — J1a sanitized markdown→HTML transform
- `GET /v2/collections/{appId}/export` — RO-Crate ZIP stream
- `POST /v2/collections/{appId}/export-url` — presigned URL (FS1g)
- `POST /v2/collections/{appId}/export/regulatory-evidence` — BagIt REP export
- `GET /v2/templates/{templateAppId}/export` — BTKVS Excel projection

None superseded by `POST /v2/shapes/render`.

### Redundant endpoints — NONE

No pairs of endpoints doing the same thing at different paths.
`FileReferenceV2Rest` and `PublicationsListRest` are properly retired/deprecated
with 410 Gone and migration pointers respectively.

## Opportunities

None. All tracked items are either shipped, intentional deviations, or blocked
on upstream migration work (L2, TS-IDb/c).

## Real-world impact

The v2 surface remains in the clean state established by fires 231–238.
No new sprawl introduced by fires 239–240 (PLACEHOLDER-shapes-render + ArchUnit guard).

## Gaps & blockers

- `APISIMP-PERMISSION-AUDIT-NEO4J-ID` — still blocked on L2 migration confirmation.
  No change.

## What surprised me

The surface held clean across a gap of three fires (238→241). The ArchUnit guard
shipped in fire-240 (`StorageAdapterIsolationTest`) actively prevents new GridFS
imports from leaking outside sanctioned zones — a structural enforcement that
should keep the storage SPI boundary stable.
