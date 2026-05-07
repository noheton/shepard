---
layout: default
title: Deploy options
description: Hosting paths for shepard — free / low-cost / self-hosted — with a comparison matrix and pointers to the deep guides.
---

# Deploy options

shepard's full stack (Quarkus + Neo4j 5 + Mongo 8 + TimescaleDB +
optional PostGIS + Nuxt 3) needs roughly **8 GB RAM and 20 GB disk**
to run comfortably. That requirement narrows the hosting field; this
page surveys what's realistic, with deep guides for the two paths most
shepard users actually take.

## Comparison matrix

| Path | Hardware budget | RAM | Cost | TLS | NAT-friendly | Best for |
|---|---|---|---|---|---|---|
| **[Oracle Cloud Free Tier (Ampere ARM)](deploy-oracle-free)** | Cloud, public IPv4 | 24 GB | €0 | Caddy + Let's Encrypt (HTTP-01) | n/a (public IP) | Demos, reviewer access, CI test instances |
| **[Self-hosted Docker host behind Zoraxy](deploy-self-hosted-zoraxy)** | Mini PC, NUC, lab server | 8–32 GB | €0 + power | Zoraxy + Let's Encrypt (HTTP-01 / DNS-01) | yes — Cloudflare Tunnel pairs cleanly | Lab teams, on-prem demos, anyone with hardware to spare |
| Hetzner Cloud CX22 / CCX13 | Cloud VPS | 4–16 GB | €4–€15 / month | Caddy / Zoraxy / Traefik | n/a | Production-shape on a budget |
| Managed-services split | n/a | per-service | €0 (free tiers) | per-service | n/a | Backend-only proof-of-concept |
| Fly.io free allowance | Cloud edge | 256 MB × 3 | €0 | Fly TLS | n/a | Toy stack only — won't fit shepard's data tier |
| GitHub Codespaces / Gitpod | Ephemeral cloud dev | 8–16 GB | 60 hr/month free | Codespace HTTPS | n/a | Throwaway evaluations, code review on a live stack |

The two **deep guides** below cover the paths that fit shepard's
multi-DB stack without surgery.

## Path 1 — Oracle Cloud Free Tier

Best when you want **a public, always-on test instance** and don't have
hardware. The Ampere Free Tier gives 4 OCPU + 24 GB RAM at €0; the
caveat is that shepard's published `backend` and `frontend` images are
amd64-only at the time of writing and either need rebuilding for
`linux/arm64` or running under QEMU emulation.

→ **[deploy-oracle-free](deploy-oracle-free)**

## Path 2 — Self-hosted Docker host behind Zoraxy

Best when you have **a mini PC, NUC, or always-on lab box** and want
control over the data without paying for cloud. Pairs nicely with
Cloudflare Tunnel if you don't have a public IP. Uses
[Zoraxy](https://github.com/tobychui/zoraxy) — a Go-based reverse
proxy with a web UI, automatic Let's Encrypt, geo-IP / TOTP / access
lists, and statistics — instead of the bundled Caddy.

→ **[deploy-self-hosted-zoraxy](deploy-self-hosted-zoraxy)**

## Other paths in brief

### Hetzner Cloud (cheapest paid)

A Hetzner **CX22** (2 vCPU, 4 GB, ~€4/month) is too small for shepard
in default shape; a **CX32** (4 vCPU, 8 GB, ~€7/month) is the workable
floor; **CCX13** (4 dedicated vCPU, 16 GB, ~€15/month) is the
production-shape reference the upstream team uses for staging. Same
docker-compose as everywhere else; reuse the bundled Caddy or swap in
Zoraxy per the self-hosted guide. No surprises.

### Managed-services split

For a **backend-only** proof-of-concept where you skip the frontend
and exercise shepard's REST API:

- **Neo4j AuraDB Free** — 50K nodes / 175K relationships limit. Plenty
  for the Showcase Seed.
- **MongoDB Atlas M0** — 512 MB, free forever. Good for files and
  structured-data payloads at small scale.
- **Render Free Postgres** — 1 GB, but **TimescaleDB extension is not
  available** on Render's managed Postgres; you'd have to drop
  timeseries from the demo or run TimescaleDB elsewhere.

The connection strings drop into the backend container's env
directly — `NEO4J_HOST`, `QUARKUS_MONGODB_CONNECTION_STRING`,
`QUARKUS_DATASOURCE_JDBC_URL`. The backend itself can run on Fly.io's
free shared-CPU VM (256 MB is too tight for the JVM; bump to a paid
shared-cpu-1x at ~$2/month).

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

### Fly.io

The free allowance (3 × 256 MB shared-cpu-1x) is too small for any
single service in shepard's stack — Neo4j alone wants ≥ 1.5 GB, Mongo
wants ≥ 512 MB, the Quarkus JVM with `-Xmx2G`. Skipping unless you
upgrade to a paid plan, at which point Hetzner is cheaper and simpler.

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
`gitlab.com/dlr-shepard/shepard`. The pages here are scoped to the
free/low-cost paths the GitHub mirror users typically take.
