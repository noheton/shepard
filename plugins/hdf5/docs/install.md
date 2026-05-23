---
title: hdf5 — Install
stage: deployed
last-stage-change: 2026-05-23
audience: plugin-author
synthetic_batch: true
generation_rule: feedback_no_synthetic_provenance.md
---

> 🤖 **BACKFILL — created retroactively 2026-05-23 by Claude Opus 4.7**
> per the docs-gap audit at `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
> The plugin's behaviour is documented from the source code as it stood
> at commit `8bdc8c6163ee4ea88acde244a1c7e9672ab593a3`. If anything is
> inaccurate, the source is authoritative; please open a PR or issue.

# hdf5 — install

`shepard-plugin-hdf5` adds the HDF5/HSDS payload kind. The plugin
delegates the heavy lifting to an
[HSDS](https://github.com/HDFGroup/hsds) sidecar — HDF Group's
service that exposes HDF5 files over HTTP. Install requires
running HSDS alongside the backend.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven
  profile (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-hdf5-${revision}.jar`.
- A running HSDS sidecar reachable from the backend (the `hdf`
  compose profile in `infrastructure/` provides one).
- Persistent storage for HSDS's domain files (a Docker volume,
  NFS mount, or S3 bucket depending on HSDS deployment mode).

---

## HSDS sidecar

The `infrastructure/` directory ships an `hdf` compose profile:

```bash
cd infrastructure
docker compose --profile hdf up -d shepard-hsds
```

To verify:

```bash
docker compose exec shepard-hsds curl -sS http://localhost:5101/about
```

Returns the HSDS version and node-count info.

For production, see [HSDS deployment docs](https://github.com/HDFGroup/hsds/blob/master/docs/docker_install.md)
— common production patterns are S3-backed HSDS with multiple
SN/DN nodes.

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.hdf5.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |
| `shepard.hdf.enabled` | `false` | **Master toggle for the HDF5 surface.** With `false`, every `/v2/hdf-containers/*` returns 404 and no HSDS client is instantiated. |
| `shepard.hdf.hsds.endpoint` | (empty) | HSDS base URL, e.g. `http://shepard-hsds:5101`. **Required when `shepard.hdf.enabled=true`** — startup fails fast if missing. |
| `shepard.hdf.hsds.username` | (empty) | HSDS admin username. Must match `HSDS_USERNAME` on the HSDS side. |
| `shepard.hdf.hsds.password` | (empty) | HSDS admin password. **Required when `shepard.hdf.enabled=true`** — startup fails fast on blank credentials. |

Minimal `application.properties` for a development install:

```properties
shepard.plugins.hdf5.enabled=true
shepard.hdf.enabled=true
shepard.hdf.hsds.endpoint=http://shepard-hsds:5101
shepard.hdf.hsds.username=admin
shepard.hdf.hsds.password=admin
```

Production: replace the HSDS basic-auth credentials with values
from a secret manager — `JAVA_OPTS` env-var injection is the
common pattern.

---

## Phase 1 auth model

Phase 1 (the current shape) supports **HTTP Basic only**. The
backend's HSDS admin credentials are used for all HSDS-side
calls; shepard's per-user permission checks happen in the backend
before the HSDS call fires.

Phase 5 (A5e, deferred) replaces this with per-user OIDC token
relay through a shared Keycloak realm. Until then, **don't expose
HSDS directly to end users** — the backend is the security
perimeter.

---

## Healthcheck

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems

# 1. Plugin registry.
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "hdf5")'

# 2. Create + read a container — exercises both Neo4j + HSDS.
curl -X POST -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"name": "smoke", "description": "hdf5 install verification"}' \
  "$SHEPARD_URL/v2/hdf-containers"
```

A 201 confirms both legs of the system: shepard wrote the
`:HdfContainer` Neo4j node AND provisioned the matching HSDS
domain (`/shepard/<appId>/`).

If the create call returns 503, HSDS is unreachable — re-check
`shepard.hdf.hsds.endpoint` and `docker compose ps shepard-hsds`.

---

## Permission bridge (A5b)

shepard is the **source of truth** for HDF container ACLs. When
permissions change on a `:Collection` containing
`:HdfContainer` rows, the `HdfPermissionBridge` CDI observer
rewrites the matching HSDS domain's ACL via the sidecar's REST
API. Mapping:

| shepard role | HSDS POSIX bits |
|---|---|
| Reader | `read` |
| Writer | `read,update,create,delete` |
| Manager | `read,update,create,delete,update_acl` |

Sync is **best-effort**. Direct HSDS-side ACL edits get clobbered
on the next permission write. The drift-recovery endpoint
`POST /v2/admin/hdf/rebuild-acls` reconciles cleanly:

```bash
curl -X POST -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/hdf/rebuild-acls"
```

---

## Disabling the plugin

The runtime hard-disable:

```properties
shepard.hdf.enabled=false
```

All `/v2/hdf-containers/*` endpoints return 404. The
`:HdfContainer` nodes are preserved; HSDS domains stay live (no
delete cascade).

The plugin-level toggle (`shepard.plugins.hdf5.enabled=false`)
disables the lifecycle hook and skips the HSDS client
construction entirely.

---

## Known pitfalls

- **HSDS startup latency**. HSDS takes ~5–15s to become ready
  on first boot. The backend's HSDS healthcheck retries on
  startup but still **fails fast** if HSDS is unreachable after
  three attempts.
- **Basic-auth credentials blank** while `shepard.hdf.enabled=true`.
  Startup aborts — Phase 1 refuses to run in "ambient auth"
  mode. Either set the credentials or flip `shepard.hdf.enabled=false`.
- **HSDS domain quota**. HSDS doesn't enforce per-domain size
  caps by default; a runaway container can fill the storage
  backing. Set HSDS's `MAX_TBL_SIZE` env var to cap individual
  dataset size.
- **`POSIX_RW_DROPPED` warnings from HSDS**. Indicates a
  permission-bridge call partially failed (the user's ACL entry
  was written, but the per-dataset chmod failed). Run
  `POST /v2/admin/hdf/rebuild-acls` to reconcile.
- **Direct `h5pyd` edits get clobbered**. Tools that edit HSDS
  ACLs out-of-band see their changes overwritten on the next
  shepard permission write. Use `rebuild-acls` to retake the
  shepard graph as the source of truth.

---

## Phase rollout status

| Phase | Status | What it adds |
|---|---|---|
| A5a | shipped | Container CRUD + V25 migration + HTTP Basic. |
| A5b | shipped | Permission bridge. |
| A5c | queued | `HdfReference` per-DataObject anchor + annotations. |
| A5d | shipped | `GET /v2/hdf-containers/{appId}/file` byte-identical download. |
| A5e | queued | Per-user OIDC token relay to HSDS. |

See [`reference.md` §"What's deferred"](reference.md#whats-deferred)
for details.

---

## See also

- [`reference.md`](reference.md) — payload kind, REST surface,
  ACL bridge.
- [`quickstart.md`](quickstart.md) — upload your first HDF5 file.
- `aidocs/35-hdf5-hsds-implementation-design.md` — full A5 design.
- [HSDS upstream](https://github.com/HDFGroup/hsds).
- [HDF5 spec](https://docs.hdfgroup.org/hdf5/develop/).
