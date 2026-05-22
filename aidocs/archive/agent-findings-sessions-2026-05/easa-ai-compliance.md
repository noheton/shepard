---
stage: decommissioned
last-stage-change: 2026-05-23
---

# EASA AI Concept Paper Issue 2 — Compliance Gap Analysis
## Shepard + ReBAR Coverage Assessment

**Source caveat:** Primary source is the EASA AI Concept Paper Proposed Issue 02
(February 2023, extracted from `easa.europa.eu/sites/default/files/dfu/...`). The
published Issue 02 (March 2024) is structurally substantively the same per EASA
programme manager presentations (Soudain, Nov 2025 ICAO event; Waller, ICAO APAC
2024). Page and objective numbers reference the Proposed Issue 02 text.

**Scope qualifier:** Not every Shepard AI use case triggers CP2 obligations. LLM-based
data exploration (the MCP surface, wiki-writer, semantic suggestions) is out of CP2
scope — these are development tools operating under human review, not deployed ML
inference systems with aviation safety significance. The use cases that *do* trigger
CP2 are: anomaly detection on manufacturing process timeseries (MFFD AFP/welding),
constraint-violation → defect prediction, flight phase classification from ADS-B data,
and channel quality scoring deployed as part of a production ingest pipeline.

**Evidence vs. assurance framing (read this first):** Shepard and ReBAR are *evidence
platforms*. They store, version, trace, and expose the artefacts that an ML assurance
case requires. They do not — and cannot — produce the *properties* that assurance
requires: generalisation guarantees, robustness certificates, HAT capability
demonstrations, runtime ODD-conformity bounds. Mapping these systems against CP2 is a
mapping of evidence *supply* against assurance *demand*. Gaps in this map are genuine
gaps; they cannot be closed by better tooling alone — they require model-level work,
operational capability design, and organisational process.

---

## 1. Executive Summary

| Dimension | Coverage estimate | Notes |
|---|---|---|
| Data management (DM) family | ~75% evidence | DM-13 five MOCs strongest; DM-12 split strategy weak |
| Development assurance (DA) family | ~60% evidence | MLflow covers training curves + params; gap on structured safety case artefact |
| Learning management (LM) family | ~55% evidence | MLflow covers LM-05/LM-06/LM-09; LM-07 bias-variance gap |
| Safety assessment (SA/ICSA) family | ~15% evidence | Static pre-deployment only; runtime monitoring absent |
| Explainability (EXP) family | ~20% evidence | Dev explainability via metadata only; operational explainability absent |
| Ethics (ET) family | ~30% evidence | FAIR-1 metadata, access control; no ethics review board tooling |
| Organisations (ORG) family | ~35% process | Audit log (F3), permission model; gaps on ORG-03/ORG-04 |
| **Overall (evidence supply)** | **~40% of CP2 objectives partially addressed** | Many objectives require non-tool organisational or model-level work |

The stack's strongest compliance story is **DM-13** (data quality and traceability) — the five MOCs map directly to Shepard features. The clearest headline gap is **runtime inference monitoring** (SA-01 MOC-3, EXP-14/15, ICSA-02) — neither Shepard nor ReBAR currently provides ODD-conformity checking during live inference.

At **Level 1A** (human assistance, AI acts as tool under full human decision authority) the evidence stack is credible for a Level 1A compliance case with gaps closed by organisational process. At **Level 1B and 2A/2B**, the runtime monitoring and explainability gaps are blocking and require dedicated infrastructure.

---

## 2. EASA AI Requirements Inventory

Organised by CP2's four trustworthiness building blocks plus the Organisations (ORG) provisions. Reference is to the objective ID used in the CP2 text. "AL" = Assurance Level (1–5, where AL1 is highest obligation); "L" = AI Level (1A/1B/2A/2B).

### 2.1 Building Block 1 — Trustworthiness Analysis

| Objective | Description | Min AL | Applies at |
|---|---|---|---|
| CO-01 | Identify end users and their interaction with the AI system | AL1 | All |
| CO-02 | Identify high-level tasks and performance objectives per end user | AL1 | All |
| CO-03 | Determine AI system boundary and domain-specific system definition | AL1 | All |
| CO-04 | Define and document the Concept of Operations (ConOps) for the AI system | AL1 | All |
| CO-05 | Document how end user inputs are collected and incorporated | AL2 | 1B+ |
| CO-06 | Perform functional analysis of the system | AL2 | 1B+ |
| SA-01 | Perform safety (support) assessment for all AI-based (sub)systems | AL1 | All |
| SA-01 MOC-1 | DAL/SWAL allocation and verification | AL1 | All |
| SA-01 MOC-2 | Performance metrics and safety thresholds | AL1 | All |
| SA-01 MOC-3 | Monitoring capabilities to detect exposure to data outside OD/ODD | AL2 | 1B+ |
| SA-01 MOC-4 | Identification and classification of uncertainties | AL2 | 1B+ |
| ET-01 | Ethics-based trustworthiness assessment | AL1 | All |
| ET-02 | No risk of creating or reinforcing unfair bias | AL2 | 1B+ |
| ET-03 | No capability of identifying personal data not required for function | AL2 | 1B+ |
| ET-04 | Comply with national and EU data protection regulations (GDPR) | AL1 | All |
| ET-05 | Procedures to avoid creating or perpetuating discrimination | AL2 | 1B+ |
| ET-06 | Environmental impact analysis | AL3 | 2A+ |
| ET-07 | Measures to reduce or mitigate environmental impacts | AL3 | 2A+ |
| ET-08 | Identify new skills needed for users and end users | AL2 | 1B+ |
| ET-09 | Assessment of risk of de-skilling of users and end users | AL3 | 2A+ |
| IS-01 | Identify AI security threats for each AI-based subsystem and dataset | AL1 | All |
| IS-02 | Document mitigation approach to address identified security threats | AL2 | 1B+ |
| IS-03 | Validate and verify effectiveness of security controls | AL2 | 1B+ |

### 2.2 Building Block 2 — AI Assurance (Learning Assurance + Development Explainability)

#### Data Management (DM)

| Objective | Description | Min AL |
|---|---|---|
| DM-01 | Define ODD parameters for AI/ML constituent data | AL1 |
| DM-02 | Capture Data Quality Requirements (DQRs) for all data | AL1 |
| DM-03 | Capture requirements on data to be pre-processed and transformed | AL1 |
| DM-04 | Ensure validation of data annotations/labels | AL2 |
| DM-05 | Ensure validation of correctness and completeness of data | AL1 |
| DM-06 | Identify data sources and collect data in accordance with DQRs | AL1 |
| DM-07 | Ensure data quality post-collection and labelling | AL2 |
| DM-08 | Define data preparation operations (format, feature extraction) | AL2 |
| DM-09 | Define and document pre-processing operations | AL2 |
| DM-10 | Define data transformations that affect feature distribution | AL3 |
| DM-11 | Ensure data effectiveness for stability of learning algorithms | AL2 |
| DM-12 | Distribute data into three separate and independent sets (train/val/test) | AL1 |
| DM-13 | Five mandatory quality criteria for each set: | AL1 |
| DM-13-1 | — Completeness: data set covers the full ODD | AL1 |
| DM-13-2 | — Representativeness: distribution matches operational distribution | AL1 |
| DM-13-3 | — Accuracy/correctness: labels and measurements are correct | AL1 |
| DM-13-4 | — Traceability: each sample traceable to origin and processing steps | AL1 |
| DM-13-5 | — Independence: training, validation, test sets are non-overlapping | AL1 |
| DM-14 | Perform data and learning verification step to confirm data quality objectives are met | AL2 |

#### Learning Management (LM)

| Objective | Description | Min AL |
|---|---|---|
| LM-01 | Describe AI/ML constituents and model architecture | AL2 |
| LM-02 | Capture requirements pertaining to the learning process | AL2 |
| LM-03 | Document credit sought from training environment (hardware, frameworks) | AL2 |
| LM-04 | Provide quantifiable generalisation guarantees | AL1 |
| LM-05 | Document results of model training (training curves, metrics) | AL2 |
| LM-06 | Document model optimisation that may affect model performance | AL2 |
| LM-07 | Account for bias-variance trade-off in model family selection | AL2 |
| LM-08 | Ensure estimated bias and variance of selected model is acceptable | AL2 |
| LM-09 | Perform evaluation of trained model performance on validation set | AL1 |
| LM-10 | Perform requirements-based verification of trained model | AL2 |
| LM-11 | Provide analysis on stability of learning algorithms | AL2 |
| LM-12 | Perform and document verification of stability of trained model | AL2 |
| LM-13 | Perform and document verification of robustness of trained model | AL3 |
| LM-14 | Verify anticipated generalisation bounds using test data set | AL1 |

#### Implementation (IMP)

| Objective | Description | Min AL |
|---|---|---|
| IMP-01 | Capture requirements pertaining to model implementation | AL2 |
| IMP-02 | Document any post-training model transformation (quantisation, conversion) | AL2 |
| IMP-03 | Plan and execute appropriate development assurance activities for implementation | AL2 |
| IMP-04 | Verify that any transformation preserves model properties | AL2 |
| IMP-05 | Document differences between training and inference hardware/software platforms | AL2 |
| IMP-06 | Perform evaluation of inference engine performance | AL2 |
| IMP-07 | Verify and document stability of inference engine | AL2 |
| IMP-08 | Verify and document robustness of inference engine | AL3 |
| IMP-09 | Perform requirements-based verification of inference engine | AL2 |

#### Development Assurance (DA)

| Objective | Description | Min AL |
|---|---|---|
| DA-01 | Describe proposed learning assurance process | AL1 |
| DA-02 | Capture requirements documents covering system, AI/ML and data requirements | AL2 |
| DA-03 | Describe system and subsystem architecture (serves as reference for requirements traceability) | AL2 |
| DA-04 | Validate captured requirements | AL2 |
| DA-05 | Document evidence that all derived requirements generated from system requirements are captured | AL2 |
| DA-06 | Document evidence of validation of derived requirements | AL2 |
| DA-07 | Validate (sub)system requirements allocated to AI/ML constituent against system requirements | AL2 |

#### Development Explainability (EXP-01 — EXP-04)

These objectives concern explainability at development time — identifying stakeholder needs and selecting methods.

| Objective | Description | Min AL |
|---|---|---|
| EXP-01 | Identify stakeholders (other than end users) needing explainability from the AI system | AL2 |
| EXP-02 | Identify explainability needs for each stakeholder group | AL2 |
| EXP-03 | Identify and document explainability methods at AI/ML item and system levels | AL2 |
| EXP-04 | Provide means to record operational data necessary for post-hoc explanation | AL2 |

### 2.3 Building Block 3 — Human Factors for AI (Operational Explainability + HAT)

Note: CP2 does not define separate "HA-xx" objectives. Human-AI teaming (HAT) requirements are
expressed through EXP-05 through EXP-17 (operational explainability) and the ORG Provisions.

| Objective | Description | Min AL | Applies at |
|---|---|---|---|
| EXP-05 | Characterise the need for explainability for each AI output relevant to end user tasks | AL2 | 1B+ |
| EXP-06 | Present explanations to end users in clear and understandable way | AL2 | 1B+ |
| EXP-07 | Define relevant explainability so receiver of explanation can contest AI output | AL1 | All |
| EXP-08 | Define level of abstraction of explanations taking into account end user's knowledge | AL2 | 1B+ |
| EXP-09 | Allow end users to customise explanations where customisation capability is available | AL3 | 2A+ |
| EXP-10 | Define timing when explainability will be available (before, during, after decision) | AL2 | 1B+ |
| EXP-11 | Design AI system to enable end user to express trust calibration / acceptance | AL3 | 2A+ |
| EXP-12 | Ensure validity of explanations for each relevant output | AL3 | 2A+ |
| EXP-13 | AI system should deliver indication of degree of confidence in its outputs | AL2 | 1B+ |
| EXP-14 | AI system inputs should be monitored to be within operational domain (OD) | AL2 | 1B+ |
| EXP-15 | AI system outputs should be monitored to be within specified performance bounds | AL2 | 1B+ |
| EXP-16 | Training and instructions for end users should include information about AI limitations | AL2 | 1B+ |
| EXP-17 | Information about unsafe AI operating conditions should be communicated to end users | AL2 | 1B+ |

### 2.4 Building Block 4 — AI Safety Risk Mitigation

CP2 defines two ICSA objectives only. ODD drift detection and model degradation are addressed
through SA-01 MOC-3 and ICSA-02 process provisions, not as separate numbered objectives.

| Objective | Description | Min AL |
|---|---|---|
| ICSA-01 | Identify data to be recorded for continuous safety assessment | AL2 |
| ICSA-02 | Use collected data to perform continuous safety assessment | AL2 |

### 2.5 Organisations (ORG)

Note: CP2 labels these as "Provisions" (not "Objectives") — they are organisational rather than
technical requirements. Cited here as ORG-01 through ORG-08 per the Provision numbering in CP2.

| Provision | Description | Applicability |
|---|---|---|
| ORG-01 | Review and adapt processes to introduction of AI | All |
| ORG-02 | Prepare for AI-specific regulatory framework (Reg. 2022/1645) | All |
| ORG-03 | Implement data-driven AI continuous safety assessment system | 1B+ |
| ORG-04 | Ensure safety-related AI systems are auditable | All |
| ORG-05 | Adapt continuous risk management process to accommodate AI specifics | 1B+ |
| ORG-06 | Adapt training processes for AI systems | All |
| ORG-07 | Ensure end users are adequately trained on AI systems they operate | All |
| ORG-08 | Establish means to continuously assess ethics of AI-based systems | 2A+ |

---

## 3. Shepard Coverage

### 3.1 DM Family — Strongest coverage area

| CP2 Objective | Shepard Feature | Status |
|---|---|---|
| DM-06 provenance to source | `GET /v2/provenance/activities` — PROV-O-aligned `:Activity` log with `actionKind`, `agentUsername`, `targetAppId`, `startedAtMillis`; ProvenanceCaptureFilter auto-records every write | Shipped (ActivityIO.java) |
| DM-08 data version control | PV1a/PV1b payload version history — every PayloadReference carries `versionNumber`; `GET /v2/*/history` returns full version chain | Shipped |
| DM-09 data access control | Keycloak OIDC RBAC — `@RolesAllowed` per endpoint; Collection-level `readGroups`/`writeGroups` | Shipped |
| DM-13-1 completeness | QA-1 `numericValue` constraints on annotation terms; `attributeSchema` required-field enforcement | Partially shipped (constraint engine) |
| DM-13-2 representativeness | Annotation-based ODD coverage documentation (structured annotations + semantic graph); no automated coverage computation | Manual process only |
| DM-13-3 accuracy/correctness | DataObject status workflow (DRAFT→IN_REVIEW→READY→PUBLISHED); reviewer role | Shipped (status machine) |
| DM-13-4 traceability | DataObject Predecessor/Successor chain in Neo4j; container-level `parentRef` in import manifests | Shipped |
| DM-13-5 independence | OpenLineage/Marquez (ReBAR) records dataset lineage per DAG run; Shepard `predecessorRefs` encode split provenance | Shipped (via integration) |
| DM-01 data management plan | `Collection` description field; `docs/` and annotation schema — no structured DMP template | Partial |
| DM-02/DM-03 source/collection docs | DataObject `attributes` + structured annotations; no enforced vocabulary | Partial |
| DM-07 schema enforcement | QA-1 annotation term constraints; `attributeSchema` validation in import pipeline | Shipped |
| DM-12 three-way split | Import manifest `predecessorRefs` can encode split membership; no split strategy enforcement or automated check | Manual process |

### 3.2 DA / IMP Family

| CP2 Objective | Shepard Feature | Status |
|---|---|---|
| DA-04 validate requirements | Version history (PV1a/PV1b) + import manifest plan-seal validate step provides requirements validation trail | Partial |
| DA-05/06 derived requirements evidence | DataObject version chain + import validation commitId records derivation; no formal traceability matrix | Partial |
| DA-07 AI/ML constituent requirements validation | No structured requirements traceability matrix; DataObject attributes can encode requirements but with no schema | Gap |
| IMP-01 implementation requirements | MLflow model registry (ReBAR) + Shepard FileReference for serialised model artefact; `aiGenerated` flag on output | Partial |

### 3.3 LM Family

| CP2 Objective | Shepard Feature | Status |
|---|---|---|
| LM-06 model versioning | `aiGenerated` flag on FileReference/DataObject; PV1a version chain; MLflow (ReBAR) primary | Partial |
| LM-09 test set evaluation | AI1c `qualityScore` ∈ [0,1] on TimeseriesReference (backend only, no UI yet) | Dev-track |

### 3.4 EXP / Explainability

| CP2 Objective | Shepard Feature | Status |
|---|---|---|
| EXP-04 record operational data for explanation | `shepard-plugin-ai` `:AiActivity` node stores `promptHash`, call metadata, and inference I/O (when `storePromptText=true`) | Shipped (design) |
| EXP-07 contest/override mechanism | Human retains DataObject status authority; no AI auto-promote; `aiGenerated` flag marks but never locks | Structurally satisfied |
| EXP-13 confidence indication | `aiGenerated` flag visible in DataObject UI; no confidence score surface | Gap |
| EXP-14/15 input/output monitoring | No runtime ODD-conformity check — see gap 6.1 (inference monitor) | Gap |

### 3.5 ORG Family

| CP2 Objective | Shepard Feature | Status |
|---|---|---|
| ORG-04 audit trail | F3 permission audit log; ProvenanceCaptureFilter — every write logged with agent, target, timestamp, method, path, status | Shipped |
| ORG-06 incident reporting | DataObject status DRAFT→PUBLISHED; no explicit incident-type marker | Partial |

---

## 4. ReBAR Coverage

ReBAR = Apache Airflow + MLflow + MinIO + Marquez/OpenLineage.
The planned `shepard-plugin-airflow` (ShepardReadOperator / ShepardWriteOperator / ShepardProvenanceOperator) is the integration bridge.

| CP2 Objective | ReBAR Feature | Status |
|---|---|---|
| LM-03 training environment | Airflow DAG records executor, Python version, framework; MLflow `mlflow.log_artifact("requirements.txt")` | Shipped |
| LM-04 training reproducibility | MLflow experiment `run_id` with pinned artifact hash; Airflow DAG `run_id` + `execution_date` | Shipped |
| LM-05 training curves | `mlflow.log_metric("train_loss", ...)` per epoch | Shipped |
| LM-06 model versioning | MLflow Model Registry — `Staging` / `Production` / `Archived` transitions | Shipped |
| LM-07 bias-variance | No native bias-variance dashboard — must log custom metrics to MLflow | Gap |
| LM-09 test performance | `mlflow.log_metric("test_accuracy", ...)` on independent test set | Shipped |
| LM-10 performance thresholds | MLflow `MlflowClient.set_model_version_tag("threshold", ...)` possible; no enforced gate | Partial |
| DM-13-5 dataset independence | OpenLineage/Marquez records which input datasets fed each DAG run | Shipped |
| DM-06 data provenance | Marquez `DatasetEvent` records full lineage graph per run | Shipped |
| DA-04 configuration management | Airflow DAG versioning via git; MLflow run params logged | Partial |
| IMP-01 deployment reproducibility | MLflow model URI (`models:/model-name/Production`) ensures consistent model load | Shipped |
| IMP-02 runtime environment | Airflow executor environment + Docker image SHA captured in DAG run metadata | Partial |

---

## 5. Integration Coverage

What the **combined Shepard + ReBAR stack** addresses that neither alone does:

| Scenario | How integration closes the gap |
|---|---|
| DM-13-4 traceability end-to-end | ShepardWriteOperator writes MLflow `run_id` into DataObject `attributes["rebar_run_id"]`; user can navigate from Shepard DataObject → MLflow run → Marquez lineage graph in one click |
| DM-13-5 independence audit | Marquez lineage records input MinIO dataset `appId`; ShepardProvenanceOperator links Marquez event to Shepard DataObject predecessor chain — auditor sees single provenance graph spanning both systems |
| LM-06 model → data binding | Shepard FileReference (serialised model artefact) + MLflow run URI stored in same DataObject; `aiGenerated` flag marks all outputs produced by that model version |
| DA-06 verification evidence | Shepard Collection groups all verification DataObjects; MLflow `run_id` on each DataObject links back to the experiment run that produced the evidence |
| ORG-04 audit trail | Shepard ProvenanceCaptureFilter logs all API access; Airflow task logs record data reads; Marquez records transformations — three-layer audit trail covers full ML pipeline |
| ICSA-01 operational data recording | ShepardWriteOperator can ingest inference call outputs back to Shepard as new DataObjects (operational inference log) — enables post-hoc safety assessment on collected operational data |

---

## 6. Gap Analysis

### 6.1 Headline gap — Runtime inference monitoring (BLOCKING for Level 1B+)

**Objectives:** SA-01 MOC-3, EXP-14/15, ICSA-01, ICSA-02

**The gap:** CP2 requires that deployed AI systems have monitoring capabilities that
detect when input data falls outside the OD/ODD, and that continuous safety assessment
is performed on operational data. Neither Shepard nor ReBAR currently provides this:
Shepard stores training data and traces provenance pre-deployment; ReBAR trains and
tracks experiments. Neither has an inference-time component.

**Minimum closure — `shepard-plugin-inference-monitor`:**

```
Plugin: shepard-plugin-inference-monitor
Capability: MONITOR

Components:
  InferenceCallRecord — DataObject subtype storing:
    inputHash, timestamp, modelVersionAppId, ODD_conformity_flag,
    predictionValue, confidenceScore, latencyMs

  ODD Boundary Check — validates each inference call's input features
    against ODD parameter ranges stored in Shepard as structured DataObject
    (ODD specification DataObject referenced by PREDECESSOR_OF from model DataObject)

  Drift Monitor — wraps Evidently AI / NannyML / Arize AI:
    batch drift report emitted as new DataObject (DatasetDriftReport kind)
    linked via PREDECESSOR_OF to the model DataObject

  Admin endpoint: GET/PATCH /v2/admin/inference-monitor/config
    fields: oddSpecAppId, driftCheckInterval, driftAlertThreshold, alertDestination

  ICSA-01 compliance: every inference call recorded as InferenceCallRecord
  ICSA-02 compliance: scheduled drift check DAG (Airflow) reads InferenceCallRecords,
    writes DriftReport DataObject back to Shepard
  SA-01 MOC-3 compliance: ODD_conformity_flag per call + alert on violation
```

External tools to wrap: Evidently AI (open-source, MIT, Python, active), NannyML
(EU-funded, open-source, LGPLv3), Arize AI (commercial, has a free tier).
Evidently is the lowest-friction option given the ReBAR Python environment.

### 6.2 Development explainability gap (BLOCKING for Level 1B)

**Objectives:** EXP-01, EXP-02, EXP-03, EXP-04

**The gap:** CP2 EXP-01/02 require identifying and documenting explainability needs for
all stakeholder groups. EXP-03 requires identifying and documenting the explainability
methods applied at AI/ML item and system levels. EXP-04 requires providing the means to
record operational data for post-hoc explanation. MLflow can store artefacts but provides
no structured mapping from stakeholder need → method → evidence artefact.

**Minimum closure:** Add `ExplainabilityReport` DataObject kind to `shepard-plugin-ai`:

```
ExplainabilityReport:
  attributes:
    stakeholderGroups: string[]      (per EXP-01)
    explainabilityNeeds: string[]    (per EXP-02, one per stakeholder group)
    method: "SHAP" | "LIME" | "integrated_gradients" | other   (per EXP-03)
    framework_version: string
    feature_importance_json: MinIO FileReference (JSON)
    global_explanation_plot: MinIO FileReference (PNG/HTML)
    confidence_calibration_plot: MinIO FileReference
  linked via PREDECESSOR_OF to the model DataObject (FileReference)
  EXP-04 satisfied: inference I/O logged to AiActivity nodes
```

SHAP (SHapley Additive exPlanations) is the current best-practice method for tabular
and timeseries models in manufacturing contexts — compatible with scikit-learn,
XGBoost, and PyTorch (via captum). ReBAR can log SHAP values as MLflow artefacts;
ShepardWriteOperator can write the structured report to Shepard.

### 6.3 Operational explainability gap (BLOCKING for Level 1B, EXP-05/06/13)

**The gap:** CP2 EXP-05/06 require characterising and presenting explanations to
end users clearly. EXP-13 requires delivering a confidence/degree-of-certainty
indication with AI outputs. The `aiGenerated` flag marks outputs but provides no
confidence score or human-readable explanation of the prediction.

**Minimum closure:**
- Extend `shepard-plugin-ai` `AiActivity` node to carry `confidenceScore` (float
  0–1), `explanationText` (short natural language), `oddConformityFlag` (bool)
- Surface these in the DataObject detail view alongside the `aiGenerated` badge
- At Level 1A this is optional (human reviews anyway); at Level 1B it is required per EXP-13

### 6.4 Safety case document structure (DA-01 through DA-07 evidence aggregation)

**The gap:** CP2 DA objectives (DA-01 through DA-07) collectively require that learning
assurance process documentation, requirements, architecture, and verification evidence
are captured and traceable. Shepard can store these as DataObjects and PDF files but
provides no structured assurance-case template that links evidence artefacts to specific
CP2 objectives.

**Minimum closure:** Define a `SafetyCaseDocument` DataObject kind with required
attributes: `aiLevel`, `assuranceLevel`, `coverageClaimsJson`, plus a Collection
convention that groups all evidence DataObjects under an "Assurance Evidence"
Collection. The structured `coverageClaimsJson` provides a machine-readable mapping
from CP2 objective ID to evidence DataObject appId — enabling automated completeness
checks.

### 6.5 Bias-variance assessment (LM-07)

**The gap:** No native tool. MLflow can log custom metrics but provides no
bias-variance decomposition or visualisation.

**Minimum closure:** Add a `BiasVarianceReport` DataObject kind; ReBAR Airflow DAG
step to compute and log via `mlflow.log_artifact`. The computation itself is
straightforward with mlxtend (`bias_variance_decomp` function).

### 6.6 DM-12 data split strategy enforcement

**The gap:** CP2 requires demonstration that training / validation / test sets are
genuinely separate. Shepard's `predecessorRefs` can encode this but does not enforce
it or provide an audit report.

**Minimum closure:** Add a `DataSplitManifest` DataObject kind that explicitly records
three `Collection appId` references (training set collection, validation set
collection, test set collection) plus `splitStrategy` (random / stratified / temporal)
and `splitRatios`. ShepardProvenanceOperator asserts the three collections have no
overlapping `FileReference.contentHash` values. This is a verification step, not just
metadata — it produces a pass/fail evidence DataObject.

### 6.7 Ethics review board tooling (ORG-08, ET-01/ET-02)

**The gap:** CP2 ORG-08 requires establishing means (processes) to continuously assess
ethics of AI-based systems. ET-01 requires ethics-based trustworthiness assessment.
ET-02 requires ensuring no unfair bias risk. Shepard has no workflow routing to an
ethics review gate.

**Minimum closure:** Use the DataObject status machine extended with two new review
states: `ETHICS_REVIEW` and `ETHICS_APPROVED`. A designated `ethics-reviewer` Keycloak
role can transition from `ETHICS_REVIEW` → `ETHICS_APPROVED` or → `REJECTED`. This
is a configuration change to the status machine, not new code.

---

## 7. AI Level Assessment

This section uses EASA CP2's own AI Level taxonomy. Note: "Level of Involvement (LOI)"
in EASA's certification vocabulary refers to authority scrutiny depth (not the AI
Levels). This document uses "AI Level" exclusively for the 1A/1B/2A/2B/3 taxonomy.

### Level 1A — Human assistance (AI acts as tool, human has full decision authority)

**CP2 scope:** DA, DM, LM, IMP families at AL3–AL4; SA, EXP, HAT at lower AL.

**Stack coverage at Level 1A:** ~70% of applicable objectives addressed

- DM-13 five MOCs: covered by Shepard (DM-13-4 traceability strongest)
- LM-03/04/05/06/09: covered by ReBAR/MLflow
- DA-04/DA-06: partially covered
- ORG-04 audit trail: covered by F3 + ProvenanceCaptureFilter
- SA-01 MOC-3 runtime monitoring: *reduced obligation at Level 1A — monitoring of OD/ODD is
  most critical where AI output has direct operational consequence; at Level 1A, the human
  acts as the OD-conformity check*
- EXP-01 through EXP-04 (development explainability): partially covered — EXP-04 satisfied
  by `:AiActivity` logging; EXP-01/02/03 need the `ExplainabilityReport` DataObject (gap 6.2)

**Verdict (credible with caveats):** With gap 6.2 (ExplainabilityReport) and gap 6.4
(SafetyCaseDocument) closed, the stack is credible for a Level 1A compliance case at AL3–AL4.
The remaining gaps (runtime monitoring, operational explainability) are not blocking at this level.

**Concrete Level 1A application:** AI1c channel quality scoring (`qualityScore` on
TimeseriesReference). The model runs at ingest time under human review of flagged
channels; no automated rejection. This is a canonical Level 1A use case.

### Level 1B — Cognitive augmentation (AI output directly informs human decision)

**CP2 scope:** DA, DM, LM, IMP at AL2–AL3; SA, EXP at AL2; ORG at AL2.

**Stack coverage at Level 1B:** ~45% of applicable objectives addressed

New requirements at Level 1B:
- SA-01 MOC-3 (runtime ODD monitoring): **gap** — blocks Level 1B
- EXP-13 (confidence indication to operator): **gap** — blocks Level 1B
- EXP-14/15 (input/output monitoring): **gap** — blocks Level 1B
- EXP-07 (contest/override): structurally satisfied by human decision authority
- ET-02/ET-08 (competency gap, user skills): **organisational process gap** — not a tool gap

**Verdict (not yet credible):** Not credible without closing gaps 6.1 (inference monitor)
and 6.3 (operational explainability UI). Both are buildable. With those two closed, Level 1B
is achievable for anomaly detection on MFFD process data.

**Concrete Level 1B application:** Anomaly detection on AFP welding timeseries.
The model flags a candidate anomaly; the process engineer reviews the flagged channel
with the confidence score and the SHAP explanation before deciding to halt the
process. Level 1B because the AI output directly informs a consequential decision
but the human retains authority.

### Level 2A/2B — Human-AI teaming (AI has partial/full operational authority sharing)

**CP2 scope:** All of the above at AL1–AL2; plus EXP-08 through EXP-17 (operational
explainability depth); ET-04/ET-06/ET-07/ET-09; ORG-08; ICSA-02 (continuous safety
assessment process is substantially more demanding at this level).

**Stack coverage at Level 2A/2B:** ~25% of applicable objectives addressed

Additional blocking gaps at Level 2A+:
- EXP-09/11/12 (customisation, trust calibration, explanation validity): **major new work** —
  these are operational system design requirements driven by UI/UX design of the AI-facing
  operator interface, not data platform features
- EXP-08/10 (abstraction level per user, timing of explanation): require runtime explanation
  infrastructure beyond gap 6.3
- ICSA-02 continuous safety assessment (which encompasses ODD drift and model degradation
  detection as process requirements): **gap** — part of the inference monitor plugin but
  requires statistical process control capability beyond basic data logging

**Verdict:** Level 2A/2B is out of scope for Shepard+ReBAR in the current design.
HAT requirements drive the design of the operational system (cockpit, control room,
shop floor UI), not the data platform. Shepard contributes evidence to a Level 2
compliance case but cannot substitute for the operational system design.

---

## 8. Roadmap Recommendations

Ordered by: closes a blocking compliance gap first, then by effort-to-value ratio.

### Priority 1 — `shepard-plugin-inference-monitor` [BLOCKING for Level 1B]

**Closes:** SA-01 MOC-3, ICSA-01, ICSA-02, EXP-14/15

**Effort:** 3–4 sprints (Evidently integration + InferenceCallRecord DataObject kind
+ ODD spec schema + admin config + Airflow DAG template)

**Design dependencies:** TS-IDa (appId on Timeseries nodes — needed to address channels
without 5-tuple) must ship first; `shepard-plugin-ai` capability registry must ship
first (STRUCTURED capability used for structured inference calls).

**Acceptance:** A new MFFD AFP anomaly detection inference session logs
`InferenceCallRecord` DataObjects; the scheduled drift check DAG (satisfying ICSA-02)
writes a `DatasetDriftReport` DataObject; admin can review ODD conformity per inference
call from the DataObject detail view (satisfying EXP-14/15).

### Priority 2 — ExplainabilityReport DataObject kind + ShepardWriteOperator extension [BLOCKING for Level 1B]

**Closes:** EXP-01 (stakeholder identification), EXP-02 (explainability needs per stakeholder), EXP-03 (document explainability methods), and provides artefact evidence for EXP-13 (confidence indication)

**Effort:** 1–2 sprints (DataObject kind definition + import manifest support +
DataObject detail view extension + ReBAR Airflow step template using SHAP)

**Design dependencies:** `shepard-plugin-airflow` ShepardWriteOperator (partially
designed). Can ship the DataObject kind without the Airflow integration.

**Acceptance:** MFFD anomaly detection model produces a `ExplainabilityReport`
DataObject with SHAP feature importance JSON and summary plot, linked via
`PREDECESSOR_OF` to the trained model FileReference.

### Priority 3 — Confidence score + ODD conformity flag in DataObject UI [BLOCKING for Level 1B]

**Closes:** EXP-06 (present explanations clearly), EXP-13 (confidence indication in outputs), partial EXP-14/15 (input/output monitoring surface)

**Effort:** 0.5–1 sprint (extend `AiActivity` node + DataObject detail view badge)

**Design dependencies:** `shepard-plugin-inference-monitor` (Priority 1) populates
`oddConformityFlag`; `shepard-plugin-ai` `AiActivity` already carries `promptHash` —
extend to carry `confidenceScore`.

**Acceptance:** DataObject detail view shows `[AI-generated] Confidence: 0.87 | ODD: IN` badge.

### Priority 4 — SafetyCaseDocument DataObject kind [required for DA-01 through DA-07 evidence aggregation]

**Closes:** DA-01 through DA-07 (evidence linkage — the assurance case that references all individual DA evidence artefacts)

**Effort:** 0.5 sprint (DataObject kind schema + import manifest support +
Collection convention documentation)

**Design dependencies:** None — independent of other priorities.

**Acceptance:** MFFD anomaly detection assurance case is represented as a
`SafetyCaseDocument` DataObject whose `coverageClaimsJson` maps CP2 objective IDs
to evidence DataObject appIds; `GET /v2/dataobjects/{appId}` returns the structured
claims.

### Priority 5 — DataSplitManifest DataObject kind + OpenLineage overlap check [DM-12, DM-13-5]

**Closes:** DM-12, DM-13-5

**Effort:** 1 sprint (DataObject kind + ShepardProvenanceOperator extension to assert
non-overlapping content hashes)

**Design dependencies:** `shepard-plugin-airflow` ShepardProvenanceOperator.

**Acceptance:** MFFD training data pipeline produces a `DataSplitManifest` DataObject
with `PASS` overlap check result; auditor can verify independence from the DataObject
detail view.

### Priority 6 — Ethics review status states [ORG-08, ET-01/ET-02]

**Closes:** ORG-08 (process to continuously assess ethics), ET-01 (ethics-based trustworthiness assessment routing), ET-02 (unfair bias review gate)

**Effort:** 0.5 sprint (status machine extension + `ethics-reviewer` Keycloak role)

**Design dependencies:** None — status machine extension is configuration.

**Acceptance:** A DataObject can be transitioned to `ETHICS_REVIEW` by a curator;
an `ethics-reviewer` role member can approve or reject with a mandatory comment.

### Priority 7 — BiasVarianceReport DataObject kind [LM-07]

**Closes:** LM-07

**Effort:** 0.5 sprint (DataObject kind + mlxtend Airflow step template)

**Design dependencies:** `shepard-plugin-airflow` ShepardWriteOperator.

**Acceptance:** MFFD anomaly detection training pipeline produces a
`BiasVarianceReport` DataObject with `biasEstimate`, `varianceEstimate`,
`totalError` attributes.

---

## Appendix A — Source Documents

1. EASA AI Concept Paper Proposed Issue 02 (February 2023) — primary source for all objective IDs and descriptions. URL: `easa.europa.eu/sites/default/files/dfu/easa-artificial-intelligence-concept-paper-issue-02-proposed.pdf`

2. EASA AI Roadmap 2.0 presentation — Guillaume Soudain, EASA AI Programme Manager (date: 2024). Source: `icao_easa_roadmap.txt` / `easa_roadmap2.txt` extracted from conference slides.

3. EASA AI Roadmap 2.0 Phase III — David Waller, EASA Representative Southeast Asia, ICAO APAC event 2024/2025. Source: `icao_easa_roadmap.txt`.

4. MLEAP project summary — Airbus Protect / LNE / Numalis, Horizon Europe. Source: `easa_mleap.txt`.

5. Shepard design docs: `aidocs/platform/86-ai-plugin-design.md`, `aidocs/platform/87-timeseries-appid-migration.md`, `aidocs/platform/30-mcp-plugin-design.md`.

6. Shepard feature matrix: `aidocs/44-fork-vs-upstream-feature-matrix.md`.

7. Provenance wire shape: `backend/src/main/java/de/dlr/shepard/v2/provenance/io/ActivityIO.java`.

8. Import manifest: `backend/src/main/java/de/dlr/shepard/v2/importer/io/ImportManifestIO.java`.

---

*Written 2026-05-21. Review against published CP2 Issue 02 (March 2024) recommended before any compliance submission.*
