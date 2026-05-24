#!/usr/bin/env bash
# setup-worktree.sh — prepare an agent worktree for `make redeploy`.
#
# Background: agent-dispatched worktrees (via `isolation: "worktree"`) do not
# inherit `infrastructure/.env`, which `make redeploy` consumes via
# docker-compose. The canonical env lives at `/opt/shepard/infrastructure/.env`
# and is symlinked into the worktree so a fresh agent can build + redeploy
# without manually copying secrets around.
#
# Other per-worktree state (Maven `.m2`, npm cache, Playwright browsers) is
# already shared via global caches; the .env is the only missing piece today.
#
# Usage:
#   scripts/setup-worktree.sh        # idempotent; run from worktree root
#
# Idempotent: if the symlink already exists and points at the canonical env,
# the script no-ops with exit 0. If a non-symlink env file exists (e.g. a
# pre-existing copy from a previous agent), the script aborts rather than
# clobbering it.
#
# Surfaced by OPS-WORKTREE-ENV (2026-05-24).

set -euo pipefail

CANONICAL_ENV="/opt/shepard/infrastructure/.env"
WORKTREE_ROOT="$(git rev-parse --show-toplevel)"
TARGET_ENV="${WORKTREE_ROOT}/infrastructure/.env"

if [[ ! -f "${CANONICAL_ENV}" ]]; then
  echo "ERROR: canonical env file missing at ${CANONICAL_ENV}" >&2
  echo "       Cannot bootstrap worktree. Create the canonical env first." >&2
  exit 2
fi

if [[ ! -d "${WORKTREE_ROOT}/infrastructure" ]]; then
  echo "ERROR: ${WORKTREE_ROOT}/infrastructure not found." >&2
  echo "       Are you running this from a shepard worktree root?" >&2
  exit 2
fi

# If the worktree IS the canonical checkout, nothing to do — the env file is
# already at its canonical location.
if [[ "${WORKTREE_ROOT}" == "/opt/shepard" ]]; then
  echo "setup-worktree.sh: this IS the canonical checkout (/opt/shepard) — nothing to do."
  exit 0
fi

if [[ -L "${TARGET_ENV}" ]]; then
  existing_target="$(readlink "${TARGET_ENV}")"
  if [[ "${existing_target}" == "${CANONICAL_ENV}" ]]; then
    echo "setup-worktree.sh: ${TARGET_ENV} already symlinked to canonical env — OK."
    exit 0
  fi
  echo "ERROR: ${TARGET_ENV} is a symlink to ${existing_target}, not the canonical env." >&2
  echo "       Remove it manually if you want to re-bootstrap." >&2
  exit 1
fi

if [[ -e "${TARGET_ENV}" ]]; then
  echo "ERROR: ${TARGET_ENV} exists and is not a symlink." >&2
  echo "       Refusing to overwrite a real file. Remove it first if intended." >&2
  exit 1
fi

ln -s "${CANONICAL_ENV}" "${TARGET_ENV}"
echo "setup-worktree.sh: symlinked ${TARGET_ENV} -> ${CANONICAL_ENV}"
