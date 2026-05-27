# RUNBOOK — MFFD v16.4 content transfer (2026-05-26)

**Task #145.** All 8514 MFFD-Dropbox DataObjects are clean skeletons (zero payload).
The gate is pre-cleared (`import_ready=2026-05-26` on dest collection 661923).
Today's default SESSION_ID (`2026-05-26`) matches — the script will skip the gate wait
and proceed directly to payload transfer.

Run **on the DLR cube** (bt-au-cube3.intra.dlr.de). All commands are copy-paste ready.

---

## §0 Pre-import reset (execute before every fresh MFFD ingest)

This step wipes all Shepard data from the dest instance (nuclide) while preserving
Keycloak users and API keys. Required because the real-data ingest assumes a clean
slate — partial state from previous synthetic/test runs will cause id-collisions and
duplicate DOs.

All commands run **on nuclide** from `/opt/shepard/infrastructure/`.

### 0.1 — Stop the application stack (preserve Keycloak)

Keycloak runs outside this compose file (managed separately). Stop only the Shepard
services:

```bash
cd /opt/shepard/infrastructure
docker compose stop backend frontend neo4j timescaledb shepard-garage
```

Do NOT stop the load balancer (caddy) or any external Keycloak service.

### 0.2 — Wipe Neo4j data volume

Neo4j data lives in a bind-mount at `/opt/shepard/neo4j/data`:

```bash
rm -rf /opt/shepard/neo4j/data/*
# Confirm empty:
ls /opt/shepard/neo4j/data/
```

### 0.3 — Wipe TimescaleDB data volume (all Flyway-managed schemas)

TimescaleDB data lives in a bind-mount at `/opt/shepard/timescaledb`:

```bash
rm -rf /opt/shepard/timescaledb/*
# Confirm empty:
ls /opt/shepard/timescaledb/
```

### 0.4 — Wipe Garage S3 data directory

Garage data lives in the compose bind-mount at `./garage-data` (i.e.
`/opt/shepard/infrastructure/garage-data`). Only needed if the files-s3 profile
was active:

```bash
rm -rf /opt/shepard/infrastructure/garage-data/*
# Confirm empty (or skip if files-s3 profile was never brought up):
ls /opt/shepard/infrastructure/garage-data/
```

### 0.5 — Restart the stack

```bash
cd /opt/shepard/infrastructure
docker compose up -d
```

Then wait for the backend to become healthy (Flyway + Neo4j migrations run on startup):

```bash
# From repo root:
make wait-for-health
# Or directly:
until curl -sf https://shepard-api.nuclide.systems/shepard/api/healthz/ready | grep -q UP; do
  echo "waiting..."; sleep 5
done
echo "backend healthy"
```

### 0.6 — Verify Flyway migrations ran clean

```bash
docker compose exec timescaledb psql -U postgres -d ${POSTGRES_DB:-shepard} \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"
```

Expected: the most recent row is V1.14.0 (channel_metadata schema) or higher, with
`success = t`. If any row shows `success = f`, stop — a failed migration means the
schema is in a partial state and the ingest will corrupt data.

### 0.7 — Re-confirm API keys are valid

After the reset, Shepard's internal user records are gone but Keycloak-issued JWTs
remain valid. The importer re-creates internal user nodes on first authenticated call.
Confirm the dest API key still authenticates:

```bash
curl -sf \
  -H "X-API-KEY: ${SHEPARD_API_KEY}" \
  https://shepard-api.nuclide.systems/v2/collections | head -c 200
```

Expected: a JSON array (possibly empty `[]`). A 401 means the JWT expired or Keycloak
lost the user — re-mint via Step 1 of this runbook.

### 0.8 — Run MFFD-TS-01 idempotency check (TimescaleDB chunk config)

V1.13.0 (1-hour chunks + 4 space partitions) runs automatically via Flyway (step 0.6).
Verify the chunk interval is correct:

```bash
docker compose exec timescaledb psql -U postgres -d ${POSTGRES_DB:-shepard} \
  -c "SELECT integer_interval FROM timescaledb_information.dimensions WHERE hypertable_name='timeseries_data_points' AND dimension_type='Time';"
```

Expected: `3600000000000` (= 1 hour in nanoseconds). If blank, the hypertable was not
created — check Flyway history for V1.13.0.

---

## Step 1 — Re-mint source JWT on cube3

The 2026-05-23 source token (jti eca5887a) is expired. Mint a fresh one:

```bash
# Replace $OLD_JWT with any still-valid cube3 JWT you have.
# If you have no valid one, log in via the browser and copy the JWT from
# Keycloak (F12 → Network → any /api request → X-API-KEY header).
curl -fsS -X POST \
  -H "X-API-KEY: $OLD_JWT" \
  -H "Content-Type: application/json" \
  -d '{"name":"mffd-importer-2026-05-26"}' \
  https://backend.bt-au-cube3.intra.dlr.de/shepard/api/users/kreb_fl/apikeys
```

The response contains `"jwt": "<NEW_TOKEN>"`. Copy that value — you will use it
as `SOURCE_SHEPARD_API_KEY` below.

---

## Step 2 — Fetch and verify v16.3

```bash
cd /tmp
curl -fsSL -o mffd-import-v15.py \
  "https://raw.githubusercontent.com/noheton/shepard/main/examples/mffd-showcase/scripts/mffd-import-v15.py"

# Verify SHA256 (must match exactly):
sha256sum mffd-import-v15.py
# expected: 67d87489ca2507c81a5141617c33900cb958f020051cd4b6e494d5bae857c77a

# Also fetch the runner:
curl -fsSL -o mffd-runner.sh \
  "https://raw.githubusercontent.com/noheton/shepard/main/examples/mffd-showcase/scripts/mffd-runner.sh"
chmod +x mffd-runner.sh
```

If SHA256 doesn't match: stop and report — do not run an unverified script.

---

## Step 3 — Set environment variables

Paste the following block into your terminal, substituting `<NEW_SOURCE_JWT>` with
the token from Step 1:

```bash
# ── DESTINATION (nuclide.systems) ──────────────────────────────────────────────
export SHEPARD_URL=https://shepard-api.nuclide.systems
export SHEPARD_API_KEY=eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJlZTRjMDEwZi1kNjQ4LTQ2MzAtYWVhNi1iODFlZjJhOWMyOTYiLCJpc3MiOiJodHRwOi8vc2hlcGFyZC1hcGkubnVjbGlkZS5zeXN0ZW1zLyIsIm5iZiI6MTc3OTQzMTY1MiwiaWF0IjoxNzc5NDMxNjUyLCJqdGkiOiI3NmMyMGM2MC1hMzVmLTQwMjQtODdhNi0xYjU0ZGRiMzcxZWIifQ.ZBY9YQZyje_ketIGB2za50H76XR-oYmCWy6wHdySBX3o2mhWgGCASrjjkmIyRDwlmQfM4MR-BtTUzS7Vp1XTROERu3AbiF-y-7CWmxHWvP0NVJ1Cl_EjdcXJjztnU8rjb-jTY5t1WOQeSgBszMDq8cwNY-67w4Xj5tyvQRq7i928kIHFiepfKg6mCHo6JVHMIdJyUHKri9J1GmbopdM7pdpN074BYxYzZQ8qCgMDN2MrMq37HjDwFrhhu1y7BPDJuglCXdM0jtU--L5aSZyENcMCiwZQyPf6Bf3AX7ddY2EDsNtB7xFgeJ7XtHVTs4yItHZmm0TTdb1-Q7lFQ19-Cg

# ── SOURCE (cube3 — DLR intranet) ─────────────────────────────────────────────
export SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de
export SOURCE_SHEPARD_API_KEY=<NEW_SOURCE_JWT>   # ← replace with Step 1 token

export SOURCE_TAPELAYING_COLL_ID=48297
export SOURCE_BRIDGEWELDING_COLL_ID=163811

# ── PREDEFINED STRUCTURED-DATA CONTAINERS (nuclide) ───────────────────────────
export MFFD_SD_CONTAINER_TAPELAYING=645000
export MFFD_SD_CONTAINER_BRIDGEWELDING=645003

# ── LICENSE / ACCESS RIGHTS ───────────────────────────────────────────────────
export MFFD_DEFAULT_LICENSE="proprietary"
export MFFD_DEFAULT_ACCESS_RIGHTS="restricted access"

# ── SESSION — pre-cleared gate value; must stay 2026-05-26 ────────────────────
export SESSION_ID=2026-05-26

# ── HIERARCHY (default=1, leave on) ──────────────────────────────────────────
export MFFD_PRESERVE_HIERARCHY=1
```

---

## Step 4 — Launch

```bash
cd /tmp
./mffd-runner.sh \
  --dest-collection 661923 \
  --source-tapelaying-coll 48297 \
  --source-bridgewelding-coll 163811
```

Expected start sequence:
1. `[gate] already cleared in a previous run — continuing`  ← gate skip confirmed
2. `[skeleton] resuming — 8514 DOs already exist, skipping skeleton phase`
3. `[payload] tapelaying: processing 8xxx DOs…`

If you see `[gate] waiting for import_ready` instead of the skip message: the
SESSION_ID env var was not exported. Re-check Step 3 and try again.

---

## Step 5 — Monitor

The runner logs to stdout. Expected runtime: several hours depending on cube3 network
throughput. You can safely detach with `tmux` / `screen`.

Stop the loop cleanly at any time:

```bash
touch /tmp/mffd-runner.stop
```

The checkpoint file (`/tmp/mffd-import-2026-05-26.state.json`) means restart = resume;
no DOs are re-created, only missing payload is filled.

---

## What to report back

When the run completes (exit 0), please send:
- The final `[summary]` line printed by the script, OR
- A `tail -50` of the log if the run ended with a non-zero exit code

This will confirm Task #145 complete and unlock the post-ingest DB audit.

---

## Background (why this is safe to run)

- All 8514 skeleton DOs already exist in MFFD-Dropbox (id=661923). The script
  detects they exist and skips the skeleton phase entirely.
- Bugs D (multi-OID parser miss), F/F2 (FileReference container reuse), and G
  (TS reference creation order) are all fixed in v16.3.
- v16.4 adds: `_refresh_session()` after reconnect (stale pool flush), workers
  default lowered 8→4 (connection-reset ceiling), upload retry loop with
  file-handle re-open (5 attempts, exp backoff 4→60s).
- The gate pre-clear was applied 2026-05-24 via:
  `PATCH /shepard/api/collections/661923 {"attributes":{"import_ready":"2026-05-26"}}`
  Confirmed HTTP 200; dest JWT verified live 2026-05-26.
