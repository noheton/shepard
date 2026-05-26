---
title: File-format parser plugin SPI — SA, SVDX, RoboDK, and beyond
stage: concept
last-stage-change: 2026-05-26
audience: contributors, plugin authors
---

# 110 — File-format parser plugin SPI

**Status:** Concept · Backlog MFFD-PARSER-01 · Triggered by [GitHub issue #1513](https://github.com/noheton/shepard/issues/1513)  
**First candidates:** Spatial Analyzer `.xit`/`.xit64` (coordinate metrology), Beckhoff SVDX (welding TS), RoboDK `.rdk` (robot cell digital twin)  
**Companion:** `aidocs/integrations/95-shepard-plugin-importer-patterns-from-v15.md` (cross-instance importer — a DIFFERENT problem; see §1)

---

## 0. The problem

A user uploads a proprietary binary file to Shepard's FileContainer. Today that file lands in Garage S3 as an opaque blob — Shepard records the OID but knows nothing about its content. The researcher must extract, convert, and re-upload the derived data manually.

Issue #1513 illustrates this concretely with Spatial Analyzer `.xit`/`.xit64` files: a single file can contain discrete point groups (3–50 points each) OR high-density 6D probe streams (millions of `(X, Y, Z, Roll, Pitch, Yaw)` data points), and the layout is only known at parse time from the binary header. A hard-coded static ingestion pipeline fails; the user needs a reactive, format-aware parser that routes the parsed content to the right Shepard containers automatically.

The same problem applies to the MFFD dataset's **SVDX** files (Beckhoff TwinCAT Scope export, `.svdx` — welding temperature, force, speed TS data), and **RoboDK `.rdk`** files (robot cell digital twin — kinematics, CAD references, AFP program paths).

---

## 1. Distinction from `shepard-plugin-importer`

These are **two different problems with similar names**:

| Concern | `shepard-plugin-importer` (aidocs/95) | `FileParserPlugin` (this doc) |
|---|---|---|
| Trigger | Operator initiates a cross-instance transfer | User uploads a file to Shepard |
| Source | Remote Shepard instance, Confluence, S3, git | File already in Shepard's Garage (OID exists) |
| Output | Full DataObject DAG replicated across instances | Derived entities created from one uploaded file |
| Authority | `ImporterRun` job + admin scope | Same write permissions as the uploading user |
| Complexity | Full resilience suite (warmup, checkpoint, retry, telemetry) | Lightweight async job, single file |

Do NOT merge these. The importer is for bulk cross-instance replication. The parser is a post-upload enrichment hook.

---

## 2. Architecture

```
User uploads file.xit
        │
        ▼
POST /v2/file-containers/{fcId}/files
        │
        ▼
FileContainerService.upload(...)
  → stores in Garage S3 → OID created
  → FileParserRegistry.findParser(mimeType, extension)
        │ match? → yes
        ▼
FileParserRegistry.dispatchAsync(oid, fcId, dataObjectId)
        │
        ▼
JobService schedules ParseJob (async, non-blocking)
        │
        ▼
FileParserPlugin.parse(ParseContext)
  reads OID bytes from Garage
  routes output to appropriate Shepard containers:
    - Point groups           → StructuredDataContainer (SD rows)
    - 6D probe streams       → TimeseriesContainer (high-freq TS)
    - Scope waveforms (SVDX) → TimeseriesContainer (multi-channel)
    - Metadata               → DataObject.attributes (PATCH)
    - Semantic content       → SemanticAnnotations (CHAMEO/SSN/SOSA/PROV-O)
    - Robot frames (RDK)     → DataObject.attributes + SemanticAnnotations
        │
        ▼
ParseJob → COMPLETED (or FAILED with diagnostic hint)
```

The upload response is still `201 Created` with the OID — the parse is async. The user can poll `GET /v2/file-containers/{fcId}/files/{oid}/parse-status` for progress.

---

## 3. `FileParserPlugin` SPI (sketch)

```java
public interface FileParserPlugin {
    /** Declare which MIME types and extensions this parser handles. */
    ParseCapability capability();

    /** Return false if the file header is unrecognised (fast path). */
    boolean probe(InputStream first4KB);

    /**
     * Parse the file identified by ctx.oid().
     * Write derived entities via ctx.destService().
     * ctx.dataObjectId() is the owning DO (if known).
     */
    ParseResult parse(ParseContext ctx) throws ParseException;
}

public record ParseCapability(
    Set<String> mimeTypes,
    Set<String> extensions,   // e.g. {".xit", ".xit64"}
    String displayName,        // "Spatial Analyzer (NRK .xit)"
    String pluginId            // "shepard-plugin-parser-sa"
) {}
```

`ParseContext` carries:
- `oid` — the file's Garage OID
- `fileContainerAppId` — write target for derived containers
- `dataObjectAppId` — optional owning DataObject
- `destService()` — pre-authorised service bean for creating TS containers, SD containers, DataObject attribute patches, and semantic annotations
- `jobId` — for progress reporting

---

## 4. First candidates

### 4.1 Spatial Analyzer (`.xit` / `.xit64`)

**Instrument:** Leica AT901 laser tracker, T-Probe, T-Mac (6D handheld probe)  
**Format:** NRK proprietary binary — **no SDK required**, confirmed parseable directly  
**Confirmed format details (2026-05-26 inspection of real MFFD files):**

- Magic bytes: `FF CF CF FF` at offset 0 — confirmed on `MFZ.xit`, `LBR_1-3_Auswertung.xit64`, `HartwegStatisch.xit`
- UTF-16LE text content begins after a fixed binary header. All entity names, attribute keys, and measurement values are embedded as UTF-16LE strings readable without SDK.
- `.xit` = 32-bit float coordinates (AFP robot cell calibration, typical < 10 MB)
- `.xit64` = 64-bit double coordinates (LBR iiwa path verification, 4–96 MB; dense scans up to 96 MB)
- Both extensions share the same parser; coordinate precision is inferred from the magic byte variant byte (offset 3).

**Entity taxonomy (from real files):**

| Entity type | Example | Shepard container |
|---|---|---|
| Collection / Group | `Referenzpunkte MFZ`, `Orientierungspunkte`, `MessnesterCAD` | Groups rows in StructuredData |
| Target / Point | `MFZ_P001`, LBR target points | StructuredDataContainer row |
| Frame | Frame with Translation dx/dy/dz + Rotation Rx/Ry/Rz | DataObject attribute + SemanticAnnotation |
| RMS Error | `0.003386 mm` per frame | SemanticAnnotation `chameo:hasMeasurementUncertainty` |
| LogEntry (timestamp + measurement) | `2014-06-03T...` | DataObject attribute `prov:startedAtTime` |
| EDMShot / spatial scan | discrete shot data | StructuredDataContainer row |
| RSCloud (dense point cloud) | millions of points | SpatialContainer [SP1] |
| SAIPScanStripe (line scan) | 6D path at 100 Hz | TimeseriesContainer |
| Weather conditions | `T=23.5C,P=962.0hPA,H=33.6%` | SemanticAnnotation `sosa:hasObservationCondition` |
| Instrument header | `Leica emScon 4087`, `Leica AT901` | SemanticAnnotation `chameo:hasInstrument` |

**Content variability detection at parse time:**
- `measurementType == DISCRETE_POINTS` → StructuredDataContainer rows: `(pointName, x, y, z, spatialRms)`
- `measurementType == 6D_PROBE_SCAN` → TimeseriesContainer: `(timestamp_ns, x, y, z, roll, pitch, yaw)` at kHz rates
- `measurementType == DENSE_SCAN` → SpatialContainer (RSCloud/SAIPScanStripe) [requires SP1]

**Semantic annotation output (parse always emits these when present):**

| Source in `.xit` file | Annotation predicate | Example value |
|---|---|---|
| Instrument type + serial | `chameo:hasInstrument` | `"Leica AT901 SN:4087"` |
| Frame RMS error | `chameo:hasMeasurementUncertainty` + `qudt:unit mm` | `0.003386` |
| Weather (T/P/H) | `sosa:hasObservationCondition` | `"T=23.5C,P=962.0hPA,H=33.6%"` |
| LogEntry timestamps | `prov:startedAtTime` | `"2014-06-03T..."` |
| Frame transform | `chameo:hasResult` | Translation + Rotation block |
| Software version | `m4i:usedSoftware` | `"Spatial Analyzer 2019.08"` |

**MFFD context:**
- `MFZ.xit` — AFP robot cell (RoboTeam R10+R20) coordinate calibration. Groups: `Referenzpunkte MFZ`, `Orientierungspunkte`, `MessnesterCAD`. Frame transforms for AT901 + robot base stations. **Cross-links to `MFZ.rdk`** (§4.3): the SA file defines the *measured* coordinate frames; the RDK file defines the *design* frames. Together they form the metrology-to-CAD registration.
- `LBR_1-3_Auswertung.xit64` / `LBR_RCC.xit64` — LBR iiwa robot arm path verification. `Spatial Scan 100 Hz, increment 0.000150`. Discrete points P100–P110+ with RCC (Reaction Compensation Controller) data.

**Conversion strategy:** Java native parser (magic check + UTF-16LE struct iteration — no SDK). Optional fallback: Spatial Analyzer Automation Server SDK via JNI/subprocess for dense-scan edge cases.  
**Prerequisite for MVP:** The real MFFD files in `/mnt/pve/unas/dump/later/` serve as parser validation corpus — no additional procurement needed.

---

### 4.2 Beckhoff TwinCAT Scope (`.svdx`)

**Instrument:** Beckhoff industrial controllers — CRW welding head, AFP consolidation module  
**Format:** `.svdx` = TwinCAT Scope v2 binary (TDMS-adjacent but not identical)  
**Content:** Multi-channel time-series: temperature setpoint/actual, force, speed, energy — typically 10–20 channels, ~30s at 1 kHz per weld seam  

**Existing converter:** `tool_sources/tdms2csv/tdms2csv.py` — Python CLI already validated on MFFD data. Plugin wraps this as a subprocess or ports the logic to Java.

**Parse output:**
- TimeseriesContainer per file (one container = one weld seam)
- Channel names from SVDX header → mapped to Shepard TS channel names
- DataObject attribute `process.type = "welding"`, `weld.seam_id` from filename

**Semantic annotation output:**

| Source | Annotation predicate | Example value |
|---|---|---|
| Channel names | `ssn:observedProperty` | `"temperature_setpoint"` |
| Acquisition rate | `sosa:madeBySensor` (rate) | `"1000 Hz"` |
| Weld seam ID | `m4i:ManufacturingProcess` | `"seam_042"` |
| Process type | `chameo:hasInstrument` | `"Beckhoff CRW Welding Head"` |

**Volume:** 181 SVDX files × 20 channels × 30k points = ~108M data points  
**Prerequisite:** N: drive mount on DLR cube (backlog MFFD-NDRIVE-01)

---

### 4.3 RoboDK Station File (`.rdk`)

**Role:** Robot cell digital twin baseline — foundation for AFP R10+R20 robot workspace  
**File in MFFD dataset:** `MFZ.rdk` (12.1 MB) — full robot cell model for the MFFD upper fuselage AFP manufacturing cell at ZLP Augsburg  
**Companion SA file:** `MFZ.xit` (§4.1) — the *measured* coordinate calibration that registers against this *design* model

**Confirmed format details (2026-05-26 inspection):**

- Magic bytes: `03 25 10 A5` (4-byte custom header, version-encoded)
- Compression: zlib deflate starting at offset 4. Decompressed size: ~52.8 MB (4.4× ratio)
- Decompressed format: UTF-16LE binary with length-prefixed strings (same encoding as SA files)
- Version string: `"Station File 01.01"` (RoboDK internal format version)
- App version: `5.5.3` / platform: `WIN64`
- Major sections: `STATION DATA`, `TREE DATA`, `CAD MAP DATA`, `TEXTURE DATA`

**Key entities found in `MFZ.rdk`:**

| Category | Content | Notes |
|---|---|---|
| Robot frames | `World`, `R20 Base`, `R20_MFZ Base`, `Form_BT`, `Form_TL_Root`, `US_Root`, `Base_measured`, `FrameChkptStart`/`End` | Kinematic tree checkpoints |
| Robot drivers | `R20_MFZDriver`, `VCP_PRIMARY_ROBOT`, `VCP_SECONDARY_ROBOT`, `VCP_RETRACT` | AFP robot identities |
| Program source | `D:/MFFD/RoboDK/Ply 1-15` | AFP ply 1–15 robot program directory |
| CAD model refs | `MFZ_Grundkonstruktion.dae`, `MFZ_Mittelschiene.dae`, `MFZ_Mittelschiene_Schlitten1.dae` | From `de.dlr.bt.au.robotcell.mfz` git repo |
| STEP references | `MTLH_MultiTape Schneideinheit.CATProduct.stp`, `Vermessung_GRU.CATProduct.stp` | Tape cutting unit + metrology CAD |
| API endpoint | `127.0.0.1` (apikuka / anonymous credentials) | RoboDK local API |

**Parse approach — two-tier:**

1. **Metadata extraction (MVP, no SDK needed):** Decompress zlib → scan UTF-16LE strings for version, robot names, CAD references, program paths, instrument names. Emit as DataObject attributes + semantic annotations.

2. **Full kinematic extraction (deferred, requires RoboDK Python API or format reverse-engineering):** Extract the full kinematic tree (robot joint axes, tool frames, target poses) for a first-class spatial representation. Deferred until RoboDK SDK is accessible on the parse host.

**Parse output (MVP — metadata tier):**

- DataObject attributes: `robot.cell = "MFZ"`, `robot.primary = "R20_MFZDriver"`, `robodk.version = "5.5.3"`, `program.source_dir = "MFFD/RoboDK/Ply 1-15"`, `cad.cell_model = "MFZ_Grundkonstruktion.dae"`
- FileContainer: raw `.rdk` stored for downstream tools (RoboDK, dataship, REBAR)
- SemanticAnnotations (see table below)

**Semantic annotation output:**

| Source in `.rdk` file | Annotation predicate | Example value |
|---|---|---|
| Robot cell name | `ssn:System` | `"MFZ (MFFD AFP Robot Cell)"` |
| Primary robot | `chameo:hasInstrument` | `"R20 (AFP primary robot)"` |
| Program source directory | `m4i:ManufacturingProcess` | `"AFP Ply 1-15 program set"` |
| CAD model references | `m4i:hasTool` | `"MFZ_Grundkonstruktion.dae"` |
| Cell frame / `Base_measured` | `chameo:hasResult` | `"Measured base frame (registration with SA)"` |
| Format version | `m4i:usedSoftware` | `"RoboDK 5.5.3"` |

**Cross-link `MFZ.xit` ↔ `MFZ.rdk`:**  
When both files are uploaded to the same DataObject, the parser should emit a provenance link: the SA file's `Base_measured` frame is the *measured calibration* of the robot base frame defined in the RDK file. This closes the metrology → digital-twin registration loop. Implemented as a SemanticAnnotation on the DataObject: `prov:wasDerivedFrom` pointing to the SA OID.

**Prerequisite for MVP:** `MFZ.rdk` already available at `examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk` — validates the zlib decompressor and string extractor without additional procurement.

---

## 5. Backlog items

| ID | Task | Size | Status |
|---|---|---|---|
| MFFD-PARSER-01 | Design + SPI skeleton for `FileParserPlugin` | M | queued |
| MFFD-PARSER-SA1 | `shepard-plugin-parser-sa` — Spatial Analyzer `.xit`/`.xit64` (no SDK needed) | L | queued |
| MFFD-PARSER-SVDX1 | `shepard-plugin-parser-svdx` — TwinCAT Scope `.svdx` → TS | M | queued (gated on MFFD-NDRIVE-01) |
| MFFD-PARSER-RDK1 | `shepard-plugin-parser-rdk` — RoboDK `.rdk` metadata extraction (MVP: attributes + SemanticAnnotations) | M | queued |
| MFFD-NDRIVE-01 | Set up N: drive mount on DLR cube for scripted SVDX ingest | S (ops) | queued |

---

## 6. Open questions

1. **SA dense scan routing:** RSCloud and SAIPScanStripe (millions of scan points) → SpatialContainer is the right target, but the SP1 SPI must be ready first. Intermediate option: write to StructuredDataContainer with page limits.
2. **`.xit64` precision:** Parser should detect `FF CF CF FF` magic and then check offset byte 3 to distinguish 32-bit vs 64-bit coordinate encoding. Both are UTF-16LE with identical entity grammar; only the float serialisation differs.
3. **SVDX vs TDMS:** `tdms2csv.py` handles both — confirm which variant Beckhoff uses on the MFFD equipment (TwinCAT Scope v2 is TDMS-based but with a scope-header wrapper).
4. **Parse failure policy:** If the parser fails on a file the user uploaded, the FileContainer retains the raw bytes (always) and marks `parse_status: FAILED` on the OID. One retry, then `FAILED_PERMANENT` with diagnostic hint in the job log.
5. **Parser registry:** Parsers auto-discovered via CDI `@ApplicationScoped` beans for v1 simplicity; manifest declaration for operator opt-in per file type deferred to v2.
6. **RoboDK full kinematic extraction:** The binary tree structure (TREE DATA section) requires either the RoboDK Python SDK (`robodk` package, Apache-licensed) or format reverse-engineering. The SDK approach runs in a sidecar Python container — viable, consistent with the `tdms2csv.py` subprocess pattern. Add as MFFD-PARSER-RDK1 Phase 2.
7. **Cross-file linking (SA ↔ RDK):** Parser can detect co-uploaded files by scanning the same DataObject's file references. If both `*.xit` and `*.rdk` are present, emit the `prov:wasDerivedFrom` cross-link automatically.

---

*Concept written 2026-05-26. Triggered by GitHub issue #1513 (feat: native SA) + MFFD RoboDK integration request.  
Format details confirmed from real MFFD files: 6 SA files in `/mnt/pve/unas/dump/later/`, `MFZ.rdk` at `examples/mffd-showcase/raw-data/mffd-data/cell/MFZ.rdk`.  
SA requires no SDK. RDK MVP requires no SDK (metadata extraction only); full kinematic tree deferred.*
