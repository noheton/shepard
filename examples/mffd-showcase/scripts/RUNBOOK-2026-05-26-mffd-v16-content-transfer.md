# RUNBOOK — MFFD v16.3 content transfer (2026-05-26)

**Task #145.** All 8514 MFFD-Dropbox DataObjects are clean skeletons (zero payload).
The gate is pre-cleared (`import_ready=2026-05-26` on dest collection 661923).
Today's default SESSION_ID (`2026-05-26`) matches — the script will skip the gate wait
and proceed directly to payload transfer.

Run **on the DLR cube** (bt-au-cube3.intra.dlr.de). All commands are copy-paste ready.

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
# expected: 930f1457828b69505bfa39d98dc3f79b9718fac6ca3a9016f2fff1a202168cf8

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
- The gate pre-clear was applied 2026-05-24 via:
  `PATCH /shepard/api/collections/661923 {"attributes":{"import_ready":"2026-05-26"}}`
  Confirmed HTTP 200; dest JWT verified live 2026-05-26.
