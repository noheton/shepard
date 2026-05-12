// k6 smoke test for shepard.
//
// Goal: confirm a fresh / freshly-deployed stack can answer the
// hottest endpoints under modest load. NOT a full stress test —
// see k6-stress.js (when it exists) for that.
//
// Run:
//
//   docker run --rm -i --network host \
//       -e SHEPARD_BASE_URL=http://localhost:8080 \
//       -e SHEPARD_API_KEY=<your-key> \
//       grafana/k6 run - < scripts/perf/k6-smoke.js
//
// Or locally with k6 installed:
//
//   SHEPARD_BASE_URL=http://localhost:8080 \
//   SHEPARD_API_KEY=<your-key> \
//   k6 run scripts/perf/k6-smoke.js
//
// Thresholds enforce the casual-user latency budget (`aidocs/42`):
//   p(95) ≤ 500ms on the cheap reads, p(95) ≤ 1500ms on search.
// Failing thresholds → non-zero exit code so CI / install-TUI
// auto-tuning loops can react.

import http from "k6/http";
import { check, group } from "k6";
import { Trend } from "k6/metrics";

const BASE = __ENV.SHEPARD_BASE_URL || "http://localhost:8080";
const KEY = __ENV.SHEPARD_API_KEY || "";

const params = KEY
  ? { headers: { "X-API-KEY": KEY, Accept: "application/json" } }
  : { headers: { Accept: "application/json" } };

const versionz = new Trend("versionz_duration", true);
const collections = new Trend("collections_list_duration", true);
const search = new Trend("search_duration", true);

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
      gracefulStop: "5s",
    },
  },
  thresholds: {
    "http_req_failed": ["rate<0.01"],
    "http_req_duration{endpoint:versionz}": ["p(95)<200"],
    "http_req_duration{endpoint:collections}": ["p(95)<500"],
    "http_req_duration{endpoint:search}": ["p(95)<1500"],
  },
};

export default function () {
  group("public", () => {
    const r = http.get(`${BASE}/versionz`, {
      ...params,
      tags: { endpoint: "versionz" },
    });
    versionz.add(r.timings.duration);
    check(r, { "versionz 200": (x) => x.status === 200 });
  });

  group("collections-list", () => {
    const r = http.get(`${BASE}/shepard/api/collections`, {
      ...params,
      tags: { endpoint: "collections" },
    });
    collections.add(r.timings.duration);
    check(r, {
      "collections 200/401": (x) =>
        x.status === 200 || x.status === 401,
    });
  });

  group("search", () => {
    const body = JSON.stringify({ kind: "Collection", filters: [] });
    const r = http.post(`${BASE}/shepard/api/collections/search`, body, {
      headers: {
        ...params.headers,
        "Content-Type": "application/json",
      },
      tags: { endpoint: "search" },
    });
    search.add(r.timings.duration);
    check(r, {
      "search 200/400/401": (x) =>
        x.status === 200 || x.status === 400 || x.status === 401,
    });
  });
}

export function handleSummary(data) {
  // k6 ≥ 0.49 emits this; stdout summary stays plus a JSON dump
  // for the auto-tuning loop in scripts/perf/recommend.py.
  return {
    "stdout": "",
    "scripts/perf/last-run.json": JSON.stringify(data, null, 2),
  };
}
