#!/bin/bash
# mffd-import-health.sh — cube-side importer-progress watchdog (v1.0)
#
# Detects an importer stall by reading the local mffd-import log file and
# counting forward-progress markers in a recent time window. If the count
# drops below threshold, emit an alert.
#
# Designed for the warmup-restart-loop failure mode observed 2026-05-23:
# the wrapper keeps relaunching python (so a naive `pgrep` always finds
# something), but python exits before any DO completion. Counting actual
# "done" markers in the log is the only reliable signal.
#
# Run from cron every 5 min:
#   */5 * * * * /home/cube/mffd-export/mffd-import-health.sh
#
# Or as a systemd timer (preferred — see mffd-import-health.timer alongside).
#
# Environment:
#   MFFD_LOG_PATH         — log file to watch (default: latest in cwd)
#   MFFD_STALL_THRESHOLD  — minutes of zero progress before alerting (default 10)
#   MFFD_HEALTH_WEBHOOK   — optional Mattermost/Matrix incoming webhook URL
#   MFFD_HEALTH_EMAIL     — optional email address (uses `mail` if installed)
#   MFFD_HEALTH_STATE     — state file to dedupe alerts (default /tmp/mffd-health.state)
#
# Exit codes: 0 = OK (no stall), 1 = stall detected, 2 = log not found, 3 = config error.

set -euo pipefail

THRESHOLD_MIN="${MFFD_STALL_THRESHOLD:-10}"
STATE_FILE="${MFFD_HEALTH_STATE:-/tmp/mffd-health.state}"

# Locate the log file — explicit override or newest mffd-import-*.log in cwd
if [ -n "${MFFD_LOG_PATH:-}" ]; then
  LOG="$MFFD_LOG_PATH"
else
  LOG=$(ls -t mffd-import-*.log 2>/dev/null | head -1 || true)
fi
if [ -z "$LOG" ] || [ ! -r "$LOG" ]; then
  echo "[health] ERROR: log file not found (MFFD_LOG_PATH=${MFFD_LOG_PATH:-unset}, cwd=$(pwd))" >&2
  exit 2
fi

now_epoch=$(date +%s)
cutoff_epoch=$((now_epoch - THRESHOLD_MIN * 60))

# Count forward-progress markers in the last THRESHOLD_MIN minutes by
# checking the file's modification time + counting "done —" lines in the
# tail. The legacy v15 log uses "↳ Execution ... (done — Nf Mts Ksd)"
# as the per-DO completion marker; v15.11+ also emits structured
# `{"kind":"do_done"}` JSON lines.
log_mtime=$(stat -c %Y "$LOG" 2>/dev/null || stat -f %m "$LOG" 2>/dev/null)
if [ -z "$log_mtime" ]; then
  echo "[health] ERROR: cannot stat $LOG" >&2
  exit 3
fi

# Tail enough lines to span >>THRESHOLD_MIN minutes of activity. With even
# slow throughput (~2 DO/min), 2000 lines is ~15 min of activity at the
# legacy log's verbosity. Bump if your log writes faster.
recent_done=$(tail -2000 "$LOG" | grep -cE '(done —|"kind":"do_done")' || true)

# If the log itself hasn't been written to in THRESHOLD_MIN, the wrapper
# may be silent (between relaunches with no warmup output yet). Treat that
# as a stall regardless of tail-grep count.
log_silent_min=$(( (now_epoch - log_mtime) / 60 ))

# Decision:
#   - If log_silent_min > THRESHOLD_MIN AND recent_done == 0  → STALL
#   - If log_silent_min <= THRESHOLD_MIN AND recent_done == 0 → also STALL
#     (wrapper is writing but python isn't completing DOs)
#   - Otherwise → OK
stall_reason=""
if [ "$recent_done" -lt 1 ]; then
  if [ "$log_silent_min" -gt "$THRESHOLD_MIN" ]; then
    stall_reason="log silent for ${log_silent_min}min (>${THRESHOLD_MIN}min threshold)"
  else
    stall_reason="wrapper alive but 0 DOs completed in last ~${THRESHOLD_MIN}min (warmup-restart loop?)"
  fi
fi

if [ -z "$stall_reason" ]; then
  echo "[health] OK: ${recent_done} DOs done in last tail window, log fresh (${log_silent_min}min)"
  # Clear the state file so we re-alert on the next stall
  : > "$STATE_FILE"
  exit 0
fi

# Stall detected — gather diagnostic context
pid=$(pgrep -f mffd-import-v15.py 2>/dev/null | head -1 || echo "none")
last_lines=$(tail -3 "$LOG" 2>/dev/null | sed 's/"/\\"/g' | tr '\n' '|' || echo "n/a")
host=$(hostname -s 2>/dev/null || echo "unknown")
session=$(grep -oE 'session=[^[:space:]]+' "$LOG" 2>/dev/null | tail -1 || echo "session=unknown")

msg="MFFD-IMPORT STALL on ${host}: ${stall_reason}. pid=${pid}, ${session}. Last log lines: ${last_lines}"
echo "[health] STALL: $msg" >&2

# Dedupe: only alert once per stall episode (state file holds the stall_reason
# that triggered the previous alert; new alert only if reason changed OR
# state file is older than 1h)
state_age_h=$(( $(( now_epoch - $(stat -c %Y "$STATE_FILE" 2>/dev/null || echo 0) )) / 3600 ))
last_reason=""
if [ -r "$STATE_FILE" ] && [ -s "$STATE_FILE" ]; then
  last_reason=$(cat "$STATE_FILE")
fi
if [ "$last_reason" = "$stall_reason" ] && [ "$state_age_h" -lt 1 ]; then
  echo "[health] (suppressed duplicate alert; same reason as ${state_age_h}h ago)"
  exit 1
fi

# Emit alert via configured channels
if [ -n "${MFFD_HEALTH_WEBHOOK:-}" ]; then
  curl -fsSL -X POST "$MFFD_HEALTH_WEBHOOK" \
    -H 'Content-Type: application/json' \
    --max-time 10 \
    -d "$(printf '{"text":"%s"}' "$msg")" >/dev/null 2>&1 \
    && echo "[health] alert sent to webhook" \
    || echo "[health] WARN: webhook delivery failed (network? bad URL?)" >&2
fi

if [ -n "${MFFD_HEALTH_EMAIL:-}" ] && command -v mail >/dev/null 2>&1; then
  printf '%s\n' "$msg" | mail -s "MFFD-IMPORT STALL on ${host}" "$MFFD_HEALTH_EMAIL" \
    && echo "[health] alert emailed to $MFFD_HEALTH_EMAIL" \
    || echo "[health] WARN: email delivery failed" >&2
fi

# Update state for dedupe
echo "$stall_reason" > "$STATE_FILE"
exit 1
