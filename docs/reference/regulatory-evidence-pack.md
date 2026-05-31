---
audience: user
---

# Regulatory Evidence Pack (REP) Export

> **Feature ID:** TPL14 &mdash; shipped 2026-05-26  
> **API surface:** `/v2/` only (additive, no change to `/shepard/api/`)  
> **Auth:** Read permission on the Collection

---

## Overview

The Regulatory Evidence Pack export builds a **BagIt 1.0** (RFC 8493) ZIP archive
containing two research-object artefacts:

| File (inside `data/`) | Contents |
|---|---|
| `data/ro-crate-metadata.json` | RO-Crate 1.1 descriptor — root `Dataset` + one `Dataset` per DataObject with name, description, license, accessRights, creator, dateCreated |
| `data/PROV-O.jsonld` | Standalone PROV-O JSON-LD — up to 1 000 most-recent `:Activity` records targeting the Collection; full W3C-conformant with `@context` + `@graph` |

BagIt tag files guarantee integrity:

- `bagit.txt` — declares BagIt-Version 1.0 and UTF-8 encoding
- `bag-info.txt` — `Payload-Oxum`, `External-Identifier: shepard:<collectionAppId>`, bagging date
- `manifest-sha256.txt` — SHA-256 checksums for all `data/` payload files
- `tagmanifest-sha256.txt` — SHA-256 checksums for all tag files (including `manifest-sha256.txt` itself)

---

## Endpoints

### Build a new REP bag

```
POST /v2/collections/{appId}/export/regulatory-evidence
```

Returns a `RepExportIO` JSON body. Bags &le; 1 MB are delivered inline as Base64.

**Response shape:**

```json
{
  "exportId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "READY",
  "bagBase64": "<base64-encoded ZIP>",
  "downloadUrl": null,
  "fileName": "018f9c5a-7e26-7000-a000-000000000042-rep.bag.zip",
  "exportedAt": "2026-05-26T15:30:00Z",
  "dataObjectCount": 42,
  "bagSizeBytes": 18432
}
```

**Status codes:**

| Code | Meaning |
|------|---------|
| 200  | Bag built successfully; `bagBase64` or `downloadUrl` contains the bag |
| 401  | Authentication required |
| 403  | Caller lacks Read permission on the Collection |
| 404  | No Collection with that `appId` |
| 500  | Build failed (serialisation error or bag exceeds 1 MB inline limit — TPL14b) |

### Retrieve the most recent REP bag

```
GET /v2/collections/{appId}/export/regulatory-evidence/latest
```

> **Current status:** Returns 404 — export persistence (TPL14b) is not yet implemented.
> The path is reserved so clients can adopt it in advance.

---

## Download a REP bag (cURL)

```bash
curl -s \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -X POST \
  https://shepard.example.dlr.de/v2/collections/$COLLECTION_APP_ID/export/regulatory-evidence \
| python3 -c "
import json, sys, base64
data = json.load(sys.stdin)
open(data['fileName'], 'wb').write(base64.b64decode(data['bagBase64']))
print('Saved:', data['fileName'])
"
```

---

## Validate the bag

Use the [Library of Congress BagIt Python library](https://github.com/LibraryOfCongress/bagit-python):

```bash
pip install bagit
python3 -c "import bagit; bag = bagit.Bag('extracted/'); bag.validate(); print('Valid!')"
```

Or unzip and inspect manually — all checksums are plain hex + two-space + path, one per line.

---

## Regulatory use cases

| Standard | What the REP provides |
|---|---|
| **EN 9100 §7.5.3** (documented information) | Immutable BagIt manifest proves the data was not altered after export |
| **EASA CS-25 / Part 21** (non-conformance records) | PROV-O graph lists every `CREATE`, `UPDATE`, `DELETE`, and `PUBLISHED` activity |
| **Clean Aviation JU** (data management plan) | RO-Crate descriptor carries `license`, `accessRights`, `datePublished`, and creator ORCID (when set) |
| **DFG / Horizon Europe DMP** | PROV-O graph is FAIR-R1.2 compliant (provenance vocabulary: W3C PROV-O) |

---

## Limitations (TPL14 v1)

- Bags larger than 1 MB return HTTP 500. Large-collection support (async build → presigned URL) is tracked as **TPL14b**.
- `GET /latest` returns 404 until persistence is implemented (also **TPL14b**).
- The PROV-O graph includes at most 1 000 activities (most recent). For collections with longer histories, a `sinceMillis` filter is planned in TPL14c.
- DataObject binary payloads (files, timeseries) are **not** included in the bag — only the metadata + provenance. A full payload bundle is covered by the existing `POST /v2/collections/{appId}/export-url` endpoint.

---

## Related

- `docs/reference/snapshots.md` — snapshot-pinned exports for reproducibility
- `docs/reference/provenance.md` — PROV-O provenance architecture
- `docs/reference/file-storage.md` — full payload export via presigned URL (FS1g)
- RFC 8493 — The BagIt File Packaging Format
- RO-Crate 1.1 specification — https://www.researchobject.org/ro-crate/1.1/
