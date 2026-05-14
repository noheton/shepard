---
layout: default
title: TLS + reverse proxy (deployment reference)
permalink: /reference/deployment-tls/
description: TLS termination for shepard — Caddy (bundled, zero-touch Let's Encrypt), Nginx, Traefik, cert-manager. Wildcard certs, internal-cluster TLS, HTTP/3.
---

# TLS + reverse proxy

The reference deploy puts **Caddy** in front of every shepard
service for TLS termination. The bundled Caddyfile at
`infrastructure/proxy/Caddyfile` handles Let's Encrypt
automatically for the configured hostname.

If you operate Nginx, Traefik, or cert-manager already, swap in
your tooling — the contract is "terminate TLS, reverse-proxy to
the backend on `:8080`, the frontend on `:3000`, optional Mongo
Express / Grafana on their respective ports."

## What needs TLS

Every **external-facing** surface:

| Subdomain | Service | Container port |
|---|---|---|
| `shepard.example.com` | frontend (Nuxt 3) | `3000` |
| `backend.shepard.example.com` | backend (Quarkus) | `8080` |
| `keycloak.example.com` | OIDC provider (if you self-host) | `8080` (HTTP) inside, TLS at the proxy |
| `grafana.shepard.example.com` (optional) | Grafana | `3000` |
| `mongoexpress.shepard.example.com` (optional) | mongo-express | `8081` |

**Internal-only** services do not face the internet and don't
need a public TLS cert:

- Neo4j Bolt (`7687`)
- MongoDB (`27017`)
- TimescaleDB (`5432`) / PostGIS (`5433` mapped)
- HSDS (`5101`)
- Prometheus (`9090`)

Internal-service TLS (mTLS between backend and DBs) is **out of
scope for the first cut** — the reference deploy puts everything
on a single Docker network. The mTLS roadmap is a future item;
flag it in your security review if it matters for your
compliance posture.

## Caddy (bundled, zero-touch Let's Encrypt)

The bundled `infrastructure/proxy/Caddyfile` is structured as
one block per subdomain. The skeleton:

```caddyfile
{
    # Global options
    email admin@example.com
}

hostname_placeholder_do_not_change {
    reverse_proxy frontend:3000
    # frontend handles its own routes
}

backend.hostname_placeholder_do_not_change {
    reverse_proxy backend:8080
}
```

The `hostname_placeholder_do_not_change` token is intentional — it's
a sed-replace target.

### Setup

Per the upstream
[`infrastructure/README.md`](https://github.com/noheton/shepard/blob/main/infrastructure/README.md):

```bash
cd infrastructure

# Replace placeholder with your real hostname
sed -i "s@hostname_placeholder_do_not_change@shepard.example.com@" proxy/Caddyfile

# Replace placeholder in the landing-page index
sed -i "s@hostname_placeholder_do_not_change@shepard.example.com@" proxy/shepard/index.html

# Set the ACME email in the global options block
sed -i "s|email admin@example.com|email you@example.com|" proxy/Caddyfile
```

On first start, Caddy hits Let's Encrypt's ACME server and
provisions certificates for every configured subdomain. DNS must
resolve — Let's Encrypt validates via the HTTP-01 challenge by
default; ports `80` + `443` must be reachable from the public
internet.

### Wildcard certs (DNS-01 challenge)

If you want a wildcard cert (`*.shepard.example.com`) — useful
when you add subdomains without re-provisioning each time — use
DNS-01:

```caddyfile
{
    email you@example.com
}

*.shepard.example.com {
    tls {
        dns route53 {
            access_key_id    {env.AWS_ACCESS_KEY_ID}
            secret_access_key {env.AWS_SECRET_ACCESS_KEY}
        }
    }
    @backend host backend.shepard.example.com
    handle @backend {
        reverse_proxy backend:8080
    }
    @frontend host shepard.example.com
    handle @frontend {
        reverse_proxy frontend:3000
    }
}
```

Caddy supports many DNS providers — Route 53, Cloudflare, OVH,
Hetzner, Gandi, [more here](https://github.com/caddy-dns).
Build a custom Caddy with the DNS plugin baked in (the bundled
`caddy:2` image doesn't include DNS plugins by default).

```dockerfile
FROM caddy:2-builder AS builder
RUN xcaddy build \
    --with github.com/caddy-dns/route53
FROM caddy:2
COPY --from=builder /usr/bin/caddy /usr/bin/caddy
```

Reference this image in `docker-compose.yml` instead of `caddy:2`.

### HTTP/3 (QUIC)

The bundled Caddyfile listens on `443/tcp` + `443/udp` (the UDP
port is HTTP/3). Caddy negotiates automatically; you don't need
to do anything beyond mapping the UDP port in `docker-compose.yml`
(already done in `infrastructure/docker-compose.yml`).

## Nginx (alternative)

If you already operate Nginx, the contract is the same. Sample
`/etc/nginx/conf.d/shepard.conf`:

```nginx
upstream shepard-frontend { server frontend:3000; }
upstream shepard-backend  { server backend:8080;  }

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name shepard.example.com;

    ssl_certificate     /etc/letsencrypt/live/shepard.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/shepard.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    location / {
        proxy_pass         http://shepard-frontend;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name backend.shepard.example.com;

    ssl_certificate     /etc/letsencrypt/live/backend.shepard.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/backend.shepard.example.com/privkey.pem;

    # Allow file uploads up to 5 GiB. Tune to your largest expected
    # FileReference / FileBundleReference upload.
    client_max_body_size 5G;

    location / {
        proxy_pass         http://shepard-backend;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
    }
}

server {
    listen 80;
    server_name shepard.example.com backend.shepard.example.com;
    return 301 https://$host$request_uri;
}
```

ACME cert provisioning is **out of Nginx's scope** — pair with
**certbot** (`certbot --nginx`) or **cert-manager** (Kubernetes).

## Traefik (alternative)

For Kubernetes / Docker Swarm deploys, Traefik is the
zero-config option:

```yaml
# docker-compose.yml fragment
services:
  traefik:
    image: traefik:v3.4
    command:
      - --providers.docker=true
      - --providers.docker.exposedbydefault=false
      - --entrypoints.web.address=:80
      - --entrypoints.websecure.address=:443
      - --certificatesresolvers.le.acme.email=you@example.com
      - --certificatesresolvers.le.acme.storage=/letsencrypt/acme.json
      - --certificatesresolvers.le.acme.httpchallenge.entrypoint=web
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - letsencrypt:/letsencrypt

  backend:
    image: shepard/backend:latest
    labels:
      - traefik.enable=true
      - traefik.http.routers.backend.rule=Host(`backend.shepard.example.com`)
      - traefik.http.routers.backend.entrypoints=websecure
      - traefik.http.routers.backend.tls.certresolver=le
      - traefik.http.services.backend.loadbalancer.server.port=8080

  frontend:
    image: shepard/frontend:latest
    labels:
      - traefik.enable=true
      - traefik.http.routers.frontend.rule=Host(`shepard.example.com`)
      - traefik.http.routers.frontend.entrypoints=websecure
      - traefik.http.routers.frontend.tls.certresolver=le
      - traefik.http.services.frontend.loadbalancer.server.port=3000
```

Traefik auto-discovers services from Docker labels — no separate
config file.

## cert-manager (Kubernetes)

For Kubernetes deploys, **cert-manager + an Ingress controller**
(Nginx Ingress, Traefik Ingress, or HAProxy Ingress) is the
canonical pattern:

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata: { name: letsencrypt-prod }
spec:
  acme:
    email: you@example.com
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef: { name: letsencrypt-prod }
    solvers:
      - http01:
          ingress: { class: nginx }
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: shepard
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  ingressClassName: nginx
  tls:
    - hosts: [ shepard.example.com, backend.shepard.example.com ]
      secretName: shepard-tls
  rules:
    - host: shepard.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: { name: shepard-frontend, port: { number: 3000 } }
    - host: backend.shepard.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: { name: shepard-backend, port: { number: 8080 } }
```

A shepard Helm chart is **not** maintained in-tree; the
community variants on the public docker-compose are the
starting point for a Kubernetes port. Reach out via the
GitHub issue tracker if you're considering one.

## Internal-cluster TLS (mTLS — out of scope for first cut)

The reference deploy runs every service on a single Docker
network. Bolt, Mongo, Postgres, etc. all speak cleartext between
the backend and the DBs.

For compliance-driven deployments where **wire encryption
between services** is mandatory:

- **Neo4j** supports SSL — `dbms.connector.bolt.tls_level=REQUIRED`
  + certs at `/var/lib/neo4j/certificates/`.
- **MongoDB** supports SSL via `--tlsMode requireTLS` plus
  certificate paths.
- **PostgreSQL** supports SSL via `ssl=on` plus
  `ssl_cert_file` / `ssl_key_file`.

shepard's container image picks up DB connection settings from
env. The configs for SSL aren't baked into the reference
compose; pull the upstream
[Neo4j operations manual §Security
SSL](https://neo4j.com/docs/operations-manual/current/security/ssl-framework/)
+ Mongo / Postgres equivalents.

A future operator-runbook for mTLS-everywhere is queued under
the security-issues ledger; if you need it sooner, file an
issue.

## Common TLS gotchas

### "Backend redirects to `http://...` instead of `https://...`"

The reverse proxy isn't forwarding the original protocol. Add:

```
proxy_set_header X-Forwarded-Proto $scheme;     # Nginx
proxy_set_header X-Forwarded-Host  $host;
```

Caddy does this automatically.

### "Login redirects fail with a CORS error"

The frontend's `CLIENT_ID` is registered with redirect URIs
that don't include `https://`. Update the OIDC client config to
include both `http://` (for local dev) and `https://` (for the
real deploy).

### "Browser shows mixed-content warnings"

Some asset URL is hardcoded to `http://`. Verify:

```bash
curl -sI https://shepard.example.com/ | grep -i "Strict-Transport-Security"
```

A missing HSTS header means the proxy isn't enforcing
HTTPS-only. Add `Strict-Transport-Security: max-age=31536000;
includeSubDomains` (Caddy does this by default; Nginx via
`add_header`).

### "Let's Encrypt rate limit hit"

You've re-issued certificates too many times for the same FQDN
in a week (Let's Encrypt's
[rate limits](https://letsencrypt.org/docs/rate-limits/)). Use
the **staging** ACME server while developing:

```caddyfile
{
    acme_ca https://acme-staging-v02.api.letsencrypt.org/directory
}
```

Switch back to the production server once your DNS + Caddyfile
are stable. Staging certs are signed by Let's Encrypt's staging
intermediate — browsers won't trust them, which is intentional.

## CORS posture

`quarkus.http.cors.origins=*` in the shipped
`application.properties` is **permissive** — fine for the local
demo, **wrong** for an internet-exposed deployment.

Tighten before public exposure. The clean path is to override
the property in the backend's env:

```env
QUARKUS_HTTP_CORS_ORIGINS=https://shepard.example.com
```

Multi-origin (e.g. you also serve a Storybook on a different
subdomain): comma-separated. Tracked under `aidocs/07` C2.

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [OIDC / authentication]({{ '/reference/deployment-oidc/' | relative_url }})
- [Monitoring + observability]({{ '/reference/deployment-monitoring/' | relative_url }})
- [Troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }})
- Upstream [`infrastructure/README.md`](https://github.com/noheton/shepard/blob/main/infrastructure/README.md) — reverse-proxy setup details.
- [`aidocs/07`](https://github.com/noheton/shepard/blob/main/aidocs/07-security-issues.md) C2 — CORS tightening.
- [Caddy documentation](https://caddyserver.com/docs/)
- [Nginx documentation](https://nginx.org/en/docs/)
- [Traefik documentation](https://doc.traefik.io/traefik/)
- [cert-manager documentation](https://cert-manager.io/docs/)
