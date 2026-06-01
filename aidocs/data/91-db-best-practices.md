---
stage: concept
last-stage-change: 2026-06-01
---

# 91 — Database best-practices catalogue (DB-BP1)

Living catalogue of good patterns across all six substrates in this repo. Authors
of new code copy from here. Each entry: pattern name → why it matters → where to
find a representative example → anti-pattern to avoid.

Companion docs:
- `aidocs/data/05-db-inventory.md` — full schema inventory (what exists, constraints, indices).
- `aidocs/platform/87-timeseries-appid-migration.md` — 5-tuple → single-id migration.
- `aidocs/data/12-timescaledb-performance-analysis.md` — TimescaleDB tuning rationale.

---

## Neo4j

### BP-N1 — Parametrised Cypher via `Neo4jQuery`

**Why it matters.** Cypher identifier and value injection let a caller escape query
context and read or write arbitrary graph data. This class of bug (designated C5 in
the project backlog) was eradicated in the C5/C5b fix pass; regressions are easy to
introduce if contributors naively string-concatenate values into query fragments.

**Pattern.** Every Cypher query that includes a caller-supplied value must bind that
value as a named parameter and carry it in a `Map<String, Object>`:

```java
// DO — bind user input as a parameter
String cypher = "MATCH (d:DataObject {appId: $appId, deleted: false}) RETURN d";
Map<String, Object> params = Map.of("appId", callerAppId);
Iterable<DataObject> results = session.query(DataObject.class, cypher, params);
```

The `Neo4jQuery` record (introduced with C5) is the canonical carrier:

```java
// Neo4jQueryBuilder output shape
public record Neo4jQuery(String cypher, Map<String, Object> params) {}
```

Callers of `Neo4jQuery`-returning methods must forward `params()` to the OGM session.
Forgetting the map means the `$p0` placeholder evaluates to `null` — not an injection
risk, but silently returns no rows.

**Representative location.** `common/search/query/Neo4jQuery.java` (record); callers
in `common/neo4j/daos/GenericDAO.java` (`getSearchForReachableReferencesByNeo4jIdQuery`
lines ~220–230); `Neo4jQueryBuilder` in `common/search/query/`.

**Anti-pattern.** Do NOT string-concatenate user data into Cypher:

```java
// DON'T — injection risk, C5 class
String cypher = "MATCH (d:DataObject {name: '" + userInput + "'}) RETURN d";
```

Property *names* cannot be bound as parameters in Cypher (only values can). Instead,
validate property names against a static whitelist (`KNOWN_PROPERTIES` in
`Neo4jQueryBuilder`) and use grave-accent quoting for any dynamic identifier.

---

### BP-N2 — `GenericDAO.createOrUpdate()` auto-mints `appId`

**Why it matters.** Every persisted Neo4j node that implements `HasAppId` must receive
a stable UUID v7 before its first write. Forgetting to call `AppIdGenerator.next()`
directly in service code is easy; the centralised gate in `GenericDAO.createOrUpdate()`
makes the mint automatic.

**Pattern.** Implement `HasAppId` on the OGM entity; call `createOrUpdate()` rather
than `session.save()` directly:

```java
// Entity
@NodeEntity
public class MyEntity extends AbstractEntity { /* inherits HasAppId via AbstractEntity */ }

// DAO — appId is minted here if still null
public T createOrUpdate(T entity) {
    if (entity instanceof HasAppId hasAppId && hasAppId.getAppId() == null) {
        hasAppId.setAppId(AppIdGenerator.next());
    }
    session.save(entity, DEPTH_ENTITY);
    return entity;
}
```

`AppIdGenerator.next()` returns a UUID v7 (time-ordered epoch, monotonic per
millisecond) via `com.github.f4b6a3:uuid-creator`.

**Representative location.** `common/neo4j/daos/GenericDAO.java` lines 125–156;
`common/identifier/AppIdGenerator.java`.

**Anti-pattern.** Do NOT call `session.save(entity)` directly from service code when
the entity has `appId == null` — the node lands in the graph without an identifier,
breaking every subsequent `appId`-keyed lookup and violating the L2 migration
invariant.

---

### BP-N3 — `IF NOT EXISTS` guards on every constraint and index migration

**Why it matters.** Neo4j migrations (using `neo4j-migrations`) run once per
instance; but dev stacks, CI, and multi-instance clusters can trigger the same
migration script more than once (e.g. after a data wipe). `IF NOT EXISTS` makes every
constraint and index idempotent — safe to re-run without failure.

**Pattern.** Every constraint or index in a `V##__*.cypher` file uses `IF NOT EXISTS`:

```cypher
CREATE CONSTRAINT appId_unique_Collection IF NOT EXISTS
    FOR (n:Collection) REQUIRE n.appId IS UNIQUE;

CREATE INDEX idx_BasicEntity_appId IF NOT EXISTS
    FOR (n:BasicEntity) ON (n.appId);
```

The uniqueness-only shape (no `IS NOT NULL` assertion) is deliberate for columns that
may be `null` on pre-migration rows — Neo4j 5 ignores `null` values in unique
constraints, so the constraint stays valid against an unbackfilled graph.

**Representative location.** `backend/src/main/resources/neo4j/migrations/V11__Add_appId_unique_constraints.cypher`
(full file, 60+ constraints); V13, V15, V17–V30 (successive per-label constraints).

**Anti-pattern.** Do NOT omit `IF NOT EXISTS` on constraint/index DDL. A migration
that fails on re-run blocks `MigrationsRunner.apply()`, which throws
`MigrationsException`, which aborts startup — taking down the entire instance.

---

### BP-N4 — `MigrationsRunner` fail-fast on startup

**Why it matters.** A migration that silently fails and lets the application start
with a broken schema produces unpredictable behaviour that is hard to diagnose.
`MigrationsRunner` catches `MigrationsException` and wraps it in a `RuntimeException`
that aborts startup (per the `CLAUDE.md` migration rule).

**Pattern.** No action required from migration authors — the runner enforces this
automatically. When writing a new Java migration (under
`common/neo4j/migrations/`), do not swallow exceptions; let them propagate to the
runner.

**Representative location.** `common/neo4j/MigrationsRunner.java` lines 109–118
(`runMigrations` static method).

---

### BP-N5 — Per-concrete-label constraint rather than on base labels

**Why it matters.** Constraints on abstract OGM base labels (e.g. `BasicEntity`,
`VersionableEntity`) would need to be re-evaluated on every structural refactor and
can produce confusing cardinality errors. Constraints scoped to the concrete label
(e.g. `Collection`, `DataObject`) mirror the OGM class-per-label registry and remain
accurate even if inheritance changes.

**Pattern.**

```cypher
-- DO: constraint on the concrete label
CREATE CONSTRAINT appId_unique_DataObject IF NOT EXISTS
    FOR (n:DataObject) REQUIRE n.appId IS UNIQUE;

-- NOT on the base label
-- CREATE CONSTRAINT ... FOR (n:BasicEntity) ...  -- avoid
```

**Representative location.** `V11__Add_appId_unique_constraints.cypher` comment block
lines 12–18 (design rationale) and every constraint in that file.

---

### BP-N6 — Rollback twin for every data-mutating migration

**Why it matters.** Schema-additive migrations (adding a constraint, adding a label)
are trivially reversible. Data-mutating migrations (backfills, relationship rewrites,
property deletions) are not. Every such migration ships a paired
`V##_R__*.cypher` rollback file so an operator can undo the change from
`cypher-shell` without setting up the project.

**Pattern.** File naming: `V12_R__Rollback_Backfill_appId.cypher` (paired with
`V12__Backfill_appId.cypher`). The rollback comment must reference the forward
migration and describe any irreversibility.

**Representative location.** `V12_R__Rollback_Backfill_appId.cypher`,
`V14_R__Rollback_Backfill_orphan_permissions.cypher`,
`V16_R__Rollback_Backfill_collection_properties.cypher` (all in
`backend/src/main/resources/neo4j/migrations/`).

---

## TimescaleDB

### BP-T1 — Hypertable with explicit chunk interval and space partitions

**Why it matters.** The default TimescaleDB chunk interval (7 days) is mismatched for
high-rate sensor ingest. At 180 Hz (AFP tapelaying) a 7-day chunk accumulates millions
of mixed-series rows with poor temporal locality. Tuning to 1-hour chunks with 4 space
partitions on `timeseries_id` (one per importer worker) eliminates lock contention on
parallel ingest.

**Pattern.**

```sql
-- Create hypertable with explicit 1-day chunk interval (nanosecond time column)
SELECT create_hypertable(
    'timeseries_data_points', 'time',
    chunk_time_interval => 86400000000000::BIGINT  -- 1 day in nanoseconds
);

-- Then tune to 1-hour chunks (V1.13.0)
PERFORM set_chunk_time_interval('timeseries_data_points', 3600000000000);

-- 4 space partitions for parallel writes
PERFORM add_dimension('timeseries_data_points', 'timeseries_id',
    number_partitions => 4, if_not_exists => true);
```

The `if_not_exists => true` flag on `add_dimension` makes the call idempotent.

**Representative location.** `backend/src/main/resources/db/migration/V1.0.0__setup_timeseries_tables.sql`
(initial hypertable); `V1.13.0__Optimize_timeseries_chunk_config.sql` (tuning with
`DO $$` idempotency checks).

**Anti-pattern.** Do NOT accept the default chunk interval for sensor data — 7-day
chunks fragment poorly for "give me series X over 10 hours" query patterns and force
full-chunk decompression on any backfill write against a compressed chunk.

---

### BP-T2 — Composite (timeseries_id, time DESC) index on the active window

**Why it matters.** TimescaleDB auto-creates a time-dimension index on hypertables but
does NOT create a composite index on `(timeseries_id, time)`. Without it, queries of
the form `WHERE timeseries_id = ? AND time BETWEEN ? AND ?` degenerate into a full
chunk scan on uncompressed chunks.

**Pattern.**

```sql
CREATE INDEX IF NOT EXISTS timeseries_data_points_id_time_idx
    ON timeseries_data_points (timeseries_id, time DESC);
```

The `DESC` ordering matches the common "most-recent first" query pattern.
`IF NOT EXISTS` makes the migration idempotent.

**Representative location.** `V1.8.0__optimize_timeseries_performance.sql` (section 1,
lines ~21–22); design rationale in same file comment block lines ~1–35.

---

### BP-T3 — Compression policy with 7-day delay (not 1 day)

**Why it matters.** Compressing chunks immediately after the chunk boundary (1-day
delay) means any backfill or out-of-order write against yesterday's data triggers a
segment decompression. A 7-day delay keeps a hot write/read window of one week
uncompressed, matching the TimescaleDB best-practice of "delay >= ~7 chunks."

**Pattern.**

```sql
ALTER TABLE timeseries_data_points SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'timeseries_id',
    timescaledb.compress_orderby   = 'time'
);

-- 7 days in nanoseconds — NOT the old 1-day policy
SELECT add_compression_policy('timeseries_data_points', BIGINT '604800000000000');
```

Use idempotent `remove_compression_policy(..., if_exists => true)` before
`add_compression_policy` when changing an existing policy.

**Representative location.** `V1.8.0__optimize_timeseries_performance.sql` (section 2);
`V1.4.0__add_compression.sql` (original 1-day policy — see this file as the
*anti-pattern* superseded by V1.8.0).

---

### BP-T4 — Continuous aggregate (`timeseries_hourly`) for overview queries

**Why it matters.** The primary chart query ("show me channel X over 6 months at
hourly resolution") without a continuous aggregate (CAgg) scans up to 10 M raw rows.
With the CAgg it reads ~4 380 pre-aggregated rows. The CAgg is maintained
incrementally by TimescaleDB at a 1-hour schedule.

**Pattern.**

```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS timeseries_hourly
WITH (timescaledb.continuous) AS
SELECT
    timeseries_id,
    time_bucket(3600000000000::BIGINT, time) AS hour_bucket,
    avg(double_value)::double precision       AS avg_double,
    min(double_value)::double precision       AS min_double,
    max(double_value)::double precision       AS max_double,
    count(*)::integer                         AS sample_count
FROM timeseries_data_points
GROUP BY timeseries_id, time_bucket(3600000000000::BIGINT, time)
WITH NO DATA;  -- defer initial materialisation
```

Gate the `add_continuous_aggregate_policy` call behind an `IF NOT EXISTS` check on
`timescaledb_information.jobs` so the migration is idempotent (the same pattern for
the compression policy on the CAgg itself).

**Representative location.** `V1.12.1__add_hourly_cagg.sql` (full file, ~78 lines;
includes refresh policy and CAgg compression).

---

### BP-T5 — Chunk-skipping on the high-cardinality secondary dimension

**Why it matters.** When a query spans many time chunks (e.g. "one channel over
1 year"), every chunk in the time range is opened unless TimescaleDB has per-chunk
min/max statistics for the non-time dimension (`timeseries_id`). Chunk-skipping
maintains those statistics and lets the planner prune chunks that contain no rows for
the requested series.

**Pattern.**

```sql
SELECT enable_chunk_skipping('timeseries_data_points', 'timeseries_id');
```

Requires TimescaleDB 2.16+.

**Representative location.** `V1.8.0__optimize_timeseries_performance.sql` (section 3,
lines ~40–47).

---

### BP-T6 — `ADD COLUMN IF NOT EXISTS` + three-step NOT NULL backfill

**Why it matters.** Adding a `NOT NULL` column with no default to a table with millions
of rows requires a full-table-lock backfill — incompatible with zero-downtime deploys.
The safe pattern: add nullable, backfill, tighten to `NOT NULL`, set default for
future inserts — all idempotent.

**Pattern.**

```sql
-- Step 1: add NULLABLE
ALTER TABLE timeseries ADD COLUMN IF NOT EXISTS shepard_id UUID;

-- Step 2: backfill (idempotent — skips rows that already have a value)
UPDATE timeseries SET shepard_id = gen_random_uuid() WHERE shepard_id IS NULL;

-- Step 3: tighten
ALTER TABLE timeseries ALTER COLUMN shepard_id SET NOT NULL;

-- Step 4: default for future inserts
ALTER TABLE timeseries ALTER COLUMN shepard_id SET DEFAULT gen_random_uuid();

-- Step 5: unique index (IF NOT EXISTS for idempotency)
CREATE UNIQUE INDEX IF NOT EXISTS idx_timeseries_shepard_id ON timeseries(shepard_id);
```

**Representative location.** `V1.11.0__add_shepard_id_to_timeseries.sql` (full file
with operator runbook comment).

---

## PostgreSQL

### BP-P1 — JPA `@Entity` + Panache `PanacheRepositoryBase` for plugin tables

**Why it matters.** Plugin Postgres tables need a lightweight, testable data layer.
The Quarkus Panache pattern (`PanacheRepositoryBase<Entity, PK>`) provides pagination,
ordering, and named query sugar without requiring hand-rolled JDBC. The `@Entity` +
`@Table` annotations are the least-surprise shape that future contributors (who know
JPA) can read immediately.

**Pattern.**

```java
// Entity
@Entity
@Table(name = "importer_run")
public class ImporterRun {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)      // store enum as text; no DDL change for new values
    @Column(name = "status", nullable = false, length = 16)
    private ImporterRunStatus status;

    @Column(name = "source_config", columnDefinition = "jsonb")
    private String sourceConfig;      // JSONB column mapped as String; parsed by service layer
    // ...
}

// Repository
@ApplicationScoped
public class ImporterRunRepository implements PanacheRepositoryBase<ImporterRun, UUID> {
    public Optional<ImporterRun> findOne(UUID id) {
        return Optional.ofNullable(findById(id));
    }
    public List<ImporterRun> listByPrincipal(String principal, ImporterRunStatus filter,
                                              int page, int size) {
        if (filter == null) {
            return find("principal = ?1 ORDER BY createdAt DESC", principal)
                .page(page, size).list();
        }
        // ...
    }
}
```

Use `@Enumerated(EnumType.STRING)` so that adding a new enum value is a zero-DDL
change; the `CHECK` constraint in the SQL migration is the belt-and-braces guard.

**Representative location.** `plugins/importer/src/main/java/de/dlr/shepard/plugins/importer/runs/ImporterRun.java`;
`ImporterRunRepository.java` (same package).

---

### BP-P2 — UUID v7 minted at service layer, not by Hibernate

**Why it matters.** Hibernate's UUID generator (`@GeneratedValue(strategy=AUTO)`)
assigns the ID only after the insert — the caller cannot know the ID before the
transaction commits. For async job tracking (e.g. importer runs) the caller needs the
ID at submission time to hand it to the user and to poll later.

**Pattern.**

```java
// Service layer: mint before insert
UUID id = UUID.fromString(AppIdGenerator.next());
ImporterRun run = new ImporterRun();
run.setId(id);
repository.persist(run);
// caller already has `id` — no round-trip needed
```

In the entity, mark the PK column `updatable = false` to prevent accidental re-ID.

**Representative location.** `ImporterRun.java` Javadoc (lines ~52–62, rationale);
`ImporterRunService` (service-layer mint pattern).

---

### BP-P3 — Partial indices on Postgres hot-path queries

**Why it matters.** A full index on `(status, last_progress_at)` wastes pages on
completed rows that are never queried by the reaper. A `WHERE status = 'RUNNING'`
partial index is smaller, fits in cache, and is faster for the one query that matters.

**Pattern.**

```sql
-- Partial index for the reaper ("what's stale and still RUNNING?")
CREATE INDEX IF NOT EXISTS idx_importer_run_status_progress
    ON importer_run (status, last_progress_at)
    WHERE status = 'RUNNING';

-- Partial index for terminal GC sweep
CREATE INDEX IF NOT EXISTS idx_importer_run_status_finished
    ON importer_run (status, finished_at)
    WHERE status IN ('SUCCEEDED','FAILED','CANCELLED');
```

**Representative location.** `plugins/importer/src/main/resources/db/migration/V1.11.1__add_importer_run_table.sql`
(lines ~94–114).

---

### BP-P4 — `ON CONFLICT DO NOTHING` for idempotent seed inserts

**Why it matters.** Seed migrations that re-run (e.g. on a developer wipe-and-restart)
must not fail on duplicate rows. `ON CONFLICT (pk_col) DO NOTHING` makes every seed
insert a no-op when the row already exists.

**Pattern.**

```sql
INSERT INTO predicate_vocabulary (predicate_uri, substrate, cardinality, ...) VALUES
    ('http://semantics.dlr.de/shepard-upper#status', 'neo4j', 'one', ...)
ON CONFLICT (predicate_uri) DO NOTHING;
```

The `CREATE TABLE IF NOT EXISTS` guard on the same file ensures the table exists
before the inserts run.

**Representative location.** `V1.16.0__Add_predicate_vocabulary.sql` (used on every
insert block; ~400 lines of seed data using this pattern throughout).

---

### BP-P5 — `TIMESTAMPTZ` for all timestamp columns

**Why it matters.** `TIMESTAMP WITHOUT TIME ZONE` stores wall-clock time but loses the
timezone, making comparisons across DST boundaries or multi-timezone deploys
ambiguous. `TIMESTAMPTZ` stores UTC internally and Postgres handles the display
conversion.

**Pattern.**

```sql
created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
started_at    TIMESTAMPTZ,         -- nullable lifecycle timestamps
finished_at   TIMESTAMPTZ,
```

**Representative location.** `V1.11.1__add_importer_run_table.sql` (every timestamp
column); `V1.10.0__add_permission_audit_log_table.sql` (`occurred_at TIMESTAMPTZ`).

---

## MongoDB

### BP-M1 — `$jsonSchema` validator applied at startup via `MongoSchemaInitializer`

**Why it matters.** MongoDB is schema-free by default; without a validator, service
bugs can insert malformed documents that break reads later. The `$jsonSchema`
validator on fixed-name singleton collections (e.g. `userAvatars`, `_shepard_files`)
enforces required fields and BSON types at write time. Using `warn` (not `error`)
allows existing pre-validator documents to survive reads while alerting on new
violations.

**Pattern.**

```java
@ApplicationScoped
public class MongoSchemaInitializer {
    void onStart(@Observes StartupEvent event) {
        applyValidator("userAvatars", buildUserAvatarsValidator());
        applyValidator("_shepard_files", buildShepardFilesValidator());
    }

    void applyValidator(String collectionName, Document validator) {
        try {
            if (existing.contains(collectionName)) {
                mongoDatabase.runCommand(new Document("collMod", collectionName)
                    .append("validator", validator)
                    .append("validationAction", "warn"));  // TODO: switch to "error" once data is clean
            } else {
                mongoDatabase.createCollection(collectionName, new CreateCollectionOptions()
                    .validationOptions(new ValidationOptions()
                        .validator(validator)
                        .validationAction(ValidationAction.WARN)));
            }
        } catch (Exception e) {
            Log.warnf("...");  // fail-soft: schema validation must not block startup
        }
    }
}
```

**Representative location.** `common/mongoDB/MongoSchemaInitializer.java` (full file,
289 lines).

**Anti-pattern.** Do NOT add `validationAction: "error"` on a collection that has
pre-existing documents without first confirming those documents all conform to the new
schema. A startup-blocking validator on a live collection is a data-quality incident
waiting to happen.

---

### BP-M2 — Fixed-prefix singleton collections vs. UUID-named container collections

**Why it matters.** MongoDB collection names encode their purpose. Singleton
infrastructure collections use a fixed `_shepard_*` prefix so they are easy to
distinguish from container-scoped collections (which are named after the Neo4j
container UUID). This naming convention is the only discriminator available in
`listCollectionNames()`.

**Pattern.**

| Collection name | Kind | Schema enforcement |
|---|---|---|
| `userAvatars` | Singleton — one doc per user | `MongoSchemaInitializer` validator |
| `_shepard_files` | Singleton — file bookkeeping | `MongoSchemaInitializer` validator |
| `_shepard_videos` | Singleton — video bookkeeping | `MongoSchemaInitializer` validator |
| `FileContainer<UUID>` | Per-container — dynamic name | No static validator (same shape, out of scope for V1) |
| `fs.files`, `fs.chunks` | GridFS chunks | MongoDB native |

**Representative location.** `MongoSchemaInitializer.java` (constants
`USER_AVATARS`, `SHEPARD_FILES`, `SHEPARD_VIDEOS`).

---

### BP-M3 — `$lookup` type bridge for `FileMongoId` to GridFS `_id`

**Why it matters.** Shadow-collection documents store the GridFS file id as a plain
hex string in `FileMongoId`, while `fs.files._id` is a BSON `ObjectId`. A naive
`$lookup` on these fields returns empty results. The bridge uses `$convert` via
`$toObjectId`.

**Pattern.**

```javascript
// Correct: convert hex string to ObjectId before joining
db.collection.aggregate([
    { $lookup: {
        from: "fs.files",
        let:  { fid: "$FileMongoId" },
        pipeline: [
            { $match: { $expr: { $eq: ["$_id", { $toObjectId: "$$fid" }] } } }
        ],
        as: "file"
    }}
])

// WRONG: silently returns empty results
db.collection.aggregate([
    { $lookup: { from: "fs.files", localField: "FileMongoId", foreignField: "_id", as: "file" }}
])
```

**Representative location.** `MongoSchemaInitializer.java` class Javadoc (lines
~63–73, MONGO-AUDIT-007 note).

**Remediation.** Migrate `FileMongoId` from hex string to native BSON `ObjectId` when
time allows (tracked as MONGO-AUDIT-2026-05-24-007 in `aidocs/16`).

---

### BP-M4 — Database name from URI path segment, not hardcoded fallback

**Why it matters.** Hardcoding `"database"` as the MongoDB database name is a silent
footgun: every dev and prod instance uses the same name, making data-isolation
accidents trivially easy. Deriving the name from the connection-string URI path
segment and warning loudly when the fallback fires forces operators to be explicit.

**Pattern.**

```properties
# application.properties
quarkus.mongodb.connection-string=mongodb://localhost:27017/shepard_prod
quarkus.mongodb.database=shepard_prod  # explicit override for clarity
```

```java
static String determineDatabaseName(String connectionString) {
    String dbName = new ConnectionString(connectionString).getDatabase();
    if (dbName == null || dbName.isBlank()) {
        return DEFAULT_DATABASE_NAME;  // "database" -- triggers operator WARN
    }
    return dbName;
}
```

**Representative location.** `common/mongoDB/MongoClientWrapper.java` (`determineDatabaseName`
and `shouldWarnAboutFallback` static methods).

---

## Garage / S3

### BP-S1 — Disable chunked encoding for S3-compatible endpoints

**Why it matters.** The AWS SDK v2 defaults to chunked transfer encoding
(`STREAMING-AWS4-HMAC-SHA256-PAYLOAD`) for `InputStream`-backed PUT requests.
Garage v1.0 rejects chunked requests with `Invalid content sha256 hash: Invalid
character 'S' at position 0`. Disabling chunked encoding makes the adapter portable
across AWS S3, Garage, MinIO, and LocalStack without requiring provider-specific
configuration.

**Pattern.**

```java
S3ClientBuilder builder = S3Client.builder()
    .serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(true)      // required by Garage / MinIO
        .chunkedEncodingEnabled(false)     // FS1b/VID1a -- disables STREAMING-AWS4 token
        .build());
```

Apply the same flag to `S3Presigner.builder()` — it shares the same protocol path.

**Representative location.** `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/S3FileStorage.java`
(lines ~130–136; comment explains the Garage-specific trigger and why the portable fix
was chosen over a Garage-specific flag).

---

### BP-S2 — Locator format: `<containerMongoId>/<uuid>` as the opaque key

**Why it matters.** The locator string stored in Neo4j's `:ShepardFile.providerId`
must be stable across bucket renames, storage-backend swaps (GridFS to S3), and
provider changes. Embedding the bucket name in the locator would break every existing
reference on a bucket rename. The opaque `<containerMongoId>/<uuid>` key isolates
topology from data.

**Pattern.**

```java
// On PUT: mint the key, store it as the locator
String uuid = UUID.randomUUID().toString();
String key = request.container() + "/" + uuid;
s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), body);
return new StorageLocator("s3", key);   // providerId + locator -- bucket not embedded

// On GET: resolve locator -> bytes; bucket from deploy-time config
GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(locator.locator()).build();
```

**Representative location.** `S3FileStorage.java` (`put` method, lines ~215–248;
Javadoc on locator format lines ~55–58).

---

### BP-S3 — `headBucket` + create-if-missing idempotent bucket setup

**Why it matters.** Relying on the bucket existing without checking causes a cryptic
`NoSuchBucketException` on first upload. Racing `createBucket` against another
starting instance can throw `BucketAlreadyOwnedByYouException`. The
`ensureBucketExists` pattern handles both.

**Pattern.**

```java
private void ensureBucketExists() {
    try {
        s3.headBucket(r -> r.bucket(bucket));   // fast: just a HEAD request
    } catch (S3Exception e) {
        try {
            s3.createBucket(r -> r.bucket(bucket));
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ok) {
            // race with another instance -- bucket now exists, carry on
        }
    }
}
```

**Representative location.** `S3FileStorage.java` lines ~185–201.

---

### BP-S4 — Presigned URL generation via `S3Presigner` (client-side upload/download)

**Why it matters.** Streaming large files through the Shepard backend wastes memory
and network capacity. Presigned PUT/GET URLs let the browser upload directly to
Garage/S3 while the backend records the locator on confirmation. The presigner shares
the same credentials and endpoint configuration as the main client.

**Pattern.**

```java
// Presigned PUT (FS1c -- direct-to-S3 upload)
PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
    .signatureDuration(ttl)
    .putObjectRequest(r -> r.bucket(bucket).key(key)
        .contentDisposition("attachment; filename=\"" + fileName + "\""))
    .build();
PresignedPutObjectRequest presigned = presigner.presignPutObject(presignReq);
return Optional.of(new PresignedPut(presigned.url().toURI(), uuid, Instant.now().plus(ttl)));
```

Use `Optional` so callers degrade gracefully when the provider is not enabled.

**Representative location.** `S3FileStorage.java` (`presignedUploadUrl` lines ~313–338;
`presignedDownloadUrl` lines ~390–410; `presignedExportUrl` lines ~348–383).

---

## PostGIS

### BP-G1 — Schema isolation: `shepard_spatial` schema

**Why it matters.** Co-locating PostGIS tables in the default `public` schema
alongside TimescaleDB tables creates naming collisions and makes access-control grants
harder. A dedicated `shepard_spatial` schema namespaces all spatial tables and allows
`GRANT USAGE ON SCHEMA` in a single statement.

**Pattern.**

```sql
CREATE SCHEMA IF NOT EXISTS shepard_spatial;
SET search_path TO shepard_spatial, public;

CREATE TABLE shepard_spatial.profile_container ( ... );
CREATE TABLE shepard_spatial.profile ( ... );
```

**Representative location.**
`plugins/spatiotemporal/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql`
(lines 26–28).

---

### BP-G2 — Discriminator + CHECK constraint for geometry-kind agreement

**Why it matters.** Storing a polygon geometry in a row whose `profile_kind = 'line'`
creates a schema inconsistency that is invisible at the SQL type level. A `CHECK`
constraint on the discriminator-geometry pair closes this at the database level (the
TS-AUDIT-003 anti-pattern) so the service layer cannot accidentally write mismatched
shapes.

**Pattern.**

```sql
CONSTRAINT chk_profile_kind_matches_geom CHECK (
    profile_kind IN ('point', 'line', 'polygon', 'tin', 'multipoint', 'tube_centerline') AND (
        (profile_kind = 'point'           AND profile IS NULL)
     OR (profile_kind = 'tube_centerline' AND ST_GeometryType(profile) IN ('ST_LineString'))
     OR (profile_kind = 'line'            AND ST_GeometryType(profile) IN ('ST_LineString'))
     OR (profile_kind = 'polygon'         AND ST_GeometryType(profile) IN ('ST_Polygon'))
     OR (profile_kind = 'multipoint'      AND ST_GeometryType(profile) IN ('ST_MultiPoint'))
     OR (profile_kind = 'tin'             AND ST_GeometryType(profile) IN ('ST_PolyhedralSurface', 'ST_Tin', 'ST_TIN'))
    )
),
```

**Representative location.** `V2.0.0__green_field_schema.sql` (lines ~103–116).

---

### BP-G3 — GIST index on geometry column (3D, `gist_geometry_ops_nd`)

**Why it matters.** The default PostGIS `gist_geometry_ops` operator class is 2D.
Shepard's spatial data is 3D (X, Y, Z). Using `gist_geometry_ops_nd` enables the 3D
`&&&` (N-dimensional bounding box intersect) operator used by bounding-sphere and KNN
queries — without it the planner cannot use the index for 3D predicates.

**Pattern.**

```sql
-- Anchor GIST (3D POINTZ column)
CREATE INDEX profile_anchor_gist
    ON shepard_spatial.profile USING GIST (anchor gist_geometry_ops_nd);

-- Profile geometry GIST (partial -- NULL for point-kind rows)
CREATE INDEX profile_geom_gist
    ON shepard_spatial.profile USING GIST (profile gist_geometry_ops_nd)
    WHERE profile IS NOT NULL;
```

The partial index saves cost on point-kind rows where `profile IS NULL`.

**Representative location.** `V2.0.0__green_field_schema.sql` (lines ~162–173).

---

### BP-G4 — BRIN index on monotonically-arriving time columns

**Why it matters.** A B-tree on a high-cardinality `BIGINT` time column consumes
substantial storage and is slower to build than BRIN for monotonic-arrival data. BRIN
stores only `(min, max)` per block range; for time-ordered data a 32-page-range BRIN
index provides nearly the same query performance at a fraction of the storage cost.

**Pattern.**

```sql
-- Time range queries: BRIN (monotonic-arrival, cheap pages)
CREATE INDEX profile_time_brin
    ON shepard_spatial.profile USING BRIN (time)
    WITH (pages_per_range = 32);
```

Applied retroactively to the V1 `spatial_data_points` table in `V1.1.0__indexes.sql`.

**Representative location.** `V2.0.0__green_field_schema.sql` (lines ~160–163);
`V1.1.0__indexes.sql` (lines ~25–27).

---

### BP-G5 — GIN index on `jsonb_path_ops` for JSONB measurement columns

**Why it matters.** Spatial data rows carry a `measurements` JSONB column for
field-value filters ("give me all points where `measurements.temperature > 20`").
`jsonb_path_ops` covers `@>`, `@?`, and `@@` operators used by containment/path
queries. A plain B-tree index on a JSONB column covers only equality on the whole
document.

**Pattern.**

```sql
CREATE INDEX profile_measurements_gin
    ON shepard_spatial.profile USING GIN (measurements jsonb_path_ops);
```

**Representative location.** `V2.0.0__green_field_schema.sql` (line ~177);
`V1.1.0__indexes.sql` (line ~31, same pattern on V1 `spatial_data_points`).

---

### BP-G6 — `ST_MakePoint` for typed geometry construction

**Why it matters.** Constructing geometry from raw coordinates using `ST_MakePoint`
rather than WKT strings avoids parsing overhead and type ambiguity. WKT strings must
be cast (`::geometry`) and can silently drop the Z coordinate if the variant is wrong.

**Pattern (from `SpatialDataPointRepository.java`).**

```java
// Build native INSERT with ST_MakePoint (3D) and typed JSONB casts
String valueClause = String.format(
    Locale.US,
    "%d, '%s', ST_MakePoint(%f, %f, %f), CAST('%s' AS JSONB), CAST('%s' AS JSONB)",
    containerId, data.getTime(),
    data.getPosition().getCoordinate().x,
    data.getPosition().getCoordinate().y,
    data.getPosition().getCoordinate().z,
    JsonConverter.convertToString(data.getMetadata()),
    JsonConverter.convertToString(data.getMeasurements())
);
```

**Representative location.**
`plugins/spatiotemporal/src/main/java/de/dlr/shepard/data/spatialdata/repositories/SpatialDataPointRepository.java`
(lines ~45–56; `insert` method body).

---

## Cross-cutting

### BP-X1 — Additive nullable columns everywhere; three-step NOT NULL

**Why it matters.** This is the single most-violated schema evolution rule. A
`NOT NULL` column with no default on a populated table requires a full-table-lock
backfill — incompatible with zero-downtime deploys. The rule applies to all substrates:

| Substrate | Pattern |
|---|---|
| Neo4j | New properties are always optional on OGM entities; absent on pre-migration rows treated as `null` at read time. |
| PostgreSQL / TimescaleDB | `ADD COLUMN IF NOT EXISTS` with no `NOT NULL`; backfill first; `ALTER COLUMN SET NOT NULL` last. |
| MongoDB | New fields in `$jsonSchema` must be absent from the `required` array for at least one release cycle. |

See BP-T6 for the canonical three-step SQL pattern.

---

### BP-X2 — `IF NOT EXISTS` / `ON CONFLICT DO NOTHING` idempotency guards everywhere

**Why it matters.** Migrations run at startup by both `MigrationsRunner` (Cypher) and
Flyway (SQL). Any non-idempotent DDL or DML that gets re-run (dev wipe, failed
half-migration, CI matrix) produces an error that aborts startup. The rules are:

- Cypher: `IF NOT EXISTS` on every `CREATE CONSTRAINT`, `CREATE INDEX`, `MERGE` seed.
- SQL: `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`,
  `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, `INSERT ... ON CONFLICT DO NOTHING`.
- Java migrations: guard every `session.query(...)` call that is not idempotent by
  nature.

---

### BP-X3 — `appId` (UUID v7) as the single cross-substrate identity

**Why it matters.** Every entity that lives in any substrate (Neo4j, Postgres,
TimescaleDB, MongoDB) carries a UUID v7 `appId` field. This is the only identifier
that crosses substrate boundaries. Semantic annotations, provenance edges, REST paths,
and MCP tool arguments all use this handle. Internal database IDs (Neo4j node IDs,
MongoDB ObjectIds, Postgres serial PKs) stay internal.

Pattern summary:
- Neo4j: implement `HasAppId`; minted by `GenericDAO.createOrUpdate()` via `AppIdGenerator.next()`.
- Postgres/TimescaleDB: `ADD COLUMN IF NOT EXISTS appId UUID` + `CREATE UNIQUE INDEX IF NOT EXISTS`.
- MongoDB: `AbstractMongoObject.appId` (String, `@BsonIgnore` — stored in the OGM layer for the Neo4j `HasAppId` contract, not in the Mongo document itself).
- Garage/S3: the S3 object key (`<container>/<uuid>`) is NOT the `appId` — the appId lives on the Neo4j `:ShepardFile` node; the S3 locator is the storage address.

---

### BP-X4 — Fail-fast migration runner; fail-soft secondary writes

**Why it matters.** Structural schema changes (migrations) must abort startup on
failure to prevent the application from running against a broken schema. Secondary
writes (provenance capture, HMAC chain, notification dispatch) must never propagate
exceptions to the caller — the primary operation succeeded; audit lag is acceptable.

| Context | Pattern |
|---|---|
| `MigrationsRunner.runMigrations()` | Catches `MigrationsException`, wraps in `RuntimeException`, aborts startup. |
| `MongoSchemaInitializer.applyValidator()` | Catches all exceptions, logs `WARN`, continues — schema validation is a data-quality aid. |
| `ProvenanceCaptureFilter` | Wraps secondary write in try/catch; logs `WARN` on failure. |

---

### BP-X5 — Rollback twin for every data-mutating migration (all substrates)

**Why it matters.** An operator who needs to roll back a data-mutating migration
without a pre-written rollback script must reconstruct the inverse transform under
pressure. Pre-shipping a `V##_R__*.cypher` / `*_R__*.sql` rollback file is cheap
and is the project standard.

File naming:
- Cypher: `V12_R__Rollback_Backfill_appId.cypher` (sibling of `V12__Backfill_appId.cypher`).
- SQL: `V1.11.0_R__drop_shepard_id.sql` (sibling of `V1.11.0__add_shepard_id_to_timeseries.sql`).

The rollback file must reference the forward migration in its header comment and
describe any irreversibility (e.g. if data written in a new column cannot be recovered
after dropping it).

Current rollback inventory: Neo4j V12, V14, V16, V34, V37, V61, V69, V70, V77–V87;
SQL V1.11.0, V1.14.0. See `aidocs/data/05-db-inventory.md` header for the live list.
