# Spatial-data performance evaluation (PostGIS reassessment)

**Status.** Design note, no implementation in scope. Captured 2026-05-19
in response to the user's ask to "reevaluate the spatial data model
with PostGIS and the implemented feature set; see if you can find more
performant solutions."

**Companion docs.** `aidocs/data/81-spatial-data-binding.md` (the
canonical spatial-data design); current migration
`plugins/spatial/src/main/resources/db/spatial/migration/V1.0.0__setup_spatial_data_tables.sql`.

## 1. Current state (inventory)

**Table.** `spatial_data_points` — a single PostgreSQL table partitioned
by HASH on `container_id` into 100 partitions
(`spatial_data_points_p0` … `_p99`). Columns: `id`, `container_id`,
`time`, `position GEOMETRY(POINTZ, 4326)`, `metadata JSONB`,
`measurements JSONB`.

**Indexes.**

| Index | Type | Column(s) | Purpose |
|---|---|---|---|
| `spatial_data_points_container_id_idx` | B-tree | `container_id` | per-container scans |
| `spatial_data_point_position_idx` | GIST | `position` | bbox / KNN / DWithin |
| `spatial_data_points_metadata_idx` | GIN | `metadata` | `metadata @> '{…}'` filters |

**Query patterns observed** (in `SpatialDataPointRepository`):

- INSERT with `ST_MakePoint(x, y, z)` + JSONB casts.
- Bbox: `position &&& ST_3DMakeBox(…)` — uses GIST.
- DWithin: `ST_3DDWithin(position, …)` — uses GIST.
- KNN: `position <<->> ST_MakePoint(…) LIMIT k` — uses GIST (`<<->>`
  is the operator class's nearest-neighbor distance).
- Time filter: `time >= … AND time < …` — **unindexed**, falls back to
  per-partition seq scan.
- Measurements filter: `measurements @> '…'` — **unindexed**.

## 2. Where the cost is hiding

Three concrete weaknesses jumped out during inventory:

### 2.1 No time index — every temporal query seq-scans its partition

Every query that filters `WHERE time >= ? AND time < ?` does a
sequential scan inside its container_id partition. The GIST index on
`position` doesn't help — Postgres can't combine GIST + time predicate
into a single index probe.

**Fix.** Add a BRIN index on `time` per partition. BRIN is cheap
(tens of bytes per "zone" of pages), well-suited for
monotonically-arriving time-series data, and Postgres can combine BRIN
+ GIST via `BitmapAnd`. Effort: one migration adding `CREATE INDEX
… USING BRIN (time)` on the parent (Postgres 13+ propagates to
partitions).

### 2.2 HASH partitioning is the wrong shape for the workload

HASH-by-container is appropriate for "give me everything in this one
container" queries (which exist) but inverts the cost for spatial bbox
queries that span multiple containers — every bbox hit fans out across
all 100 partitions. The user-facing API is per-container today, so
this isn't actively biting, but the moment we add a "global heatmap"
or "all points near this coordinate" cross-container query, every read
touches 100 partitions.

**Fix options.**
- **Keep HASH, accept the cap.** Cheapest. Acceptable until a
  cross-container query lands.
- **Switch to RANGE partitioning on `time`** (e.g. monthly). Then
  container_id becomes a secondary predicate inside each partition.
  Pairs with a per-partition `(container_id, time)` index. Major
  rewrite — full data move via partition-swap. Effort: 1 week.
- **TimescaleDB hypertable.** TimescaleDB coexists with PostGIS (the
  spatial extension is independent); converting `spatial_data_points`
  to a hypertable gives time-range partitioning automatically plus
  retention policies, continuous aggregates, and 90%+ compression on
  cold partitions. Same SQL surface; clients see no change. **Strong
  recommendation if this fork is going to lean into time-series-shaped
  spatial workloads** (LUMEN-style hot-fire campaigns hit this hard).
  Effort: half a day for the conversion migration; the timescaledb
  service is already in docker-compose for the existing timeseries
  data, so the infra cost is zero.

### 2.3 No index on `measurements` JSONB despite predicate filters

Queries that filter measurements (e.g., `measurements @> '{"vib":
{"$gt": 10}}'`) seq-scan. The existing GIN index on `metadata` doesn't
cover `measurements`.

**Fix.** Two options:
- **Mirror the metadata GIN index** for `measurements`:
  `CREATE INDEX … USING GIN (measurements jsonb_path_ops)`. Covers
  the `@>` operator. ~5 min effort.
- **Partial expression indexes** for hot measurement keys, e.g.,
  `CREATE INDEX … ((measurements->>'vib')::float)` — faster for
  numeric comparisons but only helps the specific keys indexed.

## 3. Bigger swings (deferred, in rough effort order)

| Slice | What | When it pays off | Effort |
|---|---|---|---|
| **MVT tile endpoint** | `GET /v2/spatial-data-containers/{appId}/tiles/{z}/{x}/{y}.mvt` returning `ST_AsMVT()` output. Frontend uses MapLibre / Leaflet vector tile layer. | Whenever a map UI is built — 100x faster than fetching all points + clientside render. | Half a week (backend endpoint + frontend integration). |
| **Spatial clustering for low-zoom** | `ST_ClusterDBSCAN(position, eps, minPoints)` aggregation at low zoom levels, switches to raw points at high zoom. | Same as MVT — depends on the map UI. | Few days (server-side aggregation + cache). |
| **Geography vs geometry** | Switch `position` to `geography(POINT, 4326)` for great-circle math. | Globally-distributed datasets where planar math errors matter (>100km extent). | One migration + repository tweaks. Half a day. |
| **DuckDB-Spatial read replica** | Periodic Postgres → DuckDB sync, serve analytical reads from DuckDB. | When read-heavy analytics start blocking writes. Probably overkill today. | Week. |
| **Materialized continuous aggregates** | TimescaleDB feature — pre-computed downsamples (`AVG(measurements->>'temp')` per 5-min bucket per container) auto-refreshed. | Reporting dashboards / heatmaps that re-query the same downsample shape. | Few days; requires TimescaleDB conversion first. |

## 4. Recommendation

Land **#2.1 + #2.3** as one small migration (`V1.1.0__indexes.sql`):
adds BRIN on `time` and GIN on `measurements`. Zero migration risk
(both are additive index creations; concurrent-build via `CREATE
INDEX CONCURRENTLY`). Captures the two cheapest wins.

Then schedule **§2.2 TimescaleDB conversion** as a separate slice if
the spatial workload is genuinely time-series-shaped (which the LUMEN
showcase suggests). The infra is already there; the migration is
non-trivial but the long-term payoff (compression, retention,
continuous aggregates) compounds.

Defer the bigger swings (§3) until a UI consumer materializes — they're
all map-UI-driven and pre-building the backend for a UI that doesn't
exist yet is the wrong shape.

## 5. Open questions

- **Cross-container queries**: are they on the roadmap? If yes, HASH
  partitioning starts hurting. If no, defer §2.2.
- **Coordinate-system policy**: is `4326` (WGS84) the only CRS, or
  should the schema allow other CRSs? Geography conversion answer
  depends.
- **Measurement-shape evolution**: are `measurements` JSONB keys a
  fixed vocabulary or open-world? Affects whether expression indexes
  or generic GIN is right.
