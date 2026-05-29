---
layout: default
title: Timeseries reference
permalink: /reference/timeseries/
audience: user
---
# Timeseries reference

A **`TimeseriesReference`** attaches one or more sensor channels
stored in TimescaleDB to a `DataObject`. Channels are ingested via
the existing upstream `/shepard/api/` endpoints (preserved byte-for-
byte from upstream 5.2.0). This page focuses on the **bulk-read
query endpoint** added by this fork (P10a + P10b).

---

## Bulk-read DSL — `POST /v2/sql/timeseries`

A JSON query DSL that lets you cross-container timeseries bulk reads
in a single HTTP call. Internally compiles to a parameterised SQL
`SELECT` against TimescaleDB and streams the result without
materialising it in the backend.

### Feature flags

| Config key | Default | Effect |
|---|---|---|
| `shepard.timeseries.sql.enabled` | `true` | Disables the endpoint (returns 404) when `false`. Set to `false` to opt out. |
| `shepard.timeseries.sql.max-rows` | `1000000` | Deploy-time seed for the hard row cap. Overridable at runtime via the admin config endpoint (P10c). |
| `shepard.timeseries.sql.max-duration` | `PT60S` | Deploy-time seed for the query duration cap. Overridable at runtime via the admin config endpoint (P10c). |

### Content types

The endpoint negotiates the response format from the `Accept` header.
Default (no `Accept`, or `Accept: */*`) is **CSV** — the primary use
case is opening the result in Excel.

| `Accept` value | Response format | MIME type |
|---|---|---|
| *(absent, blank, or `*/*`)* | RFC 4180 CSV with header row | `text/csv; charset=UTF-8` |
| `text/csv` | RFC 4180 CSV with header row | `text/csv; charset=UTF-8` |
| `application/json` | JSON envelope `{"rows":[…],"truncated":bool}` | `application/json` |
| `application/x-ndjson` | One JSON object per line (`\n` terminated) | `application/x-ndjson` |

CSV responses also include `Content-Disposition: attachment; filename="timeseries.csv"`.

### Truncation trailer

When the `max-rows` cap fires before the cursor is exhausted, the
server emits the HTTP trailer `x-shepard-truncated: true` **after**
the body. The `Trailer: x-shepard-truncated` announcement header is
included in the initial response so HTTP/1.1 proxies forward it.

```
HTTP/1.1 200 OK
Content-Type: text/csv; charset=UTF-8
Transfer-Encoding: chunked
Trailer: x-shepard-truncated
...body (1 000 000 rows)...
x-shepard-truncated: true
```

Only `true` is emitted; if the row cap is not reached the trailer is
not set. Clients should check for the trailer's presence, not for a
specific value.

### Timeout

When PostgreSQL's `statement_timeout` fires (configured by
`shepard.timeseries.sql.max-duration`), the endpoint returns:

```
HTTP/1.1 504 Gateway Timeout
Content-Type: application/json

{"error":"query exceeded max duration"}
```

This is returned only when the timeout fires **before** the first
response byte is written (i.e., during query planning or initial
execution). If the timeout fires mid-stream (after headers are
committed) the connection is closed instead.

### Request body

```json
{
  "select": [
    { "column": "time" },
    { "column": "value", "alias": "temperature_celsius" }
  ],
  "from": "timeseries_data_points",
  "where": {
    "time_between": {
      "start": "2026-01-01T00:00:00Z",
      "end":   "2026-02-01T00:00:00Z"
    },
    "container_id_in": [42, 99],
    "tag_filter": "sensor=PT100"
  },
  "limit": 50000,
  "order_by": [{ "column": "time", "direction": "ASC" }],
  "group_by": []
}
```

| Field | Required | Description |
|---|---|---|
| `select` | yes | Columns to return. Each item has `column` (required) and optional `alias`. |
| `from` | yes | Table name. Must be an allowed table (e.g. `timeseries_data_points`). |
| `where.time_between` | yes | ISO-8601 UTC start/end window. Both bounds are inclusive. |
| `where.container_id_in` | yes | List of TimescaleDB container IDs to include. Filtered against the caller's read permissions; IDs the caller cannot read are silently dropped. |
| `where.tag_filter` | no | Tag-filter expression applied as an additional `WHERE` predicate. |
| `limit` | no | Row cap for this request. Effective cap is `min(limit, max-rows)`. |
| `order_by` | no | List of `{column, direction}` objects. Direction is `ASC` or `DESC`. |
| `group_by` | no | List of column names for `GROUP BY`. |

### Response examples

**CSV (default)**

```
time,value
2026-01-01T00:00:00Z,23.4
2026-01-01T00:01:00Z,23.5
```

**NDJSON**

```ndjson
{"time":"2026-01-01T00:00:00Z","value":23.4}
{"time":"2026-01-01T00:01:00Z","value":23.5}
```

**JSON**

```json
{
  "rows": [
    {"time":"2026-01-01T00:00:00Z","value":23.4},
    {"time":"2026-01-01T00:01:00Z","value":23.5}
  ],
  "truncated": false
}
```

### Error responses

| HTTP | Cause |
|---|---|
| 400 | Malformed DSL (unknown table, disallowed column, invalid time window, too many container IDs). |
| 404 | Feature disabled (`shepard.timeseries.sql.enabled=false`). |
| 504 | Query exceeded `max-duration`; fired before first response byte. |

### Streaming architecture

The endpoint uses a JDBC server-side cursor (`setFetchSize(10_000)`)
so the full result set is never materialised in the backend. Rows are
written directly to the HTTP response socket in 10 000-row fetch
batches. If the client closes the connection mid-stream,
`Statement.cancel()` is called immediately to release the database
cursor.

---

## Admin config — `GET/PATCH /v2/admin/sql-timeseries/config`

Added in **P10c**. Instance-admin gated (`instance-admin` role). Lets operators
tune the `max-rows` and `max-duration` caps at runtime without a restart.
The runtime value wins over the `application.properties` deploy-time default;
setting a field to `null` via PATCH reverts it to the deploy-time default.

### GET current config

```
GET /v2/admin/sql-timeseries/config
Authorization: Bearer <instance-admin token>
```

Response `200 OK`:

```json
{
  "maxRows": 1000000,
  "maxDuration": "PT60S"
}
```

Fields are always resolved — if the singleton has never been patched, the
deploy-time defaults from `application.properties` are returned.

### PATCH to tune caps

RFC 7396 merge-patch semantics: absent = leave alone, `null` = revert to
deploy-time default, value = replace.

```
PATCH /v2/admin/sql-timeseries/config
Authorization: Bearer <instance-admin token>
Content-Type: application/json

{"maxRows": 500000, "maxDuration": "PT2M"}
```

Response `200 OK` — the updated config in the same shape as GET.

**Validation:**
- `maxRows` must be `> 0` when non-null. Invalid value → `400` with
  `application/problem+json` body (`type: /problems/sql-timeseries.config.invalid-max-rows`).
- `maxDuration` must be a valid ISO-8601 duration (e.g. `"PT60S"`, `"PT2M30S"`, `"PT1H"`)
  when non-null. Invalid value → `400` with `type: /problems/sql-timeseries.config.invalid-max-duration`.

**Revert to defaults:**

```json
{"maxRows": null, "maxDuration": null}
```

Clears both fields; effective values revert to `application.properties` defaults
(`max-rows=1000000`, `max-duration=PT60S`).

### Config fields

| Field | Type | Default | Description |
|---|---|---|---|
| `maxRows` | `long` | `1000000` | Hard row cap. When hit, stream closes and `x-shepard-truncated: true` trailer is emitted. |
| `maxDuration` | ISO-8601 string | `PT60S` | PostgreSQL `statement_timeout`. On timeout the endpoint returns HTTP 504. |

---

## Pinned channel tiles on PersonalDigest (UX-PIN1)

Any timeseries channel can be **pinned** to the personal landing page
(`PersonalDigest`). Pins are stored in `localStorage` under the key
`shepard:pinnedChannels` and survive page reloads without a round-trip
to the backend.

### Pin / unpin a channel

1. Open a Timeseries Container page.
2. In the **Measurements** table, each row has a pin icon in the rightmost
   column. Click the `mdi-pin-outline` icon to pin; the icon turns solid
   (`mdi-pin`, primary colour) when the channel is pinned. Click again to
   unpin.

The pin button is **disabled** (greyed out) while the browser is resolving
the channel's `shepardId` via
`GET /v2/timeseries-containers/{id}/channels?size=2000`. This is a one-time
load per container visit and typically completes in < 200 ms on a warm server.
The legacy v1 `TimeseriesEntity` list does not carry `shepardId`, so this
parallel fetch is necessary.

### What the tile shows

| Element | Detail |
|---|---|
| Channel name | `device · field` or all five 5-tuple parts joined with ` · ` |
| Last value | Most recent data point in the last 60 seconds, formatted to 4 significant figures |
| Trend arrow | `↑` (up ≥ 2%), `↓` (down ≥ 2%), `→` (flat) — colour-coded green/red/default |
| Sparkline | 60-point LTTB-downsampled line chart of the last 60 seconds of data |
| Source link | Click the tile title to navigate back to the timeseries container |
| Unpin | Click the `×` button on the tile to remove it from the landing page |

Data is fetched once on component mount via
`GET /v2/timeseries-containers/{containerId}/channels/{shepardId}/data?start=...&end=...&downsample=lttb&maxPoints=60`.
There is no live polling in UX-PIN1; reload the page to refresh tile data.
Live streaming is deferred to a later slice (UX-PIN1c).

### Persistence

Pins persist in `localStorage` — they survive browser restarts but are
**per-browser, not per-user-account**. Backend preference sync (so pins
follow you across devices and browsers) is planned as UX-PIN1b.

---

## Single-field channel lookup (TS-IDc, /v2/)

Every channel carries a stable single-field **`shepardId`** (UUID v4 by
default; minted by the Postgres `gen_random_uuid()` column default on
insert per the V1.11.0 migration). On the `/v2/` surface, `shepardId`
is accepted on every channel-data endpoint as an alternative to the
5-tuple `{measurement, device, location, symbolicName, field}` lookup.

**When both forms are supplied on the same request, `shepardId` wins**
and the 5-tuple is ignored.

| Endpoint | shepardId acceptance |
|---|---|
| `GET /v2/timeseries-containers/{cid}/channels` | Returns `shepardId` on every row |
| `GET /v2/timeseries-containers/{cid}/channels/{shepardId}/data` | Path param (canonical) |
| `POST /v2/timeseries-containers/{cid}/channels/data/bulk` | List of shepardIds in body |
| `POST /v2/timeseries-containers/{cid}/channels/{shepardId}/data/ingest` | Path param |
| `GET /v2/timeseries-containers/{containerAppId}/channels/live-window` | `?shepardId=<uuid>` query param (TS-IDc, new) |

Example — pull the last 60 s of one channel by shepardId on the
live-window endpoint:

```bash
curl -H "X-API-Key: $KEY" \
  "https://shepard-api.example/v2/timeseries-containers/${CONTAINER_APP_ID}/channels/live-window\
?shepardId=01930a2b-fe4c-7e3c-9f1d-8a5b2c3d4e5f&windowSeconds=60"
```

The legacy 5-tuple lookup (`?measurement=...&device=...&...`) stays
accepted on the same endpoint as a transitional bridge; new
integrations should prefer shepardId.

**v1 surface unaffected**: `/shepard/api/timeseriesContainers/*`
remains byte-frozen — it does NOT expose `shepardId` and continues
to require the 5-tuple. Wire-fidelity is locked by
`V1WireFidelityTest`.

See [aidocs/platform/87](../../aidocs/platform/87-timeseries-appid-migration.md)
for the migration design (TS-IDa, TS-IDb, TS-IDc, TS-IDd, TS-IDe
phases) and rationale.

---

## Upstream ingestion endpoints

Timeseries channels are created and written via the upstream
`/shepard/api/` surface, which this fork preserves byte-for-byte.
Refer to the [upstream shepard 5.2.0 OpenAPI spec](https://gitlab.com/dlr-shepard/shepard)
for the channel ingestion, listing, and deletion endpoints.
