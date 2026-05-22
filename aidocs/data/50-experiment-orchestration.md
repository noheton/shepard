---
stage: concept
last-stage-change: 2026-05-23
---

# Experiment Orchestration — Design

**Scope.** A system that **drives a manufacturing-style experiment
end-to-end** — coordinating PLCs (SPS), KUKA robots, and other
OPC/UA-speaking instruments — and **automatically materialises the
shepard graph** (Collection → DataObjects → References → payloads)
as the experiment runs. Builds on `aidocs/39` Templates (T1),
`aidocs/40 §2` Process design+runtime (PR1), `aidocs/40 §3`
shepard-timeseries-collector (sTC), and `aidocs/41` Snapshots (V2)
for checkpoint/restart.

**Status.** Concept design.
**Snapshot date.** 2026-05-08.
**Originating items.** User question: "how could a system be added
that uses sTC and automatically generates the collection and
substructures up to references; build on templates; configurable;
support error handling (restart whole / restart at partial); SPS +
KUKA + OPC/UA integration; three timing strategies (pre-seed /
post-execution / JIT)."

---

## 1. The shape of the problem

A typical DLR-flavoured manufacturing experiment looks like this:

> *"Run the LUMEN-style pre-cool checklist (operator). Start the
> KUKA robot moving the test article into position. Once the PLC
> reports `position_locked = TRUE`, open the fuel valves (PLC).
> Stream sensor data at 100 Hz from 12 OPC/UA tags. After the
> fuel-valve close signal, run a 30-second cooldown. Operator
> writes a debrief lab journal entry. If anything fails, restart
> from the most recent checkpoint."*

Today shepard has the **building blocks** for this:

| Block | Where |
|---|---|
| The Collection / DataObject / Reference graph | core model |
| Templates as DataObject blueprints | `aidocs/39` T1 |
| Processes as typed sequences of templates | `aidocs/40 §2` PR1 |
| OPC/UA + KUKA RSI + MQTT telemetry sources | sTC, `aidocs/40 §3` |
| Lab journal for debriefs | core; J1a markdown extension `aidocs/37` |
| Snapshots for reproducible state | V2, `aidocs/41` |

What's **missing** is the **orchestrator** — the thing that:

1. Listens to PLC/robot events (OPC/UA + KUKA RSI subscribe).
2. Drives the shepard process state machine (PR1) accordingly.
3. Wires telemetry from sTC into the right TimeseriesReferences
   for the active step.
4. Manages **checkpoints** so a failed step can resume without
   losing the prefix.
5. Stays casual-user-friendly per `aidocs/42 §1.0` north star
   (the operator at the test bench shouldn't write Cypher).

---

## 2. The three timing strategies — settled

The user's question lays out three timing models. **All three are
shippable**; pick per recipe (the orchestrator's recipe declares
which mode applies).

### 2.1 (A) Pre-seeding — eager construction

Before the physical experiment starts, the recipe creates the
**entire expected graph**: Collection + every DataObject + empty
TimeseriesReference / FileReference / StructuredDataReference
slots. As the experiment runs, payloads flow into the
pre-allocated slots.

| Pro | Con |
|---|---|
| Analysis tooling sees the shape immediately — dashboards / notebooks can subscribe before payload arrives | Aborted runs leave a polluted graph |
| Operator can pre-validate that the graph matches the experiment plan | Graph footprint exists even before any data is collected |
| Per-slot status (`pending` / `streaming` / `done` / `failed`) gives a real-time progress view | If the experiment shape changes (new sensor mid-run) the pre-seeded graph is wrong |

**Best for: routine production-line experiments** where the shape
is known and the same recipe runs hundreds of times.

### 2.2 (B) Post-execution processing — staging-then-ingest

Physical experiment runs against an external staging area (sTC
buffers to disk; PLC writes to a local DB; operator collects
files). After the run completes, the operator triggers
**ingestion**: the recipe walks the staging area + applies the
template, materialising the shepard graph **only after success**.

| Pro | Con |
|---|---|
| Failed runs never enter shepard | No real-time visibility during the experiment |
| Operator can curate / filter before ingestion | Operator must manage out-of-band staging |
| Decouples shepard's uptime from the experiment's | Late binding breaks the use case "watch the chart while it's running" |

**Best for: shepard-deployment-was-down-during-the-test** retro
fits, and for one-off experimental setups where a clean record is
more valuable than real-time.

### 2.3 (C) Just-in-time generation — lazy construction

Recipe kicks off the shepard process; **DataObjects + References
are created as each step starts**, not before. Telemetry routes
to the just-created reference for the active step.

| Pro | Con |
|---|---|
| Real-time visibility (the chart appears as the data streams) | Schema drift across runs — one run might create N DataObjects, the next N+2 |
| Failed/skipped steps don't pollute the graph | Template strictness harder to enforce ("did this run actually create everything the template required?") |
| Matches the casual-user mental model: "I clicked start, things appear" | Recovery semantics more nuanced (see §6) |

**Best for: research / one-off experiments**, the canonical casual
flow.

### 2.4 Recommendation

Default mode: **(C) JIT** — matches the casual-user expectation +
real-time visibility. Per-recipe override:

- **Pre-seed (A)** for production-line recipes where the shape is
  pinned.
- **Post-process (B)** for offline / disconnected / curated
  workflows.

The recipe carries `mode: "jit" | "preseed" | "post-process"` as
a top-level field.

---

## 3. Architecture

### 3.1 The new component — `shepard-experiment-coordinator`

A small new service (call it the **coordinator**), shipped either
as a separate repo (`gitlab.com/dlr-shepard/shepard-experiment-coordinator`)
or as a Quarkus extension in this fork (decision in §10).

```
                   PLC (SPS)              KUKA robot
                       │                      │
              OPC/UA   │                  KUKA RSI / OPC/UA
                       │                      │
                       ▼                      ▼
            ┌──────────────────────────────────────────┐
            │  shepard-experiment-coordinator          │
            │  ────────────────────────────────────    │
            │  1. Subscribes to OPC/UA + RSI events    │
            │  2. Drives shepard's /v2/process-runs    │
            │     state machine (PR1)                  │
            │  3. Routes sTC sinks to the active       │
            │     step's TimeseriesReference           │
            │  4. Manages checkpoints (snapshots V2)   │
            │  5. Operator-facing UI: start / pause /  │
            │     abort / restart-at-step              │
            └─────────────┬────────────────────────────┘
                          │
              ┌───────────┴────────────┐
              ▼                        ▼
  shepard backend (REST)        sTC (telemetry plumbing)
  /v2/process-runs              Sources → Bridges → Sinks
  /v2/<kind>-references
  /v2/snapshots
```

**Why a separate service** (not in shepard core):

- Long-running. shepard is a stateless REST backend; the
  coordinator holds state for the duration of an experiment
  (could be hours-to-days).
- Industrial-protocol-heavy (OPC/UA / KUKA RSI / Modbus); pulling
  those into shepard's core would inflate the core's dependency
  surface and force every shepard install to ship industrial
  drivers.
- Operator-side. A coordinator deployment lives near the test
  bench (often on the PLC's own subnet); shepard backend often
  lives in a data centre. The orchestrator follows the
  experiment, not the data.

### 3.2 Recipe format — extends Templates (T1) and Processes (PR1)

A **recipe** is a `ProcessDefinition` (per `aidocs/40 §2`) plus
**telemetry binding** + **trigger declarations** + **checkpoint
markers**. The recipe lives in shepard as a `DataObject` in the
`__templates` Collection (per `aidocs/39 §2`) with a marker
`templateKind = "EXPERIMENT_RECIPE"`.

Sketch (JSON; could also be YAML for portability per T1f):

```json
{
  "name": "LUMEN hot-fire test",
  "templateKind": "EXPERIMENT_RECIPE",
  "mode": "jit",
  "telemetry": [
    {
      "stcSource": "opcua://192.168.1.10:4840",
      "tags": ["pc_chamber", "rpm_fuel_pump", "vib_fuel_pump", "..."],
      "destination": {
        "kind": "timeseries-reference",
        "ofStep": "${currentStep}",
        "rate": "100Hz"
      }
    }
  ],
  "steps": [
    {
      "id": "precool",
      "template": "Pre-cool checklist",
      "checkpoint": true,
      "next": "ignition",
      "trigger": { "manual": true }
    },
    {
      "id": "ignition",
      "template": "Sensor run",
      "checkpoint": false,
      "next": "ramp_up",
      "trigger": {
        "opcua": {
          "endpoint": "opcua://192.168.1.10:4840",
          "node": "ns=2;s=PLC.Status.IgnitionAcked",
          "expect": true
        }
      }
    },
    {
      "id": "ramp_up",
      "template": "Sensor run",
      "checkpoint": true,
      "next": "steady_state",
      "trigger": { "duration": "PT10S" }
    },
    {
      "id": "steady_state",
      "template": "Sensor run",
      "checkpoint": true,
      "next": "shutdown",
      "trigger": {
        "opcua": {
          "endpoint": "opcua://192.168.1.10:4840",
          "node": "ns=2;s=PLC.Status.SteadyStateComplete"
        }
      }
    },
    {
      "id": "debrief",
      "template": "Debrief",
      "next": null,
      "trigger": { "manual": true }
    }
  ],
  "errorHandling": {
    "onStepFailure": "checkpoint-restart",
    "maxRetriesPerStep": 2,
    "abortOnTotalFailures": 5
  }
}
```

**Telemetry binding** specifies which OPC/UA tags route to which
references. `${currentStep}` is the orchestrator-substituted
identifier of the active step's DataObject.

**Triggers** advance the state machine — manual (operator clicks
"OK"), OPC/UA bit flip, time-based (`PT10S`), or compound
(any-of / all-of).

**Checkpoints** mark steps where a snapshot (V2) is taken before
moving on. A checkpointed step is **the granularity of restart**.

### 3.3 The orchestrator's REST surface

Sits on the coordinator side (not shepard's REST). Operator UI
talks to:

| Method + path | Purpose |
|---|---|
| `POST /experiments/start` (body: `{recipeId, parameters}`) | Begin a new experiment run |
| `GET /experiments/{runId}` | Read state — current step, telemetry health, last checkpoint |
| `POST /experiments/{runId}/abort` | Hard-stop; mark the shepard ProcessRun as aborted |
| `POST /experiments/{runId}/checkpoint` | Force a checkpoint (snapshot in shepard) at the current step |
| `POST /experiments/{runId}/restart` | Restart from the most recent checkpoint |
| `POST /experiments/{runId}/restart-at/{stepId}` | Restart at a specific prior step's checkpoint |
| `GET /experiments/{runId}/checkpoints` | List checkpoints — `(stepId, snapshotAppId, createdAt)` triples |

Internal: the coordinator drives shepard via the `/v2/process-runs/...`
endpoints (PR1) for the state-machine view and the
`/v2/<kind>-references/...` endpoints for actual entity creation.

---

## 4. Manufacturing integration (SPS / KUKA / OPC/UA)

### 4.1 PLC (SPS) integration

PLCs typically expose **status flags + setpoint registers** via
OPC/UA. The coordinator subscribes to specific nodes:

```
ns=2;s=PLC.Status.IgnitionAcked       # boolean — PLC reports ignition accepted
ns=2;s=PLC.Status.PositionLocked      # boolean
ns=2;s=PLC.Status.FuelValveOpen       # boolean
ns=2;s=PLC.Sensors.ChamberPressure    # float (also fed to sTC for telemetry)
ns=2;s=PLC.Status.EmergencyStop       # boolean — kills the orchestrator
```

The sTC is **already** OPC/UA-source-capable per `aidocs/40 §3`.
Two integrations:

1. **Sensors** — sTC streams the high-rate measurement nodes into
   shepard TimeseriesContainers (existing path).
2. **Status flags** — the coordinator subscribes to the same
   OPC/UA endpoint via its own OPC/UA client (or shares an
   sTC-routed bridge for status events). State changes drive
   trigger evaluation.

Bidirectional: the coordinator may **write back** setpoints
(`PLC.Setpoint.FuelValveTarget`) when a step instructs.
Implementation gates this carefully — write paths require explicit
recipe authorisation + audit log.

### 4.2 KUKA robot integration

KUKA Robot Controllers (KRC2 / KRC4) expose two relevant interfaces:

| Interface | Use |
|---|---|
| **KUKA OPC/UA Server** | Status, program selection, generic events. Use this for trigger detection. |
| **KUKA RSI (Real-time Sensor Interface)** | High-rate (250 Hz - 1 kHz) telemetry for joint angles, forces, etc. sTC already has an RSI source per `aidocs/40 §3`. |

The coordinator uses OPC/UA for state (recipe step
`{robot: kuka, programNumber: 47, expect: "complete"}` becomes an
OPC/UA subscription); telemetry flows through sTC's RSI source
into TimeseriesReferences.

### 4.3 Modbus + REST sources (sTC i9)

`aidocs/40 §3.2 i9` already lists Modbus + REST as new sTC
sources. Once landed, the coordinator inherits them for free —
the recipe's `telemetry.stcSource` field accepts whatever sTC can
speak.

---

## 5. The three timing strategies in the recipe shape

`recipe.mode` toggles the orchestrator's behaviour:

### 5.1 `mode: "preseed"` — strategy (A)

- On `POST /experiments/start`:
  1. Coordinator reads the full recipe.
  2. Creates the Collection.
  3. For every step, instantiates the template's DataObject + every
     declared reference (TimeseriesReference / FileReference / etc.).
  4. Marks each reference `status = "pending"`.
- During execution: telemetry/payloads flow into the
  pre-allocated references. As each step runs:
  - References transition `pending → streaming → done`.
  - Step's `closed_at` attribute set on success.
- On checkpoint: V2 snapshot captures the current state of the
  whole tree.
- On step-level failure: see §6.

### 5.2 `mode: "post-process"` — strategy (B)

- During the experiment: sTC buffers telemetry to a **staging
  bucket** (S3 if FS1 is enabled per `aidocs/45`, otherwise local
  filesystem); PLC writes status to a local journal; operator
  collects files manually.
- On `POST /experiments/{runId}/ingest`:
  1. Coordinator walks the staging area.
  2. Applies the recipe's template structure to materialise the
     shepard graph.
  3. Idempotent — re-running the ingest against the same staging
     bucket is safe.

### 5.3 `mode: "jit"` — strategy (C, default)

- On `POST /experiments/start`: coordinator creates only the
  Collection (no DataObjects yet).
- As each step starts (trigger fires):
  1. Coordinator instantiates the step's template DataObject
     (`POST /v2/collections/{appId}/data-objects/from-template/{templateAppId}`
     per T1e).
  2. Creates the references the template declares.
  3. Routes telemetry to the new references' OIDs.
- On step end: marks the step closed; checkpoints if the recipe
  says so.
- On step failure mid-stream: see §6.

`jit` is the casual default; switching to `preseed` is a recipe
edit, not a coordinator config change.

---

## 6. Error handling — checkpoints + restart

The user asked specifically about "restart the whole process or
restart at a specific partial process." Both shapes ship.

### 6.1 Checkpoints = snapshots

Per the recipe's `step.checkpoint: true` markers, when a step
**successfully completes** the coordinator:

1. Calls `POST /v2/collections/{collectionAppId}/snapshots`
   (V2b, `aidocs/41`) with `name = "experiment-{runId}-after-{stepId}"`.
2. Records the returned snapshot appId in its own
   `experiment_runs.checkpoints` table.
3. Updates the operator UI's "last checkpoint" display.

Snapshots are **logical** (per `aidocs/41 §3.3` option C) —
storage cost is bounded, no payload duplication.

### 6.2 Failure modes

| Failure | Coordinator response |
|---|---|
| **Trigger timeout** (e.g. PLC never reports `IgnitionAcked`) | Mark step failed; pause the run; emit an alert; await operator decision (retry / abort / restart-at) |
| **Telemetry source dropout** (sTC reports OPC/UA disconnect) | Mark current step degraded; retry sTC connection per the recipe's retry budget; if still failing, escalate to operator |
| **shepard backend unavailable** | Buffer state changes in coordinator memory + journal to disk; retry shepard writes with exponential backoff; if persistent, switch to a degraded "telemetry continues, shepard ingestion postponed" mode |
| **Coordinator process crash** | On restart, read `experiment_runs.{runId}.checkpoints` (its own state), reconnect to PLC, decide whether to resume or require operator intervention |
| **Operator-triggered abort** | Mark all in-flight references `status = "aborted"`; freeze the run state; the operator decides whether to keep the partial graph or discard via shepard-admin |

### 6.3 Restart shapes

**Restart whole process** — `POST /experiments/{runId}/restart`:

1. Mark the failed run `aborted`.
2. Create a new run (new `runId`) with a fresh shepard `ProcessRun`.
3. Start from step 1.
4. The previous run's partial graph stays in shepard as a sibling
   (operator can clean up via `shepard-admin`).

**Restart at specific partial process** —
`POST /experiments/{runId}/restart-at/{stepId}`:

1. Find the snapshot tagged at the most recent checkpoint **at or
   before** `stepId`.
2. Discard everything in the live tree that came after that
   checkpoint (mark as `aborted`, soft-delete via shepard's
   existing soft-delete mechanism).
3. Re-create the failed step's DataObject + references (in
   `jit` mode) or reset their `status` (in `preseed` mode).
4. Re-arm telemetry sinks; re-arm trigger subscriptions.
5. Resume.

The restart-at flow exploits V2's logical snapshots: the
"discard everything after checkpoint X" is `MATCH (n) WHERE
n.createdAt > $checkpointTime AND n.collectionAppId = $coll
SET n.deleted = true` — fast, idempotent, snapshot-backed.

### 6.4 Resumability boundaries

**Recipe-author guidance** (lands in the orchestrator's docs):

- Mark steps `checkpoint: true` whenever the step's outputs are
  durable. Long-running data-collection steps should checkpoint;
  fast actuator steps probably shouldn't.
- Consecutive non-checkpointed steps form an **atomic group** — a
  failure restart-at any of them rewinds to the previous
  checkpoint.
- Manual-trigger steps (operator-OK) checkpoint by default — the
  human-in-the-loop time is the cheap pause.

---

## 7. Phasing — EXP1 series ("Experiment orchestration")

| ID | Slice | Size | Gate |
|---|---|---|---|
| **EXP1a** | The coordinator service skeleton — Quarkus app or Go service (decision in §10), config-file recipes only (no shepard-side recipe storage yet), drives `/v2/process-runs/...` (PR1) + `/v2/<kind>-references/...`. JIT mode only. **No telemetry integration yet** (manual triggers only). | L | gated on PR1b (process runtime in shepard core) |
| **EXP1b** | OPC/UA trigger subscription — wires real PLC events into the trigger evaluator. Reuses sTC's OPC/UA driver internals (or pulls in `Eclipse Milo` directly). | M | EXP1a |
| **EXP1c** | sTC sink integration — telemetry from sTC routes to coordinator-managed TimeseriesReference OIDs based on the active step. | M | EXP1a + sTC's i1 (NDJSON ingest, shipped via P14) |
| **EXP1d** | Pre-seed mode — recipe declares the full graph upfront. | M | EXP1a |
| **EXP1e** | Post-process mode — staging-bucket walk + ingest endpoint. | M | EXP1a + FS1 (S3 backend, `aidocs/45`) for the staging story |
| **EXP1f** | Checkpoint + V2 snapshot integration. | M | EXP1a + V2b (snapshots, `aidocs/41`) |
| **EXP1g** | Restart-whole + restart-at-step. | M | EXP1f |
| **EXP1h** | KUKA OPC/UA integration. Recipe step `{robot: kuka, ...}`. | S | EXP1b |
| **EXP1i** | KUKA RSI telemetry routing via sTC's existing RSI source. | S | EXP1c + sTC RSI source |
| **EXP1j** | Operator UI — web, embedded in shepard's frontend `/experiments` route. Shows live state, telemetry sparklines, checkpoint history, restart controls. | L | EXP1a + frontend integration |
| **EXP1k** | Recipe storage in shepard's `__templates` Collection (per T1, `aidocs/39 §2`) with `templateKind = "EXPERIMENT_RECIPE"`. Operator picks recipes from the existing template-picker UI. | M | EXP1a + T1b |
| **EXP1l** | Modbus / REST source integration via sTC's i9. | S | sTC i9 |
| **EXP1m** | (deferred) PLC writeback — coordinator writes setpoints back to PLC. Audit-logged; recipe-authorisation-gated. | M | parked until safety review |
| **EXP1n** | (deferred) Multi-coordinator coordination — for cells with multiple synchronised stations. | L | parked |

Recommended order: **EXP1a → EXP1k → EXP1b → EXP1c → EXP1f →
EXP1g → EXP1j → EXP1d → EXP1e → EXP1h → EXP1i → EXP1l**. EXP1a is
the gate; EXP1k makes recipes shepard-stored from day 1; EXP1b/c
are the manufacturing-environment unblock; EXP1f/g land error
handling; EXP1j ships the operator UX.

---

## 8. Risks

- **Coordinator becomes the single point of failure** for the
  whole test bench. Mitigation: stateless coordinator (state
  journaled to shepard `__experiments_state` Collection +
  optional local SQLite for offline resilience); restart-from-disk
  on coordinator process crash.
- **Recipe complexity creep.** Recipes turn into a programming
  language. Mitigation: keep the schema small (steps + triggers +
  telemetry bindings + checkpoints), refuse general control-flow
  beyond simple linear / parallel / conditional. Anything more
  complex moves to a downstream tool.
- **PLC/robot integration is brittle.** OPC/UA endpoints change
  IP / port, KUKA controllers reboot, network blips. The
  coordinator's retry-with-exponential-backoff matters; a recipe
  failure in the lab is a long, lossy debugging session.
  Mitigation: detailed structured logs + per-step "what was the
  state" snapshots so post-mortem doesn't require live access.
- **Snapshots inflate Neo4j.** Each checkpoint adds entries; a
  long experiment with frequent checkpoints might create 100s of
  snapshots. Mitigation: V2's logical snapshots are cheap; admin
  CLI sweep removes old experiment snapshots per retention policy
  (`aidocs/22`).
- **Real-time visibility lags.** sTC's batch-flush pattern
  introduces a ~1s window between sample and shepard read. For
  monitoring, fine; for control-loop feedback (closing on a
  reading), inadequate. Document the latency budget explicitly;
  state that the orchestrator is **monitor + record**, not
  **closed-loop control**.
- **Industrial security.** OPC/UA endpoints often live on a
  flat-network industrial subnet. The coordinator should run on
  the same subnet (or a hardened bridge), not be exposed to the
  open internet. Document operator-side network architecture.

---

## 9. What this is NOT

- **Not** a closed-loop control system. Coordinator monitors +
  records; PLC owns control logic.
- **Not** a SCADA replacement. Coordinator integrates with
  existing SCADA via OPC/UA; doesn't replace it.
- **Not** a workflow engine like Airflow. Recipes are
  experiment-shaped (per-step manual triggers, checkpointable,
  designed for the lab loop), not data-pipeline-shaped (Airflow
  DAGs are different beasts).
- **Not** a process-modelling-language extension to PR1. Recipes
  build on PR1 by **adding telemetry + triggers + checkpoints** —
  they don't change PR1's fundamentals.
- **Not** a destination for arbitrary device drivers. The
  coordinator integrates with the protocols sTC already speaks
  (OPC/UA, MQTT, KUKA RSI, Modbus when sTC i9 lands). Drivers for
  exotic protocols ship as sTC source plugins (per
  `aidocs/47 §2` plugin SPI for sTC's source layer — out of
  scope for this design).

---

## 10. Open decisions (deferred; do not block EXP1a)

1. **Coordinator language / stack.** Quarkus (matches shepard
   backend) or Go (small, fast, easy to ship as a standalone
   binary on a PLC-adjacent box)? Recommendation: **Quarkus** for
   stack consistency. Re-evaluate at EXP1a if memory / startup
   pressure on edge hardware proves Quarkus is wrong.
2. **Recipe storage shape.** YAML on disk + import into shepard,
   vs. JSON in shepard's `__templates` Collection from day 1. T1f
   handles the YAML round-trip; recommend **JSON-in-shepard** as
   primary, YAML import for portability.
3. **Coordinator-shepard auth.** API key with admin role, or a
   dedicated coordinator role? Recommend a **dedicated
   `experiment_coordinator` role** so the coordinator's writes
   are auditable separately from human-admin writes; depends on
   A0 (admin role mechanism, currently `needs decision` per
   `aidocs/16`).
4. **Multi-coordinator scenarios.** EXP1n parks this; revisit
   when a real multi-station deployment emerges.
5. **Recipe versioning.** Should an experiment in flight refuse
   recipe edits? Recommend **immutable recipe per-run snapshot**
   — when an experiment starts, the recipe's current state is
   pinned (template `templateVersion` + a recipe-snapshot doc);
   subsequent edits to the recipe affect only future runs.

---

## 11. Cross-references

- **aidocs:** `aidocs/16` (EXP1 series queueing entry will follow
  this design; gates on PR1, V2b, FS1, sTC integration),
  `aidocs/22 §4.x` (admin CLI — `shepard-admin experiments
  history` + `purge-aborted` commands), `aidocs/30` (provenance —
  every coordinator action emits OpenLineage events alongside
  the shepard writes), `aidocs/34` (admin-facing tracker — EXP1a
  needs an entry once the new compose service ships), `aidocs/39`
  T1 (templates this builds on), `aidocs/40 §2` PR1 (process
  runtime — recipes extend the ProcessDefinition), `aidocs/40
  §3` sTC (telemetry plumbing), `aidocs/41` V2b (snapshots = the
  checkpoint primitive), `aidocs/42` casual-user north star (the
  operator at the bench is the canonical casual user;
  coordinator's UX matters), `aidocs/43 §5.8` snap dashboards
  (a coordinator-driven experiment is a perfect demo for
  chat-driven analysis), `aidocs/44` (matrix — new "experiment
  orchestration" row), `aidocs/45` FS1 (post-process staging
  story), `aidocs/47 §2` plugin SPI (future sTC source plugins
  ride through this).
- **Code seams (anticipated):** new `shepard-experiment-coordinator`
  repo or module; `__experiments_state` reserved Collection name
  in shepard for coordinator journaling; `templateKind =
  "EXPERIMENT_RECIPE"` discriminator in `__templates`.
- **Backlog:** new **EXP1** umbrella + EXP1a–EXP1n sub-IDs in
  `aidocs/16`. Most slices gate on PR1b + V2b + sTC's existing
  capabilities; EXP1a doesn't itself unlock value — EXP1b and
  EXP1c together do (the actual manufacturing-environment
  integration).
