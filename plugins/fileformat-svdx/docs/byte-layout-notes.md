---
stage: feature-defined
last-stage-change: 2026-06-02
---

# `.svdx` byte-layout notes

Reverse-engineering notes for the Beckhoff TwinCAT Scope project
container format. **There is no Beckhoff-published specification for
this format.** This document records what the
`shepard-plugin-fileformat-svdx` parser observed across 21 real files
from the DLR ZLP MFFD AFP ultrasonic-spot-welding campaign on
2023-03-20. It is the seed for `MFFD-PLUGIN-SVDX-BINARY-PARSER-1`.

## What's solved (and shipped in tier-1)

### Envelope header (bytes 0..15)

The first 16 bytes of every observed `.svdx` follow a stable shape:

| Range | Field | Decoding |
| --- | --- | --- |
| 0..7  | XML manifest BOM pointer | `uint64 LE`, value = byte index of the 3-byte UTF-8 BOM (`EF BB BF`) that precedes the trailing `<ScopeProject>` XML manifest. Verified on every file: header value + 3 + 824,938..824,942 bytes ≈ file size. |
| 8..15 | Format-version stamp | `uint64 LE`, observed values: `0x000000000c9671`, `0x000000000c9673`, `0x000000000c966d`. The high four bytes are constant zero; the low four are `00 71 96 0c` / `00 73 96 0c` / `00 6d 96 0c` in little-endian byte order. Byte 8 varies per scope-tool session (likely the build-minor); bytes 9..11 are the stable magic marker `0x96 0x0c 0x00`. |

Hex dump of one file's envelope:

```
000000 67 a3 61 00 00 00 00 00 71 96 0c 00 00 00 00 00
       |---- bytes 0..7 ----|  |---- bytes 8..15 ---|
       0x0000000000061a367      0x000000000c9671
       BOM offset = 6,398,823   format-version stamp
```

This much is decoded reliably by `SvdxEnvelope.tryDecode()`.

### Trailing XML manifest (bytes BOM..EOF)

A complete `<ScopeProject AssemblyName="TwinCAT.Measurement.Scope.API.Model">`
XML document sits in the tail. Stable size across the campaign:
**~824,940 bytes** for every file regardless of binary section size
(the same `AutoSave` config is serialised every time). The parser
streams it via StAX (`SvdxManifestExtractor`) and extracts:

* project Guid, Name, MainServer (AMS NetId of the ADS host),
  RecordTime, AutoSaveMode;
* DataPool Guid;
* per-`<AdsAcquisition>` SymbolName, DataType, AmsNetId, TargetPort,
  Name (the symbolic display name);
* per-`<Channel>` Name (the chart-channel display name);
* deduplicated AmsNetId / port / data-type lists.

These flow out as `urn:shepard:svdx:*` annotations on the parent
FileReference. See `reference.md` for the full predicate catalogue.

## What's NOT solved (deferred to MFFD-PLUGIN-SVDX-BINARY-PARSER-1)

### Binary sample blocks (bytes 16..BOM) — INDEX STRUCTURE DECODED 2026-06-02 (pass 2)

The region between the envelope header and the trailing XML manifest
holds the recorded sample data, fronted by an **acquisition index**.
**This region is proprietary and undocumented by Beckhoff.** No ZIP /
gzip / lz4 / zstd / bzip2 magic anywhere; the bytes are uncompressed.

> **Pass-1 correction (2026-06-02):** the earlier note claimed a
> *uniform* 156,311-byte per-acquisition stride and an unexplained
> "26 MB gap". Both were artifacts of reading only the first 5 index
> records, whose sizes happened to be equal. Record sizes are
> **variable**; with variable sizes the cumulative offset reaches
> 49.3 MB by record 148, so there is **no gap** — the file is almost
> entirely data + a ~1 MB trailing XML manifest. Superseded below.

#### Header + index layout (verified on `Scope Project_AutoSave_19_04_29.svdx`, 50,348,082 B, fmt-ver low byte 0x73)

```
Offset  Size  Field                                       Value (example)
─────── ──── ─────────────────────────────────────────── ──────────────────────────────
0x10    u32  n_acquisitions                              149   (= manifest AdsAcquisition count)
0x14    u64  data_section_start                          3000  (= 0xbb8; abs file offset where acq #1 data begins)
0x1c    u64  size_of_record_1                            156311  (= 0x26297; on-disk byte size of acquisition #1)
─────── ──── ─────────────────────────────────────────── ──────────────────────────────
0x24    ─    BEGIN acquisition index: 148 × 20-byte records, then a u32 trailer
0xbb4   u32  trailer = 149                               (= n_acquisitions, little-endian 95 00 00 00)
0xbb8   ─    BEGIN acquisition #1 data (== data_section_start)
```

The index region is `data_section_start - 0x24 = 2964` bytes =
**148 records × 20 bytes + a 4-byte u32 trailer (`149`)**. So the
index physically stores **148** records; the 149th acquisition's
size is implied (file_tail − cum_off[148], see below). The trailing
u32 repeats `n_acquisitions`.

**Acquisition index record (20 bytes each):**

| Sub-offset | Type | Field               | Semantics (verified) |
|------------|------|---------------------|----------------------|
| +0         | u32  | `acquisition_index` | 1-based, strictly monotonic 1..148 |
| +4         | u64  | `cumulative_offset` | **absolute end offset** of this acquisition's data. Acq *i* data spans `[cum_off[i-1], cum_off[i])`; acq #1 spans `[data_section_start, cum_off[1])` and `cum_off[1] − data_section_start == size_of_record_1` ✓ |
| +12        | u64  | `next_record_size`  | on-disk byte size of acquisition **i+1** (look-ahead). Verified: `next_record_size[i] == cum_off[i+1] − cum_off[i]` for all i. Size of acq #1 is the header field at 0x1c. |

The "+12 = next record's size" semantics is what produced the 24
apparent delta≠size "mismatches" in the naive check — they are exactly
the records where the size changes between neighbours (off-by-one), not
errors. Where consecutive acquisitions share a size, delta==field
trivially.

**Verified invariants on the 50 MB file:**

- `cum_off[1] − data_section_start == size_of_record_1` (3000 + 156311 = 159311 ✓)
- `next_record_size[i] == cum_off[i+1] − cum_off[i]` for all 0 ≤ i < 147 ✓
- acquisition_index monotonic 1..148, no drift ✓
- sizes are **variable** (observed 132,797 / 156,311 / 201,887 / 202,199 / 203,135 …) — *not* a constant
- `cum_off[148] = 49,321,248`; file size 50,348,082 ⇒ trailing 1,026,834 B = acq #149 data **+** XML manifest
- trailing XML manifest (`<Unit>…<ReturnText> (None) </ReturnText>…`) begins ≈ file offset 50,148,087 (last ~200 KB)

This **resolves former open Q1 (boundary = 20 B confirmed) and Q3 (no
26 MB gap — variable sizes account for the full file).**

#### Companion CSV structure (matched Pair B)

Native export `Scope Project_AutoSave_19_04_29.csv` (TwinCAT Scope
Export Tool output, **tab-separated**, 2079 lines):

```
Name<TAB>Scope Project_AutoSave_19_04_29
File<TAB>D:\autosave\Scope Project_AutoSave_19_04_29.csv
Starttime of export<TAB>133238090634380000<TAB>Montag, 20. März 2023<TAB>19:04:23.438
Endtime of export  <TAB>133238090654910000<TAB>Montag, 20. März 2023<TAB>19:04:25.491
```

- Export window = 19:04:23.438 → 19:04:25.491 = **2.053 s**.
- `_parsed.csv` (cleaned, **semicolon-separated**, German decimal comma)
  = 2054 lines = **2053 data rows × 46 channels**. 2053 rows over
  2.053 s ⇒ **~1 kHz** export resampling.

#### Acquisition = CHANNEL (decoded 2026-06-02, pass 3) — FORMAT CRACKED

The trailing XML manifest contains **exactly 149** `<SymbolName>` /
`<DataType>` entries — equal to `n_acquisitions`. **Each acquisition is
one channel's full recording, not a time chunk.** DataType histogram
across the 149 channels: `INT32×103, INT16×22, REAL32×15, REAL64×6,
BIT×2, UINT64×1`. Channel #1 = `GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1`
(INT16); in `_parsed.csv` that column is railed at `32767` (= INT16
max, `0x7FFF`, disconnected sensor) on every row.

**Proof the model is right:** acq #1's data block (bytes 3000..159311)
contains `0xFF 0x7F` (32767 LE) **23,412 times**, and the dominant gap
between markers is **6 bytes** (19,365 of them). So:

**Per-sample record (INT16 channel) = 6 bytes:**

| Sub-offset | Type  | Field | Notes |
|------------|-------|-------|-------|
| +0 | `<DataType>` value | sample value | width = channel DataType (INT16→2 B, REAL64→8 B, …) |
| +2 | u32 | `tick` | timestamp, **100 ns units**, relative to acquisition start |

Decoded ticks in a steady region step by exactly **10000** (`100000,
110000, 120000, 130000, 140000…`) ⇒ 10000 × 100 ns = **1 ms → 1 kHz**,
matching the CSV resample rate. ✓

**Per-channel block header (~355 B)** begins with ASCII version string
`"01.00.00.40"` then carries three Windows `FILETIME`s — **validated
byte-for-byte against the CSV**:

| Header offset | FILETIME | Decoded (UTC) | Meaning |
|---------------|----------|---------------|---------|
| +11 | `0x01d95b565c084060` | 2023-03-20 18:04:05.350 | acquisition START |
| +27 | `0x01d95b5666d042e0` | 2023-03-20 18:04:23.438 | = CSV `Starttime of export` ✓ |
| +35 | `0x01d95b5668098630` | 2023-03-20 18:04:25.491 | = CSV `Endtime of export` ✓ |

Both window timestamps match the CSV's exported `133238090634380000` /
`133238090654910000` FILETIMEs exactly. This nails the absolute-time
base: per-sample wall-clock = `FILETIME(@+11) + tick × 100 ns`.

This **resolves former Q2 (sample layout) and Q4 (CSV↔binary mapping)**.

#### Format-version sub-grouping

Among the 21 campaign files, observed format-version low bytes:
`0x71`, `0x73`, `0x6d`. **Pair A** (`19_03_26.svdx`) and **Pair B**
(`19_04_29.svdx`) are both `0x73` and share the header shape above.
Still to diff: an `0x71` and a `0x6d` file to confirm version-stability.

#### Segment sub-headers — DECODED 2026-06-02 (pass 4)

Each channel block is a **per-channel header** then a sequence of **fine
segments**. A segment is a 12-byte header followed by a short sample run:

```
Segment header (12 bytes):
  +0   u16  (the PREVIOUS segment's last sample value — NOT a marker;
            0x7FFF on the railed temp channel, 0x3506 on ch2 — do not key off it)
  +2   u64  FILETIME      absolute segment start (Windows 100 ns epoch)
  +10  u16  count         samples in this segment (16 observed)
  +12  ──   count × [value][u32 tick]   tick restarts at 0 each segment
```

Verified on two INT16 channels: **1291 segments × 16 = 20,656 samples**,
segment FILETIMEs stepping a uniform 16 ms, spanning **20.655 s**.
Absolute sample time = `segmentFILETIME + withinSegmentTick`, so a
globally-monotonic series is recovered by adding `(segmentFILETIME −
acqStart)` to each within-segment tick — implemented in
`SvdxBinaryParser.decodeSegments` and validated end-to-end (ch1: t0=0 →
tN=206,550,000 = 20.655 s).

There is also a **coarse FILETIME index table** earlier in the block
(stride 12, stepping 4096 ms ≈ one entry per 4096 samples) — a seek
index the decoder ignores (the 0-based constant-period sample-run check
rejects it).

#### Record layout differs by value width — ALL DTYPES DECODED 2026-06-02 (pass 5)

The per-sample record carries a `u32` tick plus the value, but the **order
depends on the value width**:

```
narrow value (< 4 bytes: INT16, BIT):   [ value ][ u32 tick ]
wide  value (>= 4 bytes: INT32, REAL32,  [ u32 tick ][ value ]
            REAL64, UINT64):            (tick leads so value stays aligned)
```

Earlier passes only handled the narrow form, which is why 125 of 149
channels (every INT32/REAL32/REAL64/UINT64) silently produced nothing.
`valueOffsetInRecord(width)` now encodes the order.

**Per-sample timing comes from the FILETIMEs, not the stored tick.** The
stored per-sample tick is a plain `u32` (100 ns) on narrow channels but a
fixed-point value (≈ real_tick × 65536, with low-bit noise) on wide ones,
so it is not a reliable cross-dtype clock. Instead each sample is timed by
the **FILETIME-derived cadence**: `spacing = (nextSegmentFILETIME −
thisSegmentFILETIME) / count`, and `tick = (segFILETIME − acqStart) +
round(j · spacing)`. This is dtype-agnostic and also absorbs the
different sample rates (1 kHz analog vs ~50 kHz `Sound.kanal_*` audio).

**Segments are located by a contiguity chain** — a 12-byte header is
accepted only when its successor (at `offset + 12 + count·recSize`) is
also a valid header (FILETIME in range, sane count), or it is the block's
final segment. This replaced the constant-tick-period guard (which only
worked for narrow channels) and still rejects coincidental
FILETIME-range matches by single wide values.

**Result:** all **149/149 channels** of the 50 MB reference file decode to
a fully monotonic series — **5,015,677 samples** across all six data
types, including the two ~50 kHz `Sound.kanal_*` audio channels
(1,032,096 samples each).

#### What still needs to be verified (remaining)

1. **Engineering-unit scaling**: raw values are returned as stored; the
   manifest's per-channel `<ScaleFactor>`/`<Offset>` must be applied to
   recover physical units (N, °C, MPa, …). Tracked on
   `MFFD-PLUGIN-SVDX-BINARY-PARSER-1`.
2. **Per-channel header length**: fine segments begin well into the block
   (~16.9 KB on the 50 MB ch1), after the version tag, 3 FILETIMEs and the
   coarse index table; the decoder locates the first segment by scan +
   contiguity chain rather than a pinned offset.
3. **Cross-version stability**: diff `0x71` / `0x6d` headers against the
   `0x73` layout above.

The closest community tool, [`pytcs`](https://github.com/CagtayFabry/pytcs)
([PyPI](https://pypi.org/project/pytcs/)), **explicitly works on
CSV/TXT exports only** and acknowledges the binary as "a specific,
non-disclosed data format". The pytcs maintainers recommend running
the [TwinCAT Scope Export Tool](https://infosys.beckhoff.com/content/1033/te13xx_tc3_scopeview/1022949131.html)
to convert SVDX to CSV out-of-band.

### Sample-timestamps

The `<StartTimeStamp>` and `<EndTimeStamp>` XML elements observed in
every campaign file carry literal `0` (the elements are reserved but
unused in the `<DataPool>` / `<Channel>` layer). Real recording-window
timestamps are believed to live in the per-acquisition binary record
headers. Confirming this is part of the binary-parser research row.

### Trigger groups (149 acquisitions for 46 chart channels)

The MFFD files contain 149 `<AdsAcquisition>` elements but only 46
rendered `<Channel>` elements. Hypothesis: TwinCAT Scope captures the
same logical channel under multiple trigger groups (each acquisition
is one (channel × trigger-group) pair). Confirming the channel ↔
acquisition cardinality model requires either binary parsing or a
controlled-acquisition test run with a known trigger configuration.

## Header decoding (worked example)

For `Scope Project_AutoSave_18_26_04.svdx` (7,223,768 bytes total):

```
file size                = 7,223,768
header[0..7] uint64 LE   = 6,398,823    (BOM offset)
header[8..15] uint64 LE  = 0x000000000c9671
expected XML body offset = 6,398,826    (BOM + 3 BOM bytes)
tail XML body size       = 7,223,768 - 6,398,826 = 824,942 bytes
```

The literal byte at offset 6,398,823 is `0xEF`; bytes 6,398,824..826
are `0xBB 0xBF` (UTF-8 BOM). Bytes 6,398,827..831 are `<?xml`. The
tail then terminates at EOF with `</ScopeProject>`.

## How to extend the parser

* `SvdxEnvelope.tryDecode()` — central magic check. Add new
  format-version build bytes here if a future TwinCAT version emits
  a different middle-byte pattern.
* `SvdxManifestExtractor.walk()` — StAX visitor. Add new
  acquisition-level fields (e.g. `<IndexGroup>`, `<IndexOffset>` if
  they become useful for binary-section discovery) by extending the
  `isDirectChildOf(path, ADS_ACQUISITION_ELEMENT)` switch.
* `SvdxAnnotations` — predicate IRI catalogue; add a new constant +
  documentation row when adding any new annotation kind.

## Fixture file (smallest in the campaign)

`Scope Project_AutoSave_18_26_04.svdx` (7.2 MB) is the smallest
campaign sample with the full 46-channel / 149-acquisition shape.
`SvdxParserMFFDFixtureTest` reads it from
`/mnt/pve/unas/dump/dataset/Punktschweißungen/` when present and
self-skips otherwise so the build passes on workers without the NAS
mount.

## Survey of the 21 campaign files

| Folder | Files | Size range | Format-version mode |
| --- | --- | --- | --- |
| `Punktschweißungen/` | 21 | 7 MB .. 1.4 GB | mostly `0x0c9671`, partly `0x0c9673`, one `0x0c966d` |

Earlier task framing referenced 166 `.svdx` files in three folders
(`Punktschweißungen`, `Scope_Sicherung`, `Stringer_schweissungen`).
The actual mount has **21 files in one folder** — see PR description
for the count discrepancy. Plugin behaviour is identical for any
single-folder count; the bulk-import row
(`MFFD-PLUGIN-SVDX-BULK-1`) covers folder-spanning ingest if/when
other folders surface.

## Companion CSV detection

Three of the 21 files have a sibling `.csv` (the TwinCAT Scope Export
Tool output). One file additionally has a `_parsed.csv` — the
semicolon-separated post-processed form a downstream tool produced.
The plugin emits `urn:shepard:svdx:companionCsv` pointing at the
`_parsed.csv` when both are present, falling back to the plain
`.csv`. See `SvdxManifestParser.emitCompanionCsv()`.

## References

* Beckhoff Infosys, [TwinCAT 3 Scope View — Formats](https://infosys.beckhoff.com/content/1033/te13xx_tc3_scopeview/6191506955.html)
* Beckhoff Infosys, [Saving and loading data](https://infosys.beckhoff.com/content/1033/te13xx_tc3_scopeview/955142283.html)
* Beckhoff Infosys, [Automated export](https://infosys.beckhoff.com/content/1033/te13xx_tc3_scopeview/1022949131.html)
* CagtayFabry, [`pytcs` — TwinCAT Scope text-export reader](https://github.com/CagtayFabry/pytcs)
* [`pytcs` Zenodo record 18232962](https://zenodo.org/records/18232962)
