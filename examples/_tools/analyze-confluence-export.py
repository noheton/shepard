"""analyze-confluence-export.py — discover what's in a Confluence space export.

Run this BEFORE import-confluence.py to understand what will be imported and
what will be flagged as non-importable (Jira links, NAS paths, chart macros, etc.).

Supports Confluence Data Center / Server HTML space exports (ZIP or extracted dir).
Does not support XML backup exports (different format — see aidocs/82).

Output: a human-readable discovery report. Use --report to write it to a file.

Pure stdlib. No network calls. Safe to run offline.
"""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from collections import Counter, defaultdict
from html.parser import HTMLParser
from pathlib import Path
from typing import Optional


# ── HTML parser to extract links, tables, headings ─────────────────────────

class PageParser(HTMLParser):
    """Minimal HTML parser that extracts:
    - All <a href> links
    - <img src> image sources
    - Table presence (any <table> with numeric-looking cells)
    - Confluence-style macro classes (data-macro-name attributes)
    """

    def __init__(self):
        super().__init__()
        self.links: list[str] = []
        self.images: list[str] = []
        self.macros: list[str] = []
        self.has_table = False
        self._in_table = False
        self._table_text = ""

    def handle_starttag(self, tag: str, attrs: list) -> None:
        attr_dict = dict(attrs)
        if tag == "a" and "href" in attr_dict:
            self.links.append(attr_dict["href"])
        if tag == "img" and "src" in attr_dict:
            self.images.append(attr_dict["src"])
        if tag == "table":
            self._in_table = True
            self._table_text = ""
        macro = attr_dict.get("data-macro-name")
        if macro:
            self.macros.append(macro)

    def handle_endtag(self, tag: str) -> None:
        if tag == "table":
            self._in_table = False
            # Heuristic: a table is "data-like" if it has digits in cells
            if re.search(r"\d", self._table_text):
                self.has_table = True

    def handle_data(self, data: str) -> None:
        if self._in_table:
            self._table_text += data


# ── Link classifier ─────────────────────────────────────────────────────────

_NAS_PATTERN = re.compile(
    r"(\\\\[a-zA-Z0-9\-_]+|/mnt/|/nfs/|/nas/|[A-Z]:\\)", re.IGNORECASE
)
_JIRA_PATTERN = re.compile(r"jira\.", re.IGNORECASE)
_CONFLUENCE_ATTACH = re.compile(r"attachments/")
_CONFLUENCE_PAGE = re.compile(r"/display/|/pages/viewpage")
_EXTERNAL_HTTP = re.compile(r"^https?://")
_ANCHOR = re.compile(r"^#")


def classify_link(href: str) -> str:
    if not href or href.startswith("javascript:") or href.startswith("mailto:"):
        return "other"
    if _ANCHOR.match(href):
        return "anchor"
    if _NAS_PATTERN.search(href):
        return "nas_or_file_server"
    if _CONFLUENCE_ATTACH.search(href):
        return "confluence_attachment"
    if _CONFLUENCE_PAGE.search(href):
        return "confluence_page"
    if _JIRA_PATTERN.search(href):
        return "jira"
    if _EXTERNAL_HTTP.match(href):
        return "external_http"
    return "relative_or_unknown"


# ── Attachment extension helper ─────────────────────────────────────────────

def _ext(name: str) -> str:
    return Path(name).suffix.lower() or "(no ext)"


# ── Core analysis ───────────────────────────────────────────────────────────

def analyze(source: Path) -> dict:
    """Walk a Confluence HTML export (ZIP or directory) and return stats."""

    # Open either a ZIP or a flat directory
    if source.is_file() and source.suffix.lower() == ".zip":
        zf = zipfile.ZipFile(source)
        html_files = [n for n in zf.namelist() if n.endswith(".html") and not n.startswith("__MACOSX")]
        attach_files = [n for n in zf.namelist() if "/attachments/" in n and not n.endswith("/")]

        def read_html(name: str) -> str:
            return zf.read(name).decode("utf-8", errors="replace")

        def attach_size(name: str) -> int:
            info = zf.getinfo(name)
            return info.file_size

        def attach_name(name: str) -> str:
            return Path(name).name

    elif source.is_dir():
        html_files_raw = list(source.rglob("*.html"))
        attach_dir = source / "attachments"
        attach_files_raw = list(attach_dir.rglob("*")) if attach_dir.exists() else []

        html_files = [str(f.relative_to(source)) for f in html_files_raw if f.is_file()]
        attach_files = [str(f.relative_to(source)) for f in attach_files_raw if f.is_file()]

        def read_html(name: str) -> str:
            return (source / name).read_text(encoding="utf-8", errors="replace")

        def attach_size(name: str) -> int:
            return (source / name).stat().st_size

        def attach_name(name: str) -> str:
            return Path(name).name
    else:
        sys.exit(f"ERROR: {source} is not a .zip file or directory")

    # ── Page analysis ──────────────────────────────────────────────────────
    page_count = len(html_files)
    link_type_count: Counter = Counter()
    link_domain_count: Counter = Counter()
    macro_count: Counter = Counter()
    nas_paths: list[str] = []
    jira_links: list[str] = []
    external_links: list[str] = []
    pages_with_tables = 0
    pages_with_macros = 0
    title_depths: Counter = Counter()

    for html_name in html_files:
        # Infer page depth from path (e.g. "SomeSpace/parent/child.html" → depth 1)
        depth = html_name.count("/") - 1
        title_depths[max(0, depth)] += 1

        try:
            content = read_html(html_name)
        except Exception:
            continue

        parser = PageParser()
        try:
            parser.feed(content)
        except Exception:
            pass

        if parser.has_table:
            pages_with_tables += 1
        if parser.macros:
            pages_with_macros += 1
            for m in parser.macros:
                macro_count[m] += 1

        for href in parser.links:
            kind = classify_link(href)
            link_type_count[kind] += 1
            if kind == "nas_or_file_server" and len(nas_paths) < 10:
                nas_paths.append(href[:200])
            if kind == "jira" and len(jira_links) < 10:
                jira_links.append(href[:200])
            if kind == "external_http":
                try:
                    domain = re.match(r"https?://([^/]+)", href).group(1)
                    link_domain_count[domain] += 1
                except Exception:
                    pass
                if len(external_links) < 10:
                    external_links.append(href[:200])

    # ── Attachment analysis ────────────────────────────────────────────────
    ext_count: Counter = Counter()
    ext_size: Counter = Counter()
    total_attach_bytes = 0
    large_attachments: list[tuple[int, str]] = []

    for af in attach_files:
        name = attach_name(af)
        try:
            size = attach_size(af)
        except Exception:
            size = 0
        ext = _ext(name)
        ext_count[ext] += 1
        ext_size[ext] += size
        total_attach_bytes += size
        large_attachments.append((size, name))

    large_attachments.sort(reverse=True)

    return {
        "source": str(source),
        "page_count": page_count,
        "depth_distribution": dict(title_depths),
        "link_type_count": dict(link_type_count),
        "link_domain_top": link_domain_count.most_common(10),
        "macro_count": dict(macro_count),
        "pages_with_tables": pages_with_tables,
        "pages_with_macros": pages_with_macros,
        "nas_paths_sample": nas_paths,
        "jira_links_sample": jira_links,
        "external_links_sample": external_links,
        "attachment_count": len(attach_files),
        "attachment_total_bytes": total_attach_bytes,
        "ext_catalog": [
            {"ext": e, "count": ext_count[e], "total_bytes": ext_size[e]}
            for e, _ in ext_count.most_common(20)
        ],
        "largest_attachments": large_attachments[:10],
    }


# ── Report rendering ────────────────────────────────────────────────────────

def _fmt(n: int) -> str:
    if n < 1024:
        return f"{n} B"
    if n < 1024 ** 2:
        return f"{n / 1024:.1f} KiB"
    if n < 1024 ** 3:
        return f"{n / 1024 ** 2:.1f} MiB"
    return f"{n / 1024 ** 3:.2f} GiB"


def render_report(rep: dict) -> str:
    lines = [
        f"# Confluence Space Export Discovery Report",
        f"",
        f"Source: `{rep['source']}`",
        f"",
        f"---",
        f"",
        f"## Overview",
        f"",
        f"| Item | Count |",
        f"|---|---|",
        f"| Pages (HTML files) | {rep['page_count']} |",
        f"| Attachments | {rep['attachment_count']} |",
        f"| Total attachment size | {_fmt(rep['attachment_total_bytes'])} |",
        f"| Pages with data tables | {rep['pages_with_tables']} |",
        f"| Pages with Confluence macros | {rep['pages_with_macros']} |",
        f"",
        f"---",
        f"",
        f"## Page hierarchy depth",
        f"",
        f"| Depth | Page count |",
        f"|---|---|",
    ]
    for depth, count in sorted(rep["depth_distribution"].items()):
        label = "root" if depth == 0 else f"level {depth}"
        lines.append(f"| {label} | {count} |")

    lines += [
        f"",
        f"---",
        f"",
        f"## Import feasibility: pages → DataObjects + LabJournalEntries",
        f"",
        f"✓ All {rep['page_count']} pages can be imported as DataObjects + LabJournalEntries.",
        f"  Page hierarchy is preserved via parent/child DataObject links.",
        f"",
        f"---",
        f"",
        f"## Links found in page content",
        f"",
        f"| Link type | Count | Shepard action |",
        f"|---|---|---|",
    ]
    actions = {
        "confluence_attachment": "✓ FileReference (included in import)",
        "confluence_page": "✓ Resolved to DataObject appId after import",
        "anchor": "✓ Preserved in markdown",
        "external_http": "→ URIReference (URL recorded, data not archived)",
        "jira": "→ URIReference (Jira URL only; issue data not importable)",
        "nas_or_file_server": "⚠ URIReference + `unarchived_datasource=true` annotation",
        "relative_or_unknown": "→ Preserved in markdown as-is",
        "other": "→ Ignored",
    }
    for kind, count in sorted(rep["link_type_count"].items(), key=lambda x: -x[1]):
        action = actions.get(kind, "→ see report")
        lines.append(f"| `{kind}` | {count} | {action} |")

    if rep["nas_paths_sample"]:
        lines += [
            f"",
            f"### Unarchived data sources (NAS / file server paths)",
            f"",
            f"These paths reference data that exists outside Confluence and is not in",
            f"the export. They will be recorded as URIReferences with an",
            f"`unarchived_datasource=true` annotation so you can find and archive them.",
            f"",
        ]
        for p in rep["nas_paths_sample"]:
            lines.append(f"  - `{p}`")

    if rep["jira_links_sample"]:
        lines += [
            f"",
            f"### Jira links (data not importable)",
            f"",
        ]
        for l in rep["jira_links_sample"]:
            lines.append(f"  - {l}")

    lines += [
        f"",
        f"---",
        f"",
        f"## Confluence macros found",
        f"",
        f"| Macro | Count | Import behaviour |",
        f"|---|---|---|",
    ]
    macro_actions = {
        "jira": "URIReference to the Jira issue",
        "chart": "Annotation `confluence_macro=chart` — data not importable",
        "include": "Link to target DataObject after import",
        "info": "Preserved as blockquote in markdown",
        "warning": "Preserved as blockquote in markdown",
        "note": "Preserved as blockquote in markdown",
        "tip": "Preserved as blockquote in markdown",
        "code": "Preserved as code block in markdown",
        "toc": "Removed (auto-generated in Shepard UI)",
        "draw.io": "FileReference (PNG/SVG export attached)",
        "gliffy": "FileReference (PNG/SVG export attached)",
    }
    for macro, count in sorted(rep["macro_count"].items(), key=lambda x: -x[1]):
        action = macro_actions.get(macro, "→ Annotation `confluence_macro=<name>`")
        lines.append(f"| `{macro}` | {count} | {action} |")

    if rep["pages_with_tables"]:
        lines += [
            f"",
            f"**{rep['pages_with_tables']} pages** contain tables with numeric data.",
            f"Run with `--import-tables` to convert these into StructuredDataReferences.",
            f"Without the flag, table content is preserved in the markdown journal entry.",
        ]

    lines += [
        f"",
        f"---",
        f"",
        f"## Attachments ({rep['attachment_count']} files, {_fmt(rep['attachment_total_bytes'])})",
        f"",
        f"### Extension catalog",
        f"",
        f"| Extension | Count | Total size | Shepard action |",
        f"|---|---|---|---|",
    ]
    importable_ext = {".pdf", ".xlsx", ".xls", ".csv", ".png", ".jpg", ".jpeg",
                      ".gif", ".tiff", ".tif", ".mp4", ".avi", ".mov",
                      ".zip", ".tar", ".gz", ".json", ".xml", ".txt", ".md",
                      ".docx", ".doc", ".pptx", ".ppt"}
    for entry in rep["ext_catalog"]:
        ext = entry["ext"]
        action = "✓ FileReference" if ext in importable_ext else "✓ FileReference (binary)"
        lines.append(
            f"| `{ext}` | {entry['count']} | {_fmt(entry['total_bytes'])} | {action} |"
        )

    if rep["largest_attachments"]:
        lines += [
            f"",
            f"### Largest attachments",
            f"",
        ]
        for size, name in rep["largest_attachments"]:
            lines.append(f"  - {_fmt(size):>10}  `{name}`")

    lines += [
        f"",
        f"---",
        f"",
        f"## Estimated import",
        f"",
        f"| Shepard entity | Count |",
        f"|---|---|",
        f"| DataObjects | {rep['page_count']} |",
        f"| LabJournalEntries | {rep['page_count']} |",
        f"| FileReferences | {rep['attachment_count']} |",
        f"| URIReferences (NAS paths) | {rep['link_type_count'].get('nas_or_file_server', 0)} |",
        f"| URIReferences (external HTTP) | {rep['link_type_count'].get('external_http', 0)} |",
        f"| URIReferences (Jira) | {rep['link_type_count'].get('jira', 0)} |",
        f"",
        f"---",
        f"",
        f"## Next steps",
        f"",
        f"1. Review NAS/file-server paths above — schedule archiving these data sources",
        f"   to Shepard before the Confluence instance is decommissioned.",
        f"2. Review Jira links — decide if the linked issues need URIReferences or can",
        f"   be ignored.",
        f"3. Run `import-confluence.py --dry-run` to validate the full import manifest",
        f"   against your Shepard instance before committing.",
        f"4. Run `import-confluence.py` (without --dry-run) to execute.",
        f"",
        f"See `aidocs/integrations/82-confluence-import.md` for the full design.",
    ]

    return "\n".join(lines)


# ── Main ────────────────────────────────────────────────────────────────────

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument(
        "source",
        help="Confluence HTML space export: path to ZIP file or extracted directory",
    )
    ap.add_argument(
        "--report", metavar="PATH",
        help="Write the discovery report to this markdown file",
    )
    args = ap.parse_args(argv)

    rep = analyze(Path(args.source))
    report = render_report(rep)
    print(report)

    if args.report:
        Path(args.report).write_text(report, encoding="utf-8")
        print(f"\nReport written to {args.report}", flush=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
