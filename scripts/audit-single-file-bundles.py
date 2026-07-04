#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["neo4j>=5.20"]
# ///
"""
audit-single-file-bundles.py — SINGLETON-FILE-01-AUDIT

Counts :FileBundleReference rows that hold exactly ONE file under the
"singleton-shaped bundle" debt definition codified in CLAUDE.md
("Always: singleton FileReference for one-file uploads; FileBundleReference
only when bundling >1").

The audit checks BOTH shapes the codebase has used:

  (a) Legacy bundle shape — `b.fileOids[]` array on the
      :FileBundleReference node directly (pre-V21).
  (b) Post-V21 bundle shape — `(b)-[:HAS_GROUP]->(:FileGroup)-[:has_payload]->(:ShepardFile)`
      with exactly one ShepardFile reachable through the default group.

A bundle is "singleton-shaped" if EITHER:
  - `size(b.fileOids) = 1`, or
  - exactly one ShepardFile reachable via the HAS_GROUP/has_payload chain.

The two shapes are not mutually exclusive (V21 minted the default group with
a shadow `(b)-[:has_payload]->(f)` edge), so the script de-duplicates per
elementId.

Output:
  - Per-Collection breakdown (collection.name + collection.appId → count).
  - Per-extension breakdown (.urdf, .stl, .src, .ipynb, …).
  - Combined Collection × extension matrix (the "what's where" table).
  - V23 status: how many :SingletonFileReference rows already carry the
    `legacyV23Singleton = true` marker.
  - Writes the report to aidocs/agent-findings/singleton-file-audit-<date>.md
    with proper YAML frontmatter (stage: deployed, last-stage-change: <date>).

Usage:
  NEO4J_URI=bolt://localhost:7687 NEO4J_USER=neo4j NEO4J_PASSWORD=... \\
    uv run scripts/audit-single-file-bundles.py

  # Or, the dispatch box's local Compose Neo4j (uses defaults):
  uv run scripts/audit-single-file-bundles.py

  # Override report path:
  uv run scripts/audit-single-file-bundles.py --out /tmp/audit.md

Equivalent cypher-shell session (operator can run from any shepard host
that can reach Neo4j):

    MATCH (b:FileBundleReference)
    WHERE size(coalesce(b.fileOids, [])) = 1
       OR EXISTS {
         MATCH (b)-[:HAS_GROUP]->(g:FileGroup)
         WITH g, b
         WHERE count{(g)-[:has_payload]->(:ShepardFile)} = 1
         AND   count{(b)-[:HAS_GROUP]->(:FileGroup)} = 1
         RETURN g
       }
    OPTIONAL MATCH (d:DataObject)-[:has_reference]->(b)
    OPTIONAL MATCH (c:Collection)-[:has_dataobject]->(d)
    RETURN coalesce(c.name, '<orphan>') AS collection,
           coalesce(c.appId, '<no-appId>') AS collectionAppId,
           count(b) AS bundles_with_one_file
    ORDER BY bundles_with_one_file DESC;

This script wraps that query, plus the per-extension breakdown, and writes
the findings doc that SINGLETON-FILE-03-BACKFILL-SCRIPT consumes as its
worklist.

SINGLETON-FILE-01-AUDIT — `aidocs/16-dispatcher-backlog.md`.
"""

from __future__ import annotations

import argparse
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

try:
    from neo4j import GraphDatabase
except ImportError:  # pragma: no cover — uv handles the install
    print(
        "neo4j driver not installed. Re-run with `uv run` so the inline "
        "script header installs it, or `pip install neo4j>=5.20`.",
        file=sys.stderr,
    )
    sys.exit(2)


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = (
    REPO_ROOT
    / "aidocs"
    / "agent-findings"
    / f"singleton-file-audit-{datetime.now(timezone.utc).strftime('%Y-%m-%d')}.md"
)

# Query (a) — legacy fileOids array shape (pre-V21).
#   Edge direction (from `de.dlr.shepard.common.util.Constants`):
#     (c:Collection)-[:has_dataobject]->(d:DataObject)-[:has_reference]->(b:FileBundleReference)
LEGACY_FILEOIDS_QUERY = """
MATCH (b:FileBundleReference)
WHERE size(coalesce(b.fileOids, [])) = 1
OPTIONAL MATCH (d:DataObject)-[:has_reference]->(b)
OPTIONAL MATCH (c:Collection)-[:has_dataobject]->(d)
RETURN elementId(b) AS bundleEid,
       b.appId      AS bundleAppId,
       b.name       AS bundleName,
       coalesce(c.name,  '<orphan>')   AS collectionName,
       coalesce(c.appId, '<no-appId>') AS collectionAppId,
       coalesce(c.id,    -1)           AS collectionId,
       b.fileOids[0]                   AS sampleOid
"""

# Query (b) — post-V21 group/payload shape.
GROUP_PAYLOAD_QUERY = """
MATCH (b:FileBundleReference)
WHERE NOT b:SingletonFileReference
MATCH (b)-[:HAS_GROUP]->(g:FileGroup)
WITH b, count{(b)-[:HAS_GROUP]->(:FileGroup)} AS groupCount
WHERE groupCount = 1
MATCH (b)-[:HAS_GROUP]->(g:FileGroup)
MATCH (g)-[:has_payload]->(f:ShepardFile)
WITH b, g, count(DISTINCT f) AS fileCount, collect(f)[0] AS firstFile
WHERE fileCount = 1
OPTIONAL MATCH (d:DataObject)-[:has_reference]->(b)
OPTIONAL MATCH (c:Collection)-[:has_dataobject]->(d)
RETURN elementId(b) AS bundleEid,
       b.appId      AS bundleAppId,
       b.name       AS bundleName,
       coalesce(c.name,  '<orphan>')   AS collectionName,
       coalesce(c.appId, '<no-appId>') AS collectionAppId,
       coalesce(c.id,    -1)           AS collectionId,
       coalesce(firstFile.fileName, firstFile.oid, '<unknown>') AS sampleOid
"""

V23_MARKER_QUERY = """
MATCH (s:SingletonFileReference)
WHERE s.legacyV23Singleton = true
RETURN count(s) AS legacy_v23_count
"""

TOTALS_QUERY = """
MATCH (b:FileBundleReference) WITH count(b) AS bundles
MATCH (s:SingletonFileReference) WITH bundles, count(s) AS singletons
RETURN bundles, singletons
"""


def _ext_of(name: str | None) -> str:
    if not name:
        return "<no-name>"
    n = name.lower()
    # Multi-dot tails like ".tar.gz" → ".gz". Keep the simple "last suffix"
    # rule — operator legibility beats edge-case precision here.
    if "." not in n:
        return "<no-ext>"
    return "." + n.rsplit(".", 1)[1]


def _run(driver, query, **params):
    with driver.session() as s:
        return list(s.run(query, **params))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--neo4j-uri",
        default=os.environ.get("NEO4J_URI", "bolt://localhost:7687"),
        help="Neo4j Bolt URI (default: bolt://localhost:7687).",
    )
    parser.add_argument(
        "--neo4j-user",
        default=os.environ.get("NEO4J_USER", "neo4j"),
        help="Neo4j user (default: neo4j).",
    )
    parser.add_argument(
        "--neo4j-password",
        default=os.environ.get("NEO4J_PASSWORD", os.environ.get("NEO4J_PW", "neo4j_dev_secret")),
        help="Neo4j password (default reads NEO4J_PASSWORD / NEO4J_PW; falls back to the dispatch-box dev value).",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=DEFAULT_OUT,
        help=f"Output markdown path (default: {DEFAULT_OUT}).",
    )
    parser.add_argument(
        "--print-only",
        action="store_true",
        help="Print the report to stdout but don't write the file.",
    )
    args = parser.parse_args()

    driver = GraphDatabase.driver(
        args.neo4j_uri, auth=(args.neo4j_user, args.neo4j_password)
    )
    try:
        driver.verify_connectivity()
    except Exception as ex:
        print(f"ERROR: cannot reach Neo4j at {args.neo4j_uri}: {ex}", file=sys.stderr)
        print(
            "Hint: from outside docker, use the published port "
            "(infrastructure compose maps 7687). From a different host, set "
            "NEO4J_URI / NEO4J_PASSWORD.",
            file=sys.stderr,
        )
        return 3

    # ── 1) Top-line totals
    totals = _run(driver, TOTALS_QUERY)
    total_bundles = int(totals[0]["bundles"]) if totals else 0
    total_singletons = int(totals[0]["singletons"]) if totals else 0

    # ── 2) V23 marker count
    v23_rows = _run(driver, V23_MARKER_QUERY)
    v23_legacy_count = int(v23_rows[0]["legacy_v23_count"]) if v23_rows else 0

    # ── 3) Singleton-shaped bundles (de-dup across both shapes by elementId)
    seen: dict[str, dict] = {}
    for row in _run(driver, LEGACY_FILEOIDS_QUERY):
        seen[row["bundleEid"]] = {
            "appId": row["bundleAppId"],
            "name": row["bundleName"],
            "collection": row["collectionName"],
            "collectionAppId": row["collectionAppId"],
            "collectionId": row["collectionId"],
            "shape": "fileOids[1]",
            "ext": _ext_of(row["bundleName"]),
        }
    for row in _run(driver, GROUP_PAYLOAD_QUERY):
        eid = row["bundleEid"]
        if eid in seen:
            seen[eid]["shape"] = "fileOids[1]+has_group"
        else:
            seen[eid] = {
                "appId": row["bundleAppId"],
                "name": row["bundleName"],
                "collection": row["collectionName"],
                "collectionAppId": row["collectionAppId"],
                "collectionId": row["collectionId"],
                "shape": "has_group",
                "ext": _ext_of(row["sampleOid"] or row["bundleName"]),
            }

    candidates = list(seen.values())
    candidate_count = len(candidates)

    # ── 4) Per-Collection breakdown
    per_collection: Counter[tuple[str, str, int]] = Counter()
    for c in candidates:
        per_collection[
            (c["collection"], c["collectionAppId"], c["collectionId"])
        ] += 1

    # ── 5) Per-extension breakdown
    per_ext: Counter[str] = Counter(c["ext"] for c in candidates)

    # ── 6) Combined Collection × extension matrix
    matrix: dict[tuple[str, str], Counter[str]] = defaultdict(Counter)
    for c in candidates:
        matrix[(c["collection"], c["collectionAppId"])][c["ext"]] += 1

    # ── Build the report
    now = datetime.now(timezone.utc)
    today = now.strftime("%Y-%m-%d")
    lines: list[str] = []
    lines.append("---")
    lines.append("stage: deployed")
    lines.append(f"last-stage-change: {today}")
    lines.append("---")
    lines.append("")
    lines.append(f"# Single-file FileBundleReference audit — {today}")
    lines.append("")
    lines.append(
        "Audit of `:FileBundleReference` rows that hold exactly one file — "
        "the technical debt described in CLAUDE.md "
        '"Always: singleton FileReference for one-file uploads; '
        'FileBundleReference only when bundling >1".'
    )
    lines.append("")
    lines.append(f"Run at: `{now.isoformat()}` against `{args.neo4j_uri}`.")
    lines.append("")
    lines.append("## Top line")
    lines.append("")
    lines.append("| Metric | Count |")
    lines.append("|---|---|")
    lines.append(f"| `:FileBundleReference` (total) | {total_bundles} |")
    lines.append(f"| `:SingletonFileReference` (total) | {total_singletons} |")
    lines.append(
        f"| Singleton-shaped bundles (debt) | **{candidate_count}** |"
    )
    lines.append(
        f"| Singletons minted by V23 (`legacyV23Singleton=true`) | {v23_legacy_count} |"
    )
    lines.append("")
    if candidate_count == 0:
        lines.append(
            "**No single-file FileBundleReferences found.** The instance is "
            "either fully migrated (V23 has already run, or every existing "
            "bundle genuinely holds 0 or >1 files) or has no FileBundle "
            "payload yet. No backfill action required."
        )
    else:
        lines.append(
            f"**{candidate_count} single-file bundles are eligible for migration "
            "to singletons.** See SINGLETON-FILE-03-BACKFILL-SCRIPT in "
            "`aidocs/16-dispatcher-backlog.md` and the operator runbook at "
            "`docs/admin/runbooks/single-file-singletons.md`."
        )
    lines.append("")

    lines.append("## Per-collection breakdown")
    lines.append("")
    if per_collection:
        lines.append("| Collection | appId | numeric id | single-file bundles |")
        lines.append("|---|---|---|---|")
        for (name, app_id, col_id), n in per_collection.most_common():
            lines.append(f"| {name} | `{app_id}` | {col_id} | {n} |")
    else:
        lines.append("_No candidates — table omitted._")
    lines.append("")

    lines.append("## Per-extension breakdown")
    lines.append("")
    if per_ext:
        lines.append("| extension | single-file bundles |")
        lines.append("|---|---|")
        for ext, n in per_ext.most_common():
            lines.append(f"| `{ext}` | {n} |")
    else:
        lines.append("_No candidates — table omitted._")
    lines.append("")

    lines.append("## Collection × extension matrix")
    lines.append("")
    if matrix:
        # Build a stable column ordering: extensions sorted by total count desc.
        all_exts = sorted(per_ext, key=lambda e: (-per_ext[e], e))
        header = "| Collection | appId | " + " | ".join(f"`{e}`" for e in all_exts) + " | total |"
        sep = "|---|---|" + "|".join(["---"] * (len(all_exts) + 1)) + "|"
        lines.append(header)
        lines.append(sep)
        # Sort by total count desc
        rows = sorted(
            matrix.items(),
            key=lambda kv: -sum(kv[1].values()),
        )
        for (name, app_id), exts in rows:
            row_cells = [str(exts.get(e, 0)) for e in all_exts]
            total = sum(exts.values())
            lines.append(f"| {name} | `{app_id}` | " + " | ".join(row_cells) + f" | {total} |")
    else:
        lines.append("_No candidates — matrix omitted._")
    lines.append("")

    lines.append("## Recommended next steps")
    lines.append("")
    if candidate_count == 0:
        lines.append(
            "1. Mark `SINGLETON-FILE-01-AUDIT` and `SINGLETON-FILE-03-BACKFILL-SCRIPT` "
            "as **no-op** for this instance (re-run the audit before the next backfill window)."
        )
        lines.append(
            "2. Land `SINGLETON-FILE-04-UI-DEFAULT` so the singleton-shape "
            "default prevents new debt from forming."
        )
        lines.append(
            "3. Land `SINGLETON-FILE-05-SHACL-GUARD` so the completeness widget "
            "flags any debt that does appear."
        )
    else:
        lines.append(
            "1. Run `scripts/backfill-single-file-bundles-to-singletons.py "
            "--dry-run` to verify the worklist on this instance."
        )
        lines.append(
            "2. Snapshot Neo4j + MongoDB per `docs/admin/runbooks/single-file-singletons.md`."
        )
        lines.append(
            "3. Run the backfill with `--commit`. Each transform emits one "
            "`:SingletonFileMigrationActivity` PROV-O Activity tying the "
            "old bundle appId, the new singleton label, and the user."
        )
        lines.append(
            "4. Re-run this audit. The count should drop to 0."
        )
        lines.append(
            "5. Land `SINGLETON-FILE-04-UI-DEFAULT` + "
            "`SINGLETON-FILE-05-SHACL-GUARD` to prevent re-accretion."
        )
    lines.append("")
    lines.append("---")
    lines.append("")
    lines.append(
        "Audit generated by `scripts/audit-single-file-bundles.py` "
        "(SINGLETON-FILE-01-AUDIT)."
    )
    lines.append("")

    report = "\n".join(lines)
    if args.print_only:
        print(report)
    else:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(report)
        print(f"Wrote {args.out}", file=sys.stderr)
        print(f"Findings: {candidate_count} single-file bundles across "
              f"{len(per_collection)} collections.", file=sys.stderr)
    driver.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
