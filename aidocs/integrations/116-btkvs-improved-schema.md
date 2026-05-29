---
title: BT-KVS docket improved Shepard schema — refinements to Shepard_Database_Setup.drawio
stage: idea
last-stage-change: 2026-05-29
audience: contributors + BT-KVS group external + ontologist
---

# 116 — BT-KVS docket improved schema

**Status:** idea · companion to [`aidocs/agent-findings/btkvs-docket-showcase-2026-05-29.md`](../agent-findings/btkvs-docket-showcase-2026-05-29.md) (the full BT-KVS package analysis) · response to Nils's explicit invitation in `Targets.md §3` ("if you have ideas how to improve the setup structure, create a .md with the description and a new drawio with the improved structure") · tracker row [`BTKVS-A4`](../16-dispatcher-backlog.md#btkvs-docket-showcase--targetsmd-realignment-follow-ups).

This is a **design doc only** — no code, no migrations. The aim is to give the BT-KVS group (Nils + Dennis) a concrete revised target shape that they can accept, reject, or amend before any plugin or seed script lands. Companion diagram: [`116-btkvs-improved-schema.drawio`](116-btkvs-improved-schema.drawio).

---

## 1. Motivation

The original `Shepard_Database_Setup.drawio` (operator-uploaded in `Nils-Packet-fuer-Claude.zip`, lives in `~/.claude/uploads/`, never in this repo per `feedback_uploads_never_in_repo.md`) is the right starting point — Nils already arrived at the correct Collection-of-DataObjects shape, used `:StructuredDataReference` for the JSON sub-sections, and modelled the controlled vocabularies (Fiber Material, Weave, Precursor, Additive) as separate `:Container`-style boxes. The decoded label table is in [`btkvs-docket-showcase-2026-05-29.md §3`](../agent-findings/btkvs-docket-showcase-2026-05-29.md).

Three places where Shepard's evolved primitives let us do better than the sketch:

1. **Process steps are drawn as parallel children of the root docket DO.** This is a tree shape; the actual fabrication order is sequential (Polymerisation → Tempering → Pyrolysis → Siliconization). Shepard models temporal order as a typed `Predecessor` edge between DataObjects (PROV1k, shipped — see `de.dlr.shepard.v2.dataobject.io.TypedPredecessorIO`). Linearising the chain unlocks the f(ai)²r provenance walk, the lineage-graph UI rendering (`CollectionLineageGraph.vue`), the permission-walk-inherit pattern (`aidocs/platform/24`), and the Trace3D view recipe (`project_trace3d_view`).

2. **`post_analysis` lives inside the step DO as one of its JSON sections.** The v3 docket shape from `example.json` (see `btkvs-docket-showcase-2026-05-29.md §2`) has `post_analysis` as a sub-shape of every step; in the drawio it is a section inside the step's `:StructuredDataReference`. But NDT methods produce **files**: CT volume reconstructions, X-ray PNGs, thickness micrometer CSVs, Archimedes balance readouts. JSON-only post_analysis means those files have nowhere to attach. Promoting `post_analysis` to its own child DO of the step DO lets the JSON travel as a `:StructuredDataReference` AND the CT/Röntgen/balance files travel as `:FileReference` peers on the same node.

3. **Editor stamps `{name, date}` are encoded as JSON tuples on every step and sub-step.** That's provenance buried in free text. Shepard's f(ai)²r principle (`project_fair2r_integration`) has the canonical PROV-O shape: one `:Activity` row per editor action, with `WAS_ASSOCIATED_WITH → :User`, `GENERATED → step or post-analysis DO`, `USED → input materials/sample`. Routing editor stamps to `:Activity` gives the BT-KVS docket a queryable EU AI Act Art. 50-quality audit trail at zero operator cost.

The three refinements are **independent** — each can land separately if Nils prefers a staged migration.

---

## 2. The three refinements

### 2.1 Linear `:Predecessor` chain on process steps (not tree)

**Original shape (drawio):** root docket DO has four child DOs (Polymerisation, Tempering, Pyrolysis, Siliconization) hanging off it as siblings under a `PARENT_OF` or `ChildOf` edge.

**Improved shape:** root docket DO is a **campaign anchor** carrying only the `general` + `structure` sections; the process chain is a linked list of step DOs where each carries a `:Predecessor` edge to the previous step.

Cypher (sketch — illustrative, not a runnable migration):

```cypher
// Root docket = campaign anchor
MERGE (docket:DataObject {appId: $rootAppId, name: "Docket I123"})
  SET docket.kind = "btkvs:docket-root"

// First step has no predecessor — it IS the head
MERGE (poly:DataObject {appId: $polyAppId, name: "Polymerisation"})
  SET poly.kind = "btkvs:process-step",
      poly.stepIndex = 1
MERGE (poly)-[:CHILD_OF]->(docket)

// Each subsequent step links to the previous via a typed Predecessor
MERGE (temp:DataObject {appId: $tempAppId, name: "Tempering"})
  SET temp.kind = "btkvs:process-step",
      temp.stepIndex = 2
MERGE (temp)-[:CHILD_OF]->(docket)
MERGE (temp)-[:Predecessor {relationshipType: "prov:wasInformedBy"}]->(poly)

MERGE (pyro:DataObject {appId: $pyroAppId, name: "Pyrolysis (pass 1)"})
  SET pyro.kind = "btkvs:process-step",
      pyro.stepIndex = 3
MERGE (pyro)-[:CHILD_OF]->(docket)
MERGE (pyro)-[:Predecessor {relationshipType: "prov:wasInformedBy"}]->(temp)

MERGE (sili:DataObject {appId: $siliAppId, name: "Siliconization"})
  SET sili.kind = "btkvs:process-step",
      sili.stepIndex = 4
MERGE (sili)-[:CHILD_OF]->(docket)
MERGE (sili)-[:Predecessor {relationshipType: "prov:wasInformedBy"}]->(pyro)
```

Notes:

- The `CHILD_OF` edge to the docket is kept for **navigation** (UI list of "all steps of this docket"); the `:Predecessor` edge carries the **temporal** semantics.
- The `relationshipType` literal `"prov:wasInformedBy"` is exactly the default that `TypedPredecessorIO` (PROV1k, shipped) accepts. No new enum value needed.
- The two pyrolysis passes that the v3 docket shape allows (`pyrolysis` may repeat) become two distinct step DOs (`Pyrolysis (pass 1)`, `Pyrolysis (pass 2)`) linked through the same `:Predecessor` chain. The campaign anchor counts step DOs, not step JSON entries.
- `:Predecessor` relationships of relationshipType `prov:wasInformedBy` are queryable via the typed-predecessor REST surface (`/v2/dataobjects/{appId}/typedPredecessors`) and rendered by the lineage-graph component today.

**What this unlocks:**

| Capability | How it follows from the linear chain |
|---|---|
| f(ai)²r provenance walk | A Cypher `MATCH (a)-[:Predecessor*1..]->(b)` from any step walks the entire fabrication history. |
| Lineage-graph UI | `CollectionLineageGraph.vue` already renders typed predecessor edges. |
| Permission walk-inherit | `aidocs/platform/24` permission-walk follows `:Predecessor` so an auditor with read on the campaign-final step automatically sees the whole chain. |
| Trace3D view recipe | When AFP-style spatial reasoning (`project_trace3d_view`) is the next step, the temporal chain is the time axis. |
| Predecessor-as-rework | A repair iteration (e.g. a re-pyrolysis after a damaged first pass) can carry `relationshipType: "fair2r:repairs"` on the same edge primitive — same shape, richer semantics. No second migration needed. |

### 2.2 Per-`post_analysis` DataObject (not nested `:StructuredDataReference` section)

**Original shape:** every step DO has a `:StructuredDataReference` carrying the full JSON, of which `post_analysis` is one nested object containing `ndt[]`, `sampling`, `density_porosity`, `strength_analysis`, `part_measurement`, `damage`.

**Improved shape:** each step DO has a child DO of kind `btkvs:post-analysis`. The JSON sub-fields land as a `:StructuredDataReference` on that DO, AND each NDT measurement gets a `:FileReference` peer for the actual artefact (CT volume, X-ray PNG, micrometer CSV).

Cypher (sketch):

```cypher
MERGE (pa:DataObject {appId: $paAppId, name: "Post-analysis: Pyrolysis pass 1"})
  SET pa.kind = "btkvs:post-analysis"
MERGE (pa)-[:CHILD_OF]->(pyro)

// The JSON sub-shape rides as a StructuredDataReference
MERGE (sd:StructuredDataReference {appId: $sdAppId})
  SET sd.payload = $postAnalysisJson    // sampling, density_porosity, strength_analysis, part_measurement, damage
MERGE (pa)-[:HAS_REFERENCE]->(sd)

// Each NDT method that produced a file gets its own FileReference
UNWIND $ndtMethods AS m
  MERGE (fr:FileReference {appId: m.appId, name: m.method})
    SET fr.method = m.method,             // "CT" | "Röntgen" | "Mikrometer" | ...
        fr.contentType = m.contentType,   // "application/dicom" | "image/png" | "text/csv"
        fr.oid = m.oid                    // GridFS/Garage object id
  MERGE (pa)-[:HAS_REFERENCE]->(fr)
```

Notes:

- The JSON `ndt[]` array doesn't disappear — its summarising metadata (method name, description, editor stamp) still rides in the `:StructuredDataReference`. What the new shape adds is a peer `:FileReference` per actual artefact. The JSON `ndt[N]` entry carries the `fileReferenceAppId` of its companion file.
- This is the same pattern microsections already uses (file payloads beside structured metadata, all on one DO) — see `project_imagebundle_design` for the bundle analog.
- For dockets where post_analysis is "JSON only, no files" (common at early process steps), the post-analysis DO carries only the `:StructuredDataReference` — no breaking change.
- An optional refinement (open question §5.2): split `ndt[]` so each NDT method gets its own grandchild DO. Discuss with Nils.

**What this unblocks:**

| Capability | How it follows |
|---|---|
| FAIR — Findable | CT volumes are first-class file artefacts in Shepard, not JSON comments. |
| FAIR — Reusable | A re-analysis 5 years later finds the raw CT scan attached to the right docket step. |
| NDT lineage | Future NDT-extraction plugins (e.g. CT porosity analyser) attach their output as **child** DOs of the post-analysis DO, preserving the analysis-of-analysis chain. |
| Plugin extensibility | A `shepard-plugin-ct-porosity-analyser` consumes the `:FileReference` and writes `:SemanticAnnotation` rows back onto the post-analysis DO. |

### 2.3 Editor stamps → `:Activity` PROV-O nodes

**Original shape:** every step's JSON carries `editor: {name: "Me", date: "2025-11-24T..."}`. Same shape on every sub-step (`sampling.editor`, `density_porosity.editor`, ...). The full docket can carry ~10 editor tuples per process step.

**Improved shape:** each editor stamp produces one `:Activity` node wired with PROV-O edges. The JSON's `editor` field can stay as a denormalised hint for backwards-compat reading, but the canonical provenance lives in the graph.

Cypher (sketch):

```cypher
// Look up the user by username (or fail-soft create a :MirroredUser)
MERGE (u:User {username: $editorName})
  ON CREATE SET u.shepardId = $newUserAppId,
                u.isMirrored = true,    // didn't authenticate through OIDC; placeholder
                u.createdAtMs = timestamp()

// One :Activity per editor stamp
CREATE (a:Activity {
  appId: $activityAppId,
  type: "btkvs:edit-step",                      // domain-specific subtype
  startedAtMs: $isoDateMillis,
  sourceMode: "human"                            // EU AI Act Art. 50 — human, not AI
})

// PROV-O edges
MERGE (a)-[:WAS_ASSOCIATED_WITH]->(u)
MERGE (a)-[:GENERATED]->(stepDo)                 // what the edit produced
// Optional: when the edit consumed a sample DataObject
MERGE (a)-[:USED]->(sampleDo)
```

For sub-step editor stamps (`sampling.editor`, `density_porosity.editor`, ...) the same shape applies — the only difference is the `:Activity.type` (`btkvs:edit-sampling`, `btkvs:edit-density-porosity`, ...) and the `GENERATED → post-analysis DO` target.

Notes:

- `:Activity` is the existing PROV-O substrate; no new entity type required. The `type` literal is a domain-specific subtype consumed by SPARQL filters (`SELECT ... WHERE { ?a a btkvs:edit-step }`).
- The `editor.name` string → `:User` lookup is the single tricky point. Three policies (Nils chooses):
  1. **Strict ORCID/OIDC mapping:** require an authenticated edit; reject docket uploads with unknown editor names. Highest provenance integrity, highest friction.
  2. **Soft mirror:** on first sight of an unknown editor name, create a `:MirroredUser` with `isMirrored=true` and `username = editorName`. Easy to retroactively merge into an authenticated `:User`. Recommended starting point.
  3. **Keep editor as string only:** don't create `:User`, store `editor.name` as `:Activity.agentName` string property. Loses cross-docket "what did Dennis edit this month?" queries.
- `sourceMode: "human"` follows the f(ai)²r convention. If a future BT-KVS workflow lets an LLM auto-fill a docket, the same shape with `sourceMode: "ai"` + `agentId: "claude-opus-4.7"` captures the EU AI Act Art. 50 disclosure for free.
- The handler that ingests dockets sets `ProvenanceCaptureFilter.PROP_SKIP_CAPTURE = true` per the CLAUDE.md handoff rule, since it's writing its own richer `:Activity` rows.

**What this unblocks:**

| Capability | How |
|---|---|
| EN 9100 §8.5 traceability | Auditor SPARQL: "who edited what, when, on this docket?" — one query. |
| EU AI Act Art. 50 disclosure | `sourceMode` distinguishes human vs AI edits at the node level. |
| Cross-docket queries | "All edits by Dennis in Q2" works across the BT-KVS instance. |
| HMAC chain | `HmacChainService.stamp()` (best-effort, see CLAUDE.md secondary-writes rule) can sign each `:Activity` for tamper-evidence. |

---

## 3. End-to-end example — one docket through all four steps

Below: docket I123, a C/SiC plate that goes Polymerisation → Tempering → Pyrolysis (pass 1) → Pyrolysis (pass 2) → Siliconization. Each step has a post-analysis DO; the second pyrolysis has a CT scan attached. Two editors touched the docket (Dennis edited Polymerisation + Tempering; Nils edited the rest).

```
                    :Collection {name: "BT-KVS dockets 2026"}
                              │
                              │ HAS_DATAOBJECT
                              ▼
                    :DataObject {kind: "btkvs:docket-root", name: "Docket I123"}
                              │
                              │ HAS_REFERENCE
                              ├──▶ :StructuredDataReference  {payload: general + structure JSON}
                              │
                              │ CHILD_OF (reverse view: PARENT_OF)
                              │
        ┌─────────────────────┼─────────────────────┬─────────────────────┐
        ▼                     ▼                     ▼                     ▼
  :DataObject           :DataObject           :DataObject           :DataObject
  Polymerisation        Tempering             Pyrolysis (1)         Pyrolysis (2)
  stepIndex=1           stepIndex=2           stepIndex=3           stepIndex=4
       ▲                     ▲                     ▲                     ▲
       │                     │                     │                     │
       │                     └────[:Predecessor    └────[:Predecessor    └────[:Predecessor
       │                          wasInformedBy]        wasInformedBy]        wasInformedBy]
       │                          ─────────────         ─────────────         ─────────────
       │                                                                       │
       │  CHILD_OF                                                              │ (chain continues)
       │  (each step also points up to docket-root)                             ▼
       │                                                                  :DataObject
       │                                                                  Siliconization
       │                                                                  stepIndex=5
       │                                                                       ▲
       │                                                                       └──[:Predecessor]──
       │
       │ CHILD_OF
       ▼
  :DataObject {kind: "btkvs:post-analysis", name: "Post-analysis: Polymerisation"}
       │
       ├──▶ :StructuredDataReference  {payload: ndt[] + sampling + density_porosity + ...}
       │
       │   (this post-analysis was JSON-only; no files yet)
       │
       └──◀ :Activity {type: "btkvs:edit-step", sourceMode: "human", startedAtMs: ...}
            │  WAS_ASSOCIATED_WITH → :User {username: "dennis"}
            │  GENERATED → Polymerisation step DO
            └  USED → sample / fiber-material DO

  (similar sub-tree for Tempering, edited by Dennis)

  (similar sub-tree for Pyrolysis (1), edited by Nils, JSON-only post-analysis)

  Pyrolysis (2) sub-tree:
       │
       ▼
  :DataObject {kind: "btkvs:post-analysis", name: "Post-analysis: Pyrolysis pass 2"}
       │
       ├──▶ :StructuredDataReference  {payload: ndt[] summary + sampling + density_porosity + ...}
       │
       ├──▶ :FileReference {method: "CT", contentType: "application/dicom", oid: ...}
       │
       ├──▶ :FileReference {method: "Mikrometer", contentType: "text/csv", oid: ...}
       │
       └──◀ :Activity {type: "btkvs:edit-step", sourceMode: "human", agentName: "nils"}
            │  GENERATED → Pyrolysis (2) DO
            │  USED → Pyrolysis (1) DO  ← the previous step is the input
            └  WAS_ASSOCIATED_WITH → :User {username: "nils"}

  (Siliconization sub-tree similar, JSON-only post-analysis, edited by Nils)
```

The full graph for one C/SiC docket is roughly **1 collection + 1 docket-root DO + 5 step DOs + 5 post-analysis DOs + 2 file refs + 5 structured refs + 5 step-edit Activities + ~10 sub-step Activities + 2 mirrored users + edges**. Maybe 35-40 nodes total. Scales linearly with step count.

The mermaid version of this same shape ships inside `116-btkvs-improved-schema.drawio` (deliverable 2).

---

## 4. Migration from the original drawio shape

If Nils already has the FastAPI server pointed at the original drawio shape (tree under root, JSON-nested post_analysis, JSON-tuple editor stamps), the migration is **three layered passes**, each independently runnable.

### Pass A — break the tree into a chain (refinement 2.1)

```cypher
// For each docket-root, find children ordered by their (original) JSON-step-index attribute
// and wire :Predecessor edges in that order.
MATCH (root:DataObject {kind: "btkvs:docket-root"})
MATCH (root)<-[:CHILD_OF]-(step:DataObject {kind: "btkvs:process-step"})
WITH root, step ORDER BY step.stepIndex ASC
WITH root, collect(step) AS steps
UNWIND range(1, size(steps)-1) AS i
WITH steps[i] AS curr, steps[i-1] AS prev
MERGE (curr)-[:Predecessor {relationshipType: "prov:wasInformedBy"}]->(prev)
```

Idempotent (MERGE), can re-run safely. CHILD_OF edges to the docket are preserved for navigation.

### Pass B — split post-analysis into child DOs (refinement 2.2)

Per the schema-changes-are-additive-and-nullable rule in CLAUDE.md, this pass **does not delete** the original JSON. It promotes a copy.

```cypher
MATCH (step:DataObject {kind: "btkvs:process-step"})-[:HAS_REFERENCE]->(sd:StructuredDataReference)
WHERE sd.payload CONTAINS '"post_analysis"'
  // Defensive: do not promote if already promoted
  AND NOT EXISTS { (step)<-[:CHILD_OF]-(:DataObject {kind: "btkvs:post-analysis"}) }
WITH step, sd, apoc.convert.fromJsonMap(sd.payload).post_analysis AS pa
WHERE pa IS NOT NULL
CREATE (paDo:DataObject {
  appId: randomUUID(),
  kind: "btkvs:post-analysis",
  name: "Post-analysis: " + step.name
})
MERGE (paDo)-[:CHILD_OF]->(step)
CREATE (paSd:StructuredDataReference {
  appId: randomUUID(),
  payload: apoc.convert.toJson(pa)
})
MERGE (paDo)-[:HAS_REFERENCE]->(paSd)
```

After this lands, future docket uploads write post-analysis as a child DO from the start; the legacy JSON-nested form continues to work because the new structure is additive. Phase 2 of the migration (later, in a separate maintenance window): strip `post_analysis` from the legacy `:StructuredDataReference.payload`.

### Pass C — promote editor stamps to `:Activity` (refinement 2.3)

```cypher
// For each step DO that carries an editor.name in its StructuredDataReference,
// create the matching :Activity + :User (or :MirroredUser if not found)
MATCH (step:DataObject {kind: "btkvs:process-step"})-[:HAS_REFERENCE]->(sd:StructuredDataReference)
WITH step, sd, apoc.convert.fromJsonMap(sd.payload) AS payload
WHERE payload.editor IS NOT NULL
  AND NOT EXISTS { (step)<-[:GENERATED]-(:Activity {type: "btkvs:edit-step"}) }
MERGE (u:User {username: payload.editor.name})
  ON CREATE SET u.isMirrored = true,
                u.shepardId = randomUUID(),
                u.createdAtMs = timestamp()
CREATE (a:Activity {
  appId: randomUUID(),
  type: "btkvs:edit-step",
  sourceMode: "human",
  startedAtMs: datetime(payload.editor.date).epochMillis
})
MERGE (a)-[:WAS_ASSOCIATED_WITH]->(u)
MERGE (a)-[:GENERATED]->(step)
```

Same shape applies to the sub-step `:Activity` rows, scanned out of the post-analysis JSON.

All three passes are **idempotent** (guarded by `NOT EXISTS` checks) and **reversible** via paired `V##_R__*.cypher` rollback files when this lands as a real migration. Per CLAUDE.md they belong under `backend/src/main/resources/neo4j/migrations/` with operator-runbook comments.

---

## 5. Open questions for Nils

These are choices Shepard intentionally does not pre-decide. Each one has a persona-board lens noted; the lens cites the two specialised agents this design consulted (see §5.7).

### 5.1 Process curves: `:TimeseriesContainer` or `:FileReference`?

The Pyrolysis and Siliconization step DOs both generate **process curves** — temperature and pressure traces during the firing. Two paths:

- **`:TimeseriesContainer` + `:TimeseriesReference`** on the step DO. Native windowed queries, server-side downsampling, multi-channel rendering in shepard's existing TS viewers. Right shape if the curves are sampled at decent rate (≥1 Hz) and ever cross-queried with anomaly-detection logic.
- **`:FileReference`** holding a CSV / PDF chart export. Simpler; matches whatever the oven control system already exports. Right shape if the curves are operator-archival, not for analytic queries.

**Manufacturing & Quality Engineer lens (CLAUDE.md Role 4):** an EN 9100 §8.5 auditor would expect to query "show me all pyrolysis firings where temperature deviated > 5K from set-point" — that argues for `:TimeseriesContainer`. But if Nils's oven exports a sealed PDF chart that is the official record, the file IS the certificate. **Likely answer: both — TS for analytic queries, file for the sealed record. Operator chooses primary.**

### 5.2 One `post_analysis` DO with multi-FileReference, or per-NDT-method DO?

Refinement 2.2 promotes `post_analysis` to its own DO. But within post_analysis, the `ndt[]` array can carry multiple methods (CT + Röntgen + ultrasonic + ...). Options:

- **One post-analysis DO with multiple `:FileReference` peers**, each tagged with `fr.method`. Simpler, fewer DOs, the JSON `ndt[N]` entry carries the cross-reference.
- **One DO per NDT method**, each with its own `:FileReference` + `:StructuredDataReference` for the method-specific results. Each NDT method gets its own `:Activity` provenance natively. Likely necessary if a CT-porosity analyser plugin will run and write back annotations onto the CT DO specifically.

**Data Ontologist lens (CLAUDE.md Role 2):** if NDT methods will host their own analysis pipelines (CT porosity, X-ray defect detection, ultrasonic delamination scoring), they want to be addressable nodes. The CHAMEO ontology models CharacterisationMethod as a first-class concept ([Del Nostro et al. 2022, CHAMEO](https://doi.org/10.1016/j.matchar.2022.111988)). **Likely answer: per-NDT-method DO when ≥2 methods OR an analysis plugin attaches to the method.**

### 5.3 `:Activity` granularity — per editor stamp, or per step?

The v3 docket carries `editor.{name, date}` on every step AND on every sub-step (sampling.editor, density_porosity.editor, ndt[N].editor, ...). At full granularity that's ~10 `:Activity` rows per step → 50 rows per docket → tens of thousands across the BT-KVS instance.

- **Per-stamp:** maximum fidelity, each sub-action is an `:Activity`. Right for EN 9100 §8.5 auditors who want "who reviewed the density measurement specifically?".
- **Per-step:** aggregate to one `:Activity` per step, treat sub-step editors as additional `WAS_ASSOCIATED_WITH → :User` edges on the same Activity. Much lighter; loses sub-step temporal ordering.

**Both lenses agree:** start per-stamp (it's free at write time, the SPARQL filter handles aggregation queries at read time). The cost is graph size, which is small relative to TS payload size.

### 5.4 Editor-name → `:User` resolution policy

Three options enumerated in §2.3:

- ORCID/OIDC strict mapping (highest provenance, highest friction).
- Soft mirror (recommended starting point — create `:MirroredUser` on first sight).
- Stay-as-string (loses cross-docket queries).

**Suggested default:** soft mirror, with a follow-up `BTKVS-USER-RESOLVE` task in `aidocs/16` to handle merge-on-claim when the mirrored user later authenticates with their real OIDC identity.

### 5.5 Pyrolysis-repeat naming

The v3 shape allows `pyrolysis` to repeat (densification often requires 2-3 passes). Under refinement 2.1 these become distinct step DOs in the chain. Naming options:

- `"Pyrolysis (pass 1)"`, `"Pyrolysis (pass 2)"`, `"Pyrolysis (pass 3)"` — human-readable, but pass-number is encoded in the name.
- `name: "Pyrolysis"`, `attributes.pass: 1` — name is canonical, pass-number is structured.
- `name: "Pyrolysis"`, `:SemanticAnnotation` with predicate `urn:btkvs:pyrolysisPass` and value `1` — most ontologically pure.

Recommend option 2 for human readability + queryability without touching the semantic-vocab system yet.

### 5.6 SHACL-driven decomposition tie-in

`BTKVS-A3` (the server-side decompose endpoint) is the operator-facing surface that creates this whole graph from one v3 JSON upload. The SHACL shape per `BTKVS-B1` carries the **decomposition rules** — which JSON paths become step DOs, which become post-analysis DOs, which become Activities. Open: does the schema in §3 of this doc match what the SHACL shape naturally produces? Verify in `BTKVS-B1` design.

### 5.7 Persona-board consulted

Two CLAUDE.md role lenses were applied to this design:

- **Role 4 — Industrial Manufacturing & Quality Engineer (EN 9100 audit lens).** Validated refinement 2.3 (editor stamps → `:Activity`) against EN 9100 §8.5 traceability requirements; flagged §5.1 (TS vs file for process curves) as the auditor's "show me the firing trace" question.
- **Role 2 — Data Ontologist (composite-fab domain lens).** Validated refinement 2.2 (per-post_analysis DO) against the CHAMEO CharacterisationMethod pattern; flagged §5.2 (per-NDT-method DO) as the right call when a plugin attaches to a specific method.

**Opposing lens (per `feedback_agents_argue_and_consult`):** the **Reluctant Senior Researcher (Role 9)** would push back hardest on refinement 2.3 — "I already write my name in the form, why do I need PROV-O?". The honest answer: at the Streamlit form layer the operator still types their name once; the `:Activity` is born server-side at upload time. Zero friction, audit trail for free.

---

## 6. Mapping to existing Shepard primitives and external standards

| New construct in this doc | Shepard primitive | Reference |
|---|---|---|
| Linear `:Predecessor` chain on step DOs | `:DataObject.predecessors[]`, typed via `TypedPredecessorIO.relationshipType` (PROV1k) | `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/TypedPredecessorIO.java`; `project_fair2r_integration`; CLAUDE.md "audit trail is a graph" rule |
| `prov:wasInformedBy` semantic | PROV-O standard predicate | [W3C PROV-O](https://www.w3.org/TR/prov-o/#wasInformedBy) — "Communication is the exchange of some unspecified entity by two activities" |
| `:Activity` per editor stamp | Existing `:Activity` entity (PROV1a) wired via `ProvenanceService.record()` + handler-side skip-capture | `aidocs/agent-findings/btkvs-docket-showcase-2026-05-29.md §3` table row "editor stamps"; `project_ai_human_collab_provenance`; CLAUDE.md "handlers that record their own Activity hand off skip-capture" rule |
| `sourceMode: "human"` on `:Activity` | `f(ai)²r` provenance triad — human / ai / collaborative | `project_fair2r_integration`; `project_ai_human_collab_provenance`; EU AI Act Art. 50 disclosure shape |
| `:User` resolution from `editor.name` (with soft-mirror) | `:User` entity + `:MirroredUser` placeholder (CLAUDE.md identity primitives) | CLAUDE.md "every persisted entity carries a single stable shepardId" rule; `project_appid_to_shepardid` deferred rename memo |
| Per-`post_analysis` DO with mixed `:StructuredDataReference` + `:FileReference` | Existing DO + reference-kind composition | `aidocs/agent-findings/btkvs-docket-showcase-2026-05-29.md §3`; microsections showcase precedent |
| NDT method as a first-class characterisation | CHAMEO (Characterisation Methodology Ontology) | [Del Nostro, Goldbeck, Toti 2022, *Materials Characterization* 191, "CHAMEO: an ontology for the harmonisation of materials characterisation methodologies"](https://doi.org/10.1016/j.matchar.2022.111988) |
| Method-and-instrument-of-measurement annotation | `m4i` (metadata4ing) `m4i:realizesMethod`, `m4i:hasParticipant` | `aidocs/semantics/94-metadata4ing-integration-design.md`; [Schembera et al., metadata4ing v1.2.0](https://nfdi4ing.pages.rwth-aachen.de/metadata4ing/metadata4ing/) |
| EN 9100 §8.5 traceability requirement justification | EN 9100:2018 §8.5.2 "Identification and traceability — the organisation shall use suitable means to identify outputs ... preserve identification and traceability throughout production and service provision" | EN 9100:2018 (BSI / DIN EN 9100); applies to aerospace, space, defence quality management systems |
| Soft-mirror `:User` pattern | `:User` mirror nodes (existing PROV1 / S2 design surfaces) | `aidocs/strategy/85-github-project-management-policies.md`; `feedback_appid_to_shepardid` |

External standards cited (per `feedback_bibliography_maintenance`, add to `docs/_data/references.bib` in the same PR if not already present):

1. **W3C PROV-O** — [https://www.w3.org/TR/prov-o/](https://www.w3.org/TR/prov-o/). Provenance edges (`wasInformedBy`, `wasAssociatedWith`, `generated`, `used`) used throughout §2.3 and §3.
2. **CHAMEO** — Del Nostro, Goldbeck, Toti (2022), *Materials Characterization* 191, [10.1016/j.matchar.2022.111988](https://doi.org/10.1016/j.matchar.2022.111988). Justifies per-NDT-method DO promotion (§2.2 + §5.2).
3. **metadata4ing (m4i)** — NFDI4Ing v1.2.0, [https://nfdi4ing.pages.rwth-aachen.de/metadata4ing/](https://nfdi4ing.pages.rwth-aachen.de/metadata4ing/). Per-method, per-instrument annotation predicates used in §5.2.
4. **EN 9100:2018 §8.5** — aerospace QMS identification and traceability requirement. Justifies refinement 2.3 (editor stamps → `:Activity`) at the institutional level.
5. **EU AI Act Art. 50** — machine-readable disclosure of AI vs human authorship. Justifies the `sourceMode` field on `:Activity` (per `project_fair2r_integration`).

---

## 7. What this doc does NOT decide

Per BTKVS-A4's "design doc, not migration" framing:

- **No Cypher migration files** ship in this PR. The §4 sketches are illustrative; a real migration lands when `BTKVS-A3` (server-side decompose endpoint) or `BTKVS-C1` (plugin) chooses one of them.
- **No frontend changes**. The Streamlit form-shape change waits on the SHACL-shape-as-form decision (`aidocs/integrations/116` was originally allocated to BTKVS-B1; that doc is the form-driven design separate from this one).
- **No backlog rows are closed** other than flipping `BTKVS-A4` to `design done`. `BTKVS-A1` (showcase seed), `BTKVS-A2` (semantic-vocab seed), `BTKVS-A3` (decompose endpoint), `BTKVS-B1` (SHACL shapes), `BTKVS-C1/C2` (plugin) all stay queued and consume this doc as input.

---

## 8. Acceptance criteria

This design doc lands successfully when:

1. Nils reads §2 and §5 and signals one of {accept-as-is / accept-with-amendments / propose-alternative} for each of the three refinements.
2. The five open questions in §5 each have a recorded answer (operator or persona-board).
3. `BTKVS-A3` (decompose endpoint) design references this doc as the target graph shape.
4. `BTKVS-A1` (showcase seed) writes the linear `:Predecessor` chain version, not the tree version.
5. If Nils prefers a staged migration, the three refinements are accepted one at a time in `aidocs/16` follow-up rows.
