#!/usr/bin/env bash
# DX5a — demo-seeder entrypoint. Runs inside a `neo4j:5.24` container
# (which ships `cypher-shell` + `curl`) and:
#
#   1. Waits for the backend's bootstrap token file to appear (the
#      backend writes it on first start when no instance-admin exists
#      yet; see BootstrapTokenInitializer).
#   2. Reads the token + consumes it via POST /v2/admin/bootstrap to
#      promote the `admin` Keycloak user to instance-admin.
#   3. Mints an admin API key with the fixed name `demo-admin-api-key`
#      via a Cypher MERGE (the REST mint path needs an OIDC bearer
#      we can't forge from a shell script). The key value is fixed
#      at `demo-admin-api-key-value-not-for-prod` so the smoke test
#      + `make demo-status` work deterministically.
#   4. Drives `cypher-shell` to apply every `cypher/*.cypher` file in
#      filename order; each file is idempotent (MERGE + COALESCE)
#      so re-running the seeder is a no-op.
#
# Idempotency is the explicit contract: `make demo-seed` re-runs this
# script, and the second invocation finds existing rows + skips them.
#
# Exits 0 on success, non-zero on any per-step failure (the Makefile
# surfaces the failure; operators check `make demo-logs` for context).

set -euo pipefail

readonly SEED_DIR="${SEED_DIR:-/demo-seed}"
readonly NEO4J_HOST="${NEO4J_HOST:-neo4j}"
readonly NEO4J_USER="${NEO4J_USER:-neo4j}"
readonly NEO4J_PASSWORD="${NEO4J_PASSWORD:-demo-neo4j-password}"
readonly BACKEND_HOST="${BACKEND_HOST:-backend:8080}"
readonly BOOTSTRAP_TOKEN_PATH="${BOOTSTRAP_TOKEN_PATH:-/tmp/shepard-bootstrap/.bootstrap-token}"
readonly DEMO_ADMIN_USER="${DEMO_ADMIN_USER:-admin}"
readonly DEMO_ADMIN_API_KEY_NAME="${DEMO_ADMIN_API_KEY_NAME:-demo-admin-api-key}"
readonly ADMIN_KEY_OUT="/tmp/shepard-bootstrap/.demo-admin-api-key"

log() {
  echo "[demo-seeder] $*"
}

err() {
  echo "[demo-seeder] ERROR: $*" >&2
}

wait_for_neo4j() {
  log "Waiting for Neo4j (cypher-shell against $NEO4J_HOST:7687)..."
  local attempt
  for attempt in $(seq 1 60); do
    if cypher-shell \
      -a "bolt://$NEO4J_HOST:7687" \
      -u "$NEO4J_USER" \
      -p "$NEO4J_PASSWORD" \
      --format plain \
      "RETURN 1 AS ok;" >/dev/null 2>&1; then
      log "Neo4j is up after ${attempt}s."
      return 0
    fi
    sleep 1
  done
  err "Neo4j didn't respond within 60s."
  return 1
}

wait_for_backend() {
  log "Waiting for backend ($BACKEND_HOST/q/health/ready)..."
  local attempt
  for attempt in $(seq 1 90); do
    if curl -fsS "http://$BACKEND_HOST/q/health/ready" >/dev/null 2>&1; then
      log "Backend is up after ${attempt}s."
      return 0
    fi
    sleep 1
  done
  err "Backend didn't respond within 90s. Check 'make demo-logs'."
  return 1
}

wait_for_bootstrap_token() {
  log "Waiting for bootstrap token at $BOOTSTRAP_TOKEN_PATH..."
  local attempt
  for attempt in $(seq 1 60); do
    if [[ -s "$BOOTSTRAP_TOKEN_PATH" ]]; then
      log "Bootstrap token found after ${attempt}s."
      return 0
    fi
    sleep 1
  done
  # No bootstrap token => either an instance-admin already exists
  # (idempotent re-run) or the token was already consumed. Either
  # way, the seeder proceeds to the Cypher-load step.
  log "No bootstrap token after 60s — assuming idempotent re-run."
  return 0
}

consume_bootstrap_if_present() {
  if [[ ! -s "$BOOTSTRAP_TOKEN_PATH" ]]; then
    log "Skipping bootstrap (no token file)."
    return 0
  fi
  local token
  token="$(tr -d '[:space:]' < "$BOOTSTRAP_TOKEN_PATH")"
  log "Consuming bootstrap token to promote '$DEMO_ADMIN_USER' to instance-admin..."
  local status
  status="$(curl -sS -o /tmp/bootstrap.out -w '%{http_code}' \
    -X POST "http://$BACKEND_HOST/v2/admin/bootstrap" \
    -H 'Content-Type: application/json' \
    -d "{\"token\":\"$token\",\"username\":\"$DEMO_ADMIN_USER\"}" || true)"
  if [[ "$status" =~ ^2 ]]; then
    log "Bootstrap consumed (HTTP $status)."
  elif [[ "$status" == "409" ]]; then
    log "Bootstrap already consumed (HTTP 409) — moving on."
  else
    err "Bootstrap POST failed (HTTP $status). Body: $(cat /tmp/bootstrap.out 2>/dev/null || true)"
    return 1
  fi
  return 0
}

mint_admin_api_key() {
  if [[ -s "$ADMIN_KEY_OUT" ]]; then
    log "Admin API key already stashed at $ADMIN_KEY_OUT — skipping mint."
    return 0
  fi
  # Seed a deterministic admin API key directly via Cypher. The
  # backend's JWTFilter accepts an ApiKey's `jws` claim as a
  # legitimate bearer; minting via the REST API requires an OIDC
  # token we can't easily forge in a shell context.
  #
  # The key value is fixed at `demo-admin-api-key-value-not-for-prod`
  # so `make demo-status` + `shepard-admin --api-key ...` are
  # deterministic. The :ApiKey row carries roles={instance-admin}
  # so the key bypasses the OIDC role-mapping path.
  log "Minting demo admin ApiKey via Cypher (deterministic value)..."
  cypher-shell \
    -a "bolt://$NEO4J_HOST:7687" \
    -u "$NEO4J_USER" \
    -p "$NEO4J_PASSWORD" \
    --format plain <<'CYPHER'
// Deterministic-value demo ApiKey. The jws field is the bearer
// token operators copy; the demo backend accepts it as
// instance-admin via the roles set. Idempotent: MERGE on name.
MERGE (u:User {username: 'admin'})
  ON CREATE SET u.appId = randomUUID(),
                u.firstName = 'Demo',
                u.lastName = 'Admin',
                u.email = 'admin@demo.shepard.local';
MERGE (r:Role {name: 'instance-admin'})
  ON CREATE SET r.appId = randomUUID();
MERGE (u)-[:HAS_ROLE]->(r);
MERGE (k:ApiKey {name: 'demo-admin-api-key'})
  ON CREATE SET k.uid = randomUUID(),
                k.appId = randomUUID(),
                k.createdAt = timestamp(),
                k.validUntil = timestamp() + (1000 * 60 * 60 * 24 * 365),
                k.jws = 'demo-admin-api-key-value-not-for-prod',
                k.roles = ['instance-admin']
  ON MATCH SET  k.jws = coalesce(k.jws, 'demo-admin-api-key-value-not-for-prod'),
                k.roles = ['instance-admin'];
MERGE (k)-[:BELONGS_TO]->(u);
CYPHER
  echo -n 'demo-admin-api-key-value-not-for-prod' > "$ADMIN_KEY_OUT"
  chmod 0644 "$ADMIN_KEY_OUT" 2>/dev/null || true
  log "Admin API key stashed at $ADMIN_KEY_OUT."
}

apply_cypher_seeds() {
  log "Applying Cypher seed files from $SEED_DIR/cypher/..."
  local file applied=0
  shopt -s nullglob
  for file in "$SEED_DIR"/cypher/*.cypher; do
    log "  -> $(basename "$file")"
    if cypher-shell \
      -a "bolt://$NEO4J_HOST:7687" \
      -u "$NEO4J_USER" \
      -p "$NEO4J_PASSWORD" \
      --format plain \
      --file "$file"; then
      applied=$((applied + 1))
    else
      err "Failed to apply $(basename "$file"). Aborting."
      return 1
    fi
  done
  log "Applied $applied Cypher file(s)."
}

main() {
  log "DX5a demo-seeder starting (idempotent)."
  wait_for_neo4j
  wait_for_backend
  wait_for_bootstrap_token
  consume_bootstrap_if_present
  mint_admin_api_key
  apply_cypher_seeds
  log "Seeding complete. Demo shepard is ready at http://localhost:3000/."
}

main "$@"
