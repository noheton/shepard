# Data Ontologist — Feature Proposals
**Author:** Principal Aerospace Domain Engineer and Data Ontologist
**Date:** 2026-05-21
**Inputs:** own discovery findings + 6 peer findings + plugins/ survey + aidocs/44

---

## Proposal 1: Numeric Semantic Annotations (QuantifiedAnnotation)

**Name:** QuantifiedAnnotation

**Problem it solves:**
`SemanticAnnotation` carries only a property IRI and a value IRI — no numeric value, no unit. Physical quantities with units (`target_thrust_kN = 25`, `chamber_pressure_bar = 180`, calibration uncertainty `±0.5%`) are stored as freetext strings in the `attributes` map. Range queries ("find all test runs with target thrust > 20 kN") require string-parsing on the client, and QUDT/OM-2 unit annotations — though both are seeded — are structurally impossible to use for numeric data.
The API scrutinizer independently confirmed that `DataObjectIO` leaks implementation internals; the manufacturing quality agent identified the lack of machine-readable physical quantities as a CRITICAL gap for EN 9100 traceability; the FAIR/RDM agent flagged the attributes-vs-annotations bridge as the platform's FAIR Achilles heel (R: 0.9/3).

**What it looks like:**
Extend `SemanticAnnotation.java` with two additive optional fields:

```java
private Double numericValue;   // null → existing IRI-pair-only behaviour
private String unitIRI;        // QUDT or OM-2 IRI, e.g. "http://qudt.org/vocab/unit/KiloN"
```

A QuantifiedAnnotation is valid when either `valueIRI` is set (existing IRI-pair mode) or both `numericValue` + `unitIRI` are set. A `numericValue` without a `unitIRI` is rejected (400).

Neo4j migration (`V##__AddNumericAnnotationFields.cypher`): additive `SET n.numericValue = null, n.unitIRI = null` where missing — idempotent, safe on existing data.

New Cypher query pattern enabled:
```cypher
MATCH (a:SemanticAnnotation {propertyIRI: "https://w3id.org/metadata4ing#hasTargetValue"})
WHERE a.numericValue > 20.0 AND a.unitIRI = "http://qudt.org/vocab/unit/KiloN"
RETURN a
```

Frontend: `AddAnnotationDialog.vue` conditionally renders a numeric input + QUDT unit browser when the selected property is typed as `owl:DatatypeProperty` (detectable from n10s range axioms). Non-breaking: existing IRI-pair annotations render exactly as before.

**Plugin or core?** Core — SemanticAnnotation is a core entity used across all payload kinds and all domains.

**Effort:** M (1 backend sprint for model + migration + validation + tests; 1 frontend sprint for conditional numeric form + unit browser).

**Domain impact:** MFFD manufacturing (primary — EN 9100 physical quantity traceability), PLUTO satellite (secondary — telemetry threshold annotation), general researcher (FAIR interoperability I2 gap close).

**Cross-finding hook:** Unlocks the FAIR RDM agent's I2 gap (controlled-vocab enforcement); gives the UX agent's "annotation suggestion from channel name" idea a numeric variant; gives the AI agent's provenance gap detection a numeric threshold to check against; makes the API scrutinizer's `DataObjectIO` shape less leaky by replacing freetext attributes with typed annotations.

---

## Proposal 2: Material Batch as First-Class Graph Node

**Name:** MaterialBatch DataObject Pattern + Lot Lineage API

**Problem it solves:**
Propellant lot IDs (`lox_lot_id: "LOX-2024-01"`), fiber batch IDs (`fiber_lot_id: "CF-LMPAEK-2024-07-B3"`), and welding wire spools are stored inside JSON blobs in StructuredData containers. A Cypher query "which process runs consumed LOX batch LOX-2024-01?" requires a full-text scan of JSON payloads — not a graph traversal.
The manufacturing quality agent rated this CRITICAL for EN 9100 §8.5.2 (identification and traceability to material). My own findings identified this as the single highest-leverage semantic gap for DIN EN 9100 audit readiness. No code change is required — this is a usage pattern + a dedicated API endpoint.

**What it looks like:**

**Data model (usage pattern):**
- Each material batch (propellant lot, fiber roll, welding wire spool) becomes a dedicated DataObject with:
  - `attributes["lot_id"]`, `attributes["supplier"]`, `attributes["conformance_cert_id"]`, `attributes["material_grade"]`
  - StructuredData container: certificate of conformance (JSON or PDF reference)
  - Semantic annotation: `m4i:usesMaterial → [SiMaT IRI for material type]` (SiMaT is seeded)
- Each process DataObject that consumes the batch gains a **Predecessor link** to the MaterialBatch DataObject AND a queryable `attributes["lot_id"]` for fast Cypher lookup (dual representation: attribute for indexed lookup, relationship for graph traversal).

**New API endpoint (core backend):**
```
GET /v2/collections/{appId}/lot-lineage?lot_id={id}
```
Returns all DataObjects in the collection whose `attributes["lot_id"]` matches the query value, plus their full Predecessor/Successor chain. This is the "batch recall" query: "which assemblies are affected by contaminated batch X?"

**Seed update:** Update `lumen-showcase/seed.py` to promote `lox_lot_id`/`lch4_lot_id` from JSON runlog fields to DataObject attributes and create MaterialBatch DataObjects with Predecessor links. This makes the showcase demonstrate real FAIR traceability.

**Plugin or core?** Core (uses only existing DataObject + Predecessor/Successor primitives). The API endpoint is a lightweight read-only traversal with no new entity types.

**Effort:** S (endpoint + seed update; no new entities, no migration — pure usage pattern formalization with a single new read endpoint).

**Domain impact:** MFFD manufacturing (primary — EN 9100 material traceability), LUMEN rocket testing (primary — propellant batch traceability), general researcher (secondary — material provenance for FAIR R1.2).

**Cross-finding hook:** Directly addresses the manufacturing quality agent's CRITICAL gap (EN 9100 §8.5.2) and the UX agent's "ancestor walk" idea (the lot-lineage query is the upstream traversal the compliance auditor needs). The FAIR/RDM agent's R1.2 gap (detailed provenance) is partially closed.

---

## Proposal 3: CHAMEO + SSN/SOSA Ontology Bundle

**Name:** Defect & Sensor Vocabulary Bundle (CHAMEO + SSN/SOSA)

**Problem it solves:**
CHAMEO (Characterisation Methodology Ontology) is the NFDI/W3C standard for defect classification and characterisation methods. SSN/SOSA (W3C Semantic Sensor Network Ontology) formally defines sensors, observations, and features of interest. Neither is in the ontology manifest. The MFFD use case's most critical semantic assertion — defect classification on NDT results — has no controlled vocabulary. The `AnnotatableTimeseries` bridge exists and works, but a channel cannot be described as a formal `sosa:Sensor` with a `sosa:observes` link to its physical quantity.
The manufacturing quality agent named absent CHAMEO the most conspicuous gap given the MFFD NDT focus. My own findings confirmed both absences and their impact.

**What it looks like:**
Two additive entries in `ontologies-manifest.json`:

```json
{
  "id": "chameo",
  "url": "https://raw.githubusercontent.com/emmo-repo/CHAMEO/master/chameo.ttl",
  "sha256": "<pinned>",
  "required": false,
  "description": "Characterisation Methodology Ontology — defect types, NDT methods, characterisation equipment"
},
{
  "id": "ssn-sosa",
  "url": "https://www.w3.org/ns/ssn/",
  "sha256": "<pinned>",
  "required": false,
  "description": "W3C Semantic Sensor Network / Sensor, Observation, Sample, Actuator — sensor capability and observation metadata"
}
```

`OntologySeedService` handles ingestion automatically. No migration. No backend service changes.

**Immediate effects:**
- `AddAnnotationDialog.vue` gains browsable CHAMEO terms: `chameo:LocalPorosityDefect`, `chameo:Delamination`, `chameo:FiberMisalignment`, `chameo:UltrasonicTesting` — enabling per-ply defect annotation once the `AnnotatableFile` bridge (Proposal 4) ships.
- `AnnotatableTimeseries` can annotate a channel as `sosa:Sensor` with `sosa:observes → sosa:FeatureOfInterest` — grounding a pressure channel to its physical quantity in machine-readable form.
- SSN/SOSA's `sosa:usedProcedure` enables linking a measurement channel to a calibration method IRI.

**QUDT primacy rule:** Add a `skos:scopeNote` to the manifest entry establishing QUDT as the primary unit vocabulary and OM-2 as secondary — resolving the unit conflict identified in my own findings (§4.3). One sentence, prevents future dual-annotation confusion.

**Plugin or core?** Core (ontology manifest is a core configuration file; `OntologySeedService` handles ingestion transparently).

**Effort:** S (pin SHA-256 hashes for both TTL files, add manifest entries, add `skos:scopeNote`, run regression tests on n10s ingestion).

**Domain impact:** MFFD manufacturing (primary — defect vocabulary for NDT; Nadcap characterisation method annotation), general researcher (SSN/SOSA enables sensor metadata for any measurement-intensive dataset).

**Cross-finding hook:** Directly enables the AI agent's annotation suggestion feature (Proposal C in their doc) to suggest CHAMEO defect terms from channel names. Partially closes the manufacturing quality agent's "defect classification" gap in the EN 9100 readiness table. Closes the FAIR/RDM agent's I2 (controlled vocabularies follow FAIR principles) gap for sensor metadata.

---

## Proposal 4: AnnotatableFile Bridge

**Name:** AnnotatableFile — Sub-Container File Annotation

**Problem it solves:**
The `AnnotatableTimeseries` bridge enables semantic annotation on individual Timeseries channels. No equivalent exists for individual files within a FileBundle, individual documents in StructuredData, or individual records in SpatialData. An AFP layup run produces 200 files (one per ply); a defect on ply 47 cannot be annotated with `chameo:LocalPorosityDefect` at file granularity. The annotation sits on the entire FileBundle or DataObject, losing spatial specificity.
The UX auditor called this out as a curator friction point. The manufacturing quality agent rated sub-container annotation a CRITICAL gap (EN 9100 §8.7 — control of nonconforming outputs requires locatable defect records). My own findings identified this as Gap 2 — the structural analog of the `AnnotatableTimeseries` bridge.

**What it looks like:**
New Neo4j entity `AnnotatableFile` (analogous to `AnnotatableTimeseries`):

```java
@Node("AnnotatableFile")
public class AnnotatableFile extends BasicEntity {
    private String fileContainerAppId;   // which FileBundle
    private String fileObjectId;         // MongoDB GridFS ObjectId of the specific file
    private String fileName;             // snapshot for display (not identity)
    @Relationship(type = "HAS_ANNOTATABLE_FILE")
    private FileContainer container;
}
```

`BasicEntity` inheritance gives it `HAS_ANNOTATION` for free — the same annotation mechanism as DataObjects and channels. The `fileObjectId` is the stable foreign key into MongoDB GridFS.

New REST endpoints:
```
GET  /v2/file-containers/{appId}/annotatable-files
POST /v2/file-containers/{appId}/annotatable-files        → creates bridge node for a specific file
GET  /v2/file-containers/{appId}/annotatable-files/{id}/annotations
POST /v2/file-containers/{appId}/annotatable-files/{id}/annotations
```

Frontend: in the FileBundle file list, each file row gains a small annotation badge/button (analogous to how Timeseries channels work in the measurements table). Clicking opens `AddAnnotationDialog` with the `AnnotatableFile` as the target.

Neo4j migration: no schema migration needed — new entity type, new relationship type, all additive.

**Plugin or core?** Core — this is a missing structural analog of an existing core capability (`AnnotatableTimeseries`). The missing bridge is a core entity gap, not a domain-specific plugin need.

**Effort:** M (backend: new entity + DAO + REST endpoints + service layer + migration; frontend: file row annotation badge + dialog wiring; tests: ~15 new unit tests).

**Domain impact:** MFFD manufacturing (primary — ply-level defect annotation in AFP layup FileBundle), PLUTO satellite (secondary — individual telemetry packet annotation in StructuredData), general researcher (any domain with multi-file containers needing per-file metadata).

**Cross-finding hook:** Enables CHAMEO (Proposal 3) to be used at meaningful granularity. Unblocks the AI agent's PDF auto-annotation feature at file level (the suggested annotation from a PDF can be applied to the `AnnotatableFile` for that specific file, not just the container). The UX auditor's "annotation dialog target" idea becomes richer — bulk annotation across selected files in a FileBundle becomes possible.

---

## Proposal 5: MFFD + PLUTO Domain Vocabulary Pack

**Name:** Domain Vocabulary Pack (MFFD ConceptScheme + PLUTO ConceptScheme)

**Problem it solves:**
Three vocabulary gaps block meaningful semantic annotation for the two primary use cases:
1. No MFFD manufacturing process vocabulary (`AFP_InSituConsolidation`, `ContinuousUltrasonicWelding`, `ContinuousResistanceWelding`, `StudWelding`, `FiberLayup`, `NDTInspection`).
2. No PLUTO mission phase vocabulary (`LEOP`, `Commissioning`, `NominalOps`, `SafeMode`, `DeOrbit`).
3. No facility vocabulary for LUMEN (`lumen:FacilityP3Lampoldshausen`).

These are not covered by any of the 14 seeded ontologies. The LUMEN seed's `bench: "P3-Lampoldshausen"` is a freetext string; the MFFD process type is an untyped freetext attribute; PLUTO mission phases have no IRI. The `shepard-experiment.ttl` vocabulary covers generic experiment phases but not domain-specific manufacturing steps or mission phases.
My own findings identified this as Gap 4.1/4.2 (phase vocabulary conflicts) and the annotation playbook (§3) identified mandatory annotation fields with no controlled vocabulary today.

**What it looks like:**
Two new Turtle files added to `backend/src/main/resources/ontologies/`:

**`mffd-process.ttl`** — MFFD manufacturing process vocabulary:
```turtle
mffd:ManufacturingProcess a owl:Class, skos:ConceptScheme ;
    rdfs:subClassOf prov:Activity ;
    skos:prefLabel "MFFD Manufacturing Process" .

mffd:AFP_InSituConsolidation a owl:NamedIndividual, skos:Concept ;
    skos:inScheme mffd:ManufacturingProcess ;
    skos:prefLabel "AFP In-Situ Consolidation" ;
    skos:related shex:ManufacturingProcess ;
    skos:exactMatch m4i:ProcessingStep .

mffd:FiberLayup a owl:NamedIndividual, skos:Concept ; ... 
mffd:ContinuousUltrasonicWelding a owl:NamedIndividual, skos:Concept ; ...
mffd:ContinuousResistanceWelding a owl:NamedIndividual, skos:Concept ; ...
mffd:StudWelding a owl:NamedIndividual, skos:Concept ; ...
mffd:NDTInspection a owl:NamedIndividual, skos:Concept ; ...
mffd:Certification a owl:NamedIndividual, skos:Concept ; ...

mffd:FacilityDLRAugsburg a owl:NamedIndividual, skos:Concept ;
    skos:prefLabel "DLR Augsburg ZLP" ;
    m4i:hasLocation "48.7208° N, 10.8998° E" .
```

**`pluto-mission.ttl`** — PLUTO satellite mission vocabulary:
```turtle
pluto:MissionPhase a owl:Class, skos:ConceptScheme ;
    rdfs:subClassOf prov:Activity ;
    skos:prefLabel "PLUTO Mission Phase" .

pluto:LEOP a owl:NamedIndividual, skos:Concept ;
    skos:inScheme pluto:MissionPhase ;
    skos:prefLabel "Launch and Early Operations Phase" ;
    skos:related shex:ExperimentPhase .

pluto:Commissioning a owl:NamedIndividual, skos:Concept ; ...
pluto:NominalOps a owl:NamedIndividual, skos:Concept ; ...
pluto:SafeMode a owl:NamedIndividual, skos:Concept ; ...
```

**`lumen-facility.ttl`** — add `lumen:FacilityP3Lampoldshausen`, `lumen:FacilityP4_1Lampoldshausen`, `lumen:FacilityP8Lampoldshausen` as SKOS concepts (already partially implied by the seed but never IRIfied).

Each uses the established dual-typing pattern (`a owl:Class, skos:ConceptScheme; rdfs:subClassOf prov:Activity`). Cross-links via `skos:related` to `shex:` terms follow the existing `lumen-inspired.ttl` pattern.

Update `lumen-showcase/seed.py` to use the new MFFD and lumen: IRIs for facility and process annotations instead of freetext attributes.

**Plugin or core?** Core — domain vocabulary is part of the semantic infrastructure, not a plugin-level concern. CLAUDE.md specifies ontology seeding as a startup service, not a plugin hook.

**Effort:** S (write three TTL files following the established lumen-inspired.ttl pattern; add to manifest; update seed.py; no backend code changes; regression test n10s ingestion).

**Domain impact:** MFFD manufacturing (primary — enables all MFFD process annotations), PLUTO satellite (primary — mission phase annotations), LUMEN (extends current showcase to use IRIs not freetext).

**Cross-finding hook:** The strategy advisor identified the LUMEN showcase as "a clever fiction that risks becoming a liability" if it doesn't demonstrate real semantic depth. Adding these vocabularies and updating the seed transforms freetext attributes into browsable, interoperable SKOS concepts — directly improving the showcase's credibility for external presentations.

---

## Proposal 6: Quality Status Extension + Predecessor-Status Gate

**Name:** QualityStatus — Extended Status Vocabulary with Transition Guards

**Problem it solves:**
The current five-value status vocabulary (`DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED`) is research-lifecycle oriented. It has no `NCR_OPEN`, `ON_HOLD`, `REJECTED`, or `CERTIFIED` value. More critically, status is a plain String in Neo4j with zero server-side enforcement — any Write user can set any value on any DataObject in any direction. Nothing prevents a successor DataObject from being created while a predecessor carries an open non-conformance.
The manufacturing quality agent rated both the missing status values and the absent transition guards as CRITICAL for EN 9100 §8.6 (release of products) and §8.7 (control of nonconforming outputs). The UX auditor called for a clear status advancement button for the shop floor IME.

**What it looks like:**

**Status vocabulary extension (additive enum hint, no migration):**
New values added to `AbstractDataObjectIO.status` enumeration hint:
```
NCR_OPEN       — open non-conformance, downstream processing should be blocked
ON_HOLD        — precautionary hold pending test result (not a confirmed defect)
REWORK         — active rework following disposition
REJECTED       — scrapped; record kept for traceability
CERTIFIED      — conformity declared (distinct from PUBLISHED which means "open for citation")
SUPERSEDED     — record replaced by a revision; visible in lineage but inactive
```
Because status is stored as a plain String, no Neo4j migration is needed. The new values become valid immediately.

**Predecessor-status gate (new backend logic in `DataObjectService`):**
```java
// On createSuccessorDataObject():
if (predecessor.status in {"NCR_OPEN", "ON_HOLD"} && !featureToggle("quality-gate").isEnabled()) {
    throw new PreconditionFailedException(
        "Predecessor DataObject " + predecessor.appId + " has status " + predecessor.status +
        ". Resolve the open NCR or hold before creating a successor."
    );
}
```

Toggle via `FeatureToggleRegistry` (A3b pattern already established) so operators can disable during data migration. Gate role: only `quality-engineer` role (a new role value, not a new Role entity — roles are stored as String lists) can set `status → CERTIFIED` or close `NCR_OPEN → READY`.

**Frontend (minimal):**
- Status dropdown gains the new values with color coding: `NCR_OPEN` = red chip, `ON_HOLD` = amber, `CERTIFIED` = green with lock icon.
- A `status → NCR_OPEN` transition shows a confirmation dialog ("This will block successors until resolved").

**Plugin or core?** Core — status and transition guards are core DataObject service logic, not plugin-level. The manufacturing quality agent correctly noted that `shepard-plugin-quality` is the right structural home for a full NCR lifecycle — but the status vocabulary extension and transition guard are prerequisite foundations that belong in core and are delivered before any quality plugin is built.

**Effort:** M (backend: status enum extension + predecessor gate service logic + role gate + feature toggle registration + 8–10 unit tests; frontend: dropdown colors + confirmation dialog; no migration).

**Domain impact:** MFFD manufacturing (primary — EN 9100 §8.6 quality gate), general researcher (improved status semantics for any workflow that has hold states or formal certification steps).

**Cross-finding hook:** The manufacturing quality agent's §9 "medium-term (frontend + light backend)" recommendation maps directly here. The UX auditor's "large-target status button for shop floor" becomes meaningful once the new status values exist and the transition guard gives the button real consequence.

---

## Proposal 7: FAIR Metadata Fields on AbstractDataObject

**Name:** FAIR Core Fields — License, AccessRights, CreatorOrcid, FundingRef

**Problem it solves:**
The RDM/FAIR agent scored Shepard's Reusability at 0.9/3 — the weakest FAIR dimension. The three root causes are: (a) no `license` field on any entity (the Unhide feed asserts `schema:license` with no source entity field), (b) no `accessRights` enum so a machine cannot determine access conditions, (c) ORCID is validated on User but never stamped on the entity at creation time (if a User is deleted, the creator link breaks). All three are single-field additions that together close the FAIR gap.
These gaps were independently identified by the FAIR/RDM agent (entire §3 gap analysis), the strategy advisor ("FAIR gap is R1.1 and R1.2 — both closable in one sprint"), and my own ontology analysis (§4.3 — the Unhide feed is more expressive than the entity model backing it).

**What it looks like:**
Four additive fields on `AbstractDataObject.java`:

```java
private String license;                    // SPDX identifier, e.g. "CC-BY-4.0"; nullable
private String accessRights;               // "OPEN" | "EMBARGOED" | "RESTRICTED"; nullable
private String embargoEndDate;             // ISO-8601 date string, nullable; required when accessRights=EMBARGOED
private String createdByOrcid;             // stamped from User.orcid at creation time; preserved after User deletion
private List<String> fundingReferences;    // "DFG:424955742", "HORIZON-JU-Clean-Aviation-2023-01"
```

Neo4j migration: additive `SET n.license = null, n.accessRights = null, ...` where missing.

`AbstractDataObjectService.create()` stamps `createdByOrcid` from `currentUser.getOrcid()` at creation time — one line. Preserved forever, even after User deletion.

`PublishService.buildMetadata()` populates `policy` (the HMC KIP field) from `Collection.license` — closing the "Unhide feed has no source entity field" gap.

Frontend: `CollectionDetailPage` gains a "FAIR Metadata" expansion panel showing license (SPDX select), access rights (3-option radio), embargo date picker (conditional), and funding references (multi-text input).

**Plugin or core?** Core — these are first-class metadata fields on the core entity model. They feed the KIP plugin, the Unhide plugin, and the RO-Crate export — all of which are already designed against these fields (they just don't exist yet).

**Effort:** S (additive fields + migration + service stamp + IO update + frontend panel — no new entities, no structural change).

**Domain impact:** General researcher (FAIR compliance, all funding bodies), MFFD manufacturing (Clean Aviation JU FAIR mandate for Horizon Europe projects), PLUTO satellite (DLR eLib DOI deposit requires license and creator attribution).

**Cross-finding hook:** Directly unblocks the KIP plugin's `policy` field, the Unhide plugin's `schema:license` assertion, and the FAIR/RDM agent's publisher plugin design. The strategy advisor's HMC Project Call 2026 pitch (deadline 06 July 2026) requires demonstrated FAIR compliance — these four fields are the evidence.

---

## Proposal 8: Provenance Gap Detector

**Name:** ProvenanceGapDetector — Graph-Heuristic Completeness Checker

**Problem it solves:**
Shepard captures provenance automatically (PROV1a), but the completeness of the provenance graph is never checked. A DataObject can have a timeseries container with no `wasGeneratedBy` activity, or a Predecessor link with no associated PROV-O `wasDerivedFrom` edge, or a process chain node with no annotation identifying its process type. These gaps are invisible to researchers and auditors — they only surface when a DIN EN 9100 auditor asks "show me the complete chain" and the chain has holes.
The AI/ML agent (§opportunity 4) identified provenance gap detection as a Cypher-query set, not a model-training task, and rated it "High" user value for EN 9100 audits. The manufacturing quality agent's readiness table shows "chain traversable but no directed lineage walk API" as a MAJOR gap. The UX auditor identified the 6-predecessor truncation as a CRITICAL blocker for compliance auditors.

**What it looks like:**
New read-only backend service `ProvenanceCompletenessService` and endpoint:

```
GET /v2/collections/{appId}/provenance-gaps
```

Returns a structured list of completeness findings, each with a severity (CRITICAL / MAJOR / MINOR), entity reference, and human-readable description:

```json
{
  "collectionAppId": "...",
  "analyzedAt": "2026-05-21T...",
  "totalDataObjects": 15,
  "findings": [
    {
      "severity": "MAJOR",
      "entityAppId": "01234...",
      "entityName": "TR-005-Hold-Day",
      "finding": "DataObject has no process-type semantic annotation (missing m4i:ProcessingStep or shex:ManufacturingProcess)",
      "recommendation": "Add a semantic annotation identifying the process step type"
    },
    {
      "severity": "CRITICAL",
      "entityAppId": "56789...",
      "entityName": "NDT-Inspection-001",
      "finding": "DataObject has Predecessor links but no prov:wasDerivedFrom edge in the PROV graph",
      "recommendation": "The process that created this DataObject was not captured as a PROV Activity"
    }
  ],
  "score": { "critical": 1, "major": 3, "minor": 7 }
}
```

Implemented as a set of Cypher queries:
1. DataObjects with containers but no `wasGeneratedBy` PROV activity.
2. DataObjects with Predecessor links but no `wasDerivedFrom` in the PROV graph.
3. DataObjects with no semantic annotation at all (bare nodes in the lineage).
4. DataObjects with `status = null` (the TR-005 hold-day problem identified by the manufacturing quality agent).
5. DataObjects whose `status` field is non-null but not in the known vocabulary (catches typos in the freetext status).

Frontend: a "Provenance Health" panel on the Collection detail page, showing the gap count as a badge and expanding to the finding list. Links from each finding navigate directly to the affected DataObject.

**Plugin or core?** Core — provenance completeness is a cross-cutting concern that uses only existing graph structure. No new entity types. This is graph analytics over existing data, not a domain-specific plugin.

**Effort:** M (backend: 5 Cypher queries + service + REST endpoint + IO classes + 8 unit tests; frontend: health panel + badge + navigation links).

**Domain impact:** MFFD manufacturing (primary — EN 9100 audit readiness check before certification submission), general researcher (FAIR completeness signal before PID minting), PLUTO satellite (mission data completeness for DLR archival requirements).

**Cross-finding hook:** Directly addresses the AI/ML agent's Opportunity 4 (provenance gap detection as graph heuristics). Complements the FAIR/RDM agent's Metadata Completeness Score (Proposal from their §4) — the two together cover both FAIR metadata completeness (Proposal 7 above) and structural provenance completeness. The UX auditor's "Trace provenance upstream" button becomes more actionable when the gaps are visible before the auditor needs to walk the chain manually.

---

## Proposal 9: Snapshot Label Refresh Job

**Name:** AnnotationLabelRefresher — Drift-Safe Snapshot Sync

**Problem it solves:**
`SemanticAnnotation.propertyName` and `.valueName` are snapshot strings captured at annotation time. If a term's `skos:prefLabel` is updated in the `.ttl` file and the ontology is re-seeded, all existing annotations retain their stale snapshot labels. Search uses the string form `propertyName::valueName`. A label change silently breaks search without any error or warning. This is a quiet data integrity risk that grows over time as vocabularies evolve.
Identified in my own findings (§1.8, Gap discovered during label-drift analysis).

**What it looks like:**
New admin-triggered (not automatic) background job:

```
POST /v2/admin/semantic/{repoAppId}/refresh-annotation-labels
```

The job:
1. Iterates all `SemanticAnnotation` nodes in Neo4j (batched, 500 at a time).
2. For each annotation, fetches the current `skos:prefLabel` from the n10s repo for both `propertyIRI` and `valueIRI`.
3. If the fetched label differs from the stored snapshot (`propertyName` / `valueName`), updates the Neo4j node and records the change (old label → new label, annotation appId, timestamp) in a `LabelRefreshAuditLog` table.
4. Returns a summary: total checked, total updated, list of changed annotations.

The job is **admin-triggered only** (never automatic) — preserving the principle that ontology evolution is a deliberate operator action. The audit log makes changes traceable. The `ProvenanceCaptureFilter` (PROV1a) captures the admin action automatically.

A `GET /v2/admin/semantic/{repoAppId}/label-drift` endpoint reports the current drift count (annotations whose snapshot labels differ from current n10s labels) without making any changes — useful for monitoring before triggering the refresh.

**Plugin or core?** Core — annotation label consistency is a core data integrity concern for the SemanticAnnotation entity. No plugin hook is appropriate here.

**Effort:** S (backend: job + batch Cypher + audit table migration + two REST endpoints + 5 unit tests; no frontend required beyond surfacing the drift count in the admin semantic panel).

**Domain impact:** General researcher (all domains — prevents silent search breakage as ontologies evolve), MFFD manufacturing (controlled vocabulary evolution between project phases).

**Cross-finding hook:** Enables confident ontology versioning — operators can update TTL files (e.g., add the MFFD vocabulary pack from Proposal 5, or update CHAMEO to a new release) without worrying about silent annotation-search breakage. Supports the strategy advisor's "register in NFDI4ING" recommendation — NFDI4ING ontologies evolve; Shepard must handle that gracefully.

---

## Proposal 10: Equipment Registry Pattern + CalibrationRecord Entity

**Name:** EquipmentRegistry — Calibration Traceability as First-Class Graph

**Problem it solves:**
Equipment calibration state is the single most important missing concept for Nadcap and EN 9100 §7.6 (control of monitoring and measuring equipment). An AFP laser head's calibration certificate (calibrated date, expiry, calibration authority, NPL/PTB standard reference) is not representable in Shepard's data model as a typed, time-indexed relationship to the process DataObjects that used the equipment. The current workaround (freetext `attributes["equipment_calibration_date"]`) breaks audit trails because there is no machine-readable validity period.
The manufacturing quality agent rated this CRITICAL (§2, EN 9100 §7.6) and dedicated §6 to a detailed design. My own findings (Gap 4) identified the same structural absence.

**What it looks like:**
New plugin `shepard-plugin-calibration` (plugin-first per CLAUDE.md §"Always: think plugin-first"):

**New Neo4j entities (in plugin):**
```java
@Node("Equipment")
class Equipment extends BasicEntity {
    String equipmentId;          // e.g. "AFP-LH-007"
    String equipmentType;        // e.g. "AFP_LaserHead"
    String manufacturer;
    String modelNumber;
    String serialNumber;
    String ownerDepartment;
    String location;
}

@Node("CalibrationRecord")
class CalibrationRecord extends BasicEntity {
    String calibrationDate;           // ISO-8601
    String calibrationDueDate;        // ISO-8601
    String calibrationAuthority;      // e.g. "DLR-ILS Metrology"
    String standardReference;         // DAkkS/UKAS cert number
    String calibrationStatus;         // "CURRENT" | "EXPIRED" | "SUSPENDED"
    // links to Equipment via @Relationship("CALIBRATES")
    // links to File (cert PDF) via HAS_REFERENCE to a FileContainer
}
```

**New relationship on DataObject (in plugin via SPI hook):**
`(:DataObject)-[:USED_CALIBRATED_EQUIPMENT {atDate: "ISO-8601"}]->(:Equipment)`

This is a time-indexed edge — the `atDate` property records when the equipment was used, allowing querying of "what was the calibration state of this equipment on this date?"

**New REST endpoints (under `/v2/`):**
```
GET    /v2/equipment
POST   /v2/equipment
GET    /v2/equipment/{appId}
PATCH  /v2/equipment/{appId}

GET    /v2/equipment/{appId}/calibration-records
POST   /v2/equipment/{appId}/calibration-records

GET    /v2/data-objects/{appId}/equipment-usage
POST   /v2/data-objects/{appId}/equipment-usage  → links Equipment to this DataObject
```

**Optional process guard (feature-toggle gated):**
If an Equipment linked to a process DataObject via `USED_CALIBRATED_EQUIPMENT` has `CalibrationRecord.calibrationStatus = "EXPIRED"`, the DataObject cannot be promoted to `READY`. Same feature-toggle pattern as Proposal 6.

**Admin config (`:CalibrationConfig` singleton, same pattern as `:SemanticConfig`):**
- `GET/PATCH /v2/admin/calibration/config` — configures default calibration reminder horizon (days before due date to generate a notification).
- NTF1 integration: when a `CalibrationRecord.calibrationDueDate` is within the reminder horizon, emit a notification to the equipment owner.

**PROV-O integration:** The `USED_CALIBRATED_EQUIPMENT` edge with `atDate` enables a `prov:used` assertion against the Equipment entity with a temporal qualifier — using the `time:Interval` ontology already seeded.

**Plugin or core?** Plugin (`shepard-plugin-calibration`) — equipment registries have their own release cadence, their own deployment decision (not all institutes need Nadcap-grade calibration tracking), and their own UI surface. Core provides the SPI hook for adding the `USED_CALIBRATED_EQUIPMENT` relationship to DataObjects. The manufacturing quality agent's §9 correctly identified this as a medium-term plugin.

**Effort:** L (new plugin: 2 new entity types + DAO + service + REST endpoints + admin config singleton + feature toggle + notification hook + 15+ unit tests; frontend: equipment browser panel + calibration record list + usage link UI).

**Domain impact:** MFFD manufacturing (primary — Nadcap compliance, EN 9100 §7.6), LUMEN rocket testing (test bench instrumentation calibration), general researcher (any measurement-intensive programme requiring equipment certification traceability).

**Cross-finding hook:** The manufacturing quality agent's entire §6 (calibration traceability design) maps directly here. The FAIR/RDM agent's `prov:used` link in RO-Crate export becomes richer when equipment is a first-class node with a PID. The analytics/AI agent's provenance gap detector (Proposal 8) gains a new check: "process DataObject has no linked equipment" — surfaced as a MAJOR finding for Nadcap-relevant process steps.

---

## Proposal 11: Plugin Improvement — AAS Plugin: Missing install.md + DataObject → Submodel Mapping Gap

**Name:** AAS Plugin Completion — install.md + Submodel Content Mapping

**Problem it solves:**
The `shepard-plugin-aas` is the most sophisticated plugin in the repository — it maps Collections → AAS Shells, provides IDTA v3 server self-description, and ships an outbox-based registry sync. However, it is missing `install.md` (required per CLAUDE.md §"Always: plugins ship their own documentation") and its `reference.md` documents Shell listing and single-shell retrieval but does not document the DataObject → Submodel mapping depth.
More critically: the AAS Submodel is the IDTA standard vehicle for structured, machine-readable process data (including materials, calibration, process parameters). The current mapping creates a Shell per Collection and Submodel references per DataObject, but it does not map DataObject `attributes` or semantic annotations into Submodel elements (SME). This means an external AAS registry receives structurally correct but semantically empty Shells — the attributes and ontology annotations that are Shepard's core value proposition do not cross the IDTA wire.
From a plugin review perspective, this is the gap between a plugin that exists and a plugin that delivers.

**What it looks like:**

**Immediate (documentation gap):**
Write `plugins/aas/docs/install.md` covering: prerequisites (AAS registry URL, auth), compose-profile changes, `:AasConfig` fields and defaults, the outbox retry mechanism, healthcheck endpoint, and known pitfalls (registry TLS, Base64-URL encoding of Shell IDs).

**Medium-term (semantic mapping extension):**
Extend `AasShellMappingService` to map DataObject attributes into Submodel elements:

```java
// For each DataObject → AAS Submodel:
// 1. Attributes map → SME Property elements (typed: String for text, Float for parseable numbers)
// 2. SemanticAnnotation list → SME Property with SemanticId = annotation.propertyIRI
//    and value = annotation.valueIRI (or annotation.numericValue + annotation.unitIRI from Proposal 1)
// 3. Status → SME Property "administativeInformation.status"
// 4. LabJournalEntries → SME Blob with mimeType "text/markdown"
```

This makes the AAS export carry the full Shepard semantic payload — the ontology IRIs from QUDT, metadata4ing, and CHAMEO become Submodel element semantic IDs, enabling AAS-to-AAS interoperability for a receiver who also understands those ontologies.

The IDTA SMT (Submodel Template) import path (already in `AasIdtaTemplateImportService`) runs in the opposite direction — it should also be documented (currently absent from `reference.md`).

**Plugin or core?** Plugin improvement — staying in the plugin. The mapping extension is plugin-internal and does not require core changes (it uses the existing `DataObjectService` and `SemanticAnnotationService` already consumed by the plugin).

**Effort for install.md:** XS (1 day documentation). **Effort for semantic mapping extension:** M (2 sprints — extend `AasShellMappingService` + update outbox serialization + test against IDTA AAS demo registry).

**Domain impact:** MFFD manufacturing (primary — the MFFD programme uses AAS as the standard for digital product passports under the Model-Based Enterprise framework; Shepard-generated AAS Shells with CHAMEO defect annotations would be directly consumable by IDTA-compliant systems), general researcher (any DLR programme participating in EOSC/Catena-X AAS ecosystems).

**Cross-finding hook:** The strategy advisor identified the plugin architecture as a genuine differentiator — but noted that "the question is whether it enables ecosystem growth." A semantically rich AAS export (not just a structural shell) is the feature that enables Shepard data to flow into Catena-X supply chain networks. The FAIR/RDM agent's I3 gap (qualified references using PIDs as link targets) is partially addressed when Submodel elements carry QUDT/metadata4ing IRIs as semantic IDs.

---

## Proposal 12: Causal Edge — TimeseriesAnnotation to Command/Event Link

**Name:** CausalAnnotationEdge — Fine-Grained Command-Response Causality

**Problem it solves:**
The current model can answer "which DataObject (commanding session) preceded which DataObject (anomaly investigation)" at the coarse level of DataObject-to-DataObject Predecessor/Successor. It cannot answer "which specific telecommand (row in a StructuredData document) caused which specific telemetry response (time interval in TimescaleDB)" — the fine-grained causal link needed for PLUTO anomaly investigations and for the LUMEN anomaly root cause chain.
My own findings identified this as Gap 3 — the structural absence of a typed edge from `TimeseriesAnnotation` to a StructuredData record. The AI/ML agent's anomaly notification pipeline (their §8, "Anomaly notification pipeline") assumes the causal context can be retrieved; it cannot without this edge.

**What it looks like:**
Minimal-friction implementation: extend `TimeseriesAnnotation` with two optional metadata fields (option (b) from my Gap 3 analysis — lower friction than a new edge type, but preserving query expressibility):

```java
// On TimeseriesAnnotation.java — additive optional fields:
private String causalEntityAppId;      // appId of the DataObject carrying the causal event
private String causalDocumentId;       // MongoDB document ID in the StructuredData container
private String causalRecordField;      // field name within the document (e.g. "tc_id")
private String causalRecordValue;      // value that identifies the causing record (e.g. "TC-0x1234")
```

When `AI1b` detects an anomaly interval on a channel and `createAnnotations=true`, the calling client (or a future auto-correlation service) can supply the `causal*` fields in the annotation request body.

REST update: `POST /v2/timeseries-annotations/` body gains the four optional fields. `GET /v2/timeseries-annotations/{appId}` response includes them.

**Forward path to a typed edge (option (a), deferred):** Once the usage pattern is established via the metadata fields, a future sprint can promote to a proper graph edge `(:TimeseriesAnnotation)-[:CAUSED_BY]->(:StructuredDataEntry)` with a bridge node. The metadata fields are the stepping stone — they validate the use case before committing to the structural change.

**PROV-O expression:** The causal link is expressible as `prov:wasTriggeredBy` (a PROV-O property) once the causal entity is a first-class PROV entity. For now, the metadata fields serve as a lightweight placeholder.

**Plugin or core?** Core — `TimeseriesAnnotation` is a core entity; its extension is a core model change.

**Effort:** S (four additive nullable fields + migration + IO update + 3 unit tests; no frontend change needed beyond displaying the fields if present).

**Domain impact:** PLUTO satellite (primary — the "which command caused which anomaly" question is the defining use case), LUMEN rocket testing (secondary — throttle command → vibration spike causal chain in TR-004), MFFD manufacturing (tertiary — process parameter → defect causal chain for root cause analysis).

**Cross-finding hook:** The AI/ML agent's anomaly notification pipeline becomes more informative when the notification can say "anomaly on `vib_fuel_pump_x` at t=8.4s, likely caused by throttle command TC-0x1234" rather than just "anomaly detected." The UX auditor's "ancestor walk" becomes semantically richer — the causal chain can include not just DataObject ancestors but the specific command event that triggered a telemetry response.

---

*Proposals 1–12 above span the full spectrum from S (hours) to L (weeks). The dependency ordering that maximises early impact:*

| Phase | Proposals | Rationale |
|---|---|---|
| **Sprint 1 (foundations)** | 3 (CHAMEO + SSN/SOSA), 5 (Domain Vocabulary Pack), 7 (FAIR Fields), 9 (Label Refresher) | Additive ontology + metadata changes; zero risk; high FAIR leverage |
| **Sprint 2 (data model)** | 1 (QuantifiedAnnotation), 2 (MaterialBatch), 12 (CausalEdge) | Model extensions with migrations; unlock semantic depth |
| **Sprint 3 (workflow)** | 6 (QualityStatus + Gate), 8 (Provenance Gap Detector) | Workflow enforcement + observability; depend on vocabulary from Sprint 1 |
| **Sprint 4 (structural)** | 4 (AnnotatableFile), 11 (AAS install.md + semantic mapping) | Larger structural work + plugin improvement |
| **Later (plugin)** | 10 (EquipmentRegistry) | Full plugin — coordinate with NTF1 dependency |

*Agent: Principal Aerospace Domain Engineer and Data Ontologist | Generated: 2026-05-21*
