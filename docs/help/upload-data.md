---
layout: default
title: Upload data
permalink: /help/upload-data/
---

# Upload data into shepard

You attach data to a [DataObject](/reference/data-object/) by
uploading it as a payload of one of shepard's payload kinds.

## File-shaped data (PDFs, CSVs, photos, frame dumps)

Files attach via the **file-bundle** payload kind (see
[FileBundleReference reference](/reference/file-bundle/)). A bundle is
a bag of files, optionally organised into one or more **groups**
("sub-run 1", "sub-run 2", etc.) — useful when one capture run
produces dozens or hundreds of files.

### Single file

The simplest path:

1. Create a `FileBundleReference` on the DataObject (the upstream
   `POST /shepard/api/.../fileReferences` works; `/v2/bundles/...`
   coming in FR1b will offer a singleton-shaped option).
2. Upload your file into the bundle's default group:

   ```
   POST /v2/bundles/{bundleAppId}/groups/{defaultGroupAppId}/files
   Content-Type: multipart/form-data

   file=<your file>
   ```

   Every freshly-created bundle has a default group named `"default"`
   (index 0) — discover its `appId` from `GET /v2/bundles/{appId}`.

### Multiple files in one capture run

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
