---
title: spatiotemporal — Reference
stage: deployed
last-stage-change: 2026-05-27
audience: power-user, plugin-author
---

# Plugin: spatiotemporal — PostGIS + TimescaleDB Spatiotemporal Data

Adds a PostGIS + TimescaleDB-backed spatiotemporal payload kind to shepard.
Stores geographic point observations and time-varying engineering geometry
profiles (AFP sweeps, robot paths, NDT line scans) in the TimescaleDB instance
with PostGIS co-located (SPATIAL-V6-001, aidocs/data/90).

## What it does

Registers the `spatiotemporal` payload kind via the `PayloadKind` SPI and
wires up the following CDI beans discovered at Quarkus build time:

- `SpatialDataContainerService` / `SpatialDataPointService` /
  `SpatialDataReferenceService` — domain logic
- `SpatialDataPointRest` / `SpatialDataReferenceRest` — REST endpoints
- `SpatialDataPointRepository` — JPA persistence via PostGIS-enabled
  TimescaleDB (co-located)
- `SpatialDataContainer` / `SpatialDataReference` — Neo4j-OGM entities
  registered in the core graph alongside other payload kinds
- `GeoTimeVocabularyProvider` — `SemanticVocabularyProvider` SPI bean
  contributing GeoSPARQL + OWL-Time predicates to the annotation picker

Endpoints follow the standard shepard container pattern under
`/shepard/api/` (upstream-compat surface).

## Schema — v6 green-field (SPATIAL-V6-001)

The v6 schema lives in the `shepard_spatial` schema on TimescaleDB. It is
shipped as Flyway migration `V2.0.0__green_field_schema.sql` and runs
automatically on backend startup.

### Tables

| Table | Description |
|-------|-------------|
| `shepard_spatial.profile_container` | Per-container metadata: `container_id`, `coord_frame_app_id`, `profile_kind_allowed`, optional `measurement_schema_appid`, `created_at_ms`, `retired_at_ms`. |
| `shepard_spatial.profile` | TimescaleDB hypertable. Columns: `container_id`, `time` (epoch ns), `profile_kind`, `anchor` (POINTZ), `profile` (geometry), `measurements` (JSONB), `metadata` (JSONB), `orientation` (JSONB), `seq`. |

### Hypertable parameters

- Time column: `time` (epoch nanoseconds)
- Chunk interval: 86,400,000,000,000 ns (1 day)
- Space partitions: 4 by `container_id`
- Compression: `compress_segmentby='container_id'`, `compress_orderby='time ASC, seq ASC'`, 7-day delay
- No default retention policy (per SM1a infinite-grace rule)

### Profile kinds

| `profile_kind` | `profile` geometry | Use cases |
|---|---|---|
| `point` | NULL | Robot TCP position, single-point observations |
| `line` | ST_LineString Z | AFP head sweep, ultrasonic B/C/D-scan probe path, eddy-current sweep |
| `polygon` | ST_Polygon Z | AFP compaction roller footprint, debulk patch |
| `multipoint` | ST_MultiPoint Z | Lidar slice, structured-light fringe, AE hit localisation |
| `tin` | ST_PolyhedralSurface / ST_Tin / ST_TIN Z | CT slice, DIC strain field |
| `tube_centerline` | ST_LineString Z | Robot welding torch centerline |

## Config keys

| Key | Default | Description |
|-----|---------|-------------|
| `shepard.plugins.spatiotemporal.enabled` | `false` | Gates the plugin lifecycle hook in `GET /v2/admin/plugins`. |
| `quarkus.datasource."spatial".db-kind` | — | Must be `postgresql`. |
| `quarkus.datasource."spatial".jdbc.url` | — | JDBC URL to the TimescaleDB instance, e.g. `jdbc:postgresql://timescaledb:5432/postgres`. |
| `quarkus.datasource."spatial".username` | — | Database user. |
| `quarkus.datasource."spatial".password` | — | Database password. |

Note: the Quarkus datasource qualifier is `"spatial"` (not `"spatiotemporal"`) for
backward compatibility.

## How to enable

1. Ensure the `timescaledb-postgis` Docker image is built
   (`docker compose build timescaledb` from `infrastructure/`).
2. Set the `quarkus.datasource."spatial".*` keys in `application.properties`
   or as environment variables.
3. Include `shepard-plugin-spatiotemporal` on the backend classpath (bundled in
   the `with-plugins` Maven profile).

Verify via:
```
GET /v2/admin/plugins   # should include { "id": "spatiotemporal", "version": "2.0.0-SNAPSHOT" }
```

## Vocabulary contribution — GeoTimeVocabularyProvider (SEMA-V6-009)

The plugin ships a `GeoTimeVocabularyProvider` CDI bean
(`@ApplicationScoped`) that implements the `SemanticVocabularyProvider`
SPI. At startup, `SemanticVocabularyRegistry` discovers it and makes
its predicate definitions available to annotation pickers, SHACL shape
generation, and SPARQL autocompletion.

### Vocabulary namespace

`http://www.opengis.net/ont/geosparql#` (GeoSPARQL)

### Predicates contributed

| Predicate IRI | Label | Expected value type | Description |
|---------------|-------|---------------------|-------------|
| `geosparql:hasGeometry` | has Geometry | IRI | Links a spatial feature to its geometry representation. |
| `geosparql:asWKT` | as WKT | LITERAL | WKT (Well-Known Text) serialisation of a geometry. |
| `geosparql:sfWithin` | Simple-Features Within | IRI | Topological containment (DE-9IM sfWithin). |
| `time:hasBeginning` | has Beginning | IRI | OWL-Time: start instant of a temporal entity. |
| `time:hasEnd` | has End | IRI | OWL-Time: end instant of a temporal entity. |

`geosparql:` = `http://www.opengis.net/ont/geosparql#`
`time:` = `http://www.w3.org/2006/time#`

### No extra configuration required

The vocabulary provider activates automatically whenever the spatiotemporal
plugin is on the classpath. No additional config keys are needed.
The `SemanticVocabularyRegistry` logs a summary line at startup:

```
SemanticVocabularyRegistry: discovered 1 vocabulary provider(s): [http://www.opengis.net/ont/geosparql#]; 5 total predicate(s)
```
