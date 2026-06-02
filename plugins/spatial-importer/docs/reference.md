---
title: spatial-importer — Reference
stage: feature-defined
last-stage-change: 2026-06-02
audience: power-user, plugin-author
---

# Plugin: spatial-importer — promote opaque pointcloud FileReferences

`shepard-plugin-spatial-importer` is a thin Python sidecar that promotes
ASCII pointcloud and trajectory files (the MFFD AFP tapelaying export
shape) into typed `SpatialDataContainer` rows on the existing
[`shepard-plugin-spatiotemporal`](../../spatiotemporal/docs/reference.md)
substrate. It is the W7 wave of `aidocs/integrations/113`.

There is **no Java code** in this plugin — it consumes the existing
`/shepard/api/spatialDataContainers` REST surface. The shape is plugin-
first only because the Python CLI ships its own dependency tree,
documentation, fixtures, and release cadence; that's the structural
isolation rule (CLAUDE.md "think plugin-first for new features").

## What it does

For each `Track_NN__Run_NN_/` DataObject in a target Collection, the
spatial pass:

1. Walks the source directory for the three known spatial filenames
   (see "File classifier" below).
2. Parses each ASCII file (six columns `X Y Z R G B`, CRLF) into points.
3. Detects whether the `(R, G, B)` triple is uniform across all rows
   (it always is in the real MFFD export — `(0, 130, 255)` is the
   Keyence default visualisation colour, not real data). When uniform,
   the RGB is dropped so the downstream renderer can colour by the
   real value vector (height, time, channel value).
4. MERGEs one `SpatialDataContainer` per `(dataObjectAppId, kind,
   source-sha256)` triple. Idempotent on the SHA256 — re-running the
   pass against the same source produces zero new rows.
5. Streams the points to `POST /shepard/api/spatialDataContainers/{id}/payload`
   in 1000-row batches.
6. Demotes the original FileReference: PATCH `status=ARCHIVED` (best-
   effort, fire-and-forget) and attaches a `urn:shepard:spatial:promoted-to`
   annotation as the breadcrumb to the new container.

## File classifier

The CLI walks each Track's `files/` subdirectory and classifies each
entry by filename prefix:

| Source filename                 | SpatialDataContainer `kind` | Renderer        |
|---------------------------------|-----------------------------|-----------------|
| `TPS 3D pointclouds.0` / `.1`   | `profile`                   | pointcloud      |
| `FSD course 3D pointclouds`     | `trajectory`                | trajectory line |
| anything else                   | (skipped)                   | —               |

Files NOT promoted (intentionally — they aren't spatial point data):

- `TPS raw data.0…37` — these are 1292×964 grayscale **PNG camera frames**
  from the Keyence laser sensor, *upstream* of the .0/.1 reduction.
  They stay as opaque FileReferences. Filed as
  `MFFD-SPATIAL-RAW-DATA-INVESTIGATE` in `aidocs/16-dispatcher-backlog.md`.
- `TPS intermediate evaluation files.*` — opaque vendor format.
- `Robot program` — KRL source; promoted by the existing KRL plugin.

## Format research (confirmed 2026-06-02)

Two sample tracks were extracted from `mffd.tar.gz`:

- **Track 66 / Run 23133** — TPS .0 = 4118 rows; FSD = 4168 rows.
- **Track 67 / Run 24043** — TPS .0 + TPS .1 share first lines (twin
  scans of the same stripe); FSD shows true 3D path (X varies
  1475→2845 mm, Y varies -589→+52 mm, Z varies 2622→4175 mm).

Coordinate frame: AFP robot TCP frame, millimetre units. The
`SpatialDataContainer.frameAppId` (MFFD-SPATIAL-FRAME-HANDSHAKE) can
optionally point at the matching CST1 `:CoordinateFrame` so the
renderer aligns the pointcloud inside the W5 RoboDK scene.

## CLI

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems \
SHEPARD_API_KEY=$(cat ~/.shepard.key) \
  ./cli/main.py --spatial-pass \
    --collection-app-id 019e7243-…-… \
    --source /opt/shepard/mffd-staging/w7/mffd-export/ts-export/tapelaying \
    --frame-app-id 019e9000-…-…  # optional CST1 frame
    --workers 4 \
    --limit 5  # smoke-test the first 5 tracks
```

`--dry-run` skips writes; useful to count source files first.

## Annotation namespaces

Predicates live under `urn:shepard:spatial:*` per CLAUDE.md's "evolve in
a new namespace" rule:

| Predicate                          | Subject              | Value                                          |
|------------------------------------|----------------------|------------------------------------------------|
| `urn:shepard:spatial:promoted-to`  | `:FileReference`     | UUID of the SpatialDataContainer it became     |
| `urn:shepard:spatial:source-sha256`| `:SpatialDataContainer` | SHA256 of the source ASCII file (idempotency) |
| `urn:shepard:spatial:kind`         | `:SpatialDataContainer` | `profile` / `trajectory` / `brush-trace`     |
| `urn:shepard:spatial:source-filename` | `:SpatialDataContainer` | Original filename                            |

## REST endpoints consumed

All endpoints are existing surfaces of the spatiotemporal plugin (no
new endpoints are added in this PR — the `/v2/` rule applies to new
endpoints, and consuming existing ones is fine):

- `GET /shepard/api/collections/by-app-id/{cId}` — resolve numeric id.
- `GET /shepard/api/collections/by-app-id/{cId}/dataObjects` — list Track DOs.
- `GET /shepard/api/collections/{c}/dataObjects/{do}/fileReferences` — find demotion target.
- `GET /shepard/api/collections/{c}/dataObjects/{do}/spatialDataReferences` — idempotency check.
- `POST /shepard/api/spatialDataContainers` — create the typed container.
- `POST /shepard/api/spatialDataContainers/{id}/payload` — batch upload points.
- `POST /shepard/api/collections/{c}/dataObjects/{do}/spatialDataReferences` — bind ref.
- `PATCH /v2/collections/{c}/data-objects/{do}/file-references/{fr}` — demote.

## Backend changes co-shipped

The W7 PR also extends `shepard-plugin-spatiotemporal`:

- `SpatialDataContainer` (Neo4j) gains a nullable `frameAppId` property
  pointing at the CST1 `:CoordinateFrame.appId`. Mirrors the PostGIS
  `shepard_spatial.profile_container.coord_frame_app_id` column so a
  Cypher traversal can reach the frame without a substrate hop.
- `SpatialDataContainerIO` surfaces `frameAppId` (omitted from the wire
  when null, preserving upstream byte-fidelity).
- `V106` NOOP Cypher migration documents the additive property.
- `SpatialDataContainerService.createContainer()` propagates
  `frameAppId` from IO to entity.

## Run-time behaviour

- **Workers**: `concurrent.futures.ThreadPoolExecutor`, default 4.
- **Backoff**: exponential with jitter (1, 2, 4, 8, 16, 60s) on HTTP
  429, 500, 502, 503, 504 and connection errors. Re-raises on
  persistent failure (completeness is non-negotiable for an importer).
- **Idempotency key**: SHA256 of the source bytes, stored as
  `urn:shepard:spatial:source-sha256` on the container. Re-runs detect
  matching annotations and skip.
- **Demotion**: secondary write, never blocking the primary promotion.
  Failure logs a WARN.

## What's NOT shipped (follow-ups)

- `MFFD-SPATIAL-IMPORTER-1-LIVE` — the production deployment runbook
  (W2 must drain into the dest Collection first).
- `MFFD-SPATIAL-PARSER-1` — binary format support (none needed today
  but a stretch goal if a future export ships binary).
- `MFFD-SPATIAL-RAW-DATA-INVESTIGATE` — the 38-PNG-per-track TPS raw
  data story. Likely stays as a video-like reference; not point data.
- `MFFD-SPATIAL-BRUSH-TRACE-FROM-RAW` — if research shows the raw PNG
  frames carry spatial tagging metadata (e.g. EXIF with robot pose),
  they could be joined to TimescaleDB channel values to form a true
  `kind=brush-trace` container per `aidocs/data/90 §3`.
- `MFFD-SPATIAL-IMPORTER-API-V2` — thin `/v2/spatial-data-containers`
  wrappers (current REST surface is the upstream-compat `/shepard/api/`).

## Cross-references

- `aidocs/integrations/113-mffd-real-data-import-plan.md §W7`
- `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md §GAP-5`
- `aidocs/agent-findings/mffd-afp-spatial-analysis-cases.md`
- `aidocs/data/90-spatial-as-temporal-sweep.md` (kind taxonomy)
- `aidocs/data/85-coordinate-frame-tree.md` (CST1 frame handshake)
- `plugins/spatiotemporal/docs/reference.md` (downstream substrate)
- `plugins/vis-trace3d/docs/reference.md` (Trace3DCanvas sibling)
