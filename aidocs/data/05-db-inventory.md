---
title: DB Schema Inventory
stage: concept
last-stage-change: 2026-05-26
audience: contributors, operators, maintainers
---

# Shepard DB Schema Inventory

_Snapshot 2026-05-26. Refresh annually or when a new substrate is added._

Six substrates. Two Postgres instances (timescaledb on 5432, postgis on 5433).
Migration runners: **Neo4j-Migrations** (Cypher, versioned `V##__*.cypher`)
and **Flyway** (SQL, `V#.#.#__*.sql`). Both run at startup via `MigrationsRunner`
which propagates `MigrationsException` on any failure (fail-fast per `CLAUDE.md`).

Rollback files (`V##_R__*.cypher` / `*_R__*.sql`) are not given table rows —
they are the undo paths of their forward migrations. Paired rollbacks exist for:
V12, V14, V16, V34, V37, V61, V69, V70, V77, V78, V79, V80, V81, V82, V83,
V84, V85, V86, V87 (Cypher) and V1.11.0, V1.14.0 (SQL).

---

## Neo4j (graph)

Engine: `neo4j:5.26`. Port 7687. Driver: Spring Data Neo4j OGM via Quarkus extension.
All entities extend `AbstractEntity` which carries `id` (internal Neo4j id),
`appId` (UUID v7, set by `GenericDAO`), `deleted`, `createdAt`, `updatedAt`,
`createdBy`/`updatedBy` (User edges). The `VersionableEntity` layer adds
`shepardId` (legacy long-id), `revision`, and `version` (Version node edge).
`BasicEntity` adds `name` and `HAS_ANNOTATION` edges. `AbstractDataObject` adds
`description`, `status`, `attributes` (free-text map), `license`, `accessRights`,
`createdByOrcid`, `embargoEndDate`.

Relationship types in use: `HAS_DATAOBJECT`, `HAS_REFERENCE`, `HAS_SUCCESSOR`,
`HAS_CHILD`, `POINTS_TO`, `CREATED_BY`, `UPDATED_BY`, `HAS_PERMISSIONS`,
`HAS_ANNOTATION`, `HAS_VERSION`, `has_permissions`, `HAS_PAYLOAD`, `HAS_GROUP`,
`HAS_DEFAULT_FILE_CONTAINER`, `HAS_LABJOURNAL_ENTRY`, `created_by`,
`created_in_month`, `HAS_PUBLICATION`, `HAS_ACTIVITY`.

### Auth

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :User | `auth/users` | V81 (email unique) | username, email, appId, orcid | `unique_username_User` (V1), `appId_unique_User` (V11), `User_email_unique` (V81) |
| :MirroredUser | `auth/users` | V74 | appId, sourceInstance, sourceUsername | `MirroredUser_appId_unique` (V74), composite idx on `(sourceInstance, sourceUsername)` (V74) |
| :UserGroup | `auth/users` | V11 | name, appId | `unique_id_UserGroup` (V1), `appId_unique_UserGroup` (V11), `idx_UserGroup_deleted` (V1) |
| :ApiKey | `auth/apikey` | V11 | uid, appId | `unique_uid_ApiKey` (V1), `appId_unique_ApiKey` (V11) |
| :Permissions | `auth/permission` | V11 | appId | `unique_id_Permissions` (V1), `appId_unique_Permissions` (V11) |
| :Role | `auth/role` | V13 | appId, name | `appId_unique_Role` (V13) |

### Core context

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :Collection | `context/collection` | V85 (FAIR fields) | name, description, status, appId, license, accessRights, heroImageUrl, importedFrom | `unique_id_Collection` (V1), `appId_unique_Collection` (V11), `idx_Collection_deleted` (V1), `idx_BasicEntity_appId` (V76) |
| :CollectionProperties | `context/collection` | V17 | appId (+ properties keys) | `appId_unique_CollectionProperties` (V17) |
| :DataObject | `context/collection` | V85 (FAIR fields) | name, description, status, appId, provenanceMode, typedPredecessorsJson, license, accessRights, createdByOrcid, embargoEndDate | `unique_id_DataObject` (V1), `appId_unique_DataObject` (V11), `idx_DataObject_deleted` (V1), `idx_BasicEntity_appId` (V76), `DataObject_typedPredecessors_idx` (V84), `DataObject_embargoEndDate_idx` (V85) |
| :LabJournalEntry | `context/labJournal` | V11 | appId, name | `appId_unique_LabJournalEntry` (V11) |
| :LabJournalEntryRevision | `context/labJournal` | V39 | appId, content | `LabJournalEntryRevision_appId_unique` (V39) |

### Data containers

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :FileContainer | `data/file` | V11 | appId | `unique_id_FileContainer` (V1), `appId_unique_FileContainer` (V11), `idx_FileContainer_deleted` (V1) |
| :ShepardFile | `data/file` | V79 (backfill providerId) | appId, oid, providerId, sha256, byteSize | `unique_id_ShepardFile` (V1), `appId_unique_ShepardFile` (V11), `idx_ShepardFile_oid` (V1) |
| :PayloadVersion | `data/file` | V41 | appId | `PayloadVersion_appId_unique` (V41) |
| :StructuredDataContainer | `data/structureddata` | V11 | appId | `unique_id_StructuredDataContainer` (V1), `appId_unique_StructuredDataContainer` (V11), `idx_StructuredDataContainer_deleted` (V1) |
| :StructuredData | `data/structureddata` | V11 | appId, oid | `unique_id_StructuredData` (V1), `appId_unique_StructuredData` (V11), `idx_StructuredData_oid` (V1) |
| :TimeseriesContainer | `data/timeseries` | V47 (chart view) | appId | `unique_id_TimeseriesContainer` (V1), `appId_unique_TimeseriesContainer` (V11), `idx_TimeseriesContainer_deleted` (V1) |
| :Timeseries (=ReferencedTimeseriesNodeEntity) | `data/timeseries` | V82 (backfill appIds) | appId, measurement, device, location, symbolicName, field | `unique_id_Timeseries` (V1), `appId_unique_Timeseries` (V11), `idx_Timeseries_attr` 5-tuple composite (V1); field/location/symbolicName TEXT indexes DROPPED V77 |
| :TimeseriesContainerChartView | `v2/timeseriescontainer` | V47 | appId | `timeseries_container_chart_view_appId_unique` (V47) |
| :TimeseriesAnnotation | `v2/timeseries` | V35 | appId | `TimeseriesAnnotation_appId_unique` (V35) |
| :AnnotatableTimeseries | `context/semantic` | V11 | appId | `appId_unique_AnnotatableTimeseries` (V11) |
| :HdfContainer | `plugins/hdf5 (A5a)` | V25 | appId | `appId_unique_HdfContainer` (V25) |
| :HdfReference | `plugins/hdf5 (A5c)` | V86 | appId, datasetPath | `HdfReference_appId_unique` (V86), `HdfReference_datasetPath_idx` (V86) |
| :SpatialDataContainer | `plugins/spatial` | V11 | appId | `appId_unique_SpatialDataContainer` (V11) |

### References

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :BasicReference | `context/references` | V11 | appId | `unique_id_BasicReference` (V1), `appId_unique_BasicReference` (V11), `idx_BasicReference_deleted` (V1) |
| :FileReference / :SingletonFileReference | `context/references/file` | V24 | appId | `unique_id_FileReference` (V1), `appId_unique_FileReference` (V11), `appId_unique_SingletonFileReference` (V24), `idx_FileReference_deleted` (V1) |
| :FileBundleReference | `context/references/file` | V22 | appId | shares `:FileReference` constraint; `appId_unique_FileGroup` (V22) |
| :FileGroup | `context/references/file` | V22 | appId | `appId_unique_FileGroup` (V22) |
| :URIReference | `context/references/uri` | V11 | appId | `unique_id_URIReference` (V1), `appId_unique_URIReference` (V11), `idx_URIReference_deleted` (V1) |
| :StructuredDataReference | `context/references/structureddata` | V11 | appId | `unique_id_StructuredDataReference` (V1), `appId_unique_StructuredDataReference` (V11), `idx_StructuredDataReference_deleted` (V1) |
| :SpatialDataReference | `plugins/spatial` | V11 | appId | `appId_unique_SpatialDataReference` (V11) |
| :TimeseriesReference | `context/references/timeseries` | V11 | appId | `unique_id_TimeseriesReference` (V1), `appId_unique_TimeseriesReference` (V11), `idx_TimeseriesReference_deleted` (V1) |
| :CollectionReference | `context/references/dataobject` | V11 | appId | `unique_id_CollectionReference` (V1), `appId_unique_CollectionReference` (V11), `idx_CollectionReference_deleted` (V1) |
| :DataObjectReference | `context/references/dataobject` | V11 | appId | `unique_id_DataObjectReference` (V1), `appId_unique_DataObjectReference` (V11), `idx_DataObjectReference_deleted` (V1) |
| :GitReference | `plugins/git (G1a)` | V26 (mode default) | appId, mode | `appId_unique_GitReference` (V19); `mode` default LOOSE_LINK backfilled V26 |
| :VideoStreamReference / :VideoAnnotation | `context/references/videostream` | V52 | appId | `VideoStreamReference_appId_unique` (V38), `VideoAnnotation_appId_unique` (V52) |

### Semantic layer

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :SemanticRepository | `context/semantic` | V49 (internal bootstrap) | appId, type, name, endpoint | `unique_id_SemanticRepository` (V1), `appId_unique_SemanticRepository` (V11), `idx_SemanticRepository_deleted` (V1); n10s fulltext on :Resource V44/V50/V51 |
| :SemanticAnnotation | `context/semantic` | V71 (v6 columns) | appId, predicateUri, vocabularyUri, valueLiteral, sourceMode, confidence, agentModelId | `unique_id_SemanticAnnotation` (V1), `appId_unique_SemanticAnnotation` (V11), `idx_SemanticAnnotation_deleted` (V1) |
| :SemanticConfig | `context/semantic (N1c2)` | V73 (v6 fields) | appId, preseedEnabled, disabledBundles, defaultVocabularyAppId, annotationMode, suggestionEnabled | `appId_unique_SemanticConfig` (V27) |
| :UserOntologyBundle | `context/semantic (N1c2)` | V28 | appId, id, iriPrefix, canonicalUrl, sha256 | `appId_unique_UserOntologyBundle` (V28) |
| :OntologyGitSource | `context/semantic` | V67 | appId | `OntologyGitSource_appId_unique` (V67) |
| :Vocabulary | `context/semantic (SEMA-V6)` | V72 | appId, uri | `Vocabulary_appId_unique` + `Vocabulary_uri_unique` (V72); 10 bootstrap rows (dcterms, prov, schema.org, datacite, chameo, mat, shepard, m4i, skos, geo) + hdf (V87) |
| :Predicate | `context/semantic (SEMA-V6)` | V72 | appId, uri | `Predicate_appId_unique` + `Predicate_uri_unique` (V72) |
| :Resource | `n10s (in-process)` | auto-managed by n10s | rdfs__label, skos__prefLabel, skos__altLabel | fulltext `resource_labels` (V44, patched V50, extended V51) |

### Provenance & activity

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :Activity | `provenance (PROV1a)` | V78 (backfill prov edges) | appId, startedAtMillis, endedAtMillis, action, actorUsername, entityAppId, sourceMode | `appId_unique_Activity` (V15), `Activity_startedAtMillis_idx` RANGE (V75) |
| :InstanceConfig | `provenance` | V59 | appId, instanceId, instanceName | `instance_config_appid_unique` (V59) |
| :Publication | `publish (KIP1a)` | V31 (versionNumber backfill) | appId, pid, versionNumber | `appId_unique_Publication` (V29); @Index on `pid` |
| :Snapshot | `context/snapshot` | V40 | appId | `Snapshot_appId_unique` (V40) |
| :Watch | `v2/watches` | V48 | appId | `watch_appId_unique` (V48) |
| :Notification | `v2/notifications` | V53 | appId, targetUsername | `Notification_appId_unique` (V53), `Notification_targetUsername` idx (V53) |
| :DataQualityRequirement | `v2/quality` | V68 | appId | `DataQualityRequirement_appId_unique` (V68) |
| :CollectionWatcher | `v2/collectionwatchers` | V11 (Subscription label) | appId | `unique_id_Subscription` (V1), `appId_unique_Subscription` (V11) |

### Versioning

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :Version | `context/version` | V6 (bootstrap) | uid, name, description, createdAt | `version_uid` (V6) |
| :VersionableEntity (super-label) | `context/version` | V76 (BasicEntity appId range idx) | appId, shepardId, revision | `appId_unique_VersionableEntity` (V11); `idx_VersionableEntity_shepardId` (V5); `idx_BasicEntity_appId` RANGE (V76) |

### Admin config singletons

| Label | Owner module | Latest migration | Key properties | Constraints / indices |
|---|---|---|---|---|
| :ShepardTemplate | `template (T1a)` | V18 | appId | `appId_unique_ShepardTemplate` (V18) |
| :GitCredential | `plugins/git` | V20 | appId | `appId_unique_GitCredential` (V20) |
| :UnhideConfig | `plugins/unhide (UH1a)` | V30 | appId, feedUrl, enabled | `appId_unique_UnhideConfig` (V30) |
| :DataciteMinterConfig | `plugins/minter-datacite (KIP1d)` | V33 | appId | `appId_unique_DataciteMinterConfig` (V33) |
| :EpicMinterConfig | `plugins/minter-epic` | V45 | appId | `EpicMinterConfig_appId_unique` (V45) |
| :AasRegistration | `plugins/aas` | V46 | appId | `AasRegistration_appId_unique` (V46) |
| :InstanceRorConfig | `v2/admin/ror` | V42 | appId, rorId | `instance_ror_config_appId_unique` (V42) |
| :SqlTimeseriesConfig | `v2/admin/sqltimeseries` | V43 | appId | `SqlTimeseriesConfig_appId_unique` (V43) |
| :AiCapabilityConfig | `spi/ai` | V66 | appId, capability (unique) | `AiCapabilityConfig_appId_unique` (V58), `AiCapabilityConfig_capability_unique` (V66) |
| :AiGlobalConfig | `spi/ai` | V66 | appId | `AiGlobalConfig_appId_unique` (V66) |
| :PluginRuntimeOverride | `plugin (PM1e)` | V32 | pluginId (unique) | `pluginId_unique_PluginRuntimeOverride` (V32) |
| :LegacyV1Config | `v1-compat plugin` | V63 | appId, enabled | bootstrapped by V63; `LegacyV1Config_appId_unique` |
| :ImportPlan / :ImportLock | `v2/importer` | V65 (ImportLock) | appId; lockId (ImportLock) | `Snapshot_appId_unique` (V40) for ImportPlan; `ImportLock_lockId_unique` + `ImportLock_appId_unique` (V65) |

### Special indices (cross-cutting)

| Index name | Migration | Coverage |
|---|---|---|
| `idx_BasicEntity_appId` RANGE | V76 | `:BasicEntity.appId` — permissions hot-path (1 800× speedup on MFFD data) |
| `Activity_startedAtMillis_idx` RANGE | V75 | `:Activity.startedAtMillis` — time-range activity queries |
| `idx_VersionableEntity_shepardId` | V5 | legacy long-id lookups |
| `resource_labels` FULLTEXT | V44/V50/V51 | n10s `:Resource` — autocomplete for rdfs:label, skos:prefLabel, skos:altLabel |
| `idx_Timeseries_attr` composite | V1 | 5-tuple (measurement, device, location, symbolicName, field) — still present; field/location/symbolicName TEXT indexes dropped V77 |
| `created_in_month` relationship index | V80 | `:User` supernode fanout mitigation — `created_in_month {ym:"YYYYMM"}` bucket relationships |
| `MirroredUser_sourceInstance_username_idx` | V74 | federated-user MERGE lookups |

---

## PostgreSQL / TimescaleDB

Engine: `timescale/timescaledb:2.24.0-pg16`. Port 5432 (`timescaledb` container).
Migration runner: Flyway. Migrations in `backend/src/main/resources/db/migration/`.
Plugin `importer` contributes an additional migration at its own classpath
(`plugins/importer/.../db/migration/V1.11.1__add_importer_run_table.sql`)
which Flyway discovers via the shared datasource.

| Table | Owner | Migration | Key columns | Indices |
|---|---|---|---|---|
| `timeseries` | `data/timeseries` | V1.0.0 (create), V1.2.0 (unique), V1.11.0 (shepard_id), V1.14.0 (5-tuple moved) | `id` SERIAL PK, `container_id`, `value_type`, `shepard_id` UUID NOT NULL | `unique_timeseries_5tuple` (V1.2.0, dropped at V1.14.0 but re-expressed on `channel_metadata`); `idx_timeseries_shepard_id` UNIQUE (V1.11.0) |
| `timeseries_data_points` | `data/timeseries` | V1.0.0 (create), V1.4.0 (compression), V1.8.0 (perf), V1.12.0 (NOT NULL), V1.13.0 (chunk config) | `timeseries_id` INT NOT NULL FK, `time` BIGINT NOT NULL (ns epoch), `double_value`, `int_value`, `string_value`, `boolean_value` | Hypertable (V1.0.0, chunk 1h V1.13.0, 4 space partitions); compression (7-day policy V1.8.0, compress_segmentby=timeseries_id); `timeseries_data_points_id_time_idx` (V1.8.0); `UNIQUE(timeseries_id, time)` dropped on compression enable |
| `timeseries_hourly` | `data/timeseries` | V1.12.1 (create), V1.17.0 (integer_now fix) | `timeseries_id`, `hour_bucket` BIGINT, `avg_double`, `min_double`, `max_double`, `avg_int`, `min_int`, `max_int`, `sample_count` | Continuous aggregate (TimescaleDB CAgg), refresh policy 1h, 25h lag; `integer_now_func` set to `unix_now_immutable` |
| `channel_metadata` | `data/timeseries` | V1.14.0 | `id` BIGSERIAL PK, `timeseries_id` INT UNIQUE FK, `container_id`, `measurement`, `field`, `device`, `location`, `symbolic_name` | `UNIQUE(container_id, measurement, field, symbolic_name, device, location)` — holds the 5-tuple offloaded from `timeseries` |
| `migration_tasks` | `data/timeseries` | V1.1.0 (create), V1.5.0 (add timeseries/database columns) | `id` SERIAL PK, `container_id`, `state`, `timeseries`, `database_name`, `created_at`, `started_at`, `finished_at`, `errors` | `UKne5ydyv5e0gl9v6vv588hxk6q` unique on `container_id` (dropped+re-added by V1.5.0) |
| `migration_progress` | `data/timeseries` | V1.9.0 | `container_id` BIGINT PK, `rows_total`, `rows_migrated`, `rows_failed`, `last_batch_index`, `status` CHECK, `started_at`, `last_update_at`, `errors` | `idx_migration_progress_status` (V1.9.0) |
| `permission_audit_log` | `auth/permission` | V1.10.0 | `id` BIGSERIAL PK, `occurred_at`, `entity_app_id`, `entity_kind`, `actor_username`, `action`, `detail_json` | `perm_audit_entity_app_id_idx`, `perm_audit_occurred_at_idx DESC` (V1.10.0) |
| `predicate_vocabulary` | `context/semantic (SEMA-V6)` | V1.16.0 | `predicate_uri` TEXT PK, `substrate`, `cardinality`, `writable`, `description`, `shape_file`, `added_at` | `predicate_vocab_substrate_idx` (V1.16.0); 7 bootstrap rows seeded |
| `importer_run` | `plugins/importer (IMP1a)` | V1.11.1 (plugin-owned) | `id` UUID PK, `source_kind`, `principal`, `target_collection_app_id`, `status` CHECK, `cancel_requested`, lifecycle timestamps, `progress_*`, `error_*`, `result_url`, `result_metadata` JSONB, `request_payload` JSONB, `source_config` JSONB | `idx_importer_run_principal_status`, `idx_importer_run_status_progress` (partial WHERE RUNNING), `idx_importer_run_status_finished` (partial WHERE terminal), `idx_importer_run_target_collection` |

**Utility functions:**
- `unix_now_immutable()` — IMMUTABLE SQL function returning `epoch * 1e9` (ns). Registered as `integer_now_func` on `timeseries_data_points` (V1.6.0) and `timeseries_hourly` materialization hypertable (V1.17.0).
- `pgcrypto` extension (V1.11.0) — `gen_random_uuid()` used for `shepard_id` default.
- `timescaledb_toolkit` extension (V1.15.0) — optional; gracefully skipped if image lacks it.

---

## PostGIS

Engine: `postgis/postgis:16-3.5`. Port 5433 (`postgis` container, separate Postgres instance).
Migration runner: **separate Flyway datasource** (spatial plugin configures its own `db/spatial/migration/` path).

| Table | Owner | Migration | Geometry type | Partitioning | Indices |
|---|---|---|---|---|---|
| `spatial_data_points` | `plugins/spatial` | V1.0.0 (plugin) | `position GEOMETRY NOT NULL` (generic, accepts all geometry types) | HASH partition on `container_id`, 100 partitions (`spatial_data_points_p0`…`_p99`) | `spatial_data_points_container_id_idx` BTREE; `spatial_data_point_position_idx` GIST (`gist_geometry_ops_nd`); `spatial_data_points_metadata_idx` GIN on `metadata` JSONB |

**Additional columns:** `time BIGINT NOT NULL` (nanosecond epoch, same as TimescaleDB), `metadata JSONB`, `measurements JSONB`.
Init script (`docker-entrypoint-initdb.d/postgis/00-init-postgis-db.sh`) creates a restricted `$POSTGRES_SHEPARD_USER` with DML-only privileges.

---

## MongoDB

Engine: `mongo:8.0.4`. Default port 27017 (`mongodb` container). No fixed migration runner — schema is schemaless; collections are created on first write by the application.

**Collection naming convention:**

| Collection pattern | Owner | Write path | Notes |
|---|---|---|---|
| `<StructuredDataContainer.mongoId>` | `data/structureddata` | `StructuredDataDAO`: `mongoDatabase.createCollection(mongoId)` on container creation; `insertOne()` per structured-data item | One Mongo collection per `StructuredDataContainer`. `mongoId` = OGM-generated id (long as string). Each document has `oid`, `createdAt`, `name` fields plus free-form payload. |
| `fs.files` + `fs.chunks` | `data/file (GridFS)` | `FileService.createBucket()` → `GridFSBuckets.create(mongoDatabase)` (default bucket name `fs`) | Shared GridFS bucket for the `gridfs` file storage provider. Each file upload creates one document in `fs.files` (metadata: filename, sha256, contentType, length) and N chunk documents in `fs.chunks`. `FileContainer.mongoId` acts as a folder discriminator within the shared bucket — files are tagged with the container id, not isolated in per-container Mongo collections. |

Active write paths:
- `StructuredDataDAO.create()` / `update()` / `delete()` — one collection per container.
- `FileService.createFile()` / `createFileWithSha256()` — `fs.files` / `fs.chunks`.
- Container deletion drops the associated Mongo collection (`mongoDatabase.getCollection(mongoId).drop()`).

---

## HDF5 (HSDS)

Engine: `hdfgroup/hsds:v0.9.5`. Port 5101 (`shepard-hsds` container). POSIX backend (host-mounted volume). Used by `plugins/hdf5` (A5 series).

| Domain pattern | Owner | Content type | Write path |
|---|---|---|---|
| `/home/<user>/<containerAppId>/<datasetPath>` | `plugins/hdf5` | HDF5 datasets (structured numerical arrays, groups) | `A5c` HdfReference REST endpoints write dataset arrays via HSDS HTTP API; Neo4j `:HdfContainer` and `:HdfReference` nodes track the graph side |

Default bucket: `${HSDS_BUCKET_NAME:-shepard}` (env var in compose). HSDS POSIX mode stores data under the host volume `${HSDS_ROOT_DIR}`. No SQL migration — schema is purely file-system shaped within the HSDS domain hierarchy.

---

## Garage / S3

Engine: `dxflrs/garage:v1.0.1`. S3 API port 3900 (`shepard-garage` container). ADR-0024 selected Garage over MinIO (archived community edition).

| Bucket | Owner | Content type | Write path |
|---|---|---|---|
| `${shepard.files.s3.bucket}` (operator-configured) | `plugins/file-s3 (FS1b)` | Binary file payloads | `S3FileStorage.put()` via AWS SDK v2; locator format `<containerMongoId>/<uuid>`; one random UUID key per upload |

**Bucket configuration is operator-controlled.** The `S3FileStorage` bean has `isEnabled()=false` when `shepard.files.s3.bucket` is empty (the deploy-time default). Until set, all file storage falls back to the `gridfs` adapter. There is no fixed bucket list — a single bucket per Shepard deployment is the recommended topology.

No SQL or Cypher migrations for Garage — bucket creation and key generation are handled at runtime by the application. The `garage.toml` in `infrastructure/` configures replication factor (1 for single-node dev) and metadata/data directories.

---

## Plugin-owned migrations summary

| Plugin | Migration file | Substrate | Owner |
|---|---|---|---|
| `shepard-plugin-importer` | `plugins/importer/src/main/resources/db/migration/V1.11.1__add_importer_run_table.sql` | TimescaleDB (shared datasource) | `plugins/importer (IMP1a)` |
| `shepard-plugin-spatial` | `plugins/spatial/src/main/resources/db/spatial/migration/V1.0.0__setup_spatial_data_tables.sql` | PostGIS (separate datasource) | `plugins/spatial` |

Plugins with Neo4j-side schema additions (git, hdf5, unhide, aas, minter-datacite, minter-epic, v1-compat, ai) contribute via the core migration chain (`V19–V87`) rather than separate migration files — their constraints land in the versioned `backend/src/main/resources/neo4j/migrations/` sequence.

---

## Infrastructure ports reference

| Container | Engine | Port | Role |
|---|---|---|---|
| `neo4j` | Neo4j 5.26 | 7687 (Bolt) | Graph database |
| `mongodb` | MongoDB 8.0.4 | 27017 | Structured data + GridFS |
| `timescaledb` | TimescaleDB 2.24/PG16 | 5432 | Timeseries + importer jobs |
| `postgis` | PostGIS 16-3.5 | 5433 | Spatial data |
| `shepard-hsds` | HSDS 0.9.5 | 5101 | HDF5 dataset server |
| `shepard-garage` | Garage 1.0.1 | 3900 | S3-compatible file storage |
