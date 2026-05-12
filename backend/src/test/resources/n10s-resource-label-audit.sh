#!/usr/bin/env bash
# N1a — Cypher-write `:Resource`-label audit (lint-only).
#
# Greps every Cypher string-literal in backend/src/main/java for write
# operations (CREATE/MERGE/SET/DELETE/REMOVE) that traverse without an
# explicit `:Label` predicate AND don't include a `WHERE NOT n:Resource`
# guard. Such writes risk colliding with the n10s-managed `:Resource`
# subgraph (per aidocs/48 §3.3 "namespace separation").
#
# This is **lint-only**: failures are informational. Operator runbook:
#   1. Re-read the flagged Cypher.
#   2. If it intentionally touches the n10s subgraph (e.g. seeding
#      ontologies via Cypher rather than via n10s.rdf.import.fetch),
#      add an `// ALLOW: writes to :Resource` trailing comment and
#      this script will skip it.
#   3. Otherwise add `WHERE NOT n:Resource` (or pin a specific label).
#
# Wired into CI advisory-only via `backend-ci.yml` (planned, not in
# this PR — see aidocs/34 N1a row).

set -euo pipefail

# Resolve to backend/src/main/java regardless of how the script is
# invoked. Two anchors:
#   1. SHEPARD_AUDIT_SRC env var (CI / overrides).
#   2. Walk up from the script location until we hit a `pom.xml`.
if [[ -n "${SHEPARD_AUDIT_SRC:-}" ]]; then
  SRC="${SHEPARD_AUDIT_SRC}"
else
  here="$(cd "$(dirname "$0")" && pwd)"
  while [[ "$here" != "/" && ! -f "$here/pom.xml" ]]; do
    here="$(dirname "$here")"
  done
  SRC="${here}/src/main/java"
fi

if [[ ! -d "$SRC" ]]; then
  echo "n10s-resource-label-audit: cannot find source root ($SRC); skipping." >&2
  exit 0
fi

# Heuristic grep: look for free-floating MATCH (n) / MATCH (x) with no
# label predicate that's followed by a write verb in the same string
# literal (or following lines up to a closing quote). We deliberately
# keep the regex permissive — false positives are fine for a lint pass.
# An inline `// ALLOW: writes to :Resource` opts a block out.

files=0
while IFS= read -r -d '' file; do
  files=$((files + 1))
  awk -v file="$file" '
    BEGIN {
      pat = "MATCH[[:space:]]*\\([a-zA-Z_][a-zA-Z0-9_]*\\)"
      writes = "(CREATE|MERGE|SET[[:space:]]|DELETE|REMOVE)"
      allow  = "ALLOW:[[:space:]]+writes[[:space:]]+to[[:space:]]+:Resource"
      guard  = "WHERE[[:space:]]+NOT[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*:Resource"
    }
    {
      lines[NR] = $0
    }
    END {
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        if (match(line, pat) == 0) continue
        # The match is followed immediately by a non-word char (not
        # a colon, which would mean :Label is present right after).
        rest = substr(line, RSTART + RLENGTH, 1)
        if (rest == ":") continue
        if (line ~ allow) continue

        guarded = 0
        wrote = 0
        for (j = i; j <= NR && j < i + 20; j++) {
          if (lines[j] ~ guard) guarded = 1
          if (lines[j] ~ writes) wrote = 1
          if (lines[j] ~ allow) guarded = 1
        }
        if (wrote && !guarded) {
          printf("[advisory] %s:%d  unlabelled-MATCH followed by write without :Resource guard\n", file, i)
        }
      }
    }
  ' "$file" || true
done < <(find "$SRC" -name '*.java' -print0)

echo "n10s-resource-label-audit: scanned ${files} java files."
# Lint-only: always exit 0. Operators (or CI in a future advisory job)
# pipe the output to `grep -c '^\[advisory\]'` to count hits.
exit 0
