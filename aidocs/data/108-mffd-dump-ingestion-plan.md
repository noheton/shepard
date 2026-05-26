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
| `tool_sources/` | 47,444 | 80+ prototype tools — see §9 |
| `importer/` | 21 | Legacy Python importer code |

**File type breakdown across all data steps:**

| Extension | Count | Category |
|-----------|-------|----------|
| `.json` | 81,709 | DataObject metadata (legacy Shepard export) |
| `.done` | 42,229 | Import progress markers from legacy run |
| `.csv` | 19,521 | Structured data (CameraConfig INI, FSDSet, PointCloud, brush data) |
| `.tif` | 3,525 | AFP inspection images (per-track TPS profile images) |

### 1.2 Legacy DataObject format

Each DataObject was exported as `do-<legacy_numeric_id>.json`. The numeric IDs (e.g., `do-156294`) are the old v1 long IDs from the DLR cube instance. These files are **0-byte** in the archive — the JSON export was empty shell records from the legacy system, carrying only the naming relationship.

**Implication:** We cannot reconstruct DataObject metadata from the `.json` files. The hierarchy is recoverable from the folder structure; the attribute content must be re-derived from the CSV/TIF/INI sidecar files.

### 1.3 Dump payload completeness

A survey of the tapelaying `references/` folders shows that the MongoDB GridFS export is highly incomplete:

- **131,212 OID-named payload files** exist in the archive
- **8,181 are non-zero** (~6.2%) — mainly inspection TIFFs (2–9 MB), CameraConfig INI files (~4 KB), KUKA robot programs (`.src`), and PNG thumbnails
- **123,031 are 0-byte** (~93.8%) — the FSD CSV and PointCloud CSV payloads were recorded as reference entries in MongoDB but the binary data was not exported to the dump

**Consequence:** Raw TPS sensor measurements (FSDSet rows, PointCloud rows) for most tracks are **not recoverable from the dump**. The full measurement data must come from the AFP system's N: drive output. Inspection images and calibration files ARE preserved and fully usable.

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

**SVDX format constraint:** `.svdx` = Beckhoff TwinCAT Scope export (proprietary binary). This format appears **exclusively in the welding steps** (Stringer, Punkt, Brücke, Scope_Sicherung). It has no presence in the AFP tapelaying data. Requires conversion with `tdms2csv` (included in `tool_sources/`) before ingest into TimescaleDB. This is the primary blocker for welding time-series data.

---

## 3. Data shape mapping

### 3.1 AFP Tapelaying (step 1) — fully in dump

**Hierarchy:** AFP Run → PlyGroup → Layup → Layer → Track (5 levels)

#### 3.1.1 TPS (Tape Profile Sensor) system

All five per-track sidecar files are co-registered outputs of a **single TPS system** — a line-scan laser profilometer mounted on the AFP robot head. The sensor fires a laser line across the tape width with each robot advance step. Each trigger cycle produces one **scan line** recorded in all five files simultaneously.

| File | Shepard shape | Content |
|------|--------------|---------|
| `CameraConfig.csv` (INI format) | DataObject attribute or StructuredDataReference | Sensor calibration, robot variables, track parameters — small (~4 KB), per-track |
| `FSDSet.csv` | TimeseriesReference | Fiber stress data — main process time-series; 16 columns, one row per scan line |
| `PointCloud.csv` | `shepard-plugin-spatial` SpatialContainer | 3D surface scan positions and RGB classification — 6 columns, no header, one row per scan line |
| `ProfileSet.csv` (brush data) | StructuredDataReference | Scanner trigger and AOI acquisition metadata — 13 columns, one row per scan line |
| `ProfileSet.tif` | FileReference (FileContainer) | Profile inspection image — 2048 px wide, height = scan line count; TIF row N = scan line N |

**Volume:** ~3,525 tracks × 5 files = ~17,600 data objects when fully expanded.

**Payload availability:** `ProfileSet.tif` and `CameraConfig.csv` are preserved in the dump (non-zero OIDs). `FSDSet.csv` and `PointCloud.csv` are 0-byte in the dump for most tracks — authoritative source is N: drive (see §1.3).

#### 3.1.2 Scan-line co-registration via `Packet` index

All three measurement files share a common `Packet` (0-based integer) row index. Row N in any file describes the **same physical scanner position** during the same robot advance step:

```
FSDSet.csv row N  ←─┐
PointCloud.csv row N ├─ Packet N = single scanner trigger event
ProfileSet.tif row N ─┘   (row index 0-based, height equals scan line count)
```

Row count is consistent across all three for a given track. For the sampled track (Ply 1, Track 98): **1,086 scan lines**.

`ProfileSet.csv` uses `frameCnt` (1-based) as its row key: `frameCnt = Packet + 1`.

#### 3.1.3 Column schemas (confirmed by extraction)

**FSDSet.csv** — 16 columns, header row present:

| Column | Type | Description |
|--------|------|-------------|
| `Packet` | int | Scan line index (0-based) — reconciliation key |
| `SysTime` | float | System time (seconds since process start) |
| `Time` | float | Elapsed time (seconds) |
| `PosX`, `PosY`, `PosZ` | float (mm) | Robot TCP position in base frame |
| `PosA`, `PosB`, `PosC` | float (deg) | Robot TCP orientation (Euler) |
| `Count`, `Tick` | int | Hardware counter and tick value |
| `IOS`, `GPR` | int | I/O status and GPR register |
| `VAxis` | float (mm) | Position along lay direction |
| `ChronoTimeInUs` | int64 | **Unix timestamp in microseconds** — e.g. `1671543086039925` = 2023-01-20 14:24 UTC |
| `TrackPic` | str | Track picture filename or `none` |

**PointCloud.csv** — 6 columns, no header:

| Column | Type | Description |
|--------|------|-------------|
| X, Y | float (mm) | Robot TCP position — matches FSDSet PosX/PosY exactly |
| Z | float (mm) | TPS-measured surface height (FSDSet PosZ − ~41 mm = sensor standoff) |
| R, G, B | uint8 | RGB classification color |

**ProfileSet.csv (brush data)** — 13 columns, 2-line preamble, then header:

Preamble: `n_profileFrames,3` / `total_profiles,<N>`

| Column | Type | Description |
|--------|------|-------------|
| `timeStamp` | float (s) | Relative time since process start |
| `triggerCoord` | int | Encoder position at trigger event |
| `triggerStatus` | int | Trigger status flags |
| `frameCnt` | int | Frame counter (1-based = Packet + 1) — reconciliation key |
| `DAC`, `ADC` | int | DAC/ADC register values |
| `INT_idx`, `AOI_idx` | int | Index values |
| `AOI_ys` | int (px) | AOI y-start pixel — tracks tape surface height |
| `AOI_dy` | int (px) | AOI height in pixels |
| `AOI_xs` | int (px) | AOI x-start pixel |
| `AOI_trsh` | int | AOI intensity threshold |
| `AOI_alg` | int | AOI algorithm selector |

**CameraConfig.csv** (INI format) — key fields:

| Section / Key | Example value | Description |
|---------------|--------------|-------------|
| `[Calibration values]` Height in mm | `0.001689 -11.818787` | Polynomial: pixel → mm height conversion |
| `[Calibration values]` mm on y-axis | `0.000000 -0.000003 0.041397` | Polynomial: pixel → mm lateral position |
| `SensorWidth` | `2048` | Sensor width in pixels |
| `AcquisitionFrameRateAbs` | `180.603` Hz | Acquisition rate |
| `PixelFormat` | `Mono16` | 16-bit intensity per pixel |
| `[TPS_TCP in MTLH_TCP]` X | `-137.44 mm` | TPS-to-robot-TCP offset |
| `FSD start time in ms` | `1671544708182` | Unix epoch ms — track acquisition start |
| `FSD stop time in ms` | `1671544728662` | Unix epoch ms — track acquisition end |
| `VC_PlyNumber` | `1` | Ply number |
| `VC_TrackNo` | `98` | Track number within ply |
| `VC_Track_Length` | `1872.35 mm` | Track length |
| `VC_Tow_Width` | `12.70 mm` | Tow width (3-tow 12.7 mm format) |

#### 3.1.4 Timestamp reconciliation for TimescaleDB

`FSDSet.csv.ChronoTimeInUs × 1000 = nanoseconds` → use directly as the TimescaleDB `time` column. This gives absolute wall-clock alignment across all tracks.

`CameraConfig.csv FSD start time in ms × 1_000_000 = nanoseconds` → use as the track-level anchor for gap detection (tracks with no FSD data can still be placed on the timeline).

`ProfileSet.csv.timeStamp` is a relative counter (seconds since process start), not an absolute timestamp. Absolute time: `FSD_start_ns + (timeStamp × 1e9)`.

---

### 3.2 Track DataObject structure

A single Track DataObject with its four reference groups (matching the legacy importer's `create_file_reference` call pattern):

```
Track-NNNN  (DataObject)
│  attributes:
│    legacy_id: "do-156294"
│    vc_ply_number: 1
│    vc_track_no: 98
│    vc_track_length_mm: 1872.35
│    vc_tow_width_mm: 12.70
│    track_angle_deg: 0.0
│    fsd_start_unix_ms: 1671544708182
│    fsd_stop_unix_ms: 1671544728662
│    tps_sensor_width_px: 2048
│    tps_fps: 180.603
│
├─ [FileRef group 1] "TPS raw data"
│   ├─ FSDSet.csv         → TimeseriesContainer (per-track, new design — see note)
│   │    channels (9 total):
│   │      "PosX"   (mm, robot TCP X position)
│   │      "PosY"   (mm, robot TCP Y position)
│   │      "PosZ"   (mm, robot TCP Z position)
│   │      "PosA"   (deg, robot TCP roll  / Euler A)
│   │      "PosB"   (deg, robot TCP pitch / Euler B)
│   │      "PosC"   (deg, robot TCP yaw   / Euler C)
│   │      "VAxis"  (mm, lay direction along tool axis)
│   │      "IOS"    (int, I/O status word)
│   │      "GPR"    (int, GPR register)
│   │    timestamp source: ChronoTimeInUs × 1000 → ns
│   │    row key: Packet (0-based integer)
│   │    NOTE: 0-byte in dump for most tracks; full data from N: drive
│   ├─ CameraConfig.csv  → StructuredDataReference (INI key-value, ~4 KB)
│   │    content: calibration polynomials, robot variables, track parameters
│   │    preserved: yes (non-zero in dump)
│   └─ ProfileSet.csv    → StructuredDataReference ("brush data")
│        columns (13): timeStamp, triggerCoord, triggerStatus, frameCnt,
│                      DAC, ADC, INT_idx, AOI_idx, AOI_ys, AOI_dy,
│                      AOI_xs, AOI_trsh, AOI_alg
│        row key: frameCnt - 1 = Packet
│        NOTE: 0-byte in dump for most tracks
│
├─ [FileRef group 2] "TPS 3D pointclouds"
│   └─ PointCloud.csv   → SpatialContainer  [shepard-plugin-spatial / SP1]
│        columns: X, Y, Z (mm), R, G, B (no header row)
│        spatial CRS: robot base frame (mm)
│        row key: Packet (0-based) = FSD row key (co-registered)
│        NOTE: 0-byte in dump for most tracks; full data from N: drive
│
├─ [FileRef group 3] "TPS intermediate evaluation files"
│   └─ ProfileSet.tif   → FileContainer
│        format: TIFF, 2048 px wide, height = scan line count
│        content: laser line profile image — TIF row N = scan line N = Packet N
│        preserved: yes (non-zero in dump for inspected tracks)
│
└─ [FileRef group 4] "Robot program"
    └─ <track>.src       → FileContainer
         format: KUKA KRL program (.src)
         content: robot motion program for this track
         preserved: yes (non-zero in dump)
```

**Co-registration diagram** — all four groups share `Packet` as join key:

```
Packet (0-based integer)
    │
    ├─ FSDSet row N       → ChronoTimeInUs × 1e3 = absolute ns timestamp
    │                        + 9 channels (TCP position + orientation + flags)
    ├─ PointCloud row N   → X/Y/Z surface point (mm) + RGB classification
    ├─ ProfileSet.tif row N → 2048-px laser cross-section image row
    └─ BrushData row N    → AOI_ys (tape height px), trigger/AOI metadata
```

> **Legacy shared-container note:** The DLR cube importer (`/tmp/mffd_importer/main.py`)
> stored ALL tracks' FSD channels in a SINGLE shared TimescaleDB container (ID 6603)
> and created a `TimeseriesReference` per track with a time window `[fsd_start_ns,
> fsd_stop_ns]`. The 4,883 `ts-NNNNN` entries in the dump are those references —
> all point to container 6603, which itself was 0-byte in the export.
> **The new dump-based ingestion will use per-track containers** (one `TimeseriesContainer`
> per track). This is cleaner for querying, avoids the shared-container contention,
> and aligns with the `TS-IDc` / appId migration design. The time-window model is
> not replicated.

> **Multi-source timeseries design (2026-05-26):** The current dump contains TPS/FSD
> channels only. Additional timeseries sources are expected in a subsequent dump:
> tape laying head data (consolidation force, temperature, lay pressure) and robot
> joint data (J1–J6 axis positions). Each source will get its own named
> `TimeseriesContainer` + `TimeseriesReference` under the same Track DataObject:
>
> ```
> Track-NNNN  (DataObject)
> ├─ TimeseriesReference "TPS/FSD"         → TimeseriesContainer (PosX/Y/Z, PosA/B/C, VAxis, IOS, GPR)
> ├─ TimeseriesReference "Robot Joints"    → TimeseriesContainer (J1..J6 joint angles)  [when available]
> └─ TimeseriesReference "Tape Head"       → TimeseriesContainer (force, temp, lay pressure)  [when available]
> ```
>
> The `TimeseriesReference.name` field carries the source label. Future platform work
> (backlog `MFFD-TS-MULTIREF-01`) will allow a single Reference to select channels
> from multiple containers — until then, multiple named References per DataObject is
> the correct pattern. UI design: graphical channel + interval selector (see task #228).

---

### 3.3 Bridge/Frame Welding (step 4) — partially in dump

Sub-directory: `mffd-framewelding/`. Same `.json` pattern as tapelaying. 11,099 DataObjects.

No CSV/TIF sidecar structure — welding DOs are metadata records pointing to SVDX scope files that are on the N: drive but not in the dump.

### 3.4 Spot Welding (step 3) — minimal in dump

Only 9 files total: the folder `Punktschweißungen/` exists but contains only a top-level placeholder. The actual data (`.svdx`, `.xlsx`, `.csv`) lives on the N: drive.

### 3.5 Confluence Wiki (99-WIki)

`mffd-confluence-space-export/` contains the HTML Confluence export + `confluence-analysis.md`. This maps to the existing `MFFD-Dropbox` Collection already partially processed by task #137. Treat as already handled — don't re-ingest.

---

## 4. Link / relationship integrity

### 4.1 Legacy ID scheme

Filenames embed the DLR cube's v1 long IDs: `do-156294`, `do-156302`, etc. These IDs increment by 8 (Tapelaying: 156294 → 156302 → ... consistently). The collection-level container IDs in the importer config are: `collection_id: 110748`, `file_container_id: <per-step IDs>`.

**These IDs are meaningless in the nuclide fork** — Shepard uses UUID-based `shepardId` now. They should NOT be used as Shepard object identifiers. Store as a `legacy_id` attribute for cross-referencing with DLR cube data.

### 4.2 `.done` files — legacy provenance only

Each `.json.done` file (e.g., `do-156294.json.done`) records that the legacy importer touched that DataObject. 42,229 `.done` files exist.

**Do not use `.done` files as completeness markers or skip triggers.** The legacy import ran against the old Shepard on the DLR cube; it tells us nothing about what has been ingested into the nuclide fork. Treat `.done` files as provenance evidence that the DataObject existed in the legacy system — record the presence of a `.done` file as a `legacy_imported: true` attribute on the DataObject if desired, but do not branch ingest logic on it.

### 4.3 Predecessor/Successor chain reconstruction

The AFP hierarchy (Run → PlyGroup → Layup → Layer → Track) is fully recoverable from the folder structure. Use `PARENT_OF` edges in Neo4j:

```
AFP Run (Collection/top-level DO)
  └─ PlyGroup-01 (DO)
       └─ Layup-001 (DO)
            └─ Layer-001 (DO)
                 └─ Track-0001 (DO)
                      ├─ FSDSet → TimeseriesContainer
                      ├─ PointCloud → SpatialContainer
                      ├─ ProfileSet.tif → FileContainer
                      ├─ BrushData → StructuredDataContainer
                      └─ CameraConfig → StructuredDataContainer (or attr)
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

**Option B: Stream from archive.** Use `7z e <archive> <file> -so` to stream individual files. Viable for small subsets; impractical for 228K files at scale.

**Recommendation: Option A.** Extract once to UNAS, then run importer against the filesystem.

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
2. For each track: create DataObject with attributes from CameraConfig INI (track number, ply, length, tow width, start/stop timestamps) and `legacy_id` from filename
3. Attach FileReference to `ProfileSet.tif` (FileContainer, upload to Garage S3) — preserved in dump
4. Attach CameraConfig INI as StructuredDataReference — preserved in dump
5. Parse `FSDSet.csv` → ingest as TimeseriesReference using `ChronoTimeInUs × 1000` as ns timestamp — **only if non-zero in dump**; note source as `legacy_dump` or `n_drive` in attribute
6. Parse `PointCloud.csv` → hold for spatial plugin (SP1); store as FileReference for now — **only if non-zero**
7. Parse `ProfileSet.csv` (brush data) → StructuredDataReference — **only if non-zero**
8. Set PARENT_OF chain from folder hierarchy

**Estimated time:** At ~1 DO/s with 4 workers = ~4 DO/s. 3,525 tracks × up to 5 containers = ~17,600 objects. Estimate: 1–2 hours for metadata + available payloads.

### Phase 3: Bridge Welding (Brückenschweißen) ingest

Path: `4-Brückenschweißen/mffd-framewelding/`

Same pattern as tapelaying but without CSV/TIF sidecars (metadata only). 11,099 DataObjects.

For each DO:
1. Create DataObject with name from folder, `legacy_id` from filename
2. Set PREDECESSOR_OF link to AFP tapelaying collection (welding follows layup)

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

**Source:** N: drive `Stringer_schweissungen/P14_S_2teBahn/` etc. — 6,000–6,500 `.tiff` per stringer pass. These are high-resolution thermal camera frames from the CRW (Continuous Resistance Welding) head.

**Shepard shape:** `ImageBundle` plugin (pending design) OR individual FileReferences per frame. Given the volumetric nature (6k frames × N stringers), an `ImageBundle` with temporal ordering would be ideal.

**Prerequisite:** ImageBundle plugin (#34 / project_imagebundle_design.md). Without it, these land as ~60,000 FileReferences which is technically correct but not queryable.

**Value:** Combined with the SVDX scope data (temperature setpoint vs actual), this is the primary quality assurance dataset for Stringer welding — highest scientific value data in the whole MFFD campaign.

### 6.2 SVDX Beckhoff TwinCAT time-series (welding only)

**Source:** N: drive `Stringer_schweissungen/` (137 `.svdx`) and `Punktschweißungen/` (21 `.svdx`) and `Scope_Sicherung/` (23 `.svdx`). **This format is exclusive to welding steps — not present in AFP tapelaying.**

**Format:** Beckhoff TwinCAT Scope v2 — binary proprietary format. Contains: timestamp, channel name, unit, value array.

**Conversion tool:** `tool_sources/tdms2csv/tdms2csv.py` — Python CLI that reads TDMS/SVDX files and outputs CSV. Ready to use.

**Shepard shape:** After conversion, ingest as TimeseriesReference into TimescaleDB. Each SVDX file = one process event (a weld seam). Channels: temperature setpoint, temperature actual, force, speed, energy — typically 10–20 channels per file.

**Volume:** ~180 SVDX files × 20 channels × ~30s at 1 kHz = ~540M data points. This is the single largest data ingestion task.

**DB prep prerequisite:** Chunk interval and space partitioning (Phase 1 above) must be applied first.

### 6.3 Stringer welding process video (high value, N: drive only)

**Source:** N: drive `Stringer_schweissungen/` — **138 `.mp4` files** (process video of CRW head during resistance welding). Not in dump. Confirmed accessible from DLR systems.

**Shepard shape:** `VideoReference` → `FileContainer`. One video per welding pass (same granularity as SVDX scope files). The video and SVDX data for the same pass are co-temporal and should be co-located on the same weld-seam DataObject.

**N: drive mount prerequisite:** backlog item MFFD-NDRIVE-01 — set up N: drive mount on DLR cube before this step is reachable. Once mounted, ingest via a `VideoBatchImporter` (or manual upload for a pilot subset).

**Value:** Process video + simultaneous temperature/force scope data (SVDX) for the same weld seam is the strongest multi-modal quality-assurance record in the MFFD dataset.

### 6.4 Thermal camera (ThermoCam)

**Source:** N: drive `ThermoCam/` — 7.9 GB. Likely `.avi` or `.mp4` + metadata.

**Action needed:** Manual inspection of N: drive to enumerate actual files. Possible Shepard shape: VideoReference (existing) or FileReference.

### 6.5 Test evaluation data (Testauswertung)

**Source:** `.mp4` video (901 MB), `.svdx` (255 MB), `.txt` (50 MB), `.avi`. Post-processing results and summary videos.

**Shape:** FileReference (video), SVDX → TS after conversion, `.txt` → lab journal entries or StructuredData.

### 6.6 AAS (Asset Administration Shell) integration

**tool_sources** contains 5 AAS repositories: `aas-environment-starter`, `aas-models`, `aas-webviewer`, `faaast_aas_host`, `AasxPackageExplorer`.

**Opportunity:** Load AASX packages as FileReferences on the AFP Run and Welding step DataObjects. This establishes the Equipment → Process → Data traceability chain that ISO AP242 requires.

**Backlog item:** `shepard-plugin-aas` design space. Not in current roadmap but the data is present.

---

## 7. Redundancies and cleanup

| Redundancy | Count | Resolution |
|------------|-------|-----------|
| `.json.done` alongside `.json` | 42,229 | Provenance only — do not branch ingest logic on presence/absence |
| `AFP_52185/` vs `mffd-tapelaying/` in step 1 | 1 overlap | `AFP_52185` is the legacy collection root; `mffd-tapelaying` is the importer working dir. Use `AFP_52185` as the canonical hierarchy. |
| Scope_Sicherung = backup of live scope files | ~23 files | Skip `Scope_Sicherung` — duplicate of the live SVDX files in the step folders |
| `.json` DataObjects are 0-byte in dump | 81,709 | Attributes must be re-derived from sidecar files. Do not parse these as metadata sources. |
| 0-byte OID payloads for FSD/PointCloud | 123,031 | Skip payload upload; note `payload_available: false` on the container. Revisit when N: drive data is transferred. |

---

## 8. Toolchain decision

| Option | Pro | Con |
|--------|-----|-----|
| **v16 importer (PRESERVE-HIERARCHY)** | Tested, 4-worker, PROV-O writeback, handles hierarchy | Written for MFFD-Dropbox shape — needs adaptation for AFP 5-level tree and TPS file routing |
| **shepardify** | Mirrors any folder to Shepard | No semantic type inference, no TS parsing, no spatial routing |
| **Custom script per process step** | Can be precise for each step's schema | More code, slower to develop |

**Recommendation:** Extend the v16 importer with a `MffdDumpSource` that understands the AFP directory structure and the per-track sidecar file types. The existing 4-worker + backoff + PROV-O framework is valuable and should be reused (per `feedback_reuse_before_reimplement.md`).

**New importer class:** `de.dlr.shepard.v2.importer.sources.MffdDumpSource` — reads from extracted filesystem, dispatches per-track object creation, routes FSD CSV to TS using `ChronoTimeInUs`, routes PointCloud to SpatialContainer (or FileReference if SP1 not yet shipped), routes TIF to FileContainer, parses CameraConfig INI for DataObject attributes.

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
**Method:** After SVDX → CSV conversion via `tdms2csv`, compute per-seam energy integrals and correlate against NDT pass/fail outcomes.  
**Impact:** Directly supports the Clean Aviation JU KPI of energy savings — quantifying the minimum viable energy input per unit weld quality.  
**Blocker:** INTEGRAL aggregation unimplemented in `TimeseriesDataPointRepository.assertNotIntegral()` — must be addressed before energy budget computation via the API.

### Case C: Stringer TIFF thermal trail analysis
**Data:** 6,000–6,500 `.tiff` per stringer pass in `Stringer_schweissungen/P14_S_2teBahn/` etc. Thermal camera frames at the CRW welding head.  
**Question:** What is the spatial distribution of temperature deviation from setpoint along the weld line?  
**Method:** Load TIFF sequence, register frames to robot position log, generate a 2D heat map of |T_actual - T_setpoint|.  
**Prerequisite:** `ImageBundle` plugin for storing the frame sequence; Trace3D view for visualization.

### Case D: Process chain traceability graph analysis
**Data:** Full AFP + welding hierarchy as a Neo4j graph (after ingestion), ~8,500+ DataObjects.  
**Question:** For each Track flagged as anomalous by the AFP robot QA system, which weld seams on which stringer passes were subsequently affected?  
**Method:** Graph traversal: anomalous Track → parent Layer → parent Layup → region → neighboring Stringer seams. This is the key EN 9100 PAAR (Product Anomaly and Audit Report) use case.

### Case E: SVDX inter-seam drift detection
**Data:** Collins partner data in `Scope_Sicherung/Collins/` — 7 SVDX files from May–Nov 2022, pre-dating the main campaign.  
**Question:** Did the Collins welding process drift between the 2022 commissioning runs and the 2023 production runs?  
**Method:** Convert both batches via `tdms2csv`, compute per-channel statistics per session, run a statistical drift test (CUSUM or ADWIN).

---

## 11. Recommended execution sequence

```
[ 1 ] Extract archive to UNAS
        7z x /mnt/pve/unas/dump/mffd-data.7z -o/mnt/pve/unas/dump/ -y
        (estimated: 20-40 min depending on NFS bandwidth)

[ 2 ] Apply TimescaleDB pre-import settings (§5, Phase 1)

[ 3 ] Ingest AFP Tapelaying hierarchy (§5, Phase 2)
        Estimated: 1-2 hours, 4 workers
        Note: FSD/PointCloud payloads missing for most tracks (§1.3)
              CameraConfig + TIF payloads available

[ 4 ] Ingest Bridge Welding metadata (§5, Phase 3)
        Estimated: 45 min

[ 5 ] Create placeholder DOs for empty steps (§5, Phase 4-5)
        Estimated: 5 min

[ 6 ] Convert Stringer SVDX files via tdms2csv (§6.2)
        Requires: N: drive access or SVDX files transferred to nuclide
        Estimated: 1-2 hours conversion, then 2-3 hours TS ingest

[ 7 ] Back-fill FSD/PointCloud payloads from N: drive
        Once N: drive AFP data is accessible, re-run importer for
        non-zero payload population (overwrite placeholder containers)

[ 8 ] Ingest Stringer thermal TIFFs (§6.1)
        Requires: ImageBundle plugin
        Deferred until plugin ships

[ 9 ] Infusion data
        Separate agent (vi_importer toolchain)
```

---

## 12. Open questions

1. **FSD/PointCloud from N: drive:** Most raw sensor measurement payloads are 0-byte in the dump (§1.3). Can the full N: drive AFP output be transferred to UNAS? Estimated size: the 1-Tapelaying step in the dump is 107 GB total with sparse payloads; the full FSD/PointCloud data could be an order of magnitude more.
2. **SVDX transfer:** The stringer and welding SVDX files are on the N: drive. Can they be transferred to the UNAS mount? Estimated size: ~350 GB total.
3. **NDT outcomes:** Is there a structured record of NDT pass/fail per weld seam? The Confluence wiki export may have this as a table — worth parsing.
4. **AAS packages:** Where are the AASX files for the AFP robot and CRW machine? Are they in `tool_sources/aas-models/`?
5. **N: drive access:** Confirmed accessible from DLR systems (user 2026-05-26). Backlog item MFFD-NDRIVE-01 tracks setting up the mount on the DLR cube for scripted ingest. `MFFD_Demonstrator_Recursive_Tree.json` is a PowerShell tree dump of `N:\Messdaten\MFFD_Demonstrator\` — use it as the inventory reference for Phase 7 planning.
6. **Additional timeseries data (pending):** A supplementary dump containing tape laying head telemetry and robot joint data (J1–J6) for the AFP tracks is expected. When it arrives, each new source gets its own named `TimeseriesReference` + `TimeseriesContainer` per Track DO (see §3.2 multi-source note). The inventory of channels and file formats will be documented in this plan once the dump is received.
7. **Multi-container TimeseriesReference:** Current `TimeseriesReference` supports one container per reference. Long-term design (backlog `MFFD-TS-MULTIREF-01`): extend so a single Reference can span multiple containers, with graphical channel + interval selection in the UI. Until shipped, multiple named References per DataObject is the correct pattern.

---

*Reconnaissance: 2026-05-26. Archive not yet extracted. All file counts and structure from `7z l` listing and sample file extractions.*
