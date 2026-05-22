---
stage: feature-defined
last-stage-change: 2026-05-23
---

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

#### PC1e — Surface-annotation matching (measurement attribution)

**The core inspection workflow:** After ICP alignment (PC1b), every measured point in the
T-Scan cloud has a nominal CAD surface position. The next question is *which annotated
surface region* does that point belong to? A ply overlap zone, a spar cap, a fastener-hole
boundary? Without this attribution, the deviation map is a single undifferentiated heat
map — the engineer cannot say "the overlap zone is within tolerance but the leading edge
is not."

Surface-annotation matching makes deviation inspection feature-aware:

```
POST /v2/pointcloud-references/{pcAppId}/surface-match
{
  "cadRefAppId":       "abc123",
  "annotationAppIds":  ["ann-001", "ann-002"],  // optional; omit → all annotations
  "maxDistanceMm":     2.0                       // ignore points further than this from any face
}
```

**Backend computation:**

1. Load the aligned point cloud (applying the `[:ALIGNED_TO]` matrix).
2. Load the CAD mesh face list with face-index → annotation mapping
   (from `GeometryAnnotation.faceIndices` in aidocs/78 §6).
3. For each point, compute the nearest CAD face using an Open3D BVH or trimesh
   `proximity.closest_point`. Assign the point to the annotation that owns that face.
   Points with `dist > maxDistanceMm` are unattributed (noise or fixture).
4. Per annotation, compute: `n`, `mean`, `stddev`, `min`, `max` signed deviation
   (positive = outside nominal, negative = inside).
5. Store as a `:SurfaceMatchResult` node linked from the `PointCloudReference`:

```cypher
(:PointCloudReference)-[:HAS_SURFACE_MATCH]->(:SurfaceMatchResult {
  appId:           String
  cadRefAppId:     String
  computedAt:      ZonedDateTime
  totalPoints:     long
  attributedPoints: long
  maxDistanceMm:   double
})
  -[:MATCH_STATS]->(:AnnotationMatchStats {
  annotationAppId: String
  nPoints:         long
  meanMm:          double
  stddevMm:        double
  minMm:           double
  maxMm:           double
  inTolerancePct:  double   -- % of points within ±tolerance
  toleranceMm:     double   -- from annotation's toleranceMm field, or from request override
})
```

**REST surface:**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/pointcloud-references/{appId}/surface-match` | Run surface match; async job |
| `GET` | `/v2/pointcloud-references/{appId}/surface-match/{resultAppId}` | Get match result + per-annotation stats |
| `GET` | `/v2/pointcloud-references/{appId}/surface-match/{resultAppId}/points/{annotationAppId}` | Stream matched points for one annotation (binary LAS) |

**Viewer integration:** The match result automatically creates a HEATMAP `DataBinding` per
annotation with `minValue`/`maxValue` set to ±3σ and the annotation's `toleranceMm` shown
as a dashed isoline. Clicking an annotation in the 3D view opens a stats panel: pass/fail
badge, histogram of deviations, and the matched point subset rendered in the scene at
higher opacity than unmatched points.

**T-Scan specifics:** T-Scan produces very dense clouds (1–5 M pts for a mid-size CFRP
panel). The BVH lookup scales to this density on the Python sidecar without loading the
full cloud into memory — Open3D processes it in streaming chunks of 100k points. The
matched point subset per annotation (typically 5k–50k pts) is small enough to stream to
the browser directly.

**Tolerance annotation field:** `GeometryAnnotation` (aidocs/78 §6) gains an optional
`toleranceMm: double` field. This is the drawing tolerance for that feature (e.g., ±0.5 mm
for a leading edge, ±1.0 mm for a skin surface). PC1e uses it to compute `inTolerancePct`
and to set the HEATMAP isoline automatically. An annotation without `toleranceMm` defaults
to ±σ for the isoline.

**Gating:** PC1e requires PC1b (alignment, `[:ALIGNED_TO]` edge) and CAD1b
(face-index annotation encoding from aidocs/78 §6.2).

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
| Surface-annotation matching (per-feature stats) | (PC1e result) | PC + CAD + annotations | — | New — PC1e |
| 6D pose triad (position + orientation frame) | POSE_6D | Timeseries (7-ch: xyz+quat) | ✓ | New — SB2h; SA 6D tracking |
| Multi-channel timeline | (SB2d infrastructure) | Any mix | — | Frontend + timeline endpoint |

---

### §4 Phasing

| ID | Scope | Gated on | Priority |
|---|---|---|---|
| PC1a | `PointCloudReference` payload kind; PLY/E57 ingest; Potree tile serving | CAD1a (FS1a, plugin scaffolding) | High — enables dimensional inspection workflow |
| PC1b | CAD–Cloud ICP alignment; `[:ALIGNED_TO]` edge; Open3D sidecar | CAD1b (annotation model) + PC1a | High |
| PC1c | Deviation map auto-HEATMAP binding | PC1b + SB1b (HEATMAP mode) | Medium |
| PC1e | Surface-annotation matching; per-annotation deviation stats; matched-point subsets; tolerance pass/fail; `toleranceMm` annotation field | PC1b + CAD1b (face-index encoding) | High — feature-aware inspection, T-Scan primary use case |
| PC1d | Live point cloud streaming (sTC + SSE tiles) | PC1a + SB1e (SSE live) | Low — needs scanner API adapter |
| SB2a | Camera frustum glyph + PiP video overlay | JOINT_ANGLE (SB1d) + VID1a | Medium |
| SB2b | AE event cloud glyph | SB1c (GLYPH) + PC1a (scene graph) | Medium — high value for ZLP NDT |
| SB2c | Raster projection (thermography / DIC) | SB1b (HEATMAP) + PC1b (alignment) | Low — complex renderer |
| SB2d | Multi-channel timeline endpoint | SB1a (BADGE first) | Medium — frontend-driven |
| SB2e | Nominal vs actual trajectory | JOINT_ANGLE (SB1d) | High — ZLP AFP robot use case |
| SB2f | FEM frame animation (multi-frame DEFORMATION) | DEFORMATION (SB1e) | Medium |
| SB2g | Ultrasonic C-scan overlay + interactive A-scan panel | SB1b (HEATMAP) + PC1b (alignment) | High — ZLP NDT primary technique; spec pending |
| SB2h | `POSE_6D` display mode — animated SE(3) triad from 8-channel timeseries; `showTrace` tube; JOINT_ANGLE+POSE_6D combo for calibration comparison | SB1d (JOINT_ANGLE renderer) + PC1a | High — SA robot TCP tracking, robot calibration |

---

---

### §5 External tool integration notes

#### SA1 — Spatial Analyzer (New River Kinematics)

**What SA is:** Spatial Analyzer (https://www.kinematics.com/) is the standard
metrological software in precision manufacturing and aerospace. It integrates laser
trackers (FARO, Leica API), theodolites, photogrammetry, structured-light scanners, and
arm-based CMMs into a single coordinate frame. SA measurement results are the
**highest-accuracy ground truth** for part geometry in a production environment —
typically 10–50× more accurate than ICP alignment of raw scanned point clouds.

**Integration opportunities:**

1. **Export-based ingest (no SDK required):** SA exports measured points, nominal-vs-actual
   comparisons, and fitted features as CSV, ASCII point files, IGES, or STEP. These map
   directly to `PointCloudReference` (measured points) and `CadReference` (fitted geometry).
   The ingest can be automated via the IL1 instrument-dropbox plugin watching SA's export
   folder.

2. **Coordinate frame anchor (`SA_GROUND_TRUTH` tag):** A `PointCloudReference` or
   `CadReference` produced by SA gets tagged `coordinateFrame: "SA_GROUND_TRUTH"`. All
   other `[:ALIGNED_TO]` edges on sibling references use this as the authoritative frame.
   This models the SA workflow correctly: SA is not one input among many — it is the
   reference frame that calibrates all other sensors.

3. **Target-constrained alignment:** SA places retro-reflector targets at precisely measured
   positions. These targets appear in photogrammetry images, C-scans, and structured-light
   scans as identifiable features. When SA exports target positions, shepard stores them as
   `GeometryAnnotation` entries with `role: "REFERENCE_TARGET"` on the `CadReference`. The
   PC1b ICP alignment becomes target-constrained when these annotations are present — the
   result is far more robust than free ICP.

4. **SA SDK / scripting:** SA has a COM-based SDK (Windows, `NrkSdk.dll`) with Python
   bindings (`SA-Python`). A thin `shepard-plugin-spatial-analyzer` sidecar could:
   - Watch for SA session saves
   - Extract the nominal model, measured point groups, and fit residuals
   - Create `CadReference` + `PointCloudReference` + `[:ALIGNED_TO {:method:"SA_TRACKER",
     :rmse: x}]` + deviation map (PC1c) automatically
   This is the highest-value integration: zero manual export step, automatic provenance.

5. **Viewer role:** SA's native viewer is non-web, non-collaborative. The Three.js viewer
   in `shepard-plugin-cad` becomes the shared viewer for SA-derived geometry, with all
   shepard data (C-scan, thermocouple timeseries, DIC strain fields) overlaid on the SA
   coordinate frame.

6. **SA 6D pose tracking (robot TCP measurement):** SA with a 6DOF probe or `T-Scan 5`
   measures the full rigid-body pose (SE(3): position + orientation) of a target — typically
   a robot TCP, a fixture, or a tool. This produces a timeseries of `(t, x, y, z, qx, qy, qz, qw)`
   in the SA coordinate frame.

   shepard storage: a `TimeseriesReference` with 8 channels (`t`, `x`, `y`, `z`, `qx`, `qy`, `qz`, `qw`)
   tagged `sourceKind: "SA_6D_TRACKING"`. The `SpatialAnalyzerTrigger` (IL1) ingest pipeline
   creates this automatically on SA session export.

   **SB2h — `POSE_6D` display mode:** A new `DataBinding` display mode that renders an animated
   coordinate frame triad (three unit vectors: X=red, Y=green, Z=blue) at the measured position,
   tracking along the timeseries. The `GeometryAnnotation` anchor locates the nominal TCP position
   on the URDF end-effector link; the `POSE_6D` binding shows the measured SA pose at each timestep.

   ```cypher
   (:DataBinding {
     displayMode:         "POSE_6D",
     xChannel:            "x",
     yChannel:            "y",
     zChannel:            "z",
     qxChannel:           "qx",
     qyChannel:           "qy",
     qzChannel:           "qz",
     qwChannel:           "qw",
     triadScale:          double,   -- visual size of the triad arrows (mm)
     showTrace:           boolean,  -- render the path of the origin as a tube
     traceColourByAppId:  String,   -- optional: appId of a BADGE binding whose value
                                    --   colours the trace tube (heatmap on the path).
                                    --   E.g. TCP temperature binding → thermal trail.
     traceWidth:          double,   -- tube radius in mm (default 3.0)
     alarmThreshold:      double,   -- optional: BADGE label background turns red above this
     alarmUnit:           String    -- unit string shown in the CSS2D label (e.g. "°C")
   })
   ```

   **Thermal-trail pattern (MFFD seed demo):** When `traceColourByAppId` is set, the trace
   tube is heatmap-coloured by the referenced BADGE binding's timeseries value at each trail
   point's timestamp. The canonical case is TCP temperature during an AFP layup: the trail
   shows where the robot was and how hot the tool was at every point along the path. The
   floating CSS2DObject label at the live TCP position shows the current temperature and turns
   red when `alarmThreshold` is exceeded. No new backend logic — this is a client-side
   composition of two existing bindings (POSE_6D + BADGE).

   Client-side: `THREE.ArrowHelper` × 3 per timestep, updated by the time scrubber.
   `showTrace: true` renders the full TCP path as a `THREE.TubeGeometry` — gives an
   immediate visual of how closely the robot followed its programmed path.

   **Comparison with JOINT_ANGLE:** `JOINT_ANGLE` (SB1d) drives URDF joints from timeseries
   channels — it animates the robot's kinematic chain using the robot's *own* encoder data.
   `POSE_6D` overlays the *externally measured* TCP pose from SA. Running both simultaneously
   (SB2d multi-channel timeline) gives the full robot calibration picture: joint angles
   predict the TCP position; SA measures where the TCP actually was; the gap is the calibration
   error.

**Recommended phasing:** SA export ingest (item 1) is a day's work post-PC1a. 6D tracking
ingest + SB2h POSE_6D mode (item 6) is a week post-PC1a + SB1d (JOINT_ANGLE renderer already
provides the 3D scene infrastructure). SDK integration (item 4) is a week's effort and
high-value for ZLP dimensional inspection.

#### RDK1 — RoboDK integration

**What RoboDK is:** RoboDK (https://robodk.com/) is a robot simulation and offline
programming platform used widely in manufacturing research. It imports robot models,
supports multi-robot cells, generates robot programs (KUKA, Fanuc, ABB, UR, etc.), and
exports to URDF for ROS/simulation. ZLP uses it to plan AFP layup paths, welding robot
trajectories, and automated assembly sequences before physically running them.

**Integration opportunities:**

1. **URDF export chain:** RoboDK can export robot cells as URDF. This URDF becomes a
   `CadReference` (URDF bundle, CAD1a) in shepard. The JOINT_ANGLE data binding (SB1d)
   then animates the URDF using the actual recorded trajectory timeseries. The combination
   gives a digitally-animated robot cell with real measurement data — the digital twin UX.

2. **Program provenance:** RoboDK generates robot programs from simulation. A
   `shepard-plugin-robodk` sidecar reads the RoboDK project file (`.rdk`, which is an
   SQLite database) and creates:
   - A `StructuredDataReference` for the planned joint-angle trajectory (nominal path)
   - A `CadReference` for the simulated robot cell geometry
   - A `[:PRODUCED_BY]` edge from the robot program DataObject to the RoboDK project
   The SB2e nominal-vs-actual overlay (planned trajectory in blue, sTC-recorded in red)
   then gives immediate feedback on path deviation.

3. **SA → RoboDK → shepard calibration chain:** The canonical workflow for ZLP AFP/welding:
   - SA measures the robot's TCP (Tool Centre Point) against the part's reference frame
   - SA result (TCP offset + orientation) is fed into RoboDK to calibrate the robot model
   - RoboDK regenerates the program with the calibrated model
   - shepard stores the chain: `SA_measurement -> [:CALIBRATED] -> RoboDK_program -> [:PRODUCED_BY] -> robot_run`
   This provenance chain is the "why does the layup look like this" answer.

4. **RoboDK Python API:** RoboDK has a well-documented Python API (`robodk` package). The
   `shepard-plugin-robodk` sidecar wraps this API to read the current station's robot
   configuration, joint angles, and TCP pose, creating shepard entities automatically.

5. **URDF as simulation export:** The scene graph export API (`aidocs/78 §7`) already
   plans a `GET /v2/cad-references/{appId}/export.sdf` endpoint for Gazebo/ROS 2. RoboDK
   can ingest URDF to verify that the exported simulation model matches the production robot
   cell. This closes the loop: real robot → SA calibration → RoboDK verification → shepard
   digital twin.

**Recommended phasing:** URDF export chain (item 1) works immediately once CAD1a (URDF
bundles) ships — no RoboDK plugin needed. Python API integration (item 4, RDK1) is a
medium sprint; value is highest for ZLP AFP teams already using RoboDK daily.

---

---

### §7 Managed ingest pipelines (IL1)

**The upgrade over a static instrument dropbox:**

The IL1 instrument dropbox (aidocs/70 §4.1) was originally conceived as an
operator-configured file-system watcher. The broader design — sketched here pending a
dedicated IL1 design doc — is a **hosted, user-definable ingest pipeline system**:

- **User-definable:** researchers (not just operators) can create and configure ingest
  pipelines through the shepard UI or API. A pipeline defines: what to watch (source:
  filesystem path, S3 prefix, SA export folder, RoboDK project file, Tango Controls
  signal), what parser to run (format-specific `FormatParser` SPI implementation), and
  where the result goes (target Collection, payload template, metadata defaults).
- **Admin-managed:** instance-admins see all pipelines, enable/disable them, view their
  run history and error log, and configure the parser plugins that are available.
- **Hosted:** pipeline execution runs inside shepard (or a lightweight sidecar that shares
  the DB and S3 config), not as a separate standalone install. The pipeline state is stored
  as `:IngestPipeline` Neo4j entities following the `:*Config` admin pattern from CLAUDE.md.

**Neo4j model (provisional):**

```cypher
(:IngestPipeline {
  appId:          String
  name:           String
  enabled:        boolean
  sourceKind:     String   -- "FILESYSTEM" | "S3_PREFIX" | "TANGO" | "SA_EXPORT" | "ROBODK"
  sourcePath:     String
  parserPlugin:   String   -- "csv-timeseries" | "hdf5" | "ultrasonic-omni" | "sa-native" | …
  targetCollectionAppId: String
  templateAppId:  String   -- optional — applies a :ShepardTemplate on ingest
  lastRunAt:      ZonedDateTime
  lastRunStatus:  String   -- "OK" | "PARTIAL" | "ERROR"
  lastError:      String
})
```

**Admin REST surface:**

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/ingest-pipelines` | List all pipelines with status |
| `POST` | `/v2/admin/ingest-pipelines` | Create a new pipeline |
| `GET` | `/v2/admin/ingest-pipelines/{appId}` | Get pipeline details + run history |
| `PATCH` | `/v2/admin/ingest-pipelines/{appId}` | Update pipeline config (RFC 7396) |
| `POST` | `/v2/admin/ingest-pipelines/{appId}/run` | Trigger a manual run |
| `DELETE` | `/v2/admin/ingest-pipelines/{appId}` | Remove pipeline (does not delete ingested data) |

**Researcher-facing surface:**

Researchers who own the target Collection can create pipelines against it via:
`POST /v2/collections/{appId}/ingest-pipelines` — this creates a pipeline scoped to
their collection. Admins can promote it to instance-wide or restrict it. This keeps the
researcher UX simple (no need to go through the admin panel for their own lab instruments).

**Parser plugin SPI:**

```java
public interface FormatParser {
  /** Returns the plugin id: e.g. "csv-timeseries", "hdf5", "ultrasonic-omni" */
  String pluginId();
  /** Returns a list of MIME types and file extensions this parser handles */
  List<String> supportedTypes();
  /**
   * Parse the given input stream and create the appropriate Reference(s) in the
   * target DataObject. Returns the appIds of the created References.
   */
  List<String> parse(InputStream in, IngestContext ctx) throws ParseException;
}
```

`IngestContext` carries: the target `DataObject` appId, the `Collection` appId, the
pipeline template config, the shepard client session, and a progress reporter that feeds
the pipeline's run log in Neo4j.

**Two-SPI design (trigger + parser):**

```
IngestTrigger (watches for new data)
  └─ FileSystemTrigger        watches a local path
  └─ S3PrefixTrigger          polls an S3 prefix for new objects
  └─ SpatialAnalyzerTrigger   watches an SA session for new exports
  └─ RoboDKTrigger            watches a .rdk project file for changes
  └─ TangoControlsTrigger     subscribes to a Tango Controls device event

        ↓ new file/event

FormatParser (converts raw data to shepard Entities)
  └─ CsvTimeseriesParser      CSV → TimeseriesReference
  └─ Hdf5Parser               HDF5 → HDF5Reference (aidocs/A5)
  └─ UltrasonicOmniParser     OmniScan .opd → UltrasonicScanReference
  └─ PointCloudParser         PLY/E57/LAS → PointCloudReference
  └─ SaNativeParser           SA .rit/.sa → PointCloudReference + GeometryAnnotations
  └─ RoboDKParser             .rdk SQLite → CadReference (URDF) + StructuredDataReference
```

The pipeline runtime calls `trigger.watch()` in a background thread; on each event it
resolves the `FormatParser` by `parserPlugin` id and calls `parser.parse()`.

**Relationship to upstream "data parsers":** The upstream feature request "Data parsers
as plugins / data importer tooling" maps exactly to the `FormatParser` SPI half of this
design. Our addition is the `IngestTrigger` SPI (the automated watch half) and the
`:IngestPipeline` admin entity (the hosted, user-definable management layer). Together
these are a superset of what upstream called "data importers."

**Recommended first concrete parser — IL1b: `UltrasonicOmniParser`:**

The ultrasonic inspection use case (SB2g) is the ideal first `FormatParser` implementation:
1. Single-vendor format (Olympus OmniScan `.opd`, HDF5-based) — a well-scoped parsing problem.
2. ZLP inspects every CFRP part by C-scan — highest-volume ingest path.
3. `UltrasonicScanReference` Neo4j model is already designed (SB2g above) — clear output contract.
4. Data spec (A-scan + position + metadata) is forthcoming from the user — the parser can be
   written promptly once the spec is delivered.

IL1b = FileSystem trigger watching the OmniScan export folder + `UltrasonicOmniParser` creating
`UltrasonicScanReference` entities → full end-to-end: scan → shepard → C-scan viewer overlay,
no manual upload step.

**Phasing:** IL1a = `:IngestPipeline` entity + admin REST (no parsers yet, manual trigger only).
IL1b = `UltrasonicOmniParser` + FileSystem trigger (priority first parser; gated on data spec).
IL1c = CsvTimeseries parser (general lab DAQ). IL1d–IL1n = HDF5, SA-native, RoboDK parsers
shipped incrementally.

---

### §6 See also

- `aidocs/78` — 3D Geometry & FEM Annotator (`CadReference`, `GeometryAnnotation`,
  format matrix, scene graph export)
- `aidocs/81` — Spatial Data Binding (SB1 DataBinding model, five base display modes)
- `aidocs/82` — ZLP Augsburg stakeholder brief (dimensional inspection workflow §2.4,
  AE localisation §2.3)
- `aidocs/53` — Video plugin (`VideoReference`, HLS delivery — SB2a dependency)
