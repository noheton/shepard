# mffd-import-v15 — MFFD cross-instance importer

`mffd-import-v15.py` is the field-validated cross-instance Shepard importer used
to ferry MFFD data from the DLR-internal `bt-au-cube3` instance to
`shepard.nuclide.systems`. It is the reference implementation that the
in-tree `shepard-plugin-importer` (task `#126`,
[aidocs/integrations/95](../../../aidocs/integrations/95-shepard-plugin-importer-patterns-from-v15.md))
is being distilled from.

Key properties — every one of these has been exercised by the 8462-DataObject
MFFD ingest (Q1 2026):

- **cross-instance pull**: source = DLR cube3 v5.4.0 upstream, dest = nuclide fork
- **self-update**: heartbeat polls a JSON manifest in the dest, downloads + sha256-verifies a new `.py`, and `os.execv`s itself at a graceful checkpoint boundary
- **observability into Shepard itself**: counters → `TimeseriesContainer`, structured events → `StructuredDataContainer` (so the import is plottable in Shepard's own chart UI — same discipline as [`docs/help/observing-an-import.md`](../../../docs/help/observing-an-import.md))
- **redeploy-resilient**: JWT expiry pause + `SIGCONT` resume; `SIGHUP` ignored so SSH-drop doesn't kill the import; bounded exponential backoff with jitter on transient errors
- **resume from kill -9**: atomic JSON checkpoint (`tmp + fsync + rename`); restart skips already-completed DOs / files / TS references
- **wire-shape fail-fast**: smart warmup phase probes auth + read + write + Garage on both sides BEFORE the main loop, with one exit code per failure class

This document is the operator reference for the live script. The companion
user-facing task page is
[`docs/help/importing-from-dlr-cube3.md`](../../../docs/help/importing-from-dlr-cube3.md).

## Quick start (4 lines, MFFD config baked in)

In a `tmux` session on the dev box that can reach BOTH `shepard.nuclide.systems`
AND `backend.bt-au-cube3.intra.dlr.de` (currently: the cube3 VM with the
ImportScripts bundle):

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
bash mffd-runner.sh --workers 4
```

That's it. The runner restarts on transient errors, the script self-updates from
the manifest in `fileContainer/473932`, counters flow into
`timeseriesContainer/593750`, run-log events flow into
`structuredDataContainer/593753`. The MFFD-Dropbox collection on nuclide is
appId `019e4e56-ca63-76f3-9bf0-6681f7fe6d56` (id 515365).

If you're not running the MFFD ingest, see [CLI flags](#cli-flags) and
[Env vars](#env-vars) below for the operator-supplied values.

## CLI flags

All flags are optional. The script picks a mode from env vars
(`SOURCE_*_COLL_ID` set → SOURCE mode; otherwise LOCAL mode).
`--bootstrap` and `--verify-imported` are exclusive top-level modes.

| Flag | Default | When to use |
|---|---|---|
| `--bootstrap` | off | One-time: create the dest collection + skeleton DOs + self-upload + t=0 snapshot reminder. Run once before the first real import. No license/access-rights required. |
| `--dry-run` | off | Print the resolved config + planned actions, make zero API calls. Cheap way to confirm env vars are picked up correctly. |
| `--max-dos N` | `0` (unlimited) | Cap source DOs per step. Use `--max-dos 10` for a sample run before unleashing all 8462 DOs. |
| `--default-license SPDX` | (env `MFFD_DEFAULT_LICENSE`) | **REQUIRED for SOURCE/LOCAL imports** (FAIR R1). SPDX id or any literal (e.g. `CC-BY-4.0`, `proprietary`). |
| `--default-access-rights RIGHTS` | (env `MFFD_DEFAULT_ACCESS_RIGHTS`) | **REQUIRED for SOURCE/LOCAL imports** (FAIR R1.1). E.g. `'restricted access'`, `'public-with-attribution'`. |
| `--name-mapping CSV` | none | Path to a `source-name,operator-name` CSV. When a source DO matches, the dest DO gets the operator's preferred name + the original survives as `dcterms:alternative`. |
| `--workers N` | `1` | Concurrent worker count. `1` = sequential (byte-for-byte v15 behavior). MFFD ingest runs at `--workers 4`. |
| `--verify-imported` | off | Read-only walk of the dest collection, count DOs / refs by kind, DAG-reachability spot-check. Writes `mffd-verify-<session>.json`. License/access-rights not required. |
| `--smart-warmup` | on | Run the IMPORT-W1/W2/W3 phase before the main loop. Probes auth + read + write + wire-shape on both sides, with throwaway DOs cleaned up before every exit. |
| `--legacy-warmup` | — | Opt back to the v15.1 single read-only warmup. Use only when investigating a smart-warmup false-positive. |

Mode-selection rules in `main()` (line 3686): SOURCE if either
`SOURCE_*_COLL_ID` env is set; cross-instance if `SOURCE_SHEPARD_URL` differs
from `SHEPARD_URL`; otherwise LOCAL.

## Env vars

Defaults grepped from the v15.7 source. Every one of these is read at module
load (lines 156-203, 3749-3751) so changing them mid-run requires a restart
(except `SOURCE_SHEPARD_API_KEY` / `SHEPARD_API_KEY` — see [JWT expiry](#jwt-expiry-pause--sigcont-resume)).

**Destination (required)**

| Var | Default | Notes |
|---|---|---|
| `SHEPARD_URL` | `https://shepard.nuclide.systems` | Dest Shepard. Stripped of trailing `/`. |
| `SHEPARD_API_KEY` | `""` | X-API-KEY header (Shepard-issued JWT). |
| `SHEPARD_BEARER_TOKEN` | `""` | Authorization: Bearer (Keycloak OIDC). Takes precedence over `SHEPARD_API_KEY`. |
| `COLLECTION_NAME` | `MFFD-Dropbox` | Dest collection name. Looked up by name; created if absent in `--bootstrap`. |
| `SESSION_ID` | today's `YYYY-MM-DD` | Run identifier; used in log filenames, snapshot labels, `import_ready` gate value, name-disambiguation suffixes. |
| `OPERATOR` | `""` | Free-text operator id stamped onto provenance attrs. |

**Source (cross-instance pull)**

| Var | Default | Notes |
|---|---|---|
| `SOURCE_SHEPARD_URL` | falls back to `SHEPARD_URL` | Source instance base URL. |
| `SOURCE_SHEPARD_API_KEY` | falls back to `SHEPARD_API_KEY` | Source X-API-KEY (the DLR intranet JWT). |
| `SOURCE_TAPELAYING_COLL_ID` | unset | Source collection id for tape-laying — `48297` on cube3. |
| `SOURCE_BRIDGEWELDING_COLL_ID` | unset | Source collection id for frame/bridge welding — `163811` on cube3. |

**FAIR R1 license + access-rights (required for import paths)**

| Var | Default | Notes |
|---|---|---|
| `MFFD_DEFAULT_LICENSE` | unset | Fallback when `--default-license` not passed. MFFD: `proprietary`. |
| `MFFD_DEFAULT_ACCESS_RIGHTS` | unset | Fallback when `--default-access-rights` not passed. MFFD: `restricted access`. |

**Self-update + telemetry + checkpoint**

| Var | Default | Notes |
|---|---|---|
| `MFFD_SELFUPDATE` | `1` | Set to `0` to disable the heartbeat self-updater. Also disables the background telemetry flusher. |
| `MFFD_HEARTBEAT_S` | `60` | Seconds between manifest polls + telemetry flushes. |
| `MFFD_MANIFEST_CONTAINER_ID` | `473932` | `fileContainer` id holding `mffd-import-manifest-v*.json`. The newest by `createdAt` wins. |
| `MFFD_TELEMETRY_TS` | `593750` | `timeseriesContainer` id receiving the ROW-format counter CSV. |
| `MFFD_RUNLOG_SD` | `593753` | `structuredDataContainer` id receiving the events envelope. |
| `MFFD_CHECKPOINT` | `~/.mffd-import-state.json` | Single-file resume state. Atomic-write (tmp + fsync + rename). |

**Other**

| Var | Default | Notes |
|---|---|---|
| `DATA_DIR` | `.` | LOCAL-mode root directory. Ignored in SOURCE mode. |
| `LOG_DIR` | script directory | Where `mffd-import-<session>.log` + `.state.json` + `.lock` files land. |
| `PAGE_SIZE` | `50` | DataObjects per page when paginating source collections. |
| `MAX_DOS_PER_STEP` | `0` | Set by `--max-dos`; `0` = unlimited. |

**Runner-only env vars (`mffd-runner.sh`)**

| Var | Default | Notes |
|---|---|---|
| `MFFD_SCRIPT` | `${SCRIPT_DIR}/mffd-import-v15.py` | Path to the Python script the runner re-execs. |
| `MFFD_PYTHON` | `python3` | Interpreter; override to `uv` etc. |
| `MFFD_RUNNER_STOP` | `/tmp/mffd-runner.stop` | `touch` this path to make the runner exit at the next iteration. |

## Exit codes

The runner (`mffd-runner.sh`) inspects the script's exit code and decides retry
vs. stop. Every code in the 2-9 range is "operator must act — do not retry";
anything else is treated as transient.

| Code | Meaning | Runner action |
|---|---|---|
| `0` | Clean exit. May indicate a self-update is pending — runner sleeps 2s then restarts so the new script runs. | sleep 2, restart |
| `1` | Generic failure (network, unrecoverable HTTP). | crash-loop-check, sleep 30, retry |
| `2` | Smart-warmup: AUTH failed (401 / 403, all retries exhausted). | **STOP** |
| `3` | Smart-warmup: SOURCE UNREACHABLE (no DNS / no route / source down). | **STOP** |
| `4` | Smart-warmup: Garage S3 backend inactive on dest (probe returned gridfs error). | **STOP** |
| `5` | Operator interrupt (SIGINT after state persisted). | **STOP** |
| `6` | Smart-warmup: wire-shape drift vs. `fixtures/v5/openapi-5.4.0.json`. | **STOP** |
| `7` | Smart-warmup: write-permission denied on probe DO. | **STOP** |
| `8` | IMPORT-Q7-VERIFY: source content empty for one or more probes (placeholder rows / 0-byte files). Operator must fix the source. | **STOP** |
| `9` | IMPORT-CFG1: config error — `--default-license` / `--default-access-rights` (or `MFFD_DEFAULT_*` env) missing. | **STOP** |

The runner additionally enforces **crash-loop detection**: ≥5 restarts inside
60s with non-clean exit → stop instead of hammering the dest API
(`mffd-runner.sh` lines 32-50, RUNNER-CLD).

## Self-update flow (IMPORT-SU1)

The script can replace itself on disk and `os.execv` into the new version
without operator intervention.

```text
┌──────────────────────┐    every MFFD_HEARTBEAT_S seconds
│ SelfUpdater thread   │ ──────────────────────────────────► GET /shepard/api/fileContainers/473932/payload
│ (daemon, in proc)    │                                      (list manifest files, sorted by createdAt)
└──────────────────────┘
        │ newest mffd-import-manifest-v*.json
        ▼
   parse {version, script_oid, script_sha256, script_filename}
        │ version != IMPORT_SCRIPT_VERSION ?
        ▼
   GET /shepard/api/fileContainers/473932/payload/{script_oid}
        │
        ▼
   sha256(body) == manifest.script_sha256 ?
        │
        ▼
   write body → __file__.v<new>.new   (staged, NOT yet active)
        │
        ▼
   updater.pending_path = <staged>;  updater.update_event.set()
        │
        ▼
   main-loop graceful boundary detects update_event:
       checkpoint["last_execv_at"|"last_execv_from"|"last_execv_to"] persisted
       Path(__file__) replaced by staged file (atomic rename)
       os.execv(sys.executable, [sys.executable, __file__, *argv[1:]])
```

The new process inherits the same checkpoint file and resumes from
`do_id_mapping` / `completed_files` / `completed_ts` / `completed_structured`.

Manifest schema (one JSON file per release):

```json
{
  "version": "15.7",
  "script_oid": "<file-uuid-in-fileContainer-473932>",
  "script_sha256": "<hex>",
  "script_filename": "mffd-import-v15.py",
  "released_at": "2026-05-23T12:00:00Z"
}
```

The "starter curl baker" workflow: bump `IMPORT_SCRIPT_VERSION` in the script,
upload the new script + a new manifest pointing at it. Every live runner picks
the change up within `MFFD_HEARTBEAT_S` (60s default).

## Telemetry schema (IMPORT-T1)

Two surfaces, both inside Shepard itself (same discipline as the import-rate
stats collector documented at
[`docs/help/observing-an-import.md`](../../../docs/help/observing-an-import.md)).

**Counters → `timeseriesContainer/593750`** (ROW format CSV, one row per
counter per heartbeat):

```csv
timestamp,measurement,device,location,symbolicName,field,value
<ns>,mffd_import,<hostname>,<mode>,v<version>,counter_<name>,<float>
<ns>,mffd_import,<hostname>,<mode>,v<version>,gauge_<name>,<float>
<ns>,mffd_import,<hostname>,<mode>,v<version>,gauge_uptime_s,<float>
```

5-tuple scrub rule (`Telemetry._scrub`, lines 3118-3130): the backend
`TimeseriesValidator` rejects Space/Comma/Point/Slash in ALL FIVE Timeseries
fields. Every value is rewritten to replace each with `_`, so a host like
`cube3.intra.dlr.de` becomes `cube3_intra_dlr_de`.

Counter names emitted by v15.7:
`dos_processed`, `ts_points_imported`, `files_uploaded`, `structured_imported`,
`errors`, `retries`, `redeploys_survived`, `selfupdate_checks`,
`telemetry_flush_errors`.

**Events → `structuredDataContainer/593753`** (one row per flush, envelope
carrying the batch):

```json
{
  "ts": "2026-05-23T12:34:56Z",
  "host": "<hostname>",
  "version": "15.7",
  "mode": "source",
  "events": [
    {"ts": "...", "level": "info", "event": "process_started_fresh", "mode": "source", ...},
    {"ts": "...", "level": "warn", "event": "selfupdate_error", "exc": "RequestException", "msg": "..."},
    {"ts": "...", "level": "info", "event": "update_available", "current": "15.6", "target": "15.7", "oid": "..."}
  ]
}
```

Standard event names: `process_started_fresh`, `process_started_with_checkpoint`,
`process_started_after_execv`, `process_shutting_down`, `update_available`,
`update_ready_for_exec`, `applying_update`, `update_download_failed`,
`update_sha256_mismatch`, `update_execv_failed`, `manifest_missing_oid`,
`selfupdate_error`.

## Checkpoint shape (IMPORT-CP1)

Single-file JSON at `MFFD_CHECKPOINT` (default `~/.mffd-import-state.json`).
Atomic-write via `tmp + fsync + rename` (`atomic_write_json`, line 235).
Survives kill -9 mid-write — either the old file or the new is intact, never
a half-written truncation.

```json
{
  "version": "15.7",
  "mode": "source",
  "src_coll_id": 48297,
  "dest_coll_id": 515365,
  "started_at": "2026-05-23T08:00:00Z",
  "last_saved_at": "2026-05-23T12:34:56Z",
  "pid": 12345,
  "processed_src_do_ids": [101, 102, 103, ...],
  "skipped_src_do_ids": [],
  "counters": {"dos_processed": 8400, "files_uploaded": 12700, ...},
  "last_execv_at": "2026-05-23T10:15:00Z",
  "last_execv_from": "15.6",
  "last_execv_to": "15.7"
}
```

A **second** state file lives next to the log
(`mffd-import-<session>.state.json`, managed by `ImportState`) and tracks
per-payload resume sets (`completed_files`, `completed_ts`,
`completed_structured`, `do_id_mapping`, `batch_sequence`, gate flags).
Workers call `state.persist()`; writes are coalesced (≥100 dirty events OR
≥30s since last flush).

## Runner.sh wrapper

Use the runner when:
- you want SSH-drop survival (the runner survives even if the Python script
  is killed; `SIGHUP` is also `SIG_IGN`'d inside the script itself for the
  v15.7 IMPORT-SIGHUP fix)
- you want automatic retry on transient errors (network blips, redeploys)
- you want self-update to apply transparently (the runner re-launches after
  a clean exit, picking up any newly-staged `__file__` from the SelfUpdater)

Use direct invocation (`python3 mffd-import-v15.py`) when:
- you're debugging — every retry hides the error class
- you want exit code 1 to surface to your shell as-is
- you're running under `--dry-run` or `--verify-imported` (read-only paths
  where a crash-loop is meaningless)

Stop the runner cleanly: `touch /tmp/mffd-runner.stop`. The current script
finishes its current iteration; the runner exits before the next restart.

## Troubleshooting

**`0-byte files in dest after import` (IMPORT-Q7 source-content-empty)**
The source collection has `FileReference` rows whose `fileOids[]` point at
zero-byte payloads. v15.3 fails fast: probe one file + one TS + one structured
ref before mass import. Exit code `8`. Operator must fix the source data
state (re-upload, or remove the empty refs). Verify with one curl:
`curl -H "X-API-KEY: $SOURCE_SHEPARD_API_KEY" -sI $SOURCE_SHEPARD_URL/shepard/api/collections/$CID/dataObjects/$DOID/fileReferences/$FRID/payload/$OID | grep -i content-length`.

**`401 mid-run` (JWT expiry pause → SIGCONT resume)**
kreb_fl tokens on cube3 expire after ~14h. v15 catches 401 in any worker,
prints a structured banner with the running PID and the operator's next steps,
and pauses every worker on a shared Event. Re-mint the JWT in the cube3 UI,
update the env var in the running process's shell (`export
SOURCE_SHEPARD_API_KEY=<new>`), then `kill -CONT <pid>`. The main-thread
signal handler rewrites the session headers and clears the pause Event;
workers resume. No checkpoint loss.

**`runner restarts forever, then stops` (RUNNER-CLD crash-loop detector)**
The runner counts non-clean restarts in a 60s rolling window. ≥5 → stop.
Inspect `mffd-import-<session>.log` for the underlying cause; fix and
re-run. The crash-loop limit is conservative because the typical failure is
"operator passed a bad value and the script can't classify it as exit-9".

**`script exited but a redeploy happened mid-flight`**
v15 catches that — the bounded exponential backoff
(`backoff_delay(attempt, base=1.0, cap=60.0, jitter=0.25)`) plus the runner's
30s retry-sleep covers the typical 90s nuclide redeploy window. The dest
warmup probe re-runs on every restart so a fresh `dest_client.warmup()`
exercises the new container. v15.5 fixed a tight retry-loop pattern that was
spinning at 100% CPU on persistent 502s.

**`telemetry silent, no points in container 593750` (v15.7 IMPORT-TLOOP)**
The background telemetry flusher used to die silently if any flush raised an
unexpected exception (incl. `SystemExit` from a deeply-nested `sys.exit` in a
helper). v15.7 catches `BaseException` in the flush loop, increments
`telemetry_flush_errors`, and self-heals once (single re-spawn — a second
crash means a real bug, do not hide it).

**`SSH drop killed the import` (v15.7 IMPORT-SIGHUP)**
v15.6 and older died on `SIGHUP` when the parent shell exited. v15.7
installs `SIG_IGN` on `SIGHUP` (line 3830). `tmux` / `nohup` remain the
recommended deployment shape, but the safety net catches the operator who
forgot.

**`telemetry rows missing — TimeseriesValidator rejected the row` (v15.6 IMPORT-T2)**
Backend TimeseriesValidator rejects Space/Comma/Point/Slash in the 5 channel
fields. v15.6 added `Telemetry._scrub` (lines 3118-3130). If you see this on
a custom counter name, check that your new name only uses `[A-Za-z0-9_]`.

**`manifest not picked up — selfupdater silent`**
The v5 path for listing files in a container is `/payload`, NOT `/files`
(which 404s — there is no such endpoint). v15.6 fixed `Manifest.latest()`
(IMPORT-SU2). Confirm the manifest is present with
`curl -H "X-API-KEY: $SHEPARD_API_KEY" $SHEPARD_URL/shepard/api/fileContainers/473932/payload | jq '.[].filename'`.

**`review gate stuck — script waiting on import_ready`**
The script blocks on `PATCH /collections/{coll_id}` setting
`attributes.import_ready = <SESSION_ID>` after the warmup probe. Default
timeout: 60 min (`GATE_TIMEOUT_MIN`). Operator (or Claude on the operator's
behalf) clears it via:
```bash
curl -X PATCH -H "X-API-KEY: $SHEPARD_API_KEY" -H "Content-Type: application/json" \
  -d "{\"attributes\":{\"import_ready\":\"$SESSION_ID\"}}" \
  $SHEPARD_URL/shepard/api/collections/515365
```
On timeout the script exits 1 → runner retries → probe is skipped (state
records `warmup_done=True`) → polls again.

## See also

- [`docs/help/importing-from-dlr-cube3.md`](../../../docs/help/importing-from-dlr-cube3.md) — user-facing task page
- [`aidocs/integrations/95`](../../../aidocs/integrations/95-shepard-plugin-importer-patterns-from-v15.md) — patterns distilled into `shepard-plugin-importer`
- [`docs/help/observing-an-import.md`](../../../docs/help/observing-an-import.md) — sibling import-rate stats collector (same shepard-measures-itself discipline)
- [`aidocs/16-dispatcher-backlog.md`](../../../aidocs/16-dispatcher-backlog.md) — IMPORT-* rows (IMPORT-SU1 through IMPORT-TLOOP)
- `_smart_warmup.py` (sibling file) — IMPORT-W1/W2/W3 smart-warmup probe + OpenAPI wire-shape comparator
