#!/bin/bash
# Install the project's tracked git hooks from .git-hooks/ into .git/hooks/.
#
# Run once after cloning:
#   bash scripts/install-hooks.sh
#
# Hooks installed:
#   pre-commit — runs `python3 scripts/regenerate-doc-stage-index.py --check`
#                to block commits when the aidocs/01-doc-stage-index.md index
#                is out of date or any aidocs/**/*.md is missing a stage: field.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOOKS_SRC="$REPO_ROOT/.git-hooks"

# Resolve the real hooks directory: handle both regular checkouts (.git/hooks)
# and git worktrees (.git is a file containing "gitdir: <real-git-dir>").
GIT_DIR="$REPO_ROOT/.git"
if [ -f "$GIT_DIR" ]; then
  # This is a worktree — read the real gitdir from the .git file.
  REAL_GIT_DIR="$(sed -n 's/^gitdir: //p' "$GIT_DIR")"
  # Shared hooks live in the common repo root, not the worktree-specific dir.
  HOOKS_DEST="$(git -C "$REPO_ROOT" rev-parse --git-common-dir)/hooks"
else
  HOOKS_DEST="$GIT_DIR/hooks"
fi

if [ ! -d "$HOOKS_SRC" ]; then
  echo "ERROR: $HOOKS_SRC not found — are you in the repo root?" >&2
  exit 1
fi

mkdir -p "$HOOKS_DEST"

for hook in "$HOOKS_SRC"/*; do
  name="$(basename "$hook")"
  dest="$HOOKS_DEST/$name"
  cp "$hook" "$dest"
  chmod +x "$dest"
  echo "Installed: .git/hooks/$name"
done

echo "Hooks installed."
