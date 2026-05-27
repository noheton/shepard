-- SPATIAL-V6-001 — green-field shepard_spatial schema
--
-- Ships the v6 profile hypertable on TimescaleDB with PostGIS co-located
-- on the same Postgres instance (per aidocs/data/90 §3 + synthesis §3 T2).
--
-- Pre-conditions: this migration runs against a TimescaleDB + PostGIS
-- enabled PostgreSQL instance (see infrastructure/timescaledb-postgis/Dockerfile).
-- Both extensions must be present; the CREATE EXTENSION IF NOT EXISTS guards
-- make this migration idempotent.
--
-- IMPORTANT: this migration is additive — it does NOT remove the legacy
-- spatial_data_points table created by V1.0.0__setup_spatial_data_tables.sql.
-- Old data and old endpoints remain operational during the v5→v6 transition.
-- Operators may drop the V1 tables after all data has been migrated to
-- the profile hypertable.
--
-- Operator runbook: plugins/spatiotemporal/docs/install.md §"V2 Migration"

-- Ensure both extensions exist on the target instance.
-- timescaledb is already present (it's a TimescaleDB image).
-- postgis is co-located per Decision D2 (aidocs/data/90 §10).
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;

-- New schema isolates v6 shapes from the legacy public-schema tables.
CREATE SCHEMA IF NOT EXISTS shepard_spatial;

SET search_path TO shepard_spatial, public;

-- ─────────────────────────────────────────────────────────────────────────────
-- Integer-now helper (epoch ns, matching timeseries_data_points convention).
-- Must be created before set_integer_now_func is called below.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION shepard_spatial.profile_integer_now()
  RETURNS BIGINT
  LANGUAGE SQL STABLE
AS $$
  SELECT EXTRACT(EPOCH FROM NOW())::BIGINT * 1000000000
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- §3.1 — Profile container metadata
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE shepard_spatial.profile_container (
    container_id             BIGINT  PRIMARY KEY,         -- mirrors :SpatialDataContainer.id in Neo4j; FK-by-convention
    coord_frame_app_id       UUID    NOT NULL,            -- → :CoordinateFrame.appId (CST1 / aidocs/85)
    profile_kind_allowed     TEXT[]  NOT NULL,            -- closed vocab of kinds this container accepts
    measurement_schema_appid UUID,                        -- optional :ShepardTemplate(SHACL_SHAPE) → strict mode
    created_at_ms            BIGINT  NOT NULL,
    retired_at_ms            BIGINT,                     -- soft-retire mark; never hard-delete
    CHECK (profile_kind_allowed <@ ARRAY[
        'point', 'line', 'polygon', 'tin', 'multipoint', 'tube_centerline'
    ]::text[])
);

COMMENT ON TABLE shepard_spatial.profile_container IS
    'Per-container metadata for the v6 spatiotemporal profile hypertable. '
    'Maps a Shepard SpatialDataContainer (identified by Neo4j id) to its '
    'allowed profile kinds and optional SHACL measurement schema.';

COMMENT ON COLUMN shepard_spatial.profile_container.coord_frame_app_id IS
    'UUID of the :CoordinateFrame.appId on Neo4j (CST1 / aidocs/85). '
    'All geometry in this container is expressed in this frame.';

COMMENT ON COLUMN shepard_spatial.profile_container.profile_kind_allowed IS
    'Vocabulary of profile kinds this container accepts. '
    'Writes with profile_kind outside this array are rejected by the service layer.';

COMMENT ON COLUMN shepard_spatial.profile_container.measurement_schema_appid IS
    'Optional :ShepardTemplate(SHACL_SHAPE) appId. '
    'NULL → open-world measurements (no schema enforcement). '
    'Non-null → strict SHACL validation on every write via JenaShaclValidator.';

COMMENT ON COLUMN shepard_spatial.profile_container.retired_at_ms IS
    'Soft-retire timestamp (epoch ms). NULL = active. '
    'Physical deletion is handled by SM1a orphan sweep, never by cascade.';

-- ─────────────────────────────────────────────────────────────────────────────
-- §3.2 — Profile hypertable
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE shepard_spatial.profile (
    container_id     BIGINT   NOT NULL
        REFERENCES shepard_spatial.profile_container(container_id),
    time             BIGINT   NOT NULL,                  -- epoch ns; matches timeseries_data_points convention
    profile_kind     TEXT     NOT NULL,                  -- discriminator
    anchor           GEOMETRY NOT NULL,                  -- always POINTZ — the head/TCP/origin position
    profile          GEOMETRY,                           -- NULL only when profile_kind = 'point' (degenerate)
    measurements     JSONB    NOT NULL DEFAULT '{}'::jsonb,
    metadata         JSONB    NOT NULL DEFAULT '{}'::jsonb,
    orientation      JSONB    NOT NULL DEFAULT '{}'::jsonb,  -- beam angle, probe offsets, etc. (PAUT/TOFD)
    seq              BIGSERIAL,                          -- monotonic tie-breaker within (container_id, time)

    -- PK: container + time + seq to allow multiple profiles at the same nanosecond.
    PRIMARY KEY (container_id, time, seq),

    -- Anchor must always be POINTZ (the "where the head is" position).
    CONSTRAINT chk_anchor_is_point CHECK (
        ST_GeometryType(anchor) = 'ST_Point' AND ST_NDims(anchor) >= 3
    ),

    -- Discriminator-vs-geometry agreement (closes TS-AUDIT-003 antipattern).
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

    -- JSONB shape sanity (closes TS-AUDIT-001 polymorphic-value-column trap).
    CONSTRAINT chk_measurements_is_object CHECK (jsonb_typeof(measurements) = 'object'),
    CONSTRAINT chk_metadata_is_object     CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT chk_orientation_is_object  CHECK (jsonb_typeof(orientation) = 'object')
);

COMMENT ON TABLE shepard_spatial.profile IS
    'v6 green-field profile hypertable. Each row is one timestep of a '
    'spatial profile sweep (AFP head, robot TCP, NDT probe, etc.). '
    'No default retention policy — per SM1a, orphan sweep is container-scoped. '
    'Operators may add: SELECT add_retention_policy(''shepard_spatial.profile'', INTERVAL ''10 years'');';

COMMENT ON COLUMN shepard_spatial.profile.time IS
    'Epoch nanoseconds. Matches timeseries_data_points.time convention. '
    'TimescaleDB partitions by this column (1-day chunks).';

COMMENT ON COLUMN shepard_spatial.profile.anchor IS
    'POINTZ — the head/TCP/origin position at this timestep. Always 3D.';

COMMENT ON COLUMN shepard_spatial.profile.profile IS
    'The tool footprint geometry at this timestep. '
    'NULL only for profile_kind = ''point'' (degenerate single-point streams). '
    'Type must match the profile_kind discriminator (see chk_profile_kind_matches_geom).';

COMMENT ON COLUMN shepard_spatial.profile.orientation IS
    'Supplementary orientation metadata (e.g. beam angle for PAUT, '
    'probe offsets for TOFD). Open JSONB; validated by measurement_schema when present.';

-- Convert to hypertable with 1-day chunks and 4-space partitions by container_id.
-- 1-day interval matches timeseries_data_points for operational consistency.
SELECT create_hypertable(
    'shepard_spatial.profile', 'time',
    chunk_time_interval => 86400000000000::BIGINT,       -- 1 day in nanoseconds
    partitioning_column => 'container_id',
    number_partitions   => 4,
    if_not_exists       => TRUE
);

-- Register the integer-now helper so TimescaleDB can compute relative time.
SELECT set_integer_now_func('shepard_spatial.profile',
    'shepard_spatial.profile_integer_now', if_not_exists => TRUE);

-- ─────────────────────────────────────────────────────────────────────────────
-- §3.3 — Indexes
-- ─────────────────────────────────────────────────────────────────────────────

-- Time-range queries: BRIN (monotonic-arrival, cheap pages).
-- Per TS-AUDIT-005 + aidocs/82 §4 and TimescaleDB best practice.
CREATE INDEX profile_time_brin
    ON shepard_spatial.profile USING BRIN (time)
    WITH (pages_per_range = 32);

-- Anchor 3D spatial probe: GIST on POINTZ.
CREATE INDEX profile_anchor_gist
    ON shepard_spatial.profile USING GIST (anchor gist_geometry_ops_nd);

-- Profile 3D spatial probe: partial GIST on polymorphic profile geometry.
-- Partial saves cost on point-kind rows where profile IS NULL.
CREATE INDEX profile_geom_gist
    ON shepard_spatial.profile USING GIST (profile gist_geometry_ops_nd)
    WHERE profile IS NOT NULL;

-- Measurements JSONB query support (closes aidocs/82 §2.3 gap).
CREATE INDEX profile_measurements_gin
    ON shepard_spatial.profile USING GIN (measurements jsonb_path_ops);

-- Metadata JSONB query support.
CREATE INDEX profile_metadata_gin
    ON shepard_spatial.profile USING GIN (metadata jsonb_path_ops);

-- Profile-kind discrimination: used by every read for shape-aware rendering.
CREATE INDEX profile_kind_btree
    ON shepard_spatial.profile (container_id, profile_kind, time);

-- ─────────────────────────────────────────────────────────────────────────────
-- §3.4 — Compression policy
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE shepard_spatial.profile SET (
    timescaledb.compress,
    timescaledb.compress_segmentby           = 'container_id',
    timescaledb.compress_orderby             = 'time ASC, seq ASC',
    timescaledb.compress_chunk_time_interval = '7 days'       -- merge for higher ratios
);

-- 7-day compression delay matches the live timeseries_data_points policy.
SELECT add_compression_policy('shepard_spatial.profile', INTERVAL '7 days');

-- ─────────────────────────────────────────────────────────────────────────────
-- §3.5 — Retention comment (per SM1a; no default retention policy)
-- ─────────────────────────────────────────────────────────────────────────────
-- No add_retention_policy call here. Per project_storage_management.md (SM1a),
-- referenced data has INFINITE grace. Orphan sweep is container-scoped via
-- SpatialContainerOrphanSweeper. Operators who want a safety net invoke:
--   SELECT add_retention_policy('shepard_spatial.profile', INTERVAL '10 years');
