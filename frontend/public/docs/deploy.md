---
layout: default
title: Deploy options
description: Hosting paths for shepard — comparison matrix and pointers.
---

# Deploy options

shepard's full stack (Quarkus + Neo4j 5 + Mongo 8 + TimescaleDB +
optional PostGIS + Nuxt 3) needs roughly **8 GB RAM and 20 GB disk**
to run comfortably. That requirement narrows the hosting field.

## Comparison matrix

| Path | Hardware budget | RAM | Cost | TLS | Best for |
|---|---|---|---|---|---|
| Hetzner Cloud CX32 / CCX13 | Cloud VPS | 8–16 GB | €7–€15 / month | Caddy / Traefik | Production-shape on a budget |
| Bare-metal / lab box | Mini PC, NUC, lab server | 8–32 GB | €0 + power | Caddy / Traefik | Lab teams, on-prem demos |
| Managed-services split | n/a | per-service | €0 (free tiers) | per-service | Backend-only proof-of-concept |
| GitHub Codespaces / Gitpod | Ephemeral cloud dev | 8–16 GB | 60 hr/month free | Codespace HTTPS | Throwaway evaluations, code review on a live stack |

The default compose under `infrastructure/` bundles **Caddy** as a
reverse proxy with automatic Let's Encrypt; it covers the
internet-exposed deploy without further work. Swap in Traefik / nginx
if your operator preference runs that way; the upstream production
guide on the GitLab wiki at `gitlab.com/dlr-shepard/shepard` is the
authoritative source for production-shape deploys.

## Hetzner Cloud (cheapest paid)

A Hetzner **CX22** (2 vCPU, 4 GB, ~€4/month) is too small for shepard
in default shape; a **CX32** (4 vCPU, 8 GB, ~€7/month) is the workable
floor; **CCX13** (4 dedicated vCPU, 16 GB, ~€15/month) is the
production-shape reference the upstream team uses for staging. Same
docker-compose as everywhere else; the bundled Caddy gets you TLS
without further config.

## Bare-metal / lab box

Any always-on Linux box with 8 GB+ RAM and Docker installed can run
the full stack. Clone the repo, copy `infrastructure/.env.example` to
`.env`, rotate the secrets, then `docker compose up -d`. Caddy
fronts the stack; supply a public hostname (or a Cloudflare Tunnel)
to get a TLS cert.

## Managed-services split

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
`QUARKUS_DATASOURCE_JDBC_URL`.

This path's main draw is **no infra to manage**; its cost is
**provider sprawl** (three accounts, three dashboards, three billing
relationships). Not recommended unless you have a specific reason to
avoid running databases yourself.

## GitHub Codespaces / Gitpod

For **interactive evaluation only**. A 4-core Codespace with the
docker-in-docker feature can run shepard's compose end-to-end via
`docker compose up`; access is via Codespaces' auto-issued public
HTTPS URL on the forwarded port. The instance shuts down on idle and
data does not survive the codespace's lifecycle — fine for a
30-minute demo, not for anything you'd revisit.

## Pre-deploy checklist (all paths)

Independent of where you host:

- [ ] **Rotate `.env` defaults.** `aidocs/07` H8 explicitly flags the
      shipped `POSTGRES_*`, `NEO4J_PW`, `MONGO_PASSWORD`, etc. as
      already-public placeholders.
- [ ] **Restrict CORS.** `quarkus.http.cors.origins=*` in the bundled
      `application.properties` is permissive; tighten before any
      internet-exposed deployment (`aidocs/07` C2).
- [ ] **Decide on auth.** All paths assume an OIDC provider. The
      simplest path is to add a Keycloak service to the same compose;
      a managed OIDC is less ops but shifts trust to a third party.
- [ ] **Decide on backups.** Each guide gives a recipe; pick one
      *before* you load real data, not after.

For the production-shape deploy on bare metal or a paid VPS, the
authoritative guide remains the GitLab wiki at
`gitlab.com/dlr-shepard/shepard`.
