---
stage: feature-defined
last-stage-change: 2026-05-29
last-content-change: 2026-05-29
---

# shepard-plugin-jupyter — Operator install guide

**Audience:** operators standing up the JupyterHub sidecar for a Shepard
deployment.

This guide takes a Shepard instance that does **not** have JupyterHub
yet and gets one running on the same host, sharing the existing
Keycloak realm and reachable via the deployment's reverse proxy.

Cross-references:
- Design doc: [`aidocs/integrations/81-jupyterhub-integration.md`](../../../aidocs/integrations/81-jupyterhub-integration.md)
- Reference: [`plugins/jupyter/docs/reference.md`](./reference.md) (planned)
- Quickstart: [`plugins/jupyter/docs/quickstart.md`](./quickstart.md) (planned)
- Admin runbook: `GET/PATCH /v2/admin/plugins/jupyter/config` (post J1e rename;
  see backend `JupyterConfigRest`)

---

## 1. Prerequisites

Before bringing up the JupyterHub sidecar:

1. **Keycloak realm exists.** Shepard's backend is already authenticating
   against a realm — the JupyterHub sidecar uses the **same realm**.
2. **JupyterHub OIDC client provisioned.** In the Keycloak admin console,
   under the Shepard realm:
   - Create a new client with ID `jupyterhub` (or the name you intend to
     use as `JUPYTERHUB_KEYCLOAK_CLIENT_ID`).
   - Client type: **Confidential** (`Standard flow` enabled, `Direct access
     grants` optional, `Service accounts` not required).
   - **Valid redirect URIs:** `https://shepard.<your-domain>/jupyterhub/hub/oauth_callback`
     (the path-mounted URL JupyterHub is reachable at — see §4).
   - **Web origins:** `https://shepard.<your-domain>` (the Shepard
     host root; path-mount shares the cookie domain).
   - Capture the **client secret** — you will paste it into the env file
     as `JUPYTERHUB_KEYCLOAK_CLIENT_SECRET`.
3. **Reverse proxy slot available.** The sidecar mounts as a path
   (`/jupyterhub`) on the existing Shepard host. On `nuclide.systems`
   the edge proxy is **Zoraxy**; Caddy is the documented preferred
   future shape. Either way: no new DNS / TLS / host block needed —
   see §4 for the one-rule path-mount.
4. **Docker socket access acceptable.** JupyterHub's default
   `DockerSpawner` mounts `/var/run/docker.sock` to spawn per-user
   notebook containers. If your security posture forbids this, switch
   to `KubeSpawner` — see §"Kubernetes notes" at the end.
5. **Env vars set.** The Shepard `.env` file (`infrastructure/.env`,
   templated from `infrastructure/.env.example`) must carry the four
   `JUPYTERHUB_*` keys plus the existing `OIDC_AUTHORITY`. See §3.

## 2. Compose-profile activation

The sidecar is gated behind the `jupyter` profile so a Shepard deployment
that doesn't want JupyterHub never starts the container.

To bring it up:

```bash
cd /opt/shepard
COMPOSE_PROFILES=jupyter \
  docker compose \
    -f infrastructure/docker-compose.yml \
    -f plugins/jupyter/compose-profile.yml \
    up -d jupyterhub
```

The compose-profile fragment lives in `plugins/jupyter/compose-profile.yml`;
the JupyterHub Python config lives in `plugins/jupyter/config/jupyterhub_config.py`
and is mounted read-only into the container at startup.

For permanent activation, append `jupyter` to `COMPOSE_PROFILES` in
`infrastructure/.env`:

```ini
COMPOSE_PROFILES=jupyter
```

Then `make redeploy-stack` (or the local equivalent) brings JupyterHub
up alongside backend / frontend / databases on every restart.

## 3. Env-var checklist

The plugin reads four new env vars from the shared `infrastructure/.env`
(the canonical template is `infrastructure/.env.example` — keys are
already added):

| Env var | Required? | Default | Notes |
|---|---|---|---|
| `OIDC_AUTHORITY` | already present | — | Reused from Shepard backend; this is the Keycloak realm URL. |
| `JUPYTERHUB_KEYCLOAK_CLIENT_ID` | yes | `jupyterhub` | Matches the Keycloak client ID from §1. |
| `JUPYTERHUB_KEYCLOAK_CLIENT_SECRET` | yes | — | The confidential client secret. **Do not commit.** |
| `JUPYTERHUB_PUBLIC_URL` | yes | `https://shepard.nuclide.systems/jupyterhub` | Path-mounted public URL the user is redirected back to after sign-in. Must match Keycloak's valid-redirect-URI. |
| `JUPYTERHUB_SINGLEUSER_IMAGE` | no | `jupyter/scipy-notebook:python-3.11` | The image spawned per user. Override for domain-specific notebook environments. |
| `SHEPARD_BACKEND_URL` | no | `http://backend:8080` | Used by the kernel to call back into Shepard. The `backend` hostname is the compose service name. |
| `JUPYTERHUB_SHEPARD_ALLOWED_HOSTS` | no | `shepard.nuclide.systems,shepard-api.nuclide.systems` | Comma-separated allowlist of hostnames the **`?file=<url>` auto-fetch pre-spawn hook** (J1e-PR-06) will fetch from. SSRF defense — see §4.5. Add your deployment's actual hostnames here; the default only covers `nuclide.systems`. |

The Shepard backend itself does **not** need any new env vars — the
hub URL is stored at runtime in `:JupyterConfig.hubUrl` and configured
via the v2 admin endpoint (see §6).

## 4. Reverse-proxy route (path-mount)

The sidecar listens on port `8000` inside the `shepard` network and is
**path-mounted at `/jupyterhub` on the existing Shepard host** — i.e.
the user reaches it at `https://shepard.<your-domain>/jupyterhub`,
NOT at `https://jupyterhub.<your-domain>`.

**Why path-mount?** Per the [CLAUDE.md "Always: mount plugin UI
sidecars as paths, not subdomains"](../../../CLAUDE.md#always-mount-plugin-ui-sidecars-as-paths-not-subdomains)
rule:

- No new DNS A record, no new TLS cert, no new reverse-proxy host
  block.
- The Keycloak session cookie is already valid on
  `shepard.<your-domain>` — path-mount reuses it. A subdomain forces a
  cross-domain redirect dance and CORS exemptions.
- Adding more plugin UIs later is one extra `handle_path` block, not a
  new infrastructure ticket.

`jupyterhub_config.py` already sets `c.JupyterHub.base_url = "/jupyterhub"`
(JupyterHub's official path-mount knob — single-user notebook URLs
become `/jupyterhub/user/<name>/...` automatically), so the proxy only
needs to forward the prefix unchanged.

### 4.1 Zoraxy (current `nuclide.systems` infrastructure)

The live `nuclide.systems` dev box runs **Zoraxy** as the edge reverse
proxy. Zoraxy is admin-UI-managed; add a path-mount rule by hand:

1. Open Zoraxy admin → **HTTP Proxy** → existing rule for
   `shepard.nuclide.systems` → **Virtual Directory** (or "Sub-path
   Forwarding").
2. **Path:** `/jupyterhub/*`
3. **Backend:** `http://jupyterhub:8000` (Zoraxy is on the `shepard`
   docker network and resolves the service name).
4. **Enable WebSocket forwarding** — JupyterHub needs it for kernel
   comms; without this, notebooks load but cells never execute.
5. Save and reload — no Zoraxy restart needed.

After the rule is live, browse to `https://shepard.<your-domain>/jupyterhub`
— you should land on the JH login page, which then redirects to Keycloak.

### 4.2 Caddy (preferred future shape)

If the deployment moves off Zoraxy to Caddy (tracked in
`aidocs/16-dispatcher-backlog.md` under
`J1e-PR-05-CADDY-PATH-MOUNT-02` / `INFRA-REVERSE-PROXY-CADDY-MIGRATION`),
the path-mount block is a single `handle_path` in the existing
`shepard.<your-domain>` site block:

```caddyfile
shepard.nuclide.systems {
  handle_path /jupyterhub/* {
    reverse_proxy jupyterhub:8000
  }
  handle {
    reverse_proxy frontend:3000
  }
}
```

Caddy's `reverse_proxy` directive **auto-upgrades** `Connection:
Upgrade` headers for WebSockets — no extra config block is needed for
kernel comms. After editing the Caddyfile, reload:
`docker compose exec caddy caddy reload`.

(This Caddyfile is documented here as the preferred-future shape; the
sidecar `compose-profile.yml` does NOT ship a Caddy container today —
the broader Zoraxy → Caddy migration is a separate decision.)

### 4.3 Regenerate the Keycloak client secret

The realm template (`infrastructure/keycloak/shepard-demo-realm.json`)
ships a `jupyterhub` client with the placeholder secret
`REPLACE_ME_AT_OPERATOR_BOOTSTRAP`. **Before bringing the sidecar up,
generate a real secret:**

1. Log into the Keycloak admin console → realm → **Clients** →
   `jupyterhub`.
2. **Credentials** tab → **Regenerate Secret**.
3. Copy the new value and paste into `infrastructure/.env` as:
   ```ini
   JUPYTERHUB_KEYCLOAK_CLIENT_SECRET=<paste-here>
   ```
4. (Optional, recommended.) In the same Keycloak client → **Settings**,
   trim the `redirectUris` to only the host you actually serve (e.g.
   drop the `shepard.example.org` template default once
   `shepard.nuclide.systems` is the live host).

### 4.4 `?file=<url>` auto-fetch allowlist (J1e-PR-06)

JupyterHub's pre-spawn hook reads a `?file=<encoded-url>` query param
from the spawn URL, fetches that URL using the user's forwarded OIDC
token, and writes the result into the per-user notebook volume at
`/home/jovyan/work/shepard-imports/`.

**The allowlist is the SSRF perimeter.** The hook validates the URL's
hostname against `JUPYTERHUB_SHEPARD_ALLOWED_HOSTS` (comma-separated)
BEFORE any outbound call. Hosts not in the list are rejected with a
README sidecar noting `Status: allowlist-miss` — the kernel still
spawns, the user sees what happened, no outbound request is made.

For the live `nuclide.systems` deployment, the default
(`shepard.nuclide.systems,shepard-api.nuclide.systems`) is correct.
For other deployments, add your real hostname(s) — both the user-facing
host (where "Open in Jupyter" URLs are constructed) and the API host
(where the fetch resolves) — to `infrastructure/.env`:

```ini
JUPYTERHUB_SHEPARD_ALLOWED_HOSTS=shepard.example.org,shepard-api.example.org
```

This is a **deploy-time** knob (defines the security perimeter, not a
runtime toggle); change requires a `docker compose up -d jupyterhub`
to take effect. The runtime `:JupyterConfig` admin endpoint does not
expose it — by design.

User-facing behavior (status codes, manual fallback) is documented in
[`plugins/jupyter/docs/quickstart.md §Open in Jupyter`](./quickstart.md#open-in-jupyter).

### 4.5 Caveat — shared cookie domain

Path-mounting shares the cookie domain with Shepard. JupyterHub's
session cookie defaults to `Path=/jupyterhub` so it doesn't collide
with Shepard's own cookies; verify this in a browser dev-tools cookie
panel on first deploy and check `aidocs/16` row
`J1e-PR-05-CADDY-PATH-MOUNT` for the design note if anything looks
off.

## 5. Verify the install

```bash
# 1. Container up + healthy
docker compose -f infrastructure/docker-compose.yml \
               -f plugins/jupyter/compose-profile.yml \
               ps jupyterhub
# STATUS should read "Up (healthy)" after ~60s start_period.

# 2. Healthcheck endpoint reachable
docker exec shepard-jupyterhub curl -fs http://localhost:8000/hub/health
# Expected: {"version": "5.x.x", "ok": true}

# 3. OIDC redirect probe
curl -I https://shepard.<your-domain>/jupyterhub/hub/login
# Expected: 302 Found, Location: <keycloak-realm-url>/protocol/openid-connect/auth?...
```

## 6. Wire the hub URL into Shepard

Once the sidecar is reachable, tell the Shepard backend where it lives
so the "Analyse in Jupyter" launch button (J1 series) appears on the
unified data-references table:

```bash
# Set the hub URL at runtime — no backend restart needed.
curl -X PATCH https://<shepard-host>/shepard/api/v2/admin/plugins/jupyter/config \
  -H "Authorization: Bearer <instance-admin-token>" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": true, "hubUrl": "https://shepard.<your-domain>/jupyterhub"}'
```

(Path post-J1e rename. The pre-rename path was
`/v2/admin/jupyter/config`; both resolve to the same backing entity
during the deprecation window.)

The setting persists in Neo4j as `:JupyterConfig {enabled, hubUrl}` and
survives backend restarts. See the [admin config principle in CLAUDE.md](../../../CLAUDE.md#always-surface-operator-knobs-in-the-admin-config).

## 7. Known pitfalls

1. **First-launch volume init is slow.** The `jupyter/scipy-notebook`
   single-user image is ~3 GB. The **first** spawn for any user pulls
   it, which can take 1–3 minutes on a fresh host. Pre-pull on the host
   to avoid first-user surprise:
   `docker pull jupyter/scipy-notebook:python-3.11`.

2. **Single-user image size matters at scale.** Each user gets one
   container; budget disk accordingly. Operators with strict storage
   budgets should swap to `jupyter/minimal-notebook` (~500 MB) via
   `JUPYTERHUB_SINGLEUSER_IMAGE`.

3. **JWT issuer URL mismatch breaks token forwarding.** The token
   passed from JupyterHub to the user kernel (and then forwarded to
   the Shepard backend on every API call) carries the issuer URL
   from `OIDC_AUTHORITY`. If the backend's `OIDC_AUTHORITY` and the
   hub's `SHEPARD_OIDC_ISSUER_URL` disagree by even a trailing slash,
   the backend rejects the token with 401. **Set both env vars from
   the same source** — the shared `.env` file makes this automatic.

4. **Cookie-secret regeneration on volume reset.** If the
   `jupyterhub_config` named volume is deleted (e.g.
   `docker compose down -v`), the cookie secret regenerates and all
   active sessions are invalidated. Users will see a fresh login
   prompt — not an error, but worth communicating before a restart.

5. **DockerSpawner needs the docker socket.** If the bind mount of
   `/var/run/docker.sock` is removed for security, JupyterHub cannot
   spawn user containers and login will succeed but spawn will hang.
   Switch to `KubeSpawner` in `jupyterhub_config.py` for a hardened
   deployment.

## 8. Operator-runbook pointers

- **Runtime config:** `PATCH /v2/admin/plugins/jupyter/config` —
  toggle the launch button, change the hub URL, without a restart.
- **CLI parity:** `shepard-admin jupyter status|enable|disable|set-hub-url <url>`
  (per the L1 baseline).
- **Audit trail:** every PATCH to the admin config lands in `:Activity`
  via `ProvenanceCaptureFilter` — query the activity feed filtered by
  `resourcePath = /v2/admin/plugins/jupyter/config` to see who changed
  the hub URL when.
- **Healthcheck:** `GET http://jupyterhub:8000/hub/health` from inside
  the compose network; from outside, `GET https://shepard.<your-domain>/jupyterhub/hub/health`.

## 9. Kubernetes notes

For deployments running on Kubernetes rather than docker compose:
1. Replace `DockerSpawner` with `KubeSpawner` in `jupyterhub_config.py`.
2. Drop the `/var/run/docker.sock` bind mount.
3. Provide a service account with `pods.create` in the user namespace.
4. Replace the named volumes with `PersistentVolumeClaim` bindings.

The OIDC + env-var contract is identical; only the spawner block changes.
