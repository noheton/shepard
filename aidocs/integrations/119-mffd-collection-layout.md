---
name: MFFD Collection layout (B-pattern)
description: Authoritative Collection-level layout for the real MFFD upper-shell ingest — six process-step Collections + a Programme umbrella, with cross-step Predecessor edges by appId.
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

One umbrella **MFFD Programme** Collection + six process-step Collections.
The Programme carries `urn:shepard:programme = true`; each step Collection
carries `urn:shepard:partOf = <mffd-programme.appId>` — making the
Programme's Collection-detail page the **single operator entrypoint** that
renders the 6 step Collections as tiles (per the Programme UI shipped in
`aidocs/integrations/121-programme-and-subcollections.md`). Cross-step
Predecessor edges use `appId` and walk Collection boundaries via L2d. Each
step is independently citable; the umbrella is the publishable digital-thread
aggregate.

```
mffd-programme              ← umbrella, citable digital-thread aggregate (5 DOs)
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
  Programme aggregate — matches DFG / Clean Aviation JU citation expectations),
- preserves the **digital-thread** narrative via cross-Collection Predecessor
  edges (L2d's `appId` is global; the edges traverse Collections trivially).

The downsides are real but bounded:

- Cross-Collection queries cost one extra hop (mitigated by the existing
  `appId`-keyed lineage walker — same cost as in-Collection),
- Operators need to know *which* Collection to land on first
  (mitigated by the Programme umbrella's nav-link grid).

## 2. The six Collections

### 2.1 `mffd-programme` — umbrella

**Slug:** `mffd-programme`
**Display name:** *MFFD Upper Shell — Programme*
**Why it exists:** the citable, discoverable top-level. The digital-thread
publication assembles here.
**DataObjects (~5, hand-seeded):**

| DO | Type | Content |
|---|---|---|
| `programme-overview` | text + hero | README-style narrative, hero image of the upper shell, FAIR metadata (license, accessRights, funders = Clean Aviation JU / DLR programme line) |
| `ro-crate-manifest` | structured (JSON-LD) | nightly-generated RO-Crate aggregating the 6 per-step Collections by `appId` (`isPartOf`) |
| `process-chain-mapping` | structured (YAML) | the YAML driving MFFD-AF-TRACK-MAPPING (118) — Predecessor relations layer-by-layer |
| `cell-scene-graph-ref` | reference | points at `mffd-cell` for the URDF/RoboDK link |
| `vocabulary-manifest` | structured (JSON-LD) | the `urn:shepard:mffd:*` controlled-vocab subset this programme uses (SHACL-checkable) |

**Annotations on the Programme Collection:** `urn:shepard:programme = true` (per
121 — declares this Collection as a Programme; the UI surfaces a "Sub-collections"
panel and the global `/programmes` route lists it).

**Annotations on every child Collection:**

- `urn:shepard:partOf = <mffd-programme.appId>` — the navigational parent
  (per 121; renders a "child of MFFD Programme" chip on the child's Collection
  card and lets the Programme's Collection-detail page enumerate it).
- `urn:shepard:mffd:programme = <mffd-programme.appId>` — the **semantic**
  programme membership (lets SPARQL "list every DO in any MFFD step Collection"
  walk through the umbrella regardless of navigation shape). Kept distinct
  from `urn:shepard:partOf` because the SPARQL queries pre-date the Programme
  UI and we don't want to couple them.

**Owning group:** `mffd-programme-stewards` (read for all `mffd-*-readers`).

### 2.2 `mffd-afp-tapelaying` — the chokepoint

**Slug:** `mffd-afp-tapelaying`
**Display name:** *MFFD — AFP Tapelaying*
**Source:** `cube3-export/mffd-export/ts-export/tapelaying/`
**Importer:** v15 (shipped, four workers + exponential backoff + n10s PROV-O writeback)
**Expected DOs:** ~8 251 per-track DOs, each carrying timeseries + per-track files (TPS pointclouds + line-scan PNGs + intermediate evaluations + video).
**Primary template:** `MFFDTapelayingTrack` (TT, V100 — shipped 2026-05-30).
**Process-type predicate:** `urn:shepard:mffd:process-type = afp-tapelaying`
**Owning group:** `mffd-afp-team`

This is the W2 ingest target. ETA ~24 h once W2 launches.

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
`mffd-programme/process-chain-mapping`.

### 2.4 `mffd-spot-welding` — ultrasonic weld step

**Slug:** `mffd-spot-welding`
**Display name:** *MFFD — Ultrasonic Spot Welding*
**Source:** `Punktschweißungen/` (21 svdx files + paired CSVs)
**Importer:** `shepard-plugin-svdx` (tier-1 manifest + tier-2 binary, shipped 2026-06-02)
**Expected DOs:** ~21 per-svdx DOs, each with the full 149-channel time-series decoded
(`Scope Project_AutoSave_19_04_29.svdx` validated: 5 015 677 samples, 149/149 channels monotonic).
**Companion:** the `Punktschweißen Prozessdaten.xlsx` + Origin `.opj` analyses attach as FileReferences on the spot-welding Programme DO.
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

### 2.7 Future steps (W9 + downstream)

When the 288 GB `Stringer_schweissungen/` corpus transfers from the DLR source
share (per `IMPORT_README.md §"Source-vs-dump completeness"`):

- `mffd-stringer-placement` — Stringer placement step
- `mffd-stringer-verbindung` — Stringerverbindung (stringer joining)
- `mffd-cleats-lbr` — Cleats with LBR robot

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
- The Programme RO-Crate aggregates all six per-step Collections; queries
  "show me TR-2031 across all process steps" walk via `appId`.

## 4. Citation model

Per-Collection: **one DataCite DOI each** (6 step Collections + 1 Programme = 7 DOIs).

Each per-step RO-Crate ships its own provenance + license — citable on its own:

```
@dataset{mffd-tapelaying-2023,
  title   = {MFFD Upper Shell — AFP Tapelaying (2023-02 campaign)},
  doi     = {10.0000/mffd-afp-tapelaying.20230204},
  ...
}
```

Programme-level: a **meta-publication** RO-Crate aggregates the 7 child DOIs via
`isPartOf`. This is the Clean Aviation JU citation handle for the digital-thread
case study.

This means:

- A paper citing "the MFFD AFP tapelaying dataset" cites just `mffd-afp-tapelaying`.
- A paper citing "the MFFD digital-thread case study" cites `mffd-programme`.

Both work without merging Collections.

## 5. ACL / ownership

One UserGroup per Collection (`mffd-afp-team`, `mffd-welding-team`,
`mffd-ndt-team`, `mffd-cell-admins`, `mffd-programme-stewards`), with broad read
access for `mffd-programme-readers`.

The Programme's vocabulary-manifest + RO-Crate are world-readable; everything
else is opt-in per team.

## 6. Open decisions (locked after the operator pass 2026-06-02)

| # | Question | Decision |
|---|---|---|
| 1 | Adjacent OTvis (TRuTh, Antenna, DoorCorners, Referenzbauteil, Parameter) — own Collection or `mffd-ndt-thermography` with scope tag? | **same Collection, scope-tagged** (`urn:shepard:mffd:scope`) |
| 2 | Wiki plan documents (5) — fold into Programme description or attach as FileReferences? | **both** — fold into `programme-overview` text AND attach as FileReferences |
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

What changes is **policy + naming** (`urn:shepard:mffd:programme`, six slugs,
six teams) — no Java or TypeScript needed for the layout itself. The per-wave
importers do the actual writes.

## 8. Implementation sequence

1. **Now (pre-W2):** seed the Programme Collection (5 DOs) + create the 5 empty
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
   need the relevant per-step Collection to exist; the Programme + Collection
   bootstrap of step 1 covers that).

## 9. References

- `IMPORT_README.md` (on dump) — source corpus layout + status flags
- `aidocs/integrations/113-mffd-real-data-import-plan.md` — wave plan
- `aidocs/integrations/115-otvis-tier2-frame-extraction.md` — OTvis tier-2
- `aidocs/integrations/118-mffd-process-chain-mapping.md` — Predecessor YAML
- `aidocs/integrations/120-mffd-wiki-transformation.md` — wiki→journal+glossary
- `aidocs/integrations/121-programme-and-subcollections.md` — **the Programme entity + sub-Collection UI** (the operator's single entrypoint)
- `aidocs/agent-findings/mffd-data-inventory-2026-06-02.md` — inventory
- `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md` — feature gaps
