# Migrating from GridFS to S3 (upstream v5 → this fork)

**Audience:** an operator running upstream shepard 5.2.0 (or an earlier version of this fork) on GridFS, who wants to switch file storage to an S3-compatible backend (Garage, MinIO, AWS S3, LocalStack, …) without downtime or data loss.

**Status of the code path on main:** shipped end-to-end (FS1a SPI + V34 backfill, FS1b S3 adapter, FS1c presigned URLs, FS1d Garage compose profile, FS1e1 big-bang migration + admin REST, FS1e2 continuous auto-sweep, FS1f frontend presigned upload with fallback). See [`aidocs/34-upstream-upgrade-path.md`](../../aidocs/34-upstream-upgrade-path.md) rows FS1a → FS1f for the per-slice ledger.

**Zero-impact upgrade:** if you pull this fork's main and **don't** set `shepard.storage.provider`, you stay on GridFS. The V34 migration runs (one Cypher `SET` stamping `providerId='gridfs'` on every existing `:ShepardFile`); the file-payload REST surface is byte-identical to upstream. **You can ignore this runbook entirely** unless you actively want S3.

---

## When to migrate

| Symptom on your deployment | Migration urgency |
|---|---|
| GridFS data store > 100 GB total | Yes — Mongo's GridFS gets slow at scale; backups grow expensive |
| > 100 k files | Yes — listing latency degrades; deletes accumulate fragmentation |
| Frequent backend memory pressure on uploads (large multipart bodies proxied through Quarkus) | Yes — FS1c presigned URLs bypass the backend; bytes flow direct browser↔S3 |
| Need offsite replication / CDN / lifecycle policies | Yes — S3-compatible stores ship these natively |
| Small (< 10 GB), single-host deploy, no growth expected | No — GridFS is fine; the upgrade is opt-in |

---

## Migration shape

The migration is **incremental + reversible** by design — there is no big-bang switchover, no read freeze, and no point-of-no-return:

```
Stage 0:  shepard.storage.provider unset
          → all files in GridFS, providerId='gridfs' on every :ShepardFile (V34 backfill)

Stage 1:  configure S3 backend, set provider=s3, restart
          → new uploads land in S3 (providerId='s3')
          → existing files still served from GridFS (registry routes by providerId)
          → mixed-provider state is healthy and indefinitely stable

Stage 2:  run migration sweep (big-bang or continuous)
          → every gridfs row copied to S3, providerId flipped, GridFS row deleted
          → progress visible via REST/CLI; idempotent retry on partial failure

Stage 3:  all rows have providerId='s3'; GridFS is empty
          → drop the Mongo `fs.files` + `fs.chunks` collections (manual)
          → revert config to single-adapter setup
```

You can pause at Stage 1 indefinitely (acceptance test for the S3 backend without committing). You can roll back from Stage 2 by setting provider back to gridfs and running migration in reverse (`shepard-admin files migrate s3 gridfs`).

---

## Pick your S3 backend

The S3 adapter (FS1b) is endpoint-agnostic — any S3-compatible API works. Common picks:

| Backend | Best for | Trade-off |
|---|---|---|
| **Garage** | Self-hosted, single-host or small cluster, simple ops | Newer project; v1.0+ requires layout assign before bucket create |
| **MinIO** | Established self-hosted; familiar tooling | Community edition archived 2025 (slower upstream cadence); commercial licence for enterprise features |
| **AWS S3** | Large scale, offsite, no infrastructure burden | Egress + storage costs; data sovereignty (for EU operators, consider EU regions) |
| **LocalStack** | Dev / CI only | Not production-grade |
| **Ceph RGW / Wasabi / Backblaze B2 / OVH Object Storage** | All work with FS1b; pick on cost + region + SLA | Generic — no special handling |

**This fork's recommended default for self-hosted is Garage** ([ADR-0024](../../aidocs/platform/63-architecture-decision-log.md), memory `project_storage_s3_garage.md`). The Garage activation runbook is at [`docs/ops/garage-activation-runbook.md`](./garage-activation-runbook.md).

---

## Migration runbook

### Pre-flight (5 min)

```bash
# 1. Confirm current state — backend up + GridFS active
curl -fsS https://your-shepard/shepard/api/collections/<any-id> -H "X-API-KEY: <admin-key>" >/dev/null && echo "backend OK"

# 2. Count current GridFS files (gauge the migration size)
docker exec <shepard-mongo> mongosh --quiet --eval \
  "db.getSiblingDB('shepard_files').fs.files.countDocuments({})"

# 3. Disk space — S3 backend needs ~1.1× the GridFS data size during the copy phase
docker exec <shepard-mongo> mongosh --quiet --eval \
  "db.getSiblingDB('shepard_files').stats().storageSize"

# 4. Verify the S3 plugin is loaded (bundled in this fork's backend image since FS1b)
curl -fsS https://your-shepard/v2/admin/plugins -H "X-API-KEY: <admin-key>" | jq '.[] | select(.id=="file-s3")'
# Expected: {"id":"file-s3","state":"LOADED",...}
```

### Stage 1 — bring up S3 backend + dual-provider mode (15-30 min)

**Garage example** — use [`docs/ops/garage-activation-runbook.md`](./garage-activation-runbook.md) §1-§5. End state env vars:

```
SHEPARD_STORAGE_PROVIDER=s3
SHEPARD_FILES_S3_ENDPOINT=http://garage:3900
SHEPARD_FILES_S3_REGION=garage-region
SHEPARD_FILES_S3_BUCKET=shepard-files
SHEPARD_FILES_S3_PATH_STYLE_ACCESS=true
SHEPARD_FILES_S3_ACCESS_KEY_ID=<from `garage key create`>
SHEPARD_FILES_S3_SECRET_ACCESS_KEY=<from `garage key create`>
```

**MinIO / AWS / other S3 endpoints** — same env-var set; swap `_ENDPOINT`, `_REGION`, `_BUCKET`, credentials. `_PATH_STYLE_ACCESS=true` for Garage/MinIO/LocalStack; `false` for AWS S3 with virtual-host buckets.

Restart backend, then **verify dual-provider mode is active:**

```bash
# A. Adapter registry — both should be enabled
curl -fsS https://your-shepard/v2/admin/storage -H "Authorization: Bearer <admin-token>" | jq
# Expected: { "activeId":"s3", "adapters":[{"id":"gridfs",...},{"id":"s3","active":true,...}] }

# B. New container probe — create a FileContainer and verify uploads route to S3
APPID=$(curl -fsS -X POST -H "X-API-KEY: <key>" -H "Content-Type: application/json" \
  -d '{"name":"s3-probe","providerId":"s3"}' \
  https://your-shepard/shepard/api/fileContainers | jq -r '.appId')

curl -fsS -X POST -H "X-API-KEY: <key>" -H "Content-Type: application/json" \
  -d '{"fileName":"probe.txt"}' \
  https://your-shepard/v2/file-containers/$APPID/upload-url
# Expected: HTTP 200 with {"uploadUrl":"http://<your-s3>:.../shepard-files/...","oid":"...","expiresAt":"..."}
# If 503 "gridfs": the SHEPARD_STORAGE_PROVIDER env didn't take effect — verify the restart happened
```

At this point **new uploads go to S3** and **existing reads still work from GridFS**. You can stop here indefinitely.

### Stage 2 — migrate existing GridFS data (variable duration)

Pick **one** of two paths:

#### Path 2a — Big-bang sweep (FS1e1)

For deployments with < 10⁵ files, downtime acceptable, or a quiet maintenance window:

```bash
# Trigger
shepard-admin files migrate gridfs s3 --url https://your-shepard --api-key <admin-key>
# Returns immediately with state RUNNING

# Poll until done
watch -n 5 shepard-admin files migrate-status --url https://your-shepard --api-key <admin-key>
# Or in a script:
until shepard-admin files migrate-status --url ... --api-key ...; do sleep 10; done
# Exit codes: 0=IDLE/DONE, 1=RUNNING, 2=FAILED
```

Each file is streamed (no OOM); `providerId` is flipped after successful copy + delete of the GridFS row. Per-file failures are counted and logged but don't abort the sweep. Re-running `shepard-admin files migrate gridfs s3` after a partial failure is **idempotent** — already-migrated files have `providerId=s3` and are skipped by the Cypher selector.

#### Path 2b — Continuous auto-sweep (FS1e2)

For large deployments (> 10⁵ files), or to drain GridFS gracefully over hours/days without a single big window:

```properties
# Add to application.properties (or env)
shepard.migration.auto-sweep.enabled=true
shepard.migration.auto-sweep.source=gridfs
shepard.migration.auto-sweep.target=s3
shepard.migration.auto-sweep.interval=PT5M
```

Restart backend. The `FileMigrationService.autoSweep()` `@Scheduled` job fires every interval and processes the next batch. Skips ticks where a migration is already RUNNING (no concurrent sweeps).

Monitor:

```bash
curl -fsS https://your-shepard/v2/admin/files/migrate/status \
  -H "Authorization: Bearer <admin-token>" | jq
# Expected progression:
#   {"status":"RUNNING","filesTotal":12345,"filesMigrated":2000,"filesFailed":0,...}
# When done:
#   {"status":"DONE","filesTotal":12345,"filesMigrated":12345,"filesFailed":0,...}
```

### Stage 3 — finalize (single-adapter mode)

Once `filesFailed=0` and `filesTotal=filesMigrated`:

```bash
# Confirm zero gridfs rows remain
docker exec <shepard-neo4j> cypher-shell -u neo4j -p <password> \
  "MATCH (f:ShepardFile) WHERE f.providerId='gridfs' RETURN count(f) AS remaining"
# Expected: remaining=0

# Disable auto-sweep (if used): remove the three keys from application.properties + restart.

# Drop the now-empty GridFS collections (reclaim disk + clean up)
docker exec <shepard-mongo> mongosh --quiet --eval \
  "db.getSiblingDB('shepard_files').fs.files.drop(); \
   db.getSiblingDB('shepard_files').fs.chunks.drop();"
```

Verify migration end-state:

```bash
# A. Adapter registry
curl -fsS https://your-shepard/v2/admin/storage -H "Authorization: Bearer <admin-token>" | jq
# Expected: activeId="s3"; gridfs may still appear in adapters list but active=false

# B. Sample read — pick a known file and re-fetch via presigned URL
APPID=<known FileContainer appId>
OID=<known file OID>
curl -fsS -H "X-API-KEY: <key>" \
  https://your-shepard/v2/file-containers/$APPID/files/$OID/download-url | jq
# Then curl the downloadUrl directly and verify bytes match the pre-migration value.
```

---

## Rollback

The migration is reversible at every stage.

### From Stage 1 (dual-provider mode, no migration done yet)

Revert backend env: set or unset `SHEPARD_STORAGE_PROVIDER`. Restart. Files uploaded to S3 since Stage 1 are still reachable (registry routes by providerId); new uploads go back to GridFS. To consolidate everything back to GridFS:

```bash
shepard-admin files migrate s3 gridfs --url ... --api-key ...
```

### From Stage 2 partway (some files migrated, some still on GridFS)

```bash
# Disable auto-sweep, let the current batch finish, then reverse:
shepard-admin files migrate s3 gridfs --url ... --api-key ...
```

### From Stage 3 (all on S3, GridFS dropped)

This is the hardest rollback because the GridFS collections were dropped. Restore GridFS from your most recent Mongo backup, then:

```bash
shepard-admin files migrate s3 gridfs --url ... --api-key ...
```

**Backup discipline:** before Stage 3 (the irreversible drop), take a fresh Mongo backup. Stages 1-2 are repeatable without backups; Stage 3 is not.

### Per-file rollback (FS1e3, not yet merged)

Worktree `agent-a02dc8eb-...` (task #151) adds a per-file rollback REST endpoint that lets you revert specific files (e.g. when one ShepardFile's S3 copy is corrupted but GridFS still has the original). Currently pending merge to main; until then, per-file rollback is a manual Cypher update + GridFS restore.

---

## Frequent traps

1. **`SHEPARD_FILES_S3_PATH_STYLE_ACCESS=true` is non-negotiable for Garage and MinIO.** Env-var name is `…_PATH_STYLE_ACCESS` (five underscores) — `…_PATH_STYLE` silently does nothing. AWS S3 with path-style accepts `false` but virtual-host is more common.

2. **CORS on the S3 bucket** — for the FS1f frontend presigned upload to work, the bucket must allow `PUT` from your frontend's HTTPS origin. Garage: `cors_allow_origins` in `garage.toml`. MinIO: `mc anonymous cors set <alias>/<bucket> --method PUT --origin <frontend-origin>`. AWS: bucket CORS policy. Without CORS the frontend silently falls back to the legacy backend-proxy upload path — and *that* path returns 503 for S3 containers, so the user sees an upload error with no obvious diagnostic.

3. **`shepard.storage.provider` is deploy-time only.** The backend reads it at `@PostConstruct` and never re-reads. Changing it at runtime has no effect until restart. There is no `PATCH /v2/admin/storage` for this knob (by design — flipping at runtime would orphan in-flight writes).

4. **The migration sweep is single-threaded by design.** Per-file copy is streamed; concurrent writes are safe (each file has its own row in Neo4j with `providerId` as the routing key). But the sweep itself processes files one at a time. For 10⁶+ files, plan for a multi-hour window or use auto-sweep.

5. **OID preservation.** During migration, `S3FileStorage.put()` accepts an `assignedObjectKey` so the file's existing OID is preserved as the S3 key fragment. Only `providerId` changes in Neo4j — all client-side URLs that reference `/files/{oid}` keep working.

6. **Mongo backup tooling sometimes excludes GridFS by default.** Check your backup captures the `fs.files` + `fs.chunks` collections. After Stage 3 these are empty — but during Stage 2 they hold the source-of-truth bytes for files-not-yet-migrated.

---

## Cross-references

- [`docs/ops/garage-activation-runbook.md`](./garage-activation-runbook.md) — bring up Garage as the S3 backend (FS1d)
- [`docs/reference/file-storage.md`](../reference/file-storage.md) — per-primitive reference for the file-payload kind
- [`aidocs/34-upstream-upgrade-path.md`](../../aidocs/34-upstream-upgrade-path.md) — per-PR upgrade ledger; rows FS1a → FS1f cover the slices
- [`aidocs/data/45-gridfs-to-s3-evaluation.md`](../../aidocs/data/45-gridfs-to-s3-evaluation.md) — the design doc behind the FS1 series

---

**Snapshot date:** 2026-05-22 — initial consolidation of the v5 → S3 operator-facing runbook covering FS1a-f.
