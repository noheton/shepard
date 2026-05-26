---
layout: default
title: "Runbook — Find and wipe orphaned Garage S3 objects"
description: "Scan Garage S3 buckets for objects that have no matching Neo4j FileReference node (orphans). Dry-run by default; wipe only after operator confirmation."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Find and wipe orphaned Garage S3 objects

> **When to use this runbook**: A cleanup cascade, a failed import, or a direct-delete
> API call left objects in Garage S3 with no corresponding `:FileReference` node in
> Neo4j. The objects consume storage but are unreachable via the API. This runbook
> finds them (dry-run) and optionally deletes them.

**INTEGRITY rule** (`feedback_referenced_data_infinite_retention.md`): only objects
that have **no** Neo4j `:FileReference` pointing at them are eligible for deletion.
Do not delete objects whose Neo4j node was soft-deleted (`deleted: true`) but not
purged — those are still retention-eligible.

---

## Prerequisites

- SSH access to the host running the Garage container and Neo4j container.
- `${NEO4J_PASSWORD}` and `${GARAGE_ADMIN_TOKEN}` available (source from
  `/opt/shepard/infrastructure/.env`).
- Garage admin endpoint reachable at `http://localhost:3903` from the host
  (mapped from container port 3903 — see `docker-compose.override.yml`).
- `jq`, `curl`, `python3` available on the host.
- Take a Neo4j dump before running the wipe phase (step 4 below).

---

## Steps

### 0. Collect all object keys known to Neo4j

Dump every `oid` stored in `:FileReference` nodes — these are the S3 object keys
that are **in use**.

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (r:FileReference) WHERE r.oid IS NOT NULL RETURN r.oid AS oid;" \
  --format plain \
  | tail -n +2 \
  > /tmp/neo4j-oids.txt

wc -l /tmp/neo4j-oids.txt
```

Expected: one OID per line; count should match the number of stored file references.

### 1. Collect all object keys present in Garage

List every object in the `shepard-files` bucket (adjust bucket name if different):

```bash
# [nuclide]
BUCKET="shepard-files"

# Use the Garage admin API to list objects via the S3-compatible interface.
# The Garage container_name is shepard-garage; admin port 3903; S3 port 3900.
docker exec shepard-garage \
  garage -c /etc/garage.toml s3api list-objects-v2 \
  --bucket "${BUCKET}" \
  --output json \
  | jq -r '.Contents[].Key' \
  > /tmp/garage-keys.txt

wc -l /tmp/garage-keys.txt
```

Expected: list of S3 object keys (typically UUID-like strings).

> **Note**: if the bucket contains > 1000 objects, `list-objects-v2` will paginate.
> Loop with `--starting-after` or use the `--max-keys` + continuation-token pattern.
> For a full audit use the snippet in §"Large-bucket pagination" below.

### 2. Compute the orphan set (dry-run)

```bash
# [nuclide]
# Objects in Garage that are NOT in Neo4j
sort /tmp/garage-keys.txt > /tmp/garage-keys-sorted.txt
sort /tmp/neo4j-oids.txt  > /tmp/neo4j-oids-sorted.txt

comm -23 /tmp/garage-keys-sorted.txt /tmp/neo4j-oids-sorted.txt \
  > /tmp/orphan-keys.txt

echo "Orphaned objects: $(wc -l < /tmp/orphan-keys.txt)"
echo "Garage total:     $(wc -l < /tmp/garage-keys-sorted.txt)"
echo "Neo4j referenced: $(wc -l < /tmp/neo4j-oids-sorted.txt)"
```

Expected: `Orphaned objects: N` where N ≥ 0. If N = 0, stop — no action required.

Review a sample of the orphan list before proceeding:

```bash
head -20 /tmp/orphan-keys.txt
```

### 3. Snapshot Neo4j before any deletion

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  neo4j-admin database dump neo4j \
  --to-path=/data/dumps/pre-orphan-wipe-$(date +%Y%m%d-%H%M%S).dump
echo "Snapshot complete"
```

### 4. Wipe orphaned objects (DESTRUCTIVE — confirm first)

```bash
# [nuclide]
# Review the count one more time
echo "About to delete $(wc -l < /tmp/orphan-keys.txt) objects from Garage."
echo "Press ENTER to continue or Ctrl-C to abort."
read -r _

BUCKET="shepard-files"
while IFS= read -r key; do
  docker exec shepard-garage \
    garage -c /etc/garage.toml s3api delete-object \
    --bucket "${BUCKET}" \
    --key "${key}"
  echo "Deleted: ${key}"
done < /tmp/orphan-keys.txt
echo "Wipe complete."
```

### 5. Verify

```bash
# [nuclide]
# Re-list and re-diff to confirm orphans are gone
docker exec shepard-garage \
  garage -c /etc/garage.toml s3api list-objects-v2 \
  --bucket "${BUCKET}" \
  --output json \
  | jq -r '.Contents[].Key' \
  | sort > /tmp/garage-keys-post.txt

comm -23 /tmp/garage-keys-post.txt /tmp/neo4j-oids-sorted.txt \
  > /tmp/orphan-keys-post.txt

echo "Remaining orphans: $(wc -l < /tmp/orphan-keys-post.txt)"
```

Expected: `Remaining orphans: 0`

---

## Large-bucket pagination

For buckets with > 1000 objects, paginate fully before computing the diff:

```bash
# [nuclide]
BUCKET="shepard-files"
> /tmp/garage-keys.txt
CONTINUATION=""
while true; do
  ARGS="--bucket ${BUCKET} --output json"
  if [ -n "${CONTINUATION}" ]; then
    ARGS="${ARGS} --continuation-token ${CONTINUATION}"
  fi
  RESPONSE=$(docker exec shepard-garage \
    garage -c /etc/garage.toml s3api list-objects-v2 ${ARGS})
  echo "${RESPONSE}" | jq -r '.Contents[].Key' >> /tmp/garage-keys.txt
  TRUNCATED=$(echo "${RESPONSE}" | jq -r '.IsTruncated')
  if [ "${TRUNCATED}" = "false" ]; then break; fi
  CONTINUATION=$(echo "${RESPONSE}" | jq -r '.NextContinuationToken')
done
echo "Total objects enumerated: $(wc -l < /tmp/garage-keys.txt)"
```

---

## Rollback

Object deletion in Garage is **permanent** — Garage does not implement versioning
or soft-delete. There is no rollback once an object is deleted.

Mitigation: if you deleted an object that turns out to have a Neo4j reference
(e.g. the dump in step 0 was stale), the affected `FileReference` node will now
point to a missing object. The API will return 404 for that payload. Repair path:
- Locate the original file on your source system or backup.
- Re-upload it via `PUT /v2/fileContainers/{id}/payload` (or the equivalent
  direct-to-S3 presigned URL).
- Update the `oid` on the `:FileReference` node if the new upload gets a different
  object key.

---

## End-state verification

```bash
# [nuclide]
echo "Garage post-wipe object count:"
docker exec shepard-garage \
  garage -c /etc/garage.toml s3api list-objects-v2 \
  --bucket "${BUCKET}" --output json \
  | jq '.KeyCount'

echo "Neo4j FileReference count:"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (r:FileReference) WHERE r.oid IS NOT NULL RETURN count(r) AS n;" \
  --format plain | tail -1
```

The two numbers should now agree (Garage count ≥ Neo4j count; ≤ Neo4j count is
a sign that references exist without backing objects — investigate separately).

---

## Provenance

- Triggered by: `project_db_audit_findings_2026-05-23.md` (SD wipe + orphan cleanup cascade).
- Integrity rule: `feedback_referenced_data_infinite_retention.md`.
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
