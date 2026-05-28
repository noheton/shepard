# shepard-plugin-fileformat-thermography

**Status:** alpha (tier-1 only) · standalone-build · MFFD-demo enabling

Tier-1 metadata parser for Edevis OTvis active lock-in thermography
measurements. Reads an uploaded `.OTvis` tar archive, extracts the
`content.xml` manifest and the `S<n>_M<n>_L<n>_F<n>` filename grid
pattern, and emits SemanticAnnotations on the existing FileReference +
parent DataObject.

**Scope (tier-1):**
- Parse POSIX-tar `.OTvis` → `content.xml` (UTF-16 LE XML).
- Emit ~15 canonical annotations under `urn:shepard:thermography:*` +
  `urn:shepard:mffd:*` (see `docs/reference.md`).
- Filename pattern `S<n>_M<n>_L<n>_F<n>.OTvis` → grid-position
  annotations on the parent DataObject.

**Out of scope (tier-1):**
- Frame extraction from `sequence0/f0.bin` and `sequence1/*` (tier-2,
  filed as `OTVIS-PARSE-2` in `aidocs/16-dispatcher-backlog.md`).
- `.diproj` project-manifest parsing — deliberately ignored per the
  DO-sprawl containment rule in
  `aidocs/integrations/114-process-monitoring-parser-plugin.md §0`.
- DataObject and container creation — tier-1 only enriches existing
  entities via the `AnnotationWriter` callback.

## Building

This module is **standalone**: its `pom.xml` is deliberately NOT
referenced by `/opt/shepard/plugins/pom.xml` or `/opt/shepard/backend/pom.xml`.
The aggregator wire-up is a follow-up row
(`OTVIS-WIRE-AGGREGATOR-1`) deferred until the Quarkus / Jandex
`CompositeIndex.getClassByName` infinite-loop hang in the main backend
build is resolved.

```sh
cd plugins/fileformat-thermography
mvn package
```

Build artefact: `target/shepard-plugin-fileformat-thermography-0.1.0-SNAPSHOT.jar`.

## Testing

```sh
cd plugins/fileformat-thermography
mvn test
```

The integration test loads the real-world fixture
`src/test/resources/sample_S4_M13_L18_F4.OTvis` (8.4 MB tar archive,
DLR ZLP Augsburg MFFD upper-shell campaign 2024) and asserts the full
tier-1 annotation set lands on a recording `AnnotationWriter`.

Current snapshot: **27 tests, 0 failures**.

## SPI shim

The canonical `FileParserPlugin` interface lives in the main backend
(`de.dlr.shepard.spi.fileparser.FileParserPlugin`, sketched in
`aidocs/integrations/110 §3`). Because this module is standalone, we
declare a local minimal copy in
`de.dlr.shepard.plugin.fileformat.thermography.FileParserPlugin`. When
the backend Jandex hang clears and this module is wired into the main
aggregator (`OTVIS-WIRE-AGGREGATOR-1`), the local shim is deleted and
`OTvisParser` is rebound to the real interface via a
`META-INF/services/` descriptor.

## Reference

- `aidocs/integrations/114-process-monitoring-parser-plugin.md` —
  design doc covering the full thermography + thermal-analysis parser
  family (OTvis, FLIR, IRBIS, NETZSCH `.nxpdea`).
- `aidocs/integrations/110-file-format-parser-plugin.md` — the parser
  SPI baseline this plugin implements.
- `docs/reference.md`, `docs/quickstart.md`, `docs/install.md` — this
  plugin's user-facing documentation.
