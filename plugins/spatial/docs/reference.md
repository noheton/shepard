# Plugin: spatial — PostGIS Spatial Data

Adds a PostGIS-backed spatial-data payload kind to shepard, storing
geographic point observations and references in a dedicated PostgreSQL
datasource.

## What it does

Registers the `spatial` payload kind via the `PayloadKind` SPI and
wires up the following CDI beans discovered at Quarkus build time:

- `SpatialDataContainerService` / `SpatialDataPointService` /
  `SpatialDataReferenceService` — domain logic
- `SpatialDataPointRest` / `SpatialDataReferenceRest` — REST endpoints
- `SpatialDataPointRepository` — JPA persistence via PostGIS-enabled
  PostgreSQL
- `SpatialDataContainer` / `SpatialDataReference` — Neo4j-OGM entities
  registered in the core graph alongside other payload kinds

Endpoints follow the standard shepard container pattern under
`/shepard/api/` (upstream-compat surface).

## Config keys

| Key | Default | Description |
|-----|---------|-------------|
| `shepard.plugins.spatial.enabled` | `true` | Gates the plugin lifecycle hook in `GET /v2/admin/plugins`. |
| `quarkus.datasource."spatial".db-kind` | — | Must be `postgresql`. |
| `quarkus.datasource."spatial".jdbc.url` | — | JDBC URL to a PostGIS-enabled PostgreSQL instance, e.g. `jdbc:postgresql://postgis:5432/spatial`. |
| `quarkus.datasource."spatial".username` | — | Database user. |
| `quarkus.datasource."spatial".password` | — | Database password. |

The `"spatial"` named datasource is separate from the main shepard
datasource; it must point to a PostgreSQL server with the `postgis`
extension installed.

## How to enable

1. Start a PostGIS-enabled PostgreSQL instance (the `spatial` compose
   profile in `infrastructure/` provides one).
2. Set the `quarkus.datasource."spatial".*` keys in
   `application.properties` or as environment variables.
3. Include `shepard-plugin-spatial` on the backend classpath (bundled in
   the `with-plugins` Maven profile).

Verify via:
```
GET /v2/admin/plugins   # should include { "id": "spatial", "version": "1.0.0-SNAPSHOT" }
```
