---
layout: default
title: Apache Superset — install guide
permalink: /install/superset/
---

# Apache Superset against shepard

Apache Superset is a SQL-native BI tool — dashboards, charts, ad-hoc
queries. Researchers asking "what's the wattage profile of every
hot-fire run last week?" land on a Superset dashboard, not on the
shepard frontend.

This guide explains how to point Superset at the **TimescaleDB
hypertables that back shepard's timeseries containers** as a
read-only data source. It assumes you've already got shepard
running.

> **Read this first — the permission trade-off.** shepard enforces
> permissions through its graph (per-container Read / Write /
> Manager roles, group ACLs, visibility flags). Superset bypasses
> shepard entirely and queries TimescaleDB directly. **Anyone with
> a Superset login can read every container that exists in the
> database, regardless of their shepard permissions.**
> Only point Superset at this connection for **trusted internal
> analyst users**. Public-facing dashboards built off it must be
> reviewed for data-leakage risk — a single chart can spill an
> entire container's content. The shepard team is tracking a
> richer integration (per-container Postgres views + per-user
> SQLAlchemy dialects) as a future option.

## Why not go through `/v2/sql/timeseries`?

shepard's curated SQL endpoint (`POST /v2/sql/timeseries`, P10)
enforces row + duration caps, runs through the standard
permission graph, and supports the OIDC bearer token. **But it's
HTTP+JSON-body+CSV-out**, not a SQL-over-JDBC protocol. Superset
speaks SQLAlchemy / JDBC; the two don't natively meet.

You have three options:

1. **Direct TimescaleDB read (this guide).** Simplest. Researchers
   query the hypertables straight. Permission gap as above.
2. **Custom SQLAlchemy dialect for shepard.** ~200 LOC of Python;
   each query translates to a `POST /v2/sql/timeseries`. Higher
   maintenance burden, but enforces shepard's permission model.
3. **Per-container Postgres views**, one per Read-permitted
   container per Superset user, provisioned by a sync job. Most
   secure, most expensive to maintain.

This guide is option 1.

## 1. Create the read-only Postgres role

Connect to the TimescaleDB instance as a superuser:

```bash
docker exec -it infrastructure-timescaledb-1 \
  psql -U postgres -d postgres
```

Then:

```sql
-- Create a read-only role for Superset.
CREATE ROLE shepard_readonly WITH LOGIN PASSWORD 'change-me-in-prod';

-- Grant SELECT on the timeseries data points + metadata tables. shepard
-- writes everything via its services so we don't need any write/create
-- grants on the Superset side.
GRANT CONNECT ON DATABASE postgres TO shepard_readonly;
GRANT USAGE ON SCHEMA public TO shepard_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO shepard_readonly;

-- Future-proof: every new table picks up the SELECT grant.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT ON TABLES TO shepard_readonly;

-- TimescaleDB chunk schemas — same SELECT-on-future-tables pattern.
GRANT USAGE ON SCHEMA _timescaledb_internal TO shepard_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA _timescaledb_internal
  TO shepard_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA _timescaledb_internal
  GRANT SELECT ON TABLES TO shepard_readonly;
```

## 2. Expose TimescaleDB to your Superset host

The default `infrastructure/docker-compose.yml` only exposes the
TimescaleDB port on the internal Docker network. If Superset is on
the same Docker network, no change needed — Superset connects to
`timescaledb:5432`. If Superset is on a different host, add a
public port mapping (and put it behind your reverse proxy).

## 3. Add the connection in Superset

In Superset's **Data → Databases → + Database** dialog:

- **Supported databases:** PostgreSQL
- **SQLAlchemy URI:**
  `postgresql+psycopg2://shepard_readonly:change-me-in-prod@<host>:5432/postgres`
- **Expose database in SQL Lab:** on
- **Allow CREATE TABLE AS / Allow CREATE VIEW AS:** off (read-only)
- **Allow DML:** off

Save. SQL Lab should now list the `timeseries_data_points` table and
TimescaleDB hypertable views.

## 4. First dashboard — channels for one container

shepard's data model: each timeseries container is a logical
namespace; channels (5-tuple keys) live in the
`timeseries_data_points` hypertable joined against the `timeseries`
metadata table on `timeseries_id`.

A useful first query:

```sql
SELECT
  t.measurement,
  t.device,
  t.location,
  t.symbolic_name,
  t.field,
  COUNT(*)              AS samples,
  MIN(p.int_value)      AS v_min,
  MAX(p.int_value)      AS v_max,
  AVG(p.int_value)      AS v_mean
FROM timeseries_data_points p
JOIN timeseries t ON t.id = p.timeseries_id
WHERE p.time >= NOW() - INTERVAL '7 days'
GROUP BY 1, 2, 3, 4, 5
ORDER BY samples DESC
LIMIT 100;
```

(Adjust `int_value` to `double_value` / `bool_value` per the column
that carries your channel's value type — see the
`timeseries.value_type` column.)

Save the saved-query as the basis for a Superset chart; promote
to a dashboard from there.

## 5. Day-2 caveats

- **shepard soft-deletes containers.** A deleted container's
  hypertable rows are not removed by default — Superset will
  still see them. Filter on `t.deleted IS NULL OR t.deleted = false`
  if you want to mirror shepard's UI view.
- **Permission graph drift.** If a user's shepard permissions
  change, their Superset visibility does NOT change automatically
  (option 1's structural limitation).
- **Row caps.** Superset queries have no shepard-enforced cap.
  Set a SQL Lab `SQL_LAB_TIMEOUT` in `superset_config.py` to
  prevent runaway queries.
- **TimescaleDB version pinning.** shepard's compose pins
  TimescaleDB to a specific minor version. If you replace the
  TimescaleDB image with a different image (e.g. for backups via
  pg_dump), make sure Superset's psycopg2 client supports it.

## See also

- [Container safe-delete](/reference/container-safe-delete/) — the
  authoritative way to remove a container; Superset will see the
  soft-deleted rows after the delete.
- [Timeseries reference](/reference/timeseries-reference/) — the
  shape of the data Superset sees.
- The future "per-container Postgres view" option is tracked in
  `aidocs/16` (Superset integration item).
