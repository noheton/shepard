# `clients/java/` — Java client for `/shepard/api/...`

This is the OpenAPI-Generator-emitted Java client for the byte-frozen
`/shepard/api/...` surface, per
[`aidocs/63` ADR-0022](../../aidocs/63-architecture-decision-log.md#adr-0022--openapi-client-generators-kiota-new-baseline--openapi-generator-still-maintained-legacy)
and [`aidocs/57 §4.2`](../../aidocs/57-openapi-client-generator-evaluation.md#42-secondary-still-maintained-legacy-openapi-generator-for-shepardapi-continuity).
**This is the still-maintained, never-deprecated legacy client lineage**
— same generator, same templates, same Maven coordinates as upstream
ships.

- **Maven artefact**: `de.dlr.shepard:shepard-client`
- **Source spec**: `/shepard/doc/openapi/v1.json` (P4c v1 shelf — frozen
  upstream surface only)
- **Generator**: `openapitools/openapi-generator-cli:v7.16.0` (`java`)
- **Config**: [`config.yaml`](config.yaml)

For the `/v2/` development surface, see `clients-v2/java/` (Kiota,
CG1a). Pick whichever shelf you're calling — neither generator is on a
deprecation path.

See the top-level [`clients/README.md`](../README.md) for the full
dual-posture story, the CI-pipeline shape, and the v1-scope smoke test.

## Files in this directory (non-generated)

- [`pom.xml`](pom.xml) — parent POM that aggregates the generator-emitted
  `client/` module (which lands in `clients/java/client/` at CI time).
- [`ci_settings.xml`](ci_settings.xml) — Maven settings for the
  publication step (`mvn deploy -s ci_settings.xml`).
- [`config.yaml`](config.yaml) — `--config` argument passed to the
  generator (artefact coordinates, developer fields, SCM URLs, etc.).

The generated `client/` Maven module itself is emitted at build time and
not committed; it's the artefact that gets published.
