---
title: OTvis tier-2 — Edevis frame extraction + lock-in result decoding
stage: feature-defined
last-stage-change: 2026-05-28
audience: contributors, plugin authors
---

# 115 — OTvis tier-2 design

**Status:** feature-defined · companion to [`114-process-monitoring-parser-plugin.md`](114-process-monitoring-parser-plugin.md) · prerequisite for [OTVIS-VIEW-1](../16-dispatcher-backlog.md) (full IR sequence player + lock-in amp/phase + 3D-on-CAD overlay)

**Tier-1 status:** Shipped 2026-05-28 (`OTVIS-PARSE-1`). Tier-1 parses `content.xml` from a `.OTvis` tar and emits ~15 `urn:shepard:thermography:*` + `urn:shepard:mffd:*` annotations on the parent FileReference. Frame data is left untouched.

**Tier-2 scope:** Decode `sequence0/f0.bin` (raw IR sequence) and `sequence1/f<N>.bin` + `sequence1/calibration.bin` (lock-in evaluation result) into FAIR-aligned intermediate formats Shepard can natively visualise and query.

---

## 0. What's in the binary streams

From the single MFZ campaign sample (`93e70381-S4_M13_L18_F4.OTvis`, 8.4 MB tar):

| Stream | Size | Content (hypothesis from `content.xml` parameters) |
|---|---|---|
| `sequence0/f0.bin` | 6.3 MB | Either (a) the raw IR sequence at 30 Hz × 7 ms integration × N frames, OR (b) the **already-evaluated** result frames (`RecordingType = Evaluation` in `content.xml` strongly suggests this). |
| `sequence1/calibration.bin` | 262 KB | Camera radiometric calibration (NUC offsets, gain map, bad-pixel mask, possibly black-body reference points). |
| `sequence1/f<N>.bin` | 1.6 MB | The lock-in result frames — typically amplitude + phase images, one pair per evaluation period. For our sample: 2 acquisition periods × ~1 MB-equivalent per period after de-duplication. |

The file naming `f<N>.bin` where N is a 32-bit epoch-ish ID (`f4294983590 = 0xFFFFFE36`) — Edevis assigns frame UIDs from a counter; the number isn't load-bearing for our parse.

### 0.1 The `RecordingType = Evaluation` distinction

`content.xml` has `<RecordingType>Evaluation</RecordingType>`. Per Edevis OTvis behaviour:

- **`Evaluation`** = the file ships the LOCK-IN RESULT (amplitude + phase images). Raw frames may have been dropped to save storage. `sequence0/f0.bin` would then be the displayed "live" frame at the moment of capture; `sequence1` would hold the actual amp/phase result.
- **`Raw`** would mean the full 30 Hz × ~133 s sequence (~4000 frames × 1024×768 × 2 B ≈ 6 GB) is preserved.

**Our sample is `Evaluation` → 6.3 MB of `sequence0` is too small to be raw frames at full rate; it has to be a smaller representation.**

Two hypotheses for `sequence0/f0.bin` (6.3 MB):
- **H1**: a thumbnail/preview series (e.g. 50 frames at downsampled resolution).
- **H2**: a single full-resolution radiance map (1024 × 768 × `float32` = 3.15 MB), with a second frame for the same campaign period (matches the 6.3 MB total).

Both are workable as "display frames". We don't need to commit to one before tier-2 implementation; the byte layout will tell.

### 0.2 The lock-in result (sequence1)

For our acquisition parameters (1024 × 768 × 2 periods × {amplitude, phase} × float32) → exactly 1024 × 768 × 2 × 2 × 4 = ~12.6 MB uncompressed. The file is 1.6 MB. Difference: either pre-compression in the file, or only ONE period is stored (the "result" period, with the conditioning period dropped) at half precision. Most likely: 1024 × 768 × {amplitude, phase} × float16 = 3.15 MB; with light DEFLATE / no calibration table it'd compress to ~1.6 MB.

The amplitude image is the contrast of the modulated thermal response — defects show as bright/dark spots. The phase image is the time-shift of the response — deep defects show as **phase contrast** even when invisible in the raw thermal. **Phase is the primary defect signal in lock-in active thermography.**

---

## 1. Strategy: convert to FAIR intermediate at parse time, render from intermediate

Rather than persist Edevis binary, convert once to an open standard (OME-Zarr 3D image stack) and let the viewer + future analytics read from there. Two-step:

```
.OTvis upload
    │
    ▼
[Tier-1 OTvisParser (shipped)]
    └─ annotations on FileReference

    │
    ▼ (if shepard.thermography.tier2.enabled = true)
[Tier-2 OTvisFrameExtractor]
    └─ open tar → read sequence0 + sequence1 + calibration
    └─ apply calibration → temperature/amplitude/phase float32 arrays
    └─ write 1 OME-Zarr v3 store at Garage S3 under
       `thermography/{fileReferenceAppId}/`:
         ├─ raw/      (T×H×W single-channel float32 — radiance)
         ├─ amplitude/ (H×W float32 — lock-in amplitude per period)
         └─ phase/     (H×W float32 — lock-in phase per period)
    └─ create ONE new SD-payload entity on the parent DataObject:
       `OTvisDerivedDataset` with `omezarrStoreUrl` + `urn:shepard:thermography:tier2Status = "ok"`
    └─ NO new DataObject (DO-sprawl containment).
```

**Why OME-Zarr v3** rather than HDF5 NeXus or pickle-of-numpy:
- Direct browser-streaming via `zarrita.js` / `ome-zarr-loader.js` → frontend renders chunks on demand, no full-buffer load
- Random-access into multi-dimensional chunks (single frame + single pixel both work without rescanning the whole file)
- Garage S3 backs it natively (no special storage adapter needed); `s3://shepard-payloads/thermography/...` is exactly how the OME-Zarr ecosystem expects it
- The `aidocs/ops/vis-s2-garage-omezarr-storage-policy.md` runbook already covers the bucket layout, CORS config, and signed-URL TTL for this pattern

**The intermediate is canonical**, the Edevis binary is the source archive. After tier-2 completes, the viewer reads from OME-Zarr, not from `f0.bin`. Frame extraction is reversible (the FileReference still holds the original `.OTvis`); the OME-Zarr can be regenerated at any time.

---

## 2. Byte-layout reverse-engineering plan

Tier-2 needs to know how to walk `sequence0/f0.bin`, `sequence1/f<N>.bin`, and `calibration.bin`. We don't have vendor specs. Three-step bring-up:

### 2.1 First-pass: structured probe

Open the binary in Python with `numpy`, try common Edevis layouts:

```python
import numpy as np
with open("sequence0/f0.bin", "rb") as f:
    raw = f.read()

# Hypothesis A: header (N bytes) + (H × W × dtype × N_frames) packed.
# Hypothesis B: per-frame header + payload + per-frame header + payload + ...
# Hypothesis C: raw little-endian uint16 stream, no header (NUC-corrected).
```

For each hypothesis: reshape, check that values fall in a plausible radiance range (e.g. 8000–18000 ADU for an MWIR camera), display as image — does it look like the CFRP shell from the cover image?

Successful reshape → known layout. Persist a Python notebook + a `byte-layout-notes.md` in the plugin module so the next engineer knows what we found.

### 2.2 Second-pass: cross-check against Edevis documentation

Edevis ships sparse format docs but they exist. The `DisplayImg_Dateiformat_Rev_H.pdf` (Revision H of the DisplayImg format) is the on-disk format spec — we already have a copy. Cross-check the binary against any byte-layout tables it documents.

### 2.3 Third-pass: validate

Use a known-good measurement (the MFZ sample). Compute amplitude + phase from the raw frames via the standard 4-bucket lock-in algorithm and compare against `sequence1/f<N>.bin`. They should match within float32 epsilon.

---

## 3. SPI contract (extension of `aidocs/110 §3`)

Tier-2 doesn't change the `FileParserPlugin` interface; it extends `OTvisParser.parse()` to ALSO produce a derived-dataset write callback when tier-2 is enabled:

```java
public interface FileParserPlugin {
    boolean accepts(MimeType mime, String filename);
    void parse(ParseContext ctx);
}

public interface ParseContext {
    byte[] readOid();
    String parentDataObjectAppId();
    String fileReferenceAppId();
    AnnotationWriter annotations();
    DerivedDatasetWriter derived();   // tier-2 surface (new)
    boolean tier2Enabled();           // shepard.thermography.tier2.enabled
}

public interface DerivedDatasetWriter {
    // Create a new SD-payload entity attached to the parent DO. The payload
    // body is the OME-Zarr store URL; the entity carries the
    // urn:shepard:thermography:derivedFrom = <originalFileReferenceAppId>
    // back-pointer.
    void writeOmeZarrDataset(String storeUrl, Map<String,String> annotations);
}
```

The tier-2 flag is admin-runtime (per CLAUDE.md "surface operator knobs"). Default off (tier-1 always runs; tier-2 opts in).

---

## 4. Failure handling

Tier-2 is best-effort: if frame extraction fails (corrupt tar, unknown byte layout, calibration missing), the FileReference retains its tier-1 annotations and gets `urn:shepard:thermography:tier2Status = "failed"` + `urn:shepard:thermography:tier2Reason = "<error>"`. The DataObject stays usable; the viewer falls back to showing tier-1 metadata only (the stub `ThermographyView` already implements this fallback path).

Never throw. Per CLAUDE.md "secondary writes are fire-and-forget" — tier-2 is a secondary write decorating tier-1's primary annotation.

---

## 5. Implementation phasing

| Phase | What | Effort | Acceptance |
|---|---|---|---|
| **5a** | Add `DerivedDatasetWriter` to the local SPI shim in `plugins/fileformat-thermography/`. No-op writer. Wire `tier2Enabled()` plumbing. | S | Tier-1 still passes; tier-2 entry point reachable but does nothing. |
| **5b** | Reverse-engineer byte layout via §2.1 probe. Write `byte-layout-notes.md`. | M | One-page note + a JUnit test that reads the sample's `sequence0/f0.bin` and asserts the recovered radiance range matches a plausible MWIR window. |
| **5c** | Build `OTvisFrameExtractor` — open tar, parse sequences, apply calibration, produce `float32[][]` arrays. | M | JUnit: extract frames from sample, hash deterministically, no exceptions. |
| **5d** | Plug an OME-Zarr writer (Java: `JZarr` BSD-3 OR call a Python sidecar via `subprocess`). | M | OME-Zarr store written to a temp dir; second JUnit reads it back via `zarrita.py` and verifies array shapes + dtype. |
| **5e** | Wire `OmeZarrFrameWriter` → `DerivedDatasetWriter.writeOmeZarrDataset(garageSignedUrl, annotations)`. Garage upload via existing storage SPI. | M | Integration test: end-to-end sample.OTvis → derived dataset on parent DO with valid signed URL. |
| **5f** | Frontend `ThermographyCanvas` swaps placeholder plane for `zarrita.js` OME-Zarr texture loader. Frame scrub control wired. | S | Browser smoke test: open a DataObject with a tier-2 OTvis, see the IR sequence play. |
| **5g** | 3D-on-CAD overlay — drape the lock-in phase image on the upper-shell CAD surface at the (S, M) coordinate. Uses the `CoordinateFrame` entity from `aidocs/data/85` + the grid-position annotations from tier-1. | L | Browser smoke test: open an MFFD Collection with several measured (S, M) tiles, see the false-color phase map stitched across the upper-shell. |

**Total**: roughly 2 sprints. Phases 5a–5e are backend; 5f and 5g are frontend. Phase 5g is the MFFD-demo headline visual.

---

## 6. Dependencies / unknowns

- **Tier-2 needs the backend to build.** As of 2026-05-28 the backend rebuild is wedged on the Jandex hang (`aidocs/agent-findings/backend-jandex-hang-investigation-2026-05-28.md`). Tier-2 backend work starts after that's resolved.
- **JZarr vs Python OME-Zarr sidecar**: JZarr is BSD-3, mature for read but the write path is less battle-tested. A Python sidecar (`numpy + zarr` + FastAPI) is bulletproof but adds a sidecar to declare per `feedback_plugins_declare_sidecars.md`. Decide at 5d.
- **Phase-on-CAD mapping**: needs the 14 × 14 grid positions to be tied to coordinate frames on the upper-shell CAD. The MFFD-WIKI-STRUCT umbrella's equipment-tree sub-row (`C` in `aidocs/integrations/114`?) is the right home. Cross-references with `aidocs/data/85-coordinate-frame-tree.md §7`.
- **Calibration**: `sequence1/calibration.bin` layout is also undocumented; the same §2 RE process applies. Without it, we can still display normalised counts but can't report absolute temperature in degC.

---

## 7. Backlog rows

Tier-2 lands as separate backlog rows so each phase can be picked up independently:

- **OTVIS-TIER2-LAYOUT** (M) — phase 5b: byte-layout RE for sequence0/sequence1/calibration. Output: `byte-layout-notes.md` + 1 JUnit test against the sample fixture.
- **OTVIS-TIER2-EXTRACTOR** (M) — phase 5c: `OTvisFrameExtractor` reading and calibrating frames.
- **OTVIS-TIER2-OMEZARR** (M) — phases 5d + 5e: OME-Zarr writer + derived-dataset wire-up.
- **OTVIS-TIER2-VIEWER** (S) — phase 5f: `zarrita.js` loader + scrub control in `ThermographyCanvas.vue`.
- **OTVIS-TIER2-PHASE-ON-CAD** (L) — phase 5g: the headline 3D overlay (depends on equipment-tree / CoordinateFrame plumbing being in place).

All five rows are queued; OTVIS-TIER2-LAYOUT is the only one that can land **without the backend building** (it's a JUnit + notes exercise in the standalone plugin module).

---

## 8. What this design deliberately does NOT do

- **Does not parse `.diproj` project manifests.** Per the user directive 2026-05-28, the project file is ignored to prevent DO-sprawl. Tier-2 stays single-`.OTvis`-upload-driven.
- **Does not auto-stitch the 14 × 14 grid into a single composite visualisation at upload time.** Stitching is a Collection-level render concern, not a per-upload parse concern. The `MFFD-NDT-GRID` widget (filed separately) is the natural home.
- **Does not extract video from `sequence0` even if it's a full raw sequence.** OME-Zarr is good enough for "play 100 frames in the browser"; MP4 transcoding is a follow-up if operators want shareable clips.
- **Does not normalise temperature against ambient.** The tier-2 output is the raw radiometric + lock-in result; ambient compensation belongs in an analysis recipe, not the parser.

---

## 9. Cross-references

- [`aidocs/integrations/114-process-monitoring-parser-plugin.md`](114-process-monitoring-parser-plugin.md) — the tier-1 design + the broader process-monitoring family (NETZSCH DEA, etc.)
- [`aidocs/integrations/110-file-format-parser-plugin.md`](110-file-format-parser-plugin.md) — the FileParserPlugin SPI baseline
- [`aidocs/data/85-coordinate-frame-tree.md`](../data/85-coordinate-frame-tree.md) — the CoordinateFrame entity, used by phase 5g for 3D-on-CAD mapping
- [`aidocs/ops/vis-s2-garage-omezarr-storage-policy.md`](../ops/vis-s2-garage-omezarr-storage-policy.md) — Garage S3 bucket layout for OME-Zarr
- `plugins/fileformat-thermography/` — the tier-1 module; tier-2 lives in the same module
- `feedback_plugins_declare_sidecars.md` — the sidecar declaration pattern for the optional Python OME-Zarr writer
