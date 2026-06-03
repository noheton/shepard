---
id: DB-BP1
title: "Database Best Practices Catalogue"
stage: concept
last-stage-change: 2026-06-03
---

# Database Best Practices Catalogue

> Living reference. Each entry cites the canonical implementation with file path
> and line numbers. Add new examples by appending — never delete existing entries.
> When a pattern changes, add an amended entry and note the predecessor.

---

## 1. Neo4j (OGM + Cypher)

### 1.1 Parametrised Cypher queries — never interpolate user data

**Pattern.** All variable data is passed through a `Map<String, Object>` parameter
map; the query string contains only `$paramName` placeholders. String formatting
(`%s`, `+` concatenation) is used only for structural query *fragments* (clause
assembly, label names, relationship types) that are produced entirely from
server-controlled enum values — never from user-supplied strings.

```java
// CollectionDAO.java — findByAppId
public Collection findByAppId(String appId, String username) {
    String query =
      "MATCH (c:Collection {deleted: FALSE}) WHERE c.appId = $appId AND " +
      CypherQueryHelper.getReadableByQuery("c", username) +
      " WITH c " +
      CypherQueryHelper.getReturnPart("c");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
}
```

File: `backend/src/main/java/de/dlr/shepard/context/collection/daos/CollectionDAO.java`, lines 149–157.

The `getReadableByQuery` helper in `CypherQueryHelper` inlines the `username`
into the Cypher literal — this is safe because `username` comes from the verified
JWT subject, not from a request body. The data-layer `appId` (also untrusted) goes
through `$appId` bound parameters, not string interpolation.

**Why.** Cypher injection is the Neo4j equivalent of SQL injection. Binding parameters
means the query planner can also cache the execution plan, removing per-call parse
overhead.

---

### 1.2 `IF NOT EXISTS` guards on all constraints and indexes

**Pattern.** Every `CREATE CONSTRAINT` and `CREATE INDEX` in a migration file is
written with `IF NOT EXISTS`, making re-runs and dev-stack replay completely safe.
Rollback twins follow the `V<N>_R__*.cypher` naming convention.

```cypher
// V11__Add_appId_unique_constraints.cypher (abridged)
// Idempotent: IF NOT EXISTS is a no-op when the constraint already exists.

CREATE CONSTRAINT appId_unique_Collection IF NOT EXISTS
  FOR (n:Collection) REQUIRE n.appId IS UNIQUE;

CREATE CONSTRAINT appId_unique_DataObject IF NOT EXISTS
  FOR (n:DataObject) REQUIRE n.appId IS UNIQUE;
```

File: `backend/src/main/resources/neo4j/migrations/V11__Add_appId_unique_constraints.cypher`, lines 22–43.

Second example — additive index migration for `ImportPlan`:

```cypher
// V83__ImportPlan_manifestJson.cypher
// Idempotent: CREATE INDEX IF NOT EXISTS is safe to re-run.
CREATE INDEX import_plan_commit_id IF NOT EXISTS
FOR (p:ImportPlan)
ON (p.commitId);
```

File: `backend/src/main/resources/neo4j/migrations/V83__ImportPlan_manifestJson.cypher`, lines 13–16.

Third example — uniqueness-only constraint (no NOT NULL) so pre-migration rows
with `appId = null` pass the constraint (Neo4j 5 ignores nulls on uniqueness):

```cypher
// V96__Ntf1_NotificationTransport_scaffold.cypher
// Idempotent: CREATE CONSTRAINT ... IF NOT EXISTS is a no-op when the
// constraint already exists. Safe to re-run.
CREATE CONSTRAINT appId_unique_NotificationTransport IF NOT EXISTS
  FOR (n:NotificationTransport)
  REQUIRE n.appId IS UNIQUE;
```

File: `backend/src/main/resources/neo4j/migrations/V96__Ntf1_NotificationTransport_scaffold.cypher`, lines 15–29.

**Why.** A `CREATE CONSTRAINT` without `IF NOT EXISTS` fails on a DB where that
migration has already run (dev box, CI re-run, partial rollout). `IF NOT EXISTS`
makes every migration script idempotent and operator-runnable from `cypher-shell`
without state inspection.

---

### 1.3 Fresh OGM session per DAO call (`@RequestScoped`)

**Pattern.** Every DAO that extends `GenericDAO` is annotated `@RequestScoped`.
`GenericDAO`'s constructor calls `NeoConnector.getInstance().getNeo4jSession()`,
which calls `sessionFactory.openSession()` — opening a *new* OGM session for each
CDI request scope. Sessions are never shared across requests.

```java
// GenericDAO.java — constructor opens session
protected GenericDAO() {
    session = NeoConnector.getInstance().getNeo4jSession();
}

// NeoConnector.java — always opens a fresh session
public Session getNeo4jSession() {
    if (sessionFactory == null) {
        return null;
    }
    return sessionFactory.openSession();   // new Session per call
}
```

File: `backend/src/main/java/de/dlr/shepard/common/neo4j/daos/GenericDAO.java`, lines 32–34.
File: `backend/src/main/java/de/dlr/shepard/common/neo4j/NeoConnector.java`, lines 201–207.

Representative DAO annotation:

```java
// CollectionDAO.java
@RequestScoped
public class CollectionDAO extends VersionableEntityDAO<Collection> { ... }
```

File: `backend/src/main/java/de/dlr/shepard/context/collection/daos/CollectionDAO.java`, lines 16–17.

**Why.** The OGM session carries an identity map (first-level cache). Sharing a
session across requests can serve stale cached objects to different users. `@RequestScoped`
guarantees every HTTP request gets a clean session, eliminating cross-request
cache pollution without the overhead of opening a session per DAO method.

---

### 1.4 Parameterised delete query with `CALL {}` subqueries to avoid Cartesian explosion

**Pattern.** When deleting a parent node and cascading soft-deletes to children of
multiple types, use independent `CALL {}` subqueries rather than chained
`OPTIONAL MATCH` clauses. Chained `OPTIONAL MATCH` causes row-count multiplication
(Cartesian product) when a parent has many children of each type.

```java
// CollectionDAO.java — deleteCollectionByShepardId
// NEO-AUDIT-008: the old chained OPTIONAL MATCH created c × d × r row triples.
// Replaced with CALL{} subqueries that each execute independently.
String query =
  """
  MATCH (c:Collection {shepardId:%d})
  SET c.deleted = true
  WITH c
  CALL {
    WITH c
    MATCH (c)-[:has_dataobject]->(d:DataObject)
    SET d.deleted = true
    RETURN count(d) AS doCount
  }
  CALL {
    WITH c
    MATCH (c)-[:has_dataobject]->(d:DataObject)-[:has_reference]->(r:BasicReference)
    SET r.deleted = true
    RETURN count(r) AS refCount
  }
  RETURN doCount, refCount""".formatted(shepardId);
```

File: `backend/src/main/java/de/dlr/shepard/context/collection/daos/CollectionDAO.java`, lines 118–142.

**Why.** `MATCH (a)-[:r1]->(b), (a)-[:r2]->(c)` produces `|b| × |c|` rows before
any `SET`. `CALL {}` subqueries execute independently, keeping cardinality bounded.

---

## 2. PostgreSQL / TimescaleDB

### 2.1 JPA entity with Panache `PanacheRepositoryBase`

**Pattern.** Repositories that need custom queries extend Quarkus's
`PanacheRepositoryBase<Entity, IdType>`. JPQL queries are written as Panache
fragment expressions (everything after `WHERE`) with positional or named parameters,
never via string concatenation.

```java
// TsChannelResolver.java
@ApplicationScoped
public class TsChannelResolver implements PanacheRepositoryBase<TimeseriesEntity, Integer> {

  public Optional<TimeseriesEntity> findByShepardId(UUID shepardId) {
    if (shepardId == null) return Optional.empty();
    return this.find("shepardId = ?1", shepardId).firstResultOptional();
  }

  public List<TimeseriesEntity> bulkFindByShepardIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return this.find("shepardId IN ?1", ids).list();
  }
}
```

File: `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TsChannelResolver.java`, lines 29–42, 176–179.

**Why.** Panache fragment expressions compile to JPQL prepared statements,
preventing SQL injection and enabling Hibernate's plan cache.

---

### 2.2 Additive-only migrations: `ADD COLUMN IF NOT EXISTS`

**Pattern.** New columns are always added as nullable using
`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`. The column is first added nullable,
then backfilled, then tightened to `NOT NULL` — three separate statements to
avoid a full-table lock on existing rows.

```sql
-- V1.11.0__add_shepard_id_to_timeseries.sql
-- Idempotent: ADD COLUMN IF NOT EXISTS + CREATE UNIQUE INDEX IF NOT EXISTS.

-- Step 1: add nullable first.
ALTER TABLE timeseries
    ADD COLUMN IF NOT EXISTS shepard_id UUID;

-- Step 2: backfill all NULLs (idempotent — only updates rows without one).
UPDATE timeseries
    SET shepard_id = gen_random_uuid()
    WHERE shepard_id IS NULL;

-- Step 3: tighten to NOT NULL now that every row has a value.
ALTER TABLE timeseries
    ALTER COLUMN shepard_id SET NOT NULL;

-- Step 4: ensure new rows auto-mint a shepard_id on insert.
ALTER TABLE timeseries
    ALTER COLUMN shepard_id SET DEFAULT gen_random_uuid();
```

File: `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql`, lines 27–43.

Second example — rollback twin uses `ADD COLUMN IF NOT EXISTS`:

```sql
-- V1.14.0_R__restore_5tuple_to_timeseries.sql (rollback)
ALTER TABLE timeseries
    ADD COLUMN IF NOT EXISTS measurement   TEXT,
    ADD COLUMN IF NOT EXISTS field         TEXT,
    ADD COLUMN IF NOT EXISTS device        TEXT,
    ADD COLUMN IF NOT EXISTS location      TEXT,
    ADD COLUMN IF NOT EXISTS symbolic_name TEXT;
```

File: `backend/src/main/resources/db/migration/V1.14.0_R__restore_5tuple_to_timeseries.sql`, lines 5–10.

**Why.** Adding a `NOT NULL` column without a `DEFAULT` on a large table requires
a full-table lock — incompatible with zero-downtime deploys. The three-step pattern
keeps the table online throughout.

---

### 2.3 Idempotent index and table creation with `IF NOT EXISTS`

**Pattern.** Every `CREATE INDEX` and `CREATE TABLE` in a Flyway migration carries
`IF NOT EXISTS`, making the migration safe to re-run.

```sql
-- V1.10.0__add_permission_audit_log_table.sql
CREATE TABLE IF NOT EXISTS permission_audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    entity_app_id  TEXT         NOT NULL,
    entity_kind    TEXT,
    actor_username TEXT,
    action         TEXT         NOT NULL,
    detail_json    TEXT
);

CREATE INDEX IF NOT EXISTS perm_audit_entity_app_id_idx
    ON permission_audit_log (entity_app_id);

CREATE INDEX IF NOT EXISTS perm_audit_occurred_at_idx
    ON permission_audit_log (occurred_at DESC);
```

File: `backend/src/main/resources/db/migration/V1.10.0__add_permission_audit_log_table.sql`, lines 12–26.

Second example — predicate vocabulary table:

```sql
-- V1.16.0__Add_predicate_vocabulary.sql
CREATE TABLE IF NOT EXISTS predicate_vocabulary (
    predicate_uri   TEXT        PRIMARY KEY,
    substrate       TEXT        NOT NULL
        CHECK (substrate IN ('neo4j', 'timescaledb', 'postgres', 'garage')),
    ...
);
CREATE INDEX IF NOT EXISTS predicate_vocab_substrate_idx
    ON predicate_vocabulary (substrate);
```

File: `backend/src/main/resources/db/migration/V1.16.0__Add_predicate_vocabulary.sql`, lines 29–44.

**Why.** A migration that fails on re-run blocks every Flyway-managed startup.
`IF NOT EXISTS` makes every DDL statement a no-op when the object already exists.

---

### 2.4 `@SecondaryTable` to isolate hot-path columns from identity metadata

**Pattern.** When a JPA entity has a high-write "hot" subset of columns and a
lower-frequency identity/metadata subset, the identity columns are placed in a
`@SecondaryTable` joined by primary key.

```java
// TimeseriesEntity.java
@Entity
@Table(name = "timeseries")
@SecondaryTable(
  name = "channel_metadata",
  pkJoinColumns = @PrimaryKeyJoinColumn(name = "timeseries_id")
)
public class TimeseriesEntity {

  // 5-tuple fields stored in channel_metadata, not timeseries (hot table)
  @Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
  private String measurement;

  @Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
  private String field;

  // Single-field identity column on the hot table
  @Column(name = "shepard_id", columnDefinition = "UUID",
          nullable = false, unique = true, updatable = false)
  private UUID shepardId;
}
```

File: `backend/src/main/java/de/dlr/shepard/data/timeseries/model/TimeseriesEntity.java`, lines 42–95.

**Why.** The 5-tuple is written once at channel creation and never changes.
Separating it into a secondary table keeps the hot row narrow, reducing buffer pool
pressure on every data-point insert.

---

## 3. MongoDB

### 3.1 Collection naming convention: `PascalCase` type prefix + UUID for dynamic collections; camelCase for singleton collections

**Pattern.** Per-entity-instance collections use a type-name prefix followed by a
UUID (e.g. `FileContainer<uuid>`, `StructuredDataContainer<uuid>`). Singleton
collections use camelCase with an underscore prefix for system collections
(e.g. `userAvatars`, `_shepard_files`, `_shepard_videos`).

```java
// FileService.java — dynamic per-container collection
public String createFileContainer() {
    String oid = "FileContainer" + uuidHelper.getUUID().toString();
    // e.g. "FileContainer550e8400-e29b-41d4-a716-446655440000"
    ...
}

// StructuredDataService.java — dynamic per-container collection
public String createStructuredDataContainer() {
    String mongoId = "StructuredDataContainer" + UUID.randomUUID().toString();
    mongoDatabase.createCollection(mongoId);
    return mongoId;
}

// MongoSchemaInitializer.java — singleton collection name constants
static final String USER_AVATARS   = "userAvatars";
static final String SHEPARD_FILES  = "_shepard_files";
static final String SHEPARD_VIDEOS = "_shepard_videos";
```

File: `backend/src/main/java/de/dlr/shepard/data/file/services/FileService.java`, lines 75–76.
File: `backend/src/main/java/de/dlr/shepard/data/structureddata/services/StructuredDataService.java`, lines 37–39.
File: `backend/src/main/java/de/dlr/shepard/common/mongoDB/MongoSchemaInitializer.java`, lines 81–94.

**Why.** Namespace collisions between entity types are impossible when the type name
is part of the collection key. The UUID suffix ensures per-instance isolation.

---

### 3.2 `$jsonSchema` validators applied at startup (fail-soft, `warn` action)

**Pattern.** Fixed-name singleton collections have a `$jsonSchema` validator
applied via `collMod` at every startup, using `validationAction: "warn"` so that
pre-validator rows do not break. Failures on any collection are logged as `WARN`
and swallowed so that a validator failure never aborts startup.

```java
// MongoSchemaInitializer.java — schema validator applied idempotently
static Document buildUserAvatarsValidator() {
    return new Document(
      "$jsonSchema",
      new Document()
        .append("bsonType", "object")
        .append("required", List.of("_id", "data", "mimeType", "sizeBytes", "uploadedAt"))
        .append("properties", new Document()
            .append("_id",       new Document("bsonType", "string"))
            .append("data",      new Document("bsonType", "binData"))
            .append("mimeType",  new Document("bsonType", "string")
                .append("enum", List.of("image/jpeg","image/png","image/gif","image/webp")))
            .append("sizeBytes", new Document("bsonType", "int").append("minimum", 0))
            .append("uploadedAt",new Document("bsonType", "date"))
        )
    );
}
```

File: `backend/src/main/java/de/dlr/shepard/common/mongoDB/MongoSchemaInitializer.java`, lines 135–158.

Fail-soft error handling:

```java
} catch (Exception e) {
    Log.warnf(
      "MongoSchemaInitializer: failed to apply $jsonSchema validator to '%s' — %s (non-fatal)",
      collectionName, e.getMessage()
    );
}
```

File: `backend/src/main/java/de/dlr/shepard/common/mongoDB/MongoSchemaInitializer.java`, lines 279–285.

**Why.** MongoDB has no migration framework like Flyway. Applying validators at
startup is the closest equivalent. Using `warn` (not `error`) means existing
pre-validator data continues to function.

---

### 3.3 appId as document `_id` for user-owned singleton documents

**Pattern.** For documents with at most one per user, use the user's `appId` (UUID v7)
as the MongoDB `_id`. This eliminates a secondary index and makes lookups a
primary-key scan.

```java
// UserAvatarService.java
Document doc = new Document()
    .append("_id", userAppId)          // appId IS the document _id
    .append("data", new Binary(bytes))
    .append("mimeType", mimeType)
    .append("sizeBytes", bytes.length)
    .append("uploadedAt", new Date());

collection().replaceOne(eq("_id", userAppId), doc, new ReplaceOptions().upsert(true));

// Lookup is primary-key O(log n):
public Document find(String userAppId) {
    return collection().find(eq("_id", userAppId)).first();
}
```

File: `backend/src/main/java/de/dlr/shepard/auth/users/services/UserAvatarService.java`, lines 61–80.

**Why.** When there is a 1:1 relationship between the domain entity and the document,
using the entity's stable identifier as `_id` eliminates the secondary index.

---

## 4. PostGIS

### 4.1 Extension bootstrap with `CREATE EXTENSION IF NOT EXISTS`

**Pattern.** The PostGIS (and TimescaleDB) extension bootstrap in every relevant
migration uses `CREATE EXTENSION IF NOT EXISTS`, making the migration idempotent.

```sql
-- V2.0.0__green_field_schema.sql (spatiotemporal plugin)
-- Ensure both extensions exist on the target instance.
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;
```

File: `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql`, lines 20–23.

Second example — `pgcrypto` in core migrations:

```sql
-- V1.11.0__add_shepard_id_to_timeseries.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

File: `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql`, line 24.

**Why.** `CREATE EXTENSION` without `IF NOT EXISTS` throws `ERROR: extension already
exists` on any database that pre-installs the extension. `IF NOT EXISTS` is a no-op
in that case.

---

### 4.2 Spatial index on create, with discriminator-geometry consistency constraint

**Pattern.** Every table storing `GEOMETRY` columns ships a GiST spatial index in
the same migration. Discriminator columns that select among geometry types are
enforced by `CHECK` constraints that verify both the discriminator value and the
actual `ST_GeometryType()` match.

```sql
-- V2.0.0__green_field_schema.sql (spatiotemporal plugin)
CREATE TABLE shepard_spatial.profile (
    anchor           GEOMETRY NOT NULL,       -- always POINTZ
    profile          GEOMETRY,                -- type varies with profile_kind
    profile_kind     TEXT     NOT NULL,

    -- Anchor must always be POINTZ (3D origin point).
    CONSTRAINT chk_anchor_is_point CHECK (
        ST_GeometryType(anchor) = 'ST_Point' AND ST_NDims(anchor) >= 3
    ),

    -- Discriminator-vs-geometry agreement (closes TS-AUDIT-003).
    CONSTRAINT chk_profile_kind_matches_geom CHECK (
        profile_kind IN ('point','line','polygon','tin','multipoint','tube_centerline')
        AND (
            (profile_kind = 'point'           AND profile IS NULL)
         OR (profile_kind = 'tube_centerline' AND ST_GeometryType(profile) IN ('ST_LineString'))
         OR (profile_kind = 'polygon'         AND ST_GeometryType(profile) IN ('ST_Polygon'))
         ...
        )
    )
);
```

File: `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql`, lines 81–117.

Legacy V1 schema also created spatial index at table creation:

```sql
-- V1.0.0__setup_spatial_data_tables.sql
CREATE INDEX spatial_data_point_position_idx
  ON spatial_data_points
  USING GIST (position gist_geometry_ops_nd);
```

File: `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V1.0.0__setup_spatial_data_tables.sql`, lines 23–26.

**Why.** Spatial queries without a GiST index degrade to sequential scans — O(n)
per query. The `CHECK` constraint enforcing discriminator/geometry agreement prevents
the TS-AUDIT-003 anti-pattern of storing heterogeneous types without enforcement.

---

## 5. Garage / S3

### 5.1 Presigned URL TTL discipline — capped at permissions-cache TTL

**Pattern.** Presigned URLs are never issued with a TTL longer than the permissions
cache TTL. A dedicated validator bean (`PresignTtlValidator`) enforces the cap at
startup and every URL issuance, with a `WARN` log when the configured presign TTL
would have exceeded the cache TTL.

```java
// PresignTtlValidator.java
@ApplicationScoped
public class PresignTtlValidator {

  @ConfigProperty(name = "shepard.storage.presign.upload-ttl",   defaultValue = "PT15M")
  Duration configuredUploadTtl;

  @ConfigProperty(name = "shepard.storage.presign.download-ttl", defaultValue = "PT5M")
  Duration configuredDownloadTtl;

  @ConfigProperty(name = "shepard.permissions.cache.ttl",        defaultValue = "PT5M")
  Duration permissionsCacheTtl;

  Duration cap(Duration ttl) {
    return ttl.compareTo(permissionsCacheTtl) <= 0 ? ttl : permissionsCacheTtl;
  }
}
```

File: `backend/src/main/java/de/dlr/shepard/storage/PresignTtlValidator.java`, lines 34–83.

The REST resource injects `PresignTtlValidator` and calls the effective-TTL method:

```java
// FileContainerPresignedUrlRest.java
result = fileContainerService.presignedUploadUrl(
    container.getId(), request.getFileName(),
    ttlValidator.effectiveUploadTtl()
);
```

File: `backend/src/main/java/de/dlr/shepard/v2/filecontainer/resources/FileContainerPresignedUrlRest.java`, lines 105–106.

**Why.** A permission revoked in shepard takes effect within one permissions-cache
TTL window. If a presigned URL lives longer than that window, a caller whose access
was revoked can still complete the download — bypassing the revocation because S3
authenticates the URL, not shepard.

---

### 5.2 Object key format: `<containerMongoId>/<uuid>` — bucket not in the locator

**Pattern.** The S3 object key is `<containerMongoId>/<uuid>`. The bucket name is
NOT stored in the locator — it is read from deploy-time config. This allows the
operator to rename the bucket without a data migration.

```java
// S3FileStorage.java — put
String uuid = request.assignedObjectKey() != null && !request.assignedObjectKey().isBlank()
  ? request.assignedObjectKey()
  : UUID.randomUUID().toString();
String key = request.container() + "/" + uuid;
// e.g. "FileContainer550e8400.../8a2dfe20-..."

return new StorageLocator(ID, key);  // bucket excluded from locator
```

File: `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`, lines 215–247.

**Why.** Including the bucket name in the stored locator would require a data
migration sweep whenever the operator renames or migrates the bucket.

---

### 5.3 Fail-soft adapter with `isEnabled()` guard

**Pattern.** The S3 adapter is self-disabled when its bucket config is blank.
`FileStorageRegistry` refuses to activate a `!isEnabled()` adapter.

```java
// S3FileStorage.java — @PostConstruct init
if (bucket == null || bucket.isBlank()) {
    enabled = false;
    Log.infof(
      "S3FileStorage: shepard.files.s3.bucket is unset — adapter disabled. " +
      "Set shepard.storage.provider=s3 only after configuring the bucket."
    );
    return;
}
```

File: `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`, lines 111–118.

**Why.** A startup-time misconfiguration should surface with a clean log message,
not a cryptic S3 error mid-upload. The `isEnabled()` guard routes callers to a
clean 503 with a problem-detail description.

---

## 6. Timeseries channel identity

### 6.1 Current 5-tuple pattern — `(measurement, device, location, symbolicName, field)`

**Pattern.** A timeseries channel is currently identified by a 5-part tuple stored
in the `channel_metadata` secondary table. The UNIQUE constraint enforces that each
`(container_id, measurement, field, symbolic_name, device, location)` combination
is distinct within a container.

```java
// TimeseriesEntity.java — 5-tuple fields on secondary table
@Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
private String measurement;

@Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
private String field;

@Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
private String device;

@Column(table = "channel_metadata", columnDefinition = "TEXT", nullable = false)
private String location;

@Column(table = "channel_metadata", name = "symbolic_name", columnDefinition = "TEXT", nullable = false)
private String symbolicName;
```

File: `backend/src/main/java/de/dlr/shepard/data/timeseries/model/TimeseriesEntity.java`, lines 59–72.

Legacy 5-tuple lookup (still used on the `/shepard/api/` v5-compatible surface):

```java
// TsChannelResolver.java — legacy path
public Optional<TimeseriesEntity> findByContainerAndTuple(long containerId, Timeseries ts) {
    return this.find(
        "containerId = ?1 and measurement = ?2 and field = ?3 " +
        "and symbolicName = ?4 and device = ?5 and location = ?6",
        containerId, ts.getMeasurement(), ts.getField(),
        ts.getSymbolicName(), ts.getDevice(), ts.getLocation()
    ).firstResultOptional();
}
```

File: `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TsChannelResolver.java`, lines 90–101.

**Why the 5-tuple is a known friction point.** Every endpoint that addresses a
channel by identity requires 5 query parameters instead of 1. MCP tools must supply
the complete 5-tuple — a leaky abstraction that breaks the "one stable identifier
per entity" principle.

---

### 6.2 Migration direction — `shepardId` single-field identity

**Pattern.** Flyway migration V1.11.0 added a `shepard_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()` column to the `timeseries` table. `TsChannelResolver` exposes
both a single-key path (`findByShepardId`) and the legacy 5-tuple path. The `/v2/`
surface exposes `shepardId` as the canonical channel identifier.

```java
// TsChannelResolver.java — canonical single-key path
public Optional<TimeseriesEntity> findByShepardId(UUID shepardId) {
    if (shepardId == null) return Optional.empty();
    return this.find("shepardId = ?1", shepardId).firstResultOptional();
}

// Container-scoped variant (canonical path on /v2/ surface)
public Optional<TimeseriesEntity> findByContainerAndShepardId(long containerId, UUID shepardId) {
    return findByShepardId(shepardId).filter(row -> row.getContainerId() == containerId);
}
```

File: `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TsChannelResolver.java`, lines 40–75.

The migration follows the additive 3-step pattern (see §2.2):

```sql
-- V1.11.0__add_shepard_id_to_timeseries.sql
ALTER TABLE timeseries
    ADD COLUMN IF NOT EXISTS shepard_id UUID;           -- step 1: nullable
UPDATE timeseries SET shepard_id = gen_random_uuid()
    WHERE shepard_id IS NULL;                           -- step 2: backfill
ALTER TABLE timeseries
    ALTER COLUMN shepard_id SET NOT NULL;               -- step 3: tighten
CREATE UNIQUE INDEX IF NOT EXISTS idx_timeseries_shepard_id
    ON timeseries(shepard_id);
```

File: `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql`, lines 27–46.

Design reference: `aidocs/platform/87-timeseries-appid-migration.md` — full migration trajectory.

**Why.** The `shepardId` UUID is cursor-pageable, addressable without container
context, embeddable in MCP tool arguments, and compatible with the cross-substrate
`appId` addressing principle.

---

## 7. Cross-cutting

### 7.1 UUID v7 (`AppIdGenerator`) across all substrates

**Pattern.** Every new entity across every substrate gets a UUID v7 identifier
called `appId`. In Neo4j entities, this is minted automatically by
`GenericDAO.createOrUpdate()` when the field is still `null`.

```java
// AppIdGenerator.java — UUID v7 mint
public final class AppIdGenerator {
  public static String next() {
    return UuidCreator.getTimeOrderedEpoch().toString();
    // e.g. "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506"
  }
}
```

File: `backend/src/main/java/de/dlr/shepard/common/identifier/AppIdGenerator.java`, lines 31–33.

Auto-mint on Neo4j entity save:

```java
// GenericDAO.createOrUpdate — mint appId on first save
public T createOrUpdate(T entity) {
    if (entity instanceof HasAppId hasAppId && hasAppId.getAppId() == null) {
        hasAppId.setAppId(AppIdGenerator.next());
    }
    session.save(entity, DEPTH_ENTITY);
    ...
}
```

File: `backend/src/main/java/de/dlr/shepard/common/neo4j/daos/GenericDAO.java`, lines 125–147.

`HasAppId` marker interface that every Neo4j entity must implement:

```java
// HasAppId.java
public interface HasAppId {
  String getAppId();
  void setAppId(String appId);
}
```

File: `backend/src/main/java/de/dlr/shepard/common/identifier/HasAppId.java`, lines 17–31.

**Why.** UUID v7 embeds a millisecond Unix timestamp, making identifiers monotonic
and suitable for B-tree cursor pagination without a round-trip to any database.
A single identifier shape across all six storage substrates means provenance edges,
semantic annotations, MCP tool arguments, and REST paths all use the same addressing
scheme.

---

### 7.2 Additive schema changes only — no mutation of applied migrations

**Pattern.** Once a migration file has been applied to any production instance, it
is immutable. New functionality is always a new file (`V(N+1)__`). Rollback twins
(`V(N)_R__*`) are separate files. This applies to Neo4j (`V*.cypher`),
PostgreSQL/Flyway (`V*.sql`), and the spatiotemporal plugin's Flyway baseline.

Canonical example — the timeseries 5-tuple removal (forward + rollback pair):

```
V1.14.0__drop_5tuple_from_core_timeseries.sql     ← forward
V1.14.0_R__restore_5tuple_to_timeseries.sql       ← rollback (ADD COLUMN IF NOT EXISTS)
```

Files: `backend/src/main/resources/db/migration/V1.14.0__drop_5tuple_from_core_timeseries.sql`
and `backend/src/main/resources/db/migration/V1.14.0_R__restore_5tuple_to_timeseries.sql`.

Neo4j rollback naming:

```
V96__Ntf1_NotificationTransport_scaffold.cypher
V96_R__Ntf1_NotificationTransport_scaffold.cypher
```

**Why.** Editing an applied migration file causes its checksum to diverge from what
Flyway or `neo4j-migrations` recorded. The migration runner rejects the mismatch and
aborts startup — a hard outage. Immutable forward migration files make the migration
history an accurate audit log of every schema change that touched production.
