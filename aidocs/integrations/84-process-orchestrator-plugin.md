# 84 — shepard-plugin-process-orchestrator

**Status:** Design
**Audience:** Contributors familiar with the PayloadKind SPI; does not require knowledge of the DRG tool
**Depends on:** L2d (appId-first v2 surface), ANC-1 (predecessor/successor API),
IMP1 (import validate/commit), TM1a (PATCH /v2/timeseries-references — TM1 fields),
`:OrchestratorConfig` Neo4j singleton (defined here)
**Relates to:** `aidocs/data/50-experiment-orchestration.md` (broader orchestration vision),
`aidocs/integrations/83-rebar-airflow-integration.md` (integration precedent),
`aidocs/platform/47-dev-experience-and-plugin-system.md` (PayloadKind SPI),
`aidocs/platform/32-long-running-process-pattern.md` (async job pattern)
**Snapshot date:** 2026-05-21

---

## 1. Problem Statement

DLR ZLP runs a 2022 Python tool ("Data Reference Generator," DRG) that bridges an
AFP (Automated Fiber Placement) robot cell to Shepard. When the KUKA KRC controller
signals a new layer or track via OPC UA, the DRG creates a DataObject hierarchy in
Shepard and opens a TimeseriesReference window. When the step ends, it closes the
window and broadcasts Shepard appIds back to co-process equipment (HUMM3 ultrasonic,
TPS heating, MTLH consolidation).

The pattern is generalizable: any process equipment that emits discrete
"step started / step ended" signals should be able to auto-create Shepard provenance
structure — without a custom Python sidecar per cell with hardcoded API calls.

This design generalizes the DRG pattern to a **TriggerSource SPI** plus
**HierarchyTemplate** approach, packaged as `shepard-plugin-process-orchestrator`.
It covers the JIT-materialisation mode from `aidocs/data/50-experiment-orchestration.md §2.3`
(create DataObjects exactly when steps fire); the broader orchestration vision
(checkpoint/restart, recipe-driven three-mode sequencing) remains in `aidocs/50`.

---

## 2. Core Concepts

### TriggerSource (intra-plugin SPI)

An interface **inside the plugin** (not in core's `de.dlr.shepard.spi.payload`) that fires
events when a discrete process step starts or ends. Four implementations ship in v1:

| Implementation | Signal source |
|---|---|
| `OpcUaTriggerSource` | OPC UA node value changes (the DRG pattern) |
| `WebhookTriggerSource` | HTTP POST from any system |
| `MqttTriggerSource` | MQTT topic subscription (already in the stack via home-showcase) |
| `ManualTriggerSource` | "Start step / End step" buttons in the admin UI |

```java
// de.dlr.shepard.orchestrator.trigger.TriggerSource (intra-plugin)
public interface TriggerSource {
    String id();              // unique name for status reporting
    void start(TriggerListener listener);  // begin subscription
    void stop();
    SourceStatus status();    // CONNECTED / DISCONNECTED / ERROR
}

public interface TriggerListener {
    void onStepStarted(StepEvent event);
    void onStepEnded(StepEvent event);
}

public record StepEvent(
    String stepType,          // "LAYER", "TRACK", "WELD", etc.
    int    stepNumber,
    Map<String, String> metadata,  // values read at trigger time
    Instant firedAt
) {}
```

### HierarchyTemplate

A JSON/YAML document declaring what DataObject tree to create when a step fires.
Each level specifies a name template (using `{stepNumber}`, `{timestamp}`, `{metadata.*}`
expressions), initial status, default attributes, and channels to open as
TimeseriesReferences.

### TimeseriesReference Lifecycle

When a step starts the plugin calls `POST /shepard/api/collections/{c}/dataObjects/{do}/timeseriesReferences`
with `start = now()` and `end = Long.MAX_VALUE` as a sentinel — the reference is "open."
When the step ends the plugin deletes the open reference and immediately re-creates it
with the measured start/end bounds. This two-phase approach is required because the
existing PATCH endpoint (TM1a) only mutates time-alignment fields, not `start`/`end`.

Datapoint ingestion is out of scope for the orchestrator. `sTC` (`aidocs/40 §3`) or an
equivalent collector (e.g. the home-showcase MQTT bridge pattern) writes measurement
points into the TimeseriesContainer; the orchestrator only opens and seals the reference
window that marks which time range belongs to which process step.

**API gap logged:** A dedicated `PATCH /v2/timeseries-references/{appId}` that can
seal `end` on an open reference would simplify this. Track as **OR1a** (Orchestrator
API gap #1); the two-phase fallback is safe until it lands.

### Context Broadcast

After creating DataObjects the plugin can write Shepard appIds back to any
TriggerSource that supports write (OPC UA server nodes, MQTT publish, webhook callback
URL). This closes the loop for co-process equipment that needs to tag their own data
with Shepard identifiers.

---

## 3. OpcUaTriggerSource Detail

The DRG cell uses two OPC UA nodes on the KUKA KRC controller:

| Node | Meaning |
|---|---|
| `ns=2;s=drg_data_valid` | Boolean: true when layer/track data is stable |
| `ns=2;s=VC_PLYNUMBER` | Current layer number |
| `ns=2;s=VC_TRACKNO` | Current track number |

**Behavior:** subscribe to `drg_data_valid`. On rising edge, read `VC_PLYNUMBER` and
`VC_TRACKNO`, fire `StepStarted(LAYER, n, ...)` and `StepStarted(TRACK, m, ...)`.
On falling edge, fire `StepEnded`. After DataObject creation, write the new appIds to
broadcast nodes (`ns=2;s=LayerID`, `ns=2;s=TrackID`).

```yaml
# OrchestratorConfig.opcua (stored in Neo4j singleton, editable via admin REST)
serverUrl: "opc.tcp://192.168.10.11:4840"
securityMode: NONE          # or SIGN, SIGN_AND_ENCRYPT
triggerNodes:
  - nodeId: "ns=2;s=drg_data_valid"
    type: GATE_BOOLEAN       # rising edge → StepStarted, falling → StepEnded; other types: VALUE_CHANGE, THRESHOLD
    stepsToFire: [LAYER, TRACK]
readNodes:
  - nodeId: "ns=2;s=VC_PLYNUMBER"
    metadataKey: "layerNumber"
  - nodeId: "ns=2;s=VC_TRACKNO"
    metadataKey: "trackNumber"
broadcastNodes:
  - stepType: LAYER
    nodeId: "ns=2;s=LayerID"
  - stepType: TRACK
    nodeId: "ns=2;s=TrackID"
```

**OPC UA library:** use `org.eclipse.milo:sdk-client:0.6.x` (Eclipse Milo, EPL-2.0,
compatible with Shepard's license policy). This is the recommended default. A successor
library from the original Milo author (`com.digitalpetri.opcua`) also exists; verify
its current licensing before adopting, as the commercial and open-source offerings
have diverged.

**Worker lifecycle:** a `@Startup` CDI bean (`OrchestratorLifecycleBean`) initialises
all configured TriggerSources on application start and holds long-lived subscriptions.
On reconnect failure the bean applies exponential back-off (initial 5s, cap 5min) and
emits a `WARNING` PROV1a activity so the audit trail shows connectivity gaps. The
CDI bean surfaces connection state to `GET /v2/admin/orchestrator/status`.

---

## 4. Admin REST Surface

All endpoints are `@RolesAllowed("instance-admin")`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/v2/admin/orchestrator/config` | Current `:OrchestratorConfig` as JSON |
| `PATCH` | `/v2/admin/orchestrator/config` | RFC 7396 merge-patch; restarts affected TriggerSources |
| `GET` | `/v2/admin/orchestrator/status` | `[{sourceId, state, lastEventAt, errorMessage}]` per TriggerSource |
| `POST` | `/v2/admin/orchestrator/trigger` | Manual fire for testing — body: `{stepType, stepNumber, metadata}` |
| `GET` | `/v2/admin/orchestrator/templates` | List configured HierarchyTemplates |
| `POST` | `/v2/admin/orchestrator/templates` | Create or replace a template (idempotent by `id`) |
| `DELETE` | `/v2/admin/orchestrator/templates/{id}` | Remove a template |

Mutations are captured by `ProvenanceCaptureFilter` (PROV1a) automatically. Admin CLI
parity: `shepard-admin orchestrator {status, enable, disable, trigger, templates-list, template-set}`.

`:OrchestratorConfig` is a single-instance Neo4j entity (A3b pattern). Fields:
`enabled`, `opcua.*`, `webhook.*`, `mqtt.*`, and the bound template IDs. Deploy-time
defaults in `application.properties` seed the singleton on first start; runtime PATCH
wins over deploy-time values.

---

## 5. HierarchyTemplate — MFFD AFP Cell Example

```yaml
# Template ID: mffd-afp-cell-v1
id: mffd-afp-cell-v1
description: "MFFD AFP robot cell — KUKA KRC trigger"
targetCollectionAppId: "${COLLECTION_APP_ID}"  # resolved from env at startup

levels:
  - stepType: EXPERIMENT
    trigger: MANUAL          # operator starts an experiment run
    nameTemplate: "AFP Run {timestamp:yyyyMMdd-HHmm}"
    status: IN_PROGRESS
    attributes:
      cell: "AFP-ZLP-1"
      material: "CF/LMPAEK"

  - stepType: LAYER
    trigger: OPCUA           # driven by VC_PLYNUMBER rising edge
    nameTemplate: "Layer {metadata.layerNumber}"
    parentStepType: EXPERIMENT
    status: IN_PROGRESS
    attributes:
      layer_number: "{metadata.layerNumber}"
    channels:
      - measurement: "R20"
        device: "robot"
        location: "MFZ"
        symbolicName: "AFP_head"
        field: "force_z"
      - measurement: "TPS"
        device: "heating"
        location: "MFZ"
        symbolicName: "TPS_main"
        field: "temperature"
    # Post-TS-IDa: replace the 5-tuple above with timeseriesAppId references
    # once the timeseries appId backfill (TS-IDa) is complete.

  - stepType: TRACK
    trigger: OPCUA           # driven by VC_TRACKNO rising edge
    nameTemplate: "Track {metadata.trackNumber}"
    parentStepType: LAYER
    status: IN_PROGRESS
    attributes:
      track_number: "{metadata.trackNumber}"
    channels:
      - measurement: "MTLH"
        device: "consolidation"
        location: "MFZ"
        symbolicName: "MTLH_roller"
        field: "pressure"
```

The name templates are evaluated with a small expression engine (no reflection, no
scripting runtime): `{metadata.<key>}`, `{timestamp:<format>}`, `{stepNumber}`.

---

## 6. Frontend Requirements

**Admin panel — Connection status widget**

Under Settings → Orchestrator, display a table of configured TriggerSources with a
green/yellow/red indicator (`CONNECTED` / `RECONNECTING` / `ERROR`), the last event
timestamp, and a "Test trigger" button per source (calls `POST /v2/admin/orchestrator/trigger`).

**Collection view — "Orchestrated runs" badge**

If a collection has DataObjects created by the orchestrator (attribute
`orchestrator.plugin = "process-orchestrator"` present), show a small label so
contributors know the structure is auto-generated.

**DataObject detail — Provenance metadata**

When a DataObject carries `orchestrator.stepType`, `orchestrator.sourceId`, and
`orchestrator.firedAt` attributes, display them in the "Created by" section of the
detail pane (not in a modal — inline alongside the standard provenance block).

---

## 7. Migration Path from DRG

Teams at ZLP Augsburg running the 2022 DRG Python sidecar can migrate incrementally:

| Step | Action |
|---|---|
| 1 | Deploy `shepard-plugin-process-orchestrator` alongside the existing DRG. Both can coexist: DRG writes to the `/shepard/api/` v1 surface; the plugin writes to `/v2/`. No port or node conflicts. |
| 2 | Configure `OpcUaTriggerSource` pointing at the same KRC OPC UA endpoint. Verify in the admin status panel that `CONNECTED` appears. |
| 3 | Import the `mffd-afp-cell-v1` template, adjusting `nameTemplate` to match the existing DataObject naming convention so historical data stays consistent with new runs. |
| 4 | Run a test layer via `POST /v2/admin/orchestrator/trigger` and inspect the DataObject it creates. Compare with DRG output. |
| 5 | Once validated over 2–3 production runs, stop the DRG sidecar. Existing historical DataObjects from DRG require no migration — they stay as-is in Shepard. |

The DRG wrote `projectId`, `experimentId`, `layerID`, `trackID` as integer IDs (v1
numeric). The plugin writes `appId` (UUID v7) to the broadcast nodes. If downstream
systems read from those OPC UA broadcast nodes, their consumers need updating to accept
UUID strings instead of integers. Flag this as a **BREAKING** change for those consumers
in `aidocs/34` when the decommission step lands.

---

## 8. Generalizations Beyond AFP

The TriggerSource + HierarchyTemplate pattern applies to any cell that emits
discrete step signals:

**Resistance welding cell (aidocs/34 MFFD §2)**
Each weld pulse is a `StepStarted(WELD, n)` → `StepEnded` pair. The template creates
a DataObject per weld with attributes `weld_energy_J`, `weld_duration_ms` captured
from OPC UA at step-end. TimeseriesReferences cover the current/voltage/force channels
for the weld window.

**NDT scanner (phased-array UT)**
A scan pass triggers `StepStarted(SCAN_PASS, n)` with `{scan_geometry, part_id}` in
metadata. The template creates a DataObject per pass. After `StepEnded`, the plugin
attaches a FileReference placeholder for the C-scan image (populated by the scanner
software via the `POST /v2/…/file-references/register` endpoint, tracked as RB2b in
`aidocs/83`).

**Environmental test chamber (thermal cycling)**
A thermal profile controller (ESPEC, Vötsch) emits step start/end per profile phase.
The template creates a DataObject per profile step with attributes
`target_temp_C`, `ramp_rate_Cmin`. The MQTT or webhook trigger source is preferred here
since chamber controllers commonly expose HTTP or MQTT interfaces.

---

## 9. Trade-offs and Risks

**OPC UA library choice.** Eclipse Milo (`org.eclipse.milo:sdk-client:0.6.x`, EPL-2.0)
is the recommended library. It is well-maintained, used in production at DLR, and
license-compatible. Add to `plugins/shepard-plugin-process-orchestrator/pom.xml`; do
not add to the backend core POM (plugin isolation).

**Long-lived connection management.** OPC UA subscriptions must survive backend
restarts. The `@Startup` CDI bean's `stop()` method (on `@PreDestroy`) cleanly
unsubscribes before Quarkus shuts down. On restart the bean re-subscribes from
scratch — no state is persisted. Events that fired during downtime are silently lost;
this is acceptable for JIT materialisation (the physical step is already over). If
zero-loss is required, switch to mode (B) post-execution processing from `aidocs/50 §2.2`.

**Concurrent step handling.** Multiple robots or multi-track-parallel runs require
the template engine to correctly scope parent DataObjects. The HierarchyTemplate
`parentStepType` field resolves the parent by finding the most recent open DataObject
of that type for the same source. Where true parallelism occurs (two simultaneous
tracks on different robots), scope disambiguation uses `metadata.robot_id` or an
equivalent discriminator key declared in the template's `parentScopeKey` field.

**TimeseriesReference seal gap (OR1a).** Until a `PATCH /v2/timeseries-references/{appId}`
endpoint can seal the `end` field, the plugin uses delete-and-recreate. This is a
brief (< 1 ms) window where the reference does not exist. Consumers that poll for
references during this window will see a momentary absence. This is logged at
`DEBUG` level. The OR1a endpoint eliminates the gap and is the recommended follow-up.

**No dependency on aidocs/32 JobService.** The orchestrator's core loop is a persistent
subscription, not a finite async job. The JobService pattern (`aidocs/32`) is
appropriate for bounded work (export, ingest). Here the subscription is open-ended;
use the `@Startup` supervisor pattern instead.

---

## 10. Implementation Plan

| Step | Artefact | Size |
|---|---|---|
| **OR1a** | `PATCH /v2/timeseries-references/{appId}` — extend TM1a endpoint to accept `end` field seal | S |
| **OR1b** | `TriggerSource` SPI + `StepEvent` + `TriggerListener` interfaces (intra-plugin) | S |
| **OR1c** | `OrchestratorLifecycleBean` (`@Startup`) + reconnect supervisor | S |
| **OR1d** | `OpcUaTriggerSource` (Eclipse Milo subscription) + config model | M |
| **OR1e** | `HierarchyTemplate` parser + DataObject/TimeseriesReference materialisation against v2 API | M |
| **OR1f** | Admin REST + `:OrchestratorConfig` Neo4j singleton + CLI parity | S |
| **OR2a** | `WebhookTriggerSource` (POST endpoint) | S |
| **OR2b** | `MqttTriggerSource` (paho-mqtt, already used in home-showcase collector) | S |
| **OR2c** | `ManualTriggerSource` + admin UI trigger button | S |
| **OR3a** | Frontend: status widget + orchestrated-runs badge + DataObject detail tag | M |
| **OR3b** | Plugin docs: `reference.md`, `quickstart.md`, `install.md` (per CLAUDE.md rule) | S |

OR1b → OR1c → OR1d and OR1e can proceed in parallel after OR1a. OR2x are independent.
OR3a ships with OR2c. OR3b ships with OR1f.

When any step lands, update `aidocs/34-upstream-upgrade-path.md` (admin-visible change)
and `aidocs/44-fork-vs-upstream-feature-matrix.md` (feature matrix row for the
process-orchestrator plugin).

Plugin documentation lives at
`plugins/shepard-plugin-process-orchestrator/docs/{reference,quickstart,install}.md`.
Until the auto-discovery route (`aidocs/ops/49`) ships, reference these from
`docs/reference/plugins.md`.
