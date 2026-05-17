---
layout: default
title: System requirements
description: Hardware, OS, JVM, database, and network expectations for a production shepard instance.
---

These are starting-point requirements for a small to medium production
deployment. Real-world sizing depends on data volumes (timeseries cardinality,
file sizes, spatial extent) and concurrent users — treat the numbers below as
a floor, not a ceiling.

## Hardware (single-host minimum)

| Resource | Minimum | Notes |
|---|---|---|
| CPU | 4 vCPU | Backend JVM + Neo4j + MongoDB + Postgres on one host |
| Memory | 8 GiB | Backend `-Xmx2G`, Neo4j `2G heap + 3G page cache`, MongoDB `2G WiredTiger`, plus OS headroom |
| Disk | 100 GiB SSD | Project data sits under `/opt/shepard/{neo4j,mongodb,timescaledb,postgis,backend,caddy}` |
| Network | 1 Gbit/s LAN | On-premises is the supported posture; no cloud or internet egress is assumed |

The memory floor is set by the explicit `JAVA_OPTS=-Xms2G -Xmx2G` for the
backend and the `NEO4J_server_memory_*` settings in
`infrastructure/docker-compose.yml`. Larger instances should grow each store
independently rather than uniformly.

## Supported platforms

- **Linux x86_64** — primary target.
- **Linux arm64** — supported via the upstream multi-arch images
  (`mongo:8.0`, `neo4j:5.24`, `timescale/timescaledb:2.24.0-pg16`,
  `postgis/postgis:16-3.5`, `caddy:2`); shepard's own images
  (`backend:5.2.0`, `frontend:5.2.0`) inherit the architecture from the build
  pipeline — confirm `linux/arm64` is published if you require it.
- **Container runtime** — Docker Engine + Compose v2. The reference
  deployment is the docker-compose stack under `infrastructure/`.

## JVM

- **Java 21** is required for the backend. Verified in `backend/pom.xml`:
  `<maven.compiler.release>21</maven.compiler.release>`. The backend
  container ships its own JRE; you only need a host JDK if you build from
  source.

## Pinned database versions

From `infrastructure/docker-compose.yml`:

| Component | Version pinned |
|---|---|
| Neo4j | `neo4j:5.24` |
| MongoDB | `mongo:8.0` |
| PostgreSQL + TimescaleDB | `timescale/timescaledb:2.24.0-pg16` (Postgres 16) |
| PostgreSQL + PostGIS (optional) | `postgis/postgis:16-3.5` (Postgres 16) |
| Reverse proxy | `caddy:2` |
| Prometheus (optional) | `prom/prometheus:v3.9.1` |
| Grafana (optional) | `grafana/grafana:12.2.1-security-01` |
| MongoExpress (optional) | `mongo-express:latest` |

## Networking — exposed ports

The compose file publishes:

- `caddy` — `80/tcp`, `443/tcp`, `443/udp` (HTTP/3).
- `neo4j` — `7687/tcp` (Bolt). **Internal use only**; do not expose
  externally.
- `postgis` — `5433/tcp` mapped to container `5432`. **Internal use only**.

All other containers communicate over the internal `shepard`, `mongo`,
`influxdata`, and `frontend` Docker networks.

## Identity provider

shepard relies on an external **OIDC** issuer (`OIDC_AUTHORITY`,
`OIDC_PUBLIC`, `OIDC_ROLE`). The reference IdP is Keycloak — see
`infrastructure-local/keycloak_frontend-dev.json` for a developer realm
export. Production deployments are expected to bring their own institutional
IdP.

## Browser support

Per `architecture/src/02_architecture_constraints/`: Firefox ESR and the
latest Edge, desktop or tablet. The frontend is not tested on mobile form
factors.

## Out of scope

- Cloud-only deployments — shepard is positioned for on-premises operation.
- Internet egress at runtime — semantic SPARQL endpoints (e.g. Ontobee) and
  webhook subscribers are the only outbound flows; everything else stays
  inside the institutional network.
