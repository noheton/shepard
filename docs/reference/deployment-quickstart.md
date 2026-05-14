---
layout: default
title: Deployment quickstart
permalink: /reference/deployment-quickstart
---

# Deployment quickstart — `make demo-up`

> ### TL;DR
> ```bash
> git clone https://github.com/noheton/shepard.git
> cd shepard
> make demo-up
> ```
> One command. ~90 seconds. <http://localhost:3000/> — a complete,
> seeded shepard instance with example data already loaded.

This page is the **public-docs entry point** for someone who just
wants to see shepard run locally. The source-of-truth runbook
lives in [`infrastructure-local/README-demo.md`](https://github.com/noheton/shepard/blob/main/infrastructure-local/README-demo.md)
inside the repository — flip there for the operator-level detail
(troubleshooting, credentials table, optional-feature flips).

## What you get

`make demo-up` deploys:

- the **shepard backend** (Quarkus) at <http://localhost:8080>,
- the **shepard frontend** (Nuxt) at <http://localhost:3000>,
- **Neo4j** (graph store),
- **MongoDB** (file payloads via GridFS),
- **Postgres + TimescaleDB** (relational + time-series),
- **Keycloak** at <http://localhost:8082> with four pre-imported
  test users,
- four shipped plugins
  (`unhide`, `kip`, `minter-local`, `reference-dbpedia-databus`),
- and **example data** already seeded so you can click into a
  real Collection, browse a real DataObject, and resolve a real
  KIP PID without doing anything else first.

The five seeded Collections cover the typical shepard use cases:

| Collection | Use case |
|---|---|
| Cyclic-fatigue test campaign 2026-Q1 | Engineering — mechanical-test data |
| MERRA-2 reanalysis subset | Climate — published dataset reference |
| Lab notebook 2026 | Lab-journal — daily entries |
| DLR-internal materials catalogue | Private — only Alice has access |
| Public showcase: shepard demo | Everyone-readable + KIP-resolvable |

## Test users

| User | Password | Role |
|---|---|---|
| `alice` | `alice-demo` | Manager |
| `bob` | `bob-demo` | Reader |
| `admin` | `admin-demo` | instance-admin |
| `harvester` | `harvester-demo` | service |

> Every credential above is **obviously fake**. Don't reuse them
> outside your laptop.

## Make targets

| Target | What it does |
|---|---|
| `make demo-up` | Build + start + seed |
| `make demo-down` | Stop (keeps data) |
| `make demo-reset` | Stop + nuke volumes |
| `make demo-seed` | Re-run the seeder (idempotent) |
| `make demo-status` | Show plugins + Collections |
| `make demo-smoke` | Run the post-up smoke test |
| `make demo-logs` | Tail backend logs |
| `make help` | List every target |

## Production deployment

This page documents the **demo posture**. For a real deployment
(production-grade Caddy + TLS + persistent volumes + real OIDC),
see [`/admin`]({{ '/admin' | relative_url }}) and the
upstream-compatible [`infrastructure/`](https://github.com/noheton/shepard/tree/main/infrastructure)
compose recipe.

The split is intentional: `infrastructure-local/` is for
laptops; `infrastructure/` is for hosts; the demo overlay is for
neither, just for poking at every feature without setting
anything up.

## See also

- Operator runbook: [`infrastructure-local/README-demo.md`](https://github.com/noheton/shepard/blob/main/infrastructure-local/README-demo.md).
- Admin guide: [`/admin`]({{ '/admin' | relative_url }}).
- Admin CLI reference: [`/reference/admin-cli`]({{ '/reference/admin-cli' | relative_url }}).
- Plugins reference: [`/reference/plugins`]({{ '/reference/plugins' | relative_url }}).
- Tracker: `aidocs/16-dispatcher-backlog.md` row **DX5a**.
