---
layout: default
title: "Runbook — Restore TimescaleDB from a pg_dump backup"
description: "Pause PgBouncer, restore a TimescaleDB database from a pg_dump archive, resume the connection pool, and verify hypertable integrity. Covers the pgbouncer pause step required to prevent in-flight writes during restore."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Restore TimescaleDB from a pg_dump backup

> **When to use this runbook**: TimescaleDB data needs to be restored from a
> `pg_dump` archive — e.g. after data corruption, accidental row deletion, or a
> failed migration. This runbook assumes a plain-SQL or custom-format `pg_dump`
> file is available at a known path.

**WARNING**: This is a **destructive** operation. Existing rows in the target
database are overwritten or dropped (depending on the dump format and restore
flags). Understand the blast radius:
- Neo4j shadow nodes for `:TimeseriesContainer` entities are NOT affected — they
  continue to exist and will reference hypertables that may no longer contain the
  data they pointed to before the restore.
- S3 / Garage objects are NOT affected.
- If the dump is older than recent Neo4j operations, the two substrates will be
  out of sync. Reconcile after restore.

---

## Prerequisites

- A backup dump file: `/opt/shepard/timescaledb/backups/<dumpfile>` (or accessible
  path on the host — copy into the container volume if needed).
- SSH access to the nuclide host; user in the `docker` group.
- `${TIMESCALEDB_PASSWORD}` available (from `/opt/shepard/infrastructure/.env`; the
  user is `shepard`, database is `shepard`).
- Compose working directory: `/opt/shepard/infrastructure/`.
- TimescaleDB extension already installed in the restored database (it is — the
  Docker image ships with it and `CREATE EXTENSION IF NOT EXISTS timescaledb` is
  idempotent).

---

## Steps

### 0. Record the current state

```bash
# [nuclide]
echo "Dump file to restore:"
ls -lh /opt/shepard/timescaledb/backups/<dumpfile>

echo "Current hypertable list:"
docker exec infrastructure-timescaledb-1 \
  psql -U shepard -d shepard -c \
  "SELECT hypertable_schema, hypertable_name, num_chunks
   FROM timescaledb_information.hypertables ORDER BY hypertable_name;"
```

### 1. Stop the backend

Prevent the application from writing during restore:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend
echo "Backend stopped."
```

### 2. Pause PgBouncer (drain connection pool)

PgBouncer sits between the application and TimescaleDB. Pausing it ensures no
in-flight transactions reach Postgres during restore.

```bash
# [nuclide]
# PgBouncer admin DB is named "pgbouncer"; connect on the PgBouncer port (5432 by default)
docker exec infrastructure-pgbouncer-1 \
  psql -h localhost -p 5432 -U pgbouncer pgbouncer \
  -c "PAUSE shepard;"
echo "PgBouncer paused for 'shepard' database."
```

Expected: `PAUSE`

Verify no active server connections remain:

```bash
# [nuclide]
docker exec infrastructure-pgbouncer-1 \
  psql -h localhost -p 5432 -U pgbouncer pgbouncer \
  -c "SHOW pools;" | grep shepard
```

Expected: `sv_active = 0` in the output row for the `shepard` pool.

### 3. Drop and recreate the target database

> **Skip this step** if you are doing a partial restore (only specific tables).
> For a full database restore, drop and recreate to avoid constraint conflicts.

```bash
# [nuclide]
# Connect as the postgres superuser to drop/create
docker exec infrastructure-timescaledb-1 \
  psql -U postgres -c "DROP DATABASE IF EXISTS shepard;"
docker exec infrastructure-timescaledb-1 \
  psql -U postgres -c "CREATE DATABASE shepard OWNER shepard;"
docker exec infrastructure-timescaledb-1 \
  psql -U postgres -d shepard -c "CREATE EXTENSION IF NOT EXISTS timescaledb;"
echo "Database recreated."
```

### 4. Copy the dump file into the container (if not already bind-mounted)

```bash
# [nuclide]
# If the backups path is already bind-mounted, skip this step.
docker cp /opt/shepard/timescaledb/backups/<dumpfile> \
  infrastructure-timescaledb-1:/tmp/<dumpfile>
```

### 5. Restore from dump

For a **custom-format** dump (`pg_dump -Fc`):

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  pg_restore -U shepard -d shepard \
  --no-owner --no-privileges \
  --exit-on-error \
  /tmp/<dumpfile>
echo "Restore exit code: $?"
```

For a **plain-SQL** dump (`pg_dump -Fp`):

```bash
# [nuclide]
docker exec -i infrastructure-timescaledb-1 \
  psql -U shepard -d shepard \
  < /opt/shepard/timescaledb/backups/<dumpfile>
echo "Restore exit code: $?"
```

Expected: exit code `0`. Any non-zero exit code requires investigation before
proceeding to step 6.

### 6. Resume PgBouncer

```bash
# [nuclide]
docker exec infrastructure-pgbouncer-1 \
  psql -h localhost -p 5432 -U pgbouncer pgbouncer \
  -c "RESUME shepard;"
echo "PgBouncer resumed."
```

Expected: `RESUME`

### 7. Restart the backend

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
```

Wait for readiness:

```bash
# [nuclide]
until curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq -e '.status == "UP"' > /dev/null 2>&1; do
  echo "Waiting for backend readiness…"; sleep 5
done
echo "Backend UP."
```

### 8. Verify hypertable integrity

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U shepard -d shepard -c \
  "SELECT hypertable_name, num_chunks, total_bytes
   FROM timescaledb_information.hypertables
   ORDER BY hypertable_name;"
```

Compare hypertable count and chunk counts against pre-restore values from step 0.

Spot-check a specific container's row count (substitute a known container ID):

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U shepard -d shepard -c \
  "SELECT count(*) FROM cdt_<container_id>;"
```

---

## Partial restore (single hypertable)

To restore only one hypertable from a custom-format dump:

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  pg_restore -U shepard -d shepard \
  --table=cdt_<container_id> \
  --no-owner --no-privileges \
  /tmp/<dumpfile>
```

Note: TimescaleDB chunk tables (`_timescaledb_internal._hyper_*`) may also need
restoring for the hypertable to be fully intact. A full-database restore is
preferred unless you know exactly which tables to target.

---

## Rollback

If the restore produces an unusable state:

1. Pause PgBouncer again (step 2).
2. Drop the database, recreate it (step 3).
3. Restore from an older known-good dump (repeat step 4–5 with a different file).
4. Resume PgBouncer (step 6) and restart the backend (step 7).

---

## End-state verification

```bash
# [nuclide]
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'
```

Expected: all checks `"UP"`.

```bash
# PgBouncer pool health
docker exec infrastructure-pgbouncer-1 \
  psql -h localhost -p 5432 -U pgbouncer pgbouncer \
  -c "SHOW pools;" | grep shepard
```

Expected: `sv_idle > 0`, `sv_active = 0` (no active mid-transaction connections).

---

## Provenance

- Backup recipe: `docs/admin/backup.md` §TimescaleDB.
- PgBouncer admin commands: `edoburu/pgbouncer` image documentation.
- Cross-substrate note: `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md`.
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
