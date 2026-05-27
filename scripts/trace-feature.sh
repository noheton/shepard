#!/usr/bin/env bash
#
# trace-feature.sh — resolve a feature ID across every traceability surface.
#
# Implements §14 of aidocs/strategy/85-github-project-management-policies.md.
#
# Walks every surface that *should* mention an aidocs/16 feature ID:
#   1. aidocs/16-dispatcher-backlog.md catalogue row
#   2. aidocs/ design docs (anywhere except the backlog itself)
#   3. aidocs/agent-findings/ persona reports
#   4. git log with Conventional-Commits scope = ID
#   5. files touched by those commits
#   6. aidocs/34-upstream-upgrade-path.md admin row
#   7. aidocs/data/00-model-inventory.md model delta
#   8. GitHub Issue (if any) — best-effort via `gh`
#   9. Release tag containing the merge commit
#  10. aidocs/44-fork-vs-upstream-feature-matrix.md matrix row
#  11. Open PRs referencing the ID — best-effort via `gh pr list`
#  12. Plugin docs — plugins/*/docs/*.md
#  13. User-facing docs — docs/reference/*.md and docs/help/*.md
#
# If a shipped feature returns nothing from one of these surfaces,
# traceability is broken. Fix the missing surface before shipping
# anything else.
#
# Usage:
#   scripts/trace-feature.sh <FEATURE-ID>
#   scripts/trace-feature.sh --help
#
# Example:
#   scripts/trace-feature.sh FS1b

set -u

PROG="$(basename "$0")"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

usage() {
  cat <<EOF
Usage: ${PROG} <FEATURE-ID>

Resolve a feature ID across every traceability surface (per
aidocs/strategy/85-github-project-management-policies.md §14).

Arguments:
  FEATURE-ID    The Conv-Commits scope / aidocs/16 row ID (case sensitive).
                Examples: FS1b, IMPORT-W1, GH-PM1, KIP1d.

Options:
  -h, --help    Show this help and exit.

Exit status:
  0   Trace ran (regardless of which surfaces returned data).
  1   Bad arguments or unknown flag.
  2   Repo root not found.

The script is read-only — it never writes to the repo.
EOF
}

case "${1:-}" in
  -h|--help|"")
    usage
    [ -z "${1:-}" ] && exit 1 || exit 0
    ;;
  -*)
    echo "${PROG}: unknown flag: $1" >&2
    usage >&2
    exit 1
    ;;
esac

ID="$1"

cd "${REPO_ROOT}" || { echo "${PROG}: cannot cd to repo root" >&2; exit 2; }

# ANSI helpers (skip in non-TTY)
if [ -t 1 ]; then
  BOLD=$'\033[1m'; DIM=$'\033[2m'; RESET=$'\033[0m'
else
  BOLD=""; DIM=""; RESET=""
fi

section() {
  printf '\n%s── %s ──%s\n' "${BOLD}" "$1" "${RESET}"
}

dim() {
  printf '%s%s%s\n' "${DIM}" "$1" "${RESET}"
}

# ─── 1. aidocs/16 backlog row ─────────────────────────────────────────
section "1. aidocs/16 backlog row"
# Also extract the Notes column to determine whether this row is a chore/docs-only
# change — used by surfaces 6 and 7 to emit a more informative message.
ROW_IS_EXEMPT=0
if [ -f aidocs/16-dispatcher-backlog.md ]; then
  ROW_LINE=$(grep "^| ${ID} " aidocs/16-dispatcher-backlog.md | head -1)
  if [ -n "${ROW_LINE}" ]; then
    # The backlog rows end with a trailing " |", so $NF is empty;
    # the actual Notes column is the second-to-last pipe-delimited field.
    NOTES=$(echo "${ROW_LINE}" | awk -F'|' '{print $(NF-1)}')
    if echo "${NOTES}" | grep -qiE 'chore|docs-only|doc-only|housekeeping|docs/'; then
      ROW_IS_EXEMPT=1
    fi
  fi
  if ! grep -A 2 "^| ${ID} " aidocs/16-dispatcher-backlog.md; then
    dim "  (no row found — file an aidocs/16 row first)"
  fi
else
  dim "  aidocs/16-dispatcher-backlog.md missing"
fi

# ─── 2. design docs ──────────────────────────────────────────────────
section "2. design docs (aidocs/ outside the backlog)"
if [ -d aidocs ]; then
  HITS=$(grep -rln "\\b${ID}\\b" aidocs/ --include='*.md' 2>/dev/null \
         | grep -v '16-dispatcher-backlog.md' \
         | grep -v 'agent-findings/' \
         | grep -v '01-doc-stage-index.md')
  if [ -n "${HITS}" ]; then
    echo "${HITS}"
  else
    dim "  (no design-doc references)"
  fi
fi

# ─── 3. persona findings ─────────────────────────────────────────────
section "3. persona findings (aidocs/agent-findings/)"
if [ -d aidocs/agent-findings ]; then
  HITS=$(grep -rln "\\b${ID}\\b" aidocs/agent-findings/ 2>/dev/null)
  if [ -n "${HITS}" ]; then
    echo "${HITS}"
  else
    dim "  (no persona findings reference this ID)"
  fi
else
  dim "  aidocs/agent-findings/ missing"
fi

# ─── 4. commits with Conv-Commits scope = ID ─────────────────────────
# Matches `<type>(${ID}): ` and combined scopes `<type>(${ID}+OTHER): ` /
# `<type>(OTHER+${ID}): ` (per §7 of aidocs/strategy/85). Combined scopes
# are discouraged but historically exist (e.g. `feat(FS1b+FS1d): …`).
section "4. commits scoped to ${ID}"
GREP_RE="^[a-z]\\+([A-Za-z0-9+-]*\\b${ID}\\b[A-Za-z0-9+-]*): "
COMMITS=$(git log --grep="${GREP_RE}" --oneline 2>/dev/null)
if [ -n "${COMMITS}" ]; then
  echo "${COMMITS}"
else
  dim "  (no commits with scope (${ID}))"
fi

# ─── 5. files touched ────────────────────────────────────────────────
section "5. files touched by those commits"
FILES=$(git log --grep="${GREP_RE}" --name-only --pretty=format: 2>/dev/null \
        | sort -u | sed '/^$/d')
if [ -n "${FILES}" ]; then
  echo "${FILES}"
else
  dim "  (no files — no commits or no file touches)"
fi

# ─── 6. aidocs/34 admin row ──────────────────────────────────────────
section "6. aidocs/34 admin-facing row"
if [ -f aidocs/34-upstream-upgrade-path.md ]; then
  if ! grep -n "\\b${ID}\\b" aidocs/34-upstream-upgrade-path.md; then
    if [ "${ROW_IS_EXEMPT}" -eq 1 ]; then
      dim "  (legitimately empty — chore/docs-only change; no admin or model-inventory row expected)"
    else
      dim "  (no aidocs/34 row — admins don't know this shipped)"
    fi
  fi
fi

# ─── 7. aidocs/data/00 model delta ───────────────────────────────────
section "7. aidocs/data/00 model delta"
if [ -f aidocs/data/00-model-inventory.md ]; then
  if ! grep -n "\\b${ID}\\b" aidocs/data/00-model-inventory.md; then
    if [ "${ROW_IS_EXEMPT}" -eq 1 ]; then
      dim "  (legitimately empty — chore/docs-only change; no admin or model-inventory row expected)"
    else
      dim "  (no model-inventory mention — fine if the change didn't touch the substrate)"
    fi
  fi
else
  dim "  aidocs/data/00-model-inventory.md missing"
fi

# ─── 8. GitHub Issue (best-effort) ───────────────────────────────────
section "8. GitHub Issue (best-effort via gh)"
if command -v gh >/dev/null 2>&1; then
  if gh auth status >/dev/null 2>&1; then
    ISSUES=$(gh issue list --search "${ID} in:title" --state all \
              --json number,title,state,url 2>/dev/null \
              | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for it in data:
    print(f"  #{it[\"number\"]:>4}  {it[\"state\"]:>6}  {it[\"title\"]}")
    print(f"         {it[\"url\"]}")
' 2>/dev/null)
    if [ -n "${ISSUES}" ]; then
      echo "${ISSUES}"
    else
      dim "  (no Issue — expected; default per §3 is no Issue)"
    fi
  else
    dim "  gh not authenticated; skipping (run \`gh auth login\`)"
  fi
else
  dim "  gh CLI not installed; skipping"
fi

# ─── 9. release tag(s) ──────────────────────────────────────────────
section "9. release tag(s) containing this work"
LAST_COMMIT=$(git log --grep="${GREP_RE}" --format=%H 2>/dev/null | tail -1)
if [ -n "${LAST_COMMIT}" ]; then
  # Filter out archive/ refs (those are agent-worktree snapshots, not releases).
  TAGS=$(git tag --contains "${LAST_COMMIT}" 2>/dev/null | grep -v '^archive/')
  if [ -n "${TAGS}" ]; then
    echo "${TAGS}"
  else
    dim "  (no release tag yet — feature is on a branch / unreleased)"
  fi
else
  dim "  (no commits to anchor a release lookup)"
fi

# ─── 10. aidocs/44 fork-vs-upstream matrix ───────────────────────
section "10. aidocs/44 fork-vs-upstream feature matrix"
if [ -f aidocs/44-fork-vs-upstream-feature-matrix.md ]; then
  if ! grep -n "\\b${ID}\\b" aidocs/44-fork-vs-upstream-feature-matrix.md; then
    dim "  (no aidocs/44 row — fork-vs-upstream matrix doesn't list this; flip the row when the feature ships)"
  fi
else
  dim "  aidocs/44-fork-vs-upstream-feature-matrix.md missing"
fi

# ─── 11. open PRs (best-effort via gh) ───────────────────────────
section "11. open PRs referencing ${ID}"
if command -v gh >/dev/null 2>&1; then
  if gh auth status >/dev/null 2>&1; then
    PRS=$(timeout 5 gh pr list --search "${ID}" --state open \
           --json number,title,state,url 2>/dev/null \
           | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for pr in data:
    print(f"  #{pr[\"number\"]:>4}  {pr[\"state\"]:>6}  {pr[\"title\"]}")
    print(f"         {pr[\"url\"]}")
' 2>/dev/null)
    if [ -n "${PRS}" ]; then
      echo "${PRS}"
    else
      dim "  (no open PRs reference this ID)"
    fi
  else
    dim "  gh not authenticated; skipping (run \`gh auth login\`)"
  fi
else
  dim "  gh CLI not installed; skipping"
fi

# ─── 12. plugin docs ─────────────────────────────────────────────
section "12. plugin docs (plugins/*/docs/*.md)"
if [ -d plugins ]; then
  HITS=$(grep -rln "\\b${ID}\\b" plugins/ --include='*.md' 2>/dev/null)
  if [ -n "${HITS}" ]; then
    echo "${HITS}"
  else
    dim "  (no plugin-docs references — fine if the feature is not plugin-touching)"
  fi
else
  dim "  plugins/ directory not found"
fi

# ─── 13. user-facing docs ────────────────────────────────────────
section "13. user-facing docs (docs/reference/ + docs/help/)"
USERDOC_HITS=""
if [ -d docs/reference ]; then
  USERDOC_HITS=$(grep -rln "\\b${ID}\\b" docs/reference/ --include='*.md' 2>/dev/null)
fi
if [ -d docs/help ]; then
  HELP_HITS=$(grep -rln "\\b${ID}\\b" docs/help/ --include='*.md' 2>/dev/null)
  USERDOC_HITS="${USERDOC_HITS}${USERDOC_HITS:+$'\n'}${HELP_HITS}"
fi
if [ -n "${USERDOC_HITS}" ]; then
  echo "${USERDOC_HITS}"
else
  dim "  (no user-facing docs references — fine for internal/refactor/security-fix)"
fi

printf '\n'
exit 0
