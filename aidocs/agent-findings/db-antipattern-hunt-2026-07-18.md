---
stage: fragment
last-stage-change: 2026-07-18
author: db-antipattern-hunter
task: DB-AP1
---

# DB Anti-Pattern Hunt — 2026-07-18

Substrate-direct, **read-only** audit of all six Shepard stores (Neo4j,
TimescaleDB/Postgres, MongoDB, Garage/S3, pgbouncer) for known-bad-practice
sites. Every query was `LIMIT`-bounded or count-store / catalog-only to avoid
contending with the **live MFFD tapelaying ingest** running against the backend
at audit time. No writes, no locks, no unbounded scans.

Point-in-time snapshot: numbers taken *during* an active ingest — where a count
is internally inconsistent (e.g. one user's Activity count slightly exceeds the
global Activity total) it is because rows are landing live. Both are labelled.

---

## Severity-ranked summary

| # | Severity | Store | Anti-pattern | Site | One-line fix |
|---|----------|-------|--------------|------|--------------|
| 1 | **CRITICAL** | Neo4j | Single-user **supernode** | `(:User{username:'7eead942…'})` ← **2,883,917** `WAS_ASSOCIATED_WITH` | Break density: bucket importer activities under intermediate `(:ActivityBatch)` nodes / a synthetic per-import agent; or suppress per-entity capture in bulk path |
| 2 | **MAJOR** | Neo4j | **Fan-in supernode** on channel node | one `(:Timeseries)` ← **8,262** `has_payload` (≈ all 8,263 `:TimeseriesReference`) | Traverse from the reference side only; never expand `has_payload` from the channel |
| 3 | **MAJOR** | Neo4j | **Activity volume + bare rows** | 2.88M `:Activity`; recent rows have `httpMethod=NULL` **and** `activityType=NULL` | Importer write-path emits one aggregate typed Activity per job, not one bare row per entity; verify no double-capture |
| 4 | **MAJOR** (trajectory) | MongoDB | **Collection explosion** | **3,104** `StructuredDataContainer<uuid>` collections, each 0–1 docs, only `_id_` index | Consolidate to a single `structured_data` collection keyed by `{containerAppId, oid}` |
| 5 | **MAJOR** | Neo4j | **`AnnotatableTimeseries` bridge-node** (CLAUDE-flagged EAV) | 198 `:AnnotatableTimeseries` shadowing 198 `:Timeseries` | Annotate the channel `appId` directly via `:SemanticAnnotation`; retire the bridge |
| 6 | MINOR→MAJOR | pgbouncer | Pool sizing vs heavy ingest + **zero observability** | `pool_mode=transaction`, `default_pool_size=30`, `max_client_conn=200`; no stats/admin user (`SHOW POOLS` → `not allowed`) | Add a `stats_users` entry; confirm `prepareThreshold=0`; size pool against 4-worker ingest |
| 7 | MINOR | TimescaleDB | FK on hypertable | `timeseries_data_points.timeseries_id → timeseries(id)`, replicated as **739 per-chunk FK constraints** | Per-insert parent read; parent is 136 rows & cached, so keep — but re-evaluate if ingest throughput bottlenecks |
| 8 | MINOR | Neo4j | Cross-substrate drift + missing constraint | 198 `:Timeseries` (Neo4j) vs 136 `channel_metadata` (PG); `:FileBundleReference.appId` has **no uniqueness constraint** | Reconcile channel counts; add `FileBundleReference_appId_unique` |
| 9 | MINOR | Neo4j / S3 | `:ShepardFile.storageBackend = NULL` | bytes split 436,324 Garage objects (189 GiB) + ~66k GridFS blobs, resolved only via global `activeStorage()` | Stamp per-file backend so a provider flip can't strand old-backend bytes |

**Verified clean (do not re-audit):** `appId` uniqueness constraints across ~100
labels; **no** `attributes||*` EAV bag on `:DataObject` (0 in a 200-node sample —
metadata correctly lives in 70,048 `:SemanticAnnotation` nodes); Timescale
compression (`compress_after` 7d) + `timeseries_hourly` continuous aggregate +
tuned chunk config; `chk_one_value_column` + `timeseries_value_type_check` CHECK
constraints; `importer_run` partial indexes; `permission_audit_log` carries a
UUID `app_id` + `entity_app_id`/`occurred_at` indexes; data-point indexes
`(timeseries_id, time DESC)`.

---

## What I found

**Connectivity:** all six stores reached substrate-direct. Neo4j 5.26 (3.60M
nodes), Postgres 16.11 + TimescaleDB (1 hypertable, 739 chunks), MongoDB 8.0.4
(~30 GB `database`), Garage v1.0.1 (`shepard-files` bucket, 436,324 objects /
189 GiB), pgbouncer (edoburu) transaction-mode.

**Neo4j label census** (count-store, O(1)): `Activity` **2,883,760** dominates,
then `ShepardFile` 500,046, `BasicEntity` 76,463, `SemanticAnnotation` 70,048,
`SingletonFileReference` 47,271, `DataObject` 13,610, `TimeseriesReference`
8,263. Relationship census: `WAS_ASSOCIATED_WITH` 2,873,427, `has_payload`
2,527,230, `GENERATED` 481,398.

**The two supernodes (findings 1 & 2).** Index-backed probe on
`Activity.agentUsername` returns **2,883,917** activities for a *single* service
user (`7eead942-63a5-4cfc-aea9-b1d0f0a291ea`) — i.e. essentially every Activity
in the graph associates to one `:User`. That node carries ~2.87M
`WAS_ASSOCIATED_WITH` relationships, **~28× past Neo4j's ~100k dense-node
threshold**, and it grows **~6,600/hour** during the live ingest. Separately, a
degree probe on `has_payload` shows a *single* `:Timeseries` channel node with
**8,262** incoming edges (≈ the full `:TimeseriesReference` population) — a
classic fan-in supernode where the MFFD ingest points thousands of references at
a handful of shared channel nodes.

**Cause of the Activity flood (corrects an earlier hypothesis).** A recent-window
probe (`startedAtMillis > now-1h`) returned 6,641 activities, **all with
`httpMethod=NULL` and `activityType=NULL`**. These are *not* read-captures (those
would carry `GET`) and *not* typed subtypes — they are bare rows minted by the
importer write-path, one per entity operation, each wired to the single service
user. So the fix is in the bulk/importer path and the model, **not** in flipping
`shepard.provenance.capture-reads`.

**MongoDB collection explosion (finding 4).** 3,123 collections total; **3,104**
are `StructuredDataContainer<uuid>` — one collection minted per container
(`StructuredDataService.createStructuredDataContainer()` builds the name as
`"StructuredDataContainer" + UUID.randomUUID()`), each holding 0–1 `StructuredData`
docs and only the default `_id_` index. Document lookups are by `_id` (indexed),
so this is **not** a missing-index problem — it is catalogue/WiredTiger metadata
overhead that scales 1:1 with container count. Also observed: `_shepard_files`
GridFS namespace (6,665 docs) and `fs.files`/`fs.chunks` (59,215 / 86,365) carry
only default indexes — fine, since the singleton-file path resolves by GridFS
`_id`/`oid`.

**Storage is S3, not GridFS.** `application.properties` ships
`shepard.storage.provider=gridfs`, but `docker-compose.override.yml` sets
`SHEPARD_STORAGE_PROVIDER=s3` → Garage bucket `shepard-files`. Garage holds
436,324 objects (189 GiB); GridFS retains ~66k legacy/video/avatar blobs. So the
500k `:ShepardFile` ÷ 66k GridFS "gap" is **explained** (bytes live in Garage),
**not** an orphan set. The real smell is that `:ShepardFile.storageBackend` is
`NULL` — resolution depends entirely on the global active adapter (finding 9).

**TimescaleDB is well-tuned.** Single hypertable `timeseries_data_points`,
compression on (`segmentby timeseries_id, orderby time`, `compress_after` 7d),
`timeseries_hourly` continuous aggregate with its own refresh + compression
policy, value-column CHECK constraints, and the 5-tuple correctly evicted from
core `timeseries` into a separate `channel_metadata` table
(`V1.14.0__drop_5tuple_from_core_timeseries.sql`) keyed by a composite unique on
the 6-tuple. The one smell is the hypertable→`timeseries` FK (finding 7).

**pgbouncer.** `pool_mode=transaction` (correct for Quarkus/Hibernate),
`default_pool_size=30`, `max_client_conn=200`, `scram-sha-256`. Two notes: (a)
no stats/admin user is configured, so `SHOW POOLS`/`SHOW CONFIG` return
`not allowed` — **zero pool observability** exactly when the "don't parallelize
heavy ingests → pool exhaustion → 504" failure mode (in project memory) is live;
(b) transaction pooling requires server-side prepared statements disabled
(`prepareThreshold=0`) — worth an explicit assertion in the JDBC URL audit.

## Opportunities

- **Break the Activity supernode structurally.** Model importer provenance as
  one typed `(:ImportActivity)` per job that `USED`/`GENERATED` the batch, rather
  than one bare `(:Activity)` per entity all hung off one `:User`. This collapses
  ~2.8M rels toward the number of *import jobs* and makes the provenance graph
  actually queryable (the FAIR-R1 promise).
- **Consolidate StructuredData into one collection.** A single `structured_data`
  collection with a compound index on `{containerAppId:1, _id:1}` removes the
  1:1 collection-per-container growth and keeps within Mongo's catalogue comfort
  zone as MFFD scales to 10k+ containers.
- **Add pool observability now.** A `stats_users` line is a one-liner that turns
  the 504 failure mode from "post-mortem" into "dashboard".

## Ideas

- A nightly **degree-watch** Cypher job that flags any node crossing (say) 50k
  relationships and posts to the notification transport — supernodes are caught
  at 50k, not at 2.8M.
- A **cross-substrate reconciliation** check (DB-AP2 territory): `:Timeseries`
  node count vs `channel_metadata` row count; `:ShepardFile.oid` set vs Garage
  object keys ∪ GridFS `_id` set. Both are cheap and both would have surfaced
  drift here (198 vs 136).

## Real-world impact

- The Neo4j supernode degrades **every** query the planner routes through that
  `:User` (permission expansions, provenance UI, "who did what") and every
  importer commit that has to append yet another relationship to a 2.8M-degree
  node — the write cost is itself part of why heavy ingests strain the system.
- The Mongo collection count is harmless *today* (3.1k ≪ 100k threshold) but is
  on a linear-with-containers trajectory; at MFFD's target 10k+ structured-data
  containers it enters the documented degradation band and slows mongod startup,
  backups, and `listCollections`.
- No pgbouncer stats user means the operator is flying blind into the exact
  pool-exhaustion 504 the project has already hit once.

## Gaps & blockers

- **`SHOW POOLS` inaccessible** — the audit could not read live `cl_waiting` /
  server-connection saturation because no stats user exists. This is both a
  blocker for *this* audit and a finding in its own right (finding 6).
- **DB-AP2 (cross-cutting) not attempted here** — the `:Timeseries` (198) vs
  `channel_metadata` (136) drift and the storageBackend/oid reconciliation are
  flagged but belong to the AP2 union pass.
- Devil's-advocate calibration is embedded per finding below; nothing here is a
  data-loss emergency — the CRITICAL is a *scaling/degradation* severity, not an
  outage.

## What surprised me

- **The 5-tuple debt is largely paid off.** I expected to find the 5-tuple still
  embedded in the core `timeseries` table; instead it has been evicted to
  `channel_metadata` and core `timeseries` is a clean `{id, container_id,
  value_type, shepard_id}` — the TS-ID migration actually shipped.
- **`storage.provider=gridfs` in properties is a decoy** — the live system runs
  S3/Garage via compose override. An auditor trusting `application.properties`
  would mis-locate 189 GiB of bytes.
- **The Activity supernode is one user, not a distribution.** I expected a skewed
  histogram; it is effectively a single node holding the entire provenance graph.

---

## Devil's-advocate notes (per finding)

1. **User supernode (CRITICAL):** *Is it premature?* No — it is already 28× past
   the documented threshold and growing live; this is the one finding that
   clears the "real problem now" bar. Counter-counter: if provenance is only ever
   queried by `targetAppId` (indexed) and never expanded *from* the user, the
   read pain is deferred — but writes still pay the density tax on every commit.
2. **Timeseries fan-in (MAJOR):** Mitigated in practice because callers traverse
   *into* the channel from a known reference, not *out* of it. Keep MAJOR because
   any "all references for this channel" query, or a GDS/community-detection run,
   walks the full 8,262-edge bundle.
3. **Bare Activity rows (MAJOR):** Arguably "capture succeeded, enrichment TODO."
   But CLAUDE's provenance rule demands the *richest available shape*; NULL type +
   NULL method fails it and makes the activity feed uninformative at 2.8M rows.
4. **Mongo collection explosion (MAJOR-trajectory, not CRITICAL):** Honestly
   under threshold today (3.1k ≪ 100k). Severity is about the growth law, not the
   current number, and it is an inherited-upstream pattern — worth a design note,
   not a hotfix.
5. **AnnotatableTimeseries bridge (MAJOR):** Explicitly named debt in CLAUDE.md;
   198 nodes is small, so the cost is *precedent* (siblings accreting) more than
   raw bloat. Fix is cheap; leaving it invites EAV sprawl.
6. **pgbouncer (MINOR→MAJOR):** `pool_size=30` may be plenty; transaction mode is
   correct. The MAJOR lean is the *observability* gap during a known failure mode,
   not a proven saturation.
7. **Hypertable FK (MINOR):** Parent is 136 cached rows — per-insert probe is
   effectively free. Only revisit if ingest profiling fingers FK checks. Textbook
   "premature to remove."
8. **Drift + missing FileBundleReference constraint (MINOR):** All 83 bundles
   *have* appIds; the missing constraint only bites if a future writer double-mints.
   Cheap additive fix (`CREATE CONSTRAINT … IF NOT EXISTS`).
9. **storageBackend NULL (MINOR):** Global resolver works fine until a provider
   flip; the FS1e sweep is the intended safety net, so this is belt-and-braces.

## Sources

- MongoDB — [FAQ: Storage / WiredTiger](https://www.mongodb.com/docs/manual/faq/storage/),
  [WiredTiger Storage Engine](https://www.mongodb.com/docs/manual/core/wiredtiger/):
  performance degrades as combined collections+indexes approach ~100,000;
  per-collection metadata memory + startup overhead.
- Neo4j — [Graph Modeling: All About Super Nodes](https://medium.com/neo4j/graph-modeling-all-about-super-nodes-d6ad7e11015b),
  [Super-Nodes and Indexed Relationships](https://opencredo.com/blogs/neo4j-super-nodes-and-indexed-relationships-part-i/):
  dense nodes (100k+ rels) degrade read + write; fix via relationship-type
  specificity, query direction, and intermediate nodes.
- TimescaleDB — [Tiger Data: 13 tips to improve insert performance](https://www.tigerdata.com/blog/13-tips-to-improve-postgresql-insert-performance),
  [FK handling PR #7134](https://github.com/timescale/timescaledb/pull/7134):
  FK constraints force a per-insert read of the referenced table; reconsider for
  append-only time-series.
- PostgreSQL — [Cybertec: Index your foreign key](https://www.cybertec-postgresql.com/en/index-your-foreign-key/),
  [Percona: Should I index FKs?](https://www.percona.com/blog/should-i-create-an-index-on-foreign-keys-in-postgresql/):
  the referencing side is not auto-indexed; index FKs used in joins/cascades, but
  don't blanket-index unused ones.
