#!/usr/bin/env python3
"""docs-currency-audit.py — Quarterly docs-currency sweep (DOCS-3A5).

For each ``done`` row in ``aidocs/16-dispatcher-backlog.md`` that ships a
user-visible feature, verify that a matching section exists in
``docs/help/`` or ``docs/reference/``.

Exit codes
----------
0  no gaps found (or --no-fail was passed)
1  one or more gaps found

Typical invocations
-------------------
# Human-readable markdown table to stdout:
    python3 scripts/docs-currency-audit.py

# CI warning gate (never fails the build):
    python3 scripts/docs-currency-audit.py --no-fail

# Machine-readable CSV (for dashboards / further processing):
    python3 scripts/docs-currency-audit.py --csv

# Suppress the summary line:
    python3 scripts/docs-currency-audit.py --quiet
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Repo layout
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parent.parent
BACKLOG_PATH = REPO_ROOT / "aidocs" / "16-dispatcher-backlog.md"
DOCS_HELP_DIR = REPO_ROOT / "docs" / "help"
DOCS_REF_DIR = REPO_ROOT / "docs" / "reference"

# ---------------------------------------------------------------------------
# Heuristic: is the row "user-visible"?
# A row is NOT user-visible (skip it) when its description is predominantly
# about internal / infra concerns.  We skip rows whose description matches
# ANY of the following patterns (case-insensitive).
# ---------------------------------------------------------------------------

SKIP_DESCRIPTION_PATTERNS: list[re.Pattern] = [
    # Pure test / coverage work
    re.compile(r"\btest coverage\b", re.I),
    re.compile(r"\bjacoco\b", re.I),
    re.compile(r"\bspotbugs\b", re.I),
    # Pure refactor / chore
    re.compile(r"\brefactor\b", re.I),
    re.compile(r"\bchore\b", re.I),
    re.compile(r"\btree.shak", re.I),
    # Pure DB schema migration (no REST surface)
    re.compile(r"^cypher\s+backfill\b", re.I),
    re.compile(r"\bappend-only migration\b", re.I),
    re.compile(r"\bidempoten?t migration\b", re.I),
    # Pure internal perf / cache tuning with no REST surface
    re.compile(r"\bpermission cache\b.*\bttl\b", re.I),
    re.compile(r"\bcache warming\b", re.I),
    re.compile(r"\bmicrometer metrics\b", re.I),
    re.compile(r"\bparalleliz\b", re.I),
    # Audit / design-doc only
    re.compile(r"\baudit\b.*\bwait\b", re.I),
    re.compile(r"\bdesign doc\b", re.I),
    re.compile(r"\bdesign only\b", re.I),
    # Doc-tooling work (scripts, indexes, front-matter)
    re.compile(r"\bstage.index\b", re.I),
    re.compile(r"\bfront.?matter\b", re.I),
    re.compile(r"\btaxonomy doc\b", re.I),
    re.compile(r"\bdoc.stage\b", re.I),
    re.compile(r"\bpre-commit hook\b", re.I),
    re.compile(r"\baudience.*front.?matter\b", re.I),
    re.compile(r"\bfront.?matter.*audience\b", re.I),
    # Internal plumbing: parameterised Cypher, DAO rewrites
    re.compile(r"\bparam.+bind\b", re.I),
    re.compile(r"\bstring.concatenat\b", re.I),
    # Async / startup internals
    re.compile(r"\basync\s+DB\s+init\b", re.I),
    re.compile(r"\bbounded\s+timeout\b", re.I),
    re.compile(r"\bexponential\s+backoff\b", re.I),
    re.compile(r"\bswallows.*exception\b", re.I),
    re.compile(r"\bDB\s+recovery\s+scheduler\b", re.I),
    re.compile(r"\bDB\s+connection\s+check\b", re.I),
    re.compile(r"\bcompletablefuture\b", re.I),
    re.compile(r"\bvirtual\s+thread\b", re.I),
    # ArchUnit / namespace fencing
    re.compile(r"\barchunit\b", re.I),
    re.compile(r"\bnamespace\s+split\b", re.I),
    re.compile(r"\bnamespace.*fenc\b", re.I),
    # ID migration phases that have no REST surface
    re.compile(r"phase\s+[12]:\s+additive\s+appid", re.I),
    re.compile(r"phase\s+[12]:\s+cypher\s+backfill", re.I),
    # Batch permission check (no user surface)
    re.compile(r"\bbatch\s+permission\s+check\b", re.I),
    # OpenAPI client tree-shaking / code splitting
    re.compile(r"\bOpenAPI\s+client\s+tree", re.I),
    re.compile(r"\bcode\s+split\b", re.I),
]

# The set of row IDs (lower-case) that are always infrastructure / internal
# even if their description sounds user-visible.  Keep this list narrow.
ALWAYS_SKIP_IDS: frozenset[str] = frozenset(
    {
        "c5", "c5b",           # parameterised Cypher rewrites
        "a1", "a1b", "a1c", "a1d", "a1e", "a1f",  # async DB init
        "a3",                   # CDI feature-toggle mechanism (no REST)
        "a3c",                  # namespace alias (no user surface)
        "a4",                   # permission cache config
        "a4c", "a4d",          # cache warming + metrics
        "l2a", "l2b", "l2c",  # appId phases 1-3 (no REST surface)
        "p1",                   # parallelise DB checks
        "p2",                   # batch perm checks
        "p4b",                  # OpenAPI tree-shaking
        "doc-stage1", "doc-stage2",  # doc tooling
        "docs-3a3", "docs-3a9",  # front-matter retrofit
    }
)

# ---------------------------------------------------------------------------
# Parsing helpers
# ---------------------------------------------------------------------------

_STATUS_DONE_RE = re.compile(r"\bdone\b", re.I)


def _strip_markdown(text: str) -> str:
    """Remove bold/italic markers and inline code ticks from a string."""
    text = re.sub(r"\*\*", "", text)
    text = re.sub(r"\*", "", text)
    text = re.sub(r"`[^`]*`", "", text)
    return text.strip()


def _parse_row_cells(line: str) -> list[str]:
    """Split a pipe-delimited markdown table row into trimmed cells."""
    return [c.strip() for c in line.strip("|").split("|")]


def _is_separator_row(cells: list[str]) -> bool:
    return all(re.match(r"^[-: ]+$", c) or c == "" for c in cells)


def _is_header_row(cells: list[str]) -> bool:
    first = _strip_markdown(cells[0]).upper()
    return first in ("ID", "ROW", "#")


def _looks_like_id(token: str) -> bool:
    """True when token looks like a backlog row ID (short, no long spaces)."""
    if not token:
        return False
    if len(token) > 30:
        return False
    # Must have at least one letter and one digit (e.g. A0, L2a, FR1b, DOCS-3A5)
    # OR be an all-caps/short word like "M9"
    return bool(re.match(r"^[A-Za-z][\w\-]*$", token))


# ---------------------------------------------------------------------------
# Backlog parser
# ---------------------------------------------------------------------------


def parse_backlog(path: Path) -> list[dict]:
    """Parse aidocs/16 and return list of row dicts for ``done`` data rows.

    Each dict has keys:
      ``id``          — normalised row ID string
      ``description`` — first description cell, markdown stripped
      ``status_raw``  — the raw status cell value
    """
    rows: list[dict] = []
    text = path.read_text(encoding="utf-8")

    for line in text.splitlines():
        line = line.rstrip()
        if not line.startswith("|"):
            continue
        cells = _parse_row_cells(line)
        if len(cells) < 4:
            continue
        if _is_separator_row(cells):
            continue
        if _is_header_row(cells):
            continue

        row_id = _strip_markdown(cells[0])
        if not _looks_like_id(row_id):
            continue

        description = _strip_markdown(cells[1]) if len(cells) > 1 else ""

        # Find the status cell: search all cells for one containing "done"
        status_raw = ""
        for cell in cells:
            clean = _strip_markdown(cell)
            if _STATUS_DONE_RE.search(clean):
                status_raw = clean
                break
        if not status_raw:
            continue  # not a done row

        rows.append(
            {
                "id": row_id,
                "description": description,
                "status_raw": status_raw,
            }
        )
    return rows


# ---------------------------------------------------------------------------
# User-visibility filter
# ---------------------------------------------------------------------------


def is_user_visible(row: dict) -> bool:
    """Return True when the row is likely to ship a user-visible feature."""
    row_id = row["id"].lower()
    if row_id in ALWAYS_SKIP_IDS:
        return False
    desc = row["description"]
    for pattern in SKIP_DESCRIPTION_PATTERNS:
        if pattern.search(desc):
            return False
    return True


# ---------------------------------------------------------------------------
# Docs-coverage check
# ---------------------------------------------------------------------------


def _load_docs_content(dirs: list[Path]) -> dict[str, str]:
    """Return mapping of relative posix path → lower-cased file content."""
    content: dict[str, str] = {}
    for d in dirs:
        if not d.is_dir():
            continue
        for md in sorted(d.rglob("*.md")):
            try:
                content[md.relative_to(REPO_ROOT).as_posix()] = (
                    md.read_text(encoding="utf-8").lower()
                )
            except (OSError, UnicodeDecodeError):
                pass
    return content


def _id_variants(row_id: str) -> list[str]:
    """Generate search variants for a row ID.

    Examples:
      ``A5a``     → ``["a5a", "a5-a"]``
      ``DOCS-3A5`` → ``["docs-3a5"]``
      ``FR1b``    → ``["fr1b", "fr1-b"]``
    """
    lower = row_id.lower()
    variants: set[str] = {lower}
    # Insert hyphen before a trailing letter suffix: "a5a" → "a5-a"
    m = re.match(r"^([a-z]+\d+)([a-z]+)$", lower)
    if m:
        variants.add(f"{m.group(1)}-{m.group(2)}")
    return list(variants)


def find_covering_doc(row_id: str, docs: dict[str, str]) -> str | None:
    """Return the first doc path that mentions row_id (any variant), or None."""
    variants = _id_variants(row_id)
    for path, text in docs.items():
        for v in variants:
            if v in text:
                return path
    return None


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

_GAP_COLS = ("Row ID", "Description (first 60 chars)", "Missing doc section")
_TRUNC = 60


def _trunc(s: str, n: int = _TRUNC) -> str:
    return s[:n] + "…" if len(s) > n else s


def print_markdown_table(gaps: list[dict]) -> None:
    col_id = max(len(_GAP_COLS[0]), max((len(g["id"]) for g in gaps), default=0))
    col_desc = max(len(_GAP_COLS[1]), _TRUNC + 1)
    col_miss = max(len(_GAP_COLS[2]), 50)

    def row_line(id_: str, desc: str, miss: str) -> str:
        return f"| {id_:<{col_id}} | {desc:<{col_desc}} | {miss:<{col_miss}} |"

    print(row_line(*_GAP_COLS))
    print(f"| {'-' * col_id} | {'-' * col_desc} | {'-' * col_miss} |")
    for g in gaps:
        print(row_line(g["id"], _trunc(g["description"]), g["missing"]))


def print_csv_table(gaps: list[dict]) -> None:
    writer = csv.DictWriter(
        sys.stdout,
        fieldnames=["row_id", "description", "missing_doc"],
        lineterminator="\n",
    )
    writer.writeheader()
    for g in gaps:
        writer.writerow(
            {
                "row_id": g["id"],
                "description": g["description"],
                "missing_doc": g["missing"],
            }
        )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument(
        "--no-fail",
        action="store_true",
        help="Always exit 0 — CI warning-only mode.",
    )
    ap.add_argument(
        "--csv",
        action="store_true",
        help="Emit CSV instead of a markdown table.",
    )
    ap.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress the summary line; only print the gap table.",
    )
    args = ap.parse_args()

    # 1. Parse backlog
    if not BACKLOG_PATH.exists():
        print(f"ERROR: backlog not found at {BACKLOG_PATH}", file=sys.stderr)
        return 1 if not args.no_fail else 0

    all_done = parse_backlog(BACKLOG_PATH)
    candidates = [r for r in all_done if is_user_visible(r)]

    # 2. Load docs
    docs = _load_docs_content([DOCS_HELP_DIR, DOCS_REF_DIR])

    # 3. Find gaps
    gaps: list[dict] = []
    for row in candidates:
        covering = find_covering_doc(row["id"], docs)
        if covering is None:
            gaps.append(
                {
                    "id": row["id"],
                    "description": row["description"],
                    "missing": "no mention in docs/help/ or docs/reference/",
                }
            )

    # 4. Report
    if gaps:
        if args.csv:
            print_csv_table(gaps)
        else:
            print_markdown_table(gaps)

    if not args.quiet:
        total_checked = len(candidates)
        total_covered = total_checked - len(gaps)
        print(
            f"\n{total_covered}/{total_checked} user-visible done rows covered. "
            f"{len(gaps)} gap(s) found.",
            file=sys.stderr,
        )

    if gaps and not args.no_fail:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
