#!/bin/bash
# Demo instance seeder — runs once at `docker compose up` time.
# Waits for backend + Keycloak, bootstraps the admin user if needed,
# creates an API key via JWT Bearer auth, then runs seed.py.
set -euo pipefail

BACKEND="${BACKEND_URL:-http://backend:8080/shepard/api}"
BACKEND_ROOT="${BACKEND%/shepard/api}"
KC="${KC_URL:-http://keycloak:8082}"
REALM="${KC_REALM:-shepard-demo}"
CLIENT_ID="${KC_CLIENT_ID:-frontend-dev}"
ADMIN_USER="${KC_ADMIN_USER:-admin}"
ADMIN_PASS="${KC_ADMIN_PASS:-admin-demo}"
BOOTSTRAP_TOKEN_PATH="${BOOTSTRAP_TOKEN_PATH:-/opt/shepard-bootstrap/.bootstrap-token}"

log() { echo "[seeder] $*"; }

# ---- wait for dependencies ------------------------------------------------

log "Waiting for Keycloak at ${KC}/realms/${REALM}/.well-known/openid-configuration ..."
until curl -sf "${KC}/realms/${REALM}/.well-known/openid-configuration" > /dev/null 2>&1; do
  sleep 4
done
log "Keycloak ready."

log "Waiting for backend readiness at ${BACKEND_ROOT}/q/health/ready ..."
until curl -sf "${BACKEND_ROOT}/q/health/ready" > /dev/null 2>&1; do
  sleep 5
done
log "Backend ready."

# ---- obtain Keycloak access token -----------------------------------------

log "Obtaining Keycloak access token for ${ADMIN_USER} ..."
KC_RESPONSE=$(curl -sf -X POST \
  "${KC}/realms/${REALM}/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_ID}" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "scope=openid")
ACCESS_TOKEN=$(python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" <<< "${KC_RESPONSE}")
log "Access token obtained."

# ---- bootstrap instance-admin if first start ------------------------------

if [ -f "${BOOTSTRAP_TOKEN_PATH}" ]; then
  log "Bootstrap token found — granting instance-admin to ${ADMIN_USER} ..."
  BOOTSTRAP_TOKEN=$(cat "${BOOTSTRAP_TOKEN_PATH}")
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${BACKEND_ROOT}/v2/admin/bootstrap" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"${BOOTSTRAP_TOKEN}\",\"username\":\"${ADMIN_USER}\"}")
  if [ "${HTTP_STATUS}" = "201" ]; then
    log "Bootstrap succeeded — ${ADMIN_USER} is now instance-admin."
  else
    log "Bootstrap returned HTTP ${HTTP_STATUS} (already done or failed) — continuing."
  fi
else
  log "No bootstrap token file — instance-admin already exists, skipping bootstrap."
fi

# ---- create seed API key ---------------------------------------------------

log "Creating seed API key for ${ADMIN_USER} ..."
API_KEY_RESPONSE=$(curl -sf -X POST \
  "${BACKEND}/apikeys/${ADMIN_USER}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"demo-seeder-key"}')
SEED_API_KEY=$(python3 -c "import sys,json; print(json.load(sys.stdin)['jwt'])" <<< "${API_KEY_RESPONSE}")
log "API key created."

# ---- install Python dependencies -------------------------------------------

log "Installing Python dependencies ..."
pip install --quiet --no-cache-dir numpy shepard-client

# ---- run seed ---------------------------------------------------------------

log "Running seed.py ..."
cd /seed
python seed.py \
  --host "${BACKEND}" \
  --apikey "${SEED_API_KEY}" \
  --regenerate
log "Seed complete."
