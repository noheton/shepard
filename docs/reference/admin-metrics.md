---
stage: deployed
last-stage-change: 2026-07-18
audience: admin
layout: default
title: Instance-health metrics summary
description: Operator reference for the admin metrics-summary endpoint and dashboard card — heap, uptime, request counts, permissions-cache hit ratio
permalink: /reference/admin-metrics/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The feature shipped
> as `QW6` (admin metrics card); this page documents its behaviour from the
> source as it stands at the backfill date.

<!-- backfill: DOCS-3A6/7 2026-07-18 -->

# Instance-health metrics summary reference

**Feature ID:** QW6  
**Route:** `GET /v2/admin/metrics-summary` (`instance-admin`)  
**UI surface:** the metrics card on the `/admin` landing page

---

## What it is

A single, cheap, always-available **instance-health summary** for operators. It
answers *"is this Shepard healthy right now?"* without requiring a Prometheus +
Grafana monitoring stack to be wired up. The endpoint reads whatever
Micrometer/JVM metrics are available in-process and returns a small JSON
document; the `/admin` dashboard renders it as a card.

Full time-series observability (scrape endpoints, dashboards, alerting) is a
separate concern — see `docs/admin/observability.md`. This endpoint is the
**at-a-glance** view baked into the admin UI.

---

## Endpoint

```
GET /v2/admin/metrics-summary
Authorization: Bearer <instance-admin JWT>
```

Response body:

```json
{
  "jvmHeapUsedBytes": 412663808,
  "jvmHeapMaxBytes": 1073741824,
  "uptime": "PT6H14M",
  "httpRequestsTotal": 184201,
  "httpMeanRequestDuration": "23.4ms",
  "permissionsCacheHits": 902144,
  "permissionsCacheMisses": 3187,
  "permissionsCacheHitRatio": 0.9965
}
```

| Field | Meaning |
|---|---|
| `jvmHeapUsedBytes` / `jvmHeapMaxBytes` | Current vs. maximum JVM heap. Sustained `used` near `max` signals memory pressure. |
| `uptime` | ISO-8601 duration since process start. A short uptime after you didn't restart means the container crash-looped. |
| `httpRequestsTotal` | Cumulative HTTP request count since start. |
| `httpMeanRequestDuration` | Mean request latency. A rising mean is the first cheap signal of a slow substrate (Neo4j, TimescaleDB, S3). |
| `permissionsCacheHits` / `permissionsCacheMisses` | Permission-cache effectiveness. |
| `permissionsCacheHitRatio` | `hits / (hits + misses)`. A low ratio (< ~0.9) under steady load hints at cache thrash — worth a look at permission-check hot paths. |

---

## Degraded-but-functional guarantee

The endpoint is written to **work regardless of whether the monitoring compose
profile is running**. When a metric source isn't present it returns a sensible
zero/empty value rather than failing — consistent with the fail-soft registry
rule. You always get a 200 with a valid document; you never get a 500 because a
counter wasn't registered.

---

## Where it surfaces in the UI

The `/admin` landing page renders a metrics card populated from this endpoint.
It is `instance-admin`-gated like the rest of the admin hub; a non-admin never
sees it.

---

## Reading it from a script

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  https://shepard.example.com/v2/admin/metrics-summary | jq
```

Useful for a lightweight external healthcheck or a status page that doesn't want
to run a full Prometheus scrape.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `403` | Token lacks `instance-admin` | Rotate to an admin token; `docs/admin/runbooks/14-role-grants.md` |
| All counters read `0` | Fresh start, or metrics backend not initialised | Expected right after boot; values populate as traffic flows |
| `permissionsCacheHitRatio` persistently low | Cache thrash under an unusual permission workload | Investigate per-request permission checks; not itself an error |

---

## Cross-references

- `docs/admin/observability.md` — full metrics / scrape / dashboard story
- `docs/help/troubleshooting-databases.md` — when the summary points at a slow substrate
- `docs/reference/admin-config.md` — the admin surface family this endpoint sits in
