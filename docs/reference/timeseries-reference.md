---
layout: default
title: Timeseries reference
permalink: /reference/timeseries/
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
| `shepard.timeseries.sql.enabled` | `true` | Disables the endpoint (returns 404) when `false`. |
| `shepard.timeseries.sql.max-rows` | `1000000` | Hard row cap. When hit, the stream closes and the `x-shepard-truncated: true` HTTP trailer is emitted. |
| `shepard.timeseries.sql.max-duration` | `PT60S` | PostgreSQL `statement_timeout` in ISO-8601 duration. A query that exceeds this returns HTTP 504. |

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

## Upstream ingestion endpoints

Timeseries channels are created and written via the upstream
`/shepard/api/` surface, which this fork preserves byte-for-byte.
Refer to the [upstream shepard 5.2.0 OpenAPI spec](https://gitlab.com/dlr-shepard/shepard)
for the channel ingestion, listing, and deletion endpoints.
