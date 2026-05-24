---
stage: feature-defined
last-stage-change: 2026-05-24
audience: [plugin-author, contributor]
---

# Plugin design audit — Spatial + Tables + HDF5, 2026-05-24

Design-only audit of three plugins in the `shepard-plugin-*` family.
Observation + recommendation; **no code changes**. Cross-feeds the
synthesis architecture report following the in-flight substrate audits
(TS, Neo4j+n10s, Mongo, Postgres+PgBouncer, Garage+stack, file-routing).

## TL;DR

- **Spatial** (`plugins/spatial/`) — shipped as PL1b + A1c-guarded; **substrate is fine**, the gap is (a) sidecar-decl debt (sidecar lives in central compose, not the plugin manifest) and (b) no frontend viewer + no map-tile endpoint. v0 = land `aidocs/82` §2.1+§2.3 indexes + MapLibre vector-tile shelf.
- **Tables** — **not shipped, no backlog row, no design doc**. Design memory exists (`project_table_container.md`) + ecosystem cataloged it (`aidocs/40 §140`) + epic-roadmap mentions it (`aidocs/platform/20 §398`). v0 = ship the SSOT design doc + file TB1 series before any code.
- **HDF5** (`plugins/hdf5/`) — A5a/b/d shipped; A5c (`HdfReference`) + A5e (h5pyd auth bridge) queued. **Substrate is decided** (HSDS sidecar per `aidocs/35`). Gaps: sidecar-decl debt (same as spatial), no frontend viewer, browse-tree is opaque to non-h5pyd clients.
- **Cross-cutting**: the **PM1f sidecar declaration is the headline gap** — both shipped plugins (spatial, hdf5) skip it; `file-s3` does it correctly. Two backfill rows file in §"Cross-cutting findings".

---

## Spatial (PL1b + PV1c + A1c)

### A. Current state

**Backend:** shipped (PL1b commit per `aidocs/16` row). Full tree under `plugins/spatial/src/main/java/`:
- `SpatialPluginManifest` + `SpatialPayloadKind` (ServiceLoader pre-CDI, per ADR-0023+0024).
- Domain: `data/spatialdata/{model,daos,services,repositories,endpoints,io}` — `SpatialDataContainer`, `SpatialDataPoint` (JPA, separate `quarkus.datasource."spatial"` datasource), `GeometryFilter` family (KNN, BBox, BoundingSphere, OBB), `NativeQueryStringBuilder` + `NativeInsertStatementBuilder` (raw SQL for PostGIS-specific `ST_*` calls).
- Reference: `context/references/spatialdata/{entities,daos,services,endpoints,io}` — `SpatialDataReference` Neo4j node + REST shelf.
- Migration: `src/main/resources/db/spatial/migration/V1.0.0__setup_spatial_data_tables.sql` — single `spatial_data_points` table, HASH-partitioned 100-way on `container_id`, GIST on `position`, GIN on `metadata`.

**Frontend:** stub. `frontend/pages/containers/spatialdata/[containerId]/` exists but no viewer component family (verified by `find frontend/components -iname '*spatial*'` returning empty). Compare to `frontend/components/context/display-components/{file,structured-data,relationships}-references/` — full kit for every other payload kind.

**Aidocs:** the design lives across three docs —
- `aidocs/data/81-spatial-data-binding.md` (canonical design — `DataBinding` model linking geometry annotation → timeseries channel)
- `aidocs/data/82-spatial-perf-evaluation.md` (perf reassessment — BRIN/GIN/TimescaleDB recommendations)
- `aidocs/data/83-pointcloud-and-live-overlay.md` (point cloud → `shepard-plugin-cad`, NOT spatial)

**Backlog rows touching spatial:**
- `PL1b` — pilot migration shipped.
- `PV1c` — `SpatialDataReference` versioning (`version_id` column on row groups). Queued, gated on PV1a.
- `A1c` — graceful degradation when PostGIS unavailable. Shipped (503+RFC7807).

**Stage:** prototype-to-shipped; backend complete + scale work + frontend pending.

### B. SPI contract fit

The plugin uses **two SPI seams** in tandem (correct pattern per ADR-0023):
1. `de.dlr.shepard.spi.payload.PayloadKind` — `SpatialPayloadKind` declares `name() = "spatial"` + `entityPackages()` for `SpatialDataContainer` + `SpatialDataReference`. Loaded by `ServiceLoader` in `NeoConnector.connect()` (pre-CDI).
2. `de.dlr.shepard.plugin.PluginManifest` — `SpatialPluginManifest` registers the plugin with `PluginRegistry` for the `/v2/admin/plugins` surface + runtime toggle (`shepard.plugins.spatial.enabled`).

The `PayloadKind` interface (`aidocs/47 §2.2`) is the right shape but **does not yet model a geometry-index seam or query-pushdown** — the plugin's native-SQL repositories (`NativeQueryStringBuilder`) reach around the SPI to express `ST_3DDWithin`, `<<->>` KNN, `ST_AsMVT`. This is fine *for this plugin* (PostGIS is the substrate of record) but a `ShapeReadable` / `GeoQuerySupport` capability declaration would let downstream consumers (search, OGC export, MapLibre tile endpoint) target spatial generically.

**Substrate:** PostGIS via dedicated `quarkus.datasource."spatial"` datasource (`postgis/postgis:16-3.5`). **Separate from the TS Postgres** — two Postgres instances on the same host. This is over-provisioned for the typical small-deploy and may bite when a researcher's spatial workload has natural time-series shape (per `aidocs/82 §2.2`, TimescaleDB hypertable conversion is the right answer; same SQL surface, zero infra cost since timescaledb container is already running).

**Auth + permissions:** inherits Collection permissions (Reader/Writer/Manager) the normal way — the JPA-backed rows are joined back to `SpatialDataReference` Neo4j nodes which are subject to the standard ACL filter. No bridge needed because the Postgres row never escapes shepard's API.

### C. Sibling-substrate decisions

**Cost of a separate `postgis` container vs. reusing TS Postgres**: ~256MB RSS + 100MB disk baseline + one more service to monitor. **Win:** zero blast-radius from a spatial workload running a 60-second `ST_3DDWithin` against the same PG that backs TimescaleDB hypertables. **Loss:** can't write SQL that joins `spatial_data_points` with TS measurements directly — would need cross-database query (FDW) or app-layer composition.

**Cross-ref**: this is the same decision Tables faces (§"Tables / C"). The consistent answer would be **one Postgres** with multiple schemas (`shepard_ts`, `shepard_tables`, `shepard_spatial`) — gives SQL-join power + simplifies ops. Currently we have **two Postgres + two TS-style splits**, which is the worst of both worlds. Flag as `POSTGRES-MULTITENANT-SCHEMA-DECISION` for the synthesis audit (expected TBD per the in-flight Postgres+PgBouncer audit).

**Migration:** flipping `postgis` container away and adding the `postgis` extension to the existing TS Postgres is a small operator-side change. The plugin's JDBC URL becomes the same as TS Postgres's. No application code change. Worth scheduling alongside `aidocs/82 §2.2` TimescaleDB conversion.

### D. Docs trinity status

All three pages exist under `plugins/spatial/docs/` (per DOCS-3A8 backfill 2026-05-23):
- `reference.md` — present.
- `quickstart.md` — present.
- `install.md` — present (cites the `spatial` compose profile + `postgis/postgis:16-3.4` image — note: compose file uses `16-3.5`; minor drift, fix in same backlog row as PM1f migration).

Carry `🤖 BACKFILL` marker; content is source-derived but unchecked against shipped behaviour. **Gap:** none structurally; content-accuracy follow-up is `PLUGIN-DOC-spatial-AUDIT` (queued for after sidecar migration).

### E. Sidecar declarations

**Gap.** `SpatialPluginManifest` does **not** override `sidecars()`. The `postgis` service lives in `infrastructure/docker-compose.yml` (verified: lines mentioning `postgis:` are in central compose, under the `spatial` profile, not in any plugin-shipped fragment). Violates `feedback_plugins_declare_sidecars.md`.

**Required shape** (per `PluginManifest §163` + `SidecarSpec` + `HealthcheckSpec` + `VolumeSpec` — PM1f shipped):
```java
@Override
public List<SidecarSpec> sidecars() {
  return List.of(
    new SidecarSpec(
      "postgis",                              // service name
      "postgis/postgis:16-3.5",               // image
      Map.of(                                 // env
        "POSTGRES_DB", "${POSTGRES_DB}",
        "POSTGRES_USER", "${POSTGRES_USER}",
        "POSTGRES_PASSWORD", "${POSTGRES_PASSWORD}"
      ),
      List.of(new VolumeSpec("postgis-data", "/var/lib/postgresql/data")),
      List.of(/* port mappings if any */),
      new HealthcheckSpec(
        new String[] {"CMD-SHELL", "pg_isready -U $POSTGRES_USER"},
        Duration.ofSeconds(15), 5
      ),
      /* postInit */ Optional.empty()
    )
  );
}
```
Reference: `plugins/file-s3/src/main/java/de/dlr/shepard/plugins/files3/FileS3PluginManifest.java` already does this for Garage.

**Migration shape:** add declaration → remove the `postgis` service from `infrastructure/docker-compose.yml` → operator restarts → deploy automation reads plugin manifests and re-emits compose. Net behaviour identical.

### F. Reuse-first survey

| Component | Adopt as | Why | Cite |
|---|---|---|---|
| **PostGIS** | sidecar (already adopted) | Reference implementation; battle-tested; the substrate of record for spatial in PG. | [PostGIS](https://postgis.net/) |
| **MapLibre GL JS** | library (frontend) | OSS fork of Mapbox GL (BSD-3); vector-tile rendering; designed for `ST_AsMVT` output. Pairs with Leaflet for raster fallback. | [MapLibre](https://maplibre.org/) |
| **GeoServer** | sidecar (deferred, opt-in) | Mature OGC-services (WMS/WFS/WCS). Worth as opt-in for institutes that need OGC interop. Heavy for casual use; defer. | [GeoServer](https://geoserver.org/) |
| **dash-vtk** | reference reading | Already on UNAS as field-validated viewer for 3D scientific viz (per backlog `VIS-DASH-VTK`). Trace3D (#142) candidate — orthogonal to PostGIS spatial. | `aidocs/16` row `VIS-DASH-VTK` |
| **TiTiler / pg_tileserv** | NOT adopt as SPI impl | Would compete with the in-process `ST_AsMVT` endpoint. Adds another sidecar without commensurate value at our scale. BUILD-OWN the MVT endpoint (one JAX-RS method calling `ST_AsMVT(ST_AsMVTGeom(...))`) — ~20 LoC. | — |

### G. Recommendations + v0 scope + backlog rows

**Design recommendation (3 bullets):**
1. **Substrate**: collapse `postgis` container into the existing TS Postgres via the `postgis` extension + a `shepard_spatial` schema. Re-points one JDBC URL; PG runs both PostGIS + TimescaleDB cleanly (they're independent extensions). Reclaims ~256MB + unlocks cross-substrate JOINs. Land alongside `aidocs/82 §2.2` TimescaleDB hypertable conversion.
2. **SPI hook**: add `GeoQueryCapability` declaration to `PayloadKind` (optional default-no-op) so downstream search/export consumers can ask "does this kind support a bbox / KNN / DWithin shape?" without reflecting on the repository class. Small, additive, opens the door to a generic OGC export later.
3. **Sidecar**: implement `sidecars()` per PM1f; remove `postgis` from central compose.

**v0 scope** (the MVP demonstrating SPI fit + closes the perf+frontend gap):
- Migration `V1.1.0__indexes.sql`: `CREATE INDEX CONCURRENTLY ... USING BRIN(time)` + `... USING GIN(measurements jsonb_path_ops)` (per `aidocs/82 §2.1+§2.3`).
- New JAX-RS method `GET /v2/spatial-data-containers/{appId}/tiles/{z}/{x}/{y}.mvt` returning `ST_AsMVT()` bytes (per `aidocs/82 §3` "MVT tile endpoint", half-week effort estimate).
- Frontend: `SpatialDataMapView.vue` component using MapLibre GL JS + the MVT endpoint as a vector source. Mounts under `frontend/pages/containers/spatialdata/[containerId]/index.vue`. Embeddable into `CollectionDataObjectsPanel` for spatial-bearing DataObjects.
- Sidecar declaration via PM1f (per §E).

**Backlog rows to file (in `aidocs/16-dispatcher-backlog.md`):**
- `PLUGIN-SPATIAL-AUDIT-2026-05-24-001` — PM1f sidecar migration (postgis declaration; remove from central compose).
- `PLUGIN-SPATIAL-AUDIT-2026-05-24-002` — `V1.1.0__indexes.sql` BRIN+GIN (per `aidocs/82 §2.1+§2.3`).
- `PLUGIN-SPATIAL-AUDIT-2026-05-24-003` — MVT tile endpoint + MapLibre frontend viewer.
- `PLUGIN-SPATIAL-AUDIT-2026-05-24-004` — collapse separate `postgis` container into TS Postgres (`postgis` extension + `shepard_spatial` schema); cross-ref `POSTGRES-MULTITENANT-SCHEMA-DECISION`.
- `PLUGIN-SPATIAL-AUDIT-2026-05-24-005` — `GeoQueryCapability` declaration on `PayloadKind` (additive default-no-op).

---

## Tables (no row yet — file as TB1 series)

### A. Current state

- **Backend:** none. No `plugins/tables/` directory exists.
- **Frontend:** none.
- **Aidocs:** **no canonical design doc** — only oblique mentions:
  - `aidocs/40-ecosystem.md §140` — catalog entry: *"shepard-plugin-tables (TableContainer) — 📐 designed — Teable-inspired native Postgres tables with REST + SQL surface; joins with timeseries"*.
  - `aidocs/platform/20-epic-roadmap.md §398` — *"New `TableContainer` + `TableReference` backed by a curated set of Postgres tables under a `shepard_tables` schema (one schema per container; one Postgres table per dataset)."*.
- **Memory:** the design IS in operator memory — `project_table_container.md` + `casestudy_table_container.md`. Critical decisions already taken (Teable rejected for embedding due to AGPL-3.0; native build; **same Postgres instance as TimescaleDB**; human-readable table names; Grafana migration via native PG datasource).
- **Backlog row:** none. There is no `TB1` row. The prompt's reference to "TB1" is forward-looking.

**Stage:** design-only-in-memory. The work needed first is **SSOT design doc** before any code or backlog rows beyond an umbrella.

### B. SPI contract fit

Will fit the `PayloadKind` SPI naturally:
- `name() = "table"`.
- `entityPackages() = List.of("de.dlr.shepard.data.table.entities")` for `TableContainer` + `FieldDef` + `TableReference`.
- `PayloadStorage` impl: row data lives in the Postgres tables (named `shepard_tables.tbl_<containerAppId>` or — better — `shepard_tables.<slugified_container_name>` per the user-readable-name memory). The PayloadStorage `write/read` shape generalises poorly to rows — likely a **query-pushdown seam** is the right SPI extension here: `TableQueryCapability` declaring `select(filter, sort, page) → ResultSet`.

**Substrate:** the **TS Postgres** (decided in memory). One schema (`shepard_tables`), one PG table per `TableContainer`, slugified human-readable name. **DDL ops** (CREATE TABLE on container create, ALTER TABLE on FieldDef add, DROP TABLE on container delete) become first-class plugin operations.

**Auth + permissions:** inherits Collection permissions via the `TableReference` Neo4j node. **Critical:** SQL surface for Grafana/Superset (read-only) needs a *separate role* (`shepard_tables_ro`) with table-level GRANT mirrored from shepard's Manager-on-the-Container permission. This is the same shape as ADR-0024 split-credential pattern.

### C. Sibling-substrate decisions

The Tables plugin is **the strongest argument for collapsing all PG-based plugins into one Postgres instance** with multiple schemas (`shepard_ts`, `shepard_tables`, `shepard_spatial`):
- **Win:** real SQL JOINs — `SELECT t.ts, t.value, r.sample_condition FROM measurements t JOIN shepard_tables.experiments r ON r.id = t.tags->>'experiment_id'`. This is **the value prop** per `project_table_container.md` ("native Grafana PG data source, no Infinity plugin").
- **Loss vs. separate PG:** less isolation; a runaway query in Tables affects TS. Mitigation: PgBouncer per-schema-pool (cross-ref the in-flight `pgbouncer-audit` for that pool-sizing question).

PgBouncer sizing implication: each new schema-pool adds N max-conns. With three pools (TS, Tables, Spatial) at the same backend default, total PG max_connections rises 3x. Worth quantifying once the substrate-collapse decision is made.

### D. Docs trinity status

None of the three exist (no plugin module). The PR landing TB1a must ship:
- `plugins/tables/docs/reference.md` — REST shape + SQL view (`shepard_tables.<name>` table list) + FieldType reference (Text, Number, Boolean, Date, Single-Select, Multi-Select, Relation).
- `plugins/tables/docs/quickstart.md` — "How do I create a table of instruments?" + "How do I join with TS in Grafana?".
- `plugins/tables/docs/install.md` — `shepard_tables` schema bootstrap migration + the `shepard_tables_ro` Postgres role + Grafana data-source config recipe.

### E. Sidecar declarations

**No new sidecar** — reuses the existing TS Postgres. PM1f shape: `sidecars()` returns `List.of()` (default), but the plugin manifest's `description` documents the schema + role dependency explicitly so the operator knows.

### F. Reuse-first survey

| Component | Adopt as | Why | Cite |
|---|---|---|---|
| **PostgreSQL** | substrate (already adopted) | Source of truth for typed row data + SQL surface. | [PostgreSQL](https://www.postgresql.org/) |
| **Teable** (`teableio/teable`) | ARCHITECTURE INSPIRATION, do not embed | AGPL-3.0 incompatible with shepard's Apache-2.0 line per CLAUDE.md security gates. Use as reference for *how* (each table = real PG table, spreadsheet UI affordances). | [teableio/teable](https://github.com/teableio/teable) |
| **Baserow** (`bram2w/baserow`) | NOT adopt | AGPL-3.0 also; Django; embedding cost is the same as Teable. | [Baserow](https://baserow.io/) |
| **NocoDB** (`nocodb/nocodb`) | NOT adopt | AGPL-3.0; full ORM layer would supplant shepard's. | [NocoDB](https://nocodb.com/) |
| **PostgREST** | ADOPT as SPI impl | BSD-2; auto-generates REST from PG schema. Could be the entire `/records` CRUD surface — sidecar exposes `/postgrest/`, shepard's gateway adds JWT + permission filter. **Strong recommendation** — reduces the plugin to schema-management + permission-bridge + nothing-else. | [PostgREST](https://postgrest.org/) |
| **pg_graphql** | defer | Same shape as PostgREST but GraphQL surface; nice-to-have, not v0. | [supabase/pg_graphql](https://github.com/supabase/pg_graphql) |
| **Postgres RLS** (row-level security) | ADOPT as permission enforcement | Native, fast, no app-layer permission code. `CREATE POLICY tbl_select ON shepard_tables.<t> USING (auth.user_can_read(container_id))`. Pair with PostgREST and the auth model is mostly schema-driven. | [PG RLS docs](https://www.postgresql.org/docs/current/ddl-rowsecurity.html) |
| **TanStack Table** (frontend) | ADOPT as library | Headless table-grid library (MIT); pairs with Vuetify for the row UI. Already a likely fit since shepard frontend uses Composition API. | [TanStack Table](https://tanstack.com/table) |

The **PostgREST + RLS combination is the leverage move** — folds two-thirds of the plugin into substrate config + a permission-policy bootstrap. Worth a full reuse-survey design pass.

### G. Recommendations + v0 scope + backlog rows

**Design recommendation (3 bullets):**
1. **Substrate**: TS Postgres (shared instance), `shepard_tables` schema, one PG table per container, slugified human-readable name (per memory). DDL via `ALTER TABLE` on FieldDef changes (warning on column drop — Grafana dashboards break).
2. **SPI hook**: extend `PayloadKind` with `TableQueryCapability` (declares filter/sort/page shape) + `RowMutationCapability`. Plus the **PostgREST adoption** would let the plugin contribute *zero* JAX-RS endpoints for record CRUD — the gateway proxies to PostgREST with the auth filter. This is a structurally cheaper plugin.
3. **Sidecar**: none new; document the `shepard_tables` schema + `shepard_tables_ro` role bootstrap in the plugin's V1.0.0 migration.

**v0 scope** (the MVP):
- Ship `aidocs/data/<NN>-table-container-design.md` SSOT design doc lifting the memory content + Postgres-vs-separate decision + PostgREST adoption survey + RLS auth strategy. **Doc-first.** No code.
- After doc lands, TB1a = backend skeleton (Neo4j `:TableContainer` + `:FieldDef` + `:TableReference` + DDL service + a single migration that creates the `shepard_tables` schema + `shepard_tables_ro` role).
- TB1b = REST records surface (or the PostgREST proxy decision per the doc).
- TB1c = frontend TanStack Table view component.
- TB1d = Grafana recipe doc.

**Backlog rows to file:**
- `PLUGIN-TABLES-AUDIT-2026-05-24-001` — Ship SSOT design doc `aidocs/data/<NN>-table-container-design.md` (doc-first; consolidates `project_table_container.md` memory + epic-roadmap §398 + ecosystem catalog).
- `PLUGIN-TABLES-AUDIT-2026-05-24-002` — Reuse-first survey: PostgREST + pg_graphql + Postgres RLS as alternatives to in-tree CRUD code. Decide adoption shape in the SSOT doc.
- `PLUGIN-TABLES-AUDIT-2026-05-24-003` — Umbrella TB1 series (TB1a backend skeleton, TB1b REST, TB1c frontend, TB1d Grafana recipe). Renames the missing TB1 referenced obliquely in `aidocs/40 §140`.
- `PLUGIN-TABLES-AUDIT-2026-05-24-004` — Cross-substrate question: collapse PG instances (TS + Spatial + Tables → one) per the consistent answer with spatial finding #4.

---

## HDF5 (A5a + A5b + A5d shipped; A5c + A5e queued)

### A. Current state

**Backend:** A5a, A5b, A5d shipped (per `aidocs/16` rows). Tree under `plugins/hdf5/src/main/java/`:
- `HdfPluginManifest` + `HdfPayloadKind` (same ADR-0023+0024 pattern as spatial).
- `data/hdf/{entities,daos,services,io,hsds,permissions}` — `HdfContainer` Neo4j entity, `HdfContainerDAO`, `HdfContainerService`, `HsdsClient` (Java 21 `java.net.http.HttpClient` wrapper), `HdfPermissionBridge` (observes `PermissionsChangedEvent`, maps Reader/Writer/Manager → HSDS ACLs).
- `v2/hdf/resources/HdfContainerRest` — `/v2/hdf-containers/*` (create/read/delete/download — A5d shipped the byte-identical export via HSDS).
- `v2/admin/hdf/HdfAdminRest` — `POST /v2/admin/hdf/rebuild-acls` (A5b drift recovery).
- Tests: 53+ tests across all layers (per row A5b notes).
- Migration: `V25__Add_appId_constraint_HdfContainer.cypher` (in backend, not plugin — predates the plugin extraction).

**Frontend:** none (no `frontend/components/*hdf*` or `frontend/pages/*hdf*`). Verified by `find frontend/components frontend/pages -iname '*hdf*'` returning empty.

**Aidocs:**
- `aidocs/data/35-hdf5-hsds-implementation-design.md` — canonical design (architecture decision: HSDS sidecar). Stage `feature-defined`.

**Backlog rows:**
- A5 umbrella, A5a/b/d shipped, A5c queued (`HdfReference` per-DataObject anchor at dataset path), A5e queued (auth bridge: short-lived JWT minted via `/api-keys/{id}/hsds-token` for h5pyd ergonomics).

**Stage:** mostly-shipped; missing the per-DataObject reference + the auth bridge + the entire frontend.

### B. SPI contract fit

Same two-SPI pattern as spatial:
- `HdfPayloadKind name() = "hdf5"`, `entityPackages()` = `HdfContainer` package only (no reference yet — A5c will add one).
- `HdfPluginManifest` registers with `PluginRegistry`.

**Gap:** the `HsdsClient` is a thick wrapper that's **not behind any SPI seam**. `HdfContainerService` couples directly to it. This is fine for now (one backend, one client) but if a second HDF5 surface emerges (e.g. local `h5py` library mode for a thin operator install without HSDS sidecar), the right move is a `Hdf5Backend` interface with `HsdsBackend` as default impl. **Defer** — no second backend planned.

**Substrate decided** (`aidocs/35`): HSDS sidecar with POSIX storage by default; S3 / Azure opt-in. The future `file-s3` integration (route HSDS to Garage) is a natural composition but currently unwired.

**Streaming**: `A5d` `HsdsClient.exportFile` is a streaming `InputStream` with Range-request pass-through. Correct shape for large HDF5 files (10s of GB common in CFD / atmospheric).

**Auth + permissions:** `HdfPermissionBridge` is the right shape — shepard is source of truth, HSDS mirrors. The bridge is **best-effort with in-memory retry**. Failure mode: bridge crashes → next call against HSDS hits a stale ACL. The `POST /v2/admin/hdf/rebuild-acls` admin endpoint recovers. This pattern works for the 80% case; A5e (token relay) becomes essential when `h5pyd` clients want direct HSDS access without shepard proxy.

### C. Sibling-substrate decisions

**HSDS storage backend**: currently POSIX (host volume `./hsds-storage:/data`). The right long-term answer is **Garage** (via `S3_GATEWAY` env on HSDS) — but that requires Garage to be live, which is gated on the in-flight `garage-and-docker-stack-audit-2026-05-24.md` decision. Cross-ref: when Garage is GA per ADR-0024, HSDS storage flip is one env-var change (`HSDS_AWS_S3_GATEWAY=http://garage:3900`). **No data migration needed** if HSDS is restarted with empty Garage bucket and existing POSIX files re-uploaded; or `s3 sync` from POSIX to Garage and switch.

**Index in Neo4j**: today `HdfContainer` carries `name`, `hsdsDomain`, `appId`. **A5c** will add `HdfReference` carrying `datasetPath` (e.g. `/group1/temperature[0:1000, :]`). The right index for "what HDF dataset paths does this DataObject point at" is a Neo4j `HdfReference.datasetPath` btree (Cypher: `CREATE INDEX FOR (n:HdfReference) ON (n.datasetPath)`). Add to the A5c migration when it ships.

**HSDS vs. h5wasm** (browser-side HDF5 reader): for the frontend viewer, the right answer is **HSDS over the network for browse + h5wasm for in-browser slice preview**. h5wasm is WASM (~3MB gzipped), reads HDF5 from `ArrayBuffer`. Pattern: user clicks "preview this dataset" → frontend fetches a small slice via HSDS REST → renders with h5wasm + Plotly. **Strong recommend** — keeps the frontend independent of HSDS browse-tree fidelity.

### D. Docs trinity status

All three present under `plugins/hdf5/docs/` (per DOCS-3A8 backfill):
- `reference.md`, `quickstart.md`, `install.md` — all marked `🤖 BACKFILL`. Source-derived, content-accuracy follow-up = `PLUGIN-DOC-hdf5-AUDIT` (already queued).

### E. Sidecar declarations

**Gap.** Same as spatial. `HdfPluginManifest.sidecars()` not overridden. `shepard-hsds` sidecar lives in `infrastructure/docker-compose.yml` under the `hdf` profile (verified — lines 110+ of compose, `hdfgroup/hsds:v0.9.5`, mount `./hsds-storage:/data`).

**Required shape:**
```java
@Override
public List<SidecarSpec> sidecars() {
  return List.of(
    new SidecarSpec(
      "shepard-hsds",
      "hdfgroup/hsds:v0.9.5",
      Map.of(
        "BUCKET_NAME", "${HSDS_BUCKET_NAME:-shepard}",
        "HSDS_USERNAME", "${HSDS_USERNAME:-admin}",
        "HSDS_PASSWORD", "${HSDS_PASSWORD:-admin}",
        "LOG_LEVEL", "${HSDS_LOG_LEVEL:-INFO}"
      ),
      List.of(new VolumeSpec("hsds-storage", "/data")),
      List.of(/* port 5101 if exposed externally */),
      new HealthcheckSpec(
        new String[] {"CMD-SHELL", "curl --fail --silent http://localhost:5101/about || exit 1"},
        Duration.ofSeconds(15), 5
      ),
      Optional.empty()
    )
  );
}
```

### F. Reuse-first survey

| Component | Adopt as | Why | Cite |
|---|---|---|---|
| **HSDS** | sidecar (adopted) | The deliverable IS h5pyd parity per `aidocs/35`. HSDS is the H5 Group's official REST server. | [HDFGroup/hsds](https://github.com/HDFGroup/hsds) |
| **h5pyd** | client library (Python) | Drop-in replacement for `h5py` over HSDS; the canonical client. Already cited as the deliverable in A5. | [HDFGroup/h5pyd](https://github.com/HDFGroup/h5pyd) |
| **h5wasm** | frontend library (adopt for browse) | WASM HDF5 reader (~3MB). Renders HDF5 datasets in browser without round-trip to HSDS. Pairs with Plotly for variable preview. | [usnistgov/h5wasm](https://github.com/usnistgov/h5wasm) |
| **Pyodide + h5py** | NOT adopt | ~40MB; h5wasm is the leaner answer for in-browser HDF5. Pyodide is the move when full Python execution is needed (separate concern). | — |
| **JHDF** (`jamesmudd/jhdf`) | NOT adopt | Pure-Java HDF5 reader. Was a fork in the original design (architecture option (i)) — rejected per `aidocs/35 §2`. Bypassing HSDS for in-process reads would lose h5pyd parity. | [jamesmudd/jhdf](https://github.com/jamesmudd/jhdf) |
| **nexpy / nexusformat** | reference reading | NeXus is HDF5 with a metadata convention (neutron / X-ray / muon science). Could be a future `shepard-plugin-nexus` if a DLR group needs NeXus convention validation; orthogonal to A5. | [nexpy/nexusformat](https://github.com/nexpy/nexusformat) |

### G. Recommendations + v0 scope + backlog rows

**Design recommendation (3 bullets):**
1. **Substrate**: HSDS sidecar (decided + shipped). When Garage GA-s per ADR-0024, flip `HSDS_AWS_S3_GATEWAY` to point at Garage; no app code change.
2. **SPI hook**: no new SPI hook needed today; defer `Hdf5Backend` interface until a second HDF5 surface emerges. The PM1f sidecar declaration is the immediate gap.
3. **Sidecar**: implement `sidecars()` per PM1f; remove `shepard-hsds` from central compose.

**v0 scope** (MVP unblocking the frontend gap):
- PM1f sidecar declaration (per §E).
- Unblock A5c (`HdfReference` + Annotation hookup via E6). Adds the per-DataObject anchor at a dataset path.
- Frontend: `HdfContainerBrowseTree.vue` (groups/datasets/attributes tree from HSDS REST) + `HdfDatasetPreview.vue` (h5wasm-rendered slice preview + Plotly chart). Mounts under `frontend/pages/containers/hdf5/[containerId]/`.

**Backlog rows to file:**
- `PLUGIN-HDF5-AUDIT-2026-05-24-001` — PM1f sidecar migration (shepard-hsds declaration; remove from central compose).
- `PLUGIN-HDF5-AUDIT-2026-05-24-002` — Frontend `HdfContainerBrowseTree` + `HdfDatasetPreview` using h5wasm + HSDS REST.
- `PLUGIN-HDF5-AUDIT-2026-05-24-003` — Garage storage backend flip (gated on `garage-and-docker-stack-audit` ADR-0024 GA).
- `PLUGIN-HDF5-AUDIT-2026-05-24-004` — A5c unblock (per-DataObject `HdfReference` at dataset path); already queued as A5c, this row is a redundant pointer to keep cross-feed traceability.
- `PLUGIN-HDF5-AUDIT-2026-05-24-005` — A5e unblock (h5pyd token bridge); already queued as A5e.

---

## Cross-cutting findings (feeds synthesis)

### 1. PM1f sidecar declaration is the headline gap

Both shipped plugins in this audit (spatial, hdf5) skip the PM1f
`sidecars()` declaration. `file-s3` (the third PM1f-aware plugin)
implements it correctly. The sidecars exist in
`infrastructure/docker-compose.yml` under feature profiles
(`spatial`, `hdf`) — operational, but the **plugin → sidecar map is
not machine-readable**, which is exactly what
`feedback_plugins_declare_sidecars.md` calls out as tech debt.

**Pattern that should hold across all 3:**
- Every plugin needing infra declares it in `sidecars()`.
- The central `infrastructure/docker-compose.yml` contains only
  shepard-core services (backend, frontend, Neo4j, Postgres+TS, Mongo,
  Keycloak).
- Deploy automation assembles compose from `active plugins ×
  sidecars()` declarations.

**Backlog row** (file in `aidocs/16`): `PM1f-MIGRATION-SPATIAL-HDF5-2026-05-24` — sweep both plugins to declare sidecars; remove `postgis` + `shepard-hsds` from central compose. Effort: S; tests: cross-ref existing `FileS3PluginManifestSidecarsTest`.

### 2. SPI gaps all three will hit

- **`PayloadKind` is too thin for queryable kinds.** Spatial has bbox/KNN/DWithin; Tables has SQL-shape filter/sort/page; HDF5 has dataset-slice. Each plugin reaches around the SPI with kind-specific REST shapes. A `QueryCapability` declaration (optional, default-no-op) on `PayloadKind` would let downstream consumers (search, MCP tools, OGC export, view recipes) target plugins generically. Defer until two of three plugins want it; today only Spatial+Tables would benefit, and Tables doesn't exist yet.
- **No `FrontendContribution` SPI.** Plugins ship backend; the frontend is a hand-coded sibling. Future shape: plugin ships a frontend bundle (Vue SFC + a manifest declaring "this kind needs a viewer component"); shepard frontend dynamic-imports the bundle. **Out of scope** for this audit but worth a separate design dispatch (`SPI-FRONTEND-CONTRIBUTION-2026-05-24`).
- **No `RoCrateContribution` SPI hook used by these plugins.** The SPI defines `contributeToCrate(builder, ref)` (`aidocs/47 §2.2`) but spatial + hdf5 don't override it; their references export as opaque file pointers. Reuse-the-existing-SPI gap; same backlog row as the broader `VIEWS-AS-SHAPES-EXPORT-MAPPING` (already queued).

### 3. Substrate decisions affecting the existing Postgres / Garage / Neo4j stack

- **Postgres-multitenancy decision** (Spatial finding 004 + Tables finding 004): three plugins want Postgres (TS, Tables, Spatial). Currently two PG instances (TS-Postgres + postgis); adding Tables as a third instance would be the wrong shape. **Recommend** consolidating to one Postgres + three schemas + extensions enabled (`timescaledb`, `postgis`). PgBouncer pool-per-schema. Cross-ref: in-flight `pgbouncer-audit` (TBD).
- **HSDS storage on Garage** (HDF5 finding 003): once Garage GA-s per ADR-0024, HSDS flips from POSIX to Garage via env-var. Zero app code change. Cross-ref: `garage-and-docker-stack-audit-2026-05-24.md`.
- **No new Neo4j entity packages** beyond what each plugin already declares via `PayloadKind.entityPackages()`. The SPI pattern is working for Neo4j.

### 4. Test coverage pattern the SPI should require

`file-s3` ships `FileS3PluginManifestTest` + `FileS3PluginManifestSidecarsTest`. Spatial + HDF5 have rich service / DAO / REST tests but no manifest-level test. **Pattern**: every plugin manifest should ship a test asserting `id()` / `version()` / `shepardCompatibility()` / `sidecars()` shape. Should be **enforced via a base test class** (`AbstractPluginManifestTest<T extends PluginManifest>`) plugins extend. **Backlog**: `PLUGIN-MANIFEST-TEST-BASECLASS-2026-05-24` — file in `aidocs/16`.

### 5. Frontend gap is universal

None of the three plugins owns its frontend. Spatial has a pages stub; HDF5 has nothing; Tables has nothing. Compounded by gap #2 (no `FrontendContribution` SPI). For now: each plugin's frontend lives in the main `frontend/` tree. Long-term: per-plugin frontend bundles. Track as `SPI-FRONTEND-CONTRIBUTION-2026-05-24` (filed in cross-cutting).

---

## Source citations

- `aidocs/platform/47-dev-experience-and-plugin-system.md` §2 — PayloadKind / PluginManifest SPI
- `aidocs/data/35-hdf5-hsds-implementation-design.md` — HSDS architecture decision
- `aidocs/data/81-spatial-data-binding.md` — DataBinding model
- `aidocs/data/82-spatial-perf-evaluation.md` — BRIN + GIN + TimescaleDB recommendations
- `aidocs/data/83-pointcloud-and-live-overlay.md` — point clouds → `shepard-plugin-cad`, NOT spatial
- `aidocs/platform/63 ADR-0023` — plugin extraction pattern (two-pass build)
- `aidocs/platform/63 ADR-0024` — Garage as S3 substrate
- `plugins/file-s3/src/main/java/.../FileS3PluginManifest.java` — reference impl of `sidecars()`
- Memory `project_table_container.md` — TableContainer design decisions
- Memory `feedback_plugins_declare_sidecars.md` — sidecar-decl rule
- Memory `feedback_reuse_before_reimplement.md` — reuse-first rule

External (one each, per advisor calibration):
- PostGIS — https://postgis.net/
- MapLibre GL JS — https://maplibre.org/
- HSDS — https://github.com/HDFGroup/hsds
- h5pyd — https://github.com/HDFGroup/h5pyd
- h5wasm — https://github.com/usnistgov/h5wasm
- Teable (architecture inspiration, AGPL — do not embed) — https://github.com/teableio/teable
- PostgREST — https://postgrest.org/
- Postgres RLS — https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- TanStack Table — https://tanstack.com/table
