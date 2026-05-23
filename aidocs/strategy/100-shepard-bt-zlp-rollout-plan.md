---
title: Shepard rollout plan across DLR BT / ZLP Augsburg cells
stage: feature-defined
last-stage-change: 2026-05-23
audience: strategy, contributor, thesis-substrate
---

# Shepard rollout plan — BT / ZLP Augsburg cells

**Lead use case:** MFFD AFP research. **Generalisation target:** every cell + technology in the BT/ZLP capabilities inventory [@krebsInventur2025] (Krebs F. 2025-04-18, 31 slides).

## §1 The portfolio (from the 2025-04-18 inventory)

ZLP Augsburg has **9 named cells/Anlagen + ~8 cross-cutting technologies + ~17 instrumentation sources**, all sharing the **KUKA robot + OPC UA + large-CFRP-structure pattern** that the MFFD/IPRO work already runs through.

| Cell / Anlage | Floor-plan code | Primary capability | OPC UA today | MFFD-similarity |
|---|---|---|---|---|
| Fiberplacement-Zelle | **FPZ** | CFRP layup + integrated laser heating for high-T thermoplastics — **MFFD AFP origin** | Yes (TPZ.R10 wire confirmed via IPRO Grafana 2017-11-06) | ★★★★★ |
| Thermoplast-Zelle | **TPZ** | 240 kg KUKA + high-T hand + WKP 4400S heated press — integrated control | Yes (IPRO TODO confirms) | ★★★★★ |
| Inline-Qualitätssicherungszelle | **IQZ** | NDT cell — Laser ultrasound + Air ultrasound + Thermography + NDI extensions | Likely (KUKA-based) | ★★★★ |
| Technologie-Erprobungszelle | **TEZ** | 2 × 240 kg KUKA on common linear axis — multi-robot tech-proving | Likely | ★★★★ |
| Multifunktionale Zelle | **MFZ** | 30×12×6 m, **6 robots** (3×270 kg + 3×120 kg), splittable into 4 sectors for process chains | Likely | ★★★ |
| Heißpresse | (with TPZ) | High-temperature press | Embedded in TPZ | ★★★ |
| Duromer-Ofen + Thermoplast-Ofen | (Öfen) | Curing ovens (autoclave-class enclosure visible) | TBD | ★★ |
| Cutterzentrum | — | ZÜND 3200 mm cutter, paternoster buffer, **explicit OPC UA intranet connection**, **RFID transport pallets** | **Yes (confirmed by inventory)** | ★★ |
| Wasserstrahlanlage | — | 6000 × 4000 × 500 mm work area, 3600 bar abrasive + pure waterjet | TBD | ★ |
| CoBots / Kleinrobotik + AGV | — | Collaborative human-robot + mobile platforms | UR/KUKA mixed | ★ |

Cross-cutting **technologies** (instrumentation that flows across cells):

- **Laser Excited Acoustics (LEA)** — next-gen US testing, eyesafe pulsed laser + optical microphone (no water)
- **3D mapped Thermography** — fast measurement of large structures mapped to CAD (already applied to MFFD fuselage @ 0.3 Hz per slide 18)
- **3D Geometrie-Vermessung** — Structured Light, ZIVID camera, Leica TScan + AT901 lasertracker, Soll-Ist-Vergleich with CAD
- **3D Profilometer on-the-fly** — AFP head: Heat Source + Camera + Laser Line Source + Consolidation Roller (page 20 — directly the MFFD AFP signal chain)
- **Autonome Produktion + Inline-Qualitätssicherung** — closed-loop process integration
- **Automated Defect Recognition** — "Who examines 17,000 m of tape scans?" → AI assist (page 21)

The **integration target** (page 22) is *Produktionsintegrierte QS am Beispiel Panel Produktion*: an end-to-end process chain Stringerablage → Skinablage → Vakuumaufbau → Aushärten → Einmessen → Best-Fit Analyse → Spant-integration → NDI → Weiterverarbeitung, each step feeding a *Datenerfassung, -speicherung und -auswertung, DigTwin* layer. **That data layer is Shepard.**

The **vision slide** (page 23, Krebs F. 2025-02-21) positions Shepard explicitly: yellow horizontal bar labelled "Digital integration by process chain spanning **data management system**" with the Shepard wordmark, sitting between *Multi-domain applied research with physical artefacts* (top) and *Domain-oriented digital tools for acceleration and validation of concept development* (bottom). Five vertical columns: Structural mechanics / Materials / Design,Process / **Production** / Ground/Flight Test. Shepard is the cross-column substrate.

**Sibling axis (out of scope here).** The DIVA project — *Drone Integration via AAS*, a DLR-internal LuFo VII-2 consortium where BT/ZLP contributes the AAS repository (Shepard) — is a **parallel application axis on the same substrate**, not a wave of this manufacturing rollout. UAS / operational-airspace work runs alongside the cell-by-cell manufacturing waves below; see `aidocs/strategy/101-diva-project-context.md` for the project shape, work-packages, and consortium [@krebsDIVA2026].

## §2 Rollout sequence (waves)

Ordered by **MFFD-similarity** (highest first), with each wave consolidating learnings before the next:

### Wave 0 — MFFD baseline (in flight / partially shipped, 2025-2026)

- **FPZ AFP layup** (MFFD): live cube3 import via v15.x; ~8 DataObjects landed; substrate validated
- **MFFD fuselage 3D thermography** (slide 18): file artefacts with CAD-mapped overlay; thermal-trace visualisation = Trace3D view recipe (task #142, M1-VIEWS-AS-SHAPES-WAVE)
- **IPRO TPZ.R10 telemetry** (2017): historical KUKA + OPC UA + KIBID precedent (see `aidocs/strategy/86 §3-§4`)

**Status**: lead use case running. Generalisation patterns evidenced.

### Wave 1 — Same-domain family (next 2-4 weeks; minimal new code)

Three cells share MFFD's instrumentation + workflow shape:

- **TPZ (Thermoplast-Zelle)** — KUKA + heated press + thermoplastic processing. Re-uses the FPZ ingest path. OPC UA already wired (per IPRO).
- **IQZ (Inline-Qualitätssicherungszelle)** — Laser US + Air US + Thermography. Files-with-metadata pattern; thermography mapped to CAD is already a Trace3D-class recipe.
- **FPZ extensions** beyond the MFFD AFP slot — Other process variants on the same cell, low marginal cost.

**New backend code required**: zero in this wave (the v15 importer pattern + existing payload kinds cover it). New shape templates: per-cell SHACL templates per `aidocs/semantics/95` Part 2 (views) — one VIEW_RECIPE per cell. New plugin: **none** in this wave; the existing AFP path generalises.

**Acceptance**: a researcher arriving on a TPZ or IQZ DataObject page sees a cell-specific Trace3D or thermography view recipe without code change — just a new template instance.

### Wave 2 — Cell-aware orchestration (1-2 months; shepard-plugin-opcua + multi-cell shape)

Three cells with deeper integration needs:

- **TEZ (Technologie-Erprobungszelle)** — 2-robot common-axis cell; needs **multi-robot synchronisation captures** (timestamps aligned across two TS streams).
- **MFZ (Multifunktionale Zelle)** — 6 robots, splittable into 4 sectors. Demands the **process-chain shape**: a single Collection spans multiple sequential DataObjects with Predecessor edges; per-step roles assignable to specific sectors.
- **Cutterzentrum** — already OPC UA + RFID-coded transport pallets. Add **RFID-coded artefact attribution** — every cut output traces to a transport pallet ID, which traces to a downstream cell consumer.

**New plugin**: `shepard-plugin-opcua` (backlog OPCUA1 — already in queue per UNAS code-drop survey 2026-05-23). Reuse: ZLP's existing `opcua_orchestrator`, `opcua_krc_sever`, `opcua_to_rest`, `opcua_worker_interface`, `grafana-opcua-datasource`, `stc_opcua_configurator` (8 OPC UA tools on UNAS confirm the field-validated demand).

**New shape**: process-chain template via M1-VIEWS-AS-SHAPES-WAVE (tasks #58 + #157 + #142). Multi-DataObject chain with Predecessor + transitions = the *Panel Produktion* shape on page 22 generalised.

**Acceptance**: MFZ runs a 3-step demo (e.g., AFP layup → consolidation → NDT) with each step a DataObject, the chain visible as a Gantt view recipe (VIS-GANTT backlog row), and per-step OPC UA telemetry stored as a shepardId-addressable timeseries.

### Wave 3 — Pre/post-process cells (2-3 months; importer-plugin library)

Cells that pre- or post-process the main cells' output:

- **Wasserstrahlanlage** — typically post-cure trimming; output feeds back into the part DAG as a child DataObject.
- **Heißpresse + Öfen** — heat-cycle steps with thermocouple data; closed-loop control candidates.
- **Laminier-/Harzmischraum** — material prep; input attribution back to the layup DOs.

**Reuse**: `shepard-plugin-importer` library (backlog row #126) — these cells often write CSV/TDMS/proprietary formats that need adapters. The UNAS code drop has `tdms2csv`, `halo_importer`, `kibid_exporter`, `csv-to-influxdb` — directly populate the library.

**New shape**: pre/post-process **input/output attribution** via Predecessor edges; the "raw material batch X → laid-up panel Y" lineage that EN 9100 audit needs (`aidocs/strategy/86 §6` continuity table).

### Wave 4 — End-to-end process integration (3-6 months; the *Panel Produktion* demo)

The integration target itself — slide 22's process chain captured end-to-end with the closed-loop and automation feedback paths:

- Stringerablage + Skinablage → TPS++ (Tape Profile Sensor) closed-loop
- Vakuumaufbau → Aushärten → DEA (Dielectric Analysis)
- Einmessen → 3D Metrology (TScan + AT901 + ZIVID)
- Best-Fit Analyse → Toleranzen
- Spant-integration → Guided assembly
- NDI → LEA + OLT (Laser-Excited Acoustics + Online Tomography)
- Weiterverarbeitung

**New shape**: process-chain Collection-of-DataObjects with auto-generated per-step VIEW_RECIPEs; closed-loop arrows are first-class edges (`shepard:closedLoopFeedback` predicate).

**Acceptance**: an auditor walks the whole chain from raw material to final NDI for a single panel, in Shepard, without leaving the UI; export as RO-Crate (per `aidocs/strategy/86 §6` continuity table + the federated-Shepard architecture sketch the user uploaded 2026-05-23).

### Wave 5 — Cross-cell + cross-domain (6+ months; the Krebs 2025-02-21 vision)

Realise the cross-domain matrix from slide 23: Shepard as substrate across Structural mechanics / Materials / Design+Process / Production / Ground+Flight Test. CoBots + AGV cells join the network. External partners (page 23 lateral columns) consume Shepard via either RO-Crate export-import or federated Shepard service (Flo's diagram, uploaded 2026-05-23).

This is the **§3 Architecture chapter of the thesis** made operational.

## §3 Generalisation opportunities — the layers

The rollout's **generalisation discipline** is what makes it survive scope creep. Each cell shouldn't need its own plugin or its own data model. Five reusable layers:

### Layer 1 — Wire-level adapters
- **shepard-plugin-opcua** (OPCUA1) — one plugin reads any KUKA cell's OPC UA exposure; no per-cell code
- **shepard-plugin-importer** (#126) — one library of importers (CSV / TDMS / proprietary) per source format; per-cell config not per-cell code
- Garage S3 substrate already shared

### Layer 2 — Shape templates (the M1 wave)
- VIEW_RECIPE per cell type — Trace3D for AFP, thermography overlay for IQZ, Gantt for MFZ process chains, sparkline-per-axis for TPZ joint angles
- One SHACL template defines one cell's domain; instances are per-machine
- Per `aidocs/semantics/98 §2`

### Layer 3 — Process-chain primitives
- Predecessor/Successor with typed transitions (`shepard:reworkOf` per the audit at `aidocs/agent-findings/persona-audit-views-as-shapes-2026-05-23.md`; new candidate `shepard:closedLoopFeedback` for Wave 4)
- Collections as project, DataObjects as steps — directly inherited from iDMS Project/Experiment/Step (`aidocs/strategy/86 §4`)

### Layer 4 — Quality-gate primitives
- DataObject `status` extension for NCR_OPEN / FAILED / REJECTED (backlog item)
- Calibration certificate as DataObject subtype linked to measurement steps
- AI-assisted defect recognition (slide 21) writes findings back as semantic annotations on the source DO — task TPL9 (f(ai)²r) covers the AI-provenance shape

### Layer 5 — Cross-instance + federation
- RO-Crate export-import (lower risk, per Krebs federation sketch) — backlog row TPL14 (Regulatory Evidence Pack export) covers this
- Federated Shepard service (higher risk) — long-term; the user's diagram shows this is the **deferred but planned** target
- aidocs/frontend/100 cross-instance prov UI covers the visual layer

## §4 Sequencing constraints + dependencies

Each wave gates on prior infrastructure:

| Wave | Depends on | Blocks |
|------|-----------|--------|
| 0 (done) | v15 importer + Garage + Neo4j prov + basic plugin SPI | Wave 1 |
| 1 | Wave 0; SHACL VIEW_RECIPE substrate (substrate-split #157) | Wave 2 |
| 2 | Wave 1; shepard-plugin-opcua (OPCUA1); TS-IDc migration (#58); M1 wave | Wave 3 |
| 3 | Wave 2; shepard-plugin-importer library (#126); pre/post-process attribution patterns | Wave 4 |
| 4 | Wave 3; closed-loop edge predicates; admin runtime config for per-cell tuning | Wave 5 |
| 5 | Wave 4; federation work (RO-Crate first, federated service later); cross-domain templates | external adoption |

The **TS-IDc migration (task #58)** is the single most upstream blocker for Wave 2 onwards. The M1-VIEWS-AS-SHAPES-WAVE bundle (user decision 2026-05-23) makes this concrete.

## §5 Resource model

- **Wave 1**: ~1 engineer-month equivalent, mostly templates + content — minimal new code
- **Wave 2**: ~3-4 engineer-months — shepard-plugin-opcua + M1 wave delivery
- **Wave 3**: ~2-3 engineer-months — shepard-plugin-importer library scoped to BT cell formats
- **Wave 4**: ~4-6 engineer-months — process-chain shape work + auto-generated view recipes
- **Wave 5**: ~6-12 engineer-months — federation + cross-domain scaling

At the current AI-collaborative pace observed (`project_collab_highlights.md` "Self-updating import script — the disposability-of-effort moment", `aidocs/sustainability/00-energy-estimation-log.md`), these estimates are upper-bound. Many waves' work compresses substantially when the architecture is right.

## §6 Risks + mitigations

| Risk | Mitigation |
|------|-----------|
| Per-cell hand-rolling instead of generalising | Enforce Layer 1-5 discipline; reject per-cell plugins for problems the shape templates solve |
| OPC UA exposure varies cell-to-cell | shepard-plugin-opcua handles common cases; cell-specific quirks become config not code |
| Cells without OPC UA (Öfen?) | Importer-plugin library handles CSV/TDMS/proprietary; OPC UA isn't mandatory |
| EN 9100 audit gap on non-MFFD cells | Quality-gate primitives (Layer 4) ship in Wave 3; per `aidocs/agent-findings/manufacturing-quality.md` |
| MFFD-only personnel knowledge → can't service other cells | Layer 1-3 templates + shapes make cells documented uniformly; rollout simultaneously documents |
| Federation underestimated (Wave 5 hardest) | RO-Crate export first (lower risk per Krebs sketch); federated-service deferred until export proven |
| MFFD finishes before subsequent waves can absorb the team | Wave 1 starts NOW; learnings from Wave 0 inform Wave 1 in parallel |

## §7 What this isn't

- **Not** a commitment to roll out all 9 cells in a fixed timeline; it's the sequence + dependency graph
- **Not** a replacement for the existing v15 MFFD work — that IS Wave 0 and continues
- **Not** a federation-first plan — federation is Wave 5, after generalisation has proven itself
- **Not** scope expansion against the funding case — this maps to existing Clean Aviation JU + Helmholtz + DLR strategic priorities (per `aidocs/strategy/86 §1` framing)

## §8 Connections to the thesis

This rollout plan IS the empirical evaluation chapter (§6 Case study) of the thesis outline in `project_thesis_idea.md`. Each wave's acceptance criterion produces evidence:

- **Wave 0** evidence: the live MFFD import, the v15.x evolution arc, the cube3-to-nuclide cross-instance prov chain
- **Wave 1-3** evidence: the generalisation across cells without per-cell code = the "ontology-driven substrate works" claim
- **Wave 4** evidence: the Panel Produktion end-to-end demo = the "Shepard productionises the iDMS architecture" claim
- **Wave 5** evidence: federation working = the "thesis answer scales beyond ZLP" claim

## §9 References

- *Fähigkeiten-Inventur* — Krebs F., DLR BT/ZLP Augsburg, 2025-04-18 (31 slides, archive copy uploaded to AI working memory 2026-05-23 as `44342c9d-20250418_DLRBT_Inventur_nsf_FK.pdf`)
- *Digitally integrated lightweight design and validation* — Krebs F., DLR BT, 2025-02-21 (vision slide 23, same archive)
- `aidocs/strategy/86-shepard-predecessor-systems.md` — historical lineage; TPZ.R10 IPRO-era precedent
- `aidocs/semantics/95-shacl-templates-and-individuals.md`, `aidocs/semantics/98-shapes-views-and-process-model.md` — the shape substrate this rollout depends on
- `project_m1_views_as_shapes_wave.md` — user decision 2026-05-23 to bundle TS-IDc + shapes/render + Trace3D
- `aidocs/frontend/100-cross-instance-prov-ui.md` — Wave 5 cross-instance rendering
- Backlog rows: OPCUA1, #126 (importer plugin), #58 (TS-IDc), #142 (Trace3D), #157 (shapes/render), VIS-GANTT, AAS-REUSE-AUDIT, TPL9, TPL10, TPL14
