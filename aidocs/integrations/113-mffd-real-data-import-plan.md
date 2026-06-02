---
name: MFFD real-data import plan
description: Comprehensive multi-wave plan to import the 271 GB MFFD dataset from /mnt/pve/unas into Shepard, mapped to existing features and templates.
type: project
stage: feature-defined
last-stage-change: 2026-06-02
---

# MFFD real-data import plan (113)

Companion to `aidocs/agent-findings/mffd-data-inventory-2026-06-02.md` (the inventory)
and `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md` (the gap analysis). This
doc is the **execution plan**: which wave imports what, against which existing
template, with which gate.

## 1. Targets + non-targets

**Targets** (in priority order):

1. Land `mffd.tar.gz / tapelaying` (Bucket 1) as a queryable, navigable Collection with
   timeseries + 3D pointclouds + robot programs.
2. Land `bridgewelding` (Bucket 2) and **wire its Predecessor edges back** to the
   matching layup tracks — the AFP-→-joining bridge.
3. Land RoboDK scene (Bucket 4a) at Collection level so every DO inherits its physical
   context.
4. Land thermography (Bucket 4b) as the NDT step.
5. Land Punktschweißungen + Stringerverbindung media (Bucket 3) — needs **parser
   plugins** + **scale gates**.

**Non-targets** for this plan:
- `later/` (defer per flo).
- `tool_sources/` (reference material, never ingested).
- `Stringerverbindung 288 GB` — gap-tracked separately; needs a source-side export run.

## 2. Reuse survey (per CLAUDE.md §reuse-before-reimplement)

The existing surfaces this plan compounds with:

- `scripts/mffd-ingest-kickoff.sh` (6-gate kickoff) — extend, do not rewrite.
- v15 importer (`tool_sources/shepard_importer/`) — already 4-worker + expo-backoff + n10s PROV-O writeback. **Use it for bucket 1 verbatim.**
- v16 PRESERVE-HIERARCHY (3-pass tree replication) — handles bridgewelding's AF_N → Execution_TS subtree.
- V100 MFFD process-type templates (8 templates, shipped in commit `1ae7edbba`).
- V101 demo casual templates (6 templates).
- TPL5 git ontology ingestion (used for vocabulary alignment, **not** data).
- TS-OPT3 (`timescaledb_toolkit` + CAgg routing) — must be on before mass TS writes.
- BATCH-API-4 audit — already identified the bulk endpoints.
- Garage S3 (FS1b) — `FileReference` payloads land here.
- SCENEGRAPH-CREATE-FROM-URDF — the bridge for the RDK file (post-RDK→URDF).

No new infrastructure is needed for waves 1–3.

## 3. Idempotency contract

Re-running this plan must be **safe**, **fast**, and **lossless**:

- Each manifest entry's `do_id` carries forward into a `urn:shepard:mffd:source-do-id`
  semantic annotation on the new (UUID v7 `appId`) DataObject. If a DO with that source
  id already exists in the target Collection, skip-and-update instead of duplicate.
- Each TS row insertion uses TimescaleDB's `ON CONFLICT DO NOTHING` on the
  (channel_metadata_appId, timestamp) primary key — duplicate timestamps are no-ops.
- Each FileReference upload checks `sha256` against the storage backend's existing
  object key. Match → skip upload, link the existing OID. Mismatch → version under
  PV1a.
- A persisted `ImportRun` `:Activity` records the run, the manifest path, the wave id,
  the start/end timestamps, and per-DO counters. Re-runs append a new Activity, never
  mutating the prior one.

## 4. Wave map

| Wave | Scope | Source bucket | Target | Importer | Cap | Gate | Status |
|------|-------|---------------|--------|----------|-----|------|--------|
| W1   | tapelaying small-batch dry-run | `mffd.tar.gz` (10 tracks) | new Collection `MFFD Upper-Fuselage (Real)` | v15 importer | 10 DOs, 10 TS, ~100 files | manual review | pending |
| W2   | tapelaying full | bucket 1 | same | v15 importer | 8,251 DOs, 8,251 TS, 241,962 files | TS-OPT3 ON, Garage healthy, 4 workers | pending |
| W3   | bridgewelding | bucket 2 | same Collection, as **`process-step-bridge-welding`** children | v16 PRESERVE-HIERARCHY | 13 AF parts + N executions | wave 2 done | pending |
| W4   | wire Predecessor edges layup→bridge | n/a | YAML mapping + `POST /v2/admin/mffd/process-chain-mapping` | (flo-authored YAML) | 8,251 edges | wave 3 done + flo authors mapping YAML | **infra ready 2026-06-02 (MFFD-AF-TRACK-MAPPING shipped); pending flo YAML** |
| W5   | RoboDK scene | bucket 4a (`MFZ.rdk`) | new SceneGraph attached at Collection level | SCENEGRAPH-CREATE-FROM-RDK | 1 scene | wave 2 done | pending |
| W6   | thermography | bucket 4b (`thermography.7z`) | new DOs under `MFFD Upper-Fuselage (Real)` | new `7z` importer step | ~6,000 TIFFs as ImageBundleReference | wave 2 done | pending |
| W7   | **TPS+FSD pointclouds → spatial substrate** (re-classify W2 file-stragglers) | `Track_NN__Run_NN_/files/TPS 3D pointclouds.*` + `FSD course 3D pointclouds` + `TPS raw data.*` | one `SpatialDataContainer` per track (plugin `spatiotemporal` v6) | new `shepard-plugin-spatial-importer` step | 8,251 tracks × {2 pointcloud + 1 FSD path + 38 raw-data chunks} = ~341,000 spatial profiles | wave 2 done, spatial plugin live | pending |
| W8a  | Punktschweißungen .svdx parser | bucket 3 | new DO per `.svdx` | `shepard-plugin-fileformat-svdx` tier-1 (manifest extractor; binary samples deferred to `MFFD-PLUGIN-SVDX-BINARY-PARSER-1`, CSV ingest to `MFFD-PLUGIN-SVDX-CSV-INGEST-1`) | 21 files / 4.9 GB (corrected from earlier 29-file estimate; one folder, not three) | **manifest parser shipped 2026-06-02; awaiting deploy + smoke** | partially unblocked — annotations land per upload; sample-level data still gated |
| W8b  | process video sweep | bucket 3 | VideoReference under DOs | existing VID1 | 139 MP4s / 133 GB | UI scale-test passes | gap-blocked |
| W8c  | ThermoCam TIFF stream | bucket 3 | ImageBundleReference | existing IB | 6,273 TIFFs / 0.95 GB | UI scale-test passes | gap-blocked |
| W9   | source-side Stringerverbindung export | source N: drive | bucket 1+2 shape | requires new `mffd-export` run on Stringerverbindung Collection | est. 280 GB | source-side cooperation | gap-blocked |

## 5. Per-wave detail

### W1 — Dry-run on 10 tracks

```bash
# Extract just the first 10 Track folders into a staging area
mkdir -p /opt/shepard/mffd-staging/w1
tar xzf /mnt/pve/unas/dump/dataset/mffd.tar.gz \
  -C /opt/shepard/mffd-staging/w1 \
  $(tar tzf /mnt/pve/unas/dump/dataset/mffd.tar.gz \
    | grep -E "^mffd-export/ts-export/tapelaying/Track_[0-9]+__Run_[0-9]+_/" \
    | awk -F/ '{print $1 "/" $2 "/" $3 "/" $4}' | sort -u | head -10)
# Subset the manifest.json to only those 10 tracks
python3 scripts/mffd-subset-manifest.py /opt/shepard/mffd-staging/w1 10
# Run the importer with --dry-run
shepard_importer/main.py \
  --manifest /opt/shepard/mffd-staging/w1/mffd-export/ts-export/manifest.json \
  --target-collection-name "MFFD Upper-Fuselage (Real) — W1 dry-run" \
  --workers 1 --dry-run
```

**Acceptance:** 10 DOs created, 10 TS hypertables populated, files uploaded to Garage,
all six gates from `mffd-ingest-kickoff.sh` green. Open the Collection in the UI and
click into one Track DO — chart loads, 3D pointcloud listed (no viewer yet, that's W5),
robot program previews as text.

### W2 — Full tapelaying

```bash
scripts/mffd-ingest-kickoff.sh \
  --archive /mnt/pve/unas/dump/dataset/mffd.tar.gz \
  --target-collection "MFFD Upper-Fuselage (Real)" \
  --workers 4 \
  --ts-opt3 on
```

**Acceptance:** importer log final counters match the source export log
(8,251 / 241,962 / 0 errors). Snapshot at end via PROV-USER-MIRROR.

### W3 — Bridgewelding

```bash
shepard_importer/main.py \
  --manifest /mnt/pve/unas/dump/dataset/4-Brückenschweißen/manifest.json \
  --target-collection "MFFD Upper-Fuselage (Real)" \
  --process-step bridge-welding \
  --workers 2
```

**Acceptance:** 13 AF_N DOs + N execution snapshots inserted with parent =
`MFFD Upper-Fuselage (Real)`. Each `StepMetaProcessStep` becomes one
StructuredDataReference (deduplicate the shared-file refs to one logical SD ref +
N citing-edges, see SD-DEDUPE-1 backlog row).

### W4 — Predecessor edges (YAML mapping + admin REST loader)

The natural join is on the `Track_NN__Run_NN_` → `AF_N` mapping. Today this mapping is
**not in the data** — it lives in flo's head as "AF_N is welded after layup of plies
P_{a..b} which contain tracks T_{x..y}".

**Infrastructure shipped 2026-06-02** — `MFFD-AF-TRACK-MAPPING` (closes GAP-4):
the YAML schema, validator, idempotent loader, REST endpoint, admin UI, and operator
runbook are all in place. The only remaining unblocker is flo authoring the YAML
mapping file. Until then, this wave is **infra-ready, content-pending**.

Operator workflow at W4 wave time:

1. flo authors `mffd-af-track-mapping.yaml` (off-repo) per the schema in
   [`aidocs/integrations/118-mffd-process-chain-mapping.md`](118-mffd-process-chain-mapping.md).
2. Validate read-only against the live instance:

   ```bash
   python3 scripts/validate-mffd-process-chain-mapping.py \
       --url https://shepard.example.org/v2 --api-key <key> \
       mffd-af-track-mapping.yaml
   ```

3. Apply via the admin UI (`/admin` → "MFFD process-chain mapping" tile) or curl:

   ```bash
   curl -X POST -H "Content-Type: application/yaml" -H "X-API-Key: <key>" \
       --data-binary @mffd-af-track-mapping.yaml \
       https://shepard.example.org/v2/admin/mffd/process-chain-mapping
   ```

4. The response carries `{matched, unmatched, edgesCreated, unresolved[], warnings[]}` —
   iterate the YAML until `unresolved[]` is empty.

Idempotent — re-applying the same YAML preserves the edge set; updating a
`transitionKind` refreshes the existing edge.

### W5 — RoboDK scene

Two paths:
- **Fast:** `MFZ.rdk` → upload as FileReference + a static screenshot; users open in
  RoboDK desktop. No scene-graph integration.
- **Right:** Convert `MFZ.rdk` to URDF via RoboDK's CLI (`RoboDK -CONVERT-RDK-URDF`), then
  call `POST /v2/scene-graphs/from-urdf`. The shipped URDF resolver renders it in-app.

Recommend "right" — the bridge between layup and bridgewelding is the **physical robot
cell**, and showing the same cell across waves is what makes the demonstrator stop being
a folder tree and become a digital twin.

### W6 — Thermography NDT

```bash
7z x /mnt/pve/unas/dump/dataset/thermography.7z -o/opt/shepard/mffd-staging/w6/
shepard_importer/main.py \
  --thermography-mode \
  --source /opt/shepard/mffd-staging/w6/ \
  --target-collection "MFFD Upper-Fuselage (Real)" \
  --process-step ndt-thermography
```

Per-region (P01_1teBahn, P01_2teBahn, P05_1teBahn, ...) becomes one DO carrying an
ImageBundleReference of its TIFF frames + a derived `quality_score`
(`urn:shepard:ndt:thermography:peak-delta-c`).

### W7 — TPS + FSD pointclouds → spatial substrate (re-classify W2 stragglers)

**Microsections are NOT part of MFFD** (separate showcase, Collection `019e7243…` stays
where it is). W7's real job is to re-classify the 3D-positioned tapelaying artefacts
that W2 lands as opaque FileReferences into the `spatiotemporal` (v6) substrate.

Per-track stragglers from `Track_NN__Run_NN_/files/`:

| File | Real shape | Target Shepard entity |
|------|------------|------------------------|
| `TPS 3D pointclouds.0` / `.1` | Keyence LJ-X8000 laser profilometer scan of laid tape surface; per-point `(X_track, Y_cross, Z_height)` in TCP frame (see `aidocs/agent-findings/mffd-afp-spatial-analysis-cases.md §1`) | `SpatialDataContainer` of `kind=profile` → `shepard_spatial.profile` PostGIS hypertable |
| `FSD course 3D pointclouds` | Fast Send Driver (KUKA) per-step TCP trajectory of where the tape was laid | `SpatialDataContainer` of `kind=trajectory` → linestring `(X, Y, Z, t)` |
| `TPS raw data.0 … .37` (38 chunks) | TPS device process stream — temporal series annotated with TCP position per sample (a *temporal sweep through space*) | `SpatialDataContainer` of `kind=brush-trace` (aidocs/data/90 §3) — each row = (timestamp, x, y, z, value-vector) joined to the timeseries channels |

Why this is the right shape:
- The `spatiotemporal` plugin already ships (`plugins/spatiotemporal/`), PostGIS + the
  `shepard_spatial` schema land on next backend redeploy (per
  `aidocs/data/90` live-runtime header), and the `vis-trace3d` plugin
  (`plugins/vis-trace3d/`) renders these directly.
- A FileReference holding a pointcloud blob is queryable only as "did this track produce
  a file?". A `SpatialDataContainer` is queryable as "find tracks whose surface deviation
  exceeded 0.4 mm in the cross-track band Y_cross ∈ [2, 4]" — the EN 9100 / R1 story.
- The spatial substrate also reuses the existing `shepardId` permission walk, so no new
  permission shape.

Importer step (new `shepard-plugin-spatial-importer` thin module — sibling to the
`importer` plugin):

```bash
shepard_importer/main.py --spatial-pass \
  --source /opt/shepard/mffd-staging/w7/mffd-export/ts-export/tapelaying/ \
  --target-collection "MFFD Upper-Fuselage (Real)" \
  --pointcloud-glob "TPS 3D pointclouds.*" --pointcloud-kind profile \
  --trajectory-glob "FSD course 3D pointclouds" --trajectory-kind trajectory \
  --brush-trace-glob "TPS raw data.*" --brush-trace-kind brush-trace \
  --workers 4
```

The pass:
- For each `Track_NN__Run_NN_/` DO already created in W2, MERGE one
  `:SpatialDataContainer` per kind referenced by that DO (idempotent on
  `{dataObjectAppId, kind}`).
- Streams pointcloud rows into `shepard_spatial.profile` with `track_appId`,
  `(x_track, y_cross, z_height)`, and a `profile_geom GEOGRAPHY(POINTZ)` column.
- Demotes the corresponding FileReferences to `:Archived` status with a
  `urn:shepard:spatial:promoted-to = <SpatialDataContainer.appId>` annotation, so
  the originals stay addressable but the canonical query path is now spatial.

**Acceptance:** Open Track 244, the "Cross-section profile" panel shows the laser
heightmap of the laid tape; the "FSD trajectory" panel renders the TCP path through
the cell scene-graph (W5 RoboDK URDF, shared coordinate frame via aidocs/data/85);
the brush-trace pane sweeps the TPS sensor field along that trajectory.

Status (2026-06-02):
- **`MFFD-SPATIAL-IMPORTER-1`** — ✓ shipped at `plugins/spatial-importer/`. Python
  CLI; parses the two ASCII formats; 19 pytest cases against real Track 66 / Track
  67 fixture bytes. SHA256 idempotency.
- **`MFFD-SPATIAL-FRAME-HANDSHAKE`** — ✓ shipped. `SpatialDataContainer.frameAppId`
  property + `SpatialDataContainerIO.frameAppId` + V106 NOOP Cypher migration +
  service create-flow propagation + 4 IOTest + 2 service tests.
- **`MFFD-SPATIAL-FILEREF-DEMOTE`** — ✓ shipped inside the importer (fire-and-forget
  PATCH of the original FileReference to `status=ARCHIVED` + `urn:shepard:spatial:promoted-to`
  annotation).
- **`MFFD-SPATIAL-VIEWER-UI`** — ✓ shipped (bonus). `SpatialPointsCanvas.vue` Three.js
  renderer with pointcloud + trajectory modes; `DataObjectSpatialContainersPane.vue`
  lists per-DO refs grouped by kind; spatial container detail page wires the viewer.
  13 Vitest cases.
- **`MFFD-SPATIAL-IMPORTER-LIVE`** — queued (blocked on W2 dest ingestion).

Honest scope statement (the prompt anticipated this): the original W7 plan called
out `TPS raw data.*` as a `kind=brush-trace` SpatialDataContainer carrying
(timestamp, x, y, z, value-vector) rows. Format research showed those files are
1292×964 grayscale PNG **camera frames** (the Keyence raw frame upstream of the
.0/.1 reduction), NOT point data with TCP-position tagging. They stay as
FileReferences. Filed as `MFFD-SPATIAL-RAW-DATA-INVESTIGATE` — the open question
is whether the PNGs carry per-frame robot-pose metadata that would let a future
pass join them to TimescaleDB channel values and produce a true brush-trace
container. The base W7 acceptance is met without that follow-up.

### W8 — Parser plugins & scale work

Blocked on:
- `.svdx` parser plugin (W8a) — TwinCAT Scope is an XML+blob format; no public library.
  See feature-gap doc, **GAP-1**.
- Video scale (W8b) — 133 GB across 139 MP4s tests `VideoReference.upload` and
  `VideoPlayer.vue` chunked streaming, neither of which has been validated at this
  cardinality.
- TIFF scale (W8c) — 6,273-file ImageBundle stresses the existing IB UI.

These waves only start after corresponding gap closures.

### W9 — Source-side Stringerverbindung

Out-of-scope for this plan. Track as `MFFD-STRINGER-EXPORT` requiring an
`mffd-export` tool run on the source Collection (which doesn't yet exist DLR-side).

## 6. Estimated wall-clock

Assumptions: PgBouncer 20/200 pool, Garage 17 TB free, 4 workers, ~5 MB/s per worker
sustained file upload, ~3,000 rows/s per worker timeseries insert.

| Wave | Duration |
|------|----------|
| W1   | ~15 min  |
| W2   | **~24 h** (271 GB) — overnight |
| W3   | ~10 min  |
| W4   | ~1 min   |
| W5   | ~30 min (manual RDK→URDF + upload) |
| W6   | ~45 min  |
| W7   | <1 min   |
| W8a  | gated   |
| W8b  | ~12 h (mostly upload) |
| W8c  | ~30 min  |

## 7. Pre-flight checklist (extend kickoff script)

The existing `scripts/mffd-ingest-kickoff.sh` has 6 gates. Add:

```bash
# Gate 7: target Collection capacity
collection_count=$(curl -fsSL -H "Authorization: Bearer $TOKEN" \
  https://shepard.nuclide.systems/v2/collections | jq '.data | length')
[ "$collection_count" -lt 10000 ] || { echo "Coll count >10k — confirm"; exit 1; }

# Gate 8: PROV-USER mirror table seeded
curl -fsSL -H "Authorization: Bearer $TOKEN" \
  https://shepard.nuclide.systems/v2/admin/users/mirror \
  | jq '.users | length > 0' \
  | grep -q true || { echo "PROV-USER-MIRROR empty"; exit 1; }

# Gate 9: V100 + V101 templates present
curl -fsSL -H "Authorization: Bearer $TOKEN" \
  "https://shepard.nuclide.systems/v2/templates?source=V100-mffd" \
  | jq '.data | length == 8' \
  | grep -q true || { echo "V100 templates incomplete"; exit 1; }
```

## 8. Backlog rows generated by this plan

The plan generates new `aidocs/16` rows tracked separately:

- `MFFD-IMPORT-W1` … `MFFD-IMPORT-W9` — one per wave
- `MFFD-AF-TRACK-MAPPING` — get the canonical AF_N → ply/track table from domain expert
- `MFFD-VIDEOREF-SCALE-1` — validate VideoReference + player at 133 GB / 139 MP4
- `MFFD-IMAGEBUNDLE-SCALE-1` — validate IB at 6,273-frame thermal series
- `MFFD-RDK-URDF-CONVERTER` — RDK→URDF tooling (fast path is good enough; right path is the gap)
- `MFFD-SD-DEDUPE-1` — dedupe shared-payload StructuredDataReferences in bridgewelding
- See feature-gap doc for the rest.

## 9. Definition of done

The plan is "done" when an unauthenticated visitor can:

1. Open `MFFD Upper-Fuselage (Real)` Collection.
2. See the RoboDK cell as a Trace3D-style scene.
3. Click into ply P05, then Track 244, then see ~190 channels including the
   Q1-AFP-anomaly-shape (TCP-temp spike at ply 5 — synthetic seed.py story rendered against real data).
4. Follow Predecessor → `bridgewelding/AF_3` and see the NCR / parameter history.
5. Follow Predecessor → `ndt-thermography/P05_1teBahn_Thermo_converted` and see the
   peak-delta-c quality score and the TIFF frames.

This visit reaches the *full* MFFD process chain end-to-end from one click into one
ply. That is the demonstrator the Clean Aviation JU brief was written for.
