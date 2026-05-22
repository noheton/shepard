#!/usr/bin/env python3
"""Regenerate aidocs/01-doc-stage-index.md from front-matter on every aidocs/*.md.

Walks the aidocs/ tree, parses YAML-style front-matter (the leading
`---` ... `---` block) for `stage:` and `last-stage-change:`, then emits a
grouped index table to `aidocs/01-doc-stage-index.md`.

Stdlib only. Idempotent. Run after every stage flip:

    python3 scripts/regenerate-doc-stage-index.py

The canonical taxonomy lives in `aidocs/00-doc-stages.md`.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
AIDOCS_DIR = REPO_ROOT / "aidocs"
INDEX_PATH = AIDOCS_DIR / "01-doc-stage-index.md"

# Canonical stage order (matches aidocs/00-doc-stages.md).
STAGE_ORDER = [
    "fragment",
    "concept",
    "idea",
    "feature-defined",
    "audited-by-personas",
    "feedback-implemented",
    "tests-implemented",
    "deployed",
    "decommissioned",
]

# `upgrade-vX:vY` is a parallel overlay band; it shows up as its own group
# when used standalone, but more commonly co-exists with a main stage.
UPGRADE_RE = re.compile(r"^upgrade-v[\w.\-]+:v[\w.\-]+$")

FRONT_MATTER_RE = re.compile(
    r"\A---\s*\n(.*?)\n---\s*(?:\n|$)", re.DOTALL
)

# Permissive YAML subset: `key: value` lines. We never need nested YAML
# in this front-matter shape.
KV_RE = re.compile(r"^([A-Za-z0-9_\-]+):\s*(.*?)\s*$")


def parse_front_matter(text: str) -> dict[str, str]:
    """Return a dict of front-matter keys, or {} if no front-matter."""
    match = FRONT_MATTER_RE.match(text)
    if not match:
        return {}
    block = match.group(1)
    out: dict[str, str] = {}
    for line in block.splitlines():
        line = line.rstrip()
        if not line or line.startswith("#"):
            continue
        m = KV_RE.match(line)
        if m:
            out[m.group(1)] = m.group(2).strip().strip('"').strip("'")
    return out


def first_heading(text: str) -> str:
    """Extract the first H1 heading after front-matter; fall back to filename."""
    body = FRONT_MATTER_RE.sub("", text, count=1)
    for line in body.splitlines():
        line = line.strip()
        if line.startswith("# "):
            return line[2:].strip()
    return ""


def git_last_touched(path: Path) -> str:
    """Return `YYYY-MM-DD` of the last commit that touched the file, or `—`."""
    try:
        out = subprocess.run(
            ["git", "log", "-1", "--format=%ad", "--date=short", "--", str(path)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        date = out.stdout.strip()
        return date if date else "—"
    except (OSError, subprocess.SubprocessError):
        return "—"


def collect_docs() -> list[dict]:
    """Walk aidocs/, return one row per md file."""
    rows: list[dict] = []
    for path in sorted(AIDOCS_DIR.rglob("*.md")):
        if path == INDEX_PATH:
            continue
        text = path.read_text(encoding="utf-8")
        fm = parse_front_matter(text)
        stage_raw = fm.get("stage", "").strip()
        stages = [s.strip() for s in stage_raw.split(",") if s.strip()] or ["UNTAGGED"]
        title = first_heading(text) or path.stem
        rel_path = path.relative_to(REPO_ROOT).as_posix()
        rows.append({
            "path": rel_path,
            "title": title,
            "stages": stages,
            "last_touched": git_last_touched(path),
            "last_stage_change": fm.get("last-stage-change", "—"),
        })
    return rows


def group_by_stage(rows: list[dict]) -> dict[str, list[dict]]:
    """Each doc appears once per stage token it carries."""
    grouped: dict[str, list[dict]] = {s: [] for s in STAGE_ORDER}
    grouped["UNTAGGED"] = []
    grouped["upgrade-overlay"] = []  # collects all upgrade-vX:vY tokens
    for row in rows:
        main_stages = [s for s in row["stages"] if s in STAGE_ORDER]
        upgrade_stages = [s for s in row["stages"] if UPGRADE_RE.match(s)]
        unknown = [
            s for s in row["stages"]
            if s not in STAGE_ORDER
            and not UPGRADE_RE.match(s)
            and s != "UNTAGGED"
        ]
        if not main_stages and not upgrade_stages and (unknown or "UNTAGGED" in row["stages"]):
            grouped["UNTAGGED"].append(row)
            continue
        for s in main_stages:
            grouped[s].append(row)
        for s in upgrade_stages:
            grouped["upgrade-overlay"].append({**row, "_upgrade_token": s})
    return grouped


def histogram(rows: list[dict]) -> dict[str, int]:
    """Count primary stage tokens (each doc counts once per stage token)."""
    counts: dict[str, int] = {}
    for row in rows:
        for s in row["stages"]:
            counts[s] = counts.get(s, 0) + 1
    return counts


def render_section(stage: str, items: list[dict]) -> list[str]:
    if not items:
        return []
    out = [f"## {stage} ({len(items)})", ""]
    out.append("| doc | title | last-stage-change | last-touched |")
    out.append("|---|---|---|---|")
    for row in sorted(items, key=lambda r: r["path"]):
        title = row["title"].replace("|", "\\|")
        extra = ""
        if "_upgrade_token" in row:
            extra = f" `{row['_upgrade_token']}`"
        out.append(
            f"| [`{row['path']}`]({_rel_link(row['path'])}) | {title}{extra} "
            f"| {row['last_stage_change']} | {row['last_touched']} |"
        )
    out.append("")
    return out


def _rel_link(rel_path: str) -> str:
    """Index lives at aidocs/01-doc-stage-index.md; strip the leading 'aidocs/'."""
    if rel_path.startswith("aidocs/"):
        return rel_path[len("aidocs/"):]
    return "../" + rel_path


HEADER = """<!--
AUTO-GENERATED — DO NOT EDIT BY HAND.

Regenerate with:

    python3 scripts/regenerate-doc-stage-index.py

Inputs: every `aidocs/**/*.md` file's YAML front-matter `stage:` field.
Taxonomy: see `aidocs/00-doc-stages.md`.
-->

# aidocs — Doc stage index

This is the **stage-grouped index** of every `aidocs/*.md` design doc in
this fork. The canonical taxonomy is in
[`00-doc-stages.md`](00-doc-stages.md); each section below lists every doc
whose front-matter `stage:` token matches that stage.

A doc with `stage: deployed, upgrade-v5:v6` appears in both the `deployed`
section and the `upgrade-overlay` section.

**Companion ledgers:**

- `aidocs/34-upstream-upgrade-path.md` — upstream-admin-facing upgrade ledger
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — contributor-facing feature matrix
- `aidocs/16-dispatcher-backlog.md` — actionable backlog rows

"""


def render_index(rows: list[dict]) -> str:
    grouped = group_by_stage(rows)
    parts = [HEADER]

    # Histogram summary up top.
    hist = histogram(rows)
    parts.append("## Histogram\n")
    parts.append("| stage | count |")
    parts.append("|---|---|")
    for stage in STAGE_ORDER:
        parts.append(f"| `{stage}` | {hist.get(stage, 0)} |")
    upgrade_total = sum(
        v for k, v in hist.items() if UPGRADE_RE.match(k)
    )
    parts.append(f"| `upgrade-vX:vY` (overlay) | {upgrade_total} |")
    parts.append(f"| **total docs** | **{len(rows)}** |")
    parts.append(f"| **UNTAGGED** | **{len(grouped['UNTAGGED'])}** |")
    parts.append("")

    # UNTAGGED first so it's impossible to miss.
    if grouped["UNTAGGED"]:
        parts.append("## UNTAGGED (needs `stage:` front-matter)")
        parts.append("")
        parts.append("These files have no `stage:` field. Add one per the taxonomy "
                     "in `aidocs/00-doc-stages.md`.")
        parts.append("")
        parts.append("| doc | title | last-touched |")
        parts.append("|---|---|---|")
        for row in sorted(grouped["UNTAGGED"], key=lambda r: r["path"]):
            title = row["title"].replace("|", "\\|")
            parts.append(
                f"| [`{row['path']}`]({_rel_link(row['path'])}) | {title} "
                f"| {row['last_touched']} |"
            )
        parts.append("")

    for stage in STAGE_ORDER:
        parts.extend(render_section(stage, grouped[stage]))

    if grouped["upgrade-overlay"]:
        parts.extend(render_section("upgrade-overlay", grouped["upgrade-overlay"]))

    return "\n".join(parts).rstrip() + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true",
                    help="Exit non-zero if the on-disk index doesn't match.")
    ap.add_argument("--stats", action="store_true",
                    help="Print the stage histogram to stdout and exit.")
    args = ap.parse_args()

    rows = collect_docs()

    if args.stats:
        hist = histogram(rows)
        print(f"Total docs: {len(rows)}")
        for stage in STAGE_ORDER:
            print(f"  {stage:24s} {hist.get(stage, 0):4d}")
        upgrade_total = sum(v for k, v in hist.items() if UPGRADE_RE.match(k))
        print(f"  {'upgrade-vX:vY (overlay)':24s} {upgrade_total:4d}")
        grouped = group_by_stage(rows)
        print(f"  {'UNTAGGED':24s} {len(grouped['UNTAGGED']):4d}")
        return 0

    rendered = render_index(rows)

    if args.check:
        existing = INDEX_PATH.read_text(encoding="utf-8") if INDEX_PATH.exists() else ""
        if existing != rendered:
            print(f"DRIFT: {INDEX_PATH} is out of date. Run without --check to update.",
                  file=sys.stderr)
            return 1
        print(f"OK: {INDEX_PATH} matches generated output.")
        return 0

    INDEX_PATH.write_text(rendered, encoding="utf-8")
    print(f"Wrote {INDEX_PATH} ({len(rows)} docs).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
