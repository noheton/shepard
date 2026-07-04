---
name: MFFD Collection layout (B-pattern)
description: Authoritative Collection-level layout for the real MFFD upper-shell ingest — six process-step Collections + a Project umbrella, with cross-step Predecessor edges by appId.
type: design
stage: feature-defined
last-stage-change: 2026-06-02
---

# 119 — MFFD Collection layout (B-pattern)

**Status:** decision-quality 2026-06-02 (operator chose option B).
**Companion:** `aidocs/integrations/113-mffd-real-data-import-plan.md` (wave plan),
`aidocs/integrations/118-mffd-process-chain-mapping.md` (Predecessor YAML),
`aidocs/integrations/115-otvis-tier2-frame-extraction.md` (OTvis tier-2).
**Source data:** `/mnt/pve/unas/dump/dataset/IMPORT_README.md` (on-disk corpus + status).

## 0. TL;DR

One umbrella **MFFD Project** Collection + six process-step Collections.
The Project carries `urn:shepard:project = true`; each step Collection
carries `urn:shepard:partOf = <mffd-project.appId>` — making the
Project's Collection-detail page the **single operator entrypoint** that
renders the 6 step Collections as tiles (per the Project UI (sub-collections panel + /projects route) shipped in
`aidocs/integrations/121-project-and-subcollections.md`). Cross-step
Predecessor edges use `appId` and walk Collection boundaries via L2d. Each
step is independently citable; the umbrella is the publishable digital-thread aggregate.

```
mffd-project              ← umbrella, citable digital-thread aggregate (5 DOs)
  │
  ├── (process flow, cross-Collection edges)
  │
mffd-afp-tapelaying         ← ~8 251 tracks from cube3 Coll 48297 (W2)
  ↓ Successor (per-AF mapping, MFFD-AF-TRACK-MAPPING)
mffd-bridge-welding         ← ~13 AF × N exec (W3)
  ↓ Successor
mffd-spot-welding           ← 21 svdx + paired CSVs (W8a)
  ↓ Successor (NDT inspect after weld)
mffd-ndt-thermography       ← 707 process + 37 reference OTvis (W6)
  ↕ coordinate-frame ref (urn:shepard:mffd:cell-frame-ref)
mffd-cell                   ← MFZ.rdk + KR210 URDF + KRL trajectories (W5)
```

## 1. Decision criteria — why six Collections, not one

The MFFD-Dropbox collection (8 514 DOs, single Collection) surfaced almost every
scale defect we have shipped fixes for since 2026-05-23: UI-011 (zero count),
UX-DOPANEL-STATUS-SERVER (client-side filter), UX-LIST-VIRTUALIZATION (1 470-row
panels), UX-DATAOBJECT-MAP-LAZY (eager fetch), DB-OPT5 (payload diet),
UI-020 (8 514-req lab-journal N+1), NEO-AUDIT-007 (duplicate-name disambiguation).

Splitting along the natural process-step seam:

- keeps each Collection in the **tested 100–2 000 DO range**,
- maps cleanly to **per-team ACL boundaries** (AFP, Welding, NDT, Cell ops),
- allows **parallel ingest waves** (W2 doesn't block W3 / W6 / W8a in code,
  only in chronological data dependency),
- supports **per-step DataCite DOIs** (one citation per step + one for the
  Project aggregate — matches DFG / Clean Aviation JU citation expectations),
- preserves the **digital-thread** narrative via cross-Collection Predecessor
  edges (L2d's `appId` is global; the edges traverse Collections trivially).

The downsides are real but bounded:

- Cross-Collection queries cost one extra hop (mitigated by the existing
  `appId`-keyed lineage walker — same cost as in-Collection),
- Operators need to know *which* Collection to land on first
  (mitigated by the Project umbrella's nav-link grid).

## 2. The six Collections

### 2.1 `mffd-project` — umbrella

**Slug:** `mffd-project`
**Display name:** *MFFD Upper Shell — Project*
**Why it exists:** the citable, discoverable top-level. The digital-thread
publication assembles here.
**DataObjects (~5, hand-seeded):**

| DO | Type | Content |
|---|---|---|
| `project-overview` | text + hero | README-style narrative, hero image of the upper shell, FAIR metadata (license, accessRights, funders = Clean Aviation JU / DLR programme line) |
| `ro-crate-manifest` | structured (JSON-LD) | nightly-generated RO-Crate aggregating the 6 per-step Collections by `appId` (`isPartOf`) |
| `process-chain-mapping` | structured (YAML) | the YAML driving MFFD-AF-TRACK-MAPPING (118) — Predecessor relations layer-by-layer |
| `cell-scene-graph-ref` | reference | points at `mffd-cell` for the URDF/RoboDK link |
| `vocabulary-manifest` | structured (JSON-LD) | the `urn:shepard:mffd:*` controlled-vocab subset this programme uses (SHACL-checkable) |

**Annotations on the Project Collection:**

- `urn:shepard:project = true` (per 121 — declares this Collection as a
  Project; the UI surfaces a "Sub-collections" panel and the global
  `/projects` route lists it).
- `urn:shepard:programme = "Clean Aviation JU"` (per 121 §2 — free-text
  funding-line metadata; additional values can be added for DLR-internal
  programme lines as the operator decides at seed time).

**Annotations on every child Collection:**

- `urn:shepard:partOf = <mffd-project.appId>` — the navigational parent
  (per 121; renders a "child of MFFD Project" chip on the child's Collection
  card and lets the Project's Collection-detail page enumerate it).
- `urn:shepard:mffd:programme = <mffd-project.appId>` — the **semantic**
  programme membership (lets SPARQL "list every DO in any MFFD step Collection"
  walk through the umbrella regardless of navigation shape). Kept distinct
  from `urn:shepard:partOf` because the SPARQL queries pre-date the Project
  UI and we don't want to couple them.

**Owning group:** `mffd-project-stewards` (read for all `mffd-*-readers`).

### 2.2 `mffd-afp-tapelaying` — the chokepoint

**Slug:** `mffd-afp-tapelaying`
**Display name:** *MFFD — AFP Tapelaying*
**Source:** `cube3-export/mffd-export/ts-export/tapelaying/`
**Importer:** v15 (shipped, four workers + exponential backoff + n10s PROV-O writeback)
**Expected DOs:** ~8 251 leaf Execution (Run) DOs, plus the intermediate Step / Layer / Ply-Group / Track grouping DOs that v16 PRESERVE-HIERARCHY creates above them — see §2.2.1 below for the full five-level shape.
**Primary templates:** `MFFDStepRoot`, `MFFDLayer`, `MFFDPlyGroup`, `MFFDTrack`, `MFFDExecution` (all from TT, V100 — shipped 2026-05-30). Each carries a per-template `iconKey` (MDI name) per `aidocs/integrations/122 §4` so the tree, breadcrumbs, sidebar, and DO list all render with the right visual at every depth — `mdi-factory` / `mdi-layers` / `mdi-format-list-group` / `mdi-vector-polyline` / `mdi-play-circle-outline` respectively. Bridge-weld, spot-weld, NDT, and cell templates carry their own icons too.
**Process-type predicate:** `urn:shepard:mffd:process-type = afp-tapelaying`
**Owning group:** `mffd-afp-team`

This is the W2 ingest target. ETA ~24 h once W2 launches.

#### 2.2.1 Internal hierarchy (five levels of DataObjects)

`v16 PRESERVE-HIERARCHY` rebuilds the cube3 layup tree as nested DataObjects.
The references (TimeseriesContainer, FileReferences, SpatialDataContainers,
LabJournalEntries) attach to the **leaf Execution (Run) DO** — *not* to the
Track DO. Track is an identity/grouping node, Run is the data carrier.

```
mffd-afp-tapelaying  Collection
│
└── DO  process-step-afp-tapelaying          ← Level 1 — Step root (1 DO)
    │   template: MFFDStepRoot
    │   annotations: mffd:level=step, mffd:process-type=afp-tapelaying
    │
    ├── DO  Layer_L8                          ← Level 2 — Layer (7 DOs total
    │   │   template: MFFDLayer                  per the diproj layer set:
    │   │   annotations: mffd:level=layer,        L8, L9, L11, L15, L18, L19, L19+)
    │   │              mffd:layer=8
    │   │
    │   ├── DO  PG_001                         ← Level 3 — Ply Group (N per Layer)
    │   │   │   template: MFFDPlyGroup
    │   │   │   annotations: mffd:level=ply-group,
    │   │   │              mffd:ply-group=PG_001, mffd:layer=8
    │   │   │
    │   │   ├── DO  Track_4231                 ← Level 4 — Track (~10–20 per PG)
    │   │   │   │   template: MFFDTrack
    │   │   │   │   annotations:
    │   │   │   │     mffd:level=track
    │   │   │   │     mffd:track=4231
    │   │   │   │     mffd:layer=8
    │   │   │   │     mffd:ply-group=PG_001
    │   │   │   │     mffd:cell-frame-ref=<mffd-cell.scenegraph.appId>
    │   │   │   │   sibling Predecessor/Successor edges within the PG:
    │   │   │   │     ← Track_4230   → Track_4232
    │   │   │   │   anomaly rollup (per §2.2.2): mffd:run-count, mffd:has-rerun
    │   │   │   │
    │   │   │   └── DO  Run_24261              ← Level 5 — Execution (1..N per Track)
    │   │   │       │   template: MFFDExecution
    │   │   │       │   annotations:
    │   │   │       │     mffd:level=execution
    │   │   │       │     mffd:run=24261
    │   │   │       │     mffd:track=4231 / layer=8 / ply-group=PG_001
    │   │   │       │     source:provenance = cube3:collection-48297:do-NNNN
    │   │   │       │     createdAt, createdBy (:MirroredUser)
    │   │   │       │
    │   │   │       │   ── References attach here ──────────────
    │   │   │       ├── TimeseriesContainer  "process channels"
    │   │   │       ├── FileReference[]  TPS raw data, TPS/FSD ply, video, …
    │   │   │       ├── SpatialDataContainer  kind=pointcloud  (W7)
    │   │   │       ├── SpatialDataContainer  kind=brush-trace (W7b)
    │   │   │       ├── LabJournalEntry[]  per-day Legeplan / Legetagebuch entries
    │   │   │       └── Predecessor edges (cross-Collection, via appId)
    │   │   │             → mffd-bridge-welding/AF_07_Exec_12.appId
    │   │   │             ← Run_24260   (when same Track had to be re-run)
    │   │   │
    │   │   ├── DO  Track_4232  Track_4233  …  (sibling Tracks in same PG)
    │   │
    │   ├── DO  PG_002  …
    │
    ├── DO  Layer_L9  Layer_L11  Layer_L15  Layer_L18  Layer_L19  Layer_L19plus
```

The references live on **Run**, the grouping carries through the chain above.
Total DO count is ~8 251 Runs + ~5 000 Tracks + ~800 PGs + 7 Layers + 1 Step
= **~14 000 DOs** in `mffd-afp-tapelaying` (subject to the actual layer
breakdown — the diproj gives ~8 251 leaves, the upper levels are derived by
the importer).

#### 2.2.2 Multi-run rule — same Track with multiple Run numbers signals a layup anomaly

Operator rule 2026-06-02: **when a Track DO has more than one Run child, the
re-runs are usually anomalies during layup** (splice cut, tape misalignment,
silicone-roll defect, fibre fracture, …). The first Run failed or was aborted;
later Runs are operator-driven corrections.

The importer emits, on every Track DO that has ≥2 Runs:

```
urn:shepard:mffd:run-count        = <integer N>
urn:shepard:mffd:has-rerun        = true           (only on Tracks with N >= 2)
urn:shepard:mffd:run-order        = [ Run_24260.appId, Run_24261.appId, … ]
                                    (oldest-first)
```

And on every Run DO that is **not** the first under its Track:

```
urn:shepard:status:nature         = re-run
urn:shepard:mffd:supersedes       = <previous-run.appId>
urn:shepard:mffd:rerun-index      = <1, 2, …>      (1-based; the original is 0)
```

The Predecessor edge `← Run_24260` already chains them in time order; the
annotations make the anomaly machine-queryable without walking the edge graph
(useful for the UI badge + the AI1c quality-score input).

**UI consequence (PROJ-PANEL-1 + the dataobject-list filter):**

- Track DO row shows a small `⟲ re-run` badge when `has-rerun = true`,
  with the count.
- The Collection's DO list grows a filter chip `status: re-run only`
  (toggles `urn:shepard:status:nature = re-run`) so the operator can list
  every anomalous Run in one click.
- Trace3D + the Quality-Score widget (AI1c) treat `has-rerun = true` as a
  positive label for "layup process anomaly" — labelled training data for
  free.

**NCR (AAA2 / GAP-3) coupling:**

A `has-rerun = true` Track is a *candidate* for an NCR row (per AAA2's
non-conformance status + disposition machinery), but NOT auto-promoted —
the operator confirms whether the re-run resolved the issue cleanly (final
Run passes NDT) or whether it left an NCR open. The wiki Legeplan +
Legetagebuch entries (§120 track 1) often carry the operator note for why
the re-run happened; the wiki-to-journal pass attaches that note to the
Track DO, giving the NCR reviewer the context for free.

**Tracked as:** `MFFD-RERUN-ANOMALY-DETECT` in `aidocs/16`.

#### 2.2.3 Per-Layer overview — a use case of the generic Project API

Operator note 2026-06-02: *"people might want to look at the data of a single
Layer especially with temperature and TPS data."*

This is a real analytical lens, but it is **not** a feature on top of MFFD —
it is one application of a generic capability that belongs to every Project.
There is no MFFD-specific dashboard, no MFFD-specific nav card, and no
MFFD-namespaced endpoint. The Layer-by-Layer roll-up is implemented entirely
in terms of the generalised **Project REST surface** specified in
`aidocs/integrations/121 §3` (`/v2/projects/{appId}/...`).

**How a Layer view actually resolves:**

1. The client requests
   `GET /v2/projects/{mffd-project.appId}/by-annotation/urn:shepard:mffd:layer/18`.
   The Project endpoint walks `urn:shepard:partOf` to find sibling
   Collections, then returns every DataObject across them whose annotation
   `urn:shepard:mffd:layer = 18` is set (directly or inherited via parent walk).
2. The Layer DO inside `mffd-afp-tapelaying` carries a `VIEW_RECIPE` of kind
   `MFFDLayerOverview` (a SHACL recipe shipped with the MFFD process-type
   templates, TT). The recipe declares which panels (cross-track TS, 3D
   overlay, NDT gallery, anomaly chip strip) consume which subset of the
   roll-up. It is just a recipe — it has no UI of its own; the generic
   shapes-render path (`POST /v2/shapes/render` with `kind=VIEW_RECIPE`)
   composes the panels from already-shipped components: AAA3, AAC1, AAC2,
   #142.
3. Other Projects (PLUTO, LUMEN, BT-KVS) get the same affordance the moment
   they ship their own VIEW_RECIPE pointing the generic by-annotation route
   at their own join key (mission-phase, test-bench, weld-pass, …).
   No new code per domain.

**What's already shipped that this composes:**

| Building block | Shipped as | Role |
|---|---|---|
| Cross-track TS chart | **AAA3 / GAP-2** | temperature panel |
| Spatial container + viewer | **AAC1 / GAP-5** | 3D overlay panel |
| NDT thermography heatmap | **AAC2 / GAP-7** | NDT frame gallery |
| Trace3D color-mapped path | **#142** | path overlay on temperature |
| VIEW_RECIPE template kind + render route | **TPL2, V100** + `POST /v2/shapes/render` | composes panels from recipe |
| Re-run badge + filter chip | **MFFD-RERUN-ANOMALY-DETECT** (§2.2.2) | anomalies row |
| Cross-Collection lineage walker | L2d `appId` natively | joins frames + welds |

**What's new** (only two things, both generic):

1. The Project REST surface (`/v2/projects/{appId}`) gains a
   `GET /v2/projects/{appId}/by-annotation/{predicate}/{value}` sub-resource —
   spec lives in `aidocs/integrations/121 §3`, tracked as `PROJ-REST-2`.
   Generic; Layer is just one caller.
2. The `MFFDLayerOverview` VIEW_RECIPE template — an MFFD content artefact
   shipped under the existing TT MFFD process-type templates. Not new
   infrastructure; just a recipe document. **Shipped 2026-06-03 as
   `V109__Mffd_layer_overview_view_recipe.cypher`** — a `:ShepardTemplate`
   row of `templateKind = 'VIEW_RECIPE'`, version 1, source
   `'V109-builtin'`, iconKey `mdi-view-dashboard-variant`, body
   declaring the 5 panels (`run-summary`, `temperature`,
   `tps-line-scan`, `spatial-overlay`, `ndt-frames`) each carrying its
   building-block reference (AAA3 / AAC1 / AAC2 / #142) and the
   generic Project by-annotation source endpoint shape.

**Tracked as:** `PROJ-REST-2` (generic by-annotation route, shipped
2026-06-02 as `1956054f4`) + `MFFD-LAYER-RECIPE` (the VIEW_RECIPE
itself, shipped 2026-06-03 as V109) in `aidocs/16`. The previous
`MFFD-LAYER-OVERVIEW-VIEW` row (which introduced an MFFD-namespaced
endpoint and an MFFD-specific nav card) is superseded and removed.

### 2.3 `mffd-bridge-welding` — the second pass

**Slug:** `mffd-bridge-welding`
**Display name:** *MFFD — Bridge Welding*
**Source:** either `cube3-export/mffd-export/ts-export-bridgewelding/` (full export) OR `4-Brückenschweißen/bridgewelding/` + its `manifest.json` (lighter, already-shaped Shepard-export with file/TS/structured ref ids)
**Importer:** v16 PRESERVE-HIERARCHY (shipped) OR manifest-replay (lighter)
**Expected DOs:** 13 AF parts × N executions per AF, hierarchically arranged.
**Primary template:** `MFFDBridgeWeldExecution` (TT, V100)
**Process-type predicate:** `urn:shepard:mffd:process-type = bridge-welding`
**Owning group:** `mffd-welding-team`

Predecessor edges from `mffd-afp-tapelaying` materialise per the YAML in
`mffd-project/process-chain-mapping`.

### 2.4 `mffd-spot-welding` — ultrasonic weld step

**Slug:** `mffd-spot-welding`
**Display name:** *MFFD — Ultrasonic Spot Welding*
**Source:** `Punktschweißungen/` (21 svdx files + paired CSVs)
**Importer:** `shepard-plugin-svdx` (tier-1 manifest + tier-2 binary, shipped 2026-06-02)
**Expected DOs:** ~21 per-svdx DOs, each with the full 149-channel time-series decoded
(`Scope Project_AutoSave_19_04_29.svdx` validated: 5 015 677 samples, 149/149 channels monotonic).
**Companion:** the `Punktschweißen Prozessdaten.xlsx` + Origin `.opj` analyses attach as FileReferences on the spot-welding Project DO.
**Primary template:** `MFFDSpotWeld`
**Process-type predicate:** `urn:shepard:mffd:process-type = spot-welding`
**Owning group:** `mffd-welding-team`

### 2.5 `mffd-ndt-thermography` — NDT step

**Slug:** `mffd-ndt-thermography`
**Display name:** *MFFD — NDT Thermography*
**Source:** `thermography-extracted/` (704 OTvis under `process/L*/` + 37 under `references/`)
**Importer:** `shepard-plugin-fileformat-thermography` (OTVIS-PARSE-1 shipped 2026-05-28; OTVIS-PARSE-2 frame decoder gated on the wire-aggregator-1 backend hookup)
**Expected DOs:** ~744 per-OTvis DOs (707 process + 37 reference + 3 typo-normalised), grouped by S/M/L cell via `urn:shepard:mffd:capture-pair-id`.
**Primary template:** `MFFDNDTScan` (with S/M/L/F + PRE/POST fields)
**Process-type predicate:** `urn:shepard:mffd:process-type = ndt-thermography`
**Scope sub-annotation:** `urn:shepard:mffd:scope ∈ {process, parameter, truth, referenzbauteil, antenna, doorcorners}` (one Collection, scope-tagged — operator decision 2026-06-02).
**Owning group:** `mffd-ndt-team`

#### 2.5.1 Gap-audit annotations (applied at ingest time)

Per the `MFFD.diproj` cross-reference (planning manifest):

| Cell | Status | Annotation |
|---|---|---|
| `S7/M4/L19+` | planned 4 frames, captured 3 | `urn:shepard:status:capture-incomplete = true` |
| `S9/M6/L19`  | planned 4 frames, captured 3 | `urn:shepard:status:capture-incomplete = true` |
| `S7/M7/L19+` | unplanned, 1 frame Feb 6 20:54 | `urn:shepard:status:capture-substitution = true`, `urn:shepard:status:substitution-for = S7/M4/L19+/F4` |
| `S9/M6/L18`  | unplanned, 1 frame Feb 6 22:04 | `urn:shepard:status:capture-substitution = true`, `urn:shepard:status:substitution-for = S9/M6/L19/F4` |
| `S13_M10L9_F2`, `S13_M11_L11_f2`, `S5_M5L19+_F4` | filename typos | `urn:shepard:filename:typo-corrected = true`, `urn:shepard:filename:original = <orig>` |

#### 2.5.2 PRE/POST 2-day pattern (every cell)

The OTvis `<CreationDate>` clustering shows a **PRE pair on 2023-02-04** (F1+F2
within ~1 minute) and a **POST pair on 2023-02-06** (F3+F4). Encoded as:

```
urn:shepard:mffd:measurement-phase ∈ { pre, post }     (from CreationDate clustering)
urn:shepard:mffd:campaign-day      ∈ { 2023-02-04, 2023-02-06 }
urn:shepard:mffd:capture-pair-id    = <S/M/L cell id>  (groups PRE+POST in the UI)
```

The pre/post split is the canonical thermal-cure-cycle measurement design — the
SHACL template gets a single `MeasurementPair` shape that requires one PRE and
one POST per cell (4 frames total when both pairs are complete).

#### 2.5.3 L19+ designation is OFFICIAL

`L19+` is a distinct planned layer in `MFFD.diproj` (not a typo or rescan
notation). The OTVIS-PARSE-1 filename regex needs `L<n>\+?_F<n>` — a one-line
change tracked as `OTVIS-PLUS-VARIANT-REGEX` in `aidocs/16`.

### 2.6 `mffd-cell` — infrastructure / scene-graph layer

**Slug:** `mffd-cell`
**Display name:** *MFFD — Cell + Scene*
**Source:** `RoboDK Cell Geometry/MFZ.rdk` + `examples/mffd-rdk-urdf-showcase/` (KR210 R2700/2 URDFs + trajectory CSVs)
**Importer:** `shepard-plugin-fileformat-robotics` (RDK-PARSE-1 shipped 2026-05-28) + `ScenegraphFromUrdfRest`
**Expected DOs:** ~10 (RDK file, URDFs, meshes, trajectory CSVs, scene-graph definition).
**Primary template:** `MFFDCell`
**No process-type predicate** — this is infrastructure, not a process step.
**Coordinate-frame relation:** per-step DOs reference this Collection via `urn:shepard:mffd:cell-frame-ref = <mffd-cell.scenegraph.appId>`. This is the coordinate-frame substrate the W4 process-chain mapping uses for spatial alignment.
**Owning group:** `mffd-cell-admins`

### 2.7 `mffd-stringer-welding` and downstream steps

#### 2.7.1 `mffd-stringer-welding` — stringer resistance welding

**Slug:** `mffd-stringer-welding`
**Display name:** *MFFD — Stringer Welding*
**Source:** `Stringer_schweissungen/` (~288 GB extracted; zip at `/mnt/pve/unas/dump/dataset/Stringer_schweissungen.zip` — extraction gated on `MFFD-STRINGER-EXTRACT-1`)
**Importer:** `shepard-plugin-fileformat-thermography` with `StringerWeldingDirParser` (shipped 2026-06-09, `MFFD-STRINGER-FILENAME-PARSER-1`)
**Process-type predicate:** `urn:shepard:mffd:process-type = stringer-welding`
**Owning group:** `mffd-welding-team`

Process chain position:

```
mffd-ndt-thermography
    ↓ Successor
mffd-stringer-welding        ← this Collection
    ↓ Successor
mffd-stringer-verbindung     (W9 — pending source-side export)
    ↓ Successor
mffd-cleats-lbr              (downstream — pending)
```

**Directory-name grammar** (`StringerWeldingDirParser`, shipped 2026-06-09):

| Token | Meaning | Annotation predicate |
|---|---|---|
| `P<pos>` | Stringer position number (integer) | `urn:shepard:mffd:stringer-position = <pos>` |
| `Strich` | "Strich" variant flag (optional) | `urn:shepard:mffd:stringer-strich = true` |
| `_S` | Secondary variant flag (optional) | `urn:shepard:mffd:stringer-variant = S` |
| `{1\|2}teBahn` | Path ordinal — 1st or 2nd welding pass | `urn:shepard:mffd:stringer-path-ordinal = <1\|2>` |
| `_Fehler` | Non-conformance candidate (optional) | `urn:shepard:mffd:stringer-fehler = true` |

`_Fehler` directories are NCR candidates — the importer emits
`urn:shepard:mffd:stringer-fehler = true` at ingest time; the operator
confirms via AAA2 disposition whether to open a formal NCR.

**Ingest gate:** `MFFD-STRINGER-EXTRACT-1` (extraction of
`Stringer_schweissungen.zip` from dump drive to NFS staging) must complete
before this Collection can be populated.

#### 2.7.2 Downstream steps (pending source-side export)

- `mffd-stringer-verbindung` — Stringerverbindung (stringer joining); source-side
  export run required (W9 in `aidocs/integrations/113`).
- `mffd-cleats-lbr` — LBR robot cleats step; downstream of Stringerverbindung.

Same shape as the existing six: per-step Collection, `urn:shepard:mffd:process-type`
annotation, owning group, Predecessor edges back to upstream steps.

## 3. The digital thread — Predecessor edges across Collections

A single AF track walks four Collections via `appId`:

```
mffd-afp-tapelaying:Track_4231        ──Successor──▶  mffd-bridge-welding:AF_07_Exec_12
                                                            │
                                                            ├──Successor──▶  mffd-spot-welding:WP16_Run42
                                                            │                       │
                                                            └──Successor──▶  mffd-ndt-thermography:S07_M03_L19_F1
                                                                                   │
                                                                                   ├── (if FAIL)  Disposition──▶  :NCR row
                                                                                   └── (if PASS)  no further edge
```

Implementation:

- The edges land via the **MFFD-AF-TRACK-MAPPING** infra (shipped 2026-06-02 —
  YAML loader + admin REST endpoint `POST /v2/admin/mffd/process-chain-mapping`).
- The YAML lives in the umbrella's `process-chain-mapping` DO (§2.1).
- `appId` is globally unique post-L2d, so the edges have zero special-case logic.
- The Project's aggregate RO-Crate aggregates all six per-step Collections; queries
  "show me TR-2031 across all process steps" walk via `appId`.

## 4. Citation model

Per-Collection: **one DataCite DOI each** (6 step Collections + 1 Project umbrella = 7 DOIs).

Each per-step RO-Crate ships its own provenance + license — citable on its own:

```
@dataset{mffd-tapelaying-2023,
  title   = {MFFD Upper Shell — AFP Tapelaying (2023-02 campaign)},
  doi     = {10.0000/mffd-afp-tapelaying.20230204},
  ...
}
```

Project-level: a **meta-publication** RO-Crate at the Project aggregates the 7 child DOIs via
`isPartOf`. This is the Clean Aviation JU citation handle for the digital-thread
case study.

This means:

- A paper citing "the MFFD AFP tapelaying dataset" cites just `mffd-afp-tapelaying`.
- A paper citing "the MFFD digital-thread case study" cites `mffd-project`.

Both work without merging Collections.

## 5. ACL / ownership

One UserGroup per Collection (`mffd-afp-team`, `mffd-welding-team`,
`mffd-ndt-team`, `mffd-cell-admins`, `mffd-project-stewards`), with broad read
access for `mffd-project-readers`.

The Project's vocabulary-manifest + RO-Crate are world-readable; everything
else is opt-in per team.

## 6. Open decisions (locked after the operator pass 2026-06-02)

| # | Question | Decision |
|---|---|---|
| 1 | Adjacent OTvis (TRuTh, Antenna, DoorCorners, Referenzbauteil, Parameter) — own Collection or `mffd-ndt-thermography` with scope tag? | **same Collection, scope-tagged** (`urn:shepard:mffd:scope`) |
| 2 | Wiki plan documents (5) — fold into Project description or attach as FileReferences? | **both** — fold into `project-overview` text AND attach as FileReferences |
| 3 | ~99 reference wiki pages — skip, or mine for glossary? | **mine for glossary** (`MFFD-WIKI-TO-GLOSSARY`) |
| 4 | Wiki author preservation — `:MirroredUser` or generic agent? | **`:MirroredUser`** per PROV-O `wasAttributedTo` |
| 5 | The 2 under-captures + 2 substitutions in OTvis | **annotate, do not block ingest** (§2.5.1) |

## 7. Reuse survey (per CLAUDE.md §reuse-before-reimplement)

This layout uses **zero new infrastructure** beyond what is already shipped:

- Collection-creation endpoint: ✅ `POST /v2/collections` (shipped, L2d)
- Predecessor-edge across Collections: ✅ `POST /v2/data-objects/{src}/predecessor` accepts any `appId` (shipped, L2d)
- DataCite DOI: ✅ `shepard-plugin-unhide-publish` (shipped) + each Collection's published mode
- RO-Crate export: ✅ `TPL14 — Regulatory Evidence Pack` (shipped)
- SHACL templates: ✅ TT-MFFD-PROCESS-TYPE-TEMPLATES shipped 2026-05-30
- ACL by UserGroup: ✅ shipped
- Scene-graph attached at Collection level: ✅ AAA1 / GAP-6 shipped 2026-06-02
- Cross-Collection lineage walker: ✅ L2d `appId` makes this implicit

What changes is **policy + naming** (`urn:shepard:mffd:project`, six slugs,
six teams) — no Java or TypeScript needed for the layout itself. The per-wave
importers do the actual writes.

## 8. Implementation sequence

1. **Now (pre-W2):** seed the Project Collection (5 DOs) + create the 5 empty
   step Collections + create the UserGroups. ~30 min of Cypher / admin REST,
   bookmarked at `examples/mffd-showcase/scripts/seed-mffd-collections.py` (to
   be written; the script is idempotent and uses the same `shepard_client` SDK
   the v15 importer uses).
2. **W2 (~24 h):** v15 importer populates `mffd-afp-tapelaying` from
   `cube3-export/.../ts-export/tapelaying/`.
3. **W2.5 (~30 min, post-W2):** `wiki-to-journal.py` (per 120) attaches
   ~218 lab-journal entries from the wiki to the relevant per-track DOs.
4. **W3 (~10 min):** v16 importer populates `mffd-bridge-welding` from
   `cube3-export/.../ts-export-bridgewelding/` (or
   `4-Brückenschweißen/bridgewelding/`).
5. **W4 (~1 min):** YAML loader materialises Predecessor edges across `mffd-afp-*`
   and `mffd-bridge-*`.
6. **W5, W6, W8a, W8b, W8c, W7, W7b** run in any order after W2 (they only
   need the relevant per-step Collection to exist; the Project + sub-Collection
   bootstrap of step 1 covers that).
7. **W-stringer (post `MFFD-STRINGER-EXTRACT-1`):** create `mffd-stringer-welding`
   Collection + run `StringerWeldingDirParser`-wired importer (per §2.7.1).

## 9. References

- `IMPORT_README.md` (on dump) — source corpus layout + status flags
- `aidocs/integrations/113-mffd-real-data-import-plan.md` — wave plan
- `aidocs/integrations/115-otvis-tier2-frame-extraction.md` — OTvis tier-2
- `aidocs/integrations/118-mffd-process-chain-mapping.md` — Predecessor YAML
- `aidocs/integrations/120-mffd-wiki-transformation.md` — wiki→journal+glossary
- `aidocs/integrations/121-project-and-subcollections.md` — **the Project entity + sub-Collection UI** (the operator's single entrypoint; programmes are project-metadata strings)
- `aidocs/agent-findings/mffd-data-inventory-2026-06-02.md` — inventory
- `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md` — feature gaps
