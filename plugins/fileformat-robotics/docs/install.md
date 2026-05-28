---
title: fileformat-robotics — install
stage: feature-defined
last-stage-change: 2026-05-28
audience: operators
---

# Install — shepard-plugin-fileformat-robotics

## Prerequisites

- JDK 21+ (the plugin compiles with `maven.compiler.release=21`).
- Shepard backend with the `FileParserPlugin` SPI registered. Until
  the canonical SPI lands (gated on the backend Jandex hang), the
  parser ships with a local interface shim and is invoked via direct
  bean wiring; see "Wire-up" below.

No runtime dependencies beyond the JDK — zlib decompression uses
`java.util.zip.Inflater`, the string walker uses raw `byte[]`.

## Build + install

```bash
cd plugins/fileformat-robotics
../../backend/mvnw package
```

This produces `target/shepard-plugin-fileformat-robotics-0.1.0-SNAPSHOT.jar`.
Drop the jar onto the backend classpath (Quarkus auto-discovers via
the `META-INF/services/de.dlr.shepard.plugin.fileformat.robotics.FileParserPlugin`
service descriptor — see the same shape in
`plugins/fileformat-thermography`).

## Configuration

The plugin is configuration-free in tier-1. There are no
operator-tunable knobs — the predicate regexes are baked in (they will
be externalised to a config map when a second `.rdk` variant lands,
per the persona-board review captured in `RdkTextScrapeParser`'s
javadoc).

## Wire-up

Until the canonical `FileParserPlugin` SPI lands in
`backend/src/main/java/de/dlr/shepard/spi/fileparser/`, the parser is
intended to be invoked via direct bean wiring inside the existing
upload pipeline:

1. After `FileContainerService.upload(...)` returns, look up the
   appropriate parser via the extension match (`accepts(mimeType, filename)`).
2. Build a `ParseContext` carrying the uploaded bytes (or a stream from
   Garage), filename, parent `DataObject` and `FileReference` appIds,
   and a `siblingFiles()` listing fetched from the same `FileContainer`.
3. Invoke `parse(ctx)`. Pass an `AnnotationWriter` that delegates to
   the existing `:SemanticAnnotation` writer (see
   `de.dlr.shepard.context.semantic.services.SemanticAnnotationService`).
4. The handler records its own `:Activity` and sets `PROP_SKIP_CAPTURE`
   per the CLAUDE.md "handlers that record their own Activity hand off
   skip-capture" rule.

The wire-up itself is filed as **RDK-WIRE-AGGREGATOR-1** in
`aidocs/16-dispatcher-backlog.md` and tracked alongside the OTvis
plugin (same pattern, same blocker).

## Healthcheck

The parser is stateless — no health endpoint required. A successful
build + `mvnw test` round (17 tests in ~1 second, including the live
`MFZ.rdk` acceptance test when the MFFD raw-data tree is mounted) is
the smoke check.

## Known pitfalls

- **Large files in CI.** The acceptance test `RdkTextScrapeParserMFZFixtureTest`
  reads the real 12.1 MB `MFZ.rdk` from
  `examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk` when present
  and self-skips otherwise. The file is intentionally NOT checked into
  git. CI runners that have the MFFD showcase data mounted will exercise
  this test; others will skip it (logged via JUnit's `Assumptions.assumeTrue`).
- **Inflate memory.** Zlib expansion is unbounded in the file format
  (we observed 4.4×). The implementation accumulates the inflated
  payload in a `ByteArrayOutputStream`; for a 12 MB input we allocate
  ~53 MB. Large station files (> 100 MB compressed) may need a
  streaming-walk rewrite — file as RDK-STREAMING-WALK-1 if the need
  arises.
- **Sibling-file lookups must come from the FileContainer DAO, not the
  filesystem.** `ParseContext.siblingFiles()` is the SPI hook for this;
  do not bypass it with `java.nio.Files.find()` against a Garage mount
  (Garage is a distributed S3, not a POSIX FS).
