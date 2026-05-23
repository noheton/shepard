---
audience: user
---

# Payload versioning reference

**Feature ID:** PV1a  
**Design doc:** `aidocs/data/46-payload-versioning-design.md`  
**API surface:** `/v2/` (this fork's development surface; upstream `/shepard/api/...` untouched)

---

## Overview

Every file uploaded to a **FileContainer** now automatically receives a
**PayloadVersion** record. Each record captures:

- The SHA-256 digest of the uploaded bytes (cryptographic integrity).
- The raw file size in bytes.
- A monotonically-increasing `versionNumber` scoped to the `(container, fileName)` pair.
- The uploader's username and an ISO-8601 upload timestamp.
- The GridFS ObjectId (`fileOid`) for GridFS-backed uploads (null for S3 presigned-URL uploads).

This gives researchers a numbered, tamper-evident history for every named file — without any
manual bookkeeping. Re-uploading `calibration.csv` produces version 2; uploading it again
produces version 3; the SHA-256 at each version makes deduplication and integrity checks trivial.

### Scope (PV1a)

PV1a records versions for **FileContainer uploads only** (the `POST
/shepard/api/fileContainers/{id}/payload` legacy path and, for containers with an `appId`, the
presigned upload path from FS1c). Follow-on slices (PV1b–PV1g) extend the same shape to
StructuredDataReference, SpatialDataReference, and TimeseriesReference.

### Containers without an appId

Containers created before the L2a migration (which stamped `appId` on existing rows) do not
receive version nodes — the upload completes normally and a Warning is logged. These containers
can be backfilled by running the L2b migration sweep when it ships.

---

## Endpoints

### List all versions for a named file

```
GET /v2/file-containers/{containerAppId}/files/{originalName}/versions
```

**Path parameters:**

| Parameter | Description |
|---|---|
| `containerAppId` | UUID v7 of the FileContainer. |
| `originalName` | Filename as supplied at upload time (URL-encoded if it contains special characters). |

**Responses:**

| Status | Body | When |
|---|---|---|
| 200 | Array of `PayloadVersionIO` (see below), ordered by `versionNumber` ascending. Empty array if no versions exist. | Success. |
| 401 | — | No valid authentication credential supplied. |
| 403 | — | Caller lacks Read permission on the container. |
| 404 | — | No FileContainer with that `containerAppId` exists. |

**Example request:**

```bash
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  https://shepard.example.dlr.de/v2/file-containers/018f4e2a-1b2c-7d3e-8f4a-5b6c7d8e9f0a/files/calibration.csv/versions
```

**Example response:**

```json
[
  {
    "appId": "018f4e2a-0001-7abc-8def-000000000001",
    "versionNumber": 1,
    "fileOid": "60b73212cfa45d2d5baa795d",
    "sha256": "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
    "sizeBytes": 0,
    "uploadedBy": "alice",
    "uploadedAt": "2026-05-17T12:00:00Z"
  },
  {
    "appId": "018f4e2a-0001-7abc-8def-000000000002",
    "versionNumber": 2,
    "fileOid": "60b73212cfa45d2d5baa795e",
    "sha256": "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824",
    "sizeBytes": 1024,
    "uploadedBy": "bob",
    "uploadedAt": "2026-05-17T14:30:00Z"
  }
]
```

---

## Wire shape: `PayloadVersionIO`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `appId` | string | No | UUID v7 identifier of this version node. |
| `versionNumber` | integer | No | 1-based counter per `(containerAppId, originalName)`. Version 1 is the first upload. |
| `fileOid` | string | Yes | GridFS ObjectId hex of the stored bytes. Null for S3 presigned-URL uploads. |
| `sha256` | string | Yes | SHA-256 upper-case hex digest of the uploaded bytes. Null for legacy uploads that predate PV1a. |
| `sizeBytes` | integer | Yes | Byte count of the uploaded payload. Null when the storage backend could not determine the size. |
| `uploadedBy` | string | No | Username of the caller who triggered the upload. |
| `uploadedAt` | string | No | ISO-8601 UTC timestamp of the upload. |

---

## Neo4j schema

### `:PayloadVersion` node

All fields are stored as Neo4j properties; there are no relationship edges (the version is
linked to its container via the scalar `containerAppId` property, not a graph edge).

| Property | Type | Description |
|---|---|---|
| `appId` | String | UUID v7, unique (enforced by `V41__Add_appId_constraint_PayloadVersion.cypher`). |
| `containerAppId` | String | `appId` of the parent FileContainer. |
| `originalName` | String | File name as supplied at upload time. |
| `versionNumber` | Long | Monotonically-increasing counter per `(containerAppId, originalName)`. |
| `fileOid` | String | GridFS ObjectId hex; null for S3 presigned-URL uploads. |
| `sha256` | String | SHA-256 upper-case hex; null for pre-PV1a rows. |
| `sizeBytes` | Long | Byte count; null when unavailable. |
| `uploadedBy` | String | Username of the uploader. |
| `uploadedAt` | String | ISO-8601 UTC timestamp. |
| `deleted` | Boolean | Soft-delete flag (inherited from `AbstractEntity`). |

### Constraint (migration V41)

```cypher
CREATE CONSTRAINT PayloadVersion_appId_unique IF NOT EXISTS
FOR (n:PayloadVersion) REQUIRE n.appId IS UNIQUE;
```

Applied idempotently on startup by the `MigrationsRunner`. To verify:

```cypher
SHOW CONSTRAINTS WHERE name = "PayloadVersion_appId_unique";
```

---

## Implementation notes

### SHA-256 computation

SHA-256 is computed in a single streaming pass over the upload bytes by chaining two
`DigestInputStream`s: the inner one computes SHA-256, the outer one computes MD5 (stored
in the GridFS bookkeeping document for backward compatibility). There is no extra I/O or
buffering — bytes flow through both digests as GridFS reads the stream.

### Best-effort recording

The `recordPayloadVersion` call in `FileContainerService` is wrapped in a try-catch. If
the DAO call fails for any reason (e.g. transient Neo4j hiccup), the upload is not rolled
back — the file is already persisted in MongoDB/S3. A WARNING is logged. The version node
will be absent for that upload; re-uploading the same file will produce the next version
number normally.

### Version numbering

`versionNumber` is assigned by reading `MAX(v.versionNumber)` for the
`(containerAppId, originalName)` scope and incrementing by 1. The first upload gets
version 1 (via `COALESCE(MAX(...), 0) + 1`). There are no gaps unless a best-effort
failure occurred.

---

## Upgrade notes

No action is required for operators upgrading from an earlier release. Restart shepard;
migration V41 runs on first start (zero `:PayloadVersion` rows exist beforehand). From
that point on, every new upload to a container that has an `appId` creates a version node.
Historical uploads do not receive retroactive version nodes (no backfill migration — the
SHA-256 cannot be recomputed from the already-stored GridFS bytes without re-streaming).

To check whether the constraint was applied:

```cypher
SHOW CONSTRAINTS WHERE name = "PayloadVersion_appId_unique";
```
