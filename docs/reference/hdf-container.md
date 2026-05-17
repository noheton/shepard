---
layout: default
title: HDF container (reference)
permalink: /reference/hdf-container/
---

# HDF container reference

An **`HdfContainer`** is the payload kind for **HDF5** scientific data,
backed by the [HSDS sidecar](https://github.com/HDFGroup/hsds). Phase
1 of the rollout (this slice — backlog ID **A5a**) ships
**create / read / delete** of containers; the data-path mirroring of
HSDS's dataset / value / attribute surface, the per-DataObject
`HdfReference`, the byte-identical download fallback, and the
shared-Keycloak token relay all arrive in later phases (A5b – A5e —
see [`aidocs/35`](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md)
for the rollout plan).

## Opt-in feature

The HDF / HSDS surface is **off by default**. With the toggle off,
every `/v2/hdf-containers/...` endpoint returns `404 Not Found` and
no HSDS HTTP client is ever instantiated. Operators flip the toggle
on by:

1. Bringing up the `hdf` compose profile so the `shepard-hsds`
   sidecar is running:

   ```bash
   docker compose --profile hdf up -d shepard-hsds
   ```

2. Setting the four config keys on the shepard backend:

   ```properties
   shepard.hdf.enabled=true
   shepard.hdf.hsds.endpoint=http://shepard-hsds:5101
   shepard.hdf.hsds.username=admin   # match HSDS_USERNAME from compose
   shepard.hdf.hsds.password=admin   # match HSDS_PASSWORD from compose
   ```

3. Restarting the backend. Startup fails fast if the toggle is on but
   credentials are blank — Phase 1 deliberately refuses to run in
   "ambient auth" mode.

The HTTP Basic credential pair is the **only** auth Phase 1 supports.
Per-user OIDC token relay arrives in [A5e](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md#5-auth-bridge-the-trickiest-piece).

> Read-on note for admins: see [`docs/admin.md` §"HDF5 (HSDS)"](/admin/#hdf5-hsds-opt-in-sidecar)
> for the host-side details (volume mount, capacity-planning rule of
> thumb, where to put credentials in production).

## Shape

```
HdfContainer  ────►  HSDS domain ( /shepard/<container-appId>/ )
       │
       │ (permissions)
       ▼
   Owner / Readers / Writers / Managers (standard shepard ACL)
```

| Field | Type | Notes |
|---|---|---|
| `appId` | string (UUID v7) | Server-minted. The HSDS domain path is `/shepard/{appId}/`. |
| `name` | string | Required on create. |
| `description` | string? | Optional free-form description. |
| `hsdsDomain` | string | Server-minted. Read-only on the wire. Carved out so admins can `grep` the path out of HSDS-side audit logs. |
| `attributes` | `Map<string,string>` | Free-form key/value metadata. Same delimiter idiom as the rest of the codebase. |
| `permissions` | Permissions | Standard shepard ACL (owner / readers / writers / managers). **In Phase 1 these are not yet flowed to HSDS-side ACLs** — A5b lights up the bridge. |

## REST surface

All endpoints live on the **`/v2/` shelf** (this fork's development
surface — see [API version policy](/architecture/#api-version-policy)).
Upstream shepard 5.2.0 has no HDF support; nothing lands on
`/shepard/api/...`.

| Verb | Path | What it does | Status codes |
|---|---|---|---|
| `GET` | `/v2/hdf-containers/{appId}` | Read one container. Permission-checked: caller needs READ on the container. | 200 / 401 / 403 / 404 |
| `POST` | `/v2/hdf-containers` | Create a new container. Provisions the HSDS domain via the sidecar; rolls back the HSDS side if the Neo4j commit fails. | 201 / 400 / 401 / 503 |
| `DELETE` | `/v2/hdf-containers/{appId}` | Soft-delete the container + drop the HSDS domain. Owner-only. | 204 / 401 / 403 / 404 / 503 |
| `GET` | `/v2/hdf-containers/{appId}/file` | Download the raw HDF5 file from HSDS (A5d offline fallback — see below). | 200 / 206 / 401 / 403 / 404 / 503 |

`POST /v2/hdf-containers` request body:

```json
{
  "name": "primary",
  "description": "Hot-fire run 2026-05-12",
  "attributes": {
    "project": "rocket-x",
    "instrument": "thrust-bench"
  }
}
```

Response (201):

```json
{
  "appId": "019e1cee-654f-7554-8543-0ba62ae14113",
  "name": "primary",
  "description": "Hot-fire run 2026-05-12",
  "hsdsDomain": "/shepard/019e1cee-654f-7554-8543-0ba62ae14113/",
  "attributes": { "project": "rocket-x", "instrument": "thrust-bench" }
}
```

### `GET /v2/hdf-containers/{appId}/file` — offline HDF5 download

**A5d.** Returns the raw HDF5 byte stream from the HSDS sidecar so
researchers can open the file locally with `h5py.File(local_path)`
without needing an HSDS client:

```bash
curl -H "X-API-KEY: <your-api-key>" \
  https://shepard.example.dlr.de/v2/hdf-containers/<appId>/file \
  -o container.h5

python3 -c "import h5py; f=h5py.File('container.h5'); print(list(f.keys()))"
```

**Range requests** are supported and passed through to HSDS verbatim.
When HSDS honours the `Range`, the response status is `206 Partial
Content` with a `Content-Range` header:

```bash
curl -H "X-API-KEY: <key>" -H "Range: bytes=0-1023" \
  .../v2/hdf-containers/<appId>/file -o header_chunk.bin
```

Response headers:

| Header | Value |
|---|---|
| `Content-Type` | `application/x-hdf5` |
| `Content-Disposition` | `attachment; filename="<name>.h5"; filename*=UTF-8''<percent-encoded>` |
| `Accept-Ranges` | `bytes` (or value forwarded from HSDS) |
| `Content-Range` | present on 206 responses only |
| `Content-Length` | present when HSDS supplies it |

Auth: caller needs **READ** permission on the container (same gate as
the `GET /v2/hdf-containers/{appId}` metadata endpoint).

503 is returned when the HSDS sidecar is unreachable or returns an
unexpected error code. 404 is returned when the feature toggle is off
(`shepard.hdf.enabled=false`) or the container doesn't exist.

## Permission model

shepard is the **source of truth** for HDF container ACLs (`aidocs/63`
ADR-0020). When you change permissions on a `:Collection` that contains
`:HdfContainer` rows, the `PermissionsChangedEvent` CDI seam fires the
`HdfPermissionBridge` observer, which rewrites the matching HSDS
domain's ACL via the sidecar's REST API. The mapping is:

| shepard role | HSDS POSIX bits |
|---|---|
| Reader | `read` |
| Writer | `read,update,create,delete` |
| Manager | `read,update,create,delete,update_acl` |
| _(no role)_ | (entry removed; container becomes invisible to that user) |

Sync is **best-effort**. Failures (HSDS unreachable, auth misconfig)
are logged + queued in a size-capped in-memory retry list, then
attempted again on the next permission write. A failed bridge call
**never blocks the shepard write** — the casual UX always wins.

**Direct HSDS-side mutation gets clobbered.** Tools that edit HSDS
ACLs out of band (`h5pyd` admin paths, `hsadmin` CLI) will see their
changes overwritten on the next shepard permission change for the
affected container. If you've been editing HSDS ACLs directly and
want shepard's graph to take over cleanly, run the drift-recovery
admin endpoint once:

```bash
curl -X POST https://shepard.example.dlr.de/v2/admin/hdf/rebuild-acls \
  -H "X-API-KEY: <instance-admin-api-key>"
```

Response shape:

```json
{
  "containersProcessed": 42,
  "containersSynced": 41,
  "errors": [
    {"appId": "01HF…", "reason": "hsds.connect-timeout"}
  ]
}
```

The endpoint is idempotent — re-running after a transient HSDS outage
finishes the job.

## What's deferred

The full E7 vision is rolling out across A5a – A5e:

| Phase | Status | What it adds |
|---|---|---|
| **A5a** | shipped | HSDS sidecar + `HdfContainer` create/read/delete + V25 migration. HTTP Basic auth (admin-managed). |
| **A5b** | shipped | Permission bridge — shepard permission changes flow to HSDS ACLs via a `PermissionsService` post-commit hook. |
| A5c | queued | `HdfReference` per-DataObject anchor at a specific dataset path; annotation hookup via E6 (`AnnotatableHdfDataset`). |
| **A5d** | shipped | Download-original-file fallback — `GET /v2/hdf-containers/{appId}/file` returns the byte-identical HDF5 via HSDS bulk-export. Unblocks the offline `h5py.File(local)` path. |
| A5e | queued | Auth bridge — shepard API keys mint short-lived JWTs signed by a shared Keycloak realm. Three-line `clients/python` helper that returns an `h5pyd.File`. |

## See also

- [`aidocs/35-hdf5-hsds-implementation-design.md`](https://github.com/noheton/shepard/blob/main/aidocs/35-hdf5-hsds-implementation-design.md)
  — implementation design for the entire A5 series.
- [`docs/admin.md` §"HDF5 (HSDS)"](/admin/#hdf5-hsds-opt-in-sidecar)
  — operator-side install steps.
- [HSDS](https://github.com/HDFGroup/hsds) — upstream HDF Group
  project that powers the sidecar.
