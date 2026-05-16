# aidocs/79 — CPACS Annotator (shepard-plugin-cpacs)

**Date:** 2026-05-16
**Status:** Design — ready for slice planning.
**Audience:** Contributors; DLR aircraft design researchers (AS, BT, SC institutes); operators
deploying the MDO toolchain.
**Purpose:** Specify `shepard-plugin-cpacs` — a plugin that stores CPACS aircraft configuration
files in shepard, generates 3D geometry via a TiGL sidecar, exposes the CPACS component
hierarchy as navigable Neo4j nodes, and supports XPath-anchored annotations on individual
aircraft components.

Couples to: `aidocs/47` (PayloadKind SPI), `aidocs/78` (shepard-plugin-cad, 3D geometry
annotator), `aidocs/30` (provenance), `aidocs/80` (RCE integration).

---

## 1. What CPACS is

CPACS (**Common Parametric Aircraft Configuration Schema**) is an XML-based aircraft
configuration format developed by DLR. Specification and tooling:
https://dlr-sl.github.io/cpacs-website/

### 1.1 Parametric, not pre-tessellated

Unlike CAD files (STEP, IGES, OBJ), CPACS describes aircraft geometry through **parameters**:
wing span, taper ratio, section profiles, dihedral angles, structural cross-sections. There
is no mesh in the file. Mesh generation is a runtime operation performed by TiGL at analysis
time. This has important consequences for the plugin:

- A CPACS file is small (tens of kilobytes to a few megabytes), even for a full aircraft.
- Two CPACS files with the same `uID` on a component may produce different tessellations
  (because TiGL parameters changed), but the XPath address of the component is stable.
- Annotations anchored to CPACS `uID`s survive moderate redesigns; face-index anchoring
  (as used in `aidocs/78`) does not.

### 1.2 Key toolchain

| Tool | Role | Language / bindings |
|---|---|---|
| **TiGL** (TiGL Geometry Library) | Generates B-rep / tessellated geometry from CPACS parameters. Outputs: STEP, IGES, STL, VTK, Brep, and (via STEP + pythonocc) glTF | C++ library; Python bindings: `tigl3` |
| **TiXI** | DLR XML utility for CPACS file parsing and XPath navigation | C++; Python bindings: `tixi3` |
| **RCE** | Workflow engine that passes CPACS files between MDO (multi-disciplinary optimisation) components | Java GUI tool |

Source: https://github.com/DLR-SC/tigl

### 1.3 DLR context

CPACS is the interchange format across DLR institutes in aircraft design chains:
aerodynamics (AS), structures (BT), software tools (SC). A single MDO workflow may route one
CPACS file through a dozen RCE components, each modifying a subset of the data
(aerodynamics tables, mass breakdown, structural sizing). Provenance tracking over that chain
— which component updated which part of the aircraft model — is a primary motivator for this
plugin.

### 1.4 Why a separate plugin from `aidocs/78` (shepard-plugin-cad)

| Dimension | CAD (aidocs/78) | CPACS (this doc) |
|---|---|---|
| Source format | Pre-tessellated mesh (STEP, OBJ, …) | Parametric XML — geometry must be **generated** |
| Identifier stability | Face indices shift on re-export | CPACS `uID` is designer-controlled, stable across redesigns |
| Component hierarchy | Flat or implicit assembly tree | Explicit named component tree (`/cpacs/vehicles/aircraft/model/…`) |
| Non-geometry data | None | Aerodynamics tables, mass breakdowns, performance data — all in the same file |
| Annotation anchor | Face index or world AABB | CPACS XPath (`/cpacs/…/wing[@uID='Wing1']`) + optional sub-geometry |

`shepard-plugin-cpacs` **depends on** `shepard-plugin-cad`. When TiGL conversion runs, the
generated GLB is stored as a first-class `CadReference` entity (linked from the
`CpacReference` via `[:HAS_DERIVED_CAD]`). This means:

- The GLB is addressable and annotatable via all existing CAD endpoints (CAD1d/e).
- The CPACS annotator's `CpacAnnotation` is a thin XPath-anchoring layer on top of the
  `CadReference` / `GeometryAnnotation` shape — reusing the established sub-geometry model
  rather than duplicating it.
- CAD1b's Three.js viewer is the frontend rendering surface; the CPACS component tree panel
  (CPACS1c) is a sidebar overlay that drives the same viewer.

The S3 key for the GLB file is owned by the `CadReference`'s `FileStorage` node, not by a
separate CPACS-managed cache path.

---

## 2. Plugin shape

`shepard-plugin-cpacs` follows the PayloadKind SPI from `aidocs/47`.

### 2.1 New payload kind: `CpacReference`

```
(:CpacReference {
  appId:        String     // UUID v7, stable external identifier
  name:         String     // designer-facing name ("D150 Baseline Rev3")
  description:  String
  cpacVersion:  String     // value of /cpacs/header/version in the XML
  createdAt:    DateTime
  updatedAt:    DateTime
})
-[:HAS_FILE]->    (:FileStorage {s3Key, contentType: "application/xml", size})
-[:HAS_DERIVED_CAD]-> (:CadReference {appId, name, …})  // created on first TiGL conversion
                           -[:HAS_FILE]-> (:FileStorage {s3Key: "…/geometry.glb", contentType: "model/gltf-binary"})
```

The `CadReference` entity is owned by `shepard-plugin-cad` and is created by the CPACS
plugin when TiGL conversion completes (CPACS1b). After creation it is a first-class entity
reachable and annotatable via all existing `/v2/cad-references/…` endpoints. The CPACS
plugin stores only the `[:HAS_DERIVED_CAD]` edge; it does not duplicate the CAD file
storage model.

Upload: `POST /v2/cpac-references` (multipart or pre-signed PUT, per FS1f).
Retrieval: `GET /v2/cpac-references/{appId}` — returns JSON with links to derived
endpoints (geometry, component tree, annotations).

### 2.2 Admin toggle and `:CpacConfig`

Admin toggle: `shepard.plugins.cpacs.enabled` (deploy-time default; runtime flip via
`PATCH /v2/admin/cpacs/config`).

```cypher
(:CpacConfig {
  appId:              "cpacs-config",
  enabled:            true,
  tiglSidecarUrl:     "http://tigl-sidecar:8080",   // required
  allowArbitraryXpath: false,                         // §6 XPath query endpoint
  derivedCacheEnabled: true,
  maxFileSizeMb:       500
})
```

`tiglSidecarUrl` is required — TiGL is a native C++ library and runs in a separate
container. Startup fails with a clear error if `enabled = true` and `tiglSidecarUrl` is
unset or unreachable.

### 2.3 Admin REST surface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/cpacs/config` | Current config (no secrets to mask) |
| `PATCH` | `/v2/admin/cpacs/config` | RFC 7396 merge-patch, `instance-admin` |
| `GET` | `/v2/admin/cpacs/health` | Ping TiGL sidecar; report reachable/unreachable |

CLI parity: `shepard-admin cpacs {status,enable,disable,set-tigl-url,set-xpath-query,health}`
with shared `--output={human,json}` + `--url` + `--api-key` flags.

---

## 3. TiGL conversion pipeline

TiGL is a native C++ library. It runs as a **sidecar container** that exposes a minimal
HTTP API. The backend calls the sidecar; the sidecar is stateless (reads CPACS from the
request body, writes GLB to the response).

### 3.1 Sidecar image

```
ghcr.io/dlr-shepard/shepard-tigl-sidecar:<version>
```

Base image: `python:3.12-slim`. Installed packages: `tigl3`, `tixi3`, `pythonocc-core`,
`trimesh`. Exposes:

```
POST /convert
Content-Type: application/xml
Body: CPACS XML

Response 200:
Content-Type: model/gltf-binary
Body: GLB binary
```

And:

```
POST /component-tree
Content-Type: application/xml
Body: CPACS XML

Response 200:
Content-Type: application/json
Body: component-tree.json  (§3.3)
```

### 3.2 Conversion steps (inside the sidecar)

1. Parse CPACS XML with TiXI; extract component `uID` list.
2. Open CPACS in TiGL; for each component, generate STEP via
   `tigl.exportComponent(uid, "STEP", outputPath)`.
3. Convert each STEP to glTF mesh via `pythonocc` STEP reader → `trimesh` GLTF export.
4. Assemble a single GLB with one glTF mesh node per component; set each node's `name`
   = component `uID` (e.g., `Wing1`, `Fuselage`, `HTP`). This naming is the bridge
   between the 3D scene and CPACS XPath addresses.
5. Return the assembled GLB.

### 3.3 Derived `CadReference` creation

On first `GET /v2/cpac-references/{appId}/derived.glb` (or triggered eagerly on upload
if the operator enables `derivedCacheEnabled`):

1. Backend streams the raw CPACS XML from S3 (via the `CpacReference`'s `FileStorage`).
2. Backend calls `POST {tiglSidecarUrl}/convert` with the XML body.
3. Sidecar returns GLB bytes.
4. CPACS plugin calls into `shepard-plugin-cad`'s `CadReferenceService` to create (or
   update) a `CadReference` entity:
   - Name: `"{CpacReference.name} — derived geometry"`.
   - GLB stored via the CAD plugin's `FileStorage` (S3 key managed by CAD, not CPACS).
   - `CpacReference -[:HAS_DERIVED_CAD]-> CadReference` edge written.
5. `GET /v2/cpac-references/{appId}/derived.glb` redirects (302) to
   `GET /v2/cad-references/{derivedCadAppId}/file` — the CAD endpoint serves the GLB.
   This keeps the serving path in CAD where it belongs.

On a subsequent CPACS XML upload, the plugin calls `CadReferenceService.updateFile(…)`
on the existing `CadReference` (the edge already exists; no new entity is created). This
triggers the mesh-change detection in §4.4.

Similarly, `GET /v2/cpac-references/{appId}/component-tree` is backed by
`derived/{appId}/component-tree.json` on S3 (S3 key managed by the CPACS plugin
independently of the CAD entity), generated by `POST {tiglSidecarUrl}/component-tree` and
cached with the same source-XML ETag pattern.

### 3.4 `component-tree.json` schema

```json
{
  "cpacVersion": "3.4",
  "components": [
    {
      "uid": "Fuselage",
      "displayName": "Fuselage",
      "componentType": "FUSELAGE",
      "xpath": "/cpacs/vehicles/aircraft/model/fuselages/fuselage[@uID='Fuselage']",
      "parentUid": null
    },
    {
      "uid": "Wing1",
      "displayName": "Wing (port)",
      "componentType": "WING",
      "xpath": "/cpacs/vehicles/aircraft/model/wings/wing[@uID='Wing1']",
      "parentUid": null
    },
    {
      "uid": "Wing1_Aileron",
      "displayName": "Aileron",
      "componentType": "CONTROL_SURFACE",
      "xpath": "/cpacs/vehicles/aircraft/model/wings/wing[@uID='Wing1']/componentSegments/…",
      "parentUid": "Wing1"
    }
  ]
}
```

---

## 4. CPACS component hierarchy in Neo4j

When a CPACS file is ingested (or when the component tree is first requested), the plugin
parses the component tree via the TiXI-backed sidecar and creates `:CpacComponent` nodes
linked from the parent `CpacReference`.

### 4.1 Graph model

```
(:CpacReference {appId, name})
  -[:HAS_COMPONENT]->
(:CpacComponent {
  appId:          String     // UUID v7
  uid:            String     // CPACS uID, e.g. "Wing1"
  displayName:    String
  componentType:  String     // enum (§4.2)
  xpath:          String     // full XPath to this element in the CPACS tree
  parentUid:      String     // null for top-level components
  meshHash:       String     // SHA-256 of the component's glTF mesh bytes in the current GLB
                             // set after TiGL conversion; used to detect geometry changes
})
```

`(:CpacComponent)-[:CHILD_OF]->(:CpacComponent)` edges encode the parent/child
relationship for components with a `parentUid`.

### 4.2 `componentType` enum

`FUSELAGE | WING | ENGINE | PYLON | CONTROL_SURFACE | LANDING_GEAR | OTHER`

### 4.3 Component REST surface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/cpac-references/{appId}/components` | List all components (flat, with parentUid for tree reconstruction) |
| `GET` | `/v2/cpac-references/{appId}/components/{componentAppId}` | Single component detail |
| `GET` | `/v2/cpac-references/{appId}/component-tree` | Full tree as `component-tree.json` (cached, see §3.4) |
| `GET` | `/v2/cpac-references/{appId}/derived.glb` | TiGL-generated GLB (cached, see §3.3) |

### 4.4 Mesh change detection

When a new CPACS XML is uploaded to an existing `CpacReference` (via `PUT
/v2/cpac-references/{appId}/file`):

1. The CPACS plugin calls `CadReferenceService.updateFile(derivedCadAppId, newGlb)` after
   TiGL re-conversion (§3.3). The `CadReference` entity's `FileStorage` is updated in place;
   the `[:HAS_DERIVED_CAD]` edge is unchanged.
2. The plugin reads the new GLB bytes from the updated `CadReference` file.
3. For each component, the plugin computes SHA-256 of the component's glTF mesh bytes in the
   new GLB and compares with the stored `meshHash` on the `:CpacComponent` node.
4. If a component's `meshHash` changed, all `:CpacAnnotation` nodes linked from that
   component with `annotationType != COMPONENT` have `needsReview` set to `true` (§5).
5. `meshHash` values on `:CpacComponent` nodes are updated to reflect the new GLB.

---

## 5. XPath-anchored annotations (`CpacAnnotation`)

A `CpacAnnotation` anchors to a CPACS element by XPath (via the `:CpacComponent` node)
and optionally to a geometric sub-region within that component's TiGL-generated mesh.

### 5.1 Neo4j model

```
(:CpacComponent {appId, uid, …})
  -[:HAS_ANNOTATION]->
(:CpacAnnotation {
  appId:           String
  label:           String
  cpacXpath:       String    // e.g. /cpacs/vehicles/aircraft/model/wings/wing[@uID='Wing1']
  componentUid:    String    // denormalised from the CpacComponent; fast lookup

  -- Geometry sub-region (optional; from CAD1e GeometryAnnotation shape):
  annotationType:  String    // enum: COMPONENT | POINT | REGION | SURFACE
                             //   COMPONENT = whole component, no sub-region
  worldPosition:   double[]  // [x, y, z]  — for POINT
  worldAabb:       double[]  // [xMin, yMin, zMin, xMax, yMax, zMax]  — for REGION
  faceIndices:     int[]     // within the component's glTF mesh  — for SURFACE

  -- Semantic:
  semanticTermIri: String
  aiGenerated:     boolean
  confidence:      double

  -- Design-iteration tracking:
  cpacVersion:     String    // CPACS header/version at annotation time
  needsReview:     boolean   // true when mesh changed after annotation (§4.4)

  createdAt:       DateTime
  createdBy:       String    // userAppId
})
```

### 5.2 Why XPath is more stable than face indices

The `uID` in CPACS is a designer-controlled stable identifier. A wing section redesign
changes TiGL's tessellation (face indices shift), but the `uID` remains. Annotating at the
`uID` / XPath level means:

- Annotation survives moderate redesigns without becoming invalid.
- `needsReview = true` signals only when the geometry actually changed — not on every
  non-geometric CPACS parameter update.
- The XPath is human-readable and interpretable outside shepard.

`faceIndices` (for SURFACE annotations) are still stored because some annotations do need
sub-component face precision. `needsReview` is set for these when the mesh changes, because
the indices may no longer correspond to the intended surface patch.

### 5.3 REST surface

```
/v2/cpac-references/{refAppId}/components/{componentAppId}/annotations
```

| Method | Path | Purpose |
|---|---|---|
| `GET` | `.../annotations` | List annotations on a component; filter by `needsReview`, `annotationType`, `semanticTermIri` |
| `POST` | `.../annotations` | Create annotation |
| `GET` | `.../annotations/{annotationAppId}` | Single annotation |
| `PATCH` | `.../annotations/{annotationAppId}` | Update label, semanticTermIri, sub-region; clears `needsReview` |
| `DELETE` | `.../annotations/{annotationAppId}` | Delete |

Also: `GET /v2/cpac-references/{refAppId}/annotations` — all annotations across all
components of a reference (useful for export / full review list).

### 5.4 `needsReview` lifecycle

```
annotation created (needsReview = false)
      │
      │  new CPACS XML uploaded; TiGL re-runs;
      │  component meshHash changed
      ▼
needsReview = true
      │
      │  researcher reviews annotation in viewer;
      │  PATCH annotation (confirm still correct, or update sub-region)
      ▼
needsReview = false
```

---

## 6. Non-geometry CPACS data

CPACS carries structured data beyond geometry: aerodynamics tables, mass breakdowns,
performance data, structural analysis inputs. These are currently buried in the raw XML blob
when stored naively.

### 6.1 XPath query endpoint

```
GET /v2/cpac-references/{appId}/xpath?path=<xpath>
```

Returns the value at the given CPACS XPath as JSON. Read-only. Gated on
`:CpacConfig.allowArbitraryXpath` (default `false`; admin-enabled for trusted deployments).

Example:
```
GET /v2/cpac-references/019x.../xpath?path=/cpacs/vehicles/aircraft/model/analyses/aeroPerformance/polar[@uID='CruisePolar']/dcL_dAlpha
→ { "xpath": "…", "value": "0.112", "unit": "1/deg" }
```

### 6.2 Structured sub-resource detection

When the plugin ingests a CPACS file, it checks for the presence of known top-level CPACS
sections and surfaces them as structured sub-resources when found:

| CPACS section XPath prefix | Sub-resource endpoint |
|---|---|
| `/cpacs/vehicles/aircraft/model/analyses/aeroPerformance` | `GET /v2/cpac-references/{appId}/aero` |
| `/cpacs/vehicles/aircraft/model/analyses/massBreakdown` | `GET /v2/cpac-references/{appId}/mass` |

These endpoints return a structured JSON projection of the CPACS section — not raw XML —
suitable for display in a researcher dashboard widget or programmatic consumption by an
analysis tool. The projection schema is defined by the plugin and documented in the plugin's
own `docs/reference/` pages when it ships.

---

## 7. RCE workflow integration

CPACS is the dominant data type exchanged between RCE components in aircraft MDO workflows.
This section specifies the provenance integration when `shepard-plugin-rce` is present
(see `aidocs/80`).

### 7.1 Provenance chain

When a RCE component consumes a CPACS file and outputs a modified CPACS file:

1. The input CPACS file is an existing `CpacReference` in shepard (previously ingested or
   created by an earlier RCE component in the same workflow run).
2. The RCE shepard integration (`aidocs/80` RCE1b) creates a new `CpacReference` DataObject
   for the output CPACS file.
3. The plugin creates a provenance edge:

```
(:CpacReference {appId: "output-cpac"})
  -[:DERIVED_FROM {
      rceComponentName: "AeroPanelUpdate",
      rceWorkflowRunId: "...",
      derivedAt: <ISO8601>
  }]->
(:CpacReference {appId: "input-cpac"})
```

4. Over a multi-discipline workflow, the shepard provenance graph shows the full CPACS
   evolution chain — for example: aero panel updates aerodynamics tables → structures reads
   updated geometry → mass panel reads structural results → performance analysis reads mass.

### 7.2 Component-level diff (future)

A future `CPACS1f+` phase could diff the component tree between the input and output CPACS
files and record which components were modified as metadata on the `DERIVED_FROM` edge. This
would make the provenance graph queryable at the component level: "which RCE component last
modified the fuselage skin thickness?"

---

## 8. Phasing

| Phase | ID | Deliverable | Size | Gate |
|---|---|---|---|---|
| 1 | CPACS1a | `CpacReference` PayloadKind entity + S3 raw file storage + plugin skeleton + `:CpacConfig` + admin GET/PATCH/health + TiGL sidecar Docker image (minimal, `POST /convert` stub returning a dummy GLB) | M | FS1a (file storage SPI) |
| 2 | CPACS1b | TiGL → GLB conversion pipeline in sidecar (real `tigl3` + `pythonocc`); derived GLB cache on S3; `GET /derived.glb` + `GET /component-tree`; `:CpacComponent` Neo4j nodes + graph ingestion; component list/detail REST | L | CPACS1a |
| 3 | CPACS1c | Frontend: CPACS component tree panel in the sidebar of the Three.js viewer (reuses CAD1b viewer); clicking a component highlights the corresponding glTF node; opens annotation form | M | CAD1b + CPACS1b |
| 4 | CPACS1d | `CpacAnnotation` Neo4j model + full annotation REST CRUD + POINT/REGION/SURFACE sub-annotation + `needsReview` lifecycle (mesh change detection on upload) | M | CPACS1c |
| 5 | CPACS1e | Non-geometry CPACS data — XPath query endpoint (`allowArbitraryXpath` gate) + aero/mass structured sub-resource detection and projection | M | CPACS1b |
| 6 | CPACS1f | RCE → CPACS provenance chain: `DERIVED_FROM` edges created by `aidocs/80` RCE1b; provenance viewer shows CPACS evolution | M | RCE1b (aidocs/80) |

**CPACS1b is the gating deliverable.** Without the TiGL pipeline, the frontend has nothing
to show. CPACS1c–CPACS1d are the annotation payoff. CPACS1e and CPACS1f are independent and
can be deferred.

---

## 9. Deployment

The TiGL sidecar is required when the plugin is enabled. Add to the operator's
`docker-compose.yml`:

```yaml
services:
  tigl-sidecar:
    image: ghcr.io/dlr-shepard/shepard-tigl-sidecar:latest
    # No persistent volume needed — stateless; CPACS XML in, GLB out
    mem_limit: 2g   # TiGL conversion can be memory-intensive for complex aircraft
    restart: unless-stopped

  shepard-backend:
    environment:
      - SHEPARD_PLUGINS_CPACS_ENABLED=true
      - SHEPARD_CPACS_TIGL_SIDECAR_URL=http://tigl-sidecar:8080
```

Or via admin CLI after deployment:

```
shepard-admin cpacs set-tigl-url http://tigl-sidecar:8080
shepard-admin cpacs enable
shepard-admin cpacs health
```

The sidecar image is published alongside the main shepard images on each release. Its
`Dockerfile` lives in `shepard-plugin-cpacs/tigl-sidecar/`.

---

## 10. See also

- `aidocs/78-cad-annotator.md` — 3D Geometry & FEM Annotator (shepard-plugin-cad);
  provides `CadReference`, CAD1b Three.js viewer, and `GeometryAnnotation` shape that
  CPACS1c–d reuse
- `aidocs/integrations/80-rce-integration.md` — RCE workflow integration (CPACS provenance chain in §7
  requires RCE1b)
- `aidocs/platform/47-dev-experience-and-plugin-system.md` — PayloadKind / PayloadStorage SPI that
  `CpacReference` follows
- `aidocs/30-provenance-and-lineage.md` — provenance and lineage design (`DERIVED_FROM`
  edges in §7)
- https://dlr-sl.github.io/cpacs-website/ — CPACS specification and schema documentation
- https://github.com/DLR-SC/tigl — TiGL geometry library (source, Python bindings, examples)
