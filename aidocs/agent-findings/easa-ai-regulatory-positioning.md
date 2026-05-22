---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# EASA AI Regulatory Positioning for Shepard

*Author: regulatory analyst (Claude agent), 2026-05-21.
Primary source: EASA Concept Paper Issue 02, "Guidance for Level 1 & 2 machine
learning applications," March 2024, EASA. Page numbers and objective IDs cite
the 283-page PDF at <https://www.easa.europa.eu/en/downloads/139504/en>.
A companion gap-analysis already exists at
`aidocs/agent-findings/easa-ai-compliance.md`; this document does not duplicate
it — it focuses on positioning, building-block alignment, and the
F(AI)²R / Article 50 angle.*

## TL;DR

EASA's AI trustworthiness framework (Concept Paper Issue 2, March 2024) is the
strictest evidence-discipline regime in European aviation and the structural
template that DLR aerospace projects working with ML will be judged against
once RMT.0742 turns its **anticipated MOC** into adopted MOC (target: 2028 per
the AI Roadmap 2.0). Most of what EASA requires for the *Data Management* family
(DA-01 through DA-09, DM-01 through DM-08) is a research-data-management
problem in disguise — capture DQRs, version data sets, prove independence
between train/val/test, document data lineage, retain the artefacts. Shepard's
v2 model (appId-stable identifiers, Predecessor/Successor lineage, PROV-O via
PROV1a, SHACL-validated annotations, F(AI)²R AI-provenance capture) lines up
cleanly with that supply side. Shepard does **not** provide the assurance
properties themselves (generalisation bounds per LM-04, runtime ODD monitoring
per EXP-05/06/07, HAT capability per HF-01…HF-11) — those are model-level and
operational work outside the platform's mandate. The honest positioning is:
*Shepard is a credible evidence platform for the DM/DA families and the AI
provenance/recording families (EXP-08/09); it is not, and should not claim to
be, an assurance engine.*

## 1. The EASA AI regulatory landscape

| Document | Status | Audience | Effective |
|---|---|---|---|
| **EASA AI Roadmap 2.0** (April 2024) | Strategy | EASA + industry programme managers | living document; target AI policy finalisation **2028** |
| **EASA AI Concept Paper Issue 02** ("Guidance for Level 1 & 2 ML applications", March 2024) | **Guidance** — *anticipated MOC*, not yet adopted | Applicants developing aviation ML, certifying authorities | Already used by EASA in early certification engagements (Innovation Network); becomes hard MOC via RMT.0742 |
| **EASA AI Concept Paper Issue 01** (April 2021) | Predecessor (Level 1 only) | Same | Superseded by Issue 02 |
| **MLEAP Final Report** (Airbus Protect / LNE / Numalis, July 2024) | Horizon Europe research deliverable | Industry, standardisation bodies | Feeds into Issue 03 + EUROCAE WG-114 |
| **EUROCAE/SAE WG-114/G-34** | Standards under development | All aviation ML applicants | Target adoption 2026–2027 |
| **EUROCAE/RTCA WG-72/SC-216** | Standards under development (security for AI) | Same | Same window |
| **RMT.0742** ("Artificial intelligence trustworthiness") | EASA Rulemaking Task | Aviation regulator + industry | Folds Concept Paper objectives into AMC under EU 2018/1139 |
| **EU AI Act (Regulation (EU) 2024/1689)** | Adopted, in force 1 Aug 2024; full applicability staggered | Cross-sector | **Article 50** transparency obligations effective **2 Aug 2026** |

Two things matter about this list:

1. **The Concept Paper is not yet binding.** Every objective is "anticipated MOC."
   But EASA is using it in real certification dialogues now (MLEAP, Wayfinder,
   D-AIM projects), and the rulemaking trajectory — RMT.0742 → AMC under Part 21,
   ED Decisions for Air Ops, AMC/GM for ATM/ANS — is settled. Treating the
   Concept Paper as "the law you will be audited against in 2028" is the safer
   default for any DLR project that may aim at any aviation deployment.
2. **The Concept Paper is aviation-specific but generalisable.** Its data-management
   discipline (DQRs; independence proofs; pipeline traceability) is essentially
   ISO 5259 / ISO 8000 / DIN EN 9100 §8.4 re-expressed for ML. Shepard's
   neighbours (DLR ZLP MFFD; DLR Lampoldshausen LUMEN) will inherit these
   expectations whether or not their projects ever enter a Part 21 process —
   because the Clean Aviation JU funding contracts already reference EASA's
   AI roadmap as "best practice" and Horizon Europe AERO calls cite Issue 02
   explicitly.

DLR-LY (DLR Aviation Systems, Cologne) is the institutional touchpoint for
direct engagement with EASA. Any Shepard positioning claim that references
Issue 02 should be reviewed with DLR-LY before being put in a proposal —
they will know which interpretation EASA's certification team currently
favours.

## 2. The trustworthiness building blocks — and what they need from RDM

Issue 02 §B.2 fig. 2 + §B.6 + Chapter C identify **four** trustworthiness
building blocks (down from the seven of Roadmap 1.0; the previous "AI
explainability," "Learning Assurance," and "Safety Risk Mitigation" were
restructured). For each: the EASA wording, the data-management implication,
and where Shepard sits.

### 2.1 AI Trustworthiness Analysis

> "Creates an essential link between the safety risk-based approach and the
> ethical concerns…" (Issue 02, p. 11)

Covers four sub-assessments: characterisation of the AI application
(Objectives CO-01…CO-06, classification CL-01), safety assessment of ML
applications (SA-01 et seq.), information security considerations, and
ethics-based assessment (ET-01…ET-08).

**Data-management implications.** The ConOps (CO-04), the classification
record (CL-01), the safety assessment (SA-01) and the eight ethics objectives
each generate a *durable artefact* that must be retrievable for the life of
the deployment. They are not running data — they are versioned documents
keyed to a specific configuration of the AI system. This is exactly the
shape Shepard's `:DataObject` + `:DataObjectReference` ladder fits.

**Shepard fit.** Direct: `:Collection` for the application; `:DataObject`s
for ConOps, classification, safety assessment, ethics review. The
`:Predecessor`/`:Successor` chain captures the iteration history (Issue 02
fig. 13 — "iterative nature of the learning assurance process"). Gap: no
template enforcing the *required content* of these artefacts. The SHACL
template work in `aidocs/semantics/95` is the structural fix — define a
SHACL shape per Concept-Paper artefact kind and the validation gateway
rejects an incomplete safety assessment at upload time.

### 2.2 AI Assurance — Learning Assurance + Development & Post-Ops Explainability

> "Address the AI-specific guidance pertaining to the AI-based system and
> AI/ML constituent development…" (p. 11)

This is the **W-shaped process** building block. Objectives DA-01…DA-10
(planning, requirements, architecture, validation), DM-01…DM-08 (data
management), LM-01…LM-16 (learning management), IMP-01…IMP-12
(implementation), EXP-01…EXP-09 (development + post-ops explainability).

**Data-management implications.** Every objective in the W produces an
artefact — a plan, a requirement set, a data set, a model checkpoint, a
verification report, a transformation log. Issue 02 fig. 18 (p. 63) shows
the train/val/test split *threaded* through the W; the platform that
manages the W must be able to reconstruct, at audit time, exactly which
data went into which training run, with which transformations, against
which DQRs. This is the heaviest RDM load in the entire framework.

**Shepard fit.** Strong on storage; weak on enforcement.
`:TimeseriesContainer` + `:FileContainer` + `:StructuredDataContainer` cover
every artefact type. The appId-stable identifier (post-L2a/b in
`aidocs/platform/87-timeseries-appid-migration.md`) is *exactly* the level
of identification Issue 02 §G.1 implies when it says "the ability to
determine the origin of the data (traceability)." Gaps: (a) no built-in
enforcement that train/val/test are independent (Objective DM-06); the
forthcoming SHACL templates can carry this constraint as
`sh:disjoint`-style invariants. (b) No first-class concept of a "model
checkpoint" — currently shoehorned into `:FileContainer`. A
`shepard-plugin-mlflow` (or native integration) would close this. The
existing `easa-ai-compliance.md` analysis rates this family at ~55–75%
evidence supply, which matches.

### 2.3 Human Factors for AI

> "Introduces the necessary guidance to account for the specific impact of
> using AI-based systems on the end user and the related risks…" (p. 11)

Covers operational explainability (EXP-01…EXP-09 in §C.3.2 cross-link to
HF), HAT (HF-01…HF-11), modality of interaction, error management, failure
management. The **guarantor agent** concept lives here — see §5 below.

**Data-management implications.** The Concept Paper requires the AI system
to be *designed* with capabilities (HF-01: "ability to build its own
individual situation representation"; HF-02: reinforce end user's situation
awareness; HF-04: dual-validation for high-stakes decisions; HF-05/HF-06:
collaborative behaviour in normal/abnormal situations). The evidence each
HF objective generates is a *design artefact* (architectural decision
record) plus a *test record* (validation that the capability works).
Shepard handles both as `:DataObject`s — but does not host the AI system
itself.

**Shepard fit.** Indirect. Shepard is the evidence vault for the design
records and the test records. It is not (and should not be) the runtime
host of the HAT capabilities.

### 2.4 AI Safety Risk Mitigation

> "Considers that we may not always be able to open the 'AI black box' to
> reach the level of confidence sought for the other building blocks…" (p. 11)

Objectives in §C.5. Catch-all for residual risk after the other three
building blocks have done what they can. Issue 02 is honest that this is
where the *open questions* live — what mitigations are credible when LM-04
generalisation bounds cannot be established at high assurance level.

**Data-management implications.** Mitigation actions themselves (a
monitoring procedure, an alerting threshold, a procedural workaround) are
documents. Their *operation* generates events. Both are versioned and
retained.

**Shepard fit.** Documents → `:DataObject`. Runtime events → an integration
target, not a Shepard core feature.

## 3. The W-shaped process and its data trail

ASCII recapitulation of Issue 02 fig. 11/12 (pp. 50–51), with the artefacts
each leg generates and Shepard's mechanism for hosting them.

```
                       Requirements                Verification
                       (DA-02, DA-03,             (LM-14, LM-16,
                        DA-04, DA-05)              IMP-12, DM-08,
   System level         /\                          DA-10)         System level
        \              /  \                          /\              /
         \   DATA MGMT/    \   LEARNING MGMT        /  \    IMPLEM. /
          \    (DM-01..    \    (LM-01..LM-15)    /     \    (IMP-01..
           \    DM-07)      \                    /       \    IMP-11)
            \   ─ ─ ─ ─ ─    \                  /     ─ ─ ─\─ ─ ─ ─
             \                \                /              \
              \  Data sets     \ Trained model/  Inference     \
               \  Train/Val/    \ + perf.      / model +        \
                Test (DM-06)     \ metrics    / quantised        \
                                  V  (LM-09) V  artefact (IMP-04) V
                              ML model      Verified ML model
                              architecture  ready for deployment
                              (LM-01,2,3)
```

Artefacts generated, by W leg (Issue 02 §C.3):

| W leg | Artefacts | Shepard mechanism |
|---|---|---|
| Plans (DA-01) | Learning Assurance Plan (LAP), DM plan | `:DataObject` (kind = `learning_plan`) |
| Requirements + Arch (DA-02, DA-03, DA-06) | ODD spec, DQR set, preliminary architecture | `:DataObject` + `:StructuredData` |
| Data management (DM-01..DM-07) | Raw collected data, labelled data, pre-processed, normalised; train/val/test split; verification reports | `:FileContainer` / `:TimeseriesContainer` / `:StructuredData`; appId stable |
| Learning management (LM-01..LM-15) | Model architecture, training curves, hyperparameter set, performance metrics, generalisation bound report, stability + robustness verification | MLflow integration target; for evidence retention, `:FileContainer` |
| Implementation (IMP-01..IMP-11) | Quantised/converted model, performance evaluation on target, stability + robustness on target | Same as LM |
| Verification + closure (DM-08, LM-16, IMP-12, DA-10) | Requirements-based verification matrix; signed-off LAP | `:DataObject` + (planned) workflow status |
| Post-ops recording (EXP-04..EXP-09) | Runtime logs; deviation records; chronological I/O sequences; FDM-style data | TimescaleDB via `:TimeseriesContainer`; this is the ingest fit |

The honest assessment: the **W is a directed acyclic graph of
artefacts**, with explicit iteration cycles between data management ↔
learning management ↔ safety assessment (Issue 02 fig. 13). Shepard's
Predecessor/Successor relation expresses iteration cycles natively. The
seven-row table above is feasibility-checked against the LUMEN seed
(15 test runs with predecessor chains; TR-004 anomaly → investigation →
TR-005 hold/repair → TR-006 re-test) — Shepard already models the right
*shape*, just not the right *required content*.

## 4. Mapping EASA objectives to Shepard capabilities

Selected objectives, focused on the families where Shepard genuinely has
something to say. (For exhaustive enumeration see Issue 02 Annex H —
283 pp.; for a per-objective compliance scoring see
`easa-ai-compliance.md`.)

| EASA Obj. | Description (paraphrased, see Issue 02 p. ref) | Shepard mechanism | Status |
|---|---|---|---|
| **DA-01** (p. 52) | Document learning assurance process plan | `:DataObject` + planned `learning_plan` template | gap: no template |
| **DA-02** (p. 53) | Capture AI/ML constituent requirements (safety, security, functional, operational, interface) | `:DataObject` + SHACL template | gap: template needed |
| **DA-03** (p. 53) | Define ODD parameter set; trace to OD | `:StructuredData` keyed by appId | shipped (shape) / gap (vocabulary) |
| **DA-04** (p. 56) | Capture **DQRs**: relevance, origin/lineage, annotation, format/accuracy/resolution, **traceability**, integrity, **completeness**, **independence** | DQR set as `:StructuredData`; lineage = Predecessor edges + PROV-O Activities; immutable appId | **best alignment in the entire CP2** |
| **DM-01** (p. 60) | Identify data sources and collect per DQRs | Container ingest; appId on every record | shipped |
| **DM-02-SL / DM-02-UL** (p. 60, 64) | Verify labelled data against DQRs | Annotation system (post-SHACL templates) | partial |
| **DM-03..DM-05** (p. 61–62) | Define + document pre-processing, feature engineering, normalisation | `:StructuredData` records of transformations | shape ok, content discipline missing |
| **DM-06** (p. 63) | Distribute data into train/val/test sets meeting independence DQR | Three `:Collection`s with cross-references | gap: no enforcement of independence |
| **DM-07** (p. 64) | Verify data throughout the pipeline (completeness, representativeness, balance, accuracy, transparency, freshness, integrity, timeliness) | Validation hook (template-driven) | gap (planned via SHACL) |
| **LM-04** (p. 73) | Provide quantifiable generalisation bounds | n/a — this is model-level work | not in Shepard scope |
| **LM-05/06/09** (p. 80–84) | Document training results, optimisation, performance evaluation | `:FileContainer` for MLflow exports | partial |
| **EXP-01..EXP-03** (p. 88–90) | Stakeholder identification, explainability methods at item/constituent/system level | `:DataObject` + SemanticAnnotation | shape ok |
| **EXP-04** (p. 91) | Design AI system to deliver explanations | runtime AI capability — n/a | not Shepard |
| **EXP-05/06/07** (p. 91) | ODD-conformity monitoring, OoD detection, output-confidence monitoring at runtime | runtime AI capability — n/a | not Shepard |
| **EXP-08** (p. 91) | Recorded output of EXP-05/06/07 monitoring | `:TimeseriesContainer` ingest | **strong fit** |
| **EXP-09** + MOC-09-1..5 (p. 92–95) | Means to **record operational data** that is necessary to explain post-ops the behaviour of the AI system and its interactions with the end user; means to **retrieve** that data; auto-start/stop; sufficient to detect deviations, accurately reconstruct chronological I/O, monitor trends; access for FDM-style monitoring; for accident investigation (with crash-protected medium for airborne case) | TimescaleDB ingest; appId-keyed event logs; query API | **best post-ops alignment** |
| **SA-02** (p. 30) | Identify data to be recorded for continuous safety assessment | List in `:StructuredData` | shape ok |
| **SA-03** (p. 30) | Define the monitoring criteria | List in `:StructuredData` | shape ok |
| **ET-01..ET-08** (p. 43) | Ethics-based assessment (people, privacy, fairness/bias, awareness, environment, skills, de-skilling) | `:DataObject` per assessment | gap: template |
| **HF-01..HF-11** (p. 106–135) | HAT capabilities | runtime AI capability — n/a | not Shepard |

**Three observations from this table.** First, the **DM/DA cluster is
Shepard's natural territory** — these are RDM objectives wearing a
certification hat. Second, the **EXP-08/09 cluster is the
underappreciated win**: post-ops recording of AI behaviour for FDM-style
monitoring + accident investigation is exactly what a high-cadence
TimescaleDB-backed time series store does well, and it is one of the
*hardest* parts of CP2 for traditional aerospace tooling (cockpit voice
recorders + flight data recorders weren't designed for ML I/O streams).
Third, **everything model-internal is correctly out of scope**: LM-04
generalisation bounds, EXP-04 runtime explanations, HF-* runtime
capabilities — Shepard hosts the *evidence about them*, never the
capability itself.

## 5. The guarantor agent and F(AI)²R provenance

The Concept Paper uses *guarantor* sparingly and not as a hard technical
term. The role appears in §C.4.2 ("Human-AI Teaming") and the HF-* family:
the **end user is the guarantor of the safety of the operation** at AI
Level 1 and 2A; at AI Level 2B the responsibility is *shared*; at Level 3
the AI system is the guarantor with humans in monitoring/safeguard role.

The data trail a guarantor needs is exactly the EXP-08 / EXP-09 trail
plus the HF-01 situation-representation trail: at any moment of operation,
they must be able to reconstruct what the AI knew, what the AI did, why,
and whether the human authority gate operated as designed.

This is where **F(AI)²R** (Krebs, 2026; reference TTL at
<https://github.com/noheton/f-ai-r>) maps in. F(AI)²R extends PROV-O with:

- `fair2r:AIAgent ⊑ prov:SoftwareAgent` — every AI model that participates
  in any write or audit becomes a named individual.
- `fair2r:AuthoringPass ⊑ prov:Activity` — every AI-driven write becomes a
  typed Activity with timestamps + agent + prompt + transcript.
- `fair2r:AuditPass ⊑ prov:Activity` — every AI-driven read where the
  caller self-identifies as AI becomes a typed Activity.
- `fair2r:Claim ⊑ prov:Entity` with a SHACL-enforced invariant
  `prov:wasGeneratedBy minCount 1` — no claim without provenance.
- A **verification ladder** (unverified → needs-research → lit-retrieved
  → ai-confirmed → human-confirmed → source-vendored → lit-read) — the
  property `fair2r:verificationState` carries the rung explicitly.

This is *not* identical to what EASA requires of an aviation guarantor —
the EASA guarantor is a *human end user* operating in a defined ODD;
F(AI)²R covers *AI agents acting on shepherded data*. But the
**vocabulary fits**: an aviation guarantor agent in a fielded system
would naturally produce the same kind of typed Activity edges that
F(AI)²R produces in research data work, and the EXP-09 retention
requirements ("recorded data … retrieve it … reconstruct chronological
sequence of inputs and outputs") are byte-compatible with F(AI)²R's
PROV-O edges plus a Transcript entity per session.

**Honest caveat.** F(AI)²R is a research proposal — not adopted as a
standard, no W3C status, no aviation regulator endorsement, ~one
implementation (Shepard) plus the reference repo. Calling it
"EASA-aligned" overstates it. The defensible claim is: *F(AI)²R's
PROV-O extension produces, by construction, the kind of typed
Activity record that EXP-09 MOC-2 and MOC-4 require for post-ops
behavioural reconstruction; if a future EASA AI guarantor-agent
specification adopts a PROV-O baseline (a reasonable bet given EU
data-sovereignty preferences), F(AI)²R interoperates with it natively.*

## 6. AI Explainability

EASA splits explainability into two views (Issue 02 §B.6.2):

- **Development explainability** (Objectives EXP-01..EXP-03): why the
  system makes the decisions it makes, for engineers + certifiers.
  Static methods at AI/ML item / constituent / system level. Methods are
  *recorded* as documents.
- **Operational explainability** (Objectives in §C.4.1, esp. EXP-04..
  EXP-09): explanations *delivered at runtime* to the end user; the
  AI system must be designed to provide them (EXP-04), monitor itself
  (EXP-05, 06, 07), and record those monitors (EXP-08).

Forms of explanation EASA contemplates: counterfactuals, saliency,
attention maps, surrogate models, rule-extraction, confidence indicators,
ODD-conformity signals. **Prompt-and-response capture** in the F(AI)²R
sense is not mentioned in Issue 02 — that style of explanation belongs to
a generative-AI use case that the Concept Paper deliberately scopes
*out* (Issue 02 §B.3 limits scope to "ML applications," excluding
foundation models / LLMs as of Issue 02).

**Implication for Shepard.** Shepard's contribution to operational
explainability is **archival + queryable + replayable**, not
runtime-rendered. It hosts the records that *enable* an explainability
audit, not the explanations themselves. This is the right scope.

## 7. Continuous safety / monitoring (post-deployment)

Issue 02 §C.2.2.4 ("Continuous safety assessment") + §C.3.2.7 ("AI data
recording capability") + the AI Safety Risk Mitigation building block.
Three distinct recording uses, three distinct retention regimes (Issue 02
p. 92–95):

1. **Safety management of operations** (end-user organisation's SMS) —
   FDM-style, not crash-protected, performance/deviation monitoring.
2. **Continuous safety assessment by the applicant** — drift detection,
   ODD-conformity check over time, in-service performance.
3. **Accident/incident investigation** (ICAO Annex 13 / Regulation
   996/2010) — crash-protected for airborne case; chronological I/O
   sequence; communications between HAT members.

Shepard's TimescaleDB layer is a credible fit for (1) and (2). For (3),
the crash-protected medium is out of scope and stays out of scope —
that is a flight-data-recorder vendor's problem. Shepard's value to (3)
is as the *ground-side mirror* where the recovered data lives once
downloaded — which is exactly how upstream Shepard already gets used.

## 8. EU AI Act intersection

Article 50 (transparency obligations, in force **2 August 2026**, ~75
days from this analysis) requires that synthetic audio/image/video/text
be marked in machine-readable form, that interactions with AI systems be
disclosed, that deep fakes be labelled, and that emotion-recognition
systems disclose to subjects. Penalties: up to €15M or 3% global
turnover. The Commission's *Code of Practice on Marking and Labelling of
AI-Generated Content* — first draft Dec 2025, second draft expected
March 2026, final June 2026 — gives the operational interpretation.

**Where Shepard intersects.** Three places:

1. **MCP-driven writes.** Every annotation, file upload, or DataObject
   created via the MCP surface is, by definition, AI-generated or
   AI-mediated content. F(AI)²R's `X-AI-Agent` header convention + the
   `fair2r:wasGeneratedBy` PROV-O edge make this *machine-readable* at
   query time. That satisfies Article 50(2)'s machine-readable marking
   in a clean way.
2. **Generated wiki pages / lab journals.** The planned
   `shepard-plugin-wiki-writer` and the lab-journal render path produce
   human-facing artefacts. Article 50(4) requires *human-understandable*
   disclosure for "AI-generated text … published to inform the public on
   matters of public interest." A DLR research report partly drafted by
   the wiki-writer falls into that scope. The compliance posture:
   render a visible disclosure plus the machine-readable F(AI)²R-edge.
3. **Synthetic data** generated for ODD-completeness augmentation
   (Issue 02 DM-01 anticipates this). Article 50(2) marking applies. A
   shepard-plugin-synthetic-data SHOULD set both the visible and the
   machine-readable marker on every emitted artefact.

**Honest caveat.** Article 50 was written for consumer-facing generative
AI, not for research-data-management traffic. The legal interpretation of
"deployed" or "made available on the market" for an internal DLR research
RDM platform is ambiguous. DLR-LY and the DLR legal office should be
consulted before any external positioning claim that "Shepard is Art. 50
compliant." The defensible claim is: *Shepard provides the technical
hooks (X-AI-Agent header, PROV-O edges, F(AI)²R verification ladder) that
make Article 50 marking implementable without re-architecting; the
operational obligation is the operator's.*

## 9. AI Levels and where DLR aerospace research lands

Issue 02 §B.5 + §C.2.1.3 + table on p. 17–18:

| Level | Definition | Authority | DQR rigour expectation | DLR typical fit |
|---|---|---|---|---|
| **1A** | Automation support to **information acquisition** (sense-making aid) | Full human | Full but with assurance-level reduction permitted at low IDAL/SWAL | LUMEN auto-tag of TS channels; Shepard MCP exploration |
| **1B** | Automation support to **decision-making** (advisory) | Full human | Full | MFFD AFP defect classifier *advising* an operator |
| **2A** | **Directed decision and automatic action** — predefined task allocation | Full human authority, AI cooperates | Higher (cooperation requires capability evidence per HF-*) | MFFD welding-robot trajectory adjustment with operator override |
| **2B** | **Supervised automatic decision and action** — dynamic task allocation | Partial human authority (collaboration); guarantor role active | Full HF-* applies; HAII guidance | Hypothetical: an ATM advisory system; PLUTO ground-segment automation |
| **3A** | Safeguarded automatic decision and action | Limited human (monitoring) | Per §C.5; many objectives currently "anticipated" not adopted | Future autonomy work; out of current DLR Shepard scope |
| **3B** | Non-supervised automatic decision and action | None | Not yet covered by Issue 02 | Out of scope |

EASA initially accepts only AI/ML constituents at **IDAL D / SWAL 4 / AL5
or lower** (Issue 02 §B.4, p. 12). For unsupervised learning the bar is
identical. Most DLR research projects today land at 1A or 1B, with MFFD
inspection candidates plausibly 2A in a fielded setting. Anything 2B+
requires the full HF-* HAT capability evidence stack, which is not
something Shepard hosts.

## 10. Where Shepard already aligns (defensible claims)

These claims are defensible *today* against the current Concept Paper
text — i.e., they would survive a peer-review reading by someone holding
the 283-page PDF open.

1. **Data lineage and traceability at the appId level** satisfy
   Objective DA-04's "ability to determine the origin of the data
   (traceability)" and the "traceability of the data from their origin to
   their final operation through the whole pipeline of operations" verb.
   The L2 chain (`aidocs/platform/25-neo4j-id-migration-design.md`) makes
   this **stable across migrations**, which is a stronger claim than most
   research data stacks can make.
2. **Predecessor/Successor relationships** between DataObjects express
   the iteration cycles in Issue 02 fig. 13 natively and queryably.
   The LUMEN demo (TR-004 anomaly → investigation → TR-005 hold/repair →
   TR-006 re-test) is an audit-grade lineage walk in two clicks.
3. **PROV1a Activity capture filter** records every mutation as a
   `prov:Activity` with agent + timestamp + payload reference. This is
   the substrate for Objective DA-04 integrity + the EXP-09 chronological
   reconstruction requirement.
4. **TimescaleDB-backed `:TimeseriesContainer`** ingest is a credible
   host for the runtime monitor outputs required by EXP-08 + EXP-09. The
   start/stop logic of MOC EXP-09-1 (auto-start with the AI system,
   continue while operating, auto-stop after) is a one-shape adaptor away
   from existing Shepard ingest behaviour.
5. **SHACL-validated annotations** (the post-95 templates work) give
   structured, machine-checkable adherence to Concept Paper artefact
   shapes — a research-grade alternative to free-text reports for
   ConOps, classification, ethics review, safety assessment artefacts.
6. **F(AI)²R PROV-O extension** provides EU AI Act Article 50(2)
   machine-readable marking by construction for any AI-mediated write to
   Shepard, including the `X-AI-Agent` header convention and the
   verification-ladder property `fair2r:verificationState`.

## 11. Where Shepard needs to extend (honest gap list)

1. **No template enforcement for Concept-Paper artefact kinds.** A safety
   assessment uploaded as a plain `:FileContainer` blob does not verify
   that it covers Objective SA-01's seven required topics. **Fix:** ship
   SHACL templates per CP2 artefact (ConOps per CO-04, DQR set per
   DA-04, ethics assessment per ET-01) under the
   `aidocs/semantics/95-shacl-templates-and-individuals.md` programme.
   Effort: 1 sprint per template; ~6 templates min.
2. **No first-class ML model checkpoint object.** Currently shoehorned
   into `:FileContainer`. **Fix:** `shepard-plugin-mlflow` adapter +
   `ModelCheckpoint` payload kind. Effort: medium; precedent in the
   plugin SPI work.
3. **No enforcement of train/val/test independence (DM-06).** Currently a
   user discipline issue. **Fix:** SHACL shape on the three-set bundle
   with `sh:disjoint`-style invariant + a verification widget. Effort:
   small.
4. **Synthetic data marking not built in (Article 50(2) + DM-01 note on
   synthetic augmentation).** **Fix:** mandatory `synthetic: true` flag +
   `prov:wasDerivedFrom` edges back to the generating model run for any
   `:DataObject` ingested by a generator plugin.
5. **No standardised Concept-Paper artefact bundling for export.** A
   certification audit will request "give me the Learning Assurance Plan,
   the DQR set, the train/val/test sets, the LM-09 performance reports,
   the verification matrix, and the post-ops recordings for application
   X" as a single bundle. **Fix:** export profile in the planned
   `shepard-plugin-publisher`. Effort: medium.
6. **No runtime monitoring; no ODD-conformity check at inference.** This
   is correctly **outside Shepard's scope** — it is a plugin to the
   *deployed* AI system, not to the evidence vault. But the platform
   should be ready to *ingest* the resulting EXP-08 stream at high
   rates. Already covered by the TimescaleDB stack.

## 12. Timeline — when does this matter

| Date | Event | Implication for Shepard |
|---|---|---|
| **2024-03-06** | Concept Paper Issue 02 published | Reference text; not yet adopted MOC |
| **2024-07-03** | MLEAP Final Report (Airbus Protect / LNE / Numalis) | Feeds Issue 03 + EUROCAE WG-114 |
| **2024-08-01** | EU AI Act in force | Counts down to Art. 50 |
| **2025–2027** | EUROCAE WG-114 / WG-72 standards drafting | Some objectives crystallise into industry standards before EASA adopts them |
| **2026-08-02** | **EU AI Act Article 50 fully enforceable** | F(AI)²R-style marking transitions from "good practice" to "legal requirement" for any covered system |
| **~2027** | Concept Paper Issue 03 anticipated (post-MLEAP, post-Level 3) | Likely tightens generalisation-bound MOCs; extends Level 3 |
| **~2028** | RMT.0742 finalises AMC integration | Concept Paper objectives become adopted MOC; "anticipated" stops being a hedge |
| **2028+** | Aviation domain rulemaking (vertical: Part 21, Air Ops, ATM/ANS) catches up | EASA-certified AI deployments at scale possible |

**Two horizons matter.** Horizon 1 (2026-08-02): EU AI Act Article 50.
Practically here. Affects every Shepard-hosted AI-mediated write today.
F(AI)²R alignment is the structural fix; ship it.
Horizon 2 (2028): RMT.0742. Affects any DLR aerospace project aiming at
EASA certification of an AI/ML constituent. Shepard's positioning as
"audit-grade RDM for ML evidence" needs to be credible by then — meaning
the gap list in §11 needs to be closed by ~2027.

## 13. Positioning claims defensible at funding panels

The following three sentences are calibrated for inclusion in a Clean
Aviation JU / Horizon Europe aerospace AI proposal. They cite specific
EASA objectives and avoid overclaim.

1. *"Shepard provides immutable, appId-keyed data lineage and PROV-O
   activity capture for every artefact in the ML development pipeline,
   covering the traceability and integrity requirements of EASA AI
   Concept Paper Issue 02 Objective DA-04 (data quality requirements)
   and Objectives DM-01 through DM-07 (data management) for any
   AI/ML constituent up to IDAL D / SWAL 4 / AL 5."*
2. *"Shepard's TimescaleDB-backed time-series payload kind, combined
   with its PROV-O activity capture, provides a credible host for the
   post-operation data recording required by EASA Objectives EXP-08 and
   EXP-09 (MOCs 09-1 through 09-3), supporting both Safety Management
   System monitoring by end-user organisations and continuous safety
   assessment by applicants."*
3. *"For AI-mediated writes via the platform's MCP interface, Shepard
   implements the F(AI)²R PROV-O extension (Krebs 2026, GitHub
   reference repo), producing machine-readable provenance edges
   suitable for EU AI Act Article 50(2) transparency marking; visible
   human-readable disclosure is rendered in the UI alongside the
   provenance edge."*

These claims are *evidence-supply* claims, not assurance claims. They
do not say "Shepard makes your AI EASA-compliant" — they say "Shepard
hosts the right shape of evidence to support your assurance case."
That posture is defensible. The reverse posture is not.

## 14. Risks and honest caveats

- The Concept Paper is **guidance, not regulation**. Every objective
  here may shift before adoption. Anticipated MOCs explicitly say so.
- The Issue 02 text I worked from is the **published March 2024
  release**; some of the references in the existing
  `easa-ai-compliance.md` analysis use the **Proposed Issue 02 (Feb
  2023)** text. Where objective IDs disagree between the two, I cite
  the March 2024 published version (p. 1 of the PDF: ISO9001-certified
  proprietary doc).
- The **EXP-09 fit** assumes Shepard is used as the *ground-side*
  evidence vault. The Concept Paper anticipates that airborne crash-
  protected recording stays a separate hardware item (FDR/CVR).
- **F(AI)²R is a community proposal**, not an adopted standard. Calling
  it "EASA-compliant marking" overstates it; calling it "compatible with
  the kind of marking EASA's runtime explainability MOCs anticipate" is
  honest.
- **DLR-LY engagement is required** for any external positioning that
  cites EASA directly. The interpretation of "applicant" and
  "certification" in a research context (where there is no Part 21 type
  certificate, no air operator certificate) is a regulator-dialogue
  question.
- Some Concept Paper objectives require **organisational** evidence (a
  competence framework per §C.6.2; a design-organisation case per §C.6.3)
  that Shepard cannot supply at all. A complete CP2 case is
  ~tool-evidence + ~organisational-evidence; the latter is out of scope.

## 15. Recommended next steps for Shepard

In rough priority (highest impact first), assuming a small team:

1. **Ship the SHACL template programme** (`aidocs/semantics/95`)
   targeted first at Concept Paper artefacts: ConOps (CO-04), DQR set
   (DA-04), train/val/test bundle (DM-06), ethics assessment (ET-01),
   safety assessment (SA-01), learning assurance plan (DA-01). Six
   templates ≈ 6 sprints. Closes the largest evidence-discipline gap.
2. **Ship F(AI)²R vendor-tier ontology + the X-AI-Agent header +
   visible-disclosure rendering**. Before 2026-08-02. Closes Article 50
   exposure for MCP-mediated writes. The reference TTL exists; the
   integration work is the gateway filter + UI badge.
3. **Add the synthetic-data marker** (mandatory `synthetic: true` flag
   plus `prov:wasDerivedFrom` edge on every generator-emitted
   DataObject). Half-sprint. Closes Article 50(2) for the
   data-augmentation case.
4. **Plug into MLflow** as a `shepard-plugin-mlflow` first-class
   integration so model checkpoints + training-run metadata + LM-05/06/09
   artefacts are native objects rather than file blobs. One quarter.
5. **Bundle export profile** for a Concept-Paper-aligned audit dump
   (Learning Assurance Plan + DQR set + train/val/test refs + LM
   reports + post-ops recordings, all keyed to one application). The
   `shepard-plugin-publisher` is the right home.
6. **Open a dialogue with DLR-LY** about which Shepard claims they
   would back when engaging EASA on a specific certification project.
   They are the institutional gatekeeper for any externally-cited
   positioning.
7. **Track Issue 03 + RMT.0742 progress**. A standing watch in
   `aidocs/34-upstream-upgrade-path.md` would force the team to revise
   this positioning document when EASA moves.

## Sources

Primary:

- [EASA AI Concept Paper Issue 02 (PDF, March 2024)](https://www.easa.europa.eu/en/downloads/139504/en)
- [EASA AI Concept Paper Issue 02 — landing page](https://www.easa.europa.eu/en/document-library/general-publications/easa-artificial-intelligence-concept-paper-issue-2)
- [EASA news: Concept Paper Issue 2 published (6 March 2024)](https://www.easa.europa.eu/en/newsroom-and-events/news/easa-publishes-artificial-intelligence-concept-paper-issue-2-guidance)
- [EASA Final Report on Machine Learning (MLEAP) — news, July 2024](https://www.easa.europa.eu/en/newsroom-and-events/news/artificial-intelligence-easa-publishes-final-report-machine-learning)
- [EASA AI Roadmap 2.0 — landing page](https://www.easa.europa.eu/en/document-library/general-publications/easa-artificial-intelligence-roadmap-20)
- [EASA AI Roadmap 2.0 (PDF)](https://www.easa.europa.eu/en/downloads/137919/en)
- [EASA AI Concept Paper Proposed Issue 02 (PDF, Feb 2023, predecessor draft)](https://www.easa.europa.eu/sites/default/files/dfu/easa_concept_paper_guidance_for_level_1and2_machine_learning_applications_proposed_issue_02_feb2023.pdf)
- [EASA AI Concept Paper Issue 01 — First usable guidance for Level 1 ML applications (PDF, 2021)](https://www.easa.europa.eu/sites/default/files/dfu/easa_concept_paper_first_usable_guidance_for_level_1_machine_learning_applications_issue_01_1.pdf)
- [EASA RMT.0742 Terms of Reference — AI trustworthiness](https://www.easa.europa.eu/en/document-library/terms-of-reference-and-rulemaking-group-compositions/tor-rmt0742)
- [EU AI Act Article 50 — Transparency obligations](https://artificialintelligenceact.eu/article/50/)
- [European Commission — Code of Practice on AI-generated content](https://digital-strategy.ec.europa.eu/en/policies/code-practice-ai-generated-content)

Secondary / cited:

- [AI Standards Hub — EASA AI Roadmap 2.0 entry](https://aistandardshub.org/guidance/easa-artificial-intelligence-roadmap-2-0/)
- [Unmanned Airspace — EASA Concept Paper coverage](https://www.unmannedairspace.info/uncategorized/easa-issues-concept-paper-to-address-challenges-of-deploying-ai-in-aviation/)
- [Bird & Bird — Draft Code of Practice analysis (2026)](https://www.twobirds.com/en/insights/2026/taking-the-eu-ai-act-to-practice-understanding-the-draft-transparency-code-of-practice)
- [Jones Day — EC publishes Draft Code of Practice (Jan 2026)](https://www.jonesday.com/en/insights/2026/01/european-commission-publishes-draft-code-of-practice-on-ai-labelling-and-transparency)

Internal references:

- `aidocs/semantics/95-shacl-templates-and-individuals.md`
  (SHACL templates + F(AI)²R integration design)
- `aidocs/agent-findings/easa-ai-compliance.md` (per-objective gap analysis;
  complementary to this positioning document)
- `aidocs/platform/25-neo4j-id-migration-design.md` (appId stability,
  the L2 chain)
- `aidocs/platform/87-timeseries-appid-migration.md` (timeseries identity,
  EXP-08/09 alignment)
- `aidocs/integrations/67-unhide-publish-plugin.md` (FAIR publishing —
  intersects with Art. 50)
- F(AI)²R reference: <https://github.com/noheton/f-ai-r>
  (Krebs, 2026, MIT/CC-BY-4.0; *not* a DLR work per the §9.5 disclosure
  in the precursor paper)
