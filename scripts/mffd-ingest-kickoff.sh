#!/usr/bin/env bash
# mffd-ingest-kickoff.sh — pre-flight + launch for the 346 GB MFFD ingest.
# Idempotent. Re-running re-checks gates; the v15 state file lets the runner
# resume. See aidocs/agent-findings/mffd-ingest-346gb-readiness-2026-05-31.md §5.
#
# Env (overridable):
#   STAGING_DIR=/mnt/pve/unas/dump/ts-export
#   MFFD_WORKERS=4                # 8 needs PgBouncer pool bump (auto-checked)
#   MFFD_ENV_FILE=examples/mffd-showcase/scripts/.env.local   (NOT committed)
#   MFFD_SESSION_ID=ingest-346gb-YYYYMMDD
#   MFFD_NO_LAUNCH=1              # do pre-flight only
# Exit: 2 archive, 3 manifest, 4 backend, 5 env, 6 disk, 7 pool

set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGING_DIR="${STAGING_DIR:-/mnt/pve/unas/dump/ts-export}"
ENV_FILE="${MFFD_ENV_FILE:-${REPO}/examples/mffd-showcase/scripts/.env.local}"
KEYS_FILE="${MFFD_KEYS_FILE:-/root/.claude/uploads/mffd-ingest-keys-2026-05-31.txt}"
SESSION_ID="${MFFD_SESSION_ID:-ingest-346gb-$(date +%Y%m%d)}"
WORKERS="${MFFD_WORKERS:-4}"
WATCH_LOG="${MFFD_WATCH_LOG:-/tmp/mffd-disk-watch.log}"
RUNNER_LOG="${MFFD_RUNNER_LOG:-/tmp/mffd-ingest-${SESSION_ID}.log}"
log() { echo "[$(date '+%H:%M:%S')] $*"; }
fail() { log "FAIL: $1" >&2; exit "${2:-1}"; }

log "1/6 archive @ ${STAGING_DIR}"
[[ -d "${STAGING_DIR}" ]] || fail "STAGING_DIR not found: ${STAGING_DIR}" 2
[[ -r "${STAGING_DIR}/manifest.json" ]] || fail "manifest.json missing" 3
python3 -c "import json;json.load(open('${STAGING_DIR}/manifest.json'))" || fail "manifest malformed" 3
log "  staged: $(du -sh "${STAGING_DIR}" | awk '{print $1}')"

log "2/6 disk capacity"
HOST_FREE=$(df -BG / | awk 'NR==2{gsub("G","",$4);print $4}')
NFS_FREE=$(df -BG /mnt/pve/unas | awk 'NR==2{gsub("G","",$4);print $4}')
log "  host ${HOST_FREE}G / NFS ${NFS_FREE}G free"
[[ "${HOST_FREE}" -ge 200 ]] || fail "host ZFS <200 G free" 6
[[ "${NFS_FREE}" -ge 400 ]] || fail "NFS <400 G free" 6

log "3/6 backend healthz"
curl -fsS "https://shepard-api.nuclide.systems/shepard/api/healthz/ready" \
  | grep -q '"status": "UP"' || fail "backend not UP" 4

log "4/6 credentials from ${ENV_FILE}"
[[ -r "${ENV_FILE}" ]] || { cat >&2 <<EOF
Missing env file: ${ENV_FILE}
  Required (write to NEW file, do NOT commit):
    SHEPARD_URL=https://shepard-api.nuclide.systems
    SHEPARD_API_KEY=<fresh dest JWT — see ${KEYS_FILE}>
    SOURCE_TAPELAYING_COLL_ID=48297
    SOURCE_BRIDGEWELDING_COLL_ID=163811
    MFFD_DEFAULT_LICENSE=proprietary
    MFFD_DEFAULT_ACCESS_RIGHTS='restricted access'
EOF
fail "env file required" 5; }
# shellcheck disable=SC1090
set -a; source "${ENV_FILE}"; set +a
[[ -n "${SHEPARD_API_KEY:-}" ]] || fail "SHEPARD_API_KEY unset" 5
CODE=$(curl -sS -o /tmp/mffd-me.json -w "%{http_code}" \
  -H "X-API-KEY: ${SHEPARD_API_KEY}" \
  "https://shepard-api.nuclide.systems/v2/users/me")
[[ "${CODE}" == "200" ]] || fail "dest JWT failed /v2/users/me (HTTP ${CODE}); re-mint per ${KEYS_FILE}" 5
log "  auth OK"

log "5/6 PgBouncer pool for ${WORKERS} workers"
POOL=$(docker exec infrastructure-pgbouncer-1 sh -c \
  'awk "/^default_pool_size/{print \$3}" /etc/pgbouncer/pgbouncer.ini' 2>/dev/null || echo 20)
NEEDED=$((WORKERS * 5 + 10))
log "  pool=${POOL}; needed≈${NEEDED}"
if [[ "${POOL}" -lt "${NEEDED}" ]]; then
  cat >&2 <<EOF
PgBouncer default_pool_size=${POOL} < needed ${NEEDED}.  Bump first:
  sed -i 's/DEFAULT_POOL_SIZE: "${POOL}"/DEFAULT_POOL_SIZE: "${NEEDED}"/' \\
    infrastructure/docker-compose.override.yml
  (cd infrastructure && docker compose up -d pgbouncer)
Then re-run this script.
EOF
  fail "pool too small" 7
fi

if [[ -n "${MFFD_NO_LAUNCH:-}" ]]; then
  log "6/6 MFFD_NO_LAUNCH set — pre-flight done, NOT launching"; exit 0
fi

log "6/6 disk-watch + tmux launch"
if ! pgrep -f "mffd-disk-watch-loop" >/dev/null; then
  (exec -a mffd-disk-watch-loop bash -c '
     while sleep 1800; do
       printf "=== %s ===\n" "$(date -Is)" >>"'"${WATCH_LOG}"'"
       df -h / /mnt/pve/unas >>"'"${WATCH_LOG}"'" 2>&1
       du -sh /opt/shepard/timescaledb /opt/shepard/neo4j/data \
         /opt/shepard/mongodb/db \
         /var/lib/docker/volumes/infrastructure_garage_data/_data \
         2>/dev/null >>"'"${WATCH_LOG}"'"
     done') >/dev/null 2>&1 &
  log "  watch → ${WATCH_LOG}"
fi

TMUX="mffd-${SESSION_ID}"
if tmux has-session -t "${TMUX}" 2>/dev/null; then
  log "  tmux ${TMUX} exists — tmux attach -t ${TMUX}"; exit 0
fi
cd "${REPO}/examples/mffd-showcase/scripts"
tmux new-session -d -s "${TMUX}" \
  "set -a; source '${ENV_FILE}'; set +a; \
   DATA_DIR='${STAGING_DIR}' SESSION_ID='${SESSION_ID}' \
   exec ./mffd-runner.sh --workers ${WORKERS} \
     --default-license \"\${MFFD_DEFAULT_LICENSE:-proprietary}\" \
     --default-access-rights \"\${MFFD_DEFAULT_ACCESS_RIGHTS:-restricted access}\" \
     2>&1 | tee '${RUNNER_LOG}'"
log "DONE.  Attach: tmux attach -t ${TMUX}"
log "       Tail:   tail -f ${RUNNER_LOG}"
log "       Disk:   tail -f ${WATCH_LOG}"
log "       Stop:   touch /tmp/mffd-runner.stop"
