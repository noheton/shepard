---
title: API access — calling Shepard programmatically
stage: deployed
last-stage-change: 2026-05-24
---

# API access — calling Shepard programmatically

Shepard exposes its REST and MCP surfaces at the **same hostname** you use in
the browser. A researcher writing a Python script, a Jupyter notebook, or an
MCP-aware AI client can authenticate with an API key or bearer token without
ever touching the web UI's signin flow.

## TL;DR

```python
import requests

API_KEY = "..."  # mint at https://shepard.nuclide.systems/me → API keys
BASE = "https://shepard.nuclide.systems"

r = requests.get(
    f"{BASE}/v2/collections",
    headers={"X-API-KEY": API_KEY},
)
r.raise_for_status()
print(r.json())
```

## Endpoint surface

| Path | Purpose | Auth shape |
|---|---|---|
| `/v2/*` | **This fork's development surface** — every new endpoint lands here | `X-API-KEY` or `Authorization: Bearer <JWT>` |
| `/shepard/api/*` | **Upstream-compat surface** — byte-frozen against shepard 5.2.0 | same |
| `/shepard/doc/openapi/v2.json` | OpenAPI 3 spec — feed into `openapi-generator` | none (public) |
| `/shepard/doc/openapi/swagger-ui` | Swagger UI for browsing endpoints | none (public) |
| `/v2/mcp/sse` | MCP server (SSE transport) for AI clients | `Authorization: Bearer <JWT>` |
| `/v2/mcp` | MCP server (streamable HTTP transport) | same |
| `/` and everything else | The web UI | NextAuth session |

## API key minting

1. Sign in at `https://shepard.nuclide.systems/`
2. Go to **My profile** → **API keys**
3. Click **New key**, copy the secret immediately (shown once)

Each key carries the same permissions as the user who minted it.

## OpenAPI client generation

```bash
# Python
openapi-generator-cli generate \
  -i https://shepard.nuclide.systems/shepard/doc/openapi/v2.json \
  -g python \
  -o ./shepard-py-client

# TypeScript
openapi-generator-cli generate \
  -i https://shepard.nuclide.systems/shepard/doc/openapi/v2.json \
  -g typescript-fetch \
  -o ./shepard-ts-client
```

A first-party Python facade is tracked in the contributor backlog
(`shepard-py` — see `aidocs/16-dispatcher-backlog.md` and the Digital Native
persona report `aidocs/agent-findings/persona-digital-native-2026-05-24.md`
for the prioritisation rationale).

## MCP from Claude or another agent host

In Claude Code:

```bash
claude mcp add shepard https://shepard.nuclide.systems/v2/mcp/sse \
  --header "Authorization: Bearer $(cat ~/.shepard-token)"
```

The MCP server enforces the same permission set as a user session — every
tool call lands as a typed `:Activity` in the provenance trail under
`shepard.actor.<your-username>`.

## Common failure modes

- **HTTP 401 on every request** — the API key is wrong, expired, or
  belongs to a deleted user. Mint a new one.
- **HTTP 403 on a specific endpoint** — your user lacks the role for that
  surface (most `/v2/admin/*` paths require `instance-admin`). Ask an
  instance admin to grant the role.
- **HTTP 302 to NextAuth signin** — you're hitting a path that goes through
  the frontend (anything that isn't `/v2/*`, `/shepard/api/*`, or
  `/shepard/doc/*`). Check the path; for the API, use the surfaces above.
- **HTTP 500 with "OpenApiDocument not initialised"** — backend cold-start
  is in progress. Wait ~30 seconds and retry; the next request will warm
  the spec generator.

## See also

- `docs/reference/` — per-primitive reference pages for every shipped endpoint
- `aidocs/integrations/30-mcp-plugin-design.md` — MCP surface design
- `aidocs/platform/25-neo4j-id-migration-design.md` — why some endpoints take `appId` and others take numeric ids (the L2 chain explains the deprecation path)
