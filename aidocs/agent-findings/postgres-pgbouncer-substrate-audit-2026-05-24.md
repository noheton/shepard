---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor
---

# Postgres + PgBouncer substrate audit — 2026-05-24

**Scope**: non-TimescaleDB workloads on the TimescaleDB container (regular postgres tables: Keycloak-related, `permission_audit_log`, `importer_run`, `migration_progress`, `migration_tasks`, `flyway_schema_history`, `timeseries` metadata table) **plus** the PgBouncer pooler shipped today (task #77). Hypertable + chunks belong to sibling audit **#208**.

## TL;DR

The non-TS Postgres workload is **tiny + healthy in shape** (6 non-TS tables, total ~3 MB, all schemas + indexes well-formed), but **wrapped in a runtime configuration that is wrong in five places**: PgBouncer healthcheck spams the bouncer logs at 12 errors/min, the Postgres tuning is sized for a 16 GB host when we have 32 GB shared with Neo4j+Mongo+backend (and `shared_buffers=15.5 GB` is too aggressive), `pg_stat_statements` is not loaded (so the audit can't see slow queries), no `statement_timeout` / `idle_in_transaction_session_timeout` (any runaway query blocks autovacuum + a connection forever), there is **no backup configured** at all, and the `tweak-db-settings.sql` file in `infrastructure/` is dead code — never applied, contradicts the live config, will confuse the next operator. The PgBouncer wiring itself is correct (transaction-pool, `prepareThreshold=0`, `auth_query` via `SECURITY DEFINER` is best-practice), but **the pool is over-provisioned for current load** (20 server conns, peak observed: 3 backend conns) and the image is pinned to `:latest`.

**Counts**: 14 findings — **2 CRITICAL** (no backups, healthcheck-spam loop), **4 MAJOR** (pg_stat_statements missing, statement_timeout=0, host-RAM mis-sizing, image `:latest` pin), **5 MINOR** (dead tweak.sql, unused indexes, audit retention orphan, `:latest` for pgbouncer, host-port leak on postgis), **3 BEST-PRACTICE GAPS** (no slow-query log, no pg_partman for audit, JDBC pool unset).

## 1. Substrate state

### Databases
| DB | Owner | Size | Tables non-TS | Notes |
|---|---|---|---|---|
| `postgres` | `postgres` | **2754 MB** | 6 (3 MB total) | Holds TS hypertable + all non-TS shepard tables; single DB design |
| `template0` | `postgres` | 7.3 MB | — | system |
| `template1` | `postgres` | 9.0 MB | — | system |

**Observation**: Single shared DB for both TS hypertable + non-TS tables is fine at current scale — separating into per-workload DBs would force a second JDBC pool and complicate the pgbouncer config without measurable benefit. Note for the future: if TS volume grows past ~500 GB, a second DB would let us back up the non-TS tables in seconds while the TS dump runs for hours.

### Schemas (postgres DB)
- `public` — application tables (see below)
- `pgbouncer` — single function `get_auth(text)` (SECURITY DEFINER, owned by `postgres`, GRANTed to `shepard`) — correct
- `_timescaledb_*` (6 schemas) — TS internal, out of scope for this audit
- `timescaledb_information`, `timescaledb_experimental` — TS catalogs, out of scope

### Non-TS tables
| Table | Rows | Total | Indexes | Purpose |
|---|---|---|---|---|
| `permission_audit_log` | 4 456 | 2.04 MB | 3 (pkey + entity_app_id + occurred_at DESC) | F3 — grant/update audit, append-only |
| `timeseries` | 867 | 1.16 MB | 3 (pkey + shepard_id UNIQUE + 5-tuple UNIQUE) | TS-channel metadata (the 5-tuple → appId migration target — `aidocs/platform/87`) |
| `flyway_schema_history` | 13 | 48 kB | 2 (pkey + success_idx) | Flyway migration log |
| `importer_run` | 0 | 48 kB | 5 (pkey + 4 partial indexes) | Importer state (currently empty — no live runs since the v15 ingest) |
| `timeseries_data_points` | 0 (logical) | 32 kB | 3 | Hypertable parent — real data lives in `_timescaledb_internal._hyper_1_*` chunks (#208 scope) |
| `migration_progress` | 0 | 24 kB | 2 | TS row-migration progress, dormant |
| `migration_tasks` | 0 | 16 kB | 1 | TS row-migration tasks, dormant |

### Roles
| Role | Attributes | Used by |
|---|---|---|
| `postgres` | Superuser + Create role + Create DB + Replication + Bypass RLS | Init + admin only |
| `shepard` | none (plain user) | Backend (Quarkus), PgBouncer auth_query |

Auth is clean: backend uses `shepard` (no superuser), PgBouncer reads `pg_shadow` via the SECURITY DEFINER function.

### pg_hba.conf
```
local   all  all                trust            # docker exec only — defensible
host    all  all  127.0.0.1/32  trust            # docker exec only — defensible
host    all  all  ::1/128       trust
local   replication  all        trust
host    replication  all  127.0.0.1/32  trust
host    replication  all  ::1/128       trust
host    all  all  all  scram-sha-256             # everything from the docker network
```
**OK** — the only callers that bypass scram-sha-256 are local socket + loopback, both reachable only via `docker exec`.

## 2. PgBouncer state

### Config (auto-generated from compose env)
```ini
[databases]
postgres = host=timescaledb port=5432 auth_user=shepard
[pgbouncer]
listen_port = 5432
auth_type   = scram-sha-256
auth_user   = shepard
auth_query  = SELECT username, password FROM pgbouncer.get_auth($1)
pool_mode             = transaction
max_client_conn       = 200
default_pool_size     = 20
server_idle_timeout   = 600
ignore_startup_parameters = extra_float_digits
admin_users = postgres
```

**Image**: `edoburu/pgbouncer:latest` → resolves to PgBouncer 1.25.1 (Oct 2025), libevent 2.1.12, OpenSSL 3.5.4. **`:latest` is a CRITICAL anti-pin** — next operator-side `docker compose pull` ships an untested upgrade.

### Backend wiring (correct)
```
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://pgbouncer:5432/postgres?prepareThreshold=0
QUARKUS_DATASOURCE_SPATIAL_JDBC_URL=jdbc:postgresql://postgis:5432/postgres  # bypasses pgbouncer
```

- `prepareThreshold=0` correctly disables PostgreSQL JDBC server-side prepared statements (required for `pool_mode=transaction`). Documented inline.
- Spatial datasource bypasses the pooler (separate PostGIS container) — fine, but means PgBouncer pooling only covers the main TS-DB workload; the spatial pool stays at Agroal defaults.

### Live pool occupancy (from logs)
```
stats: 5 xacts/s, 15 queries/s, 0 client parses/s, 0 server parses/s, 0 binds/s,
       in 54900 B/s, out 2528 B/s, xact 7793 us, query 2492 us, wait 0 us
```
- 5 xacts/s steady-state, **wait time 0 us** — pool is way over-provisioned for current load. 20 server slots when peak observed backend connection count is 3 (one TS bgwriter + 2 JDBC).
- `0 client parses/s` confirms server-side preparing is OFF; statement cache lives in Agroal (correct).
- Backend pool size: **unconfigured** in `application.properties`, so Quarkus defaults (Agroal: `max-size=20`, `min-size=5`). PgBouncer's 20-slot pool will start to queue under load if a second backend instance shares it.

### Healthcheck loop spam — **CRITICAL**
Container healthcheck is `pg_isready -h localhost -p 5432`. `pg_isready` opens a connection with **no DB name** — PgBouncer routes the connection to `auth_user=shepard` with `db=pgbouncer` (the default DB for that probe), but **`pgbouncer` is a reserved DB name and is rejected when used as `auth_dbname`**. The log fills with:
```
ERROR C-0x...: (nodb)/postgres@127.0.0.1:50018 cannot use the reserved "pgbouncer" database as an auth_dbname
LOG   C-0x...: closing because: bouncer config error (age=0s)
WARNING C-0x...: pooler error: bouncer config error
```
**At 12 errors/min × 1440 min/day = ~17 280 spurious error logs per day**, and `pg_isready` still reports healthy because the connection refusal is counted as "server responded". The healthcheck WORKS but pollutes the log + masks any real config error. Fix: `pg_isready -h localhost -p 5432 -d postgres -U shepard` (or use `pg_isready -q` to silence client output AND pick a valid DB).

## 3. Antipatterns (severity-sorted)

### CRITICAL

**`PG-AUDIT-2026-05-24-001` — No backup of postgres data**
There is no `pg_dump` job, no WAL archiving, no streaming replica, no `infrastructure/scripts/*backup*`, no Makefile `backup-postgres` target, no cron / systemd-timer on the host. A volume corruption or a wrong `docker volume rm` and the Keycloak realm + permission audit + TS metadata are gone, irrecoverable. The TS hypertable is the largest victim (2.6 GB), but the non-TS tables include the permission audit trail (regulatory artifact) and Flyway history (without which the next `migrate-at-start` would replay all migrations on a fresh DB and crash on existing tables). **Fix shape**: add a nightly `pg_dump` to a Garage bucket + WAL archiving to the same bucket; document recovery in `docs/admin/runbooks/`. Sibling to `aidocs/16` row `ADMIN-RUNBOOKS-LIBRARY`.

**`PG-AUDIT-2026-05-24-002` — PgBouncer healthcheck spams 17 K error logs/day**
See "Healthcheck loop spam" above. Fix: `test: ["CMD", "pg_isready", "-q", "-h", "localhost", "-p", "5432", "-d", "postgres", "-U", "shepard"]` in `docker-compose.override.yml`.

### MAJOR

**`PG-AUDIT-2026-05-24-003` — `pg_stat_statements` not loaded**
`SELECT extname FROM pg_extension` returns `pgcrypto, plpgsql, timescaledb` only. Without `pg_stat_statements` we can't profile slow queries from inside PG, can't surface importer hot paths (every `timeseries` row by 5-tuple uniqueness lookup hits the pkey 85.9M times for 867 rows — see #5 below), and can't feed query-level metrics into the OBS-MFFD1 Shepard-self-observability work. Fix: add `shared_preload_libraries='timescaledb,pg_stat_statements'` via `postgres -c` in compose + `CREATE EXTENSION` in `00-init-postgres-db.sh`. Requires Postgres restart.

**`PG-AUDIT-2026-05-24-004` — `statement_timeout=0` + `idle_in_transaction_session_timeout=0`**
Both timeouts are unlimited. A runaway `SELECT` or a backend bug that leaves a transaction open forever will hold a JDBC pool slot + block autovacuum on whatever tables the transaction touched indefinitely. Recommended defaults: `statement_timeout=5min` (override via JDBC `options=-c statement_timeout=...` for long migrations), `idle_in_transaction_session_timeout=10min`. Set per-role to avoid affecting TS bgworkers: `ALTER ROLE shepard SET statement_timeout='5min'; ALTER ROLE shepard SET idle_in_transaction_session_timeout='10min';`.

**`PG-AUDIT-2026-05-24-005` — Host-RAM sizing mismatch (shared_buffers=15.5 GB on 32 GB shared host)**
Live config (from `timescaledb-tune` v0.18.1 run 2026-05-14): `shared_buffers=15902MB` (≈ 50% of host RAM), `effective_cache_size=47706MB` (≈ 1.5× host RAM — implies tuner thought it had more). Host has 32 GB total, currently `used=11Gi free=18Gi`. **Neo4j and Mongo on the same host also claim large heaps**. Postgres is currently using only 2.5 GB resident (`docker stats`), so the page cache is happy, but under MFFD-Dropbox + Neo4j ingest concurrency, the four large heaps (Neo4j, Mongo, Postgres `shared_buffers`, JVM backend) compete for the same 32 GB. Recommended: pin `mem_limit: 6g` on `timescaledb` in compose + tune `shared_buffers=2GB`, `effective_cache_size=4GB` (re-run `timescaledb-tune --pg-config /usr/lib/postgresql/16/bin/pg_config --memory 6GB` and capture output to a checked-in `postgresql.conf.fragment`). 

**`PG-AUDIT-2026-05-24-006` — `edoburu/pgbouncer:latest` image pin**
`docker-compose.override.yml:140` uses `:latest`. Any `docker compose pull` on a new operator host fetches whatever the maintainer pushed last. Fix: pin to the resolved digest, e.g. `edoburu/pgbouncer:1.25.1@sha256:85d1e38593617af1b5f7f285e97d407e56c29939683cc7cfe4c8f6dc19f1268b`, OR move to an officially-versioned image (`bitnami/pgbouncer:1.25.1`, `pgbouncer/pgbouncer:1.25.1`). Sibling note: `timescale/timescaledb:2.24.0-pg16` IS pinned — apply the same discipline to pgbouncer.

### MINOR

**`PG-AUDIT-2026-05-24-007` — `infrastructure/tweak-db-settings.sql` is dead code**
The file sizes Postgres for 16 GB host + 20 connections. **It is never executed.** The live config was set by `timescaledb-tune` on first startup (its sentinel: `timescaledb.last_tuned='2026-05-14T10:21:14Z'` in `postgresql.conf`). The values in `tweak-db-settings.sql` (e.g. `shared_buffers=4GB`, `max_connections=20`) contradict the live values (`15902MB`, `100`). Either wire it into the init script (`docker-entrypoint-initdb.d`) and remove `timescaledb-tune` reliance, or delete the file. Documentation hazard: the next operator will believe the SQL describes the running config.

**`PG-AUDIT-2026-05-24-008` — Unused indexes on `permission_audit_log` (0 scans both)**
`perm_audit_entity_app_id_idx` (272 kB) and `perm_audit_occurred_at_idx` (192 kB) both show `idx_scan=0`. They were sized for query patterns that exist in code (`PermissionAuditLogQueryService`) but the admin REST endpoint hasn't been called against this instance. **Keep** — they're correctly designed and will be needed on first audit-log read. But: flag in the audit doc so reviewers don't drop them in a future cleanup.

**`PG-AUDIT-2026-05-24-009` — Unused indexes on `importer_run` (5 indexes, all 0 scans)**
Table is empty → indexes are zero-cost. **Keep** for the same reason — they're aligned to the service queries (`status, finished_at WHERE status IN (...)` etc.). Listed for completeness.

**`PG-AUDIT-2026-05-24-010` — No retention/cleanup on `permission_audit_log`**
Migration `V1.10.0` comments explicitly: *"Retention is left to the operator (e.g. partition pruning, pg_partman)."* The table is BIGSERIAL + plain heap, currently 4 456 rows in 6 days = ~750/day = ~270 K/year. Sustainable for years, but no operator runbook exists. Fix: add to `docs/admin/runbooks/postgres-permission-audit-retention.md` with three options (cron `DELETE WHERE occurred_at < NOW() - INTERVAL '2 years'`, pg_partman monthly partitions, or accept indefinite growth and budget 270 KB/year per active user-month).

**`PG-AUDIT-2026-05-24-011` — PostGIS host port `5433:5432` exposes a second DB to the host**
`docker-compose.yml:160` publishes `5433:5432`. The PostGIS container has the same `shepard / shepard_secret` credentials as TimescaleDB (init script reused). On a dev box this is fine; on a prod box this is a second internet-reachable DB if firewalld is misconfigured. Fix: drop the host-port mapping unless an operator explicitly needs psql-from-host (use `docker exec postgis psql` instead).

### BEST-PRACTICE GAPS

**`PG-AUDIT-2026-05-24-012` — No `log_min_duration_statement`**
Current: `-1` (off). Recommended: `1000` (log queries >1 s) — pairs with `pg_stat_statements` to capture slow-query examples with bind values. Cheap: <1% perf overhead at that threshold.

**`PG-AUDIT-2026-05-24-013` — `permission_audit_log` will need partitioning at scale**
At 270 K rows/year × ~500 bytes = ~135 MB/year, the table stays small for years. But: if write volume jumps (multi-tenant adoption, plugin-driven grant cascades), partitioning by `occurred_at` (monthly) lets `DROP PARTITION` retention beat row-by-row `DELETE`. Design now → ship when needed (rule: when the table crosses 1 GB or 10 M rows, whichever first).

**`PG-AUDIT-2026-05-24-014` — Backend Agroal pool size + leak detection unset**
`application.properties` has no `quarkus.datasource.jdbc.max-size` / `min-size` / `leak-detection-interval`. Quarkus defaults: `max=20`, `min=0`, no leak detection. Combined with PgBouncer's `default_pool_size=20`, **a single backend instance + a single TS bgwriter exhausts the PgBouncer pool** (Agroal can open 20 → PgBouncer routes 20 → 21st client waits). For single-instance shepard this is fine; for a future horizontal-scale (two backend replicas, A8 task family), the math breaks. Recommended explicit pin: `quarkus.datasource.jdbc.max-size=15`, `quarkus.datasource.jdbc.leak-detection-interval=PT2M` (catches connection leaks in dev/test), `quarkus.datasource.jdbc.background-validation-interval=PT30S`.

## 4. Stack-level findings (recap)

| Surface | Current | Risk | Recommended |
|---|---|---|---|
| `timescale/timescaledb:2.24.0-pg16` | pinned tag | OK | keep |
| `edoburu/pgbouncer:latest` | unpinned | HIGH (PG-AUDIT-006) | digest-pin or move to `bitnami/pgbouncer:1.25.1` |
| TimescaleDB `mem_limit` | unset (32 GB host) | MEDIUM (PG-AUDIT-005) | `mem_limit: 6g` + retune |
| PgBouncer `mem_limit` | unset | LOW (uses ~50 MB) | optional `mem_limit: 256m` |
| TS healthcheck | none on `timescaledb` service | MEDIUM | add `pg_isready -U shepard -d postgres` |
| PgBouncer healthcheck | spammy (PG-AUDIT-002) | CRITICAL | `pg_isready -q -d postgres -U shepard` |
| TS volume | host bind `/opt/shepard/timescaledb` → `/var/lib/postgres/data` | OK | document on `docs/admin/install.md` |
| PG driver | `org.postgresql:postgresql:42.7.11` | OK (current latest is 42.7.11, Nov 2025) | keep |
| pgbouncer version | 1.25.1 (Oct 2025) | OK | digest-pin per PG-AUDIT-006 |
| Backup | **none** (PG-AUDIT-001) | CRITICAL | add nightly `pg_dump` + WAL archive |

## 5. Cross-substrate observations (light)

- The `timeseries` metadata table receives **85.9 M pkey scans for 867 rows** — almost 100 K scans/row in this instance's lifetime. The synthesis audit should look at whether the importer / TS write path is doing a `findById` per data-point write (it shouldn't — V1.11.0 added `shepard_id UUID` to support batch lookup by single ID), or whether OGM hydration is firing the pkey lookup per row. Likely candidate: the `timeseries_data_points` foreign key constraint enforcement (`fkog3jr0iowrx3wkun79k0ihs6o`) pkey-scans on every row insert — that's the cost of FK integrity at 7.5 M data points. Confirms the design intent of #208 (chunk-level audit + compression analysis).
- The `pgbouncer.get_auth` SECURITY DEFINER pattern is a **transferable template** to any future SCRAM-protected Postgres in the stack (a second timeseries store, a metrics DB). Capture in `docs/admin/install.md` as the reference auth_query pattern.
- The split between Postgres-via-pgbouncer (main) and Postgres-direct (postgis) is asymmetric. If the spatial plugin grows past trivial use, the spatial datasource should also route through a (second?) pgbouncer instance — or both should share one pgbouncer with two `[databases]` entries.

## Top 5 fixes (ordered by effort × risk reduction)

1. **`PG-AUDIT-2026-05-24-002` — Fix PgBouncer healthcheck.** XS effort, eliminates 17 K spurious error logs/day, makes the log signal usable. One line in `docker-compose.override.yml`.
2. **`PG-AUDIT-2026-05-24-001` — Wire nightly `pg_dump` → Garage bucket + recovery runbook.** S effort, closes a CRITICAL data-loss gap. Reuses the Garage substrate that's already deployed.
3. **`PG-AUDIT-2026-05-24-006` — Digest-pin `edoburu/pgbouncer` (or move to a versioned image).** XS effort, prevents an unannounced upgrade ambushing the operator.
4. **`PG-AUDIT-2026-05-24-007` — Delete `infrastructure/tweak-db-settings.sql` OR wire it into init.** XS effort, eliminates documentation drift that already misleads code-reading agents.
5. **`PG-AUDIT-2026-05-24-003` + `004` — Load `pg_stat_statements` and set per-role `statement_timeout` + `idle_in_transaction_session_timeout`.** S effort combined. Closes the observability gap (PG-003) + the runaway-connection gap (PG-004) with a single restart.

## Out of scope (reminders)

- `timeseries_data_points` hypertable, chunk policy, compression, retention — sibling audit #208.
- Garage backup target sizing + IAM model — covered in storage audits.
- Neo4j heap competition with `shared_buffers` — needs the synthesis audit.
