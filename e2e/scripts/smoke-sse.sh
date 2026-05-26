#!/usr/bin/env bash
# P22: SSE proxy-compatibility smoke test
#
# Usage:
#   SHEPARD_URL=https://shepard.nuclide.systems \
#   API_KEY=xxx \
#   COLLECTION_ID=yyy \
#   ./smoke-sse.sh
#
# Exit 0 = SSE stream received at least one event (heartbeat or data) within TIMEOUT seconds
# Exit 1 = connection failed, returned an error status, or no events received within TIMEOUT
#
# Environment variables:
#   SHEPARD_URL    Base URL of the Shepard instance (no trailing slash)
#                  Default: http://localhost:8080
#   COLLECTION_ID  appId of a Collection the API_KEY has Read access to (required)
#   API_KEY        Bearer token or API key value (required)
#   TIMEOUT        Seconds to wait for at least one event before giving up (default: 15)
#
# P22 background:
#   CollectionEventsRest (P13) publishes a text/event-stream on
#   GET /v2/collections/{collectionAppId}/events.  An immediate HEARTBEAT event
#   is emitted on connect; subsequent heartbeats arrive every 30 s.  This script
#   connects through the full reverse-proxy stack (Zoraxy → Caddy → Quarkus) and
#   asserts that at least the connect-time HEARTBEAT passes back without buffering.
#
#   KNOWN ISSUE (aidocs/ops/p22-sse-proxy-compat-findings.md):
#   The Caddy `handle /v2/*` catch-all block (Caddyfile line 43-45) does NOT set
#   `flush_interval -1`.  SSE events may be buffered until Caddy's default flush
#   interval elapses.  Until that block is fixed (or a dedicated
#   `handle /v2/collections/*/events` block is added with `flush_interval -1` +
#   long timeouts), this test may intermittently fail even when the backend is
#   healthy.  See the findings doc for the required Caddyfile patch.
#
# curl flags used:
#   -s              silent (no progress bar)
#   -f              fail on HTTP error status (4xx/5xx)
#   --no-buffer / -N  disable curl's own output buffer (critical for SSE)
#   -H "Accept: text/event-stream"  signal SSE client intent
#   -H "Authorization: Bearer ..."  carry auth (native EventSource cannot do this)
#   --max-time      hard wall-clock limit; curl exits when limit reached

set -euo pipefail

SHEPARD_URL="${SHEPARD_URL:-http://localhost:8080}"
COLLECTION_ID="${COLLECTION_ID:?must set COLLECTION_ID to a collection appId}"
API_KEY="${API_KEY:?must set API_KEY to a valid Bearer token or API key}"
TIMEOUT="${TIMEOUT:-15}"

SSE_URL="$SHEPARD_URL/v2/collections/$COLLECTION_ID/events"

echo "P22: SSE proxy-compatibility smoke test"
echo "  URL:     $SSE_URL"
echo "  Timeout: ${TIMEOUT}s"
echo ""

# Capture output; suppress curl exit code — we inspect the output to distinguish
# "timed out after receiving events" (success) from "timed out with nothing" (failure).
RESULT=$(
  curl -sf --no-buffer -N \
    -H "Authorization: Bearer $API_KEY" \
    -H "Accept: text/event-stream" \
    --max-time "$TIMEOUT" \
    "$SSE_URL" 2>&1
) || CURL_RC=$?

# curl exits non-zero on --max-time expiry (exit 28) OR on connect/auth failure.
# We captured stdout+stderr; inspect for SSE event markers regardless of exit code.
# A healthy stream that is cut by --max-time will have emitted at least one event.

if echo "$RESULT" | grep -qE 'data:|:heartbeat|event:|HEARTBEAT'; then
  echo "PASS: SSE stream received at least one event through the reverse proxy."
  echo ""
  echo "First 5 lines received:"
  echo "$RESULT" | head -5
  exit 0
else
  echo "FAIL: No SSE events received within ${TIMEOUT}s."
  echo ""
  echo "Raw output (up to 20 lines):"
  echo "$RESULT" | head -20
  echo ""
  echo "Possible causes:"
  echo "  1. Caddy buffering — 'handle /v2/*' block lacks 'flush_interval -1'."
  echo "     See aidocs/ops/p22-sse-proxy-compat-findings.md for the required patch."
  echo "  2. Authentication failure — check API_KEY is valid."
  echo "  3. Collection not found — check COLLECTION_ID is a valid appId."
  echo "  4. Backend unhealthy — check GET $SHEPARD_URL/q/health."
  exit 1
fi
