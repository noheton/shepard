---
layout: default
title: Install
description: First-time deployment of shepard — compose stack, profiles, hosting paths.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/install/
---

# Install

This page covers a first-time deployment of shepard. For host sizing read
[System requirements]({{ '/admin/system-requirements/' | relative_url }})
first.

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
  (`grafana/grafana:12.2.1-security-01`) under profile `monitoring` — see
  [Observability]({{ '/admin/observability/' | relative_url }}).
- `timescale-migration-preparation` under profile
  `timescale-migration-preparation` for the InfluxDB → TimescaleDB migration.

Bring it up:

```bash
cd infrastructure
docker compose --env-file .env up -d
# add profiles as needed:
docker compose --env-file .env --profile spatial --profile monitoring up -d
```

## Pre-deploy checklist

Independent of where you host:

- [ ] **Rotate `.env` defaults.** `aidocs/07` H8 explicitly flags the
      shipped `POSTGRES_*`, `NEO4J_PW`, `MONGO_PASSWORD`, etc. as
      already-public placeholders.
- [ ] **Restrict CORS.** `quarkus.http.cors.origins=*` in the bundled
      `application.properties` is permissive; tighten before any
      internet-exposed deployment (`aidocs/07` C2). See
      [Configuration]({{ '/admin/config/' | relative_url }}) for the knobs.
- [ ] **Decide on auth.** All paths assume an OIDC provider. The
      simplest path is to add a Keycloak service to the same compose;
      a managed OIDC is less ops but shifts trust to a third party. See
      [Authentication]({{ '/admin/auth/' | relative_url }}).
- [ ] **Decide on backups.** Each substrate has its own recipe; pick one
      *before* you load real data, not after. See
      [Backup and restore]({{ '/admin/backup/' | relative_url }}).
- [ ] **Pick a file-storage adapter.** Default is GridFS (no extra service);
      Garage S3 is the production-grade option. See
      [Storage substrate]({{ '/admin/storage/' | relative_url }}).

## Hosting paths

shepard's full stack (Quarkus + Neo4j 5 + Mongo 8 + TimescaleDB +
optional PostGIS + Nuxt 3) needs roughly **8 GB RAM and 20 GB disk**
to run comfortably. That requirement narrows the hosting field.

### Comparison matrix

| Path | Hardware budget | RAM | Cost | TLS | Best for |
|---|---|---|---|---|---|
| Hetzner Cloud CX32 / CCX13 | Cloud VPS | 8–16 GB | €7–€15 / month | Caddy / Traefik | Production-shape on a budget |
| Bare-metal / lab box | Mini PC, NUC, lab server | 8–32 GB | €0 + power | Caddy / Traefik | Lab teams, on-prem demos |
| Managed-services split | n/a | per-service | €0 (free tiers) | per-service | Backend-only proof-of-concept |
| GitHub Codespaces / Gitpod | Ephemeral cloud dev | 8–16 GB | 60 hr/month free | Codespace HTTPS | Throwaway evaluations, code review on a live stack |

The default compose under `infrastructure/` bundles **Caddy** as a reverse
proxy with automatic Let's Encrypt; it covers the internet-exposed deploy
without further work. Swap in Traefik / nginx if your operator preference
runs that way; the upstream production guide on the GitLab wiki at
`gitlab.com/dlr-shepard/shepard` is the authoritative source for
production-shape deploys.

### Hetzner Cloud (cheapest paid)

A Hetzner **CX22** (2 vCPU, 4 GB, ~€4/month) is too small for shepard
in default shape; a **CX32** (4 vCPU, 8 GB, ~€7/month) is the workable
floor; **CCX13** (4 dedicated vCPU, 16 GB, ~€15/month) is the
production-shape reference the upstream team uses for staging. Same
docker-compose as everywhere else; the bundled Caddy gets you TLS
without further config.

### Bare-metal / lab box

Any always-on Linux box with 8 GB+ RAM and Docker installed can run
the full stack. Clone the repo, copy `infrastructure/.env.example` to
`.env`, rotate the secrets, then `docker compose up -d`. Caddy
fronts the stack; supply a public hostname (or a Cloudflare Tunnel)
to get a TLS cert.

### Managed-services split

For a **backend-only** proof-of-concept where you skip the frontend
and exercise shepard's REST API:

- **Neo4j AuraDB Free** — 50K nodes / 175K relationships limit.
  Plenty for the Showcase Seed.
- **MongoDB Atlas M0** — 512 MB, free forever. Good for files and
  structured-data payloads at small scale.
- **Render Free Postgres** — 1 GB, but **TimescaleDB extension is not
  available** on Render's managed Postgres; you'd have to drop
  timeseries from the demo or run TimescaleDB elsewhere.

The connection strings drop into the backend container's env
directly — `NEO4J_HOST`, `QUARKUS_MONGODB_CONNECTION_STRING`,
`QUARKUS_DATASOURCE_JDBC_URL`. See
[Configuration]({{ '/admin/config/' | relative_url }}) for the full
list of consumed environment variables.

This path's main draw is **no infra to manage**; its cost is
**provider sprawl** (three accounts, three dashboards, three billing
relationships). Not recommended unless you have a specific reason to
avoid running databases yourself.

### GitHub Codespaces / Gitpod

For **interactive evaluation only**. A 4-core Codespace with the
docker-in-docker feature can run shepard's compose end-to-end via
`docker compose up`; access is via Codespaces' auto-issued public
HTTPS URL on the forwarded port. The instance shuts down on idle and
data does not survive the codespace's lifecycle — fine for a
30-minute demo, not for anything you'd revisit.

## HDF5 (HSDS) — opt-in sidecar

The HDF5 payload kind is **off by default**. With the toggle off, every
`/v2/hdf-containers/...` endpoint returns `404 Not Found` and no HSDS HTTP
client is ever instantiated. See [`docs/reference/hdf-container.md`]({{ '/reference/hdf-container/' | relative_url }})
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

### Capacity planning

The compose service mounts `./hsds-storage` for the **POSIX** storage
backend (HSDS's default). Object-store backends (S3 / MinIO / Azure
Blob) are opt-in via HSDS upstream env vars — see
[`aidocs/35` §3](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md#3-storage-layer).

**Rule of thumb** (per HSDS docs): plan for **~1.2× the raw HDF5
size** on disk because of HSDS's chunk-store overhead. A 100 GB HDF5
file → ~120 GB on disk.

Add `./hsds-storage` (or your S3 bucket prefix, if you flip storage
backends) to your [backup checklist]({{ '/admin/backup/' | relative_url }}).
HSDS supplies the `hsadmin` CLI for higher-level export / import if you need
storage-backend migration.

### What's deferred

Until A5e ships the auth bridge, users authenticate to **HSDS
directly** with the admin credentials configured here (or per-user
Basic credentials you provision via `hsadmin`). Once A5e lands,
shepard mints per-user JWTs signed by a shared Keycloak realm and
the `h5pyd` ergonomics ("one credential, the shepard API key")
arrive — see [`aidocs/35` §5](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md#5-auth-bridge-the-trickiest-piece).

## Neo4j plugins

Operators get the **neosemantics ("n10s")** plugin enabled by default
(`NEO4J_PLUGINS=["n10s"]` plus `n10s.*` allowed in
`dbms.security.procedures.{allowlist,unrestricted}` in
`infrastructure/docker-compose.yml`). The plugin backs the new
`SemanticRepositoryType.INTERNAL` connector type — see
[Semantic repositories]({{ '/reference/semantic-repositories/' | relative_url }})
for the casual-user story.

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

shepard core stays small; new payload kinds, external integrations, and
identifier providers ship as `shepard-plugin-*` JARs that an operator drops
into `/deployments/plugins/` without rebaking the image. The first shipped
plugin is the [Helmholtz Unhide publish feed]({{ '/reference/unhide-publish/' | relative_url }})
(UH1a); HDF5, video, AAS, ePIC, DataCite, GridFS-vs-S3, and others queue
behind it.

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

The design rationale (drop-in JARs via Java `ServiceLoader`, not
compose-side sidecars or per-install Dockerfile forks) is recorded in
[ADR-0023]({{ '/aidocs/63-architecture-decision-log#adr-0023' | relative_url }}).

## Reverse proxy

`caddy` terminates TLS on ports 80 / 443 (and 443/UDP for HTTP/3). Configuration
lives in `infrastructure/proxy/Caddyfile`; static SSL material in
`infrastructure/proxy/ssl`. Local-development variants are under
`infrastructure-local/` (Keycloak + a developer-friendly compose file).

## Integrations

Third-party tools that integrate against a running shepard instance live under
[`docs/install/`](https://github.com/noheton/shepard/tree/main/docs/install) — e.g.
[Apache Superset]({{ '/install/superset/' | relative_url }}) for SQL-native BI
against the TimescaleDB hypertables.
