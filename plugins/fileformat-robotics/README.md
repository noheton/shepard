# shepard-plugin-fileformat-robotics

Tier-1 text-scrape parser for RoboDK station files (`.rdk`) — the robot
cell digital twin format used by the MFFD upper-shell campaign at DLR
ZLP Augsburg.

Implements the `FileParserPlugin` SPI sketched in
[aidocs/integrations/110 §3](../../aidocs/integrations/110-file-format-parser-plugin.md).
Currently ships a local SPI shim; will bind to the canonical backend
SPI once the Quarkus/Jandex hang in `backend/pom.xml` is resolved (see
the sibling [fileformat-thermography](../fileformat-thermography/)
plugin for the same constraint).

## What it does

Given the raw bytes of a `.rdk` file:

1. Skip the 4-byte custom magic header.
2. Zlib-inflate the remainder.
3. Walk `[uint32 LE byte-length][UTF-16LE bytes]` records and collect
   the printable-ASCII strings.
4. Classify each string against eight predicate regexes and emit one
   `:SemanticAnnotation` per match on the parent `FileReference`.

Predicates (all under `urn:shepard:rdk:*`):

| Predicate | Cardinality | Source string example |
| --- | --- | --- |
| `appVersion` | one | `5.5.3` |
| `platform` | one | `WIN64` |
| `programSource` | one | `D:/MFFD/RoboDK/Ply 1-15` |
| `cadRef` | many | `…/MFZ_Grundkonstruktion.dae` |
| `stepRef` | many | `D:/MFFD/CAD/Vermessung_GRU.CATProduct.stp` |
| `apiEndpoint` | one | `127.0.0.1` |
| `robotController` | one | `R20_MFZDriver` |
| `companionSpatialAnalyzer` | one | (FileReference appId of `<base>.xit`) |

## Out of scope (tier-2, [RDK-PARSE-2](../../aidocs/16-dispatcher-backlog.md))

Full kinematic-tree extraction (joints, tool frames, target poses)
requires the RoboDK Python API or format reverse-engineering and is
delivered as a sidecar plugin per
[`feedback_plugins_declare_sidecars.md`](../../docs/internal/feedback_plugins_declare_sidecars.md).

## Build

```bash
cd plugins/fileformat-robotics
../../backend/mvnw test
../../backend/mvnw package
```

The module is standalone and not yet wired into the aggregator
(`plugins/pom.xml`) — same reason as `fileformat-thermography`: the
Quarkus/Jandex hang in `backend/pom.xml` would amplify if the plugin
were aggregated today. Wire-up is filed as RDK-WIRE-AGGREGATOR-1.

## Test fixture

The real `MFZ.rdk` (12.1 MB) is not checked into git;
`RdkTextScrapeParserMFZFixtureTest` reads it from
`examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk` when present,
and self-skips otherwise. Synthetic-fixture coverage in
`RdkTextScrapeParserTest` and `RdkTextScrapeParserCompanionTest`
exercises every classifier branch.

## Docs

- `docs/reference.md` — predicate catalogue + regex reference
- `docs/quickstart.md` — "I uploaded a .rdk; what happens?"
- `docs/install.md` — operator install + config notes

Apache-2.0 licensed.
