---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Data Ontologist — Discovery Findings
**Role:** Principal Aerospace Domain Engineer and Data Ontologist
**Date:** 2026-05-21
**Scope:** Deep technical audit of Shepard's data model, semantic layer, and ontology coverage against MFFD AFP and PLUTO satellite use cases

---

## 1. What I Found (actual data in the system)

### 1.1 The Attributes/Annotation Duality — the model's central tension

Every graph node that extends `AbstractDataObject` carries two extension mechanisms:

```java
// AbstractDataObject.java
@Properties(delimiter = "||")
private Map<String, String> attributes;   // open-world freetext, all String values

// BasicEntity.java (parent of AbstractDataObject)
@Relationship(type = Constants.HAS_ANNOTATION)
private List<SemanticAnnotation> annotations;  // IRI-pair only, no numeric value
```

The LUMEN seed exposes exactly how this duality plays out in practice. `seed.py` lines 376–391 show the DataObject attributes for every test run:

```python
attributes = {
    "test_run_id": name,
    "date": scheduled,
    "bench": "P3-Lampoldshausen",
    "propellant": "LOX/LCH4",
    "target_thrust_kN": "25",
    "target_mixture_ratio": "3.4",
    "duration_s": "30",
    "test_engineer": "T. Marek",
    "notes_brief": _short_note_for_run(n),
    "is_fired": "true",
}
```

Meanwhile, `seed.py` lines 808–903 show the semantic annotations: phase boundary IRIs, TestOutcome, CampaignRole, AnomalyType, QualityFlag — rich semantic structure on the lifecycle events. But `bench`, `propellant`, `test_engineer`, `target_thrust_kN` — the operational facts that a DIN EN 9100 auditor needs — are all freetext strings.

The available semantic infrastructure could handle all of these. QUDT is seeded (`om-2` bundle 147 KB, `qudt` 318 KB). PROV-O `prov:Agent` maps directly to `test_engineer`. `lumen:FacilityP3Lampoldshausen` could be a skos:Concept for `bench`. The ontologies are there — the seed does not connect them to the operational attributes.

### 1.2 What V49 actually does (and does not do)

`V49__Bootstrap_internal_semantic_repository.cypher` (25 lines) creates exactly one node:

```cypher
MERGE (r:SemanticRepository {type: 'INTERNAL'})
ON CREATE SET
  r:BasicEntity,
  r.appId     = randomUUID(),
  r.name      = 'Built-in Semantic Store (n10s)',
  r.endpoint  = '',
  r.deleted   = false,
  r.createdAt = timestamp()
```

No ontology terms are seeded by migration. Term ingestion is entirely the responsibility of `OntologySeedService`, which loads `.ttl` files from `resources/ontologies/` at startup time according to `ontologies-manifest.json`. The 14 bundled ontologies are runtime state, not migration state. This is important for upgrade operators: on a fresh install, terms appear after first startup, not after migration completes.

### 1.3 The 14 seeded ontologies — what's in, what's missing

From `ontologies-manifest.json` (as of 2026-05-19):

| Bundle | Size | Coverage |
|---|---|---|
| prov-o | required | Provenance activities, agents, entities |
| obo-relations | required | Basic relation axioms (RO:) |
| dublin-core | dcterms | Bibliographic metadata |
| schema-org | schema: | Web-native metadata |
| foaf | foaf: | Agent/person identity |
| qudt | 318 KB | Quantity kinds, units |
| om-2 | 147 KB | Alternative units (SI-compliant) |
| time | time: | Temporal intervals (OWL Time) |
| geosparql | geo: | Spatial geometry |
| metadata4ing | m4i: | NFDI4Ing engineering process vocabulary |
| simat | 407 KB | DLR materials testing/simulation ontology |
| lumen-inspired | 5 ConceptSchemes | Rocket engine test lifecycle |
| nasa-thesaurus | nasa: | Space mission terminology |
| shepard-experiment | 7 ConceptSchemes | Generic experiment lifecycle |

**Absent (confirmed by checking manifest):**
- CHAMEO (Characterisation Methodology Ontology) — NOT present
- Material OWL (material properties OWL ontology) — NOT present
- CCSDS (space data link protocol standards) — NOT present
- ISO 10303 AP242 STEP spatial concepts — NOT present
- SSN/SOSA (Semantic Sensor Network, W3C) — NOT present

### 1.4 The Timeseries channel model — five fields, no units

`Timeseries.java` carries exactly five fields:

```java
@NotBlank private final String measurement;
@NotBlank private final String device;
@NotBlank private final String location;
@NotBlank private final String symbolicName;
@NotBlank private final String field;
```

No `unit`, no `calibrationRef`, no `sampleRate`, no `uncertainty`. The LUMEN `generate.py` defines 25 `ChannelSpec` objects with unit strings (`"bar"`, `"K"`, `"g_rms"`, `"m/s²"`) but these are Python-only; they never reach Neo4j. The unit information exists in the seed documentation layer and nowhere else in the graph.

QUDT and OM-2 are both seeded. A channel annotation pointing to `unit/Bar` or `qudt:Kelvin` is structurally possible today via the `AnnotatableTimeseries` bridge. The seed does not do this.

### 1.5 Material lot IDs — buried, not queryable

`tr-001-runlog.json` (the StructuredData payload for test run 1) contains:

```json
{
  "lox_lot_id": "LOX-2024-01",
  "lch4_lot_id": "CH4-2024-01",
  ...
}
```

These propellant batch IDs are exactly what DIN EN 9100 traceability requires: which material lot was loaded, sourced from which supplier, with which certificate of conformance. They are buried inside a JSON blob in a StructuredData container. They are not queryable as graph attributes, not linked as semantic annotations, not represented as relationships to a propellant DataObject. A Cypher query cannot answer "which test runs used LOX batch LOX-2024-01?" without a full-text scan of JSON payloads.

### 1.6 Phase vocabulary — lumen vs. shepard-experiment

Two ConceptSchemes cover "phase":

- `lumen:TestPhase` — 7 concepts: Precool, Ignition, RampUp, SteadyState, Throttle, Shutdown, Purge
- `shex:ExperimentPhase` — generic: Preparation, Conditioning, TestRun, Cooldown, PostProcessing

Both are dual-typed: `a owl:Class, skos:ConceptScheme; rdfs:subClassOf prov:Activity`. The seed (seed.py lines 128–171) uses the lumen: IRIs for phase boundary annotations. The cross-link (`lumen:ThrustRampUp skos:related shex:TestRun`) exists in `lumen-inspired.ttl`. The resolution mechanism exists — it is just incomplete. An MFFD process phase vocabulary (fiber placement, weld, inspect, verify) would need a third ConceptScheme with cross-links to shex:ExperimentPhase.

### 1.7 Annotation coverage asymmetry

From `aidocs/semantics/14-semantic-improvements.md`:

| Node type | Annotatable? | Mechanism |
|---|---|---|
| Collection | Yes | BasicEntity.HAS_ANNOTATION |
| DataObject | Yes | BasicEntity.HAS_ANNOTATION |
| All container types | Yes | BasicEntity.HAS_ANNOTATION |
| Individual Timeseries channels | Yes | AnnotatableTimeseries bridge |
| Individual files in FileBundle | No | No bridge exists |
| Individual StructuredData documents | No | No bridge exists |
| Individual SpatialData records | No | No bridge exists |

An AFP fiber run produces one file per robot-arm pass. Annotating file 47 of 200 in a FileBundle with a defect class (CHAMEO:LocalPorosityDefect) is not possible today.

### 1.8 SemanticAnnotation label drift risk

`SemanticAnnotation.java` fields include `propertyName` and `valueName` — snapshot strings captured at annotation time. Identity is enforced by the IRI pair (`propertyIRI`, `valueIRI`). Search uses the string form `propertyName::valueName`. If a term's `skos:prefLabel` is updated in the `.ttl` file and the file is re-seeded, existing annotations retain their stale snapshot labels, silently breaking search without any error.

---

## 2. Entity Blueprint

### 2.1 MFFD AFP Fuselage Shell

```
Collection: MFFD-Project-Augsburg-2024
│
├── DataObject: MFFD-UP-Shell-001  [Parent/Child root]
│   ├── type: ShellAssembly (annotation: shex:ManufacturingProcess → MFFD:InSituConsolidation)
│   ├── attributes: part_number="MFFD-UP-001", material="CF/LMPAEK", serial="SN-2024-001"
│   │
│   ├── DataObject: SkinPanel-001  [Parent/Child of Shell]
│   │   ├── type: AFP_Layup_Run (annotation)
│   │   ├── Predecessor: MaterialBatch-CF-LMPAEK-2024-07
│   │   ├── Successor: SkinPanel-001-PostWeld
│   │   ├── attributes: ply_count="47", thickness_mm="3.2", layup_angle="0/45/90"
│   │   ├── Container: Timeseries/AFP-Run-001-Sensors
│   │   │   └── channels: laser_power_W, roller_force_N, feed_rate_mm_s,
│   │   │                  tcp_temp_K, in_situ_void_pct, gap_mm, overlap_mm
│   │   │   NOTE: unit annotation via AnnotatableTimeseries possible, not done
│   │   ├── Container: FileBundle/AFP-Run-001-Geometry
│   │   │   └── files: layup_path_ply_001.stp, layup_path_ply_002.stp, ...
│   │   │   NOTE: individual file annotation NOT supported — defect per ply unlocatable
│   │   └── Container: StructuredData/AFP-Run-001-Runlog
│   │       └── fiber_lot_id: "CF-LMPAEK-2024-07-B3" ← buried, not queryable
│   │
│   ├── DataObject: Stringer-UW-007  [Parent/Child of Shell]
│   │   ├── Predecessor: SkinPanel-001 (after layup complete)
│   │   ├── type: UltrasonicWeld (annotation: shex:ManufacturingProcess → MFFD:ContinuousUltrasonicWelding)
│   │   ├── Container: Timeseries/UW-007-Sensors
│   │   │   └── channels: horn_freq_Hz, horn_amplitude_um, weld_pressure_bar, feed_rate_mm_s
│   │   └── Container: FileBundle/UW-007-NDT-Scans
│   │       NOTE: cannot annotate individual scan files with defect class
│   │
│   └── DataObject: NDT-Inspection-001  [Successor of Stringer-UW-007]
│       ├── type: InspectionRun (annotation: shex:InspectionMethod)
│       ├── Successor: MaterialReviewBoard-001 (if reject/repair)
│       └── Container: FileBundle/NDT-Scans
│           NOTE: CHAMEO defect classes not available — not seeded

Separate DataObject lineage (predecessor/successor chain):
MaterialBatch-CF-LMPAEK-2024-07 → SkinPanel-001 → SkinPanel-001-PostWeld → NDT-001 → Certified-Panel-001
```

**Key topology decisions:**
- Parent/Child = structural assembly hierarchy (Shell contains Panels contains Stringers)
- Predecessor/Successor = process chain thread (Material → Layup → Weld → NDT → Cert)
- Equipment calibration (AFP head, roller force gauge) should be a separate DataObject linked via `shex:RelatedEquipment` annotation or a custom relationship — not an attribute of the process DataObject
- Material certificates (Tg, Tm, lot conformance) belong in a MaterialBatch DataObject's StructuredData container, linked upstream via Predecessor. The batch ID should also appear as a queryable attribute `fiber_lot_id` on the process DataObject for fast Cypher traversal

### 2.2 PLUTO Satellite (hypothetical, used to expose model gaps)

```
Collection: PLUTO-Mission-2024
│
├── DataObject: PLUTO-Spacecraft-001  [BOM root, structural]
│   ├── DataObject: PLUTO-TCS-001  [Thermal Control Subsystem]
│   │   ├── DataObject: PLUTO-Heater-H01  [Individual heater unit]
│   │   └── ...
│   └── DataObject: PLUTO-Propulsion-001
│
├── DataObject: LEOP-Commissioning-Phase  [mission phase, successor chain]
│   ├── Predecessor: LaunchVehicleRelease
│   ├── Successor: NominalOps-Phase-001
│   ├── type: annotation: pluto:MissionPhase → pluto:LEOP  [VOCABULARY MISSING]
│   └── Container: Timeseries/LEOP-Telemetry-All-Channels
│
├── DataObject: TC-Sequence-2024-07-15T09:30:00  [a specific commanding session]
│   ├── attributes: tc_apid="0x12", sequence_count="4471", uplink_band="S-Band"
│   └── Container: StructuredData/TC-Log-2024-07-15
│       └── individual TCs: {tc_id, apid, param_value, timestamp}
│           NOTE: CANNOT link individual TC to individual TM interval
│
└── DataObject: TM-Anomaly-2024-07-15T09:31:22  [response anomaly]
    ├── Predecessor: TC-Sequence-2024-07-15T09:30:00  ← coarse-grain link
    ├── type: annotation: shex:QualityFlag → QualityFlag:Suspect
    └── Container: Timeseries/TM-Anomaly-Window
        NOTE: TimeseriesAnnotation on the anomaly interval possible,
              but no typed edge from specific TC to specific TM window
```

**PLUTO-specific gap:** The question "which uplink command caused which telemetry response" requires an edge between a row in a StructuredData document (the TC) and a time interval in TimescaleDB (the TM window). No such edge type exists. The coarsest available link is Predecessor/Successor between DataObjects representing commanding sessions and anomaly investigations. Fine-grained command-response causality requires either: (a) a new edge type `CAUSED_BY` from `TimeseriesAnnotation` to `StructuredDataEntry`, or (b) embedding the `tc_id` as a metadata field on the `TimeseriesAnnotation`.

---

## 3. Annotation Playbook

### 3.1 MFFD AFP — mandatory vs. optional semantic annotations

**Mandatory (DIN EN 9100 traceability requires these to be queryable):**

| Field | Current storage | Recommended | Controlled vocabulary |
|---|---|---|---|
| Manufacturing process type | attributes["process"] freetext | SemanticAnnotation shex:ManufacturingProcess → MFFD:AFP_InSituConsolidation | New MFFD ConceptScheme |
| Material batch ID | Buried in StructuredData JSON | attributes["fiber_lot_id"] + SemanticAnnotation to MaterialBatch DataObject | Internal reference |
| Equipment serial + calibration date | Not stored | Separate Equipment DataObject, linked via annotation | New EquipmentRegistry concept |
| Inspection outcome | Not stored | SemanticAnnotation shex:InspectionMethod + QualityFlag | shex: (already seeded) |
| Defect classification | Not possible per file | CHAMEO:DefectType (not yet seeded) on file-level annotation (not yet possible) | CHAMEO (add to manifest) |
| Part number / serial | attributes["part_number"] freetext | attributes["part_number"] + annotation to STEP/AP242 entity IRI | ISO AP242 (not seeded) |

**Optional (enrich for discovery, not required for audit):**

| Field | Recommended |
|---|---|
| Fiber orientation per ply | attributes["layup_angle_deg"] freetext |
| Process temperature profile | Timeseries annotation: qudt:ThermodynamicTemperature |
| Robot arm ID | attributes["robot_arm_id"] |
| Shift / operator | SemanticAnnotation prov:wasAssociatedWith prov:Agent |

### 3.2 LUMEN / rocket engine — annotation upgrade path

The LUMEN seed's semantic annotations cover lifecycle events correctly. The operational attributes are the gap. Recommended additions (all use already-seeded ontologies):

| Attribute (current freetext) | Recommended semantic annotation | Ontology IRI |
|---|---|---|
| bench: "P3-Lampoldshausen" | SemanticAnnotation m4i:hasLocation → lumen:FacilityP3Lampoldshausen | metadata4ing + lumen: |
| propellant: "LOX/LCH4" | SemanticAnnotation m4i:usesMaterial → simat:LOX + simat:LCH4 | SiMaT |
| test_engineer: "T. Marek" | SemanticAnnotation prov:wasAssociatedWith → prov:Agent (foaf:Person) | PROV-O + FOAF |
| target_thrust_kN: "25" | Cannot — SemanticAnnotation has no numeric field | Model gap |
| lox_lot_id, lch4_lot_id | Move from JSON to DataObject.attributes; ideally a Predecessor link | Internal |

### 3.3 Controlled vocabulary recommendations

1. **Facility names** — add `lumen:Facility` ConceptScheme with `P3-Lampoldshausen`, `P4.1-Lampoldshausen`, `P8-Lampoldshausen` as skos:Concepts. Currently only in template ENUM (seed.py lines 1042–1092), which is controlled at template layer but not semantic layer.

2. **Propellant combinations** — SiMaT is the right vocabulary. Check whether SiMaT already has `LOX` and `LCH4` as named individuals. If not, add a `lumen:Propellant` ConceptScheme bridged to SiMaT via `skos:exactMatch`.

3. **Process types** — add an MFFD-specific ConceptScheme: `MFFD:ManufacturingProcess` with concepts for AFP_InSituConsolidation, ContinuousUltrasonicWelding, ContinuousResistanceWelding, StudWelding. Cross-link each via `skos:related` to `shex:ManufacturingProcess`.

4. **Defect types** — add CHAMEO to the manifest. CHAMEO provides a standard vocabulary for characterisation methods and defect classes. Without it, defect annotation uses freetext and is not interoperable.

---

## 4. Vocabulary Conflicts and Resolution

### 4.1 Phase concept collision

Three domains, three "phase" ConceptSchemes are needed:

| Domain | ConceptScheme | Representative concepts |
|---|---|---|
| Rocket engine (seeded) | lumen:TestPhase | Precool, Ignition, RampUp, SteadyState, Throttle, Shutdown, Purge |
| Generic experiment (seeded) | shex:ExperimentPhase | Preparation, Conditioning, TestRun, Cooldown, PostProcessing |
| MFFD manufacturing (missing) | MFFD:ProcessPhase | MaterialPrep, FiberLayup, InSituWeld, NDTInspection, Certification |
| PLUTO mission (missing) | PLUTO:MissionPhase | LEOP, Commissioning, NominalOps, SafeMode, DeOrbit |

**Resolution pattern (already established in lumen-inspired.ttl):** Cross-link via `skos:related`:
```turtle
mffd:FiberLayup skos:related shex:TestRun .
mffd:NDTInspection skos:related shex:PostProcessing .
pluto:NominalOps skos:related shex:TestRun .
```

This preserves domain specificity while enabling cross-domain discovery via semantic reasoning.

### 4.2 "Phase" as class vs. concept vs. interval

All three ConceptSchemes use the dual-typing pattern:
```turtle
lumen:TestPhase a owl:Class, skos:ConceptScheme ;
    rdfs:subClassOf prov:Activity .
```

Individual phase concepts (e.g., `lumen:SteadyState`) are simultaneously `owl:NamedIndividual` and `skos:Concept`. This is semantically coherent (a named individual of class TestPhase, also browsable as a SKOS concept), but it means OWL reasoners and SKOS browsers give different views of the same IRI. Document this explicitly in the ontology `rdfs:comment` for each ConceptScheme to prevent future confusion.

### 4.3 Unit quantity conflicts — QUDT vs. OM-2

Both `qudt` and `om-2` are seeded. Both define SI units. A channel annotated with `qudt:Bar` and another with `om-2:bar` are describing the same unit but their IRIs differ, making automated equivalence reasoning necessary. **Recommendation:** establish a house rule that QUDT is the primary unit vocabulary and OM-2 is secondary (used only when QUDT lacks coverage). Document this in the LUMEN seed's `lumen-inspired.ttl` header as a `skos:scopeNote`.

### 4.4 SiMaT vs. Material OWL

SiMaT (seeded, 407 KB) is DLR-authored and covers materials testing and simulation. Material OWL (not seeded) covers material class hierarchy. For MFFD, the fiber material `CF/LMPAEK` needs: (a) a material class (Material OWL domain), (b) physical properties like Tg, Tm (SiMaT domain). Adding Material OWL and bridging via `owl:sameAs` or `skos:exactMatch` where concepts overlap is the right path. Absent Material OWL, freetext strings in `attributes["material"]` are the only option.

---

## 5. Model Gaps

### Gap 1: Numeric measurement cannot be a semantic annotation value

`SemanticAnnotation.java` carries `propertyIRI` and `valueIRI` — both IRIs. There is no `numericValue`, no `unit`, no `datatype`. A channel reading `target_thrust_kN = 25` with unit `qudt:KiloNewton` cannot be expressed as a SemanticAnnotation. The value `"25"` is a string in `attributes`. This means:

- Physical quantities with units are not machine-readable via the semantic layer
- Range queries ("find all test runs with target thrust > 20 kN") must query `attributes["target_thrust_kN"]` as a string, relying on client-side numeric parsing
- No inferencing is possible: a reasoner cannot conclude that 25 kN is close to the facility's rated thrust of 30 kN

**Structural fix:** Extend `SemanticAnnotation` with an optional `numericValue` (Double) + `unitIRI` (String) triple-pattern, making it an RDF Literal-bearing annotation rather than a pure IRI-pair. This is a model change; it requires a Neo4j migration and a frontend update.

### Gap 2: Sub-container granularity — files and documents not annotatable

The `AnnotatableTimeseries` bridge enables semantic annotation on individual Timeseries channels (Neo4j → TimescaleDB bridge). No equivalent bridge exists for:
- Individual files within a FileBundle
- Individual rows/documents within a StructuredData container
- Individual records within a SpatialData container

For MFFD, this means: 200 AFP layup files (one per ply), and the defect detected on ply 47 cannot be annotated with `CHAMEO:LocalPorosityDefect` on that specific file. The annotation can only be placed at the FileBundle or DataObject level, losing spatial specificity.

**Structural fix:** A `AnnotatableFile` bridge node (analogous to `AnnotatableTimeseries`) with a foreign key to the file's GridFS ObjectId. Requires extending `FileReference` to carry the annotation relationship.

### Gap 3: Causal edge between commands and telemetry

PLUTO exposes a gap that MFFD also has in a weaker form. The model has DataObject-level Predecessor/Successor, but no fine-grained causal edge between:
- A specific row in a StructuredData document (a telecommand)
- A specific time interval in a TimescaleDB series (the resulting telemetry response)

This is the "which command caused which response" question. The current model can answer it at DataObject granularity (e.g., "TC session X preceded anomaly investigation Y") but not at record granularity.

**Structural fix:** Either (a) a new graph edge type `CAUSED_BY` from `TimeseriesAnnotation` to a bridge node that references a StructuredData row ID, or (b) embed `causal_tc_id` as a metadata field on `TimeseriesAnnotation`. Option (b) is lower friction but loses graph-query expressibility.

### Gap 4: Equipment calibration state — no native home

An AFP head's calibration state ("calibrated 2026-01-15, valid until 2026-07-15, certificate AFP-CAL-2026-01") is neither a DataObject attribute (too structured) nor a SemanticAnnotation (no date range). The correct model is a dedicated `Equipment` DataObject linked to process DataObjects via a custom annotation or a `USED_EQUIPMENT` relationship — but this relationship type does not exist. The alternative (freetext `attributes["equipment_calibration_date"]`) breaks audit trails because there is no machine-readable validity period.

**Structural fix:** An `EquipmentRegistry` pattern: Equipment DataObjects with StructuredData containers holding calibration records. Link to process DataObjects via `prov:used` annotation (PROV-O, already seeded). The `time:Interval` ontology (also seeded) can express validity windows.

### Gap 5: Material batch — buried in JSON, not graph-traversable

Propellant lot IDs (`lox_lot_id: "LOX-2024-01"`) are stored in `StructuredData` JSON blobs. They are not attributes, not annotations, not relationships. A graph query "find all test runs that used LOX batch LOX-2024-01" requires a full-text scan of JSON payloads across all test run StructuredData containers — not a graph traversal.

**Structural fix:** Promote material lot IDs to DataObject.attributes (immediately queryable by Cypher) AND create a Predecessor link from each process DataObject to the corresponding MaterialBatch DataObject. The dual representation (attribute for fast lookup, relationship for graph traversal) is the pattern.

### Gap 6: CHAMEO and SSN/SOSA — missing characterisation vocabulary

Defect characterisation (NDT results, material characterisation methods) has no controlled vocabulary. CHAMEO (Characterisation Methodology Ontology) is the standard. SSN/SOSA (Semantic Sensor Network Ontology, W3C) defines sensor capabilities and observations formally. Neither is in the manifest. Without CHAMEO, defect classification is freetext. Without SSN/SOSA, a Timeseries channel's calibrated range, accuracy, and measurement method cannot be expressed as ontology-linked metadata.

---

## 6. Opportunities

### Opportunity 1: Numeric semantic annotations (highest ontological leverage)

Extend `SemanticAnnotation` to carry an optional `numericValue` (Double) + `unitIRI` (String). This single change converts physical measurements from opaque strings into machine-readable, unit-aware, queryable quantities.

Immediate impact:
- `target_thrust_kN = 25` becomes `(m4i:hasTargetValue, qudt:KiloNewton, 25.0)` — range-queryable in Cypher
- Material batch Tg, Tm become annotatable on the MaterialBatch DataObject
- Calibration uncertainty (±0.5%) becomes expressible
- Cross-domain queries: "find all test runs within 10% of rated thrust" become possible without client-side parsing

This does not break existing annotations. Existing IRI-pair annotations retain their current structure. The numeric triple is additive.

### Opportunity 2: Material batch as first-class graph node (highest traceability leverage)

Promote material batches from JSON blobs to DataObjects. Each batch (fiber roll, propellant lot, welding wire spool) becomes a DataObject with:
- `attributes["lot_id"]`, `attributes["supplier"]`, `attributes["conformance_cert_id"]`
- StructuredData container: the full certificate of conformance (PDF or JSON)
- Predecessor/Successor links to all process DataObjects that consumed it

This makes DIN EN 9100 traceability queries a graph traversal, not a JSON scan:
```cypher
MATCH (mat:DataObject {attributes: {lot_id: 'LOX-2024-01'}})-[:HAS_SUCCESSOR*]->(proc:DataObject)
RETURN proc.name
```

The pattern already exists in the model — every primitive needed is already there. It is a seed/usage pattern change, not a model change.

### Opportunity 3: Add CHAMEO and SSN/SOSA to the ontology manifest

Three bundles to add:
1. **CHAMEO** — defect and characterisation vocabulary for NDT and material analysis
2. **SSN/SOSA** — W3C sensor network ontology; enables `sosa:Sensor`, `sosa:Observation`, `sosa:FeatureOfInterest` as annotation targets
3. **IEC 61360 / ECLASS** — component and property classification for equipment (alternatively, use the existing SiMaT extension hooks)

These are additive manifest entries. `OntologySeedService` handles them automatically. No migration needed. The frontend `AddAnnotationDialog` gains browsable terms for defects and sensor metadata immediately.

**Side effect:** SSN/SOSA enables the `AnnotatableTimeseries` bridge to express a channel as a formal `sosa:Sensor` with `sosa:observes` pointing to a `sosa:FeatureOfInterest`. This is the semantic grounding that links a sensor channel to its physical quantity — currently absent.

---

## 7. Ideas

**Idea A: "Lot lineage" query panel in the frontend**
A dedicated UI pane that accepts a material lot ID and returns every process DataObject that consumed it, ordered by time. Implemented as a Cypher traversal on the attributes map (once lot IDs are promoted from JSON blobs). Useful for recall scenarios ("batch contamination — which assemblies are affected?").

**Idea B: Channel unit annotation as a mandatory field in the channel creation form**
Rather than a post-hoc annotation, make unit selection a required step when creating a Timeseries channel. The frontend `AddChannelDialog` could present a QUDT unit browser. The selected IRI creates an `AnnotatableTimeseries` annotation automatically. Zero extra steps for the user — one IRI stored per channel, forever queryable.

**Idea C: Equipment DataObject template**
A built-in Template DSL definition for `EquipmentItem` with mandatory fields: serial_number, manufacturer, model, calibration_valid_until, calibration_cert_id. Every process DataObject that uses equipment stores a semantic annotation `prov:used → [Equipment DataObject appId]`. The template enforces the fields; the Predecessor link enforces the provenance trail. DIN EN 9100 Section 7.1.5 (measuring equipment) is satisfied.

**Idea D: Cross-ConceptScheme search mode in AddAnnotationDialog**
Currently, a user browses one ConceptScheme at a time. Add a "related concepts" sidebar: when the user selects `lumen:SteadyState`, the sidebar shows `shex:TestRun` (because `skos:related` exists in the TTL). This turns the skos:related cross-links from invisible graph data into a discovery affordance. No backend change — pure frontend SPARQL query against the internal n10s repo.

**Idea E: Snapshot label refresh job**
`SemanticAnnotation.propertyName` and `.valueName` are snapshots at annotation time. A background job that periodically re-fetches `skos:prefLabel` from the internal n10s repo and updates stale snapshot strings would prevent search drift without requiring re-annotation. This is an opt-in admin operation, not an automatic mutation.

---

## 8. Real-World Impact

**DIN EN 9100 audit readiness (MFFD)**

Today: An auditor asks "show me the complete traceability chain for fuselage panel SN-2024-001, from raw material through all process steps." The answer is: manually navigate DataObject predecessor/successor graph in the UI, open each StructuredData JSON blob to find lot IDs, check each lot ID against a separate certificate management system. The graph is correct but the material batch data is not in it.

After Opportunity 2 (material batch as graph node): The same question becomes a Cypher traversal. The auditor gets a lineage graph that shows: CF/LMPAEK batch B3 → SkinPanel-001-LayupRun-007 → PostWeld-Panel-001 → NDT-OK → Certified. Machine-readable, exportable, immutable (Predecessor/Successor edges are not mutable post-creation in Shepard).

**FAIR compliance (cross-domain discovery)**

Today: A researcher looking for "LOX/LCH4 combustion data" searches Shepard's text index and finds 15 LUMEN test runs — because the string "LOX/LCH4" appears in the freetext attribute. A researcher using a different system searching by the SiMaT IRI for liquid oxygen finds nothing.

After Opportunity 1 + Opportunity 3 (numeric annotations + SSN/SOSA + SiMaT IRIs on propellant): The propellant annotation carries the SiMaT IRI. A federated query across Shepard and a Helmholtz HMC catalogue node using the same IRI finds both datasets. FAIR's Interoperable principle (I1: use a formal, broadly applicable knowledge representation language) is satisfied.

**Anomaly investigation speed (PLUTO / LUMEN)**

Today: When an anomaly is detected in a LUMEN test run (TR-004, `vib_fuel_pump` spike), the investigation path is: open TR-004 DataObject, read anomaly attributes (freetext), navigate to the Timeseries container, filter by the anomaly time window in the UI. The Predecessor/Successor chain connects TR-004 to the anomaly investigation DataObject. The specific sensor channel and time range are identified by the engineer from memory.

After Gap 3 fix (causal edge from TimeseriesAnnotation to command/event): The anomaly TimeseriesAnnotation on the `vib_fuel_pump` channel carries a reference to the triggering event (e.g., the throttle command). A single graph query returns: anomaly interval → causing command → command sequence → operator session. Investigation time drops from hours of cross-referencing to a single query.

---

## 9. What Surprised Me

**The ontology infrastructure is dramatically ahead of the seed data.** QUDT, PROV-O, metadata4ing, SiMaT, OWL Time — all seeded. The vocabulary to express units, agents, processes, material properties, and temporal intervals is there. The LUMEN seed (the primary showcase demonstrating Shepard's capabilities) puts propellant, thrust target, test engineer, and facility into freetext strings. The semantic layer is ready; the usage patterns are not. This is not a model weakness — it is a showcase weakness. The gap is smaller than it looks.

**V49 does nothing to the ontology graph.** Until I read the 25-line migration file, I expected it to seed some base terms. It creates exactly one Neo4j node. The entire term graph is runtime state created by `OntologySeedService`. This means a database restore without re-running the application will result in a `SemanticRepository` node with no term content — the n10s graph is gone. Operators need to know that the semantic term graph is not captured in Neo4j backup alone if the `OntologySeedService` auto-seed is disabled.

**The AnnotatableTimeseries bridge exists but the lumen seed never uses it for units.** The bridge was built to enable semantic annotation of individual channels. The LUMEN seed's 25 channels have known, well-defined units (bar, K, g_rms). QUDT is seeded. The connection is never made. The bridge is infrastructure awaiting a usage convention.

**`attributes["propellant"] = "LOX/LCH4"` is repeated 15 times.** Every test run in the LUMEN campaign uses the same propellant combination. If it were a semantic annotation pointing to a shared IRI, it would be shared. As a freetext string, it is 15 independent copies. One typo in the seed generator creates a run that no "find all LOX/LCH4 tests" query would find. The redundancy is not just philosophical — it is a data integrity risk.

**CHAMEO's absence is conspicuous given the MFFD use case.** The MFFD is presented as the primary demonstrator. MFFD's primary quality concern is defect detection (porosity, delamination, fiber misalignment in AFP). CHAMEO is the W3C/NFDI-endorsed vocabulary for characterisation methods and defects. Not having it seeded means the most important semantic assertion for MFFD inspection data — the defect classification — has no controlled vocabulary. The defect annotation capability and the defect vocabulary are both absent; they should land together.

**The dual-typing OWL class + SKOS ConceptScheme pattern is elegant but underdocumented.** `lumen:TestPhase a owl:Class, skos:ConceptScheme; rdfs:subClassOf prov:Activity` is semantically sophisticated — it makes phases browsable as SKOS concepts while allowing OWL inferencing that phase instances are prov:Activities. But this pattern is not documented anywhere in the aidocs. A future ontology author extending the vocabulary might abandon the dual-typing without realizing what they are giving up.

---

*Agent: Principal Aerospace Domain Engineer and Data Ontologist | Generated: 2026-05-21*
*Primary sources: lumen-showcase/seed.py, backend entity classes, ontologies-manifest.json, V49 migration, aidocs/semantics/*
