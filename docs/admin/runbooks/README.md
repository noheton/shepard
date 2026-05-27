---
layout: default
title: "Admin runbooks — index"
description: "One-page recovery and diagnosis runbooks for specific failure modes and operational procedures. Copy-paste ready; each runbook covers Prerequisites, numbered Steps with host indicators, Rollback, and End-state verification."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
---

# Admin runbooks — index

Operational runbooks for instance admins. Every runbook follows this contract:

- **Frontmatter**: `host:` tells you which machine the step runs on;
  `tested:` notes whether the procedure has been exercised end-to-end.
- **Prerequisites**: what you need before starting.
- **Steps**: numbered, host-tagged (`[nuclide]` / `[cube]` / `[operator-machine]`),
  with expected stdout for each.
- **Rollback**: how to undo if the procedure goes wrong.
- **End-state verification**: commands that confirm the desired state.

---

## Numbered runbooks (new)

| # | Runbook | Host | Scenario |
|---|---|---|---|
| 01 | [Generic cube hotpatch](01-generic-cube-hotpatch.md) | cube | Pull a new image tag, verify SHA-256 digest, rolling restart |
| 02 | [Orphan payload wipe](02-orphan-payload-wipe.md) | nuclide | Find Garage objects with no Neo4j FileReference; dry-run then wipe |
| 03 | [Rotate API key](03-rotate-api-key.md) | operator | Revoke a user/service key; issue replacement; update all callers |
| 04 | [Restore Neo4j](04-restore-neo4j.md) | nuclide | Load a `neo4j-admin database dump` artefact; verify migration chain |
| 05 | [Restore TimescaleDB](05-restore-timescaledb.md) | nuclide | Pause PgBouncer; `pg_restore`; resume; verify hypertables |
| 06 | [Restore Garage S3](06-restore-garage.md) | nuclide | Volume-level or object-level Garage recovery; reconcile with Neo4j |
| 07 | [Add instance-admin](07-add-instance-admin.md) | nuclide | Consume one-shot bootstrap token; grant instance-admin role |
| 08 | [Enable plugin](08-enable-plugin.md) | nuclide | Runtime-toggle an existing plugin; or install a new JAR + restart |
| 09 | [Permission repair](09-permission-repair.md) | nuclide | Diagnose 403/404 on a Collection; repair the Neo4j permission edge |
| 10 | [Cut a release](10-cut-a-release.md) | operator | Pre-flight → tag → build → GitHub Release + SBOM |
| 11 | [Postgres restore](11-postgres-restore.md) | nuclide | Four recovery scenarios: corrupt schema, accidental table drop, full-instance loss, point-in-time (Wal-G) |
| 12 | [Postgres collapse + restart](12-postgres-collapse-restart.md) | nuclide | Detect collapse → log capture → in-place restart → volume-preserving restart → wipe + restore |

---

## Pre-existing runbooks

| Runbook | Scenario |
|---|---|
| [migration-chain-integrity](migration-chain-integrity.md) | `/shepard/api/healthz/ready` DOWN with `INCOMPLETE_MIGRATIONS` or `DIFFERENT_CONTENT` |
| [restore-tsdb-container-neo4j-shadow](restore-tsdb-container-neo4j-shadow.md) | TimescaleDB has rows but Neo4j lost the `:TimeseriesContainer` shadow node |
| [docker-bind-mount-inode-drift](docker-bind-mount-inode-drift.md) | Caddy / `.env` edits not visible after `caddy reload`; single-file bind-mount inode staleness |
| [orphan-retention-policy](orphan-retention-policy.md) | Configure orphan-data retention windows, recover from delete-on-next-sweep state, query deletion audit trail (SM1a) |

---

## Quick decision guide

```
User reports 403 / 404 on a Collection they had yesterday
  → 09-permission-repair.md

/shepard/api/healthz/ready shows neo4j-migration-chain-readiness DOWN
  → migration-chain-integrity.md

Timeseries data in TimescaleDB but API returns 404 for the container
  → restore-tsdb-container-neo4j-shadow.md

Garage S3 shows objects but API can't serve them / referencing nodes missing
  → 02-orphan-payload-wipe.md (orphan audit) or 06-restore-garage.md (full restore)

Need to grant first admin to a fresh instance
  → 07-add-instance-admin.md

Need to roll out a hotfix to the DLR cube instance
  → 01-generic-cube-hotpatch.md

Caddyfile or .env edit didn't take effect in the running container
  → docker-bind-mount-inode-drift.md

Need to cut a tagged release + push to GHCR + attach SBOM
  → 10-cut-a-release.md

Operator needs to set how long orphan (unreferenced) payloads are kept
  → orphan-retention-policy.md

Container was accidentally set to delete-on-next-sweep and needs recovering
  → orphan-retention-policy.md §4

Postgres container exited / crash-looping / health check DOWN
  → 12-postgres-collapse-restart.md

Postgres data needs restoring from a pg_dump backup
  → 11-postgres-restore.md (choose scenario: corrupt schema / table drop / full-instance loss / point-in-time)
```

---

## Conventions

- **`[nuclide]`** — run on the nuclide server (hosts the production compose stack).
- **`[cube]`** — run on the DLR cube host (VPN required).
- **`[operator-machine]`** — run on your local workstation or any machine with API
  access and the `gh` CLI.
- `${NEO4J_PASSWORD}`, `${INSTANCE_ADMIN_API_KEY}`, etc. — source from
  `/opt/shepard/infrastructure/.env` or your secrets manager. Never commit these.
- All `docker compose` commands assume the working directory is
  `/opt/shepard/infrastructure/`.
