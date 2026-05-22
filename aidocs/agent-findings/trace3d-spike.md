# Trace3D view — spike: library shortlist + view-shape input contract

**Origin:** user idea (2026-05-22, session 33f9b6cd). MFFD AFP TCP thermal-trail
dataset (ETA ~2026-05-26) is the acceptance demo. Frames as the first concrete
`VIEW_RECIPE` per `aidocs/semantics/98-shapes-views-and-process-model.md §2`.

## 1. Library shortlist

| Library | Vue/Nuxt 3 fit | Already in deps | Interactivity ceiling | Setup cost | Isaac bridge |
|---|---|---|---|---|---|
| **TresJS** (`@tresjs/nuxt` + `@tresjs/core`) | ★★★★★ native | no — new dep | Three.js (high) | medium | direct (glTF / USD via Three) |
| Three.js raw | ★★ no Vue binding; roll own composable | no | Three.js (high) | high | direct |
| ECharts-gl (`echarts-gl` 3d-scatter/3d-line) | ★★★★ ECharts works in Nuxt | partial — ECharts is in deps already | medium (no shader-level control) | low | none — would need to swap library for Isaac |
| Plotly.js 3D | ★★★ works but bulky | no | low (no custom shaders) | low | none |
| Babylon.js (`vue-babylonjs`) | ★★ smaller Vue ecosystem | no | very high (game engine) | high | direct (Babylon has USD ext) |
| deck.gl | ★★ React-first; Vue story weak | no | high | high | indirect |

### Tradeoff per option

- **ECharts-gl** is the *fastest path to MFFD demo* (already in repo's stack
  via the live-mode chart, no new dep, ~3-day implementation). But task #56
  is "swap ECharts → uPlot for streaming" — investing more in ECharts works
  against that direction, and ECharts-gl has no Isaac bridge.
- **TresJS** is the *best Vue-ergonomic Three.js wrapper* — Nuxt 3 module,
  reactive scene graph, declarative TresCanvas + TresMesh components. Three.js
  underneath means glTF/USD-loader extensions are available, and the Isaac
  renderer-swap story is "load the same channel-data into Isaac via USD" —
  the bridge already exists. Setup cost ~1 week (compose stack untouched;
  Nuxt module + composable + view-shape resolver).
- **Plotly 3D** is the fallback if TresJS adds friction we don't want to pay
  for v1. It would ship a credible 3D scatter/line with the inferno color
  map in ~2 days. But it's a dead-end for Isaac / interactive viz.

### Lean: **TresJS for v1.**

Pays the higher initial setup once, gets a native-Vue scene graph and the
Three.js ecosystem (orbit controls, line2 fat-line shader, the trail
extension, glTF loader for the robot geometry that'll show up in v1.1, USD
loader for Isaac in v1.5). Aligns with the synthesis doc's "renderer URL,
not Java SPI" pattern — the same view-shape instance points its renderer
URL at the in-tree TresJS bundle today and at an Isaac-Lab WebRTC stream
tomorrow.

If the AFP demo is tight on time and TresJS proves a 3-day slip, drop to
ECharts-gl as the v0 shim and re-target TresJS for v1. Decision can wait
until the dataset lands and we know the scale.

## 2. View-shape input contract

This is the `VIEW_RECIPE` template the synthesis doc §2.1 calls for. The
view-shape is a SHACL-style spec; backend resolves channel data + alignment,
returns aligned frames; frontend renderer reads frames + view-shape props.

### TTL (canonical)

```turtle
@prefix sh:      <http://www.w3.org/ns/shacl#> .
@prefix shepard: <https://shepard.dlr.de/ontology#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .

shepard:Trace3DViewShape a sh:NodeShape ;
    shepard:templateKind shepard:VIEW_RECIPE ;
    shepard:rendererUrl "https://cdn.shepard.dlr.de/views/trace-3d/v1/index.mjs" ;
    shepard:rendererIntegrity "sha384-..." ;
    sh:property [
        sh:path shepard:xChannel ;
        sh:datatype shepard:ShepardId ;
        sh:class shepard:TimeseriesChannel ;
        sh:minCount 1 ; sh:maxCount 1 ;
        sh:description "X-position channel (shepardId)"
    ] ;
    sh:property [
        sh:path shepard:yChannel ;
        sh:datatype shepard:ShepardId ;
        sh:class shepard:TimeseriesChannel ;
        sh:minCount 1 ; sh:maxCount 1
    ] ;
    sh:property [
        sh:path shepard:zChannel ;
        sh:datatype shepard:ShepardId ;
        sh:class shepard:TimeseriesChannel ;
        sh:minCount 1 ; sh:maxCount 1
    ] ;
    sh:property [
        sh:path shepard:valueChannel ;
        sh:datatype shepard:ShepardId ;
        sh:class shepard:TimeseriesChannel ;
        sh:maxCount 1 ;
        sh:description "Optional channel mapped to colour (e.g., TCP temperature)"
    ] ;
    sh:property [
        sh:path shepard:colorMap ;
        sh:in ( "viridis" "plasma" "inferno" "magma" "cividis" "turbo" "RdBu" "jet" ) ;
        sh:defaultValue "viridis"
    ] ;
    sh:property [
        sh:path shepard:interpolation ;
        sh:in ( "none" "linear" "catmull-rom" ) ;
        sh:defaultValue "linear"
    ] ;
    sh:property [
        sh:path shepard:alignment ;
        sh:in ( "nearest" "linear-resample" "snap-to-x" ) ;
        sh:defaultValue "linear-resample" ;
        sh:description "How to align timestamps across the 3 (or 4) channels"
    ] ;
    sh:property [
        sh:path shepard:sampleRate ;
        sh:datatype xsd:double ;
        sh:description "Output samples per second; null = use lowest input rate"
    ] ;
    sh:property [
        sh:path shepard:valueRange ;
        sh:datatype shepard:Range ;
        sh:description "Color-map domain; null = auto-scale"
    ] ;
    sh:property [
        sh:path shepard:timeRange ;
        sh:datatype shepard:TimeRange ;
        sh:description "Sub-window of the channels' shared time domain; null = full"
    ] ;
    sh:property [
        sh:path shepard:traces ;
        sh:node shepard:Trace3DTraceSpecShape ;
        sh:description "Multi-trace: one entry per robot in the same scene"
    ] .

shepard:Trace3DTraceSpecShape a sh:NodeShape ;
    sh:property [ sh:path shepard:xChannel ; sh:minCount 1 ; sh:maxCount 1 ] ;
    sh:property [ sh:path shepard:yChannel ; sh:minCount 1 ; sh:maxCount 1 ] ;
    sh:property [ sh:path shepard:zChannel ; sh:minCount 1 ; sh:maxCount 1 ] ;
    sh:property [ sh:path shepard:valueChannel ; sh:maxCount 1 ] ;
    sh:property [ sh:path shepard:label ; sh:datatype xsd:string ] ;
    sh:property [ sh:path shepard:lineWidth ; sh:datatype xsd:double ; sh:defaultValue 2.0 ] .
```

### JSON instance (what a user creates from the template)

```json
{
  "templateKind": "VIEW_RECIPE",
  "templateName": "trace-3d",
  "title": "AFP TCP thermal trail — Tapelaying run 2026-05-26",
  "rendererUrl": "https://cdn.shepard.dlr.de/views/trace-3d/v1/index.mjs",
  "rendererIntegrity": "sha384-...",
  "xChannel": "019eaa01-7000-7c1d-a1b2-c3d4e5f60001",
  "yChannel": "019eaa01-7000-7c1d-a1b2-c3d4e5f60002",
  "zChannel": "019eaa01-7000-7c1d-a1b2-c3d4e5f60003",
  "valueChannel": "019eaa01-7000-7c1d-a1b2-c3d4e5f60004",
  "colorMap": "inferno",
  "interpolation": "linear",
  "alignment": "linear-resample",
  "sampleRate": 30.0,
  "valueRange": null,
  "timeRange": null,
  "traces": []
}
```

## 3. Backend resolver — channel data → aligned frames

The view-render endpoint receives the JSON instance, resolves channels,
returns an aligned-frame envelope:

```
POST /v2/shapes/render
Content-Type: application/json
{ "viewShape": "trace-3d", "inputs": { ...JSON above... } }
```

Response:
```json
{
  "frames": [
    { "t": 0.000, "x": 0.12, "y": 0.05, "z": 0.34, "v": 22.5 },
    { "t": 0.033, "x": 0.13, "y": 0.05, "z": 0.34, "v": 22.6 },
    ...
  ],
  "stats": { "frameCount": 17820, "sampleRateHz": 30.0, "duration": 594.0 },
  "valueRange": [22.4, 41.2]
}
```

Frame envelope is the contract the renderer reads. Same envelope works for
the in-tree TresJS renderer AND for an Isaac Lab renderer — the only thing
that changes is the `rendererUrl` slot in the view-shape instance.

The resolver lives in `de.dlr.shepard.v2.shapes` (same package as
`ShapesValidateRest`). It's the same `POST /v2/shapes/render` endpoint the
API Scrutinizer persona (`aidocs/agent-findings/persona-review-api-scrutinizer.md`)
proposed — Trace3D is just one consumer.

## 4. Multi-trace pattern (multi-robot scene)

The `traces[]` array carries N `Trace3DTraceSpec` instances, one per robot.
The renderer composes them in a single `TresCanvas` with shared orbit
controls + a unified time scrubber sibling widget.

Per `project_trace3d_view.md` open question: lean is `traces[]` array for
"robots in one scene"; separate view-shape instances for "comparison
workspaces" (side-by-side, different scenes).

## 5. Implementation order (rough — pending dataset arrival)

1. **Backend `POST /v2/shapes/render` for Trace3D** — channel resolver +
   alignment + interpolation + color-map domain compute. Ships with the
   API Scrutinizer-recommended endpoint independent of the renderer.
2. **TresJS Nuxt module install + `Trace3DView.vue` component** — reads
   the frame envelope, draws a Line2 with vertex colours, exposes orbit
   controls + time scrubber.
3. **MFFD AFP TCP demo wiring** — once the 2026-05-26 dataset lands, build
   the JSON instance, create as a saved template under MFFD-Dropbox, link
   from the run's DataObject.
4. **(v1.1) glTF robot loader** — drop in the AFP robot geometry so the
   trace draws *at the TCP* not *as the TCP*.
5. **(v1.5) Isaac Sim renderer URL swap** — same view-shape JSON, different
   `rendererUrl` pointing at an Isaac Lab WebRTC stream. No core change.

## 6. Open decisions (not blocking the spike)

- **Sample budget**: AFP TCP at ~30 Hz × 10 min run = ~18k frames. Line2
  fat-line shader handles this. At 1 kHz the budget is 600k — needs LOD
  (mipmapped polyline) or windowed playback. Decide at dataset land.
- **Time scrubber**: own widget or part of the `Trace3DView` component?
  Lean: sibling widget bound to a shared `viewTime` ref so other view-
  shapes in the same workspace can synchronise (e.g., scrub the 3D trace
  + a 2D chart of the same channels at the same `viewTime`).
- **SRI URL hosting**: the renderer module needs an SRI-pinned URL. v1
  hosts via the Shepard backend's static asset path (`/v2/assets/views/trace-3d/v1/index.mjs`)
  with an `sha384-` integrity in the view-shape. Mirror to a CDN later.

## 7. What this proves

This spike proves the synthesis doc's §2 view-shape collapse is real:

- One new endpoint (`POST /v2/shapes/render`) — already on the doc's
  required list, this is one consumer
- One `templateKind` enum value (`VIEW_RECIPE`) — already proposed
- No new node type, no new SPI, no `/v2/views` URL — same view as a
  table view, just a different renderer URL
- The Isaac integration is a swap, not a port

That's the structural payoff of trio-collapse: a 3D digital-twin viz lands
as a configuration over existing primitives, not as a new platform feature.
