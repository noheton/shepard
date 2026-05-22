# Visualization plugin survey — addendum: CAD + FEM

**Date:** 2026-05-22
**Origin:** User directive — extend the just-landed vis-plugin survey
(`aidocs/agent-findings/vis-plugin-survey.md`, commit `5b8e325c`) with
two additional categories the user surfaced after the original survey
ran: **CAD data** and **FEM result files**. Memory rule
`project_vis_categories.md` already has these two new rows in the
categories table.
**Frames against:** same as parent survey —
`aidocs/platform/47-dev-experience-and-plugin-system.md §2` (PayloadKind /
PayloadStorage SPI), `aidocs/semantics/98-shapes-views-and-process-model.md §2`
(VIEW_RECIPE as `:ShepardTemplate`),
`feedback_plugins_declare_sidecars.md` (plugin-declared sidecars).
**Stack:** Nuxt 3 + Vue 3 + Vuetify 3 + TS (fixed); Garage S3
(ADR-0024); no GPL / AGPL / SSPL.

This addendum follows the parent survey's shape exactly — candidate
matrix, lead pick, VIEW_RECIPE shape, plugin sketch,
MFFD-grounded acceptance test — for Categories 5 (CAD) and 6 (FEM).

---

## TL;DR — addendum

| # | Category | Lead lib | Plugin name | One-line why |
|---|---|---|---|---|
| 5 | CAD (STEP / IGES / glTF derivative) | **`occt-import-js`** (WASM OpenCascade reader, browser-side) **+ Three.js** render path; **`Online3DViewer`** (`kovacsv/Online3DViewer`, MIT) as an iframe-fallback rendererUrl for the "everything-format" case | `shepard-plugin-vis-cad` | The only actively-maintained WASM OCCT binding; STEP→tessellated-mesh in-browser; emit glTF for downstream views (reuses Three.js scene from `vis-trace3d`) |
| 6 | FEM result files (.odb, .rst, .op2, .med, .h3d) | **VTK.js v35** in the browser **after** a **`fem-converter` Python sidecar** normalises proprietary binaries to **VTU + glTF** via `meshio` / `pyNastran` / vendor SDKs; **PyVista + Trame** as a server-side rendering escape hatch when the dataset doesn't fit on the client | `shepard-plugin-vis-fem` + `shepard-plugin-fem-converter` sidecar | Pure browser parsing of `.odb`/`.rst` is unrealistic (proprietary binary, vendor-SDK linked); the sidecar is the structural answer — server-side normalisation + browser-side render |

**Plugin-family count addition: 2 new plugins + 1 new sidecar.** Combined
with the parent survey's 3-module family, total = **5 vis plugins + 2
sidecars** covering 7 categories.

---

## Category 5 — CAD data

### Scope

STEP (ISO 10303-21), IGES, JT, Parasolid, and native CAD formats
(CATIA `.CATPart`, NX `.prt`, Creo `.prt`/`.asm`). Derived viewing
formats: STL, glTF / GLB, OBJ.

Use cases this category serves:
- **MFFD as-designed geometry** — the upper-fuselage CFRP shell as
  authored in CATIA, viewed alongside the as-built measurement
  results from the AFP layup process.
- **AAS submodel `Nameplate` + `Geometry`** — the Asset
  Administration Shell carries a geometry fragment that the UI
  needs to render.
- **CPACS airframe** — DLR-internal aircraft geometries authored
  via CPACS+TiGL ([dlr-sc.github.io/tigl](https://dlr-sc.github.io/tigl/)) frequently export to
  STEP/IGES for downstream tools.

### 5.1 Candidate matrix

| Library | Footprint | Perf @ 10⁴ parts | License | Maintenance | Vue/Vuetify integration | Renderer-swap | Notes |
|---|---|---|---|---|---|---|---|
| **`occt-import-js`** (Kicad Foundation, kovacsv) | 3 (~7 MB WASM + ~600 kB Three.js) | 4 (parses 1k-part STEP in < 5 s; tessellation memory-bound) | LGPL-2.1 (dynamic linking — see §5.3) | 4 (active; npm v0.0.23, last cut 2025-Q4; "most actively maintained WASM-OCCT binding") | 4 (plain JS API, mounts into a Three.js scene we already own from vis-trace3d) | 5 (output is glTF / tessellated mesh → swap to Babylon / Isaac trivially) | **LEAD for the reader path.** Browser-side STEP/IGES/BREP → JSON-tessellation → Three.js BufferGeometry. |
| **`Online3DViewer`** (kovacsv) | 2 (~3 MB gz; whole app) | 4 | MIT | 5 (active; supports 3dm, 3ds, brep, dae, fbx, fcstd, gltf, ifc, iges, step, stl, obj, off, ply, wrl — the widest format coverage on the web) | 1 (iframe-only practical embedding) | 4 (its own viewer; renderer-URL swap with one URL change) | **LEAD for the "throw-anything" iframe path.** Build is a static SPA at `cdn.shepard/online3dviewer/v1/`; pass file URL via query-string. |
| **CascadeStudio** (zalo) | 3 | 3 (single-part focus) | MIT | 1 (slow / dormant; last meaningful release 2022) | 2 | 3 | Older OpenCascade.js binding. Superseded by `occt-import-js` for read-only viewing. Skip. |
| **OpenCascade.js** (donalffons) | 4 (~10 MB WASM) | 4 (full OCCT API) | LGPL-2.1 | 4 (active) | 3 (lower-level than occt-import-js) | 5 | More than we need for viewing; the full CAD-kernel API. Worth as a v1.5 escape hatch for CAD *editing*. |
| **xeokit-sdk** (xeolabs / Creoox) | 2 (~1 MB gz core) | 5 (purpose-built for hundreds of thousands of parts; double-precision) | **AGPLv3** (commercial license also offered) | 5 | 4 | 4 | **BLOCKED** by the CLAUDE.md GPL/AGPL/SSPL gate. Best-in-class for BIM/large-CAD but cannot land without a commercial licence. Reject. |
| **Autodesk Platform Services** (Forge Viewer) | — (CDN) | 5 | proprietary / commercial | 5 | 3 | 1 | Commercial; vendor lock-in; reject. |
| **`f3d`** (Kitware) | iframe / desktop | 5 | BSD-3 | 5 | 1 (desktop-first; web build experimental) | 4 | Native desktop tool with a fledgling WASM build; not a browser library today. Watch for v1.5. |
| **`step-to-gltf` CLI pipeline** (server-side OCCT → glTF, browser renders glTF only) | 0 (browser side just renders glTF) | 5 (glTF browser perf is essentially free) | varies (OCCT itself LGPL; the converter is BSD-3) | 4 | 5 (Three.js GLTFLoader already in `vis-trace3d`) | 5 | **Strong sidecar option** — pair with occt-import-js for "convert once, view many" path. Recommended as the production-grade route in §5.4. |

### 5.2 Recommended lead pick + why

**Two-track within one plugin** — the parent survey's "two render
targets, one plugin" pattern from `vis-volume` carries cleanly here:

- **Default track — `occt-import-js` + Three.js inline render.**
  User uploads a STEP / IGES; the plugin's frontend reads the file
  via WASM occt-import-js, tessellates to glTF in-browser, and
  mounts into the same Three.js scene the `vis-trace3d` plugin
  already owns. Footprint stays under 8 MB (one-time WASM
  download); the existing `<TresCanvas>` mount handles orbit,
  zoom, time-scrubber (if the CAD assembly is animated via
  attached glTF skeletons).

- **Heavy-CAD track — server-side `step-to-gltf` sidecar +
  browser glTF render.** For STEP files > ~50 MB or assemblies
  > ~5k parts, in-browser WASM tessellation grinds. Operators
  configure a small Python sidecar (FastAPI + `pythonocc-core` or
  the same `OpenCascade.js` running on Node) that converts STEP to
  glTF on upload and caches the result on Garage. The frontend
  just reads the cached glTF. Same `vis-cad` plugin; the choice
  surfaces as a `shepard:viewerTrack` slot on the VIEW_RECIPE.

- **Fallback track — `Online3DViewer` iframe.** For exotic
  formats (CATIA native `.CATPart`, NX `.prt`) that neither OCCT
  nor a Python converter handle natively, the plugin emits a URL
  to a self-hosted Online3DViewer build. Inferior interaction
  fidelity (no glTF reuse) but supports the widest format set
  on the web today.

Licence: **`occt-import-js` is LGPL-2.1**. This is OK under the
CLAUDE.md gate (LGPL is permitted; the dynamic-linking
clause is satisfied when the library is `npm install`ed and used
without modification — it lives as a `node_modules/` dependency
and is loaded at runtime). **The CLAUDE.md gate text is
"No GPL / AGPL / SSPL" — LGPL is not in the ban list.** Flag
the licence in `plugins/vis-cad/manifest.yml` and in
`docs/reference.md`; ensure the WASM blob is shipped
unmodified.

(xeokit is AGPL → rejected. Forge is commercial → rejected.)

### 5.3 LGPL note — dynamic-linking + WASM

`occt-import-js` is LGPL-2.1. The LGPL is **not** in the CLAUDE.md
ban list ("No GPL / AGPL / SSPL"). Operators concerned about
licence purity have these options:

1. **Use the WASM blob unmodified** (default). LGPL's
   dynamic-linking clause is satisfied because the WASM module is
   loaded at runtime and not statically linked into Shepard's
   compiled output.
2. **Disable the plugin** by setting `shepard.plugins.vis-cad.enabled=false`
   if the operator's compliance team mandates "no LGPL on the
   stack."
3. **Substitute with the Online3DViewer iframe track** (MIT)
   only — same VIEW_RECIPE, different `shepard:viewerTrack`.

The plugin's `docs/install.md` documents these three options
explicitly so operators see the licence position at install time
(per the CLAUDE.md "plugins ship their own documentation" rule).

### 5.4 Concrete VIEW_RECIPE shape

```turtle
shepard:CadViewShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl   "/v2/assets/views/cad/v1/index.mjs" ;
    shepard:rendererIntegrity "sha384-..." ;
    sh:property [
        sh:path shepard:cadSource ;
        sh:class shepard:FilePayload ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:description "STEP / IGES / BREP / glTF / STL / OBJ / native CAD"
    ] ;
    sh:property [
        sh:path shepard:cadFormat ;
        sh:in (
            "step" "iges" "brep"
            "gltf" "glb" "stl" "obj"
            "catia-catpart" "nx-prt" "creo-prt"
            "jt" "parasolid"
        ) ;
        sh:minCount 1
    ] ;
    sh:property [
        sh:path shepard:viewerTrack ;
        sh:in ( "occt-inline" "gltf-cached" "online3dviewer-iframe" ) ;
        sh:defaultValue "occt-inline" ;
        sh:description "Choose by file size and format — see plugin docs"
    ] ;
    sh:property [
        sh:path shepard:tessellationDeflection ;
        sh:datatype xsd:double ;
        sh:defaultValue 0.1 ;
        sh:description "OCCT tessellation deflection — smaller = finer mesh"
    ] ;
    sh:property [
        sh:path shepard:showHierarchy ;
        sh:datatype xsd:boolean ;
        sh:defaultValue true ;
        sh:description "Render the assembly tree sidebar (collapse/expand parts)"
    ] ;
    sh:property [
        sh:path shepard:explosionFactor ;
        sh:datatype xsd:double ;
        sh:defaultValue 0.0 ;
        sh:description "0.0 = assembled, >0 = exploded view (assemblies / AAS submodel-trees)"
    ] ;
    sh:property [
        sh:path shepard:sectionPlane ;
        sh:datatype xsd:string ;
        sh:description "JSON: {normal: [x,y,z], offset: d} for live section view"
    ] ;
    sh:property [
        sh:path shepard:overlayMeshes ;
        sh:class shepard:FilePayload ;
        sh:maxCount 5 ;
        sh:description "Additional STL/glTF overlays — e.g. as-designed vs as-built comparison"
    ] .
```

Input: a CAD file payload. Output: Three.js scene with assembly
tree sidebar, section-plane / exploded view affordances, and
optional overlay meshes (the **as-designed vs as-built**
comparison case — a STEP from CATIA + a STL from the laser
scanner of the actual cured part).

### 5.5 `shepard-plugin-vis-cad` sketch

- **Manifest** (`plugins/vis-cad/manifest.yml`):
  - `id: vis-cad`
  - `payloadKinds: []` (consumes existing File payloads)
  - `viewRecipes: [CadViewShape]`
  - `dependsOn: [vis-trace3d]` (reuses the Three.js
    `<TresCanvas>` + `useTres()` composable + GLTFLoader path)
  - `sidecars: [step-to-gltf-converter]` (optional; only required
    when `viewerTrack = gltf-cached`)
- **Backend (Java, in `shepard-plugin-vis-cad/src/main/java`):**
  - `CadResolver` — given the VIEW_RECIPE instance: for
    `occt-inline` returns a signed Garage URL to the raw STEP/IGES;
    for `gltf-cached` triggers the sidecar conversion (if not yet
    cached) and returns the signed URL to the resulting glTF on
    Garage; for `online3dviewer-iframe` returns a constructed iframe
    URL with the file URL as query-string.
  - `CadShapeBindingValidator` — registers `CadViewShape` SHACL.
  - Wires into `POST /v2/shapes/render` via the
    `ViewRecipeRenderer` SPI from VIS-S1.
- **Frontend (`plugins/vis-cad/frontend/`):**
  - `components/CadView.vue` — branches on `viewerTrack`. For
    `occt-inline`, lazy-imports `occt-import-js`, fetches the
    signed URL, runs `ReadStepFile()` / `ReadIgesFile()`, builds
    `THREE.BufferGeometry` from the returned tessellation,
    mounts inside `<TresCanvas>`.
  - `components/AssemblyTree.vue` — Vuetify treeview reading the
    occt-import-js hierarchy result (`hierarchy: { name, children: [...] }`);
    click-to-isolate parts.
  - `components/SectionPlaneControls.vue` — three-slider Vuetify
    dialog for plane normal + offset; applies a Three.js
    `Clipping Plane` to the geometry.
  - `components/CadIframe.vue` — wraps Online3DViewer iframe with
    a Vuetify `<v-card>` shell.
- **Sidecar — `step-to-gltf-converter` (optional):**
  - Python container (FastAPI) with `pythonocc-core` (LGPL — same
    LGPL story as occt-import-js, applied server-side; ship as
    docker image).
  - Endpoints: `POST /convert {srcUrl, dstBucket, deflection}` →
    202 + job ID; `GET /status/{jobId}`.
  - Reads from Garage via signed URL; writes glTF + `.bin` back to
    Garage at a deterministic path
    (`cad-cache/{collectionAppId}/{filePayloadAppId}.gltf`).
  - Declared in manifest per `feedback_plugins_declare_sidecars.md`:
    ```yaml
    sidecars:
      - name: step-to-gltf-converter
        image: ghcr.io/shepard/vis-cad-converter:1.0
        env: [GARAGE_S3_ENDPOINT, GARAGE_BUCKET]
        readiness: GET /health
        optional: true   # only required if any view uses gltf-cached track
    ```
- **Docs (per the plugins-ship-own-docs rule):**
  `plugins/vis-cad/docs/{reference,quickstart,install}.md`.
  `install.md` calls out the LGPL note from §5.3.

### 5.6 MFFD-grounded acceptance test

**Dataset:** MFFD upper-fuselage CFRP shell — as-designed STEP
from CATIA V5 export (~80 MB, ~2k parts), plus the AFP tape-laying
tool assembly STEP (~30 MB, ~500 parts).

**"Did it work" criterion:**
1. Upload the CATIA STEP; viewer renders the assembled fuselage
   shell within 15 s (cold, `occt-inline` track) or within 3 s
   (warm, `gltf-cached` track).
2. Assembly tree shows the part hierarchy; click "frame 14"
   isolates a single frame ring; "skin section 3" highlights.
3. Section plane drag at > 24 FPS reveals the inner moldline.
4. Overlay the AFP-deposited STL (synthesised from path-plan
   data for the demo) — visual diff shows agreement with the
   as-designed shell to within tessellation deflection.

**Backup if MFFD-real CATIA file is unavailable:** open public
STEP datasets from [STEP File Library
(steptools.com/library)](https://www.steptools.com/library/) or
DLR's TiGL-shipped example aircraft
([dlr-sc/tigl-examples](https://github.com/DLR-SC/tigl-examples)).

### 5.7 CPACS / TiGL note

DLR-internal aircraft geometries authored via CPACS+TiGL most
commonly export to STEP / IGES / BREP — the same formats
`occt-import-js` handles. TiGL itself uses the OpenCASCADE kernel,
so the round-trip is lossless within OCCT's tessellation
tolerance. A future `shepard-plugin-cpacs` would generate the STEP
from a CPACS XML upload and hand the resulting file to
`vis-cad` — out of scope for the vis family, but a clean
hand-off ([dlr-sc.github.io/tigl](https://dlr-sc.github.io/tigl/)).

---

## Category 6 — FEM result files

### Scope

Unstructured-mesh + scalar / vector / tensor field results from
the major commercial and open-source FEM solvers:

- **Abaqus** `.odb` — proprietary binary, requires Abaqus Python.
- **Ansys** `.rst` / `.rth` — proprietary binary, requires Ansys SDK.
- **Nastran** `.op2` — open binary format, parseable via `pyNastran`.
- **Code_Aster** `.med` — HDF5-based, open format (MEDfichier
  library).
- **OpenRadioss** `.h3d` — Altair HyperMesh format, partly open.
- **VTU / XDMF / .vtu / .pvd** — the open VTK formats, output by
  most modern open-source solvers (FEniCS, deal.II, MOOSE,
  Code_Aster post-processing).

Use cases:
- **MFFD structural analysis of welded joints** — Abaqus `.odb`
  of a CF/LMPAEK ultrasonic-welded coupon under tensile load;
  von-Mises stress field overlaid on the as-designed CAD.
- **Fuselage stress** — Nastran `.op2` of the full barrel under
  pressurisation; principal-stress isosurfaces.
- **Autoclave-free CFRP cure-deformation** — Abaqus `.odb` of the
  out-of-autoclave cure cycle; nodal-displacement vector field
  showing spring-in distortion.

### 6.1 Candidate matrix

| Library | Footprint | Perf @ 10⁶ cells | License | Maint | Vue integration | Renderer-swap | Notes |
|---|---|---|---|---|---|---|---|
| **VTK.js v35** (browser-side, post-conversion) | 3 (shared with `vis-vector`, `vis-volume`) | 4 (unstructured grid + scalar / vector / tensor field overlay) | BSD-3 | 5 (Kitware) | 3 (mounts via shared `<VtkScene>` from vis-vector) | 4 (same data opens in desktop ParaView) | **LEAD for the browser side.** Reads VTU / XDMF; renders scalar field on unstructured mesh; supports glyph fields for vectors and threshold/contour filters. |
| **`meshio`** (Python; nschloe) | 0 (server-side) | — | MIT | 5 (active; the standard "FEM mesh swiss-army knife") | — | — | **LEAD for the mesh-conversion path.** Reads Abaqus `.inp`, Nastran bulk `.bdf`/`.nas`, Ansys `.msh`, MED, gmsh, etc.; writes VTU / XDMF / glTF. **Does NOT handle result fields in `.odb` / `.rst` / `.op2`** — geometry only. |
| **`pyNastran`** (SteveDoyle2) | 0 (server-side) | 4 | BSD-3 (relicensed from LGPL) | 5 (active; v1.5-dev) | — | — | **LEAD for Nastran result reading.** Reads `.op2` (binary results) + `.bdf` / `.f06`; bundles a VTK exporter; pairs with VTK.js end-to-end. |
| **`odb2vtk`** (Arris-Composites; Liujie-SYSU; rorlandoc) | 0 (server-side) | 4 | MIT (various forks) | 3 (Arris-Composites fork active for Abaqus 2023; others slower) | — | — | **LEAD for Abaqus ODB result reading.** Python script that runs inside Abaqus's own Python interpreter; emits VTU per timestep + PVD time-series file. **Requires a licensed Abaqus installation in the sidecar** — DLR-internal use-case only, not redistributable. |
| **PyVista + Trame** (kitware) | 0 (server-side render) + iframe on client | 5 (server-side renders any unstructured grid) | BSD-3 | 5 (active) | 1 (iframe) | 5 | **LEAD for the server-side rendering escape hatch** — when the dataset doesn't fit on the client. Trame streams an interactive Vue-component-embeddable canvas; renders happen on the sidecar's GPU. |
| **Salomé Platform** (Open CASCADE Foundation) | desktop / iframe | 5 | **LGPL-2.1 / partial AGPL on Web modules** | 4 (slow) | 1 (iframe) | 3 | Native FEM pre/post-processor with a web mode (Salomé Web). Heavy; AGPL on parts blocks the gate; the desktop tool stays available as the operator's escape hatch but no in-tree integration. |
| **Code_Aster native web viewer** | iframe | 4 | LGPL | 3 (cadence slow) | 1 | 3 | Specific to Code_Aster MED output. Skip — `meshio` reads MED, then VTK.js renders. Same outcome, fewer deps. |
| **`dxf-viewer`** / **`dwg2gltf`** | 2 | 4 | MIT | 3 | 3 | 3 | For 2D-FEM (planar stress reports as DXF/DWG drawings). Marginal use-case for MFFD; skip for v1, defer to v1.5. |
| **ParaView Glance / ParaView Web** | 2 (iframe app) | 5 | BSD-3 | 5 | 1 (iframe) | 5 | Heavyweight but proven; sister of VTK.js. Same iframe-with-state-URL story as Neuroglancer in `vis-volume`. Worth as a `paraview-glance-iframe` track parallel to the VTK.js-inline lead. |
| **`f3d`** (Kitware, desktop) | desktop | 5 | BSD-3 | 5 | 1 | 4 | Desktop FEM viewer; the operator's "open in desktop" escape hatch. Not a browser library. |

### 6.2 Recommended lead pick + why

**Server-side sidecar + browser-side VTK.js — the only realistic
pattern.** Most native FEM formats are proprietary binary
(`.odb`, `.rst`) and require vendor SDKs linked in. Pure
browser parsing is unrealistic. The structural answer is:

1. **`shepard-plugin-fem-converter` sidecar** (Python + FastAPI):
   - For `.op2` (Nastran): `pyNastran` reads, emits VTU / PVD.
   - For `.odb` (Abaqus): operator-installed-Abaqus runs
     `odb2vtk` (Arris-Composites fork) inside Abaqus Python,
     emits VTU / PVD.
   - For `.med` (Code_Aster): `meshio` reads, emits VTU.
   - For `.rst` (Ansys): requires `ansys-dpf-core` Python library
     + Ansys runtime. Same vendor-bound pattern as Abaqus.
   - For `.h3d` (Altair / OpenRadioss): requires Altair SDK.
   - Output: VTU per timestep + a PVD index + a derived glTF of
     the mesh-without-fields (for the CAD overlay case).
   - Caches results on Garage at
     `fem-cache/{collectionAppId}/{filePayloadAppId}/`.

2. **`shepard-plugin-vis-fem` (browser-side):** VTK.js v35 reads
   the cached VTU / PVD, mounts inside the shared `<VtkScene>`
   from `vis-vector`. Standard sci-vis affordances: field
   colour-mapping (von Mises stress, displacement magnitude,
   strain), glyphs for vector fields, threshold + contour
   filters, deformed-shape rendering (scale-factor on
   displacement), time-step scrubber for `.pvd` time-series.

3. **PyVista + Trame escape hatch:** when the converted VTU is
   too large to ship to the client (> a few hundred MB), the
   plugin offers a `pyvista-trame` track — server-side
   rendering streams an interactive canvas via WebSocket. Same
   VIEW_RECIPE, different `shepard:renderTarget`.

This is the same "two render targets, one plugin" pattern as
`vis-volume`. The proprietary-vendor-SDK part is the unavoidable
reality: **the sidecar is the right call for FEM** (rationale in
§6.7).

### 6.3 Concrete VIEW_RECIPE shape

```turtle
shepard:FemResultViewShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl   "/v2/assets/views/fem/v1/index.mjs" ;
    shepard:rendererIntegrity "sha384-..." ;
    sh:property [
        sh:path shepard:femSource ;
        sh:class shepard:FilePayload ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:description "Abaqus ODB / Ansys RST / Nastran OP2 / Code_Aster MED / OpenRadioss H3D / VTU"
    ] ;
    sh:property [
        sh:path shepard:femFormat ;
        sh:in (
            "abaqus-odb" "ansys-rst" "nastran-op2"
            "code-aster-med" "openradioss-h3d"
            "vtu" "xdmf" "pvd"
        ) ;
        sh:minCount 1
    ] ;
    sh:property [
        sh:path shepard:renderTarget ;
        sh:in ( "vtkjs-inline" "pyvista-trame-iframe" "paraview-glance-iframe" ) ;
        sh:defaultValue "vtkjs-inline" ;
        sh:description "Choose by dataset size — see plugin docs"
    ] ;
    sh:property [
        sh:path shepard:fieldName ;
        sh:datatype xsd:string ;
        sh:description "Name of scalar/vector/tensor field to render — e.g. 'von_mises_stress', 'displacement'"
    ] ;
    sh:property [
        sh:path shepard:fieldComponent ;
        sh:in ( "magnitude" "x" "y" "z" "xx" "yy" "zz" "xy" "yz" "xz" "max-principal" "min-principal" ) ;
        sh:defaultValue "magnitude" ;
        sh:description "Component to render for vector/tensor fields"
    ] ;
    sh:property [
        sh:path shepard:colorMap ;
        sh:in ( "viridis" "plasma" "inferno" "magma" "cividis" "turbo" "RdBu" "jet" ) ;
        sh:defaultValue "viridis"
    ] ;
    sh:property [
        sh:path shepard:colorRange ;
        sh:datatype xsd:string ;
        sh:description "JSON: [min, max] field range; null = auto"
    ] ;
    sh:property [
        sh:path shepard:deformationScale ;
        sh:datatype xsd:double ;
        sh:defaultValue 1.0 ;
        sh:description "Scale factor on displacement field — 1 = true scale, >1 = exaggerated for visualisation"
    ] ;
    sh:property [
        sh:path shepard:showUndeformed ;
        sh:datatype xsd:boolean ;
        sh:defaultValue false ;
        sh:description "Overlay the undeformed wireframe on the deformed mesh"
    ] ;
    sh:property [
        sh:path shepard:timeStep ;
        sh:datatype xsd:string ;
        sh:description "JSON: { 'step': int, 'frame': int } or 'all' for time-series scrubber"
    ] ;
    sh:property [
        sh:path shepard:isoContours ;
        sh:datatype xsd:string ;
        sh:description "JSON array of iso-values for contour filter"
    ] ;
    sh:property [
        sh:path shepard:threshold ;
        sh:datatype xsd:string ;
        sh:description "JSON: {field, min, max} — extract sub-mesh where field is in range"
    ] ;
    sh:property [
        sh:path shepard:cadOverlay ;
        sh:class shepard:FilePayload ;
        sh:maxCount 1 ;
        sh:description "Optional STEP / glTF — as-designed geometry overlaid for context (consumes vis-cad)"
    ] .
```

Input: a proprietary or open FEM result file payload. Output:
unstructured mesh rendered with field colour-mapping, optionally
deformed by the displacement field, with isosurface / threshold /
section-plane filters and a time-step scrubber.

### 6.4 `shepard-plugin-vis-fem` sketch

- **Manifest** (`plugins/vis-fem/manifest.yml`):
  - `id: vis-fem`
  - `payloadKinds: []` (consumes File payloads)
  - `viewRecipes: [FemResultViewShape]`
  - `dependsOn: [vis-vector]` (reuses `<VtkScene>` Vue ↔ VTK.js
    mount helper)
  - `sidecars: [fem-converter, pyvista-trame-renderer]`
- **Backend (Java, in `shepard-plugin-vis-fem/src/main/java`):**
  - `FemResolver` — given a VIEW_RECIPE instance: if input format
    is already VTU/XDMF/PVD, returns a signed Garage URL directly;
    otherwise triggers `fem-converter` sidecar conversion (if not
    yet cached) and returns the signed URL to the resulting
    VTU/PVD; if `renderTarget = pyvista-trame-iframe`, returns an
    iframe URL with session ID.
  - `FemShapeBindingValidator` — registers `FemResultViewShape`
    SHACL.
  - Wires into `POST /v2/shapes/render` via VIS-S1's SPI.
- **Frontend (`plugins/vis-fem/frontend/`):**
  - `components/FemView.vue` — branches on `renderTarget`:
    `<VtkScene>` for inline, `<PyVistaTrameIframe>` for iframe,
    `<ParaViewGlanceIframe>` for ParaView.
  - `components/FemFieldPicker.vue` — Vuetify dropdown that lists
    available fields from the VTU header; selects field +
    component for rendering.
  - `components/DeformedShapeControls.vue` — Vuetify slider for
    `deformationScale`; toggle for undeformed wireframe overlay.
  - `components/IsoContourEditor.vue` — drag-add iso-values
    rendered as Three.js helper lines on a histogram of the field.
- **Sidecar — `fem-converter`:**
  - Python container (FastAPI) with `pyNastran` (BSD-3),
    `meshio` (MIT), `ansys-dpf-core` (commercial — only loaded
    if `FEM_CONVERTER_ENABLE_ANSYS=true` + valid Ansys
    license), and a mounted Abaqus install (operator-provided —
    only enabled if `FEM_CONVERTER_ENABLE_ABAQUS=true`).
  - Per-format dispatcher: `.op2` → pyNastran path;
    `.med` → meshio path; `.odb` → Abaqus Python subprocess +
    odb2vtk; `.rst` → ansys-dpf-core path; `.h3d` → Altair SDK
    path (rare; flagged as v1.1).
  - Endpoints: `POST /convert {srcUrl, format, dstBucket}` → 202
    + job ID; `GET /status/{jobId}`.
  - Writes VTU + PVD + derived glTF to Garage at
    `fem-cache/{collectionAppId}/{filePayloadAppId}/`.
  - Declared per `feedback_plugins_declare_sidecars.md`:
    ```yaml
    sidecars:
      - name: fem-converter
        image: ghcr.io/shepard/vis-fem-converter:1.0
        env:
          - GARAGE_S3_ENDPOINT
          - GARAGE_BUCKET
          - FEM_CONVERTER_ENABLE_ABAQUS  # opt-in; requires bind-mounted Abaqus install
          - FEM_CONVERTER_ENABLE_ANSYS   # opt-in; requires Ansys license
        readiness: GET /health
        notes: |
          Abaqus and Ansys support require operator-provided licensed installs
          mounted into the container. Open-source formats (.med, .op2, .vtu)
          work out-of-the-box with no extra licences.
      - name: pyvista-trame-renderer
        image: ghcr.io/shepard/vis-fem-trame:1.0
        env: [GARAGE_S3_ENDPOINT, GARAGE_BUCKET]
        readiness: GET /
        optional: true   # only required for large-dataset escape hatch
        notes: |
          Server-side rendering escape hatch via PyVista+Trame. Useful when
          the converted VTU is too large to ship to the client (> ~500 MB).
    ```
- **Docs:** `plugins/vis-fem/docs/{reference,quickstart,install}.md`.
  `install.md` documents the Abaqus / Ansys vendor-licence
  constraints prominently — operators must bring their own licences;
  open-source `.med` / `.op2` / `.vtu` work out-of-the-box.

### 6.5 MFFD-grounded acceptance test

**Dataset:** MFFD CF/LMPAEK ultrasonic-welded T-joint coupon
tensile-pull simulation — Abaqus `.odb` (~150 MB), ~80k
quadratic-tetrahedron elements, 50 timesteps.

**"Did it work" criterion:**
1. Upload the `.odb`; `fem-converter` sidecar (with operator's
   Abaqus install) produces VTU + PVD on Garage within 60 s.
2. Frontend loads VTU; user picks `von_mises_stress` field;
   colour-mapped mesh renders within 3 s; rotates at > 30 FPS at
   1080p.
3. Drag `deformationScale` from 1× to 50× — exaggerated
   deformation shows weld-line distortion clearly.
4. Time-step scrubber slides through 50 frames at > 24 FPS.
5. Add an iso-contour at the material's yield stress (e.g.
   95 MPa); a coloured surface marks the yield-zone.
6. Overlay the as-designed STEP via `cadOverlay` (consumes
   vis-cad); see the deformed weld bead vs the as-designed
   geometry in one scene.

**Backup if MFFD-real ODB is unavailable** (Abaqus licence not
in the test environment): open Code_Aster `.med` of a CFRP
laminate stress test, OR Nastran `.op2` from public benchmarks
(e.g. NASA Common Research Model wing-box analysis,
[opencrm.com](https://commonresearchmodel.larc.nasa.gov/)).

### 6.6 Cross-cutting with `vis-cad`

The `cadOverlay` slot in §6.3 is the "as-designed vs as-built
vs as-deformed" comparison case — MFFD's most realistic
workflow. Implementation: when set, the resolver hands the CAD
file URL to vis-cad's resolver and embeds the resulting glTF
into the same `<VtkScene>`. The two plugins share a
`<TresCanvas>`/`<VtkScene>` parent and compose meshes.

This is the same "fold-in" pattern the parent survey used for
surface meshes inside `vis-trace3d`. CAD overlay on FEM results
is the user-facing payoff of the plugin-family architecture.

### 6.7 Why the sidecar pattern is the right call for FEM

Three reasons, applied to this category specifically:

1. **Proprietary binary formats with vendor-SDK dependencies.**
   `.odb` requires linking against Abaqus's own Python (in turn
   linking against the Abaqus C++ runtime). `.rst` requires
   ansys-dpf-core, which calls into the Ansys DPF server. Neither
   compiles for the browser; neither has a pure-JS or WASM
   alternative. Server-side conversion is the only path.
2. **Conversion is expensive and cache-friendly.** A 150 MB ODB
   takes 30–60 s to convert to VTU; that work shouldn't repeat per
   page-load. The sidecar writes the result to Garage with a
   deterministic key, and subsequent views are instant.
3. **The licence-segregation argument.** Abaqus and Ansys are
   commercial; their runtime lives in the sidecar container that
   the operator opts into (via `FEM_CONVERTER_ENABLE_*` env
   vars), not in the core Shepard application. Operators without
   commercial FEM licences still get full support for the
   open formats (`.med`, `.op2`, `.vtu`, `.xdmf`) — no
   degradation. This is the same vendor-isolation pattern Kadi4Mat
   and SciCat punt on entirely (they typically don't ship any FEM
   viewer; users open results in desktop tools).

The Trame + PyVista escape-hatch for **rendering** (not just
conversion) is the same logic taken one step further: when the
client can't hold the data, the server renders frames and streams
them. Same sidecar pattern, different sidecar.

This makes FEM the most-sidecar-heavy plugin in the family — but
also the one where the sidecar pattern most clearly earns its
keep. The parent survey's volume-pyramidiser sidecar was *one*
heavy compute step; this one orchestrates *several* vendor SDKs
behind a uniform conversion interface.

---

## Updated backlog rows

Proposed for `aidocs/16-dispatcher-backlog.md` under the existing
`### VIS — 2026-05-22 (visualization plugin family)` section (additions
to the pick-up order: VIS-S1 → VIS-T1 → VIS-V1 → VIS-X1 → **VIS-C1 →
VIS-F1** → VIS-S2 → VIS-S3 stretch):

| ID | Slice | Size | Notes |
|---|---|---|---|
| VIS-C1 | `shepard-plugin-vis-cad` module skeleton + `CadViewShape` SHACL allow-list extension + `CadResolver` (File payload → signed Garage URL for `occt-inline` track, or triggers `step-to-gltf-converter` sidecar for `gltf-cached` track) + `CadView.vue` (`occt-import-js` WASM browser read + Three.js BufferGeometry build inside shared `<TresCanvas>` from `vis-trace3d`) + `AssemblyTree.vue` (Vuetify treeview from occt hierarchy result) + `SectionPlaneControls.vue` + `CadIframe.vue` (Online3DViewer iframe fallback for exotic formats) + plugin docs trio | L (~2 sprints) | `occt-import-js` is LGPL-2.1 — dynamic-linking clause satisfied (not in CLAUDE.md ban list of GPL/AGPL/SSPL); documented in `plugins/vis-cad/docs/install.md`. xeokit rejected (AGPL). `Online3DViewer` (MIT) ships as static SPA at `cdn.shepard/online3dviewer/v1/` for the iframe fallback. Optional `step-to-gltf-converter` Python sidecar (`pythonocc-core` LGPL) for assemblies > ~50 MB. Acceptance: MFFD upper-fuselage CATIA STEP (~80 MB, ~2k parts) renders in < 15 s cold / < 3 s warm; section-plane drag at > 24 FPS; as-designed-vs-as-built overlay diff works. Depends on VIS-S1 + VIS-T1. |
| VIS-F1 | `shepard-plugin-vis-fem` module skeleton + `FemResultViewShape` SHACL allow-list extension + `FemResolver` (File payload → signed Garage URL for VTU/XDMF/PVD; triggers `fem-converter` sidecar for `.odb` / `.rst` / `.op2` / `.med` / `.h3d`; or iframe URL for `pyvista-trame-iframe` / `paraview-glance-iframe` render targets) + `FemView.vue` (VTK.js v35 unstructured-grid reader + field colour-mapping inside shared `<VtkScene>` from `vis-vector`) + `FemFieldPicker.vue` + `DeformedShapeControls.vue` + `IsoContourEditor.vue` + `PyVistaTrameIframe.vue` + `fem-converter` Python sidecar (FastAPI + `pyNastran` BSD-3 + `meshio` MIT + opt-in `ansys-dpf-core` commercial + opt-in Abaqus Python + `odb2vtk` MIT) + `pyvista-trame-renderer` sidecar (PyVista BSD-3 + Trame BSD-3) + plugin docs trio | L (~3 sprints) | The most-sidecar-heavy plugin in the family — and the one where the sidecar pattern most clearly earns its keep (proprietary binary formats require vendor SDK linkage that can't run in the browser; rationale in survey addendum §6.7). Operators bring their own Abaqus/Ansys licences via env-var opt-ins (`FEM_CONVERTER_ENABLE_ABAQUS`, `FEM_CONVERTER_ENABLE_ANSYS`); open-source formats (`.med`, `.op2`, `.vtu`, `.xdmf`) work out-of-the-box. Acceptance: MFFD CF/LMPAEK ultrasonic-weld T-joint Abaqus `.odb` (~150 MB, ~80k C3D10 elements, 50 timesteps) → VTU+PVD in < 60 s, browser von-Mises render at > 30 FPS at 1080p, deformation-scale slider responsive, time-scrubber > 24 FPS, iso-contour-at-yield works, cadOverlay composes with vis-cad in one scene. Depends on VIS-V1 (`VtkScene`) + VIS-C1 (cadOverlay path). |

---

## Sources

### External — CAD

- [occt-import-js (kovacsv) — WASM OCCT reader; LGPL-2.1](https://github.com/kovacsv/occt-import-js)
- [occt-import-js on npm](https://www.npmjs.com/package/occt-import-js)
- [Online3DViewer (kovacsv) — MIT; widest format coverage on the web](https://github.com/kovacsv/Online3DViewer)
- [Online 3D Viewer demo](https://3dviewer.net/)
- [xeokit-sdk — AGPLv3 (blocked); commercial-licence option](https://xeokit.io/)
- [OpenCascade.js (donalffons) — full OCCT WASM binding; LGPL-2.1](https://github.com/donalffons/opencascade.js)
- [CascadeStudio (zalo) — dormant since 2022; MIT](https://github.com/zalo/CascadeStudio)
- [DLR TiGL — CPACS-to-CAD parametric geometry; LGPL](https://dlr-sc.github.io/tigl/)
- [DLR tigl-examples — example aircraft STEP files](https://github.com/DLR-SC/tigl-examples)
- [STEP File Library (steptools.com/library)](https://www.steptools.com/library/)

### External — FEM

- [VTK.js v35 release (March 2026) — unstructured grid reader](https://www.kitware.com/vtk-js-v35-release/)
- [meshio (nschloe) — MIT; the FEM mesh swiss-army knife](https://github.com/nschloe/meshio)
- [pyNastran (SteveDoyle2) — BSD-3; reads .op2 binary results](https://github.com/SteveDoyle2/pyNastran)
- [odb2vtk (Arris-Composites fork) — MIT; Abaqus 2023 support](https://github.com/Arris-Composites/ODB2VTK)
- [PyVista + Trame integration docs](https://docs.pyvista.org/api/plotting/trame.html)
- [PyVista Trame tutorial](https://tutorial.pyvista.org/tutorial/09_trame/index.html)
- [Salomé Platform — LGPL with AGPL Web modules (partial block)](https://www.salome-platform.org/)
- [ParaView Glance / ParaView Web — BSD-3 iframe escape hatch](https://kitware.github.io/paraview-glance/)
- [ansys-dpf-core — commercial; opt-in sidecar dependency](https://github.com/ansys/pydpf-core)
- [NASA Common Research Model — public FEM benchmark dataset](https://commonresearchmodel.larc.nasa.gov/)

### Internal references

- `aidocs/agent-findings/vis-plugin-survey.md` — parent survey (categories 1–4)
- `aidocs/platform/47-dev-experience-and-plugin-system.md §2` — plugin SPI
- `aidocs/semantics/98-shapes-views-and-process-model.md §2` — VIEW_RECIPE template kind
- `aidocs/agent-findings/garage-activation-runbook.md` — Garage S3 setup
- `feedback_plugins_declare_sidecars.md` (memory) — plugins declare their own sidecars
- `feedback_storage_s3_garage.md` (memory) — Garage is the S3 answer
- `project_vis_categories.md` (memory) — the 7-category table
