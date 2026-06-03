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

## 4.5. NDT quality score + plate heatmap (MFFD-NDT-QUALITY-1, 2026-06-02)

A complementary surface ships in the main backend (not in this plugin —
see "Why in-tree" below): `POST /v2/thermography/analyze` and
`GET /v2/thermography/{imageBundleAppId}/plate-heatmap`. These compute
per-frame statistics on TIFF bundles and surface a DataObject-level
quality score for the MFFD upper-shell NDT use case.

### 4.5.1 Endpoints

| Verb | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/v2/thermography/analyze` | `AnalyzeRequestIO` | `AnalyzeResultIO` |
| `GET`  | `/v2/thermography/{imageBundleAppId}/plate-heatmap` | — | `PlateHeatmapIO` |

`AnalyzeRequestIO`:

```json
{ "imageBundleAppId": "0197b6a2-...", "thresholdC": 80.0, "gridWidth": 64, "gridHeight": 64 }
```

`thresholdC`, `gridWidth`, `gridHeight` are optional; deploy defaults
(`shepard.v2.thermography.threshold-c=80.0`,
`shepard.v2.thermography.grid-width=64`,
`shepard.v2.thermography.grid-height=64`) apply when omitted.

`AnalyzeResultIO` summary fields: `framesAnalyzed`, `framesSkipped`,
`maxPeakDeltaC`, `meanOfMeanDeltaC`, `maxC`, `thresholdC`, `qualityScore`,
`hotspotCentroidX`, `hotspotCentroidY`, `annotationsWritten`.

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

### 4.5.4 Why in-tree, not in this plugin

This plugin is currently standalone (NOT wired into the backend
aggregator) because of the Quarkus / Jandex `CompositeIndex` hang in
the main backend — see `OTVIS-WIRE-AGGREGATOR-1`. Plugin-resident
classes cannot serve a `/v2/` REST endpoint until that's fixed.

The MFFD-NDT-QUALITY-1 implementation therefore lives at
`backend/src/main/java/de/dlr/shepard/v2/thermography/` so it can be
called immediately. A follow-up row `MFFD-THERMO-MOVE-TO-PLUGIN-1`
will relocate the code into this plugin once
`OTVIS-WIRE-AGGREGATOR-1` lands. The wire shape (REST paths +
annotation predicates) does not change with the move — operators won't
notice the transplant.

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

## 6.5 REST surface — decoded-frame viewer (OTVIS-VIEWER)

Tier-2 frame extraction (`OTvisFrameExtractor` → amplitude / phase /
raw-calibrated) is surfaced over REST so the frontend renders the
**actual decoded heatmap frames** (distinct from the §4.5
plate-heatmap, which works on a `FileBundleReference` of pre-rendered
TIFFs). The endpoints live on the existing
`de.dlr.shepard.v2.thermography.resources.ThermographyV2Rest`
(`/v2/thermography`).

The backend depends on this plugin as a **plain-Java jar** (the
extractor has no Quarkus/CDI surface, so — like
`shepard-plugin-fileformat-svdx` — it does **not** worsen the Jandex
`CompositeIndex` hang that blocks `OTVIS-WIRE-AGGREGATOR-1`). The
extractor is invoked directly; the `FileParserPlugin` SPI shim stays
unused until the aggregator wire-up lands.

Both endpoints take a **singleton `FileReference` appId** (FR1b) of
the `.OTvis` archive. The backend resolves the bytes via
`SingletonFileReferenceService.getPayload(appId)` and decodes with
`OTvisFrameExtractor`. Read is enforced against the reference's parent
DataObject. The viewer never sees a path/URL (per the
"UI never asks for paths/URLs" rule).

### `GET /v2/thermography/otvis/{fileReferenceAppId}/frames`

Frame index, no pixel data:

```jsonc
// 200 OK
{
  "fileReferenceAppId": "019e7243-f995-7914-be80-53e367aa5172",
  "width": 1024, "height": 768, "frameCount": 2,
  "frames": [
    { "index": 0, "kind": "lockin", "channels": ["amplitude","phase"], "defaultChannel": "phase" },
    { "index": 1, "kind": "raw",    "channels": ["temperature"],        "defaultChannel": "temperature" }
  ],
  "partialReason": null   // non-null → fail-soft tolerance notes (unknown DataFormat, truncation, …)
}
```

`200` ok · `401` unauth · `403` no Read on parent DO · `404` no
singleton FileReference with that appId · `422` not a decodable OTvis
archive.

### `GET /v2/thermography/otvis/{fileReferenceAppId}/frames/{n}?channel=...`

Colour-mapped heatmap **PNG** (`image/png`) for frame `n`:

| Frame kind | valid `channel` | default |
|---|---|---|
| `lockin` | `amplitude`, `phase` | `phase` |
| `raw`    | `temperature`        | `temperature` |

Colour map is applied server-side: **inferno** for magnitude /
temperature; a **cyclic** blue→white→red→blue ramp for `phase` (phase
wraps at ±π, so a discontinuous ramp misleads). Phase is the canonical
NDT defect channel — least sensitive to surface emissivity and uneven
heating.

`200` PNG · `401` · `403` · `404` · `422` (bad archive / frame index /
channel).

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
