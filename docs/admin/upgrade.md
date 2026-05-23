---
layout: default
title: Upgrade
description: Upgrading from upstream shepard 5.2.0 or an earlier fork release.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/upgrade/
---

# Upgrade

This page is the operator's quick-orient checklist for upgrading. The
authoritative per-change ledger lives in
[`aidocs/34-upstream-upgrade-path.md`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md) —
every entry in that table reflects something materially visible to an
admin (config keys, endpoints, schemas, defaults, dependencies, breaking
behaviour).

## Upgrade posture — what this fork promises

Per the API-version policy in `CLAUDE.md`:

- **`/shepard/api/...` stays byte-compatible with upstream
  shepard 5.2.0.** A client built against upstream keeps working
  against this fork. No new behaviour, no removed fields, no schema
  drift on the v1 surface.
- **`/v2/...` is the additive development surface.** This is where
  all fork features land. Adopting them is opt-in: operators choose
  when their downstream tooling can consume them.

For an admin upgrading from upstream:

- `/shepard/api/...` works exactly like upstream — zero breakage.
- `/v2/...` is opt-in additional surface — you choose when to consume it.

## v1 deprecation control plane

The `shepard-plugin-v1-compat` plugin ships a `:LegacyV1Config` singleton +
admin REST + frontend banner so **operators decide when to disable the
upstream `/shepard/api/...` surface**. The fork imposes no global sunset
timeline. See [v1 deprecation]({{ '/reference/v1-deprecation/' | relative_url }}).

## Upstream image pin

The Neo4j and MongoDB images are pinned with explicit comments in
`infrastructure/docker-compose.yml` pointing at the upstream upgrade guides
(MR-315 for Neo4j 4.4 → 5.24; MR-306 for MongoDB step upgrades). Read those
before bumping major versions.

### Neo4j 5 → 6 upgrade note

The n10s plugin tracks Neo4j major versions. When upgrading Neo4j across a
major boundary, also bump the n10s version (the `NEO4J_PLUGINS=["n10s"]`
env var auto-resolves to the version matching the running Neo4j image). Plan
a single restart that includes both, watch the bootstrap log line for
`n10s INTERNAL semantic repository ready` on first start after the upgrade.

## Migrations runner

Per `CLAUDE.md §"Always: maintain the upstream upgrade path"`:

- Cypher migrations live under
  `backend/src/main/resources/neo4j/migrations/`
  (e.g. `V49__Bootstrap_internal_semantic_repository.cypher`).
- SQL migrations live under
  `backend/src/main/resources/db/migration/`.
- Migrations are **idempotent** (safe to re-run) and **fail-fast** (abort
  startup on error — `MigrationsRunner` post-A1e propagates
  `MigrationsException`).
- Rollback files follow the `V##_R__*.cypher` convention when a change is
  data-mutating.

## Upgrade procedure (typical)

1. **Read the change ledger.** Walk every row in
   [`aidocs/34`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md)
   that landed since your current version. Each row spells out what an
   admin must do (or what migration runs automatically).
2. **Snapshot every substrate.** See
   [Backup and restore]({{ '/admin/backup/' | relative_url }}).
3. **Pull the new images, bring the stack down, bring it back up.**

   ```bash
   cd infrastructure
   docker compose --env-file .env pull
   docker compose --env-file .env up -d
   ```

4. **Watch the startup log.** Migrations run on backend start; any
   failure aborts the boot and you fall back to the previous image.
5. **Verify health.**

   ```bash
   curl -fsS https://shepard.example.com/shepard/api/healthz/ready && echo OK
   ```

6. **Walk the post-upgrade rows in [`aidocs/34`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md)** for any
   operator-side flips you need to do (e.g. flipping a feature toggle,
   uploading an ontology, enabling a plugin).

## Plugin compatibility

Plugins are loaded from `/deployments/plugins/` at startup
(`PluginRegistry`); their lifecycle is independent of the backend image.
When upgrading the backend, check each loaded plugin's release notes for
breaking SPI changes — bump plugin JARs alongside the backend if so.

```bash
shepard-admin plugins list
# Plugin       Version   State      Source
# unhide       1.0.0     ENABLED    build classpath
# file-s3      1.0.0     ENABLED    /deployments/plugins/shepard-plugin-file-s3-1.0.0.jar
```

## When upgrade fails

- Most failures are **migration failures** — read the backend startup log.
  Migrations abort the boot before the REST surface comes up.
- Fall back by re-deploying the previous image tag and restoring the
  snapshot taken before the upgrade.
- File a bug report on
  [github.com/noheton/shepard/issues](https://github.com/noheton/shepard/issues)
  with the failing migration name and the log excerpt.
