# EASA Data Management + Learning Assurance — deep dive (Shepard scope)

*Author: regulatory analyst (Claude agent), 2026-05-21.
Companion to `easa-ai-regulatory-positioning.md` (broader EASA framework /
Article 50 / strategic positioning) and `easa-ai-compliance.md` (objective-by-
objective gap analysis). This document narrows scope to **two building
blocks** — Data Management (DM) and Learning Assurance Data-track
(DA / LA-data) — and goes deeper on **what an auditor sees**, not what
EASA writes.*

---

## TL;DR (5 sentences)

EASA Concept Paper Issue 02 puts the heaviest evidence burden on the
Data Management family (DM-01..DM-09) and the data-track Learning
Assurance objectives (DA-01..DA-07, plus the DQR-anchored DM ones):
roughly half of all AL1/AL2 objectives are satisfied by *documented
data artefacts*, not by clever algorithms. Shepard's v2 surface
(appId-stable identifiers, Predecessor/Successor lineage edges,
PROV-O via PROV1a, SHACL-validated annotations from TPL1, importer
manifest at `/v2/import`) maps cleanly to DA-01..DA-05 (plans,
requirements, ODD), to DM-01/DM-06/DM-09 (sources, transformations,
preprocessing), and most decisively to the **independence DQR**
(DM-05 / the DM-06 split) because appId-keyed dataset entities make
"train, val and test never overlapped in lineage" a graph query, not
a paperwork claim. The single gap that matters most is the absence
of a **first-class DQR record type** — DQRs are currently free-text
annotations, where they should be a typed `:StructuredData` shape
with mandatory fields (relevance, completeness criterion,
representativeness criterion, independence criterion, accuracy
tolerance, integrity-assurance level). REBAR-style functional
validation outputs sit naturally as `fair2r:AuditPass` Activities
attached to the model-under-test DataObject; the Regulatory Evidence
Pack proposal in §4 is the SHACL shape that bundles all
DM+DA artefacts for a single AL into a single export.

---

## Methodology note

EASA Concept Paper Issue 02 PDFs are heavily compressed and
WebFetch's HTML-extraction layer returns binary for every mirror I
tried (the EASA copy, the bainessimmons copy, the Horizon Europe NCP
portal copy, the MLEAP D2/D4 PDFs). Verbatim paragraph extraction
was therefore impossible in this research session. Objective IDs and
operational meanings used in this document come from three
secondary sources I could read cleanly:

1. `aidocs/agent-findings/easa-ai-regulatory-positioning.md` (the
   broader companion doc) — cites DA-01..DA-09, DM-01..DM-08,
   LM-01..LM-16, IMP-01..IMP-12, EXP-01..EXP-09, HF-01..HF-11 with
   page references to the 283-page Issue 02 PDF.
2. `aidocs/agent-findings/easa-ai-compliance.md` — enumerates DM-01..
   DM-09 and DA-01..DA-07 with one-line summaries and AL targets.
3. arxiv 2409.08666 ("Towards certifiable AI in aviation"; HTML
   render readable) — confirmed the W-shape building blocks and
   the operational meaning of completeness/representativeness/ODD.
   Also a search snippet citing DM-08 and LM-16 (paper title:
   "On the Feasibility of EASA Learning Assurance Objectives",
   ERTS 2024, de Grancey et al.).

Each row in the tables below is labelled with operational meaning,
not pretended-verbatim wording. Where the broader doc cites a page
number to the Issue 02 PDF, I carry it through; where it doesn't,
I mark `(p. n.a.)`.

**REBAR framing.** DLR's REBAR project is referenced as an internal
project Shepard should ingest evidence from, not as a public
framework Shepard aligns to. REBAR's public footprint is thin (no
project page on dlr.de; one unrelated paper on elib.dlr.de). The
"Shepard hosts REBAR's outputs" pattern in §3 is design intent, not
shipped capability — labelled clearly.

---

## 1. Data Management — objective-by-objective mapping

EASA's DM block (Issue 02 §C.3.1) covers everything from raw data
collection through pre-processing and dataset split. The block has
**nine numbered objectives** (DM-01..DM-09) plus the DQR thread
that runs through DA-04 → DM-02 → DM-06 → DM-07. Every objective
expects a *documented artefact*. The auditor opens that artefact and
checks the trace.

| EASA objective | Operational meaning | Shepard capability today | Evidence path the auditor would follow |
|---|---|---|---|
| **DM-01** (AL1) | Define ODD parameters for the AI/ML constituent's data inputs | `:DataObject` (kind=`odd_spec`) + `:StructuredData`; no enforced template | `GET /v2/data-objects/{appId}` returns the ODD record; auditor checks each ODD parameter is keyed and that downstream training datasets reference this appId in their lineage |
| **DM-02** (AL1) | Capture Data Quality Requirements (DQRs) for every dataset role | **Gap** — DQRs are free-text annotations today; no `:DQR` shape | (Future) `GET /v2/data-objects/{appId}/dqr-record` resolving a SHACL-validated `:DQR` shape with required fields: relevance, completeness criterion, representativeness criterion, independence criterion, accuracy bound, integrity level. Without this shape, the auditor reads prose and forms a subjective opinion — failing the "documented" bar |
| **DM-03** (AL1) | Capture requirements on pre-processing and feature engineering | `:DataObject` (kind=`preprocessing_spec`) + Predecessor edge to the input dataset | Auditor traverses `(:DataObject{kind:"dataset"})<-[:PREDECESSOR_OF]-(:DataObject{kind:"preprocessing_spec"})` to find the spec; then to the transformed dataset that cites it |
| **DM-04** (AL2) | Validate data annotations / labels | Annotation system (SemanticAnnotation, post-TPL1 SHACL templates) | `GET /v2/data-objects/{appId}/annotations` + `GET /v2/semantic/shape-validation/{annotationId}` returns the SHACL validation report. **Strong fit once TPL1 ships** |
| **DM-05** (AL1) | Ensure correctness and completeness of collected data | Validation hook (planned via SHACL); appId-keyed timestamps; integrity hash on file containers | Three-step: (a) checksum on each `:FileContainer`/`:TimeseriesContainer`; (b) SHACL validation report on each `:StructuredData`; (c) coverage report comparing observed vs ODD parameter space (gap: not yet generated) |
| **DM-06** (AL1) | Identify data sources and collect per DQRs | Container ingest; every record stamped with appId at ingest time | `GET /v2/containers/{appId}/provenance` returns the ingest `:Activity` with source URI, importer plan-seal (V2 `ImportV2Rest`), commit ID. Auditor cross-checks against DQR origin clause. **Best alignment in the DM block today** |
| **DM-07** (AL2) | Ensure data quality post-collection and labelling | Same hook as DM-05, applied to the labelled corpus | Same path; the auditor wants two reports — pre-label and post-label — at distinct appIds with a PROV-O `wasDerivedFrom` edge between them |
| **DM-08** (AL2) | Define data preparation operations (format conversion, feature extraction) | `:DataObject` (kind=`preparation_spec`) | Same traversal as DM-03; the prepared dataset's `:DataObject` cites the spec via Predecessor |
| **DM-09** (AL2) | Define and document pre-processing operations | Same shape as DM-08 — the two objectives differ in *when* (pre-collection vs post-collection) but the artefact shape is identical in Shepard | Same |

### 1.1 Data lineage requirements

Lineage is the **architectural fit** that justifies Shepard's
existence in this conversation. EASA expects "the ability to
determine the origin of the data" (DA-04 DQR clause). Shepard's
graph answers this with two complementary edges:

- `:DataObject -[:PREDECESSOR_OF]-> :DataObject` — the
  research-domain lineage (engineering interpretation: "this
  dataset was produced by that process step").
- PROV-O activities via PROV1a — the *system* lineage (every
  ingest, every transformation creates a `:Activity` with
  `prov:used` / `prov:wasGeneratedBy` edges).

An auditor verifying DA-04 lineage runs:

```cypher
MATCH (final:DataObject {appId:$datasetId})
  -[:PREDECESSOR_OF*1..]->(ancestor:DataObject)
RETURN ancestor.appId, ancestor.kind, ancestor.createdAt
```

Plus the PROV-O cross-check via `GET /v2/provenance/{appId}` (handled
by `ProvenanceRest`). Two views, one source of truth.

### 1.2 Data quality & verification requirements

DM-04, DM-05, DM-07 all reduce to "show me the validation report."
Shepard's planned SHACL pipeline (TPL1 — `aidocs/semantics/95`) is
exactly the right shape: a SHACL shape per dataset role, validation
runs as `:Activity` nodes, the report as a `:StructuredData` output.
**The miss** is that nobody is currently writing the SHACL shapes
that encode DQR clauses. Closing that gap is one short sprint per
dataset role.

### 1.3 Data versioning requirements

EASA does not use the phrase "versioning" — it uses "configuration
control" (DA-08 in some Issue 02 renderings). Operationally: a
dataset that the model was trained on must be retrievable, bit-for-
bit, at audit time. Shepard's appId is **immutable by design** (the
L2 migration explicitly makes this so), file containers carry
content hashes, and timeseries data is append-only in TimescaleDB.
This is the cleanest alignment in the whole document. The evidence
path: `GET /v2/data-objects/{appId}` returns the dataset; the
`:FileContainer` reports its hash; the auditor recomputes.

### 1.4 Independent data requirements

The **independence DQR** (woven through DM-02 and DM-06): the train,
validation and test sets must not share members or near-members.
This is the EASA objective most often ducked in practice. Shepard
turns it into a graph query:

```cypher
MATCH (train:Collection {appId:$trainId})-[:CONTAINS*]->(d:DataObject)
MATCH (val:Collection {appId:$valId})-[:CONTAINS*]->(d2:DataObject)
WHERE d.sourceArtifact = d2.sourceArtifact
RETURN count(d)
```

Zero rows = clean. Non-zero rows = overlap, contamination, fail.
This is uniquely valuable: most ML platforms can't run that query
because dataset membership is encoded in CSV row-IDs rather than
first-class entities. Shepard can. **What Shepard does not yet do**
is automate the check at split time — an admin can run it, but
nothing forces it. The fix is one CI-style hook on the dataset-split
`:Activity`.

### 1.5 Storage / retention / accessibility requirements

DA-04 explicitly cites "integrity" and "timeliness" — operationally
that means *retention*. The Storage Management design (SM1, per
memory) ties this together: storage policies, orphan grace periods,
provenance-aware retention rules. For DM evidence, the operator
policy must be "retain forever, never garbage-collect Collections
flagged as `regulatory-evidence`." That tag does not exist yet
(gap). The endpoint to add is straightforward: a boolean
`:Collection.retainForRegulatoryEvidence` checked by the storage
collector.

---

## 2. Learning Assurance — objective-by-objective mapping

The DA block (Issue 02 §C.3.1.1) sits one layer above DM and covers
the *programme-level* artefacts: the Learning Assurance Plan, the
requirements documents, the architecture description, the
requirements-validation activity. These artefacts are
**Shepard-native**: every one is a document attached to a
`:DataObject` with a typed kind.

| EASA objective | Operational meaning | Shepard capability | Evidence path |
|---|---|---|---|
| **DA-01** (AL1) | Describe the proposed learning assurance process | `:DataObject` (kind=`learning_assurance_plan`); no template ships | `GET /v2/data-objects/{appId}` returns the LAP. Auditor wants the document plus a SHACL-validated metadata block (gap: template) |
| **DA-02** (AL2) | Capture system / AI-ML / data requirements | `:DataObject` (kind=`requirements`) + `:StructuredData` per requirement | One requirement = one appId; traceability edges via Predecessor/Successor to model, datasets, and validation runs |
| **DA-03** (AL2) | Describe (sub)system architecture as traceability anchor | `:DataObject` (kind=`architecture`) + Predecessor to requirements | Same — Shepard's *strength* is making these documents addressable, not the writing of them |
| **DA-04** (AL2) | Validate captured requirements | `:Activity` (kind=`requirements_validation`) + report | The auditor wants to see *who validated*, *when*, *against what criterion*. PROV1a's automatic capture handles "who" + "when"; the criterion needs to be carried in the Activity's `prov:Plan` (gap: no plan template) |
| **DA-05** (AL2) | Document all *derived* requirements generated from system requirements | Same shape as DA-02 with a typed `:DERIVED_FROM` edge | Cypher: `MATCH (r:DataObject{kind:"requirement"})<-[:DERIVED_FROM]-(d) RETURN d` |
| **DA-06** (AL2) | Validate derived requirements | Same shape as DA-04 | Same |
| **DA-07** (AL2) | Validate (sub)system requirements allocated to the AI/ML constituent against system requirements | `:Activity` with cross-references to both layers | Same |

### 2.1 Functional validation requirements (where REBAR lives)

DA-04 + DA-06 + DA-07 are validation-by-activity: a campaign happens
(humans run tests, computers run tests), a report drops, the report
is signed off. This is exactly what REBAR produces — a use-case-
specific test campaign with deterministic oracle data and a pass/fail
verdict per oracle case.

The Shepard integration shape (proposal, not shipped):

- **One REBAR campaign = one `:Collection`** with kind=`rebar_campaign`.
- **One oracle dataset = one `:DataObject`** (containers carry the
  actual data; appId stable; immutable).
- **One validation run = one `:Activity`** of class
  `fair2r:AuditPass` (already designed in
  `aidocs/semantics/95-shacl-templates-and-individuals.md §6`),
  with `prov:used` → oracle dataset, `prov:wasGeneratedBy` ← report
  Entity, `prov:wasAssociatedWith` → the model-under-test (`:DataObject`
  kind=`ml_model`).
- **The verdict** is a `fair2r:Claim` Entity whose
  `fair2r:verificationState` carries the pass/fail rung explicitly.

An auditor querying "show me functional validation evidence for
model X" runs:

```cypher
MATCH (m:DataObject {appId:$modelId})
  <-[:WAS_ASSOCIATED_WITH]-(a:Activity {class:"fair2r:AuditPass"})
  -[:WAS_GENERATED_BY]->(report:Entity)
RETURN a.startedAt, a.endedAt, report.appId, a.verificationState
```

Without REBAR-style structured outputs, the same auditor reads a
PDF and forms a subjective opinion. With the structured outputs,
the answer is one query.

### 2.2 Data quality for training / verification

This is DM-02..DM-07 re-asserted at the LA level. The Shepard
position is identical: SHACL shapes on the DQR record carry the
clauses; validation runs as `:Activity`; reports as `:StructuredData`.
Nothing new at the LA layer except the rollup view — the auditor
wants to read *one* LAP-level summary that points at each
underlying DQR record. That summary should be a generated artefact
(see §4 Regulatory Evidence Pack).

### 2.3 Learning-process verification

LM-05/LM-06/LM-09 (training curves, optimisation history,
validation-set performance) are MLflow's home turf. Shepard's
position is "we ingest the MLflow export." Concretely: an
`mlflow_run.zip` lands as a `:FileContainer` attached to a
`:DataObject` kind=`training_run`; the run's hyperparameters land as
`:StructuredData` keyed by run-id; the produced model lands as a
`:DataObject` kind=`ml_model`. The Predecessor chain goes
training-dataset → training-run → model.

LM-04 (quantifiable generalisation bound) is *not* a Shepard
objective — that's the model author's analytical work. But the
*report* of the bound is a `:DataObject` like any other, and the
Predecessor edge to the validation-set DataObject is the auditor's
trace.

### 2.4 Operational design domain (ODD) traceability

DM-01 captures the ODD; DA-02 references it from requirements;
DM-05 must show that collected data covers the ODD; DM-07 must show
that the labelled corpus still covers it. The thread is exactly the
PROV-O edge story:

```cypher
MATCH (odd:DataObject {kind:"odd_spec", appId:$oddId})
  <-[:REFERENCES]-(req:DataObject {kind:"requirement"})
  <-[:DERIVED_FROM]-(coverage:DataObject {kind:"coverage_report"})
RETURN odd, req, coverage
```

The gap is the **coverage report** itself — Shepard knows how to
store it but does not know how to *generate* it. That generator
belongs in a plugin (`shepard-plugin-odd-coverage`) — out of core
scope but on the in-tree SPI seam.

---

## 3. REBAR-style functional validation as the Shepard integration test case

A concrete worked example: a REBAR campaign validating an ML model
that predicts turbopump vibration anomalies from chamber-pressure
timeseries (the TR-004 signature in the LUMEN demo).

**The DataObject tree the campaign deposits in Shepard:**

```
:Collection {name:"REBAR-T1-vibration-anomaly", kind:"rebar_campaign"}
  ├─ :DataObject {kind:"learning_assurance_plan", appId:LAP-001}
  │    └─ :FileContainer (PDF + machine-readable YAML twin)
  ├─ :DataObject {kind:"odd_spec", appId:ODD-001}
  │    └─ :StructuredData {parameters: [chamber_p ∈ [10,200] bar,
  │                                     mass_flow ∈ [0.5,5] kg/s, …]}
  ├─ :DataObject {kind:"dqr_record", appId:DQR-001}    ← NEW shape
  ├─ :Collection {name:"training-set",   kind:"dataset_role:train"}
  ├─ :Collection {name:"validation-set", kind:"dataset_role:val"}
  ├─ :Collection {name:"test-set",       kind:"dataset_role:test"}
  ├─ :DataObject {kind:"oracle_dataset", appId:OR-001}
  │    └─ :TimeseriesContainer (TR-004 vibration + ground-truth label)
  ├─ :DataObject {kind:"ml_model", appId:M-001}
  └─ :Activity   {class:"fair2r:AuditPass", appId:AP-001}
       prov:used      → OR-001, M-001
       prov:generated → :DataObject{kind:"validation_report", appId:VR-001}
       fair2r:verificationState = "PASS"
```

**SHACL shapes (TPL1) that describe this tree:**

- `sh:NodeShape :LearningAssurancePlanShape` — required: title,
  applicableObjectives (DM-01..DM-09, DA-01..DA-07), assuranceLevel
  (AL1/AL2/AL3), reviewer, signedOff.
- `sh:NodeShape :DQRRecordShape` — required: relevance,
  completenessCriterion, representativenessCriterion,
  independenceCriterion, accuracyBound, integrityLevel.
- `sh:NodeShape :AuditPassShape` — required: testedEntity (must
  resolve via prov:used), verificationState ∈ {PASS, FAIL,
  CONDITIONAL_PASS}, validationReport (must resolve via
  prov:generated).

**Auditor query "find evidence for LA objective DA-07 on model M-001":**

```cypher
MATCH (m:DataObject {appId:"M-001"})
  <-[:WAS_ASSOCIATED_WITH]-(a:Activity {class:"fair2r:AuditPass"})
  -[:WAS_GENERATED_BY]->(r:Entity {kind:"validation_report"})
WITH a, r
MATCH (lap:DataObject {kind:"learning_assurance_plan"})
  -[:COVERS_OBJECTIVE {ref:"DA-07"}]->()
RETURN a.startedAt, r.appId, lap.signedOff
```

One Cypher query answers an EASA objective. That is the value
proposition.

---

## 4. The Shepard "Regulatory Evidence Pack" concept (proposed)

*Label: PROPOSAL, not shipped capability. Aligned to the SHACL TPL1
roadmap in `aidocs/semantics/95`. Builds on the SM1 retention work
and the upcoming Unhide publish plugin (`aidocs/integrations/67`).*

A **Regulatory Evidence Pack (REP)** is a SHACL-validated bundle of
DataObjects that satisfies one defined set of EASA objectives for
one AI/ML constituent at one Assurance Level. The REP is exportable
as a self-describing FAIR-compliant package (BagIt + RO-Crate +
PROV-O + SHACL).

### 4.1 The REP SHACL shape (Turtle pseudocode)

```turtle
@prefix rep:    <https://shepard.dlr.de/ns/rep#> .
@prefix sh:     <http://www.w3.org/ns/shacl#> .
@prefix prov:   <http://www.w3.org/ns/prov#> .
@prefix fair2r: <https://shepard.dlr.de/ns/fair2r#> .

rep:RegulatoryEvidencePackShape a sh:NodeShape ;
  sh:targetClass rep:RegulatoryEvidencePack ;
  sh:property [ sh:path rep:scopeStandard ;
                sh:hasValue "EASA-CP2-2024" ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:assuranceLevel ;
                sh:in ("AL1" "AL2" "AL3") ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:coversObjective ;
                sh:minCount 1 ] ;   # DM-01, DM-02, … strings

  # Required artefact slots — each resolves to a DataObject appId
  sh:property [ sh:path rep:learningAssurancePlan ;
                sh:class :LearningAssurancePlan ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:oddSpec ;
                sh:class :OddSpec ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:dqrRecord ;
                sh:class :DQRRecord ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:trainingSet ;
                sh:class :DatasetCollection ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:validationSet ;
                sh:class :DatasetCollection ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:testSet ;
                sh:class :DatasetCollection ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:independenceProof ;
                sh:class :IndependenceProof ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:validationActivity ;
                sh:class fair2r:AuditPass ; sh:minCount 1 ] ;
  sh:property [ sh:path rep:signOffActivity ;
                sh:class fair2r:SignOff ; sh:minCount 1 ] .
```

### 4.2 What the REP covers

| EASA objective set | REP slot |
|---|---|
| DA-01 | `rep:learningAssurancePlan` |
| DA-02..DA-07, requirements & arch | `rep:learningAssurancePlan → requirements` (link) |
| DM-01 | `rep:oddSpec` |
| DM-02, DA-04 (DQR thread) | `rep:dqrRecord` |
| DM-03, DM-04, DM-08, DM-09 | `rep:trainingSet/validationSet/testSet` annotations |
| DM-05, DM-07 (data quality verification) | `rep:dqrRecord` validation report (linked) |
| DM-06 + independence DQR | `rep:independenceProof` (graph-overlap query result) |
| LA functional validation | `rep:validationActivity` |
| Programme sign-off | `rep:signOffActivity` |

### 4.3 What the REP does not cover (honest)

- LM-04 generalisation bound — the *report* is in the LAP-linked
  artefacts; the *bound itself* is the model author's work.
- EXP-05/EXP-06/EXP-07 runtime monitoring — these belong in EXP-08/
  EXP-09 recordings, handled by TimescaleDB ingest. A separate
  "Operational Evidence Pack" shape will mirror REP for those
  objectives.

### 4.4 The export endpoint (proposed)

`GET /v2/regulatory/evidence-pack/{repId}.zip` — returns a BagIt
container with `data/` (the DataObjects' payloads, derefed via
appId), `metadata/ro-crate-metadata.json` (RO-Crate description),
`provenance.ttl` (PROV-O serialisation), `shapes.ttl` (the SHACL
shapes the pack validates against). Idempotent, content-addressed,
re-runnable.

---

## 5. The gaps Shepard does NOT cover today

Honest list. No softening.

1. **No first-class DQR record type.** This is the single biggest
   gap. DM-02, DM-04, DM-05, DM-07, DA-04 all assume a DQR exists
   as a typed, validated artefact. Today it's free-text. One sprint
   to design the shape; one sprint to write the SHACL.
2. **No automated independence check at dataset-split time.** DM-06
   independence DQR is *queryable* in Shepard but not *enforced*.
   The Cypher exists; nobody calls it.
3. **No coverage-report generator.** DM-05/DM-07 wants
   "data covers the ODD." Shepard stores the report but cannot
   compute it — that belongs in `shepard-plugin-odd-coverage`.
4. **No retention flag for regulatory evidence.** SM1's storage
   collector could delete a flagged Collection by accident; need
   `:Collection.retainForRegulatoryEvidence` and a hard check.
5. **No `learning_assurance_plan` template.** DA-01 expects a
   structured plan; today it's a free document. SHACL shape +
   TPL1 form would close this in one PR.
6. **No requirement-traceability matrix view.** DA-05 / DA-07
   demand "all derived requirements traceable to system
   requirements." The edges exist (`:DERIVED_FROM`); a frontend
   matrix view does not.
7. **No `:Activity` for sign-off.** A regulatory pack needs a
   non-AI sign-off Entity (signed by a human, with credentials).
   PROV1a captures AI Activities automatically; human sign-offs
   need a separate explicit endpoint (proposed:
   `POST /v2/data-objects/{appId}/sign-off`).
8. **No LM-04 / EXP-05..EXP-07 capability.** These are
   correctly out of Shepard's scope (model-level work, runtime
   monitoring). The pack must link to them but cannot generate
   them.

The first five are Shepard's homework. Items 6–7 are
trivial-effort, high-value. Item 8 is the boundary of the platform
mandate.

---

## 6. Coordination with the broader analysis

This document complements `easa-ai-regulatory-positioning.md`
(broader EASA framing, regulatory landscape, Article 50,
F(AI)²R angle) and `easa-ai-compliance.md` (objective-by-objective
high-level gap analysis). All three use the same objective-ID
notation (DM-XX, DA-XX, LM-XX, EXP-XX, HF-XX), the same Assurance
Level naming (AL1/AL2/AL3), and the same Shepard entity vocabulary
(`:DataObject`, `:Collection`, `:Activity`, `:StructuredData`,
appId, PROV1a, TPL1, SM1, `fair2r:` namespace from
`aidocs/semantics/95`).

The split of labour is: the broader positioning doc takes the *why
this matters* angle; the compliance doc takes the *full inventory*
angle; this doc takes the *operational mapping + auditor evidence
path* angle. A reader who wants to understand "what does an auditor
do, concretely, with Shepard in front of them" reads §1 and §2 of
this document. A reader who wants the strategic case reads the
broader doc. A reader who wants the comprehensive table reads the
compliance doc.

The **single net-new contribution** of this document is the
Regulatory Evidence Pack proposal in §4 — that pattern is not
defined in either companion doc and is the natural next design step
once TPL1 ships.

---

## Sources

- EASA AI Concept Paper Issue 02 (March 2024) — landing page:
  <https://www.easa.europa.eu/en/document-library/general-publications/easa-artificial-intelligence-concept-paper-issue-2>
  (PDFs returned binary to WebFetch; objective IDs and meanings
  drawn from secondary sources below.)
- EASA AI Concept Paper Issue 2 publication news:
  <https://www.easa.europa.eu/en/newsroom-and-events/news/easa-publishes-artificial-intelligence-concept-paper-issue-2-guidance>
- "Towards certifiable AI in aviation: landscape, challenges, and
  opportunities," arxiv 2409.08666, HTML render:
  <https://arxiv.org/html/2409.08666v1>
  (W-shape building blocks and operational definitions for
  completeness, representativeness, ODD).
- "On the Feasibility of EASA Learning Assurance Objectives for
  Machine Learning Components" — de Grancey, Gerchinovitz, Alecu,
  Bonnin, Dalmau, Delmas, Mamalet (ERTS 2024), HAL hal-04575318
  (confirmed DM-08, LM-16 IDs and the prefix structure used here).
- EASA ForMuLA report (Collins Aerospace, 2023):
  <https://www.easa.europa.eu/en/downloads/137878/en>
- EASA MLEAP Final Report (July 2024):
  <https://www.easa.europa.eu/sites/default/files/dfu/mleap-d4-public-report-issue01.pdf>
- Daedalean explainer on W-shaped learning assurance (now
  destinus.com, redirect dead at fetch time).
- Companion: `aidocs/agent-findings/easa-ai-regulatory-positioning.md`.
- Companion: `aidocs/agent-findings/easa-ai-compliance.md`.
- Shepard internal: `aidocs/semantics/95-shacl-templates-and-individuals.md`
  (TPL1 / fair2r namespace).
- Shepard internal: `aidocs/platform/47-dev-experience-and-plugin-system.md`
  (plugin SPI seam — REBAR ingest target).
