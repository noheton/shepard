---
layout: default
title: "Runbook — Verify TS-IDc shepardId channel lookup is live"
description: "Step-by-step verification that the v2 timeseries data endpoints accept channelShepardId in addition to the legacy 5-tuple. Covers DB index sanity, EXPLAIN ANALYZE plan-time evidence, and a smoke probe against the live channels listing."
stage: feature-defined
last-stage-change: 2026-05-29
audience: instance-admin
host: nuclide
tested: "— (procedure verified 2026-05-29 against shepard.nuclide.systems MFFD container 1772)"
---

# Verify TS-IDc shepardId channel lookup is live

> **When to use this runbook**: After deploying a backend image that includes
> the TS-IDc migration ([aidocs/platform/87 §3](../../../aidocs/platform/87-timeseries-appid-migration.md)),
> verify that the v2 timeseries data endpoints accept `shepardId` as a
> single-field channel identity, and confirm the planner takes the
> index-only path. The legacy 5-tuple stays accepted as a transitional
> bridge.

---

## What TS-IDc changes

- `TsChannelResolver.findByContainerAndShepardId(containerId, shepardId)` —
  container-scoped single-key lookup; wraps `findByShepardId` with an
  in-memory container filter. Replaces the 5-tuple-walk for hot-path
  reads.
- `GET /v2/timeseries-containers/{containerAppId}/channels/live-window` —
  accepts an optional `?shepardId=<uuid>` query param; when present,
  shepardId wins and the 5-tuple is ignored.
- `GET /v2/timeseries-containers/{cid}/channels/{shepardId}/data` —
  already accepted shepardId as a path param (TS-IDc PR-2, shipped
  earlier).
- `POST /v2/timeseries-containers/{cid}/channels/data/bulk` — already
  accepted a list of shepardIds (TS-OPT2, shipped earlier).

The frozen v1 surface (`/shepard/api/timeseriesContainers/*`) is
**untouched** — its 5-tuple shape is locked by `V1WireFidelityTest`.

---

## Prerequisites

- An `instance-admin` API key against the target host (`shepard-api.<host>.systems`).
- One known channel `shepardId` on that host — pull one from the
  channels-listing endpoint (see step 1) or via
  `cypher-shell` on the running TimescaleDB container.

---

## Step 1 — Sanity-probe the channels listing carries shepardId

```bash
TARGET=shepard-api.nuclide.systems
TS_CID=1772   # MFFD synthetic container; replace with your container
API_KEY=...   # your instance-admin key

curl -sS -H "X-API-Key: $API_KEY" \
  "https://${TARGET}/v2/timeseries-containers/${TS_CID}/channels?size=3" \
  | jq -r '.[] | "\(.shepardId)  \(.measurement) | \(.device) | \(.field)"'
```

Expected — three rows like:

```
a2c0f1dd-4dce-4400-92e4-445cd18826e6  mffd | AFP-AFPT-MTLH-S1 | consolidation_force_N
b28831a0-c83c-4b8b-ba9f-5dbc4cd8473b  mffd | AFP-AFPT-MTLH-S1 | tcp_temperature_C
…
```

If `shepardId` is absent from the response, the backend image is
**older than TS-IDb**. Pull a newer tag (see runbook 01) before
proceeding.

Pick one `shepardId` for the remaining steps; the runbook uses
`a2c0f1dd-4dce-4400-92e4-445cd18826e6` as the placeholder.

---

## Step 2 — Smoke the shepardId path-param endpoint

```bash
SID=a2c0f1dd-4dce-4400-92e4-445cd18826e6
END_NS=$(($(date +%s%N)))
START_NS=$((END_NS - 60000000000))

curl -sS -w "\nHTTP %{http_code} · %{time_total}s\n" \
  -H "X-API-Key: $API_KEY" \
  "https://${TARGET}/v2/timeseries-containers/${TS_CID}/channels/${SID}/data\
?start=${START_NS}&end=${END_NS}&downsample=lttb&max_points=10" \
  | head -c 300
```

Expected — `HTTP 200` and a body of the form
`{"timeseries":{...},"points":[...]}`. An empty `points` array is fine
if no data was ingested in the window; the wire-shape is what matters.

`HTTP 404` here means the shepardId is unknown OR is in a different
container (deliberately indistinguishable — see the cross-container
leak guard in `findByContainerAndShepardId`).

---

## Step 3 — Smoke the live-window endpoint with shepardId

```bash
CONTAINER_APP_ID=...  # the container's appId (UUID v7); GET /v2/timeseries-containers
curl -sS -w "\nHTTP %{http_code} · %{time_total}s\n" \
  -H "X-API-Key: $API_KEY" \
  "https://${TARGET}/v2/timeseries-containers/${CONTAINER_APP_ID}/channels/live-window\
?shepardId=${SID}&windowSeconds=60"
```

Expected — `HTTP 200`, body with `windowStart`, `windowEnd`, and a
`points` array.

To confirm the **shepardId-wins-over-5-tuple** precedence, hit the
same endpoint with a deliberately-wrong 5-tuple alongside the
shepardId — the response must still be the shepardId-resolved channel:

```bash
curl -sS -w "\nHTTP %{http_code}\n" \
  -H "X-API-Key: $API_KEY" \
  "https://${TARGET}/v2/timeseries-containers/${CONTAINER_APP_ID}/channels/live-window\
?shepardId=${SID}&measurement=wrong&device=wrong&windowSeconds=60"
```

`HTTP 200` (not 404) — proof that the 5-tuple was ignored.

---

## Step 4 — Confirm the plan-time evidence (DB-direct)

This step uses `psql` directly on the TimescaleDB container. Skip if
you don't have shell access to the host.

```bash
# On the host running the TimescaleDB container
docker exec -it infrastructure-timescaledb-1 \
  psql -U shepard -d postgres -c \
  "EXPLAIN (ANALYZE, BUFFERS) \
   SELECT t.id, m.measurement FROM timeseries t \
   JOIN channel_metadata m ON m.timeseries_id = t.id \
   WHERE t.shepard_id = '${SID}'::uuid;"
```

Expected — the plan uses `Index Scan using idx_timeseries_shepard_id`
on `timeseries` and `Index Scan using channel_metadata_timeseries_id_key`
on `channel_metadata`. Planning Time and Execution Time should both
be under 5 ms; on the MFFD synthetic dataset (113 channels in
container 1772) we see ~4.4 ms planning + ~0.17 ms execution on
warm cache.

For comparison, the legacy 5-tuple path with all five predicates does
a `Seq Scan on channel_metadata` because the table is small enough
that the planner prefers it over the partial-tuple index — that path
costs ~2.2 ms planning + ~0.07 ms execution at this container size,
and grows linearly with channel count per container. The shepardId
path stays constant.

---

## What to look for in logs

A successful TS-IDc fetch produces no application-level log line
(this is a hot-path read). What *would* appear is the existing
PERF10 partial-tuple-lookup warning, which now does NOT fire on the
shepardId path:

```
DEBUG ... TsChannelResolver: partial-tuple lookup containerId=...   # only legacy path
```

If you see no such line on a request that used `?shepardId=...`, the
new code path is live.

---

## Rollback

The TS-IDc change is purely additive — no migration to roll back.
If the new code path misbehaves, redeploy the previous image tag
(runbook 01) and callers continue to work via the legacy 5-tuple path.
Existing `shepardId` columns in the database remain populated; only
the REST acceptance path reverts.

---

## See also

- [aidocs/platform/87 — TS-ID migration design](../../../aidocs/platform/87-timeseries-appid-migration.md)
- [TS-AUDIT-2026-05-24-009](../../../aidocs/agent-findings/db-audit-2026-05-24.md) —
  the 17× planning/execution audit that motivated TS-IDc.
- [Runbook 01 — generic cube hotpatch](01-generic-cube-hotpatch.md)
