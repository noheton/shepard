---
title: Import a collection from a DLR-internal Shepard instance
description: Pull DataObjects, files, timeseries and structured-data references from an upstream Shepard (v5.x) into this fork — with provenance, resume-on-crash and live observability.
permalink: /help/importing-from-dlr-cube3/
layout: default
audience: admin
stage: deployed
last-stage-change: 2026-05-23
---
# Import a collection from a DLR-internal Shepard instance

This page is for an operator who wants to bring data from an
upstream-shepard 5.x instance (DLR `cube3` was the field test; any
upstream-compatible Shepard works) into a Shepard fork instance. The
worked example uses the live MFFD ingest, but the same workflow runs
against any pair of instances.

For the full operator reference (every flag, every env var, every
exit code) see
[`examples/mffd-showcase/scripts/README.md`](https://github.com/noheton/shepard/blob/main/examples/mffd-showcase/scripts/README.md).

## What gets carried over

Per source DataObject: name + description + attributes + predecessor /
successor links + every `FileReference` (multi-OID bundles
preserved) + every `TimeseriesReference` (channels + data points,
exported in ROW format) + every `StructuredDataReference`. Snapshots
and semantic repositories are **not** ferried — snapshots are
human-fired at boundaries; `prov-o` and friends are preseeded on the
dest.

## Prerequisites

- Network reach to BOTH source and dest (MFFD: from the cube3 VM,
  which sees `backend.bt-au-cube3.intra.dlr.de` AND
  `shepard-api.nuclide.systems`).
- Two Shepard-issued JWTs: `SHEPARD_API_KEY` (or
  `SHEPARD_BEARER_TOKEN` for Keycloak) + `SOURCE_SHEPARD_API_KEY`.
- The source collection IDs (MFFD: `SOURCE_TAPELAYING_COLL_ID=48297`,
  `SOURCE_BRIDGEWELDING_COLL_ID=163811`).
- A FAIR R1 license + access-rights default (MFFD: `proprietary` +
  `restricted access`). The script refuses to start without these.
- `uv` or `python3 ≥ 3.11`, plus `tmux` so SSH-drop doesn't kill the run.

## Step 1 — bootstrap once

The bootstrap pass creates the dest collection, the skeleton
DataObjects, the `ImportScripts` DO (where the script self-uploads),
and prints reminders for the t=0 snapshot. It's safe to re-run. Use
the [Quick start in the scripts
README](https://github.com/noheton/shepard/blob/main/examples/mffd-showcase/scripts/README.md#quick-start-4-lines-mffd-config-baked-in)
— it carries the live MFFD JWTs and adds the `--bootstrap` flag to
the single invocation. Capture the printed collection id (515365 on
nuclide) and manifest container id (`fileContainer/473932`); Step 2
uses them via env defaults.

## Step 2 — kick off the full import

Open a `tmux` window on the source-side machine. Set the source
credentials + collection ids, the dest credentials, and the FAIR
defaults. The 4-line invocation from the [scripts
README](https://github.com/noheton/shepard/blob/main/examples/mffd-showcase/scripts/README.md#quick-start-4-lines-mffd-config-baked-in)
already wires everything up; the only thing you may want to change
is `--workers` (default 1, MFFD ran at 4) and `--max-dos N` for a
sample run:

```bash
export SHEPARD_URL=https://shepard-api.nuclide.systems
export SHEPARD_API_KEY=eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJlZTRjMDEwZi1kNjQ4LTQ2MzAtYWVhNi1iODFlZjJhOWMyOTYiLCJpc3MiOiJodHRwOi8vc2hlcGFyZC1hcGkubnVjbGlkZS5zeXN0ZW1zLyIsIm5iZiI6MTc3OTQzMTY1MiwiaWF0IjoxNzc5NDMxNjUyLCJqdGkiOiI3NmMyMGM2MC1hMzVmLTQwMjQtODdhNi0xYjU0ZGRiMzcxZWIifQ.ZBY9YQZyje_ketIGB2za50H76XR-oYmCWy6wHdySBX3o2mhWgGCASrjjkmIyRDwlmQfM4MR-BtTUzS7Vp1XTROERu3AbiF-y-7CWmxHWvP0NVJ1Cl_EjdcXJjztnU8rjb-jTY5t1WOQeSgBszMDq8cwNY-67w4Xj5tyvQRq7i928kIHFiepfKg6mCHo6JVHMIdJyUHKri9J1GmbopdM7pdpN074BYxYzZQ8qCgMDN2MrMq37HjDwFrhhu1y7BPDJuglCXdM0jtU--L5aSZyENcMCiwZQyPf6Bf3AX7ddY2EDsNtB7xFgeJ7XtHVTs4yItHZmm0TTdb1-Q7lFQ19-Cg
export SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de
export SOURCE_SHEPARD_API_KEY=eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJrcmViX2ZsIiwiaXNzIjoiaHR0cDovL2JhY2tlbmQuYnQtYXUtY3ViZTMuaW50cmEuZGxyLmRlL3NoZXBhcmQvYXBpLyIsIm5iZiI6MTc3OTQ4MzM1NCwiaWF0IjoxNzc5NDgzMzU0LCJqdGkiOiJjMjg0ZmE1Mi1lNTcwLTRjNjQtODI5Ny05NzM3YmU4M2M1MTAifQ.gRrUGsvppocO-2BBkPrMaTqKkIzT2A3xzYvO0qFTBwjbJ_4kxu7n2OouTlBVYufQreoNLViES8dN6g-zrPwohE9xud3JLFsAmsFzJq0sj3JCS7RfAVkOrVgluxuG9QrMxh_RLbKcyoeK3UdiIkVUd_OjzMuQMIkSvMyyWiw-Mk5Tiu5zAvmPMMYmJFYvAAhnKFu-KPAr7Dqm4CocUTI_afF4i2nc9ytyBfg55pyQM-k9BzmDVd8fFs_jku85hAeVrkNfXS8rOhwJp9h2A8kRCEnihNhNafPc-Uf5dKW8nkn7K048WtDRS5oWKyHJFc2tVkAwwvR3v7SfLYXewYRZVg
export SOURCE_TAPELAYING_COLL_ID=48297
export SOURCE_BRIDGEWELDING_COLL_ID=163811
export SESSION_ID=2026-05-23-mffd
export MFFD_DEFAULT_LICENSE=proprietary
export MFFD_DEFAULT_ACCESS_RIGHTS='restricted access'
bash examples/mffd-showcase/scripts/mffd-runner.sh --max-dos 10 --workers 4   # sample
bash examples/mffd-showcase/scripts/mffd-runner.sh --workers 4                # full
```

The `mffd-runner.sh` wrapper restarts the script on transient errors
(network blips, dest redeploys), enforces a crash-loop limit (≥5
non-clean exits in 60s = stop), and applies a self-update without
operator action when a new script version lands in the dest's
manifest container.

## Step 3 — clear the review gate

After the smart warmup the script creates a `WarmupProbe-<session>`
DataObject in the dest, uploads one probe payload of every
reference type, then waits for `attributes.import_ready = <SESSION_ID>`
on the dest collection.

Review the probe DO in the dest UI. When it looks correct, set the
flag:

```bash
curl -X PATCH \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"attributes\":{\"import_ready\":\"$SESSION_ID\"}}" \
  $SHEPARD_URL/shepard/api/collections/515365
```

The script polls every 8s, sees the change, and continues.

If you walk away and the 60-minute timeout fires, the script exits 1
→ the runner retries → the probe step is skipped (already done) and
the gate polling resumes.

## Step 4 — watch the import land

Open the dest collection. Find the `ImportStats-<session>-mffd`
DataObject (created by the [sibling stats
collector](observing-an-import.md)) and plot
`dos_total`, `file_bytes_total`, and `garage_bucket_bytes` from its
`import-progress-counters` timeseries reference.

The importer itself also writes counters into the manifest container's
sibling `timeseriesContainer/593750` — every counter and gauge is
plottable from the dest UI without leaving Shepard.

## Step 5 — fire the post-import snapshot

The script does NOT auto-fire snapshots (per the snapshot-boundary
policy — humans decide when a forging stage ends). On completion it
prints two reminders with the exact `POST /v2/collections/<appId>/snapshots`
calls for the pre- and post-import boundaries. Fire the pre-import
snapshot before clearing the review gate and the post-import
snapshot once you're satisfied with the ingest shape — together
they form the provenance boundary for any later mutation pass.

## When things go wrong

The full troubleshooting list is in the
[scripts README](https://github.com/noheton/shepard/blob/main/examples/mffd-showcase/scripts/README.md#troubleshooting),
but the three most common operator-side issues:

- **`401` mid-run** — JWT expired (kreb_fl tokens last ~14h). The
  script pauses every worker, prints the running PID, and waits.
  Re-mint, `export SOURCE_SHEPARD_API_KEY=<new>`, then `kill -CONT <pid>`.
- **Runner stops after a few restarts** — the crash-loop limit
  tripped. Read `mffd-import-<session>.log` to see the real cause;
  fix and re-run. The script resumes from checkpoint.
- **Exit code 8** — source has placeholder rows with empty payloads
  (the IMPORT-Q7 case). Operator must fix the source data state;
  the importer will not ferry zero-byte files.

## Related

- [Watch an import land in real time](observing-an-import.md) — the
  per-DataObject-kind influx chart (sibling stats collector).
- [`examples/mffd-showcase/scripts/README.md`](https://github.com/noheton/shepard/blob/main/examples/mffd-showcase/scripts/README.md)
  — full operator reference (every flag, every env var, every exit
  code, every troubleshooting recipe).
- Backlog rows `IMPORT-SU1` through `IMPORT-TLOOP` in
  [`aidocs/16-dispatcher-backlog.md`](https://github.com/noheton/shepard/blob/main/aidocs/16-dispatcher-backlog.md)
  — design history of the patterns documented above.
