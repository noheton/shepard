---
audience: user
---

# Import validation — reference

## Overview

The `POST /v2/import/validate` endpoint performs a **dry-run validation** of
an import manifest — a batch description of DataObjects, Containers, and
DataObject-Container links to be created together in a Collection.

On success, the endpoint returns a **commitId** (plan seal) that must be
presented to `POST /v2/import/jobs` to actually execute the import.  No
data is written to the database during the dry run.

This pattern lets automated importers (scripts, agents, CI pipelines) get
server-side feedback on a manifest before committing any writes — catching
structural errors, name conflicts, and stale collection state early.

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

## Plan lifecycle

Plans are write-once records keyed by `commitId`.  The lifecycle is:

| Status | Meaning |
|---|---|
| `VALID` | Ready to commit (before `expiresAt`) |
| `EXPIRED` | Plan TTL elapsed (24 hours after creation); rejected by `POST /v2/import/jobs` |
| `USED` | Import job executed against this plan |
| `INVALIDATED` | Manifest had hard errors; no plan persisted |

The commitId encodes the manifest, the collection state at validation time, the
caller's username, and the validation timestamp — so each call produces a unique
plan even for identical manifests.  If the collection changes between `validate`
and the future `import/jobs` call, the jobs endpoint will detect the fingerprint
mismatch and reject the plan.

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
| DataObject name already exists in Collection | Warning | Existing name will be skipped during the actual import; plan is still VALID |

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

# 4. Save the commitId for the import job (future endpoint)
COMMIT_ID=$(echo $PLAN | jq -r '.commitId')
```

---

## Notes

- Plans expire 24 hours after creation.  Re-validate if your workflow takes
  longer than 24 hours end-to-end.
- The `wouldSkipDataObjects` field in the summary counts DataObjects whose
  names already exist in the Collection.  They will be skipped (not duplicated)
  when the import job runs.
- `POST /v2/import/jobs` (the commit endpoint) is not yet available.  It will
  enforce the commitId / fingerprint contract and create the DataObjects,
  Containers, and References atomically.
