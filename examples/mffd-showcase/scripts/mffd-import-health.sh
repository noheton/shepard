#!/bin/bash
# mffd-import-health.sh — cube-side importer-progress watchdog (v1.1)
#
# Detects an importer stall via TWO complementary signals:
#   (1) forward-progress markers in the mffd-import log (count of completed DOs)
#   (2) python PID stability over time (constant PID = stable process;
#       rapidly-cycling PID = warmup-restart loop, the 2026-05-23 failure mode)
#
# Designed for the warmup-restart-loop signature observed 2026-05-23:
# the wrapper kept relaunching python (so a naive `pgrep` always finds
# something), but python exited before any DO completion. Looking at PID
# stability across consecutive runs catches this even before the log
# stall-threshold trips.
#
# Run from cron every 5 min:
#   */5 * * * * /home/cube/mffd-export/mffd-import-health.sh
#
# Or as a systemd timer (preferred — see mffd-import-health.timer alongside).
#
# Environment:
#   MFFD_LOG_PATH               — log file to watch (default: latest in cwd)
#   MFFD_STALL_THRESHOLD        — minutes of zero progress before alerting (default 10)
#   MFFD_PID_RESTART_THRESHOLD  — alert if PID changed >N times in last hour (default 3)
#   MFFD_HEALTH_WEBHOOK         — optional Mattermost/Matrix incoming webhook URL
#   MFFD_HEALTH_EMAIL           — optional email address (uses `mail` if installed)
#   MFFD_HEALTH_STATE           — alert-dedupe state file (default /tmp/mffd-health.state)
#   MFFD_HEALTH_PID_LOG         — PID history log (default /tmp/mffd-health.pidlog)
#
# Exit codes: 0 = OK (no stall), 1 = stall detected, 2 = log not found, 3 = config error.

set -euo pipefail

THRESHOLD_MIN="${MFFD_STALL_THRESHOLD:-10}"
PID_RESTART_THRESHOLD="${MFFD_PID_RESTART_THRESHOLD:-3}"
STATE_FILE="${MFFD_HEALTH_STATE:-/tmp/mffd-health.state}"
PID_LOG="${MFFD_HEALTH_PID_LOG:-/tmp/mffd-health.pidlog}"

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

# v1.1 PID STABILITY TRACKING
# Capture current PID + timestamp, append to PID log, then read recent
# history to compute "PID stability" (how long has current PID been the
# same) and "restart count" (how many distinct PIDs in last hour).
# Constant PID for ≥THRESHOLD_MIN = stable process (good).
# Multiple distinct PIDs in last hour = warmup-restart loop (today's bug).
current_pid=$(pgrep -f mffd-import-v15.py 2>/dev/null | head -1 || echo "0")
echo "${now_epoch} ${current_pid}" >> "$PID_LOG"
# Trim history to last 2 hours (~24 rows at 5-min cadence)
if [ -s "$PID_LOG" ]; then
  cutoff_2h=$((now_epoch - 7200))
  awk -v c="$cutoff_2h" '$1 >= c' "$PID_LOG" > "${PID_LOG}.tmp" && mv "${PID_LOG}.tmp" "$PID_LOG"
fi
# Compute: PID restart count in last hour (distinct non-zero PIDs)
cutoff_1h=$((now_epoch - 3600))
distinct_pids_1h=$(awk -v c="$cutoff_1h" '$1 >= c && $2 != 0 { print $2 }' "$PID_LOG" | sort -u | wc -l)
# Compute: how long has the CURRENT PID been observed (since first sighting)
pid_first_seen_epoch=$(awk -v p="$current_pid" '$2 == p { print $1; exit }' "$PID_LOG" 2>/dev/null || echo "$now_epoch")
pid_stable_min=$(( (now_epoch - pid_first_seen_epoch) / 60 ))

# Decision:
#   - log_silent_min > THRESHOLD_MIN AND recent_done == 0  → STALL (silent log)
#   - log_silent_min <= THRESHOLD_MIN AND recent_done == 0 → STALL (no completions)
#   - distinct_pids_1h > PID_RESTART_THRESHOLD              → STALL (restart loop)
#   - Otherwise → OK (also report pid_stable_min for visibility)
stall_reason=""
if [ "$recent_done" -lt 1 ]; then
  if [ "$log_silent_min" -gt "$THRESHOLD_MIN" ]; then
    stall_reason="log silent for ${log_silent_min}min (>${THRESHOLD_MIN}min threshold); pid=${current_pid}, stable for ${pid_stable_min}min"
  else
    stall_reason="wrapper alive but 0 DOs completed in last ~${THRESHOLD_MIN}min (warmup-restart loop?); pid=${current_pid}, stable for ${pid_stable_min}min"
  fi
elif [ "$distinct_pids_1h" -gt "$PID_RESTART_THRESHOLD" ]; then
  stall_reason="PID restart loop: ${distinct_pids_1h} distinct PIDs in last 1h (>${PID_RESTART_THRESHOLD} threshold); current pid=${current_pid}, stable for ${pid_stable_min}min"
fi

if [ -z "$stall_reason" ]; then
  echo "[health] OK: ${recent_done} DOs done in tail window, log fresh (${log_silent_min}min); pid=${current_pid} stable for ${pid_stable_min}min, ${distinct_pids_1h} distinct PIDs in last 1h"
  # Clear the state file so we re-alert on the next stall
  : > "$STATE_FILE"
  exit 0
fi

# Stall detected — gather diagnostic context (PID context already in stall_reason)
last_lines=$(tail -3 "$LOG" 2>/dev/null | sed 's/"/\\"/g' | tr '\n' '|' || echo "n/a")
host=$(hostname -s 2>/dev/null || echo "unknown")
session=$(grep -oE 'session=[^[:space:]]+' "$LOG" 2>/dev/null | tail -1 || echo "session=unknown")

msg="MFFD-IMPORT STALL on ${host}: ${stall_reason}, ${session}. Last log lines: ${last_lines}"
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
