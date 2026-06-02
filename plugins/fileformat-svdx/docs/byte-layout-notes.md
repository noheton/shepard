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

### Binary sample blocks (bytes 16..BOM)

The 1 KB to 1 GB region between the envelope header and the trailing
XML manifest holds the actual recorded sample data. **This region is
proprietary and undocumented by Beckhoff.** Empirical inspection
shows:

* No ZIP central-directory signature (`unzip -l` rejects every file).
* No gzip / lz4 / zstd / bzip2 magic at any offset checked.
* The literal string `01.00.00.40` appears ~149 times in the smallest
  file (= the AdsAcquisition count); appears to be embedded as a
  version stamp inside per-acquisition record headers.
* 24-byte aligned records visible in the first ~256 bytes, with the
  constant `23 51 00 00 00 00 00 00` (`0x5123` LE = 20,771) appearing
  as a recurring sentinel. Hypothesis: per-acquisition record-size in
  bytes, but unverified.
* The XML manifest's `<AdsAcquisition>` blocks carry `<IndexGroup>`
  and `<IndexOffset>` pairs that point into the binary section, but
  no per-acquisition data-start offset is exposed — those would have
  to be inferred by walking the binary headers.

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
