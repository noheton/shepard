---
stage: feature-defined
last-stage-change: 2026-05-24
audience: contributor
related:
  - aidocs/data/35-hdf5-hsds-implementation-design.md (HDF5/HSDS — the canonical voxel substrate for reconstructed CT volumes)
  - aidocs/data/90-spatial-as-temporal-sweep.md (SPATIAL-V6 — CT slice reconstruction is row 6 in the §1 use-case catalogue; DIC strain field is row 15; UT NDT family is rows 3, 9–13)
  - aidocs/data/88-thumbnail-spi.md (per-payload thumbnail SPI — CT slice + first-frame video previews)
  - aidocs/integrations/97-shepard-plugin-ai-design.md (AI-V6 — anomaly detection capability and EmbeddingProvider SPI; CT defect classification rides this)
  - aidocs/strategy/74-dlr-bt-stakeholder.md (institute brief — CMC + ADAPT + H2 propulsion lens)
  - aidocs/strategy/82-zlp-augsburg-stakeholder.md (already flagged "no voxel payload kind" as a gap; this doc closes that with HDF5+SPATIAL-V6 reuse)
  - aidocs/strategy/94-federation-and-dataspaces.md (federation framing for CITE-DIGITAL relationship)
  - aidocs/integrations/96-metrology-spatial-analyzer.md (SA-Python sidecar pattern — relevant for any DIC sidecar)
---

# aidocs/107 — CITE + Nanotom as shepard data-management substrate

DLR-BT (Institut für Bauweisen und Strukturtechnologie) operates two
infrastructure clusters that produce the data shapes shepard is now
designed to carry: the **CITE** crash- and impact-test centre
(crash + high-speed event capture, full-scale aircraft structures)
and the **CT facility** (the high-resolution *nanotom* paired with
the large-volume *v|tome|x L240/450*, both run by the Ceramic
Composite Structures group under Raouf Jemmali in Stuttgart).
This doc walks each facility's native data shapes, maps them to
existing shepard substrates (HDF5/HSDS, SPATIAL-V6, AI-V6, the
File payload kind, PROV-O activity capture), names the per-facility
plugins, and lists honest concerns and a 7-row backlog wave to file
under the `CITE-*` and `CT-*` prefixes (both currently unused in
`aidocs/16`).

**For the record.** The task brief referenced "DLR Braunschweig";
DLR-BT operates at **Stuttgart and Augsburg** per
`aidocs/strategy/74 §1` and the institute's *Über uns* page. Both
CITE and the CT cluster are Stuttgart-based (Pfaffenwaldring 38–40,
70569 Stuttgart). All technical claims below are anchored to the
DLR primary-source pages cited inline.

The two eLib references in the brief — 220036 (DiCADeMa, cabin
manufacturing digitalisation; Malecha et al., 2025) and 219970
(MaSiMO, AAS-mediated autonomous quality inspection; Weiss et al.,
2025) — turned out on inspection to address adjacent BT/FK topics
(cabin manufacturing automation, AAS-mediated MRO inspection) and
not CITE or the CT cluster directly. They are noted here as the
broader BT/FK digitalisation context and are not cited as
facility-specific sources.

---

## §1 — TL;DR

**CITE** (Center for Crash and Impact Test) is a €25M flagship
testbed funded by Baden-Württemberg (Förderbescheid Feb 2023, build
ramp 2023–2027) and DLR. It extends BT's 30-year crash-and-impact
record by enabling **full-scale** crash and impact tests on small
aircraft, helicopters, air-taxis, and aircraft sub-assemblies under
all-weather-independent conditions, with integrated energy sources
(batteries, hydrogen tanks) and water-impact (ditching) scenarios.
Its data shapes are multi-modal: synchronised multi-camera
high-speed video at 1–10 kfps, distributed accelerometer and strain
arrays at 10–100 kHz, optical 3D deformation via DIC, pre- and
post-test 3D scans, and a planned **CITE-DIGITAL** platform that
fuses experimental and simulation data. shepard ships the
multi-modal capture container shape, the PROV-O test-event timeline,
and the SPATIAL-V6 row-15 DIC strain-field carrier; CITE-DIGITAL is
the federation partner above shepard, not a competitor (see §4).

**The CT cluster** has been operating since 2007 and pairs two
phoenix|x-ray systems (Baker Hughes / Waygate Technologies): the
**nanotom** (180 kV / 20 W nanofocus, 0.9 µm focal spot, ≈ 2 µm
voxel resolution, samples up to 100–150 mm Ø / 1 kg) for high-resolution
microstructure inspection — porosity, fibre architecture, joint
quality, small electronic and material samples — and the large
**v|tome|x L240/450** (240 kV microfocus + 450 kV minifocus, ≈ 5 µm
voxel, parts up to 800 mm Ø / 1000 mm H / 100 kg) for full
sub-assemblies, cast metal components, and large composite parts.
Both share one operator team, one reconstruction stack (phoenix
**datos|x**), and one analysis tradition (VG Studio MAX / Volume
Graphics). shepard ships the reconstructed-volume substrate via the
**HDF5/HSDS** payload kind (`aidocs/data/35`), the CT-slice-as-sweep
timeline via **SPATIAL-V6 row 6** (`aidocs/data/90`), the raw
projection stack as File-kind, and semantic defect annotations via
the existing annotation surface aligned to CHAMEO / Material OWL
(`aidocs/semantics/96`).

**What this doc contributes.** A cross-walk: facility data shapes
→ existing shepard substrate IDs. No new substrate, no parallel
storage tier. Two facility plugins (`shepard-plugin-ct` covering
both CT systems and `shepard-plugin-cite` as a thin federation
adapter to CITE-DIGITAL) carry the facility-specific ingest and
VIEW_RECIPE shapes. The joint workflow (§6) — a single CMC or CFRP
coupon impacted in CITE then post-mortem-scanned in the nanotom —
becomes the canonical f(ai)²r provenance demo for BT.

---

## §2 — CITE facility — data shapes

CITE is built around the BT *Structural Integrity* department's
existing infrastructure (drop tower, gas-gun lab, high-speed
testing machines) and adds full-scale crash and high-speed impact
capability, plus the digital backbone (CITE-DIGITAL) that ties
experiment and simulation together. The primary-source description
positions four CITE capabilities: full-scale testing coupled to
full-scale simulation; physical tests with integrated energy
sources (battery, hydrogen tank) including water-impact; experimental
and virtual certification up to TRL6; and certification-method
development that combines simulation with test validation
(`dlr.de/en/bt/.../projects/.../cite-center-for-crash-and-impact-test`).

The existing **Drop Tower** (Pfaffenwaldring) defines the baseline
shape: 800 kg max drop mass, 14 m/s, 60 kJ, specimen up to
920×920×980 mm; instrumented with load cells, triangulation lasers
for displacement, acceleration sensors, strain sensors, and
high-speed cameras (`dlr.de/en/bt/.../drop-tower`). The **sled**
infrastructure at the co-located Institute of Vehicle Concepts (FK)
on the same Stuttgart site gives the corresponding sled-test
numbers: 64 km/h at 1300 kg, 205 kJ, modular two-slide impactor;
3-axis and 6-axis force sensors up to 400 kN; strain gauges;
draw-wire potentiometers; onboard DAQ at up to 100 kHz; five
high-speed cameras (1024×1024 at 1–2 kfps and 1920×1440 at 1.125 kfps);
2D/3D point tracking in HS images; pre- and post-test 3D scan
(`dlr.de/en/fk/.../dynamic-high-strain-component-test-facility`).

For full-scale CITE tests (helicopter cabin, air-taxi airframe,
hydrogen-tank sub-assembly impact), the data shape generalises:

| Stream | Native form | Typical sampling | Volume per event |
|---|---|---|---|
| **High-speed video** (N cameras) | 12-bit raw frames; vendor codec (Vision Research `.cine`, Phantom `.mraw`, FastVideo `.fv`) | 1080p–4K, 1–10 kfps, 10–60 s capture per camera | 100–500 GB per camera per event; N=5–20 cameras → multi-TB per event |
| **Accelerometer arrays** | Multi-channel time series; PCB / Endevco / Kistler ICP sensors; sometimes IEPE | 10–100 kHz per channel; 8–256 channels typical | 100 MB – 5 GB per event |
| **Strain gauges / FBG fibre** | Time series; Wheatstone-bridge gauges or fibre-Bragg multiplexed | 10–50 kHz per channel; 16–512 channels | 100 MB – 5 GB per event |
| **Load cells / force sensors** | Time series, 3- or 6-axis | 10–100 kHz | 10–500 MB per event |
| **DIC photogrammetry** | Stereo image pairs → strain tensor field on a surface mesh; GOM ARAMIS, LaVision StrainMaster | 100 Hz – 5 kHz frame; 1k–100k surface points | 5–50 GB raw; 1–10 GB strain field |
| **Pre-test 3D scan** | Static point cloud / mesh (handheld or stationary laser scanner) | one-shot | 100 MB – 5 GB |
| **Post-test 3D scan** | Same; captures permanent deformation | one-shot | 100 MB – 5 GB |
| **Post-test CT** (joint workflow, §6) | Reconstructed voxel volume | one-shot | 50–200 GB |
| **Test setup metadata** | Free-text + structured (specimen ID, instrumentation map, calibration certs, environmental conditions) | one-shot | KB |
| **Simulation companion** (CITE-DIGITAL) | LS-DYNA / PAM-CRASH `.d3plot`, `.k`; per-state binary | scenario-dependent | 10–100 GB |

A CITE event has a sub-millisecond time axis (the impact lasts
1–30 ms) and a multi-second context window. Timing synchronisation
across the streams is the dominant data-quality concern: the
HS-cameras, the DAQ chassis, and the DIC system are typically
triggered by a single shared TTL pulse; the timestamp accuracy
target is ≤ 1 µs. The PROV-O test-event timeline carries the
trigger reference as the canonical `t = 0`.

A campaign aggregates 5–50 events with shared instrumentation maps,
calibration certificates, and specimen lineage. The CITE-DIGITAL
platform's stated role is to integrate experiment and simulation
data and serve as an exchange platform with industry partners; that
positions it as a federation layer rather than a single-instance
RDM substrate. See §4 for shepard's complementary role.

---

## §3 — Nanotom + v|tome|x L240/450 — data shapes

The BT CT facility operates two phoenix|x-ray (Waygate Technologies)
systems independently and in complement. The **nanotom**
(`dlr.de/de/bt/.../hochaufloesende-ct-anlage-nanotom`) is the
high-resolution end: 180 kV / 20 W nanofocus tube, 0.9 µm minimum
focal spot, 2300×2300-pixel detector at 50 µm pitch (12–16 bit),
≈ 2 µm voxel resolution at maximum magnification, samples up to
100–150 mm diameter and 1 kg. Typical samples: ceramic-matrix
composite (CMC) sub-elements (the BT institute's signature material;
`dlr.de/de/bt/.../cmc-technologie-und-strukturbauteile`), CFRP
coupons, additively-manufactured metal samples, joint inspection,
electronic-assembly inspection. The **v|tome|x L240/450** handles
the large-volume end: dual tubes (240 kV microfocus and 450 kV
minifocus) on a 4048×4048 / 100 µm-pitch detector (14 bit),
≈ 5 µm voxel resolution at best, sub-assemblies up to 800 mm Ø ×
1000 mm H × 100 kg, raw parts up to 1000×1500 mm.

The reconstruction-pipeline output shape is shared between the two
systems (same vendor stack: phoenix **datos|x** for acquisition
and reconstruction; downstream analysis typically in **VG Studio
MAX** by Volume Graphics):

| Artefact | Native form | Typical size |
|---|---|---|
| **Raw projection stack** | 2D X-ray projections, vendor-format (`.tif` or `.raw`), one per angular position | nanotom: 2300×2300×{1500–3000 projections}×12–16 bit ≈ **20–50 GB** per scan; v|tome|x: 4048×4048×{1500–3000}×14 bit ≈ **80–200 GB** per scan |
| **Reconstructed voxel volume** | 3D float / 16-bit unsigned `.vol` or stack of 16-bit TIFFs (one per Z-slice) | nanotom: a 2000³ volume at 16-bit ≈ **15 GB**; in practice 50–150 GB with crop margins and full-bit-depth reconstructions; v|tome|x: 100–500 GB |
| **Slice stack** (derived view of the volume) | Per-Z `.tif` or `.png` series | full-resolution: same magnitude as the volume; downsampled previews: 100 MB – 5 GB |
| **Defect map / segmentation** | Region masks (`.nii.gz` / `.h5` / per-defect bounding boxes + class labels in `.json` / `.xml`) | 10 MB – 1 GB per scan |
| **Reconstruction metadata** | Scan parameters, geometry calibration, ring-artefact correction settings (vendor XML + log) | KB |
| **Quality-assurance report** | PDF or vendor `.vgreport` | MB |

A representative throughput shape: a research-grade campaign on the
nanotom produces 5–15 scans per week (each is a 4–12 hour
acquisition; reconstruction adds 0.5–4 hours on a workstation),
which translates to **1–3 TB of new data per week**. The large
v|tome|x is closer to 1–3 scans per week at 100–500 GB each, so
**0.5–1.5 TB per week**. Aggregated CT-facility throughput is in
the low single-digit TB/week range, with peaks during industry
projects.

Defect-type vocabulary for the CMC and CFRP use cases includes:
**porosity**, **voids**, **delamination**, **fibre waviness**,
**fibre breakage**, **matrix cracking**, **ply wrinkling**, **lack
of consolidation**. For CMC specifically: **silicon-rich pockets**
(LSI process artefact), **uncarbonised polymer residue**, **fibre
pull-out**. These align cleanly to existing CHAMEO and Material OWL
ontology entries (`aidocs/semantics/96`); shepard's existing
semantic-annotation surface (SEMA-V6, per `aidocs/semantics/100`)
carries them on the CT DataObject without new vocabulary work.

A workflow consideration: the reconstructed volume's *raw form* is
the working artefact for the research scientist (sliced and
re-rendered live in VG Studio), and the *raw projection stack* is
the working artefact for the reconstruction specialist who tunes
algorithm parameters (Feldkamp, iterative, ring correction). Both
have legitimate "I need to keep this byte-identical" demands; the
shepard substrate ships both.

---

## §4 — Shepard mapping for CITE

The CITE data shape lands on shepard substrates the v6 design
already covers; no CITE-specific storage tier is needed. The
mapping by stream:

**Multi-camera high-speed video.** Each camera's `.cine` / `.mraw`
file lands as a **File payload** under a `VideoFileReference`
(per `aidocs/data/53` rename of `FileContainer` semantics — video
content is a first-class file kind for the post-rename shape).
Per-frame metadata (trigger offset, exposure, gain) lives in the
attached attribute map; the canonical `t = 0` is the shared trigger
in the parent test-event Activity. The thumbnail SPI
(`aidocs/data/88`) generates a first-frame preview server-side via
the existing video sidecar.

**Accelerometer / strain / load-cell time series.** Lands in the
existing Timeseries payload (TimescaleDB substrate). Each channel
is a `:Channel` individual under the post-`TS-IDc` migration
(`aidocs/platform/87`) — single appId per channel, no 5-tuple
friction for ML pipelines. Per-channel metadata (sensor make /
model, calibration cert ID, mounting location, sensitivity) lives
on the `:Channel` node; the calibration certificate itself lands
as a File payload linked by `:CHARACTERISES` (per
`aidocs/strategy/82` SB1f) to the channel — closing the EN 9100
traceability gap that strategy/82 flagged.

**DIC photogrammetry strain field.** The DIC stream is *exactly*
**SPATIAL-V6 row 15** in the §1 catalogue of `aidocs/data/90`:
strain tensor on a `TIN_Z` or `MULTIPOINTZ` surface mesh, 1–100 Hz
frame rate, static surface mesh (the specimen surface) with time as
the brush axis. Each frame is one row in the SPATIAL-V6 hypertable;
the `BrushTraceView` Vue component renders strain magnitude or
principal-strain direction as the per-vertex colour. No new VIEW_RECIPE
is needed; the existing `shepard:BrushTraceShape` with
`valueChannel='strain_e1'` (or similar) covers it.

**Pre- and post-test 3D scans.** Lands in shepard-plugin-spatial as
static `:SpatialDataContainer` instances (the pre-test surface
mesh is the static anchor for DIC; the post-test scan registers
permanent deformation). Frame registry per CST1
(`aidocs/data/85`) links both to the same specimen frame.

**Test-event timeline (PROV-O Activity).** The whole test-event
shape — preparation, calibration check, trigger, impact, post-test
inspection — is a chain of typed `:Activity` instances per the
f(ai)²r model. The trigger event carries the shared TTL timestamp
as the canonical `prov:atTime`; downstream streams reference it by
`prov:wasGeneratedBy → :Activity[appId]`. This makes the
"which streams belong to this event?" query a single graph
traversal rather than a timestamp range match.

**AI plugin for damage-pattern recognition.** AI-V6 anomaly detection
on the time-series streams (per `aidocs/integrations/97 §8`) — TR-004
in the LUMEN synthetic showcase is the prototype; CITE's
accelerometer signature of a wing-spar buckle is the production
target. The semantic embedding surface
(`aidocs/integrations/97 §6`) lets a researcher find similar past
events ("show me past tests where the strain field looked like
this") via vector search.

**Relationship to CITE-DIGITAL.** CITE-DIGITAL is the funded BT
project to build the experimental ↔ simulation fusion platform. Per
the public description, its scope is the **integration layer**:
experiment data and simulation data brought together for
correlation, simulation-method validation, and partner exchange.
shepard's natural position is **a substrate beneath** the
researcher-facing CITE-DIGITAL interface — the place where the raw
HS-video and the per-channel TS and the DIC field actually live,
with the byte-identical guarantees, the PROV-O audit trail, and
the f(ai)²r typed activity capture that publication and
certification demand — and **a federation partner** at the
dataspace layer (per `aidocs/strategy/94`) for cross-institute
exchange. shepard does not propose to replicate CITE-DIGITAL's
simulation-correlation tooling. The shape of that integration is
itself a CITE-DIGITAL-led conversation; the open question is
captured in §10 as `CITE-FEDERATION-PROBE`.

---

## §5 — Shepard mapping for Nanotom + v|tome|x L240/450

The CT data shape splits across two substrates by *question shape*,
not by data substrate count. The researcher who asks "show me the
volume" wants the reconstructed voxels; the researcher who asks
"what was happening at slice 423 during the reconstruction" wants
the per-slice timeline. shepard carries both without duplication.

**Reconstructed voxel volume → HDF5/HSDS plugin (`aidocs/data/35`).**
This is the canonical substrate. A converted `.vol` lands as an
HDF5 file with one main dataset (`/reconstructed/volume`) at
`uint16` or `float32`, plus a `/metadata/` group carrying the scan
parameters and geometry calibration. `h5pyd` over HSDS gives the
researcher's existing `pandas` / `numpy` / `scikit-image` pipeline
a slicing API identical to local `h5py.File()` — no analysis-code
rewrite needed (this is the hard-constraint from
`aidocs/data/35 §1`). A **vendor-format pass-through** (the original
`.vol` and the original projection stack as File payloads) ships
alongside the HDF5 for analysts who require the vendor-original
artefact for tooling like VG Studio MAX or for tracable
reconstruction-parameter changes.

**CT slice reconstruction timeline → SPATIAL-V6 row 6
(`aidocs/data/90 §1`).** The CT reconstruction is itself a brush
sweep along the stage-Z axis: each slice is a `POLYGONZ` or
`TIN_Z` profile anchored at a Z stage position, with measurements
like `mean_density`, `defect_pixel_count`, `void_fraction_pct` per
slice. The `BrushTraceView` then renders the volume's "skin" as a
swept ruled surface with per-slice colouring — useful for the
quality-engineer dashboard where the question is "where along the
specimen do voids cluster?" not "what's the full volumetric
structure?". This view co-exists with the HDF5 voxel volume; the
two answer different questions on the same data.

**Raw projection stack → File payload.** The acquisition-stage
TIFF / `.raw` files land as File payloads under a single
`:DataObject` representing the scan. Stored byte-identical so the
reconstruction specialist can re-run datos|x with different
algorithm settings (Feldkamp variants, iterative reconstruction,
ring-artefact correction parameters). Each re-reconstruction
becomes a new HDF5 payload version (per `aidocs/data/46` payload
versioning) tied to the same source projection stack via PROV-O
`prov:wasDerivedFrom`. The reconstruction parameter set lives in
the `:Activity.attrs` for the reconstruction Activity.

**Defect map / segmentation.** Lands as an HDF5 companion dataset
(`/defects/mask` in the same file as the volume, or a separate
HDF5 file linked by `:CHARACTERISES`). Per-defect bounding boxes
and class labels also project to **semantic annotations** on the
DataObject, with the defect-class vocabulary aligned to CHAMEO /
Material OWL (`aidocs/semantics/96`) and CMC-specific extensions
recorded in the BT institute's local SHACL shapes registry
(`aidocs/semantics/95`). This gives the researcher both the
machine-readable per-voxel mask (for re-analysis) and the
graph-queryable annotation (for "find all CMC scans with
silicon-rich pockets ≥ 2 mm²").

**AI plugin for defect classification.** The AI-V6 spec
(`aidocs/integrations/97 §12 row 12`) explicitly flags multimodal
image embeddings as Phase-3 / out-of-v0-scope. CT defect
classification is the canonical Phase-3 candidate: a 3D-CNN or
ViT-based defect detector exposed as a *capability* on the
existing AI plugin SPI rather than a new plugin. v0 carries the
data; v3 adds the classifier as an `EmbeddingProvider` extension
with `imageDimension=512` (CLIP-style) or a dedicated
`ClassifierProvider` SPI. Filed in §10 as `CT-AI-DEFECT-CLASS-V3`.

**Thumbnail strategy.** Per `aidocs/data/88` (Thumbnail SPI), a CT
DataObject ships three thumbnails: the volume's central axial,
sagittal, and coronal slice as PNG. The HDF5 plugin generates them
on ingest via a Python sidecar reading the HDF5 with `h5py` and
writing slice PNGs to Garage. This makes the CT DataObject
human-recognisable in the collection-list view without a full VG
Studio session.

**The vendor-stack boundary.** phoenix datos|x is the acquisition
and reconstruction tooling; VG Studio MAX is the analysis tooling.
shepard ingests the *output* of each (the projection stack from
datos|x acquisition, the reconstructed volume from datos|x
reconstruction, the segmentation mask from VG Studio analysis). It
does not propose to replace either. The HDF5 export from datos|x is
the canonical ingest path; for sites where datos|x exports
vendor-binary `.vol`, a one-off converter sidecar (the existing
HDF5 plugin's ingest fan-out) handles the conversion. Filed as
`CT-DATOSX-INGEST` in §10.

---

## §6 — Joint workflow: one CMC sub-element across CITE + Nanotom

The killer demo for the BT institute, and the prototype for the
multi-facility f(ai)²r provenance story, is **one CMC or CFRP
sub-element's full lifecycle in one shepard Collection**. The CMC
case is the more BT-native one (per
`dlr.de/de/bt/.../cmc-technologie-und-strukturbauteile`); the CFRP
parallel maps onto the existing MFFD showcase shape (§8).

The narrative, as a researcher tells it:

> *We took a CMC sub-element from the LSI process line, scanned it
> in the nanotom as the as-manufactured baseline, instrumented it,
> dropped it in the drop tower at 6 m/s, captured the impact with
> three HS cameras and 32 accelerometers and DIC on the rear face,
> then put it back in the nanotom to map the post-impact damage.
> We want to compare the as-manufactured void distribution against
> the post-impact damage to ask: where did damage propagate from?*

The shepard data model carries this as **one `:Collection`** with
six DataObjects in a PROV-O lineage chain:

```
DO-001 "as-manufactured nanotom scan"
   └─ HDF5 payload (volume) + File (projection stack) + annotations (void distribution)
   ↓ :PREDECESSOR_OF
DO-002 "specimen instrumentation"
   └─ File (instrumentation diagram, calibration certs)
   ↓ :PREDECESSOR_OF
DO-003 "impact event" (the test-event Activity timeline anchor)
   └─ File payloads (3× HS-camera .cine; the test plan PDF)
   └─ Timeseries (32× accelerometer; 16× strain)
   └─ Spatial container (DIC strain field; SPATIAL-V6 row 15)
   └─ Annotations (peak acceleration, peak strain location)
   ↓ :PREDECESSOR_OF
DO-004 "post-impact nanotom scan"
   └─ HDF5 payload (volume) + File (projection stack) + annotations (damage map)
   ↓ :PREDECESSOR_OF
DO-005 "damage analysis" (comparison + report)
   └─ File (analysis report PDF) + AI-V6 annotations (defect-growth
   delta vs DO-001 via image embedding)
   ↓ :PREDECESSOR_OF
DO-006 "publication snapshot" (RO-Crate export, DataCite-ready)
   └─ Snapshot (per aidocs/data/41) freezing the whole chain
```

Each step's Activity is `:wasAssociatedWith` an `:Agent`
(researcher) and ‒ where AI is used ‒ a typed AI-V6 Activity per
the f(ai)²r 🧑/🤖/🤝 mode tagging
(`project_ai_human_collab_provenance.md`). The certification
auditor's query "show me the calibration certificates for the
accelerometers used in event DO-003, in force at the time of the
test" is a one-hop graph traversal:
`DO-003 ─[:WAS_GENERATED_BY]→ :Activity ─[:USED]→ :Channel ─[:CHARACTERISES]← :CalibrationCert`.

The whole chain compresses into a single Collection-level snapshot
(`aidocs/data/41`); the snapshot's SHA-256 is the publication
fingerprint cited in the paper. The published RO-Crate carries the
full lineage, the controlled-vocabulary annotations, the byte-identical
data, and the typed Activity log — meeting Helmholtz HMC FAIR
requirements and EN 9100 traceability simultaneously. This is the
v6 elevator-pitch: *one platform carries the dataset that becomes
the certification artefact that becomes the published paper*.

---

## §7 — Per-facility plugin shape

Following the **plugin-first** rule in `CLAUDE.md` and the
**plugins-declare-own-sidecars** rule
(`feedback_plugins_declare_sidecars.md`), the facility-specific
ingest and viewer logic lands in two plugins.

### 7.1 `shepard-plugin-ct` (Nanotom + v|tome|x L240/450)

**Why one plugin, not two.** The two CT systems share the vendor
stack (phoenix|x-ray / Waygate / datos|x), one operator team, the
same downstream analysis tradition (VG Studio MAX), and substantially
the same data shapes (raw projection stack + reconstructed volume
+ optional segmentation). Splitting them gains nothing and doubles
the maintenance surface. The plugin distinguishes the two systems
by an `instrument_id` attribute on the DataObject (`nanotom-2007`
vs `vtomex-L240-450-2007`) and per-instrument calibration metadata.

**Components.**

- **Vendor-format ingest sidecar.** Python service that ingests
  datos|x output (the `.vol` file and the projection-stack
  directory) and emits HDF5 (for shepard-plugin-hdf5 to land in
  HSDS) plus the projection-stack TIFFs as File payloads. Declares
  dependency on shepard-plugin-hdf5 in the manifest.
- **Slice-stack VIEW_RECIPE.** A `shepard:CtSliceStackShape`
  SHACL shape with companion Vue 3 viewer that loads three
  orthogonal slices (axial / sagittal / coronal) from HSDS via
  `h5pyd` over HTTP; user scrubs through Z. Builds on
  `BrushTraceView` only conceptually — the CT-volume viewer is a
  different render pipeline (volume rendering vs ruled-surface
  brush), so it ships as a separate component but reuses the
  SHACL-as-VIEW-RECIPE machinery from `aidocs/semantics/98`.
- **Defect-annotation VIEW_RECIPE.** Overlay layer on the slice
  viewer that reads bounding-box annotations and draws them on
  the slice. Reuses the existing `AddAnnotationDialog` shape.
- **Thumbnail provider.** Implements the Thumbnail SPI
  (`aidocs/data/88`) for HDF5-CT DataObjects, producing the
  three central-slice PNGs server-side.
- **CMC defect-vocabulary SHACL.** A SHACL shape file under
  `plugins/ct/src/main/resources/shapes/` enumerating the CMC and
  CFRP defect classes; aligns to CHAMEO and Material OWL via
  `owl:equivalentClass` declarations.
- **Plugin docs trinity** (`feedback_plugins_ship_own_docs.md`):
  `reference.md`, `quickstart.md`, `install.md` under
  `plugins/ct/docs/`. Install notes the HDF5 plugin as a
  hard dependency and recommends the Garage SSD tier (per
  `project_storage_s3_garage.md`) for the volume bucket given
  the per-scan size.

**What this plugin does NOT introduce.** No new payload kind
(HDF5 already exists; File already exists; SPATIAL-V6 already
covers the slice timeline; annotations exist). No new storage
substrate. No vendor-format storage as primary; HDF5 export is
the canonical landing point, with vendor formats as
byte-identical companion File payloads.

### 7.2 `shepard-plugin-cite` (CITE federation adapter — open shape)

**Why this plugin's shape is open.** CITE-DIGITAL is the funded BT
platform for the experiment-↔-simulation fusion layer; it has not
yet shipped its public API surface (project ramp 2023–2027). The
shepard-side plugin shape depends on what CITE-DIGITAL exposes.
Two plausible end states:

- **(a) shepard-as-substrate.** CITE-DIGITAL adopts shepard as the
  raw-data substrate underneath its researcher-facing interface;
  the plugin is then a *thin extension* that adds CITE-specific
  VIEW_RECIPE shapes (multi-camera synchronised playback,
  test-event timeline with trigger overlay, instrumentation-map
  visualisation) and CITE-specific ingest helpers (Vision Research
  `.cine` and Phantom `.mraw` decoders; LS-DYNA `.d3plot`
  decoder for the simulation-companion landing).
- **(b) shepard-as-federation-partner.** CITE-DIGITAL ships its
  own substrate; shepard federates at the dataspace layer (per
  `aidocs/strategy/94`). The plugin then is an outbound adapter:
  shepard exports DataObject snapshots as CITE-DIGITAL-consumable
  artefacts via a documented manifest format; shepard imports
  CITE-DIGITAL references as `:DataObjectReference` with a
  `cite-digital` source-kind label.

The two shapes are not mutually exclusive — some research groups
will adopt (a) for their internal data while the institute-level
exchange uses (b). The plugin should be written so its capability
declarations gracefully degrade between the two modes. Filed in
§10 as `CITE-FEDERATION-PROBE` to schedule a CITE-side conversation
before any plugin code is committed.

**Common components regardless of mode.**

- **Test-event timeline VIEW_RECIPE.** A SHACL shape rendering the
  PROV-O Activity chain of a CITE test as a horizontal timeline
  with synchronised trigger overlay and per-stream timing markers
  (HS-camera trigger, DAQ trigger, DIC trigger). Reusable beyond
  CITE.
- **Multi-camera synchronised playback VIEW_RECIPE.** Vue 3
  component rendering N HS-camera streams in a grid with one
  shared time scrubber and shared `t = 0` from the trigger
  Activity. The video sidecar (per `aidocs/data/53` /
  `project_video_plugin`) handles each stream's transcoding.
- **HS-video ingest helpers.** Decoders for `.cine` (PCC SDK
  Python binding) and `.mraw` (Phantom Camera Control SDK)
  emitting MP4 + per-frame metadata JSON. Vendor SDKs are
  free-as-in-beer; license terms allow integration.
- **Plugin docs trinity.** Same shape as `shepard-plugin-ct`.

---

## §8 — MFFD parallel

The CITE + Nanotom story shares core data-shape DNA with the
existing MFFD showcase:

- MFFD AFP layup + ultrasonic welding **is** the SPATIAL-V6
  brush-sweep use case 1 (AFP head sweep) and use case 9–13
  (NDT inspection). CITE DIC strain field is row 15. The CT slice
  reconstruction is row 6. **Three different facility classes,
  one substrate.**
- MFFD's NDT-after-each-step pattern (AFP layup → NDT → next step
  with rework loop) is *exactly* the CITE-then-Nanotom shape at
  a coarser time scale: process → inspection → branch on result.
  The Predecessor/Successor chain shape carries both.
- AI-V6 anomaly detection trained on MFFD AFP sensor data (the
  Q1 consolidation-force-drop synthetic anomaly) generalises to
  CITE impact-event accelerometer signatures via the same SPI;
  CT defect classification is the Phase-3 image-embedding
  extension.
- The TR-004 / LUMEN "anomaly → investigation → repair → re-test"
  pattern is the same shape as "CITE impact → post-mortem CT →
  damage analysis → spec revision". The provenance chain
  template is identical.

The deliberate consequence: BT can adopt the existing MFFD
showcase data shape directly. The seed shape, the snapshot shape,
the SPATIAL-V6 viewer, the AI-V6 anomaly detection, the f(ai)²r
typed Activity capture — all transfer without modification. Only
the facility-specific ingest sidecars and the CITE/CT VIEW_RECIPE
shapes are new. **This is the v6 dividend BT realises**: paying
the substrate cost once and amortising across LUMEN, MFFD, CITE,
and the CT cluster.

---

## §9 — Honest concerns

**Data volume.** A research-grade CT campaign produces 1–3 TB of
voxel data per week (§3). CITE full-scale tests produce multi-TB
per event from the HS-camera bank alone. Annual aggregate at BT
is plausibly in the **0.5–1 PB range**, with hot-tier (last six
months for active analysis) closer to 50–150 TB. Garage on
commodity hardware (per `project_storage_s3_garage.md`) handles
this at single-digit-€/TB-month operating cost, but the **storage
plan needs an honest hardware budget conversation with the
institute** before adoption. The HDF5 plugin's HSDS substrate has
its own scaling characteristics (HSDS is an HTTP-fronted chunked
HDF5 service; per-chunk caching is the dominant performance lever);
benchmarking at the BT volume scale is **`CT-HSDS-SCALE-AUDIT`**
in the backlog.

**Industrial confidentiality.** CITE tests are routinely
commissioned by aircraft OEMs (Airbus, Pilatus, eVTOL startups)
or certification authorities (EASA, FAA) under NDA. Crash data
on a specific airframe can be commercially sensitive years after
the test. shepard's role-based access control (`aidocs/auth/...`
existing) handles per-Collection and per-DataObject ACLs; the
**embargo workflow** (per `aidocs/research-data-manager` agent
findings — currently no `embargo_until` field on DataObject) is
the gap. Filed as `CITE-EMBARGO-FIELD` in §10. The CMC defect-map
data has similar concerns at lower stakes; the same field closes
both gaps.

**Facility-specific equipment integration cost.** The
HS-camera-decoder sidecars (Vision Research `.cine`, Phantom
`.mraw`) require the vendor SDKs; integration is not free. The
DIC integration with GOM ARAMIS or LaVision StrainMaster needs a
per-vendor adapter. The phoenix datos|x export pipeline needs a
vendor-format converter (datos|x can export HDF5 natively in
recent versions; older installations export `.vol` and require
the conversion sidecar). Each facility plugin's integration cost
is **1–3 person-weeks per vendor adapter**; staging by adoption
priority is the realistic plan.

**Sub-millisecond timing.** A 1 µs timing-accuracy target across
N independent acquisition systems is *not* what generic NTP gives
you. CITE's TTL-trigger discipline solves the hard problem at the
trigger; the ingest pipeline needs to *preserve* the trigger as
the canonical `t = 0` for every stream and not lose sub-millisecond
offset metadata in the conversion. The current Timeseries ingest
preserves microsecond timestamps; the video-file payload metadata
needs to carry the trigger-offset and the per-frame timestamps as
attributes. Filed as `CITE-TRIGGER-PROVENANCE`.

**CITE-DIGITAL boundary.** Until CITE-DIGITAL's public surface is
defined, the `shepard-plugin-cite` shape is speculative (§7.2). The
honest answer is to schedule a conversation with the CITE-DIGITAL
team before any code lands; the federation-or-substrate decision
is theirs to lead and shepard's to support. The risk of
mis-positioning here is institutional — CITE-DIGITAL is a flagship
BT-led project, and a parallel-substrate framing in shepard's
external comms would be politically corrosive. Per
`feedback_respectful_predecessor_framing.md` the framing in this
doc is *complementary substrate / federation partner*, never
*alternative*.

**Volume rendering in the browser.** A 100 GB CT volume cannot be
fully loaded into a browser session; the slice-stack viewer is the
practical baseline. Full volume-rendering in the browser
(WebGL2-based ray-marching, NVIDIA IndeX-style) is a Phase-3
capability and probably belongs as a separate `vis-volume`
package rather than the slice-stack viewer. Filed as
`CT-VOLUME-RENDERER-V3`.

---

## §10 — Backlog rows (file under `aidocs/16` `CITE-*` + `CT-*`)

Seven rows. Both prefixes are currently unused in the dispatcher
backlog (verified via `grep -E "^\| (CITE|CT-)"`). Filed wave-by-wave;
no row blocks the SPATIAL-V6 or HDF5 substrate work that is the
existing precondition.

| ID | Description | Size | Status hint | Depends on |
|---|---|---|---|---|
| `CITE-FEDERATION-PROBE` | Schedule a 60-min conversation with the CITE-DIGITAL technical lead at DLR-BT to lock the federation-vs-substrate boundary (§4 + §7.2). Output: a one-page memo that resolves shepard-plugin-cite's shape choice between modes (a) and (b). No code change until the memo lands. | S | queued (gate before code) | nil |
| `CITE-EMBARGO-FIELD` | Add `embargo_until` (timestamp) + `publication_state` (enum: `internal` / `restricted` / `embargo` / `public`) to `:DataObject`. Single Cypher migration; UI surface in the DataObject metadata panel; permission filter integrates with existing role check. Closes the CITE NDA workflow gap (§9) and the strategy/82 / research-data-manager gap simultaneously. | M | queued | nil |
| `CITE-TRIGGER-PROVENANCE` | Preserve the test-event trigger as the canonical `prov:atTime` on the test-event Activity; per-stream offset metadata carried in attribute map for HS-video File payloads. Required to keep sub-millisecond cross-stream alignment after ingest. | S | queued | requires CITE-FEDERATION-PROBE outcome (mode (a) makes this internal; mode (b) federation may push it upstream) |
| `CT-DATOSX-INGEST` | Vendor-format ingest sidecar for phoenix datos|x output: ingest `.vol` + projection-stack TIFF directory, emit HDF5 (for shepard-plugin-hdf5 landing) plus byte-identical projection-stack File payloads. Implements the §5 + §7.1 ingest pipeline. | M | queued | aidocs/data/35 HDF5 plugin landed |
| `CT-SLICE-VIEWER` | `shepard:CtSliceStackShape` SHACL VIEW_RECIPE + Vue 3 component loading three orthogonal slices via `h5pyd` over HSDS. Thumbnail SPI implementation for HDF5-CT DataObjects produces the three central-slice PNGs. | M | queued | aidocs/data/35 + aidocs/data/88 |
| `CT-HSDS-SCALE-AUDIT` | Substrate-direct audit of HSDS at the BT volume scale: ingest the equivalent of one week of CT data (≈ 2 TB; mix of nanotom and v|tome|x scans), exercise concurrent slice-read patterns, measure P95 latency and chunk-cache hit rate. Mirror of the TS-audit shape (`aidocs/agent-findings/ts-design-audit-2026-05-24.md`). | M | queued | CT-DATOSX-INGEST + CT-SLICE-VIEWER |
| `CT-AI-DEFECT-CLASS-V3` | Phase-3 AI capability: 3D-CNN or ViT defect classifier exposed as a new `ClassifierProvider` SPI on the AI plugin (parallel to `EmbeddingProvider`). Training data: existing BT CMC defect-mask archive (institute-supplied; not synthetic). f(ai)²r typed `:AnnotatedBy` Activity captured per inference. | L | queued (v3) | AI-V6-FUTURE-MULTIMODAL + a published defect-class dataset |
| `CT-VOLUME-RENDERER-V3` | Phase-3 in-browser volume renderer (WebGL2 ray-marching). Separate `vis-volume` package; complementary to CT-SLICE-VIEWER not replacement. Performance target: interactive frame rate at 512³ downsampled volume; full-resolution streamed by tile. | L | queued (v3) | CT-SLICE-VIEWER (proves the data path); WebGPU adoption decision |

(The `shepard-plugin-ct` and `shepard-plugin-cite` plugin-scaffold
creation rows are implicit in `CT-DATOSX-INGEST` and the
post-`CITE-FEDERATION-PROBE` work respectively; they don't need
separate rows.)

---

## §end — Companion-doc updates required

Per the `CLAUDE.md` continuous-doc-maintenance rule, the same PR
that lands this design should also touch:

- `aidocs/strategy/74-dlr-bt-stakeholder.md` — add CITE + CT
  facility names to the §2 use-case catalogue, cross-reference
  this doc.
- `aidocs/strategy/82-zlp-augsburg-stakeholder.md` — flip the
  "no voxel payload kind" gap row to "addressed via HDF5/HSDS
  plugin + this doc".
- `aidocs/data/00-model-inventory.md` — note the `embargo_until`
  + `publication_state` fields on `:DataObject` once
  `CITE-EMBARGO-FIELD` ships.
- `aidocs/16-dispatcher-backlog.md` — file the seven rows above.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — add a CITE +
  CT readiness row pointing at this doc.

**Primary sources cited.** All DLR `www.dlr.de` URLs accessed
2026-05-24:

- CITE project page: `/en/bt/research-transfer/projects/.../cite-center-for-crash-and-impact-test`
- CITE press release (Feb 2023, €25M): `/de/aktuelles/nachrichten/2023/01/25-millionen-euro-dlr-baut-testzentrum-fuer-luftfahrtstrukturen`
- Drop Tower facility page: `/en/bt/.../drop-tower`
- FK sled facility page: `/en/fk/.../dynamic-high-strain-component-test-facility-crash-test-facility`
- Structural Integrity department: `/en/bt/.../structural-integrity`
- CT facility overview: `/de/bt/forschung-transfer/forschungsinfrastruktur/computertomographie-ct` (and `/en/` counterpart)
- DLR-wide CT page (Großforschungsanlagen): `/de/forschung-und-transfer/forschungsinfrastruktur/grossforschungsanlagen/ct-anlage`
- CMC technology page: `/de/bt/.../cmc-technologie-und-strukturbauteile`
- BT institute *Über uns* (locations): `/de/bt/ueber-uns`
- BT Forschungsinfrastruktur index: `/de/bt/forschung-transfer/forschungsinfrastruktur`
- Phoenix Nanotom M datasheet (Waygate / Baker Hughes): `bakerhughes.com/de/waygate-technologies/.../nanotom-m`
- Project CREVAD (historical context on BT crash work): `/en/bt/.../project-crevad`
- CITE video page: `/de/bt/medien/videos/revolutionising-aviation-safety-...`
