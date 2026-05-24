---
layout: default
title: Admin guide
description: Operator-facing reference — install, configure, upgrade, back up, and observe a shepard instance.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/
---

# Admin guide

This is the operator-facing reference for running shepard. Everything on this
page cites what is actually in the repository today; the broader
mutating CLI commands and a few sections marked "in design" remain on the
backlog ([`aidocs/16`](https://github.com/noheton/shepard/blob/main/aidocs/16-dispatcher-backlog.md)).

Persona: the operator / SRE / institute IT engineer responsible for keeping a
shepard instance healthy. If you are an end-user (researcher / data scientist),
start at the [user guide]({{ '/user-guide' | relative_url }}). If you are a
plugin author, see [the plugin docs convention]({{ '/reference/plugins/' | relative_url }}).

## Sections

| Page | When to read it |
|---|---|
| [System requirements]({{ '/admin/system-requirements/' | relative_url }}) | Before provisioning a host — hardware floor, supported platforms, pinned DB versions, exposed ports. |
| [Install]({{ '/admin/install/' | relative_url }}) | First-time deployment. Compose stack, profiles, deploy paths (bare-metal, paid VPS, managed-services split). |
| [Configuration]({{ '/admin/config/' | relative_url }}) | Tuning a running instance. Env vars, `application.properties`, runtime `:*Config` Neo4j singletons, CORS. |
| [Upgrade]({{ '/admin/upgrade/' | relative_url }}) | Moving from upstream shepard 5.2.0 or an earlier fork release to a newer cut. |
| [Backup and restore]({{ '/admin/backup/' | relative_url }}) | Per-substrate dump recipes (Neo4j, MongoDB, Postgres/TimescaleDB, PostGIS, S3, HSDS POSIX). |
| [Storage substrate]({{ '/admin/storage/' | relative_url }}) | File-storage adapter selection (GridFS, S3 via Garage), migration runbooks, capacity planning. |
| [Authentication]({{ '/admin/auth/' | relative_url }}) | OIDC + API-key model, role mapping, instance-admin role, audit trail. |
| [Observability]({{ '/admin/observability/' | relative_url }}) | Health endpoints, Prometheus scrape, bundled Grafana dashboard, k6 performance scripts, self-observability TS substrate. |
| [Security]({{ '/admin/security/' | relative_url }}) | CI security gates (SpotBugs, CodeQL, OWASP, Trivy, gitleaks), SBOM, secret rotation. |
| **Operator runbooks** ([`docs/admin/runbooks/`](https://github.com/noheton/shepard/tree/main/docs/admin/runbooks)) | One-page recovery + diagnosis runbooks for specific failure modes. Currently: **migration-chain-integrity** (Neo4j readiness DOWN with `INCOMPLETE_MIGRATIONS`); **restore-tsdb-container-neo4j-shadow** (re-establish Neo4j `HasAppId` + `appId` for orphaned TS containers); **docker-bind-mount-inode-drift** (Caddy/`.env` edits not visible to the container after `caddy reload`). |

Maintainer-facing pages — cutting a release, the GitHub Projects board — live
under `docs/ops/`; they are not part of the admin guide. See
[`docs/ops/cut-a-release.md`](https://github.com/noheton/shepard/blob/main/docs/ops/cut-a-release.md)
and [`docs/ops/github-projects-board-setup.md`](https://github.com/noheton/shepard/blob/main/docs/ops/github-projects-board-setup.md).

## Admin CLI — quick orientation

A read-only `shepard-admin` CLI ships in L1 Phase 1 — `features list`,
`health`, `migrations status [containerId]`. Build the uber-jar locally:

```bash
cd cli
mvn package -DskipTests
export SHEPARD_ADMIN_URL=https://shepard.example.com
export SHEPARD_ADMIN_API_KEY=<instance-admin-roled API key>
java -jar target/shepard-admin-*.jar features list
```

Full reference, sample output, and exit-code semantics in
**[Admin CLI (reference)]({{ '/reference/admin-cli/' | relative_url }})**.
Phase 2+ (cleanup of soft-deleted entities, RO-Crate import/export, the `init`
TUI wizard for first-run `.env`) is queued — see
[`aidocs/ops/22-admin-cli-draft.md`](https://github.com/noheton/shepard/blob/main/aidocs/ops/22-admin-cli-draft.md).

## Further reading

- [`aidocs/34-upstream-upgrade-path.md`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md) — change ledger for admins upgrading from upstream shepard 5.2.0
- [`aidocs/44-fork-vs-upstream-feature-matrix.md`](https://github.com/noheton/shepard/blob/main/aidocs/44-fork-vs-upstream-feature-matrix.md) — per-feature shipped/designed/pending matrix
- [Bibliography]({{ '/bibliography' | relative_url }}) — standards and regulations referenced from this guide (ISO/IEC 42001, EU AI Act, EU Machinery Regulation, Helmholtz FAIR requirements)
