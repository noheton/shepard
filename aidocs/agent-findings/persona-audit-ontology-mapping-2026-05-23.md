---
title: Persona audit — AI-assisted ontology mapping survey (4 lenses, round 1)
stage: audited-by-personas
last-stage-change: 2026-05-23
audience: contributors, ontology engineers, reviewers of ONT-AI-MAP1
audited-doc: aidocs/agent-findings/ai-ontology-mapping-survey-2026-05-23.md
audited-commit: c14957ba (survey body) + 5b7a7166 (§11 OQ-6 closure)
backlog-row: ONT-AI-MAP1
---

# Persona audit — AI-assisted ontology mapping survey (round 1)

**Scope.** Four personas review `aidocs/agent-findings/ai-ontology-mapping-survey-2026-05-23.md`. Per
the scope-table in `feedback_persona_audit_triggers.md`, "new ontology / annotation key" routes:
**primary** Data & Process Ontologist; **secondary** RDM + Strategy Aligner; plus
Manufacturing & Quality Engineer added because the §5 acceptance ladder makes EN 9100
auditability claims.

**Audit form.** Each persona section: lens statement → 3–5 element-anchored findings →
verdict (ACCEPT / ACCEPT-WITH-CHANGES / REJECT) → change-requests with rationale +
"what would change my mind" → external citations (≥3 per persona, lens-cited per finding).

**Methodology note.** Lightweight imagined-lens consult per `feedback_persona_audit_triggers.md`
Addendum 2026-05-23 (no `Agent` tool available in this harness). External research via
WebSearch / WebFetch / paper-search probes batched up-front. The advisor (stronger reviewer)
was consulted before substantive writing to surface discriminating constraints; the four
likely change-requests it predicted (SSSOM wording, DeepOnto activity, Matcha-as-sidecar,
NFDI4Ing outreach) all land below, plus a fifth the advisor didn't flag: the **OAEI 2025
MSE-track regression**.

---

## §1 Data & Process Ontologist — primary lens

**Lens.** Domain-modelling fit. A mapping is correct only when both subject and object
ontology classes denote the same world entity; the SHACL substrate must let an
auditor SPARQL the provenance back to the human + AI + prompt that produced it.
Bad mappings here silently corrupt downstream cross-domain queries.

### Findings

**[F1.1] §6 MFFD CHAMEO ↔ MatOnto example mapping #5 already demonstrates the failure
mode the survey claims it prevents — and that's the strongest evidence the design is
right.** Cite: survey §6.1 row #5 (`chameo:Specimen` ↔ `matonto:Sample`, conf 0.92,
**Reject**). The LLM was 92% confident and structurally wrong (Specimen = physical
artefact; Sample = analytical subset). Without §5's acceptance ladder this would have
landed in `default-SPARQL` results and silently broken any provenance query that joined
on `m4i:hasInput`. Lens: Data Ontologist. **Status: strengthens the survey's case.**

**[F1.2] CHAMEO real-world deployment evidence is THIN for aerospace composites — the
MFFD demonstrator (§6) is a *first* application, not a *recognised* one.** External
evidence: CHAMEO was developed by the EU NanoMECommons project for nanomechanical
characterisation + battery testing [4]; documented applications cite nanoindentation
+ FIB-DIC on coatings + partial-discharge battery testing. Aerospace composite layup
(LMPAEK / CF-PEEK) is NOT in CHAMEO's published case-study set [5]. The survey
§6 presents the MFFD pair as if the ontology already covers it; in reality CHAMEO will
need extension for `prepreg_lot`, `consolidation_force`, `tcp_temperature` as
characterisation parameters (or these belong to a different ontology — possibly EMMO
core + an MFFD-specific application ontology). Cite: survey §6, `examples/mffd-showcase/seed.py`.
Lens: Data Ontologist. **Status: change-request — survey should say "first known
aerospace-composite CHAMEO application" + name the extension work as a separate task.**

**[F1.3] §4.5 SHACL-substrate-as-storage decision is correct AND non-trivial — the
n10s projection into Neo4j creates a `:Resource` round-trip path that needs verification
for mapping individuals.** Cite: survey §4.5 ("the SHACL n10s layer already projects
into Neo4j via `:Resource` nodes"). Counter-evidence: `feedback_shacl_single_source_of_truth.md`
caveats that SHACL is the domain-data SoT, but mappings are *meta-data about* domain
data — they conceptually sit between ontologies. The n10s projection has known
quirks with `owl:imports` chains and `skos:exactMatch` resolution [3], and the
survey doesn't say whether a `sssom:Mapping` individual is treated as a domain-data
shape or a meta-relation. Lens: Data Ontologist. **Status: change-request — add §4.5
sub-section "how the n10s projection handles `sssom:Mapping` individuals; specifically
whether `skos:exactMatch` triples get propagated into Neo4j as `:Resource`-to-`:Resource`
relationships or stay SHACL-side only".**

**[F1.4] §8.6 semapv vocabulary mapping is correct but incomplete — the
`semapv:CompositeMatching` justification (multiple methods agreed) is exactly the
LLM-as-oracle output, but the survey doesn't explicitly say which Shepard mappings
take which value.** Cite: survey §8.6. Suggested addition: a one-line per-mode mapping
("oracle-pattern output → `semapv:CompositeMatching`; LogMap raw → `semapv:LogicalReasoning`;
OntoAligner raw → `semapv:LexicalMatching`; human-curated → `semapv:ManualMappingCuration`").
Lens: Data Ontologist. **Status: change-request — small clarification, ~5 lines.**

**[F1.5] §1.2 mapping-pair table understates the
`f(ai)²r ↔ PROV-O` row's structural importance.** Cite: survey §1.2 row 6. The f(ai)²r
vocabulary is what makes Shepard's mapping graph *self-describing about its own AI
authorship*. Without it, the SHACL substrate cannot distinguish "AI proposed,
human reviewed" from "human authored from scratch" — and that distinction is the
entire EN AI Act Article 50 disclosure case (see RDM §2). Lens: Data Ontologist.
**Status: nit — bump pain rating from "High" to "Foundational"; the other five rows
*depend on* this one being in place to be EU-AI-Act-compliant.**

### Verdict: **ACCEPT-WITH-CHANGES** (4 of 5 changes are clarifications, not pivots)

The survey's recommendation is structurally sound. The Data Ontologist's concerns
are about: (a) overclaim on CHAMEO's existing aerospace coverage (F1.2), (b) silent
gap in the n10s projection handling for `sssom:Mapping` individuals (F1.3), and
(c) two small completeness gaps (F1.4, F1.5). None of these block adoption.

**What would change my mind.** If F1.3 turns up that n10s materially loses
`skos:exactMatch` triples on round-trip, the storage decision in §4.5 needs a
plan-B (separate SHACL graph for mappings, Cypher-bridged via a custom relationship
type, OR a dedicated `:Mapping` Neo4j label that mirrors the SHACL individual).
If F1.2 turns up that CHAMEO is fundamentally the wrong upper-ontology for
manufacturing-process characterisation (vs IOF + m4i), the MFFD demonstrator
should pivot to a different demonstrator pair.

### Citations (Data Ontologist)

[1] Del Nostro, Goldbeck, Toti (2022). *CHAMEO: An ontology for the harmonisation
of materials characterisation methodologies.* Applied Ontology 17(3).
https://dl.acm.org/doi/abs/10.3233/AO-220271
[2] CHAMEO documentation — *Real-world applications.*
https://emmo-repo.github.io/domain-characterisation-methodology/pages/introduction.html
[3] Neosemantics (n10s) documentation — `skos:exactMatch` and `owl:imports` handling
notes. https://neo4j.com/labs/neosemantics/
[4] NanoMECommons EU project — CHAMEO origin context.
https://materialsmodelling.com/chameo-release-v1-0-0/
[5] CHAMEO case studies — nanoindentation, FIB-DIC, partial discharge battery testing
(MaterialsWeek 2024 abstract S05_P01). https://materials-week.org/wp-content/uploads/MW24_Abstracts_FINAL/S05_P01_GerhardGoldbeck.pdf

---

## §2 Research Data Manager — secondary lens (FAIR)

**Lens.** Every mapping is itself a FAIR object; the *mapping graph* must satisfy
F-A-I-R for downstream re-use, embargo, attribution, license-portability. SSSOM
adoption is the right call — its claim of W3C-class status is the question.

### Findings

**[F2.1] §1.3, §5.3, §8.1 call SSSOM "W3C-style community standard". This
overclaims. SSSOM is a mapping-commons / OBO-Foundry community standard, NOT a
W3C Recommendation, NOT a W3C Working Group output.** External evidence: the SSSOM
specification site [1] describes itself as "a standard developed under the
auspices of the [Mapping Commons] community" with OBO-Foundry-aligned governance;
the spec is hosted at `mapping-commons.github.io/sssom/`, not `w3.org/TR/`. The
NCBI / Database paper [2] is peer-reviewed but cites no W3C process. The "1.0" release
of SSSOM has happened (current is 1.1 alpha [3]); "1.0-compliant" means tooling
compatibility, not W3C standardisation. Lens: RDM. **Status: change-request —
in §1.3, §5.3, §8.1, replace "W3C-style community standard" with
"OBO Foundry-endorsed community standard maintained by mapping-commons". The FAIR
interoperability case stands either way; the wording is just inaccurate.**

**[F2.2] §5.3 FAIR scoring is fair but doesn't quantify against the RDA FAIR Maturity
Indicators or FAIRsharing rubric.** Cite: survey §5.3 Findable/Accessible/Interoperable/
Reusable. The four-dimension recital is correct, but a public-funded research data
service ought to give a numeric score per RDA Maturity Indicator [4]. RDM persona
prefers: F2 (no own PID resolution unless `shepmap:` is registered as a
w3id.org redirect — Shepard CAN do this); A1 (HTTP GET works); I1 (RDF + SHACL ✓);
I2 (SSSOM ✓); R1.1 (license per §8.4 ✓). Lens: RDM. **Status: change-request — add a
§5.3 sub-paragraph "FAIR maturity per RDA indicator" with one line per indicator
that the design hits or doesn't.**

**[F2.3] §8.4 license handling is correct but the SHACL graph can hold mixed-license
mappings in one named graph — that's a known FAIR licensing failure mode (mixing CC0
with CC-BY-SA contaminates the CC0 default).** Cite: survey §8.4 ("A mixed SHACL graph
can hold mappings of different licenses side-by-side"). The export pipeline filters by
license, but the *graph itself* needs license-namespace separation OR per-mapping
license on every triple. The mapping-commons community has hit this; the SSSOM spec
recommends one license per MappingSet [1, §3] specifically to avoid the contamination.
Lens: RDM. **Status: change-request — adopt the SSSOM `MappingSet`-level license
discipline; the SHACL substrate should hold one named graph per (source-ontology,
target-ontology, license) triple. Or accept the contamination risk explicitly and
document why.**

**[F2.4] §11.1 acceptance criterion #6 ("default SPARQL filter is acceptance ≥
Reviewed") is the right A-default but needs a UI surface that an *evaluator* can
discover.** Cite: survey §11.1 row 6. The RDM persona consulting downstream
publication (Unhide / Databus) needs to know: when I export a mapping bundle, what
acceptance threshold did I use? A `mapping_set_export_acceptance_threshold` field
in the SSSOM header would make the export self-documenting. Lens: RDM.
**Status: change-request — add §8 entry: SSSOM export header carries an
`acceptance_threshold` extension slot stating which rung was the inclusion floor.**

**[F2.5] §10.3 IP risk paragraph is excellent — the SAIA / GWDG-routing mitigation
is FAIR-compliant in the most cautious reading; the only gap is that the
mapping rationale text (LLM natural-language explanation) is stored alongside the
mapping with no provenance about *which* LLM produced it.** Cite: survey §10.3 +
§5.2 example Turtle (the `f-ai-r:proposalPrompt` triple exists but no
`f-ai-r:proposalModel`). External: EU AI Act Article 50 [6] requires disclosure of
"who and what AI was used" for AI-generated content; the mapping rationale IS
AI-generated content. Lens: RDM. **Status: change-request — add `f-ai-r:proposalModel`
triple to §5.2 example (model + provider + version + datacentre region) so the AI
Act disclosure chain is complete.**

### Verdict: **ACCEPT-WITH-CHANGES** (one factual fix [F2.1] + four polish)

The FAIR posture is right. The W3C overclaim is the only finding that has a
public-perception cost (a reviewer who knows SSSOM's status will silently downgrade
the survey's credibility). The other four are improvements.

**What would change my mind.** If the mapping-commons community moves SSSOM to a
W3C Submission or Community Group track, F2.1 dissolves. If
RDA Maturity Indicators are published in a Shepard-friendly machine-readable form,
F2.2 can be auto-checked instead of documented.

### Citations (RDM)

[1] Matentzoglu et al. (2022). *A Simple Standard for Sharing Ontological Mappings
(SSSOM).* Database (Oxford), baac035. https://doi.org/10.1093/database/baac035
[2] SSSOM specification (mapping-commons). https://mapping-commons.github.io/sssom/
[3] SSSOM 1.1.0 alpha release notes (April 2026).
https://github.com/mapping-commons/sssom/actions/runs/24081471025
[4] RDA FAIR Data Maturity Model Working Group — *FAIR Data Maturity Indicators.*
https://www.rd-alliance.org/groups/fair-data-maturity-model-wg
[5] Wilkinson et al. (2016). *The FAIR Guiding Principles for scientific data
management and stewardship.* Scientific Data 3, 160018.
https://doi.org/10.1038/sdata.2016.18
[6] European Union (2024). *Regulation (EU) 2024/1689 (Artificial Intelligence Act)
Article 50 — Transparency obligations for providers and deployers of certain AI
systems.* https://artificialintelligenceact.eu/transparency-rules-article-50/

---

## §3 Strategy Aligner — secondary lens (institutional + adoption)

**Lens.** Where does this decision place Shepard institutionally? OntoAligner is
maintained by TIB Hannover, which is the NFDI4Ing host; CHAMEO comes from
EU NanoMECommons; SSSOM comes from OBO-Foundry / NIH BioPortal. The adoption
choices wire Shepard into specific funder + ecosystem networks. The §3 ADOPT
calls determine which collaborations open and which close.

### Findings

**[F3.1] DeepOnto activity claim "active (2024 paper, 2025 commits)" in §3.1 table
T2 is STALE BY 14 MONTHS as of 2026-05-23.** External evidence: latest PyPI release
of `deeponto` is **v0.9.3 dated 2025-03-10** [1]; the GitHub repo's last release
matches [2]; no newer activity on PyPI's release history page. By contrast,
OntoAligner shipped **8 releases between 2025-06 and 2026-05** including
v1.8.0 on 2026-05-22 [3]. Lens: Strategy. **Status: BLOCKING change-request — §3.1
row T2 must be revised to "v0.9.3 (Mar 2025, last release 14 months stale)";
§3.2 T2 recommendation must downgrade DeepOnto from "ADOPT as fallback / complement"
to "WATCH — adopt as fallback only if maintenance resumes by Q4 2026". The BERTMap
+ verbalisation features motivate it, but adopting a 14-months-stale dependency is
the exact anti-pattern `feedback_reuse_before_reimplement.md` discourages
("activity heuristic — last commit, contributors, releases/year").**

**[F3.2] OntoAligner ADOPT call is strongly correct — and the institutional
alignment opportunity is BIGGER than §3.2 T1 acknowledges.** External evidence:
OntoAligner won the **ESWC 2025 Best Resource Paper Award** [4]; the project is
funded under **NFDI4Ing DFG grant 442146713** [5]; the maintainer team (Babaei
Giglou, D'Souza, Karras, Auer) is the same TIB Data Science & Digital Libraries
Research Group that publishes metadata4ing v1.4.0 [6] — which Shepard already
pins (`aidocs/semantics/94`). This is a **single research group** producing
both ontologies + alignment tool Shepard depends on. Lens: Strategy. **Status:
change-request — add §9 "Synergy notes" row: "TIB Hannover (NFDI4Ing host)
maintains BOTH OntoAligner AND metadata4ing — same research group. Adoption
+ contribution to OntoAligner is the natural NFDI alignment move and should be
proposed as an NFDI4Ing collaboration item, not as a passive upstream dependency."**

**[F3.3] OAEI 2025 has NO MSE track (the §3.2 T1 claim "MSE track support out of
the box" is grounded in a 2023 track).** External evidence: OAEI 2025 results page [7]
lists 16 tracks (Anatomy, Conference, Multifarm, Complex, Biodiversity & Ecology,
Interactive, Bio-ML, Digital Humanities, Archaeology multiling, Circular Economy,
Knowledge Graph, Beyond Equivalence, Pharmacogenomics, SemTab, etc.) — no MSE,
no materials, no manufacturing track. MSE-Benchmark GitHub [8] was last updated
**November 2022** (the 2023 OAEI campaign used it; it has been dormant since).
Lens: Strategy. **Status: change-request — §3.2 T1 should be revised: "OntoAligner
supports the MSE track from OAEI 2023 (the most recent MSE evaluation). MSE was
not run in OAEI 2024 or 2025. Shepard cannot rely on OAEI to provide a 2026 SOTA
score for CHAMEO ↔ MatOnto; it must run its own evaluation against the 2022
MSE-Benchmark ground truth, OR contribute a CHAMEO/MFFD case-study to revive
the track via an OAEI 2026 proposal." This pivots the survey from "we use SOTA
benchmarks" to "we contribute to the benchmark community." Bigger ask, bigger
return.**

**[F3.4] §3.2 T3 Matcha REJECT-for-direct-adoption is correct, but the survey
doesn't address the sidecar option that `feedback_reuse_before_reimplement.md` +
`feedback_plugins_declare_sidecars.md` explicitly allow for GPL-licensed
components.** Cite: survey §3.2 T3; rule files. Counter-evidence: Matcha hit
**F=0.941 + Recall+ 0.82 on biomedical track + best in 15 of 43 OAEI 2024 tasks**
[9] — that's the SOTA hybrid (rule-based + LM-based). Process isolation via a
separate Docker container with HTTP-only IPC is the standard GPL-mitigation
pattern. Lens: Strategy. **Status: change-request — §3.2 T3 should read:
"REJECT for in-process adoption (GPL-3.0). RECONSIDER as a sidecar (HTTP-isolated
container) if OntoAligner-based CHAMEO ↔ MatOnto baseline measures F < 0.85 in
the §6.3 validation. Sidecar isolation per `feedback_plugins_declare_sidecars.md`
keeps Shepard's core permissive-licensed."**

**[F3.5] EU AI Act Article 50 enforcement date is **2 August 2026** [10] —
the survey §1.2 TPL9 row and §10.3 mitigation are correct; the §3 ADOPT
recommendation needs to land in production BEFORE that date for Shepard to be
the differentiator the f(ai)²r predicates claim. The implementation sequencing
in §11.1 doesn't show this dependency.** Cite: survey §1.2, §10.3, §11.1. Lens:
Strategy. **Status: change-request — §11.1 acceptance criteria should add an item
0: "Plugin lands in production before 2026-08-02 to capture the AI Act Article 50
'transparency obligations' market window. After August 2026, any RDM platform
without per-mapping AI-attribution provenance becomes non-compliant for
EU-funded research."**

### Verdict: **ACCEPT-WITH-CHANGES** (1 BLOCKING [F3.1] + 4 important)

OntoAligner is the right adopt. DeepOnto activity is the only finding that
blocks — adopting a 14-months-stale dependency violates the reuse-survey
discipline. The other four findings amplify the strategic upside (NFDI4Ing
collaboration, AI Act timing, sidecar option for Matcha).

**What would change my mind.** If DeepOnto ships v0.10 before ONT-AI-MAP1
implementation starts, F3.1 dissolves. If OntoAligner adds native BERTMap
+ reasoning + verbalisation modules in v2.0, the DeepOnto question becomes
moot. If a separate maintainer fork of DeepOnto with active commits exists,
that fork (with vendor commitment) is an acceptable adopt.

### Citations (Strategy)

[1] DeepOnto PyPI release page (last release v0.9.3, 2025-03-10).
https://pypi.org/project/deeponto/
[2] KRR-Oxford/DeepOnto GitHub releases.
https://github.com/KRR-Oxford/DeepOnto/releases
[3] OntoAligner PyPI release history (v1.8.0 2026-05-22; 8 releases 2025-06 → 2026-05).
https://pypi.org/project/OntoAligner/
[4] OntoAligner ESWC 2025 Best Resource Paper Award (ResearchGate profile,
Babaei Giglou). https://www.researchgate.net/profile/Hamed-Babaei-Giglou-2
[5] NFDI4Ing DFG grant 442146713 funding statement (in OntoAligner arXiv preprint
acknowledgements). https://arxiv.org/abs/2503.21902
[6] metadata4ing v1.4.0 (2025-12-08); maintained at TIB by same research group.
https://nfdi4ing.de/5-24-3/
[7] OAEI 2025 results overview page.
https://oaei.ontologymatching.org/2025/
[8] EngyNasr/MSE-Benchmark GitHub (last commit Nov 2022; OAEI 2023 was last
campaign using it). https://github.com/EngyNasr/MSE-Benchmark
[9] Faria, Pesquita et al. (2024). *Matcha — Results in OAEI 2024.* CEUR Vol-3897.
https://ceur-ws.org/Vol-3897/oaei2024_paper3.pdf
[10] EU Regulation 2024/1689 (AI Act) — Article 50 enforcement date 2 August 2026.
https://artificialintelligenceact.eu/transparency-rules-article-50/

---

## §4 Manufacturing & Quality Engineer — secondary lens (EN 9100 audit)

**Lens.** A mapping used in a regulated decision (composite-layup process spec,
NCR investigation, certification artefact) needs a chain-of-custody an auditor can
follow without privileged access. §5's acceptance ladder claims EN-9100 auditability;
this lens tests that claim.

### Findings

**[F4.1] §5.4 EN-9100 audit chain is structurally correct AND under-specified for
EASA Learning Assurance.** Cite: survey §5.4. The three-step chain (review → proposal
→ derivation) is necessary but not sufficient under the **EASA AI Concept Paper
Issue 2 "W-shaped development process"** [1] for ML applications used in
safety-critical aviation. The W-shape requires explicit (i) learning-assurance objectives
declared, (ii) data-management plan for the LLM training data, (iii) explainability
provision per output, (iv) ethics-based assessment. The survey gives (iii) via
the `f-ai-r:rejectionReason` triple but doesn't address (i)(ii)(iv). Lens:
Manufacturing-Quality. **Status: change-request — add §5.5 "EASA Learning
Assurance posture": acknowledge that Shepard mappings are *not* claimed as
EASA-certifiable AI outputs (current scope is research data management,
not airworthiness); document the gap so a future safety-critical-use plugin
knows what it would have to add.**

**[F4.2] §5.1 acceptance ladder rung 3 "Endorsed" requires either a second reviewer
or a passing SHACL validation rule — but the SHACL validation rule for ontology
mappings is undefined.** Cite: survey §5.1 row 3. What SHACL constraint validates
that `chameo:Specimen rdfs:subClassOf matonto:Material` is logically consistent
with the OBO RO mereological axioms? The survey §1.1 lists OBO RO as a shipped
ontology (PROV1a) but doesn't say which RO axioms the validation rule enforces.
This is the EN 9100 "objective evidence" requirement — without it, "Endorsed"
collapses to "two humans agreed." Lens: Manufacturing-Quality. **Status:
change-request — §5.1 must specify either (a) the minimum SHACL shape an
Endorsed mapping must validate against, OR (b) downgrade rung 3 to "second
human reviewer; SHACL validation pending #127 SHACL changeover".**

**[F4.3] §6.1 walk-through accept/reject decisions are textual — no audit can
reproduce them from the SHACL substrate alone.** Cite: survey §6.1 row #5
(reviewer reject reason text). The Turtle in §5.2 includes
`f-ai-r:rejectionReason "structural mismatch — ..."^^xsd:string` — but a string
literal is unreviewable by SPARQL. The reviewer's reasoning needs to be either
(a) a structured ontology reference (`semapv:` extension predicate for
rejection cause), or (b) a versioned text block with a `prov:wasAttributedTo`
chain to the reviewer. Lens: Manufacturing-Quality. **Status: change-request —
extend §5.2 turtle example to show: `f-ai-r:rejectionReasonCode <vocab:StructuralMismatch>`
+ `f-ai-r:rejectionReasonNarrative "..."^^xsd:string`. The code is auditable; the
narrative is reviewer-readable.**

**[F4.4] §10.3 IP-risk paragraph correctly points at SAIA/GWDG-hosted models for
DLR — but for MFFD specifically, the IP boundary is tighter. MFFD layup attributes
+ propellant batch + welding-process parameters ARE Airbus/DLR industrial IP.
The §4.3 sidecar must NEVER send these as part of the LLM prompt to a non-DLR
endpoint.** Cite: survey §4.3, §10.3. The current §4.3 design says
"calls LLM oracle (delegating to the configured AI provider per
`project_ai_plugin_config.md`)" — but the AI provider could be set to
`openai.com` per `feedback_no_redactions.md` testing setup. The mapping
context for MFFD must be filtered to public-only attribute names (CHAMEO term
labels, not their MFFD-specific values). Lens: Manufacturing-Quality.
**Status: change-request — add §10.6 "MFFD-specific IP boundary": the
ontology-mapper plugin only ever sends *ontology term labels + definitions* to
the LLM, never the *instance values* attached to MFFD DataObjects. This is
correct by current design (mapping is term-to-term, not instance-to-instance)
but the survey should say it explicitly so an MFFD project lead can read §10
and verify.**

**[F4.5] §11.1 acceptance criterion #4 ("MFFD SPARQL query in §6.3 returns
the layup steps with their materials") is the right validation but it tests
the *output*, not the *audit trail*. An EN 9100 audit needs to verify the
provenance chain, not just the result.** Cite: survey §11.1 #4. Suggested
additional criterion: "#11 — for any Reviewed mapping returned by the §6.3
SPARQL query, a second SPARQL query
`SELECT ?mapping ?reviewer ?reviewedAt ?proposedBy ?proposalPrompt`
returns a complete chain." This is the auditor's traceability check.
Lens: Manufacturing-Quality. **Status: change-request — add criterion #11
to §11.1.**

### Verdict: **ACCEPT-WITH-CHANGES** (5 changes, all about audit traceability)

The acceptance ladder is the right structural answer. The Manufacturing-Quality
lens's concerns are all about making the audit trail *queryable* (vs only
human-readable). None block adoption; all should land in the implementation.

**What would change my mind.** If MFFD pivots to a safety-critical airworthiness
use case (e.g. as-built materials data feeding into certification), F4.1 stops
being a clarification and becomes a blocking concern — the plugin would need an
explicit "non-airworthiness use" declaration. Currently MFFD scope is research /
manufacturing-quality, so this is documentation-not-design.

### Citations (Manufacturing-Quality)

[1] EASA (2024). *Artificial Intelligence Concept Paper Issue 2 — Guidance for
Level 1 & 2 machine-learning applications.* W-shaped development process
+ learning-assurance objectives. https://www.easa.europa.eu/en/document-library/general-publications/easa-artificial-intelligence-concept-paper-issue-2
[2] DIN EN 9100:2018 — *Quality management systems — Requirements for aviation,
space and defence organisations.* §8.3 design control + §8.5.6 control of
changes. (Standards-body specification, behind paywall — operator's copy at
DLR/Augsburg quality office.)
[3] EU AI Act Article 50 transparency obligations (enforcement date
2026-08-02). https://artificialintelligenceact.eu/transparency-rules-article-50/
[4] FAA (2023). *Roadmap for AI Safety Assurance.* W-shape adoption signal
beyond EU/EASA. (Referenced by EASA concept paper.)
[5] PMC9573068 (2022). *Learning Assurance Analysis for Further Certification
Process of Machine Learning Techniques: Case-Study Air Traffic Conflict
Detection Predictor.* https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9573068/

---

## §5 Cross-persona reconciliation

### Where personas agree

- **OntoAligner ADOPT is the right call.** All four personas concur: Apache-2.0,
  TIB-maintained, NFDI4Ing-funded, exactly the right granularity.
- **sssom-py ADOPT is mandatory.** All four personas concur, though Data
  Ontologist + RDM both flag the "W3C-style" wording (F2.1) as overclaim.
- **LLM-as-oracle pattern is the right shape.** Data Ontologist + RDM + Manufacturing
  Quality all separately arrive at this — for different reasons (hallucination
  bound; FAIR auditability; EN 9100 traceability).
- **§5 acceptance ladder is necessary AND insufficient.** Manufacturing-Quality
  finds gaps in the SHACL validation rule for "Endorsed" rung; RDM finds the
  export header doesn't carry the threshold; Data Ontologist finds the rejection
  reason needs to be structured (code) not just narrative.
- **EU AI Act Article 50 timeline matters.** Strategy + RDM + Manufacturing-Quality
  all separately call out 2026-08-02 as a structural deadline; Strategy makes it
  an acceptance criterion.

### Trade-off the personas surface

**DeepOnto adoption** is the one finding with cross-persona disagreement.
Strategy lens says BLOCK ([F3.1] — 14 months stale violates reuse-discipline).
Data Ontologist lens says the BERTMap reasoning + verbalisation features are
genuinely valuable and a stale dep is OK if no alternative exists. Resolution:
**STRATEGY WINS** because the reuse-discipline rule
(`feedback_reuse_before_reimplement.md`) is explicit about activity heuristics,
AND the OntoAligner v1.8.0 release is so recent (day before survey) that any
missing BERTMap features will be added there before DeepOnto becomes a real
dependency. **Action: revise §3.2 T2 to WATCH-not-ADOPT.**

**Matcha-as-sidecar option** is the second trade-off. Strategy says reconsider
(F=0.941 SOTA is hard to walk away from). Data Ontologist + Manufacturing-Quality
have no objection. RDM has a small GPL-license-exposure concern but agrees a
sidecar process-boundary is the standard mitigation. Resolution: **add the
sidecar-option clause to §3.2 T3 with the F < 0.85 trigger.**

### TIB Hannover collaboration outreach — yes, recommend

Three personas land here independently:
- Strategy (F3.2): TIB maintains both OntoAligner + metadata4ing; same research
  group as Shepard's m4i pin
- RDM (implicit in F2.1, F2.2): mapping-commons community is the natural FAIR
  network; TIB is an NFDI4Ing hub
- Data Ontologist (implicit): CHAMEO ↔ MatOnto evaluation needs benchmark
  community engagement that TIB participates in

**Outreach shape (recommended):**
1. PR back to OntoAligner with Shepard's `shepard-plugin-ontology-mapper`
   listed as a downstream user (`README.md` "Used by" section).
2. Present Shepard's CHAMEO ↔ MatOnto MFFD case study at the next
   NFDI4Ing Section Metadata + Terminologies + Provenance workshop.
3. Propose an OAEI 2026 MSE track revival co-authored with TIB + DLR (if MFFD
   data can be sanitised; gated on F4.4 IP boundary).
4. Co-author a short paper "AI-assisted ontology alignment in industrial
   research data management — the Shepard / NFDI4Ing case" for ESWC 2026
   resource track.

---

## §6 ESCALATIONS

These items contradict the existing §11 OQ-6 closure OR existing HARD design
assumptions and need explicit human resolution before implementation:

**[E1] OQ-6 closure is sound; no escalation.** Verified `aidocs/platform/86 §4`
contents directly: it specifies exactly the three-step BYOK chain claimed
(`UserPreferences → :AiCapabilityConfig → error`). §8 provenance writes the
`(:User)-[:wasAssociatedWith]->(:AiActivity)` triple regardless of which
credentials resolve. §13 LiteLLM proxy handles billing routing. The
ontology-mapping plugin's `@AiCapabilityRequirement(capability = STRUCTURED,
hardDep = true)` declaration is correct per §9. **No contradiction.**

**[E2] "W3C-style" wording in §1.3, §5.3, §8.1 is factually inaccurate.** SSSOM
is NOT a W3C standard. This is a small wording change but it appears 3 times in
the survey and once in `aidocs/reading-list.md` (line 125: "W3C-blessed mapping
serialisation"). All four locations should be updated to
"OBO Foundry-endorsed community standard". **Surfaced as escalation because
the reading-list.md entry will propagate into future docs if not corrected.**

**[E3] DeepOnto staleness contradicts `feedback_reuse_before_reimplement.md`.**
The reuse-before-reimplement rule explicitly says "activity heuristic — last
commit, contributors, releases/year". A dependency with one release 14 months ago
is the exact case the rule warns about. **Survey's §3 ADOPT call for T2 violates
the rule it cites.** Must be downgraded to WATCH before the survey can advance to
`feedback-implemented`. **This is the BLOCKING finding.**

**[E4] OAEI 2025 MSE-track regression silently invalidates §3.2 T1's
"MSE track support out of the box" claim.** OAEI dropped MSE after 2023; the
MSE-Benchmark repo has been dormant since Nov 2022. Shepard cannot point at a
2025/2026 SOTA score for the CHAMEO ↔ MatOnto pair. **Strategy lens proposes
turning this into the OAEI 2026 collaboration opportunity (see §5 outreach
proposal item 3).**

**[E5] TIB Hannover outreach decision** — recommended by three personas
independently; user signal required on (a) does Flo have an NFDI4Ing contact
already at TIB? (b) is MFFD data sanitisable enough to share as an OAEI
benchmark? Both gate the §5 outreach plan.

**[E6] EASA Learning Assurance applicability** — Manufacturing-Quality F4.1
flagged that if MFFD pivots to safety-critical use, the plugin needs a much
larger compliance posture. Currently scope is research-RDM (not airworthiness),
so this is documentation-only. But the assumption needs explicit confirmation.

---

## §7 Net verdict + change-request priority

**All four personas: ACCEPT-WITH-CHANGES.** Stage advancement to
`audited-by-personas` is appropriate; advancement to `feedback-implemented` is
gated on the changes below.

Highest-priority change-requests (in implementation order):

| Order | Change | Lens | Severity | Files |
|---|---|---|---|---|
| 1 | **Downgrade DeepOnto T2 from ADOPT to WATCH** (§3.2 T2; activity stale 14 months) | Strategy | **BLOCKING** | survey §3.1, §3.2 |
| 2 | **Replace "W3C-style" → "OBO Foundry-endorsed community"** (3 occurrences in survey + 1 in reading-list) | RDM | MAJOR | survey §1.3, §5.3, §8.1; reading-list line 125 |
| 3 | **Add Matcha-as-sidecar option to §3.2 T3** (trigger: F < 0.85 on OntoAligner baseline) | Strategy | MAJOR | survey §3.2 |
| 4 | **Add NFDI4Ing / TIB collaboration synergy row to §9** (PR to OntoAligner; ESWC 2026 paper; OAEI 2026 MSE revival proposal) | Strategy | MAJOR | survey §9 |
| 5 | **Acknowledge OAEI 2025 MSE-track absence in §3.2 T1** (revise "MSE-track support out of the box" claim) | Strategy | MAJOR | survey §3.2 |
| 6 | **Add structured `f-ai-r:rejectionReasonCode` triple** (alongside the narrative string) | Manufacturing-Quality | MAJOR | survey §5.2 |
| 7 | **Define SHACL validation rule for §5.1 "Endorsed" rung** (or downgrade rung to "second human" until #127 SHACL changeover lands) | Manufacturing-Quality | MAJOR | survey §5.1 |
| 8 | **Add §10.6 "MFFD IP boundary"** (mapping sends term labels only, never instance values) | Manufacturing-Quality | MAJOR | survey §10 |
| 9 | **Add `f-ai-r:proposalModel` triple to §5.2 example** (model + provider + version + datacentre) | RDM | MAJOR | survey §5.2 |
| 10 | **Add §11.1 acceptance criterion #11** (provenance-chain SPARQL test for any Reviewed mapping) | Manufacturing-Quality | MAJOR | survey §11.1 |
| 11 | **Add §11.1 acceptance criterion #0** (plugin lands before 2026-08-02 for AI Act Article 50 timing) | Strategy | MAJOR | survey §11.1 |
| 12 | **Clarify n10s projection handling for `sssom:Mapping` individuals in §4.5** | Data Ontologist | MINOR | survey §4.5 |
| 13 | **Add §5.3 RDA Maturity Indicator per-dimension table** | RDM | MINOR | survey §5.3 |
| 14 | **Add §8 SSSOM-export header `acceptance_threshold` extension slot** | RDM | MINOR | survey §8 |
| 15 | **Add §6 "first known aerospace-composite CHAMEO application" caveat** | Data Ontologist | MINOR | survey §6 |
| 16 | **Add §5.5 EASA Learning Assurance non-applicability note** | Manufacturing-Quality | MINOR | survey §5 |
| 17 | **Adopt MappingSet-level license discipline (one named graph per source/target/license triple)** | RDM | MINOR | survey §8.4 |
| 18 | **§8.6 explicit per-mode `semapv:*` justification mapping** | Data Ontologist | MINOR | survey §8.6 |

**One BLOCKING + 9 MAJOR + 8 MINOR.** The BLOCKING (DeepOnto staleness) plus the
top three MAJOR (W3C wording, Matcha-sidecar, TIB outreach) should land before
the survey advances to `feedback-implemented`. The remaining MAJORs can land in
the design-doc follow-up at `aidocs/semantics/99-ontology-mapping.md` (per
survey §11.2 OQ-7). MINORs land at implementation time.

---

## Appendix — research probes consulted

WebSearch / WebFetch / paper-search probes batched 2026-05-23:
- SSSOM W3C-status verification → confirmed NOT a W3C standard
- DeepOnto last release / GitHub commit signal → v0.9.3 (March 2025), stale
- OntoAligner PyPI release history → 8 releases between Jun-2025 and May-2026
- OAEI 2025 results page + track list → no MSE track, no materials track
- MSE-Benchmark GitHub status → dormant since Nov 2022 (last campaign OAEI 2023)
- NFDI4Ing collaboration patterns + DFG grant 442146713 → TIB co-funded
- EU AI Act Article 50 enforcement date → 2026-08-02 confirmed
- EASA AI Concept Paper Issue 2 W-shape → confirmed for safety-critical context
- CHAMEO real-world use cases → nanoindentation + battery; aerospace gap
- Matcha-DL semi-supervised + GPL license confirmed
- AI plugin design `aidocs/platform/86 §4 + §8 + §13` directly read for OQ-6
  verification → closure confirmed

External citations per persona: 5 / 6 / 10 / 5 = 26 total external citations
across the audit. Quality bar (≥3 per persona) exceeded for all four lenses.
