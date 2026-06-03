---
stage: concept
last-stage-change: 2026-06-03
---

# Pre-mutation snapshot contract

**Backlog row:** PRE-MUT-SNAP1  
**Companion rows:** PRE-MUT-SNAP2 (substrate primitives), PRE-MUT-SNAP3 (gate
every pathway), PRE-MUT-SNAP4 (rollback runbook), PRE-MUT-SNAP5 (MFFD tapelaying
acceptance test)  
**Status:** concept — no code shipped yet  
**Date:** 2026-06-03

---

## 1. Policy statement

**Every destructive mutation pathway in Shepard MUST take a pre-mutation snapshot
of the affected scope BEFORE the first destructive operation.**

A "destructive mutation" is any operation that removes or overwrites data in a
way that cannot be trivially undone by the normal REST API. This includes, but is
not limited to:

- Bulk deletion of graph nodes (Neo4j DETACH DELETE, soft-delete sweeps)
- TimescaleDB chunk drops or row deletion (including `drop_chunks`)
- MongoDB collection drops, document purges, or `$out` overwrites
- Garage/S3 object deletion or bucket lifecycle expiry of live objects
- File storage migration that overwrites a provider pointer before the source
  is verified (FS1e)
- Schema backfills that rewrite existing rows with a new shape

The rule is absolute for automated pathways. Human-initiated one-off Cypher
sessions at `cypher-shell` are encouraged to follow the rule; the tooling makes
it easy to do so.

**The `--no-snapshot` escape hatch** (§4) is available for operators who have a
clear reason to skip. Every skip is audit-logged as a `SnapshotSkippedActivity`.
The default is always: take the snapshot first.

---

## 2. Definition of "pre-mutation snapshot"

A pre-mutation snapshot is an artefact that captures, at minimum, enough state
to answer the question "what did the affected data look like immediately before
the mutation ran?"

### 2.1 Scope

The scope is the **substrate slice** that would be mutated, expressed as the
union of:

| Substrate | Scope expression | Example |
|---|---|---|
| Neo4j | A Cypher `MATCH` clause that returns every node and relationship that will be touched | `MATCH (c:TimeseriesContainer) WHERE c.id IN $ids` |
| TimescaleDB | A `SELECT *` scope over the rows to be deleted / rewritten | `SELECT * FROM timeseries_data_points WHERE container_id = ANY($ids)` |
| MongoDB | An aggregation `$match` that mirrors the delete/update filter | `{containerMongoId: {$in: ids}}` |
| Garage/S3 | The list of object keys (OIDs) that will be deleted or overwritten | `[oid1, oid2, ...]` |

A snapshot that covers only one substrate when the operation touches several is
incomplete. If a migration touches Neo4j pointers AND the underlying Garage
objects, both slices must be snapshotted.

### 2.2 What "snapshot" means per substrate

The substrate-native primitives are specified in **PRE-MUT-SNAP2**. In summary:

- **Neo4j** — APOC Cypher export of the matched subgraph to a Garage artefact
  (`apoc.export.cypher.query`). The artefact is stored at
  `snapshots/<snapshotAppId>/neo4j-subgraph.cypher`.
- **TimescaleDB** — `INSERT INTO snapshots.<table>_snap SELECT * FROM <table>
  WHERE <scope>` using a per-session schema, OR a `COPY` dump to a Garage artefact.
- **MongoDB** — `db.<coll>.aggregate([{$match: scope}, {$out:
  'snapshots.<coll>_<sessionId>'}])` to a side collection retained for the
  grace window.
- **Garage/S3** — copy the object bytes (via `GET` + `PUT`) into a
  `snapshots/<snapshotAppId>/` prefix before deletion.

Each substrate primitive returns a `SnapshotResult` record:

```
{
  substrate:    "neo4j" | "timescaledb" | "mongodb" | "garage",
  scope:        string (human-readable description of what was captured),
  rowCount:     long (rows / nodes / objects),
  sizeBytes:    long,
  artefactUri:  string (Garage URI or TimescaleDB schema+table),
  sha256:       string (SHA-256 of the artefact bytes; hex lower-case),
  durationMs:   long
}
```

---

## 3. Artefact shape in Shepard

After the substrate-native captures complete, a **Shepard-native artefact** is
written to the graph so the snapshot is queryable like any other Shepard entity.

### 3.1 Dedicated system collection

The artefact is a `:Snapshot` node under a dedicated Collection named
`__snapshots-system`. This collection is:

- Created automatically on first use by the `SnapshotGateService`.
- Owned by the `system` account; readable by `instance-admin` role only.
- Not shown in the normal collection list (hidden via a `system: true` flag on
  the `:Collection` node, to be added in PRE-MUT-SNAP2).

### 3.2 Node structure

A pre-mutation snapshot produces the following Neo4j nodes:

```
(:Snapshot {
  appId:                      // UUID v7 — the stable identifier
  name:                       // "<pathwayId> pre-mutation <ISO-timestamp>"
  description:                // human-readable summary of what was snapshotted
  snapshotCapturedAtMs:       // epoch millis (frozen)
  snapshotCreatedByUsername:  // caller identity (frozen)
  entryCount:                 // number of SnapshotEntry rows
  mutationPathwayId:          // e.g. "FS1e", "SM1-orphan-collect", "ADMIN-NUKE"
  scopeSummary:               // JSON string — one entry per substrate with scope expr
  manifestHash:               // SHA-256 of the JSON manifest (see §3.3)
  noSnapshotReason:           // null for real snapshots; set on SnapshotSkippedActivity
})-[:SNAPSHOT_OF]->(:Collection {name: "__snapshots-system"})

// One SnapshotEntry per VersionableEntity in the Neo4j scope slice:
(:SnapshotEntry {
  appId:        // UUID v7
  entityAppId:  // appId of the pinned node
  revision:     // revision at snapshot time
})-[:ENTRY_OF]->(:Snapshot)
```

The non-Neo4j substrate artefacts (TimescaleDB side-table, Garage export) are
referenced via `scopeSummary` — a JSON array of `SnapshotResult` objects (§2.2).
They are not stored as additional graph nodes in v1 of this design; a future
PRE-MUT-SNAP2 slice may introduce `:SnapshotArtefact` as a first-class node.

### 3.3 Manifest hash

The `manifestHash` field is the SHA-256 of the canonicalised JSON manifest:

```json
{
  "snapshotAppId":     "<uuid>",
  "capturedAtMs":      1234567890000,
  "pathwayId":         "FS1e",
  "substrates": [
    {
      "substrate":   "neo4j",
      "scope":       "MATCH (f:ShepardFile) WHERE f.providerId = 'garage-src'",
      "rowCount":    4200,
      "sizeBytes":   98304,
      "artefactUri": "garage://snapshots/abc123/neo4j-subgraph.cypher",
      "sha256":      "deadbeef..."
    }
  ]
}
```

The manifest is serialised with sorted keys and no trailing whitespace. The
`manifestHash` lets an operator verify at restore time that the artefact has
not been tampered with.

### 3.4 PROV-O attribution

A `:Activity` node is created alongside the snapshot (per the PROV-O pattern in
`aidocs/workflows/55-provenance-and-activity-overhaul.md` and
`aidocs/workflows/64-provenance-architecture.md`):

```
(:Activity {
  appId:           // UUID v7
  kind:            "PreMutationSnapshot"
  startedAtMillis: // epoch millis
  endedAtMillis:   // epoch millis (after all substrate captures)
  pathwayId:       // e.g. "FS1e"
  triggeredBy:     // username or "system"
  snapshotAppId:   // appId of the created :Snapshot
})-[:WAS_ASSOCIATED_WITH]->(:User)
 -[:GENERATED]->(:Snapshot)
```

This Activity is written by the handler calling `ProvenanceService.record()`, with
`PROP_SKIP_CAPTURE` set on the request context so the `ProvenanceCaptureFilter`
does not emit a second generic Activity (per the skip-capture handoff rule in
`CLAUDE.md`).

---

## 4. The `--no-snapshot` escape hatch

Operators and automated pathways MAY skip the snapshot by passing
`--no-snapshot` (CLI) or `noSnapshot: true` (REST/API) to the mutation entry
point.

Rules for a skip:

1. **The skip must be explicit.** There is no implicit skip. Default behaviour
   is always: take the snapshot.
2. **Every skip is audit-logged.** A `SnapshotSkippedActivity` `:Activity` node
   is written regardless of whether a snapshot was taken:

   ```
   (:Activity {
     kind:            "SnapshotSkipped"
     pathwayId:       // mutation pathway
     triggeredBy:     // caller
     noSnapshotReason:// reason string, required when skip=true
     startedAtMillis: // epoch millis
   })
   ```

3. **A reason string is required.** The CLI must accept `--no-snapshot-reason=<text>`;
   the REST body must include `noSnapshotReason: "<text>"`. If omitted, the
   pathway rejects the request with HTTP 400 (CLI: non-zero exit).
4. **A skip does NOT bypass the operator confirmation gate** (§5). The operator
   must confirm the mutation even when skipping the snapshot.
5. **Dry-run mode is unaffected.** `--dry-run` never takes a snapshot and never
   requires `--no-snapshot`.

---

## 5. Operator confirmation gate

Every mutation pathway covered by this contract presents an operator confirmation
step before proceeding. This is separate from the snapshot.

**CLI pattern:**

```
shepard-admin storage migrate \
  --src garage-src --dest garage-dest \
  [--no-snapshot --no-snapshot-reason "already snapshotted manually"]

Snapshot scope: 4 200 ShepardFile nodes + 4 200 Garage objects
Estimated snapshot duration: ~45 s
Snapshot artefact: snapshots/01JXQ3.../neo4j-subgraph.cypher (Garage)

Confirm mutation? [y/N]:
```

**REST pattern:** the mutation endpoint returns HTTP 202 with a `planId` (per
the importer's plan-seal pattern). The snapshot is taken as part of creating the
plan. A subsequent `POST /v2/admin/<feature>/confirm/{planId}` commits the
mutation.

Both patterns ensure the operator has seen the snapshot scope and manifest hash
before the first destructive operation runs.

---

## 6. Retention window

Pre-mutation snapshot artefacts are retained for a configurable grace window:

| Config key | Default | Description |
|---|---|---|
| `shepard.mutations.snapshot.retention-days` | `90` | Days to retain pre-mutation snapshot artefacts (Neo4j nodes + substrate side-tables + Garage exports). Set to `-1` for indefinite retention. |

The retention sweep is handled by the SM1 orphan-retention scheduler once it
ships. Until SM1a lands, snapshots are retained indefinitely and must be pruned
manually by an operator using `DELETE /v2/snapshots/{appId}`.

The `__snapshots-system` collection itself is exempt from the normal SM1 orphan
retention rules — it uses only the `shepard.mutations.snapshot.retention-days`
clock.

---

## 7. Per-pathway compliance table

The following table catalogues every known destructive mutation pathway in this
codebase as of 2026-06-03 and its compliance status against this contract.

| Pathway ID | Entry point | Status | Notes |
|---|---|---|---|
| **FS1e** | `POST /v2/admin/files/migrate` → `FileMigrationService.triggerMigration()` (`storage/migration/FileMigrationService.java`) | **TO GATE** (PRE-MUT-SNAP3) | Copies `:ShepardFile` rows from one storage adapter to another and flips `providerId`. Pre-mutation snapshot scope = all `:ShepardFile` nodes whose `providerId = $src` + corresponding Garage object keys. |
| **FS1e-ROLLBACK** | `POST /v2/admin/files/migrate/rollback/{appId}` → `FileMigrationRest.rollback()` | **TO GATE** (PRE-MUT-SNAP3) | Reverses a single migrated file. Snapshot scope = the single `:ShepardFile` node + its Garage object at the new key. |
| **SM1-orphan-collect** | SM1a scheduled sweep (not yet shipped; backlog row SM1a) | **TO GATE when shipped** (PRE-MUT-SNAP3) | Deletes non-referenced payload rows from TimescaleDB / MongoDB / Garage after the retention window expires. Snapshot scope = all rows matching the sweep's `WHERE` clause. |
| **ADMIN-STALE-CH** | `DELETE /v2/admin/timeseries/channels/stale` (ADMIN-STALE-CH sub-row; not yet shipped) | **TO GATE when shipped** (PRE-MUT-SNAP3) | Bulk-deletes stale timeseries channel rows in TimescaleDB + Neo4j shadow nodes. |
| **ADMIN-NUKE** | `POST /v2/admin/instance/nuke` → `NukeService.nuke()` | **EXEMPT — see notes** | Full-instance wipe. Snapshot scope would be the entire instance — prohibitively large and redundant with the backup an operator should have. Documented exemption: operator must confirm with `confirmPhrase` + a separate `preNukeBackupConfirmed: true` flag (to be added in PRE-MUT-SNAP3). |
| **MFFD-PRUNE** | `examples/mffd-showcase/scripts/` + one-off Cypher sessions | **TO GATE** (PRE-MUT-SNAP3) | Ad-hoc graph debris cleanup. Snapshot scope = matched nodes. Superseded for the specific 2026-05-23 debris (MFFD-PRUNE-1..4 are all superseded); rule applies to future instances. |
| **NEO4J-MIGRATION-BACKFILLS** | `V(N)__*.cypher` migration files that use `SET` or `DETACH DELETE` | **EXEMPT** | Migrations run at startup before the SnapshotService is available; they are guarded by `IF NOT EXISTS` and rollback twins (`V(N)_R__*`), which serve the same safety purpose. |
| **TSDB-DDL-3 / drop_chunks** | `TimescaleDB drop_chunks(...)` for pruned container ids (backlog row TSDB-DDL-3) | **TO GATE** (PRE-MUT-SNAP3) | Cross-references PRE-MUT-SNAP5. Snapshot scope = the TimescaleDB rows for the container ids being dropped. |
| **SM1a-RetentionSweep** | Scheduled retention sweep (SM1a; not yet shipped) | **TO GATE when shipped** (PRE-MUT-SNAP3) | Each sweep cycle snapshot the expiring rows before `DELETE`. Use a micro-snapshot (scope = expiring batch only, not the entire container). |
| **MFFD-WIKI mutations** | `examples/mffd-showcase/scripts/` sub-rows for MFFD-WIKI-A..E | **TO GATE** (PRE-MUT-SNAP3) | User directive 2026-05-28: bracket each apply step with a snapshot of the MFFD synthetic showcase collection before mutation. |

### 7.1 Exempt pathway criteria

A pathway is EXEMPT from the pre-mutation snapshot requirement when ALL of:

1. The operation is a full-instance wipe (nuke) where a snapshot would cover
   the entire dataset and is redundant with a mandatory operator backup.
2. The operation is a startup migration script that runs before any service
   is available, guarded by a rollback twin file.
3. The operation is a dry-run or read-only operation.

Any pathway claiming EXEMPT status must document the reason explicitly here
(not just "N/A"). Reviewers should push back on undocumented exemptions.

---

## 8. Implementation sketch (for PRE-MUT-SNAP2 + PRE-MUT-SNAP3)

This section is non-normative guidance for the PRE-MUT-SNAP2 implementer.

### 8.1 `SnapshotGateService` (new, Java)

A new `@ApplicationScoped` bean at `de.dlr.shepard.ops.SnapshotGateService`
wraps the per-substrate snapshot primitives (PRE-MUT-SNAP2) and the
`__snapshots-system` artefact write (§3).

```java
public record MutationScope(
  String pathwayId,
  String triggeredBy,
  String neo4jScopeCypher,          // nullable
  String timescaledbScopeQuery,     // nullable
  String mongodbScopeMatch,         // nullable
  List<String> garageObjectKeys,    // nullable
  boolean noSnapshot,
  String noSnapshotReason           // required when noSnapshot=true
) {}

public record SnapshotGateResult(
  String snapshotAppId,             // null when noSnapshot=true
  boolean skipped,
  List<SnapshotResult> substrates,
  String manifestHash
) {}

public SnapshotGateResult gate(MutationScope scope);
```

Callers:

1. Build a `MutationScope`.
2. Call `SnapshotGateService.gate(scope)`.
3. On `SnapshotGateResult.skipped() = false`: proceed with mutation.
4. On `SnapshotGateResult.skipped() = true` (operator passed `--no-snapshot`):
   confirm the `noSnapshotReason` was logged; proceed.
5. On exception from `gate()`: abort the mutation. Do NOT proceed with
   destructive operations if the snapshot failed.

### 8.2 REST surface

```
POST /v2/admin/mutations/snapshot
```

Body: `MutationScopeIO` (pathway, triggeredBy, substrate scopes, noSnapshot flag).  
Response: `SnapshotGateResultIO` (snapshotAppId, skipped, manifestHash).

This endpoint is consumed by the CLI and can be called independently of a specific
mutation pathway — useful for an operator who wants to take a manual pre-mutation
snapshot before a one-off Cypher session.

---

## 9. References

| Reference | Description |
|---|---|
| `aidocs/16-dispatcher-backlog.md §PRE-MUT-SNAP` | Backlog rows PRE-MUT-SNAP1..5 |
| `aidocs/data/41-snapshots-design.md` | V2b snapshot design — the underlying `:Snapshot` / `:SnapshotEntry` entity model this contract reuses |
| `aidocs/workflows/55-provenance-and-activity-overhaul.md` | PROV-O Activity design |
| `aidocs/workflows/64-provenance-architecture.md` | Live provenance architecture reference |
| PRE-MUT-SNAP2 | Substrate-native snapshot primitives (to be designed) |
| PRE-MUT-SNAP4 | Rollback runbook: `docs/admin/runbooks/rollback-from-snapshot.md` |
| `project_snapshot_boundaries.md` | Existing memory: "every larger transformation gets a snapshot pair" |
| `feedback_referenced_data_infinite_retention.md` | Kill-switch criterion: referenced data is never deleted regardless of retention policy |
| `aidocs/ops/vis-s2-garage-omezarr-storage-policy.md` | Garage storage policy — bucket layout for snapshot artefacts |
