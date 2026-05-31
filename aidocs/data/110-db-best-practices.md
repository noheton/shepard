---
stage: concept
last-stage-change: 2026-05-31
---

# 110 — Database Best-Practices Catalogue

Living reference for developers writing new code against the four active substrates
in this fork: **Neo4j**, **PostgreSQL / TimescaleDB**, **MongoDB / GridFS**, and
**Garage / S3 (file storage)**. Each entry names a practice, points to the canonical
implementation, and names the anti-pattern to avoid.

This catalogue is updated whenever a new pattern is introduced or an existing
anti-pattern is fixed. The `DB-AP1` backlog row (see `aidocs/16`) is the sister
audit: it hunts for surviving violations; this document records the established
good shapes that should be replicated going forward.

---

## Neo4j

### BP-NEO1: Parameterised Cypher — never string-concatenated values

**Good shape:** `Neo4jQueryBuilder.ParamBinder` mints a fresh `$pN` placeholder for
every user-controlled value and accumulates the bindings in a `Map<String, Object>`
passed to the session. String, numeric, and boolean values are unwrapped to their
native Java type before being bound.

```java
// Neo4jQueryBuilder.java — ParamBinder.bind()
String bind(Object value) {
    String name = "p" + (counter++);
    params.put(name, value);
    return "$" + name;
}
// Callers: lowerCasedValuePart(), rawValuePart(), byPart(), atPart(), iRIPart(), …
```

**Anti-pattern:** `"MATCH (n:User) WHERE n.username = '" + username + "'"` — Cypher
injection; enables arbitrary graph mutation or exfiltration via crafted username strings.
This was the root cause of the C5/C5b security findings (see `aidocs/07-security-issues.md`).

**Files:**
- `backend/src/main/java/de/dlr/shepard/common/search/query/Neo4jQueryBuilder.java`
- `backend/src/main/java/de/dlr/shepard/common/search/query/Neo4jQuery.java`

---

### BP-NEO2: Property-identifier whitelist before splicing into Cypher

**Good shape:** Cypher cannot parameter-bind property *identifiers* (only values), so
`Neo4jQueryBuilder` maintains `KNOWN_PROPERTIES` (a whitelist of trusted property
names) and falls back to `SAFE_PROPERTY_NAME` (pattern `[A-Za-z_][A-Za-z0-9_|.]*`)
for any identifier not on the whitelist. Anything that fails both checks throws
`ShepardParserException` — the query is rejected before reaching Neo4j.

```java
// Neo4jQueryBuilder.java
private static final Set<String> KNOWN_PROPERTIES = Set.of(
    "name", "description", "createdAt", "updatedAt", "createdBy", "updatedBy",
    "deleted", "username");
private static final Pattern SAFE_PROPERTY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_|.]*");

private static String validatePropertyIdentifier(String property) {
    if (KNOWN_PROPERTIES.contains(property)) return property;
    if (SAFE_PROPERTY_NAME.matcher(property).matches()) return property;
    throw new ShepardParserException("invalid property name: " + property);
}
```

**Anti-pattern:** Splicing a user-supplied property name directly into the Cypher
string — `variable + "." + property` without validation — enables identifier injection
(e.g. `name} DETACH DELETE n RETURN {x` as a property name). C5 documented the exact
attack surface; the whitelist/pattern combo is the mitigation.

**Files:**
- `backend/src/main/java/de/dlr/shepard/common/search/query/Neo4jQueryBuilder.java`
  (`validatePropertyIdentifier`, `validateIriIdentifier`)

---

### BP-NEO3: UUID v7 `appId` on every persisted entity

**Good shape:** Every node entity that implements `HasAppId` receives a UUID v7
`appId` minted at first save by `GenericDAO.createOrUpdate()`. UUID v7 (RFC 9562)
embeds a millisecond Unix timestamp so identifiers are monotonic-per-millisecond and
compose with cursor pagination without a database round-trip.

```java
// GenericDAO.java — createOrUpdate()
if (entity instanceof HasAppId hasAppId && hasAppId.getAppId() == null) {
    hasAppId.setAppId(AppIdGenerator.next());  // UUID v7 via uuid-creator
}
session.save(entity, DEPTH_ENTITY);
```

The V11 migration adds `REQUIRE n.appId IS UNIQUE` per concrete label; V12 backfills
existing rows in 10 000-row chunks using `IN TRANSACTIONS` to avoid locking the whole
graph in one transaction.

**Anti-pattern:** Using Neo4j's internal `id()` function as the application-level
identifier. Internal ids are recycled by Neo4j after node deletion and expose
implementation details at the API surface. Using them in REST paths, MCP tool
arguments, or cross-substrate references creates coupling that breaks on node deletions
or database migrations. The L2 series (L2a–L2e) in `aidocs/platform/25` documents the
remediation.

**Files:**
- `backend/src/main/java/de/dlr/shepard/common/identifier/HasAppId.java`
- `backend/src/main/java/de/dlr/shepard/common/identifier/AppIdGenerator.java`
- `backend/src/main/java/de/dlr/shepard/common/neo4j/daos/GenericDAO.java`
- `backend/src/main/resources/neo4j/migrations/V11__Add_appId_unique_constraints.cypher`
- `backend/src/main/resources/neo4j/migrations/V12__Backfill_appId.cypher`

---

### BP-NEO4: Per-label `IF NOT EXISTS` uniqueness constraints — not on abstract base labels

**Good shape:** V11 creates one `REQUIRE n.appId IS UNIQUE` constraint per *concrete*
`@NodeEntity` label (e.g. `:Collection`, `:DataObject`, `:FileReference`) rather than
on abstract base labels (`:BasicEntity`, `:VersionableEntity`). This mirrors the OGM
class-per-label registry so the constraint surface stays accurate even if a future
entity gains or loses an inherited label. Every constraint is guarded with
`IF NOT EXISTS` so the migration is a no-op when re-run against a database that already
has it.

```cypher
-- V11__Add_appId_unique_constraints.cypher
CREATE CONSTRAINT appId_unique_Collection IF NOT EXISTS
    FOR (n:Collection) REQUIRE n.appId IS UNIQUE;
-- (one block per concrete label — 28 total)
```

**Anti-pattern:** One broad constraint on the base label (e.g. `:BasicEntity`) would
appear to work until a concrete subtype is introduced that Neo4j does not register under
the base label. The constraint becomes silently incomplete. Constraints without
`IF NOT EXISTS` fail on replay — they cannot be run from `cypher-shell` to verify a
fresh environment without first dropping them.

**Files:**
- `backend/src/main/resources/neo4j/migrations/V11__Add_appId_unique_constraints.cypher`

---

### BP-NEO5: Soft-delete (`deleted: FALSE`) — never hard-delete nodes that carry provenance edges

**Good shape:** Every `Deletable` node entity carries a `boolean deleted` field
(default `false`). All collection- and data-object-scoped queries append a
`AND NONE(node IN ns WHERE node.deleted = TRUE)` or equivalent `WHERE n.deleted = FALSE`
predicate via `CypherQueryHelper.getObjectPartWithoutName()`. Physically deleting a
graph node would orphan its `GENERATED` / `USED` provenance edges and break the HMAC
chain.

```java
// CypherQueryHelper.java
private static String getObjectPartWithoutName(String variable, String type) {
    var namePart = "{ deleted: FALSE }";
    return "(%s:%s %s)".formatted(variable, type, namePart);
}
// GenericDAO.getReturnPart() includes:
// AND NONE(node IN ns WHERE (node.deleted = TRUE))
```

**Anti-pattern:** Calling `session.delete(entity)` for user-visible entities. This
physically removes the node and all its relationships from the graph, including
`:Activity` provenance nodes pointing to it. The `deleteByNeo4jId()` method in
`GenericDAO` is reserved for internal cleanup of truly transient nodes (e.g. dangling
permission nodes); user-visible DataObjects, Collections, and References must only be
soft-deleted.

**Files:**
- `backend/src/main/java/de/dlr/shepard/common/neo4j/entities/Deletable.java`
- `backend/src/main/java/de/dlr/shepard/common/util/CypherQueryHelper.java`

---

### BP-NEO6: Batch permission resolution in one Cypher round-trip

**Good shape:** `PermissionsDAO.findByEntityNeo4jIds(Collection<Long>)` resolves
permissions for any number of entities in a single query using `WHERE e.appId IN $appIds`.
The OGM long ids are resolved to their appIds via `EntityIdResolver`, a `$appIds` list
parameter is bound to the query, and results are projected back to long-keyed entries by
reading the OGM id of the matched entities.

```java
// PermissionsDAO.java
params.put("appIds", new ArrayList<>(ogmIdByAppId.keySet()));
String query =
  "MATCH (e:BasicEntity)-[:has_permissions]->(p:Permissions) WHERE e.appId IN $appIds "
  + CypherQueryHelper.getReturnPart("p");
```

**Anti-pattern:** Calling `findByEntityNeo4jId(long)` in a loop — one network round-trip
per entity. For a collection of 500 DataObjects this means 500 sequential Cypher queries
where one would suffice, directly proportional to latency visible to the end user.

**Files:**
- `backend/src/main/java/de/dlr/shepard/auth/permission/daos/PermissionsDAO.java`

---

## PostgreSQL / TimescaleDB

### BP-PG1: `ADD COLUMN IF NOT EXISTS` with a nullable or defaulted type — never `NOT NULL` without a default

**Good shape:** New columns are always added with `ADD COLUMN IF NOT EXISTS` and a
`NULL`-accepting type (or a `DEFAULT` that covers all existing rows) so the migration
can be applied to a live database with millions of rows without a blocking full-table
backfill. If the column must eventually be `NOT NULL`, a separate step sets the
constraint *after* a `WHERE col IS NULL` backfill, guarded by an idempotency check.

```sql
-- V1.11.0__add_shepard_id_to_timeseries.sql
ALTER TABLE timeseries ADD COLUMN IF NOT EXISTS shepard_id UUID;
UPDATE timeseries SET shepard_id = gen_random_uuid() WHERE shepard_id IS NULL;
ALTER TABLE timeseries ALTER COLUMN shepard_id SET NOT NULL;
ALTER TABLE timeseries ALTER COLUMN shepard_id SET DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX IF NOT EXISTS idx_timeseries_shepard_id ON timeseries(shepard_id);
```

**Anti-pattern:** `ALTER TABLE timeseries ADD COLUMN new_col TEXT NOT NULL` on a table
with existing rows — fails immediately because existing rows cannot satisfy the
`NOT NULL` constraint. Requires a same-transaction `DEFAULT` that Postgres forces a
full-table rewrite for. On a multi-million-row hypertable this takes an
exclusive-lock rewrite, causing downtime. The timeseries channel table (5-tuple debt,
TS-CORE-SCHEMA-01) is the canonical example of the failure mode this rule prevents.

**Files:**
- `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql`
- `backend/src/main/resources/db/migration/V1.11.0_R__drop_shepard_id.sql` (rollback)

---

### BP-PG2: TimescaleDB compression with `segmentby` on the join column

**Good shape:** The `timeseries_data_points` hypertable is compressed with
`timescaledb.compress_segmentby = 'timeseries_id'` and
`timescaledb.compress_orderby = 'time'`. Segmenting by `timeseries_id` ensures that
all rows for one channel are co-located in the same compressed block so per-channel
range queries decompress only one segment. An automatic compression policy fires every
12 hours for chunks older than one day (86 400 000 000 000 ns).

```sql
-- V1.4.0__add_compression.sql
ALTER TABLE timeseries_data_points SET (
  timescaledb.compress,
  timescaledb.compress_segmentby = 'timeseries_id',
  timescaledb.compress_orderby = 'time'
);
SELECT add_compression_policy('timeseries_data_points', BIGINT '86400000000000');
```

**Anti-pattern:** Enabling compression with no `segmentby` clause. Without it,
compressed blocks mix rows from many channels; a per-channel range scan decompresses
multiple blocks even when only one channel is needed, negating most of the storage
savings and adding decompression overhead to every query. Forgetting to add the
automatic policy leaves old chunks uncompressed indefinitely.

**Files:**
- `backend/src/main/resources/db/migration/V1.4.0__add_compression.sql`
- `backend/src/main/resources/db/migration/V1.13.0__Optimize_timeseries_chunk_config.sql`

---

### BP-PG3: Continuous aggregate (`timeseries_hourly`) for wide-window overview queries

**Good shape:** `timeseries_hourly` is a TimescaleDB continuous aggregate that
pre-computes one row per `(timeseries_id, 1-hour bucket)` with `avg/min/max/count`. The
Java service layer (`TimeseriesService.getDataPointsLttbOptimised`) routes wide-window
queries to this view when `windowNs / maxPoints > CAGG_THRESHOLD_NS` (1 hour). A 6-month
overview reads at most ~4 380 pre-aggregated rows instead of scanning ~10M raw points.
The CAgg refresh policy covers the last 25 hours on a 1-hour schedule. CAgg compression
fires after 30 days.

```java
// TimeseriesDataPointRepository.java
static final long CAGG_THRESHOLD_NS = 3_600_000_000_000L;  // 1 hour
public static boolean shouldUseCagg(long windowNs, int maxPoints) {
    if (maxPoints <= 0 || windowNs <= 0) return false;
    return (windowNs / maxPoints) > CAGG_THRESHOLD_NS;
}
```

**Anti-pattern:** Sending all range queries to `timeseries_data_points` directly. For a
100 Hz channel viewed over 6 months that is ~15.7M rows per query — unbounded scan that
blocks under concurrent load. Using `time_bucket()` aggregations inline on the raw table
at query time is cheaper than full scans but still recomputes what the CAgg already
materialized.

**Files:**
- `backend/src/main/resources/db/migration/V1.12.1__add_hourly_cagg.sql`
- `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java`
  (`shouldUseCagg`, `queryCagg`, `CAGG_THRESHOLD_NS`)
- `backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java`
  (`getDataPointsLttbOptimised`)

---

### BP-PG4: Named-parameter binding for all SQL queries — no string concatenation

**Good shape:** All parameterised SQL queries in `TimeseriesDataPointRepository` use
JPA `setParameter(name, value)` bindings. The batch INSERT method constructs a query
template with placeholders (`:timeseriesid`, `:time{i}`, `:value{i}`) and binds each
value separately — never interpolates user data into the SQL string. A compile-time
static initialiser guards that the batch size cannot exceed the Postgres `Bind` int16
parameter limit (32 767):

```java
// TimeseriesDataPointRepository.java
static {
    int estimated = INSERT_BATCH_SIZE * PARAMS_PER_ROW_LOWER_BOUND + FIXED_PARAMS_PER_BATCH;
    if (estimated >= PG_BIND_PARAM_LIMIT) {
        throw new IllegalStateException("INSERT_BATCH_SIZE ... >= Postgres Bind parameter limit ...");
    }
}
// Query bindings:
query.setParameter("timeseriesId", timeseriesEntity.getId());
query.setParameter("startTimeNano", queryParams.getStartTime());
```

**Anti-pattern:** `"WHERE t.container_id = " + containerId` directly in a SQL string.
Even when `containerId` is a Neo4j-generated long (not user-supplied), inline
interpolation sets a pattern that, if imitated for user-supplied fields, becomes a SQL
injection vector. Use parameter binding unconditionally.

**Files:**
- `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java`
  (`buildInsertQueryObject`, `buildSelectQueryObject`, `INSERT_BATCH_SIZE` guard)

---

### BP-PG5: PostgreSQL `COPY` for bulk ingestion — not `INSERT … VALUES` batches for large loads

**Good shape:** `TimeseriesDataPointRepository.insertManyDataPointsWithCopyCommand`
uses the JDBC `CopyManager` API (`COPY timeseries_data_points FROM STDIN WITH (FORMAT csv)`)
for bulk ingest paths (InfluxDB migration, CSV import). The COPY protocol bypasses the
Postgres `Bind` message int16 parameter-count limit entirely and has 3–10x the
throughput of batch INSERT for row counts in the tens of thousands.

```java
// TimeseriesDataPointRepository.java
PGConnection pgConn = (PGConnection) conn.unwrap(PGConnection.class);
CopyManager copyManager = pgConn.getCopyAPI();
String sql = "COPY timeseries_data_points (timeseries_id, time, %s) FROM STDIN WITH (FORMAT csv);"
    .formatted(columnName);
copyManager.copyIn(sql, input);
```

**Anti-pattern:** Using the batched INSERT path for migration-scale loads (millions of
rows). The 20 000-row INSERT batch cap is sized for interactive ingestion; running
thousands of batches in a migration is both slower (round-trip overhead per batch) and
risks exceeding the Postgres parameter limit if batch parameters grow.

**Files:**
- `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java`
  (`insertManyDataPointsWithCopyCommand`, `insertManyDataPointsWithCopyCommandBatched`)

---

### BP-PG6: Idempotent migrations with `IF NOT EXISTS` and operator runbook comments

**Good shape:** Every Flyway migration uses `IF NOT EXISTS` guards on table creation,
index creation, and constraint addition. DO blocks that change hypertable configuration
check current state before acting and emit `RAISE NOTICE` in both the "changed" and
"already set" paths. Each migration file includes a top comment with: purpose, safety
note (non-blocking / blocking), idempotency guarantee, rollback instruction, and an
operator verification query.

```sql
-- V1.13.0__Optimize_timeseries_chunk_config.sql
-- IDEMPOTENCY: Both blocks check current state before making any change and emit
-- a NOTICE reporting what they did (or skipped). Safe to re-run on existing
-- instances...
-- Rollback: space partitions cannot be removed via SQL once added...
DO $$
DECLARE current_interval BIGINT; target_interval BIGINT := 3600000000000;
BEGIN
  SELECT integer_interval INTO current_interval FROM timescaledb_information.dimensions ...;
  IF current_interval IS NULL OR current_interval <> target_interval THEN
    PERFORM set_chunk_time_interval(...);
    RAISE NOTICE 'set chunk_time_interval to 1h; was %', current_interval;
  ELSE
    RAISE NOTICE 'chunk_time_interval already 1h, skipping';
  END IF;
END $$;
```

**Anti-pattern:** Migrations that run unconditionally (no `IF NOT EXISTS`) — they fail
on replay and cannot be pasted into `psql` to verify a fresh environment without first
dropping them. Migrations without operator runbook comments require the developer to
reverse-engineer the intent from SQL to understand whether a partial failure is safe to
retry.

**Files:**
- `backend/src/main/resources/db/migration/V1.11.0__add_shepard_id_to_timeseries.sql`
- `backend/src/main/resources/db/migration/V1.13.0__Optimize_timeseries_chunk_config.sql`
- `backend/src/main/resources/db/migration/V1.18.0__add_ts_datapoints_value_check.sql`

---

## MongoDB / GridFS

### BP-MON1: `$jsonSchema` validators on singleton collections via `MongoSchemaInitializer`

**Good shape:** `MongoSchemaInitializer` applies `$jsonSchema` validators to the three
fixed-name singleton collections (`userAvatars`, `_shepard_files`, `_shepard_videos`) at
application startup using `collMod`. The validator uses `validationAction: "warn"` (not
`"error"`) until existing data across all deployed instances is confirmed clean —
soft-fail first, tighten later. The initialiser is fail-soft (a failure on one
collection logs a `WARN` and continues to the next) because schema validation is a
data-quality aid, not a startup-blocking invariant.

```java
// MongoSchemaInitializer.java (MONGO-AUDIT-2026-05-24-005)
// collMod with a $jsonSchema validator on userAvatars, _shepard_files, _shepard_videos.
// validationAction: "warn" — soft-fail until data confirmed clean.
// Idempotent: calling collMod replaces an existing validator atomically.
```

**Anti-pattern:** No schema enforcement on MongoDB collections. Without validators,
a bug in any writer silently stores a malformed document; future readers either crash or
silently return null for missing required fields. This is exactly what MONGO-AUDIT-005
identified as the pre-existing state for these collections.

**Files:**
- `backend/src/main/java/de/dlr/shepard/common/mongoDB/MongoSchemaInitializer.java`

---

### BP-MON2: `$lookup` with `$convert` to bridge plain-hex-string `FileMongoId` to BSON ObjectId

**Good shape:** Shadow-collection bookkeeping documents (e.g. `_shepard_files`) store
the GridFS file id as a plain hex string in `FileMongoId`, while `fs.files._id` is a
BSON `ObjectId`. Any aggregation pipeline that joins these two collections must use
`$convert` (or `$toObjectId`) to bridge the type mismatch:

```javascript
// MongoSchemaInitializer.java — MONGO-AUDIT-007 documentation
{ $lookup: { from: "fs.files", let: { fid: "$FileMongoId" },
  pipeline: [{ $match: { $expr: { $eq: ["$_id", { $toObjectId: "$$fid" }] } } }],
  as: "file" } }
```

A direct `$lookup` with `localField: "FileMongoId"` and `foreignField: "_id"` returns
empty results because the string type does not match BSON `ObjectId`.

**Anti-pattern:** Using `$lookup: { localField: "FileMongoId", foreignField: "_id" }`
directly. This silently returns an empty `as` array, making the join look successful
while actually finding nothing — a data retrieval bug invisible at write time.

**Files:**
- `backend/src/main/java/de/dlr/shepard/common/mongoDB/MongoSchemaInitializer.java`
  (MONGO-AUDIT-007 comment)

---

### BP-MON3: Configurable upload size cap checked before GridFS write

**Good shape:** `FileService` reads `shepard.mongo.file.max-bytes` (default 2 GiB) and
rejects uploads that exceed the cap with a 400 `InvalidRequestException` before any
bytes reach GridFS. The check uses the caller-declared size (`Content-Length` or
multipart-declared size); when size is unknown (`<= 0`) the check is skipped to
preserve backwards compatibility.

```java
// FileService.java — MONGO-AUDIT-2026-05-24-012
void enforceFileSizeCap(long declaredSize) {
    if (mongoFileMaxBytes > 0 && declaredSize > mongoFileMaxBytes) {
        throw new InvalidRequestException(
            "File exceeds the maximum allowed size of " + mongoFileMaxBytes + " bytes");
    }
}
```

**Anti-pattern:** No size check before writing. A single oversized upload saturates
MongoDB working set, blocks the GridFS write path for all concurrent uploads, and may
exhaust available disk on the MongoDB host without any operator-visible rejection at the
API layer.

**Files:**
- `backend/src/main/java/de/dlr/shepard/data/file/services/FileService.java`
  (`enforceFileSizeCap`, `mongoFileMaxBytes`)

---

## File Storage (GridFS + Garage / S3)

### BP-FS1: `FileStorage` SPI — never reference the adapter class directly

**Good shape:** All file payload operations go through `FileStorageRegistry.requireActive()`,
which returns the `FileStorage` interface. Feature code only sees the `FileStorage`
contract; the active implementation (`GridFsFileStorage` or `S3FileStorage`) is
resolved at startup via the deploy-time `shepard.storage.provider` key. Switching
backends is a single config change with no feature-code touch.

```java
// FileStorageRegistry.java
public FileStorage requireActive() {
    FileStorage active = activeStorage;
    if (active != null) return active;
    throw new StorageNotInstalledException("No file-payload storage adapter is active...");
}
// Feature code:
FileStorage storage = fileStorageRegistry.requireActive();
StorageLocator locator = storage.put(request);
```

**Anti-pattern:** `new GridFsFileStorage()` or injecting `GridFsFileStorage` directly.
This hard-codes the backend, making the storage choice a compile-time decision rather
than a deploy-time operator choice. Any test or deployment that uses S3/Garage instead
of GridFS would require a code change.

**Files:**
- `backend/src/main/java/de/dlr/shepard/storage/FileStorage.java` (SPI interface)
- `backend/src/main/java/de/dlr/shepard/storage/FileStorageRegistry.java`
- `backend/src/main/java/de/dlr/shepard/storage/gridfs/GridFsFileStorage.java`
- `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`

---

### BP-FS2: Fail-soft registry — missing or disabled provider returns 503, not startup abort

**Good shape:** `FileStorageRegistry` does not fail-fast when no provider matches the
configured id, or when the matched provider reports `isEnabled() = false`. Instead it
sets `activeStorage = null`, logs a `WARN` with an operator-actionable hint, and lets
the file-payload REST endpoints return RFC 7807 `storage.provider.not-installed` (503)
on demand. An operator mid-swap (FS1e migration in flight) does not lose their entire
backend on a stale config typo.

```java
// FileStorageRegistry.java
if (picked == null) {
    this.activeStorage = null;
    Log.warnf("FileStorageRegistry: shepard.storage.provider=%s — no matching bean. " +
              "Available: %s. Install shepard-plugin-file-%s ...", want, available, want);
    return;
}
```

**Anti-pattern:** `throw new RuntimeException("storage not found")` in `@PostConstruct`
or in the startup event observer. A storage-configuration typo should not prevent the
entire Shepard instance from booting — search, provenance queries, and every non-file
operation are unaffected. The 503 is the correct signal to the operator; an abort is an
amplified blast radius.

**Files:**
- `backend/src/main/java/de/dlr/shepard/storage/FileStorageRegistry.java`

---

### BP-FS3: Disable S3 chunked encoding for Garage-compatible uploads

**Good shape:** `S3FileStorage` constructs its AWS SDK v2 client with
`chunkedEncodingEnabled(false)`. This forces the SDK to send a full SigV4 payload hash
in `x-amz-content-sha256` instead of the `STREAMING-AWS4-HMAC-SHA256-PAYLOAD` token
that Garage v1.0 rejects with `Invalid content sha256 hash: Invalid character 'S' at
position 0`. The unchunked form is accepted by real AWS S3 as well, so this is a
portable choice — no Garage-specific feature flag needed.

```java
// S3FileStorage.java — FS1b/VID1a comment
S3ClientBuilder builder = S3Client.builder()
    .httpClientBuilder(UrlConnectionHttpClient.builder())
    .serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(pathStyleAccess)
        .chunkedEncodingEnabled(false)   // required for Garage compatibility
        .build());
```

**Anti-pattern:** Using the AWS SDK default (chunked encoding enabled). Works for real
AWS S3 but silently breaks all video and large-file uploads to Garage with a cryptic
`invalid content sha256` error that only surfaces at the first streaming upload
(`RequestBody.fromInputStream(stream, sizeBytes)` triggers chunked mode whereas
`fromBytes(readAllBytes())` did not, masking the bug until the larger path was exercised).

**Files:**
- `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`
  (`init()`, FS1b/VID1a comment block)

---

### BP-FS4: Presigned PUT / GET URLs for client-direct uploads and downloads

**Good shape:** `S3FileStorage` implements the optional `presignedUploadUrl()` and
`presignedDownloadUrl()` methods from the `FileStorage` SPI. These return a short-lived
signed URL so the client uploads or downloads bytes directly to/from Garage/S3 without
routing through the Shepard JVM. The assigned object key is `containerMongoId/uuid`;
this is the locator value stored in Neo4j once the client commits via the confirm
endpoint. `GridFsFileStorage` returns `Optional.empty()` for both — the REST layer
falls back to the direct-through-backend path when presigning is unsupported.

```java
// FileStorage.java — SPI default (GridFS)
default Optional<PresignedPut> presignedUploadUrl(...) throws StorageException {
    return Optional.empty();
}
// S3FileStorage overrides both with real presigning.
```

**Anti-pattern:** Always streaming bytes through the JVM application tier. For a 500 MB
HDF5 file the backend becomes a 500 MB buffer, blocking request threads for the full
upload duration. Presigning offloads the data plane to the object store, leaving the
JVM free to handle other requests.

**Files:**
- `backend/src/main/java/de/dlr/shepard/storage/FileStorage.java` (`PresignedPut` record, default impls)
- `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`
  (`presignedUploadUrl`, `presignedDownloadUrl`, `presignedExportUrl`)

---

## Cross-cutting migration practices

### BP-MIGS1: Every data-mutating migration ships a paired rollback file

**Good shape:** Any Neo4j Cypher migration that changes data (not just adds a constraint
or index) ships a sibling `V(N)_R__*.cypher` rollback file. Any Postgres SQL migration
that changes schema ships a sibling `V(N.M.0)_R__*.sql`. These are *operator-run only*
(the migration frameworks do not execute them automatically) — they exist as
documented, tested escape hatches, not automation.

Examples in the codebase:
- `V12__Backfill_appId.cypher` paired with `V12_R__Rollback_Backfill_appId.cypher`
- `V14__Backfill_orphan_permissions.cypher` paired with `V14_R__Rollback_Backfill_orphan_permissions.cypher`
- `V1.11.0__add_shepard_id_to_timeseries.sql` paired with `V1.11.0_R__drop_shepard_id.sql`

**Anti-pattern:** Shipping a data-mutating migration with no rollback. The operator
has no clean path if the migration is applied to a production instance that later needs
to be rolled back. Even if the rollback file cannot automate everything, a documented
"here is how to undo this" is the minimum acceptable shape.

**Files:**
- `backend/src/main/resources/neo4j/migrations/V12_R__Rollback_Backfill_appId.cypher`
- `backend/src/main/resources/db/migration/V1.11.0_R__drop_shepard_id.sql`

---

### BP-MIGS2: `MigrationsRunner` fail-fast — abort startup on migration failure

**Good shape:** `MigrationsRunner.runMigrations()` catches `MigrationsException` and
re-throws it as `RuntimeException("Aborting startup: neo4j migration failed", e)`.
Flyway is configured to validate on migrate. A migration that fails hard during
backfill (e.g. `appId` uniqueness collision) causes an immediate, loud, logged startup
abort rather than silently proceeding with a partially-migrated database.

```java
// MigrationsRunner.java (lines 115-117)
} catch (MigrationsException e) {
    throw new RuntimeException("Aborting startup: neo4j migration failed", e);
}
```

**Anti-pattern:** Swallowing migration exceptions and continuing startup. A partially
applied backfill leaves the database in an inconsistent state that may appear functional
until a read path hits the unbackfilled rows, producing a confusing null-pointer or
constraint-violation error far removed from the actual failure site.

**Files:**
- `backend/src/main/java/de/dlr/shepard/common/neo4j/MigrationsRunner.java` (lines 115–117)
