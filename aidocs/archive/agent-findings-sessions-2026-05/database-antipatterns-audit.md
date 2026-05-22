# Database anti-pattern audit — Neo4j + Postgres/Hibernate + MongoDB/GridFS + PostGIS + Garage S3

**Date:** 2026-05-22
**Scope:** Established anti-patterns Shepard has accidentally implemented in
each backing data store. Companion to (not duplicate of) the forward-looking
schema research in `aidocs/agent-findings/timescaledb-schema-research.md`.
**Author:** anti-pattern hunter — findings only, no design.

This is a grep-driven audit against the canonical anti-pattern catalogues
for each substrate. Where a finding overlaps with the TimescaleDB ingest
report, it is cross-referenced and not restated.

---

## §1. Neo4j (Neo4j-OGM, Spring-Data-style entities)

### §1.1 Findings — accidentally implemented anti-patterns

**A. Map-of-properties on the hottest node-type ("graph EAV", a.k.a. attribute bag).**
Severity: **MAJOR**.
- Where: `backend/src/main/java/de/dlr/shepard/common/neo4j/entities/AbstractDataObject.java:21-23`
  ```java
  @ToString.Exclude
  @Properties(delimiter = "||")
  private Map<String, String> attributes;
  ```
  Neo4j-OGM `@Properties` flattens the map into per-node properties keyed by
  the map keys; on every `:DataObject` and `:Collection` in the system. This
  is the graph-DB analogue of the EAV anti-pattern: arbitrary keys live on
  the node with no schema, no constraint, no index plan, and they are
  invisible to label/property indexes.
- Concrete example: the LUMEN seed sets `propellant`, `bench`,
  `test_engineer` on every TR-NNN DataObject (15 nodes); MFFD seed does the
  same with `processStep`, `materialBatch`. Discoverability queries like
  "all DataObjects with `propellant = LOX/LH2`" require a full label scan +
  property filter — no index can help.
- Impact: writes are cheap, reads at scale are not. Worse, attributes are
  un-typed `String`s, so range/numeric predicates are impossible without
  casts and there is no enforcement that two DataObjects mean the same
  thing by "propellant".
- Canonical name: **Entity-Attribute-Value (EAV)**, applied to graph nodes.
  See Karwin, *SQL Antipatterns* (Pragmatic Bookshelf, 2010), ch. 6;
  Robinson, Webber & Eifrem, *Graph Databases* 2nd ed., ch. 4 — explicitly
  warns against the attribute-bag pattern on graph nodes because it
  defeats the whole point of using a graph DB.
  https://neo4j.com/docs/cypher-manual/current/syntax/maps/ (notes properties
  are not first-class — you can't traverse them, you can only filter on
  them).
- Remediation reference: hoist the keys that have an established
  vocabulary (status, processStep, propellant) to typed properties or to
  typed relationships toward a `:ControlledTerm` node. The SHACL-as-source-
  of-truth direction recorded in `feedback_shacl_single_source_of_truth.md`
  is exactly this fix at the next level up.

**B. String-typed enum on the gate-keeping field.**
Severity: **MAJOR**.
- Where: `AbstractDataObject.java:19` — `private String status;` (free-text).
- Same shape on `ImportPlan.java` `status` (line 95-101 commented values
  `VALID/EXPIRED/USED/INVALIDATED`) and on `Activity.java` `actionKind` /
  `targetKind` (free-text strings).
- Impact: typo-tolerant data path. A future status `"REWORK"` vs
  `"Rework"` vs `"rework"` becomes a real bug — there's no enum
  constraint at the graph layer, no `CHECK`, no `IS IN`-style guard. The
  manufacturing-quality agent's audit-readiness finding (`aidocs/agent-
  findings/manufacturing-quality.md`) lands on this exact gap.
- Canonical name: **stringly-typed enum**, sometimes "magic strings".
  See Fowler, *Refactoring* 2nd ed., "Replace Type Code with Subclasses";
  Neo4j 5.x docs recommend property-existence constraints or relationship
  types over string-coded enums.
  https://neo4j.com/docs/cypher-manual/current/constraints/syntax/#syntax-property-existence-constraints
- Remediation: a property-existence + value-constraint pair on Neo4j 5
  (Enterprise feature) **or** typed-relationship redesign (status as
  `(:DataObject)-[:HAS_STATUS]->(:Status {name:'READY'})`). The 2024
  V49 bootstrap of an internal semantic repository already gives us a
  vocabulary node for this; nothing on the DataObject side consumes it.

**C. Stored JSON-as-property (graph node carrying serialised structure).**
Severity: **MAJOR**.
- Where: `backend/src/main/java/de/dlr/shepard/v2/importer/entities/ImportPlan.java`
  ```java
  /** JSON-serialized {@code ImportPlanIO.ImportSummaryIO} (avoids nested Neo4j complexity). */
  @Property("summaryJson")
  private String summaryJson;
  ...
  @Property("warningsJson")
  private String warningsJson;
  ```
  Two fat string properties carry serialised JSON because "nested Neo4j
  complexity" was a stated tradeoff. This is the **JSON-as-blob-on-a-row**
  anti-pattern in a graph DB — the data is opaque to Cypher; you can't
  filter, you can't index, you can't UNWIND it without app-side parsing.
- Canonical name: **JSON-as-Blob** (PostgreSQL Wiki "Don't Do This" lists
  the SQL flavour; the rule transfers verbatim to graph nodes).
  https://wiki.postgresql.org/wiki/Don%27t_Do_This#Don.27t_use_serial
  (same wiki page, section on JSON-on-everything).
- Remediation reference: split the validated-warning rows into child
  nodes (`:ImportPlanWarning {code, message, severity}`) on the
  `:ImportPlan` — the very next ImportPlan-improvement PR.

**D. `MERGE` against a property without uniqueness constraint (race-on-create).**
Severity: **MINOR** (the property is appId-shaped in most cases, which **is**
constrained).
- Where: `OrphanPermissionsBackfillContext.java:` `MERGE (ctx:_ShepardMigrationContext) SET ctx.defaultOwner = $u`
  — there is no uniqueness constraint on `:_ShepardMigrationContext`. If
  this MERGE ran from two concurrent startup paths (operationally
  unlikely — migrations are serialised by Flyway), it would create two
  `:_ShepardMigrationContext` nodes. The migration is meant to run once,
  so the race window is narrow, but the **pattern** is the documented
  Neo4j anti-pattern.
- Other MERGEs (`ShepardTemplateDAO.java:131,168,185,216`,
  `PublicationDAO.java:175`) target relationships, not nodes — those are
  safe; relationship-MERGE on a unique node pair is the idiomatic shape.
- Canonical name: **MERGE without uniqueness constraint**, see Neo4j
  knowledge base. https://neo4j.com/developer/kb/understanding-how-merge-works/
- Remediation: add `CREATE CONSTRAINT … FOR (n:_ShepardMigrationContext)
  REQUIRE n.singleton IS UNIQUE` and `MERGE (ctx:_ShepardMigrationContext {singleton:true})`.

**E. Deeply-nested `OPTIONAL MATCH` chain on the collection-detail read.**
Severity: **MAJOR** (this is a hot read path).
- Where: `backend/src/main/java/de/dlr/shepard/context/collection/daos/DataObjectDAO.java:480-506` —
  three sibling `OPTIONAL MATCH` legs against the same `:DataObject`
  spider out to `TimeseriesReference`, `FileBundleReference`,
  `StructuredDataReference`. Each leg is independent, so Cypher evaluates
  them in turn and the planner is forced into a Cartesian-product over
  the cross-product of leg sizes. On a MFFD-style DataObject with 4-6 TS
  references + 2-3 file references + 0-1 SD references this is still
  cheap; on a DataObject with 50 file refs and 50 TS refs this becomes
  2500 rows multiplied by remaining matches.
- `CollectionDAO.java:120-121` has the same shape one level up
  (`Collection → DataObject → BasicReference`).
- Canonical name: **Cartesian product / cross-product explosion via
  unrelated OPTIONAL MATCH legs**.
  https://neo4j.com/developer-blog/cypher-anti-patterns-merge/
  https://neo4j.com/docs/cypher-manual/current/clauses/optional-match/#cypher-optional-match-pitfalls
- Remediation: split into separate queries each with a `WITH d AS d
  LIMIT 1` reseal, or use `CALL { … }` subqueries (Neo4j 4.4+) to
  evaluate each leg in its own context.

**F. Bidirectional `@Relationship` with both incoming + outgoing list on
the same node = OGM-traversal cost-double.**
Severity: **MINOR**.
- Where: `context/collection/entities/DataObject.java:30-34` — successors
  and predecessors both declared, `:has_successor` walked from both ends.
- Impact: every `:DataObject` load with default fetch depth pulls both
  directions; an OGM "fetch the node with its relationships" call loads
  twice the data of a one-directional model. The model is correct (the
  graph is bidirectional) but the entity-side cost is paid even when
  callers want only one direction.
- Canonical name: **bidirectional fetch overhead** — Neo4j-OGM is not
  unique here; it's the same shape as Hibernate `@OneToMany` mirrored
  with `@ManyToOne` and both fetched eagerly. The OGM
  `LoadStrategy.SCHEMA_LOAD_STRATEGY` default plus `@Relationship` on
  both sides triggers it.
- Remediation reference: defer; the directionality is load-bearing for
  the provenance graph UI; the fix is direction-aware repository methods
  rather than entity refactor.

### §1.2 Not-found — anti-patterns checked and ruled out

- **Graph supernode.** None of the singleton config nodes (`:InstanceConfig`,
  `:SemanticConfig`, `:UnhideConfig`, `:AiCapabilityConfig`, `:SqlTimeseriesConfig`,
  `:InstanceRorConfig`) carry runtime-growing relationships — they're
  config singletons with handful-of-properties shape. `:Collection ─[:has_dataobject]→ :DataObject`
  is one edge per DataObject and at MFFD/LUMEN scale a Collection has
  ≤15-50 children — far below the Neo4j-documented supernode threshold
  of "tens of thousands of relationships" [neo4j.com/developer-blog/modeling-designing-relationships-neo4j/].
  `:User ─[:WAS_ASSOCIATED_WITH]→ :Activity` will eventually grow without
  bound for the original operator — that is the **suspected** entry in §1.3.
- **Missing label index.** `V11` ships `CREATE CONSTRAINT … REQUIRE n.appId
  IS UNIQUE` for every node type touched by callers; uniqueness constraint
  is auto-indexed; queries by `appId` hit indexes. Fulltext index for
  `:Resource` labels (V44, V50, V51) is correctly used.
- **Stored procedure / APOC abuse.** No APOC functions in hot paths; the
  only APOC use is the V2/V23 migration runners (one-shot, not in the
  serving path).

### §1.3 Suspected but inconclusive

- **`:User → :Activity` will become a supernode for operators with high
  request volumes.** With PROV1a capturing every authenticated POST/PUT/
  PATCH/DELETE, a single MFFD ingest job creates ~12 000 `:Activity` rows;
  all of them dangle off the importing user's node via `:WAS_ASSOCIATED_WITH`.
  Multiply by months of operator activity and the node passes the
  supernode threshold.
  Query to confirm: `MATCH (u:User)-[:WAS_ASSOCIATED_WITH]->(:Activity)
  WITH u, count(*) AS n WHERE n > 10000 RETURN u.username, n;`

---

## §2. Postgres / Hibernate (`timeseries`, `timeseries_data_points`,
   `importer_run`, plus the Panache classes in plugins)

The TimescaleDB-specific ingest anti-patterns (multi-VALUES INSERT with
`prepareThreshold=0`, ON-CONFLICT-DO-UPDATE on compressed chunks, no
continuous aggregates) are already documented in
`aidocs/agent-findings/timescaledb-schema-research.md §2.1, §2.2, §2.6`.
**Not restated here.** The findings below are *additional* Postgres-side
anti-patterns those reports do not cover.

### §2.1 Findings

**A. Native SQL with string-formatted user input — SQL injection-shape risk
in the spatial plugin.**
Severity: **CRITICAL**.
- Where:
  - `plugins/spatial/src/main/java/de/dlr/shepard/data/spatialdata/repositories/NativeQueryStringBuilder.java:41-43, 65, 109-127`
    — every `whereCondition`, `jsonContainsCondition`, and
    `jsonFilterCondition` interpolates user input with `String.format`
    (`'%s'`-quoted), not `setParameter`.
  - `plugins/spatial/src/main/java/de/dlr/shepard/data/spatialdata/repositories/NativeInsertStatementBuilder.java` +
    `SpatialDataPointRepository.java:44-56,80-101` — every INSERT row
    embedded as `String.format` against the raw values:
    ```java
    .addValues(String.format(Locale.US,
       "%d, '%s', ST_MakePoint(%f, %f, %f), CAST('%s' AS JSONB), CAST('%s' AS JSONB)",
       containerId, data.getTime(), ..., metadata, measurements));
    ```
  - The `key` field of `FilterCondition` (free-text, user-supplied via
    the `/v2/` spatial query API) is concatenated into the SQL via
    `String.format` at `NativeQueryStringBuilder.java:116-123`.
- Concrete attack: a `FilterCondition.key = "a'); DROP TABLE
  spatial_data_points; --"` would break out of the JSONB-path literal.
  The keys are not validated against any whitelist or regex.
- Canonical name: **SQL Injection (CWE-89)**, the OWASP A03:2021
  *Injection* category, top-3 web-application risk for 20+ years.
  https://owasp.org/Top10/A03_2021-Injection/
  https://wiki.postgresql.org/wiki/Don%27t_Do_This#Don.27t_use_string_concatenation_for_SQL_assembly
- Remediation: all `addWhereCondition`/`addJsonFilterConditions` calls
  must move to `setParameter` placeholders. The two `addBSGeometryCondition`
  / `addAABBGeometryCondition` helpers in the same file already use
  parameterised binds — the asymmetry inside one class makes the fix
  unambiguous. **This is the single highest-severity finding in the
  audit.**

**B. JSONB as the de-facto schema for `metadata` and `measurements` —
  EAV-in-Postgres pattern.**
Severity: **MAJOR**.
- Where: `plugins/spatial/src/main/java/de/dlr/shepard/data/spatialdata/model/SpatialDataPoint.java:44-61` —
  ```java
  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> metadata;

  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> measurements;
  ```
  GIN index on metadata exists (`V1.0.0__setup_spatial_data_tables.sql:30-32`)
  but no index on `measurements`. Worse, filter predicates on measurements
  go through `jsonb_typeof(... #> '{...}') = 'number' AND (... #>> '{...}')::NUMERIC <op> <val>`
  (`NativeQueryStringBuilder.java:114-127`) — this cannot use the GIN
  index even if one existed; it's a per-row JSON path walk + cast.
- Canonical name: **JSONB-as-EAV** / "wide JSON column as schema replacement".
  See PostgreSQL Wiki "Don't Do This": *"Don't use jsonb where you should
  use a table."*
  https://wiki.postgresql.org/wiki/Don%27t_Do_This (section on JSONB
  misuse); Vlad Mihalcea, https://vladmihalcea.com/postgresql-jsonb-data-type/
  — discusses when JSONB is and isn't the right tool.
- Remediation: keep JSONB for the truly-unstructured shape but project
  the numeric "measurement value" rows to a sibling fact table
  (`spatial_measurement {container_id, time, key TEXT, value DOUBLE}`)
  with a B-tree on `(container_id, key, value)` — same shape as the
  TimescaleDB `timeseries_data_points` already uses.

**C. `prepareThreshold=0` + parameterised multi-VALUES INSERT — every batch
re-parsed and re-planned.**
Severity: **MAJOR**.
- Cross-reference: TimescaleDB research §2.1 covers this. The same anti-
  pattern exists in the spatial plugin (`SpatialDataPointRepository.java`
  `INSERT_BATCH_SIZE = 20000`) but is **worse** because the spatial plugin
  doesn't have an NDJSON-COPY alternative endpoint — every spatial INSERT
  goes through this path.
- Canonical name: **Re-prepared parameterised batch** — pgJDBC
  documentation, https://jdbc.postgresql.org/documentation/server-prepare/

**D. Skip implemented as `AND id %% N = 0` instead of `OFFSET`.**
Severity: **MAJOR** (correctness bug, not just performance).
- Where: `NativeQueryStringBuilder.java:135-139`
  ```java
  public NativeQueryStringBuilder addSkipClause(Integer skip) {
    if (skip == null) return this;
    skipClause = " AND id %% %d = 0".formatted(skip);
    return this;
  }
  ```
  This is **not pagination**; this is "every Nth row where N is the skip
  value". A caller asking `skip=10, limit=100` gets every 10th row up to
  100 returned, not "rows 10-110". The `id` is the surrogate identity
  PK — there is no guarantee of contiguity (deletes break the
  modulo distribution). This is a behavioural bug shipped as a
  "pagination" knob.
- Canonical name: not an established name; closest match is **misused
  modulus-as-pagination**. The PostgreSQL canonical pagination patterns
  are `LIMIT/OFFSET` (simple) and keyset/seek pagination
  (https://use-the-index-luke.com/no-offset).
- Remediation: replace with `OFFSET :skip LIMIT :limit` or keyset.

**E. Selecting `SELECT *` from a hypertable / partitioned table for the
list-by-container read path.**
Severity: **MINOR**.
- Where: `SpatialDataPointRepository.java:26 ALL_COLUMNS_STRING = new String[] { "*" }`
  used by all 5 query methods. The table has `metadata` + `measurements`
  as JSONB; selecting `*` ships the full JSONB blobs on every row even
  when the caller only wants `(time, position)` for a chart.
- Canonical name: **`SELECT *` anti-pattern**.
  https://wiki.postgresql.org/wiki/Don%27t_Do_This#Don.27t_use_SELECT_.2A
  *"In production code, this is almost always a bug … just spell out the
  columns you actually need."*

**F. `@Transactional(REQUIRES_NEW)` on a service method called from the
hot ingest path.**
Severity: **MINOR**.
- Where: `data/timeseries/services/TimeseriesService.java:249, 270` — two
  `REQUIRES_NEW` methods. `REQUIRES_NEW` suspends the caller's
  transaction and opens a new connection from the pool. Combined with
  the `DEFAULT_POOL_SIZE=20` PgBouncer setting (TS research §2.3) this
  doubles the pool-occupancy fingerprint of any ingest path that crosses
  the `REQUIRES_NEW` boundary.
- Canonical name: **`REQUIRES_NEW` propagation pitfall**, Mihalcea,
  *High-Performance Java Persistence*, ch. 7;
  https://vladmihalcea.com/a-beginners-guide-to-transaction-propagation-strategies/

### §2.2 Not-found

- **Classic Hibernate N+1.** No `@OneToMany`/`@ManyToMany` collections on
  any Panache entity in the repo (`TimeseriesEntity`, `MigrationProgress`,
  `ImporterRun`, `SpatialDataPoint`). All Postgres reads are native SQL
  via `EntityManager.createNativeQuery`; the N+1 trap is bypassed
  entirely. Counter-intuitive: the *reason* the trap is absent is that
  Hibernate is barely used as an ORM — Panache entities are essentially
  one-table data classes with explicit native queries.
- **`FetchType.EAGER` cascading.** No `@OneToMany` declarations to mark
  EAGER/LAZY; not applicable for the same reason.
- **PgBouncer transaction-pool conflict with `SET LOCAL` / cursors.** No
  cursor / `SET LOCAL` calls in the codebase; `prepareThreshold=0` is the
  correct switch for transaction-pool mode and is already set
  (`docker-compose.override.yml:113`).
- **Cartesian-product join via simultaneous fetches.** No JPA fetch graphs
  — see N+1 not-found above.

### §2.3 Suspected but inconclusive

- **Connection-pool starvation under concurrent ingest.** Suspected per
  TS research §2.3. Confirmation query: `psql -h pgbouncer -d pgbouncer
  -c "SHOW POOLS;"` during MFFD ingest — look for `cl_waiting > 0`.

---

## §3. MongoDB (file metadata documents + GridFS chunks)

**Active in production today** despite the migration-to-Garage roadmap
(ADR-0024). `infrastructure/docker-compose.yml` still spins up
`mongo:8.0` and the backend connection string is required. The
`GridFsFileStorage` SPI adapter wraps the legacy `FileService` exactly
as before — the only path that bypasses GridFS today is the file-s3
plugin.

### §3.1 Findings

**A. Mongo-collection-per-FileContainer — collection explosion.**
Severity: **MAJOR**.
- Where: `backend/src/main/java/de/dlr/shepard/data/file/services/FileService.java:48-52`
  ```java
  public String createFileContainer() {
    String oid = "FileContainer" + uuidHelper.getUUID().toString();
    mongoDatabase.createCollection(oid);
    return oid;
  }
  ```
  Every shepard FileContainer becomes a **separate Mongo collection**
  named `FileContainer<UUID>`. The MFFD seed alone creates ≥12
  containers; a multi-tenant DLR instance with ~hundreds of campaigns
  creates *thousands* of collections in a single Mongo database.
  WiredTiger keeps a per-collection file handle, system catalog entry,
  and metadata document. Mongo's official guidance recommends keeping
  collections-per-database in the low hundreds at most.
- Canonical name: **collection explosion** / "collection-per-tenant"
  anti-pattern. https://www.mongodb.com/blog/post/building-with-patterns-the-subset-pattern
  https://www.mongodb.com/developer/products/mongodb/schema-design-anti-pattern-massive-number-collections/
  *"Each collection in MongoDB requires a minimum of 8 KB just for
  metadata. Hundreds of collections is fine. Tens of thousands hurts."*
- Concrete impact: `db.stats()` and `db.runCommand('listCollections')` —
  which the admin-storage-overview endpoint already calls
  (`AdminStorageOverviewRest.java:117`) — slow linearly with collection
  count.
- Remediation: a single `file_metadata` collection with an indexed
  `container_id` field. The "drop the whole container = drop the Mongo
  collection" semantics (`NukeService.java`) need replacing with a
  filtered delete, but the cost is one operator-visible behaviour change.

**B. Sequential `ObjectId` insertion = hot shard tail (if the cluster is
ever sharded).**
Severity: **MINOR** (single-replica today; latent risk if sharding
considered).
- Where: every `bucket.uploadFromStream(...)` and `collection.insertOne(...)`
  in `FileService.java` gets a default Mongo `ObjectId` which is
  timestamp-prefixed; on a sharded cluster the last shard always
  receives the latest write.
- Canonical name: **Monotonically Increasing Shard Key**.
  https://www.mongodb.com/docs/manual/core/sharding-shard-key/#monotonically-changing-shard-keys
- Remediation: if/when sharding is considered, use a hashed shard key on
  `_id` or on `container_id`. Latent — flag, don't fix.

**C. App-side join over `file_metadata` × GridFS chunks (no `$lookup`).**
Severity: **MINOR**.
- Where: `FileService.java:104-112` reads `bucket.uploadFromStream`,
  then **separately** writes `collection.insertOne(doc)` to bookkeep the
  file in the FileContainer collection. The GridFS bucket lives in the
  default `fs.files`/`fs.chunks`; the bookkeeping doc lives in
  `FileContainer<UUID>`. There is no atomic join between the two on read;
  the get-payload path reads the FileContainer doc by `_id` then opens
  the GridFS stream by the embedded `fileMongoId`.
- Canonical name: **App-side join instead of `$lookup`**.
  https://www.mongodb.com/developer/products/mongodb/transactional-guarantees/
- Impact: small. The two reads are independent and the bookkeeping
  collection is the smaller side; this is only an issue at very high
  read fanout.

### §3.2 Not-found

- **Unbounded array growth on a document (16 MB doc-cap risk).** No
  document in the codebase carries an unbounded array; the legacy
  `AbstractMongoObject` shape is flat key-value. GridFS chunks are
  paged at 1 MiB by design (`FileService.java:30 CHUNK_SIZE_BYTES`) so
  the doc-cap risk isn't there.
- **GridFS chunks missing `{files_id: 1, n: 1}` index.** The MongoDB
  GridFS driver creates this index automatically on first write
  (per https://www.mongodb.com/docs/manual/core/gridfs/#the-files.chunks-collection)
  — the Java driver in this project (mongodb-driver 5.x) does the same.
  Verified absence-of-problem.
- **Manual `$lookup` instead of an embedded shape** when the join is
  hot. The file<→file-container join is cold (taken on download of one
  file at a time, not on listing) — no concern.

### §3.3 Suspected but inconclusive

- **The "drop a Mongo collection = drop the FileContainer" semantic is
  load-bearing on `NukeService`** but invisible to the cleaner-storage
  migration plan. Suspected impact on the Garage cutover: callers that
  delete via collection-drop must move to filtered-delete by
  `container_id`; the SPI side (`FileStorage.delete`) already handles
  per-object delete, so the path exists. Confirm by reading
  `NukeService.java:106 dropMongoCollections` against the planned
  Garage-only mode.

---

## §4. PostGIS (spatial plugin)

### §4.1 Findings

**A. `ST_3DDWithin` predicate without a bounding-box pre-filter — index
not used.**
Severity: **MAJOR**.
- Where: `NativeQueryStringBuilder.java:100-107` (`addBSGeometryCondition`):
  ```java
  geometryFilterCondition = " AND ST_3DDWithin(position, ST_MakePoint(:x1, :y1, :z1), :radius)";
  ```
  Compare with `addAABBGeometryCondition` (line 89-90) which uses `&&&`
  — the n-dimensional bounding-box overlap operator — which **is**
  index-aware. `ST_3DDWithin` alone cannot use the GIST index without
  a sibling bbox predicate to give the planner a startup filter.
- Canonical name: **Spatial-function predicate without bounding-box
  pre-filter.** The canonical PostGIS perf rule.
  https://postgis.net/workshops/postgis-intro/indexing.html
  *"Without an && operator alongside, the GiST index cannot help."*
  https://postgis.net/docs/performance_tips.html
- Remediation: rewrite to `position && ST_3DMakeBox(...)` (bbox of
  point ± radius) `AND ST_3DDWithin(position, point, radius)`. Two-line
  fix.

**B. Wrong index operator class for the workload.**
Severity: **MINOR** (medium impact; the chosen class is correct for the
3D workload, but the comment trail doesn't match).
- Where: `V1.0.0__setup_spatial_data_tables.sql:26-28` — `USING GIST
  (position gist_geometry_ops_nd)`. The `_nd` (n-dimensional) variant
  is required for the 3D points the plugin stores (per the comment in
  `SpatialDataPoint.java:42`). However, the KNN operator used in
  `addKNNGeometryCondition` is `<<->>` (the 3D KNN operator,
  available in PostGIS 2.2+), which **does** use `_nd`. So index +
  operator are aligned. Verified — **this is a not-found**, kept here
  because the original audit instruction asked to check.

**C. No CLUSTER on the GIST index for the spatial table.**
Severity: **MINOR**.
- Where: V1.0.0 migration — no `CLUSTER spatial_data_points USING
  spatial_data_point_position_idx`. The table is partitioned by hash
  on `container_id`; within a partition, rows are appended in time
  order, not spatial order. A `CLUSTER` would reorder rows by spatial
  locality, improving spatial-range scan cache locality. Probably not
  worth running at MFFD's load today, but the absence is documented.
- Canonical name: **un-clustered spatial table** — PostGIS workshop
  recommends `CLUSTER` for read-heavy spatial workloads.
  https://postgis.net/workshops/postgis-intro/clusterindex.html

**D. SRID = unspecified (default 0).**
Severity: **MINOR**.
- Where: `SpatialDataPoint.java:42` — `Geometry position` declared
  without an SRID. The `ST_MakePoint(x,y,z)` calls in
  `SpatialDataPointRepository.java:47, 86` produce SRID-0 geometries.
  V1.0.0 declares `position GEOMETRY` (no SRID typmod).
- Impact: every cross-SRID operation needs explicit `ST_SetSRID`. If
  spatial data ever needs to round-trip with GIS data in EPSG:4326,
  the migration is non-trivial.
- Canonical name: **Geometries without SRID** — PostGIS docs
  https://postgis.net/docs/using_postgis_dbmanagement.html#PostGIS_SRS

### §4.2 Not-found

- **GeoJSON-as-JSONB instead of a geometry column.** The plugin uses
  proper PostGIS `GEOMETRY` typed columns — verified at
  `SpatialDataPoint.java:42` (`Geometry position`) and
  `V1.0.0__setup_spatial_data_tables.sql:5` (`position GEOMETRY not null`).
  No GeoJSON-in-JSONB column for spatial data. This is the **single
  most important anti-pattern the plugin avoids**.
- **`ST_Transform` in a `WHERE` predicate.** Search for `ST_Transform`
  in `plugins/spatial/src/main/java/` returns zero hits in query
  builders.
- **GIST index on un-partitioned table.** Table is hash-partitioned
  by `container_id` into 100 partitions — each partition's GIST index
  is a separately-tuned plan.

### §4.3 Suspected but inconclusive

- **Are 100 hash-partitions overkill for current data volumes?** The
  spatial table has 100 partitions, but at typical Shepard scales (a
  handful of containers) most partitions are empty. The query planner
  still has to evaluate plan-time pruning on all 100. Confirm by
  `EXPLAIN ANALYZE` of `SELECT * FROM spatial_data_points WHERE
  container_id = X`. If "Partitions removed: 99" appears with
  meaningful planning time, reduce to 10 partitions.

---

## §5. Garage S3 (object storage, `file-s3` plugin)

### §5.1 Findings

**A. No bucket-level lifecycle policy for incomplete multipart uploads.**
Severity: **MAJOR**.
- Where: `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`
  — `ensureBucketExists()` (line 174-191) creates the bucket on demand
  but does not configure any lifecycle rules. The plugin issues both
  direct `putObject` (line 231) and presigned multipart PUTs (line
  318-324, `presignedUploadUrl`). A client that abandons a multipart
  upload mid-flight leaves stale parts in the bucket forever.
- Canonical name: **AbortIncompleteMultipartUpload missing** — AWS S3
  best practice, applies verbatim to Garage.
  https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpu-abort-incomplete-mpu-lifecycle-config.html
  https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/
  (Garage supports `lifecycle` only partially; configure operator-side).
- Remediation: ship a `bucket-lifecycle.xml` template in
  `docs/reference/file-storage.md` (already referenced from the code
  comment at S3FileStorage.java:333-336 for the exports/ prefix, but
  not for the main bucket).

**B. No encryption-at-rest configuration on the SDK side.**
Severity: **MAJOR** (for production deployments, not for the demo).
- Where: `S3FileStorage.java:211-218, 343-350` — `PutObjectRequest`
  built without `serverSideEncryption(...)` or any equivalent. Relies
  entirely on the storage backend's at-rest encryption configuration.
  Garage supports server-side encryption (per its docs) but is not
  enabled by default in the compose profile.
- Canonical name: **Plaintext-at-rest by default** — AWS S3 Security
  Best Practices, https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html
  *"Enable Amazon S3 server-side encryption with at-rest encryption."*
- Remediation: `reqBuilder.serverSideEncryption(ServerSideEncryption.AES256)`
  on every put. One-line per put site.

**C. No bucket versioning configured.**
Severity: **MINOR**.
- Where: `ensureBucketExists()` does not call `putBucketVersioning`.
  Operator-controlled, but the operator-runbook (referenced at
  `docs/reference/file-storage.md` per the code comment) should call
  it out.
- Canonical name: **Versioning-off-by-default** — AWS S3 Security
  Best Practices (same source).
- Remediation: document in operator runbook; do not auto-enable
  (versioning has storage-cost implications).

**D. `RequestBody.fromBytes(request.bytes().readAllBytes())` — full-file
buffering when `sizeBytes()` is unknown.**
Severity: **MAJOR** (silent OOM risk at TB scale).
- Where: `S3FileStorage.java:219-225`
  ```java
  if (request.sizeBytes() != null) {
    body = RequestBody.fromInputStream(request.bytes(), request.sizeBytes());
  } else {
    body = RequestBody.fromBytes(request.bytes().readAllBytes());
  }
  ```
  If a caller doesn't set `sizeBytes`, the entire payload is read into
  a single heap `byte[]`. The PV1a UI has a 100 MB confirm guard for
  the *user* path, but the SPI accepts arbitrary bytes — a future
  caller that forgets `sizeBytes` (the importer plugin? a scripted
  bulk upload?) will hit `OutOfMemoryError`.
- Canonical name: **Unbounded in-memory buffer for streaming I/O**.
  Not S3-specific; the SDK docs (https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/best-practices.html)
  explicitly warn against `readAllBytes()` for blob upload.
- Remediation: never fall through to the `fromBytes` branch — surface
  the missing-size as `StorageException` and let the caller buffer
  to a temp file if needed; or use the SDK's TransferManager which
  handles unknown-size streams without OOM.

**E. Object key contains the MongoDB legacy container id — naming-debt
crossover.**
Severity: **MINOR**.
- Where: `S3FileStorage.java:209` — `String key = request.container() + "/" + uuid`,
  where `request.container()` is the MongoDB container id from the
  legacy `FileContainer<UUID>` collection name. The S3 plugin
  inherits the MongoDB naming convention into the S3 key shape.
- Impact: when the Mongo migration completes, S3 keys still carry
  the `"FileContainer<UUID>"` prefix; a future "rename containers"
  operation can't be done by renaming the row alone.
- Canonical name: doesn't have an established name; closest is
  **embedded ID in object key**.
  https://aws.amazon.com/blogs/aws/amazon-s3-performance-tips-tricks-seattle-hiring-event/
  recommends opaque, randomly-distributed prefixes.

### §5.2 Not-found

- **Bucket-per-tenant explosion.** The plugin uses a **single bucket**
  configured via `shepard.files.s3.bucket` and prefixes objects with
  the container id. Correct shape per Garage + S3 best practice.
- **Tiny-file packing not implemented (yes, but this is correct).** For
  a researcher dataset, small files (e.g. metadata JSONs) are uploaded
  individually. Garage uses a different storage layout from S3 (data
  blocks vs. erasure-coded shards), and the Garage docs explicitly
  note that erasure-coded waste matters at <128 KB only. Most
  Shepard payloads are larger.
- **Path-style access misconfiguration.** Correctly defaults to
  `path-style-access=true` (line 102-103) — required for Garage.

### §5.3 Suspected but inconclusive

- **Multipart upload threshold left at SDK default (8 MB).** Garage's
  recommended threshold varies; check
  `shepard.files.s3.multipart-threshold` — not currently configurable.
  Confirm via load test of a 10 GB upload.
- **The `exports/` prefix lifecycle rule** is mentioned in the comment
  but not enforced by the plugin. An operator who never reads the
  runbook accumulates export ZIPs forever.

---

## §6. Cross-cutting summary

### §6.1 Top-3 anti-patterns ranked by severity × remediation effort

| Rank | Finding | Severity | Effort | Store |
|------|---------|----------|--------|-------|
| 1 | **SQL injection via `String.format` in spatial query/insert builders** (§2.1.A) | CRITICAL | Low (mechanical refactor to `setParameter`; 4-6 sites) | Postgres/PostGIS |
| 2 | **Mongo-collection-per-FileContainer explosion** (§3.1.A) | MAJOR | Medium (data migration + NukeService rewrite) | MongoDB |
| 3 | **EAV-on-graph (`AbstractDataObject.attributes`)** (§1.1.A) | MAJOR | Medium-High (touches every DataObject reader; coordinate with SHACL-as-source-of-truth direction) | Neo4j |

Honorable mentions:
- §5.1.D unbounded buffer (silent OOM)
- §4.1.A `ST_3DDWithin` without bbox prefilter
- §2.1.D `id % N = 0` as "skip" (correctness bug shipped)

### §6.2 Cross-cutting anti-patterns spanning ≥2 stores

**EAV (Entity-Attribute-Value) is the largest cross-cutting pattern in
Shepard.** It appears as:

| Store | Manifestation | Reference |
|-------|---------------|-----------|
| Neo4j | `AbstractDataObject.attributes: Map<String,String>` (`@Properties` flatten) | §1.1.A |
| Postgres | `SpatialDataPoint.metadata` + `.measurements` as `JSONB` | §2.1.B |
| Neo4j | `ImportPlan.summaryJson` + `.warningsJson` as serialised strings | §1.1.C |

All three are the same intent — "let callers attach arbitrary key-value
context to this row" — implemented in three different ways, each
defeating the schema it lives on. The SHACL-as-source-of-truth direction
in `feedback_shacl_single_source_of_truth.md` is the structural fix for
all three: domain "data data" goes into the SHACL graph; only
operational/identity fields stay typed on the substrate.

**Stringly-typed enums** also span two stores: `DataObject.status` (Neo4j),
`ImportPlan.status` (Neo4j), `Activity.actionKind` (Neo4j) on the graph
side; the `value_type` column on Postgres `timeseries` table is correctly
constrained via `CHECK in ('Boolean','Integer','Double','String')` — so
the issue is *Neo4j-side only* for now, but the discipline gap is shared
across the team's mental model.

**Native-SQL/Cypher with formatted strings** is a cross-cutting risk:
the spatial plugin's SQL injection (§2.1.A) and the Neo4j Cartesian-leg
problem (§1.1.E) both come from the same engineering pattern: hand-built
query strings without a query-shape abstraction. Both stores have a
canonical safer path (`setParameter` for JDBC, parameterised Cypher for
Neo4j), and both fixes would benefit from the same "query-builder must
not interpolate user input" code review check.

---

## §7. References (primary sources cited)

1. Karwin, B. *SQL Antipatterns: Avoiding the Pitfalls of Database
   Programming.* Pragmatic Bookshelf, 2010. — EAV chapter.
2. Robinson, I., Webber, J., Eifrem, E. *Graph Databases* 2nd ed.
   O'Reilly, 2015. — Property bag anti-pattern, supernode patterns.
3. Mihalcea, V. *High-Performance Java Persistence.* — N+1, transaction
   propagation. https://vladmihalcea.com/
4. **PostgreSQL Wiki — Don't Do This.**
   https://wiki.postgresql.org/wiki/Don%27t_Do_This
5. **OWASP Top 10 2021 — A03 Injection.**
   https://owasp.org/Top10/A03_2021-Injection/
6. **PostGIS Workshops — Indexing & Performance.**
   https://postgis.net/workshops/postgis-intro/indexing.html
   https://postgis.net/docs/performance_tips.html
7. **MongoDB — Schema Design Anti-Patterns.**
   https://www.mongodb.com/developer/products/mongodb/schema-design-anti-pattern-massive-number-collections/
8. **Neo4j — Cypher anti-patterns.**
   https://neo4j.com/developer-blog/cypher-anti-patterns-merge/
   https://neo4j.com/docs/cypher-manual/current/clauses/optional-match/
9. **AWS S3 Security Best Practices.**
   https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html
10. **Garage S3 compatibility reference.**
    https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/
11. Cross-reference: `aidocs/agent-findings/timescaledb-schema-research.md`
    (commit `5523fa25`) — TimescaleDB-specific ingest anti-patterns.

---

## §8. Methodology note

This audit is grep-driven, not metric-driven. Findings labelled
"CRITICAL" or "MAJOR" are confirmed by direct code reading; findings in
each §X.3 "Suspected" subsection require a runtime metric or log
snapshot to confirm and are listed with the exact query to run. The
"Not-found" subsections are equally important — they document the
anti-patterns Shepard *got right*, so a future audit doesn't waste
cycles re-checking them.

No code changes were made by this agent; this is a findings-only report
per `feedback_agent_worktree_must_commit.md` direct-write convention.
