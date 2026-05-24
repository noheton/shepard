---
layout: default
title: "Runbook — Restore a TimescaleDB-backed TimeseriesContainer's Neo4j node"
description: "Cleanup-recovery runbook for the case where TimescaleDB still has the rows but Neo4j has lost the :TimeseriesContainer node (or the node exists with NULL id). Reinstates REST/UI visibility without re-ingesting data."
stage: feature-defined
last-stage-change: 2026-05-24
audience: instance-admin
---

# Restore a TimescaleDB-backed `:TimeseriesContainer` Neo4j shadow node

> **When to use this runbook**: TimescaleDB still contains the `cdt_*` rows for one or more container `id`s, but the REST API returns 404 (`Timeseries Container with id N is null or deleted`) and `GET /shepard/api/v2/timeseriesContainers` does not list them. The Neo4j node was either deleted by an over-broad cleanup, or was created via a live-write path that skipped the `id`/`appId` properties.

This is a **recovery** runbook, not a routine. Only run it when:
1. The TSDB rows are intact (`SELECT count(*) FROM cdt_<id>` returns > 0).
2. The Neo4j node is missing or has `id IS NULL`.
3. You know what the container was for (don't restore stranded containers blindly).

The integrity rule from `feedback_referenced_data_infinite_retention.md` is honored here: we never delete TSDB rows; we only repair the Neo4j-side shadow so the data is reachable through the public API again.

---

## 0. Pre-flight

**Container set to restore** — fill in before running. Example for the 2026-05-23 cleanup cascade:

| Container `id` | Name | TSDB hypertable | Notes |
|---|---|---|---|
| `61` | (home environment) | `cdt_61` | live MQTT writes (home-collector) |
| `729` | (home environment) | `cdt_729` | live MQTT writes |
| `732` | (home environment) | `cdt_732` | live MQTT writes; collector currently stopped to silence 404 spam |
| `473928` | (home environment) | `cdt_473928` | live MQTT writes |
| `590324` | (home environment) | `cdt_590324` | live MQTT writes |
| `593750` | (home environment) | `cdt_593750` | live MQTT writes |

Confirm TSDB row counts (HOST: `infrastructure-timescaledb-1`):

```bash
docker exec infrastructure-timescaledb-1 psql -U shepard -d shepard -c \
  "SELECT 'cdt_'||id AS table_name FROM (VALUES (61),(729),(732),(473928),(590324),(593750)) v(id);" \
  > /tmp/tsdb-tables.txt
# Then for each row, run SELECT count(*) — abort if any returns 0
```

Confirm Neo4j shadow state (HOST: `infrastructure-neo4j-1`):

```bash
docker exec infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (c:TimeseriesContainer) WHERE c.id IN [61,729,732,473928,590324,593750] OR c.id IS NULL
   RETURN c.id, c.appId, c.name, c.deleted, labels(c) ORDER BY c.id;"
```

Expected: rows missing, or rows present with `id=NULL` / `appId=NULL`. Anything else (full row with populated `id`+`appId`+permissions) → that one doesn't need this runbook.

---

## 1. Stop the producer (MQTT collector)

If a live writer is still POSTing to the broken container, restore is racey. For the home-energy set, the collector is already stopped:

```bash
docker ps --filter 'name=home-showcase-collector' --format '{{.Names}} {{.Status}}'
# expected empty / Exited
```

If anything shows `Up`, stop it before continuing:

```bash
docker stop infrastructure-home-showcase-collector-1
```

End-state: the producer is verifiably stopped. Restart deferred to step 5.

---

## 2. Mint fresh `appId` UUIDs (UUID v7)

Shepard uses UUID v7 (time-ordered) for `appId`. Generate one per container being restored:

```bash
# Linux/bash with a recent uuidgen — falls back to Python's uuid7 if unavailable
python3 - <<'PY'
import uuid, secrets, time
def uuid7():
    ts_ms = int(time.time() * 1000)
    ver = 0x7000
    var = 0x8000
    rand_a = secrets.randbits(12)
    rand_b = secrets.randbits(62)
    n = (ts_ms << 80) | (ver << 64) | (rand_a << 64) | (var << 48) | rand_b
    # Slice to UUID hex
    return uuid.UUID(int=(ts_ms & 0xFFFFFFFFFFFF) << 80 | 0x7 << 76 |
                          secrets.randbits(12) << 64 | 0x8 << 60 |
                          secrets.randbits(60))
for cid in [61,729,732,473928,590324,593750]:
    print(f"{cid}\t{uuid7()}")
PY
```

Save the output. Each `(id, appId)` pair feeds step 3.

> **Why we mint new appIds rather than recover the original**: the original appIds were deleted with the nodes. The TSDB hypertable doesn't carry the appId (it carries the Neo4j numeric id). Producers that knew the old appId will need to be updated. For the home-collector that's fine — it identifies the container by numeric id only.

---

## 3. Restore the Neo4j shadow node

For each container, run this Cypher (substitute `<ID>`, `<APPID>`, `<NAME>`, `<DB>`). Wrap all restores in a single transaction so a partial failure rolls back:

```cypher
:begin
// container 61
MERGE (c:TimeseriesContainer {id: 61})
  ON CREATE SET c.appId = "<APPID-61>",
                c.name = "<NAME-61>",
                c.database = "shepard",
                c.deleted = false,
                c.createdAt = timestamp(),
                c.updatedAt = timestamp()
  ON MATCH SET c.appId = coalesce(c.appId, "<APPID-61>"),
               c.deleted = false,
               c.updatedAt = timestamp()
MERGE (p:Permissions {ownerType: 'PUBLIC'})  // adjust if you want a real owner
MERGE (c)-[:HAS_PERMISSIONS]->(p);

// repeat for 729, 732, 473928, 590324, 593750
:commit
```

Notes:
- `:TimeseriesContainer` is the load-bearing label — Neo4j-OGM keys the class off it.
- `id`, `appId`, `deleted`, `createdAt`, `updatedAt`, `name`, `database` are all required for REST visibility. `database` defaults to `"shepard"` per the TimeseriesContainer entity.
- `Permissions` node + `HAS_PERMISSIONS` relationship is required by the auth path. If the container should be restricted, replace the PUBLIC permission with a real `(:User)` + permission record.
- `createdBy` + `updatedBy` are nice-to-have but not required for read paths. Add them if you want a clean audit trail (per `project_mffd_dest_backfill_pass.md`, audit backfill is queued for a later pass).

---

## 4. Verify REST visibility

```bash
TOKEN="<a valid JWT>"
for id in 61 729 732 473928 590324 593750; do
  curl -fsS -H "Authorization: Bearer $TOKEN" \
    "https://shepard.nuclide.systems/shepard/api/timeseriesContainers/${id}" \
    | jq '{id, name, database, deleted}' \
    || echo "FAIL: id=$id still 404"
done
```

Expected: each `curl` returns `{"id": N, "name": "...", "database": "shepard", "deleted": false}`. Any `FAIL: ...` line means the node restore was incomplete — re-run step 3 and inspect what's different from a known-healthy container:

```cypher
MATCH (c:TimeseriesContainer) WHERE c.id IS NOT NULL AND c.appId IS NOT NULL
RETURN labels(c) AS lbls, keys(c) AS props LIMIT 3;
```

Diff your restored node against this contract.

---

## 5. Restart the producer

Only after step 4 succeeds:

```bash
docker start infrastructure-home-showcase-collector-1
docker logs --since 60s -f infrastructure-home-showcase-collector-1
```

Expected: log lines show successful `POST /timeseriesContainers/{id}/payload` with `2xx`. If you still see 404s, the collector is targeting an `id` you didn't restore — check the collector's config.

End-state: the collector is `Up` again and writes resolve to `2xx` rather than `404`.

---

## 6. Rollback

If step 3 or 4 misbehaves and you want to abort cleanly:

```cypher
// only safe if you haven't done a separate REST write against these containers
MATCH (c:TimeseriesContainer) WHERE c.id IN [<ids>]
DETACH DELETE c;
```

Then re-run the restore from step 2 with corrected parameters.

**Don't** drop the TSDB hypertables as a "fresh start" — the historical data is the whole point.

---

## Versions / sha256 / commits

- **This runbook** — `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md` (first written 2026-05-24)
- **TimeseriesContainer entity** — `backend/src/main/java/de/dlr/shepard/data/timeseries/model/TimeseriesContainer.java` (extends `BasicContainer` → `BasicEntity` → `AbstractEntity` with `id`/`appId`/`deleted`/`createdAt`/`updatedAt`)
- **TSDB hypertable convention** — `cdt_<id>` (one hypertable per container)
- **Memory references**:
  - `project_db_audit_learnings_2026-05-24.md` Learning 1-3 (why this runbook exists)
  - `project_db_audit_findings_2026-05-23.md` (the original cleanup cascade)
  - `feedback_referenced_data_infinite_retention.md` (the integrity rule honored by step 3)
  - `feedback_admin_runbooks_pattern.md` (the single-page copy-paste contract this runbook follows)

## When to graduate this runbook

Once the upcoming `MFFD-GRAPH-PRUNE` work lands a safer cleanup script (positive-allowlist + snapshot-before-delete per `PRE-MUT-SNAP`), this runbook should still exist as a recovery path but cleanups themselves should never *create* its triggering conditions. Update `stage:` accordingly.
