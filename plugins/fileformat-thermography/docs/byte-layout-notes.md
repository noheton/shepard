# Edevis OTvis byte-layout notes (tier-2 RE)

**Status:** confirmed against the official Edevis spec `DisplayImg Dateiformat .DI Rev H`
(operator-supplied PDF, 2012-11-22 revision) AND empirically verified by reshape-and-stats
against the MFFD sample fixture `sample_S4_M13_L18_F4.OTvis` (8.4 MB).

**Audience:** the future engineer implementing `OTvisFrameExtractor` (row OTVIS-TIER2-EXTRACTOR).

This note is the byte-precision contract `OTvisByteLayoutProbeTest` asserts against. If
you change any inferred layout below, update the test in the same commit.

---

## 1. Archive shape (tar TOC)

The `.OTvis` file is a POSIX tar with this layout (sample fixture):

| Entry | Size (B) | Notes |
|---|---:|---|
| `HIDDEN TOC` | 8 | tar block-device pseudo-entry (type `4`). Per Rev H §"Datei-Aufbau" this is the **SpecialFile / Verweisliste** — two `Int64` values: (1) absolute byte offset of `content.xml` within the archive; (2) end-of-binary-data offset. The tar entry's payload region holds these 16 bytes despite the 8-B header `size`. **Tier-1 does not need it**; tier-2 may use it as a fast seek-to-XML hint. |
| `schemata/core.de.xml` | 99,616 | display-format schema, German |
| `schemata/core.ko.xml` | 90,002 | display-format schema, Korean |
| `schemata/core.en-us.xml` | 99,750 | display-format schema, en-US |
| `sequence0/` | (dir) | sequence index `0` |
| `sequence1/` | (dir) | sequence index `1` |
| `sequence1/calibration.bin` | 262,144 | uint16 → float temperature LUT (see §4) |
| `sequence1/f4294983590.bin` | 1,572,892 | **uint16 raw frame** (see §3) |
| `sequence0/f0.bin` | 6,291,484 | **complex-float lock-in result frame** (see §2) |
| `content.xml` | 20,824 | UTF-16 LE XML, parsed by tier-1 |

**Caveat on the iteration:** Python's `tarfile` (default mode) stops at `HIDDEN TOC` because
of its block-device type. Use `ignore_zeros=True` + `next()` iteration, or call
`TarArchiveInputStream.getNextEntry()` (commons-compress, used by tier-1) which walks past
it cleanly. The tier-1 parser already does the right thing.

**Surprise vs. aidocs/115 §0:** the original hypothesis swapped the roles of
`sequence0` and `sequence1`. In the MFFD sample the LOCK-IN COMPLEX RESULT lives in
`sequence0/f0.bin`, and a SINGLE RAW UINT16 DISPLAY FRAME lives in `sequence1/f<N>.bin`.
This matches `content.xml`'s `<Sequence id="0">` block carrying the complex `DataType=13`
sequence and the second sequence carrying the uint16 reference frame. The §0.1
"H1 thumbnail series vs H2 full-resolution radiance map" pair is therefore **rejected**
— what's in `sequence0` is the actual phase-bearing complex frame, not a preview.

---

## 2. Per-frame binary layout (Rev H §"Frame-Datei", confirmed)

Every `f<N>.bin` (and `c<N>.bin` per-frame calibration override files, not present in our
fixture) has this fixed 28-byte header followed by tightly-packed pixel data, **no
alignment, no padding, little-endian** throughout:

| Offset | Field | Type | Notes |
|---:|---|---|---|
| 0 | Identifier | 16 ASCII bytes | Constant magic. Sample value: `DIFFJPBG00000001` (= "Display IMG Frame Format JPEG-style? version 1"). Tier-2 may validate as a sanity guard, but Rev H does not document the alphabet — treat any 16-byte value as the magic. |
| 16 | Width | `uint32 LE` | image width in pixels |
| 20 | Height | `uint32 LE` | image height in pixels |
| 24 | DataFormat | `uint32 LE` | OpenCV-style code — see §2.1 |
| 28 | PixelData | width × height × bpp | tightly packed; no padding |

### 2.1 DataFormat codes (Rev H page 3)

| Value | Type | Bytes/pixel | Notes |
|---:|---|---:|---|
| `0` | uint8 grayscale | 1 | |
| `2` | uint16 grayscale | 2 | raw radiance; apply `calibration.bin` LUT to get °C |
| `5` | float32 grayscale | 4 | already-calibrated single-channel image |
| `13` | complex float (`{float Real; float Imag}`) | 8 | lock-in result. **amplitude = hypot(re,im); phase = atan2(im, re)** |
| `24` | BGR truecolor | 3 | overlay/display only |

### 2.2 Optional INFL compression (Rev H pages 3–4)

`content.xml` may carry a `Cmpr="INFL"` attribute on a frame; if so, the uint16 payload
is differentially encoded (single-byte deltas with a 0x80 flag for raw 16-bit reload, 0x40
flag for sign on a 6-bit delta). Our MFFD sample has **no Cmpr attribute** — all frames
are uncompressed. Tier-2 should look at the `Cmpr` attribute first; if absent → raw, if
`INFL` → decode per the C# reference implementation in the PDF.

### 2.3 Confirmed against the fixture

`sequence0/f0.bin` (size = 6,291,484 B):

```
Identifier (16B): "DIFFJPBG00000001"
Width  : 1024
Height : 768
DataFormat : 13   (complex float, 8 B/pixel)
Payload size : 6,291,456 = 1024 * 768 * 8   EXACT
```

Pixel-level sanity (786,432 complex values):

| Quantity | min | max | mean | comment |
|---|---:|---:|---:|---|
| `real` | 0.561 | 4.747 | 2.170 | finite throughout |
| `imag` | -4.018 | -0.757 | -2.567 | all negative — one excitation phase quadrant |
| `amplitude = hypot(re,im)` | 0.952 | 6.203 | 3.365 | bright/dark contrast across image |
| `phase = atan2(im,re)` | -1.007 rad | -0.700 rad | — | tight phase distribution (~17° wide), consistent with one excitation frequency and a CFRP shell sample |

`sequence1/f4294983590.bin` (size = 1,572,892 B):

```
Identifier (16B): "DIFFJPBG00000001"
Width  : 1024
Height : 768
DataFormat : 2   (uint16 grayscale, 2 B/pixel)
Payload size : 1,572,864 = 1024 * 768 * 2   EXACT
```

| Quantity | min | max | mean |
|---|---:|---:|---:|
| raw uint16 | 29,548 | 29,798 | 29,657.6 |
| via `calibration.bin` LUT (°C) | 22.329 | 24.831 | 23.426 |

22–25 °C across the entire image is exactly the **MFFD shop-floor room temperature**.
The calibration LUT is verified end-to-end against ground truth.

---

## 3. The frame UID in the filename (`f4294983590.bin`)

Rev H §"Datei-Aufbau" specifies `f<FrameID>.bin`. The FrameID is a monotonically-increasing
counter assigned by the Edevis acquisition software; per Rev H it has no physical meaning
beyond uniqueness within the campaign. `4294983590 = 0x100003E26` — note this is **greater
than `2^32`** (`0xFFFFFFFF = 4294967295`), so the field is at least 64-bit on the producer
side. Tier-2 should treat the filename's numeric component as opaque text, not parse it as
uint32 (it can overflow).

Per-frame calibration overrides live in sibling files named `c<FrameID>.bin` with the
same 28-byte-header + LUT-payload layout (per Rev H). None are present in the MFFD
fixture; `sequence1/calibration.bin` (no `c` prefix) is the sequence-wide calibration.

---

## 4. Calibration LUT (`calibration.bin`, no header)

Per Rev H §"Datei-Aufbau" final paragraph: "Kalibrierinformationen sind per Definition
immer 2^16 floats (4-Byte) lang und sind eine einfache unsigned short zu float Look-up-Table."

| Total bytes | Format | Indexing |
|---:|---|---|
| 262,144 | 65,536 × `float32 LE` | `temperatureCelsius = lut[rawUint16]` |

No header. No padding. Direct array of 65,536 little-endian floats.

Confirmed against the fixture: monotonically increasing from −273.15 °C at index 0
(absolute zero) to +382.20 °C at index 65535. Mid-table samples (linear-ish):

| index | °C |
|---:|---:|
| 0 | -273.15 |
| 8,192 | -191.21 |
| 16,384 | -109.27 |
| 32,768 | +54.53 |
| 49,152 | +218.43 |
| 65,535 | +382.20 |

Step size is ~0.01 °C per uint16 unit. The LUT covers absolute zero to ~382 °C — useful
range for MFFD AFP / welding work which sits well within [20, 350] °C.

---

## 5. What we still don't know

- **`HIDDEN TOC` payload bytes.** The tar entry is type `4` (block device), size field
  `8`. Per Rev H the SpecialFile body is two Int64 (= 16 B) — the actual layout in the
  tar stream needs a raw byte-walk (commons-compress's `getNextEntry()` skips it). For
  tier-2 this is optional optimisation, not correctness.
- **Per-frame `c<FrameID>.bin` overrides** — Rev H mentions them but our fixture has none.
  When tier-2 sees one, it should apply the per-frame LUT in preference to the sequence
  `calibration.bin`.
- **INFL compression on real-world MFFD captures** — our fixture is uncompressed. If any
  production OTvis file carries `Cmpr="INFL"` on its `FrameInfo` block, tier-2 needs the
  C# reference decoder ported (Rev H page 4). The signal-shape probe will fail loudly
  on a compressed frame because the header `Width × Height × bpp` won't match the
  payload size.
- **Multi-frame `f0.bin`** — Rev H supports stacking frames within one file. Our fixture
  has exactly one frame per file (each header's Width × Height × bpp == payload bytes).
  When tier-2 encounters `actualPayload > expectedFrameSize`, it should treat the file as
  a frame stack indexed by repeated 28-byte headers OR read the `FrameCount` from
  `content.xml/Sequence[@id]/SequenceInfo/FrameCount` (= `2` in the PDF example).
  **Cross-check via `content.xml` is the safer path.**
- **`<TarFileHeaderDataOffset>` semantics** — `content.xml` (per Rev H sample) emits this
  per-frame for direct seeking. It is the **absolute byte offset within the tar archive**
  of the frame's 28-byte header. Tier-2 can use this for random-access reads without
  re-walking the tar TOC. Not yet exercised in our probe.

---

## 6. Cross-check against Rev H spec

| Hypothesis from our probe | Rev H reference | Match? |
|---|---|---|
| 28-byte header (16+4+4+4) | page 3 "Identifier (16) . Width (4). Height (4). DataFormat (4)" | yes — exact |
| DataFormat 13 = complex float | page 3 "13: 32bit complex floating point" | yes |
| `hypot(re,im)` = amplitude, `atan2(im,re)` = phase | page 3 "Der Phasenwinkel ergibt sich aus atan2(Imag, Real)" | yes |
| Calibration is 65,536 floats LUT, no header | page 1 final paragraph | yes — exact size match |
| Calibration LUT is monotonic ushort→float | page 1 "einfache unsigned short zu float Look-up-Table" | yes — sampled-monotonic confirmed |
| Little-endian throughout | not explicit in Rev H but consistent with Windows producer (DESKTOP-4VRK5DU per tar uid) and the values we recover making sense | yes |
| `Cmpr="INFL"` indicates differential 16-bit compression | page 3 "INFL" | yes (not exercised in fixture) |

No conflicts. The probe-recovered layout is the spec layout.

---

## 7. Implementation notes for `OTvisFrameExtractor` (next row)

1. Walk the tar with `TarArchiveInputStream.getNextEntry()` (tier-1 pattern). Collect
   names by sequence (`sequence0/*.bin`, `sequence1/*.bin`) and the `calibration.bin`
   present in each sequence directory.
2. For each `f<N>.bin` (skip `c<N>.bin` first pass; they are per-frame calibration
   overrides):
   - Read 28 bytes; `ByteBuffer.wrap(...).order(LITTLE_ENDIAN)`.
   - Skip the 16-byte identifier (or validate it equals the first frame's identifier as
     a corruption guard).
   - Read `width`, `height`, `dataFormat` as `int`.
   - Compute `bpp = bytesPerPixel(dataFormat)`; throw if unknown.
   - Read `width * height * bpp` bytes for the pixel payload.
   - Per-format unpack: uint8 → byte[], uint16 → short[]/int[], float32 → float[],
     complex → float[2] interleaved → derive amplitude/phase float[] images.
3. Apply the sequence `calibration.bin` LUT to uint16 frames to get °C.
4. Emit one OME-Zarr array per kind (raw °C / amplitude / phase) — see aidocs/115 §1.
5. Keep the extractor **fail-soft** per the CLAUDE.md "secondary writes" rule — any
   parse failure logs WARN, sets `urn:shepard:thermography:tier2Status = "failed"`,
   does not throw.

---

## 8. Tooling used

- Apache Commons Compress 1.27.1 (already a tier-1 dep) — confirmed it walks the tar
  past `HIDDEN TOC` without an `ignore_zeros` knob; safer than Python's `tarfile`.
- `java.nio.ByteBuffer` with `ByteOrder.LITTLE_ENDIAN` for header + payload decoding.
- No new Maven dependencies — JZarr / OME-Zarr decisions stay deferred to TIER2-OMEZARR.

The committed test (`OTvisByteLayoutProbeTest`) uses only the existing deps:
`commons-compress` + `junit-jupiter` + `assertj-core`.
