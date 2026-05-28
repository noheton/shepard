---
stage: feature-defined
last-stage-change: 2026-05-28
audience: [contributors, frontend, mffd, robotics]
---

# 113 — URDF web viewer + animator (URDF-WEBVIEW-1)

**Audience.** Frontend contributors integrating robot descriptions into
Shepard's VIEW_RECIPE pipeline; operators looking at MFZ.rdk → URDF
translation; researchers binding joint-angle timeseries channels to a
URDF model for trajectory playback.

## 1. What this is

A browser-based URDF (Unified Robot Description Format) renderer + animator
that slots into the existing VIEW_RECIPE infrastructure as a SEPARATELY
SELECTABLE renderer alongside Trace3D. A user opening the "Visualize in 3D"
dialog from a TimeseriesContainer picks one of two renderers:

- **Trace 3D** — color-mapped 3D path from X/Y/Z + value channels.
- **URDF** — robot rendered from a `.urdf` file, optionally animated from
  joint-angle timeseries.

Future work may compose them (URDF cell with a Trace3D TCP path overlaid in
the same scene); that is OUT OF SCOPE for v1.

## 2. Shipped surface

- **`urdf-loader`** (gkjohnson, MIT, npm package) added to
  `frontend/package.json`. Depends on `three` which was already pinned.
- **`frontend/components/shapes/UrdfCanvas.vue`** — Three.js client-only
  scene + OrbitControls + ResizeObserver. Loads the URDF, mounts the
  resulting URDFRobot Object3D, applies `jointValues` reactively via
  `robot.setJointValue(name, radians)` per frame WITHOUT rebuilding the
  scene. Exposes `captureDataUrl()` for screenshots, mirroring
  Trace3DCanvas.
- **`frontend/components/container/timeseries/UrdfView.vue`** — thin
  adapter over `UrdfCanvas`, the URDF sibling of `Trace3DView`. This is
  the component dispatched by the shapes/render page when
  `ShapesRenderResponseIO.renderer === "urdf"`.
- **`frontend/components/container/timeseries/UrdfAnimator.vue`** —
  wraps `UrdfView` with Vuetify play/pause/scrub/speed controls (presets
  0.1× / 0.5× / 1× / 2× / 10× + reverse toggle). Interpolates each
  bound joint track at the current cursor via the pure helper
  `interpolateAt` (`utils/urdfAnimation.ts`).
- **`frontend/components/container/timeseries/UrdfJointPanel.vue`** —
  manual `v-slider` per movable joint (static-view mode, used when no TS
  animation is bound).
- **`frontend/components/container/timeseries/UrdfChannelPicker.vue`** —
  channel-to-joint binding UI, sibling of `Trace3DChannelPicker`.
  Annotation-driven preselection per
  `project_annotation_preselection_principle.md`: see §3 below.
- **`frontend/pages/shapes/render.vue`** — adds a `renderer === "urdf"`
  branch that mounts `UrdfView`. The query-param bootstrap accepts
  `?renderer=urdf&urdfUrl=…&packagePath=…` from the dialog.
- **`frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue`**
  — adds a Trace3D / URDF `v-btn-toggle` at the top and conditionally
  swaps the body + action button.
- **`frontend/public/urdf-samples/two-link-arm.urdf`** — minimal
  hand-written sample so `UrdfView` paints something even before a real
  URDF is bound. Acceptance: open
  `/shapes/render?renderer=urdf&urdfUrl=/urdf-samples/two-link-arm.urdf`
  → see two-link arm in browser.

## 3. The annotation contract (channel → joint binding)

URDF joint binding uses the predicate **`urn:shepard:urdf:joint`** in the
`urn:shepard:*` namespace (per CLAUDE.md "evolve in a new namespace; never
mutate an existing one"). Constant exported by
`utils/urdfChannelPicker.ts` as `URDF_JOINT_PREDICATE`.

A channel annotated with `urn:shepard:urdf:joint = <jointName>` auto-binds
to that joint when `UrdfChannelPicker` opens. Heuristic fallback (so v1
works even before annotations are seeded): a channel whose `symbolicName`
or `field` equals the joint name (case-insensitive) auto-binds.

The picker emits a "save as default" snackbar when the user changes a
binding manually, mirroring the Trace3D `TS-AXIS-AUTO` UX. Persisting that
choice through to a `:SemanticAnnotation` write is wired identically to
Trace3D: the parent dialog (here `ViewRecipeBuilderDialog`) calls
`POST /v2/timeseries-containers/{containerId}/channels/{shepardId}/annotations`
with `{ value: <jointName> }` per role. A future companion endpoint
`GET /v2/timeseries-containers/{containerId}/channels/joint-roles`
(mirroring `/channels/spatial-roles`) would let the picker fetch the
preselect map in one round-trip; until that lands, the heuristic + the
in-flight `annotatedJoint` field carry the load.

## 4. The Trace3D / URDF separation

Both are VIEW_RECIPE templates (templateKind = `VIEW_RECIPE`). They share:

- The same VIEW_RECIPE template kind.
- The same `POST /v2/shapes/render` response shape.
- The same shapes/render delegation pattern.
- The same Three.js + OrbitControls + ResizeObserver scaffolding.
- The same `captureDataUrl()` screenshot contract.

They differ on:

- The renderer hint string in `ShapesRenderResponseIO.renderer`
  (`"trace-3d"` vs `"urdf"`).
- The channel-binding semantics (axis roles vs joint names).
- The geometry (line-strip color-mapped trace vs robot-arm meshes).

A user picks one or the other from the renderer toggle in
`ViewRecipeBuilderDialog`. Future composition (a single VIEW_RECIPE that
renders a URDF AND overlays a Trace3D TCP path) is the next iteration,
not v1.

## 5. The lossy RDK → URDF boundary

URDF is Shepard's lingua franca for kinematics because every robotics tool
in the ecosystem (ROS RViz, Isaac, MuJoCo, Foxglove, Gazebo) consumes it.
The conversion from a RoboDK `.rdk` station to URDF is **lossy** by design:

- **URDF carries**: joint axes, joint limits, link visual meshes, the
  parent/child frame tree.
- **URDF does NOT carry**: cell-level RoboDK objects (programs, target
  arrays, named poses), simulation tolerances, post-processor metadata,
  PRP arrays, tool-changer state. This information lives on the RDK
  FileReference as `urn:shepard:rdk:*` annotations (RDK-PARSE-1 surface)
  and on the `:DigitalTwinScene` graph (RDK-PARSE-2 surface).

For an MFFD operator viewing R10 → R20 layup, the URDF gives the
geometric/kinematic view; the RDK annotations and DigitalTwinScene give
the cell-level context. Both live on the same DataObject.

The conversion sidecar (`RdkToUrdfExporter`, deferred to URDF-WEBVIEW-1
phase 2) wraps `robolink.Item.UpdateRobotURDF()`.

## 6. Licensing

- `urdf-loader` (gkjohnson) — **MIT**. Compatible with the CLAUDE.md
  licence-compatibility policy (no GPL/AGPL/SSPL exposure).
- Foxglove Studio iframe fallback (referenced in URDF-WEBVIEW-1 §g but not
  shipped in v1) — Apache-2.0.
- Three.js — MIT.

The sample two-link-arm URDF at `public/urdf-samples/two-link-arm.urdf`
is original work for this project; no third-party meshes embedded.

## 7. Tests

- `frontend/tests/unit/UrdfCanvas.test.ts` (9 tests) — `extractJointSpecs`
  helper that converts a loaded URDFRobot's `joints` map into the
  lightweight specs UrdfJointPanel + UrdfChannelPicker consume. Fixed
  joints filtered by default; jointType + limits carried through.
- `frontend/tests/unit/UrdfAnimator.test.ts` (21 tests) — `interpolateAt`
  (clamping, midpoints, descending values, zero-width segments),
  `jointValuesAt`, `trackTimeBounds`, `advanceCursor` (forward, reverse,
  clamping, time-unit scaling).
- `frontend/tests/unit/UrdfChannelPicker.test.ts` (17 tests) —
  `preselectChannelForJoint` (annotation match wins, heuristic fallback,
  case-insensitivity), `initialBinding`, `isBindingReady`,
  `resolveBoundChannels`.

Total: **47 tests, all passing.** Pure-helper pattern only (no component
mounting, matching the project's existing Vitest layout).

## 8. Out of scope (deferred to URDF-WEBVIEW-1 phase 2)

- `UrdfRecordButton.vue` — record current animation timeline as a new
  TimeseriesReference with joint-name channel annotations.
- Foxglove Studio iframe fallback at `cdn.shepard/foxglove/v2/`.
- `RdkToUrdfExporter` sidecar.
- Trace3D + URDF composition in a single VIEW_RECIPE.
- Backend `joint-roles` aggregation endpoint (sibling of `spatial-roles`).
- Real-time LTTB downsampling for tracks > 10k samples per channel.

## 9. Cross-refs

- `aidocs/16-dispatcher-backlog.md` — URDF-WEBVIEW-1 row.
- `aidocs/data/85-coordinate-frame-tree.md` — CoordinateFrame / DigitalTwinScene schema.
- `aidocs/semantics/98-shapes-views-and-process-model.md` — VIEW_RECIPE design.
- `aidocs/agent-findings/trace3d-spike.md` — the Trace3D spike that
  established the shapes/render delegation pattern.
- `frontend/components/container/timeseries/Trace3DView.vue` — the sibling
  Trace3D adapter (separately selectable; future composable).
- `project_annotation_preselection_principle.md` — the principle this
  binding contract follows.
