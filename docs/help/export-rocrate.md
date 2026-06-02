---
layout: default
title: Download a collection as RO-Crate
permalink: /help/export-rocrate/
audience: user
---
# Download a collection as RO-Crate

shepard can package an entire collection into a
[Research Object Crate (RO-Crate)](https://www.researchobject.org/ro-crate/)
archive — a self-describing ZIP file that bundles the collection's metadata,
data objects, annotations, and provenance graph in a format any FAIR-compliant
tool can read.

---

## What the export contains

| Contents | Notes |
|---|---|
| `ro-crate-metadata.json` | JSON-LD metadata record covering every data object, file reference, and annotation in the collection. |
| All file references | Every file attached to a data object in the collection is included. |
| Provenance graph | PROV-O provenance in JSON-LD, covering the process chain (predecessor/successor links, status transitions). |
| Collection manifest | Top-level descriptor: collection name, owner, creation date, license. |

Timeseries data and structured data references are represented in the metadata
but their raw payload is not bundled (export of high-volume timeseries is out of
scope for RO-Crate; use the timeseries download API for raw channel data).

---

## Download from the collection page

1. Open the collection you want to export.
2. In the top-right button row, click **Download as RO-Crate**
   (icon: package with a downward arrow).
3. The export is generated server-side. A browser download starts automatically
   when it is ready. The file is named `<collection-name>-export.zip`.

You need at least **Reader** permission on the collection to export it. The
download includes only entities you have permission to see.

---

## Download from the sidebar

Alternatively:

1. Hover over the collection name in the left sidebar.
2. Click the three-dot context menu.
3. Choose **Export to RO-Crate**.

This triggers the same export and download.

---

## Regulatory Evidence Pack (advanced)

For submissions that require a full audit trail — EN 9100, EASA Part 21, Clean
Aviation — the collection page also offers a **Regulatory Evidence Pack** button
(orange, with a certificate icon). This produces a BagIt bag containing
RO-Crate + a complete PROV-O provenance graph, formatted for regulatory
submissions. The bag is digitally stamped and includes checksums for every
file.

Use the standard **Download as RO-Crate** button for everyday data sharing and
archival; use **Regulatory Evidence Pack** when you need a submission-grade package.

---

## Use the API

```
GET /shepard/api/collections/{collectionId}/export
Authorization: Bearer <token>
```

The response is `application/zip`. Pipe it directly to a file:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://shepard.example.dlr.de/shepard/api/collections/42/export" \
  -o my-collection-export.zip
```

---

## See also

- [Publish a DataObject or Collection](/help/publish-data-object/) — mint a
  persistent identifier for the collection so it is citable from a paper.
- [Share a collection](/help/share-collection/) — control who can access and
  download the collection.
- [Annotating data](/help/annotating-data/) — annotations are included in the
  RO-Crate metadata.
