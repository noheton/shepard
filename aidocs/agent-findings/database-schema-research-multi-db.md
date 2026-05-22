# Database schema research — multi-substrate sweep (Neo4j + Postgres + MongoDB + PostGIS + Garage)

**Date:** 2026-05-22
**Scope:** Every backing store *except* the TimescaleDB hypertable.
**Companion:** TimescaleDB sweep at `aidocs/agent-findings/timescaledb-schema-research.md`
(commit `5523fa25`); §3 of this doc cross-references but does not re-state.
**Author:** research-only agent. No code, no migrations.

**TL;DR.** Four of five non-TS stores need *operational tweaks*, not a redesign.
The one exception is **PostGIS** (`shepard-plugin-spatial`): geometry-no-SRID,
nd-index-only, and 100 unmotivated hash partitions add up to a genuine partial
redesign before the plugin sees real load. The most valuable findings are
**cross-cutting** — the substrate-split is already in mid-flight (Neo4j vs.
n10s; GridFS vs. Garage), and the live `:DataObject` graph carries operational
+ identity + domain-data load that the SHACL substrate decision is intended to
peel apart.

---

## §0. Pointer to the TS sweep

The TimescaleDB hypertable (`timeseries_data_points`) and the `timeseries`
metadata table are already covered in `timescaledb-schema-research.md`. The
two findings from that doc that re-surface here, only because they cross
stores:

- The 5-tuple → `shepard_id` migration (V1.11.0 SQL) and the appId UUID v7
  primary key in Neo4j must agree on a cross-reference index — `§7.2` below.
- PgBouncer transaction-pool sizing and `prepareThreshold=0` apply **only**
  to the default datasource (the Timescale one). The spatial datasource
  bypasses PgBouncer — `§3.3` and `§5.4` below.

Everything else is fresh below.

---

## §1. Inventory

| Store | Image / version | Where the schema lives | Approx. row volume (today) | Hot pain |
|-------|-----------------|------------------------|---------------------------|----------|
| **Neo4j** | `neo4j:5.26` ([docker-compose.yml:68][cc-yml]) | `backend/src/main/resources/neo4j/migrations/V*.cypher` (63 files V3 → V63) | ~8.5 k `:DataObject` skeletons from MFFD ingest (per TS sweep §1); ~50+ node labels | Per-label index bloat from `deleted` flag on `AbstractEntity`; n10s `:Resource` graph shares the same physical store |
| **Postgres (relational)** | `timescale/timescaledb:2.24.0-pg16` ([cc-yml:134][cc-yml]) | `backend/src/main/resources/db/migration/V1.*.sql` + `plugins/importer/.../V1.11.1__add_importer_run_table.sql` | Five non-TS tables: `timeseries` (metadata, identity table), `migration_tasks`, `migration_progress`, `permission_audit_log`, `importer_run` | PgBouncer + `prepareThreshold=0` re-plans every batch; pool sized 20 |
| **MongoDB / GridFS** | `mongo:8.0` ([cc-yml:101][cc-yml]) | Per-`FileContainer` collections; **no Flyway** (Mongo has no schema migration framework wired) | One `MongoCollection<Document>` per `FileContainer` UUID; GridFS `fs.files` + `fs.chunks` for blobs | Collection-count grows with FileContainer count; legacy storage in flight to Garage |
| **PostGIS** | `postgis/postgis:16-3.5` ([cc-yml:150][cc-yml]) | `plugins/spatial/src/main/resources/db/spatial/migration/V1.0.0__setup_spatial_data_tables.sql` (one file) | 0 today; plugin off by default (`shepard.spatial-data.enabled=false`) | **No SRID constraint, nd-only index, 100 hash partitions** |
| **Garage S3** | `dxflrs/garage:v1.0.1` ([cc-yml:225][cc-yml]) | No DDL — bucket is bootstrapped manually per the commented runbook | One bucket today (`shepard.files.s3.bucket`); flat keyspace `<containerMongoId>/<uuid>` and `exports/<key>` | Single bucket; no lifecycle policies on `exports/` |

[cc-yml]: ../../infrastructure/docker-compose.yml

---

## §2. Neo4j — label + relationship audit

### 2.1 Label inventory (54 `@NodeEntity` classes)

54 OGM `@NodeEntity` classes in core backend; plugin code adds more (e.g.
`SpatialDataReference` in `plugins/spatial/`). The graph is a single physical
Neo4j 5.26 database that hosts:

- **Domain identity nodes** — `:Collection`, `:DataObject`, `:Annotation`
  (`:SemanticAnnotation`), `:Timeseries`, `:FileContainer`,
  `:StructuredDataContainer`, `:TimeseriesContainer`, plus reference-style
  bridges (`:DataObjectReference`, `:FileReference`, `:FileBundleReference`,
  `:TimeseriesReference`, `:StructuredDataReference`, `:URIReference`,
  `:CollectionReference`, `:VideoStreamReference`).
- **Provenance** — `:Activity` (PROV-O; HMAC-chained per `shacl-changeover-non-ts.md`),
  `:Snapshot`, `:SnapshotEntry`, `:VersionableEntity`, `:Version`,
  `:LabJournalEntry` + `:LabJournalEntryRevision`, `:PayloadVersion`,
  `:Publication`.
- **Admin singletons** (the A3b / N1c2 / UH1a `:*Config` pattern) — `:InstanceConfig`,
  `:UnhideConfig`, `:AiCapabilityConfig`, `:FeatureToggleRegistry`,
  `:SemanticConfig`, `:InstanceRorConfig`, `:SqlTimeseriesConfig`,
  `:DataciteMinterConfig`, `:EpicMinterConfig`, `:AasRegistration`.
- **Auth** — `:User`, `:UserGroup`, `:Role`, `:Permissions`, `:ApiKey`,
  `:GitCredential`, `:BootstrapState`.
- **Templates + plans** — `:ShepardTemplate`, `:ImportPlan`,
  `:TimeseriesContainerChartView`, `:Watch`, `:CollectionWatcher`,
  `:Notification`, `:Subscription`, `:PayloadKind`.
- **n10s-owned RDF graph** — `:Resource` (n10s convention) holding every
  imported ontology term. Lives in the same physical database
  (`N10sBootstrapHook.java`).
- **SHACL `:UserOntologyBundle`** + `:SemanticRepository` — n10s adapter
  bookkeeping, plus the `INTERNAL` singleton seeded by V49.

### 2.2 Constraint surface — appId is the workhorse

Cypher migrations V11 → V58 + V63 each carry one constraint:
`CREATE CONSTRAINT … FOR (n:Label) REQUIRE n.appId IS UNIQUE`. Coverage
is now wide: `:Collection`, `:DataObject`, `:CollectionProperties`,
`:Role`, `:Activity`, `:ShepardTemplate`, `:GitReference`,
`:GitCredential`, `:FileGroup`, `:SingletonFileReference`,
`:HdfContainer`, `:SemanticConfig`, `:UserOntologyBundle`,
`:Publication`, `:UnhideConfig`, `:DataciteMinterConfig`,
`:TimeseriesAnnotation`, `:VideoStreamReference`,
`:LabJournalEntryRevision`, `:Snapshot`, `:PayloadVersion`,
`:InstanceRorConfig`, `:SqlTimeseriesConfig`, `:EpicMinterConfig`,
`:AasRegistration`, `:TimeseriesContainerChartView`, `:Watch`,
`:VideoAnnotation`, `:AiCapabilityConfig`, `:InstanceConfig`,
`:LegacyV1Config`. **+ `:PluginRuntimeOverride.pluginId`** (V32).

**Gap.** `:User`, `:UserGroup`, `:ApiKey`, `:Permissions`,
`:BootstrapState`, `:Notification`, `:Subscription`,
`:CollectionReference`, `:DataObjectReference`, `:FileReference`,
`:FileBundleReference`, `:StructuredDataReference`, `:URIReference`,
`:TimeseriesReference`, `:SemanticAnnotation`, `:SemanticRepository`,
`:Annotation`-on-individual-data, `:CollectionWatcher`, `:Subscription`
— either no appId constraint, or appId not yet enforced. These nodes
sit on the v1 frozen surface (long-id is canonical) and the
substrate-split decision (§7) defers their appId enforcement to L2e.

### 2.3 The `deleted` index on `AbstractEntity` — bug-class

`AbstractEntity.java:44`:

```java
@Index
protected boolean deleted = false;
```

Every concrete `:Label` extending `AbstractEntity` gets a per-label index
on `deleted`. Neo4j docs and Cypher tuning guides recommend **against**
indexing low-cardinality booleans because (a) the planner rarely picks a
low-selectivity index over a label scan, (b) the index still costs write
amplification on every node creation, and (c) cardinality on a 2-value
domain skews the planner's row estimates [3][4].

Side-effect: the V44/V50/V51 fulltext index on `:Resource` labels does
**not** filter `deleted=false` (n10s `:Resource` nodes don't even have
the property). Soft-deleted user-ontology terms that **do** carry
`deleted` aren't filtered out of autocomplete results either, because
the fulltext query doesn't AND in `deleted=false`. **Real bug-class
finding** — investigate after this report.

### 2.4 Multi-parent feasibility for the SHACL substrate split

`:DataObject` today is **single-parent only**: `HAS_CHILD` is modelled
as `List<DataObject> children` and **scalar** `DataObject parent`
(`DataObject.java:36-40`). `HAS_SUCCESSOR` is many-to-many already.

The SHACL design (`aidocs/semantics/98 §1.5` "composition pattern")
needs *typed shape-bound parents* per shape — e.g. "this DataObject is
an AFP-layup *and* an NCR-investigation in two different process
trees." A scalar `parent` precludes this without a new relationship
type. **Recommendation in §8** — introduce `HAS_PARENT_<shapeKind>` as
a typed relationship via SHACL predicate lift (PR-5 in
`shacl-changeover-non-ts.md`), keeping the legacy `HAS_CHILD` for
the v1 surface, and leave `parent` as a derived projection.

### 2.5 Relationship-type vocabulary is freetext, lowercase, undocumented

`Constants.java:173-195`: relationship-type strings are lowercase
underscore (`has_dataobject`, `has_child`, `has_successor`,
`points_to`, `has_annotation`, `has_labjournalentry`). No central
controlled vocabulary, no PROV-O alignment (the ontologist agent's
review flagged this), no `subPropertyOf rdfs:`-style hierarchy. The
SHACL changeover (`shacl-changeover-non-ts.md` PR-5) is the right
moment to lift these to RDF-typed predicates — `shepard:hasChild
rdfs:subPropertyOf prov:wasDerivedFrom` etc. — without breaking the
OGM mapping.

---

## §3. Postgres (non-Timescale) — Hibernate Panache audit

### 3.1 Five non-TS tables on the **default datasource**

| Table | Purpose | Where | Notes |
|------|---------|-------|-------|
| `timeseries` | Channel metadata (5-tuple + `shepard_id`) — covered in TS sweep | `V1.0.0` + `V1.11.0` | Identity table, not TS data |
| `migration_tasks` | Container-level migration job state (legacy Influx→Timescale) | `V1.1.0`, `V1.5.0` | One row per container; UNIQUE `container_id` |
| `migration_progress` | Per-batch progress for the migrator | `V1.9.0` | One row per container; PK `container_id` |
| `permission_audit_log` | Append-only F3 audit trail | `V1.10.0` | TIMESTAMPTZ, `BIGSERIAL`; **no partitioning, no retention** |
| `importer_run` | IMP1a job-state for `shepard-plugin-importer` | `plugins/importer/V1.11.1` | UUID PK, jsonb payload/result/source_config |

**Plus** the spatial datasource on `postgis:5432` (§5).

### 3.2 `migration_tasks` vs `migration_progress` — they overlap but don't duplicate

V1.1.0 `migration_tasks` predates V1.9.0 `migration_progress`. They
serve different callers:

- `migration_tasks` is **backend-internal** — the Influx→Timescale
  cutover job (FileMigrationService cousin). State is `'Planned' |
  'Running' | 'Finished'`. UNIQUE `container_id`.
- `migration_progress` is a **side-channel** — the standalone
  migrator container writes here, the backend reads it via a status
  endpoint. State is `'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'`.

They share the `container_id` join key but their state-machine
vocabularies differ. **Not a bug** but a documentation gap: the
status enum vocabularies should converge in §8. The right merge target
is the IMP1a JobService kernel (`importer_run.status` =
`'PENDING'|'RUNNING'|'SUCCEEDED'|'FAILED'|'CANCELLED'`) — the
`aidocs/platform/32-long-running-process-pattern.md §3` design that's
already in flight.

### 3.3 PgBouncer + `prepareThreshold=0` is now obsolete

PgBouncer 1.21+ supports prepared statements in transaction-pool mode
[6][7]. The image `edoburu/pgbouncer:latest`
(`docker-compose.override.yml:134`) ships 1.23.1 — already past 1.21.
Dropping `?prepareThreshold=0` from the JDBC URL would re-enable
server-side prepared statements and recover the planner re-cost on
every batch INSERT that the TS sweep §2.1 already called out. **One
config change**; needs a pin to a known-good PgBouncer tag (no longer
`:latest` — that's an operational hygiene fix too) plus a smoke test.

### 3.4 Indexes on the hot read paths

- `permission_audit_log` — `(entity_app_id)` + `(occurred_at DESC)`
  partials. Reasonable. Will degrade after ~10 M rows without
  partitioning; the table comment explicitly defers retention to the
  operator. **Recommendation**: pg_partman daily partition once F3
  audit volume grows past ~100 k rows/day.
- `importer_run` — three partial indexes covering "my running jobs",
  "GC scan", "per-collection imports". Solid for IMP1a load.

### 3.5 Second datasource: spatial bypasses PgBouncer

`docker-compose.override.yml:113` and `:20`:

```
QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://pgbouncer:5432/...?prepareThreshold=0
QUARKUS_DATASOURCE_SPATIAL_JDBC_URL: jdbc:postgresql://postgis:5432/...
```

The spatial Hibernate ORM (`quarkus.hibernate-orm.spatial.datasource=spatial`)
talks **direct** to PostGIS. **Prepared statements are normal there.** This is
fine today (spatial plugin off by default), but worth flagging: the operational
profile differs between the two Postgres-backed datasources, and the TS sweep's
`§2.3` "raise pool size" recommendation doesn't apply to spatial.

---

## §4. MongoDB / GridFS — schema oddities + migration progress

### 4.1 One Mongo collection per `FileContainer`

`FileService.java:81`, `:129`, `:172`, `:205`, `:230`:
`mongoDatabase.getCollection(containerId)` with `containerId = "FileContainer<UUID>"`.

This is **unusual** — collection-count grows linearly with FileContainer
count. Each collection holds the per-file bookkeeping documents; the
actual bytes live in the **single shared** `fs.files` + `fs.chunks`
GridFS bucket (`createBucket()` returns `GridFSBuckets.create(mongoDatabase)`
— the *default* bucket name, shared across all containers).

**Implication.** Operators with thousands of FileContainers will see
thousands of Mongo collections in the catalogue. MongoDB doesn't hard-cap
collection count, but performance degrades and `db.collectionNames()`
slows. Move bookkeeping documents to **one shared collection with a
`containerId` index**, keyed off the same string. Defer until the
GridFS → Garage migration completes; do it as part of the wind-down,
not as a parallel refactor.

### 4.2 No Flyway

The Mongo store has **no schema-migration framework**. The `migrations`
directory only covers Neo4j (`neo4j/migrations/`) and Postgres
(`db/migration/` + plugin-side migrations). Mongo collections are
created on first write — schemaless. The collection rename from V21
(File*Reference → File*BundleReference) is documented on the **Neo4j**
side only; the matching MongoDB collection renames (if any) happen in
Java code, not in a migration. This is fine while the schema is
"random `Document`" but a problem the moment a bookkeeping document
shape changes — there's no idempotent script to re-shape existing
docs. **Recommendation in §8**: don't add Mongoose-style migrations
now; instead, accelerate the Garage cutover so Mongo can be retired
as a write target.

### 4.3 GridFS → Garage migration is half-shipped

- The Neo4j `:ShepardFile` nodes carry `providerId` (V34, post-FS1a).
  This means **Neo4j is canonical** for "what files exist + where they
  live"; Mongo and Garage are *byte backends* the Neo4j row points at.
  This collapses the "which is canonical when they diverge?" question
  from `§7` to a clean answer: **Neo4j wins**, GridFS / Garage are
  caches of the bytes. Anything left in Mongo but not in Neo4j is an
  orphan; anything in Neo4j but not in Mongo (after Garage cutover) is
  a recoverable read miss only if the provider id stays consistent.
- `FileMigrationService.java` already implements per-oid migration
  with idempotent moves between providers.
- AWS's GridFS → S3 migration playbook [1] matches the
  Shepard approach: store the **reference** in the metadata DB (Neo4j
  here) and use S3 as the byte plane.

### 4.4 What's left to do (Mongo-side)

- Wire a `FileMigrationService.triggerMigration("gridfs", "s3")`
  invocation as the operator's documented cutover sequence.
- Once all `:ShepardFile.providerId='s3'`, drop the GridFS
  `MongoCollection<Document>` setup. Keep the Mongo container itself
  only if any other Mongo-resident features need it (none today).
- Don't migrate the per-FileContainer collection shape ahead of the
  cutover — same as §4.1.

---

## §5. PostGIS — the one store with the most-pressing schema issue

### 5.1 Schema today (`V1.0.0__setup_spatial_data_tables.sql`)

```sql
CREATE TABLE spatial_data_points (
  id bigint generated by default as identity,
  container_id bigint not null,
  time bigint not null,
  position GEOMETRY not null,           -- ← no SRID, no type modifier
  metadata JSONB,
  measurements JSONB
) PARTITION BY HASH (container_id);

-- 100 partitions created in a DO $$ loop
-- (spatial_data_points_p0 .. spatial_data_points_p99)

CREATE INDEX … ON spatial_data_points(container_id);
CREATE INDEX … ON spatial_data_points USING GIST (position gist_geometry_ops_nd);
CREATE INDEX … ON spatial_data_points USING GIN(metadata);
```

### 5.2 Three real problems

**(a) `position GEOMETRY` without SRID or type modifier.** Per the PostGIS
docs [2], the recommended shape is
`geometry(POINTZ,4326)` (or equivalent typed declaration) — both type
and SRID constraints. The current declaration accepts mixed-SRID, mixed-
type geometries; nothing prevents one insert with SRID 4326 (WGS84)
and the next with SRID 3857 (Web Mercator) or 0 (unknown). **Once any
real spatial data lands, queries with `ST_DWithin(pos, …, distance)`
will be wrong** because PostGIS doesn't auto-project at query time
without a SRID. The doc comment on `SpatialDataPoint.position`
acknowledges 3D-only usage today — the right migration is `ALTER COLUMN
position TYPE geometry(POINTZ, 4326)` (or whichever SRID DLR aerospace
data targets — load-bearing decision in §9).

**(b) `gist_geometry_ops_nd` only.** The n-dimensional operator class
correctly supports 3D points, but **2D queries** (`ST_Within(pos,
polygon)`, `ST_DWithin(pos, pt, dist)` against a 2D polygon) **can't
use it** — they need the standard 2D operator class `gist_geometry_ops_2d`.
Today's NativeQueryStringBuilder doesn't appear to issue 2D ops, so this
is latent. But the moment a 2D bbox filter lands (e.g. "find all spatial
points inside this map rectangle"), the planner will full-scan the
hypertable. **Fix: keep the nd index and add a 2D companion index on the
same column** when 2D queries are exercised.

**(c) 100 hash partitions on `container_id` is unmotivated.** Postgres
hash partitioning makes sense when:

1. A single dominant key has high cardinality (true here, container ids).
2. Per-partition row count is **roughly equal** (hash distributes randomly).
3. The expected partition size warrants the planning overhead.

In Shepard's case: there's no published workload model for how many
SpatialDataContainers an instance will have, and the rows-per-container
distribution is almost certainly *skewed* (a few big containers, many
empty). With 100 partitions, a single dominant container goes to one
partition that ends up with ~99% of the rows — **the partitioning makes
the problem worse, not better**, because lookups still all hit one
partition while constraint exclusion buys nothing. The standard
PostGIS pattern is **no partitioning** for ≤1 B rows and BRIN/GiST as
the primary access methods. Recommend: **drop hash partitioning** in a
migration, or switch to **range partitioning on `time`** if temporal
queries dominate.

### 5.3 SRID standardization is a load-bearing decision

§9 records `[NEEDS-CLARIFICATION]` for SRID 4326 (WGS84 lat/lon, the
universal interchange) vs. SRID 3857 (Web Mercator, the tile-server
default) vs. a UTM zone (best accuracy for a fixed region — Augsburg
ZLP would be UTM zone 32N, EPSG:25832). Aerospace test data from a
fixed site is best stored in UTM (preserves distance in metres); web
visualisation re-projects to 3857 client-side. **Default lean: 25832
for the ZLP-bound MFFD use case, with a per-container SRID override
field for cross-site data.**

### 5.4 Plus: spatial datasource bypasses PgBouncer

Confirmed in §3.5. The spatial plugin's Hibernate ORM goes direct to
`postgis:5432`. Mixed pool sizing — if spatial workload spikes, it
won't be throttled by PgBouncer's pool, but it also can't share that
pool's idle slack.

---

## §6. Garage S3 — bucket layout + lifecycle

### 6.1 Single bucket, flat keyspace

`S3FileStorage.java:99` — bucket name read from
`shepard.files.s3.bucket` (deploy-time, single value). Object key shapes:

- `<containerMongoId>/<uuid>` for payload files (`put`, line 209).
- `exports/<key>` for RO-Crate / collection-export ZIPs (line 341).
- Future presigned-PUT uploads share the payload prefix (line 307).

Bucket creation is **eager**: `S3FileStorage.init()` (line 176) calls
`headBucket`, and on `NoSuchBucket` calls `createBucket`. This means
the bucket is auto-created on first backend start with `provider=s3`.

### 6.2 No lifecycle policy on `exports/`

The doc comment on `presignedExportUrl` (line 334-336) explicitly notes
that operators should configure a 24 h lifecycle on the `exports/`
prefix; the plugin itself doesn't create it. **Implication**: stale
RO-Crate ZIPs accumulate indefinitely. Operators reading
`docs/reference/file-storage.md` will know; operators who follow only
ADR-0024 may not. **Recommendation in §8**: ship a Garage CLI snippet
(`/garage bucket lifecycle …`) as part of the install runbook.

### 6.3 No encryption-at-rest layer

Garage stores plaintext blobs on disk. No SSE-S3-style server-side
encryption is configured (`PutObjectRequest.builder()` line 211 sets
neither `ssec` nor `kms`). For DLR aerospace IP (MFFD industrial data
on the ZLP site), this matters. Garage's own encryption story is
"encrypt the underlying filesystem" — fine on a single host with LUKS,
but not portable. **Recommendation**: document the operator's
disk-encryption expectation in `plugins/file-s3/docs/install.md`; that
file should call out that bucket-level SSE is not provided by Garage
today, so OS-level encryption is the canonical answer.

### 6.4 Bucket-per-tenant vs. per-purpose — both deferred

Shepard is single-tenant per-instance today (the institute deploys
their own). Bucket-per-tenant is **not relevant** in the current
posture. Bucket-per-purpose (`shepard-payload`, `shepard-exports`,
`shepard-snapshots`) **is** an option — it would let operators apply
different lifecycle rules per bucket. The AWS multi-tenant guidance
[5] is consistent: ≤100 buckets prefers per-purpose;
≥1 k tenants prefers single-bucket with prefix isolation. Shepard at
"one bucket today" is in the prefix-isolation camp. **Defer the
split** until per-purpose retention rules differ — at which point
`exports/`, `payloads/`, `snapshots/` cleanly become bucket-per-purpose.

---

## §7. Cross-cutting findings — the highest-value section

### 7.1 Neo4j is the canonical "what files exist + where they live" registry — both Mongo and Garage are bytes backends

The V34 `providerId` backfill makes this explicit. `:ShepardFile.providerId
∈ {gridfs, s3, …}` + the per-row `oid` (Mongo ObjectId hex or S3 object
key) = the locator. The `:DataObject` graph is therefore unaffected by
the GridFS → Garage cutover; the byte tier swaps under it. **This is
the right architectural shape** — confirmed by AWS's GridFS → S3
playbook [1] and the Coscine architecture (metadata in
a graph store, bytes in an S3 backend per their conference papers,
exact URL behind RWTH SSO so unverifiable here).

**Action**: document this in `docs/reference/file-storage.md` as the
single-source-of-truth rule. Future plugins (HDF5, video, CAD) must
NOT mint their own per-store identity — they extend `:ShepardFile`
(or its appId+providerId convention) and pick a `FileStorage` adapter.

### 7.2 The shepardId column in Postgres `timeseries` and the appId in Neo4j `:Timeseries` must agree on a cross-reference index

The 5-tuple → shepardId migration (TS sweep §1) introduces a `shepard_id
UUID NOT NULL UNIQUE` on the Postgres `timeseries` table. Neo4j-side,
the `:Timeseries` `@NodeEntity` carries its own `appId` (V11
constraint). **Two unique appIds for the same concept.** The L2d-shipped
v2 endpoints must use the *Postgres `shepard_id`* as the canonical
external identifier (it's the one TS data points join against in
TimescaleDB); the Neo4j `:Timeseries.appId` becomes a secondary index
or — better — is dropped in favour of reusing the Postgres value.

**Action**: either (a) backfill `:Timeseries.appId` =
`timeseries.shepard_id` (one Cypher script over a JDBC bridge), or
(b) drop the appId constraint on `:Timeseries` (V35
`@AddAppIdConstraint(TimeseriesAnnotation)` is for *annotations*, not
the channel — but verify before acting).

### 7.3 PostGIS spatial entities don't yet have a Neo4j `:Spatial` shadow

Today's `SpatialDataReference` (Neo4j @NodeEntity) is the bridge from
the graph to the PostGIS row by container id — same pattern as
`:Timeseries` → TimescaleDB. **Good.** Don't add a `:Spatial` mirror
node per point — that would re-introduce the dual-store divergence
problem.

### 7.4 SHACL substrate placement — n10s in same Neo4j today, extraction is a future decision

`feedback_shacl_single_source_of_truth.md` calls for "domain data data"
→ SHACL graph, "operational/identity data" → Neo4j. Today the SHACL
graph is **physically the same Neo4j store** (n10s + the in-process
Jena validator). This is fine for the foundation slice
(`shacl-changeover-non-ts.md` PR-1, PR-3, PR-4 landed) but won't
scale once SHACL shapes become the single source of truth for domain
data (PR-5 predicate lift; PR-6 NCR/AFP/welding shapes). The substrate
split is a **logical** split, not yet a physical one.

Three physical placements possible (all loud `[NEEDS-CLARIFICATION 1]`
in §9):

1. **Stay co-located in Neo4j.** Cheapest. n10s is already wired. The
   downside: Neo4j is now load-bearing for both operational (DAOs,
   permission queries) and domain-data (SHACL validation, agent
   contracts) reads. One slow ontology import slows everything.
2. **Extract to Apache Jena Fuseki / TDB** as a sibling service.
   Clean substrate split. Per the `aidocs/98 §1` design, this is
   the eventual target. Adds a database (operator pain).
3. **Extract to Postgres with `rdfox` or `Apache Jena +
   pg-backed-graph` adapter.** Reuses the Postgres operator skillset.
   Performance unproven for SHACL-heavy workloads.

**Default lean (option 2)** once SHACL becomes write-side authoritative,
not before. Until PR-5 lands, the same-Neo4j placement is fine.

### 7.5 PgBouncer applies only to the default datasource — spatial is direct

Repeat of §3.5 for cross-cut visibility: the JDBC URLs differ, the
pooling story differs, and any TS-sweep-flavour recommendation
("raise the pool", "drop prepareThreshold") is **default-datasource
only**. Spatial datasource's prepared-statement story is normal.

### 7.6 The `deleted` boolean index is a write-amplification cost across the entire graph

Every `:Label` extending `AbstractEntity` carries the per-label index
on `deleted`. With 54 entity classes the total index-write cost on
each Cypher `CREATE` is non-trivial. The Cypher planner rarely picks
a low-cardinality index over a label scan anyway [3]. **Remove the
`@Index` annotation** and replace per-query with `WHERE n.deleted = false`
+ label-only access. Coordinate with v1 sunset (the v1 surface still
honours soft-delete; the v2 surface can do it differently).

---

## §8. Recommended changes — ranked

| # | Store | Change | Effort | Breaking? | Coordinate with |
|---|-------|--------|--------|-----------|-----------------|
| **1** | PostGIS | Add SRID + geometry-type constraint; drop hash partitioning (or switch to range-on-time); add 2D companion index | M (one migration on a low-volume table) | Yes — existing rows must be re-typed; plugin off by default = low risk today | `[NEEDS-CLARIFICATION 1]` SRID; do **before** spatial plugin ships at a customer |
| **2** | Neo4j | Drop `@Index` on `AbstractEntity.deleted`; rewrite hot queries to use label-scan + WHERE clause | M (54 entities, ~20 hot Cypher queries) | No — index drop is reversible; queries become explicit | None — pure win |
| **3** | Postgres (default) | Drop `?prepareThreshold=0` from JDBC URL; pin PgBouncer image to a known-good 1.21+ tag (no `:latest`) | S | Yes — operational; revertible | TS sweep §4 — combine with the pool-size raise |
| **4** | MongoDB | Accelerate GridFS → Garage migration; document the `FileMigrationService.triggerMigration("gridfs","s3")` runbook in `plugins/file-s3/docs/install.md` + `docs/reference/file-storage.md` | M | No (FS1a registry abstracts the swap) | ADR-0024 Garage |
| **5** | Garage | Document operator lifecycle policy on `exports/` (24 h auto-cleanup); document disk-encryption posture | S | No | Operator runbook only |
| **6** | Neo4j | Lift relationship-type vocabulary to RDF-typed predicates (PR-5 in `shacl-changeover-non-ts.md`) | L | No — additive; legacy types stay as `subPropertyOf` aliases | SHACL changeover PR-5; defer past Q3 |
| **7** | Cross-cut (Neo4j ↔ Postgres) | Reconcile `:Timeseries.appId` (Neo4j) with `timeseries.shepard_id` (Postgres) — single canonical UUID per channel | M | Yes (API surface) | `aidocs/platform/87-timeseries-appid-migration.md` |
| **8** | Postgres (default) | Plan `permission_audit_log` partitioning (pg_partman daily) before audit volume exceeds 100 k rows/day | M | No — additive | F3 audit retention policy |
| **9** | Mongo | Merge per-container `MongoCollection<Document>` into one shared `file_records` collection with `containerId` index | L | Yes (read path) | Coordinate with Mongo retirement (#4); **do not do separately** |
| **10** | Cross-cut | Decide SHACL substrate physical placement (stay co-located vs. Fuseki vs. pg-backed-Jena) | XL | Yes | `[NEEDS-CLARIFICATION 1]`; defer past SHACL PR-5 |

---

## §9. `[NEEDS-CLARIFICATION]`

[NEEDS-CLARIFICATION 1 — load-bearing]
**SHACL substrate physical placement once PR-5 lifts predicates.**
Today: n10s and the in-process Jena validator live in the same Neo4j
instance — fine for the foundation slice. Future options:
- **(a)** Stay co-located in Neo4j (cheapest; performance risk: one
  slow ontology import slows operational queries).
- **(b)** Extract to Apache Jena Fuseki as a sibling service (clean
  split; operator pain: +1 store).
- **(c)** Extract to Postgres-backed Jena adapter (reuses Postgres
  operator skillset; performance unproven for SHACL).

**Default lean: (a) until PR-5 ships, (b) afterwards.** Decision blocks
on whether MFFD-domain shapes (NCR, AFP, welding) need cross-shape
SPARQL joins at write time — if yes, option (b) is forced; if no,
option (a) survives indefinitely.

[NEEDS-CLARIFICATION 2 — load-bearing for the PostGIS partial redesign]
**Spatial SRID.** Options:
- **(a) 4326 (WGS84 lat/lon).** Universal interchange; degrees are not
  metres so distance queries need `geography(POINT,4326)` or
  re-projection.
- **(b) 3857 (Web Mercator).** Tile-server-friendly; distortion at high
  latitudes; metres at equator only.
- **(c) 25832 (ETRS89 / UTM zone 32N).** Best for German aerospace data
  fixed to ZLP Augsburg; preserves distance in metres; loses validity
  outside zone 32N.

**Default lean: (c) for MFFD-bound data with a per-container `srid`
override column for cross-site/cross-zone data.** Decision blocks on
whether the spatial plugin's first customer is purely DE-zone-32N or
multi-region.

[NEEDS-CLARIFICATION 3]
**Garage bucket layout.** Today: single bucket (`shepard.files.s3.bucket`).
Options:
- **(a) Stay single-bucket with prefix isolation** (`payloads/`,
  `exports/`, `snapshots/`). Per AWS multi-tenant guidance [5],
  this is the right choice for ≤1 k tenants. Shepard's posture today.
- **(b) Bucket-per-purpose** (`shepard-payloads`, `shepard-exports`).
  Cleaner lifecycle rules; tiny operator overhead (3 buckets).
- **(c) Bucket-per-tenant.** Not applicable — Shepard is
  single-tenant per instance.

**Default lean: (a) today; revisit when per-prefix lifecycle rules
genuinely diverge.**

[NEEDS-CLARIFICATION 4]
**Drop the `:Timeseries.appId` Neo4j constraint, or unify it with
`timeseries.shepard_id`?** §7.2 outlines the choice. Decision blocks
on whether v2 endpoints exposing TS treat the Neo4j `:Timeseries`
node as primary (then keep appId) or the Postgres row as primary
(then drop appId and reuse `shepard_id` in the OGM).

---

## §10. Five-minute metrics — one per store

**Neo4j.** Index utilisation snapshot:
```cypher
SHOW INDEXES YIELD name, labelsOrTypes, properties, type, entityType, options
RETURN name, labelsOrTypes, properties, type, entityType, options.indexProvider;
```
Look for the `deleted` index on multiple labels (confirms §2.3 cost);
check if `n10s_unique_uri` is present (confirms n10s healthy); confirm
appId uniqueness coverage matches §2.2.

**Postgres (default).** Per-statement cost on the non-TS path:
```sql
SELECT relname, n_live_tup, n_dead_tup, last_autovacuum
FROM   pg_stat_user_tables
WHERE  schemaname='public'
ORDER  BY n_live_tup DESC;
```
Look for `permission_audit_log` growth rate and `importer_run` dead-tuple
ratio. Aim for `n_dead_tup / n_live_tup < 0.1` after vacuum.

**MongoDB.** Collection count growth (one-liner):
```js
db.runCommand({listCollections: 1, nameOnly: true}).cursor.firstBatch.length
```
Look for the count vs. the FileContainer count in Neo4j —
`MATCH (c:FileContainer) RETURN count(c)`. The two numbers should
match within a small delta (Mongo carries `fs.files` + `fs.chunks` +
the per-container collection).

**PostGIS.** SRID consistency:
```sql
SELECT DISTINCT ST_SRID(position) AS srid, count(*) AS rows
FROM   spatial_data_points GROUP BY 1;
```
Today (plugin off): one empty row. After the plugin ships: any srid
≠ chosen value is a real bug. Run after every spatial ingest.

**Garage.** Bucket usage today:
```bash
docker exec shepard-garage /garage bucket info <bucket-name>
docker exec shepard-garage /garage stats
```
Look for object count vs. `:ShepardFile` count where `providerId='s3'`
in Neo4j — the two should match exactly (no orphans, no missing
references).

---

## §11. Cross-reference

- TS sweep companion — `aidocs/agent-findings/timescaledb-schema-research.md`.
- SHACL substrate design — `aidocs/semantics/98-shapes-views-and-process-model.md`.
- SHACL changeover (non-TS) implementation log —
  `aidocs/agent-findings/shacl-changeover-non-ts.md`.
- File-storage SPI / FS1 series — `aidocs/platform/47-dev-experience-and-plugin-system.md`,
  ADR-0024 (Garage).
- Timeseries appId migration — `aidocs/platform/87-timeseries-appid-migration.md`.
- IMP1a / JobService kernel — `aidocs/platform/32-long-running-process-pattern.md`.

---

## Sources

1. AWS Database Blog — **Migrate from GridFS to Amazon S3 and Amazon DocumentDB.**
   https://aws.amazon.com/blogs/database/migrate-an-application-from-using-gridfs-to-using-amazon-s3-and-amazon-documentdb-with-mongodb-compatibility/
   The canonical playbook for the §4 GridFS → Garage migration:
   metadata + reference in the database, bytes in the object store.
2. **PostGIS Manual 3.5 — Database Management.**
   https://postgis.net/docs/manual-3.5/using_postgis_dbmanagement.html
   Source of the §5.2 (a) and (b) findings: typed geometry columns are
   the recommended default; `gist_geometry_ops_nd` is for nD only and
   2D queries should use the 2D operator class.
3. Neo4j Cypher Manual — **Search-performance indexes / managing indexes.**
   https://neo4j.com/docs/cypher-manual/current/indexes/search-performance-indexes/managing-indexes/
   Source for §2.3 — low-cardinality index pitfalls; planner cardinality
   estimation behaviour on Boolean indexes.
4. Neo4j Cypher Tuning — **Reducing cardinality.**
   https://neo4j.com/graphacademy/training-cqt-40/03-cqt-40-reducing-cardinality/
   Boolean/status flags are explicitly called out as low-cardinality and
   not worth indexing in most cases.
5. AWS APN Blog — **Partitioning and Isolating Multi-Tenant SaaS Data with
   Amazon S3.** https://aws.amazon.com/blogs/apn/partitioning-and-isolating-multi-tenant-saas-data-with-amazon-s3/
   Source for §6.4 and `[NEEDS-CLARIFICATION 3]`: bucket-per-tenant
   works for ≤100 tenants; prefix-based isolation scales past 1 k
   tenants. Shepard's single-tenant posture lands cleanly in
   "single bucket, prefix isolation".
6. Tiger Data (TimescaleDB) — **Boosting Postgres Performance With Prepared
   Statements and PgBouncer's Transaction Mode.**
   https://www.tigerdata.com/blog/boosting-postgres-performance-with-prepared-statements-and-pgbouncers-transaction-mode
   Confirms PgBouncer 1.21+ supports prepared statements in transaction
   pool mode; the `prepareThreshold=0` workaround is no longer needed.
7. Crunchy Data — **Prepared Statements in Transaction Mode for PgBouncer.**
   https://www.crunchydata.com/blog/prepared-statements-in-transaction-mode-for-pgbouncer
   Same as [6], independent operator viewpoint. Recommends pinning to
   a known PgBouncer tag.
8. Coscine — published architecture papers. The Coscine documentation
   page at https://docs.coscine.de/en/research-data-platform/concepts/architecture/
   returned 404 at fetch time; the architecture is documented in
   conference papers behind RWTH SSO so can't be verified inline. The
   Coscine pattern (metadata graph + S3 bytes) matches Shepard's, per
   memory `project_competitive_position.md`.
9. Welzmüller F. et al. (2024) — **Research Data Management for Space
   Missions** (DLR eLib 215120). Per memory: Postgres + Timescale + a
   graph-shape metadata layer; matches Shepard's architecture. The
   multi-substrate-split decisions in that paper map cleanly onto the
   §7 cross-cuts here. Not re-fetched (memory already carries the
   takeaway).
10. **MongoDB GridFS spec — bucket / collection naming.**
    https://www.mongodb.com/docs/manual/core/gridfs/ Confirms the
    default `fs.files` + `fs.chunks` shape, which is what `FileService`
    uses via `GridFSBuckets.create(mongoDatabase)`. The Shepard quirk
    in §4.1 is the per-container `MongoCollection<Document>` for
    bookkeeping documents *alongside* the standard GridFS bucket.

