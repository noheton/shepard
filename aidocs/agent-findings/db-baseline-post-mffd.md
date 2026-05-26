---
stage: deployed
last-stage-change: 2026-05-26
author: db-baseline-agent
task: DB-OPT1 (#230)
---

# DB Baseline: post-MFFD ingest (2026-05-26)

**Captured:** 2026-05-26  
**Purpose:** Reference snapshot for DB-OPT2..5 optimisation passes. All measurements
are against the live nuclide.systems dev box after MFFD-Dropbox ingest completed.

---

## Neo4j

**Total nodes:** 749,375  
**Total relationships:** 1,110,380  
**Disk (host volume):** 231 MB (113 MB databases/ subdirectory)  
**Online indexes:** 114

### Node counts by label

| Label | Count | Notes |
|---|---|---|
| Resource | 378,547 | n10s ontology nodes — the dominant label by far |
| Activity | 296,808 | PROV audit trail (265,833 CREATE / 18,056 UPDATE / 12,919 DELETE) |
| BasicReference | 22,250 | DataObject reference nodes |
| ShepardFile | 22,071 | File metadata (mirrors MongoDB fs.files) |
| DataObject | 17,152 | All collections combined |
| Permissions | 4,368 | ACL nodes |
| StructuredDataContainer | 4,207 | |
| PayloadVersion | 1,263 | |
| StructuredData | 1,237 | |
| Timeseries | 600 | TimescaleDB pointer nodes |
| SemanticAnnotation | 370 | |
| SnapshotEntry | 142 | |
| ApiKey | 96 | |
| Version | 81 | |
| __Neo4jMigration | 64 | Migration history |
| FileGroup | 37 | |
| Collection | 13 | |
| LabJournalEntry | 11 | |
| TimeseriesAnnotation | 9 | |
| TimeseriesContainer | 9 | |
| FileContainer | 8 | |
| User | 7 | |
| Snapshot | 5 | |
| Watch / SemanticRepository / CollectionWatcher | 3 each | |
| Singleton config nodes | 8 | SqlTimeseriesConfig, SemanticConfig, InstanceConfig, etc. |

### Relationship counts by type (top 20)

| Type | Count | Notes |
|---|---|---|
| TYPE | 203,217 | RDF/n10s semantic |
| SUBJECT | 170,026 | RDF/n10s semantic |
| PREDICATE | 170,026 | RDF/n10s semantic |
| OBJECT | 160,370 | RDF/n10s semantic |
| RELATED | 117,345 | n10s |
| created_by | 43,724 | Provenance |
| has_payload | 43,227 | Payload links |
| has_reference | 22,250 | DataObject → BasicReference |
| updated_by | 21,265 | Provenance |
| has_version | 17,444 | |
| has_dataobject | 17,127 | Collection → DataObject |
| BROADER | 17,014 | SKOS semantic |
| NARROWER | 17,012 | SKOS semantic |
| has_child | 16,961 | DataObject hierarchy |
| has_successor | 16,874 | DataObject lineage chain |
| ONPROPERTY | 7,446 | OWL semantic |
| ALLVALUESFROM | 7,227 | OWL semantic |
| FIRST / REST | 5,329 each | RDF list structure |
| USE / UF | 4,503 each | Thesaurus |
| owned_by | 4,368 | ACL |

### DataObjects by collection

| Collection | DataObjects |
|---|---|
| MFFD-Dropbox (019e4e56) | 8,531 |
| MFFD-Dropbox (019e55f3) | 8,514 |
| LUMEN synthetic | 17 |
| docs-backfill-plugin | 17 |
| AI Exchange | 14 |
| MFFD synthetic (seed) | 12 |
| Home energy (live) | 3 |
| TS-IDc demo (multiple) | 3 each |
| MFFD-Dropbox (019e525d) | 9 |
| MFFD-Dropbox (019e553b) | 7 |

Note: There are **four separate MFFD-Dropbox collections** (all named identically),
created at different times — the two largest holding ~8,500 DOs each are the primary
ingest targets. Combined MFFD-Dropbox DataObject count: **17,061**.

---

## PostgreSQL / TimescaleDB

**Disk (host volume):** 1.1 GB  
**TimescaleDB version:** 2.24.0-pg16

### Top tables by size (public schema, user tables only)

| Table | Size | Row count |
|---|---|---|
| permission_audit_log | 2,088 kB | 4,460 |
| timeseries | 512 kB | 871 |
| channel_metadata | 360 kB | 871 |
| importer_run | 48 kB | 0 |
| flyway_schema_history | 48 kB | — |
| timeseries_data_points | 32 kB (parent) | 132,667,051 (via chunks) |
| migration_progress | 24 kB | — |
| migration_tasks | 16 kB | — |

The `timeseries_data_points` parent table shows 32 kB because all data lives in
TimescaleDB chunks (see below).

### TimescaleDB hypertables

| Hypertable | Chunks | Compressed chunks | Total size |
|---|---|---|---|
| timeseries_data_points | 35 | 28 | 1,362 MB |

Compression policy is active: 28/35 chunks compressed (80%).
Largest individual chunks: 115–133 MB each.
Compression segmented by `timeseries_id`; ordered by time.

A continuous-aggregate materialized hypertable (`_materialized_hypertable_34`)
also exists, segmented by `timeseries_id` + `hour_bucket`.

### Timeseries data range

| Metric | Value |
|---|---|
| Earliest timestamp | 2023-01-02 13:29 UTC |
| Latest timestamp | 2026-05-26 04:28 UTC |
| Distinct timeseries_id values | 871 |
| Total rows | 132,667,051 |

### Top channel groups by row count (MFFD-dominated)

| Measurement | Device | Row count |
|---|---|---|
| mm | R20 | 33,539,293 |
| angle_degree | R20 | 17,162,006 |
| number | R20 | 14,545,855 |
| signal_binary | R20 | 14,382,663 |
| kelvin | R20 | 5,231,964 |
| angle_degree_per_s | R20 | 4,583,220 |
| mm_per_s | R20 | 3,993,155 |
| newton | MTLH | 3,572,811 |
| watt | MTLH | 3,568,139 |
| celsius | MTLH | 3,563,951 |

Device R20 (AFP robot) and MTLH (bridge-welding) together account for the overwhelming
majority of rows. This confirms the MFFD AFP + bridge-welding ingest dominates the TS store.

### Index hit rates

All sampled tables have hit rates ≥ 94.8%, ranging up to 99.2%. The lowest is
`bgw_job` at 94.8% — a TimescaleDB internal scheduler table, not a hot path.
User-facing tables (permission_audit_log: 96.1%; data chunks: 96–99%) indicate
the buffer cache is working well at current scale.

---

## MongoDB

**Disk (host volume):** 931 MB  
**Total documents:** 67,776  
**Total data size:** ~874 MB  
**Total storage size:** ~764 MB

### Key collections

| Collection | Documents | Data size | Storage size | Notes |
|---|---|---|---|---|
| fs.chunks | 22,711 | ~865 MB | ~760 MB | GridFS binary chunks — dominant |
| fs.files | 22,039 | ~2 MB | ~1 MB | GridFS file metadata |
| _shepard_files | 21,965 | ~4 MB | ~2 MB | Shepard file metadata index |
| StructuredDataContainer943e8388... | 864 | ~3 MB | ~606 KB | Largest SDC collection |
| StructuredDataContainer70037425... | 100 | ~51 KB | ~45 KB | |
| FileContainer7693e977... | 34 | ~6 KB | ~36 KB | |
| FileContainera33198d8... | 6 | ~1 KB | ~20 KB | |
| userAvatars | 2 | ~149 KB | ~332 KB | |
| _shepard_videos | 3 | <1 KB | ~36 KB | |
| Empty StructuredDataContainers | ~10 | 0 | 4 KB each | Many empty placeholder collections |

The MongoDB store is almost entirely occupied by GridFS (`fs.chunks` + `fs.files`).
The 22,711 GridFS chunks at ~865 MB represent the file payload store for
~22,039 uploaded files (avg chunk size ~38 KB). With 21,965 `_shepard_files`
entries, there is ~1:1 parity between GridFS metadata and Shepard file records.

---

## Garage S3

**Status:** Healthy, fully operational  
**Node count:** 1 (single-node, dc1, capacity 1,000 MB, zone dc1)  
**Data available:** 450.5 GB / 536.9 GB (83.9% free)

### Buckets

| Bucket alias | ID | Objects | Size |
|---|---|---|---|
| shepard-files | 61cc4c928f... | 24 | 13.6 MiB (14.2 MB) |

The Garage S3 backend holds only 24 objects (13.6 MiB). This is consistent with
files-s3 storage being opt-in via the `files-s3` compose profile — MongoDB GridFS
is the active file storage provider on this instance. Garage is deployed and healthy
but not receiving the bulk of file writes.

---

## MFFD-specific counts

| Metric | Value |
|---|---|
| MFFD-Dropbox DataObjects (4 collections) | 17,061 |
| MFFD-Dropbox BasicReferences | 17,775 (019e55f3 alone) + 499 (019e4e56) |
| TS channels (MFFD-dominated) | ~860 of 871 total |
| TS rows (MFFD AFP R20 + MTLH bridge) | ~93M of 132.7M total |
| Dominant TS measurement group | mm/R20: 33.5M rows |
| Total TS store size | 1,362 MB |
| File storage (MongoDB GridFS) | ~865 MB across 22,711 chunks |

---

## Observations

### 1. n10s ontology nodes dominate Neo4j (378K Resource nodes)
The largest label by node count is `:Resource` (378,547), which are neosemantics
(n10s) ontology graph nodes — not Shepard domain data. Together with ontology
relationship types (TYPE, SUBJECT, PREDICATE, OBJECT, RELATED, BROADER, NARROWER,
ONPROPERTY, ALLVALUESFROM = ~900K rels), semantic data accounts for roughly **50%
of all Neo4j storage**. This is expected given multiple ontology bundles are seeded
(PROV-O, Dublin Core, QUDT, CHAMEO, etc.), but it means any full-graph scan
(`MATCH (n)`) will be dominated by ontology nodes. Query patterns for DB-OPT2
must always filter on non-Resource labels.

### 2. Activity trail: 296K provenance records
The PROV capture filter has generated 296,808 `:Activity` nodes (265,833 CREATE,
18,056 UPDATE, 12,919 DELETE). This is the largest user-domain label. At current
scale it's manageable, but with 17K DataObjects × multiple operations each, the
trail grows proportionally. No index exists on `Activity.startedAtMillis` — a
time-range query over the audit trail would be a full scan.

### 3. Four duplicate MFFD-Dropbox collections
Four collections share the name "MFFD-Dropbox" with distinct `appId` values, created
at different timestamps. The two most recent (019e55f3, 019e553b) are small (7–9 DOs);
the two largest (019e4e56 at 8,531 DOs, 019e55f3 at 8,514 DOs) represent successive
ingest runs. This suggests the ingest was run multiple times, possibly after resets.
Before DB-OPT2 begins, the team should confirm which two collections are canonical
and whether the smaller two are stale/test artifacts that can be archived.

### 4. DataObject status field is almost entirely NULL
Of 17,152 DataObjects, 17,150 have `status = NULL` and only 2 have `status = 'DRAFT'`.
The status lifecycle is not being used in practice. This is relevant for any
quality-gate or NCR workflow (DB-OPT4 / manufacturing quality findings).

### 5. TimescaleDB compression is active and healthy
28/35 chunks are compressed (80%). Buffer hit rates across all data chunks are
≥ 96%, indicating the chunk-level cache is working. The continuous-aggregate
for hourly rollups (`_materialized_hypertable_34`) is present but its utilisation
should be profiled in DB-OPT3.

### 6. MongoDB GridFS holds ~865 MB of file data
GridFS is the active file backend (not Garage S3). 22,039 files are stored with
~38 KB average chunk size. The `_shepard_files` collection mirrors fs.files (21,965
entries vs 22,039 in GridFS) — a 74-document discrepancy worth investigating in
DB-OPT4 (potential orphan check).

### 7. Garage S3 is underutilised (13.6 MiB, 24 objects)
Garage is running and healthy but nearly empty — it holds only 24 objects. Either
the `files-s3` storage provider was not enabled during MFFD ingest, or only a small
subset of files was routed there. Any future MFFD ingest under the S3 provider
would need this confirmed. Garage has 450 GB available.

### 8. No PostGIS data present
The PostGIS container is not in the running stack (profile `spatial` not active).
No spatial data was captured in this baseline.

---

## Summary table

| Substrate | Disk | Key metric |
|---|---|---|
| Neo4j | 231 MB | 749K nodes / 1.1M rels; 114 indexes; 17,152 DataObjects |
| TimescaleDB | 1.1 GB | 132.7M rows; 35 chunks (28 compressed); 1,362 MB hypertable |
| MongoDB | 931 MB | 67K docs; 865 MB GridFS chunks; 22K files |
| Garage S3 | 14 MB | 24 objects in shepard-files bucket; 450 GB available |
| PostGIS | N/A | Not deployed (spatial profile off) |
