---
layout: default
title: Sizing recommendations (deployment reference)
permalink: /reference/deployment-sizing/
description: CPU / RAM / disk recommendations for shepard backend, Neo4j, MongoDB, Postgres+TimescaleDB, PostGIS, HSDS, Prometheus, Grafana — small lab to medium institute to multi-tenant.
---

# Sizing recommendations

Numbers here are **starting points**, not guarantees. Real shepard
load depends on:

- **Number of Collections / DataObjects.** Neo4j RAM scales with
  the active working set.
- **File sizes + count.** MongoDB GridFS overhead is ~3% of payload;
  HDF5 via HSDS is ~1.2× raw size.
- **Timeseries cardinality.** TimescaleDB compresses heavily once
  chunks are old; sizing assumes 30-day uncompressed window.
- **Concurrent users + API calls.** The backend's `-Xmx2G` is the
  documented default; scales linearly with concurrent active users.

Tune from the floor up. If you don't know your load yet, deploy at
the **small-lab** profile, watch Grafana for a week, then scale the
hot bottleneck — usually Neo4j page cache or MongoDB cache.

## Single-host floor (small lab)

A single physical or virtual host can carry shepard end-to-end for
a small lab — a single research group with **≤ 50 Collections,
≤ 10k DataObjects, ≤ 500 GiB total payload, ≤ 10 concurrent
users**.

| Resource | Floor | Notes |
|---|---|---|
| CPU | 4 vCPU | Backend JVM + Neo4j + MongoDB + Postgres on one host |
| Memory | **8 GiB** | Backend `-Xmx2G`, Neo4j `2G heap + 3G page cache`, MongoDB `2G WiredTiger`, plus OS headroom (~1 GiB) |
| Disk | **100 GiB SSD** | Data sits under `/opt/shepard/{neo4j,mongodb,timescaledb,backend,caddy}` |
| Network | 1 Gbit/s LAN | On-premises posture; no cloud egress assumed |

The memory floor reflects the explicit
`JAVA_OPTS=-Xms2G -Xmx2G` for the backend and the
`NEO4J_server_memory_*` settings in
`infrastructure/docker-compose.yml`.

This profile fits comfortably on a **Hetzner CCX13** (4 dedicated
vCPU, 16 GB, ~€15/month — the upstream staging reference) with
room to grow. A **Hetzner CX32** (4 vCPU, 8 GB, ~€7/month) is the
working absolute floor.

## Medium institute (10× the small lab)

A whole institute — **≤ 500 Collections, ≤ 100k DataObjects,
≤ 5 TiB total payload, ≤ 50 concurrent users** — wants
**per-service sizing**, not a single big host.

### Per-service breakdown

| Service | vCPU | RAM | Disk | Notes |
|---|---|---|---|---|
| **shepard backend** (Quarkus) | 4 | **6 GiB** (`-Xms4G -Xmx6G`) | 20 GiB (logs + config) | Stateless; horizontally scale by adding instances behind the reverse proxy once vCPU saturates |
| **shepard frontend** (Nuxt 3) | 1 | 1 GiB | 5 GiB | Stateless |
| **Neo4j 5.24** | 4 | **12 GiB** (heap 4 GiB, page cache 8 GiB) | 200 GiB SSD | Page cache should hold the working set of relationships; tune up if `dbms_memory_pagecache_size` is consistently under-sized in Grafana |
| **MongoDB 8.0** | 4 | **8 GiB** (WiredTiger cache 4 GiB) | 2 TiB SSD | GridFS payloads + structured-data docs |
| **PostgreSQL + TimescaleDB 2.24** | 4 | **8 GiB** (`shared_buffers` ~2 GiB, `effective_cache_size` ~6 GiB) | 1 TiB SSD | Timeseries; compression kicks in for chunks older than 30 days |
| **PostgreSQL + PostGIS** (optional) | 2 | 4 GiB | 200 GiB SSD | Spatial; only if you set `SHEPARD_INFRASTRUCTURE_SPATIAL_ENABLED=true` |
| **HSDS** (optional, HDF5) | 2 | 4 GiB | **1.2× raw HDF5** | Plan disk separately; HSDS chunk-store overhead is ~20% |
| **Prometheus** | 2 | 4 GiB | 50 GiB | 30-day metric retention by default |
| **Grafana** | 1 | 1 GiB | 5 GiB | UI only |
| **Caddy / Nginx / Traefik** | 1 | 1 GiB | 5 GiB | TLS termination |

**Total floor: 16 vCPU, 40 GiB RAM, 3.5 TiB SSD** (excluding HSDS).

### Layout options

- **Single host** with the above as docker-compose services — the
  reference posture. Works on a **Hetzner CCX23** (8 vCPU, 32 GiB,
  ~€30/month) for the low end of medium scale, **CCX33** (16 vCPU,
  64 GiB) for the comfortable middle.
- **Split databases** to dedicated hosts when one DB consistently
  saturates: Neo4j + Mongo on host A, Timescale + Postgres on
  host B, backend + frontend + Caddy on host C. The compose file's
  database services accept `_HOST` env vars pointing at remote
  endpoints — see `infrastructure/docker-compose.yml` and the
  upstream production guide.
- **Kubernetes** is a known-good posture for the medium-institute
  scale; ports of the reference compose to Helm charts live
  outside this repo (community-maintained).

## Multi-tenant / cluster scale

For shared infrastructure across multiple research groups —
**≥ 5k Collections, ≥ 1M DataObjects, ≥ 50 TiB payload, ≥ 100
concurrent users** — the deploy shape is outside the scope of
this reference. The shapes that are known to work:

- **Backend pool** behind a load balancer (the backend is
  stateless once JWT validation is in place).
- **Neo4j cluster** (Causal Cluster) — Neo4j Enterprise feature;
  shepard does not depend on cluster-only Cypher features.
- **MongoDB replica set** — supported transparently by the Java
  driver; just change the connection string.
- **TimescaleDB multi-node** — the timeseries continuous
  aggregates (`aidocs/12`) become more important at this scale.
- **S3-backed file storage** instead of GridFS — see
  [storage backends]({{ '/reference/deployment-storage/' | relative_url }}).
  Plan for FS1b's `FileStorage` SPI swap once it lands.

Reach out via the GitHub issue tracker if you're standing up at
this scale — the team would like to hear from you.

## Sizing the JVM heap

The backend reads `JAVA_OPTS` from the container environment.
The shipped default is `-Xms2G -Xmx2G`; grow it when you grow
load.

A working rule:

```
backend_heap = 2 GiB + (concurrent_users × 40 MiB)
```

So 10 concurrent users → 2.5 GiB; 50 users → 4 GiB; 100 users → 6
GiB. Above ~8 GiB heap, switch to ZGC
(`-XX:+UseZGC -XX:+ZGenerational`) to keep pause times tight.

The backend's Caffeine permission cache is sized by
`shepard.permissions.cache.max-size` (default 10000 entries
post-A4). Each cache entry is ~200 bytes — 10k entries → ~2 MiB,
negligible. Bump to 50000 if you have many concurrent users
hitting many different entities.

## Sizing Neo4j

Neo4j page cache should **hold the working set of nodes +
relationships**. Working set is roughly proportional to active
DataObjects × ~5 KiB per node.

| Active DataObjects | Recommended page cache |
|---|---|
| ≤ 10k | 1 GiB |
| 10k – 100k | 3 GiB (default) |
| 100k – 1M | 8 GiB |
| 1M – 10M | 32 GiB |

Heap (the `*_heap_max__size` setting) should be
**half of page cache, max 32 GiB**. Above 32 GiB you hit the
compressed-oops boundary and lose memory efficiency; scale
horizontally instead.

## Sizing MongoDB

WiredTiger cache should hold the **active document set**, which
for shepard means the **structured-data documents the active
users are reading**. GridFS payloads (file content) don't need
to be cached; they stream.

A working rule:

```
mongo_cache = max(2 GiB, structured_doc_GiB × 0.5)
```

For payloads heavy on **files** (camera-cycle bundles, HDF5
sidecars, lab-journal attachments) the cache stays at 2 GiB; for
payloads heavy on **structured data** (large StructuredDataPoint
collections), grow proportionally.

Set via `--wiredTigerCacheSizeGB <n>` on the `mongo` container.

## Sizing TimescaleDB

`shared_buffers` should be **25% of the host's available RAM**,
capped at 16 GiB. `effective_cache_size` is the OS-cache-aware
hint — set to 50–75% of host RAM.

For timeseries write throughput, the key knob is `wal_buffers` —
default 16 MiB, set to 64 MiB if you're sustained-writing
> 10k points/sec.

For read latency on aggregations, continuous aggregates (CAGGs)
materialize rollups; see `aidocs/12-timescaledb-performance-analysis.md`
for the design.

## Sizing the HSDS sidecar (HDF5)

Per HSDS docs: plan for **~1.2× the raw HDF5 size** on disk. A
100 GiB HDF5 file → ~120 GiB on HSDS chunk store.

HSDS memory is unbounded against the chunk store; budget **4 GiB
RAM** for a workload of `≤ 100 GiB raw HDF5` and grow linearly.
HSDS scales horizontally — run multiple SN / DN nodes if you saturate
a single sidecar.

## Disk layout

The reference deploy assumes one large disk under `/opt/shepard/`.
At medium-institute scale, split per-database to dedicated volumes:

```
/opt/shepard/neo4j/        → SSD volume A (low latency critical)
/opt/shepard/mongodb/      → SSD volume B (high throughput)
/opt/shepard/timescaledb/  → SSD volume C (write-heavy)
/opt/shepard/backend/      → cheap volume D (logs, config)
/opt/shepard/caddy/        → cheap volume E (TLS state, ACME)
/opt/shepard/hsds-storage/ → cheap volume F (object-friendly)
/opt/shepard/backups/      → mounted from a separate physical device
```

Backups should land on a **separate physical device** —
`/opt/shepard/backups/` mounted from an NFS share, a USB-attached
disk, or an object store. Local-only backups die with the host.

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [Storage backends]({{ '/reference/deployment-storage/' | relative_url }})
- [Backup + restore]({{ '/reference/deployment-backup/' | relative_url }})
- [Monitoring + observability]({{ '/reference/deployment-monitoring/' | relative_url }})
- [System requirements]({{ '/system-requirements/' | relative_url }}) — the higher-level requirements page.
- [`aidocs/12`](https://github.com/noheton/shepard/blob/main/aidocs/12-timescaledb-performance-analysis.md) — TimescaleDB performance analysis.
- [`aidocs/35` §3](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md) — HSDS storage-layer design.
