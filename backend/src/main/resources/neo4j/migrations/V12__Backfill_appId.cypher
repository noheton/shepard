// L2b — Phase 2 of the Neo4j-ID migration (aidocs/25 §3.1, §4 Phase 2).
// Backfills `appId` on every pre-L2a node that is missing one. After L2a
// (commit fec7979 / PR #1003) every newly written node carries a UUID v7
// `appId`; this migration only touches rows older than that.
//
// Idempotent shape:
//   - `WHERE n.appId IS NULL` so reruns are no-ops on already-backfilled rows.
//   - The V11 unique constraint on `appId` per label guards against the
//     rare-but-possible v4 collision; on collision the chunk fails hard,
//     `MigrationsRunner.runMigrations` propagates `MigrationsException` as
//     `RuntimeException("Aborting startup: neo4j migration failed", …)`
//     (post-A1e fail-fast — `MigrationsRunner.java:115-117`), and the boot
//     fails with a clear stack trace. That is the intended behaviour.
//
// `randomUUID()` (Cypher built-in) is UUID v4 and per aidocs/25 §3.1 is
// acceptable for backfill: pre-migration rows predate the migration so do
// not need v7's time-ordered prefix. New writes continue to mint v7 via
// `AppIdGenerator.next()` at the DAO write boundary.
//
// Chunking strategy — `CALL { ... } IN TRANSACTIONS OF 10000 ROWS`. The
// neo4j-migrations 3.2.1 library detects the `IN TRANSACTIONS` clause via
// its `CALL_PATTERN` matcher and routes the statement through an implicit
// (autocommit) transaction even though the runner's default
// `MigrationsConfig.TransactionMode.PER_STATEMENT` would otherwise wrap it.
// This avoids locking millions of nodes in one transaction on a populated
// instance.
//
// Label list source: the per-label set is the authoritative subset shipped
// in V11__Add_appId_unique_constraints.cypher — same 28 labels, same order.
// Do NOT re-derive from the Java sources; V11's list is what's been
// deployed, and any drift between V11 and V12 leaves a row uniquely
// constrained but not backfilled (or backfilled but unconstrained).
//
// Operator runbook:
//   - Migration runs automatically on backend startup via MigrationsRunner.
//   - To run manually from `cypher-shell`, paste each `MATCH … CALL { … }
//     IN TRANSACTIONS …` block sequentially. Each block is independently
//     idempotent.
//   - Rollback: V12_R__Rollback_Backfill_appId.cypher. Operator-run only.

// auth
MATCH (n:User) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:UserGroup) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:ApiKey) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:Permissions) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;

// subscriptions
MATCH (n:Subscription) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;

// data containers and payloads
MATCH (n:FileContainer) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:ShepardFile) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:StructuredDataContainer) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:StructuredData) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:SpatialDataContainer) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:TimeseriesContainer) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
// `Timeseries` is the OGM label for ReferencedTimeseriesNodeEntity.
MATCH (n:Timeseries) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;

// references
MATCH (n:BasicReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:FileReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:URIReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:StructuredDataReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:SpatialDataReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:TimeseriesReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:CollectionReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:DataObjectReference) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;

// collections / data objects / lab journal
MATCH (n:Collection) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:DataObject) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:LabJournalEntry) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;

// semantic
MATCH (n:SemanticRepository) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:SemanticAnnotation) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:AnnotatableTimeseries) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;

// versioning
MATCH (n:VersionableEntity) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
MATCH (n:Version) WHERE n.appId IS NULL
CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS;
