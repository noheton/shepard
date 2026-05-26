#!/usr/bin/env bash
# upgrade-from-v5.sh — Interactive upgrade wizard for upgrading from
# upstream DLR Shepard v5.2.0 to this fork's main branch.
#
# Usage:
#   ./scripts/upgrade-from-v5.sh           # dry-run (default, safe — prints actions only)
#   ./scripts/upgrade-from-v5.sh --apply   # execute each step
#   ./scripts/upgrade-from-v5.sh --help
#
# Flags:
#   --apply       Actually perform destructive steps (pull images, run migrations, restart)
#   --no-prompt   Skip interactive y/N prompts for breaking changes (assumes yes in --apply,
#                 continues in dry-run)
#   --log-dir DIR Write upgrade-YYYYMMDD-HHMMSS.log to DIR (default: current directory)
#   --help        Show this message and exit
#
# All output is tee'd to an upgrade log.  The log is written even in dry-run mode.
#
# Rollback: every destructive step prints explicit rollback instructions.
# Neo4j rollback Cypher files live in
#   backend/src/main/resources/neo4j/migrations/V*_R__*.cypher
# SQL rollback files live in
#   backend/src/main/resources/db/migration/V*_R__*.sql
#
# Reference: aidocs/34-upstream-upgrade-path.md — the authoritative admin-facing
# change ledger for every addition on top of upstream v5.2.0.
# ===========================================================================

set -uo pipefail

# ---------------------------------------------------------------------------
# Globals
# ---------------------------------------------------------------------------
APPLY=0
NO_PROMPT=0
LOG_DIR="$(pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

WARNINGS=0   # non-fatal issues found during pre-flight
ERRORS=0     # fatal issues found during pre-flight

COMPOSE_CMD=""          # resolved below
LOG_FILE=""             # set after arg parse

# Colour helpers (disabled if not a terminal)
if [[ -t 1 ]]; then
  RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'
  CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
else
  RED=''; YELLOW=''; GREEN=''; CYAN=''; BOLD=''; RESET=''
fi

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
ok()      { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; (( WARNINGS++ )) || true; }
error()   { echo -e "${RED}[ERROR]${RESET} $*"; (( ERRORS++ )) || true; }
section() { echo -e "\n${BOLD}━━━  $*  ━━━${RESET}"; }
hr()      { echo -e "${BOLD}───────────────────────────────────────────────────────────────${RESET}"; }

# run CMD [ARGS…]
#   In --apply mode: execute the command.
#   In dry-run mode: print what would happen, do nothing.
run() {
  if [[ $APPLY -eq 1 ]]; then
    "$@"
  else
    echo -e "${YELLOW}[DRY-RUN]${RESET} Would run: $*"
  fi
}

# run_quiet CMD [ARGS…]  — same as run() but suppresses stdout on success
run_quiet() {
  if [[ $APPLY -eq 1 ]]; then
    "$@" >/dev/null
  else
    echo -e "${YELLOW}[DRY-RUN]${RESET} Would run: $*"
  fi
}

# ask_confirm DESCRIPTION
#   In --apply mode: prompt y/N.  Returns 0 (apply) or 1 (skip).
#   In dry-run mode: always returns 0 (just informational).
#   With --no-prompt: always returns 0 in apply, 0 in dry-run.
ask_confirm() {
  local desc="$1"
  if [[ $APPLY -eq 0 ]]; then
    echo -e "${YELLOW}[DRY-RUN]${RESET} Would prompt: $desc [y/N]"
    return 0
  fi
  if [[ $NO_PROMPT -eq 1 ]]; then
    info "Auto-confirming (--no-prompt): $desc"
    return 0
  fi
  echo -e "${YELLOW}?${RESET}  $desc"
  read -r -p "    Apply this step? [y/N] " reply
  case "$reply" in
    [yY]*) return 0 ;;
    *)     info "Skipping step."; return 1 ;;
  esac
}

# rollback_hint TEXT — always print (both modes)
rollback_hint() {
  echo -e "${YELLOW}  ↩  Rollback:${RESET} $*"
}

usage() {
  grep '^#' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' | head -20
  exit 0
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --apply)      APPLY=1 ;;
      --no-prompt)  NO_PROMPT=1 ;;
      --log-dir)    shift; LOG_DIR="$1" ;;
      --help|-h)    usage ;;
      *) echo "Unknown flag: $1"; usage ;;
    esac
    shift
  done
}

# ---------------------------------------------------------------------------
# Logging setup
# ---------------------------------------------------------------------------
setup_logging() {
  mkdir -p "$LOG_DIR"
  LOG_FILE="${LOG_DIR}/upgrade-$(date +%Y%m%d-%H%M%S).log"
  # Tee all subsequent output to the log file
  exec > >(tee -a "$LOG_FILE") 2>&1
  info "Log file: ${LOG_FILE}"
}

# ---------------------------------------------------------------------------
# Banner
# ---------------------------------------------------------------------------
banner() {
  section "Shepard v5.2.0 → fork/main upgrade wizard"
  if [[ $APPLY -eq 0 ]]; then
    echo -e "${YELLOW}  Mode: DRY-RUN  (pass --apply to execute steps)${RESET}"
  else
    echo -e "${RED}  Mode: APPLY  — this will pull images and restart services${RESET}"
  fi
  echo -e "  Repo root : ${REPO_ROOT}"
  echo -e "  Log       : ${LOG_FILE}"
  echo ""
  echo "  Reference : aidocs/34-upstream-upgrade-path.md"
  echo "  Date      : $(date -u '+%Y-%m-%d %H:%M UTC')"
  hr
}

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
preflight_docker() {
  section "Pre-flight: Docker"

  # Probe docker compose v2 plugin first, then legacy docker-compose binary
  if docker compose version &>/dev/null; then
    COMPOSE_CMD="docker compose"
    ok "docker compose (v2 plugin) found: $(docker compose version --short 2>/dev/null || true)"
  elif command -v docker-compose &>/dev/null; then
    COMPOSE_CMD="docker-compose"
    warn "Using legacy docker-compose binary. Consider upgrading to Docker Compose v2."
    ok "docker-compose found: $(docker-compose --version 2>/dev/null || true)"
  else
    error "Neither 'docker compose' (v2 plugin) nor 'docker-compose' binary found. Install Docker Compose."
  fi

  if ! docker info &>/dev/null; then
    error "Docker daemon is not running or current user lacks access. Run: sudo systemctl start docker"
  else
    ok "Docker daemon reachable."
  fi
}

preflight_version() {
  section "Pre-flight: Current instance version"

  local health_url="${SHEPARD_URL:-http://localhost:80}/shepard/api/health/live"
  local version_url="${SHEPARD_URL:-http://localhost:80}/shepard/api/v2/admin/version"

  info "Probing health endpoint: ${health_url}"
  if curl -sf --max-time 5 "$health_url" &>/dev/null; then
    ok "Instance is up."
    local ver
    ver=$(curl -sf --max-time 5 "$version_url" 2>/dev/null | \
          grep -oE '"version"[[:space:]]*:[[:space:]]*"[^"]*"' | \
          grep -oE '"[^"]*"$' | tr -d '"' 2>/dev/null || true)
    if [[ -n "$ver" ]]; then
      info "Reported version: ${ver}"
      if [[ "$ver" == 5.* ]]; then
        ok "Confirmed: running upstream v5.x — upgrade applicable."
      elif [[ "$ver" == 6.* ]]; then
        warn "Instance reports v6.x — may already be on the fork. Review whether re-running is needed."
      else
        warn "Unexpected version string '${ver}'. Proceed with caution."
      fi
    else
      warn "Could not parse version from ${version_url}. Proceeding without version confirmation."
    fi
  else
    warn "Instance not reachable at ${health_url}. Is it running? Continuing pre-flight..."
  fi
}

preflight_env_vars() {
  section "Pre-flight: Required environment variables"

  local compose_env="${REPO_ROOT}/infrastructure/.env"
  if [[ -f "$compose_env" ]]; then
    # shellcheck source=/dev/null
    set -a; source "$compose_env"; set +a
    ok "Loaded ${compose_env}"
  else
    warn ".env file not found at ${compose_env}. Checking shell environment only."
  fi

  # Core variables (always required)
  local required_vars=(
    NEO4J_PW
    MONGO_ROOT_USERNAME MONGO_ROOT_PASSWORD MONGO_USERNAME MONGO_PASSWORD MONGO_DATABASE
    POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD POSTGRES_SHEPARD_USER POSTGRES_SHEPARD_USER_PW
    OIDC_PUBLIC OIDC_AUTHORITY OIDC_ROLE
    BACKEND_URL FRONTEND_URL CLIENT_ID FRONTEND_AUTH_SECRET
  )

  local missing=0
  for var in "${required_vars[@]}"; do
    if [[ -z "${!var:-}" ]]; then
      error "Required var not set: ${var}"
      (( missing++ )) || true
    fi
  done
  [[ $missing -eq 0 ]] && ok "All required env vars present."

  # New vars introduced by the fork — warn if absent (they have defaults or are optional)
  section "Pre-flight: Fork-introduced env vars (new since v5.2.0)"

  local new_vars=(
    "SHEPARD_BOOTSTRAP_TOKEN_PATH:Optional. Path for first-start bootstrap token. Default: /home/default/.shepard/.bootstrap-token"
    "SHEPARD_SECRETS_ENCRYPTION__KEY:REQUIRED for G1 git-integration. Generate: openssl rand -base64 32"
    "GRAFANA_ADMIN_USERNAME:Required if using --profile monitoring"
    "GRAFANA_ADMIN_PASSWORD:Required if using --profile monitoring"
    "HSDS_BUCKET_NAME:Required if using --profile hdf (HDF5/HSDS sidecar)"
    "HSDS_USERNAME:Required if using --profile hdf"
    "HSDS_PASSWORD:Required if using --profile hdf"
    "GARAGE_RPC_SECRET:Required if using --profile files-s3 (Garage S3). Generate: openssl rand -hex 32"
    "SESSION_REFRESH_INTERVAL:Optional frontend session refresh. Default: 60"
  )

  for entry in "${new_vars[@]}"; do
    local var="${entry%%:*}"
    local desc="${entry#*:}"
    if [[ -z "${!var:-}" ]]; then
      warn "New fork var not set: ${BOLD}${var}${RESET} — ${desc}"
    else
      ok "${var} is set."
    fi
  done
}

preflight_backup() {
  section "Pre-flight: Backup recommendation"

  echo ""
  echo -e "${YELLOW}  ┌─────────────────────────────────────────────────────────────┐${RESET}"
  echo -e "${YELLOW}  │  STRONGLY RECOMMENDED: back up all data stores before        │${RESET}"
  echo -e "${YELLOW}  │  applying this upgrade.                                       │${RESET}"
  echo -e "${YELLOW}  │                                                               │${RESET}"
  echo -e "${YELLOW}  │  Neo4j:      /opt/shepard/neo4j/data/                        │${RESET}"
  echo -e "${YELLOW}  │  MongoDB:    /opt/shepard/mongodb/db/                        │${RESET}"
  echo -e "${YELLOW}  │  TimescaleDB:/opt/shepard/timescaledb/                       │${RESET}"
  echo -e "${YELLOW}  │  PostGIS:    /opt/shepard/postgis/db/ (if spatial profile)   │${RESET}"
  echo -e "${YELLOW}  │                                                               │${RESET}"
  echo -e "${YELLOW}  │  Quick snapshot: tar -czf shepard-backup-\$(date +%Y%m%d).tgz │${RESET}"
  echo -e "${YELLOW}  │    /opt/shepard/neo4j/data /opt/shepard/mongodb/db           │${RESET}"
  echo -e "${YELLOW}  │    /opt/shepard/timescaledb                                   │${RESET}"
  echo -e "${YELLOW}  └─────────────────────────────────────────────────────────────┘${RESET}"
  echo ""

  if [[ $APPLY -eq 1 && $NO_PROMPT -eq 0 ]]; then
    read -r -p "  Have you backed up your data stores? [y/N] " reply
    case "$reply" in
      [yY]*) ok "Backup confirmed." ;;
      *) error "Upgrade aborted — please back up your data before proceeding."; exit 1 ;;
    esac
  fi
}

preflight() {
  preflight_docker
  preflight_version
  preflight_env_vars
  preflight_backup

  section "Pre-flight summary"
  echo -e "  Warnings : ${YELLOW}${WARNINGS}${RESET}"
  echo -e "  Errors   : ${RED}${ERRORS}${RESET}"

  if [[ $ERRORS -gt 0 && $APPLY -eq 1 ]]; then
    error "Pre-flight failed with ${ERRORS} error(s). Fix the issues above and re-run."
    exit 1
  elif [[ $ERRORS -gt 0 ]]; then
    warn "Pre-flight found ${ERRORS} error(s) — shown for awareness (dry-run continues)."
  fi
}

# ---------------------------------------------------------------------------
# Compose diff summary
# ---------------------------------------------------------------------------
show_compose_diff() {
  section "Compose file changes since v5.2.0"

  echo ""
  echo "  The following new compose services / profiles were added in this fork:"
  echo ""
  echo -e "  ${BOLD}prometheus${RESET}   (profile: monitoring)"
  echo  "    Prometheus metrics scraper with remote-write receiver."
  echo  "    Start: docker compose --profile monitoring up -d"
  echo  "    Env:   (none required beyond --profile)"
  echo ""
  echo -e "  ${BOLD}grafana${RESET}      (profile: monitoring)"
  echo  "    Grafana dashboard at http://localhost:3001"
  echo  "    Env:   GRAFANA_ADMIN_USERNAME, GRAFANA_ADMIN_PASSWORD"
  echo ""
  echo -e "  ${BOLD}shepard-hsds${RESET} (profile: hdf)"
  echo  "    HSDS HDF5 service sidecar (POSIX backend by default)."
  echo  "    Start: docker compose --profile hdf up -d"
  echo  "    Env:   HSDS_BUCKET_NAME, HSDS_USERNAME, HSDS_PASSWORD, HSDS_LOG_LEVEL"
  echo  "    Data:  ./hsds-storage/ (relative to infrastructure/)"
  echo ""
  echo -e "  ${BOLD}shepard-garage${RESET} (profile: files-s3)"
  echo  "    Garage S3-compatible object store (replaces or supplements GridFS)."
  echo  "    Start: docker compose --profile files-s3 up -d"
  echo  "    Env:   GARAGE_RPC_SECRET (generate: openssl rand -hex 32)"
  echo  "    Init (first run):"
  echo  "      docker exec shepard-garage /garage layout assign -z dc1 -c 1G <node-id>"
  echo  "      docker exec shepard-garage /garage layout apply --version 1"
  echo  "      docker exec shepard-garage /garage bucket create shepard"
  echo  "      docker exec shepard-garage /garage key create shepard-backend"
  echo  "      docker exec shepard-garage /garage bucket allow --read --write shepard --key shepard-backend"
  echo  "    Data:  ./garage-data/ (relative to infrastructure/)"
  echo ""
  echo -e "  ${BOLD}neo4j image${RESET}"
  echo  "    Upgraded from neo4j:4.4.x to neo4j:5.26."
  echo  "    NOTE: 4→5 migration requires the offline migration tool — see MR !315."
  echo  "    Within 5.x the store migration is automatic."
  echo  "    NEO4J_PLUGINS: [\"n10s\"] added for the internal SemanticRepository."
  echo ""
  echo -e "  ${BOLD}mongodb image${RESET}"
  echo  "    Upgraded from mongo:6.x to mongo:8.0.4 (incremental — follow MR !306)."
  echo ""
  echo -e "  ${BOLD}timescaledb image${RESET}"
  echo  "    Now at timescale/timescaledb:2.24.0-pg16."
  echo ""
  echo -e "  ${BOLD}grafana image${RESET}"
  echo  "    grafana/grafana:12.2.1-security-01 (monitoring profile)"
  echo ""
  echo -e "  ${BOLD}prometheus image${RESET}"
  echo  "    prom/prometheus:v3.9.1 (monitoring profile)"
  echo ""
  hr
}

# ---------------------------------------------------------------------------
# New application.properties / env-var config keys
# ---------------------------------------------------------------------------
show_new_config_keys() {
  section "New configuration keys since v5.2.0"

  echo ""
  echo "  These keys are set in application.properties or as env vars (env wins)."
  echo "  All have safe defaults — only set them if you need non-default behaviour."
  echo ""

  cat <<'CONFIG'
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │ KEY                                       │ DEFAULT       │ NOTES             │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.bootstrap.token-path              │ ~/.shepard/   │ A0: first-admin   │
  │                                           │ .bootstrap-   │ bootstrap token.  │
  │                                           │ token         │ Printed on first  │
  │                                           │               │ start.            │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.secrets.encryption-key            │ (none)        │ G1-cred: REQUIRED │
  │                                           │               │ if git-integration│
  │                                           │               │ used. Generate:   │
  │                                           │               │ openssl rand -b64 │
  │                                           │               │ 32                │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.permissions.default-owner         │ (none)        │ C3: set to a valid│
  │                                           │               │ username if orphan│
  │                                           │               │ permissions exist │
  │                                           │               │ after V14 cypher  │
  │                                           │               │ migration.        │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.infrastructure.spatial.*          │               │ A3c: replaces old │
  │   (renamed from shepard.spatial-data.*)   │               │ prefix.           │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.publish.minter                    │ local         │ KIP1h: valid       │
  │                                           │               │ values: local,     │
  │                                           │               │ handle, datacite.  │
  │                                           │               │ 'mock' removed.   │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.plugins.unhide.enabled            │ false         │ Default flipped   │
  │                                           │               │ to off. Set =true │
  │                                           │               │ to keep Unhide    │
  │                                           │               │ publishing.       │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.plugins.compatibility.strict      │ true          │ V6: third-party   │
  │                                           │               │ plugins with      │
  │                                           │               │ compat range      │
  │                                           │               │ >=5.2.0,<6 won't  │
  │                                           │               │ load. Set =false  │
  │                                           │               │ to bypass (not    │
  │                                           │               │ recommended).     │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.hdf.hsds.url                      │ (none)        │ A5a: HSDS sidecar │
  │ shepard.hdf.hsds.username                 │               │ URL + creds for   │
  │ shepard.hdf.hsds.password                 │               │ HDF5 plugin.      │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.storage.provider                  │ gridfs        │ FS1d: set =s3 to  │
  │ shepard.storage.s3.endpoint               │               │ use Garage/S3.    │
  │ shepard.storage.s3.bucket                 │               │                   │
  │ shepard.storage.s3.access-key             │               │                   │
  │ shepard.storage.s3.secret-key             │               │                   │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.ai.provider                       │ (none)        │ AI1c: LLM         │
  │ shepard.ai.base-url                       │               │ integration.      │
  │ shepard.ai.api-key                        │               │                   │
  ├──────────────────────────────────────────────────────────────────────────────┤
  │ shepard.unhide.harvest.api-key            │ (none)        │ UH1a: Helmholtz   │
  │ shepard.unhide.feed.base-url              │               │ Unhide harvest.   │
  └──────────────────────────────────────────────────────────────────────────────┘
CONFIG
  hr
}

# ---------------------------------------------------------------------------
# List Neo4j migrations (runtime enumeration)
# ---------------------------------------------------------------------------
list_cypher_migrations() {
  section "Neo4j migrations (applied automatically on startup)"

  local migrations_dir="${REPO_ROOT}/backend/src/main/resources/neo4j/migrations"
  if [[ ! -d "$migrations_dir" ]]; then
    warn "Neo4j migrations directory not found: ${migrations_dir}"
    return
  fi

  echo ""
  echo "  Directory: ${migrations_dir}"
  echo ""
  echo "  Neo4j-Migrations (MigrationsRunner) applies all V*.cypher files in"
  echo "  order on startup. The backend will fail to start if any migration fails."
  echo "  Rollback files (V*_R__*.cypher) must be run manually via cypher-shell."
  echo ""
  echo "  Forward migrations:"
  local count=0
  while IFS= read -r f; do
    local name
    name="$(basename "$f")"
    # Skip rollback files and NOOP files from listing
    if [[ "$name" =~ _R__ ]]; then continue; fi
    echo "    ${name}"
    (( count++ )) || true
  done < <(find "$migrations_dir" -name 'V*.cypher' | sort)
  ok "  ${count} forward Cypher migrations found."

  echo ""
  echo "  Rollback files (manual, via cypher-shell):"
  while IFS= read -r f; do
    echo "    $(basename "$f")"
  done < <(find "$migrations_dir" -name 'V*_R__*.cypher' | sort)
  echo ""
  rollback_hint "cypher-shell -u neo4j -p \"\${NEO4J_PW}\" < <rollback-file.cypher>"
  hr
}

# ---------------------------------------------------------------------------
# List SQL migrations (runtime enumeration)
# ---------------------------------------------------------------------------
list_sql_migrations() {
  section "SQL / Flyway migrations (applied automatically on startup)"

  local migrations_dir="${REPO_ROOT}/backend/src/main/resources/db/migration"
  if [[ ! -d "$migrations_dir" ]]; then
    warn "SQL migrations directory not found: ${migrations_dir}"
    return
  fi

  echo ""
  echo "  Directory: ${migrations_dir}"
  echo ""
  echo "  Flyway applies all V*.sql files in order on startup."
  echo ""
  echo "  Forward migrations:"
  local count=0
  while IFS= read -r f; do
    local name
    name="$(basename "$f")"
    if [[ "$name" =~ _R__ ]]; then continue; fi
    echo "    ${name}"
    (( count++ )) || true
  done < <(find "$migrations_dir" -name 'V*.sql' | sort)
  ok "  ${count} forward SQL migrations found."

  echo ""
  echo "  Rollback files (manual, via psql):"
  while IFS= read -r f; do
    echo "    $(basename "$f")"
  done < <(find "$migrations_dir" -name 'V*_R__*.sql' | sort)
  echo ""
  rollback_hint "psql -h timescaledb -U \"\${POSTGRES_SHEPARD_USER}\" -d \"\${POSTGRES_DB}\" -f <rollback-file.sql>"
  hr
}

# ---------------------------------------------------------------------------
# Breaking changes — interactive prompts
# ---------------------------------------------------------------------------
show_breaking_changes() {
  section "Breaking changes requiring operator action"

  echo ""
  echo "  The items below are the BREAKING changes since v5.2.0."
  echo "  In --apply mode you will be prompted for each.  In dry-run mode"
  echo "  each item is shown with the action you would need to take."
  echo ""

  # ----- BREAKING 1: G1-cred — encryption key for git PAT storage -----
  hr
  echo -e "  ${RED}[BREAKING] G1-cred — Git integration encryption key${RESET}"
  echo ""
  echo "  Git credentials (PATs) are now stored encrypted in Neo4j."
  echo "  A 256-bit encryption key must be set BEFORE first startup on the fork,"
  echo "  otherwise the backend will refuse to start if any git credentials exist."
  echo ""
  echo "  Action: add to application.properties or as env var:"
  echo "    shepard.secrets.encryption-key=<base64-32-bytes>"
  echo ""
  echo "  Generate:"
  echo "    openssl rand -base64 32"
  echo ""
  if ask_confirm "G1-cred: confirm you have set shepard.secrets.encryption-key"; then
    ok "G1-cred: encryption key confirmed."
  fi
  rollback_hint "Remove the key (and any :GitCredential nodes) to roll back to a state without git integration."

  # ----- BREAKING 2: C3 — orphan permissions default-owner -----
  hr
  echo -e "  ${RED}[BREAKING] C3 — Orphan permissions: default owner${RESET}"
  echo ""
  echo "  V14 Cypher migration backfills orphaned permission nodes with a default owner."
  echo "  If your database has permissions not linked to any user (edge case, e.g. from"
  echo "  bulk imports), set:"
  echo "    shepard.permissions.default-owner=<username>"
  echo "  Otherwise the migration assigns them to a placeholder account."
  echo ""
  echo "  Migration file: V14__Backfill_orphan_permissions.cypher"
  echo "  Rollback file:  V14_R__Rollback_Backfill_orphan_permissions.cypher"
  echo ""
  if ask_confirm "C3: confirm you have reviewed orphan permissions (or have none)"; then
    ok "C3: orphan permission handling confirmed."
  fi
  rollback_hint "Run V14_R__Rollback_Backfill_orphan_permissions.cypher via cypher-shell to undo."

  # ----- BREAKING 3: A0 — bootstrap token first-start -----
  hr
  echo -e "  ${RED}[BREAKING] A0 — Bootstrap token (first-admin mechanism)${RESET}"
  echo ""
  echo "  On first start of the fork, the backend writes a one-time bootstrap token"
  echo "  to: /opt/shepard/backend/config/.bootstrap-token  (default path)"
  echo "  Use it via:  POST /v2/auth/bootstrap  {\"token\": \"<value>\"}"
  echo "  to grant the first instance-admin role."
  echo ""
  echo "  If upgrading from a running v5 instance with admins already configured,"
  echo "  this bootstrap token is a no-op — existing roles are preserved."
  echo "  The token path can be changed via:"
  echo "    SHEPARD_BOOTSTRAP_TOKEN_PATH (env) or shepard.bootstrap.token-path"
  echo ""
  if ask_confirm "A0: confirm you understand the bootstrap token mechanism"; then
    ok "A0: bootstrap token mechanism acknowledged."
  fi
  rollback_hint "The bootstrap token file can simply be deleted — it has no persistent effect once used."

  # ----- BREAKING 4: A3c — spatial-data config prefix rename -----
  hr
  echo -e "  ${RED}[BREAKING] A3c — Config prefix rename: spatial-data → infrastructure.spatial${RESET}"
  echo ""
  echo "  If you previously set any keys under 'shepard.spatial-data.*', rename them:"
  echo "    Old: shepard.spatial-data.enabled"
  echo "    New: shepard.infrastructure.spatial.enabled"
  echo ""
  echo "  Also: the SHEPARD_SPATIAL_DATA_ENABLED env var is still accepted but the"
  echo "  canonical internal key has changed. Update your .env or application.properties."
  echo ""
  if ask_confirm "A3c: confirm you have renamed shepard.spatial-data.* keys (or never used them)"; then
    ok "A3c: spatial config prefix confirmed."
  fi
  rollback_hint "Revert the key rename in application.properties and re-deploy the old image."

  # ----- BREAKING 5: KIP1h — minter=mock removed -----
  hr
  echo -e "  ${RED}[BREAKING] KIP1h — shepard.publish.minter: 'mock' value removed${RESET}"
  echo ""
  echo "  The 'mock' minter (a no-op stub used in older test setups) has been replaced"
  echo "  by 'local'. If you have:"
  echo "    shepard.publish.minter=mock"
  echo "  in your application.properties, change it to:"
  echo "    shepard.publish.minter=local"
  echo ""
  echo "  Valid values: local | handle | datacite"
  echo ""
  if ask_confirm "KIP1h: confirm you have changed minter=mock to minter=local (or never set it)"; then
    ok "KIP1h: minter config confirmed."
  fi
  rollback_hint "Revert to 'mock' is not possible on the fork — use 'local' as the no-op equivalent."

  # ----- BREAKING 6: Plugin unhide default flip -----
  hr
  echo -e "  ${RED}[BREAKING] Plugin hardening — Unhide plugin default changed to disabled${RESET}"
  echo ""
  echo "  The Helmholtz Unhide publishing plugin now defaults to DISABLED."
  echo "  If you were relying on it being auto-enabled, add:"
  echo "    shepard.plugins.unhide.enabled=true"
  echo "  to your application.properties."
  echo ""
  echo "  This is a security hardening: no outbound publishing happens by default."
  echo ""
  if ask_confirm "Plugin/Unhide: confirm you have set enabled=true if you use Unhide publishing"; then
    ok "Plugin/Unhide: Unhide default confirmed."
  fi
  rollback_hint "Remove 'shepard.plugins.unhide.enabled=true' and the plugin will not publish."

  # ----- BREAKING 7: V6 version bump — third-party plugin compat -----
  hr
  echo -e "  ${RED}[BREAKING] V6 — Plugin compatibility range: v5 plugins will not load${RESET}"
  echo ""
  echo "  All bundled plugins have been updated to compat range >=6.0.0-SNAPSHOT,<7."
  echo "  Any third-party plugin jar built against >=5.2.0,<6 will be REJECTED by the"
  echo "  PM1b2 semver enforcement gate."
  echo ""
  echo "  Action options:"
  echo "    a) Rebuild third-party plugins against the fork's plugin API (recommended)."
  echo "    b) As a temporary escape hatch, set:"
  echo "         shepard.plugins.compatibility.strict=false"
  echo "       (not recommended for production)."
  echo ""
  echo "  Check your plugins directory:"
  echo "    ls /opt/shepard/backend/config/plugins/ 2>/dev/null || echo '(empty — no third-party plugins)'"
  echo ""
  run ls /opt/shepard/backend/config/plugins/ 2>/dev/null || \
    echo "    (directory not found or empty — no third-party plugins detected)"
  echo ""
  if ask_confirm "V6: confirm you have handled third-party plugin compat (or have none)"; then
    ok "V6: plugin compat range confirmed."
  fi
  rollback_hint "Roll back to the v5.2.0 image to restore v5-compat plugin loading."

  hr
  echo ""
  ok "All breaking change items reviewed."
}

# ---------------------------------------------------------------------------
# Apply phase — image pull + restart
# ---------------------------------------------------------------------------
apply_phase() {
  section "Apply: Pull images and restart services"

  local compose_dir="${REPO_ROOT}/infrastructure"
  if [[ ! -f "${compose_dir}/docker-compose.yml" ]]; then
    warn "docker-compose.yml not found at ${compose_dir}. Skipping compose operations."
    return
  fi

  # Determine active profiles from environment
  local profiles=""
  [[ -n "${COMPOSE_PROFILES:-}" ]] && profiles="--profile ${COMPOSE_PROFILES// / --profile }"

  if ask_confirm "Pull latest images for all services?"; then
    run $COMPOSE_CMD -f "${compose_dir}/docker-compose.yml" $profiles pull
    rollback_hint "docker tag <old-image>:<old-tag> <service>; docker compose up -d --no-pull"
  fi

  if ask_confirm "Restart services (docker compose up -d)?"; then
    run $COMPOSE_CMD -f "${compose_dir}/docker-compose.yml" $profiles up -d
    rollback_hint "docker compose -f ${compose_dir}/docker-compose.yml up -d  (with old image tags)"
  fi

  if ask_confirm "Tail logs for 60 seconds to watch startup?"; then
    if [[ $APPLY -eq 1 ]]; then
      timeout 60 $COMPOSE_CMD -f "${compose_dir}/docker-compose.yml" logs -f backend || true
    else
      echo -e "${YELLOW}[DRY-RUN]${RESET} Would run: ${COMPOSE_CMD} logs -f backend  (for 60s)"
    fi
  fi
}

# ---------------------------------------------------------------------------
# Post-upgrade summary
# ---------------------------------------------------------------------------
post_summary() {
  section "Post-upgrade checklist"

  echo ""
  echo "  After the backend starts, verify:"
  echo ""
  echo "  1. Health check passes:"
  echo "       curl -sf http://localhost/shepard/api/health/live"
  echo ""
  echo "  2. Check backend startup logs for migration errors:"
  echo "       ${COMPOSE_CMD:-docker compose} logs backend 2>&1 | grep -i 'migration\\|error\\|WARN'"
  echo ""
  echo "  3. Smoke test the v2 API:"
  echo "       curl -sf http://localhost/v2/collections | head -5"
  echo ""
  echo "  4. Bootstrap token (if first start on fork):"
  echo "       cat /opt/shepard/backend/config/.bootstrap-token"
  echo "       curl -X POST http://localhost/v2/auth/bootstrap \\"
  echo "            -H 'Content-Type: application/json' \\"
  echo "            -d '{\"token\":\"<value-from-file>\"}'"
  echo ""
  echo "  5. Verify Neo4j migrations applied:"
  echo "       cypher-shell -u neo4j -p \"\${NEO4J_PW:-<password>}\" \\"
  echo "         'MATCH (m:__Neo4jMigration) RETURN m.version, m.description ORDER BY m.version'"
  echo ""
  echo "  6. Verify Flyway migrations applied:"
  echo "       docker compose exec timescaledb psql -U \"\${POSTGRES_SHEPARD_USER:-shepard}\" \\"
  echo "         -d \"\${POSTGRES_DB:-shepard}\" -c 'SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;'"
  echo ""
  echo "  7. Check the upgrade log for any warnings:"
  echo "       cat ${LOG_FILE}"
  echo ""
  hr
  ok "Upgrade wizard complete."
  echo -e "  Full log written to: ${BOLD}${LOG_FILE}${RESET}"
  echo ""
  echo "  Reference docs:"
  echo "    aidocs/34-upstream-upgrade-path.md — full change ledger"
  echo "    aidocs/44-fork-vs-upstream-feature-matrix.md — feature matrix"
  hr
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  parse_args "$@"
  setup_logging
  banner
  preflight
  show_compose_diff
  show_new_config_keys
  list_cypher_migrations
  list_sql_migrations
  show_breaking_changes
  apply_phase
  post_summary
}

main "$@"
