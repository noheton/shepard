# Reference — shepard-plugin-fileformat-thermography (tier-1)

**Status:** alpha · tier-1 metadata-only

Comprehensive reference for the Edevis OTvis tier-1 parser. Each
section answers a single question an operator or power user might ask
when something goes wrong.

## 1. What the plugin does

When a `.OTvis` file lands on a FileContainer, this plugin is invoked
via the `FileParserPlugin` SPI (see
`aidocs/integrations/110 §3`). It:

1. Opens the upload as a POSIX tar archive.
2. Extracts the `content.xml` entry (UTF-16 LE XML).
3. Reads the `<FileInfo>` block.
4. Parses the filename for the `S<n>_M<n>_L<n>_F<n>.OTvis` grid
   pattern.
5. Writes one `:SemanticAnnotation` per discovered field. Acquisition
   annotations are anchored on the FileReference; MFFD grid annotations
   are anchored on the parent DataObject.

The plugin creates no DataObjects, no DataContainers, and never writes
anywhere outside its `AnnotationWriter` callback — see the
DO-sprawl containment rule in `aidocs/integrations/114 §0`.

## 2. Accepted inputs

| Field | Value |
|---|---|
| File extension | `.OTvis` (case-insensitive) |
| MIME type | `application/x-tar` accepted; `application/octet-stream` accepted when extension matches |
| Archive shape | POSIX tar — typically 6–10 MB; must contain `content.xml` |

`.diproj` files are NOT accepted (deliberate — DO-sprawl rule).

## 3. Annotation key reference

### 3.1 `urn:shepard:thermography:*` (FileReference subject)

| Predicate | XML source | Unit / range | Example |
|---|---|---|---|
| `urn:shepard:thermography:frameRate_Hz` | `<FrameRate>` (Hz stripped) | float Hz | `"30"` |
| `urn:shepard:thermography:integrationTime_s` | `<IntegrationTime>` (s stripped) | float seconds | `"0.007"` |
| `urn:shepard:thermography:excitationDevice` | `<ExcitationDeviceSelection>` (canonicalised) | `"halogen" \| "flash" \| "ultrasound" \| "passive"` | `"halogen"` |
| `urn:shepard:thermography:excitationFrequency_Hz` | `<ExcitationFrequency>` (Hz stripped) | float Hz | `"0.015"` |
| `urn:shepard:thermography:excitationAmplitude_pct` | `<ExcitationAmplitude>` (% stripped) | float percent | `"70.00"` |
| `urn:shepard:thermography:excitationSignalType` | `<ExcitationSignalType>` (lower-cased) | `"sine" \| "square" \| ...` | `"sine"` |
| `urn:shepard:thermography:recordingType` | `<RecordingType>` (lower-cased) | `"evaluation" \| "raw" \| ...` | `"evaluation"` |
| `urn:shepard:thermography:resolution` | `<Window>` (W,H projected) | `"<W>x<H>"` | `"1024x768"` |
| `urn:shepard:thermography:conditioningPeriods` | `<ConditionPeriods>` | int | `"1"` |
| `urn:shepard:thermography:acquisitionPeriods` | `<AcquisitionPeriods>` | int | `"2"` |
| `urn:shepard:thermography:campaign` | `<Campaign>` | string | `"MFFD"` |
| `urn:shepard:thermography:moduleName` | `<ModuleName>` | string | `"OTvis"` |
| `urn:shepard:thermography:creatingVersion` | `<CreatingVersion>` | string (Edevis software version) | `"7.0.425.8903"` |
| `urn:shepard:thermography:createdAt` | `<CreationDate>` (ISO-8601 normalised) | ISO-8601 UTC | `"2023-07-02T06:55:41.414Z"` |

### 3.2 `urn:shepard:mffd:*` (parent DataObject subject)

Emitted only when the filename matches the canonical
`S<n>_M<n>_L<n>_F<n>.OTvis` pattern.

| Predicate | Source | Example |
|---|---|---|
| `urn:shepard:mffd:section` | filename group 1 (literal `S<n>`) | `"S4"` |
| `urn:shepard:mffd:module`  | filename group 2 (literal `M<n>`) | `"M13"` |
| `urn:shepard:mffd:layer`   | filename group 3 (literal `L<n>`) | `"L18"` |
| `urn:shepard:mffd:frame`   | filename group 4 (literal `F<n>`) | `"F4"` |

## 4. Behaviour on partial / malformed input

The parser is best-effort by contract — it never throws on a
malformed file:

| Situation | Behaviour |
|---|---|
| Corrupt tar archive | Grid annotations from filename still emit; acquisition annotations skipped. |
| Missing `content.xml` | Grid annotations from filename still emit; acquisition annotations skipped. |
| Malformed XML | Grid annotations from filename still emit; acquisition annotations skipped. |
| Missing XML field | The matching annotation is silently dropped; other fields still emit. |
| Unrecognised excitation-device wording | Falls through as lower-cased original (e.g. `"laser diode stack"`). |
| Filename does not match `S_M_L_F` pattern | All four MFFD grid annotations are dropped; acquisition annotations still emit. |
| Both DataObject and FileReference appIds absent | Zero annotations emitted (nothing to anchor on). |

## 4.5. NDT quality score + plate heatmap (MFFD-NDT-QUALITY-1, 2026-06-02; V2CONV-A7-THERMO, 2026-06-10)

The composite plate-heatmap surfaces through the **generic
`POST /v2/shapes/render`** endpoint (V2CONV-A7-THERMO — the bespoke
`/v2/thermography/*` REST is dissolved). A *file-rooted* render names the
`ThermographyHeatmapShape` IRI + the TIFF `FileBundleReference` appId; the
plugin's `ThermographyHeatmapRenderer` returns the cached grid
(`PlateHeatmapIO`) as its own `application/json` view-model.

**Analysis runs at upload time** via the thermography file-parse side-effect
(per V2CONV-A7-THERMO) — there is no user-triggered analyze REST call. The
per-frame statistics + DataObject-level quality score are computed once on
ingest and cached as `urn:shepard:ndt:*` annotations.

### 4.5.1 Render call (plate heatmap)

```jsonc
// POST /v2/shapes/render   (Accept: application/json)
{
  "shapeIri": "http://semantics.dlr.de/shepard-ui/thermography/transform#ThermographyHeatmapShape",
  "focusFileRefAppId": "0197b6a2-..."   // the TIFF FileBundleReference appId
}
// 200 → PlateHeatmapIO ; 422 (code render.not-analyzed) when never analyzed
```

`200` heatmap grid · `401` unauth · `403` no Read on parent DO ·
`422` (`render.not-analyzed`) bundle never analyzed.

`PlateHeatmapIO`: `width`, `height`, `cells` (row-major `[h][w]` floats,
degrees Celsius), `minTemp`, `maxTemp`, `thresholdTemp`, `frameCount`.

### 4.5.2 Metric math

For each TIFF frame, the service computes (post-calibration, °C):

- `peakDeltaC = max(pixel) − median(pixel)` — hot-spot signal isolated
  from the bulk temperature offset.
- `meanDeltaC = mean(pixel) − median(pixel)` — distribution skew.
- `hotspotIx / hotspotIy` — pixel coords of the hottest pixel (argmax;
  first wins on ties).

The plate-heatmap accumulator is a `float[gridH][gridW]` of running
maxima — each pixel maps to one grid cell via integer division
(`cx = px * gridW / frameW`). The accumulator never holds a frame stack:
each TIFF is decoded, scored, applied to the grid, then discarded.

DataObject-level `qualityScore = 1 − clip(maxPeakDeltaC / thresholdC, 0, 1)`.
A perfectly uniform bundle scores 1.0; a bundle whose worst frame's
peak-delta-C meets or exceeds the threshold scores 0.0. The DO score
is the conservative `min` across multiple thermography bundles attached
to the same DataObject — the worst region surfaces first.

### 4.5.3 Annotations written

All annotations carry `sourceMode = "ai"` (system-generated) and
`confidence = 1.0` (deterministic computation) per the
{@link ChannelUnitInferenceService} convention.

| Predicate | Subject | Value | Source |
|---|---|---|---|
| `urn:shepard:ndt:peak-delta-c` | FileBundleReference | numeric °C | `thermography-analyze` |
| `urn:shepard:ndt:mean-delta-c` | FileBundleReference | numeric °C | `thermography-analyze` |
| `urn:shepard:ndt:hotspot-centroid-x` | FileBundleReference | numeric px | `thermography-analyze` |
| `urn:shepard:ndt:hotspot-centroid-y` | FileBundleReference | numeric px | `thermography-analyze` |
| `urn:shepard:ndt:threshold-c` | FileBundleReference | numeric °C | `thermography-analyze` |
| `urn:shepard:ndt:frame-count` | FileBundleReference | numeric | `thermography-analyze` |
| `urn:shepard:ndt:plate-heatmap-json` | FileBundleReference | encoded grid | `thermography-analyze` |
| `urn:shepard:ndt:quality-score` | DataObject | numeric [0,1] | `thermography-analyze` |

Re-running `analyze` is idempotent: existing `urn:shepard:ndt:*` rows on
the bundle are wiped before the re-write. The DO-level `quality-score`
takes the `min` of the existing value and the new computation so a
second bundle with a worse region replaces an earlier permissive score.

### 4.5.4 Renderer wiring (V2CONV-A7-THERMO)

The analysis + heatmap classes (`ThermographyAnalysisService`,
`OtvisFrameRenderService`, the IO records) live in this plugin (Tier-1,
backend dep). Viewing is exposed through two `ViewRecipeRenderer`
registrations on the generic `/v2/shapes/render` endpoint — no bespoke
plugin REST resource remains:

| Renderer | Shape IRI | Output |
|---|---|---|
| `ThermographyHeatmapRenderer` | `…/transform#ThermographyHeatmapShape` | `application/json` plate-heatmap grid (`PlateHeatmapIO`) |
| `OtvisFrameRenderer` | `…/transform#OtvisFrameShape` | frame catalogue JSON (`params.mode=index`) / frame heatmap PNG (`params.frame`+`params.channel`) |

Both are registered via
`META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer`. The
heatmap renderer reaches the request-scoped analysis service via
`CDI.current()` (it reads cached annotations); the OTvis frame renderer
reads bytes through the render dispatcher's `FocusPayloadResolver`
(V2CONV-A1b E3) — no decode logic is rewritten.

## 5. Out-of-scope (tier-2+)

| Concern | Filed as |
|---|---|
| Frame extraction from `sequence0/f0.bin` (raw IR frames) | `OTVIS-PARSE-2` |
| Frame extraction from `sequence1/*` (lock-in amplitude/phase) | `OTVIS-PARSE-2` |
| OME-Zarr / NeXus FAIR-intermediate output | `OTVIS-PARSE-2` |
| Frontend channel-bound playback | `THERMO-CHANNELS-1` |
| Full Three.js IR-sequence viewer | `OTVIS-VIEW-1` |
| `.diproj` project-manifest support | Not planned (DO-sprawl containment) |

## 6. SPI surface

```java
package de.dlr.shepard.plugin.fileformat.thermography;

public interface FileParserPlugin {
    boolean accepts(String mimeType, String filename);
    int parse(ParseContext ctx);

    interface ParseContext {
        byte[] bytes();
        String filename();
        Optional<String> parentDataObjectAppId();
        Optional<String> fileReferenceAppId();
        AnnotationWriter annotations();
    }

    @FunctionalInterface
    interface AnnotationWriter {
        void write(String subjectAppId, String predicate, String value);
    }
}
```

Implementation: `OTvisParser.accepts(...)` matches on extension
`.OTvis` (case-insensitive) or MIME type `application/x-tar`.

## 6.5 Render surface — decoded-frame viewer (OTVIS-VIEWER; V2CONV-A7-THERMO)

Tier-2 frame extraction (`OTvisFrameExtractor` → amplitude / phase /
raw-calibrated) is surfaced through the **generic `POST /v2/shapes/render`**
(V2CONV-A7-THERMO — the bespoke `/v2/thermography/otvis/*` REST is
dissolved) so the frontend renders the **actual decoded heatmap frames**
(distinct from the §4.5 plate-heatmap on pre-rendered TIFFs). The
`OtvisFrameRenderer` (`ViewRecipeRenderer`) claims the
`OtvisFrameShape` IRI and serves both the frame catalogue and the
per-frame heatmap PNG.

Both calls take a **singleton `FileReference` appId** (FR1b) of the
`.OTvis` archive as `focusFileRefAppId`. The render dispatcher resolves the
bytes via the `FocusPayloadResolver` (V2CONV-A1b E3) and the renderer
decodes with `OTvisFrameExtractor`. Read is enforced against the
reference's parent DataObject upstream of the renderer. The viewer never
sees a path/URL (per the "UI never asks for paths/URLs" rule).

### Frames catalogue — `POST /v2/shapes/render` (`params.mode=index`, Accept: application/json)

```jsonc
// request
{ "shapeIri": "…/transform#OtvisFrameShape", "focusFileRefAppId": "019e7243-…", "params": { "mode": "index" } }
// 200 → ShapesRenderResponseIO; one channelBinding per frame:
//   role=frame index, channelSelector=kind (lockin|raw), unit=defaultChannel
```

The frontend reconstructs the per-frame channel list from the kind
(`utils/otvisViewer.ts → parseFramesIndex` / `channelsForKind`). `401`
unauth · `403` no Read on parent DO · `422` not a decodable OTvis archive.

### Frame heatmap — `POST /v2/shapes/render` (`params.frame`+`params.channel`, Accept: image/png)

```jsonc
// request
{ "shapeIri": "…/transform#OtvisFrameShape", "focusFileRefAppId": "019e7243-…", "params": { "frame": "0", "channel": "phase" } }
```

Colour-mapped heatmap **PNG** (`image/png`) for the named frame:

| Frame kind | valid `channel` | default |
|---|---|---|
| `lockin` | `amplitude`, `phase` | `phase` |
| `raw`    | `temperature`        | `temperature` |

Colour map is applied server-side: **inferno** for magnitude /
temperature; a **cyclic** blue→white→red→blue ramp for `phase` (phase
wraps at ±π, so a discontinuous ramp misleads). Phase is the canonical
NDT defect channel — least sensitive to surface emissivity and uneven
heating.

`200` PNG · `401` · `403` · `422` (bad archive / frame index / channel).
`params.frame` defaults to `0` when omitted.

**Serving-shape note.** v1 re-decodes the bounded archive per request
and renders one PNG. Full OME-Zarr (chunked, multiscale, napari-pannable)
is deferred as `OTVIS-TIER2-OMEZARR-ZARR` — overkill for a "see the
inspection" viewer; the MFFD fixtures are single-frame-per-archive. A
decoded-frame cache keyed by FileReference appId is the follow-up if a
multi-frame archive makes per-request decode expensive.

Frontend: `frontend/components/context/thermography/DataObjectOtvisViewer.vue`
mounts on the DataObject detail page for every `.OTvis` singleton
FileReference (in-context-first entry).

## 7. Compose / install footprint

No sidecars, no external services, no compose changes required for
tier-1. The plugin runs entirely inside the backend JVM. See
`docs/install.md` for the standalone-build status.
