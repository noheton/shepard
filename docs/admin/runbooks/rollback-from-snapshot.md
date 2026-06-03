# Rollback from Snapshot

> **When to use this runbook**: An accidental mutation, partial-import corruption,
> or data-quality incident has affected entities inside a Collection, and a
> Shepard V2 Snapshot taken before the incident is available to roll back from.
> This runbook restores the affected scope to the state recorded in the snapshot,
> in the correct cross-substrate order so referential integrity is maintained.

**Estimated time**: 15–45 minutes depending on scope (Neo4j only vs. all four
substrates). Plan 15 min for a graph-node-only rollback, 45 min when full
timeseries + object storage restore is needed.

---

## Prerequisites

| Requirement | How to verify |
|---|---|
| Snapshot `appId` known | `GET /v2/snapshots/{snapshotAppId}` returns 200 |
| Snapshot within retention window (not soft-deleted) | Same call; `deleted: false` in the response |
| No ongoing mutations on the Collection scope | Pause any import jobs or writing clients before starting |
| Admin credentials available | `${INSTANCE_ADMIN_API_KEY}` from `/opt/shepard/infrastructure/.env` |
| SSH access to nuclide host | User in the `docker` group |
| `${NEO4J_PASSWORD}`, `${TIMESCALEDB_PASSWORD}` | From `/opt/shepard/infrastructure/.env` |
| Compose working directory | `/opt/shepard/infrastructure/` |

---

## What this does

A Shepard Snapshot is an immutable Neo4j record of every `VersionableEntity`
(DataObject, Container, Reference) reachable from a Collection at snapshot time,
with each entity's `appId` and `revision` counter frozen. Snapshot entries never
change after creation — only live entities move forward.

This runbook uses the snapshot manifest as the authoritative list of what should
exist, then walks the four substrates in dependency order — Neo4j graph nodes
first (because TimescaleDB containers and Garage file references are foreign-keyed
to Neo4j `appId` values), then TimescaleDB rows, then MongoDB documents, then
Garage/S3 objects — restoring any missing or corrupted data at each layer.

**Scope caveat**: Snapshots record the graph topology and revision counters; they
do not snapshot payload bytes. Payload data (timeseries rows, GridFS blobs, S3
objects) is immutable by construction (append-only TimescaleDB chunks, MongoDB
OID-addressed GridFS objects, Garage content-addressed blocks). The restore
procedure below addresses the case where those immutable objects were accidentally
deleted after the snapshot was taken. If payload bytes were never corrupted, only
Phase 2 (Neo4j graph) needs to run.

---

## Cross-substrate restore order

```
Phase 2  Neo4j graph nodes       ← FIRST (other substrates reference appIds here)
Phase 3  TimescaleDB rows        ← SECOND (hypertables keyed to TimeseriesContainer appId)
Phase 4  MongoDB documents       ← THIRD (GridFS fileIds referenced by FileReference appId)
Phase 5  Garage / S3 objects     ← LAST (object keys keyed to FileReference oid)
```

Always follow this order. Restoring Garage objects before the Neo4j
`:FileReference` nodes exist makes those objects permanently unreachable via the
API (no reference node → no download path). Restoring TimescaleDB hypertable rows
before the `:TimeseriesContainer` shadow node is repaired causes the backend to
return 404 for valid data.

---

## Phase 1 — Identify the snapshot

### Step 1 — Verify the snapshot exists and note its metadata

```bash
# [operator-machine or nuclide]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/snapshots/${SNAPSHOT_APP_ID}" \
  | jq '{appId, name, snapshotCapturedAt: (.snapshotCapturedAtMs / 1000 | todate),
         snapshotCreatedByUsername, collectionAppId, entryCount, deleted}'
```

Expected output — note these values for later verification:
```json
{
  "appId": "01900000-0000-7000-8000-000000000020",
  "name": "v1.0 — campaign close",
  "snapshotCapturedAt": "2026-05-17T14:00:00Z",
  "snapshotCreatedByUsername": "alice",
  "collectionAppId": "01900000-0000-7000-8000-000000000010",
  "entryCount": 42,
  "deleted": false
}
```

If `deleted: true`, the snapshot is outside the retention window. Stop here and
contact the data owner — the snapshot manifest is gone and a full-substrate
restore (runbooks 04–06) is the only path.

### Step 2 — Pull the full manifest

```bash
# [operator-machine or nuclide]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/snapshots/${SNAPSHOT_APP_ID}/manifest" \
  | jq '.'
```

Expected: an array of `{entityAppId, revision}` pairs, one per captured entity.
Save the output for cross-checking in later phases:

```bash
# [operator-machine or nuclide]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/snapshots/${SNAPSHOT_APP_ID}/manifest" \
  > /tmp/snapshot-manifest.json

echo "Manifest entries: $(jq length /tmp/snapshot-manifest.json)"
```

The count here is the target you must match in the end-state verification.

### Step 3 — Identify the DataObjects specifically (V2c endpoint)

```bash
# [operator-machine or nuclide]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/collections/${COLLECTION_APP_ID}/snapshots/${SNAPSHOT_APP_ID}/data-objects" \
  | jq '{totalEntries, dataObjectCount: (.dataObjectAppIds | length), dataObjectAppIds}'
```

This tells you how many DataObjects should be present. Use `totalEntries` as the
authoritative entry count for Phase 6 verification.

---

## Phase 2 — Neo4j graph restore (FIRST)

All other substrates reference Neo4j `appId` values. Graph nodes must be intact
before any foreign-substrate restore.

### Step 4 — Stop the backend (prevent writes during restore)

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend
echo "Backend stopped."
```

### Step 5 — Identify missing or soft-deleted Neo4j entities

For each `entityAppId` in the manifest, verify the Neo4j node still exists and
is not soft-deleted:

```bash
# [nuclide]
# Extract all entityAppIds from the manifest into a Cypher list
ENTITY_IDS=$(jq -r '[.[].entityAppId] | @json' /tmp/snapshot-manifest.json)

docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "WITH ${ENTITY_IDS} AS ids
   MATCH (e) WHERE e.appId IN ids
   RETURN e.appId AS entityAppId,
          labels(e)[0] AS kind,
          coalesce(e.deleted, false) AS deleted
   ORDER BY entityAppId;"
```

Compare the row count against `jq length /tmp/snapshot-manifest.json`.
Entities present in the manifest but absent from the query output are the ones
that need restoring.

### Step 6 — Restore missing graph nodes from a Neo4j dump (if any)

If nodes were hard-deleted (not soft-deleted), restore them from the Neo4j backup
dump that pre-dates the incident. Follow **runbook 04** (`04-restore-neo4j.md`)
for the full stop/load/restart cycle, scoped to the affected snapshot time:

```bash
# [nuclide] — load a dump taken at or before the snapshot capturedAt
# (see runbook 04 for full procedure; this shows the targeted load command)
docker compose run --rm neo4j \
  neo4j-admin database load neo4j \
  --from-path=/data/dumps/<pre-incident-dump>.dump \
  --overwrite-destination=true
```

If only soft-deletion happened (entities present but `deleted: true`), you can
un-delete them with a targeted Cypher mutation instead of a full dump restore:

```bash
# [nuclide]
# Un-delete a specific entity by appId (repeat for each affected appId)
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e {appId: '<entityAppId>'})
   SET e.deleted = false
   RETURN e.appId, labels(e)[0] AS kind, e.deleted;"
```

Expected: `deleted: false` in the output for each restored entity.

### Step 7 — Verify the snapshot graph topology

After any graph repairs, confirm that the `SnapshotEntry` nodes can be traversed:

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e:SnapshotEntry)-[:ENTRY_OF]->(s:Snapshot {appId: '${SNAPSHOT_APP_ID}'})
   RETURN count(e) AS entryCount;"
```

Expected: `entryCount` equals the `entryCount` field from Phase 1, Step 1.

---

## Phase 3 — TimescaleDB restore (SECOND)

Run this phase only if timeseries data (rows in TimescaleDB hypertables) was
lost or corrupted after the snapshot. If timeseries data is intact, skip to
Phase 4.

### Step 8 — Identify affected TimeseriesContainers

```bash
# [nuclide]
# List all TimeseriesContainer appIds in the snapshot
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e:TimeseriesContainer)-[:ENTRY_OF|SNAPSHOT_OF*0..2]-(s:Snapshot {appId: '${SNAPSHOT_APP_ID}'})
   RETURN e.appId AS containerAppId;" \
  --format plain | tail -n +2
```

### Step 9 — Verify row counts match pre-snapshot state

For each `containerAppId`, check how many timeseries data points exist versus
what the snapshot would expect:

```bash
# [nuclide]
# Substitute the actual container appId for <containerAppId>
docker exec infrastructure-timescaledb-1 \
  psql -U shepard -d shepard -c \
  "SELECT COUNT(*) AS row_count
   FROM timeseries_data_points
   WHERE timeseries_id IN (
     SELECT id FROM timeseries
     WHERE container_id = '<containerAppId>'
   );"
```

Compare against the pre-incident backup or a known-good count. If rows are
missing, proceed to Step 10.

### Step 10 — Restore TimescaleDB rows from backup

Follow **runbook 05** (`05-restore-timescaledb.md`) for the full
PgBouncer-pause/pg_restore/resume cycle. For a targeted single-hypertable
restore from a custom-format dump:

```bash
# [nuclide]
docker exec infrastructure-timescaledb-1 \
  pg_restore -U shepard -d shepard \
  --table=timeseries_data_points \
  --no-owner --no-privileges \
  --exit-on-error \
  /tmp/<pre-incident-dump>
echo "Restore exit code: $?"
```

Re-run Step 9 to confirm the count matches the expected value.

---

## Phase 4 — MongoDB restore (THIRD)

Run this phase only if MongoDB GridFS documents (file blobs stored in
`_shepard_files`) were lost after the snapshot. If file content is intact, skip
to Phase 5.

### Step 11 — Identify affected FileReferences

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e:FileReference)-[:ENTRY_OF|SNAPSHOT_OF*0..2]-(s:Snapshot {appId: '${SNAPSHOT_APP_ID}'})
   WHERE e.mongoOid IS NOT NULL
   RETURN e.appId AS fileReferenceAppId, e.mongoOid AS mongoOid;" \
  --format plain | tail -n +2
```

### Step 12 — Verify GridFS document count

```bash
# [nuclide]
docker exec infrastructure-mongo-1 \
  mongosh shepard --quiet --eval \
  'db.getCollection("_shepard_files.files").countDocuments({}) + " GridFS file documents"'
```

If documents are missing, restore from a MongoDB backup using `mongorestore`:

```bash
# [nuclide]
docker exec infrastructure-mongo-1 \
  mongorestore \
  --uri "mongodb://shepard:${MONGO_PASSWORD}@localhost:27017/shepard" \
  --nsInclude "shepard._shepard_files.*" \
  --drop \
  /tmp/<pre-incident-mongodump>/
echo "mongorestore exit code: $?"
```

Re-run Step 12 to confirm counts. Cross-check that every `mongoOid` from
Step 11 resolves in GridFS:

```bash
# [nuclide]
# Replace <oid> with each mongoOid from Step 11
docker exec infrastructure-mongo-1 \
  mongosh shepard --quiet --eval \
  'db.getCollection("_shepard_files.files").findOne({_id: ObjectId("<oid>")}, {_id:1, filename:1, length:1})'
```

Expected: a document is returned for each OID.

---

## Phase 5 — Garage/S3 restore (LAST)

Run this phase only if Garage object blocks were deleted after the snapshot. If
object storage is intact, skip to Phase 6.

### Step 13 — Identify affected object keys from Neo4j FileReferences

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e:FileReference)-[:ENTRY_OF|SNAPSHOT_OF*0..2]-(s:Snapshot {appId: '${SNAPSHOT_APP_ID}'})
   WHERE e.oid IS NOT NULL
   RETURN e.appId AS fileReferenceAppId, e.oid AS garageKey;" \
  --format plain | tail -n +2
```

### Step 14 — Verify object presence in Garage

```bash
# [nuclide]
# List all objects in the shepard-files bucket and compare against the expected keys
docker exec shepard-garage \
  garage -c /etc/garage.toml s3api list-objects-v2 \
  --bucket shepard-files --output json \
  | jq '.KeyCount'
```

For a targeted single-object check:

```bash
# [nuclide]
# Replace <garageKey> with the oid from Step 13
docker exec shepard-garage \
  garage -c /etc/garage.toml s3api head-object \
  --bucket shepard-files \
  --key "<garageKey>" 2>&1
```

Expected: `"ContentLength"` and `"ContentType"` present in output. A `NoSuchKey`
error means the object must be restored.

### Step 15 — Re-upload missing objects

Follow **runbook 06** (`06-restore-garage.md`), Section B (object-level
re-upload), targeting only the missing keys identified in Step 13.

Using rclone from a cold-storage backup:

```bash
# [operator-machine or nuclide]
rclone copy \
  <cold-storage-remote>:shepard-files-backup/<garageKey> \
  :s3,endpoint=http://garage:3900,access_key_id=${GARAGE_KEY},secret_access_key=${GARAGE_SECRET}:shepard-files/<garageKey> \
  --s3-path-style \
  --progress
```

Re-run Step 14 to confirm each key is present.

---

## Phase 6 — Restart and end-state verification

### Step 16 — Restart the backend

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
```

Wait for readiness:

```bash
# [nuclide]
until curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq -e '.status == "UP"' > /dev/null 2>&1; do
  echo "Waiting for backend readiness…"; sleep 5
done
echo "Backend UP."
```

### Step 17 — Confirm snapshot manifest entry count

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e:SnapshotEntry)-[:ENTRY_OF]->(s:Snapshot {appId: '${SNAPSHOT_APP_ID}'})
   RETURN count(e) AS restoredEntryCount;"
```

Expected: `restoredEntryCount` equals the `entryCount` from Phase 1, Step 1.

### Step 18 — Confirm DataObject count via the API

```bash
# [operator-machine or nuclide]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/collections/${COLLECTION_APP_ID}/snapshots/${SNAPSHOT_APP_ID}/data-objects" \
  | jq '{totalEntries, dataObjectCount: (.dataObjectAppIds | length)}'
```

Expected: `dataObjectCount` matches the count from Phase 1, Step 3.

### Step 19 — Healthcheck

```bash
# [nuclide]
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'
```

Expected: all checks `"UP"`.

### Step 20 — Spot-check a specific DataObject

Pick one representative `entityAppId` from the manifest and verify it is readable:

```bash
# [operator-machine or nuclide]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/dataObjects/<entityAppId>" \
  | jq '{appId, name, status}'
```

Expected: 200 response with the correct `appId`.

---

## If restore fails mid-way

**Do not attempt a partial restore.** A half-restored state (e.g. Neo4j graph
repaired but TimescaleDB rows still missing) is better than a partially-applied
fix that leaves the system in an inconsistent state. If any phase fails:

1. Do not restart the backend — leave it stopped (Step 4) to prevent additional
   writes to the partially-restored state.
2. Capture the error output and the step number.
3. Open a GitHub issue with:
   - Snapshot `appId`
   - The failing step number from this runbook
   - The full error output from that step
4. For substrate-level recovery, follow the full-instance runbooks:
   - Neo4j: `docs/admin/runbooks/04-restore-neo4j.md`
   - TimescaleDB: `docs/admin/runbooks/05-restore-timescaledb.md`
   - Garage/S3: `docs/admin/runbooks/06-restore-garage.md`

The full-instance runbooks are the fallback when a scoped rollback cannot be
completed cleanly. They replace the entire substrate from a dump rather than
attempting a targeted repair.

---

## Provenance

- Snapshot system design: `aidocs/data/41-snapshots-design.md`.
- Snapshot user reference: `docs/reference/snapshots.md`.
- Neo4j full restore: `docs/admin/runbooks/04-restore-neo4j.md`.
- TimescaleDB full restore: `docs/admin/runbooks/05-restore-timescaledb.md`.
- Garage S3 full restore: `docs/admin/runbooks/06-restore-garage.md`.
- TS shadow repair: `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md`.
- Tracked: `PRE-MUT-SNAP4` in `aidocs/16-dispatcher-backlog.md`.
