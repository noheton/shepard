---
stage: concept
last-stage-change: 2026-05-30
---

# 89 — Pre-mutation snapshot contract

**Status.** Concept. Design doc only; no implementation shipped yet.
**Audience.** Backend contributors, instance admins, CI gate authors.
**Relates to.** `aidocs/16 §PRE-MUT-SNAP` (backlog),
`aidocs/data/41-snapshots-design.md` (snapshot architecture),
`project_snapshot_boundaries.md` (memory: snapshots are human-gated
boundaries), `feedback_referenced_data_infinite_retention.md`
(kill-switch: referenced data is never a deletion candidate).

---

## 1. Motivation

On 2026-05-23, during the MFFD TS-tapelaying-duplicate triage, an
operator ran a spot-check before issuing a `DELETE` against three
TimescaleDB containers (IDs 528098, 589657, 599903). The backend
returned a 500 due to an unrelated decompression-limit issue
(TSDB-DDL-1), so no data was lost. But the near-miss exposed a
structural gap: the only safety net was an accidental backend error,
not an intentional pre-mutation gate.

The structural fix is to require a pre-mutation snapshot for every
pathway that can destroy or irreversibly transform data. The snapshot
is the last readable checkpoint before the change; if anything goes
wrong after it, the operator has a restore path. Without it, "undo"
means backups — slower, noisier, and scope-wider than needed.

Two existing policies frame this contract:

- **`project_snapshot_boundaries.md`** — "every larger transformation
  gets a snapshot pair (before + after)." This doc operationalises
  that rule for the specific class of mutations that modify persisted
  data.
- **`feedback_referenced_data_infinite_retention.md`** — "data that
  has any live reference is never touched." This is the kill-switch
  criterion: if a mutation pathway's scope analysis shows that every
  candidate row has at least one live reference, the snapshot is still
  taken (for audit), but the mutation itself is a no-op.

The companion snapshot architecture is `aidocs/data/41`, which defines
the logical snapshot model (entity revisions, `SnapshotEntry` map).
This document is not a redesign of that model; it defines the **gate
behaviour** — when a snapshot must be taken, what it must cover, and
what happens when it fails.

---

## 2. Scope — mutation pathways that require a pre-mutation snapshot

The following operation classes are in scope. Each one MUST take a
pre-mutation snapshot of the affected scope before executing the
first destructive operation.

### 2.1 Orphan cleanup and purge operations

- SM1 `purgeOrphans` — removes TimescaleDB rows, Mongo documents, and
  Garage objects whose reference count has fallen to zero and whose
  retention window has expired.
- Manual orphan-wipe scripts under `examples/mffd-showcase/scripts/`
  (e.g. the tapelaying-duplicate consolidation).
- Any future `DELETE /v2/admin/storage/purge` endpoint (PRE-MUT-SNAP2
  will add a REST surface for this).

### 2.2 Schema and data migrations

- Any Cypher migration file that mutates existing node properties or
  deletes nodes / relationships (as opposed to purely additive `IF NOT
  EXISTS` guards — those are exempt).
- Any Flyway SQL migration that runs `UPDATE`, `DELETE`, or `ALTER
  TABLE ... DROP COLUMN`.
- The `MigrationsRunner` post-A1e propagation path — if a migration
  fails, the snapshot taken before it ran is the rollback artefact.

### 2.3 Storage rebalance and file-move operations

- FS1e file-move (`POST /v2/admin/files/migrate`) — copies every
  payload SHA from one FileStorage backend to another, then removes
  the source objects.
- Any future blob-rebalance or cross-bucket copy that removes source
  objects after confirming the destination copy.

### 2.4 Shepherded admin commands that touch data

- `shepard-admin storage purge` — the CLI wrapper for SM1 purgeOrphans.
- `shepard-admin storage migrate` — the CLI wrapper for FS1e.
- Any future `shepard-admin <feature> reset` or `shepard-admin
  <feature> wipe` variant.

### 2.5 Out of scope (no pre-mutation snapshot required)

- Purely additive writes (POST, create, append-only INSERT) — no
  existing data is at risk.
- Read-only operations (GET, SPARQL queries, export without delete).
- Soft-delete status flips (DataObject status DRAFT → ARCHIVED) that
  do not destroy payload bytes.
- Provenance and secondary-write activity recording — these are
  secondary effects; their own failure mode is handled by the
  fire-and-forget rule (CLAUDE.md §"secondary writes are fire-and-forget").

---

## 3. Snapshot artefact shape

### 3.1 The `:Snapshot` DataObject in `snapshots-system`

Every pre-mutation snapshot is materialised as a `:Snapshot`
DataObject inside a reserved collection named `snapshots-system`. The
collection is created by a startup migration and is read-only for all
roles except `instance-admin`. Normal users cannot write to, rename,
or delete it.

The `:Snapshot` DataObject carries the following fields in addition to
the standard `appId + createdAt + createdBy`:

```
name:          string   — human-readable, e.g. "pre-purge-SM1-2026-05-30T14:22Z"
description:   string   — machine-generated summary of the scope
triggerKind:   enum     — one of: ADMIN_CLI | REST_ENDPOINT | MIGRATION | SCRIPT
triggeredBy:   string   — userId or system principal (e.g. "shepard-migrations")
scope:         JSON     — per-substrate scope descriptor (see §3.2)
artefactHash:  string   — SHA-256 of the concatenated per-substrate artefact hashes
retainUntil:   Instant  — createdAt + grace window (default 90 days)
```

PROV-O edges wired via `ActivityDAO.wireEdges()`:
- `WAS_ASSOCIATED_WITH` → `:User` or system actor
- `GENERATED` → the `:Snapshot` DataObject
- `USED` → every entity whose `appId` appears in the scope descriptor

The `artefactHash` field provides tamper-evidence at the snapshot
boundary. The HMAC chain (`HmacChainService.stamp()`) extends this to
the per-`:Activity` level.

### 3.2 Scope descriptor (per-substrate)

The `scope` field is a JSON object keyed by substrate name. Each value
is a substrate-native description of what was captured.

```json
{
  "timescaledb": {
    "table": "timeseries_data_points",
    "filter": "container_id IN (528098, 589657, 599903)",
    "rowCount": 17843201,
    "sizeBytes": 1073741824,
    "snapshotTable": "snapshots.timeseries_data_points_20260530T142200Z",
    "sha256": "a3f1..."
  },
  "neo4j": {
    "rootAppIds": ["019...a", "019...b", "019...c"],
    "nodeCount": 127,
    "relationshipCount": 318,
    "exportFile": "s3://snapshots/neo4j-20260530T142200Z.cypher.gz",
    "sha256": "b7e2..."
  },
  "mongodb": {
    "collections": ["timeseries_metadata"],
    "filter": {"containerId": {"$in": [528098, 589657, 599903]}},
    "documentCount": 42,
    "snapshotCollection": "snapshots.timeseries_metadata_20260530T142200Z",
    "sha256": "c9d4..."
  },
  "garage": {
    "buckets": ["shepard-files"],
    "objectKeys": ["sha256/a3f1...", "sha256/b7e2..."],
    "objectCount": 7,
    "sizeBytes": 204800,
    "manifestFile": "s3://snapshots/garage-manifest-20260530T142200Z.json",
    "sha256": "d5f6..."
  }
}
```

Substrates not covered by the mutation scope are omitted from the
descriptor. A migration that only touches Neo4j will have a
`neo4j`-only scope descriptor.

### 3.3 Per-substrate snapshot primitives

These are the implementation targets for PRE-MUT-SNAP2.

**TimescaleDB.** Create a parallel table in a dedicated `snapshots`
schema:

```sql
CREATE TABLE IF NOT EXISTS snapshots.<table>_<session>
  AS SELECT * FROM <table> WHERE <scope_filter>;
ALTER TABLE snapshots.<table>_<session> SET (autovacuum_enabled = false);
```

The session identifier is a UTC timestamp slug to avoid collisions.
The snapshot table is read-only by construction (no triggers). After
the grace window, `DROP TABLE snapshots.<table>_<session>` runs as
part of the retention sweep.

**Neo4j.** Use `apoc.export.cypher.query(cypher, filePath, config)`
to write the subgraph defined by the scope to a Garage object:

```cypher
CALL apoc.export.cypher.query(
  "MATCH (n) WHERE n.appId IN $appIds OPTIONAL MATCH (n)-[r]->(m) RETURN n, r, m",
  $exportUri,
  {format: 'plain', useOptimizations: {type: 'UNWIND_BATCH', unwindBatchSize: 100}}
) YIELD nodes, relationships, properties
```

The `$exportUri` is a `s3://snapshots/neo4j-<session>.cypher.gz`
path. The SHA-256 of the gzipped file is the `sha256` field.

**MongoDB.** Use the `$out` aggregation stage to project into a
snapshot collection:

```javascript
db.getCollection(coll).aggregate([
  { $match: scopeFilter },
  { $out: `snapshots.${coll}_${session}` }
])
```

Snapshot collections are in the same MongoDB database to avoid a
cross-database `$out` restriction.

**Garage / S3.** For file payloads, the snapshot is a manifest JSON
listing every object key + expected SHA-256, uploaded to
`s3://snapshots/garage-manifest-<session>.json`. The actual objects
are not copied (they are content-addressed by SHA-256 and are the
primary source of truth). The manifest is the restore anchor: if a
post-mutation operator needs to verify that the right objects are
still in Garage, they compare the current bucket contents against the
manifest. A full copy is only needed if objects were deleted as part
of the mutation (e.g. FS1e after confirming destination copy).

### 3.4 SnapshotResult contract

Each substrate primitive returns a structured result:

```java
record SnapshotResult(
    String substrate,       // "timescaledb" | "neo4j" | "mongodb" | "garage"
    String scope,           // human-readable scope summary
    long   rowCount,        // rows / nodes / documents / objects captured
    long   sizeBytes,       // uncompressed size estimate
    String artefactUri,     // where the snapshot artefact lives
    String sha256,          // SHA-256 of the artefact
    long   durationMs       // wall-clock time to take the snapshot
)
```

The per-substrate results are aggregated by `SnapshotService` into the
`scope` JSON and the combined `artefactHash`.

---

## 4. Retention policy

### 4.1 Default grace window: 90 days

Pre-mutation snapshots are retained for 90 days after creation. This
covers the typical post-deployment observation window during which an
operator might notice a data anomaly and need to restore from the
pre-mutation state.

The window is configurable at runtime via the `:SnapshotConfig`
singleton:

```
GET  /v2/admin/snapshots/config
PATCH /v2/admin/snapshots/config
  body: { "graceWindowDays": 180 }
```

The deploy-time default is `shepard.v2.snapshots.grace-window-days=90`
in `application.properties`. The runtime value from `:SnapshotConfig`
takes precedence per the admin-configurable-at-runtime rule
(CLAUDE.md §"Always: surface operator knobs in the admin config").

Individual snapshots can be pinned indefinitely by setting their
`retainUntil` to null:

```
PATCH /v2/snapshots/{appId}
  body: { "retainUntil": null }
```

Pinned snapshots are exempt from the grace-window sweep. Only
`instance-admin` can pin or unpin.

### 4.2 Retention sweep

A scheduled task (default: daily, 03:00 UTC) scans for snapshots
where `retainUntil < now()` and issues the substrate-native drop
operations in reverse-dependency order (Garage manifest → Mongo
snapshot collections → TimescaleDB snapshot tables → Neo4j export
files). The `:Snapshot` DataObject itself is soft-deleted after all
artefacts are removed.

The sweep is itself governed by the `--no-snapshot` escape hatch rule:
the sweep DOES NOT take a snapshot before dropping old snapshots. Old
snapshot artefacts are by definition already the final state of the
data at the time they were taken; dropping them after the grace window
is an intentional expiry, not a destructive mutation.

### 4.3 `--no-snapshot` escape hatch

Any mutation pathway can be invoked with `--no-snapshot` (CLI) or
`?skipSnapshot=true` (REST admin endpoint, requires `instance-admin`).
When this flag is present, the mutation proceeds without a pre-mutation
snapshot and a `WARN` log entry is emitted:

```
WARN pre-mutation snapshot skipped by operator request;
     mutation kind=SM1_PURGE scope=containers:[528098] user=fkrebs
```

The `--no-snapshot` flag is an operator-controlled bypass, not a
silent default. Using it in a script without explicit documentation
in the script's comment header is a code-review finding.

---

## 5. Failure behaviour — snapshot fails → abort the mutation

If the pre-mutation snapshot fails for any substrate in the scope, the
mutation MUST NOT proceed. This is not a best-effort secondary write;
it is a hard gate.

The failure behaviour is:

1. `SnapshotService.takePreMutationSnapshot(scope)` throws
   `SnapshotFailedException` if any substrate primitive fails.
2. The calling mutation pathway catches `SnapshotFailedException`,
   logs the error with full scope detail, and returns an error to
   the caller (HTTP 500 with RFC 7807 body, or a non-zero exit code
   from the CLI).
3. No mutation operation (DELETE, UPDATE, DROP) is issued to any
   substrate. The system remains in its pre-mutation state.
4. The partially-created snapshot artefacts (if any substrates
   succeeded before the failure) are cleaned up by a background
   garbage-collect pass within 5 minutes.

The rationale: a partial snapshot is worse than no snapshot, because
it may give the operator false confidence that the scope is covered.
The clean failure posture is "no snapshot, no mutation."

### 5.1 Transient vs permanent failures

`SnapshotService` distinguishes between transient failures (network
timeout to Garage, temporary TimescaleDB lock contention) and
permanent failures (insufficient disk space, APOC not installed). For
transient failures, a single automatic retry is attempted after a 5-
second back-off. If the retry also fails, the exception is propagated
as in the main flow above.

### 5.2 What "snapshot fails" looks like to an operator

The operator sees a clear error from the CLI or REST response:

```
ERROR: Pre-mutation snapshot failed for scope containers:[528098, 589657, 599903].
Substrate: timescaledb
Reason: INSERT INTO snapshots.timeseries_data_points_... failed —
  no space left on device (available: 2.1 GB, required estimate: 8.4 GB)
Mutation aborted. No data has been modified.
To bypass this gate (DANGEROUS): re-run with --no-snapshot and document why.
```

The error message always includes:
- which substrate failed
- the human-readable reason
- explicit confirmation that no data was modified
- the `--no-snapshot` escape hatch and a warning that it is dangerous

---

## 6. Cross-substrate restore order

When restoring from a pre-mutation snapshot, substrates must be
restored in the following order to preserve referential integrity:

1. **Neo4j** — restore the subgraph first. Neo4j is the primary
   identity and relationship substrate. References in TimescaleDB,
   MongoDB, and Garage are keyed to Neo4j entity IDs or `appId`
   values. Restoring Neo4j first means that subsequent substrate
   restores can validate foreign-key consistency.

2. **TimescaleDB** — restore the time-series data. TimescaleDB rows
   reference `container_id` values that must exist in Neo4j (which
   was just restored in step 1).

3. **MongoDB** — restore the document collections. Mongo documents
   reference `containerId` values in the same pattern as TimescaleDB.

4. **Garage / S3** — verify or restore file objects. For mutations
   that deleted source objects (e.g. FS1e), copy back from the
   destination bucket or from tape. For mutations that did not delete
   source objects, verify current object integrity against the
   pre-mutation manifest.

The restore order is documented in the operator runbook (PRE-MUT-SNAP4
target: `docs/admin/runbooks/rollback-from-snapshot.md`).

If a partial restore is detected (Neo4j restored but TimescaleDB
restore failed), the system emits a `WARN` and leaves the instance in
a known-partially-restored state rather than attempting a rollback of
the already-applied Neo4j restore. The operator must complete the
remaining substrates manually, following the runbook.

---

## 7. Implementation plan

This section summarises the follow-on work items from `aidocs/16
§PRE-MUT-SNAP`. The present doc (PRE-MUT-SNAP1) is the contract
definition; the items below build the mechanism.

| ID | Slice | Deliverable |
|---|---|---|
| **PRE-MUT-SNAP2** | Substrate primitives | `de.dlr.shepard.ops.SnapshotService` Java class implementing the per-substrate snapshot operations described in §3.3. REST surface: `POST /v2/admin/snapshots` (admin-gated, `instance-admin` role). |
| **PRE-MUT-SNAP3** | Gate wiring | Wire `SnapshotService.takePreMutationSnapshot()` into every mutation pathway listed in §2: SM1 purgeOrphans, FS1e file-move, migration runner, `shepard-admin` data-mutating commands, and the MFFD showcase orphan-wipe scripts. Default: gate on. |
| **PRE-MUT-SNAP4** | Rollback runbook | `docs/admin/runbooks/rollback-from-snapshot.md`. Single-page operator procedure per the `feedback_admin_runbooks_pattern.md` contract: numbered steps, host indicators, expected stdout, end-state verification. Includes the §6 restore order. |
| **PRE-MUT-SNAP5** | Acceptance test | Apply the mechanism to the deferred MFFD-tapelaying consolidation (528098 keep, 589657 + 599903 delete). This is the end-to-end acceptance test: snapshot-first, then DELETE, then confirm the snapshot artefact is navigable via `GET /v2/snapshots/{appId}`. |

PRE-MUT-SNAP2 is the unblocking dependency. PRE-MUT-SNAP3, PRE-MUT-SNAP4,
and PRE-MUT-SNAP5 can proceed in parallel once PRE-MUT-SNAP2 ships.

### 7.1 `:SnapshotConfig` singleton

PRE-MUT-SNAP2 ships a `:SnapshotConfig` Neo4j singleton with the
following runtime-mutable fields:

```
graceWindowDays:    int      (default 90)
sweepScheduleCron:  string   (default "0 3 * * *")
autoRetryOnTransientFailure: boolean (default true)
retryBackoffSeconds: int     (default 5)
```

Seeded from `application.properties` keys in the
`shepard.v2.snapshots.*` namespace. Admin REST surface:
`GET/PATCH /v2/admin/snapshots/config`.

### 7.2 `snapshots-system` collection bootstrap

The `snapshots-system` collection is created by a Neo4j migration
(`V(N+1)__Bootstrap_snapshots_system_collection.cypher`) on first
startup. It is a singleton: the migration is idempotent
(`MERGE ... ON CREATE SET ...`). The collection is marked with a
`systemCollection: true` property; the UI and API gate all
destructive operations behind `instance-admin`.

The `snapshots` PostgreSQL/TimescaleDB schema is created by a Flyway
migration (`V(N+1)__Create_snapshots_schema.sql`):

```sql
CREATE SCHEMA IF NOT EXISTS snapshots;
REVOKE CREATE ON SCHEMA snapshots FROM PUBLIC;
GRANT ALL ON SCHEMA snapshots TO shepard_admin;
```

---

## 8. Cross-references

- **`aidocs/data/41-snapshots-design.md`** — the snapshot entity model
  (`:Snapshot`, `:SnapshotEntry`, `revision` field on
  `VersionableEntity`). This doc depends on V2b being shipped before
  PRE-MUT-SNAP2 can build on it.
- **`aidocs/16 §PRE-MUT-SNAP`** — the backlog rows for the full
  mechanism (PRE-MUT-SNAP1 through PRE-MUT-SNAP5).
- **`aidocs/16 §TSDB-DDL-3`** — the specific first acceptance test:
  the tapelaying-duplicate consolidation that should go through the
  new path once PRE-MUT-SNAP1..4 are ready.
- **`docs/admin/runbooks/orphan-retention-policy.md`** — the SM1
  orphan-retention runbook; the purge pathway described there is one
  of the gates PRE-MUT-SNAP3 will wire into.
- **CLAUDE.md §"Always: surface operator knobs in the admin config"**
  — the `:SnapshotConfig` singleton and admin REST surface pattern.
- **CLAUDE.md §"Always: secondary writes are fire-and-forget"** — note
  that the pre-mutation snapshot is NOT a secondary write; it is a hard
  gate on the primary mutation. The snapshot service must NOT be
  wrapped in a try/catch that continues on failure.
