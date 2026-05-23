---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 94 — Federation and dataspaces: where Shepard sits in Manufacturing-X / Aerospace-X / Catena-X

**Audience.** Funding reviewers tracing the federation horizon
Shepard is committed to; contributors planning the
`shepard-plugin-publisher` and Aerospace-X EDC adapters; thesis
readers (§3 / §9 of the outline in `project_thesis_idea.md`)
asking *"what does Shepard's federation story look like beyond
RO-Crate?"*.

**Status.** Distilled from two primary sources by the WP-author
uploaded to AI working memory 2026-05-23:
@krebsAerospaceXJaxa2025 (10-slide JAXA briefing) and the
federation-relevant portion of @zlpDataDrivenIntelligence2025
(slides 18–24 of the 29-slide ZLP deck). Two hand-drawn
architecture sketches received in session chat the same day
[@krebsFederationSketches2026] anchor the local-to-federation
articulation. Sibling to `aidocs/strategy/87-dlr-zlp-positioning.md`
(centre-level positioning), `aidocs/strategy/88-nfdi4ing-alignment.md`
(NFDI4Ing federation), `aidocs/strategy/90-hmc-phase-2-positioning.md`
(HMC WP-3 cross-DLR federation), and
`aidocs/strategy/92-aerospace-x-regulatory-context.md`
(the regulatory-tsunami framing from the Weber 2024 BDLI deck —
this doc covers the architecture-side of Aerospace-X; the 92 doc
covers the regulatory motivation). Each of those docs describes
*one* federation horizon; this doc names the
**industrial-dataspace** horizon and how the three converge.

---

## 1. Manufacturing-X: the framework Shepard's federation work
   compiles against

Manufacturing-X is the German Industrie-4.0-platform-led
initiative to build *autonomous data spaces* for industrial value
chains, with the automotive Catena-X programme as the blueprint
[@krebsAerospaceXJaxa2025]. Three core goals carry institutional
weight at DLR:

1. **Establishing an autonomous data space** for Industrie 4.0,
   with Catena-X as the operational blueprint.
2. **Integrating SMEs** via needs-based application strategies
   (the platform must not be solvable only by Airbus-scale actors).
3. **Internationalising** by implementing global standards for a
   comprehensive data economy.

The architectural delta from a traditional Industry-4.0 platform
is sharp [@zlpDataDrivenIntelligence2025, slide 21]:

| Dimension | Traditional Industrie 4.0 platform | Manufacturing-X |
|-----------|------------------------------------|-----------------|
| Applications | Provided by the platform owner; all integrated and aligned | Multiple competing solutions from various vendors; each vendor provides and operates their own |
| Topology | Central network service | (Slim) federated operating environment provided by an operating company (joint-venture of multiple companies); data exchange decentral / directly between network participants |
| Data persistence | Centrally owned by the platform owner | Decentral — data resides with the data owner who can grant access to others |

The federated-and-decentral posture is exactly the posture
Shepard has chosen at the architecture level (multi-substrate,
plugin-extensible, no central registry of record). Manufacturing-X
is the **federation framework** Shepard's design compiles against —
not a competing product.

## 2. Catena-X as the operational blueprint

Catena-X is the **automotive Manufacturing-X realisation**
[@krebsAerospaceXJaxa2025, slide 5; @zlpDataDrivenIntelligence2025,
slide 22]. Two facts from the deck shape Shepard's design space
materially:

- **AAS as the digital-twin technological base.** Catena-X uses
  the Asset Administration Shell (AAS, Plattform Industrie 4.0)
  as the standardised representation of every asset in the
  ecosystem. The 26-million-AAS estimate cited in the deck
  (parts, products, production resources) is the figure that
  forces the conclusion: any aerospace-side substrate that does
  not speak AAS natively will be unable to ride into the
  ecosystem. Shepard's AAS-bridge work
  (`aidocs/integrations/68-aas-bridge.md` if filed, otherwise
  `aidocs/16` row AAS-1; the `aidocs/strategy/87` ZEUS-horizon
  argument requires this).
- **Eclipse Tractus-X as the open-source code base.** The
  ecosystem implementation is already open source under the
  Eclipse Foundation. Shepard's federation adapter does not have
  to invent the EDC (Eclipse Dataspace Connector) protocol —
  it has to *consume* it. The plugin shape is therefore an EDC
  client wrapped as a `shepard-plugin-edc` rather than a
  reimplementation.

The architectural posture Shepard inherits from this: **the
substrate is the source of truth for its own contents; the EDC is
the boundary through which the substrate participates in a wider
data space**. The two concerns are deliberately separated.

## 3. Aerospace-X: the aeronautical adaptation Shepard is targeted at

Aerospace-X is the aeronautics-sector adaptation of
Manufacturing-X [@krebsAerospaceXJaxa2025, slide 3 and slide 7;
@zlpDataDrivenIntelligence2025, slide 20]. Two slides from the
ZLP deck make the positioning unusually explicit.

**Slide 20 — Aerospace-X Aeronautical Dataspace** lays out a
Germany map with **EDC nodes at Stade, Aachen (Brunswick),
Stuttgart, and Augsburg** (the four bold-marked DLR / industrial
sites). On the left, the *Apps / Use-Cases* layer names the four
Manufacturing-X priorities translated into German: Nachfrage und
Kapazitätsmanagement, CO₂ Emissionen und PCF, Kreislaufwirtschaft,
E2E-Qualitätsmanagement. Below it, the *Core Services* layer
names six federated services: Digital Twin Registry, Portal,
Marketplace (Contracting / Apps), Semantic / Self-Description Hub,
Identity Provider / DAPS, Business Partner Management. Augsburg
is one of the four named EDC sites — ZLP Augsburg is therefore
within the German federation footprint, not adjacent to it.

**Slide 23 — First planned application: E2E-Quality assurance**
is the slide that names Shepard inside the federation. The
diagram has three horizontal layers: a top layer of *Core
Services* + *Apps*; a middle IT layer with three components — an
*EDC* connector, an *AAS Repository*, and **`SHEPARD`** — all
linked bidirectionally; and a bottom OT (operational technology)
layer with the four manufacturing process stages
*PREPARE → MAKE → CHECK → DELIVER* (material input → manufacturing
→ quality assessment → tracked shipping), with the CHECK stage
ringed in red. The slide's goal sentence: *"establish data
foundation and methods for cross-supply-chain quality control."*

This slide is the single most important piece of external evidence
for the thesis's positioning chapter: **Shepard is named, in a
shop-floor-to-dataspace architecture, as the substrate underneath
the AAS Repository on the IT side of the IT/OT boundary**. It is
not adjacent to the AAS Repository; it is the system the AAS
Repository draws from to populate the digital-twin records that
the EDC then publishes to the federation. The architectural
claim Shepard's design makes — *graph-organised provenance + typed
storage substrates + open standards* — has external institutional
endorsement to be exactly that substrate.

## 4. The Krebs federated-Shepard sketch — articulating the local
   commitment

A hand-drawn architecture sketch by the WP-author received in
session chat 2026-05-23 [@krebsFederationSketches2026, sketch A]
makes the local-side architecture explicit. The sketch shows the
DLR-internal process chain a researcher walks through:

> Simulation/Auslegung (as-designed) → Bauteilherstellung → NDT
> Prüfung (Laser US) → Re-Simulation (as-built) → Maintenance
> [with side-branch: Destructive Testing]

Each step is annotated with the responsible organisational role
(SY/VPH, SY/BT, BT/MO?, SY/VPH, VPH, MO — DLR institute /
department abbreviations). Below the entire chain, a single
horizontal bar reads **`Common Data Infrastructure (shepard)`**,
with bidirectional arrows up to every process step.

The sketch's footnote is the key claim and the reason this
document exists:

> *"2 Wege: Export Import zur Datenweitergabe (mittels RO-Crate)
> / Federated shepard service (viel Aufwand, höheres Risiko)"*

Two integration paths are named explicitly, with their
risk-vs-effort trade-off recorded:

- **Path A — Export/Import (RO-Crate).** Each Shepard instance
  publishes RO-Crates and consumes RO-Crates. Federation is a
  *protocol* — agreed-upon JSON-LD + checksum + manifest shape —
  not a runtime coupling. Lower effort, lower risk; instances
  remain independently operable. This is what the HMC Phase 2
  WP-1 deliverable (`aidocs/strategy/90` §3) is already
  implementing.
- **Path B — Federated Shepard service.** A second Shepard
  instance behaves as a peer of the first: live queries, live
  joins, shared identity, shared annotation surface. Higher
  effort, higher risk; instances become functionally coupled.

The thesis architecture chapter (§3 of
`project_thesis_idea.md`) should record this as the explicit
**design-by-honest-trade-off** decision: Shepard's near-term
federation story is **Path A**, not Path B. The sketch is not
arguing for Path B; it is arguing that Path B exists, that the
author understands its cost, and that the choice to ship Path A
first is deliberate. The Manufacturing-X / Aerospace-X
federation infrastructure (EDC + AAS Repository + Digital Twin
Registry) is precisely the *external* substrate that lets Path A
scale — Shepard publishes RO-Crates, the EDC publishes the
Catena-X-shaped digital-twin records, and the federation handles
the cross-instance routing.

The two paths are not exclusive in the long run. Path A is the
operational minimum; Path B is the design horizon that becomes
realistic once the EDC ecosystem stabilises.

## 5. The internationalisation horizon — what JAXA was about

The JAXA briefing's closing slide [@krebsAerospaceXJaxa2025,
slide 9; @zlpDataDrivenIntelligence2025, slide 24] records the
international scope: Manufacturing-X already has institutional
contact points in Japan (Robot Revolution & Industrial IoT
Initiative; KOSMO Korea), Canada (Offensive de Transformation
Numérique), Italy (Confindustria), Australia, France
(Alliance Industrie du Futur), and Smart Industry (Netherlands).
The author's slide makes the **opportunity** explicit:

> *"Opportunity for lighthouse project: establish a (prototypical)
> transatlantic dataspace between Canada and Germany to foster
> data-driven collaboration based on emerging standards (AAS,
> Manufacturing-X / Gaia-X)."*

The thesis's discussion chapter (§9 of
`project_thesis_idea.md`) takes this as evidence that the
federation horizon is **not just intra-DLR** (the
`aidocs/strategy/90` WP-3 scope) or **intra-Helmholtz** (the HMC
PoF V framing). It is *international*, and Shepard's design
choices — open source, European-jurisdiction storage (Garage),
AAS-ready, EDC-targetable — are the prerequisites for being
inside that federation rather than adjacent to it.

The 2022 Catena-X reference points the author preserves in the
deck (Catena-X uses AAS as the digital-twin base; Eclipse
Tractus-X is the open-source code base; estimated 26 million AAS
in the ecosystem demonstrator) are the *boundary conditions* the
international federation will inherit. Shepard's federation
roadmap is sized against those numbers, not against single-DLR
demonstrators.

## 6. Convergence with the other federation horizons

This document and its three siblings each name a different
federation horizon Shepard has commitments toward:

| Horizon | Document | Federation substrate | Status |
|---------|----------|----------------------|--------|
| Intra-DLR (cross-institute) | `aidocs/strategy/90` §5 (HMC WP-3) | Databus + MOSS + Unhide feed | Designed; survey work pending |
| Intra-Helmholtz (FAIR + PID) | `aidocs/strategy/90` §3 (HMC WP-1) | RO-Crate + ePIC / DataCite | RO-Crate shipped; PID flow designed |
| Inter-NFDI (engineering ontology) | `aidocs/strategy/88-nfdi4ing-alignment.md` | m4i + NFDI4Ing Data Mesh | Design-and-survey arc |
| Industrial dataspace (this doc) | `aidocs/strategy/92` (this doc) | AAS + EDC (Tractus-X) + Catena-X / Aerospace-X | External substrate maturing; Shepard's plugin shape designed (EDC adapter), not yet built |

The horizons are not competing. Each adopts a different protocol
because each serves a different audience: HMC WP-3 serves DLR
institutes; HMC WP-1 serves Helmholtz reviewers; NFDI4Ing serves
engineering researchers; Manufacturing-X serves industrial supply
chains. Shepard's job is to be **the same substrate underneath
all four** — which is the central architectural claim of the
thesis (§3 of `project_thesis_idea.md`) and the reason a
plugin-first SPI is load-bearing rather than incidental.

The convergence point is **m4i + AAS**: m4i carries the research
semantics (NFDI4Ing horizon), AAS carries the industrial
semantics (Manufacturing-X horizon), and the two have to
co-exist on the same Shepard collection without forcing the
researcher to choose. The `aidocs/semantics/94-metadata4ing-integration-design.md`
M4I-d slice (AAS-submodel ↔ m4i alignment, if filed; otherwise
the cross-cutting design row in `aidocs/16`) is the seam where
that co-existence is engineered.

## 7. What this doc establishes for the thesis

For the thesis architecture and discussion chapters, this doc
delivers four answers:

1. **Why Shepard's federation story is plural, not singular.**
   Four horizons, four substrates, one Shepard. The thesis can
   defend this as architectural humility — no single federation
   protocol covers all four audiences — rather than as
   incoherence.
2. **Why "Path A first, Path B later" is the right local
   architecture.** The Krebs federated-Shepard sketch records
   the deliberate trade-off in the author's own hand. The thesis
   can cite it without speculation.
3. **Where the external endorsement comes from.** Slide 23 of
   @zlpDataDrivenIntelligence2025 names Shepard inside the
   Aerospace-X E2E-Quality architecture. The thesis's
   §1 introduction can cite this as evidence the substrate is
   *expected* by the German aerospace ecosystem, not merely
   *offered* to it.
4. **Why plugin-first matters now.** The convergence-point table
   (§6) makes the case directly: four federation substrates ×
   four audiences = four adapter plugins. A monolithic Shepard
   that tried to vendor all four would be unmaintainable; the
   plugin-first SPI (`aidocs/platform/47`) is the architectural
   premise that lets the substrate keep its identity while
   speaking four protocols.

## 8. Honest companion

This document records federation *commitments* and *external
positioning*, not federation *deliveries*. Reviewers using this
doc as evidence of Shepard's Manufacturing-X integration should
treat the following as ground truth, not promise:

- **Path A (RO-Crate export/import):** shipped (export FS1g) and
  used in production (MFFD ingest). Round-trip integrity not yet
  proven end-to-end.
- **AAS native support:** designed, not built. The integration
  row in `aidocs/16` (AAS-1, if not yet filed: the next
  federation-direction backlog item to file) is the
  acceptance-test gate.
- **EDC adapter (`shepard-plugin-edc`):** not yet designed. The
  Eclipse Tractus-X reference implementation is the upstream
  Shepard would consume; the adapter plugin is a future
  `shepard-plugin-*` per the plugin-first rule.
- **Slide 23 endorsement:** is a *plan* in an institutional deck,
  not a deployed system. The thesis can cite the architectural
  intent honestly; it cannot cite a working Augsburg-EDC
  installation.

When the deliveries arrive, this section grows a "what shipped"
appendix and the §6 table's "Status" column moves from
"designed" to "shipped."

---

## Primary sources

- @krebsAerospaceXJaxa2025 — *Aerospace-X — for JAXA*, slide deck
  by Florian Krebs, 2025-03-17 (10 slides; reuses material from
  a 2022-10-07 DLR ZAP Catena-X briefing). Uploaded 2026-05-23.
- @zlpDataDrivenIntelligence2025 — *DLR ZLP Augsburg — Data
  Driven Intelligence*, 29-page institutional deck, generated
  2025-01-27. Slides 18–24 + 12 cited here; remainder cited in
  `aidocs/strategy/87` §§5–6 and `aidocs/strategy/91` (ForInfPro
  outlook). Uploaded 2026-05-23.
- @krebsFederationSketches2026 — two architecture sketches by
  Florian Krebs received in session chat 2026-05-23. Sketch A
  (federated Shepard / Common Data Infrastructure) cited in §4
  above; Sketch B (Pub-Service publication architecture) cited
  in `aidocs/strategy/90` §3.

Citations resolve via [`docs/_data/references.bib`](../../docs/_data/references.bib);
see [`/bibliography`](/bibliography) on the live site.
