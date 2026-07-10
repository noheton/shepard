---
stage: feature-defined
last-stage-change: 2026-07-10
---

# The "missing TPS data" + svdx (welding) plan

**Trigger (operator, 2026-07-10):** *"the TPS data is missing"* on the MFFD
tapelaying data; plus *".svdx files should be promoted"*. Initial investigation
mis-attributed `.svdx` to tapelaying/TPS. **Operator correction (2026-07-10):**
*"the svdx are used for welding. TPS has these tiffs, and other files."* The live
graph confirms the correction — see §1. These are **two independent concerns**:

- **`TPS-TAPELAYING-DATA-MISSING`** — the real "missing data": the tapelaying
  track files (TIFFs + other per-course files) were **never ingested**. An
  **ingest gap**, not a parse gap. (§2)
- **`SVDX-*`** — svdx is **welding** process data, already imported; the work is
  fileKind promotion + parse-backfill + viewer, on the *welding* collections. (§3)

## §1 Ground truth (verified 2026-07-10, live Neo4j)

| Process | Collection | DataObjects | Data files imported |
|---|---|---|---|
| **AFP tapelaying (TPS)** | `mffd-afp-tapelaying` | 8483 (`PlyGroup→Ply→Track (Run N)`, **8251 Track leaves**) | **none** — only `s3-test-marker.txt` on one PlyGroup; tracks carry only structural `BasicReference` |
| **Stringer welding** | `mffd-stringer-welding` | 144 | 930 **svdx** + 178 mp4 + 6 tiff + 65 txt |
| **Spot welding** | `mffd-spot-welding` | 21 | 21 **svdx** |
| **Bridge welding** | `mffd-bridge-welding` | 3108 | 7886 `Processmonitoring-Reference.{0-3}` |
| **NDT thermography** | `mffd-ndt-thermography` | 1845 | 1855 `.OTvis` |

**Conclusions:**
- **svdx = welding** (951 total: stringer 930 + spot 21). The operator's
  correction is correct. `.svdx` never lived on tapelaying.
- The **tapelaying tree is an empty skeleton** — the "missing TPS data" is the
  per-track TIFFs + other files that were never uploaded. The 6 TIFFs that exist
  are in *stringer-welding*, unrelated to TPS.
- Each `Track (Run NNNNN)` leaf carries `attributes||run_number` +
  `attributes||track_number` — the **join key** an ingest uses to attach the
  right source files to the right track.

## §2 `TPS-TAPELAYING-DATA-MISSING` — the ingest gap (the real fix)

**Source located + structure decoded (2026-07-10, live on UNAS).** The full
tapelaying corpus is the extraction of `mffd.tar.gz` at
`/mnt/pve/unas/dump/dataset/cube3-export/mffd-export/ts-export/tapelaying/`
(`Z:\dump\dataset` → `/mnt/pve/unas/dump/dataset`). **354 GB**, 8251
`Track_N__Run_N_/` folders + `collection.json` (coll 48297) + `hierarchy.json`
+ a 461 MB `manifest.json` (the v15 importer manifest). The live
`mffd-afp-tapelaying` collection has the **tree** (8251 Track DataObjects, each
with `attributes||run_number` + `attributes||track_number`) but **zero payloads**
— the W2 import built the hierarchy and never attached the references.

**Each `Track_N__Run_N_/` carries exactly 6 references (matching the 6
`referenceIds` in its `metadata.json`):**

| On disk | Kind | Contents |
|---|---|---|
| `ts/Timeseries.csv` | TimeseriesReference | 34 TPS channels (`DEVICE=TPS`, `fitCorrelation`, `area`, `width`…) |
| `files/TPS raw data.{0-10}` + `.11` | FileBundle | 11 PNG (1292×964 grayscale profilometer frames) + 1 CSV |
| `files/TPS intermediate evaluation files.{0-10}` | FileBundle | 11 **TIFF** (2048×873) — the operator's "TIFFs" |
| `files/TPS 3D pointclouds.{0-1}` | FileBundle | 2 ASCII point clouds (`X Y Z R G B`) |
| `files/FSD course 3D pointclouds` | **singleton** FileReference | 1 ASCII point cloud |
| `files/Robot program` | **singleton** FileReference | 1 KRL program (`DEF PG43_PLY122_Track23…`) |

The numbered `.0/.1/…` suffixes are **complete independent files** (the fileOids
inside each bundle) — NOT split parts. Multi-file groups → `FileBundleReference`;
the two 1-file groups → **singletons** (FR1b one-file rule).

**The fix is the scripted v15 ingest, not a hand-rolled loop.** The path already
exists: `scripts/mffd-ingest-kickoff.sh` (pre-flight + resumable v15 importer,
worker-pooled, state-file resume) + readiness doc
`aidocs/agent-findings/mffd-ingest-346gb-readiness-2026-05-31.md`. It consumes
exactly this `ts-export/tapelaying/manifest.json` and attaches the 6 refs/track
to the existing tree by `run_number`. Re-running resumes and backfills the
payloads onto the 8251 tracks.

**Launch requirements (pre-flight gates the kickoff enforces):**
1. **Staging dir** → point `STAGING_DIR` at
   `/mnt/pve/unas/dump/dataset/cube3-export/mffd-export/ts-export/tapelaying`
   (the kickoff default `…/dump/ts-export` predates the `dataset/` reorg).
2. **Fresh dest API key** in `examples/mffd-showcase/scripts/.env.local`
   (`SHEPARD_API_KEY` — the prior JWT from `mffd-ingest-keys-2026-05-31.txt` is
   likely expired; must pass `GET /v2/users/me` → 200).
3. **Capacity** — host ZFS ≥200 G, NFS ≥400 G free; **PgBouncer** pool ≥ `workers·5+10`.
4. **Completeness-non-negotiable, sequential** — `MFFD_WORKERS=4` default; never
   parallel with another heavy ingest (shared-pool 504 risk,
   `feedback_no_parallel_heavy_ingests`). ~24 h wall (W2 estimate).
5. **Snapshot bracket** once stable (PRE-MUT-SNAP suspended pre-reset — confirm
   posture; this ATTACHES to existing tracks, doesn't delete, so lower risk).
6. **TIFF preview** (`TIFF-PREVIEW-SUPPORT`, in flight) makes the intermediate-
   evaluation TIFFs render inline once attached — the two land complementary.

**Remaining operator decision:** approve launching the ~24 h / 354 GB sequential
ingest + supply/confirm the fresh dest API key. No open structural questions —
the mapping is fully decoded.

## §3 svdx = welding (separate track, data already present)

svdx is **welding** process data (Trumpf/TC-Scope), imported on the stringer +
spot welding collections. This work is unchanged by the correction *except* it is
now correctly scoped to **welding**, not tapelaying.

| Layer | State |
|---|---|
| `fileKind` on `.svdx` | 947/951 carry `fileKind="svdx"` (4 NULL stragglers). A `FileKindDetector` claims `.svdx`. |
| Manifest annotations (`urn:shepard:svdx:*`) | **0** of 951 — `SvdxManifestParser` never fired on the bulk-ingested files. |
| Binary parse | `SvdxBinaryParser` + `SvdxManifestExtractor` + `SvdxEnvelope` exist. |
| CSV-driven extraction | `SvdxCsvTransformExecutor` needs a `.csv` sibling; none present (`SVDX-CSV-SIBLINGS-MISSING`). |
| View | `SvdxChannelChartShape` + `SvdxChannelChartRenderer` exist; Vue component open (`SVDX-CHANNEL-CHART-VUE`). |

**Phase A (welding svdx) — backfill the manifest parse (S, safe).** Run
`SvdxManifestParser` over the 951 `fileKind=svdx` FileReferences to emit
`urn:shepard:svdx:*` structural annotations (uses the **binary** parser — no
`.csv`). Ship as `POST /v2/admin/svdx/parse-backfill` (instance-admin, collection
filter, dry-run) mirroring the video backfill; fix the ingest hook so future
`.svdx` parse automatically; fix the 4 NULL stragglers. Additive; no data move.
Every welding svdx DataObject then shows *what welding signals are inside*.

**Phase B (welding svdx) — extract welding traces (M).** Extend
`SvdxBinaryParser` to materialise the welding process traces as derived
`TimeseriesReference`s (measurement prefix `svdx_weld_`), gated on 2–3-file
validation against a known weld before full backfill. Data-mutating → operator
confirm; PROV-O Activity per extraction; source `.svdx` untouched.

**Phase C (welding svdx) — surface (S).** Ship `SVDX-CHANNEL-CHART-VUE` so the
parsed channels render inline on the `.svdx` FileReference detail page.

## §4 Backlog wiring

- `TPS-TAPELAYING-DATA-MISSING` → **§2 ingest** (the real "missing TPS data").
  **Not** resolved by any svdx work. Blocked on operator source-location input.
- `SVDX-PROMOTE-KIND` → mostly done (fileKind on 947); residual = ingest-time
  parse hook (Phase A) + 4 NULL stragglers. **Scope = welding.**
- `SVDX-PARSED-CONTENT-SHAPE` → Phase A + C.
- `SVDX-CSV-SIBLINGS-MISSING` → Phase B fallback.
- `SVDX-CHANNEL-CHART-VUE` → Phase C.
- `AAH1`/`AAH2` (binary RE) → Phase B foundation.

## §5 First references

`mffd-afp-tapelaying` (8251 tracks, `attributes||run_number` join key);
`mffd-stringer-welding` + `mffd-spot-welding` (the svdx-bearing welding
collections); `plugins/fileformat-svdx/` (`SvdxManifestParser`,
`SvdxBinaryParser`, `SvdxEnvelope`, `SvdxCsvTransformExecutor`,
`SvdxChannelChartRenderer`); `feedback_completeness_nonnegotiable.md`,
`feedback_no_parallel_heavy_ingests.md`, `feedback_singleton_file_*` (FR1b);
the video-promotion PR (the sibling fileKind+backfill pattern Phase A mirrors);
`feedback_file_viewers_as_view_recipe.md`.
