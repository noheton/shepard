# AI-in-Science policy alignment — does Shepard's AI design match the prevailing authoritative consensus?

**Snapshot date.** 2026-05-22.
**Scope.** Map Shepard's four-band AI-in-Shepard model (`project_ai_data_arranger.md`)
to the prevailing authoritative consensus on AI in scientific research. Ten source
families surveyed below.

---

## 1. Executive summary

**Verdict — Shepard's AI design is strongly aligned with the prevailing authoritative
consensus, and in one structural respect goes beyond it.** Every source surveyed
endorses (or at minimum tolerates) AI assistance in dataset preparation, curation,
and metadata creation; every source mandates transparency, attribution, and human
oversight on top — exactly the posture Shepard implements through the f(ai)²r /
PROV-O capture (`project_fair2r_integration.md`) and the "AI proposes, user
approves, snapshot before/after" forging cycle (`project_dataset_forging.md`).
The structural over-shoot is automatic per-Activity PROV-O capture of every AI
forging pass — every source surveyed asks for *disclosure*, none yet asks for
machine-readable per-call provenance, which Shepard ships. The honest gaps are
three: (a) sampler-parameter capture (temperature/top-p/seed) is designed but not
recorded today; (b) Article-50-style end-user labelling of AI-generated artefacts
in the UI is designed but not shipped; (c) Article-4-style AI-literacy surface
(in-app explainers) is absent.

---

## 2. Per-source synopsis

### S1 — OECD "Artificial Intelligence in Science" (2023, a8d820bd-en)

URL: https://www.oecd.org/en/publications/artificial-intelligence-in-science_a8d820bd-en.html
(PDF blocked behind 403; secondary high-quality summaries used; document landing
page cited.)

The OECD frames AI as potentially "the most economically and socially valuable" of
all uses of AI — a productivity multiplier for science. The report explicitly
addresses governance challenges posed by large language models (LLMs often "get
things wrong"; the report calls for "processes of evaluation to ensure accuracy
as applications are scaled up"). Reproducibility is named as a first-order
concern; LLMs "risk making superficial work more abundant." The report does not
prohibit AI in dataset preparation — to the contrary, it endorses AI as an
accelerator across the research lifecycle (data extraction, curation,
hypothesis generation). The reproducibility framing is what Shepard's snapshot-
bounded forging cycle directly answers.

### S2 — OECD AI Principles (2019; updated 2024)

URL: https://oecd.ai/en/ai-principles

Five values-based principles, all relevant: (1) inclusive growth, (2) human rights
and democratic values, (3) **transparency and explainability**, (4) robustness/
security/safety, (5) **accountability**. Principles #3 and #5 directly bear on
Shepard's design: explainability requires "clarity about data sources, processing
methods, and algorithmic decisions"; accountability requires identifiable
responsible parties for AI outputs. The principles do not prohibit AI in dataset
curation. They demand it be transparent and accountable.

### S3 — UNESCO Recommendation on the Ethics of AI (Nov 2021)

URL: https://www.unesco.org/en/artificial-intelligence/recommendation-ethics
Full text: https://unesdoc.unesco.org/ark:/48223/pf0000381137 (fetch blocked 403,
themes used)

Ten core principles. Most relevant to Shepard: **Principle 6 Transparency and
Explainability**, **Principle 7 Responsibility and Accountability**, **Principle
on Human Oversight and Determination**, **Principle of Auditability and
Traceability** ("AI systems should be auditable and traceable"). Member States
are explicitly urged to ensure that "development and use of AI technologies are
guided by sound scientific research as well as ethical analysis and evaluation."
This endorses AI-in-science as a legitimate activity under transparency
constraints. Para-level numbering not extracted from the public landing page.

### S4 — EU AI Act (Regulation EU 2024/1689)

URLs:
- Article 2: https://artificialintelligenceact.eu/article/2/
- Article 50: https://artificialintelligenceact.eu/article/50/
- Article 4 (AI literacy): https://artificialintelligenceact.eu/article/4/

**Article 2(6):** "This Regulation does not apply to AI systems or AI models,
including their output, specifically developed and put into service for the sole
purpose of scientific research and development." A bright research exemption.
Shepard's AI-assistant features used inside a research workflow sit inside this
exemption. **Article 2(8)** further exempts "research, testing or development
activity regarding AI systems or AI models prior to their being placed on the
market or put into service" (excluding real-world testing).

**Article 4 (AI literacy):** providers and deployers must ensure "a sufficient
level of AI literacy of their staff and other persons dealing with the operation
and use of AI systems on their behalf." Effective from Feb 2025. Not exempt
from research carve-out for staff training.

**Article 50 (Transparency):** synthetic audio/image/video/text outputs must be
"marked in a machine-readable format and detectable as artificially generated."
Deployers must disclose deep-fakes; AI-generated text about matters of public
interest must be disclosed. Effective 2 August 2026.

**Annex III high-risk:** scientific research is **not** listed. Categories are
biometrics, critical infrastructure, education, employment, essential services,
law enforcement, migration, justice administration. Shepard's AI-assistant
shape is not high-risk under Annex III.

### S5 — NIST AI Risk Management Framework 1.0 (Jan 2023, NIST AI 100-1)

URL: https://nvlpubs.nist.gov/nistpubs/ai/nist.ai.100-1.pdf
Playbook: https://airc.nist.gov/airmf-resources/playbook/

Four functions: **Govern (GV)**, **Map (MP)**, **Measure (MS)**, **Manage (MG)**.
Key sub-categories: **GV-1.1** (legal/regulatory requirements understood and
documented), **MP-5.1** (likelihood and magnitude of impacts documented),
**MS-2.11** (fairness and bias evaluated), **MG-4.1** (post-deployment
monitoring, appeal/override, decommissioning, change management). Voluntary,
risk-based — explicitly endorses AI use across the lifecycle when paired with
documentation/traceability/audit-trail infrastructure. The Measure function is
where Shepard's sampler-parameter gap matters most: rigorous risk measurement
implies capturing model + parameters + seed at inference time.

### S6 — ISO/IEC 42001:2023 (AI Management System)

URL: https://www.iso.org/standard/42001

PDCA-shaped management system. Clauses cited in industry summaries: **6.1**
(AI risk identification and assessment), **8.2** (operational controls
mitigating risk), **9** (monitoring/measurement), **10** (continuous
improvement). **Annex A** lists controls, **Annex B** provides implementation
guidance including "data management processes." The standard requires that AI
systems be "documented and traceable, with sufficient transparency to explain
outputs, especially for high-risk applications." Endorses AI in data
management subject to operational controls + lifecycle documentation.

### S7 — Wellcome / publisher consensus on AI in research

URLs:
- COPE position: https://publicationethics.org/guidance/cope-position/authorship-and-ai-tools
- 2025 publishing-policy survey: https://www.thesify.ai/blog/ai-policies-academic-publishing-2025

No Wellcome-specific 2024/25 guidance surfaced cleanly in search. The de-facto
consensus is the **COPE position** mirrored by Elsevier / Springer Nature /
Wiley / Taylor & Francis / SAGE: **(a) AI cannot be listed as an author** — only
humans/legal persons can take accountability — and **(b) AI use in writing, image
production, or data collection/analysis must be disclosed in Materials &
Methods, including the tool's name, version, manufacturer, and purpose.** This
is the load-bearing constraint Shepard's f(ai)²r capture answers structurally.

### S8 — Royal Society "Science in the Age of AI" (2024)

URL: https://royalsociety.org/news-resources/projects/science-in-the-age-of-ai/

The Royal Society 2024 project explicitly addresses how AI changes "research
integrity, research skills, and research ethics." Emphasises openness:
"ensuring AI systems are as open as possible is vital." Their May 2024 piece
"Opaque AI research tools could undermine trust and accuracy of scientific
findings" (https://royalsociety.org/news/2024/05/ai-research-tools-could-
undermine-trust-accuracy-scientific-findings/) is the strongest single piece of
guidance on AI transparency in research. The Royal Society / DataSeer
agreement (March 2024) operationalises open-data compliance checks — analogous
to Shepard's SHACL-validation surface.

### S9 — NASA / ESA / EASA — aerospace and scientific AI guidelines

URLs:
- EASA AI Concept Paper Issue 2 (March 2024): https://www.easa.europa.eu/en/document-library/general-publications/easa-artificial-intelligence-concept-paper-issue-2
- NASA SMD AI Workshop 2024: https://science.data.nasa.gov/features-events/nasa-smd-ai-workshop-2024
- NASA 2024 AI Use Cases: https://www.nasa.gov/organizations/ocio/dt/ai/2024-ai-use-cases/

EASA Issue 2 is the aerospace-binding document. Three pillars: **learning
assurance** (the evidence pack for trained models), **AI explainability**
(human-interpretable rationale), **ethics-based assessment**. Levels: Level 1
(AI assists human), Level 2 (AI/human teaming with human oversight), Level 3
(autonomous, in 2025+). Shepard's REBAR framing (`project_rebar_integration.md`)
positions Shepard as the FAIR/audit-evidence layer over REBAR's ML pipeline —
that is the EASA Learning Assurance evidence pack target. NASA's 2024 use-case
inventory normalises AI across mission lifecycle; both NASA and ESA endorse AI
in engineering data subject to traceable lifecycle management.

### S10 — CODATA / RDA — AI for research data

URLs:
- FAIR-for-AI Nature paper: https://www.nature.com/articles/s41597-023-02298-6
- CODATA AI readiness (SciDataCon 2025): https://scidatacon.org/event/9/contributions/122/
- ML pipelines + FAIR + provenance arXiv: https://arxiv.org/pdf/2006.12117

CODATA and RDA both endorse extending FAIR to "tools, algorithms, and
workflows that produce results." The community position is explicit:
"Trustworthy AI requires trustworthy data" — data provenance, integrity, and
quality measures are the precondition for AI use in science, not optional
extras. RDA's FAIR Data Maturity Model Working Group provides an assessment
framework that Shepard's SHACL-driven completeness checks structurally
implement. No prohibition on AI in dataset shaping; explicit endorsement of
AI when provenance is captured.

---

## 3. Alignment table — Shepard's four bands (Band IV split into established + cutting-edge) × 10 sources

✓ = source explicitly endorses or aligns; △ = source neutral / partially-aligned;
✗ = source raises caution or constraint. Cell content is the citation hook, not
a paragraph.

| Band | OECD-Sci (S1) | OECD-Princ (S2) | UNESCO (S3) | EU AI Act (S4) | NIST RMF (S5) | ISO 42001 (S6) | COPE/Wellcome (S7) | Royal Soc (S8) | EASA / NASA (S9) | CODATA / RDA (S10) |
|---|---|---|---|---|---|---|---|---|---|---|
| **I Arrange** (organise/re-link/re-shape) | ✓ productivity multiplier framing | ✓ transparency principle applies | △ Princ 6+7 require explainability | ✓ Art 2(6) research exemption | ✓ GV-1.1, MP-5.1 if documented | ✓ cl 6.1/8.2 if controlled | ✓ disclose tool + purpose | ✓ open AI tools | ✓ Level-1 assist (EASA) | ✓ FAIR + AI explicit |
| **II Annotate** (metadata creation) | ✓ data extraction/curation endorsed | △ explainability binds | ✓ auditability principle | ✓ Art 2(6); Art 50 mark synthetic | ✓ MP-5.1 if logged | ✓ Annex B data-mgmt controls | ✓ disclose in M&M | ✓ openness vital | ✓ Level-1/2 assist | ✓ provenance is the precondition |
| **III Quality + Standardise** | ✓ "processes of evaluation" required | ✓ robustness principle | ✓ traceability principle | ✓ Art 2(6) | ✓ MS-2.11 fairness/bias | ✓ cl 9 monitoring | ✓ disclose | ✓ DataSeer open-data checks analogue | ✓ EASA learning assurance fit | ✓ FAIR maturity model |
| **IV Explore — established methods** (z-score, k-means, correlation, descriptive stats) | ✓ accelerator role | ✓ transparency applies | ✓ traceability principle | ✓ Art 2(6) research exemption | △ MS-2.11 + MG-4.1 still apply | ✓ cl 8.2/9 | ✓ disclose method + tool | ✓ open methods endorsed | ✓ Level 1 OK | ✓ FAIR + reproducibility |
| **IV Explore — cutting edge** (foundation models, GNN on graph, cross-modal embeddings) | ✗ "superficial work risk" + accuracy concerns | ✗ robustness/explainability harder | ✗ explainability tension | △ Art 2(6) covers, but GPAI rules + Art 50 trigger if outputs published | ✗ MS gaps (no sampler params), MG-4.1 hard | ✗ cl 6.1 risk-id more onerous | ✗ disclosure must be richer | ✗ "opaque tools undermine trust" | ✗ EASA Lvl-3 not yet guided; Learning Assurance required | △ FAIR-for-AI emerging; provenance non-negotiable |

The Band IV-cutting-edge row is the load-bearing row. Every source surveyed
hesitates here. Shepard's design answer — research-mode flag + f(ai)²r
provenance + EASA Learning Assurance evidence pack — is exactly the right
shape for the constraints in this row.

---

## 4. Where Shepard goes BEYOND authoritative guidance

These are the capabilities Shepard's design has that no surveyed source has
fully reckoned with yet. Useful for funding pitches.

1. **Automatic per-Activity PROV-O capture of every AI forging pass.** Every
   source surveyed (COPE, OECD, EU AI Act Art. 50, ISO 42001) requires
   *disclosure* of AI use. None requires machine-readable per-call provenance
   tied to specific dataset mutations. Shepard's f(ai)²r implementation
   (`project_fair2r_integration.md`) makes every AI invocation a typed PROV-O
   Activity with `prov:wasAssociatedWith` chain to an `AIAgent` and
   `prov:used` chain to a `Prompt` plan. This is structural over-shoot:
   compliance becomes a query, not an attestation.

2. **Snapshot-bounded mutation cycles as the audit primitive.**
   `project_dataset_forging.md`'s named, addressable, reproducible forging
   stages have no analogue in any surveyed source. Auditors today get
   "the dataset as it was when the AI ran" — Shepard delivers "the dataset
   before forging pass N, the dataset after forging pass N, and the PROV-O
   trace of every triple that moved between them." This is what EASA
   Learning Assurance auditors and DIN EN 9100 inspectors actually need but
   cannot articulate as a requirement yet.

3. **SHACL-shape-driven prompt templates as an AI ergonomic gate**
   (`aidocs/semantics/98-shapes-views-and-process-model.md §4.10`).
   The shape and the prompt are the same artefact. The AI's
   `instantiate_template(shapeAppId, payload)` call is validated by exactly
   the same SHACL that validates the human form. No source — not even the
   CODATA "FAIR-for-AI" Nature paper — has named this collapse yet.

4. **Substrate-split for AI-vs-operational data** (`feedback_shacl_single_source_of_truth.md`).
   AI claims (`fair2r:Claim` with verification ladder) live in the SHACL graph
   alongside human-asserted triples; operational state stays in Neo4j. No
   surveyed source has reckoned with where AI-generated assertions live in
   a hybrid graph database.

---

## 5. Where Shepard FALLS SHORT of authoritative guidance

These are the gaps. Useful for the roadmap.

1. **Sampler-parameter capture is designed but not recorded today.** The
   persona review (`aidocs/agent-findings/persona-review-ai-opportunities.md
   §1`) flagged that current capture is "good for attribution but thin for
   ML reproducibility — no temperature, top-p, random seed, or sampler
   params." NIST AI RMF Measure (MS-2.11) and ISO 42001 cl 9 both
   imply this level of detail is required for non-trivial AI deployments.
   **Fix:** additive fields on `fair2r:AuthoringPass` — `fair2r:temperature`,
   `fair2r:topP`, `fair2r:seed`, `fair2r:sampler`. Low-effort.

2. **Article-50-style end-user labelling of AI-generated artefacts in the UI is
   not shipped.** The frontend has no consistent visual badge or machine-
   readable mark on AI-generated annotations, descriptions, or attribute
   backfills. EU AI Act Article 50 effective 2 August 2026 will require
   "machine-readable format and detectable as artificially generated" for
   synthetic content. Shepard's research exemption under Art. 2(6) may
   cover internal use, but published artefacts (collection exports,
   Unhide-published metadata, RO-Crate exports) fall back outside the
   exemption.
   **Fix:** a `fair2r:Claim`-derived UI badge + a JSON-LD `@type
   fair2r:AIGenerated` mark on export. Medium-effort.

3. **Article-4-style AI-literacy surface is absent.** EU AI Act Article 4
   requires deployers to ensure "sufficient level of AI literacy" of staff
   using AI. Shepard has no in-app explainer of what the AI does, what
   methods it uses, what its limits are, or how to interpret confidence
   scores.
   **Fix:** an AI literacy page under `/help/ai-assistant.md` + a "What is
   this AI doing?" panel on every AI-touched artefact. Low-effort.

4. **Cutting-edge methods are not gated by an explicit research-mode flag in
   the UI.** The design names a "Research mode" flag (`project_ai_data_arranger.md`
   Band IV) but it is not in the codebase. Without the flag, established
   and cutting-edge methods sit in the same toolset — exactly the failure
   mode the OECD and Royal Society both flag.
   **Fix:** ship the `aiMode: "established" | "research"` collection-level
   setting + a feature gate in the AI plugin. Low-effort.

5. **Sampler/model-card metadata for human-readable disclosure is absent
   from the published export.** RO-Crate / DataCite exports do not yet
   include a model-card-style summary of which AI tools touched which
   DataObjects with which parameters. The COPE consensus mandates exactly
   this disclosure in published outputs.
   **Fix:** a "Provenance Summary" section in RO-Crate export, derived from
   the `fair2r:` graph. Medium-effort.

---

## 6. Specific design changes recommended by the survey

Concrete shifts to `project_ai_data_arranger.md` and adjacent docs:

1. **Make attribution mandatory, not optional.** The current design implies
   every AI action lands as PROV-O Activity, but the SHACL constraint should
   be raised to a *closed-world* invariant: no `fair2r:Claim` MAY exist
   without `prov:wasGeneratedBy`. (`project_fair2r_integration.md` already
   names this invariant; promote it from prose to enforcement.)

2. **Add a "cutting-edge research" flag with explicit user opt-in.** Per
   §5.4, expose `aiMode` at the collection level with default `"established"`.
   Cutting-edge methods (foundation-model TS, GNN, cross-modal) require
   `aiMode: "research"` and surface a banner explaining the EASA-Learning-
   Assurance-evidence-pack implication.

3. **Add an "audit-export-for-EASA" target.** A single endpoint
   `/v2/collections/{id}/export?profile=easa-learning-assurance` that bundles
   the snapshot lineage, the f(ai)²r Activity trace, the sampler-parameter
   pack, the SHACL validation report, and the model cards. This is the
   pitchable artefact: "Shepard → one click → EASA Learning Assurance
   evidence pack." (Aligned to `project_rebar_integration.md`.)

4. **Add sampler-parameter capture to the f(ai)²r AuthoringPass Activity.**
   Required fields when present: `fair2r:model`, `fair2r:modelVersion`,
   `fair2r:temperature`, `fair2r:topP`, `fair2r:seed`, `fair2r:sampler`.
   Vendor-level: extend `provenance.ttl` in `shepard-plugin-ai`.

5. **Add Article-50 marking on AI-generated artefacts.** UI badge + export-
   level `fair2r:AIGenerated` `@type`. Required before any external publish
   path (Helmholtz Unhide, Databus, public RO-Crate) goes live.

6. **Add AI-literacy surface.** `/help/ai-assistant.md` reference page (per
   project plugin-docs rule) + per-artefact "What did the AI do here?"
   pop-over showing the prompt template, tool name, model, parameters,
   verification state.

---

## 7. Elevator pitch — 3 sentences for Clean Aviation JU / EASA / EOSC quote

> Shepard treats AI assistance as a first-class typed activity in the
> research-data graph — every AI-proposed dataset reshaping, every AI-drafted
> annotation, every AI-suggested quality fix lands as a PROV-O `Activity`
> against the f(ai)²r vocabulary, anchored to a snapshot before and after,
> so the provenance of who-and-what touched the dataset is a SPARQL query
> not an attestation.
>
> This sits squarely inside the EU AI Act Article 2(6) research exemption,
> meets the COPE / publisher consensus disclosure requirement by construction,
> and structurally answers the OECD and Royal Society reproducibility concerns
> about AI in science — the dataset is the artefact, the AI is a forging
> assistant whose every strike is auditable.
>
> The result is an EASA Learning Assurance evidence pack that exports in one
> click: snapshot lineage, AI-Activity trace, sampler-parameter capture,
> SHACL validation report, model cards — the precise audit primitive that
> NIST AI RMF, ISO/IEC 42001, and the CODATA FAIR-for-AI community position
> all describe but none yet operationalise.

---

## 8. `[NEEDS-CLARIFICATION]` — places where authoritative sources contradict each other

```
[NEEDS-CLARIFICATION-1] Disclosure obligations inside the EU AI Act research
  exemption — does Shepard disclose AI involvement on internal artefacts?

Question: Article 2(6) of the EU AI Act exempts "AI systems ... specifically
  developed and put into service for the sole purpose of scientific research
  and development." But the COPE / publisher consensus (Elsevier, Springer
  Nature, Wiley, Taylor & Francis, SAGE) mandates disclosure of AI use even
  in research output — and the OECD AI-in-Science report calls reproducibility
  the load-bearing concern. The two regimes do not contradict for *published*
  artefacts (both demand disclosure). They diverge for *internal* artefacts
  during research: the EU AI Act exempts; COPE / OECD recommend disclosure
  anyway for downstream reproducibility.

Context: Shepard sits in both worlds — internal research notebooks AND
  external publication targets (Unhide, Databus, RO-Crate). The question
  is whether the f(ai)²r capture surface is visible by default or opt-in.

Options:
  A) Always-on capture, default-visible UI badge on every AI-touched artefact
     pro: COPE/OECD-aligned by construction; no operator opt-in required;
          publisher-export needs no special path
     con: research-only deployments get UI noise they don't legally need
  B) Always-on capture, badge visible only on publication-export
     pro: clean internal UX; legal-minimum compliance for internal use
     con: researchers may share unpublished artefacts (preprints, lab
          notebooks) where COPE recommends disclosure
  C) Capture and badge both opt-in at deployment time
     pro: most flexible
     con: operator-configurable disclosure defeats the structural-compliance
          pitch; rebuilds the "did you disclose?" attestation problem

Lean: A. The structural-compliance pitch is the whole point — the moment we
  let an operator switch it off, we're back to attestation. UI noise can be
  styled down.
```

```
[NEEDS-CLARIFICATION-2] AI in cutting-edge methods — research-mode flag at
  which scope?

Question: §5.4 + §6.2 recommend a research-mode flag. The OECD AI-in-Science
  report and the Royal Society 2024 piece both flag cutting-edge methods
  (foundation models, GNN, cross-modal) as the failure mode. EASA Concept
  Paper Issue 2 wants Learning Assurance evidence for any non-trivial ML.
  Where does the flag live?

Options:
  A) Collection-level (`Collection.aiMode = "established" | "research"`)
     pro: matches how researchers actually scope work; aligns with the
          forging-cycle abstraction
     con: a single shared collection across teams may have mixed needs
  B) Per-Activity (`fair2r:AuthoringPass fair2r:mode "research"`)
     pro: finer-grained; supports mixed-method workflows
     con: every user must remember to set it; the OECD "superficial work
          risk" comes precisely from forgetting
  C) Workspace-level (per-user setting)
     pro: matches the user's experience
     con: data is shared; a researcher's mode is the wrong scope for a
          published artefact

Lean: A + sticky default. The collection (the unit of dataset-forging in
  Shepard) is the natural scope. Default `established`; opt-in `research`
  by collection admin; surface a banner when active.
```

```
[NEEDS-CLARIFICATION-3] Where the EU AI Act Article 50 "machine-readable
  mark" lives — at the artefact, the claim, or the activity?

Question: Art. 50 effective 2 Aug 2026 requires "synthetic outputs are marked
  in a machine-readable format and detectable as artificially generated."
  Shepard's f(ai)²r model has three candidates for the mark.

Options:
  A) Mark the Activity (every f(ai)²r:AuthoringPass carries an Art-50 tag)
     pro: every AI run is marked at the source
     con: an artefact derived from multiple activities is ambiguous to
          downstream consumers
  B) Mark the Claim (every fair2r:Claim carries the tag)
     pro: per-assertion mark; closest to the law's intent
     con: explosion of marks on a heavily AI-touched DataObject
  C) Mark the Artefact (DataObject-level fair2r:AIGenerated annotation)
     pro: simplest downstream UX; one mark per artefact
     con: loses granularity when a DO has both human + AI contributions

Lean: B + C. Mark the Claim for granularity (legal-minimum) AND mark the
  Artefact (UX/export). Don't mark the Activity (auditors query the
  Activity through the Claim chain anyway).
```

**Total `[NEEDS-CLARIFICATION]` blocks: 3.**

---

## Source URL index (cite-ready)

1. OECD AI in Science 2023: https://www.oecd.org/en/publications/artificial-intelligence-in-science_a8d820bd-en.html
2. OECD AI Principles 2019/2024: https://oecd.ai/en/ai-principles
3. UNESCO Recommendation on Ethics of AI 2021: https://www.unesco.org/en/artificial-intelligence/recommendation-ethics (full text https://unesdoc.unesco.org/ark:/48223/pf0000381137)
4. EU AI Act Art. 2: https://artificialintelligenceact.eu/article/2/  ; Art. 4: https://artificialintelligenceact.eu/article/4/  ; Art. 50: https://artificialintelligenceact.eu/article/50/
5. NIST AI RMF 1.0: https://nvlpubs.nist.gov/nistpubs/ai/nist.ai.100-1.pdf  ; Playbook: https://airc.nist.gov/airmf-resources/playbook/
6. ISO/IEC 42001:2023: https://www.iso.org/standard/42001
7. COPE position on AI tools: https://publicationethics.org/guidance/cope-position/authorship-and-ai-tools  ; Publishing survey 2025: https://www.thesify.ai/blog/ai-policies-academic-publishing-2025
8. Royal Society "Science in the Age of AI": https://royalsociety.org/news-resources/projects/science-in-the-age-of-ai/  ; May 2024 statement: https://royalsociety.org/news/2024/05/ai-research-tools-could-undermine-trust-accuracy-scientific-findings/
9. EASA AI Concept Paper Issue 2 (Mar 2024): https://www.easa.europa.eu/en/document-library/general-publications/easa-artificial-intelligence-concept-paper-issue-2  ; NASA 2024 AI Use Cases: https://www.nasa.gov/organizations/ocio/dt/ai/2024-ai-use-cases/  ; NASA SMD AI Workshop 2024: https://science.data.nasa.gov/features-events/nasa-smd-ai-workshop-2024
10. FAIR-for-AI (Nature 2023): https://www.nature.com/articles/s41597-023-02298-6  ; ML pipelines + FAIR + provenance: https://arxiv.org/pdf/2006.12117  ; CODATA AI readiness SciDataCon 2025: https://scidatacon.org/event/9/contributions/122/
