#!/usr/bin/env bash
#
# test-trace-feature.sh — timing assertion for scripts/trace-feature.sh.
#
# Implements GH-PM-ENH-API-11: verifies that trace-feature.sh completes
# in under 5 seconds wall-clock for a known-shipped feature (FS1b).
#
# Usage:
#   scripts/test-trace-feature.sh
#
# Exit status:
#   0   PASS — completed within the 5s limit
#   1   FAIL — exceeded the 5s limit (or trace-feature.sh itself errored)

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="${REPO_ROOT}/scripts/trace-feature.sh"

if [ ! -x "${SCRIPT}" ]; then
  echo "FAIL: ${SCRIPT} not found or not executable"
  exit 1
fi

START=$(date +%s%N)
"${SCRIPT}" FS1b > /dev/null 2>&1
END=$(date +%s%N)

ELAPSED_MS=$(( (END - START) / 1000000 ))
ELAPSED_S_INT=$(( ELAPSED_MS / 1000 ))
ELAPSED_S_DEC=$(( (ELAPSED_MS % 1000) / 10 ))
ELAPSED_S=$(printf '%d.%02d' "${ELAPSED_S_INT}" "${ELAPSED_S_DEC}")

if [ "${ELAPSED_MS}" -lt 5000 ]; then
  echo "PASS: trace-feature.sh completed in ${ELAPSED_S}s (< 5s limit)"
  exit 0
else
  echo "FAIL: trace-feature.sh took ${ELAPSED_S}s (exceeded 5s limit)"
  exit 1
fi
