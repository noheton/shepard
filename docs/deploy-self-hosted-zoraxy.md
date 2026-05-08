---
layout: default
title: Self-hosted shepard behind Zoraxy
description: Operator guide for running shepard's Docker stack on a self-hosted machine, fronted by Zoraxy as the reverse proxy with automatic Let's Encrypt and optional Cloudflare Tunnel.
---

# Self-hosted shepard behind Zoraxy

This guide covers running shepard's full stack on **a Docker host you
own** — a mini PC, NUC, lab server, or any always-on machine with
≥ 8 GB RAM — and fronting it with [**Zoraxy**](https://github.com/tobychui/zoraxy)
instead of the bundled Caddy. Zoraxy gives you a web UI for managing
proxy rules, automatic Let's Encrypt, geo-IP filtering, TOTP-protected
admin access, and a statistics dashboard — all from a single Go
binary or one Docker container.

> Companion to [Deploy options](deploy/) — see that page for the
> comparison matrix and other hosting paths.

## 1. What you need

- **A Docker host.** Linux, ≥ 8 GB RAM, ≥ 30 GB free disk. Tested on
  Ubuntu 22.04 / 24.04 LTS. Mini-PC class hardware (Beelink, Minisforum,
  Topton) works fine; older laptops with the lid-close-suspend disabled
  are surprisingly viable.
- **A domain name.** Any registrar. You'll point a wildcard (or two
  A records) at the Docker host's reachable IP.
- **Reachability for ports 80 and 443**, *or* a Cloudflare account
  for Tunnel (covered in §6).
- **Docker + Docker Compose plugin.** `curl -fsSL https://get.docker.com | sh`.

## 2. Architecture

```
                           ┌─────────────────────────────┐
   Internet ──── 80/443 ───▶│ Zoraxy (zoraxydocker:latest)│
                            │  - HTTP/HTTPS listener      │
                            │  - ACME / Let's Encrypt     │
                            │  - Web UI on :8000 (LAN)    │
                            └────────────┬────────────────┘
                                         │ docker network: edge
                                         ▼
                  ┌──────────────────────────────────────────┐
                  │ shepard compose stack                    │
                  │  frontend (nuxt)  ─┐                     │
                  │  backend (quarkus) ┤                     │
                  │  neo4j ─┐          ├─ docker network:    │
                  │  mongo  ┤           shepard (internal)   │
                  │  timescaledb ┤                           │
                  │  postgis (opt) ┘                         │
                  └──────────────────────────────────────────┘
```

Zoraxy and the shepard stack run on the **same host** (typical) or on
**separate hosts** (Zoraxy on edge, shepard on a backend host). Same
host is simpler; the two-host split lets you put the data tier on
beefier hardware.

## 3. Drop the bundled Caddy

shepard's `infrastructure/docker-compose.yml` ships a Caddy proxy on
ports 80/443. Zoraxy will take those ports, so disable Caddy.

```bash
git clone https://github.com/noheton/shepard.git /opt/shepard
cd /opt/shepard/infrastructure
cp .env.example .env
# Rotate every credential in .env — see aidocs/07 H8.
$EDITOR .env
```

Create `docker-compose.override.yml` next to the upstream compose file
so you don't have to edit it directly:

```yaml
# /opt/shepard/infrastructure/docker-compose.override.yml
services:
  caddy:
    profiles:
      - disabled

  backend:
    networks:
      - shepard
      - edge      # joinable from Zoraxy

  frontend:
    networks:
      - frontend
      - edge

networks:
  edge:
    external: true
    name: edge
```

The `profiles: [disabled]` trick excludes Caddy from the default
`docker compose up` (compose only starts services with no profile, or
with a profile passed via `--profile`).

## 4. Bring up the `edge` network and shepard

```bash
docker network create edge

cd /opt/shepard/infrastructure
docker compose up -d

docker compose ps
# backend, frontend, neo4j, mongodb, timescaledb listed Up.
# caddy NOT in the list (excluded by profile).
```

The shepard frontend is reachable at `frontend:80` from any container
on the `edge` network; the backend at `backend:8080`.

## 5. Run Zoraxy

`/opt/zoraxy/docker-compose.yml`:

```yaml
services:
  zoraxy:
    image: zoraxydocker/zoraxy:latest
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
      - "8000:8000"           # management UI; firewall this off WAN
    volumes:
      - ./config:/opt/zoraxy/config
      - ./plugin:/opt/zoraxy/plugin
      - /var/run/docker.sock:/var/run/docker.sock:ro
    environment:
      TZ: Europe/Berlin
      FASTGEOIP: "true"
    networks:
      - edge

networks:
  edge:
    external: true
```

```bash
sudo mkdir -p /opt/zoraxy/{config,plugin}
cd /opt/zoraxy
docker compose up -d
```

First-run setup of the Zoraxy admin account happens via the UI at
`http://<host>:8000`. Do this from the LAN, set a strong password, and
**enable TOTP** before exposing the host to the internet. Then either
stop publishing port 8000 (`docker compose down && remove the line`) or
restrict it with the host firewall (`ufw allow from <your-lan-cidr> to
any port 8000`).

## 5a. Connecting to an existing Zoraxy host (dev workflow)

If a Zoraxy instance is already running on a host you have admin
access to — the typical dev case where you want to point a new
shepard build at "your" Zoraxy and iterate — skip §3-§5 above and
follow this section instead. **No changes to the existing Zoraxy
configuration or its other proxied apps.**

### 5a.1 What you need on the existing Zoraxy host

- Admin login to the Zoraxy management UI (the box that runs Zoraxy
  itself).
- Either:
  - **(i) Same-host deploy** — shepard runs in the same Docker daemon
    as Zoraxy. Easiest; no network plumbing.
  - **(ii) Different-host deploy** — shepard runs on a different
    machine on the same network. Needs the Zoraxy host to be able
    to reach `<shepard-host>:8080` and `<shepard-host>:80` (or
    whichever ports your shepard frontend / backend land on).

### 5a.2 Pick a subdomain for the dev instance

Don't reuse your main shepard subdomain — keep dev and any existing
prod cleanly separate. Convention:

```
shepard-dev.<your-zoraxy-domain>            -> shepard frontend
api-shepard-dev.<your-zoraxy-domain>        -> shepard backend
```

Add A records (or CNAMEs to the Zoraxy host's main domain) for both.
DNS-01 challenges via Zoraxy's ACME flow handle the cert provision.

### 5a.3 Same-host deploy (path i, recommended for dev)

On the Zoraxy host:

```bash
# Confirm the existing 'edge' network Zoraxy uses (or whatever name
# it was set up with). Shepard will need to join the same network so
# Zoraxy can resolve service names.
docker network ls | grep -E '(edge|zoraxy)'

# If your existing Zoraxy is on a network called 'zoraxy' rather than
# 'edge', adjust the override below accordingly.
EXISTING_ZORAXY_NETWORK=edge   # change me if needed

sudo mkdir -p /opt/shepard-dev && sudo chown -R "$USER" /opt/shepard-dev
cd /opt/shepard-dev
git clone https://github.com/noheton/shepard.git .
cd infrastructure
cp .env.example .env
$EDITOR .env   # rotate every shipped credential per aidocs/07 H8
```

Create a `docker-compose.override.yml` that disables the bundled
Caddy and joins the existing Zoraxy network:

```yaml
# /opt/shepard-dev/infrastructure/docker-compose.override.yml
services:
  caddy:
    profiles:
      - disabled              # existing Zoraxy serves TLS

  backend:
    container_name: shepard-dev-backend
    networks:
      - shepard
      - edge                  # ${EXISTING_ZORAXY_NETWORK}

  frontend:
    container_name: shepard-dev-frontend
    networks:
      - frontend
      - edge

networks:
  edge:
    external: true
    name: edge                # match EXISTING_ZORAXY_NETWORK
```

```bash
docker compose up -d
docker compose ps
# shepard-dev-backend, shepard-dev-frontend, neo4j, mongodb,
# timescaledb listed Up. The existing Zoraxy container is unchanged.
```

### 5a.4 Different-host deploy (path ii)

Run shepard on its own host as you normally would
(`docker compose -f infrastructure/docker-compose.yml up -d`),
including the bundled Caddy if you want a TLS terminator on the
shepard side too — **but Zoraxy will be the public-facing TLS
terminator**, so you can also disable Caddy on the shepard host
(same override as 5a.3) and let Zoraxy talk plain HTTP over the
private network.

Confirm reachability from the Zoraxy host:

```bash
# From the Zoraxy host, hit the dev shepard's backend health probe:
curl http://<shepard-dev-host>:8080/shepard/api/healthz
```

If that works, you're set for §5a.5.

### 5a.5 Add proxy rules in the existing Zoraxy UI

In the Zoraxy admin (`https://<zoraxy-host>:8000` or wherever it
lives):

1. **HTTP Proxy → New Proxy Rule**
   - Matching keyword: `shepard-dev.<your-domain>`
   - Backend: `shepard-dev-frontend:80` (path i, container DNS) or
     `<shepard-dev-host>:80` (path ii)
   - Enable HTTPS, "Use TLS for backend": off
   - Save.
2. Repeat for `api-shepard-dev.<your-domain>` → backend service.
3. **TLS / SSL → ACME** — issue or renew certs for both hostnames.
   Reuses the existing ACME provider config; zero new setup.
4. Hit `https://shepard-dev.<your-domain>` from a browser.

The other apps Zoraxy proxies are untouched.

### 5a.6 Iterate (the dev-loop part)

```bash
cd /opt/shepard-dev/infrastructure

# Pull a new shepard build:
git pull origin main
docker compose build backend frontend       # or pull, depending on your image source
docker compose up -d backend frontend       # recreate just these two
docker compose logs -f backend              # tail until the migrations log "OK"

# Roll back fast:
git checkout <previous-sha>
docker compose up -d --force-recreate backend frontend
```

`docker compose up -d --force-recreate` is the "redeploy this
container" verb. The data tier (Neo4j / Mongo / Timescale) keeps
running across backend/frontend recreates so you don't lose state
between iterations.

For **hot-reload** of the frontend during active dev: run the Nuxt
dev server outside docker on your laptop (`npm run dev` against the
shepard-frontend repo) pointed at the dev-deployed backend
(`VUE_APP_BACKEND=https://api-shepard-dev.<your-domain>/shepard/api`).
Backend code changes still need a rebuild + recreate; the frontend
gets HMR.

### 5a.7 Smoke-test checklist

After each redeploy, the same tests pass before you call it good:

- [ ] `https://shepard-dev.<your-domain>` loads the Nuxt app
- [ ] `https://api-shepard-dev.<your-domain>/shepard/api/healthz`
      returns 200 with all DBs `UP`
- [ ] `https://api-shepard-dev.<your-domain>/shepard/doc/openapi.json`
      serves the spec
- [ ] OIDC login round-trips against the configured `OIDC_AUTHORITY`
- [ ] A new Collection POST + GET round-trips through the API
- [ ] (when applicable) a new `/v2/...` endpoint you're testing
      returns the expected shape

Wire those into a small `make smoke-test` target; saves time on
every iteration.

### 5a.8 Clean teardown

When you're done with the dev instance:

```bash
cd /opt/shepard-dev/infrastructure
docker compose down -v       # stops + drops the data volumes
```

Then in Zoraxy: HTTP Proxy → delete the two proxy rules; TLS / SSL
→ revoke or just let the certs expire. The DNS records can stay
(or be removed via your registrar).

### 5a.9 Common gotchas (existing-Zoraxy variant)

- **Network name mismatch.** If the existing Zoraxy uses a different
  Docker network name (e.g. `zoraxy`, `proxy`, `traefik-net`), the
  shepard override's `name:` must match — otherwise Zoraxy can't
  resolve container DNS and you get 502s.
- **Container-name collision.** `container_name: shepard-dev-backend`
  in the override avoids collision with any other shepard-prod
  container on the same daemon.
- **Port collision on path (ii).** If shepard's bundled Caddy is
  still up on the dev-host, ports 80/443 are taken — disable it via
  the override (same trick as path i).
- **Subdomain TLS rate-limit.** Let's Encrypt limits 5 cert issuances
  per registered domain per hour; if you're spinning up many dev
  instances, use the **staging directory** while iterating, then
  flip to production for the keeper.

## 6. Public reachability

Two paths depending on whether your host has a public IP:

### 6a. Direct (you have a public IPv4 / IPv6 + DNS control)

Open ports **80** and **443** on your router / firewall and forward
both to the Docker host. Add A records:

```
shepard.example.com.       A  <your-public-ip>
api.shepard.example.com.   A  <your-public-ip>
```

Skip to §7.

### 6b. Cloudflare Tunnel (CG-NAT, no static IP, or you don't want
to open ports)

Add a `cloudflared` sidecar to the Zoraxy compose file:

```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      TUNNEL_TOKEN: ${CF_TUNNEL_TOKEN}
    networks:
      - edge
```

Provision the tunnel and grab `CF_TUNNEL_TOKEN` from the Cloudflare
Zero Trust dashboard (Networks → Tunnels → Create). Configure the
tunnel's Public Hostname routes to point at **`http://zoraxy:80`** for
each subdomain you need; Cloudflare handles the public TLS, Zoraxy
handles the routing inside.

This is the recommended path for residential / lab installations —
no port forwards, no DDNS, and Cloudflare's edge takes the brunt of
unsolicited traffic.

## 7. Configure Zoraxy proxy rules

In the Zoraxy UI:

1. **HTTP Proxy → New Proxy Rule**
   - Matching keyword: `shepard.example.com`
   - Backend: `frontend:80` (Zoraxy is on the same docker network, so
     container DNS resolves)
   - Enable HTTPS, "Use TLS for backend": off
   - Save.

2. Repeat for `api.shepard.example.com` → `backend:8080`.

3. **TLS / SSL → ACME** (left sidebar)
   - Provider: Let's Encrypt
   - Email: yours
   - Challenge: HTTP-01 (works for direct deploys; Cloudflare Tunnel
     deployments should use **DNS-01** with Cloudflare API token
     instead)
   - Generate certificates for both hostnames.
   - Renewal is automatic; the `AUTORENEW` env var defaults to
     86400 s (daily check) and `EARLYRENEW` to 30 days.

4. **Apply** — Zoraxy reloads listeners. Hit
   `https://shepard.example.com` from any browser; the Nuxt frontend
   loads with a green-padlock cert.

## 8. Update the backend's published URL

The frontend container reads the backend URL from the env it was
started with (`VUE_APP_BACKEND` for the legacy frontend, the
new-frontend equivalent in `application.properties`). Update `.env`:

```
BACKEND_URL=https://api.shepard.example.com/
```

then `docker compose up -d backend frontend` to recreate with the new
env. CORS in the backend (`quarkus.http.cors.origins=*`) is permissive
out of the box; **tighten it** to the frontend hostname before going
public — this is `aidocs/07` C2.

## 9. Backups

Same recipe as the Oracle guide, minus the OCI Object Storage step:

```bash
cat > /usr/local/bin/shepard-backup.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd /opt/shepard/infrastructure
docker compose stop neo4j mongodb timescaledb postgis
tar czf /backup/shepard-$(date -u +%FT%H%MZ).tgz \
    /var/lib/docker/volumes/shepard_*_data \
    /opt/shepard/backend/{logs,config}
docker compose start neo4j mongodb timescaledb postgis
# Optional: rsync to a Hetzner Storage Box, BorgBase, or rclone target.
find /backup -name 'shepard-*.tgz' -mtime +14 -delete
EOF
chmod +x /usr/local/bin/shepard-backup.sh
echo '30 2 * * * root /usr/local/bin/shepard-backup.sh' | sudo tee /etc/cron.d/shepard-backup
```

If your edge box has limited disk, point `/backup` at a USB drive or
NAS mount. **Test restore at least once** — an untested backup is a
hope, not a backup.

## 10. Hardening checklist (Zoraxy specifics)

- [ ] **TOTP on the Zoraxy admin account.** UI: System Settings →
      Auth → enable 2FA.
- [ ] **Firewall :8000** to LAN only (or unpublish entirely; access
      the UI via `ssh -L 8000:localhost:8000 host` instead).
- [ ] **Geo-IP block lists** for the proxy rules — Zoraxy's GeoIP
      filter blocks at the proxy layer, ahead of the application.
- [ ] **Access lists** for sensitive paths (e.g.
      `/shepard/api/admin/*` if/when admin endpoints exist —
      `aidocs/22-admin-cli-draft.md` for the planned shape).
- [ ] **Statistics retention** — Zoraxy logs every request by default;
      tune retention so the volume doesn't blow up disk.
- [ ] **shepard `.env`** rotated, not the shipped placeholders
      (aidocs/07 H8). Worth saying twice.
- [ ] **Docker socket exposure.** The compose snippet mounts
      `/var/run/docker.sock:ro` so Zoraxy can resolve container names.
      `:ro` is mandatory; without it a Zoraxy compromise becomes a
      host compromise.

## 11. Troubleshooting

- **"Bad Gateway" from Zoraxy** — the backend / frontend containers
  aren't on the `edge` network. `docker network inspect edge` should
  list them. Recreate with `docker compose up -d --force-recreate`
  after fixing the override file.
- **Let's Encrypt rate limit** — five failed attempts in an hour
  trips the rate limit for that hostname. Use Let's Encrypt's
  **staging** ACME directory while you debug:
  `https://acme-staging-v02.api.letsencrypt.org/directory`.
- **Port 80 on host is taken** — `sudo ss -ltnp | grep ':80 '` will
  show the offender. Common culprits: another reverse proxy (nginx,
  Caddy outside compose), or the host's mDNS responder.
- **HTTP-01 challenge fails behind Cloudflare Tunnel** — switch to
  DNS-01 with a Cloudflare API token; HTTP-01 won't work because
  Cloudflare terminates TLS on its edge.

## 12. What this isn't

- **Not a multi-tenant deployment.** Zoraxy can host multiple sites
  on one box but isn't built for ten-thousand-tenant scale; for that
  use Traefik or HAProxy.
- **Not a substitute for an HA setup.** Single-host, single-Zoraxy
  means a host outage takes everything down.
- **Not a fit for compliance-heavy data.** A self-hosted lab box
  rarely satisfies the auditing, access-logging, and physical-security
  controls real research data demands. shepard is a research-data
  platform; treat your test instance accordingly.
