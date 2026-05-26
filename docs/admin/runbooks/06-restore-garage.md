---
layout: default
title: "Runbook — Restore Garage S3 from a snapshot"
description: "Recover a Garage S3 bucket from a filesystem-level snapshot or object-level copy. Covers the bucket re-creation workflow, key bootstrap, and integrity checks against Neo4j FileReference nodes."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Restore Garage S3 from a snapshot

> **When to use this runbook**: Garage S3 data needs to be recovered after storage
> corruption, accidental object deletion, or a filesystem-level failure. Garage
> (v1.0.1) does not implement versioning or soft-delete natively — recovery is
> at the filesystem or object-copy level.

**Garage architecture reminder** (`docs/ops/garage-activation-runbook.md`):
- Garage stores data in named Docker volumes: `garage_data` (object blocks) and
  `garage_meta` (metadata RocksDB).
- The Garage container name is `shepard-garage`; admin port 3903; S3 port 3900.
- S3-compatible endpoint used by the backend: `http://garage:3900` (internal Docker network).
- Bucket name for Shepard file payloads: `shepard-files` (verify with `garage bucket list`).
- **Known trap**: always run `garage layout assign` → `garage layout apply` BEFORE
  creating buckets. Buckets created on an unassigned layout do not store data.
- **Known trap**: use path-style addressing (`http://garage:3900/shepard-files/key`)
  not virtual-host style; configure `GARAGE_S3_PATH_STYLE=true` in the backend.

---

## Prerequisites

- SSH access to the nuclide host; user in the `docker` group.
- `${GARAGE_ADMIN_TOKEN}` available (from `/opt/shepard/infrastructure/.env`; the
  token is shown once during Garage bootstrap — see
  `docs/ops/garage-activation-runbook.md §3`).
- A filesystem-level backup of the `garage_data` and `garage_meta` Docker volumes,
  OR a bucket-level object copy (e.g. rclone sync from a cold storage target).
- Compose working directory: `/opt/shepard/infrastructure/`.

---

## Steps

### 0. Determine recovery method

| Scenario | Recovery method | Section |
|---|---|---|
| Both `garage_data` + `garage_meta` volumes backed up (e.g. via `docker run --rm -v garage_data:/data busybox tar`) | Volume-level restore | §A |
| Only objects backed up (rclone, aws s3 sync, etc.) | Object-level re-upload | §B |
| Individual objects missing but layout intact | Object re-upload | §B (partial) |

---

## Section A — Volume-level restore

### A1. Stop services that touch Garage

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend
docker compose stop garage
echo "Backend and Garage stopped."
```

### A2. Restore `garage_data` and `garage_meta` volumes

Replace the volume contents with the backup tarball:

```bash
# [nuclide]
# Replace <backup-path> with the actual tarball paths
docker run --rm \
  -v garage_data:/data \
  -v /opt/shepard/backups:/backup \
  busybox sh -c "cd /data && rm -rf * && tar xf /backup/garage_data-<timestamp>.tar"

docker run --rm \
  -v garage_meta:/meta \
  -v /opt/shepard/backups:/backup \
  busybox sh -c "cd /meta && rm -rf * && tar xf /backup/garage_meta-<timestamp>.tar"

echo "Volumes restored."
```

### A3. Restart Garage

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d garage
```

Wait for Garage to start:

```bash
# [nuclide]
until docker exec shepard-garage \
  garage -c /etc/garage.toml status > /dev/null 2>&1; do
  echo "Waiting for Garage…"; sleep 3
done
echo "Garage up."
```

### A4. Verify cluster layout

```bash
# [nuclide]
docker exec shepard-garage \
  garage -c /etc/garage.toml layout show
```

Expected: node(s) show as `ACTIVE` with zone and capacity assigned.
If the layout is unassigned (blank), re-run the layout assignment from
`docs/ops/garage-activation-runbook.md §3`.

### A5. Verify bucket and objects

```bash
# [nuclide]
docker exec shepard-garage \
  garage -c /etc/garage.toml bucket list

docker exec shepard-garage \
  garage -c /etc/garage.toml s3api list-objects-v2 \
  --bucket shepard-files --output json | jq '.KeyCount'
```

Expected: `shepard-files` bucket listed; object count matches pre-failure count.

Skip to §"End-state verification".

---

## Section B — Object-level re-upload

Use this path when you have an off-box copy of the objects but not the volume
snapshots (e.g. rclone cold storage, manual download archive).

### B1. Ensure Garage layout is valid

```bash
# [nuclide]
docker exec shepard-garage \
  garage -c /etc/garage.toml layout show
```

If layout is not assigned, follow `docs/ops/garage-activation-runbook.md §3`
before creating or writing to buckets.

### B2. Ensure the bucket exists

```bash
# [nuclide]
docker exec shepard-garage \
  garage -c /etc/garage.toml bucket list | grep shepard-files \
  || docker exec shepard-garage \
       garage -c /etc/garage.toml bucket create shepard-files
```

### B3. Re-upload objects from the off-box copy

Using rclone (adjust remote name and source path):

```bash
# [operator-machine or nuclide]
rclone sync \
  <cold-storage-remote>:shepard-files-backup/ \
  :s3,endpoint=http://garage:3900,access_key_id=${GARAGE_KEY},secret_access_key=${GARAGE_SECRET}:shepard-files/ \
  --s3-path-style \
  --progress
```

Or using `aws s3 sync` with a local directory:

```bash
# [nuclide]
AWS_ACCESS_KEY_ID="${GARAGE_KEY}" \
AWS_SECRET_ACCESS_KEY="${GARAGE_SECRET}" \
aws s3 sync /opt/shepard/backups/garage-objects/ \
  s3://shepard-files \
  --endpoint-url http://localhost:3900 \
  --cli-patchset pathstyle.s3.amazonaws.com
```

### B4. Reconcile with Neo4j

After object re-upload, verify every Neo4j `:FileReference` has a corresponding
object in Garage (the inverse of the orphan-wipe runbook):

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (r:FileReference) WHERE r.oid IS NOT NULL RETURN r.oid AS oid;" \
  --format plain | tail -n +2 | sort > /tmp/neo4j-oids.txt

docker exec shepard-garage \
  garage -c /etc/garage.toml s3api list-objects-v2 \
  --bucket shepard-files --output json \
  | jq -r '.Contents[].Key' | sort > /tmp/garage-keys.txt

# Objects referenced in Neo4j but missing from Garage
comm -23 /tmp/neo4j-oids.txt /tmp/garage-keys.txt > /tmp/missing-objects.txt
echo "Missing objects (referenced but not in Garage): $(wc -l < /tmp/missing-objects.txt)"
```

For each missing object, attempt to source and re-upload from the backup.

---

## Restart backend after either recovery path

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
```

---

## Rollback

If the restoration makes Garage worse (e.g. metadata corruption after a bad volume
paste):

1. Stop Garage: `docker compose stop garage`
2. Wipe both volumes and restore from a different snapshot (repeat §A2).
3. If no good volume snapshot exists: fall back to §B (object-level re-upload from
   off-box copy).

---

## End-state verification

```bash
# [nuclide]
# 1. Garage layout healthy
docker exec shepard-garage \
  garage -c /etc/garage.toml status

# 2. Bucket accessible
docker exec shepard-garage \
  garage -c /etc/garage.toml s3api list-objects-v2 \
  --bucket shepard-files --output json | jq '.KeyCount'

# 3. Backend readiness
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status}'

# 4. File download smoke test — fetch one known object via the API
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/fileContainers/<id>/payload" \
  -o /tmp/smoke-payload.bin \
  && echo "Payload retrieved: $(wc -c < /tmp/smoke-payload.bin) bytes"
```

Expected: all steps succeed with no error output.

---

## Provenance

- Garage activation guide: `docs/ops/garage-activation-runbook.md`.
- Backup recipe: `docs/admin/backup.md` §S3/Garage.
- Orphan-wipe (inverse operation): `docs/admin/runbooks/02-orphan-payload-wipe.md`.
- ADR rejecting MinIO in favour of Garage: `aidocs/ADR-0024` (referenced in
  `project_storage_s3_garage.md`).
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
