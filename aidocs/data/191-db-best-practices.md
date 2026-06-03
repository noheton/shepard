---
stage: deployed
last-stage-change: 2026-06-03
---

# DB-BP1: Per-Substrate Database Best-Practices Catalogue

**Purpose.** This is a living reference for authors adding new code to Shepard.
When you add a new entity, a new migration, a new storage interaction, or a new
background write, start here and copy from the patterns below. Each entry cites
a concrete file so you can read the full production implementation, not a
hypothetical example.

This catalogue covers six substrates: Neo4j (OGM), TimescaleDB/PostgreSQL,
MongoDB, PostGIS, S3/Garage, and cross-cutting patterns that apply everywhere.

---

## 1. Neo4j (OGM)

### 1.1 UUID v7 via `HasAppId` + `GenericDAO.createOrUpdate`

Every persisted Neo4j node entity implements `HasAppId` and gains a stable
`appId` (UUID v7) minted at first save — no round-trip to the database required.

**Pattern:**

```java
// Entity declares the mixin:
public class MyEntity implements HasAppId { ... }

// DAO saves it; GenericDAO mints the appId automatically if null:
myDAO.createOrUpdate(entity);  // → AppIdGenerator.next() fills appId when null
```

**Why UUID v7:** monotonic-per-millisecond, globally unique, sortable for
cursor pagination. Backed by `com.github.f4b6a3:uuid-creator`.

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/common/identifier/HasAppId.java` — interface
- `backend/src/main/java/de/dlr/shepard/common/identifier/AppIdGenerator.java` — generator
- `backend/src/main/java/de/dlr/shepard/common/neo4j/daos/GenericDAO.java` — `createOrUpdate` (line 125)

### 1.2 Parameterised Cypher — `ParamBinder` (C5 mitigation)

Cypher has no parameter binding for property *identifiers* (only for *values*).
The C5 security fix introduced two defences:

1. **Values** — always bound via `ParamBinder.bind(value)` which mints a fresh
   `$pN` placeholder and stores the value in a parameter map.
2. **Identifiers** — validated against `KNOWN_PROPERTIES` allowlist or
   `SAFE_PROPERTY_NAME` regex before being spliced into Cypher verbatim.

**Pattern:**

```java
ParamBinder binder = new ParamBinder();
String placeholder = binder.bind(userSuppliedValue);  // returns "$p0", "$p1", ...
String cypher = "WHERE n.name = " + placeholder;
session.query(getEntityType(), cypher, binder.params());
```

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/common/search/query/Neo4jQueryBuilder.java`
  — `ParamBinder` inner class (lines 104–118), `lowerCasedValuePart`
  (lines 231–241), `validatePropertyIdentifier` (lines 125–132)

### 1.3 `IF NOT EXISTS` migration guards

Every Neo4j migration file uses `IF NOT EXISTS` on every `CREATE CONSTRAINT`
and `CREATE INDEX` statement. This makes migrations idempotent — safe to re-run
on dev stacks, staging replays, and post-rollback recovery.

**Pattern (Cypher):**

```cypher
CREATE CONSTRAINT appId_unique_MyEntity IF NOT EXISTS
  FOR (n:MyEntity) REQUIRE n.appId IS UNIQUE;
```

**Files to copy:**
- `backend/src/main/resources/neo4j/migrations/V11__Add_appId_unique_constraints.cypher`
  — canonical example of all-entity `IF NOT EXISTS` constraints
- `backend/src/main/resources/neo4j/migrations/V95__DT1_Phase0_DigitalTwinScene_scaffold.cypher`
  — recent constraint migration with rollback reference in header comment

### 1.4 Rollback twin for every data-mutating migration

Every migration file that mutates data ships a sibling
`V<N>_R__<description>.cypher` rollback file. The rollback is idempotent
(uses `IF NOT EXISTS` guards or `REMOVE` on nullable properties). The forward
migration's top comment cites the rollback file by name.

**Naming convention:**
- Forward: `V<N>__<Description>.cypher`
- Rollback: `V<N>_R__<Description>.cypher`

**Files to copy:**
- `backend/src/main/resources/neo4j/migrations/V98__Role_changed_at_user.cypher` + sibling `V98_R__Role_changed_at_user.cypher`
  — NOOP forward migration + data-only rollback (strip a nullable property)
- `backend/src/main/resources/neo4j/migrations/V95__DT1_Phase0_DigitalTwinScene_scaffold.cypher` + sibling

### 1.5 Additive nullable properties — no DDL migration needed

Neo4j is schema-less. A new optional property added to the OGM entity class
needs **no** migration file: existing nodes simply lack the property; OGM reads
absence as `null`. The migration file is only needed when you also want a
uniqueness constraint or index on the new property.

**Pattern:** Add the `@Property` field in Java. Existing nodes coalesce the
absence to `null` at read time. No backfill required.

**Anti-pattern to avoid:** writing a migration that sets a default value on
every existing node when the default is also the Java `null`-safe fallback —
that is redundant work.

**Example:** `V98__Role_changed_at_user.cypher` is a NOOP forward migration
that exists only to document the additive property and to pair with its rollback.

### 1.6 `MigrationsRunner` — fail-fast on migration error

The `MigrationsRunner` propagates `MigrationsException` to abort startup when
any migration fails. This is correct and intentional: a failed migration means
the schema is in an unknown state and the backend must not serve traffic.

**File:**
- `backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java`

Do not catch `MigrationsException` in the migration runner. Do not add
migrations that can fail silently.

---

## 2. TimescaleDB / PostgreSQL

### 2.1 `ADD COLUMN IF NOT EXISTS` — idempotent additive schema changes

New columns are always nullable (or carry a default covering all existing rows)
and use `ADD COLUMN IF NOT EXISTS` so the migration is safe to re-run.

**Pattern (SQL):**

```sql
ALTER TABLE timeseries
    ADD COLUMN IF NOT EXISTS shepard_id UUID;
```

**Files to copy:**
- `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql`
  — canonical example: nullable add, backfill, NOT NULL tighten, DEFAULT set,
  unique index — all in one idempotent migration with a rollback sibling

### 2.2 `CREATE INDEX IF NOT EXISTS` — idempotent index creation

All index creation uses `IF NOT EXISTS` to avoid conflicts on re-run or
multi-instance deploys.

**Pattern (SQL):**

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_timeseries_shepard_id
    ON timeseries(shepard_id);
```

**Files to copy:**
- `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql` (line 47)
- `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V1.1.0__indexes.sql`
  — `CREATE INDEX IF NOT EXISTS` examples with BRIN and GIN

### 2.3 Rollback sibling for data-mutating SQL migrations

Same rule as Neo4j: every data-mutating Flyway migration ships a `_R__` sibling.

**Naming convention:**
- Forward: `V<semver>__<description>.sql`
- Rollback: `V<semver>_R__<description>.sql`

**Files to copy:**
- `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql` + `V1.11.0_R__drop_shepard_id.sql`

### 2.4 Flyway version naming convention

Flyway migrations use a semver-style version: `V<major>.<minor>.<patch>__<description>.sql`.

- Increment `minor` for additive changes (new columns, new indexes).
- Increment `major` for schema restructurings (new tables, table renames).
- Patches (`.<patch>`) for hot-fixes that must interleave with an existing series.

**Example:** `V1.11.0__add_shepard_id_to_timeseries.sql` → additive column.

### 2.5 COPY-command bulk ingest — `insertManyDataPointsWithCopyCommand`

For high-volume row insertion (timeseries data points, importer batch commits),
use PostgreSQL's `COPY` protocol via `CopyManager` instead of batched `INSERT`.
`COPY` bypasses the Postgres `Bind` message's 32 767-parameter limit and achieves
order-of-magnitude higher throughput.

**Pattern:**

```java
PGConnection pgConn = (PGConnection) conn.unwrap(PGConnection.class);
CopyManager copyManager = pgConn.getCopyAPI();
InputStream input = new ByteArrayInputStream(csvBytes);
copyManager.copyIn("COPY table (col1, col2) FROM STDIN WITH (FORMAT csv);", input);
```

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java`
  — `insertManyDataPointsWithCopyCommand` (line 135), `insertManyDataPointsWithCopyCommandBatched` (line 193)
- Note: The `INSERT_BATCH_SIZE` constant (line 59) and the static initializer
  guard (line 78) protect against exceeding the Postgres Bind limit when
  falling back to batched INSERT. Copy-command is the preferred path for large
  payloads.

### 2.6 Named JPA parameters — always use `setParameter`

All native queries use named parameters (`:name`) rather than positional `?`
markers. Bind via `query.setParameter("name", value)`. This makes large
multi-row insert queries readable and prevents accidental mis-ordering.

**Files to copy:**
- `TimeseriesDataPointRepository.java` — `buildInsertQueryObject` (line 371),
  `buildSelectQueryObject` (line 409)

---

## 3. MongoDB

### 3.1 Collection naming convention

Fixed-name singleton collections use one of two naming styles:

| Style | When | Examples |
|---|---|---|
| `camelCase` | User-domain objects | `userAvatars` |
| `_shepard_<noun>` | Internal bookkeeping / shadow collections | `_shepard_files`, `_shepard_videos` |

Per-`FileContainer` collections are dynamically named by UUID and follow no
fixed naming convention — that is a known design constraint (MONGO-AUDIT-007).

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/common/mongoDB/MongoSchemaInitializer.java`
  — `USER_AVATARS`, `SHEPARD_FILES`, `SHEPARD_VIDEOS` constants (lines 81–94)

### 3.2 `$jsonSchema` validator on startup — `MongoSchemaInitializer`

Fixed-name collections receive a `$jsonSchema` validator applied idempotently
on every startup via `MongoSchemaInitializer`. Existing collections use
`collMod`; absent collections are created with the validator pre-wired. Failures
are logged at WARN and do not block startup (schema validation is a data-quality
aid, not a structural invariant).

**Pattern for a new singleton collection:**
1. Add the collection-name constant in `MongoSchemaInitializer`.
2. Add a `buildMyCollectionValidator()` method returning a `$jsonSchema` Document.
3. Call `applyValidator(MY_COLLECTION, buildMyCollectionValidator())` in `onStart`.

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/common/mongoDB/MongoSchemaInitializer.java`
  — `buildUserAvatarsValidator()` (line 135), `applyValidator()` (line 251)

### 3.3 Size cap + MIME-type allowlist on binary uploads

Every binary upload to MongoDB enforces a size cap and a MIME-type allowlist
before writing. The cap uses `readNBytes(MAX_BYTES + 1)` to detect over-limit
without buffering the whole stream.

**Pattern:**

```java
static final long MAX_BYTES = 2 * 1024 * 1024L;  // 2 MiB
static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", ...);

byte[] bytes = in.readNBytes((int) MAX_BYTES + 1);
if (bytes.length > MAX_BYTES) return false;  // reject
if (!ALLOWED_MIME_TYPES.contains(mimeType)) return false;  // reject
```

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/auth/users/services/UserAvatarService.java`
  — `MAX_BYTES`, `ALLOWED_MIME_TYPES`, `upsert()` (lines 33–70)

### 3.4 `appId` as the document key for singleton-reference documents

When a MongoDB document has a 1:1 relationship with a Neo4j entity (e.g. one
avatar per user), use the entity's `appId` as the document's `_id`. This
eliminates the need for a separate index and makes the join key explicit.

**Pattern:**

```java
Document doc = new Document()
    .append("_id", userAppId)  // ← Neo4j entity's appId
    .append("data", new Binary(bytes))
    ...;
collection().replaceOne(eq("_id", userAppId), doc, new ReplaceOptions().upsert(true));
```

**Files to copy:**
- `UserAvatarService.java` — `upsert()` (line 49)

### 3.5 `AbstractMongoObject` — base class for Neo4j-mirrored Mongo documents

Documents that shadow a Neo4j node (e.g. `StructuredData` containers) extend
`AbstractMongoObject`, which carries `oid` (the Mongo-internal identifier),
`appId` (the Neo4j appId, written by `GenericDAO`), and `createdAt`. The
`@BsonIgnore` on `appId` means Neo4j OGM writes it but MongoDB never persists
it in the BSON document — the two sides stay decoupled.

**File:**
- `backend/src/main/java/de/dlr/shepard/common/mongoDB/AbstractMongoObject.java`

---

## 4. PostGIS

### 4.1 Spatial index on create — GIST for geometry columns

Every `GEOMETRY` column receives a GIST index at schema creation time. For 3D
geometries use `gist_geometry_ops_nd` to handle the Z dimension. For partial
indexes (e.g. nullable geometry columns), use `WHERE profile IS NOT NULL`.

**Pattern (SQL):**

```sql
CREATE INDEX profile_anchor_gist
    ON shepard_spatial.profile USING GIST (anchor gist_geometry_ops_nd);

-- Partial GIST for nullable geometry:
CREATE INDEX profile_geom_gist
    ON shepard_spatial.profile USING GIST (profile gist_geometry_ops_nd)
    WHERE profile IS NOT NULL;
```

**Files to copy:**
- `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V1.0.0__setup_spatial_data_tables.sql`
  — baseline GIST index
- `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql`
  — full index suite (BRIN on time, GIST on anchor + profile, GIN on JSONB)

### 4.2 BRIN index for time columns on hypertables

Spatial hypertables store time as nanoseconds since epoch (matching
`timeseries_data_points`). Use a BRIN index on the time column — monotonic
arrival data means BRIN gives near-B-tree selectivity at a fraction of the
storage cost.

**Pattern (SQL):**

```sql
CREATE INDEX profile_time_brin
    ON shepard_spatial.profile USING BRIN (time)
    WITH (pages_per_range = 32);
```

**Files to copy:**
- `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V1.1.0__indexes.sql` (BRIN + GIN with `IF NOT EXISTS`)
- `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql` (line 161)

### 4.3 `@RequiresDatabase` annotation — graceful 503 when PostGIS not installed

REST resources that require PostGIS carry `@RequiresDatabase(DatabaseKind.POSTGIS)`.
The `RequiresDatabaseFilter` intercepts such requests and returns 503 with a
structured error body when the database is unreachable or not installed. This
prevents a `null` datasource from propagating as a 500.

**Pattern:**

```java
@RequiresDatabase({ DatabaseKind.POSTGIS })
@Path("/v2/spatial/...")
public class MySpatialRest { ... }
```

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/common/healthz/RequiresDatabase.java`
- `backend/src/main/java/de/dlr/shepard/common/healthz/DatabaseKind.java`
- `backend/src/main/java/de/dlr/shepard/common/healthz/RequiresDatabaseFilter.java`

### 4.4 `CREATE EXTENSION IF NOT EXISTS` for PostGIS and TimescaleDB

Always guard extension creation with `IF NOT EXISTS` so migrations are safe
on instances where the extension is already active.

**Pattern (SQL):**

```sql
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;
```

**File:**
- `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql` (lines 21–22)

---

## 5. S3 / Garage (FS1)

### 5.1 `FileStorage` SPI — never call the AWS SDK directly from services

All file storage operations go through the `FileStorage` SPI and its registry.
Services call `fileStorageRegistry.activeStorage()` and use the returned
`Optional<FileStorage>`. They never construct an `S3Client` or call GridFS
APIs directly.

**Pattern:**

```java
@Inject FileStorageRegistry registry;

Optional<FileStorage> storage = registry.activeStorage();
if (storage.isEmpty()) {
    throw new StorageProviderUnavailableException("no storage provider configured");
}
StorageLocator locator = storage.get().put(request);
```

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/storage/FileStorage.java` — SPI interface
- `backend/src/main/java/de/dlr/shepard/storage/FileStorageRegistry.java` — CDI registry
- `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java` — reference implementation

### 5.2 Graceful 503 when provider not installed or misconfigured

`S3FileStorage` checks `shepard.files.s3.bucket` at `@PostConstruct`. If blank,
it sets `enabled = false` and logs at INFO. Every public method calls
`requireEnabled()` which throws `StorageProviderUnavailableException` → 503.
The backend boots cleanly even when the S3 plugin is present but unconfigured.

**Files to copy:**
- `S3FileStorage.java` — `init()` (line 110), `requireEnabled()` (line 412)
- `backend/src/main/java/de/dlr/shepard/storage/StorageProviderUnavailableException.java`

### 5.3 `PresignTtlValidator` — cap presigned TTL at permissions-cache TTL

A presigned URL that lives longer than the permissions cache TTL allows a
revoked user to continue downloading files. `PresignTtlValidator` caps every
effective presigned TTL at `shepard.permissions.cache.ttl` (default 5 min) and
emits a `WARN` at startup for any configured TTL that would be capped.

Always call `presignTtlValidator.effectiveUploadTtl()` / `.effectiveDownloadTtl()`
/ `.effectiveExportTtl()` instead of reading the TTL config values directly.

**File:**
- `backend/src/main/java/de/dlr/shepard/storage/PresignTtlValidator.java`

### 5.4 Locator format — `<containerMongoId>/<uuid>`

The S3 locator string stored in Neo4j is `<containerMongoId>/<uuid>`. The bucket
name is *not* stored in the locator — it is a deploy-time topology knob
(`shepard.files.s3.bucket`). Changing the bucket requires a migration sweep (FS1e).

**Anti-pattern:** storing the bucket name in the locator would hard-code
deployment topology into the data.

### 5.5 `chunkedEncodingEnabled(false)` for Garage / MinIO compatibility

Garage v1.0 rejects AWS SDK chunked-encoding (`STREAMING-AWS4-HMAC-SHA256-PAYLOAD`).
Always disable chunked encoding when building the S3 client for non-AWS endpoints:

```java
S3Configuration.builder()
    .pathStyleAccessEnabled(pathStyleAccess)
    .chunkedEncodingEnabled(false)  // required for Garage, MinIO, LocalStack
    .build()
```

**File:**
- `S3FileStorage.java` (lines 130–136)

---

## 6. Cross-Cutting Patterns

### 6.1 Every persisted entity carries a single stable `appId` (UUID v7)

Entities across Neo4j, MongoDB, TimescaleDB, PostGIS, and S3 all carry one
stable UUID v7 `appId`. This is the only identifier that crosses substrate
boundaries. Semantic annotations, provenance edges, MCP tool arguments, and
REST resource paths use `appId` exclusively — never Neo4j node IDs, MongoDB
`ObjectId`s, or TimescaleDB serial PKs.

| Substrate | Mechanism |
|---|---|
| Neo4j | `HasAppId` interface + `GenericDAO.createOrUpdate` mints via `AppIdGenerator.next()` |
| MongoDB | `AbstractMongoObject.appId` field (`@BsonIgnore` — Mongo doesn't persist it; Neo4j OGM writes it) |
| TimescaleDB | `ADD COLUMN IF NOT EXISTS shepard_id UUID DEFAULT gen_random_uuid()` |
| PostGIS | `coord_frame_app_id UUID NOT NULL` in `profile_container` |
| S3 | Locator key includes no `appId` — S3 objects are addressed by locator; the *FileReference* node's `appId` is the stable identity |

**Files:** See §1.1, §2.1, §3.4, §4.1 above.

### 6.2 Secondary writes are fire-and-forget — wrap in try/catch

Provenance capture, HMAC chain stamping, notification dispatch, and
observability counters are secondary effects. They must never propagate
exceptions to the caller. Wrap every secondary write in try/catch, log at
WARN, and continue.

**Pattern:**

```java
try {
    hmacChainService.stamp(activity);
} catch (RuntimeException e) {
    Log.warnf(e, "HMAC stamp failed for activity %s — audit chain gap, continuing", activity.getAppId());
}
```

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java`
  — `aroundWriteTo` phase (line 236 area): catch block logs WARN, never re-throws
- `backend/src/main/java/de/dlr/shepard/provenance/services/HmacChainService.java`
  — `stamp()` is already defensive; callers must also wrap the call site

### 6.3 Registries are fail-soft — return `Optional.empty()` on missing slot

Every SPI registry (`AiRegistry`, `FileStorageRegistry`, `MinterRegistry`,
`ViewRecipeRendererRegistry`) returns `Optional.empty()` when a slot is
unconfigured or a provider is unavailable. They never throw on a missing
registration and never abort startup when a provider fails to load.

**Pattern:**

```java
Optional<Transport> transport = aiRegistry.byId(configuredTransportId);
if (transport.isEmpty()) {
    // degrade gracefully: return a no-op response, log a WARN
    Log.warnf("AI transport '%s' not installed — returning empty result", configuredTransportId);
    return Optional.empty();
}
```

**Files to copy:**
- `backend/src/main/java/de/dlr/shepard/spi/ai/AiRegistry.java` — `byId()` (line 149), `firstEnabledFor()` (line 175)
- `backend/src/main/java/de/dlr/shepard/storage/FileStorageRegistry.java` — `activeStorage()` Optional return
- `backend/src/main/java/de/dlr/shepard/publish/minter/MinterRegistry.java`

### 6.4 Schema changes are additive and nullable

Schema changes across all substrates must be:
- **Forward-compatible** — existing rows/nodes are valid after the migration.
- **Zero-backfill** by default — new fields are nullable (or have a default
  that covers existing rows without a write).
- **Idempotent** — safe to re-run (`IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`,
  `REMOVE` on nullable property).
- **Paired with a rollback** when data-mutating.

`NOT NULL` without a `DEFAULT` that covers all existing rows is banned. It
requires a full-table-lock backfill — incompatible with zero-downtime deploys.

**Anti-pattern:**

```sql
ALTER TABLE timeseries ADD COLUMN channel_name TEXT NOT NULL;  -- BANNED: breaks existing rows
```

**Correct pattern:**

```sql
ALTER TABLE timeseries ADD COLUMN IF NOT EXISTS channel_name TEXT;  -- nullable first
UPDATE timeseries SET channel_name = 'default' WHERE channel_name IS NULL;  -- backfill if needed
ALTER TABLE timeseries ALTER COLUMN channel_name SET NOT NULL;  -- tighten after backfill
```

### 6.5 Operators write migrations, not the runtime

Migrations are applied by `MigrationsRunner` (Neo4j) or Flyway (Postgres) at
startup. The runtime service layer never issues DDL (`CREATE TABLE`, `CREATE INDEX`,
`CREATE CONSTRAINT`) at request time. The sole exception is `MongoSchemaInitializer`
which applies `$jsonSchema` validators idempotently — that is a startup-time
operation, not a request-time one.

---

## 7. Quick-Reference Cheat-Sheet

| Pattern | Copy from | Substrate |
|---|---|---|
| UUID v7 entity identity | `HasAppId.java`, `AppIdGenerator.java`, `GenericDAO.java` | Neo4j |
| Parameterised Cypher values | `Neo4jQueryBuilder.java` → `ParamBinder` | Neo4j |
| Property-name injection defence | `Neo4jQueryBuilder.java` → `validatePropertyIdentifier` | Neo4j |
| `IF NOT EXISTS` constraint | `V11__Add_appId_unique_constraints.cypher` | Neo4j |
| Rollback twin (Cypher) | `V98__Role_changed_at_user.cypher` + `V98_R__...` | Neo4j |
| Additive nullable SQL column | `V1.11.0__add_shepard_id_to_timeseries.sql` | TimescaleDB |
| `CREATE INDEX IF NOT EXISTS` | `V1.1.0__indexes.sql` (spatiotemporal plugin) | TimescaleDB/PostGIS |
| Rollback twin (SQL) | `V1.11.0_R__drop_shepard_id.sql` | TimescaleDB |
| COPY-command bulk ingest | `TimeseriesDataPointRepository.java` → `insertManyDataPointsWithCopyCommand` | TimescaleDB |
| Collection naming | `MongoSchemaInitializer.java` constants | MongoDB |
| `$jsonSchema` validator on startup | `MongoSchemaInitializer.java` → `applyValidator()` | MongoDB |
| Size cap + MIME allowlist | `UserAvatarService.java` → `upsert()` | MongoDB |
| appId as Mongo `_id` | `UserAvatarService.java` | MongoDB |
| GIST index on geometry | `V2.0.0__green_field_schema.sql` (spatiotemporal plugin) | PostGIS |
| BRIN index on time column | `V1.1.0__indexes.sql` (spatiotemporal plugin) | PostGIS |
| `@RequiresDatabase` 503 guard | `RequiresDatabase.java`, `RequiresDatabaseFilter.java` | PostGIS |
| `FileStorage` SPI — no direct SDK | `FileStorage.java`, `FileStorageRegistry.java` | S3/Garage |
| Presign TTL cap | `PresignTtlValidator.java` | S3/Garage |
| Graceful 503 on missing provider | `S3FileStorage.java` → `init()`, `requireEnabled()` | S3/Garage |
| Chunked-encoding off for Garage | `S3FileStorage.java` (line 131) | S3/Garage |
| Secondary writes fire-and-forget | `ProvenanceCaptureFilter.java`, `HmacChainService.java` | Cross-cutting |
| Fail-soft registry | `AiRegistry.java`, `FileStorageRegistry.java` | Cross-cutting |
| Additive nullable schema | `V1.11.0__add_shepard_id_to_timeseries.sql` pattern | Cross-cutting |
