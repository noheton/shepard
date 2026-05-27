#!/usr/bin/env bash
# lint-label-stages.sh — GH-PM-ENH-API-1
#
# Verifies that the `stage:*` labels in `.github/labels.yml` exactly match
# the canonical stage tokens in `aidocs/00-doc-stages.md`.
#
# The doc defines one overlay band (`upgrade-vX:vY`) that is intentionally
# excluded from the label set; this script filters it out before diffing.
#
# Exit 0 → in sync.  Exit 1 → drift detected (error printed to stderr).
# Runs in under 2s; no network, no build tools.
#
# Usage:
#   scripts/lint-label-stages.sh
#
# Invoked by .github/workflows/label-stage-lint.yml on push to main and
# on PRs that touch .github/labels.yml or aidocs/00-doc-stages.md.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LABELS_FILE="${REPO_ROOT}/.github/labels.yml"
STAGES_FILE="${REPO_ROOT}/aidocs/00-doc-stages.md"

if [[ ! -f "${LABELS_FILE}" ]]; then
  echo "ERROR: ${LABELS_FILE} not found." >&2
  exit 1
fi
if [[ ! -f "${STAGES_FILE}" ]]; then
  echo "ERROR: ${STAGES_FILE} not found." >&2
  exit 1
fi

# Extract stage tokens from labels.yml:  - name: "stage:<TOKEN>"
labels=$(grep -oP '(?<=- name: "stage:)[^"]+' "${LABELS_FILE}" | sort)

# Extract stage tokens from aidocs/00-doc-stages.md: ### `<TOKEN>`
# Filter out the `upgrade-vX:vY` overlay band — it is intentionally absent
# from the label set (it co-exists with a main stage, not a standalone label).
stages=$(grep -oP '(?<=### `)[^`]+' "${STAGES_FILE}" \
  | grep -v '^upgrade-' \
  | sort)

if [[ "${labels}" == "${stages}" ]]; then
  echo "OK: stage labels in .github/labels.yml match aidocs/00-doc-stages.md."
  exit 0
fi

echo "ERROR: stage label drift detected." >&2
echo "" >&2
echo "Labels in .github/labels.yml (stage:* entries):" >&2
echo "${labels}" | sed 's/^/  /' >&2
echo "" >&2
echo "Canonical stages in aidocs/00-doc-stages.md (overlay excluded):" >&2
echo "${stages}" | sed 's/^/  /' >&2
echo "" >&2
echo "Diff (< labels.yml only, > doc-stages.md only):" >&2
diff <(echo "${labels}") <(echo "${stages}") >&2 || true
echo "" >&2
echo "Fix: update .github/labels.yml (stage: section) or aidocs/00-doc-stages.md" >&2
echo "     so the two lists match. The upgrade-vX:vY overlay is intentionally" >&2
echo "     excluded from labels — do not add a stage:upgrade-* label." >&2
exit 1
