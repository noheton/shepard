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

## SPATIAL-V6-004 additions

### BrushTraceViewRecipeRenderer

Registered via `META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer`.

Handles VIEW_RECIPE templates with shape IRI
`https://shepard.dlr.de/ontology/BrushTraceShape`. The template body
must carry `traceSourceAppId` (the UUID v7 appId of the
`SpatialDataContainer`).

**Supported properties (template body JSON keys):**

| Key | Required | Description |
|-----|----------|-------------|
| `traceSourceAppId` | yes | UUID v7 appId of the SpatialDataContainer to render. |
| `brushMode` | no | `"point"` \| `"line"` \| `"tube"` \| `"ruled-surface"` (default `"ruled-surface"`, v0 renders as line). |
| `valueChannel` | no | JSONB key in `measurements` for coloring (v0 ignored). |
| `gradientStops` | no | Color map name e.g. `"viridis"` (v0 uses white line). |

**Response binding on OK:**
```json
{
  "role": "traceSource",
  "channelSelector": "traceSourceAppId",
  "status": "OK",
  "resolved": { "channelRef": "<traceSourceAppId>" }
}
```

**Response binding on MISSING** (when `traceSourceAppId` is absent or blank):
```json
{
  "role": "traceSource",
  "channelSelector": "traceSourceAppId",
  "status": "MISSING",
  "resolved": null
}
```

### GET /v2/spatial-containers/{appId}/trace

Returns the spatial data points for a container addressed by its UUID v7 `appId`.
The response shape is identical to `GET /shepard/api/spatial-data-containers/{id}/payload`.

**Auth:** `@RolesAllowed("authenticated")`  
**Feature gate:** `shepard.spatial-data.enabled=true` (same as v1)

**Parameters:**

| Name | In | Required | Description |
|------|----|----------|-------------|
| `appId` | path | yes | UUID v7 appId of the SpatialDataContainer. |
| `limit` | query | no | Maximum number of points (default 5000). |

**Example request:**
```
GET /v2/spatial-containers/0190adef-1234-7000-0000-000000000001/trace?limit=1000
Authorization: Bearer <token>
```

**Example response (200 OK):**
```json
[
  {
    "timestamp": 1748000000000000000,
    "x": 100.5,
    "y": 200.3,
    "z": 15.0,
    "measurements": { "temperature": { "val": 280.0 } },
    "metadata": { "track": 1, "layer": 4 }
  }
]
```

**Error responses:**

| Code | Cause |
|------|-------|
| 401 | Not authenticated. |
| 403 | No read permission on the container. |
| 404 | No `SpatialDataContainer` found with this `appId`. |
| 503 | Spatial database unreachable. |
