---
layout: default
title: "Runbook — Single-file bind-mount inode drift (Caddy, .env)"
description: "Editing a host file via an atomic-rename-style editor (Claude Code Write, mv -f, most modern editors) changes the file's inode. Containers that bind-mount that single file are still attached to the old inode; reload won't pick the new content up. The fix is a container restart, not a reload."
stage: feature-defined
last-stage-change: 2026-05-24
audience: instance-admin
---

# Single-file Docker bind-mount inode drift

## The shape of the failure

Docker single-file bind mounts (e.g. `infrastructure/proxy/Caddyfile:/etc/caddy/Caddyfile`) pin the **inode** of the host file at container-start time, not the path. Modern editors save files atomically by writing a temporary file and renaming it over the target — which changes the inode. When the inode changes, the container's view goes stale and **no in-container reload will pick the new content up**, because the container is reading the wrong inode.

Symptoms in shepard:

- You edit `infrastructure/proxy/Caddyfile` (e.g. to add a new `handle` block)
- `docker exec infrastructure-caddy-1 caddy validate --config /etc/caddy/Caddyfile` reports **Valid configuration**
- `docker exec infrastructure-caddy-1 caddy reload --config /etc/caddy/Caddyfile` reports success
- The new routes do **not** take effect — requests still match the old handle blocks

The same shape applies to `infrastructure/.env` and any other single-file bind mount; the Caddyfile is the most operationally visible because the routing change is observable end-to-end via `curl`.

## Diagnosis (one line)

```bash
diff <(cat infrastructure/proxy/Caddyfile) \
     <(docker exec infrastructure-caddy-1 cat /etc/caddy/Caddyfile)
```

If the diff is non-empty, the bind mount is stale.

## The fix

```bash
docker compose -f infrastructure/docker-compose.yml restart caddy
```

This is a **~3 second gateway blink**. The Zoraxy upstream queues during the restart; in-flight HTTPS connections see a brief connection-reset, then resume. SSE connections (MCP) reconnect automatically via the client's standard `EventSource` retry.

The fix for `.env` changes is the same: restart whichever services consume the env file (often: the full stack via `docker compose up -d --force-recreate`). For `.env` the impact is bigger because more containers depend on it.

## Why this is the wrong fix shape going forward

The right structural fix is to **mount the directory containing the file, not the file itself.** Directory mounts follow the host path, not the inode — so atomic-rename writes propagate immediately + `caddy reload` works as expected.

A future Caddyfile refactor would change the compose entry from:

```yaml
volumes:
  - ./proxy/Caddyfile:/etc/caddy/Caddyfile
```

to:

```yaml
volumes:
  - ./proxy:/etc/caddy
```

(after moving any other files in `proxy/` to a sibling directory). Tracked as `LIM-DOCKER-BIND-MOUNT-INODE` in `aidocs/16-dispatcher-backlog.md` — the runbook fix below is the **interim** discipline until that refactor lands.

## Smoke-probe addition (OPS hygiene follow-up)

A guardrail probe belongs in the OPS hygiene smoke set (see `aidocs/agent-findings/ops-hygiene-bundle-2026-05-24.md`): "for every single-file bind mount declared in the live compose, assert `diff <host-file> <container-view>` is empty." Filed as part of `LIM-DOCKER-BIND-MOUNT-INODE` (queued).

## Affected files in this repo

- `infrastructure/proxy/Caddyfile` — single-file bind mount; surfaces this issue every time the Caddyfile is edited
- `infrastructure/.env` — single-file bind mount; surfaces this issue when the env file is edited live

If you add a new single-file bind mount in `docker-compose.yml`, add it to this list and document the restart requirement in the relevant service's section.

## Provenance

- Filed by: `LIM-DOCKER-BIND-MOUNT-INODE` row in `aidocs/16-dispatcher-backlog.md`
- Surfaced: 2026-05-24 by `CADDY-API-PASSTHROUGH-2026-05-24` shipping (the Caddy reload didn't take; the restart did)
- Memory: `~/.claude/projects/-opt-shepard/memory/feedback_docker_single_file_bind_mount.md`
- Related: `OPS-WORKTREE-ENV` (sibling hygiene class); `feedback_check_branch_before_merge.md` (separate operational drift)
