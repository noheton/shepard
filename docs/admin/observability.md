---
layout: default
title: Observability
description: Health endpoints, Prometheus scrape, bundled Grafana dashboard, k6 performance scripts, self-observability TS substrate.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/observability/
---

# Observability

shepard ships three observability surfaces out of the box: SmallRye Health
probes, a Prometheus + Grafana dashboard bundle, and self-observability
metrics stored in shepard's own TimescaleDB substrate.

## Health endpoints

SmallRye Health exposes the standard probe paths under the configured root
(`/shepard/api/healthz`):

- `/shepard/api/healthz/live` — liveness
- `/shepard/api/healthz/ready` — readiness
- `/shepard/api/healthz/started` — startup

Datasource and MongoDB health checks are explicitly disabled
(`quarkus.datasource.health.enabled=false`,
`quarkus.mongodb.health.enabled=false`) so readiness reports application
state, not raw DB connectivity. Use Prometheus scrapes plus per-DB
monitoring for connectivity.

## Prometheus scrape

Metrics: Prometheus scrape at `/shepard/doc/metrics/prometheus`. The
bundled compose `monitoring` profile boots Prometheus pre-wired to scrape
this endpoint every 10 s — see `infrastructure/prometheus/prometheus.yml`.

## Bundled Grafana dashboard

The `monitoring` compose profile boots **Prometheus** and **Grafana** with
auto-provisioning:

```bash
docker compose --env-file .env --profile monitoring up -d
```

- **Prometheus** scrapes the backend's `/shepard/doc/metrics/prometheus`
  endpoint every 10 s (see `infrastructure/prometheus/prometheus.yml`)
  and exposes its UI at <http://localhost:9090>.
- **Grafana** auto-loads the Prometheus datasource and the
  **"shepard — Overview"** dashboard with panels for HTTP request
  rate + p95/p99 latency, JVM heap / threads / GC, Hibernate session
  events, MongoDB command latency, and the permissions-cache hit
  ratio. UI at <http://localhost:3001>; admin login from
  `GRAFANA_ADMIN_USERNAME` + `GRAFANA_ADMIN_PASSWORD` in `.env`.

The dashboard JSON lives at
`infrastructure/grafana/dashboards/shepard-overview.json` — edit
in place and re-deploy to extend the panel set. Provisioning specs
are in `infrastructure/grafana/provisioning/`. Dashboards are
re-loaded by Grafana on a 30-second poll.

For shared/long-running deployments, replace the bundled
`shepard` / `secret` Grafana credentials in `.env` before exposing
port 3001 publicly.

## Performance testing (k6)

Two k6 scripts ship under `scripts/perf/`:

- **`perf-smoke.js`** — baseline smoke that runs in CI on every PR.
  Designed to fail fast on egregious regressions, not to gate on every
  microbenchmark wobble.
- **`k6-endpoints.js`** (designed, see PERF4a in
  [`aidocs/16`](https://github.com/noheton/shepard/blob/main/aidocs/16-dispatcher-backlog.md))
  — per-endpoint named-scenario Trend metrics for TimescaleDB
  range-scan, Neo4j provenance walk, `/v2/` data-objects, timeseries
  ingest batch, admin-features. Three executor shapes (steady, ramp,
  spike) with `p(95)` CI-gate thresholds.

Run locally:

```bash
cd scripts/perf
K6_SCENARIO=steady k6 run k6-endpoints.js
```

Optional outputs: Prometheus remote-write via
`--out experimental-prometheus-rw`, or `k6 cloud` via
`K6_CLOUD_TOKEN`. See `scripts/perf/README.md` for the full options.

## Self-observability — TS substrate (OBS-MFFD1+)

Per `feedback_shepard_measures_itself.md`, **shepard measures itself
inside its own timeseries substrate** — the same chart UI a researcher
uses to look at LUMEN hot-fire data also surfaces shepard's
ingest-throughput, payload-kind counters, and per-substrate latencies.
The first instance (OBS-MFFD1, 2026-05-23) lands self-observability for
the MFFD import scripts; default-yes for ingest / payload-kind / Garage
counters; default-no for sub-second SLO / alerting (still Prometheus's
job).

The observability TS Container is created at first-boot of the backend
under a reserved appId; the frontend's `/observability` view (TBD)
points the standard chart components at it.

## Log retention

Backend logs land in the `/opt/shepard/backend/logs` volume; rotation
is host-managed (logrotate or equivalent). The LOGSTORE1 design
(structured logs in a queryable substrate — Loki, VictoriaLogs, or
shepard-native) is queued, not shipped.
