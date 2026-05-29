---
stage: feature-defined
last-stage-change: 2026-05-29
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
   - **Valid redirect URIs:** `https://jupyterhub.<your-domain>/hub/oauth_callback`
     (the public URL JupyterHub will be reachable at).
   - **Web origins:** the same public URL without the path.
   - Capture the **client secret** — you will paste it into the env file
     as `JUPYTERHUB_KEYCLOAK_CLIENT_SECRET`.
3. **Reverse proxy slot available.** The compose stack here uses Caddy
   for production deployments and Zoraxy for the `nuclide.systems` dev
   box; pick a hostname (`jupyterhub.<your-domain>`) and have it ready
   to map to the `jupyterhub` service on port `8000`. (See §4 below.)
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
| `JUPYTERHUB_PUBLIC_URL` | yes | `https://jupyterhub.nuclide.systems` | Public-facing URL the user is redirected back to after sign-in. Must match Keycloak's valid-redirect-URI. |
| `JUPYTERHUB_SINGLEUSER_IMAGE` | no | `jupyter/scipy-notebook:python-3.11` | The image spawned per user. Override for domain-specific notebook environments. |
| `SHEPARD_BACKEND_URL` | no | `http://backend:8080` | Used by the kernel to call back into Shepard. The `backend` hostname is the compose service name. |

The Shepard backend itself does **not** need any new env vars — the
hub URL is stored at runtime in `:JupyterConfig.hubUrl` and configured
via the v2 admin endpoint (see §6).

## 4. Reverse-proxy route

The sidecar listens on port `8000` inside the `shepard` network. To
expose it on the public hostname `jupyterhub.<your-domain>`:

**Caddy** (production deployments — `infrastructure/proxy/Caddyfile`):

```caddyfile
jupyterhub.example.org {
    reverse_proxy jupyterhub:8000
}
```

After editing the Caddyfile, reload: `docker compose exec caddy caddy reload`.

**Zoraxy** (the `nuclide.systems` dev box): the Zoraxy admin UI does
not auto-discover plugin services. Add a virtual-host route by hand:

1. Open Zoraxy admin → HTTP Proxy → New Proxy Rule.
2. Domain: `jupyterhub.nuclide.systems`.
3. Backend: `http://jupyterhub:8000` (Zoraxy is on the `shepard` network).
4. Enable WebSocket forwarding (JupyterHub needs it for kernel comms).

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
curl -I https://jupyterhub.<your-domain>/hub/login
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
  -d '{"enabled": true, "hubUrl": "https://jupyterhub.<your-domain>"}'
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
  the compose network; from outside, `GET https://jupyterhub.<your-domain>/hub/health`.

## 9. Kubernetes notes

For deployments running on Kubernetes rather than docker compose:
1. Replace `DockerSpawner` with `KubeSpawner` in `jupyterhub_config.py`.
2. Drop the `/var/run/docker.sock` bind mount.
3. Provide a service account with `pods.create` in the user namespace.
4. Replace the named volumes with `PersistentVolumeClaim` bindings.

The OIDC + env-var contract is identical; only the spawner block changes.
