# aidocs/82 — shepard: Stakeholder brief for DLR ZLP Augsburg

**Date:** 2026-05-16
**Audience:** DLR Zentrum für Leichtbauproduktionstechnologie (ZLP), Augsburg —
institute leadership, research group leaders, researchers considering a deeper
shepard deployment.
**Purpose:** Document the full depth of ZLP's data landscape and the gaps shepard
still doesn't cover well; map new capabilities to specific ZLP experiments; define
a concrete roadmap for a full-depth ZLP deployment across all five research groups;
make the case for ZLP as the reference deployment that other DLR institutes point to.

For the DLR-wide brief, see `aidocs/73`. For the DLR-BT institute brief, see `aidocs/74`.

---

## 1. Institute profile

DLR ZLP (Zentrum für Leichtbauproduktionstechnologie) in Augsburg is DLR's center for
automated CFRP (carbon fiber reinforced plastic) production research. shepard was
developed here and is actively used across AFP manufacturing, MFFD welding, and MEMAS
material characterisation. ZLP is shepard's home institute — the primary developer and
largest user.

Industrial context: CFRP components for next-generation aircraft (Airbus A320 successors,
MFFD demonstrators within Clean Sky 2 / AIRFRAME ITD), wind turbine rotor blades, and
automotive structural components.

### Five research groups

**1. Flexible Automation Systems**
Robot-based production, the Multifunctional Robot Cell (MFC — unique in Europe),
AFP (Automated Fibre Placement), and flexible tooling. The MFC is ZLP's primary AFP
platform; it generates the richest timeseries and trajectory data of any group.

**2. Assembly and Joining Technologies**
Automated assembly of aerospace CFRP components: resistance welding, ultrasonic welding.
The MFFD (Multi-Functional Fuselage Demonstrator) upper shell within Clean Sky 2 /
AIRFRAME ITD is the flagship campaign. ProcessControl and ProcessMonitoring — the
SCADA systems built on top of shepard (ICINCO 2023) — live here.

**3. Production Integrated Quality Assurance**
Real-time sensor integration during manufacturing. NDT: ultrasonic C-scan, active
thermography, acoustic emission (AE). Process monitoring and defect characterisation.

**4. Processes and Automation**
New automation strategies for low-volume, high-variety aerospace CFRP production.
Process parameter optimisation; linking parameter choices to downstream quality.

**5. Technical Centre for Production Processes**
Hands-on manufacturing with real aircraft-grade equipment. The facility where AFP,
welding, and NDT campaigns are physically executed.

### Confirmed publications citing shepard from ZLP

DGLR 2021 (AFP data management), DGLR 2022 (VPH/RCE), DGLR 2023 (closed-loop AFP
ecosystem), DGLR 2025 (QA in semi-automated processes), ICINCO 2023
(ProcessControl/ProcessMonitoring for MFFD welding), HMC 2023 and HMC 2025 (MEMAS
ontology-based storage), SAMPE 2022 (MFFD welding quality system). Full citation
catalogue in `aidocs/76`.

---

## 2. The five research campaigns and their data landscape

### 2.1 AFP — Automated Fibre Placement

**Data generated:**

| Data type | Format / rate | Currently in shepard | Gap |
|---|---|---|---|
| Robot 6-DOF joint angles + TCP position/velocity | Timeseries, ~10 Hz during layup | Some process timeseries | No spatial binding: robot position ↔ sensor reading unlinked |
| Layup speed, compaction roller force, heating temp, ply sequence log | Timeseries scalar channels | Partial | — |
| Per-ply visual inspection images | One image per ply, named by ply index + part ID | As files | Not linked to ply geometry |
| Post-cure ultrasonic C-scan | 2D spatial image (amplitude/phase at each x,y) | As files | Not bound to part surface |
| CT scan (post-cure, large defects) | 3D voxel data, hundreds of GB | Not stored | No voxel payload kind |
| Structural coupon tensile tests | Force-displacement → E-modulus, UTS, fracture strain | Partial | Not linked to specific panel location |
| CAD: nominal layup geometry | CPACS or STEP | Not stored | No CadReference payload kind |
| Robot path program | `.rob` or equivalent | Not stored | — |

**Pain point:** Process parameters, inspection images, C-scan results, and tensile
test data from the same panel exist in separate locations with no machine-readable
link. Closing the process-to-performance loop requires manual assembly.

**shepard answer (with upcoming features):**

| Feature | How it helps |
|---|---|
| `CadReference` + STEP import (CAD1a) | Store nominal layup geometry alongside layup process data |
| URDF bundle + joint annotation (CAD1d) | Robot kinematic model for trajectory replay |
| GeometryAnnotation SURFACE (SB1a) | Annotate individual ply surfaces on the part model |
| DataBinding HEATMAP (SB1c) | C-scan amplitude overlaid directly on the part surface in the 3D viewer |
| DataBinding JOINT_ANGLE (SB1d) | Replay AFP robot arm motion from recorded joint-angle timeseries |
| Provenance graph (PROV1a, shipped) | `Coupon -[:PRODUCED_BY]-> Panel -[:DERIVED_FROM]-> AFP Run` — machine-readable chain |
| Templates (T1) | Enforce required metadata at DataObject creation (part ID, material spec, ply sequence file) |
| RO-Crate export (FS1g, shipped) | One-click citable export of a full AFP campaign |

---

### 2.2 Resistance / Ultrasonic Welding (MFFD)

**Data generated:**

| Data type | Format / rate | Currently in shepard | Gap |
|---|---|---|---|
| SCADA timeseries: current, voltage, power, force, displacement, process time | Timeseries scalar channels | Yes — via ProcessControl/ProcessMonitoring (ICINCO 2023) | — |
| Temperature: thermocouple array at weld line | 4–16 channels × time | Yes | Thermocouple positions not geometrically referenced |
| Post-weld NDT: ultrasonic C-scan of weld zone | 2D spatial image, void content map | As files | Not spatially bound to weld zone surface |
| Mechanical: tensile shear strength test | Load, area → strength [MPa] | Partial | Not linked to weld location |
| Micrograph: optical cross-section of weld zone | Images | As files | Not linked to specimen position |
| CAD: CFRP part geometry + weld line location | STEP | Not stored | No CadReference |

**Pain point:** ProcessControl and ProcessMonitoring already write SCADA timeseries to
shepard — this is the strongest existing ZLP deployment. The gap is geometric: the weld
line location is not referenced to part geometry, so "thermocouple at channel 7" is
a label, not a 3D position.

**shepard answer (with upcoming features):**

| Feature | How it helps |
|---|---|
| `CadReference` + STEP import (CAD1a) | Part geometry as a first-class payload |
| GeometryAnnotation SURFACE (SB1a) | Define the weld zone as a named surface region on the part |
| GeometryAnnotation POINT (SB1a) | Pin each thermocouple channel to its (x,y,z) position on the weld line |
| DataBinding BADGE (SB1a) | Display live or historical temperature at each thermocouple position in the 3D viewer |
| DataBinding HEATMAP (SB1c) | Void content map overlaid on the weld zone surface |
| DataBinding GLYPH (SB1e) | Force vector at actuator contact point during welding |
| Semantic relationship `CHARACTERISES` (SB1f) | C-scan `:CHARACTERISES` weld run — navigable in graph and SPARQL |
| Weld recipe versioning (V2b, aidocs/41) | Track changes to process parameters between runs |

---

### 2.3 Quality Assurance / NDT

**Data generated:**

| Data type | Format / rate | Currently in shepard | Gap |
|---|---|---|---|
| Ultrasonic C-scan | 2D spatial: amplitude at each (x,y) on part surface | As files | Not geometrically referenced |
| Active thermography | 2D thermal image sequence (temp over time at each pixel) | As files | Not geometrically referenced |
| Acoustic emission (AE) | Event timeseries: timestamp, amplitude [dBae], freq [kHz], risetime [µs], localised (x,y,z) | Not stored | No AE event payload kind |
| Penetrant testing results | Photographic images | As files | — |
| Defect catalogue | Type, location, size, severity per part | Not stored (structured) | No spatial binding to part CAD |

**Pain point:** NDT results are stored as files but are not geometrically referenced to
the part CAD. "Defect at (x=142 mm, y=380 mm)" in a C-scan is a coordinate in scan
space, not a location on the 3D part model. Spatial search — "show me all weld zones
with void content > 3% within 20 mm of a stiffener" — is not possible today.

**shepard answer (with upcoming features):**

| Feature | How it helps |
|---|---|
| `CadReference` + STEP import (CAD1a) | Part geometry as the spatial reference frame |
| GeometryAnnotation SURFACE (SB1a) | Register the scan area (C-scan, thermography footprint) to the part surface |
| DataBinding HEATMAP (SB1c) | C-scan amplitude or thermography image overlaid on part surface in 3D viewer |
| GeometryAnnotation POINT (SB1a) | AE sensor positions on the part; AE source localisations as event points |
| DataBinding GLYPH (SB1e) | AE source glyph at (x,y,z) localisation position, scaled by amplitude |
| Reverse lookup (SB1b) | "Where was this AE sensor?" from any timeseries search result |
| Semantic relationship `CHARACTERISES` (SB1f) | NDT DataObject `:CHARACTERISES` the manufactured part |

---

### 2.4 Material Characterisation (MEMAS)

**Data generated:**

| Data type | Format / rate | Currently in shepard | Gap |
|---|---|---|---|
| Tensile test: force-displacement → E-modulus, UTS, fracture strain | Timeseries + derived scalars | Yes — MEMAS (HMC project, aidocs/76 §4.1) | — |
| Compression test: same under compressive loading | As above | Yes | — |
| ILSS: load, thickness → ILSS [MPa] | Scalars | Yes | — |
| DIC (Digital Image Correlation): full-field displacement + strain maps | Dense 2D spatial grid over specimen, timeseries | As files | Not spatially bound to specimen geometry; GOM Aramis `.aramedit` and Vic-3D `.mat` formats not parsed |
| Microscopy: optical / SEM cross-sections | Images | As files | Not linked to the specimen location they show |
| Specimen metadata: material batch, layup sequence, cure cycle, dimensions | Structured JSON | Yes — MEMAS ontology annotations | — |

**Pain point:** MEMAS is ZLP's most mature shepard deployment — material property
storage with ontology annotations is working. The gap is the spatial layer: DIC strain
fields are dense 2D data over the specimen surface but are stored as opaque files, not
queryable spatial fields. A researcher cannot ask "show me the strain concentration at
the notch root across all specimens from batch M-047."

**shepard answer (with upcoming features):**

| Feature | How it helps |
|---|---|
| `CadReference` + simple geometry (CAD1a) | Specimen geometry as the spatial reference for DIC registration |
| DataBinding HEATMAP (SB1c) | DIC strain field overlaid on specimen surface; animatable across load steps |
| GeometryAnnotation POINT (SB1a) | Pin microscopy image to the specific specimen location it documents |
| Semantic relationship `COMPANION_TO` (SB1f) | DIC DataObject `:COMPANION_TO` load-cell timeseries — co-registered datasets |
| Reverse lookup (SB1b) | "Which batch did this specimen come from?" navigable from any search result |

---

### 2.5 Robotic Component Testing

**Data generated:**

| Data type | Format / rate | Currently in shepard | Gap |
|---|---|---|---|
| 6-DOF force/torque at robot TCP | Timeseries | Yes — Hanke et al. ETFA 2022 (Universität Augsburg) | Force glyph not anchored to contact point |
| Robot joint angles → TCP position/velocity | Timeseries | Yes | Trajectory not spatially bound to component geometry |
| Strain gauges on component, deflection sensors | Timeseries | Yes | Not spatially referenced |
| Camera images during loading (fracture events) | Images | As files | Not linked to component geometry |
| CAD: component geometry | STEP | Not stored | No CadReference |

**Pain point:** Robot trajectory and force data are in shepard but are not geometrically
referenced to the test component. The question "where exactly on the component did the
robot apply force when the acoustic emission event occurred?" requires manual post-processing.
Universität Augsburg used shepard for this campaign (ETFA 2022) — the CAD and spatial
binding layer is the missing piece for a full-depth deployment there too.

**shepard answer (with upcoming features):**

| Feature | How it helps |
|---|---|
| `CadReference` + STEP import (CAD1a) | Component geometry as the spatial reference |
| URDF bundle + joint annotation (CAD1d) | Robot kinematic model enabling trajectory replay in the 3D viewer |
| DataBinding JOINT_ANGLE (SB1d) | Replay robot arm motion from recorded joint-angle timeseries |
| DataBinding GLYPH (SB1e) | Force/torque vector at TCP contact point, spatially anchored |
| GeometryAnnotation SURFACE (SB1a) | Tag the contact zone, fracture surface, gauge locations |
| DataBinding BADGE (SB1a) | Live or historical strain gauge value displayed at sensor position |

---

## 3. Feature map: new capabilities → ZLP campaigns

| Feature | ZLP campaigns | Specific use |
|---|---|---|
| `CadReference` + STEP/IGES import (CAD1a–CAD1c) | All | Store nominal part geometry alongside process data |
| URDF bundle + joint annotation (CAD1d) | AFP, Robotic testing | Robot kinematic model for trajectory replay |
| GeometryAnnotation SURFACE (SB1a) | AFP, Welding, QA | Ply surface, weld zone, NDT scan area |
| GeometryAnnotation POINT (SB1a) | Welding, QA, Testing | Thermocouple position, AE sensor position, robot contact point |
| DataBinding BADGE (SB1a) | All | Live or historical value displayed at sensor location in 3D viewer |
| DataBinding HEATMAP (SB1c) | QA/NDT, DIC (MEMAS) | C-scan amplitude on part surface; DIC strain field on specimen |
| DataBinding JOINT_ANGLE (SB1d) | AFP, Robotic testing | Robot arm replay from recorded joint-angle timeseries |
| DataBinding GLYPH (SB1e) | Welding, Testing, AE | Force vector at TCP; AE source glyph at event (x,y,z) |
| Reverse lookup (SB1b) | All | "Where was this thermocouple?" navigable from any timeseries search |
| CPACS Annotator (aidocs/79) | AFP (path planning) | Nominal geometry → AFP robot path → actual layup comparison |
| RCE Integration (aidocs/80) | All MDO workflows | Process chain provenance: material → process → part → structural test |
| Semantic relationships (SB1f) | All | `CHARACTERISES`: C-scan characterises weld run; `COMPANION_TO`: DIC co-registered with load cell |

---

## 4. The process-to-performance chain

The central research question at ZLP: **which manufacturing parameters produce components
that meet structural performance requirements?** Today this chain exists in spreadsheets,
file-naming conventions, and researcher memory. shepard makes it machine-queryable.

The full chain as a Neo4j provenance graph:

```
(:DataObject "Material batch #M-047")
  -[:DERIVED_FROM]->
(:DataObject "AFP Layup Run #42")
  -[:DERIVED_FROM]->
(:DataObject "Post-cure Panel #P-112")
  -[:CHARACTERISES]->
(:DataObject "C-scan Panel #P-112")

(:DataObject "Coupon #C-112-T01")
  -[:PRODUCED_BY]->
(:DataObject "Post-cure Panel #P-112")

(:DataObject "Tensile Test Coupon #C-112-T01")
  -[:DERIVED_FROM]->
(:DataObject "Coupon #C-112-T01")
```

With spatial data binding (SB1a–SB1f), this entire chain is navigable in the 3D viewer:
load Panel P-112's `CadReference`, scrub through the AFP robot trajectory replay, see the
C-scan overlaid on the part surface, click a defect location → jump to the tensile test
result from the coupon cut at that location.

The Cypher query "find all panels where weld void content exceeds 3% AND coupon tensile
strength is below 400 MPa" becomes a 10-line graph traversal across provenance edges —
not a cross-referencing session across four spreadsheets.

---

## 5. ZLP as the reference deployment

ZLP should be the **shepard reference deployment** — the instance other DLR institutes
study before adopting. This is not a proposal to do something new; it is a proposal to
formalise and complete what is already happening.

### 5.1 What reference deployment means concretely

**Full feature coverage.** ZLP runs all five research campaigns with CAD/timeseries
spatial binding (SB1a–SB1f), CPACS and RCE integration (aidocs/79–80), and MEMAS
ontology annotations. No research group at ZLP has a data type that falls outside
shepard's payload model.

**Public process-to-performance dataset.** One AFP campaign exported as a citable
RO-Crate via KIP + InvenioRDM (aidocs/66, aidocs/72) and deposited in elib or Zenodo.
This is the "shepard showcase" — the dataset a DFG reviewer or a prospective adopter
can download, inspect provenance, and reproduce analysis on.

**DFG Bedarfsnachweis.** ZLP's deployment demonstrates demand beyond the tool's
developers. Every new external user (DLR-BT, Universität Augsburg, prospective NFDI
partners) points back to the ZLP running instance as the existence proof. The DFG
e-Research-Technologien proposal (aidocs/75) names ZLP's deployment explicitly in the
Bedarfsanalyse section.

**The Universität Augsburg connection.** Universität Augsburg already used shepard for
robotic component testing (Hanke et al. ETFA 2022, `aidocs/76 §2.1`) — the only
confirmed external-to-DLR adopter. ZLP's reference deployment is the instance Uni
Augsburg would connect to in a DFG Phase 2 pilot, converting an existing informal
relationship into a documented cross-institution deployment.

### 5.2 Maturity ladder: where ZLP is today vs. full depth

| Capability layer | Status today | To reach full depth |
|---|---|---|
| Timeseries ingest (AFP, welding SCADA) | Deployed | — |
| File storage + provenance (PROV1a) | Deployed | — |
| MEMAS ontology annotations | Deployed (HMC project) | — |
| ProcessControl / ProcessMonitoring | Deployed (ICINCO 2023) | — |
| RO-Crate export (FS1g) | Shipped | Configure one campaign for citable export |
| Unhide / HKG publication (aidocs/67) | Shipped | Wire up feed; agree PID policy |
| `CadReference` + spatial viewer (CAD1a–CAD1d) | Design (aidocs/78) | Implementation + ZLP campaign data as test input |
| Spatial data binding (SB1a–SB1f) | Design (aidocs/81) | Implementation; ZLP C-scan and DIC as validation datasets |
| CPACS Annotator (aidocs/79) | Design | ZLP AFP path-planning as first real-world CPACS use case |
| RCE deep integration (aidocs/80) | Design | Extend existing RCE2Shepard adapter with provenance tracking |
| CT voxel payload kind | Not yet designed | Requires new plugin; ZLP is the primary use case |
| DIC proprietary format adapters | Not yet designed | GOM Aramis `.aramedit`, Vic-3D `.mat` |

---

## 6. Gaps and open questions

This section is honest about what shepard does not yet handle well for ZLP. These are
gaps today, not "future opportunities."

**CT scan data.** Post-cure CT scans of AFP panels are 3D volumetric voxel data,
typically hundreds of gigabytes per scan. shepard has no voxel payload kind. CT data is
currently stored on institute file shares, outside any provenance graph. ZLP is the
primary use case that would justify a `shepard-plugin-ct` voxel payload kind.

**DIC proprietary formats.** GOM Aramis exports `.aramedit` files; Vic-3D exports `.mat`
files. Neither is an open format. Conversion to VTU (VTK Unstructured Grid) is possible
but lossy — coordinate system and metadata are partially preserved. shepard currently has
no import adapter for either format. A DIC plugin needs a reliable open-format bridge
before DIC data can be spatially bound to specimen geometry.

**Real-time process control feedback.** ProcessControl can write to shepard but cannot
read from shepard fast enough to close a control loop. Latency through the REST API is
too high for PLC-speed feedback at 100 Hz. The current integration is therefore one-way:
process parameters and SCADA streams are archived in shepard, but control decisions
cannot be fed back from shepard in real time. This is a fundamental latency limit of
the current architecture, not a configuration issue.

**Large-assembly STEP files.** AFP layup programs for full fuselage panels (as in MFFD)
can exceed 50 MB of STEP geometry. The cascadio WASM tier used in the current CAD
annotator design (aidocs/78) is insufficient for files of this size in-browser. A
`pythonocc` sidecar service is needed for server-side STEP tessellation and progressive
LOD delivery.

**Weld recipe versioning.** The current `Version` marker system in shepard tracks
DataObject versions but does not model changes to process parameter sets (weld recipe)
between runs. Welding engineers need to know "which recipe version was used in run #47,
and how does it differ from run #46?" The V2b snapshot mechanism (aidocs/41) is the
planned fix, but it is not yet shipped.

---

## 7. The ask

1. **Designate ZLP as the reference deployment site.** Formal acknowledgement — in the
   DFG proposal, in the upstream shepard README, and in internal DLR communications —
   that the shepard instance at ZLP Augsburg is the primary test and showcase environment
   for the fork's new capabilities.

2. **One champion per research group** to provide use-case requirements for the CAD1,
   SB1, CPACS1, and RCE1 feature slices. The champions are the validation path: new
   features are validated against real ZLP campaign data before they are documented as
   stable API.

3. **One citable export.** One AFP or welding campaign published as an RO-Crate via
   KIP + InvenioRDM as the showcase dataset for the DFG e-Research-Technologien proposal
   (aidocs/75). This requires designating one completed campaign, configuring the export
   pipeline, agreeing on a DOI policy, and depositing to elib or Zenodo.

4. **Access to Pandora converter and Paradigms kernel documentation** for the
   DLR-internal format adapters in CAD1j (aidocs/78). These are DLR-proprietary geometry
   kernels; the adapters cannot be implemented without internal documentation access.

Contact: Felix Krebs (fkrebs@nucli.de) — development lead for this fork.

---

## 8. See also

- `aidocs/74-dlr-bt-stakeholder.md` — DLR-BT stakeholder brief (comparison institute)
- `aidocs/75-dfg-eresearch-funding.md` — DFG e-Research-Technologien application sketch
- `aidocs/76-shepard-users-and-citations.md` — Known users and citations (ZLP publications catalogue)
- `aidocs/78-cad-geometry-annotator.md` — 3D Geometry and FEM Annotator design
- `aidocs/79-cpacs-annotator.md` — CPACS Annotator design
- `aidocs/80-rce-integration.md` — RCE Integration design
- `aidocs/81` — Spatial Data Binding design (forthcoming)
- `aidocs/73-dlr-stakeholder.md` — DLR-wide stakeholder brief
