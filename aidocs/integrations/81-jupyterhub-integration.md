---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 81 — JupyterHub Integration

**Status:** design
**Audience:** contributors, operators, researchers

---

## Motivation

Shepard stores research data; JupyterHub is where researchers analyse it.
Today the two are separate: a researcher exports a CSV from Shepard, uploads it to
a Jupyter environment, runs analysis, then manually pastes results back.
This document describes a plugin-first integration that collapses that round-trip
into a seamless, auth-transparent workflow:

- Open Shepard → click "Analyse in Jupyter" → a pre-populated notebook opens with the data already loaded.
- Inside Jupyter → run analysis → call `shepard.upload(result_df, "Results")` → data appears in the same DataObject.
- Auth is shared (Keycloak / OIDC) — one login, both systems.

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│  Keycloak (shared OIDC provider)                            │
│  realm: shepard-demo   client: frontend-dev (public)        │
│         + client: jupyterhub (confidential, token exchange) │
└───────────────┬─────────────────────────────────────────────┘
                │ OIDC
        ┌───────┴────────┐
        │  JupyterHub    │          ┌──────────────────────┐
        │  (Kubernetes   │◄─────────┤ shepard-kernel-ext   │
        │   or compose)  │  Bearer  │ (JupyterLab plugin)  │
        └───────┬────────┘          └──────────────────────┘
                │ user token forwarded to kernel
        ┌───────▼──────────────┐
        │  User Jupyter kernel │
        │  shepard-py (SDK)    │──► POST /v2/collections/…/data-objects
        └──────────────────────┘    GET  /v2/…/timeseries-references/{}/payload
```

---

## Components

### J2a — JupyterHub OIDC login (Keycloak-backed)

JupyterHub's `GenericOAuthenticator` points at the same Keycloak realm already
used by Shepard. Users log in once and get tokens valid for both.

Config (`jupyterhub_config.py`):

```python
from oauthenticator.generic import GenericOAuthenticator
c.JupyterHub.authenticator_class = GenericOAuthenticator
c.GenericOAuthenticator.oauth_callback_url  = "https://jupyter.example.com/hub/oauth_callback"
c.GenericOAuthenticator.client_id           = "jupyterhub"
c.GenericOAuthenticator.client_secret       = "${JUPYTER_OIDC_CLIENT_SECRET}"
c.GenericOAuthenticator.token_url           = "https://auth.example.com/realms/shepard-demo/protocol/openid-connect/token"
c.GenericOAuthenticator.userdata_url        = "https://auth.example.com/realms/shepard-demo/protocol/openid-connect/userinfo"
c.GenericOAuthenticator.userdata_params     = {"state": "state"}
c.GenericOAuthenticator.username_key        = "preferred_username"
# Forward the user's Shepard access_token into the kernel environment
c.GenericOAuthenticator.token_request_kwargs = {"scope": "openid profile email offline_access"}
c.Authenticator.enable_auth_state       = True
c.KernelSpecManager.ensure_native_kernel = True
```

The access token (short-lived, Keycloak-issued) is injected into each user
kernel's environment as `SHEPARD_ACCESS_TOKEN`. The kernel SDK reads it on
import so every `shepard.client()` call is pre-authenticated.

### J2b — `shepard-py` SDK (Python package)

A thin wrapper around the `/v2/` REST surface. Installable in the kernel image:

```
pip install shepard-py
```

Core API:

```python
import shepard

# Auto-reads SHEPARD_ACCESS_TOKEN + SHEPARD_BASE_URL from environment.
client = shepard.client()

# Load a timeseries reference as a DataFrame.
df = client.load_timeseries_reference(
    collection="lumen-dataset",     # appId or name
    data_object="Run-001",
    reference="Measurements",       # name or appId
)

# Upload a DataFrame back as a new timeseries reference.
ref = client.upload_timeseries(
    collection="lumen-dataset",
    data_object="Run-001",
    reference_name="Analysis results",
    df=result_df,                   # columns = channel names, index = datetime
    description="Anomaly scores from LSTM model",
)

# Upload a file.
client.upload_file(
    collection="lumen-dataset",
    data_object="Run-001",
    path_or_bytes=report_pdf,
    filename="anomaly-report.pdf",
)
```

Internally, `load_timeseries_reference` calls:
```
GET /v2/collections/{c}/data-objects/{do}/timeseries-references/{ref}/payload
```
and converts the JSON to a `pd.DataFrame` with a `DatetimeTZDtype` index (UTC).

`upload_timeseries` calls:
```
POST /v2/files   (CSV upload)
POST /v2/collections/{c}/data-objects/{do}/timeseries-references
```

### J2c — JupyterLab data picker extension (`shepard-kernel-ext`)

A JupyterLab 4 panel (sidebar widget) that lets users browse their Shepard
collections and click **"Load into kernel"**. Under the hood it:

1. Calls `GET /v2/collections?mine=true` to list accessible collections.
2. Lets the user drill into DataObjects → references.
3. On "Load", injects a code cell into the active notebook:

```python
import shepard
df = shepard.client().load_timeseries_reference(
    collection="<appId>",
    data_object="<appId>",
    reference="<appId>",
)
df.head()
```

The picker is a standard JupyterLab 4 frontend extension (TypeScript + Lumino)
distributed via npm and built into the kernel image. Auth token is read from the
same kernel environment variable `SHEPARD_ACCESS_TOKEN`.

### J2d — "Send to Shepard" magic / template cell

A `%shepard_upload` IPython magic and a standard notebook template section that
appears in every new notebook created from the Shepard picker:

```python
# =============================================================================
# § Results — upload back to Shepard
# Run this cell when analysis is complete.
# =============================================================================
import shepard

# Edit the reference name if you want a different label in Shepard.
_result_name = "Analysis results"

shepard.client().upload_timeseries(
    collection=_SHEPARD_COLLECTION,   # injected by picker
    data_object=_SHEPARD_DATA_OBJECT, # injected by picker
    reference_name=_result_name,
    df=result_df,                     # <-- replace with your output DataFrame
    description=f"Uploaded from Jupyter notebook: {__file__}",
)
print(f"Uploaded '{_result_name}' to {_SHEPARD_DATA_OBJECT}")
```

The picker injects `_SHEPARD_COLLECTION` and `_SHEPARD_DATA_OBJECT` as kernel
variables when it creates a notebook, so the upload cell knows where to send
results without the user having to copy-paste IDs.

---

## Deployment

### docker-compose profile (`jupyter` profile)

```yaml
# infrastructure/docker-compose.override.yml  (addition)
  jupyterhub:
    image: jupyterhub/jupyterhub:4
    profiles: [jupyter]
    ports:
      - "8083:8000"
    volumes:
      - ./jupyter/jupyterhub_config.py:/srv/jupyterhub/jupyterhub_config.py:ro
      - jupyter-data:/srv/jupyterhub
    environment:
      JUPYTER_OIDC_CLIENT_SECRET: "${JUPYTER_OIDC_CLIENT_SECRET}"
      SHEPARD_BASE_URL: "https://shepard-api.nuclide.systems"
    networks:
      - shepard
    depends_on:
      - keycloak
    restart: unless-stopped

volumes:
  jupyter-data:
```

Bring up: `docker compose --profile jupyter up -d`

The Zoraxy rule for `https://jupyter.nuclide.systems → 192.168.1.49:8083`
completes the public URL routing (operator adds it after deploying).

### Keycloak client setup

Add a new **confidential client** `jupyterhub` to the `shepard-demo` realm:
- Valid redirect URIs: `https://jupyter.example.com/hub/oauth_callback`
- Direct access grants: enabled (for token exchange / service accounts)
- Set `JUPYTER_OIDC_CLIENT_SECRET` from the generated secret.

The existing `frontend-dev` public client stays unchanged — Shepard's frontend
auth is unaffected.

### Kernel Docker image

```dockerfile
FROM jupyter/scipy-notebook:latest
RUN pip install shepard-py jupyterlab-shepard-ext
```

Push to GHCR alongside the Shepard images (same build workflow).
Kernel spec path registered automatically by `jupyterlab-shepard-ext`.

---

## Security

- Access tokens are short-lived (Keycloak default: 5 min). The SDK auto-refreshes
  using the `offline_access` refresh token injected into the kernel at spawn time.
- Tokens never leave the kernel environment variable — they are not logged or
  stored in notebooks (the SDK reads `SHEPARD_ACCESS_TOKEN` at call time).
- Shepard's permission model applies end-to-end: if a user can't read a
  DataObject in the UI, their Jupyter session can't load it via the SDK either.
- The JupyterHub `GenericOAuthenticator` only allows users who already have
  accounts in the Keycloak realm — no separate user provisioning.

---

## Data access patterns

| Workflow | SDK call | Shepard endpoint |
|---|---|---|
| Load timeseries as DataFrame | `client.load_timeseries_reference(...)` | `GET /v2/…/timeseries-references/{}/payload` |
| Load file into bytes / path | `client.download_file(...)` | `GET /v2/files/{appId}/download` |
| Upload analysis result as TS | `client.upload_timeseries(...)` | `POST /v2/…/timeseries-references` |
| Upload output file | `client.upload_file(...)` | `POST /v2/files` |
| List my collections | `client.list_collections()` | `GET /v2/collections?mine=true` |
| Get DataObject metadata | `client.get_data_object(...)` | `GET /v2/collections/{}/data-objects/{}` |

All endpoints use `appId` as the primary identifier (L2d conventions). Name-based
lookups are syntactic sugar in the SDK: `client.load_timeseries_reference(collection="lumen-dataset", ...)`
resolves the name to an `appId` via a search call before hitting the payload endpoint.

---

## Implementation sketch

| Step | Artefact | Notes |
|---|---|---|
| J2a | `infrastructure/jupyter/jupyterhub_config.py` | OIDC + token forwarding |
| J2a | Keycloak realm: add `jupyterhub` client | Import via realm JSON patch |
| J2b | `shepard-py` Python package | `GET /v2/` wrapper + DataFrame helpers |
| J2c | `jupyterlab-shepard-ext` npm package | Sidebar picker + code injection |
| J2d | Template notebook shipped in kernel image | Upload-back pattern cells |
| — | `build-images.yml` extension | Build + push kernel image to GHCR |
| — | `docker-compose.override.yml` | `jupyter` profile service entry |

---

## Decided

### D1 — Notebooks + artifacts → Shepard by default (J2e)

When a user finishes a Jupyter session, the `.ipynb` file and any produced
output artifacts (CSVs, PNGs, model files) are pushed to Shepard as a
**"Jupyter Analysis" DataObject** by default. This is template-driven: the
"Jupyter Analysis" template pre-defines the structure (file container for
the notebook + artifacts, lab journal entry summarising the run, predecessor
link back to the source DataObject the analysis was opened from).

If `shepard-plugin-git` is active, the notebook is additionally committed to
the DataObject's git repository (same session, same commit). Artifacts follow
the same file-reference path as other container uploads.

The J2d upload-back cell becomes the implementation of this default: it runs
automatically on kernel shutdown unless the user explicitly opts out
(`SHEPARD_AUTOSAVE=0`). The picker injects the template appId alongside the
source DataObject appId so the auto-save cell knows which template to use.

**New component:** `J2e` — auto-save on kernel shutdown + "Jupyter Analysis"
template seed in `__templates`.

### D2 — Collection picker: mine / watched / all

The J2c sidebar picker shows three views, not one:

| View | Query | Requires |
|---|---|---|
| **Mine** | `GET /v2/collections?createdBy=me` | `createdBy=me` filter (to implement) |
| **Watched** | `GET /v2/collections?watched=true` | collection watches feature (WATCH1, shipped) |
| **All** | `GET /v2/collections` (paginated) | exists today |

Default view on open: **Mine**. Tab strip at top of picker panel switches views.
The `createdBy=me` filter is a small addition to `CollectionV2Rest`; `watched=true`
is backed by the existing `CollectionWatcher` relationship.

### D3 — GPU kernel: detect at runtime / boot, not hardcoded

JupyterHub kernel profiles are decided at **Shepard boot time** (or re-evaluated
on admin config change) rather than baked into the compose file:

- At startup, Shepard checks for GPU availability (NVIDIA SMI presence /
  CUDA device count via a lightweight probe script in the `jupyter` profile).
- The result is stored in `:JupyterConfig` (a runtime admin config node,
  per the standard `:*Config` pattern from `CLAUDE.md`).
- `GET /v2/admin/jupyter/config` returns `{ "gpuAvailable": true/false,
  "gpuKernelEnabled": true/false }`.
- `PATCH /v2/admin/jupyter/config` lets an admin enable/disable the GPU
  kernel profile at runtime without restart — useful when GPU nodes are
  added to an existing cluster.
- JupyterHub's `KernelSpecManager` is configured to read this flag on
  spawner start; if `gpuKernelEnabled=false`, the GPU profile is hidden
  from the user's kernel picker.

This follows the same pattern as `aidocs/semantics/65` (ontology runtime
config) and `aidocs/integrations/67` (Unhide runtime config).

---

## Open questions / deferred

- **Collaborative editing**: JupyterHub's default is per-user. Real-time
  multi-user editing requires JupyterLab RTC or a separate nbgrader setup.

---

## Related

- `aidocs/platform/47-dev-experience-and-plugin-system.md` — plugin SPI
- `aidocs/integrations/67-unhide-publish-plugin.md` — pattern for compose-profile plugins
- `aidocs/ux/78-containerless-basic-mode.md` — basic mode framing
- `aidocs/platform/26-git-integration.md` — shepard-plugin-git (D1 dependency)
- L2d — appId as primary identifier in `/v2/`
- `shepard-py` package (to be created; analogous to upstream `shepard-client`)
