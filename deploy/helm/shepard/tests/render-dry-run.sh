#!/usr/bin/env bash
# Phase 1 reviewer-test smoke script.
#
# Renders the umbrella chart against tests/test-values.yaml and exits non-zero if
# rendering fails. CI wiring lands in HELM-K8S-DEPLOY-06 (Phase 5).
#
# Usage:
#   cd deploy/helm/shepard
#   ./tests/render-dry-run.sh
set -euo pipefail

CHART_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$CHART_DIR"

echo "==> helm dependency build (sub-chart packaging may warn — that's fine for Phase 1)"
helm dependency build || true

echo "==> helm lint"
helm lint . -f tests/test-values.yaml

echo "==> helm template (dry-run)"
RENDERED=$(helm template shepard-test . -f tests/test-values.yaml)
echo "$RENDERED" | head -5

COUNT=$(echo "$RENDERED" | grep -c '^---' || true)
echo "==> Resource document separators found: $COUNT"

# Phase 1 reviewer-test gate: >= 10 separated resources.
if [ "$COUNT" -lt 10 ]; then
  echo "FAIL: expected >= 10 resources, got $COUNT" >&2
  exit 1
fi

echo "PASS: Phase 1 skeleton renders cleanly with $COUNT resources."
