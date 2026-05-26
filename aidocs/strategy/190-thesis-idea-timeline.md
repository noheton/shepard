---
stage: concept
last-stage-change: 2026-05-26
title: Thesis idea timeline — Shepard research programme
audience: [thesis-substrate, contributor, supervisor-candidate]
sibling-docs:
  - aidocs/strategy/86-shepard-predecessor-systems.md
  - aidocs/strategy/104-author-research-profile.md
  - aidocs/strategy/88-nfdi4ing-alignment.md
  - aidocs/strategy/90-hmc-phase-2-positioning.md
---

# Thesis Idea Timeline

## How to read this

Each idea is a **research question** that Shepard's codebase and datasets
can serve as the primary empirical substrate for. The ideas are not "use
Shepard to store X"; they are "use Shepard as the substrate to prove Y
about X", where Y is a publishable, examinable, original contribution.

The timeline organises by maturity of the supporting evidence:

- **2024–2025 (Foundation layer)** — ideas rooted in already-shipped work.
  The codebase, the DLRK 2021 paper of record, and the iDMS predecessor
  chain supply the primary evidence today. A thesis submitted now could
  draw on these without waiting for further development.
- **2026 (Current horizon)** — ideas grounded in in-flight or near-term
  work. The MFFD import pipeline, the AI-provenance vocabulary, and the
  SHACL-substrate design are actively generating primary evidence.
- **2027+ (Speculative)** — ideas that need features or datasets not yet
  built (federation, real MFFD sensor data, multi-site deployment).

**Level notation.** Each idea carries `BSc | MSc | PhD` markers indicating
the most appropriate level for the **primary contribution**; lower levels
may use the same idea as a narrower sub-study.

---

## 2024–2025: Foundation layer

Ideas where the supporting evidence already exists in the codebase,
primary-source documents, or published literature. These could anchor a
thesis submitted in 2025–2026 without needing further feature development.

---

### T-01: Institutional predecessor critique as a requirements derivation method

**Level.** MSc (methods focus) / PhD (if formalised as a transferable methodology)

**Pitch.** Most RDM systems are designed against abstract user requirements.
Shepard was designed against a named sequence of failed predecessor systems
(PRAESTO → KIBID → iDMS) at the same institution, over 16 years, by the same
architect. This thesis asks: does the predecessor-critique method produce
architecturally superior systems compared with requirements-gathering-from-scratch?
The operationalisation is a structured failure-mode analysis of each predecessor,
mapped against the architectural decisions Shepard makes, to test whether each
design choice has a traceable predecessor-failure antecedent.

**Evidence from codebase.** `aidocs/strategy/86-shepard-predecessor-systems.md`
(full predecessor chain with primary sources); iDMS final presentation 2020-11-04
(primary source in thesis library); `docs/cv.md` (16-year institutional continuity
documented); `krebsDlrk2021` (architecture paper of record); `aidocs/34`
(upgrade-path discipline as operationalisation of the "don't repeat failure modes"
requirement).

**Method.** Structured case study; failure-mode attribution matrix (each
predecessor failure → Shepard design response); comparison with published RDM
evaluation frameworks (e.g., Kadi4Mat comparison, SciCat adoption studies).

**Novel contribution.** A formalised "institutional predecessor critique" method
as a transferable requirements-derivation pattern for domain-specific RDM tools.

---

### T-02: Plugin-SPI extensibility as a design pattern for domain-specific RDM platforms

**Level.** MSc / PhD

**Pitch.** Every major open-source RDM platform (openBIS, Kadi4Mat, SciCat,
FAIRDOM-SEEK) eventually faces the "new payload kind" problem: a research team
needs a data type the platform doesn't support. The typical responses are either
a monolithic core commit or a bespoke fork. This thesis evaluates Shepard's
Plugin-SPI design (`aidocs/platform/47-dev-experience-and-plugin-system.md`) as
a third response: a formal extensibility seam that isolates new payload kinds
without forking the core. The research question: does the plugin-SPI pattern
measurably reduce the time-and-code cost of adding a new payload kind?

**Evidence from codebase.** `aidocs/platform/47-dev-experience-and-plugin-system.md`
(SPI design); `de.dlr.shepard.plugin.PluginManifest` (the Java interface);
`shepard-plugin-unhide` (the first plugin under the new shape — UH1a shipped);
the 12-file PR overhead quantified in `aidocs/42` ("plugin from day one" policy
preventing the 12-file sprawl).

**Method.** Comparative code-change measurement: count files, lines, and coupling
edges required to add an equivalent payload kind via (a) upstream monolithic commit,
(b) Shepard pre-SPI approach (mirrored by the pre-PM1a codebase), and (c) the
plugin-SPI post-PM1a. Complement with a developer-effort micro-study if additional
contributors can be recruited.

**Novel contribution.** Empirical evidence for (or against) the plugin-SPI pattern
as a maintainability improvement in polyglot-persistence RDM platforms.

---

### T-03: Provenance-as-substrate vs provenance-as-log — comparing two approaches to research data lineage

**Level.** MSc

**Pitch.** Most scientific data management systems (from Galaxy workflows to
electronic lab notebooks) record provenance as an audit log appended alongside
the data. Shepard encodes provenance structurally: the predecessor/successor
graph IS the lineage; the snapshot chain IS the version history; each mutation
produces a PROV-O `:Activity` node that is itself a queryable graph entity. This
thesis asks whether the structural approach produces provenance that is (a) more
complete, (b) more machine-readable, and (c) more useful in practice than
log-based provenance, using the LUMEN showcase dataset as the empirical case.

**Evidence from codebase.** PROV1 series shipped features (PROV1a–h); `aidocs/55`
(activity dashboard design); the LUMEN seed demonstrating TR-004 → investigation
→ repair → TR-006 lineage (predecessor chain); `aidocs/42` "snapshot chain IS the
provenance" narrative; `de.dlr.shepard.provenance.*` package.

**Method.** FAIR data principles evaluation (FAIR R1.2 — provenance); structured
comparison against published provenance models (W3C PROV-O, Open Provenance
Model); user-study with 3–5 researchers navigating the TR-004 anomaly
investigation using (a) the Shepard provenance graph and (b) a reconstructed
flat audit log of the same events.

**Novel contribution.** Empirical comparison of structural vs. log-based provenance
in a real aerospace research data case, grounded in W3C PROV-O implementations.

---

### T-04: Snapshot-based reproducibility in research data management — a formal model and case study

**Level.** MSc / PhD

**Pitch.** "Reproducibility" in research is often reduced to "can I re-run the
analysis script?" Shepard's V2 snapshot design addresses a harder problem: can
a third party independently verify which data existed at the time a result was
published, not merely which code was run? This thesis formalises Shepard's
snapshot semantics as a reproducibility guarantee, specifying under what
conditions a snapshot-pinned RO-Crate export constitutes sufficient evidence for
independent replication, and under what conditions it does not.

**Evidence from codebase.** V2b + V2c + V2e + V2d shipped (`aidocs/41`);
`GET /v2/snapshots/{a}/diff/{b}` (V2e); snapshot-pinned RO-Crate export (V2d);
`backend/src/main/java/de/dlr/shepard/v2/snapshot/` package;
`aidocs/42` §"Versioning + snapshots".

**Method.** Formal specification (lightweight model using predicate logic or Alloy);
evaluation against the LUMEN showcase (can TR-004 → TR-006 be independently
reproduced from a snapshot-pinned export?); comparison against existing
reproducibility frameworks (ReproZip, MLflow, DVC).

**Novel contribution.** A formal reproducibility guarantee for snapshot-pinned
RO-Crate exports, with precisely stated boundary conditions.

---

## 2026: Current horizon

Ideas grounded in in-flight or near-term work. Supporting evidence is actively
accumulating; these ideas become examinable as the relevant features stabilise.

---

### T-05: AI-collaborative research as a documented, reproducible method — operationalising f(ai)²r

**Level.** PhD (methodological contribution)

**Pitch.** Most AI-assisted research reports that "we used LLM X for Y" in a
methods section, without capturing what prompts were used, which outputs were
accepted without modification, which were corrected, and what the aggregate
AI-contribution fraction was across the project. This thesis treats the
AI-collaborative development of Shepard itself as a **case study in
AI-co-authorship methodology**: the f(ai)²r vocabulary, the memory-rule
discipline, the snapshot-boundary rule, and the energy log constitute a
documented, transferable method for AI-collaborative research. The research
question: can the method be formalised and applied to a second research project
by a different team, and do the resulting provenance artefacts satisfy EU AI Act
Article 50 transparency requirements?

**Evidence from codebase.** `f(ai)²r` vocabulary (github.com/noheton/f-ai-r);
`aidocs/sustainability/00-energy-estimation-log.md` (214-commit footprint log,
~547 Wh for the active arc); `aidocs/strategy/89-genai-methodology-and-reflexivity.md`
(the GenKI deck reflexivity audit, no divergence finding); `feedback_*.md`
memory rules as the durable operationalisation; `project_collab_highlights.md`
(13-entry case-study payload); EU AI Act Article 50 transparency operationalisation
described in `aidocs/42`.

**Method.** Action research (the method is being practised on the thesis project
itself — the reflexive contribution is that the thesis can cite its own provenance
trail, distinguishing AI-co-authored from human-authored passages); transferability
tested by applying the method to a second small project (e.g., ForInfPro dry-spot
analysis sprint); EU AI Act Article 50 gap analysis against the resulting artefacts.

**Novel contribution.** A formally specified, transferable AI-collaborative research
method with an operationalised provenance trail — one of the first in an
aerospace manufacturing domain.

---

### T-06: SHACL-as-substrate: using RDF constraint shapes as data-model evolution anchors in polyglot-persistence systems

**Level.** PhD

**Pitch.** In a polyglot-persistence system (Neo4j + TimescaleDB + MongoDB + PostGIS
+ S3 as in Shepard), schema evolution is expensive: migrations must touch four or
five stores simultaneously. This thesis evaluates whether using SHACL constraint
shapes as a substrate-level domain model (the M1-VIEWS-AS-SHAPES approach in
`aidocs/semantics/98`) allows the domain model to evolve independently of the
storage schemas, reducing migration coupling and enabling "views as shapes"
— different constraint profiles for different audiences of the same underlying data.

**Evidence from codebase.** `aidocs/semantics/95-shacl-templates-and-individuals.md`;
`aidocs/semantics/98-shapes-views-and-process-model.md`;
`backend/src/main/resources/neo4j/migrations/` (migration corpus demonstrating
the coupling problem this approach aims to solve);
`aidocs/platform/87-timeseries-appid-migration.md` (a live multi-store migration
that a SHACL-substrate approach might have constrained more cleanly).

**Method.** Design science research: formalise the "views as shapes" model; implement
one SHACL-driven view for the MFFD process chain; compare migration effort for
adding a new field under (a) conventional polyglot migration and (b) SHACL-as-substrate;
evaluate against NFDI4Ing metadata4ing and ISO AP242 compliance targets.

**Novel contribution.** A formal design pattern for SHACL-as-substrate in polyglot
RDM platforms, with an empirical comparison of migration coupling.

---

### T-07: FAIR compliance by construction — evaluating semantic-annotation infrastructure in real aerospace RDM workflows

**Level.** MSc / PhD

**Pitch.** FAIR data principles (Findable, Accessible, Interoperable, Reusable)
are widely cited but rarely operationalised beyond a checklist. Shepard ships a
pre-seeded ontology stack (PROV-O, QUDT, metadata4ing, Dublin Core, schema.org,
GeoSPARQL, OBO RO, OM-2, W3C Time) and a runtime annotation infrastructure.
This thesis empirically evaluates whether that infrastructure, used in a real
aerospace manufacturing context (MFFD AFP layup), produces datasets that satisfy
FAIR at the level required by DFG / Horizon Europe / Clean Aviation JU mandates
— and what the gap is where it falls short.

**Evidence from codebase.** `aidocs/42` §"Semantic-annotation repositories";
`aidocs/agent-findings/research-data-manager.md` (existing FAIR gap analysis,
scores F:2/3, A:2/3, I:2/3, R:2/3 as baseline);
`backend/src/main/resources/neo4j/migrations/V49__Bootstrap_internal_semantic_repository.cypher`;
`aidocs/integrations/66-hmc-kip-integration.md` (KIP1 PID series); the Unhide
plugin UH1a–c as the harvester-feed test surface.

**Method.** FAIR assessment using the F-UJI automated FAIR assessment tool +
manual checklist against DFG-RDM guidelines; structured annotation of the MFFD
showcase dataset with the pre-seeded vocabularies; gap analysis against
CHAMEO + ISO AP242 as domain requirements; comparison with Kadi4Mat's
annotation model.

**Novel contribution.** Empirical FAIR compliance scores for a real aerospace
manufacturing RDM system, grounded in domain-specific controlled vocabularies,
with a gap-closure roadmap.

---

### T-08: Timeseries channel identity migration in research data management — a formal design and safety proof

**Level.** MSc (engineering focus) / PhD (formal methods emphasis)

**Pitch.** Research timeseries data is often originally addressed by a composite
multi-field key (in Shepard: `{measurement, device, location, symbolicName, field}`
— the "5-tuple"). As a dataset grows and is shared, the composite key becomes
a liability: every downstream consumer must embed five parameters to address one
channel, migration is non-trivial, and ML pipelines are fragile. The
TS-ID migration design (`aidocs/platform/87-timeseries-appid-migration.md`)
proposes a phased migration to a single stable `appId`. This thesis formalises
the migration as a state-machine refinement, proves safety properties
(no channel data is lost; old addresses remain resolvable during the migration
window), and evaluates the migration against the live Shepard TimescaleDB instance.

**Evidence from codebase.** `aidocs/platform/87-timeseries-appid-migration.md`
(full design, TS-IDa → TS-IDe phases); `backend/src/main/java/de/dlr/shepard/v2/timeseries/`
(current 5-tuple implementation);
`aidocs/agent-findings/analytics-ai.md` §"The 5-tuple ML pipeline tax"
(empirical friction evidence); TimescaleDB migration scripts.

**Method.** State-machine modelling (TLA+ or Alloy for the safety proof);
implementation of TS-IDa + TS-IDb; measurement of query-complexity reduction
before/after; ML-pipeline simplification demonstrated with a notebook
reconstructing the TR-004 anomaly detection chain.

**Novel contribution.** A formally verified migration design pattern for
composite-key → stable-ID transitions in research timeseries systems, with
an empirical evaluation.

---

### T-09: Energy and carbon accounting for AI-collaborative research software development

**Level.** MSc (methods study)

**Pitch.** AI-assisted software development consumes large quantities of LLM
inference compute. The sustainability cost is rarely measured. This thesis uses
Shepard's `aidocs/sustainability/00-energy-estimation-log.md` — a 214-commit
energy/CO₂ log tracking the AI-collaborative development of a research data
platform — as primary data, and asks: (1) how does AI-collaborative development
compare in energy efficiency to human-only development of equivalent functionality?
(2) what fraction of AI inference is cache-hit (re-use) vs. net-new compute?
(3) what is the break-even for AI assistance — at what feature complexity does
AI-collaborative development become energy-neutral relative to human-only?

**Evidence from codebase.** `aidocs/sustainability/00-energy-estimation-log.md`
(~547 Wh total for active arc, 63% cache-read finding, 11.76B tokens);
`aidocs/strategy/89-genai-methodology-and-reflexivity.md` §"model collapse" gap
(the Shumailov 2024 training-data hygiene dependency as a methodological risk).

**Method.** Longitudinal measurement study; energy-per-feature-point estimation;
comparison with published software-engineering energy benchmarks (e.g.,
Verdecchia et al. 2023 on green software); cache-hit rate as an efficiency metric;
carbon-intensity sensitivity analysis (us-east-1 vs. EU grid).

**Novel contribution.** One of the first empirical energy-accounting studies of
AI-collaborative research software development, with a replicable methodology.

---

### T-10: Ontology alignment for aerospace manufacturing RDM — CHAMEO, metadata4ing, and ISO AP242 in a unified provenance graph

**Level.** PhD

**Pitch.** The MFFD AFP process chain involves at least four ontology families
that must interoperate: CHAMEO (characterisation methods in materials science),
metadata4ing / m4i (NFDI4Ing engineering-research PROV-O extension), ISO AP242
(aerospace product data exchange), and Shepard's own PROV-O + OBO Relation Ontology
baseline. This thesis proposes and evaluates an alignment strategy for these four
families within Shepard's semantic substrate, producing a unified provenance
graph for one MFFD process step (AFP layup → NDT scan) that satisfies all four
ontology families' validation requirements simultaneously.

**Evidence from codebase.** `aidocs/semantics/94-m4i-deepening-design.md` (m4i
deepening design); `aidocs/agent-findings/ai-ontology-mapping-survey-2026-05-23.md`
(existing ontology mapping survey); `aidocs/strategy/91-forinfpro-semantically-driven-analytics.md`
(ODIX ontology architecture — CHAMEO + BFO/IOF/RO deferred framing);
`aidocs/agent-findings/data-ontologist.md` (gap analysis for AFP and PLUTO);
`V49__Bootstrap_internal_semantic_repository.cypher` (the pre-seeded vocabulary baseline).

**Method.** Ontology alignment (OWL/SKOS mapping patterns); SHACL constraint
validation across all four families for a representative MFFD DataObject;
gap analysis against ISO AP242 Part 59 (product lifecycle support); evaluation
with NFDI4Ing and HMC community reviewers.

**Novel contribution.** A formal alignment between four major aerospace
manufacturing ontology families within a deployed polyglot-persistence RDM system,
validated against a real process chain.

---

### T-11: The digital thread in thermoplastic CFRP manufacturing — Shepard as evidence substrate for a DIN EN 9100 audit trail

**Level.** PhD (industrial focus) / MSc (case-study focus)

**Pitch.** Aerospace quality management (DIN EN 9100, EASA Part 21G) requires
traceable, immutable records linking process steps, equipment calibration state,
operator credentials, and material batch data. "Digital thread" is the industry
term for the continuous data trail that connects design intent to as-built
reality. This thesis evaluates whether Shepard's provenance graph, snapshot
semantics, and semantic annotation layer can serve as the evidence substrate for
a DIN EN 9100 audit trail covering the MFFD AFP layup process — and formally
specifies what additional data model elements would be required to close
identified gaps (particularly NCR routing, concession approval, and calibration
certificate linking).

**Evidence from codebase.** `aidocs/agent-findings/manufacturing-quality.md`
(EN 9100 readiness assessment with gap table); `aidocs/42` §"Lifecycle status"
(ST1a shipped — status vocabulary gap identified); MFFD seed data and import
pipeline; `aidocs/strategy/91-forinfpro-semantically-driven-analytics.md`
(dry-spot reasoning chain as the intended audit target); `audetIcra2024` (the
FPC cell description as the physical process reference).

**Method.** Requirements analysis against DIN EN 9100:2018 (full standard);
entity-relationship gap analysis between Shepard's current data model and
EN 9100 evidence requirements; formal specification of the NCR entity and
rework-loop data model; pilot implementation with MFFD synthetic data; review
by a qualified EN 9100 lead auditor.

**Novel contribution.** A formal specification of the minimum data model
extensions for an open-source RDM platform to serve as a DIN EN 9100 audit
substrate, empirically validated against the MFFD AFP process chain.

---

## 2027+: Speculative

Ideas that require features not yet built, datasets not yet available, or
a deployment scale not yet reached. These are honest long-horizon bets.

---

### T-12: Cross-institute research data federation — a formal interoperability model for Helmholtz RDM instances

**Level.** PhD

**Pitch.** The BMFTR PoF V mandate (`bmftrPofV2025`) requires a "joint and
overarching architecture concept for the data-management infrastructure" across
Helmholtz centres. Shepard's federation design (`aidocs/strategy/94`) proposes
two paths: RO-Crate export/import (store-and-forward) vs. federated live query.
Neither path has been implemented at scale. This thesis formalises the
interoperability model for the federated path, specifying the protocol, the
identity model, the provenance bridge, and the access-control semantics
required for a three-instance Shepard federation (nuclide / DLR cube / PLUTO
instance), and evaluates the implementation against Helmholtz Open Science
guidelines and EOSC semantic interoperability requirements.

**Dependency.** Requires Wave 5 federation infrastructure (cross-instance prov UI
per `aidocs/frontend/100`), the Unhide plugin at production scale, and at least
one second Shepard instance in production at a different DLR institute.

**Novel contribution.** A formal interoperability model and reference implementation
for federated research data management across aerospace research institutes,
compliant with PoF V and EOSC requirements.

---

### T-13: Real-time process monitoring as a research data management problem — live sensor feeds, snapshots, and control-loop participation

**Level.** PhD (systems focus)

**Pitch.** Traditional RDM is retrospective — data is captured, then archived.
ForInfPro's "recorded ideal process pattern" and Shepard's experiment coordinator
design (`aidocs/50`) propose a different posture: the RDM substrate participates
in the live control loop, detecting deviations in real time and triggering
provenance records that support later audit. This thesis evaluates whether the
query latency and write throughput of Shepard's TimescaleDB substrate are
sufficient for real-time AFP process monitoring (frame rate: ~1 Hz for process
parameters; ~10 kHz for force/torque), and proposes an architecture for the
live-ingest → anomaly-detect → provenance-record pipeline.

**Dependency.** Requires real MFFD/AFP sensor data (not synthetic) piped through
the `shepard-timeseries-collector` into a live instance; the experiment
coordinator (PR1) at a production-usable stage.

**Novel contribution.** An empirical evaluation of polyglot-persistence RDM
infrastructure for real-time process monitoring, with a formal performance model
and a reference architecture for the live-ingest pipeline.

---

## Cross-cutting themes

Themes that recur across multiple ideas, and could serve as organising lenses
for a PhD that spans several of the above:

### Theme A: Predecessor critique as design method (T-01, T-02, T-11)

The PRAESTO → KIBID → iDMS → Shepard lineage is a rare case of a single
architect iterating on the same domain problem over 16 years with primary-source
documentation at each step. The predecessor-critique-as-design-method theme
unifies T-01 (method formalisation), T-02 (plugin-SPI as a specific outcome of
that method), and T-11 (the quality-management gap as a next iteration target).

### Theme B: Provenance as infrastructure, not afterthought (T-03, T-04, T-05, T-10)

Shepard encodes provenance structurally (snapshot chain, predecessor graph, PROV-O
activities). T-03 evaluates this design choice empirically; T-04 formalises its
reproducibility guarantees; T-05 extends the model to AI-collaborative development;
T-10 aligns it with aerospace manufacturing ontologies. A PhD spanning T-03 + T-05
+ T-10 would produce a unified framework for provenance-as-infrastructure in
domain-specific RDM systems.

### Theme C: The polyglot-persistence RDM platform as a research object (T-02, T-06, T-08)

Shepard's multi-substrate architecture (Neo4j + TimescaleDB + MongoDB + PostGIS + S3)
is itself a research object — few published RDM systems document the coupling costs
and migration complexity of this architecture choice. T-02 (plugin extensibility),
T-06 (SHACL-as-substrate), and T-08 (channel identity migration) all produce
transferable architectural knowledge that extends beyond Shepard.

### Theme D: AI transparency and the EU AI Act (T-05, T-09)

T-05 and T-09 together address the AI-transparency mandate from two directions:
T-05 provides the provenance vocabulary and method; T-09 provides the energy
accounting. A thesis combining both would be positioned at the intersection of
EU AI Act Article 50 compliance and sustainability accounting — a gap in the
published literature as of 2026.

### Theme E: Industrial aerospace as the empirical anchor (T-07, T-10, T-11, T-13)

The MFFD AFP process chain is unusually rich as a thesis empirical anchor: it is
a real JEC World Innovation Award-winning industrial demonstrator, it involves
multiple intersecting standards (DIN EN 9100, ISO AP242, CHAMEO), and the data
pipeline is directly accessible. T-07, T-10, T-11, and T-13 all use the MFFD
chain as their primary empirical case; a PhD combining two or three would produce
a coherent multi-chapter evaluation against a single industrial reference case.

---

## Sequencing recommendation

If the thesis must begin writing in 2026, the strongest starting position is:

1. **T-01** (predecessor critique method) as the literature-review anchor — the
   primary sources are already in the thesis library.
2. **T-05** (AI-collaborative method) as the methods chapter — uniquely reflexive,
   no additional implementation required.
3. **T-03 or T-04** (provenance) as the architecture chapter — shipped features
   supply the empirical evidence.
4. **T-07** (FAIR compliance) as the evaluation chapter — the FAIR gap analysis
   in `aidocs/agent-findings/research-data-manager.md` is the baseline; closing
   one gap for the thesis provides the "contribution".

This four-idea core (T-01 + T-05 + T-03/T-04 + T-07) maps onto the existing
thesis outline in `project_thesis_idea.md`: §2 Continuity + §5 Method +
§3 Architecture + §8 Evaluation respectively.

Ideas T-06, T-08, T-10, T-11 are strong MSc topics that colleagues at ZLP
or partner universities could pursue using Shepard as their substrate — each
one produces a contribution that feeds back into the platform.

---

*Librarian dispatch: 2026-05-26. Next pass triggers when: five or more new
artefacts arrive in the thesis library, a chapter advances stage materially,
or a supervisor/institution is confirmed.*
