---
layout: default
title: "Runbook — Apply a hotpatch to the DLR cube instance"
description: "Step-by-step procedure to pull a specific backend or frontend image tag on the DLR cube instance, verify the SHA-256 digest, and roll back if the new image misbehaves. Parameterised by VERSION and SHA256."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: cube
tested: "— (procedure derived from live session pattern; reviewed 2026-05-26)"
---

# Apply a hotpatch to the DLR cube instance

> **When to use this runbook**: A fix has been merged to `main` and a new image tag
> has been built and pushed to GHCR. You need to pull the new tag on the DLR cube
> instance and restart the affected service without a full redeploy from scratch.

---

## Prerequisites

- SSH access to the DLR cube host (VPN required from outside DLR network).
- `docker` available on the cube host; user in the `docker` group.
- The target image **tag** (`VERSION`) and its **digest** (`SHA256`) — take both from
  the GitHub Actions "Build and push images" run or the GitHub Packages page for
  `ghcr.io/noheton/shepard-backend` / `ghcr.io/noheton/shepard-frontend`.
- The compose stack lives at `/opt/shepard/infrastructure/` on the cube host.
- `${NEO4J_PASSWORD}` available in the shell (source from
  `/opt/shepard/infrastructure/.env` on the cube).

---

## Steps

### 0. Set the patch parameters

```bash
# [cube] — run all cube steps on the DLR cube host

export VERSION="5.6.1"           # ← fill in the actual tag
export SHA256="sha256:abc123..."  # ← fill in the expected digest from GHCR
export SERVICE="backend"          # ← "backend" or "frontend"
```

### 1. Pull the new image and verify the digest

```bash
# [cube]
docker pull ghcr.io/noheton/shepard-${SERVICE}:${VERSION}
```

Expected: `Status: Downloaded newer image for ghcr.io/noheton/shepard-${SERVICE}:${VERSION}`

Verify the digest matches what CI published:

```bash
# [cube]
docker inspect --format='{{index .RepoDigests 0}}' \
  ghcr.io/noheton/shepard-${SERVICE}:${VERSION}
```

Expected: output contains `@${SHA256}`. If it does not, **stop** — the image may be
corrupted or you have the wrong tag. Do not proceed.

### 2. Snapshot the current state (Neo4j backup)

```bash
# [cube]
docker exec infrastructure-neo4j-1 \
  neo4j-admin database dump neo4j \
  --to-path=/data/dumps/pre-hotpatch-$(date +%Y%m%d-%H%M%S).dump
echo "Dump complete: $(ls -lh /opt/shepard/neo4j/dumps/ | tail -1)"
```

Expected: dump file created in `/opt/shepard/neo4j/dumps/` (bind-mounted from `/data/dumps/`).

### 3. Record the currently running image digest

```bash
# [cube]
PREV_DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' \
  ghcr.io/noheton/shepard-${SERVICE}:latest 2>/dev/null || echo "none")
echo "Previous digest: ${PREV_DIGEST}"
```

Save the printed value — you'll need it for rollback.

### 4. Update the image tag in the compose override

Edit `/opt/shepard/infrastructure/docker-compose.override.yml` and set the
`image:` field of the affected service to the new tag:

```yaml
# example for backend
services:
  backend:
    image: ghcr.io/noheton/shepard-backend:5.6.1
```

(If the override already pins a tag, change it in place. If it inherits `latest`,
add an explicit `image:` line.)

### 5. Apply the patch (rolling restart)

```bash
# [cube]
cd /opt/shepard/infrastructure
docker compose up -d --no-deps ${SERVICE}
```

Expected: `Container infrastructure-${SERVICE}-1  Started`

### 6. Wait for the service to become healthy

```bash
# [cube]
until docker exec infrastructure-backend-1 \
  curl -fsS http://localhost:8080/shepard/api/healthz/ready > /dev/null 2>&1; do
  echo "waiting for backend readiness…"; sleep 5
done
echo "Backend is UP"
```

(Skip for `frontend` — check with `curl -fsS https://shepard.<cube-hostname>/` instead.)

### 7. Confirm the running image

```bash
# [cube]
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}' \
  --filter "name=infrastructure-${SERVICE}-1"
```

Expected: `IMAGE` column shows `ghcr.io/noheton/shepard-${SERVICE}:${VERSION}`.

### 8. Smoke-test

```bash
# [operator-machine or cube]
curl -fsS https://shepard-api.<cube-hostname>/shepard/api/healthz/ready \
  | jq '.status'
```

Expected: `"UP"`

```bash
curl -fsS -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  https://shepard-api.<cube-hostname>/v2/admin/features \
  | jq 'length'
```

Expected: a positive integer (number of registered features).

---

## Rollback

If the new image misbehaves, revert to the previous digest:

```bash
# [cube]
cd /opt/shepard/infrastructure

# 1. Restore the old tag in docker-compose.override.yml (manual edit, or:)
docker pull ghcr.io/noheton/shepard-${SERVICE}@${PREV_DIGEST}
docker tag ghcr.io/noheton/shepard-${SERVICE}@${PREV_DIGEST} \
  ghcr.io/noheton/shepard-${SERVICE}:rollback

# Edit docker-compose.override.yml to pin image: ...:rollback
docker compose up -d --no-deps ${SERVICE}
```

Re-run step 8 to confirm the old behaviour is restored.

---

## End-state verification

```bash
# [cube]
curl -fsS https://shepard-api.<cube-hostname>/shepard/api/healthz/ready \
  | jq '{status, checks: [.checks[] | {name, status}]}'
```

Expected: `"status": "UP"` and every check `"status": "UP"`.

---

## Provenance

- Pattern derived from live sessions 2026-05-24/2026-05-26 applying backend patches to nuclide.
- Related: `docs/admin/runbooks/migration-chain-integrity.md` (if the hotpatch includes new Neo4j migrations, that runbook covers what to do if the chain health check goes DOWN post-restart).
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
