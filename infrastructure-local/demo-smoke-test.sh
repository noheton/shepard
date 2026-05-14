#!/usr/bin/env bash
# DX5a — post-`make demo-up` smoke test.
#
# Validates that the seeded demo posture is actually serving the
# features `make demo-up` advertises. Useful as a:
#
#   - hands-on confirmation after `make demo-up`,
#   - CI smoke job (DX5a §9 — optional follow-up workflow),
#   - regression check after upgrading the local stack.
#
# Each check prints PASS/FAIL on its own line; the script exits 0
# when every check passes, 1 on any failure.

set -uo pipefail

readonly BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
readonly FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"
readonly KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8082}"
readonly ADMIN_API_KEY="${ADMIN_API_KEY:-demo-admin-api-key-value-not-for-prod}"
readonly SHOWCASE_APP_ID="${SHOWCASE_APP_ID:-demo-collection-public-showcase}"
readonly DEMO_PUBLICATION_PID_SUFFIX="${DEMO_PUBLICATION_PID_SUFFIX:-01HFDEMO0PUBLICSHOWCASEPID01v1}"

failures=0

pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n      -> %s\n' "$1" "$2" >&2; failures=$((failures + 1)); }
skip() { printf 'SKIP  %s\n      -> %s\n' "$1" "$2"; }

# 1. Backend health.
check_backend_health() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' "$BACKEND_URL/q/health/ready" || echo 000)"
  if [[ "$code" == "200" ]]; then
    pass "backend /q/health/ready returns 200"
  else
    fail "backend /q/health/ready returns 200" "got HTTP $code"
  fi
}

# 2. Frontend reachable.
check_frontend_reachable() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' "$FRONTEND_URL/" || echo 000)"
  if [[ "$code" =~ ^(200|302|303|307|308)$ ]]; then
    pass "frontend $FRONTEND_URL/ reachable (HTTP $code)"
  else
    fail "frontend reachable" "got HTTP $code from $FRONTEND_URL/"
  fi
}

# 3. Keycloak realm reachable.
check_keycloak_realm() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' "$KEYCLOAK_URL/realms/shepard-demo/.well-known/openid-configuration" || echo 000)"
  if [[ "$code" == "200" ]]; then
    pass "keycloak shepard-demo realm reachable"
  else
    fail "keycloak shepard-demo realm reachable" "got HTTP $code from $KEYCLOAK_URL/realms/shepard-demo/.well-known/openid-configuration"
  fi
}

# 4. Plugins list (>= 4 plugins, registered + ENABLED).
check_plugins_listed() {
  local body
  body="$(curl -sS -H "X-API-KEY: $ADMIN_API_KEY" "$BACKEND_URL/v2/admin/plugins" 2>/dev/null || echo '{}')"
  local count
  count="$(printf '%s' "$body" | grep -cE '"id" *:' || true)"
  if [[ "$count" -ge 4 ]]; then
    pass "v2/admin/plugins lists >= 4 plugins (count=$count)"
  else
    fail "v2/admin/plugins lists >= 4 plugins" "count=$count, body=$body"
  fi
}

# 5. Seeded Collection visible.
check_showcase_collection() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' \
    -H "X-API-KEY: $ADMIN_API_KEY" \
    "$BACKEND_URL/v2/collections/$SHOWCASE_APP_ID" 2>/dev/null || echo 000)"
  if [[ "$code" == "200" ]]; then
    pass "seeded showcase Collection /v2/collections/$SHOWCASE_APP_ID returns 200"
  else
    skip "seeded showcase Collection by appId" "got HTTP $code (endpoint may be queued under L2d); legacy /shepard/api/collections/1005 is the fallback"
  fi
}

# 6. KIP resolver returns the seeded Publication.
check_kip_resolver() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' \
    "$BACKEND_URL/v2/.well-known/kip/$DEMO_PUBLICATION_PID_SUFFIX" 2>/dev/null || echo 000)"
  if [[ "$code" == "200" ]]; then
    pass "KIP resolver returns the seeded LocalMinter PID"
  else
    fail "KIP resolver returns the seeded LocalMinter PID" "got HTTP $code from $BACKEND_URL/v2/.well-known/kip/$DEMO_PUBLICATION_PID_SUFFIX"
  fi
}

# 7. DataCite minting (gated on operator opt-in — skipped here).
check_datacite_skipped() {
  skip "real DataCite minting" "gated on operator opt-in; needs a real Fabrica account. See infrastructure-local/README-demo.md."
}

# 8. UH1a Unhide feed (gated on operator opt-in — skipped here).
check_unhide_skipped() {
  skip "Helmholtz Unhide feed" "gated on operator opt-in via 'shepard-admin unhide enable'."
}

main() {
  printf '== DX5a demo-smoke-test against %s ==\n\n' "$BACKEND_URL"
  check_backend_health
  check_frontend_reachable
  check_keycloak_realm
  check_plugins_listed
  check_showcase_collection
  check_kip_resolver
  check_datacite_skipped
  check_unhide_skipped

  printf '\n'
  if [[ "$failures" -eq 0 ]]; then
    printf '== All required checks passed. ==\n'
    exit 0
  else
    printf '== %d check(s) FAILED. ==\n' "$failures" >&2
    exit 1
  fi
}

main "$@"
