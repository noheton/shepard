# Visualization plugin survey

**Date:** 2026-05-22
**Origin:** User directive — broaden the Trace3D (#142) seed into a
visualization plugin family covering (1) 3D paths/traces,
(2) vector fields / CFD, (3) voxel / volumetric (REM/EM/μCT),
(4) surface meshes. Plus the streaming-substrate question.
**Frames against:** `aidocs/platform/47-dev-experience-and-plugin-system.md §2`
(PayloadKind / PayloadStorage SPI), `aidocs/semantics/98-shapes-views-and-process-model.md §2`
(views as `:ShepardTemplate` with `templateKind = VIEW_RECIPE`),
`aidocs/agent-findings/trace3d-spike.md` (the v1 lean and the renderer-URL
contract).
**Stack assumed:** Nuxt 3 + Vue 3 + Vuetify 3 + TypeScript Composition API
(fixed). Garage S3 backend (per ADR-0024). TimescaleDB for TS,
Neo4j for graph, Postgres for relational, MinIO/GridFS replaced by
Garage for object storage.

---

## TL;DR — recommended plugin family

| # | Category | Lead lib | Plugin name | One-line why |
|---|---|---|---|---|
| 1 | 3D paths / traces | **`@tresjs/core` v5.8.x** (Three.js wrapper) + thin Nuxt composable; **NOT** the archived `@tresjs/nuxt` module | `shepard-plugin-vis-trace3d` | Vue-native scene graph over Three.js; Line2 fat-line shader handles 10⁶ points; renderer-URL swap to Babylon/Isaac stays free |
| 2 | Vector fields / CFD | **VTK.js v35** (BSD-3, Kitware, March 2026) | `shepard-plugin-vis-vector` | The only mature, actively-maintained web sci-vis library with streamlines + LIC + arrow glyphs + WebGPU on the horizon |
| 3 | Voxel / volumetric (REM/EM/μCT) | **VTK.js v35 volume mapper** for ≤ 1 GB stacks; **Neuroglancer** (Google, Apache-2.0) as the renderer-URL escape hatch for TB-scale OME-Zarr/N5 | `shepard-plugin-vis-volume` | One plugin, two render targets behind the same `VIEW_RECIPE` shape; choose by dataset size |
| 4 | Surface meshes (STL/glTF/AAS geometry) | **Three.js** (built-in `GLTFLoader` + `STLLoader`); fold into `shepard-plugin-vis-trace3d` | (merged into #1) | Same Three.js scene as Trace3D — a robot glTF is just another `<TresMesh>` next to the line2 trace |

**Plugin-family count: 3 modules**, not 4, because surface meshes
collapse cleanly into the Trace3D scene. This honours the
ontology-first / plugin-first heuristic — don't spin a new module for
a capability that's already a tuple in an existing one.

**Shared streaming substrate:** OME-Zarr v0.5 over Garage S3 + HSDS
for HDF5 + cloud-optimised GeoTIFF (COG) for raster slices. All three
plugins read frames/chunks through the same `POST /v2/shapes/render`
backend endpoint or directly from Garage via signed URLs (per
`aidocs/agent-findings/garage-activation-runbook.md`). See §6 below.

---

## Conflict with `aidocs/agent-findings/trace3d-spike.md`

The spike (2026-05-22, earlier session) recommended **TresJS via
`@tresjs/nuxt`** for v1. Web verification 2026-05-22:

- `Tresjs/nuxt` repository archived **Feb 1, 2026** ([github.com/Tresjs/nuxt](https://github.com/Tresjs/nuxt))
- `@tresjs/core` itself is **alive** — latest release **5.8.1** published
  May 2026, repo `Tresjs/tres` last updated March 2026
  ([npmjs.com/@tresjs/core](https://www.npmjs.com/package/@tresjs/core))

**Resolution.** The spike's lead pick of TresJS stays correct, but the
integration shape changes: use `@tresjs/core` directly via a thin
Nuxt composable (5–20 LOC `useTres()` in `plugins/shepard-plugin-vis-trace3d/frontend/composables/`),
**not** the archived `@tresjs/nuxt` module. This is mechanical — TresCanvas + TresMesh
components still mount inside a Nuxt page; the archived module
provided only auto-import + SSR helpers we can stand up ourselves.

Risk if `@tresjs/core` itself goes unmaintained: the renderer-URL
swap pattern (`shepard:rendererUrl` slot in the view-shape) means
any successor — vanilla Three.js, react-three-fiber via a Vue
mount-shim, Babylon — replaces only the rendererUrl, not the
view-shape or the backend resolver. The contract decouples the lib
from the platform.

---

## Category 1 — 3D paths / traces (Trace3D)

### 1.1 Candidate matrix

| Library | Footprint (0-5) | Perf @ 10⁶ pts (0-5) | License | Maintenance (0-5) | Vuetify/Vue integration (0-5) | Renderer-swap (0-5) | Notes |
|---|---|---|---|---|---|---|---|
| **`@tresjs/core` v5.8 + Three.js** | 4 (~150 kB gz + ~600 kB Three) | 5 (Line2 fat-line shader; instanced rendering) | MIT | 4 (core alive, Nuxt wrapper archived) | 5 (Vue-native scene graph) | 5 (Three.js → glTF/USD/Isaac all free) | **LEAD** — see §1.2 |
| Three.js raw + custom composable | 3 (~600 kB gz) | 5 | MIT | 5 (Three.js itself rock-solid) | 2 (roll own reactive wrapper) | 5 | Fallback if Tres ergonomic API drifts; more code, same ceiling |
| Babylon.js + `vue-babylonjs` | 2 (~1.2 MB gz) | 5 (game engine class) | Apache-2.0 | 5 (Microsoft) | 3 (Vue wrapper smaller community) | 4 (Babylon has USD ext, weaker Three-ecosystem reuse) | High ceiling, heavier; reasonable plan-B |
| ECharts-gl 3d-line/3d-scatter | 4 (~400 kB gz total — ECharts core is often already loaded) | 3 (no shader-level control; capped at ~10⁵ pts smoothly) | Apache-2.0 | 3 (Apache project, slower release cadence) | 5 (already vue-friendly) | 1 (no clear path to Isaac) | Fastest demo, dead-end for digital-twin; rejected per spike |
| Plotly.js 3D | 2 (~3 MB gz with WebGL) | 2 | MIT | 4 | 4 (vue-plotly community) | 1 | Bulky, low ceiling, no swap |
| deck.gl | 3 (~500 kB gz) | 5 (WebGL2/WebGPU) | MIT | 5 (vis.gl / OpenJS Foundation, Uber) | 2 (React-first; Vue story weak) | 3 | Strong for vector fields (see §2); awkward for primary trace3d in Vue |
| regl + bespoke WebGL | 5 (tiny, ~50 kB) | 5 | MIT | 3 (mature but slow movement) | 2 (no Vue affordance) | 3 | Hand-rolled; not worth the cost for a single product |

### 1.2 Recommended lead pick + why

**`@tresjs/core` 5.8.x with a hand-rolled Nuxt composable.**

- The spike's analysis still holds: Vue-native scene graph
  (`<TresCanvas>` / `<TresMesh>` / `<TresLine>`) is the smallest
  cognitive footprint for the frontend team.
- The archived `@tresjs/nuxt` module added: (a) auto-imports for
  Tres components, (b) SSR-safety wrapping. Both are 5–10 LOC each
  to replicate locally; the maintenance burden is zero next to the
  alternatives.
- Three.js underneath unlocks `GLTFLoader` for robot geometry
  (§4 surface meshes), `Line2` for fat-line shader rendering of
  vertex-coloured paths (the colour-map mechanic), the trail
  extension for animated playback, and a USD loader path to NVIDIA
  Isaac.
- License: MIT throughout. Clean against the CLAUDE.md
  GPL/AGPL/SSPL gate.

### 1.3 Concrete VIEW_RECIPE shape

Reuse the spike's TTL verbatim (`aidocs/agent-findings/trace3d-spike.md §2`).
The shape is:

```turtle
shepard:Trace3DViewShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl   "/v2/assets/views/trace-3d/v1/index.mjs" ;
    shepard:rendererIntegrity "sha384-..." ;
    sh:property [ sh:path shepard:xChannel ; sh:class shepard:TimeseriesChannel ; sh:minCount 1 ] ;
    sh:property [ sh:path shepard:yChannel ; sh:class shepard:TimeseriesChannel ; sh:minCount 1 ] ;
    sh:property [ sh:path shepard:zChannel ; sh:class shepard:TimeseriesChannel ; sh:minCount 1 ] ;
    sh:property [ sh:path shepard:valueChannel ; sh:class shepard:TimeseriesChannel ; sh:maxCount 1 ] ;
    sh:property [ sh:path shepard:colorMap ; sh:in ( "viridis" "plasma" "inferno" "magma" "cividis" "turbo" ) ] ;
    sh:property [ sh:path shepard:traces ; sh:node shepard:Trace3DTraceSpecShape ] ;
    sh:property [ sh:path shepard:meshOverlay ; sh:class shepard:MeshAsset ; sh:maxCount 1 ] .
```

Note: the spike's shape is unchanged except for the new
`shepard:meshOverlay` slot — that's the surface-mesh fold-in (§4).
A robot glTF lives as a `:ShepardFile` of MIME `model/gltf-binary`;
when the slot is set the renderer drops the mesh into the scene at
the world origin (or aligned to a transform encoded as additional
channels — out of scope v1).

Input: aligned frames + (optional) mesh URL. Output: rotating
viewport with colour-mapped polyline + (optional) static or
animated mesh + orbit controls + time-scrubber sibling widget.

### 1.4 `shepard-plugin-vis-trace3d` sketch

- **Manifest** (`plugins/vis-trace3d/manifest.yml`):
  - `id: vis-trace3d`
  - `payloadKinds: []` (consumes existing TS + File payloads)
  - `viewRecipes: [Trace3DViewShape, Trace3DTraceSpecShape]`
  - `sidecars: []` (no infra deps — runs entirely in the browser; backend renderer-resolver is in-tree under `de.dlr.shepard.v2.shapes`)
  - `dependsOn: []`
- **Backend (Java, in `shepard-plugin-vis-trace3d/src/main/java`):**
  - `TraceFrameResolver` — reads N TS channels via existing
    `de.dlr.shepard.v2.timeseries.*` services, time-aligns
    (linear-resample / nearest / snap-to-x), interpolates,
    returns `FrameEnvelope`.
  - `TraceShapeBindingValidator` — registers the
    `Trace3DViewShape` SHACL with the in-tree
    `JenaShaclValidator` (extends the allow-list).
  - Wires into `POST /v2/shapes/render` (in-tree endpoint) via the
    `ViewRecipeRenderer` SPI extension. No new endpoint.
- **Frontend (`plugins/vis-trace3d/frontend/`):**
  - `composables/useTres.ts` — 15 LOC SSR-safe `<TresCanvas>`
    mounter that the archived `@tresjs/nuxt` module used to
    provide.
  - `components/Trace3DView.vue` — reads `FrameEnvelope`, builds
    Line2 geometry with vertex colours, attaches OrbitControls,
    exposes `:viewTime` prop for scrubber sync.
  - `components/TimeScrubber.vue` — sibling widget driving a
    shared `viewTime` ref (so multiple views in one workspace
    scrub together).
- **Sidecar:** none for v1. v1.5 Isaac Lab adds a WebRTC sidecar
  declared per `feedback_plugins_declare_sidecars.md`.
- **Docs (per CLAUDE.md plugins-ship-own-docs rule):**
  `plugins/vis-trace3d/docs/{reference,quickstart,install}.md`.

---

## Category 2 — Vector fields / CFD

### 2.1 Candidate matrix

| Library | Footprint | Perf @ 10⁵ cells | License | Maint | Vue integration | Renderer-swap | Notes |
|---|---|---|---|---|---|---|---|
| **VTK.js v35** | 3 (~1 MB gz; tree-shake helps) | 5 (WebGL2; WebGPU coming) | BSD-3-Clause | 5 (Kitware) | 3 (no Vue wrapper — but it's plain JS, drops into a `<canvas ref>`) | 4 (the same data piped to Server-Side ParaView is one URL swap) | **LEAD** — has `vtkStreamTracer`, `vtkGlyph3DMapper`, `vtkLineIntegralConvolution2D` all built-in |
| deck.gl | 3 | 4 (WebGL2/WebGPU) | MIT | 5 (vis.gl / Uber) | 2 (React-first) | 3 | `LineLayer` + `IconLayer` for glyphs; no native LIC; wind-vis example uses GPU compute |
| ParaView Glance | 2 (~3 MB gz; full viewer app, not lib) | 5 | BSD-3 | 4 | 1 (iframe-only practical embedding) | 5 (Glance IS the in-browser ParaView) | Heavyweight; not a library — works as a `rendererUrl: glance-stream` escape hatch |
| Plotly `cone` / `streamtube` | 2 (3 MB gz) | 2 (capped at ~10⁴ cells smoothly) | MIT | 4 | 4 | 1 | Quick demo only; no LIC; abandons at first real CFD dataset |
| Custom LIC implementations (CG&A / shader-only) | 5 (~20 kB) | 4 | varies (mostly MIT in published refs) | 2 (one-off academic code; bit-rot risk) | 2 | 2 | Cite as a reference for the VTK.js LIC mapper; not a candidate on its own |

### 2.2 Recommended lead pick + why

**VTK.js v35** ([kitware.github.io/vtk-js](https://kitware.github.io/vtk-js/index.html), released March 2026).

- It is the only browser-side library with a maintained
  `vtkStreamTracer` (3D streamlines), `vtkGlyph3DMapper` (arrow
  glyphs), and a 2D LIC pipeline. The python/C++ heritage means
  examples and shader specs are 20+ years deep.
- BSD-3-Clause — clean against the security gate.
- Kitware backs it as their flagship web sci-vis effort; v35 in
  March 2026 is a stable, current release; volume rendering and
  vector fields share one library, which simplifies the
  plugin-family deps.
- The "renderer-swap" path is **VTK.js → ParaView Server / Glance**:
  the same `.vti`/`.vtu` dataset (or a `vtk.js sceneGraph` JSON
  export) can be opened in a stand-alone ParaView, which is the
  domain-expert escape hatch for serious CFD work.

The Vuetify/Vue integration cost is real but bounded: VTK.js
mounts on a `<canvas>` via `vtkFullScreenRenderWindow.newInstance()`.
Wrap it in a `<VtkScene>` Vue component (≈ 80–120 LOC) per the
existing pattern in `kitware/vue-vtk-js` ([github.com/Kitware/vue-vtk-js](https://github.com/Kitware/vue-vtk-js)) — that wrapper itself is BSD-3 and a reference.

### 2.3 Concrete VIEW_RECIPE shape

```turtle
shepard:VectorFieldViewShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl   "/v2/assets/views/vector-field/v1/index.mjs" ;
    shepard:rendererIntegrity "sha384-..." ;
    sh:property [
        sh:path shepard:vectorFieldSource ;
        sh:class shepard:StructuredDataPayload ;  # cells × (u,v,w) × time
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:description "Structured data with u/v/w components per cell"
    ] ;
    sh:property [
        sh:path shepard:vectorComponentMap ;
        sh:datatype xsd:string ;
        sh:description "JSON map { 'u': 'col_u', 'v': 'col_v', 'w': 'col_w' }"
    ] ;
    sh:property [
        sh:path shepard:gridSource ;
        sh:class shepard:StructuredDataPayload ;
        sh:description "Cell-centre coordinates (x,y,z); optional if vectorFieldSource includes them"
    ] ;
    sh:property [
        sh:path shepard:renderMode ;
        sh:in ( "streamlines" "lic" "arrows" "particle-trace" ) ;
        sh:defaultValue "streamlines"
    ] ;
    sh:property [
        sh:path shepard:seedingDensity ;
        sh:datatype xsd:double ;
        sh:defaultValue 0.1
    ] ;
    sh:property [
        sh:path shepard:colorMap ;
        sh:in ( "viridis" "plasma" "inferno" "magma" "cividis" "turbo" "RdBu" ) ;
        sh:defaultValue "viridis"
    ] ;
    sh:property [
        sh:path shepard:timeRange ; sh:datatype shepard:TimeRange ; sh:maxCount 1 ] .
```

The big concept change vs Trace3D: the **input is a
StructuredDataPayload**, not a tuple of TS channels. CFD data is
spatial — cells × components × (optional time-step). The resolver
in `shepard-plugin-vis-vector` reads the structured payload, maps
the column names to (u,v,w), and emits a `.vti` (image data) or
`.vtu` (unstructured grid) blob that the frontend feeds to VTK.js.

### 2.4 `shepard-plugin-vis-vector` sketch

- **Manifest:** `id: vis-vector`, `dependsOn: ["vis-trace3d"]`
  (shares the renderer-resolver pattern), `sidecars: []`
- **Backend:**
  - `VectorFieldResolver` — reads structured payload, maps columns,
    converts to VTK XML (`.vti` for regular grids, `.vtu` for
    unstructured) and stores as a transient blob on Garage. Returns
    a signed URL.
  - Allow-list extension for `VectorFieldViewShape` in
    `TemplateBodyValidator`.
- **Frontend:**
  - `components/VectorFieldView.vue` — wraps VTK.js
    `vtkStreamTracer` / `vtkGlyph3DMapper` / `vtkLineIntegralConvolution2D`
    based on `renderMode`. Reads the signed URL, hands it to VTK's
    reader.
  - `components/VtkScene.vue` — generic Vue ↔ VTK.js mount helper,
    shared with `vis-volume`.
- **Sidecar:** none required. If the user uploads a CFD dataset too
  big to convert in-process (> ~500 MB), declare a `cfd-converter`
  sidecar (Python + pyvista) per `feedback_plugins_declare_sidecars.md`.
  v1.0 keeps everything in-process.
- **Docs:** `plugins/vis-vector/docs/{reference,quickstart,install}.md`.

### 2.5 Acceptance test

Wind-tunnel CFD from a DLR aero campaign — fuselage flow with ~10⁵
cells × 50 time-steps. "Did it work" criterion: streamlines visibly
trace separation/recirculation regions; the scrubber slides time
smoothly at > 24 FPS.

Backup (if no DLR aero data lands in time): NASA TetGen sample
datasets (cube-flow, sphere-flow) — public, suitable.

---

## Category 3 — Voxel / volumetric (REM/EM/μCT)

REM here is German *Rasterelektronenmikroskopie* — Scanning EM.
Volumetric stacks from EM tomography, μCT, MRI, FIB-SEM. Stack
sizes range from ~50 MB (single μCT slice stack) to multi-TB
(connectomics EM).

### 3.1 Candidate matrix

| Library | Footprint | Perf @ 10⁸ voxels | License | Maint | Vue integration | Renderer-swap | Notes |
|---|---|---|---|---|---|---|---|
| **VTK.js v35** `vtkVolumeMapper` | 3 (shared with §2) | 4 (single-volume on GPU; multi-volume in v35) | BSD-3 | 5 | 3 | 4 | **LEAD for ≤ 1 GB stacks**; volume mapper now supports multiple input volumes (background + segmentation) |
| **Neuroglancer** (Google) | 1 (~5 MB gz; whole app) | 5 (designed for petabyte EM) | Apache-2.0 | 4 (Google maintains; cadence slow but steady) | 1 (iframe-only practical) | 5 (`rendererUrl` swap point) | **LEAD for ≥ 10 GB or OME-Zarr/N5 stacks**. Embed via iframe with URL-encoded scene state |
| itk-vtk-viewer | 2 (~3 MB gz; whole app, not lib) | 4 | BSD-3 | 4 (Kitware) | 1 (iframe) | 4 | Built on VTK.js + itk-wasm; great default viewer if rolling our own VTK.js wrapper is too much; ships as a viewer app, not as a library to mount |
| niivue | 4 (~600 kB gz) | 4 | BSD-2 | 5 (active 2026; UoNottingham/UCL) | 3 (TS, no Vue wrapper but lightweight to mount) | 3 | NIfTI / DICOM neuroimaging focus but works for any 3D volume; smaller alternative if VTK.js feels heavy |
| OHIF Viewer | 1 (~10 MB gz; full app) | 4 | MIT | 5 (OHIF Foundation) | 1 (iframe) | 3 | DICOM-medical heavyweight; overkill for REM/EM |
| 3D Slicer web export | 1 (very heavy) | 5 | BSD-3 | 4 | 1 | 4 | One-shot exporter, not interactive |
| xtk | — | — | MIT | 1 (no commits since 2020) | 2 | 1 | Legacy; superseded |
| neuroglancer-cli / Trame | depends | 5 | varies | 4 | 2 | 5 | Server-side rendering pipeline; consider for the very-large-stack path |

### 3.2 Recommended lead pick(s) + why

**Two render targets, one plugin.** Same VIEW_RECIPE shape,
two `rendererUrl` choices switched by an explicit
`shepard:renderTarget` field (`vtkjs-inline` vs `neuroglancer-iframe`):

- **VTK.js v35** for the everyday case — μCT, FIB-SEM, single-volume
  TIFF/DICOM stacks that fit in GPU memory after a brief
  pre-processing pass (down-sampled multi-resolution pyramid built
  server-side). Best Vuetify integration story; same lib already
  in `vis-vector`.
- **Neuroglancer** for the rare-but-critical case where the dataset
  is genuinely TB-scale (e.g. a full FIB-SEM connectomics scan)
  and the substrate is OME-Zarr or N5. Neuroglancer is the proven
  petabyte-scale viewer; the integration is iframe-with-shared-URL,
  not library-mount. The `rendererUrl` slot in the VIEW_RECIPE
  carries a Neuroglancer state URL; the frontend mounts an iframe.

### 3.3 Concrete VIEW_RECIPE shape

```turtle
shepard:VolumeViewShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl   "/v2/assets/views/volume/v1/index.mjs" ;
    shepard:rendererIntegrity "sha384-..." ;
    sh:property [
        sh:path shepard:volumeSource ;
        sh:class shepard:FilePayload ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:description "OME-Zarr root URL, HDF5/HSDS URL, DICOM-series root, or TIFF stack manifest"
    ] ;
    sh:property [
        sh:path shepard:volumeFormat ;
        sh:in ( "ome-zarr" "hdf5-hsds" "dicom-series" "tiff-stack" "n5" ) ;
        sh:minCount 1
    ] ;
    sh:property [
        sh:path shepard:renderTarget ;
        sh:in ( "vtkjs-inline" "neuroglancer-iframe" ) ;
        sh:defaultValue "vtkjs-inline" ;
        sh:description "Choose by stack size — see plugin docs"
    ] ;
    sh:property [
        sh:path shepard:transferFunction ;
        sh:datatype xsd:string ;
        sh:description "JSON: piecewise-linear opacity + RGB map"
    ] ;
    sh:property [
        sh:path shepard:clippingPlanes ;
        sh:datatype xsd:string ;
        sh:description "JSON array of clipping plane definitions"
    ] ;
    sh:property [
        sh:path shepard:isoSurfaces ;
        sh:datatype xsd:string ;
        sh:description "JSON array of isovalue + colour + opacity tuples"
    ] ;
    sh:property [
        sh:path shepard:segmentationOverlay ;
        sh:class shepard:FilePayload ;
        sh:maxCount 1 ;
        sh:description "Optional label volume aligned to volumeSource (multi-volume rendering)"
    ] .
```

### 3.4 `shepard-plugin-vis-volume` sketch

- **Manifest:** `id: vis-volume`, `dependsOn: ["vis-vector"]` (shares
  the `VtkScene` mount helper), `payloadKinds: []` (consumes file
  payloads — but optionally declares an OME-Zarr-aware sub-payload-kind
  if Shepard adopts OME-Zarr as a first-class chunked-volume payload),
  `sidecars: ["volume-pyramidiser"]`.
- **Backend:**
  - `VolumePyramidiser` (CDI bean) — on upload of a TIFF stack or
    DICOM series, produces a multi-resolution OME-Zarr pyramid on
    Garage. This is the expensive operation that gives the
    frontend a fast read path. Declared as a sidecar in the
    manifest so operators see it in the compose tree.
  - `VolumeResolver` — given the VIEW_RECIPE instance, returns a
    signed Garage URL (for OME-Zarr/N5) or a transient `.vti`
    blob (for inline VTK.js render of small stacks).
  - `NeuroglancerStateBuilder` — for the `neuroglancer-iframe`
    render target, constructs the Neuroglancer state JSON
    (datasource: zarr) and base64-encodes into an iframe URL.
- **Frontend:**
  - `components/VolumeView.vue` — branches on `renderTarget`:
    `<VtkScene>` for inline, `<NeuroglancerIframe>` for iframe.
  - `components/TransferFunctionEditor.vue` — piecewise-linear
    opacity / colour map editor (drag points). Vuetify dialog.
- **Sidecar — `volume-pyramidiser`:**
  - Python container (FastAPI) with `imagecodecs`, `tifffile`,
    `ome-zarr-py`, `pydicom`. Reads upload, writes Zarr pyramid to
    Garage, posts completion event back to backend.
  - Declared per `feedback_plugins_declare_sidecars.md`:
    `sidecars: [{ name: volume-pyramidiser, image: ghcr.io/shepard/vis-volume-pyramidiser:1.0, env: [GARAGE_S3_ENDPOINT, GARAGE_BUCKET], readiness: GET /health }]`.
- **Docs:** `plugins/vis-volume/docs/{reference,quickstart,install}.md`.

### 3.5 Acceptance test

DLR MFFD μCT of a CF/LMPAEK welded joint (porosity inspection):
~1024³ × float32 ≈ 4 GB raw. Acceptance: full-volume volume render
in `vtkjs-inline` after the pyramidiser produces the OME-Zarr
pyramid (allowed cold-cache time < 5 min); user can drag a
clipping plane to inspect a weld cross-section at interactive
rate (> 24 FPS).

Backup: open μCT datasets from the [OpenSciVis collection](https://github.com/InsightSoftwareConsortium/OMEZarrOpenSciVisDatasets) (kingsnake, tooth, foot — all public, all
OME-Zarr).

---

## Category 4 — Surface meshes

### 4.1 Decision: fold into `shepard-plugin-vis-trace3d`

A surface mesh (STL, glTF, OBJ, AAS-submodel geometry) renders in
exactly the same Three.js scene as a Trace3D polyline. Splitting
into a separate plugin would force two TresCanvas mounts per page
and duplicate the orbit-controls / time-scrubber wiring.

The Trace3D VIEW_RECIPE shape already includes a
`shepard:meshOverlay` slot (§1.3). A **mesh-only** view is simply a
Trace3D instance with `xChannel/yChannel/zChannel` unset and
`meshOverlay` set — no library change, no new SHACL shape, no new
plugin.

For a dedicated mesh-viewer experience (no trace at all) we add a
**second VIEW_RECIPE within the same plugin**:

```turtle
shepard:MeshViewShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl   "/v2/assets/views/mesh/v1/index.mjs" ;
    sh:property [
        sh:path shepard:meshSource ;
        sh:class shepard:FilePayload ;
        sh:minCount 1 ; sh:maxCount 5 ;
        sh:description "STL / glTF / OBJ; multiple = composed scene"
    ] ;
    sh:property [
        sh:path shepard:meshFormat ;
        sh:in ( "gltf" "glb" "stl" "obj" "step-aas" ) ;
        sh:minCount 1
    ] ;
    sh:property [
        sh:path shepard:colorOverride ; sh:datatype xsd:string ; sh:maxCount 1 ] ;
    sh:property [
        sh:path shepard:wireframe ; sh:datatype xsd:boolean ; sh:defaultValue false ] ;
    sh:property [
        sh:path shepard:explosionFactor ;
        sh:datatype xsd:double ;
        sh:description "0.0 = assembled, > 0 = exploded view (AAS submodel-trees)"
    ] .
```

Loaders: Three.js's built-in `GLTFLoader` and `STLLoader` (both MIT,
shipped with three.js since 2017). AAS-submodel geometry maps to
glTF via a future step in the AAS plugin (`shepard-plugin-aas`,
not in scope here) — the mesh-viewer just consumes the resulting
glTF blob.

### 4.2 Acceptance test

DLR MFFD AFP robot scene — load the robot manufacturer's glTF
(KUKA / KR Coater Robot CAD export) and a tape-laying tool glTF.
Acceptance: assembled view, smooth orbit, > 30 FPS at 1080p; flip
to `explosionFactor = 0.3` for inspection of tool-mount geometry.

---

## 5. Cross-cutting: huge-data substrate (OME-Zarr / HSDS / COG)

The four vis categories all hit the same wall once data exceeds
a few hundred MB: monolithic file formats (single-blob TIFF,
HDF5, STL) don't stream over the web. The answer is
**chunked, cloud-native formats** read directly by the renderer.

### 5.1 The three formats Shepard should standardise on

| Format | Best for | Renderer | License | Reader libs (browser) |
|---|---|---|---|---|
| **OME-Zarr v0.5** ([ngff.openmicroscopy.org](https://ngff.openmicroscopy.org/latest/)) | Volumetric (REM/EM/μCT/MRI), multi-resolution pyramids; the chunked standard for bioimaging in 2026 | VTK.js, Neuroglancer, itk-vtk-viewer | BSD-3 (spec) | `zarrita.js`, `@zarr.js/core` (MIT) |
| **HDF5 + HSDS** ([hdfgroup.org](https://www.hdfgroup.org)) | High-rate timeseries, scientific data cubes; matches Shepard's planned A5 series | h5wasm, h5lib-js | BSD-3 (HDF5), BSD-3 (HSDS) | `h5wasm` (MIT) |
| **COG (Cloud-Optimised GeoTIFF)** | 2D raster (NDT scans, X-ray inspection imagery, satellite); slicing-on-demand | leaflet-cog, OpenLayers | MIT (typical) | `geotiff.js` (MIT) |

### 5.2 Garage S3 implications

Per `feedback_storage_s3_garage.md`: when anyone asks about S3 the
answer is **Garage**, not MinIO. The substrate alignment:

- **OME-Zarr pyramids written by the volume-pyramidiser sidecar
  → Garage bucket** with a path structure
  `volumes/{collectionAppId}/{dataObjectAppId}/{filePayloadAppId}.zarr/`.
- **HDF5 stays as opaque blobs in Garage**, with HSDS as the
  protocol layer if a plugin needs slice-level reads (e.g.
  high-rate vibration TS at MHz). HSDS itself is a sidecar
  declared by the (future) `shepard-plugin-hdf5` per the A5
  series.
- **Signed-URL pattern:** the backend resolver mints a short-lived
  Garage presigned URL (or path-based signed URL via Garage's
  built-in S3-compatible signer) and the frontend reads chunks
  directly — backend stays out of the data path for large
  payloads.

### 5.3 Why this shared backplane matters

It collapses three "would-have-been-separate" plugin concerns into
one architectural fact: every vis plugin reads chunked data through
the same backplane. The volume-pyramidiser sidecar is the only
piece of per-plugin infrastructure; the read path is uniform.

The competing RDM platforms (Kadi4Mat, openBIS, NOMAD, SciCat)
typically do **not** have this backplane — they treat large
volumetric data as opaque blobs and rely on external viewers.
This is Shepard's structural advantage if we ship the
pyramidiser-and-presigned-URL story coherently.

---

## 6. Plugin-family architecture

```
                              ┌─────────────────────────────────────────┐
                              │  POST /v2/shapes/render (in-tree)       │
                              │  - TemplateBodyValidator allow-list ext │
                              │  - Per-recipe Resolver (SPI)            │
                              └────────────┬────────────────────────────┘
                                           │
              ┌────────────────────────────┼────────────────────────────┐
              │                            │                            │
   ┌──────────▼──────────┐    ┌───────────▼──────────┐    ┌─────────────▼────────┐
   │ shepard-plugin-     │    │ shepard-plugin-      │    │ shepard-plugin-      │
   │   vis-trace3d       │    │   vis-vector         │    │   vis-volume         │
   │                     │    │                      │    │                      │
   │ VIEW_RECIPEs:       │    │ VIEW_RECIPEs:        │    │ VIEW_RECIPEs:        │
   │  - Trace3DViewShape │    │  - VectorFieldView…  │    │  - VolumeViewShape   │
   │  - MeshViewShape    │    │                      │    │                      │
   │                     │    │ Resolver:            │    │ Resolver:            │
   │ Resolver:           │    │   structured →       │    │   file → OME-Zarr    │
   │   TS channels →     │    │   .vti/.vtu          │    │   pyramid OR         │
   │   FrameEnvelope     │    │                      │    │   Neuroglancer state │
   │                     │    │ Frontend: VtkScene + │    │                      │
   │ Frontend:           │    │   VectorFieldView    │    │ Frontend: VolumeView │
   │   useTres + Line2   │    │                      │    │   (VtkScene OR      │
   │   GLTFLoader +      │    │ Sidecar: none v1     │    │    NeuroglancerIfrm) │
   │   StlLoader         │    │                      │    │                      │
   │                     │    │ depends: trace3d     │    │ Sidecar:             │
   │ Sidecar: none v1    │    │   (VtkScene helper)  │    │   volume-pyramidiser │
   │ (v1.5: Isaac WebRTC)│    │                      │    │                      │
   │                     │    │                      │    │ depends: vector      │
   │ Library: @tresjs/   │    │ Library: VTK.js v35  │    │   (VtkScene helper)  │
   │   core 5.8 + three  │    │                      │    │                      │
   └─────────────────────┘    └──────────────────────┘    │ Library: VTK.js v35  │
                                                          │   + neuroglancer     │
                                                          │   (iframe)           │
                                                          └──────────────────────┘
                                           │
                              ┌────────────▼────────────┐
                              │  Garage S3 + signed URL │
                              │  - OME-Zarr pyramids    │
                              │  - HDF5 blobs (HSDS)    │
                              │  - COG rasters          │
                              │  - glTF/STL meshes      │
                              │  - .vti/.vtu blobs      │
                              └─────────────────────────┘
```

### 6.1 Cross-plugin dependency rules

- `vis-trace3d` has **no plugin deps** — it's the foundation
  (Three.js + the basic `useTres` composable).
- `vis-vector` **depends on vis-trace3d** for: nothing today.
  Initially independent. The shared `VtkScene` Vue component
  could live in `vis-vector` (since vector is its primary
  consumer) and `vis-volume` depends on vis-vector for it.
- `vis-volume` **depends on vis-vector** for `VtkScene`. Some
  hassle but normal — both use VTK.js.
- All three plugins **share the renderer-URL contract** at the
  view-shape layer. Replacing a plugin means changing one URL
  string per saved view-shape instance, nothing else.

### 6.2 What stays in-tree (not plugins)

Per `aidocs/platform/47-dev-experience-and-plugin-system.md §2.4`:

- The `POST /v2/shapes/render` endpoint and its
  `ViewRecipeRenderer` SPI dispatcher.
- The SHACL validator + meta-shape allow-list extension hooks.
- The Garage S3 signed-URL minter.
- The presigned-URL access-control filter.

These are platform primitives; every vis plugin compiles
against them.

---

## 7. Risks + open questions

### 7.1 TresJS sustainability

`@tresjs/nuxt` archived Feb 2026 raises the question of whether
`@tresjs/core` follows. Mitigation:

- The view-shape's `shepard:rendererUrl` slot lets us swap to
  raw Three.js, react-three-fiber via a mount shim, or Babylon
  by changing the rendererUrl. No view-shape data migration
  required. **The contract decouples the lib from the platform.**
- Treat `@tresjs/core` as a "preferred default" rather than a
  hard requirement in the plugin manifest. If the project goes
  dormant, the migration is a frontend-only change in the
  `vis-trace3d` plugin's `Trace3DView.vue`.

### 7.2 VTK.js bundle size

VTK.js minified-gzipped is ~1 MB even after tree-shaking. For a
data platform this is acceptable (it loads only on pages that
mount a volume / vector view) but worth dynamic-importing inside
the plugin's frontend entry. Don't bundle it into the main app
shell.

### 7.3 Neuroglancer iframe sandbox

Neuroglancer is a full SPA. Iframe-embedding means:
- Cross-origin issues with Garage signed URLs (need CORS on the
  bucket — Garage supports it natively).
- The iframe lives at `cdn.shepard.dlr.de/neuroglancer/v1/` or
  bundled into the plugin's static assets (~5 MB).
- No bi-directional Vue ↔ Neuroglancer interaction beyond URL
  encoded state. Acceptable for v1 (the renderer is a "viewer",
  not a programmable widget).

### 7.4 Server-side rendering (SSR) safety

Three.js + WebGL touches `window`/`document` — must be
dynamically-imported inside `onMounted` to avoid Nuxt SSR
crashes. The 5-LOC `useTres()` composable handles this. Same
for VTK.js.

### 7.5 Test-data licensing

For CI tests of the vis plugins, prefer public datasets — the
[OpenSciVis OME-Zarr collection](https://github.com/InsightSoftwareConsortium/OMEZarrOpenSciVisDatasets) (kingsnake / tooth /
foot, all public domain) is ideal. Avoid embedding MFFD industrial
IP into test fixtures.

### 7.6 Multi-volume rendering

VTK.js v35's volume mapper supports multiple input volumes (per the
[v35 release notes](https://www.kitware.com/vtk-js-v35-release/)) —
this is the segmentation-overlay path. Worth highlighting in the
quickstart docs because medical/microscopy users expect it.

### 7.7 WebGPU horizon

Both VTK.js and deck.gl are migrating to WebGPU through 2026–2027.
This is not a v1 concern but the plugin manifest should not
pin against a specific WebGL version. The frontend entry should
feature-detect WebGPU and fall back to WebGL2 if unavailable.

### 7.8 AAS submodel geometry

`shepard:meshFormat "step-aas"` is a placeholder — STEP doesn't
render directly in any of these libraries. A separate AAS plugin
would need to convert STEP → glTF before handing to the mesh
viewer. Out of scope for the vis family; flagged for the
(future) `shepard-plugin-aas` design.

### 7.9 Provenance capture

Per `aidocs/agent-findings/easa-ai-regulatory-positioning.md` and
the f(ai)²r integration, the act of opening / interacting with a
view recipe should land a typed `:Activity` (`shepard:View`,
`shepard:Inspect`). The vis plugins already inherit this via
`ProvenanceCaptureFilter` (PROV1a) when they hit
`POST /v2/shapes/render`. Worth documenting in each plugin's
`reference.md` so users see the audit trail.

---

## 8. Real-world acceptance tests

| Category | Dataset | Acceptance criterion |
|---|---|---|
| 1. Trace3D | MFFD AFP TCP thermal-trail (dataset ETA 2026-05-26) | ~30 Hz × 10 min = 18k points; smooth orbit at 60 FPS; time scrubber lights up the colour-mapped polyline in real time; glTF robot mesh overlay |
| 1. Mesh viewer | KUKA AFP robot glTF + AFP tape head | Orbit at > 30 FPS at 1080p; explosion factor reveals tool-mount geometry |
| 2. Vector field | DLR aero wind-tunnel CFD (or NASA TetGen sphere-flow if no DLR data) | Streamlines trace separation/recirculation; scrubber slides time-steps at > 24 FPS |
| 3. Volume — small | OpenSciVis "tooth" μCT (~120 MB) | < 30 s upload-to-pyramid-to-render; clipping plane drag at > 24 FPS |
| 3. Volume — large | MFFD welded joint μCT (~4 GB) | OME-Zarr pyramid built in < 5 min cold cache; full-volume render with porosity-band transfer function |
| 3. Volume — huge | [OpenOrganelle](https://openorganelle.janelia.org) FIB-SEM (TB) | Neuroglancer iframe renders a 1024³ ROI in < 5 s after first chunk load |

---

## 9. Competing-platform comparison

| Platform | 3D paths | Vector fields | Volumetric | Mesh | Notes |
|---|---|---|---|---|---|
| **Shepard (proposed)** | ✓ trace3d | ✓ vis-vector | ✓ vis-volume | ✓ (folded into trace3d) | View-recipe SPI; renderer-URL swap |
| **Kadi4Mat** ([Helmholtz](https://helmholtz.software/software/kadi4mat)) | ✗ | ✗ | partial (external viewer hand-off) | partial (STL viewer plugin) | Plugin system (pluggy); no in-tree sci-vis |
| **openBIS** | ✗ | ✗ | external (e.g. OMERO bridge) | external | Wet-lab-data focus; no built-in 3D |
| **NOMAD** | partial (atom-positions via NOMAD Encyclopedia 3D structure viewer) | ✗ | ✗ | ✓ (atomistic structures) | Materials-science-specific viz |
| **SciCat** | ✗ | ✗ | external (Jupyter handoff) | ✗ | Metadata catalogue; analysis is downstream |
| **OMERO** (bioimaging) | ✗ | ✗ | ✓ (OMERO.iviewer, OMERO.web) | ✗ | THE bioimaging RDM; tightly coupled to Bio-Formats |

**Honest position:** Shepard's planned vis family is unusually
broad for a research RDM platform. Most platforms ship either a
narrow domain viewer (atom positions for NOMAD, 2D bioimaging for
OMERO) or no in-tree viz at all. The view-recipe SPI is the
structural differentiator: same shape contract across
categories, plugin authors only need to register a resolver and
a renderer URL.

---

## 10. Backlog rows to add

Proposed for `aidocs/16-dispatcher-backlog.md` under a new
`### VIS — 2026-05-22 (visualization plugin family)` section, all
`queued`:

| ID | Slice | Size | Notes |
|---|---|---|---|
| VIS-T1 | `shepard-plugin-vis-trace3d` module skeleton + `Trace3DViewShape` + `MeshViewShape` SHACL allow-list extension + `TraceFrameResolver` (TS channels → aligned frames) + `Trace3DView.vue` (TresJS `@tresjs/core` 5.8, Line2 fat-line shader) + `MeshView.vue` (Three.js `GLTFLoader`/`STLLoader`) + `TimeScrubber.vue` + plugin docs trio | L (~2 sprints) | Builds on `aidocs/agent-findings/trace3d-spike.md`. **Important:** use `@tresjs/core` directly via in-plugin `useTres()` composable; `@tresjs/nuxt` archived Feb 2026. Acceptance: MFFD AFP TCP thermal-trail demo lands. |
| VIS-V1 | `shepard-plugin-vis-vector` module skeleton + `VectorFieldViewShape` SHACL + `VectorFieldResolver` (structured → `.vti`/`.vtu`) + `VtkScene.vue` (shared Vue ↔ VTK.js mount, reused by vis-volume) + `VectorFieldView.vue` (vtkStreamTracer / vtkGlyph3DMapper / vtkLineIntegralConvolution2D) + plugin docs trio | M | VTK.js v35 (BSD-3). No sidecar v1. Acceptance: NASA TetGen sphere-flow streamlines or DLR aero CFD. |
| VIS-X1 | `shepard-plugin-vis-volume` module skeleton + `VolumeViewShape` SHACL + `VolumeResolver` (file → OME-Zarr URL OR Neuroglancer state) + `VolumeView.vue` (VTK.js vtkVolumeMapper inline OR Neuroglancer iframe per `renderTarget`) + `TransferFunctionEditor.vue` + `volume-pyramidiser` sidecar (Python FastAPI + `ome-zarr-py` + `tifffile` + `pydicom`) declared in manifest per PM1f + plugin docs trio | L (~3 sprints) | Depends on VIS-V1 for `VtkScene` reuse. Acceptance: MFFD μCT welded-joint inspection (~4 GB) renders with clipping plane drag at > 24 FPS. |
| VIS-S1 | Shared cross-cutting: `POST /v2/shapes/render` `ViewRecipeRenderer` SPI dispatcher (in-tree, plugin authors register a `Resolver` keyed by templateName) + Garage signed-URL minter helper (`SignedUrlIssuer`) + presigned-URL access-control filter | M | In-tree; not a plugin. Predecessor for all VIS-* plugins. Already partially covered by the Trace3D spike. |
| VIS-S2 | Garage S3 + OME-Zarr storage-policy doc — bucket layout `volumes/{collectionAppId}/{dataObjectAppId}/...`, CORS config, retention, signed-URL TTL defaults. New `aidocs/ops/` doc + Garage admin runbook update. | S | Operator-facing only; no code. |
| VIS-S3 | (v1.5) Isaac Lab WebRTC renderer URL swap — sidecar declaring NVIDIA Isaac Lab + WebRTC bridge; same Trace3D view-shape, different `rendererUrl`. **Stretch goal**, not v1. | XL | Cite as the "renderer-swap escape hatch already implemented" once shipped. |

---

## 11. Honest assessment

- **Trace3D (Category 1) is shippable in the next sprint** with
  the spike already done. The lib choice debate is settled
  (`@tresjs/core` 5.8 via own composable, not the archived Nuxt
  module). Real risk: TresJS-core itself stalls — mitigated by
  the renderer-URL escape hatch.
- **Vector fields (Category 2) is shippable next** with VTK.js.
  Some Vue-integration ceremony but bounded; the
  [Kitware/vue-vtk-js](https://github.com/Kitware/vue-vtk-js)
  reference saves us from rolling our own from zero.
- **Volume (Category 3) is the most ambitious** because the
  pyramidiser sidecar is real infrastructure. But it's also the
  category with the **largest competitive moat** — almost no
  open-source RDM platform ships an in-the-box volume viewer.
  This is where Shepard wins JEC Award and Helmholtz audiences.
- **Surface meshes (Category 4) is "free" once Trace3D ships.**
  Don't spin a separate plugin.
- **The huge-data substrate (§5) is the load-bearing decision.**
  Standardising on OME-Zarr over Garage early prevents a future
  rewrite. The volume-pyramidiser sidecar is the one piece of new
  infrastructure required.

The plugin family adds ~3 new modules (or 2 if you count the
mesh fold-in) and one new sidecar (`volume-pyramidiser`). For
that cost, Shepard gets a vis story that competing RDM platforms
do not have. The view-recipe SPI is the architectural fact that
makes this incremental rather than monolithic — each plugin is
two SHACL shapes, one Java resolver, one Vue component, and one
markdown trio of docs.

---

## 12. Sources

### Primary references in this repo

- `aidocs/agent-findings/trace3d-spike.md` — the seed library survey for Trace3D (TresJS lean, view-shape TTL, multi-trace pattern).
- `aidocs/semantics/98-shapes-views-and-process-model.md §2` — VIEW_RECIPE as a `:ShepardTemplate` (no new node label).
- `aidocs/platform/47-dev-experience-and-plugin-system.md §2 + §2.6` — plugin SPI + sidecar declaration pattern.
- `aidocs/agent-findings/garage-activation-runbook.md` — Garage S3 setup and signed-URL access pattern.
- `feedback_plugins_declare_sidecars.md` (memory) — sidecar manifest rule.
- `feedback_storage_s3_garage.md` (memory) — Garage is the S3 answer.

### External

- [TresJS official site](https://tresjs.org/)
- [@tresjs/core on npm](https://www.npmjs.com/package/@tresjs/core) — v5.8.1, May 2026
- [Tresjs/nuxt — archived Feb 1, 2026](https://github.com/Tresjs/nuxt)
- [VTK.js v35 release (March 2026)](https://www.kitware.com/vtk-js-v35-release/)
- [VTK.js BSD-3 license](https://github.com/Kitware/vtk-js/blob/master/LICENSE)
- [Kitware/vue-vtk-js reference wrapper](https://github.com/Kitware/vue-vtk-js)
- [itk-vtk-viewer (BSD-3, Kitware)](https://github.com/Kitware/itk-vtk-viewer)
- [niivue (BSD-2)](https://github.com/niivue/niivue) — neuroimaging volumetric, generalisable
- [Neuroglancer (Apache-2.0, Google)](https://github.com/google/neuroglancer)
- [OME-Zarr v0.5 spec (NGFF)](https://ngff.openmicroscopy.org/latest/)
- [OME-Zarr open scivis datasets](https://github.com/InsightSoftwareConsortium/OMEZarrOpenSciVisDatasets)
- [OME-Zarr publication (Moore et al., 2023, PMC10492740)](https://pmc.ncbi.nlm.nih.gov/articles/PMC10492740/)
- [Garage S3-compatible object store](https://garagehq.deuxfleurs.fr/) — per ADR-0024
- [deck.gl (MIT, vis.gl)](https://github.com/visgl/deck.gl)
- [Kadi4Mat plugin system (pluggy)](https://kadi4mat.readthedocs.io/en/latest/development/plugins.html) — competing RDM reference
- [Three.js GLTFLoader](https://threejs.org/docs/pages/GLTFLoader.html) / [STLLoader](https://threejs.org/docs/pages/STLLoader.html) — built-in mesh loaders
