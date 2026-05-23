---
audience: user
---

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

### List DataObjects captured in a snapshot (V2c)

```
GET /v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects
```

**Permission:** Read on the Collection.

Returns the list of DataObject `appId` strings that were captured in the snapshot, filtered from the full `SnapshotEntry` set to only those `entityAppId` values that resolve to a live (non-deleted) `:DataObject` node. Collections, References, and soft-deleted entities are excluded.

**Path parameters:**
- `collectionAppId` — the `appId` of the root Collection.
- `snapshotAppId` — the `appId` of the snapshot.

**Response (200 OK):**
```json
{
  "snapshotAppId": "01900000-0000-7000-8000-000000000020",
  "collectionAppId": "01900000-0000-7000-8000-000000000010",
  "snapshotName": "v1.0 — campaign close",
  "snapshotCapturedAt": "2026-05-17T14:00:00Z",
  "totalEntries": 42,
  "dataObjectAppIds": [
    "01900000-0000-7000-8000-000000000030",
    "01900000-0000-7000-8000-000000000040"
  ]
}
```

`totalEntries` is the full entry count (includes Collections, References, etc.); `dataObjectAppIds.length` may be less than `totalEntries`.

**Error responses:**
- 401 — unauthenticated.
- 403 — caller lacks Read permission on the Collection.
- 404 — no Collection or Snapshot with that `appId`.
- 409 — the snapshot does not belong to the specified Collection (ownership mismatch).

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

| Slice | Status | Description |
|---|---|---|
| V2c | **shipped** | `GET /v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects` — DataObject list at snapshot time (documented above). |
| V2d | queued | RO-Crate export against a snapshot — reproducible by construction. |
| V2e | queued | Snapshot diff (`GET /v2/snapshots/{a}/diff/{b}` — entities added/removed/changed). |
