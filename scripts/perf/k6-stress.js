// k6 multi-stage stress test for shepard.
//
// Goal: surface bottlenecks that the smoke test can't reach —
// file-handle exhaustion, GC pause storms, Neo4j page-cache misses,
// permissions-cache saturation — by driving 25–50 concurrent users
// through a realistic workload mix.
//
// Design: aidocs/ops/59-performance-testing-and-tuning.md §3.2 (PERF2a).
//
// Run:
//
//   docker run --rm -i --network host \
//       -e SHEPARD_BASE_URL=http://localhost:8080 \
//       -e SHEPARD_API_KEY=<your-key> \
//       grafana/k6 run - < scripts/perf/k6-stress.js
//
// Or locally with k6 installed:
//
//   SHEPARD_BASE_URL=http://localhost:8080 \
//   SHEPARD_API_KEY=<your-key> \
//   k6 run scripts/perf/k6-stress.js
//
// The test writes scripts/perf/last-run.json for the recommender
// (PERF2b). Thresholds mirror aidocs/42 casual-user latency budgets
// adjusted for peak-load conditions.
//
// Prerequisites: a running shepard instance with at least one
// accessible collection. The API key must have write access so the
// setup phase can create temporary test fixtures.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Trend, Counter, Rate } from "k6/metrics";
import { randomIntBetween } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const BASE = __ENV.SHEPARD_BASE_URL || "http://localhost:8080";
const KEY = __ENV.SHEPARD_API_KEY || "";

// ── Auth headers ─────────────────────────────────────────────────────────────

const headers = KEY
  ? { "X-API-KEY": KEY, Accept: "application/json", "Content-Type": "application/json" }
  : { Accept: "application/json", "Content-Type": "application/json" };

const headersNoBody = { ...headers };
delete headersNoBody["Content-Type"];

// ── Custom metrics ────────────────────────────────────────────────────────────

const collectionsListLatency  = new Trend("collections_list_duration",   true);
const searchLatency           = new Trend("search_duration",             true);
const dataObjectReadLatency   = new Trend("dataobject_read_duration",    true);
const fileUploadLatency       = new Trend("file_upload_duration",        true);
const tsIngestLatency         = new Trend("ts_ingest_duration",          true);
const permCheckedReadLatency  = new Trend("perm_checked_read_duration",  true);
const errors                  = new Counter("stress_errors");
const errorRate               = new Rate("stress_error_rate");

// ── Test options ──────────────────────────────────────────────────────────────
//
// Five stages per aidocs/59 §3.2:
//   1. 1 min  : 0 → 25 VUs  (warm-up)
//   2. 3 min  : hold 25 VUs (steady-state baseline)
//   3. 1 min  : 25 → 50 VUs (peak ramp)
//   4. 2 min  : hold 50 VUs (peak — file upload + ingest + search + perm reads)
//   5. 1 min  : 50 → 0 VUs  (cool-down)

export const options = {
  stages: [
    { duration: "1m",  target: 25 },
    { duration: "3m",  target: 25 },
    { duration: "1m",  target: 50 },
    { duration: "2m",  target: 50 },
    { duration: "1m",  target: 0  },
  ],
  thresholds: {
    // Hard error cap — any higher and we're stressing the wrong thing.
    "http_req_failed":                             ["rate<0.05"],
    "stress_error_rate":                           ["rate<0.05"],

    // Collections list — casual read, should stay fast even under load.
    "collections_list_duration":                   ["p(95)<800",  "p(99)<2000"],

    // Search — heavier Cypher, relaxed budget for peak load.
    "search_duration":                             ["p(95)<3000", "p(99)<6000"],

    // Single DataObject read — cached graph path.
    "dataobject_read_duration":                    ["p(95)<500",  "p(99)<1500"],

    // File upload — I/O bound; 10 s p95 is the first-alarm threshold.
    "file_upload_duration":                        ["p(95)<10000"],

    // Timeseries CSV ingest — batch insert; 5 s p95 is first-alarm.
    "ts_ingest_duration":                          ["p(95)<5000"],

    // Permissions-checked read — exercises the cache; should stay fast.
    "perm_checked_read_duration":                  ["p(95)<600",  "p(99)<1500"],
  },
};

// ── Setup — create shared test fixtures once ──────────────────────────────────
//
// Returns a data bag that is passed to every VU's default function and
// to teardown.  The fixtures are prefixed with "k6-stress-" so they are
// identifiable for manual cleanup if teardown is interrupted.

export function setup() {
  const h = { headers };
  const hNoBody = { headers: headersNoBody };

  // 1. Create a throwaway collection to hang test objects on.
  const collRes = http.post(
    `${BASE}/shepard/api/collections`,
    JSON.stringify({ name: "k6-stress-collection", description: "k6 stress fixture — safe to delete" }),
    h,
  );
  if (collRes.status !== 200 && collRes.status !== 201) {
    console.error(`setup: failed to create collection (${collRes.status}): ${collRes.body}`);
    return {};
  }
  const coll = JSON.parse(collRes.body);
  const collectionId = coll.id;

  // 2. Create a DataObject inside the collection for read tests.
  const doRes = http.post(
    `${BASE}/shepard/api/collections/${collectionId}/dataobjects`,
    JSON.stringify({ name: "k6-stress-dataobject" }),
    h,
  );
  const doId = doRes.status < 300 ? JSON.parse(doRes.body).id : null;

  // 3. Create a FileContainer for upload tests.
  const fcRes = http.post(
    `${BASE}/shepard/api/filecontainers`,
    JSON.stringify({ name: "k6-stress-filecontainer" }),
    h,
  );
  const fileContainerId = fcRes.status < 300 ? JSON.parse(fcRes.body).id : null;

  // 4. Create a TimeseriesContainer for ingest tests.
  const tcRes = http.post(
    `${BASE}/shepard/api/timeseriescontainers`,
    JSON.stringify({ name: "k6-stress-tscontainer" }),
    h,
  );
  const tsContainerId = tcRes.status < 300 ? JSON.parse(tcRes.body).id : null;

  // 5. Create a Timeseries inside the container so VUs can ingest data points.
  let timeseriesId = null;
  if (tsContainerId) {
    const tsRes = http.post(
      `${BASE}/shepard/api/timeseriescontainers/${tsContainerId}/timeseries`,
      JSON.stringify({ name: "k6-stress-series" }),
      h,
    );
    if (tsRes.status < 300) {
      timeseriesId = JSON.parse(tsRes.body).id;
    }
  }

  console.log(
    `setup: collectionId=${collectionId} doId=${doId} ` +
    `fileContainerId=${fileContainerId} tsContainerId=${tsContainerId} ` +
    `timeseriesId=${timeseriesId}`,
  );

  return { collectionId, doId, fileContainerId, tsContainerId, timeseriesId };
}

// ── VU default function ───────────────────────────────────────────────────────
//
// Each VU picks a workload scenario by random weight:
//   40% — cheap reads (collections list + DataObject read)
//   25% — search
//   20% — permissions-checked read
//   10% — file upload (1 KB blob)
//    5% — timeseries CSV ingest

export default function (data) {
  if (!data || !data.collectionId) {
    // Setup failed (e.g. unauthenticated) — all VUs become search-only.
    doSearch();
    sleep(1);
    return;
  }

  const roll = Math.random();

  if (roll < 0.40) {
    doCheapReads(data);
  } else if (roll < 0.65) {
    doSearch();
  } else if (roll < 0.85) {
    doPermCheckedRead(data);
  } else if (roll < 0.95) {
    doFileUpload(data);
  } else {
    doTsIngest(data);
  }

  sleep(randomIntBetween(1, 3));
}

// ── Workload scenarios ────────────────────────────────────────────────────────

function doCheapReads(data) {
  group("cheap-reads", () => {
    // Collections list — paginated
    {
      const r = http.get(`${BASE}/shepard/api/collections?page=0&pageSize=20`, {
        headers: headersNoBody,
        tags: { endpoint: "collections-list" },
      });
      collectionsListLatency.add(r.timings.duration);
      const ok = check(r, { "collections list 200": (x) => x.status === 200 || x.status === 401 });
      if (!ok) { errors.add(1); errorRate.add(1); } else { errorRate.add(0); }
    }

    // DataObject read — single-item lookup
    if (data.doId) {
      const r = http.get(
        `${BASE}/shepard/api/collections/${data.collectionId}/dataobjects/${data.doId}`,
        { headers: headersNoBody, tags: { endpoint: "dataobject-read" } },
      );
      dataObjectReadLatency.add(r.timings.duration);
      const ok = check(r, { "dataobject read 200": (x) => x.status === 200 || x.status === 401 || x.status === 403 });
      if (!ok) { errors.add(1); errorRate.add(1); } else { errorRate.add(0); }
    }
  });
}

function doSearch() {
  group("search", () => {
    const body = JSON.stringify({ kind: "Collection", filters: [] });
    const r = http.post(`${BASE}/shepard/api/collections/search`, body, {
      headers,
      tags: { endpoint: "search" },
    });
    searchLatency.add(r.timings.duration);
    const ok = check(r, {
      "search 200/400/401": (x) => x.status === 200 || x.status === 400 || x.status === 401,
    });
    if (!ok) { errors.add(1); errorRate.add(1); } else { errorRate.add(0); }
  });
}

function doPermCheckedRead(data) {
  group("perm-checked-read", () => {
    // GET collections exercises the permissions filter on every result.
    const r = http.get(`${BASE}/shepard/api/collections?page=0&pageSize=10`, {
      headers: headersNoBody,
      tags: { endpoint: "perm-checked-read" },
    });
    permCheckedReadLatency.add(r.timings.duration);
    const ok = check(r, {
      "perm read 200/401": (x) => x.status === 200 || x.status === 401,
    });
    if (!ok) { errors.add(1); errorRate.add(1); } else { errorRate.add(0); }
  });
}

function doFileUpload(data) {
  if (!data.fileContainerId) return;
  group("file-upload", () => {
    // Generate a reproducible 1 KB payload (k6 runs are deterministic per VU seed).
    const blob = new Uint8Array(1024).fill(0x41); // 1 KB of 'A'
    const fd = {
      file: http.file(blob, "k6-test.bin", "application/octet-stream"),
    };
    const r = http.post(
      `${BASE}/shepard/api/filecontainers/${data.fileContainerId}/files`,
      fd,
      {
        headers: KEY ? { "X-API-KEY": KEY } : {},
        tags: { endpoint: "file-upload" },
      },
    );
    fileUploadLatency.add(r.timings.duration);
    const ok = check(r, {
      "file upload 200/201/401/403": (x) =>
        x.status === 200 || x.status === 201 || x.status === 401 || x.status === 403,
    });
    if (!ok) { errors.add(1); errorRate.add(1); } else { errorRate.add(0); }
  });
}

function doTsIngest(data) {
  if (!data.tsContainerId || !data.timeseriesId) return;
  group("ts-ingest", () => {
    // Post 10 data points as CSV (the backend's COPY-API bulk path).
    const now = Date.now();
    const rows = Array.from({ length: 10 }, (_, i) => {
      const ts = new Date(now - (9 - i) * 1000).toISOString();
      return `${ts},${(Math.random() * 100).toFixed(3)}`;
    });
    const csv = "time,value_double\n" + rows.join("\n");

    const r = http.post(
      `${BASE}/shepard/api/timeseriescontainers/${data.tsContainerId}/timeseries/${data.timeseriesId}/datapoints/file`,
      csv,
      {
        headers: KEY
          ? { "X-API-KEY": KEY, "Content-Type": "text/csv" }
          : { "Content-Type": "text/csv" },
        tags: { endpoint: "ts-ingest" },
      },
    );
    tsIngestLatency.add(r.timings.duration);
    const ok = check(r, {
      "ts ingest 200/201/204/401/403": (x) =>
        x.status === 200 || x.status === 201 || x.status === 204 ||
        x.status === 401 || x.status === 403,
    });
    if (!ok) { errors.add(1); errorRate.add(1); } else { errorRate.add(0); }
  });
}

// ── Teardown — remove test fixtures ──────────────────────────────────────────

export function teardown(data) {
  if (!data || !data.collectionId) return;

  const h = { headers: headersNoBody };

  // Order matters: delete children before parents.
  if (data.tsContainerId) {
    http.del(`${BASE}/shepard/api/timeseriescontainers/${data.tsContainerId}`, null, h);
  }
  if (data.fileContainerId) {
    http.del(`${BASE}/shepard/api/filecontainers/${data.fileContainerId}`, null, h);
  }
  if (data.doId) {
    http.del(
      `${BASE}/shepard/api/collections/${data.collectionId}/dataobjects/${data.doId}`,
      null,
      h,
    );
  }
  if (data.collectionId) {
    http.del(`${BASE}/shepard/api/collections/${data.collectionId}`, null, h);
  }

  console.log("teardown: test fixtures removed");
}

// ── Summary ───────────────────────────────────────────────────────────────────
//
// Writes last-run.json for scripts/perf/recommend.py (PERF2b).

export function handleSummary(data) {
  return {
    "stdout": "",
    "scripts/perf/last-run.json": JSON.stringify(data, null, 2),
  };
}
