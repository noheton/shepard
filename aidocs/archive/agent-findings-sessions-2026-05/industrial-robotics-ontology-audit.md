---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Industrial Robotics + ZLP-Domain Ontology Audit (Shepard scope)

**Author:** Industrial Robotics Ontology Researcher (specialised agent)
**Date:** 2026-05-21
**Scope:** Complement to `aidocs/agent-findings/dlr-ontology-catalog.md`. Focused on industrial-robotics ontologies and ontologies relevant to ZLP Augsburg's domains: AFP, thermoplastic CFRP, ultrasonic / resistance / stud welding, NDT, KUKA R20 + LBR cobots, digital twin / digital thread.

## TL;DR (5 sentences)

The single highest-leverage adoption is the **hsu-aut Industrial Standard Ontology Design Patterns** family (DIN 8580 manufacturing-process taxonomy + VDI 2860 handling operations + IEEE 1872.2 autonomous-robotics + OPC UA) — MIT-licensed, small, BFO-compatible by construction, and the only ready-made ontology cluster that aligns directly to the German manufacturing-standards stack ZLP uses. **MSEO (Fraunhofer/Mat-O-Lab)** is the second pillar — IOF-stack-based and the only public welding-domain ontology with real maintenance activity, directly applicable to MFFD's ultrasonic and resistance welding steps. The IEEE 1872 / CORA family is **SUMO-rooted**, has no native OWL form of SUMO, and **should not be dual-anchored against IOF Core** — adopt CORA terminology via SKOS-only import to avoid axiom conflicts with the BFO-anchored Shepard upper stack that `aidocs/semantics/96-upper-ontology-alignment.md` already commits to. The AAS / IDTA submodel surface (Digital Nameplate 3.0 Oct 2025, AGV Technical Data Mar 2025, AI Model Nameplate Feb 2025) is the **Industrie-4.0 bridge most worth wiring**: KUKA R20 / TPS head / LBR iiwa each map onto Digital Nameplate naturally and AAS already publishes RDF. The biggest ZLP-domain gap is **AFP / resin-infusion / thermoplastic-welding process ontologies do not exist** publicly — that is the ▲-quadrant opportunity for Shepard + DLR-Stade's BMBF-funded OntOMaT to publish the first public artefact in this space.

---

## 1. Methodology + adoption-signal framing

Industrial robotics + manufacturing-process ontologies are a **smaller niche** than the broader DLR/Helmholtz mid-level ontology landscape covered in the sibling audit. Adoption signals must be read relative to the niche: a repository with 20 stars and one academic group maintaining it can be a **canonical artefact** here, where the same numbers in the materials-science niche would be a hobby project. Conversely, niche-internal absence (no public AFP ontology in 2026) is signal in itself — the community has not converged.

Within-niche checks applied:
- **Code/artefact presence on GitHub** (clone-able + machine-readable + dated last commit).
- **Standards-body anchoring** (IEEE-RAS, OAGi/IOF, IDTA, DIN/VDI, ISO/CEN).
- **Upper-ontology lineage** — every entry classed as BFO-rooted, SUMO-rooted, or upper-unaligned. Mixing roots is technical debt; see §5.
- **License clarity** — required for redistribution as a Shepard bootstrap bundle. A "no license" repo is a blocker even if active.
- **DLR participation** — flagged where surfaced; the honest finding is that **DLR-RM (Oberpfaffenhofen)** is a ROS-code contributor without surfaced IEEE-RAS / IDTA standards-committee seats, and DLR-Stade is the only institute with an active manufacturing-ontology project (OntOMaT, BMBF-funded 2022-2026, **no public artefact yet**).

Sibling audit grounding: `aidocs/agent-findings/dlr-ontology-catalog.md` Quadrant ★ entries (BFO, IAO, EMMO Core, CHAMEO, MWO, PMDco, HDO, NFDIcore, m4i) form the upper layer this audit's entries either bridge to or compete with.

---

## 2. Industrial robotics ontologies catalogue

| ID | Name + version | Source | Sponsor | Upper anchor | License | Status | Scope | DLR-fit | Shepard priority |
|---|---|---|---|---|---|---|---|---|---|
| **ieee-1872** | IEEE 1872-2015 CORA + suite (CORA, CORAX, RPARTS, POS) | [github.com/srfiorini/IEEE1872-owl](https://github.com/srfiorini/IEEE1872-owl); [standard](https://standards.ieee.org/ieee/1872/5354/) | IEEE-RAS | **SUMO** (DUL for 1872.2) | spec paywalled; OWL implementations MIT-ish | published 2015; OWL maintained | Robot / robotic-system / robot-part / position core; "the" robotics ontology standard | 3 (vocabulary value high; alignment-cost high) | **investigate** — SKOS-only import; do NOT dual-anchor with IOF (§5) |
| **ieee-1872-2** | IEEE 1872.2 — Autonomous Robotics ontology | [github.com/hsu-aut/IndustrialStandard-ODP-IEEE1872-2](https://github.com/hsu-aut/IndustrialStandard-ODP-IEEE1872-2) | IEEE-RAS / HSU Hamburg ODP-port | DUL + SUMO | MIT | active (HSU port) | Robot autonomy classifications | 3 | investigate as SKOS |
| **iof-core** | IOF Core Ontology v202301 + | [github.com/iofoundry/Core](https://github.com/iofoundry/Core); [spec.industrialontologies.org](https://spec.industrialontologies.org/iof/) | OAGi / IOF (NIST + Boeing + BAM etc.) | **BFO** | MIT | active | Mid-level: ManufacturingProcess, Material, Equipment, Specification, ProcessParameter | 4 | already in **96-upper §2.2 must-have** |
| **iof-maint** | IOF Maintenance Ontology (Hodkiewicz et al. 2024) | [arxiv:2404.05224](https://arxiv.org/abs/2404.05224); part of [iofoundry/ontology](https://github.com/iofoundry/ontology) | IOF + Univ. Western Australia | BFO via IOF Core | MIT | active (2024) | Maintenance management, asset failure, FMEA | 4 (MFFD calibration + NDT recheck = maintenance pattern) | **add to bootstrap** — closes EN 9100 maintenance/calibration traceability gap |
| **iof-supplychain** | IOF Supply Chain Reference Ontology (SCRO) | [github.com/iofoundry/ontology](https://github.com/iofoundry/ontology); [ASU SCRO](https://labs.engineering.asu.edu/semantics/ontology-download/iof-reference-ontologies/iof-supply-chain-structure/) | IOF + NIST + ASU | BFO via IOF Core | MIT | active | Material batch / shipment / fulfilment | 3 (MFFD batch traceability) | nice-to-have |
| **iof-robotics** | IOF Robotics module | not yet published | IOF | BFO via IOF Core | n/a | **does not exist publicly as of 2026-05** | — | n/a | **gap** — Shepard could be early contributor |
| **knowrob** | KnowRob 2.0 | [knowrob.org](https://knowrob.org/); [github.com/knowrob/knowrob](https://github.com/knowrob/knowrob) | Univ. Bremen IAI (Beetz) | DUL + custom upper | BSD-2 | active | Cognition-enabled robot knowledge base; everyday-activity skills | 3 (research-grade; aerospace MFG mismatch) | skip — academic robotics, not manufacturing-MES shape |
| **caskman** | CaSkMan + CSS + CaSk (capability/skill stack) | [github.com/CaSkade-Automation/CaSkMan](https://github.com/CaSkade-Automation/CaSkMan); [CSS](https://github.com/CaSkade-Automation/CSS) | Helmut-Schmidt-University Hamburg (Köcher) | DIN 8580 + VDI 2860 (no explicit BFO) | **no license declared** | active (last push 2025-07) | Machine capabilities + executable skills; OPC UA + WSDL bindings | 4 (MFFD device-capability model; LBR iiwa skill description) | **investigate** — blocked on license until clarified |
| **css** | Capability-Skill-Service ontology (Plattform I4.0 reference model) | [github.com/CaSkade-Automation/CSS](https://github.com/CaSkade-Automation/CSS) | Plattform Industrie 4.0 + HSU | — | — | active | Abstract reference model (parent of CaSk + CaSkMan) | 3 | follows CaSkMan decision |
| **hsu-odp-din8580** | DIN 8580 ODP (German MfG-process taxonomy) | [github.com/hsu-aut/IndustrialStandard-ODP-DIN8580](https://github.com/hsu-aut/IndustrialStandard-ODP-DIN8580) | HSU Hamburg AUT | upper-agnostic (pure `rdfs:subClassOf`) | **MIT** | active (last push 2023-07) | 6-group MfG-process taxonomy (forming / cutting / joining / coating / property-changing / primary-shaping) | **5** (every ZLP process is a DIN 8580 leaf) | **must-have** — drop alongside SimaT |
| **hsu-odp-vdi2860** | VDI 2860 ODP (handling-operations taxonomy) | [github.com/hsu-aut/IndustrialStandard-ODP-VDI2860](https://github.com/hsu-aut/IndustrialStandard-ODP-VDI2860) | HSU Hamburg AUT | upper-agnostic | **MIT** | active | Atomic handling actions (grasp, place, store …) | 4 (LBR iiwa cleat handling; AFP placement vocabulary) | **must-have** |
| **hsu-odp-opc-ua** | OPC UA ODP | [github.com/hsu-aut/IndustrialStandard-ODP-OPC-UA](https://github.com/hsu-aut/IndustrialStandard-ODP-OPC-UA) | HSU Hamburg AUT | upper-agnostic | MIT | active | OPC UA NodeSet vocabulary | 4 (DRG bridge already speaks OPC UA — `project_drg_tooling`) | **must-have** if `shepard-plugin-industrial` ships |
| **hsu-aut-umbrella** | Industrial-Standard ODP umbrella | [github.com/hsu-aut/Industrial-Standard-Ontology-Design-Patterns](https://github.com/hsu-aut/Industrial-Standard-Ontology-Design-Patterns) | HSU Hamburg AUT | — | MIT | **archived 2021** (individual ODP repos continue) | Index | — | reference only |
| **nist-task** | NIST Robot Task Ontology + RACR / kit-building | [nist.gov/publications/towards-robot-task-ontology-standard](https://www.nist.gov/publications/towards-robot-task-ontology-standard) | NIST | mixed (some NIST-specific) | US Gov public-domain | research-grade | Robot kitting + agility-performance metrics | 3 | skip — kit-building scope, not aerospace assembly |
| **nist-am** | NIST Additive Manufacturing OWL | [github.com/iassouroko/AMontology](https://github.com/iassouroko/AMontology) | NIST | own | US Gov | research | AM domain | 3 (DLR-WF AM) | nice-to-have for AM showcase |
| **aas-rdf** | Semantic Asset Administration Shell + IDTA RDF submodels | [admin-shell-io/aas-specs-metamodel](https://github.com/admin-shell-io/aas-specs-metamodel); [arxiv:1909.00690](https://arxiv.org/abs/1909.00690) | Plattform Industrie 4.0 + IDTA | own (mapped to BFO via capability ontologies — [arxiv:2307.00827](https://arxiv.org/html/2307.00827v2)) | mixed (AAS metamodel: CC-BY; IDTA submodel templates: free-to-use) | **active** (IDTA: AGV Mar 2025, AI Model Nameplate Feb 2025, Digital Nameplate 3.0 Oct 2025) | Industrie-4.0 digital-twin metamodel; 90+ IDTA submodel templates | **5** (MFFD digital twin; each KUKA/TPS/LBR is one AAS) | **investigate → adopt** — see §6 |
| **idta-nameplate** | IDTA 02006-3-0 Digital Nameplate for Industrial Equipment | [PDF Oct 2025](https://industrialdigitaltwin.org/wp-content/uploads/2025/10/IDTA-02006-3-0-1_Submodel_Digital-Nameplate.pdf) | IDTA | free-to-use | active | KUKA R20 / LBR iiwa / TPS head all qualify | 5 | adopt via AAS-RDF |
| **idta-agv** | IDTA 02047-1-0 Technical Data for AGV in Intralogistics (Mar 2025) | [PDF](https://industrialdigitaltwin.org/wp-content/uploads/2025/03/IDTA-02047-1-0-Submodel_Technical-Data-for-AGV.pdf) | IDTA | free | active | Mobile-platform metadata (MFFD has no AGV currently; future-proof) | 2 | optional |
| **idta-ai-nameplate** | IDTA 02060-1-0 AI Model Nameplate (Feb 2025) | [PDF](https://industrialdigitaltwin.org/wp-content/uploads/2025/02/IDTA-02060-1-0_Submodel_AIModelNameplate.pdf) | IDTA | free | active | AI lifecycle metadata; pairs with FAIR4ML (`project_rebar_integration`) | 4 (EASA Learning Assurance) | **adopt** — covers TPL9 model-card requirements |

Notes on ontologies actively considered but **omitted** as unsuitable:
- **RACE / RACER, PERLA / Open-PERLA** — academic, no maintained public artefact found 2024-2026.
- **OAA Ontology for Autonomous Agents** — superseded by IEEE 1872.2.
- **EMMO Robotics extension** — not present in [emmo-repo](https://github.com/emmo-repo/) as of 2026-05.

---

## 3. ZLP-domain ontologies catalogue

ZLP Augsburg's published domain catalogue ([standort page](https://www.dlr.de/de/zlp/ueber-uns/das-institut/standort-augsburg)) lists: flexible automation, ultrasonic + resistance welding, thermoplastic composite technologies, hybrid structures, multifunctional robotic cell, production-integrated quality control, Guided Wave Dispersion Analysis. The ontology landscape covering these domains is **thin to non-existent**.

| Domain | Ontology candidate | Source | Upper | License | Status | Verdict |
|---|---|---|---|---|---|---|
| MfG-process taxonomy | **DIN 8580 ODP** (HSU) | [hsu-aut/IndustrialStandard-ODP-DIN8580](https://github.com/hsu-aut/IndustrialStandard-ODP-DIN8580) | upper-agnostic | MIT | active | **adopt** |
| MfG-process inside EMMO | MaterialDigital DIN-8580→EMMO bridge | [ResearchGate fig 371748451](https://www.researchgate.net/figure/ManufacturingProcess-ontology-based-on-DIN-8580-and-its-integration-into-the-EMMO_fig2_371748451) | EMMO | — | research figure only; no public TTL surfaced | watch |
| Welding (mid-level + IOF-stack) | **MSEO mid + welding submodule** | [github.com/Mat-O-Lab/MSEO](https://github.com/Mat-O-Lab/MSEO) ([raw TTL](https://raw.githubusercontent.com/Mat-O-Lab/MSEO/main/MSEO_mid.ttl)) | **IOF stack** | MIT | active | **adopt** — already named in `96-upper §2.5 optional bridges` |
| Welding (joining-processes domain ontology) | CDOJP — Core Domain Ontology of Joining Processes (Tikkala et al. 2018) | [ScienceDirect 0736584518302126](https://www.sciencedirect.com/science/article/abs/pii/S0736584518302126) | research-specific | paywalled paper | academic, no public artefact surfaced | reference only |
| AFP (Automated Fiber Placement) | **no public ontology found** | — | — | — | gap | **▲-quadrant Shepard opportunity** (see §7) |
| Resin infusion (VAP / VARI) | **no public ontology found** | — | — | — | gap | ▲-quadrant; less mature than AFP though |
| NDT (ultrasonic, thermography, X-ray) | **CHAMEO** covers characterisation methods; no NDT-specific extension | [emmo-repo/domain-characterisation-methodology](https://github.com/emmo-repo/domain-characterisation-methodology) | EMMO/BFO | CC BY 4.0 | active | **already in 96-upper bootstrap**; sufficient |
| Robot capabilities / skills (KUKA R20 + LBR iiwa) | **CaSkMan + CSS + CaSk** | [CaSkade-Automation](https://github.com/CaSkade-Automation) | DIN 8580 + VDI 2860 | **unlicensed (blocker)** | active | **investigate** — needs license clarification |
| Digital thread (aerospace) | Coppin et al. (2025) "An ontology-based digital thread framework" | [HAL hal-05348319](https://hal.science/hal-05348319v1) | unspecified BFO-adjacent | open-access | recent paper; no canonical artefact | reference |
| Mid-level MfG ontology (DLR-led) | **OntOMaT** (DLR-Stade, BMBF 2022-2026) | [DLR project page](https://www.dlr.de/en/zlp/research-transfer/projects/projects-from-stade/ontomat-englisch); [CEUR Vol-4104 paper1](https://ceur-ws.org/Vol-4104/paper1.pdf) | not disclosed (BMBF-stated CC BY 4.0 outputs) | **no public artefact** as of 2026-05 | active project | **engage** — DLR-Stade + Fraunhofer IWM + Siemens + FIBRE; covers SMC/BMC + additive extrusion + FRP; sibling of SimaT inside DLR; coordinate before duplicating effort |
| Material (CF/LMPAEK + PAEK) | EMMO Materials Module + Material OWL Ontology references | [EMMO](https://github.com/emmo-repo/EMMO); CF/LMPAEK datasheet community (NIAR / TenCate) | EMMO | CC BY 4.0 | active | adopt EMMO Core (already planned) |

---

## 4. Adoption-vs-priority quadrant for the combined catalogue

Same format as the broader audit. **Adoption** axis is within-niche strong-signal; **DLR-fit** weights ZLP-Augsburg manufacturing scope.

```
                      DLR-fit / Shepard priority
                      LOW                  HIGH
                      ─────────────────────────────────────
  HIGH                │  KnowRob          │  ★ MSEO         │
                      │  IEEE 1872 OWL    │  ★ IOF Core     │
   adoption           │  CSS reference    │  ★ AAS-RDF      │
   signal             │                   │  ★ IOF-Maint    │
                      │                   │  ★ DIN 8580 ODP │
                      │                   │  ★ VDI 2860 ODP │
                      │                   │  ★ IDTA Digital │
                      │                   │     Nameplate   │
                      │                   │  ★ IDTA AI      │
                      │                   │     Nameplate   │
                      ─────────────────────────────────────
                      │  IDTA AGV         │  ◆ CaSkMan      │
   LOW                │  RACE / PERLA     │     (license)   │
                      │  NIST kit-build   │  ◆ OPC UA ODP   │
                      │                   │  ▲ AFP (none)   │
                      │                   │  ▲ Resin infu.  │
                      │                   │     (none)      │
                      │                   │  ▲ Thermoplastic│
                      │                   │     weld (CDOJP │
                      │                   │     research)   │
                      │                   │  ▼ OntOMaT      │
                      │                   │     (DLR — eng.)│
                      ─────────────────────────────────────
```

Legend:
- **★** = drop-in must-have
- **◆** = ready in shape but blocker (license / scope) before adoption
- **▲** = ZLP-domain gap; Shepard could be catalyst publisher
- **▼** = DLR-internal engagement opportunity (coordinate, don't duplicate)

---

## 5. IEEE 1872 (SUMO) ↔ IOF Core (BFO) bridging — verdict

The task framing asked whether to anchor in IEEE 1872, in IOF, in both, or to choose IOF and bridge to IEEE 1872 via a separate alignment ontology. **The verdict is clear.**

**`aidocs/semantics/96-upper-ontology-alignment.md` has already committed to BFO 2020 + IOF Core + IAO + PROV-O as the Shepard upper stack.** That commitment is grounded in the sibling DLR ontology audit's ★-quadrant findings and the NIST/OAGi/Boeing/BAM industrial backing of IOF. Reopening that choice for this audit would be incoherent.

The non-trivial follow-on question is therefore: **how should Shepard ingest CORA / IEEE 1872 vocabulary** if it wants the robotics terminology benefits without compromising the BFO root?

Two practical constraints:

1. **SUMO has no canonical OWL form.** The [srfiorini/IEEE1872-owl](https://github.com/srfiorini/IEEE1872-owl) repository explicitly notes that the OWL artefact ships a partial SUMO taxonomy (`sumo-cora.owl`) because the upstream SUMO ontology has no native OWL representation. The [meteck.org SUGOIEKAW14 paper](http://www.meteck.org/files/SUGOIEKAW14.pdf) and the [Semantic Web Journal foundational-ontologies survey](https://www.semantic-web-journal.net/system/files/swj2650.pdf) both confirm that SUMO ↔ BFO foundational mappings are partial, manual, and contested. Importing CORA's OWL form with its native `rdfs:subClassOf` chain into a BFO-rooted graph means **two parallel, incompletely aligned upper layers in the same reasoning context** — a structural defect Shepard should not accept.

2. **CORA's terminology is genuinely valuable.** "Robot", "robotic system", "robot part", and the geometric position/orientation vocabulary are well-thought-through and the *de jure* IEEE-RAS standard.

**Recommended adoption pattern: SKOS-only import of IEEE 1872 / CORA.** Treat the CORA terms as `skos:Concept`s under a `cora:` namespace, with `skos:exactMatch` / `skos:relatedMatch` to the corresponding IOF Core classes (e.g. `cora:Robot skos:relatedMatch iof:Equipment`). No `rdfs:subClassOf` axiom is emitted from `cora:*` into BFO or IOF. The reasoner sees IOF/BFO; the librarian sees CORA labels. This is the same pattern Shepard already uses for [NASA Thesaurus](https://www.sti.nasa.gov/docs/thesaurus/) and GEMET — vocabularies imported as SKOS without committing to their upper-class axioms.

Concretely:
- A `cora-skos.ttl` Shepard-side adapter (~50 lines) declares the CORA top concepts as `skos:Concept`s.
- The five-ish key bridges (`cora:Robot skos:relatedMatch iof:Equipment`, `cora:RobotPart skos:broader cora:Robot`, etc.) are curated, not derived.
- The CORA OWL artefact itself is **not** loaded into the manifest. Only the Shepard-side SKOS adapter is.

This costs ~1 day to author + ~0.5 day for the curated bridges. It preserves the BFO/IOF reasoner integrity, gives Shepard the IEEE-RAS terminology surface for free, and avoids the trap of dual-rooted upper layers.

The same pattern applies to IEEE 1872.2 (DUL-rooted), VDI 2860 (upper-agnostic so safer; can be imported directly), and KnowRob (skip entirely — academic-research scope, not the right shape for an aerospace-MES RDM).

---

## 6. AAS submodels — the Industrie 4.0 bridge

AAS (Asset Administration Shell, Plattform Industrie 4.0) is the **dominant Industrie-4.0 digital-twin metamodel** in Europe and is heavily DLR-context-aligned: KUKA, Siemens, Festo are major IDTA members and most ZLP shop-floor equipment will plausibly ship with an AAS over the next 3-5 years. AAS specifications (JSON, XML, **and RDF**) are published by [admin-shell-io](https://github.com/admin-shell-io) with RDF being natively part of the spec since IDTA's metamodel v3.

The IDTA (Industrial Digital Twin Association) publishes **submodel templates** — concrete, domain-specific schemas. **Three submodels are immediately relevant** to ZLP:

| IDTA submodel | Version + date | Maps to | Shepard angle |
|---|---|---|---|
| **Digital Nameplate for Industrial Equipment** | 02006-3-0 (Oct 2025) | KUKA R20, KUKA LBR iiwa, TPS (Tape Placement Station), MTLH (Material Tape Loading Head) | Every Shepard-imported equipment-DataObject can carry an AAS-RDF Digital Nameplate annotation. Directly closes the EN 9100 "equipment traceability" axis (sibling audit's `manufacturing-quality.md` calls this out). |
| **AI Model Nameplate** | 02060-1-0 (Feb 2025) | Trained models from the REBAR pipeline (`project_rebar_integration`) | FAIR4ML + AAS bridge. Covers TPL9 F(AI)²R model-card requirements without re-inventing the schema. |
| **Technical Data for AGV in Intralogistics** | 02047-1-0 (Mar 2025) | MFFD has no AGV today; the LBR iiwa cleat-handling cell is the closest analog. | Future-proofing only. |

The bridge between AAS submodels and capability/skill ontologies has a published design pattern: [arxiv 2307.00827 "Toward a Mapping of Capability and Skill Models using Asset Administration Shells and Ontologies"](https://arxiv.org/html/2307.00827v2) — which proposes IDTA-submodel ↔ CSS/CaSkMan ↔ ontology mappings. This is the natural place for `shepard-plugin-industrial` (per `project_drg_tooling`) to live: a plugin that ingests an AAS instance + its RDF submodels and creates a Shepard DataObject for the asset, annotated with the corresponding capability terms.

**DLR institutional participation in IDTA** was not surfaced publicly via searches. The [questions-and-answers IDTA-IO portal](http://industrialdigitaltwin.io/questions-and-answers/) lists corporate members (Siemens, KUKA, Festo) prominently but not DLR institutes by name. **Recommendation: confirm DLR-RM / DLR-BT / ZLP-Augsburg membership status with the institute liaisons before committing to AAS as a primary surface**; if no membership, joining IDTA as a research institute is a cheap institutional move and directly serves the Shepard plugin design.

---

## 7. ZLP-domain gaps Shepard should fill

Three ▲-quadrant gaps where Shepard + DLR could publish the first public artefact:

### 7.1 AFP — Automated Fiber Placement ontology

**State of the art:** No public OWL/RDF ontology surfaced in 2026-05 covering AFP process parameters (laser power, layup speed, compaction force, nip-point temperature, gap/overlap sensor data, layer-sequence number, spatial zone). The AFP community is **vocabulary-rich** (every paper uses the same terms) but **schema-poor** (no formal artefact). DLR has unique credibility here — ZLP Augsburg + DLR-Stade have authored most of the EU AFP literature of the past decade.

**Shepard role:** Co-publish `afp-process.ttl` as a domain ontology built on the upper stack already chosen (`mffd:TapeLayup rdfs:subClassOf iof:ManufacturingProcess`) with QUDT-typed process parameters, CHAMEO-typed defect characterisations, and AAS Digital Nameplate references to the AFP-cell equipment. The DRG channel descriptor schema (`measurement / device / location / symbolicName / field`) per `project_drg_tooling` is the raw vocabulary input.

**Effort:** 1-2 weeks of ontologist + ZLP-engineer review. The shepard-experiment + lumen-inspired pattern is the template.

### 7.2 Thermoplastic welding (ultrasonic + resistance + stud) ontology

**State of the art:** MSEO covers welding terminology at the IOF-stack mid-level. Tikkala et al.'s CDOJP ([ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S0736584518302126)) is the academic precursor but paywalled and without a public TTL artefact. No public ontology covers **thermoplastic welding specifically** — the LM-PAEK welding process is unique (no autoclave; in-situ consolidation by ultrasonic or resistance heating) and its sensor-stream vocabulary (horn frequency, amplitude, electrical-current-cycles, dwell-time-under-pressure) is not captured anywhere.

**Shepard role:** `mffd-welding.ttl` extending MSEO's welding submodule with thermoplastic-specific subclasses. Smaller than AFP but equally novel.

**Effort:** 3-5 days, gated on the AFP pattern.

### 7.3 Resin infusion (VAP / VARI) process ontology

**State of the art:** None public. This is genuine green-field but **less mature** than AFP — Shepard does not have a flagship resin-infusion demo today (the LUMEN seed is hot-fire test data, the MFFD seed is AFP + welding). Defer until a resin-infusion seed dataset lands.

---

## 8. Concrete bootstrap-shortlist delta

Recommended appends to `backend/src/main/resources/ontologies/ontologies-manifest.json`, on top of the sibling audit's nine entries (BFO, IAO, EMMO, CHAMEO, HDO, NFDIcore, MWO, PMDco, SSN/SOSA):

| ID | Source | Size approx. | Slot in `aidocs/semantics/96` |
|---|---|---|---|
| **mseo** | [Mat-O-Lab/MSEO MSEO_mid.ttl](https://raw.githubusercontent.com/Mat-O-Lab/MSEO/main/MSEO_mid.ttl) | ~200 KB | §2.5 optional bridges — already named; promote to "must-have alongside CHAMEO" |
| **iof-core** | [iofoundry/Core](https://github.com/iofoundry/Core) (Core.ttl) | ~150 KB | §2.2 must-have |
| **iof-maint** | [iofoundry/ontology Maintenance](https://github.com/iofoundry/ontology) | ~120 KB | new entry — EN 9100 + calibration traceability |
| **din8580-odp** | [hsu-aut/IndustrialStandard-ODP-DIN8580](https://github.com/hsu-aut/IndustrialStandard-ODP-DIN8580) (DIN8580.owl) | ~40 KB | new entry — ZLP-process taxonomy anchor |
| **vdi2860-odp** | [hsu-aut/IndustrialStandard-ODP-VDI2860](https://github.com/hsu-aut/IndustrialStandard-ODP-VDI2860) | ~25 KB | new entry — handling-operations vocab for LBR iiwa cleat work |
| **cora-skos** *(Shepard-internal)* | author in `backend/src/main/resources/ontologies/cora-skos.ttl` | ~5 KB | §5 SKOS-only adapter |

Six entries; ~540 KB; matches the precedent that the manifest absorbs 407 KB for SimaT without operator pain (sibling audit §"What surprised me" point 7). All MIT-licensed except mseo (MIT) and cora-skos (Shepard-authored, recommend CC BY 4.0 to match shepard-experiment + lumen-inspired).

TPL3 slice mapping (per `aidocs/semantics/96 §8`):
- **TPL3a (bootstrap migration)** — adds the six entries above to the load order alongside existing BFO/IAO/IOF Core.
- **TPL3b (`shepard-upper`)** — gains `cora:Robot skos:relatedMatch iof:Equipment` style curated bridges; ~10 axioms.
- **TPL3f (worked example)** — extend the `mffd:BridgeWelding` example to show `mffd:UltrasonicWelding rdfs:subClassOf mseo:JoiningProcess, hsu-din8580:Group4Joining` — demonstrating multi-source typing without dual-root conflict.

CaSkMan and AAS-RDF are **deferred to a later TPL slice** pending:
- CaSkMan license clarification (block on upstream PR adding `LICENSE` file — likely MIT-intent given the rest of the family).
- AAS-RDF + IDTA submodel ingestion is the `shepard-plugin-industrial` scope, not the bootstrap.

---

## 9. Honest caveats

- **IEEE 1872 spec is paywalled.** Conclusions about CORA structure rest on the [srfiorini/IEEE1872-owl](https://github.com/srfiorini/IEEE1872-owl) OWL implementation + the [ResearchGate preprint of the CORA paper](https://www.researchgate.net/publication/273122687_Core_Ontology_for_Robotics_and_Automation). Direct ISO/IEEE text not consulted.
- **OntOMaT (DLR-Stade BMBF project)** is publicly described but **has not released any ontology artefact as of 2026-05**. The [CEUR Vol-4104 paper1](https://ceur-ws.org/Vol-4104/paper1.pdf) describes methodology but cites no GitHub/GitLab URL. The DLR institutional credit for first-public-FRP-manufacturing-ontology is theirs to claim if they ship by 2026; Shepard should coordinate (Sven Torstrick-von der Lieth at DLR Stade per the project page) before authoring `afp-process.ttl` independently.
- **DLR participation in IEEE-RAS standards / IDTA / OAGi-IOF was not surfaced** through public search. DLR-RM (Oberpfaffenhofen) contributes [open-source ROS code](https://github.com/DLR-RM) but no IEEE-RAS Standing Committee seats or IDTA working-group memberships were found. This is the kind of "honest gap" the user should resolve with the DLR-RM and ZLP institute leadership: institutional standards-body participation is cheap visibility and is the structural fix for "Shepard adopts standards but doesn't shape them".
- **CaSkMan is unlicensed.** Without a `LICENSE` file the repo is **all-rights-reserved by default under German law**, even though the surrounding community treats it as open-source. Email Aljosha Köcher (HSU Hamburg AUT) to confirm; without that, the OWL artefact cannot legally ship inside Shepard's manifest.
- **Niche literature is dispersed.** Several research-grade ontologies (RACE, PERLA, CDOJP, the Coppin et al. 2025 aerospace digital-thread ontology) were surfaced via paper abstracts only; canonical artefacts may exist behind institutional firewalls. The audit prefers "no public artefact found" to overpromising adoption.
- **Within-niche signal is weak by absolute standards.** A 20-star MIT repository (hsu-aut umbrella) **is** canonical here. Treat the absolute numbers with the niche-relative framing of §1 — don't apply biomedical-OBO-Foundry adoption norms to industrial robotics.
- **AAS-RDF maturity varies by submodel.** The AAS metamodel itself ships RDF natively, but not every IDTA submodel template ships an OWL/Turtle render. Digital Nameplate 3.0 (Oct 2025) has known RDF; the AGV submodel's RDF status was not directly confirmed in the audit.

---

## Sources (consolidated)

### Standards bodies + foundational

- [IEEE 1872-2015 Standard for Robotics + Automation Ontologies](https://standards.ieee.org/ieee/1872/5354/)
- [IEEE 1872-2015 (IEEE Xplore)](https://ieeexplore.ieee.org/document/7084073/)
- [IEEE Standard for Autonomous Robotics Ontology (1872.2)](https://aistandardshub.org/ai-standards/ieee-standard-for-autonomous-robotics-aur-ontology/)
- [CORA paper (ResearchGate)](https://www.researchgate.net/publication/273122687_Core_Ontology_for_Robotics_and_Automation)
- [srfiorini/IEEE1872-owl](https://github.com/srfiorini/IEEE1872-owl)
- [hsu-aut/IndustrialStandard-ODP-IEEE1872-2](https://github.com/hsu-aut/IndustrialStandard-ODP-IEEE1872-2)
- [meteck.org SUGOIEKAW14 — Foundational ontology interchangeability](http://www.meteck.org/files/SUGOIEKAW14.pdf)
- [Semantic Web Journal — Foundational Ontologies meet Ontology Matching: A Survey (swj2650)](https://www.semantic-web-journal.net/system/files/swj2650.pdf)

### IOF + NIST industrial ontologies

- [Industrial Ontologies Foundry GitHub](https://github.com/iofoundry)
- [IOF Core spec](https://spec.industrialontologies.org/iof/)
- [iofoundry/Core](https://github.com/iofoundry/Core)
- [The Industrial Ontologies Foundry (IOF) Core Ontology (NIST)](https://tsapps.nist.gov/publication/get_pdf.cfm?pub_id=935068)
- [IOF-Maint — Modular Maintenance Ontology (arXiv 2404.05224)](https://arxiv.org/abs/2404.05224)
- [IOF Supply Chain Ontology — ASU Semantic Computing Lab](https://labs.engineering.asu.edu/semantics/ontology-download/iof-reference-ontologies/iof-supply-chain-structure/)
- [Modeling a Supply Chain Reference Ontology (NIST)](https://tsapps.nist.gov/publication/get_pdf.cfm?pub_id=928051)
- [NIST Robot Task Ontology Standard](https://www.nist.gov/publications/towards-robot-task-ontology-standard)
- [NIST Ontology for Kit Building](https://www.nist.gov/publications/overview-ontology-based-approach-kit-building-applications)
- [NIST Additive Manufacturing Ontology](https://github.com/iassouroko/AMontology)

### German manufacturing standards as RDF (HSU Hamburg AUT)

- [hsu-aut/Industrial-Standard-Ontology-Design-Patterns](https://github.com/hsu-aut/Industrial-Standard-Ontology-Design-Patterns) (umbrella, archived 2021)
- [hsu-aut/IndustrialStandard-ODP-DIN8580](https://github.com/hsu-aut/IndustrialStandard-ODP-DIN8580)
- [hsu-aut/IndustrialStandard-ODP-VDI2860](https://github.com/hsu-aut/IndustrialStandard-ODP-VDI2860)
- [hsu-aut/IndustrialStandard-ODP-OPC-UA](https://github.com/hsu-aut/IndustrialStandard-ODP-OPC-UA)

### Capability + skill ontologies

- [CaSkade-Automation/CaSkMan](https://github.com/CaSkade-Automation/CaSkMan)
- [CaSkade-Automation/CSS](https://github.com/CaSkade-Automation/CSS)
- [Reference model for capabilities and skills in manufacturing (De Gruyter)](https://www.degruyterbrill.com/document/doi/10.1515/auto-2022-0117/html?lang=en)
- [Toward a Mapping of Capability and Skill Models using AAS and Ontologies (arXiv 2307.00827)](https://arxiv.org/html/2307.00827v2)

### Mat-O-Lab / MSEO + welding

- [Mat-O-Lab/MSEO](https://github.com/Mat-O-Lab/MSEO)
- [MSEO_mid.ttl raw](https://raw.githubusercontent.com/Mat-O-Lab/MSEO/main/MSEO_mid.ttl)
- [Mat-O-Lab homepage](https://mat-o-lab.github.io/OrgSite/)
- [Core domain ontology for joining processes (CDOJP) — Tikkala et al.](https://www.sciencedirect.com/science/article/abs/pii/S0736584518302126)

### Robotics knowledge bases

- [KnowRob homepage](https://knowrob.org/)
- [knowrob/knowrob](https://github.com/knowrob/knowrob)
- [KnowRob 2.0 paper](https://ai.uni-bremen.de/papers/beetz18knowrob.pdf)
- [Ontology-based Approaches to Robot Autonomy (review)](https://daniel86.github.io/KER-robot-ontologies/)
- [Review of ontology-based approaches in robotics (Olivares-Alarcos et al.)](https://www.aolivaresalarcos.com/pdf/2019-ker_review.pdf)

### Asset Administration Shell + IDTA

- [admin-shell-io/aas-specs-metamodel releases](https://github.com/admin-shell-io/aas-specs-metamodel/releases)
- [Semantic AAS — Bader & Maleshkova (arXiv 1909.00690)](https://arxiv.org/pdf/1909.00690)
- [IDTA submodel template archive](https://industrialdigitaltwin.org/en/news-dates/tag/submodel-template)
- [IDTA 02006-3-0 Digital Nameplate for Industrial Equipment (Oct 2025)](https://industrialdigitaltwin.org/wp-content/uploads/2025/10/IDTA-02006-3-0-1_Submodel_Digital-Nameplate.pdf)
- [IDTA 02047-1-0 Technical Data for AGV (Mar 2025)](https://industrialdigitaltwin.org/wp-content/uploads/2025/03/IDTA-02047-1-0-Submodel_Technical-Data-for-AGV.pdf)
- [IDTA 02060-1-0 AI Model Nameplate (Feb 2025)](https://industrialdigitaltwin.org/wp-content/uploads/2025/02/IDTA-02060-1-0_Submodel_AIModelNameplate.pdf)

### DLR-internal manufacturing-ontology activity

- [DLR ZLP Augsburg standort page](https://www.dlr.de/de/zlp/ueber-uns/das-institut/standort-augsburg)
- [DLR OntOMaT project page (Stade)](https://www.dlr.de/en/zlp/research-transfer/projects/projects-from-stade/ontomat-englisch)
- [OntOMat — Towards an Ontology-based Integration (CEUR Vol-4104 paper1)](https://ceur-ws.org/Vol-4104/paper1.pdf)
- [DLR-RM GitHub organisation](https://github.com/DLR-RM)
- [DLR Robotics and Mechatronics Center](https://www.dlr.de/en/rmc)

### Digital thread + aerospace

- [Coppin et al. (2025) "An ontology-based digital thread framework" (HAL)](https://hal.science/hal-05348319v1)
- [Conceptualizing the digital thread for smart manufacturing](https://www.researchgate.net/publication/380031495_Conceptualizing_the_digital_thread_for_smart_manufacturing_a_systematic_literature_review)

### Materials + EMMO + CF/LMPAEK

- [emmo-repo/EMMO](https://github.com/emmo-repo/EMMO)
- [emmo-repo/domain-characterisation-methodology (CHAMEO)](https://github.com/emmo-repo/domain-characterisation-methodology)
- [PEEK vs PEKK vs PAEK (CompositesWorld)](https://www.compositesworld.com/articles/peek-vs-pekk-vs-paek-and-continuous-compression-molding)
- [DIN8580 → EMMO bridge figure (ResearchGate)](https://www.researchgate.net/figure/ManufacturingProcess-ontology-based-on-DIN-8580-and-its-integration-into-the-EMMO_fig2_376984242)

### Companion Shepard docs

- `aidocs/agent-findings/dlr-ontology-catalog.md` — sibling broader audit
- `aidocs/semantics/96-upper-ontology-alignment.md` — BFO 2020 + IOF Core + IAO + PROV-O anchor decision
- `aidocs/semantics/95-shacl-templates-and-individuals.md` — SHACL templates + individuals design
- `project_drg_tooling` (memory) — DRG OPC UA → Shepard bridge, AFP cell
- `project_mffd_domain_context` (memory) — MFFD process chain, DIN EN 9100, ISO AP242, CHAMEO
- `project_rebar_integration` (memory) — DLR cross-institute ML infra; FAIR4ML + AI Model Nameplate hook
