---
stage: idea
last-stage-change: 2026-05-27
---

# 109 — MFFD scan-line join key across TPS stores

**Status.** Idea — design note, no implementation yet.
**Snapshot date.** 2026-05-27.
**Backlog row.** `aidocs/16 MFFD-JOIN-01`.
**Audience.** Contributors implementing PointCloud / SpatialContainer Phase 2;
operators ingesting MFFD AFP track data with multi-store payloads.

---

## 1. The problem

Each AFP track DataObject holds data in three or more containers:

| Container | Store | Row identity today |
|---|---|---|
| `FSDSet` timeseries | TimescaleDB | `(measurement, device, location, symbolicName, field)` 5-tuple + timestamp |
| `PointCloud` spatial | PostGIS (`profile` hypertable) | `container_id` + `profile_id` + timestamp |
| `ProfileSet.tif` file | Garage (S3) | opaque blob, no row-level identity |
| `BrushData` structured | MongoDB | arbitrary JSON document |

Within the FSD binary format, every row in every table is indexed by a **`Packet`**
value — a 0-based integer that increments monotonically across the acquisition
session. `Packet` is the shared row key that ties a timeseries sample, a spatial
profile, and a structured record to the same physical scan position on the part.

Today Shepard has no first-class way to express "row N in the TS container equals
row N in the SpatialContainer." A researcher or analysis pipeline wanting to
correlate a vibration spike (FSD timeseries) with the robot TCP position
(PointCloud) at the same instant must construct the join themselves — and the join
key is implicitly the timestamp, which is only correct if both stores recorded in
the same unit and epoch.

---

## 2. Design options

Three options emerged from the backlog analysis (`aidocs/16 MFFD-JOIN-01`):

### Option (a) — Store `Packet` as an additional TS channel

Add a synthetic timeseries channel named `Packet` (or `packet_index`) to the FSD
ingest path. Each row in TimescaleDB gains a companion `packet_index` column via the
multi-channel endpoint (TS-OPT2, issue #226). SpatialContainer rows similarly carry
a `packet_index` column.

**Pros:** Explicit, self-describing, survives any timestamp discrepancy.
**Cons:** Adds write amplification during ingest (one extra channel per row for every
FSD track); requires schema changes to `profile` hypertable (`packet_index` column);
increases storage ~5-10% per track.

**Phase:** 2 (post-PointCloud implementation).

### Option (b) — Timestamp alignment via `ChronoTimeInUs × 1000 ns`

The FSD binary format records `ChronoTimeInUs` (microseconds since Unix epoch) on
every row. The TimescaleDB `time` column stores nanoseconds since Unix epoch.
The conversion is: `ts_ns = chrono_time_us × 1000`.

If the spatial data (PointCloud profiles) uses the **same** FSD `ChronoTimeInUs`
source and the same conversion, then the TimescaleDB `time` column and the PostGIS
`time` column are already co-registered and the join key is the timestamp itself.

**Pros:** No schema changes; no new channels; zero storage overhead. Works today if
the ingest paths already apply the same conversion.
**Cons:** Silently wrong if either store rounds differently or uses a different clock
source. Requires an explicit verification step before trusting the join.

**Phase:** 1 (sufficient for initial analysis; no implementation needed beyond
confirming the conversion is applied consistently).

### Option (c) — `frame_index` column in SpatialContainer

Add a first-class `frame_index: BIGINT` column to the `profile` hypertable in
PostGIS. The FSD ingest path populates it from `Packet`. TimescaleDB rows already
have millisecond-precision `time`; a separate `frame_index` channel would give an
explicit integer join.

**Pros:** Explicit integer join without touching TS schema; SpatialContainer carries
its own row index independent of timestamp precision.
**Cons:** PostGIS schema change (new column on a hypertable with existing data);
SpatialContainer plugin must be updated; new Flyway migration needed; no benefit if
Option (b) proves reliable.

**Phase:** 2 (implement only if Option (b) proves insufficient after real-data testing).

---

## 3. Recommendation

**Phase 1: adopt Option (b) — timestamp alignment.**

The FSD importer already converts `ChronoTimeInUs` to nanoseconds when writing to
TimescaleDB. The PointCloud ingest (Phase 2) should apply the identical conversion.
If both stores record timestamps in the same unit (nanoseconds since Unix epoch), the
join is `fsd_timeseries.time = spatial_profile.time` with no new schema, no new
channels, and no write amplification.

Option (b) defers all schema complexity to Phase 2 and makes it conditional:
implement Option (a) or (c) only if timestamp alignment fails the verification step
below.

---

## 4. Verification step for Option (b)

Before trusting timestamp-based joins in production, the following must be confirmed
across **all four stores** for a representative AFP track DataObject:

1. **FSD timeseries (TimescaleDB):** confirm `time` column values equal
   `ChronoTimeInUs × 1000` for at least one spot-check row.

   ```sql
   SELECT time, measurement, value
   FROM timeseries_data
   WHERE container_id = '<fsd_container_id>'
   ORDER BY time
   LIMIT 5;
   ```

2. **PointCloud (PostGIS `profile` hypertable):** confirm `time` column uses the
   same epoch and unit (nanoseconds since Unix epoch) for the corresponding track.

   ```sql
   SELECT time, container_id, profile_id
   FROM shepard_spatial.profile
   WHERE container_id = '<spatial_container_id>'
   ORDER BY time
   LIMIT 5;
   ```

   Compare the first row's `time` value to the FSD `ChronoTimeInUs × 1000` for the
   matching Packet=0 row. They must agree within 1 µs (1000 ns) to be co-registered.

3. **ProfileSet.tif (Garage):** TIFF files have no row-level timestamps accessible
   without reading the file. The join from TIFF to other stores requires a layer-index
   mapping derived from the filename convention (e.g. `profile_<packet>.tif`).
   This is out of scope for Phase 1.

4. **BrushData (MongoDB):** structured records carry a `timestamp` field in the
   Shepard document model. Confirm the field uses nanoseconds since Unix epoch (not
   milliseconds). If MongoDB records use milliseconds, multiply by 1,000,000 before
   joining.

Document the outcome of this verification in a `aidocs/data/` follow-up note when
Phase 2 PointCloud ingest lands. If any store uses a different unit or epoch, escalate
to Option (a) or (c).

---

## 5. Cross-references

- `aidocs/16 MFFD-JOIN-01` — originating backlog row
- `aidocs/platform/87-timeseries-appid-migration.md` — 5-tuple → `shepardId` migration
  (addresses channel identity; the join-key problem is orthogonal but co-located)
- `aidocs/data/90-spatiotemporal-plugin-design.md` — SpatialContainer / PostGIS
  `profile` hypertable schema (the target of Option (c))
- `examples/mffd-showcase/seed.py` — synthetic AFP demo dataset (reference for the
  four-store structure)
