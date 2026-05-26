---
layout: default
title: "Runbook — Restore Neo4j from a backup dump"
description: "Stop the backend, load a Neo4j database dump into the running container, restart, and verify migration chain integrity. Covers the coordinated-snapshot pattern required when multiple substrates need point-in-time consistency."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Restore Neo4j from a backup dump

> **When to use this runbook**: The Neo4j graph database needs to be restored from
> a known-good dump — e.g. after data corruption, a failed cleanup cascade, or a
> hardware failure. This runbook covers the case where a `neo4j-admin database dump`
> artefact is available.

**WARNING**: This is a **destructive** operation. The current Neo4j data is
overwritten. Ensure you understand the blast radius before proceeding:
- TimescaleDB rows and Garage S3 objects are NOT restored here — they stay as-is.
- After restore, the Neo4j shadow nodes for TS containers and File containers must
  match what is in those substrates. If the dump pre-dates recent TS writes, you
  may need to re-run `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md`
  for any containers created after the dump date.

---

## Prerequisites

- A backup dump file: `/opt/shepard/neo4j/dumps/<dumpfile>.dump` (or available
  on an accessible path; will be copied into the container volume).
- SSH access to the nuclide host; user in the `docker` group.
- `${NEO4J_PASSWORD}` available (from `/opt/shepard/infrastructure/.env`).
- Compose working directory: `/opt/shepard/infrastructure/`.
- `make` available for rebuilding and redeploying the backend after restore.

---

## Steps

### 0. Record the current state

```bash
# [nuclide]
echo "Dump file to restore:"
ls -lh /opt/shepard/neo4j/dumps/<dumpfile>.dump

echo "Current Neo4j migration chain:"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (m:__Neo4jMigration) WHERE m.version IS NOT NULL
   RETURN m.version AS v, m.installedOn AS ts ORDER BY toInteger(v) DESC LIMIT 5;"
```

### 1. Stop the backend (prevent writes during restore)

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose stop backend
echo "Backend stopped."
```

### 2. Stop Neo4j

Neo4j must be stopped before `neo4j-admin database load` runs — the database
cannot be loaded while it is online.

```bash
# [nuclide]
docker compose stop neo4j
echo "Neo4j stopped."
```

### 3. Verify the dump file is accessible inside the container volume

Neo4j Admin tools read from paths **inside** the container. The dumps directory
`/opt/shepard/neo4j/dumps/` is bind-mounted to `/data/dumps/` inside the container
(see `docker-compose.yml` volumes). Confirm:

```bash
# [nuclide]
ls -lh /opt/shepard/neo4j/dumps/<dumpfile>.dump
# Corresponds to /data/dumps/<dumpfile>.dump inside the container
```

### 4. Load the dump

```bash
# [nuclide]
docker compose run --rm neo4j \
  neo4j-admin database load neo4j \
  --from-path=/data/dumps/<dumpfile>.dump \
  --overwrite-destination=true
```

Expected output:
```
Done: X files, Y MiB processed.
```

If you see `ERROR: Failed to load the specified database` with "database in use",
the stop in step 2 did not complete cleanly. Verify `docker ps` shows no running
Neo4j container before retrying.

### 5. Restart Neo4j

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d neo4j
```

Wait for Neo4j to become available (bolt protocol ready):

```bash
# [nuclide]
until docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "RETURN 1;" > /dev/null 2>&1; do
  echo "Waiting for Neo4j…"; sleep 5
done
echo "Neo4j is up."
```

### 6. Restart the backend (triggers migration apply)

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose up -d backend
```

Watch the startup logs for migration activity:

```bash
# [nuclide]
docker compose logs -f --tail 50 backend 2>&1 | head -100
```

Expected: `MigrationsRunner: applying migration V…` lines, then `Quarkus started`.

If startup fails with `MigrationsException: Aborting startup`, consult
`docs/admin/runbooks/migration-chain-integrity.md` §1.

### 7. Verify readiness

```bash
# [nuclide]
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'
```

Expected: all checks `"UP"`.

### 8. Spot-check data integrity

```bash
# [nuclide]
# Count top-level entities in the restored graph
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (n) WHERE n:Collection OR n:DataObject OR n:TimeseriesContainer OR n:FileContainer
   RETURN labels(n)[0] AS type, count(n) AS cnt ORDER BY type;"
```

Compare counts against your pre-restore inventory. If TS containers are present in
this graph but their hypertables are in a newer state, see the "Cross-substrate
consistency note" below.

---

## Cross-substrate consistency note

If the dump pre-dates TimescaleDB or Garage writes that happened after the backup:
- New TS containers (created after the dump) will be missing in Neo4j → use
  `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md` to re-create their
  shadow nodes.
- New File containers / references (created after the dump) will be missing in Neo4j
  → those files become unreachable via the API but remain in Garage; contact the
  data owner to re-register.

---

## Rollback

If the restore produces an unusable state (e.g. the dump file itself is corrupted),
the path is: stop Neo4j again → load an older known-good dump → restart backend.

There is no automated undo for a `database load --overwrite-destination`. Always
snapshot the *current* state before step 2 if it is in any way recoverable:

```bash
# [nuclide] — run BEFORE step 2 if current state has value
docker exec infrastructure-neo4j-1 \
  neo4j-admin database dump neo4j \
  --to-path=/data/dumps/pre-restore-$(date +%Y%m%d-%H%M%S).dump
```

---

## End-state verification

```bash
# [nuclide]
curl -fsS http://localhost:8080/shepard/api/healthz/ready \
  | jq '.checks[] | select(.name == "neo4j-migration-chain-readiness") | {status, data}'
```

Expected: `"status": "UP"`, `"outcome": "VALID"`.

```bash
# Neo4j chain length matches classpath
CHAIN=$(docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (m:__Neo4jMigration) WHERE m.version IS NOT NULL RETURN count(m) AS n;" \
  --format plain | tail -1)
CLASSPATH=$(find /opt/shepard/backend/src/main/resources/neo4j/migrations \
  -name 'V*.cypher' -not -name '*_R__*' | wc -l)
echo "DB chain: ${CHAIN}, classpath: ${CLASSPATH}"
```

Expected: `DB chain` == `classpath` (±1 for the BASELINE node).

---

## Provenance

- Backup recipe: `docs/admin/backup.md` §Neo4j.
- Migration-chain integrity: `docs/admin/runbooks/migration-chain-integrity.md`.
- TS shadow repair: `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md`.
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
