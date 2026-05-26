---
stage: audited-by-personas
last-stage-change: 2026-05-24
audience: strategy, contributor, thesis-substrate
---

# eLib BT-project sweep + 5 user-flagged papers — Braunschweig / Stuttgart / Augsburg deep-dive

**Author:** eLib BT-sweep agent (specialised research-network sub-agent)
**Date:** 2026-05-24
**Scope:** Per-paper synopses of 5 user-flagged eLib entries; broader sweep of DLR-BT publication clusters 2022-2025; people / method / equipment graph deltas vs. the sibling `research-network-deepening-2026-05-24.md` dossier.

**Sibling agent owns:** `aidocs/agent-findings/research-network-deepening-2026-05-24.md` (Armin Huber + Marin + MASiMo). This document is strictly non-overlapping with that file.

**Cross-reference target:** `aidocs/strategy/103-research-network.md` (network anchor), `aidocs/strategy/104-author-research-profile.md` (Krebs corpus), `aidocs/strategy/74-dlr-bt-stakeholder.md` (BT pilot brief), `aidocs/strategy/100-shepard-bt-zlp-rollout-plan.md` (rollout plan).

---

## 0. TL;DR

The five user-flagged papers cluster cleanly: **four are the ZLP-Augsburg thermoplastic-welding axis** (resistance + ultrasonic welding for MFFD-class CFRP structures), **one is the MFFD upper-shell quality-gap paper that the existing MFFD showcase paraphrases**. The broader BT sweep surfaces six durable publication clusters — welding (≥30 papers since 2022), AFP defects & strain-rate testing (~10), composite hydrogen tanks, hybrid laminar flow control, AFP steering, and crashworthiness simulation — plus a **GAN-based data-augmentation thread under Toso & Yoo** that is the most plausible AI/ML pilot inside BT and a natural next step for the `shepard-plugin-ai` design.

The single highest-value finding for Shepard adoption: **`Vistein, Mayer, Endraß, Fischer 2023 — "Single Source of Truth: Integrated Process Control and Data Acquisition System"`** is the missed citation. That paper is from the MFFD axis and shares Shepard's exact framing ("single source of truth for process + data"). It is not currently in `references.bib` and is not cited from `aidocs/strategy/100`. Adding it closes the most material gap in the BT-rollout narrative — BT already has a paper-of-record arguing for the data-substrate concept Shepard implements.

Showcase candidates surfaced: **Toso group's GAN data-augmentation work** (paper-of-record for the "AI assist on small labelled datasets" plugin pitch) and **Voggenreiter+Raps 2026 AFP-steering paper** (Trace3D acceptance test for in-situ AFP trajectory provenance, currently the canonical Krebs-coauthored ICRA 2024 calibration paper plus the new steering paper bracket the same cell).

---

## 1. User-flagged 5 papers — per-paper synopses

The five papers landed in the user message resolve to five distinct DLR-BT eLib records, all 2024–2025, all from the ZLP-Augsburg axis of DLR-BT (Institut für Bauweisen und Strukturtechnologie). Synopsis below for each.

### 1.1 eLib 220159 — Janek, Larsen, Jarka, Görick (2025): *Roadmap for the Industrialization of Robotic Continuous Ultrasonic Welding*

- **Authors:** Maximilian Janek, Lars-Christian Larsen, Stefan Jarka, Dominik Görick — all DLR-BT / Automation und Produktionstechnologie, Augsburg.
- **Venue:** DLRK 2025 (Deutscher Luft- und Raumfahrtkongress), Augsburg, 23–25 Sep 2025.
- **What it argues:** continuous ultrasonic welding (UW) is presented as the rapid, adhesive-free joining technique that displaces conventional fasteners for thermoplastic CFRP. The paper enumerates development milestones already met at ZLP and proposes a multi-step roadmap to industrial maturity. The standardisation gap (cross-academia + cross-industry) is the named blocker.
- **Why it matters for Shepard:** this is a **process-chain paper** — it implicitly assumes a data substrate that tracks each milestone, each weld, each parameter set, each qualification campaign across years and labs. It is the precise use case for the [Shepard `Predecessor / Successor` chain](#) on DataObjects: a UW qualification run today should be navigable to the milestone it advanced, to the prior milestone that justified it, and to the next-step downstream tests. **Maps to:** existing welding payload kinds (timeseries + structured-data + files) + the *cross-cell process chain* shape (Wave 2 of `aidocs/strategy/100`); reinforces TableContainer (TB1) as the right surface for the "qualification matrix" view.
- **Feature opportunity:** **`shepard-plugin-roadmap`** — a milestone-aware view recipe that renders the Predecessor chain as a maturity-roadmap Gantt with milestone status labels. Plausible MVP in 2-3 weeks if M1-VIEWS-AS-SHAPES-WAVE ships.

### 1.2 eLib 221339 — Bauer, Larsen, Gesang, Hümbert, Streck, Kupke (2025): *Toleranzausgleich beim Schweißen von Faserverstärkten Thermoplasten*

- **Authors:** Simon Bauer, Lars-Christian Larsen, Dustin Gesang, Simon Hümbert, Florian Streck, Michael Kupke — DLR-BT Stuttgart (*Bauteilgestaltung und Fertigungstechnologien*) + Augsburg (*Automation und Produktionstechnologie*). **Cross-location collaboration**, which is structurally significant (see §3.2).
- **Venue:** DLRK 2025, Augsburg.
- **What it argues:** combines two of BT's core capabilities — **3D-printed shims** (additive manufacturing) and **thermoplastic resistance welding** — into a single tolerance-compensation workflow for overlap joints. Findings: increasing shim thickness reduces strength; guided tension testing reduces strength loss; microscopy reveals reduced pore content + short-fibre alignment in melt regions.
- **Why it matters for Shepard:** this is a paper about **the joint of two payload types** — additive process parameters (shim geometry/material) joined to welding process parameters (current, time, pressure) joined to characterisation results (microscopy + tension test). The DataObject graph in current Shepard cannot natively express "AM-shim X was used in weld-trial Y was characterised in microscopy session Z" without a shape; **today an admin would model this as three DataObjects linked by Predecessor/Successor with attribute coupling.** A SHACL template (per `aidocs/semantics/95`) for "additive-tolerance-compensation-trial" would make this entire paper's data trivially queryable.
- **Feature opportunity:** SHACL template family for **multi-process-step characterisation campaigns**. Backlog row: `SHAPE-WELDING-AM-COUPLING` (see §7).

### 1.3 eLib 220292 — Larsen, Endraß, Bauer, Jarka, Janek (2025): *The state of the art in resistance and continuous ultrasonic welding*

- **Authors:** Lars-Christian Larsen, Manuel Endraß, Simon Bauer, Stefan Jarka, Maximilian Janek — all DLR-BT Augsburg.
- **Venue:** *JEC Composites Magazine*, Vol. 162, April 2025, pp. 26–28. **Industry-magazine state-of-the-art piece — public-facing, peer-reviewed by editorial standards but not academic peer review.**
- **What it argues:** comparative state-of-the-art between **resistance welding** (Joule heating in conductive carbon-fibre implant) and **continuous ultrasonic welding** (HF vibrations, polymer-melt). Both processes, both physics, both relevant for thermoplastic composite joining.
- **Why it matters for Shepard:** this is the **vocabulary anchor** for any controlled-vocabulary work on welding-process classification. The annotation playbook for thermoplastic-welding DataObjects should align to the terminology in this paper. Specifically: the `welding_method ∈ {resistance, ultrasonic_continuous, ultrasonic_static}` controlled value goes here.
- **Feature opportunity:** seed the `welding_method` SKOS code-list in the **internal semantic repository** (Neo4j `V49__Bootstrap` migration). This is a near-zero-effort win — add 5–6 terms with definitions sourced from the paper.

### 1.4 eLib 216030 — Larsen, Krombholz (2025): *Entwicklungen und Aktivitäten des DLR für die hochratenfähige Fertigung von Faserverbundstrukturen der Luftfahrt*

- **Authors:** Lars-Christian Larsen (DLR-BT), Christian Krombholz (DLR Systemleichtbau **SY**, Augsburg). **First user-flagged paper with cross-institute authorship (BT + SY).**
- **Venue:** Composites United workshop *CFK-Zulieferer und Anlagenfertiger*, Garching bei München, 29–30 July 2025.
- **What it argues:** umbrella presentation of DLR's high-rate CFRP manufacturing capabilities for aerospace, naming **ZLP, MFFD, Wing of Tomorrow** as the strategic projects. Open-access PDF (3 MB) suggests slide-deck format.
- **Why it matters for Shepard:** **proof that BT and SY share the ZLP brand and the MFFD output**. This matters because the existing `aidocs/strategy/74` brief frames BT as the pilot target but `aidocs/strategy/100` correctly notes that the FPZ AFP cell sits inside the *Systemleichtbau (SY)* org line ("Produktionstechnologien SD"). **The pilot story for ZLP-Augsburg is necessarily BT+SY joint, not BT-only.** The user-flagged paper is the externally visible artefact confirming this.
- **Feature opportunity:** none directly; this is a **strategic correction** for the stakeholder-brief framing — `aidocs/strategy/74 §1` and `aidocs/strategy/100 §0` should both acknowledge BT+SY as the joint pilot domain. Logged in §end as a doc-correction action.

### 1.5 eLib 211330 — Deden, Brandt, Larsen (2024): *Closing the Quality Gap of thermoplastic AFP: Insights from the Production of the MFFD Upper Shell*

- **Authors:** Dominik Deden, Lars Brandt, Lars-Christian Larsen — DLR-BT Augsburg.
- **Venue:** ITHEC 2024, Bremen, 9–10 Oct 2024.
- **What it argues:** post-mortem on the 8-metre composite fuselage shell production for MFFD. Roots out the AFP defects encountered, traces them to process causes, proposes corrective measures. **In-situ consolidation** is the named technology.
- **Why it matters for Shepard:** **this is the paper of record behind the MFFD seed.py demo.** The TR-001 → TR-015 narrative in `examples/lumen-showcase/seed.py` mirrors the Quality Gap framing — the "AFP defect" → "investigation" → "rework" → "re-test" chain is exactly the storyline this paper documents. The MFFD showcase is therefore **not arbitrary synthetic data** — it is a synthetic operationalisation of a real DLR paper. That linkage should be cited in the showcase README.
- **Feature opportunity:** **cite this paper in `examples/mffd-showcase/README.md`** as "the real-world process this synthetic demo paraphrases". Cross-link to `references.bib`. Also: the **Q1 anomaly** in the seed (`project_mffd_synthetic_vs_real.md` notes "synthetic not real") gains documentary grounding — the seed designer was modelling the *kind* of defects the paper describes. Logged in §end as a doc-action.

---

## 2. BT publication-cluster identification (2022-2025)

The eLib sweep across Larsen (50 papers visible), Kupke (64), Endraß (≥20), Voggenreiter (≥20) and topic queries (MFFD: 11 papers; ultrasonic welding: dozens) resolves into **6 durable publication clusters** at DLR-BT for 2022–2025. Per cluster: lead PI/author cluster, focus area, output volume, and Shepard relevance.

### 2.1 Cluster A — Thermoplastic welding (resistance + ultrasonic continuous)

- **Lead authors:** **Manuel Endraß** (RW lead, ZLP-Augsburg), **Maximilian Janek** (UW lead), **Lars-Christian Larsen** (cross-method coordinator), **Simon Bauer** (cross-method), **Michael Kupke** (institute vice-director, senior author on most).
- **Focus area:** static + continuous resistance welding of CF-thermoplastic; continuous ultrasonic welding; weld qualification, process control, NDI integration.
- **Output volume:** **≥30 papers since 2022** (dominant cluster). Co-authors fan out to NRC Canada (Palardy-Sim, Barroeta Robles, Octeau), UBC (Vaziri, Poursartip, Yousefpour, Atkinson, Nesbitt — *Composites Research Network*), Concordia/Mass General-affiliates.
- **Shepard relevance:** **direct.** This is the cluster the MFFD showcase targets; it owns the most live data, the most cross-institutional collaboration, the most parameter sweeps, and the most likely demand for FAIR-trail data management. **Pilot priority #1.**

### 2.2 Cluster B — Thermoplastic AFP (automated fibre placement) — defect & strain-rate testing

- **Lead authors:** **Dominik Deden** (process eng., MFFD AFP), **Lars Brandt**, **Sanghyun Yoo** (strain-rate testing), **Mathieu Vinot** (simulation lead), **Nathalie Toso** (BT-Stuttgart structural integrity), **Heinz Voggenreiter** (institute director, senior author).
- **Focus area:** in-situ consolidation defects; dynamic compression of AFP laminates at intermediate strain rates; data-driven (CANN, GAN) defect/strain-rate prediction.
- **Output volume:** **~10 papers since 2023**, growing. Notable: Yoo et al. 2024 + 2025 (defect-strain-rate coupling, CANN, GAN data augmentation) — see §2.6 for the GAN sub-cluster.
- **Shepard relevance:** **high.** Strain-rate test data is a textbook timeseries-with-rich-metadata payload (one TS per channel, multiple channels per specimen, defect labels per region). The Quality Gap paper (eLib 211330) is from this cluster. **Pilot priority #2.**

### 2.3 Cluster C — Robot-based assembly & process control (cross-cell orchestration)

- **Lead authors:** **Michael Vistein** (process control + OPC UA), **Matthias Mayer** (robot control), **Florian Fischer** (manufacturing eng.), **Manuel Endraß**.
- **Focus area:** single-source-of-truth process control; robot-based assembly cells; smart sensors for autonomous panel assembly; multi-robot orchestration.
- **Output volume:** ~5–8 papers since 2022, with **Vistein et al. 2023 "Single Source of Truth: Integrated Process Control and Data Acquisition System"** (ICINCO 2023) as the cluster's named anchor.
- **Shepard relevance:** **critical and currently under-cited.** This cluster's vocabulary ("single source of truth", "integrated data acquisition") is literally Shepard's pitch. **The Vistein 2023 paper is the missing citation in `aidocs/strategy/100`.** Adding it converts an external argument into a within-BT alignment claim. Pilot priority for *citation*, not new pilot work — the existing OPCUA1 backlog plugin (`shepard-plugin-opcua`) covers the technical surface.

### 2.4 Cluster D — Composite crashworthiness & impact (BT-Stuttgart)

- **Lead authors:** **Nathalie Toso** (sims + experimental, BT-Stuttgart), **Mathieu Vinot**, **Heinz Voggenreiter**, **Jan Philip Dittmann** (composite hydrogen tanks under impact, 2025).
- **Focus area:** composite plate impact response, axial crush simulation, bird-strike on hybrid laminar flow control (Clean Sky 2 demonstrator), composite hydrogen tanks under internal pressure + impact (LS-DYNA).
- **Output volume:** ~6–10 papers since 2022. Anchored by Voggenreiter's senior-authorship pattern.
- **Shepard relevance:** **direct fit for CITE pilot brief in `aidocs/strategy/74 §2.1`** — exactly the millisecond-resolution sensor-array crash data the brief proposes. Pilot priority #3. The hydrogen-tank impact paper (Dittmann et al. 2025) is also the bridge to BT's **TeTeAnt-H2 / ADAPT** hydrogen propulsion work in §2.5.

### 2.5 Cluster E — Composite hydrogen tanks / pressure vessels

- **Lead authors:** **Jan Philip Dittmann** (sims), Vinot, Toso, **Voggenreiter** (senior author). Cross-coupling with **Clemens Schmidt-Eisenlohr** (Augsburg axis, helicopter US welding, vacuum bagging).
- **Focus area:** thick-walled composite hydrogen tanks under internal pressure + impact loads; LS-DYNA simulation methodology; experimental draping behaviour of auxiliary materials for vacuum-bagged CFRP parts.
- **Output volume:** ~3-5 papers since 2023, but **trajectory matches the DLR-wide H2 strategic theme**, so volume will grow.
- **Shepard relevance:** **moderate now, high near-term.** This is the cluster behind the TeTeAnt-H2/ADAPT mention in `aidocs/strategy/74`. The shepard data substrate for tank characterisation = timeseries (pressure, temperature) + simulation HDF5 outputs (per A5-HDF) + cross-run comparison shape (parameter sweep). **Plugin candidate:** the HDF5 simulation-output payload kind in A5 directly applies.

### 2.6 Cluster F — AI/ML on composite data (data-driven defect & strain-rate prediction)

- **Lead authors:** **Sanghyun Yoo** (multi-paper PhD-track), **Mathieu Vinot**, **Julia Kowalski** (RWTH, external), **Nathalie Toso**, **Voggenreiter**.
- **Focus area:** Constitutive Artificial Neural Networks (CANNs) for strain-rate effect prediction; Generative Adversarial Networks (GANs) for **material-test data augmentation**; classical ML for ultrasonic-weld quality prediction (Görick 2023, 2024).
- **Output volume:** ~4-6 papers since 2023, two of them 2025.
- **Shepard relevance:** **structurally important.** This cluster is the **paper-of-record reason to build `shepard-plugin-ai`**. The data shape (small, well-labelled material-test campaigns; need for augmentation; need for embedding-based discovery of similar specimens) is exactly what `aidocs/agent-findings/analytics-ai.md` argued for. **Pilot priority #2 (tied with B) — these authors are the BT-internal natural audience for the AI plugin.**

---

## 3. People-graph deltas vs. the sibling research-network dossier

The sibling agent owns Huber + Marin + Müller + MASiMo. The eLib sweep surfaces additional names not in the existing `aidocs/strategy/103-research-network.md` or `aidocs/strategy/104-author-research-profile.md` corpus (verified by grep). These are the **delta surface** — researchers who are heavy publishers in BT's 2022–2025 output and who plausibly belong in the network graph but are not yet captured.

### 3.1 New name surfaces (high signal)

| Researcher | DLR affiliation | Role / signal | Co-authorship with Krebs? | Recommend dossier entry? |
|---|---|---|---|---|
| **Manuel Endraß** | BT-Augsburg | RW lead, ZLP. ≥20 papers since 2022. MFFD upper-shell production paper co-author. Cluster A senior. | Indirect (shared cell ecosystem; no joint paper yet on eLib) | **Yes** — high-volume MFFD axis |
| **Maximilian Janek** | BT-Augsburg | UW lead. Newer researcher (likely PhD/early career), heavy 2024–2025 output. Roadmap paper lead author (eLib 220159). | None visible | **Yes** — UW process owner |
| **Sanghyun Yoo** | BT-Stuttgart (Toso group) | Strain-rate + AI/ML on composites. ~5 papers 2023–2025, multiple as first author. | None visible | **Yes** — most plausible AI-pilot champion inside BT |
| **Nathalie Toso** | BT-Stuttgart | Structural integrity, crashworthiness, senior author on crash + impact + AI sub-clusters. ~10 papers since 2019. | None visible | **Yes** — Cluster D + F senior, owns the crash/AI bridge |
| **Mathieu Vinot** | BT-Stuttgart | Simulation lead (LS-DYNA, CANN, GAN sims). Senior co-author across crash + AI papers. | None visible | **Yes** — simulation-side of strain-rate AI pipeline |
| **Heinz Voggenreiter** | BT institute director (Stuttgart) | Senior author on AFP + crash + composite hydrogen + AI papers. Institute leadership. | None visible | **Yes** — must-have for institute-leadership outreach |
| **Lars Brandt** | BT-Augsburg | MFFD upper-shell production co-author; thermocouple-based AFP optimisation. | Indirect (FPZ/T-AFP cell) | **Yes** — direct MFFD axis |
| **Dominik Deden** | BT-Augsburg | AFP defects + MFFD lead. Quality Gap paper (eLib 211330) lead author. | Indirect (FPZ/T-AFP cell) | **Yes** — MFFD axis |
| **Michael Vistein** | BT-Augsburg | Process control, OPC UA, robot orchestration. Vistein 2023 "Single Source of Truth" paper anchor. | **Yes — almost certain joint domain history at ZLP (IPRO timeframe)** | **Yes — high priority** — most likely *direct citation peer* for Shepard |
| **Matthias Mayer** | BT-Augsburg | Robot control, cabin assembly automation. Co-author on Braun/Mayer 2025 cabin-assembly paper, Krebs's IPRO-era contemporary. | **Likely** | **Yes** |
| **Simon Hümbert** | BT-Stuttgart | PEEK overprinting bond formation (2024). Cross-author with Bauer (eLib 221339). | None visible | **Possibly** — Stuttgart node |
| **Florian Fischer** | BT-Augsburg | Manufacturing eng., MFFD upper shell. Co-author with Endraß + Vistein on SSoT paper. | Indirect (ZLP cell) | **Yes** — MFFD axis |
| **Dominik Görick** | BT-Augsburg | UW process monitoring, ML-on-weld-quality (with Kupke, Welsch). | None visible | **Yes** — bridges Clusters A + F |
| **Clemens Schmidt-Eisenlohr** | BT-Augsburg (helicopter axis) | US welding for helicopter CFRP; vacuum bagging draping behaviour. | None visible | **Yes** — bridges welding to rotorcraft |
| **Christian Krombholz** | DLR-**SY** (not BT) Augsburg | High-rate CFRP eng. Krombholz-Larsen 2025 joint paper (eLib 216030). | None visible | **Yes** — SY-BT bridge node |

### 3.2 Cross-location authorship graph

The Bauer–Larsen–Gesang–Hümbert–Streck–Kupke 2025 paper (eLib 221339) is the **first user-flagged paper to cross Stuttgart × Augsburg**. The Larsen–Krombholz 2025 paper (eLib 216030) is the **first to cross BT × SY**. Both are 2025. Pattern: **the BT publication ecosystem is increasingly cross-location and cross-institute** — the Shepard pilot brief should not assume a single-site rollout. Action: update `aidocs/strategy/74 §1` to acknowledge BT-Stuttgart + BT-Augsburg + SY-Augsburg as the joint pilot domain. (Logged in §end.)

### 3.3 Cross-reference vs. Krebs corpus (`aidocs/strategy/104`)

`aidocs/strategy/104` already names **Vistein, Brandt, Krebs** on the 2024 ICRA paper (Audet et al.); names **Brandt, Deden** on the krebsDlrk2021 paper-of-record. Endraß, Janek, Yoo, Toso, Vinot, Voggenreiter, Bauer, Hümbert, Görick, Schmidt-Eisenlohr, Krombholz — **none of these names appear in the 104 corpus**. The eLib sweep confirms they are the publication-volume centroid of contemporary BT work, which means the network dossier in 103 has a substantial **co-author-graph blind spot** on the Stuttgart + welding axes. The sibling agent's dossier (Huber, Marin) covers part of this gap; this sweep names the rest.

### 3.4 Respectful framing reminder

Per `feedback_respectful_predecessor_framing.md`: these are public byline names from public publications; cited factually without speculation about internal roles. Per `project_ux_research_2024_anonymity.md`: no individual identification is being asserted beyond what is already public in the eLib record.

---

## 4. Method / equipment landscape — recurring across BT publications

Reading the publication record as a methods catalogue, the following recur across ≥3 papers each in the 2022–2025 window and represent the **field-tested instrumentation Shepard must accommodate** if it is to be the data substrate for BT:

| Method / equipment | Recurring across | Shepard payload kind | Existing or backlog |
|---|---|---|---|
| **Thermography (3D-mapped to CAD)** | MFFD upper shell production, AFP TCP monitoring, Görick UW thermal data 2024 | Files (image stacks) + structured-data (geometry mapping) + Trace3D view | Trace3D is task #142 (M1-VIEWS-AS-SHAPES-WAVE) |
| **Polymer-CMUT array sensors** | Görick et al. 2024 — inline US-weld monitoring via polyCMUT | Timeseries (waveform) + structured-data | TS substrate ready; visualisation TBD |
| **Laser Excited Acoustics (LEA)** | ZLP NDT cell (per `aidocs/strategy/100 §1`) — eyesafe pulsed laser + optical mic | Timeseries (waveform) | TS substrate ready; specific plugin not designed |
| **LS-DYNA simulation outputs** | Toso/Vinot/Dittmann papers; composite hydrogen tank, AFP defect strain-rate, hybrid laminar flow bird strike | HDF5 (A5-HDF backlog) | A5-HDF designed (see `aidocs/44`) |
| **Structured-light 3D + Leica laser tracker** | ZLP 3D Geometrie-Vermessung capability | Files + structured-data (point cloud or mesh) | Spatial-container backlog (task #79); Trace3D recipe relevant |
| **High-speed camera (impact, crash)** | Toso crash + Voggenreiter impact-on-composite-plate papers | Files (video) + timeseries (frame events) | Video plugin (PT-VIDEO backlog) |
| **DIC (digital image correlation)** | Implicit in strain-rate / crash sub-cluster (Yoo, Toso, Voggenreiter) | Files (image stacks) + structured-data (strain field) | Surface-mesh / PIV-style plugin backlog |
| **Dispersion calculation (US-weld)** | Less explicit but implicit in cluster A signal-processing chain | Structured-data | Trivial — fits TableContainer (TB1) |
| **Photogrammetry** | Implicit at ZLP per `aidocs/strategy/100` (Soll-Ist-Vergleich) | Files + structured-data | Spatial backlog |
| **CT / industrial radiography** | Not strongly represented in 2022–2025 BT eLib output (surprise — see §end) | Files (volume) | Nanotom-volumetric plugin backlog (sibling dossier topic) |
| **CFRP autoclave + heated press telemetry** | TPZ cell (per `aidocs/strategy/100`); historical IPRO chain | Timeseries (pressure, temp) + files (cycle reports) | Native to existing TS + file substrate |
| **OPC UA (KUKA + Festo + Siemens integration)** | Vistein 2023 SSoT paper; Krebs IPRO history; ZLP cell standard | Timeseries (high-rate) + structured-data (machine state) | OPCUA1 plugin backlog (task) |

**Bottom-line gap:** the **CT / Nanotom volumetric** thread is *not* visible in the 2022–2025 BT eLib output. The sibling agent's brief mentions Nanotom; it may live at a different institute (Werkstoff-Forschung, WF?) or be an internal capability not yet reflected in publications. Worth a direct probe with stakeholders. Logged in §end.

---

## 5. Showcase candidates — which BT projects benefit most from Shepard as substrate

Synthesising clusters § 2 with the rollout sequence in `aidocs/strategy/100 §2`:

### Tier 1 — already in flight + highest fit

1. **MFFD upper-shell production data (Cluster A+B)** — Shepard already ingests cube3 import. The Quality Gap paper (eLib 211330) is the paper-of-record. Action: **cite eLib 211330 in `examples/mffd-showcase/README.md`** so the synthetic demo's narrative is explicitly grounded in public BT publication.

2. **Vistein 2023 SSoT-pattern citation (Cluster C)** — already lives at ZLP-Augsburg, already publishes the data-substrate pitch. Action: **add `vistein2023sourceOfTruth` to `references.bib`** + cite from `aidocs/strategy/100 §0`.

### Tier 2 — natural near-term pilot candidates

3. **Toso/Yoo AI-augmentation pipeline (Cluster F)** — small, labelled material-test campaigns; immediate need for embedding-based discovery. This is the **most plausible BT-internal champion for `shepard-plugin-ai`**. Outreach target: Toso + Yoo + Vinot. Pilot shape: ingest a single CANN/GAN dataset to Shepard, demonstrate end-to-end with annotation playbook + version chain.

4. **Janek UW roadmap data (Cluster A — eLib 220159)** — milestone-tracked qualification data across years. Pilot shape: model the UW roadmap as a chain of DataObjects with milestone-status attributes; visualise via M1-VIEWS-AS-SHAPES-WAVE (Gantt recipe).

### Tier 3 — strategic depth, longer arc

5. **Dittmann composite hydrogen tank (Cluster E)** — simulation HDF5 (A5-HDF dep) + experimental TS + cross-run comparison shape. Best after A5-HDF ships.

6. **CITE crash data (Cluster D)** — exactly the pitch in `aidocs/strategy/74 §2.1`. Best after the BT-Stuttgart relationship is warmed by tiers 1-2.

---

## 6. Bibliography additions for `docs/_data/references.bib`

Per `feedback_bibliography_maintenance.md`, every new citation in this doc needs a BibTeX entry. Add to `docs/_data/references.bib` under category `dlr_internal`:

```bibtex
@inproceedings{janek2025roadmap,
  author       = {Janek, Maximilian and Larsen, Lars-Christian and Jarka, Stefan and Görick, Dominik},
  title        = {{Roadmap for the Industrialization of Robotic Continuous Ultrasonic Welding of Fiber-Reinforced High-Performance Polymers in the Aerospace Industry}},
  booktitle    = {Deutscher Luft- und Raumfahrtkongress (DLRK) 2025},
  year         = {2025},
  address      = {Augsburg},
  organization = {DGLR},
  url          = {https://elib.dlr.de/220159/},
  note         = {DLR-BT Augsburg; thermoplastic CFRP UW roadmap to industrial maturity.},
  category     = {dlr_internal}
}

@inproceedings{bauer2025toleranzausgleich,
  author       = {Bauer, Simon and Larsen, Lars-Christian and Gesang, Dustin and Hümbert, Simon and Streck, Florian and Kupke, Michael},
  title        = {{Toleranzausgleich beim Schweißen von Faserverstärkten Thermoplasten}},
  booktitle    = {Deutscher Luft- und Raumfahrtkongress (DLRK) 2025},
  year         = {2025},
  address      = {Augsburg},
  url          = {https://elib.dlr.de/221339/},
  note         = {DLR-BT Stuttgart × Augsburg cross-location paper; 3D-printed shims + thermoplastic resistance welding for overlap-joint tolerance compensation.},
  category     = {dlr_internal}
}

@article{larsen2025stateOfTheArt,
  author       = {Larsen, Lars-Christian and Endraß, Manuel and Bauer, Simon and Jarka, Stefan and Janek, Maximilian},
  title        = {{The state of the art in resistance and continuous ultrasonic welding}},
  journal      = {JEC Composites Magazine},
  volume       = {162},
  pages        = {26--28},
  year         = {2025},
  month        = apr,
  url          = {https://elib.dlr.de/220292/},
  note         = {Industry magazine state-of-the-art piece; vocabulary anchor for welding-method controlled value.},
  category     = {dlr_internal}
}

@misc{larsen2025hochratenfaehig,
  author       = {Larsen, Lars-Christian and Krombholz, Christian},
  title        = {{Entwicklungen und Aktivitäten des DLR für die hochratenfähige Fertigung von Faserverbundstrukturen der Luftfahrt}},
  howpublished = {Workshop CFK-Zulieferer und Anlagenfertiger, Composites United},
  year         = {2025},
  month        = jul,
  address      = {Garching bei München},
  url          = {https://elib.dlr.de/216030/},
  note         = {BT × SY cross-institute joint presentation on high-rate CFRP manufacturing (ZLP, MFFD, Wing of Tomorrow).},
  category     = {dlr_internal}
}

@inproceedings{deden2024closingQualityGap,
  author       = {Deden, Dominik and Brandt, Lars and Larsen, Lars-Christian},
  title        = {{Closing the Quality Gap of thermoplastic AFP: Insights from the Production of the Multifunctional Fuselage Demonstrators Upper Shell}},
  booktitle    = {International Conference and Exhibition on Thermoplastic Composites (ITHEC) 2024},
  year         = {2024},
  address      = {Bremen},
  url          = {https://elib.dlr.de/211330/},
  note         = {Paper of record for the MFFD AFP showcase narrative in examples/mffd-showcase/; documents real DLR upper-shell production quality gaps that the synthetic demo paraphrases.},
  category     = {dlr_internal}
}

@inproceedings{vistein2023singleSourceOfTruth,
  author       = {Vistein, Michael and Mayer, Matthias and Endraß, Manuel and Fischer, Florian},
  title        = {{Single Source of Truth: Integrated Process Control and Data Acquisition System}},
  booktitle    = {Proceedings of the 20th International Conference on Informatics in Control, Automation and Robotics (ICINCO 2023)},
  year         = {2023},
  note         = {DLR-BT Augsburg; the in-domain paper of record for the data-substrate pattern Shepard implements. Underlying citation for any Shepard ↔ BT-internal positioning.},
  category     = {dlr_internal}
}

@article{yoo2025dataDrivenStrainRate,
  author       = {Yoo, Sanghyun and Aslamsha, Ijaz Ahamed and Bhattacharya, Dipankul and Kowalski, Julia and Voggenreiter, Heinz},
  title        = {{A Data-driven Approach to predict Strain Rate Effect of Carbon/epoxy Composites incorporating Constitutive Artificial Neural Networks (CANNs)}},
  journal      = {Conference proceedings, 10th ECCOMAS Thematic Conference on the Mechanical Response of Composites},
  year         = {2025},
  note         = {DLR-BT Stuttgart AI/ML thread; reference for shepard-plugin-ai pitch + Toso/Yoo outreach.},
  category     = {dlr_internal}
}

@article{yoo2025ganDataAugmentation,
  author       = {Yoo, Sanghyun and Viswakumar, Amal Jyothis and Vinot, Mathieu and Toso, Nathalie and Voggenreiter, Heinz},
  title        = {{A Generative Adversarial Networks (GANs)-based data augmentation in the context of material testing and predicting strain rate effect}},
  journal      = {ECCOMAS proceedings},
  year         = {2025},
  note         = {GAN data augmentation on small material-test datasets — direct fit for shepard-plugin-ai data-augmentation capability.},
  category     = {dlr_internal}
}

@article{voggenreiter2026afpSteering,
  author       = {Raps, Lukas and Chadwick, Ashley and Voggenreiter, Heinz},
  title        = {{Steering for in-situ AFP-manufactured structures: Part 1 - Critical arc length}},
  journal      = {(journal TBC; eLib record indicates 2026 release)},
  year         = {2026},
  note         = {AFP trajectory control; pairs with Audet/Krebs ICRA 2024 calibration paper as bracket for FPZ T-AFP cell. Trace3D acceptance test candidate.},
  category     = {dlr_internal}
}
```

(9 entries. The Voggenreiter–Raps 2026 record is forward-dated; cite cautiously until publication is confirmed.)

---

## 7. Backlog rows surfaced — for filing to `aidocs/16-dispatcher-backlog.md`

For the user to file (this agent does not modify the dispatcher backlog):

| Proposed ID | Title | Origin | Plausible plugin family |
|---|---|---|---|
| `SHAPE-WELDING-AM-COUPLING` | SHACL template family for multi-process-step characterisation campaigns (AM-shim ↔ welding ↔ characterisation tri-DataObject linkage) | §1.2 eLib 221339 | In-tree shape; no new plugin |
| `VOCAB-WELDING-METHOD` | Seed `welding_method` SKOS code-list into internal semantic repository (resistance / ultrasonic_continuous / ultrasonic_static + definitions) | §1.3 eLib 220292 | Migration only — extends `V49__Bootstrap_internal_semantic_repository.cypher` |
| `VIEW-ROADMAP-GANTT` | Milestone-roadmap Gantt view recipe for Predecessor-chain DataObjects with milestone-status attribute | §1.1 eLib 220159 | Part of M1-VIEWS-AS-SHAPES-WAVE (#142) |
| `AI-PILOT-TOSO-YOO` | Outreach + pilot scope for Toso/Yoo GAN data-augmentation campaign → shepard-plugin-ai | §2.6, §5 tier 2 | `shepard-plugin-ai` |
| `OUTREACH-VISTEIN-CITE` | Cite Vistein 2023 SSoT in `aidocs/strategy/100`; coordinate outreach with Vistein as in-domain peer | §2.3 | Doc + outreach |
| `DOC-MFFD-SHOWCASE-CITE` | Cite `deden2024closingQualityGap` in `examples/mffd-showcase/README.md` as paper-of-record for synthetic narrative | §1.5 | Doc only |
| `SHEPARD-BT-SY-JOINT-FRAMING` | Update `aidocs/strategy/74 §1` + `100 §0` to acknowledge BT+SY joint pilot domain (not BT-only) | §1.4, §3.2 | Doc only |
| `CT-NANOTOM-PROBE` | Direct stakeholder probe — why is CT/Nanotom volumetric absent from BT 2022-2025 eLib output? WF institute? Internal-only? | §4, §end | Reconnaissance |

---

## 8. Honest gap section — where eLib returned thin results

eLib's simple search endpoint is **fragile across author and topic queries** — multiple searches returned "Keine passenden Einträge gefunden" for inputs that almost certainly have matching publications (e.g. "Dominik Deden" alone, "Hühne Wiedemann" together, "Wing of Tomorrow", "CITE crash"). The successful results came from **single-author surname queries** ("Larsen Lars-Christian", "Kupke Michael", "Endraß", "Voggenreiter", "Toso", "MFFD") where the search index appears more stable. The thinness is a **search-tool artifact**, not a publication-volume signal.

Specific clusters under-explored on this sweep:

- **CITE / crash facility infrastructure papers** — no eLib hits via direct topic query; Toso publications partially cover this but the *facility itself* may be documented in internal reports not indexed for public search. **Direct outreach needed** to confirm CITE's data-substrate footprint.
- **CMC / Ceramic Composite Structures** (BT-Stuttgart department per `aidocs/strategy/74 §1`) — no specific authors surfaced this sweep. Cluster exists but the publication centre-of-mass was not found via the queries tried.
- **CALLISTO / Space System Integration** (BT-Stuttgart department) — same.
- **Hühne / Wiedemann (composite shell buckling / Braunschweig deep history)** — only 3 hits, mostly historical (2002, 2017, 2019). Either the search syntax is wrong or this group has moved or restructured; **the 2002 Braunschweig IB report (`Influence of Loading Conditions on Buckling of Fibre Composite Cylindrical Shells, IB 131-2002/20, Braunschweig`) is the only direct Braunschweig-named record found.** Worth a separate Scholar query later.
- **CT / Nanotom volumetric** — see §7 backlog row `CT-NANOTOM-PROBE`. Absence is the data point.

The Braunschweig framing in the task title therefore requires **a small correction** — BT's contemporary publication centre-of-mass is **Augsburg + Stuttgart**, not Braunschweig. Braunschweig hosts the *Forschungsflughafen* and other DLR institutes (Flugführung, Aerodynamik, Faserverbundleichtbau und Adaptronik — formerly FA, now part of Systemleichtbau SY), but BT-as-currently-structured is not heavily Braunschweig-localised. The sibling agent's Nanotom thread (CITE-related) lives in Stuttgart. If a future sweep explicitly targets Braunschweig-DLR composite work, the right institute is likely **SY (Systemleichtbau)** or historically **FA**, not BT.

---

## End — actions for the user

**Doc-correction actions** (per `feedback_continuous_doc_maintenance.md` — listed for the user to action, not actioned by this agent):

1. **Update `aidocs/strategy/74 §1`** — acknowledge BT+SY joint pilot domain (Augsburg + Stuttgart), not BT-only. Specifically: drop the "Braunschweig" framing in the task title's implicit assumption; BT-as-contemporary is Augsburg + Stuttgart.
2. **Update `aidocs/strategy/100 §0`** — same correction; add `vistein2023singleSourceOfTruth` as the in-domain paper-of-record citation.
3. **Update `examples/mffd-showcase/README.md`** — cite `deden2024closingQualityGap` (eLib 211330) as the real DLR paper the synthetic showcase paraphrases.
4. **Append 9 entries to `docs/_data/references.bib`** (§6 above).
5. **Consider filing 8 backlog rows** in `aidocs/16-dispatcher-backlog.md` (§7 above).
6. **Direct stakeholder probe** for `CT-NANOTOM-PROBE` (§7 / §8) — coordinate with sibling research-network agent's Nanotom thread.

**Thesis library updates** (per `feedback_thesis_library_maintained.md`):

- All 9 new bib entries belong in the thesis-relevant library (cluster A welding evidence base for the FPZ axis; cluster F AI/ML for the AI-substrate thread). Recommend updating `aidocs/strategy/102` or `103` index sections that reference the BT publication centroid to include the new names from §3.1.

**Aidocs updates beyond this doc** (for the user to action):

- `aidocs/strategy/103-research-network.md` — add new names from §3.1 to the network roster; cross-reference sibling-agent dossier (Huber + Marin + MASiMo) when it lands.
- `aidocs/strategy/104-author-research-profile.md` — append §3.1 names to the co-author graph (Krebs has indirect co-authorship via the FPZ/T-AFP cell with Brandt, Deden, Vistein, Mayer per the existing ICRA 2024 + DLRK 2021 entries).
- `aidocs/strategy/100-shepard-bt-zlp-rollout-plan.md §0` and §1 — add Vistein 2023 SSoT citation; correct BT+SY framing.

---

## Word count + metrics

- **Word count:** ~4,400 words (target band 3,000–5,000).
- **Papers synopsised in §1:** 5 (per user request).
- **BT clusters identified in §2:** 6 durable clusters.
- **People-graph deltas in §3:** 15 named (12 BT, 1 SY bridge, 2 Stuttgart-axis).
- **Methods/equipment catalogued in §4:** 12.
- **Showcase candidates in §5:** 6 (across 3 tiers).
- **BibTeX additions in §6:** 9 entries.
- **Backlog rows surfaced in §7:** 8.
- **Sibling agent overlap:** zero (verified — sibling agent owns Huber + Marin + MASiMo; this sweep covers welding/AFP/crash/AI clusters).
