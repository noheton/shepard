---
stage: fragment
last-stage-change: 2026-05-29
---

# DB Best-Practices Catalogue — All Six Substrates

**Audience.** Authors of new backend code in this fork. Copy patterns
from here; do not invent a new pattern when a proven one exists.

**Living document.** Add a row whenever a new practice is established,
a pattern is refined, or a known anti-pattern is retired. The "example"
column must cite a real file path and line range (or a commit hash when
the practice spans a whole file).

**Six substrates covered:**

1. Neo4j (primary graph store)
2. TimescaleDB / PostgreSQL (timeseries data)
3. PostgreSQL via JPA / Panache (structured plugin tables)
4. MongoDB (structured data payloads)
5. PostGIS (spatial data — `plugins/spatial`)
6. Garage / MinIO S3 (file storage — `plugins/file-s3`)

---

## 1. Neo4j

**Package root:** `backend/src/main/java/de/dlr/shepard/common/neo4j/`
**Migrations:** `backend/src/main/resources/neo4j/migrations/V*.cypher`

### 1.1 Best-practices table

| Practice | Rationale | Representative example |
|---|---|---|
| **Parametrised Cypher via `Map<String, Object>` params** — never concatenate user-supplied strings into a Cypher string. Call `session.query(type, query, paramsMap)` and bind every variable with `$paramName`. | Prevents Cypher injection (C5 / C5b security findings). The OGM session driver handles quoting and type coercion; concatenation bypasses both. | `GenericDAO.getSearchForReachableReferencesByNeo4jIdQuery` — `Map.of("startAppId", ..., "collectionAppId", ...)` passed to `new Neo4jQuery(ret, params)`. `GenericDAO.java` lines 221–229, commit `c707e56` (C5b). |
| **`IF NOT EXISTS` on every constraint and index** — all `CREATE CONSTRAINT` and `CREATE INDEX` statements in migration files must carry `IF NOT EXISTS`. | Migration files can be re-applied on a dev stack that was partially migrated. Without the guard, a re-run aborts at the first duplicate-constraint error and leaves the graph in an inconsistent state. | `V11__Add_appId_unique_constraints.cypher` — every `CREATE CONSTRAINT … IF NOT EXISTS` line. `V58__AiCapabilityConfig_constraint.cypher` single-statement example. |
| **`HasAppId` on every new Neo4j entity** — implement `de.dlr.shepard.common.identifier.HasAppId` and let `GenericDAO.createOrUpdate()` mint a UUID v7 `appId` automatically on first save. | Every entity needs a stable, substrate-independent identifier that crosses substrate boundaries (annotations, provenance edges, REST paths). The `appId` is minted by `AppIdGenerator.next()` at the write seam — callers never set it manually. | `HasAppId.java` (interface contract), `GenericDAO.createOrUpdate()` lines 125–156 (mint-on-null guard). Constraint registered by migration `V11`. |
| **Query by `appId` property, not by `id()`** — use `WHERE e.appId = $appId` in new Cypher queries. The deprecated `id()` function (Neo4j internal node ID) is being retired by the L2 migration chain. | Internal Neo4j IDs are reused after node deletion. `id()` is not stable across restores and was the root cause of the `C5b` injection surface. `appId` (UUID v7, unique-constrained) is the safe replacement. | `GenericDAO.getSearchForReachableReferencesQuery(long, String)` — `WHERE col.appId = $collectionAppId` at line 250. `EntityIdResolver` bridges legacy `long` call-sites to appId at the DAO boundary. |
| **`@NodeEntity` label = class name** — OGM maps each concrete entity class to a Neo4j label matching `ClassName`. Do not override the label unless migrating legacy data. Constraints in migration files reference the exact class-name label. | OGM's class-per-label registry assumes this mapping. A mismatch between the Java class name and the migration's `FOR (n:Label)` target means the constraint lands on a label no node ever carries — silent no-op that creates a false sense of security. | `V11__Add_appId_unique_constraints.cypher` — e.g. `CREATE CONSTRAINT appId_unique_Collection IF NOT EXISTS FOR (n:Collection) …` matches `Collection.java` OGM entity. |
| **Uniqueness-only constraint during additive migration; no `IS NOT NULL` until backfill is complete** — phase new columns in as nullable; add the uniqueness constraint first; run backfill; then optionally add the not-null assertion. | Neo4j 5 uniqueness constraints ignore `null` values, so adding the constraint before backfill is safe. Adding a `IS NOT NULL` assertion before every row has a value would fail immediately on any graph with pre-migration nodes. | `V11__Add_appId_unique_constraints.cypher` comment header (L2a phase description): "Uniqueness only — NO non-null assertion, because rows pre-dating L2a keep appId = null until L2b's backfill". |
| **One migration file per logical change, numbered sequentially** — each `V<N>__<Description>.cypher` file is immutable after it has been applied to any production instance. Never edit an applied migration; instead, add a new `V<N+1>__` file. | Neo4j Migrations (the library) hashes each applied file. If the file changes after application, the checksum mismatch causes `Migrations.validate()` to fail and `neo4j-migration-chain-readiness` to report unhealthy on the next startup. | `V50__Fix_fulltext_index_Resource_labels.cypher`, `V51__Extend_fulltext_index_Resource_labels.cypher` — two sequential files for two phases of the same index refactor, rather than mutating V50 after the fact. |
| **Rollback file for data-mutating migrations** — when a migration writes or deletes data (not just schema), ship a paired `V<N>_R__rollback.cypher` alongside the forward migration. | Data-mutating migrations are irreversible at the schema level. A rollback file gives an operator a known-good path to undo the change from `cypher-shell` without reconstructing the reverse logic under pressure. | `V61_R__rollback.cypher` paired with `V61__v15_prov_predicates.cypher`. |
| **No raw string interpolation in `deleteRelation()`** — the one remaining string-concat site in `GenericDAO.deleteRelation()` uses `shepardId` (a server-controlled integer) not a user string. Even so, treat it as a debt item and migrate to a params map at the next touch. | String format with a server-generated integer is not injection-vulnerable, but the pattern must not be copied. Every new Cypher-executing method must use the named-parameter map form. | `GenericDAO.deleteRelation()` lines 308–317 — acceptable for now (integer-only interpolation); do not replicate for string fields. |

### 1.2 Session management

- The OGM `Session` is obtained once per `GenericDAO` constructor via `NeoConnector.getInstance().getNeo4jSession()`. Do not hold sessions across request boundaries; the Quarkus CDI scope ensures a fresh session per injection site.
- `session.clear()` is available as `GenericDAO.clearSession()` for tests that need to detach the session-level cache before re-fetching.

### 1.3 Error-handling pattern

- `session.load()` returns `null` for a missing node — callers throw `jakarta.ws.rs.NotFoundException` (mapped to HTTP 404 by the standard Quarkus exception mapper).
- `resolveAppIdOrEmpty()` in `GenericDAO` converts a missing-OGM-Long lookup to an empty-string sentinel that never matches any real appId, preserving the pre-L2c "unknown id → no match" semantic without throwing.

---

## 2. TimescaleDB / PostgreSQL (timeseries)

**Package root:** `backend/src/main/java/de/dlr/shepard/data/timeseries/`
**Migrations:** `backend/src/main/resources/db/migration/V1.*.sql`

### 2.1 Best-practices table

| Practice | Rationale | Representative example |
|---|---|---|
| **Batch insert at `INSERT_BATCH_SIZE = 20 000` rows** — build one `INSERT … VALUES (…), (…), …` statement per 20 000 data points; do not issue one statement per row. | A single-row insert-per-point degrades PostgreSQL throughput by ~100×. The 20 000 row ceiling keeps the prepared-statement size below the `max_wal_size` threshold and avoids OOM from unbounded statement growth. | `TimeseriesDataPointRepository.insertManyDataPoints()` — `INSERT_BATCH_SIZE = 20000` constant at line 35; batch loop at lines 56–70. |
| **`COPY`-based bulk ingest for migration workloads** — use `CopyManager.copyIn()` with a CSV stream instead of `INSERT` when ingesting large historical datasets. | `COPY` bypasses the query planner and WAL constraint checks, giving 5–10× higher ingest rates for sequential bulk loads (as measured in `aidocs/data/12-timescaledb-performance-analysis.md`). | `TimeseriesDataPointRepository.insertManyDataPointsWithCopyCommand()` lines 81–128; batched variant with per-batch progress callback at lines 139–159. |
| **Named bind parameters via `Query.setParameter()`** — every SQL string uses `:paramName` placeholders, never string concatenation for user-supplied values. | SQL injection prevention. JPA `createNativeQuery()` + `setParameter()` is the safe equivalent of JDBC `PreparedStatement`. | `TimeseriesDataPointRepository.buildSelectQueryObject()` — `query.setParameter("timeseriesId", timeseriesId)` etc., lines 307–314. |
| **`CREATE INDEX IF NOT EXISTS` for every new index** — SQL migration files use the idempotent form so re-runs are no-ops. | Same rationale as Neo4j: partial migration is a real scenario on dev stacks and during disaster recovery. | `V1.8.0__optimize_timeseries_performance.sql` line 1: `CREATE INDEX IF NOT EXISTS timeseries_data_points_id_time_idx …`. |
| **`ALTER TABLE … ADD COLUMN IF NOT EXISTS` for new columns** — new Postgres columns are added nullable first; no `NOT NULL` without a preceding backfill step. | Prevents table-lock backfill on tables with millions of rows. Nullable columns can be added instantly even on hypertables with hundreds of compressed chunks. | `V1.11.0__add_shepard_id_to_timeseries.sql` — four-step pattern: nullable `ADD COLUMN IF NOT EXISTS`, `UPDATE … WHERE NULL`, `SET NOT NULL`, `SET DEFAULT`. |
| **Rollback file paired with data-mutating SQL migrations** — every migration that writes or removes data ships a `V<N>_R__<description>.sql` sibling. | Operators must be able to undo a migration from `psql` without reconstructing the logic. | `V1.11.0_R__drop_shepard_id.sql` rollback file for the `shepard_id` column addition. |
| **7-day hot window before compression** — the TimescaleDB compression policy is set to 7 days (not 1 day), keeping recent data in uncompressed chunks. | Out-of-order point insertions and `INSERT … ON CONFLICT DO UPDATE` against compressed chunks force segment decompression — a 10–50× write-latency penalty. A 7-day hot window covers all realistic out-of-order backfill scenarios while still compressing 90%+ of data at rest. | `V1.8.0__optimize_timeseries_performance.sql` lines 28–33: `add_compression_policy(…, BIGINT '604800000000000')` (7 days in nanoseconds). |
| **Composite `(timeseries_id, time DESC)` index on the hypertable** — maintain this index in addition to TimescaleDB's default time-only index. | Without this composite index, a `WHERE timeseries_id = ? AND time BETWEEN ? AND ?` query degenerates into a full chunk scan on uncompressed chunks. The index is 20–50× faster for the single-series time-range query that every chart renders. | `V1.8.0__optimize_timeseries_performance.sql` lines 14–16: `CREATE INDEX IF NOT EXISTS timeseries_data_points_id_time_idx ON timeseries_data_points (timeseries_id, time DESC)`. |
| **Chunk-skipping on `timeseries_id`** — enable `enable_chunk_skipping('timeseries_data_points', 'timeseries_id')` so the planner can skip chunks that don't contain the requested series. | Avoids opening every chunk in a year-range query. Per-chunk min/max statistics let the planner jump directly to relevant chunks. | `V1.8.0__optimize_timeseries_performance.sql` lines 36–39. |
| **`@Timed` annotation on every repository method** — wrap every public data-access method with `@Timed(value = "shepard.<domain>.<operation>")` for automatic Micrometer instrumentation. | Provides out-of-the-box latency percentile tracking per operation in Prometheus/Grafana without bespoke instrumentation code. | `TimeseriesDataPointRepository` — `@Timed(value = "shepard.timeseries-data-point.batch-insert")` at line 54, `@Timed(value = "shepard.timeseries-data-point.query")` at line 168. |

### 2.2 Session / connection management

- Use `@PersistenceContext EntityManager entityManager` (request-scoped by Quarkus) for JPA queries. Never cache the `EntityManager` in an instance field.
- For raw JDBC (COPY operations): obtain and close within a single try-with-resources `AgroalDataSource.getConnection()` block — see `insertManyDataPointsWithCopyCommand()` lines 85–127.

---

## 3. PostgreSQL via JPA / Panache (plugin structured tables)

**Primary example:** `plugins/importer/src/main/java/de/dlr/shepard/plugins/importer/runs/`
**Migrations:** `plugins/importer/src/main/resources/db/migration/V1.11.1__add_importer_run_table.sql`

### 3.1 Best-practices table

| Practice | Rationale | Representative example |
|---|---|---|
| **`@Entity` + `PanacheRepositoryBase<E, ID>` pattern** — define the entity as a plain `@Entity @Table(name = "snake_case_table")` class and the repository as `@ApplicationScoped class XxxRepository implements PanacheRepositoryBase<Xxx, UUID>`. | Panache's fluent API (`find()`, `page()`, `list()`) generates safe, parameterised HQL queries. It eliminates hand-written query strings for common CRUD operations, removing an entire injection surface class. | `ImporterRun.java` (`@Entity @Table(name = "importer_run")`); `ImporterRunRepository.java` (`implements PanacheRepositoryBase<ImporterRun, UUID>`). |
| **UUID v7 PK minted at the service layer, not by Hibernate** — set `@Id @Column(nullable = false, updatable = false) private UUID id;` and mint the value in the service before calling `persist()`. | UUID v7 is time-sortable, which keeps B-tree index inserts sequential. Minting at the service layer means the caller knows the ID before the row commits — useful for async job submission where the ID is returned to the client immediately. | `ImporterRun.java` line 62 (`@Id … private UUID id`); comment at lines 54–60 explaining the service-layer mint rationale. `ImporterRunService` mints via `UUID.randomUUID()` (v4 today; upgrade to v7 is tracked). |
| **`EnumType.STRING` for enum columns** — persist Java enums as their string name (`@Enumerated(EnumType.STRING)`), never as their ordinal. | Ordinal enums break silently if enum members are reordered. String enums are self-documenting and survive any reordering; a `CHECK (status IN (…))` constraint on the SQL side double-guards the allowed set. | `ImporterRun.java` line 69 (`@Enumerated(EnumType.STRING) … ImporterRunStatus status`) and line 95. |
| **SQL `text` for Java `String` enum discriminators** — use `text` in the DDL rather than `varchar(N)` so adding new enum values is a no-DDL change. | `varchar(N)` truncates silently if a future value is longer than N. `text` in Postgres has no length limit and no storage penalty over `varchar`. | `V1.11.1__add_importer_run_table.sql` — `source_kind text NOT NULL`. |
| **`jsonb` columns for structured blobs** — use `jsonb` (not `json` or `text`) when a column stores structured JSON that may be queried from SQL. | `jsonb` stores the parsed representation so `result_metadata->>'collectionAppId'` is a direct key lookup rather than a text scan. Used by ops tooling to introspect job state without application-layer decoding. | `ImporterRun.java` — `@Column(name = "result_metadata", columnDefinition = "jsonb")` at line 172; `request_payload jsonb` in the migration DDL. |
| **Partial indexes for filtered table scans** — create partial indexes with `WHERE status = 'X'` for all pattern-specific queries (e.g. "find all RUNNING stale jobs"). | Partial indexes are smaller and faster than covering indexes over the full table. For a job table that is 95% terminal rows, scanning only RUNNING rows for the stale-job reaper is dramatically cheaper with a partial index. | `V1.11.1__add_importer_run_table.sql` lines 97–104: `CREATE INDEX … WHERE status = 'RUNNING'`; lines 106–109: `… WHERE status IN ('SUCCEEDED','FAILED','CANCELLED')`. |
| **`CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS` in all plugin SQL migrations** — same idempotency guarantee as the core timeseries migrations. | Plugin migrations run via the same Flyway classpath scanner as core. A plugin that is upgraded then downgraded then upgraded again must not fail on the re-run. | `V1.11.1__add_importer_run_table.sql` line 31: `CREATE TABLE IF NOT EXISTS importer_run`. All four `CREATE INDEX` statements carry `IF NOT EXISTS`. |
| **`@Column(name = "snake_case")` explicit mapping on every field** — never rely on Hibernate's implicit `camelCase → snake_case` transformation. | Quarkus Hibernate has changed this default across versions. Explicit `@Column(name = …)` makes the SQL column name visible in the Java class and independent of the Hibernate naming strategy. | `ImporterRun.java` — every field carries `@Column(name = "…")` explicitly. |
| **Sensitive fields redacted before `requestPayload` insert** — strip `apiKey`, `password`, and similar credential fields from the request body JSON before writing to `request_payload`; persist credentials only in the encrypted `source_config` column. | `request_payload` is meant to be readable by ops tooling for debugging. Credentials in a plaintext jsonb column are a secret-in-cleartext finding. | `ImporterRun.java` comment on `requestPayload` at lines 177–185: "sensitive fields redacted by the service layer before insert". |

### 3.2 Error-handling pattern

- Panache `findById()` returns `null` for a missing row; wrap with `Optional.ofNullable()` in the repository and throw `NotFoundException` at the service layer.
- Do not swallow `PersistenceException` from plugin repositories. Let Quarkus map them to 500 responses with a traceId.

---

## 4. MongoDB (structured data payloads)

**Package root:** `backend/src/main/java/de/dlr/shepard/`
**Key files:** `common/mongoDB/MongoClientWrapper.java`, `data/structureddata/services/StructuredDataService.java`

### 4.1 Best-practices table

| Practice | Rationale | Representative example |
|---|---|---|
| **Inject `MongoDatabase` via `@Named("mongoDatabase")` producer** — never construct a `MongoClient` directly inside a service; always inject `MongoDatabase` from `MongoClientWrapper`'s `@Produces @Named("mongoDatabase")` method. | `MongoClientWrapper` parses the database name from the connection string (including handling the blank-name fallback) and initialises exactly once per application lifecycle. Direct construction bypasses this and creates multiple connections. | `MongoClientWrapper.java` — `@Produces @Named("mongoDatabase") public MongoDatabase getMongoDatabase()` at line 63. `StructuredDataService.java` — `@Inject @Named("mongoDatabase") MongoDatabase mongoDatabase;` lines 28–31. |
| **Collection names follow `TypeNameUUID` convention** — when creating a new MongoDB collection to hold payload data for a Neo4j container, name it `"<EntityTypeName>" + UUID.randomUUID().toString()`. | The name must be unique (UUIDs guarantee this), human-interpretable (the type prefix helps `mongosh` inspections), and stable for the lifetime of the container Neo4j node. Do not use sequential integers, which create race conditions when two containers are created concurrently. | `StructuredDataService.createStructuredDataContainer()` lines 35–38: `String mongoId = "StructuredDataContainer" + UUID.randomUUID().toString()`. |
| **Use MongoDB driver `Filters.*` DSL instead of raw JSON strings for programmatic queries** — build filter predicates with `Filters.and()`, `Filters.eq()`, `Filters.in()` etc. rather than assembling a `Document.parse(...)` string. | `Document.parse(string)` is injection-vulnerable when any part of the string comes from user input. The `Filters.*` DSL is type-safe and driver-encoded. | `StructuredDataService.java` — `import static com.mongodb.client.model.Filters.eq;` at line 3 (initial adoption); the legacy `Document.parse(mongoQuery)` path in `StructuredDataSearchService.findMatchingReferences()` lines 83–93 is retained for backward-compat but carries a TODO deprecation notice. Do not add new callers of the parse path. |
| **Strip underscore-prefixed keys from user payloads before insert** — filter out any JSON key whose name starts with `_` before inserting a user-supplied `Document`. | MongoDB's `_id` is the identity key; other `_`-prefixed keys are reserved for internal metadata. A user sending `{"_id": "x"}` would silently override the document's ObjectId. | `StructuredDataService.createStructuredData()` lines 54–59: remove keys starting with `"_"` from `toInsert` before insert. |
| **One collection per logical data container, not one per tenant** — do not multiplex multiple tenants or data objects into a single MongoDB collection. Each `StructuredDataContainer` Neo4j node owns exactly one MongoDB collection. | Mixing tenants in one collection forces client-side permission filtering of every query result and makes it impossible to drop a container cleanly (you'd need a filtered `deleteMany`). One collection per container means a container drop is `drop()`. | `StructuredDataService.createStructuredDataContainer()` and `StructuredDataContainerService.delete()` — one-to-one relationship between Neo4j node and MongoDB collection. |
| **`MongoDatabase` name from connection string, not hardcoded** — parse the database name from `quarkus.mongodb.connection-string` at startup; fall back to `"database"` only with a `WARN` log. | Operators must be able to rename the database in the connection string without requiring code changes. Hardcoded `"database"` would silently ignore the operator's chosen name. | `MongoClientWrapper.determineDatabaseName()` lines 42–50; `Log.warn()` fallback at line 45. |

### 4.2 Error-handling pattern

- Wrap every MongoDB operation that may throw `MongoException` in a try/catch at the service layer; convert to the appropriate HTTP status (404 for missing collection, 500 for driver-level errors) using the Shepard RFC 7807 mapper.
- `IllegalArgumentException` from `mongoDatabase.getCollection(invalidName)` must be caught at the call site; see `StructuredDataService.createStructuredData()` lines 42–48.

---

## 5. PostGIS / PostgreSQL (spatial data)

**Package root:** `plugins/spatial/src/main/java/de/dlr/shepard/data/spatialdata/`
**Migrations:** `plugins/spatial/src/main/resources/db/spatial/migration/V1.0.0__setup_spatial_data_tables.sql`

### 5.1 Best-practices table

| Practice | Rationale | Representative example |
|---|---|---|
| **GIST index on `GEOMETRY` column, created in the migration** — every `GEOMETRY`-typed column gets a `CREATE INDEX … USING GIST (column gist_geometry_ops_nd)` in the same migration that creates the table. | A GIST index is mandatory for acceptable performance on any bounding-box or proximity query. Without it, `&&& ST_3DMakeBox(…)` and `<<->>` KNN queries degrade to full-table scans. `gist_geometry_ops_nd` handles 3D geometries (POINT Z) correctly. | `V1.0.0__setup_spatial_data_tables.sql` — `CREATE INDEX spatial_data_point_position_idx ON spatial_data_points USING GIST (position gist_geometry_ops_nd)`. |
| **Hash-partition by `container_id` with 100 partitions** — partition the spatial points table by `HASH (container_id)` into 100 child tables at migration time. | Spatial operations on one container never lock or scan data for a different container. 100 partitions keeps the planner's pruning cost low while providing sufficient isolation for tens of thousands of containers. | `V1.0.0__setup_spatial_data_tables.sql` — `CREATE TABLE spatial_data_points … PARTITION BY HASH (container_id);` followed by the 100-iteration `DO $$ … END $$` loop. |
| **GIN index on `JSONB metadata` column** — index the `metadata jsonb` column with `USING GIN(metadata)` to support `@>` containment queries. | The metadata filter in `getByContainerId(…, metadataFilter, …)` uses the `@>` JSONB containment operator. Without the GIN index, this degenerates into a full table scan. | `V1.0.0__setup_spatial_data_tables.sql` — `CREATE INDEX spatial_data_points_metadata_idx ON spatial_data_points USING GIN(metadata)`. |
| **`NativeQueryStringBuilder` + named params for all geometry queries** — build spatial queries via the fluent `NativeQueryStringBuilder` and populate the `queryParameters` map, then call `query.setParameter()` from the map. Never inline coordinate values into the SQL string. | Direct coordinate injection is a SQL injection vector when coordinates come from user input. The builder uses `:x1`, `:y1`, `:z1` named placeholders bound by the JPA `setParameter()` call chain. | `NativeQueryStringBuilder.addKNNGeometryCondition()` — `queryParameters.put("x1", x1)` etc.; `SpatialDataPointRepository.getByKNN()` — `queryBuilder.getQueryParameters().forEach(query::setParameter)` lines 241–243. |
| **`@PersistenceUnit("spatial")` for the spatial DB, not the default unit** — inject the spatial `EntityManager` with the named persistence unit to isolate it from the timeseries / default Postgres connection. | PostGIS requires its own database schema or at minimum its own connection pool configuration. The named persistence unit allows independent Flyway migration history (`db/spatial/migration/`) and independent connection pool sizing. | `SpatialDataPointRepository.java` — `@PersistenceUnit("spatial") EntityManager entityManager` at line 28. |
| **Batch insert at `INSERT_BATCH_SIZE = 20 000`** — same constant as the timeseries substrate. Build one multi-row INSERT per batch. | Same rationale as §2: single-row inserts are prohibitively slow for bulk spatial ingest. | `SpatialDataPointRepository.java` — `private final int INSERT_BATCH_SIZE = 20000` at line 18; batch loop at lines 80–98. |
| **`ST_MakePoint(x, y, z)` for 3D geometry construction** — always use the PostGIS constructor function, never insert WKT or WKB strings directly. | `ST_MakePoint` validates the coordinate types at the Postgres function boundary. Inline WKT strings bypass type validation and can produce silent geometry malformation when user-supplied coordinates have swapped axes. | `SpatialDataPointRepository.insert()` — `"ST_MakePoint(%f, %f, %f)"` using `String.format(Locale.US, ...)` at line 47 (note: `Locale.US` prevents decimal-comma localisation bugs in European locales). |
| **`Locale.US` for `String.format` with floating-point geometry** — every `String.format` call that embeds `%f` coordinates must pass `Locale.US`. | The default `Locale.getDefault()` on a German-locale host formats `3.14` as `3,14`. PostGIS rejects comma-decimal WKT, causing every geometry insert to fail in production environments. | `SpatialDataPointRepository.insert()` and `insert(long, SpatialDataPoint[])` — both use `String.format(Locale.US, …)`. |
| **`@Timed` on every public repository method** — same policy as §2 timeseries. | Consistent instrumentation for all spatial operations in Micrometer. | `SpatialDataPointRepository` — `@Timed(value = "shepard.spatial-data.insert")` at line 31, `@Timed(value = "shepard.spatial-data.query-by-bounding-box")` at line 161. |

### 5.2 Error-handling pattern

- `entityManager.createNativeQuery(sql).executeUpdate()` returns a row count; throw `RuntimeException("SpatialData was not stored in database.")` if count ≤ 0 rather than silently swallowing a failed insert.
- `@RequiresDatabase("spatial")` on the REST layer signals graceful 503 degradation when the PostGIS extension is unavailable (A1c pattern).

---

## 6. Garage / MinIO S3 (file storage)

**Package root:** `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/`
**SPI definition:** `backend/src/main/java/de/dlr/shepard/storage/FileStorage.java`

### 6.1 Best-practices table

| Practice | Rationale | Representative example |
|---|---|---|
| **Implement `FileStorage` SPI, not a bespoke service** — all file storage adapters implement `de.dlr.shepard.storage.FileStorage` and register via CDI `@ApplicationScoped`. The `FileStorageRegistry` resolves the active adapter by `id()`. | The SPI boundary ensures the rest of Shepard never imports `S3FileStorage` directly. Callers are insulated from the choice of storage backend. Changing from GridFS to S3 is a config key change, not a code change. | `S3FileStorage.java` — `@ApplicationScoped public class S3FileStorage implements FileStorage`. `FileStorage.java` — full SPI contract with Javadoc explaining the design. |
| **`isEnabled()` guards on every public method** — call `requireEnabled()` (which throws `StorageProviderUnavailableException`) at the top of every `put()`, `get()`, `delete()`, and presign method. | If the bucket config is absent, the adapter self-disables. A misconfigured adapter that silently no-ops on writes would produce corrupt data (metadata written but no bytes stored). The explicit guard converts the misconfiguration into a clear 503. | `S3FileStorage.requireEnabled()` at lines 401–407; called at the top of `put()`, `get()`, `delete()`, `presignedUploadUrl()`, `presignedExportUrl()`, `presignedDownloadUrl()`. |
| **Locator format `"<containerMongoId>/<uuid>"`** — the opaque string returned by `put()` encodes the MongoDB container ID and a fresh UUID, separated by `/`. The bucket name is NOT in the locator. | The locator is stored in Neo4j and must survive a bucket rename or migration. Encoding the bucket in the locator would couple the persisted data to a deploy-time topology decision. | `S3FileStorage.put()` — `String key = request.container() + "/" + uuid;` at line 209; Javadoc on `id()` explaining the locator shape. |
| **`providerId` routing check in `get()` and `delete()`** — verify `locator.providerId().equals(ID)` before dispatching; throw `StorageException` if there is a mismatch. | A locator minted by GridFS (e.g. `"gridfs:containerMongoId:fileOid"`) must never be passed to the S3 adapter. The routing check catches `FileStorageRegistry` bugs before they cause data corruption or silent 404s. | `S3FileStorage.requireS3Locator()` at lines 409–415; called in `get()` and `delete()`. |
| **Idempotent `delete()` — swallow `NoSuchKeyException`** — `delete()` must succeed (return normally) when the key does not exist. `NoSuchKeyException` is a no-op, not an error. | The `FileStorage.delete()` contract requires idempotency so that a retry after a partial failure (e.g. Neo4j updated but S3 delete timed out) succeeds cleanly. | `S3FileStorage.delete()` lines 276–281: `catch (NoSuchKeyException e) { // Idempotent contract: missing key is a no-op. }`. |
| **Bucket auto-creation via `headBucket → createBucket` race-safe pattern** — `ensureBucketExists()` calls `headBucket()`; if it throws `S3Exception`, attempts `createBucket()`; swallows `BucketAlreadyOwnedByYouException` and `BucketAlreadyExistsException`. | Multiple Shepard instances may start simultaneously. Without the race-safe swallow, the second instance to start would fail with `BucketAlreadyExistsException` and mark itself disabled. | `S3FileStorage.ensureBucketExists()` lines 174–191. |
| **Deploy-time-only config keys for storage topology** — `shepard.files.s3.endpoint`, `shepard.files.s3.bucket`, etc. are deploy-time-only config keys (not runtime-mutable `:*Config` entities) per the `CLAUDE.md` "cluster identity / topology" exception. | Switching the storage backend mid-runtime would orphan any in-flight write whose bytes were directed to the old endpoint. This is the one class of knobs that must not be runtime-mutable. | `S3FileStorage.java` — `@ConfigProperty(name = "shepard.files.s3.endpoint", defaultValue = "")` etc. at lines 87–103; Javadoc citing the `CLAUDE.md` exception. |
| **Presigned URL for direct client upload/download (FS1c)** — implement `presignedUploadUrl()` and `presignedDownloadUrl()` to return `Optional<URI>`, letting clients bypass the JVM for byte transfer. Return `Optional.empty()` when the adapter does not support presigning (GridFS). | Routing file bytes through the Quarkus JVM adds latency and memory pressure proportional to file size. Large files (NDT scans, HDF5, video) should never transit the application tier. The `Optional.empty()` fallback lets the REST layer degrade gracefully to direct upload for adapters that don't support presigning. | `S3FileStorage.presignedUploadUrl()` lines 303–327; `FileStorage.presignedUploadUrl()` default returning `Optional.empty()` at lines 131–135. |
| **`exports/` prefix for transient export blobs** — when uploading a temporary export artifact (RO-Crate ZIP, NDJSON dump), key it as `"exports/" + uuid`. Advise operators to set a 24-hour lifecycle rule on the `exports/` prefix. | Export objects accumulate indefinitely without a lifecycle rule. The prefix convention makes it trivial to write a single S3 lifecycle policy (`exports/*` → expire after 24h) without affecting payload objects. | `S3FileStorage.presignedExportUrl()` — `String objectKey = "exports/" + key;` at line 342; Javadoc and `docs/reference/file-storage.md` reference. |

### 6.2 Error-handling pattern

- Map `NoSuchKeyException` → `StorageNotFoundException` (caller maps to 404).
- Map `S3Exception` with `RESOURCE_NOT_FOUND` error code → `StorageProviderUnavailableException` (caller maps to 503 with `Retry-After`).
- Every method that may fail storage-tier I/O must propagate `StorageException`; callers must not swallow it — the REST layer converts to RFC 7807 problem JSON.
- Do not let storage failures propagate through the `ProvenanceCaptureFilter` (secondary writes are fire-and-forget per `CLAUDE.md`).

---

## Cross-cutting patterns

These patterns apply to all six substrates.

| Pattern | Rationale | Where enforced |
|---|---|---|
| **`appId` as the universal cross-substrate identity** — every new persisted entity in any substrate gets a UUID v7 `appId` field. Never expose database-internal IDs (Neo4j node IDs, MongoDB ObjectIds, TimescaleDB PKs, Postgres serials) across substrate boundaries. | Internal IDs are implementation details. Cross-substrate queries (e.g. "find all annotations for this S3 file") must use a shared, stable identifier that every substrate can carry. | `HasAppId` interface (Neo4j); `appId UUID NOT NULL` column in SQL migrations (`V1.11.0`, `V1.11.1`); `oid` field in `AbstractMongoObject`; `StorageLocator` carrying the container mongoId as the cross-substrate pointer for files. |
| **Additive schema evolution — new columns/properties are nullable first** — never add a `NOT NULL` column without a default or a preceding backfill step. Never add a `NOT NULL` constraint on an existing column before backfilling every row. | Non-nullable columns without a default require a full-table-lock backfill, which is incompatible with zero-downtime deploys. | Neo4j: constraint-only (no NOT NULL on new properties). PostgreSQL: `ADD COLUMN IF NOT EXISTS` + nullable → backfill → `SET NOT NULL` (see `V1.11.0`). MongoDB: document-level null-check at read time. |
| **`@Timed` on every repository/data-access method** — use Micrometer's `@Timed` with a dot-separated `"shepard.<substrate>.<operation>"` name. | Enables latency tracking per operation, per substrate, in Prometheus without custom instrumentation. The naming convention (`shepard.timeseries-data-point.batch-insert`, `shepard.spatial-data.insert`) follows the Micrometer hierarchy pattern. | `TimeseriesDataPointRepository`, `SpatialDataPointRepository`. Adopt in all new repository classes. |
| **Migrations are idempotent and fail-fast** — all migration files use `IF NOT EXISTS` (or `IF EXISTS` for drops); any non-idempotent statement is wrapped in a guard. Any error aborts startup via `MigrationsException` propagation. | Idempotency allows safe re-runs after partial failure. Fail-fast prevents silent partial migrations from leaving the database in an inconsistent state that causes runtime errors hours later. | `MigrationsRunner.apply()` propagates `MigrationsException` (A1e). Every cypher and SQL migration in this fork carries `IF NOT EXISTS`. |
