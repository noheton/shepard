---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor + maintainer + operator
decision-id: POSTGRES-MULTITENANT-SCHEMA-DECISION
status: ACCEPTED
---

# aidocs/105 — POSTGRES-MULTITENANT decision: one PG, N schemas, ACCEPTED

This is the keystone substrate ADR for shepard v6. It records the
decision the audits + design docs + the operator's "we can change
DBs" mandate all converged on as of 2026-05-24 and unblocks the two
remaining v6 flagship capabilities (SPATIAL + AI). It is an ADR,
not a design exploration; the verdict opens each section.

---

## §1 — Decision

**Decision**: shepard runs **one** Postgres instance carrying the
TimescaleDB + PostGIS + pgvector + pg_stat_statements extensions
co-resident. Each PG-using domain plugin lands in its own schema:
`shepard_ts` for the existing timeseries hypertable, `shepard_spatial`
for the spatial-v6 hypertable, `shepard_ai` for pgvector embeddings,
and `shepard_tables` reserved for the future TableContainer plugin.
The separate `postgis` container is retired. The existing PgBouncer
gains one `[databases]` entry per schema so each plugin gets its own
pool slice. The backup contract is shared: nightly
`pg_dump --schema-only`-plus-data per schema + Wal-G WAL archiving
to a Garage bucket reserved for this purpose, governed by a runtime
admin singleton (`:BackupConfig`, A3b pattern). **Status: ACCEPTED,
2026-05-24.**

---

## §2 — Why now

Three forces converge on this week as the right window:

1. **SPATIAL-V6 needs `shepard_spatial`.** The green-field schema in
   `aidocs/data/90 §3` writes its `CREATE EXTENSION postgis;
   CREATE SCHEMA IF NOT EXISTS shepard_spatial;` block as the
   pre-condition for V2.0.0 (SPATIAL-V6-001). That row is blocked on
   this decision.
2. **AI-V6 needs `shepard_ai`.** The pgvector DDL in
   `aidocs/integrations/97 §6.1` writes against `shepard_ai` on the
   collapsed PG. AI-V6-002 is gated on this decision.
3. **PG-AUDIT-2026-05-24-001 (no backups) is CRITICAL-unresolved.**
   Adding a third or fourth PG instance now multiplies the backup
   surface; collapsing first lets us ship one backup pipeline that
   covers four schemas.

Plus the implicit force: the synthesis architecture report's §3 T2
named this as one of the five architectural tensions the audit fleet
exposed; both candidate plugin SSOTs (aidocs/90, aidocs/97) wrote
their schemas assuming this decision would land. Without the ADR
landing, both designs are at-risk of substrate drift.

---

## §3 — The cut (verbatim from synthesis §3 T2)

Quoting `aidocs/agent-findings/synthesis-architecture-report-2026-05-24.md
§3 T2` verbatim:

> **T2 — One Postgres + N schemas vs N Postgres instances**
>
> **Side A (one Postgres, multiple schemas):** PostgREST adoption
> (Tables plugin §F), real SQL joins across `shepard_ts`/
> `shepard_tables`/`shepard_spatial`, PgBouncer per-schema pools,
> one backup target. **Side B (per-plugin Postgres):** blast-radius
> isolation (a Tables-runaway can't OOM the TS pagecache),
> independent version + restart cadence, separate `mem_limit` per
> workload.
>
> **Decisive constraint:** PG-AUDIT-005 quantifies the cost of side
> B *today* — `shared_buffers=15.5 GB` on a 32 GB host shared with
> Neo4j+Mongo+JVM is already over-provisioned. Adding a Tables
> instance and keeping the Spatial instance makes it strictly worse.
> The IME persona's killer-demo claim ("native Grafana PG joining TS
> with Tables") requires Side A.
>
> **Cut:** **Side A — collapse to one Postgres with three schemas,
> PgBouncer pool-per-schema.** Land alongside aidocs/82 §2.2
> TimescaleDB hypertable conversion. Backlog rows:
> PLUGIN-SPATIAL-AUDIT-004 + PLUGIN-TABLES-AUDIT-004 +
> POSTGRES-MULTITENANT-SCHEMA-DECISION. **Counter-evidence to
> watch:** if the PostGIS workload regularly takes > 30 s per query,
> the side B isolation argument re-opens; mitigate with
> `statement_timeout` set at the role level per PG-AUDIT-004.

Both downstream design docs ratified the cut before this ADR
landed: `aidocs/data/90 §3` opens *"The new schema lives in the
**existing TimescaleDB Postgres** under the new `shepard_spatial`
schema (per synthesis §3 T2)"*; `aidocs/integrations/97 §6` opens
*"Lands on the collapsed PG instance after synthesis T2"*. This ADR
ratifies a choice already encoded into shipped designs.

---

## §4 — Concrete substrate shape

The schemas + extensions tables below are the canonical view; every
later section refers back to them.

### 4.1 Schemas

| Schema | Plugin / owner | Substrate features | Created by |
|---|---|---|---|
| `public` | shared (legacy) | flyway_schema_history, importer_run, permission_audit_log, timeseries metadata | bootstrap (existing) |
| `shepard_ts` | timeseries (in-tree) | timescaledb hypertable; FK to `public.timeseries` metadata; chunks under `_timescaledb_internal` | Flyway V1.13 (this PR's migration) |
| `shepard_spatial` | shepard-plugin-spatial | timescaledb hypertable + postgis geometry columns + GIST + BRIN indexes | spatial-V2.0.0 (`SPATIAL-V6-001`) |
| `shepard_ai` | shepard-plugin-ai | pgvector; HNSW indexes; per-dim tables | ai-V1.0.0 (`AI-V6-002`) |
| `shepard_tables` | shepard-plugin-tables (future) | PostgREST-compatible row tables; one PG table per container | tables-V1.0.0 (PLUGIN-TABLES-AUDIT-001 follow-up) |

The existing TimescaleDB hypertable stays under
`_timescaledb_internal._hyper_1_*`; `shepard_ts` is the named-schema
landing zone for the future logical view + any TS metadata
migrating out of `public`. The ADR-shipping PR reserves the schemas
empty; data movement is a separate, queued pass.

### 4.2 Extensions

| Extension | Source | When | Why |
|---|---|---|---|
| `timescaledb` | (already installed) | (already enabled) | TS hypertables (existing) |
| `postgis` | `postgis/postgis:16-3.5` base image OR apt-add to `timescale/timescaledb:2.24.0-pg16` | bootstrap migration V1.13 | spatial-V6 hypertable + future cross-substrate spatial joins |
| `vector` (pgvector) | apt-add to base image OR rebase on `pgvector/pgvector:pg16` carrying timescaledb extension | bootstrap migration V1.14 | AI-V6 embeddings + HNSW indexes |
| `pg_stat_statements` | bundled with Postgres; needs `shared_preload_libraries` | bootstrap (next restart window) | TS-AUDIT-005 + PG-AUDIT-003 closure |

Image strategy: rebase on `timescale/timescaledb-ha:pg16` and
apt-add `postgresql-16-postgis-3` + `postgresql-16-pgvector` at
image-build time. The `-ha` base already carries the recommended
`shared_preload_libraries` + patched WAL functions; the alternative
(rebasing on `pgvector/pgvector:pg16` and adding TS as the late
package) inverts the upstream-upgrade dependency tree. PG-COLLAPSE-001
(§13) carries the rebase.

Be honest: this requires a **one-time Postgres restart** to add
`postgis`, `vector`, `pg_stat_statements` to
`shared_preload_libraries`. Schedule with the deferred
NEO-AUDIT-013 (APOC) + TS-AUDIT-005 (`pg_stat_statements`) restart
window when MFFD ingest drains.

---

## §5 — PgBouncer pool-per-schema

The current PgBouncer wiring is one `[databases]` entry against the
TS PG. The decision: keep the SAME PgBouncer container but add
per-schema database aliases. The Postgres database name stays
`postgres`; the alias selects which schema the pool's connections
default to via the `search_path`.

```ini
[databases]
shepard          = host=timescaledb port=5432 dbname=postgres
shepard_ts       = host=timescaledb port=5432 dbname=postgres pool_mode=transaction connect_query='SET search_path TO shepard_ts,public'
shepard_spatial  = host=timescaledb port=5432 dbname=postgres pool_mode=transaction connect_query='SET search_path TO shepard_spatial,public'
shepard_ai       = host=timescaledb port=5432 dbname=postgres pool_mode=transaction connect_query='SET search_path TO shepard_ai,public'
shepard_tables   = host=timescaledb port=5432 dbname=postgres pool_mode=transaction connect_query='SET search_path TO shepard_tables,public'
```

Each datasource in `application.properties` + plugin sidecars uses
the schema-named alias as the database in the JDBC URL:

```properties
# main backend (TS metadata + permission audit + flyway)
quarkus.datasource.jdbc.url=jdbc:postgresql://pgbouncer:5432/shepard?prepareThreshold=0

# shepard-plugin-spatial (replaces today's direct-to-postgis URL)
quarkus.datasource.spatial.jdbc.url=jdbc:postgresql://pgbouncer:5432/shepard_spatial?prepareThreshold=0

# shepard-plugin-ai (new datasource)
quarkus.datasource.ai.jdbc.url=jdbc:postgresql://pgbouncer:5432/shepard_ai?prepareThreshold=0
```

The pool sizing decision per schema:

| Pool / database | `pool_size` | `reserve_pool_size` | Rationale |
|---|---|---|---|
| `shepard` (legacy) | 10 | 2 | Current observed peak: 3 conns (per PG-AUDIT live evidence); 10 leaves headroom + the reserve covers Flyway migration spikes |
| `shepard_ts` | 10 | 2 | Mirrors legacy; will absorb workload when TS metadata migrates out of `public` |
| `shepard_spatial` | 10 | 2 | Cold-start sizing; spatial reads are bursty; raise after first MFFD-scale evidence |
| `shepard_ai` | 10 | 2 | Embedding writes are bursty during backfill; reads (kNN) are short |
| `shepard_tables` | 10 | 2 | Reserved; will activate when Tables plugin ships |

**Total server-side connections**: 5 × (10 + 2) = **60**. Postgres
default `max_connections=100` accommodates this with 40 slots
headroom for TS bgworkers + WAL sender + admin probes. Each schema
gets its own pool slice — a runaway query in spatial cannot starve
the TS hypertable. Per-row `pool_mode=transaction` is deliberate
so a future operator can flip one pool to `session` mode (some
PostgREST + RLS flows benefit from session `SET ROLE`) without
affecting siblings.

---

## §6 — Backup contract

This closes PG-AUDIT-2026-05-24-001 (CRIT: no backup) and
GARAGE-AUDIT-2026-05-24-003 (no backup target) in one design.

**The contract**:

- **Nightly schema-aware dump.** Scheduled job runs `pg_dump
  --schema=<each>` for `shepard_ts`, `shepard_spatial`, `shepard_ai`,
  `shepard_tables`, `public` — five files, uploaded to
  `s3://shepard-backups/postgres/YYYY-MM-DD/<schema>.sql.gz`. Each
  restorable independently.
- **Continuous WAL archiving via Wal-G.** `wal-g wal-push` on
  `archive_command`; same Garage bucket under `.../wal/`. Recovery
  via `wal-g wal-fetch`. Point-in-time recovery to any second within
  the WAL window.
- **Retention** is operator-knob (`:BackupConfig`, §7): default 30
  days nightly + 7 days WAL. Garage bucket lifecycle enforces.
- **Bucket separation**: `shepard-backups` is distinct from
  `shepard-public` (file payloads) — retention + IAM scope diverge
  cleanly. Created in PG-COLLAPSE-002 with 50 GB initial cap; raise
  via `garage layout` on restore-size telemetry.
- **Recovery runbook** `docs/admin/runbooks/postgres-restore.md`
  documents four scenarios: (1) corrupt schema, (2) accidental
  table drop (`pg_restore --table=<t>`), (3) full-instance loss
  (`docker volume rm timescaledb-data` simulation → clean image +
  base + WAL replay), (4) point-in-time via Wal-G.
- **DR-drill cadence**: monthly tabletop on scenario 3, separate
  host; success criteria + restore-time logged to
  `docs/admin/runbooks/postgres-restore-drill-log.md`.

This contract is the **template** for the Neo4j + Mongo backups
tracked as `BACKUP-CONTRACT-NEO4J-MONGO` in §13.

### 6.1 Note on host-RAM (PG-AUDIT-005)

Collapsing four PG workloads onto one instance retires the `postgis`
backend's ~256 MB idle footprint and unifies the four under one
`shared_buffers` + `mem_limit` budget. The PG-AUDIT-005 re-tune
recommendation (`shared_buffers=2GB`, `effective_cache_size=4GB`,
`mem_limit: 6g`) applies cleanly to the collapsed instance.

---

## §7 — Operator knobs (`:BackupConfig` per A3b pattern)

Per CLAUDE.md `Always: surface operator knobs in the admin config`
and `feedback_admin_runbooks_pattern.md`, the backup contract ships
runtime-mutable knobs through a small `:BackupConfig` Neo4j
singleton (`HasAppId`, single-instance), the A3b pattern matching
`:FeatureToggleRegistry`, `:SemanticConfig`, and `:UnhideConfig`.

**Fields**:

| Field | Default | Mutable | Notes |
|---|---|---|---|
| `backupSchemaCron` | `0 3 * * *` | yes | Quartz/Spring cron; default 03:00 UTC |
| `walArchivingEnabled` | `true` | yes | Off allows operators to disable WAL streaming during restore drills |
| `backupBucketName` | `shepard-backups` | yes | Re-point to a different Garage bucket for migrations |
| `retentionNights` | `30` | yes | Days of nightly dumps to retain |
| `retentionWalDays` | `7` | yes | Days of WAL to retain |
| `lastBackupAt` | (read-only) | no | Set by job; surfaced for the admin dashboard |
| `lastBackupSizeBytes` | (read-only) | no | Per-schema map; surfaces growth telemetry |

**REST surface** (per A3b admin-config pattern, all RoleAllowed
`instance-admin`):

```
GET   /v2/admin/backup/config
PATCH /v2/admin/backup/config           # RFC 7396 merge-patch
POST  /v2/admin/backup/run-now          # trigger an out-of-band dump
GET   /v2/admin/backup/status           # last 7 runs + sizes + durations
```

**CLI parity** under `shepard-admin backup {status,run-now,
set-cron,set-retention,enable-wal,disable-wal}` with shared
`--output={human,json}` + `--url` + `--api-key` flags per the L1
baseline.

**Precedence**: runtime `:BackupConfig` wins; deploy-time
`shepard.backup.*` properties seed the singleton on first start
(install-default; IaC stays valid). Mutations land in `:Activity`
via `ProvenanceCaptureFilter` (PROV1a, automatic for admin
endpoints).

---

## §8 — Migration plan (concrete)

The shipping plan as a numbered list. Each step is small enough to
ship as one commit; the full sequence is the PG-COLLAPSE-001 PR
(§13) + PG-COLLAPSE-002 (operator-side) + PG-COLLAPSE-003 (runbook).

1. **Image rebase** — TS-HA base + apt-add postgis + pgvector at
   build time. Build recipe at
   `infrastructure/images/timescaledb-ha/Dockerfile`; tag scheme
   `nucli.de/shepard/timescaledb-ha:pg16-pgis3-pgv0.7-YYYY-MM`.
2. **Flyway V1.12** — `shared_preload_libraries` fragment at
   `infrastructure/postgres/postgresql.conf.d/01-preload.conf`
   (bind-mount) adding `pg_stat_statements,postgis,vector`.
   **Requires PG restart** (joins the deferred APOC +
   `pg_stat_statements` restart window).
3. **Flyway V1.13** — `CREATE SCHEMA IF NOT EXISTS shepard_ts;
   CREATE EXTENSION IF NOT EXISTS postgis; CREATE SCHEMA IF NOT
   EXISTS shepard_spatial;` + grants to `shepard` role. Empty
   schemas; no data movement.
4. **Flyway V1.14** — `CREATE EXTENSION IF NOT EXISTS vector;
   CREATE SCHEMA IF NOT EXISTS shepard_ai;` + grants. The plugin-side
   AI V1.0.0 migration (aidocs/97 §6.1) runs against this.
5. **Datasource re-point**: edit `application.properties` so
   `quarkus.datasource.spatial.jdbc.url` flips from
   `jdbc:postgresql://postgis:5432/postgres` to
   `jdbc:postgresql://pgbouncer:5432/shepard_spatial`. The
   `QUARKUS_DATASOURCE_SPATIAL_JDBC_URL` env-var shape stays intact.
6. **PgBouncer `[databases]` block update** per §5 (via the
   compose env-var entrypoint in `pgbouncer/`).
7. **Remove `postgis:` service block** from
   `infrastructure/docker-compose.yml` (current lines 149–168). The
   `spatial` compose profile moves to the `shepard-plugin-spatial`
   feature toggle. **Gated on step 5.**
8. **Remove host-port `5433:5432` mapping** (closes
   PG-AUDIT-011 by construction).
9. **Backup contract bootstrap** — `:BackupConfig` Cypher migration
   (`V51__BackupConfig_singleton.cypher`), `BackupConfigService` +
   admin REST, `BackupSchemaJob` (Quartz), `shepard-admin backup`
   CLI, Wal-G config bind-mount (`infrastructure/postgres/walg-env`).
10. **Garage bucket** — `garage bucket create shepard-backups` +
    key bootstrap + S3 lifecycle policy (50 GB initial cap).
11. **Operator runbook** —
    `docs/admin/runbooks/postgres-collapse-restart.md` covers the
    one-time restart window: pre-restart checklist (MFFD ingest
    paused), restart sequence, post-restart smoke
    (`SELECT extversion FROM pg_extension WHERE extname IN
    ('postgis','vector','pg_stat_statements');`).
12. **Smoke gate** — `infrastructure/scripts/smoke-pg-collapse.sh`
    asserts: all five schemas exist; all three extensions loaded;
    pgvector kNN against fixture works; `pg_stat_statements`
    returns rows; pgbouncer routes each schema-alias correctly.

Step 7 waits on **PM1f sidecar declarations for spatial + hdf5**
(`PM1f-MIGRATION-SPATIAL-HDF5-2026-05-24`) — otherwise the postgis
removal forces an out-of-band spatial-manifest sidecar update. Pair
the PG-COLLAPSE-001 PR with PM1f.

---

## §9 — What this absorbs (audit row closure ledger)

Closed (or partially closed) by this decision shipping:

1. **PG-AUDIT-2026-05-24-001** (CRIT: no PG backups) — §6 contract; PG-COLLAPSE-002 implementation.
2. **PG-AUDIT-2026-05-24-005** (host-RAM mis-sizing) — collapse retires `postgis`'s ~256 MB; one `shared_buffers`/`mem_limit` budget (§6.1).
3. **PG-AUDIT-2026-05-24-011** (`5433:5432` host-port leak) — §8 step 8 removes by construction.
4. **TS-AUDIT-2026-05-24-005** (`pg_stat_statements` unloaded) — §8 step 2; TS-AUDIT-005-DEFERRED promotes ✅ on restart.
5. **PG-AUDIT-2026-05-24-003** (no slow-query observability) — sibling of #4; same install closes both.
6. **PLUGIN-SPATIAL-AUDIT-2026-05-24-004** (collapse postgis container) — §8 steps 3, 5, 7.
7. **PLUGIN-TABLES-AUDIT-2026-05-24-004** (mirror for tables) — §4 reserves `shepard_tables`.
8. **POSTGRES-MULTITENANT-SCHEMA-DECISION** (`aidocs/16` row) — **this doc IS the closure**.
9. **GARAGE-AUDIT-2026-05-24-003** (no backup target) — §6 names `shepard-backups`; PG-COLLAPSE-002 ships the lifecycle + key bootstrap.

Seven substrate-audit rows + two cross-cutting decisions retire on
this ADR; PG-COLLAPSE-001..003 (§13) carry implementation.

---

## §10 — What this UNblocks

- **SPATIAL-V6-001** — `aidocs/data/90 §3` DDL writes against
  `shepard_spatial`; gated only on this ADR.
- **AI-V6-002** — `aidocs/integrations/97 §6.1` pgvector DDL writes
  against `shepard_ai`; same gate.
- **PLUGIN-TABLES-AUDIT-2026-05-24-001** — Tables SSOT can proceed
  knowing the substrate.
- **PLUGIN-SPATIAL-AUDIT-2026-05-24-004 + -TABLES-004** — both
  decision-rows close (§9 #6 + #7).
- **Synthesis §6 Pass-2 PG-collapse row** — flips `queued` →
  `decision-shipped` on merge.

---

## §11 — What this does NOT do

  - Does **not** design `shepard_tables` (Tables plugin SSOT is
    `PLUGIN-TABLES-AUDIT-001`; this doc reserves the slot only).
  - Does **not** ship the migration code (Flyway V1.12/V1.13/V1.14,
    singleton, cron, runbook → `PG-COLLAPSE-001..003`).
  - Does **not** design Neo4j or Mongo backup contracts; §6 is the
    template, each substrate's contract is a sibling ADR
    (`BACKUP-CONTRACT-NEO4J-MONGO`, §13).
  - Does **not** address FAIR-side data-retention (orphan-grace,
    soft-delete cleanup); `SM1a` remains the SSOT for that.
  - Does **not** touch the existing `public` schema. Current tables
    (`permission_audit_log`, `timeseries`, `importer_run`,
    `flyway_schema_history`) stay put; eventual migration to
    `shepard_ts` is a separate optional cleanup pass.

---

## §12 — Decisions log

Alternatives considered, rejected with one-sentence reasoning:

| Alt | Shape | Verdict | Why |
|---|---|---|---|
| A | Keep separate `postgis` container | Rejected | AP-X9 cost (sibling-substrate split where a schema split would suffice) without isolation benefit — same creds reused across both PG instances, no real blast-radius separation, doubles the backup surface |
| B | One PG with separate **databases** (not schemas) | Rejected | Loses cross-substrate SQL JOIN value-prop (cross-database joins require FDW round-trip); complicates PgBouncer to per-DB auth_query; PG schemas already give the namespace separation we want |
| C | Multiple PG instances per plugin | Rejected | AP-X9 violation amplified; impractical operator burden (N restart windows, N backup contracts, N `mem_limit` budgets); no plugin shows a workload demanding instance isolation today |
| D | **One PG with schemas + extensions co-resident** | **ADOPTED** | Aligns with synthesis §3 T2 cut; absorbs seven audit rows; unblocks SPATIAL-V6 + AI-V6; preserves single backup pipeline; enables cross-schema SQL joins (the IME persona "Grafana data-source joining TS with Tables" demo) |
| E | Citus / distributed PG | Rejected | Massive operator complexity (coordinator + worker tier); no scale-out need today (single 32 GB host runs the full live deploy comfortably); incompatible with TimescaleDB community-edition's hypertable model |

The decision is straightforward enough that the §12 dialectic is
five rows, not a dialectical essay. Counter-evidence to watch
(carried from the synthesis quote): if PostGIS workloads regularly
exceed 30 s per query at MFFD scale, the side B isolation argument
re-opens; mitigate first with role-level `statement_timeout` per
PG-AUDIT-004 before any instance-split re-think.

---

## §13 — Backlog rows to file

The following rows land in `aidocs/16-dispatcher-backlog.md` under a
new sibling section to `## AI-V6-*` and `## Synthesis Pass-1`:

```markdown
## PG-COLLAPSE-* — substrate convergence (POSTGRES-MULTITENANT-DECISION shipping)

SSOT: [`aidocs/strategy/105-postgres-multitenant-decision.md`](strategy/105-postgres-multitenant-decision.md).
ADR commits the one-PG-N-schemas shape; this section tracks the
implementation work.

| ID | Item | Size | Status | Notes |
|---|---|---|---|---|
| PG-COLLAPSE-001 | **Image rebase + Flyway V1.12/V1.13/V1.14 + PgBouncer per-schema + datasource re-point + remove `postgis:` service + drop `5433:5432`.** Pair with PM1f-MIGRATION-SPATIAL-HDF5-2026-05-24. Gates SPATIAL-V6-001 + AI-V6-002. | M | queued | aidocs/105 §8 steps 1–8. |
| PG-COLLAPSE-002 | **`:BackupConfig` singleton + admin REST + CLI + Quartz cron + Wal-G + Garage `shepard-backups` bucket bootstrap.** A3b pattern. Closes PG-AUDIT-001 + GARAGE-AUDIT-003. | M | queued | aidocs/105 §6 + §7. |
| PG-COLLAPSE-003 | **`docs/admin/runbooks/postgres-restore.md` + `postgres-collapse-restart.md` + monthly DR-drill log.** Four recovery scenarios (§6). | S | queued | aidocs/105 §6 + §8 step 11. |
| BACKUP-CONTRACT-NEO4J-MONGO | **Apply §6 template to Neo4j + Mongo** (per-substrate dump scripts + Garage + `:BackupConfig` knobs + runbook + drill). Closes synthesis AP-X3 from the remaining two sides. | L | queued | Sibling ADR when triggered. |
```

### Same-PR tracker obligations (per CLAUDE.md standing rules)

1. **This doc** at `aidocs/strategy/105-postgres-multitenant-decision.md`.
2. **PG-COLLAPSE-001..003 + BACKUP-CONTRACT-NEO4J-MONGO** filed in
   `aidocs/16-dispatcher-backlog.md` (rows above).
3. **`aidocs/34-upstream-upgrade-path.md`** row: "POSTGRES-MULTITENANT
   decision: collapse `postgis` into TS Postgres; new schemas;
   admin-config backup". Migration column: "ADDITIVE (decision-only;
   restart + collapse arrive with PG-COLLAPSE-001)". Tests: post-restart
   smoke per §8 step 12.
4. **`aidocs/44-fork-vs-upstream-feature-matrix.md`**: "Postgres
   multi-tenant schemas" row → `📐 ADR-accepted`.
5. **`aidocs/data/00-model-inventory.md`**: §3 Postgres / TimescaleDB
   / PostGIS rows collapse to one Postgres entry listing four schemas
   + four extensions; §4 adds `shepard_spatial`/`shepard_ai`/
   `shepard_tables` rows (initially empty).
6. **Doc-stage index**: `python3
   scripts/regenerate-doc-stage-index.py` in the same commit.
7. **Trinity tracking**: this PR is contributor-only (the ADR);
   operator runbook arrives with PG-COLLAPSE-003; admin REST docs
   arrive with PG-COLLAPSE-002 — both tracked + dated.

---

## §14 — See also

**Internal**:
- `aidocs/agent-findings/synthesis-architecture-report-2026-05-24.md §3 T2` — tension resolved (verbatim quote, §3)
- `aidocs/data/90-spatial-as-temporal-sweep.md §3` — unblocked DDL (SPATIAL-V6-001)
- `aidocs/integrations/97-shepard-plugin-ai-design.md §6` — unblocked DDL (AI-V6-002)
- `aidocs/agent-findings/postgres-pgbouncer-substrate-audit-2026-05-24.md` — PG-AUDIT-001 / 003 / 005 / 011
- `aidocs/agent-findings/ts-design-audit-2026-05-24.md` — TS-AUDIT-005 / 007
- `aidocs/agent-findings/garage-and-docker-stack-audit-2026-05-24.md` — GARAGE-AUDIT-003
- `aidocs/agent-findings/plugin-design-audit-2026-05-24.md §Spatial.C + §Tables.C`
- `aidocs/data/00-model-inventory.md` — updated this PR
- `aidocs/platform/47-dev-experience-and-plugin-system.md` — `SidecarSpec` + `PluginManifest` shapes
- `aidocs/16-dispatcher-backlog.md` — PG-COLLAPSE-001..003 land here

**External**:
- [PostgreSQL extensions](https://www.postgresql.org/docs/16/extend-extend.html)
- [PostGIS install](https://postgis.net/docs/postgis_installation.html)
- [pgvector](https://github.com/pgvector/pgvector)
- [TimescaleDB on PostGIS](https://docs.timescale.com/use-timescale/latest/extensions/postgis/)
- [pg_stat_statements](https://www.postgresql.org/docs/16/pgstatstatements.html)
- [PgBouncer multi-database config](https://www.pgbouncer.org/config.html#section-databases)
- [Wal-G](https://github.com/wal-g/wal-g)
- [Garage S3 lifecycle](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/)
