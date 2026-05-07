---
layout: default
title: shepard
description: Storage for HEterogeneous Product And Research Data — overview, use cases, and where to go next.
---

shepard ("Storage for HEterogeneous Product And Research Data") is a multi-database
research-data platform developed by DLR's Center for Lightweight Production Technology
(Augsburg). It exposes a single REST API that stores timeseries, files, structured
documents, spatial data, and semantic annotations alongside metadata, in support of
FAIR research-data management. (Source: `aidocs/01-repo-overview.md`.)

## What it is for

<div class="cards">
  <div class="card">
    <h3>Research data for an aerospace experiment</h3>
    <p>Group flight-test artefacts — CAD files, sensor channels, lab-journal entries,
    derived analyses — under one Collection, navigated as a parent/child
    DataObject tree with predecessor/successor links for derivations.</p>
  </div>
  <div class="card">
    <h3>Time-series ingestion from a measurement campaign</h3>
    <p>Stream channels into a TimescaleDB-backed timeseries container, then attach the
    container as a Reference to the DataObject that represents the test run.</p>
  </div>
  <div class="card">
    <h3>Cross-DB graph + tabular + spatial in one place</h3>
    <p>Metadata graph in Neo4j, files and structured documents in MongoDB, timeseries
    in Postgres+TimescaleDB, optional spatial data in Postgres+PostGIS — addressed
    through one REST surface, no per-store integrations to maintain.</p>
  </div>
</div>

## Where to next

<ul class="role-nav">
  <li><a href="{{ '/getting-started' | relative_url }}">I want to use shepard</a><br>
      <span>Quickstart with the Python client.</span></li>
  <li><a href="{{ '/admin' | relative_url }}">I want to deploy shepard</a><br>
      <span>docker-compose stack, configuration, health endpoints.</span></li>
  <li><a href="{{ '/architecture' | relative_url }}">I want to extend shepard</a><br>
      <span>Stack, modules, data model, auth model.</span></li>
</ul>

## At a glance

| Aspect | Today |
|---|---|
| API surface | REST under `/shepard/api`, OpenAPI 3.0 at `/shepard/doc/openapi.json`, Swagger UI at `/shepard/doc/swagger-ui` |
| Backend | Quarkus 3.27.x on Java 21 |
| Frontend | Nuxt 3 + Vue 3 + Vuetify 3 (separate Docker image) |
| Persistence | Neo4j 5.24 + MongoDB 8.0 + Postgres+TimescaleDB; optional Postgres+PostGIS |
| Auth | External OIDC (Keycloak typical) + secondary `X-API-KEY` header |
| Deployment | docker-compose, on-premises (no cloud assumed) |

Snapshot date: {{ site.snapshot_date }}.

## Notes on this site

This site is **scaffolding** built from `aidocs/` and the source. The visual treatment
is intentionally neutral and accessible — the canonical DLR Corporate-Design assets
(palette, fonts, logo, Motion-CI rules) are not in this repository, so nothing here
attempts to look "DLR-ish". When the canonical assets land, the swap is a one-file
diff in `assets/css/main.scss` plus a logo replacement; see `docs/README.md`.
