---
title: MFFD cube3 import slowness — diagnostic
stage: deployed
last-stage-change: 2026-05-23
audience: contributor, ops
---

# MFFD cube3 import slowness — diagnostic

Diagnosis of the v15.x MFFD import running on cube3 (DLR intranet, source side)
into nuclide-dest (`shepard-api.nuclide.systems`, destination side). The
operator reported: **only 6 DataObjects in 5+ hours despite healthy telemetry.**

Investigation is dest-side observation only — cube3 is intranet-only and not
reachable from the diagnostic worktree. Findings are grounded in the
telemetry payloads, the actual DataObject graph on nuclide, and source-code
inspection of `examples/mffd-showcase/scripts/mffd-import-v15.py` (4167 lines,
v15.7 as of commit `28a067c2`).

---

## §1 Observed symptoms

### DataObject inventory in MFFD-Dropbox (id 595088)

Pulled via `GET /shepard/api/collections/595088/dataObjects/{id}`. Six DOs
exist; **every one has zero file/TS/SD references** (confirmed for each via
`GET .../{id}/{fileReferences,timeseriesReferences,structuredDataReferences}`):

| id | createdAt (UTC) | name | refs |
|---|---|---|---|
| 595094 | 01:05:05 | Tapelaying-2026-05-23-mffd | f=0 ts=0 sd=0 |
| 599254 | 02:08:22 | WarmupProbe-2026-05-23 | f=0 ts=0 sd=0 |
| 599901 | 02:21:09 | Tapelaying-2026-05-23 | f=0 ts=0 sd=0 |
| 609607 | 04:39:29 | Bridgewelding-2026-05-23 | f=0 ts=0 sd=0 |
| 610368 | 04:41:07 | WikiDump-2026-05-23 | f=0 ts=0 sd=0 |
| 610370 | 04:41:08 | ImportScripts | f=0 ts=0 sd=0 |

The script's v15 design uses **one step-container DO** per process step
(tapelaying / bridgewelding), with all migrated payload references attached
to that step DO (`run_source_mode` L2393 + L2495..2596). So with 8 457
source DOs to migrate into the `Tapelaying-2026-05-23` step DO (599901),
the EXPECTED inventory is 6 step/scaffold DOs **with hundreds-of-thousands
of refs hanging off them**. Reality: zero refs.

### TS container 589657 has 192 series but they pre-date this session

`TimeseriesContainer 589657` ("MFFD-tapelaying-ts-2026-05-23-mffd") was
created at **2026-05-22T22:57:53Z** — i.e. about **2 h before** the current
script session started at 00:54Z. The 192 registered series in that
container are from a prior attempt (Q5/Q6 from the operator's earlier
worklog); none are from the current run. **The current session imported
zero payload during its first 4 h.**

### Telemetry tape (SD container 593753)

145 heartbeat batches over 2026-05-23T00:54..04:41Z; per-event payloads
fetched via `GET /structuredDataContainers/593753/payload/{oid}`. Event
type histogram:

| event | count |
|---|---|
| `update_available` | 137 |
| `update_ready_for_exec` | 137 |
| `process_shutting_down` | 4 |
| `process_started_with_checkpoint` | 3 |
| `process_started_fresh` | 2 |
| `applying_update` | 1 |
| **errors / warnings** | **0** |

No error events ever. Telemetry healthy.

---

## §2 The cube3-side cost model

### Per-DO round-trip count in `iter_data_objects` (L694..726)

```python
for src_do in _src.iter_data_objects(src_coll_id):
    # ↑ inside iter_data_objects, PER DO:
    #   1. GET /collections/{c}/dataObjects?page=N   (amortised: 1 per PAGE_SIZE)
    #   2. GET /collections/{c}/dataObjects/{id}/fileReferences        (1)
    #   3. GET /collections/{c}/dataObjects/{id}/timeseriesReferences  (1)
    #   4. GET /collections/{c}/dataObjects/{id}/structuredDataReferences (1)
```

That is **3 round-trips per DO purely for enrichment**, plus pagination
amortised across PAGE_SIZE (default 50). Then per-ref payload work:

- Per file ref: `download_file_ref` (L892, HEAD + chunked GET — cube3)
  + `upload_file` (L1106, POST → dest).
- Per TS ref: `export_ts` (L792, `GET .../export?csv_format=ROW`,
  timeout=300s — can return megabytes for high-rate channels)
  + `import_ts_csv` (L1719, POST multipart → dest).
- Per SD ref: `download_structured` (L833) + `upload_structured_payload`
  (L1700ish, POST → dest) + `create_structured_container` (L1500ish)
  + `link_structured_to_do` (L1600ish).

### Latency estimate

Assume cube3 → nuclide internet round-trip ≈ 200 ms + research-platform
typical 500 ms server response = **~0.7 s per round-trip**, with no
parallelism (see §5 hypothesis #1).

- Enrichment alone: 3 × 0.7 ≈ **2.1 s per source DO**.
- For 8 457 source DOs in tapelaying: 8 457 × 2.1 s ≈ **5 h** purely
  in enrichment, before any payload moves.

The empirical pacing matches this within a factor of 2:
- Tapelaying step DO created 02:21:09Z.
- Bridgewelding step DO created 04:39:29Z (signals end of tapelaying loop).
- Δ = **2 h 18 min ≈ 138 min ≈ 0.98 s per source DO** averaged across the
  3 GETs.

So the loop did iterate 8 457 source DOs in ~2.3 h. The reason no payload
landed: at the speed it's going (3 enrichment GETs per DO before any
work), and given the resume state-skip is checked **after** the per-DO
GETs (L2504 file, L2541 TS, L2570 SD), most-or-all per-DO units must
have either failed silently in dest-side write paths, or been skipped
via a state-file from a prior session.

Without cube3 SSH it isn't possible to tell which. See §7.

---

## §3 Telemetry analysis

### Heartbeat cadence

Computed deltas between adjacent heartbeats:

```
gap > 90s observations:
  00:54 → 01:04   559 s   (9.3 min)   — startup, v15.4 → 15.6 self-update
  01:05 → 02:04  3545 s  (59.1 min)   — STARTUP STALL: warmup gate + bootstrap
  02:04 → 02:09   251 s   (4.2 min)
  02:09 → 02:15   397 s   (6.6 min)
  02:15 → 02:24   541 s   (9.0 min)   — last gap; from here, perfect 60s
```

After 02:24, heartbeats are **rock-solid 60s** (30 per 30-min bucket):
the telemetry self-heal loop (L3849+) is doing its job. The startup
59-min gap is interesting — likely the warmup probe + gate-poll path
(L3475..3561) which sits in a `time.sleep(GATE_POLL_SEC)` loop waiting
for the operator to set `import_ready` on the collection.

### Counter values

The counter data lives in TS container 593750, but the dest `getTimeseries`
endpoint rejects `symbolicName=v15_6` as "must not be blank" — a known
v5 pagination control-character / underscore-in-symbolicName bug (per
project_db_optimisation_stuck.md memory). Counter time-series are
visible via the chart UI (the OBS-MFFD1 plotting path uses the same
container) but cannot be queried directly from a script in this
diagnostic window.

What can be inferred from the 192 dest series in container 589657:
**at least some `import_ts_csv` calls succeeded in a prior session**
(Q5/Q6). The current session contributed zero.

### Self-update activity

The updater (L3279..) checks every `HEARTBEAT_S` (60 s) — see the
`_tel.counter("selfupdate_checks")` at L3300. From 02:24:48 onward it
saw v15.7 available, downloaded + staged it, fired `update_available` +
`update_ready_for_exec` events on EVERY 60-s heartbeat (137 × 2 =
274 events). It only **applied** the update at 04:41:09 — when
`run_source_mode` returned (tapelaying loop done) and the `finally`
block ran `apply_pending_update` (L4154..4157).

So between 02:24 and 04:41 (2 h 17 min) the script knew an update was
ready and was waiting for the main loop to release.

---

## §4 What's different from Q6

The Q6 attempt (per operator's memory log) processed **827 files + 30 TS
in similar duration before SIGHUP-death**. Two structural differences
made Q6 faster:

1. **Q6 ran on v15.3 or earlier — before the per-DO PROV-O annotation
   block was added.** v15.1 added `annotate_do_mode_of_production` +
   `annotate_typed_predecessor` + `annotate_alternative_name` calls
   per step DO (L2422..2449). These add ≈3 SPARQL writes per STEP DO,
   not per source DO, so they're amortised — **not the issue.**
2. **Q6 did not have the v15.1 worker-pool wrapper** that wastes
   pool-spawn time without parallelising (see §5 #1). The Q6 main
   loop went straight into `run_source_mode`. **This is the issue.**
3. **Q6's TS container was empty at start** — so its `state.is_ts_done`
   skip check was always false, and every TS ref attempted import.
   Q5/Q6 left 192 series in 589657; the current session resumes against
   the state file from prior runs, so most TS refs **may** be skipped
   without any cube3 cost — but the per-DO enrichment GETs still fire
   (L709..711 before L2541's skip check). The "skip" doesn't help.

The observation "Q6 was faster" is consistent with the script having
LESS overhead per startup round in earlier versions. v15.7 added more
PROV-O wiring, redeploy-survival, self-update polling — none of which
hits cube3 per-DO, but each adds tens of seconds to startup.

---

## §5 Bottleneck hypothesis (ranked by likelihood)

### Hypothesis #1 — `run_source_mode_workers` is a fake fan-out [CONFIDENCE: HIGH]

`examples/mffd-showcase/scripts/mffd-import-v15.py` L2983..2999:

```python
with ThreadPoolExecutor(max_workers=workers) as executor:
    # Probe the executor wiring (1 trivial task per worker — verifies the
    # pool is healthy before running the real import).
    probes = [executor.submit(lambda: True) for _ in range(workers)]
    for p in probes:
        p.result(timeout=5.0)
    print(f"[workers] pool healthy ({workers} workers responded)")

    # Run the existing sequential path inside the executor context. This
    # is a deliberate v15.1 conservatism — the wiring is provable, the
    # behavior is unchanged. Tests assert both branches.
    result = run_source_mode(
        dest_client, coll_id, state, source_client,
        ...
    )
```

The comment at L2976..2982 admits it:
> "in v15.1 we ship the *wiring* + the sequential-by-default contract.
>  The actual per-task closure is identical to what `run_source_mode`
>  does inline. Because that body is ~150 lines and not factored out
>  today, the concurrent path delegates back to `run_source_mode` after
>  constructing the queue + executor (a no-op fan-out that proves the
>  wiring is in place)."

When the operator runs with `--workers 4`, the pool spawns, four `lambda: True`
probes run, then `run_source_mode` is called **sequentially inside the
executor context**. Threads 2/3/4 never receive real work. The user
believes they have 4× concurrency; they have 1×.

**Evidence ratio:** 0.98 s per source DO (§2) vs. 0.7 s minimum per
serial round-trip = 1.4× of single-thread baseline. With 4 workers
actually saturated, this should be 0.7 s ÷ 4 = ~175 ms per DO and the
tapelaying loop would finish in ~25 min, not 138.

### Hypothesis #2 — Eager per-DO enrichment defeats state-resume [CONFIDENCE: HIGH]

`iter_data_objects` at L707..711:

```python
for item in items:
    do_id = item["id"]
    file_refs = self._fetch_file_refs(coll_id, do_id)        # always 1 cube3 GET
    ts_refs = self._fetch_ts_refs(coll_id, do_id)            # always 1 cube3 GET
    structured_refs = self._fetch_structured_refs(coll_id, do_id)  # always 1 cube3 GET
    yield SourceDO(...)
```

The state-file `is_file_done` / `is_ts_done` / `is_structured_done`
checks happen INSIDE the for-loop AFTER `iter_data_objects` already
fetched all three ref-lists. So on a resumed run that's already done
80 % of the work, **the script still pays 3 cube3 round-trips per DO
just to discover everything is marked done.** For 8 457 DOs that's
25 371 cube3 GETs of pure waste on a resume.

Fix: lazy enrichment — yield a stub SourceDO with `do_id`+`name`, and
have the per-DO loop fetch refs only when at least one of file/TS/SD
work is not already done. Cuts the resume-case enrichment cost by ~95 %.

### Hypothesis #3 — Self-update apply latency [CONFIDENCE: HIGH — observability only]

Self-update detection (L3299..3311) runs in its own thread every 60 s.
Apply (`apply_pending_update`) is in the `finally` block at L4154..4157,
which only runs after `run_source_mode` returns or raises. So between
"v15.7 detected" (02:24:48) and "v15.7 applied" (04:41:09) the script
ran the v15.6 code for 2 h 17 min. This is a cost in **operator
observability** (they see "stuck on 15.6" while the script is in fact
making progress on the 15.6 loop), not throughput.

Operator-side fix: hook a "checkpoint-friendly" preempt — main loop
checks `updater.update_event.is_set()` between per-DO units of work
and gracefully exits with checkpoint after the current DO finishes,
letting the `finally`-block apply the update.

### Hypothesis #4 — Each step DO loop is whole-collection [CONFIDENCE: MED]

The script iterates ALL 8 457 source DOs serially per step. There is
no `--max-dos` cap on resume. This is by-design (importing the whole
collection is the point) but it means the operator can't get **any**
payload visible until the entire tapelaying loop has churned through
all 8 457 source DOs. With Hypothesis #2 fixed (lazy enrichment) the
resume case is fast — but the *first* full run pays full price.

Fix: process per-DO eagerly and CONTINUE the iter even if state is
partial. Currently each successful upload calls `state.mark_*_done`
(L2513, 2532, 2562, 2593) — so partial progress IS captured. But the
loop body's `tempfile.TemporaryDirectory()` is per-DO (L2498), so a
crash mid-DO loses the per-file downloads in that DO. That's not a
speed bug, it's a robustness bug.

### Hypothesis #5 — Large TS-export payloads block per-DO [CONFIDENCE: MED]

`export_ts` (L792, `?csv_format=ROW`, timeout=300s) can return many
megabytes for a single high-rate channel. With 192 series in the
tapelaying container and an unknown number of rows per series, a
single source DO with a fat TS ref can hold the per-DO loop for tens
of seconds. With Hypothesis #1 fixed (real parallelism), this would
overlap with other DOs' file uploads; today it serialises.

---

## §6 Concrete improvements

Ranked by effort × payoff:

| # | Improvement | Effort | Payoff | Cite |
|---|---|---|---|---|
| 1 | **Fix `run_source_mode_workers` to actually fan out.** Extract the per-DO loop body (L2495..2598) into a callable `_process_one_source_do(src_do, ...)` and submit one future per `src_do` from the producer. Bound the queue at 256 (as the comment already says). Per the aidocs/93 §5 budget: max 4 parallel GETs against cube3 + 4 parallel POSTs to dest. **Expected speedup: 4×** on cube3-WAN-bound work. | M (2-4 h refactor) | HIGH | L2983..2999 |
| 2 | **Lazy enrichment in `iter_data_objects`.** Yield a stub `SourceDO(do_id, name, attributes, ...)` with `file_refs=None / ts_refs=None / sd_refs=None`. Add `_lazy_fetch_file_refs(src_do)` etc. that the loop calls only when no `state.is_*_done` skip matches. On resume of a fully-done collection: **3 GETs → 0 per DO**, 8 457 DOs × 3 = 25 371 GETs saved on resume. | S (1-2 h) | HIGH on resume case | L707..711 + L2504/2541/2570 |
| 3 | **Preempt-friendly self-update apply.** In the main per-DO for-loop, check `if updater.update_event.is_set(): break` after each DO completes. Lets v15.7 take over during the loop instead of after. Observability win, no throughput change. | XS (10 lines) | MED for operator UX | L2598 |
| 4 | **Drop the per-DO `tempfile.TemporaryDirectory()` indirection.** Files can stream from `download_file_ref` directly to `upload_file`, with a `BytesIO` window or a single shared work dir. Saves a per-DO disk-create + cleanup cycle (~5 ms × 8 457 = 42 s total — small, but not zero). | S (1 h) | LOW | L2498 |
| 5 | **Surface "blocked on cube3 round-trip" as a gauge.** Add `gauge_cube3_inflight_requests` + `gauge_cube3_p95_latency_ms` so the next time this happens the operator sees the bottleneck location in the chart UI (per OBS-MFFD1's `mffd-import-stats-collector` pattern). | XS (30 min) | HIGH for future diagnosis | new Telemetry helper |
| 6 | **Document the workers=N gotcha in the script header.** Until #1 ships, `--workers 4` is a footgun: it COSTS startup-time without buying parallelism. Print a warning at startup if `workers > 1 AND <real-fanout-not-shipped>`. | XS (5 lines) | MED | L2964 |

---

## §7 What I CAN'T diagnose from nuclide-side

These require cube3 SSH or operator-side log access:

1. **cube3 response latency.** Whether `_request_with_retry` is hitting
   the 60-s timeout, retry-storming on 502/503, or just paying clean
   ~700 ms per round-trip. Need cube3-side request log or local
   `curl --connect-timeout 5 https://backend.bt-au-cube3.intra.dlr.de/health`
   from the dev box.

2. **state.json contents.** Specifically: `completed_files`,
   `completed_ts`, `completed_sds` lists, plus the `ts_container`
   mapping. If these are populated from a prior session, that explains
   why current-session DO refs are 0 (everything is state-skipped) —
   but the per-DO enrichment GETs still ran for 2.3 h to discover that.
   Need access to the state file on cube3.

3. **`mffd-import-2026-05-23-Q*.log` stdout/stderr.** The script's
   per-DO `do_bar.write(...)` prints (L2484, 2493, 2505, 2510, etc.)
   would say `[skip-file] X` / `[ok-file] Y` / `[error-file] Z`.
   Reading this log directly would distinguish "skip-storm because of
   state" from "error-storm because of broken upload" from "silent
   eat from unreached path".

4. **cube3 Shepard pagination control-char query bug.**
   `getTimeseries.symbolicName="v15_6"` is rejected as blank — known
   v5 bug. Without it, counter time-series values from the telemetry
   container can't be machine-read. Operator can see them via the chart
   UI (the OBS-MFFD1 pipeline plots them) but a script can't poll them.
   This is a separate Shepard backend bug (`project_db_optimisation_stuck.md`).

5. **Garage upload-url latency.** If `garage_preflight` (L1173) or the
   `presigned-URL upload flow` (L1167) is the bottleneck on dest-side
   uploads, the diagnostic would need timing data from cube3 (where
   the script runs) since the dest-side never receives a "blocked
   upload" event.

---

## §8 Summary

| Finding | Confidence | Source-code citation |
|---|---|---|
| **#1 fake worker fan-out** — `--workers N` doesn't parallelise | HIGH | L2983..2999, comment at L2976..2982 |
| **#2 eager enrichment defeats resume** — 3 cube3 GETs per DO before any skip-check | HIGH | L707..711 vs L2504/2541/2570 |
| **#3 self-update apply gated by main loop** — observability cost | HIGH | L3344..3346 + L4154..4157 |
| **#4 zero payload imported this session** — 0 refs on every dest DO | HIGH | live API probe |
| **#5 v15.6 ran for ~2.3 h then handed off to v15.7** | HIGH | telemetry events 02:24..04:41 |
| **#6 192 series in TS 589657 are from a prior session** | HIGH | TS container createdAt = 2026-05-22T22:57Z |

**Top 2 recommended fixes:**

1. **Fix #1 — actual worker fan-out.** Extract the per-DO loop body
   into a callable, submit one future per source DO, cap concurrency
   at 4 cube3 + 4 dest per aidocs/93 §5. Expected 4× speedup on
   WAN-bound work. Effort: M (2-4 h refactor).
2. **Fix #2 — lazy enrichment.** Defer `_fetch_file_refs/_fetch_ts_refs/
   _fetch_structured_refs` until after state-skip-check. Resume case
   goes from 3 GETs per DO to 0 for already-done DOs. Effort: S (1-2 h).

Both fixes are independent and stackable; #2 alone helps the resume
case dramatically, #1 alone helps the first-run case.

Together they would take the current 5+ hour "0 payload imported"
state to a few minutes for resume / under an hour for first run.
