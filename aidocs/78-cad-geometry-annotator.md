# aidocs/78 — 3D Geometry & FEM Annotator (`shepard-plugin-cad`)

**Date:** 2026-05-16
**Status:** Design — ready for slice planning.
**Audience:** Contributors; DLR researchers and engineers; operators deploying a CAD/FEM
workbench stack.
**Purpose:** Specify `shepard-plugin-cad`, a 3D geometry and FEM annotator plugin for the
shepard research data management platform.

Couples to: `aidocs/47` (PayloadKind / PayloadStorage SPI), `aidocs/45` (FileStorage S3 SPI),
`aidocs/30` (provenance and lineage), `aidocs/14` (semantic improvements / annotation vocabulary),
`aidocs/79-cpacs-annotator.md` (CPACS-specific annotator built on this foundation).

---

## 1. Motivation

shepard has no structured home for 3D geometry, CAD assemblies, or FEM mesh data. These are
central to DLR's engineering research: crash tests, AFP layup inspection, structural analysis,
wind-tunnel model geometry, robot test-bench assemblies, and aircraft structural simulations.
Today they land on shared filesystems with no provenance, no spatial search, and no structured
metadata.

The goal is a **3D annotator** — not a passive viewer. Researchers click geometry to annotate
it, select surface regions, tag components, and link annotations to semantic terms from
metadata4ing and domain ontologies. Annotations persist as first-class Neo4j entities attached
to the geometry payload, queryable via SPARQL and exportable alongside the geometry into target
simulation formats.

Three structural gaps this plugin closes:

1. **Provenance gap**: who produced this mesh, from which experiment, using which FEM solver
   version? Today this information lives in emails or offline file-naming conventions.
2. **Annotation gap**: which face is the wing leading edge? Which node set is the impact zone
   from this crash test? Without structured annotation, downstream simulations re-derive this
   geometry interpretation by hand.
3. **Format barrier**: DLR workflows span STEP, Nastran BDF, VTK, URDF, proprietary kernels
   (Paradigms, Pandora) — no single tool reads them all. shepard becomes the common intake
   point, converting where possible and caching derived formats.

---

## 2. Plugin shape

`shepard-plugin-cad` follows the PayloadKind / PayloadStorage SPI from `aidocs/47 §2`. It
registers a new payload kind — **`CadReference`** — alongside the existing `FileReference`,
`TimeSeriesReference`, etc.

### 2.1 Payload kind: `CadReference`

The original file is stored on S3 via the FS1a `FileStorage` SPI. On first access, a derived
glTF/GLB representation (or VTU for FEM formats) is generated and cached at:

```
/v2/cad-references/{appId}/derived.glb      ← CAD/mesh formats
/v2/cad-references/{appId}/derived.vtu      ← FEM/scientific formats
```

Both endpoints return an `ETag` header. Re-uploading the original file invalidates the cache.
Clients may pass `?force=true` to regenerate the derived format on demand.

### 2.2 Plugin registration

Plugin id: `cad` — matches `shepard.plugins.cad.enabled=true` in `application.properties`.

The plugin registers:

- **REST endpoints** under `/v2/cad-references/...`
- **Admin REST endpoints** under `/v2/admin/cad/...`
- **CLI subcommands** under `shepard-admin cad ...`
- **A Neo4j singleton config entity** `:CadConfig`

### 2.3 `:CadConfig` admin entity

```cypher
(:CadConfig {
  appId: "cad-config",
  enabled: false,
  conversionSidecarUrl: null,     // URL of pythonocc/meshio sidecar; null = browser-only
  maxBrowserSideMb: 50,           // assemblies above this threshold are sent to sidecar
  derivedCacheTtlDays: 30,        // how long derived glTF/VTU objects persist in S3
  converterSpiEnabled: false       // placeholder for DLR-internal CadConverter SPI (CAD1j)
})
```

### 2.4 Admin REST surface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/cad/config` | Current config |
| `PATCH` | `/v2/admin/cad/config` | RFC 7396 merge-patch; `@RolesAllowed("instance-admin")` |
| `GET` | `/v2/admin/cad/health` | Ping conversion sidecar (if configured); report reachable/unreachable |

CLI parity:

```
shepard-admin cad status
shepard-admin cad enable
shepard-admin cad disable
shepard-admin cad set-sidecar-url https://cad-sidecar.example.org
shepard-admin cad set-max-browser-mb 50
shepard-admin cad health
```

All flags: `--url`, `--api-key`, `--output={human,json}`.

---

## 3. Format support matrix

| Category | Format(s) | Extension(s) | Conversion path | Renderer | Notes |
|---|---|---|---|---|---|
| **Native web** | glTF, GLB | `.gltf`, `.glb` | None — served directly | Three.js | Zero round-trip; preferred upload target |
| **Simple mesh** | STL, OBJ, PLY, OFF | `.stl`, `.obj`, `.ply`, `.off` | None — browser loaders | Three.js (`STLLoader`, `OBJLoader`) | Server stores original; browser loads directly |
| **Engineering CAD (open)** | STEP, IGES | `.stp`, `.step`, `.igs`, `.iges` | Two-tier: cascadio WASM (browser, ≤ 50 MB); pythonocc sidecar (larger assemblies → glTF) | Three.js | cascadio is OpenCASCADE compiled to WASM (MIT-adjacent); sidecar off by default |
| **Engineering CAD (proprietary via STEP)** | NX, CATIA V5, Parasolid | `.prt`, `.CATPart`, `.CATProduct`, `.x_t`, `.x_b` | User exports to STEP; OCC handles from there | Three.js | Direct format reading requires vendor licenses (Spatial ACIS, Siemens Parasolid kernel) — not open-source; STEP is the realistic open path |
| **Robotics** | URDF + mesh bundle | `.urdf` + `.dae`/`.stl` | `urdf-loaders` (gkjohnson/urdf-loaders, MIT, JPL/NASA) → Three.js | Three.js | `CadBundle` groups URDF root + referenced meshes; kinematic joint tree panel |
| **Scientific / FEM mesh** | VTK unstructured grid | `.vtu`, `.vtp`, `.vtk` | None — served directly | vtk.js (Kitware, BSD, GPU-accelerated) | Separate renderer path for volumetric / scalar field data |
| **FEM formats (via meshio)** | Nastran BDF, Abaqus INP, CGNS, ExodusII, MED, OpenFOAM | `.bdf`, `.dat`, `.inp`, `.cgns`, `.exo`, `.e`, `.med`, OpenFOAM dir | Python `meshio` converts to VTU → vtk.js | vtk.js | meshio supports 40+ formats; runs in meshio sidecar (same or separate container) |
| **DLR-internal — Paradigms kernel** | DLR parametric geometry | varies | TBD — `CadConverter` SPI placeholder for adapter JAR | Three.js (via glTF) | DLR-internal only as of 2026-05-16; pending access from DLR geometry group (see §9) |
| **DLR-internal — Pandora (DLR-BT-SIN)** | Multi-format CAD (BT-SIN workflows) | varies | Pandora CLI/API → standard output (STEP, STL, OBJ) → standard path | Three.js or vtk.js | DLR-internal only; Pandora output targets open formats; details pending BT-SIN access agreement (see §9) |

---

## 4. Conversion pipeline architecture

Three tiers, chosen in order of availability:

```
                       Upload original file
                              │
                   ┌──────────▼───────────┐
                   │   shepard S3 store   │
                   │  (original, always)  │
                   └──────────┬───────────┘
                              │
              ┌───────────────┼──────────────────┐
              │               │                  │
              ▼               ▼                  ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
   │  TIER 1      │  │  TIER 2      │  │  TIER 3          │
   │  Browser     │  │  Backend     │  │  meshio pipeline │
   │  WASM        │  │  sidecar     │  │  (FEM formats)   │
   │  (cascadio)  │  │  (pythonocc) │  │                  │
   │              │  │              │  │                  │
   │  STEP/IGES   │  │  STEP/IGES   │  │  BDF/INP/CGNS/  │
   │  ≤ 50 MB     │  │  > 50 MB     │  │  EXO/MED/OF     │
   │              │  │  + larger    │  │                  │
   │  → glTF in   │  │  assemblies  │  │  → VTU           │
   │    browser   │  │              │  │                  │
   │  (no server  │  │  → glTF      │  │  vtk.js loads    │
   │   round-trip)│  │  → S3 cache  │  │  VTU client-side │
   └──────────────┘  └──────────────┘  └──────────────────┘
              │               │                  │
              └───────────────┼──────────────────┘
                              │
                   ┌──────────▼───────────┐
                   │  Derived cache (S3)  │
                   │  derived/{appId}/    │
                   │    geometry.glb      │
                   │    geometry.vtu      │
                   │  ETag per object     │
                   └──────────────────────┘
```

**Tier 1 — Browser WASM (cascadio):** OpenCASCADE compiled to WASM. STEP/IGES → glTF in the
browser, no round-trip to the server. Best for assemblies up to `maxBrowserSideMb` (default
50 MB). The derived glTF is POSTed back to `POST /v2/cad-references/{appId}/derived` for
caching after first conversion.

**Tier 2 — Backend sidecar (pythonocc, optional Docker service):** Large or complex assemblies
that exceed the WASM budget are sent to `conversionSidecarUrl`. The sidecar is an optional
service (`docker compose --profile cad-sidecar up`); off by default. When absent, assemblies
above the threshold are marked `CONVERSION_DEFERRED` and the user is notified.

**Tier 3 — meshio pipeline:** FEM formats (BDF, INP, CGNS, ExodusII, MED, OpenFOAM) are
converted to VTU by a Python meshio process (same sidecar container, separate endpoint, or a
dedicated `cad-meshio` service). The resulting VTU is cached in S3 and served at
`/v2/cad-references/{appId}/derived.vtu`.

**DLR-internal adapters (CAD1j):** the `CadConverter` SPI (§2) allows Paradigms and Pandora
adapters to inject into Tier 2 as drop-in plugin JARs. No modification to `shepard-plugin-cad`
core is needed; the adapter registers itself under the relevant MIME type.

---

## 5. Frontend annotator

The frontend component (`CadAnnotator.vue`) is an **annotator**, not just a viewer.

### 5.1 Dual renderer architecture

Two renderer paths live inside the same Vue 3 component, selected by the derived format served:

- **Three.js path** (CAD/mesh): for glTF, STL, OBJ, URDF. Uses `three-mesh-bvh` (MIT) for fast
  raycasting against large meshes with millions of triangles.
- **vtk.js path** (FEM/scientific): for VTU/VTP. Supports colored scalar fields (stress,
  temperature, displacement magnitude) via vtk.js lookup tables and color bars.

The backend signals which path to use via the `Content-Type` header on the derived format
endpoint (`model/gltf-binary` vs `model/vnd.vtk`).

### 5.2 Annotation interaction modes

| Mode | Trigger | Selection geometry | Annotation type produced |
|---|---|---|---|
| **Point** | Single click on face | Barycentric UV + face index + world XYZ | `POINT` |
| **Region** | Shift+drag | Frustum or screen-space lasso selection of faces/nodes | `REGION` |
| **Surface** | Alt+click | Face-flood-fill by normal threshold (configurable angle) | `SURFACE` |
| **FEM** (vtk.js) | Box or sphere brush | Node IDs or element IDs within brush | `FEM_NODE_SET` / `FEM_ELEMENT_SET` |
| **Component** (URDF) | Click link in joint tree | Link name in kinematic tree | `LINK` |

The active mode is selected from the toolbar. The mode selector is hidden for formats that
do not support it (e.g. Component mode is only shown for URDF payloads).

### 5.3 Annotation panel

A left sidebar lists all `GeometryAnnotation` nodes for the current `CadReference`, grouped by
type and filterable by semantic term. Clicking an annotation in the panel:

1. Highlights the annotated region in the 3D scene (colored overlay).
2. Opens the annotation detail card (label, semantic term, type, `aiGenerated` flag, bounding box).
3. Focuses the camera on the annotation bounding box.

Annotations may be created, edited (label + semantic term), or deleted from the panel. The
panel is read-only for users with Reader permission on the parent DataObject.

### 5.4 Semantic term picker

The annotation detail card includes a semantic term picker that queries the active ontologies
from N1c2 (`GET /v2/admin/semantic/ontologies`) — the same vocabulary as existing
`AnnotationItem` semantic links. An IRI from metadata4ing, QUDT, or a domain ontology can be
attached to any `GeometryAnnotation`.

---

## 6. Spatial annotation model (`GeometryAnnotation`)

### 6.1 Neo4j entity

New node `:GeometryAnnotation` linked from `:CadReference` via `[:HAS_GEOMETRY_ANNOTATION]`.

```
(:CadReference {appId: $refAppId})
    -[:HAS_GEOMETRY_ANNOTATION]->
(:GeometryAnnotation {
  appId           String   -- UUID v7, HasAppId
  label           String   -- human-readable tag
  annotationType  String   -- enum: POINT | REGION | SURFACE | VOLUME |
                           --       FEM_NODE_SET | FEM_ELEMENT_SET | LINK
  aiGenerated     boolean  -- true when produced by ML model (future)
  confidence      double   -- optional; ML model confidence score

  -- World-space encoding (format-agnostic; primary for export):
  worldPosition   double[3]   -- centroid (POINT / SURFACE / REGION / VOLUME)
  worldNormal     double[3]   -- surface normal (SURFACE)
  worldAabb       double[6]   -- [minX, minY, minZ, maxX, maxY, maxZ] (REGION / VOLUME)

  -- Topology-relative encoding (precise; format-coupled):
  meshNodeName    String      -- glTF node name or URDF link name
  primitiveIndex  int         -- glTF mesh primitive index
  faceIndices     int[]       -- SURFACE / REGION face set (indices into primitive)
  barycentricUV   double[2]   -- POINT: UV within the face
  femNodeIds      long[]      -- FEM_NODE_SET: Nastran / VTK node IDs
  femElementIds   long[]      -- FEM_ELEMENT_SET: Nastran / VTK element IDs

  -- Semantic link (same vocabulary as existing AnnotationItem):
  semanticTermIri  String     -- IRI from metadata4ing / domain ontology; nullable
})
```

**Dual encoding rationale:** world-space coordinates survive format conversion and re-meshing;
topology-relative indices (face, node, element IDs) are precise for same-format round-trips.
Export pipelines use world-space as the primary signal; topology-relative as the precise anchor
when the same format is the target.

### 6.2 REST surface

All paths require at minimum Reader permission on the parent DataObject; POST/PATCH/DELETE
require Writer.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/cad-references/{refAppId}/annotations` | List all annotations; filterable by `type`, `semanticTermIri`, `aiGenerated` |
| `POST` | `/v2/cad-references/{refAppId}/annotations` | Create annotation |
| `GET` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}` | Get single annotation |
| `PATCH` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}` | RFC 7396 merge-patch (label, semanticTermIri) |
| `DELETE` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}` | Delete annotation |

Permissions piggyback the parent DataObject's permission graph — no separate ACL is
introduced for annotations.

---

## 7. Scene graph export API

`GET /v2/cad-references/{appId}/export?format={glb,usd,sdf,obj,stl,vtu}`

| Format | Target environment | Annotations embedded? | Mechanism |
|---|---|---|---|
| glTF / GLB | Unity, Unreal Engine, web, universal | Yes | Custom `SHEPARD_annotations` JSON extension on mesh nodes (glTF `extensions` field) |
| USD / USDZ | NVIDIA Omniverse, Apple RealityKit | Yes | `UsdGeomSubset` per annotated face set + custom metadata schema (`shepard:annotationType`, `shepard:semanticTermIri`) |
| SDF | Gazebo (ROS 2 simulation) | Yes | `<plugin>` semantic tag per annotated component/link; annotation IRI in plugin namespace |
| OBJ | Blender, legacy tools | No | OBJ format has no annotation mechanism; geometry only |
| VTU | ParaView, VTK-based pipelines | Yes | Field data arrays carrying annotation metadata as `vtkStringArray` + `vtkDoubleArray` |

### 7.1 Live scene sync (SSE stream)

`GET /v2/cad-references/{appId}/annotations/stream`

Server-Sent Events stream emitting events as annotations are created, updated, or deleted.
Intended for live simulator sync with Gazebo transport or NVIDIA Omniverse `omni.replicator`.

Event shape:

```json
{
  "eventType": "ANNOTATION_CREATED",
  "annotationAppId": "...",
  "cadReferenceAppId": "...",
  "annotation": { ... }
}
```

Event types: `ANNOTATION_CREATED`, `ANNOTATION_UPDATED`, `ANNOTATION_DELETED`.

---

## 8. Phasing (CAD1a – CAD1j)

| ID | Deliverable | Size | Gate |
|---|---|---|---|
| **CAD1a** | `CadReference` Neo4j entity + S3 storage via FS1a SPI + plugin stub + `:CadConfig` admin entity + admin GET/PATCH/health REST + CLI parity + `CadConverter` SPI interface (placeholder, no implementations) | M | FS1a (S3 SPI) |
| **CAD1b** | Three.js annotator frontend — glTF/GLB native + STL/OBJ loaders; point annotation CRUD (`POINT` type); annotation panel sidebar; semantic term picker | M | CAD1a |
| **CAD1c** | cascadio WASM STEP/IGES → glTF browser-side conversion; derived glTF cache endpoint (`GET /v2/cad-references/{appId}/derived.glb`); ETag + `?force=true` regeneration | M | CAD1a |
| **CAD1d** | URDF + `CadBundle` entity grouping URDF root + referenced meshes; `urdf-loaders` integration; kinematic joint tree panel; `LINK` annotation type | M | CAD1b |
| **CAD1e** | Region + surface annotation modes: three-mesh-bvh picking for frustum/lasso selection; face-flood-fill for surface patches; `REGION` and `SURFACE` annotation types | M | CAD1b |
| **CAD1f** | vtk.js renderer path; meshio conversion sidecar (`docker compose --profile cad-meshio`); VTU derived cache endpoint; `FEM_NODE_SET` and `FEM_ELEMENT_SET` annotation types; scalar field color bar | L | independent of CAD1c |
| **CAD1g** | Scene graph export API: glTF, SDF, VTU with annotation embedding; `GET /v2/cad-references/{appId}/export?format=...` | M | CAD1e |
| **CAD1h** | USD / USDZ export + Omniverse annotation schema (`UsdGeomSubset` + custom metadata); `format=usd` export variant | M | CAD1g |
| **CAD1i** | SSE annotation stream for live simulator sync (`GET /v2/cad-references/{appId}/annotations/stream`) | S | CAD1g |
| **CAD1j** | Pandora converter bridge (DLR-BT-SIN) + Paradigms kernel adapter (DLR geometry group) — M each; both implement `CadConverter` SPI from CAD1a; drop-in JAR deployment; no change to plugin core | M each | CAD1a + access agreements (see §9) |

**CAD1a + CAD1b** are the minimal viable plugin: geometry stored, annotated, and queryable.
**CAD1c** adds the STEP/IGES conversion that makes the plugin useful for mechanical engineering
workflows. **CAD1f** adds FEM mesh support for simulation workflows. **CAD1j** is gated on
DLR-internal access agreements and can land independently of the open-source slices.

---

## 9. DLR-internal format notes

### 9.1 Paradigms geometry kernel

**What it is:** DLR's parametric geometry kernel used in aircraft design workflows (e.g. CPACS
geometry generation, wind-tunnel model parametrisation).

**Availability:** DLR-internal only as of 2026-05-16. Not publicly available.

**Integration path:** The `CadConverter` SPI (introduced in CAD1a) is a single-method interface:

```java
public interface CadConverter {
    InputStream toGltf(InputStream in,
                       String sourceMimeType) throws CadConversionException;
}
```

A Paradigms adapter can be shipped as a drop-in plugin JAR implementing this interface, without
modifying `shepard-plugin-cad` core. The adapter registers via CDI or the plugin manifest.

**Next step:** Contact the DLR geometry group to assess the Paradigms API surface and licensing
terms for a converter adapter. The SPI seam is ready; the adapter is blocked on access.

### 9.2 Pandora converter (DLR Institut für Bauweisen und Strukturtechnologie, BT-SIN)

**What it is:** A multi-format CAD converter developed at DLR Institut für Bauweisen und
Strukturtechnologie in Stuttgart. Used in aerospace structures workflows. Converts between
formats specific to structural analysis and manufacturing.

**Availability:** DLR-internal only. Not publicly available.

**Integration approach:** Pandora's output targets standard open formats (STEP, STL, OBJ). The
integration pattern is:

1. shepard receives an unsupported format upload.
2. The `CadConverter` SPI adapter for Pandora invokes the Pandora CLI or REST API (subprocess
   or HTTP, whichever Pandora exposes) with the input file.
3. The Pandora output (STEP, STL, OBJ) is picked up and injected into the standard conversion
   pipeline (Tier 1 or Tier 2 per §4).
4. The original and converted intermediate are both stored; the derived glTF is cached per §4.

**Next step:** Contact the BT-SIN team for CLI/API documentation and an access agreement. The
`CadConverter` SPI placeholder is already in CAD1a; the Pandora adapter implementation is
straightforward once CLI/API documentation is available.

### 9.3 Common principle for DLR-internal adapters

Both Paradigms and Pandora adapters follow the same pattern: implement `CadConverter`, package
as a JAR, deploy alongside `shepard-plugin-cad`. shepard never needs to know the internal
format details — it only sees the SPI contract. This keeps the open-source core clean and the
DLR-internal adapters in a separate (potentially private) repository.

---

## 10. See also

- `aidocs/47-dev-experience-and-plugin-system.md` — PayloadKind / PayloadStorage SPI (plugin
  foundation this design builds on)
- `aidocs/45-filestorage-s3-spi.md` — FileStorage S3 SPI (FS1a, prerequisite for CAD1a)
- `aidocs/79-cpacs-annotator.md` — CPACS-specific annotator (separate plugin, built on the
  CAD1 foundation and `CadReference` payload kind)
- `aidocs/30-provenance-and-lineage.md` — provenance and lineage design (geometry conversion
  operations as PROV-O activities; links mesh upload → conversion → annotation)
- `aidocs/14-semantic-improvements.md` — semantic annotation vocabulary
  (`GeometryAnnotation.semanticTermIri` reuses the same metadata4ing vocabulary)
