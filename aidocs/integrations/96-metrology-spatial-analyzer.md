---
stage: feature-defined
last-stage-change: 2026-05-23
audience: [integrations, plugin-author, mffd, metrology]
---

# 96 — Metrology integration: Spatial Analyzer + Leica trackers as a Shepard payload kind

**Audience.** Plugin authors evaluating the metrology-payload-kind
opportunity; MFFD operators considering whether to push Spatial
Analyzer projects into Shepard alongside the AFP timeseries;
thesis readers (§3 architecture, §6 case study in
`project_thesis_idea.md`) seeing how the as-built verification
stream closes the digital-thread loop.

**Status.** **feature-defined**. The integration target and
payload-kind shape are known; no shepard-plugin-metrology module
exists yet. This doc captures the design seed and the primary-source
evidence justifying the work.

This document complements:

- `aidocs/integrations/52-aas-backend-integration.md` — AAS submodel
  templates as the federation seam (Spatial Analyzer geometry maps
  cleanly into the GeometryAndKinematics submodel family).
- `aidocs/strategy/87-dlr-zlp-positioning.md` §3.2 — the institutional
  positioning of the metrology stack at ZLP.
- `aidocs/semantics/98-shapes-views-and-process-model.md` — Trace3D as
  a shape-driven view; the MFFD IREC case study is the bridge
  use-case where Shepard's metrology stream and its trajectory stream
  meet in a single view.

---

## 1. What the centre's metrology stack actually is

The DLR ZLP metrology platform, per Krebs's *Integrating Metrology,
Spatial Analyzer and Industry 4.0* deck [@krebsMetrologyI42025], is:

| Component | Role | Output shape |
|-----------|------|--------------|
| **Spatial Analyzer Ultimate** (Hexagon / New River Kinematics) | Unified measurement-software environment — the "project" container that ties everything together | Spatial Analyzer project files (`.xit64`-family), tracker measurement traces, CAD-aligned coordinate frames, GD&T reports |
| **2× Leica AT 901 LR** laser trackers | Long-range absolute-position metrology, the institute reference instruments | Binary tracker stream over Leica's protocol; 6-DoF probe pose at high rate; reflector position at higher rate |
| **T-Probe** | Hand-held contact probe with active retroreflector — point-by-point measurement | Discrete 6-DoF poses on operator-triggered captures |
| **T-MAC** | Multi-camera active reflector attached to robot end-effector — continuous 6-DoF tracking of TCP | Continuous 6-DoF stream; the canonical input to dynamic robot calibration |
| **T-SCAN 5** | Hand-held laser line scanner — dense surface point clouds | Point-cloud frames synchronised to Leica tracker's 6-DoF reference |

The day-to-day applications the deck names are *GD&T of finished
parts or assemblies, process-specific measurements (e.g. thermal
expansion of moulds), and surface-geometry reconstruction*. The
advanced application — in-situ robot elastic calibration (IREC) —
is the headline research result and the case study described
in §3 below.

## 2. The integration gap today

None of the artefacts produced by the metrology stack land in
Shepard today. They live on the metrology workstation's local
file system, organised by Spatial Analyzer's internal project
hierarchy, with whatever supplementary documentation the operator
chose to write at the time. The institutional cost shape is
familiar:

- **No provenance link** between a Spatial Analyzer report and the
  manufacturing run it verified. The DataObject for "AFP layup run
  TR-#####" exists in Shepard; the metrology report verifying that
  run exists on a separate workstation; the link between them is
  in the operator's head.
- **No semantic addressability.** A user asking "show me the
  GD&T reports for parts produced on mould M-12" has to know which
  workstation, which operator's project folder, which file naming
  convention.
- **No federation.** Spatial Analyzer projects cannot be exported
  to RO-Crate, cannot land in InvenioRDM, cannot be discovered via
  the Unhide feed. The as-built stream is invisible to the centre's
  outward-facing data substrate.

This is the gap a dedicated payload kind closes.

## 3. The IREC demonstrator — why this matters for MFFD

The advanced-application example in the metrology deck is a
concrete research result that ties the metrology stack to the
AFP cell and to Shepard's existing data substrate. IREC (in-situ
elastic calibration) replaces the AFP compaction roller on a
KUKA KR 120 HA with a calibration apparatus carrying a urethane
disk and a Leica metrology marker; an 18-DOF lumped kinetostatic
model running on a real-time PC (Linux-Xenomai) corrects the
robot TCP via the KUKA RSI interface every 4 ms based on filtered
F/T readings from an ATI 6-axis sensor.

The published results, on the double-curvature MFFD mould with
**280 N lateral / 1100 N normal** load cases simulating real
process forces, are:

- **89.30% reduction in mean TCP deviation** under load
- **70.19% reduction in maximum TCP deviation**
- Trajectory kept inside the **±0.3 mm aerospace tolerance**

The dataset behind those numbers — 31 engagement poses generated
by the COVER heuristic, calibration tool 6-DoF poses, F/T traces,
TCP-correction-delta timeseries, before/after deviation
heatmaps — is exactly the cross-substrate data product Shepard's
M1 wave (TS-IDc + POST /v2/shapes/render + Trace3D, see
`aidocs/semantics/98`) is designed to compose. The Trace3D view
showing TCP deviation colour-mapped along the trajectory before
and after correction is a worked example of what the
shape-driven-view substrate is for.

Without a metrology payload kind, IREC's dataset cannot live
natively in Shepard. With one, IREC becomes the canonical
**multi-stream demonstrator**: AFP trajectory (existing payload
kind), F/T forces (existing), metrology poses (new payload
kind), CAD-aligned deviation maps (new payload kind). The
case-study chapter (`project_thesis_idea.md` §6) gains a
worked example that exercises every architectural seam Shepard
ships.

## 4. Design seed for shepard-plugin-metrology

A first-cut design, written ahead of the SPI seam being formally
mature enough to host it (per `aidocs/platform/47-dev-experience-and-plugin-system.md` plugin-first
discipline):

### 4.1 Payload kinds (two)

- **`MetrologyProject`** — Spatial Analyzer project files,
  versioned by Spatial Analyzer's own native versioning, with
  Shepard providing the outer envelope (provenance, attribution,
  appId, attribute layer). Garage-backed.
- **`MetrologyStream`** — Leica tracker traces and T-MAC continuous
  6-DoF streams. TimescaleDB-backed (the same substrate as the
  existing timeseries payload kind, but with a fixed 6-DoF
  schema and a controller-frame attribute).

Point clouds (T-SCAN 5 output) are out of scope for v1 — they
fold into the existing 3D-payload family designed in
`project_vis_categories.md` (REM voxel + surface meshes), but
the metrology plugin can produce point-cloud DataObjects that the
3D-payload family then renders.

### 4.2 Ontology bindings

- **CHAMEO** for the measurement-method binding (laser-tracker
  measurement of a 6-DoF pose is a clean CHAMEO instance).
- **m4i** for the apparatus / instrument / measurement-process
  identities (instrument carries its calibration certificate as
  an m4i:Tool predicate cluster).
- **AAS GeometryAndKinematics submodel template** for the
  CAD-aligned geometry export — that's the federation seam to
  Industrie-4.0 platforms.

### 4.3 Provenance links

The new payload kinds *Used* (PROV-O sense) the trajectory and
F/T DataObjects produced by the AFP cell; they *Generate* the
deviation-map and corrected-trajectory DataObjects. The Predecessor
chain encodes the experiment order; the typed PROV-O edges encode
the data-flow.

### 4.4 Open questions

- **Spatial Analyzer's native format is closed.** A working
  importer needs either Spatial Analyzer's documented project API
  (Hexagon licenced) or a reverse-engineered reader. The licenced
  path is operationally honest; the reverse-engineered path is
  fragile.
- **Calibration-certificate cardinality.** Each instrument carries
  a calibration certificate (PTB or DAkkS issued) whose validity
  window must be enforced — measurements taken on a tracker whose
  calibration has expired are not aerospace-admissible. This is
  the same NCR-shaped requirement the manufacturing-quality
  agent's findings flagged (`aidocs/agent-findings/manufacturing-quality.md`).
- **Frame transformations.** Spatial Analyzer projects carry a
  CAD-aligned coordinate frame; the KUKA TCP carries the
  robot-base frame; the AFPT tool carries the tool frame. Shepard
  must store these without flattening — Trace3D rendering uses
  one frame, deviation analysis uses another.

## 5. Sequencing

This is a **post-M1-wave** plugin. The M1 wave (TS-IDc +
shapes/render + Trace3D) must ship first because the metrology
plugin's value comes from exercising that substrate. Building
the plugin before the substrate is ready means hand-rolling the
rendering path twice.

The honest sequencing is:

1. M1 wave ships (in flight, see `aidocs/strategy/86` §decision
   2026-05-23 and the M1-VIEWS-AS-SHAPES-WAVE bundle).
2. IREC dataset gets loaded into Shepard manually via the existing
   trajectory and F/T payload kinds, as a stress test of M1.
3. Pain points from step 2 inform the metrology plugin's
   first-cut design (this doc gets revised).
4. `shepard-plugin-metrology` module created; payload kinds
   in §4.1 shipped; first MFFD operator-facing use.

Step 1 is the gate; nothing before that ships. The reason this
doc exists at all is so that the IREC case study can be cited
in §3 architecture (the metrology stream is **structurally
important**, not "optional plugin work") before the plugin
ships.

## 6. Sources

- `77a40948-Integrating_Metrology_Spatial_Analyzer_and_Industry_4.0.pdf`
  — Krebs's metrology-and-I4.0 deck. Uploaded to AI working memory
  2026-05-23. Bib entry: `krebsMetrologyI42025`. Contains the
  equipment inventory, the day-to-day-applications statement, the
  IREC demonstrator description, and the published 89.30% / 70.19%
  reduction numbers.

- `aidocs/strategy/87-dlr-zlp-positioning.md` §3.2 — institutional
  positioning that motivates this integration.

- `aidocs/integrations/52-aas-backend-integration.md` — AAS
  submodel-template family that the federation seam uses.

- `aidocs/semantics/98-shapes-views-and-process-model.md` — the
  Trace3D view recipe IREC exercises end-to-end.

- `project_vis_categories.md` (memory) — 3D-payload family that
  T-SCAN 5 point clouds would fold into in v2 of this plugin.

## 7. Honest companion

This is design-as-of-2026-05-23. None of it is shipped. The plugin
will be redesigned once the M1 wave's real shape is known —
plugins exercise the substrate, and substrate shapes shift in the
final pre-ship pass. The numbers in §3 (89.30% / 70.19% / ±0.3 mm)
are the IREC publication's claims; Shepard's role is to host the
dataset that backs them, not to re-derive them.
