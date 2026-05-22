---
layout: default
title: Architecture
description: shepard's high-level architecture — stack, persistence, entity model, auth, export.
mermaid: true
hero: true
hero_eyebrow: Architecture
hero_title: Architecture
hero_lede: Stack, persistence, entity model, auth, and export — sourced from `aidocs/01-repo-overview.md` and the in-repo `architecture/src/` AsciiDoc.
hero_bg: green
---

This page summarises shepard's architecture as it exists today. Sources:
`aidocs/01-repo-overview.md`, `architecture/src/05_building_block_view/`,
`architecture/src/09_architecture_decisions/`, `infrastructure/docker-compose.yml`.

## High-level block diagram

<div class="mermaid">
flowchart LR
  user[Browser / API client] --> caddy[Caddy reverse proxy]
  caddy --> frontend[Nuxt 3 frontend]
  caddy --> backend[Quarkus 3 backend - Java 21]
  frontend --> backend
  backend --> neo4j[(Neo4j 5.24<br/>metadata graph)]
  backend --> mongo[(MongoDB 8.0<br/>structured docs<br/>+ GridFS fallback)]
  backend --> ts[(Postgres + TimescaleDB<br/>timeseries)]
  backend --> s3[(S3-compatible<br/>file payloads<br/>Garage default)]
  backend -. optional .- gis[(Postgres + PostGIS<br/>spatial)]
  backend -. SPARQL .- semantic[Semantic repos<br/>internal n10s or external]
  backend -. webhooks .- subs[Subscribers]
  frontend -. presigned PUT/GET .- s3
  plugins[shepard-plugin-*.jar<br/>drop-in] --> backend
  oidc[OIDC IdP - Keycloak typical] --> caddy
  prom[Prometheus] --> backend
</div>

## Stack

- **Backend** — Quarkus 3.27.x, Java 21, Maven; JJWT 0.11.5; Hibernate Spatial 7.2.6.
  Verified in `backend/pom.xml` (`<maven.compiler.release>21</maven.compiler.release>`).
- **Frontend** — Nuxt 3, Vue 3, Vuetify 3, TipTap, Vite 6.
- **Build tooling** — OpenAPI Generator for the polyglot clients;
  Renovate for dependency updates; docToolchain for HTML architecture docs.

## Polyglot persistence — and why each store

| Store | Role | Reason |
|---|---|---|
| Neo4j 5.24 | Metadata graph | The Collection / DataObject / Reference / Container relationships are inherently graph-shaped; parent/child + predecessor/successor traversals are first-class. |
| MongoDB 8.0 | Structured documents, GridFS fallback for file payloads | Variable-shaped JSON payloads have no benefit from a relational schema; MongoDB's document model is the natural fit. GridFS remains a first-class file backend for small / air-gapped deployments. |
| Postgres + TimescaleDB | Timeseries | Hypertables, time-bucket aggregation, and SQL-compatible ingestion outperformed InfluxDB for the workload (ADR-010 / ADR-011). |
| S3-compatible (Garage default) | File payloads at scale | The [`shepard-plugin-file-s3`](/reference/file-storage/) adapter (FS1b) supports any S3-compatible endpoint — Garage, Cloudflare R2, Backblaze B2, AWS S3, Ceph RGW. Garage is the reference self-hosted choice (ADR-0024). Presigned URLs unblock browser-direct uploads + RO-Crate ZIP delivery. |
| Postgres + PostGIS (optional) | Spatial data | Bounding-box queries returned in 380 ms versus 59 s on alternative stacks (ADR-014 / ADR-017). Behind the `shepard.spatial-data.enabled` feature flag. |

ADR rationale: see `architecture/src/09_architecture_decisions/008-...`,
`010-...`, `011-...`, `014-...`, `017-...`, and ADR-0024 for the
Garage / S3-default decision.

The file-storage adapter is **swappable at deploy time**:
`shepard.storage.provider=gridfs` (default, in-Mongo) or `s3` (any
S3 endpoint). See the
[GridFS → S3 migration runbook](/ops/migrate-gridfs-to-s3/) for the
in-place upgrade path.

## Entity model

The four entity kinds, per `architecture/src/05_building_block_view/` and the
data-model wiki page:

- **Collection** — top-level container; permissioned root for a workpackage,
  experiment, or campaign.
- **DataObject** — node within a Collection, related parent/child (composition)
  and predecessor/successor (derivation). Holds References.
- **References** — typed pointers from a DataObject to data: Structured-Data,
  File, Timeseries, Spatial-Data, URI, Lab-Journal, plus inter-entity
  References (Collection, DataObject).
- **Containers** — the actual payload store for Timeseries, Structured-Data,
  and File data; addressed by Reference.

Concrete `*Rest.java` endpoints live under
`backend/src/main/java/de/dlr/shepard/{context,data,auth,common}/.../endpoints/`
— e.g. `CollectionRest`, `DataObjectRest`, `FileReferenceRest`,
`TimeseriesRest`, `StructuredDataRest`, `SpatialDataPointRest`,
`LabJournalEntryRest`, `URIReferenceRest`,
`SemanticRepositoryRest`, `SubscriptionRest`,
`UserRest`, `UserGroupRest`, `ApiKeyRest`, `SearchRest`.

## API shelves — `/shepard/api/` and `/v2/`

This fork exposes the REST surface on two shelves that share the same
backend and the same Neo4j graph:

- **`/shepard/api/...`** — the upstream surface. **Frozen for
  byte-for-byte parity with shepard 5.2.0.** Existing clients
  (Python, TypeScript, Java) built against upstream keep working
  without modification.
- **`/v2/...`** — this fork's development surface. New endpoints
  land here additively: container-level semantic annotations
  (`/v2/{kind}-containers/{id}/annotations`), server-enforced
  safe-delete (`DELETE /v2/{kind}-containers/{id}?force=…`), the
  instance-identity public read (`GET /v2/instance/identity`),
  curated SQL-over-HTTP (`POST /v2/sql/timeseries`), file V2 + git +
  video references, runtime admin-config endpoints, …

`aidocs/25` formalises this split (L2 chain). Operators upgrading
from upstream see zero breakage on `/shepard/api/` and choose when
to start consuming `/v2/`.

## Plugin SPI

shepard's value grows from extension — new payload kinds, new
external integrations, new identifier providers, new file storage
backends — all without forking core. The
[`PluginManifest`](/reference/plugins/) SPI (PM1a) loads
`shepard-plugin-*.jar` files from `/deployments/plugins/` at
startup via Java `ServiceLoader`.

Plugins can declare:

- REST resources mounted on the `/v2/` surface,
- Neo4j entities / Flyway migrations / CDI beans,
- their own SPDX licence + version / shepard-compatibility range
  (PM1b enforces the range; PM1b2 verifies JAR signatures when
  `shepard.plugins.signing.required=true`),
- runtime overrides persisted to `:PluginRuntimeOverride` (PM1e —
  flips survive restart),
- their own `shepard-admin` CLI subcommands (PM1d), and
- **infrastructure sidecars** they need to function
  ([Sidecars SPI](/reference/sidecars/), PM1f).

Bundled plugins include `unhide` (Helmholtz Unhide publish),
`kip` + `minter-local` + `minter-datacite` (PID minting),
`file-s3` (S3 file storage), `video` (video payloads),
`ai` + `wiki-writer` (LLM integration), `importer` (cross-instance
import), and `v1-compat` (the upstream-frozen surface's control
plane). The full list is at
[Plugins reference](/reference/plugins/).

## Auth model

- **Inbound** — JWT bearer tokens from an external OIDC provider (Keycloak in
  the typical deployment), validated against a pinned static OIDC public key.
- **Long-lived access** — `X-API-KEY` header (verified in CORS allowlist
  `quarkus.http.cors.headers` in `application.properties`).
- **Authorization** — per-entity `Permissions` graph (Neo4j) with
  Owner / Manager / Writer / Reader roles plus group-level Reader/Writer; entity
  visibility flag is one of `Public`, `PublicReadable`, `Private`
  (`backend/src/main/java/de/dlr/shepard/common/util/PermissionType.java`).

For deeper material, see the upstream architecture chapters under
`architecture/src/`. Cross-references to the in-repo design notes
`aidocs/12 §11`, `aidocs/13`, and `aidocs/14` are marked **planned / proposal**;
some forward-referenced aidocs (e.g. `aidocs/19`, `aidocs/24`) are not yet
checked in at this snapshot date.

## Export model

RO-Crate ZIP export with `ro-crate-metadata.json`, per `aidocs/01`. This is the
canonical machine-readable handover format; consumers can re-attach the export
into another shepard instance or process it with the wider RO-Crate toolchain.

## Observability

- Prometheus metrics at `/shepard/doc/metrics/prometheus`
  (`quarkus.micrometer.export.prometheus.enabled=true`).
- Health endpoints under `/shepard/api/healthz`
  (`quarkus.smallrye-health.root-path`).

## Where this site sits

This site does not duplicate the canonical Arc42 docs in `architecture/src/`.
For decision rationale and module decomposition, read those AsciiDoc sources
(or the rendered docToolchain output) directly.
