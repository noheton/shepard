---
title: Timeline of ideas across Shepard's intellectual trajectory
stage: feature-defined
last-stage-change: 2026-05-23
last-pass-by-librarian: 2026-05-23
audience: [thesis-substrate, contributor, strategy]
---

# Timeline of ideas across Shepard's intellectual trajectory

*First dispatch of the **Thesis Librarian** standing role
(per `feedback_thesis_librarian_standing.md`), 2026-05-23. The
substrate of this synthesis is the ~50 artefacts uploaded by the
author on 2026-05-23 plus the integrated library docs at
`aidocs/strategy/86–101`, `aidocs/98`, `aidocs/integrations/96`,
and the bibliography backbone at `docs/_data/references.bib`.*

## §1. Scope and how to read this doc

This document is the chronological backbone for the §2 *Continuity
of field* and §5 *Method / reflexivity* chapters of the thesis
outline in `project_thesis_idea.md`. It synthesises — it does not
re-list — the artefacts that anchor Shepard's intellectual
trajectory from **2010 (the MFZ-Anlage control-concept deck)** to
**2026 (today's DLR Datenstrategie v60 draft and this librarian
pass)**.

The synthesis serves four readers:

- **The thesis-defence examiner** who needs the citation chain
  from PRAESTO (2014) → KIBID (2016) → iDMS (2017–2020) →
  Shepard (2021+) → Shepard-fork (2024+) without speculation.
- **The funding-cycle reviewer** (HMC mid-cycle; NFDI4Ing Phase 2;
  PoF V) who needs to see the architectural commitments arriving
  at the regulatory ask, not the other way round.
- **The new contributor** orienting themselves to "what came
  before and why this codebase looks the way it does."
- **The librarian-future-self** running the next maintenance pass
  and asking which threads stalled, which converged, and which
  bib entries went stale.

**Reflexivity note (per `feedback_thesis_librarian_standing.md`
and the f(ai)²r AI-provenance discipline).** This doc was
produced by the Thesis Librarian agent — an AI dispatch — under
direct user instruction. Its `fair2r:modeOfProduction` is `ai`;
its human-acceptance grade pends Florian's review. Every claim is
sourced; every date is grounded either in the artefact's filename
date, its PPTX/PDF metadata, or a library doc that already carries
the primary-source citation. Where dates conflict (template-
inheritance creation-date vs. filename date), the **filename or
substantive-revision date wins** per §6 below.

## §2. The chronological table

Sorted oldest to newest. Author column: **FK** = Florian Krebs;
**SN** = Stefan Nuschele (PRAESTO 2014 paper lead); **MS** =
Manfred Schönheits (ZLP colleague); **HW** = Holger Weber
(AerospaceX deck); **Other** as labelled. **Bib key** is the
canonical citation handle from `docs/_data/references.bib` or
**(MISSING)** if the entry needs to be added.

| Date | Artefact / bib key | Author(s) | Idea / claim | Predecessor idea | Successor idea | Library doc |
|------|--------------------|-----------|--------------|------------------|----------------|-------------|
| 2009 | EUROP *Robotic Visions to 2020 and Beyond — Strategic Research Agenda* (cited in `krebsMfzSteuerung2010`) | EUROP consortium | Strategic-research-agenda framing for European robotics | — | grounds the 2010 MFZ deck's design intent | `aidocs/strategy/86 §1.5` |
| **2010-10-05** | `krebsMfzSteuerung2010` (filename `9acbb990-20101005_Steuerungskonzept_der_MFZAnlage.ppt`, 21 internal revisions Sept–Oct 2010) | **FK** | Control-cell concept for the Multifunktionale Fertigungszelle. Already articulates: avoidance of island solutions; modular incremental architecture; generic measurement-data interface; automatic process-data documentation; central DB storage; replay capability; calibration-deviation detection; open interfaces (AutomationML/XML); NDT-framework integration. | (none — earliest recovered FK primary source) | iDMS substrate split + plugin SPI seam + PROV1a Activity stream (decade-plus later) | `aidocs/strategy/86 §1.5` (14-row mapping table) |
| 2010-11-16 | `krebsRobotikThemen2010` (filename matches; 29 words, 2 revisions; slot-deck) | **FK** | Themes for robotics in composite manufacturing — title-and-image deck, single MFZ photo. | 2010-10-05 MFZ deck | proto-IPRO subject matter | `aidocs/strategy/86 §1.5` |
| 2012-07-25 | `wantedFlo2012` (35-word "Wanted" office-door poster) | **FK** | Self-aware satire on PI/collaborator asymmetry. Methodologically light; reflexivity-chapter footnote calibration on the author's working voice. | — | 2026 reflexivity audit (`aidocs/strategy/89 §8.5`) | `aidocs/strategy/89 §8.5` |
| **2013-07-09** | SOA_MES.pptx (Krebs; created 2013, last-modified 2016-10-26) — **(MISSING from bib)** | **FK** | Service-Oriented-Architecture-and-MES framing — proto-MES thinking at ZLP, three years before KIBID lands as the timeseries store. The substrate-as-MES question is already in the author's working drafts. | 2010 MFZ control concept | 2017 ProDES + Prozessleitsystem proposals | (no library doc yet — candidate §3.6 extension to `aidocs/strategy/86`) |
| ~2014 (DLRK paper deposit) | `nuschelePraesto2014paper` + `nuschelePraesto2014talk` (DLRK 2014, Augsburg, eLib 94077/94078) | SN + 5 co-authors | PRAESTO commercial-product paper — *Datenbank PRAESTO: Speicherung von CFK-Forschungsdaten auf Fertigungsniveau*. Only bibliographic metadata is public. | (commercial product, supplier later identified as Kisters AG) | KIBID assumes the substrate role at ZLP; PRAESTO continues as an internal-network DNS (hostname `kibid-proxy.praesto.lo` observed 2017) | `aidocs/strategy/86 §2` |
| ~2016-11-24 | `Input_Florian_PRAESTO.pptx` (Krebs last-modified 2016-11-24; per PPTX core.xml) | **FK** (annotator over Nemes-authored template) | Scope-stocktake of PRAESTO: names supplier **Kisters AG**; "DLR seit 2010"; architecture scoped to *Inspektionsprozess* (inspection-data fusion, not end-to-end process). | PRAESTO 2014 paper | 2017 build-internally decision → ProDES → NSR → iDMS | `aidocs/strategy/86 §2.1` |
| c. 2016 | KIBID `kibidPy` SDK (vendor: `gruenefeld`, Created 2016-09-08 per docstring; UNAS code drop) | Vendor + **FK** (DLR-side adapters) | Tagged timeseries entity; REST+Python SDK; OPC UA shop-floor bridge; Keycloak realm `kibid`; per-user authentication. Shape that survives into Shepard's `TimeseriesContainer`. | PRAESTO (substrate ancestor) | iDMS slide-15 stack; Shepard `shepard-timeseries-collector` | `aidocs/strategy/86 §3` |
| **2017-04-25** | `20170425_Prozessdatenerfassungssystem_ProDES.pptx` (filename) | **FK** | First ProDES (Prozessdatenerfassungssystem) concept — end-to-end process-data acquisition; the umbrella question PRAESTO didn't answer. | PRAESTO scope (inspection-only); 2010 design intent | second ProDES iteration 2017-06; NSR proposals | (no library doc yet — candidate §3.6 extension to `aidocs/strategy/86`) |
| **2017-06-19** | `20170619_Prozessdatenerfassungssystem_ProDES.pptx` (filename) | **FK** | ProDES revision — refined end-to-end acquisition spec. | 2017-04-25 ProDES v1 | Prozessleitsystem 2017-07; NSR Orchestrator 2017-09 | (candidate §3.6 extension) |
| **2017-07-18** | `20170718_Status_Prozessleitsystem.pptx` (filename) | **FK** | Process-control-system status report — substrate scope widens from acquisition to control. | ProDES (acquisition only) | NSR Orchestrator + Architektur (Q4 2017) | (candidate §3.6 extension) |
| 2017-07-21 | IPRO Prozessablauf (XLSX, `20170721_Prozessablauf.xlsx`) | (IPRO operations team) | CFRP layup process sequence (FWD/AFT/LE zones; manual stacking + drape-cylinder + stapling tool + handling tool). | KIBID timeseries layer (active substrate) | the 2017-11-06 IPRO Grafana dashboard | `aidocs/strategy/86 §4.4` |
| **2017-09-14** | `20170914_NSR_Orchestrator_Konzept.pptx` (filename) | **FK** | NSR (Next Step / Neue Steuerungs-Rolle?) Orchestrator concept — cross-cell control orchestration spec. | Prozessleitsystem 2017-07 | NSR Architektur 2017-12-04 | (candidate §3.6 extension) |
| **2017-10-10** | `20171010_Industry_4.0_defined.pptx` (filename) | **FK** | Author's articulation of Industry 4.0 at ZLP — orienting frame for the orchestration work. | 2017 ProDES / Prozessleitsystem | 2017-12 NSR docs + Vision Datenmanagement; later AerospaceX I4.0-paradigm-shift framing | (candidate §3.6 extension) |
| 2017-11-06 | IPRO Grafana dashboard screenshot, 14:50–15:15 UTC (PNG, captured live) | (IPRO operations team) | Live TPZ.R10 KUKA telemetry — six-axis joints, TCP XYZ + ABC Euler, tool force ±500N, discrete I/O (`ENABLE_HOLD_*`, `OPEN_TOOLCHANGER`, `ENABLE_STACK_GRIPPER`), KRC `ROBTIMER=1211982238`. KIBID as the timeseries store; Grafana as the viewer. | KIBID 2016 ingest | iDMS slide-12 data-pipeline-model + Shepard `TimeseriesContainer` | `aidocs/strategy/86 §4.4` (named thesis-figure candidate) |
| **2017-12-04** | `20171204_NSR_Architektur.pptx` (filename) | **FK** | NSR Architektur deck — control-architecture finalisation for ZLP cells. | NSR Orchestrator 2017-09 | iDMS architecture overview slide 14 (2020-11-04) | (candidate §3.6 extension) |
| **2017-12-05** | `20171205_Vision_Datenmanagement.pptx` (filename) | **FK** | Vision Datenmanagement — the **strategic data-management vision deck** that frames what iDMS will become. | 2017 ProDES + Prozessleitsystem + NSR arc | 2020-11-04 iDMS final presentation | (candidate §3.6 extension — load-bearing thesis source) |
| 2017-12-05 | `20171205_Workshop_HRI_Demo.pptx` | **MS** (Schönheits) | Human-Robot Interaction workshop demo — parallel ZLP track. | — | VAMHRI 2018 paper (Schönheits + Krebs co-authored) | (companion, not predecessor — separate research line) |
| 2018-02-10 | `Embedding AR In Industrial HRI Applications` (VAMHRI 2018 paper PDF) | MS + **FK** | AR-in-industrial-HRI peer-reviewed paper. | 2017-12 workshop demo | DLR-internal HRI research line continues parallel to Shepard | (not in library — peer-reviewed AR work, sibling to Shepard substrate) |
| ~2017–2020 | CUBE iDMS prototype (8 UNAS components: examples / frontend / kafka-bridge / oven-importer / pandora-converter / rce / hotstuff / utils) | **FK** (frontend + RCE + exporters) | Project / Experiment / Step entity model; Mongo + Influx references; Python+Java+C+++Jupyter SDKs; PROV via `provtoolutils`; Kafka bridge (experimental); RCE workflow integration. | 2017-12 Vision Datenmanagement | 2020-11-04 final presentation; 2021 DLRK Shepard paper (Krebs et al.) | `aidocs/strategy/86 §4` |
| **2020-11-04** | `krebsIdms2020` — *iDMS final presentation* (35 slides, "Datenmanagement > Florian Krebs 12.12.2017" footer) | **FK** | Slides 10/12/14/15/16/17/18: "One data base – many digital twins"; three input modes (manual/scripted/auto); CUBE iDMS Architecture Overview — *graph for relations + provenance, separate substrates by data type, no one-size-fits-all*; Neo4j+InfluxDB+MongoDB stack; agile-Neo4j-justification; Project→Experiment→Step→Artifact; Keycloak realm `kibid` carried forward. | KIBID + 2017 design arc | 2021 DLRK Shepard paper; 2021 Zenodo Shepard release | `aidocs/strategy/86 §4` (load-bearing primary source) |
| **2021** (DLRK 2021) | **(MISSING from bib)** — *Systematische Erfassung, Verwaltung und Nutzung von Daten aus Experimenten* (DLRK 2021; 6-page PDF; LaTeX/MiKTeX; PDF creation 2021-07-30; uploaded as `6db8bac2-DLRK_Systematische_Erfassung...pdf`) | **FK** (lead), Willmeroth M., Haase T., Kaufmann P., Glück R., Deden D., Brandt L., Mayer M. — DLR BT/ZLP Augsburg | **The formal Shepard publication.** Names Shepard verbatim ("Das am DLR entwickelte integrierte Datenmanagementsystem 'shepard'") and uses **IDMS** abbreviation throughout (architectural lineage made citable in the public record). AFP thermoplastic case study. | iDMS 2020-11-04 final presentation | Zenodo software record (`haaseShepard2021`, also 2021-07); fork-side development | **NOT YET CITED in any library doc** — see §6 CRITICAL drift |
| 2021-07 | `haaseShepard2021` — Zenodo software record (DOI 10.5281/zenodo.5091604; Haase, Glück, Kaufmann, Willmeroth) | Haase + 3 co-authors (note: Krebs not on Zenodo, but is DLRK-paper lead) | Apache-2.0 Shepard 1.0.0 — the open-source release that lands the iDMS architecture in public. | iDMS 2020-11-04 + DLRK 2021 paper | external DLR adoption; this fork (2024+) | `aidocs/strategy/86 §5` (lineage table) |
| **2022-11-04** | `Federated_shepard_data_space.pptx` (Krebs; created+modified same day) — **(MISSING from bib)** | **FK** | **Federation thinking on paper three years before the 2025 BT inventory and the Krebs-federation-sketches of 2026-05-23 (sketches A and B).** Federated-Shepard-as-dataspace is a 2022 working idea, not a 2026 reaction to Manufacturing-X / NFDI4Ing pressure. | iDMS digital-twin framing (slide 10, 2020) | 2025 BT/ZLP inventory slide 23; 2026 federation sketches; HMC WP-3 | (candidate addition to `aidocs/strategy/94` §1 — antedates the Manufacturing-X external framing) |
| 2024 | `bergsAerospaceXImec2024` (Bergs + Ganser, IMEC 2024) | Bergs + Ganser | Aerospace-X multi-tier traceability framing; the E2E-quality worked example. | Catena-X 2022 reference architecture | 2024+ AerospaceX BDLI deck (Weber et al.) | `aidocs/strategy/92 §2.4` |
| 2024 (ICRA 2024) | `Audet et al. — Iterative Robot Calibration … AFP Processing with Real-time Elastic Path Compensation` (ICRA 2024; submitted 2023-09-15) — **(MISSING from bib; cite to back IREC claims)** | Audet, Fortin, Côté, Vistein, Brandt, **FK**, Monsarrat | **The published IREC paper** that backs the 89.30% / 70.19% TCP-deviation-reduction claims in metrology doc 96. Co-authored by Krebs. | metrology stack at ZLP (Spatial Analyzer + Leica AT 901 LR + T-MAC) | `shepard-plugin-metrology` design seed; M1-wave Trace3D acceptance test | `aidocs/integrations/96` §3 (claims cited; paper not yet bib-keyed) |
| 2024-09-18 | `helmholtzKiEmpfehlungen2024` (Helmholtz Mitgliederversammlung; v1.0) | Helmholtz | Five-risk-category framework for AI use across all Helmholtz centres; authorship reserved for natural persons. | OECD AI Principles 2019/2024; EU AI Act draft trajectory | DLR Leitfaden 2024-12; thesis §5 reflexivity audit | `aidocs/strategy/93 §2` + §6.A |
| 2024-12-17 | `dlrKiLeitfaden2024` (Köster + Raulf; IT-LA review; 594. VS approval) | F. Köster + A. Raulf (DLR) | DLR operational AI-use checklist; seven value-pairs (Menschenzentrierung, Fairness, Transparenz, Privatsphäre, Sicherheit, Ökologie, Beherrschbarkeit); DSGVO + EU AI Act + DSA + Data Act applicability. | Helmholtz Handlungsempfehlungen 2024-09 | thesis §5 reflexivity audit; Compliance Agent §6.B lens | `aidocs/strategy/93 §2` + §6.B |
| 2025-01-27 | `zlpDataDrivenIntelligence2025` (29-slide ZLP institutional deck) | (institutional, anonymous renderer) | DIKW pyramid as the centre's epistemological commitment; "active research data" framing; three-figure architectural triplet (slides 9/10/11): Shepard architecture + shop-floor protocol reality + context-capture pattern; slide 23 names Shepard inside Aerospace-X E2E-quality architecture. | 2020 iDMS slide-14 architecture | 2025-04-18 BT inventory (Krebs); 2026-02-26 GenKI deck | `aidocs/strategy/87 §§5–6` + `aidocs/strategy/94 §3` |
| 2025-02-21 | Vision slide 23 of `krebsInventur2025` (footer date) | **FK** | Cross-domain Shepard substrate band spanning *Structural mechanics / Materials / Design+Process / Production / Ground+Flight Test* — Krebs's institutional positioning of Shepard as the centre's cross-domain layer. | 2025-01-27 ZLP Data-Driven-Intelligence | 2025-04-18 BT/ZLP inventory full deck | `aidocs/strategy/100 §1` (called out as canonical thesis-substrate figure) |
| 2025-03-17 | `krebsAerospaceXJaxa2025` (10-slide briefing for JAXA) | **FK** | Aerospace-X internationalisation horizon — Japan/Korea/Canada/Italy/Australia/France/Netherlands contact points; Catena-X 26-M-AAS estimate; "transatlantic dataspace Canada↔Germany" lighthouse-project opportunity. | 2022-10-07 DLR ZAP Catena-X briefing (reused material) | 2026-05 federation sketches; thesis §9 outlook | `aidocs/strategy/94 §§2,5` |
| 2025-04-18 | `krebsInventur2025` — *Fähigkeiten-Inventur* (31 slides) | **FK** | BT/ZLP capabilities inventory: 9 cells (FPZ/TPZ/IQZ/MFZ/TEZ/Heißpresse/Cutterzentrum/Wasserstrahlanlage/CoBots+AGV) + 8 cross-cutting technologies + 17 instrumentation sources. **The portfolio Shepard's manufacturing rollout consumes.** | 2025-02-21 vision slide | 2026-05-23 BT/ZLP rollout plan (`aidocs/strategy/100`) | `aidocs/strategy/100` (load-bearing source) |
| 2025-07-23 | `krebsKukaCnc2025` (7 slides) | **FK** | KUKA.CNC vs KRL+RoboDK evaluation. KSS 8.6 / X63 EMI / PROFINET IRT / Sinumerik 840D compatibility analysis. Strategic conclusion: ~€100k delta + autonomous-vs-CNC paradigm tension. | KUKA-platform dominance at ZLP (TPZ.R10 IPRO precedent 2017) | M1-wave Trace3D attribute design (forward-compatibility note) | `aidocs/strategy/87 §3.1` |
| 2025-09-19 | `hgfFoPoZi5_2025` (cross-association research-policy targets, addressed to HGF) | (federal cross-Association) | PoF V (2028–2034) policy targets: (a) PFI continuity; (b) joint sustainability strategy; **(c) joint and overarching architecture concept for data-management infrastructure** — including FAIR, IP, NFDI alignment, Datenresilienz. | HMC Phase 2 commitments; NFDI4Ing Phase 2 | 2025-09-24 BMFTR letter to Wiestler transmitting the targets | `aidocs/strategy/87 §7.5` + `aidocs/strategy/93 §1` |
| 2025-09-24 | `bmftrPofV2025` (BMFTR letter, signed Dr. Zachgo) | BMFTR | Federal ministry transmits PoF V cross-cutting goals to Helmholtz presidium (Wiestler). Point (c) = data-management architecture concept. | 2025-09-19 cross-association draft | 2026-05-23 DLR Datenstrategie v60 draft | `aidocs/strategy/90 §1.1` |
| 2025-11-03 | `nfdi4ingIntroKrebs2025` (8-slide internal DLR briefing) | **FK** | NFDI4Ing 1+2 intro: DLR Phase 2 partner on F-1 (data-integration guidelines) and F-2 (Data Mesh ↔ RDM adaption). Phase 2 = 2025–2030; DLR contributes 4 PM/year. | NFDI4Ing existence since 2017/2019 | F-1 + F-2 deliverables across the cycle | `aidocs/strategy/88` |
| 2025-11-05 | `krebsMetrologyI42025` (15-slide PDF) | **FK** | Spatial Analyzer + 2× Leica AT 901 LR + T-Probe + T-MAC + T-SCAN 5 metrology stack; IREC published numbers (89.30% / 70.19% / ±0.3 mm aerospace tolerance). | ICRA24 paper (Audet et al., 2024) | `shepard-plugin-metrology` design seed; M1-wave acceptance test | `aidocs/strategy/87 §3.2` + `aidocs/integrations/96` |
| 2025 (HMC Phase 2 deck) | `hmcPhase2WpKrebs2025` (preliminary, footer placeholders) | **FK** | Three WPs: WP-1 export+import+PID (RO-Crate; ePIC/DataCite); WP-2 semantic-features (M1 wave); WP-3 cross-DLR interoperability (Shepard + twinStash + Defacto + Inst.DLR; Databus + MOSS foundation). | iDMS open-source release; ZLP Augsburg Shepard adoption | Phase 2 (2025–2030) deliverables | `aidocs/strategy/90` |
| 2025 (AerospaceX BDLI deck) | `weberAerospaceX2025` (14 slides; Weber lead) | **HW** + colleagues | Aerospace-X German consortium (30+ partners, Airbus Operations lead, April 2024 kickoff, 27 months): regulatory-tsunami timeline, four use cases, Catena-X/AAS/Tractus-X stack as technical vision. | Catena-X 2022 reference; Manufacturing-X programme | thesis §9 discussion; `shepard-plugin-edc` design candidate | `aidocs/strategy/92` |
| 2025 (Welzmüller PLUTO poster, FK-annotated) | `welzmuellerForschungsdatenPoster2025` | Welzmüller et al. (annotated by **FK**) | PLUTO RDM poster — Shepard's parallel space-mission use case (different domain, same architectural shape). | `welzmueller2024Pluto` eLib paper | thesis §6 case study (PLUTO sibling to MFFD) | (companion to existing PLUTO eLib bib) |
| 2026-02-02 | `Shepard__A_RDM_Stack` PDF (20 pages) — **(MISSING from bib)** | **FK** | Shepard-as-RDM-stack talk by Krebs. The author's current institutional positioning deck. | 2025 portfolio of strategic decks (KUKA, metrology, AerospaceX, inventory) | 2026-02-26 GenKI methodology deck | (no library doc citation yet — candidate primary source for §1 of thesis) |
| 2026 (early; placeholder 21.02.2025 on slide-3 footer) | `krebsForInfPro2026` (11 slides; Krebs lead; Glück, Lettowsky, Kaufmann co-authors) | **FK** + co-authors | ForInfPro semantically-driven data analytics: DIKW (Rowley 2007); Context Gap (silos / implicit knowledge / 2-year decay); slide-7 container/data-object contextualisation diagram (substrate's master picture); dry-spot reasoning chain ("could be cold resin at time X"); constraint-driven visualisation. | 2025-01-27 ZLP DIKW slide | M1-VIEWS-AS-SHAPES-WAVE bundle | `aidocs/strategy/91` |
| 2026 | `krebsOdix2026` (6 slides) | **FK** | ODIX ontology architecture — composite-infusion process spine; BFO/IOF/RO deferred-integration. | ForInfPro DIKW frame | `aidocs/semantics/95` SHACL template substrate | `aidocs/strategy/91 §7b` |
| 2026 (workshop) | `krebsForInfProWorkshop2026` (7 slides) | **FK** | Recorded-ideal-process-pattern as the realistic control strategy; substrate as live-loop participant. | ForInfPro main deck | dry-spot evaluation target | `aidocs/strategy/91 §7a` |
| 2026 | `krebsHumanoids2026` (15 slides) | **FK** | Humanoid robotics in manufacturing — market/capability survey; ISO 13482 / ISO 25785-1 future safety-evidence regime. | DLR robotics broader portfolio | thesis §9 discussion (horizon material) | (logged in bib; no standalone strategy doc — tangential to substrate thesis) |
| 2026 | `krebsRapid2025` (5 slides; RAPID WBS milestones; 2025-10-17 filename) | **FK** | Lot-Size-1 / rapid-refit → process model as first-class object; dual-use audit-trail framing. | 2017-2020 IPRO process modelling | thesis §3 architecture + §9 discussion | `aidocs/strategy/92 §5` (within cell-floor instrumentation context) |
| 2026-02-26 | `krebsGenKi2026` (17-slide PDF; closing AI-Disclosure slide naming SAIA/GWDG + MCP server set) | **FK** | Generative-KI tutorial deck: probability-not-magic; tokenisation; embeddings; transformer; HHH; four-risk-table; AI Disclosure as the deck's methodological hinge. **The author's publicly delivered AI-use posture.** | NIST AI RMF 2023; Vaswani 2017; Hoffmann 2022; Shumailov 2024 | thesis §5 methodology + reflexivity (this project's observed practice) | `aidocs/strategy/89` |
| 2026-03-20 | `bayernFachpanel2026` (16 slides) | (Bavarian state cross-stakeholder panel) | Bayern Fachpanel on data spaces: SWOT + six action fields (Lived Data Culture / Activate Expertise / Access to Infrastructure / Sovereignty + Resilience / Regulation + Standards / Bayern Acting Globally). The panel reads as a requirements spec for Shepard's plugin-first European-sovereignty architecture. | 2024-12 EU regulatory frame | thesis §1 introduction (regional policy context) | `aidocs/strategy/87 §7` |
| 2026 | `krebsDIVA2026` (V01 Projektideenskizze; LuFo VII-2 Aufgabe 3) | **FK** | DIVA — Drone Integration via AAS. TRL 1–2. Five DLR institutes (FT/KI/DW/MO/BT). **BT/ZLP names Shepard as AAS-repository infrastructure.** Three barriers (fragmentation/missing-identity/proprietary-standards) → four-layer ecosystem (e-conspicuity / proactive AAS Typ 3 / sovereign dataspaces / PHM 4.0 + autonomous MRO). | Aerospace-X regulatory framing; AAS-as-data-store roadmap | thesis §10 outlook; AAS-as-application-protocol future | `aidocs/strategy/101` |
| 2026 | `zlpKompetenzportfolio2026` (3-pp PDF; renderer Google Docs) | (institutional) | ZLP Competence Portfolio: TRL-6 bridge; five research groups (Flex Automation / Assembly+Joining / Production-Integrated QA / Processes+Automation / Technical Centre); four flagship cells (MFZ/FPC/TPC/TEC); **Shepard named as one of three enabling technologies** for the integrated factory (alongside DigECAT digital twin + inline QA). | 2025-04-18 BT inventory | 2026 ZEUS horizon (hydrogen-class structures) | `aidocs/strategy/87` |
| 2026-05-07 | `Strategy_Synthesis_BT_V23.pdf` (Foxit-rendered) — **(MISSING from bib)** | (DLR BT institutional) | BT-level strategy synthesis (V23). Likely incorporates the 2026-05-11/13 Digitalisierung_BT positioning. | 2025-04-18 BT inventory; 2025-09-19 PoF V targets | 2026-05-23 Datenstrategie v60 (federal alignment) | (no library citation yet — candidate input to `aidocs/strategy/93` §5) |
| 2026-05-11/13 | `Digitalisierung_BT.pptx` (Krebs; created 2026-05-11, modified 2026-05-13) — **(MISSING from bib)** | **FK** | BT-internal digitalisation positioning. Companion to BT V23 synthesis. | BT inventory + ZLP Data Driven Intelligence | 2026-05-23 v60 Datenstrategie | (no library citation yet) |
| 2026-05-22 | MFFD multi-source-collection import arc (cube3 + nuclide; v15 importer evolution) — operational evidence | (operations log; FK + AI dispatcher) | First production MFFD-real-data import. The "anomalies in the wild" demo path complementary to the synthetic seed. | Synthetic LUMEN + MFFD demos; v14 importer | MFFD seed demo (Trace3D thermal trail, dataset expected ~2026-05-26) | `RESUME.md`; `aidocs/strategy/100 §2 Wave 0` |
| **2026-05-23** | This Thesis Librarian pass + integrated library docs 86–101 + this doc 102 | AI dispatcher (Librarian agent) under FK instruction | First chronological-backbone synthesis across 16 years of artefacts. | The avalanche of 2026-05-23 (~50 uploads) | (next pass per §38 of the standing rule — ≥10 new artefacts or 2-week cadence) | This document |
| 2026-05-23 | `dlrDatenstrategieV60_2026` (DLR-internal v60 draft) | (DLR cross-institute) | Draft DLR Datenstrategie: four objectives (Datenökosystem / Interdisciplinary research / Predictive analytics decision support / Data culture and competence); data-steward network as evaluation surface. | 2025-09 PoF V cross-cutting target (c) | thesis §1 introduction; Compliance Agent §6.F lens | `aidocs/strategy/93 §1` + §6.F |
| 2026-05-23 | `krebsFederationSketches2026` — two architecture sketches (Sketch A: federated-Shepard / Common Data Infrastructure; Sketch B: Pub-Service publication architecture) | **FK** | Two integration paths named explicitly: **Path A** RO-Crate export/import (low risk); **Path B** federated-Shepard service (high effort/risk, deferred). Pub Service component routes to Databus + S3 + InvenioRDM. | 2022-11-04 federated-Shepard-data-space thinking | HMC WP-1 + WP-3 implementation; RO-Crate FS1g already shipped | `aidocs/strategy/90 §3.5` (Sketch B); `aidocs/strategy/94 §4` (Sketch A) |

## §3. Coherent threads

Six threads span ≥3 artefacts and reach the present. Each thread
is summarised in one paragraph + an evidence-anchor list.

### §3.1 Data-management thread (substrate-of-record)

**The longest thread.** It runs continuously from FK's 2010 MFZ
control-concept deck (*generic measurement-data interface*,
*central DB storage*, *automatic process-data documentation*)
through the 2013 SOA_MES proto-thinking, the 2017 ProDES/
Prozessleitsystem/NSR arc, the 2017-12-05 Vision Datenmanagement
deck (the strategic frame for what iDMS would become), the
2017–2020 iDMS prototype (slide 14 architecture), the 2020-11-04
final presentation, the **2021 DLRK paper (Krebs lead)**, the
2021 Zenodo Shepard release, the 2024+ Shepard-fork, and into the
2025 institutional artefacts (BT inventory, ZLP competence
portfolio, ZLP Data Driven Intelligence) and the 2026
forward-looking commitments (HMC Phase 2, NFDI4Ing F-1+F-2, DIVA,
Aerospace-X).

**Anchors.** `krebsMfzSteuerung2010` → 2013 SOA_MES → 2017 ProDES
(×2) → 2017-12-05 Vision Datenmanagement → `krebsIdms2020` →
**DLRK 2021 (MISSING bib)** → `haaseShepard2021` → `shepardFork`
→ `zlpKompetenzportfolio2026` + `krebsInventur2025` →
`hmcPhase2WpKrebs2025` + `nfdi4ingIntroKrebs2025` + `krebsDIVA2026`.

**Why this is the strongest thread.** Sixteen years of evidence,
one architect (FK), one architectural commitment (generic
measurement-data substrate with provenance), three institutional
form factors (paper-vision 2010-2017 → prototype iDMS → product
Shepard).

### §3.2 Process-control / orchestration thread

A shorter, sibling thread that runs from the 2017-04-25 ProDES
through the 2017-07-18 Prozessleitsystem deck, the 2017-09-14 NSR
Orchestrator concept, the 2017-12-04 NSR Architektur finalisation.
The thread **does not extend continuously into Shepard** — the
orchestration question parked at the 2017 NSR docs and is picked
up by separate workstreams (`opcua_orchestrator` on UNAS;
DLR-internal RCE workflow tool; the 2026 ForInfPro workshop's
*recorded-ideal-process-pattern* framing).

**Anchors.** `20170425_ProDES` → `20170619_ProDES` →
`20170718_Status_Prozessleitsystem` → `20170914_NSR_Orchestrator_Konzept`
→ `20171204_NSR_Architektur` → 2026 `krebsForInfProWorkshop2026`
(the substrate-as-live-loop-participant reframing).

**Observation.** The thread suggests Shepard *parked* the
orchestration ambition deliberately — what shipped is the
substrate, not the controller. The 2017 NSR docs are evidence
that orchestration was once in scope; the 2025 Shepard architecture
chose to keep it out of scope (plugin SPI lets a future external
orchestrator drive Shepard rather than vice versa). The thesis §3
architecture chapter can cite this as an *intentional restriction*,
not an oversight.

### §3.3 Robotics + cell-instrumentation thread

Runs from 2010 *Themen Robotik für Faserverbundfertigung*
through the 2017 IPRO TPZ.R10 KUKA telemetry (Grafana 2017-11-06),
the 2018 VAMHRI paper (Schönheits + Krebs on AR-in-HRI), the 2024
ICRA paper (Audet et al. incl. Krebs on AFP+elastic calibration),
the 2025-07-23 KUKA.CNC evaluation, the 2025-11-05 metrology
stack deck, and the 2026 Humanoids 2026 horizon-setting.

**Anchors.** `krebsRobotikThemen2010` → IPRO 2017-07-21 Prozessablauf
+ 2017-11-06 Grafana → VAMHRI 2018 (Schönheits+Krebs) →
**ICRA24 (MISSING bib)** → `krebsKukaCnc2025` → `krebsMetrologyI42025`
→ `krebsHumanoids2026`.

**Observation.** The robotics thread is not the substrate's
thread — it is the *data-source* thread. Each artefact along it
generates the kind of data Shepard's substrate is built to absorb.
The ICRA24 paper is the most thesis-load-bearing of the recent
robotics works because it is the **peer-reviewed source** that
backs the IREC numbers (89.30% / 70.19% / ±0.3 mm) cited in
metrology doc 96 — and Krebs is co-author.

### §3.4 Metrology + as-built verification thread

A short but high-value thread: the metrology stack (Spatial
Analyzer + 2× Leica AT 901 LR + T-Probe + T-MAC + T-SCAN 5) is
the **as-built verification stream** that closes the digital
thread against the *as-designed* CAD substrate. The thread
appears in the 2018 VAMHRI paper (calibration as the AR
application), the 2024 ICRA paper (IREC delivers ±0.3 mm
aerospace tolerance), and the 2025-11-05 metrology deck (the
integration target named for Shepard).

**Anchors.** VAMHRI 2018 → ICRA24 → `krebsMetrologyI42025` →
`aidocs/integrations/96` design seed.

**Convergence.** The thread converges with the data-management
thread at `aidocs/integrations/96` — the proposed
`shepard-plugin-metrology` payload kind. The thread also
converges with the federation thread at `aidocs/strategy/94`
(AAS GeometryAndKinematics submodel as the federation seam for
metrology data).

### §3.5 Institutional positioning + funding-cycle thread

Runs from the 2014 DLRK PRAESTO paper (first peer-reviewed ZLP
RDM record) through the 2021 DLRK Shepard paper (Krebs et al.;
formal publication of the substrate), into the 2024 Helmholtz
AI Empfehlungen, the 2024-12 DLR KI Leitfaden, the 2025-04 BT
inventory + the 2025-09-19 PoF V cross-association targets +
the 2025-09-24 BMFTR letter to Wiestler, and arrives at the
2026 institutional anchors: ZLP Kompetenzportfolio,
Bayern Fachpanel, DLR Datenstrategie v60, HMC Phase 2 WPs,
NFDI4Ing Phase 2 commitments, the DIVA Projektideenskizze.

**Anchors.** `nuschelePraesto2014paper` → `krebsIdms2020` →
**DLRK 2021 (MISSING bib)** → `haaseShepard2021` →
`helmholtzKiEmpfehlungen2024` → `dlrKiLeitfaden2024` →
`krebsInventur2025` → `hgfFoPoZi5_2025` → `bmftrPofV2025` →
`zlpKompetenzportfolio2026` + `bayernFachpanel2026` +
`dlrDatenstrategieV60_2026` + `hmcPhase2WpKrebs2025` +
`nfdi4ingIntroKrebs2025` + `krebsDIVA2026`.

**Observation.** The institutional framing has moved from
*"can a centre that produces this data manage it?"* (2014, PRAESTO
question) to *"does the centre's data infrastructure satisfy the
federal-funding-cycle mandate (point c of PoF V)?"* (2025–2026).
Shepard sits where the question was always pointing; the question
caught up with the architecture rather than the other way round.

### §3.6 Reflexivity / AI-in-science thread

The youngest thread but the most methodologically distinctive.
Anchored by the 2024-09-18 Helmholtz Handlungsempfehlungen and
the 2024-12-17 DLR KI Leitfaden + Leitbild on the institutional
side, and by Krebs's own 2026-02-26 GenKI tutorial deck (with its
load-bearing closing AI-Disclosure slide naming SAIA/GWDG + MCP
servers) on the personal side. The thread's third leg is this
project's *observed practice* — the memory rules, the
persona-board pattern, the snapshot-boundary discipline, the
f(ai)²r AI-provenance vocabulary, the energy-and-cost-accounting
log, and (per §6 of doc 93) the three-way alignment audit
showing **alignment with two honest divergences**
(European-hosting; AI-inference energy logging).

**Anchors.** `wantedFlo2012` (footnote-class calibration on the
author's working voice) → `helmholtzKiEmpfehlungen2024` →
`dlrKiLeitfaden2024` + `dlrKiLeitbild` → `krebsGenKi2026` →
this project's observed practice (`aidocs/strategy/89 §6` audit;
`aidocs/strategy/93 §2` three-way table).

**Observation.** The thread's value to the thesis is unusually
load-bearing — it makes the thesis's AI-assisted methodology a
*contribution*, not a *vulnerability*. The author has publicly
defended (in `krebsGenKi2026`) the very posture the thesis
demonstrates; the divergences (two, named openly) prove the
reflexivity exercise *found* something.

## §4. The convergence pattern

Five paragraphs naming what merged when and what the implications
are. The user's 2026-05-23 edit to doc 100 (adding the "Sibling
axis (DIVA)" line) is the rhetorical model — name the cross-thread
observation explicitly.

**§4.1 Convergence I (2017-12-05). The Vision Datenmanagement
moment.** Across an eight-month 2017 chronology, the substrate
question separated from the orchestration question. ProDES (April,
June) widened scope from inspection to end-to-end acquisition.
Prozessleitsystem (July) and the NSR Orchestrator/Architektur
concepts (September, December-04) tried to widen further — into
control. The 2017-12-05 *Vision Datenmanagement* deck made the
choice: the substrate would be the focus; the orchestrator would
be a separate concern. iDMS (2017–2020) and Shepard (2021+)
inherited this choice. The 2017 NSR docs are the *path not taken*
that explains why Shepard ships as a substrate, not a controller.

**§4.2 Convergence II (2020-11-04 → 2021 DLRK).** Slide 14 of the
iDMS final presentation crystallised the substrate's architecture
("graph for relations + provenance, separate substrates by data
type, no one-size-fits-all"). The 2021 DLRK paper (Krebs lead,
seven co-authors) **published** that architecture under the name
"shepard" the same year the Zenodo software record landed. **The
public-record citation chain from iDMS to Shepard runs through
this paper.** Library doc 86 §5 currently characterises the
2021-team continuity as "predominantly institutional and
architectural" because the Zenodo record's four authors do not
match iDMS's known authors except Willmeroth. The DLRK 2021 paper
revises this: Krebs is **first author** of the public Shepard
publication, the paper uses the iDMS abbreviation throughout
("IDMS — integriertes Datenmanagementsystem"), and the
seven-co-author list includes Willmeroth, Haase, Kaufmann, Glück
(the Zenodo authors) plus Deden, Brandt, Mayer. **The handoff is
personal as well as institutional.** This is the highest-priority
drift finding for the next pass — see §6 below.

**§4.3 Convergence III (2022-11-04). Federation thinking, three
years early.** The `Federated_shepard_data_space.pptx` deck of
2022-11-04 (Krebs; created and modified same day) puts federation
on paper three years before the 2025-04-18 BT inventory names
Shepard as the cross-domain substrate and three-and-a-half years
before the 2026-05-23 federation sketches (A and B). The
Manufacturing-X / NFDI4Ing / HMC WP-3 federation arc that doc 94
synthesises is therefore not Shepard reacting to external pressure
— it is Shepard's design *meeting* external pressure that the
design anticipated. The thesis §3 architecture chapter can cite
this as evidence that the plugin-first SPI is not a 2024 reaction
to plugin-system fashion; it is the operational form of a 2022
strategic position.

**§4.4 Convergence IV (2025-09-19 + 2025-09-24). The PoF V
ministerial moment.** The cross-association PoF V research-policy
targets and the BMFTR letter to Wiestler land within five days of
each other in September 2025. The targets name *data-management
architecture concept* as one of three cross-cutting goals; the
letter transmits the targets to the Helmholtz presidium with full
ministerial weight. The HMC Phase 2 WPs (`hmcPhase2WpKrebs2025`)
and the NFDI4Ing Phase 2 measures (`nfdi4ingIntroKrebs2025`)
become the operational vehicles by which this ministerial ask is
discharged at the DLR-ZLP level. Shepard's WP-3 federation
prototype, the M1-wave semantic features, and the RO-Crate +
PID minting work are not internal preferences — they are the
DLR-ZLP slice of the federal-policy-mandated joint architecture.
The thesis §1 introduction can be unusually direct about this
chain of causation: the substrate is on-policy because the policy
caught up with the substrate.

**§4.5 Convergence V (2026-05-23). The sibling-axes day.** The
day this librarian pass runs: the DLR Datenstrategie v60 draft is
generated; the rollout-plan doc 100 is published with the user's
hand-added "Sibling axis (DIVA)" line at line 42; the DIVA doc
101 is filed as the parallel application axis on the same
substrate; the federation sketches A (federated-Shepard) and B
(Pub-Service) anchor doc 94's Path-A-first architectural humility;
the Strategy_Synthesis_BT_V23 and Digitalisierung_BT decks
position BT-level digitalisation; the ZLP Competence Portfolio
names Shepard as one of three enabling technologies. **The thread
convergence is the day itself.** Six previously distinct threads
(data-management, robotics, metrology, institutional positioning,
federation, reflexivity) all surface artefacts on the same day,
not because the threads are interchangeable but because each has
reached a milestone the others can recognise. The thesis §4
ontology chapter can read this convergence as the moment the
substrate's ontology stops being a developer concern and starts
being an institutional one.

## §5. Open threads

These are surfaced-but-not-yet-resolved ideas across the corpus.
Each is a candidate for either a backlog row, a future strategy
doc, or a thesis §10 outlook subsection.

- **The DLRK 2021 paper's reception.** Krebs et al. (2021) is the
  formal Shepard publication. Has it been cited externally? Has
  it been read against the Schlenz et al. (2026) handbook (which
  does not cite it)? A web-search pass should attempt to find
  any Google-Scholar, Crossref, or eLib downstream references —
  this is the kind of citation-chain evidence the thesis defence
  needs.

- **The 2017 NSR Architektur path-not-taken.** What happened to
  the orchestration question between 2017-12-04 (NSR Architektur
  finalised) and the 2025 ForInfPro workshop's
  *recorded-ideal-process-pattern*? Is there a documented
  decision-of-record that says "ZLP parked orchestration in
  favour of substrate-first"? The thesis §3 architecture chapter
  benefits from one citable internal decision.

- **The 2013 SOA_MES deck's status.** Krebs's 2013 SOA + MES
  deck is the earliest evidence of proto-MES thinking at ZLP.
  Was a paper or design memo produced from it? Where is the
  Vision-Datenmanagement-2017 deck's bibliography to that 2013
  thinking? The library currently has no §3 entry on this in
  doc 86.

- **The Helmholtz Open Science Policy line-by-line audit.** Doc
  93 §6.E names Helmholtz Open Science Policy as a satisfied
  lens but does not cite the policy document itself. The next
  consolidator pass should pull the policy text (cf.
  `feedback_ask_for_artefacts.md`).

- **The IDTA submodel catalogue's coverage of DIVA needs.** DIVA's
  AP 2 proposes net-new IDTA submodels (*DroneIdentity*,
  *AirspaceSlot*). A survey of the existing IDTA catalogue would
  tell us whether DIVA's AP 2 is a contribution to IDTA or a
  gap-filling project against it. (Per doc 101 §6 open question
  parked for V02.)

- **The AerospaceX deck's primary-source provenance.** The Weber
  deck (`weberAerospaceX2025`) is uploaded but not paginated or
  cross-referenced against the IMEC2024 paper by Bergs and Ganser
  (`bergsAerospaceXImec2024`). The thesis §9 discussion would
  benefit from naming the Bergs+Ganser paper as the primary
  source and the Weber deck as the institutional restatement.

- **PRAESTO's operational scope.** Despite the 2026-11-24
  Krebs annotation of the Nemes input deck, PRAESTO's
  operational fate at ZLP after 2017 is undocumented. The eLib
  record's silence + the testimony-only framing remains the
  evidence base. A consolidator pass could ask: was there a
  PRAESTO-to-internal handover memo?

- **The DIKW commitment's externalisability.** Doc 87 §5 quotes
  the Data-Driven-Intelligence deck's DIKW triplet as Shepard's
  philosophical premise. The thesis §3 architecture chapter
  needs the DIKW commitment turned into measurable claims about
  Shepard's substrate (which traversals climb DIKW; which can't).
  No design doc captures this yet.

- **The reading list's surfaced-but-not-pursued topics.** Per
  `feedback_reading_list.md`, every dispatch adds 2–5 entries to
  `aidocs/reading-list.md`. The librarian pass should write a
  one-paragraph reading-list update on completion (see §8 below).

## §6. Library-coherence findings

The pass surfaced six drift findings. Severity scale per
`feedback_thesis_librarian_standing.md`: CRITICAL = blocks the
thesis citation chain; MAJOR = significantly weakens a load-bearing
claim; MINOR = stale wording or a fixable cross-reference.

**§6.1 CRITICAL — DLRK 2021 paper missing from the bibliography.**
The paper *Systematische Erfassung, Verwaltung und Nutzung von
Daten aus Experimenten* (Krebs F. (lead), Willmeroth M., Haase T.,
Kaufmann P., Glück R., Deden D., Brandt L., Mayer M., DLR
BT/ZLP Augsburg; 6 pages; PDF generated 2021-07-30; presented at
DLRK 2021) is **the formal Shepard publication** and is **not in
`docs/_data/references.bib`**. The artefact upload
`6db8bac2-DLRK_Systematische_Erfassung_Verwaltung_und_Nutzung_von_Daten_aus_Experimenten.pdf`
exists; the bib entry does not. **Fix:** add bib entry
`krebsShepardDlrk2021` in this PR's commit; cite it from doc 86
§4 (replaces the "personal continuity is zero" framing of §5),
from this doc's §3.1 + §3.5 + §4.2, and from §1 of the thesis
outline. **Note:** doc 86 §5's *Lineage-glue confidence: HIGH*
note correctly says the personal continuity is "no longer zero" —
but understates it. Krebs himself is the **first author** of the
public Shepard paper. This is the strongest single piece of
citation-chain evidence the thesis can carry.

**§6.2 CRITICAL — doc 86 §5 needs revision.** The current
characterisation that "the other Shepard authors (Haase, Glück,
Kaufmann) do not appear in iDMS/KIBID sources we hold" is true
*on the UNAS code drop alone* but false *on the public-record
evidence* — Haase, Glück, Kaufmann are three of the seven
co-authors of the 2021 DLRK paper that names Shepard explicitly
and uses the IDMS abbreviation. The personal-handoff finding
extends from one name (Willmeroth) to **all four Zenodo authors +
three more (Deden, Brandt, Mayer)**. **Fix:** revise doc 86 §5
*Lineage-glue confidence* paragraph in a follow-up PR (not this
one — librarian-pass discipline is *flag the drift, don't edit
the sibling doc*).

**§6.3 MAJOR — ICRA24 paper not bib-keyed despite metrology
doc 96 citing its claims.** Doc 96 §3 reports the 89.30% / 70.19%
TCP-deviation-reduction numbers without bib citation. The Audet et
al. (ICRA 2024) paper is the peer-reviewed source and Krebs is a
co-author; the PDF is in the upload set
(`3f8f6af3-ICRA24_2640_MS.pdf`). **Fix:** add bib entry
`audetIcra2024Irec` (or similar) in this PR's commit; cite from
doc 96 §3 and doc 102 §3.3 + §3.4.

**§6.4 MAJOR — 2022-11-04 Federated_shepard_data_space deck not
in library or bib.** The deck is the earliest writeup of
federation thinking by Krebs (`9f915a7c-Federated_shepard_data_space.pptx`,
created and modified 2022-11-04). The current doc 94 *Federation
and dataspaces* implies the federation thinking arrives in 2025–2026
with Manufacturing-X / NFDI4Ing / HMC pressure. The 2022 deck
contradicts that implication. **Fix:** add bib entry
`krebsFederatedShepard2022`; cite from doc 94 §1 (the chapter
arrives with antedated authorship); from this doc's §3.1 + §4.3.

**§6.5 MAJOR — 2017 ProDES/Prozessleitsystem/NSR/Vision-DM
chronology not in any library doc.** Five Krebs-authored 2017
decks (ProDES Apr+Jun, Prozessleitsystem Jul, NSR Orchestrator
Sep, NSR Architektur Dec-04, Vision Datenmanagement Dec-05) plus
the Industry-4.0-defined deck (2017-10-10) are uploaded but not
incorporated. Doc 86 §3.5 cites the IPRO TODO + Grafana but does
not have the eight-month strategic-decks arc that frames the
substrate-vs-orchestrator question. **Fix:** add a §3.6 subsection
to doc 86 in a follow-up PR (separate concern from this one); add
bib entries `krebsProDES2017a/b`, `krebsProzessleitsystem2017`,
`krebsNsrOrchestrator2017`, `krebsNsrArchitektur2017`,
`krebsVisionDatenmanagement2017`, `krebsIndustry40_2017`. **Note:**
the standing-rule allows the consolidator (not the librarian) to
do the per-artefact extraction; the librarian's job is to flag
the gap.

**§6.6 MINOR — 2013 SOA_MES deck (Krebs) not in bib or library.**
The 2013-07-09-created (2016-10-26-modified) Krebs deck is the
earliest evidence of proto-MES thinking at ZLP. **Fix:** add
bib entry `krebsSoaMes2013`; consider whether it warrants more
than a footnote in doc 86's revised §3.

**§6.7 MINOR — Shepard__A_RDM_Stack deck (Krebs 2026-02-02)
not in bib.** The 20-page PDF talk by Krebs is uploaded but
uncited. **Fix:** add bib entry `krebsShepardRdmStack2026`;
consider it for §1 of the thesis outline.

**§6.8 MINOR — Strategy_Synthesis_BT_V23 and Digitalisierung_BT
not in library.** Two 2026-05 BT-internal strategy artefacts;
candidates for inclusion in doc 93 §5 (the management-context
chapter) on a future consolidator pass.

**§6.9 MINOR — Reading-list update missing for this dispatch.**
Per `feedback_reading_list.md`, every dispatch should add 2-5
entries to `aidocs/reading-list.md`. The first librarian pass
contributes its own (see §8 below).

## §7. Suggested outline shifts for `project_thesis_idea.md`

The existing outline (`project_thesis_idea.md` §"Outline" subsection,
dated 2026-05-23) is materially correct on §1–§9. Three
minor extensions follow from this librarian pass:

- **§1 Introduction — add the DLRK 2021 paper as the citation-chain
  anchor.** The thesis can cite Krebs et al. (2021) as the public-
  record document that names "shepard" the same year the Zenodo
  release lands. Combined with `krebsIdms2020` (the 2020 final
  presentation) and the 2026 Shepard fork, this gives the
  citation chain its three formal anchors.

- **§2 Continuity of field — add the 2017 chronology subsection.**
  Doc 86 §3 (PRAESTO) → §4 (CUBE iDMS) currently has a §3.5 on
  the operational evidence (KIBID + IPRO Grafana) but no §3.6 on
  the *paper trail* — the eight Krebs-authored 2017 decks that
  bridge KIBID-vendor-abandonment to iDMS-internal-build. The
  outline should reserve a §2.3 subsection for this chronology.

- **§10 Outlook — confirm DIVA + Aerospace-X + Wave-5 federation
  as the canonical outlook material.** Per doc 101 §4.2, DIVA is
  outlook material (TRL 1-2, V01 skizze, no funding award). Per
  doc 94 §6, the four federation horizons (intra-DLR / intra-
  Helmholtz / inter-NFDI / industrial-dataspace) converge on the
  m4i+AAS seam. Per doc 100 §5, Wave 5 (cross-cell + cross-domain)
  is the realisation of the 2025-02-21 vision slide. The §10 chapter
  can be a coherent piece across these three.

No structural restructure proposed. The outline matures by
*addition* per the librarian-cadence rule, not by reshape.

## §8. Reading-list contribution from this dispatch

Per `feedback_reading_list.md`, this dispatch adds the following
to `aidocs/reading-list.md` (not edited in this PR; flagged here
for the next consolidator pass):

1. **Schlenz et al. (2026) NFDI4Ing RDM handbook §5 ELN
   detail tours** — JuliaBase / eLabFTW / Kadi4Mat. The thesis §4
   ELN-comparison scorecard needs the line-by-line read.
2. **Citation-chain hunt for DLRK 2021 Krebs et al.** — any
   downstream references via Google Scholar / Crossref / eLib?
3. **IDTA submodel catalogue current state** — does it cover the
   DIVA AP-2 *DroneIdentity* / *AirspaceSlot* candidate submodels?
4. **Helmholtz Open Science Policy text** — referenced in doc 93
   §6.E but not pulled.
5. **Dehghani 2019 Data Mesh paper** — referenced in doc 88 §2.2
   but not in `references.bib`.
6. **QMH-DLR-VA014 + QMH-DLR-VA015** — DLR intranet QMH portal;
   the COMP-SE-MAP backlog row depends on these.

## §9. References

Citations in this doc resolve via
`docs/_data/references.bib` and the per-doc §"Primary sources"
sections of `aidocs/strategy/86–101`, `aidocs/98`,
`aidocs/integrations/96`.

**Sources newly proposed for bib entry in this PR** (the
librarian's commit adds them):

- `krebsShepardDlrk2021` — Krebs F., Willmeroth M., Haase T.,
  Kaufmann P., Glück R., Deden D., Brandt L., Mayer M. (2021).
  *Systematische Erfassung, Verwaltung und Nutzung von Daten aus
  Experimenten*. DLRK 2021 (Deutscher Luft- und Raumfahrtkongress),
  DLR BT/ZLP Augsburg. 6 pages. PDF creation 2021-07-30.
  **The formal Shepard publication.**
- `audetIcra2024Irec` — Audet J.-M., Fortin Y., Côté G.,
  Vistein M., Brandt L., Krebs F., Monsarrat B. (2024). *Iterative
  Robot Calibration with Accuracy Saturation Termination and
  Application for AFP Processing of a Thermoplastic Aerostructure
  with Real-time Elastic Path Compensation*. IEEE ICRA 2024.
  Submitted 2023-09-15. **Backs the IREC numbers in doc 96.**
- `krebsFederatedShepard2022` — Krebs F. (2022-11-04).
  *Federated shepard data space*. ZLP working deck.
  **Antedates the 2025–2026 federation thinking by 3 years.**
- `krebsSoaMes2013` — Krebs F. (2013-07-09; mod 2016-10-26).
  *SOA + MES*. ZLP working deck. **Earliest evidence of proto-MES
  thinking at ZLP.**
- `krebsShepardRdmStack2026` — Krebs F. (2026-02-02).
  *Shepard — A RDM Stack*. 20-page talk PDF.

**Sources flagged for a future consolidator pass** (not added in
this PR; require per-artefact extraction):

- `krebsProDES2017a` (2017-04-25), `krebsProDES2017b`
  (2017-06-19), `krebsProzessleitsystem2017` (2017-07-18),
  `krebsNsrOrchestrator2017` (2017-09-14),
  `krebsNsrArchitektur2017` (2017-12-04),
  `krebsVisionDatenmanagement2017` (2017-12-05),
  `krebsIndustry40_2017` (2017-10-10).

These seven 2017 Krebs-authored decks together form the **proposed
§2.3 subsection of doc 86** — see §6.5 above.

## §10. Honest companion

This is the **first** Thesis Librarian pass. The rule's standing
shape says the librarian *dispatches on signals + cadence, not
continuously*; the next dispatch is plausibly when (a) ≥10 new
artefacts have landed, (b) a chapter advances stage materially,
(c) cross-doc drift is detected, or (d) the suggested 2-week
cadence elapses during active research.

The pass's evidentiary basis is uneven in the way the §6.1
finding makes load-bearing: the **DLRK 2021 paper is the highest-
priority correction the library carries away from this pass**.
If the next consolidator does nothing else with the librarian's
output, adding the `krebsShepardDlrk2021` bib entry + revising
doc 86 §5 to incorporate the personal+intellectual handoff is the
single change that improves the thesis citation chain the most.

The chronology is **coherent enough to defend** — the 2010 → 2026
spine has anchors at every load-bearing year, the four
predecessor-system claims (PRAESTO / KIBID / iDMS / Shepard) are
substantiated, the six threads converge legibly. The gaps that
remain are documentary (line-by-line audits of the 2017 decks; a
PRAESTO operational-fate memo; an IDTA submodel-catalogue survey)
rather than structural. The librarian-future-self running the
next pass should expect to find these gaps closing, not widening.
