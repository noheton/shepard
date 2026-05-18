#!/bin/bash
# home-showcase entrypoint — mints a fresh admin API key against
# Keycloak, then dispatches to either the seeder (one-shot) or the
# collector (long-lived) based on the $HOME_SHOWCASE_MODE env var
# (values: "seeder" | "collector").
#
# Mirrors examples/lumen-showcase/entrypoint.sh's auth flow so neither
# the seeder nor the collector services need a pre-baked SHEPARD_API_KEY
# in compose.
set -euo pipefail

BACKEND="${BACKEND_URL:-http://backend:8080/shepard/api}"
BACKEND_ROOT="${BACKEND%/shepard/api}"
KC="${KC_URL:-http://keycloak:8082}"
REALM="${KC_REALM:-shepard-demo}"
CLIENT_ID="${KC_CLIENT_ID:-frontend-dev}"
ADMIN_USER="${KC_ADMIN_USER:-admin}"
ADMIN_PASS="${KC_ADMIN_PASS:-admin-demo}"
MODE="${HOME_SHOWCASE_MODE:-seeder}"

log() { echo "[home-showcase:$MODE] $*"; }

# ---- wait for deps --------------------------------------------------------

log "Waiting for Keycloak at ${KC}/realms/${REALM}/.well-known/openid-configuration ..."
until curl -sf "${KC}/realms/${REALM}/.well-known/openid-configuration" > /dev/null 2>&1; do
  sleep 4
done
log "Keycloak ready."

log "Waiting for backend readiness at ${BACKEND_ROOT}/shepard/api/healthz/ready ..."
until curl -sf "${BACKEND_ROOT}/shepard/api/healthz/ready" > /dev/null 2>&1; do
  sleep 5
done
log "Backend ready."

# ---- mint admin token + api key ------------------------------------------

log "Obtaining Keycloak access token for ${ADMIN_USER} ..."
KC_RESPONSE=$(curl -sf -X POST \
  "${KC}/realms/${REALM}/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_ID}" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "scope=openid")
ACCESS_TOKEN=$(python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" <<< "${KC_RESPONSE}")

ADMIN_SUB=$(python3 -c "
import sys, json, base64
t = sys.argv[1].split('.')
pad = lambda s: s + '=' * (-len(s) % 4)
print(json.loads(base64.urlsafe_b64decode(pad(t[1])))['sub'])
" "${ACCESS_TOKEN}")
log "Resolved admin sub = ${ADMIN_SUB}"

# Touch the user so Shepard's UserFilter mints the :User node if missing.
curl -s -o /dev/null "${BACKEND}/users/${ADMIN_SUB}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" || true

log "Creating short-lived API key ..."
API_KEY_RESPONSE=$(curl -sf -X POST \
  "${BACKEND}/users/${ADMIN_SUB}/apikeys" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"home-showcase-runtime"}')
API_KEY=$(python3 -c "import sys,json; print(json.load(sys.stdin)['jwt'])" <<< "${API_KEY_RESPONSE}")
log "API key minted."

export SHEPARD_API_KEY="${API_KEY}"
export SHEPARD_API="${BACKEND}"

# ---- dispatch -------------------------------------------------------------

case "${MODE}" in
  seeder)
    log "Installing shepard-client + running seed.py ..."
    pip install --quiet --no-cache-dir --extra-index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple shepard-client
    exec python3 /home-showcase/seed.py --host "${BACKEND}" --apikey "${API_KEY}"
    ;;
  collector)
    # The compose service is no longer profile-gated, so this entrypoint
    # runs on every deploy. Operators without an MQTT broker shouldn't
    # see a crashlooping container — exit clean (0) with a single log
    # line when MQTT_HOST is empty/missing.
    if [ -z "${MQTT_HOST:-}" ]; then
      log "MQTT_HOST is empty/unset — collector has nothing to subscribe to. Exiting clean (operator can set MQTT_HOST=… in infrastructure/.env to enable the live-data path)."
      exit 0
    fi
    log "Installing shepard-client + paho-mqtt + running collector.py ..."
    pip install --quiet --no-cache-dir --extra-index-url https://gitlab.com/api/v4/projects/59082852/packages/pypi/simple shepard-client paho-mqtt
    exec python3 /home-showcase/collector.py
    ;;
  *)
    log "Unknown HOME_SHOWCASE_MODE='${MODE}' (expected 'seeder' or 'collector')"
    exit 1
    ;;
esac
