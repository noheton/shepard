#!/usr/bin/env bash
# DX5a — wait for the backend's readiness probe to go green before
# the seeder runs (or before `make demo-status` queries the admin
# REST surface).
#
# Polls http://localhost:8080/q/health/ready every 3s, up to 30
# attempts (~90s wall-clock). Exits 0 on first 200 response,
# non-zero with a clear message after the timeout.

set -euo pipefail

readonly BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
readonly HEALTH_PATH="${HEALTH_PATH:-/q/health/ready}"
readonly MAX_ATTEMPTS="${MAX_ATTEMPTS:-30}"
readonly SLEEP_SECONDS="${SLEEP_SECONDS:-3}"

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  if curl -fsS "$BACKEND_URL$HEALTH_PATH" >/dev/null 2>&1; then
    printf '==> Backend is up after %ds (attempt %d/%d).\n' \
      "$((attempt * SLEEP_SECONDS))" "$attempt" "$MAX_ATTEMPTS"
    exit 0
  fi
  sleep "$SLEEP_SECONDS"
done

printf '!! Backend %s%s did not respond within %ds.\n' \
  "$BACKEND_URL" "$HEALTH_PATH" "$((MAX_ATTEMPTS * SLEEP_SECONDS))" >&2
printf '!! Check "make demo-logs" for the backend container logs.\n' >&2
exit 1
