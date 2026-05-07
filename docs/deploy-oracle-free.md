---
layout: default
title: Deploy on Oracle Cloud Free Tier
description: One-page operator guide for hosting a free shepard test instance on Oracle Cloud Infrastructure (Ampere ARM, always-free).
---

# Deploy on Oracle Cloud Free Tier

A one-page guide for putting a **test instance** of shepard on
Oracle Cloud Infrastructure's Always-Free Ampere ARM allocation. This
is the cheapest practical home for shepard's full stack
(Quarkus + Neo4j + Mongo + TimescaleDB + PostGIS + Nuxt) outside a
managed-services split.

> **Not for production data.** Oracle's "Always Free" guarantee has an
> idle-reclaim clause: a VM with no traffic for 7 consecutive days can
> be flagged for reclamation. Use this for demos, CI environments, and
> reviewer trials — back up anything you care about (see §6).

## 1. What you get

| Resource | Always-Free allowance |
|---|---|
| Compute | **4 OCPU + 24 GB RAM** of `VM.Standard.A1.Flex` (Ampere ARM), splittable across up to 2 VMs |
| Block storage | 200 GB total |
| Object storage | 10 GB (good for backups) |
| Egress | 10 TB / month |
| Public IPv4 | 2 reserved |

For shepard, allocate **the full 4 OCPU + 24 GB** to a single VM. The
default JVM heap (`-Xmx2G` per `infrastructure/docker-compose.yml:5`),
Neo4j 5.24, Mongo 8, TimescaleDB, and PostGIS together fit comfortably
inside 24 GB with headroom for swap.

## 2. Sign up — the friction

Oracle requires a credit card at signup even for the free tier (no
charge unless you upgrade). ARM-shape capacity has historically been
patchy in some regions; if your home region's Ampere pool is full, the
console returns "Out of host capacity" — pick a different home region
on signup (Frankfurt and Phoenix are usually available). The home
region is permanent; choose carefully.

## 3. Provision the VM

In the OCI console: **Compute → Instances → Create instance**.

- **Image:** Canonical Ubuntu 22.04 LTS (`Ubuntu-22.04-aarch64-...`)
- **Shape:** `VM.Standard.A1.Flex`, **4 OCPU**, **24 GB**
- **Networking:** assign a public IPv4. Add an **ingress** rule on the
  default VCN's security list for TCP **22**, **80**, **443**.
  Optionally **8080** while you smoke-test before TLS.
- **Boot volume:** 200 GB (max free).
- **SSH keys:** paste your public key. Password auth stays disabled.

Capacity hint: if creation fails with a capacity error, retry every
30 minutes — Ampere capacity churns. Don't pre-provision a smaller
shape and resize later; create at the target shape.

## 4. System prep on the VM

```bash
ssh ubuntu@<public-ip>

# OCI's default firewalld rules drop everything; either flush or just
# use ufw. The ingress allowlist on the VCN is what matters.
sudo systemctl disable --now firewalld

# Docker (official repo, arm64).
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
newgrp docker

# 8 GB swap. Helps when migrations spike Neo4j heap.
sudo fallocate -l 8G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# UFW for ssh + http(s).
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443
sudo ufw --force enable
```

## 5. Run shepard

### 5a. The ARM image caveat

shepard's published images
(`registry.gitlab.com/dlr-shepard/shepard/{backend,frontend}:5.2.0`)
are **amd64-only** at the time of writing. The data tier
(`neo4j:5.24`, `mongo:8.0`, `postgis/postgis:16-3.5`,
`timescale/timescaledb:2.24.0-pg16`, `caddy:2`) ships multi-arch and
runs natively on ARM.

Two paths:

- **(recommended) Rebuild backend + frontend for `linux/arm64`** on the VM, push to a free registry (Docker Hub free tier or **GitHub Container Registry**, which is free for public images), and pin the compose file to your registry tag. Wall-clock: ≈ 10 min Maven build, ≈ 2 min Nuxt build. Native ARM throughput.

- **(fast) Run the upstream amd64 images under QEMU emulation** — add `platform: linux/amd64` to the `backend` and `frontend` services in `docker-compose.yml`, install `qemu-user-static` (`sudo apt install qemu-user-static binfmt-support`). Functional but ≈ 2-3× slower; fine for a demo.

### 5b. Clone, configure, start

```bash
sudo mkdir -p /opt/shepard && sudo chown -R ubuntu:ubuntu /opt/shepard
cd /opt && git clone https://github.com/noheton/shepard.git
cd shepard/infrastructure
cp .env.example .env

# REQUIRED: rotate the example passwords. The shipped values
# (POSTGRES_*, NEO4J_PW, MONGO_PASSWORD, etc.) are placeholders and
# already-public — see aidocs/07 H8.
$EDITOR .env

docker compose up -d
docker compose ps
```

Backend logs land in `/opt/shepard/backend/logs` per the compose
volume mount.

### 5c. TLS + domain

The repo ships `infrastructure/proxy/Caddyfile` with a
`hostname_placeholder_do_not_change` token. For a free domain:

```bash
# Pick one — DuckDNS free subdomain is the simplest.
# Sign up at https://www.duckdns.org/, get <name>.duckdns.org pointed
# at your OCI public IP.

cd /opt/shepard/infrastructure/proxy
sed -i 's/hostname_placeholder_do_not_change/<name>.duckdns.org/g' Caddyfile

# Switch Caddy from the bundled self-signed cert to Let's Encrypt:
# remove the `auto_https disable_certs` line and the explicit
# `tls /etc/caddy/ssl/...` lines so Caddy auto-issues.
$EDITOR Caddyfile
docker compose restart caddy
```

Caddy obtains and renews Let's Encrypt certs automatically; no
certbot needed.

## 6. Backup to the free Object Storage

```bash
# 10 GB Always-Free object storage. Tarball the volumes nightly.
sudo apt install -y oci-cli
oci setup config   # walks you through API key + region

cat > /usr/local/bin/shepard-backup.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd /opt/shepard
docker compose stop neo4j mongodb timescaledb postgis
tar czf /tmp/shepard-$(date -u +%FT%H%MZ).tgz \
    /var/lib/docker/volumes/{shepard_neo4j_data,shepard_mongodb_data,shepard_timescaledb_data,shepard_postgis_data}
docker compose start neo4j mongodb timescaledb postgis
oci os object put -bn shepard-backups --file /tmp/shepard-*.tgz --force
rm -f /tmp/shepard-*.tgz
EOF
chmod +x /usr/local/bin/shepard-backup.sh

# Nightly at 02:30.
echo '30 2 * * * root /usr/local/bin/shepard-backup.sh' | sudo tee /etc/cron.d/shepard-backup
```

Object Storage lifecycle policies can age out backups beyond 7 days
to stay inside the 10 GB allowance.

## 7. Hardening checklist

Beyond the defaults the steps above set:

- [ ] **Rotate every password** in `.env` — `aidocs/07` H8 explicitly
      flags the shipped defaults as a known issue.
- [ ] **fail2ban** for sshd (`sudo apt install fail2ban`).
- [ ] **Restrict port 22 ingress** to your IP via the VCN security list
      once you have a stable home address.
- [ ] **Disable mongo-express and prometheus exposure** on a public
      instance — they listen on the docker network only by default,
      but double-check `docker compose ps` doesn't bind them to
      `0.0.0.0`.
- [ ] **Set `OIDC_AUTHORITY`** to a real Keycloak (Identity Cloud
      Service free tier, or a self-hosted Keycloak in the same compose).

## 8. What this isn't

- **Not a production deployment.** No HA, no off-site backups by
  default, no log aggregation, no monitoring alerting.
- **Not a privacy-grade host.** Oracle's free tier ToS reserves the
  right to inspect VMs flagged as abusive.
- **Not a benchmark target.** Ampere ARM throughput is solid but the
  free tier shares physical hosts; latency is variable.

For a production deploy, see `docs/admin.md` and consider
self-hosting on bare metal or a paid VPS (Hetzner CCX13 is the usual
shepard-team reference at €4–€8 / month).

## 9. Teardown

`Compute → Instances → Terminate` releases the VM and the boot volume.
The reserved public IP and Object Storage bucket survive separately —
delete those from their respective consoles to fully reclaim the
allowance.
