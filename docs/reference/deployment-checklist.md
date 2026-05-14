---
layout: default
title: Pre-flight checklist (deployment reference)
permalink: /reference/deployment-checklist/
description: Tickbox pre-flight checklist for a production shepard deployment — DNS, TLS, OIDC, storage, secrets, ports, default passwords, backups, monitoring.
---

# Pre-flight checklist

Tick every box **before** you run `docker compose up -d` against
a host that will hold real data. Each item links to the runbook
that covers the detail.

The checklist is roughly in the order things bite an operator at
go-live. Items marked **CRITICAL** will cost you data or
credentials if you skip them; **PROD** items are fine to defer
on an internal proof-of-concept but mandatory before public
exposure.

## Identity (CRITICAL)

- [ ] **OIDC provider chosen and reachable.** Keycloak / Helmholtz
      AAI / university SAML→OIDC / GitHub OAuth — pick one. See
      [OIDC setup]({{ '/reference/deployment-oidc/' | relative_url }}).
- [ ] **`OIDC_AUTHORITY`** set with trailing slash —
      e.g. `https://keycloak.example.com/realms/master/`.
- [ ] **`OIDC_PUBLIC`** set to the signing key (RS256 public key
      from `Realm Settings → Keys` in Keycloak; the JWKS-derived
      key for other IdPs).
- [ ] **`OIDC_ROLE`** (optional) — set to restrict access to
      users carrying a specific realm role.
- [ ] **`CLIENT_ID`** matches the OIDC client you registered for
      the shepard frontend.
- [ ] **OIDC discovery doc loads from the backend host** —
      `curl -fsS $OIDC_AUTHORITY/.well-known/openid-configuration`
      should succeed from inside the container network.

## DNS + TLS (CRITICAL for any internet-exposed deploy)

- [ ] **Primary hostname** — e.g. `shepard.example.com` —
      resolves to the host's public IP.
- [ ] **Wildcard subdomain** — e.g. `*.shepard.example.com` —
      resolves to the same IP. The reference Caddyfile uses
      sub-domains for the frontend, backend, MongoDB UI, etc.
- [ ] **TLS certificates** sourced. Caddy + Let's Encrypt is the
      default (zero-touch when DNS resolves); see
      [TLS + reverse proxy]({{ '/reference/deployment-tls/' | relative_url }})
      for nginx / Traefik / cert-manager paths.
- [ ] **Wildcard cert** OR per-subdomain certs — the bundled
      Caddyfile assumes wildcard. Adjust if you provision
      per-subdomain.

## Storage + filesystem (CRITICAL)

- [ ] **`/opt/shepard/` directories created with correct
      ownership** (`185:185` for backend logs / config, `1000:1000`
      for TimescaleDB; see
      `infrastructure/README.md` step 4).
- [ ] **Disk sized** per
      [sizing recommendations]({{ '/reference/deployment-sizing/' | relative_url }}).
      100 GiB is the small-lab floor. Plan separately if you'll
      run the HDF5 sidecar (`~1.2× raw HDF5 size`).
- [ ] **Backup volume** mounted **outside the application disk**
      (e.g. a separate device or remote NFS) so a disk failure
      doesn't take backups with it. See
      [backup + restore]({{ '/reference/deployment-backup/' | relative_url }}).

## Secrets + credentials (CRITICAL)

- [ ] **All `.env` defaults rotated.** The shipped
      `infrastructure/.env.example` carries placeholder
      `POSTGRES_*`, `NEO4J_PW`, `MONGO_PASSWORD`,
      `MONGO_ROOT_PASSWORD`, `FRONTEND_AUTH_SECRET` values that
      are already in the public git history (see `aidocs/07`
      H8). Generate fresh per-host secrets:

      ```bash
      openssl rand -base64 32   # for FRONTEND_AUTH_SECRET
      openssl rand -base64 32   # for each DB password
      ```

- [ ] **Credential cipher key** persisted somewhere — the
      backend's `CredentialCipher` reads a per-instance key
      derived from local state; losing it means losing every
      credential stored for plugins (DataCite Fabrica, ePIC, git
      hosts). See
      [secrets management]({{ '/reference/deployment-secrets/' | relative_url }}).
- [ ] **Bootstrap token** removed from disk after first admin is
      minted. The file lives at `/opt/shepard/.bootstrap-token`
      (mode 0600) — `POST /v2/admin/bootstrap` consumes it.
- [ ] **CORS tightened** — `quarkus.http.cors.origins=*` ships
      permissive; restrict to your frontend's actual hostname
      before exposing publicly.

## Ports + networking

- [ ] **Public TCP** — `80`, `443`, `443/udp` (HTTP/3) only.
- [ ] **Bolt port** (`7687`) is internal-only. The reference
      compose binds it on the `shepard` Docker network with no
      published port; **do not** expose to the public.
- [ ] **PostGIS port** (`5433/tcp` mapped to container `5432`)
      is internal-only.
- [ ] **Grafana port** (`3001`) is internal-only or behind the
      same reverse proxy.
- [ ] **Firewall rules** match the above.

## Databases

- [ ] **Neo4j memory tuned** — `NEO4J_dbms_memory_heap_initial__size`,
      `NEO4J_dbms_memory_heap_max__size`,
      `NEO4J_dbms_memory_pagecache_size` set per sizing.
- [ ] **MongoDB cache sized** — `--wiredTigerCacheSizeGB <n>`
      passed to the `mongo` container.
- [ ] **TimescaleDB tuned** — see
      `infrastructure/tweak-db-settings.sql` for the starting
      `pgtune`-shaped configuration; apply with
      `cat tweak-db-settings.sql | docker exec -i $DB psql -U $ADMIN`.
- [ ] **n10s plugin enabled** in Neo4j — `NEO4J_PLUGINS=["n10s"]`
      + `n10s.*` in `dbms.security.procedures.allowlist` and
      `.unrestricted`. The compose file ships this; verify if
      you customized.

## OIDC role mapping (PROD)

- [ ] **`OIDC_ROLE`** matches the realm role you want gating
      access. If you want every authenticated user to access
      shepard, leave it unset.
- [ ] **`instance-admin` role** mapped if you want IdP-claim-based
      admin access (A0). The default mapping is the
      `instance-admin` realm role for Keycloak. Configurable via
      `shepard.security.oidc.roles-claim-path` (see
      [admin CLI]({{ '/reference/admin-cli/' | relative_url }})).
- [ ] **Bootstrap-token path** chosen — the first admin is minted
      via the file-on-disk token, not the IdP, so an air-gapped
      deploy can still get its first admin without OIDC working
      end-to-end.

## Monitoring + alerts (PROD)

- [ ] **Liveness probe** wired to `/shepard/api/healthz/live`.
- [ ] **Readiness probe** wired to `/shepard/api/healthz/ready`.
- [ ] **Prometheus scraping** `/shepard/doc/metrics/prometheus`
      every 10 s (the bundled `monitoring` profile sets this).
- [ ] **Grafana** dashboard auto-loaded — verify by hitting the
      Grafana UI; the bundled `shepard — Overview` panel is at
      `infrastructure/grafana/dashboards/shepard-overview.json`.
- [ ] **Alerting rules** for "plugin shows FAILED", "DataCite
      mint failed", "migration aborted" — see
      [monitoring]({{ '/reference/deployment-monitoring/' | relative_url }}).

## Backups (PROD — CRITICAL before any real data lands)

- [ ] **Backup schedule defined** for each DB (see
      [backup + restore]({{ '/reference/deployment-backup/' | relative_url }})).
- [ ] **One restore tested** against a scratch host. An untested
      backup is not a backup.
- [ ] **Off-host destination** — backups copied to a separate
      machine / object store / institutional backup target.
- [ ] **Retention window** chosen — daily for 14 days +
      weekly for 12 weeks is a common starting point.

## Plugins (if you ship any)

- [ ] **Bundled plugins** verified loaded —
      `shepard-admin plugins list` should show `unhide`, `kip`,
      and `minter-local` in `ENABLED` state on a fresh image.
- [ ] **Third-party plugins** dropped into `/deployments/plugins/`
      and present in the listing. If a plugin shows `FAILED`,
      see [plugins
      reference]({{ '/reference/plugins/' | relative_url }})
      §"Troubleshooting".
- [ ] **JAR signing** (optional but recommended) — set
      `shepard.plugins.signing.required=true` and import the
      publisher cert into a JKS truststore. See the
      [plugins reference]({{ '/reference/plugins/' | relative_url }})
      §"Signing + compatibility enforcement".

## Optional features

- [ ] **Spatial data** (PostGIS) — enable
      `SHEPARD_INFRASTRUCTURE_SPATIAL_ENABLED=true` AND set
      `COMPOSE_PROFILES=spatial`. Add the PostGIS backup to
      your schedule.
- [ ] **HDF5 / HSDS** — enable `SHEPARD_HDF_ENABLED=true`, set
      `HSDS_USERNAME` / `HSDS_PASSWORD` / `HSDS_BUCKET_NAME` and
      `COMPOSE_PROFILES=hdf`. Add `./hsds-storage/` to your
      backup schedule. Plan for `~1.2× raw HDF5` disk usage.
- [ ] **Monitoring** (Prometheus + Grafana) —
      `COMPOSE_PROFILES=monitoring`, set
      `GRAFANA_ADMIN_USERNAME` / `GRAFANA_ADMIN_PASSWORD` in
      `.env`, **rotate them from the shipped defaults** before
      exposing port 3001.

## Operator runbook ownership

- [ ] **Designated operator-on-call** — a human who knows the
      passwords, has shell on the host, and reads alerts.
- [ ] **Onboarding doc** — the operator-on-call has read
      [this section]({{ '/reference/deployment/' | relative_url }})
      end-to-end at least once.
- [ ] **Runbook for the four most common alerts** —
      "backend won't start", "Neo4j out of memory",
      "migration aborted", "OIDC unreachable". See
      [troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }}).

## After first start

- [ ] **Bootstrap admin minted.** Use the
      `/opt/shepard/.bootstrap-token` file value as the
      `Authorization: Bearer …` for one `POST /v2/admin/bootstrap`
      call.
- [ ] **At least one human admin** mapped to `instance-admin`
      (via IdP claim or `:HAS_ROLE` Neo4j edge).
- [ ] **Bootstrap token deleted from disk** —
      `rm /opt/shepard/.bootstrap-token`. The
      `BootstrapState` replay-protection flag stays in Neo4j.
- [ ] **First end-to-end smoke test** — log in via the frontend,
      create a Collection, create a DataObject, upload a file,
      log out, log back in.
- [ ] **First backup taken** and tested for restore.
- [ ] **Default Grafana credentials rotated.**

## See also

- [Deployment front door]({{ '/reference/deployment/' | relative_url }})
- [Sizing recommendations]({{ '/reference/deployment-sizing/' | relative_url }})
- [OIDC / authentication]({{ '/reference/deployment-oidc/' | relative_url }})
- [Secrets management]({{ '/reference/deployment-secrets/' | relative_url }})
- [Backup + restore]({{ '/reference/deployment-backup/' | relative_url }})
- [Troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }})
- [`aidocs/34`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md) — admin-facing upgrade ledger.
- [`aidocs/07`](https://github.com/noheton/shepard/blob/main/aidocs/07-security-issues.md) — security-issues ledger (H8 default-creds, C2 CORS).
