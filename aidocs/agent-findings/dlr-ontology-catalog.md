# DLR Ontology & Model Initiative Catalogue

**Author:** Ecosystem Advocate (specialised agent)
**Date:** 2026-05-21
**Scope:** DLR-maintained, DLR-coauthored, or DLR-institutionally-relevant ontologies, schemas, and model initiatives that could feed Shepard's ontology-driven UI (per `aidocs/semantics/95-shacl-templates-and-individuals.md`).

**Summary.** Identified **27 candidates** across CPACS / space / materials / energy / earth-observation / cross-cutting standards. Of these, **6 are must-have** for the bootstrap (HDO, NFDIcore, MWO, PMDco, EMMO, CHAMEO — plus already-named BFO, IAO, RO), **8 are nice-to-have** (SSN/SOSA, OEO, DCAT-AP, GeoDCAT-AP, AAS-RDF, schema.org/Aerospace, RO-Crate, NFDI4Earth), **6 require investigation** (ECSS-SWIM, AIRM/AIXM, GEMET, NASA-ATM, Space System Ontology, ESA OSMoSE), and **7 are RDF-mapping work** (CPACS, AIXM, CCSDS DoT, ISO 10303 AP242, ECSS, PLUTO/PLATO mission profiles, DLR DataFinder schemas — all XML/JSON-native, would need a Shepard-side adapter or community-built bridge).

Headline finding: **HDO (Helmholtz Digitisation Ontology)** is the single highest-leverage missing addition — DLR is a core Helmholtz member, the HMC Hub Aeronautics, Space and Transport is *physically located at DLR Cologne*, and HDO is the cross-Helmholtz mid-level ontology this fork can claim alignment with as a free interoperability win. **PMDco 3.0** and **MWO 3.0.x** are the parallel materials-science peers — both BFO-compliant, both actively maintained, both have official PURL-resolvable IRIs. Adding the trio (HDO + PMDco + MWO) to the manifest is roughly a four-hour task and unlocks alignment claims for three NFDI consortia plus Helmholtz.

---

## What I found

DLR's ontology ecosystem position is **structural participant, not primary author**. DLR institutes co-author the cross-cutting infrastructure (HMC, NFDI4Energy steering, NFDI4Ing partner, NFDI4Earth via DLR Bremen/Oberpfaffenhofen) but the canonical ontology artefacts are produced by partner institutes (FIZ Karlsruhe, RWTH Aachen, GFZ Potsdam, FZ Jülich, AKSW Leipzig). The shepard fork is therefore best positioned to **adopt** these ontologies rather than originate them.

Three concentric rings emerged:

1. **Inner ring — BFO-compliant mid-level ontologies that the German RDM community is converging on.** NFDIcore 2.0, MWO 3.0, PMDco 3.0, HDO. All four cite BFO as upper ontology, all four resolve via Helmholtz PURLs (`purls.helmholtz-metadaten.de/...`) or w3id.org. If Shepard adopts BFO + these four, it's interoperable with the entire NFDI + Helmholtz RDM stack by default.

2. **Middle ring — domain ontologies DLR institutes use directly.** OEO (energy, DLR-TT steering committee member), SimaT (already shipped, DLR-BT authored), CHAMEO (materials characterisation, EMMO domain), EMMO 1.0.0 (released Feb 2025 — major), SSN/SOSA (sensors, W3C/OGC), GeoDCAT-AP (EOC catalogues).

3. **Outer ring — domain models DLR maintains in non-RDF formats.** CPACS (XML schema, DLR-flagship), AIXM (aeronautical, DLR-ATM), ISO 10303 AP242 (STEP, DLR-DT engineering), CCSDS Dictionary of Terms (space data, DLR-RY/-IRS), DLR DataFinder schema. None have canonical RDF — adopting them in Shepard means **building the RDF mapping** as a downstream artefact.

**Surprises:**
- **CPACS has no public RDF/OWL representation** despite being 20+ years old, despite serving as DLR's "common language for aircraft design". The 2020 ResearchGate paper "Recent Advances in Establishing a Common Language for Aircraft Design with CPACS" explicitly calls CPACS "an aircraft ontology in XML form" — but no XSD→OWL transformation has been published. This is a real gap and a Shepard opportunity.
- **HDO is CC0** — most permissive ontology licence possible. Free for shepard to redistribute, modify, derive from. No licence-compatibility friction.
- **PMDco 3.0 (released 2025) breaks backward compatibility with 2.x.** Anyone adopting it now should pin v3.0 explicitly.
- **NASA Thesaurus is already in the manifest** (`nasa-thesaurus.ttl`, since 2026-05-19). This is unexpectedly forward — most German RDM platforms don't carry it.
- **SimaT is already in.** DLR-BT (Mathieu Vinot's institute) authored ontology — this is a strong signal that the shepard team already values DLR-internal ontologies and would value HDO, MWO, PMDco equally.

---

## CPACS deep-dive

CPACS is DLR's flagship aircraft-configuration schema. Maintained at `github.com/DLR-SL/CPACS` ([repo](https://github.com/DLR-SL/CPACS), [website](https://dlr-sl.github.io/cpacs-website/)). CPACS describes aircraft, rotorcraft, engines, climate impact, fleets, and missions in a hierarchical XML schema. Used in DLR's multidisciplinary aircraft design tools (TIGL geometry library, CEASIOMpy, etc.).

**RDF status: NONE.** The 2020 paper [Recent Advances in Establishing a Common Language for Aircraft Design with CPACS](https://www.researchgate.net/publication/344346475_Recent_Advances_in_Establishing_a_Common_Language_for_Aircraft_Design_with_CPACS) describes CPACS as "a general representation of an aircraft ontology and corresponding implementation in XML" — but the implementation is XSD, not OWL. The OGC Testbed-14 Engineering Report (2018) confirms: "there is currently no encoding of major aviation standards (FIXM, AIXM, WXXM) based on linked data standards such as OWL or RDF Schema" ([source](https://docs.ogc.org/per/18-035.html)).

**What this means for Shepard:**
- A `cpacs-skos.ttl` adapter ontology — defining the top-level CPACS concepts (aircraft, wing, engine, mission, fleet) as `skos:Concept`s — could be hand-built in a sprint and would be a **genuine novel contribution**, not just a re-bundle. The shepard-experiment + lumen-inspired pattern is the template.
- A CPACS XML instance is a natural fit for a Shepard `DataObject` (kind: structured-doc) with semantic annotations linking elements to the CPACS-SKOS vocabulary. The MFFD use case currently has no aircraft-configuration story; CPACS could be the bridge.
- DLR-SL maintains CPACS publicly under [DLR's organisation](https://github.com/DLR-SL). Reaching out to coordinate a CPACS-SKOS publication would land Shepard squarely in the DLR-internal interop story.

**Recommended priority: investigate-further** (the artefact doesn't exist; producing one is a 1-2 week task, not a "drop the .ttl in" task). High strategic value, medium effort.

Related DLR projects without RDF: **TIGL Geometry Library** ([github.com/DLR-SC/tigl](https://github.com/DLR-SC/tigl)) — geometry computation, no semantic layer needed for shepard. **AIXM** — used by DLR-FL for ATM research; XML-native; an RDF representation exists at NASA's ATM Ontology and AIRM but neither is DLR-maintained.

---

## Catalogue

| ID | Name + version | Source URL | Sponsor / Institute | Licence | Maint. | Last rel. | Scope | RDF readiness | DLR rel. | Shepard priority | Why for Shepard |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **bfo** | Basic Formal Ontology 2.0 | [github.com/BFO-ontology/BFO](https://github.com/BFO-ontology/BFO) | IFOMIS / BFO consortium | CC BY 4.0 | active | 2020 (v2020) | Upper-level ontology used by 130+ ontology efforts; ISO/IEC 21838-2:2021. | Native OWL (Turtle avail.) | 5 | **must-have** | Upper-ontology spine. Named in design doc; not yet in manifest. |
| **iao** | Information Artifact Ontology | [github.com/information-artifact-ontology/IAO](https://github.com/information-artifact-ontology/IAO) | OBO Foundry | CC BY 4.0 | active | 2024 | BFO-aligned info artefacts (documents, datasets, identifiers). | Native OWL | 5 | **must-have** | Named in design doc. Pairs with BFO. |
| **emmo** | EMMO 1.0.0 | [emmo-repo.github.io](https://emmo-repo.github.io/) | EMMC consortium | CC BY 4.0 | active | **Feb 2025** | Mid-level multi-perspective ontology for materials science. | Native Turtle ([emmo.ttl](https://github.com/emmo-repo/EMMO/blob/master/emmo.ttl)) | 4 | **must-have** | 1.0.0 is 2025 milestone. CHAMEO depends on it. |
| **chameo** | CHAMEO 1.0.0 | [emmo-repo.github.io/domain-characterisation-methodology](https://emmo-repo.github.io/domain-characterisation-methodology/) | EMMC / NanoMECommons | CC BY 4.0 | active | 2024 | EMMO-aligned methodology for materials characterisation (CEN CWA 17815). | Native OWL | 4 (NDT, materials testing) | **must-have** | Named in design doc. Closes materials-characterisation gap for MFFD. |
| **ro** | OBO Relation Ontology | [purl.obolibrary.org/obo/ro.owl](http://purl.obolibrary.org/obo/ro.owl) | OBO Foundry | CC0 | active | continuous | Cross-cutting relations (`part_of`, `derives_from`, `has_input`). | Native — shipped | 5 | shipped | Already in manifest. |
| **hdo** | Helmholtz Digitisation Ontology | [codebase.helmholtz.cloud/hmc/hmc-public/hob/hdo](https://codebase.helmholtz.cloud/hmc/hmc-public/hob/hdo) | HMC (DLR-Cologne hub) | **CC0** | active | 2 releases, 146 commits since 2022 | Mid-level ontology for digital assets/processes across Helmholtz; OBO-conventions-aligned. | Native OWL | **5** (HMC hub at DLR Cologne) | **must-have** | Direct DLR-institutional alignment. CC0 makes redistribution trivial. |
| **nfdicore** | NFDIcore 2.0 | [ise-fizkarlsruhe.github.io/nfdicore/2.0.0/](https://ise-fizkarlsruhe.github.io/nfdicore/2.0.0/) | FIZ Karlsruhe (ISE) / NFDI | CC BY 4.0 | active | 2024 | BFO-compliant mid-level ontology for NFDI consortia; mapped to schema.org. | Native OWL | 4 (DLR is multi-NFDI partner) | **must-have** | Cross-NFDI interop spine. |
| **mwo** | NFDI-MatWerk Ontology 3.0.x | [github.com/ISE-FIZKarlsruhe/mwo](https://github.com/ISE-FIZKarlsruhe/mwo); PURL `purls.helmholtz-metadaten.de/mwo/mwo.owl/3.0.1` | FIZ Karlsruhe + NFDI-MatWerk | CC BY 4.0 | active | 2025 | BFO-compliant modular ontology for materials science; extends NFDIcore. | Native OWL | 4 (DLR-BT materials testing) | **must-have** | Pairs with SimaT (already shipped). MFFD CFRP. |
| **pmdco** | PMD Core Ontology 3.0.0 | [github.com/materialdigital/core-ontology](https://github.com/materialdigital/core-ontology); IRI `w3id.org/pmd/co` | Platform MaterialDigital (BMBF) | CC BY 4.0 | active | **2025** | BFO-aligned mid-level for MSE process chains; reuses RO, IAO, OBI, PROV-O. | Native Turtle ([pmdco.ttl](https://github.com/materialdigital/core-ontology/blob/main/pmdco.ttl)) | 4 (MFFD process chain) | **must-have** | Natural overlay for MFFD process-step DataObjects. |
| **metadata4ing** | Metadata4Ing 1.4.0 | [w3id.org/nfdi4ing/metadata4ing/](https://w3id.org/nfdi4ing/metadata4ing/) | NFDI4Ing (RWTH Aachen) | CC BY 4.0 | active | Dec 2025 | Engineering-research process modelling; PROV-O extension. | Native Turtle — shipped | 4 | shipped | Already in manifest. |
| **simat** | SiMaT 1.0.0 | `w3id.org/simat/` | **DLR-BT (Mathieu Vinot)** | CC BY 4.0 | active | 2026-05-19 | DLR materials testing + simulation ontology. | Native Turtle — shipped | 5 | shipped | Already in manifest. |
| **oeo** | Open Energy Ontology | [github.com/OpenEnergyPlatform/ontology](https://github.com/OpenEnergyPlatform/ontology) | OEP / NFDI4Energy / **DLR-TT (Hoyer-Klick steering)** | CC BY 4.0 | active | continuous | Domain ontology for energy-systems analysis; standard for energy-scenario annotation. | Manchester OWL syntax (Turtle release artefacts) | **5** (DLR-TT steering) | nice-to-have | Strong DLR-internal stewardship; on-mission for DLR-VE/DLR-TT instances. |
| **sosa-ssn** | SOSA + SSN (W3C/OGC) | [w3.org/TR/vocab-ssn/](https://www.w3.org/TR/vocab-ssn/) | W3C + OGC | W3C Doc | stable | 2017 (ed. 2023) | Sensor / Observation / Sample / Actuator ontology. | Native OWL | 4 (every shepard timeseries is an SSN Observation) | **must-have** (upgrade to) | **Right semantic shape for shepard timeseries**. `tsref:point` rdf-types as `sosa:Observation`. Resolves 5-tuple ontology gap. |
| **schema-org** | schema.org core | [schema.org](https://schema.org/) | W3C / Google et al. | CC BY-SA 3.0 | active | continuous | Web-vocab metadata; required by RO-Crate. | Native — shipped | 3 | shipped | Already in manifest. |
| **dcterms** | DCMI Metadata Terms | [dublincore.org](https://www.dublincore.org/specifications/dublin-core/dcmi-terms/) | DCMI | CC BY 4.0 | stable | 2020-01-20 | FAIR baseline metadata terms. | Native — shipped | 3 | shipped | Already in manifest. |
| **prov-o** | W3C PROV-O | [w3.org/ns/prov-o.ttl](https://www.w3.org/ns/prov-o.ttl) | W3C | W3C Doc | stable | Apr 2013 | Provenance ontology. | Native — shipped | 5 (PROV1 audit-trail) | shipped | Already required. |
| **dcat-ap** | DCAT-AP 3.0 | [semiceu.github.io/DCAT-AP](https://semiceu.github.io/DCAT-AP/releases/3.0.0/) | SEMIC / EU JRC | CC BY 4.0 | active | 2023 | EU data-catalogue application profile of DCAT. | Native RDF | 3 (Unhide harvest) | nice-to-have | Aligns Shepard exports with EU portals + Helmholtz Databus. |
| **geodcat-ap** | GeoDCAT-AP | [github.com/SEMICeu/GeoDCAT-AP](https://github.com/SEMICeu/GeoDCAT-AP) | SEMIC / OGC | CC BY 4.0 | active | 2024 | Geospatial extension of DCAT-AP; ISO 19115 bridge. | Native RDF | 4 (DLR-EOC catalogues) | nice-to-have | EOC publishes STAC + GeoDCAT-AP. |
| **ro-crate** | RO-Crate 1.2 | [researchobject.org/ro-crate](https://www.researchobject.org/ro-crate/specification/1.2/) | Research Object Alliance | Apache 2.0 / CC BY | active | 2024 | JSON-LD-on-schema.org packaging for research outputs. | Native JSON-LD | 4 (Collection export) | nice-to-have | Partially in scope per Unhide plugin design. |
| **gemet** | GEMET Multilingual Environmental Thesaurus | [eea.europa.eu/help/glossary/gemet-environmental-thesaurus](https://www.eea.europa.eu/help/glossary/gemet-environmental-thesaurus) | EEA | EU re-use | stable | continuous | Multilingual environmental thesaurus; INSPIRE-aligned. | Native SKOS | 3 (DLR-EOC, NFDI4Earth) | nice-to-have | Pairs with GeoDCAT-AP for EO instances. |
| **aas-rdf** | AAS — Semantic | [arxiv:1909.00690](https://arxiv.org/abs/1909.00690) | Plattform Industrie 4.0 + community | mixed | active | 2024 | RDF/OWL representation of AAS submodels; SHACL shapes. | Native OWL (community-derived) | 3 (MFFD I4.0) | investigate | Named in shepard-plugin-aas design. |
| **nasa-thesaurus** | NASA Thesaurus 2024 | [sti.nasa.gov/docs/thesaurus/](https://www.sti.nasa.gov/docs/thesaurus/) | NASA STI | Public domain | active | 2024 | ~18,400 aerospace/engineering concepts. | SKOS — shipped (stub + fetch) | 4 | shipped | Already in manifest. |
| **time** | W3C Time Ontology | [w3.org/2006/time](https://www.w3.org/2006/time) | W3C | W3C Doc | stable | 2017 | Temporal intervals + instants. | Native — shipped | 4 | shipped | Already in manifest. |
| **qudt** | QUDT Units v2.1 | [qudt.org](http://qudt.org/) | QUDT consortium | CC BY 4.0 | active | continuous | Quantity kinds, units, dimensions. | Native Turtle — shipped | 4 | shipped | Already in manifest. |
| **om-2** | OM-2 Units 2.0 | [ontology-of-units-of-measure.org](http://www.ontology-of-units-of-measure.org/) | Wageningen UR | CC BY 4.0 | stable | OM 2.0 | Alternative engineering-units ontology. | Native Turtle — shipped | 3 | shipped | Already in manifest. |
| **geosparql** | OGC GeoSPARQL 1.0 | [opengis.net/ont/geosparql](http://www.opengis.net/ont/geosparql) | OGC | OGC Open Data | stable | 2012 | Geospatial geometry vocab for SPARQL. | Native — shipped | 3 | shipped | Already in manifest. |
| **foaf** | FOAF | [xmlns.com/foaf/spec/](http://xmlns.com/foaf/spec/) | FOAF Project | CC BY 1.0 | stable | 2014 | Persons / orgs / accounts. | Native — shipped | 3 | shipped | Already in manifest. |
| **nfdi4earth-ont** | NFDI4Earth Ontology + Knowledge Hub | [knowledgehub.nfdi4earth.de](https://knowledgehub.nfdi4earth.de/) | NFDI4Earth (GFZ Potsdam + DLR partners) | open | active | 2024 | DCAT + FOAF + PROV-O reuse for Earth System Science; SPARQL endpoint. | Native RDF | 3 (DLR-Bremen / Oberpfaffenhofen EO) | nice-to-have | Aligns shepard-EO instances with NFDI4Earth. |
| **ecss-swim** | ECSS Software Information Model | research artefact ([ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S0273117722003295)) | ESA + research community | restricted | research | 2022 | OWL/RDF representation of ECSS software-engineering terms. | Research-grade OWL (not canonical) | 4 (DLR space institutes) | investigate | Not formally released. |
| **space-system-ontology** | OSMoSE — ESA Space System Ontology | [indico.esa.int/event/310/](https://indico.esa.int/event/310/) | ESA + DLR-RY participation | unclear | dormant? | 2019 workshop | ECSS + SAVOIR + CCSDS-EDS unified ontology. | Workshop-stage | 4 | investigate | DLR-RY participated; post-2019 status unclear. |
| **aixm-airm** | AIRM / AIXM Ontology | AIRM.aero + community OWL derivations | EUROCONTROL + NASA + research | mixed | active | 2024 | RDF representations of aeronautical exchange model. | Community OWL (NASA-ATM canonical NASA-side) | 3 (DLR-FL ATM research) | investigate | Domain-specific to DLR-FL. |
| **ccsds-dot** | CCSDS Dictionary of Terms (DoT) — SANA registry | [sanaregistry.org](https://sanaregistry.org/) | CCSDS | open | active | Mar 2024 | Spacecraft data semantics + Electronic Data Sheets. | XML/JSON registry — **no RDF** | 4 (DLR satellite missions) | investigate (mapping work) | Would need shepard-side `ccsds-skos.ttl` adapter. |
| **cpacs** | CPACS XML schema | [github.com/DLR-SL/CPACS](https://github.com/DLR-SL/CPACS) | **DLR-SL flagship** | open | active | continuous | DLR aircraft-configuration schema since 2005. | XSD-only — **no RDF** | **5** (DLR flagship) | investigate (mapping work) | Highest strategic value DLR-side; needs CPACS-SKOS adapter (~2 weeks). |
| **iso-10303-ap242** | ISO 10303 STEP AP242 | ISO standard | ISO | proprietary | active | continuous | Model-based 3D engineering. | EXPRESS schema — **no canonical RDF** | 3 | probably-skip | Community efforts exist; no canonical artefact. |
| **inspire** | INSPIRE Themes / Core Vocabularies | [knowledge-base.inspire.ec.europa.eu](https://knowledge-base.inspire.ec.europa.eu/) | EU INSPIRE | EU re-use | stable | 2007+ | Geospatial themes; bridged via GeoDCAT-AP. | Bridged via GeoDCAT-AP | 3 | probably-skip | Indirect coverage via GeoDCAT-AP. |
| **lumen-inspired** | LUMEN-Inspired Hotfire Test Ontology | shepard internal | DLR-shepard fork | CC BY 4.0 | active | 2026-05-19 | Showcase ontology for LUMEN seed. | Native Turtle — shipped | 5 | shipped | Already in manifest. |
| **shepard-experiment** | Shepard Experiment Ontology | shepard internal | DLR-shepard fork | CC BY 4.0 | active | 2026-05-17 | Generic experiment lifecycle + manufacturing terms. | Native Turtle — shipped | 5 | shipped | Already in manifest. |

---

## Opportunities

Ordered by leverage × feasibility.

### 1. Add the BFO-stack bundle: BFO + IAO + HDO + NFDIcore + MWO + PMDco + EMMO + CHAMEO (effort: ~6 hours)

Already named in the design doc: BFO, IAO, EMMO, CHAMEO. Missing: **HDO, NFDIcore, MWO, PMDco** — the BFO-compliant mid-level ontologies the German RDM community has converged on. Adding them is a one-sitting task: download each canonical TTL/OWL, drop into `backend/src/main/resources/ontologies/`, append manifest entry with SHA-256 + size, mark `required: false`. The manifest pattern is established by the existing 14 entries; pure operator-comfort work.

This single change moves Shepard's "ontology coverage" claim from "we ship W3C / DLR-specific basics" to "we ship the full German NFDI + Helmholtz mid-level stack". Direct alignment claim for HMC, NFDI4Ing, NFDI-MatWerk, NFDI4Energy, Platform MaterialDigital.

### 2. Build the CPACS-SKOS adapter ontology (effort: 1-2 weeks)

CPACS is DLR-flagship and has **no** public RDF representation. A `cpacs-skos.ttl` (top-level concepts as `skos:Concept`s, aligned to BFO via HDO) would be:
- A **genuine novel contribution** (not just a re-bundle).
- A direct DLR-internal interop story.
- A bridge between MFFD (composite manufacturing) and DLR aircraft-design.

The shepard-experiment + lumen-inspired pattern is the template. Could be authored by Mathieu Vinot's institute (DLR-BT, SimaT authors) or coordinated with DLR-SL.

### 3. Upgrade timeseries semantics to SSN/SOSA (effort: 3-5 days)

The 5-tuple `{measurement, device, location, symbolicName, field}` is currently semantically opaque. SSN/SOSA gives every Shepard timeseries point a natural `sosa:Observation` rdf-type, with `sosa:madeBySensor` (device), `sosa:observedProperty` (measurement), `sosa:hasFeatureOfInterest` (location). Pairs with the planned ts-appId migration (`aidocs/platform/87`) — when each TS channel gets an appId, that appId can rdf-type as `sosa:Sensor` and carry full SSN/SOSA metadata in the semantic store.

This is the **structural fix for the 5-tuple problem at the semantic layer**, not just the API layer.

### 4. Wire HMC Hub Aeronautics, Space and Transport contact (effort: 1 conversation)

The HMC Hub AST is **physically located at DLR Cologne** and co-publishes HDO. Listing Shepard in their inventory of FAIR tooling is free visibility and an institutional signal.

### 5. Adopt PMDco process-chain modelling for MFFD (effort: aligned with seed work)

PMDco 3.0's Process/Structure/Properties triad is the natural overlay for the MFFD AFP → ultrasonic welding → resistance welding → stud welding chain. Each process-step DataObject can carry a `pmdco:Process` annotation, with input/output materials linked via `pmdco:hasInput` / `pmdco:hasOutput`. This is the **specific concrete demonstration** that the MFFD use case is BFO-compliant.

---

## Ideas

- **CPACS instance as a Shepard Collection.** A complete CPACS XML file (aircraft + missions + fleet) imported as a Collection with one DataObject per top-level CPACS element. The CPACS-SKOS adapter is the semantic glue. Demonstrates "Shepard handles full aircraft configuration data" — no German-RDM platform currently does this.
- **AAS submodels as Shepard DataObjects.** AAS submodels are JSON-LD-friendly (per Semantic AAS work); each AAS submodel imports as a DataObject with semantic annotations linking to `aas:` IRIs. MFFD Industrie 4.0 / digital-twin angle.
- **OEO-annotated energy-system DataObjects for DLR-VE.** A DLR-VE instance could ship with OEO as primary annotation vocabulary — no change to core needed, just operator-configuration via the N1c2 endpoint to enable OEO. The case for shipping operator-selectable ontology bundles at instance level: each DLR institute picks its own primary domain ontology.
- **HDO as the "house ontology" for cross-Shepard interop.** When two DLR-Shepard instances need to share data (BT and RY), HDO gives them a shared mid-level vocabulary without committing either to MWO vs PMDco vs OEO.
- **CHAMEO + NDT scan annotation.** Every NDT (ultrasonic, X-ray) scan file in MFFD is a CHAMEO `Characterisation`. Annotate the file DataObject with `chameo:hasCharacterisationMethod` → the lab's specific CHAMEO subclass.

---

## Real-world impact

| Initiative | Direct beneficiary at DLR | What unlocks |
|---|---|---|
| HDO + NFDIcore + MWO + PMDco + EMMO + CHAMEO bundle | DLR-BT (Vinot, MFFD), DLR-FA, DLR-WF | BFO-compliance claim. Interop with HMC, NFDI-MatWerk, PMD without per-instance config. |
| CPACS-SKOS adapter | DLR-SL, DLR-SY, DLR-AS | First public CPACS RDF representation. DLR-flagship integration story. |
| SSN/SOSA timeseries upgrade | Every Shepard instance | Closes the 5-tuple semantic gap. Pairs with ts-appId migration. |
| OEO bundle | DLR-VE, DLR-TT (Hoyer-Klick steering) | Energy-systems use case becomes plug-and-play. |
| GeoDCAT-AP + NFDI4Earth + GEMET | DLR-EOC Oberpfaffenhofen | EO catalogue interop. NFDI4Earth Knowledge Hub indexing. |
| CCSDS-DoT adapter | DLR-RY, DLR-IRS, DLR-OS | PLUTO + Compass + EnMAP mission-data interop with NASA / ESA / CCSDS. Mapping work. |
| AAS-RDF bundle | DLR-BT (MFFD), ZLP Augsburg | Digital-twin / Industrie 4.0 alignment for manufacturing demos. |

---

## Gaps & blockers

- **CPACS has no canonical RDF.** Real gap. DLR flagship, 20+ years old, XSD-only. Shepard could fill this — but the artefact doesn't exist today.
- **PLUTO mission-profile ontology is not public.** The Welzmüller et al. (2024) paper (`elib.dlr.de/215120`) is referenced in CLAUDE.md but I could not retrieve it via the public elib search. **Recommendation: pull the PDF from DLR elib directly and reconcile this catalogue's PLUTO row.**
- **CCSDS Dictionary of Terms is XML-only.** SANA registry treats DoT as "ontology" in CCSDS terminology but it's not OWL. Shepard-side `ccsds-skos.ttl` adapter could be built but substantial (DoT is large and growing).
- **ECSS-SWIM (2022 paper) is research-grade, not canonical.** Citable but not maintainable as a Shepard bundle.
- **ESA OSMoSE (Space System Ontology) appears dormant** — last public activity was the 2019 ESTEC workshop. DLR-RY participated. Status unclear; no 2024+ release found.
- **HMC Hub Aeronautics, Space and Transport publishes coordination services, not ontology artefacts directly.** Published artefact is HDO itself (cross-Helmholtz). Aeronautics-specific HDO extensions may be in development but were not surfaced publicly. **Recommendation: reach out to the hub for a list of aeronautics-specific HDO extensions.**
- **PMDco 3.0 is not backward-compatible with 2.x.** If Shepard pins 3.0, downstream consumers on 2.x can't interop without an alignment file. Pin v3.0 explicitly and document the boundary.

---

## What surprised me

1. **HDO is CC0.** Among major mid-level ontologies, only RO and HDO are CC0 in this catalogue. HDO is the legally cleanest possible addition — no CC BY attribution chain to maintain.
2. **PMDco 3.0 dropped in 2025.** Genuinely fresh material. Shepard adopting v3.0 within months of release would be early-mover positioning in the materials-RDM community.
3. **DLR-TT has steering-committee representation on OEO** (Carsten Hoyer-Klick). Concrete DLR-internal ontology authorship not on the BT/MFFD radar but meaningful for the broader fork story.
4. **The shepard fork already ships SimaT** — a DLR-BT-authored ontology — but does not ship HDO. Asymmetry: shepard curates DLR-internal material but hasn't reached out to the immediate Helmholtz peer layer. Closing that asymmetry is the single most cost-effective move.
5. **The NFDI consortia have converged on BFO.** All four NFDIcore-aligned ontologies (NFDIcore, MWO, PMDco, NFDI4DS) inherit from BFO. German RDM community has effectively chosen its upper ontology. Shepard not bundling BFO yet is a planning gap.
6. **CCSDS officially treats its Dictionary of Terms as "ontology"** ("In CCSDS documentation, a vocabulary is considered the same as an ontology"). Terminology alignment that doesn't reflect RDF readiness, but suggests if Shepard built a CCSDS-SKOS bridge, CCSDS would consider it native.
7. **SimaT already imports 407 KB of TTL on every shepard startup.** If 407 KB is fine for SimaT, the manifest can absorb HDO, MWO, PMDco, EMMO without operator pain. The bundle-size objection is already disproven by what ships today.

---

## Recommended bootstrap delta (concrete next step)

Append to `backend/src/main/resources/ontologies/ontologies-manifest.json`, in this order (after `metadata4ing`, before `simat`):

1. `bfo` — BFO 2.0 (CC BY 4.0, ~150 KB)
2. `iao` — Information Artifact Ontology (CC BY 4.0)
3. `emmo` — EMMO 1.0.0 (CC BY 4.0, Feb 2025 — pin to 1.0.0)
4. `chameo` — CHAMEO 1.0.0 (CC BY 4.0)
5. `hdo` — Helmholtz Digitisation Ontology (**CC0** — best-in-class licence)
6. `nfdicore` — NFDIcore 2.0 (CC BY 4.0)
7. `mwo` — NFDI-MatWerk Ontology 3.0.x (CC BY 4.0)
8. `pmdco` — PMD Core Ontology 3.0.0 (CC BY 4.0, pin v3.0)
9. `sosa-ssn` — W3C/OGC SSN+SOSA (W3C Doc Licence)

All nine fit the existing manifest pattern (canonicalUrl, license, sha256, sizeBytes, required: false). Total effort: one PR. Total semantic coverage gain: **the entire German NFDI + Helmholtz BFO-aligned stack**, plus the W3C sensor ontology that resolves the timeseries semantic gap.

---

## Sources

- [CPACS GitHub (DLR-SL)](https://github.com/DLR-SL/CPACS)
- [CPACS website](https://dlr-sl.github.io/cpacs-website/)
- [Recent Advances in Establishing a Common Language for Aircraft Design with CPACS (2020)](https://www.researchgate.net/publication/344346475_Recent_Advances_in_Establishing_a_Common_Language_for_Aircraft_Design_with_CPACS)
- [OGC Testbed-14: Semantically Enabled Aviation Data Models](https://docs.ogc.org/per/18-035.html)
- [HMC Hub Aeronautics, Space and Transport](https://helmholtz-metadaten.de/en/aeronautics/uebersicht)
- [Helmholtz Digitisation Ontology (HDO) — codebase.helmholtz.cloud](https://codebase.helmholtz.cloud/hmc/hmc-public/hob/hdo)
- [NFDIcore 2.0 documentation](https://ise-fizkarlsruhe.github.io/nfdicore/2.0.0/)
- [NFDIcore 2.0 paper (arXiv 2410.01821)](https://arxiv.org/abs/2410.01821)
- [NFDI-MatWerk MWO GitHub](https://github.com/ISE-FIZKarlsruhe/mwo)
- [NFDI-MatWerk MWO documentation](https://ise-fizkarlsruhe.github.io/mwo/)
- [PMD Core Ontology GitHub](https://github.com/materialdigital/core-ontology)
- [PMD Core Ontology 3.0 docs](https://materialdigital.github.io/core-ontology/)
- [EMMO 1.0.0 release announcement](https://materialsmodelling.com/emmov1-0-0/)
- [EMMO GitHub](https://github.com/emmo-repo/EMMO)
- [CHAMEO GitHub](https://github.com/emmo-repo/domain-characterisation-methodology)
- [CHAMEO 1.0.0 release](https://materialsmodelling.com/chameo-release-v1-0-0/)
- [BFO 2.0 GitHub](https://github.com/BFO-ontology/BFO)
- [Open Energy Ontology GitHub](https://github.com/OpenEnergyPlatform/ontology)
- [OEO Steering Committee (DLR-TT membership)](https://openenergyplatform.org/ontology/oeo-steering-committee/)
- [Metadata4Ing GitHub Pages (NFDI4Ing)](https://nfdi4ing.pages.rwth-aachen.de/metadata4ing/metadata4ing/)
- [W3C Semantic Sensor Network Ontology](https://www.w3.org/TR/vocab-ssn/)
- [SOSA arxiv paper](https://arxiv.org/abs/1805.09979)
- [Semantic Asset Administration Shell (arxiv 1909.00690)](https://arxiv.org/abs/1909.00690)
- [DCAT-AP 3.0](https://semiceu.github.io/DCAT-AP/releases/3.0.0/)
- [GeoDCAT-AP](https://github.com/SEMICeu/GeoDCAT-AP)
- [RO-Crate specification 1.2](https://www.researchobject.org/ro-crate/specification/1.2/)
- [Space System Ontology Workshop 2019 (ESA ESTEC)](https://indico.esa.int/event/310/)
- [ECSS Software Information Model ontology paper](https://www.sciencedirect.com/science/article/abs/pii/S0273117722003295)
- [CCSDS SANA registry](https://sanaregistry.org/)
- [NASA Thesaurus](https://www.sti.nasa.gov/docs/thesaurus/)
- [NFDI4Earth Knowledge Hub](https://knowledgehub.nfdi4earth.de/)
- [DLR ZLP Augsburg (Center for Lightweight Production Technology)](https://www.dlr.de/zlp/en/desktopdefault.aspx/tabid-7824/)
- [shepard at Helmholtz Research Software Directory](https://helmholtz.software/software/shepard)
- [GEMET Thesaurus](https://www.eea.europa.eu/help/glossary/gemet-environmental-thesaurus)
- [DLR EOC EOmetadataTool (GitHub)](https://github.com/dlr-eoc/EOmetadataTool)
