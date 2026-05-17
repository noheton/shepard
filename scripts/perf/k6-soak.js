// k6 soak test for shepard.
//
// Goal: catch memory leaks, JDBC connection-pool drift, and Mongo
// connection-pool growth that only appear under sustained (hours-long)
// low-VU load — patterns invisible in the 8-minute stress run.
//
// Design: aidocs/ops/59-performance-testing-and-tuning.md §3.3 (PERF2c).
//
// Run (off-by-default — slow):
//
//   docker run --rm -i --network host \
//       -e SHEPARD_BASE_URL=http://localhost:8080 \
//       -e SHEPARD_API_KEY=<your-key> \
//       grafana/k6 run - < scripts/perf/k6-soak.js
//
// Prometheus remote-write (optional, requires monitoring profile):
//
//   docker run --rm -i --network host \
//       -e SHEPARD_BASE_URL=http://localhost:8080 \
//       -e SHEPARD_API_KEY=<your-key> \
//       -e K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
//       -e K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true \
//       grafana/k6 run --out=experimental-prometheus-rw - < scripts/perf/k6-soak.js
//
// The Prometheus output causes Grafana (from the monitoring profile)
// to annotate the dashboard with a "soak run" band — see aidocs/59 §3.3.
//
// NOTE: This test runs for 2 hours at 5 VUs. Do not run it against
// production or a shared environment without operator sign-off.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import { randomIntBetween } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const BASE = __ENV.SHEPARD_BASE_URL || "http://localhost:8080";
const KEY  = __ENV.SHEPARD_API_KEY  || "";

const headers = KEY
  ? { "X-API-KEY": KEY, Accept: "application/json", "Content-Type": "application/json" }
  : { Accept: "application/json", "Content-Type": "application/json" };

const headersNoBody = { ...headers };
delete headersNoBody["Content-Type"];

// ── Custom metrics ────────────────────────────────────────────────────────────

const collectionsLatency = new Trend("soak_collections_duration", true);
const searchLatency      = new Trend("soak_search_duration",      true);
const versionzLatency    = new Trend("soak_versionz_duration",    true);
const errorRate          = new Rate("soak_error_rate");

// ── Test options ──────────────────────────────────────────────────────────────
//
// Low VU count (5), long duration (2 h), with a 30 s ramp-up and
// 30 s cool-down.  The soak window is `SOAK_DURATION_MINUTES` minutes
// (default 120 = 2 h), overridable via environment variable so operators
// can do a quick 15-minute trial first.

const soakMinutes = parseInt(__ENV.SOAK_DURATION_MINUTES || "120", 10);

export const options = {
  stages: [
    { duration: "30s",              target: 5 },
    { duration: `${soakMinutes}m`, target: 5 },
    { duration: "30s",              target: 0 },
  ],
  thresholds: {
    // Soak thresholds are deliberately relaxed vs smoke/stress — the goal
    // is detecting DRIFT over time, not hitting latency budgets under load.
    "http_req_failed":            ["rate<0.02"],
    "soak_error_rate":            ["rate<0.02"],
    "soak_collections_duration":  ["p(95)<2000"],
    "soak_search_duration":       ["p(95)<5000"],
    "soak_versionz_duration":     ["p(95)<500"],
  },
};

// ── Setup ─────────────────────────────────────────────────────────────────────

export function setup() {
  // Record the start time so teardown can calculate the total elapsed duration.
  return { startEpochMs: Date.now() };
}

// ── VU function ───────────────────────────────────────────────────────────────
//
// Soak workload is deliberately light: healthcheck, collection listing,
// and search.  No writes — the soak measures read-path stability.

export default function () {
  group("healthcheck", () => {
    const r = http.get(`${BASE}/versionz`, {
      headers: headersNoBody,
      tags: { endpoint: "versionz" },
    });
    versionzLatency.add(r.timings.duration);
    const ok = check(r, { "versionz 200": (x) => x.status === 200 });
    errorRate.add(ok ? 0 : 1);
  });

  group("collections-list", () => {
    const r = http.get(`${BASE}/shepard/api/collections?page=0&pageSize=10`, {
      headers: headersNoBody,
      tags: { endpoint: "collections" },
    });
    collectionsLatency.add(r.timings.duration);
    const ok = check(r, {
      "collections 200/401": (x) => x.status === 200 || x.status === 401,
    });
    errorRate.add(ok ? 0 : 1);
  });

  group("search", () => {
    const body = JSON.stringify({ kind: "Collection", filters: [] });
    const r = http.post(`${BASE}/shepard/api/collections/search`, body, {
      headers,
      tags: { endpoint: "search" },
    });
    searchLatency.add(r.timings.duration);
    const ok = check(r, {
      "search 200/400/401": (x) =>
        x.status === 200 || x.status === 400 || x.status === 401,
    });
    errorRate.add(ok ? 0 : 1);
  });

  // Pause between iterations — keeps each VU at ~3 req/s so 5 VUs
  // generate ~15 req/s total, a realistic background read rate.
  sleep(randomIntBetween(1, 3));
}

// ── Teardown ──────────────────────────────────────────────────────────────────

export function teardown(data) {
  if (data && data.startEpochMs) {
    const elapsedMin = ((Date.now() - data.startEpochMs) / 60_000).toFixed(1);
    console.log(`soak: completed — elapsed ${elapsedMin} min`);
  }
}

// ── Summary ───────────────────────────────────────────────────────────────────
//
// Writes last-run.json for scripts/perf/recommend.py (PERF2b).
// If the Prometheus remote-write output is active, the time-series data
// is already in Prometheus; this JSON captures the run-level summary
// (thresholds, aggregates) only.

export function handleSummary(data) {
  return {
    "stdout": "",
    "scripts/perf/last-run.json": JSON.stringify(data, null, 2),
  };
}
