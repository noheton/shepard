---
title: spatial-importer — Operator Install Guide
stage: feature-defined
last-stage-change: 2026-06-02
audience: operator
---

# Install — spatial-importer operator runbook

This is the operator-facing install guide for the W7 spatial promotion
pass. The plugin is Python-only and runs as a one-shot CLI, not a
sidecar.

## Prerequisites

1. **shepard-plugin-spatiotemporal must be enabled and migrated.** The
   PostGIS schema (`shepard_spatial`) must have been created by Flyway
   on backend startup. Confirm:
   ```sql
   \dn shepard_spatial   -- (TimescaleDB psql)
   ```
   And the plugin must appear in `GET /v2/admin/plugins`.
2. **W2 must have run.** Every `Track_NN__Run_NN_/` folder must already
   exist as a DataObject in the target Collection — the W2 importer
   creates them and uploads the original FileReferences. The W7 pass
   only promotes them; it does not create DataObjects.
3. **Source files accessible.** The path passed via `--source` must be
   the tapelaying root containing `Track_NN__Run_NN_/files/` subdirs.
4. **uv installed** (Python 3.11+). The CLI uses the inline `# /// script`
   header — `uv run` resolves dependencies on the fly.

## Configuration

No deploy-time configuration on the backend side. The CLI takes its
config from CLI flags + two env vars:

| Env var          | Required | Default                                  |
|------------------|----------|------------------------------------------|
| `SHEPARD_URL`    | no       | `https://shepard-api.nuclide.systems`    |
| `SHEPARD_API_KEY`| yes      | —                                        |

The API key must belong to a user with WRITE permission on every Track
DataObject in the target Collection. Recommended: grant the
`spatial-importer` service user WRITE on the Collection root and let it
propagate.

## Running the pass

```bash
export SHEPARD_URL=https://shepard-api.nuclide.systems
export SHEPARD_API_KEY=$(cat ~/.shepard.key)

cd /opt/shepard/plugins/spatial-importer
./cli/main.py --spatial-pass \
  --collection-app-id 019e7243-…-… \
  --source /mnt/mffd-staging/w7/mffd-export/ts-export/tapelaying \
  --workers 4
```

### Smoke-test first (recommended)

```bash
./cli/main.py --spatial-pass --dry-run \
  --collection-app-id 019e7243-…-… \
  --source /mnt/mffd-staging/w7/mffd-export/ts-export/tapelaying \
  --limit 5
```

Prints the file inventory for the first 5 Tracks without any writes.

### Limit to a slice

```bash
./cli/main.py --spatial-pass --limit 100 ...
```

Useful for staged rollout.

### Line-scan pass (W7b, 2026-06-02)

The `--linescan-pass` step promotes the `TPS raw data.N` 1292×964 8-bit
grayscale PNG chunks into brush-trace SpatialDataContainers. Each row of a
PNG becomes one SpatialDataPoint carrying the full 1292-element intensity
vector in `measurements.intensities`.

```bash
python3 -m cli.main --linescan-pass \
  --collection-app-id 019e7243-…-… \
  --source /mnt/mffd-staging/w7/mffd-export/ts-export/tapelaying \
  --intensity-decimation 1 \      # 1 = preserve all 1292 cols; raise to 2/4/8 for storage trade-off
  --row-period-ns 1 \              # row-as-time period when no wall-clock available
  --linescan-batch-size 64 \      # rows per /payload POST (default 64 keeps body ~5 MB)
  --workers 4
```

Storage cost: ~5 KB JSONB per row × 964 rows × 38 chunks per Track ≈
180 MB per Track before TimescaleDB compression. Plan ≈ 40 GB for the
full 8,251 Tracks at `intensity-decimation=1`.

You can run both passes together:

```bash
python3 -m cli.main --spatial-pass --linescan-pass --collection-app-id … --source …
```

Format-drift gate: the decoder errors loudly on non-PNG / non-grayscale
inputs. If a future MFFD export ships 16-bit PNGs, the importer emits
`bit_depth=16` in the structured log and the operator decides whether to
proceed (`MFFD-SPATIAL-LINESCAN-FORMAT-DRIFT-1`).

## Idempotency

The pass is safe to re-run. SHA256 of each source file's bytes is
stored as `urn:shepard:spatial:source-sha256` on the resulting
SpatialDataContainer. On re-run, files matching an existing annotation
are skipped (logged at INFO).

If you need to *force* re-import (e.g. the source file changed and you
want the new content), drop the existing SpatialDataContainer first
via the UI or the existing `DELETE /shepard/api/spatialDataContainers/{id}`.

## Coordinate-frame handshake (optional)

Pass `--frame-app-id <UUID>` to bind every container to a CST1
`:CoordinateFrame`. The frame must already exist
(`POST /shepard/api/coordinate-frames` per `aidocs/data/85`).

Without `--frame-app-id`, the container's `frameAppId` is null and the
viewer renders points in the file's native coordinate system without
applying any transform. This is fine for solo-viewer use; binding to a
frame becomes important once the W5 RoboDK scene-graph is in place and
the viewer wants to render the pointcloud *inside* the AFP cell.

## Healthcheck

There's no long-running health endpoint — the CLI is one-shot. The
final stdout line summarises the run:

```
2026-06-02 09:55:12 INFO [spatial-importer] done — promoted=423 skipped_idempotent=18 errors=0
```

Non-zero exit codes:

- `1` — `SHEPARD_API_KEY` missing.
- `2` — `--spatial-pass` flag not provided.
- `3` — Pass completed but at least one track had errors. The
  Activity/PROV trail records what failed.

## Scaling notes

The 24,753-pointcloud full MFFD dataset is sized as follows on a fresh
deployment:

- 24,753 source files × ~190 KB avg = ~4.7 GB of ASCII.
- Decoded as `(x, y, z)` triples in PostGIS:
  - ~4,000 points/file × 24,753 = ~99 M rows in `shepard_spatial.profile`.
  - With JSONB measurements + metadata: ~50 GB on disk after compression.
- Throughput: ~3 files/second per worker against a non-pgbouncered
  TimescaleDB. With 4 workers, that's a ~35-minute pass for a full
  re-promotion. The pass is IO-bound, not CPU-bound; raising workers
  past ~8 increases contention rather than throughput.

## Known pitfalls

1. **JANDEX-HANG-FIX-2026-05-29 / Quarkus CDI augmentation**: if the
   backend image is fresh (post-Jandex-fix) but the plugin container is
   not, `GET /shepard/api/spatialDataContainers` may return 503. Rebuild
   the backend image (`make redeploy-backend`).
2. **PgBouncer 20/200 default pool**: with `--workers > 8` the pool can
   saturate; lower workers or raise pool size.
3. **Permission boundary**: the API key user must have WRITE on the
   Track DataObject. A 403 from `POST /spatialDataReferences` means the
   key user doesn't see the DO; check via
   `GET /shepard/api/collections/{c}/dataObjects/{do}/permissions`.
