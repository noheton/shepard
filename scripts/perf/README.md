# shepard performance test scripts

Companion to the `monitoring` compose profile
(`docs/admin.md §Performance metrics`) and `aidocs/59`.

## Files

- `k6-smoke.js` — minimal smoke test. 5 VUs × 30 s; hits the
  hottest endpoints; asserts the casual-user latency budget
  (`aidocs/42`). **This is the only script that ships in PERF1.**
- `k6-stress.js` — heavier multi-stage stress (to be written;
  PERF2a). Ramps to 50 VUs, exercises file upload + timeseries
  ingest + search + permissions paths.
- `recommend.py` — reads `last-run.json` from k6, surfaces
  recommendations (to be written; PERF2b). See `aidocs/59 §4`.

## Quick start

```bash
# Bring up the stack + Grafana + Prometheus
docker compose --env-file .env --profile monitoring up -d

# Smoke test (5 VUs × 30 s)
docker run --rm -i --network host \
    -e SHEPARD_BASE_URL=http://localhost:8080 \
    -e SHEPARD_API_KEY=$YOUR_API_KEY \
    grafana/k6 run - < scripts/perf/k6-smoke.js
```

Exit code is non-zero if any threshold fails; suitable for CI
gates or the install-TUI auto-tuning loop (`aidocs/59 §5`).

## Why k6

- Single static binary; no JVM warmup; reproducible numbers.
- Apache-2.0 licence (passes our `aidocs/CLAUDE.md` deny-list).
- Native Prometheus remote-write — points the runner at the
  compose profile's Prometheus directly when run in `--out`
  mode (deferred; PERF2c).

## Cross-references

- `aidocs/59` — performance-testing + auto-tuning design.
- `docs/admin.md §Performance metrics` — the dashboard the
  scripts feed into.
- `aidocs/16` — PERF1, PERF2a, PERF2b, PERF2c backlog rows.
