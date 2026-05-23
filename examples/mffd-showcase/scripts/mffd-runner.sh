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

# v15.16 RUNNER-UV — prefer `uv run --script` (reads the PEP 723 header in the
# script and creates an isolated venv with declared deps, zero global pollution).
# Falls back to bare python3 if uv isn't on PATH. Operators can force a specific
# interpreter via MFFD_PYTHON.
if [[ -n "${MFFD_PYTHON:-}" ]]; then
  RUN_CMD=("${MFFD_PYTHON}")                                          # honor explicit operator override
elif command -v uv >/dev/null 2>&1; then
  RUN_CMD=(uv run --script)                                           # PEP 723 self-contained
  echo "[runner] using: uv run --script (PEP 723 isolated venv)"
else
  RUN_CMD=(python3)                                                   # legacy fallback
  echo "[runner] WARNING: uv not on PATH; falling back to python3 — ensure 'requests' + 'tqdm' are installed."
  echo "[runner]          install uv: curl -LsSf https://astral.sh/uv/install.sh | sh"
fi

if [[ ! -f "${PY_SCRIPT}" ]]; then
  echo "[runner] ERROR: ${PY_SCRIPT} not found"
  exit 1
fi

ATTEMPT=0
# v15.7 RUNNER-CLD — crash-loop detection. If the script restarts >5 times in
# 60 seconds with non-clean exit, the runner stops instead of spinning. Saves
# you from hammering the dest API on a real config error that exit-code 1
# couldn't classify.
CRASH_WINDOW_S=60
CRASH_MAX=5
CRASH_TIMES=()
crash_loop_check() {
  local now=$(/usr/bin/date +%s)
  local cutoff=$((now - CRASH_WINDOW_S))
  local kept=()
  for t in "${CRASH_TIMES[@]}"; do
    if [[ "$t" -ge "$cutoff" ]]; then kept+=("$t"); fi
  done
  CRASH_TIMES=("${kept[@]}")
  CRASH_TIMES+=("$now")
  if [[ "${#CRASH_TIMES[@]}" -ge "$CRASH_MAX" ]]; then
    echo "[runner] CRASH-LOOP detected: ${#CRASH_TIMES[@]} restarts in ${CRASH_WINDOW_S}s"
    echo "[runner] Stopping. Check the log + fix root cause. Re-run when ready."
    return 1
  fi
  return 0
}

while true; do
  ATTEMPT=$((ATTEMPT + 1))
  echo "[runner] attempt #${ATTEMPT} → ${RUN_CMD[*]} ${PY_SCRIPT} $*"
  "${RUN_CMD[@]}" "${PY_SCRIPT}" "$@"
  EC=$?
  echo "[runner] script exited with ${EC}"

  if [[ -f "${STOP_FILE}" ]]; then
    echo "[runner] stop file present (${STOP_FILE}) — exiting"
    break
  fi

  case "${EC}" in
    0)
      # Clean exit. Only re-loop if a self-update is genuinely pending — i.e.
      # the checkpoint records a `last_execv_from`/`last_execv_to` pair AND
      # `last_execv_to` matches a NEWER version than what we just ran. Otherwise
      # treat exit 0 as "import completed" and STOP — preventing the
      # clean-exit-restart loop that thrashes the lock file every 2s.
      STATE_FILE="${SCRIPT_DIR}/mffd-import-$(date +%Y-%m-%d).state.json"
      if [[ -f "${STATE_FILE}" ]] && grep -q 'last_execv_to.*[0-9]' "${STATE_FILE}" 2>/dev/null; then
        # Read last_execv_to and the current IMPORT_SCRIPT_VERSION in the script.
        EXECV_TO=$(python3 -c "import json,sys; d=json.load(open('${STATE_FILE}')); print(d.get('last_execv_to') or '')" 2>/dev/null || echo '')
        CURR_VERSION=$(grep '^IMPORT_SCRIPT_VERSION' "${PY_SCRIPT}" | head -1 | awk -F'"' '{print $2}')
        if [[ -n "${EXECV_TO}" && "${EXECV_TO}" != "${CURR_VERSION}" ]]; then
          echo "[runner] clean exit + self-update pending (current=${CURR_VERSION} → pending=${EXECV_TO}); restarting in 2s"
          sleep 2
          continue
        fi
      fi
      echo "[runner] clean exit — import completed (no pending self-update); stopping the loop"
      echo "[runner] (re-run ./mffd-runner.sh manually if you want another pass)"
      break
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
      # Anything else: probably transient (network, redeploy, OOM). Retry —
      # unless the crash-loop detector says we've been here too often.
      if ! crash_loop_check; then break; fi
      echo "[runner] non-zero exit ${EC} — backoff 30s then retry"
      sleep 30
      ;;
  esac
done

echo "[runner] done"
