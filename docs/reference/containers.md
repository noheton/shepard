---
layout: default
title: Containers (unified /v2/containers)
permalink: /reference/containers/
audience: user
---
# Containers — the unified `/v2/containers` surface

Every **container** kind in shepard — file, timeseries, structured-data,
and (once its plugin ships a handler) HDF5 — shares one polymorphic REST
surface for the homogeneous operations: **create, get-one, patch,
delete, and list/filter**. This is the V2CONV-A3 convergence — the
direct sibling of the unified `/v2/references` surface (V2CONV-A2).

Containers are addressed by `appId` (UUID v7) throughout; numeric Neo4j
ids never appear on the wire.

> **Additive, not a replacement.** Before A3 there was no `/v2/`
> container CRUD at all — container create/get/delete/list only ever
> lived on the frozen, upstream-byte-compatible v1 surface
> (`/shepard/api/fileContainers`, `…/timeseriesContainers`,
> `…/structuredDataContainers`), keyed by numeric id. That surface is
> **unchanged**. `/v2/containers` is a new appId-keyed surface you can
> adopt when ready.

> Kind-specific **special** operations keep their own paths and are
> **not** part of this surface: the timeseries channel
> data / chart-view / anomaly endpoints, the file-container
> payload / content / presigned-url endpoints, the DI1 safe-delete
> (`DELETE /v2/{kind}-containers/{id}?force=true` with 409
> reference-conflict detection), and the HDF5 browse surface.

---

## The unified wire shape — `ContainerV2IO`

Every endpoint here returns (or returns arrays of) one envelope:

```json
{
  "appId": "019e7244-…",            // UUID v7 of the container
  "id": 123,                         // numeric Neo4j id (read-only, upstream-compat)
  "name": "NDT scans",
  "type": "FileContainer",           // entity class name (upstream-compat)
  "status": "READY",                 // DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED | null
  "createdAt": "2026-06-04T…",
  "createdBy": "alice",
  "kind": "file",                    // container family (discriminator)
  "payload": { "oid": "…" }          // per-kind read-only fields (see below)
}
```

Discriminator:

| Field | Meaning |
| --- | --- |
| `kind` | The container family: `file`, `timeseries`, `structured-data`, `hdf`. Never a numeric id. |

The per-kind read-only `payload` key sets:

| kind | `payload` keys |
| --- | --- |
| `file` | `oid` (GridFS object id), `defaultCollectionAppIds` (collections this container is the default for) |
| `structured-data` | `oid` |
| `timeseries` | _(none today)_ |

---

## Endpoints

### Create — `POST /v2/containers?kind={kind}`

Creates a container of `kind`. The request body is the per-kind create
payload — today `{name}` for every core kind. The creator becomes the
owner.

```bash
curl -fsS -X POST 'https://<host>/v2/containers?kind=file' \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"NDT scans"}'
```

→ `201` + the `ContainerV2IO`.

### Get one — `GET /v2/containers/{appId}`

The entity self-describes its kind; no `?kind=` needed.

```bash
curl -fsS 'https://<host>/v2/containers/<appId>' -H 'Authorization: Bearer <token>'
```

### Patch — `PATCH /v2/containers/{appId}`

RFC 7396 merge-patch. Mutable fields: `name`, `status`. Absent keys are
left unchanged.

```bash
curl -fsS -X PATCH 'https://<host>/v2/containers/<appId>' \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/merge-patch+json' \
  -d '{"name":"NDT scans (rev B)"}'
```

### Delete — `DELETE /v2/containers/{appId}`

```bash
curl -fsS -X DELETE 'https://<host>/v2/containers/<appId>' -H 'Authorization: Bearer <token>'
```

→ `204`.

> For a **safe delete** that refuses (409) when active references still
> point at the container — and tells you which DataObjects — use the
> DI1 endpoint `DELETE /v2/{kind}-containers/{id}?force=true`
> (see [Container safe delete](container-safe-delete)). That richer
> delete is kept separate from this plain delete.

### List / filter — `GET /v2/containers?kind={kind}[&name={substr}]`

Lists containers of `kind` the caller may read. The optional `name`
query param narrows by substring.

```bash
# all timeseries containers
curl -fsS 'https://<host>/v2/containers?kind=timeseries' -H 'Authorization: Bearer <token>'

# file containers whose name contains "scan"
curl -fsS 'https://<host>/v2/containers?kind=file&name=scan' -H 'Authorization: Bearer <token>'
```

---

## Permissions

Create and list require authentication. Get gates on **Read**, and
patch / delete on **Write**, of the container itself. `401`
unauthenticated, `403` on permission denied, `404` on a missing
container.

## Plugin kind (HDF5)

The unified surface dispatches through a `ContainerKindHandler` SPI.
Core kinds (file / timeseries / structured-data) ship in-tree. The HDF5
handler ships inside the `hdf5` plugin module; until it is installed,
`?kind=hdf` returns `400` (uninstalled kind) and HDF5 containers keep
their own plugin paths. See `PLUGIN-CONTAINER-HANDLER-HDF` in
`aidocs/16`.
