#!/usr/bin/env bash
# P17b: Lint that every IO/DTO class has @Schema(name = ...) so the
# OpenAPI generator is given an explicit, stable schema name. A class
# rename then no longer breaks every downstream generated client.
#
# See aidocs/16-dispatcher-backlog.md P17b and aidocs/23-api-critique.md
# section 5.
#
# Predicate: a class is an IO/DTO iff its file is either
#   (a) directly under any 'io/' package (depth 1, no nested sub-packages
#       like 'io/validation/'), excluding *Mapper.java helper classes; or
#   (b) named *IO.java anywhere under backend/src/main/java/.
#
# Existing offenders are listed in backend/src/main/resources/schema-name-baseline.txt
# so cleanup can happen incrementally; the lint passes only if the
# missing set is a subset of the baseline.
#
# Usage: bash scripts/check-schema-name.sh
# Exits non-zero on regressions.

set -euo pipefail

# Resolve repo root from script location, tolerate being invoked from anywhere.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

JAVA_ROOT="backend/src/main/java"
BASELINE_FILE="backend/src/main/resources/schema-name-baseline.txt"

if [[ ! -d "$JAVA_ROOT" ]]; then
  echo "error: $JAVA_ROOT not found (run from repo root)" >&2
  exit 2
fi

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "error: $BASELINE_FILE not found" >&2
  exit 2
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

candidates="$tmpdir/candidates.txt"
with_schema="$tmpdir/with_schema.txt"
missing="$tmpdir/missing.txt"
baseline="$tmpdir/baseline.txt"
unexpected="$tmpdir/unexpected.txt"
stale="$tmpdir/stale.txt"

# Build candidate list (deterministic, sorted).
{
  find "$JAVA_ROOT" -path '*/io/*.java' -not -path '*/io/*/*' -not -name '*Mapper.java'
  find "$JAVA_ROOT" -name '*IO.java' -not -path '*/io/*'
} | LC_ALL=C sort -u > "$candidates"

# Find classes that have a class-level @Schema(name = "...") on them.
# We only require the annotation appear in the file (class-level pinning is
# the convention; field-level @Schema(name=...) is rare and not what we want
# to enforce, but keeping the predicate file-level keeps the lint simple and
# matches how the OpenAPI generator picks up the model name).
: > "$with_schema"
while IFS= read -r f; do
  # Match both attribute orderings: name first or name later in the args.
  if grep -qE '@Schema\([^)]*\bname[[:space:]]*=' "$f"; then
    printf '%s\n' "$f" >> "$with_schema"
  fi
done < "$candidates"
LC_ALL=C sort -u -o "$with_schema" "$with_schema"

# Compute the actually-missing set.
LC_ALL=C comm -23 "$candidates" "$with_schema" > "$missing"

# Load the baseline (ignore comments and blank lines, normalise to repo-relative paths).
grep -vE '^[[:space:]]*(#|$)' "$BASELINE_FILE" | LC_ALL=C sort -u > "$baseline"

# Regressions: missing classes that are NOT in the baseline.
LC_ALL=C comm -23 "$missing" "$baseline" > "$unexpected"

# Stale baseline entries: baseline entries that are no longer missing
# (either the class got the annotation, or it was removed). These do not fail
# the build, but we surface them so the baseline can shrink over time.
LC_ALL=C comm -13 "$missing" "$baseline" > "$stale"

# Baseline entries that are still missing -- inform contributors per task brief.
LC_ALL=C comm -12 "$missing" "$baseline" > "$tmpdir/baseline_hits.txt"

cand_count=$(wc -l < "$candidates" | tr -d ' ')
ok_count=$(wc -l < "$with_schema" | tr -d ' ')
miss_count=$(wc -l < "$missing" | tr -d ' ')
base_count=$(wc -l < "$baseline" | tr -d ' ')
unexp_count=$(wc -l < "$unexpected" | tr -d ' ')
stale_count=$(wc -l < "$stale" | tr -d ' ')

echo "@Schema(name=...) lint"
echo "  IO/DTO candidates : $cand_count"
echo "  with @Schema(name): $ok_count"
echo "  missing           : $miss_count"
echo "  baseline allowlist: $base_count"
echo

if [[ "$stale_count" -gt 0 ]]; then
  echo "stale baseline entries detected:"
  while IFS= read -r f; do
    echo "  - $f"
    echo "    remove this entry from $BASELINE_FILE once you've added @Schema(name=...) to $f"
  done < "$stale"
  echo
fi

if [[ "$unexp_count" -gt 0 ]]; then
  echo "ERROR: $unexp_count IO/DTO class(es) lack @Schema(name=...) and are not on the baseline:"
  while IFS= read -r f; do
    echo "  - $f"
  done < "$unexpected"
  echo
  echo "Add @org.eclipse.microprofile.openapi.annotations.media.Schema(name=\"...\") at the class level."
  exit 1
fi

if [[ "$miss_count" -eq 0 ]]; then
  echo "OK: every IO/DTO class has @Schema(name=...)."
else
  echo "OK: all $miss_count missing class(es) are covered by the baseline."
fi
