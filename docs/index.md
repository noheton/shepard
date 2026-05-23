---
layout: default
title: shepard
description: Storage for HEterogeneous Product And Research Data — overview, use cases, and where to go next.
hero: true
hero_eyebrow: Research Data Management
hero_title: shepard
hero_lede: A multi-database research-data platform from DLR's Center for Lightweight Production Technology — one REST API for timeseries, files, structured documents, spatial data, and semantic annotations.
hero_photo: /assets/img/photo-aircraft.jpg
hero_splash: true
hero_cta1_text: Get started
hero_cta1_url: /getting-started
hero_cta2_text: View architecture
hero_cta2_url: /architecture
audience: visitor
---
shepard ("Storage for HEterogeneous Product And Research Data") is a multi-database
research-data platform developed by DLR's Center for Lightweight Production Technology
(Augsburg). It exposes a single REST API that stores timeseries, files, structured
documents, spatial data, and semantic annotations alongside metadata, in support of
FAIR research-data management. (Source: `aidocs/01-repo-overview.md`.)

## What it is for

<div class="card-grid">
  <article class="dlr-card">
    <div class="img" style="background-image:url('{{ '/assets/img/photo-aircraft.jpg' | relative_url }}')"></div>
    <div class="body">
      <span class="eyebrow">Use case</span>
      <h3>Research data for an aerospace experiment</h3>
      <p>Group flight-test artefacts — CAD files, sensor channels, lab-journal entries, derived analyses — under one Collection, navigated as a parent/child DataObject tree with predecessor/successor links for derivations.</p>
      <a href="{{ '/user-guide' | relative_url }}" class="more">Learn more ›</a>
    </div>
  </article>
  <article class="dlr-card">
    <div class="img" style="background-image:url('{{ '/assets/img/photo-satellite.jpg' | relative_url }}')"></div>
    <div class="body">
      <span class="eyebrow">Use case</span>
      <h3>Time-series ingestion from a measurement campaign</h3>
      <p>Stream channels into a TimescaleDB-backed timeseries container, then attach the container as a Reference to the DataObject that represents the test run.</p>
      <a href="{{ '/architecture' | relative_url }}" class="more">Learn more ›</a>
    </div>
  </article>
  <article class="dlr-card">
    <div class="img" style="background-image:url('{{ '/assets/img/photo-solar.jpg' | relative_url }}')"></div>
    <div class="body">
      <span class="eyebrow">Use case</span>
      <h3>Cross-DB graph + tabular + spatial in one place</h3>
      <p>Metadata graph in Neo4j, files and structured documents in MongoDB, timeseries in Postgres+TimescaleDB, optional spatial data in Postgres+PostGIS — addressed through one REST surface, no per-store integrations to maintain.</p>
      <a href="{{ '/architecture' | relative_url }}" class="more">Learn more ›</a>
    </div>
  </article>
</div>

## Where to next

<ul class="role-nav">
  <li><a href="{{ '/getting-started' | relative_url }}">I want to use shepard</a><br>
      <span>Quickstart with the Python client.</span></li>
  <li><a href="{{ '/admin/' | relative_url }}">I want to deploy shepard</a><br>
      <span>docker-compose stack, configuration, health endpoints.</span></li>
  <li><a href="{{ '/architecture' | relative_url }}">I want to extend shepard</a><br>
      <span>Stack, modules, data model, auth model.</span></li>
</ul>

## At a glance

<div class="facts">
  <div><div class="num">21</div><div class="lbl">Java version (backend)</div></div>
  <div><div class="num">3.27.x</div><div class="lbl">Quarkus version</div></div>
  <div><div class="num">5</div><div class="lbl">Persistence substrates</div></div>
  <div><div class="num">FAIR</div><div class="lbl">RDM posture</div></div>
</div>

| Aspect | Today |
|---|---|
| API surface | Two shelves: upstream-compatible `/shepard/api/...` (frozen for byte-for-byte parity with shepard 5.2.0) + this fork's additive `/v2/...` development surface. OpenAPI 3.0 at `/shepard/doc/openapi.json`, Swagger UI at `/shepard/doc/swagger-ui/` |
| Backend | Quarkus 3.27.x on Java 21 |
| Frontend | Nuxt 3 + Vue 3 + Vuetify 3 (separate Docker image) |
| Persistence | Neo4j 5.24 (metadata) + MongoDB 8.0 (files / structured) + Postgres+TimescaleDB (timeseries) + S3-compatible object store via [`shepard-plugin-file-s3`](/reference/file-storage/) (Garage by default); optional Postgres+PostGIS |
| Auth | External OIDC (Keycloak typical) + secondary `X-API-KEY` header |
| Deployment | docker-compose, on-premises (no cloud assumed) |

## What's new on this fork

Recent capabilities not in upstream shepard 5.2.0:

- **S3-compatible file storage with presigned uploads.** The
  [`shepard-plugin-file-s3`](/reference/file-storage/) adapter is
  live (FS1b–FS1g): browser-direct presigned upload, presigned
  download, presigned RO-Crate export, and a
  [GridFS → S3 migration runbook](/ops/migrate-gridfs-to-s3/) for
  in-place adapter swap. Garage is the default self-hosted endpoint;
  [Garage activation runbook](/ops/garage-activation-runbook/).
  GridFS stays first-class supported — not a deprecation path.
- **Plugins declare their sidecars (PM1f).** Activating a plugin
  that needs an external service (S3 backend, Kafka, Redis, …) no
  longer means hand-editing a compose override — the plugin's
  manifest carries the sidecar shape and an operator-side renderer
  pastes the compose snippet. See [Sidecars SPI](/reference/sidecars/).
- **Cross-instance import with full provenance** — the
  [`mffd-import-v15`](/reference/import/) reference script pulls
  Collections from a remote shepard (or local directory), uploads
  via the presigned-URL flow, writes PROV-O activities on every
  transfer, and survives JWT expiry + redeploy + operator
  interrupt without losing state.
- **View recipes + process recipes (TPL2a).** Two new
  `TemplateKind`s drive the upcoming `POST /v2/shapes/render`
  endpoint — same recipe drives a TresJS / Three.js component
  today, an Isaac Sim scene tomorrow. See
  [View recipes](/reference/view-recipes/).
- **MCP server, native + JVM-side.** `/v2/mcp/sse` exposes an
  8-tool surface (collections, dataobjects, timeseries channels,
  files, structured-data, annotations) for Claude and other MCP
  clients. OIDC + API-key auth; LTTB downsampling on
  `get_channel_data`. Discoverable from the `/me#mcp` profile pane.
- **AI plugin + wiki-writer.** The
  [`shepard-plugin-ai`](https://github.com/noheton/shepard/tree/main/plugins/ai)
  module exposes an `LlmProvider` SPI for OpenAI-compatible
  endpoints with per-capability slots (TEXT / FAST_TEXT / IMAGE_GEN
  / VISION / EMBEDDING / STRUCTURED), prompt-injection defence, and
  `:AiActivity` provenance on every call. `shepard-plugin-wiki-writer`
  generates a Markdown lab-journal entry for any DataObject from
  its metadata + Collection context with one click.
- **Semantic annotations on every primitive.** Collections, DataObjects,
  References, Timeseries channels, **and Containers themselves** —
  see the [container annotations reference](/reference/container-annotations/).
  An n10s-backed [internal semantic repository](/reference/semantic-repositories/)
  ships with eleven pre-seeded ontologies (PROV-O, Dublin Core,
  schema.org, FOAF, QUDT, OM-2, W3C Time, GeoSPARQL, OBO Relation
  Ontology, NFDI4Ing metadata4ing) so casual users have resolvable
  IRIs out of the box.
- **Per-instance organisation identity** via ROR — admins set the
  instance's ROR id once; the About → Organization pane fetches live
  details from ror.org.
- **HMC Kernel Information Profile publication** — one-click
  Publish button on any Collection or DataObject mints a PID
  (local default; DataCite DOI via the
  `shepard-plugin-minter-datacite` plugin); resolver at
  `/v2/.well-known/kip/{pid-suffix}`. See
  [Publish and PIDs](/reference/publish-and-pids/).
- **Helmholtz Unhide publish feed** —
  `shepard-plugin-unhide` exposes `GET /v2/unhide/feed.jsonld`
  (schema.org + metadata4ing JSON-LD) for the HKG / Unhide
  harvester. Runtime-configurable.
- **Plugin extensibility** — drop `shepard-plugin-*.jar` files into
  `/deployments/plugins/`, restart; bundled plugins cover Helmholtz
  Unhide publish, KIP / local + DataCite minters, S3 file storage,
  video, AI provider, wiki-writer. See [Plugins](/reference/plugins/).
- **v1 deprecation control plane.** The
  `shepard-plugin-v1-compat` plugin ships a `:LegacyV1Config`
  singleton + admin REST + frontend banner so operators can decide
  *when* to disable the upstream `/shepard/api/...` surface —
  the fork imposes no global sunset timeline. See
  [v1 deprecation](/reference/v1-deprecation/).

Snapshot date: {{ site.snapshot_date }}.

---

Cite our work or look up the standards behind these design decisions in
the [bibliography]({{ '/bibliography' | relative_url }}). For an
entirely overblown, AI-generated account of why this software is called
*shepard*, see the [origin myth]({{ '/origin-myth' | relative_url }}).

> *Unofficial motto:* **"Data management even institute directors understand."**
