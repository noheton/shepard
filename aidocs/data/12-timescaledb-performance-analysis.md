---
stage: fragment
last-stage-change: 2026-05-23
---

# TimescaleDB Timeseries Integration — Performance Analysis & Mitigations

**Scope.** End-to-end review of the timeseries write and read paths against
TimescaleDB 2.24 / PostgreSQL 16, focusing on why ingestion and queries feel
sluggish compared to the previous InfluxDB deployment.

**Status legend.**
- ✅ FIXED — already merged in `V1.8.0__optimize_timeseries_performance.sql`
- 🟥 HIGH — significant impact, recommend addressing in next sprint
- 🟧 MED — measurable impact, schedule when possible
- 🟨 LOW — opportunistic improvement
- 🟪 ARCH — architectural change, plan as a project

---

## 1. Executive summary

The ingest and query paths are slow primarily because of **three independent
amplifiers**: an aggressive compression policy that decompresses chunks on
nearly every write, a missing per-series index on uncompressed chunks, and an
insert path that pays per-row JDBC parameter overhead. Each of these by itself
is a 2–10× slowdown vs. a tuned setup; together they compound. The InfluxDB
deployment never paid any of these costs because (a) it does not use SQL
parameter binding, (b) it has its own segmented index by tag set, and (c) it
does not modify already-compressed blocks on upsert.

The schema-level fixes are already merged in branch
`claude/optimize-timescaledb-performance-pUKoh` as a single Flyway migration
(`V1.8.0`). The remaining work is split across the JDBC/Hibernate layer and
the application's query patterns. None of the remaining mitigations require
schema changes that block writes.

| # | Area | Problem | Severity | Effort |
|---|------|---------|----------|--------|
| 1 | Schema | Composite `(timeseries_id, time DESC)` index dropped in V1.4.0 | ✅ FIXED | — |
| 2 | Schema | Compression policy = chunk interval (1d) → upserts decompress chunks | ✅ FIXED | — |
| 3 | Schema | No chunk skipping on `timeseries_id` for multi-chunk reads | ✅ FIXED | — |
| 4 | Ingest | Insert binds ~40 001 parameters per 20 k-row batch (parser overhead) | 🟥 HIGH | 1 d |
| 5 | Ingest | `INSERT ... ON CONFLICT DO UPDATE` decompresses; could be `DO NOTHING` for dedup | 🟧 MED | 0.5 d |
| 6 | Ingest | `assertDataPointsMatchTimeseriesValueType` runs O(n) per batch | 🟨 LOW | 0.25 d |
| 7 | Ingest | Each upload pays 2 Neo4j round-trips before insert (`getContainer` ×2) | 🟧 MED | 0.5 d |
| 8 | Query | No continuous aggregates → range aggregations rescan raw rows every request | 🟪 ARCH | 2 d |
| 9 | Query | `findTimeseries` uses 5-string `WHERE` on every read; no covering index | 🟧 MED | 0.5 d |
| 10 | Query | `parallelStream` for multi-series fan-out competes with the request thread pool | 🟨 LOW | 0.5 d |
| 11 | Query | Returned result sets are unbounded — large windows materialise everything in heap | 🟧 MED | 1 d |
| 12 | Driver | `reWriteBatchedInserts=true` not set on JDBC URL | 🟧 MED | 0.1 d |
| 13 | Driver | Default Agroal pool size is 20; no min/max configured | 🟨 LOW | 0.1 d |
| 14 | Config | `quarkus.hibernate-orm.metrics.enabled=true` adds per-query interceptor cost | 🟨 LOW | 0.1 d |
| 15 | Ops | No retention policy — table grows forever, planner stats degrade on cold chunks | 🟧 MED | 0.5 d |
| 16 | Ops | No dedicated `quarkus.flyway.connect-retries` / health timeouts for long V1.7.0 migration | 🟨 LOW | 0.1 d |
| 17 | Schema | Time stored as `bigint` ns, not `timestamptz` — disables several Timescale optimiser paths | 🟪 ARCH | week+ |
| 18 | Schema | Single-table polymorphic value (`int`/`double`/`text`/`bool` columns) — wastes 30–60% per row | 🟪 ARCH | week+ |
| 19 | API | Read endpoints materialise the entire result set into a `List` before responding (CSV "stream" only streams serialisation) | 🟥 HIGH | 1-2 d |
| 20 | API | No `hibernate.jdbc.fetch_size` set → PG JDBC buffers full result client-side regardless of code path | 🟧 MED | 0.1 d |
| 21 | API | Dual identity: data endpoints address timeseries via 5-tuple, semantic annotations via numeric `timeseries_id` | 🟧 MED → 🟪 ARCH | M (compat-aware rollout) |

---

## 2. Background — what TimescaleDB does that you must work *with*

A short orientation that explains why several of the issues below exist.

- **Hypertable = parent + child chunks.** Each chunk is a regular Postgres
  table covering a slice of the time dimension (1 day here). The hypertable
  has only catalog metadata; data lives in chunks.
- **Per-chunk indexing.** When you `CREATE INDEX` on a hypertable, Timescale
  creates the same index on every existing and future chunk. There is **no
  global index**.
- **Compression replaces a chunk** with a compressed columnar form, segmented
  by `compress_segmentby` columns and ordered by `compress_orderby`. Inserts,
  updates, and deletes against compressed chunks require Timescale to either
  decompress the affected segment (slow) or use the `INSERT INTO compressed
  hypertable` fast path (only valid for new rows, not updates).
- **`ON CONFLICT DO UPDATE` on a compressed chunk** is only supported via
  decompression — there is no compressed upsert path. This is the single most
  important fact for understanding the ingest slowdown.
- **Chunk exclusion** at plan time uses chunk constraints derived from the
  partitioning column (here: `time`). Other filters (e.g. `timeseries_id`)
  cannot exclude chunks unless **chunk skipping** is enabled, which maintains
  per-chunk min/max sketches.

---

## 3. Schema layer (FIXED in V1.8.0)

Recap of the three already-merged fixes for completeness, then the structural
issues that remain.

### 3.1 Restored `(timeseries_id, time DESC)` index ✅
V1.3.0 added an index on `timeseries_id`; V1.4.0 dropped it with the comment
*"useless with compression"*. That comment is half right: compressed chunks
get an internal index on the `segmentby` column. Uncompressed chunks (after
fix 3.2, the entire 7-day hot window) get only the auto-created `time` index.
Per-series queries then degenerate into chunk scans filtered by time.

### 3.2 Compression delay 1 day → 7 days ✅
With chunk interval = 1 day and compression delay = 1 day, every chunk older
than the active one is compressed. Any upsert against historical data —
including normal idempotent re-uploads using the existing
`ON CONFLICT (timeseries_id, time) DO UPDATE` clause — forces a segment
decompression. A 7-day delay matches the TimescaleDB best practice of "delay
≥ ~7 chunks" and keeps a hot write/read window.

### 3.3 `enable_chunk_skipping` on `timeseries_id` ✅
Without this, a query like *"series 12345 over the last year"* opens every
chunk in the time range. With it, the planner can skip chunks whose
min/max range for `timeseries_id` doesn't overlap the predicate.

### 3.4 Chunk interval review — likely fine 🟨
1-day chunks suit shepard's apparent ingest scale (~minute-resolution sensor
data). Two checks worth running before changing anything:

```sql
SELECT chunk_name,
       pg_size_pretty(total_bytes)         AS size,
       pg_size_pretty(uncompressed_total)  AS uncompressed
FROM   chunks_detailed_size('timeseries_data_points');
```

- **If chunks are < 25% of `shared_buffers` (4 GB → ~1 GB) when uncompressed**,
  the interval is fine.
- **If chunks are routinely under 10 MB**, you have many small chunks — planner
  overhead becomes visible at high chunk counts (>10 000). Increase to 7 days.
- **If chunks routinely exceed `shared_buffers`**, queries on the active chunk
  start spilling. Decrease to 12 hours.

### 3.5 Time as `bigint` nanoseconds 🟪 ARCH
The `time` column is `bigint not null` storing epoch ns. This works, but:

- Several TimescaleDB convenience features (`time_bucket_ng`, retention by
  interval, `set_chunk_time_interval` with `INTERVAL`, hyperfunctions like
  `time_weight`) are friendlier with `timestamptz`.
- Postgres planners use `timestamptz` statistics histograms more aggressively
  than `bigint` for time-range predicates.
- Hyperfunctions for downsampling (`asap_smooth`, `lttb`) require `timestamptz`
  in some versions.

**Mitigation:** treat as a future migration, **not urgent**. The ns precision
requirement from the InfluxDB era is real; if you ever decide ms precision is
enough, `timestamptz` opens up a richer toolset. Workaround in the meantime
is a generated column:

```sql
ALTER TABLE timeseries_data_points
  ADD COLUMN time_ts timestamptz
  GENERATED ALWAYS AS (to_timestamp(time / 1e9)) STORED;
```
…then you can build continuous aggregates against `time_ts` while keeping
all current code paths reading `time`.

### 3.6 Polymorphic value columns 🟪 ARCH
Each row carries `double_value`, `int_value` (`bigint` since V1.7),
`string_value`, `boolean_value`, of which exactly one is non-null. Postgres
stores all four nullable columns; with NULL bitmap and alignment, an
all-numeric row burns ~24 bytes for 16 bytes of payload (~50% overhead).
Compression hides this on cold chunks but not on the hot (uncompressed)
window — and it makes `CREATE INDEX … INCLUDE (…)` impossible without
ballooning index size.

**Mitigation (long-term):** split into per-value-type hypertables
(`timeseries_data_points_double`, `_int`, `_string`, `_boolean`). Each becomes
2-column (`timeseries_id`, `time`) + value, no NULLs, smaller rows, smaller
indexes, faster compression. Routing in the repository is just a `switch` on
`valueType` (already present). Plan as a separate effort.

---

## 4. Ingest path

Hot path: `TimeseriesService.saveDataPoints` →
`TimeseriesDataPointRepository.insertManyDataPoints` →
`buildInsertQueryObject`.

### 4.1 Per-row parameter binding 🟥 HIGH
**`backend/.../TimeseriesDataPointRepository.java:176-212`**

```java
queryString.append(
  IntStream.range(startInclusive, endExclusive)
    .mapToObj(index -> "(:timeseriesid" + ",:time" + index + ",:value" + index + ")")
    .collect(Collectors.joining(","))
);
…
IntStream.range(startInclusive, endExclusive).forEach(index -> {
  query.setParameter("time" + index, entities.get(index).getTimestamp());
  query.setParameter("value" + index, entities.get(index).getValue());
});
```

For a 20 000-row batch the produced SQL has ≈ 40 001 bind parameters and a
1.5 MB query string. Costs paid every batch:

1. Hibernate's named-parameter expansion regex'es a million-character
   string.
2. PostgreSQL parses, plans, and binds 40 001 parameters (the protocol limit
   is 65 535; you are within a factor of 1.6 of hitting it).
3. The plan cache cannot reuse the prepared statement because the literal
   number of placeholders changes per batch size.

**Mitigation.** Replace with array-binding via `unnest`:

```sql
INSERT INTO timeseries_data_points (timeseries_id, time, double_value)
SELECT :tsid, t, v
FROM   unnest(cast(:times AS bigint[]), cast(:vals AS double precision[]))
       AS s(t, v)
ON CONFLICT (timeseries_id, time) DO UPDATE
  SET double_value = EXCLUDED.double_value;
```

3 parameters per batch instead of 40 001. Java side uses
`entityManager.unwrap(Session.class).doWork(conn -> { … conn.createArrayOf(…) })`.

Expected impact: **2–5× faster ingest**, lower CPU on both backend and DB,
and the ability to push batch sizes higher (currently capped by parameter
count).

### 4.2 `ON CONFLICT DO UPDATE` vs `DO NOTHING` 🟧 MED
The current upsert overwrites the value column when a `(timeseries_id, time)`
row already exists. For the common case of clients re-uploading the same
batch (idempotent retries), the new value equals the old value. The decompression
+ rewrite cost is paid for nothing.

**Mitigation.**
- If overwriting on conflict is not a documented contract, switch to
  `ON CONFLICT … DO NOTHING`. Re-uploads become free on already-ingested
  rows.
- If the contract must keep "later upload wins", at minimum add
  `WHERE EXCLUDED.<value> IS DISTINCT FROM <table>.<value>` so identical
  rewrites are skipped.

Verify with `git log -p` and a quick check of `architecture/` whether
overwrite is part of the public contract — I did not find evidence either way.

### 4.3 O(n) value-type assertion 🟨 LOW
**`TimeseriesService.assertDataPointsMatchTimeseriesValueType`**:

```java
for (TimeseriesDataPoint dataPoint : dataPoints) {
  DataPointValueType expectedType = ObjectTypeEvaluator
      .determineType(dataPoint.getValue())
      .orElseThrow(...);
  assertValueTypeMatchesTimeseries(timeseriesEntity, expectedType);
}
```

`determineType` does `instanceof` checks with reflection-like fallbacks; for
20 k-row payloads this can dominate the request when payloads are small. Most
production payloads are homogeneous (the timeseries has a fixed type by
construction).

**Mitigation.** Sample first row + assert remaining rows share class via
`getClass() == first.getClass()`. ~20× faster validation.

### 4.4 Ingest preamble: 2× Neo4j round-trips 🟧 MED
`TimeseriesService.saveDataPoints(long, Timeseries, List, DataPointValueType)`:

```java
timeseriesContainerService.getContainer(timeseriesContainerId);              // hit 1
timeseriesContainerService.assertIsAllowedToEditContainer(timeseriesContainerId); // potentially hit 2
TimeseriesEntity ts = getOrCreateTimeseries(...);   // round-trip to Postgres + maybe upsert
```

`getContainer` is called *twice* in the same request: once at the REST entry
and once inside `saveDataPoints`. For high-frequency micro-batches this latency
adds up.

**Mitigation.** Make `saveDataPoints` accept the already-loaded `TimeseriesContainer`
or guard with `@CacheResult` (Quarkus caffeine cache is already configured,
`maximum-size=8192`, `expire-after-write=3h`). Add a small Caffeine cache
keyed by `containerId` for `TimeseriesContainer` lookups and
`(containerId, Timeseries)` for `TimeseriesEntity` lookups. Eviction on
delete only.

---

## 5. Query path

### 5.1 Five-string lookup on every read 🟧 MED
`TimeseriesRepository.findTimeseries`:

```sql
WHERE containerId = ?
  AND measurement = ? AND field = ? AND symbolicName = ? AND device = ? AND location = ?
```

This is run before *every* point query, on every CSV export, on every metrics
call. The `timeseries_unique_b4a836fabc25` unique constraint added in V1.2.0
provides a btree on the same 6 columns, so it's index-backed — but every
lookup traverses 6 string comparisons and Hibernate materialises the entity.

**Mitigation.**
- Cache `TimeseriesEntity` by `(containerId, Timeseries)` in Caffeine for
  ~5 min. Eviction on edit/delete only. The 5-string identifier is by design
  immutable for an entity, so cache hits are safe.
- Even better: have the REST layer accept `timeseriesId` directly when
  available (the entity is returned to the client at create time; clients can
  store it). Reserve the 5-string lookup for the import path.

### 5.2 No continuous aggregates 🟪 ARCH — biggest InfluxDB-era regression
This is the single biggest reason aggregation queries feel slow vs. InfluxDB.
InfluxDB ships continuous queries / downsampling out of the box; with shepard
on TimescaleDB, every `time_bucket(…) … AVG(…)` aggregation rescans **raw
rows** in the time range each time the dashboard refreshes.

For a 90-day window over a 1-Hz series, that's ~7.7 M rows per chart load,
per series, per refresh. Compression helps but does not eliminate the
decompression+aggregation work.

**Mitigation.** Define one or two continuous aggregates for the common bucket
sizes used by the frontend (likely 1 minute and 1 hour — confirm with
frontend code in `frontend/composables/context/useFetchTimeseries*.ts`):

```sql
CREATE MATERIALIZED VIEW ts_data_1min
WITH (timescaledb.continuous) AS
SELECT timeseries_id,
       time_bucket(60_000_000_000, time)        AS bucket,
       AVG(double_value)   AS avg_d,
       MIN(double_value)   AS min_d,
       MAX(double_value)   AS max_d,
       COUNT(*)            AS n
FROM   timeseries_data_points
WHERE  double_value IS NOT NULL
GROUP  BY timeseries_id, bucket;

SELECT add_continuous_aggregate_policy('ts_data_1min',
  start_offset => BIGINT '604800000000000',  -- 7 days
  end_offset   => BIGINT '60000000000',      -- 1 minute
  schedule_interval => INTERVAL '1 minute');
```

Then route the read in `buildSelectQueryObject` to the CAGG when
`timeSliceNanoseconds` is a multiple of an existing CAGG bucket and the
aggregation is one the CAGG materialises. Falls back to raw query otherwise.

Caveats:
- One CAGG per value type (or per polymorphic column you want to aggregate).
- CAGGs operate on the polymorphic columns by default; will be much cleaner
  after the per-type table split (§3.6).
- Timescale 2.x supports realtime CAGGs (data newer than `end_offset` is
  computed live and unioned).

Expected impact for dashboard queries: **10–100× faster** depending on
window size.

### 5.3 No-bucket aggregations still build a bucket 🟨 LOW
`buildSelectQueryObject` always passes through a `time_bucket(…)` even for
"single number" queries. When the caller doesn't pass `timeSliceNanoseconds`
the code synthesises one equal to the whole window, then groups on it.

That works, but adds a hash-aggregate stage Postgres has to choose. The
adjacent `buildSelectAggregationFunctionQueryObject` already does the
single-row form correctly. There's no observed regression — flagging only
because the duplicated branching in two methods is a maintenance hazard. Low
priority; consider folding the two methods together.

### 5.4 Parallel stream for multi-series fan-out 🟨 LOW
`TimeseriesService.getManyTimeseriesWithDataPoints`:

```java
timeseriesList.parallelStream().forEach(ts -> {
  timeseriesWithDataPointsQueue.add(...
      getDataPointsByTimeseriesActivatedRequestContext(...));
});
```

`parallelStream()` uses the **common ForkJoinPool**, sized to
`Runtime.getRuntime().availableProcessors() - 1`. Two issues:

- Under load this competes with every other parallel-stream user in the JVM
  (Quarkus, RESTEasy reactive, etc.). Tail latency degrades non-locally.
- Each parallel branch acquires its own JDBC connection; the Agroal pool
  default of 20 can be exhausted by a single fan-out request with 20+
  series.

**Mitigation.** Use a bounded `ForkJoinPool` or `Executors.newFixedThreadPool`
sized to ≤ `quarkus.datasource.jdbc.max-size / 2`, owned by the service.
Better still: **a single SQL query** with `timeseries_id IN (?, ?, ?)` and
post-process in Java. One round-trip, planner can use the composite index
once.

### 5.5 Unbounded result sets 🟧 MED
`getDataPointsByTimeseries` and CSV export pull **all matching rows** into a
`List<TimeseriesDataPoint>`. A 90-day window at 1 Hz = 7.8 M doubles
≈ 200 MB on heap as Java objects. With concurrent users this is an OOM
vector and explains why GC pauses can dominate "sluggish" reports.

**Mitigation.**
- Server-enforced max-row cap with explicit error: e.g. 1 M rows; client must
  pass a `groupBy` to get more.
- Or stream the result: switch the export path to
  `Query.getResultStream()` + `Response.ok(StreamingOutput)` and write CSV
  rows as they come off the cursor. JVM heap stays flat.
- For JSON responses, the only safe answer is the row cap.

---

## 6. JDBC driver and connection pool

### 6.1 `reWriteBatchedInserts=true` 🟧 MED
Not set in either `application.properties` or `infrastructure/docker-compose.yml`.
With `reWriteBatchedInserts=true`, the PG JDBC driver collapses repeated
single-row inserts into multi-VALUES inserts. Even with the array-unnest
mitigation (§4.1) this is worth setting — the COPY path
(`insertManyDataPointsWithCopyCommand`) doesn't use it, but other Hibernate-
managed inserts (timeseries metadata, migration tasks, etc.) benefit.

**Mitigation.** Add to JDBC URL via `quarkus.datasource.jdbc.additional-jdbc-properties`:

```properties
quarkus.datasource.jdbc.additional-jdbc-properties.reWriteBatchedInserts=true
```

### 6.2 Agroal pool sizing 🟨 LOW
No `quarkus.datasource.jdbc.max-size` / `min-size` configured. Agroal's
default `max-size` is 20, `min-size` 0, with idle eviction.

For a backend serving CSV imports + dashboards in parallel, 20 is tight,
especially when fan-out queries (§5.4) acquire connections per series.
PostgreSQL's `tweak-db-settings.sql` sets `max_connections = 20`, so the
upper bound is locked there. If the only Postgres consumer is shepard, raise
both to ~50.

**Mitigation.**

```properties
quarkus.datasource.jdbc.min-size=4
quarkus.datasource.jdbc.max-size=30
quarkus.datasource.jdbc.acquisition-timeout=PT10S
```
…and bump `max_connections` in `tweak-db-settings.sql` to 50, and
`shared_buffers`/`work_mem` proportionally if other consumers are added.

### 6.3 Server-side prepared statement threshold 🟨 LOW
PG JDBC's `prepareThreshold` defaults to 5 — the same statement must run 5
times before it gets a server-side plan. Because §4.1 generates **a different
SQL string for every batch size**, this threshold is never reached. Once §4.1
is fixed, the SQL becomes constant per `(valueType)` and prepareThreshold
kicks in automatically. No change needed once §4.1 lands.

---

## 7. Configuration / operational

### 7.1 Hibernate ORM metrics 🟨 LOW
**`application.properties:110`**:
```properties
quarkus.hibernate-orm.metrics.enabled=true
```

Per-statement and per-session statistics on the hibernate fast path. Useful
during dev profiling, costly in prod (Hibernate docs warn it can add ~5%
latency on small queries). Move to `%dev` profile only.

### 7.2 No retention policy 🟧 MED
There is no `add_retention_policy('timeseries_data_points', …)`. The table
will grow until disk is full. As a side-effect, autovacuum and ANALYZE on
catalog tables get expensive once chunk count climbs into the tens of
thousands.

**Mitigation.** Even a generous policy ("drop chunks older than 5 years")
prevents pathological growth and keeps the catalog small:

```sql
SELECT add_retention_policy('timeseries_data_points',
                            BIGINT '157680000000000000'); -- 5 years
```

Coordinate with users on the actual retention requirement before applying.

### 7.3 `effective_io_concurrency=200` 🟨 LOW — likely fine
`tweak-db-settings.sql:22` sets it to 200, appropriate for SSD. No change.

### 7.4 Statistics target 🟨 LOW
`default_statistics_target=300` is on the high side and slows ANALYZE; for
hypertables it's also overridden per-chunk by Timescale. Either leave at the
PG default (100) or set it explicitly per column where you know it matters
(unlikely here).

### 7.5 Long-running V1.7 migration 🟨 LOW
`V1_7_0__int_to_bigint.java` decompresses every chunk one by one. On a large
deployment this can take hours. Flyway runs migrations inside one transaction
by default; if the connection drops mid-migration the work is lost.

**Mitigation.** Add `@Transactional(propagation = …)` boundary around the
per-chunk loop, or annotate the migration with
`@CustomLog` + `executeInTransaction = false` (Flyway feature) so each chunk
is its own transaction. Future-proofing only — V1.7 has presumably already
run on production.

---

## 8. Architecture-level mitigations

These are scoped as separate projects.

### 8.1 Per-value-type hypertables 🟪 ARCH
Already discussed in §3.6. Smaller rows, smaller indexes, simpler CAGGs,
better compression ratios.

### 8.2 `timestamptz` over `bigint` ns 🟪 ARCH
Already discussed in §3.5. Unlocks Timescale hyperfunctions and cleaner CAGG
syntax. Keep ns column for backward compatibility during transition.

### 8.3 Continuous aggregates 🟪 ARCH (already partially in §5.2)
The biggest single perf win still on the table. Pair with a query router in
`buildSelectQueryObject` that picks the CAGG when bucket sizes align.

### 8.4 Read replica for analytics 🟪 ARCH
If dashboards are the dominant load, a streaming replica + Pgpool / hot
standby splits ingest from read load. Out of scope unless dashboard
contention is observed.

---

## 9. Verification

Recommended steps after each mitigation lands:

1. **Reproducible benchmark.** `load-tests/` already exists in the repo. Add
   a scenario that ingests N points and queries M aggregations, captures
   p50/p95/p99 from the existing Micrometer timers
   (`shepard.timeseries-data-point.batch-insert`,
   `shepard.timeseries-data-point.query`). The Prometheus / Grafana stack is
   already in `docker-compose.yml` (under the `monitoring` profile).
2. **Per-mitigation A/B.** Run the benchmark on `main` and on the branch with
   the mitigation. Record before/after for the relevant timer.
3. **EXPLAIN ANALYZE on production-shaped queries.** With the new V1.8.0
   migration in place, verify the new index is chosen:

   ```sql
   EXPLAIN (ANALYZE, BUFFERS)
   SELECT time, double_value
   FROM   timeseries_data_points
   WHERE  timeseries_id = 12345
     AND  time BETWEEN 1700000000000000000 AND 1730000000000000000
   ORDER  BY time;
   ```
   Look for `Index Scan using timeseries_data_points_id_time_idx_<chunk>`
   on the recent (uncompressed) chunks, and `DecompressChunk` with a
   `Bitmap Index Scan` on `timeseries_id` for older ones.
4. **Compression ratio sanity check.**

   ```sql
   SELECT pg_size_pretty(before_compression_total_bytes) AS before,
          pg_size_pretty(after_compression_total_bytes)  AS after
   FROM   hypertable_compression_stats('timeseries_data_points');
   ```
   Expect ≥ 10× for double-valued series, less for highly entropic strings.

---

## 10. Recommended sequence

1. **Already merged (V1.8.0)** — index, compression delay, chunk skipping.
2. **Sprint 1 (low-risk code, ~2 d total).**
   §4.1 (unnest insert), §4.3 (cheap type check), §6.1
   (`reWriteBatchedInserts`), §7.1 (disable Hibernate metrics in prod),
   §6.2 (pool sizing), §5.5 (row cap).
3. **Sprint 2 (~1 wk).**
   §5.1 (Caffeine for `TimeseriesEntity`), §4.4 (drop duplicate
   `getContainer`), §5.4 (single-query fan-out), §7.2 (retention policy
   discussion + apply).
4. **Project: continuous aggregates (§5.2).** Largest win, deserves its
   own design / review cycle.
5. **Project: per-type hypertables + `timestamptz` (§3.5, §3.6, §8.1, §8.2).**
   Plan with a roll-forward Flyway migration that copies in chunks and
   atomically swaps. Likely the next major release.

---

## 11. Data access through the API — streaming and identifier alignment

This section was added in a follow-up review focused on the read path as it
appears to API consumers, not just the database. Two themes:

- **(11.A) Streaming reads without materialising the whole result set** —
  closing the heap-blow-up vector flagged in §5.5 properly, end-to-end.
- **(11.B) Aligning the numeric `timeseries_id` (used by semantic
  annotations on a `TimeseriesContainer`) with the 5-tuple addressing used
  by every data-point endpoint** — needed for any future search /
  cross-reference work and to remove the dual-identifier hazard.

### 11.A Streaming the read path end-to-end

#### 11.A.1 Where data is materialised today 🟥 HIGH

Following the request through `TimeseriesRest`:

| Endpoint | Method | Repo call | Materialisation |
|---|---|---|---|
| `GET /timeseries-containers/{id}/payload` | `getDataPointsByTimeseries` | `queryDataPoints` | full `List<TimeseriesDataPoint>` (`.getResultList()`) |
| `GET /timeseries-containers/{id}/export` (CSV) | `getDataPointsByTimeseries` then `CsvConverter.convertToCsv` | same | full list, then CSV serialised line-by-line on top of the materialised list |
| `GET …/payload` (multi-series) | `getManyTimeseriesWithDataPoints` | parallel-stream over `getDataPointsByTimeseriesActivatedRequestContext` | one full list per series, all kept until the response is assembled |

**`TimeseriesDataPointRepository.queryDataPoints` (lines 138-158)** is the
single bottleneck. It calls `query.getResultList()`. Every read endpoint
goes through it.

The CSV path looks like it streams (`CsvInputStream` extends `InputStream`),
but the constructor receives an already-materialised list — the stream
flows from the in-memory list into the HTTP response, not from the database
into the response. This is the heap vector flagged in §5.5; a 90-day
1 Hz export is ~7.8 M rows ≈ 200 MB of `TimeseriesDataPoint` objects on
heap before the first byte goes out.

`application.properties` does **not** set `hibernate.jdbc.fetch_size`, so
the JDBC fetch size is the PG JDBC default (0 = "all rows"). Even if the
code switched to a stream API, without a fetch size the driver would still
buffer the entire result set client-side.

#### 11.A.2 Mitigation — streaming end-to-end 🟥 HIGH

The smallest viable change set is:

1. **Add a streaming repository method** alongside `queryDataPoints`:

   ```java
   public Stream<TimeseriesDataPoint> streamDataPoints(int timeseriesId,
                                                       DataPointValueType type,
                                                       TimeseriesDataPointsQueryParams qp) {
     Query q = buildSelectQueryObject(timeseriesId, type, qp);
     q.setHint(QueryHints.HINT_FETCH_SIZE, 5_000);
     q.unwrap(org.hibernate.query.Query.class).setFetchSize(5_000);
     return q.getResultStream(); // jakarta.persistence.Query#getResultStream
   }
   ```

   `getResultStream()` returns a JDBC-backed `Stream` that pulls rows in
   `fetchSize`-sized blocks. On PostgreSQL the underlying cursor needs
   autocommit off; with Hibernate inside a `@Transactional` boundary this
   is handled automatically.

2. **Switch the CSV path to the stream**. `CsvConverter` and
   `CsvRowLineProvider` already iterate row-by-row; they only need a
   `Stream` (or `Iterator`) handle instead of a `List`. JAX-RS exposes
   the response body as a `StreamingOutput`:

   ```java
   StreamingOutput body = out -> {
     try (var rows = repo.streamDataPoints(id, type, qp);
          var w = new OutputStreamWriter(out, UTF_8)) {
       w.write(header);
       rows.forEach(p -> writeCsvRow(w, p));
     }
   };
   return Response.ok(body).type("text/csv").build();
   ```

   The transactional boundary must wrap the `forEach`, not the resource
   method — Quarkus's `@ActivateRequestContext` + a service-layer
   `@Transactional` on a method that *takes a `Consumer<TimeseriesDataPoint>`*
   keeps the cursor open while JAX-RS writes bytes.

3. **For JSON responses**, streaming is constrained because clients today
   expect a single JSON object with a `dataPoints` array. Two options:

   - **Stream the array** with Jackson's `JsonGenerator`: write the wrapper
     start, stream each point, write the wrapper end. Same pattern as CSV.
     Backwards-compatible; just changes how the bytes flow.
   - **Add a hard row cap** (e.g. 1 M rows) and return `413 Payload Too
     Large` with a `groupBy` hint when exceeded. Recommended *in
     addition* to streaming so a buggy client cannot DoS the JVM with a
     90-day request on a 1 kHz series.

4. **Multi-series fan-out (`getManyTimeseriesWithDataPoints`).** Today this
   spawns N parallel queries each materialising a `List`. After streaming
   lands per-series, a multi-series stream is just a flat-map over the
   timeseries IDs — but the better fix (from §5.4) is a single SQL query
   with `timeseries_id IN (?)` and `ORDER BY timeseries_id, time`, then a
   server-side group-by-key while writing the response.

5. **Set `hibernate.jdbc.fetch_size` globally** as a backstop:

   ```properties
   quarkus.hibernate-orm.jdbc.statement-fetch-size=2000
   ```

   Affects every Hibernate query in the app; safe value for a backend
   that does not pull arbitrarily wide rows. Per-query hints override it.

6. **Pagination as a public API.** Independent of streaming, the JSON
   endpoint should accept `pageSize` / `cursor` query params.
   Cursor = `(time, timeseries_id)` of the last returned row. No `OFFSET`
   — TimescaleDB handles `WHERE (time, ts_id) > (?, ?) ORDER BY time
   LIMIT n` cleanly via the composite index restored in V1.8.0.

Expected impact: **constant heap** independent of window size, no more
GC-pause-driven "sluggish" reports on big exports, and a safety rail
for JSON responses that doesn't need every client to update.

#### 11.A.3 Quarkus reactive vs. blocking — a note 🟨 LOW

Quarkus has a reactive REST stack. Tempting to switch the read path to
`Multi<TimeseriesDataPoint>`. **Don't, in this iteration.** Hibernate ORM
+ Agroal is blocking; mixing reactive at the HTTP edge with blocking at
the JDBC edge yields the worst of both (event-loop starvation, opaque
back-pressure). Stay blocking; rely on `StreamingOutput` + a worker
thread. Consider Hibernate Reactive only as a coordinated effort with
the rest of the app.

### 11.B Aligning `timeseries_id` (annotations) with the 5-tuple (data)

#### 11.B.1 The current dual-identity problem 🟧 MED → 🟪 ARCH

The system addresses an individual timeseries two different ways:

- **Numeric ID path.** `TimeseriesEntity.id` (Postgres `bigint`) is the
  primary key and the value Neo4j uses for semantic annotations. The
  `AnnotatableTimeseries` Neo4j node stores
  `(containerId, timeseriesId)` as the unique tuple, and the semantic
  annotation REST surface is keyed by it
  (`/timeseries-containers/{cid}/timeseries/{tsid}/semantic-annotations`,
  `AnnotatableTimeseriesRest.java`).
- **5-tuple path.** Every data-point endpoint (`/payload`, `/export`,
  list / filter under `/timeseries`) takes
  `(measurement, field, symbolicName, device, location)` as query
  parameters and resolves them to the numeric ID via
  `TimeseriesRepository.findTimeseries` (lines 21-42) on each request.

Consequences:

- **Duplicate index work.** The 5-tuple lookup is a string-heavy btree
  probe before *every* read; the numeric ID is what the hot path
  actually needs (§5.1).
- **Cross-feature mismatch.** A user who annotates a series via the
  Neo4j path then queries it via the data path has two unrelated
  identifiers in flight. The frontend has to round-trip through
  "list timeseries → match by 5-tuple → get id" to bridge them.
- **No stable external identifier.** The 5-tuple is part of the public
  contract; the numeric ID is not exposed as a primary lookup
  parameter on data endpoints. Client libraries that cache a
  numeric ID today have to hope it stays valid (it does, but isn't
  documented).
- **Semantic-search blocker.** Any future "find timeseries annotated
  with X and aggregate its data" workflow has to translate the
  Neo4j query result (a list of `(containerId, timeseriesId)` pairs)
  back to 5-tuples to call the data endpoints — gratuitous.

#### 11.B.2 Target state — numeric ID is the canonical address

The endgame is: a `TimeseriesEntity.id` is the public, stable
identifier for a timeseries. The 5-tuple stays as a *creation-time*
descriptor (a label set), not as a routing key.

```text
Today:   client → /payload?measurement=…&field=…&… → DB lookup → numeric id → data
Target:  client → /payload?timeseriesId=12345 → data
                                    ↑
                              same id used by /semantic-annotations
```

Concretely:

1. **Expose `id` on every timeseries response.** The `TimeseriesIO`
   DTO returned from `/timeseries` already carries it; verify it is
   serialised (not just used internally). If not, add it.
2. **Accept `timeseriesId` as an alternative on every data endpoint.**
   New optional query parameter `timeseriesId` on `/payload`,
   `/export`, `getManyTimeseriesWithDataPoints`. When present, skip
   the 5-tuple resolve. When absent, fall back to today's behaviour.
3. **Document numeric-id as preferred** in OpenAPI and clients.
4. **Add the same dual-identity affordance to semantic annotations.**
   Where today `/timeseries-containers/{cid}/timeseries/{tsid}/…`
   takes only `tsid`, also accept the 5-tuple as query parameters
   on the parent `/timeseries-containers/{cid}/semantic-annotations`
   (or on a new `…/timeseries:lookup` endpoint that returns the id).
   Keeps the IRI shape stable while letting clients that only have
   the 5-tuple use it.
5. **Container ↔ container alignment.** `TimeseriesContainer` (Neo4j)
   does *not* hold the list of `timeseriesId`s today; the join lives
   implicitly in Postgres via `timeseries.containerId`. Two options
   when this becomes painful:
   - **Materialised list on the container.** Add
     `@Relationship(type=HAS_TIMESERIES) List<AnnotatableTimeseries>`
     so the Neo4j-side queries (annotation searches, see
     `/aidocs/semantics/13-search-improvements.md`) don't need a Postgres join.
     Maintain on create/delete; rebuild via migration. Storage is
     small (one node + edge per timeseries).
   - **Status quo + lookup view.** Keep the join in Postgres and
     just expose a Neo4j projection in the search API. Simpler.
     Pick this unless the search workload demands otherwise.

Recommendation: start with (1)–(3); they are additive and
backwards-compatible. (4) follows once at least one client has
adopted (2). (5) is a search-driven decision — defer until
`13-search-improvements.md` is acted on.

#### 11.B.3 Backwards compatibility plan 🟧 MED

The 5-tuple is part of the public REST contract — current clients
(Python, frontend, manual cURL users) rely on it. Plan:

| Step | Visible change | Breaking? |
|---|---|---|
| 1 | `TimeseriesIO` always carries `id`; OpenAPI updated | No |
| 2 | All read endpoints accept **either** `timeseriesId` or the 5-tuple. If both are passed, `timeseriesId` wins; if neither, return 400 | No (5-tuple still works) |
| 3 | Clients (`@dlr-shepard/shepard-client`, `scripts/`) updated to prefer `timeseriesId`. Bump minor version | No |
| 4 | Deprecation banner in OpenAPI on the 5-tuple parameters of read endpoints (`@deprecated`); release notes call out the migration | No |
| 5 | After ≥ 2 minor releases with the deprecation visible: 5-tuple removed from read endpoints; remains on **create**, where it is the actual descriptor of the series | **Yes — major version bump** |

Step 5 is gated on telemetry: log a one-line warning each time a
read endpoint is called without `timeseriesId`. Don't proceed until
the rate is below an agreed threshold.

#### 11.B.4 What this unlocks

- **Caching becomes trivial.** §5.1 already proposes Caffeine on
  `(containerId, 5-tuple) → TimeseriesEntity`. Switching to id-keyed
  reads removes the cache from the hot path entirely; the entity
  doesn't need to be loaded for a data query at all (the id alone
  is enough to hit the composite index in §3.1).
- **Streaming and id-addressing are independent**, but they
  compose: a streaming export keyed by numeric id is the cleanest
  call shape for high-volume consumers (DataLad/MLflow integrations,
  external dashboards).
- **Annotations and data converge.** Search by annotation returns
  numeric ids; the same ids are valid arguments to data endpoints.
  No translation step. This is a precondition for the
  unified-search proposal in `13-search-improvements.md`.

### 11.C Cross-reference

- §3.1, §5.1 — composite index and 5-tuple lookup; this section
  generalises the fix.
- §5.5 — unbounded result sets; §11.A is the concrete remediation.
- §5.4 — multi-series fan-out; §11.A.2 step 4 supersedes the
  parallel-stream proposal.
- `aidocs/semantics/13-search-improvements.md` — depends on §11.B for
  cross-store id alignment.

---

## 12. Appendix — paths referenced

- Migrations: `backend/src/main/resources/db/migration/V1.0.0…V1.8.0`,
  `backend/src/main/java/de/dlr/shepard/data/timeseries/migrations/V1_7_0__int_to_bigint.java`
- Repository: `backend/src/main/java/de/dlr/shepard/data/timeseries/repositories/TimeseriesDataPointRepository.java`
- Service: `backend/src/main/java/de/dlr/shepard/data/timeseries/services/TimeseriesService.java`
- Config: `backend/src/main/resources/application.properties`,
  `infrastructure/tweak-db-settings.sql`,
  `infrastructure/docker-compose.yml`
- Frontend (slow path): `frontend/composables/context/useFetchTimeseries*.ts`
