---
stage: feature-defined
last-stage-change: 2026-05-23
audience: [strategy, funding-reviewer, plugin-author, mffd]
---

# 92 — Aerospace-X and the aerospace regulatory context

**Audience.** Funding reviewers asking how Shepard relates to the
emerging German/European aerospace dataspace; plugin authors
evaluating whether the Catena-X / Tractus-X / AAS stack is a
serious integration target; MFFD operators wondering whether
their data will eventually federate beyond ZLP; thesis readers
(§6 case study, §9 discussion in `project_thesis_idea.md`)
tracing how Shepard's federation story interacts with the
industrial dataspace landscape.

**Status.** **feature-defined**. This doc captures the regulatory
context (the "regulatory tsunami" framing) and the dataspace
landscape (Manufacturing-X / Aerospace-X) that Shepard's
federation work-package (HMC WP-3,
`aidocs/strategy/90-hmc-phase-2-positioning.md` §5) eventually
intersects. The doc is reconstructed from the *AerospaceX:
Charting a course through regulatory storms towards sustainable
aerospace solutions* deck by Holger Weber et al. (the deck's PPTX
core.xml names Weber as creator) uploaded to AI working memory
2026-05-23 [@weberAerospaceX2025].

This document complements:

- `aidocs/strategy/90-hmc-phase-2-positioning.md` §5 (WP-3 — DLR
  cross-institute federation) — the **internal-to-DLR** federation
  story; Aerospace-X is the **DLR-and-industry** federation story
  that eventually subsumes it.
- `aidocs/integrations/77-databus-moss-federation.md` — the
  open-data-substrate federation track; Aerospace-X is the
  industrial-data-space alternative track. Both exist; they
  serve different audiences.
- `aidocs/integrations/52-aas-backend-integration.md` — AAS is
  Catena-X's technological base and therefore Aerospace-X's
  building block; Shepard's AAS work is the precondition for
  any future Aerospace-X integration.

---

## 1. The "regulatory tsunami" — why this matters now

The deck opens with a timeline of European and German regulations
relevant to aerospace manufacturing data. The full timeline names:

- **REACH (2007)** — chemical safety
- **RoHS (2013)** — restriction of hazardous substances
- **Paris Agreement (2015)** — climate-policy substrate
- **DSGVO / GDPR (2016)** — data-protection compliance
- **SDGs (2016)** — sustainability framing
- **German Packaging Act (2019)** + **European Green Deal (2019)**
- **EU Circular Economy Action Plan (2020)**
- **German Carbon Tax (2021)**
- **Ecodesign for Sustainable Products Regulation (EPR) incl.
  Digital Product Passport (DPP) (2022)** + **ESG / Supply Chain
  Act (2022)**
- **EU Battery Regulation (2023)** + **CSRD reform (2023)**
- **ESPR in force (PCF for batteries) (2025)**
- **Carbon Border Adjustment Mechanism (CBAM) (2026)**
- **Digital Battery Passport (2027)** + **DPP fully in force
  (2030)** + **Minimum recycling quotas for batteries (2036)**

The deck's framing is direct: this is the **regulatory tsunami**
the German aerospace industry has to navigate. The framing is
politically chosen — "tsunami" foregrounds the compliance burden,
not the policy goal — but the timeline is empirical, and the
compliance burden is real. Each regulation creates a data
obligation: someone has to record, store, and produce on demand
the evidence that the regulated activity complied.

The deck names four concrete data pains the current state of
aerospace data exchange produces:

1. **No seamless data transfer** — bespoke formats for 1:1
   exchanges.
2. **Paper- and PDF-based documentation** along the supply chain.
3. **Multiple scanning** of documents introduces document-quality
   degradation.
4. **Time-consuming manual processes** (e.g. comparing
   paper-vs-paper or paper-vs-electronic).

The named exemplar is the *Certificate of Conformity*: every
aerospace part ships with one, today as a PDF, often re-scanned
multiple times along its multi-tier supply chain.

This is the demand-side that motivates digital product passports
and a sovereign data exchange substrate. The deck names two
load-bearing reasons specifically:

- **Export control** — ITAR, Dual-Use, sanctions, embargos
  require full, reliable, accessible data on the provenance of
  every component. The deck notes that geopolitical
  tensions raise the friction tax on incomplete data.
- **Traceability of "Suspected Unapproved Parts"** — the
  counterfeit-part risk. The deck cites the
  Boeing/Airbus 2024 counterfeit-titanium and 737/A320
  counterfeit-engine-part findings (Bloomberg 2023 onward) as
  evidence that incomplete supply-chain data is a flight-safety
  risk. Aircraft serve up to 60–70 years (P3C Orion, AWACS); the
  MRO market is the most exposed.

The argument the deck makes — and Shepard sits inside — is that
the data substrate **either makes the regulations manageable or
makes them existential**. The cost is not in producing the data
the first time; it is in producing it again every time someone
asks.

## 2. Manufacturing-X and Aerospace-X — the German response

**Manufacturing-X** is the BMWK-funded (German Federal Ministry
for Economic Affairs) programme building on Industrie 4.0 to
establish a *decentrally organised data economy* for German and
European industry. **Aerospace-X** is its aerospace-industry
arm — submitted by the BDLI (Bundesverband der Deutschen
Luft- und Raumfahrtindustrie, the German aerospace industry
association) and led by **Airbus Operations GmbH** as
consortium lead.

### 2.1 Scope and shape

- **Consortium:** more than 30 companies, associations, and
  research institutions
- **Kickoff:** April 2024
- **Duration:** 27 months
- **Scope statement (deck):** *"Supply Chain for Aircraft
  Manufacturing in Germany focusing fuselage, Cabin and Engine"*
- **Strategic objectives (deck):**
  1. Build a trusted data ecosystem for the aerospace industry
  2. Ensure digital continuity via common standards for aerospace
     product manufacturers, OEMs, and the multi-tier supply chain
  3. Enable a new form of collaborative, data-based value creation
  4. Decrease manual efforts; increase efficiency and transparency

The strategic objectives are notable because they read almost
verbatim like the Shepard vision (`aidocs/42-vision.md`) — but
addressed to a different audience (industry instead of research)
and at a different layer (supply chain instead of single
laboratory).

### 2.2 The four use cases

The deck names four use cases the consortium is delivering:

1. **Demand and capacity management**
2. **CO₂ emissions and PCF (Product Carbon Footprint) rules**
3. **Circularity / R-Strategies** (the EU Circular Economy
   Action Plan's family of "Reduce, Reuse, Recycle, Recover, ..."
   strategies)
4. **End-to-end quality** — the use case the deck dwells on,
   with a concrete worked example (§2.4 below).

### 2.3 The technical vision: Catena-X / AAS / Tractus-X

The deck's technical-vision slide makes three explicit claims:

- *Catena-X uses AAS as the technological base for (standardized)
  digital twins.* Aerospace-X inherits this; AAS is therefore the
  federation primitive.
- *Building on existing infrastructures and implementations.* The
  current implementation is open-source: **Eclipse Tractus-X**.
- *Highly decentralized structures give data providers full
  control over what data they share and with whom.*

The shape is therefore: each participant runs an EDC (Eclipse
Dataspace Connector); the EDCs federate via the
Tractus-X protocol; the data products exchanged are AAS
submodels backed by Digital Twin Registry entries. The deck's
diagram (slide 12, "Manufacturing-X — A blueprint for sovereign
data exchange", attributed to SAP) is the canonical reference
illustration.

For Shepard, this means: **the federation seam to Aerospace-X is
AAS**. Shepard's existing AAS integration design
(`aidocs/integrations/52-aas-backend-integration.md`) is the
precondition. The next step is an EDC-side adapter; that adapter
is not yet designed.

### 2.4 The worked use case: E2E quality management for aero engines

The deck's slide 13 — sourced from Bergs & Ganser, *Aerospace-X —
Towards multi-tier traceability in the manufacturing of safety
critical aerospace components*, IMEC2024 — gives a concrete
three-step worked example:

1. **Production monitoring and early warning** — *"Material batch
   shows difficult machinability..."* — a Tier-1 manufacturer
   detects an anomaly during production.
2. **Traceability and root-cause analysis** — *"Cracks at drill
   holes detected during MRO..."* — years later, an MRO operator
   investigates a defect.
3. **Containment and countermeasure** — *"Engines identified where
   worn tool was used..."* — the data trail makes it possible to
   identify exactly which engines were affected.

The data flow goes: Tier-n → Tier-2 → Tier-1 → Engine-OEM →
Aircraft-OEM → Airline, with EDCs at every node and a Digital Twin
Registry resolving identifiers across the chain.

This is the **scaled-up, multi-party version** of the digital
thread Shepard demonstrates inside a single research centre. The
shape is the same; the operational scale (30+ companies, 27-month
delivery, regulatory contractual constraints) is what makes
Aerospace-X a hard problem.

## 3. The Shepard angle — what fits, what doesn't

### 3.1 What fits

- **Provenance discipline.** Shepard's PROV1a + PROV1h + f(ai)²r
  vocabulary (`project_fair2r_integration.md`) is the trace
  Aerospace-X's "Traceability and root-cause analysis" needs.
  Shepard sits naturally at any Tier-n that runs research or
  prototyping work feeding the chain — e.g. DLR ZLP feeding
  MFFD-derived data into an Airbus-led downstream chain.
- **AAS as the federation primitive.** Already a Shepard design
  target. The submodel templates Shepard plans to support
  (per `aidocs/integrations/52`) overlap with the templates
  Aerospace-X's E2E-quality use case uses.
- **Catena-X / Tractus-X open-source substrate.** Eclipse-licensed
  software the Shepard plugin model can adopt directly (per
  `feedback_reuse_before_reimplement.md`). The plugin-shape
  candidate is `shepard-plugin-edc` — an Eclipse Dataspace
  Connector adapter that exposes Shepard collections as offered
  data products and consumes Aerospace-X data products as Shepard
  imports.
- **CHAMEO + material-OWL grounding.** Aerospace-X's E2E-quality
  use case needs material-batch identity to flow across tiers;
  Shepard's CHAMEO bindings (designed for the MFFD AFP material
  story) are the same primitives.

### 3.2 What doesn't fit naturally

- **Commercial confidentiality.** Aerospace-X participants exchange
  IP-sensitive supply-chain data under contractual NDAs. Shepard
  is open-source research-data-management; the production
  Shepard deployment at DLR ZLP would need careful access-control
  posture if it ever offered data products into an Aerospace-X
  dataspace.
- **Operational maturity.** Aerospace-X's 27-month delivery
  window targets production-grade software, not research-grade.
  Shepard is at production-grade in the research-data
  sense (live MFFD ingest), but not in the
  industrial-software-quality-assurance sense Aerospace-X's
  industrial partners expect.
- **The "Made-in-Germany via BDLI / Airbus / SAP / large-OEM"
  governance.** Shepard's open governance model (DLR as steward,
  open contribution) does not naturally slot into an
  industry-association-led consortium. The DLR-side participation
  in Aerospace-X is via its industrial-partner program, not via
  ZLP's Shepard work directly.

### 3.3 The honest mid-term claim

Shepard is **not yet** an Aerospace-X data-product producer. It
**could become** one via a `shepard-plugin-edc` adapter, once the
M1 wave + AAS integration ship. The political precondition is the
DLR-internal federation prototype (HMC WP-3) demonstrating that
multiple DLR Shepards can federate — that demonstrator is what
makes "DLR can also play in Aerospace-X" credible.

The thesis-relevant claim is *the architectural shape Shepard ships
generalises beyond research-data into industrial-dataspace use*.
Whether this fork ever produces production-grade EDC code is a
separate question from whether the shape is right.

## 4. Caveats and challenges (from the deck's own §5)

The deck's own *caveats / challenges / outlook* slide names the
things Aerospace-X has not yet solved. Two are worth carrying
forward into Shepard's planning:

- **Export control as the boundary condition.** The deck flags
  that decisions about export control (ITAR, Dual-Use) require
  "full, reliable and accessible data" — and that Digital Product
  Passports will be a *"valuable support"*. The same condition
  binds Shepard the moment a Shepard collection contains
  export-controlled data. Shepard's access-control posture today
  is **not yet hardened against export-control-grade
  requirements**; this is an open backlog item.
- **AI-driven applications.** The deck's outlook slide names "AI
  applications" as a future challenge the consortium is preparing
  for. Shepard's f(ai)²r vocabulary
  (`project_fair2r_integration.md`) and the AI provenance UI
  badges (`project_ai_human_collab_provenance.md`) are the
  cross-cutting story Shepard can tell that Aerospace-X has not
  yet operationalised. There is a contribution path here, not
  just an integration path.

## 5. Forward backlog candidates

These are not yet `aidocs/16` rows but should be considered:

- **`shepard-plugin-edc`** — Eclipse Dataspace Connector adapter
  that exposes Shepard Collections as Tractus-X data products and
  consumes the same. Depends on AAS work (per
  `aidocs/integrations/52`).
- **Export-control access-control posture review** — a security
  pass on Shepard's permission model evaluating it against
  export-control-grade data confidentiality. Owner: the
  PERM-SYSTEM-REVIEW backlog row.
- **Digital Product Passport export** — Shepard's RO-Crate +
  Unhide feed have the FAIR side; a DPP-formatted export
  (whatever the eventual EPR-mandated shape turns out to be)
  is the supply-chain side. Out of scope until the DPP
  standard stabilises.

## 6. Sources

- `ff21078f-AerospaceX__Charting_a_course_through_regulatory_storms_towards_sustainable_aerospace_solutions.pptx`
  — 14-slide deck, creator (per PPTX core.xml) Weber, Holger;
  title fragment "Joint Target Picture". Uploaded to AI working
  memory 2026-05-23. Bib entry: `weberAerospaceX2025`. The deck
  is the primary source for the regulatory timeline (§1), the
  consortium shape (§2), the technical vision (§2.3), and the
  E2E-quality worked example (§2.4).

- Bergs, Berend and Ganser, Patrick — *Aerospace-X — Towards
  multi-tier traceability in the manufacturing of safety critical
  aerospace components*. IMEC2024 (the 20th International Machine
  Tool Engineers' Conference), 2024. Cited on slide 13 of the
  deck. Bib entry: `bergsAerospaceXImec2024`.

- `aidocs/strategy/90-hmc-phase-2-positioning.md` §5 — the
  internal-DLR federation work-package this externalises to.
- `aidocs/integrations/52-aas-backend-integration.md` — AAS
  integration design (the federation primitive).
- `aidocs/integrations/77-databus-moss-federation.md` — the
  open-data-substrate parallel federation track.

## 7. Honest companion

This doc records what the Aerospace-X deck **claims for itself**
and where Shepard would intersect *if Shepard's federation work
matures*. It does not claim Shepard is part of Aerospace-X today —
it is not. It does not claim that a `shepard-plugin-edc` is
imminent — it is not. It claims that the architectural
shape Shepard has converged on is *compatible* with the
industrial-dataspace direction the German aerospace industry has
chosen, and that compatibility is the thing to preserve as
Shepard's design evolves.

The regulatory tsunami is real either way. Whether Shepard's
federation work-stream eventually plugs into Aerospace-X or
into NFDI4Ing's Data Mesh or into HMC's joint architecture is a
question of which audience Shepard chooses to serve first. The
honest answer today is *research first, industry second*; the
audience boundary is HMC + NFDI4Ing this funding cycle,
Aerospace-X next.
