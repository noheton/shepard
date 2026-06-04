---
layout: default
title: References (unified /v2/references)
permalink: /reference/references/
audience: user
---
# References — the unified `/v2/references` surface

Every **reference** kind in shepard — file (singleton), timeseries,
URI, and the plugin kinds (video, git, HDF5) — shares one polymorphic
REST surface for the homogeneous operations: **create, get-one,
patch, delete, and list/filter**. This is the V2CONV-A2 convergence;
it replaces the per-kind paths (`/v2/timeseries-references`,
`/v2/uri-references`, …) for those operations.

> Kind-specific **binary / special** operations keep their own paths
> and are **not** part of this surface: the multipart
> `POST /v2/files` upload, `GET /v2/files/{appId}/content`, the video
> `/download`, and the git `/preview` + `/check-update`.

---

## The unified wire shape — `ReferenceV2IO`

Every endpoint here returns (or returns arrays of) one envelope:

```json
{
  "appId": "019e7244-…",          // UUID v7 of the reference
  "name": "calibration-run-2026",
  "type": "FileReference",         // entity class name (upstream-compat)
  "dataObjectId": 123,             // parent DO (resolved server-side)
  "createdAt": "2026-06-04T…",
  "createdBy": "alice",
  "kind": "file",                  // payload family (discriminator)
  "referenceShape": "singleton",   // file only: singleton | bundle
  "fileKind": "urdf",              // file singletons only (else null)
  "payload": { … }                 // per-kind fields (see below)
}
```

Discriminators:

| Field | Meaning |
| --- | --- |
| `kind` | The payload family: `file`, `timeseries`, `uri`, `video`, `git`, `hdf`. Never a numeric id. |
| `referenceShape` | For `kind=file` only — `singleton` (FR1b) vs `bundle` (legacy multi-file). `null` for other kinds. |
| `fileKind` | For `kind=file` singletons only — `krl`, `svdx`, `otvis`, `urdf`, `xit`, `pdf`. `null` when unrecognised. |

The per-kind `payload` key sets:

| kind | `payload` keys |
| --- | --- |
| `file` | `file` (the embedded `ShepardFile`) |
| `uri` | `uri`, `relationship` |
| `timeseries` | `start`, `end`, `timeseriesContainerId`, `timeseries`, `timeReference`, `wallClockOffset`, `wallClockOffsetSource`, `qualityScore`, `lastScoredAt` |
| `git` | `repoUrl`, `ref`, `path`, `mode` |

---

## Endpoints

### Create — `POST /v2/references?kind={kind}&dataObjectAppId={appId}`

Creates a **non-binary** reference of `kind` attached to the
DataObject. The request body is the per-kind create payload.

```bash
curl -fsS -X POST \
  'https://<host>/v2/references?kind=uri&dataObjectAppId=<doAppId>' \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"DLR homepage","uri":"https://www.dlr.de","relationship":"seeAlso"}'
```

→ `201` + the `ReferenceV2IO`.

> **File singletons are binary** — create them via the multipart
> `POST /v2/files?parentDataObjectAppId=…` upload (which also sets
> `fileKind`). `kind=file` rejects here with `400`.

### Get one — `GET /v2/references/{appId}`

The entity self-describes its kind; no `?kind=` needed.

```bash
curl -fsS 'https://<host>/v2/references/<appId>' -H 'Authorization: Bearer <token>'
```

### Patch — `PATCH /v2/references/{appId}`

RFC 7396 merge-patch, dispatched by the entity's kind (timeseries →
time-alignment fields; uri → name/uri/relationship; file → name).

```bash
curl -fsS -X PATCH 'https://<host>/v2/references/<appId>' \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/merge-patch+json' \
  -d '{"name":"renamed"}'
```

### Delete — `DELETE /v2/references/{appId}`

```bash
curl -fsS -X DELETE 'https://<host>/v2/references/<appId>' -H 'Authorization: Bearer <token>'
```

→ `204`.

### List / filter — `GET /v2/references?kind={kind}&dataObjectAppId={appId}[&fileKind={sub}]`

Lists references of `kind` on a DataObject. For `kind=file`, the
optional `fileKind` narrows to singletons of that file-kind.

```bash
# all timeseries references on a DataObject
curl -fsS 'https://<host>/v2/references?kind=timeseries&dataObjectAppId=<doAppId>' \
  -H 'Authorization: Bearer <token>'

# only the URDF singletons
curl -fsS 'https://<host>/v2/references?kind=file&dataObjectAppId=<doAppId>&fileKind=urdf' \
  -H 'Authorization: Bearer <token>'
```

---

## Permissions

Every operation gates on the reference's **parent DataObject** —
Read for get/list, Write for create/patch/delete — inherited from the
DataObject's Collection. `401` unauthenticated, `403` on permission
denied, `404` on a missing reference/DataObject.

## Plugin kinds (video / git / HDF5)

The unified surface dispatches through a `ReferenceKindHandler` SPI.
Core kinds (file/timeseries/uri) ship in-tree. The video, git, and
HDF5 handlers ship inside their plugin modules; until a plugin's
handler is installed, `?kind=video|git|hdf` returns `400`
(uninstalled kind) and those kinds keep their own plugin paths. See
the plugin reference pages and `PLUGIN-REF-HANDLER-*` in
`aidocs/16`.
