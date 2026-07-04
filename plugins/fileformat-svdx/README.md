# shepard-plugin-fileformat-svdx

Tier-1 manifest-extraction parser for Beckhoff TwinCAT Scope project
files (`.svdx`) — the spot-welding scope-trace format used by the
MFFD upper-shell ultrasonic-welding campaign at DLR ZLP Augsburg
(March 2023).

Implements the `FileParserPlugin` SPI sketched in
[`aidocs/integrations/110 §3`](../../aidocs/integrations/110-file-format-parser-plugin.md).
Currently ships a local SPI shim; will bind to the canonical backend
SPI once the Quarkus/Jandex hang in `backend/pom.xml` is resolved
(see the sibling [`fileformat-thermography`](../fileformat-thermography/)
and [`fileformat-robotics`](../fileformat-robotics/) plugins for the
same constraint).

## What it does

Given the raw bytes of a `.svdx` file:

1. Decode the 16-byte envelope header — bytes 0..7 = `uint64 LE`
   pointer to the trailing XML manifest's UTF-8 BOM; bytes 8..15 =
   format-version stamp with stable magic marker
   `0x96 0x0c 0x00` at offsets 9..11.
2. Locate the trailing `<ScopeProject>` XML manifest using the
   envelope pointer.
3. Parse the manifest with streaming StAX (no DOM, no schema
   validation, XXE-hardened).
4. Emit `urn:shepard:svdx:*` semantic annotations on the parent
   FileReference's `appId`.

**The proprietary binary sample section is NOT decoded.** Beckhoff
publishes no specification for it; the community-standard
[`pytcs`](https://github.com/CagtayFabry/pytcs) library explicitly
punts on the binary in favour of CSV/TXT exports. The plugin's
binary research is captured in
[`docs/byte-layout-notes.md`](docs/byte-layout-notes.md) and
deferred to `MFFD-PLUGIN-SVDX-BINARY-PARSER-1` in
`aidocs/16-dispatcher-backlog.md`.

Predicates (all under `urn:shepard:svdx:*`) — see the full table in
[`docs/reference.md`](docs/reference.md):

| Predicate | Cardinality | Source |
| --- | --- | --- |
| `formatVersion` | one | envelope bytes 8..15 |
| `projectGuid`, `projectName`, `dataPoolGuid` | one each | manifest root |
| `mainServer`, `recordTimeNs`, `autoSaveMode`, `assemblyName` | one each | manifest root |
| `channelCount`, `acquisitionCount` | one each | derived |
| `amsNetId`, `port`, `dataType` | many (dedup) | `<AdsAcquisition>` children |
| `channelName` | many | `<Channel>/<Name>` |
| `symbolName` | many | `<AdsAcquisition>/<SymbolName>` |
| `companionCsv` | one | sibling-file detection |

## Out of scope (deferred follow-ups)

* `MFFD-PLUGIN-SVDX-BINARY-PARSER-1` — reverse-engineer the
  proprietary binary sample section (multi-week research, may never
  resolve cleanly).
* `MFFD-PLUGIN-SVDX-CSV-INGEST-1` — backend service that consumes
  the operator-driven TwinCAT Scope Export Tool CSV output and
  populates a TimeseriesReference. The realistic ingestion path
  given the binary opacity.
* `MFFD-PLUGIN-SVDX-SEMANTIC-1` — auto-detect physical-quantity
  semantics from the GVL_* Beckhoff naming convention (e.g.
  `aTemperatureAnalogIntput*` → `urn:shepard:phys:temperature`).
* `MFFD-PLUGIN-SVDX-BULK-1` — folder-spanning bulk import for
  campaigns with many `.svdx` siblings.
* `MFFD-PLUGIN-SVDX-WIRE-1` — wire the module into
  `plugins/pom.xml` once the Jandex hang clears.

## Build

```bash
cd plugins/fileformat-svdx
../../backend/mvnw test
../../backend/mvnw package
```

The module is standalone and not yet wired into the aggregator
(`plugins/pom.xml`) — same reason as `fileformat-thermography` and
`fileformat-robotics`: the Quarkus/Jandex hang in `backend/pom.xml`
would amplify if the plugin were aggregated today.

## Test fixture

The real MFFD spot-welding files are NOT checked into git;
`SvdxParserMFFDFixtureTest` reads them from
`/mnt/pve/unas/dump/dataset/Punktschweißungen/` when present and
self-skips otherwise. Synthetic-fixture coverage in
`SvdxEnvelopeTest`, `SvdxManifestExtractorTest`, and
`SvdxManifestParserTest` exercises every classifier branch.

Test count: **22 JUnit tests**, all green.

## Docs

* [`docs/reference.md`](docs/reference.md) — predicate catalogue + per-predicate provenance
* [`docs/quickstart.md`](docs/quickstart.md) — "I uploaded a .svdx; what happens?"
* [`docs/install.md`](docs/install.md) — operator install + config notes
* [`docs/byte-layout-notes.md`](docs/byte-layout-notes.md) — reverse-engineering notes + seed for binary-parser research

## Honest scope statement

Task GAP-1 framed this as a binary-parsing + REST-endpoint +
frontend-dialog combo backed by 166 files across three folders.
Empirical findings during research lowered the scope:

* The dataset has **21 `.svdx` in one folder** (Punktschweißungen),
  not 166 across three folders. Other named folders
  (`Scope_Sicherung`, `Stringer_schweissungen`) do not exist on the
  mount.
* The binary section is **proprietary and undocumented by
  Beckhoff** — the community-standard tool punts.
* Sibling-CSV coverage is **3/21 ≈ 14%** — too thin to be the
  primary user-facing path on its own.

The shipped scope (manifest extraction + sibling CSV link emission)
mirrors the existing `fileformat-robotics` + `fileformat-thermography`
pattern and ships real, reliable value without faking binary parsing.
The unshipped scope is filed as concrete follow-up rows above.

Apache-2.0 licensed.
