---
layout: default
title: Admin
description: Operational guide — docker-compose stack, configuration, health endpoints, backups.
---

This page describes how to run shepard. It cites only what is actually in the
repository today; the dedicated admin CLI is **in design** and not yet
shipped — see "Admin CLI" below.

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

## Admin CLI — coming, not shipped

A dedicated administration CLI is in design — proposed candidate functions
include user/group lifecycle, API-key rotation, permission audits, and bulk
exports/imports. **It is not implemented today.** Until it lands, operators
use docker-compose plus the per-DB CLI tools above.

(Design reference: `aidocs/22-admin-cli-draft.md` — forthcoming. The link
will resolve once that document is committed.)

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
