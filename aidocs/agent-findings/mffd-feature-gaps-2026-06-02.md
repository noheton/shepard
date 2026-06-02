---
name: MFFD feature-gap discovery (2026-06-02)
description: Features Shepard needs to make the MFFD real dataset immersive. Reads the data, names the gaps, files backlog rows.
type: project
stage: feature-defined
last-stage-change: 2026-06-02
---

# MFFD feature-gap discovery — 2026-06-02

Companion to the inventory (`mffd-data-inventory-2026-06-02.md`) and the import plan
(`aidocs/integrations/113-mffd-real-data-import-plan.md`). This doc says **what the MFFD
data wants from Shepard that Shepard doesn't yet give it.**

The bar is *immersive*, not "imports cleanly". A researcher visiting the demonstrator
should feel they're walking through the *process*, not browsing a folder tree.

## 0. Reading the data through five personas

| Persona | Walks in expecting | Hits today | Closes by |
|---------|---------------------|------------|-----------|
| **Senior Researcher** (Role 9) | "Show me TR-004 and the rework" | Predecessor chain on tapelaying ✓; bridge to NDT and to bridgewelding ✗ | GAP-4 (cross-step Predecessor edges) |
| **Digital Native** (Role 10) | "5-line Python pulls 192 channels into DataFrame" | Possible only after TS-IDc rename; today still needs 5-tuple | TS-IDc rename completes (#58 / #123) |
| **IME/AQE** (Role 4 EN 9100) | "Show NCR for ply 5, who repaired it, when was re-test passed" | No NCR_OPEN status; rework loops live in `nextParameterNumber` numbers | GAP-3 (NCR-as-first-class) |
| **RDM Steward** | "Cite this Collection with DOI + license + ORCIDs" | LIC1 shipped; PID mint still pending | covered by LIC1 follow-up |
| **Process Engineer** | "Hover a track, see TCP-temp swept against ply schedule" | Trace3D is for a single track; cross-track view doesn't exist | GAP-2 (cross-track view) |

## 1. Top-12 immersion gaps

Listed in **highest-value-first** order (effort × user value).

### GAP-1 — `.svdx` parser plugin — **PARTIALLY CLOSED 2026-06-02**

**The data (corrected):** **21** Beckhoff TwinCAT Scope (`.svdx`) files in one
folder (`Punktschweißungen`), 7 MB .. 1.4 GB each. Earlier scoping mentioned 166
files across three folders; the additional `Scope_Sicherung` and
`Stringer_schweissungen` folders do not exist on the mount. The 73 GB total
across the campaign holds, but in one folder. Without a parser they land as
opaque blobs.

**The gap (refined):** The proprietary binary sample section of `.svdx` is
**not publicly documented by Beckhoff**. The community-standard
[`pytcs`](https://github.com/CagtayFabry/pytcs) tool explicitly punts on the
binary and reads only the CSV/TXT exports the operator-driven TwinCAT Scope
Export Tool produces. Sibling-CSV coverage in the actual dataset is **3/21 ≈
14%**, too thin to be the primary user-facing path.

**What shipped (tier-1 — `MFFD-PLUGIN-SVDX-1`, this PR):**
`shepard-plugin-fileformat-svdx`, mirroring `fileformat-thermography` and
`fileformat-robotics`. The parser decodes the 16-byte envelope, locates and
StAX-parses the trailing `<ScopeProject>` XML manifest, and emits
`urn:shepard:svdx:*` semantic annotations on the parent FileReference:
`formatVersion`, `projectGuid`, `projectName`, `dataPoolGuid`, `mainServer`,
`recordTimeNs`, `autoSaveMode`, `assemblyName`, `channelCount`,
`acquisitionCount`, deduped `amsNetId` / `port` / `dataType` lists, every
`channelName` (rendered chart channel) and `symbolName` (ADS data source),
plus `companionCsv` when a sibling `<basename>.csv` or `<basename>_parsed.csv`
sits in the same FileContainer. Real MFFD fixture test confirms the parser
extracts 46 channels + 149 acquisitions per file with full symbol-name
fidelity. 22 JUnit tests, all green.

**What's deferred (filed as concrete follow-up rows):**
- `MFFD-PLUGIN-SVDX-BINARY-PARSER-1` — reverse-engineer the proprietary binary
  sample blocks (multi-week research, may never resolve cleanly).
- `MFFD-PLUGIN-SVDX-CSV-INGEST-1` — backend service that consumes the
  operator-driven TwinCAT Scope Export Tool CSV output and populates a
  TimeseriesReference. The realistic ingestion path given binary opacity.
- `MFFD-PLUGIN-SVDX-SEMANTIC-1` — auto-detect physical-quantity semantics from
  the `GVL_IO_US_Endeffektor.aTemperature*` Beckhoff naming convention.
- `MFFD-PLUGIN-SVDX-BULK-1` — folder-spanning bulk import.
- `MFFD-PLUGIN-SVDX-WIRE-1` — wire the module into `plugins/pom.xml` once the
  Jandex hang clears.

**Honest scope statement.** The shipped scope unblocks "I want to find every
file from project Guid X" + "what acquisitions are in this file" + "what AMS
NetId is this from" — the queries the dataset can support today without
solving the binary. Sample-level ingest waits for either the binary RE
follow-up or the operator-driven CSV path.

### GAP-2 — Cross-track timeseries view

**The data:** 8,251 tapelaying tracks, each with ~190 channels. Today the chart lives on
one DataObject. Researchers want **"plot TCP temperature for every track of ply 5, sorted
by time."**

**The gap:** No multi-DO timeseries view exists. The single `TimeseriesAllChannelsChart`
component is single-DO scope. The `multi-channel bulk data` endpoint (TS-OPT2) supports
many channels of one DO but not many DOs.

**The fix:**
- Backend: extend `/v2/timeseries/bulk-data` to accept `dataObjectAppIds[]` and align by
  the channel-key `urn:shepard:afp:tcp-temperature-c`.
- Frontend: on Collection landing, add a "Saved View: TCP-temp by ply" tab that renders
  a small-multiples grid (one trace per ply, x = within-ply time).
- Template: a `VIEW_RECIPE` template `view-cross-ply-tcp-temp` referencing the channel
  predicate + the ply axis.

**Backlog rows:** `TS-CROSS-DO-VIEW-1` (backend), `TS-CROSS-DO-VIEW-2-FE` (frontend),
`TPL-VIEW-CROSS-PLY-TCP-1` (template).

### GAP-3 — NCR as first-class (Role 4 ask, already designed)

**The data:** `bridgewelding/AF_3` has `nextParameterNumber: 9` — the operator re-tried
the weld 9 times. Today this is just an integer. The MFFD demonstrator runs a real rework
loop in the bridgewelding step; EN 9100 wants `NCR_OPEN → DISPOSITION → CLOSED`.

**The gap:** `DataObject.status` enum is `DRAFT / IN_REVIEW / READY / PUBLISHED / ARCHIVED`.
No `NCR_OPEN`, `REJECTED`, no `repair-of` Predecessor edge type, no concession
attachment slot.

**The fix:** Land the **QM1** family from Role 4's audit:
- New status values: `NCR_OPEN`, `REJECTED`, `CONCESSION_PENDING`.
- New Predecessor edge property `mffd:transition-kind = rework | re-test | concession`.
- StructuredDataReference template `disposition-record` (8N1 part 4).

**Backlog rows:** `QM1a` (status enum + UI badges), `QM1b` (Predecessor edge metadata),
`QM1c` (`disposition-record` template).

### GAP-4 — Cross-process Predecessor edges

**The data:** tapelaying → bridgewelding → NDT thermography → cleats. The data preserves
the order in folder names; Shepard's Predecessor graph today carries only same-process
linkages from the `mffd-export` tool.

**The gap:** No automated way to draw "Track 244 was layed up to make ply 5 of part
which is welded into AF_3". The mapping lives in flo's domain knowledge.

**The fix:**
- Build a one-shot **`MFFD-AF-TRACK-MAPPING`** input (CSV or YAML) authored once by
  domain expert: each AF_N → list of (ply, track) tuples it joins.
- Cypher migration draws edges from that file.
- UI: the existing CollectionLineageGraph renders these — but at MFFD scale it breaks
  (task #25). See GAP-12.

**Backlog rows:** `MFFD-AF-TRACK-MAPPING-1` (input file authoring), `MFFD-AF-TRACK-MAPPING-2-CYPHER`.

### GAP-5 — 3D pointcloud viewer  ✅ shipped 2026-06-02

**The data:** Each `Track_NN__Run_NN_/files/` has `TPS 3D pointclouds.0`, `TPS 3D
pointclouds.1`, `FSD course 3D pointclouds`. That's **24,753 pointclouds** across the
dataset.

**The gap (original assessment):** No `PointcloudReference` payload kind. The
Trace3D viewer (#142) handles single (x,y,z,value) curves, not point sets.

**What shipped 2026-06-02:** The format research showed a cleaner fix than a
brand-new `PointcloudReference` payload kind: both `TPS 3D pointclouds.*` and
`FSD course 3D pointclouds` are 6-column ASCII (`X Y Z R G B`, CRLF, RGB always
the export-artefact triple `0 130 255`). They fit the *already-shipped*
`shepard-plugin-spatiotemporal` substrate (PostGIS `shepard_spatial.profile`
hypertable, SpatialDataContainer entity, REST endpoints) with no new payload
kind. The W7 plan in `aidocs/integrations/113 §W7` is the canonical reference.

The shipped solution:
- **Python promotion pass** (`plugins/spatial-importer/`) — parses the two ASCII
  formats, MERGEs `:SpatialDataContainer` rows per (DO, kind, SHA256), uploads
  points via existing payload endpoint, demotes original FileReferences to
  ARCHIVED. SHA256 idempotency makes re-runs zero-write.
- **`SpatialPointsCanvas.vue`** — Three.js `Points` (pointcloud) + `Line`
  (trajectory) renderer with voxel-grid downsample capped at 500k points.
  Pointcloud colour-maps by value (height by default); trajectory by time.
- **`DataObjectSpatialContainersPane.vue`** — lists every spatial container on
  the DataObject grouped by kind, with "Open in 3D viewer" button per row.
- **`SpatialDataContainer.frameAppId`** (Neo4j) + V106 NOOP migration — wires
  each container to a CST1 `:CoordinateFrame` so the viewer renders inside the
  W5 RoboDK scene (GAP-6 sibling).

The original "new payload kind" idea (`shepard-plugin-pointcloud`) was correctly
rejected because the spatiotemporal substrate already covers point storage; a
parallel `PointcloudReference` would have been EAV bloat (CLAUDE.md
"per-kind annotation entities are an anti-pattern").

**Not closed by this PR (file follow-ups):**
- `MFFD-SPATIAL-RAW-DATA-INVESTIGATE` — `TPS raw data.0…37` are 1292×964 grayscale
  PNG camera frames, NOT spatial point data. They stay as FileReferences. Open
  question: do they carry per-frame robot-pose metadata that would let them
  become a true `brush-trace` SpatialDataContainer?
- `MFFD-SPATIAL-IMPORTER-LIVE` — production deployment runbook; blocked on W2
  ingest draining into the dest Collection.
- `REF-EDIT-SPATIAL` — edit / delete dialogs for SpatialDataReference (the W7 PR
  ships list-only; create is via the Python importer).

**Commit:** `e654b87f9` (backend + importer) and `0a6b22291` (frontend viewer).
**Backlog rows:** `MFFD-SPATIAL-*` in `aidocs/16-dispatcher-backlog.md`.

### GAP-6 — RoboDK / URDF scene at Collection level

**The data:** `MFZ.rdk` (12 MB) is the physical robot cell every track was made in.

**The gap:** Today a scene-graph is attached to a SceneGraph entity; there's no
"Collection has a scene" affordance. The Collection landing page doesn't render the
robot cell as ambient context. Once the scene is on every detail page, the viewer
*becomes* the demonstrator.

**The fix:**
- Allow Collection to carry a scene-graph appId (`urn:shepard:collection:scene-graph`).
- Frontend: Collection landing shows the URDF viewer (full-bleed top section), with
  per-DO highlights synced ("this Track happened *here* in the cell").

**Backlog rows:** `COLL-SCENE-1` (entity link), `COLL-SCENE-2-UI` (Collection-landing renderer),
`MFFD-RDK-URDF-CONVERTER` (RDK→URDF tooling — plan W5 right-path).

### GAP-7 — Thermography NDT quality score + heatmap — ✅ closed 2026-06-02

**The data:** 6,273 TIFFs of thermal images across the layup; plus `thermography.7z` of
the post-layup inspection.

**The gap (original):** ImageBundleReference exists but has no derived `quality_score`
per frame, no heatmap overlay, no peak-delta-C extraction.

**What shipped (2026-06-02):**
- **Backend (`MFFD-NDT-QUALITY-1`):** `POST /v2/thermography/analyze` +
  `GET /v2/thermography/{appId}/plate-heatmap`. Stream-decodes each TIFF via the new
  TwelveMonkeys `imageio-tiff` 3.10.1 dep; per-frame `peakDeltaC = max(pixel) − median(pixel)`;
  bundle-wide composite plate heatmap (default 64×64 cells, configurable); DataObject-
  level `quality-score = 1 − clip(maxPeakDeltaC / threshold-c, 0, 1)` (default
  threshold 80 °C, deploy-tunable). Seven `urn:shepard:ndt:*` SemanticAnnotations
  written per analysis with `sourceMode=ai` + `confidence=1.0` (deterministic
  computation — local-default rule satisfied; no AI backend required). Lives in-tree
  at `backend/src/main/java/de/dlr/shepard/v2/thermography/` until the
  `OTVIS-WIRE-AGGREGATOR-1` Jandex blocker clears; relocation to the plugin tracked
  as `MFFD-THERMO-MOVE-TO-PLUGIN-1`. 26 JUnit tests green.
- **Frontend (`MFFD-NDT-HEATMAP-2-UI`):** `DataObjectThermographyPane.vue` mounts
  conditionally on the DataObject detail page when a thermography-shaped
  FileBundleReference is attached (name match: `thermo` / `ndt` / `.tif`). Canvas-
  backed inferno-colormap heatmap; Vuetify quality chip (green/amber/red bands);
  hover tooltip → exact cell temperature; Re-analyze CTA (Write-gated). Pure helpers
  in `~/utils/thermographyHeatmap.ts` (11 Vitest cases).
- **Plugin docs:** `plugins/fileformat-thermography/docs/{reference,quickstart,install}.md`
  updated with the new surface, the metric math, the annotation predicate list, and
  the deploy-time keys.

**Deferred follow-ups (filed in `aidocs/16`):**
- `MFFD-NDT-AI-CLASSIFY-1` — layer `AiCapability.VISION` over the deterministic
  pipeline for per-frame defect segmentation (void / porosity / delamination) once a
  VISION provider lands in `plugins/ai/`.
- `MFFD-NDT-CROSS-REGION-1` — Collection-landing cross-region heatmap comparison
  (P05 vs P06 …) reusing the cached plate-heatmap JSON.
- `MFFD-NDT-LIVE-1` — live streaming during layup (new
  `ThermographyStreamReference` kind).
- `MFFD-NDT-ADMIN-CONFIG-1` — promote the three deploy-time keys to a runtime-
  mutable `:ThermographyConfig` admin singleton.
- `MFFD-NDT-HEATMAP-MOUNT-TEST-1` — vue-test-utils mount-integration test (helper
  layer is already covered by the 11 Vitest cases).

**Honest scope:** Real-camera radiometric calibration assumed pre-calibrated TIFFs
(°C-valued floats). Falls back to greyscale-pixel-value interpretation for unitless
TIFFs — usable but the threshold knob loses physical meaning. The deferred
calibration row is `MFFD-NDT-CALIBRATION-1` (not yet filed; flag if the use case
arises).

**Backlog rows:** `MFFD-NDT-QUALITY-1` (✅ shipped), `MFFD-NDT-HEATMAP-2-UI` (✅ shipped),
follow-ups above.

### GAP-8 — Process-chain "as-built timeline" view ✅ closed 2026-06-02

**The data:** 2.6 years of production (2023-03-20 → 2025-11-12). Currently you'd
browse it ply-by-ply; researchers want a **timeline** showing the entire campaign:
"how many tracks per day, when did NCRs cluster, when did we re-test?"

**The gap:** No timeline view existed at Collection level. The closest was the
CollectionLineageGraph which scales linearly (task #25) and doesn't have time-on-x.

**Shipped 2026-06-02 (COLL-TIMELINE-1 + COLL-TIMELINE-1-FE):**
- Backend `GET /v2/collections/{appId}/timeline?binSizeDays=` returns a swimlane
  envelope (lanes derived from `urn:shepard:mffd:process-type` annotations).
- Per-bin counts plus NCR / REJECTED status overlays.
- Adaptive auto-coarsening so a 2.6-year campaign renders cleanly (1 → 7 → 30 → 90 → 365 ladder).
- Frontend Timeline `ExpansionPanelItem` on the Collection landing page —
  one ECharts stacked-bar swimlane per lane (OK / NCR / REJECTED colours),
  Day / Week / Month bin-size toggle, hover tooltip, click drill-down.
- 24 JUnit + 18 Vitest tests; sub-second perf on a 1000-row mock.

**Follow-ups (queued):**
- `COLL-TIMELINE-DRILLDOWN-FILTER-1` — make the DataObjects list panel
  honour the `?process-type=&date=` filter the Timeline link adds.
- `COLL-TIMELINE-CROSS-1` — multi-Collection campaign view.
- `COLL-TIMELINE-ANNOTATE-1` — annotate-on-a-bin affordance.
- `COLL-TIMELINE-LIVE-1` — SSE-driven live bin updates.

### GAP-9 — Video scale + scrubbing — ✅ closed (partial) 2026-06-02

**The data:** 139 MP4s totaling 133 GB across `Stringer_schweissungen/LRV_Videos`,
processed AFP video, etc. Plus the 313,538 PNG-frame TPS ImageBundle cardinality
(AAC1 finding: 38 per track × 8,251 tracks).

**The gap (original):** `VideoReference` shipped, but never scale-tested at 133 GB.
Browser streaming on long videos needs HLS segmentation + a per-second key-frame
index for scrubbing. ImageBundle existed but the choke point was loading every
file's OID into the UI at once.

**What shipped 2026-06-02 (`MFFD-VIDEOREF-SCALE-1` + `MFFD-IMAGEBUNDLE-PAGINATE-1`
+ `MFFD-IMAGEBUNDLE-SCRUBBER-1`):**

- **Backend Range requests.** `VideoStreamReferenceV2Rest.download` honours
  `Range: bytes=START-END`, returns 206 Partial Content with `Content-Range`
  + `Accept-Ranges: bytes` headers. 416 on unsatisfiable; falls back to full
  body when the underlying GridFS row has no size bookkeeping. Shared
  `HttpRangeUtil` (parse + slice stream) extracted from the FR1b path so
  every future content endpoint inherits the same shape. **This is the
  cheap fix that unlocks browser-native scrubbing on multi-GB MP4s.**
- **JWTFilter `?access_token=…` fallback.** Browser `<video src>`,
  `<img src>`, `<a download>` cannot inject custom headers. Per RFC 6750
  §2.3 the JWT travels as a query parameter when no `Authorization` header
  is present. Header takes precedence; empty query value falls back to 401.
- **Frontend VideoPlayer rewrite.** Drops the previous blob-download
  approach (fatal on 6+ GB MP4s — froze the tab) and hands the URL with
  `?access_token=…` directly to the `<video>` element. The browser does
  its own range requests + scrubbing natively.
- **Paginated bundle files endpoint.** New
  `GET /v2/bundles/{bundleAppId}/groups/{groupAppId}/files?page=&size=`
  returning `PagedFilesIO` envelope. Default page size 200, server cap
  1000. Server clamps out-of-range hints rather than rejecting (a stale
  client slider value should not produce a 400).
- **ImageBundleViewer scrubber.** Generic `ImageBundleViewer.vue`: large
  preview + range slider + virtualised thumbnail strip (Vuetify
  `v-virtual-scroll`) + lazy-loaded thumbnails via TH1a. Page-planning
  helpers in `utils/imageBundleScrubber.ts` mean the scrubber jumps cleanly
  between pages of the paginated endpoint; only one page is cached
  client-side at a time. Mounts cleanly onto the DataObject detail page
  via the follow-up below.
- **45 new unit tests** (16 `HttpRangeUtilTest` + 10 new video range tests
  + 3 new JWTFilter cases + 9 bundle pagination tests + 7 Vitest videoUrl
  cases + 13 Vitest scrubber cases). All green.

**Deferred follow-ups (filed in `aidocs/16`):**

- **`MFFD-VIDEOREF-HLS-1`** (L) — lazy ffmpeg segmenter + `.m3u8` manifest
  generator for very-long welding videos (12 h+) where the codec quirks on
  Safari's HLS-first stack may bite. Multi-day deliverable: ffmpeg sidecar,
  temp-file lifecycle, manifest regeneration races. Cut from this PR to
  ship the Range path now.
- **`MFFD-VIDEOREF-LIVE-1`** (XL) — live ingest during welding via sVC /
  MediaMTX sidecar; new `LiveVideoStreamReference` kind.
- **`MFFD-VIDEOREF-KEYFRAME-1`** (M) — AI-extracted keyframes as
  `urn:shepard:video:keyframe-at:` annotations for scrub-bookmark UX.
- **`MFFD-VIDEOREF-TRANSCODE-1`** (XL) — server-side HEVC/ProRes/DNxHR →
  H.264 pipeline preserving the original.
- **`MFFD-IMAGEBUNDLE-PANE-MOUNT-1`** (S) — mount the scrubber on the
  DataObject detail page with auto-detection of image-shaped bundles.
- **`MFFD-IMAGEBUNDLE-COMPARE-1`** (M) — side-by-side compare across
  DataObjects (Track 042 ply 5 vs Track 043 ply 5).

**Honest scope statement.** Real-fixture scale validation under Playwright
at the 4K viewport is the operator's post-deploy step — the worktree agent
does not perform `make redeploy-*` per the operator-not-redeploy rule.
The numbers we target (time-to-first-frame < 3 s on the largest extracted
MP4; 20-frame strip < 2 s; JS heap < 500 MB) are the acceptance criteria;
the deploy-time runbook will record measured values into
`aidocs/agent-findings/mffd-videoref-scale-playwright-2026-06-02.md` once
the W2 ingest provides the real fixtures (currently `/mnt/pve/unas/...`
not mounted from the agent worktree). The architecture passes the gate
that the previous blob-download path could not — range-on-MP4 is
browser-native and the previous failure mode (whole-blob download)
cannot recur.

**Backlog rows:** `MFFD-VIDEOREF-SCALE-1` (✅ shipped),
`MFFD-IMAGEBUNDLE-PAGINATE-1` (✅ shipped), `MFFD-IMAGEBUNDLE-SCRUBBER-1`
(✅ shipped), follow-ups above.

### GAP-10 — `.xit` / `.xit64` archive parser

**The data:** Files in `later/` carry Beckhoff `.xit` / `.xit64` archives (full
TwinCAT project + recorded variables). Same parser family as `.svdx` but archive shape.

**The gap:** No support. Marked "later" by flo, so this is **GAP-LATE** — track and
defer.

**Backlog row:** `MFFD-PLUGIN-XIT-1` (deferred to wave 5+).

### GAP-11 — Multi-source DataObject (data + image + video) — ✅ closed 2026-06-02

**The data:** Each ply is *one* event but has timeseries + thermal frames + multiple
video angles + a robot program + pointcloud. Today one DO can hold all those references
but the **UI separates them onto tabs**. A researcher wants the simultaneous view —
*timeseries scrubbing also moves the video and the thermal frame and the pointcloud
position*.

**The gap:** No synchronised multi-payload player. Each payload type lives in its own
component.

**The fix:** New `MultiPlayer.vue` linked via a shared time cursor. Out of scope for
near-horizon; track as `MFFD-MULTIPLAYER-1` (medium-effort).

**✅ Shipped 2026-06-02 (`MFFD-MULTIPLAYER-1`):**
`MultiPlayerPane.vue` mounts on the DataObject detail page between Spatial-data and
Provenance when ≥ 2 distinct payload kinds are present; toolbar (play/pause + rewind +
rate ∈ {0.5×, 1×, 2×, 4×} + scrubber + time readout) drives a shared
`useSyncedTimeCursor` composable. Two tiles genuinely sync today: TS (mirroring the
AAA3 `crossTime` idiom) and video (extending `VideoPlayer.vue` with a two-way
`currentTime` binding). Thermography and spatial tiles render informational placeholders
— the source components need follow-up work (frame-strip view / brush-head marker)
before they can read + write the cursor; those are filed as
`MFFD-MULTIPLAYER-THERMO-1` and `MFFD-MULTIPLAYER-SPATIAL-1`. Region annotation,
montage export, multi-DO sync, pointcloud animation, live mode, Playwright 4K
cross-tile playback test all queued under `MFFD-MULTIPLAYER-*` follow-ups. 29 Vitest
tests on the composable + tile-selection helper; lint + typecheck clean.

### GAP-12 — Lineage graph at MFFD scale (task #25) — ✅ closed 2026-06-02

**The data:** 8,251 tracks × tracks-per-ply × parent-relations = O(20k) edges on the
Collection lineage view.

**The gap (initial framing, partially wrong):** Reported as "the graph is
force-directed with no LOD; breaks past ~500 nodes." On inspection the actual
problem was different — the existing engine was already `@dagrejs/dagre` +
ECharts Canvas; the silent ceiling was a hard-coded `NODE_CAP = 150` that
sliced off 99% of an MFFD-scale Collection without telling the user.

**The fix shipped 2026-06-02 (LINEAGE-GRAPH-MFFD-SCALE):**
- `NODE_CAP` removed — full lineage rendered.
- Three-tier Level-Of-Detail keyed on the ECharts roam zoom event (macro
  ply / process bubbles below 0.3; meso nodes-only below 0.8; detail
  labels above).
- Filter pill row above the canvas (Status, Process-type, "Around DO N
  depth ≤ K").
- Minimap toggle (second `vue-echarts` instance, roam off, 2-px markers).
- Click-through to the DataObject detail page.
- Pure helpers extracted to `frontend/utils/lineageLayout.ts`; 32 Vitest
  cases incl. 20k-node perf smoke.
- Engine choice: ECharts Canvas kept (the substrate that hits the
  20k-node performance budget; DOM-based vue-flow / cytoscape SVG cap
  around 5k even with viewport culling).

**Backlog row:** `LINEAGE-GRAPH-MFFD-SCALE` (this PR) + LINEAGE-EDIT-1 /
LINEAGE-CROSS-1 / LINEAGE-TIMELINE-1 / LINEAGE-AI-GAP-1 (queued follow-ups).

## 2. Lower-priority gaps catalogued

| Gap | Backlog row | Note |
|-----|-------------|------|
| Origin Lab `.opj` file preview | `MFFD-PLUGIN-OPJ-1` | Single 200 MB file in dataset — defer |
| OriginLab Python `.npy` array | reuse existing `.npy` viewer | small, fine |
| `Gemini-Temporary Chat.md` in RoboDK dir | already markdown, renders | n/a |
| 7z archive auto-extract on upload | `FE-7Z-AUTO-EXTRACT-1` | small win for flo's workflow |
| Excel `.xlsx` of process parameters | TB1 TableContainer (#76 pending) | already designed |
| `MFZ.rdk` direct upload (no URDF conversion) | `MFFD-RDK-DIRECT-1` | fallback for GAP-6 |
| Track-name "Track_244__Run_30239_" parser | `MFFD-NAME-PARSER-1` | extract `track=244`, `run=30239` into semantic annotations on import |

## 3. What MFFD *doesn't* need that we have

Useful to call out — these features land in Shepard for other reasons and are correct
to keep, but the MFFD dataset doesn't push them:

- **HDF5** — MFFD never exports HDF5; cleaning-cycle plugin (#65) is for other domains.
- **SPARQL playground for end users** — domain users would never compose SPARQL. The
  endpoint stays for power users + agents but the UI ranks below all 12 gaps above.
- **Snapshot diff** — most MFFD DOs never mutate; the rework loop produces *new* DOs,
  not edits.

## 4. Top-5 highest-impact gaps (recommended dispatch order)

1. **GAP-6 — Collection-level scene-graph** (small, high immersion lift — 12 MB of RDK
   becomes the *demonstrator's centrepiece*).
2. **GAP-3 — NCR as first-class** (already partially designed; closes the EN 9100 story
   that opens MFFD funding conversations).
3. **GAP-4 — Cross-process Predecessor edges** (the mapping file is the bottleneck; the
   Cypher is trivial).
4. **GAP-2 — Cross-track timeseries view** (turns 8,251 datapoints from a tree into a
   trend).
5. **GAP-1 — `.svdx` parser plugin** (unlocks the 73 GB of currently-opaque scope data
   — blocks W8a entirely).

Dispatched as agent wave **ZZ** (next session). Estimated effort: GAP-6 ~1 day,
GAP-3 ~2 days, GAP-4 ~0.5 day + domain-expert sync, GAP-2 ~2 days, GAP-1 ~3 days
(plugin scaffolding + parser validation).

## 5. What surprised me

- **The .svdx pile (73 GB / 166 files) is bigger than every other type except MP4.**
  Without a parser the Beckhoff process-trace story is mostly invisible. This was not
  flagged in earlier MFFD discussions as a priority.
- **The exported tapelaying tar carries no StructuredDataReferences (`structured: 0`).**
  Yet bridgewelding's separate export is 100% StructuredDataReferences (zero TS, zero
  files). The two export shapes are profoundly different and need different importer
  paths.
- **`nextParameterNumber: 9` in StepMetaProcessStep is a numeric scar from a rework
  loop.** Treating it as a quality-trail signal (rather than a leftover field) gives MFFD
  the EN 9100 story for free.
- **The largest single ingestion gap is the 288 GB `Stringerverbindung` block** that has
  no exporter run on the DLR side. The data exists on N:\ but nobody has run the export.
  This is a workflow request to flo's collaborators, not a Shepard fix.
- **Domain-knowledge bottleneck:** the AF_N → ply/track mapping for GAP-4 is in flo's
  head; without it the demonstrator can't connect AFP layup to joining. This is the
  hidden blocker.
