---
title: spatiotemporal — Reference
stage: deployed
last-stage-change: 2026-06-10
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
| `shepard.plugins.spatiotemporal.enabled` | **`true`** | Gates the plugin lifecycle hook in `GET /v2/admin/plugins`.  Default-on since 2026-05-28 (SPATIAL-V6-001 is the v6 flagship payload kind).  Set to `false` to opt out.  The legacy v5 `SHEPARD_SPATIAL_DATA_ENABLED` env gate has been removed. |
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

## NDT orientation extensions

### Beam-steering NDT orientation extensions

The `orientation` JSONB field on profiles (notably `line`-kind profiles used for
ultrasonic B/C/D-scan probe paths) accepts two optional sub-schemas for phased-array
and time-of-flight diffraction instruments:

**PAUT (Phased-Array Ultrasound Testing):**
```json
{
  "pose": { "qx": 0, "qy": 0, "qz": 0, "qw": 1 },
  "beamSteer": { "angleDeg": 45.0, "skewDeg": 0.0 }
}
```

**TOFD (Time-of-Flight Diffraction):**
```json
{
  "pose": { "qx": 0, "qy": 0, "qz": 0, "qw": 1 },
  "pairOffsetMm": {
    "transmitter": [0, -15, 0],
    "receiver": [0, 15, 0]
  }
}
```

No schema migration is required — `orientation` is JSONB (open schema). These conventions
are enforced by the `MFFDUTAScanShape` SHACL companion shape. A future validator can
require `beamSteer` when `scanMode = "PAUT"` and `pairOffsetMm` when `scanMode = "TOFD"`.

## Endpoints

All endpoints live on the upstream-compat surface (`/shepard/api/`).
The `{containerId}` path parameter is the Neo4j `id` long returned by the container
create/list responses.

### Container management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/shepard/api/spatialDataContainers` | List all accessible containers (paginated). |
| `GET` | `/shepard/api/spatialDataContainers/{containerId}` | Get a single container. |
| `POST` | `/shepard/api/spatialDataContainers` | Create a new `SpatialDataContainer`. |
| `DELETE` | `/shepard/api/spatialDataContainers/{containerId}` | Delete a container and all its data. |
| `GET` | `/shepard/api/spatialDataContainers/{containerId}/permissions` | Read permissions. |
| `PUT` | `/shepard/api/spatialDataContainers/{containerId}/permissions` | Update permissions. |
| `GET` | `/shepard/api/spatialDataContainers/{containerId}/roles` | Get caller's roles on the container. |

### Payload (spatial data points)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/shepard/api/spatialDataContainers/{containerId}/payload` | Query points; supports geometry, metadata, measurement, and time filters (see query parameters below). |
| `POST` | `/shepard/api/spatialDataContainers/{containerId}/payload` | Ingest a batch of `SpatialDataPoint` objects. |

#### Query parameters for `GET .../payload`

| Parameter | Type | Description |
|-----------|------|-------------|
| `geometryFilter` | JSON string | One of `AXIS_ALIGNED_BOUNDING_BOX`, `BOUNDING_SPHERE`, `K_NEAREST_NEIGHBOR`, `ORIENTED_BOUNDING_BOX`. |
| `metadataFilter` | JSON string | Key-value exact-match filter on the `metadata` JSONB field. |
| `measurementsFilter` | JSON array string | Array of `{key, operator, value}` conditions on the `measurements` JSONB field. |
| `startTime` | Long (ns) | Inclusive start timestamp in nanoseconds since epoch. |
| `endTime` | Long (ns) | Inclusive end timestamp in nanoseconds since epoch. |
| `limit` | Integer | Maximum number of points to return. |
| `skip` | Integer | Stride — returns every nth point by modulo on point id. |

### Context reference (DataObject anchor)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/shepard/api/collections/{cId}/dataObjects/{doId}/spatialDataReferences` | List spatial references on a DataObject. |
| `POST` | `/shepard/api/collections/{cId}/dataObjects/{doId}/spatialDataReferences` | Create a `SpatialDataReference` linking a DataObject to a container. |
| `GET` | `/shepard/api/collections/{cId}/dataObjects/{doId}/spatialDataReferences/{refId}` | Get a single reference. |
| `DELETE` | `/shepard/api/collections/{cId}/dataObjects/{doId}/spatialDataReferences/{refId}` | Remove a reference. |

> **Note:** the `/shepard/api/…/spatialDataReferences` + `/shepard/api/spatialDataContainers`
> numeric-id resources above are **frozen upstream-byte-compat** (they appear in
> `openapi-5.4.0.json`). Third-party upstream clients depend on them; they are
> not extended. The fork's own callers (frontend, importer, MCP) use the
> `/v2/` surface below.

### Unified reference surface — `kind=spatial` (SPATIAL-UNIFY)

Spatial data is a reference kind on the unified `/v2/references` surface,
addressed by the reference `appId` (UUID v7) like File / TimeSeries / Video.
The `SpatialDataContainer` behind a reference is a storage primitive — users
never pick a container from a list; the container `appId` is exposed only so the
viewer can resolve bytes.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v2/references?kind=spatial&dataObjectAppId={appId}` | List spatial references on a DataObject (unified `ReferenceV2IO[]`). |
| `GET` | `/v2/references/{appId}` | Get a spatial reference (the entity self-describes `kind=spatial`). |
| `POST` | `/v2/references?kind=spatial&dataObjectAppId={appId}` | Create a spatial reference bound to an existing container (`{spatialDataContainerAppId, …}`). |
| `DELETE` | `/v2/references/{appId}` | Delete a spatial reference. |
| `POST` | `/v2/spatial/promote?fileReferenceAppId={appId}` | **In-context promote** (per-file, one-click): mint a spatial reference + backing container from an eligible pointcloud/trajectory FileReference. |

The `kind=spatial` `ReferenceV2IO.payload` carries: `geometryFilter`,
`measurementsFilter`, `startTime`, `endTime`, `metadata`, `limit`, `skip`,
`spatialDataContainerAppId`, `promotionState`.

**`POST /v2/spatial/promote` — worked example.** Given a singleton FileReference
holding a pointcloud (`.las/.laz/.ply/.e57/.pcd/.xyz/.pts` or a named pointcloud/
trajectory file), Write on the parent DataObject:

```bash
curl -X POST \
  -H "X-API-KEY: $KEY" \
  "http://localhost:8080/v2/spatial/promote?fileReferenceAppId=019e7244-0000-7000-8000-000000000001"
```

Response `201` (or `200` when already promoted — the call is **idempotent**):

```json
{
  "appId": "019e7244-0000-7000-8000-000000000aaa",
  "name": "TPS 3D pointclouds.0",
  "kind": "spatial",
  "payload": {
    "spatialDataContainerAppId": "019e7244-0000-7000-8000-000000000ccc",
    "promotionState": "pending"
  }
}
```

The minted container is marked `promotionState=pending`; the Python
`spatial-importer` sidecar drains that queue and streams the points into the
PostGIS hypertable (SPATIAL-UNIFY-004-SIDECAR). The promote records a typed
`:Activity` automatically via the core `ProvenanceCaptureFilter`.

## BrushTraceShape

`BrushTraceShape` is the SHACL `sh:NodeShape` VIEW_RECIPE that drives the
Trace3D renderer for spatiotemporal sweep data (AFP layup paths, NDT probe
paths, robot TCP traces). It is defined in `aidocs/data/90 §7` and
`plugins/spatial/src/main/resources/shapes/BrushTraceShape.ttl`.

### What a BrushTraceShape renders

A `BrushTraceShape` instance is a `shepard:SemanticAnnotation` attached to a
`SpatialDataContainer`. When the vis-trace3d frontend renderer detects it, it:

1. Fetches the `shepard_spatial.profile` rows for the container in time order.
2. Stitches successive line-geometry profiles (each an `ST_LineStringZ`) into
   ruled surfaces — producing a swept-surface mesh.
3. Maps the `measurements` JSONB values to a colour gradient (e.g. TCP
   temperature, compaction force) over the mesh surface.

### Key SHACL properties

| Property | Range | Description |
|----------|-------|-------------|
| `shepard:traceSource` | IRI of a `SpatialDataContainer` | The container whose `profile` rows are rendered. |
| `shepard:valueChannel` | string | `measurements` key to map to colour (e.g. `"tcp_temp_C"`). |
| `shepard:cadOverlayAppId` | UUID string | Optional `:FileReference` appId of a CAD file to render as an overlay (requires `shepard-plugin-cad >= v1.0`). |
| `shepard:rendererUrl` | IRI | Renderer module path: `/v2/assets/views/spatial/v1/brush.mjs`. |

### Example annotation

```turtle
@prefix shepard: <http://shepard.dlr.de/ontology/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<urn:shepard:annotation:q1-thermal-trail> a shepard:BrushTraceShape ;
    shepard:traceSource    <urn:shepard:container:12345> ;
    shepard:valueChannel   "tcp_temp_C" ;
    shepard:rendererUrl    "/v2/assets/views/spatial/v1/brush.mjs" .
```

The MFFD AFP thermal trail (Q1 campaign, ply 5 TCP temperature anomaly) is the
acceptance test for this shape — see `aidocs/agent-findings/mffd-afp-spatial-analysis-cases.md`.

## Coordinate frame handshake

Every `SpatialDataContainer` has a coordinate frame declared at creation time.
The frame identity flows through two mirrored representations:

### Postgres side — `profile_container.coord_frame_app_id`

`V2.0.0__green_field_schema.sql` declares:

```sql
coord_frame_app_id  UUID  NOT NULL  -- → :CoordinateFrame.appId (CST1 / aidocs/85)
```

All geometry rows in `shepard_spatial.profile` are expressed in the coordinate
system identified by this UUID. Spatial queries (bounding-box, KNN, etc.) are
only meaningful when the query geometry is expressed in the same frame.

### Neo4j side — `[:ANCHORED_IN]->(:CoordinateFrame)` (SPATIAL-V6-006, queued)

The CST1 integration (SPATIAL-V6-006) will add a mirror Cypher edge
`(:SpatialDataContainer)-[:ANCHORED_IN]->(:CoordinateFrame)` so that graph
queries can traverse from a DataObject through its spatial reference into the
frame definition. Until SPATIAL-V6-006 ships, the `coord_frame_app_id` is a
FK-by-convention UUID column (no Cypher constraint enforced).

### Coordinate reference systems in use

| `coord_frame_app_id` value | CRS | Typical use |
|----------------------------|-----|-------------|
| WGS-84 global frame UUID | EPSG:4326 | Geographic point observations (bench positions at Lampoldshausen). |
| AFP-robot local frame UUID | Robot base frame (mm) | AFP layup sweeps; `x`/`y`/`z` in millimetres from robot base origin. |
| NDT fixture frame UUID | Fixture coordinate system (mm) | Ultrasonic B/C/D-scan probe paths. |

Operators define frames via the CST1 tree endpoints (aidocs/85). The frame UUID
returned by the CST1 create call is what goes into `coord_frame_app_id` at
container creation time.

### What happens when frames are mixed

Mixing frames without applying a transform yields wrong spatial query results.
The service layer does **not** auto-reproject. If a bounding-box filter is
expressed in WGS-84 but the container's frame is a millimetre robot frame, the
query will return incorrect (likely empty) results. Always confirm
`coord_frame_app_id` on the container matches the frame of the filter geometry.
