---
layout: default
title: "Runbook — Postgres collapse + controlled restart"
description: "Detect a Postgres service collapse, capture diagnostics, attempt in-place restart, escalate to volume-preserving stop/start, and if the data volume is corrupt, wipe and restore from backup. Includes post-restart Flyway and row-count verification."
stage: feature-defined
last-stage-change: 2026-05-27
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Postgres collapse + controlled restart

> **When to use this runbook**: The Postgres / TimescaleDB service has stopped,
> is crash-looping, is not accepting connections, or was reported down by the
> health endpoint. Follow the steps in order — each escalates only if the
> previous did not restore service.

**Container / service reference:**

| Compose service | Running container name |
|---|---|
| `timescaledb` | `infrastructure-timescaledb-1` |

**Compose working directory**: `/opt/shepard/infrastructure/` (all `docker compose`
commands below assume this directory).

---

## Step 1 — Detect the collapse

Run the health probe to establish the failure mode:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose ps db timescaledb 2>/dev/null || docker compose ps timescaledb
```

Expected when healthy:

```
NAME                            STATUS          PORTS
infrastructure-timescaledb-1   running (healthy)   0.0.0.0:5432->5432/tcp
```

Indicators of collapse:

| `STATUS` value | Meaning |
|---|---|
| `exited (1)` | Postgres crashed; not restarting |
| `restarting` | Crash-looping; Docker restart policy active |
| `(unhealthy)` | Container running but health probe failing |
| No row / empty output | Container does not exist |

Also check the Shepard health endpoint (if the backend is still up):

```bash
# [nuclide]
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '.checks[] | select(.name | test("datasource|timescale|postgres"; "i")) | {name, status, data}'
```

Expected when Postgres is down: one or more checks with `"status": "DOWN"` and
a connection error in `data`.

---

## Step 2 — Capture the last-good log tail

Before touching anything, preserve the final log lines for post-mortem:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose logs --tail=100 timescaledb 2>&1 \
  | tee /opt/shepard/timescaledb/backups/collapse-log-$(date +%Y%m%d-%H%M%S).txt
echo "Log saved."
```

Expected: 100 lines of Postgres log output, ending with the last message before
the crash. The saved file is in your backups directory for later analysis.

Key patterns to look for in the log tail:

| Pattern | Likely cause |
|---|---|
| `PANIC: could not write to file "pg_wal/…"` | WAL disk full or I/O error |
| `FATAL: database file appears to be corrupt` | Data volume corruption |
| `Out of memory` | OOM kill; container memory limit too low |
| `FATAL: max_connections exceeded` | Connection storm; PgBouncer not pooling |
| `SIGTERM received` | Clean shutdown (expected, not a crash) |
| `SIGSEGV / SIGABRT` | Postgres process crash (hardware or software bug) |

---

## Step 3 — Attempt in-place restart

For most transient crashes (OOM kill, clean SIGTERM, brief I/O spike), an
in-place restart is sufficient:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose restart timescaledb
echo "Restart exit code: $?"
```

Wait for Postgres readiness:

```bash
# [nuclide]
until docker exec infrastructure-timescaledb-1 \
  pg_isready -U postgres > /dev/null 2>&1; do
  echo "Waiting for Postgres…"; sleep 3
done
echo "Postgres is up."
```

Expected: `Waiting for Postgres…` (zero or more lines), then `Postgres is up.`

Re-check health:

```bash
# [nuclide]
docker compose ps timescaledb
```

If `STATUS` is `running (healthy)` — **proceed to the post-restart verification
in Step 6**. If the container exits again within 30 seconds, move to Step 4.

---

## Step 4 — Volume-preserving stop, config check, restart

If the in-place restart fails (container exits again), stop cleanly, inspect the
configuration, and restart explicitly.

#### 4a. Stop the container cleanly

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop timescaledb
echo "Container stopped."
```

#### 4b. Check the environment and config

```bash
# [nuclide]
# Confirm .env file is intact and required vars are set
grep -E "POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD|POSTGRES_SHEPARD_USER" \
  /opt/shepard/infrastructure/.env

# Confirm the data directory exists and is non-empty
ls -lh /opt/shepard/timescaledb/ | head -5

# Confirm the data directory owner (Postgres runs as uid 70 in the Alpine image)
stat /opt/shepard/timescaledb/
```

Expected: `.env` vars are present and non-empty. Data directory contains
`PG_VERSION`, `global/`, `base/`. If the data directory is empty or missing
`PG_VERSION`, the volume was lost — skip to Step 5.

Data directory owner should be `70:70` (Postgres Alpine uid). If it is `root:root`
or another user, fix it:

```bash
# [nuclide]
sudo chown -R 70:70 /opt/shepard/timescaledb/
```

#### 4c. Restart

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d timescaledb
```

Wait for readiness:

```bash
# [nuclide]
until docker exec infrastructure-timescaledb-1 \
  pg_isready -U postgres > /dev/null 2>&1; do
  echo "Waiting for Postgres…"; sleep 3
done
echo "Postgres is up."
```

Check status:

```bash
# [nuclide]
docker compose ps timescaledb
```

If `STATUS` is `running (healthy)` — **proceed to Step 6 for post-restart
verification**. If the container still exits or is unhealthy, check the log
for `FATAL: database file appears to be corrupt` or similar I/O errors — move
to Step 5.

---

## Step 5 — Data volume is corrupt: full wipe + restore from backup

> **WARNING**: This permanently destroys all data in the Postgres volume. Confirm
> you have a usable backup at `/opt/shepard/timescaledb/backups/` before
> proceeding.
>
> If you have not yet captured the log from the corrupt volume, do so now
> (repeat Step 2 with `-v /opt/shepard/timescaledb:/var/lib/postgres/data`
> mounted to a temporary container so you can run `pg_dumpall --globals-only` or
> at least list the `PG_VERSION` file).

#### 5a. Stop all dependent services

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend timescaledb
echo "Backend and Postgres stopped."
```

#### 5b. Wipe the data volume

```bash
# [nuclide]
# Confirm the path before running
echo "About to wipe: /opt/shepard/timescaledb/"
ls /opt/shepard/timescaledb/ | head -5

sudo rm -rf /opt/shepard/timescaledb/
mkdir -p /opt/shepard/timescaledb
echo "Data volume wiped and recreated."
```

#### 5c. Restore from backup

Follow **Scenario 3 (Full-instance loss)** in
`docs/admin/runbooks/11-postgres-restore.md` from Step 1 onwards. The restore
procedure covers:

- Starting a fresh container with the init script.
- Copying and restoring the dump.
- Re-running Flyway via the backend startup.

Return here for Step 6 (post-restart verification) after the restore runbook
declares success.

---

## Step 6 — Post-restart verification

Run all checks in sequence. Each expected output is shown inline.

#### 6a. Container health

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose ps timescaledb
```

Expected: `STATUS = running (healthy)`.

#### 6b. Flyway `current_version`

```bash
# [nuclide]
source /opt/shepard/infrastructure/.env
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT version, description, installed_on, success
      FROM flyway_schema_history
      ORDER BY installed_rank DESC LIMIT 5;"
```

Expected: the most recent row matches the latest migration version in
`backend/src/main/resources/db/migration/` and has `success = t`. No rows with
`success = f`.

To cross-check against the classpath:

```bash
# [nuclide]
ls /opt/shepard/backend/src/main/resources/db/migration/V*.sql \
  | sort -V | tail -3
```

The highest version file should match the top Flyway history row.

#### 6c. Row count spot-check on `timeseries_data_points`

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  psql -U "${POSTGRES_SHEPARD_USER}" -d "${POSTGRES_DB}" \
  -c "SELECT count(*) AS total_data_points FROM timeseries_data_points;"
```

Expected: a non-zero count consistent with the amount of timeseries data you
expected to be present. If this is `0` and you had data before the collapse,
the volume restore did not include the data — check that the dump file was
current and re-run the restore with the correct `<dumpfile>`.

#### 6d. Application health endpoint

```bash
# [nuclide]
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'
```

Expected: all checks `"UP"`. If `datasource` or `timescaledb` checks are `DOWN`,
the backend has not yet completed startup — wait 30 seconds and re-run.

#### 6e. Smoke test — write and read

```bash
# [nuclide]
# Quick functional probe: call a read-only API that exercises the datasource
curl -fsS "http://localhost:8080/shepard/api/healthz/live" | jq .
```

Expected: `{"status": "UP"}`.

---

## Rollback

If Steps 3–4 produced a worse state than the original collapse (e.g. you
accidentally cleared useful data during troubleshooting), the only recovery path
is Step 5 (volume wipe + restore from backup). Ensure the backup file you select
pre-dates the troubleshooting steps if those steps wrote data.

---

## Escalation path

| Condition | Action |
|---|---|
| Step 3 restart fails | Proceed to Step 4 |
| Step 4 restart fails with `corrupt` in logs | Proceed to Step 5 |
| No usable backup exists in `/opt/shepard/timescaledb/backups/` | Contact instance owner; data loss is unavoidable; restore from off-site backup or cold storage |
| Recovery loops back to collapse after Step 5 | Hardware failure likely; migrate to a new host; restore from off-site backup |

---

## Provenance

- Restore scenarios: `docs/admin/runbooks/11-postgres-restore.md`.
- Migration-chain integrity: `docs/admin/runbooks/migration-chain-integrity.md`.
- TS shadow repair: `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md`.
- Neo4j equivalent: `docs/admin/runbooks/04-restore-neo4j.md`.
- Backup configuration (Wal-G + Garage): pending `PG-COLLAPSE-002` in
  `aidocs/16-dispatcher-backlog.md`.
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md` (PG-COLLAPSE-003).
