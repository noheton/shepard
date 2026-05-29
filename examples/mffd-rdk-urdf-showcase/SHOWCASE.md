# MFFD RDK → URDF Showcase

End-to-end demonstrator of Shepard's browser URDF viewer + animator
driven by real DLR MFFD AFP-cell metadata.

## What this shows

- **Real `.rdk` station file uploaded → tier-1 parser auto-scrapes
  8 `urn:shepard:rdk:*` annotations on the FileReference** (appVersion,
  platform, programSource, robotController, apiEndpoint, three cadRef
  paths, two stepRef paths) — no UI work; the RDK-PARSE-1 plugin runs
  on upload.
- **URDF model rendered in-browser** (Three.js via `urdf-loader`)
  for the KR210 L150 — a representative six-axis KUKA articulated arm
  standing in for the actual MFFD KR270 R2700 (no public URDF
  exists; see `urdf/SOURCES.md § Caveat`).
- **6-channel joint trajectory whose channels match the URDF joint
  names (`joint_a1 … joint_a6`)** → `UrdfChannelPicker` auto-binds
  channels to URDF joints with zero manual configuration. The
  picker's preselection contract is `urn:shepard:urdf:joint` (per
  URDF-WEBVIEW-1 §3) with a field-name heuristic fallback (per
  `frontend/utils/urdfChannelPicker.ts` line 47); the showcase
  exercises both — the seeder best-effort writes the annotation, and
  the channel field names match the joint names, so the binding lands
  even if the annotation predicate is rejected by the current
  channel-annotation endpoint (see "Honest caveats" below).
- **Animator plays the synthetic AFP layup trajectory** through the
  URDF at ≥30 FPS — scrub, play/pause, speed presets — via
  `UrdfAnimator.vue`.

## 90-second click-walkthrough

After running `seed.py` (see `README.md`) the seeder prints the
Collection URL. Then:

1. **Collection page** — three DataObjects visible:
   - `MFFD AFP Cell — MFZ.rdk source`
   - `R10 (KR210 L150) — kinematic model`
   - `AFP Ply 5 layup — joint trajectory`

2. **Click "MFZ.rdk source" → its single FileReference** — the
   FileReference detail panel shows the eight scraped annotations
   under the `urn:shepard:rdk:*` namespace.
   The scrape happened automatically on upload — no user action.

3. **Back to Collection → click "AFP Ply 5 layup" → its
   TimeseriesReference** → "Visualize in 3D" → toggle the renderer
   to **URDF**. Enter the URDF source URL
   `/urdf-samples/kr210/kuka_kr210_support/urdf/kr210l150.urdf` +
   packagePath `/urdf-samples/kr210` (the seeder logs these as the
   final summary). Click "Open" → renderer dispatches `UrdfView` →
   the KR210 robot paints.

4. **Open `UrdfChannelPicker`** — the six joints `joint_a1 …
   joint_a6` are already bound to the seeded channels with zero
   clicks (the annotation does the work).

5. **Press play on the animator** — the robot moves through the
   30-second AFP raster sweep. Scrub the timeline; toggle 0.1×,
   1×, 10× speed.

## Honest caveats

This showcase **routes around** the missing phase-2 piece of the
URDF-WEBVIEW-1 chain. Things you should know:

- **Tier-1 scrape is metadata-only** — `RdkTextScrapeParser`
  emits cell-level annotations from the `.rdk` byte stream but
  **does not** derive kinematics. The URDF is sourced separately.
- **The URDF is open-source ROS, not generated from the `.rdk`** —
  KR210 L150 from `ros-industrial/kuka_experimental` (Apache-2.0).
  Substitutes for the actual MFFD KR270 R2700 — `urdf/SOURCES.md`
  documents the substitution.
- **Phase 2 — `RdkToUrdfExporter` sidecar — is queued**, gated on
  RoboDK SDK + KUKA OLP licence availability. When shipped, this
  showcase's URDF can be regenerated directly from MFZ.rdk and the
  substitution row in `SOURCES.md` retired.
- **The joint trajectory is synthetic but joint-limit-respecting** —
  `trajectory/generate.py` produces 30 s × 100 Hz of believable
  AFP raster motion. Joint angles are hand-tuned sinusoids + ramps,
  not back-solved from a TCP path. Looks like an AFP layup; isn't
  one.
- **Channel-annotation endpoint** — `POST /v2/timeseries-containers/
  {containerId}/channels/{shepardId}/annotations` currently anchors
  the predicate at `urn:shepard:spatial:axis` (the TS-AXIS-AUTO
  shape). A companion endpoint for arbitrary predicates (or one
  parameterised at `urn:shepard:urdf:joint`) is the URDF-WEBVIEW-1 §3
  TODO. Until that lands, the seeder's annotation step may be a
  no-op; auto-binding still works via the field-name heuristic
  fallback because the channel `field` values exactly match the URDF
  joint names. When the proper endpoint ships, the annotations the
  seeder tries to write will succeed and the binding will switch
  from heuristic to declared.
- **Mesh weight** — the visual meshes total ~14 MB across 7
  Collada files. `link_2.dae` is the largest at 5.2 MB. The
  collision meshes (~221 KB STL set) are NOT shipped; the viewer
  uses visual meshes only.
- **Path-mounted at `/urdf-samples/kr210`** — the URDF + meshes
  are served from `frontend/public/` so the browser fetches them
  via plain HTTP. The seeder also uploads them as FileReferences
  for provenance; the runtime fetch path is the public dir, not
  the FileReference signed URL (saves CORS + presign latency).

## Cross-references

- [`urdf/SOURCES.md`](./urdf/SOURCES.md) — URDF licence + commit hash
- [`urdf/kr210l150.urdf`](./urdf/kr210l150.urdf) — URDF source-of-truth copy
- [`trajectory/generate.py`](./trajectory/generate.py) — synthetic trajectory generator
- [`plugins/fileformat-robotics/`](../../plugins/fileformat-robotics/) — RDK-PARSE-1 plugin
- [`aidocs/integrations/113-urdf-viewer.md`](../../aidocs/integrations/113-urdf-viewer.md) — URDF-WEBVIEW-1 design
- [`aidocs/integrations/110-file-format-parser-plugin.md`](../../aidocs/integrations/110-file-format-parser-plugin.md) — Parser plugin SPI
- [`examples/mffd-showcase/`](../mffd-showcase/) — sibling MFFD AFP showcase (process chain + sensors)
- URDF-WEBVIEW-1, RDK-PARSE-1 in [`aidocs/16-dispatcher-backlog.md`](../../aidocs/16-dispatcher-backlog.md)

## Phase 2 — what's missing

- `RdkToUrdfExporter` sidecar (`plugins/fileformat-robotics/sidecars/rdk-to-urdf/`)
  wrapping the RoboDK Python API. When licence-cleared, this would
  derive a URDF directly from MFZ.rdk's frame tree, eliminating the
  KR210 substitution.
- Composition with Trace3D — overlay the TCP path (Trace3D) on top of
  the moving URDF robot in a single canvas, so the IR temperature
  trail + joint motion render synchronized. Tracked under
  URDF-WEBVIEW-1 phase 2 acceptance.
- Foxglove Studio iframe fallback — same URDF, ROS-bag replay, for
  operators living in RViz already.
- `UrdfRecordButton` — record the in-browser animation as a new
  `TimeseriesReference` (round-trip for human-tuned demo motion).
