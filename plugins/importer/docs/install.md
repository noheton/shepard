# shepard-plugin-importer — Install

**Status (PR-1).** The plugin module is included in the
backend image's default-active `with-plugins` Maven profile.
Operators consuming the published image get the plugin "for free"
at PR-1 scaffold scope — it registers with `PluginRegistry` but
exposes no user-visible surface yet.

## Prerequisites

- shepard ≥ 6.0.0-SNAPSHOT (PR-1 lands during this development line).
- Postgres + Flyway already in the backend image (PR-2 adds a
  Flyway migration; PR-1 itself adds none).
- `${SHEPARD_INSTANCE_SECRET}` env var set (PR-3 will use this to
  derive an AES-GCM key for encrypting source credentials at rest).

## Plugin discovery

The plugin is wired into the backend's `with-plugins` profile in
`backend/pom.xml`. No operator action is needed; on the next
restart, `shepard-admin plugins list` shows:

```
importer | 1.0.0-SNAPSHOT | ENABLED | true | Apache-2.0 | https://github.com/noheton/shepard
```

To disable: `shepard-admin plugins disable importer` (the override
is persisted per PM1e and survives restart).

## Migrations

- PR-1 ships **no migrations**.
- PR-2 will ship a Flyway SQL migration creating the
  `importer_run` table (Postgres; not a hypertable).

## Pitfalls (PR-1)

- The plugin currently has no REST surface — `GET /v2/imports`
  will 404 until PR-4 lands.
- `shepard-admin importer ...` subcommands will be missing until
  PR-5.
- Source credential encryption uses `${SHEPARD_INSTANCE_SECRET}`
  as the key seed (PR-3); proper KMS/Vault integration is a
  follow-up. **Do not run the plugin with the default
  `changeme`-style env var in production.**

## Known limitations

- Single in-process Quarkus shape (decision #1) — runs share the
  backend JVM. If a large import saturates the JVM heap, increase
  `MAX_HEAP` accordingly.
- Cooperative cancellation only — a runaway HTTP read in
  `DLRv5Source` (PR-3) takes up to the read-timeout to honour a
  cancel; the heartbeat reaper will eventually transition stalled
  rows to `FAILED` with `error_class=JOB_STALLED`.
