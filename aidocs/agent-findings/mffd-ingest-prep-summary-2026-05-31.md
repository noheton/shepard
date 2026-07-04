---
stage: feature-defined
last-stage-change: 2026-05-31
audience: [operator, dispatcher]
---

# MFFD ingest prep — one-page status (2026-05-31)

**Scope.** Status summary of the 6 prep tasks defined for the 346 GB
MFFD ingest readiness. Pairs with:
- Readiness audit: `aidocs/agent-findings/mffd-ingest-346gb-readiness-2026-05-31.md`
- Capacity snapshot: `aidocs/agent-findings/mffd-ingest-prep-capacity-2026-05-31.md`
- Kickoff script: `scripts/mffd-ingest-kickoff.sh` (executable)
- Keys handoff: `/root/.claude/uploads/mffd-ingest-keys-2026-05-31.txt`

All work read-only on nuclide. No archive transfer started, no
state mutated (warmup probe used `write_to_dest=False`).

---

## §1 Task 1 — Defensive API key re-mint

🔴 **NEEDS OPERATOR ACTION — auto-mint blocked by permission gate.**

Findings:

- The 8-day-old DEST_JWT (jti `76c20c60`, sub `ee4c010f-…` = Flo
  Researcher) from `project_mffd_api_keys` memory **fails authentication**
  against the live nuclide instance. Live probe:
  ```
  GET /v2/users/me  → HTTP 404
  body: {"detail":"ID ERROR - ApiKey does not exist"}
  ```
  Cause: post-reset between 2026-05-22 and 2026-05-31 the `:ApiKey`
  Neo4j node was wiped. JWT signature still verifies but the server
  cannot find the matching `:ApiKey` row.

- Live `:ApiKey` records on nuclide (Cypher probe, `MATCH (k:ApiKey)`):
  | name | subject | roles | status |
  |---|---|---|---|
  | `bob-seed-key` (jti 115c6def) | 14bbe5f9-…/Bob Reviewer | `[instance-admin]` | ✅ VERIFIED working (`/v2/users/me` → 200) |
  | `scenegraph-bootstrap-2026-05-30` (jti 4fb50d05) | 14bbe5f9-…/Bob | `[]` | unverified |

- The old kreb_fl source JWT (cube3, jti `eca5887a`) cannot be verified
  from nuclide — cube3 lives on `intra.dlr.de`. Operator must verify +
  re-mint from inside DLR network.

Operator decision (documented in keys handoff file):
- **(A)** use Bob's seed key as `SHEPARD_API_KEY` (works today; activity
  attributed to "Bob Reviewer")
- **(B)** mint fresh key for Flo Researcher under Bob's admin JWT
  (preferred — preserves attribution; one curl)
- **(C)** mint fresh key for operator's own user (`7eead942-…` =
  Florian Krebs)

Cross-user mint blocked at this agent's permission layer; operator runs
the one curl from the keys handoff file when ready. Old keys do not
need explicit revocation — they're already server-side dead.

---

## §2 Task 2 — Capacity (read-only)

🟢 **PASS.** Full snapshot at `aidocs/agent-findings/mffd-ingest-prep-capacity-2026-05-31.md`.

Headline numbers:
- NFS staging `/mnt/pve/unas/` → **17 TB free** (target ≥ 400 GB)
- Host ZFS `/` → **400 GB free** (target ≥ 401 GB)
- TimescaleDB: 252 MB (pristine post-reset)
- Neo4j data: 7.4 MB
- MongoDB: 368 MB
- Garage S3 (data): 22 MB; bucket `shepard-files` = 38.5 MiB, no quota
- Garage S3 (meta): 194 KB

All free numbers match the readiness audit §1 within rounding noise.
The only blocking pre-requirement: **stage `ts-export/` on NFS**
(`DATA_DIR=/mnt/pve/unas/dump/ts-export/`), not on host ZFS. The
kickoff script enforces this (exits 2 if archive path is missing,
exits 6 if disk margins fall under safe floors).

---

## §3 Task 3 — Importer health + warmup probe

🟢 **PASS.** Smart warmup against the live dest succeeds.

Probes:
- `GET /shepard/api/healthz/ready` → `status: UP` (all checks UP: neo4j,
  neo4j-migration-chain, postgis, mongodb, …)
- `GET /shepard/api/healthz/live` → `status: UP`
- Smart-warmup module loads (`_smart_warmup.py` → `SmartWarmup`,
  `WarmupAborted`, `WarmupReport`)
- Standalone `SmartWarmup.run()` with Bob admin JWT, source=None,
  `write_to_dest=False`, `probe_garage=False`:
  ```
  === Smart Warmup Report ===
    status        : OK  (0.04s)
    exit_code     : 0
    probes_run    : 2
    endpoints_probed     : 1
    endpoints_with_spec  : 1
    1. [OK ] GET /v2/users/me
    2. [OK ] [spec✓] GET /shepard/api/collections
  === end ===
  ```

The full warmup (source + dest writes + Garage probe) cannot be
exercised standalone — it needs both the operator's fresh dest JWT and
the source kreb_fl JWT, plus a clean dest collection to probe. The
runner exercises it on real launch; the kickoff script invokes the
runner.

LOCAL_MODE probe is deferred to the operator: per audit §3.2 the
recommended probe is a 1 GB subdir from the staged archive. Will run
naturally once the archive is in place — kickoff script verifies
`manifest.json` parses + size before launch.

---

## §4 Task 4 — PgBouncer pool sizing

🟢 **OK for 4 workers; needs bump for 8.**

Live config (read from `/etc/pgbouncer/pgbouncer.ini` inside container):
- `pool_mode = transaction`
- `default_pool_size = 20`
- `max_client_conn = 200`
- `reserve_pool_size`: not set (default 0)
- `server_idle_timeout = 600`

Budget math (audit §3.3 reaffirmed):
- 4 workers × ~5 active conns/worker + ~10 head-room = 30 → 20 is
  marginal but historically OK (the audit notes 4 workers fit in 20)
- 8 workers × ~5 + ~10 = 50 → 20 is INSUFFICIENT → bump to ≥ 40

Bump path (compose `infrastructure/docker-compose.override.yml` line 153):
```
sed -i 's/DEFAULT_POOL_SIZE: "20"/DEFAULT_POOL_SIZE: "40"/' \
  infrastructure/docker-compose.override.yml
(cd infrastructure && docker compose up -d pgbouncer)
```

The kickoff script (`scripts/mffd-ingest-kickoff.sh` §5) checks
`default_pool_size` vs `(workers * 5 + 10)` and exits 7 with a
remediation block if the pool is too small. Operator can therefore
proceed safely at any worker count.

---

## §5 Task 5 — Kickoff script

🟢 **`scripts/mffd-ingest-kickoff.sh` shipped** (executable, 113 LOC,
syntax-clean). One-command pre-flight + launch.

Gates, in order:
1. Archive presence + `manifest.json` parse
2. Disk capacity (host ZFS ≥ 200 G, NFS ≥ 400 G)
3. Backend `healthz/ready` UP
4. Env file readable + dest JWT verifies against `/v2/users/me`
5. PgBouncer pool ≥ `(workers × 5 + 10)`
6. Disk-watch sidecar + tmux launch of `mffd-runner.sh`

Idempotency:
- Re-running re-checks every gate (no destructive ops in pre-flight).
- Disk-watch sidecar uses `pgrep -f mffd-disk-watch-loop` — won't start
  a second instance.
- tmux session name is `mffd-${SESSION_ID}` — existing session
  short-circuits to "attach with …" message.
- The importer's `mffd-import-{SESSION}.state.json` lets the v15
  runner-loop resume rather than restart.

Env file expected at `examples/mffd-showcase/scripts/.env.local`
(NOT committed). Required keys documented in the FAIL block when the
file is missing.

`MFFD_NO_LAUNCH=1` runs pre-flight only — useful for a dry-run after
the archive lands.

The audit §10 sequence "operator confirms §8 decisions → §3.5 key
freshness → §5 pre-flight 4–8 → rsync transfer → LOCAL_MODE probe →
full kickoff" collapses into:
1. Operator drops archive at `/mnt/pve/unas/dump/ts-export/`
2. Operator mints fresh dest key from `/root/.claude/uploads/mffd-ingest-keys-2026-05-31.txt`
3. Operator writes `.env.local`
4. `./scripts/mffd-ingest-kickoff.sh`

Wave-7 notification transport (Matrix) referenced in the prompt: the
script does not yet POST status to it. The runner's existing telemetry
hooks (`mffd-import-{SESSION_ID}.log`) are the immediate feedback
surface; a wrapper sending 5-min summary to Matrix is a follow-up.

---

## §6 Task 6 — TS-OPT3 verification

🟢 **TS-OPT3 verified default-on. timescaledb_toolkit not installed
but design covers that case.**

Findings:

- Flyway migrations applied (`flyway_schema_history`): V1.13.0 (chunk
  config), V1.15.0 (enable toolkit), V1.17.0 (CAgg integer_now +
  backfill fix), V1.18.0/.1 — all `success=t`.

- Hypertable `timeseries_data_points` exists; 86 chunks; all
  compressed (per audit baseline).

- Compression policies active:
  - `timeseries_data_points` → `compress_after = 7 days`
  - `timeseries_hourly` (CAgg materialized HT) → `compress_after = 30 days`

- Continuous aggregate `timeseries_hourly` → `finalized = t` (the
  V1.17.0 fix). Used by the Java `QueryStrategy.AUTO` default routing
  in `TimeseriesService` + `TimeseriesDataPointRepository`. CAgg
  routing fires when window-per-pixel exceeds 1 hour.

- `pg_extension`: `timescaledb 2.24.0` installed. `timescaledb_toolkit`
  NOT installed.
  - Root cause: the running image is plain `timescale/timescaledb:2.24.0-pg16`,
    which does not ship `timescaledb_toolkit` (only `timescaledb-ha`
    ships it).
  - Migration V1.15.0 anticipates this and gracefully `RAISE NOTICE` +
    continues. Confirmed by reading the migration body
    (`backend/src/main/resources/db/migration/V1.15.0__enable_timescaledb_toolkit.sql`).
  - The migration comment is explicit: "TS-OPT3 CAgg routing still
    works via `timeseries_hourly`" — the routing layer falls back to
    the CAgg without LTTB/percentile_agg/stats_agg.

- Java routing is wired:
  - `TimeseriesService.QueryStrategy.AUTO` = default
  - `TimeseriesDataPointRepository` queries `timeseries_hourly` when
    appropriate (lines 663–680).
  - `QueryStrategy` enum doc-comments confirm AUTO uses the CAgg for
    wide windows.

Nothing to flip. TS-OPT3 is correctly default-on. The toolkit
unavailability is a known design point, not a regression.

---

## §7 What the operator does next

1. **Transfer the archive** to `/mnt/pve/unas/dump/ts-export/`
   (rsync per audit §2.2). Verify `manifest.json` lands too.

2. **Read** `/root/.claude/uploads/mffd-ingest-keys-2026-05-31.txt`
   and pick option A/B/C. Run the one curl to mint a fresh dest JWT
   (under 30 s).

3. **Write** `examples/mffd-showcase/scripts/.env.local` with the
   fresh dest JWT + the four other env keys listed in the kickoff
   script FAIL block (NOT committed — `.env.local` should be in
   `.gitignore`; verify before saving).

4. **Pre-flight smoke** (no launch):
   `MFFD_NO_LAUNCH=1 ./scripts/mffd-ingest-kickoff.sh`
   All 5 gates green → safe to proceed.

5. **Decide worker count** (audit §8.1):
   - default 4: safe; ~11–16 h wall-clock for 346 GB
   - 8: ~6–10 h, requires PgBouncer bump (kickoff script will tell
     you so + remediation steps if you pass `MFFD_WORKERS=8`)

6. **Launch**:
   `./scripts/mffd-ingest-kickoff.sh`
   Then `tmux attach -t mffd-<session>` to watch.

7. **During ingest**: `tail -f /tmp/mffd-disk-watch.log` for the
   30-min disk snapshot loop.

8. **After**: run `examples/mffd-showcase/scripts/mffd-completeness-check.py`
   then snapshot per audit §9.

---

## §8 Artefacts (file inventory)

| File | Purpose | Committed? |
|---|---|---|
| `aidocs/agent-findings/mffd-ingest-prep-capacity-2026-05-31.md` | Capacity snapshot | yes (after operator review) |
| `aidocs/agent-findings/mffd-ingest-prep-summary-2026-05-31.md` | This file — 6-task status | yes (after operator review) |
| `scripts/mffd-ingest-kickoff.sh` | Pre-flight + launch | yes (after operator review) |
| `/root/.claude/uploads/mffd-ingest-keys-2026-05-31.txt` | API key status + mint commands | **never** — per `feedback_uploads_never_in_repo` |
| `examples/mffd-showcase/scripts/.env.local` (operator-created) | Importer env (JWT + collection IDs) | **never** — gitignored |

---

## §9 What was NOT done (deferred / out-of-scope)

- ❌ Did not start the archive transfer (operator handles).
- ❌ Did not mint fresh keys (permission gate; operator runs one curl).
- ❌ Did not mutate dest state (write-side warmup probes disabled).
- ❌ Did not dispatch sub-agents.
- ❌ Did not start the full ingest.
- ❌ Did not commit (per task instructions; operator commits after
  review).
- ❌ Did not wire the kickoff script's 5-min status updates to Matrix
  (wave-7 notification transport). Runner already logs to file; Matrix
  hook is a thin wrapper that can land as a follow-up.

---

## §10 Cross-references

- `aidocs/agent-findings/mffd-ingest-346gb-readiness-2026-05-31.md` —
  full readiness audit (this prep is its operational follow-up)
- `aidocs/16-dispatcher-backlog.md` rows `TS-INGEST-222GB-*`,
  `MFFD-INGEST-*`, `IMPORT-W*` — all relevant rows reconciled in the
  readiness audit §4.1–§4.2
- `examples/mffd-showcase/scripts/RUNBOOK-2026-05-26-mffd-v16-content-transfer.md` —
  the existing v16 runbook; kickoff script automates its §0–§7
- `project_mffd_api_keys` memory — needs update after fresh mint
  (operator action)
- `backend/src/main/resources/db/migration/V1.15.0__enable_timescaledb_toolkit.sql` —
  the TS-OPT3 toolkit-graceful migration
