---
title: Notebook references (reference)
---

# Notebook references — reference

This page documents how Shepard surfaces `.ipynb` files as first-class
**Notebook** rows in the unified Data References table, the admin-mutable
JupyterHub link-out gate (J1e), and the additive REST endpoints both rely on.

## What a Notebook row is

A Notebook row is a FR1b singleton `:FileReference` whose attached
`ShepardFile.filename` ends in `.ipynb` (case-insensitive). The classifier
runs in the frontend — the backend simply returns the FR1b shape. Result:

- **Type column:** "Notebook" with the `mdi-notebook-outline` icon
- **Meta column:** filename + file size chips
- **Actions column:** download (always), open in JupyterHub (conditional),
  annotate (always), delete (when the user has write permission)

A FR1b singleton whose filename is `analysis.csv` shows up as **File** —
same row shape, different icon, no JupyterHub action.

## Wire shape

### List notebooks attached to a DataObject

The unified-table composable fetches FR1b singletons via:

```http
GET /v2/files/by-data-object/{dataObjectAppId}
→ 200 [FileReferenceV2IO]   (may be empty)
→ 404 when no DataObject with that appId
→ 403 when the caller lacks Read on the parent Collection
```

The classifier runs client-side, so this endpoint returns every FR1b
singleton attached to the DataObject — not just the notebooks.

### Open in JupyterHub gate

```http
GET /v2/jupyter/config             → 200 { enabled, hubUrl }    (any authenticated user)
GET /v2/admin/config/jupyter       → 200 same shape             (instance-admin)
PATCH /v2/admin/config/jupyter     → RFC 7396 merge-patch       (instance-admin)
```

The PATCH body is the runtime-mutable subset:

```json
{ "enabled": true, "hubUrl": "https://hub.example.org" }
```

Both fields are tri-state per RFC 7396 (absent / null / value). The backend
validates `hubUrl` as an absolute http(s) URL with a non-empty host;
anything else returns `400` with a `application/problem+json` body.

### Launch URL composition

The launch URL is composed client-side from `JupyterConfig.hubUrl` plus the
FR1b download URL:

```
{hubUrl}/hub/spawn?file={shepardBaseUrl}/v2/files/{appId}/content
```

The hub-side script that imports the URL into the user's notebook workspace
is left to the operator — Shepard does not assume a specific JupyterHub
extension.

## Config fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `enabled` | bool | `false` | Master switch for the "Open in JupyterHub" row action. |
| `hubUrl` | string | `null` | Absolute http(s) URL. Falls back to the deploy-time `shepard.jupyter.hub-url` when null. |

### Deploy-time properties

```properties
shepard.jupyter.enabled=false
shepard.jupyter.hub-url=https://hub.example.org
```

These seed the `:JupyterConfig` singleton on first start. Runtime PATCHes
win after that; clearing a field via `PATCH {field: null}` reverts to the
deploy-time default.

## CLI

The `shepard-admin jupyter` subcommand group provides parity:

```bash
shepard-admin jupyter status                                 # GET /v2/admin/config/jupyter
shepard-admin jupyter enable                                 # PATCH enabled=true
shepard-admin jupyter disable                                # PATCH enabled=false
shepard-admin jupyter set-hub-url https://hub.example.org    # PATCH hubUrl=...
shepard-admin jupyter set-hub-url ""                         # PATCH hubUrl=null (clear)
```

Shared flags from `AbstractCommand`: `--output={human,json}`, `--url`,
`--api-key`, plus the standard env equivalents.

## Neo4j shape

The singleton is a `:JupyterConfig` node with the `HasAppId` interface
implemented:

```cypher
(j:JupyterConfig { appId: <UUID v7>, enabled: <bool>, hubUrl: <string|null> })
```

The `V94__Add_appId_constraint_JupyterConfig.cypher` migration creates a
`REQUIRE appId IS UNIQUE` constraint guaranteeing the singleton invariant
at the database boundary; the rollback twin
`V94_R__Add_appId_constraint_JupyterConfig.cypher` drops it. PATCH mutations
land in `:Activity` via the `ProvenanceCaptureFilter` automatic admin
capture.

## See also

- `docs/help/work-with-notebooks.md` — researcher-facing walk-through.
- `docs/admin/runbooks/jupyterhub-config.md` — operator runbook.
- `docs/reference/file-reference.md` — the FR1b singleton reference type
  this builds on.
- `aidocs/integrations/81-jupyterhub-integration.md` — original J-series
  design.
