---
title: Author research profile — Florian Krebs, DLR ZLP Augsburg
stage: feature-defined
last-stage-change: 2026-05-23
last-pass-by-librarian: 2026-05-23
audience: thesis-substrate, funding-reviewer, contributor
---

# Author research profile — Florian Krebs, DLR ZLP Augsburg

*Maintained research profile of the author-subject of the thesis project this
library serves. First dispatch of the maintained-profile pattern per
`feedback_thesis_librarian_standing.md §1b`; subsequent passes update it as
the library and its citations grow. The profile is a substrate document — it
supplies a thesis preamble, a funding-reviewer briefing, and a contributor
"who builds this" reference — and is not itself the thesis.*

## §1 At-a-glance

Florian Krebs is a research engineer at the **DLR Zentrum für
Leichtbauproduktionstechnologie (ZLP) Augsburg**, the German Aerospace
Center's institute for lightweight production technology and the host site of
the Multifunctional Fuselage Demonstrator (MFFD, JEC World Innovation Award
2025 — Aerospace Parts) [@mffdJecAward2025]. His research line at the
centre spans **sixteen years (2010–2026) of continuous work on the
data-substrate problem of full-scale composite manufacturing**: from the
control-concept document for the centre's flagship robot cell
[@krebsMfzSteuerung2010], through the in-house prototype iDMS he architected
2017–2020 [@krebsIdms2020], into the open-source Shepard substrate for which
he is lead author on the peer-reviewed architecture paper of record
[@krebsDlrk2021] and the named *Verantwortliche Person* (software maintainer)
of the noheton fork his thesis work is grounded in [@shepardFork]. The
intellectual trajectory is unusually coherent: predecessor systems
(commercial, vendor-built, prototype) failed by an enumerable set of
mechanisms his earlier work documents, and the substrate he now ships
(plugin-extensible, ontology-driven, AI-collaboratively developed,
federation-ready) is the structured response to each named failure.
Krebs's research method as practised on this thesis project is explicitly
AI-collaborative under his own publicly defended position
[@krebsGenKi2026]; the resulting thesis is reflexively a worked example of
the very pattern its substrate is built to capture.

## §2 Timeline of contributions

Reconstructed from the chronology in `aidocs/strategy/86-shepard-predecessor-systems.md`
§3.6 plus bibliography entries with Krebs as author or co-author. Entries
without a peer-reviewed venue are DLR-internal working documents preserved
in the project's primary-source archive (uploaded to AI working memory
2026-05-23). Bib keys resolve via [`docs/_data/references.bib`](../../docs/_data/references.bib).

| Year | Artefact / contribution | Role | Co-authors | Citation |
|---|---|---|---|---|
| 2010 | *Steuerungskonzept der MFZ-Anlage* — earliest recovered primary source; 21 internal revisions; control-concept document for the Multi-Function Cell (MFZ) at ZLP | sole author | — | [@krebsMfzSteuerung2010] |
| 2010 | *Themen Robotik für Programmpunkt 4* — 29-slide companion brief on robotics topics | sole author | — | [@krebsRobotikThemen2010] |
| 2012 | Office-door "Wanted" poster (35-word satirical notice) — included only as a register-of-voice primary source for the reflexivity chapter | sole author | — | [@wantedFlo2012] |
| 2013 | *Hightech auf vier Säulen — Glanzstück im DLR Augsburg* in *DLR magazin* #140 (Roboter issue) — public-facing institutional exposition of the MFZ at commissioning | co-author | Jung, B. | [@krebsJungDlrMagazin140] |
| (date undetermined) | *Input Florian — PRAESTO* — two-slide working deck naming Kisters AG as PRAESTO supplier; load-bearing for the misfit-thesis evidence | sole author | — | [@krebsInputPraesto] |
| 2017-04-25 | *ProDES v1 — Prozessdatenerfassungssystem* — names the "viele Inseln" diagnosis; layered architecture; NoSQL technology survey | sole author | — | [@krebsProDES2017a] |
| 2017-06-19 | *ProDES v2* — bridges the PRAESTO authoring community (Nuschele, Schuster) to the iDMS architect (Krebs) | co-author | Nuschele, S.; Schuster, A. | [@krebsProDES2017b] |
| 2017-07-18 | *Status Prozessleitsystem* — modular Plug-and-Automate goal; Self-X as future ambition | sole author | — | [@krebsStatusProzessleitsystem2017] |
| 2017-09-14 | *NSR Orchestrator Konzept* — IPRO architecture diagram naming KiBiD Adapter alongside KUKA / Festo / Siemens; primary-source proof KIBID was IPRO's data layer | sole author | — | [@krebsNSROrchestrator2017] |
| 2017-10-10 | *Industry 4.0 defined* — external curation reframing the work in Industrie-4.0 vocabulary | curator | — | [@krebsI4Definition2017] |
| 2017-12-04 | *NSR Architektur* — architecture-vision week opens | sole author | — | [@krebsNSRArchitektur2017] |
| 2017-12-05 / -12 | *Vision: Datenmanagement* — *Herausforderung: Datenmanagement* diagnosis the iDMS data model is built to answer | co-author | Nuschele, S. | [@krebsVisionDatamanagement2017] |
| 2017-12-06–08 | *Robotics: Human-Robot-Interaction* — DLR@UBC workshop deck (Vancouver); same robot-cell testbed that becomes the VAM-HRI 2018 paper | co-author | Schönheits, M. | [@dlrSchoenheitsKrebsHriWorkshop2017] |
| ≈ late 2017 / early 2018 | *SOA MES* — CUBE acronym first appears; **Haase named as workshop co-organiser** — earliest Haase↔Krebs personal link | sole author | (Haase co-organiser) | [@krebsSoaMes] |
| 2018-03 | *Embedding AR in Industrial HRI Applications* — six-page VAM-HRI 2018 workshop paper (ACM); industrial HRI testbed on KUKA KR120 Quantec extra HA + HoloLens + UWB-RTLS + OPC-UA; **first peer-reviewed publication on which Krebs is named** | co-author | Schönheits, M. | [@dlrSchoenheitsVamhri2018] |
| 2020-11-04 | *CUBE iDMS — Final Presentation* — Krebs-architected research-data prototype used in IPRO; Neo4j + MongoDB + InfluxDB substrate stack; Project / Experiment / Step entity model; PROV provenance; the direct architectural ancestor of Shepard | sole author / presenter | — | [@krebsIdms2020] |
| 2021 | *Systematische Erfassung, Verwaltung und Nutzung von Daten aus Experimenten* — DLRK 2021; **load-bearing peer-reviewed publication of record for Shepard's architecture**; Krebs is lead author jointly with all four named Zenodo software-citation authors | lead author | Willmeroth, M.; Haase, T.; Kaufmann, P.; Glück, R.; Deden, D.; Brandt, L.; Mayer, M. | [@krebsDlrk2021] |
| 2024 | *Iterative Robot Calibration with Accuracy Saturation Termination …* — ICRA 2024 (IEEE); ICAST algorithm; in-situ elastic calibration of the DLR ZLP T-AFP cell; joint DLR–NRC publication | co-author (sixth-named, DLR side: Vistein, Brandt, Krebs) | Audet, J.-M.; Fortin, Y.; Côté, G.; Vistein, M.; Brandt, L.; Monsarrat, B. | [@audetIcra2024] |
| 2025-02-21 | Vision slide (slide 23 of the BT/ZLP capability inventory) — Shepard positioned as the cross-domain substrate spanning Structural mechanics / Materials / Design+Process / Production / Ground+Flight Test | sole author | — | [@krebsInventur2025] |
| 2025-03-17 | *Aerospace-X — for JAXA* — author-led JAXA briefing on adapting Catena-X / Tractus-X to aeronautics | sole author | — | [@krebsAerospaceXJaxa2025] |
| 2025-04-18 | *Fähigkeiten-Inventur DLR-BT — Standort Augsburg / ZLP* — comprehensive 31-slide machine-portfolio inventory; the basis of the rollout plan at `aidocs/strategy/100` | sole author | — | [@krebsInventur2025] |
| 2025-05-05 | *HMC 2 Workpackages @ DLR ZLP* — Phase 2 work-package proposal naming three Shepard-bearing WPs (export/import + PID, semantic-features, cross-DLR DM interoperability) | sole author | — | [@hmcPhase2WpKrebs2025] |
| 2025-07-23 | *KUKA CNC Robotics* — internal technology-strategy evaluation of KUKA × Sinumerik 840D integration | sole author | — | [@krebsKukaCnc2025] |
| 2025-09-30 | *ODIX — Milestone Report (Milestone 2)* — cross-cutting milestone covering four HAPs of the DLR ODIX project; documents the JSON-as-intermediate pivot and the platform-side shape gap | sole author | — | [@odixMilestone2_2025] |
| 2025-11-03 | *Intro NFDI4Ing (1+2)* — DLR ZLP positioning briefing on NFDI4Ing Phase 2 (2025–2030); Measure F-1 + Measure F-2 commitments | sole author | — | [@nfdi4ingIntroKrebs2025] |
| 2025-11-05 | *Integrating Metrology, Spatial Analyzer and Industry 4.0* — IREC methodology talk; 89.30 % mean / 70.19 % maximum TCP-deviation reduction within ±0.3 mm aerospace tolerance | sole author | — | [@krebsMetrologyI42025] |
| 2026-02-26 | *Generative KI: Technologie, Ethik und Zukunft — Wie LLMs (unser) Arbeiten und Denken (verändern)* — 17-slide author-led tutorial with explicit closing AI-Disclosure slide; the methodological deck of the thesis | sole author | — | [@krebsGenKi2026] |
| 2026 | *Semantically-Driven Data Analytics* (ForInfPro deck, 11 slides) — DIKW framing; the canonical container/data-object contextualisation diagram; the dry-spot reasoning chain | co-author | Glück, R.; Lettowsky, F.; Kaufmann, P.; et al. | [@krebsForInfPro2026] |
| 2026 | *Humanoids 2026: Trends, Achievements, Gaps — Implementation in Manufacturing* — market and capability survey of humanoid-robot deployments in manufacturing | sole author | — | [@krebsHumanoids2026] |
| 2026-05 | *Projektentwurf: DIVA — Drone Integration via AAS* (V01 skizze) — LuFo VII-2 idea sketch; names BT/ZLP's contribution as the AAS-Repository-Infrastructure (**Shepard**) for a five-DLR-institute consortium | sole author | — | [@krebsDIVA2026] |
| 2026-05-23 | Federated-Shepard architecture sketch + Pub-Service whiteboard sketch — two hand-drawn architecture sketches articulating the two-path federation trade-off (RO-Crate export/import vs federated service) | sole author | — | [@krebsFederationSketches2026] |
| 2026 | *shepard (noheton fork)* — software artefact; Krebs is the named *Verantwortliche Person* (software maintainer) for the fork | sole maintainer | — | [@shepardFork] |
| 2026 | *f(ai)²r — FAIR-for-AI Research vocabulary* — software / specification; the AI-provenance vocabulary the thesis substrate operationalises | sole author | — | [@fair2rVocab] |

The chronological spine has three uncontroversial inflection points worth
naming in the thesis preamble: **2010** (the first recovered primary
source — Krebs already working on the data side of the centre's flagship
cell), **2017** (the iDMS-architecture cluster — eight Krebs-authored
artefacts in nine months bridging from the diagnosis of PRAESTO/KIBID's
"viele Inseln" to the vision document Shepard would later operationalise),
and **2021** (the DLRK paper — the moment the iDMS architecture moves from
prototype to the peer-reviewed shared property of a wider team).

## §3 Co-authorship network position

The bibliography surfaces several distinct collaboration clusters around
Krebs. Each is named with the primary-source evidence that establishes the
link; clusters are not islands but they have distinguishable centres of
gravity.

**The PRAESTO-to-Vision-Datenmanagement bridge (Nuschele).** Sven Nuschele is
the named author on the 2014 PRAESTO conference paper (cited in `aidocs/strategy/86`
§2; eLib [94104](https://elib.dlr.de/94104/)) and a co-author with Krebs
on the 2017-06-19 ProDES v2 iteration [@krebsProDES2017b] and the
2017-12-05 *Vision: Datenmanagement* deck [@krebsVisionDatamanagement2017].
The personal continuity is documented: Nuschele carries the PRAESTO
critique into the working group that articulates the iDMS data model
Krebs then implements. Krebs is, in this reading, the next-generation
successor to the PRAESTO-era ZLP data-management work rather than a
clean-sheet arrival.

**The HRI cluster (Schönheits).** Manfred Schönheits is first author on
the VAM-HRI 2018 workshop paper [@dlrSchoenheitsVamhri2018] and the
DLR@UBC 2017 HRI deck [@dlrSchoenheitsKrebsHriWorkshop2017], with Krebs as
co-author on both. This is the institute's HRI line — KUKA KR120 + HoloLens
+ UWB-RTLS + OPC-UA — that surfaces independently as one of the four
high-relevance threads in the institute's YouTube self-presentation
(`aidocs/strategy/102` §4). For Krebs's profile it documents an earlier
willingness to publish at international peer-reviewed venues
(ACM workshop, IEEE conference) on robotics-and-data topics adjacent to
the substrate work.

**The DLRK 2021 Shepard cluster (Willmeroth, Haase, Kaufmann, Glück,
Deden, Brandt, Mayer).** Krebs is lead author and the four Zenodo
software-citation authors of `haaseShepard2021` (Haase, Glück, Kaufmann,
Willmeroth) are co-authors. The personal continuity from CUBE iDMS
(Krebs-architected) into the 2021 Shepard team is therefore directly
attested, not merely institutional. Of these collaborators,
**Mark Willmeroth** is the most chronologically significant: the IPRO
TODO file dated July 2017 ([`aidocs/strategy/86`](86-shepard-predecessor-systems.md)
§3.5) names `mwillmeroth` working on the KIBID-adapter task five months
before the iDMS architecture decision — the same individual then appears
as a co-author on the 2021 Shepard release, closing the personal-handoff
loop from the predecessor system into the deployed one. Tobias Haase
surfaces as Krebs's workshop co-organiser as early as the SOA MES deck
[@krebsSoaMes].

**The ICRA 2024 DLR–NRC cluster.** Audet, Fortin, Côté, Monsarrat
(National Research Council Canada) with Vistein, Brandt, Krebs (DLR side)
co-authored the ICAST paper at ICRA 2024 [@audetIcra2024] on the DLR ZLP
T-AFP robotic workcell — the same FPC described in [@krebsDlrk2021]. This
is the most internationally visible peer-reviewed co-authorship in the
record and the closest the bibliography gets to a single primary-source
piece of evidence for the FPC's scientific output stream.

**The ForInfPro / live-use-case cluster (Glück, Lettowsky, Kaufmann).**
The 2026 ForInfPro deck [@krebsForInfPro2026] is co-authored by Krebs,
Glück, Lettowsky, Kaufmann — Glück and Kaufmann are also on the 2021
DLRK paper; Lettowsky is the new name. This is the **live use case team**
shaping the semantic-substrate work that closes the loop on HMC WP-2 (see
[`aidocs/strategy/90`](90-hmc-phase-2-positioning.md) and
[`aidocs/strategy/91`](91-forinfpro-semantically-driven-analytics.md)).

**The Welzmüller / PLUTO cluster (adjacent network).** Welzmüller and
Dannemann [@welzmueller2024Pluto] are the DLR Bremen authors of the
PLUTO satellite-mission RDM paper that Krebs annotated and uploaded to
the project's working memory. Krebs is not a co-author of the Welzmüller
paper. The link is institutional (Shepard is the RDM stack at DLR
Bremen as well as DLR Augsburg) rather than personal, and it documents
Shepard's cross-institute reach.

**The DLR-internal management-and-funding-context network.** The
bibliography also surfaces Krebs's network into DLR management and the
federal-funding cycle: Voggenreiter (Institute Director, named in
`project_collab_highlights.md` "the avatar moment"); the ministerial
correspondence chain Zachgo (BMFTR) → Wiestler (Helmholtz President)
[@bmftrPofV2025] that authorises the data-management mandate Krebs's
HMC Phase 2 work-package proposal lands inside [@hmcPhase2WpKrebs2025].

A separate doc dedicated to the co-authorship network as a self-standing
artefact (candidate filename `aidocs/strategy/103-research-network.md`)
would consolidate these clusters into a single citable figure. As of this
profile pass, the network is reconstructable from the bibliography but
does not yet have its own doc; this is recorded in §9 below as a
maintenance ask.

## §4 Methodological signature

What distinguishes Krebs's research practice in the primary-source
artefacts is less a single methodological commitment than a set of
discipline patterns that recur across the sixteen-year arc:

**Honest-reconstruction discipline.** The personally-authored
2010–2017 PPTX series is, in the language of `aidocs/strategy/86`,
"the kind of evidence base most institute projects don't preserve":
Krebs kept the working decks, the iteration drafts, and the dated
versions of the architecture diagrams from his own desk, and on
2026-05-23 made them available as primary sources for the
reconstruction of the iDMS / Shepard lineage. The willingness to
have the working trail audited — including the SOA MES deck where
his own pencil notation reads "CUBE … KiBiD?" with the question
mark intact — is the methodological precondition for any honest
reconstruction-of-field chapter.

**Shape-first / plugin-first architectural posture.** Krebs's
architectural decisions in 2017 (iDMS as graph-organised provenance +
substrate-split + modular components in Docker [@krebsIdms2020])
and in 2026 (Shepard's plugin SPI as the structural reply to the
KIBID-vendor-abandonment lesson; the federated-Shepard sketch's
explicit "Path A first, Path B later" trade-off [@krebsFederationSketches2026])
share a posture: the shape of the substrate is decided before the
content; new capability arrives through declared seams rather than
through forks. The 2026 design-doc rule that *the default question
is "should this be a plugin?"* is the contemporary articulation of
the same posture the 2017 iDMS architecture already expressed.

**Snapshot-bracketed iterative shaping ("dataset forging").** The
working term *dataset forging* (`project_dataset_forging.md`) was
coined by Krebs mid-conversation 2026-05-22 to name a cycle the fork
had already shipped without naming: successive shaping passes
bracketed by COW snapshots, each pass intentional, each pass
recoverable. The methodological commitment behind the term — that
the snapshot chain *is* the provenance, that *"mutation will probably
involve multiple mutations and snapshot until the shape is
juuuuuuuust right"* (`project_collab_highlights.md` 2026-05-23) — is
visible across the IPRO-era artefacts (the dated revision sequence
of the 2010 MFZ-Steuerungskonzept deck) as well as in the present
project's snapshot-boundary rule.

**Reflexivity as research method.** The most thesis-load-bearing
methodological signature is also the most recent: Krebs's GenKI deck
[@krebsGenKi2026] closes on an explicit AI-Disclosure slide naming
SAIA / GWDG (DLR-compliant cloud), MCP-server tools (Papersearch,
Docling, web-search, image-tools), and the *Decide → Research →
Convert → Write → Export* workflow. The reflexivity-audit chapter
at [`aidocs/strategy/89`](89-genai-methodology-and-reflexivity.md)
returns *alignment, not divergence* between the stated position and
the observed practice of this thesis project across six axes
(transparent disclosure, choice of substrate, reproducibility,
human oversight, data provenance, workflow shape). The
three-way alignment table at [`aidocs/strategy/93`](93-management-context-and-compliance.md)
§2 extends this to institutional policy (Helmholtz
Handlungsempfehlungen + DLR Leitbild + Leitfaden); two honest
divergences surface (European-hosting of the authoring AI; AI-inference
energy accounting), both disclosed.

**AI-collaborative development discipline as research output.** A
distinctive feature of Krebs's practice on this project is that the
disciplines that pair with AI-collaborative work — memory rules,
persona-audit pattern, snapshot-boundary rule, dataset-forging cycle,
clarify-first agent protocol, lens-citation requirement — are not
merely development conventions but are recorded as *methodological
contributions* in their own right (`project_collab_highlights.md`,
the `feedback_*.md` memory family). The thesis methodology chapter
described at [`aidocs/strategy/89`](89-genai-methodology-and-reflexivity.md)
§7 is "a contribution: a worked example of how a research project
can use generative AI in the open without ceasing to be scientific."

## §5 Publication record

The publication record is split by category as recorded in
[`docs/_data/references.bib`](../../docs/_data/references.bib). The
**`papers`** category in the bib is the closest proxy for "peer-reviewed";
**`dlr_internal`** entries are working documents, decks, and reports
preserved as primary sources for this thesis substrate but not themselves
peer-reviewed; **`peer_reviewed`** is used selectively where the venue is
ACM/IEEE-class workshop or conference.

### 5.1 Peer-reviewed

- **Krebs, F., Willmeroth, M., Haase, T., Kaufmann, P., Glück, R., Deden,
  D., Brandt, L., Mayer, M. (2021).** *Systematische Erfassung, Verwaltung
  und Nutzung von Daten aus Experimenten.* Deutscher Luft- und Raumfahrtkongress
  (DLRK 2021), Bremen. [@krebsDlrk2021]
  *Load-bearing peer-reviewed publication of record for Shepard's architecture.*

- **Audet, J.-M., Fortin, Y., Côté, G., Vistein, M., Brandt, L., Krebs, F.,
  Monsarrat, B. (2024).** *Iterative Robot Calibration with Accuracy
  Saturation Termination and Application for AFP Processing of a
  Thermoplastic Aerostructure with Real-time Elastic Path Compensation.*
  ICRA 2024 (IEEE), manuscript 2640. [@audetIcra2024]

- **Schönheits, M. & Krebs, F. (2018).** *Embedding AR in Industrial HRI
  Applications.* VAM-HRI 2018 workshop, ACM, Chicago. [@dlrSchoenheitsVamhri2018]
  *Listed in the bib under category `peer_reviewed`.*

### 5.2 DLR-internal documents

The bibliography preserves 26 Krebs-authored or Krebs-co-authored
DLR-internal working documents (decks, notes, reports, sketches) spanning
2010–2026. These are not peer-reviewed; they are cited as primary sources
for the thesis's continuity-of-field, methodology, and institutional-context
chapters. The full chronology is given in §2 above. Aggregated highlights:

- **Eight 2017 documents** that compose the iDMS-architecture cluster —
  ProDES v1/v2, Status Prozessleitsystem, NSR Orchestrator Konzept,
  NSR Architektur, Vision Datenmanagement, Industry 4.0 defined,
  SOA MES.
- **The 2020-11-04 iDMS final presentation** — sole-author
  Krebs F.; architectural ancestor of Shepard with direct visual
  continuity in the data model.
- **Seven 2025 working documents** — Inventur (2025-04-18, the
  capability inventory underlying the rollout plan), HMC Phase 2 WPs
  (the funding-cycle proposal), KUKA CNC (the controller-paradigm
  audit), Metrology + Spatial Analyzer + I4.0 (IREC results),
  NFDI4Ing intro, ODIX Milestone 2, JAXA briefing.
- **Six 2026 documents** — GenKI deck (the methodological deck),
  ForInfPro analytics deck (the live use case), Humanoids 2026
  market survey, DIVA V01 skizze (the LuFo VII-2 idea), the two
  federation sketches.

### 5.3 Talks, presentations, workshops

- **2013** — Public-facing institutional exposition of the MFZ in
  *DLR magazin* #140 (co-authored with B. Jung) [@krebsJungDlrMagazin140].
- **2017-12-06–08** — DLR@UBC workshop (University of British
  Columbia, Vancouver), HRI demo deck co-authored with Schönheits
  [@dlrSchoenheitsKrebsHriWorkshop2017].
- **2025-03-17** — JAXA briefing on Manufacturing-X / Aerospace-X
  [@krebsAerospaceXJaxa2025].
- Multiple internal DLR positioning talks 2025–2026 (NFDI4Ing intro
  2025-11-03; HMC Phase 2 WPs; GenKI tutorial; ForInfPro).

### 5.4 Shipped software artefacts and standing online presence

- **`shepard` (noheton fork)** — Krebs is the named *Verantwortliche
  Person* / Software Maintainer [@shepardFork]. <https://github.com/noheton/shepard>
- **`f(ai)²r` — FAIR-for-AI Research vocabulary** — open specification +
  software [@fair2rVocab]. <https://github.com/noheton/f-ai-r>
- **Email of record** — `florian.krebs@dlr.de` (named contact address on
  [@krebsDlrk2021] and used as the affiliation handle on the 2010 PPTX series).

### 5.5 Adjacent network — papers Krebs annotated rather than authored

The bibliography includes one important *adjacent* publication: the
Welzmüller & Dannemann PLUTO RDM paper [@welzmueller2024Pluto], DLR eLib
[215120](https://elib.dlr.de/215120/), which Krebs did not co-author but
which describes Shepard's use at DLR Bremen for satellite-mission data.
Krebs uploaded the corresponding annotated conference poster
([`project_thesis_idea.md`](../../project_thesis_idea.md) inventory entry,
`Tag_der_Forschungsdaten_Poster_Anmerkungen_FK.pptx`) to the project's
primary-source archive on 2026-05-23.

## §6 Active projects and institutional roles

The bibliography and the strategy chapters at
[`aidocs/strategy/87`](87-dlr-zlp-positioning.md),
[`aidocs/strategy/90`](90-hmc-phase-2-positioning.md),
[`aidocs/strategy/91`](91-forinfpro-semantically-driven-analytics.md),
[`aidocs/strategy/100`](100-shepard-bt-zlp-rollout-plan.md), and
[`aidocs/strategy/101`](101-diva-project-context.md) document Krebs's
current project portfolio:

- **MFFD AFP — the live use case.** Live import pipeline against the
  DLR cube3 instance of Shepard; the v15.x importer-script evolution arc
  recorded in `project_collab_highlights.md`; the rollout-plan Wave 0
  per [`aidocs/strategy/100`](100-shepard-bt-zlp-rollout-plan.md) §2.
- **ForInfPro — the analytics use case.** Co-author on the 2026 deck;
  the live composite-infusion process-control use case driving the
  semantic-features work-stream per [`aidocs/strategy/91`](91-forinfpro-semantically-driven-analytics.md).
- **HMC Phase 2 — three Shepard-bearing work-packages.** Krebs-authored
  Phase 2 proposal [@hmcPhase2WpKrebs2025]: WP-1 (export/import + PID),
  WP-2 (semantic-features extensions), WP-3 (cross-DLR DM interoperability).
- **NFDI4Ing Phase 2 (2025–2030).** Krebs is the named author of the
  DLR ZLP positioning briefing [@nfdi4ingIntroKrebs2025]; DLR is a
  Measure F-1 + F-2 partner.
- **DIVA — Drone Integration via AAS (V01 skizze).** Krebs is the
  sole author of the LuFo VII-2 idea skizze [@krebsDIVA2026] naming
  BT/ZLP's contribution as the Shepard AAS-Repository for a
  five-DLR-institute consortium.
- **ODIX — Milestone 2 (2025-09-30).** Krebs-authored cross-cutting
  milestone report [@odixMilestone2_2025]; HAP 2 (storage management
  with metadata schemas) is the Krebs-group deliverable.
- **The Shepard rollout across the BT/ZLP cell portfolio** — the
  rollout plan at [`aidocs/strategy/100`](100-shepard-bt-zlp-rollout-plan.md)
  is built from the 2025-04-18 Inventur and the 2025-02-21 vision slide,
  both Krebs-authored.
- **Institutional roles per [`aidocs/strategy/93`](93-management-context-and-compliance.md)
  §3 table:** Krebs is named as the *Verantwortliche Person für die
  Software (Software Maintainer)* for the noheton fork; the *Software-Engineering-Ansprechperson*
  (SEA) for ZLP is recorded as **TBD — should be named explicitly (likely
  Krebs or a ZLP DM colleague)**.

A standing in-conversation reference point recorded in the
collaboration highlights is the institutional direct-line relationship:
Krebs reports through ZLP institute leadership to **Prof. Heinz
Voggenreiter** as Institute Director. The "avatar moment"
(`project_collab_highlights.md` 2026-05-20) is preserved as a single
unscripted illustration of the boundary the thesis's reflexivity chapter
investigates.

## §7 Research questions Krebs has named

Krebs's research questions are not stated in canonical "RQ1 / RQ2"
academic form in the decks surveyed. They are visible *as the framings
the decks build against*. The list below extracts them in the
chronological order they surface, citing the source artefact for each:

1. **The PRAESTO misfit question (2017, restated 2026-05-23).** *Can
   the centre's data-management need be served by a commercial
   spatial-data-fusion analyser scoped to inspection workflows?* The
   ProDES v1 deck's *"viele Inseln"* diagnosis [@krebsProDES2017a]
   is the recorded negative answer; the PRAESTO input deck
   [@krebsInputPraesto] is the evidence base for the misfit.
2. **The KIBID-sustainability question (2017–2020).** *What survives
   when the vendor-supplied substrate loses vendor support?* The
   lesson Krebs drew from KIBID — preserved in his
   contemporaneous DLR-side adapter code header and revisited in
   `aidocs/strategy/86` §3.5 — shaped the architectural decision to
   build iDMS (and later Shepard) as an internal substrate the
   institute could maintain independently of external commercial support.
3. **The DIKW-context-capture question (2026, ForInfPro deck).** *Can
   a research-data substrate climb the Data → Information → Knowledge
   → Wisdom ladder by making context a first-class object alongside
   the raw sensor traces?* The deck's slide 7 contextualisation
   diagram and slide 9 dry-spot reasoning chain are the architectural
   answer [@krebsForInfPro2026]; the deck's mid-term goal of
   *"enabling data-based process control"* is the live-loop variant.
4. **The ontology-annotation-usability question (HMC Phase 2 WP-2).**
   *Why is ontology-based annotation in Shepard not yet usable
   enough?* The deck states the question and the work-package commits
   to delivering the answer [@hmcPhase2WpKrebs2025]; the
   M1-VIEWS-AS-SHAPES wave is the substrate-side response.
5. **The cross-substrate federation question (2025–2026, JAXA
   briefing + federation sketches).** *What does a research-data
   substrate need to look like to participate in Manufacturing-X /
   Aerospace-X / Catena-X without becoming an EDC client?* The
   "Path A first, Path B later" trade-off in the 2026-05-23 sketch
   [@krebsFederationSketches2026] is the architectural commitment;
   the JAXA briefing [@krebsAerospaceXJaxa2025] sets the
   international scope.
6. **The AI-as-research-method question (2026, GenKI deck).** *How
   can a research project use generative AI assistance in the open
   without ceasing to be scientific?* The deck's seven-step
   exposition + four-risk slide + AI-Disclosure slide
   [@krebsGenKi2026] is the public methodological position; the
   reflexivity audit at [`aidocs/strategy/89`](89-genai-methodology-and-reflexivity.md)
   is the substrate-side evaluation.

The thesis defence will need *the candidate's stated research
questions, and how the body of work answers them*. The six items above
are reconstruction-by-citation, not a statement Krebs has made in
academic form; a thesis-introduction chapter would refine them into a
canonical RQ shape with the candidate's input.

## §8 Thesis-readiness assessment

A short, honest evaluation, holding the thesis-type ambiguity open. The
`project_thesis_idea.md` note records *"Thesis type: PhD (full doctoral)?
Habilitation? Master's? Different framings"* as an explicitly open
question. The readiness picture below is staged accordingly.

**Strongest dimensions** (any thesis register):

- A peer-reviewed publication of record for the substrate's
  architecture, with Krebs as lead author [@krebsDlrk2021]; one
  further peer-reviewed conference paper with Krebs as co-author
  on a directly-related FPC topic [@audetIcra2024]; one ACM
  workshop paper from 2018 [@dlrSchoenheitsVamhri2018].
- A sixteen-year primary-source trail (2010–2026) preserved and
  available for citation, with the load-bearing 2017-iDMS-architecture
  cluster reconstructed in detail at
  [`aidocs/strategy/86`](86-shepard-predecessor-systems.md).
- A deployed software artefact [@shepardFork] under Krebs's
  maintainership, with live production use at MFFD AFP.
- A publicly defended methodological position on AI use in science
  [@krebsGenKi2026] consistent with the observed practice of the
  thesis project itself (`aidocs/strategy/89` reflexivity-audit
  finding: alignment).
- Funding-cycle alignment at three layers — federal-ministerial
  PoF V mandate [@bmftrPofV2025], HMC Phase 2 work-packages
  [@hmcPhase2WpKrebs2025], NFDI4Ing Phase 2 measures
  [@nfdi4ingIntroKrebs2025] — meaning the substrate's continued
  development is *on-policy* at all three levels above the centre.

**Weakest dimensions** (would benefit from more material before any
thesis-type-defence):

- The peer-reviewed publication count is **three** (one lead-author,
  two co-author). For a German PhD this is at the lower end of
  workable; for a Habilitation it is sparse. The DLR-internal
  document set is large but is not a substitute.
- The ICRA 2024 paper is the only internationally-visible IEEE/ACM
  publication of the last five years. The DLRK 2021 paper is in
  German.
- The MFFD case-study evidence (§6 of the thesis outline) is
  *live but not yet captured-as-published-result*. The Q1 anomaly
  was synthetic [@krebsForInfPro2026 context;
  `project_collab_highlights.md` 2026-05-22]; the real-data demo
  is empirical-and-ongoing. The case-study chapter therefore awaits
  the data-arrival arc to complete.
- A standing online presence outside `florian.krebs@dlr.de` (the
  DLR institute page, an ORCID identifier, a Google Scholar
  profile) is not yet in our records.

**Honest mid-confidence verdict.**

- For a **PhD**, the architectural-substrate work alone is plausibly
  sufficient if framed correctly: the
  [`project_thesis_idea.md`](../../project_thesis_idea.md) outline §1–§10
  is a defensible chapter structure, the primary sources for
  continuity-of-field are unusually well-preserved, and the
  AI-collaborative method has a real methodological-contribution
  shape. The gating constraints are publication volume and the MFFD
  case-study completion.
- For a **Habilitation**, the publication record is currently thin
  and would benefit from two or three additional peer-reviewed
  papers (one each on the ontology-driven substrate, the federation
  story, and the AI-collaborative method) before a serious defence.
- For a **Master's**, the work substantially exceeds the
  expected scope.

The strongest current claim the profile would let an external
reader make: **Krebs has built a coherent sixteen-year body of work
around the data-substrate problem of full-scale composite
manufacturing, has shipped a deployed, peer-reviewed-architecture,
funding-aligned open-source substrate as its current expression, and
has publicly defended a reproducible methodological posture on
AI-collaborative research that the thesis itself realises.** That
claim is defensible against the bibliography. The thesis-type
question is the one a doctoral committee + supervisor will help
calibrate, not a librarian.

## §9 Open profile gaps and maintenance asks

Per `feedback_ask_for_artefacts.md`, the items below are low-friction
asks that would close specific evidence gaps in this profile. They are
not blockers for the next pass; they would each strengthen one section
above. The librarian (this AI assistant) will not invent answers.

**Items the user could supply on request:**

- **ORCID identifier** — none currently in our records; would
  improve §5 cross-referencing and the online-presence sub-section.
- **Google Scholar profile URL** — same effect; also a useful
  citation-count check on the peer-reviewed list of §5.1.
- **DLR institute-page URL** with Krebs's contact / role line — the
  external-visibility counterpart to the `aidocs/strategy/87`
  internal-positioning material.
- **First DLR employment date / contractual position** — *omitted by
  default per the task's reflexivity-and-register guidance; include
  only if the author explicitly chooses to.*
- **A profile photograph** — needed only if the thesis register
  requires an author photo (PhD-thesis-front-matter convention
  varies by faculty); the avatar-moment in
  `project_collab_highlights.md` 2026-05-20 is the standing reminder.

**Bibliography hygiene asks** (recorded for the next maintained-profile
pass):

- **`krebsForInfProWorkshop2026`** — referenced in
  `project_thesis_idea.md` inventory; not yet in
  [`docs/_data/references.bib`](../../docs/_data/references.bib).
  Bibliography entry needed.
- **`krebsOdix2026`** — same status. (Separate from
  `odixMilestone2_2025` which is in the bib.)
- **`krebsRapid2025`** — referenced in the inventory; bib entry
  needed.
- **`aerospaceXProject2024`** — referenced as the AerospaceX project
  deck citation key in `project_thesis_idea.md` table; the actual
  resolved bib entry uses `weberAerospaceX2025` (Holger Weber as
  creator). The inventory key needs reconciliation with the
  resolved bib key.
- **`welzmuellerPlutoPoster2025`** — referenced in the inventory;
  bib entry needed. (The `welzmueller2024Pluto` eLib entry is in
  the bib; the annotated poster is a separate artefact.)
- **`krebsMetrology2025`** — referenced in the inventory; the actual
  resolved key in the bib is `krebsMetrologyI42025`. Inventory
  needs the canonical-key update.

**Sibling-doc asks** (sequencing observation):

- The task instructions referenced two sibling docs by name —
  `aidocs/strategy/102-thesis-timeline-of-ideas.md` and
  `aidocs/strategy/103-research-network.md` — neither of which
  exists at the time of this profile pass. The actual
  `aidocs/strategy/102` is `institute-youtube-profile.md`. The
  natural next library-maintenance step is to author both **102b
  (thesis-timeline-of-ideas)** and **103 (research-network)** as
  standalone docs that the next pass of this profile can
  cross-reference. The timeline doc would consolidate the §2
  table above into a citable figure; the network doc would do the
  same for §3.

**External-source asks** (would extend §5.5 *adjacent network*):

- PRAESTO design / evaluation documentation, if any internal copy
  remains.
- KIBID design doc or vendor-relationship memo explaining why
  support stopped (would close `aidocs/strategy/86` §3.5).
- IPRO project final report or summary.
- NFDI4Ing Phase 2 measure-F-1 + measure-F-2 official descriptors
  from `nfdi4ing.de`.
- HMC Phase 2 work-package proposal in its finally-accepted form
  (currently only the preliminary slide deck is in hand).

## §10 References and reflexivity footnote

All citations resolve via [`docs/_data/references.bib`](../../docs/_data/references.bib);
the project's bibliography is also browsable at
[`/bibliography`](/bibliography) on the live Pages site. The primary
companions to this profile within the strategy library are
[`aidocs/strategy/86`](86-shepard-predecessor-systems.md) (continuity
of field, the 2010–2021 arc), [`aidocs/strategy/87`](87-dlr-zlp-positioning.md)
(institutional positioning of ZLP Augsburg, with Krebs as named author of
several load-bearing institutional artefacts cited there), and
[`aidocs/strategy/89`](89-genai-methodology-and-reflexivity.md) (the
reflexivity audit that this profile depends on for the methodological
section). The chronology backbone of §2 is `aidocs/strategy/86` §3.6
extended forward to 2026; the network reconstruction of §3 is built
directly from the bibliography's `author = {…Krebs…}` filter and
follow-the-co-authors traversal.

**Reflexivity footnote** (per the task's standing requirement). This
profile was produced under the project's AI-collaborative practice
(`aidocs/strategy/89`). Its `fair2r:modeOfProduction` value is
**`ai`** — the prose was drafted by an AI assistant (Claude Opus 4.7,
1M-context) operating against the primary-source library Krebs
maintained and the strategy docs the project's persona-board has
authored over the preceding session arc. The author-subject is the
human at the centre of the profile; the AI is the librarian. The
profile's existence as an AI-authored document about the human author
is a methodological feature, not a quirk; the
[`aidocs/strategy/89`](89-genai-methodology-and-reflexivity.md) audit
returns *alignment* between the stated and observed positions on this
exact production mode. The author-subject is expected to review and
amend before any external use.
