---
layout: default
title: Storage substrate
description: File-storage adapter selection (GridFS vs S3 via Garage), migration runbooks, capacity planning.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/storage/
---

# Storage substrate

shepard's file payload kind is backed by a swappable storage adapter. As
of FS1a–FS1g the shipped adapters are:

| Adapter | Sidecar | Cost shape | Recommended at |
|---|---|---|---|
| **GridFS** (default) | None — uses the existing MongoDB | No extra services to run | < 10 GB, single-host deploy, no growth |
| **S3 via `shepard-plugin-file-s3`** | Garage (`dxflrs/garage:v1.0.1`) by default; any S3-compatible endpoint works | Adds one container (Garage) or external billing (AWS / B2 / Wasabi) | > 100 GB total, > 100 k files, browser-direct uploads |

GridFS stays **first-class supported** in this fork — it is not on a
deprecation path. The S3 adapter is the right call when you outgrow
GridFS's operational sweet spot.

## Zero-impact upgrade

If you pull this fork's main and **don't** set `shepard.storage.provider`,
you stay on GridFS. The V34 migration runs (one Cypher `SET` stamping
`providerId='gridfs'` on every existing `:ShepardFile`); the file-payload
REST surface is byte-identical to upstream. You can ignore the rest of
this page unless you actively want S3.

## When to migrate

| Symptom on your deployment | Migration urgency |
|---|---|
| GridFS data store > 100 GB total | Yes — Mongo's GridFS gets slow at scale; backups grow expensive |
| > 100 k files | Yes — listing latency degrades; deletes accumulate fragmentation |
| Frequent backend memory pressure on uploads (large multipart bodies proxied through Quarkus) | Yes — FS1c presigned URLs bypass the backend; bytes flow direct browser↔S3 |
| Need offsite replication / CDN / lifecycle policies | Yes — S3-compatible stores ship these natively |
| Small (< 10 GB), single-host deploy, no growth expected | No — GridFS is fine; the upgrade is opt-in |

## Migration runbooks

The migration is **incremental + reversible** by design — there is no
big-bang switchover, no read freeze, and no point-of-no-return. Two
operator-runbook docs in `docs/ops/` cover the lifecycle:

1. **[Garage activation runbook]({{ '/ops/garage-activation-runbook/' | relative_url }})**
   — bring up Garage v1.0.1 as the S3 sidecar on your host. Covers
   pre-conditions, compose override, bucket+credential creation,
   backend wire-up, post-flip smoke tests. Audience: operator on the
   nuclide.systems-shaped host.

2. **[GridFS → S3 migration runbook]({{ '/ops/migrate-gridfs-to-s3/' | relative_url }})**
   — switch existing data from GridFS to S3 in place. Covers the FS1a
   provider-tagging migration (V34), the FS1e1 big-bang admin REST,
   the FS1e2 continuous auto-sweep, and rollback. Audience: any
   operator running v1 or earlier of this fork.

## Plugin sidecar declaration (PM1f)

Activating the file-S3 plugin used to mean hand-editing a compose
override. As of PM1f, the plugin's `FileS3PluginManifest.sidecars()`
declares the Garage shape, and the operator-side renderer pastes the
compose snippet for you. See [Sidecars SPI]({{ '/reference/sidecars/' | relative_url }}).

## Capacity planning

| Adapter | Disk overhead | Notes |
|---|---|---|
| GridFS | ~5% (chunk metadata) | Stored alongside other Mongo collections; the same volume is the file store |
| Garage (S3) | ~3 × raw (default 3-replica) | Reduce by configuring fewer replicas if availability tolerates it |
| HSDS POSIX (HDF5 only) | ~1.2 × raw | Chunk-store overhead per HSDS docs |

## Backups

Per substrate:

- **GridFS** — included in your MongoDB dump (see
  [Backup and restore]({{ '/admin/backup/' | relative_url }})).
- **Garage** — provider-native (`garage bucket snapshot`), or rely on
  Garage's own multi-node replication for availability.
- **HSDS POSIX** — snapshot the `./hsds-storage` directory.
