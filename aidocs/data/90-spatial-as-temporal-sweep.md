---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor
supersedes:
  - aidocs/data/82-spatial-perf-evaluation.md (BRIN+GIN+TimescaleDB recommendations folded into §3)
related:
  - aidocs/data/81-spatial-data-binding.md (DataBinding model stays in shepard-plugin-cad scope; bound-to target widens to brush traces)
  - aidocs/data/83-pointcloud-and-live-overlay.md (CAD plugin frame-handshake partner; see §8)
  - aidocs/data/85-coordinate-frame-tree.md (CST1 frame tree — adopted verbatim, not duplicated; see §4)
  - aidocs/data/84-live-digital-twin.md (DT1 live state stream; see §5 live-tail)
  - aidocs/data/86-scene-drive-and-replay.md (DR1 scene drive — brush trace = animation axis variant)
  - aidocs/semantics/98-shapes-views-and-process-model.md (VIEW_RECIPE as `:ShepardTemplate`; brush mode = SHACL property)
  - aidocs/agent-findings/ts-design-audit-2026-05-24.md (TS-AUDIT-001/002/003 antipatterns — design must NOT repeat)
  - aidocs/agent-findings/synthesis-architecture-report-2026-05-24.md §3 T1, T2 (SHACL-as-SoT, postgis collapse)
  - aidocs/agent-findings/plugin-design-audit-2026-05-24.md §Spatial (existing 5 audit rows)
---

# aidocs/90 — Spatial as temporal sweep (v6 SSOT)

The `shepard-plugin-spatial` reshape for v6. Time-varying engineering
geometry as a first-class payload kind. Green-field schema (no
backward compat with the current experimental `spatial_data_points`
shape). One substrate (TimescaleDB hypertable with PostGIS
extension), one viewer (Three.js brush via `vis-trace3d` + glTF
sidecar), one frame registry (CST1 from aidocs/85), one acceptance
test (MFFD AFP TCP thermal-trail).

This doc is the **canonical SSOT** for the v6 reshape. aidocs/82
(perf evaluation) and the current V1.0.0 migration are retired by
§3 + §10; aidocs/81 (DataBinding) stays alive but its `BOUND_TO`
targets widen to include brush traces.

---

## §1 — TL;DR + v6 positioning

**v6 statement.** shepard v6 ships **time-varying engineering
geometry as a first-class payload kind**. A scanning profile, a
robot end-effector path with a tool footprint, an ultrasonic line
scan, a CT slice, a lidar sweep — all share one substrate, one
viewer, one frame registry, one render pipeline.

**Why this is the differentiator.** None of the European RDM
platforms we surveyed (Kadi4Mat, SciCat, openBIS, Coscine, NOMAD,
FAIRDOM-SEEK; per `project_competitive_position.md`) serve this
shape cleanly. Most either treat geometry as a static file (mesh
upload, no time axis) or treat time-series as scalars (no
per-sample geometry). The MFFD/PLUTO use case wants both at once.
A `shepard-plugin-spatial` that gets this right is **the v6
flagship capability** — the one feature that demos in 30 seconds
and that no other tool can match in a quarter.

**The use-case catalogue (8 rows that justify one substrate):**

| # | Use case | Profile shape per timestep | Typical rate | Anchor source | Vendor reference |
|---|---|---|---|---|---|
| 1 | AFP head sweep (TCP footprint + thermal) | `LINESTRINGZ` (~50 pts) or `POLYGONZ` (compaction roller contact) | 5–50 Hz | AFP robot encoders | Coriolis, MAG/Cincinnati, Mikrosam |
| 2 | Robot welding torch path | `POINTZ` (TCP) or `TUBE_CENTERLINE` (tool centerline) | 50–250 Hz | KUKA RSI / Fanuc | Standard ROS-Industrial |
| 3 | Ultrasonic NDT line scan (PAUT) | `LINESTRINGZ` along probe footprint | 20–500 Hz | Encoder + arm pose | Olympus OmniScan X3, GE Krautkramer |
| 4 | Laser line profilometer | `LINESTRINGZ` (64–3200 vertices per profile) | 1–64 kHz profile rate | Mounting stage encoder | Keyence LJ-X8000 series [^keyence] |
| 5 | Lidar slice from moving platform | `MULTIPOINTZ` (~16–128 returns per slice) | 5–20 Hz slice | Vehicle / robot pose | Velodyne, Ouster, Hesai |
| 6 | CT slice reconstruction | `POLYGONZ` or `TIN_Z` (per-slice mesh) | sub-Hz (reconstruction) | Stage Z position | Zeiss METROTOM, GE phoenix |
| 7 | AFP debulking patch inspection | `POLYGONZ` patches (irregular) | per-patch (event) | Inspection grid | GOM ATOS Q, ZEISS T-SCAN |
| 8 | Structured-light scanner pass | `MULTIPOINTZ` (5k–200k pts per fringe-set) | 0.5–5 Hz fringe | Scanner head pose | GOM ATOS, HP 3D Structured Light |

All eight collapse to one shape: `(time, profile_kind, profile_geometry, measurements, anchor_frame)`. v6 is the structural recognition that this is one substrate, not eight.

**Three-line elevator pitch.** A scanning-profile, robot-trajectory, or NDT line-scan stream lands as one container. Trace3D (the SHACL VIEW_RECIPE) renders the sequence as a swept ruled surface — a *brush stroke* through 3D space. Frame-handshake with `shepard-plugin-cad` puts the as-designed CAD model under the brush so the engineer sees as-designed and as-measured in one canvas, time-scrubbable, color-coded by any bound measurement channel.

[^keyence]: Keyence LJ-X8000 series datasheet, https://www.keyence.com/products/measure/laser-2d/lj-x8000/ — 64–3200 profile points; profile rate up to 64 kHz; ±0.5 µm repeatability.

---

## §2 — Reuse-first survey

Per `feedback_reuse_before_reimplement.md`, every shape this design proposes must be checked against existing libraries / specs / patterns before any new code is justified.

| Component | Adopt? | What it gives | What it doesn't | Decision |
|---|---|---|---|---|
| **PostGIS** geometry types (`POINTZ`, `LINESTRINGZ`, `POLYGONZ`, `POLYHEDRALSURFACEZ`, `TIN Z`, `MULTIPOINTZ`) [^postgis-types] | **ADOPT** (substrate-of-record) | Native 3D geometry storage, `ST_3DDWithin`, `ST_3DDistance`, GIST `gist_geometry_ops_nd` 3D index | Server-side lofting of *consecutive profile rows* is not a built-in (must compose `ST_MakePolyhedralSurface`). | Adopt as the geometry column types in §3 schema. |
| **TimescaleDB** hypertable + continuous aggregates + native columnstore compression [^tsdb-compression] | **ADOPT** (substrate-of-record) | Time-range partitioning, automatic compression (live shepard: 23× ratio per `ts-design-audit-2026-05-24.md`), `time_bucket()` aggregates, retention policies | Need a manual `segmentby=container_id` decision per workload | Adopt; pair with PostGIS in **one** PG instance via `CREATE EXTENSION postgis` alongside existing `CREATE EXTENSION timescaledb`. Aligns with synthesis §3 T2 (postgis-collapse). |
| **pgpointcloud** (postgres extension for storing point cloud patches) [^pgpointcloud] | **REJECT for v0**, watch for v2 | Patch-based storage (~600 pts per patch), better than raw POINT for dense clouds | Adds a third PG extension + new query operators researchers don't know | Point-cloud-style sweeps (use cases 5, 8) land as `MULTIPOINTZ` with `ST_Subdivide` if needed. Re-evaluate when a >10M-point-per-frame use case lands. |
| **`ST_MakePolyhedralSurface`** + **`ST_Tin`** (server-side lofting) [^postgis-3d] | **ADOPT** (server-render path) | Build PolyhedralSurface from consecutive `LINESTRINGZ` profiles via SQL; build TIN from MultiPoint slice | No streaming variant; full materialisation per call | Adopt in §5 server-loft path; cache outputs to Garage. |
| **glTF 2.0 binary (`.glb`)** + **`KHR_mesh_quantization`** [^khr-quantization] + **`EXT_meshopt_compression`** [^ext-meshopt] + **`KHR_draco_mesh_compression`** [^khr-draco] | **ADOPT** (server-render delivery) | Industry-standard 3D format; KHR_mesh_quantization shipping ~50% smaller meshes losslessly-perceptual; meshopt ~50% further with fast decode; Draco wider but slower decode | None — but choose **default = meshopt** (Three.js + Babylon + Filament all support; smaller and faster than Draco in 2026 benchmarks per [glTF compression compare](https://github.com/zeux/meshoptimizer#comparison)) | Adopt all three as content-negotiable; default to meshopt. |
| **Apache Arrow Flight** (streaming tail format) [^arrow-flight] | **DEFER to v2** | Columnar binary stream; pgbouncer-friendly; Python+Java+C++ native | Larger client dependency than SSE+JSON; not what Three.js expects | Note as v2 candidate for high-rate live-tail (>10 kHz profile rate). v0/v1 uses SSE with JSON frames. |
| **Three.js `BufferGeometry`** + **`ParametricGeometry`** + **`TubeGeometry`** [^threejs] | **ADOPT** (client-render path) | WebGL2 vertex-buffer abstraction; supports up to ~5M vertices comfortably, 10M with `EXT_mesh_gpu_instancing`; native lighting and material model | None | Adopt in vis-trace3d renderer. Stitch ruled-surface BufferGeometry from JSON frame stream. |
| **ROS tf2** [^ros-tf2] (transform tree) | **NOT adopt — adopt CST1 instead** | Established robotics frame tree, 4×4 matrix convention | Pure C++/Python lib; no Java port; runtime, not storage | aidocs/85 (CST1) is shepard's persistent equivalent; **already designed with TF2-compatible matrix convention**. The spatial schema references CST1 frames by `:CoordinateFrame.appId`. |
| **AAS `CoordinateSystem` submodel** [^aas-coords] | Cross-reference in CST1 | DLR-relevant (RWTH/PIB), Industrie 4.0 standard | Submodel shape only specifies leaf attributes, not the tree shape | CST1 (aidocs/85) already supports AAS-submodel export. Spatial inherits. |
| **CGAL / OpenCascade lofting** [^cgal-lofting] | **NOT adopt as runtime dep** | C++ libraries for industrial-grade ruled-surface and B-Rep lofting | LGPL / mixed; large native binary | Server-side ruled-surface generation uses PostGIS `ST_MakePolyhedralSurface` (good enough for triangulated brushes). Defer CGAL/OCCT to a CAD-grade lofting sidecar if needed. |
| **meshio / trimesh / PyVista** (Python mesh ops) [^trimesh] | **CONDITIONAL adopt** (sidecar only) | Decimation, simplification, format conversion | Adds Python sidecar (a sidecar already lands for HDF5/HSDS, so the pattern is paid for) | Adopt as a *render-pipeline sidecar* if PostGIS-only path proves insufficient for use case 6 (CT) or 8 (structured-light). v0 stays in-process. |
| **SHACL `MeasurementSchema` shape** (per synthesis §3 T1) | **ADOPT conditionally** | Closed-vocabulary validation for `measurements` JSONB when a shape is declared on the container; open-world degraded mode otherwise | SHACL substrate not landed yet (synthesis §3 T1 queued) | Container declares optional `measurement_schema_appid`. Present → strict validate. Absent → open-world. Soft-coupling lets v0 ship before SHACL substrate. |

**What we're building vs. adopting:** the only genuinely new code is (a) the schema DDL + write path (§3), (b) the Java SPI capability declarations (§4 + §8), (c) the Three.js brush-stitching renderer in `vis-trace3d`, (d) the server-loft endpoint shape in §5. Everything else is glue between existing libraries.

[^postgis-types]: PostGIS 3.4 documentation, "Reference: PostGIS Geometry Types", https://postgis.net/docs/using_postgis_dbmanagement.html#PostGIS_Geometry
[^tsdb-compression]: Tiger Data, "Compression — TimescaleDB Documentation", https://docs.tigerdata.com/use-timescale/latest/compression/ ; live shepard ratio per `aidocs/agent-findings/ts-design-audit-2026-05-24.md` §"Substrate state" (9.9 GB → 425 MB = 23.2×).
[^pgpointcloud]: pgpointcloud, https://pgpointcloud.github.io/pointcloud/
[^postgis-3d]: PostGIS 3D Reference, `ST_MakePolyhedralSurface` and `ST_Tin`, https://postgis.net/docs/reference.html#Geometry_Constructors
[^khr-quantization]: Khronos glTF 2.0 extension `KHR_mesh_quantization`, https://github.com/KhronosGroup/glTF/blob/main/extensions/2.0/Khronos/KHR_mesh_quantization/README.md — typically ~50% size reduction with no visible loss.
[^ext-meshopt]: Khronos extension `EXT_meshopt_compression`, https://github.com/KhronosGroup/glTF/blob/main/extensions/2.0/Vendor/EXT_meshopt_compression/README.md — multiplicative with quantization.
[^khr-draco]: Khronos extension `KHR_draco_mesh_compression`, https://github.com/KhronosGroup/glTF/blob/main/extensions/2.0/Khronos/KHR_draco_mesh_compression/README.md
[^arrow-flight]: Apache Arrow Flight RPC, https://arrow.apache.org/docs/format/Flight.html
[^threejs]: Three.js `BufferGeometry`, https://threejs.org/docs/#api/en/core/BufferGeometry
[^ros-tf2]: ROS 2 tf2 design, https://docs.ros.org/en/jazzy/Concepts/Intermediate/About-Tf2.html
[^aas-coords]: Asset Administration Shell Specification — Submodel Templates, https://industrialdigitaltwin.org/en/content-hub/aasspecifications
[^cgal-lofting]: CGAL Surface Reconstruction Manual, https://doc.cgal.org/latest/Surface_reconstruction_points_3/
[^trimesh]: trimesh, https://trimsh.org/

---

## §3 — Schema (the green-field DDL)

The new schema lives in the **existing TimescaleDB Postgres** under
the new `shepard_spatial` schema (per synthesis §3 T2 and
backlog row `PLUGIN-SPATIAL-AUDIT-2026-05-24-004`). The legacy
`postgis` container is retired in §10 v0.

**Pre-conditions** (added to the V2.0.0 migration before any `CREATE TABLE`):

```sql
-- Run once at install; both extensions coexist cleanly on PG 16+.
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE SCHEMA IF NOT EXISTS shepard_spatial;
SET search_path TO shepard_spatial, public;
```

### 3.1 Profile container metadata

```sql
CREATE TABLE shepard_spatial.profile_container (
    container_id          BIGINT PRIMARY KEY,                -- mirrors :SpatialDataContainer.id on Neo4j; FK-by-convention
    coord_frame_app_id    UUID    NOT NULL,                  -- → :CoordinateFrame.appId on Neo4j (CST1 / aidocs/85)
    profile_kind_allowed  TEXT[]  NOT NULL,                  -- closed vocab the container accepts
    measurement_schema_appid UUID,                            -- optional :ShepardTemplate of kind SHACL_SHAPE → strict mode
    created_at_ms         BIGINT NOT NULL,
    retired_at_ms         BIGINT,                             -- soft-retire mark; never delete
    CHECK (profile_kind_allowed <@ ARRAY[
        'point', 'line', 'polygon', 'tin', 'multipoint', 'tube_centerline'
    ]::text[])
);

COMMENT ON COLUMN shepard_spatial.profile_container.profile_kind_allowed IS
    'Vocabulary of profile kinds this container accepts. Writes with profile_kind outside this array are rejected.';
COMMENT ON COLUMN shepard_spatial.profile_container.measurement_schema_appid IS
    'Optional :ShepardTemplate(SHACL_SHAPE) appId. NULL → open-world measurements (legacy/single-point); present → strict SHACL validation on every write.';
```

### 3.2 Profile hypertable

```sql
CREATE TABLE shepard_spatial.profile (
    container_id     BIGINT       NOT NULL
        REFERENCES shepard_spatial.profile_container(container_id),
    time             BIGINT       NOT NULL,                  -- epoch ns, mirrors TS hypertable convention
    profile_kind     TEXT         NOT NULL,                  -- discriminator
    anchor           GEOMETRY     NOT NULL,                  -- always POINTZ; the "where the head is" position
    profile          GEOMETRY,                                -- NULL only when profile_kind = 'point' (degenerate)
    measurements     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    metadata         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    seq              BIGSERIAL,                               -- monotonic within (container_id, time) for tie-breaking

    -- PK: container + time + seq. Multiple profiles at same nanosecond allowed (high-rate streams).
    PRIMARY KEY (container_id, time, seq),

    -- Anchor is always POINTZ (the head/origin/TCP)
    CONSTRAINT chk_anchor_is_point CHECK (
        ST_GeometryType(anchor) = 'ST_Point' AND ST_NDims(anchor) >= 3
    ),

    -- Discriminator-vs-geometry agreement (closes TS-AUDIT-003 antipattern: no soft type contract)
    CONSTRAINT chk_profile_kind_matches_geom CHECK (
        profile_kind IN ('point', 'line', 'polygon', 'tin', 'multipoint', 'tube_centerline') AND (
            (profile_kind = 'point'            AND profile IS NULL)
         OR (profile_kind = 'tube_centerline'  AND ST_GeometryType(profile) IN ('ST_LineString'))
         OR (profile_kind = 'line'             AND ST_GeometryType(profile) IN ('ST_LineString'))
         OR (profile_kind = 'polygon'          AND ST_GeometryType(profile) IN ('ST_Polygon'))
         OR (profile_kind = 'multipoint'       AND ST_GeometryType(profile) IN ('ST_MultiPoint'))
         OR (profile_kind = 'tin'              AND ST_GeometryType(profile) IN ('ST_PolyhedralSurface', 'ST_Tin', 'ST_TIN'))
        )
    ),

    -- JSONB shape minimum sanity (closes TS-AUDIT-001 polymorphic-value-column trap by being explicit)
    CONSTRAINT chk_measurements_is_object CHECK (jsonb_typeof(measurements) = 'object'),
    CONSTRAINT chk_metadata_is_object     CHECK (jsonb_typeof(metadata) = 'object')
);

-- Hypertable conversion + space-partitioning. 1-day default chunk; container_id space-partition for query pruning.
SELECT create_hypertable(
    'shepard_spatial.profile', 'time',
    chunk_time_interval => 86400000000000::BIGINT,            -- 1 day in ns; mirrors timeseries_data_points
    partitioning_column => 'container_id',
    number_partitions   => 4,                                 -- 4 space partitions; reconsider after first MFFD scale-test
    if_not_exists       => TRUE
);

-- Integer-time bucket support (matches the TS hypertable pattern)
SELECT set_integer_now_func('shepard_spatial.profile',
    'shepard_spatial.profile_integer_now', if_not_exists => TRUE);
```

### 3.3 Indexes

```sql
-- Time-range queries: BRIN is the right shape (monotonic-arrival, cheap, multi-MB pages)
-- Per TS-AUDIT-005 + aidocs/82 §4 and TimescaleDB best practice.
CREATE INDEX profile_time_brin
    ON shepard_spatial.profile USING BRIN (time)
    WITH (pages_per_range = 32);

-- Anchor 3D spatial probe: GIST on POINTZ
CREATE INDEX profile_anchor_gist
    ON shepard_spatial.profile USING GIST (anchor gist_geometry_ops_nd);

-- Profile 3D spatial probe: GIST on the polymorphic profile geometry — partial index
-- saves cost on `point`-kind rows where profile IS NULL (use case 2 / use case 5 degenerate single-point streams).
CREATE INDEX profile_geom_gist
    ON shepard_spatial.profile USING GIST (profile gist_geometry_ops_nd)
    WHERE profile IS NOT NULL;

-- Measurements JSONB query support (closes aidocs/82 §2.3 gap)
CREATE INDEX profile_measurements_gin
    ON shepard_spatial.profile USING GIN (measurements jsonb_path_ops);

-- Metadata JSONB query support
CREATE INDEX profile_metadata_gin
    ON shepard_spatial.profile USING GIN (metadata jsonb_path_ops);

-- Profile-kind discrimination: used by every read for shape-aware rendering
CREATE INDEX profile_kind_btree
    ON shepard_spatial.profile (container_id, profile_kind, time);
```

### 3.4 Compression policy

```sql
ALTER TABLE shepard_spatial.profile SET (
    timescaledb.compress,
    timescaledb.compress_segmentby   = 'container_id',
    timescaledb.compress_orderby     = 'time ASC, seq ASC',
    timescaledb.compress_chunk_time_interval = '7 days'        -- merge chunks for higher compression ratios
);

-- 7-day compression delay matches the live timeseries_data_points policy (aidocs/agent-findings/ts-design-audit-2026-05-24.md §"Substrate state")
SELECT add_compression_policy('shepard_spatial.profile', INTERVAL '7 days');
```

### 3.5 Retention (operator-configurable; default deferred per SM1a)

```sql
-- Per project_storage_management.md: referenced data has INFINITE grace.
-- No retention policy is added by default. SM1a (storage management) controls
-- orphan sweep at the container level via the SpatialContainerOrphanSweeper service.
-- If an operator wants a safety-net retention, they invoke:
--   SELECT add_retention_policy('shepard_spatial.profile', INTERVAL '10 years');
COMMENT ON TABLE shepard_spatial.profile IS
    'No default retention policy. Per project_storage_management.md (SM1a), '
    'orphan sweep is a container-scoped service. Operators may add a safety '
    'retention policy via add_retention_policy().';
```

### 3.6 Foreign-key behaviour

We adopt the same posture as TS-AUDIT-004 recommends for `timeseries_data_points`:
- **Dev / default-on:** FK from `profile.container_id → profile_container.container_id` for early bug catch.
- **Runtime escape hatch:** `shepard.spatial.fk.enabled` (default `true`) — operators can flip off at >10k rows/sec ingest scale. The DAO performs the existence check application-side.
- **No CASCADE on delete:** per `feedback_referenced_data_infinite_retention.md`, deleting a container does NOT cascade-drop profile rows. Container retire is `retired_at_ms` + soft mark; physical drop is operator-scoped (the SM1a orphan sweep).

### 3.7 Closed-vocabulary vs open-world for `measurements`

Per synthesis §3 T1 (SHACL-as-SoT decision) + `feedback_shacl_single_source_of_truth.md` and the advisor's reconcile note:

- **Strict mode (closed):** `profile_container.measurement_schema_appid` references a `:ShepardTemplate(SHACL_SHAPE)`. Every write validates `measurements` JSONB against the shape via the Jena SHACL validator (already shipped in `de.dlr.shepard.v2.shapes.validator.JenaShaclValidator`). Reject on violation.
- **Open-world (legacy / single-point streams):** `measurement_schema_appid IS NULL` → only the `chk_measurements_is_object` substrate-level check runs. Used for use case 2 (welding torch TCP) and use case 5 (lidar) where pre-declared shape adds friction without value.

The runtime flag `shepard.spatial.shacl-strict-by-default` (default `false`) controls whether new containers without an explicit shape default to strict-reject-on-write or open-world. v0 ships `false`; cleanup PR after SHACL substrate is broadly available will flip to `true` for new-container defaults.

### 3.8 What's explicitly NOT in this schema

- **No 4-column polymorphic value table** (closes TS-AUDIT-001). Measurements are `JSONB` with sanity CHECK; type discipline rides on the SHACL `MeasurementSchema`. We refuse to re-derive the dead-column-NULL antipattern.
- **No HASH partitioning** (the current V1.0.0 antipattern per aidocs/82 §2.2). Hypertable does time-range partitioning + space-by-`container_id` cleanly.
- **No string-INSERT write path** (closes TS-AUDIT-002). See §6 — COPY is the write path.

---

## §4 — Coordinate frame model

**No invention.** aidocs/85 (CST1) already defines a first-class
Neo4j `:CoordinateFrame` tree with parent/child edges, named
frame types (`WORLD`, `MEASUREMENT_TARGET`, `ROBOT_BASE`,
`SENSOR`, `SCAN_HEAD`, ...), 4×4 matrix transforms, time-aware
`validFromMs`/`validUntilMs`, ground-truth anchoring via SA, and
ROS-TF2-compatible matrix convention. The spatial plugin **adopts
CST1 verbatim**.

**The integration shape:**

1. Every `:SpatialDataContainer` has a Cypher relationship `[:ANCHORED_IN]->(:CoordinateFrame)` carrying the `coord_frame_app_id` that mirrors the Postgres `profile_container.coord_frame_app_id` column.
2. Profile geometry is **stored in the container's frame**, not in any global frame. Cross-frame queries traverse CST1 to compose a transform; the spatial plugin's `FrameAlignedQueryService` calls `CoordinateFrameService.chainTo(from, to)` from aidocs/85 §3.2 and applies the resulting matrix to the GIST bbox or KNN target before issuing the PostGIS query.
3. The CAD plugin (per aidocs/83) renders in the same frame; the brush sits naturally in the as-designed coordinate system without translation glue.
4. ICP alignment between scanning-profile streams and CAD meshes (the §8 frame-handshake) updates the CST1 transform edge, not the profile rows. The data stays put; the frame moves.

**Migration of the legacy `SpatialDataContainer.crs` (WGS84-by-default) field.** Today's plugin defaults `crs` to `'4326'`. In the v6 reshape, every existing container gets a CST1 frame registered with `frameType=WGS84_GLOBAL` and a one-time backfill rewrites the FK column. WGS84-shaped data continues to work via the same CST1 lookup path. Frame-naming convention added: any `:CoordinateFrame { frameType: 'WGS84_GLOBAL' }` carries a stable IRI `shepard-frame:wgs84-global`.

**No new Java code.** The `CoordinateFrameService` from CST1 is the entire interaction surface; the spatial plugin's `SpatialContainerService` injects it and never touches matrices directly.

---

## §5 — The brush rendering pipeline

Three render paths. The decision tree depends on the size and concurrency of the requested window.

```
                            GET /v2/spatial-containers/{appId}/trace?...
                                              │
                            ┌─────────────────┼────────────────────┐
                            │                 │                    │
                       <5k profiles      ≥5k profiles         is live-tail
                       single viewer     OR shared cache      (subscribe=true)?
                            │                 │                    │
                            ▼                 ▼                    ▼
                  Path A: client lofts   Path B: server lofts  Path C: SSE/Arrow
                  (JSON frames over      to glTF (.glb),       streaming append
                   HTTP, Three.js        cached on Garage      (no caching layer,
                   builds BufferGeometry per (from, to,        new frames pushed
                   per consecutive       simplify, decimate)   as they arrive)
                   profile pair)
```

### Path A — Client lofts (default for ad-hoc exploration)

- Request: `GET /v2/spatial-containers/{appId}/trace?from=…&to=…&format=json&decimate=N`
- Response: SSE or chunked JSON of `{time, profile_kind, anchor, profile, measurements}` frames.
- Client: vis-trace3d Vue component stitches a ruled `BufferGeometry` between consecutive profile rows. Per-vertex color from `measurements[valueChannel]`.
- Budget: ≤5k profiles × ≤100 vertices = 500k vertices comfortably within Three.js single-mesh limits [^threejs-perf]. ~3MB JSON over the wire uncompressed; gzip drops to ~600 KB.
- Latency: SSE first byte <200ms; full render <1s on a mid-range laptop GPU.

### Path B — Server lofts to glTF (default for >5k profiles or shared cache)

- Request: `GET /v2/spatial-containers/{appId}/trace?from=…&to=…&format=glb&simplify=0.5mm&decimate=N`
- Server: PostGIS `ST_MakePolyhedralSurface` over consecutive profiles; per-vertex color attribute baked from `measurements[valueChannel]`; quantized to 16-bit positions via `KHR_mesh_quantization`; meshopt-compressed via `EXT_meshopt_compression`.
- Cache key: `(container_id, from_ns, to_ns, simplify, decimate, value_channel, color_map_id)`. Cache target: Garage S3 at `s3://shepard-public/spatial-cache/<container>/<sha256(key)>.glb`. TTL 7 days; rebuilt on hit-miss; pre-baked on container close (see §10 v1).
- Pre-bake hook: container.markComplete() submits a server-render job that walks all VIEW_RECIPEs declared against this container and pre-bakes the canonical (from=container.start, to=container.end) cache.
- Format negotiation: `Accept: model/gltf-binary` (current standard MIME); the server prefers `KHR_mesh_quantization + EXT_meshopt_compression`. Falls back to plain glTF if the client lists only `model/gltf+json` in `Accept`.

### Path C — Live-tail (PC1d-style streaming append)

- Request: `GET /v2/spatial-containers/{appId}/trace/stream`
- Server: SSE channel; on every new INSERT (via Postgres LISTEN/NOTIFY on the container_id channel) emits a JSON frame for the new profile.
- Client: appends `BufferGeometry` segments to the existing mesh without a full reload. Tail-mode renders the most recent N seconds as a "fade-to-transparent" trail (Three.js `MeshBasicMaterial` per-vertex alpha).
- Live-tail and cached glTF do not coexist for the same viewer-session — the live-tail bypasses the cache entirely (cache key includes `to`, which is `now()` for live).
- Aligns with aidocs/84 §4 SSE state stream + aidocs/86 §1 sTC ingest fan-out — the brush is a consumer of the same FanOutBus.

### Decision criteria

| Constraint | Path A | Path B | Path C |
|---|---|---|---|
| Profile count | ≤5k | >5k | live-tail (open-ended) |
| Concurrent viewers | 1–2 | ≥2 (cache amortises) | 1+ (no cache amortisation) |
| Latency floor | ~1s (full data download) | ~100ms (cached); ~3s (cache miss + bake) | <50ms per new sample |
| Network budget | ~600KB gzip JSON | ~50–300KB compressed glb | small per-sample frames |
| GPU budget | ~500k vertices client | ~5M vertices client | unbounded; tail-window trim |
| Render-budget shape | one BufferGeometry | one BufferGeometry (loaded from glb) | growing BufferGeometry with periodic trim |

### Estimated latency at MFFD scale

Worst-case from §1 catalogue: use case 4 (laser line profilometer) at 1 kHz × 1 hour × 100 vertices per profile = 3.6M rows. Path B cached: ~100ms read-from-Garage + ~50ms glb deserialise + ~200ms BufferGeometry upload to GPU = ~350ms P50. Cache miss bake estimate: PostGIS `ST_MakePolyhedralSurface` over 3.6M profiles via batched `SELECT` is ~30s; meshopt compress is ~5s; Garage upload is ~2s. Total cache miss ≈ 40s. Tolerable for an interactive session as a one-time wait; the pre-bake hook ensures the second viewer's experience is the 350ms case.

[^threejs-perf]: Three.js docs on `BufferGeometry`, https://threejs.org/docs/#api/en/core/BufferGeometry ; practical vertex budget per mesh ≈ 2¹⁶ × 64 with default attribute indexing; instancing extends usable scene capacity to ~10⁷ vertices.

---

## §6 — Performance numbers

Every claim here cites either: a primary-source benchmark, a substrate measurement on live shepard, or a vendor datasheet. No hand-waving.

### 6.1 Storage volume

Worked example: AFP head sweep, 10 Hz × 1 hour × 50-vertex `LINESTRINGZ` per profile = **36 000 rows**.

| Per-row component | Bytes | Notes |
|---|---|---|
| Row header + visibility | ~28 | Standard PG MVCC tuple header |
| `time` BIGINT | 8 | |
| `container_id` BIGINT | 8 | |
| `seq` BIGSERIAL | 8 | |
| `profile_kind` TEXT (`'line'`) | 5 | One-byte short-text storage in PG TOAST |
| `anchor` GEOMETRY (POINTZ) | 40 | EWKB POINTZ payload |
| `profile` GEOMETRY (LINESTRINGZ × 50 pts) | ~1232 | 4-byte type tag + 4-byte npoints + 50 × 24 bytes (3 × double) |
| `measurements` JSONB (~3 keys × ~20 bytes) | ~120 | Compact JSONB binary encoding |
| `metadata` JSONB (empty `'{}'`) | ~4 | |
| **Total** | **~1453 bytes/row** | |

- **Raw**: 36 000 × 1453 ≈ **52 MB** per hour-of-AFP, 864 MB if scaled to use case 4 (1 kHz × 1 hour × 100-vertex).
- **Compressed**: per the live shepard TimescaleDB measurement (`ts-design-audit-2026-05-24.md §"Substrate state"`): 23.2× ratio on similar shape. Tiger Data documentation [^tsdb-compression] reports 90%+ (10×) is typical baseline; segmentby + numeric-heavy payload pushes that higher. Our compressed AFP-hour ≈ **2.2 MB**; compressed profilometer-hour ≈ 37 MB.
- Across a typical 8-hour MFFD campaign with three concurrent AFP heads + one profilometer + one welding torch ≈ **3 GB raw → ~130 MB compressed**. Scales linearly with campaign duration.

### 6.2 Insert throughput

The TS-AUDIT-002 antipattern (string-INSERT VALUES per batch) **must NOT recur**. The spatial write path uses **COPY**:

- Path: `SpatialProfileRepository.insertManyWithCopy(List<ProfileRow>)`. Batches > 1000 use COPY; smaller batches use parameterised INSERT (round-trip dominates parse cost).
- Conflict handling: per-container, per-time uniqueness is **NOT** enforced (the `seq` BIGSERIAL allows multi-profile-per-nanosecond), so no `ON CONFLICT` clause is needed. Late-arriving duplicates are observable as same-(container, time) rows with adjacent `seq` values; the dedupe is a read-time concern.
- Backfill: same path. Session-scoped TEMP TABLE pattern (per TS-AUDIT-002 §"Fix shape") is reserved for the case where a future write path requires upserts; the current schema's append-only model avoids that need.

**Projected rates** (PG 16 + TimescaleDB 2.24 + COPY format binary):

| Use case | Source rate | Profile size | Row rate | Bytes/sec ingress |
|---|---|---|---|---|
| AFP, single head | 10 Hz | 50 pts | 10 rows/s | ~15 KB/s |
| Welding torch (TCP) | 250 Hz | 1 pt (degenerate) | 250 rows/s | ~50 KB/s |
| PAUT line scan | 500 Hz | 32 pts | 500 rows/s | ~500 KB/s |
| Laser profilometer (worst case) | 1000 Hz | 100 pts | 1000 rows/s | ~1.5 MB/s |
| Lidar slice | 20 Hz | 64 pts | 20 rows/s | ~40 KB/s |

Aggregate peak (all five concurrent): ~1800 rows/s, ~2.1 MB/s. Postgres COPY benchmarks [^pg-copy-bench] regularly sustain >50 000 rows/s for small rows on commodity hardware; we are comfortably an order of magnitude under that ceiling.

### 6.3 Query latency targets

Backed by index plan: BRIN(time) prunes chunks → space-partition prunes by container_id → GIST/Btree narrows the chunk pages.

| Query shape | P50 | P95 | Backing index |
|---|---|---|---|
| "last 5 minutes" (live tail) | <50ms | <200ms | BRIN(time) → 1 chunk; ~3k rows |
| "full sweep" of one container (cache hit) | <100ms | <500ms | Garage cache GET |
| "full sweep" (cache miss + bake) | <30s | <60s | full chunk scan + ST_MakePolyhedralSurface + meshopt |
| Spatial bbox + time range | <200ms | <800ms | BRIN(time) + GIST(anchor) BitmapAnd |
| Measurement key filter (e.g. `measurements @> '{"tcp_temp_c": >400}'`) | <300ms | <1.5s | GIN(measurements jsonb_path_ops) + BRIN |
| `time_bucket('5 min', time)` aggregate over 1 hour | <100ms | <400ms | continuous aggregate (§6.4) |

EXPLAIN check (projected; modelled on shepard's live `timeseries_data_points` plan with EXPLAIN ANALYZE returning 1000 points in 0.43ms per ts-design-audit): the BRIN + GIST BitmapAnd cost is dominated by index-page reads (≤ 50KB) with execution costs in the microsecond range; the latency floor is JDBC + JSON serialisation.

### 6.4 Continuous aggregates

Closes the AP-6 audit gap (no continuous aggregates on TS today). Ship one per canonical bucket:

```sql
CREATE MATERIALIZED VIEW shepard_spatial.profile_1m
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    container_id,
    time_bucket(60 * 1e9::BIGINT, time) AS bucket,
    count(*)              AS n_profiles,
    avg((measurements->>'tcp_temp_c')::double precision) AS mean_tcp_temp_c,
    max((measurements->>'tcp_temp_c')::double precision) AS max_tcp_temp_c,
    ST_3DExtent(anchor)   AS anchor_bbox
FROM shepard_spatial.profile
GROUP BY container_id, bucket;
```

Refresh policy: 5-minute lag, 1-day window. `materialized_only = false` ensures live data still falls through to the raw hypertable. Wire from the Chart-View service path. The `tcp_temp_c` specialisation here is the MFFD canonical case; the production set is auto-generated from declared `MeasurementSchema` shapes at container-create time (so each schema produces matching aggregates per declared `qudt:numericValue` field).

### 6.5 Render budget

Path A (client loft):

| Element | Budget | Source |
|---|---|---|
| BufferGeometry max vertices (per mesh) | ~16M with `Uint32` indices [^threejs-perf] | Three.js docs |
| Practical "smooth interaction" cap | ~5M vertices per scene | Three.js BufferGeometry community benchmarks |
| Per-frame draw calls | ≤200 to maintain 60fps | Three.js stats panel norms |
| Per-frame memory | ~250 MB GPU RAM acceptable on mid-range laptop | WebGL2 typical |

Path B (server-loft glTF):

- `KHR_mesh_quantization` shrinks positions from 12 bytes to 6 bytes per vertex (~50% reduction without visible loss in the engineering-mesh regime) [^khr-quantization].
- `EXT_meshopt_compression` adds ~50% further reduction with fast SIMD decode (~1µs/vertex on modern desktop) [^ext-meshopt-bench].
- Net: 5M-vertex brush mesh in glTF ~= 120 MB uncompressed → ~60 MB quantized → ~30 MB meshopt → ~5 MB gzipped over the wire.

### 6.6 Network budget for live-tail (Path C)

SSE JSON frame per profile (LINESTRINGZ × 50pts × 24 bytes + measurements ≈ ~1.5 KB raw + ~200 byte overhead). At AFP 10 Hz = ~15 KB/s/viewer; trivial. At profilometer 1 kHz = ~1.5 MB/s/viewer; this is the upper bound where switching to Arrow Flight (§2 deferred row) becomes attractive (~4× compression on a typical profile via Arrow IPC dictionary encoding).

### 6.7 Compression sensitivities + backup/restore

- TimescaleDB compression segments by `container_id` (matches the workload's natural partition).
- Backup contract aligns with the cross-substrate plan in synthesis Pass 2 §"backup contract": Garage volume snapshot (Postgres data directory + the spatial-cache bucket) replicated nightly. Restore tested by container-restore drill on a staging replica (per `aidocs/admin/runbooks/restore-spatial-container.md` to be filed at v0).
- Compression-policy lag: `compress_after: 7 days` matches the TS hypertable; backfilled rows older than 7 days await the nightly compression job (closes AP-8 by reusing the same retrofit policy the TS audit recommends — call `compress_chunk` post-import).

[^pg-copy-bench]: Tiger Data, "Best practices for batch inserts and COPY", https://docs.tigerdata.com/use-timescale/latest/write-data/insert/ — COPY recommended for batches >1000 rows; sustains tens of thousands of rows/sec on commodity hardware.
[^ext-meshopt-bench]: meshoptimizer benchmark comparison, https://github.com/zeux/meshoptimizer#comparison — meshopt typically 30–50% smaller than Draco with 5–10× faster decode.

---

## §7 — Trace3D VIEW_RECIPE extension

Per `aidocs/semantics/98 §2`, a view is a `:ShepardTemplate` with `templateKind = VIEW_RECIPE`. The brush mode is a SHACL property on the existing Trace3D recipe shape from `project_trace3d_view.md`.

```turtle
@prefix sh:      <http://www.w3.org/ns/shacl#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
@prefix shepard: <https://shepard.dlr.de/ontology/> .

shepard:BrushTraceShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl       "/v2/assets/views/spatial/v1/brush.mjs" ;
    shepard:rendererIntegrity "sha384-…" ;

    sh:property [ sh:path shepard:traceSource ;
                  sh:class shepard:SpatialDataContainer ;
                  sh:minCount 1 ; sh:maxCount 1 ] ;

    sh:property [ sh:path shepard:brushMode ;
                  sh:in ( "point" "line" "tube" "ruled-surface" "tin-mesh" "point-cloud-frames" ) ;
                  sh:defaultValue "ruled-surface" ;
                  sh:description "Brush stroke geometry. Choose by profile_kind in the source container." ] ;

    sh:property [ sh:path shepard:valueChannel ;
                  sh:datatype xsd:string ;
                  sh:description "JSONB key in measurements to color the brush by. Empty → uniform color." ] ;

    sh:property [ sh:path shepard:gradientStops ;
                  sh:datatype xsd:string ;
                  sh:description "Color-map identifier (e.g. 'inferno', 'viridis') or JSON array of [value,#RRGGBB]." ] ;

    sh:property [ sh:path shepard:simplifyToleranceMm ;
                  sh:datatype xsd:double ;
                  sh:defaultValue 0.0 ;
                  sh:description "Douglas-Peucker per-profile-vertex tolerance. 0 = no simplification." ] ;

    sh:property [ sh:path shepard:decimateEveryN ;
                  sh:datatype xsd:integer ;
                  sh:defaultValue 1 ;
                  sh:description "Keep every Nth profile in time. 1 = no temporal decimation." ] ;

    sh:property [ sh:path shepard:tubeRadiusMm ;
                  sh:datatype xsd:double ;
                  sh:description "For brushMode=tube only. Radius of the tube around the anchor path." ] ;

    sh:property [ sh:path shepard:liveTail ;
                  sh:datatype xsd:boolean ;
                  sh:defaultValue false ;
                  sh:description "If true, subscribe to live-append SSE stream (Path C)." ] ;

    sh:property [ sh:path shepard:trailLengthMs ;
                  sh:datatype xsd:long ;
                  sh:description "For live-tail mode: window length in ms to keep visible." ] ;

    sh:property [ sh:path shepard:overlay ;
                  sh:class shepard:FilePayload ;
                  sh:maxCount 1 ;
                  sh:description "Optional as-designed CAD mesh rendered semi-transparent under the brush. See §8 frame-handshake." ] ;

    sh:property [ sh:path shepard:cadOverlayAppId ;
                  sh:class shepard:CadReference ;
                  sh:maxCount 1 ;
                  sh:description "Alternative to shepard:overlay — references a CadReference already in shepard (preferred for the as-designed/as-built workflow)." ] .
```

**Composition with existing shapes.** `shepard:BrushTraceShape` extends `shepard:Trace3DShape` from `project_trace3d_view.md`. The Trace3D shape already declares `xChannel`/`yChannel`/`zChannel` + `valueChannel`; the brush extension specialises the renderer for the *container-driven* case (one SpatialDataContainer → swept-profile geometry) vs the *channel-driven* case (three TS channels = X/Y/Z point trace). Same renderer module URL with mode discriminator; ≥1 of `traceSource` (BrushTrace) or `xChannel`+`yChannel`+`zChannel` (Trace3D) is required, per SHACL `sh:xone`.

**Upper-ontology alignment** (per `aidocs/96`): the BrushTraceShape carries `iao:isAbout shepard:SpatialDataContainer`. The displayed mesh is a `bfo:GenericallyDependentContinuant` (a view artefact) of the underlying `bfo:Process` (the scan or layup that produced the profiles). Provenance trail (`prov:wasGeneratedBy <render-activity>`) is captured per F(AI)²R when an AI agent invokes the render.

---

## §8 — Frame-handshake with `shepard-plugin-cad`

This is the architectural bridge that makes the killer demo — **swept brush of as-built scanning surface overlaid on as-designed CAD mesh in the same Three.js scene** — work end-to-end.

### 8.1 Shared frame registry

Both plugins read `:CoordinateFrame` via the CST1 service (aidocs/85). The spatial plugin reads its container's anchor frame; the CAD plugin reads its CadReference's anchor frame. Both register their renders against the same Three.js `THREE.Scene` instance via the existing TresJS `<TresCanvas>` mount used by vis-trace3d.

### 8.2 Slot mechanism

A `BrushTraceShape` instance with `cadOverlayAppId` set declares a **render slot** for a CAD payload. At render-time the vis-trace3d component:

1. Loads the brush geometry (Path A/B/C from §5).
2. Calls the CAD plugin's `loadIntoScene(cadAppId, sceneRef, opacity=0.3)` SPI method.
3. CAD plugin loads the glTF/STEP-tessellated mesh into the same scene, semi-transparent.
4. Both meshes share the CST1-resolved transform — no per-mesh matrix juggling.

The slot mechanism is a **frontend Vue composable** (`useCadOverlay`) provided by `shepard-plugin-cad`'s exported package. Spatial doesn't import CAD; it asks the plugin registry for any contributor of the `cad-overlay-slot` capability and uses what's there. If no CAD plugin is installed, the overlay slot returns a no-op; the brush still renders.

### 8.3 ICP-alignment workflow

When the user clicks "Align to CAD" on a BrushTrace view:

1. Frontend POSTs `/v2/spatial-containers/{appId}/align-to-cad/{cadAppId}` with `{ algorithm: "ICP" | "RANSAC", samplePoints: N }`.
2. Backend dispatches to the CAD plugin's Open3D sidecar (per aidocs/83 PC1b — the existing alignment substrate).
3. Sidecar samples N representative points from the swept-profile geometry (densify multipoint profiles, sample along ruled-surfaces) and runs ICP against the CAD mesh.
4. Result: a 4×4 transform matrix + RMSE.
5. Backend writes a new edge on CST1: `(:CoordinateFrame {anchor-of-spatial-container})-[:ALIGNED_TO {matrix, rmse, alignedAt, algorithm}]->(:CoordinateFrame {anchor-of-cad-reference})`. The frame tree absorbs the alignment; the profile rows are untouched.
6. Brush re-renders: CST1 returns the new composed transform; both meshes align.

### 8.4 Plugin-dependency declaration

Per `project_plugin_categories.md` (plugin meta-ontology): `shepard-plugin-spatial` declares `optional-requires: shepard-plugin-cad >= v1.0` for the overlay capability. Without CAD installed, the BrushTraceShape's `cadOverlayAppId` property silently falls back to no-op (the field is `sh:maxCount 1`, not `sh:minCount 1`).

### 8.5 What the killer demo looks like (acceptance criterion)

User opens TR-Q1 (MFFD AFP run, ply 5 anomaly): sees the as-designed CFRP fuselage section, the brush stroke laid down along the layup path, color-mapped by TCP temperature, the consolidation-force-drop region highlighted in red where the brush crosses the anomaly window. Scrubs time backward to ply 4 (nominal): brush goes blue, force-drop disappears. Total clicks from collection landing: 3.

---

## §9 — MFFD acceptance test

The first DT1 (digital-twin) stress test, per `project_mffd_seed_demo.md` + `project_trace3d_view.md`.

### 9.1 Synthetic AFP head sweep generator

Extension to `examples/mffd-showcase/scripts/` (mffd-showcase already has `scripts/` and `pipeline.yaml` per the directory inspection): add `scripts/gen_afp_sweep.py --with-afp-sweep` invoked from `seed.py` when the `--with-afp-sweep` flag is present.

Synthetic shape:
- 10 Hz × 30 minutes × 50-vertex `LINESTRINGZ` per profile (the TCP footprint contact arc on the substrate, ~80mm wide).
- ply 5 of the layup (per `project_mffd_seed_demo.md` Q1 anomaly).
- `anchor`: TCP position in `:CoordinateFrame {name:"mffd-fuselage-q1", frameType:"PART_NOMINAL"}`.
- `measurements` (closed-vocab via `MFFDAFPProfileShape`):
  - `tcp_temp_c` (double, deg-C, ~380 nominal, anomaly window 350–450 during ply 5)
  - `consolidation_force_n` (double, Newtons, ~450 nominal, drops to ~380 at ply 5)
  - `compaction_roller_force_n` (double, Newtons)
  - `roller_speed_mm_per_s` (double)

### 9.2 Container create

```bash
curl -X POST $SHEPARD/v2/spatial-containers \
  -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MFFD-Q1-AFP-Ply-5-Sweep",
    "coordFrameAppId": "frame-mffd-fuselage-q1",
    "profileKindAllowed": ["line"],
    "measurementSchemaAppId": "shape-mffd-afp-profile"
  }'
```

### 9.3 VIEW_RECIPE

```turtle
mffd-views:Q1AFPThermalTrail a shepard:BrushTraceShape ;
    shepard:traceSource         spatial-container:mffd-q1-afp-ply-5-sweep ;
    shepard:brushMode           "tube" ;
    shepard:tubeRadiusMm        40.0 ;
    shepard:valueChannel        "tcp_temp_c" ;
    shepard:gradientStops       "inferno" ;
    shepard:simplifyToleranceMm 0.5 ;
    shepard:decimateEveryN      5 ;
    shepard:cadOverlayAppId     cad-reference:mffd-fuselage-q1-step .
```

### 9.4 Expected output + acceptance criteria

- Three.js scene: the thermal trail laid down along ply 5 of the fuselage; CAD overlay underneath at 0.3 opacity.
- Color band: deep red where TCP temp >430°C (the anomaly hot zone).
- Time scrubber spans the 30-minute window; scrubbing animates the brush growing along the path.
- Click on the hot zone → tooltip shows `tcp_temp_c=445`, `consolidation_force_n=378`, `ts=2026-05-22T14:08:31`.

**Acceptance**:

| Criterion | Target | Measurement |
|---|---|---|
| Full sweep render time, cache hit | <3s | from request to first paint |
| Full sweep render time, cache miss (cold) | <15s | bake + transfer + paint |
| Spatial bbox + time-range query | <200ms P95 | via API direct |
| Storage post-compression | <100MB | (3.6M rows × ~1.5KB / 23× = ~235MB; with decimate=5 → ~47MB) |
| Frame rate during scrub | ≥30 fps | Three.js stats panel |
| Frame rate live-tail mode | ≥30 fps with 10 Hz append | Three.js stats panel |

### 9.5 Test obligations

Per `feedback_always_write_tests.md`:

- **Backend**: JUnit5 in `plugins/spatial/src/test/java/de/dlr/shepard/plugins/spatial/SpatialBrushE2ETest.java`. Testcontainer with PG16 + postgis + timescaledb extensions; seeds 1000 synthetic profiles; asserts query latency P95 < 200ms; asserts the SHACL strict-mode rejection on bad measurements payload.
- **Frontend**: Vitest in `frontend/components/spatial/__tests__/BrushTrace.spec.ts`. Mocks the JSON-frame stream; asserts BufferGeometry vertex count matches expectation; asserts color-map application.
- **Playwright** (per `feedback_validate_via_ui.md` + `feedback_ux_playwright.md`): one e2e visiting the MFFD-Q1-AFP-Ply-5 view at 4K viewport, asserting the canvas renders within budget and the hot-zone tooltip resolves.

---

## §10 — v0 / v1 / v2 milestone breakdown

### v0 — MVP (1–2 sprint target)

**Code:**
- `plugins/spatial/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql` — full §3 DDL.
- Operator runbook for the schema swap (see §11): live spatial table has zero application rows in the audit data; drop-and-replace is safe but the runbook ships either way.
- `SpatialProfileRepository.insertManyWithCopy()` — COPY binary write path.
- `SpatialBrushQueryService.streamWindow(containerAppId, from, to, decimate)` — Path A JSON SSE.
- `SpatialBrushRenderResource` — `GET /v2/spatial-containers/{appId}/trace?format=json|glb` (Path A + Path B; Path C deferred to v1).
- `BrushTraceShape.ttl` shipped to `plugins/spatial/src/main/resources/shapes/`.
- Frontend `frontend/components/spatial/BrushTraceView.vue` — Path A renderer, no live-tail yet.
- MFFD AFP synthetic generator: `examples/mffd-showcase/scripts/gen_afp_sweep.py`.

**Endpoints:**
- `POST /v2/spatial-containers` (gets `coord_frame_app_id`, `profile_kind_allowed`, `measurement_schema_appid`).
- `POST /v2/spatial-containers/{appId}/profiles` — bulk write, COPY backed.
- `GET /v2/spatial-containers/{appId}/trace?format=json|glb&from=&to=&simplify=&decimate=` — Path A + Path B.

**Docs trinity** (per `feedback_plugins_ship_own_docs.md`):
- `plugins/spatial/docs/reference.md` — update to v2.0 schema + endpoints + the BrushTraceShape.
- `plugins/spatial/docs/quickstart.md` — "How do I publish my AFP TCP path?"
- `plugins/spatial/docs/install.md` — update from `postgis` separate container to TS Postgres `postgis` extension.

**Tests** (per `feedback_always_write_tests.md`):
- Schema migration test (testcontainer assertions on CHECK constraints + indexes).
- Write-path test (COPY ingest, 10k rows, assert no SQL string per batch).
- Read-path test (BRIN+GIST plan check via EXPLAIN; verifies index hit).
- The full §9 e2e acceptance test.

**Updates to existing trackers** (per CLAUDE.md mandatory rules):
- `aidocs/34-upstream-upgrade-path.md` — new row for the schema breaking change.
- `aidocs/42-vision.md` — update §"What's in the box" payload-kind table; add brush-trace bullet to §"Where it's going"→"near horizon".
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — new SPATIAL-V6 rows in the matrix.
- `aidocs/data/00-model-inventory.md` — register the new schema entities.
- Regenerate `aidocs/01-doc-stage-index.md`.

### v1 — Full feature surface (1 sprint after v0)

- Path C live-tail (SSE + Postgres LISTEN/NOTIFY); frontend live-tail mode in `BrushTraceView.vue`.
- Pre-bake hook on container close (calls render-to-Garage server-side; populates cache).
- Continuous aggregate views (§6.4) + Chart-View consumer wiring.
- The CST1 `:ANCHORED_IN` edge + spatial container migration (legacy `crs` field → CST1 frame).
- Frame-handshake §8 implementation: CAD overlay slot + `useCadOverlay` composable shipped from shepard-plugin-cad.
- ICP `POST /v2/spatial-containers/{appId}/align-to-cad/{cadAppId}` endpoint (reuses CAD plugin's Open3D sidecar).
- MapLibre vector-tile endpoint (closes existing `PLUGIN-SPATIAL-AUDIT-2026-05-24-003`) — for 2D top-down inspection workflows.
- v1-grade docs trinity refresh.

### v2 — Scale + viewer polish (later)

- Apache Arrow Flight streaming-tail format (high-rate live-tail).
- pgpointcloud adoption for >10M-point-per-frame use cases (CT, structured-light at full density).
- Multi-trace overlay in one viewer (multiple containers in one BrushTrace scene — for AFP+welding+frame-assembly comparison).
- Profile embedding (`pgvector`-indexed brush descriptors for "find similar sweep" — but only after the AI plugin lands).
- Plugin rename decision (see §11 open).

---

## §11 — Honest concerns + open questions

1. **Profile-shape evolution mid-run.** A profilometer's vertex count changes when the operator reconfigures probe spacing. v0: vertex count is per-row, not per-container; the only constraint is `profile_kind`. Open: should the container's `MeasurementSchema` shape include a `profileVertexCountRange` constraint? Lean: yes for instruments where vertex-count change is operationally meaningful, no otherwise. Decide via field experience.

2. **Coord-frame propagation when a parent frame moves.** CST1 supports `validFromMs`/`validUntilMs` on frame transforms (per aidocs/85 §3). When ICP re-aligns mid-stream, do older profiles re-render in the old frame or the new one? Lean: render uses the frame transform valid **at the profile's `time`** — historically accurate, but means a re-alignment doesn't visually move old data (which is the right RDM behaviour — provenance trail intact). Confirm with IME persona before v1.

3. **When does a 10k-point per-frame profile cross into pointcloud-plugin territory?** Heuristic: vertex count per profile × profile rate > 10⁵ vertices/sec for sustained periods → recommend `shepard-plugin-cad/PointCloudReference` (PC1a) instead. Document the heuristic in `install.md`; ship a CLI checker.

4. **Live-tail vs batch-render cache conflict.** A live-tail viewer and a cache-fetching viewer on the same container don't share state. Acceptable: live-tail is for in-flight viewing; once container closes (markComplete called), live-tail mode is unavailable and the cache becomes authoritative. Pre-bake hook bridges the gap.

5. **Drop-and-replace runbook for the legacy `spatial_data_points` table.** Per the audit-data note in the dispatch brief: live spatial workload is zero rows on the audited deploys. v0 ships a `verify-empty.sh` operator script: `SELECT count(*) FROM spatial_data_points;` — if zero, drop the table; if non-zero, run a one-shot migrator (rows mapped to `(profile_kind='point', anchor=position, profile=NULL, measurements=measurements, metadata=metadata)` in the new schema, container metadata derived from `spatial_data_container`). Migrator stub ships in v0 but is only required by operators with live data.

6. **Audit-fleet gap** (synthesis §7). A PostGIS-substrate audit on this design should fire BEFORE code lands. Backlog row in §13. Audit prompt mirrors the TS audit shape; reviews EXPLAIN plans on a seeded dataset.

7. **Naming bikeshed.** Should the plugin still be called `shepard-plugin-spatial`, or rename to `shepard-plugin-trace` / `shepard-plugin-sweep` / `shepard-plugin-engineering-geometry`? Lean: **keep `shepard-plugin-spatial`** — the name carries continuity, the v6 reshape extends the meaning naturally, and a rename creates artefact-discoverability churn for operators. Open for community feedback; backlog row tracks the decision.

8. **SHACL-substrate dependency.** `MeasurementSchema` shapes need a substrate to live in. Per synthesis §3 T1, the SHACL-as-SoT cut is queued. v0 ships with `measurement_schema_appid IS NULL` permitted (open-world) so it doesn't block on the SHACL substrate; strict-mode is unblocked the moment SHACL substrate lands.

9. **Cross-substrate JOIN unlocking.** Per synthesis §3 T2 cut (one Postgres + three schemas), the spatial plugin's value compounds the moment Tables ships: `SELECT t.ply_number, p.tcp_temp_c FROM shepard_spatial.profile p JOIN shepard_tables.layup_plan t ON t.ply_number = (p.metadata->>'ply')::int AND t.campaign = p.container_id`. v0 doesn't depend on Tables; v1 enables the cross-substrate query example in docs.

10. **AI-pipeline addressing through 5-tuple.** Brush traces are addressed by `(container_app_id, time, seq)` — clean, no 5-tuple. Profile embeddings (v2 idea) would key on the same shape; future `pgvector` index lives in the same hypertable as a sister column. No 5-tuple debt.

---

## §12 — Decisions log

| # | Decision | Alternatives | Decisive constraint | Cut |
|---|---|---|---|---|
| D1 | Green-field schema, no backward compat | (a) ALTER TABLE incremental, (b) shadow table | Existing rows are zero / negligible (advisor verification); the V1.0.0 antipatterns (HASH partition, no BRIN, polymorphic JSONB without CHECK) are not patchable without a rewrite | (a) |
| D2 | One Postgres + `shepard_spatial` schema, retire separate `postgis` container | Keep separate PG | Synthesis §3 T2 + PG-AUDIT-005 over-provisioning + the Tables plugin's cross-substrate-JOIN value-prop | (a) |
| D3 | TimescaleDB hypertable + BRIN(time) + space-partition on container_id | RANGE partition manually | Hypertable is best-practice for time-series + we already pay the operational cost of TimescaleDB | (a) |
| D4 | `profile_kind` discriminator with CHECK matching geometry type | Polymorphic geometry only, type discriminated at read | Closes TS-AUDIT-003 (substrate-level type contract); read path doesn't need to guess | (a) |
| D5 | `measurements` JSONB with optional SHACL strict mode | (a) typed columns, (b) per-key expression indexes only, (c) hard-bound vocabulary at substrate | (a) requires per-deployment DDL; (c) blocks v0 on SHACL substrate; (b) doesn't generalise; JSONB + SHACL-strict-conditional is the SHACL-as-SoT cut in T1 | (a) JSONB + optional SHACL |
| D6 | Adopt CST1 (aidocs/85) frame tree, no custom frame model | ROS tf2 binding, AAS submodel only, custom new shape | CST1 already designed + accepted, ROS-TF2-compatible, AAS-exportable | (a) |
| D7 | Three render paths (client / server-glTF / SSE-stream) | Single path | Workload characteristics differ by 3+ orders of magnitude (ad-hoc <5k vs profilometer 3.6M); one path optimises for the wrong end | (a) |
| D8 | meshopt as default glTF compression | Draco, plain glTF | Faster decode + smaller payload (vendored benchmarks); Three.js native support | (a) |
| D9 | Append-only writes, no ON CONFLICT, no upsert | Upsert by (container_id, time) | Append-only matches the scanning/streaming source semantics; closes TS-AUDIT-002 by removing the ON CONFLICT complexity | (a) |
| D10 | Plugin retains name `shepard-plugin-spatial` | Rename to `-trace`, `-sweep`, `-engineering-geometry` | Operator discoverability + continuity; doesn't block the v6 narrative | (a) flag as bikeshed |
| D11 | Brush-rendering renderer lives in vis-trace3d, not in new plugin | New plugin `shepard-plugin-brush` | vis-trace3d already owns the Three.js scene mount + the SHACL Trace3D shape; brush is a mode, not a new container | (a) |
| D12 | Frame-handshake via Vue composable (`useCadOverlay`), not Java SPI | Java SPI seam, REST callback | Deployment-unit semantics (per `aidocs/98 §2.3`): the renderer is JS, the SPI seam belongs in the JS layer | (a) |

---

## §13 — Backlog rows to file

The following rows file in `aidocs/16-dispatcher-backlog.md` under a new section `SPATIAL-V6-*`. Existing `PLUGIN-SPATIAL-AUDIT-2026-05-24-*` rows are reconciled per the table at the end of this section.

| ID | Item | Size | Status | Notes |
|---|---|---|---|---|
| `SPATIAL-V6-001` | **v0 schema migration** (`V2.0.0__green_field_schema.sql`) — full §3 DDL on the existing TS Postgres with `postgis` extension; new `shepard_spatial` schema; drop-and-replace runbook. | M | queued | aidocs/90 §3 + §10 v0. |
| `SPATIAL-V6-002` | **COPY-based write path** — `SpatialProfileRepository.insertManyWithCopy()` + delete the string-INSERT antipattern preemptively. | S | queued | aidocs/90 §3.8 + §6.2; closes TS-AUDIT-002 for spatial in advance. |
| `SPATIAL-V6-003` | **`GET /v2/spatial-containers/{appId}/trace` endpoint** — Path A JSON SSE + Path B glTF format-negotiated (KHR_mesh_quantization + EXT_meshopt_compression). | M | queued | aidocs/90 §5 + §10 v0. |
| `SPATIAL-V6-004` | **Frontend `BrushTraceView.vue`** in vis-trace3d — Path A renderer; tied to the BrushTraceShape VIEW_RECIPE. | M | queued | aidocs/90 §7 + §10 v0. |
| `SPATIAL-V6-005` | **MFFD acceptance test** — synthetic `--with-afp-sweep` generator + e2e test (backend JUnit + frontend Vitest + Playwright at 4K). | M | queued | aidocs/90 §9. |
| `SPATIAL-V6-006` | **CST1 integration** — `:ANCHORED_IN` Cypher edge + `coord_frame_app_id` FK-by-convention + migrate existing `crs` field to a CST1 frame. | S | queued | aidocs/90 §4 + aidocs/85. |
| `SPATIAL-V6-007` | **Frame-handshake with shepard-plugin-cad** — `useCadOverlay` composable shipped from CAD plugin + brush+CAD overlay rendering. | M | queued | aidocs/90 §8. |
| `SPATIAL-V6-008` | **Path C live-tail** — SSE stream + Postgres LISTEN/NOTIFY + frontend live-tail mode. | M | queued (v1) | aidocs/90 §5 Path C + §10 v1. |
| `SPATIAL-V6-009` | **ICP alignment endpoint** — `POST /v2/spatial-containers/{appId}/align-to-cad/{cadAppId}` reusing CAD plugin's Open3D sidecar; writes CST1 `[:ALIGNED_TO]` edge. | M | queued (v1) | aidocs/90 §8.3 + aidocs/83 PC1b. |
| `SPATIAL-V6-010` | **PostGIS-substrate audit dispatch** — substrate-direct audit prompt on the new schema BEFORE code lands; mirror of TS audit shape; reviews EXPLAIN plans on the seeded MFFD dataset. | M | queued (gate v0) | aidocs/90 §11 row 6; synthesis §7 audit-fleet gap. |
| `SPATIAL-V6-011` | **Docs trinity refresh** — `plugins/spatial/docs/{reference,quickstart,install}.md` updated to v6 schema, endpoints, BrushTraceShape, and frame-handshake. | S | queued | aidocs/90 §10 v0 docs gate. |

**Reconciliation with existing `PLUGIN-SPATIAL-AUDIT-2026-05-24-*` rows:**

| Existing row | Disposition | Rationale |
|---|---|---|
| `-001` (PM1f sidecar — postgis manifest) | **retire** (folded into SPATIAL-V6-001) | Once postgis collapses into TS Postgres (`-004` below), there is no separate sidecar to declare. The TS Postgres sidecar declaration lives with the TS plugin. |
| `-002` (`V1.1.0__indexes.sql` BRIN+GIN) | **retire** (folded into SPATIAL-V6-001) | The green-field V2.0.0 ships these indexes by construction; the V1.1.0 patch is moot. |
| `-003` (MVT vector-tile + MapLibre) | **carry forward as SPATIAL-V6-012 (v1)** | Independent capability; survives the reshape. |
| `-004` (collapse `postgis` container) | **carry forward as SPATIAL-V6-001 subtask** | This is the substrate-collapse that v0 depends on; absorbed. |
| `-005` (GeoQueryCapability SPI) | **carry forward as SPATIAL-V6-013 (v1)** | Independent of reshape; still parked-defer until second consumer surfaces. |

---

## §14 — See also

- `aidocs/data/81-spatial-data-binding.md` — DataBinding model (alive; binds to brush traces too in v6).
- `aidocs/data/82-spatial-perf-evaluation.md` — perf evaluation (recommendations folded into §3 here; doc retires after this lands).
- `aidocs/data/83-pointcloud-and-live-overlay.md` — CAD plugin frame-handshake partner (§8).
- `aidocs/data/85-coordinate-frame-tree.md` — CST1; adopted by §4.
- `aidocs/data/84-live-digital-twin.md` — DT1; brush is a consumer of the FanOutBus.
- `aidocs/data/86-scene-drive-and-replay.md` — DR1; brush is a scene-drive variant.
- `aidocs/semantics/98-shapes-views-and-process-model.md` — VIEW_RECIPE substrate (§7).
- `aidocs/agent-findings/ts-design-audit-2026-05-24.md` — antipatterns this design refuses to repeat.
- `aidocs/agent-findings/synthesis-architecture-report-2026-05-24.md` — §3 T1 + T2 cuts this design honours.
- `aidocs/agent-findings/plugin-design-audit-2026-05-24.md` — §Spatial; original audit feeding this reshape.
- `aidocs/agent-findings/vis-plugin-survey-addendum-cad-fem.md` — §Category 5 CAD; sibling plugin family.
- `aidocs/platform/47-dev-experience-and-plugin-system.md §2` — PayloadKind / PayloadStorage SPI; the seam this plugin fits.
- `project_mffd_seed_demo.md`, `project_trace3d_view.md`, `project_mffd_guiding_principle.md` — user-side context for §9.
