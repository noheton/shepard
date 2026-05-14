---
layout: default
title: Deploying shepard (reference)
permalink: /reference/deployment/
description: Front door for operators deploying shepard to production. Lists the specialized runbooks for OIDC, storage, secrets, backup, monitoring, upgrade, TLS, and troubleshooting.
---

# Deploying shepard

This page is the **front door for operators** standing up a
production shepard instance — against real OIDC, real DNS + TLS,
real storage, real backups. It cross-links the specialized
runbooks. Pick whichever step is in front of you; nothing here
assumes you've read the others.

Just kicking the tyres on a laptop?
**[Quickstart: local demo]({{ '/reference/deployment-quickstart/' | relative_url }})**
is the five-minute path. Come back here when you're ready for
something durable.

## The ten-minute orientation

shepard is a Quarkus backend, a Nuxt 3 frontend, three required
databases (Neo4j, MongoDB, Postgres+TimescaleDB), an OIDC
identity provider you bring yourself, and a reverse proxy. The
reference deploy is a `docker compose` stack at
`infrastructure/docker-compose.yml`.

- **Sizing.** 4 vCPU + 8 GiB RAM + 100 GiB SSD is the single-host
  floor (see [sizing]({{ '/reference/deployment-sizing/' | relative_url }})).
- **Identity.** shepard does not ship an identity provider. You
  bring an OIDC issuer — Keycloak, Helmholtz AAI, a university
  SAML→OIDC bridge, GitHub OAuth (see
  [OIDC setup]({{ '/reference/deployment-oidc/' | relative_url }})).
- **Storage.** Files default to MongoDB GridFS. Swapping in
  S3-compatible storage (Garage, MinIO, AWS S3, Wasabi) is
  designed and queued behind FS1b — see
  [storage backends]({{ '/reference/deployment-storage/' | relative_url }}).
- **Secrets.** Per-instance static key today (`CredentialCipher`);
  Vault / KMS integration is queued. See
  [secrets management]({{ '/reference/deployment-secrets/' | relative_url }})
  for what you're getting and what's coming.
- **Backups.** Each database backs up independently
  (`neo4j-admin database dump`, `mongodump`, `pg_dump`). See
  [backup + restore]({{ '/reference/deployment-backup/' | relative_url }}).
- **Upgrade.** Track
  [`aidocs/34-upstream-upgrade-path.md`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md)
  for the per-change admin ledger; the
  [upgrade runbook]({{ '/reference/deployment-upgrade/' | relative_url }})
  covers rolling-restart vs blue-green vs cold-cut over.

## The runbooks

In rough order of "things you need to think about" when standing
up a fresh instance:

1. **[Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})** — DNS, TLS, OIDC, storage, secrets, ports, default passwords. Tick the boxes before `docker compose up`.
2. **[Sizing recommendations]({{ '/reference/deployment-sizing/' | relative_url }})** — CPU / RAM / disk per service. Small lab → medium institute → multi-tenant.
3. **[OIDC / authentication]({{ '/reference/deployment-oidc/' | relative_url }})** — Keycloak realm import; Helmholtz AAI; university SAML→OIDC; GitHub OAuth.
4. **[Storage backends]({{ '/reference/deployment-storage/' | relative_url }})** — GridFS today; S3-compatible (Garage / MinIO / SeaweedFS / AWS S3 / Wasabi) on the FS1b roadmap.
5. **[Secrets management]({{ '/reference/deployment-secrets/' | relative_url }})** — `CredentialCipher` per-instance key; Vault / KMS futures.
6. **[Backup + restore]({{ '/reference/deployment-backup/' | relative_url }})** — `neo4j-admin database dump`, `mongodump`, `pg_dump`, `influx backup`; tested-restore obligation.
7. **[Monitoring + observability]({{ '/reference/deployment-monitoring/' | relative_url }})** — health endpoints, Prometheus + Grafana, log aggregation, alerting recipes.
8. **[Upgrade path]({{ '/reference/deployment-upgrade/' | relative_url }})** — rolling restart, blue-green, migration ordering, cross-link to `aidocs/34`.
9. **[TLS + reverse proxy]({{ '/reference/deployment-tls/' | relative_url }})** — Caddy / Nginx / Traefik; cert-manager / Let's Encrypt automation.
10. **[Troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }})** — "Backend won't start", "Login redirects fail", "Plugin shows FAILED", "Migration aborted".

## What this fork adds for operators

Most of the new operator surface in this fork compared to
upstream 5.2.0 is **additive** — runtime admin endpoints +
`shepard-admin` CLI parity for things upstream made
deploy-time-only:

- **Runtime feature toggles** — `GET/PATCH /v2/admin/features` (A3b)
  + `shepard-admin features list / enable / disable`.
- **Runtime plugin registry** — `GET/PATCH /v2/admin/plugins`
  (PM1b/e — persisted across restart) + `shepard-admin plugins
  list / enable / disable`. See
  [plugins reference]({{ '/reference/plugins/' | relative_url }}).
- **Runtime ontology preseed** —
  `GET/PATCH /v2/admin/semantic/ontologies` (N1c2) +
  `shepard-admin semantic ...`.
- **Runtime Unhide-publish config** —
  `GET/PATCH /v2/admin/unhide/config` (UH1a/c) +
  `shepard-admin unhide ...`.
- **Instance-admin role + bootstrap token** — file-on-disk
  one-shot token to mint the first admin API key without going
  through the UI (A0; see
  [admin CLI]({{ '/reference/admin-cli/' | relative_url }})).
- **Per-DB health-check separation** — `/healthz/{live,ready,started}`
  surface per-database `state` + `kind` (A1b/c/f).
- **Comprehensive migration progress** — `GET /migrations/progress`
  for ops dashboards (P3).

Every endpoint is `@RolesAllowed("instance-admin")`-gated; rotate
the bootstrap-token API key into a long-lived key (or per-admin
keys) before exposing the admin pane to humans.

## Upgrading from upstream

If you ran upstream `dlr-shepard/shepard 5.2.0` and want to swap
to this fork:

- Wire shape on `/shepard/api/...` stays byte-frozen — existing
  clients keep working unchanged.
- New surface lands at `/v2/...` — opt-in for clients that want
  the additions.
- The per-change ledger lives at
  [`aidocs/34-upstream-upgrade-path.md`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md).
  Read the **TL;DR** + skim each row's *Operator action* column;
  rows are sorted oldest-first so a fresh upgrade is a linear
  read.
- The shape of the change ledger is "additive everywhere except
  one config-key namespace rename (A3c) which is
  alias-compatible until v6.0." There's no destructive migration
  to run.

The [upgrade runbook]({{ '/reference/deployment-upgrade/' | relative_url }})
turns the above into a step-by-step.

## Day-2 operations

Once you're up and running, the
[admin guide]({{ '/admin/' | relative_url }})
covers day-2 work — running, monitoring, troubleshooting
**features** (HDF5 sidecar, plugins, n10s, Grafana). The split:

- **`deployment*.md`** (this section) — day-1 deployment.
- **`admin.md`** — day-2 operations.

If you're not sure which you need, you're probably looking for
this section.

## Documentation surface

These pages live at:

- **Public:** `https://noheton.github.io/shepard/reference/deployment/`
  (this site).
- **In-app:** the `/help` route in the shepard frontend
  (D1a — design landed at
  [`aidocs/49`](https://github.com/noheton/shepard/blob/main/aidocs/49-in-app-user-docs.md);
  the frontend bundle pulls `docs/reference/deployment*.md`
  from this same source).

Both surfaces serve the same Markdown; updates to either land in
the same PR. (D1a hasn't shipped yet — the in-app `/help` shows
the public docs site until then.)

## See also

- [Quickstart: local demo]({{ '/reference/deployment-quickstart/' | relative_url }}) — DX5a `make demo-up` for evaluation.
- [Plugins reference]({{ '/reference/plugins/' | relative_url }}) — drop-in JARs (HDF5, video, AAS, minters, Unhide feed).
- [Admin CLI reference]({{ '/reference/admin-cli/' | relative_url }}) — `shepard-admin` verbs + shared flags.
- [API reference]({{ '/reference/api/' | relative_url }}) — REST surface, `/v2/` shelf, OpenAPI per-shelf splits.
- [`aidocs/34`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md) — admin-facing upgrade ledger.
- [`aidocs/49`](https://github.com/noheton/shepard/blob/main/aidocs/49-in-app-user-docs.md) — docs architecture (public site + in-app `/help`).
