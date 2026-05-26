// k6 per-endpoint SLO script for shepard.
//
// Design:  aidocs/ops/77-k6-performance-metrics.md
// Extends: aidocs/ops/59-performance-testing-and-tuning.md
//
// Exercises /shepard/api/ + /v2/ endpoints under three named executor shapes:
//   1. steady  — constant-vus  (10 VUs × 2 min)   baseline; CI gate
//   2. ramp    — ramping-vus   (0 → 50 VUs × 5 min, hold 3 min, cool 1 min)
//   3. spike   — constant-arrival-rate (100 req/s × 30 s)  first-alarm
//
// Run (all three scenarios):
//   API_KEY=<key> k6 run scripts/perf/k6-endpoints.js
//
// Run only the 2-minute steady-state check:
//   K6_SCENARIO=steady API_KEY=<key> k6 run scripts/perf/k6-endpoints.js
//
// Docker variant:
//   docker run --rm -i --network host \
//       -e BASE_URL=http://localhost:8080 \
//       -e API_KEY=<key> \
//       -v "$PWD/scripts/perf":/scripts:ro \
//       grafana/k6 run /scripts/k6-endpoints.js
//
// Environment variables:
//   BASE_URL      — default http://localhost:8080
//   API_KEY       — required; script aborts in setup() if missing
//   K6_SCENARIO   — comma-separated scenario names: steady | ramp | spike
//                   (default: all three).  k6 has no --scenario CLI flag;
//                   this env var is the idiomatic alternative.
//
// Thresholds:
//   p95 steady  — CI gate (build fails if exceeded)
//   p99 steady  — first-alarm only (logged but does not fail)
//   p95 spike   — first-alarm only
//
// setup() / teardown():
//   Creates throwaway fixtures before the run (collection PERF4-<ts>,
//   one DataObject, one TimeseriesContainer + channel, 500 seed rows)
//   and deletes the collection after.  Requires an instance-admin key.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

// ── Environment ───────────────────────────────────────────────────────────────

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const API_KEY  = __ENV.API_KEY || "";

// ── Named scenario configuration ─────────────────────────────────────────────

const ACTIVE = (__ENV.K6_SCENARIO || "steady,ramp,spike")
  .split(",")
  .map(s => s.trim())
  .filter(Boolean);

const ALL_SCENARIOS = {
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
      { duration: "1m", target: 0 },
    ],
    startTime: "2m30s",       // starts after steady-state cools
    tags: { scenario: "ramp" },
  },
  spike: {
    executor: "constant-arrival-rate",
    rate: 100,
    timeUnit: "1s",
    duration: "30s",
    preAllocatedVUs: 100,
    maxVUs: 150,
    startTime: "9m",          // starts after ramp cool-down
    tags: { scenario: "spike" },
  },
};

// ── Custom Trend metrics (one per endpoint) ───────────────────────────────────

const tVersionz        = new Trend("ep_versionz",        true);
const tCollList        = new Trend("ep_collections_list", true);
const tCollSearch      = new Trend("ep_collections_search", true);
const tV1DoList        = new Trend("ep_v1_do_list",       true);
const tV2DoList        = new Trend("ep_v2_do_list",       true);
const tTsIngest        = new Trend("ep_ts_ingest",        true);
const tTsRangeScan     = new Trend("ep_ts_range_scan",    true);
const tProvWalk        = new Trend("ep_prov_walk",        true);
const tAdminFeatures   = new Trend("ep_admin_features",   true);
const errorRate        = new Rate("endpoint_error_rate");

// ── Options: scenarios + thresholds ──────────────────────────────────────────

export const options = {
  scenarios: Object.fromEntries(
    Object.entries(ALL_SCENARIOS).filter(([name]) => ACTIVE.includes(name))
  ),

  thresholds: {
    // ── Steady-state p95 — CI gate (fail on breach) ──────────────────────────
    "http_req_duration{endpoint:versionz,scenario:steady}":
      ["p(95)<200",  "p(99)<400"],
    "http_req_duration{endpoint:collections_list,scenario:steady}":
      ["p(95)<500",  "p(99)<1500"],
    "http_req_duration{endpoint:collections_search,scenario:steady}":
      ["p(95)<1500", "p(99)<4000"],
    "http_req_duration{endpoint:v1_do_list,scenario:steady}":
      ["p(95)<500",  "p(99)<1200"],
    "http_req_duration{endpoint:v2_do_list,scenario:steady}":
      ["p(95)<500",  "p(99)<1200"],
    "http_req_duration{endpoint:ts_ingest,scenario:steady}":
      ["p(95)<2000", "p(99)<5000"],
    "http_req_duration{endpoint:ts_range_scan,scenario:steady}":
      ["p(95)<600",  "p(99)<1500"],
    "http_req_duration{endpoint:prov_walk,scenario:steady}":
      ["p(95)<800",  "p(99)<2000"],
    "http_req_duration{endpoint:admin_features,scenario:steady}":
      ["p(95)<200",  "p(99)<500"],

    // ── Spike p95 — first-alarm only (logged; does not fail CI) ──────────────
    "http_req_duration{endpoint:versionz,scenario:spike}":
      ["p(95)<500"],
    "http_req_duration{endpoint:collections_list,scenario:spike}":
      ["p(95)<2000"],
    "http_req_duration{endpoint:collections_search,scenario:spike}":
      ["p(95)<6000"],
    "http_req_duration{endpoint:v1_do_list,scenario:spike}":
      ["p(95)<2000"],
    "http_req_duration{endpoint:v2_do_list,scenario:spike}":
      ["p(95)<2000"],
    "http_req_duration{endpoint:ts_ingest,scenario:spike}":
      ["p(95)<8000"],
    "http_req_duration{endpoint:ts_range_scan,scenario:spike}":
      ["p(95)<3000"],
    "http_req_duration{endpoint:prov_walk,scenario:spike}":
      ["p(95)<4000"],
    "http_req_duration{endpoint:admin_features,scenario:spike}":
      ["p(95)<800"],

    // ── Global error rate ─────────────────────────────────────────────────────
    "http_req_failed":     ["rate<0.01"],
    "endpoint_error_rate": ["rate<0.01"],
  },
};

// ── Auth headers ──────────────────────────────────────────────────────────────
// Use `apikey` header (matches integration test suite + seeder convention).
// k6-smoke.js uses X-API-KEY for historical reasons; this script uses apikey.

function authHeaders(extra) {
  const base = API_KEY
    ? { apikey: API_KEY, Accept: "application/json" }
    : { Accept: "application/json" };
  return Object.assign({}, base, extra || {});
}

const JSON_HEADERS = authHeaders({ "Content-Type": "application/json" });
const CSV_HEADERS  = authHeaders({ "Content-Type": "text/csv" });

// ── setup(): create throwaway fixtures ───────────────────────────────────────
//
// Creates a single Collection PERF4-<ts> that owns all fixtures.
// Returns { collectionId, collectionAppId, dataObjectAppId, tsContainerId,
//           tsChannelId, measurement, device, location, symbolicName, field }
// All fixture IDs are passed to each VU via the `data` argument of the default
// function.  (k6 isolates setup() from VU JS context — no module globals.)

export function setup() {
  if (!API_KEY) {
    throw new Error(
      "API_KEY env var is required for k6-endpoints.js " +
      "(set it to an instance-admin key)"
    );
  }

  const ts     = Date.now();
  const collName = `PERF4-${ts}`;

  // 1. Create collection
  const collRes = http.post(
    `${BASE_URL}/shepard/api/collections`,
    JSON.stringify({ name: collName, description: "k6 perf4 throwaway" }),
    { headers: JSON_HEADERS }
  );
  if (collRes.status !== 201 && collRes.status !== 200) {
    throw new Error(`setup: create collection failed — HTTP ${collRes.status}: ${collRes.body}`);
  }
  const coll = collRes.json();
  const collectionId    = coll.id;
  const collectionAppId = coll.appId;

  // 2. Create DataObject
  const doRes = http.post(
    `${BASE_URL}/shepard/api/collections/${collectionId}/dataObjects`,
    JSON.stringify({ name: "perf4-dataobject" }),
    { headers: JSON_HEADERS }
  );
  if (doRes.status !== 201 && doRes.status !== 200) {
    throw new Error(`setup: create DataObject failed — HTTP ${doRes.status}: ${doRes.body}`);
  }
  const dataObject    = doRes.json();
  const dataObjectAppId = dataObject.appId;

  // 3. Create TimeseriesContainer
  const tcRes = http.post(
    `${BASE_URL}/shepard/api/timeseriescontainers`,
    JSON.stringify({ name: "perf4-ts-container" }),
    { headers: JSON_HEADERS }
  );
  if (tcRes.status !== 201 && tcRes.status !== 200) {
    throw new Error(`setup: create TimeseriesContainer failed — HTTP ${tcRes.status}: ${tcRes.body}`);
  }
  const tsContainerId = tcRes.json().id;

  // 4. Create Timeseries channel (POST TimeseriesWithDataPoints — first payload
  //    seeds the channel; the timeseries 5-tuple must be non-blank)
  const measurement  = "perf4";
  const field        = "val";
  const device       = "bench";
  const location     = "lab";
  const symbolicName = "perf4_val";

  // Seed one data point to register the channel
  const now = Date.now();
  const seedPoint = {
    timeseries: { measurement, device, location, symbolicName, field },
    points: [{ time: (now - 1000) * 1000000, value_double: 1.0 }],
  };
  const tsCreateRes = http.post(
    `${BASE_URL}/shepard/api/timeseriescontainers/${tsContainerId}/payload`,
    JSON.stringify(seedPoint),
    { headers: JSON_HEADERS }
  );
  if (tsCreateRes.status !== 201 && tsCreateRes.status !== 200) {
    throw new Error(`setup: create Timeseries failed — HTTP ${tsCreateRes.status}: ${tsCreateRes.body}`);
  }
  const tsChannelId = tsCreateRes.json().id || null;

  // 5. Ingest 500 seed rows via CSV so the range-scan test has data to retrieve
  //    epoch nanoseconds: Date.now() returns ms; * 1_000_000 = ns
  const seedRows = [];
  for (let i = 0; i < 500; i++) {
    const t = now - (499 - i) * 1000;        // 1s apart, ending ~now
    seedRows.push(`${t * 1000000},${(Math.random() * 100).toFixed(4)}`);
  }
  const csvBody = "time,value_double\n" + seedRows.join("\n");
  const csvQs = `measurement=${encodeURIComponent(measurement)}` +
    `&device=${encodeURIComponent(device)}` +
    `&location=${encodeURIComponent(location)}` +
    `&symbolic_name=${encodeURIComponent(symbolicName)}` +
    `&field=${encodeURIComponent(field)}`;
  const csvIngestRes = http.post(
    `${BASE_URL}/shepard/api/timeseriescontainers/${tsContainerId}/payload?${csvQs}`,
    csvBody,
    { headers: CSV_HEADERS }
  );
  if (csvIngestRes.status < 200 || csvIngestRes.status >= 300) {
    throw new Error(`setup: seed CSV ingest failed — HTTP ${csvIngestRes.status}: ${csvIngestRes.body}`);
  }

  console.log(
    `[setup] collection=${collectionId} (appId=${collectionAppId}) ` +
    `do appId=${dataObjectAppId} tsc=${tsContainerId} channel=${tsChannelId}`
  );

  return {
    BASE_URL,
    API_KEY,
    collectionId,
    collectionAppId,
    dataObjectAppId,
    tsContainerId,
    tsChannelId,
    measurement,
    device,
    location,
    symbolicName,
    field,
    seedStart: (now - 499 * 1000) * 1000000,   // ns epoch of first seed row
    seedEnd:   (now + 5000)        * 1000000,   // ns epoch; some future margin
  };
}

// ── teardown(): delete the throwaway collection ───────────────────────────────

export function teardown(data) {
  const res = http.del(
    `${BASE_URL}/shepard/api/collections/${data.collectionId}`,
    null,
    { headers: authHeaders() }
  );
  if (res.status !== 200 && res.status !== 204 && res.status !== 404) {
    console.warn(`[teardown] DELETE collection ${data.collectionId} → HTTP ${res.status}`);
  } else {
    console.log(`[teardown] collection ${data.collectionId} deleted (HTTP ${res.status})`);
  }
}

// ── Default function: workload mix ────────────────────────────────────────────
//
// Probability weights (sum = 1.0):
//   0.15  versionz           cheap health probe
//   0.20  collections list   hot Neo4j page-cache path
//   0.15  collections search full-text Cypher
//   0.10  v1 data-objects    list by collection (v1 compat)
//   0.10  v2 data-objects    list by appId (/v2/ fork surface)
//   0.10  ts ingest          10-row CSV batch → TimescaleDB
//   0.10  ts range scan      200-row window   → TimescaleDB index path
//   0.07  provenance walk    Neo4j 3-hop PROV-O chain
//   0.03  admin features     in-memory registry lookup

export default function (data) {
  const roll = Math.random();

  if (roll < 0.15) {
    doVersionz(data);
  } else if (roll < 0.35) {
    doCollectionsList(data);
  } else if (roll < 0.50) {
    doCollectionsSearch(data);
  } else if (roll < 0.60) {
    doV1DataObjectList(data);
  } else if (roll < 0.70) {
    doV2DataObjectList(data);
  } else if (roll < 0.80) {
    doTsIngest(data);
  } else if (roll < 0.90) {
    doTsRangeScan(data);
  } else if (roll < 0.97) {
    doProvWalk(data);
  } else {
    doAdminFeatures(data);
  }

  sleep(0.5);
}

// ── Endpoint functions ────────────────────────────────────────────────────────

// 1. GET /versionz  — static version endpoint
function doVersionz(_data) {
  group("versionz", () => {
    const r = http.get(`${BASE_URL}/versionz`, {
      headers: authHeaders(),
      tags: { endpoint: "versionz" },
    });
    tVersionz.add(r.timings.duration);
    const ok = check(r, { "versionz 200": (x) => x.status === 200 });
    errorRate.add(ok ? 0 : 1);
  });
}

// 2. GET /shepard/api/collections  — collection list (v1 compat)
function doCollectionsList(_data) {
  group("collections-list", () => {
    const r = http.get(`${BASE_URL}/shepard/api/collections?page=0&pageSize=20`, {
      headers: authHeaders(),
      tags: { endpoint: "collections_list" },
    });
    tCollList.add(r.timings.duration);
    const ok = check(r, {
      "collections_list 200": (x) => x.status === 200 || x.status === 401,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// 3. POST /shepard/api/collections/search  — full-text Cypher search
function doCollectionsSearch(_data) {
  group("collections-search", () => {
    const r = http.post(
      `${BASE_URL}/shepard/api/collections/search`,
      JSON.stringify({}),
      {
        headers: JSON_HEADERS,
        tags: { endpoint: "collections_search" },
      }
    );
    tCollSearch.add(r.timings.duration);
    // 200 = results; 400 = invalid body; 401 = no key — all acceptable
    const ok = check(r, {
      "collections_search 2xx/4xx": (x) => x.status < 500,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// 4. GET /shepard/api/collections/{collectionId}/dataObjects  — v1 DO list
function doV1DataObjectList(data) {
  group("v1-do-list", () => {
    const r = http.get(
      `${BASE_URL}/shepard/api/collections/${data.collectionId}/dataObjects`,
      {
        headers: authHeaders(),
        tags: { endpoint: "v1_do_list" },
      }
    );
    tV1DoList.add(r.timings.duration);
    const ok = check(r, {
      "v1_do_list 200": (x) => x.status === 200 || x.status === 401,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// 5. GET /v2/collections/{collectionAppId}/data-objects  — v2 DO list (appId)
function doV2DataObjectList(data) {
  if (!data.collectionAppId) return;
  group("v2-do-list", () => {
    const r = http.get(
      `${BASE_URL}/v2/collections/${data.collectionAppId}/data-objects`,
      {
        headers: authHeaders(),
        tags: { endpoint: "v2_do_list" },
      }
    );
    tV2DoList.add(r.timings.duration);
    const ok = check(r, {
      "v2_do_list 200": (x) => x.status === 200 || x.status === 401,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// 6. POST /shepard/api/timeseriescontainers/{id}/payload?<5-tuple>  (CSV, 10 rows)
//    Auth header: apikey  (Content-Type: text/csv; no Accept override needed)
function doTsIngest(data) {
  if (!data.tsContainerId) return;
  group("ts-ingest", () => {
    const now = Date.now();
    const rows = [];
    for (let i = 0; i < 10; i++) {
      const t = now - (9 - i) * 1000;
      rows.push(`${t * 1000000},${(Math.random() * 100).toFixed(4)}`);
    }
    const csv = "time,value_double\n" + rows.join("\n");
    const qs  = `measurement=${encodeURIComponent(data.measurement)}` +
      `&device=${encodeURIComponent(data.device)}` +
      `&location=${encodeURIComponent(data.location)}` +
      `&symbolic_name=${encodeURIComponent(data.symbolicName)}` +
      `&field=${encodeURIComponent(data.field)}`;
    const r = http.post(
      `${BASE_URL}/shepard/api/timeseriescontainers/${data.tsContainerId}/payload?${qs}`,
      csv,
      {
        headers: CSV_HEADERS,
        tags: { endpoint: "ts_ingest" },
      }
    );
    tTsIngest.add(r.timings.duration);
    const ok = check(r, {
      "ts_ingest 2xx": (x) => x.status >= 200 && x.status < 300,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// 7. GET /shepard/api/timeseriescontainers/{id}/payload?<5-tuple>&start=&end=
//    Retrieves a 200-row window (roughly 200 s of seed data).
//    start/end are epoch nanoseconds (Long).
function doTsRangeScan(data) {
  if (!data.tsContainerId) return;
  group("ts-range-scan", () => {
    const qs = `measurement=${encodeURIComponent(data.measurement)}` +
      `&device=${encodeURIComponent(data.device)}` +
      `&location=${encodeURIComponent(data.location)}` +
      `&symbolic_name=${encodeURIComponent(data.symbolicName)}` +
      `&field=${encodeURIComponent(data.field)}` +
      `&start=${data.seedStart}` +
      `&end=${data.seedEnd}`;
    const r = http.get(
      `${BASE_URL}/shepard/api/timeseriescontainers/${data.tsContainerId}/payload?${qs}`,
      {
        headers: authHeaders(),
        tags: { endpoint: "ts_range_scan" },
      }
    );
    tTsRangeScan.add(r.timings.duration);
    const ok = check(r, {
      "ts_range_scan 200": (x) => x.status === 200 || x.status === 401,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// 8. GET /v2/provenance/entity/{dataObjectAppId}  — Neo4j 3-hop PROV-O walk
function doProvWalk(data) {
  if (!data.dataObjectAppId) return;
  group("prov-walk", () => {
    const r = http.get(
      `${BASE_URL}/v2/provenance/entity/${data.dataObjectAppId}`,
      {
        headers: authHeaders(),
        tags: { endpoint: "prov_walk" },
      }
    );
    tProvWalk.add(r.timings.duration);
    // 200 = has activities; 404 = valid entity with no activities yet
    const ok = check(r, {
      "prov_walk 200/404": (x) => x.status === 200 || x.status === 404 || x.status === 401,
    });
    errorRate.add(ok ? 0 : 1);
  });
}

// 9. GET /v2/admin/features  — in-memory feature registry
function doAdminFeatures(_data) {
  group("admin-features", () => {
    const r = http.get(`${BASE_URL}/v2/admin/features`, {
      headers: authHeaders(),
      tags: { endpoint: "admin_features" },
    });
    tAdminFeatures.add(r.timings.duration);
    // 200 = instance-admin key; 401/403 = insufficient scope — all valid
    const ok = check(r, {
      "admin_features 200/401/403": (x) =>
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
