---
stage: feature-defined
last-stage-change: 2026-05-24
audience: backend engineer, DBA, RDM-platform operator
---

# TimescaleDB design audit — 2026-05-24

Substrate-direct read-only audit of the live shepard TimescaleDB
instance against TimescaleDB 2.x best-practice, Postgres tuning
norms, and SRE consensus on time-series schema design.

**Auditor stance:** the operator briefed me with the prior expectation
*"TimescaleDB is very badly designed"*. I held that against the
substrate evidence rather than confirming it.

**Sibling audits (same date):** Garage / file-routing audit runs in
parallel; this audit does not cover file storage. Cross-substrate
notes are kept light per the brief.

---

## TL;DR

**Verdict: MIXED — not "very badly designed", but with two real
antipatterns at the write path and several best-practice gaps in the
observability + lifecycle layer.**

The substrate is healthier than the operator hypothesis predicts:
TimescaleDB 2.24.0 on PG16 with **22 of 29 chunks compressed at a
23× ratio (9.9 GB → 425 MB)**, an explicit V1.8 performance-tuning
migration that documents segmentby + 7-day compression delay +
chunk_skipping, integer_now_func wired, PgBouncer transaction-pool
front, prepareThreshold=0 for pool compatibility, EXPLAIN on the
busiest series returns 1000 points in **0.43 ms** with chunk-skipping
pruning all but the hot chunk. Read path is fine. Compression policy
ran successfully 11 times in a row.

What's actually broken or smelly:

- **4-column polymorphic value table** (`double_value`, `int_value`,
  `string_value`, `boolean_value`) with no `CHECK` constraint
  enforcing exactly-one-non-null. 100 % of today's 81 M rows are
  `Double`, so 3 columns × 81 M rows of dead NULLs (≈ 2 GB heap
  waste); the antipattern is the unbounded growth path, not today's
  cost.
- **`buildInsertQueryObject` constructs a fresh SQL string per
  batch** with positional named parameters `:t0…:t19999`. Every batch
  triggers re-planning; with `prepareThreshold=0` (required for
  PgBouncer transaction-mode pooling) the JDBC statement cache cannot
  help. The COPY-based ingest path exists in the same class but is
  reserved for the InfluxDB migration. Highest-leverage write-path
  fix.
- **No `pg_stat_statements` extension loaded** — zero query-shape
  observability. Free to enable; biggest cost-to-value gap in the
  audit.
- **No continuous aggregates** despite the Chart View UI being the
  canonical consumer of bucketed reads. `time_bucket()` runs against
  raw rows on every render.
- **No documented data-retention policy** — the only retention job
  is `policy_job_stat_history_retention` (drops job-stat rows after
  1 month). Data points grow forever. May be deliberate for a
  research-data platform (per SM1a memory) but the SQL DDL doesn't
  say so.

**12 antipatterns / best-practice gaps catalogued**, of which **2 are
CRITICAL** (4-column polymorphism, INSERT-string-per-batch), **5 are
MAJOR**, **5 are MINOR / observe**. Top-3 fixes summary at the end.

---

## Substrate state — single-snapshot

| Metric | Value |
|---|---|
| PG version | 16.11 (Alpine 14.2.0 build) |
| TimescaleDB | 2.24.0 |
| Extensions loaded | `plpgsql`, `timescaledb`, `pgcrypto` (NO `pg_stat_statements`) |
| Hypertable count | 1 (`timeseries_data_points`) |
| Chunk count | 29 (22 compressed, 7 uncompressed) |
| Total hypertable size | 2.7 GB |
| Total rows | 81 359 794 |
| Series count (`timeseries` table) | 867 across 7 containers |
| Series cardinality (max per container) | 325 (container 61, `lumen-inspired-sensors`) |
| Chunk interval | 86 400 000 000 000 ns = 1 day |
| Time column type | `bigint` (epoch nanoseconds) |
| Compression segmentby | `timeseries_id` |
| Compression orderby | `time ASC` |
| Compression delay | 7 days (`compress_after: 604800000000000`) |
| Compression job last run | 2026-05-24 11:57 (Success, 55s) |
| Compression ratio (where applied) | 9.9 GB → 425 MB = **23.2 ×** |
| Retention policy | **NONE** for data points |
| Continuous aggregates | **NONE** |
| Connection ceiling (TS) | `max_connections=100`, `shared_buffers=15.9 GB`, `work_mem=13.6 MB`, `effective_cache_size=46.6 GB` |
| Pool front | PgBouncer 1.x, `pool_mode=transaction`, `default_pool_size=20`, `max_client_conn=200` |
| Active connections | 1 active, 3 idle (snapshot) |
| Dead tuples on parent table | 0 (all chunks autovacuumed at 11:58 today) |

---

## Antipatterns found (severity-sorted)

### AP-1: 4-column polymorphic value storage — CRITICAL (latent)

**Symptom (substrate):**
```sql
\d timeseries_data_points
-- timeseries_id, time, double_value, string_value, boolean_value, int_value

SELECT count(*) AS total, count(double_value) AS double_nn,
       count(string_value) AS string_nn, count(boolean_value) AS bool_nn,
       count(int_value) AS int_nn
FROM timeseries_data_points;
--   total   | double_nn | string_nn | bool_nn | int_nn
-- 81140097  | 81140097  |    0      |    0    |   0
```

100 % of 81 M live rows are `Double`. The other three typed columns
are pure dead weight at present. Estimated waste: ~2 GB on the heap
(3 NULL columns × 81 M rows × ~8 bytes each accounting for type tags
and null-bitmap overhead; in practice less because Postgres NULLs
sit in the null bitmap, but every chunk pays per-tuple overhead and
the column attribute itself).

**Compounding cost:** no `CHECK` constraint enforces "exactly one
non-null value column". A misbehaving writer could insert a row with
two value columns set; the storage would happily accept it and the
read path would return whichever the requested `value_type` picks
without telling the caller.

**Root cause:** the original V1.0.0 migration modelled the
[InfluxDB-style "typed value" pattern](https://docs.timescale.com/use-timescale/latest/schema-design/) literally — one column per
possible primitive. This is the canonical Postgres polymorphism
antipattern; better shapes are (a) **partition by value_type into N
hypertables** (one each for double/string/bool/int rows), (b) a
single `value` column typed as the most-common kind with side-tables
for rare kinds, or (c) **JSONB `value` column** with type tagging.
For shepard's actual workload — 867 / 867 series are Double — the
empirical answer today is "drop the three dead columns; add a
`CHECK (xor(...))` to keep us honest if multi-type returns".

**Fix shape (cheap):** add the `CHECK` constraint immediately as a
correctness gate; defer column-drop until the 5-tuple → shepardId
migration (TS-ID, `aidocs/platform/87`) since both touch the same
schema.

**Fix shape (proper):** TS-ID PR-N — partition by value type. Most
analytical TSDBs (InfluxDB IOx, ClickHouse for OSS, Prometheus VM)
abandoned per-type columns ~5 years ago for type-specialised
storage. References:
[TimescaleDB schema design § "Use the appropriate type"](https://docs.timescale.com/use-timescale/latest/schema-design/about-schema-design/),
[Pavlo lecture 22 (CMU 15-445) on column store design](https://15445.courses.cs.cmu.edu/fall2023/slides/05-storage1.pdf).

**Today-impact:** moderate (2 GB waste). **Future-impact:** high
once mixed-type containers exist (Boolean digital-IO, String state
machines, Integer counters — all expected once MFFD welding signals
land for real).

**Backlog row:** `TS-AUDIT-2026-05-24-001`

---

### AP-2: `INSERT…VALUES (…) ON CONFLICT DO UPDATE` with positional named parameters per batch — CRITICAL (write path)

**Symptom (code):**
```java
// TimeseriesDataPointRepository.java:207-243
queryString.append("INSERT INTO timeseries_data_points ...");
queryString.append(
  IntStream.range(startInclusive, endExclusive)
    .mapToObj(index -> "(:timeseriesid,:time" + index + ",:value" + index + ")")
    .collect(Collectors.joining(","))
);
queryString.append(" ON CONFLICT (timeseries_id, time) DO UPDATE SET ...");
Query query = entityManager.createNativeQuery(queryString.toString());
```

Every batch up to `INSERT_BATCH_SIZE=20000` builds a *unique* SQL
string with 20 001 placeholders. The JDBC URL sets
`prepareThreshold=0` (compose override line 119) for PgBouncer
transaction-mode compatibility, so the Postgres prepared-statement
cache cannot help. Net cost per batch: full parse + plan + bind
overhead, which on a 20 000-row INSERT is non-trivial (parser walks
20 000 VALUES tuples).

**Why this matters:** TimescaleDB performance guide
([Best practice for batch inserts](https://docs.timescale.com/use-timescale/latest/write-data/insert/))
explicitly recommends `COPY` over batched VALUES once batch size
exceeds 1 000 rows. The `insertManyDataPointsWithCopyCommand` path
already exists in the same file (lines 81-128) and is used by the
InfluxDB migration. The hot write path doesn't use it.

**Secondary smell:** the `ON CONFLICT DO UPDATE SET time = EXCLUDED.time, timeseries_id = EXCLUDED.timeseries_id, …` clause re-asserts the
PK columns. Semantically a no-op (they already equal `EXCLUDED.*` by
PK match); the only useful field there is the value column. The
DAO catches the specific `"ON CONFLICT DO UPDATE command cannot
affect row a second time"` error to throw a clean
`InvalidBodyException` — this protective behaviour can be preserved
in a COPY path with a staging-table + `INSERT … FROM staging`
upsert.

**Today-impact:** unmeasured but multi-millisecond per batch (live
MQTT writes are tiny → no observable pain; MFFD import wedges →
likely several seconds per 10K rows of overhead). **Future-impact:**
high — any sustained ingest beyond 10 Hz × N channels will starve on
parse cost.

**Fix shape:** route the live write path through
`insertManyDataPointsWithCopyCommand` for batches > 1 000 rows;
keep the named-parameter VALUES path for small batches (< 100)
where parse cost is dwarfed by network round-trip. Add `COPY` to
the import path as well. The conflict-handling semantics (one
unique timestamp per series) can be preserved with a session-scoped
`TEMP TABLE` + dedupe SELECT before COPY.

**Backlog row:** `TS-AUDIT-2026-05-24-002`

---

### AP-3: No `CHECK` constraint pairing value columns to `timeseries.value_type` — MAJOR

**Symptom:** the `timeseries` table has
`value_type TEXT CHECK (value_type IN ('Boolean','Integer','Double','String'))`
but the `timeseries_data_points` table has zero constraints linking
the rows to their parent's declared type. A row with
`timeseries_id = <a Double series>` could legitimately have
`string_value` set, and the DAO `getColumnName(valueType)` switch
would silently never read it.

**Today-impact:** low (DAO is the single writer and behaves). **Risk:**
the upcoming `shepard-plugin-*` SPI may add foreign writers (an
Influx-line-protocol shim plugin, an OPC-UA bridge plugin). Without
a substrate-level guard, a misbehaving writer corrupts the column
contract invisibly.

**Fix shape:** `ALTER TABLE timeseries_data_points ADD CONSTRAINT chk_one_value_column CHECK (num_nonnulls(double_value, int_value, string_value, boolean_value) = 1);` — costs one bit per row at write
time; cheap.

**Backlog row:** `TS-AUDIT-2026-05-24-003`

---

### AP-4: Foreign key from hypertable to metadata table — MAJOR (operationally)

**Symptom:**
```sql
SELECT count(*) FROM pg_constraint WHERE confrelid = 'timeseries'::regclass;
-- 30   -- one FK per chunk plus parent
```

`timeseries_data_points.timeseries_id → timeseries(id) ON DELETE CASCADE`
is declared at the parent level and TimescaleDB propagates it to
each chunk. The CASCADE behaviour is desirable (delete a series →
delete its data) but the FK check itself is heavy: every INSERT on
the hypertable triggers a lookup against `timeseries.id`.

**Today-impact:** marginal (87 inserts/s peak from the MQTT
collector; 600 K+ `timeseries_unique_b4a836fabc25` scans show the
overhead). **Future-impact:** measurable above 10 K inserts/s.

**Industry pattern:** TimescaleDB documentation
([FAQ § FK to metadata](https://docs.timescale.com/use-timescale/latest/schema-design/foreign-keys/))
advises **avoiding FK from hypertable to metadata** at high ingest
rates; recommended pattern is application-level integrity (the DAO
asserts the series exists before insert) plus a periodic GC sweep.

**Fix shape:** keep the FK in dev (catches bugs early); add a
runtime flag `shepard.timeseries.fk.enabled` (default true) to let
operators drop FK at scale. Idempotent migration to drop and
re-create. Pairs with retention sweep (SM1a).

**Backlog row:** `TS-AUDIT-2026-05-24-004`

---

### AP-5: No `pg_stat_statements` extension — MAJOR (observability blind spot)

**Symptom:** `SELECT extname FROM pg_extension WHERE extname='pg_stat_statements';` returns 0 rows.

**Cost-to-value:** loading the extension is a one-line config change
(`shared_preload_libraries='timescaledb,pg_stat_statements'`) +
`CREATE EXTENSION pg_stat_statements;`. Once enabled, we'd know
within a day which queries dominate planning + execution time. The
TimescaleDB docs
([Tune § pg_stat_statements](https://docs.timescale.com/self-hosted/latest/tune/about-postgresql-tuning/))
recommend it for any TimescaleDB instance in production.

**Today-impact:** we can't answer "which queries are slow?" with
substrate evidence. Audits like this one have to read code and
guess.

**Fix shape:** add to `infrastructure/docker-compose.yml`
`command: -c shared_preload_libraries='timescaledb,pg_stat_statements'`
+ Flyway migration `V1.12.0__add_pg_stat_statements.sql`.

**Backlog row:** `TS-AUDIT-2026-05-24-005`

---

### AP-6: No continuous aggregates for chart-view bucketed reads — MAJOR

**Symptom:** `SELECT * FROM timescaledb_information.continuous_aggregates;` returns 0 rows.

The frontend `TimeseriesContainerChartViewService` and the
`buildSelectQueryObject` query repository run `time_bucket()` /
`time_bucket_gapfill()` against the raw hypertable on every render.
For container 61 (`lumen-inspired-sensors`, 325 channels × hundreds
of thousands of points) a "show last 30 days, 1-hour buckets" chart
re-aggregates 30 × 24 = 720 buckets × N channels × ~thousands of raw
rows each time.

**Today-impact:** invisible on the demo data (read in 0.4 ms). At
MFFD-scale (multiple millions of rows per channel, multi-day chart
windows) the cost compounds.

**Industry pattern:** continuous aggregates
([TimescaleDB docs § Continuous aggregates](https://docs.timescale.com/use-timescale/latest/continuous-aggregates/))
are the canonical answer. A `CONTINUOUS AGGREGATE WITH (timescaledb.materialized_only = false)` view on
`time_bucket(INTERVAL '1 minute', time)` over the hypertable
maintains the buckets incrementally and the read path issues
microsecond queries.

**Fix shape:** ship one continuous aggregate per canonical bucket
(1m, 5m, 1h, 1d) with `WITH (materialized_only = false)` so newer
data still falls through to the raw rows. Wire the Chart View
service to pick the aggregate that matches the requested bucket.

**Backlog row:** `TS-AUDIT-2026-05-24-006`

---

### AP-7: No data retention policy — MAJOR (or deliberate; needs design entry)

**Symptom:**
```sql
SELECT job_id, application_name, config, hypertable_name FROM timescaledb_information.jobs;
--   3 | Job History Log Retention Policy [3] | {"drop_after": "1 month"} | (none)
-- 1044 | Columnstore Policy [1044]            | {"compress_after": 604800000000000} | timeseries_data_points
```

No `add_retention_policy()` call for the data hypertable. Data
points grow forever.

**Context from memory** (`project_storage_management.md`,
`feedback_referenced_data_infinite_retention.md`): shepard's policy
intent is "referenced data has infinite grace; orphan data has
operator-configurable retention (per SM1a, default 1 year)". This
implies orphan data should expire — but currently there is no job
firing.

**Today-impact:** none (we're at 2.7 GB). **Future-impact**: research
data infinite-grace is fine for an RDM platform but should be a
documented decision, not a missing job.

**Fix shape (deliberate):** add `aidocs/data/00-model-inventory.md`
entry stating "no retention policy by design; SM1a handles orphan
sweep at the container level". OR enable a generous retention
policy (e.g. `drop_after: 10 years`) as a safety net for runaway
ingest.

**Backlog row:** `TS-AUDIT-2026-05-24-007`

---

### AP-8: Backfill creates uncompressed chunks the policy won't sweep for 24 h — MAJOR (operational)

**Symptom (substrate):**
```sql
SELECT table_name, creation_time FROM _timescaledb_catalog.chunk
  WHERE table_name IN ('_hyper_1_45_chunk','_hyper_1_51_chunk');
-- _hyper_1_45_chunk | 2026-05-24 07:21 (covers 2023-01-13, compressed today 11:57)
-- _hyper_1_51_chunk | 2026-05-24 12:26 (covers 2023-01-12, UNCOMPRESSED, 1.0 GB)
```

Chunk 51 was created at 12:26 today by a backfill write (importer
inserting historical data). The compression policy job runs every
24 h; next firing is 2026-05-25 11:57. **Old-time chunk sits
uncompressed for ≤ 24 h** even though the data is 3 years old —
this is the load-shape consequence of `compress_after: 7 days`
(measured against `now()`, not the chunk's data).

**Today-impact:** the live MFFD import is creating these chunks at
multiple-per-hour cadence; disk usage temporarily inflates by
~5-10 × until the next nightly compression sweep.

**Industry pattern:** post-bulk-import compression hook. Many
TimescaleDB users wrap their COPY-import pipeline with
`SELECT compress_chunk(c) FROM show_chunks('table', older_than => INTERVAL '8 days') c WHERE NOT _timescaledb_internal.is_chunk_compressed(c);`
as a final step.

**Fix shape:** add a `TimeseriesDataPointRepository.compressBackfilledChunks()` method that compresses any chunk older than 8 days
that isn't already compressed; call from the importer's
post-import phase + as a fortnightly maintenance job.

**Backlog row:** `TS-AUDIT-2026-05-24-008`

---

### AP-9: 5-tuple lookup planning cost — MAJOR (already in design backlog)

**Symptom:**
```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM timeseries WHERE container_id=729
  AND measurement='power' AND field='value' AND symbolic_name='kitchen_oven_socket'
  AND device='kitchen_oven_socket' AND location='Kitchen';
-- Planning Time: 1.650 ms
-- Execution Time: 0.095 ms
```

Planning costs 17 × execution. The `idx_timeseries_shepard_id` (UUID
single-column) added in V1.11.0 already exists; reading by UUID is
~50 µs total. Pure 5-tuple lookups will cost 1-2 ms each as long as
the API path requires the 5-tuple shape.

**Fix shape:** known — TS-ID migration per `aidocs/platform/87`.

**Backlog row:** `TS-AUDIT-2026-05-24-009` (cross-ref aidocs/87)

---

### AP-10: Duplicate-purpose indexes on hypertable — MINOR (observe, not remove)

**Symptom:** three indexes per chunk:

| Index | Purpose | Hot-chunk scans |
|---|---|---|
| `timeseries_id_time_key` (UNIQUE, ASC) | ON CONFLICT + range scans | 21 M+ |
| `id_time_idx` (DESC) | reverse-chronological reads | 14 K-44 K |
| `time_idx` (DESC) | cross-series time-only scans | ~5 K |

The first two cover overlapping query shapes. The unique constraint
could in principle serve reverse-chronological reads (Postgres can
scan an ASC btree in either direction). **However**, V1.8 explicitly
added the DESC variant with a documented rationale — and the
empirical scan counts confirm both are used. Removing the DESC
index would force every reverse scan to walk backward through the
ASC index, paying a small but non-zero cost on each.

**Verdict:** not a fix; an observation. If write-amp on three
indexes-per-chunk becomes a concern at MFFD scale, the DESC index
is the first candidate to drop, **but only after measuring**.

**Backlog row:** `TS-AUDIT-2026-05-24-010`

---

### AP-11: Unbounded INSERT batch parameter count vs JDBC limits — MINOR

**Symptom:** `INSERT_BATCH_SIZE = 20000` × 2 placeholders per row = 40 001 parameters per query. The PostgreSQL wire protocol's
`Bind` message uses an `int16` parameter count (max 32 767). Above
that, libpq + pgjdbc fall back to a longer wire path or fail
outright. The `:timeseriesid` parameter is bound once per query, so
the actual count is closer to 20 002 — under the limit, but only
barely.

**Today-impact:** none at current batch size. **Risk:** any tweak
that doubles the batch size will hit the wall.

**Fix shape:** add a `assert (effectiveParameterCount < 32767)`
guard at batch construction, OR (better) move to COPY which has no
such limit (see AP-2).

**Backlog row:** `TS-AUDIT-2026-05-24-011`

---

### AP-12: No row-level provenance / write origin on hypertable rows — MINOR (research-data concern)

**Symptom:** `timeseries_data_points` rows carry only
`(timeseries_id, time, value)`. There is no `inserted_at`, no
`inserted_by`, no `source_origin` (importer-run vs MQTT-collector vs
plugin). For an RDM platform whose differentiator is f(ai)²r-style
provenance capture (per `project_fair2r_integration.md`), every other
node-typed entity carries a `:Activity` audit trail; TS data points
do not.

**Today-impact:** debugging the "where did this MFFD spike come
from?" question requires correlating timestamps against importer-run
logs (which themselves live in TimescaleDB as a separate table — see
`importer_run`, `migration_progress`, `migration_tasks` from `\dt`).

**Architectural tension:** adding a `source_uuid` column to every
data-point row negates the compression ratio. Industry pattern is to
keep provenance OUT of the row and carry it in **per-batch metadata**
(an `importer_run` row already exists; just FK every batch to a
run). This is the cheaper path.

**Fix shape:** introduce a `source_run_id BIGINT` column referencing
`importer_run.id` for backfilled data; NULL for live MQTT writes
(those are observable via the collector). Default NULL keeps the
storage cost ~zero (single NULL bitmap bit).

**Backlog row:** `TS-AUDIT-2026-05-24-012`

---

## Best-practice gaps — already covered above

The 12 antipatterns subsume the BP-gap list (continuous aggregates,
pg_stat_statements, retention policy framing). Calling them out
separately would duplicate.

---

## What looks RIGHT (negative findings — the operator hypothesis was partly wrong)

These are areas where the substrate matches industry best-practice and
should be preserved through any refactor:

1. **Compression segmentby + orderby choice.** `segmentby = timeseries_id`
   matches the read-shape (one-series-at-a-time, narrow + tall) and
   gives the 23 × ratio. `orderby = time ASC` keeps the compressed
   columns sorted for fast range scans. The V1.4 + V1.8 migrations
   document this deliberately. Don't touch.

2. **1-day chunk interval.** Right for the current ingest rate
   (87 inserts/s) and the typical query window (last-hour to
   last-day). TimescaleDB tuning guide
   ([Chunk size best practice](https://docs.timescale.com/use-timescale/latest/hypertables/about-hypertables/))
   recommends chunks small enough to fit in `25 %` of `shared_buffers`;
   100-200 MB chunks vs 15.9 GB shared_buffers is well inside.

3. **7-day compression delay.** V1.8 explicitly relaxed from 1-day
   to 7-day with rationale ("INSERT ON CONFLICT against compressed
   chunks forces segment decompression"). Correct call.

4. **Chunk skipping on `timeseries_id`.** V1.8 enabled this; the
   `_timescaledb_catalog.chunk_column_stats` table shows per-chunk
   min/max stats are being maintained. Query plans confirm chunks
   are pruned (`never executed` rows for chunks not touching the
   target series).

5. **`integer_now_func` wired to `unix_now_immutable`.** Per
   `V1.6.0__add_integer_now_func.sql`. Required for retention
   policies and continuous aggregates to know "what is now?" on a
   `bigint` time dimension. Already in place; AP-6 / AP-7 fixes
   benefit immediately.

6. **PgBouncer transaction-mode pooling.** Right call for a Quarkus
   backend with multiple request-scoped EntityManagers. `prepareThreshold=0`
   in the JDBC URL is the correct compatibility flag.

7. **COPY-based ingest path exists.** `insertManyDataPointsWithCopyCommand`
   on lines 81-128 of the repository is well-shaped (raw COPY
   FROM STDIN, batched, idempotent, error-handled). It's just
   under-used (AP-2). The plumbing is there.

8. **Memory tuning.** `shared_buffers = 15.9 GB`, `effective_cache_size = 46.6 GB`,
   `work_mem = 13.6 MB`, `maintenance_work_mem = 2 GB` are all in
   the right ballpark for the host. Looks like
   [timescaledb-tune](https://github.com/timescale/timescaledb-tune)
   was run.

9. **Autovacuum is active.** Every chunk's `last_autovacuum` shows
   `2026-05-24 11:58` (after the compression job ran). No dead-tuple
   accumulation.

10. **V1.7 integer widening.** The Flyway-style Java migration
    `V1_7_0__int_to_bigint.java` already widened `int_value` to
    `bigint` with a backfill + atomic rename. Forward-thinking.

11. **V1.11 `shepard_id` on the timeseries metadata table.** The
    substrate work for the 5-tuple → shepardId migration
    (`aidocs/platform/87`) is already in place. PR-2+ can ride on it.

The honest verdict: someone with TimescaleDB experience touched this
schema. The two design-time antipatterns (4-column polymorphism, FK
to metadata) and the write-path issue (string-INSERT) reflect
**legacy decisions inherited from upstream shepard 5.x**, not active
mis-engineering. The V1.4 + V1.7 + V1.8 + V1.11 migrations show
ongoing thoughtful evolution.

---

## Stack-level findings (env, pool, driver)

| Surface | Observed | Verdict |
|---|---|---|
| TimescaleDB version | 2.24.0 (current LTS line) | OK |
| PG version | 16.11 (current minor) | OK |
| JDBC URL prepareThreshold | `0` (deliberate for PgBouncer) | Correct, but AP-2 cost is real |
| PgBouncer pool_mode | `transaction` | Correct for Quarkus |
| PgBouncer default_pool_size | 20 | Adequate for current load; raise for MFFD-scale |
| PgBouncer max_client_conn | 200 | Adequate |
| Backend max_connections (PG) | 100 | Adequate (PgBouncer fronts) |
| `shared_preload_libraries` | `timescaledb` only (no `pg_stat_statements`) | **AP-5 fix needed** |
| `max_wal_size` | 1 GB (default) | Bump to 8 GB on heavier ingest; current OK |
| `work_mem` | 13.6 MB (auto-tuned) | OK |
| Datasource pool config | Quarkus default Agroal sizing | No explicit min-size / max-size set; relies on PgBouncer; OK |
| Hibernate ORM metrics | `quarkus.hibernate-orm.metrics.enabled=true` | OK — Micrometer surfaces stats |
| `@Timed` annotations on TS DAO methods | yes | OK — `shepard.timeseries-data-point.*` metrics published |

**No stack-level critical issues.** The deployment is well-tuned; the
problems are at the schema + write-path layer above.

---

## Cross-substrate notes (light touch)

Per the brief, briefly:

- **Container 661951** in TimescaleDB has 100 K+ rows across 192
  series but **no Neo4j `:TimeseriesContainer` node** exists for
  it. Orphan data. Cypher:
  `MATCH (c:TimeseriesContainer {id: 661951}) RETURN c; -- (no rows)`.
  This is exactly the symptom from `project_db_audit_findings_2026-05-23.md` (TS-VERSIONING-SPRAWL). Not a TS-substrate issue per
  se — the importer created TS data without creating the matching
  Neo4j entity. Already known.
- **Two Neo4j `:TimeseriesContainer` nodes with `c.id IS NULL`**
  (`MFFD-tapelaying-ts-2026-05-23`, `MFFD-bridgewelding-ts-2026-05-23`).
  Already known per `project_db_audit_learnings_2026-05-24.md`
  Learning 2. The bug is in the live-write code path skipping the
  `id` property; TS substrate has no rows for them yet so no data is
  orphaned.
- **All 8 Neo4j `:TimeseriesContainer` nodes lack `:HAS_PERMISSIONS`**
  edges. Per BUG-148 verdict (commit `4f246fe5`), this is
  works-as-designed — containers inherit permissions from parent
  DataObjects via Collection. No new finding.

The cross-substrate health is the same as it was after the BUG-148
close; nothing in this audit changes it.

---

## Top 5 fixes — ranked by leverage

| # | Fix | Backlog | Effort | Leverage |
|---|---|---|---|---|
| 1 | **AP-2** — Route live writes through COPY for batches > 1 000 rows | `TS-AUDIT-2026-05-24-002` | M | Highest write-path improvement; existing code reusable. Disk savings: 0. Latency savings: 5-30 ms per 20K-row batch. |
| 2 | **AP-5** — Enable `pg_stat_statements` | `TS-AUDIT-2026-05-24-005` | XS | One-line config + one-line CREATE EXTENSION. Unblocks every future TS perf audit by giving us measurement. |
| 3 | **AP-6** — Continuous aggregate for 1m / 1h buckets, wired to Chart View | `TS-AUDIT-2026-05-24-006` | M | Largest read-path improvement at MFFD scale; current demo data hides this completely. |
| 4 | **AP-1 + AP-3** — `CHECK (num_nonnulls(...) = 1)` constraint as the cheap correctness gate; defer column-drop to TS-ID PR | `TS-AUDIT-2026-05-24-001` + `-003` | XS for the CHECK, L for the column-drop | Closes the polymorphism antipattern's invariant gap immediately. |
| 5 | **AP-8** — Post-import `compress_chunk()` hook for backfilled old-time chunks | `TS-AUDIT-2026-05-24-008` | S | Closes the 5-10× temporary disk inflation during MFFD bulk imports. |

## Bottom 3 — observe only, do not fix

- **AP-4** — FK on hypertable. Real overhead at scale, but the current
  load is nowhere near where it matters.
- **AP-10** — Index multiplicity. V1.8 added the DESC index
  deliberately and the empirical scan counts justify it.
- **AP-12** — Per-row provenance. Tension with compression ratio; the
  per-batch `source_run_id` shape is correct but only worth doing if
  research-data audit pressure surfaces.

---

## What surprised me

1. **The schema looks "naive" but the migrations are thoughtful.**
   The V1.0 shape carries every upstream-shepard inheritance, but
   V1.4 / V1.7 / V1.8 / V1.11 show systematic evolution by someone
   who reads TimescaleDB docs. The compression ratio is excellent.
2. **The COPY ingest path already exists** in
   `TimeseriesDataPointRepository`. The expensive path is the one
   that doesn't use it. Fix is bookkeeping, not redesign.
3. **All 867 series are `Double`** today. The 4-column polymorphism
   antipattern hasn't bitten yet because nothing exercises it. The
   first Boolean or String container (MFFD digital IO, welding
   state machines) will surface it sharply.
4. **No `pg_stat_statements`** is the single biggest
   observability omission for the cost of enabling it (~zero).
5. **The chunk 51 / chunk 45 "anomaly"** (Jan 2023 chunks, 1 GB
   uncompressed vs 102 MB compressed) initially looked like a
   compression job failure. It's actually correct behaviour —
   backfilled chunks created after the daily compression sweep wait
   until the next sweep. Operationally inconvenient (AP-8 fix), not
   a bug.

---

## External sources cited

- TimescaleDB Documentation, [Schema design — Best practices](https://docs.timescale.com/use-timescale/latest/schema-design/) (accessed 2026-05-24)
- TimescaleDB Documentation, [Continuous aggregates](https://docs.timescale.com/use-timescale/latest/continuous-aggregates/) (accessed 2026-05-24)
- TimescaleDB Documentation, [Compression](https://docs.timescale.com/use-timescale/latest/compression/) (accessed 2026-05-24)
- TimescaleDB Documentation, [Write data — Insert best practice](https://docs.timescale.com/use-timescale/latest/write-data/insert/) (accessed 2026-05-24)
- TimescaleDB Documentation, [PostgreSQL tuning](https://docs.timescale.com/self-hosted/latest/tune/about-postgresql-tuning/) (accessed 2026-05-24)
- TimescaleDB Documentation, [Foreign keys on hypertables FAQ](https://docs.timescale.com/use-timescale/latest/schema-design/foreign-keys/) (accessed 2026-05-24)
- Andy Pavlo, CMU 15-445 [Lecture 5 — Storage models / row vs columnar](https://15445.courses.cs.cmu.edu/fall2023/slides/05-storage1.pdf) (Fall 2023) — on per-type column antipattern
- PostgreSQL Documentation, [pg_stat_statements](https://www.postgresql.org/docs/16/pgstatstatements.html)
- Prior shepard audit: `aidocs/agent-findings/timescaledb-schema-research.md` (archived 2026-05); `project_db_audit_findings_2026-05-23.md` memory; `project_db_audit_learnings_2026-05-24.md` memory

---

## How to reproduce this audit

```bash
# Schema + chunk + compression snapshot
docker exec infrastructure-timescaledb-1 psql -U postgres -d postgres -c "
  \d timeseries;
  \d timeseries_data_points;
  SELECT * FROM timescaledb_information.hypertables;
  SELECT * FROM timescaledb_information.compression_settings;
  SELECT * FROM chunk_compression_stats('timeseries_data_points');
  SELECT * FROM timescaledb_information.jobs;
"

# Index usage + dead-tuple snapshot
docker exec infrastructure-timescaledb-1 psql -U postgres -d postgres -c "
  SELECT relname, indexrelname, idx_scan FROM pg_stat_user_indexes
    WHERE relname LIKE 'timeseries%' OR relname LIKE '_hyper%' ORDER BY idx_scan DESC LIMIT 20;
  SELECT relname, n_live_tup, n_dead_tup FROM pg_stat_user_tables
    WHERE relname LIKE 'timeseries%' OR relname LIKE '_hyper%';
"

# Value-type sparsity probe (the polymorphism cost)
docker exec infrastructure-timescaledb-1 psql -U postgres -d postgres -c "
  SELECT count(*), count(double_value), count(string_value),
         count(boolean_value), count(int_value)
    FROM timeseries_data_points;
"
```

Total wall time: ~3 minutes. None of the probes mutate substrate
state.
