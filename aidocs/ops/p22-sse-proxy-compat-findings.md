---
title: P22 — SSE proxy-compatibility findings
stage: concept
last-stage-change: 2026-05-26
---

# P22 — SSE proxy-compatibility findings

**Tripwire for:** `aidocs/ops/28-paradigms-and-clients-synthesis.md §7.1`
**Feature gate for:** P13 (`CollectionEventsRest`) default-on promotion

---

## Finding

### 1. The problem: `handle /v2/*` catch-all lacks `flush_interval -1`

Inspecting `infrastructure/proxy/Caddyfile`:

```caddy
# Lines 42-44 (handle /v2/* catch-all)
handle /v2/* {
    reverse_proxy backend:8080
}
```

This block has **no `flush_interval` directive**, which means Caddy uses its
default buffering behaviour. For SSE, this causes the proxy to hold received
bytes until either its internal buffer fills or a default flush interval
elapses — meaning clients do not receive events in real time.

Compare with the existing MCP block (lines 29-37) which *correctly* handles
a long-lived SSE connection:

```caddy
# Lines 29-37 (handle /v2/mcp* — correct shape)
handle /v2/mcp* {
    reverse_proxy backend:8080 {
        flush_interval -1
        transport http {
            read_timeout 24h
            write_timeout 24h
        }
    }
}
```

`flush_interval -1` tells Caddy to flush immediately on every write from the
upstream, which is exactly what SSE requires. The MCP block was already
written this way (MCP-over-SSE needed the same fix), but the generic
`/v2/*` catch-all that handles `GET /v2/collections/{id}/events` does not
inherit those settings.

**Note on `X-Accel-Buffering: no`:** This is an nginx convention. Caddy does
**not** honour `X-Accel-Buffering: no` — it uses its own `flush_interval`
directive. So even though Quarkus/Vert.x may send this header on SSE
responses (following Quarkus defaults), Caddy ignores it on the `handle /v2/*`
block. The fix must be in the Caddyfile.

### 2. Zoraxy (upstream TLS terminator)

The Caddyfile header (lines 1-7) documents that Zoraxy terminates TLS and
forwards plain HTTP to Caddy on port 80. Zoraxy sits at
`https://shepard.nuclide.systems → 192.168.1.49:80`. Zoraxy is outside
this repo's configuration, but operators should verify it does not buffer
SSE streams. Most Zoraxy versions forward as-is without additional buffering,
but the smoke test (`e2e/scripts/smoke-sse.sh`) will catch any Zoraxy-level
buffering in production.

---

## Required fix: Caddyfile change

Add a dedicated block for the SSE route **before** the generic `/v2/*` catch-all.
Caddy matches `handle` blocks in order — a more-specific path earlier wins.

```caddy
# P22: SSE change-feed — needs flush_interval -1 + long timeouts.
# Must be placed BEFORE the generic handle /v2/* block.
handle /v2/collections/*/events {
    reverse_proxy backend:8080 {
        flush_interval -1
        transport http {
            read_timeout 2h
            write_timeout 2h
        }
    }
}
```

Two hours is sufficient for a research session. The `CollectionEventBus`
sends a 30-second heartbeat to keep the connection alive through idle
periods; a 2-hour transport timeout is conservative and leaves room for
a multi-hour recording session without forcing reconnects.

**Alternative:** extend the existing MCP block into a shared directive or
add `flush_interval -1` to the generic `/v2/*` block directly. The
dedicated route above is preferred because it keeps the timeout configuration
narrow — only SSE connections hold a socket open for hours, not regular
REST calls.

---

## Status

| Layer          | Status                              | Action required |
|----------------|-------------------------------------|-----------------|
| Backend (P13)  | `CollectionEventsRest` ships; sets  | None            |
|                | `@Produces(SERVER_SENT_EVENTS)`     |                 |
| Caddy          | `handle /v2/*` lacks               | Add dedicated   |
|                | `flush_interval -1`                 | block above     |
| Zoraxy         | No buffering observed (no config    | Verify in       |
|                | available in this repo)             | prod smoke test |

---

## Test

**Automated:** `e2e/tests/sse-proxy-compat.spec.ts` (Playwright)
- Test 1: asserts HTTP 200 (no 502/504 from Caddy)
- Test 2: asserts `Content-Type: text/event-stream` present in response
- Test 3: asserts at least one event received within 15 s (flush check)
- Test 4: asserts 401 on unauthenticated request

Requires `SSE_SMOKE_COLLECTION_ID` env var pointing to a readable collection.
All four tests are skipped (not failed) when the env var is unset.

**Manual / operator:** `e2e/scripts/smoke-sse.sh`

```bash
SHEPARD_URL=https://shepard.nuclide.systems \
API_KEY=your-api-key \
COLLECTION_ID=your-collection-appid \
./e2e/scripts/smoke-sse.sh
```

Exit 0 = SSE stream works through the full proxy stack.
Exit 1 = no events received (likely buffering — apply the Caddyfile fix above).

---

## References

- `infrastructure/proxy/Caddyfile` — reverse proxy configuration
- `backend/src/main/java/de/dlr/shepard/v2/events/CollectionEventsRest.java` — P13 SSE endpoint
- `aidocs/ops/28-paradigms-and-clients-synthesis.md §7.1` — SSE proxy risk note
- `aidocs/16-dispatcher-backlog.md` row P22 — backlog tracking
