#!/bin/bash
# Post-deployment smoke test for shepard.
#
# Catches the regressions we've actually hit on this fork:
#   - Frontend SSR crash from missing vue/index.mjs (skipped patch-output.mjs)
#   - Backend missing REST classes (stale image; endpoints return 404 instead of 401)
#   - Backend startup config crashes (Quarkus 3.27 empty-default required props)
#   - Flyway duplicate-version migration crashes
#
# Usage:
#   ./smoke-test.sh                     # tests localhost
#   FRONTEND_URL=https://shepard.nuclide.systems \
#   BACKEND_URL=https://shepard-api.nuclide.systems \
#     ./smoke-test.sh                   # tests prod
#
# Exit codes:
#   0 — all checks passed
#   1 — at least one check failed (run again with VERBOSE=1 to see body)

set -u

# Defaults: hit the docker-compose mapped ports on the host.
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
VERBOSE="${VERBOSE:-0}"

# Resolve the frontend container directly if localhost:3000 isn't published
# (override.yml omits the ports: mapping in some deploys, since Zoraxy proxies it).
if ! curl -sf -o /dev/null --max-time 3 "$FRONTEND_URL/" 2>/dev/null; then
  ip="$(docker inspect infrastructure-frontend-1 --format \
    '{{range $k, $v := .NetworkSettings.Networks}}{{$v.IPAddress}}{{end}}' 2>/dev/null)"
  if [ -n "$ip" ]; then
    FRONTEND_URL="http://$ip:3000"
    echo "[smoke] frontend not on $FRONTEND_URL, using container IP: $FRONTEND_URL"
  fi
fi

PASS=0
FAIL=0
FAILED=()

# Image-freshness check: if both the locally-built image tag and the running
# container's image exist, complain when they don't match. This catches the
# "ran docker compose from the wrong cwd, deploy was a no-op, smoke still
# passed because previous container is fine" footgun.
check_image_freshness() {
  local container="$1"
  local local_tag="$2"
  local label="$3"
  local local_id running_id
  local_id="$(docker inspect "$local_tag" --format '{{.Id}}' 2>/dev/null)"
  running_id="$(docker inspect "$container" --format '{{.Image}}' 2>/dev/null)"
  if [ -z "$local_id" ] || [ -z "$running_id" ]; then
    return  # one or both not present locally — skip silently
  fi
  if [ "$local_id" = "$running_id" ]; then
    echo "  ✓ $label image matches local build"
    PASS=$((PASS+1))
  else
    echo "  ✗ $label image DRIFT — running $running_id but local tag $local_tag is $local_id"
    echo "      The last 'docker compose up' likely didn't actually redeploy this service."
    echo "      Re-run it from /opt/shepard/infrastructure/."
    FAIL=$((FAIL+1))
    FAILED+=("$label image freshness")
  fi
}

check() {
  local name="$1"
  local url="$2"
  local expected_status="$3"   # comma-separated list e.g. "200" or "401,403"
  local extra_assert="${4:-}"  # optional grep pattern that must match body
  local method="${5:-GET}"
  local body
  local status

  body="$(curl -s -X "$method" -o /tmp/smoke-body.$$ -w '%{http_code}' --max-time 10 "$url" 2>/dev/null)"
  status="$body"

  local status_ok=0
  IFS=',' read -ra expected_arr <<< "$expected_status"
  for e in "${expected_arr[@]}"; do
    if [ "$status" = "$e" ]; then status_ok=1; break; fi
  done

  local body_ok=1
  if [ -n "$extra_assert" ]; then
    if ! grep -q "$extra_assert" /tmp/smoke-body.$$ 2>/dev/null; then
      body_ok=0
    fi
  fi

  if [ "$status_ok" = "1" ] && [ "$body_ok" = "1" ]; then
    echo "  ✓ $name — HTTP $status"
    PASS=$((PASS+1))
  else
    echo "  ✗ $name — HTTP $status (expected $expected_status${extra_assert:+, body must match: $extra_assert})"
    if [ "$VERBOSE" = "1" ]; then
      echo "      url: $url"
      echo "      body: $(head -c 500 /tmp/smoke-body.$$ 2>/dev/null)"
    fi
    FAIL=$((FAIL+1))
    FAILED+=("$name")
  fi
  rm -f /tmp/smoke-body.$$
}

echo "[smoke] frontend: $FRONTEND_URL"
echo "[smoke] backend:  $BACKEND_URL"
echo ""

echo "Deploy freshness"
check_image_freshness infrastructure-frontend-1 shepard-frontend:local         frontend
check_image_freshness infrastructure-backend-1  shepard-backend-patched:local  backend
echo ""

echo "Frontend"
# 200 = SSR rendered OK (this catches the missing-vue-index.mjs case)
check "GET /"            "$FRONTEND_URL/"           "200"
# Login page either renders (200) or 302s into the OIDC redirect chain — both fine.
check "GET /login"       "$FRONTEND_URL/login"      "200,302"

echo ""
echo "Backend health"
check "GET /shepard/api/healthz/ready" \
      "$BACKEND_URL/shepard/api/healthz/ready" \
      "200" \
      '"status": "UP"'

# Swagger UI is served by Quarkus under non-application-root-path (/shepard/doc).
# The frontend's "API Docs" link points here — catch broken links to the wrong path.
check "GET /shepard/doc/swagger-ui/ (frontend's API Docs link target)" \
      "$BACKEND_URL/shepard/doc/swagger-ui/" \
      "200"
check "GET /shepard/doc/openapi.json (OpenAPI spec)" \
      "$BACKEND_URL/shepard/doc/openapi.json" \
      "200"

echo ""
echo "Backend wire compatibility (upstream API frozen at /shepard/api/)"
# These endpoints exist in upstream 5.2.0; unauthenticated should be 401/403, NEVER 404.
# A 404 here means the REST class wasn't compiled into the image (the regression we hit
# when the patched image was stale and lacked MePreferencesRest etc.).
check "GET /shepard/api/collections (no auth → 401)" \
      "$BACKEND_URL/shepard/api/collections" \
      "401,403"

echo ""
echo "Backend /v2/ surface (this fork's additive endpoints)"
# 401 means the endpoint exists and demands auth — good.
# 404 means the REST class is missing from the JAR — bad (regression).
check "GET /v2/users/me/preferences (no auth → 401)" \
      "$BACKEND_URL/v2/users/me/preferences" \
      "401,403"

# /v2/timeseries-containers/{id}/linked-data-objects — CC1b
# Need any container id; the ID doesn't have to exist (the auth filter runs first).
check "GET /v2/timeseries-containers/1/linked-data-objects (no auth → 401)" \
      "$BACKEND_URL/v2/timeseries-containers/1/linked-data-objects" \
      "401,403"

# /v2/admin/features — A3b
check "GET /v2/admin/features (no auth → 401)" \
      "$BACKEND_URL/v2/admin/features" \
      "401,403"

# DI1 — safe delete endpoints exist (DELETE without auth → 401, not 404)
check "DELETE /v2/timeseries-containers/1 (no auth → 401)" \
      "$BACKEND_URL/v2/timeseries-containers/1" \
      "401,403" "" DELETE
check "DELETE /v2/file-containers/1 (no auth → 401)" \
      "$BACKEND_URL/v2/file-containers/1" \
      "401,403" "" DELETE
check "DELETE /v2/structured-data-containers/1 (no auth → 401)" \
      "$BACKEND_URL/v2/structured-data-containers/1" \
      "401,403" "" DELETE

# SA-CONT — container-level semantic annotation endpoints exist
check "GET /v2/timeseries-containers/1/annotations (no auth → 401)" \
      "$BACKEND_URL/v2/timeseries-containers/1/annotations" \
      "401,403"
check "GET /v2/file-containers/1/annotations (no auth → 401)" \
      "$BACKEND_URL/v2/file-containers/1/annotations" \
      "401,403"
check "GET /v2/structured-data-containers/1/annotations (no auth → 401)" \
      "$BACKEND_URL/v2/structured-data-containers/1/annotations" \
      "401,403"

# INST1 — public instance identity (ROR-based)
check "GET /v2/instance/identity (no auth → 401)" \
      "$BACKEND_URL/v2/instance/identity" \
      "401,403"

echo ""
echo "──────────────────────────────────────────────"
echo "PASS: $PASS    FAIL: $FAIL"
if [ "$FAIL" -gt 0 ]; then
  echo "Failed checks:"
  for f in "${FAILED[@]}"; do echo "  - $f"; done
  echo ""
  echo "Re-run with VERBOSE=1 to see response bodies."
  exit 1
fi
echo "All checks passed."
exit 0
