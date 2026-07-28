---
stage: feature-defined
last-stage-change: 2026-07-28
---

# k6 LARGE-DATA baseline — first post-tapelaying-ingest perf run (2026-07-28)

First run of the `LARGE-DATA-K6-COVERAGE` scenarios (`scripts/perf/k6-endpoints.js`)
against the deployed backend **after** the MFFD tapelaying TPS ingest completed
(258,751-ref DataObject live) and **after** the GETDO-DETAIL-TARGETED deploy.

Target: `https://shepard.nuclide.systems` (nuclide dev box, single node, shared with
Neo4j/Timescale/Mongo). Scenarios: `steady` (10 VUs / 2 min), `ramp` (→50 VUs / 6 min),
`spike` (100 iters/s, 150 VUs / 30 s). Raw: `scripts/perf/last-run.json` (gitignored).

## What I found

**Headline: the 258k-ref DataObject detail page is OPENABLE again** — the state the
GETDO-DETAIL-ON2 family set out to fix. Quiescent, it returns **HTTP 200 in ~3.1–3.4 s**
(previously it spiraled the backend for minutes and was effectively unopenable).

### Quiescent single-request (no load) — the honest per-request cost

| Endpoint | Time |
|---|---|
| v2 DO detail, **258,751-ref** DO | **3.1 – 3.4 s** (200) |
| v2 DO detail, **<10-ref** DO (same collection) | **1.8 s** (200) |

Only ~1.5 s of the large DO's cost is attributable to its fan-out. **~1.8 s is a
constant cost paid by every DataObject detail read regardless of size.**

### Cypher-level (PROFILE, live, same DO) — the deployed fix works

| Detail-neighborhood form | DbHits | Time |
|---|---|---|
| Old negative `NONE(has_reference)` filter | **258,789** | 66 ms |
| **Deployed** positive edge-type allowlist | **43** | 14 ms |

~6,000× fewer db-hits, flat in reference degree. Row counts 18 → 16: the only dropped
edges are `created_in_month` + `has_permissions`, both OGM-**unmapped** on DataObject —
empirically confirming the static byte-compat argument that shipped the fix.

### Under load (p95, spike-dominated)

| Endpoint tag | p95 | avg |
|---|---|---|
| `ep_large_do_detail` | 39.0 s | 13.7 s |
| `ep_large_ref_list` | 9.2 s | 4.8 s |
| `ep_large_ts_channels` | 2.8 s | 1.4 s |
| `ep_large_coll_do_list` | **0.66 s** | 0.24 s |
| `collections_list` (spike / steady) | 34.4 s / 3.0 s | — |
| `v2_do_list` (spike / steady) | 33.2 s / 1.7 s | — |

Overall `http_req_failed` **5.26 %** (240 / 4,559); checks 97.8 % pass.
Several latency thresholds crossed — expected for a 100 iters/s spike against a
single shared dev node, but the *steady* numbers (3.0 s `collections_list`,
1.7 s `v2_do_list`) are the ones worth acting on.

`ep_large_coll_do_list` at **0.66 s p95** on the 8,483-DO collection is the
SUPERNODE-F2-COLLECTION-DETAIL fix holding up under load.

## Opportunities

1. **`GETDO-DETAIL-SHEPARDID-INDEX` is now empirically substantiated.** `SHOW INDEXES`
   confirms **no `shepardId` index** on `:DataObject` (only appId/deleted/id/
   embargoEndDate/typedPredecessorsJson). Every `findByShepardId*` detail/list seed
   scans all 13,628 DataObjects. This is the prime suspect for the **~1.8 s constant
   cost** measured on a <10-ref DO. A `RANGE INDEX … ON (d.shepardId)` should collapse
   it — but it shifts the planner for every `findByShepardId*` path, so it needs its
   own PROFILE sweep (already filed, `aidocs/16`).
2. **`ep_large_ref_list` (9.2 s p95)** — paging 258k references is the next fan-out
   surface after the detail load.
3. Re-run this baseline after any index change to get a true before/after.

## Ideas

- Wire this k6 run into a scheduled (not per-PR) perf job so the LARGE_* numbers
  become a tracked series rather than a one-off.
- Add `ep_large_do_detail` quiescent latency as an explicit regression guard —
  it is the single most user-visible number in the MFFD dataset.

## Real-world impact

An operator opening the Tapelaying DataObject gets a page in ~3 s instead of a hung
backend. That is the difference between "the MFFD dataset is browsable" and "don't
click that". The residual constant cost means *every* DataObject detail page is ~1.8 s
slower than it needs to be — a whole-instance UX tax, not an MFFD-specific one.

## Gaps & blockers

- The spike-scenario p95s are **not** a clean signal for endpoint quality — they mostly
  measure the dev box saturating. Treat steady + quiescent as the real numbers.
- `ep_large_ts_channels` ran against a container that returned 404 at setup, so the TS
  scenarios partly self-skipped; the 2.8 s figure is thin evidence.
- No before/after for the GETDO deploy at the *HTTP* layer — the pre-fix endpoint was
  unopenable, so there is no comparable baseline (only the Cypher-level PROFILE pair).

## What surprised me

The GETDO fix delivered a ~6,000× reduction in db-hits, yet the endpoint still takes
3.3 s — and a nearly-empty DataObject still takes 1.8 s. **Fixing the O(K) fan-out
revealed a large O(1)-per-request constant that had been hidden behind it.** The
un-indexed `shepardId` seed that agent B flagged as a "residual" in the PROFILE is, at
the HTTP layer, now the *dominant* cost. Good reminder that a query-level win does not
automatically become a user-visible win.
