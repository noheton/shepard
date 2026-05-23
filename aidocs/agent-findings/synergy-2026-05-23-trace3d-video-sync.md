---
stage: fragment
last-stage-change: 2026-05-23
---

# S-04 — Trace3D × Video × DataBinding: synchronized 3D-trace + camera-PiP

## Synergy

A TresJS 3D trace driven by X/Y/Z + value timeseries is rendered
side-by-side with an HLS video stream from the cell camera. A single
playhead scrubber drives **both**: dragging the timeline scrubs the
TCP path and seeks the video; clicking a point on the path snaps the
video to the matching frame. The DataBinding SSE multiplexer already
ships the timing primitive.

## Elements (named anchors)

- **Spike (audited-by-personas):** Trace3D —
  `aidocs/agent-findings/trace3d-spike.md` (TresJS library shortlist;
  MFFD AFP TCP thermal-trail = acceptance demo).
- **Plugin (shipped):** `shepard-plugin-video` (VID1a) —
  `aidocs/40 §4`, HLS via MediaMTX sidecar.
- **Plugin (designed):** Live Digital Twin —
  `aidocs/data/84-live-digital-twin.md` (DataBinding live mode SSE;
  state multiplexer at 30 Hz max).
- **Design doc (planned VIEW_RECIPE):** `aidocs/semantics/98-shapes-views-and-process-model.md §2`
  (shapes as views; first concrete view recipe).
- **Demo dataset:** MFFD AFP TCP thermal-trail (ETA ~2026-05-26 per
  `project_mffd_seed_demo`).

## Why this is non-obvious

- The Trace3D spike picks TresJS for the *3D path* alone; the spike
  document doesn't mention the video plugin.
- The video plugin (VID1) is designed for "first-class video
  playback" with frame extraction queued; the design doc doesn't
  mention any 3D viewer.
- The DataBinding live-mode SSE primitive (aidocs/84 §1) was
  designed for *streaming* multi-channel state to a digital-twin
  viewer; *replay* mode (same multiplexer, deterministic playhead)
  was a side-note, not a feature.
- The compound view recipe makes all three suddenly more valuable:
  a path without a camera lacks ground truth; a camera without a
  path is hard to query; a multiplexer without two consumers is a
  primitive looking for a use.
- The MFFD AFP TCP thermal-trail is the perfect first dataset
  because it has all three modalities — XYZ pose from the KUKA
  joints, value timeseries from the thermal sensor, and a TCP-
  mounted camera frame stream. The three modalities are already
  time-synchronised by the cell PLC.

## Concrete output

### 1. ViewRecipe shape

```yaml
# views/trace3d-with-video.yaml
view-recipe:
  templateKind: VIEW_RECIPE
  id: trace3d-with-video-v1
  inputs:
    - name: trace
      shapes: [shp:Trace3DInputShape]   # X/Y/Z + value channels
      cardinality: 1
    - name: video
      shapes: [shp:VideoStreamShape]    # HLS playlist URI
      cardinality: 1
    - name: coord-frame
      shapes: [shp:CoordinateFrameShape] # camera-extrinsic, world-frame
      cardinality: 1
  layout:
    - kind: TresCanvasPanel
      ref: trace
      colorMap: inferno
      width: 0.7
    - kind: HlsVideoPanel
      ref: video
      width: 0.3
      pip: true                          # picture-in-picture toggle
  sync:
    - kind: SingleScrubber
      driver: trace.timestamp
      followers: [video.currentTime]
      timezoneAlign: cellLocalTime
      transform:
        # video t=0 corresponds to trace's startTimestamp,
        # plus a per-cell offset stored on the camera DataBinding
        offset: $databinding.video.wallClockOffset
```

### 2. Frontend composable

```ts
// useTrace3DVideoSync.ts
export function useTrace3DVideoSync(
  traceRef: Ref<Trace3D>,
  videoRef: Ref<HTMLVideoElement>,
  syncOffset: Ref<number>,
) {
  const playhead = ref(0);                  // unix ms

  watch(playhead, (t) => {
    traceRef.value.scrubTo(t);
    const videoT = (t - traceRef.value.startTime) / 1000 + syncOffset.value;
    if (Math.abs(videoRef.value.currentTime - videoT) > 0.05) {
      videoRef.value.currentTime = videoT;
    }
  });

  // reverse direction: video timeupdate → playhead
  function onVideoTime() {
    const t = traceRef.value.startTime +
              (videoRef.value.currentTime - syncOffset.value) * 1000;
    if (Math.abs(t - playhead.value) > 50) playhead.value = t;
  }

  return { playhead, onVideoTime };
}
```

### 3. Live-mode upgrade

Same view in live mode uses the DataBinding SSE multiplexer (aidocs/84
§1) — playhead becomes `now()`; the video is the live HLS feed; the
trace appends as data arrives. The replay-vs-live switch is one
boolean — same component.

### 4. Coordinate-frame anchor

The trace path is in the robot base frame; the video is in the
camera frame. The CoordinateFrameTree (`aidocs/data/85`) already
ships the transform graph — the view recipe resolves the path-to-
camera projection via that tree at load time. A tagged point on the
path can be drawn over the video as an AR overlay (v2 feature).

## Real-world use case

**Persona:** MFFD AFP cell engineer at ZLP Augsburg investigating a
2026-05-22 layup with measured TCP-temperature drop on ply 5. Today:
they open the timeseries chart UI, scroll to the relevant minute,
guess what the robot was doing, and walk over to the cell to find
the camera SD card. After this synergy: they open the
`trace3d-with-video` view on the DataObject, scrub to the
temperature drop, see the 3D path of the TCP at that instant AND
the corresponding camera frame — one screen, three modalities.

For PLUTO (DLR satellite mission): the same recipe covers
ground-station telemetry replay — the 3D trace is the orbital
position, the "video" is the camera-feed payload data, and the
scrubber walks the operator through a commissioning window
seamlessly.

For LUMEN TR-004 (the anomaly seed): an X/Y/Z accelerometer trace
plus the test-bench camera plus the vibration channel = the same
view recipe.

## External evidence

- **TresJS official docs / Nuxt module** —
  [tresjs.org](https://tresjs.org) (and `@tresjs/nuxt` on npm)
  Takeaway: declarative Three.js wrapper for Vue 3 with reactive
  scene graph; the chosen library in the Trace3D spike — proven
  fit for the rendering side.
- **HLS.js project / video element `currentTime` semantics** —
  [github.com/video-dev/hls.js](https://github.com/video-dev/hls.js)
  Takeaway: HLS playback honours `currentTime` writes for seek
  within the loaded buffer; sync precision is ~40-100 ms for live
  streams — good enough for the engineer-walk-through use case.
- **W3C *Live Digital Twin* literature (e.g. *Digital Twin in
  the Built Environment*, IEEE 2024)** —
  [ieeexplore.ieee.org search "digital twin sensor video fusion"](https://ieeexplore.ieee.org/search/searchresult.jsp?queryText=digital%20twin%20sensor%20video%20fusion)
  Takeaway: the literature converges on the time-aligned multi-
  modal viewer pattern; what's missing is open-source platforms
  that let researchers compose it without bespoke code. The
  TresJS + HLS + DataBinding combination is that path.
- **IDTA-02008-1-1 *TimeSeriesData* + IDTA-02002 *Digital Nameplate
  for Smart Manufacturing***  —
  [industrialdigitaltwin.org/en/content-hub/submodels](https://industrialdigitaltwin.org/en/content-hub/submodels)
  Takeaway: AAS Submodels carry the coordinate-frame metadata
  industry-wide; reusing the spec (via S-01) means the view
  recipe is reusable across cells with different layouts.

## Effort estimate

**M (medium).** Components:

- Trace3D v1 (path-only, no video) — already on the roadmap, ~1.5
  weeks per the spike.
- Video plugin VID1a — already shipped.
- DataBinding SSE multiplexer — designed (aidocs/84 §1), pending
  implementation ~2 weeks.
- The sync composable + view-recipe wiring — ~3-5 days once the
  three pieces exist.
- Coordinate-frame projection for AR overlay — v2; out of scope for
  v1.

Net incremental over the three components: ~1 week.

## Risk / counter-evidence

- HLS seek precision is buffer-dependent; for fine-grained frame
  scrub you may want WebM or MJPEG. Mitigation: HLS chunks ~2s by
  default; for the use case (engineer walk-through) this is fine,
  but advertise the limit.
- TresJS is a relatively young library (v3.x as of 2025). A breaking
  change in TresJS adds churn. Mitigation: per the Trace3D spike,
  Three.js is the substrate; TresJS is removable if needed.
- Time alignment is the bug factory. Different sources have
  different clock skews; `wallClockOffset` on DataBinding (TM1a
  is the precedent) is the integration discipline. The view
  recipe MUST require an aligned coord-frame to render.
- For very long campaigns (multi-day video), the HLS playlist may
  outgrow the player's tolerance. Mitigation: split into
  per-hour chunks; the view recipe handles the chunk-boundary
  transparently.
