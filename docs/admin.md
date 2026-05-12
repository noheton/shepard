---
layout: default
title: Admin
description: Operational guide — docker-compose stack, configuration, health endpoints, backups.
---

This page describes how to run shepard. It cites only what is actually in the
repository today. A read-only admin CLI ([`shepard-admin`](reference/admin-cli/))
ships in L1 Phase 1 — see "Admin CLI" below; the broader mutating commands
remain in design.

## Deploy paths beyond docker-compose

For paths that go beyond running the bundled compose locally — paid
VPS, managed-services split, ephemeral cloud dev — see
**[Deploy options](deploy/)**.

## Stack — docker-compose

Production deployment uses `infrastructure/docker-compose.yml`. The
non-optional services are:

| Service | Image (pinned) | Role |
|---|---|---|
| `backend` | `registry.gitlab.com/dlr-shepard/shepard/backend:5.2.0` | Quarkus REST API |
| `frontend` | `registry.gitlab.com/dlr-shepard/shepard/frontend:5.2.0` | Nuxt 3 UI |
| `neo4j` | `neo4j:5.24` | Metadata graph |
| `mongodb` | `mongo:8.0` | Files and structured data |
| `timescaledb` | `timescale/timescaledb:2.24.0-pg16` | Timeseries |
| `caddy` | `caddy:2` | Reverse proxy + TLS |

Optional / behind compose `profiles`:

- `postgis` (`postgis/postgis:16-3.5`) under profile `spatial` — enables the
  optional spatial-data feature.
- `mongoexpress` for ad-hoc DB browsing.
- `prometheus` (`prom/prometheus:v3.9.1`) and `grafana`
  (`grafana/grafana:12.2.1-security-01`) under profile `monitoring`.
- `timescale-migration-preparation` under profile
  `timescale-migration-preparation` for the InfluxDB → TimescaleDB migration.

Bring it up:

```bash
cd infrastructure
docker compose --env-file .env up -d
# add profiles as needed:
docker compose --env-file .env --profile spatial --profile monitoring up -d
```

## Configuration — environment and properties

The container reads its environment from `.env` (consumed by docker-compose)
and the JAR reads runtime properties from
`backend/src/main/resources/application.properties`. The shepard-specific
properties verified in source today are:

| Property | Default | Effect |
|---|---|---|
| `shepard.version` | `${project.version}` | Version reported on `/versionz` |
| `shepard.versioning.enabled` | `false` | Enables the entity versioning code path |
| `shepard.spatial-data.enabled` | `false` | Enables PostGIS spatial features |
| `shepard.autoconvert-int` | `false` | Numeric autoconversion in structured data |

Quarkus path roots (also from `application.properties`):

- `quarkus.http.root-path=/shepard/api`
- `quarkus.http.non-application-root-path=/shepard/doc`
- `quarkus.smallrye-health.root-path=/shepard/api/healthz`
- `quarkus.smallrye-openapi.path=openapi.json`
- `quarkus.swagger-ui.always-include=true`

The combined OpenAPI document is served at `/shepard/doc/openapi.json`.
This fork additionally exposes two filtered views, useful when an
operator wants to generate a client pinned to a single API shelf:

- `/shepard/doc/openapi/v1.json` — only the upstream-compatible
  `/shepard/api/...` paths.
- `/shepard/doc/openapi/v2.json` — only the fork's `/v2/...`
  development surface.

Both honour `?format=yaml`; both are unauthenticated, matching the
posture of the combined document. The combined `/shepard/doc/openapi.json`
keeps working unchanged. (P4c.)

CORS is permissive (`quarkus.http.cors.origins=*`) and accepts headers
`Origin, Accept, X-Requested-With, Content-Type, Authorization, X-API-KEY`
plus the standard preflight set. Tighten this in front of internet-exposed
deployments.

Database connection environment variables consumed by the backend container
(see `infrastructure/docker-compose.yml`): `OIDC_PUBLIC`, `OIDC_AUTHORITY`,
`OIDC_ROLE`, `NEO4J_HOST`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`,
`QUARKUS_MONGODB_CONNECTION_STRING`, `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`,
`QUARKUS_DATASOURCE_SPATIAL_*` (only used when spatial is enabled),
`SHEPARD_MIGRATION_MODE_ENABLED`, `SHEPARD_SPATIAL_DATA_ENABLED`,
`SHEPARD_AUTOCONVERT_INT`. The compose file still wires legacy `INFLUX_*`
variables — these only matter for the timescale-migration-preparation profile.

## Health endpoints

SmallRye Health exposes the standard probe paths under the configured root
(`/shepard/api/healthz`):

- `/shepard/api/healthz/live` — liveness
- `/shepard/api/healthz/ready` — readiness
- `/shepard/api/healthz/started` — startup

(Datasource and MongoDB health checks are explicitly disabled —
`quarkus.datasource.health.enabled=false`,
`quarkus.mongodb.health.enabled=false` — so readiness reports application
state, not raw DB connectivity. Use Prometheus scrapes plus per-DB
monitoring for connectivity.)

Metrics: Prometheus scrape at `/shepard/doc/metrics/prometheus`.

## Performance metrics — out-of-the-box dashboard

The bundled `monitoring` compose profile boots **Prometheus** and
**Grafana** with auto-provisioning:

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

## Backups

Each persistence store has its own backup path. shepard does not ship a
unified backup tool today.

- **Neo4j 5.24** — use `neo4j-admin database dump` (or volume snapshots of
  `/opt/shepard/neo4j/data`).
- **MongoDB 8.0** — `mongodump` against the `mongodb` service.
- **Postgres + TimescaleDB** — `pg_dump` (Timescale-aware via
  `timescaledb-tune` / `pg_dump` extension support, depending on workload),
  or volume snapshots of `/opt/shepard/timescaledb`.
- **Postgres + PostGIS** (optional) — `pg_dump` against the `postgis`
  service.
- **Backend logs and config** — volumes at `/opt/shepard/backend/logs` and
  `/opt/shepard/backend/config`.
- **Caddy data and config** — volumes at `/opt/shepard/caddy/data` and
  `/opt/shepard/caddy/config`.

Coordinate the dumps so they reflect a consistent point-in-time, especially
when permissions or schema migrations are mid-flight.

## Admin CLI

A read-only `shepard-admin` CLI ships in L1 Phase 1 — `features list`,
`health`, and `migrations status [containerId]`. Build the uber-jar
locally for now:

```bash
cd cli
mvn package -DskipTests
export SHEPARD_ADMIN_URL=https://shepard.example.com
export SHEPARD_ADMIN_API_KEY=<instance-admin-roled API key>
java -jar target/shepard-admin-*.jar features list
```

Full reference, sample output, and exit-code semantics in
**[Admin CLI (reference)](reference/admin-cli/)**.

Phase 2+ (cleanup of soft-deleted entities, RO-Crate import/export,
the `init` TUI wizard for first-run `.env`) remains in design — see
`aidocs/22-admin-cli-draft.md`.

## Upgrades

The Neo4j and MongoDB images are pinned with explicit comments in
`infrastructure/docker-compose.yml` pointing at the upstream upgrade guides
(MR-315 for Neo4j 4.4 → 5.24; MR-306 for MongoDB step upgrades). Read those
before bumping major versions.

## Reverse proxy

`caddy` terminates TLS on ports 80 / 443 (and 443/UDP for HTTP/3). Configuration
lives in `infrastructure/proxy/Caddyfile`; static SSL material in
`infrastructure/proxy/ssl`. Local-development variants are under
`infrastructure-local/` (Keycloak + a developer-friendly compose file).
