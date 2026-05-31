---
title: JupyterHub link-out config (J1e)
nav_order: 95
audience: admin
---

# JupyterHub link-out — admin runbook

**Audience:** Shepard instance admins. **Risk:** low (purely a UI gate; no
data is moved). **Estimated time:** 2 minutes.

## What this controls

The unified Data References table on every DataObject detail page shows a
per-notebook **Open in JupyterHub** action whenever an attached file's name
ends in `.ipynb`. The action is hidden unless the instance-wide
`:JupyterConfig` singleton has BOTH:

1. `enabled = true` (the master switch), AND
2. `hubUrl` set to an absolute http(s) URL pointing at your JupyterHub
   instance.

When both knobs resolve truthy, the button opens
`{hubUrl}/hub/spawn?file={download-url}` in a new tab.

## Wire shape

```http
GET    /v2/admin/jupyter/config    → 200 { enabled, hubUrl }
PATCH  /v2/admin/jupyter/config    → RFC 7396 merge-patch (instance-admin)
GET    /v2/jupyter/config          → 200 same shape (any authenticated user)
```

The public `GET /v2/jupyter/config` exists so the frontend can decide whether
to render the launch button without forcing every user through an
instance-admin role check. PATCH is admin-only and is captured as an
`:Activity` row via the `ProvenanceCaptureFilter`.

## Pick the right launch-URL convention

Shepard's launch URL shape is intentionally vanilla:

```
{hubUrl}/hub/spawn?file={downloadUrl}
```

If your JupyterHub deployment expects a different shape, configure the hub to
accept `?file=` as a fetch hint, or interpose a small proxy that rewrites the
URL. Common JupyterHub-side conventions you may want to align to:

| Convention | Path shape | Typical use |
| --- | --- | --- |
| `nbgitpuller` | `{hub}/hub/user-redirect/git-pull?repo=<git>&urlpath=...` | Pull a git repo, open a specific path |
| `urlpath` redirect | `{hub}/user-redirect/notebooks/<path>` | Open an existing notebook on the user's volume |
| Vanilla spawn | `{hub}/hub/spawn` | Start a fresh server |

Shepard's `?file=` argument carries the Shepard-side download URL
(`/v2/files/{appId}/content`); the JupyterHub-side script that imports the
file into the user's workspace is left to the operator.

## Configuring the knob

### Via the admin UI

1. Navigate to **Administration → JupyterHub link-out**.
2. Click **Edit**.
3. Flip the **Enabled** switch and enter your hub URL.
4. **Save**.

The data-references table picks up the change on the next page load (the
public `/v2/jupyter/config` endpoint is fetched per page render and is not
cached client-side).

### Via the CLI

The `shepard-admin` CLI ships parity verbs:

```bash
shepard-admin jupyter status                                   # read
shepard-admin jupyter enable                                   # flip on
shepard-admin jupyter disable                                  # flip off (hubUrl preserved)
shepard-admin jupyter set-hub-url https://hub.example.org      # set the URL
shepard-admin jupyter set-hub-url ""                           # clear back to deploy default
```

All four subcommands honour `--output={human,json}` for scripting and use
the standard `--url` / `--api-key` flags or their env equivalents
(`SHEPARD_URL`, `SHEPARD_API_KEY`).

### Via REST (raw curl)

```bash
# Enable + set URL in one shot
curl -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": true, "hubUrl": "https://hub.example.org"}' \
  https://shepard-api.example.org/v2/admin/jupyter/config
```

### Via deploy-time properties (seed-only)

```properties
shepard.jupyter.enabled=false
shepard.jupyter.hub-url=https://hub.example.org
```

These deploy-time values seed the `:JupyterConfig` singleton on first start.
Once the singleton exists, runtime PATCHes win — the deploy-time keys only
re-apply if an operator clears the runtime value via `PATCH {hubUrl: null}`.

## Validating it

- `shepard-admin jupyter status` should print `affordanceVisible = true`.
- Open a DataObject that has a `.ipynb` FileReference attached. The unified
  Data References row should show a yellow **Open in JupyterHub** button.
- Click it. Your hub should receive `?file=<the FR1b content URL>`.

## Disabling the feature

`shepard-admin jupyter disable` suppresses the action button instance-wide.
The configured `hubUrl` is preserved so you can re-enable later without
re-entering it.

## Migration: the per-user preference is gone

Pre-J1e installs let each user store a personal JupyterHub URL in their
profile preferences (`editor.preferredJupyter`). That key still exists in
the `MeApi` preferences bag for migration purposes but is no longer read by
the unified table. The admin-configured URL is now the single source of
truth.

## See also

- `aidocs/16-dispatcher-backlog.md` J1e row — design intent + acceptance
  criteria.
- `aidocs/34-upstream-upgrade-path.md` — upstream upgrade tracker entry.
- `aidocs/integrations/81-jupyterhub-integration.md` — full J-series design.
