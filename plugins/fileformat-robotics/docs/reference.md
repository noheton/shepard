---
title: shepard-plugin-fileformat-robotics — reference
stage: feature-defined
last-stage-change: 2026-05-28
audience: power users, operators
---

# fileformat-robotics — reference

The robotics file-format parser is registered as a `FileParserPlugin`
under the SPI sketched in [aidocs/integrations/110 §3](../../../aidocs/integrations/110-file-format-parser-plugin.md).
It handles RoboDK station files (`.rdk`).

## Tier-1 surface (RDK-PARSE-1, shipped)

Trigger: any file uploaded to a `FileContainer` whose filename ends in
`.rdk` (case-insensitive).

Output: a set of `:SemanticAnnotation` nodes on the parent
`FileReference` with predicates in the `urn:shepard:rdk:*` namespace.

### Predicate catalogue

| Predicate IRI | Cardinality | Source | Example value |
| --- | --- | --- | --- |
| `urn:shepard:rdk:appVersion` | one | first match of `^\d+\.\d+\.\d+([._-][A-Za-z0-9]+)?$` | `5.5.3` |
| `urn:shepard:rdk:platform` | one | first match of `^(WIN64\|WIN32\|MACOS\|MACOSX\|LINUX)$` | `WIN64` |
| `urn:shepard:rdk:programSource` | one | first string after the literal marker `VCP_SOURCE_DIRECTORY` | `D:/MFFD/RoboDK/Ply 1-15` |
| `urn:shepard:rdk:cadRef` | many | every string matching `(?i).+\.dae$`, deduplicated | `…/MFZ_Grundkonstruktion.dae` |
| `urn:shepard:rdk:stepRef` | many | every string matching `(?i).+\.(stp\|step)$`, deduplicated | `D:/MFFD/CAD/Vermessung_GRU.CATProduct.stp` |
| `urn:shepard:rdk:apiEndpoint` | one | first IPv4 (optional `:port`) match | `127.0.0.1` |
| `urn:shepard:rdk:robotController` | one | first non-`VCP_` string ending in `Driver` | `R20_MFZDriver` |
| `urn:shepard:rdk:companionSpatialAnalyzer` | one | FileReference appId of a sibling file named `<base>.xit` or `<base>.xit64` (case-insensitive) in the same `FileContainer` | `xit-appid-fixture-1234` |

Multi-valued predicates fire once per distinct value (insertion order
preserved from the file). Single-valued predicates take the first
match — duplicate appearances later in the file are ignored.

### Subject anchoring

Annotations are written on the **FileReference** appId by default. If
no FileReference appId is supplied in the `ParseContext`, the parser
falls back to the parent `DataObject` appId. If neither is available
the parser is a no-op.

### File-format notes (empirical)

RoboDK `.rdk` (observed against `MFZ.rdk`, 12.1 MB):

- Bytes 0–3: 4-byte custom magic. On `MFZ.rdk`: `03 25 10 A5`. Treated
  opaquely; the parser skips these four bytes.
- Bytes 4..end: zlib (Deflate) stream. Decompressed payload on
  `MFZ.rdk`: 52.8 MB (4.4× expansion).
- Inflated payload: little-endian binary with embedded text records.
  Each text record is `[uint32 LE byte-length][UTF-16LE bytes]`. The
  length is in **bytes** (not characters); odd lengths are filtered
  out.
- Strings observed on `MFZ.rdk`: 72 records after walk filtering.
  The walker constrains length to `[2 .. 2048]` bytes and requires
  every decoded character to be printable ASCII (`[0x20, 0x7E]`).

### Error policy

Tier-1 is a best-effort enrichment hook. Any of the following result
in zero or partial annotations but no thrown exception:

- Zlib stream is missing, truncated, or corrupt.
- Length-prefix walk finds no records.
- A given predicate's regex matches nothing.

In all such cases the upload itself succeeds with the raw bytes
preserved in Garage S3; the parse is post-upload enrichment.

## Tier-2 surface (RDK-PARSE-2, deferred)

Full kinematic-tree extraction — joints, tool frames, target poses —
requires the RoboDK Python API or format reverse-engineering and is
delivered as a sidecar plugin per
[`feedback_plugins_declare_sidecars.md`](../../../docs/internal/feedback_plugins_declare_sidecars.md).
The sidecar will emit a `:DigitalTwinScene` Neo4j entity per
[aidocs/data/85](../../../aidocs/data/85-coordinate-frame-tree.md);
tier-1 string annotations stay regardless of whether the sidecar is
running.

## Reparse hook

The parser is idempotent — re-running it against the same file emits
the same annotation set. `POST /v2/files/{appId}/reparse` (when wired
through the canonical SPI per aidocs/110 §3) replays the parse against
the stored OID, useful after operator-driven regex tweaks or after
landing a tier-2 sidecar.

## Cross-references

- `urn:shepard:rdk:*` is the annotation namespace consumed by the URDF
  viewer ([URDF-WEBVIEW-1](../../../aidocs/integrations/113-urdf-viewer.md))
  for the joint-mapping signal.
- The companion-file predicate closes the metrology → digital-twin
  registration loop with the Spatial Analyzer `.xit` companion (see
  aidocs/integrations/110 §4.1 / §4.3).
