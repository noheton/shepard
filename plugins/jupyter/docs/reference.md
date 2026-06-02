---
stage: deployed
last-stage-change: 2026-06-02
layout: default
title: Jupyter plugin (reference)
permalink: /reference/jupyter/
---

# shepard-plugin-jupyter — Reference

**Plugin id:** `jupyter`
**Series:** J1e / J2a
**Module:** `plugins/jupyter/` (in-tree, baked into the backend image)

The Jupyter plugin ships two surfaces:

1. **JupyterHub link-out** — an "Open in JupyterHub" action on `.ipynb`
   FileReference rows in the unified data-references table. Controlled
   by `:JupyterConfig {enabled, hubUrl}` at runtime (no backend restart
   needed).
2. **JupyterHub sidecar** — a JupyterHub 5.x container (`jupyterhub`)
   declared in `plugins/jupyter/compose-profile.yml`, mounted at
   `/jupyterhub` on the existing Shepard host. Includes a
   `?file=<url>` pre-spawn hook (J1e-PR-06-AUTOFETCH-01) that
   fetches the `.ipynb` bytes into the user's notebook volume before
   the kernel starts.

Cross-references:
- **Quickstart** (user task): [`plugins/jupyter/docs/quickstart.md`](./quickstart.md)
- **Operator install guide**: [`plugins/jupyter/docs/install.md`](./install.md)
- **Design doc**: [`aidocs/integrations/81-jupyterhub-integration.md`](../../../aidocs/integrations/81-jupyterhub-integration.md)

---

## Neo4j entities

### `:JupyterConfig`

Single-instance node following the A3b / N1c2 / UH1a admin-config
pattern (CLAUDE.md "Always: surface operator knobs in the admin
config"). One node is seeded on first backend startup from the
deploy-time defaults in `application.properties`; subsequent
`PATCH /v2/admin/jupyter/config` calls mutate it in place.

| Property | Type | Default | Description |
|---|---|---|---|
| `appId` | `string` (UUID v7) | minted at creation | Stable cross-substrate identity. `REQUIRE n.appId IS UNIQUE` (V94). |
| `enabled` | `boolean` | `false` | Master switch. When `false`, the "Open in JupyterHub" action is hidden in the data-references table even if `hubUrl` is set. |
| `hubUrl` | `string` (nullable) | `null` | JupyterHub base URL (e.g. `https://shepard.example.org/jupyterhub`). The frontend builds the launch URL as `{hubUrl}/hub/spawn?file={downloadUrl}`. `null` → affordance hidden regardless of `enabled`. |

**Visibility gate:** the "Open in JupyterHub" button renders only when
`enabled === true AND hubUrl !== null` — either knob being clear
suppresses the action.

**Constraint migration:** `V94__Add_appId_constraint_JupyterConfig.cypher`
adds the uniqueness constraint; the rollback twin is
`V94_R__Add_appId_constraint_JupyterConfig.cypher`.

---

## REST endpoints

All endpoints live on the `/v2/` development surface (never
`/shepard/api/`, which is upstream-compat-frozen). Error bodies
follow RFC 7807 `application/problem+json`.

### `GET /v2/jupyter/config`

**Auth:** any authenticated principal (JWT or API key). Returns `401`
for unauthenticated callers.

Read-only view of the `:JupyterConfig` singleton for any authenticated
user. Used by the unified data-references frontend table to decide
whether to render the "Open in JupyterHub" action on `.ipynb` rows.

**Request:**

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.org/v2/jupyter/config
```

**Response — 200 OK:**

```json
{
  "enabled": true,
  "hubUrl": "https://shepard.example.org/jupyterhub"
}
```

```json
{
  "enabled": false,
  "hubUrl": null
}
```

`hubUrl` is `null` when neither the runtime singleton nor the
deploy-time default (`shepard.jupyter.hub-url`) carries a value.

---

### `GET /v2/admin/jupyter/config`

**Auth:** `@RolesAllowed("instance-admin")`.

Returns the current `:JupyterConfig` singleton in the same JSON shape
as the public endpoint. The admin path also supports `PATCH` (below).

**Request:**

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  https://shepard.example.org/v2/admin/jupyter/config
```

**Response — 200 OK:**

```json
{
  "enabled": true,
  "hubUrl": "https://shepard.example.org/jupyterhub"
}
```

**Status codes:**

| Status | When |
|---|---|
| `200 OK` | Config returned. |
| `403 Forbidden` | Caller lacks the `instance-admin` role. |

---

### `PATCH /v2/admin/jupyter/config`

**Auth:** `@RolesAllowed("instance-admin")`.
**Content-Type:** `application/merge-patch+json` or `application/json`.

RFC 7396 merge-patch. Absent fields are left unchanged; an explicit
`null` on `hubUrl` clears the runtime value (falls back to the
deploy-time default). A `null` on `enabled` is treated as "leave
alone" since the field is a non-nullable primitive boolean.

**Patchable fields:**

| Field | Type | Effect |
|---|---|---|
| `enabled` | `boolean` | Flip the master switch. Absent or `null` → unchanged. |
| `hubUrl` | `string` or `null` | Set the hub URL. `null` → clear the runtime value (reverts to `shepard.jupyter.hub-url` deploy-time default). Non-null must be a syntactically valid absolute `http` or `https` URL. |

**Request — enable the link-out and set the hub URL:**

```bash
curl -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": true, "hubUrl": "https://shepard.example.org/jupyterhub"}' \
  https://shepard.example.org/v2/admin/jupyter/config
```

**Request — disable without changing the URL:**

```bash
curl -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": false}' \
  https://shepard.example.org/v2/admin/jupyter/config
```

**Request — clear the runtime hub URL (revert to deploy-time default):**

```bash
curl -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"hubUrl": null}' \
  https://shepard.example.org/v2/admin/jupyter/config
```

**Response — 200 OK** (updated config in the same shape as GET):

```json
{
  "enabled": true,
  "hubUrl": "https://shepard.example.org/jupyterhub"
}
```

**Status codes:**

| Status | When |
|---|---|
| `200 OK` | Patch applied; updated config returned. |
| `400 Bad Request` | `hubUrl` was non-null but not a valid absolute `http(s)` URL. RFC 7807 body, type `/problems/jupyter.config.invalid-hub-url`. |
| `403 Forbidden` | Caller lacks the `instance-admin` role. |

**Audit trail:** PROV1a's `ProvenanceCaptureFilter` records each PATCH
as an `:Activity` row (`targetKind=JupyterConfig`). Query who changed
the config:

```cypher
MATCH (a:Activity)
WHERE a.resourcePath = '/v2/admin/jupyter/config'
  AND a.httpMethod = 'PATCH'
RETURN a.userId, a.startedAt, a.appId
ORDER BY a.startedAt DESC
```

---

## Admin config keys (`application.properties`)

| Key | Default | Purpose | Runtime-mutable? |
|---|---|---|---|
| `shepard.jupyter.enabled` | `false` | Seeds `:JupyterConfig.enabled` on first startup. | Yes — via `PATCH /v2/admin/jupyter/config` or `shepard-admin jupyter enable` |
| `shepard.jupyter.hub-url` | (empty) | Seeds `:JupyterConfig.hubUrl` on first startup. Once an operator PATCHes the runtime singleton, this becomes a fallback-only default. | Yes — via `PATCH /v2/admin/jupyter/config` or `shepard-admin jupyter set-hub-url <url>` |

**Precedence:** the runtime `:JupyterConfig` value always wins.
Deploy-time keys are install defaults that seed the singleton on first
start; they do not override a runtime flip. Setting `hubUrl` to `null`
via PATCH reverts to the deploy-time default.

---

## Sidecar env vars

Set in `infrastructure/.env` (template: `infrastructure/.env.example`).
These configure the JupyterHub sidecar container, not the Shepard
backend.

| Env var | Required? | Default | Notes |
|---|---|---|---|
| `OIDC_AUTHORITY` | already present | — | Keycloak realm URL shared with the backend. JupyterHub reads it as `SHEPARD_OIDC_ISSUER_URL`. |
| `JUPYTERHUB_KEYCLOAK_CLIENT_ID` | yes | `jupyterhub` | Keycloak confidential client ID for JupyterHub's OIDC flow. |
| `JUPYTERHUB_KEYCLOAK_CLIENT_SECRET` | yes | — | Confidential client secret. **Do not commit.** |
| `JUPYTERHUB_PUBLIC_URL` | yes | `https://shepard.nuclide.systems/jupyterhub` | Public URL JupyterHub redirects users to after OIDC sign-in. Must match Keycloak's valid-redirect-URI. |
| `JUPYTERHUB_SINGLEUSER_IMAGE` | no | `jupyter/scipy-notebook:python-3.11` | Single-user notebook image spawned per user. |
| `SHEPARD_BACKEND_URL` | no | `http://backend:8080` | Shepard backend URL injected into the kernel environment as `SHEPARD_BACKEND_URL`. |
| `JUPYTERHUB_SHEPARD_ALLOWED_HOSTS` | no | `shepard.nuclide.systems,shepard-api.nuclide.systems` | Comma-separated allowlist of hostnames the `?file=<url>` pre-spawn hook may fetch from. SSRF defense. Add your deployment's actual hostnames. |
| `JUPYTERHUB_DOCKER_NETWORK` | no | `infrastructure_shepard` | Docker compose network the DockerSpawner attaches single-user containers to. |
| `JUPYTERHUB_CRYPT_KEY` | no | (empty) | 32-byte hex string for encrypting OIDC tokens stored server-side in JupyterHub's auth_state. Strongly recommended for production. |

---

## CLI commands (`shepard-admin jupyter`)

The `jupyter` subcommand group provides CLI parity for the admin
REST surface. All commands share the L1 baseline flags: `--url`,
`--api-key`, `--output={human,json}`, `--verbose`.

```text
shepard-admin jupyter status
shepard-admin jupyter enable
shepard-admin jupyter disable
shepard-admin jupyter set-hub-url <url>
```

### `shepard-admin jupyter status`

Reads `GET /v2/admin/jupyter/config` and prints the current
`enabled` + `hubUrl` pair.

```bash
$ shepard-admin jupyter status
enabled:  false
hub-url:  (not set)
```

```bash
$ shepard-admin jupyter status --output=json
{
  "enabled": true,
  "hubUrl": "https://shepard.example.org/jupyterhub"
}
```

### `shepard-admin jupyter enable`

Sets `enabled=true` via `PATCH /v2/admin/jupyter/config`. Idempotent.

```bash
$ shepard-admin jupyter enable
JupyterHub link-out enabled.
```

### `shepard-admin jupyter disable`

Sets `enabled=false`. The hub URL is preserved.

```bash
$ shepard-admin jupyter disable
JupyterHub link-out disabled.
```

### `shepard-admin jupyter set-hub-url <url>`

Sets `hubUrl` to the provided absolute `http(s)` URL.

```bash
$ shepard-admin jupyter set-hub-url https://shepard.example.org/jupyterhub
Hub URL set: https://shepard.example.org/jupyterhub
```

Pass an empty string or `null` to clear the runtime value (reverts to
the deploy-time default):

```bash
$ shepard-admin jupyter set-hub-url ""
Hub URL cleared (reverts to deploy-time default).
```

---

## `?file=<url>` pre-spawn hook (J1e-PR-06-AUTOFETCH-01)

The JupyterHub sidecar's `jupyterhub_config.py` installs a
`pre_spawn_hook` that reads `?file=<url>` from the spawn request
(appended by Shepard's "Open in JupyterHub" action on FileReference
rows), fetches the file using the user's forwarded OIDC access token,
and writes it into the user's notebook volume at:

```
/home/jovyan/work/shepard-imports/<filename>
```

A `README-<filename>.md` sidecar lands next to it recording the source
URL, fetch timestamp, and status code.

### Allowlist (SSRF defence)

The hook validates the URL's hostname against
`JUPYTERHUB_SHEPARD_ALLOWED_HOSTS` **before making any outbound
request**. Hosts not in the list are rejected immediately; the kernel
still spawns, and the user sees a `README-*.md` with
`Status: allowlist-miss`.

### Status values in `README-<filename>.md`

| Status | Meaning |
|---|---|
| `OK` | File fetched and written to the workspace. |
| `401` / `403` | Shepard rejected the user's token. User should re-authenticate and re-spawn. |
| `allowlist-miss` | Hostname not in `JUPYTERHUB_SHEPARD_ALLOWED_HOSTS`. Operator must add it. |
| `timeout` | Backend didn't respond within 300 s. User should re-spawn or fetch manually. |
| `no-token` | OIDC access token was absent from auth_state. User should sign out and back in. |
| `error: <Name>` | Unexpected exception. Show the README to the operator. |

### Manual fallback (kernel-side)

When the pre-spawn hook cannot deliver the file, users can fetch it
directly inside the notebook using the `SHEPARD_OIDC_ACCESS_TOKEN`
environment variable injected into every kernel:

```python
import os, requests

token = os.environ["SHEPARD_OIDC_ACCESS_TOKEN"]
resp = requests.get(
    "https://shepard.example.org/v2/files/<appId>/payload",
    headers={"Authorization": f"Bearer {token}"},
)
resp.raise_for_status()
with open("my-download.bin", "wb") as f:
    f.write(resp.content)
```

---

## Data flow

```
User clicks "Open in JupyterHub" on an .ipynb FileReference row
       ↓
Frontend builds: {hubUrl}/hub/spawn?file={encoded-content-url}
       ↓
JupyterHub redirects to Keycloak (same realm as Shepard)
       ↓
Pre-spawn hook: validates ?file= URL against allowlist → fetches bytes →
  writes /home/jovyan/work/shepard-imports/<filename> + README sidecar
       ↓
Kernel spawns with SHEPARD_OIDC_ACCESS_TOKEN + SHEPARD_BACKEND_URL
       ↓
User opens the notebook — file is already in the workspace
       ↓
Kernel calls Shepard REST API (e.g. /v2/timeseries/...) with forwarded token
       ↓
Results → notebook cells → analysis → (user uploads derived data separately)
```

---

## Known limitations

1. **No write-back.** Files fetched by the pre-spawn hook and
   notebook cells are copies inside the user's volume. Modifications
   do NOT propagate back to Shepard automatically. Users who produce
   derived data worth keeping must upload it manually via the Shepard
   UI or `shepard-py` client (pre-installed in the scipy-notebook
   image).

2. **DockerSpawner requires the docker socket.** The default
   `DockerSpawner` mounts `/var/run/docker.sock`. For hardened
   deployments, switch to `KubeSpawner` (see
   [`install.md §9`](./install.md#9-kubernetes-notes)).

3. **Pre-spawn hook is not available without the sidecar.** The
   `?file=` autofetch requires the JupyterHub sidecar running with
   the `compose-profile.yml` overlay. If an operator points
   `shepard.jupyter.hub-url` at an external JupyterHub that wasn't
   configured with the Shepard pre-spawn hook, the kernel spawns but
   no file is pre-fetched.

4. **Single-user image size.** The default `jupyter/scipy-notebook`
   is ~3 GB. First spawn on a fresh host pulls the image; operators
   should pre-pull it: `docker pull jupyter/scipy-notebook:python-3.11`.

5. **CLI commands are in-tree pending J1e-PR-03 plugin move.**
   `shepard-admin jupyter` currently lives in the core CLI module.
   The plugin relocation (J1e-PR-03) is tracked in
   `aidocs/16-dispatcher-backlog.md`.

6. **REST path pending relocation.** Post-J1e-PR-07, the admin path
   will move from `/v2/admin/jupyter/config` to
   `/v2/admin/plugins/jupyter/config`. A wire-compat shim will keep
   both paths live during the deprecation window. Until that PR
   ships, use `/v2/admin/jupyter/config`.
