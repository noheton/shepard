# aidocs/81 — Spatial Data Binding: Linking Geometry to Measurements

**Date:** 2026-05-16
**Status:** Design
**Audience:** Contributors; DLR manufacturing/robotics researchers.
**Purpose:** Specify the DataBinding extension to `GeometryAnnotation` — the mechanism
that links spatial locations on 3D geometry to shepard data objects (timeseries, structured
data, images), turning shepard into a digital twin substrate.

Couples to: `aidocs/78` (3D Geometry & FEM Annotator — `GeometryAnnotation` foundation),
`aidocs/30` (provenance and lineage), `aidocs/47` (PayloadKind / PayloadStorage SPI),
`aidocs/40 §3` (shepard-timeseries-collector, live SSE source).

---

### §1 Motivation: the missing link between where and what

Research instruments are physically located somewhere. A thermocouple is welded to a
specific spot on the MFFD upper shell. A DIC camera covers a specific surface patch on a
tensile specimen. A robot end-effector position is the "where" for every process parameter
sampled at that instant. Today these two facts — location and measurement — live in separate
systems with only informal connections (lab notebook entries, filenames like
`TC_upper_left_welding_run_042.csv`).

The DataBinding model makes this a first-class machine-readable relationship:
`(geometry annotation) --[:HAS_DATA_BINDING]--> (data binding config) --[:BOUND_TO]-->
(timeseries or other Reference)`. This is what turns shepard from a data repository into a
digital twin substrate.

---

### §2 DataBinding model

New Neo4j node `:DataBinding` linked from `:GeometryAnnotation` via `[:HAS_DATA_BINDING]`.
There can be multiple bindings per annotation (e.g. a single sensor mount point measuring
both temperature and pressure):

```cypher
(:GeometryAnnotation)
  -[:HAS_DATA_BINDING]->
(:DataBinding {
  appId           String   -- HasAppId
  channelName     String   -- timeseries channel name, structured-data field, or image layer name
  displayMode     String   -- enum: BADGE | HEATMAP | GLYPH | JOINT_ANGLE | DEFORMATION
  colorMap        String   -- "viridis", "plasma", "hot", "RdBu", "coolwarm"
  minValue        double   -- display clamp range
  maxValue        double
  unit            String   -- shown in badge / tooltip
  liveEnabled     boolean  -- if true, subscribe to SSE feed for real-time updates
})
  -[:BOUND_TO]->
(:TimeseriesReference | :StructuredDataReference | :FileBundleReference | :SingletonFileReference)
```

**REST surface for DataBinding CRUD:**

All paths require at minimum Reader permission on the parent DataObject; POST/PATCH/DELETE
require Writer. The `refAppId` and `annotationAppId` path parameters identify the parent
`CadReference` and `GeometryAnnotation` respectively.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings` | List all DataBindings on an annotation |
| `POST` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings` | Create a DataBinding |
| `GET` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings/{bindingAppId}` | Get single DataBinding |
| `PATCH` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings/{bindingAppId}` | RFC 7396 merge-patch (any writable field) |
| `DELETE` | `/v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings/{bindingAppId}` | Delete DataBinding |

`POST` body example:

```json
{
  "channelName": "temperature_ch1",
  "displayMode": "BADGE",
  "unit": "°C",
  "liveEnabled": true,
  "boundToAppId": "<appId of the target TimeseriesReference>"
}
```

The `boundToAppId` resolves to the `[:BOUND_TO]` edge target. The reference type
(`TimeseriesReference`, `StructuredDataReference`, `FileBundleReference`,
`SingletonFileReference`) is inferred from the entity at `boundToAppId`.

---

### §3 Display modes

Five display modes in the Three.js / vtk.js annotator frontend:

| Mode | Visual effect | Typical use at ZLP |
|---|---|---|
| **BADGE** | Floating label next to annotation point, value + unit, updates live via SSE | Temperature thermocouple, pressure sensor, force value |
| **HEATMAP** | Face colour on a bound SURFACE annotation, `colorMap` mapped to `[minValue, maxValue]` | Thermal imaging (thermography sequence over specimen), strain field from DIC |
| **GLYPH** | Arrow/cone at annotation point; magnitude from one channel, direction from `(x,y,z)` channel triple | Force vector at robot TCP, acoustic emission source intensity, flow velocity |
| **JOINT_ANGLE** | `robot.joints[channelName].setAngle(value)` in urdf-loaders — drives kinematic transform directly | Robot arm replay from AFP trajectory or component test timeseries |
| **DEFORMATION** | Per-node mesh displacement from `(dx,dy,dz)` channel triple; FEM animated deformation | Structural response from DIC full-field measurement, FEM simulation result |

**Renderer path per mode:**

- `BADGE`, `GLYPH`, `JOINT_ANGLE` — Three.js path (operates on glTF / URDF geometry).
- `HEATMAP` — Three.js path for surface-colour mapping on glTF faces; vtk.js path for FEM
  scalar fields on VTU elements.
- `DEFORMATION` — vtk.js path (per-node displacement; requires VTU mesh with addressable
  node IDs).

---

### §4 Time scrubber and live mode

**Historical playback:** A time-scrubber slider component (Vue 3) spans the temporal range
of the bound timeseries. Dragging it fires
`GET /v2/timeseries-references/{appId}/data?t={epoch_ns}&channels={ch}` for each binding.
All bindings in the scene update simultaneously — the geometry, its sensor badges, and any
heatmaps or glyphs.

**Live mode:** When `liveEnabled = true` and the timeseries is being actively written (by
`shepard-timeseries-collector` or the resistance welding SCADA), the frontend connects an
SSE stream to `GET /v2/timeseries-references/{appId}/stream` (or the existing SSE
endpoint). Each new sample updates the relevant visual element.

**Robot replay (JOINT_ANGLE mode):** The time scrubber becomes a motion replay controller.
Every joint in the URDF has a binding to a channel; scrubbing replays the robot's recorded
motion. For the AFP Multifunctional Robot Cell, this lets researchers replay a layup
trajectory over the as-built part geometry.

---

### §5 Reverse lookup: from data to geometry

A critical query: "I have this timeseries — where was the sensor mounted?"

New endpoint: `GET /v2/timeseries-references/{appId}/spatial-bindings`

Returns a list of `{geometryAnnotation, cadReference, dataBinding}` triples, each with the
annotation's world-space coordinates (from `GeometryAnnotation.worldPosition`), the
`CadReference` the annotation belongs to, and the full binding config. This closes the
traceability loop from measurement → physical location, without the researcher having to
remember filename conventions.

The same pattern applies to the other reference types:

| Endpoint | Source reference type |
|---|---|
| `GET /v2/timeseries-references/{appId}/spatial-bindings` | `TimeseriesReference` |
| `GET /v2/structured-data-references/{appId}/spatial-bindings` | `StructuredDataReference` |
| `GET /v2/file-bundle-references/{appId}/spatial-bindings` | `FileBundleReference` |

Response shape (one element per binding):

```json
{
  "items": [
    {
      "cadReference": { "appId": "...", "label": "..." },
      "geometryAnnotation": {
        "appId": "...",
        "label": "thermocouple TC-01",
        "annotationType": "POINT",
        "worldPosition": [1.23, 0.45, 0.78]
      },
      "dataBinding": {
        "appId": "...",
        "channelName": "temperature_ch1",
        "displayMode": "BADGE",
        "unit": "°C",
        "liveEnabled": true
      }
    }
  ]
}
```

---

### §6 ZLP-specific relationship patterns

This section documents the data relationship chains that emerge specifically from ZLP
Augsburg's manufacturing research experiments. Each chain is a concrete instance of the
DataBinding + provenance graph model.

#### 6.1 AFP (Automated Fibre Placement) chain

A layup campaign produces several linked data types:

```
CadReference (nominal layup geometry / CPACS)
  └─► DataObject "AFP Run #42"
        ├─ TimeseriesReference: robot joint angles (6 DOF × time)  --JOINT_ANGLE binding--> robot URDF
        ├─ TimeseriesReference: layup speed, compaction force, temperature at roller
        │    └─ HEATMAP binding: temperature mapped to the ply surface
        ├─ FileBundleReference: per-ply inspection images (one image per ply, named by ply index)
        │    └─ HEATMAP binding: defect-density map on ply surface
        └─ DataObject "AFP Run #42 — Post-cure C-scan"
              └─ SingletonFileReference: ultrasonic C-scan TIFF
                   └─ HEATMAP binding: signal amplitude on part upper surface
```

The `[:DERIVED_FROM]` edge from the C-scan DataObject to the AFP run DataObject is the
process→quality link. The joint-angle timeseries, bound to the robot URDF annotation,
replays the actual layup motion over the nominal geometry.

#### 6.2 Resistance welding chain (MFFD)

```
CadReference (MFFD upper shell geometry)
  └─ GeometryAnnotation: weld line (SURFACE type covering the weld zone)
       └─ DataBinding: HEATMAP → TimeseriesReference: thermocouple array temperature [°C]
       └─ DataBinding: BADGE → TimeseriesReference: weld power [W]
  └─► DataObject "Resistance Weld Run #7"
        ├─ TimeseriesReference: current [A], voltage [V], power [W], actuator force [N]
        ├─ TimeseriesReference: thermocouple array (8 channels × time)
        └─ DataObject "Weld Run #7 — Quality"
              ├─ SingletonFileReference: C-scan image of weld zone
              └─ StructuredDataReference: tensile shear strength test result {load [N], area [mm²], strength [MPa]}
```

#### 6.3 DIC (Digital Image Correlation) full-field strain

```
CadReference (specimen geometry)
  └─ GeometryAnnotation: specimen gauge section (SURFACE)
       └─ DataBinding: HEATMAP (DEFORMATION mode) → FileBundleReference: DIC sequence
            channelName: "von_mises_strain", colorMap: "hot", minValue: 0.0, maxValue: 0.02
  └─► DataObject "Tensile Test #12"
        ├─ TimeseriesReference: load cell force [N], crosshead displacement [mm]
        ├─ FileBundleReference: DIC image sequence (stereo camera frames)
        └─ StructuredDataReference: {E_modulus, tensile_strength, fracture_strain}
```

#### 6.4 Acoustic emission localization

AE sensors are placed at known spatial locations on the specimen. AE source localization
(triangulation from arrival times) maps each AE event to an (x,y,z) coordinate.

```
CadReference (specimen geometry)
  └─ GeometryAnnotation: AE sensor #1 position (POINT) at world-space [x,y,z]
       └─ DataBinding: BADGE → TimeseriesReference: AE channel 1 (amplitude, frequency, rise time)
  └─ GeometryAnnotation: AE sensor #2 position (POINT) …
       └─ DataBinding: BADGE → TimeseriesReference: AE channel 2
  └─ DataObject "Fatigue Test + AE Monitoring"
        ├─ TimeseriesReference: AE events (timestamp, amplitude, frequency, source_x, source_y, source_z)
        └─ TimeseriesReference: load cycle (force [N], cycle count)
```

AE source coordinates from the timeseries become dynamic `GLYPH` annotations on the
geometry — a dot appears at each localised crack event as you scrub through time.

#### 6.5 Robot component testing (from ETFA 2022 Uni Augsburg)

```
CadReference (component geometry)
  └─ GeometryAnnotation: contact point (POINT on component surface)
       └─ DataBinding: GLYPH → TimeseriesReference: 6-DOF F/T sensor (Fx,Fy,Fz,Tx,Ty,Tz)
  └─ DataObject "Robot Test Run #3 — Component Loading"
        ├─ TimeseriesReference: 6-DOF F/T at TCP
        ├─ TimeseriesReference: robot joint angles (6 DOF)  --JOINT_ANGLE--> robot URDF
        └─ StructuredDataReference: test protocol {material, layup, temperature, humidity}
```

---

### §7 Semantic relationship types beyond DERIVED_FROM

Standard `[:DERIVED_FROM]` expresses data lineage (output came from input). ZLP's
experiments reveal additional semantic relationship types that should be first-class Neo4j
edge labels:

| Relationship | From → To | Semantics |
|---|---|---|
| `[:CHARACTERISES]` | NDT/test DataObject → manufactured component DataObject | The quality measurement characterises the component |
| `[:VALIDATES]` | Experimental DataObject → simulation DataObject | Measurement validates the simulation prediction |
| `[:INSPECTS]` | Inspection DataObject → process step DataObject | Post-process inspection inspects a specific manufacturing step |
| `[:PRODUCED_BY]` | Specimen DataObject → manufacturing run DataObject | Specimen was produced by this process run |
| `[:COMPANION_TO]` | DataObject A → DataObject B | Simultaneous co-measurements (DIC + load cell + AE in same test) |
| `[:LOCATES_SENSOR]` | GeometryAnnotation → TimeseriesReference | Annotation spatially locates the sensor that produced this timeseries |

These ship as typed Neo4j relationship labels. REST surface:

- `POST /v2/dataobjects/{fromId}/relationships` — body `{"type": "CHARACTERISES", "toAppId": "..."}`
- `GET /v2/dataobjects/{appId}/relationships?type=CHARACTERISES`

The `[:LOCATES_SENSOR]` edge is a convenience shortcut: it is equivalent to traversing
`[:HAS_DATA_BINDING]` → `[:BOUND_TO]` but surfaces the relationship at the
`GeometryAnnotation` level for SPARQL queries and provenance export. It is materialised as
a Neo4j edge at `POST /v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings`
time whenever the `[:BOUND_TO]` target is a `TimeseriesReference`.

This is the design foundation; the full provenance graph from `aidocs/30` (OpenLineage +
PROV-O) remains the authoritative lineage layer — the semantic relationships above are the
engineering-research domain overlay.

---

### §8 Phasing (SB1a–SB1f)

| ID | Deliverable | Size | Gate |
|---|---|---|---|
| **SB1a** | `:DataBinding` Neo4j entity + `[:HAS_DATA_BINDING]` + `[:BOUND_TO]` edges; REST CRUD on `/v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings`; BADGE display mode only | M | CAD1b |
| **SB1b** | Reverse lookup endpoints: `GET /v2/timeseries-references/{appId}/spatial-bindings` and structured-data, file-bundle variants | S | SB1a |
| **SB1c** | HEATMAP display mode — surface face colour from bound timeseries sample; `colorMap` + `min/maxValue` config | M | CAD1e (surface annotation) |
| **SB1d** | JOINT_ANGLE mode — URDF joint animation from bound timeseries channel; time scrubber Vue 3 component | M | CAD1d (URDF bundles) |
| **SB1e** | GLYPH mode — arrow/vector from `(x,y,z)` channel triple; DEFORMATION mode — mesh node displacement | M | CAD1f (vtk.js + FEM) |
| **SB1f** | Semantic relationship types (`CHARACTERISES` / `VALIDATES` / `INSPECTS` / `PRODUCED_BY` / `COMPANION_TO` / `LOCATES_SENSOR`) — Neo4j edges + REST surface | M | independent |

**SB1a + SB1b** are the minimal viable DataBinding: a sensor location on a geometry is
linked to a timeseries, browsable in the annotator, and the reverse lookup ("where was
this sensor?") is a single REST call. **SB1c–SB1e** add progressive display richness gated
on the corresponding annotator capabilities from `aidocs/78`. **SB1f** is independent of
all display modes and can land in parallel with any of the above.

---

### §9 See also

- `aidocs/78-cad-geometry-annotator.md` — 3D Geometry & FEM Annotator (`shepard-plugin-cad`,
  foundation; defines `CadReference` and `GeometryAnnotation`)
- `aidocs/79-cpacs-annotator.md` — CPACS Annotator (CPACS→geometry binding for MDO
  provenance)
- `aidocs/80-rce-integration.md` — RCE Integration (RCE component execution → DataObject
  with DataBinding to weld-line geometry)
- `aidocs/30-provenance-and-lineage-design.md` — Provenance and lineage design
  (OpenLineage / PROV-O complement to semantic relationships)
- `aidocs/82` — ZLP Augsburg Stakeholder Brief (the concrete user of this design)
- `aidocs/40-ecosystem.md §3` — shepard-timeseries-collector (sTC, the live SSE source
  for real-time DataBinding updates)
