---
stage: tests-implemented
last-stage-change: 2026-06-02
---

# MFFD `TPS raw data.N` line-scan format notes

Reverse-engineering notes captured 2026-06-02 while shipping
`MFFD-SPATIAL-LINESCAN-IMPORTER-1`. Concrete byte-level facts a future
implementer (or a CI format-drift gate) can verify against.

## What the bytes are

```
$ file 'TPS raw data.0' 'TPS raw data.18'
TPS raw data.0:  PNG image data, 1292 x 964, 8-bit grayscale, non-interlaced
TPS raw data.18: PNG image data, 1292 x 964, 8-bit grayscale, non-interlaced
```

```python
>>> from PIL import Image
>>> im = Image.open('TPS raw data.0')
>>> im.format, im.size, im.mode, im.info
('PNG', (1292, 964), 'L', {})
```

| property        | value                                |
|-----------------|--------------------------------------|
| container       | PNG (RFC 2083)                       |
| dimensions      | 1292 × 964 pixels                    |
| bit depth       | 8                                    |
| colour mode     | grayscale (PIL `mode='L'`)           |
| interlacing     | none                                 |
| sample size     | ~570 KB per chunk (PNG-compressed)   |
| chunks per Track | observed: 37 (`TPS raw data.0` … `TPS raw data.36`) |

## Semantic interpretation (operator-confirmed, 2026-06-02)

Each **row** of the PNG is one sensor-measurement instant along the AFP head's
track. The TPS (Tape Placement System) sensor head sweeps a 1292-element
line-scan array; each row of the PNG is one capture of that array.

| axis           | meaning                                       |
|----------------|-----------------------------------------------|
| Y (964 rows)   | time / along-track sweep (0 = earliest)       |
| X (1292 cols)  | sensor element / across-track position        |
| pixel value    | raw measurement intensity (uint8, 0–255)       |

Per operator: "each line in the [TPS raw data] image corresponds to a
measurement of the system in the track". This contradicts the W8d
classification that briefly treated these as process video frames; the
2026-06-02 doc commit (`23ed2a1`) reclassified W8d → W7b.

## Sample statistics

Captured against `Track_66__Run_23133_/files/TPS raw data.0` and `.18`:

| chunk | shape (rows, cols) | dtype | min | max | mean   |
|-------|--------------------|-------|-----|-----|--------|
| .0    | (964, 1292)        | uint8 | 1   | 255 | 57.87  |
| .18   | (964, 1292)        | uint8 | 1   | 255 | 57.51  |

Dynamic range is full 8-bit (1..255 observed, no clipped 0-bin). Two chunks
from the same Track show identical dimensions, supporting the "constant
across the chunk index" assumption the decoder makes.

## Pixel sampling

Row 0, first 5 columns of chunk .0: `[6, 6, 4, 7, 5]`
Row 500, first 5 columns of chunk .0: `[8, 9, 6, 9, 9]`
Pixel `(0, 0)` of chunk .0 = `6`; pixel `(0, 1291)` of chunk .0 = `12`.

(These exact values are not asserted in the test suite — the captured
64×128 crop is the byte-stable regression target.)

## Sibling files in the same `files/` directory

Observed alongside the line-scan PNGs in `Track_66__Run_23133_/files/`:

- `TPS raw data.0` … `TPS raw data.36` — the line-scan chunks (this doc).
- `TPS intermediate evaluation files.N` — companion analysis output per
  raw chunk. **Out of scope** for `MFFD-SPATIAL-LINESCAN-IMPORTER-1`; file
  contents not yet inspected.
- `TPS 3D pointclouds.0`, `TPS 3D pointclouds.1` — pointcloud variants
  handled by the existing `--spatial-pass` (AAC1).
- `FSD course 3D pointclouds` — trajectory file handled by AAC1.
- `Robot program` — KRL.

## Open questions (filed as follow-ups)

- **Per-pixel calibration** (mm per column, mm per row) — likely lives in a
  sidecar `CameraConfig.csv` or in `TPS intermediate evaluation files.*`;
  not surveyed in this pass. Tracked: `MFFD-SPATIAL-LINESCAN-CALIB-1`.
- **Wall-clock timestamp per chunk** — not present in the PNG bytes. Until
  a sidecar surfaces this, the decoder uses row-index-as-time and the
  promoted SpatialDataContainer carries
  `urn:shepard:spatial:t-axis = row-index`. Tracked under
  `MFFD-SPATIAL-LINESCAN-CALIB-1`.
- **16-bit depth variant** — the decoder accepts PIL modes `I;16`,
  `I;16B`, `I;16L` defensively; not observed in the wild, but the rejection
  path documents `MFFD-SPATIAL-LINESCAN-FORMAT-DRIFT-1` so we know what to
  do if it shows up.

## Decode contract

The decoder (`plugins/spatial-importer/cli/linescan.py`) guarantees:

1. **Streaming row-by-row** — `iter_linescan_rows(file)` yields
   `LineScanRow(row_index, intensities)` without loading the whole image at
   once.
2. **SHA256-stable idempotency key** — derived from the PNG bytes, used to
   MERGE on `(dataObjectAppId, kind, source-sha256, chunkIndex)` and skip
   re-uploaded chunks.
3. **Filename → chunk index** — `classify_linescan("TPS raw data.18") == 18`.
4. **Uniform row widths within a chunk** — the helper
   `assert_row_widths_uniform()` is invoked by the test suite to surface
   any future drift from this invariant.

## Regression fixture

`plugins/spatial-importer/tests/fixtures/tps_raw_data_chunk18_64x128.png` —
the upper-left 64 × 128 pixel crop of the real
`Track_66__Run_23133_/files/TPS raw data.18`. SHA256
`874039f6…f2dec6`. The crop preserves the row-as-time semantics in
miniature and lets the test suite run without the 80 GB MFFD tarball
present.

If the source export ever changes format, the byte-level regression test
(`test_fixture_byte_stability`) will fail first.
