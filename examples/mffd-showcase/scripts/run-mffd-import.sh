#!/usr/bin/env bash
# run-mffd-import.sh
#
# Fetches mffd-dropbox-import.py from the ImportScripts DataObject in the
# MFFD-Dropbox collection on Shepard, then runs it via uv.
#
# Real data never touches the git repo — data lives on disk at DATA_DIR,
# the versioned script lives inside Shepard itself (provenance artifact).
#
# Requirements: curl, jq, uv
#
# ── Quick start ───────────────────────────────────────────────────────────────
#
# Step 0 — bootstrap once (run from anywhere with access to nuclide.systems):
#
#   SHEPARD_URL=https://shepard.nuclide.systems \
#   SHEPARD_API_KEY=<nuclide-key> \
#   uv run python mffd-dropbox-import.py --bootstrap
#
# Step 1 — full cross-instance import (run from DLR network):
#
#   export SHEPARD_URL=https://shepard.nuclide.systems
#   export SHEPARD_API_KEY=<nuclide-key>
#   export SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de
#   export SOURCE_SHEPARD_API_KEY=eyJhbGciOiJSUzI1NiJ9...   ← DLR intranet JWT
#   export SOURCE_TAPELAYING_COLL_ID=48297
#   export SOURCE_BRIDGEWELDING_COLL_ID=163811
#   export SESSION_ID=2026-05-22-Q1
#   export DATA_DIR=/data/mffd/session-q1
#   bash run-mffd-import.sh [--dry-run]
#
# Auth notes:
#   SHEPARD_API_KEY    Shepard-issued JWT  → sent as X-API-KEY header
#   SHEPARD_BEARER_TOKEN  Keycloak OIDC token → sent as Authorization: Bearer
#   SOURCE_SHEPARD_API_KEY  DLR intranet Shepard JWT (always X-API-KEY format)

set -euo pipefail

SHEPARD_URL="${SHEPARD_URL:-https://shepard.nuclide.systems}"
COLLECTION_NAME="${COLLECTION_NAME:-MFFD-Dropbox}"
SCRIPT_NAME="mffd-dropbox-import.py"
DATAOBJECT_NAME="ImportScripts"

# ── Auth header (for destination Shepard = nuclide.systems) ──────────────────
if [[ -n "${SHEPARD_BEARER_TOKEN:-}" ]]; then
  AUTH_HEADER="Authorization: Bearer ${SHEPARD_BEARER_TOKEN}"
elif [[ -n "${SHEPARD_API_KEY:-}" ]]; then
  AUTH_HEADER="X-API-KEY: ${SHEPARD_API_KEY}"
else
  echo "ERROR: set SHEPARD_BEARER_TOKEN or SHEPARD_API_KEY" >&2
  exit 1
fi

# ── Fetch helper ─────────────────────────────────────────────────────────────
shepard_get() {
  curl -sS -H "${AUTH_HEADER}" -H "Accept: application/json" "$@"
}

echo "[fetch] Locating ${SCRIPT_NAME} in ${SHEPARD_URL} ..."

# 1. Find the MFFD-Dropbox collection
COLL_ID=$(
  shepard_get "${SHEPARD_URL}/shepard/api/collections?name=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote('${COLLECTION_NAME}'))")" \
  | jq --arg name "${COLLECTION_NAME}" '.[] | select(.name==$name) | .id' \
  | head -1
)
if [[ -z "${COLL_ID}" ]]; then
  echo "ERROR: Collection '${COLLECTION_NAME}' not found." >&2
  echo "       Run bootstrap first:" >&2
  echo "         SHEPARD_URL=${SHEPARD_URL} SHEPARD_API_KEY=<key> \\" >&2
  echo "         uv run python mffd-dropbox-import.py --bootstrap" >&2
  exit 1
fi
echo "  collection id: ${COLL_ID}"

# 2. Find the ImportScripts DataObject
DO_ID=$(
  shepard_get "${SHEPARD_URL}/shepard/api/collections/${COLL_ID}/dataObjects?name=${DATAOBJECT_NAME}" \
  | jq --arg name "${DATAOBJECT_NAME}" '.[] | select(.name==$name) | .id' \
  | head -1
)
if [[ -z "${DO_ID}" ]]; then
  echo "ERROR: DataObject '${DATAOBJECT_NAME}' not found in '${COLLECTION_NAME}'." >&2
  echo "       Re-run bootstrap to create it:" >&2
  echo "         uv run python mffd-dropbox-import.py --bootstrap" >&2
  exit 1
fi
echo "  dataobject id: ${DO_ID}"

# 3. Find the file reference for mffd-dropbox-import.py
FILE_ID=$(
  shepard_get "${SHEPARD_URL}/shepard/api/collections/${COLL_ID}/dataObjects/${DO_ID}/fileReferences" \
  | jq --arg name "${SCRIPT_NAME}" '.[] | select(.name==$name) | .id' \
  | head -1
)
if [[ -z "${FILE_ID}" ]]; then
  echo "ERROR: '${SCRIPT_NAME}' not found in ImportScripts." >&2
  echo "       The script is self-uploaded during bootstrap — re-run it:" >&2
  echo "         uv run python mffd-dropbox-import.py --bootstrap" >&2
  exit 1
fi
echo "  file ref id:   ${FILE_ID}"

# 4. Download the script
echo "[fetch] Downloading ${SCRIPT_NAME} ..."
shepard_get \
  -H "Accept: application/octet-stream" \
  -o "${SCRIPT_NAME}" \
  "${SHEPARD_URL}/shepard/api/collections/${COLL_ID}/dataObjects/${DO_ID}/fileReferences/${FILE_ID}/payload"

echo "[fetch] ${SCRIPT_NAME} ready ($(wc -c < "${SCRIPT_NAME}") bytes)"

# ── Run ───────────────────────────────────────────────────────────────────────
echo "[run] uv run python ${SCRIPT_NAME} $*"
exec uv run python "${SCRIPT_NAME}" "$@"
