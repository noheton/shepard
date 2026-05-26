---
title: File-format parser plugin SPI — SA, SVDX, and beyond
stage: concept
last-stage-change: 2026-05-26
audience: contributors, plugin authors
---

# 110 — File-format parser plugin SPI

**Status:** Concept · Backlog MFFD-PARSER-01 · Triggered by [GitHub issue #1513](https://github.com/noheton/shepard/issues/1513)  
**First candidates:** Spatial Analyzer `.xit`/`.xit64` (coordinate metrology), Beckhoff SVDX (welding TS)  
**Companion:** `aidocs/integrations/95-shepard-plugin-importer-patterns-from-v15.md` (cross-instance importer — a DIFFERENT problem; see §1)

---

## 0. The problem

A user uploads a proprietary binary file to Shepard's FileContainer. Today that file lands in Garage S3 as an opaque blob — Shepard records the OID but knows nothing about its content. The researcher must extract, convert, and re-upload the derived data manually.

Issue #1513 illustrates this concretely with Spatial Analyzer `.xit`/`.xit64` files: a single file can contain discrete point groups (3–50 points each) OR high-density 6D probe streams (millions of `(X, Y, Z, Roll, Pitch, Yaw)` data points), and the layout is only known at parse time from the binary header. A hard-coded static ingestion pipeline fails; the user needs a reactive, format-aware parser that routes the parsed content to the right Shepard containers automatically.

The same problem applies to the MFFD dataset's **SVDX** files (Beckhoff TwinCAT Scope export, `.svdx` / `.tdms` — welding temperature, force, speed TS data), and will recur for every new instrument format added to the MFFD or PLUTO campaigns.

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
- `destService()` — pre-authorised service bean for creating TS containers, SD containers, DataObject attribute patches
- `jobId` — for progress reporting

---

## 4. First candidates

### 4.1 Spatial Analyzer (`.xit` / `.xit64`)

**Instrument:** Leica AT901 laser tracker, T-Probe, T-Mac (6D handheld probe)  
**Format:** NRK proprietary binary, UTF-16LE layout  
**Content variability:** discrete coordinate mappings OR high-density 6D scan streams — must be detected at parse time from binary header  

**Parse output:**
- `measurementType == DISCRETE_POINTS` → StructuredDataContainer rows: `(pointName, x, y, z, spatialRms)`
- `measurementType == 6D_PROBE_SCAN` → TimeseriesContainer: `(timestamp_ns, x, y, z, roll, pitch, yaw)` at kHz rates
- In both cases: DataObject attribute `instrument.type = "Leica AT901"`, `instrument.id` from file header, `frame_of_reference` from file metadata

**Conversion strategy:** Java native parser (NRK format is well-documented for UTF-16LE + binary struct) OR wrap the existing C++ Spatial Analyzer Automation Server SDK via JNI / subprocess  
**Prerequisite for MVP:** At least one known-good `.xit` file for parser validation

### 4.2 Beckhoff TwinCAT Scope (`.svdx`)

**Instrument:** Beckhoff industrial controllers — CRW welding head, AFP consolidation module  
**Format:** `.svdx` = TwinCAT Scope v2 binary (TDMS-adjacent but not identical)  
**Content:** Multi-channel time-series: temperature setpoint/actual, force, speed, energy — typically 10–20 channels, ~30s at 1 kHz per weld seam  

**Existing converter:** `tool_sources/tdms2csv/tdms2csv.py` — Python CLI already validated on MFFD data. Plugin wraps this as a subprocess or ports the logic to Java.

**Parse output:**
- TimeseriesContainer per file (one container = one weld seam)
- Channel names from SVDX header → mapped to Shepard TS channel names
- DataObject attribute `process.type = "welding"`, `weld.seam_id` from filename

**Volume:** 181 SVDX files × 20 channels × 30k points = ~108M data points  
**Prerequisite:** N: drive mount on DLR cube (backlog MFFD-NDRIVE-01)

---

## 5. Backlog items

| ID | Task | Size | Status |
|---|---|---|---|
| MFFD-PARSER-01 | Design + SPI skeleton for `FileParserPlugin` | M | queued |
| MFFD-PARSER-SA1 | `shepard-plugin-parser-sa` — Spatial Analyzer `.xit`/`.xit64` | L | queued |
| MFFD-PARSER-SVDX1 | `shepard-plugin-parser-svdx` — TwinCAT Scope `.svdx` → TS | M | queued (gated on MFFD-NDRIVE-01) |
| MFFD-NDRIVE-01 | Set up N: drive mount on DLR cube for scripted SVDX ingest | S (ops) | queued |

---

## 6. Open questions

1. **SA binary format:** Is the full NRK `.xit` spec documented? Or does this require disassembly / the Spatial Analyzer SDK? If SDK-only, we may need to wrap the Windows-only automation server via a sidecar container.
2. **SVDX vs TDMS:** `tdms2csv.py` handles both — confirm which variant Beckhoff uses on the MFFD equipment (TwinCAT Scope v2 is TDMS-based but with a scope-header wrapper).
3. **Parse failure policy:** If the parser fails on a file the user uploaded, should the FileContainer retain the raw bytes (always yes) and mark `parse_status: FAILED` on the OID, or should the job be retried? Recommendation: one retry, then FAILED_PERMANENT with diagnostic hint.
4. **Parser registry:** Should parsers be auto-discovered via CDI `@ApplicationScoped` beans or declared in a plugin manifest? CDI discovery is simpler for v1; manifest declaration allows operator opt-in per file type.

---

*Concept written 2026-05-26. Triggered by GitHub issue #1513 (feat: native SA).  
First implementation blocked on: (a) MFFD-NDRIVE-01 for SVDX, (b) SA test file procurement for SA parser.*
