---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 88 — NFDI4Ing alignment: positioning Shepard inside the German engineering RDM federation

**Audience.** DLR stakeholders evaluating whether Shepard is the
"NFDI4Ing-compliant engineering RDM platform" or merely uses one
NFDI4Ing vocabulary; NFDI4Ing programme participants assessing
DLR's Phase 2 contribution; thesis readers (§1 + §4 of
`project_thesis_idea.md`) tracking how Shepard relates to Germany's
national engineering data infrastructure.

**Status.** Reconstructed from Flo Krebs's NFDI4Ing 1+2 intro
slide deck (uploaded to AI working memory 2026-05-23 as
`b7cde45d-20251103_Intro_NFDI4Ing_12__Kopie.pptx`, dated
2025-11-03, eight slides). The deck is an internal-DLR
positioning briefing for ZLP / BT / RY audiences explaining what
NFDI4Ing is and what DLR's Phase 2 commitments are.

This document **complements** `aidocs/semantics/94-metadata4ing-integration-design.md`:
doc 94 is the m4i vocabulary operationalisation (how Shepard's
graph speaks m4i); this doc is the **org/political/funding-cycle
layer** that frames why doc 94's work matters institutionally.

---

## 1. What NFDI4Ing is, in three paragraphs

The **Nationale Forschungsdateninfrastruktur (NFDI)** is the
German national research-data-infrastructure programme — a
federation of 26 consortia plus the cross-cutting Base4NFDI,
covering 400+ institutions, coordinated by the NFDI e.V. The
mission, from the deck: *"data as a common good for excellent
research, organized by the scientific community in Germany"*. NFDI
contributes to the European Open Science Cloud (EOSC) as Germany's
upstream connector.

**NFDI4Ing** is the engineering-sciences consortium within NFDI —
one of the 26. Per the deck, it has been *"actively engaging
engineers since 2017 (2019 with funding), now boasting over 50
members and continuing to expand"*. The deck explicitly notes
NFDI4Ing's relevance for DLR Aeronautics, Space and Transport
(with NFDI4Energy covering Energy). NFDI4Ing's central output for
Shepard is the **metadata4ing (m4i) vocabulary** — see doc 94 for
the operationalisation.

NFDI4Ing's organisational shape (deck slide 6) is **two core task
area groups**: "Overarching solutions" (cross-cutting coordination,
scaling products, preventing silos) and "Archetype" (method-focused
engineering approaches). They collaborate on shared goals; the
overarching half is where Shepard's federation-and-standards story
plays.

---

## 2. NFDI4Ing Phase 2: what DLR has committed

The deck's slide 7 names DLR as an internal NFDI4Ing partner for
Phase 2 and lists two specific measures with DLR participation:

| Measure | Title | DLR partners (named) | External partners |
|---------|-------|----------------------|-------------------|
| **F-1** | *Practical guidelines for data integration* | DLR (institutes IW, TS, BT, RY, VE per slide 7 partner list) | WZL, IPT, IoP |
| **F-2** | *Data Mesh to RDM adaption concept* | DLR | WZL, IPT, TUB |

**Phase 2 runtime: 2025 – 2030**, with DLR contributing 4 person-months/year.

### F-1: practical guidelines for data integration

From the deck:
> "Based on previous work, we have hands-on knowledge on the
> sharing and reuse of data, especially in the context of
> combining existing data sets with each other to enhance their
> value. On this basis, we create a guideline for data integration
> focused on applicability."

**What this means for Shepard.** F-1 is a guidelines document, not
a code deliverable. Shepard's role is **evidence supply**: every
production import (the MFFD ingest arc, the LUMEN-showcase
synthetic dataset, the home-showcase MQTT bridge) demonstrates a
data-integration pattern that the guideline can codify. F-1 is the
externally-visible writeup of what Shepard practitioners do
operationally.

The MFFD multi-source-collection import (cube3 + nuclide; v15
importer evolution; ZLP Augsburg's two production source
collections) is **directly the kind of case study F-1 needs**.
The shape "preserve the source-system folder semantics, layer
m4i-aligned attributes on top, run a snapshot-bracketed forging
pass to reorganise" is a concrete pattern that generalises.

### F-2: Data Mesh to RDM adaption concept

From the deck:
> "While measure F-1 is focused on the conceptional introduction
> of data integration, measure F-2 is leveraging and adapting the
> (industrial) concept of 'Data Mesh' for RDM along the principles."

**What this means for Shepard.** F-2 is the **architecturally
interesting** commitment. Data Mesh (Dehghani 2019; not yet in
bib) is a decentralised data-architecture pattern from industry:
domain-owned data products, federated computational governance,
self-serve data platform, data-as-a-product mindset. Shepard's
shape — per-instance Shepards owned by domain teams (BT, RY,
ZLP), federated via Unhide + Databus + RO-Crate, with shared
semantic substrate (m4i) — **is already a Data Mesh** in the RDM
domain. F-2 is the writeup that makes this explicit.

The conceptual gap to close (per the deck): adapting the
**industrial** Data Mesh principles (which assume commercial
data ownership, profit-centre alignment, ETL pipelines) to
**RDM** (which has research-group ownership, FAIR alignment,
process-driven provenance). Shepard is positioned as the
reference implementation — but the writeup must articulate
what doesn't transfer cleanly, not just what does.

### What this WP doesn't yet have

- A **public Data Mesh reference doc** for RDM that names
  Shepard as the implementation. The thesis (per
  `project_thesis_idea.md` outline §3 + §6) is the long-form
  argument; F-2 is the short-form summary that should land in
  an NFDI4Ing publication.

- **Comparison against other consortia's data-mesh attempts** —
  NFDI-MatWerk, NFDI4Chem, NFDI4Culture have parallel approaches.
  The honest comparison is part of F-2's deliverable.

---

## 3. NFDI4Ing alignment by Shepard layer

Mapping the alignment claim concretely:

| NFDI4Ing artefact | Shepard layer | Status |
|-------------------|---------------|--------|
| **metadata4ing (m4i) vocabulary** | Internal semantic repo (n10s); UH1b feed; PROV1h JSON-LD render | Pinned 1.4.0; deepening slices designed (`aidocs/semantics/94`); audit found two predicate-naming bugs (M4I-b in doc 94) |
| **NFDI4Ing Terminology Service** | Admin ontology preseed (N1c2); admin-configurable per `aidocs/semantics/65` | Static bundle today; live federation against `terminology.nfdi4ing.de` is design-pending |
| **HPMC sub-ontology** (production-and-manufacturing specialisation, `nfdi4ing.de/5-23-2/`) | Mentioned in doc 94 §1; not yet a payload-kind target | Survey-only; the MFFD AFP domain is the obvious target if HPMC's coverage holds up |
| **F-1 data integration guidelines** | MFFD multi-source-collection import arc; v15 importer; dataset-forging pattern | Operational evidence exists; writeup-pending |
| **F-2 Data Mesh adaption** | Multi-instance federation (cube3 + nuclide + future cube); Unhide feed; Databus integration (HMC-WP-3, see `aidocs/strategy/90` §5) | Architectural shape in place; conceptual writeup pending |
| **NFDI4Energy Databus + MOSS integration** | Direct cross-funding overlap with HMC WP-3 | Federation-substrate manual already in artefacts pile (`6e8723b0-NFDI4Energy_databus_and_MOSS_guide.pdf`); see `aidocs/strategy/90` §5 |
| **Base4NFDI cross-cutting services** | Not currently wired | Survey-pending; PID4NFDI is the obvious first integration target (overlaps `aidocs/strategy/90` WP-1's PID flow) |
| **EOSC** (upstream) | Indirect, via NFDI as Germany's EOSC connector | No direct integration; Shepard's RO-Crate + Unhide feed are the EOSC-compatible outputs |

---

## 4. Cross-funding leverage: HMC ↔ NFDI4Ing

A single body of work funds twice when the deliverables align.
Concrete overlaps to exploit:

- **HMC WP-2 "semantic features extensions" ↔ NFDI4Ing m4i
  deepening.** Same code path (the M1 wave + m4i renderer
  bug-fixes); HMC funds the usability half, NFDI4Ing funds the
  vocabulary-fidelity half. See `aidocs/semantics/94` for the
  technical shape.
- **HMC WP-1 "PID integration" ↔ NFDI4Ing Base4NFDI PID4NFDI
  service.** Same operator-facing goal (mint a DOI from a
  Collection); two upstream service providers (HMC's KIP +
  NFDI4Ing's PID4NFDI). The right shape is a `PidMinter` SPI
  in Shepard with both implementations.
- **HMC WP-3 federation prototype ↔ NFDI4Ing F-2 Data Mesh
  writeup.** The prototype IS the evidence the writeup cites.
  Same engineering work, two reporting destinations.

The honest read: this is **good politics**, not duplicate work. A
single Shepard contributor's PR can plausibly hit two funding
deliverables. Track which deliverable each PR maps to in the
commit message (per `aidocs/strategy/85` PM-policy on
Conventional-Commits scope).

---

## 5. Competitive positioning inside NFDI4Ing

NFDI4Ing has multiple engineering RDM tool stacks among its
members. The honest competitive position:

- **Kadi4Mat** (KIT) — the materials-science-leaning sibling.
  Complementary, not competing (see `project_competitive_position.md`).
  Kadi4Mat targets benchtop materials experiments; Shepard
  targets industrial-scale process data + large timeseries +
  multi-substrate storage. Overlap is the m4i layer (both speak
  it); divergence is the substrate (Kadi4Mat is monolithic
  Postgres-based, Shepard is multi-substrate).
- **NFDI-MatWerk infrastructure** — adjacent consortium, not
  Engineering proper, but parallel RDM efforts.
- **DataPLANT** — agricultural sciences, adjacent NFDI consortium.
  Shares m4i adoption.
- **CoScInE** (RWTH Aachen) — administrative-storage layer;
  Shepard could be the data-modelling layer on top, not a
  competitor.

The deck names DLR as a Phase 2 partner alongside WZL, IPT, IoP,
TUB — these are the institutions Shepard interoperates with, not
competes against. The competitive position inside NFDI4Ing is
**"the DLR engineering-industrial RDM platform with m4i alignment
and the multi-substrate / large-timeseries / multi-instance
strengths the rest of the consortium lacks."**

### 5.1. The NFDI4Ing community best-practice handbook

Schlenz, Bronger, Selzer, Nestler, Riem and Enahoro (2026) ---
*Research Data Management: A Practical Introduction*
(Zenodo 18468308, Version 1.1, CC BY-SA 4.0; ref
[`schlenzRdmHandbook2026`](../../docs/_data/references.bib)) ---
is the community-standard best-practice handbook for
engineering-research RDM in Germany. It was funded directly by
the **NFDI4Ing CADEN archetype** (the codes D.B.B01953 and
D.B.C02632 are Schlenz's CADEN funding) and presented at
**CoRDI 2025 at RWTH Aachen**. The author team is itself the
maintainer cohort for the three free ELNs the handbook profiles:
Bronger maintains JuliaBase + SciMesh at FZJ; Selzer / Nestler /
Riem maintain Kadi4Mat at KIT.

Treat this handbook as the **frame Shepard implements**, not the
frame Shepard departs from. Specifically:

- **DMP template (§ 4.1 -- 4.6)** — six DFG-aligned questions
  (`Data description`, `Documentation and data quality`,
  `Storage and technical security`, `Legal obligations`,
  `Data exchange and permanent accessibility`,
  `Responsibility and resources`). Shepard's Collection-level
  metadata can be projected into exactly this DMP shape; the
  `shepard-plugin-dmp` candidate (queue task; future work) would
  ship a stable Collection-attribute schema + a PDF /
  RDMO-compatible export per these six headings. Closing this
  alignment is the most legible NFDI4Ing-community deliverable
  Shepard can ship.
- **FAIR life-cycle (§ 2.2)** — handbook subsection-titles its
  P as "Processability" while the parent paragraph still names
  it "Interoperability" (the canonical Wilkinson-2016 letter).
  This is editorial slip, not framework divergence. Shepard's
  F / A / I / R compliance scoring (per
  `project_competitive_position.md` gap analysis) maps without
  remapping.
- **Data quality (§ 6)** — handbook defines the systematic /
  statistical error split + names CADEN's **AI-supported
  intrinsic data analysis with automatic outlier detection**
  as the in-flight CADEN contribution (NFDI4Ing-internal, not
  yet released). This is the directly-overlapping near-future
  effort to Shepard's `shepard-plugin-ai` quality-flagging
  capability (per `project_ai_data_arranger.md`). The right
  posture is **alignment + handoff**: track Bronger's CADEN AI
  output, integrate as upstream when it ships, do not
  re-implement.
- **SciMesh ELN-to-ELN data exchange (§ 7.1 -- 7.4)** —
  HTTP GET on sample URIs returning RDF; opaque mass-data via
  URL with multihash-suffixed content-addressed fragment
  (`<base>base<version><multihash>`). This is **exactly the
  federation pattern Shepard implements** (Garage CAS substrate
  + per-DataObject n10s repository + the cross-instance
  federation arc HMC WP-3 prototypes; see
  `aidocs/strategy/90` § 5). Shepard could become a SciMesh
  node with a thin REST adapter (HTTP GET on `appId` → RDF
  content-negotiation). `shepard-plugin-scimesh` is a
  reasonable plugin-first candidate; the surface area is
  small (one well-defined GET shape) and the alignment
  benefit is large (instant interoperability with every
  CADEN-using institute).
- **MetaData4Ing (§ 7.5)** — handbook places m4i alongside
  SciMesh as the two "data-tracking" approaches; positions m4i
  as "more for IT experts / application developers". This is
  useful contextual framing for Shepard's own m4i alignment
  in `aidocs/semantics/94`: Shepard exposes m4i to applications
  via JSON-LD render (M4I-b) AND speaks the lighter SciMesh
  shape to researcher-facing peers, sitting between the two
  layers the handbook describes.
- **Coscine (§ 9.3)** — handbook treats Coscine as the
  RWTH/KIT-axis administrative-storage layer for RDM. Shepard's
  position (as flagged in § 5 above) is **above** Coscine
  (modelling + provenance) rather than competing with it; the
  natural integration is Shepard-as-a-data-modelling-layer-on-
  top-of-Coscine-storage. No work scheduled, but the option
  is open.
- **ELN comparison criteria** — § 5 of the handbook (JuliaBase
  / eLabFTW / Kadi4Mat detail tours) implicitly compares ELNs
  along five axes: open-source licensing, customisability
  to existing workflows, sample-vs-experiment-vs-process model
  fit, REST-API completeness, federation-readiness (SciMesh
  adoption). Shepard is **not an ELN** — but the same five
  axes are what a thesis-examiner or a CoRDI-conference
  reviewer will apply to it. § 4.1 of the thesis-positioning
  doc (`aidocs/98-thesis-perspective.md`) carries the explicit
  scorecard.

The honest read: NFDI4Ing's published best-practice frame
**already does not assume Shepard exists** (the handbook names
JuliaBase, eLabFTW and Kadi4Mat as the three free ELNs to know;
Shepard is not in scope). That is the **gap Shepard's NFDI4Ing
visibility work has to close** — not by displacing any of the
three named ELNs, but by occupying the
"industrial-scale-process-data + multi-substrate +
large-timeseries" slot the handbook visibly does not currently
have a candidate for. The handbook's CoRDI 2025 launch is the
venue Shepard's first NFDI4Ing-facing paper should target
(CoRDI 2026 or 2027; cross-reference § 5.2 of
`aidocs/strategy/90` for the publication calendar).

---

## 6. What this means for Shepard's roadmap

Three things follow from being an NFDI4Ing Phase 2 partner:

1. **The m4i deepening slices (`aidocs/semantics/94` M4I-a..f)
   are doubly-load-bearing.** They satisfy HMC's WP-2 commitment
   AND NFDI4Ing's m4i-fidelity expectation. M4I-b (the renderer
   bug-fix) is the highest-leverage item: fixing it makes
   Shepard's PROV-O exports m4i-canonical, which directly affects
   how Shepard data lands in the NFDI4Ing Terminology Service +
   any consortium-wide harvester.

2. **F-1 evidence-supply runs almost-for-free.** Every production
   ingest the MFFD pipeline runs is F-1 case-study material.
   The labour is in the writeup, not the engineering. Schedule:
   one F-1 contribution per year of Phase 2 is achievable.

3. **F-2 is the thesis-bridge.** The Data-Mesh-for-RDM writeup
   that NFDI4Ing wants for F-2 is largely the same content as
   `project_thesis_idea.md` outline §3 (Architecture) +§4
   (Domain-driven ontology). Coordinating the two deliverables —
   thesis + F-2 — is a content-economy move.

---

## 7. Sources

- `b7cde45d-20251103_Intro_NFDI4Ing_12__Kopie.pptx` — NFDI4Ing 1+2
  intro deck by Florian Krebs, DLR ZLP Augsburg, dated 2025-11-03.
  Uploaded to AI working memory 2026-05-23. Bib entry:
  `nfdi4ingIntroKrebs2025` (see `docs/_data/references.bib`).

- `nfdi4ingMetadata4ing` (existing bib) — m4i vocabulary itself.

- `aidocs/semantics/94-metadata4ing-integration-design.md` —
  technical operationalisation of m4i in Shepard.

- `aidocs/strategy/90-hmc-phase-2-positioning.md` (sibling) —
  HMC Phase 2 WPs; cross-funding overlap explained in §4.

- `aidocs/strategy/76-shepard-users-and-citations.md` — adjacent
  context (existing Shepard adopters across DLR).

- `aidocs/integrations/66-hmc-kip-integration.md` — KIP DOI flow
  (the PID4NFDI overlap target).

- `schlenzRdmHandbook2026` — Schlenz, Bronger, Selzer, Nestler,
  Riem, Enahoro (2026) *Research Data Management: A Practical
  Introduction*. Zenodo 18468308, Version 1.1, CC BY-SA 4.0;
  NFDI4ING CADEN-funded; CoRDI 2025 launch. The
  community-standard best-practice handbook for
  engineering-research RDM in Germany. Primary source for the
  § 5.1 positioning. Web-fetched primary source 2026-05-23;
  full PDF read in `/tmp/rdm-handbook/`.

---

## 8. Honest companion

NFDI4Ing alignment is **claimed in the deck**. The deck is a 2025
positioning document; the Phase 2 measures start in 2025 and run
to 2030. As of 2026-05-23 the deliverables are:

- F-1 writeup: 0% drafted
- F-2 writeup: 0% drafted
- M4I deepening (the technical substrate F-1 + F-2 lean on): partly
  designed (`aidocs/semantics/94`), not yet shipped
- Public NFDI4Ing presence (a conference paper, a Terminology
  Service contribution, a Base4NFDI integration): not yet visible

A Phase 2 mid-cycle (≈ 2027-08-01) check should compare each row
of §3 against actual ship state. If multiple rows still read
"design-pending", this doc has aged into a marketing artefact and
needs a §6.1 "what slipped" appendix.

The thesis (per `project_thesis_idea.md`) is the longest-lived
deliverable into which the F-1 + F-2 work plausibly composts; the
Phase 2 commitments are the work-package shape of the same
intellectual programme.
