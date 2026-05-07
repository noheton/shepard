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
  <li><a href="{{ '/admin' | relative_url }}">I want to deploy shepard</a><br>
      <span>docker-compose stack, configuration, health endpoints.</span></li>
  <li><a href="{{ '/architecture' | relative_url }}">I want to extend shepard</a><br>
      <span>Stack, modules, data model, auth model.</span></li>
</ul>

## At a glance

<div class="facts">
  <div><div class="num">21</div><div class="lbl">Java version (backend)</div></div>
  <div><div class="num">3.27.x</div><div class="lbl">Quarkus version</div></div>
  <div><div class="num">4</div><div class="lbl">Persistence stores</div></div>
  <div><div class="num">FAIR</div><div class="lbl">RDM posture</div></div>
</div>

| Aspect | Today |
|---|---|
| API surface | REST under `/shepard/api`, OpenAPI 3.0 at `/shepard/doc/openapi.json`, Swagger UI at `/shepard/doc/swagger-ui` |
| Backend | Quarkus 3.27.x on Java 21 |
| Frontend | Nuxt 3 + Vue 3 + Vuetify 3 (separate Docker image) |
| Persistence | Neo4j 5.24 + MongoDB 8.0 + Postgres+TimescaleDB; optional Postgres+PostGIS |
| Auth | External OIDC (Keycloak typical) + secondary `X-API-KEY` header |
| Deployment | docker-compose, on-premises (no cloud assumed) |

Snapshot date: {{ site.snapshot_date }}.
