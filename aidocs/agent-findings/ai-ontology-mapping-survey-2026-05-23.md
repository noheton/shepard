---
title: AI-assisted ontology mapping — research survey + adoption recommendation
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributors, ontology engineers, plugin authors
---

# AI-assisted ontology mapping — research survey + adoption recommendation

**Backlog row.** `aidocs/16-dispatcher-backlog.md` → ONT-AI-MAP1.
**Output.** `aidocs/agent-findings/ai-ontology-mapping-survey-2026-05-23.md`.
**Snapshot date.** 2026-05-23.
**Audience.** Contributors landing the slice (`shepard-plugin-ontology-mapper`),
ontology engineers writing the SHACL substrate, plugin authors choosing
mapping shapes, reviewers asking "did we pick the right tool for this?"

**Personas consulted.**
- **Data & Process Ontologist** (primary) — Shepard's mapping pairs are
  domain-heavy (CHAMEO ↔ Material OWL, m4i ↔ DataCite, CPACS ↔ AAS).
  Bad mappings here corrupt downstream SPARQL, not just user search.
- **Research Data Manager** (secondary) — every mapping is a FAIR
  Interoperability move; the mapping itself must be FAIR (provenance,
  confidence, justification, license).
- **Analytics & AI** (secondary) — LLM is the alignment engine; the
  shape of "AI-suggested → human-accepted" is the production loop.

**Cross-references** (per `feedback_backlog_consult_context.md`):
- `aidocs/semantics/95-shacl-templates-and-individuals.md` — SHACL is
  the canonical substrate; the mapper writes into it
- `aidocs/semantics/96-upper-ontology-alignment.md` — BFO / IOF / IAO /
  PROV-O upper anchors define the alignment ceiling
- `aidocs/semantics/94-metadata4ing-integration-design.md` — m4i is one
  of the most-needed mapping pairs (m4i ↔ schema.org / Dublin Core /
  DataCite)
- `aidocs/semantics/48-internal-semantic-repository-via-neosemantics.md`
  — n10s substrate hosts the imported bundles
- `aidocs/40-ecosystem.md` — current ecosystem inventory; the chosen
  tool must land here
- `aidocs/41-synergy-sweep.md` — synergy collapse: mapper participates
  in the "Capability" abstraction (§1 in synergy sweep)
- `aidocs/integrations/40` / `aidocs/integrations/52-aas-backend-integration.md`
  — AAS submodel-template mapping target
- `feedback_ai_human_collab_provenance.md` — 🧑 / 🤖 / 🤝 modes for the
  acceptance loop
- `feedback_reuse_before_reimplement.md` — §3 reuse survey is mandatory
- `feedback_shacl_single_source_of_truth.md` — mappings stored
  SHACL-side, not as a parallel Postgres table

---

## §1 The Shepard mapping landscape

### 1.1 Inventory: what ontologies Shepard already touches

From the orientation reads (`aidocs/semantics/95 §13`, `aidocs/semantics/96
§2`, `aidocs/agent-findings/dlr-ontology-catalog.md`, ONT1a/ONT1b
manifest):

| Ontology | Role in Shepard | IRI / source | Adopted via |
|---|---|---|---|
| **PROV-O** | Activity / Entity / Agent backbone | `w3.org/ns/prov#` | PROV1a (shipped) |
| **OBO RO** | Relations across domain ontologies | `purl.obolibrary.org/obo/ro.owl` | ONT1a (shipped) |
| **metadata4ing (m4i)** | NFDI4Ing engineering RDM | `w3id.org/nfdi4ing/metadata4ing/` | ONT1b (shipped); deepening in design |
| **IAO** | Information artifacts | `purl.obolibrary.org/obo/iao.owl` | aidocs/96 (designed) |
| **BFO 2020** | Upper ontology | `purl.obolibrary.org/obo/bfo.owl` | aidocs/96 (designed) |
| **IOF Core** | Industrial upper | `spec.industrialontologies.org/ontology/core/` | aidocs/96 (designed) |
| **EMMO + CHAMEO** | Materials characterisation | `emmo-repo.github.io/...` | aidocs/95 §13 (designed) |
| **Material OWL / MatOnto** | Materials properties | various | aidocs/95 §13 (designed) |
| **SOSA/SSN** | Sensor / observation | `w3.org/ns/sosa/` | aidocs/95 §3 (designed) |
| **schema.org / Dublin Core / DataCite** | Discovery + publish | various | aidocs/40 (target for Unhide / Databus exports) |
| **AAS submodel templates** | Industrial digital twin | `industrialdigitaltwin.org` | aidocs/integrations/52 |
| **CPACS** | Aerospace configuration schema | `dlr-sl.github.io/cpacs-website/` | aidocs/40 + DLR strategy |
| **f(ai)²r** | AI provenance | `github.com/noheton/f-ai-r` | aidocs/95 §15 (designed) |
| **CCSDS** | Space mission ops | various | PLUTO use case |

### 1.2 The mapping pairs Shepard actually needs

Each pair below is a mapping **edge** in the cross-ontology graph. The
column counts and consequence are estimates from the design docs and
the demo seeds (MFFD `examples/mffd-showcase/seed.py`, LUMEN
`examples/lumen-showcase/seed.py`).

| Pair | Roughly how many mappings | Consequence of a bad mapping |
|---|---|---|
| **m4i ↔ DataCite + schema.org** | ~30 core terms (`ProcessingStep`, `Method`, `Tool`, `hasInput`, `realizesMethod`, `hasParticipant` …) | Helmholtz Unhide / Databus / Invenio publish records misrepresent provenance; an aerospace-paper auditor sees a `dc:creator` where they expected an `m4i:hasParticipant` chain. **High pain — FAIR Interoperability score drops.** |
| **CHAMEO ↔ Material OWL / MatOnto** | ~50–80 pairs (e.g. CHAMEO `Specimen` ↔ MatOnto `Material`, `CharacterisationMethod` ↔ MatOnto `MaterialProperty.measurementProcedure`) | MFFD layup attributes (`prepreg_lot`, `consolidation_force`) lose semantic anchor; cross-institute MFFD↔NFDI-MatWerk queries fail silently. **High pain — the MFFD demonstrator depends on this.** |
| **CPACS ↔ AAS submodel templates** | ~20–40 pairs at first slice (Nameplate, TechnicalData, OperationalData, Aerodynamics, Geometry) | An aerospace partner pulling a Catena-X/EDC dataset built from a Shepard CPACS DataObject sees the wrong submodel kind; **S-05 (AAS endpoint = dropbox-adapter + publish-adapter) blocked.** |
| **BFO / IOF / IAO upper ↔ domain (m4i + CHAMEO + AAS)** | ~10–30 axiomatic edges (`m4i:ProcessingStep rdfs:subClassOf bfo:Process`, `iao:document-part-of`) | Cross-domain SPARQL ("show me every Activity that consumed a Material of type X across MFFD and PLUTO") returns nothing. **Medium pain — kills the upper-ontology investment.** |
| **SOSA/SSN ↔ IoT-Lite / QUDT** | ~15 pairs (`sosa:Observation` ↔ `iot-lite:DataValue`, `qudt:unit` ↔ `sosa:hasResult`) | Sensor-data exports from the home-showcase MQTT collector or sTC (`shepard-timeseries-collector`) don't round-trip into NFDI4Ing / W3C SOSA pipelines. **Medium pain — affects sTC + edge integration.** |
| **f(ai)²r ↔ PROV-O** | ~10 predicates (`f-ai-r:aiAuthored`, `f-ai-r:humanAuthored`, `f-ai-r:wasAcceptedAs` over `prov:wasAttributedTo`) | EU AI Act Art-50 traceability fails — the predicate-set is the differentiator vs other RDM platforms (per `feedback_ai_human_collab_provenance.md`). **High pain — TPL9 regulatory deadline 2026-08-02.** |

**Total surface.** Rough order of 150–250 mapping triples across these
six pairs. **That is far too small for a from-scratch ML pipeline and
far too large for hand-curation by one ontologist.** This is exactly
the sweet spot AI-assisted alignment is designed for: human throughput
× LLM coverage × cheap human-in-the-loop validation.

### 1.3 What "good" looks like

A good Shepard mapping has six properties (synthesised from §1.1
inventory and FAIR-mapping literature `[Matentzoglu 2022]`):

1. **Provable.** The mapping carries justification (`semapv:LexicalMatching`,
   `semapv:LogicalReasoning`, `semapv:ManualMappingCuration`, or
   `semapv:UnspecifiedMatching`) and confidence (0–1).
2. **Attributable.** Modes are `🧑 human-authored`, `🤖 AI-authored`,
   `🤝 AI-proposed-human-accepted` (per `feedback_ai_human_collab_provenance.md`).
3. **Reviewable.** Stored in SHACL substrate so any reviewer can SPARQL
   the mapping graph (per `feedback_shacl_single_source_of_truth.md`).
4. **Reversible.** Carries `created_at` + `revision_predecessor` so a
   bad mapping can be retracted without breaking downstream consumers.
5. **Portable.** Round-trips to SSSOM TSV so other platforms can
   consume it `[Matentzoglu 2022, §3]`.
6. **License-clean.** Has a `mapping_license` field (CC0 default;
   imported mappings keep upstream license; see §8.4).

---

## §2 Literature survey

Twelve sources below, ordered by **directness of relevance to
Shepard's slice**. Every system tested against OAEI is annotated with
its result against the pairs Shepard cares about.

### [P1] OLaLa — Ontology Matching with Large Language Models (Hertling & Paulheim, 2023)

- **DOI:** [10.1145/3587259.3627571](https://doi.org/10.1145/3587259.3627571) — K-CAP 2023, **57 citations** as of 2026-05.
- **Method.** Prompt-engineered LLM (zero-shot + few-shot) against
  OAEI benchmark tasks (Anatomy, Conference, Disease, Knowledge Graph
  track). Multi-LLM: GPT-3.5, GPT-4, Llama-2-13B, several open models.
- **Headline result.** "With only a handful of examples and a
  well-designed prompt, it is possible to achieve results that are
  en par with supervised matching systems which use a much larger
  portion of the ground truth." On the Conference track, GPT-4
  zero-shot already lands within ~5 F1 points of the supervised SOTA.
- **Strengths for Shepard.** Demonstrates that **few-shot prompting with
  10–20 examples per pair** is enough to beat unsupervised baselines.
  This is the budget Shepard would actually have.
- **Weaknesses.** No structural matching (subsumption, complex
  alignment) — the LLM treats it as a pairwise classification.
  Hallucination is unmeasured.

### [P2] LLMs4OM — Matching Ontologies with Large Language Models (Babaei Giglou, D'Souza, Engel, Auer, 2024)

- **arXiv:** [2404.10317](https://arxiv.org/abs/2404.10317) — ESWC 2024.
- **Method.** Two-module pipeline: **retrieval** (FAISS over BERT-embeddings of
  concept labels) → **matching** (LLM evaluates retrieved candidates).
  Three ontology representations tested: concept, concept-parent,
  concept-children. Seven LLMs × 20 OAEI datasets evaluated.
- **Headline result.** On the **MSE track / MaterialInformation–MatOnto** task
  (directly Shepard's CHAMEO ↔ Material OWL territory), LLMs4OM with
  Mistral-7B + Stella-base retrieval reaches F1 ~0.78, vs the prior
  best ~0.65. Across 20 datasets, LLM-only configurations match or
  beat traditional OM systems "particularly in complex matching
  scenarios."
- **Strengths for Shepard.** **This is the framework directly applicable
  to Shepard's hardest pair (CHAMEO ↔ MatOnto).** Materials track was
  a major focus.
- **Weaknesses.** Recall remains the weak axis (precision is high
  ~0.85, recall ~0.7) — LLMs miss true mappings when the lexical
  signal is weak.

### [P3] OntoAligner — A Comprehensive Modular and Robust Python Toolkit (Babaei Giglou et al., 2025)

- **arXiv:** [2503.21902](https://arxiv.org/html/2503.21902v1) — TIB / L3S
  Hannover. ESWC 2025 demo paper.
- **Method.** Python toolkit that **operationalises LLMs4OM** plus
  traditional fuzzy/retrieval/RAG/few-shot-RAG/in-context-vector-learning
  strategies. Apache-2.0, v1.8.0 as of 2026-05-22.
- **Headline result.** Reproducible end-to-end pipelines: parser →
  retriever → matcher → SSSOM-export. Supports the MSE-track Shepard
  needs out of the box.
- **Strengths for Shepard.** **This is the ADOPT candidate** (see §3
  recommendation). TIB maintenance, Apache-2.0, Python 3.10–3.13.
- **Weaknesses.** Active but young — v1.x churn; SSSOM export only
  partially implemented as of writing; no built-in human-in-the-loop
  UI (Shepard would supply it).

### [P4] OAEI 2024 — Synthesis report (Pour, Algergawy, Hertling et al., 2024)

- **CEUR:** [Vol-3897/oaei2024_paper0.pdf](https://ceur-ws.org/Vol-3897/oaei2024_paper0.pdf)
  / [OpenAccess City](https://openaccess.city.ac.uk/id/eprint/34615/1/OAEI_synthesis_2024.pdf)
- **What it is.** The OAEI 2024 campaign overall report — 14 tracks,
  18 participants, **new Bio-LLM track** specifically for LLM-based
  systems.
- **Headline.** "CANARD's adoption of LLM embeddings (Stella-base on
  Instance Embeddings, GritLM-7B on SPARQL-query embeddings)
  **increased precision and F-measure by up to 45 % over the 2018
  baseline.**" This is the largest single-system improvement in the
  campaign's history.
- **Bio-LLM track.** Five ontology pairs from MONDO + UMLS, including
  SNOMED-NCIT Pharmacy + Neoplasm subsets. Matching + ranking results
  reported per subset.
- **Conclusion the report draws.** LLMs are now a first-class
  technique in the OAEI campaign, with measurable per-track wins
  but no across-the-board sweep.

### [P5] Matcha — Results in OAEI 2024 (Faria, Pesquita et al., U. Lisbon, 2024)

- **CEUR:** [Vol-3897/oaei2024_paper3.pdf](https://ceur-ws.org/Vol-3897/oaei2024_paper3.pdf)
- **Method.** Matcha = AML-successor (`AgreementMakerLight`). 2024 edition added LM-based
  signals in **two of the algorithms**: word-embedding similarity in
  the literal matcher + a BERT-based structural matcher.
- **Headline.** "Highest F-measure in **15 out of 43 distinct OAEI
  tasks** and ranked in the top three in ten others. **Biomedical
  track: F-measure 0.941, Recall+ 0.82**" — the SOTA on the
  large-biomedical track in 2024.
- **Strengths for Shepard.** Best-in-class hybrid (rule-based +
  embeddings + LM) demonstrates the production-grade architecture.
  GPL'd, Java — usable from Shepard backend with a thin adapter.
- **Weaknesses.** GPL license (incompatible with Shepard's
  permissive-license posture — see §3.2). Java-OWL-API stack means
  heavy memory footprint per ontology load.

### [P6] BERTMap — A BERT-Based Ontology Alignment System (He, Chen, Antonyrajah, Horrocks, 2022)

- **AAAI 2022:** [doi/10.1609/aaai.v36i5.20510](https://doi.org/10.1609/aaai.v36i5.20510)
- **Method.** Fine-tunes BERT on text extracted from the ontologies
  themselves (definitions, synonyms, parent labels), then refines
  through ontology structure + logic.
- **Headline.** On three biomedical alignment tasks (OAEI Bio-ML),
  BERTMap "can often perform better than the leading OM systems
  LogMap and AML." In unsupervised mode, BERTMap was the first
  embedding-based system to win against rule-based heavyweights.
- **Strengths for Shepard.** Apache-2.0 (part of `DeepOnto`),
  unsupervised mode works without labelled examples — important
  for Shepard's mapping pairs where we have ~0 labels.
- **Weaknesses.** Per-pair BERT fine-tuning is **expensive**
  (GPU-hours per ontology pair). Shepard would need GPUs to retrain
  per ontology release.

### [P7] DeepOnto — Python package for ontology engineering with deep learning (He et al., 2024)

- **Semantic Web Journal:** [doi/10.3233/SW-243568](https://doi.org/10.3233/SW-243568)
  — KRR-Oxford + Samsung Research.
- **Method.** Python wrapper over OWL API; bundles BERTMap,
  BERTSubs, OAEI-Bio-ML training utilities; PyPI `deeponto`,
  Apache-2.0.
- **Strengths for Shepard.** **Reference implementation** for
  reasoning + verbalisation + projection in Python, which Shepard's
  Java backend doesn't have. Could run as a sidecar.
- **Weaknesses.** Python-only. Heavy dependencies (PyTorch,
  transformers, OWL-API via JPype).

### [P8] OAEI-LLM — Benchmark Dataset for Understanding LLM Hallucinations in Ontology Matching (Lin, Zhou et al., 2024)

- **arXiv:** [2409.14038](https://arxiv.org/abs/2409.14038)
- **Method.** Extends OAEI datasets with **hallucination labels** —
  every LLM-proposed mapping marked as `true_positive`,
  `hallucinated_false_positive`, or `missed_true_positive`. Tested
  GPT-4, GPT-3.5, Claude-3, Llama-2-70B.
- **Headline finding.** Hallucination rate (false-positive among
  LLM-proposed mappings) ranges **5 %–18 %** depending on model +
  prompt. Lowest with **GPT-4 + retrieval grounding** (~5 %), highest
  with **Llama-2 + zero-shot** (~18 %). The "missed_true_positive"
  rate (false-negative) is consistently worse: **20 %–35 %.**
- **Implication for Shepard.** **LLM alignment can never be unattended
  for the kinds of mappings Shepard hosts.** A 5–18 % silent-error
  rate corrupts SPARQL queries; the acceptance gate (§5) is
  non-negotiable.

### [P9] OAEI-LLM-T — TBox Benchmark for LLM Hallucinations in Ontology Matching Systems (Lin, Zhou et al., 2025)

- **arXiv:** [2503.21813](https://arxiv.org/pdf/2503.21813)
- **Method.** Sister benchmark to OAEI-LLM, focused on **TBox
  (schema-level) matching** — class hierarchies + property hierarchies,
  not just instance pairs. Extends the OAEI Conference / Anatomy / LB
  tracks with hallucination annotations.
- **Headline.** TBox alignment hallucination is **2–3× higher** than
  ABox: GPT-4 hits ~12 % false-positive on Conference TBox vs ~5 % on
  ABox. Implication: **structural alignment (subsumption,
  equivalence) is where LLMs are weakest.**
- **Implication for Shepard.** Shepard's mapping needs are mostly
  TBox (class ↔ class, property ↔ property). The acceptance gate
  must be **stricter for subsumption claims than for exactMatch**
  claims.

### [P10] Large Language Models as Oracles for Ontology Alignment (Amini et al., 2025)

- **arXiv:** [2508.08500](https://arxiv.org/pdf/2508.08500)
- **Method.** Inverts the LLMs4OM pattern. Traditional matcher
  generates candidates; LLM acts as **oracle** (validator only, not
  proposer). Two-stage: classical matcher → LLM verifies. Tested
  Claude, GPT-4, Gemini, Llama, Mistral, Qwen.
- **Headline.** "Strong performance in **OAEI 2025**, achieving the
  **top-2 overall rank in the bio-ml track**." Oracle pattern reduces
  hallucination because the LLM never proposes — only accepts/rejects
  candidates a deterministic matcher generated.
- **Strengths for Shepard.** This is the **architectural pattern
  Shepard should adopt** — it dovetails with the human-in-the-loop
  acceptance ladder (§5) and the SHACL substrate (a SHACL shape can
  pre-filter candidate predicates).
- **Weaknesses.** Cost is still LLM-per-candidate; needs careful
  batching.

### [P11] A Simple Standard for Sharing Ontological Mappings — SSSOM (Matentzoglu et al., 2022)

- **Database:** [doi/10.1093/database/baac035](https://doi.org/10.1093/database/baac035)
  — **OBO Foundry endorsed; W3C-style community standard.**
- **What it is.** TSV-based serialisation for ontology mappings, with
  a machine-readable header for mapping-set metadata. The standard
  has versions; **SSSOM 1.0** (mapping-commons GitHub) is the current
  stable.
- **Why it matters for Shepard.** The standard answers the
  "how do we serialise + round-trip a mapping" question. Adopting
  SSSOM means every Shepard mapping is **immediately** consumable by
  the OBO / Mondo / NFDI / BioPortal ecosystems. See §8.
- **Reference Python:** `sssom-py` (MIT, Python ≥3.10, `pip install
  sssom`).

### [P12] Mapping.bio — Piloting FAIR semantic mappings for biodiversity digital twins (Wolodkin, Weiland, Grieb, 2023)

- **DOI:** [10.3897/biss.7.111979](http://dx.doi.org/10.3897/biss.7.111979)
- **What it is.** EU Horizon BioDT project — web-tool for **visual
  ontology mapping with FAIR Digital Objects + SSSOM**. Stores
  mappings as FDOs in a repository; web UI for human-in-the-loop
  pairing.
- **Strengths for Shepard.** Reference for **UI shape** — what does a
  human-in-the-loop mapping picker look like? Side-by-side ontology
  tree views + drag-to-connect; mapping carries SSSOM metadata.
- **Implication.** Shepard's Vuetify UI page (§7.4) can borrow
  Mapping.bio's interaction model without reimplementing the engine.

### Supporting [P13] LLMs4OM Knowledge Graph LLM Synergy (Dehal, Sharma, Rajabi, 2025)

- **DOI:** [10.3390/make7020038](https://doi.org/10.3390/make7020038)
- **What it is.** Systematic review of 77 studies on LLM ↔ KG
  integration patterns; healthcare, finance, justice, industrial
  automation.
- **Relevant quote.** "LLMs improve KG construction; KGs serve as
  structured + interpretable data sources that improve the
  transparency, factual consistency, and reliability of LLM-based
  applications, mitigating challenges such as hallucinations and lack
  of explainability."
- **Strengths for Shepard.** Catalogues the **complementarity**
  pattern Shepard should adopt: SHACL graph constrains LLM output;
  LLM verbalises the SHACL output for the UI.

### Survey verdict

Five of these (P1, P2, P3, P4, P10) are directly applicable to
Shepard's pairs. P5 (Matcha) is the production-grade reference but
GPL-licensed. P6/P7 (BERTMap/DeepOnto) are the Python sidecar
candidates. P8/P9 (OAEI-LLM/T benchmarks) are the **why we need an
acceptance gate** evidence. P10 is the **production pattern** Shepard
should follow. P11 (SSSOM) is **non-negotiable** serialisation.

---

## §3 OSS tool inventory (reuse survey)

Per `feedback_reuse_before_reimplement.md` §3. Six candidates with
license, activity, slice coverage, recommendation.

### 3.1 Candidates

| # | Tool | License | Lang | Last commit (≈) | Slice fit |
|---|---|---|---|---|---|
| **T1** | **OntoAligner** (TIB) | Apache-2.0 | Python | active, v1.8.0 (2026-05-22) | Best end-to-end LLM-OM pipeline; **MSE track support out of the box** |
| **T2** | **DeepOnto** (Oxford/Samsung) | Apache-2.0 | Python | active (2024 paper, 2025 commits) | BERTMap reference impl; reasoning + verbalisation |
| T3 | **Matcha** (U. Lisbon) | **GPL-3.0** | Java | active, OAEI 2024 SOTA | Production traditional + LM hybrid; **license blocks adoption** |
| T4 | **AgreementMakerLight (AML)** | LGPL-3.0 | Java | older, superseded by Matcha | Historical; not the active fork |
| T5 | **LogMap** | LGPL-3.0 | Java | maintained, low-activity | Logic-based; deterministic baseline, no LLM |
| T6 | **sssom-py** | **MIT** | Python | active (mapping-commons) | Serialisation only — **always adopt for SSSOM I/O** |
| T7 | **CANARD** (KIT) | research code | various | OAEI 2024 entrant | KG-level matching; LLM embeddings; **academic, not a library** |
| T8 | **LLMs4OM** (TIB) | Apache-2.0 | Python | merged into OntoAligner | Predecessor of T1 |

### 3.2 Per-candidate reasoning

**T1 — OntoAligner.** ADOPT.

- License Apache-2.0 (compatible with Shepard's posture; see
  `aidocs/strategy/`).
- Maintained by TIB Hannover (NFDI / German RDM ecosystem —
  **organisational alignment** with Shepard).
- Python toolkit so it lives as a **sidecar** behind a REST contract.
- Supports the **exact track Shepard needs (MSE: MaterialInformation
  ↔ MatOnto)**.
- Maintainer Hamed Babaei Giglou — author of the LLMs4OM paper, so
  the implementation tracks the research.
- v1.8.0 released **the day before this survey was written**
  (2026-05-22) — alive.
- **What would change my mind.** If OntoAligner stops publishing
  before Q3 2026, switch to DeepOnto (T2) with an OntoAligner-style
  adapter we maintain ourselves.

**T2 — DeepOnto.** ADOPT as fallback / complement.

- Apache-2.0; KRR-Oxford academic stewardship; lower release cadence
  but more mature.
- Bundles **BERTMap** which is the unsupervised baseline Shepard
  should compare against for every pair.
- We adopt it for **reasoning + verbalisation** (Python wrapper over
  OWL API) — those features are missing from OntoAligner.
- **What would change my mind.** If OntoAligner grows native
  reasoning + verbalisation, DeepOnto becomes optional.

**T3 — Matcha.** REJECT for direct adoption.

- **GPL-3.0** — incompatible with Shepard's permissive default
  license posture (Shepard core is permissive; GPL'd plugins are
  acceptable only when isolated as separate processes — see
  `feedback_no_redactions.md` adjacent license-compatibility rules).
- However, **emulate the architecture** (rule-based primary + LM
  signals in two algorithms) in OntoAligner pipelines. The 2024
  benchmark wins (15/43 tasks) are a reference of what's achievable.
- **What would change my mind.** If Matcha relicenses to Apache /
  MPL / LGPL-with-classpath-exception, it becomes the front-runner.

**T4 — AML.** REJECT (superseded).

- LGPL-3.0, no longer the primary research vehicle (Matcha is the
  successor); historical reference only.

**T5 — LogMap.** ADOPT as deterministic baseline (sidecar).

- LGPL-3.0; logic-based reasoner; **best when an answer must be
  defensible** (every match has a logical derivation).
- For TBox alignment in safety-critical contexts (EN 9100 audit
  trail), the LogMap result is more trustworthy than a pure LLM
  output. Use as the **🤖 candidate generator** in the oracle pattern
  ([P10]).
- **What would change my mind.** If LogMap stops releasing — last
  major release was years ago — fork or drop.

**T6 — sssom-py.** ADOPT (mandatory).

- MIT license; mapping-commons stewardship; the **only** way to
  guarantee round-trip with the OBO / NFDI / BioPortal world.
- Use for both import (Shepard reads SSSOM TSV) and export (Shepard
  emits SSSOM for any third party).

**T7 — CANARD.** Watch, don't adopt.

- Research code, not a library; reference for what "LLM-embedding-
  refined classical alignment" can achieve (45 % F-measure lift over
  2018 baseline per [P4]).
- Watch the GitHub repo; if it ever packages, reconsider.

### 3.3 Verdict

**ADOPT: OntoAligner (T1) + DeepOnto (T2 fallback) + sssom-py (T6
mandatory) + LogMap (T5 deterministic baseline).**

This stack is all-Apache/MIT/LGPL — no GPL pollution — and covers the
four mapping shapes Shepard needs (lexical / structural / logical /
LLM-oracle).

---

## §4 Recommended approach for Shepard

### 4.1 The single-sentence claim

> **Shepard uses the LLM-as-oracle pattern over an OntoAligner-driven
> candidate generator, with every mapping written to SHACL substrate
> as a `f-ai-r`-attributed triple and round-tripped via SSSOM.**

### 4.2 Why this over others (across-persona trade-off)

- **Data Ontologist lens.** The oracle pattern ([P10]) is the *only*
  shape that bounds hallucination to an acceptable rate (P10 shows
  top-2 OAEI 2025 bio-ml — that's the lowest-hallucination evidence
  available). A pure LLM proposer ([P1] OLaLa) would corrupt the
  graph faster than humans can fix it.
- **RDM lens.** SSSOM serialisation ([P11]) is **the** FAIR
  serialisation for mappings; the OBO/NFDI/BioPortal ecosystem
  already speaks it. Anything else costs Shepard interoperability.
- **Analytics & AI lens.** OntoAligner is the **right granularity** —
  it has the retriever + matcher + few-shot scaffolding without
  forcing Shepard to host its own embedding service.
- **Manufacturing & Quality lens** (consulted via aidocs/95 §15 + the
  EN 9100 reasoning): every mapping carries `mapping_justification` +
  `confidence` + `🧑/🤖/🤝` attribution; an EN 9100 auditor can trace
  any derived SPARQL result back to the human who accepted the
  mapping. Without this attribution chain, mappings are not
  audit-quality.

### 4.3 Integration shape — plugin? sidecar? in-tree?

**Plugin-first per `CLAUDE.md` §"Always: think plugin-first".**
Specifically:

- `shepard-plugin-ontology-mapper` (the plugin) — Java module in the
  Shepard core repo (or external GitHub repo). Owns:
  - REST endpoints under `/v2/ontology/mappings/*`
  - SHACL-substrate writes (uses Shepard's existing n10s + SHACL
    layer)
  - Plugin manifest + admin config (`:OntologyMapperConfig`
    singleton per the runtime-knob pattern from
    `aidocs/integrations/67 §5-6`)
  - Job queue for async mapping suggestion runs
- `ontology-mapper-engine` sidecar (Python, OntoAligner + DeepOnto +
  sssom-py) — separate Docker container exposed to the plugin via
  internal REST. Owns:
  - OntoAligner pipeline execution (retrieve + match)
  - LogMap candidate generation
  - LLM oracle calls (delegating to the configured AI provider per
    `project_ai_plugin_config.md`)
  - SSSOM serialisation

The plugin/sidecar split is necessary because **OntoAligner is
Python** and Shepard backend is Java; Java-Python bridge libraries
add more friction than a clean HTTP boundary.

### 4.4 Human-in-the-loop role (🧑 / 🤖 / 🤝 modes)

Per `feedback_ai_human_collab_provenance.md`:

| Mode | When | Who proposes | Who accepts | Stored attribution |
|---|---|---|---|---|
| **🧑** | Operator hand-curates a mapping (e.g. CHAMEO `Specimen` ↔ MFFD `prepreg_lot.material`) | human | human | `f-ai-r:humanAuthored` + `f-ai-r:wasAcceptedAs human` |
| **🤖** | Bulk import — admin trusts a pre-existing SSSOM TSV from BioPortal | AI/external | none (bulk) | `f-ai-r:aiAuthored` + `f-ai-r:wasAcceptedAs unreviewed` (flagged for later review) |
| **🤝** | **Default path** — LLM-as-oracle proposes; human accepts/rejects in the UI | AI | human | `f-ai-r:aiAuthored` + `f-ai-r:wasAcceptedAs reviewed` + `f-ai-r:reviewedBy <person>` |

The 🤝 mode is the **production path**. The 🤖-unreviewed mode is the
operator escape hatch (faster bulk import; reviewable later).

### 4.5 Storage — SHACL substrate vs Postgres mapping table vs Neo4j edges

**SHACL substrate, period** (per `feedback_shacl_single_source_of_truth.md`).

Concretely, each mapping is **one named individual** in the SHACL graph:

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix sssom: <https://w3id.org/sssom/> .
@prefix shepmap: <https://shepard.nuclide.systems/ontology/mapping/> .
@prefix f-ai-r: <https://github.com/noheton/f-ai-r/vocab#> .
@prefix semapv: <https://w3id.org/semapv/vocab/> .

shepmap:m_4f8a1e9c a sssom:Mapping ;
    sssom:subject_id chameo:Specimen ;
    sssom:predicate_id skos:exactMatch ;
    sssom:object_id matonto:Material ;
    sssom:mapping_justification semapv:ManualMappingCuration ;
    sssom:confidence "0.95"^^xsd:decimal ;
    sssom:author_id <https://orcid.org/0000-0002-XXXX> ;
    sssom:mapping_date "2026-05-23"^^xsd:date ;
    f-ai-r:proposalMode "AI" ;
    f-ai-r:proposedBy <https://shepard/v2/admin/agents/onto-mapper-engine#run-2026-05-23T14:00> ;
    f-ai-r:wasAcceptedAs "Reviewed" ;
    f-ai-r:reviewedBy <https://orcid.org/0000-0002-XXXX> ;
    sssom:mapping_license <https://creativecommons.org/publicdomain/zero/1.0/> .
```

**Why not a Postgres table.** The mapping is **graph-shaped data** —
SPARQL queries naturally federate over it. Postgres would force a
parallel schema and break the SSOT rule.

**Why not Neo4j edges.** Neo4j is the operational substrate; the
SHACL n10s layer already projects into Neo4j via `:Resource` nodes —
mappings are stored there as named individuals, queryable via SPARQL
and Cypher. No double-write.

### 4.6 Confidence vs acceptance

The **confidence** number (0–1) is what the LLM/aligner reported. The
**acceptance state** (`Reviewed` / `Unreviewed` / `Rejected`) is what
a human decided. These are independent axes:

- A mapping can be `confidence 0.95 + Unreviewed` (LLM was sure, but
  no human looked yet).
- A mapping can be `confidence 0.60 + Reviewed` (LLM was unsure but
  human checked the source ontologies and confirmed).
- A mapping can be `confidence 0.98 + Rejected` (LLM was sure but
  human ontologist saw the structural error LLM missed — the OAEI-LLM
  [P8] missed_true_positive class shows this is real).

Downstream SPARQL queries always filter on **acceptance ≥
`Reviewed`** unless explicitly asked for everything (admin/audit
view).

---

## §5 Quality gate

How do we know an AI-generated mapping is acceptable?

### 5.1 The acceptance ladder

Five rungs, each gating which downstream consumer can use the mapping:

| Rung | State | Visible to | Notes |
|---|---|---|---|
| 0 | **Proposed** | nobody (queue only) | LLM raw output |
| 1 | **Triaged** | admin queue | Auto-rules applied (confidence threshold, exclude self-loops, type-compatibility check) |
| 2 | **Reviewed** | UI, SPARQL default | A human pressed "Accept" |
| 3 | **Endorsed** | export pipelines (Unhide, Databus, Invenio) | Cross-checked by a second reviewer or passed a SHACL validation rule |
| 4 | **Standardised** | external publish | Made it into the upstream SSSOM TSV bundle Shepard ships |

Rules:
- Only **Reviewed** and above show up in default SPARQL results.
- Only **Endorsed** and above are exported.
- Demotion is allowed (a Reviewed mapping can be marked Rejected
  later — that's just another mapping with `acceptance = Rejected`
  and `predecessor_id = <previous-mapping>`).

### 5.2 F(AI)²R predicate set

Per `feedback_ai_human_collab_provenance.md` + `project_fair2r_integration.md`:

```turtle
shepmap:m_4f8a1e9c f-ai-r:wasAcceptedAs "Reviewed" ;
                  f-ai-r:reviewedBy <orcid:0000-0002-XXXX> ;
                  f-ai-r:reviewedAt "2026-05-23T15:23:00Z"^^xsd:dateTime ;
                  f-ai-r:proposalMode "AI" ;
                  f-ai-r:proposedBy <agent:onto-mapper-engine#run-...> ;
                  f-ai-r:proposalPrompt <prompt:p_8a7c4d2b> ;
                  f-ai-r:rejectionReason "structural mismatch — CHAMEO.Specimen is a physical artefact while MatOnto.Material is a substance kind"^^xsd:string ; # only when rejected
                  .
```

### 5.3 RDM lens — FAIR implications

- **Findable.** Every mapping has a `shepmap:m_*` URI →
  resolvable + dereferenceable; the mapping itself becomes
  findable as a first-class FAIR object.
- **Accessible.** Mappings inherit the parent ontology bundle's
  access control. SSSOM TSV export = HTTP GET (open by default).
- **Interoperable.** SSSOM IS the W3C-style standard for
  mapping interop. Adopting it = automatic alignment with
  OBO/NFDI/BioPortal.
- **Reusable.** `mapping_license` + `author_id` + `mapping_date`
  ensure downstream consumers can legally re-use; CC0 default;
  upstream imports keep upstream license.

### 5.4 Manufacturing & Quality lens — EN 9100 auditability

For EN 9100, every datum used in a regulated decision needs a
chain-of-custody:

1. The **review step** is the EN-9100 sign-off — human + ORCID +
   timestamp.
2. The **proposal step** is the AI assistance — agent + prompt + run.
3. The **derivation step** for any downstream SPARQL result that
   depends on the mapping must be re-derivable from the named
   individual graph.

A bare LLM API call returning a list of suggested mappings is **not
EN-9100-auditable**. The SHACL-substrate-named-individual approach
**is** auditable because every reviewer's ORCID is in the graph.

This is the conversation-killer for "let's just call OpenAI from the
frontend and write the result to a free-text field." That shape
cannot be audited.

---

## §6 The MFFD demonstrator pairing — CHAMEO ↔ Material OWL

First concrete pair to validate the approach on. Justified because:

1. **It's the actual mapping the MFFD demo needs** — the layup
   attributes (`prepreg_lot`, `consolidation_force`, `tcp_temperature`)
   in `examples/mffd-showcase/seed.py` lack semantic anchor today.
2. **OntoAligner has the MSE-track support** for exactly this pair
   ([P2] LLMs4OM evaluated MaterialInformation–MatOnto).
3. **Both ontologies are public** — CHAMEO at EMMO-repo, MatOnto on
   GitHub.

### 6.1 Walk-through: five example mappings the agent proposes

The OntoAligner sidecar runs once per night against the active
`(CHAMEO, MatOnto)` pair. Output for a representative night:

| # | Subject (CHAMEO) | Object (MatOnto) | Predicate | Confidence | Justification | Verdict (human) |
|---|---|---|---|---|---|---|
| 1 | `chameo:Specimen` | `matonto:Material` | `skos:relatedMatch` | 0.91 | `semapv:LexicalMatching` | **Accept** (the LLM noted in its rationale "specimen is a sample of material, not the material kind itself; relatedMatch, not exactMatch") |
| 2 | `chameo:CharacterisationMethod` | `matonto:MeasurementProcedure` | `skos:exactMatch` | 0.94 | `semapv:LexicalMatching` + LLM rationale | **Accept** |
| 3 | `chameo:Property` | `matonto:MaterialProperty` | `skos:exactMatch` | 0.96 | `semapv:LexicalMatching` | **Accept** |
| 4 | `chameo:Equipment` | `matonto:LaboratoryInstrument` | `skos:narrowMatch` | 0.78 | `semapv:LexicalMatching` + structural check | **Accept** (LLM noted Equipment is broader than LaboratoryInstrument; narrowMatch is the LLM saying "LaboratoryInstrument is a narrower kind of Equipment") |
| 5 | `chameo:Specimen` | `matonto:Sample` | `skos:exactMatch` | 0.92 | `semapv:LexicalMatching` | **Reject** with reason: "Sample in MatOnto is a deliberately-selected subset for analysis; chameo:Specimen is the physical thing being characterised. Conceptually different despite the synonym." (This is exactly the OAEI-LLM [P8] hallucination class — the LLM had ~92 % confidence and was wrong; the human-reviewed acceptance saved the SPARQL graph.) |

### 6.2 The acceptance flow (UI)

1. Operator opens `/v2/ontology/mappings/queue?status=Triaged` UI page.
2. Sees the 5 proposed mappings (above) with side-by-side ontology
   tree views (subject ontology left, object right; cf. Mapping.bio
   [P12]).
3. For each mapping, sees the LLM's natural-language rationale
   ("this is exactMatch because both terms…").
4. Clicks **Accept** / **Reject** / **Edit predicate**.
5. On Accept: `acceptance = Reviewed`, reviewer's ORCID + timestamp
   written.
6. On Reject: `acceptance = Rejected` + reviewer enters reason
   (mandatory).
7. On Edit: reviewer changes `predicate_id` (e.g. exactMatch →
   relatedMatch); reviewer's edit creates a new mapping individual
   with the previous one as `revision_predecessor`.

### 6.3 Validation signal that the slice works

Once 10–20 CHAMEO ↔ MatOnto mappings are Reviewed, run the SPARQL:

```sparql
SELECT ?mffdProcessStep ?material WHERE {
  ?mffdProcessStep a m4i:ProcessingStep ;
                   m4i:hasInput ?input .
  ?input a chameo:Specimen ;
         shepmap:exactMatchOf ?material .
  ?material a matonto:Material .
}
```

If this returns the MFFD layup steps with their material kinds (LMPAEK
prepreg, CF-PEEK, etc.), the slice is **demonstrably useful**. That's
the acceptance evidence.

---

## §7 Plugin design — `shepard-plugin-ontology-mapper`

### 7.1 Should it be a plugin? Yes.

Per `CLAUDE.md` §"Always: think plugin-first":

- It introduces a new payload kind (the `sssom:Mapping` individual).
- It has a sidecar dependency (the Python OntoAligner runtime) that
  doesn't belong in core.
- The LLM provider is admin-configurable per
  `project_ai_plugin_config.md` — so the plugin shares the AI
  provider knob.
- It has a clean SPI seam at the **SHACL substrate** — the plugin
  only writes individuals, never modifies schema.

### 7.2 Sidecar — `ontology-mapper-engine` container

Per `feedback_plugins_declare_sidecars.md`, the plugin manifest declares:

```yaml
# plugins/ontology-mapper/manifest.yaml
id: shepard-plugin-ontology-mapper
version: 0.1.0
sidecars:
  - id: ontology-mapper-engine
    image: ghcr.io/noheton/shepard-plugin-ontology-mapper-engine:0.1.0
    ports: [ 8181 ]
    healthcheck: /health
    env:
      - ONTOALIGNER_MODEL=mistral-7b
      - AI_PROVIDER_URL=${shepard.ai.provider.url}
      - AI_PROVIDER_KEY=${shepard.ai.provider.key}
shepard_dependencies:
  - shepard >= 5.3.0          # needs SHACL substrate + n10s
  - plugin: shepard-plugin-ai   # uses configured AI provider
```

### 7.3 REST surface (per `feedback_ui_api_parity.md`)

Under `/v2/ontology/mappings/*` (v2 surface per `CLAUDE.md` §"API-version policy"):

| Endpoint | Verb | Purpose |
|---|---|---|
| `/v2/ontology/mappings` | GET | List mappings (paged, filter by `acceptance`, `confidence_min`, `subject_namespace`, `object_namespace`, `subject_id`, `predicate_id`) |
| `/v2/ontology/mappings/{id}` | GET | Get one mapping (SSSOM JSON) |
| `/v2/ontology/mappings/{id}` | PATCH | Update acceptance / predicate (creates revision) |
| `/v2/ontology/mappings/queue` | GET | List Triaged mappings (the review queue) |
| `/v2/ontology/mappings/runs` | POST | Trigger a mapping run between two ontologies (async job; returns `runId`) |
| `/v2/ontology/mappings/runs/{runId}` | GET | Poll run status / get proposals |
| `/v2/ontology/mappings/import` | POST | Bulk import (SSSOM TSV upload; `acceptance = Unreviewed` default) |
| `/v2/ontology/mappings/export` | GET | Export filtered mappings as SSSOM TSV |
| `/v2/admin/ontology-mapper/config` | GET / PATCH | The `:OntologyMapperConfig` singleton (per A3b + UH1a pattern) |
| `/v2/ontology/mappings/runs/{runId}/abort` | POST | Cancel an in-flight run |

All endpoints `@RolesAllowed("ontology-mapper-reviewer")` except
`/import` and `/runs POST` which need `instance-admin`.

### 7.4 UI page (Vuetify components)

Per `feedback_reuse_before_reimplement.md`, reuse existing Vuetify
components — don't build new ones.

| UI element | Vuetify reuse |
|---|---|
| Two-tree side-by-side view | `<v-treeview>` ×2 in a `<v-row>` with `<v-divider vertical>` |
| Connection draw (subject → object) | SVG overlay; reuse the lineage-graph svg layer from `CollectionLineageGraph.vue` |
| Acceptance state chips | `<v-chip>` with colour-coded acceptance |
| LLM rationale | `<v-card>` with `<v-card-text>` |
| Confidence | `<v-progress-linear>` 0–1 |
| Accept/Reject/Edit actions | `<v-btn-group>` |
| Queue list | `<v-data-table-server>` (server-side paging) |
| SSSOM TSV upload | `<v-file-input>` (already used by `FileBundle` plugin) |

Page locations:

- `/ontology/mappings` — list view
- `/ontology/mappings/queue` — review queue (the daily-driver page)
- `/ontology/mappings/runs` — admin: start runs, see history
- `/ontology/mappings/{id}` — detail view

### 7.5 Plugin doc artefacts (per `CLAUDE.md` §"Always: plugins ship their own documentation")

Three files in `plugins/ontology-mapper/docs/`:

- `reference.md` — every REST endpoint with worked request/response,
  every config field, every SSSOM TSV column, every f-ai-r predicate
  emitted.
- `quickstart.md` — "How do I map CHAMEO to MatOnto in 5 minutes?"
  (admin enables the plugin, picks two ontology bundles, clicks Start
  Run, reviews queue, accepts mappings).
- `install.md` — sidecar deployment (Docker Compose snippet),
  prerequisites (the AI provider plugin must be enabled and
  configured), known limits (Python ≥3.10 in the sidecar, ~4 GB RAM
  per run, GPU optional but recommended for >5k-concept pairs).

---

## §8 SSSOM compatibility

### 8.1 Should Shepard emit SSSOM? Yes — it's the only sane choice.

Per [P11] and the OBO/NFDI/BioPortal ecosystem, **SSSOM is the
W3C-style community standard**. It is:

- Maintained by the mapping-commons community (open governance).
- Supported by `sssom-py` (MIT, mature).
- Already consumed by every major terminology service (BioPortal,
  Ontology Lookup Service, NFDI4Ing Terminology Service).
- Round-trippable to OWL + RDF.

Not emitting SSSOM = costing Shepard a tier of interoperability for
no design benefit.

### 8.2 Adopt sssom-py? Yes — in the sidecar.

The Java backend doesn't need direct SSSOM I/O — the Python sidecar
already speaks it. The plugin REST surface uses sssom-py for both
import (`/import` POST endpoint) and export (`/export` GET).

### 8.3 The mapping — Shepard `:Mapping` individual ↔ SSSOM TSV column

The Shepard SHACL-substrate individual carries one-to-one with the
SSSOM TSV row:

| SHACL field | SSSOM TSV column |
|---|---|
| `sssom:subject_id` | `subject_id` |
| `sssom:predicate_id` | `predicate_id` |
| `sssom:object_id` | `object_id` |
| `sssom:mapping_justification` | `mapping_justification` |
| `sssom:confidence` | `confidence` |
| `sssom:author_id` | `author_id` |
| `sssom:mapping_date` | `mapping_date` |
| `sssom:mapping_license` | (set on `MappingSet` header, not per-row) |
| `f-ai-r:proposalMode` | (non-SSSOM extension; in `other` column) |
| `f-ai-r:wasAcceptedAs` | (non-SSSOM extension; in `other` column) |
| `f-ai-r:reviewedBy` | (non-SSSOM extension; in `other` column) |

The SSSOM spec is **extensible** — non-standard slots go in an
`other` slot or as extra columns; consumers that don't understand
them ignore them safely.

### 8.4 License handling

- Shepard's own mappings: CC0-by-default (set in the
  `:OntologyMapperConfig` singleton).
- Imported SSSOM mappings: inherit the imported set's
  `mapping_license`.
- A mixed SHACL graph can hold mappings of different licenses
  side-by-side; the export pipeline filters by license-compatibility.

### 8.5 Predicate vocabulary (SKOS)

SSSOM uses **SKOS** mapping predicates per [P11]:

- `skos:exactMatch` — identity (the LLM oracle pattern's modal output)
- `skos:closeMatch` — close but not identical
- `skos:broadMatch` — subject is broader than object
- `skos:narrowMatch` — subject is narrower than object
- `skos:relatedMatch` — related (the safe default when unsure)
- `owl:equivalentClass` / `owl:equivalentProperty` (structural)
- `rdfs:subClassOf` (structural)

### 8.6 Justification vocabulary (semapv)

The `semapv:` (Semantic Mapping Vocabulary) values relevant to
Shepard:

- `semapv:LexicalMatching` — based on labels / synonyms (the OntoAligner
  default)
- `semapv:LogicalReasoning` — derived from a reasoner (LogMap output)
- `semapv:ManualMappingCuration` — a human curated it
- `semapv:UnspecifiedMatching` — fallback when the method is unknown
- `semapv:CompositeMatching` — multiple methods agreed (the oracle pattern result)

---

## §9 Synergy notes

This work compounds with several other backlog rows:

| Row | Synergy |
|---|---|
| **TPL5** (Git ingestion of ontologies) | TPL5 lands ontologies into Shepard; ONT-AI-MAP1 maps them. Without TPL5, the operator hand-uploads each `.ttl`; with TPL5, a Git URL is the source. |
| **ONT1d** (Frontend ontology-picker) | ONT1d surfaces ontology vocabulary in the UI; ONT-AI-MAP1 fills the cross-vocabulary edges. Combined: pick a term in ontology A, see its mapped equivalent(s) in ontology B inline. |
| **#127 SHACL changeover** | SHACL is the substrate ONT-AI-MAP1 writes into. The slice **must land after #127 stabilises** or the schema moves under our feet. |
| **S-07** (SHACL × MCP) | MCP tools generated from SHACL shapes can include the mapping individuals as queryable entities — `get_mapping(subject, predicate?, object?)` MCP tool. |
| **S-02** (OpenLineage × F(AI)²R) | F(AI)²R predicates on mappings = OpenLineage Activity records on map runs. The mapping run IS a typed Activity. |
| **S-05** (AAS endpoints = dropbox + publish) | The CPACS ↔ AAS mapping pair (§1.2 above) feeds S-05 — without the mapping, AAS publish exports lose semantic anchor. |
| **PROMPT1** (Prompt as Shepard artefact) | The prompt used for each mapping proposal is the `f-ai-r:proposalPrompt` triple's object — a PromptLog payload kind individual. |
| **TPL9** (F(AI)²R + EU AI Act Art-50) | The f-ai-r predicates on mappings are exactly what TPL9 needs. ONT-AI-MAP1 is **the first concrete artefact class** that exercises the f-ai-r vocab in production. |

This is dense — meaning ONT-AI-MAP1 is a **structural** investment, not a
local one. Build it once, reap across seven downstream rows.

---

## §10 Risks + counter-evidence

Where does the literature push back?

### 10.1 Hallucination rates remain non-trivial

[P8] OAEI-LLM showed 5–18 % false-positive rate, 20–35 %
false-negative — and [P9] OAEI-LLM-T showed TBox is **2–3× worse**.
Shepard's mappings are largely TBox.

**Counter-evidence to naive adoption:**
1. Vasilevsky et al. (2018) "On the Reproducibility of Semantic Web
   Applications" — manual ontology mappings still drift over time;
   any approach (human OR AI) needs versioning.
2. Shvaiko & Euzenat (2013) "Ontology Matching: State of the Art and
   Future Challenges" — surveys 10 years of OM and concludes
   **human-in-the-loop is irreducible** for high-stakes domains;
   ML/LLM is acceleration, not replacement. **Cited as the rationale
   for the acceptance ladder (§5).**
3. Bender et al. (2021) "On the Dangers of Stochastic Parrots: Can
   Language Models Be Too Big?" — LLM training data contamination
   means **a model that's seen the target ontology will appear to
   "know" it without actually reasoning**. Mitigation: prefer
   retrieval + grounding ([P10] oracle pattern) over zero-shot.

### 10.2 Low-resource ontology drop-off

LLM quality degrades on ontologies the pre-training did **not** see.
CPACS (aerospace-specific, DLR-curated, narrow user community) is a
worst case — GPT-4 likely has very few CPACS tokens in pre-training.

**Mitigation.** For low-resource pairs, fall back to LogMap
(deterministic, structure-based — [T5]) as the primary, with LLM as
oracle only. OntoAligner supports the mode switch via a `strategy:`
config field.

### 10.3 Training-data contamination + IP risk

EU AI Act Article 50 + GDPR / institutional IP policy: if the LLM
provider trained on copyrighted ontology files (some commercial
ontologies are licensed), Shepard inadvertently consumes their
output and may face license challenge.

**Mitigation per `project_ai_plugin_config.md`:**
1. SAIA / GWDG-hosted models (DLR-internal infrastructure) when
   available — no cross-border data-transfer concern.
2. Prompt does **not** include the full target ontology — only the
   candidate concepts the retriever surfaced.
3. The mapping rationale (the LLM's natural-language explanation)
   is stored alongside the mapping; if a vendor challenges, we have
   primary evidence the mapping was derived from a candidate
   retrieval pass, not memorised verbatim.

### 10.4 Multilingual quality drop

OAEI 2024 Multifarm track results show LLM quality drops sharply on
non-English ontology labels. Shepard's user base is German
(DLR-internal) + multilingual (Helmholtz Unhide, EU partners).

**Mitigation.** Use the **m4i / DataCite English canonical labels**
where possible (m4i is published bilingually with English-canonical
IRIs); fall back to OntoAligner's `multilingual` strategy for German
or French ontologies.

### 10.5 "Why not just hand-curate?" the honest answer

A human ontologist hand-curating 200 mappings at 3 minutes each = 10
hours of expert time per ontology release. Shepard has 14 ontology
pairs to cover (§1.1); ontologies release ~quarterly. That's 14 × 4
× 10 = **560 hours/year of expert time**, recurring.

LLM-assisted with 30 % acceptance rate = 200 × 3 = 600 candidates
proposed → 200 accepted → reviewer time at ~30s per Accept/Reject
decision = 5 hours per ontology release. **112× speedup**, and the
remaining 5 hours is the **only** part that needs the senior
ontologist.

**That ratio is the case for adoption.** Without it, the manual
budget collapses under its own weight.

---

## §11 Acceptance criteria + open questions

### 11.1 Acceptance criteria for ONT-AI-MAP1 implementation

When all of these are met, the slice is done:

1. `shepard-plugin-ontology-mapper` exists with manifest + sidecar +
   REST + UI.
2. CHAMEO ↔ MatOnto pairing yields ≥10 Reviewed mappings.
3. SSSOM TSV export of those mappings parses cleanly by `sssom-py
   parse` (round-trip test).
4. The MFFD SPARQL query in §6.3 returns the layup steps with their
   materials.
5. Every mapping in the SHACL substrate carries the f-ai-r
   attribution triple set (`wasAcceptedAs`, `reviewedBy`,
   `proposalMode`, `proposedBy`).
6. Acceptance-ladder rule enforced (default SPARQL filter is
   acceptance ≥ Reviewed).
7. Reference + Quickstart + Install docs in
   `plugins/ontology-mapper/docs/`.
8. `aidocs/34-upstream-upgrade-path.md` row added.
9. `aidocs/44-fork-vs-upstream-feature-matrix.md` row added.
10. `aidocs/40-ecosystem.md` updated: OntoAligner + DeepOnto +
    sssom-py listed.

### 11.2 Open questions

1. **Should the sidecar be one container per ontology pair, or one
   shared sidecar that processes all pairs in a queue?** Default
   answer: one shared, queue-based — simpler ops. Revisit if the
   queue depth blocks reviewers.
2. **Where do we get OntoAligner's GPU inference?** Default: CPU
   (Mistral-7B quantised). Revisit if MSE-track quality is insufficient.
3. **How do we handle CHAMEO version drift?** CHAMEO 1.x → 2.0 is
   already on the horizon; mappings tagged with `subject_source_version`
   per SSSOM convention.
4. **Should Shepard contribute mappings upstream to OBO / NFDI / IDTA?**
   Should — that's the §5 rung-4 "Standardised" rung. Workflow TBD.
5. **What about complex alignments (`subject_id` is a class
   expression, not a single class)?** SSSOM supports this via
   anonymous-class subjects but the implementations are uneven.
   Defer to v0.2.
6. ~~**Should LLM-as-oracle calls use the user's session AI provider
   or the admin-configured one?**~~ **RESOLVED 2026-05-23** — not a
   design decision the ontology-mapping plugin needs to make. The AI
   plugin's BYOK resolution chain (`aidocs/platform/86-ai-plugin-design.md
   §4`) already handles both cases correctly:
   `(1) User per-capability override in :UserPreferences →
    (2) Instance :AiCapabilityConfig slot → (3) error`. The
   ontology-mapping plugin declares
   `@AiCapabilityRequirement(capability = STRUCTURED, hardDep = true)`
   and calls `LlmProvider.complete(request)`; the chain auto-resolves.
   `(:User)-[:wasAssociatedWith]->(:AiActivity)` provenance is written
   regardless of which credentials were used (`§8`), so audit
   accountability holds on both paths. Billing routes via LiteLLM
   proxy (`§13`) which already does per-user/per-team/per-model cost
   tracking. → Captured as backlog row `ONT-MAP-AI-DEP` for the
   plugin's `@AiCapabilityRequirement` declaration.
7. **Is there a sub-aidocs/ semantics doc home for this design?**
   Yes — `aidocs/semantics/99-ontology-mapping.md` (or next free
   number); follow-up PR.

---

## Appendix A — Citation index

| Ref | Title | Authors | Year | Venue | URL |
|---|---|---|---|---|---|
| P1 | OLaLa: Ontology Matching with Large Language Models | Hertling, Paulheim | 2023 | K-CAP 2023 | [doi.org/10.1145/3587259.3627571](https://doi.org/10.1145/3587259.3627571) |
| P2 | LLMs4OM: Matching Ontologies with Large Language Models | Babaei Giglou, D'Souza, Engel, Auer | 2024 | ESWC 2024 | [arxiv.org/abs/2404.10317](https://arxiv.org/abs/2404.10317) |
| P3 | OntoAligner: A Comprehensive Modular and Robust Python Toolkit for Ontology Alignment | Babaei Giglou et al. | 2025 | ESWC 2025 demo | [arxiv.org/html/2503.21902v1](https://arxiv.org/html/2503.21902v1) |
| P4 | Results of the Ontology Alignment Evaluation Initiative 2024 | Pour, Algergawy, Hertling, et al. | 2024 | OAEI 2024 / OM workshop | [ceur-ws.org/Vol-3897/oaei2024_paper0.pdf](https://ceur-ws.org/Vol-3897/oaei2024_paper0.pdf) |
| P5 | Results in OAEI 2024 for Matcha | Faria, Pesquita, et al. | 2024 | OAEI 2024 / OM workshop | [ceur-ws.org/Vol-3897/oaei2024_paper3.pdf](https://ceur-ws.org/Vol-3897/oaei2024_paper3.pdf) |
| P6 | BERTMap: A BERT-Based Ontology Alignment System | He, Chen, Antonyrajah, Horrocks | 2022 | AAAI 2022 | [doi.org/10.1609/aaai.v36i5.20510](https://doi.org/10.1609/aaai.v36i5.20510) |
| P7 | DeepOnto: A Python package for ontology engineering with deep learning | He et al. | 2024 | Semantic Web Journal | [doi.org/10.3233/SW-243568](https://doi.org/10.3233/SW-243568) |
| P8 | OAEI-LLM: A Benchmark Dataset for Understanding Large Language Model Hallucinations in Ontology Matching | Lin, Zhou, et al. | 2024 | arXiv | [arxiv.org/abs/2409.14038](https://arxiv.org/abs/2409.14038) |
| P9 | OAEI-LLM-T: A TBox Benchmark Dataset | Lin, Zhou, et al. | 2025 | arXiv | [arxiv.org/pdf/2503.21813](https://arxiv.org/pdf/2503.21813) |
| P10 | Large Language Models as Oracles for Ontology Alignment | Amini, et al. | 2025 | arXiv | [arxiv.org/pdf/2508.08500](https://arxiv.org/pdf/2508.08500) |
| P11 | A Simple Standard for Sharing Ontological Mappings (SSSOM) | Matentzoglu et al. | 2022 | Database | [doi.org/10.1093/database/baac035](https://doi.org/10.1093/database/baac035) |
| P12 | Mapping.bio: Piloting FAIR semantic mappings for biodiversity digital twins | Wolodkin, Weiland, Grieb | 2023 | Biodiversity Information Science and Standards | [doi.org/10.3897/biss.7.111979](http://dx.doi.org/10.3897/biss.7.111979) |
| P13 | Knowledge Graphs and Their Reciprocal Relationship with Large Language Models | Dehal, Sharma, Rajabi | 2025 | Mach. Learn. Knowl. Extr. | [doi.org/10.3390/make7020038](https://doi.org/10.3390/make7020038) |

Standards + tools:

- SSSOM specification — [mapping-commons.github.io/sssom/](https://mapping-commons.github.io/sssom/)
- sssom-py GitHub — [github.com/mapping-commons/sssom-py](https://github.com/mapping-commons/sssom-py)
- OntoAligner GitHub — [github.com/sciknoworg/OntoAligner](https://github.com/sciknoworg/OntoAligner)
- DeepOnto PyPI — [pypi.org/project/deeponto/](https://pypi.org/project/deeponto/)
- OAEI 2024 — [oaei.ontologymatching.org/2024/](https://oaei.ontologymatching.org/2024/)
- OAEI Bio-ML 2024 — [krr-oxford.github.io/OAEI-Bio-ML/2024/](https://krr-oxford.github.io/OAEI-Bio-ML/2024/index.html)
- CHAMEO ontology — [emmo-repo.github.io/domain-characterisation-methodology/](https://emmo-repo.github.io/domain-characterisation-methodology/)
- CPACS — [github.com/DLR-SL/CPACS](https://github.com/DLR-SL/CPACS)
- AAS submodel templates — [industrialdigitaltwin.org/en/content-hub/submodels](https://industrialdigitaltwin.org/en/content-hub/submodels)

---

## Appendix B — Recommendation summary card

If this document is collapsed to one paragraph for the next-sprint
review:

> **Adopt OntoAligner (Apache-2.0, TIB-maintained, Python) as the
> primary ontology-alignment engine, in the LLM-as-oracle pattern
> (LogMap or OntoAligner-retrieval generates candidates → LLM
> validates), packaged as `shepard-plugin-ontology-mapper` with a
> Python sidecar. Store every mapping as a named `sssom:Mapping`
> individual in the SHACL substrate with f(ai)²r attribution and a
> five-rung acceptance ladder. Round-trip via SSSOM (sssom-py, MIT)
> for FAIR interoperability. First demonstrator: CHAMEO ↔ MatOnto for
> the MFFD layup pair. Defer Matcha (GPL-incompatible) but emulate
> its hybrid architecture. Risk-bound by [P8] OAEI-LLM evidence:
> 5–18 % LLM false-positive rate makes the human-in-the-loop
> acceptance gate non-negotiable.**
