---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Manufacturing & Quality Engineering — Feature Proposals
**Author:** Industrial Manufacturing & Quality Engineer agent  
**Date:** 2026-05-21  
**Synthesised from:** IME/AQE findings + all peer agent findings (UX, Ontologist, API, RDM, Strategy, Analytics)

---

## Proposal 1: Predecessor-Status Gate (Quality Progression Lock)

**Problem it solves**  
EN 9100 §8.6 requires that a product cannot advance to the next process step until planned verification activities are satisfactorily completed. Today, a successor DataObject can be created at any time regardless of its predecessor's status. This is a structural gap for any quality-gate workflow (own finding §2, severity CRITICAL). It also requires a `PATCH` endpoint for predecessor/successor relationships on `/v2/` — which does not currently exist (API Scrutinizer MAJOR: "Add a DataObject predecessor/successor relationship — Cannot do it from /v2/"). The gate depends on that endpoint as a sub-deliverable.

**What it looks like**  
A shop floor IME creates `NDT-Inspection-001` DataObject, sets it to `NCR_OPEN`. When attempting to create `Stringer-Weld-001` with a predecessor link to `NDT-Inspection-001`, the API returns `HTTP 409 Conflict` with a reason body: `{ "blocked": true, "openNcrs": [{ "appId": "...", "name": "NDT-Inspection-001", "status": "NCR_OPEN" } ] }`. The UI surfaces this as a blocking banner on the DataObject creation dialog: "Cannot create successor — 1 predecessor has status NCR_OPEN." Proceeding requires the NCR to be resolved first (status moved to `READY` or `CERTIFIED`). A `FeatureToggle` (`quality.predecessor-gate.enabled`, default `false`) lets operators disable this during initial data migration.

Sub-deliverables (in order):
1. `PATCH /v2/data-objects/{appId}/relationships` — sets `predecessorAppIds[]` and `successorAppIds[]` (additive only, no removals). This closes the API Scrutinizer MAJOR missing-operation gap.
2. Service-layer guard in `DataObjectService.createDataObject` checking predecessor status.
3. Frontend 409 handler showing the blocked-predecessor list.

**Plugin or core?**  
Core — this is a DataObject lifecycle invariant, not a domain-specific extension. The role gate (who can override) and transition rules may eventually move to `shepard-plugin-quality`, but the structural guard belongs in the core service layer (same reasoning as why auth stays in-tree per CLAUDE.md).

**Effort estimate:** M (2 weeks backend + tests; 3 days frontend)

**Domain impact:** MFFD manufacturing, EN 9100 §8.6 compliance, EASA audit

**Cross-finding hook:**  
- API Scrutinizer MAJOR: "Add a DataObject predecessor/successor relationship — Cannot do it from /v2/" — this proposal ships that missing endpoint as part of the gate implementation.
- UX Auditor IME persona: shop floor operator cannot currently be blocked from creating the next process step DataObject even with an active defect. The 409 banner surfaces the blockage visibly.

---

## Proposal 2: Extended Status Vocabulary with Role-Gated Transitions

**Problem it solves**  
The current five-value status vocabulary (`DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED`) is research-lifecycle oriented and has no aerospace manufacturing semantics. Status is stored as a free String with no server-side machine and no role restriction on transitions (own finding §2, severity CRITICAL + §5). Any Write user can freely move a PUBLISHED DataObject back to DRAFT, silently destroying its conformity record semantics.

**What it looks like**  
Add seven new status values to the server-enforced enum (see own finding §5): `NCR_OPEN`, `NCR_DISPOSITIONED`, `ON_HOLD`, `REWORK`, `REJECTED`, `SUPERSEDED`, `CERTIFIED`.

Two changes beyond adding enum values:
1. **Transition guards** in `DataObjectService.updateDataObject` enforce a role gate on specific transitions. Example: `status → CERTIFIED` requires the caller to hold either `instance-admin` or (eventually) the `quality-engineer` role. `status → REJECTED` requires the same. Transitions not in the guard table are unrestricted (preserving backward compatibility for research workflows). Guard table stored as an admin-configurable Neo4j singleton (`:QualityGateConfig`) following the CLAUDE.md "surface operator knobs" pattern.
2. **Immutability lock** when status reaches `CERTIFIED` or `PUBLISHED`. Subsequent attribute/description edits return `HTTP 409 Lock`. Unlock requires an explicit admin action (`POST /v2/data-objects/{appId}/unlock`) with a reason body, which creates a provenance activity. This closes the RDM finding on A2 (tombstone / record preservation after retirement) at the DataObject level.

Frontend: the status dropdown becomes a state-machine chip. Illegal transitions are greyed out with a tooltip ("Requires quality-engineer role" or "Record is locked"). A CERTIFIED DataObject shows a green "Certified" banner at the top of its detail page.

**Plugin or core?**  
Core — status is a first-class entity field. Transition guards for specific roles may evolve into `shepard-plugin-quality` config, but the core mechanism (guard table, lock mechanism, role check) stays in-tree as a security perimeter concern.

**Effort estimate:** M (1 week backend — no schema migration; 1 week frontend dropdown + lock UX)

**Domain impact:** MFFD manufacturing, EN 9100 §8.7 + §8.6, EASA Part 21 G, PLUTO (as ARCHIVED → CERTIFIED for mission phase closure)

**Cross-finding hook:**  
- RDM agent: A2 gap — published records remain mutable; the `CERTIFIED` + immutability lock closes this for manufacturing conformity records.
- UX Auditor curator persona: status filter is currently client-side only, missing items across pages. The server-side status enum enforcement enables a proper server-side `?status=` filter parameter on `GET /v2/collections/{appId}/data-objects`, closing UX Auditor finding (CollectionDataObjectsPanel status filter, HIGH).

---

## Proposal 3: Material Batch as First-Class Graph Node

**Problem it solves**  
Material lot IDs (`lox_lot_id: "LOX-2024-01"`, `fiber_lot_id: "CF-LMPAEK-2024-07-B3"`) are buried inside StructuredData JSON blobs, making DIN EN 9100 §8.5.2 traceability queries a full-text JSON scan rather than a graph traversal. A question like "which MFFD panels consumed fiber batch B3?" cannot be answered without scanning every StructuredData container payload (Ontologist Gap 5; own finding §2 row "Identification and traceability", CRITICAL). The Ontologist found this independently and proposed Opportunity 2 (Material batch as first-class graph node).

**What it looks like**  
No new entity types are needed — a `MaterialBatch` is a DataObject with a convention:
- Mandatory attributes (enforced via Template `T-MATERIALBATCH`): `lot_id`, `supplier`, `material_type`, `conformance_cert_id`, `received_date`, `expiry_date` (nullable).
- StructuredData container: the full certificate of conformance (JSON or PDF-extracted JSON).
- Predecessor/Successor links: each process DataObject that consumes this batch lists the MaterialBatch DataObject as a predecessor.
- Queryable attribute: `lot_id` also copied to the *consuming process DataObject's* attributes for fast single-hop Cypher lookup without full graph traversal.

Operator-facing: a new `shepard-template-materialbatch.yaml` shipped with the platform's bundled Template library. When a process DataObject is created, the IME can search for the relevant MaterialBatch DataObject and add it as a predecessor in a single search-and-link step.

Audit query becomes:
```cypher
MATCH (mb:DataObject {attributes: {lot_id: 'CF-LMPAEK-2024-07-B3'}})
      -[:HAS_SUCCESSOR*]->(proc:DataObject)
RETURN proc.name, proc.status
```
This is a standard Neo4j graph traversal — no JSON scanning.

**Plugin or core?**  
Core — this is a seed pattern + Template definition + documentation change, not a new entity or REST endpoint. No code changes needed for the concept. A `GET /v2/data-objects/{appId}/ancestor-chain?maxDepth=10` endpoint (lineage walk) would complete the tooling; see Proposal 5.

**Effort estimate:** S (1 week — Template definition, documentation, seed.py update to demonstrate the pattern, LUMEN seed adds MaterialBatch DataObjects for LOX and LCH4 lots)

**Domain impact:** MFFD manufacturing, EN 9100 §8.5.2 traceability, DFG/FAIR R1.3 (domain-relevant community standards)

**Cross-finding hook:**  
- Ontologist Gap 5 (lot IDs buried in JSON, not graph-traversable) — directly closes this gap.
- Strategy Advisor: "ship a real MFFD dataset" recommendation — the MaterialBatch pattern is the structural pre-requisite for making an MFFD AFP run dataset auditable without additional tooling.
- RDM agent I3 gap: qualified references become graph-traversable once lot IDs are nodes with appIds.

---

## Proposal 4: Equipment Registry + AAS-Backed Calibration Records

**Problem it solves**  
Calibration traceability is the most critical gap for Nadcap and EN 9100 §7.6 (own finding §2, severity CRITICAL; §6). An AFP robot laser head calibration cannot be linked to a process run as a typed, machine-enforceable relationship. The Ontologist found the same gap independently (Gap 4). Critically, the `shepard-plugin-aas` already exists in `plugins/aas/` — and IEC 63278 Asset Administration Shell submodels were designed exactly for equipment-as-data. This proposal extends the existing AAS plugin rather than inventing a new entity.

**What it looks like**  
Extend `shepard-plugin-aas` to support an "Equipment Calibration" submodel template aligned to the AAS IDTA Submodel Template "Handover Documentation" (IDTA-02004). The AAS submodel provides: `SerialNumber`, `ManufacturerProductFamily`, `CalibrationDate`, `CalibrationDueDate`, `CertifyingBody`, `CertificateId`.

Integration:
- Each piece of measurement equipment (AFP laser head, force gauge, thermocouple logger) gets an `AAS Reference` DataObject entry, with the AAS submodel carrying calibration state.
- A process run DataObject links to the calibration state at run time via a `prov:used` semantic annotation pointing to the equipment DataObject's appId.
- A calibration-validity check runs at DataObject creation (leveraging Proposal 1's gate infrastructure): if `CalibrationDueDate` in the linked equipment's AAS submodel is in the past, the process DataObject creation returns a `HTTP 409` with reason `{ "blocked": true, "reason": "equipment-calibration-expired", "equipmentAppId": "..." }`. This can be toggled off via `FeatureToggle`.

Frontend: a "Linked Equipment" expansion panel on the DataObject detail page shows equipment name, calibration status (green/amber/red chip), and calibration due date. The panel pulls from the `prov:used` annotations via the SPARQL proxy (`GET /v2/semantic/{repoAppId}/sparql`).

Admin: `shepard-admin aas calibration-status [--collection={appId}]` lists all equipment with expired or soon-expiring calibration.

**Plugin or core?**  
Plugin (`shepard-plugin-aas` extension) — the AAS plugin already exists; calibration records are a submodel variant, not a new SPI concept. The `prov:used` semantic annotation link is in-tree (PROV-O is seeded). Only the calibration-validity guard in `DataObjectService` needs a small in-tree hook: `EquipmentCalibrationGuard` SPI with a default no-op implementation; the AAS plugin registers the live implementation.

**Effort estimate:** L (3–4 weeks AAS submodel work + frontend panel + calibration guard SPI + tests)

**Domain impact:** MFFD manufacturing, EN 9100 §7.6, Nadcap special-process traceability, PLUTO (spacecraft calibration records for scientific instruments)

**Cross-finding hook:**  
- Ontologist Gap 4: "Equipment calibration state — no native home." This proposal provides the home by extending the existing AAS plugin with a calibration submodel.
- Existing plugin improvement: the `plugins/aas/` directory exists but was not mentioned in any other proposal. This is the "at least 1 plugin improvement" required by the task.

---

## Proposal 5: Ancestor-Chain Endpoint + Branching DAG Provenance View

**Problem it solves**  
The UX Auditor found a CRITICAL blocker: `DataObjectProvGraph.vue` hard-caps at 6 predecessors (`slice(0, 6)`, lines 86 and 106) and silently drops ancestors beyond that limit. A compliance auditor tracing a 4-hop defect chain (ply → layup run → robot session → material batch) hits this cap and loses traceability — exactly what EN 9100 requires to be complete (UX Auditor, Compliance Auditor persona, CRITICAL). The existing `DataObjectProvGraph.vue` is already in the working tree (untracked); the fix is to remove the cap and feed it a proper ancestor-walk endpoint.

**What it looks like**  
Two deliverables:

1. **`GET /v2/data-objects/{appId}/ancestor-chain?maxDepth=10`** — returns an adjacency list of predecessor DataObjects up to `maxDepth` hops. Response: `{ "nodes": [{appId, name, status, ...}], "edges": [{fromAppId, toAppId}] }`. The Cypher is a depth-bounded `MATCH p=(root:DataObject {appId:$appId})<-[:HAS_PREDECESSOR*1..{depth}]-(ancestor:DataObject)` traversal. This is the missing operation the UX Auditor identified (Idea A: "Trace provenance upstream") and also closes the auditor's "no recursive predecessor walk" CRITICAL finding.

2. **Branching DAG layout in `DataObjectProvGraph.vue`** — replace the force layout with a dagre (DAG layout) for the predecessor-successor chain view. The MFFD rework loop (AFP → NDT FAIL → Rework → NDT PASS → Weld) is a DAG, not a free network. Dagre renders it as a left-to-right directed acyclic graph where branching (rework) is visually obvious. Status colour-codes nodes: `NCR_OPEN` = red, `CERTIFIED` = green, `REWORK` = amber. Remove the `slice(0, 6)` cap; replace it with `maxDepth=10` (configurable via a depth slider in the panel).

For MFFD EASA audit use: the graph panel includes an "Export Lineage" button that calls the `ancestor-chain` endpoint and packages the response as a simple JSON or CSV table (node name, status, timestamp, operator) — the linear audit trail an auditor can hand to a certification body without post-processing (UX Auditor, Medium finding: "Export for audit").

**Plugin or core?**  
Core backend endpoint (DataObject lineage walk is a core graph capability). Frontend fix is in-tree.

**Effort estimate:** M (1 week backend endpoint + tests; 1 week frontend DAG layout + remove cap + export button)

**Domain impact:** MFFD manufacturing, EN 9100 §7.8.2 traceability, EASA audit, PLUTO (command-response causal chain)

**Cross-finding hook:**  
- UX Auditor CRITICAL: 6-predecessor cap in `DataObjectProvGraph.vue` — directly fixes this.
- UX Auditor Idea A: "ancestor walk — Trace provenance upstream button" — implements it with a dedicated backend endpoint.
- UX Auditor CRITICAL (Compliance Auditor persona): "process-chain traversal — no UI-level recursive predecessor walk" — closes this.
- API Scrutinizer MAJOR missing operation: "List all DataObjects across all Collections (search) / Get a DataObject by appId without knowing the Collection appId" — the ancestor-chain endpoint adds a new traversal path that returns DataObjects by graph position, not by collection membership.

---

## Proposal 6: File-Level Annotation Bridge (AnnotatableFile)

**Problem it solves**  
Individual files within a FileBundle cannot be semantically annotated. An MFFD AFP run produces ~200 STP layup-geometry files (one per ply), and an NDT scan set may contain hundreds of C-scan images. A defect detected on ply 47 cannot be tagged `CHAMEO:LocalPorosityDefect` on that specific file — the annotation can only go on the FileBundle or DataObject, losing spatial specificity (Ontologist Gap 2; own finding, NDT scans cannot carry defect class annotations). The feature matrix shows L7 ("Annotate file / structured / spatial payloads") as `📐 (queued)`.

**What it looks like**  
Introduce an `AnnotatableFile` bridge node (analogous to the existing `AnnotatableTimeseries` bridge):
- Neo4j entity `:AnnotatableFile` with `fileObjectId` (GridFS OID or S3 key), `fileName`, and a `HAS_ANNOTATION` relationship.
- REST: `GET/POST /v2/file-containers/{containerAppId}/files/{fileName}/annotations` and `DELETE .../{annotationId}`.
- Frontend: in the file list table (`FileContainerDetailPage.vue`), each file row gets an annotation count badge (inline, like the Timeseries measurements table). Clicking opens `AddAnnotationDialog` targeting the `AnnotatableFile` node. CHAMEO terms (once added to the ontology manifest; see separate ontology proposal) appear in the autocomplete.

For MFFD NDT use: an NDT engineer opens the C-scan FileBundle, finds the image for ply 47, tags it `CHAMEO:LocalPorosityDefect` + `shex:QualityFail`. The annotation is queryable via SPARQL and appears in the RO-Crate export's annotation document.

**Plugin or core?**  
Core — the `AnnotatableTimeseries` bridge precedent is in-tree; the file bridge follows the same shape. L7 is already in the feature matrix as a queued core item.

**Effort estimate:** M (1 week backend — new entity, DAO, REST, migration; 1 week frontend file-row annotation badges + dialog integration; 3 days tests)

**Domain impact:** MFFD manufacturing (NDT defect classification per ply), EN 9100 §8.7 (control of nonconforming outputs — defect must be identified on the specific affected unit)

**Cross-finding hook:**  
- Ontologist Gap 2: "Sub-container granularity — files and documents not annotatable." Directly closes this.
- Ontologist Opportunity 3: Add CHAMEO to the ontology manifest. The AnnotatableFile bridge is the consumer that makes CHAMEO terms actionable at file granularity.
- UX Auditor annotation friction: with bulk file selection in the file list (row checkboxes), a batch-annotate flow becomes possible; the bridge is the backend prerequisite.

---

## Proposal 7: Shop Floor Template Mode (Large-Target, Touch-First Layout)

**Problem it solves**  
The current UI is designed for researchers at a desk. An MFFD shop floor IME environment has fundamentally different constraints: 27" ruggedized touchscreen, gloved hands, 85+ dB noise, 60 cm viewing distance (own finding §7). The UX Auditor documented the same gap from the other direction: no shop floor mode, small touch targets, no big-number display, no keyboard/scan navigation (IME persona, HIGH). The Templates system (T1a–T1f shipped) provides the correct hook: a per-Template `shopFloorMode: boolean` flag that triggers an alternative rendering path.

**What it looks like**  
Extend `ShepardTemplate` DSL with a `shopFloorMode: true` flag. When this flag is set, the DataObject creation/edit form for that Template renders in a large-target mobile-first layout:

- Field labels at 24px; input fields at 80px height (minimum); dropdowns render as full-width button arrays (status options as large tap tiles, not a dropdown select).
- A fixed "Advance Status" full-width primary button at the bottom of every DataObject detail page rendered from a shop-floor template, sized 80px tall.
- A "Raise NCR / Place Hold" red secondary button, also 80px, immediately below the advance button. Two taps from any DataObject in shop-floor mode. The button opens a simplified NCR form with only: defect type (enum chips, not freeform), responsible person (current user auto-filled), and optional photo upload.
- QR code generation: once a DataObject reaches `CERTIFIED` status, a "Print QR" button appears (within shop-floor mode) that renders the DataObject's `/v2/` URL as a QR code for physical panel labelling.
- Barcode/scan-to-find: a URL-scheme handler `shepard://dataobject?q={value}` registered in the Nuxt manifest. When a barcode scanner (keyboard-wedge) triggers the URL, the app navigates to the DataObject whose `name` or `attributes["serial_number"]` matches the scanned value.

Admin: `shopFloorMode` flag is a Template DSL field; operators define shop-floor templates per process step. No new admin configuration required beyond the Template editor.

**Plugin or core?**  
Core — the Template system and its rendering are in-tree. The large-target layout is a CSS/Vuetify variant of the existing form renderer, not a new plugin.

**Effort estimate:** L (2 weeks Template DSL extension + new rendering path; 1 week QR generation + barcode scan handler; 1 week tests and accessibility review)

**Domain impact:** MFFD manufacturing (shop floor adoption), EN 9100 §8.6 (operator interface for status advancement at point of work)

**Cross-finding hook:**  
- UX Auditor IME persona (HIGH): large-target status button, no glove-friendly mode, no keyboard/scan navigation — closes all three.
- UX Auditor Opportunity 2 (bulk actions): the shop floor's simplified NCR-raise form is the IME equivalent of the desk curator's bulk annotation, both reducing click depth for a common action.
- Strategy Advisor Risk 2 (UI completeness gap): the shop floor mode is a new UI surface with no backend-completeness gap, directly improving the "researchers see an incomplete tool" risk.

---

## Proposal 8: Bulk Status Transition with Role Gate (Curator + Quality Review Table)

**Problem it solves**  
A data curator setting 15 LUMEN test runs to `READY` must open each DataObject individually and change the status — 15 × 5 = 75 interactions (UX Auditor curator persona, HIGH). At MFFD scale (50+ AFP passes), this becomes 250+ interactions. There is no bulk DataObject selection UI and no batch status endpoint (UX Auditor CRITICAL for annotation, same infrastructure gap). The server-side `status` filter on `GET /v2/collections/{appId}/data-objects` does not exist (server-side filter missing), compounding the problem.

**What it looks like**  
Three coordinated deliverables:

1. **Server-side status filter**: add `?status=` query parameter to `GET /v2/collections/{appId}/data-objects`. Cypher adds `AND do.status = $status` to the existing list query. This closes the UX Auditor's pagination-gap finding (status filter applies to current page only; HIGH severity).

2. **Row selection in CollectionDataObjectsPanel**: add `v-checkbox` to each table row. A "Select all on page" header checkbox. When selection is non-empty, a bulk-action toolbar appears above the table: "Set status →" dropdown (filtered to allowed transitions per Proposal 2's role gate), "Add annotation", "Export selected".

3. **Batch status endpoint**: `POST /v2/data-objects/bulk-status` with body `{ "appIds": [...], "status": "READY" }`. The service applies Proposal 2's transition guards per DataObject; returns a partial-success response listing which DataObjects succeeded and which were rejected (with reason). Maximum 200 DataObjects per batch.

For quality review workflows: a quality engineer opens the collection, filters by `?status=IN_REVIEW`, selects all 15 test runs, verifies them, and bulk-promotes to `CERTIFIED` in one action — subject to the role gate from Proposal 2.

**Plugin or core?**  
Core — row selection and bulk actions are a fundamental table UX pattern. The batch status endpoint is a small additive `/v2/` surface.

**Effort estimate:** M (3 days backend batch endpoint + filter param; 1 week frontend row selection + toolbar; 3 days tests)

**Domain impact:** MFFD manufacturing (curator efficiency), EN 9100 §8.6 (quality review gate), PLUTO (bulk promotion of commissioning DataObjects after ground verification)

**Cross-finding hook:**  
- UX Auditor CRITICAL: "DataObject selection — the DataObjects table has no row selection." Directly closes this, the highest-impact missing affordance for the curator persona.
- UX Auditor HIGH: "CollectionDataObjectsPanel status filter — client-side only." The server-side `?status=` parameter closes this.
- UX Auditor bulk annotation (Opportunity 2): the row selection infrastructure is shared with bulk annotation — the "Add annotation" bulk action reuses `AddAnnotationDialog` with a `targets[]` array.

---

## Proposal 9: AI Anomaly → NCR Auto-Raise Bridge

**Problem it solves**  
AI1b (MAD anomaly detection) is shipped and detects anomaly intervals on timeseries channels with confidence scores. The detected anomaly writes a `TimeseriesAnnotation` (`aiGenerated=true`, `label=anomaly`). Currently, an engineer must manually poll or browse to discover this annotation, then manually create an NCR DataObject to begin the investigation workflow. The Analytics agent identified this bridge (§8: "Anomaly notification pipeline — AI1b → NTF1 bridge") as a 1-day integration once NTF1 ships. This proposal extends the bridge to also auto-raise an NCR draft DataObject — closing the loop between AI detection and quality workflow.

**What it looks like**  
When `POST /v2/timeseries-references/{refAppId}/detect-anomalies` detects an interval with `confidence >= 0.8` and `createAnnotations=true`:

1. **NTF1 notification**: emit a Notification via the `NotificationProducer` SPI to all DataObject/Collection watchers. Message: "Anomaly detected: channel `vib_fuel_pump_x`, t=7.65s–8.45s, confidence=0.92. Review required."

2. **NCR draft auto-creation** (gated by `FeatureToggle` `quality.anomaly-auto-ncr.enabled`, default `false`): create a child DataObject of the parent DataObject with:
   - `name`: `"AI-NCR-{parentName}-{channel}-{timestamp}"`
   - `status`: `NCR_OPEN`
   - `attributes`: `{ "defect_type": "AnomalyDetected", "channel": "vib_fuel_pump_x", "ai_confidence": "0.92", "interval_start_ns": "...", "interval_end_ns": "..." }`
   - Semantic annotation: `shex:QualityFail`
   - Provenance: the `:Activity` node carries `aiGenerated=true` and the AI1b `modelId` + `confidence`, satisfying EN 9100's traceability requirement that the origin of a quality flag is documented.

3. The NTF1 notification body includes a deep link to the auto-created NCR DataObject.

The auto-created NCR DataObject is a draft — it does not trigger Proposal 1's predecessor gate until a quality engineer confirms it. The IME reviews, fills in human-readable description, and either closes (status → `READY`) or escalates (keep `NCR_OPEN`).

**Plugin or core?**  
Plugin (`shepard-plugin-quality` dependency for the NCR auto-creation shape; the NTF1 bridge itself is in-tree as a `NotificationProducer` call). The `FeatureToggle` gate means the behaviour is off until explicitly enabled.

**Effort estimate:** M (1 week: NTF1 producer call in AnomalyDetectionService; auto-NCR creation with feature toggle; provenance stamping; 3 days frontend notification display; 5 days tests)

**Domain impact:** MFFD manufacturing (closing the AI-to-quality-workflow loop), EN 9100 §10.2 (nonconformity and corrective action — root-cause link from defect to process), PLUTO (telemetry anomaly → incident report)

**Cross-finding hook:**  
- Analytics AI agent §8: "Anomaly notification pipeline — AI1b → NTF1 bridge — 1-day integration once NTF1 ships." Implements that bridge and extends it to NCR creation.
- Own finding §3: NCR representation lacks a notification trigger — this closes that specific gap.
- Strategy Advisor Risk 2 (UI completeness): the notification display is a concrete UI deliverable tied to an already-shipped backend feature (AI1b), directly reducing the "backend-rich, UI-thin" pattern.

---

## Proposal 10: CHAMEO + SSN/SOSA Ontology Manifest Addition

**Problem it solves**  
CHAMEO (Characterisation Methodology Ontology) is the W3C/NFDI-endorsed vocabulary for defect characterisation and inspection methods. SSN/SOSA (W3C Semantic Sensor Network Ontology) defines sensor capabilities and observations formally. Neither is in the manifest (Ontologist Gap 6; confirmed by checking `ontologies-manifest.json`). Without CHAMEO, defect classification in NDT annotations is freetext — unqueryable via SPARQL, not interoperable with other NFDI4Ing platforms, and not aligned with EN 9100 §8.7's requirement for machine-readable defect classification. This is described as "conspicuous given the MFFD use case" in the Ontologist's surprise section.

**What it looks like**  
Add three bundles to `ontologies-manifest.json`:
1. **CHAMEO** — `https://github.com/emmo-repo/EMMO/raw/master/chameo/chameo.ttl` (SHA-256-pinned); ~180 concepts covering `chameo:DefectType`, `chameo:InspectionMethod`, `chameo:CharacterisationMethod`.
2. **SSN/SOSA** — `https://www.w3.org/ns/ssn/` and `https://www.w3.org/ns/sosa/`; ~120 concepts covering `sosa:Sensor`, `sosa:Observation`, `sosa:FeatureOfInterest`, `sosa:observedProperty`.
3. **MFFD-specific ConceptScheme** (new `.ttl` file at `backend/src/main/resources/ontologies/mffd-process.ttl`): 4 process steps as SKOS concepts (`MFFD:AFP_InSituConsolidation`, `MFFD:ContinuousUltrasonicWelding`, `MFFD:ContinuousResistanceWelding`, `MFFD:StudWelding`), cross-linked to `shex:ManufacturingProcess` via `skos:related`. Follows the `lumen-inspired.ttl` pattern exactly.

Immediate impact on existing features:
- `AddAnnotationDialog.vue` autocomplete gains CHAMEO defect types and SSN/SOSA sensor concepts immediately.
- With Proposal 6 (AnnotatableFile bridge) shipped, NDT scan files can be tagged `chameo:LocalPorosityDefect`.
- `AnnotatableTimeseries` bridge can annotate channels as `sosa:Sensor` observing a `sosa:FeatureOfInterest` — the semantic grounding linking a sensor channel to its physical quantity, currently absent.

Admin: the `POST /v2/admin/semantic/ontologies` upload endpoint (N1c2, shipped) handles admin-uploaded bundles. CHAMEO and SSN/SOSA are bundled as built-ins in the manifest; the MFFD `.ttl` is a built-in bundle seeded at startup.

**Plugin or core?**  
Core — manifest additions use the existing `OntologySeedService` infrastructure. The MFFD `.ttl` is a built-in bundle following the `lumen-inspired.ttl` pattern.

**Effort estimate:** S (3–5 days: manifest entries, SHA-256 pinning, MFFD .ttl authoring, V-migration for fulltext index extension if needed, integration test update)

**Domain impact:** MFFD manufacturing (NDT defect classification, AFP process annotation), EN 9100 §8.7 (machine-readable defect classification), FAIR I2 (controlled vocabulary), PLUTO (SSN/SOSA for telemetry channel formal description)

**Cross-finding hook:**  
- Ontologist Gap 6: "CHAMEO and SSN/SOSA — missing characterisation vocabulary." Direct fix.
- Ontologist surprise: "CHAMEO's absence is conspicuous given the MFFD use case." This is the lowest-effort, highest-ontological-leverage change in the entire proposal set.
- RDM agent I2 gap: "vocabularies follow FAIR principles — annotation predicates not required from controlled vocab." Adding CHAMEO and SSN/SOSA to the manifest makes controlled terms available in the annotation picker; enforcement (requiring CHAMEO terms for NDT DataObjects) is a future metadata-profile step.

---

## Summary Table

| # | Name | Effort | Domain | Plugin/Core | Key Cross-Finding |
|---|---|---|---|---|---|
| 1 | Predecessor-Status Gate | M | MFFD, EN 9100 §8.6, EASA | Core | API Scrutinizer: missing `/v2/` predecessor PATCH |
| 2 | Extended Status + Role-Gated Transitions | M | MFFD, EN 9100 §8.7, EASA | Core | RDM A2 immutability; UX server-side status filter |
| 3 | Material Batch as First-Class Graph Node | S | MFFD, EN 9100 §8.5.2, FAIR | Core (pattern) | Ontologist Gap 5; Strategy real MFFD dataset |
| 4 | Equipment Registry + AAS Calibration | L | MFFD, EN 9100 §7.6, Nadcap | Plugin (AAS) | Ontologist Gap 4; AAS plugin improvement |
| 5 | Ancestor-Chain Endpoint + DAG View | M | MFFD, EN 9100 §7.8.2, EASA | Core | UX CRITICAL 6-predecessor cap; Auditor traversal |
| 6 | File-Level Annotation Bridge | M | MFFD NDT, EN 9100 §8.7 | Core | Ontologist Gap 2; feature matrix L7 |
| 7 | Shop Floor Template Mode | L | MFFD manufacturing | Core | UX IME persona (all HIGH/CRITICAL); Strategy Risk 2 |
| 8 | Bulk Status Transition + Role Gate | M | MFFD, EN 9100 §8.6 | Core | UX CRITICAL row selection; UX HIGH status filter |
| 9 | AI Anomaly → NCR Auto-Raise Bridge | M | MFFD, EN 9100 §10.2, PLUTO | Plugin (quality) | Analytics AI1b→NTF1; own NCR notification gap |
| 10 | CHAMEO + SSN/SOSA Manifest | S | MFFD NDT, FAIR I2, PLUTO | Core | Ontologist Gap 6; RDM I2; lowest effort, high yield |

**Recommended shipping order** (dependency-aware, value-first):
1. **P10 (S)** → immediate: unblocks CHAMEO terms in annotation picker, zero breaking changes
2. **P3 (S)** → immediate: documentation + Template, no code, seeds the material traceability pattern
3. **P2 (M)** → next sprint: status vocabulary + role gate; unlocks P1, P8, P9 semantics
4. **P8 (M)** → next sprint parallel: bulk select + server status filter, high UX ROI
5. **P5 (M)** → next sprint: ancestor-chain endpoint + DAG view, closes auditor CRITICAL finding
6. **P1 (M)** → after P2: predecessor gate depends on new status vocabulary
7. **P6 (M)** → after P10: AnnotatableFile bridge; CHAMEO terms must be available first
8. **P9 (M)** → after P2 + NTF1 ships: NCR auto-raise requires status vocabulary + notification system
9. **P4 (L)** → sprint 3: AAS calibration submodel; requires AAS plugin team bandwidth
10. **P7 (L)** → sprint 3: shop floor template mode; large UI effort, highest shop-floor adoption value
