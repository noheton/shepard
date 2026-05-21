# shepard-plugin-mcp — Installation

## Prerequisites

- Docker + Docker Compose (same stack as Shepard)
- A running Shepard backend (`backend` service healthy)

## 1. Build the image

```bash
docker build -t shepard-plugin-mcp:local plugins/shepard-plugin-mcp/
```

Or via the project Makefile (if extended):
```bash
make build-mcp
```

## 2. Start the service

```bash
docker compose --profile mcp up -d
```

The service will wait for the backend health check before starting.

Caddy automatically routes `/mcp/*` → `shepard-mcp:8811` (prefix stripped) —
see `infrastructure/proxy/Caddyfile`. No additional proxy configuration needed.

After starting, the SSE endpoint is live at:
```
https://shepard.nuclide.systems/mcp/sse
```

## 3. Verify

```bash
curl -N -H "Authorization: Bearer <your-api-token>" \
  https://shepard.nuclide.systems/mcp/sse
```

You should see an SSE stream header. Send an MCP `initialize` message to confirm tool discovery.

## Configuration Reference

All configuration is via environment variables in `docker-compose.override.yml`:

| Variable | Default | Description |
|---|---|---|
| `SHEPARD_API_BASE` | `http://backend:8080` | Backend URL on the Docker network |
| `PORT` | `8811` | Port the MCP server listens on |

## Health Check

The MCP service calls `GET /shepard/api/healthz/ready` on startup to confirm backend connectivity. If the backend is unreachable, the service will log an error but remain up (it retries per request).

## Logs

```bash
docker compose logs -f shepard-mcp
```

Logs include: startup confirmation, tool call names, error traces from failed Shepard API calls.

## Notes

- The server is **stateless**: every tool call carries the caller's bearer token. No sessions, no stored credentials.
- **LTTB downsampling** is applied client-side (in the MCP server) when raw channel data exceeds `maxPoints` (default 2000). This prevents context-window overflow.
- **`compare_channels`** issues N parallel requests for N measurements. On a large container with many measurements this may be slow — use `groupBy` to reduce point counts.
