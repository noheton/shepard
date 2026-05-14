---
layout: default
title: Storage backends (deployment reference)
permalink: /reference/deployment-storage/
description: File storage in shepard — MongoDB GridFS (default), S3-compatible (Garage / MinIO / SeaweedFS / AWS S3 / Wasabi — queued behind FS1b), HSDS for HDF5, backup strategies per backend.
---

# Storage backends

shepard stores **bytes** in three places, by payload kind:

- **MongoDB GridFS** — files, file-bundles, structured-data docs,
  user avatars. Today's only path; FS1b will introduce a
  `FileStorage` SPI that swaps GridFS for S3-compatible storage.
- **PostgreSQL + TimescaleDB** — timeseries.
- **HSDS (sidecar)** — HDF5 containers (opt-in via the `hdf`
  profile).

This page covers the file-storage backends. Timeseries lives in
TimescaleDB by definition (see
[backup + restore]({{ '/reference/deployment-backup/' | relative_url }})
for the `pg_dump` recipe); HDF5 sidecar storage is documented in
the [admin guide]({{ '/admin/#hdf5-hsds-opt-in-sidecar' | relative_url }})
§"HDF5 (HSDS)".

## GridFS (today's default)

The shepard backend writes file payloads to **MongoDB GridFS**
in the same MongoDB instance that backs structured data and
file-bundle metadata. Single MongoDB connection, single backup
target, single failure domain.

### How GridFS works in shepard

- Each `FileReference` / `FileBundleReference` / singleton
  `FileReference` maps to a GridFS file in the `fs.files` +
  `fs.chunks` collections under the database named by
  `MONGO_DATABASE`.
- Chunk size defaults to 255 KiB (GridFS default); shepard
  doesn't override.
- A FileBundle (FR1a) bundles multiple GridFS files into a
  logical group with a default file group; a singleton
  `FileReference` (FR1b) is a single GridFS file behind the
  primitive.

### Sizing GridFS

GridFS has effectively no upper bound — the practical ceiling is
the MongoDB instance's disk. **Plan for 1× the raw file payload
+ 3% chunk-bookkeeping overhead.**

A 5 TiB file workload → ~5.15 TiB MongoDB volume + WiredTiger
cache + index overhead. See
[sizing]({{ '/reference/deployment-sizing/' | relative_url }}).

### Backup posture for GridFS

`mongodump` against the shepard database captures GridFS
bookkeeping (`fs.files`) AND chunk data (`fs.chunks`) in one
go. The backup is **internally consistent** at point-in-time
when run against a quiescent or replica-set-read-secondary
instance.

For large GridFS deployments, prefer:

- A **MongoDB replica set** with `mongodump --readPreference
  secondary` against a delayed secondary, so the backup doesn't
  hammer the primary's working set.
- Or **filesystem snapshots** of the MongoDB data volume taken
  via LVM / ZFS / cloud-provider snapshot APIs; faster than
  `mongodump` for multi-TiB volumes.

See [backup + restore]({{ '/reference/deployment-backup/' | relative_url }})
for the recipes.

### Limits of GridFS

GridFS is fine up to ~5 TiB and ~5M files per instance. Beyond
that, the **inode pressure** (each chunk is a document) and the
**backup duration** start to bite. The fix is FS1b's
S3-compatible storage swap.

## S3-compatible (queued behind FS1b)

The **`FileStorage` SPI** lets operators swap GridFS for
S3-compatible object storage. Design is at
[`aidocs/45-gridfs-to-s3-evaluation.md`](https://github.com/noheton/shepard/blob/main/aidocs/45-gridfs-to-s3-evaluation.md)
and ADR-0024
(`aidocs/63-architecture-decision-log.md#adr-0024`).
**The SPI is queued (FS1a/FS1b);** the description below is the
target shape post-FS1b. **Until FS1b ships, GridFS is the only
backend.**

The SPI is a single interface — `de.dlr.shepard.fs.FileStorage`
— with `write(InputStream, key)`, `read(key)`, `delete(key)`,
and `range(key, start, end)`. Plugins implement it for the
backend they target.

Planned plugins (FS1c+):

| Plugin | Target | Use case |
|---|---|---|
| **`shepard-plugin-fs-gridfs`** | MongoDB GridFS | Today's default — extracted from in-core code into a plugin during FS1b. **Always available.** |
| **`shepard-plugin-fs-s3`** | AWS S3 | Cloud-native deploys |
| **`shepard-plugin-fs-garage`** | [Garage](https://garagehq.deuxfleurs.fr/) | Self-hosted S3-compatible; minimal RAM footprint, written in Rust. Recommended for self-hosted multi-host clusters. |
| **`shepard-plugin-fs-minio`** | [MinIO](https://min.io/) | Self-hosted S3-compatible; HA-capable; well-known ops shape. |
| **`shepard-plugin-fs-seaweedfs`** | [SeaweedFS](https://github.com/seaweedfs/seaweedfs) | Self-hosted; can mix with file / volume servers in one cluster. |
| **`shepard-plugin-fs-wasabi`** | [Wasabi](https://wasabi.com/) | Cloud S3-compatible; cheaper than AWS S3 for cold-ish workloads. |

Per the
[plugin-first heuristic in `CLAUDE.md`](https://github.com/noheton/shepard/blob/main/CLAUDE.md),
every adapter ships as a `shepard-plugin-fs-*` JAR; the SPI
stays in core.

### S3 deployment shape (preview)

When FS1b ships, the deploy shape is:

```env
# Active file-storage backend
shepard.fs.backend=s3

# S3 connection
shepard.fs.s3.endpoint=https://s3.example.com
shepard.fs.s3.region=eu-central-1
shepard.fs.s3.bucket=shepard-files
shepard.fs.s3.access-key-id=...
shepard.fs.s3.secret-access-key=...   # via CredentialCipher, see secrets management
```

Backend selection is **runtime-configurable** via the A3b feature
toggle pattern — you can swap backends without rebuilding the
image. Existing GridFS data is **not auto-migrated**; the
operator runbook for migration ships alongside FS1b.

### Capacity + cost shape

S3-compatible storage scales linearly with the bytes stored;
egress costs and request costs vary per provider.

| Provider | Storage cost (per TiB/month, 2025) | Egress | Notes |
|---|---|---|---|
| Garage (self-hosted) | hardware-only | free | RAM-efficient; recommended self-hosted |
| MinIO (self-hosted) | hardware-only | free | HA-capable; heavier than Garage |
| SeaweedFS (self-hosted) | hardware-only | free | Mixed file + object server cluster |
| AWS S3 | ~€23 (Standard) / ~€10 (IA) | charged | Default cloud option |
| Wasabi | ~€7 | free up to 1×storage/month | Cheaper than S3 for write-heavy |

Self-hosted backends mean **you own the operational story**
(replication, scrubbing, geographic redundancy). Cloud backends
mean **you trust the provider's durability guarantee** (S3 = 11
nines).

### Backup posture for S3-compatible

S3's durability + versioning often replaces application-level
backup. The recommended posture:

- **Enable bucket versioning** so a `DELETE` is reversible for
  the retention window.
- **Enable lifecycle rules** to age objects to cheaper tiers
  (S3 IA / Glacier / Wasabi cold).
- **Cross-region replication** for geographic redundancy.
- A periodic **manifest dump** (`shepard-admin fs verify`,
  forthcoming) compares the database's expected object list
  against what's in the bucket — catches the rare bookkeeping
  drift.

S3 + database backups still need to be **coordinated** — a
point-in-time MongoDB backup must correspond to the S3 bucket
state at the same time, or restore will reference objects that
aren't there. The runbook is in
[backup + restore]({{ '/reference/deployment-backup/' | relative_url }}).

## HDF5 via HSDS

HDF5 is a **separate payload kind**, not file storage in the
GridFS-vs-S3 sense. The HSDS sidecar (Phase 1, A5a) manages
its own storage — POSIX by default, S3-compatible / Azure Blob
via HSDS upstream env vars.

See
[admin guide §HDF5 (HSDS)]({{ '/admin/#hdf5-hsds-opt-in-sidecar' | relative_url }})
for the operator runbook, including:

- POSIX vs S3-backed HSDS.
- `~1.2× raw HDF5 size` disk planning.
- HSDS-side backup (`hsadmin` export).

HDF5 backup is **independent** of GridFS — you back up the
HSDS storage volume / bucket separately.

## Plugin-payload-kind storage

Plugins that introduce new payload kinds (video, AAS submodels,
lab-bench recordings) ship their **own** `FileStorage`-shaped
backend or piggyback on an existing one. Each plugin's reference
page covers its storage:

- **Video** — `aidocs/53` §"Video as a first-class payload
  kind"; ships its own segment + manifest storage (HLS-shaped).
- **AAS** — `aidocs/52`; stores Shell / Submodel JSON in
  MongoDB.
- **HDF5** — HSDS sidecar (see above).

The plugin runbook is in
[plugins reference]({{ '/reference/plugins/' | relative_url }}).

## Quick decision matrix

| Scenario | Recommended backend |
|---|---|
| Small lab, < 5 TiB total files, single host | GridFS (today's default) |
| Medium institute, ≥ 5 TiB, multi-host | GridFS until FS1b ships; then Garage or MinIO |
| Cloud-native deploy (AWS / Azure / GCP) | AWS S3 / Azure Blob / GCS, once FS1b ships |
| Cold archival (write-once, rare read) | Wasabi or S3 Glacier, once FS1b ships |
| Heavy HDF5 (NumPy arrays, scientific data) | HSDS sidecar in addition to GridFS |

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [Sizing recommendations]({{ '/reference/deployment-sizing/' | relative_url }}) — disk budgets per backend.
- [Backup + restore]({{ '/reference/deployment-backup/' | relative_url }}) — backup recipes per backend.
- [Plugins reference]({{ '/reference/plugins/' | relative_url }}) — drop-in JARs.
- [File reference (primitive reference)]({{ '/reference/file-reference/' | relative_url }})
- [File bundle (primitive reference)]({{ '/reference/file-bundle/' | relative_url }})
- [HDF container (primitive reference)]({{ '/reference/hdf-container/' | relative_url }})
- [`aidocs/45`](https://github.com/noheton/shepard/blob/main/aidocs/45-gridfs-to-s3-evaluation.md) — GridFS-vs-S3 design + benchmark.
- [`aidocs/63`](https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md) ADR-0024 — S3 / Garage decision.
- [Admin guide §HDF5 (HSDS)]({{ '/admin/#hdf5-hsds-opt-in-sidecar' | relative_url }}) — HDF5 sidecar setup.
