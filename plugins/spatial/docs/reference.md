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

## Vocabulary contribution — GeoTimeVocabularyProvider (SEMA-V6-009)

The plugin ships a `GeoTimeVocabularyProvider` CDI bean
(`@ApplicationScoped`) that implements the `SemanticVocabularyProvider`
SPI.  At startup, `SemanticVocabularyRegistry` discovers it and makes
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

The vocabulary provider activates automatically whenever the spatial
plugin is on the classpath.  No additional config keys are needed.
The `SemanticVocabularyRegistry` logs a summary line at startup:

```
SemanticVocabularyRegistry: discovered 1 vocabulary provider(s): [http://www.opengis.net/ont/geosparql#]; 5 total predicate(s)
```
