---
layout: default
title: Upload data
permalink: /help/upload-data/
audience: user
---
# Upload data into shepard

You attach data to a [DataObject](/reference/data-object/) by
uploading it as a payload of one of shepard's payload kinds.

## File-shaped data (PDFs, CSVs, photos, frame dumps)

shepard ships two file primitives. The casual upload UI picks the
right one by **drag count**:

| Drag count | Primitive | Reference doc |
|---|---|---|
| 1 file | `FileReference` (singleton) | [File reference](/reference/file-reference/) |
| ≥ 2 files | `FileBundleReference` (multi) | [File bundle](/reference/file-bundle/) |

A "wrap as bundle" toggle on the drop target lets you pre-declare
bundle intent when you have one file now but expect more. The
direction is **singleton → bundle, never bundle → singleton**: a
user who realises mid-upload they want bundle structure converts
forward; un-bundling is delete + re-upload.

### Single file (singleton)

The simplest path — one file becomes one Reference. No groups, no
bundle ceremony.

```
POST /v2/files?parentDataObjectAppId={dataObjectAppId}&name=My%20PDF
Content-Type: multipart/form-data

file=<your file>
```

The response carries the singleton's `appId`; the underlying bytes
live in the shared `_shepard_files` MongoDB / GridFS namespace. To
download:

```
GET /v2/files/{appId}/content
```

Range requests are supported (`Range: bytes=0-1023` for the first
1 KiB; `Range: bytes=500-` for everything from offset 500 onwards) —
useful for streamed previews and resumable downloads.

To rename the singleton (the only patchable field in FR1b):

```
PATCH /v2/files/{appId}
Content-Type: application/merge-patch+json

{"name": "renamed"}
```

`DELETE /v2/files/{appId}` removes the Reference and its bytes.

### Multiple files in one capture run (bundle)

Group them so future-you can navigate them sub-run by sub-run:

1. Create a `FileBundleReference` on the DataObject.
2. Create one `FileGroup` per sub-run:

   ```
   POST /v2/bundles/{bundleAppId}/groups
   Content-Type: application/json

   {"name": "sub-run 1", "startedAt": "2026-05-12T14:00:00Z"}
   ```

3. Upload each file into the appropriate group:

   ```
   POST /v2/bundles/{bundleAppId}/groups/{groupAppId}/files
   Content-Type: multipart/form-data

   file=<frame-0001.png>
   ```

4. Repeat for each frame; repeat for each group.

### Renaming, reordering, deleting groups

Groups support RFC 7396 merge-patch:

```
PATCH /v2/bundles/{bundleAppId}/groups/{groupAppId}
Content-Type: application/merge-patch+json

{"name": "renamed", "index": 3, "endedAt": "2026-05-12T14:30:00Z"}
```

Delete a group with `DELETE /v2/bundles/{bundleAppId}/groups/{groupAppId}`.
The server refuses if:

- The group contains files (pass `?force=true` to delete the group
  AND its files), or
- The group is the **last** remaining group of the bundle (would
  orphan all the bundle's files — create another group first, or
  delete the whole bundle instead).

## Other payload kinds

shepard ships several payload kinds beyond files. See:

- [Timeseries](/reference/timeseries/) — high-frequency sensor data
  (InfluxDB-backed).
- [Structured data](/reference/structured-data/) — schema-validated
  JSON documents.
- [Spatial data](/reference/spatial-data/) — geometry / point clouds
  (PostGIS-backed).
- More coming: video (VID1 series), HDF5 (`aidocs/35`), git refs
  (`aidocs/38`).
