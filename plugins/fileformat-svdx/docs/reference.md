---
stage: feature-defined
last-stage-change: 2026-06-02
---

# `shepard-plugin-fileformat-svdx` — reference

Tier-1 reference for the SVDX manifest parser. Audience: power users
and operators who need to know exactly which predicates appear, where
they come from, and what the parser does NOT do.

## Predicate catalogue

All predicates live under the stable `urn:shepard:svdx:*` namespace.
Each is emitted as a `:SemanticAnnotation` on the parent
FileReference's `appId` (the subject of every triple). Cardinality is
one (at most one annotation per FileReference) or many (zero or more,
deduplicated per the column).

| Predicate IRI | Cardinality | Source XML | Notes |
| --- | --- | --- | --- |
| `urn:shepard:svdx:formatVersion` | one | envelope bytes 8..15 | Always emitted on a recognised SVDX even if the manifest parse later fails. Lowercase hex, e.g. `0x000000000c9671`. |
| `urn:shepard:svdx:assemblyName` | one | `<ScopeProject AssemblyName="…">` | Usually `TwinCAT.Measurement.Scope.API.Model`. |
| `urn:shepard:svdx:projectGuid` | one | `<ScopeProject>/<Guid>` | Stable per scope project — multiple files from the same auto-save session share a Guid. |
| `urn:shepard:svdx:projectName` | one | `<ScopeProject>/<Name>` | Display name (e.g. `Scope Project`). |
| `urn:shepard:svdx:dataPoolGuid` | one | `<ScopeProject>/<DataPool>/<Guid>` | One DataPool per ScopeProject in every observed file. |
| `urn:shepard:svdx:mainServer` | one | `<ScopeProject>/<MainServer>` | AMS NetId of the main ADS host (e.g. `127.0.0.1.1.1`). |
| `urn:shepard:svdx:recordTimeNs` | one | `<ScopeProject>/<RecordTime>` | Configured record duration in 100-ns ticks (TwinCAT convention). |
| `urn:shepard:svdx:autoSaveMode` | one | `<ScopeProject>/<AutoSaveMode>` | Usually `SVDX`; can be `CSV` when the project is configured for direct CSV save. |
| `urn:shepard:svdx:channelCount` | one | derived | Decimal count of `<Channel>` elements (rendered chart channels). Always emitted, even when zero, so a query for unparseable uploads can distinguish "no manifest" from "manifest empty". |
| `urn:shepard:svdx:acquisitionCount` | one | derived | Decimal count of `<AdsAcquisition>` elements (ADS data sources; usually higher than `channelCount` because of trigger-group fan-out). |
| `urn:shepard:svdx:amsNetId` | many | `<AdsAcquisition>/<AmsNetId>` | Deduplicated across acquisitions. |
| `urn:shepard:svdx:port` | many | `<AdsAcquisition>/<TargetPort>` | Deduplicated. Typically `851` (TwinCAT runtime task 1). |
| `urn:shepard:svdx:dataType` | many | `<AdsAcquisition>/<DataType>` | Deduplicated. IEC 61131-3 names (`INT16`, `INT32`, `REAL32`, `REAL64`, `BIT`, `UINT64`). |
| `urn:shepard:svdx:channelName` | many | `<Channel>/<Name>` | Per chart-rendered channel. |
| `urn:shepard:svdx:symbolName` | many | `<AdsAcquisition>/<SymbolName>` | Fully qualified TwinCAT symbol path (e.g. `RobotData.rRoboPosA`). |
| `urn:shepard:svdx:companionCsv` | one | derived | FileReference `appId` of a sibling `<basename>.csv` or `<basename>_parsed.csv` in the same FileContainer. The `_parsed.csv` wins when both are present. |

## Detection rule

The parser claims a file when **either**:

* the filename ends with `.svdx` (case-insensitive), OR
* the MIME type equals `application/vnd.beckhoff.scope+svdx`.

When claimed, the parser:

1. Reads the first 16 bytes. If they don't match the envelope magic
   (`SvdxEnvelope.tryDecode`), returns 0 annotations (no false-positive
   emission on an unrelated file with a `.svdx` extension).
2. Locates the trailing XML manifest via the envelope BOM pointer.
3. Streams the manifest through StAX and emits all predicates in the
   catalogue above.
4. Walks the sibling-file list for a companion CSV and emits
   `companionCsv` when found.

Total emissions per real MFFD file: ~250 annotations
(46 channelName + 149 symbolName + ~10 metadata + ~3 dedup-list
entries).

## Tier-2: binary sample decoding (`SvdxBinaryParser`)

`SvdxBinaryParser` decodes the proprietary binary sample region — the
format reverse-engineered and validated in `byte-layout-notes.md`
(`MFFD-PLUGIN-SVDX-BINARY-PARSER-1`). It exposes:

* `decodeHeader(byte[])` → the acquisition-index preamble
  (`n_acquisitions`, `data_section_start`, `size_of_record_1`).
* `decodeIndex(file, header, xmlBomOffset)` → one `IndexEntry` per
  acquisition (each acquisition is **one channel's** recording).
* `decodeChannel(block, idx, symbol, dataType)` → the per-channel
  header timing (three FILETIMEs) plus decoded `Sample(tickNs100,
  value)` records. Each on-disk record is `[value : DataType width]
  [u32 tick · 100 ns]`; the stream is split into **fine segments**
  (a 12-byte `[_][u64 FILETIME][u16 count]` header + a sample run whose
  tick restarts at 0). `decodeSegments` applies each segment's FILETIME
  so `tickNs100` is **globally monotonic** relative to `acqStart`
  (absolute wall-clock = `acqStartFiletime + tickNs100`).
* `decodeAll(file, manifest, xmlBomOffset)` → all channels, joining
  per-channel `DataType` from the manifest's acquisition order.

**Record layout differs by value width.** A `u32` tick travels with every
sample; on narrow channels (INT16/BIT) the value leads and the tick
follows, while on wide channels (≥ 4-byte value: INT32/REAL32/REAL64/
UINT64) the tick leads so the value stays naturally aligned
(`valueOffsetInRecord`). Segments are located by a **contiguity chain** —
a header is only accepted when its successor (or the block end) is also a
valid header — which rejects coincidental FILETIME-range matches. Sample
timing within a segment is taken from the **FILETIME-derived cadence**
(next segment's FILETIME − this one, ÷ count), the dtype-agnostic source
of truth (the stored per-sample tick is plain `u32` on narrow channels
but a fixed-point value on wide ones).

Validated against the real 50 MB campaign file
`Scope Project_AutoSave_19_04_29.svdx`: **all 149 channels decode to a
fully monotonic series — 5,015,677 samples across all six data types**
(INT16, INT32, REAL32, REAL64, UINT64, BIT), including the 1 kHz analog
welding signals (ch1 = 20,656 samples / 20.655 s, matching the CSV) and
the ~50 kHz `Sound.kanal_*` audio channels (1,032,096 samples each).

**Note on engineering units:** in the 50 MB reference file **every**
channel's manifest unit transform is the identity (`<ScaleFactor>1</…>`,
`<Offset>0</…>` on all 149 `AdsAcquisition`s), so the decoded raw values
already equal the manifest-"scaled" values — there is nothing to apply.
The large raw magnitudes on some analog-input channels (e.g. an INT32
`aKraftZylinderIstwert` of ~-9.1e7) are **not** corrected by these
fields; that raw→physical (force/temperature) calibration is **not
carried in the .svdx** and needs external calibration data. So the
parser intentionally returns raw values; a unit-conversion layer is
out of scope until a file with non-identity transforms (or external
calibration) is available. (`<BaseSampleTime>10000</…>` in the same
manifest independently confirms the 1 ms / 1 kHz analog cadence.)

## What the parser does NOT do

* **TimeseriesReference creation from the binary.** `SvdxBinaryParser`
  decodes samples in-memory; wiring the decoded channels into a
  TimeseriesContainer is the `MFFD-PLUGIN-SVDX-CSV-INGEST-1` follow-up
  (which can now ingest from the binary directly, not only the CSV).
* **TimeseriesReference creation.** The canonical ingestion path is
  the operator-driven CSV export from the TwinCAT Scope Export Tool,
  uploaded as a sibling file; the plugin's role stops at annotation
  emission. See `MFFD-PLUGIN-SVDX-CSV-INGEST-1`.
* **DataObject mutation.** Only the parent FileReference's annotation
  bag is touched; no parent or sibling DataObjects are modified.
* **Channel ↔ acquisition join.** Each `<Channel>` references its
  `<AdsAcquisition>` by GUID; the parser does not resolve the link
  because the cardinality model (multiple acquisitions per chart
  channel) is still being characterised — see byte-layout notes.

## Reference type lifecycle (CLAUDE.md §every-reference-type-ships-CREDL)

The plugin introduces **no new reference type**. It emits annotations
onto an existing `FileReference` (CREDL-complete since file references
shipped in shepard 5.x) and references a companion CSV via its
existing FileReference appId. The four-surface bar (Create / Edit /
Delete / List) is therefore inherited from the FileReference UI and
is already met.

If a future tier-2 plugin (`MFFD-PLUGIN-SVDX-CSV-INGEST-1`) creates a
TimeseriesReference per parsed CSV, that reference type also already
ships full CREDL via the existing TimeseriesContainer UI.

## Activity capture

The plugin's annotation writes flow through the backend's
`SemanticAnnotationService`, which records one `:SemanticAnnotation`
node per write. The wrapping mutation (`POST /v2/files/{appId}/parse`
or the equivalent backend dispatch) is captured by the
`ProvenanceCaptureFilter` per CLAUDE.md §handlers-that-record-skip;
the plugin itself does not call `ProvenanceService.record()` directly.

The recorded Activity carries (per the standard
`ProvenanceCaptureFilter` shape):

* `userId` — the writer who triggered the parse;
* `resourcePath` — the file appId being parsed;
* `httpMethod` — `POST` on the dispatching endpoint;
* `timestamp` — UTC ISO-8601;
* `sourceMode` — derived from the `X-AI-Agent` header per
  CLAUDE.md §cross-cutting-context-in-headers; typically `human`
  when an operator clicks "Parse manifest" in the UI.

## Configuration

The plugin has **no runtime config knobs**. Per CLAUDE.md
§admin-knobs, the bar for adding one is "operator needs to flip at
runtime"; nothing about manifest extraction qualifies. Future tiers
(CSV ingest, bulk import) may add a `:SvdxConfig` singleton; this
tier does not.

## Logging

A single `java.util.logging` logger named after the parser class:

```
de.dlr.shepard.plugin.fileformat.svdx.SvdxManifestParser
```

Emits at `WARNING` when:

* the envelope decoded but the XML manifest could not be parsed (the
  `formatVersion` annotation is still emitted; the rest are skipped);

Per CLAUDE.md §secondary-writes-fire-and-forget the parser never
propagates exceptions to the caller.

## Source map

| Concept | File |
| --- | --- |
| SPI shim | `src/main/java/de/dlr/shepard/plugin/fileformat/svdx/FileParserPlugin.java` |
| Predicate IRIs | `src/main/java/de/dlr/shepard/plugin/fileformat/svdx/SvdxAnnotations.java` |
| Envelope decoder | `src/main/java/de/dlr/shepard/plugin/fileformat/svdx/SvdxEnvelope.java` |
| Manifest data model | `src/main/java/de/dlr/shepard/plugin/fileformat/svdx/SvdxManifest.java` |
| StAX extractor | `src/main/java/de/dlr/shepard/plugin/fileformat/svdx/SvdxManifestExtractor.java` |
| Tier-1 parser entrypoint | `src/main/java/de/dlr/shepard/plugin/fileformat/svdx/SvdxManifestParser.java` |
| Service descriptor | `src/main/resources/META-INF/services/de.dlr.shepard.plugin.fileformat.svdx.FileParserPlugin` |
