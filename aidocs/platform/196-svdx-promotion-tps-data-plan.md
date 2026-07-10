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

The tapelaying process export (per-course TIFF profile/thermography images +
"other files") lives on the operator's source (host `/mnt/pve/unas` or the ZLP
export), **not yet in Shepard**. The fix is an **ingest**, not a parser:

1. **Locate the source export** (operator-held). Establish the directory layout
   and how a file maps to a `Run NNNNN` / track (filename convention or a
   manifest). *Needs operator input — the raw export is host-only.*
2. **Map file → track by `run_number`.** Resolve each track's `appId` from
   `mffd-afp-tapelaying` via `attributes||run_number`; attach each source file as
   a **singleton `FileReference`** (`POST /v2/files?parentDataObjectAppId=…`) per
   the one-file rule (FR1b). TIFFs get `fileKind` via the detector; "other files"
   by extension.
3. **Completeness-non-negotiable ingest** — retry-forever with adaptive backoff,
   never SKIP (per `feedback_completeness_nonnegotiable`); run **sequentially**,
   not parallel with any other heavy ingest (shared-pool 504 risk). Bracket with
   a snapshot pair once the destination is stable (PRE-MUT-SNAP is currently
   suspended pre-reset — confirm posture before mutating).
4. **Surface per-track.** TIFFs render inline via the image viewer (VIEWER-AS-
   VIEW-RECIPE); the Track detail page lists them in its File panel.

**Open questions for the operator (blockers for §2):**
- Where is the tapelaying source export, and what is its directory/filename
  convention? What is the "other files" set beyond the TIFFs?
- Is a track's data 1 TIFF (→ singleton) or a set (→ bundle)? Cardinality drives
  the reference shape.
- One-shot ingest, or does it need to be a repeatable importer (a
  `shepard-plugin-importer` recipe) for future campaigns?

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
