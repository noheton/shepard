# aidocs/80 — RCE Integration: Data Distribution with Provenance Tracking (`shepard-plugin-rce`)

**Date:** 2026-05-16
**Status:** Design — ready for slice planning.
**Audience:** Contributors; DLR-SC (RCE team); DLR MDO and VPH workflow researchers.
**Purpose:** Specify deep integration between shepard and DLR's Remote Component Environment
(RCE) workflow tool, covering bidirectional data flow and full provenance/lineage tracking.

Couples to: `aidocs/30` (OpenLineage ↔ PROV-O mapping), `aidocs/55` PROV1a
(activity log overhaul, prerequisite for provenance tracking), `aidocs/47` (plugin SPI),
`aidocs/76 §3` (existing RCE2Shepard adapter), `aidocs/79` (CPACS annotator).

---

## 1. What RCE is and the current integration state

### 1.1 What RCE is

**Remote Component Environment (RCE)** is DLR's open-source workflow tool for distributed
multi-disciplinary optimisation (MDO) and simulation chains. It is developed and maintained
by DLR-SC (Institut für Softwaretechnologie).

Core concepts:

- **Components** — individual tool executions (CFD solver, FEM code, CPACS panel method,
  post-processor, Python script). Each component has typed input/output **channels**.
- **Channels** — typed data connections between components: `File`, `Float`, `Integer`,
  `Boolean`, `Directory`, `ShortText`, `Matrix`.
- **Workflow** — a directed graph of components connected by channels. RCE executes the
  graph, routing data between components and handling convergence loops.
- **Distributed execution** — components can run on multiple compute nodes over SSH. A
  researcher on their workstation can orchestrate a workflow where one component runs on
  `bt-au-cube2.intra.dlr.de` and another on `bt-nec-gpu01.intra.dlr.de`.

RCE is the primary workflow engine for the DLR Virtual Product House (VPH) — the
integrated MDO environment used across structural mechanics, aerodynamics, and systems
engineering disciplines.

### 1.2 Current integration state

The `RCE2Shepard` adapter was developed at DLR-SR and described in Dressel et al.
(DGLR 2022, `aidocs/76 §3`). It connects RCE to shepard as a persistence layer for
VPH workflows: after a workflow run, tool outputs are uploaded to a shepard Collection.

**What the existing adapter does:**

- POSTs output files from selected RCE components to a shepard Collection via the
  `/shepard/api/` REST surface.
- Creates DataObjects per uploaded artifact.

**What the existing adapter does not do:**

- Track provenance: which component, at which tool version, on which compute node,
  produced which DataObject.
- Allow RCE to fetch input datasets from a shepard Collection (one-directional: push only).
- Emit OpenLineage events for the workflow execution graph.
- Create `DERIVED_FROM` edges between input and output DataObjects in the provenance graph.
- Handle CPACS-typed data specially (all files are generic `FileReference` objects).

The `shepard-plugin-rce` design closes these gaps and replaces the adapter with a
first-class, bidirectional plugin integration.

---

## 2. Integration vision: RCE as a data distribution node

The vision: every DataObject flowing through an RCE workflow is stored in shepard with a
complete provenance chain. A researcher can answer:

> "Which solver version, with which aerodynamic inputs, on which compute node, produced
> this structural result — and how does it compare to the sweep run from last Tuesday?"

— from the shepard provenance graph, not from memory or email threads.

Three roles for the integration:

| Role | Description |
|---|---|
| **RCE → shepard (sink)** | Components write output datasets to shepard. Each write creates or updates a DataObject in the workflow's Collection. |
| **shepard → RCE (source)** | Components read input datasets from shepard. Researchers pin a specific Collection or DataObject version as the workflow input. |
| **Provenance tracking** | For each component execution, the plugin emits OpenLineage run events and creates `:Activity` nodes linking input DataObjects to output DataObjects. The shepard provenance graph mirrors the RCE workflow graph. |

The `shepard-plugin-rce` makes shepard the **structured MDO data bus** for DLR VPH
workflows — not just a file dump after the fact, but a live, queryable lineage record
throughout the execution.

---

## 3. Architecture: two sides of the integration

### 3.1 RCE side — tool integrations (Java)

RCE's **tool integration framework** exposes external tools as workflow components via a
JSON descriptor file. The plugin ships two new tool integrations:

**`Shepard Source` component**

Given a Collection `appId` and a list of DataObject `appIds` (or a search query), fetches
files and/or structured data from shepard and makes them available on RCE output channels
(`File` or `ShortText`/`Float` type, depending on the DataObject payload kind).

Parameters (RCE input channels or static descriptor values):
- `shepardBaseUrl` — the shepard instance URL (static, set by operator).
- `apiKey` — the API key for the requesting user (static or channel input).
- `collectionAppId` — the source Collection.
- `dataObjectAppIds` — comma-separated list, or empty to use `searchQuery`.
- `searchQuery` — optional free-text or structured query forwarded to `POST /v2/search`.

**`Shepard Sink` component**

Given a Collection `appId`, accepts data from RCE input channels and creates DataObjects
in shepard. Optionally creates a provenance link to the input DataObject that fed this
workflow step.

Parameters (RCE input channels):
- `collectionAppId` — the destination Collection.
- `label` — DataObject label (can be templated: `"CFD result run {runIndex}"`).
- `description` — DataObject description.
- `semanticTerm` — optional ontology term URI for semantic annotation.
- `inputDataObjectAppId` — optional `appId` of the shepard DataObject that was the
  input to this step; used to create the `DERIVED_FROM` edge.

Channels can carry `File`, `Directory`, `Float`, `Matrix`, or `ShortText` values. The
component maps each channel type to the appropriate shepard payload kind (`FileReference`,
`StructuredDataReference`, etc.).

**Descriptor delivery mechanism**

Tool descriptor JSON files and their shell wrapper scripts ship as resources inside the
`shepard-plugin-rce` JAR. On plugin startup, the plugin extracts them to the configured
RCE tool integration directory (configurable via `:RceConfig.rceToolIntegrationPath`).
This mirrors the pattern used by `shepard-timeseries-collector` as a separate sidecar.
Operators point their RCE installations at the same directory on a shared network path, or
distribute the descriptors via the DLR internal package registry.

### 3.2 Shepard side — `shepard-plugin-rce`

Follows the same `PluginManifest` SPI seam as `shepard-plugin-unhide` and
`shepard-plugin-invenio` (per `aidocs/47 §2`).

Registers:

- **REST endpoints** under `/v2/rce/...`
- **Admin REST endpoints** under `/v2/admin/rce/...`
- **CLI subcommands** under `shepard-admin rce ...`
- **A Neo4j singleton config entity** `:RceConfig`

Plugin id: `rce`.

**Researcher-facing REST surface:**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/rce/events` | Webhook: RCE POSTs OpenLineage `RunEvent` JSON here (HMAC-validated) |
| `GET` | `/v2/rce/workflows` | List workflow runs tracked in this shepard instance |
| `GET` | `/v2/rce/workflows/{workflowRunId}` | Provenance graph for one workflow run (inputs, outputs, component activities, timings) |
| `POST` | `/v2/collections/{appId}/dataobjects/batch` | Batch DataObject creation (RCE1f) |

Permissions: `POST /v2/rce/events` is validated by HMAC only (no user auth — it is
called by the RCE runtime, not a browser). All `GET` endpoints require at minimum
Reader access on the associated Collection.

---

## 4. Data model: workflow provenance in Neo4j

### 4.1 Collection and DataObject structure

When a workflow starts (triggered by the `WORKFLOW_START` event on `POST /v2/rce/events`),
the plugin creates a Collection per the configured `workflowCollectionStrategy` (see §8).

```
(:Collection {
  appId: <uuid>,
  name: "RCE Workflow: <workflowName>",
  description: "Workflow run <workflowRunId> started <startTime>"
})
  -[:HAS_DATA_OBJECT]->
(:DataObject {
  appId: <uuid>,
  name: "<componentName> run <n>",
  description: "Output of component <componentName> in workflow <workflowName>"
})
  -[:HAS_FILE_REFERENCE]-> (:FileReference { ... })
  -[:HAS_STRUCTURED_DATA_REFERENCE]-> (:StructuredDataReference { ... })
```

### 4.2 Derivation edges

Each component step that reads from one DataObject and writes to another gets a
`DERIVED_FROM` edge in the graph:

```
(:DataObject)   ← output of component step
  -[:DERIVED_FROM]->
(:DataObject)   ← input to the same step (from prior step or Shepard Source)
```

This mirrors the RCE channel graph in the shepard provenance graph: data lineage is
traversable by following `DERIVED_FROM` chains.

### 4.3 `:Activity` nodes (PROV-O)

Each component execution becomes an `:Activity` node (per PROV1a, `aidocs/55`):

```cypher
(:Activity {
  appId: <uuid>,
  activityType: "RCE_COMPONENT_EXECUTION",
  componentName: "CPACS_Aerodynamics_Panel",
  workflowRunId: "<rceRunUUID>",
  workflowName: "VPH_MDO_Loop_v3",
  startTime: <ISO8601>,
  endTime: <ISO8601>,
  rceToolVersion: "10.4.0",
  rceComponentVersion: "1.2",
  rceNodeHost: "bt-au-cube2.intra.dlr.de"
})
  -[:USED]-> (:DataObject)        -- input DataObjects
  -[:GENERATED]-> (:DataObject)   -- output DataObjects
  -[:WAS_ASSOCIATED_WITH]-> (:User)   -- researcher who triggered the run
```

This is fully PROV-O-compatible:

| `:Activity` field | PROV-O mapping |
|---|---|
| `:Activity` node | `prov:Activity` |
| `-[:USED]->` | `prov:used` |
| `-[:GENERATED]->` | `prov:wasGeneratedBy` (inverse) |
| `-[:WAS_ASSOCIATED_WITH]->` | `prov:wasAssociatedWith` |
| `startTime` / `endTime` | `prov:startedAtTime` / `prov:endedAtTime` |

The full PROV-O graph is queryable via `GET /v2/rce/workflows/{workflowRunId}` and
exportable as RDF/Turtle for submission to the DLR MOSS federation layer (`aidocs/77`).

---

## 5. OpenLineage emission

### 5.1 The gap

RCE does not natively emit OpenLineage events. The integration requires a mechanism to
capture data flow metadata and POST it to `POST /v2/rce/events`.

### 5.2 RCE Observer component (preferred approach)

The plugin ships an **RCE Observer** — a new RCE tool integration component that sits
between connected components in the workflow:

```
[CFD Solver] --[File channel]--> [RCE Observer] --[File channel]--> [CPACS Assembler]
                                       │
                                       └── POST /v2/rce/events
                                           (OpenLineage RunEvent JSON)
```

The observer transparently passes data through (it does not modify the payload) while
capturing:
- The channel name and data type
- The upstream component name, version, and host
- The downstream component name
- A fingerprint (SHA-256) of the file/parameter value for data integrity tracking

Insertion of the observer is done via a **workflow template** that the plugin provides —
researchers apply the template to instrument their workflow, or the plugin provides a
pre-run instrumentation CLI step.

### 5.3 Script-component wrapper (v1 fallback)

A simpler v1 approach: an RCE script component (Python) wraps each tool invocation and
emits events. This requires no RCE-framework-level changes and works with any existing
workflow, at the cost of being explicit in the workflow graph (less transparent to the
designer).

The plugin ships both; operators choose per deployment.

### 5.4 OpenLineage namespace and event format

- **Namespace URI**: `https://rce.dlr.de/` (or `dlr.rce` as the short form)
- **Job name**: `<workflowName>/<componentName>`
- **Run ID**: UUID derived from the RCE execution run ID (deterministic, so re-runs of
  the same component get distinct run IDs even within the same workflow run)

Event types mapped from RCE lifecycle:

| RCE lifecycle point | OpenLineage event type | shepard action |
|---|---|---|
| Workflow start | `START` (job = workflow) | Create Collection + workflow `:Activity` |
| Component start | `START` (job = component) | Create component `:Activity`, record inputs |
| Component finish | `COMPLETE` or `FAIL` | Close `:Activity`, create DataObjects for outputs |
| Workflow finish | `COMPLETE` or `FAIL` | Close workflow `:Activity`, finalise Collection |

### 5.5 OpenLineage → PROV-O translation

The `aidocs/30` OpenLineage ↔ PROV-O mapping layer is the translation point. Incoming
`RunEvent` JSON on `POST /v2/rce/events` → PROV-O `:Activity` + `:Entity` nodes in
Neo4j. The plugin registers a `RunEventHandler` that implements this translation using
the mapping rules established in `aidocs/30`.

---

## 6. CPACS workflow provenance (with `aidocs/79`)

For CPACS-heavy MDO workflows, the integration provides richer typing than generic file
DataObjects:

- Each RCE component that reads or writes a CPACS file (detected by MIME type
  `application/xml` + CPACS schema namespace, or by file extension `.xml` +
  CPACS header, per CPACS1b in `aidocs/79`) creates or reads a `CpacReference`
  DataObject rather than a generic `FileReference`.
- The derivation chain in Neo4j mirrors the CPACS configuration evolution through the
  MDO loop:

  ```
  (:DataObject { name: "CPACS_init" })
    <-[:DERIVED_FROM]-
  (:DataObject { name: "CPACS_after_aero_panel" })
    <-[:DERIVED_FROM]-
  (:DataObject { name: "CPACS_after_structural_FEM" })
    <-[:DERIVED_FROM]-
  (:DataObject { name: "CPACS_converged" })
  ```

- This chain is browsable in the shepard UI as "CPACS evolution history for workflow
  run `<uuid>`": the researcher can inspect the CPACS XML at each iteration, diff
  specific tool model sections, and identify which component introduced a configuration
  change.

This feature is gated on CPACS1b (`aidocs/79`) and RCE1b (see §9).

---

## 7. Data distribution: shepard as the MDO data bus

### 7.1 Parameter sweeps and optimisation loops

For large-scale MDO campaigns (parameter sweeps, optimisation loops), RCE runs the same
workflow many times with different inputs. shepard becomes the structured repository for
all parameter combinations and results:

- Each sweep iteration → one DataObject per component output, tagged with the iteration
  index and parameter values as structured attributes.
- The parameter sweep itself → one Collection, with one DataObject per iteration result.
- The `workflowCollectionStrategy: ONE_PER_WORKFLOW` mode groups all sweep iterations
  under one Collection, with the iteration index encoded in the DataObject label.

A researcher can query across the entire sweep via `POST /v2/search`:

```json
{
  "query": "aerodynamics panel CFD",
  "filters": {
    "collectionAppId": "<sweepCollectionAppId>",
    "attributes": { "iterationIndex": { "gte": 0, "lte": 99 } }
  }
}
```

Or, for timeseries outputs: `POST /sql/timeseries` with iteration index as the x-axis.

### 7.2 Batch DataObject ingest

For high-frequency optimisation loops (hundreds of iterations per hour), per-component
REST calls create API latency that degrades workflow throughput. The plugin provides a
**batch ingest API**:

```
POST /v2/collections/{appId}/dataobjects/batch
```

Body: an array of DataObject creation payloads (up to `batchSize`, default 20, configurable
via `:RceConfig.batchSize`). The endpoint creates all DataObjects in a single Neo4j
transaction and returns the created `appId` list.

The `Shepard Sink` RCE component accumulates outputs across iterations and flushes in
batches, reducing round-trip overhead for high-frequency sweeps.

---

## 8. Admin configuration surface

Follows the CLAUDE.md `admin-configurable at runtime` standing rule.

### 8.1 `:RceConfig` Neo4j singleton

```cypher
(:RceConfig {
  appId: "rce-config",
  enabled: false,
  workflowCollectionStrategy: "ONE_PER_RUN",  // ONE_PER_RUN | ONE_PER_WORKFLOW | SHARED
  provenanceTrackingEnabled: true,
  openLineageNamespace: "https://rce.dlr.de/",
  batchSize: 20,
  rceToolIntegrationPath: "/opt/shepard/rce-tools",  // where descriptors are extracted
  rceWebhookSecret: "<HMAC-secret>"  // stored encrypted; validates POST /v2/rce/events
})
```

### 8.2 Admin REST surface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/rce/config` | Current config (webhook secret masked as `"***"`) |
| `PATCH` | `/v2/admin/rce/config` | RFC 7396 merge-patch; `@RolesAllowed("instance-admin")` |
| `POST` | `/v2/admin/rce/webhook-secret/rotate` | Generate and store a new HMAC webhook secret |
| `GET` | `/v2/admin/rce/status` | Last received event, active workflow count, total DataObjects created by this plugin |

### 8.3 CLI parity

```
shepard-admin rce status
shepard-admin rce enable
shepard-admin rce disable
shepard-admin rce set-collection-strategy ONE_PER_RUN
shepard-admin rce set-batch-size 50
shepard-admin rce set-namespace https://rce.dlr.de/
shepard-admin rce set-tool-path /opt/shepard/rce-tools
shepard-admin rce rotate-webhook-secret
shepard-admin rce health
```

All flags: `--url`, `--api-key`, `--output={human,json}`.

### 8.4 Precedence rule

Runtime `:RceConfig` values win over deploy-time `application.properties` keys
(e.g. `shepard.plugins.rce.batch-size`). The deploy-time key seeds the singleton on
first start; subsequent admin `PATCH` calls override it without restart.

---

## 9. Phasing (RCE1a–RCE1f)

| Phase | ID | Deliverable | Size | Gate |
|---|---|---|---|---|
| 1 | **RCE1a** | Plugin stub: `PluginManifest`, `:RceConfig` singleton, admin `GET/PATCH/rotate-secret/status`, CLI `status/enable/disable/set-*`; `POST /v2/rce/events` webhook skeleton — accepts and stores raw OpenLineage JSON, no graph construction yet | M | PM1a (plugin SPI) |
| 2 | **RCE1b** | OpenLineage → PROV-O/Neo4j mapping: `RunEvent` → `:Activity` + DataObject creation + `DERIVED_FROM` edges; `ONE_PER_RUN` collection strategy; `GET /v2/rce/workflows` list endpoint; `GET /v2/rce/workflows/{workflowRunId}` provenance graph endpoint | L | PROV1a (`aidocs/55`) |
| 3 | **RCE1c** | **`Shepard Sink` RCE tool integration** — JSON component descriptor + shell wrapper; ships as JAR resources extracted to `rceToolIntegrationPath` on startup; creates DataObjects via `POST /v2/collections/{appId}/dataobjects` | M | RCE1b |
| 4 | **RCE1d** | **`Shepard Source` RCE tool integration** — fetches DataObjects from a shepard Collection into RCE channels by `appId` or search query; `GET /v2/collections/{appId}/dataobjects` + `POST /v2/search` | M | RCE1c |
| 5 | **RCE1e** | CPACS-aware provenance — detect CPACS MIME type on DataObject creation, create `CpacReference` instead of generic `FileReference`; CPACS derivation chain in the MDO loop; CPACS evolution history view | M | CPACS1b (`aidocs/79`) + RCE1b |
| 6 | **RCE1f** | Batch DataObject ingest API (`POST /v2/collections/{appId}/dataobjects/batch`) + parameter-sweep collection structure + `Shepard Sink` batch flush mode | M | RCE1c |

**RCE1a + RCE1b are the minimal viable integration.** RCE1c + RCE1d complete the
bidirectional data flow. RCE1e adds CPACS-typed provenance. RCE1f enables high-throughput
sweep campaigns.

---

## 10. Relationship to the existing RCE2Shepard adapter

`shepard-plugin-rce` is designed as the **successor** to the `RCE2Shepard` adapter:

| Capability | RCE2Shepard (existing) | `shepard-plugin-rce` |
|---|---|---|
| Push outputs to shepard | Yes (one-directional) | Yes (via Shepard Sink component) |
| Fetch inputs from shepard | No | Yes (via Shepard Source component) |
| Provenance / lineage tracking | No | Yes (OpenLineage → PROV-O → Neo4j) |
| CPACS-typed DataObjects | No (generic file) | Yes (RCE1e, gated on CPACS1b) |
| Batch ingest for sweeps | No | Yes (RCE1f) |
| Admin-configurable at runtime | No | Yes (`:RceConfig` + admin REST) |
| Workflow provenance graph view | No | Yes (`GET /v2/rce/workflows/{id}`) |

Migration path for existing VPH workflows using `RCE2Shepard`:

1. Deploy `shepard-plugin-rce` alongside the existing adapter (no breakage — the plugin
   adds new endpoints; it does not touch `/shepard/api/` paths).
2. Replace the `RCE2Shepard` component in each workflow with the `Shepard Sink` component
   (the tool descriptor is a drop-in for the same channel types; DataObject structure is
   identical).
3. Optionally add the `Shepard Source` component for input pinning.
4. Remove `RCE2Shepard` once all workflows are migrated.

The migration is per-workflow, not a flag-day cutover. Both can run in parallel during
transition. No database migration is required.

---

## 11. See also

- `aidocs/79` — CPACS Annotator (CPACS provenance chain; RCE1e gated on CPACS1b)
- `aidocs/30` — Provenance and lineage design (OpenLineage ↔ PROV-O mapping)
- `aidocs/55` — Activity log → PROV-O provenance overhaul (PROV1a prerequisite for RCE1b)
- `aidocs/76 §3` — Dressel et al. DGLR 2022 (existing RCE2Shepard adapter, DLR-SR)
- `aidocs/47` — PayloadKind / PayloadStorage SPI (plugin registration pattern)
- `aidocs/77` — Databus + MOSS federation (RCE provenance graphs as PROV-O Turtle for federation)
- `aidocs/34` — Upstream upgrade path tracker (migration notes for this feature)
- https://rcenvironment.de — RCE project homepage (DLR-SC)
