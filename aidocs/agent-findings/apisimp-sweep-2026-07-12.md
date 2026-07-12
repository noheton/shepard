---
stage: deployed
last-stage-change: 2026-07-12
---

# APISIMP sweep — 2026-07-12 (fire-559)

Full inventory of 84 v2 REST classes audited. Five new findings filed as
`APISIMP-*` rows in `aidocs/16-dispatcher-backlog.md`. No new finding is
higher than LOW severity. Positive results: no forbidden
`Constants.SHEPARD_API` usages in the v2 directory (clean); no v2 endpoint
superseded by `POST /v2/shapes/render` (correct); pagination param names
are consistent (`page`/`pageSize`) across all endpoints.

## Complete Resource Inventory (84 classes)

| Class | Base Path |
|---|---|
| AdminConfigRest | `/v2/admin/config` |
| BootstrapRest | `/v2/admin/bootstrap` |
| FileMigrationRest | `/v2/admin/files/migrate` |
| InstanceRegistryRest | `/v2/admin/instances` |
| LedgerAnchorRest | `/v2/admin/ledger` |
| AdminMetricsRest | `/v2/admin/metrics-summary` |
| MffdProcessChainMappingRest | `/v2/admin/mffd/process-chain-mapping` |
| NotificationAdminRest | `/v2/admin/notifications` |
| NotificationTransportRest | `/v2/admin/notifications/transports` |
| PluginsAdminRest | `/v2/admin/plugins` |
| AdminFeaturesRest | `/v2/admin/runtime-toggles` (TOMBSTONE — 410 Gone) |
| SemanticAdminRest | `/v2/admin/semantic` |
| OntologyGitSourceRest | `/v2/admin/semantic/git-sources` |
| StorageAdminRest | `/v2/admin/storage` |
| AdminStorageOverviewRest | `/v2/admin/storage-overview` |
| MirroredUserRest | `/v2/admin/users/mirror` |
| AdminUserGitCredentialRest | `/v2/admin/users/{username}/git-credentials` |
| AdminUserOrcidRest | `/v2/admin/users/{username}/orcid` |
| InstanceAdminRest | `/v2/admin` |
| SemanticAnnotationV2Rest | `/v2/annotations` |
| CollectionV2Rest | `/v2/collections` |
| CollectionExportUrlRest | `/v2/collections` |
| CollectionStreamExportRest | `/v2/collections` |
| RepExportV2Rest | `/v2/collections` |
| DmpSnippetV2Rest | `/v2/collections` |
| CollectionCrossTimelineRest | `/v2/collections/timeline` |
| CollectionPermissionsRest | `/v2/collections/{appId}` |
| CollectionEventsRest | `/v2/collections/{appId}/events` |
| CollectionLabJournalEntriesRest | `/v2/collections/{appId}/lab-journal-entries` |
| CollectionPropertiesRest | `/v2/collections/{appId}/properties` |
| CollectionPublicationStateRest | `/v2/collections/{appId}/publication-state` |
| CollectionContainersRest | `/v2/collections/{appId}/referenced-containers` |
| CollectionSceneGraphRest | `/v2/collections/{appId}/scene-graph` |
| CollectionSnapshotRest | `/v2/collections/{appId}/snapshots` |
| CollectionTemplatesRest | `/v2/collections/{appId}/templates` |
| CollectionTimelineRest | `/v2/collections/{appId}/timeline` |
| CollectionWatchesRest | `/v2/collections/{appId}/watched-containers` |
| CollectionWatchersRest | `/v2/collections/{appId}/watches` |
| DataObjectV2Rest | `/v2/collections/{collectionAppId}/data-objects` |
| DataObjectCollectionScopedRdfRest | `/v2/collections/{collectionAppId}/data-objects` |
| TemplateInstantiationRest | `/v2/collections/{collectionAppId}/data-objects/from-template` |
| CollectionDQRRest | `/v2/collections/{collectionAppId}/dqr` |
| SnapshotPinnedReadRest | `/v2/collections/{collectionAppId}/snapshots/{appId}/data-objects` |
| ContainersV2Rest | `/v2/containers` |
| ContainerPublicationStateRest | `/v2/containers/{appId}/publication-state` |
| DataObjectRdfRest | `/v2/data-objects` |
| DataObjectBatchV2Rest | `/v2/data-objects/batch` |
| CrossDoBulkDataRest | `/v2/data-objects/cross-bulk` |
| CrossDoBulkTombstoneRest | `/v2/data-objects/cross-timeseries-bulk` (TOMBSTONE) |
| ImportV2Rest | `/v2/import` |
| ImportDiagnosticsV2Rest | `/v2/import` |
| ImportJobsV2Rest | `/v2/import/jobs` |
| ImportLockV2Rest | `/v2/import/lock` |
| InstanceCapabilitiesRest | `/v2/instance/capabilities` |
| InstanceIdentityRest | `/v2/instance/identity` |
| InstanceRegistryPublicRest | `/v2/instance/registry` |
| JupyterConfigPublicRest | `/v2/jupyter/config` |
| LabJournalHistoryRest | `/v2/lab-journal` |
| LabJournalRenderRest | `/v2/lab-journal` |
| NotebookRest | `/v2/lab-journal` |
| MappingsMaterializeRest | `/v2/mappings` |
| NotificationRest | `/v2/notifications` |
| ProjectsRest | `/v2/projects` |
| ProvenanceRest | `/v2/provenance` |
| FlatPublicationsRest | `/v2/publications` |
| IndependenceProofRest | `/v2/quality/independence-proof` |
| ReferencesV2Rest | `/v2/references` |
| ReferenceActionsRest | `/v2/references/{appId}/actions` |
| ReferenceAnnotationRest | `/v2/references/{appId}/annotations` |
| AnomalyDetectionTombstoneRest | `/v2/references/{appId}/detect-anomalies` (TOMBSTONE) |
| BundleGroupsV2Rest | `/v2/references/{appId}/groups` |
| SearchV2Rest | `/v2/search` |
| SemanticSparqlRest | `/v2/semantic` |
| OntologyAlignmentRest | `/v2/semantic/ontology/alignment` |
| SemanticPredicateStatsRest | `/v2/semantic/predicates` |
| SemanticTermSearchRest | `/v2/semantic/terms/search` |
| VocabularyBrowseRest | `/v2/semantic/vocabularies` |
| ShapesApplicableRest | `/v2/shapes/applicable` |
| ShapesBuildRest | `/v2/shapes/build` |
| ShapesPredicatesRest | `/v2/shapes/predicates` |
| ShapesRenderRest | `/v2/shapes/render` |
| ShapesValidateRest | `/v2/shapes/validate` |
| SnapshotListRest | `/v2/snapshots` |
| SnapshotDiffRest | `/v2/snapshots/{aAppId}/diff/{bAppId}` |
| SnapshotRest | `/v2/snapshots/{appId}` |
| SqlTimeseriesRest | `/v2/sql/timeseries` |
| ShepardTemplateRest | `/v2/templates` |
| TemplatePortabilityRest | `/v2/templates` |
| TemplateExcelExportRest | `/v2/templates/{appId}/export` |
| TemplateFormRest | `/v2/templates/{appId}/form` |
| UserGroupV2Rest | `/v2/user-groups` |
| UserSearchV2Rest | `/v2/users` |
| MeRest | `/v2/users/me` |
| UserAvatarRest | `/v2/users/me/avatar` |
| MePreferencesRest | `/v2/users/me/preferences` |
| MeRoleInRest | `/v2/users/me/role-in/{appId}` |
| UserAvatarByAppIdRest | `/v2/users/{appId}/avatar` |
| PublicationsListRest | `/v2/{kind}/{appId}/publications` |
| PublishRest | `/v2/{kind}/{appId}/publish` |

## Finding summary

| # | Category | Severity | Row filed |
|---|---|---|---|
| 1 | `NukeResultIO.deletedNeo4jNodes` — field name leaks backend substrate | LOW | `APISIMP-NUKE-RESULT-NEO4J-NAME` |
| 2 | `PublishRest`/`PublicationsListRest` use `@PathParam("kind")` path segment vs `?kind=` query param used elsewhere | LOW | `APISIMP-PUBLISH-KIND-PATHSEG` |
| 3 | `JupyterConfigPublicRest` at `GET /v2/jupyter/config` — bespoke public read outside admin registry | LOW | `APISIMP-JUPYTER-PUBLIC-CONFIG-GENERIC` |
| 4 | `AdminFeaturesRest` at `/v2/admin/runtime-toggles` — dead 410 tombstone class, safe to delete | LOW | `APISIMP-ADMIN-FEATURES-TOMBSTONE-DELETE` |
| 5 | `pageSize` default = 200 on 7 endpoints, 100/20 on 3 others, vs standard 50 | LOW | `APISIMP-PAGESIZE-DEFAULT-AUDIT` |

## Already-tracked findings (not re-filed)

| Finding | Existing row | Status |
|---|---|---|
| `TimeseriesChannelV2IO.int id` — Postgres serial on wire | `APISIMP-TSCHANNEL-INT-ID-DEPRECATE` | ✅ done (fire-218) |
| `TimeseriesChannelV2IO.long containerId` — Postgres serial FK on wire | `APISIMP-TSCHANNEL-CONTAINER-ID-WIRE` | ⏳ blocked (TS-IDb/c) |
| `PermissionAuditEntryIO.Long neo4jNodeId` — Neo4j internal node ID | `APISIMP-PERMISSION-AUDIT-NEO4J-ID` | ⏳ queued (blocked L2e) |

## Positive results

- **`Constants.SHEPARD_API` in v2 directory**: CLEAN — zero hits.
  No forbidden v1 path creep detected.
- **Endpoints superseded by `POST /v2/shapes/render`**: NONE.
  `LabJournalRenderRest` and `CollectionSceneGraphRest` are unrelated; not superseded.
- **Pagination param names**: CONSISTENT — all paginated endpoints use `page` + `pageSize`.
  (Defaults and max caps vary; see Finding 5.)
