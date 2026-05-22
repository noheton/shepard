#!/usr/bin/env python3
"""
build-traceability-index.py — Option A prototype of requirements traceability.

Reads:
  - `git log --format='%H%x09%s' --no-merges` — for Conventional Commits scopes
    (e.g. `feat(FS1b): ...`, `fix(KIP1d): ...`, `docs(TRACE): ...`)
  - `aidocs/16-dispatcher-backlog.md` — for the canonical catalogue of feature IDs
  - `aidocs/34-upstream-upgrade-path.md` — for explicit hash citations from rows
  - `aidocs/44-fork-vs-upstream-feature-matrix.md` — for per-ID status

Emits:
  - `docs/traceability.json` — machine-readable index
  - `docs/traceability.md` — human-readable report

Rationale: see `aidocs/platform/106-requirements-traceability.md` §3 Option A.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# Scope IDs look like FS1b, KIP1d, A0, TPL2a, V1COMPAT.0, U-01, NTF1, IMP1a, TRACE-A, etc.
# Accept dotted suffixes and dash-segmented IDs. Tolerate lowercase for ad-hoc IDs.
SCOPE_RE = re.compile(
    r"^(?P<type>feat|fix|chore|docs|refactor|test|perf|build|ci|revert)"
    r"(?:\((?P<scope>[A-Za-z0-9][A-Za-z0-9_./#-]*)\))?(?P<bang>!)?:\s*(?P<rest>.*)$"
)


@dataclass
class Commit:
    sha: str
    scope: str | None
    type: str
    bang: bool
    subject: str


@dataclass
class Requirement:
    rid: str
    title: str = ""
    status: str = "unknown"
    sources: list[str] = field(default_factory=list)
    commits: list[Commit] = field(default_factory=list)
    files_touched: set[str] = field(default_factory=set)

    def to_dict(self) -> dict:
        return {
            "id": self.rid,
            "title": self.title,
            "status": self.status,
            "sources": sorted(set(self.sources)),
            "commits": [
                {"sha": c.sha[:12], "type": c.type, "subject": c.subject, "breaking": c.bang}
                for c in self.commits
            ],
            "files_touched": sorted(self.files_touched),
            "commit_count": len(self.commits),
            "file_count": len(self.files_touched),
        }


def git_log() -> list[Commit]:
    out = subprocess.run(
        ["git", "log", "--format=%H%x09%s", "--no-merges"],
        cwd=REPO,
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    commits: list[Commit] = []
    for line in out.splitlines():
        if "\t" not in line:
            continue
        sha, subject = line.split("\t", 1)
        m = SCOPE_RE.match(subject)
        if not m:
            commits.append(Commit(sha=sha, scope=None, type="", bang=False, subject=subject))
            continue
        commits.append(
            Commit(
                sha=sha,
                scope=m.group("scope"),
                type=m.group("type"),
                bang=bool(m.group("bang")),
                subject=subject,
            )
        )
    return commits


def git_files_for(sha: str) -> list[str]:
    out = subprocess.run(
        ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", sha],
        cwd=REPO,
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    return [line for line in out.splitlines() if line.strip()]


def parse_backlog_ids(path: Path) -> dict[str, dict]:
    """Pull `| ID | item | ... |` rows from aidocs/16."""
    out: dict[str, dict] = {}
    if not path.exists():
        return out
    text = path.read_text(encoding="utf-8")
    # Match table rows where the first cell is an ID-like token; tolerant to bold + dashes.
    row_re = re.compile(
        r"^\|\s*\**(?P<rid>[A-Za-z][A-Za-z0-9_.#-]+)\**\s*\|\s*(?P<title>[^|]+?)\s*\|"
        r"[^|]*\|[^|]*\|\s*\**(?P<status>[a-zA-Z\- ]+)\**\s*\|",
        re.MULTILINE,
    )
    for m in row_re.finditer(text):
        rid = m.group("rid").strip()
        # Filter out obvious non-IDs (table-header strings, narrative)
        if rid.lower() in {"id", "table", "convention", "status"}:
            continue
        if len(rid) > 24:
            continue
        out[rid] = {
            "title": m.group("title").strip(),
            "status": m.group("status").strip().lower(),
            "source": "aidocs/16",
        }
    return out


def parse_upgrade_ledger_ids(path: Path) -> dict[str, str]:
    """aidocs/34 leads each row with a bold ID — capture them."""
    out: dict[str, str] = {}
    if not path.exists():
        return out
    text = path.read_text(encoding="utf-8")
    # Rows look like `| **ID — title** | ... |`
    row_re = re.compile(
        r"^\|\s*\*\*(?P<rid>[A-Za-z][A-Za-z0-9_./#-]+)(?:\s*[—-]\s*(?P<title>[^*|]+?))?\*\*",
        re.MULTILINE,
    )
    for m in row_re.finditer(text):
        rid = m.group("rid").strip()
        title = (m.group("title") or "").strip()
        out[rid] = title
    return out


def parse_feature_matrix_status(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not path.exists():
        return out
    text = path.read_text(encoding="utf-8")
    row_re = re.compile(
        r"^\|\s*\**(?P<rid>[A-Z][A-Za-z0-9_./#-]+)\**\s*\|\s*[^|]+?\s*\|\s*(?P<status>[^|]+?)\s*\|",
        re.MULTILINE,
    )
    for m in row_re.finditer(text):
        rid = m.group("rid").strip()
        status = m.group("status").strip()
        # Status cells in aidocs/44 often contain emoji + text. Keep the text.
        out[rid] = status
    return out


def build_index(limit_commits: int | None = None) -> dict[str, Requirement]:
    backlog = parse_backlog_ids(REPO / "aidocs" / "16-dispatcher-backlog.md")
    ledger = parse_upgrade_ledger_ids(REPO / "aidocs" / "34-upstream-upgrade-path.md")
    matrix = parse_feature_matrix_status(REPO / "aidocs" / "44-fork-vs-upstream-feature-matrix.md")

    reqs: dict[str, Requirement] = {}
    for rid, info in backlog.items():
        reqs[rid] = Requirement(rid=rid, title=info["title"], status=info["status"], sources=["aidocs/16"])
    for rid, title in ledger.items():
        r = reqs.setdefault(rid, Requirement(rid=rid))
        if title and not r.title:
            r.title = title
        if "aidocs/34" not in r.sources:
            r.sources.append("aidocs/34")
    for rid, status in matrix.items():
        r = reqs.setdefault(rid, Requirement(rid=rid))
        if "aidocs/44" not in r.sources:
            r.sources.append("aidocs/44")
        if status and r.status == "unknown":
            r.status = status

    commits = git_log()
    if limit_commits:
        commits = commits[:limit_commits]

    # Group commits by scope; tolerate scope IDs that don't match the catalogue
    # (track them under "_orphan").
    by_scope: dict[str, list[Commit]] = defaultdict(list)
    for c in commits:
        if c.scope:
            by_scope[c.scope].append(c)

    for scope, scope_commits in by_scope.items():
        r = reqs.setdefault(scope, Requirement(rid=scope, sources=["git-log"]))
        r.commits = scope_commits
        files: set[str] = set()
        for c in scope_commits:
            for f in git_files_for(c.sha):
                files.add(f)
        r.files_touched = files

    return reqs


def emit_json(reqs: dict[str, Requirement], dest: Path) -> None:
    payload = {
        "generated_by": "scripts/build-traceability-index.py",
        "doc": "aidocs/platform/106-requirements-traceability.md",
        "count": len(reqs),
        "with_commits": sum(1 for r in reqs.values() if r.commits),
        "requirements": {rid: r.to_dict() for rid, r in sorted(reqs.items())},
    }
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def emit_markdown(reqs: dict[str, Requirement], dest: Path) -> None:
    lines: list[str] = [
        "# Traceability index",
        "",
        "Auto-generated by `scripts/build-traceability-index.py`. Do not edit by hand.",
        "Design rationale: `aidocs/platform/106-requirements-traceability.md` §3 Option A.",
        "",
        f"**Catalogue size:** {len(reqs)} IDs (from aidocs/16 + aidocs/34 + aidocs/44 + git-log scopes).",
        f"**With commits:** {sum(1 for r in reqs.values() if r.commits)} IDs have at least one commit citing them.",
        "",
        "## Requirements with shipped code",
        "",
        "| ID | Status | Commits | Files touched | Title |",
        "|---|---|---|---|---|",
    ]
    for rid, r in sorted(reqs.items()):
        if not r.commits:
            continue
        title = (r.title or "").replace("|", "\\|")[:120]
        lines.append(
            f"| `{rid}` | {r.status} | {len(r.commits)} | {len(r.files_touched)} | {title} |"
        )

    lines += [
        "",
        "## Catalogue entries without shipped commits",
        "",
        "(IDs in the backlog or feature matrix that no commit scope cites.)",
        "",
        "| ID | Status | Sources | Title |",
        "|---|---|---|---|",
    ]
    for rid, r in sorted(reqs.items()):
        if r.commits:
            continue
        title = (r.title or "").replace("|", "\\|")[:120]
        sources = ", ".join(r.sources) or "—"
        lines.append(f"| `{rid}` | {r.status} | {sources} | {title} |")

    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    limit = None
    if "--limit" in argv:
        limit = int(argv[argv.index("--limit") + 1])

    reqs = build_index(limit_commits=limit)

    json_dest = REPO / "docs" / "reference" / "traceability.json"
    md_dest = REPO / "docs" / "reference" / "traceability.md"
    emit_json(reqs, json_dest)
    emit_markdown(reqs, md_dest)

    with_commits = sum(1 for r in reqs.values() if r.commits)
    print(f"Indexed {len(reqs)} requirement IDs; {with_commits} cite at least one commit.")
    print(f"Wrote {json_dest.relative_to(REPO)} and {md_dest.relative_to(REPO)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
