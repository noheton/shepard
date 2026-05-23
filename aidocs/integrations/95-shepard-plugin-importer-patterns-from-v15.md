---
title: shepard-plugin-importer — patterns distilled from v15.x MFFD import script
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributors, plugin authors
---

# 95 — shepard-plugin-importer: patterns from v15.x MFFD field experience

**Status:** Design notes · IMPORTER-CARRY (aidocs/16 #126) · queued → design-shipped
**Companion:** `aidocs/integrations/93-mffd-import-v15-requirements.md` (v15 requirements),
`plugins/importer/docs/reference.md` (PR-2 scaffold already shipped),
`aidocs/platform/32-long-running-process-pattern.md` (JobService shape inherited)

## 0. Why this doc exists

`examples/mffd-showcase/scripts/mffd-import-v15.py` (4321 LoC, v15.8) is the
**field-validated cross-instance importer** that lifts the real MFFD process data
(8457 source DataObjects across two collections — 5012 tapelaying + 3371
bridgewelding) from DLR cube3 (`backend.bt-au-cube3.intra.dlr.de`) into
nuclide.systems. It ran for many hours, across redeploys, JWT rotations, and
self-updates, under real production load.

That run was the laboratory. The patterns it forged are what
`shepard-plugin-importer` (already at PR-2 scaffold, per
`plugins/importer/docs/reference.md`) must absorb — **not literally as code,
but as design pressure** on PR-3 through PR-7. This document distils 15
patterns from the v15.x source into the plugin-shaped equivalents an in-tree
Quarkus plugin should land.

**The translation principle** (this is the load-bearing assumption — re-read it
when each pattern reads off-key):

> The v15 patterns split cleanly into two families.
> **Family A — wire-shape + observability + state shape** translate directly
> from the Python script into the Java plugin: state-as-JSONB column, atomic
> writes, self-observability counters, idempotent by-name lookups, exit-code
> discipline → REST-status discipline.
> **Family B — Python-process resilience** (`os.execv` self-update, SIGCONT
> JWT pause, SIGHUP ignore, daemon-thread self-heal, runner crash-loop) do
> **not** translate literally — they map onto JobService state-machine
> primitives + REST endpoints + operator-action surfaces. Several of these
> are tempting to misread as "add plugin hot-reload" or "add signal handlers
> in JVM" — **that's not the lift**. The lift is *what the pattern solves*,
> not *how the Python script solved it.*

This translation principle drives the table in §2.

## 1. Reuse survey (per `feedback_reuse_before_reimplement.md` §1)

The question: should `shepard-plugin-importer` adopt an existing
extract-load (or extract-load-transform) framework as its substrate, or build
its orchestration shell from scratch on Quarkus + `importer_run` Postgres
table (the PR-2 baseline)?

### Candidates evaluated

| # | Candidate | License | Activity | Primary lens | Coverage of patterns 1–15 | Decision |
|---|---|---|---|---|---|---|
| 1 | **dlt** (data-load-tool) [^dlt] | Apache-2.0 | Active (2025-Q4 releases) | Declarative Python ELT, schema-on-load, state mgmt | Patterns 3 (state), 14 (idempotent incremental), 7 (config); doesn't cover 1, 8, 9, 10, 11, 12, 15 | ADOPT-AS-PATTERN-ONLY |
| 2 | **Singer / Meltano** [^singer] | Apache-2.0 (Singer spec); MIT (Meltano) | Mature; Meltano ~2017+ | tap/target/state JSON protocol; orchestration via Meltano | Patterns 3 (state), 14 (idempotent), 7 (catalog/config); no resilience patterns | ADOPT-AS-PATTERN-ONLY |
| 3 | **Airbyte** [^airbyte] | ELv2 (core) + MIT (connectors) | Very active | Connector-spec model, multi-substrate destinations, state mgmt | Patterns 3, 7, 14 + adapter-registry model from §3; partial 8 (job state machine); doesn't cover 1, 9, 10, 11, 12, 15 | ADOPT-AS-PATTERN-ONLY (connector-spec model in §3) |
| 4 | **Apache NiFi** [^nifi] | Apache-2.0 | Very active, ASF top-level | Visual dataflow, processor-graph engine | Patterns 11 (backpressure/retry built-in); doesn't speak Shepard's entity model | REJECT |
| 5 | **Apache Airflow** [^airflow] | Apache-2.0 | Very active | DAG scheduler, task-level retry, sensor pattern | Patterns 10 (max-attempts), 11 (retry decorator); doesn't speak payload kinds; heavyweight | REJECT — sidecar weight + DAG runtime > pattern value |
| 6 | **Prefect 3.x** [^prefect] | Apache-2.0 | Very active | Python-native flow engine, state machine | Patterns 11 (retry), 10 (cap), partial 3 (state); modern but Python-runtime | REJECT (wrong language for in-tree plugin) |
| 7 | **Dagster** [^dagster] | Apache-2.0 | Very active | Asset-graph oriented, software-defined assets, IO managers | Asset-graph would be a nice fit for DataObject DAG — but introduces Python runtime + Dagster cluster | REJECT for v1; revisit for "external observer" plugin shape |
| 8 | **Apache Kafka Connect** [^kconnect] | Apache-2.0 | Very active | Source/sink connector spec, distributed worker | Patterns 7 (config), 14 (state via topic), partial connector-registry | REJECT — Kafka cluster requirement > pattern value |
| 9 | **Eclipse Smila** [^smila] | EPL-1.0 | DORMANT (last release ~2014) | Crawler framework | Historical interest only | REJECT |

### Decision

**BUILD-OWN orchestration shell on top of Quarkus + `importer_run` Postgres
JobService (PR-2 baseline). ADOPT-AS-PATTERN-ONLY from Singer, dlt, and
Airbyte (citations in §2 and §3).**

**Reasoning — why off-the-shelf orchestrators don't fit:**

1. **Patterns 1–15 are *operational resilience* patterns, not *data-flow*
   patterns.** Orchestrators give you scheduling, retry, DAG topology, and
   pluggable operators. They do not give you (a) shepard-specific entity
   awareness — `DataObject` + `TimeseriesReference` + `SemanticAnnotation`
   are not in any of those vocabularies; (b) self-observability into the
   destination substrate (the importer needs to emit counters *into Shepard*,
   not into a separate Prometheus stack); (c) v5/v2 dual-API knowledge
   (idempotent by-name lookup against the upstream-compatible legacy API,
   write against the v2 surface).

2. **Multi-substrate destination.** dlt's killer feature is "load any source
   into any of {DuckDB, BigQuery, Snowflake, Postgres}" — a single warehouse
   target. Shepard's destination is **four substrates simultaneously**
   (Neo4j + Garage S3 + TimescaleDB + MongoDB), wired together by the
   `:DataObject` graph. No off-the-shelf framework models that.

3. **In-process Quarkus plugin, not sidecar.** `shepard-plugin-importer`
   lives in the JVM next to `PermissionsService` and `JobService`. NiFi,
   Airflow, Prefect, Dagster, Kafka Connect all require a separate runtime
   to be deployed alongside Shepard. That's `+1 deploy artifact, +1 helm
   chart, +1 RBAC story`. For the patterns in this doc the cost outweighs
   the value.

4. **Singer's thesis ("tap and target should be decoupled") is rejected by
   design.** A Singer tap doesn't know whether its target is BigQuery or
   Snowflake; a Shepard source adapter ABSOLUTELY knows it's writing into
   `:DataObject` nodes with `:TimeseriesReference` children. Decoupling
   here would be premature — the value of the importer plugin is precisely
   that it speaks `Shepard:DataObject`. The cost is that we can never run a
   Singer tap unmodified against Shepard. That's the correct trade for v1.

**What we *do* take from the survey:**

- **Singer's `state.json` shape** → confirms the v15 checkpoint shape is
  industry-standard. Cited under Pattern 3.
- **dlt's incremental loading** → confirms "by-name idempotent" is the right
  default. Cited under Pattern 14.
- **Airbyte's `connectorSpec`** → shapes the §3 adapter registry: each
  `ImportSource` declares a JSON-schema for its config blob, which the
  frontend renders. Cited under §3.
- **Airflow / Prefect max-attempts knob** → confirms Pattern 10 belongs on
  the JobService not on a runner. Cited under Pattern 10.

## 2. The 15 patterns and how they land in the plugin

Each row below cites the v15.x line range, names the failure mode the pattern
prevents (often from this session's MFFD logs), and shows the Java/Quarkus
equivalent in `shepard-plugin-importer` (PR-3 through PR-7 scope). The
"family" column flags whether the translation is direct (A) or requires
re-shaping (B) — recall the translation principle in §0.

| # | Pattern (origin tag) | Family | v15 source lines | Java/Quarkus shape |
|---|---|---|---|---|
| 1 | Self-update via manifest (IMPORT-SU1/SU2) | B | mffd-import-v15.py:3221–3346 | `ImporterRun.source_config.scriptVersion` field + REST `GET /v2/imports/upgrade-status` |
| 2 | Telemetry into Shepard itself (IMPORT-T1/T2) | A | mffd-import-v15.py:3098–3217 | `ImporterTelemetryService` emits to `importer-telemetry` TS container + `importer-runlog` SD container |
| 3 | Checkpoint resume (IMPORT-CP1) | A | mffd-import-v15.py:3381–3433, 3019–3093 | `importer_run.state JSONB` column (PR-2 table already present); workers read/write via `ImporterRunService` |
| 4 | Smart warmup with fail-fast (IMPORT-W1/W2/W3) | A | _smart_warmup.py:1–1226 + v15.py:3876–3920 | `WarmupRunner` Java class; `JobState.PRE_FLIGHT_FAILED` instead of exit codes |
| 5 | Source content probe (IMPORT-Q7-VERIFY) | A | mffd-import-v15.py:3922–4006 | First step of WarmupRunner: one-of-each fetch against source; fails with `EMPTY_SOURCE_PAYLOAD` status |
| 6 | Distinct config-error state (IMPORT-CFG1, exit 9) | B | mffd-import-v15.py:3748–3764 | `JobState.NEEDS_OPERATOR_INPUT` — visually distinct from `FAILED` in UI; UI surfaces "fix and resubmit" CTA |
| 7 | Env-var fallback for config (IMPORT-CFG1) | A | mffd-import-v15.py:3744–3751 | Source `connectorSpec` declares precedence: per-run override → per-source default → instance default (Airbyte-style) |
| 8 | SIGHUP protection (IMPORT-SIGHUP) | B | mffd-import-v15.py:3824–3834 | Not applicable — Quarkus worker threads don't die on terminal disconnect. The *intent* (no operator-mistake-friendly mode) maps to "all imports run inside JobService; never bound to a user shell" |
| 9 | Telemetry-thread self-heal (IMPORT-TLOOP) | B | mffd-import-v15.py:3843–3874 | `@Scheduled` on `ImporterTelemetryService.flush()` — Quarkus reschedules even if a task throws; redundant for v1, no extra code needed |
| 10 | Runner crash-loop detection (RUNNER-CLD) | B | mffd-runner.sh:32–50 | JobService `max-attempts` knob (PR-4: `shepard.importer.max-attempts=5`); transitions to `JobState.FAILED_PERMANENT` after cap |
| 11 | Worker pool with deadline retry (`_request_with_retry`) | A | mffd-import-v15.py:1962–2065 | `ImporterHttpClient` wraps `MicroProfile RestClient` + `@Retry(maxRetries=N, retryOn=...)` annotations (smallrye-fault-tolerance) |
| 12 | F(AI)²R agent identity header (X-AI-Agent) | A | mffd-import-v15.py:622–638 | Source-credentials blob already carries `aiAgent` field (per §3); `ImporterHttpClient` adds `X-AI-Agent` on every dest write |
| 13 | Per-DO state granularity | A | mffd-import-v15.py:3052–3093 (StateFile) | `importer_run.state JSONB` schema: `{completed_dos:[uuid], completed_files:[oid], completed_ts:[refId], completed_structured:[oid]}` |
| 14 | Idempotent imports by-name | A | mffd-import-v15.py:2082–2150 (ensure_dest_collection/do) | `ImporterDestService.findOrCreate*()` family — name-lookup against dest, skip if exists, never duplicate |
| 15 | Snapshot boundaries = HUMAN-decided | B | mffd-import-v15.py:4071–4080, 4103–4113 | `JobState.AWAITING_SNAPSHOT_DECISION` (pre and post); UI surfaces "Take pre-import snapshot? Skip? Take post-import snapshot?" gate |

---

### Pattern 1 — Self-update via manifest (`IMPORT-SU1`, v15.4 + v15.6)

**Source lines:** `mffd-import-v15.py:3221–3346` (Manifest poller +
SelfUpdater thread), `mffd-import-v15.py:3349–3376` (`apply_pending_update`
with `os.execv`).

**Why it matters — the failure it prevented:** the MFFD import ran for
many hours. Twice during the run we had to ship a patched script (v15.4 →
v15.5 → v15.6 → v15.7). Without self-update, every patch meant a manual
re-fetch on the bt-au-cube-mig bridge host, manual `SIGTERM`, manual restart,
and a real risk of half-imported state. With self-update: operator uploads
new manifest into dest Shepard, importer polls every 60s, sha256-verifies the
new bytes, atomically replaces, `execv`s the new interpreter, and resumes
from checkpoint. The pattern's payoff is *operator labour saved at the worst
possible moment* (live production import, JWT half-expired).

**Adoption risk: HIGH.** This is the pattern most likely to be misread as
"add plugin hot-reload to Shepard." That's not the lift.

**How it lands in the plugin — the translation:**

The Quarkus plugin lives inside the same JVM as the Shepard backend. It is
loaded by `PluginRegistry`. Hot-swapping plugin JARs at runtime is a
*separate* architectural decision (`aidocs/platform/47 §6` — out of scope for
the importer plugin). What we **do** carry across is the *idea that drift
between deployed importer code and the source-of-truth in Shepard is
visible*:

- `ImporterRun.source_config.expectedPluginVersion`: optional pin. If
  set, the import refuses to start when `PluginRegistry.getActiveVersion()`
  differs — the operator either upgrades the deployment or updates the
  pin.
- `GET /v2/imports/upgrade-status`: returns `{currentVersion, latestAvailable,
  driftDetected, releaseNotes}`. Polled by the admin UI; surfaces a banner
  when a newer release is available.
- The *Python script* still exists as a forgeable single-file artefact in
  `examples/mffd-showcase/scripts/`. For workloads that must run from outside
  the Shepard host (DLR cube3 case — the bridge host pulls from DLR
  intranet *and* writes to nuclide.systems, neither side runs the importer
  plugin), the script's self-update mechanism stays as-is. The plugin
  inherits the *idea*, not the implementation.

**Citation:** `mffd-import-v15.py:153–158` ("the running script polls a
JSON manifest in dest Shepard. On newer version → sha256-verified download
→ checkpoint save → os.execv replace").

---

### Pattern 2 — Telemetry into Shepard itself (`IMPORT-T1` v15.4 + `IMPORT-T2` v15.6)

**Source lines:** `mffd-import-v15.py:3098–3217`. Two destinations:
counters → TS container (ROW-format CSV), events → SD container
(JSON envelope per heartbeat).

**Why it matters — the failure it prevented:** during the MFFD run we wanted
to know "how many DOs has the importer processed in the last hour?" without
opening an ssh session to the bridge host. The v15.4 telemetry pushed
counters straight into the dest Shepard's own TS container, so the *chart
component already in the UI* plotted the import's progress. This is
`feedback_shepard_measures_itself.md` in its sharpest form — the same UI a
researcher uses for hot-fire data now plots the import that brought the data
in. No Prometheus, no Grafana, no separate ops UI.

**Subtle bug caught in v15.6 (IMPORT-T2):** the backend
`TimeseriesValidator` rejects `Space/Comma/Point/Slash` in all five
5-tuple fields. A hostname like `cube3.intra.dlr.de` will be rejected unless
scrubbed; v15.6 ships a `_scrub()` helper (`mffd-import-v15.py:3118–3130`).
This is the *exact friction* `aidocs/platform/87-timeseries-appid-migration.md`
exists to fix: the 5-tuple is a foot-gun even for the system's own
telemetry.

**Adoption risk: LOW.** Direct translation.

**How it lands in the plugin:**

- `ImporterTelemetryService` — Quarkus `@ApplicationScoped` bean, injected
  into all sources.
- Counters: `dos_processed`, `files_uploaded`, `ts_points_imported`,
  `structured_imported`, `errors`, `retries`, `redeploys_survived`.
- Gauges: `current_throughput_bps`, `eta_remaining_s`, `queue_depth`.
- Events: per-run runlog rows.
- Destination containers: bootstrap on first plugin start. Container names
  pinned by convention: `importer-telemetry-ts` + `importer-runlog-sd`,
  visible to instance-admins.
- Per-job namespacing: every metric carries `runId` as a tag (replaces v15's
  `mode`+`host`+`version` 3-tuple).
- Once `aidocs/platform/87` lands (TS appId migration), the 5-tuple goes
  away — telemetry then uses single-appId channel identity. The plugin's
  emission code is rewritten *once* at that crossover.

**Citation:** `mffd-import-v15.py:3101–3104` ("observability lives where the
researcher already looks").

---

### Pattern 3 — Checkpoint resume (`IMPORT-CP1`, v15.4)

**Source lines:** `mffd-import-v15.py:3381–3433` (Checkpoint class with
atomic write via tmp+fsync+rename via `atomic_write_json`),
`mffd-import-v15.py:3019–3093` (per-payload-kind state granularity).

**Why it matters — the failure it prevented:** every self-update execv
(Pattern 1), every JWT pause (Pattern 11), every SIGTERM, every Quarkus
redeploy on the dest side could have left the import re-doing thousands of
already-uploaded files. The checkpoint reduced restart cost from "potentially
hours of re-work" to "next batch starts where we left off."

**Adoption risk: LOW.** PR-2 already laid the groundwork.

**How it lands in the plugin:**

- `importer_run.state` JSONB column (already in `V1.11.1` Flyway migration
  per PR-2; see `plugins/importer/docs/reference.md`).
- Schema (mirrors v15 ImportState):
  ```jsonc
  {
    "completed_dos": ["uuid-a", "uuid-b"],
    "completed_files": ["uuid-a:oid-1"],
    "completed_ts": ["uuid-a:ref-1"],
    "completed_structured": ["uuid-a:oid-2"],
    "ts_containers": {"step-key": "container-uuid"},
    "warmup_done": true,
    "gate_passed": true,
    "snapshots_decision": {"pre": "skipped", "post": "pending"}
  }
  ```
- Atomic update via Postgres `UPDATE ... WHERE state_version = ?` (optimistic
  lock; no need for fsync+rename when the substrate is transactional).
- Worker reads + writes via `ImporterRunService.markCompleted(runId, kind,
  key)`.

**Industry citation:** Singer spec [^singer] uses a `state.json` artefact
with the same shape (per-stream bookmarks). dlt [^dlt] uses incremental
state-mgmt with a similar schema. v15's checkpoint shape is industry-
standard, and the JSONB column is its natural home in Postgres.

---

### Pattern 4 — Smart warmup with fail-fast (`IMPORT-W1/W2/W3`, v15.2)

**Source lines:** `examples/mffd-showcase/scripts/_smart_warmup.py:1–1226`
(probe + report); `mffd-import-v15.py:3876–3920` (invocation).

**Why it matters — the failure it prevented:** v14 happily started ingesting
data even when (a) the dest-side Garage backend was inactive (silent fallback
to GridFS, then GridFS exhausted on the first multi-GB file), (b) the source
JWT was expired (404s misclassified as "no data"), (c) the v5 wire shape had
drifted (Bug E — silent data corruption from a misread
`StructuredDataPayload` wrapper). v15.2 probes auth + reads + writes +
wire-shape + Garage **before** the main loop, with a distinct exit code per
failure class.

**Adoption risk: LOW.** Direct translation; only the exit-code → JobState
mapping is plugin-shaped.

**How it lands in the plugin:**

- `WarmupRunner` Java class — runs as the first phase of every `ImporterRun`.
- Phases (each one fails fast with a distinct `JobState`):
  - `AUTH_FAILED` (was exit 2)
  - `SOURCE_UNREACHABLE` (was exit 3)
  - `GARAGE_DOWN` (was exit 4)
  - `WIRE_SHAPE_DRIFT` (was exit 6) — compares observed responses against
    `backend/src/test/resources/fixtures/v5/openapi-5.4.0.json`
  - `WRITE_PERMISSION_DENIED` (was exit 7)
- `WarmupReport` POJO → `importer_run.warmup_report` JSONB column → REST
  surface `GET /v2/imports/{id}/warmup-report` → frontend renders the same
  structured diagnostic the Python script prints to stdout. The diagnostic
  artefact pattern (`feedback_diagnostic_artefact.md`) carries directly.
- Probe DOs created during the warmup are tagged
  `importer.warmup-probe=true` and torn down on success or kept on failure
  for post-mortem (operator decides via `warmup.retain-probes-on-failure`
  config — default `true`).

**Adoption risk note:** the OpenAPI shape comparator is the most fragile
part. Adopt verbatim from `_smart_warmup.py:200–600` (path-template cache,
JSON-pointer schema-match). Don't reinvent.

---

### Pattern 5 — Source content probe (`IMPORT-Q7-VERIFY`, v15.3)

**Source lines:** `mffd-import-v15.py:3922–4006`.

**Why it matters — the failure it prevented:** the local on-disk export at
`examples/mffd-showcase/raw-data/mffd-data/` was *shape-correct but
content-empty*: 4627 `ts-*.csv` files all 0 bytes, exporter v1.2 placeholders.
v14 happily ingested 4627 zero-byte files and wrote 4627 zero-row TS
references. v15.3 fetches one-of-each (file + TS + structured) from source
BEFORE the bulk loop; if any returns empty, exit 8 / stop.

**Adoption risk: LOW.**

**How it lands in the plugin:**

- Embedded in `WarmupRunner` as Phase 5 (`EMPTY_SOURCE_PAYLOAD` JobState).
- Each `ImportSource` declares `probeOneOfEach()` on the `ImportSource` SPI
  — the source adapter knows best what "one file + one TS + one structured"
  means in its world (S3 / DLR v5 / Confluence / Git).
- Returned `ProbeResult` records: `{kind, ok, bytesFetched, sampleRow}`.
- The plugin **does not** make the operator interpret the probe — UI shows
  green/red per kind, with the sampled bytes inline. The v15.3 ASCII-table
  format (`mffd-import-v15.py:3997–3999`) maps to the same UI affordance.

---

### Pattern 6 — Distinct config-error state (`IMPORT-CFG1`, exit 9, v15.5)

**Source lines:** `mffd-import-v15.py:3748–3764` (the FAIR R1
license/access-rights gate); `mffd-runner.sh:77–82` (exit 9 means STOP, not
retry).

**Why it matters — the failure it prevented:** if the operator forgets
`MFFD_DEFAULT_LICENSE`, the script `exit 1`s, the runner sees exit 1, the
runner retries — and the same fail repeats forever. v15.5 distinguishes
"operator must act" from "transient — retry" with a separate exit code.

**Adoption risk: MEDIUM** — needs explicit UI affordance, otherwise the
state is invisible.

**How it lands in the plugin:**

- New `JobState.NEEDS_OPERATOR_INPUT` (semantic sibling of
  `FAILED_PERMANENT`, but distinguishable in UI).
- `importer_run.operator_action_required` JSONB column carries the structured
  description: `{field, currentValue, hint, suggestedValues}`.
- REST: `GET /v2/imports/{id}` returns the action description; `PATCH
  /v2/imports/{id}/config` lets operator supply the missing value and
  immediately resume (no resubmit).
- UI: yellow status + "Fix to continue" CTA + inline editor for the field.
- The JobService's automatic-retry policy treats `NEEDS_OPERATOR_INPUT` as a
  hard stop (no exponential backoff, no max-attempts decrement).

**Industry citation:** Airbyte [^airbyte] connector spec uses
`connectionStatus: FAILED` with a structured `message` field for the same
intent.

---

### Pattern 7 — Env-var fallback for config (`IMPORT-CFG1`, v15.5)

**Source lines:** `mffd-import-v15.py:3744–3751`.

**Why it matters — operator ergonomics:** the runner.sh wrapper sets
`MFFD_DEFAULT_LICENSE` once; ad-hoc operator runs override with `--default-
license=foo`. Both paths land at the same downstream code.

**Adoption risk: LOW.**

**How it lands in the plugin:**

- `connectorSpec` declares fields with a precedence list:
  `runOverride > sourceDefault > instanceDefault > builtIn`.
- `ImporterRunRequest.config` (per-run override) takes precedence over
  `ImportSource.defaults` (per-source-instance), takes precedence over
  `:ImporterConfig` singleton (instance-wide), takes precedence over the
  `application.properties` shipped default.
- Per the `:*Config` rule in `CLAUDE.md §"Always: surface operator knobs in
  the admin config"`, the instance default is admin-configurable at runtime
  via `GET/PATCH /v2/admin/importer/config`.

**Industry citation:** Airbyte's `connectorSpec.connectionSpecification` is
the inspiration: JSON-schema declared by the source, rendered by the frontend.

---

### Pattern 8 — SIGHUP protection (`IMPORT-SIGHUP`, v15.7)

**Source lines:** `mffd-import-v15.py:3824–3834`.

**Why it matters — the failure it prevented:** operator runs `python3
mffd-import-v15.py` in a plain ssh session, no `tmux`, no `nohup`. Operator's
laptop goes to sleep → ssh times out → kernel sends SIGHUP → import dies
mid-run. v15.7 ignores SIGHUP.

**Adoption risk: HIGH if misread.** This is the most-tempting pattern to
copy literally ("add signal handlers to the JVM"). The JVM does *not* die on
parent-shell SIGHUP — `nohup` is a shell builtin, not a JVM behaviour. The
pattern is irrelevant in-process.

**How it lands in the plugin:**

- It doesn't, directly. The plugin runs inside Quarkus, which is a
  long-running JVM process supervised by Docker / systemd / Kubernetes. It
  doesn't bind to operator shells.
- The *intent* (no operator-mistake-friendly mode) maps to the broader
  architecture: an `ImporterRun` is durable in Postgres + state JSONB, so
  any process restart resumes from checkpoint. Pattern 3 already covers
  this.
- **Carry-over to docs**: `plugins/importer/docs/install.md` should explicitly
  call out "no tmux/nohup needed — imports run inside Quarkus."

---

### Pattern 9 — Telemetry-thread self-heal (`IMPORT-TLOOP`, v15.7)

**Source lines:** `mffd-import-v15.py:3843–3874`.

**Why it matters — the failure it prevented:** a daemon thread that crashes
silently leaves observability gaps that can mask real problems. v15.7 catches
`BaseException` in the flush loop, increments a counter, and respawns the
loop *once* (deliberate cap — re-spawning forever masks bugs).

**Adoption risk: LOW.**

**How it lands in the plugin:**

- Quarkus `@Scheduled` methods are already supervised by the runtime —
  uncaught exceptions are logged + the next tick still fires. No additional
  code needed.
- *Optional polish*: emit `importer.telemetry_flush_errors` counter on
  exception so the meta-observation (Pattern 2) catches the
  meta-observability failure. Three-line addition; ship in PR-4.

---

### Pattern 10 — Runner crash-loop detection (`RUNNER-CLD`, v15.7)

**Source lines:** `mffd-runner.sh:28–50`.

**Why it matters — the failure it prevented:** an operator misconfigures
`MFFD_DEFAULT_LICENSE` (or the source URL, or the API key). Script fails,
runner restarts, script fails, runner restarts, …. After 5 restarts in
60 seconds, the runner aborts with a *visible* "stop and fix" message rather
than hammering the dest API forever.

**Adoption risk: LOW.**

**How it lands in the plugin:**

- The plugin has no runner — `JobService` is the runner.
- `shepard.importer.max-attempts` knob (PR-4, default `5`) caps retries.
- On exceeding the cap, the job transitions to `JobState.FAILED_PERMANENT`
  (not `FAILED_TRANSIENT`); the scheduler stops claiming it.
- The cap should be admin-configurable at runtime per the `:*Config` rule
  (instance default; per-source override; per-run override).

**Industry citation:** Airflow [^airflow] (`retries` + `retry_delay`),
Prefect [^prefect] (`Task.max_retries`) — both confirm cap-based termination
as the standard.

---

### Pattern 11 — Worker pool with deadline-based retry (`_request_with_retry`)

**Source lines:** `mffd-import-v15.py:1962–2065`.

**Why it matters — the failure it prevented:** during the MFFD run, the dest
side was redeployed twice (Quarkus restart + Flyway migrations). Each
redeploy = ~5 minutes of 502/503 responses. v15's
`_request_with_retry(deadline_s=900.0)` simply waits up to 15 minutes per
call, with exp backoff (2s → 60s cap), printing "[reconnect] backend
unreachable; waiting…" once and "[reconnect] backend back ✓" on recovery.
The import survived the redeploys without ever burning a checkpoint cycle.

**Adoption risk: LOW.**

**How it lands in the plugin:**

- `ImporterHttpClient` wraps `MicroProfile RestClient` calls with
  `@Retry(maxRetries=N, retryOn={ConnectException, IOException},
  delay=2000, jitter=500, maxDuration=900000)` from `smallrye-fault-tolerance`.
- Retry status policy mirrors v15: `502, 503, 504, 520-524` get retried;
  other 4xx/5xx propagate.
- The "[reconnect] waiting…" / "back ✓" log messages translate to runlog
  events (Pattern 2) so the UI can show "waiting for backend recovery"
  status during long retry windows.

---

### Pattern 12 — F(AI)²R agent identity header (`X-AI-Agent`)

**Source lines:** `mffd-import-v15.py:622–638`.

**Why it matters — provenance:** every dest-side write during the MFFD
import carried `X-AI-Agent: claude-opus-4-7; actedOnBehalfOf=fkrebs@nucli.de`.
The `ProvenanceCaptureFilter` (`PROV1a`) recorded this on every `:Activity`
node. The result: an auditor can run a Cypher query "show me every entity
modified by Claude Opus 4.7 on behalf of fkrebs" — exactly the
`project_ai_human_collab_provenance.md` story.

**Adoption risk: LOW.**

**How it lands in the plugin:**

- `ImportSource.credentials` blob carries an optional `aiAgent` field:
  `{agentId, actedOnBehalfOf}`.
- `ImporterHttpClient` reads it and adds `X-AI-Agent: <agentId>;
  actedOnBehalfOf=<email>` to every dest-side request.
- The frontend source-config form has the field, prefilled with the calling
  user's identity (overridable for AI-initiated runs).
- For human-only imports the header is omitted (no AI agency to record).

---

### Pattern 13 — Per-DO state granularity

**Source lines:** `mffd-import-v15.py:3052–3093`.

**Why it matters — the failure it prevented:** if a DataObject's TS payload
fails mid-upload but its file payload succeeded, the import restart must NOT
re-upload the file (waste) but MUST retry the TS (necessary). v15 tracks
`completed_dos`, `completed_files`, `completed_ts`, `completed_structured`
separately. Restart loop walks the source, *for each (do, kind) pair*
checks the corresponding completion set, and skips what's done.

**Adoption risk: LOW.**

**How it lands in the plugin:**

- The JSONB schema in Pattern 3 already has the four arrays.
- Helpers on `ImporterRunService`:
  - `markFileCompleted(runId, doId, oid)`
  - `markTsCompleted(runId, doId, refId)`
  - `markStructuredCompleted(runId, doId, oid)`
  - `isFileCompleted(runId, doId, oid)` ...
- Each call does `UPDATE importer_run SET state = jsonb_set(state, '{completed_files}',
  state->'completed_files' || $1) WHERE id = $2`.

---

### Pattern 14 — Idempotent imports by-name

**Source lines:** `mffd-import-v15.py:2082–2150` (`ensure_dest_collection` +
`ensure_dest_do`).

**Why it matters — the failure it prevented:** restart-as-resume is only
safe if "create-if-missing, find-if-exists" is the universal write pattern.
v15 looks up by name first; only creates when find returns null. Result:
restart after partial run = exact same dest collection, exact same dest DOs,
no duplicates.

**Adoption risk: LOW.**

**How it lands in the plugin:**

- `ImporterDestService` exposes `findOrCreateCollection(name, attrs)`,
  `findOrCreateDataObject(collId, name, parentId, attrs)`, etc.
- All `ImportSource` implementations use these helpers exclusively.
- Conflict handling on name collision in non-import paths is the
  caller's problem; the importer's name space is its own (per `runId`).

**Industry citation:** dlt [^dlt] incremental loading + Singer [^singer]
`bookmarks` are the analog of "what does the source consider already-loaded"
— in Shepard the analog is "does this dest collection/DO already exist by
name" because Shepard's `:DataObject.name` uniqueness within a Collection is
the natural idempotency key.

---

### Pattern 15 — Snapshot boundaries = HUMAN-decided (`snapshot-NS1`, v15.3)

**Source lines:** `mffd-import-v15.py:4071–4080` (pre-import reminder),
`mffd-import-v15.py:4103–4113` (post-import reminder).

**Why it matters — the principle:** per
`project_snapshot_boundaries.md`, **larger transformations** are bracketed by
snapshots, but the snapshot is a *human-decided* action (the human knows
"this is the boundary worth capturing"). v15 announces the boundary
("snapshot reminder: fire when ready") and prints the exact REST call the
operator should issue — but it never auto-fires.

**Adoption risk: HIGH if misread.** Tempting to auto-snapshot ("the plugin
knows better"). Don't. The plugin can offer the gesture; the human pulls the
trigger.

**How it lands in the plugin:**

- New JobStates: `AWAITING_PRE_SNAPSHOT_DECISION`,
  `AWAITING_POST_SNAPSHOT_DECISION`.
- After warmup (and probe) the run enters `AWAITING_PRE_SNAPSHOT_DECISION`.
  UI surfaces "Take pre-import snapshot? [Yes] [Skip] [Cancel import]".
- After successful import, `AWAITING_POST_SNAPSHOT_DECISION` with the same
  three CTAs.
- The job persists indefinitely in the awaiting state (no timeout — humans
  decide on human timescales). REST: `POST /v2/imports/{id}/snapshot-
  decision {kind:"pre"|"post", action:"take"|"skip"}`.
- If `take` was chosen, the plugin fires the existing
  `POST /v2/collections/{appId}/snapshots` and waits for completion before
  proceeding.

**This is the ESCALATION:** the v15 script's pattern was "print to stdout and
hope the operator notices." The plugin's pattern is "block in a visible
JobState until the operator decides." That's a real UI surface change — not
just an importer-plugin design point, but a `frontend/components/importer/`
follow-up. Add IMPORTER-PAT-NS1-UI sub-row to §4.

---

### Pattern 16 — Worker fan-out + lazy enrichment (`IMPORT-PERF1` + `IMPORT-PERF2`, v15.8)

**Source lines:** `mffd-import-v15.py` `run_source_mode` (workers parameter +
`_process_one_source_do` closure + per-step `ThreadPoolExecutor` with
`as_completed` drain/refill); `iter_data_objects` (yields stubs);
`ShepardClient._load_file_refs / _load_ts_refs / _load_structured_refs`
(lazy fetchers); `ImportState._lock: RLock` (state thread-safety).

**Why it matters — two anti-patterns the field caught:**

1. **PERF1 — fake fan-out.** In v15.1..v15.7, `run_source_mode_workers`
   spawned a `ThreadPoolExecutor`, ran trivial `lambda: True` probes, then
   called `run_source_mode` SEQUENTIALLY inside the executor context. The
   author admitted this in the source ("a no-op fan-out that proves the
   wiring is in place"). `--workers 4` bought ZERO concurrency. The
   diagnostic ([`aidocs/agent-findings/mffd-import-slowness-diagnose-2026-05-23.md
   §5 hypothesis #1`](../agent-findings/mffd-import-slowness-diagnose-2026-05-23.md))
   measured 0.98 s/DO single-thread, which would have been ~250 ms/DO with
   real 4-worker concurrency.
2. **PERF2 — eager enrichment defeats resume.** `iter_data_objects` fired
   3 cube3 GETs per source DO (file / TS / SD refs) BEFORE the state-skip
   check could short-circuit the work. On a fully-resumed 8 457-DO collection
   that was 25 371 wasted WAN round-trips just to discover the work was
   already done.

**Adoption risk: MEDIUM.** The fix requires three coordinated edits — extract
the per-DO body into a callable, make the iterator yield stubs, add a
state-file lock — all of which need to land together. Worker-pool tests
(`test_worker_pool.py`) had to be rewritten in v15.8 to assert the new shape.
Easy to ship the wiring without the actual fan-out (v15.1 already did that;
v15.8 closes the gap).

**How it lands in the plugin:**

- The plugin's `ImporterJob.runStep(step, sourceDOs)` ALREADY parallelises
  per-DO via a Quarkus `@Asynchronous` ManagedExecutor; the v15.8 lesson is
  "make sure the pool actually executes futures, not health-checks itself."
  Convergence test: assert that with 4 workers, total wall-clock < 1.5× the
  per-DO mean (i.e. ≥ 2.5× speedup achieved against the serial baseline).
- Pre-flight enrichment goes through a lazy gateway: `SourceDOReference`
  carries `(coll_id, do_id, name)`; `client.refsForDO(ref)` is called only
  when the per-DO state check confirms work is needed. The DAO equivalent
  in the plugin: don't `JOIN FETCH` ref-lists in the source-side pagination
  query — fetch per-DO only when downstream needs them.
- `ImporterRunState` (JSONB) writes need transactional isolation (Postgres
  row-level `FOR UPDATE` or optimistic locking on `version`) — same problem
  as v15.8's `ImportState._lock: RLock`, different solution because the JVM
  doesn't share process memory across workers in the same way.
- The per-step pool size is configurable via `:ImporterConfig.maxWorkers`
  (default 4 per `aidocs/93 §5`). The plugin enforces the same upper bound
  the script does (don't let an operator set 1000 and DDoS their source).
- `existing_names` hoisting: in the plugin, `ImporterRunStep.knownDestNames`
  is a Set materialised ONCE per step at step-start. Workers consume; the
  per-DO state-file check is the actual correctness guard against
  double-upload.

**What the v15.8 fix did NOT do (and the plugin should):**

- The `tqdm`-style progress bar in v15.8 is updated only from the driver
  thread. The plugin needs a per-step gauge that increments from each
  completing future (atomic counter; visible via `/v2/imports/{id}/status`).
- The v15.8 sequential fallback (`workers <= 1`) preserves byte-for-byte
  semantics — useful for debugging. The plugin should retain a `workers=1`
  mode flag for the same reason.

### Pattern 17 — Source-user identity capture for cross-instance prov chain (`MFFD-IMPORT-USER-CAPTURE`, v15.9)

**Source lines:** `mffd-import-v15.py` `ShepardClient.resolve_self`
(JWT-`sub`-then-`/users`-fallback lookup); `ShepardClient.apply_source_user_headers`
(Session header injection with ASCII coercion); module-level `SOURCE_USER_INFO`
cache; per-DO attribute injection in `run_source_mode` (`base_attrs`
expansion); telemetry event `source_user_resolved`. Tests:
`test_user_capture.py` (19 cases — JWT decode, header coercion,
resolve fallback chain, header injection).

**Why it matters — attribution without it is useless to an auditor.**
Until v15.8 every DataObject imported cross-instance carried
`source_collection_id` but no human identity. A DIN EN 9100 chain-of-
custody read as "some bytes arrived from cube3 collection 48297 on
2026-05-23." That is not attribution; that is a metadata orphan.
Pattern 17 forwards the identity along **three channels** so the
dest's provenance machinery can light up immediately AND the
attribution is queryable via search even without dest-side semantic
plumbing:

1. **Per-DO attributes** (`source_user_username` + `source_user_displayName`
   + `source_user_email` + `source_user_instance`) — searchable,
   exportable, visible in the UI without any plugin install.
2. **Per-write headers** (`X-Source-User-Username`,
   `X-Source-User-DisplayName`, `X-Source-User-Email`,
   `X-Source-User-Instance`) on every POST/PUT/PATCH — consumed
   automatically by `ProvenanceCaptureFilter` (PROV1a). Sibling
   pattern: `X-AI-Agent` (Pattern 12). The header approach scales
   without per-call-site threading because requests.Session default
   headers apply to every call through the session.
3. **Telemetry event** `source_user_resolved` (level=info on success,
   level=warn on graceful skip) — gives operators a flag in the
   runlog to confirm enrichment was active for a given import.

**Adoption risk: LOW.** Pure read-side enrichment, no dest schema
change, no breaking change. The ASCII header coercion
(`_ascii_safe_header`) is necessary — requests/urllib3 encode header
values as latin-1, so a DLR name like "Müller" would otherwise raise
`UnicodeEncodeError` on the next write. The Session-default-headers
strategy means a bug in v15.9 startup CAN'T leak into the per-DO
loop (the headers either get set once or not at all; partial-state
is impossible).

**§3 link — pairs with PROV-USER-ENRICH (dest-side enrichment).**
v15.9 ships only the READ + FORWARD half of the attribution loop.
The CLOSE happens on the dest, where `ProvenanceCaptureFilter` must
consume the `X-Source-User-*` headers and turn them into typed
PROV-O triples on `:Activity` (`prov:wasAssociatedWith` an
`:Agent` minted from the source identity, with `prov:atLocation`
referring back to the source instance). That work is tracked as
the `PROV-USER-ENRICH` backlog row. Without it the headers still
land in the dest activity log, but only as opaque labels — the
graph queries that close the audit chain need typed triples.

**Mirror endpoint gap (CRITICAL for plugin SPI).** The original
backlog spec called for a third channel: **minting a `:User` node
on the dest** that mirrors the source user with `prov:wasDerivedFrom`
to the source instance. Field probing on 2026-05-23 found the
nuclide dest exposes neither `POST /v2/admin/users` (404) nor
`POST /shepard/api/users` (405) — user creation flows through the
OIDC/IDM substrate, not a public REST surface. v15.9 emits a
diagnostic warning when it skips this step and points at the new
`PROV-USER-MIRROR-ENDPOINT` backlog row for the design work needed.
This pattern's mirror step is therefore **gated on dest having a
User-mint endpoint** — current v5 + fork surface does not, so v15.9
ships the read-side + header-forward only. The plugin SPI should
make this seam explicit: `ImportSource.resolveSelf() → SourceUser`
is mandatory; `ImportSink.mirrorSourceUser(SourceUser) → Boolean`
is **optional** with the default impl being a no-op + warning.

**Graceful degradation IS the design.** The Reluctant Senior would
not forgive an import that died on a metadata polish step at hour 6.
Three independent guards:

  - `resolve_self()` returns `None` (not an exception) on any
    failure — bad JWT, 404, 403, network blip, malformed JSON.
  - `apply_source_user_headers(None)` is a no-op (returns False).
  - The startup call site is wrapped in `try/except Exception`
    with a counter + event, then continues.

The contract: if the lookup fails, the import runs at v15.8
semantics. If it succeeds, every DO gets enrichment. There is no
partial / inconsistent middle state.

**How it lands in the plugin:**

- `ImportSource.resolveSelf() → SourceUser` (returns Optional<>,
  not throws) — every concrete source implements this. For
  `DLRv5Source` it's the JWT-sub + GET `/users/{sub}` walk; for
  `GitSource` (when shipped) it's the commit-author identity per
  default branch HEAD; for `LocalDropboxSource` it's null (no
  remote identity).
- `ImporterJob.headersForWrite()` returns the `X-Source-User-*`
  header set; the plugin's HTTP client (the Quarkus REST client
  pointing at dest) gets these as default headers on the
  request context.
- `ImporterJob.attributesForDataObject()` returns the
  `source_user_*` map keyed for the dest's `DataObject.attributes`
  setter — same shape as v15.9's `base_attrs` expansion.
- The `ImportSink.mirrorSourceUser()` no-op + warning shape covers
  the `PROV-USER-MIRROR-ENDPOINT` gap; once the dest exposes a
  user-mint endpoint, the default impl swaps for a real call
  without any source-side change.
- Telemetry: a `source_user_resolved` gauge per import-job
  parallels the v15.9 script-side counter, visible in the
  `/v2/imports/{id}/status` shape.

**Test obligations the plugin must port:**

- JWT-sub decode handles UUID + username + missing-sub +
  malformed cases (port `test_decode_jwt_sub_*`).
- Header values are ASCII-coerced before being sent (port
  `test_ascii_safe_header_*` + `test_apply_source_user_headers_handles_umlauts`).
- Fallback chain: JWT-sub-404 → /users (no path), upstream
  list-response bail, 403 returns None (port `test_resolve_self_*`).
- `apply_source_user_headers(None)` is a no-op (port
  `test_apply_source_user_headers_none_user_is_noop`) — the plugin
  equivalent is "no `SourceUser` resolved → no header context".

## 3. What's different in the plugin vs the v15 script

The v15 script is **single-tenant, single-source, single-collection**. The
plugin is **multi-tenant, multi-source, per-Collection-scoped**. Concretely:

### 3.1 Importer registry — multiple `ImportSource` implementations

```
de.dlr.shepard.plugins.importer.spi.ImportSource
├── DLRv5Source           — DLR shepard v5 instance (PR-3 first cut)
├── JsonLdSource          — JSON-LD URL → DataObjects + annotations
├── ConfluenceSource      — Confluence dump → DataObjects (per aidocs/integrations/82)
├── CsvFolderSource       — local folder of CSVs → timeseries
├── GitSource             — git repo → files + manifest (per
│                          project_git_data_vs_code.md)
├── S3Source              — bucket prefix → file DataObjects
└── (operator-installable plugins of plugins — future)
```

Each `ImportSource` declares:

- `connectorSpec()` — JSON-schema for `source_config` (Airbyte-style,
  [^airbyte])
- `probeOneOfEach(srcConfig)` — Pattern 5 probe
- `iterDataObjects(srcConfig, state)` — pagination, resumable from state
- `iterPayloads(srcDO, kind)` — file / ts / structured per-DO yields

### 3.2 Per-Collection import configuration

UI flow:

1. Operator (or researcher with Write on target Collection) navigates to
   Collection detail page → "Imports" tab.
2. "+ New import" → picks source from registered `ImportSource` list.
3. Form rendered from `connectorSpec()`: URL, credentials, page size,
   filters.
4. Optional: AI-agent override (Pattern 12).
5. Optional: schedule (one-shot / cron-expr / webhook trigger).
6. Save → `POST /v2/imports` returns `202` + `Location: /v2/imports/{id}`.

### 3.3 Scheduling shape

- **One-shot** (default): submits to `JobService.PENDING`. Worker claims, runs,
  marks terminal.
- **Cron-expr**: scheduled job spawns a new `importer_run` on each tick
  (idempotency-key prevents duplicates if the previous run hasn't finished).
- **Webhook**: external trigger via signed POST → spawns a run with the
  webhook payload as `state.triggerPayload`. Useful for "Unhide notified us
  of a new dataset" or "Confluence page updated".

### 3.4 Permission boundary

- The importer plugin runs as a JVM in-process bean; it cannot bypass
  `PermissionsService`.
- Each `ImporterRun` carries the submitting principal's identity (or AI
  agent + `actedOnBehalfOf`).
- All dest-side writes flow through the same `Repository` / `Service` layer
  as user-driven writes — so a run that can't write to the target Collection
  fails at the first write with `FORBIDDEN`, not at submit time. Document
  this clearly in install.md.

### 3.5 Telemetry namespace per-import-job

- TS container is shared across all runs; `runId` is the disambiguator tag.
- Runlog SD container ditto.
- `GET /v2/admin/importer/runs/{id}/telemetry` returns the per-run metrics
  slice for the UI's progress widget.

## 4. Roadmap mapping (aidocs/16 #126 PRs 3–7 umbrella)

New sub-rows to add under the IMPORTER-CARRY parent row in
`aidocs/16-dispatcher-backlog.md`:

| Sub-row ID | Pattern | Lands in | Status | Effort |
|---|---|---|---|---|
| IMPORTER-PAT-SU1 | Pattern 1 — version drift detection (no hot-reload) | PR-4 (admin/upgrade-status endpoint) | queued | S |
| IMPORTER-PAT-T1 | Pattern 2 — `ImporterTelemetryService` + bootstrap of TS+SD containers | PR-4 | queued | M |
| IMPORTER-PAT-CP1 | Pattern 3 — JSONB checkpoint schema in `importer_run.state` | PR-3 (already needed for `DLRv5Source`) | queued | S |
| IMPORTER-PAT-W1 | Patterns 4 + 5 — `WarmupRunner` + source content probe + structured `WarmupReport` | PR-3 | queued | M |
| IMPORTER-PAT-CFG1 | Patterns 6 + 7 — `NEEDS_OPERATOR_INPUT` JobState + `connectorSpec` precedence | PR-4 + frontend | queued | M |
| IMPORTER-PAT-NS1 | Pattern 15 — `AWAITING_*_SNAPSHOT_DECISION` JobStates | PR-4 | queued | M |
| IMPORTER-PAT-NS1-UI | Pattern 15 — snapshot-decision CTAs in import dashboard | frontend follow-up | queued | M |
| IMPORTER-PAT-HTTP1 | Pattern 11 — `ImporterHttpClient` + `@Retry` policy + reconnect log events | PR-3 | queued | S |
| IMPORTER-PAT-AI1 | Pattern 12 — `X-AI-Agent` header on dest writes | PR-3 | queued | XS |
| IMPORTER-PAT-IDEM1 | Pattern 14 — `findOrCreate*` helpers in `ImporterDestService` | PR-3 | queued | S |

Patterns 8 (SIGHUP) and 9 (TLOOP) collapse into "no-op — Quarkus already
gives us this." They get a one-line mention in `install.md` rather than a
sub-row.

Pattern 10 (RUNNER-CLD) maps to existing `shepard.importer.max-attempts`
knob (PR-4 reference already lists it) — no new row.

Pattern 13 (per-DO state granularity) is subsumed by IMPORTER-PAT-CP1 — no
separate row.

## 5. Demonstrator pairing — which use-case validates each adopted pattern

Each pattern needs a first live use case before it can be called "shipped."
The MFFD run already validated patterns 1–15 in the Python script form — but
plugin-form validation requires fresh demonstrators.

| Pattern | First plugin demonstrator | Why this one |
|---|---|---|
| 1 (version drift) | `DLRv5Source` PR-3 — operator pins `expectedPluginVersion`; admin upgrades plugin; UI surfaces drift banner | MFFD's actual pain point — multiple patch versions over a long-running import |
| 2 (telemetry) | `DLRv5Source` PR-3 — counters visible in the same chart UI that plots TR-004 vibration | Validates `feedback_shepard_measures_itself.md` in plugin form |
| 3 (checkpoint) | `DLRv5Source` PR-3 — restart mid-run, verify resume from JSONB | Direct port of v15.4 success criterion |
| 4 + 5 (warmup + probe) | `DLRv5Source` PR-3 against a deliberately-misconfigured source (expired JWT, empty payloads) | Reproduce v15.2's wins as PR-3 acceptance tests |
| 6 + 7 (config-error state) | `ConfluenceSource` (PR-4 candidate, per aidocs/integrations/82) — operator forgets API key | New source = new failure surface = validates the JobState in fresh territory |
| 11 (retry) | All three of `DLRv5Source`, `ConfluenceSource`, `GitSource` — chaos test redeploys dest mid-import | Confirms the pattern generalises across source kinds |
| 12 (X-AI-Agent) | `DLRv5Source` driven by Claude Desktop MCP tool — verify `:Activity` capture includes AI agent | Closes the loop with `project_ai_human_collab_provenance.md` |
| 14 (idempotent by-name) | `LocalDropboxSource` (operator drops zips into a watched folder; second drop of same zip = no duplicates) | Idempotency is most visible when source can fire twice |
| 15 (snapshot decision) | `JsonLdSource` from a public dataset URL (e.g. zenodo record) — operator gets explicit "take a 'before/after public-data-import' snapshot?" CTA | Educational — surfaces the snapshot ritual to users new to Shepard |

The strongest single demonstrator: **a new cross-instance MFFD-style import
from DLR cube3 driven by the plugin instead of the Python script**. If that
import survives a dest redeploy + an upstream upgrade + a JWT rotation and
produces the same final-state DAG as the v15 run did, the plugin has
absorbed the patterns successfully.

## 6. Risks + counter-evidence from the literature

Where does the literature push back on the importer-as-plugin shape, or on
specific patterns above?

### 6.1 Singer's thesis (tap/target decoupling) is rejected by design

Singer [^singer] argues that source connectors (taps) should be ignorant of
destination (target) — they emit a standardised `RECORD` / `SCHEMA` /
`STATE` stream and a separate target program writes it. This is the
**foundational decision** of the Singer ecosystem, and it's why Singer taps
can be re-targeted from BigQuery to Snowflake without code changes.

`shepard-plugin-importer` rejects this. An `ImportSource` knows it's writing
`:DataObject` graphs with `:TimeseriesReference` children into a specific
target shape. We lose the ability to retarget; we gain Shepard-native entity
awareness (no "schema translation" pass at the end).

**Why this trade is correct for v1:** Shepard's value is the typed entity
graph (`:DataObject` provenance chain + `:Annotation` semantics +
multi-substrate payloads). A "generic record stream" abstraction would hide
the very thing the importer exists to populate. The cost is real — if a
fifth substrate later becomes a thing, the source adapters all need
rewriting. The bet is that the substrate set is stable.

### 6.2 dlt's "declarative pipelines" critique

dlt [^dlt] argues that imperative ETL scripts (which is exactly what
`mffd-import-v15.py` is) are brittle: every retry policy, every schema
evolution, every state-mgmt decision becomes ad-hoc code. Declarative
pipelines lift these into framework concerns.

This is a real risk for `shepard-plugin-importer`: if every `ImportSource`
hand-codes its retry policy + state shape + warmup probe, we lose the
patterns we just distilled. **Counter-design:** the SPI base class
`AbstractImportSource` provides the declarative scaffolding (retry policy
inherited, state shape inherited, warmup probe inherited); concrete
sources fill in only the source-specific extraction logic. The patterns
move from "shared discipline among ad-hoc scripts" to "enforced by the SPI
base class."

### 6.3 Airbyte's connector-spec model — the right shape; adopt it

Airbyte [^airbyte] standardised on a `spec` / `check` / `discover` / `read`
contract per source. Every connector declares its config schema; the
platform validates and renders. This is **directly applicable** to
`ImportSource.connectorSpec()` (Pattern 7).

We adopt this directly. Citation in §3.1.

### 6.4 Apache NiFi / Airflow / Prefect / Dagster — wrong runtime model

All four require deploying a separate orchestration runtime. For a research
RDM operator running one Shepard instance per institute, "+1 helm chart for
Airflow" is a significant operational tax. The patterns these systems
implement (retry, scheduling, DAG) are valuable but achievable in-tree via
Quarkus `@Scheduled` + `@Retry` + `JobService`. Citation under "REJECT" in
§1.

[^dlt]: dlt — *the open-source Python library for moving data*. https://dlthub.com/docs/ — Apache-2.0; declarative pipelines + incremental state-mgmt.
[^singer]: Singer.io — *Open source standard for moving data between databases, web APIs, files, queues, and just about anything else*. https://www.singer.io/spec/ — tap/target/state protocol; foundational reference for Pattern 3.
[^airbyte]: Airbyte protocol spec. https://docs.airbyte.com/understanding-airbyte/airbyte-protocol — connector-spec model; foundational reference for Pattern 7 + §3.1.
[^nifi]: Apache NiFi user guide. https://nifi.apache.org/docs/nifi-docs/html/user-guide.html — processor-graph dataflow engine; rejected as substrate for in-tree plugin.
[^airflow]: Apache Airflow concepts. https://airflow.apache.org/docs/apache-airflow/stable/core-concepts/index.html — DAG + retry-policy reference; pattern source for max-attempts cap (Pattern 10).
[^prefect]: Prefect 3.x state-machine. https://docs.prefect.io/v3/develop/write-flows — modern Python flow engine; rejected as substrate but cited for retry semantics.
[^dagster]: Dagster software-defined assets. https://docs.dagster.io/concepts/assets/software-defined-assets — asset-graph oriented; rejected for v1.
[^kconnect]: Apache Kafka Connect documentation. https://kafka.apache.org/documentation/#connect — source/sink connector spec; rejected (Kafka cluster requirement).
[^smila]: Eclipse Smila project — dormant. https://projects.eclipse.org/projects/technology.smila — historical reference only.

## 7. Acceptance criteria + open questions

### Acceptance criteria (when can this design be called "shipped"?)

- [ ] PR-3 (DLRv5Source) ships and a fresh cross-instance MFFD-style import
  runs end-to-end via the plugin (no Python script).
- [ ] Patterns 2 (telemetry), 3 (checkpoint), 4+5 (warmup+probe), 11
  (retry), 12 (X-AI-Agent), 14 (idempotent) are visibly exercised in that
  run — counters present, restart-as-resume verified, AI agent captured in
  `:Activity`.
- [ ] Patterns 6+7 (config-error state) demonstrated via deliberate
  misconfiguration — `NEEDS_OPERATOR_INPUT` surfaces in UI with the right
  CTA.
- [ ] Pattern 15 (snapshot decision) surfaces as a JobState the operator
  must clear via REST or UI.
- [ ] Each sub-row IMPORTER-PAT-* in §4 is either shipped or has a follow-on
  PR scheduled.
- [ ] `plugins/importer/docs/reference.md` updated to describe the
  patterns-as-shipped (not just the planned SPI).

### Open questions (escalate)

- **Q1 (UI footprint):** Pattern 15 (snapshot decision) needs a non-trivial
  UI gesture in `frontend/components/importer/`. PR-4 frontend scope already
  budgets the progress widget; does it also cover the snapshot-decision CTA?
  → IMPORTER-PAT-NS1-UI sub-row in §4 makes this explicit.
- **Q2 (cross-instance write):** the MFFD run needed a host with both
  source-network access AND dest-network access (the bridge host
  bt-au-cube-mig). If `DLRv5Source` runs inside the dest Shepard's JVM, it
  needs network access TO the source — which inverts the bridge-host model.
  Either (a) the plugin always runs in a Shepard instance that can reach the
  source, or (b) we keep `mffd-import-v15.py` as the "outside-runner"
  variant for asymmetric-network cases. Decision: keep both variants;
  document the network model in `install.md`.
- **Q3 (Pattern 1 version-drift policy):** what's the default behaviour when
  drift is detected? "Refuse to start" is safest but blocks operators who
  know what they're doing. "Warn and proceed" is more friendly but invites
  drift-bug masking. Default: **warn and proceed**; ops can override to
  **refuse-and-pin** via `:ImporterConfig.driftPolicy`. Match the v15.7
  shape (manifest drives behaviour, operator overrides).
- **Q4 (which sources for v1):** PR-3 ships `DLRv5Source`. Which is PR-5?
  Strong candidates: `LocalDropboxSource` (smallest, validates Pattern 14
  idempotency), `JsonLdSource` (Helmholtz Databus / Unhide adjacency),
  `ConfluenceSource` (already designed in aidocs/integrations/82). My pick:
  `LocalDropboxSource` for breadth-with-low-effort, deferring the larger
  external integrations to PR-6+.

---

**Document boundary:** this is design notes feeding into PR-3 / PR-4 of
shepard-plugin-importer per aidocs/16 #126. It is not a feature spec — the
spec for each pattern lives in the sub-row's eventual PR description.
