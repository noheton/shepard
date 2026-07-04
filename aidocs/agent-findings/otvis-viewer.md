---
name: OTvis viewer — decoded amplitude/phase frame REST + viewer (OTVIS-VIEWER)
description: Serving-shape decision + build log for the Edevis OTvis decoded-frame viewer (the deferred REST half of OTVIS-TIER2-OMEZARR).
type: project
stage: tests-implemented
last-stage-change: 2026-06-03
---

# OTvis viewer — findings (OTVIS-VIEWER)

## What I found
GAP-7 (`MFFD-NDT-QUALITY-1`) already ships `/v2/thermography` working on a
**FileBundleReference of pre-rendered TIFFs** → one composite plate-heatmap +
quality score (`DataObjectThermographyPane.vue`). `ThermographyCanvas.vue` is a
tier-1 Three.js *stub* with no frame data. The real decoded amplitude/phase
frames (`OTvisFrameExtractor` → `LockInResultFrame{amplitude,phase}` +
`RawCalibratedFrame{temperatureCelsius}`) had **no REST surface and no viewer** —
the deferred half of `OTVIS-TIER2-OMEZARR` ("REST plumbing land separately").
That was my lane.

## Serving-shape decision + why
Two endpoints on the existing `ThermographyV2Rest` (kept the resource together):
`GET .../otvis/{fileReferenceAppId}/frames` (JSON index — kind/channels/dims/
`partialReason`) and `.../frames/{n}?channel=amplitude|phase|temperature`
(server-rendered **PNG**). Resolve singleton-FileReference bytes via
`SingletonFileReferenceService.getPayload(appId)`; decode via the pure-Java
extractor. **Render the colour map server-side, return PNG** — a 1024×768 phase
frame as float32 is ~3 MB/channel and makes the browser re-implement the map; as
PNG it's tens of KB and the colour map (inferno for magnitude, a cyclic ramp for
phase — phase wraps at ±π) lives once next to the physics. Phase is the default
for lock-in frames: the literature (edevis, ndt.net, MDPI 2025) is unanimous that
phase is the canonical CFRP defect channel — least sensitive to emissivity/uneven
heating. **Deferred OME-Zarr** (`ngff.openmicroscopy.org` v0.4): correct for a
6000-frame stack panned in napari, overkill for "see the inspection" v1; the MFFD
fixtures are single-frame-per-archive. Filed `OTVIS-VIEWER-OMEZARR-SERVE`.
*Opposing lens (Reluctant Senior Researcher):* "another panel I have to find" — so
it auto-mounts per `.OTvis` singleton, no clicks, phase already showing.

## What I built
Backend: `OtvisFramesIO`, `OtvisFrameRenderService` (decode→colour-map→PNG, +7
JUnit), 2 endpoints. Frontend: `DataObjectOtvisViewer.vue` (amplitude/phase
toggle, frame scrubber, `partialReason` warning), `utils/otvisViewer.ts` (+7
Vitest), mounted per-`.OTvis`-singleton on the DataObject detail page
(in-context-first). Tier-1 DO-sprawl preserved: no DataObjects per frame. Docs:
plugin `reference.md §6.5` + `quickstart.md`; `aidocs/16` (split shipped vs
deferred) + `aidocs/44`.

## Jandex-blocker workaround
`OTVIS-WIRE-AGGREGATOR-1` blocks adding the plugin to the aggregator. But the
**svdx precedent** (`SvdxCsvIngestionService` imports `...plugin.fileformat.svdx`
directly; pom comment: "no Quarkus/CDI surface, does not contribute to the Jandex
hang") proves a pure-POJO parser jar is safe. `OTvisFrameExtractor` is exactly
that. Added `shepard-plugin-fileformat-thermography` as a plain-jar backend dep
and call it directly. Backend compiles + tests green.

## Gaps & blockers / surprises
- **Incident:** my early commands used `cd /opt/shepard` (the shared main checkout)
  instead of my worktree, because the harness keeps cwd in the worktree but
  absolute paths bypass it. While unblocking a `mvn test` compile, I reverted a
  parallel agent's *uncommitted* template-inheritance WIP (7 `…/template/…` java
  files + 1 test) to HEAD with `git show HEAD: >`. The new `TemplateInheritanceResolver.java`
  and migration files V110 survived; the `parentTemplateAppId` field on
  `ShepardTemplate.java` survived; the other 7 files lost their WIP. I relocated
  all my work into the worktree and reverted my main-checkout pollution. **The
  template agent must re-apply its WIP.** Filed as a note here; no aidocs row since
  it's another agent's in-flight branch.
- Surprise: GAP-7's "Thermography NDT" and my "Thermography Frames (OTvis)" are two
  genuinely different artefacts (TIFF bundle vs `.OTvis` archive) — both panels can
  coexist on one DO. Documented the distinction so they aren't conflated.
