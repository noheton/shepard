---
title: vis-trace3d — Reference
stage: feature-defined
last-stage-change: 2026-06-04
audience: plugin-author
---

# vis-trace3d — reference

`shepard-plugin-vis-trace3d` ships the **Trace3D VIEW_RECIPE** —
the shape contract that lets a researcher render a time-varying
3D end-effector path (robot TCP, AFP head, satellite ground track)
as a colour-mapped brush trace, with an optional scalar channel
(temperature, force, anomaly score) driving the colour map.

The plugin is a **Phase-1 capability declaration** in this release.
The Three.js renderer (`Trace3DView.vue`) and the
`POST /v2/shapes/render` dispatcher are in-tree on the backend
(commits `643d271dc`, `70b133d28`, `f63f82624`, `6e970e659`); Phase 2
extracts them once the `ViewRecipeRenderer` SPI dispatcher (VIS-S1)
lands.

---

## When to use this view recipe

- **AFP head TCP thermal trail** — render the carbon-tape laydown
  path as a 3D ribbon, colour-mapped by tool-centre-point temperature.
  The MFFD layup demonstrator is the canonical acceptance dataset.
- **Hot-fire test instrumentation** — render a 3D accelerometer
  trace coloured by vibration RMS, so a stage anomaly (e.g.
  LUMEN TR-004 turbopump spike at t=8s) shows up as a colour band
  in space.
- **Satellite ground-track** — render lat/lon/altitude tuples over
  the orbit, coloured by signal-strength or instrument-temperature
  scalars.
- **Force-torque traces** — LBR iiwa joint force/torque vectors
  rendered as 3D paths during a peg-in-hole assembly (the existing
  MFFD `examples/mffd-showcase/shapes.py` demonstrator).

The colour-mapped path is the unifying shape: three required X/Y/Z
channels + one optional scalar channel, all aligned on the same
time grid.

---

## What this plugin ships

### `Trace3DViewShape` — the SHACL VIEW_RECIPE shape

Classpath resource at `/shapes/trace-3d-view.shacl.ttl`. Extends the
upstream `shepard-ui:ViewRecipeShape` meta-shape
(`backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl`) with
the trace3d-specific render parameters.

Required channel bindings (three or four, all
`shepard-ui:ChannelBinding` instances):

| Role | Required? | Notes |
|---|---|---|
| `x` | ✓ | TimeseriesChannel for the X spatial coordinate. |
| `y` | ✓ | TimeseriesChannel for the Y spatial coordinate. |
| `z` | ✓ | TimeseriesChannel for the Z spatial coordinate. |
| `color` | ✗ | TimeseriesChannel for the scalar driving the colour map. |

Trace3D-specific scalar properties (all optional with sensible
defaults):

| Predicate | Type | Default | Notes |
|---|---|---|---|
| `trace3d:colorMap` | `xsd:string` | `"viridis"` | One of `viridis`, `plasma`, `inferno`, `magma`, `cividis`, `turbo`, `rdbu`, `jet`, `heat`, `cool`. The renderer's `frontend/utils/colormap.ts` lookup table provides RGB. |
| `trace3d:interpolation` | `xsd:string` | `"linear"` | `none` / `linear` / `catmull-rom`. |
| `trace3d:alignment` | `xsd:string` | `"linear-resample"` | `nearest` / `linear-resample` / `snap-to-x`. |
| `trace3d:sampleRate` | `xsd:double` | (lowest input rate) | Frames per second the resolver emits. |
| `trace3d:lineWidth` | `xsd:double` | `2.0` | Width of the Line2 fat-line shader. |
| `trace3d:valueRange` | `xsd:string` | (auto-scale) | JSON `[min, max]` clamping the colour-map domain. |
| `trace3d:timeRange` | `xsd:string` | (full window) | ISO-8601 interval `<start>/<end>`. |
| `trace3d:frameKey` | `sh:IRI` | (world frame) | Optional CST1 `:CoordinateFrame` IRI. Renderer surfaces the name in the legend; resolver does no transform in v1. |
| `trace3d:rendererUrl` | `xsd:string` | (in-tree component) | Optional ES-module URL of a `mount(canvas, envelope)` entry point. The VIS-S3 stretch goal points this at an Isaac Lab WebRTC bridge. |

### `VisTrace3DPluginManifest` — the capability declaration

- `id = "vis-trace3d"`
- `version = "1.0.0-SNAPSHOT"` (plugin-side; independent of core)
- `shepardCompatibility = ">=6.0.0-SNAPSHOT,<7"`
- Surfaces in `GET /v2/admin/plugins`.
- Runtime toggle: `shepard.plugins.vis-trace3d.enabled` (default `true`).
- No sidecars — the renderer is browser-side Three.js.

---

## REST surface

Phase 1 adds **no new endpoints**. The existing `POST /v2/shapes/render`
dispatcher (TPL2b, in-tree on the backend at
`de.dlr.shepard.v2.shapes.resources.ShapesRenderRest`) handles the
Trace3D VIEW_RECIPE by returning the channel-binding envelope.

### `POST /v2/shapes/render` — what to send + what comes back

Request:

```json
{
  "templateAppId": "<the VIEW_RECIPE template's appId>",
  "focusShepardId": "<the focus DataObject's appId>"
}
```

Response (beta — all bindings carry `status = "DECLARED"`):

```json
{
  "templateAppId": "...",
  "focusShepardId": "...",
  "renderer": "tresjs",
  "channelBindings": [
    { "role": "x", "channelSelector": "{\"measurement\":\"afp\",...}", "unit": "http://qudt.org/vocab/unit/MilliM", "required": true, "status": "DECLARED", "resolved": null },
    { "role": "y", "channelSelector": "...", "unit": "...", "required": true, "status": "DECLARED", "resolved": null },
    { "role": "z", "channelSelector": "...", "unit": "...", "required": true, "status": "DECLARED", "resolved": null },
    { "role": "color", "channelSelector": "...", "unit": "http://qudt.org/vocab/unit/DEG_C", "required": false, "status": "DECLARED", "resolved": null }
  ]
}
```

The frontend `Trace3DView.vue` consumes the envelope's
`channelSelector` strings to make its own TimescaleDB channel
queries. Live channel resolution (`status = "OK"` /
`"MISSING"` / `"UNIT_MISMATCH"`) ships in TPL2c, gated on the
TS-ID migration (`aidocs/platform/87`).

### `POST /v2/shapes/render` with `Accept: image/png` (RESEED-FIND-RENDER-PNG)

This plugin ships `Trace3DPngRenderer`
(`de.dlr.shepard.plugins.vistrace3d.render.Trace3DPngRenderer`), a
`ViewRecipeRenderer` that **claims the `Trace3DViewShape` IRI** and
declares `image/png` in `producibleMedia()`. With the plugin installed,
`POST /v2/shapes/render` for a Trace3D VIEW_RECIPE honours
`Accept: image/png` and returns real PNG bytes instead of falling back
to the JSON view-model (the V2CONV-A1 content-negotiation contract).

The PNG is rasterised **server-side, pure-JVM** via
`java.awt.BufferedImage` + `Graphics2D` + `javax.imageio.ImageIO` — no
headless browser, no native dependency. The image is a labelled
view-recipe card: the recipe title, the declared colour map, a 2-D
axis frame with a colour-ramped illustrative path, and a legend of the
declared channel bindings. Because the render endpoint is stateless and
does not yet resolve live channel samples (TPL2b/DECLARED beta), the
drawn path is deterministic and illustrative; it is replaced by the
resolved frame polyline once live channel resolution lands (TPL2c)
with no change to the negotiation contract.

```bash
curl -X POST https://<host>/v2/shapes/render \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -H "Accept: image/png" \
  -d '{"templateAppId":"<view-recipe-appId>","focusShepardId":"<focus-appId>"}' \
  -o trace3d.png
```

Registered through
`META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer`
(ServiceLoader, the same mechanism `ViewRecipeRendererRegistry` walks
at startup). Any other `Accept` (`application/json`, `*/*`) returns the
JSON view-model unchanged.

### Frame envelope (the in-tree v1 contract)

The current renderer reads three flat number arrays (`xData`, `yData`,
`zData`) plus an optional `valueData`, indexed by the array position
(no real timestamp). See
`aidocs/agent-findings/trace3d-spike.md §3` for the planned per-frame
envelope (`{t, x, y, z, v}`) that the resolver will return once
VIS-S1 lands. The Vitest suite at
`frontend/tests/unit/trace3dView.test.ts` pins the current flat-array
adapter shape.

---

## Frontend mount points

| Component | Path | Notes |
|---|---|---|
| `Trace3DView.vue` | `frontend/components/container/timeseries/` | The Three.js Line2 renderer. Consumes `xData/yData/zData/valueData` plus `colorScheme` ∈ `heat|cool|viridis`. |
| `Trace3DCanvas.vue` | `frontend/components/shapes/` | Lower-level canvas wrapper (used by the shapes/render route). |
| `Trace3DChannelPicker.vue` | `frontend/components/container/timeseries/` | Vuetify dialog that picks X/Y/Z + colour channels from the focus DO's TS bag. |
| `Trace3DEditChannelsDialog.vue` | `frontend/components/container/timeseries/` | The "edit channel bindings" dialog the create + edit paths share. |
| `frontend/pages/shapes/render.vue` | (page) | Dispatches `renderer = "tresjs" | "trace-3d"` to `<Trace3DView>`. |

Phase 2 (post-VIS-S1) moves these components into the plugin module
and registers them via the to-be-designed frontend-plugin
registration shape.

---

## Cross-references

- `aidocs/16-dispatcher-backlog.md` — **VIS-T1** row (sprint 1 of 2).
  Also: **VIS-S1** (the SPI predecessor), **SPATIAL-V6-ACTIVATE**
  (the spatial substrate that activates after VIS-T1 ships),
  **VIS-V1** / **VIS-X1** / **VIS-C1** / **VIS-F1** (the sibling
  visualisation plugins that follow the same shape).
- `aidocs/agent-findings/trace3d-spike.md` — library shortlist
  (TresJS won), §2 canonical TTL, §3 frame envelope, §4 multi-trace
  pattern.
- `aidocs/semantics/98-shapes-views-and-process-model.md` —
  `TemplateKind = VIEW_RECIPE` definition.
- `aidocs/data/90-spatial-as-temporal-sweep.md` — the substrate the
  Trace3D view consumes when SPATIAL-V6-ACTIVATE flips the
  `SHEPARD_SPATIAL_DATA_ENABLED` flag.
- `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl` —
  the upstream meta-shape this plugin's Trace3DViewShape extends.
- `frontend/components/container/timeseries/Trace3DView.vue` — the
  in-tree renderer the plugin currently delegates to.
- `examples/mffd-showcase/shapes.py` — the LBR iiwa force-torque
  VIEW_RECIPE demonstrator that seeds a Trace3D template against the
  MFFD dataset.

---

## What Phase 2 will add

When VIS-S1 (the `ViewRecipeRenderer` SPI dispatcher) lands, this
plugin gains:

1. A `TraceFrameResolver` implementing the SPI: given a
   `RenderRequest` with channel selectors + time window, returns the
   per-frame envelope (`{t, x, y, z, v}`) by reading from
   TimescaleDB through the existing `BulkChannelDataRequestIO` path.
2. The four `Trace3DView*.vue` components move into
   `plugins/vis-trace3d/frontend/`.
3. A `MeshViewShape` SHACL allow-list entry — same plugin, second
   VIEW_RECIPE for the static glTF / STL surface-mesh viewer (per
   the VIS-T1 backlog row).
4. The Phase-1 → Phase-2 cutover is recorded as a new tracker row
   in `aidocs/34` referencing this Phase-1 row.

---

## SceneGraphPlay — the MAPPING_RECIPE transform (V2CONV-B4)

As of **V2CONV-B4** this plugin also ships the **scene-graph play
transform** — the consumer that dissolved the bespoke `/v2/scene-graphs/*`
namespace into the generic MAPPING_RECIPE mechanism
(`aidocs/platform/191` §decision-2).

- **Shape:** `scenegraph:SceneGraphPlayShape`, IRI
  `http://semantics.dlr.de/shepard/transform#SceneGraphPlayShape`
  (`src/main/resources/shapes/scene-graph-play.shacl.ttl`).
- **Executor:** `SceneGraphPlayTransformExecutor` (a `TransformExecutor`
  ServiceLoader SPI POJO registered via
  `META-INF/services/de.dlr.shepard.spi.transform.TransformExecutor`).

A `MAPPING_RECIPE` template targeting this shape binds:

| Field | Required | Meaning |
|---|---|---|
| `urdfFileReferenceAppId` | yes | singleton FileReference (FR1b) carrying URDF XML — the kinematic tree, parsed on demand |
| `jointTimeseriesReferenceAppId` | no | TimeseriesReference of joint values over time |
| `jointChannelBindings` | no | JSON array `[{joint, channelSelector}]` |

Materializing it via `POST /v2/mappings/{templateAppId}/materialize`
resolves + parses the URDF (a self-contained `UrdfKinematics` parser,
OWASP-secure XML defaults — no new dependency), reads the binding plan,
and returns a `TransformResult.view` **play envelope**
(`{ kind, robotName, rootLink, frames[], joints[], jointChannelBindings,
urdfFileReferenceAppId, playbackStatus }`). `playbackStatus` is
`STATIC_POSE` (no joint TS bound) or `DECLARED` (joint TS bound; live
channel resolution lands with VIS-S1).

The frontend reaches this in-context from a URDF FileReference detail
page ("Create / Open 3D view") and renders the URDF with the existing
`UrdfCanvas` (Three.js urdf-loader). See `docs/reference/scene-graph.md`
in the main docs tree for the full worked example.
