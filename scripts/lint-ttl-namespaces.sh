#!/usr/bin/env bash
# lint-ttl-namespaces.sh — Reject non-canonical namespace declarations in TTL files.
#
# Rules enforced (per aidocs/semantics/101-canonical-iris.md):
#
#   1. No @prefix declaration may use "example.org" as a namespace URI.
#
#   2. If a @prefix line declares mffd:, shepard:, or fair2r: it must
#      map to EXACTLY the canonical base IRI:
#        mffd:    -> http://semantics.dlr.de/mffd-process#
#        shepard: -> http://semantics.dlr.de/shepard-upper#
#        fair2r:  -> https://noheton.org/f-ai-r/ns#
#      Any other URI for these prefixes is a drift violation.
#
# Scans: aidocs/**/*.ttl and backend/src/main/resources/**/*.ttl
#        Also scans examples/**/*.ttl if the directory exists.
#
# Exit codes:
#   0 — all clean
#   1 — one or more violations found (diagnostics printed to stderr)
#
# Usage:
#   bash scripts/lint-ttl-namespaces.sh
#
# Speed: pure grep + bash, no external tools beyond coreutils. Runs in
# under 2s on this repo (~30 TTL files).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ─── Canonical IRI bases ────────────────────────────────────────────────────
MFFD_CANONICAL="http://semantics.dlr.de/mffd-process#"
SHEPARD_CANONICAL="http://semantics.dlr.de/shepard-upper#"
FAIR2R_CANONICAL="https://noheton.org/f-ai-r/ns#"

# ─── Collect TTL files ──────────────────────────────────────────────────────
mapfile -t TTL_FILES < <(
  find \
    "${REPO_ROOT}/aidocs" \
    "${REPO_ROOT}/backend/src/main/resources" \
    $( [ -d "${REPO_ROOT}/examples" ] && echo "${REPO_ROOT}/examples" ) \
    -name "*.ttl" 2>/dev/null | sort
)

if [[ ${#TTL_FILES[@]} -eq 0 ]]; then
  echo "lint-ttl-namespaces: no .ttl files found — nothing to check." >&2
  exit 0
fi

VIOLATIONS=0

# ─── Rule 1: reject example.org namespaces ──────────────────────────────────
while IFS=: read -r file lineno line; do
  echo "${file}:${lineno}: VIOLATION rule-1 (example.org namespace): ${line}" >&2
  VIOLATIONS=$(( VIOLATIONS + 1 ))
done < <(
  grep -rnE '@prefix[[:space:]]+[a-zA-Z0-9_-]*:[[:space:]]*<[^>]*example\.org[^>]*>' \
    "${TTL_FILES[@]}" 2>/dev/null || true
)

# ─── Rule 2: canonical-prefix drift ─────────────────────────────────────────
# For each of the three canonical prefixes, find any @prefix declaration that
# uses a DIFFERENT URI than the canonical one.

check_prefix_drift() {
  local prefix_token="$1"     # e.g. "mffd:"
  local canonical_uri="$2"    # e.g. "http://semantics.dlr.de/mffd-process#"

  # Match lines that declare this prefix; capture the URI actually used.
  # A valid line looks like: @prefix mffd:    <http://semantics.dlr.de/mffd-process#> .
  while IFS=: read -r file lineno line; do
    # Extract the URI from within < >
    actual_uri="$(echo "${line}" | grep -oE '<[^>]+>' | tr -d '<>')"
    if [[ "${actual_uri}" != "${canonical_uri}" ]]; then
      echo "${file}:${lineno}: VIOLATION rule-2 (drift for ${prefix_token}): expected <${canonical_uri}> but got <${actual_uri}>" >&2
      VIOLATIONS=$(( VIOLATIONS + 1 ))
    fi
  done < <(
    grep -rnE "@prefix[[:space:]]+${prefix_token}[[:space:]]*<" \
      "${TTL_FILES[@]}" 2>/dev/null || true
  )
}

check_prefix_drift "mffd:"    "${MFFD_CANONICAL}"
check_prefix_drift "shepard:" "${SHEPARD_CANONICAL}"
check_prefix_drift "fair2r:"  "${FAIR2R_CANONICAL}"

# ─── Result ─────────────────────────────────────────────────────────────────
if [[ "${VIOLATIONS}" -gt 0 ]]; then
  echo "lint-ttl-namespaces: ${VIOLATIONS} violation(s) found. See aidocs/semantics/101-canonical-iris.md." >&2
  exit 1
else
  echo "lint-ttl-namespaces: ${#TTL_FILES[@]} file(s) checked — all clean."
  exit 0
fi
