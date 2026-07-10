---
stage: feature-defined
last-stage-change: 2026-07-10
---

# SVDX promotion + the "missing TPS data" plan

**Trigger (operator, 2026-07-10):** on the MFFD stringer/tapelaying data, `.svdx`
files sit as plain File rows and *"the TPS data is missing"*. This plan connects
three backlog rows into one coherent piece of work:
`SVDX-PROMOTE-KIND`, `SVDX-PARSED-CONTENT-SHAPE-2026-06-29`,
`SVDX-CSV-SIBLINGS-MISSING-2026-06-29`, and `TPS-TAPELAYING-DATA-MISSING`.

## §1 What `.svdx` is (and where the TPS data lives)

`.svdx` = the DLR ZLP MFFD **AFP TPS profilometer** export (Trumpf/TC-Scope). It
holds the tape-placement sensor's **per-tow cross-section profiles** — the rich
width/height/displacement/area/fitError arrays per tow, per scan — that the
timeseries container's 34 TPS *summary* channels (`area0..3`, `width0..3`,
`fitCorrelation`, `n_tows`, …) only summarise. **The operator's "missing TPS
data" is precisely this profile data: it is inside the unparsed `.svdx` files,
not in the timeseries.**

## §2 Current state (verified 2026-07-10, live)

| Layer | State |
|---|---|
| `fileKind` on `.svdx` | **Done** — 947 `:SingletonFileReference` carry `fileKind="svdx"` (4 NULL stragglers). A `FileKindDetector` claims `.svdx`. |
| Manifest annotations (`urn:shepard:svdx:*`) | **Missing on live data** — **0** of 947 files carry them. The `SvdxManifestParser` never fired: the files were bulk-ingested (v15/v16) before/around the plugin, so the parser hook did not run. |
| Binary parse capability | **Exists** — `SvdxBinaryParser` + `SvdxManifestExtractor` + `SvdxEnvelope` parse the binary header/manifest directly (no `.csv` needed for structure). |
| Full TPS timeseries extraction | **Blocked** — `SvdxCsvTransformExecutor` needs a paired `.csv` sibling; the seeded/ingested `.svdx` have **no `.csv` sibling** (`SVDX-CSV-SIBLINGS-MISSING`). The binary profile arrays are not yet extracted to timeseries. |
| View | `SvdxChannelChartShape` + `SvdxChannelChartRenderer` exist (shipped 2026-06-29); the Vue component is open (`SVDX-CHANNEL-CHART-VUE`). |

**So svdx-fileKind promotion is essentially already done. The real gaps are
(a) the parser never ran on live data, and (b) the TPS profile data is only
reachable via the binary parser, which does not yet extract the profile arrays
into a queryable timeseries.**

## §3 The plan — three phases

### Phase A — backfill the manifest parse (S, safe, high signal-to-effort)
Run `SvdxManifestParser` over the 947 existing `fileKind=svdx` FileReferences to
emit the `urn:shepard:svdx:*` structural annotations (formatVersion, tow count,
scan count, channel inventory, bounds). Uses the **binary** parser — **no `.csv`
needed**. Ship as an admin backfill endpoint mirroring the video backfill:
`POST /v2/admin/svdx/parse-backfill` (instance-admin, filter by collection/limit,
dry-run), re-using `SvdxManifestParser` per file. Also fix the ingest hook so
future `.svdx` uploads parse automatically (the fileKind is set but the parser
run isn't wired on the bulk path). Fix the 4 NULL-fileKind stragglers.
**Outcome:** every `.svdx` DataObject shows its parsed manifest (the
`SVDX-PARSED-CONTENT-SHAPE` view lights up); the operator sees *what's in the
svdx* even before full data extraction. Additive; no data move.

### Phase B — extract the TPS profile data (M, the "missing TPS data" fix)
The profile arrays must become queryable. Two sources, in preference order:
1. **Binary extraction (preferred, no external deps).** Extend `SvdxBinaryParser`
   to read the per-tow profile arrays from the binary `.svdx` body (the envelope
   already locates the manifest; the profile blocks follow a documented offset
   table — see `SvdxEnvelope`). Materialise them as a derived **TimeseriesReference**
   (or a per-tow StructuredData/profile payload) via the generic
   `MAPPING_RECIPE` path (`SvdxCsvTransformExecutor`'s sibling — a
   `SvdxBinaryTransformExecutor`), measurement prefix `svdx_tps_profile_`. This
   is the honest fix: the TPS profiles land as first-class, annotatable,
   chartable timeseries alongside the AFP channels.
2. **CSV sibling (fallback, if binary offsets prove unstable).** Resolve
   `SVDX-CSV-SIBLINGS-MISSING`: locate/produce the paired `.csv` (the raw TC-Scope
   export the operator may still have on `/mnt/pve/unas`) and run the existing
   `SvdxCsvTransformExecutor`. Host-only (needs the raw export) — flag for the
   operator.
**Decision gate:** attempt (1) on 2–3 representative `.svdx` and validate the
extracted profiles against a known tow before committing to the full backfill.
The binary format was partially reverse-engineered in `AAH1`/`AAH2` — build on
that, don't restart it.

### Phase C — surface it (S)
- Ship `SVDX-CHANNEL-CHART-VUE` (the Vue component for `SvdxChannelChartShape`)
  so the parsed channels/profiles render inline on the `.svdx` FileReference
  detail page (per VIEWER-AS-VIEW-RECIPE — the viewer is the ViewRecipe).
- The `.svdx` FileReference detail page gets an "Interpret profiles" affordance
  (in-context-tools rule) that triggers the Phase-B materialisation on demand
  when a DataObject's svdx isn't yet extracted.

## §4 Sequencing + risk

- **Phase A first** — cheapest, safe, immediately answers *"what's in the svdx"*
  and de-risks B (the manifest tells us the profile block layout per file).
- **Phase B is the real "missing TPS data" fix** and the only data-mutating step
  (it writes derived timeseries). Gate it on the 2–3-file validation; it mutates
  real MFFD data, so confirm with the operator before the full backfill (PROV-O
  Activity per extraction; additive — the source `.svdx` is untouched).
- **Phase C** rides A/B.
- Everything stays `/v2/`, appId-native, fileKind-subtype (never a new payload
  kind or `/shepard/api/` change), per the plugin-v2 + evolve-in-new-namespace
  rules.

## §5 Backlog wiring

- `SVDX-PROMOTE-KIND` → **mostly done** (fileKind set on 947); residual = the
  ingest-time parse hook (Phase A) + 4 NULL stragglers.
- `SVDX-PARSED-CONTENT-SHAPE` → Phase A + C.
- `SVDX-CSV-SIBLINGS-MISSING` → Phase B fallback (2).
- `TPS-TAPELAYING-DATA-MISSING` → **resolved by Phase B** (the profiles ARE the
  missing TPS data). Cross-link once B lands.
- `SVDX-CHANNEL-CHART-VUE` → Phase C.
- `AAH1`/`AAH2` (binary RE) → the foundation Phase B builds on.

## §6 First references

`plugins/fileformat-svdx/` (`SvdxManifestParser`, `SvdxBinaryParser`,
`SvdxEnvelope`, `SvdxCsvTransformExecutor`, `SvdxChannelChartRenderer`);
TimescaleDB container 221633 (tapelaying, 34 TPS channels — the summaries);
the video-promotion PR `MP4-PROMOTE-VIDEO` (the sibling fileKind+backfill
pattern Phase A mirrors); `feedback_file_viewers_as_view_recipe.md`;
CLAUDE.md `## Always: think plugin-first` + `## Always: the audit trail is a
graph` (PROV-O per extraction).
