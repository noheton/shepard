---
stage: feature-defined
last-stage-change: 2026-05-26
---

# MFFD Dump Ingestion Plan

**Source archive:** `/mnt/pve/unas/dump/mffd-data.7z`  
**Archive size:** 107 GB (Method=Copy — no compression gain, extract = copy)  
**Status:** Reconnaissance complete. Extraction not yet started.

---

## 1. Dataset inventory

### 1.1 Archive structure

| Path | Files | Notes |
|------|-------|-------|
| `1-Tapelaying/` | 228,206 | AFP robot data — fully imported by legacy importer |
| `4-Brückenschweißen/` | 27,114 | Bridge/frame welding — partially imported |
| `3-Punktschweißen/` | 9 | Spot welding — minimal data only |
| `2-Stringerschweißen/` | 1 | **Placeholder only** — no data imported |
| `5-Spantverbindung/` | 1 | **Placeholder only** |
| `6-Cleats/` | 1 | **Placeholder only** |
| `0-CAD/` | 1 | **Placeholder only** — CAD not in dump |
| `99-WIki/` | 1,164 | Confluence wiki export (HTML + attachments) |
| `MFFD_Demonstrator_Recursive_Tree.json` | 1 | **N: drive inventory** — describes full dataset that exists at source |
| `manifest.txt` | 1 | Human-readable process list |
| `tool_sources/` | 47,444 | 80+ prototype tools — see §6 |
| `importer/` | 21 | Legacy Python importer code |

**File type breakdown across all data steps:**

| Extension | Count | Category |
|-----------|-------|----------|
| `.json` | 81,709 | DataObject metadata (legacy Shepard export) |
| `.done` | 42,229 | Import progress markers from legacy run |
| `.csv` | 19,521 | Structured data (CameraConfig, FSDSet, PointCloud, ProfileSet) |
| `.tif` | 3,525 | AFP inspection images (per-track profiles) |

### 1.2 Legacy DataObject format

Each DataObject was exported as `do-<legacy_numeric_id>.json`. The numeric IDs (e.g., `do-156294`) are the old v1 long IDs from the DLR cube instance. These files are **0-byte** in the archive — the JSON export was empty shell records from the legacy system, carrying only the naming relationship. The actual file payloads were stored separately and referenced by the container IDs in `shepard_importer/conf/config.json`.

**Implication:** We cannot reconstruct DataObject metadata from the `.json` files. The hierarchy is recoverable from the folder structure; the attribute content must be re-derived from the CSV/TIF/JSON sidecar files.

---

## 2. Full source inventory (N: drive — beyond the dump)

`MFFD_Demonstrator_Recursive_Tree.json` inventories the full `N:\Messdaten\MFFD_Demonstrator\` directory. This is **not inside the 7z** — it's a catalogue of what MORE data exists at the source. Key folders:

| Source folder | Size | Types | Notes |
|---------------|------|-------|-------|
| `Punktschweißungen` | ~6 GB | `.svdx` (21), `.xlsx`, `.csv` | Beckhoff TwinCAT Scope — spot welding TS |
| `Scope_Sicherung` | ~14.6 GB | `.svdx` (23) | Scope backup from Collins equipment |
| `Stringer_schweissungen` | ~288 GB | `.mp4` (138), `.svdx` (137), `.tiff` (6000–6500 per pass), `.txt` | Stringer welding: video + TS + thermal images |
| `ThermoCam` | ~7.9 GB | subdirs | Thermal camera recordings (empty parent) |
| `Testauswertung` | ~6.4 GB | `.mp4`, `.svdx`, `.txt`, `.csv` | Test evaluation / post-processing results |
| `Scope_Sicherung/Collins/` | ~6 GB | `.csv` (8), `.svdx` (7) | Collins partner data |

**Key finding:** Steps 2 (Stringerschweißen), 3 (Punktschweißen), and 5–6 are present on the N: drive but **absent or minimal in the dump**. The dump captured the AFP tapelaying run (step 1) and bridge welding (step 4) only.

**SVDX format constraint:** `.svdx` = Beckhoff TwinCAT Scope export (proprietary binary). Requires conversion with `tdms2csv` (included in `tool_sources/`) before ingest into TimescaleDB. This is the blocker for welding time-series data.

---

## 3. Data shape mapping

### 3.1 AFP Tapelaying (step 1) — fully in dump

**Hierarchy:** AFP Run → PlyGroup → Layup → Layer → Track (5 levels)

Per-track files and their Shepard mapping:

| File | Shepard shape | Notes |
|------|--------------|-------|
| `<track>.json` (sidecar) | DataObject attributes | Contains metadata: track ID, robot params, QA flags |
| `CameraConfig.csv` | StructuredDataReference or DataObject attribute | Camera calibration — small, per-track |
| `FSDSet.csv` | TimeseriesReference | Fiber stress data points — main process TS |
| `PointCloud.csv` | `shepard-plugin-spatial` SpatialContainer | 3D scan positions — needs spatial plugin (SP1) |
| `ProfileSet.csv` | StructuredDataReference | Cross-section profile data |
| `ProfileSet.tif` | FileReference (FileContainer) | Profile inspection image |

**Volume:** ~3,525 tracks × 5 files = ~17,600 data objects when fully expanded

**Note:** The AFP run collection is also captured in `AFP_52185/` (numeric ID). This is the legacy collection ID on the DLR cube. Reference for cross-checking imported hierarchy.

### 3.2 Bridge/Frame Welding (step 4) — partially in dump

Sub-directory: `mffd-framewelding/`. Same `.json` + `.done` pattern as tapelaying. 11,099 DataObjects + 7,726 `.done` markers. The `.done` markers cover ~70% of DOs, meaning ~30% were not successfully imported in the legacy run.

No CSV/TIF sidecar structure — welding DOs are mostly metadata records pointing to SVDX scope files that are on the N: drive but not in the dump.

### 3.3 Spot Welding (step 3) — minimal in dump

Only 9 files total: the folder `Punktschweißungen/` exists but contains only a top-level placeholder. The actual data (`.svdx`, `.xlsx`, `.csv`) lives on the N: drive.

### 3.4 Confluence Wiki (99-WIki)

`mffd-confluence-space-export/` contains the HTML Confluence export + `confluence-analysis.md`. This maps to the existing `MFFD-Dropbox` Collection already partially processed by task #137. Treat as already handled — don't re-ingest.

---

## 4. Link / relationship integrity

### 4.1 Legacy ID scheme

Filenames embed the DLR cube's v1 long IDs: `do-156294`, `do-156302`, etc. These IDs increment by 8 (Tapelaying: 156294 → 156302 → ... consistently). The collection-level container IDs in the importer config are: `collection_id: 110748`, `file_container_id: <per-step IDs>`.

**These IDs are meaningless in the nuclide fork** — Shepard uses UUID-based `shepardId` now. They should NOT be used as Shepherd object identifiers. They can be stored as a `legacy_id` attribute for cross-referencing with DLR cube data.

### 4.2 `.done` files as completeness markers

Each `.json.done` file (e.g., `do-156294.json.done`) means the legacy importer successfully processed that DataObject. 42,229 `.done` files vs 81,709 `.json` files = ~52% completion rate overall (~100% in tapelaying, ~70% in bridge welding, 0% in welding steps).

**Ingestion skip logic:** A `.done` file adjacent to a `.json` can be treated as "previously imported." During re-ingestion, these can be fast-tracked (metadata already known) or skipped if we accept the legacy import as canonical. We should NOT skip — we're re-importing to the new Shepard with correct shapes.

### 4.3 Predecessor/Successor chain reconstruction

The AFP hierarchy (Run → PlyGroup → Layup → Layer → Track) is fully recoverable from the folder structure. Use `PARENT_OF` edges in Neo4j:

```
AFP Run (Collection/top-level DO)
  └─ PlyGroup-01 (DO)
       └─ Layup-001 (DO)
            └─ Layer-001 (DO)
                 └─ Track-0001 (DO)
                      ├─ FSDSet.csv → TimeseriesRef
                      ├─ PointCloud.csv → SpatialRef
                      └─ ProfileSet.tif → FileRef
```

Welding steps chain to tapelaying via `SUCCESSOR_OF`:

```
AFP Run → [Stringerschweißen] → [Punktschweißen] → [Brückenschweißen] → [Spantverbindung] → [Cleats]
```

Currently only tapelaying and bridge welding have data. The intermediate steps are placeholders — represent as DOs with `status=PLANNED` until data arrives.

---

## 5. Ingestion process order

### Phase 0: Pre-extraction decision

**Option A: Extract to UNAS.** Extract 107 GB to `/mnt/pve/unas/dump/dataset/` (UNAS has 19 TB free). Single command:
```bash
7z x /mnt/pve/unas/dump/mffd-data.7z -o/mnt/pve/unas/dump/ -y
```
Time: ~15–30 min (NFS-limited, Method=Copy so no decompression). After this, all files readable at `/mnt/pve/unas/dump/mffd-data/dataset/`.

**Option B: Stream from archive.** Use `7z e <archive> <file> -so` to stream individual files. Viable for small subsets (reading a track's JSON); impractical for 228K files at scale.

**Recommendation: Option A.** Extract once to UNAS, then run importer against the filesystem. At 107 GB (no compression), extraction time ≈ time to copy 107 GB over NFS. The UNAS has the capacity.

### Phase 1: Pre-import DB prep

Execute these before any data lands in TimescaleDB:

```sql
-- Hourly chunk interval (default 7d is wrong for sub-second sensor data)
SELECT set_chunk_time_interval('timeseries_data_points', INTERVAL '1 hour');

-- Space partitioning (parallel writes across timeseries_id)
SELECT add_space_partitions('timeseries_data_points', 'timeseries_id', 4);
```

Also: enable PgBouncer transaction mode for the importer workers (already shipped, verify config).

### Phase 2: AFP Tapelaying ingest

This is the highest-value step and has the cleanest data.

**Tool:** Extended v16 importer (PRESERVE-HIERARCHY, 4 workers). The importer already handles the hierarchy reconstruction.

**Execution:**
```
/mnt/pve/unas/dump/mffd-data/dataset/1-Tapelaying/AFP_52185/
```

**Per-track dispatch plan:**
1. Walk directory tree: AFP Run → PlyGroup → Layup → Layer → Track
2. For each track: create DataObject (with `legacy_id` attribute set to numeric ID from filename)
3. Attach FileReference to `ProfileSet.tif` (FileContainer, upload to Garage S3)
4. Parse `FSDSet.csv` → ingest as TimeseriesReference (channel: fiber stress data)
5. Parse `PointCloud.csv` → hold for spatial plugin (SP1); store as FileReference for now
6. Parse `ProfileSet.csv` → StructuredDataReference
7. Record `CameraConfig.csv` as FileReference (calibration artifact)
8. Set PARENT_OF chain from folder hierarchy

**Skip logic:** If `do-XXXXX.json.done` exists, log "previously imported" but still ingest (legacy export is incomplete).

**Estimated time:** At ~1 DO/s with 4 workers = ~4 DO/s. 3,525 tracks × 5 containers = ~17,600 objects. Estimate: 1–2 hours.

### Phase 3: Bridge Welding (Brückenschweißen) ingest

Path: `4-Brückenschweißen/mffd-framewelding/`

Same pattern as tapelaying but without CSV/TIF sidecars (metadata only). 11,099 DataObjects.

For each DO:
1. Create DataObject with name from folder, `legacy_id` from filename
2. Set PREDECESSOR_OF link to AFP tapelaying collection (welding follows layup)
3. Mark `status=PUBLISHED` if `.done` file exists, else `status=DRAFT`

No file payloads in dump. Note: SVDX scope data for this step exists on N: drive (see §6.2).

**Estimated time:** ~45 min.

### Phase 4: Placeholder process steps

For steps 2, 5, 6 (no data in dump):

Create a single DataObject per step with:
- `name = "Stringerschweißen"` / `"Spantverbindung"` / `"LBR Cleats"`
- `description = "Placeholder — source data on N:\\Messdaten\\MFFD_Demonstrator; requires SVDX conversion"`
- `status = DRAFT`
- SUCCESSOR_OF chain to previous step

### Phase 5: Spot Welding (Punktschweißen) — stub

Same as Phase 4. 9 files total in dump = just the directory entry.

---

## 6. Extension ideas — what else can be ingested

### 6.1 Stringer welding thermal images (high value)

**Source:** N: drive `Stringer_schweissungen/P14_S_2teBahn/` etc. — 6,000–6,500 `.tiff` per stringer pass. These are high-resolution thermal camera frames from the CRW (Continuous Resistance Welding) head scanning the stringer as it bonds.

**Shepard shape:** `ImageBundle` plugin (pending design) OR individual FileReferences per frame OR a PointCloud-like spatial sequence. Given the volumetric nature (6k frames × N stringers), an `ImageBundle` with temporal ordering would be ideal.

**Prerequisite:** ImageBundle plugin (#34 / project_imagebundle_design.md). Without it, these land as ~60,000 FileReferences which is technically correct but not queryable.

**Value:** The thermal frames show the weld line quality in real time. Combined with the SVDX scope data (temperature setpoint vs actual), this is the primary quality assurance dataset for Stringer welding — the highest scientific value data in the whole MFFD campaign.

### 6.2 SVDX Beckhoff TwinCAT time-series

**Source:** N: drive `Stringer_schweissungen/` (137 `.svdx`) and `Punktschweißungen/` (21 `.svdx`) and `Scope_Sicherung/` (23 `.svdx`).

**Format:** Beckhoff TwinCAT Scope v2 — binary proprietary format. Contains: timestamp, channel name, unit, value array.

**Conversion tool:** `tool_sources/tdms2csv/tdms2csv.py` — Python CLI that reads TDMS/SVDX files and outputs CSV. Ready to use.

**Shepard shape:** After conversion, ingest as TimeseriesReference into TimescaleDB. Each SVDX file = one process event (a weld seam). Channels: temperature setpoint, temperature actual, force, speed, energy — typically 10–20 channels per file.

**Volume:** ~180 SVDX files × 20 channels × ~30s at 1 kHz = ~540M data points. This is the single largest data ingestion task.

**DB prep prerequisite:** Chunk interval and space partitioning (Phase 1 above) must be applied first.

### 6.3 Thermal camera (ThermoCam)

**Source:** N: drive `ThermoCam/` — 7.9 GB. Empty parent folder in tree; actual data in subdirs not inventoried. Likely `.avi` or `.mp4` + metadata.

**Action needed:** Manual inspection of N: drive to enumerate actual files. Possible Shepard shape: VideoReference (existing) or FileReference.

### 6.4 Test evaluation data (Testauswertung)

**Source:** `.mp4` video (901 MB), `.svdx` (255 MB), `.txt` (50 MB), `.avi`. Post-processing results and summary videos.

**Shape:** FileReference (video), SVDX → TS after conversion, `.txt` → lab journal entries or StructuredData.

### 6.5 AAS (Asset Administration Shell) integration

**tool_sources** contains 5 AAS repositories: `aas-environment-starter`, `aas-models`, `aas-webviewer`, `faaast_aas_host`, `AasxPackageExplorer`. These are the ZLP AAS infrastructure stack.

The MFFD assets (AFP robot, CRW head, spot welding machine) each have an AAS submodel in the DLR system. `aas-models/` likely contains the AASX packages.

**Opportunity:** Load AASX packages as FileReferences on the AFP Run and Welding step DataObjects. This establishes the Equipment → Process → Data traceability chain that ISO AP242 requires.

**Backlog item:** This is the `shepard-plugin-aas` design space. Not in current roadmap but the data is present.

---

## 7. Redundancies and cleanup

| Redundancy | Count | Resolution |
|------------|-------|-----------|
| `.json.done` alongside `.json` | 42,229 | Read `.json` for metadata; use `.done` as provenance marker only |
| `AFP_52185/` vs `mffd-tapelaying/` in step 1 | 1 overlap | `AFP_52185` is the legacy collection root; `mffd-tapelaying` is the importer working dir. Use `AFP_52185` as the canonical hierarchy. |
| Scope_Sicherung = backup of live scope files | ~23 files | Skip `Scope_Sicherung` — duplicate of the live SVDX files in the step folders |
| `.json` DataObjects are 0-byte in dump | 81,709 | Attributes must be re-derived from sidecar files. Do not parse these as metadata sources. |

---

## 8. Toolchain decision

| Option | Pro | Con |
|--------|-----|-----|
| **v16 importer (PRESERVE-HIERARCHY)** | Tested, 4-worker, PROV-O writeback, handles hierarchy | Written for MFFD-Dropbox shape — needs adaptation for AFP 5-level tree |
| **shepardify** | Mirrors any folder to Shepard | No semantic type inference, no TS parsing, no spatial routing |
| **Custom script per process step** | Can be precise for each step's schema | More code, slower to develop |

**Recommendation:** Extend the v16 importer with a `MffdDumpSource` that understands the AFP directory structure and the per-track sidecar file types. The existing 4-worker + backoff + PROV-O framework is valuable and should be reused (per `feedback_reuse_before_reimplement.md`).

**New importer class:** `de.dlr.shepard.v2.importer.sources.MffdDumpSource` — reads from extracted filesystem, dispatches per-track object creation, routes CSV to TS or Structured based on filename, routes TIF to FileContainer.

---

## 9. Tool sources of note

The `tool_sources/` folder contains 80+ prototype tools built around Shepard and the MFFD dataset. The highest-value ones for feature ideas:

| Tool | What it does | Shepard feature idea |
|------|-------------|---------------------|
| `mffd_alizier` (Dash+VTK) | 3D spatial viewer with Shepard search integration | Trace3D view / shepard-plugin-spatial frontend |
| `ff_detektion` (RealSense) | Real-time Intel RealSense depth camera fiber flaw detection | Live-stream QA result → Shepard TS via home-showcase pattern |
| `tdms2csv` | TDMS/SVDX → CSV converter | Needed for SVDX welding data ingest |
| `shepard_llm` | LLM + ChromaDB embeddings over Shepard metadata | `shepard-plugin-ai` semantic search candidate |
| `pgvector_test` | pgvector embedding search prototype | Vector-based DataObject similarity (already have PG) |
| `langchain_experiments` | LangChain pipelines on sensor data | Auto-annotation from uploaded PDFs |
| `hotstuff_jupyter_analysis` | Analysis notebooks for welding SVDX data | JupyterHub integration reference |
| `shepard-mcp` | Older MCP tool prototype | Reference for current MCP plugin |
| `odix-onto` | ODIX manufacturing ontology | Ontology-first annotation design (aidocs/95) |
| `aas-*` (5 repos) | Full AAS stack (Eclipse FAAAST, webviewer) | `shepard-plugin-aas` substrate |
| `nsr_process_model` | NSR process ontology model | MFFD process step vocabulary |
| `Faserwinkelauswertung.py` | Fiber angle measurement script | QA metric: fiber deviation angle per ply |
| `ap242_pyocc` | AP242 + OpenCASCADE geometry | CAD geometry with ISO AP242 metadata |
| `herfuse_experiment_manager` | Experiment manager (HerFUSE project) | Experiment orchestration design reference |

---

## 10. Interesting data analysis cases

These are the analysis opportunities grounded in the actual data:

### Case A: AFP layup quality prediction
**Data:** Per-track `FSDSet.csv` (fiber stress) + `ProfileSet.tif` (inspection image) for all 3,525 tracks across all ply groups.  
**Question:** Can the FSD stress data alone predict whether a profile inspection will show a gap or overlap defect?  
**Method:** Binary classification (gap/overlap vs. OK) on the TS window per track. Labels can be derived from the profile TIF images using the `ff_detektion` RealSense pipeline pattern — the TIFs already encode the outcome.  
**Impact:** A model trained on this data would flag anomalous tracks before the quality inspector reviews the image. This is a direct EN 9100 corrective action trigger.

### Case B: Welding energy budget analysis
**Data:** SVDX scope data from `Stringer_schweissungen/` — 137 files, ~20 channels, temperature/force/energy per weld seam. Dates span 2023-03-22 to 2023-04-04 (12 days).  
**Question:** Which welding parameters (setpoint temperature, travel speed, consolidation force) most strongly correlate with weld seam quality (NDT outcome)?  
**Method:** After SVDX → CSV conversion via `tdms2csv`, compute per-seam energy integrals and correlate against NDT pass/fail outcomes (if NDT data is available or can be reconstructed from the wiki export).  
**Impact:** Directly supports the Clean Aviation JU KPI of energy savings — quantifying the minimum viable energy input per unit weld quality.

### Case C: Stringer TIFF thermal trail analysis
**Data:** 6,000–6,500 `.tiff` per stringer pass in `Stringer_schweissungen/P14_S_2teBahn/` etc. These are thermal camera frames at the CRW welding head.  
**Question:** What is the spatial distribution of temperature deviation from setpoint along the weld line? Can a ply-level quality map be reconstructed from the thermal frames?  
**Method:** Load TIFF sequence, register frames to a coordinate system (using AFP robot position log as ground truth), generate a 2D heat map of |T_actual - T_setpoint|.  
**Impact:** The `mffd_alizier` Dash+VTK prototype already does this for point cloud data. Extending it to thermal frames gives the first true 2D quality heatmap of an MFFD panel.  
**Prerequisite:** `ImageBundle` plugin for storing the frame sequence; Trace3D view for visualization.

### Case D: Process chain traceability graph analysis
**Data:** The full AFP + welding hierarchy as a Neo4j graph (after ingestion), ~8,500+ DataObjects.  
**Question:** For each Track that was flagged as anomalous by the AFP robot QA system, can we trace which weld seams on which stringer passes were subsequently affected?  
**Method:** Graph traversal: anomalous Track → parent Layer → parent Layup → region → neighboring Stringer seams. This is the key EN 9100 PAAR (Product Anomaly and Audit Report) use case.  
**Impact:** Makes Shepard's provenance graph a direct audit tool for regulatory compliance. The `onto2pddl` tool in tool_sources (planning-from-ontology) suggests someone already thought about this for automated process planning.

### Case E: SVDX inter-seam drift detection
**Data:** Collins partner data in `Scope_Sicherung/Collins/` — 7 SVDX files from May–Nov 2022, pre-dating the main campaign.  
**Question:** Did the Collins welding process drift between the 2022 commissioning runs and the 2023 production runs?  
**Method:** Convert both batches via `tdms2csv`, compute per-channel statistics (mean, std, skew) per session, run a statistical drift test (CUSUM or ADWIN).  
**Impact:** Cross-partner quality comparison. Directly relevant to MFFD multi-partner integration story.

---

## 11. Recommended execution sequence

```
[ 1 ] Extract archive to UNAS
        7z x /mnt/pve/unas/dump/mffd-data.7z -o/mnt/pve/unas/dump/ -y
        (estimated: 20-40 min depending on NFS bandwidth)

[ 2 ] Apply TimescaleDB pre-import settings (§5, Phase 1)

[ 3 ] Ingest AFP Tapelaying hierarchy (§5, Phase 2)
        Estimated: 1-2 hours, 4 workers

[ 4 ] Ingest Bridge Welding metadata (§5, Phase 3)
        Estimated: 45 min

[ 5 ] Create placeholder DOs for empty steps (§5, Phase 4-5)
        Estimated: 5 min

[ 6 ] Convert Stringer SVDX files via tdms2csv (§6.2)
        Requires: N: drive access or SVDX files transferred to nuclide
        Estimated: 1-2 hours conversion, then 2-3 hours TS ingest

[ 7 ] Ingest Stringer thermal TIFFs (§6.1)
        Requires: ImageBundle plugin
        Deferred until plugin ships

[ 8 ] Infusion data
        Separate agent (vi_importer toolchain)
```

---

## 12. Open questions

1. **SVDX transfer:** The stringer and welding SVDX files are on the N: drive, not in the dump. Can they be transferred to the UNAS mount? Estimated size: ~350 GB total.
2. **NDT outcomes:** Is there a structured record of NDT pass/fail per weld seam? The Confluence wiki export may have this as a table — worth parsing.
3. **AAS packages:** Where are the AASX files for the AFP robot and CRW machine? Are they in `tool_sources/aas-models/`?
4. **MFFD_Demonstrator_Recursive_Tree.json ownership:** This was generated as a Windows PowerShell script output (`N:\Messdaten\...` paths). Is the full N: drive accessible via UNAS or is it on a separate DLR Augsburg NAS?

---

*Reconnaissance: 2026-05-26. Archive not yet extracted. All file counts and structure from `7z l` listing.*
