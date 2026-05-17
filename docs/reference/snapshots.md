# Snapshots reference

**Feature ID:** V2b  
**Design doc:** `aidocs/data/41-snapshots-design.md`  
**API surface:** `/v2/` (this fork's development surface; upstream `/shepard/api/...` untouched)

---

## Overview

A **Snapshot** is a point-in-time, immutable record of every `VersionableEntity`
reachable from a Collection at snapshot-creation time. For each entity the
snapshot records its `appId` and the `revision` counter it held at that instant.
Subsequent writes to live entities do not affect the snapshot.

Snapshots power reproducible workflows: an audit tool or RO-Crate export
can cite a `snapshotAppId` and recover exactly which entities — and which
revision of each — existed at the time of the snapshot.

### What is captured

The creation walk traverses `(c:Collection)-[*0..15]->(e:VersionableEntity)`,
following any relationship type. One `SnapshotEntry` is created per distinct
`VersionableEntity` node that already carries an `appId` (i.e. was created or
touched after the L2a migration). Entities without an `appId` are silently
skipped.

### What is not captured

- Payload bytes (files, timeseries, MongoDB documents) — these are immutable by
  construction (append-only timescale rows, MongoDB OIDs, S3 presigned objects).
  The snapshot records which revision of a reference points at which payload.
- Permissions — snapshots are read via the same permission check as the live tree.

---

## Endpoints

### Create a snapshot

```
POST /v2/collections/{collectionAppId}/snapshots
```

**Permission:** Write on the Collection.

**Request body:**
```json
{
  "name": "v1.0 — campaign close",
  "description": "State of the dataset as submitted to the journal."
}
```
`name` is required and must be non-blank. `description` is optional.

**Response (201 Created):**
```json
{
  "appId": "01900000-0000-7000-8000-000000000020",
  "name": "v1.0 — campaign close",
  "description": "State of the dataset as submitted to the journal.",
  "snapshotCapturedAt": "2026-05-17T14:00:00Z",
  "snapshotCreatedByUsername": "alice",
  "collectionAppId": "01900000-0000-7000-8000-000000000010",
  "entryCount": 42
}
```

**Error responses:** 400 (missing name), 401 (unauthenticated), 403 (no Write permission), 404 (unknown Collection).

---

### List snapshots for a Collection

```
GET /v2/collections/{collectionAppId}/snapshots
```

**Permission:** Read on the Collection.

**Response (200 OK):** Array of snapshot objects (newest first). Empty array when no snapshots exist.

---

### Read snapshot metadata

```
GET /v2/snapshots/{snapshotAppId}
```

**Permission:** Read on the root Collection.

Returns the same shape as the create response (name, description, timestamps, entryCount). Does not include the full manifest — use the manifest endpoint for that.

---

### Read the full manifest

```
GET /v2/snapshots/{snapshotAppId}/manifest
```

**Permission:** Read on the root Collection.

**Response (200 OK):** Array of `{entityAppId, revision}` pairs, ordered by `entityAppId` ascending for deterministic diff tooling.

```json
[
  { "entityAppId": "01900000-0000-7000-8000-000000000030", "revision": 3 },
  { "entityAppId": "01900000-0000-7000-8000-000000000040", "revision": 7 }
]
```

---

### Delete a snapshot

```
DELETE /v2/snapshots/{snapshotAppId}
```

**Permission:** Write on the root Collection.

**Response (204 No Content)** on success. Soft-deletes the `Snapshot` node and all associated `SnapshotEntry` rows. The underlying entity data (Collections, DataObjects, references, payloads) is unaffected.

---

### Diff two snapshots

```
GET /v2/snapshots/{aAppId}/diff/{bAppId}
```

**Permission:** Authenticated caller required (401 when not authenticated). In v1 no per-collection permission gate is applied — snapshots are metadata-only reads.

**Path parameters:**
- `aAppId` — the base snapshot (the older one, A).
- `bAppId` — the head snapshot (the newer one, B).

**Error responses:**
- 400 — when `aAppId` equals `bAppId` (self-diff is not useful).
- 401 — unauthenticated.
- 404 — when either snapshot is not found or is soft-deleted.

**Response (200 OK):**
```json
{
  "snapshotAAppId": "01900000-0000-7000-8000-000000000020",
  "snapshotBAppId": "01900000-0000-7000-8000-000000000021",
  "snapshotACapturedAtMs": 1700000000000,
  "snapshotBCapturedAtMs": 1700001000000,
  "added": [
    "01900000-0000-7000-8000-000000000050"
  ],
  "removed": [
    "01900000-0000-7000-8000-000000000031"
  ],
  "changed": [
    {
      "entityAppId": "01900000-0000-7000-8000-000000000040",
      "revisionA": 1,
      "revisionB": 3
    }
  ],
  "unchangedCount": 38
}
```

Fields:
- `added` — `entityAppId` values present in B but not in A (created between snapshots). Sorted by `entityAppId` ascending.
- `removed` — `entityAppId` values present in A but not in B (deleted or out of scope in B). Sorted by `entityAppId` ascending.
- `changed` — entities present in both with a different `revision` value. Each entry carries `entityAppId`, `revisionA` (from A), and `revisionB` (from B). Sorted by `entityAppId` ascending.
- `unchangedCount` — count of entities with identical revisions in both snapshots (not listed to avoid large payloads).

---

## Data model

```
(:Snapshot {
  appId,                   // UUID v7
  name,                    // user label
  description,             // optional
  snapshotCapturedAtMs,    // epoch millis (frozen at creation)
  snapshotCreatedByUsername, // frozen at creation
  entryCount,              // number of SnapshotEntry rows
  deleted                  // soft-delete flag
})-[:SNAPSHOT_OF]->(:Collection)

(:SnapshotEntry {
  appId,         // UUID v7
  entityAppId,   // appId of the pinned VersionableEntity
  revision       // revision counter at snapshot time
})-[:ENTRY_OF]->(:Snapshot)
```

## Schema migration

`V40__Add_appId_constraint_Snapshot.cypher` — adds a unique constraint on
`:Snapshot(appId)`. Idempotent; runs automatically on startup.

## Upgrade path

See `aidocs/34-upstream-upgrade-path.md` V2b row. Impact: **ZERO** — all new
nodes and endpoints; no change to the upstream `/shepard/api/...` surface.

## Planned follow-on slices

| Slice | Description |
|---|---|
| V2c | Snapshot-pinned read path (`?snapshot=snapshotAppId` query param on Collection reads). |
| V2d | RO-Crate export against a snapshot — reproducible by construction. |
| V2f | (Deferred) "Branch from snapshot" — fork off a snapshot into a writable child Collection. |
