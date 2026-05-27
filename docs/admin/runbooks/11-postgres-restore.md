---
layout: default
title: "Runbook — Postgres restore (four scenarios)"
description: "Recovery procedures for four Postgres failure modes: corrupt schema, accidental table drop, full-instance loss, and point-in-time restore via Wal-G. Covers the public, shepard_ts, and shepard_spatial schemas. Each scenario has numbered steps with host indicators, expected stdout, and end-state verification."
stage: feature-defined
last-stage-change: 2026-05-27
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Postgres restore — four scenarios

> **When to use this runbook**: Postgres data needs to be recovered. Four scenarios
> are covered. Identify your scenario, jump to the relevant section, and follow the
> numbered steps from top to bottom.

**Container / service reference:**

| Compose service | Running container name | Postgres role |
|---|---|---|
| `timescaledb` | `infrastructure-timescaledb-1` | App DB (superuser: `postgres`, app user: `shepard`, database: `postgres`) |

**Schema reference:**

| Schema | Content |
|---|---|
| `public` | Flyway-managed app tables (Flyway history, channel_metadata, migration task, etc.) |
| `shepard_ts` | TimescaleDB hypertables and CAGGs (`timeseries_data_points`, `timeseries_hourly`, …) |
| `shepard_spatial` | PostGIS spatial layers (future; seeded by `PG-COLLAPSE-001`) |

**Environment variables** (sourced from `/opt/shepard/infrastructure/.env`):

```bash
# [nuclide] — load before running any step in this runbook
source /opt/shepard/infrastructure/.env
# Key vars: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB, POSTGRES_SHEPARD_USER, POSTGRES_SHEPARD_USER_PW
```

**Backup path convention:** `/opt/shepard/timescaledb/backups/<dumpfile>`.
`<dumpfile>` names follow `shepard-full-<YYYYMMDD-HHmmss>.pgdump` (custom format,
`pg_dump -Fc`). Per-schema dumps follow `shepard-schema-public-<date>.pgdump`, etc.

---

## Scenario 1 — Corrupt schema

**Symptom**: Flyway reports `FAILED` migration, tables are missing or corrupt,
`\dt` in psql shows unexpected state, or the backend refuses to start with a
`SchemaNotFoundException`.

### Steps

#### 0. Stop the backend

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend
echo "Backend stopped."
```

Expected: `Container infrastructure-backend-1  Stopped`

#### 1. Record the current schema state

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  -c "\dt public.*" \
  -c "\dt shepard_ts.*" \
  -c "SELECT version_rank, version, description, success
      FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"
```

Save this output — compare against end-state.

#### 2. Identify the target dump file

```bash
# [nuclide]
ls -lht /opt/shepard/timescaledb/backups/ | head -10
```

Choose the most recent dump that predates the corruption event. Note the filename
as `<dumpfile>` in subsequent steps.

#### 3. Copy the dump into the container

```bash
# [nuclide]
docker cp /opt/shepard/timescaledb/backups/<dumpfile> \
  infrastructure-timescaledb-1:/tmp/<dumpfile>
echo "Copy exit code: $?"
```

Expected: exit code `0`.

#### 4. Restore the target schema

For a **full-database** restore (replaces all schemas):

```bash
# [nuclide]
# Drop and recreate the database first
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${POSTGRES_DB}' AND pid <> pg_backend_pid();"
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" \
  -c "DROP DATABASE IF EXISTS ${POSTGRES_DB};"
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" \
  -c "CREATE DATABASE ${POSTGRES_DB} OWNER ${POSTGRES_SHEPARD_USER};"
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  -c "CREATE EXTENSION IF NOT EXISTS timescaledb;"

# Restore
docker exec infrastructure-timescaledb-1 \
  pg_restore -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  --no-owner --no-privileges \
  --exit-on-error \
  /tmp/<dumpfile>
echo "Restore exit code: $?"
```

Expected: exit code `0`. Any non-zero result requires investigation before
proceeding.

For a **single-schema** restore (less disruptive; use when only one schema is affected):

```bash
# [nuclide]
# Example: restore only the public schema
docker exec infrastructure-timescaledb-1 \
  pg_restore -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  --schema=public \
  --no-owner --no-privileges \
  --exit-on-error \
  /tmp/<dumpfile>
echo "Restore exit code: $?"
```

For `shepard_ts` schema:

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  pg_restore -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  --schema=shepard_ts \
  --no-owner --no-privileges \
  --exit-on-error \
  /tmp/<dumpfile>
```

#### 5. Re-run Flyway migrations

The backend applies pending Flyway migrations on startup. Restart the backend to
trigger this:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
```

Watch startup logs for Flyway activity:

```bash
# [nuclide]
docker compose logs -f --tail=60 backend 2>&1 | grep -E "Flyway|migration|MigrationsRunner|ERROR" | head -40
```

Expected: lines like `Successfully applied N migrations to schema "public"` or
`Successfully validated N migrations` (if already current), then `Quarkus started`.

#### 6. Verify schema with `\dt`

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "\dt public.*" \
  -c "\dt shepard_ts.*"
```

Expected: `public` tables include `flyway_schema_history`, `channel_metadata`,
`migration_task_timeseries`, `migration_progress`. `shepard_ts` tables include
`timeseries_data_points` (and `_timescaledb_internal` chunk tables).

#### End-state verification

```bash
# [nuclide]
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'
```

Expected: all checks `"UP"`.

```bash
# Flyway history — confirm no FAILED rows
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

Expected: all rows have `success = t`.

---

## Scenario 2 — Accidental table drop

**Symptom**: A specific table is missing (`ERROR: relation "X" does not exist`)
but the rest of the database is intact.

### Steps

#### 0. Stop the backend

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend
echo "Backend stopped."
```

#### 1. Confirm the table is missing

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "\dt public.<table_name>"
```

Expected: `Did not find any relation named "<table_name>"`.

#### 2. Identify the dump and copy into the container

```bash
# [nuclide]
ls -lht /opt/shepard/timescaledb/backups/ | head -10
docker cp /opt/shepard/timescaledb/backups/<dumpfile> \
  infrastructure-timescaledb-1:/tmp/<dumpfile>
echo "Copy exit code: $?"
```

#### 3. Restore the single table

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  pg_restore -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  --table=<table_name> \
  --no-owner --no-privileges \
  --exit-on-error \
  /tmp/<dumpfile>
echo "Restore exit code: $?"
```

Expected: exit code `0`.

> **Note**: If the table has dependencies (sequences, foreign keys to other tables),
> additional `--table=<dependency>` flags may be required, or use `pg_restore
> --section=pre-data --table=<table_name>` to restore DDL first, then
> `--section=data`.

#### 4. Re-run Flyway if the table is Flyway-managed

If the restored table is part of the Flyway-managed schema (i.e., the dump
pre-dates migrations that alter it), let Flyway apply pending migrations
automatically by restarting the backend:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
docker compose logs -f --tail=60 backend 2>&1 | grep -E "Flyway|migration|ERROR" | head -30
```

If Flyway detects a checksum mismatch (the restored table structure differs from
what the script expects), consult
`docs/admin/runbooks/migration-chain-integrity.md` §2 (resolve out-of-sequence
migration).

#### End-state verification

```bash
# [nuclide]
# Confirm table exists and has rows
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT count(*) FROM public.<table_name>;"

curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'
```

Expected: row count > 0 (if the table had data); all health checks `"UP"`.

---

## Scenario 3 — Full-instance loss

**Symptom**: The Postgres container is gone, the data volume is lost or corrupted
beyond use, or a fresh host requires a full database rebuild.

### Steps

#### 0. Ensure data volume is clear (skip if volume already absent)

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop timescaledb backend
```

If the volume exists and contains corrupt data:

```bash
# [nuclide]
# WARNING: This permanently deletes all Postgres data.
sudo rm -rf /opt/shepard/timescaledb/
mkdir -p /opt/shepard/timescaledb
echo "Volume cleared."
```

If the volume is already absent (fresh host), just ensure the directory exists:

```bash
# [nuclide]
mkdir -p /opt/shepard/timescaledb
```

#### 1. Start a fresh Postgres container

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d timescaledb
```

Wait for Postgres to be ready:

```bash
# [nuclide]
until docker exec infrastructure-timescaledb-1 \
  pg_isready -U "${POSTGRES_USER}" > /dev/null 2>&1; do
  echo "Waiting for Postgres…"; sleep 3
done
echo "Postgres is up."
```

Expected stdout from wait loop: one or more `Waiting for Postgres…` lines, then
`Postgres is up.`

#### 2. Verify init script ran (extensions + schemas)

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  -c "\dx" \
  -c "\dn"
```

Expected: `timescaledb` and `postgis` (if spatial plugin installed) in `\dx`;
`public`, `shepard_ts`, `shepard_spatial` in `\dn`. If extensions are missing,
run the init script manually:

```bash
# [nuclide]
docker exec -i infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  < /opt/shepard/infrastructure/docker-entrypoint-initdb.d/postgres/00-init-postgres-db.sh
```

#### 3. Copy the dump into the container

```bash
# [nuclide]
ls -lht /opt/shepard/timescaledb/backups/ | head -10
docker cp /opt/shepard/timescaledb/backups/<dumpfile> \
  infrastructure-timescaledb-1:/tmp/<dumpfile>
echo "Copy exit code: $?"
```

#### 4. Restore the full database

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  pg_restore -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  --no-owner --no-privileges \
  --exit-on-error \
  /tmp/<dumpfile>
echo "Restore exit code: $?"
```

Expected: exit code `0`.

#### 5. Re-run Flyway migrations

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
docker compose logs -f --tail=80 backend 2>&1 | grep -E "Flyway|migration|MigrationsRunner|Quarkus|ERROR" | head -50
```

Expected: Flyway applies any migrations the dump predates, then `Quarkus started`.

#### 6. Re-establish PgBouncer (if used)

If `pgbouncer` is in your compose stack, restart it after Postgres is confirmed up:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose restart pgbouncer 2>/dev/null || echo "pgbouncer not in this stack — skip"
```

#### End-state verification

```bash
# [nuclide]
# Health
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'

# Row count spot-check on timeseries_data_points
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT count(*) AS total_data_points FROM timeseries_data_points;"

# Flyway chain — confirm all applied
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"
```

Expected: all health checks `"UP"`; `total_data_points` matches expected count
from pre-disaster baseline; all Flyway rows have `success = t`.

---

## Scenario 4 — Point-in-time restore via Wal-G

> **PREREQUISITE**: This scenario requires Wal-G to be configured and actively
> archiving WAL segments to the Garage `shepard-backups` bucket. This is delivered
> by **PG-COLLAPSE-002** (queued, not yet shipped — see
> `aidocs/16-dispatcher-backlog.md`). **Until PG-COLLAPSE-002 ships, skip this
> scenario** and use Scenario 3 (full dump restore) instead, accepting data loss
> back to the last scheduled `pg_dump`.
>
> Once Wal-G is active, this procedure restores the database to a specific LSN
> (Log Sequence Number) — useful when a transaction that corrupted data can be
> identified precisely.

### Steps

#### 0. Stop all writers

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend
echo "All writers stopped."
```

#### 1. Identify the target LSN or timestamp

```bash
# [nuclide]
# List available base backups
docker exec infrastructure-timescaledb-1 \
  wal-g backup-list DETAIL 2>/dev/null | tail -10
```

Expected output (once Wal-G is configured):

```
name                          modified                   wal_segment_backup_start
base_XXXXXXXXXX_D_XXXXXXXXXX  2026-05-27T03:00:01+00:00  000000010000000000000042
…
```

Choose the base backup name (`<backup-name>`) and, optionally, a target restore
time (`<YYYY-MM-DDTHH:MM:SSZ>`).

#### 2. Clear the data volume

```bash
# [nuclide]
docker compose stop timescaledb
sudo rm -rf /opt/shepard/timescaledb/
mkdir -p /opt/shepard/timescaledb
echo "Data volume cleared."
```

#### 3. Fetch the base backup via Wal-G

```bash
# [nuclide]
# Restore base backup into the cleared data directory
docker run --rm \
  --env-file /opt/shepard/infrastructure/.env \
  -v /opt/shepard/timescaledb:/var/lib/postgres/data \
  infrastructure-timescaledb-1 \
  wal-g backup-fetch /var/lib/postgres/data <backup-name>
echo "Base backup fetch exit code: $?"
```

Expected: exit code `0`.

#### 4. Write a `recovery.conf` / `postgresql.auto.conf` target

```bash
# [nuclide]
# For Postgres 16 (PG-COLLAPSE-001 base image), recovery params go in postgresql.auto.conf
cat >> /opt/shepard/timescaledb/postgresql.auto.conf << 'EOF'
restore_command = 'wal-g wal-fetch %f %p'
recovery_target_time = '<YYYY-MM-DDTHH:MM:SSZ>'
recovery_target_action = 'promote'
EOF
touch /opt/shepard/timescaledb/recovery.signal
echo "Recovery config written."
```

Replace `<YYYY-MM-DDTHH:MM:SSZ>` with the exact time immediately before the
bad transaction. Alternatively use `recovery_target_lsn = '<LSN>'` for
LSN-precise targeting.

#### 5. Start Postgres in recovery mode

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d timescaledb
```

Monitor recovery progress:

```bash
# [nuclide]
docker compose logs -f --tail=50 timescaledb 2>&1 | grep -E "recovery|redo|promoted|checkpoint|LOG" | head -30
```

Expected: lines like `LOG: starting point-in-time recovery to …`,
`LOG: restored log file …`, then `LOG: database system is ready to accept
connections` (after promotion).

#### 6. Confirm promotion and re-run Flyway

Once Postgres is promoted (no longer in recovery mode):

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT pg_is_in_recovery();"
```

Expected: `f` (false — fully promoted).

Start the backend to apply any pending Flyway migrations:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
docker compose logs -f --tail=60 backend 2>&1 | grep -E "Flyway|migration|Quarkus|ERROR" | head -40
```

#### End-state verification

```bash
# [nuclide]
# Confirm Postgres is not in recovery
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT pg_is_in_recovery(), now();"

# Health check
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'

# Row count spot-check
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT count(*) AS total_data_points FROM timeseries_data_points;"
```

Expected: `pg_is_in_recovery()` = `f`; all health checks `"UP"`.

---

## Cross-substrate consistency note

Postgres restore does **not** affect:
- **Neo4j** — graph nodes and edges remain as-is; if the Postgres restore goes back
  in time, Neo4j shadow nodes for `:TimeseriesContainer` entities may reference
  hypertables in a state that predates the graph.
- **Garage S3** — file objects remain; `:FileReference` nodes in Neo4j still point
  to them. No action needed unless the Neo4j restore is older.

If the dump is older than recent Neo4j writes:
- Hypertable row counts will be lower than Neo4j `num_entries` metadata.
- Missing rows are gone; the Shepard API will return correct (smaller) datasets.
- No automatic repair is possible without re-ingesting the missing timeseries data.

---

## Rollback

If the restore produces an unusable state, repeat from step 0 of the appropriate
scenario using an older dump file from
`/opt/shepard/timescaledb/backups/`.

There is no automated undo for `pg_restore --exit-on-error`. Always preserve the
**current** dump before step 0 if any data in the current volume may be recoverable:

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  pg_dump -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" -Fc \
  -f /tmp/pre-restore-$(date +%Y%m%d-%H%M%S).pgdump
docker cp infrastructure-timescaledb-1:/tmp/pre-restore-*.pgdump \
  /opt/shepard/timescaledb/backups/
```

---

## Provenance

- Backup recipe: `docs/admin/backup.md` §Postgres.
- Wal-G setup: pending `PG-COLLAPSE-002` in `aidocs/16-dispatcher-backlog.md`.
- Collapse/restart procedure: `docs/admin/runbooks/12-postgres-collapse-restart.md`.
- Migration-chain integrity: `docs/admin/runbooks/migration-chain-integrity.md`.
- TS shadow repair: `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md`.
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md` (PG-COLLAPSE-003).
