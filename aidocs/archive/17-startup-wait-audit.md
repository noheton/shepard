---
stage: decommissioned
last-stage-change: 2026-05-23
---

# 17 — Startup Wait / Retry Audit

Snapshot date: 2026-05-05. Follow-up from `16-dispatcher-backlog.md`
A1d, which itself is a follow-up from A1 (commit `a74d278`). A1 bounded
the Neo4j connection-wait infinite-loop in `MigrationsRunner` /
`NeoConnector` to a configurable ceiling
(`shepard.migrations.connection-wait-timeout`, default `PT60S`). This
note audits the equivalent startup-wait semantics for the non-Neo4j
databases — MongoDB, TimescaleDB (default datasource), and PostGIS
(spatial datasource) — and reconciles them with the same ~60s
fail-fast philosophy.

## Per-database findings

Quarkus version: **3.27.2** (`backend/pom.xml`). Property names below
were verified against
`META-INF/quarkus-config-doc/quarkus-config-model.json` shipped in each
extension JAR.

### MongoDB (`quarkus-mongodb-client`)

| Aspect | Observed | Match 60s ceiling? |
|---|---|---|
| `quarkus.mongodb.connect-timeout` | unset → MongoDB driver default `connectTimeoutMs=10000` (10s) | yes (faster) |
| `quarkus.mongodb.server-selection-timeout` | unset → driver default `serverSelectionTimeoutMs=30000` (30s) | yes (faster) |
| Health check on startup | disabled (`quarkus.mongodb.health.enabled=false`); A1b's `MongoStartupCheck` now drives readiness | n/a |

**Change applied:** **none.** Both driver defaults already fail fast
within the 60s ceiling. Per the A1d brief — "only add properties whose
effective default differs from the Neo4j 60s ceiling. Don't pollute
config with redundant values" — no explicit values were added. If the
driver's defaults drift in a future MongoDB upgrade we may want to lock
them, but that is out of scope for this audit.

### TimescaleDB default datasource (`quarkus-jdbc-postgresql` + Agroal + Flyway)

| Aspect | Observed | Match 60s ceiling? |
|---|---|---|
| `quarkus.datasource.jdbc.acquisition-timeout` | unset → Agroal default `5S` | yes (faster) |
| `quarkus.flyway.migrate-at-start` | `true` | n/a |
| `quarkus.flyway.connect-retries` | unset (= 0 retries) | partial — fails on first JDBC error rather than waiting for the database to come up |
| `quarkus.flyway.connect-retries-interval` | unset → Flyway/Quarkus default `120s` | **no** — single interval already exceeds the 60s ceiling |

**Change applied:** added two properties to `application.properties`:

```properties
quarkus.flyway.connect-retries=10
quarkus.flyway.connect-retries-interval=PT5S
```

Total ceiling ≈ `10 * 5s = 50s`, comfortably inside the 60s Neo4j
window. Without these, Flyway either gives up on the first error
(if `connect-retries=0`) or — if a future Quarkus default flips
non-zero — would wait 120s per retry, exceeding the ceiling.

### PostGIS spatial datasource (`quarkus-jdbc-postgresql` + Agroal + Flyway, named `spatial`)

| Aspect | Observed | Match 60s ceiling? |
|---|---|---|
| `quarkus.datasource.spatial.jdbc.acquisition-timeout` | unset → Agroal default `5S` (per-DS, not inherited from default DS) | yes (faster) |
| `quarkus.flyway.spatial.migrate-at-start` | `true` (when `shepard.spatial-data.enabled=true`) | n/a |
| `quarkus.flyway.spatial.active` | `${shepard.spatial-data.enabled}` (default `false` in prod, `true` in `%dev`) | n/a |
| `quarkus.flyway.spatial.connect-retries` | unset (= 0) | partial |
| `quarkus.flyway.spatial.connect-retries-interval` | unset → `120s` | no |

**Change applied:** added the same two retries to the spatial block:

```properties
quarkus.flyway.spatial.connect-retries=10
quarkus.flyway.spatial.connect-retries-interval=PT5S
```

Inherits the same ~50s ceiling. Only takes effect when the spatial
toggle is on; when off, `quarkus.flyway.spatial.active=false` short-
circuits the migration entirely (no retry loop runs).

### Neo4j (reference, set by A1)

`shepard.migrations.connection-wait-timeout=PT60S` — bounded loop in
`MigrationsRunner.waitForConnection` and `NeoConnector` with
exponential backoff. This is the source of the 60s ceiling that the
other databases are aligned against.

## Orchestrator probes

`infrastructure/docker-compose.yml` does not configure a healthcheck on
the `backend` service itself (only on `keycloak` —
`infrastructure-local/docker-compose.yml:111-115`, 15s interval × 15
retries). No Helm chart or Kubernetes manifest is checked into this
repo (searched for `livenessProbe` / `readinessProbe` /
`startupProbe` — no matches). The de-facto fail-fast for production
deployments therefore depends on the orchestrator the operator
chooses; the application-side ceilings configured here are the only
guaranteed ceiling.

A1b shipped `/shepard/api/healthz/{started,ready,live}` endpoints
backed by `DbHealthState` and per-DB pingers; an orchestrator that
binds `startupProbe` to `/started` and `livenessProbe` to `/live`
will restart the pod when the JVM hangs even if a future driver
default ignores the limits set here. That is the recommended
deployment pattern but is not currently enforced by anything in
this repo.

## Open questions

1. **Flyway `connect-retries` semantics.** The Flyway docs state the
   retry loop runs **once per migration invocation** — i.e. once per
   `migrate-at-start`, not per migration script. The retries cover only
   the initial JDBC connection, not failures during script execution.
   Worth confirming end-to-end with an integration test the next time
   we touch the migrations area (A1f recovery scheduler is a natural
   place).
2. **Mongo driver defaults locking.** Should we set explicit
   `quarkus.mongodb.connect-timeout=PT10S` and
   `quarkus.mongodb.server-selection-timeout=PT30S` to lock the
   current driver defaults? Adds two lines of config; protects against
   silent driver upgrades changing the ceiling. Trade-off vs the "don't
   pollute config" rule — current pass left them alone.
3. **`acquisition-timeout` vs `initial-size`.** The default `min-size=0`
   plus `acquisition-timeout=5S` means Agroal does not pre-warm any
   connections at startup; the first request after readiness pays the
   handshake cost. If we ever set `initial-size>0` we should also set
   `acquisition-timeout` explicitly so a slow Postgres still triggers
   readiness rather than blocking the request thread.
4. **Per-DB ceilings vs single global.** Currently we have
   `shepard.migrations.connection-wait-timeout` (Neo4j only) and the
   Flyway retries here, with no shared property. Future cleanup could
   unify under a single `shepard.startup.db-wait-ceiling` referenced
   by every connect-retry block; deferred.
