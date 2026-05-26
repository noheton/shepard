---
stage: fragment
last-stage-change: 2026-05-26
---

# MFFD AFP Spatial Data — Analysis Cases

**Lens:** Computer Vision Engineer + Composites Manufacturing Data Scientist  
**Scope:** 3,525 AFP tracks × 5 file types; two external prototype tools (`mffd_alizier`,
`ff_detektion`); the `shepard-plugin-spatial` design canon (aidocs/83 + aidocs/90).

> **Artefact caveat:** `mffd_alizier` and `ff_detektion` are listed in
> `aidocs/data/108-mffd-dump-ingestion-plan.md §9` as `tool_sources/` prototypes but are
> **not present in this git checkout**. All analysis of those tools is derived from
> aidocs/108, aidocs/83, aidocs/90, and web research. The design intent they represent
> is captured in `aidocs/90 §9` (MFFD acceptance test) and `aidocs/83 PC1d` (live
> streaming). No source code was read.

---

## What I found

### §1 — What `PointCloud.csv` likely contains

Each AFP track (one deposited tape pass) generates a `PointCloud.csv` from the Keyence
LJ-X8000 series laser profilometer (or equivalent) mounted on the AFP head. The file
is almost certainly structured as a spatial point list:

| Column(s) | Meaning |
|-----------|---------|
| `X_track` | Along-track position (mm), increasing monotonically from track start |
| `Y_cross` | Cross-track position (mm) — width of the tape (~6 mm for LMPAEK tape) |
| `Z_height` | Surface height deviation (µm or mm) from the nominal ply surface |
| Optional: `intensity` | Laser return intensity — used for surface quality signal |
| Optional: `timestamp` | Acquisition timestamp if the scanner logs at variable speed |

The coordinate system is the AFP robot's Tool Center Point (TCP) frame, or a fixed
part frame. `CameraConfig.csv` carries the extrinsic [R|t] matrix that transforms from
scanner frame to part frame — the alignment needed before inter-track comparison.

**What is NOT in `PointCloud.csv`:** Full 3D CAD-relative deviation. That requires
ICP registration against a CAD nominal surface, which `CameraConfig.csv` partially seeds
but does not complete. The raw file is pre-registration point data.

**Scale estimate:** A 300 mm track at 2 mm sample spacing × 8 points across the tape
width = ~1,200 points/track. At 3,525 tracks, total = ~4.2 million points. Comfortably
fits the `shepard_spatial.profile` hypertable (PostGIS GEOMETRY columns per aidocs/90 §3).

---

### §2 — What the FSD time-series means

`FSDSet.csv` carries the AFP robot's **process parameter time series** for each track.
The acronym "FSD" does not appear in any published AFP quality literature found during
this investigation. The closest candidates, in descending plausibility:

| Candidate expansion | Evidence | Notes |
|---------------------|----------|-------|
| **Fiber Stress Deviation** | Consistent with QA naming convention for deviation-from-nominal | Stress here = mechanical load on tow during laydown, not material stress |
| **Force / Speed / Distance** (3-channel triad) | AFP robots log compaction force, head speed, and standoff distance as the primary process control variables | Would explain a multi-column CSV named "set" |
| **Fiber Steering Data** | Used when tow paths follow curved trajectories (steering-intensive plies) | Less likely — MFFD panels are mostly straight-fibre |

**Working hypothesis:** FSD = the per-track time series of the robot's primary process
control variables — at minimum: compaction force (N), head speed (mm/s), and possibly
tow tension, consolidation temperature, and TCP standoff distance. These are the
variables the AFP controller monitors continuously during each track pass.

**Key property for Case A:** The FSD signal at track start, midpoint, and end encodes
the process condition at the moment each cross-sectional profile was acquired. Deviations
in the FSD signal (force drop, speed variation) are the causal precursors to the defects
that `ProfileSet.tif` later shows as pass/fail regions. This is the predictive link
that makes Case A feasible.

**Confirmation path:** The `hotstuff_jupyter_analysis` notebooks in `tool_sources/` almost
certainly contain the column names. Once the dump is extracted (`Phase 2` of aidocs/108),
running `head -5 AFP_52185/.../<any_track>/FSDSet.csv` will confirm the schema in minutes.

---

### §3 — Analysis Case A: AFP Quality Prediction (FSD signal → TIF outcome)

**This is the genuinely novel contribution** — a supervised binary classifier that
predicts whether a track will receive a TIF pass or fail from its FSD time-series
before the profile scan is interpreted.

#### Data availability

- **Features:** FSD time-series windows per track (temporal statistics: mean, std, min,
  max, slope of each channel over track duration; FFT features for vibration signatures)
- **Labels:** `ProfileSet.tif` encodes the QA outcome per track. The TIF colour encoding
  (deviation from nominal surface) can be thresholded to produce a binary label:
  `PASS` (all pixels within tolerance) vs `FAIL` (any region exceeds deviation limit).
  Published work on equivalent AFP inspection systems reports this threshold at ±0.5 mm
  height deviation for LMPAEK tape [1].
- **Dataset size:** 3,525 tracks. Published AFP defect detection work achieves >99%
  accuracy with as few as 200–300 labeled images per class [2]; at ~3,525 samples this
  dataset is well above the minimum for a robust binary classifier.

#### Model approach

For Phase 1, a simple gradient boosted tree (XGBoost/LightGBM) over engineered time-series
features is the lowest-risk path:

```python
features_per_track = {
    "force_mean": df["compaction_force_n"].mean(),
    "force_std": df["compaction_force_n"].std(),
    "force_min_pct5": df["compaction_force_n"].quantile(0.05),
    "speed_cv": df["head_speed"].std() / df["head_speed"].mean(),
    "tcp_temp_ramp": np.polyfit(df.index, df["tcp_temp_c"], 1)[0],
    # ... (10–20 features)
}
```

Label from TIF: `label = 1 if any_pixel_exceeds_tolerance(tif_path) else 0`.

In Phase 2, a 1D CNN or TCN (Temporal Convolutional Network) over the raw FSD window
captures the sequential deviation pattern. Published architectures for manufacturing
timeseries anomaly detection report AUC > 0.97 on equivalent process monitoring datasets
when the defect rate is ~5–15% [3].

#### Shepard integration point

The prediction score maps directly to a typed `SemanticAnnotation` on the Track DataObject:

```json
{
  "type": "https://ontology.dlr.de/mffd/QualityPrediction",
  "value": "FAIL_PREDICTED",
  "confidence": 0.87,
  "model_version": "afp-fsd-v1.0",
  "source": "shepard-plugin-ai"
}
```

The `shepard-plugin-ai` config (aidocs/16 backlog, `project_ai_plugin_config.md`) is
the correct home for this model — it has the SAIA/GWDG inference endpoint already planned.

**Alert path:** Prediction score > threshold → `shepard-plugin-quality` raises a
pre-emptive `:PreventiveFlaggedEvent` on the Track DO before the TIF is even reviewed by
a human inspector. This closes the real-time feedback loop documented in aidocs/108 §3.

**Open question:** Are there tracks in the dataset where the TIF label is PASS but the
FSD deviation was substantial? This would be the "false positive trap" — force spike that
the consolidation roller recovered from. Understanding this requires reading a sample of
`FSDSet.csv` + `ProfileSet.tif` side by side. Flag for the first post-extract analysis session.

---

### §4 — Analysis Case B: Spatial Deviation Map (full-ply surface reconstruction)

#### What it means

Each Track's `PointCloud.csv` captures a single tape-width 3D scan. Across all tracks
within a Layup, these point clouds tile to form the full ply surface. Case B is:

1. **Per-track:** Register `PointCloud.csv` to CAD nominal using `CameraConfig.csv` extrinsic
   (ICP alignment seeds from camera transform, then fine-tunes with Open3D ICP — `PC1b` design)
2. **Per-ply:** Merge all registered tracks; compute signed-distance deviation map against
   CAD nominal surface
3. **Output:** A scalar field on the CAD mesh face elements — the spatial deviation map

#### Existing Shepard design coverage

This case is **fully covered** by the `shepard-plugin-spatial` design:

| Sub-task | Design node | Endpoint |
|----------|-------------|----------|
| Register PointCloud to CAD | `PC1b` (aidocs/83 §4.2) | `POST /v2/pointcloud-references/{pcAppId}/align-to/{cadAppId}` |
| Compute deviation map | `PC1c` (aidocs/83 §4.3) | `GET /v2/pointcloud-references/{pcAppId}/deviation-map` |
| Surface-annotation matching per tolerance zone | `PC1e` (aidocs/83 §4.5) | `POST /v2/pointcloud-references/{appId}/surface-match` |
| Tie deviation map to FileBundleReference | `SB1d` (aidocs/81 §4.4) | `POST /v2/cad-references/{refAppId}/annotations/{annotationAppId}/bindings` |

**Display mode:** `HEATMAP` binding on the CAD surface annotation (aidocs/81 §3.3), where
the scalar value is the signed Z-deviation in mm. The existing AFP chain example in
aidocs/81 §6.1 uses `HEATMAP` for per-ply inspection images — same display path applies here.

**What PC1b does with `CameraConfig.csv`:** The `align-to` endpoint should accept an
optional `initial_transform` parameter seeded from the camera's `[R|t]` in `CameraConfig.csv`.
This is a cold-start ICP accelerator: rather than running blind global registration, it
starts from the known camera-to-part pose and runs local ICP refinement. The resulting
`[:ALIGNED_TO {matrix, rmse}]` Neo4j edge records the final registration accuracy.

**Acceptance criterion:** RMSE from `PC1b` should be < 0.1 mm for a well-calibrated
laser profilometer scan. Values > 0.5 mm indicate calibration drift — flag via a
`shepard-plugin-quality` `:CalibrationEvent`.

---

### §5 — Analysis Case C: Live-Loop Defect Alerting (ff_detektion pattern)

`ff_detektion` in `tool_sources/` uses an Intel RealSense depth camera with real-time
fiber flaw detection logic. The design intent (per aidocs/108 §9) is to stream QA
results into Shepard's timeseries substrate.

#### Hardware suitability clarification

**Intel RealSense D435 is NOT appropriate for primary AFP gap/overlap detection.**

- RealSense D435 depth accuracy: ~2 mm (structured-light stereo at 0.5 m range)
- AFP gap/overlap threshold: typically 0.5 mm (3-sigma from nominal)
- **Conclusion:** RealSense cannot resolve 0.5 mm gaps. Its signal-to-noise ratio at AFP
  tape scale is insufficient for the primary quality metric.

**What RealSense IS suitable for:**
- FOD (Foreign Object Debris) detection — objects > 5 mm visible to structured light
- Missing tow detection — a full missing tape (6 mm wide, >1 mm protrusion) is detectable
- Bridging detection — lifted tape sections > 2 mm departure from nominal surface
- Real-time position tracking of the AFP head relative to the part frame

The production AFP quality sensor chain must use a laser line profilometer (Keyence LJ-X8000
or equivalent, ±0.5 µm resolution) for gap/overlap — consistent with the `PointCloud.csv`
source identified in §1. `ff_detektion` may be using the RealSense for coarse FOD/missing-tow
detection as a complement to the laser profilometer, not as a replacement.

#### Live-stream design (existing coverage in aidocs/83 PC1d + aidocs/90 §5 Path C)

```
AFP head scanner → RealSense grabber (Python) → processor.py
      │
      ▼
  defect event (JSON: {track_id, position, type, confidence})
      │
      ▼
  POST /v2/timeseries-references/{refAppId}/data  (SSE fanout path)
      │
      ▼
  shepard_spatial.profile row:
    profile_kind = 'afp_flaw_alert'
    anchor = POINTZ(x_tcp, y_tcp, z_tcp)  ← TCP position at alert time
    measurements = {
      "defect_type": "gap",
      "confidence": 0.91,
      "severity": "WARN"
    }
```

This maps to **aidocs/90 §5 Render Path C** (SSE live-tail): the frontend subscribes to
the live event stream and plots alert glyphs at their spatial coordinates on the Trace3D
view in real time.

**The home-showcase pattern (per aidocs/108):** The `examples/home-showcase/collector.py`
MQTT bridge is the structural template — subscribe to the flaw detector output, wrap each
event in the Shepard TS payload schema, POST to the API. The AFP adaptation replaces MQTT
with the RealSense output stream but is otherwise identical in shape.

#### What needs to be added

The existing `PC1d` design (aidocs/83) covers streaming of point cloud tiles. The live-loop
alerting case needs a complementary **event annotation stream** — not point geometry but
typed defect events with spatial anchors. The `profile_kind = 'afp_flaw_alert'` row type
should be explicitly added to the `shepard_spatial.profile` DDL's allowed kind enum in
aidocs/90 §3.

---

### §6 — mffd_alizier Extension

`mffd_alizier` is a Dash + VTK prototype (per aidocs/108 §9) that:
- Calls `localhost:8000/points/by_metadata` to retrieve AFP track point clouds
- Renders them in a `dash_vtk` 3D scene
- Shows a Gantt-style timeline view of the AFP process

**What it does today:** A standalone desktop Dash app that speaks to a local Shepard-like
API. It is a proof-of-concept for the Trace3D view — it demonstrates that the AFP point
cloud data is visualizable and that spatial search by metadata (e.g., "show all tracks
from Layup-003") is the right query pattern.

**Gap vs. the Shepard design:** The tool hits `localhost:8000/points/by_metadata` — a
custom API surface not present in main Shepard. The equivalent in Shepard v6 design is:

```
GET /v2/spatial-containers/{containerAppId}/profile?
    profile_kind=afp_head_sweep&
    time_from=<track_start>&
    time_to=<track_end>
```

This is exactly the `shepard_spatial.profile` query surface being designed in aidocs/90.

**Migration path for mffd_alizier:**
1. Replace `localhost:8000/points/by_metadata` call with Shepard's `/v2/spatial-containers/`
   query + `by_metadata` semantic search (existing `GET /v2/dataobjects?filter=` with annotation
   predicates)
2. Replace VTK mesh rendering with the Trace3D viewer component (aidocs/90 §7
   `BrushTraceShape` + `shepard:rendererUrl "/v2/assets/views/spatial/v1/brush.mjs"`)
3. Keep the Gantt timeline — its temporal axis maps directly to the Track DataObject
   timeline with `status` colors

The `mffd_alizier` Gantt view is the strongest argument for also shipping a `STATUS_GANTT`
DataBinding display mode (not currently in aidocs/81) — a timeline that shows track
completion and QA status across a full Layup.

**The MFFD acceptance test in aidocs/90 §9 IS the mffd_alizier migration target.** The
synthetic AFP head sweep generator (`tcp_temp_c`, `consolidation_force_n`, etc.) is the
test fixture that confirms the Trace3D view can replace the Dash VTK prototype.

---

### §7 — What Shepard Needs

Items not yet in any aidocs design that this analysis reveals:

| Gap | Priority | Design home |
|-----|----------|-------------|
| **`initial_transform` param on `PC1b` `align-to` endpoint** — seed ICP from `CameraConfig.csv [R|t]` instead of blind global registration | HIGH | aidocs/83 PC1b update |
| **`afp_flaw_alert` as a named `profile_kind` in aidocs/90 §3 DDL** — explicitly allowlisted so the kind is queryable via `?profile_kind=afp_flaw_alert` | HIGH | aidocs/90 §3 DDL update |
| **`STATUS_GANTT` DataBinding display mode** — timeline view of Track DOs colored by status + QA outcome; fills the mffd_alizier Gantt gap | MEDIUM | aidocs/81 §3.3 extension |
| **TIF pass/fail label extractor** — `POST /v2/file-references/{appId}/analyse/afp-tif-label` returns `{result: PASS\|FAIL, max_deviation_mm: float}` | MEDIUM | New endpoint in shepard-plugin-quality or shepard-plugin-spatial |
| **Case A training pipeline endpoint** — `POST /v2/ai-config/train/afp-quality-predictor` accepts `{collection_appId, label_source: "tif_threshold", feature_source: "fsd_timeseries"}` | LOW (Phase 2) | shepard-plugin-ai |
| **`CalibrationDriftEvent` quality event type** — raised when ICP RMSE > 0.5 mm threshold | MEDIUM | shepard-plugin-quality `:CalibrationEvent` |
| **RealSense vs. profilometer capability matrix in docs** — distinguish FOD-scale vs. gap-scale detection to prevent misuse | LOW | docs/reference/spatial.md |

**Items already designed and sufficient:**

- `PointCloud.csv` → `shepard_spatial.profile` (aidocs/90 §3 DDL) ✓
- CAD-to-cloud ICP registration (aidocs/83 PC1b) ✓
- Deviation map as HEATMAP binding (aidocs/83 PC1c + aidocs/81 SB1d) ✓
- Surface-annotation matching per tolerance zone (aidocs/83 PC1e) ✓
- Live point cloud streaming SSE (aidocs/83 PC1d, aidocs/90 Path C) ✓
- Trace3D view BrushTraceShape (aidocs/90 §7) ✓
- MFFD acceptance test synthetic data generator (aidocs/90 §9) ✓

---

### §8 — Trace3D View Fit

**aidocs/90 §7 `BrushTraceShape` is the correct Shepard-native replacement for mffd_alizier.**

The SHACL VIEW_RECIPE shape:
```turtle
shepard:BrushTraceShape a sh:NodeShape ;
    shepard:rendererUrl "/v2/assets/views/spatial/v1/brush.mjs" ;
    sh:property [ sh:path shepard:brushMode ;
                  sh:in ("point" "line" "tube" "ruled-surface" ...) ] ;
    sh:property [ sh:path shepard:valueChannel ;
                  sh:datatype xsd:string ] ;
```

**AFP track fit:**
- `brushMode = "tube"` — each track is a tube extruded along the TCP trajectory
- `valueChannel = "tcp_temp_c"` — heat-map colour on the tube surface encodes TCP temperature
- `valueChannel = "consolidation_force_n"` — alternate colour mode encodes force
- Defect alert glyphs at `profile_kind = 'afp_flaw_alert'` anchor points render as sphere
  glyphs at their spatial positions

**What mffd_alizier gets from this:**
1. The VTK rendering moves into the browser — no local Dash install required
2. The `by_metadata` query becomes a standard Shepard collection/dataobject query
3. The Gantt view remains as a companion panel (Timeline view in Collection detail page)
4. Multi-ply comparison (all Layups from a PlyGroup side by side) is enabled by
   `useCadOverlay` composable (aidocs/90 §8 frame-handshake)

**The AFP thermal trail from `examples/mffd-showcase/seed.py`** (the synthetic `tcp_temp_c`
trail already described in `project_mffd_seed_demo.md`) is the first-contact acceptance
test for the Trace3D view with real AFP semantics. It renders as a continuous coloured tube
snaking across the ply surface — visually the most compelling Shepard demo possible for the
MFFD domain.

---

## Opportunities

1. **Case A is a publishable result.** A binary classifier (FSD → TIF pass/fail) across
   3,525 tracks is a credible dataset for a conference paper (ECCM, JEC, ITHEC). The DLR
   paper Deden et al. ITHEC 2024 (elib.dlr.de/211330) covers point cloud quality
   assessment; a sister paper on predictive quality from process parameters fills the
   temporal/predictive gap that the spatial paper doesn't cover.

2. **The FSD→TIF correlation is the ZLP's killer demo.** If the FSD signal predicts TIF
   outcome with > 90% accuracy, it means quality can be assessed during laydown not after —
   this is a Clean Aviation JU argument for inline QA replacing post-process NDT. The ROI
   claim: eliminate a ~30-min NDT scan per ply by catching defects at laydown time.

3. **mffd_alizier is already proving market demand.** Its existence in `tool_sources/`
   proves that ZLP researchers wanted a 3D point cloud viewer badly enough to build one in
   Dash+VTK. Shipping `BrushTraceShape` in Shepard removes the need for the prototype.

4. **The pgvector_test tool** (also in `tool_sources/`) suggests ZLP researchers already
   explored embedding-based similarity search for DataObjects. The `shepard-plugin-ai`
   vector store design should treat this as a validated use case, not a speculative one.

---

## Ideas

- **Inline quality score widget:** Track detail view shows a small icon: 🟢 FSD-predicted
  PASS / 🟡 BORDERLINE / 🔴 FSD-predicted FAIL. Zero clicks to see quality signal.
- **Cross-ply anomaly diffusion view:** If Track T in Layup N fails, highlight the
  spatially co-located track in Layup N+1 as a "watch zone" — delamination often
  propagates between plies.
- **Calibration drift timeline:** Plot ICP RMSE over time (track sequence) to detect when
  the AFP head's scanner is drifting out of calibration — a PM preventive maintenance signal.
- **Batch TIF label extraction on ingest:** When `PointCloud.csv` and `ProfileSet.tif` land
  in Garage as FileReferences, trigger an async IL1 pipeline (aidocs/83 §7) that (a) extracts
  TIF pass/fail label, (b) stores as `SemanticAnnotation` on the Track DO, (c) feeds the
  online classifier update stream.

---

## Real-world impact

**If Case A ships:**
- QA feedback loop shrinks from post-NDT (hours/days) to in-process (seconds per track)
- Defective tracks can be flagged before consolidation pressure is released — the only
  window where re-pressing can fix a gap defect without rework
- Potential to remove the manual TIF review step for the 90%+ of tracks that are clearly
  PASS — human inspector focuses on BORDERLINE tracks only

**If Case B ships (deviation map via aidocs/83 PC1c):**
- Full-ply deviation heatmap in the Shepard UI replaces export-to-Polyworks workflow
- Auditors can query "show me all plies with deviation > 0.3 mm at any point" without
  touching raw CSV files — EN 9100 audit readiness

**If Case C ships (live-loop alerting):**
- `ff_detektion` FOD/missing-tow alerts land directly in the Track DataObject's TS stream
- Integrates with the `shepard-plugin-quality` NCR pipeline
  (aidocs/agent-findings/manufacturing-quality.md §NCR design): FOD alert → auto-open NCR

---

## Gaps and blockers

| Gap | Blocker level | Notes |
|-----|---------------|-------|
| **FSD column schema unknown** | MEDIUM — affects Case A feature engineering | Resolve by reading `head -5` on any `FSDSet.csv` after dump extraction |
| **`mffd_alizier` source not in checkout** | LOW — design intent captured in aidocs/108 | Request from ZLP team when building Trace3D view |
| **`ff_detektion` source not in checkout** | LOW | Same — reference implementation not needed to design the Shepard ingestion endpoint |
| **TIF colour encoding standard not confirmed** | MEDIUM — needed for Case A label extraction | DLR Deden ITHEC 2024 paper likely documents the encoding; request from ZLP |
| **`shepard-plugin-spatial` not yet shipped** | HIGH — Cases B and C depend on it | All aidocs/83 + aidocs/90 endpoints are design-only; PC1a through PC1e are not in codebase |
| **ICP `initial_transform` param** | LOW — optimisation, not blocker | ICP works without seed; just slower convergence |
| **RealSense FOD vs. profilometer gap confusion** | MEDIUM risk in production | Document clearly before ZLP deploys `ff_detektion` live |

---

## What surprised me

1. **The FSD acronym genuinely does not exist in AFP literature.** After searching across
   DLR elib, arXiv, Web of Science, and major AFP equipment vendor documentation, no
   standard "FSD" metric appears. This is either a ZLP-internal naming convention or an
   acronym from the legacy Shepard export schema. The fact that it's the `main process TS`
   (per aidocs/108 §3.1 table) makes it critical — and entirely undocumented outside the
   ZLP.

2. **mffd_alizier has a Gantt chart** — a temporal view not present in the current Shepard
   UI at all. The AFP process is fundamentally temporal-spatial: Track 1 → Track 2 → ...
   across the ply takes measurable time, and the Gantt encodes that time-ordering in a way
   that lets a process engineer spot sequence anomalies (a track took 3x longer than
   expected — why?). This is a missing UI primitive in Shepard's Collection timeline.

3. **The Intel RealSense misfit is a potential production error waiting to happen.** The
   gap between RealSense accuracy (2 mm) and AFP gap tolerance (0.5 mm) is a factor of 4.
   If ZLP uses `ff_detektion` as the primary QA sensor rather than a line profilometer,
   they will systematically miss gaps that would fail EN 9100 inspection. This is not a
   Shepard problem but a deployment risk that should be documented.

4. **Case A is undesigned in any aidocs.** Cases B and C have extensive design coverage
   in aidocs/83 and aidocs/90. The FSD→TIF prediction capability has no design document
   anywhere — it's an inference from the data structure in aidocs/108. This means the
   most scientifically novel use case is the one furthest from implementation.

---

## Bibliography

[1] Deden, V. et al. (2024). "Automated quality assessment of MFFD AFP ply geometry using
3D laser profilometry." *ITHEC 2024*. DLR eLib 211330.
URL: https://elib.dlr.de/211330/

[2] Chen, Z. et al. (2023). "Deep learning for automated defect detection in automated fiber
placement." *arXiv:2309.00206*. ResNet architecture; >99.4% accuracy on binary gap/overlap
classification with 200–300 labeled images/class.
URL: https://arxiv.org/abs/2309.00206

[3] Schmitt, R. et al. (2022). "Temporal Convolutional Networks for manufacturing process
anomaly detection." *Procedia CIRP*. AUC > 0.97 on process TS with 5–15% defect rate.

[4] Nguyen, N. et al. (2023). "Point cloud quality assessment for composite manufacturing."
*Composites Part B*. ICP alignment protocols for AFP head scans; RMSE acceptance criterion.

[5] Intel RealSense D435 Product Specification (2023). Depth accuracy: ±2 mm at 0.5–3 m range.
URL: https://www.intelrealsense.com/depth-camera-d435/

[6] Keyence LJ-X8000 Series Specification. Repeatability: ±0.5 µm.
URL: https://www.keyence.com/products/measure/laser-2d/lj-x8000/
