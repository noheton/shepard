---
layout: default
title: Admin CLI (reference)
permalink: /reference/admin-cli/
---

# `shepard-admin` reference (L1 Phase 1)

`shepard-admin` is the operator command-line tool that wraps the
`/v2/admin/...` and operator-only `/shepard/api/temp/migrations/...`
endpoints. Phase 1 ships three read-only commands; mutation, RO-Crate
import/export, and the first-run TUI wizard land in later phases (see
`aidocs/22`).

## Install

The CLI is shipped as a shaded uber-jar — one file, runs on any host
with Java 21. Until the JBang catalogue + GHCR container image ship in
a follow-up phase, the canonical install path is a local Maven build:

```bash
git clone https://github.com/noheton/shepard.git
cd shepard/cli
mvn package -DskipTests
# Produces cli/target/shepard-admin-<version>.jar
java -jar target/shepard-admin-*.jar --version
```

A future minor release will publish to a JBang catalogue and a GHCR
container image; this page will update with the published commands
when they land.

## Configure

The CLI reads its target shepard URL and the API key it presents in
the `X-API-KEY` header from layered sources (highest priority first):

1. CLI flags: `--url <base-url>` and `--api-key <key>`.
2. Environment variables: `SHEPARD_ADMIN_URL` and `SHEPARD_ADMIN_API_KEY`.
3. Config file: `~/.shepard/admin.toml` — keys `url = "..."` and
   `apiKey = "..."`.
4. Built-in defaults — URL falls back to `http://localhost:8080`; API
   key has no default (commands needing auth report a clear error).

The API key **must carry the `instance-admin` role** (per A0; see
`aidocs/51`). Mint it with the role explicitly set:

```bash
# Create an admin-role-bearing key via the existing API key endpoints
# (the minter must themselves be an instance-admin).
curl -X POST \
  -H "X-API-KEY: $YOUR_ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"name":"ops-cli","roles":["instance-admin"]}' \
  https://shepard.example.com/shepard/api/users/$ME/apikeys
```

Export the key + URL once per shell:

```bash
export SHEPARD_ADMIN_URL=https://shepard.example.com
export SHEPARD_ADMIN_API_KEY=<key-from-above>
```

After that, every CLI invocation runs against your shepard instance
with zero per-command flags.

## Commands

### `shepard-admin features list`

Lists the runtime feature toggles known to the backend. Calls
`GET /v2/admin/features`.

```text
$ shepard-admin features list
NAME        ENABLED  DESCRIPTION
----------  -------  ---------------------------------------------
spatial     true     Enable spatial payloads.
versioning  false    Enable versioned writes.
hdf         false    Enable HDF5/HSDS payloads.
```

JSON output for scripting:

```text
$ shepard-admin features list --output json
[
  {"name":"spatial","enabled":true,"description":"Enable spatial payloads."},
  {"name":"versioning","enabled":false,"description":"Enable versioned writes."}
]
```

### `shepard-admin health`

Probes the backend's Quarkus SmallRye-Health endpoints
(`/shepard/api/healthz/ready` and `/shepard/api/healthz/live`) and
prints the per-check breakdown. **Exit code maps to health state** —
0 when both readiness and liveness are `UP`, 1 when either reports
`DOWN`. Drop this into a shell pipeline as a kubelet-style probe.

```text
$ shepard-admin health
READINESS — overall UP
CHECK   STATUS  DATA
------  ------  -----------
neo4j   UP      latency-ms=4
mongo   UP

LIVENESS — overall UP
CHECK  STATUS  DATA
-----  ------  ----
alive  UP

overall: readiness=UP liveness=UP
$ echo $?
0
```

When something is wrong:

```text
$ shepard-admin health
READINESS — overall DOWN
CHECK   STATUS  DATA
------  ------  --------------------------
neo4j   DOWN    error=connection refused
mongo   UP

LIVENESS — overall UP
CHECK  STATUS  DATA
-----  ------  ----
alive  UP

overall: readiness=DOWN liveness=UP
$ echo $?
1
```

### `shepard-admin migrations status [containerId]`

Lists migration progress. Without a positional argument, fetches
`GET /shepard/api/temp/migrations/state` and prints one row per
container. With an explicit `containerId`, fetches
`GET /shepard/api/temp/migrations/{containerId}` and prints a
single-row table.

```text
$ shepard-admin migrations status
CONTAINER  STATUS     MIGRATED  TOTAL  FAILED  BATCH  STARTED                UPDATED                ETA(s)
---------  ---------  --------  -----  ------  -----  ---------------------  ---------------------  ------
42         RUNNING    750       1000   0       7      2026-05-12T10:00:00Z   2026-05-12T10:05:30Z   90
99         COMPLETED  50        50     0       1      2026-05-11T09:00:00Z   2026-05-11T09:00:10Z   -
```

For one container (handy under `watch` for a live progress bar):

```text
$ shepard-admin migrations status 42
CONTAINER  STATUS   MIGRATED  TOTAL  FAILED  BATCH  STARTED                UPDATED                ETA(s)
---------  -------  --------  -----  ------  -----  ---------------------  ---------------------  ------
42         RUNNING  750       1000   0       7      2026-05-12T10:00:00Z   2026-05-12T10:05:30Z   90
```

Failed containers surface their `errors` field underneath the table:

```text
$ shepard-admin migrations status
CONTAINER  STATUS  MIGRATED  TOTAL  FAILED  BATCH  STARTED                UPDATED                ETA(s)
---------  ------  --------  -----  ------  -----  ---------------------  ---------------------  ------
13         FAILED  100       500    7       2      2026-05-12T08:00:00Z   2026-05-12T08:01:00Z   -

container 13 errors: schema column missing on batch 3
```

JSON output is symmetric — a list when no `containerId` is given, a
single object when one is.

## Exit codes

| Code | Meaning |
|---:|---|
| 0 | Success (including the "no migrations" and "nothing to list" cases). |
| 1 | Operator-readable error — connect failure, 401/403/404, malformed body, or `health` reported anything DOWN. The message on stderr is meant to be the only thing you need; `--verbose` adds the stack. |
| 2 | Unexpected runtime exception. |

## Common errors

| Stderr message | Cause | Fix |
|---|---|---|
| `error: Cannot connect to shepard at <url> — is the backend reachable?` | TCP-level refusal — wrong host/port, or shepard down. | Verify `$SHEPARD_ADMIN_URL` and that the backend pod is `Ready`. |
| `error: 401 Unauthorized from <url> — set SHEPARD_ADMIN_API_KEY or pass --api-key.` | API key missing or wrong. | Set the env var or pass `--api-key`. |
| `error: 403 Forbidden from <url> — the API key lacks the instance-admin role.` | The key is valid but role-less. | Rotate to a key whose JWT carries `roles: ["instance-admin"]`. The minting user must themselves hold the role (no privilege escalation). |
| `error: 404 Not Found from <url> — endpoint missing (backend may be older than this CLI expects).` | CLI expects `/v2/admin/features` (or another endpoint) that doesn't exist on the running backend version. | Upgrade the backend to a build that includes A3b / A0 / P3c, or pin the CLI to a matching version. |

## What's next

Phase 2 will add `shepard-admin cleanup deleted-entities --dry-run`
(`aidocs/22 §4.1`). Phase 3 adds RO-Crate import/export
(`aidocs/22 §4.7`). Phase 4 lands the `init` TUI wizard, JBang
distribution, and the universal-TUI patterns from `aidocs/22 §4.x`.
