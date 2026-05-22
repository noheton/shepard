---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Data & Process Ontologist — Debate Output

**Role:** Data & Process Ontologist  
**Phase:** 2 — Multi-agent debate, ontology/data-model lens  
**Date:** 2026-05-21  
**Source proposals:** 8 agent files, 96+ individual proposals  
**Ground-truth verified against:** `SemanticAnnotation.java`, `AbstractDataObject.java`, `DataObject.java`, `AnnotatableTimeseries.java`, `ontologies-manifest.json`, `shepard-experiment.ttl`, `V55__NOOP_ImportPlan_additive.cypher`

---

## Top 5 I'm championing

### CHAMPION 1 — QuantifiedAnnotation (DO P1)

**Verdict: CHAMPION — implement in Sprint 1**

The current `SemanticAnnotation` entity carries only four string fields: `propertyName`, `propertyIRI`, `valueName`, `valueIRI`. There is zero numeric capability. When a researcher annotates a DataObject with "tensile strength = 850 MPa", the value 850 cannot be stored in a machine-readable way today — it either goes in `valueName` as a freetext string (destroying computability) or in `attributes` (destroying semantic linkage to QUDT units).

The fix is structurally clean and additive: add optional `numericValue` (Double) and `unitIRI` (String) to `SemanticAnnotation`. Both fields are `null` for existing vocabulary-only annotations, so no existing data is touched. The UI renders a numeric input + QUDT unit picker when `propertyIRI` resolves to a QUDT `QuantityKind`; the API gains a `numericValue` field in the `SemanticAnnotationIO` response.

Migration: `V56__AddNumericAnnotationFields.cypher` — SET `numericValue = null`, `unitIRI = null` on all existing `:SemanticAnnotation` nodes (idempotent). One migration, ~3 lines of Cypher.

Why this matters for MFFD: tensile strength, delamination depth (mm), void content (%), weld line temperature (°C), cure time (min) — all quantified measurements that researchers currently lose the unit information on. With QUDT seeded (14 ontology bundles already include `qudt:Unit`), the round-trip from annotation to unit conversion to export is complete without any additional ontology work.

Convergence: API scrutinizer, UX auditor, and research data manager all surface the missing numeric capability independently. Six agents agree something is wrong here; DO P1 is the precise structural fix.

---

### CHAMPION 2 — FAIR Fields Bundle (RDM P1–P4 + DO P7 integrated)

**Verdict: CHAMPION — HMC deadline 06 July 2026 is the forcing function**

Six agents converged on the same structural gap: `AbstractDataObject` has no `license` field, no `orcidOfCreator`, no `accessRights`, no `embargoEndDate`, no `fundingReferences`. The HMC (Helmholtz Metadata Catalogue) deadline of 06 July 2026 makes the license + ORCID fields the immediate critical path — without them, no Shepard dataset is HMC-eligible.

The five fields to add:

| Field | Type | Default | Notes |
|---|---|---|---|
| `license` | String (SPDX ID) | `null` | Required for HMC ingest |
| `createdByOrcid` | String | `null` | Stamped at DO creation from authenticated user profile |
| `accessRights` | Enum: `OPEN / RESTRICTED / CLOSED / EMBARGOED` | `RESTRICTED` | Drives Unhide publish eligibility |
| `embargoEndDate` | LocalDate | `null` | Non-null only when `accessRights = EMBARGOED` |
| `fundingReferences` | List<String> | `[]` | Funder DOIs (Clean Aviation JU: 10.3030/…) |

Migration: `V57__AddFairFieldsToDataObject.cypher` — SET all five fields to null/default on existing nodes. Additive. The default `RESTRICTED` for `accessRights` is safe-by-default (nothing becomes unexpectedly public).

**Critical consumer cascade (migration risk, see §4):** These fields are not just Neo4j schema. They flow through `DataObjectIO`, `DataObjectV2IO`, `ImportManifestIO`, `UnhidePublishPlugin`, and the Unhide P9 export endpoint. The RDM agent flags this as S effort; I flag it as **M effort minimum** because of the cascade. But HMC deadline means we absorb that cost on schedule regardless.

**ORCID stamp at creation time:** When a user creates a DataObject, `createdByOrcid` is populated from their OIDC token's ORCID claim if present. This is the `UserinfoService` hook — already modified in the current git diff (`backend/src/main/java/de/dlr/shepard/auth/security/UserinfoService.java` is listed as modified). Verify ORCID claim is extracted there and thread it to `DataObjectService.create()`.

---

### CHAMPION 3 — CHAMEO + SSN/SOSA as Alignment, Not Addition (DO P3 + MfgQ P10 merged)

**Verdict: CHAMPION with critical reframing — the shex: terms already exist**

Three agents (DO P3, MfgQ P10, Strategy P4) claim CHAMEO fills a missing defect vocabulary gap. **This framing is wrong.** Ground-truth check of `shepard-experiment.ttl` confirms:

- `shex:DefectType` → Delamination + 7 other defect types
- `shex:InspectionMethod` → UltrasonicTesting, XRayInspection, VisualInspection + others
- `shex:ManufacturingProcess` → AFP, Welding + variants
- `shex:SensorRole` → primary/reference/redundant/calibration roles

The gap is not a missing vocabulary. The gap is that `shex:` terms are a Shepard-internal coinage with no external alignment. A researcher at another institute cannot resolve `shex:Delamination` to anything. A CHAMEO cross-alignment bridges that:

```turtle
shex:Delamination skos:exactMatch chameo:Delamination .
shex:UltrasonicTesting skos:exactMatch chameo:UltrasonicWave .
shex:AFP skos:exactMatch chameo:ProcessCategory .  # or closest CHAMEO term
```

Similarly, SSN/SOSA provides formal `sosa:Sensor`, `sosa:observes`, `sosa:FeatureOfInterest` — no equivalent exists in the 14 seeded bundles. Adding SSN/SOSA enables sensor-to-channel provenance that is otherwise unmodelable: "channel `pressure_chamber_1` was observed by `sosa:Sensor` with `sosa:madeObservation` triples."

Implementation: Two new entries in `ontologies-manifest.json` (chameo.ttl + ssn.ttl), SHA-256 validated, imported via n10s. A companion `V58__AlignShexToChameoAndSSN.cypher` adds `skos:exactMatch` triples using n10s SPARQL update. No existing nodes touched.

**The correct pitch:** CHAMEO + SSN/SOSA is an interoperability layer on top of working vocabulary, not a replacement for it. Researchers at external institutes can now cite CHAMEO terms when they annotate; Shepard resolves them to the local `shex:` terms.

---

### CHAMPION 4 — MaterialBatch as DataObject (DO P2 + MfgQ P3 merged)

**Verdict: CHAMPION — MFFD AFP dataset arrives ~2026-05-26, must be pre-landed**

MFFD material traceability requires linking a process step (AFP layup DataObject) to the CF/LMPAEK material batch used. The correct shape is: create a DataObject of type "MaterialBatch" with attributes `lot_id`, `material_grade`, `supplier`, `certificate_id`; link it as predecessor to the process step DataObject via `HAS_PREDECESSOR`. No new entity type needed.

This works because:
1. The Predecessor/Successor chain already models "this step used this input"
2. A `lot_id` attribute enables `MATCH (n:DataObject) WHERE n.attributes.lot_id = 'LOT-2026-004'` Cypher lookup
3. The `status = 'READY'` on the MaterialBatch DataObject signals that the certificate has been verified
4. Multiple process steps in the same campaign that use the same material batch share a single MaterialBatch DataObject as common predecessor — no duplication

The dual representation to also consider: `attributes['material_type'] = 'CF/LMPAEK'` on the process-step DataObject itself. Both coexist. The MaterialBatch DataObject is the authoritative record; the attribute on the process step is a search shortcut.

Migration: **none**. Uses existing graph schema with existing node labels. The seed script update (`examples/seed-showcase/seed.py` is already in the git diff) adds the MaterialBatch DataObjects. This is genuinely S effort.

**EN 9100 requirement this satisfies:** Material traceability from finished part back to raw material certificate — without MaterialBatch, this chain is broken.

---

### CHAMPION 5 — AnnotatableFile Bridge (DO P4 + MfgQ P6 merged)

**Verdict: CHAMPION — structural analog of AnnotatableTimeseries; L7 feature matrix queued**

`AnnotatableTimeseries` demonstrates the bridge pattern: a Neo4j entity with `containerId` + `timeseriesId` fields + `HAS_ANNOTATION` relationship to `SemanticAnnotation` nodes, decoupled from the file storage layer. The same pattern is absent for files.

An NDT scan file (ultrasonic C-scan TIFF), a process report PDF, a CAD model — none can carry semantic annotations today. A researcher cannot annotate a specific file as "this NDT scan shows delamination at location X" with a machine-readable delamination type from `shex:DefectType`.

The new `AnnotatableFile` entity:
```
(:AnnotatableFile {
  appId: String,
  fileContainerAppId: String,
  fileObjectId: String,   // GridFS OID
  fileName: String
})-[:HAS_ANNOTATION]->(:SemanticAnnotation)
```

Migration: `V59__CreateAnnotatableFileEntity.cypher` — adds the constraint `CREATE CONSTRAINT ON (n:AnnotatableFile) ASSERT n.appId IS UNIQUE`. No existing data touched (no existing AnnotatableFile nodes exist).

The API shape mirrors the AnnotatableTimeseries endpoints: `GET/POST /v2/filecontainers/{containerId}/files/{fileId}/annotations`. The frontend gets an "Annotate File" button in the file container view — currently absent.

Why this is worth championing hard: the MFFD AFP dataset will include NDT scan files. Those scans need to carry defect annotations. Without AnnotatableFile, the defect data goes in freetext attributes (no semantic resolution) or nowhere (lost). The structural work is obvious, the pattern is proven, and the data is arriving in 5 days.

---

## Top 3 I'm challenging

### CHALLENGE 1 — AI Audit Narrative Generator (AI P7)

**Verdict: CHALLENGE — LLM hallucination risk in EN 9100 regulatory context is disqualifying**

The Analytics/AI agent proposes a `TEXT` capability in `shepard-plugin-ai` that generates DIN EN 9100 audit narrative from the provenance graph. The generated text would say things like "TR-006 passed re-test, corrective action for TR-004 anomaly was completed per procedure X."

In a DIN EN 9100 audit, written records are legally significant. An LLM-generated narrative that confabulates a procedure reference, misstates a date, or fills in a gap with plausible-but-wrong text is not a time-saving tool — it is a **compliance liability**. The standard requires traceability, not plausibility. "The AI generated it" is not an accepted justification when an EASA Part 21G auditor asks why a corrective action record names a procedure that doesn't exist.

The correct shape for audit narrative is a **deterministic template renderer**: populate a Jinja2/Mustache template with provenance graph data, render a PDF/HTML document with zero LLM involvement. Every sentence is derived from a specific graph query; the graph query result is attached as evidence. This is testable, reproducible, and audit-defensible.

**Redirect:** Replace AI P7 with a `shepard-plugin-audit-renderer` that takes a DataObject subtree and a template ID and produces a deterministic audit evidence package. The LLM adds zero value here; it adds active risk.

What the LLM genuinely helps with in this domain: suggesting annotation keys when a researcher uploads a new DataObject (auto-annotation proposal, AI P2) — where the output is advisory, not regulatory.

---

### CHALLENGE 2 — CausalEdge relationship type (DO P12, own proposal)

**Verdict: CHALLENGE — premature; no PLUTO data in system; adds graph complexity without current payoff**

My own P12 proposes a `HAS_CAUSED` directed relationship type connecting a commanding DataObject (a ground command sent to PLUTO) to an effect DataObject (a telemetry anomaly recorded after the command). This would enable "which command caused which anomaly" queries.

**Challenge to self:** There is no PLUTO data in Shepard today. There is no PLUTO seed script. There is no agreed DataObject topology for PLUTO mission phases. Building a causal edge type before the data model for the use case exists is premature schema speculation.

The `HAS_SUCCESSOR` chain already encodes temporal sequence. For the LUMEN test campaign, temporal succession is sufficient: TR-004 (anomaly) → TR-005 (hold/repair) → TR-006 (re-test) is a causal chain implicitly. Making it explicit with a new relationship type is a premature optimization.

**Defer:** When PLUTO data arrives and the PLUTO DataObject topology is seeded, revisit whether `HAS_CAUSED` is needed or whether `HAS_SUCCESSOR` with an `attributes['transition_type'] = 'causal'` attribute satisfies the use case at zero migration cost.

---

### CHALLENGE 3 — Conference-Mode Story Layer (EP-04)

**Verdict: CHALLENGE — entity sprawl for what is fundamentally metadata**

The Ecosystem Advocate proposes a `ShepardStory` entity as a `StructuredDataReference` that sequences DataObjects into a narrative arc for conference demonstrations. This is a new Neo4j node type, a new relationship, and a new API surface to maintain.

**Challenge:** A "story" is an ordered list of DataObject appIds with prose annotations. This is metadata, not a first-class entity. The same capability is achievable today with a Collection that uses a `story_order` attribute on each DataObject + `description` fields as the narrative. Adding a `ShepardStory` entity type is entity sprawl.

The real requirement is: "demo mode where the UI walks through a curated sequence of DataObjects with contextual explanation." This is a **presentation layer feature**, not a data model feature. The correct implementation is a configuration file (JSON/YAML, stored as a file payload) that drives a dedicated `/demo` route in the frontend. No new Neo4j entities needed.

**Redirect:** EP-04 should be implemented as (1) a `demo-config.json` schema spec (ordered DataObject appIds + narration text + highlight annotations) and (2) a `/demo/{collectionId}` frontend route that reads the config and drives a step-through view. The data model stays clean.

---

## Merges I'm calling

### MERGE A — CHAMEO + SSN/SOSA (DO P3 + MfgQ P10 + Strategy P4 → single deliverable)

All three agents propose ontology additions but frame them differently and with different scope. Merge into a single `ontologies-manifest.json` PR that adds CHAMEO and SSN/SOSA together with the `skos:exactMatch` alignment migration. Owner: ontology steward. One PR, one migration, one review cycle.

The merged deliverable also handles DO's OM-2/QUDT deduplication concern by adding a machine-readable preference rule to the manifest: `qudt:Unit` is primary; `om-2:Measure` is retained for backward compat but deprecated in new annotations.

### MERGE B — MaterialBatch (DO P2 + MfgQ P3 → single seed + docs)

Both proposals describe the same pattern. Merge into a single seed update (already in `examples/seed-showcase/seed.py` diff) + a documentation addition to `docs/help/` explaining the MaterialBatch DataObject convention. No backend changes needed.

### MERGE C — Ancestor-Chain Traversal Endpoint (UX P5 + API P8 + MfgQ P5 + Strategy P3 + RDM P11 + AI P6 → single `/v2/dataobjects/{appId}/lineage` endpoint)

Six agents independently ask for "give me the full predecessor chain in one call." This is the most-converged proposal in the entire multi-agent process. The endpoint should return:
- `ancestors[]`: ordered list (oldest first) of DataObject summaries, traversed via `HAS_SUCCESSOR` INCOMING
- `descendants[]`: ordered list (newest first) via `HAS_SUCCESSOR` OUTGOING
- `depth`: integer (max traversal depth, default 10, capped at 50)
- `includeChildren`: boolean (include `HAS_CHILD` subtrees at each node)

The UX agent's note about `DataObjectProvGraph.vue` line 86 `slice(0,6)` hard cap must be removed simultaneously — the frontend bug that drops ancestors silently is as urgent as the missing API endpoint.

Owner: single backend + frontend PR. Complexity: M. Value: CRITICAL (EN 9100 audit trail completeness depends on it).

### MERGE D — AnnotatableFile (DO P4 + MfgQ P6 → single entity + API PR)

Same structural pattern, same migration needed. Merge into one PR. The bridge entity is simple; the migration is one constraint creation; the API is 4 endpoints mirroring AnnotatableTimeseries.

### MERGE E — FAIR Field Bundle (RDM P1–P4 + DO P7 + Strategy P1 + AI P10 + EP-02 + UX P4 → single V57 migration + IO update PR)

Seven agents contribute pieces of the same structural change. One migration (`V57__AddFairFieldsToDataObject.cypher`), one `AbstractDataObject.java` change, one `DataObjectIO` update, one `DataObjectV2IO` update. All seven agents' requirements satisfied in one PR. The HMC deadline enforces that this PR ships before 06 July 2026.

### MERGE F — Equipment/Calibration (CHALLENGE DO P10, CHAMPION MfgQ P4's AAS approach)

DO P10 proposes a standalone `shepard-plugin-calibration` with new Equipment + CalibrationRecord Neo4j entities and a `USED_CALIBRATED_EQUIPMENT` time-indexed edge. MfgQ P4 proposes extending the existing `shepard-plugin-aas` with an IDTA Handover Documentation submodel template.

**Call: MfgQ P4 approach wins.** Reasons:
1. Plugin-first principle: extend shipped infrastructure over creating new plugins
2. IDTA Asset Administration Shell submodel templates natively carry CHAMEO semantic IDs and QUDT unit references per IDTA spec — the interoperability is built in
3. `shepard-plugin-calibration` as a separate plugin adds a new dependency surface for operators; operators already deploying `shepard-plugin-aas` absorb zero additional friction
4. The `USED_CALIBRATED_EQUIPMENT` edge from DO P10 can still land — but on the existing AAS plugin's `AASSubmodel` node, not on a new entity type

Retain DO P10's time-indexed edge proposal as a graph schema addition inside MfgQ P4's PR scope.

---

## Migration risk underestimated

The following proposals claim S or XS effort but contain hidden Neo4j migration or consumer cascade work that elevates true cost.

| Proposal | Claimed effort | Actual effort | Hidden cost |
|---|---|---|---|
| RDM P1–P4 (FAIR fields) | S each | M total | `AbstractDataObject` change cascades to `DataObjectIO`, `DataObjectV2IO`, `ImportManifestIO`, `UnhidePublishPlugin`, and the collection export endpoint. Each consumer needs null-safe reads + new field serialization. Budget 2–3 days for cascade + integration tests. |
| Strategy P5 / API P9 (TS-IDa+IDb) | M | L | `selectedChannels` on `:TimeseriesChart` Neo4j nodes stores channel identity as pipe-separated 5-tuples (`measurement|device|location|symbolicName|field`). When channel appIds land, every stored `selectedChannels` value must be re-mapped. This is a **data migration over live TimescaleDB + Neo4j**. The migration script must JOIN 5-tuple parts against the new channel appId table. No rollback is cheap. Treat as L and write a dry-run mode first. |
| RDM P7 (Tombstone / Publication retirement) | M | L | Adds `retiredAt`, `retiredReason`, `successorPid` to `:Publication` nodes. But Publication is currently a VersionableEntity subclass — check whether OGM's `@NodeEntity` inheritance chain serializes these fields cleanly or requires a discriminator migration. Additionally, the Unhide export flow must handle tombstoned publications (suppress from harvest or return 410). Two additional consumers beyond the entity itself. |
| DO P5 (Status vocabulary enforcement) | S | M | Current `status` field on `AbstractDataObject` is a freetext String with no enum constraint. Existing data includes `null`, `"DRAFT"`, `"IN_REVIEW"`, `"READY"`, `"PUBLISHED"`, `"ARCHIVED"`, and potentially typos (LUMEN seed TR-005 has `null` status — verify). Adding `FAILED`, `NCR_OPEN`, `REJECTED` to the vocabulary is additive. But: (1) any code that does `status.equals("X")` breaks if null; (2) the stale-value sweep for existing `null` statuses needs a migration that sets a default (`"DRAFT"` is safe). `V60__DefaultNullStatusToDraft.cypher`: `MATCH (n) WHERE n.status IS NULL AND (n:DataObject OR n:Collection) SET n.status = 'DRAFT'`. This is a data-mutating migration — include rollback file. |
| UX P9 / AI P9 (POST /v2/import/jobs) | S | M | The `validate` endpoint exists and produces a `commitId`. The `execute` endpoint does not exist. Creating it requires (1) a new `ImportJob` Neo4j entity to track async status, (2) a job queue or async executor, (3) status polling endpoint. The import system is not complete without execute — and the migration to store ImportJob state is a new node type. Not S. |
| EP-05 (sTC schema-aware annotation) | XS | S | The proposal sounds like config-only, but making hotfolder routing rules carry provenance stamps means the `HotfolderRoutingRule` entity needs a `provenanceTemplate` field and a migration to add it. Minor but non-zero. |

**Summary principle:** Any proposal touching `AbstractDataObject` or its subclasses costs +1 complexity tier due to the OGM inheritance cascade. Any proposal touching `selectedChannels` or timeseries channel identity is automatically L. Any proposal that adds a new relationship type is S minimum (constraint creation + index + update to graph query in affected services).

---

## My overall priority stack

Sequencing is driven by three constraints: (1) **HMC deadline 06 July 2026** — approximately 6 weeks; (2) **MFFD AFP dataset arrival ~2026-05-26** — approximately 5 days; (3) **dependency ordering** — some proposals are blockers for others.

### Sprint 0 (this week, before AFP data arrives)

| Priority | Proposal | Why now |
|---|---|---|
| P0.1 | MaterialBatch DataObject pattern (MERGE B) | AFP data arrives in 5 days; seed script update is already in diff |
| P0.2 | Status vocabulary enforcement + null sweep (V60) | TR-005 null status is a data quality bug; fix before AFP data adds more nulls |
| P0.3 | AnnotatableFile bridge entity (MERGE D, backend only) | NDT scan files arrive with AFP data; annotation capability must be pre-landed |

### Sprint 1 (weeks 1–2)

| Priority | Proposal | Why now |
|---|---|---|
| P1.1 | QuantifiedAnnotation (CHAMPION 1, V56) | Unlocks numeric measurement annotations for AFP process data |
| P1.2 | FAIR Fields Bundle (CHAMPION 2, MERGE E, V57) | HMC deadline is 6 weeks; consumer cascade takes time; start now |
| P1.3 | Ancestor-chain traversal endpoint (MERGE C) | EN 9100 audit trail; DataObjectProvGraph slice(0,6) bug fix simultaneously |

### Sprint 2 (weeks 3–4)

| Priority | Proposal | Why this order |
|---|---|---|
| P2.1 | CHAMEO + SSN/SOSA alignment (CHAMPION 3, MERGE A, V58) | Requires P1.1 (QuantifiedAnnotation) to have landed first so QUDT cross-links work |
| P2.2 | referenceIds rename (API P1, CRITICAL/S) | Blocked nothing; fixes MCP tool 404s; S effort, do it |
| P2.3 | POST /v2/import/jobs execute endpoint (API P3 / AI P9, MERGE) | Required for agentic ingest pipeline; build ImportJob entity + async executor |

### Sprint 3 (weeks 5–6, targeting HMC deadline)

| Priority | Proposal | Why |
|---|---|---|
| P3.1 | Unhide publish eligibility driven by accessRights (RDM P9) | Depends on FAIR fields (P1.2) being live |
| P3.2 | TS-IDa channel appId migration (Strategy P5, DRY-RUN first) | L effort; start dry-run now; selectedChannels migration last |
| P3.3 | Equipment/Calibration via AAS submodel extension (MERGE F) | Depends on CHAMEO alignment (P2.1) for semantic IDs |

### Later (post-HMC, no deadline pressure)

| Proposal | Sequencing note |
|---|---|
| ProblemJson global ExceptionMapper (API P7) | S effort, QoL; any sprint slot |
| Status workflow gate (MfgQ P2 blocking gate) | Requires status vocabulary (P0.2) as prerequisite |
| Tombstone / Publication retirement (RDM P7) | Depends on FAIR fields live (P1.2) |
| AI auto-annotation from file content (AI P2) | Depends on AnnotatableFile (P0.3) + plugin-ai skeleton |
| CausalEdge (DO P12) | Deferred until PLUTO data arrives; revisit then |
| Conference-Mode demo route (EP-04 redirect) | Low structural cost once lineage endpoint ships (P1.3) |
| LUMEN seed FAIR upgrade (RDM P12) | Can land any time after FAIR fields (P1.2) |

---

## Meta-observation: what the convergence tells us

Six or more agents agreed on four proposals: (1) ancestor-chain endpoint, (2) FAIR fields, (3) referenceIds rename, (4) QuantifiedAnnotation. When 6 of 8 independently-prompted agents converge on the same gap, treat it as settled — build it. The debate is over.

Three agents independently discovered CHAMEO as "missing" but all three were working from the wrong premise (empty defect vocabulary). The `shepard-experiment.ttl` ground truth changes the framing from "add CHAMEO vocabulary" to "align existing vocabulary to CHAMEO." This is a cheaper and more correct action — and it would have been missed without codebase verification. **Domain-lens debate is most valuable precisely in cases like this, where the ontology is doing work that proposal authors couldn't see from their own vantage point.**

The AI Audit Narrative challenge (CHALLENGE 1) is the strongest single cross-agent disagreement from this review. The Analytics/AI agent is optimistic about LLM regulatory narrative generation; the ontology lens sees a compliance liability. The resolution is deterministic templating — not rejection of AI tooling generally, just rejection of LLM generation in the specific EN 9100 regulatory context. This nuance matters because the same `shepard-plugin-ai` can carry both the dangerous (audit narrative) and the safe (annotation suggestion) capabilities — the question is which capabilities are exposed to which workflows.
