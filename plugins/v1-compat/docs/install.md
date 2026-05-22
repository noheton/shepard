# shepard-plugin-v1-compat — install guide

Phase 1 marker plugin. Ships with every standard shepard image; you
do not need to install it manually. This page exists for operators
running custom builds or wanting to disable the plugin entirely.

## Prerequisites

- shepard backend `>= 6.0.0-SNAPSHOT` (the plugin's
  `shepardCompatibility` is `>=6.0.0-SNAPSHOT,<7`)
- Neo4j running (the V63 Cypher migration runs at startup)

## Default install

The plugin is included in the backend's `with-plugins` Maven profile
(active by default). A standard `docker compose up` brings it up
automatically. To verify the plugin loaded:

```bash
curl -H "X-API-KEY: <admin-key>" https://<host>/v2/admin/plugins \
  | jq '.[] | select(.id=="v1-compat")'
```

Expected output:

```json
{
  "id": "v1-compat",
  "version": "1.0.0-SNAPSHOT",
  "shepardCompatibility": ">=6.0.0-SNAPSHOT,<7",
  "state": "ENABLED",
  "enabled": true,
  "title": "v1 Compat Surface",
  "licence": "Apache-2.0"
}
```

## Config keys

The plugin reads two install-time defaults from
`application.properties`. Both seed the runtime singleton on first
start and are then superseded by the
`:LegacyV1Config` row's runtime value (per CLAUDE.md
"Always: surface operator knobs in the admin config" precedence).

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.v1-compat.enabled` | `true` | Master plugin toggle. The Phase 1 design overrides the usual "all-plugins-opt-in" default because this plugin IS the byte-compat contract — disabling it would 410-storm any downstream tooling still hitting `/shepard/api/...`. The right operator gesture is to flip the singleton `enabled` field at the data layer; the plugin toggle stays on so the admin REST + stats endpoint remain reachable. |
| `shepard.legacy.v1.enabled` | `true` | Deploy-time install default for the singleton's `enabled` field. Seeds the singleton when V63 fires on first start; thereafter the runtime row wins. |

## Migration

`backend/src/main/resources/neo4j/migrations/V63__Bootstrap_legacy_v1_config.cypher`:

- Creates `CREATE CONSTRAINT LegacyV1Config_appId_unique IF NOT EXISTS`
  for appId uniqueness on the singleton label.
- `MERGE (c:LegacyV1Config) ON CREATE SET ...` seeds the singleton
  with `enabled=true`. The ON CREATE clause means SET only fires on
  the first insertion — re-running the migration NEVER overwrites a
  runtime-mutated value.

To roll back the migration manually:

```cypher
MATCH (c:LegacyV1Config) DETACH DELETE c;
DROP CONSTRAINT LegacyV1Config_appId_unique IF EXISTS;
```

## Healthcheck

There is no plugin-specific healthcheck endpoint. The plugin
participates in the global `/v2/admin/plugins` listing per the
`PluginRegistry`. State `ENABLED` + `enabled=true` is the green
state; `DISABLED` / `FAILED` indicates a problem.

## Disabling the plugin entirely

To skip the plugin at build time:

```bash
mvn -DnoPlugins package
```

…or in `application.properties`:

```properties
shepard.plugins.v1-compat.enabled=false
```

When the plugin is fully off, the legacy v1 surface still works
exactly as upstream did (no gating, no headers, no telemetry). The
admin REST + stats endpoint disappear from the OpenAPI; an operator
who needs the runtime knob back must re-enable the plugin first.

## Known pitfalls

- **Disabling the plugin at deploy time loses the audit trail.** If
  you want to keep the byte-compat surface on but stop the
  deprecation telemetry, leave the plugin enabled and ignore the
  stats endpoint. The plugin's runtime cost is two filter passes
  per `/shepard/api/...` request (constant-time counter increment +
  three header writes); negligible compared to a JWTFilter pass.
- **Flipping `:LegacyV1Config.enabled=false` on a live system is
  observable to every downstream tool.** Before flipping, inspect
  `/v2/admin/legacy/v1/stats` — if there have been recent hits from
  unrecognised principals or IPs, surface those first. The Phase 2
  design (`aidocs/103`) will add a confirmation gate; Phase 1 trusts
  the admin to read the stats first.

## References

- `aidocs/platform/103a-v1-compat-marker-plugin.md` — Phase 1 design
- `aidocs/platform/103-v1-compat-plugin-extraction.md` — Phase 2 design
- `docs/reference/v1-deprecation.md` — operator-facing runbook
- `aidocs/34-upstream-upgrade-path.md` — upstream-upgrade ledger
