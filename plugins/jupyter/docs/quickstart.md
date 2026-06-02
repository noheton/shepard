---
stage: feature-defined
last-stage-change: 2026-05-29
last-content-change: 2026-06-02
---

# shepard-plugin-jupyter — User quickstart

**Audience:** Shepard users who want to analyse data in a Jupyter
notebook without leaving the Shepard surface.

## Open in Jupyter

Any FileReference (and, soon, any DataObject with a fetchable payload)
shows an **"Open in Jupyter"** action in its detail view. Clicking it:

1. Redirects you to JupyterHub at
   `https://shepard.<your-domain>/jupyterhub/hub/spawn?file=<encoded-url>`.

   The `<encoded-url>` is the Shepard file-content URL
   (`/v2/files/{appId}/content`) **percent-encoded with
   `encodeURIComponent`**.  Every character that is special in a URL
   query string — `?`, `&`, `=`, `#` — appears as its `%XX` escape so
   that the JupyterHub server can parse the outer spawn URL without
   ambiguity.  The round-trip is guaranteed:
   `decodeURIComponent(encodedParam) === originalShepardDownloadUrl`.
2. JupyterHub signs you in via the same Keycloak realm as Shepard
   (no second login if you're already in).
3. **Before the kernel boots**, a pre-spawn hook on the JupyterHub
   side reads the `?file=` query param, validates the URL against an
   operator-configured allowlist, fetches the bytes using your
   forwarded OIDC token, and drops the file in your notebook workspace
   at:

   ```
   ~/work/shepard-imports/<filename>
   ```

4. A sidecar `README-<filename>.md` lands next to it noting the source
   URL, the fetch timestamp, and the HTTP status. Worth reading on
   first launch.

**The pre-fetched file is yours to modify**, but modifications stay
inside the notebook volume — they do **not** write back to Shepard.
If you produce derived data worth keeping, upload it via the regular
Shepard API or the `shepard-py` client (already on the kernel's path).

### Allowlist behavior — what to expect when it misses

For SSRF defense, the pre-spawn hook only fetches from hostnames the
operator listed in `JUPYTERHUB_SHEPARD_ALLOWED_HOSTS` (default
`shepard.nuclide.systems,shepard-api.nuclide.systems`).

If you click "Open in Jupyter" on a Shepard surface served from a host
**not** in the allowlist (e.g. a staging instance the operator forgot
to add), the kernel still boots — but `shepard-imports/` contains only
a `README-*.md` with `Status: allowlist-miss` and the URL you can fetch
manually instead. Ask the operator to add the host to the allowlist;
see the install guide §4.

### Other status values

The README's `Status:` field tells you what happened:

| Status | Meaning | What to do |
|---|---|---|
| `OK` | File fetched cleanly. | Use it in your notebook. |
| `401` / `403` | Shepard refused the token. | Re-authenticate via the Shepard UI, then re-spawn. |
| `allowlist-miss` | Host not allow-listed. | Ask the operator. |
| `timeout` | Server didn't respond in 5 min. | Re-spawn or fetch in a cell manually. |
| `no-token` | OIDC access token wasn't available. | Sign out + back in to Shepard, then retry. |
| `error: <Name>` | Unexpected failure. | Show the README to the operator. |

## Troubleshooting

### "My file isn't in `shepard-imports/`"

Check `~/work/shepard-imports/README-*.md` first — it explains what
happened. If you see no README at all, the auto-fetch didn't run (the
spawn URL probably didn't carry `?file=`); use the manual fallback
below.

### Manual fallback — kernel-side fetch

Until **J1e-PR-06-AUTOFETCH-01** is deployed (or for files the
allowlist rejects), use the forwarded OIDC token directly:

```python
import os, requests
token = os.environ["SHEPARD_OIDC_ACCESS_TOKEN"]
url = "https://shepard-api.nuclide.systems/v2/files/<appId>/payload"

resp = requests.get(url, headers={"Authorization": f"Bearer {token}"})
resp.raise_for_status()
with open("my-download.bin", "wb") as f:
    f.write(resp.content)
```

The token environment variable is set by JupyterHub's `auth_state_hook`
on every kernel spawn. It expires when your OIDC session does — re-spawn
to refresh.

## See also

- [Install guide](./install.md) — operator-side setup, including the
  allowlist env var.
- Backlog row
  [`J1e-PR-06-OPEN-IN-JUPYTER-AUTOFETCH`](../../../aidocs/16-dispatcher-backlog.md)
  — design + status of the auto-fetch pipeline.
