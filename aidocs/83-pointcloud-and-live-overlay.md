# aidocs/83 — Point Cloud Integration and Live Overlay Modalities

**Date:** 2026-05-16
**Status:** Design
**Audience:** Contributors; DLR manufacturing/robotics/NDT researchers.
**Purpose:** Extend `shepard-plugin-cad` (aidocs/78) with point cloud support and a
richer set of live-data overlay modalities for the 3D viewer. Supersedes the informal
"spatial would be also interesting" and "superposing live data" open questions.

Couples to: `aidocs/78` (CAD annotator — formats, renderer, scene graph), `aidocs/81`
(spatial data binding — DataBinding model, display modes, SSE live mode), `aidocs/53`
(video plugin — live camera feeds), `aidocs/82` (ZLP Augsburg data landscape).

---

### §1 Point clouds: why they belong in `shepard-plugin-cad`, not `SpatialDataReference`

The upstream `SpatialDataReference` (PostGIS-backed) is a **geographic/geospatial**
primitive — GPS tracks, floor-plan coordinates, lab-room layouts. Its native coordinate
system is WGS84/UTM. It is not designed for dense 3D scan data.

Point clouds from DLR research workflows are **engineering geometry**:

| Source | Format | Typical size | Primary use |
|---|---|---|---|
| Structured-light scanner (GOM ATOS, ZEISS T-SCAN) | PLY, E57, XYZ | 5–200 M pts | Dimensional inspection vs CAD nominal |
| Lidar (robot cell ceiling lidar, Velodyne) | LAS/LAZ | 1–50 M pts/s | Environment mapping, robot safety zones |
| Computed tomography (CT) | voxel → PLY reconstruction | 50 M+ pts | Internal defect localisation |
| Photogrammetry (agisoft metashape) | PLY/OBJ | 10–500 M pts | Large-area surface capture |
| Acoustic emission localisation | sparse XYZ event list | 10 k–1 M pts | Damage zone mapping on structure |

The central workflow is **CAD-to-scan alignment** (ICP or feature matching), which
requires both the CAD mesh and the point cloud to share a coordinate frame and a parent
Neo4j entity. Keeping them in the same plugin eliminates coordinate-frame translation
and lets the viewer render both in the same Three.js scene without a cross-plugin API.

#### PC1a — `PointCloudReference` payload kind

Registered as a second payload kind by `shepard-plugin-cad` (or, if the plugin becomes
large, a thin `shepard-plugin-pointcloud` that declares `cad` as a required dependency).

```
Accepted source formats (stored verbatim on S3):
  PLY, E57, LAS/LAZ, XYZ/TXT (whitespace-delimited)

Derived viewer format (generated once, cached in S3):
  Potree 1.8 octree — the standard for streaming large clouds in browsers
  Toolchain: py3dtiles or PDAL → Potree converter (server-side sidecar)

Streaming delivery: chunked Potree format tiles via
  GET /v2/pointcloud-references/{appId}/tiles/{path}
```

**Neo4j model:**

```cypher
(:PointCloudReference {
  appId          String
  sourceFormat   String   -- "PLY" | "E57" | "LAS" | "LAZ" | "XYZ"
  pointCount     long     -- populated after ingest
  boundingBoxMin [double] -- [xmin, ymin, zmin]
  boundingBoxMax [double] -- [xmax, ymax, zmax]
  coordinateFrame String  -- "world" | "part" | "sensor"
  tiledAt        ZonedDateTime
})
```

#### PC1b — CAD–Cloud alignment

When a `PointCloudReference` and a `CadReference` share the same DataObject, the viewer
offers an **Align** action.

Backend alignment flow:

1. Client sends `POST /v2/pointcloud-references/{pcAppId}/align-to/{cadAppId}` with
   optional `{ "algorithm": "ICP" | "RANSAC", "maxIterations": 100, "tolerance": 0.01 }`.
2. Backend dispatches to the Python sidecar (Open3D `o3d.pipelines.registration`).
3. Sidecar returns a 4×4 homogeneous transformation matrix.
4. Backend stores as a Neo4j relationship:
   ```cypher
   (:PointCloudReference)-[:ALIGNED_TO {
     matrix: [double × 16],    -- row-major 4×4
     rmse: double,             -- RMS point-to-plane distance after alignment
     alignedAt: ZonedDateTime,
     algorithm: String
   }]->(:CadReference)
   ```
5. The viewer applies the matrix client-side (`THREE.Matrix4`) when co-rendering.

#### PC1c — Deviation map

After alignment, `GET /v2/pointcloud-references/{pcAppId}/deviation-map` returns a
per-face signed-distance value for every triangle of the aligned CAD mesh. These values
are injected as a HEATMAP DataBinding (aidocs/81 §3) automatically, so the CAD surface
is coloured by deviation without the researcher having to manually create a binding.

Colour map defaults: `"RdBu"` centred at 0, ±3σ clamped, ±tolerance shown as
dashed isoline. The researcher can adjust in the binding's PATCH endpoint.

#### PC1d — Live point cloud streaming (scanner-connected mode)

When a scanner exposes a streaming API (GOM ATOS via its REST SDK, or a Velodyne UDP
listener via a thin adapter), the `sTC` collector (aidocs/40 §3) can ingest in real
time:

```
sTC --plugin pointcloud-live \
    --source velodyne:192.168.1.77:2368 \
    --target shepard://pointcloud-references/{appId}/chunks
```

The viewer subscribes to the SSE stream at
`GET /v2/pointcloud-references/{appId}/live-chunks` and appends new point batches to
the Three.js PointsMaterial scene without a full reload. Chunk format: binary LAS 1.4
frames, ~1 Hz flush rate.

---

### §2 Live overlay modalities (extending SB1)

SB1 (aidocs/81) defines five display modes (BADGE, HEATMAP, GLYPH, JOINT_ANGLE,
DEFORMATION) driven by stored timeseries or structured-data snapshots, with a time
scrubber and SSE live mode. This section catalogues additional modalities that benefit
from the same binding infrastructure.

#### SB2a — Camera frustum + PiP video overlay

**Use case:** A robot arm carries a thermographic or optical camera. The camera's pose
is known from the URDF kinematic chain + the joint-angle timeseries. Display the
camera's field of view as a `THREE.CameraHelper` frustum anchored to the end-effector
link. Render a PiP (picture-in-picture) panel in the corner of the 3D viewport showing
the synchronized live feed from the corresponding `VideoReference` (aidocs/53 VID1).

**Binding shape extension:**

```cypher
(:DataBinding {
  displayMode: "CAMERA_FRUSTUM",
  videoRefAppId: String,   -- links to a VideoReference for PiP
  fovDeg: double,
  aspectRatio: double,
  nearClip: double,
  farClip: double
})
```

The frustum geometry is derived from the URDF end-effector link annotation (JOINT_ANGLE
binding) and the camera intrinsics. Time-synchronization: the PiP frame is the video
frame whose timestamp is closest to the current scrubber position (±100 ms).

**Implementation note:** Requires VID1 (aidocs/53) for video storage + HLS delivery;
gated on VID1a + CAD1b (URDF kinematic chain).

#### SB2b — Acoustic emission event cloud

**Use case:** AE localisation systems (Physical Acoustics, Vallen) produce
`(x, y, z, time_us, energy_aJ, channel_id)` event lists. Display as animated
THREE.Points glyphs anchored to the specimen geometry, colour-coded by energy, with
temporal clustering by `eps_t = 1 ms` buckets on the time scrubber.

**Binding shape:**

```cypher
(:DataBinding {
  displayMode: "AE_EVENT_CLOUD",
  energyChannel: String,   -- field name in the StructuredDataReference
  xChannel: String,
  yChannel: String,
  zChannel: String,
  timeChannel: String,
  clusterEpsMs: double,    -- temporal bucket width for animation
  glyphScale: double       -- glyph size ∝ energy^glyphScale
})
```

No coordinate-frame issue when the AE sensor array positions are themselves stored as
`GeometryAnnotation` entries on the same specimen `CadReference` (using the
`[:LOCATES_SENSOR]` edge from aidocs/81 §7).

#### SB2c — Thermography / DIC raster projection

**Use case:** A 2D thermal image (from an IR camera) or DIC displacement field image
(from ARAMIS/ISTRA) is projected onto the 3D surface of the specimen using the
camera's calibration data (intrinsic matrix K + extrinsic [R|t]).

**Binding shape:**

```cypher
(:DataBinding {
  displayMode: "RASTER_PROJECTION",
  imageRefAppId: String,    -- links to an image FileReference
  calibrationJson: String,  -- JSON: {"K": [[...]], "R": [[...]], "t": [...]}
  colorMap: String,
  minValue: double,
  maxValue: double
})
```

Client-side: Three.js `TextureLoader` fetches the image; the calibration matrix is used
to build a `THREE.Matrix4` projection that maps the image texture onto the mesh UVs.
For DIC images the calibration comes from the DIC system's export; for thermography
from the camera's NUC file.

This is the most complex renderer; ship after HEATMAP (SB1a) is stable. Gated on
PC1b (alignment) when the camera is not intrinsically registered to the part frame.

#### SB2d — Multi-channel synchronised playback

**Use case:** Play back temperature, force, acoustic emission, and camera feed
simultaneously on the same timeline. The time scrubber drives all bound channels at
once; channels with different sample rates are linearly interpolated to the scrubber
position.

This is primarily a **frontend concern** — the DataBinding model already supports
multiple bindings per `GeometryAnnotation`, and each binding independently resolves
its `[:BOUND_TO]` Reference at query time. The frontend's time scrubber broadcasts
the current time to all active binding renderers.

The one backend addition: `GET /v2/cad-references/{appId}/bindings/timeline` returns
the union time range `[earliest sample, latest sample]` across all DataBindings on all
annotations of that `CadReference`, so the frontend can render a correct scrubber range
without querying each binding separately.

#### SB2e — Nominal vs actual trajectory comparison

**Use case:** AFP or welding robot has a planned trajectory (from the experiment recipe,
stored as a `StructuredDataReference` with joint-angle columns) and an actual recorded
trajectory (from sTC ingest, stored as a `TimeseriesReference`). Display both in the
3D scene simultaneously as two JOINT_ANGLE animations: planned in blue, actual in red.
Deviation per joint per timestep rendered as a BADGE panel.

**Binding shape extension:**

```cypher
-- two separate DataBinding nodes, both JOINT_ANGLE mode:
(:DataBinding { displayMode: "JOINT_ANGLE", role: "NOMINAL", colorTint: "#4a90d9" })
  -[:BOUND_TO]-> (:StructuredDataReference)   -- recipe / planned

(:DataBinding { displayMode: "JOINT_ANGLE", role: "ACTUAL", colorTint: "#d94a4a" })
  -[:BOUND_TO]-> (:TimeseriesReference)        -- sTC recorded

-- deviation binding (auto-computed by backend when both role-typed bindings exist):
(:DataBinding { displayMode: "BADGE", role: "DEVIATION", unit: "deg" })
```

The backend auto-generates the DEVIATION binding when `NOMINAL` and `ACTUAL` bindings
coexist on the same `GeometryAnnotation` (during `POST .../bindings` processing).

#### SB2f — FEM solution animation

**Use case:** Step through frames of a FEM simulation result (crash timesteps, modal
shapes, thermal distribution over time). Each frame is a structured-data row with
per-node displacement or temperature values.

This is a special case of DEFORMATION mode (SB1, aidocs/81 §3.5) with a multi-frame
structured-data source. The time scrubber drives the frame index rather than wall-clock
time. The backend returns the frame range via the timeline endpoint (SB2d).

No new binding shape needed — DEFORMATION mode with a multi-frame `StructuredDataReference`
already covers this. The addition is the frontend frame-index playback mode vs the
wall-clock interpolation mode.

#### SB2g — Ultrasonic inspection (A-scan + position)

**Use case:** Phased-array ultrasound (PAUT) and conventional UT systems produce
**A-scan signals** — time-domain waveforms of ultrasound amplitude vs depth — at each
encoder-registered (x, y) surface position. The full dataset for one inspection pass is:

```
A-scan:     amplitude(t)           at each position (x, y, step_index)
B-scan:     amplitude(depth, x)    cross-section slice
C-scan:     max_amplitude or ToF   projected onto the part surface (the "map")
Position:   (x, y, step_index, timestamp)  from encoder or robot joint angles
```

**Data spec is to be defined** (placeholder pending spec delivery). The design below
gives the architectural wiring; field names and format details will be filled in once
the spec is known.

**Storage model (provisional):**

```cypher
(:UltrasonicScanReference {
  appId        String
  systemMake   String    -- "Olympus OmniScan" | "GE Krautkramer" | "custom" | …
  frequency    double    -- transducer centre frequency in MHz
  scanType     String    -- "PAUT" | "UT" | "TOFD"
  positionRef  String    -- appId of a TimeseriesReference or StructuredDataReference
                          -- that holds (x, y, step_index, timestamp) encoder data
  ascanRef     String    -- appId of HDF5Reference (or StructuredDataReference) holding
                          -- the raw A-scan array [n_positions × n_time_samples]
  cscanRef     String    -- appId of a FileReference (PNG/TIFF) for the C-scan image,
                          -- OR a StructuredDataReference for the numeric C-scan array
})
```

`UltrasonicScanReference` registers as a payload kind in `shepard-plugin-cad` (or
`shepard-plugin-ultrasound` if the scope justifies a separate plugin).

**Viewer binding (new display mode: `UT_CSCAN`):**

```cypher
(:DataBinding {
  displayMode:     "UT_CSCAN",
  scanRefAppId:    String,    -- links to UltrasonicScanReference
  channel:         String,    -- "AMPLITUDE" | "TOF" | "PHASE"
  colorMap:        String,    -- "hot", "viridis", "Greys" — defects typically hot/red
  gatingStart:     double,    -- time gate start (µs) for A-scan gating
  gatingEnd:       double,    -- time gate end (µs)
  defectThreshold: double     -- amplitude threshold for defect highlight overlay
})
```

Client-side rendering: the C-scan is projected onto the part surface as a HEATMAP
texture (same UV-mapping path as SB2c RASTER_PROJECTION). Position registration uses
the `[:ALIGNED_TO]` edge when the scan was done in a scanner-specific frame that
differs from the CAD frame.

**Interactive A-scan panel:** clicking a surface point in the 3D viewer sends a
`GET /v2/ultrasonic-scan-references/{appId}/ascan?x={}&y={}` request that returns the
raw A-scan waveform at the nearest position point. The frontend renders it as a
time-domain line chart in a sidebar panel alongside the 3D view. This gives the NDT
engineer direct access to the raw signal at any inspection location without leaving
shepard.

**Live scanning mode:** when connected to a PAUT system via a streaming adapter
(similar to PC1d live point cloud), the C-scan HEATMAP updates as the scan head
traverses the part. Position data comes from the encoder SSE feed; A-scan blocks are
batched by the sTC adapter. Useful for monitoring a scan in progress rather than
post-hoc analysis.

**Phasing:** SB2g is gated on SB1b (HEATMAP) + PC1b (coordinate alignment). Data spec
delivery is the prerequisite; once the spec is known, the `UltrasonicScanReference`
Neo4j model and ingest pipeline can be finalised. Provisional priority: **high** for
ZLP Augsburg (C-scan is the primary NDT technique for CFRP parts).

---

### §3 Unified viewer capabilities matrix

| Capability | Mode token | Source type | Live capable | Notes |
|---|---|---|---|---|
| Scalar label on annotation | BADGE | Timeseries / Structured | ✓ | Already in SB1 |
| Surface colour map | HEATMAP | Timeseries / Structured | ✓ | Already in SB1 |
| Vector arrow/cone | GLYPH | Timeseries / Structured | ✓ | Already in SB1 |
| URDF joint animation | JOINT_ANGLE | Timeseries | ✓ | Already in SB1 |
| FEM node displacement | DEFORMATION | Structured (multi-frame) | — | Already in SB1 |
| Dense point cloud | (PC1a–PC1d) | PointCloudReference | ✓ (PC1d) | New — §1 |
| Deviation heat map (CAD vs cloud) | HEATMAP auto | PC + CAD | — | New — §1 PC1c |
| Camera frustum + PiP video | CAMERA_FRUSTUM | VideoReference + URDF | ✓ | New — SB2a |
| AE event cloud | AE_EVENT_CLOUD | Structured | ✓ | New — SB2b |
| Thermal / DIC raster projection | RASTER_PROJECTION | Image file + calibration | — | New — SB2c |
| Nominal vs actual trajectory | JOINT_ANGLE (dual) | Recipe + Timeseries | partial | New — SB2e |
| FEM frame animation | DEFORMATION (framed) | Structured (multi-frame) | — | Clarified — SB2f |
| Ultrasonic C-scan projection | UT_CSCAN | UltrasonicScanReference | ✓ (live) | New — SB2g; spec pending |
| Interactive A-scan panel | (UT_CSCAN sidebar) | UltrasonicScanReference | — | New — SB2g sidebar |
| Multi-channel timeline | (SB2d infrastructure) | Any mix | — | Frontend + timeline endpoint |

---

### §4 Phasing

| ID | Scope | Gated on | Priority |
|---|---|---|---|
| PC1a | `PointCloudReference` payload kind; PLY/E57 ingest; Potree tile serving | CAD1a (FS1a, plugin scaffolding) | High — enables dimensional inspection workflow |
| PC1b | CAD–Cloud ICP alignment; `[:ALIGNED_TO]` edge; Open3D sidecar | CAD1b (annotation model) + PC1a | High |
| PC1c | Deviation map auto-HEATMAP binding | PC1b + SB1b (HEATMAP mode) | Medium |
| PC1d | Live point cloud streaming (sTC + SSE tiles) | PC1a + SB1e (SSE live) | Low — needs scanner API adapter |
| SB2a | Camera frustum glyph + PiP video overlay | JOINT_ANGLE (SB1d) + VID1a | Medium |
| SB2b | AE event cloud glyph | SB1c (GLYPH) + PC1a (scene graph) | Medium — high value for ZLP NDT |
| SB2c | Raster projection (thermography / DIC) | SB1b (HEATMAP) + PC1b (alignment) | Low — complex renderer |
| SB2d | Multi-channel timeline endpoint | SB1a (BADGE first) | Medium — frontend-driven |
| SB2e | Nominal vs actual trajectory | JOINT_ANGLE (SB1d) | High — ZLP AFP robot use case |
| SB2f | FEM frame animation (multi-frame DEFORMATION) | DEFORMATION (SB1e) | Medium |
| SB2g | Ultrasonic C-scan overlay + interactive A-scan panel | SB1b (HEATMAP) + PC1b (alignment) | High — ZLP NDT primary technique; spec pending |

---

### §5 See also

- `aidocs/78` — 3D Geometry & FEM Annotator (`CadReference`, `GeometryAnnotation`,
  format matrix, scene graph export)
- `aidocs/81` — Spatial Data Binding (SB1 DataBinding model, five base display modes)
- `aidocs/82` — ZLP Augsburg stakeholder brief (dimensional inspection workflow §2.4,
  AE localisation §2.3)
- `aidocs/53` — Video plugin (`VideoReference`, HLS delivery — SB2a dependency)
