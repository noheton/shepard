#!/usr/bin/env bash
# run-mffd-import.sh
#
# Fetch the MFFD dropbox import script from Shepard and run it.
# Keeps real data off the git repo — data lives on disk at DATA_DIR,
# the script lives in Shepard (ImportScripts DataObject).
#
# Requirements: curl, jq, uv  (pip install uv or brew install uv)
#
# Usage:
#   export SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de
#   export SHEPARD_BEARER_TOKEN=eyJhbGciOiJSUzI1...
#   export SESSION_ID=2026-05-22-Q1
#   export DATA_DIR=/data/mffd/session-q1
#   bash run-mffd-import.sh [--dry-run]
#
# For nuclide.systems use SHEPARD_API_KEY instead of SHEPARD_BEARER_TOKEN.
#
# First-time bootstrap (before the script is in Shepard):
#   Skip the fetch step and run the local script directly:
#     uv run python mffd-dropbox-import.py
#   The script will upload itself to the ImportScripts DataObject automatically.
#   From the second run on, use this shell script.

set -euo pipefail

SHEPARD_URL="${SHEPARD_URL:-https://shepard.nuclide.systems}"
COLLECTION_NAME="${COLLECTION_NAME:-MFFD-Dropbox}"
SCRIPT_NAME="mffd-dropbox-import.py"
DATAOBJECT_NAME="ImportScripts"

# ── Auth header ──────────────────────────────────────────────────────────────
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

# 1. Find the collection
COLL_ID=$(
  shepard_get "${SHEPARD_URL}/shepard/api/collections?name=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote('${COLLECTION_NAME}'))")" \
  | jq --arg name "${COLLECTION_NAME}" '.[] | select(.name==$name) | .id' \
  | head -1
)
if [[ -z "${COLL_ID}" ]]; then
  echo "ERROR: Collection '${COLLECTION_NAME}' not found — run the script locally first (bootstrap)." >&2
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
  echo "ERROR: DataObject '${DATAOBJECT_NAME}' not found in collection — run the script locally first (bootstrap)." >&2
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
  echo "ERROR: File '${SCRIPT_NAME}' not found in ImportScripts — run the script locally first (bootstrap)." >&2
  exit 1
fi
echo "  file ref id: ${FILE_ID}"

# 4. Download the script
echo "[fetch] Downloading ${SCRIPT_NAME} ..."
shepard_get \
  -H "Accept: application/octet-stream" \
  -o "${SCRIPT_NAME}" \
  "${SHEPARD_URL}/shepard/api/collections/${COLL_ID}/dataObjects/${DO_ID}/fileReferences/${FILE_ID}/payload"

echo "[fetch] ${SCRIPT_NAME} ready ($(wc -c < "${SCRIPT_NAME}") bytes)"

# ── Run ──────────────────────────────────────────────────────────────────────
echo "[run] uv run python ${SCRIPT_NAME} $*"
exec uv run python "${SCRIPT_NAME}" "$@"
