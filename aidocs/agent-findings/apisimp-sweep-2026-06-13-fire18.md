---
stage: fragment
last-stage-change: 2026-06-13
---

# APISIMP fire-18 sweep — 2026-06-13

Scan of the live `/v2/` REST surface for residual sprawl. All previous named
API-simplification rows (A4-A7, B-series, APISIMP-EMPTY-BODIES-BATCH-1
through BATCH-8, APISIMP-PAGINATION-UNIFY-RECREATE) are shipped or in-flight.

## Method

Grepped all `backend/src/main/java/de/dlr/shepard/v2/` and `plugins/*/src/main/java/`
REST resources for:

1. Bespoke admin `*ConfigRest` not on the generic `ConfigRegistry`
2. Numeric `@PathParam`/`@QueryParam` on `/v2/` paths
3. `Response.status(UNAUTHORIZED|NOT_FOUND|FORBIDDEN).build()` with no body
4. `Map.of("error",...)` standalone error envelopes
5. Remaining plugin per-kind CRUD REST after V2CONV-A2

## Findings

### Finding 1 — APISIMP-EMPTY-BODIES-BATCH-9

**File:** `v2/references/resources/ReferencesV2Rest.java`  
**Sites:** 17 empty-body 4xx returns (UNAUTHORIZED×5, NOT_FOUND×9, FORBIDDEN×3)
across `create()`, `get()`, `patch()`, `delete()`, `list()`, and the `gateOnParent()`
helper.  
**Pattern:** The file already imports `ProblemJson` and has a private static
`problem()` helper — only missing `PT_UNAUTHORIZED / PT_NOT_FOUND / PT_FORBIDDEN`
constants.  
**Fix:** Add 3 constants; wire all 17 sites.  
**Size:** S

### Finding 2 — APISIMP-EMPTY-BODIES-BATCH-10

**File:** `v2/provenance/resources/ProvenanceRest.java`  
**Sites:** 17 empty-body 4xx returns.  
**Fix:** Add `problem()` helper + PT constants, wire all sites.  
**Size:** S

### Finding 3 — APISIMP-EMPTY-BODIES-BATCH-11

**Files:** Collection-auxiliary cluster (6 files):
`CollectionSceneGraphRest.java` (11), `CollectionPropertiesRest.java` (6),
`CollectionPublicationStateRest.java` (5), `ContainerPublicationStateRest.java` (5),
`CollectionTimelineRest.java` (3), `CollectionContainersRest.java` (3),
`CollectionExportUrlRest.java` (3) = 36 sites total  
**Fix:** Add `problem()` helper + PT constants per file.  
**Size:** M

### Finding 4 — APISIMP-EMPTY-BODIES-BATCH-12

**Files:** Timeseries cluster (5 files):
`TimeseriesAnnotationRest.java` (7), `AnomalyDetectionRest.java` (4),
`TimeseriesContainerTemporalAnnotationRest.java` (3), `SqlTimeseriesRest.java` (1),
`CrossDoBulkDataRest.java` (1) = 16 sites  
**Fix:** Add `problem()` helper + PT constants per file.  
**Size:** S

### Finding 5 — APISIMP-EMPTY-BODIES-BATCH-13

**Files:** Lab journal cluster (4 files):
`LabJournalRenderRest.java` (4), `LabJournalHistoryRest.java` (4),
`NotebookRest.java` (3), `CollectionLabJournalEntriesRest.java` (3) = 14 sites  
**Fix:** Add `problem()` helper + PT constants per file.  
**Size:** S

### Finding 6 — APISIMP-EMPTY-BODIES-BATCH-14

**Files:** Misc small cluster (6 files):
`PublicationsListRest.java` (3), `DataObjectRdfRest.java` (3),
`DmpSnippetV2Rest.java` (3), `DataObjectBatchV2Rest.java` (1),
`SemanticAnnotationV2Rest.java` (1), `CollectionDQRRest.java` (1) = 12 sites  
**Fix:** Add `problem()` helper + PT constants per file.  
**Size:** S

### Finding 7 — APISIMP-EMPTY-BODIES-BATCH-15

**Files:** Admin/me cluster (7 files):
`AdminUserGitCredentialRest.java` (4), `MeRoleInRest.java` (3),
`MePreferencesRest.java` (2), `NotificationTransportRest.java` (2),
`JupyterConfigPublicRest.java` (1), `AdminUserOrcidRest.java` (1),
`AiAdminRest.java` (1) = 14 sites  
**Fix:** Add `problem()` helper + PT constants per file.  
**Size:** S

### Finding 8 — APISIMP-EMPTY-BODIES-BATCH-16

**Files:** Plugin cluster (8 files):
`VideoAnnotationRest.java` (12), `VideoStreamReferenceV2Rest.java` (10),
`MeCredentialsRest.java` (9), `SpatialPromoteRest.java` (5),
`GitReferenceRest.java` (5), `WikiWriterRest.java` (3),
`AasShellsRest.java` (2), `AiAdminRest.java` (1) = 47 sites  
**Fix:** Add `problem()` helper + PT constants per plugin file.  
**Size:** M

## Not-findings (investigated and excluded)

- **Spatiotemporal numeric `@PathParam`** — on `Constants.SHEPARD_API` (v1 frozen compat surface, explicitly exempt per CLAUDE.md)
- **`ShapesRenderRest` Map.of** — RFC-7807 extension members inside `ProblemJson` constructor, correct usage
- **EpicAdminRest, DataciteAdminRest, UnhideAdminRest** — credential-only endpoints (POST/DELETE /credential), NOT config endpoints. Exempt as "credential sister endpoints" per CLAUDE.md
- **`AiAdminRest` capabilities** — capability management surface, architecturally distinct from generic config registry; not a ConfigDescriptor candidate
- **`HdfAdminRest`** — admin action endpoint (rebuild-acls), not a config surface
- **`VideoStreamReferenceV2Rest`** — only POST /upload and GET /{appId}/download remain, which are binary ops outside the unified `/v2/references` surface per PLUGIN-REF-HANDLER-VIDEO/GIT/HDF design

## Total remaining empty-body 4xx sites

196 across 40 files (backend: 149, plugins: 47). Organized into 8 batches above.
