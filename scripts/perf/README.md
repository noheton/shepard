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
- `k6-endpoints.js` — per-endpoint SLO script (PERF4a). Three
  named scenarios: steady, ramp, spike. Covers all 9 SLO endpoints
  including `/v2/` paths and timeseries. See §k6-endpoints.js below.

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

## k6-endpoints.js — Per-Endpoint SLO

`scripts/perf/k6-endpoints.js` (PERF4a) is the per-endpoint SLO script.
It replaces the coarse smoke test with nine individually-tagged endpoints
and three executor shapes in a single run.

### SLO matrix

| Endpoint tag | p95 steady (ms) | p99 steady (ms) | p95 spike (ms) |
|---|---|---|---|
| `versionz` | 200 | 400 | 500 |
| `collections_list` | 500 | 1 500 | 2 000 |
| `collections_search` | 1 500 | 4 000 | 6 000 |
| `v1_do_list` | 500 | 1 200 | 2 000 |
| `v2_do_list` | 500 | 1 200 | 2 000 |
| `ts_ingest` | 2 000 | 5 000 | 8 000 |
| `ts_range_scan` | 600 | 1 500 | 3 000 |
| `prov_walk` | 800 | 2 000 | 4 000 |
| `admin_features` | 200 | 500 | 800 |

**Threshold interpretation:**
- `p95 steady` — CI gate: build fails if exceeded.
- `p99 steady` — first-alarm: logged in `last-run.json` for
  `recommend.py` but does **not** fail the build.
- `p95 spike` — first-alarm: same policy as p99 steady.

### Quick start

```bash
# Full run — all three scenarios (~10 min)
API_KEY=<instance-admin-key> k6 run scripts/perf/k6-endpoints.js

# Steady-state only — 10 VUs × 2 min (~2 min)
K6_SCENARIO=steady API_KEY=<key> k6 run scripts/perf/k6-endpoints.js

# Steady + ramp only — skip the spike scenario
K6_SCENARIO=steady,ramp API_KEY=<key> k6 run scripts/perf/k6-endpoints.js

# Docker variant
docker run --rm -i --network host \
    -e BASE_URL=http://localhost:8080 \
    -e API_KEY=$YOUR_KEY \
    -v "$PWD/scripts/perf":/scripts:ro \
    grafana/k6 run /scripts/k6-endpoints.js
```

### Environment variables

| Variable | Default | Required | Description |
|---|---|---|---|
| `BASE_URL` | `http://localhost:8080` | No | Target server URL |
| `API_KEY` | — | **Yes** | Instance-admin API key; script aborts if missing |
| `K6_SCENARIO` | `steady,ramp,spike` | No | Comma-separated scenario filter |

### How it works

`setup()` creates throwaway fixtures (collection `PERF4-<ts>`, one
DataObject, one TimeseriesContainer with one channel, 500 seed rows)
and returns fixture IDs to each VU. `teardown()` deletes the collection.
Each VU call picks an endpoint at random (probability-weighted) and
records the latency in both a named `Trend` metric and the standard
`http_req_duration` with `endpoint:` and `scenario:` tags.

`handleSummary()` writes `scripts/perf/last-run.json` — the same path
consumed by `recommend.py`.

### Note on PERF4d

This script is the basis for the nightly CI job `perf-endpoints.yml`
(PERF4d — not yet created). The full workflow design is in
`aidocs/ops/77-k6-performance-metrics.md §9.2`.

## Grafana integration (PERF4c)

`k6-endpoints.js` can stream metrics to Prometheus or Grafana Cloud via
`--out` flags. No changes to the script itself are required.

### Option A: Local Prometheus remote-write

When the `monitoring` compose profile is active, Prometheus is already
running at `http://localhost:9090`. Pipe k6 metrics there directly:

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  k6 run --out experimental-prometheus-rw scripts/perf/k6-endpoints.js
```

Or with Docker (compose-internal Prometheus name):

```bash
docker run --rm -i --network host \
    -e SHEPARD_BASE_URL=http://localhost:8080 \
    -e SHEPARD_API_KEY="${SHEPARD_API_KEY}" \
    -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
    -v "$PWD/scripts/perf":/scripts:ro \
    grafana/k6 run \
      --out experimental-prometheus-rw \
      --tag testid="perf4c-$(date +%Y%m%d)" \
      /scripts/k6-endpoints.js
```

**Requirements:**
- k6 >= 0.43 (the `experimental-prometheus-rw` output ships in that release)
- Prometheus >= 2.39 with remote-write receiver enabled:
  `--enable-feature=remote-write-receiver`

**Env var:** `K6_PROMETHEUS_RW_SERVER_URL` — set to your Prometheus
remote-write endpoint (default Prometheus listens at `:9090`, so the
path is `http://localhost:9090/api/v1/write`). When running k6 inside
the compose network, use the service name instead:
`http://prometheus:9090/api/v1/write`.

### Option B: Grafana Cloud k6

Operators with a Grafana Cloud account can pipe results to the hosted k6
Cloud for multi-run trend charts and team sharing. Zero code changes
required — only the invocation flag changes:

```bash
K6_CLOUD_TOKEN=<token> k6 cloud scripts/perf/k6-endpoints.js
```

Or with Docker:

```bash
docker run --rm -i --network host \
    -e K6_CLOUD_TOKEN="${K6_CLOUD_TOKEN}" \
    -e SHEPARD_BASE_URL="${SHEPARD_BASE_URL}" \
    -e SHEPARD_API_KEY="${SHEPARD_API_KEY}" \
    grafana/k6 cloud /scripts/k6-endpoints.js
```

**Obtain token:** Grafana Cloud → Testing → k6 → Settings → API Token.
Results appear in the Grafana Cloud k6 UI automatically (no local
Prometheus needed). The `k6 cloud` subcommand streams results to
`app.k6.io` and provides regression comparison, alert rules, and CI
badge output. This is entirely optional and requires no changes to this
fork's CI or compose stack.

**Licence note.** k6 (the binary) is Apache-2.0. Grafana Cloud k6 is a
hosted service with a paid tier. There is no licence incompatibility for
operators who choose to use it; the binary itself remains Apache-2.0.

### Viewing dashboards locally

Import `infrastructure/grafana/dashboards/shepard-perf4-endpoints.json`
into your local Grafana instance:

1. Dashboards → Import → Upload JSON file
2. Select `infrastructure/grafana/dashboards/shepard-perf4-endpoints.json`
3. Set the Prometheus datasource when prompted

The dashboard contains four panels in a 2×2 grid:

| Panel | Metric | Unit |
|---|---|---|
| TimescaleDB range-scan p95 (k6) | `k6_http_req_duration_bucket` filtered on `.*timeseries.*` | ms |
| Neo4j provenance walk p95 (k6) | `k6_http_req_duration_bucket` filtered on `.*provenance.*\|.*lineage.*` | ms |
| /v2/ data-objects p95 (k6) | `k6_http_req_duration_bucket` filtered on `.*/v2/.*data.?objects.*` | ms |
| Endpoint error rate (k6) | `k6_http_reqs_total` 4xx+5xx / total | percent (0–100%) |

The error-rate panel has a red threshold line at 1% (0.01) matching the
`"http_req_failed": ["rate<0.01"]` CI gate in the k6 script.

## Cross-references

- `aidocs/ops/77` — per-endpoint SLO matrix design + PERF4 task breakdown.
- `aidocs/59` — performance-testing + auto-tuning design.
- `docs/admin.md §Performance metrics` — the dashboard the
  scripts feed into.
- `aidocs/16` — PERF1–PERF4 backlog rows.
