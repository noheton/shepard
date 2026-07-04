---
title: Import API — reference
audience: user
---

# Import API — reference

## Overview

The import system provides a **validate → execute** workflow for batch-creating
DataObjects, Containers, and Container-to-DataObject links within a Collection.

1. **Validate** (`POST /v2/import/validate`) — dry-run; returns a plan seal
   (`commitId`) that must be presented to execute.
2. **Execute** (`POST /v2/import/jobs`) — consumes the commitId and performs
   the actual writes atomically.

Supporting primitives:
- **Import lock** (`/v2/import/lock/*`) — per-collection mutex preventing
  concurrent imports.
- **Import diagnostics** (`/v2/import/diagnostics/*`, `GET /v2/import/runs`) —
  structured event log for monitoring import runs.
- **Import context** (`GET /v2/import/context`) — lightweight Collection
  snapshot for automated importers.

---

## Endpoints

### `POST /v2/import/validate`

Validate an import manifest.  Requires **Write** permission on the target
Collection.

**Request body** (`application/json`):

```json
{
  "collectionAppId": "019e3c96-0000-7000-a000-000000000001",
  "dataObjects": [
    {
      "localRef": "do-1",
      "name": "AFP run #42",
      "description": "Robot layup, ply 3",
      "status": "DRAFT",
      "attributes": { "machine": "AFP-7", "material": "CFRP" },
      "parentRef": null,
      "predecessorRefs": []
    }
  ],
  "containers": [
    {
      "localRef": "c-ts",
      "type": "TIMESERIES",
      "name": "TCP temperature"
    }
  ],
  "references": [
    { "dataObjectRef": "do-1", "containerRef": "c-ts" }
  ]
}
```

**Fields:**

| Field | Required | Description |
|---|---|---|
| `collectionAppId` | yes | AppId (UUID v7) of the target Collection |
| `dataObjects` | yes | List of DataObjects to create (at least one) |
| `containers` | no | Containers to create |
| `references` | no | Container-to-DataObject associations |

**DataObject fields:**

| Field | Required | Description |
|---|---|---|
| `localRef` | yes | Caller-defined key used only within this manifest |
| `name` | yes | Display name for the DataObject |
| `description` | no | Free-text description |
| `status` | no | One of `DRAFT`, `IN_REVIEW`, `READY`, `PUBLISHED`, `ARCHIVED` |
| `attributes` | no | Key/value metadata map |
| `parentRef` | no | `localRef` of the DataObject to set as parent (must be in this manifest) |
| `predecessorRefs` | no | List of `localRef` values to set as predecessors (must be in this manifest) |

**Container fields:**

| Field | Required | Description |
|---|---|---|
| `localRef` | yes | Caller-defined key used only within this manifest |
| `type` | yes | One of `TIMESERIES`, `FILE`, `STRUCTURED_DATA` |
| `name` | yes | Display name for the Container |

**Success response** (200 OK):

```json
{
  "commitId": "sha256:a1b2c3...",
  "status": "VALID",
  "expiresAt": "2026-05-22T15:30:00Z",
  "summary": {
    "wouldCreateDataObjects": 1,
    "wouldCreateContainers": 1,
    "wouldCreateReferences": 1,
    "wouldSkipDataObjects": 0
  },
  "warnings": [],
  "errors": []
}
```

**Error response** (422 Unprocessable Entity — hard validation errors):

```json
{
  "commitId": null,
  "status": "INVALIDATED",
  "expiresAt": null,
  "summary": { "wouldCreateDataObjects": 0, "wouldCreateContainers": 0, "wouldCreateReferences": 0, "wouldSkipDataObjects": 0 },
  "warnings": [],
  "errors": [
    "Invalid status 'BOGUS' on dataObject 'do-1'",
    "dataObject 'do-2' parentRef 'do-missing' not in manifest"
  ]
}
```

When there are hard errors, `commitId` is `null` and no plan is persisted.

**Other status codes:**

| Code | Meaning |
|---|---|
| 401 | Not authenticated |
| 403 | Caller does not have Write on the target Collection |
| 404 | Collection not found |

---

### `POST /v2/import/jobs`

Execute a previously-validated import plan.  The commitId is **one-shot** —
once this endpoint is called (successfully or not), the plan is marked `USED`
and cannot be re-used.

**Request body** (`application/json`):

```json
{
  "commitId": "sha256:a1b2c3..."
}
```

**Success responses:**

**201 Created** — all entities were created successfully:

```json
{
  "jobAppId": "019f1234-0000-7000-a000-000000000001",
  "planCommitId": "sha256:a1b2c3...",
  "status": "COMPLETED",
  "dataObjects": [
    { "localRef": "do-1", "appId": "019f1234-0000-7000-a000-000000000002", "kind": "DataObject" }
  ],
  "containers": [
    { "localRef": "c-ts", "appId": "019f1234-0000-7000-a000-000000000003", "kind": "TimeseriesContainer" }
  ],
  "errors": []
}
```

**207 Multi-Status** — some entities failed; result body contains per-entity
detail. The `errors` list enumerates failures; successfully-created entities
still appear in `dataObjects` / `containers`.

```json
{
  "jobAppId": "019f1234-0000-7000-a000-000000000001",
  "planCommitId": "sha256:a1b2c3...",
  "status": "PARTIAL_FAILURE",
  "dataObjects": [],
  "containers": [],
  "errors": ["Failed to create DataObject 'do-1': duplicate name detected"]
}
```

**Response fields:**

| Field | Description |
|---|---|
| `jobAppId` | UUID v7 minted per execution (for diagnostics correlation) |
| `planCommitId` | The commitId that was consumed |
| `status` | `COMPLETED` or `PARTIAL_FAILURE` |
| `dataObjects` | List of successfully-created DataObjects with their appIds |
| `containers` | List of successfully-created Containers with their appIds |
| `errors` | Empty on full success; per-entity error messages on partial failure |

**`kind` values** in the entity lists:

| kind | Entity type |
|---|---|
| `DataObject` | DataObject |
| `FileContainer` | FILE-type Container |
| `TimeseriesContainer` | TIMESERIES-type Container |
| `StructuredDataContainer` | STRUCTURED_DATA-type Container |
| `FileReference` | link between DataObject and FileContainer |
| `TimeseriesReference` | link between DataObject and TimeseriesContainer |
| `StructuredDataReference` | link between DataObject and StructuredDataContainer |

**Error responses:**

| Code | Meaning |
|---|---|
| 401 | Not authenticated |
| 403 | Caller does not have Write on the target Collection |
| 404 | No plan found for this commitId |
| 409 | Plan already executed (`USED`), collection changed since validation (fingerprint mismatch), or a concurrent import is running |
| 410 | Plan expired (TTL exceeded) or predates IMP2 (re-validate to get a new commitId) |
| 422 | Plan was `INVALIDATED` (had hard validation errors) — no import was run |

**Execution sequence** (for diagnostics / lock monitoring):

1. Resolve the plan from the commitId.
2. Check plan status: `USED → 409`, `INVALIDATED → 422`, `EXPIRED → 410`.
3. Re-check `expiresAt < now` → 410 if stale.
4. Guard against pre-IMP2 plans (no stored manifest) → 410.
5. Verify Write permission on the target Collection → 403.
6. Re-verify the collection fingerprint → 409 if the collection changed since validation.
7. Acquire the import lock → 409 if a concurrent import is running.
8. Execute the manifest.
9. Release the lock; mark the plan `USED`.
10. Best-effort post-import backfill compression (non-blocking).
11. Return 201 (`COMPLETED`) or 207 (`PARTIAL_FAILURE`).

---

### `GET /v2/import/plans/{commitId}`

Retrieve a previously-issued plan by its commitId.  Requires authentication
(no additional collection permission check).

**Response** (200 OK): same shape as the `POST /validate` success response.

**404** — no plan with that commitId.

---

### `GET /v2/import/context`

Returns a lightweight snapshot of a Collection's current state — useful for
automated importers and AI agents that need to generate manifests aligned with
the live collection.

**Query parameters:**

| Parameter | Default | Description |
|---|---|---|
| `collectionAppId` | required | AppId of the Collection to snapshot |
| `includeSemanticGraph` | `false` | When `true`, includes semantic annotations in use within the Collection |

**Response** (200 OK):

```json
{
  "collectionAppId": "019e3c96-0000-7000-a000-000000000001",
  "dataObjectCount": 14,
  "collectionFingerprint": "sha256:d4e5f6...",
  "semanticGraph": null
}
```

When `includeSemanticGraph=true`, `semanticGraph` contains:

```json
{
  "annotations": [
    {
      "appId": "019e3c96-...",
      "propertyName": "Material",
      "valueName": "CFRP",
      "propertyIRI": "https://shepard.dlr.de/ontologies/experiment#Material",
      "valueIRI": "..."
    }
  ]
}
```

**Requires** Read permission on the Collection.  Returns 403 if the caller
lacks it.

---

## Import lock API

The import lock prevents concurrent imports against the same Collection.
`POST /v2/import/jobs` acquires the lock automatically — the lock endpoints
are exposed for monitoring and emergency admin control.

### `GET /v2/import/lock`

Returns the most recent lock regardless of status.  Returns **204** when no
lock has ever been created.

**Response** (200 OK):

```json
{
  "lockId": "019f1234-0000-7000-a000-000000000010",
  "targetCollectionAppId": "019e3c96-0000-7000-a000-000000000001",
  "status": "RUNNING",
  "acquiredBy": "alice",
  "acquiredAt": "2026-05-22T14:00:00Z",
  "lastHeartbeatAt": "2026-05-22T14:02:30Z",
  "completedAt": null,
  "errorMessage": null
}
```

---

### `POST /v2/import/lock`

Acquire a new lock (start of an import run).

**Request body** (`application/json`):

```json
{ "targetCollectionAppId": "019e3c96-0000-7000-a000-000000000001" }
```

Returns **201** with the new lock on success.  Returns **409** if a fresh
lock already exists (heartbeat within the last 5 minutes).  If the existing
lock is stale (> 5 min without heartbeat), it is automatically transitioned
to `ABANDONED` and a new lock is created.

---

### `POST /v2/import/lock/{lockId}/heartbeat`

Extend the lock heartbeat while the import is running.  Call approximately
every 30 seconds.  Returns **200** with the updated lock.

**404** — lock not found or not in `RUNNING` status.

---

### `POST /v2/import/lock/{lockId}/release`

Transition a `RUNNING` lock to `COMPLETED` (normal import completion).

Returns **200** with the updated lock.  **404** if not found or not `RUNNING`.

---

### `POST /v2/import/lock/{lockId}/abandon`

Transition a `RUNNING` lock to `FAILED` (error termination).

**Request body** (`application/json`):

```json
{ "errorMessage": "Upload failed on DataObject do-3: connection reset" }
```

Returns **200** with the updated lock.  **400** if `errorMessage` is missing.
**404** if not found or not `RUNNING`.

---

### `DELETE /v2/import/lock/{lockId}`

Admin cancel: transition a `RUNNING` lock to `CANCELLED`.  Terminal locks
(`COMPLETED`, `FAILED`, `CANCELLED`, `ABANDONED`) are returned as-is.
Requires the **`instance-admin`** role.

Returns **200**.  **403** if caller lacks `instance-admin`.  **404** if lock
not found.

---

### Lock states

| Status | Meaning |
|---|---|
| `RUNNING` | Import is in progress |
| `COMPLETED` | Import finished successfully |
| `FAILED` | Import terminated with an error |
| `CANCELLED` | Admin-cancelled while running |
| `ABANDONED` | Lock became stale (> 5 min without heartbeat) and was superseded |

---

## Import diagnostics API

The diagnostics log holds structured events for each import run.
Events are kept in-memory (not persisted across restarts) and evicted after
24 hours.  The `runId` is the `lockId` from the import lock.

### `GET /v2/import/runs`

List recent run IDs known to the in-memory log, sorted newest first.

**Response** (200 OK):

```json
[
  {
    "runId": "019f1234-0000-7000-a000-000000000010",
    "startedAt": "2026-05-22T14:00:00Z",
    "lastEventAt": "2026-05-22T14:05:12Z",
    "lastLevel": "INFO"
  }
]
```

`lastLevel` is the most-severe level seen: `INFO`, `WARN`, or `ERROR`.

---

### `GET /v2/import/diagnostics/{runId}`

Return all events for a run, ordered oldest-first.  Returns an empty array
when the `runId` is unknown.

**Query parameters:**

| Parameter | Values | Description |
|---|---|---|
| `level` | `INFO`, `WARN`, `ERROR` | Filter to a single severity |
| `phase` | `WARMUP`, `DO_CREATE`, `REF_ATTACH`, `FILE_UPLOAD`, `COMPLETE` | Filter to a single phase |

**Response** (200 OK):

```json
[
  {
    "timestamp": "2026-05-22T14:00:01Z",
    "level": "INFO",
    "phase": "WARMUP",
    "entityAppId": null,
    "message": "Lock acquired; starting import",
    "attributes": {}
  },
  {
    "timestamp": "2026-05-22T14:01:30Z",
    "level": "INFO",
    "phase": "DO_CREATE",
    "entityAppId": "019f1234-0000-7000-a000-000000000002",
    "message": "DataObject created",
    "attributes": { "localRef": "do-1" }
  }
]
```

**400** if the `level` or `phase` query parameter is not a valid value.

---

### `POST /v2/import/diagnostics/{runId}/events`

Ingest a single diagnostic event (for use by the external Python importer,
which pushes `DO_CREATE` / `REF_ATTACH` / `FILE_UPLOAD` phase events that
the Java service cannot capture directly).

**Request body** (`application/json`):

```json
{
  "level": "INFO",
  "phase": "FILE_UPLOAD",
  "entityAppId": "019f1234-0000-7000-a000-000000000003",
  "message": "Uploaded AFP_run42_temperature.hdf5 (14.2 MB)",
  "attributes": { "bytes": "14890123" }
}
```

**Required fields:** `level`, `phase`, `message`.  `entityAppId` and
`attributes` are optional.

Returns **204** on success.  **400** if required fields are missing or values
are invalid.

---

### `POST /v2/import/diagnostics/{runId}/events/batch`

Ingest multiple events in a single call.  All events are validated before any
are recorded; the entire batch is rejected if any event is invalid.

**Request body** (`application/json`):

```json
{
  "events": [
    { "level": "INFO", "phase": "DO_CREATE", "message": "Creating DataObject do-1" },
    { "level": "WARN", "phase": "DO_CREATE", "message": "DataObject do-2 skipped (name exists)" }
  ]
}
```

Returns **204** on success.  **400** if the batch is empty or any event is
invalid.

---

## Plan lifecycle

Plans are write-once records keyed by `commitId`.  The lifecycle is:

| Status | Meaning |
|---|---|
| `VALID` | Ready to commit (before `expiresAt`) |
| `EXPIRED` | Plan TTL elapsed (24 hours after creation); rejected by `POST /v2/import/jobs` |
| `USED` | Import job executed against this plan (one-shot — cannot be re-used) |
| `INVALIDATED` | Manifest had hard errors; no plan persisted |

The commitId encodes the manifest, the collection state at validation time, the
caller's username, and the validation timestamp — so each call produces a unique
plan even for identical manifests.  If the collection changes between `validate`
and `import/jobs`, the jobs endpoint detects the fingerprint mismatch and
rejects the plan with 409.

---

## Validation rules

| Rule | Severity | Description |
|---|---|---|
| Collection must exist | Error | `collectionAppId` must resolve to an existing Collection |
| DataObject `status` must be valid | Error | Allowed: `DRAFT`, `IN_REVIEW`, `READY`, `PUBLISHED`, `ARCHIVED` |
| `localRef` must be unique within manifest | Error | Duplicate `localRef` across DataObjects is rejected |
| `parentRef` must resolve within manifest | Error | Parent must be a `localRef` declared in `dataObjects` |
| `predecessorRefs` must resolve within manifest | Error | Same rule as `parentRef` |
| Container `type` must be valid | Error | Allowed: `TIMESERIES`, `FILE`, `STRUCTURED_DATA` |
| Reference `dataObjectRef` and `containerRef` must resolve | Error | Both must match declared `localRef` values |
| DataObject name already exists in Collection | Warning | Existing name will be skipped during the actual import; plan is still `VALID` |

---

## Typical usage

```bash
# 1. Fetch collection context (optional — gives you the current fingerprint)
curl -H "Authorization: Bearer $TOKEN" \
  "$SHEPARD/v2/import/context?collectionAppId=$COLL_ID"

# 2. Validate a manifest
PLAN=$(curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @my-manifest.json \
  "$SHEPARD/v2/import/validate")

# 3. Check for errors
echo $PLAN | jq '.errors'

# 4. Execute the plan
COMMIT_ID=$(echo $PLAN | jq -r '.commitId')
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"commitId\": \"$COMMIT_ID\"}" \
  "$SHEPARD/v2/import/jobs"
```

---

## Notes

- Plans expire 24 hours after creation.  Re-validate if your workflow takes
  longer than 24 hours end-to-end.
- The `wouldSkipDataObjects` field in the summary counts DataObjects whose
  names already exist in the Collection.  They will be skipped (not duplicated)
  when the import job runs.
- `POST /v2/import/jobs` is **one-shot** — even on unexpected server error, the
  plan is marked `USED`.  Re-validate to obtain a fresh `commitId` if you need
  to retry.
- The import lock is acquired automatically by `POST /v2/import/jobs`.  You do
  not need to call `POST /v2/import/lock` yourself unless you are implementing a
  custom external importer that uses the diagnostics API.
- Diagnostic events are in-memory only and are not persisted across server
  restarts.
