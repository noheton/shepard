---
layout: default
title: Backup + restore (deployment reference)
permalink: /reference/deployment-backup/
description: Backup strategies per shepard database — Neo4j (neo4j-admin database dump), MongoDB (mongodump), Postgres+TimescaleDB (pg_dump), PostGIS (pg_dump), HSDS (hsadmin export), CredentialCipher key. Tested-restore obligation.
---

# Backup + restore

shepard does **not** ship a unified backup tool. Each persistence
store has its own backup path, and a complete shepard backup is
the **coordinated** capture of all of them.

This page covers the recipes per backend, the coordination story,
and the tested-restore obligation that turns "we have backups"
into "we can survive a disk failure."

## The five things to back up

| Store | What it holds | Recipe |
|---|---|---|
| **Neo4j** | The graph — Collections, DataObjects, References, Permissions, Roles, Activity log | `neo4j-admin database dump` or volume snapshot |
| **MongoDB** | GridFS file payloads + structured-data docs + lab-journal entries | `mongodump` or volume snapshot |
| **PostgreSQL + TimescaleDB** | Timeseries | `pg_dump` or volume snapshot |
| **PostgreSQL + PostGIS** (optional) | Spatial data | `pg_dump` |
| **`~/.shepard/keys/`** | API-key signing key + `CredentialCipher` key | File copy |

Two **optional** stores to back up if you enable the relevant
profile:

| Optional store | What it holds | Recipe |
|---|---|---|
| **HSDS** (HDF5 sidecar) | HDF5 chunk-store | `hsadmin` export + storage-backend backup |
| **Backend logs + config** | `/opt/shepard/backend/` | File copy |
| **Caddy state** | TLS ACME state + reverse-proxy config | File copy |

## Backup posture

The recommended baseline: **daily backups, off-host, with a
tested restore each quarter.**

- **Daily** — runs after the lowest-traffic hour. A
  small-lab-scale shepard backs up in ~10 minutes; a
  medium-institute scale backs up in ~1 hour.
- **Off-host** — the backup destination is a **separate
  physical device** (institutional backup target, remote NFS,
  S3 / object store). Local-only backups die with the host.
- **Retention** — 14 daily + 12 weekly + 12 monthly is a
  common starting point (~38 backups retained at any time).
  Drive retention from your data-recovery requirement, not
  default-comfort.
- **Tested restore each quarter** — restore one daily snapshot
  to a scratch host; verify the frontend loads + a sample
  Collection is intact. An untested backup is not a backup.

## Neo4j backup

### `neo4j-admin database dump` (recommended)

```bash
# Stop accepting writes (optional — Neo4j 5 supports online
# backups against a paused or running primary). For a cold
# dump, stop the backend first.
docker compose stop backend

# Dump the database to a file on the host
docker exec neo4j neo4j-admin database dump neo4j \
  --to-path=/data/backups

# Resume
docker compose start backend
```

The output file is `neo4j-<timestamp>.dump`. Move it off-host:

```bash
rsync -avP /opt/shepard/neo4j/data/backups/ backup-host:/backups/shepard/neo4j/
```

### Volume snapshot (alternative)

If you have LVM / ZFS / cloud-provider snapshot APIs, snapshot
the Neo4j data volume:

```bash
docker compose stop backend
lvcreate --snapshot --name shepard-neo4j-snap --size 10G \
  /dev/vg0/shepard-neo4j
docker compose start backend
# now copy the snapshot somewhere safe before lvremove
```

Snapshots are **faster** than `neo4j-admin database dump` on
multi-100-GiB stores but tie restore to the same Neo4j version
as the snapshot was taken under. Upgrading Neo4j (5.24 → 5.25)
through a snapshot restore is fine; cross-major (5 → 6) is
**not**.

### Restore

```bash
docker compose stop neo4j backend
docker exec neo4j neo4j-admin database load neo4j \
  --from-path=/data/backups \
  --overwrite-destination=true
docker compose start neo4j backend
```

Watch the backend startup log for the Neo4j-Migrations bootstrap
— a restored DB triggers idempotent re-checks against the
migration history, which is safe. The `MigrationsRunner` is
fail-fast post-A1e — if a migration breaks after restore, the
backend won't start. (Read the error and fix the migration; the
restore itself is fine.)

## MongoDB backup

### `mongodump` (recommended)

```bash
# Dump the shepard database
docker exec mongo mongodump \
  --uri="mongodb://$MONGO_USERNAME:$MONGO_PASSWORD@localhost/$MONGO_DATABASE" \
  --out=/data/backups/$(date +%F)

# Move off-host
rsync -avP /opt/shepard/mongodb/backups/ backup-host:/backups/shepard/mongo/
```

`mongodump` against a single-node deploy reads from the primary;
the backup is **point-in-time consistent** for the database it
dumps. For a replica set, point at a delayed secondary:

```bash
docker exec mongo mongodump \
  --uri="mongodb://...secondary.example.com:27017/$MONGO_DATABASE" \
  --readPreference=secondary
```

### Restore

```bash
docker exec mongo mongorestore \
  --uri="mongodb://$MONGO_ROOT_USERNAME:$MONGO_ROOT_PASSWORD@localhost" \
  --drop \
  /data/backups/<dump-dir>
```

`--drop` ensures a clean restore; without it, the restore merges
into existing collections (rarely what you want).

### Volume snapshot (alternative)

WiredTiger journal-aware snapshots via LVM / ZFS:

```bash
# WiredTiger requires fsyncLock to flush journal first
docker exec mongo mongosh --eval 'db.fsyncLock()'
lvcreate --snapshot ...
docker exec mongo mongosh --eval 'db.fsyncUnlock()'
```

## PostgreSQL + TimescaleDB backup

TimescaleDB is a Postgres extension — `pg_dump` knows about it
since Postgres 14 + Timescale 2.x. The recipe:

```bash
docker exec timescaledb pg_dump \
  -U $POSTGRES_USER \
  -d $POSTGRES_DB \
  -Fc \
  -f /var/lib/postgresql/backups/shepard-$(date +%F).dump

rsync -avP /opt/shepard/timescaledb/backups/ backup-host:/backups/shepard/timescale/
```

Use the **custom format** (`-Fc`) — it's the most flexible
restore target. Plain SQL dump (`-Fp`) is human-readable but
slow.

For very large timeseries (TiB-scale), prefer
**continuous physical backup via `pg_basebackup` + WAL
archiving** — the recipe in
[Timescale's docs](https://docs.timescale.com/use-timescale/latest/backup-restore/).
That's outside the scope of this page.

### Restore

```bash
docker exec timescaledb pg_restore \
  -U $POSTGRES_USER \
  -d $POSTGRES_DB \
  --clean --if-exists \
  /var/lib/postgresql/backups/shepard-<date>.dump
```

`--clean --if-exists` ensures dropped tables are recreated
cleanly; without it, restore conflicts.

## PostgreSQL + PostGIS backup (optional)

Same as TimescaleDB but against the `postgis` service:

```bash
docker exec postgis pg_dump \
  -U $POSTGRES_USER \
  -d $POSTGRES_DB \
  -Fc \
  -f /var/lib/postgresql/backups/spatial-$(date +%F).dump
```

Restore is identical to TimescaleDB.

## HSDS backup (optional)

The HDF5 sidecar (A5a) stores chunks under
`./hsds-storage` (POSIX backend) or in a configured S3 bucket
(opt-in S3 backend).

### POSIX backend

```bash
rsync -avP /opt/shepard/hsds-storage/ backup-host:/backups/shepard/hsds/
```

Snapshot the volume if you have LVM / ZFS — same recipe as
Neo4j.

### S3 backend

If you've configured HSDS with an S3 storage backend, the
backup story is **bucket versioning + cross-region replication**
— same posture as
[storage backends §S3-compatible]({{ '/reference/deployment-storage/#backup-posture-for-s3-compatible' | relative_url }}).

### HSDS-side export (for migration / verification)

```bash
docker exec shepard-hsds hsadmin -u $HSDS_USERNAME -p $HSDS_PASSWORD \
  export /shepard/<container-domain>/ \
  --output /backups/hsds-<container>.h5
```

`hsadmin` reconstructs a byte-identical HDF5 file from the chunk
store — useful for migration or sanity checks. Slow on
multi-TiB containers.

## CredentialCipher key backup

The most important file you back up:

```bash
docker exec shepard-backend tar czf - /home/$BACKEND_USER/.shepard/keys/ \
  | gzip > /backups/shepard-keys-$(date +%F).tar.gz
```

Encrypt the resulting tarball before moving it off-host (the
backup is your IdP-bypass material if it falls into the wrong
hands):

```bash
gpg --symmetric --cipher-algo AES256 /backups/shepard-keys-<date>.tar.gz
shred -u /backups/shepard-keys-<date>.tar.gz
rsync -avP /backups/shepard-keys-<date>.tar.gz.gpg backup-host:/backups/shepard/keys/
```

**Losing this directory means losing every encrypted credential
in Neo4j** (DataCite, ePIC, Unhide harvest keys, git PATs once
G1c lands). There's no recovery path — re-provision each
credential through its plugin's admin endpoint.

See [secrets management]({{ '/reference/deployment-secrets/' | relative_url }})
for the credential model.

## Coordinated backup (point-in-time consistency)

Each DB backs up independently — but a complete shepard backup
should reflect a **single point in time**. The coordination
story:

### Option 1: Quiesce shepard (recommended for small/medium scale)

```bash
# 1. Stop the backend so no new writes land.
docker compose stop backend

# 2. Run each DB backup serially (they're independent).
./backup-neo4j.sh
./backup-mongo.sh
./backup-timescale.sh
./backup-postgis.sh   # only if spatial profile is active
./backup-hsds.sh      # only if hdf profile is active

# 3. Back up keys.
./backup-keys.sh

# 4. Resume.
docker compose start backend
```

This is the **simplest** path. Downtime is ~10 minutes for a
small-lab-scale shepard. For 24/7 deploys, see Option 2.

### Option 2: Replica-set + WAL archive (medium / large scale)

- MongoDB: replica set, backup from delayed secondary.
- Postgres / TimescaleDB: continuous WAL archive, point-in-time
  recovery to any timestamp.
- Neo4j: causal-cluster read replica (Enterprise).

These let you back up without downtime, at the cost of cluster
complexity. The recipes live in each backend's upstream docs.

## Tested restore — the rule

**Untested backups are not backups.** Test the restore path
**once a quarter** against a scratch host:

1. Spin up a fresh shepard stack (`docker compose up`) on a
   throwaway host or VM.
2. Restore each DB from yesterday's backup.
3. Restore the keys directory.
4. Start the backend.
5. Open the frontend; log in; navigate to a known Collection;
   open a known DataObject; download a file.
6. Verify the Activity log shows pre-restore entries.

If any of (5) fails, the backup is silently broken — fix the
backup script.

## Backup-script template

A starter `backup.sh` an operator can drop into cron:

```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_ROOT=/opt/shepard/backups
DATE=$(date +%F)
mkdir -p "$BACKUP_ROOT/$DATE"

# 1. Neo4j
docker exec neo4j neo4j-admin database dump neo4j \
  --to-path=/data/backups
mv /opt/shepard/neo4j/data/backups/*.dump "$BACKUP_ROOT/$DATE/"

# 2. MongoDB
docker exec mongo mongodump \
  --uri="mongodb://$MONGO_USERNAME:$MONGO_PASSWORD@localhost/$MONGO_DATABASE" \
  --out="/data/backups/$DATE"
mv "/opt/shepard/mongodb/backups/$DATE" "$BACKUP_ROOT/$DATE/mongo"

# 3. TimescaleDB
docker exec timescaledb pg_dump \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc \
  -f "/var/lib/postgresql/backups/timescale-$DATE.dump"
mv "/opt/shepard/timescaledb/backups/timescale-$DATE.dump" \
   "$BACKUP_ROOT/$DATE/"

# 4. Keys
docker exec shepard-backend tar czf - /home/*/.shepard/keys/ \
  > "$BACKUP_ROOT/$DATE/keys.tar.gz"

# 5. Encrypt + ship off-host
tar czf - "$BACKUP_ROOT/$DATE" | \
  gpg --symmetric --cipher-algo AES256 \
  > "$BACKUP_ROOT/$DATE.tar.gz.gpg"
rsync -avP "$BACKUP_ROOT/$DATE.tar.gz.gpg" \
  backup-host:/backups/shepard/

# 6. Retention — keep last 14 days locally
find "$BACKUP_ROOT" -maxdepth 1 -mtime +14 -exec rm -rf {} \;
```

Schedule via cron at the lowest-traffic hour:

```cron
# Daily backup at 04:13 UTC
13 4 * * * /opt/shepard/scripts/backup.sh >> /var/log/shepard-backup.log 2>&1
```

## What to monitor

- Backup script exit code (alert on non-zero).
- Backup file size (alert on a sudden drop — that's a silent
  backup failure).
- Time-since-last-successful-restore (alert if > 90 days).

See [monitoring]({{ '/reference/deployment-monitoring/' | relative_url }})
for the Prometheus pattern.

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [Storage backends]({{ '/reference/deployment-storage/' | relative_url }}) — GridFS vs S3-compatible backup posture.
- [Secrets management]({{ '/reference/deployment-secrets/' | relative_url }}) — why the keys directory matters.
- [Monitoring + observability]({{ '/reference/deployment-monitoring/' | relative_url }})
- [Upgrade path]({{ '/reference/deployment-upgrade/' | relative_url }}) — back up before any upgrade.
- [Troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }})
- Neo4j docs — [`neo4j-admin database dump`](https://neo4j.com/docs/operations-manual/current/backup-restore/online-backup/)
- MongoDB docs — [`mongodump`](https://www.mongodb.com/docs/database-tools/mongodump/)
- Timescale docs — [Backup and restore](https://docs.timescale.com/use-timescale/latest/backup-restore/)
