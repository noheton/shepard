# Install — shepard-plugin-fileformat-cad

**Status:** bundled · Tier-1 · ships with the standard backend image.

## 1. Prerequisites

- shepard backend ≥ 6.0.0-SNAPSHOT
- Maven wrapper available at `backend/mvnw`
- Java 21+

No external services required. The parser is pure-Java with no
network calls, no sidecar, and no storage dependencies.

## 2. Is it already installed?

The plugin ships bundled with the fork's standard backend image.
Check `GET /v2/admin/plugins` to confirm it is active:

```sh
curl -s https://shepard.example.com/v2/admin/plugins \
  -H "Authorization: Bearer $TOKEN" | jq '.[] | select(.id=="fileformat-cad")'
```

Expected response:

```json
{
  "id": "fileformat-cad",
  "title": "CAD file format metadata parser (STEP / 3DXML / JT / OBJ)",
  "version": "6.0.0-SNAPSHOT",
  "enabled": true
}
```

## 3. Build

The plugin is built automatically by `scripts/install-plugins.sh` as
part of both `make build-plugins` (local) and the CI workflows.

For a standalone local build:

```sh
cd plugins/fileformat-cad
../../backend/mvnw -B -DnoPlugins -Dmaven.test.skip=true install
```

To run unit tests:

```sh
cd plugins/fileformat-cad
../../backend/mvnw test
```

## 4. Compose / deployment

No compose profile changes are required. The plugin JAR is bundled
into the backend image via the `with-plugins` Maven profile:

```xml
<!-- backend/pom.xml — with-plugins profile -->
<dependency>
  <groupId>de.dlr.shepard.plugins</groupId>
  <artifactId>shepard-plugin-fileformat-cad</artifactId>
  <scope>provided</scope>
</dependency>
```

The plugin is loaded at backend startup via `ServiceLoader` from
`META-INF/services/de.dlr.shepard.plugin.fileformat.cad.FileParserPlugin`
and
`META-INF/services/de.dlr.shepard.plugin.PluginManifest`.

## 5. Config keys

None. The plugin has no deploy-time `application.properties` keys and
no runtime `:*Config` singleton.

To disable the plugin without removing it from the image:

```properties
# application.properties
shepard.plugins.fileformat-cad.enabled=false
```

Or at runtime (in-memory until restart):

```sh
shepard-admin plugins disable fileformat-cad
```

## 6. Migrations

None. The plugin writes only `:SemanticAnnotation` nodes, which already
exist in the Neo4j schema.

## 7. Known operational notes

- **Large STEP DATA sections (> 512 KB):** only the first 512 KB of the
  DATA section is scanned for `PRODUCT`, `MATERIAL`, and composite
  entities. Ply counts and fibre angles may be incomplete for very large
  AP242 assemblies.
- **STEP encoding:** the parser reads bytes as ISO-8859-1 (the standard
  encoding for STEP P21 physical files). Files with non-standard encoding
  may produce garbled annotation values.
- **3DXML security:** the XML parser runs with XXE protections enabled
  (`disallow-doctype-decl`, `external-general-entities=false`,
  `external-parameter-entities=false`). Intentionally malformed 3DXML
  files will be rejected gracefully with a partial (or empty) annotation
  set — no exceptions propagate to the caller.
