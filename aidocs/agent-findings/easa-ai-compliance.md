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
| Development assurance (DA) family | ~60% evidence | MLflow covers training curves + params; gap on DA-09 safety case |
| Learning management (LM) family | ~55% evidence | MLflow covers LM-05/LM-06/LM-09; LM-07 bias-variance gap |
| Safety assessment (SA/ICSA) family | ~15% evidence | Static pre-deployment only; runtime monitoring absent |
| Explainability (EXP) family | ~20% evidence | Dev explainability via metadata only; operational explainability absent |
| Ethics (ET) family | ~30% evidence | FAIR-1 metadata, access control; no ethics review board tooling |
| Organisations (ORG) family | ~35% process | Audit log (F3), permission model; gaps on ORG-03/ORG-04 |
| **Overall (evidence supply)** | **~40% of CP2 objectives partially addressed** | Many objectives require non-tool organisational or model-level work |

The stack's strongest compliance story is **DM-13** (data quality and traceability) — the five MOCs map directly to Shepard features. The clearest headline gap is **runtime inference monitoring** (SA-01-3, EXP-14/15, ICSA-02) — neither Shepard nor ReBAR currently provides ODD-conformity checking during live inference.

At **Level 1A** (human assistance, AI acts as tool under full human decision authority) the evidence stack is credible for a Level 1A compliance case with gaps closed by organisational process. At **Level 1B and 2A/2B**, the runtime monitoring and explainability gaps are blocking and require dedicated infrastructure.

---

## 2. EASA AI Requirements Inventory

Organised by CP2's four trustworthiness building blocks plus the Organisations (ORG) provisions. Reference is to the objective ID used in the CP2 text. "AL" = Assurance Level (1–5, where AL1 is highest obligation); "L" = AI Level (1A/1B/2A/2B).

### 2.1 Building Block 1 — Trustworthiness Analysis

| Objective | Description | Min AL | Applies at |
|---|---|---|---|
| CO-01 | Define intended function, operational context, performance criteria | AL1 | All |
| CO-02 | Characterise operating domain (OD) and operational design domain (ODD) | AL1 | All |
| CO-03 | Identify OD/ODD parameters and their acceptable ranges | AL1 | All |
| CO-04 | Document ODD coverage and corner cases | AL2 | 1B+ |
| SA-01-1 | Perform safety risk assessment of AI application | AL1 | All |
| SA-01-2 | Identify failure conditions and their severity classifications | AL1 | All |
| SA-01-3 | Define monitoring capabilities to detect OD/ODD conformity at runtime | AL2 | 1B+ |
| ET-01 | Human oversight capability per AI level | AL1 | All |
| ET-02 | Competency gap assessment and mitigation | AL2 | 1B+ |
| ET-03 | Unfair bias avoidance — training data audit | AL2 | 1B+ |
| ET-04 | Societal impact assessment | AL3 | 2A+ |
| ET-05 | Personal data privacy compliance (GDPR alignment) | AL2 | 1B+ |
| ET-06 | Environmental impact consideration | AL4 | 2B+ |
| ET-07 | Risk of de-skilling consideration | AL3 | 2A+ |
| ET-08 | Interaction with AI systems audit | AL3 | 2A+ |
| IS-01 | Information security risk assessment | AL1 | All |
| IS-02 | Adversarial robustness assessment | AL2 | 1B+ |
| IS-03 | Data poisoning risk assessment | AL2 | 1B+ |

### 2.2 Building Block 2 — AI Assurance (Learning Assurance + Development Explainability)

#### Data Management (DM)

| Objective | Description | Min AL |
|---|---|---|
| DM-01 | Data management plan exists and is maintained | AL1 |
| DM-02 | Data sources identified and documented | AL1 |
| DM-03 | Data collection process documented | AL1 |
| DM-04 | Data labelling process documented and controlled | AL2 |
| DM-05 | Data pre-processing steps documented | AL2 |
| DM-06 | Data provenance traced to original source | AL1 |
| DM-07 | Data schema defined and enforced | AL2 |
| DM-08 | Data version control applied | AL2 |
| DM-09 | Data access control enforced | AL1 |
| DM-10 | Data retention and archival policy defined | AL3 |
| DM-11 | OOD (out-of-distribution) data handling defined | AL2 |
| DM-12 | Data split into three independent sets: training / validation / test | AL1 |
| DM-13 | Five mandatory quality criteria for each set: | AL1 |
| DM-13-1 | — Completeness: data set covers the full ODD | AL1 |
| DM-13-2 | — Representativeness: distribution matches operational distribution | AL1 |
| DM-13-3 | — Accuracy/correctness: labels and measurements are correct | AL1 |
| DM-13-4 | — Traceability: each sample traceable to origin and processing steps | AL1 |
| DM-13-5 | — Independence: training, validation, test sets are non-overlapping | AL1 |
| DM-14 | Data augmentation strategy documented and validated | AL3 |
| DM-15 | Synthetic data use documented and bounded | AL3 |

#### Learning Management (LM)

| Objective | Description | Min AL |
|---|---|---|
| LM-01 | ML model architecture selection justified | AL2 |
| LM-02 | Hyperparameter selection process documented | AL2 |
| LM-03 | Training environment documented (hardware, framework versions) | AL2 |
| LM-04 | Training reproducibility demonstrated | AL2 |
| LM-05 | Training curves (loss/cost) recorded and reviewed | AL2 |
| LM-06 | Model versioning applied | AL1 |
| LM-07 | Bias-variance trade-off assessed | AL2 |
| LM-08 | Generalisation assessment performed | AL1 |
| LM-09 | Performance evaluated on independent test set | AL1 |
| LM-10 | Performance thresholds defined pre-training | AL2 |
| LM-11 | Model update / re-training policy defined | AL2 |
| LM-12 | Transfer learning justification if applied | AL3 |

#### Implementation (IMP)

| Objective | Description | Min AL |
|---|---|---|
| IMP-01 | Deployed model reproducibly matches trained model | AL1 |
| IMP-02 | Runtime environment documented | AL2 |
| IMP-03 | Integration test plan and evidence | AL2 |
| IMP-04 | Deployment pipeline change management | AL2 |

#### Development Assurance (DA)

| Objective | Description | Min AL |
|---|---|---|
| DA-01 | Development process documented | AL1 |
| DA-02 | Requirements traceability (requirements → model → tests) | AL2 |
| DA-03 | Tool qualification for ML-specific tools | AL3 |
| DA-04 | Configuration management applied | AL2 |
| DA-05 | Problem reporting and corrective action process | AL2 |
| DA-06 | Verification evidence documented | AL2 |
| DA-07 | Software quality plan | AL2 |
| DA-08 | Independent review of ML artefacts | AL3 |
| DA-09 | Safety case document (assurance case referencing all evidence) | AL2 |

#### Development Explainability (EXP-01 — EXP-04)

| Objective | Description | Min AL |
|---|---|---|
| EXP-01 | Input feature importance analysis | AL2 |
| EXP-02 | Model behaviour explanation at development time | AL2 |
| EXP-03 | Confidence / uncertainty estimation for predictions | AL2 |
| EXP-04 | Documentation of explainability methods used | AL2 |

### 2.3 Building Block 3 — Human Factors for AI (Operational Explainability + HAT)

| Objective | Description | Min AL | Applies at |
|---|---|---|---|
| EXP-05 | Human-readable output explanation available | AL2 | 1B+ |
| EXP-06 | Confidence indication surfaced to operator | AL2 | 1B+ |
| EXP-07 | Override mechanism available and tested | AL1 | All |
| EXP-08 | Explanation tailored to operator role | AL3 | 2A+ |
| EXP-09 | Explanation available within operational latency | AL2 | 1B+ |
| EXP-10 | Explanation not misleading under distribution shift | AL3 | 2A+ |
| EXP-11 through EXP-17 | Advanced HAT explainability (collaboration, delegation) | AL4–5 | 2B+ |
| HA-01 | Human-AI teaming specification | AL3 | 2A+ |
| HA-02 | Handover / takeover protocol defined | AL3 | 2A+ |
| HA-03 | Situation awareness maintained | AL3 | 2A+ |
| HA-04 | De-skilling mitigation strategy | AL4 | 2B+ |

### 2.4 Building Block 4 — AI Safety Risk Mitigation

| Objective | Description | Min AL |
|---|---|---|
| ICSA-01 | Record operational data for continuous safety assessment | AL2 |
| ICSA-02 | Continuous safety assessment process from operational data | AL2 |
| ICSA-03 | ODD drift detection and reporting | AL3 |
| ICSA-04 | Model degradation detection | AL3 |
| ICSA-05 | Re-training trigger criteria defined | AL3 |

### 2.5 Organisations (ORG)

| Objective | Description | Min AL |
|---|---|---|
| ORG-01 | Responsible organisation designated | AL1 |
| ORG-02 | Competency requirements defined for ML roles | AL2 |
| ORG-03 | Continuous safety monitoring process owner designated | AL2 |
| ORG-04 | Audit trail for all decisions affecting ML assurance | AL2 |
| ORG-05 | Change management process for post-deployment updates | AL2 |
| ORG-06 | Incident reporting process defined | AL2 |
| ORG-07 | Training and awareness programme | AL3 |
| ORG-08 | Ethics review board or equivalent body | AL3 |

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
| DA-04 configuration management | Version history (PV1a/PV1b); git integration design (G1, in design) | Partial |
| DA-05 problem reporting | DataObject status + Predecessor chain for NCR sub-objects (see `aidocs/agent-findings/manufacturing-quality.md`) | Partial |
| DA-09 safety case | No structured assurance case template; DataObject collection can store the document but with no schema | Gap |
| IMP-01 model reproducibility | MLflow model registry (ReBAR) + Shepard FileReference for serialised model artefact; `aiGenerated` flag on output | Partial |

### 3.3 LM Family

| CP2 Objective | Shepard Feature | Status |
|---|---|---|
| LM-06 model versioning | `aiGenerated` flag on FileReference/DataObject; PV1a version chain; MLflow (ReBAR) primary | Partial |
| LM-09 test set evaluation | AI1c `qualityScore` ∈ [0,1] on TimeseriesReference (backend only, no UI yet) | Dev-track |

### 3.4 EXP / Operational explainability

| CP2 Objective | Shepard Feature | Status |
|---|---|---|
| EXP-07 override mechanism | Human is always the decision authority for DataObject status; no AI autopromote | Structurally satisfied |
| EXP-05/06 confidence surfacing | `aiGenerated` flag visible in DataObject UI; no confidence score surface | Gap |

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

**Objectives:** SA-01-3, EXP-14/15, ICSA-02, ICSA-03, ICSA-04

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
  SA-01-3 compliance: ODD_conformity_flag per call + alert on violation
```

External tools to wrap: Evidently AI (open-source, MIT, Python, active), NannyML
(EU-funded, open-source, LGPLv3), Arize AI (commercial, has a free tier).
Evidently is the lowest-friction option given the ReBAR Python environment.

### 6.2 Development explainability gap (BLOCKING for Level 1B AL2)

**Objectives:** EXP-01, EXP-02, EXP-03, EXP-04

**The gap:** CP2 requires feature importance analysis, model behaviour explanation,
and uncertainty/confidence estimation at development time. MLflow can store these as
logged artefacts but provides no structure or enforcement.

**Minimum closure:** Add `ExplainabilityReport` DataObject kind to `shepard-plugin-ai`:

```
ExplainabilityReport:
  attributes:
    method: "SHAP" | "LIME" | "integrated_gradients" | other
    framework_version: string
    feature_importance_json: MinIO FileReference (JSON)
    global_explanation_plot: MinIO FileReference (PNG/HTML)
    confidence_calibration_plot: MinIO FileReference
  linked via PREDECESSOR_OF to the model DataObject (FileReference)
```

SHAP (SHapley Additive exPlanations) is the current best-practice method for tabular
and timeseries models in manufacturing contexts — compatible with scikit-learn,
XGBoost, and PyTorch (via captum). ReBAR can log SHAP values as MLflow artefacts;
ShepardWriteOperator can write the structured report to Shepard.

### 6.3 Operational explainability gap (BLOCKING for Level 1B AL2, EXP-05/06)

**The gap:** The operator-facing UI must surface confidence indication and
human-readable explanation for each AI prediction. The `aiGenerated` flag marks
outputs but provides no confidence score or explanation.

**Minimum closure:**
- Extend `shepard-plugin-ai` `AiActivity` node to carry `confidenceScore` (float
  0–1), `explanationText` (short natural language), `oddConformityFlag` (bool)
- Surface these in the DataObject detail view alongside the `aiGenerated` badge
- At Level 1A this is optional (human reviews anyway); at Level 1B it is AL2 mandatory

### 6.4 Safety case document structure (DA-09)

**The gap:** CP2 requires a structured assurance case (safety case) document that
references all evidence artefacts. Shepard can store a PDF but provides no schema.

**Minimum closure:** Define a `SafetyCaseDocument` DataObject kind with required
attributes: `aiLevel`, `assuranceLevel`, `coverageClaimsJson`, plus a Collection
convention that groups all evidence DataObjects under a "Assurance Evidence"
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

### 6.7 Ethics review board tooling (ORG-08, ET-03)

**The gap:** CP2 requires an ethics review body and unfair bias avoidance. Shepard has
no workflow routing to an ethics review group.

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
- SA-01-3 runtime monitoring: *not required at Level 1A (monitoring of OD/ODD applies
  when AI output has direct operational consequence)*
- EXP-01 through EXP-04 (development explainability): covered by ExplainabilityReport
  DataObject (gap 6.2 closure)

**Verdict:** With gap 6.2 (ExplainabilityReport) and gap 6.4 (SafetyCaseDocument)
closed, the stack is credible for a Level 1A compliance case at AL3–AL4. The remaining
gaps (runtime monitoring, operational explainability) are not blocking at this level.

**Concrete Level 1A application:** AI1c channel quality scoring (`qualityScore` on
TimeseriesReference). The model runs at ingest time under human review of flagged
channels; no automated rejection. This is a canonical Level 1A use case.

### Level 1B — Cognitive augmentation (AI output directly informs human decision)

**CP2 scope:** DA, DM, LM, IMP at AL2–AL3; SA, EXP at AL2; ORG at AL2.

**Stack coverage at Level 1B:** ~45% of applicable objectives addressed

New requirements at Level 1B:
- SA-01-3 (runtime ODD monitoring): **gap** — blocks Level 1B
- EXP-05/EXP-06 (confidence surfacing to operator): **gap** — blocks Level 1B
- EXP-07 (override): structurally satisfied by human decision authority
- ET-02 (competency gap): **organisational process gap** — not a tool gap

**Verdict:** Not credible without closing gaps 6.1 (inference monitor) and 6.3
(operational explainability UI). Both are buildable. With those two closed, Level 1B
is achievable for anomaly detection on MFFD process data.

**Concrete Level 1B application:** Anomaly detection on AFP welding timeseries.
The model flags a candidate anomaly; the process engineer reviews the flagged channel
with the confidence score and the SHAP explanation before deciding to halt the
process. Level 1B because the AI output directly informs a consequential decision
but the human retains authority.

### Level 2A/2B — Human-AI teaming (AI has partial/full operational authority sharing)

**CP2 scope:** All of the above at AL1–AL2; plus HA-01 through HA-04; EXP-08
through EXP-17; ET-03/ET-04/ET-07/ET-08; ICSA-03/04/05.

**Stack coverage at Level 2A/2B:** ~25% of applicable objectives addressed

Additional blocking gaps at Level 2A+:
- HA-01/02/03 (HAT specification, handover, situation awareness): **major new work** —
  these are operational system design requirements, not data platform features
- EXP-08/10 (role-tailored explanation, non-misleading under distribution shift):
  require runtime explanation infrastructure beyond gap 6.3
- ICSA-03/04 (ODD drift detection, model degradation detection): **gap** — part of
  the inference monitor plugin but requires statistical process control capability

**Verdict:** Level 2A/2B is out of scope for Shepard+ReBAR in the current design.
HAT requirements drive the design of the operational system (cockpit, control room,
shop floor UI), not the data platform. Shepard contributes evidence to a Level 2
compliance case but cannot substitute for the operational system design.

---

## 8. Roadmap Recommendations

Ordered by: closes a blocking compliance gap first, then by effort-to-value ratio.

### Priority 1 — `shepard-plugin-inference-monitor` [BLOCKING for Level 1B]

**Closes:** SA-01-3, ICSA-01, ICSA-02, EXP-14/15

**Effort:** 3–4 sprints (Evidently integration + InferenceCallRecord DataObject kind
+ ODD spec schema + admin config + Airflow DAG template)

**Design dependencies:** TS-IDa (appId on Timeseries nodes — needed to address channels
without 5-tuple) must ship first; `shepard-plugin-ai` capability registry must ship
first (STRUCTURED capability used for structured inference calls).

**Acceptance:** A new MFFD AFP anomaly detection inference session logs
`InferenceCallRecord` DataObjects; the scheduled drift check DAG writes a
`DatasetDriftReport` DataObject; admin can review ODD conformity per inference call
from the DataObject detail view.

### Priority 2 — ExplainabilityReport DataObject kind + ShepardWriteOperator extension [BLOCKING for Level 1B EXP-01 through EXP-04]

**Closes:** EXP-01, EXP-02, EXP-03, EXP-04

**Effort:** 1–2 sprints (DataObject kind definition + import manifest support +
DataObject detail view extension + ReBAR Airflow step template using SHAP)

**Design dependencies:** `shepard-plugin-airflow` ShepardWriteOperator (partially
designed). Can ship the DataObject kind without the Airflow integration.

**Acceptance:** MFFD anomaly detection model produces a `ExplainabilityReport`
DataObject with SHAP feature importance JSON and summary plot, linked via
`PREDECESSOR_OF` to the trained model FileReference.

### Priority 3 — Confidence score + ODD conformity flag in DataObject UI [BLOCKING for Level 1B EXP-05/06]

**Closes:** EXP-05, EXP-06

**Effort:** 0.5–1 sprint (extend `AiActivity` node + DataObject detail view badge)

**Design dependencies:** `shepard-plugin-inference-monitor` (Priority 1) populates
`oddConformityFlag`; `shepard-plugin-ai` `AiActivity` already carries `promptHash` —
extend to carry `confidenceScore`.

**Acceptance:** DataObject detail view shows `[AI-generated] Confidence: 0.87 | ODD: IN` badge.

### Priority 4 — SafetyCaseDocument DataObject kind [required for DA-09 at all levels]

**Closes:** DA-09

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

### Priority 6 — Ethics review status states [ORG-08, ET-03]

**Closes:** ORG-08 (tooling contribution), ET-03 (unfair bias routing)

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
