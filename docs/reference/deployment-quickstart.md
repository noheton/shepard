---
layout: default
title: Quickstart — local demo (deployment reference)
permalink: /reference/deployment-quickstart/
description: Five-minute local demo of shepard via make demo-up — seeded LUMEN showcase, mock OIDC, no external dependencies.
---

# Quickstart — local demo

> **Note.** The detailed `make demo-up` runbook ships under
> **DX5a** (`claude/dx5a-deploy-an-instance`, in flight). When
> that slice lands, this page is replaced with the full operator
> walk-through: prerequisites, one-command boot, seeded LUMEN
> showcase Collection, mock OIDC, a worked end-to-end smoke
> test.
>
> Until then, the closest existing path is the upstream
> [`infrastructure-local/README.md`](https://github.com/noheton/shepard/blob/main/infrastructure-local/README.md)
> which boots a developer-shape stack with a local Keycloak.

This page is the **evaluation path** — get a shepard running on
a laptop in five minutes for a demo, a code review, or a
"does this thing do what I need?" kicks-the-tyres session. **Not
for production data.**

For production deployment, start at the
[deployment front door]({{ '/reference/deployment/' | relative_url }}).

## What `make demo-up` will give you (DX5a target)

When DX5a ships, the operator experience is:

```bash
git clone https://github.com/noheton/shepard.git
cd shepard
make demo-up
# wait ~2 min for the stack to come up
open http://localhost:3000
```

You land in a shepard frontend pre-seeded with the LUMEN
showcase Collection. Mock OIDC bypasses real authentication —
log in as the demo user (credentials in the
`make demo-up` output). The stack tears down via
`make demo-down`; data does not persist past the next reboot.

The `make demo-up` flow boots:

- shepard backend (Quarkus, dev mode).
- shepard frontend (Nuxt 3, dev mode with HMR).
- Neo4j, MongoDB, TimescaleDB (single-node, in-memory where
  possible).
- Mock OIDC (no Keycloak), three pre-minted users
  (`demo-user`, `demo-admin`, `read-only`).
- The LUMEN showcase Collection (`examples/seed-showcase/seed.py`).

For air-gapped previews and CI integration, see DX5a once
shipped.

## In the meantime — `infrastructure-local`

The closest already-shipped path is `infrastructure-local/`:

```bash
git clone https://github.com/noheton/shepard.git
cd shepard/infrastructure-local

# 1. Copy the dev environment template
cp .env.example .env

# 2. Boot the databases + Keycloak
docker compose --profile dev up -d

# 3. Configure the Keycloak realm + a frontend client.
#    See infrastructure-local/README.md for the click-through —
#    or use the bundled realm export at
#    infrastructure-local/keycloak_frontend-dev.json.

# 4. Update OIDC_PUBLIC in .env to the Keycloak RS256 public
#    key.

# 5. Boot the shepard backend + frontend
docker compose --profile tryout up -d

# 6. Open http://localhost:3000
```

This path is **developer-shaped**, not seeded — you'll need to
create at least one Collection before there's anything to look
at. The
[upload-data help page]({{ '/help/upload-data/' | relative_url }})
covers the first-Collection flow.

## Why not just deploy directly?

The full production deploy (real DNS, real TLS, real OIDC, real
backups) is **at least an afternoon** of operator work. The
quickstart path **bypasses every production concern** —
self-signed certs, in-memory DBs, mock OIDC — so a researcher
considering shepard can be looking at the UI in minutes.

If after the demo you want to move to a durable instance, work
through the
[deployment front door]({{ '/reference/deployment/' | relative_url }})
and its specialised runbooks. None of the demo-stack credentials
or storage carries over — production is a clean start.

## See also

- [Deployment front door]({{ '/reference/deployment/' | relative_url }})
  — for production.
- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
  — what to set up before exposing to anyone but yourself.
- [`infrastructure-local/README.md`](https://github.com/noheton/shepard/blob/main/infrastructure-local/README.md)
  — the closest already-shipped local-dev recipe.
- [Help: upload your first data]({{ '/help/upload-data/' | relative_url }})
- DX5a slice (in flight) at
  [`claude/dx5a-deploy-an-instance`](https://github.com/noheton/shepard/tree/claude/dx5a-deploy-an-instance).
