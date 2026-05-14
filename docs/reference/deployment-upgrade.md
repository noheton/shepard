---
layout: default
title: Upgrade path (deployment reference)
permalink: /reference/deployment-upgrade/
description: Upgrading shepard — from upstream 5.2.0 to this fork's main; rolling restart vs blue-green; migration ordering; the V## sequence guarantees from aidocs/34.
---

# Upgrade path

shepard upgrades come in three flavours:

1. **Upstream → fork** — operator ran upstream `dlr-shepard/shepard
   5.2.0`; wants to swap to this fork.
2. **In-fork patch upgrade** — bump to a newer commit on this
   fork's `main`. The day-to-day case.
3. **Major DB version bump** — Neo4j 5 → 6, MongoDB 8 → 9,
   Postgres 16 → 17. Less frequent; coordinate with the DB
   release notes.

Each has its own runbook. **All three rely on a tested backup
taken just before the upgrade** — see
[backup + restore]({{ '/reference/deployment-backup/' | relative_url }}).

## The change ledger

The authoritative source for **what changed and what an admin
needs to do** is
[`aidocs/34-upstream-upgrade-path.md`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md).
Every merged change to `main` that touches admin-visible
surface gets a row there with one of these statuses:

- **ZERO** — additive only; nothing for an admin to do.
- **CONFIG** — admin should change config (backward-compatible
  alias / fallback in place).
- **AWARE** — admin should know about a new behaviour even
  though it's not breaking.
- **BREAKING** — a real breaking change with required admin
  action; spelled out in the row.

**Read the ledger before every upgrade.** Skim each row's
*Operator action* column for anything that mentions your
configuration. Most rows are ZERO; the few CONFIG / AWARE rows
are quick reads.

The fork has **no BREAKING rows** as of this writing — every
config-key rename has an alias path through to v6.0.

## Upstream → this fork

If you're running upstream `dlr-shepard/shepard 5.2.0` and want
to swap to this fork:

### Wire compatibility

- **`/shepard/api/...` is byte-frozen** with upstream 5.2.0.
  Existing clients (Python / TypeScript / Java generated against
  upstream OpenAPI) keep working unchanged.
- **`/v2/...` is this fork's development surface.** New
  endpoints land there; opt-in for clients that want them.
- See [the API-version policy in
  `CLAUDE.md`](https://github.com/noheton/shepard/blob/main/CLAUDE.md)
  for the standing rule.

### Operator quick-start

```bash
# 1. Stop upstream stack.
cd /opt/shepard && docker compose down

# 2. Run a backup of the upstream data before swapping. See
#    deployment-backup.md.
./backup.sh

# 3. Switch to this fork. Build locally or pull the fork's
#    published images.
cd /opt/shepard
git remote set-url origin https://github.com/noheton/shepard.git
git fetch origin main
git checkout main
docker compose build      # if building locally
# or: docker compose pull  # if using published GHCR tags

# 4. Optional one-time prep: rotate any default credentials per
#    aidocs/07 H8 (already-public placeholders).
$EDITOR infrastructure/.env

# 5. Bring up. V11+ idempotent Neo4j constraint additions apply
#    on first start; the post-A1e fail-fast catches any
#    migration error.
cd infrastructure && docker compose up -d
docker compose logs -f backend
# Watch for: "neo4j-migrations: applied V<N>" lines
# Watch for: ✓ Started Quarkus (...)
```

The frontend container doesn't need a separate migration step —
the backend is the only service with schema state.

### What's different vs upstream 5.2.0

The biggest admin-visible additions (a non-exhaustive sample;
see [`aidocs/44`](https://github.com/noheton/shepard/blob/main/aidocs/44-fork-vs-upstream-feature-matrix.md)
for the per-feature matrix):

- **Runtime admin REST + CLI parity** for features / plugins /
  ontologies / Unhide config (A3b, PM1b/e, N1c2, UH1a).
- **Bounded `MigrationsRunner` wait** with configurable timeout
  (A1; default `PT60S`). Upstream loops infinitely on a
  slow-booting Neo4j.
- **Per-DB health-check separation** — `/healthz` reports per-DB
  state.
- **`MigrationsRunner` fail-fast** on errors (A1e). A failed
  migration now aborts startup; upstream logs and continues
  silently.
- **Migration progress endpoint** — `GET /migrations/progress`
  (P3).
- **Semi-permanent API keys with expiry** — `validUntil` +
  JWT `exp` (L5).
- **Cypher injection closed** across the DAO surface (C5 +
  C5b).
- **`/v2/` API surface** for everything new (P4 routing
  scaffolding + ArchUnit fence).
- **OpenAPI spec splitting** — `/shepard/doc/openapi/v1.json`
  (compat) + `v2.json` (this fork) for clients that want one
  shelf or the other (P4c).
- **RFC 7807 error responses** (`application/problem+json`),
  with legacy `ApiError` preserved for upstream-client compat
  (H4).
- **Plugin system** — drop-in `shepard-plugin-*` JARs via
  ServiceLoader (PM1a–PM1e); bundled plugins for Unhide,
  HMC KIP, local-minter (no rebuild needed to add new payload
  kinds or integrations).
- **Instance-admin role + bootstrap-token mechanism** (A0).
- **m4i + RO + 8 other pre-seeded ontologies** in the n10s
  semantic repo (ONT1a / ONT1b / N1).

Read [`aidocs/34`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md)
for the per-change ledger.

## In-fork patch upgrade

The day-to-day case — `git pull` and restart.

### Pre-upgrade checklist

- [ ] **Read `aidocs/34` diff** since your current commit.
      `git log --oneline <current>..main aidocs/34-upstream-upgrade-path.md`
      shows the rows added since.
- [ ] **Back up** — see
      [backup + restore]({{ '/reference/deployment-backup/' | relative_url }}).
- [ ] **Check for `BREAKING` rows** in `aidocs/34` since your
      last upgrade (none exist as of 2026-05-08; the fork
      hasn't shipped any).
- [ ] **Schedule downtime** — a small-lab-scale upgrade is
      ~2 min; medium-institute is ~10 min once migrations run.

### Recipe

```bash
cd /opt/shepard/infrastructure
docker compose down

# Pull the new code + images
cd /opt/shepard
git fetch origin main
git pull origin main
cd infrastructure
docker compose pull   # if using published tags
# or: docker compose build  # if building locally

# Bring back up
docker compose up -d
docker compose logs -f backend
```

### Rolling restart (zero-downtime)

For ≥ 2 backend instances behind a load balancer:

```bash
# For each backend instance in turn:
#   1. Remove from LB (or set readiness to fail)
#   2. docker compose stop backend
#   3. docker compose up -d --no-deps backend
#   4. Wait for /healthz/ready
#   5. Re-add to LB
```

The shepard backend is **stateless** once JWT validation works
locally — rolling restarts are safe. The DBs are not in scope
for rolling restart (single-instance assumption in the
reference compose); HA-cluster DB upgrades follow their own
runbooks.

### Blue-green (preferred for multi-host scale)

1. Stand up the new version side-by-side at a different port /
   subdomain (`green.shepard.example.com`).
2. Run schema migrations against a **copy** of the production
   DB (so you find migration breakage on the copy, not
   production).
3. Flip the reverse proxy to point at the new version.
4. Keep the old version warm for 24 h; roll back by flipping
   the proxy back if needed.

This requires more infra (you need both stacks running at
once) but isolates blast radius cleanly.

## Migration ordering

The `MigrationsRunner` runs Neo4j migrations (`V<N>__*.cypher`)
and SQL migrations (`V<n>__*.sql`) at backend startup, in
**numeric V order**. The post-A1e fail-fast posture means a
failed migration aborts startup — the backend doesn't continue
in a partially-migrated state.

The fork's migration sequence (selection — see
`backend/src/main/resources/neo4j/migrations/` for the
authoritative list):

| Migration | Purpose | Adds |
|---|---|---|
| `V11__Add_appId_unique_constraints.cypher` | L2a | Per-label `appId` UNIQUE constraints for the L2 chain |
| `V12__Backfill_appId.cypher` | L2b | Backfills `appId` on legacy nodes (idempotent, chunked) |
| `V13__*` | A0 | `:Role` entity + constraint |
| `V14__Backfill_orphan_permissions.cypher` | C3 / A0 | Backfills `:has_permissions` edges on orphan entities, gated by `shepard.permissions.default-owner` |
| `V21__*` + `V22__*` | FR1a | File-bundle rename + `:FileGroup` migration |
| `V23__*` + `V24__*` | FR1b | Singleton-file-reference carve-out |
| `V25__*` | A5a | `:HdfContainer` constraint |
| `V29__*` + `V31__*` | KIP1a / KIP1h | `:Publication` entity + versioned-PID backfill |
| `V30__*` | UH1a | `:UnhideConfig` singleton constraint |

Every migration is:

- **Idempotent** — safe to re-run.
- **Fail-fast** — aborts startup on error (post-A1e).
- **Logged** — each `V<N>` line in the backend log on first
  start.

Some migrations ship a **rollback** file (`V##_R__*.cypher` —
e.g. `V12_R__Rollback_Backfill_appId.cypher`). These are
**operator-run only** — they don't run automatically; you
invoke them via `cypher-shell` if you need to undo.

The per-PR documentation of migrations lives in the same
`aidocs/34` row that ships the migration.

## Pre-upgrade DB version checks

The bundled compose pins specific DB versions
(see
[system requirements]({{ '/system-requirements/' | relative_url }})).
When you upgrade across a **major** DB version (Neo4j 5 → 6,
MongoDB 8 → 9, Postgres 16 → 17), check:

- **Neo4j** — the migration history (V## sequence) might use
  Cypher syntax that's deprecated in the next major. Pin to the
  Neo4j version range in `infrastructure/docker-compose.yml`'s
  comments before bumping. The n10s plugin tracks Neo4j majors;
  bump both together.
- **MongoDB** — `mongo-tools` version compatibility — the
  `mongodump` you used for the last backup must restore into the
  new mongod. Test the restore on a scratch host before doing
  the production upgrade.
- **Postgres + TimescaleDB** — the TimescaleDB version pins the
  Postgres major (`timescale/timescaledb:2.24.0-pg16` →
  Postgres 16). Cross-major Postgres upgrades require
  `pg_upgrade` or dump-and-restore; don't combine with a
  shepard upgrade in the same maintenance window.

## Rollback

Two rollback paths, by upgrade type:

### Rollback an in-fork upgrade

```bash
cd /opt/shepard
git checkout <previous-commit>
cd infrastructure
docker compose down
docker compose up -d
```

The DB state from before the upgrade is what you see — if the
upgrade ran any migrations, those are not auto-reverted; you'd
restore from the pre-upgrade backup or run the migration's
`V##_R__*.cypher` rollback file manually.

### Rollback to upstream

The fork is byte-frozen against upstream on `/shepard/api/`,
so rollback is mostly mechanical:

```bash
cd /opt/shepard
git remote set-url origin https://gitlab.com/dlr-shepard/shepard.git
git fetch origin
git checkout v5.2.0  # or whatever upstream tag
cd infrastructure
docker compose down
docker compose pull
docker compose up -d
```

The data layer's only fork-specific addition that **isn't**
backward-compatible with upstream is the `appId` property on
every node (L2a). Upstream ignores it — the rollback is
zero-touch from upstream's view. The fork-added Neo4j
constraints (`V11`, `V13`, `V14` etc.) are also ignored by
upstream.

Plugin data (Unhide harvest keys, DataCite credentials) is **not**
in the upstream schema — rolling back to upstream means those
plugins won't work until you upgrade back to the fork. That's
the only operator-visible regression.

## Verifying the upgrade

After a successful upgrade:

```bash
# 1. Backend is up
curl -fsS http://localhost:8080/shepard/api/healthz/ready | jq .

# 2. Migration history is current
shepard-admin migrations status

# 3. Plugins all ENABLED
shepard-admin plugins list

# 4. A representative API call works
curl -fsS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/shepard/api/collections | jq '.[0:5]'

# 5. Frontend loads and shows a Collection
```

A failed step at (1) means migration aborted — read the
backend log, find the `MigrationsException`, fix and retry.
A failed step at (2) means a migration didn't run — re-check
`shepard.migrations.connection-wait-timeout` and DB
connectivity. A failed step at (3) means a plugin is `FAILED`
— see [plugins
reference]({{ '/reference/plugins/#troubleshooting' | relative_url }}).

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [Backup + restore]({{ '/reference/deployment-backup/' | relative_url }}) — back up before upgrading.
- [Monitoring + observability]({{ '/reference/deployment-monitoring/' | relative_url }}) — wire alerts on migration failures.
- [Troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }})
- [`aidocs/34`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md) — the per-change admin ledger (read this).
- [`aidocs/44`](https://github.com/noheton/shepard/blob/main/aidocs/44-fork-vs-upstream-feature-matrix.md) — fork-vs-upstream feature matrix.
- [`CLAUDE.md` §API-version policy](https://github.com/noheton/shepard/blob/main/CLAUDE.md)
- [Plugins reference]({{ '/reference/plugins/' | relative_url }}) — verifying plugins post-upgrade.
- [Admin CLI reference]({{ '/reference/admin-cli/' | relative_url }}) — `shepard-admin migrations status`.
