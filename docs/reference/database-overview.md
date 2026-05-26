---
layout: default
title: Database overview (reference)
description: What data lives in which substrate, how to connect to each one, and where to look when something breaks.
audience: admin
permalink: /reference/database-overview/
stage: deployed
last-stage-change: 2026-05-26
---

# Database overview

Shepard uses five persistence substrates, each chosen for a specific data shape.
This page tells you what lives where, how to reach each one from the host, and
which substrate to inspect first for a given symptom.

---

## Substrate map

| Substrate | Image (pinned) | What lives there | Compose service |
|---|---|---|---|
| **Neo4j** | `neo4j:5.26` | Collections, DataObjects, Users, Permissions, Activity provenance trail, SemanticAnnotations, all graph relationships | `neo4j` |
| **PostgreSQL + TimescaleDB** | `timescale/timescaledb:2.24.0-pg16` | Timeseries data points (`timeseries_data_points` hypertable), channel metadata (`channel_metadata`), permission audit log; PgBouncer connection pool in front | `timescaledb`, `pgbouncer` |
| **MongoDB** | `mongo:8.0.4` | File metadata (GridFS `fs.files`), file chunks (GridFS `fs.chunks`), structured-data documents | `mongodb` |
| **Garage S3** | `dxflrs/garage:v1.0.1` | Binary file objects (used when `STORAGE_BACKEND=s3`); also profile pictures | `garage` (under `files-s3` compose profile) |
| **PostGIS** | `postgis/postgis:16-3.5` | Geospatial vector data; optional — only running if the `geo` compose profile is active | `postgis` |

---

## Connection quick-reference

All commands assume you are on the host running `docker compose`.
Replace `<password>` with values from your `.env` or `infrastructure/.env`.

### Neo4j

```bash
# Cypher shell (interactive)
docker compose exec neo4j cypher-shell -u neo4j -p <password>

# Neo4j Browser: http://<host>:7474  (Bolt: bolt://<host>:7687)
```

### PostgreSQL / TimescaleDB

```bash
# psql directly on the TimescaleDB container (bypasses PgBouncer)
docker compose exec timescaledb psql -U shepard -d shepard

# Through PgBouncer (as the app sees it)
docker compose exec pgbouncer psql -h localhost -p 5432 -U shepard -d shepard
```

### MongoDB

```bash
# mongosh on the MongoDB container
docker compose exec mongodb mongosh -u shepard -p <password> shepard
```

### Garage S3

```bash
# Garage CLI — bucket list
docker compose exec garage /garage bucket list

# Garage admin token lives in GARAGE_ADMIN_TOKEN in your .env
docker compose exec garage /garage --rpc-secret <admin-token> status
```

### PostGIS

```bash
docker compose exec postgis psql -U shepard -d shepard
```

---

## Which substrate do I look at for X?

| Symptom / question | Start here |
|---|---|
| A Collection or DataObject is missing from the API | Neo4j — check for the node with `MATCH (d:DataObject {appId: '...'}) RETURN d` |
| Permissions check failing / 403 on a known-good resource | Neo4j — trace `has_permissions` edge and V76 `BasicEntity_appId_idx` index |
| Timeseries data not appearing in charts | TimescaleDB — check `channel_metadata` and `timeseries_data_points`; also check the Neo4j `:TimeseriesContainer` node exists |
| File download returns HTTP 500 | MongoDB/Garage — check GridFS `fs.files` for the document; if using Garage, check object existence and capacity |
| Structured-data document not found | MongoDB — query `db.structuredData.findOne({...})` |
| Geospatial query returns wrong results | PostGIS — `SELECT PostGIS_version();` first; verify the `geo` profile is active |
| SPARQL query timing out | Neo4j — n10s semantic layer; `:Resource` nodes are the largest label class (~50% of graph by volume at production scale) |
| Provenance / Activity log slow | Neo4j — verify the `Activity_startedAtMillis_idx` RANGE index (V75) is present via `SHOW INDEXES` |
| Every API request slow | Neo4j — verify the `BasicEntity_appId_idx` RANGE index (V76) is present; this index is on the permissions hot path and fires on every authenticated request |
| Migration aborts on startup | Check `MigrationsRunner` logs — Neo4j Flyway cursor and PostgreSQL Flyway cursor must be sequential. Do not run migrations out of order. |

---

## Schema migration cursors

Migrations are applied automatically by `MigrationsRunner` at application startup.

```bash
# Neo4j — last 5 applied migrations
docker compose exec neo4j cypher-shell -u neo4j -p <password> \
  "MATCH (n:__Neo4jMigration) RETURN n.version, n.description ORDER BY n.installedOn DESC LIMIT 5"

# PostgreSQL — last 5 applied migrations
docker compose exec timescaledb psql -U shepard -c \
  "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

The current schema floor is Neo4j **V76** (BasicEntity.appId index) and PostgreSQL **V1.17.0**
(TimescaleDB CAgg integer\_now fix). Instances below these versions are missing performance-critical
indexes — see the [post-ingest tuning runbook]({{ '/ops/db-optimisation-runbook/' | relative_url }}).

---

## Data volumes at production scale

The numbers below are representative of a full MFFD ingest
(~17 k DataObjects, 871 timeseries channels, 132 M data points).
Use them as order-of-magnitude guidance; your instance may differ.

| Substrate | Approximate size | Dominant contributor |
|---|---|---|
| Neo4j | ~200 MB (graph store) | `:Resource` semantic nodes from n10s (~50% of node count) |
| TimescaleDB | ~1 GB (before compression: ~18 GB) | `timeseries_data_points` hypertable; 23.5× compression ratio observed |
| MongoDB | ~1 GB | GridFS file chunks; a single large binary can dominate |
| Garage S3 | Varies by file storage mode; small if GridFS is primary | Binary file objects |
| PostGIS | Not deployed in typical installs | — |

---

## Further reading

- **[Post-ingest tuning runbook]({{ '/ops/db-optimisation-runbook/' | relative_url }})** — PROFILE queries, index verification, CAgg health check. Run this after the first real-scale ingest.
- **[Backup and restore]({{ '/admin/backup/' | relative_url }})** — per-substrate dump recipes.
- **[Storage substrate]({{ '/admin/storage/' | relative_url }})** — Garage vs. GridFS selection, capacity planning.
- **[Troubleshooting databases]({{ '/help/troubleshooting-databases/' | relative_url }})** — step-by-step fixes for the most common operator issues.
