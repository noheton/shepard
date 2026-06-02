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

### Binary sample blocks (bytes 16..BOM) — STRUCTURE PARTIALLY DECODED 2026-06-02

The 1 KB to 1 GB region between the envelope header and the trailing
XML manifest holds the actual recorded sample data. **This region is
proprietary and undocumented by Beckhoff.** No ZIP / gzip / lz4 /
zstd / bzip2 magic anywhere; the bytes are uncompressed.

#### Layout decoded so far (verified on `Scope Project_AutoSave_19_04_29.svdx`, 50 MB, fmt-ver 0x0c9673)

```
Offset  Size  Field                                       Value (example)
─────── ──── ─────────────────────────────────────────── ──────────────────────────────
0x10    u32  n_acquisitions                              149   (= manifest AdsAcquisition count)
0x14    u64  data_section_start_offset                   3000  (= 0xbb8; relative to envelope?)
0x1c    u64  first_record_size_field                     156311  (= 0x26297; recurs in every record)
─────── ──── ─────────────────────────────────────────── ──────────────────────────────
0x24    ─    BEGIN per-acquisition index (149 × 20 bytes = 2980 bytes)
```

**Per-acquisition index record (20 bytes each):**

| Sub-offset | Type | Field                  | Notes |
|------------|------|------------------------|-------|
| +0         | u32  | `acquisition_index`    | 1-based, monotonic |
| +4         | u64  | `cumulative_offset`    | end-byte-position of this acquisition's data (relative to envelope or data-section start — see open question below) |
| +12        | u64  | `record_size_const`    | Always `156311` (`0x26297`) in observed files; possibly the **nominal** per-acquisition size, while the actual data size varies per `cumulative_offset` deltas |

**Observed cumulative offsets, first 5 records:**

```
idx=1  cum_off=159,311  (Δ = ?, baseline unknown)
idx=2  cum_off=315,622  (Δ from idx=1 = 156,311) ← matches record_size_const
idx=3  cum_off=471,933  (Δ = 156,311)            ← matches
idx=4  cum_off=628,244  (Δ = 156,311)            ← matches
idx=5  cum_off=784,555  (Δ = 156,311)            ← matches
```

**Strong signal**: the per-acquisition data is uniformly 156,311 bytes.
Total expected data section = 149 × 156,311 = 23,290,339 bytes
+ index header (3000 bytes) + envelope (16 bytes) + XML manifest
(824,944 bytes) ≈ 24,118,283 bytes. **Actual file size: 50,348,082 bytes**
— there's a 26.2 MB gap unaccounted for. Two hypotheses:

1. There are **two index passes**: a 149-record "early" pass at file
   start, plus a second 149-record pass with `156,823`-byte records
   (the irregular deltas observed at higher indices) representing a
   second acquisition cycle. The dataset would be 149 × 2 × ~156k
   bytes ≈ 46 MB, much closer to the 50 MB file.
2. The 156,311-byte chunks contain padding / metadata interleaved
   with the actual samples. Per-acquisition raw sample size could be
   smaller, with the remainder being headers / timestamps / footers.

#### Cross-check against the matched `_parsed.csv` (Pair B)

- `_parsed.csv` has **2053 data rows × 46 channels** = 94,438 samples total.
- Per-acquisition: 2053 / 149 = **13.78 rows per acquisition** — **non-integer**,
  meaning rows do NOT distribute uniformly across acquisitions, OR
  acquisitions overlap, OR the CSV represents only the LATEST sample
  per row across overlapping acquisitions.
- 156,311 bytes / (46 channels × 8-byte double) = **424.76 samples** per
  acquisition per channel. Not clean.
- 156,311 bytes / (46 channels × 4-byte float) = **849.51 samples** per
  acquisition per channel. Not clean either.
- The acquisition record size likely includes a per-record header.
  Conjecture: `record = 24-byte header + (840 × 46 × 4)` = 24 + 154,560
  = 154,584 bytes payload + 1,727-byte trailer = 156,311. Not yet
  cross-validated.

#### Format-version sub-grouping

Among the 21 campaign files, observed format-version low bytes:
`0x71`, `0x73`, `0x6d`. Initial hypothesis: each version family may
have a slightly different layout. **Pair A** (`19_03_26.svdx`,
fmt 0x73) and **Pair B** (`19_04_29.svdx`, fmt 0x73) share the same
header shape; we have not yet diffed against an `0x71` or `0x6d` file.

#### What still needs to be verified

1. **Index-record byte boundaries**: are records 20 bytes (decoded
   above) or 24 bytes (AAB1's initial seed observation)? Memory walk
   strongly suggests 20, but final 49 records (indices 100-149)
   should be checked for alignment drift.
2. **Sample data layout per acquisition**: is the 156,311-byte block
   raw samples + small header, or do per-record metadata + variable
   sizes alternate? Need to dump one full acquisition block + cross-
   reference against CSV rows.
3. **The 26.2 MB gap**: between expected (149 × 156k = 22 MB) and
   actual (50 MB) file size, what is the second 26 MB block? Walk the
   bytes after acquisition 149's end-offset and look for a second
   index header.
4. **CSV-to-binary row mapping**: 2053 CSV rows ÷ 149 acquisitions =
   13.78 rows/acquisition (non-integer). Is the CSV row #N the last
   sample of acquisition #floor(N×149/2053)? Or is the CSV the
   union of all acquisitions' final values?

The first three questions are now answerable in 1-2 hours of focused
work given the structure decoded above. The CSV-to-binary mapping is
the trickier one and probably needs synthetic test data (a small
.svdx generated by a known TwinCAT install).

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
