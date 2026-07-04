---
stage: feature-defined
last-stage-change: 2026-06-02
---

# `shepard-plugin-fileformat-svdx` — install

Operator-facing install guide. Audience: a shepard administrator
adding the SVDX parser to a running instance.

## Prerequisites

* shepard backend ≥ post-A1e (the file-format SPI dispatcher).
* Java 21 (matches the backend runtime).
* No sidecar process. No external service dependency. No new
  database substrate. This plugin is pure-Java and runs in-process
  in the backend JVM.

## Build

The plugin is a standalone Maven module — same constraint as the
sibling `fileformat-thermography` and `fileformat-robotics` plugins.
The main aggregator build is currently wedged on a Quarkus/Jandex
infinite-loop hang in `backend/pom.xml`; pulling this module into
the aggregator would amplify the hang.

```bash
cd plugins/fileformat-svdx
../../backend/mvnw package
```

Produces `target/shepard-plugin-fileformat-svdx-0.1.0-SNAPSHOT.jar`.

Aggregator wire-up is tracked under `MFFD-PLUGIN-SVDX-WIRE-1` in
`aidocs/16-dispatcher-backlog.md` and is deferred until the Jandex
hang resolves.

## Installation

Until the file-format SPI dispatcher lands in `backend/`, the JAR is
installed by dropping it onto the backend classpath at startup:

1. Copy the JAR into the backend's plugin extension directory (see
   your deployment's `docker-compose.yml` for the exact mount —
   typically `/opt/shepard/plugins/` inside the container).
2. Restart the backend: `make redeploy-backend`.
3. The SPI loader (when the dispatcher lands) auto-discovers the
   service via the
   `META-INF/services/de.dlr.shepard.plugin.fileformat.svdx.FileParserPlugin`
   resource shipped inside the JAR. Until then, downstream pipelines
   can invoke `SvdxManifestParser` directly as a library.

## Configuration keys

**None.** The plugin has no `application.properties` keys, no
`:SvdxConfig` Neo4j singleton, no operator dials. Manifest
extraction is universal and parameter-free; future tiers (CSV ingest)
may add per-tier knobs at that point.

## What the plugin touches

* Reads: the FileReference content (bytes) of every uploaded file
  whose `accepts()` returns `true` (extension `.svdx` or MIME
  `application/vnd.beckhoff.scope+svdx`).
* Writes: one `:SemanticAnnotation` per emitted predicate, anchored
  to the parent FileReference's `appId`. Typical magnitude: ~250
  annotations per real MFFD file.
* Records: one `:Activity` per parsing invocation, via the standard
  `ProvenanceCaptureFilter` (the plugin does not call
  `ProvenanceService.record()` directly).

## What the plugin does NOT touch

* No DataObjects, Collections, or Containers are mutated.
* No new files are uploaded or downloaded.
* No external network calls. No HTTP, no DNS, no ADS, no OPC UA.
* No filesystem writes outside the standard backend log.

## Healthcheck

The plugin has no health endpoint of its own; it is exercised
indirectly through the file-format SPI dispatcher's health page when
that lands. Until then, the smoke test is:

```bash
# Upload a known .svdx, then query the FileReference's annotations.
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "https://<shepard-host>/v2/files/$FILE_APPID/annotations" \
  | jq '.[] | select(.predicate | startswith("urn:shepard:svdx:"))'
```

A successful install returns one or more `urn:shepard:svdx:*` rows.

## Known limitations

* **Binary section unparseable** — see `byte-layout-notes.md`. The
  recording samples themselves remain in the FileReference's bytes;
  the plugin only enriches the annotation graph.
* **Sibling-CSV scope** — `companionCsv` only fires when a sibling
  `.csv` or `_parsed.csv` is in the same FileContainer at parse
  time. If the CSV is uploaded later, the annotation is not
  retroactively added; a re-parse is needed.
* **Aggregator not wired** — see "Build" above; the aggregator
  wire-up is `MFFD-PLUGIN-SVDX-WIRE-1`.

## Upgrade path

This plugin is purely additive. There are no breaking changes from
any prior version (initial release). Future tier-2 work
(`MFFD-PLUGIN-SVDX-CSV-INGEST-1` for CSV ingest,
`MFFD-PLUGIN-SVDX-BINARY-PARSER-1` for binary-section research,
`MFFD-PLUGIN-SVDX-SEMANTIC-1` for symbol-name semantic
auto-annotation, `MFFD-PLUGIN-SVDX-BULK-1` for folder-spanning
import) will land as additional optional modules; the tier-1
annotation set documented in `reference.md` is the stable contract.

## Removal

Stop the backend, remove the JAR from the plugin directory, restart.
Annotations previously emitted by the plugin remain in Neo4j; clean
them up with a Cypher query if desired:

```cypher
MATCH (a:SemanticAnnotation)
WHERE a.predicate STARTS WITH 'urn:shepard:svdx:'
DETACH DELETE a;
```

## License

Apache-2.0 (same as the rest of shepard).
