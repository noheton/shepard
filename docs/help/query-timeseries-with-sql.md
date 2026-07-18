---
stage: deployed
last-stage-change: 2026-07-18
audience: user
layout: default
title: Query timeseries with SQL
description: Pull many channels into one CSV or JSON payload with a single POST — the fast path for notebooks and dashboards
permalink: /help/query-timeseries-with-sql/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The bulk-read
> endpoint was already documented in the
> [timeseries reference](../reference/timeseries-reference.md); this task
> page is the missing casual front door to it.

<!-- backfill: DOCS-3A6/7-sweep2 2026-07-18 -->

# Query timeseries with SQL

**You want:** all the channels you care about, for one time window, in a single
file you can drop into a notebook or spreadsheet — without one HTTP call per
channel.

Shepard exposes a **bulk-read** endpoint for exactly this: `POST
/v2/sql/timeseries`. You send a small **query DSL** — which columns, which
containers, which time window — and Shepard compiles it to a safe,
parameterised SQL `SELECT` against TimescaleDB and streams the result back as
**CSV, JSON, or NDJSON**.

> This is a read-only convenience surface. You never write raw SQL and you never
> see a database path — you name the containers (by appId) and a window, and the
> backend writes and parameterises the query for you. See the
> [timeseries reference](../reference/timeseries-reference.md#bulk-read-dsl--post-v2sqltimeseries)
> for the complete request schema (all `select` / `where` / `order_by` options).

---

## The 60-second path

1. **Find your containers.** Open a timeseries container on a DataObject's
   detail page and note its **appId**. (The
   [timeseries plotting](timeseries-plotting.md) page walks through browsing
   channels.) You can list several — containers you cannot read are silently
   dropped.
2. **Pick your window.** A start and end timestamp (`time_between`).
3. **Ask for the format you want** with an `Accept` header:
   - `text/csv` (the default) → straight into Excel / `pandas.read_csv`
   - `application/json` → one JSON envelope `{"rows":[…],"truncated":bool}`
   - `application/x-ndjson` → newline-delimited, stream-friendly for large pulls
4. **POST** the DSL.

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/csv" \
  -d '{
        "select": [ { "column": "time" }, { "column": "value", "alias": "vibration_rms" } ],
        "from": "timeseries_data_points",
        "where": {
          "time_between": { "start": "2024-06-02T08:00:00Z", "end": "2024-06-02T08:01:00Z" },
          "container_app_id_in": [ "018f7c91-…" ]
        },
        "limit": 50000,
        "order_by": [ { "column": "time", "direction": "ASC" } ]
      }' \
  https://shepard.example.org/v2/sql/timeseries > run.csv
```

Straight into a notebook:

```python
import io, requests, pandas as pd
r = requests.post(
    "https://shepard.example.org/v2/sql/timeseries",
    headers={"Authorization": f"Bearer {token}", "Accept": "text/csv"},
    json={
        "select": [{"column": "time"}, {"column": "value", "alias": "vibration_rms"}],
        "from": "timeseries_data_points",
        "where": {
            "time_between": {"start": start, "end": end},
            "container_app_id_in": [container_app_id],
        },
    },
)
df = pd.read_csv(io.StringIO(r.text))
```

---

## Good to know

- **Caps protect the instance.** There is a hard **row cap** (default
  1,000,000) and a **query-duration cap** (default 60 s). Narrow your window or
  channel list if you hit them. An admin can tune both at runtime — see the
  [admin config registry](../reference/admin-config.md#sql-timeseries).
- **`404` means it's switched off.** If the endpoint returns `404`, an operator
  has disabled the SQL surface for this instance
  (`shepard.timeseries.sql.enabled=false`). Fall back to the per-channel read
  paths.
- **You never touch SQL.** The "SQL" is an implementation detail — you send the
  channel + window DSL, Shepard writes and parameterises the query. This keeps
  the read permission-checked and injection-safe.

---

## See also

- [Bulk-read reference](../reference/timeseries-reference.md#bulk-read-dsl--post-v2sqltimeseries) — the complete request/response schema, caps, and error shapes.
- [Timeseries plotting](timeseries-plotting.md) — browse and chart channels in the UI.
- [Units on channels](units-on-channels.md) — how channel units are recorded and shown.
- [API access](api-access.md) — generating the token used above.
