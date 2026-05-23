---
title: FAIR4ML × FAIR for AI × f(ai)²r — comparative survey + alignment plan
stage: feature-defined
last-stage-change: 2026-05-23
audience: thesis, contributors, semantic-WG, future-Claude
---

# FAIR4ML × FAIR for AI × f(ai)²r — comparative survey + alignment plan

**TL;DR.** Three FAIR-AI vocabularies are in active use, none of them subsumes
the others:

- **FAIR4ML** (RDA Interest Group, schema.org extension; v0.1.0 released
  2024-10-27; `https://w3id.org/fair4ml#`) describes **ML model artefacts**
  (training data, evaluation data, mlTask, modelRisksBiasLimitations, CO₂eq,
  fineTunedFrom). It is a **model-card vocabulary**, not a provenance
  vocabulary.
- **FAIR for AI** (Huerta et al. 2023, *Scientific Data* 10:487) is **not a
  vocabulary** — it is a **community-of-practice paper** scoping what FAIR
  means for AI and naming initiatives (FAIR4HEP, Garden, HPC-FAIR, …). It
  defines requirements; it does not ship predicates.
- **f(ai)²r** (Krebs 2026, `github.com/noheton/f-ai-r`; namespace
  `https://noheton.org/f-ai-r/ns#`) is a **PROV-O extension for
  AI-in-the-authoring-loop**: classes for `AIAgent`, `HumanResearcher`,
  `Claim`, `Source`, `Prompt`, `Transcript`, `AuthoringPass`, `AuditPass`,
  `Manuscript`, `Section`, `Figure`, `Build`; properties for
  `verificationState`, `contradicts`, `repairs`; a verification ladder
  (unverified → needs-research → lit-retrieved → ai-confirmed →
  human-confirmed → source-vendored → lit-read).

The three are **complementary along orthogonal axes**: FAIR4ML describes
**what was built** (the model card), Huerta-FAIR4AI describes **what FAIR
means** for that artefact (the requirements lens), f(ai)²r describes **how
the writing/authoring was done with AI in the loop** (the provenance trail
through the production process).

This survey reads each one in detail, compares them across nine dimensions,
takes a defensible thesis-positioning stance on why f(ai)²r exists alongside
FAIR4ML, and proposes concrete predicate-level alignment work. Eleven
backlog rows land in `aidocs/16` under the `FAIR4-` prefix.

---

## 1. What each one is

### 1.1 FAIR4ML — the RDA Interest Group's schema.org extension

**FAIR4ML** is a metadata vocabulary built by the **Research Data Alliance
(RDA) FAIR for Machine Learning Interest Group** (FAIR4ML-IG), formally
accepted as an RDA IG in **September 2022** after roughly two years of
landscaping. v0.0.1 shipped 2023; **v0.1.0 shipped 2024-10-27** under
Apache-2.0 at `https://w3id.org/fair4ml#`.

**Lead authors** (per the v0.1.0 spec metadata):

- Leyla-Jael Castro (ZB MED, Cologne) — convening author, IG co-chair
- Daniel Garijo (Universidad Politécnica de Madrid, Ontology Engineering Group)
- Dietrich Rebholz-Schuhmann (ZB MED)
- Dhwani Solanki (ZB MED)
- Jenifer Tabita Ciuciu-Kiss (UPM)
- Dan Katz (UIUC) — also a FAIR for AI 2023 co-author
- Lars Eklund (Uppsala)
- Gnana Bharathy (ARDC, Australia)
- RDA FAIR4ML Task Force (collective)

**Scope.** A schema.org extension introducing two classes — `fair4ml:MLModel`
(subclass of `schema:CreativeWork`) and `fair4ml:MLModelEvaluation` — and a
predicate set focused on **model artefact description**, not training-process
trace:

| Predicate | Range | Purpose |
|---|---|---|
| `fair4ml:trainedOn` | `schema:Dataset` / `cr:Dataset` (Croissant) | Training dataset reference |
| `fair4ml:validatedOn` | `schema:Dataset` / `cr:Dataset` | Validation set |
| `fair4ml:testedOn` | `schema:Dataset` / `cr:Dataset` | Test set |
| `fair4ml:evaluatedOn` | `schema:Dataset` / `cr:Dataset` | Benchmark / extrinsic eval |
| `fair4ml:mlTask` | `schema:Text` / `schema:DefinedTerm` | The task (binary classification, etc.) |
| `fair4ml:modelCategory` | `schema:Text` / `schema:DefinedTerm` | Family (CNN, transformer, …) |
| `fair4ml:fineTunedFrom` | `fair4ml:MLModel` | Lineage to source model |
| `fair4ml:hasCO2eEmissions` | `schema:Text` | CO₂eq of training |
| `fair4ml:intendedUse` | text/term/URL | What the model is for |
| `fair4ml:modelRisksBiasLimitations` | `schema:Text` | Risks, biases, known failure modes |
| `fair4ml:ethicalSocial` | `schema:Text` | Ethical considerations |
| `fair4ml:legal` | `schema:Text` | Legal considerations |
| `fair4ml:usageInstructions` | `schema:Text` | How to run the model |
| `fair4ml:codeSampleSnippet` | `schema:Text` | Inference code |
| `fair4ml:sharedBy` | `schema:Person`/`Organization` | Uploader (HuggingFace etc.) |

All inherited schema.org properties (`schema:author`, `schema:citation`,
`schema:license`, `schema:codeRepository`, …) apply on top.

**Implementation status (mid-2025, per the Zenodo CoRDI 2025 paper
[doi:10.5281/zenodo.16735334]).** Adopted by:

- **InesData** (small set of models, demonstrator)
- **MLentory** (NFDI4DataScience service; harmonises metadata from Hugging
  Face + OpenML using FAIR4ML)
- Integration planned into the **NFDI4DS in-silico reproducibility metadata
  protocol**.
- **Oak Ridge National Lab** (ORNL) interested in extending FAIR4ML for
  provenance + results of trained models — **this is the gap f(ai)²r could
  fill** (see §4).

**Companion vocabularies in the stack** (FAIR4ML re-uses, doesn't
re-define):

- **Croissant** (MLCommons, 2024-03; `http://mlcommons.org/croissant/`) —
  ML-ready dataset format; schema.org extension; Hugging Face / Kaggle /
  OpenML / TFDS adoption.
- **CodeMeta** (`https://w3id.org/codemeta/`) — research-software vocabulary;
  FAIR4ML uses `codemeta:codeRepository` to point at the model's code.
- **schema:Dataset** — the substrate.

**Maturity.** Draft (under review). v0.1.0 is the **first reviewed
release**; next releases are open for community input (additional metrics,
evaluation results, hyperparameters, model-generation-process,
external-validation triples). **No SHACL shapes published yet** (the spec
table prescribes "expected types" but doesn't ship a validatable shape
graph).

### 1.2 FAIR for AI — Huerta et al. 2023 (the community position paper)

**FAIR for AI** is *not* a vocabulary. It is the **agenda-setting paper**
that translates FAIR principles to AI artefacts. The canonical reference:

> Huerta, E.A., Blaiszik, B., Brinson, L.C., Bouchard, K.E., Diaz, D.,
> Doglioni, C., Duarte, J.M., Emani, M., Foster, I., Fox, G., Harris, P.,
> Heinrich, L., Jha, S., Katz, D.S., Kindratenko, V., Kirkpatrick, C.R.,
> Lassila-Perini, K., Madduri, R.K., Neubauer, M.S., Psomopoulos, F.E., Roy,
> A., Rübel, O., Zhao, Z., Zhu, R. (2023). **FAIR for AI: An
> interdisciplinary and international community building perspective**.
> *Scientific Data* 10:487. doi:10.1038/s41597-023-02298-6

Built from the **FAIR for AI Workshop** at Argonne National Lab, 2022-06-07.
It surveys **eight US-DOE / NIH / NSF-funded FAIR-for-AI initiatives**
(FAIR4HEP, ENDURABLE, NIH Common Fund Data Ecosystem, BioDataCatalyst,
Garden, Braid, HPC-FAIR, the FAIR Surrogate Benchmarks Initiative) and lays
out **shared requirements** for what FAIR means when applied to:

- AI/ML datasets (preprocessing trail, biases, splits)
- AI/ML models (architecture, hyperparameters, training compute,
  reproducibility)
- AI workflows (pipeline DAGs, environment capture, hardware footprint)

**Not a vocabulary.** The paper doesn't define classes/predicates. It cites
schema.org, codemeta, MLflow-style tracking as candidate substrates. It
explicitly leaves the predicate-design work to follow-up initiatives like
**RDA FAIR4ML** (which is the practical descendant of this paper —
Dan Katz, Catherine Brinson, several others overlap as authors).

**Maturity.** Position paper, 19k accesses, 101 citations as of 2026-05 —
**community-foundational, not implementation-ready**. The 2025 healthcare
"FAIR-AI" derivative (Nature npj Digital Medicine, 2025) is a separate
deployment-focused framework (pre/post-implementation evaluation for clinical
AI) that doesn't extend the vocabulary; it operationalises FAIR as a
checklist.

### 1.3 f(ai)²r — Krebs 2026 (the AI-authoring-loop PROV-O extension)

**f(ai)²r** ("FAIR for AI-in-the-loop research") is a **PROV-O vocabulary
extension** authored by **Florian Krebs** (DLR ZLP / fork author /
`noheton`) at `github.com/noheton/f-ai-r`. Namespace
`https://noheton.org/f-ai-r/ns#` (predicates also documented at
`https://noheton.github.io/f-ai-r/`). Pinned in this fork's bib as
`noHetonFair2r2026`.

**Scope.** Where FAIR4ML describes a **finished ML artefact** and Huerta
describes **what FAIR means for AI**, f(ai)²r describes **how the writing
happens with AI in the loop**. Each AI invocation becomes a typed
`prov:Activity`. Each generated assertion becomes a typed `fair2r:Claim`
that must be attached to an activity (no-parentless-claim invariant —
enforced by SPARQL conformance query). Every authoring session produces a
machine-readable PROV-O graph that ships **alongside the artefact**, not as
a post-hoc audit log.

**Predicate inventory (extracted directly from `doc/provenance.ttl` 2026-05
HEAD).**

Classes:

| Class | rdfs:subClassOf | Purpose |
|---|---|---|
| `fair2r:AIAgent` | `prov:SoftwareAgent` | LLM-driven agent in the pipeline |
| `fair2r:HumanResearcher` | `prov:Person` | Human author |
| `fair2r:Claim` | `prov:Entity` | An assertion in the manuscript warranting attribution |
| `fair2r:Source` | `prov:Entity` | A cited source |
| `fair2r:Manuscript` | `prov:Entity` | The primary artefact |
| `fair2r:Section` | `prov:Entity` | A manuscript section |
| `fair2r:Transcript` | `prov:Entity` | Session conversation log (first integrated practice) |
| `fair2r:Figure` | `prov:Entity` | A figure |
| `fair2r:Prompt` | `prov:Plan` | A prompt-as-Plan; a template |
| `fair2r:Build` | `prov:Activity` | A compile / build step |
| `fair2r:AuthoringPass` | `prov:Activity` | LLM-assisted authoring step |
| `fair2r:AuditPass` | `prov:Activity` | LLM-assisted audit step (the second "AI" of f(ai)²) |
| `fair2r:VerificationState` | `owl:Class` | Verification ladder rung |

Properties:

| Predicate | Domain | Range | Purpose |
|---|---|---|---|
| `fair2r:verificationState` | `fair2r:Claim` | `fair2r:VerificationState` | The ladder rung the claim sits on |
| `fair2r:contradicts` | `fair2r:Claim` | `fair2r:Claim` | A claim contradicts another |
| `fair2r:repairs` | `prov:Activity` | `fair2r:Claim` | An activity fixes a structural defect on a claim |

Verification ladder (rungs as named individuals):

`verif:unverified` → `verif:needs-research` → `verif:lit-retrieved` →
`verif:ai-confirmed` → `verif:human-confirmed` → `verif:source-vendored` →
`verif:lit-read`

Plus, used in Shepard-internal extensions (the fork's
`aidocs/semantics/99-promptlog-design.md` Part 15 names additional
predicates which are **fork-extensions to f-ai-r, not yet in the canonical
repo**):

| Predicate (fork-internal) | Status | Purpose |
|---|---|---|
| `fair2r:claimStatus` (verif:unverified / ai-confirmed / human-confirmed) | Used in fork; should be `fair2r:verificationState` per the canonical repo | The fork has used `claimStatus` historically; rename in the next refactor |
| `fair2r:modeOfProduction` (`"ai"` / `"human"` / `"hybrid"`) | Fork-internal; not in canonical repo | Per-artefact mode-of-production; lifted in `aidocs/strategy/86` and `aidocs/16 HTML-TO-MD-MIGRATION` |
| `fair2r:syntheticBackfill` (boolean) | Fork-internal; not in canonical repo | Flag for backfilled-after-the-fact synthetic provenance |
| `fair2r:realizesModel` | Fork-internal | Identifies the model an `AIAgent` realises (e.g., Claude Opus 4.7) |
| `fair2r:rejectionReasonCode` | Fork-internal proposal (per ONT-AI-MAP1) | Structured code on rejected AI proposals |
| `fair2r:proposalModel` | Fork-internal proposal (per ONT-AI-MAP1) | Which model produced an AI proposal |

**Maturity.** Working manuscript + reproducible writing pipeline; ~2300+
PROV-O triples in the canonical graph; **integrated practices** include
transcript preservation (the first), no-parentless-claim invariant
(structural defect class repaired in the open), verification ladder. Used
in production in this fork to capture every AI interaction during
manuscript and code authoring. **No SHACL shapes published in canonical
repo**, but the fork's `aidocs/semantics/99` ships `shp:PromptTemplateShape`
+ `shp:AuthoringPassShape` + `shp:TranscriptShape` + `shp:JudgePassShape`
SHACL families against `fair2r:` targets.

**Authorial disclosure.** f(ai)²r is the personal-capacity work of the same
human (Florian Krebs / `noheton`) who authors this fork. Both the
canonical repo (`github.com/noheton/f-ai-r`) and the manuscript
(`github.com/noheton/Obscurity-Is-Dead`) carry explicit non-DLR
declarations. This survey defends f(ai)²r against the FAIR4ML community
on technical merit (§4), not on author identity — the case has to stand
without leaning on the author.

---

## 2. Nine-dimension comparison

| Dimension | FAIR4ML v0.1.0 | Huerta FAIR for AI 2023 | f(ai)²r |
|---|---|---|---|
| **Authority** | RDA IG (formal community, 2022 IG acceptance) | Peer-reviewed paper (Scientific Data, Nature stable) | Personal-capacity author repo (Krebs 2026, MIT/CC-BY) |
| **Scope** | ML model **artefact** (model card) | FAIR meaning for AI **artefacts** (data, models, workflows) — requirements, not predicates | AI-in-the-loop **authoring process** (the PROV-O of LLM-assisted writing) |
| **Maturity** | Draft v0.1.0 (2024-10-27); 2 adopters (InesData, MLentory); under review | Position paper (2023); 101+ citations; **no shipped vocabulary** | Working RDF + manuscript + writing pipeline; ~2300 triples; used in production in this fork |
| **Lead institutions** | ZB MED (DE), UPM (ES), UIUC (US), Uppsala (SE), ARDC (AU); RDA umbrella | Argonne (US), UIUC, MIT, CERN, NIH-funded labs; DOE/NIH/NSF axis | DLR ZLP (DE, personal-capacity); Helmholtz, NFDI4Ing, HMC affiliations |
| **Vocabulary base** | schema.org + codemeta + Croissant | (no vocabulary) | PROV-O + DCTERMS + FOAF |
| **Predicate count** | ~15 new + all schema.org / codemeta inherited | 0 | 13 classes + 3 object-properties + 7 verification-state individuals (canonical); ~6 fork-internal extensions |
| **RDF availability** | yes (`w3id.org/fair4ml#` with content negotiation, JSON-LD) | n/a | yes (Turtle in `doc/provenance.ttl`, schema rendered on site) |
| **SHACL shapes** | no (only "expected type" notes) | n/a | not in canonical; **yes in this fork** (`aidocs/semantics/99` §SHACL) |
| **EU policy alignment** | Implicit (schema.org / Croissant base widely accepted in EU science cloud) | None named; US-DOE-centric | Mapped to **EU AI Act Article 50** (transparency on AI-generated content, deadline 2026-08-02 — see `aidocs/16 PROMPT1` row) |

---

## 3. How they fit together — orthogonal stacks, not competitors

The three vocabularies cover **non-overlapping concerns** because they sit
on different points of the AI artefact lifecycle:

```
                      Lifecycle stage
                      ───────────────────────────────────────────────►
                      ┌───────────────┬──────────────────┬───────────────┐
                      │  Authoring    │ Training/Building │  Distribution  │
                      │  (writing)    │  (model creation) │  (publishing)  │
                      ├───────────────┼──────────────────┼───────────────┤
   Concern: HOW       │   f(ai)²r     │   m4i (NFDI4Ing) │       —        │
                      │   PROV-O ext  │   PROV-O ext     │                │
                      ├───────────────┼──────────────────┼───────────────┤
   Concern: WHAT      │       —       │       —          │   FAIR4ML      │
                      │               │                  │   schema.org   │
                      ├───────────────┼──────────────────┼───────────────┤
   Concern: WHY/      │  Huerta 2023 (the requirements lens — "what FAIR  │
   REQUIREMENTS       │  means for AI artefacts at every lifecycle stage")│
                      └───────────────────────────────────────────────────┘
```

- **FAIR4ML** is a *descriptive* schema for the **distribution / publication
  endpoint** — what does the model card need to say so a third party can
  find, access, interoperate with, and reuse this model? It's deliberately
  schema.org-flavoured because that's what registry indexers consume.
- **Huerta 2023** is the *requirements* lens — it doesn't ship triples; it
  argues *that* FAIR has to extend across the AI artefact family and
  catalogues the initiatives that are doing it.
- **f(ai)²r** is a *process* PROV-O extension for the **authoring stage**
  — what did the human + the LLMs actually do when producing this artefact
  (manuscript, code, dataset annotation, …)? It's PROV-O-flavoured because
  that's what auditors consume.

The sister relationship between f(ai)²r and **metadata4ing (m4i, NFDI4Ing,
v1.4.0)** is important: m4i is also a PROV-O extension, but for **scientific
process** generally (experiment → sample → instrument → data). f(ai)²r is
m4i's *authoring*-stage cousin. Both subclass `prov:Activity`. The two are
**aligned and composable** — an m4i `:ProcessingStep` (e.g., a model
training run) can `prov:wasInformedBy` a f(ai)²r `:AuthoringPass` (the
LLM-assisted hyperparameter selection conversation that produced the
training plan), and the training run's outputs land as inputs to the
manuscript-stage f(ai)²r activities.

**Concrete claim.** None of the three subsumes the others. A FAIR-complete
AI artefact in 2026 needs **all three** — FAIR4ML for the model card,
m4i (or FAIR4ML's planned provenance extensions) for the training run,
f(ai)²r for the authoring trail. This is the **three-layer** the thesis
defends in §4.

---

## 4. Thesis-positioning argument

The thesis claim is that this fork operationalises FAIR-compliant
AI-provenance capture via f(ai)²r. Three postures are available; each one
has to survive the test "would a peer reviewer accept this?"

### Posture A — "f(ai)²r is FAIR4ML's authoring-stage missing layer"

**The argument.** FAIR4ML focuses on the **model card** (distribution
endpoint). The CoRDI 2025 paper [doi:10.5281/zenodo.16735334] explicitly
names provenance as a **next-release** open question: *"Other collaborators
like the Oak Ridge National Laboratory (ORNL) have shown interest in
contributing to and extending FAIR4ML to describe provenance and results of
their trained ML models."* The fork's f(ai)²r covers this — but at the
**authoring stage**, not the training stage. f(ai)²r predicates
(`AuthoringPass`, `Prompt`, `Transcript`, `Claim`, `verificationState`) have
no equivalent in FAIR4ML v0.1.0 because FAIR4ML deliberately stayed
schema.org-flavoured for indexer adoption.

**Why this posture works.** It's honest. f(ai)²r and FAIR4ML are not
competitors; they target different lifecycle stages with different
substrates. The thesis defends the integration story, not a head-to-head
fight.

**Defensibility.** Strong. The two vocabularies cite each other naturally;
the only work is the predicate-level alignment (§5). No claim of f(ai)²r
"replacing" or "superseding" FAIR4ML is needed.

### Posture B — "f(ai)²r fills a gap FAIR4ML/Huerta haven't filled"

**The argument.** Huerta's 2023 paper enumerates FAIR-for-AI requirements
but doesn't operationalise them. FAIR4ML operationalises the model-card
half. **Neither operationalises AI-in-the-authoring-loop**, which is the
EU AI Act Article 50 surface (transparency on AI-generated content,
deadline 2026-08-02). f(ai)²r is the **first PROV-O-native realisation** of
the authoring trail that an Article 50 auditor would consume.

**Why this posture works.** The EU AI Act gap is real and dated. FAIR4ML
v0.1.0 has no Article 50 hooks. The 2025 healthcare FAIR-AI paper has none
either. f(ai)²r ships predicates an Article 50 evidence pack can map to
directly (`AuthoringPass`, `verificationState`, `repairs`).

**Defensibility.** Strong, with the caveat that **OpenLineage**
(`openlineage.io`) and **LangFuse** are nearby substrates that capture
LLM-call telemetry — see `aidocs/agent-findings/synergy-2026-05-23-openlineage-fair2r.md`
in this fork for the alignment design. The thesis must show f(ai)²r is
**semantically richer than OpenLineage telemetry** (claims + verification
ladder + repair edges, not just call traces).

### Posture C — "f(ai)²r is a parallel competing vocabulary"

**The argument.** f(ai)²r is the personal-capacity work of a single author.
FAIR4ML is RDA-community-backed. The author should adopt FAIR4ML and
deprecate f(ai)²r.

**Why this posture doesn't work.** f(ai)²r and FAIR4ML cover different
lifecycle stages (see §3 — orthogonal axes). FAIR4ML has **no
`AuthoringPass`, no `Prompt`, no `verificationState`, no `Claim` with
contradicts/repairs edges**. Adopting FAIR4ML doesn't deprecate f(ai)²r;
it pairs with f(ai)²r at the publishing endpoint. **Defensibility: weak —
this is a strawman posture that doesn't survive a five-minute review with
the FAIR4ML authors themselves.**

### Verdict

**Postures A + B are jointly the thesis stance.** f(ai)²r is the
authoring-stage PROV-O extension; FAIR4ML is the distribution-stage
schema.org extension; m4i is the training-stage PROV-O extension; Huerta
2023 is the requirements paper that motivates all three. The thesis
contributes the **alignment design** that lets them interoperate (the
predicate-level work in §5).

The honest weakness in posture A+B: **f(ai)²r has one author, two adopters
(the manuscript itself, and this fork), and no published peer-reviewed
paper yet.** The Obscurity-Is-Dead manuscript (Krebs 2026) is the
foundational paper but is not yet through peer review. The thesis depends
on f(ai)²r reaching at least conference-publication maturity (CoRDI 2026,
EOSC Symposium 2026, RDA Plenary 24) **before** thesis submission.
This is captured as a backlog row (FAIR4-PUB-VENUE).

---

## 5. Integration questions — predicate-level alignment plan

Concrete work items that close the integration gap between f(ai)²r and
FAIR4ML/m4i/Croissant. Each one is a §6 backlog row.

### 5.1 Namespace + vocabulary import discipline

Currently the fork uses **two different f(ai)²r namespaces** in different
places:

- `https://noheton.org/f-ai-r/ns#` — canonical (in `doc/provenance.ttl`)
- `https://noheton.github.io/f-ai-r/ns#` — used in this fork's
  `aidocs/semantics/99-promptlog-design.md`

**Fix.** Freeze `https://noheton.org/f-ai-r/ns#` as canonical (already named
in `aidocs/16 VIEWS-AS-SHAPES-CANONICAL-IRIS`); regenerate fork SHACL
shapes against it. Backlog row: `FAIR4-NS-FREEZE`.

### 5.2 `owl:equivalentProperty` triples — bridge f(ai)²r to FAIR4ML

f(ai)²r and FAIR4ML have **no overlapping predicates today** (different
lifecycle stages). The bridge isn't an equivalence; it's a **composition**.
Build a small bridge ontology under `aidocs/semantics/fair4ml-bridge.ttl`
that imports both:

```turtle
@prefix fair4ml: <https://w3id.org/fair4ml#> .
@prefix fair2r:  <https://noheton.org/f-ai-r/ns#> .
@prefix prov:    <http://www.w3.org/ns/prov#> .
@prefix shp:     <http://semantics.dlr.de/shepard-upper#> .

# An MLModel is a prov:Entity (FAIR4ML doesn't explicitly say this)
fair4ml:MLModel rdfs:subClassOf prov:Entity .

# An MLModel can be the prov:generated of an m4i:ProcessingStep
# (the training run) which prov:wasInformedBy a fair2r:AuthoringPass
# (the LLM-assisted hyperparameter conversation)

# Shepard-side convenience: a Collection that ships an MLModel is a
# Manuscript-like artefact (the README + model card is the "paper")
shp:MLModelCollection rdfs:subClassOf fair2r:Manuscript ,
                                       schema:CreativeWork .
```

Backlog row: `FAIR4-BRIDGE-ONTOLOGY`.

### 5.3 SHACL shapes for FAIR4ML compliance

FAIR4ML v0.1.0 ships predicate definitions but no SHACL shapes. Build:

```turtle
shp:Fair4mlMLModelShape a sh:NodeShape ;
  sh:targetClass fair4ml:MLModel ;
  sh:property [ sh:path schema:license       ; sh:minCount 1 ] ;
  sh:property [ sh:path fair4ml:mlTask       ; sh:minCount 1 ] ;
  sh:property [ sh:path fair4ml:trainedOn    ; sh:minCount 1 ] ;
  sh:property [ sh:path fair4ml:intendedUse  ; sh:minCount 1 ] ;
  sh:property [ sh:path fair4ml:modelRisksBiasLimitations ; sh:minCount 1 ] ;
  sh:property [ sh:path schema:author        ; sh:minCount 1 ] .
```

Validate a Shepard Collection-tagged-as-MLModel against this shape via the
existing `n10s:validateShapes` substrate. Backlog row:
`FAIR4-SHACL-COMPLIANCE`.

### 5.4 Adopt the FAIR4ML predicates in the existing fork-internal
"AI accountability dashboard" panel

The fork already projects f(ai)²r predicates through the
PromptLog feature (`aidocs/semantics/99`). The AI-accountability dashboard
described in `aidocs/agent-findings/persona-strategy-aligner-gh-pm-2026-05-23.md`
can additionally read FAIR4ML predicates from any Collection tagged as an
MLModel (per §5.2). The dashboard then becomes the **single UI surface that
proves f(ai)²r + FAIR4ML compose**. Backlog row: `FAIR4-DASHBOARD-INTEGRATE`.

### 5.5 The `fair2r:claimStatus` → `fair2r:verificationState` rename

The fork uses `fair2r:claimStatus` historically (per `aidocs/semantics/99`
line 301), but the canonical f-ai-r repo uses `fair2r:verificationState`
(per `doc/provenance.ttl`). **The fork has diverged from canonical.** This
is a coordinated rename pass in a single PR. Backlog row:
`FAIR4-PREDICATE-RENAME`.

### 5.6 Lift the fork-extension predicates back into canonical f-ai-r

The fork has invented `fair2r:modeOfProduction`, `fair2r:syntheticBackfill`,
`fair2r:realizesModel`, `fair2r:rejectionReasonCode`,
`fair2r:proposalModel`. These extensions need to either:

(a) land as canonical predicates in `github.com/noheton/f-ai-r` (since the
author is the same person, this is a low-friction PR);
(b) move under a fork-private namespace (e.g.,
`http://semantics.dlr.de/shepard-fair2r-ext#`).

**Recommendation: option (a)** — lift to canonical, because the predicates
solve genuine problems (per-artefact mode-of-production is a recurring
need; synthetic-backfill flagging is needed by any RDM platform that
ingests AI-curated data). Backlog row: `FAIR4-PREDICATE-LIFT`.

### 5.7 Croissant interop on training-dataset references

Where a Shepard `TimeseriesContainer` or `StructuredDataContainer` is used
as training data, render it as a `cr:Dataset` for FAIR4ML's
`fair4ml:trainedOn` predicate. This is a small mapping in the existing
`shepard-plugin-unhide` / `shepard-plugin-publisher` export path. Backlog
row: `FAIR4-CROISSANT-EXPORT`.

### 5.8 metadata4ing alignment — explicit `prov:wasInformedBy` between
m4i:ProcessingStep and fair2r:AuthoringPass

Where an m4i `ProcessingStep` (instance: a training run) was *informed by*
a fair2r `AuthoringPass` (instance: a hyperparameter-selection LLM
conversation), the bridge is `prov:wasInformedBy`. **No new predicate
needed; document the pattern.** This is the m4i × f(ai)²r alignment slice
named in `aidocs/semantics/94-metadata4ing-integration-design.md` (M4I-d).
Backlog row: `FAIR4-M4I-BRIDGE-DOC` (light docs work; ties to existing M4I-d).

### 5.9 EU AI Act Article 50 mapping table

f(ai)²r predicates → AI Act Article 50 obligations. The `verificationState`
+ `AuthoringPass` + `Prompt` triple covers the "transparency on
AI-generated content" obligation if the manuscript exports a per-claim
ladder rung. **This is the thesis's most policy-relevant integration
slice.** Backlog row: `FAIR4-AI-ACT-MAP` (tight: deliver as a
two-column table in `aidocs/semantics/99 §15`).

### 5.10 OAEI / MLflow / OpenLineage substrate-survey for f(ai)²r predicate-emission

Beyond FAIR4ML, f(ai)²r predicates need **emission paths** from
running systems. OpenLineage's `gen_ai.*` facets (per
`aidocs/agent-findings/synergy-2026-05-23-openlineage-fair2r.md`) carry
LLM-call telemetry; MLflow tracks model-training runs;
both can be mapped to f(ai)²r predicates. **This is the
emission-substrate-survey question.** Backlog row:
`FAIR4-EMISSION-SURVEY`.

### 5.11 Publication venue for f(ai)²r itself

f(ai)²r is currently a working repo + draft manuscript. For the thesis to
defend the integration story, **f(ai)²r needs at least one peer-reviewed
landing site** by mid-2026. Candidate venues: CoRDI 2026, EOSC Symposium
2026, RDA Plenary 24, FORCE11 2026, ESWC 2026 (Semantic Web). Backlog
row: `FAIR4-PUB-VENUE`.

---

## 6. Open questions for the thesis

- **OQ-1.** Can f(ai)²r and FAIR4ML co-publish a position paper at CoRDI
  2026? The RDA FAIR4ML lead (Castro, ZB MED) is approachable; the
  alignment-design work in §5 is publishable as a joint contribution.
- **OQ-2.** Is the verification-ladder vocabulary (`unverified` →
  `human-confirmed`) generalizable enough to ship as a separate W3C-style
  recommendation, or does it stay an f(ai)²r-internal vocabulary? If the
  former, the thesis claim broadens; if the latter, the thesis claim is
  narrower but harder to attack.
- **OQ-3.** The fork-internal `fair2r:modeOfProduction` predicate is
  load-bearing for the EU AI Act Article 50 evidence pack. Does it survive
  scrutiny from a competing FAIR4ML extension that proposes a different
  predicate name? **Resolution path:** coordinate naming with FAIR4ML's
  next-release discussion before the fork freezes.

---

## 7. Conclusion

**f(ai)²r and FAIR4ML are complementary, not competitors.** The thesis
posture is A+B: f(ai)²r is the authoring-stage PROV-O extension that
neither FAIR4ML v0.1.0 nor Huerta 2023 provides. The integration plan in
§5 is the contribution. Eleven backlog rows under `FAIR4-` close the
predicate-level alignment work.

The honest weakness is **community-of-practice depth** — FAIR4ML has an
RDA IG, two adopters, and ZB MED institutional backing; f(ai)²r has one
author and one fork. Closing that gap is the work of the §5.11
publication-venue row and the §5.6 predicate-lift back to canonical.

---

## 8. Sources

Cited inline; full entries land in `docs/_data/references.bib`:

- `huertaFairForAi2023` (already in bib) — Huerta et al. 2023, *Scientific
  Data* 10:487, doi:10.1038/s41597-023-02298-6
- `castroFair4ml2024` (new) — Castro et al. 2024-10-27, FAIR4ML v0.1.0 spec
  at `https://w3id.org/fair4ml/0.1.0`
- `solankiFair4mlCordi2025` (new) — Solanki et al. 2025-08-04, CoRDI 2025
  paper, doi:10.5281/zenodo.16735334
- `akhtarCroissant2024` (new) — Akhtar et al. 2024, Croissant arXiv 2403.19546
- `noHetonFair2r2026` (already in bib) — Krebs 2026, f(ai)²r vocabulary
- `metadata4ing2024` (already in bib) — NFDI4Ing metadata4ing v1.4.0
- `mukherjeeFairAi2025` (new) — Mukherjee et al. 2025, *npj Digital
  Medicine*, FAIR-AI clinical implementation framework
- `w3cProvO2013` (already in bib) — Lebo et al. 2013, PROV-O
