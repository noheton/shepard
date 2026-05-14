---
layout: default
title: Monitoring + observability (deployment reference)
permalink: /reference/deployment-monitoring/
description: Health endpoints, Prometheus scrape config, Grafana dashboard, log aggregation, alerting rules for shepard — backend, Neo4j, MongoDB, TimescaleDB, plugins, DataCite mint failures, migration aborts.
---

# Monitoring + observability

shepard exposes three observability surfaces:

1. **Health endpoints** — `/shepard/api/healthz/{live,ready,started}`
   (SmallRye Health under Quarkus). Wire into your liveness /
   readiness / startup probes.
2. **Prometheus metrics** — `/shepard/doc/metrics/prometheus`
   (Micrometer-backed, JVM + Hibernate + Mongo + permission-cache
   metrics).
3. **Structured logs** — backend log files at
   `/opt/shepard/backend/logs/shepard.log`, plus stdout for every
   container.

The bundled `monitoring` Docker Compose profile boots **Prometheus**
and **Grafana** with auto-provisioning, so you can be looking at
graphs within five minutes of `docker compose --profile monitoring
up -d`. For production you'll likely want to scrape into your own
Prometheus + ship logs into your own aggregator (Loki / ELK).

## Health endpoints

SmallRye Health under the configured root
(`/shepard/api/healthz`):

| Endpoint | What it answers | Use for |
|---|---|---|
| `/shepard/api/healthz/live` | Is the JVM up? | Kubernetes `livenessProbe`; restart on `5xx` |
| `/shepard/api/healthz/ready` | Are we ready to take traffic? | Kubernetes `readinessProbe`; pull out of LB on `5xx` |
| `/shepard/api/healthz/started` | Have the startup hooks finished? | Kubernetes `startupProbe`; gives the backend up to ~5 min to come up before liveness kicks in |

Per A1b/c/f, the body breaks out **per-database** state:

```bash
curl -fsS http://localhost:8080/shepard/api/healthz/ready | jq .
```

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "neo4j",
      "status": "UP",
      "data": { "state": "UP", "kind": "REQUIRED" }
    },
    {
      "name": "mongo",
      "status": "UP",
      "data": { "state": "UP", "kind": "REQUIRED" }
    },
    {
      "name": "timescale",
      "status": "UP",
      "data": { "state": "UP", "kind": "REQUIRED" }
    },
    {
      "name": "postgis",
      "status": "UP",
      "data": { "state": "DEGRADED", "kind": "OPTIONAL" }
    }
  ]
}
```

`OPTIONAL` databases (PostGIS when spatial isn't enabled, HSDS
when HDF isn't enabled) being `DEGRADED` doesn't fail readiness.
`REQUIRED` databases being `DOWN` does.

Datasource + MongoDB raw-connection health checks are
**disabled** in `application.properties` — readiness reports
**application state**, not raw DB connectivity. The A1f
recovery scheduler picks up a `DOWN` DB on its `PT15S` cadence;
endpoints decorated with `@RequiresDatabase` return **503 + RFC
7807** when their DB is `DOWN` (and `Retry-After` for the
spatial endpoints).

## Prometheus metrics

The backend exposes Micrometer metrics at
`/shepard/doc/metrics/prometheus`. The bundled
`infrastructure/prometheus/prometheus.yml` scrapes every 10
seconds.

### Key metrics

| Metric family | What it's good for |
|---|---|
| `http_server_requests_seconds_*` | HTTP request rate + p50/p95/p99 latency per endpoint |
| `jvm_memory_*` | JVM heap usage, GC behaviour |
| `jvm_threads_*` | Thread-count drift; thread-pool saturation |
| `process_cpu_usage` | Backend CPU saturation |
| `hibernate_sessions_*` | DB session lifecycle |
| `mongodb_driver_commands_seconds_*` | MongoDB command latency |
| `cache_gets_total{cache="permissions-service-cache"}` | Permission-cache hit/miss ratio (post-A4) |
| `shepard_migration_*` | Migration progress (P3) |

### Adding alerting rules

Sample rules — drop into your Prometheus' `rules.d/`:

```yaml
groups:
- name: shepard
  rules:
  - alert: shepardBackendDown
    expr: up{job="shepard-backend"} == 0
    for: 2m
    annotations:
      summary: "shepard backend is unreachable"
      runbook: "https://shepard.example.com/docs/reference/deployment-troubleshooting/#backend-wont-start"

  - alert: shepardReadinessFlapping
    expr: increase(http_server_requests_seconds_count{uri="/shepard/api/healthz/ready", status="503"}[5m]) > 0
    for: 5m
    annotations:
      summary: "shepard readiness probe returning 503"

  - alert: shepardMigrationAborted
    expr: shepard_migration_status{status="ABORTED"} > 0
    annotations:
      summary: "shepard migration aborted on startup"
      runbook: "https://shepard.example.com/docs/reference/deployment-troubleshooting/#migration-aborted"

  - alert: shepardPluginFailed
    # The plugin registry surfaces FAILED state at scrape time
    # (PM1b exposes plugin state via Micrometer).
    expr: shepard_plugin_state{state="FAILED"} > 0
    annotations:
      summary: "shepard plugin {{ $labels.plugin_id }} is FAILED"
      runbook: "https://shepard.example.com/docs/reference/plugins/#troubleshooting"

  - alert: shepardJvmHeapHigh
    expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.9
    for: 10m
    annotations:
      summary: "shepard JVM heap > 90% for 10m"

  - alert: shepardPermissionCacheLow
    expr: |
      rate(cache_gets_total{cache="permissions-service-cache",result="hit"}[5m])
      /
      rate(cache_gets_total{cache="permissions-service-cache"}[5m]) < 0.5
    for: 30m
    annotations:
      summary: "permission cache hit ratio under 50%"
      runbook: "Tune shepard.permissions.cache.{ttl,max-size} per deployment-sizing.md"

  - alert: shepardDataciteMintFailed
    expr: increase(shepard_publish_mint_failures_total{minter="datacite"}[1h]) > 0
    annotations:
      summary: "DataCite mint failed in the last hour"
      runbook: "Check Fabrica credentials via shepard-admin minter-datacite credentials show"

  - alert: shepardBackupOverdue
    # Custom metric pushed by your backup script via Pushgateway
    expr: time() - shepard_backup_last_success_seconds > 86400 * 2
    annotations:
      summary: "shepard backup hasn't succeeded in > 48h"
```

The `runbook` annotations point at the
[troubleshooting page]({{ '/reference/deployment-troubleshooting/' | relative_url }}).
Wire `runbook_url` into your Alertmanager templates so paged
operators land on the right page.

## Grafana — bundled dashboard

The bundled `monitoring` Docker Compose profile auto-provisions
a **"shepard — Overview"** dashboard with panels for:

- HTTP request rate + p95 / p99 latency.
- JVM heap / threads / GC.
- Hibernate session events.
- MongoDB command latency.
- Permissions-cache hit ratio.

```bash
docker compose --env-file .env --profile monitoring up -d
# Grafana UI: http://localhost:3001
# admin login: $GRAFANA_ADMIN_USERNAME / $GRAFANA_ADMIN_PASSWORD
```

Dashboard JSON: `infrastructure/grafana/dashboards/shepard-overview.json`.
Edit in place, save back, Grafana reloads on a 30-second poll.

### Rotating Grafana defaults before public exposure

The shipped `infrastructure/.env.example` carries
`shepard` / `secret` as the Grafana admin credentials.
**Rotate them** before exposing port 3001 publicly:

```bash
# .env
GRAFANA_ADMIN_USERNAME=ops
GRAFANA_ADMIN_PASSWORD=$(openssl rand -base64 32)
```

Then `docker compose restart grafana`.

## Log aggregation

The backend writes to `/opt/shepard/backend/logs/shepard.log`
(rotating). For multi-host deploys, ship logs to a central
aggregator:

| Aggregator | Shipper | Notes |
|---|---|---|
| **Loki** | Promtail (Grafana stack) | Simplest path if you already run Grafana |
| **Elasticsearch** | Filebeat / Fluentd | Heavier but more queryable |
| **CloudWatch / Stackdriver** | provider sidecar | Cloud-native |
| **journald** | the default systemd journal | Single-host only; fine for small lab |

The backend's log format is one line per event with sanitised
exception info (post-H4 / RFC 7807). For each 5xx the log line
carries `traceId + class + method + path` — full stack at
`debug`. Use the `traceId` to correlate to the corresponding
client-side error.

### Sensitive log content

Post-M5, the backend logs `Authorization: present` / `absent` —
never the token value. Stack traces don't echo request bodies.
The exception sanitiser knows about
`InvalidAuthException` / `JwtVerificationException` — those don't
leak the token even to `debug`.

If you find a path that leaks sensitive content into the log,
file an issue against the
[security-issues ledger](https://github.com/noheton/shepard/blob/main/aidocs/07-security-issues.md).

## Tracing (queued)

Distributed tracing via OpenTelemetry is queued — the SPI is
in place (Micrometer Tracing autoconfigures with the
`micrometer-tracing-bridge-otel` dependency) but no exporter
ships out of the box yet. The Jaeger / Tempo recipe lives in
the queued runbook.

## What to alert on (prioritised)

If you're starting from zero, wire these in order:

1. **`shepardBackendDown`** — backend unreachable.
2. **`shepardMigrationAborted`** — DB migration failed; the
   backend won't start.
3. **`shepardBackupOverdue`** — backup hasn't run in 48h.
4. **`shepardPluginFailed`** — a plugin is in `FAILED` state.
5. **`shepardReadinessFlapping`** — readiness 503-ing
   intermittently.
6. **`shepardJvmHeapHigh`** — heap under pressure.
7. **`shepardDataciteMintFailed`** (if you've enabled DataCite
   minting) — publish requests failing.

Each alert should point at a section in
[troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }})
with a concrete fix-it recipe.

## Per-DB monitoring

The application-level health checks tell you **shepard's view**
of the DB. For the **DB's own view** (slow queries, lock
contention, replication lag), run per-DB monitoring:

- **Neo4j** — `neo4j-admin server report`, plus the Bolt
  metrics if your Neo4j Enterprise edition supports it. The
  bundled compose exposes Bolt on the internal network only.
- **MongoDB** — `mongo-express` (under
  `COMPOSE_PROFILES=monitoring`) for ad-hoc; ship the MongoDB
  exporter for Prometheus scraping.
- **PostgreSQL + TimescaleDB** — `postgres_exporter` for
  Prometheus; `pg_stat_statements` for slow-query analysis.
- **PostGIS** — same as PostgreSQL.
- **HSDS** — exposes its own `/about` and `/info` endpoints;
  no Prometheus integration yet.

Wire these into the same Prometheus scrape so all the metrics
land in one Grafana.

## Trace operator activity

Post-PROV1a, every `:Activity` row is captured automatically by
the `ProvenanceCaptureFilter`. Filter the activity log for
"who changed `<feature>` settings when":

```bash
# Via the admin API
curl -fsS -H "Authorization: Bearer $TOKEN" \
  'https://shepard.example.com/v2/provenance/activities?targetKind=PluginEntry&limit=50' \
  | jq .
```

```cypher
# Or via Cypher directly
MATCH (a:Activity)
WHERE a.targetKind IN ['PluginEntry', 'UnhideConfig', 'SemanticConfig', 'FeatureToggle']
RETURN a.targetKind, a.actionKind, a.actorUsername, a.timestamp, a.targetAppId
ORDER BY a.timestamp DESC
LIMIT 50
```

Activity rows are pruned per
`shepard.provenance.retention` (default 365 days, PROV1f).
Drop the retention if you want a long audit horizon.

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [Sizing recommendations]({{ '/reference/deployment-sizing/' | relative_url }}) — when to scale the JVM heap and per-DB caches.
- [Backup + restore]({{ '/reference/deployment-backup/' | relative_url }}) — what to monitor about your backups.
- [Troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }}) — alert → runbook recipes.
- [Admin guide §Performance metrics]({{ '/admin/#performance-metrics--out-of-the-box-dashboard' | relative_url }}) — bundled Grafana panels.
- [Provenance reference]({{ '/reference/provenance/' | relative_url }}) — `:Activity` log shape.
