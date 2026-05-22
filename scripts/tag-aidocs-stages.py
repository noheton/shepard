#!/usr/bin/env python3
"""One-shot bulk tagger for `aidocs/*.md` lifecycle stages.

Idempotent: skips any file that already has YAML front-matter with a
`stage:` field. Otherwise prepends a front-matter block based on a
heuristic that maps existing `**Status.** ...` lines + path location to
one of the stages defined in `aidocs/00-doc-stages.md`.

Run once:

    python3 scripts/tag-aidocs-stages.py

Then regenerate the index:

    python3 scripts/regenerate-doc-stage-index.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
AIDOCS_DIR = REPO_ROOT / "aidocs"
TODAY = "2026-05-23"

FRONT_MATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n", re.DOTALL)
STATUS_RE = re.compile(r"^\*\*Status[.:]\*\*\s*(.+?)\s*$", re.MULTILINE)


# Path-based overrides — these win over Status-line parsing.
PATH_OVERRIDES: dict[str, str] = {
    # Root ledger / living docs.
    "aidocs/00-index.md": "deployed",
    "aidocs/01-doc-stage-index.md": "deployed",  # auto-generated; deployed-as-index
    "aidocs/00-doc-stages.md": "deployed",
    "aidocs/16-dispatcher-backlog.md": "deployed",
    "aidocs/34-upstream-upgrade-path.md": "deployed",
    "aidocs/40-ecosystem.md": "deployed",
    "aidocs/41-synergy-sweep.md": "deployed",
    "aidocs/42-vision.md": "deployed",
    "aidocs/44-fork-vs-upstream-feature-matrix.md": "deployed",
    "aidocs/99-api-annoyances.md": "deployed",
    "aidocs/100-ui-annoyances.md": "deployed",
    "aidocs/97-shepard-pipelines.md": "deployed",
    "aidocs/98-thesis-perspective.md": "idea",
    "aidocs/roadmap.md": "deployed",
    "aidocs/handover-2026-05-19.md": "deployed",
    "aidocs/case-study-2026-05-19.md": "deployed",

    # Truly raw note files.
    "aidocs/input/input_raw.md": "fragment",

    # Known shipped/locked specs.
    "aidocs/integrations/93-mffd-import-v15-requirements.md": "tests-implemented",
}


def classify_by_path(rel_path: str) -> str | None:
    """Return a stage if the path forces one, else None."""
    if rel_path in PATH_OVERRIDES:
        return PATH_OVERRIDES[rel_path]
    # agent-findings/*.md ARE the persona audit reports.
    if rel_path.startswith("aidocs/agent-findings/"):
        return "audited-by-personas"
    # Anything under archive/ is decommissioned.
    if rel_path.startswith("aidocs/archive/"):
        return "decommissioned"
    return None


def classify_by_status(status_text: str) -> str:
    """Map a free-text Status line to a stage token."""
    s = status_text.lower()

    # Shipped / live signals (check first — strongest).
    live_markers = [
        "**live", " live.", "live.", "live  ", "live ",
        "living document", "living design",
        "shipped", "landed", "implemented at", "active standard",
        "live reference",
    ]
    for marker in live_markers:
        if marker in s:
            return "deployed"

    # ADR-style "accepted" => decision made, feature defined.
    if "accepted" in s:
        return "feature-defined"

    if "requirements locked" in s or "requirements" in s and "locked" in s:
        return "feature-defined"

    # Research / ideas / sketches.
    if any(t in s for t in (
        "research direction", "research snapshot", "concept sketch",
        "personal-perspective", "point-in-time", "strategic synthesis",
        "discussion draft", "proposal", "concept survey",
    )):
        return "idea"

    if "concept" in s:
        return "concept"

    # Most Design variants — feature is defined.
    if any(t in s for t in (
        "design", "designed", "implementation design", "design doc",
        "design draft", "design done", "design — ", "design --",
        "design,", "design.", "design:", "queued",
    )):
        return "feature-defined"

    return "feature-defined"  # safe default for status-bearing docs


def classify(rel_path: str, text: str) -> str:
    forced = classify_by_path(rel_path)
    if forced:
        return forced

    # First Status line wins.
    m = STATUS_RE.search(text)
    if m:
        # Skip the "Status legend" / "Status filter" / "Status vocabulary"
        # variants — they describe in-doc conventions, not the doc itself.
        first = m.group(1).strip()
        if first.lower().startswith(("legend", "filter", "vocabulary")):
            # Look for a *real* status line further down.
            for further in STATUS_RE.finditer(text):
                cand = further.group(1).strip()
                if not cand.lower().startswith(("legend", "filter", "vocabulary")):
                    return classify_by_status(cand)
            return "feature-defined"
        return classify_by_status(first)

    # No Status line → fragment by default (signals "needs owner review").
    return "fragment"


def already_tagged(text: str) -> bool:
    fm = FRONT_MATTER_RE.match(text)
    if not fm:
        return False
    return "stage:" in fm.group(1)


def merge_front_matter(text: str, stage: str) -> str:
    """If file already has front-matter (no `stage:`), inject stage in-place.
    Otherwise prepend a new block."""
    fm = FRONT_MATTER_RE.match(text)
    block = (
        f"---\n"
        f"stage: {stage}\n"
        f"last-stage-change: {TODAY}\n"
        f"---\n\n"
    )
    if fm:
        # Merge: insert stage + last-stage-change before the closing `---`.
        existing_block = fm.group(1).rstrip()
        new_block = (
            f"---\n"
            f"{existing_block}\n"
            f"stage: {stage}\n"
            f"last-stage-change: {TODAY}\n"
            f"---\n"
        )
        return new_block + text[fm.end():]
    return block + text


def main() -> int:
    changed = 0
    skipped = 0
    by_stage: dict[str, int] = {}

    for path in sorted(AIDOCS_DIR.rglob("*.md")):
        rel = path.relative_to(REPO_ROOT).as_posix()
        text = path.read_text(encoding="utf-8")

        if already_tagged(text):
            # Still count for histogram.
            fm = FRONT_MATTER_RE.match(text)
            assert fm  # already_tagged guarantees this
            for line in fm.group(1).splitlines():
                if line.startswith("stage:"):
                    existing_stage = line.split(":", 1)[1].strip()
                    by_stage[existing_stage] = by_stage.get(existing_stage, 0) + 1
                    break
            skipped += 1
            continue

        stage = classify(rel, text)
        by_stage[stage] = by_stage.get(stage, 0) + 1
        new_text = merge_front_matter(text, stage)
        path.write_text(new_text, encoding="utf-8")
        changed += 1
        print(f"  {stage:24s} {rel}")

    print()
    print(f"Tagged: {changed}  Skipped (already tagged): {skipped}")
    print("Histogram:")
    for k in sorted(by_stage):
        print(f"  {k:24s} {by_stage[k]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
