# k6 Per-Endpoint SLO Matrix + `/v2/` Coverage

**Scope.** Define per-endpoint SLO targets, concrete k6 script extensions
that exercise the `/v2/` API shelf and the timeseries/provenance data paths,
and a Grafana Cloud output option. This document builds directly on
`aidocs/ops/59-performance-testing-and-tuning.md` — it does **not** re-argue
the tool choice or the three base scripts (`k6-smoke.js`, `k6-stress.js`,
`k6-soak.js`). Read `59` first.

**Status.** Partly shipped. The PERF1–PERF3 foundation (`scripts/perf/k6-smoke.js`,
`k6-stress.js`, `k6-soak.js`, `recommend.py`, `perf-smoke.yml`) is live on
`main`. This document designs the SLO matrix for named endpoints (PERF4a),
a `/v2/` scenario script (PERF4b), Grafana Cloud remote-write output (PERF4c),
and a nightly extended-run CI job (PERF4d). None of PERF4 is yet
implemented.

**Snapshot date.** 2026-05-19.

**Related docs.**
- `aidocs/ops/59-performance-testing-and-tuning.md` — toolchain design,
  recommender, install-TUI; PERF1–PERF3 phasing
- `aidocs/ops/75-api-integration-test-suite.md` — assertion-level test suite
  (complementary; different failure mode from this)
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — feature matrix row PF-k6

---

## §1. Goal

The existing smoke script (`k6-smoke.js`) catches gross regressions on three
coarse metrics: `/versionz`, `/shepard/api/collections`, and
`/shepard/api/collections/search`. That is enough to detect a dead backend but
not enough to catch:

1. **TimescaleDB range-scan regression** — a slow index rebuild or chunk
   over-merge can push a 200-row time window query from 40 ms to 4 s without
   the generic collections endpoint noticing.
2. **Neo4j graph-walk regression** — a missing relationship index can
   multiply provenance traversal time by 10× while list endpoints stay fast
   (they use the page-cache hot path).
3. **`/v2/` endpoint drift** — new fork-specific endpoints (`/v2/provenance`,
   `/v2/collections/{id}/data-objects`, admin REST) have no perf baseline at
   all; a bad query in a PR merges silently.
4. **Timeseries ingest batching** — CSV bulk-insert throughput degrades when
   TimescaleDB chunk intervals drift or when the ingest endpoint switches from
   COPY to single-row INSERT (regression we have hit before in testing).
5. **Spike resilience** — a sudden burst of 100 VUs (e.g. from a group of
   researchers loading the same collection simultaneously) must shed load
   gracefully, not deadlock the Neo4j session pool.

This document defines the per-endpoint SLO targets, the additional k6 scripts
and scenario variants, the Grafana Cloud option, and the nightly CI job that
locks these SLOs into the release gate.

---

## §2. Tool choice — extending k6

`59 §2` justifies k6. There is no reason to revisit that choice for this
extension. One addition: k6's **named scenario executors** (available since
k6 0.37) let a single script file run three distinct executor shapes
(`constant-vus`, `ramping-vus`, `constant-arrival-rate`) in one run, each
tagged separately in the metric stream. PERF4 uses this to keep the per-endpoint
SLO matrix in one script rather than three.

**Auth header note.** The existing `k6-smoke.js` and `k6-stress.js` use
`X-API-KEY` as the auth header. The backend's `UserinfoService` currently
supports both `apikey` and `X-API-KEY` (case-insensitive; the Caddy proxy
strips and rewrites headers). New scripts in PERF4 will use `apikey` to match
the integration test suite (`75`) and the seeder's convention.

---

## §3. Per-endpoint SLO matrix

The following table is the **authoritative latency budget** for each named
endpoint. Thresholds are set at steady-state (10–25 VUs); the spike column is
the first-alarm threshold for a 100 VU burst. Exceed either column and the CI
job files a GitHub issue (same mechanism as PERF3).

| Endpoint | Method | SLO p95 (steady) | SLO p99 (steady) | SLO p95 (spike) | Notes |
|---|---|---|---|---|---|
| `GET /versionz` | GET | 200 ms | 400 ms | 500 ms | Static; 59 already tracks this |
| `GET /shepard/api/collections` | GET | 500 ms | 1 500 ms | 2 000 ms | Neo4j page-cache hot path |
| `POST /shepard/api/collections/search` | POST | 1 500 ms | 4 000 ms | 6 000 ms | Cypher full-text; 59 tracks this |
| `GET /shepard/api/collections/{id}/dataobjects` | GET | 500 ms | 1 200 ms | 2 000 ms | |
| `GET /v2/collections/{appId}/data-objects` | GET | 500 ms | 1 200 ms | 2 000 ms | `/v2/` equivalent; appId-indexed |
| `POST /shepard/api/timeseriescontainers/{id}/timeseries/{tsId}/datapoints/file` | POST (CSV ingest) | 2 000 ms | 5 000 ms | 8 000 ms | TimescaleDB COPY; 10-row batch |
| `GET /shepard/api/timeseriescontainers/{id}/timeseries/{tsId}/datapoints` | GET (range scan) | 600 ms | 1 500 ms | 3 000 ms | 200-row window; TimescaleDB index path |
| `GET /v2/provenance/entity/{appId}` | GET | 800 ms | 2 000 ms | 4 000 ms | Neo4j graph walk; 3-hop PROV-O chain |
| `GET /v2/admin/features` | GET | 200 ms | 500 ms | 800 ms | In-memory registry lookup |
| `POST /shepard/api/filecontainers/{id}/files` | POST (1 KB) | 800 ms | 2 000 ms | 4 000 ms | Object-store write; 59 tracks this |

The p95/p99 split matters: p95 is the SLO that gates CI; p99 is the first-alarm
threshold that triggers a recommender suggestion but does not fail the build.
This avoids tail-latency spikes from JVM GC pauses failing the gate when the
median is healthy.

---

## §4. Test scenarios

PERF4a ships one new script: `scripts/perf/k6-endpoints.js`. It runs all three
scenario shapes in a single execution using k6's named-scenario executor API.

### 4.1 Steady-state (10 VUs × 2 min)

Baseline. Establishes the "healthy-stack floor" for all SLO columns above.

```javascript
// scripts/perf/k6-endpoints.js — excerpt: options block
//
// K6_SCENARIO env var selects which scenarios to activate (comma-separated).
// Valid values: "steady", "ramp", "spike" (default: all three).
//   K6_SCENARIO=steady k6 run scripts/perf/k6-endpoints.js   # ~2 min
//   K6_SCENARIO=steady,ramp k6 run ...                        # ~8 min
// k6 does not have a --scenario CLI flag; the env-var approach is the standard
// pattern for selective scenario activation.

const ACTIVE_SCENARIOS = ((__ENV.K6_SCENARIO || "steady,ramp,spike")
  .split(",")
  .map(s => s.trim()));

const allScenarios = {
  steady: {
    executor: "constant-vus",
    vus: 10,
    duration: "2m",
    tags: { scenario: "steady" },
  },
  ramp: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "2m", target: 50 },
      { duration: "3m", target: 50 },
      { duration: "1m", target: 0  },
    ],
    startTime: "2m30s",         // starts after steady-state cools
    tags: { scenario: "ramp" },
  },
  spike: {
    executor: "constant-arrival-rate",
    rate: 100,
    timeUnit: "1s",
    duration: "30s",
    preAllocatedVUs: 100,
    maxVUs: 150,
    startTime: "9m",            // starts after ramp cool-down
    tags: { scenario: "spike" },
  },
};

// Build the active scenarios object from the env-var selection.
const scenarios = Object.fromEntries(
  Object.entries(allScenarios).filter(([name]) => ACTIVE_SCENARIOS.includes(name))
);

export const options = {
  scenarios,

  thresholds: {
    // ── Steady-state SLOs (p95 gates CI) ─────────────────────────────────────
    "http_req_duration{endpoint:collections_list,scenario:steady}":
      ["p(95)<500", "p(99)<1500"],
    "http_req_duration{endpoint:collections_search,scenario:steady}":
      ["p(95)<1500", "p(99)<4000"],
    "http_req_duration{endpoint:v2_data_objects,scenario:steady}":
      ["p(95)<500", "p(99)<1200"],
    "http_req_duration{endpoint:ts_ingest,scenario:steady}":
      ["p(95)<2000", "p(99)<5000"],
    "http_req_duration{endpoint:ts_range_scan,scenario:steady}":
      ["p(95)<600", "p(99)<1500"],
    "http_req_duration{endpoint:provenance_walk,scenario:steady}":
      ["p(95)<800", "p(99)<2000"],
    "http_req_duration{endpoint:admin_features,scenario:steady}":
      ["p(95)<200", "p(99)<500"],
    // ── Spike SLOs (p95 first-alarm; does not fail CI gate) ──────────────────
    "http_req_duration{endpoint:collections_list,scenario:spike}":
      ["p(95)<2000"],
    "http_req_duration{endpoint:ts_range_scan,scenario:spike}":
      ["p(95)<3000"],
    "http_req_duration{endpoint:provenance_walk,scenario:spike}":
      ["p(95)<4000"],
    // ── Global error rate ─────────────────────────────────────────────────────
    "http_req_failed": ["rate<0.01"],
  },
};
```

### 4.2 Ramp (0 → 50 VUs over 5 min)

Detects thread-pool saturation, Neo4j session-pool exhaustion, and
permissions-cache cold-start degradation under increasing concurrency.
The ramp starts 30 s after the steady-state scenario ends so the two
don't race each other for the TimescaleDB write-ahead log.

### 4.3 Spike (100 VU burst for 30 s)

Uses `constant-arrival-rate` executor so VU demand is fixed regardless of
response time (unlike `constant-vus` which throttles effective RPS when p95
grows). A healthy stack must respond 200 within `p(95) < 2 000 ms` on the
cheap reads and must not return 5xx on any path (5xx = service degradation,
not just slowdown).

---

## §5. Full script: `scripts/perf/k6-endpoints.js`

```javascript
// k6 per-endpoint SLO script for shepard.
//
// Design: aidocs/ops/77-k6-performance-metrics.md
// Extends:  aidocs/ops/59-performance-testing-and-tuning.md
//
// Exercises /shepard/api/ + /v2/ endpoints under three executor shapes:
//   1. Steady (10 VUs × 2 min)
//   2. Ramp   (0 → 50 VUs over 5 min, hold 3 min)
//   3. Spike  (100 VU/s constant-arrival-rate, 30 s)
//
// Run:
//   docker run --rm -i --network host \
//       -e SHEPARD_BASE_URL=http://localhost:8080 \
//       -e SHEPARD_API_KEY=<key> \
//       -v "$PWD/scripts/perf":/scripts:ro \
//       grafana/k6 run /scripts/k6-endpoints.js
//
// Environment:
//   SHEPARD_BASE_URL   — defaults to http://localhost:8080
//   SHEPARD_API_KEY    — API key (apikey header)
//   SHEPARD_COLL_APPID — appId of a seeded Collection for /v2/ reads
//                        (optional; falls back to /shepard/api/ path)
//   SHEPARD_TS_COLL_ID — numeric id of the timeseries container
//   SHEPARD_TS_ID      — numeric id of the timeseries inside the container
//   SHEPARD_DO_APPID   — appId of any DataObject for provenance walk
//   K6_SCENARIO        — comma-separated scenario filter: "steady", "ramp",
//                        "spike" (default: all three). k6 has no --scenario
//                        CLI flag; this env var is the idiomatic alternative.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import { randomIntBetween } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const BASE      = __ENV.SHEPARD_BASE_URL    || "http://localhost:8080";
const KEY       = __ENV.SHEPARD_API_KEY     || "";
const COLL_APP  = __ENV.SHEPARD_COLL_APPID  || "";
const TS_COLL   = __ENV.SHEPARD_TS_COLL_ID  || "";
const TS_ID     = __ENV.SHEPARD_TS_ID       || "";
const DO_APP    = __ENV.SHEPARD_DO_APPID    || "";

// Auth headers — use 'apikey' header (matches integration-test suite + seeder)
const authHeaders = KEY
  ? { apikey: KEY, Accept: "application/json" }
  : { Accept: "application/json" };

const jsonHeaders = { ...authHeaders, "Content-Type": "application/json" };

// ── Named endpoint Trends (true = track as time series, not histogram) ────────

const collectionsListT = new Trend("ep_collections_list", true);
const searchT          = new Trend("ep_search",           true);
const v2DataObjectsT   = new Trend("ep_v2_data_objects",  true);
const tsIngestT        = new Trend("ep_ts_ingest",        true);
const tsRangeScanT     = new Trend("ep_ts_range_scan",    true);
const provenanceWalkT  = new Trend("ep_provenance_walk",  true);
const adminFeaturesT   = new Trend("ep_admin_features",   true);
const errorRate        = new Rate("endpoint_error_rate");

// ── Scenario options (see §4 for narrative) ───────────────────────────────────
//
// Use K6_SCENARIO env var to run only specific scenarios (comma-separated):
//   K6_SCENARIO=steady k6 run k6-endpoints.js   → 2 min quick check
// k6 has no --scenario CLI flag; env-var selection is the idiomatic pattern.

const ACTIVE = ((__ENV.K6_SCENARIO || "steady,ramp,spike").split(",").map(s => s.trim()));

const allScenarios = {
  steady: {
    executor: "constant-vus",
    vus: 10,
    duration: "2m",
    tags: { scenario: "steady" },
  },
  ramp: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "2m", target: 50 },
      { duration: "3m", target: 50 },
      { duration: "1m", target: 0  },
    ],
    startTime: "2m30s",
    tags: { scenario: "ramp" },
  },
  spike: {
    executor: "constant-arrival-rate",
    rate: 100,
    timeUnit: "1s",
    duration: "30s",
    preAllocatedVUs: 100,
    maxVUs: 150,
    startTime: "9m",
    tags: { scenario: "spike" },
  },
};

export const options = {
  scenarios: Object.fromEntries(
    Object.entries(allScenarios).filter(([name]) => ACTIVE.includes(name))
  ),

  thresholds: {
    // Steady-state SLOs — CI gate
    "http_req_duration{endpoint:collections_list,scenario:steady}":
      ["p(95)<500",  "p(99)<1500"],
    "http_req_duration{endpoint:collections_search,scenario:steady}":
      ["p(95)<1500", "p(99)<4000"],
    "http_req_duration{endpoint:v2_data_objects,scenario:steady}":
      ["p(95)<500",  "p(99)<1200"],
    "http_req_duration{endpoint:ts_ingest,scenario:steady}":
      ["p(95)<2000", "p(99)<5000"],
    "http_req_duration{endpoint:ts_range_scan,scenario:steady}":
      ["p(95)<600",  "p(99)<1500"],
    "http_req_duration{endpoint:provenance_walk,scenario:steady}":
      ["p(95)<800",  "p(99)<2000"],
    "http_req_duration{endpoint:admin_features,scenario:steady}":
      ["p(95)<200",  "p(99)<500"],
    // Spike SLOs — first-alarm only (p95; does not fail CI gate)
    "http_req_duration{endpoint:collections_list,scenario:spike}":
      ["p(95)<2000"],
    "http_req_duration{endpoint:ts_range_scan,scenario:spike}":
      ["p(95)<3000"],
    "http_req_duration{endpoint:provenance_walk,scenario:spike}":
      ["p(95)<4000"],
    // Global error cap — any breach fails CI
    "http_req_failed":    ["rate<0.01"],
    "endpoint_error_rate": ["rate<0.01"],
  },
};

// ── Shared helper: build an ISO-8601 window for timeseries range scans ────────

function tsWindow(lookbackSeconds) {
  const end   = new Date();
  const start = new Date(end.getTime() - lookbackSeconds * 1000);
  return { start: start.toISOString(), end: end.toISOString() };
}

// ── Workload mix: each VU picks a path by probability ─────────────────────────
//   35% cheap reads (collections list + /v2/ data-objects)
//   20% search
//   15% timeseries range scan
//   15% provenance walk
//   10% timeseries ingest
//    5% admin-features read

export default function () {
  const roll = Math.random();

  if (roll < 0.35) {
    doCollectionsAndDataObjects();
  } else if (roll < 0.55) {
    doSearch();
  } else if (roll < 0.70) {
    doTsRangeScan();
  } else if (roll < 0.85) {
    doProvenanceWalk();
  } else if (roll < 0.95) {
    doTsIngest();
  } else {
    doAdminFeatures();
  }

  sleep(randomIntBetween(1, 2));
}

// ─── Scenario implementations ─────────────────────────────────────────────────

function doCollectionsAndDataObjects() {
  group("collections-and-data-objects", () => {
    // 1. Collections list (v1 compat surface)
    {
      const r = http.get(`${BASE}/shepard/api/collections?page=0&pageSize=20`, {
        headers: authHeaders,
        tags: { endpoint: "collections_list" },
      });
      collectionsListT.add(r.timings.duration);
      const ok = check(r, { "collections 200": (x) => x.status === 200 });
      errorRate.add(ok ? 0 : 1);
    }

    // 2. /v2/ data-objects — appId-indexed (fork-specific surface)
    if (COLL_APP) {
      const r = http.get(`${BASE}/v2/collections/${COLL_APP}/data-objects`, {
        headers: authHeaders,
        tags: { endpoint: "v2_data_objects" },
      });
      v2DataObjectsT.add(r.timings.duration);
      const ok = check(r, { "v2 data-objects 200": (x) => x.status === 200 });
      errorRate.add(ok ? 0 : 1);
    }
  });
}

function doSearch() {
  group("search", () => {
    const r = http.post(
      `${BASE}/shepard/api/collections/search`,
      JSON.stringify({ kind: "Collection", filters: [] }),
      { headers: jsonHeaders, tags: { endpoint: "collections_search" } },
    );
    check(r, { "search 200": (x) => x.status === 200 || x.status === 400 });
    errorRate.add(r.status >= 500 ? 1 : 0);
  });
}

function doTsRangeScan() {
  if (!TS_COLL || !TS_ID) return;
  group("ts-range-scan", () => {
    // 200-row time window (±5 min around now)
    const { start, end } = tsWindow(300);
    const r = http.get(
      `${BASE}/shepard/api/timeseriescontainers/${TS_COLL}` +
      `/timeseries/${TS_ID}/datapoints?start=${encodeURIComponent(start)}` +
      `&end=${encodeURIComponent(end)}&pageSize=200`,
      { headers: authHeaders, tags: { endpoint: "ts_range_scan" } },
    );
    tsRangeScanT.add(r.timings.duration);
    const ok = check(r, { "ts scan 200": (x) => x.status === 200 });
    errorRate.add(ok ? 0 : 1);
  });
}

function doProvenanceWalk() {
  if (!DO_APP) return;
  group("provenance-walk", () => {
    // /v2/provenance/entity/{appId} — Neo4j 3-hop PROV-O chain
    const r = http.get(`${BASE}/v2/provenance/entity/${DO_APP}`, {
      headers: authHeaders,
      tags: { endpoint: "provenance_walk" },
    });
    provenanceWalkT.add(r.timings.duration);
    // 200 = entity has provenance; 404 = valid but no activities yet
    const ok = check(r, {
      "provenance 200/404": (x) => x.status === 200 || x.status === 404,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

function doTsIngest() {
  if (!TS_COLL || !TS_ID) return;
  group("ts-ingest", () => {
    const now = Date.now();
    const rows = Array.from({ length: 50 }, (_, i) => {
      const ts = new Date(now - (49 - i) * 1000).toISOString();
      return `${ts},${(Math.random() * 100).toFixed(4)}`;
    });
    const csv = "time,value_double\n" + rows.join("\n");

    const r = http.post(
      `${BASE}/shepard/api/timeseriescontainers/${TS_COLL}` +
      `/timeseries/${TS_ID}/datapoints/file`,
      csv,
      {
        headers: KEY
          ? { apikey: KEY, "Content-Type": "text/csv" }
          : { "Content-Type": "text/csv" },
        tags: { endpoint: "ts_ingest" },
      },
    );
    tsIngestT.add(r.timings.duration);
    const ok = check(r, {
      "ts ingest 2xx": (x) =>
        x.status === 200 || x.status === 201 || x.status === 204,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

function doAdminFeatures() {
  group("admin-features", () => {
    const r = http.get(`${BASE}/v2/admin/features`, {
      headers: authHeaders,
      tags: { endpoint: "admin_features" },
    });
    adminFeaturesT.add(r.timings.duration);
    // 401/403 = valid (auth boundary); 200 = instance-admin key
    const ok = check(r, {
      "admin features 200/401/403": (x) =>
        x.status === 200 || x.status === 401 || x.status === 403,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// ── Summary → last-run.json (feeds recommend.py) ─────────────────────────────

export function handleSummary(data) {
  return {
    "stdout": "",
    "scripts/perf/last-run.json": JSON.stringify(data, null, 2),
  };
}
```

---

## §6. Auth strategy

All new scripts use the `apikey` request header. This matches:
- The integration test suite (`e2e/api/`) — `headers={"apikey": api_key}`
- The seeder's `entrypoint.sh` — passes the key in the same header
- The Caddy proxy rewrite (`infrastructure/proxy/Caddyfile`)

The CI job reads `SHEPARD_API_KEY` from the same Actions secret as PERF3
(`secrets.SHEPARD_PERF_API_KEY`). No new secrets are needed.

The existing `k6-smoke.js` and `k6-stress.js` use `X-API-KEY`. Leave those
unchanged to avoid drift with the `recommend.py` baseline. PERF4a scripts
use `apikey`; a follow-up cleanup (PERF4e) can unify after the next major
release.

---

## §7. Directory layout

New scripts land alongside the existing `scripts/perf/` suite. No new
directory is introduced.

```
scripts/perf/
├── k6-smoke.js            # PERF1 — shipped
├── k6-stress.js           # PERF2a — shipped
├── k6-soak.js             # PERF2c — shipped
├── recommend.py           # PERF2b — shipped
├── last-run.json          # auto-written by handleSummary
├── k6-endpoints.js        # PERF4a — this design (§5)
└── README.md              # update to mention k6-endpoints.js + PERF4
```

The `e2e/perf/` path mentioned in some design discussions is not used here —
`scripts/perf/` is the established convention; changing it would break the
`perf-smoke.yml` volume mount without any functional benefit.

---

## §8. Grafana Cloud integration (PERF4c)

k6 supports two output targets that require no changes to the script itself;
only the `--out` flag at invocation time changes.

### 8.1 Local Prometheus remote-write (no account required)

When the `monitoring` compose profile is active, Prometheus is already running
at `http://localhost:9090`. k6 0.37+ can write metrics there directly:

```bash
docker run --rm -i --network host \
    -e SHEPARD_BASE_URL=http://localhost:8080 \
    -e SHEPARD_API_KEY="${SHEPARD_API_KEY}" \
    -v "$PWD/scripts/perf":/scripts:ro \
    grafana/k6 run \
      --out experimental-prometheus-rw \
      --tag testid="perf4a-$(date +%Y%m%d)" \
      /scripts/k6-endpoints.js
```

The environment variable `K6_PROMETHEUS_RW_SERVER_URL` must point to
`http://localhost:9090/api/v1/write` (or the compose-internal name
`http://prometheus:9090/api/v1/write` when running inside compose).

The existing PERF1 Grafana dashboard (`docs/admin.md §Performance metrics`)
can be extended with a panel group "PERF4 endpoint SLOs" that queries the
`ep_*` Trend metrics by `testid` label, overlaying multiple runs for
regression comparison.

**Required Grafana dashboard panel additions:**

| Panel | PromQL sketch |
|---|---|
| TimescaleDB range-scan p95 | `histogram_quantile(0.95, rate(k6_ep_ts_range_scan_bucket[5m]))` |
| Neo4j provenance walk p95 | `histogram_quantile(0.95, rate(k6_ep_provenance_walk_bucket[5m]))` |
| `/v2/` data-objects p95 | `histogram_quantile(0.95, rate(k6_ep_v2_data_objects_bucket[5m]))` |
| Endpoint error rate | `rate(k6_endpoint_error_rate_total[5m])` |

### 8.2 Grafana Cloud k6 (paid-tier option, operator-opt-in)

Operators who have a Grafana Cloud account can pipe results to the hosted k6
Cloud for multi-run trend charts and team sharing. This requires zero code
changes — only the invocation flag changes:

```bash
K6_CLOUD_TOKEN=<grafana-cloud-token>  \
k6 cloud scripts/perf/k6-endpoints.js
```

or with the Docker image:

```bash
docker run --rm -i --network host \
    -e K6_CLOUD_TOKEN="${K6_CLOUD_TOKEN}" \
    -e SHEPARD_BASE_URL="${SHEPARD_BASE_URL}" \
    -e SHEPARD_API_KEY="${SHEPARD_API_KEY}" \
    grafana/k6 cloud /scripts/k6-endpoints.js
```

The `k6 cloud` subcommand streams results to `app.k6.io` and provides
regression comparison, alert rules, and CI badge output. This is entirely
optional and requires no changes to this fork's CI or compose stack.

**Licence note.** k6 (the binary) is Apache-2.0. Grafana Cloud k6 is a
hosted service with a paid tier. There is no licence incompatibility for
operators who choose to use it; the binary itself remains Apache-2.0 either
way.

---

## §9. CI integration

### 9.1 Existing PERF3 weekly smoke

`.github/workflows/perf-smoke.yml` already runs `k6-smoke.js` weekly on
Sundays (02:17 UTC) and files a GitHub issue on threshold breach. No changes
to this workflow are needed for PERF4a.

### 9.2 PERF4d — nightly extended-run job

A separate workflow `.github/workflows/perf-endpoints.yml` runs the new
`k6-endpoints.js` script nightly (02:37 UTC, staggered from PERF3). It boots
the full compose stack, seeds a minimal dataset, and runs all three executor
scenarios. Failing steady-state SLOs fail the job; spike SLO breaches are
logged as annotations but do not fail the job (first-alarm only).

```yaml
# .github/workflows/perf-endpoints.yml  (PERF4d — design; not yet created)
name: Nightly endpoint SLO check

on:
  schedule:
    - cron: "37 2 * * *"           # 02:37 UTC daily
  workflow_dispatch:
    inputs:
      post_issue:
        description: "File issue on breach?"
        required: false
        default: "true"
        type: boolean

permissions:
  contents: read
  issues: write

concurrency:
  group: perf-endpoints
  cancel-in-progress: true

jobs:
  endpoints:
    name: k6 per-endpoint SLOs
    runs-on: ubuntu-latest
    timeout-minutes: 20

    env:
      SHEPARD_BASE_URL: http://localhost:8080
      SHEPARD_API_KEY: ${{ secrets.SHEPARD_PERF_API_KEY || 'ci-perf-key' }}

    steps:
      - uses: actions/checkout@v4

      - name: Start compose stack
        run: |
          docker compose \
            -f infrastructure/docker-compose.yml \
            up -d --wait --wait-timeout 120
        env:
          SHEPARD_INITIAL_API_KEY: ${{ env.SHEPARD_API_KEY }}

      - name: Wait for backend
        run: |
          for i in $(seq 1 30); do
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
              http://localhost:8080/versionz || echo 000)
            [ "$STATUS" = "200" ] && { echo "Ready"; exit 0; }
            echo "Attempt $i: $STATUS — waiting 5s"; sleep 5
          done
          exit 1

      # Seed minimal perf fixtures (reuses existing seed infrastructure)
      - name: Seed perf fixtures
        run: |
          # Create one collection + timeseries container with data via API key
          COLL=$(curl -sf -X POST http://localhost:8080/shepard/api/collections \
            -H "apikey: ${SHEPARD_API_KEY}" \
            -H "Content-Type: application/json" \
            -d '{"name":"perf-fixtures"}' | jq -r '.appId // .id')
          echo "SHEPARD_COLL_APPID=${COLL}" >> "$GITHUB_ENV"

          TC=$(curl -sf -X POST http://localhost:8080/shepard/api/timeseriescontainers \
            -H "apikey: ${SHEPARD_API_KEY}" \
            -H "Content-Type: application/json" \
            -d '{"name":"perf-ts"}' | jq -r '.id')
          TS=$(curl -sf -X POST \
            "http://localhost:8080/shepard/api/timeseriescontainers/${TC}/timeseries" \
            -H "apikey: ${SHEPARD_API_KEY}" \
            -H "Content-Type: application/json" \
            -d '{"name":"perf-channel"}' | jq -r '.id')
          echo "SHEPARD_TS_COLL_ID=${TC}" >> "$GITHUB_ENV"
          echo "SHEPARD_TS_ID=${TS}"       >> "$GITHUB_ENV"

      - name: Run k6 endpoint SLO check
        id: k6
        continue-on-error: true
        run: |
          docker run --rm --network host \
            -e SHEPARD_BASE_URL="${SHEPARD_BASE_URL}" \
            -e SHEPARD_API_KEY="${SHEPARD_API_KEY}" \
            -e SHEPARD_COLL_APPID="${SHEPARD_COLL_APPID}" \
            -e SHEPARD_TS_COLL_ID="${SHEPARD_TS_COLL_ID}" \
            -e SHEPARD_TS_ID="${SHEPARD_TS_ID}" \
            -v "$PWD/scripts/perf":/scripts:ro \
            grafana/k6 run /scripts/k6-endpoints.js \
              2>&1 | tee k6-endpoints-output.txt
          echo "exit_code=$?" >> "$GITHUB_OUTPUT"

      - name: Upload artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: k6-endpoint-results
          path: |
            k6-endpoints-output.txt
            scripts/perf/last-run.json
          retention-days: 30

      - name: Dump logs on failure
        if: steps.k6.outcome == 'failure'
        run: |
          docker compose -f infrastructure/docker-compose.yml \
            logs --tail=100 backend

      - name: File issue on SLO breach
        if: >-
          steps.k6.outcome == 'failure' &&
          (github.event_name == 'schedule' ||
           inputs.post_issue == 'true')
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            let summary = '_(check k6-endpoint-results artifact)_';
            try {
              const data = JSON.parse(
                fs.readFileSync('scripts/perf/last-run.json', 'utf8')
              );
              const failing = Object.entries(data.metrics || {})
                .filter(([, v]) =>
                  v.thresholds &&
                  Object.values(v.thresholds).some(t => !t.ok)
                )
                .map(([k, v]) => {
                  const exprs = Object.entries(v.thresholds)
                    .filter(([, t]) => !t.ok)
                    .map(([e]) => e);
                  return `- **${k}**: ${exprs.join(', ')}`;
                });
              if (failing.length) {
                summary = '### Failing SLO thresholds\n\n' +
                          failing.join('\n');
              }
            } catch (_) {}

            const runUrl =
              `https://github.com/${context.repo.owner}/${context.repo.repo}` +
              `/actions/runs/${context.runId}`;

            await github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: `[perf] endpoint SLO breach — ${context.sha.slice(0,7)}`,
              labels: ['performance', 'automated'],
              body: [
                '## Nightly endpoint SLO check — breach',
                '',
                `**Commit:** ${context.sha}`,
                `**Run:** ${runUrl}`,
                '',
                summary,
                '',
                '### Next steps',
                '1. Download `k6-endpoint-results` artifact.',
                '2. Run `scripts/perf/recommend.py` for ranked suggestions.',
                '3. Investigate endpoint-specific regression with `recommend.py`.',
                '4. Close once all SLO thresholds are green.',
                '',
                '_Auto-filed by `.github/workflows/perf-endpoints.yml` (PERF4d)._',
              ].join('\n'),
            });

      - name: Propagate k6 exit code
        if: steps.k6.outcome == 'failure'
        run: exit 1

      - name: Stop compose stack
        if: always()
        run: docker compose -f infrastructure/docker-compose.yml down -v
```

**Required GitHub Actions secret (reuses PERF3's):**

| Name | Type | Description |
|---|---|---|
| `SHEPARD_PERF_API_KEY` | Secret | Instance-admin API key; already set for PERF3 |

No additional secrets are needed for PERF4d.

### 9.3 PR gate consideration

The endpoint SLO check is **not** on the PR critical path (same policy as
PERF3). A CI run of `~10 min` (compose boot + 3 scenarios) is too slow for
per-PR blocking. Regressions surface the next nightly run — typically within
hours of a merge to `main`.

Operators who want a faster signal can run `k6-endpoints.js` locally before
merging:

```bash
K6_SCENARIO=steady \
SHEPARD_BASE_URL=http://localhost:8080 \
SHEPARD_API_KEY=$YOUR_KEY \
SHEPARD_TS_COLL_ID=$TC_ID \
SHEPARD_TS_ID=$TS_ID \
k6 run scripts/perf/k6-endpoints.js
```

The `K6_SCENARIO=steady` env var activates only the 10-VU × 2 min scenario,
completing in ~2 min rather than ~10 min. k6 does not have a `--scenario` CLI
flag; the script reads `K6_SCENARIO` at startup to decide which executor shapes
to register. Use `K6_SCENARIO=steady,ramp` to skip only the spike scenario.

---

## §10. Task breakdown

| ID | Slice | Deliverable | Size | Dependencies |
|---|---|---|---|---|
| **PERF4a** | Per-endpoint SLO script | `scripts/perf/k6-endpoints.js` | S | PERF1 (k6-smoke.js shipped) |
| **PERF4b** | README + SLO table update | Update `scripts/perf/README.md`; add SLO table to `docs/admin.md §Performance metrics` | XS | PERF4a |
| **PERF4c** | Grafana integration | Prometheus remote-write env-var doc + Grafana dashboard panel additions | S | PERF1 monitoring profile |
| **PERF4d** | Nightly CI job | `.github/workflows/perf-endpoints.yml` as designed in §9.2 | S | PERF4a |
| **PERF4e** | Auth-header unification | Align `k6-smoke.js` + `k6-stress.js` to `apikey` header | XS | None (cosmetic) |

PERF4a and PERF4d are the meaningful implementation items. PERF4b/c are
documentation and dashboard follow-ons that land in the same PR as PERF4a.
PERF4e is a clean-up; defer it to the next release cycle.

---

## §11. Connection to the recommender (PERF2b)

`scripts/perf/recommend.py` reads `last-run.json` (written by
`handleSummary`). `k6-endpoints.js` writes to the same path. The recommender's
existing rule catalogue (`59 §4`) will fire on the new endpoint trends without
changes, because it pattern-matches on metric names containing `_duration` or
`_latency`. Two new rules should be added to the recommender alongside PERF4a:

| Rule | Trigger | Suggestion |
|---|---|---|
| `R-ts-range-scan-slow` | `ep_ts_range_scan` p95 > 600 ms at steady-state | Check TimescaleDB chunk interval (`timescaledb.chunk_time_interval`) and the `time_idx` index on the measurements hypertable. Recommend `ANALYZE` + `REINDEX` if the table has grown without a maintenance window. |
| `R-provenance-walk-slow` | `ep_provenance_walk` p95 > 800 ms at steady-state | Check Neo4j relationship index on `:Activity[:GENERATED,USED,WASATTRIBUTEDTO,…]` paths. Suggest `CREATE INDEX FOR ()-[:GENERATED]-() ON ()` if the PROV-O edge types are missing a native index. |

These additions are two Python functions in `recommend.py` — ~30 lines each.
They are not blocked on PERF4a landing; they can ship in the same PR.

---

## §12. Known limitations and follow-ons

| Item | Notes |
|---|---|
| `SHEPARD_COLL_APPID` / `SHEPARD_TS_*` env vars | The CI seed step mints fresh IDs on each run (§9.2). For local use, operators must supply these manually or extend the `setup()` block to auto-discover them from the LUMEN seed (by name lookup). |
| No write-path SLO for `/v2/` endpoints | `POST /v2/collections/{appId}/export`, `POST /v2/processes/{appId}/runs`, and the AAS endpoints have no SLO baseline yet. Add them in a PERF5 slice once those endpoints are stable. |
| Spike scenario accuracy | `constant-arrival-rate` pre-allocates 100 VUs but only fires 100 requests/s. A real sudden burst (e.g. 100 users clicking simultaneously) is closer to `shared-iterations`. The current shape is a first approximation; tune if the production access pattern is known. |
| TimescaleDB data density dependency | The range-scan SLO assumes the seeded LUMEN timeseries data is present (25 channels × N test runs × ~1 000 samples each). On a fresh stack with no data, the range-scan returns in <1 ms and is not a meaningful SLO signal. The CI seed step must ingest at least a few hundred rows before the endpoint SLO run. |
| Grafana Cloud auth rotation | If operators adopt Grafana Cloud output (§8.2), the `K6_CLOUD_TOKEN` must be rotated on the standard 90-day cycle and stored as a repository secret. Add it to the secrets rotation runbook. |
