# GridFS → S3 (Garage) Migration — Operator Runbook

**Scope.** Operator-facing runbook for migrating a Shepard install's
primary file payload storage from **MongoDB GridFS** to an
**S3-compatible** endpoint (Garage reference per ADR-0024; any
S3-compatible endpoint works).

**Status.** Draft — gated on the `[NEEDS-CLARIFICATION]` block at
the end. Do **not** execute against `shepard-api.nuclide.systems`
or any other live instance until those decisions are resolved.

**Snapshot date.** 2026-05-22.

**Companion documents.**

- `aidocs/data/45-gridfs-to-s3-evaluation.md` — the architecture and
  trade-off evaluation; this runbook is the **operational
  procedure** the evaluation calls for in §6.
- `docs/reference/file-storage.md` — public reference docs for the
  S3 adapter, presigned-URL flow, and Garage init.

---

## 1. Purpose + audience

**Two purposes, one runbook.**

| Purpose | Audience | Outcome |
|---|---|---|
| **Operational** — flip `shepard-api.nuclide.systems` from GridFS to Garage after the in-flight MFFD import (Q6) completes. Live data: ~40 k file blobs in MongoDB GridFS. | nuclide.systems ops (Flo). | Bytes move to Garage; `providerId` flipped per file; clients see no wire-shape change. |
| **Reference** — the validated upgrade path for any DLR institute running a "legacy" GridFS-only Shepard. The nuclide.systems run is the **canonical reference run**. | Other institutes upgrading from upstream Shepard 5.2.0 or any earlier fork build. | Same procedure, captured in `aidocs/34` (admin upgrade tracker) so other instances can follow with confidence. |

The reference framing is load-bearing: the nuclide.systems numbers
**become** §14 of this doc post-migration. Other institutes cite
that case study + the procedure below.

---

## 2. Preconditions

Run all of these to PASS before starting Phase 0. None are optional.

| # | Check | Verify with |
|---|---|---|
| P1 | **Garage healthy** — admin port responsive, layout applied, bucket created, key issued. | `docker exec shepard-garage /garage status` shows the node healthy; `docker exec shepard-garage /garage bucket list` shows `shepard`; `docker exec shepard-garage /garage key info shepard-backend` prints `access-key-id` + `secret-access-key`. |
| P2 | **Free space on Garage volume ≥ 1.5 × GridFS content size**. 1.5× because Garage replication factor may be > 1 + headroom for the migration's `:ShepardFileMigration` audit + safety margin. | On the Garage node: `df -h <garage-data-volume>` against the dry-run total from Phase 0. |
| P3 | **MongoDB backup taken** — `mongodump` of the entire shepard DB, including all `FileContainer*` GridFS buckets. Verified restorable on a scratch host. | `mongodump --uri <prod> --out backup-<date>` + restore-test on a scratch container. **Mandatory** — Phase 4 cleanup is irreversible without this. |
| P4 | **Neo4j backup taken** — full graph snapshot. | `neo4j-admin database dump --to-path=<backup>` (offline) or `neo4j-admin database backup` (online). |
| P5 | **S3 adapter wired but not yet active.** `shepard.plugins.file-s3.enabled=true` + `shepard.files.s3.*` configured + `shepard.storage.provider=gridfs` (still). | `GET /v2/admin/storage` returns both `gridfs` and `s3` with `enabled=true`; `activeProviderId=gridfs`. |
| P6 | **Instance not under heavy write load.** No bulk import is running; admin notice posted; the platform-wide instance lock (task #119, when shipped) is held. | Watch `shepard-admin metrics` for upload rate ≈ 0 for a full minute. Cancel any in-flight imports first. |
| P7 | **Admin API key on hand.** `instance-admin` role required for `/v2/admin/storage` + `/v2/admin/files/migrate`. | `shepard-admin storage status --api-key <key>` returns 200. |
| P8 | **`backend/src/main/java/de/dlr/shepard/storage/migration/FileMigrationService.java` is the version listed in `aidocs/34` row FS1e1 or newer.** Older builds lack OID preservation and will break wire compat. | `shepard-admin --version` ≥ the version that ships FS1e1. |

If **any** check fails, stop. Do not proceed.

---

## 3. Architecture invariants

These hold true throughout migration. They are what makes the
runbook safe.

- **Read routing is per-row.** `FileStorageRegistry` (cited:
  `backend/src/main/java/de/dlr/shepard/storage/FileStorageRegistry.java:62`)
  routes each read through `:ShepardFile.providerId` (cited:
  `backend/src/main/java/de/dlr/shepard/data/file/entities/ShepardFile.java:52-56`).
  A file with `providerId='gridfs'` resolves via `GridFsFileStorage`
  even while `shepard.storage.provider=s3` is the active default;
  the same is true in reverse. **Reads stay available throughout
  migration** — there is no moment where a `:ShepardFile` is
  un-routable.
- **New writes route by `shepard.storage.provider` at process
  start.** Flipping this requires a backend restart (deploy-time
  per the cluster-identity exception in `CLAUDE.md` + the
  `FileStorageRegistry` Javadoc lines 70-88). The runbook sequences
  the flip so all rows are migrated **before** the new-write
  routing changes.
- **OID is preserved across the move.** `FileMigrationService.migrateOne()`
  (cited: `FileMigrationService.java:249-293`) passes the source
  `oid` as `StoragePutRequest.assignedObjectKey`; `S3FileStorage.put()`
  (cited: `S3FileStorage.java:204-237`) uses it instead of minting
  a fresh UUID. Net: the S3 key fragment is `containerMongoId/<original-oid>`,
  identical to the GridFS oid, so any cached `:ShepardFile.oid`
  reference in clients keeps working.
- **The legacy `/shepard/api/...fileContainers/...payload` wire
  shape is byte-identical** regardless of backend, per the
  upstream-compat contract.

---

## 4. Phase 0 — Pre-flight

Verify the preconditions, run a dry-run, validate counts.

### 4.1 Verify adapters

```bash
shepard-admin storage status --api-key $SHEPARD_API_KEY
# Expect:
#   activeProviderId: gridfs
#   adapters:
#     - id: gridfs    enabled: true   active: true
#     - id: s3        enabled: true   active: false
```

If `s3` reports `enabled: false`, check the `shepard.files.s3.bucket`
+ credentials config and restart the backend before continuing.

### 4.2 Dry-run count

```bash
# Count + total bytes (no copy — read-only)
shepard-admin files migrate-status --api-key $SHEPARD_API_KEY
# Returns: { status: IDLE, ... }   (nothing running yet)

# Cypher-shell version (independent verification — recommended):
cypher-shell -u neo4j -p $NEO4J_PW \
  "MATCH (fc:FileContainer)-[:file_in_container]->(f:ShepardFile)
   WHERE f.providerId = 'gridfs'
   RETURN count(f) AS files,
          sum(coalesce(f.fileSize, 0)) AS totalBytes;"
```

Expected output shape:

```text
files     totalBytes
40123     1234567890123
```

[NEEDS-CLARIFICATION] **A first-class `POST /v2/admin/files/migrate/dry-run`
endpoint** does not exist today; `FileMigrationRest` is trigger-only
(`FileMigrationRest.java:53-88`). See clarification §17.A.

### 4.3 Per-container breakdown (optional but recommended)

```cypher
MATCH (c:Collection)-[:collection_dataobjects]->(:DataObject)
      -[*]->(fc:FileContainer)-[:file_in_container]->(f:ShepardFile)
WHERE f.providerId = 'gridfs'
RETURN c.name AS collection,
       count(DISTINCT fc) AS containers,
       count(f) AS files,
       sum(coalesce(f.fileSize, 0)) AS totalBytes
ORDER BY totalBytes DESC
LIMIT 50;
```

Use this to spot heavy containers (a single 1 TB container is fine;
a single 1 TB file is worth eyeballing for content type before the
copy starts).

### 4.4 Exit criteria

- Both adapters report `enabled: true`.
- Total file count + bytes from §4.2 matches operator expectation
  (e.g. ~40 k files on nuclide.systems).
- Free Garage volume ≥ 1.5 × `totalBytes`.
- Backups in §2 are taken **and** restore-tested.

---

## 5. Phase 1 — Copy (idempotent, single-thread today)

This phase reads each file from GridFS, writes it to S3 with the
preserved OID, leaves `:ShepardFile.providerId` as `gridfs`.

Today's `FileMigrationService` **fuses Phase 1 + Phase 2 + Phase 4**
per file: copy → flip providerId → delete from source, inside one
`migrateOne()` call (cited: `FileMigrationService.java:249-293`).
That's safe for an operator who is happy with per-file
fire-and-forget semantics, but it does **not** match the
Phase-separated rollback story this runbook documents in §6 + §9.

[NEEDS-CLARIFICATION] §17.B — phase split scope.

### 5.1 Algorithm (target end-state, Phase-separated)

```pseudocode
for each f in :ShepardFile WHERE providerId = SOURCE:
    locator_src      = buildLocator(SOURCE, f.containerMongoId, f.oid)
    resp             = SOURCE.get(locator_src)              # streamed
    sha256_in        = sha256_of(resp.stream)               # hash while reading
    put_req          = StoragePutRequest(
                         container       = f.containerMongoId,
                         fileName        = f.filename,
                         contentType     = resp.contentType,
                         bytes           = resp.stream,
                         sizeBytes       = f.fileSize,
                         assignedObjectKey = f.oid,
                         metadata        = { sha256: sha256_in }
                       )
    locator_dst      = TARGET.put(put_req)
    head             = TARGET.head(locator_dst)             # verify
    if head.sizeBytes != f.fileSize or head.metadata.sha256 != sha256_in:
        record FAILED with {oid, reason}
        continue
    # Create audit node — does NOT flip providerId yet
    CREATE (:ShepardFileMigration {
        forOid:               f.oid,
        sourceProviderId:     SOURCE,
        sourceLocator:        locator_src.value,
        targetProviderId:     TARGET,
        targetLocator:        locator_dst.value,
        sha256:               sha256_in,
        sizeBytes:            f.fileSize,
        copiedAt:             now(),
        phase:                'COPIED'
    })
```

### 5.2 Properties

- **Idempotent.** Re-running after partial failure skips already-copied
  rows (audit-node match on `forOid` + `targetProviderId`); a stray
  half-uploaded S3 object is overwritten by the next pass.
- **Resumable.** The `:ShepardFileMigration` audit node is the
  per-file checkpoint.
- **Streaming.** No file is fully buffered in JVM heap; size-bounded
  via `RequestBody.fromInputStream` (cited: `S3FileStorage.java:222`).
- **Parallelism: single-thread today.** `FileMigrationService` uses
  a single-thread executor (`FileMigrationService.java:105-109`).
  Throughput target tuning via `WORKERS` env is a **planned**
  extension; see §12 + §17.D.

### 5.3 Operator command (today)

```bash
shepard-admin files migrate gridfs s3 --api-key $SHEPARD_API_KEY
# Returns: 202 + { status: RUNNING, sourceProviderId: gridfs, targetProviderId: s3, ... }

# Watch progress (single-loop, exit codes: 0=IDLE/DONE, 1=RUNNING, 2=FAILED)
until shepard-admin files migrate-status --api-key $SHEPARD_API_KEY; do
  sleep 30
done
```

**Caveat — today's single command runs all 4 phases per file.** For
the operational nuclide.systems run, that is acceptable if (and only if)
the operator accepts that per-file rollback is not available (see §9).
For the reference-test framing, the runbook needs the §17.B split
landed first.

---

## 6. Phase 2 — Swap pointers (atomic per file)

Flip `:ShepardFile.providerId` from `SOURCE` to `TARGET`. Retain
`previousProviderId` + `previousLocator` for rollback.

[NEEDS-CLARIFICATION] §17.B — this phase only exists once
`FileMigrationService` is split per §17.B; today's code fuses it
into Phase 1.

### 6.1 Algorithm

```cypher
// One transaction per file. Predicate ensures idempotency.
MATCH (f:ShepardFile {oid: $oid})
WHERE f.providerId = $source
SET   f.previousProviderId = f.providerId,
      f.previousLocator    = $sourceLocator,
      f.providerId         = $target
WITH f
MATCH (m:ShepardFileMigration {forOid: $oid, targetProviderId: $target})
SET   m.phase          = 'SWAPPED',
      m.swappedAt      = datetime()
RETURN f.oid;
```

### 6.2 Transaction boundaries

- One Neo4j transaction per file. Throughput cost is real (one
  round-trip per row, ~40 k round-trips on the nuclide.systems run)
  but enables clean per-file rollback.
- Failure mode: if the transaction aborts after the COPY succeeded,
  the audit node stays in `phase=COPIED` and the re-run loop in §5
  picks it up.

---

## 7. Phase 3 — Verify completeness

Confirm every `:ShepardFile.providerId` flipped and every S3 object
is readable.

### 7.1 Count parity

```cypher
// Should be 0
MATCH (f:ShepardFile) WHERE f.providerId = 'gridfs' RETURN count(f) AS stillOnSource;

// Should equal the dry-run count
MATCH (f:ShepardFile) WHERE f.providerId = 's3' RETURN count(f) AS onTarget;

// Audit parity
MATCH (m:ShepardFileMigration {targetProviderId: 's3', phase: 'SWAPPED'})
RETURN count(m) AS swapped;
```

### 7.2 Random-sample integrity check

[NEEDS-CLARIFICATION] §17.E — sample fraction default.

```bash
shepard-admin files migrate verify \
  --api-key $SHEPARD_API_KEY \
  --sample-pct 1.0 \
  --out migration-report-$(date +%Y%m%d).json
# Pseudocode:
#   pick floor(N * sample_pct/100) :ShepardFileMigration nodes at random
#   for each: stream S3 object, sha256, compare to audit-node sha256
#   write report
```

Endpoint to add as part of §17.B: `POST /v2/admin/files/migrate/verify`
+ `GET /v2/admin/files/migrate/report`.

### 7.3 Report shape (`migration-report-YYYYMMDD.json`)

```json
{
  "instance": "shepard-api.nuclide.systems",
  "startedAt": "2026-05-22T14:00:00Z",
  "completedAt": "2026-05-22T16:42:00Z",
  "source": "gridfs",
  "target": "s3",
  "totals": {
    "filesExpected": 40123,
    "filesCopied":   40123,
    "filesSwapped":  40123,
    "filesFailed":   0,
    "bytesCopied":   1234567890123,
    "bytesPerSec":   127006982
  },
  "sample": {
    "samplePct": 1.0,
    "samplesChecked": 401,
    "samplesOk":      401,
    "samplesMismatch": 0,
    "mismatches": []
  },
  "errors": []
}
```

### 7.4 Exit criteria

All of:

- `stillOnSource == 0`
- `onTarget == filesExpected` (from Phase 0 dry-run)
- `samplesMismatch == 0`
- No entries in `errors`

If any check fails: **do not proceed to Phase 4.** Re-run Phase 1
(idempotent) to sweep residuals; investigate any sha-mismatched OIDs
individually.

---

## 8. Phase 4 — Cleanup (gated)

Delete GridFS payloads. After this phase, per-file rollback (§9.1)
is no longer possible; rollback is from the Mongo backup (§9.2).

[NEEDS-CLARIFICATION] §17.C — one-click vs 7-day soft delete.

### 8.1 Operator confirm

```bash
shepard-admin files migrate commit \
  --api-key $SHEPARD_API_KEY \
  --report-file migration-report-20260522.json \
  --confirm "yes, delete GridFS payloads"
```

This MUST:

- Refuse if any `:ShepardFile.providerId = 'gridfs'` row exists.
- Refuse if the report file's `filesFailed > 0` or
  `samplesMismatch > 0`.
- Refuse if the report file is older than 24 h.
- Log a single line to the structured-log audit channel.

### 8.2 Delete GridFS payloads

```pseudocode
for each :ShepardFileMigration {phase: 'SWAPPED', targetProviderId: TARGET}:
    parse sourceLocator → (containerMongoId, oid)
    GridFs(containerMongoId).delete(oid)            # idempotent
    SET m.phase = 'CLEANED', m.cleanedAt = now()
```

### 8.3 Optional follow-ups

- Drop the now-empty per-container GridFS collections (`FileContainer<uuid>.{files,chunks}`).
  Reclaims Mongo disk. Reversible only from backup.
- Disable the GridFS adapter on next restart: remove or comment-out
  the in-core `GridFsFileStorage` registration. **Not recommended**
  until at least one quarter of stable S3 operation — keeping it
  installed costs nothing and preserves the optional posture in
  `FileStorageRegistry`.

---

## 9. Rollback semantics

Per-file vs full restore depends on **which phase** failed.

### 9.1 Per-file revert (available between Phase 2 and Phase 4)

```cypher
MATCH (f:ShepardFile {oid: $oid})
WHERE f.previousProviderId IS NOT NULL
SET   f.providerId = f.previousProviderId
REMOVE f.previousProviderId, f.previousLocator
WITH f
MATCH (m:ShepardFileMigration {forOid: $oid, targetProviderId: $target})
SET   m.phase = 'REVERTED', m.revertedAt = datetime();
// S3 object stays in place — orphan; pick up via FS1e + a 'sweep-orphans' verb
```

### 9.2 Decision table

| Failure mode | Phase reached | Action |
|---|---|---|
| Garage unreachable mid-copy | Phase 1 | Resume — `shepard-admin files migrate gridfs s3` again. |
| Cypher swap fails mid-batch | Phase 2 | Resume. Audit nodes still in `phase=COPIED` are re-swapped. |
| Sample sha-mismatch | Phase 3 | Investigate the specific OID; re-copy via `shepard-admin files migrate verify --repair <oid>` (verb to be added per §17.B); rerun verify. |
| Operator wants to abort cleanly post-Phase-2 | Phase 2 done, Phase 4 not started | `shepard-admin files migrate rollback --api-key ...` walks every `:ShepardFileMigration {phase: 'SWAPPED'}` and reverses via §9.1. |
| Catastrophic — corruption discovered post-Phase-4 | Phase 4 | Restore from §2 P3 / P4 backup. **No per-file revert.** Document the operator runbook for full restore in `docs/admin.md`. |

---

## 10. Operator UX

### 10.1 CLI subcommand surface (target — gaps flagged)

| Verb | Status | Notes |
|---|---|---|
| `shepard-admin files migrate <source> <target>` | **shipped** (FS1e1) | Today fires Phase 1+2+4 fused; runbook target is Phase 1 only. |
| `shepard-admin files migrate-status` | **shipped** (FS1e1) | exit 0=IDLE/DONE, 1=RUNNING, 2=FAILED. |
| `shepard-admin files migrate plan` | **gap** (§17.A) | Dry-run; returns count+bytes shape. |
| `shepard-admin files migrate start` | gap | Alias for `migrate` after split. Triggers Phase 1 only. |
| `shepard-admin files migrate verify` | **gap** (§17.E) | Phase 3 sample check + report file. |
| `shepard-admin files migrate commit` | **gap** (§17.B) | Phase 4 gated cleanup. |
| `shepard-admin files migrate cleanup` | gap | Alias of `commit`. |
| `shepard-admin files migrate rollback` | gap | Phase 2 reverse per §9.1. |

### 10.2 REST endpoint matrix

| Endpoint | Status | Cited |
|---|---|---|
| `GET /v2/admin/storage` | **shipped** (FS1e1) | `StorageAdminRest.java:36-61` |
| `POST /v2/admin/files/migrate` | **shipped** (FS1e1) | `FileMigrationRest.java:53-88` |
| `GET /v2/admin/files/migrate/status` | **shipped** (FS1e1) | `FileMigrationRest.java:90-105` |
| `POST /v2/admin/files/migrate/dry-run` | gap (§17.A) | Phase 0 count + bytes shape |
| `POST /v2/admin/files/migrate/verify` | gap | Phase 3 sample check |
| `GET /v2/admin/files/migrate/report` | gap | Latest report JSON |
| `POST /v2/admin/files/migrate/commit` | gap | Phase 4 |
| `POST /v2/admin/files/migrate/rollback` | gap | Phase 2 reverse |

### 10.3 Frontend pairing

Ticket #67 (frontend storage-admin panel) is the partner of this
runbook. The panel shows the FS1e state machine (IDLE → RUNNING →
DONE / FAILED), pull-to-refresh, and links to the latest report
JSON. The panel does **not** offer commit/rollback in v1; both stay
CLI-only until the platform-wide instance lock (#119) is in.

---

## 11. Test plan

A runbook is "validated for legacy instances" once **all of**:

- **(a) Unit tests** — one per `FileMigrationService` phase function;
  one per CLI verb. Includes failure-mode coverage (Garage 5xx,
  Cypher abort, sha-mismatch in verify, Phase 4 refusal when
  `filesFailed > 0`).
- **(b) Integration test** on a seeded GridFS instance with N synthetic
  files:
  - N = 10 (smoke)
  - N = 100 (loop + report shape)
  - N = 1000 (concurrency + audit-node correctness)
  - N = 10 000 (throughput sanity at single-thread)
  - **N = 50 000** ([NEEDS-CLARIFICATION] §17.F — bumps the ceiling
    so the nuclide.systems 40 k run is below the validated ceiling,
    not the validation case)

  Each level asserts: (i) post-Phase-3 counts match seed counts;
  (ii) random-sample sha256 matches; (iii) reads remain available
  through Phase 1 + 2; (iv) per-file revert works between Phase 2
  and 4; (v) Phase 4 refuses when `filesFailed > 0`.
- **(c) The nuclide.systems live run** — captured as §14 reference
  case study post-execution. Numbers fill the empty table.

Until all three exist, the runbook's `aidocs/34` row stays at
**AWARE**, not OPERATIONAL.

---

## 12. Performance + scaling

**Today's single-thread reality.** `FileMigrationService` runs on a
single-thread executor (`FileMigrationService.java:105-109`). On a
modest VM with NVMe Mongo + Garage on the same host, expect **5–20
files/sec** for small files (1–10 MiB), **bottlenecked by sequential
PUT** for large ones. The nuclide.systems 40 k file run at 10 f/s ≈
1.1 hours; at 5 f/s ≈ 2.2 hours.

**Planned tuning (out of scope for the runbook draft; gated on
§17.B + §17.D):**

- `WORKERS` env (default 8) — parallel file workers.
- Per-file timeout to skip pathologically slow rows.
- Auto-throttle on observed error rate (§17.D).

**When to scale Garage replication:** the §2 P2 1.5× headroom
assumes replication factor 1 (single-node Garage). For RF=3
(production three-node Garage), the multiplier is 4.5× content
size — recheck before triggering.

---

## 13. Audit trail

Three layers of permanence:

### 13.1 Structured logs

`FileMigrationService` already logs:

- `migration queued (source=…, target=…)` (line 146)
- `%d file(s) to migrate from '%s' to '%s'` (line 205)
- `skipping oid=%s — %s` per-file failure (line 228)
- `migration complete (migrated=…, failed=…, source=…, target=…)` (line 237-240)

These go to the standard Quarkus log stream; an EN 9100 auditor
queries them by correlation id.

### 13.2 `:ShepardFileMigration` audit nodes

Per-file Neo4j record (created in Phase 1, mutated in Phase 2 + 4).
[NEEDS-CLARIFICATION] §17.G — retention forever vs prune after N
months.

Auditor reconstruction queries:

```cypher
// Total files migrated in a given run
MATCH (m:ShepardFileMigration {sourceProviderId: 'gridfs', targetProviderId: 's3'})
WHERE m.copiedAt >= datetime('2026-05-22T00:00:00Z')
RETURN count(m) AS migrated, sum(m.sizeBytes) AS bytes;

// SHA-256 sum-of-sums (immutable inventory)
MATCH (m:ShepardFileMigration {targetProviderId: 's3'})
RETURN m.forOid, m.sha256, m.sizeBytes
ORDER BY m.forOid;
```

### 13.3 Migration report file

§7.3 JSON. Upload artefact (`/v2/admin/files/migrate/report` or the
local `ImportScripts` directory).

**Auditor flow:** count migrated files in §13.2 → match against
`totals.filesCopied` in §13.3 → spot-check 10 random `sha256` values
against S3 via `mc cp s3://shepard/<container>/<oid> -` + `sha256sum`.
**No data was lost** ↔ the three counts agree + sha256s match.

---

## 14. Reference run case study (appendix)

**Status — placeholder.** Filled in after the nuclide.systems
migration executes.

| Field | Value |
|---|---|
| Instance | `shepard-api.nuclide.systems` |
| Date | TBD post-Q6 import completion |
| Source file count | TBD (~40 k expected) |
| Source byte total | TBD |
| Target endpoint | Garage (single-node, RF=1) on `shepard.nuclide.systems` host |
| Duration | TBD |
| Throughput (files/sec) | TBD |
| Errors | TBD |
| sha256 sample-check mismatches | TBD |
| Notable anomalies | TBD |
| Operator commentary | TBD |

Other DLR institutes cite **this case study** as evidence the
procedure is field-validated. The §11 N=50k synthetic test is the
**capacity ceiling**; nuclide.systems is the **wild run**.

---

## 15. What this validates

Successful execution of this runbook against nuclide.systems is the
first production exercise of:

| Ticket | Validated property | aidocs/34 row transition |
|---|---|---|
| **FS1b** (S3 adapter) | Writes + reads through `S3FileStorage` against a real Garage at non-trivial scale. | AWARE → OPERATIONAL |
| **FS1c** (presigned URLs) | Post-cutover, new uploads use the presigned PUT path; the migrated rows are downloadable via presigned GET. | AWARE → OPERATIONAL |
| **FS1e1** (migration tool) | OID preservation under load; resume-after-failure; per-file failure isolation. | AWARE → OPERATIONAL |
| **FS1f** (frontend fallback) | Post-cutover, the browser uploads bypass the backend byte proxy. | AWARE → OPERATIONAL |
| **FS1e2** (auto-sweep) | Not exercised by this runbook (big-bang mode). Stays AWARE. | — |

---

## 16. Tracker hooks (DO NOT EDIT IN THIS PR)

The implementation PR that lands the §17 extensions + this runbook
needs to touch:

- **`aidocs/34-upstream-upgrade-path.md`** — new row "FS1e3 —
  phase-split migration + audit nodes + verify/commit/rollback
  verbs" (status AWARE on landing; OPERATIONAL after nuclide.systems
  case study is filled into §14). FS1e1 row gets a "see also FS1e3"
  pointer in the notes column.
- **`aidocs/44-fork-vs-upstream-feature-matrix.md`** — FS1e3 row
  status flip from designed → in-flight → shipped.
- **`aidocs/16-dispatcher-backlog.md`** — FS1e3 sub-IDs (FS1e3a
  dry-run REST, FS1e3b phase split, FS1e3c verify+report, FS1e3d
  commit, FS1e3e rollback).

---

## 17. `[NEEDS-CLARIFICATION]`

Resolve these before this runbook lands in `aidocs/34` as
OPERATIONAL or runs against any live instance.

### 17.A — Add a first-class dry-run endpoint?

Phase 0 §4.2 needs a count + total-bytes shape; today it requires
direct Cypher access.

  Context: `FileMigrationRest.java:53-88` is trigger-only; there is
  no `POST /v2/admin/files/migrate/dry-run` and no
  `GET /v2/admin/files/migrate/plan`.
  Options:
    A) Ship a dedicated `POST /v2/admin/files/migrate/dry-run` that
       returns `{filesPending, totalBytes, perContainer[]}` —
       pro: matches the runbook's other phase verbs symmetrically;
       con: another REST surface to maintain.
    B) Document the Cypher-shell snippet in §4.2 as the operator's
       answer — pro: zero new code; con: requires DB shell access
       (which a hosted-shepard operator may not have).
    C) Add `--dry-run` flag to the existing
       `shepard-admin files migrate` verb — pro: keeps the CLI shape
       compact; con: extends an already-overloaded verb.
  Lean: **A** — dry-run is a distinct admin operation; bundling it
  into `migrate` invites the "I meant to dry-run but I actually
  ran" foot-gun.

### 17.B — Phase split scope

Today's `FileMigrationService.migrateOne()` fuses Phase 1 + 2 + 4
per file (`FileMigrationService.java:282-291`). This runbook
describes phases that **separate** them. Real behavioural change,
not just docs.

  Context: line 290 `source.delete(srcLocator)` is the fused Phase
  4 step; line 287 `neo4jSession.query(CYPHER_UPDATE, ...)` is the
  fused Phase 2 step. No `previousProviderId` field on `ShepardFile`,
  no `:ShepardFileMigration` audit node label.
  Options:
    A) Build FS1e3 (phase-split) before any production run —
       runbook waits for it. pro: clean rollback semantics;
       con: blocks nuclide.systems Q6 cleanup window.
    B) Document the today-state ("Phases are fused; per-file
       rollback unavailable; cleanup is immediate per file") and
       ship FS1e3 as a follow-up. pro: nuclide.systems can run
       this week; con: the runbook's "reference test" framing is
       weaker because the live run does not exercise the
       phase-separated path.
    C) Half-measure — add `previousProviderId` + `previousLocator`
       + audit node now, defer the cleanup-gating verb. pro: gets
       per-file rollback without a new endpoint; con: split UX
       (rollback works, commit/verify don't yet).
  Lean: **C** — gets the load-bearing rollback property in place
  for nuclide.systems while keeping the FS1e3 scope tight enough to
  land in one PR.

  **Resolution: option C shipped (2026-05-22)** —
  `aidocs/34 FS1e3` and `aidocs/44 FS1e3`. Four nullable wire-hidden
  fields on `:ShepardFile` (`previousProviderId`, `previousLocator`,
  `migratedAt`, `migrationHmac`) + new
  `POST /v2/admin/files/migrate/rollback/{appId}` (instance-admin) +
  `FileMigrationService.rollbackOne(appId)`. Today's fused-Phase-4
  `source.delete()` stays per task-spec. Verify/commit/sweep-orphans
  verbs deferred to FS1e3c/d/e. See
  `docs/reference/file-storage.md §Per-file rollback (FS1e3)` for
  the operator runbook section.

### 17.C — Cleanup phase irreversibility model

§8 says Phase 4 deletes GridFS payloads after operator confirm.
Should the operator confirm be ONE-CLICK or REQUIRE-A-7-DAY-DELAY?

  Context: `aidocs/45 §6` documents the no-going-back semantics but
  does not specify a delay. EN 9100 prefers explicit
  hard-to-accidentally-trip safeguards.
  Options:
    A) One-click confirm via the typed phrase `"yes, delete GridFS
       payloads"` — pro: fast; con: no recovery window after a
       typed-confirm slip.
    B) 7-day soft-delete window: Phase 4 marks `:ShepardFileMigration`
       as `phase=SOFT_DELETED, hardDeleteAfter=<now+7d>`; a scheduled
       sweep runs the actual GridFS delete after the window. Operator
       can revert in the window via §9.1. — pro: real safety net;
       con: GridFS bytes occupy storage for 7 extra days; extra
       scheduled-task code.
    C) 24-hour soft-delete — middle ground.
  Lean: **B** — the operator most-likely to need this runbook is
  one running an unattended Shepard with months of accumulated
  data. A 7-day window is cheap insurance.

### 17.D — Parallelism shape

§12 says single-thread today; future tuning via `WORKERS` env. When
the worker pool lands, should error rate auto-throttle workers?

  Options:
    A) Operator-configured constant `WORKERS=8` — pro: predictable;
       con: a flaky Garage causes the operator to manually slow
       down.
    B) Auto-throttle: halve worker count on every 5 consecutive
       errors; double back up on every 100 consecutive successes
       (capped at `WORKERS`). pro: hands-off resilience; con: code
       complexity + harder to reason about live throughput.
    C) Constant `WORKERS` + a `--max-errors-before-pause` knob
       (manual circuit-breaker). pro: simple; con: still requires
       a watcher.
  Lean: **A** for FS1e3; **C** if there's bandwidth in the same
  PR. Auto-throttle (B) is over-engineering for a one-shot per-
  install migration.

### 17.E — Inconsistent-state files

How does the runbook handle (a) GridFS oids with no `:ShepardFile`
row (orphan in Mongo) and (b) `:ShepardFile` rows whose oid is
missing from GridFS?

  Context: `:ShepardFile.providerId = gridfs` but the legacy
  GridFS read returns `NoSuchKeyException` — currently §5 marks
  this as FAILED and continues. Symmetric: orphan GridFS chunks
  with no `:ShepardFile` are never read by the migration loop, so
  they stay in Mongo forever unless explicitly swept.
  Options:
    A) Log + continue (today's behaviour). pro: simple; con:
       orphans accumulate; operator does not see them in the
       report.
    B) Add a pre-flight `find-orphans` step that catalogues both
       directions and lists them in the dry-run report. Operator
       chooses to skip or repair. pro: visible; con: extra Mongo
       traversal at scale.
    C) Add the catalog + auto-purge the orphan GridFS chunks at
       Phase 4 (with separate confirm). pro: cleanest; con: more
       phases, more confirms.
  Lean: **B** — visibility without auto-action. Operators can run
  a follow-up `shepard-admin files sweep-orphans` if they want
  cleanup.

### 17.F — Integration-test scale ceiling

§11 lists N up to 10 000; the nuclide.systems run is 40 000. Either
bump the ceiling to 50 000 (so the live run is well below the
validated ceiling) or accept "validated for ≤ 10 k file installs"
until the live run completes.

  Options:
    A) Bump synthetic ceiling to N = 50 000. pro: nuclide.systems
       is below the ceiling; con: synthetic dataset generation takes
       longer in CI.
    B) Keep ceiling at N = 10 000; declare "validated for ≤ 10 k
       file installs" until the nuclide.systems case study fills
       §14. pro: less CI time; con: explicit caveat in the
       `aidocs/34` row.
    C) Tier the validation: N=10 000 = "validated for typical
       institute"; N=50 000 = "validated for larger institute";
       N=nuclide.live = "validated by reference run". pro: clearest
       claim per scale; con: most CI time.
  Lean: **C** — the runbook is a reference; explicit scale tiers
  make the claim defensible.

### 17.G — `:ShepardFileMigration` retention

Forever (audit) or prune after N months?

  Context: ~40 k rows added to Neo4j per migration is not large; but
  if this runbook becomes an annual exercise (yearly Garage cluster
  rotation, e.g. RF=1 → RF=3 cutover) the count multiplies.
  Options:
    A) Retain forever. pro: audit-grade; EN 9100 / DIN EN 9100
       happy; con: graph clutter at multi-year horizon.
    B) Prune after 24 months by default; configurable via
       `shepard.migration.audit-retention`. pro: bounded; con:
       audit-grade only within retention.
    C) Hybrid: keep the per-file `:ShepardFileMigration` for 24
       months; aggregate to a per-run `:FileMigrationReport` node
       (sum of bytes, count, sha256 sum-of-sums) forever.
       pro: audit + bounded; con: more code.
  Lean: **C** — preserves the auditable summary forever; keeps the
  graph clean.

### 17.H — SHA-256 persistence on `:ShepardFile`

The runbook stores per-file SHA-256 in the audit node + as S3
object metadata. Should the same SHA-256 also be persisted on the
`:ShepardFile` node going forward (useful for FS1g content-
addressing) or remain migration-scoped only?

  Context: `ShepardFile` already has an `md5` field
  (`ShepardFile.java:25-26`); SHA-256 would be a new property and
  a schema decision beyond migration.
  Options:
    A) Migration-scoped only. pro: smallest surface; con: re-do
       work later when FS1g lands.
    B) Persist `sha256` on `:ShepardFile` as part of FS1e3.
       pro: free content-addressing prep; con: drags FS1g
       discussion into the migration PR.
    C) Persist on `:ShepardFile` but **only for migrated rows**;
       new uploads compute on demand later. pro: incremental;
       con: split-data — some rows have sha256, some don't.
  Lean: **A** — keep migration scope clean. The FS1g design
  document decides the schema property separately.

### 17.I — SHACL revalidation on migration

Does the migration trigger a re-validation against SHACL shapes
(per the upcoming SHACL changeover, task #120) or stay purely
byte-level?

  Context: SHACL shapes constrain `:ShepardFile` properties (oid,
  filename, etc.); migration only mutates `providerId` /
  `previousProviderId`. A byte-level migration shouldn't break
  any shape, but a defensive validate-after pass would catch
  unrelated drift.
  Options:
    A) No SHACL revalidation; pure byte/pointer change. pro:
       fast; con: drift goes undetected.
    B) Validate **after Phase 2** against the active SHACL
       shapes; refuse Phase 4 if validation fails. pro: catches
       drift; con: couples the runbook to task #120's schedule.
    C) Validate only the migration-touched properties (`providerId`,
       `previousProviderId`, `previousLocator`) post-Phase-2.
       pro: minimal coupling; con: misses unrelated drift.
  Lean: **A** — SHACL revalidation is an orthogonal concern
  belonging to task #120's own runbook. Don't braid two big
  changeovers together.
