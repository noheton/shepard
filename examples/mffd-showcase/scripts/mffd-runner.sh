#!/usr/bin/env bash
# mffd-runner.sh — v15.4 self-update wrapper.
#
# Why: the Python script can replace itself in-place via os.execv when a new
# manifest version lands. The runner-loop catches the case where the script
# exits cleanly (e.g. after a fatal but recoverable error, a JWT-expiry pause
# that timed out, a self-update that needed a fresh interpreter start) and
# restarts it. The Python script's checkpoint file means restart = resume.
#
# Use:
#   ./mffd-runner.sh [args passed through to the python script]
# Stop:
#   touch /tmp/mffd-runner.stop   (loop exits before next restart)
#
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY_SCRIPT="${MFFD_SCRIPT:-${SCRIPT_DIR}/mffd-import-v15.py}"
STOP_FILE="${MFFD_RUNNER_STOP:-/tmp/mffd-runner.stop}"
PYTHON="${MFFD_PYTHON:-python3}"

if [[ ! -f "${PY_SCRIPT}" ]]; then
  echo "[runner] ERROR: ${PY_SCRIPT} not found"
  exit 1
fi

ATTEMPT=0
while true; do
  ATTEMPT=$((ATTEMPT + 1))
  echo "[runner] attempt #${ATTEMPT} → ${PY_SCRIPT} $*"
  "${PYTHON}" "${PY_SCRIPT}" "$@"
  EC=$?
  echo "[runner] script exited with ${EC}"

  if [[ -f "${STOP_FILE}" ]]; then
    echo "[runner] stop file present (${STOP_FILE}) — exiting"
    break
  fi

  case "${EC}" in
    0)
      # Clean exit. If checkpoint says "last_execv_to" matches the new
      # in-place script, restart so the new code runs. Otherwise stop.
      echo "[runner] clean exit — checking if a self-update is pending"
      sleep 2
      ;;
    8)
      # IMPORT-Q7-VERIFY exit code (source-content-empty). Operator must fix
      # the source; loop would be useless.
      echo "[runner] exit 8 = source content empty (Q7-VERIFY). Stopping."
      break
      ;;
    9)
      # v15.5 IMPORT-CFG1 — config / required-arg error. Operator must act
      # (set MFFD_DEFAULT_LICENSE + MFFD_DEFAULT_ACCESS_RIGHTS, or pass
      # --default-license / --default-access-rights). Retrying is useless.
      echo "[runner] exit 9 = config error. Operator must set license + access-rights. Stopping."
      break
      ;;
    2|3|4|5|6|7)
      # Smart-warmup hard-fail codes — environment is wrong; don't retry.
      echo "[runner] exit ${EC} = warmup hard-fail. Stopping for operator."
      break
      ;;
    *)
      # Anything else: probably transient (network, redeploy, OOM). Retry.
      echo "[runner] non-zero exit ${EC} — backoff 30s then retry"
      sleep 30
      ;;
  esac
done

echo "[runner] done"
