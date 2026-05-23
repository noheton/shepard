---
layout: default
title: Backup and restore
description: Per-substrate backup recipes for a running shepard instance.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/backup/
---

# Backup and restore

Each persistence store has its own backup path. shepard does not ship a
unified backup tool today; coordinate dumps across substrates so they
reflect a consistent point-in-time, especially when permissions or schema
migrations are mid-flight.

## Per-substrate recipes

| Substrate | Recipe |
|---|---|
| **Neo4j 5.24** | `neo4j-admin database dump` (or volume snapshots of `/opt/shepard/neo4j/data`). |
| **MongoDB 8.0** | `mongodump` against the `mongodb` service. |
| **Postgres + TimescaleDB** | `pg_dump` (Timescale-aware via `timescaledb-tune` / `pg_dump` extension support, depending on workload), or volume snapshots of `/opt/shepard/timescaledb`. |
| **Postgres + PostGIS** (optional) | `pg_dump` against the `postgis` service. |
| **Backend logs and config** | Volumes at `/opt/shepard/backend/logs` and `/opt/shepard/backend/config`. |
| **Caddy data and config** | Volumes at `/opt/shepard/caddy/data` and `/opt/shepard/caddy/config`. |
| **HSDS POSIX storage** (optional HDF5 sidecar) | Snapshot `./hsds-storage` (or the bucket prefix if you flipped HSDS to an object-store backend). |
| **S3 file storage** (Garage by default; optional via `shepard-plugin-file-s3`) | Provider-native — for Garage, `garage bucket snapshot`. See the [storage substrate guide]({{ '/admin/storage/' | relative_url }}). |

## Coordinated snapshot

Run all dumps against the same wall-clock window. The safest pattern:

1. Pause new writes (close the frontend behind a maintenance page, or
   temporarily disable API-key roles via
   `PATCH /v2/admin/features` — see
   [Configuration]({{ '/admin/config/' | relative_url }})).
2. Run all per-substrate dumps in parallel.
3. Re-enable writes.

If a brief read-only window is acceptable, the simpler pattern is to
stop the backend container, snapshot the volumes, restart:

```bash
cd infrastructure
docker compose --env-file .env stop backend
# snapshot or rsync /opt/shepard/{neo4j,mongodb,timescaledb,postgis,backend,caddy}
docker compose --env-file .env start backend
```

## Restore

Restore is the inverse of dump, performed against an **empty** store:

1. Stop the backend container.
2. Wipe the substrate's data volume (or provision a fresh one).
3. Restore from the dump (`neo4j-admin database load`, `mongorestore`,
   `pg_restore`).
4. Restart the backend. The migrations runner will detect the schema
   version and skip / run any necessary migrations.

**Cross-substrate consistency**: if a partial restore lands one
substrate at a different point-in-time than another, you may see
dangling references (e.g. a `DataObject` in Neo4j referencing a
`ShepardFile` that does not exist in MongoDB or S3). The frontend
surfaces these as broken links; the backend logs a `WARN` per
broken reference but does not fail. Re-running the matching dump
fixes the divergence.

## See also

- [Garage activation runbook](https://github.com/noheton/shepard/blob/main/docs/ops/garage-activation-runbook.md) — bringing up the Garage S3 sidecar (FS1b/c/d)
- [GridFS → S3 migration runbook](https://github.com/noheton/shepard/blob/main/docs/ops/migrate-gridfs-to-s3.md) — incremental, reversible, no downtime
