#!/usr/bin/env bash
# DB-OPT5 — measure the v2 DataObject list payload size with + without the
# new ?fields= param and the default-trim, against a live Shepard instance.
#
# Usage:
#   SHEPARD_URL=https://shepard-api.nuclide.systems \
#   SHEPARD_TOKEN=eyJ...JWT... \
#   COLLECTION_APPID=019e6ffc-89a4-76b5-8dbb-15888646a904 \
#   bash measure-payload-diet.sh
#
# All three env vars are required. The script hits the same collection four
# times — `?include=full`, default-trim (no param), explicit `?fields=` for
# the panel-shaped allow-list, and the minimal "id+name+createdAt" shape —
# and reports Content-Length, wall-clock, and time-to-first-byte for each.
#
# Acceptance gate for DB-OPT5:
#   - default-trim ≥ 50% smaller than ?include=full (median DO)
#   - panel fields ≥ 50% smaller than ?include=full
#   - minimal fields ≥ 75% smaller than ?include=full
#
# The LUMEN seed has only ~15 DataObjects so absolute byte counts are small;
# point COLLECTION_APPID at MFFD-Dropbox (~8500 DOs) to see the real impact.

set -euo pipefail

: "${SHEPARD_URL:?SHEPARD_URL env var required (e.g. https://shepard-api.nuclide.systems)}"
: "${SHEPARD_TOKEN:?SHEPARD_TOKEN env var required (JWT or API key)}"
: "${COLLECTION_APPID:?COLLECTION_APPID env var required (UUID v7)}"

SIZE="${PAGE_SIZE:-50}"
BASE="${SHEPARD_URL%/}/v2/collections/${COLLECTION_APPID}/data-objects"

# Panel allow-list: exactly what CollectionDataObjectsPanel.vue renders.
PANEL_FIELDS="id,appId,name,status,createdAt,referenceIds,childrenIds,incomingIds,timeseriesCount,fileCount,structuredDataCount,timeBoundsStart,timeBoundsEnd"
# Minimal: id + name + createdAt (for navigation + sort only).
MIN_FIELDS="appId,name,createdAt"

probe() {
  local label="$1"
  local url="$2"
  local resp_file
  resp_file="$(mktemp)"
  local stats
  stats="$(curl -sS \
    -o "$resp_file" \
    -w "size=%{size_download} ttfb=%{time_starttransfer} total=%{time_total} http=%{http_code}" \
    -H "X-API-KEY: ${SHEPARD_TOKEN}" \
    -H "Accept: application/json" \
    "$url")"
  local bytes
  bytes="$(wc -c < "$resp_file" | tr -d ' ')"
  local diet_hdr
  diet_hdr="$(curl -sSI \
    -H "X-API-KEY: ${SHEPARD_TOKEN}" \
    "$url" 2>/dev/null | awk -F': *' '/^X-Shepard-Payload-Diet/ {print $2}' | tr -d '\r' || true)"
  rm -f "$resp_file"
  printf '%-18s %-12s %s bytes (Content-Length-equivalent: %s)\n' "$label" "[diet=${diet_hdr:-?}]" "$bytes" "$stats"
}

echo "DB-OPT5 — payload diet measurement"
echo "URL    : $SHEPARD_URL"
echo "Coll   : $COLLECTION_APPID"
echo "PageSize: $SIZE"
echo "----------------------------------------------------------------------"
probe "include=full"   "${BASE}?size=${SIZE}&include=full"
probe "default-trim"   "${BASE}?size=${SIZE}"
probe "panel-fields"   "${BASE}?size=${SIZE}&fields=${PANEL_FIELDS}"
probe "minimal-fields" "${BASE}?size=${SIZE}&fields=${MIN_FIELDS}"
echo "----------------------------------------------------------------------"
echo "Acceptance gate: default-trim and panel-fields ≥ 50% smaller than include=full;"
echo "                 minimal-fields ≥ 75% smaller than include=full."
