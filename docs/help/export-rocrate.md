---
layout: default
title: Export a collection as RO-Crate
description: How to download a collection as a self-contained RO-Crate ZIP package for archiving or sharing
permalink: /help/export-rocrate/
audience: user
stage: deployed
---
# Export a collection as RO-Crate

An **RO-Crate export** bundles a collection's metadata and data
payloads into a single ZIP file that follows the
[RO-Crate 1.1 specification](https://www.researchobject.org/ro-crate/).
The result is a self-contained, machine-readable archive — useful for:

- Handing a dataset to a collaborator at another institute who has no
  shepard account.
- Submitting to a data repository that accepts RO-Crate deposits.
- Long-term archiving at a suitable infrastructure.

## Quick export from the UI

1. Open the collection you want to export.
2. In the collection action bar, click **Export → Download RO-Crate**.
3. The browser downloads a `<collectionId>-export.zip`.

The ZIP contains an `ro-crate-metadata.json` manifest describing the
collection and its DataObjects, plus the actual payload files (CSVs
for timeseries channels, raw files for FileReferences, JSON for
structured-data references). Lab-journal entries are included by
default.

## Presigned download URL (S3 storage)

If your shepard instance uses S3-compatible object storage (MinIO,
AWS S3, etc.) you can request a **presigned URL** instead of
streaming the ZIP through the browser. The ZIP is built server-side
and uploaded to object storage; the returned URL is valid for 30
minutes and downloads the ZIP directly from storage — good for large
collections:

```
POST /v2/collections/{appId}/export-url
Content-Type: application/json

{}
```

Response:

```json
{
  "url": "https://minio.example.dlr.de/exports/abc123.zip?X-Amz-...",
  "fileName": "01HF...-export.zip",
  "expiresAt": "2026-05-29T15:30:00Z"
}
```

Open or `curl` the `url` within the validity window to download.
If your instance uses GridFS (MongoDB) instead of S3 the endpoint
returns 503 — fall back to the streaming export below.

## Streaming export (legacy / GridFS instances)

```
GET /shepard/api/collections/{collectionId}/export
```

Replace `{collectionId}` with the numeric id from the collection
detail page (or retrieve it via `GET /v2/collections/{appId}`).

The server streams the ZIP directly in the response body. This works
on all storage backends but holds a server thread for the duration of
the download.

## Filtered export

If you only need part of a collection — specific payload kinds, a
time window for timeseries channels, or just selected files — include
an `ExportSelection` body in the presigned-URL request:

```
POST /v2/collections/{appId}/export-url
Content-Type: application/json

{
  "payloads": {
    "include": ["FileReference", "TimeseriesReference"],
    "excludeIds": ["<referenceId-to-skip>"],
    "perPayload": {
      "<timeseriesReferenceId>": {
        "timeRange": {
          "start": "2024-06-02T08:00:00Z",
          "end":   "2024-06-02T10:00:00Z"
        },
        "columns": ["pressure_bar", "temperature_K"]
      }
    }
  },
  "metadata": {
    "annotations": true,
    "permissions": false
  }
}
```

The `ExportSelection` is optional — an empty body `{}` or no body
at all produces the full collection export.

**Snapshot-based export:** if you have created a
[snapshot](/reference/snapshots/) of the collection, pass its `appId`
to get a reproducible subset:

```json
{ "snapshotAppId": "<snapshot-appId>" }
```

Only DataObjects captured in that snapshot will appear in the ZIP.

## What the ZIP contains

```
my-collection-export.zip
├── ro-crate-metadata.json        ← RO-Crate 1.1 manifest
├── <dataObjectId>/
│   ├── <dataObjectId>.json       ← DataObject metadata + attributes
│   ├── <fileId>.<ext>            ← attached files (FileReferences)
│   ├── <timeseriesId>.csv        ← timeseries data (one CSV per reference)
│   ├── <structuredDataId>.json   ← structured-data payloads
│   └── ...
└── ...
```

Lab-journal markdown entries are embedded in the DataObject metadata
by default. To exclude them, set `"labJournal": false` under `metadata`
in the `ExportSelection`.

## Permissions and privacy

You need at least **Reader** access to export a collection. The export
includes everything you can see — the same access rules that govern
the REST surface apply inside the export.

If you want to share the ZIP with someone outside your organisation
but prefer not to expose usernames, add
`"permissions": true, "redactFields": ["PERMISSION_USERNAME"]` to the
`metadata` block. shepard replaces every username in the
`-permissions.json` sidecar files with `[REDACTED]` but still records
the structure so the crate remains self-describing.

## Further reading

- [User guide](/user-guide/) — overview of collections and DataObjects.
- [Publish a DataObject or Collection](/help/publish-data-object/) —
  mint a persistent identifier instead of downloading the raw crate.
- [RO-Crate specification](https://www.researchobject.org/ro-crate/) —
  the upstream standard the ZIP conforms to.
