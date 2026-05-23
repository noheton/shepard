# Runbook — BUG-E + BUG-F + BUG-G recovery (2026-05-23)

**Goal**: Deploy v15.14 to cube, wipe ~4 200 orphan SD containers, restart importer with the correct one-container-per-step model.

**Prerequisite**: importer is already stopped on cube (`pkill -f mffd-runner.sh` done).

---

## Step 1 — Hot-deploy v15.14 on cube

One curl block. Run as `cube` user from `/home/cube/mffd-export`:

```bash
cd /home/cube/mffd-export && \
curl -fL -o /tmp/mffd-import-v15.py        https://raw.githubusercontent.com/noheton/shepard/main/examples/mffd-showcase/scripts/mffd-import-v15.py && \
curl -fL -o /tmp/mffd-import-health.sh     https://raw.githubusercontent.com/noheton/shepard/main/examples/mffd-showcase/scripts/mffd-import-health.sh && \
curl -fL -o /tmp/mffd-completeness-check.py https://raw.githubusercontent.com/noheton/shepard/main/examples/mffd-showcase/scripts/mffd-completeness-check.py && \
echo "=== expected sha256:" && \
echo "fc8250c51bc5d45f740cc74b6c61c853fe993c4b4a484c5374636f2b9170e1cc  mffd-import-v15.py (v15.14, BUG-E+F+G fixes)" && \
echo "1855a752bee24184009bb23101d98ec86a02e823ef0e1a381301d53e55c51ac8  mffd-import-health.sh (v1.1)" && \
echo "a163ebd236da466d9fcc2cd798b2b63c5d467aab0c3ac0960e8db103a01ead95  mffd-completeness-check.py" && \
echo "=== actual sha256:" && \
sha256sum /tmp/mffd-import-v15.py /tmp/mffd-import-health.sh /tmp/mffd-completeness-check.py && \
grep '^IMPORT_SCRIPT_VERSION' /tmp/mffd-import-v15.py && \
cp mffd-import-v15.py mffd-import-v15.py.bug-efg.bak 2>/dev/null || true && \
cp /tmp/mffd-import-v15.py mffd-import-v15.py && \
cp /tmp/mffd-import-health.sh mffd-import-health.sh && \
chmod +x mffd-import-health.sh && \
cp /tmp/mffd-completeness-check.py mffd-completeness-check.py && \
chmod +x mffd-completeness-check.py && \
rm -f .mffd-import.lock && \
echo "v15.14 staged. NOT starting yet — run wipe + state reset (steps 2-3) before relaunch."
```

Expected stdout: `IMPORT_SCRIPT_VERSION = "15.14"` + sha256 match for `fc8250c51bc5d45f740cc74b6c61c853fe993c4b4a484c5374636f2b9170e1cc`.

---

## Step 2 — Wipe orphan SD containers on nuclide

Run from **this dev box** (not cube — needs nuclide intranet). The block below uses the nuclide dest JWT (already authenticated in memory).

### 2a. DRY-RUN — enumerate orphan candidates

```bash
JWT='eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJlZTRjMDEwZi1kNjQ4LTQ2MzAtYWVhNi1iODFlZjJhOWMyOTYiLCJpc3MiOiJodHRwOi8vc2hlcGFyZC1hcGkubnVjbGlkZS5zeXN0ZW1zLyIsIm5iZiI6MTc3OTQzMTY1MiwiaWF0IjoxNzc5NDMxNjUyLCJqdGkiOiI3NmMyMGM2MC1hMzVmLTQwMjQtODdhNi0xYjU0ZGRiMzcxZWIifQ.ZBY9YQZyje_ketIGB2za50H76XR-oYmCWy6wHdySBX3o2mhWgGCASrjjkmIyRDwlmQfM4MR-BtTUzS7Vp1XTROERu3AbiF-y-7CWmxHWvP0NVJ1Cl_EjdcXJjztnU8rjb-jTY5t1WOQeSgBszMDq8cwNY-67w4Xj5tyvQRq7i928kIHFiepfKg6mCHo6JVHMIdJyUHKri9J1GmbopdM7pdpN074BYxYzZQ8qCgMDN2MrMq37HjDwFrhhu1y7BPDJuglCXdM0jtU--L5aSZyENcMCiwZQyPf6Bf3AX7ddY2EDsNtB7xFgeJ7XtHVTs4yItHZmm0TTdb1-Q7lFQ19-Cg'
BASE='https://shepard-api.nuclide.systems'

# Fetch all SD containers; filter to today's BUG-F-induced creations + 0-reference orphans
python3 <<'PYEOF'
import urllib.request, json
JWT = '__PASTE_JWT_HERE__'  # replace with the JWT above before running
BASE = 'https://shepard-api.nuclide.systems'
req = urllib.request.Request(f'{BASE}/shepard/api/structuredDataContainers?size=20000', headers={'X-API-KEY': JWT})
rows = json.load(urllib.request.urlopen(req, timeout=60))
print(f'total SD containers in dest: {len(rows)}')
today = [r for r in rows if (r.get('createdAt') or '').startswith('2026-05-23')]
print(f'created today: {len(today)}')

# Confirm: each candidate should have NO back-reference (BUG-G means no link was ever made)
# Quick check: just count by name pattern — Execution-* containers from BUG-F
buge_pattern = [r for r in today if 'Execution' in (r.get('name') or '')]
print(f'Execution-named containers today (BUG-F victims): {len(buge_pattern)}')

# Sample 3 confirm 0 references
import random
sample = random.sample(buge_pattern, min(3, len(buge_pattern)))
for c in sample:
    req2 = urllib.request.Request(f"{BASE}/shepard/api/structuredDataContainers/{c['id']}", headers={'X-API-KEY': JWT})
    d = json.load(urllib.request.urlopen(req2, timeout=15))
    print(f"  sample id={c['id']} name={c.get('name')[:50]} payload={len(d.get('payload') or [])} refs={len(d.get('references') or [])}")

# Save the kill list
kill_list = [c['id'] for c in buge_pattern]
with open('/tmp/sd-wipe-kill-list.txt', 'w') as f:
    for i in kill_list: f.write(f'{i}\n')
print(f'kill list ({len(kill_list)} ids) → /tmp/sd-wipe-kill-list.txt')
PYEOF
```

**Operator confirms** the sample shows `payload=0 refs=0` and the kill-list count matches the expected ~4 200 (file count grows slightly because there are also some warmup-probe + RoboDK + ImportScripts SD containers from earlier that aren't BUG-F victims; the `Execution` name filter excludes those).

### 2b. EXECUTE — batch DELETE

```bash
JWT='__PASTE_JWT_HERE__'
BASE='https://shepard-api.nuclide.systems'
deleted=0
failed=0
while read -r id; do
  status=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE -H "X-API-KEY: $JWT" "$BASE/shepard/api/structuredDataContainers/$id" --max-time 10)
  case "$status" in
    20*|404) deleted=$((deleted+1));;
    *) failed=$((failed+1)); echo "FAIL id=$id status=$status" >&2;;
  esac
  if [ $((deleted % 100)) -eq 0 ]; then echo "deleted=$deleted failed=$failed"; fi
done < /tmp/sd-wipe-kill-list.txt
echo "DONE: deleted=$deleted failed=$failed"
```

Expect ~4 200 deletes in ~5 minutes (~14 DELETEs/sec).

---

## Step 3 — Reset SD-related state entries (on cube)

The state file tracks per-DO "sd already done" markers via the `sd/{step_key}/{src_do.do_id}/{sd_ref.ref_id}` key shape. Reset only those entries so the SD work re-runs; file + TS state stays so they're not redone.

```bash
cd /home/cube/mffd-export
cp mffd-import-state.json mffd-import-state.json.bak-pre-sd-reset-$(date +%Y%m%d_%H%M%S)
python3 <<'PYEOF'
import json
with open('mffd-import-state.json') as f: s = json.load(f)
# State shape per importer code: a dict with 'structured_done' set of state-keys.
# Reset only the SD-related state, preserve file + TS.
before = len(s.get('structured_done') or [])
s['structured_done'] = []
with open('mffd-import-state.json', 'w') as f: json.dump(s, f, indent=2)
print(f'reset {before} structured_done entries; file+TS state preserved')
PYEOF
```

---

## Step 4 — Restart importer (on cube)

```bash
cd /home/cube/mffd-export
tmux send-keys -t mffd './mffd-runner.sh' Enter
# OR if the tmux session is gone, recreate it (preserves the env vars setup the wrapper expects):
# see your original launch command — same envs (SHEPARD_URL, SOURCE_*, MFFD_DEFAULT_LICENSE, etc.)
```

Within ~30s you should see in tmux:

```text
PRE-FLIGHT TOTALS
  tapelaying      src_coll=48297     total=    XXX DOs
  bridgewelding   src_coll=163811    total=    YYY DOs
  GRAND TOTAL: ZZZ source DataObject(s) to migrate
```

That's v15.13's banner — confirms the new script is live. The SD POSTs should now succeed (BUG-E fix) and the dest should accumulate exactly 2 SD containers (one per step, BUG-F fix) with proper `structuredDataReferences` on each per-Execution DO (BUG-G fix).

---

## Step 5 — Watch + verify

```bash
# Watch the in-flight progress (in cube terminal):
tail -f /home/cube/mffd-export/mffd-import-*.log | grep -E 'ok-sd|error|done —|PRE-FLIGHT|GRAND TOTAL|MIGRATION COMPLETENESS|ok-file|ok-ts'
```

Concurrently, the watchdog (if you wire the systemd timer or cron) reports OK / STALL every 5 min with PID-stability + DO-completion signals.

---

## Step 6 — End-state verification (after import completes)

```bash
# The contained completeness check runs automatically at end of run_source_mode.
# Also can re-run forensically:
cd /home/cube/mffd-export
python3 mffd-completeness-check.py
# Exit 0 = PASS; 1 = PARTIAL; 2 = MISSING-DOS; 3 = CONN-FAIL
```

Then on this dev box, confirm the dest SD-container model is now correct:

```bash
JWT='__PASTE_JWT_HERE__'
curl -fsSL -H "X-API-KEY: $JWT" "https://shepard-api.nuclide.systems/shepard/api/structuredDataContainers?size=20000" \
  | python3 -c "
import sys, json
rows = json.load(sys.stdin)
today = [r for r in rows if (r.get('createdAt') or '').startswith('2026-05-23')]
print(f'SD containers today: {len(today)}  (expected: ~2 per process step after fix)')
for r in today: print(f'  id={r[\"id\"]} name={r.get(\"name\")} createdAt={r.get(\"createdAt\")}')
"
```

Expected: 2 SD containers (one per process step) created late afternoon 2026-05-23, instead of the ~4 200 from the morning's BUG-F run.

---

## Versions in this runbook

- **v15.14** — `mffd-import-v15.py` — BUG-E (payload wrap) + BUG-F (container reuse) + BUG-G (link-after-upload + oid plumbing) — sha256 `fc8250c51bc5d45f740cc74b6c61c853fe993c4b4a484c5374636f2b9170e1cc`
- **v1.1** — `mffd-import-health.sh` — log-stall + PID-stability watchdog — sha256 `1855a752bee24184009bb23101d98ec86a02e823ef0e1a381301d53e55c51ac8`
- **v1.0** — `mffd-completeness-check.py` — post-migration verifier — sha256 `a163ebd236da466d9fcc2cd798b2b63c5d467aab0c3ac0960e8db103a01ead95`

All on `main` at commit `e59cd82f` (or later if you pull again before the deploy).
