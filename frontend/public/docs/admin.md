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
- `shepard-hsds` (`hdfgroup/hsds:v0.9.5`) under profile `hdf` — enables the
  optional HDF5 / HSDS feature ([details below](#hdf5-hsds-opt-in-sidecar)).
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

## HDF5 (HSDS) — opt-in sidecar

The HDF5 payload kind is **off by default**. With the toggle off, every
`/v2/hdf-containers/...` endpoint returns `404 Not Found` and no HSDS HTTP
client is ever instantiated. See [`docs/reference/hdf-container.md`](reference/hdf-container/)
for the API surface; this section covers the operator side.

**Phase 1 only.** The current slice (backlog ID A5a — see
[`aidocs/35`](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md))
ships `HdfContainer` create / read / delete plus the HSDS sidecar
itself. The per-DataObject `HdfReference`, the byte-identical
download fallback, and the shared-Keycloak token relay are deferred
to A5b – A5e. Phase 1 uses **HTTP Basic** between the shepard
backend and the HSDS sidecar.

### Install steps

1. **Pick credentials and put them in `.env`.** The compose service
   defaults to `admin` / `admin` — fine for an air-gapped dev box,
   never fine for any deployment reachable from the public internet.

   ```bash
   # in infrastructure/.env
   HSDS_USERNAME=hsds-admin
   HSDS_PASSWORD=<32-char-random-secret>
   HSDS_BUCKET_NAME=shepard
   ```

2. **Mirror the credentials onto the backend.** The backend speaks
   HTTP Basic to the sidecar; supply the same username / password as
   environment variables consumed by `application.properties`.

   ```bash
   # also in infrastructure/.env, then reference from docker-compose.yml
   # as env to the backend service:
   SHEPARD_HDF_ENABLED=true
   SHEPARD_HDF_HSDS_USERNAME=hsds-admin
   SHEPARD_HDF_HSDS_PASSWORD=<same-32-char-secret>
   # endpoint defaults to http://shepard-hsds:5101 (the compose service
   # name), no override needed when running inside the compose network.
   ```

   Then add these to the backend service's `environment:` block in
   `infrastructure/docker-compose.yml` next to the other
   `SHEPARD_*` keys. Startup fails fast if `SHEPARD_HDF_ENABLED=true`
   but the credentials are blank — Phase 1 deliberately refuses to
   run in "ambient auth" mode.

3. **Bring up the profile.**

   ```bash
   cd infrastructure
   docker compose --env-file .env --profile hdf up -d
   ```

   Verify the sidecar is up:

   ```bash
   curl -fsS -u "$HSDS_USERNAME:$HSDS_PASSWORD" http://localhost:5101/about
   ```

4. **Restart the backend** (so it picks up the new env). On startup
   the shepard log emits one line per HsdsClient construction:
   `HSDS client initialised against endpoint=http://shepard-hsds:5101 (HTTP Basic, Phase 1)`.

5. **Smoke test from the API.**

   ```bash
   # Create a container.
   curl -fsS -X POST http://localhost:8080/v2/hdf-containers \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer <shepard-api-key>" \
        -d '{"name":"smoke","description":"first hdf"}'
   # Returns: {"appId":"019…","hsdsDomain":"/shepard/019…/", …}

   # Read it back.
   curl -fsS http://localhost:8080/v2/hdf-containers/019… \
        -H "Authorization: Bearer <shepard-api-key>"
   ```

   You can also confirm the HSDS-side row directly:

   ```bash
   curl -fsS -u "$HSDS_USERNAME:$HSDS_PASSWORD" \
        "http://localhost:5101/?domain=/shepard/019…/"
   ```

### Storage and capacity planning

The compose service mounts `./hsds-storage` for the **POSIX** storage
backend (HSDS's default). Object-store backends (S3 / MinIO / Azure
Blob) are opt-in via HSDS upstream env vars — see
[`aidocs/35` §3](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md#3-storage-layer).

**Rule of thumb** (per HSDS docs): plan for **~1.2× the raw HDF5
size** on disk because of HSDS's chunk-store overhead. A 100 GB HDF5
file → ~120 GB on disk.

### Backups

Add `./hsds-storage` (or your S3 bucket prefix, if you flip storage
backends) to the [backup checklist](#backups). HSDS supplies the
`hsadmin` CLI for higher-level export / import if you need
storage-backend migration.

### What's deferred

Until A5e ships the auth bridge, users authenticate to **HSDS
directly** with the admin credentials configured here (or per-user
Basic credentials you provision via `hsadmin`). Once A5e lands,
shepard mints per-user JWTs signed by a shared Keycloak realm and
the `h5pyd` ergonomics ("one credential, the shepard API key")
arrive — see [`aidocs/35` §5](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md#5-auth-bridge-the-trickiest-piece).

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

## Neo4j plugins

Operators get the **neosemantics ("n10s")** plugin enabled by default
(`NEO4J_PLUGINS=["n10s"]` plus `n10s.*` allowed in
`dbms.security.procedures.{allowlist,unrestricted}` in
`infrastructure/docker-compose.yml`). The plugin backs the new
`SemanticRepositoryType.INTERNAL` connector type — see
[Semantic repositories](/reference/semantic-repositories/) for the
casual-user story.

If you customised the Neo4j service and **removed** the procedures
allowlist line for `n10s.*`, you'll need to add it back, otherwise
shepard's startup hook logs:

```
N10sBootstrapHook: neosemantics (n10s) procedures not registered in Neo4j.
SemanticRepositoryType.INTERNAL will report unhealthy.
```

That's the only operator-visible signal — the rest of shepard
(including external `SPARQL`/`JSKOS`/`SKOSMOS` repositories)
continues to work unchanged.

The bootstrap calls `n10s.graphconfig.init(...)` exactly once per
fresh database; it's idempotent and safe to restart through. Override
the `handleVocabUris` mode via the
`shepard.semantic.internal.handle-vocab-uris` property
(default `IGNORE`), or fully disable the bootstrap with
`shepard.semantic.internal.enabled=false`.

## shepard plugins

shepard core stays small; new payload kinds, external
integrations, and identifier providers ship as
`shepard-plugin-*` JARs that an operator drops into
`/deployments/plugins/` without rebaking the image. The first
shipped plugin is the [Helmholtz Unhide publish
feed]({{ '/reference/unhide-publish/' | relative_url }}) (UH1a);
HDF5, video, AAS, ePIC, DataCite, GridFS-vs-S3, and others
queue behind it.

Quick install:

```bash
cp shepard-plugin-foo-1.2.3.jar /deployments/plugins/
# restart shepard so PluginRegistry picks it up
docker compose restart shepard-backend
```

Inspect what's loaded:

```bash
shepard-admin plugins list
# Plugin       Version   State      Source
# unhide       1.0.0     ENABLED    build classpath
```

Full operator runbook, lifecycle states, troubleshooting in
**[Plugins (reference)]({{ '/reference/plugins/' | relative_url }})**.

The design rationale (drop-in JARs via Java `ServiceLoader`,
not compose-side sidecars or per-install Dockerfile forks)
is recorded in
[ADR-0023]({{ '/aidocs/63-architecture-decision-log#adr-0023' | relative_url }}).

## Upgrades

The Neo4j and MongoDB images are pinned with explicit comments in
`infrastructure/docker-compose.yml` pointing at the upstream upgrade guides
(MR-315 for Neo4j 4.4 → 5.24; MR-306 for MongoDB step upgrades). Read those
before bumping major versions.

**Neo4j 5 → 6 upgrade note.** The n10s plugin tracks Neo4j major
versions. When upgrading Neo4j across a major boundary, also bump
the n10s version (the `NEO4J_PLUGINS=["n10s"]` env var auto-resolves
to the version matching the running Neo4j image). Plan a single
restart that includes both, watch the bootstrap log line for
"n10s INTERNAL semantic repository ready" on first start after the
upgrade.

## Reverse proxy

`caddy` terminates TLS on ports 80 / 443 (and 443/UDP for HTTP/3). Configuration
lives in `infrastructure/proxy/Caddyfile`; static SSL material in
`infrastructure/proxy/ssl`. Local-development variants are under
`infrastructure-local/` (Keycloak + a developer-friendly compose file).
