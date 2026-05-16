# Performance testing + auto-tuning — design

**Status.** Concept design (k6 smoke shipped in PERF1; stress +
recommender + auto-tuning TUI step queued).
**Snapshot date.** 2026-05-12.
**Audience.** Contributors + operators. Casual-user-shaped: a
fresh deploy should self-check + suggest tuning, not require a
deep dive into Prometheus before the first connection lands.

## 1. The problem

shepard's full stack (Quarkus + Neo4j + Mongo + TimescaleDB +
PostGIS) has a wide tunable surface — JVM heap, Neo4j page-cache,
TimescaleDB chunk-interval, the permissions-cache TTL, Hibernate
batch sizes, the worker-thread pool. Operators rarely know which
knobs matter for their workload; the upstream `application.properties`
ships defaults aimed at a "modest team" install.

Two consequences:

1. **A poorly-tuned install reads slow.** The casual user blames
   shepard, not the box.
2. **A well-tuned install is invisible.** Operators have no
   feedback loop telling them "you're 30% off the latency you
   could have."

This design closes both gaps with three additive pieces:

- **k6 stress + smoke scripts** under `scripts/perf/`. Operators
  run them ad-hoc after deploy; CI runs them on every release
  candidate.
- **A recommender** that reads the run results + the live
  Prometheus snapshot (per the `monitoring` profile from PERF1)
  and surfaces 3-5 concrete tuning suggestions.
- **An optional install-TUI step** (gated behind a flag — slow,
  not for everyone) that runs the recommender, applies safe
  suggestions, re-runs the smoke, and lands the install on a
  measurably-good config.

The recommender + TUI step are **post-PERF1**; PERF1 is the
out-of-the-box Grafana dashboard + the smoke script.

## 2. What k6 buys us

[k6](https://k6.io) is Grafana's load-testing tool — a small Go
binary that runs JavaScript scenarios. Apache-2.0 (passes
`CLAUDE.md`'s licence deny-list).

Why k6 over the alternatives:

- **Single static binary.** No JVM warmup or Python ramp-up; the
  numbers compare cleanly across runs.
- **Native Prometheus remote-write.** A run can publish its
  metrics directly to the `monitoring` profile's Prometheus, so
  the Grafana dashboard from PERF1 grows a "load-test run"
  annotation overlay for free.
- **Cloud-free.** k6 Cloud exists as a hosted offering; we don't
  use it — everything runs against the operator's stack.
- **Thresholds as code.** The script declares `p(95)<500ms` as a
  fail condition; non-zero exit code on miss makes it CI-friendly.

Alternatives surveyed: Locust (Python), Gatling (Scala / heavy
JVM), JMeter (Java / GUI-flavoured), wrk2 (C / no scenario
language). All workable; k6 wins on JS-as-scenario-DSL +
Prometheus integration. Locust is the runner-up if the casual
operator demographic skews more Python than JS.

## 3. The three scripts

### 3.1 `scripts/perf/k6-smoke.js` — PERF1 (shipped)

5 VUs × 30 s; hits the public `/versionz`, the auth'd
`GET /shepard/api/collections`, and the auth'd
`POST /shepard/api/collections/search`. Thresholds match the
`aidocs/42` casual-user latency budget:

- `p(95) ≤ 200ms` on `/versionz`
- `p(95) ≤ 500ms` on listings
- `p(95) ≤ 1500ms` on search
- `< 1%` HTTP failure rate

Non-zero exit on threshold miss. The run leaves a `last-run.json`
behind for the recommender (§4).

### 3.2 `scripts/perf/k6-stress.js` — PERF2a (queued)

Multi-stage ramp:

- Stage 1 (1 min): 0 → 25 VUs warm-up.
- Stage 2 (3 min): hold at 25 VUs steady-state.
- Stage 3 (1 min): 25 → 50 VUs peak.
- Stage 4 (2 min): hold at 50 VUs, exercising file upload +
  timeseries ingest + paginated search + permissions-checked
  reads.
- Stage 5 (1 min): 50 → 0 VUs cool-down.

Exposes per-endpoint p50 / p95 / p99 latency Trends; per-stage
`http_reqs` rates; permissions-cache hit-rate (read from a
`recommender_hit_ratio` Counter pulled out of the Prometheus
scrape). Designed to surface bottlenecks the smoke can't:
file-handle exhaustion, GC pause storms, Neo4j page-cache misses.

### 3.3 `scripts/perf/k6-soak.js` — PERF2c (queued, optional)

Low-VU long-duration soak (5 VUs × 2 h) to catch memory leaks /
JDBC connection-leak / Mongo connection-pool drift. Off-by-default
because it's slow.

## 4. The recommender — `scripts/perf/recommend.py` — PERF2b

Takes two inputs:

1. `scripts/perf/last-run.json` (from k6).
2. Live Prometheus snapshot via the `monitoring` profile's
   `http://localhost:9090/api/v1/query` (instant queries for JVM
   heap, GC pause, permissions-cache hit ratio, MongoDB command
   latency).

Outputs a markdown report `scripts/perf/recommendations.md` with
3-5 concrete suggestions, ranked by expected-impact × ease.

Rule catalogue (initial; each rule is a small Python function):

| Rule | Trigger | Suggestion |
|---|---|---|
| `R-jvm-heap-low` | `jvm_memory_used_bytes{area=heap}` > 85% of max during stress | Bump `-Xmx` from default to **2 × current**; document `JVM_HEAP_MAX` env var. |
| `R-gc-pauses-frequent` | `jvm_gc_pause_seconds_sum` rate > 0.05 s/s during stress | Switch from default ParallelGC to **G1GC** (`JVM_GC_TUNING=g1gc`). |
| `R-perms-cache-cold` | `cache_gets_total{result=hit}/total` < 60% over 5 min stress | Increase `shepard.cache.permissions-service.maximum-size` from 10k to **50k**; raise TTL from 5 min to **15 min**. |
| `R-mongo-latency` | Mongo command p95 > 50 ms | Suggest reviewing Mongo connection-pool size (`quarkus.mongodb.max-pool-size`); document Mongo `wiredTiger.cacheSizeGB` tuning. |
| `R-hibernate-batch` | Hibernate session events > 100 / s sustained | Suggest `quarkus.hibernate-orm.jdbc.statement-batch-size=25` (currently default). |
| `R-thread-pool` | HTTP queue length (`vertx_thread_pool_queued`) > 50 sustained | Raise `quarkus.thread-pool.max-threads`. |
| `R-search-slow` | `http_req_duration{endpoint:search} p(95)` > 1500 ms | Check Neo4j heap + page-cache; suggest `NEO4J_dbms_memory_pagecache_size`. |

Each suggestion in the output carries:

- The Prometheus query that fired it (so the operator can
  verify).
- The exact env var / property change.
- A "safe to apply automatically?" boolean (used by the install
  TUI in §5).
- A link to the relevant `docs/admin.md` section.

Recommender is a single ~300-line Python script. Tested against
`last-run.json` fixtures captured from known-bad and known-good
runs.

## 5. Optional install-TUI auto-tuning step — PERF2d (queued)

Add a final optional step to the install TUI (`aidocs/22` L1
admin-CLI Phase 1):

```
[ Optional ] Run k6 smoke + auto-tune?  (≈3 min, may restart backend)
  [ y ] Yes — apply safe suggestions and re-run
  [ N ] Skip
```

Flow:

1. TUI starts `docker compose --profile monitoring up -d` (if not
   already up).
2. Runs `k6-smoke.js` against the local stack.
3. Calls the recommender; receives a list of suggestions tagged
   safe-to-apply.
4. Writes safe suggestions to `infrastructure/.env` (preserving
   any operator-modified values via a `.env.orig` backup); restart
   the backend container.
5. Re-runs `k6-smoke.js`; reports the delta in p95 latency.
6. If thresholds now pass: success. If they fail or got worse,
   the TUI offers to revert from `.env.orig`.

**Safe-to-apply** means: pure env-var changes that don't touch
database schemas or persist data; reversible by editing `.env`
back. The TUI never edits property files in the JAR. Unsafe
suggestions (database tuning, Neo4j page-cache sizing) are
**printed but not applied** — the operator runs them through the
documented runbook.

## 6. CI integration — PERF3 (queued)

A new workflow `.github/workflows/perf-smoke.yml` runs the smoke
script weekly on `main` against a CI-booted compose stack (same
shape as the `aidocs/49` screenshot pipeline). Failing thresholds
post an issue (one per recommender finding) so regressions get a
ticket without manual review. Off the critical path; PRs aren't
gated by perf.

## 7. Phasing

| ID | Slice | Size | Gate |
|---|---|---|---|
| **PERF1** | Out-of-the-box monitoring profile + Grafana dashboard + k6 smoke | S | shipped — this batch |
| **PERF2a** | k6-stress.js multi-stage ramp | M | none |
| **PERF2b** | recommender (Python) — rule catalogue | M | PERF2a (needs realistic stress data) |
| **PERF2c** | k6-soak.js + Prometheus remote-write integration | S | PERF2a |
| **PERF2d** | Install-TUI auto-tune step | M-L | L1 Phase 1 admin-CLI + PERF2b |
| **PERF3** | Weekly CI perf-smoke + issue auto-file | S | PERF2a |

## 8. Risks

- **Recommender false-positives.** A 25-VU k6 stress doesn't
  reflect real workload. Mitigation: every rule cites the
  Prometheus query so operators can disagree; safe-to-apply is a
  small subset.
- **k6 binary licensing.** Apache-2.0 — clean.
- **TUI step length.** 3 min is on the edge of "wait, what?" for
  a fresh install. Mitigation: opt-in, with a progress bar; a
  skip option that's prominent.
- **Knob interaction.** Heap × GC × thread-pool can interact
  non-linearly. Mitigation: the recommender applies suggestions
  **one at a time**, re-runs the smoke between, and abandons the
  loop after 3 rounds.

## 9. Open questions for the maintainer

1. **k6 vs Locust** for the post-PERF1 stress script? (Default:
   k6. Locust would suit Python-shop operators better.)
2. **Recommender language** — Python (matches `scripts/`'s
   existing Python tooling) vs Go binary (single artifact). Default
   Python.
3. **Auto-tune as TUI step** — yes/no? The user asked "maybe we
   even make the optimisation of parameters an optional install
   tui step." Recommendation: yes, but opt-in with a clear
   "applied changes" review screen before commit.
4. **Soak test** — ship `k6-soak.js` at all, or document it as a
   one-off the operator writes when they need it? Default: ship
   it (off-by-default).

## 10. Cross-references

- `docs/admin.md §Performance metrics` — Grafana / Prometheus
  setup (PERF1 — shipped this batch).
- `aidocs/22` — admin CLI / install TUI (PERF2d hooks in).
- `aidocs/42 §"Where it's going"` — vision doc near-horizon.
- `aidocs/16` — backlog rows PERF1..PERF3.
- `aidocs/55` — provenance dashboard (sibling casual-user
  surface; both feed the same Grafana).
- `aidocs/51 §9.5` — admin-page metrics strip (A3b1) — could
  surface the latest k6 smoke result in the same strip
  ("p95 latency last run: 320ms — within budget").

## 11. What this isn't

- Not a production load-test framework. Operators with serious
  perf needs run their own JMeter / Gatling rigs.
- Not auto-tuning every knob. The recommender ships ~7 rules;
  hand-tuning for Neo4j page-cache, Mongo wiredTiger, and
  TimescaleDB chunk intervals stays a documented runbook step.
- Not a replacement for an SRE practice. The dashboard +
  smoke + recommender raise the floor; they don't define the
  ceiling.
