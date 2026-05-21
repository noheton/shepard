"""analyze-confluence-export.py — discover and optionally fetch missing files.

Run this BEFORE import-confluence.py to understand what will be imported and
what will be flagged as non-importable (Jira links, NAS paths, chart macros, etc.).

Supports Confluence Data Center / Server HTML space exports (ZIP or extracted dir).
Does not support XML backup exports (different format — see aidocs/82).

Output: a human-readable discovery report. Use --report to write it to a file.

Fetch mode (--fetch-dir):
  After analysis, attempts to copy every NAS/file-server path found in the pages
  into a local output directory, using the current Windows session credentials
  (UNC paths like \\\\server\\share\\file are opened with standard file I/O — no
  explicit password needed when run from a machine that already has SMB access).

  Results are written to --fetch-log (default: fetch-manifest.json) — a JSON
  list of {source, dest, sha256, status} records. Duplicate content (same SHA-256)
  is stored once; subsequent occurrences reference the first copy.

Pure stdlib. No extra dependencies.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
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

    nas_paths_all: set[str] = set()  # full set for fetch mode

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
            if kind == "nas_or_file_server":
                nas_paths_all.add(href)
                if len(nas_paths) < 10:
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
        "nas_paths_all": sorted(nas_paths_all),
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


# ── Fetch missing files ──────────────────────────────────────────────────────

def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_missing_files(
    nas_paths: list[str],
    output_dir: Path,
    log_path: Path,
) -> list[dict]:
    """Copy each NAS/UNC path into output_dir using the current OS session credentials.

    On Windows this transparently uses the logged-in user's SMB/NTLM token for
    UNC paths (\\\\server\\share\\...). On Linux with a mounted share the path
    must be accessible via the filesystem.

    Deduplication: files with identical SHA-256 are stored once; subsequent
    entries reference the first copy's dest path via ``duplicate_of``.

    Returns a list of manifest records and writes them to log_path (JSON).
    """
    output_dir.mkdir(parents=True, exist_ok=True)

    # Load existing manifest to support resumable runs
    existing: dict[str, dict] = {}
    if log_path.exists():
        try:
            for entry in json.loads(log_path.read_text(encoding="utf-8")):
                existing[entry["source"]] = entry
        except Exception:
            pass

    seen_hashes: dict[str, str] = {}  # sha256 → dest path (for dedup)
    for entry in existing.values():
        if entry.get("sha256") and entry.get("dest") and entry.get("status") == "ok":
            seen_hashes[entry["sha256"]] = entry["dest"]

    manifest: list[dict] = list(existing.values())
    already_done = {e["source"] for e in manifest}

    for raw_path in nas_paths:
        if raw_path in already_done:
            continue

        record: dict = {"source": raw_path, "dest": None, "sha256": None, "status": None, "error": None}

        # Normalise: on Windows, backslash UNC works natively; convert forward-slash NAS paths
        try:
            src = Path(raw_path)
        except Exception as exc:
            record["status"] = "error"
            record["error"] = f"invalid path: {exc}"
            manifest.append(record)
            continue

        if not src.exists():
            record["status"] = "not_found"
            record["error"] = f"path not accessible: {raw_path}"
            print(f"  NOT FOUND  {raw_path}", file=sys.stderr)
            manifest.append(record)
            continue

        if src.is_dir():
            record["status"] = "skipped_dir"
            record["error"] = "source is a directory, not a file"
            manifest.append(record)
            continue

        # Compute hash of source
        try:
            digest = _sha256(src)
        except OSError as exc:
            record["status"] = "error"
            record["error"] = f"cannot read: {exc}"
            manifest.append(record)
            continue

        # Deduplicate by content
        if digest in seen_hashes:
            record["sha256"] = digest
            record["dest"] = seen_hashes[digest]
            record["status"] = "duplicate"
            record["error"] = f"duplicate of {seen_hashes[digest]}"
            manifest.append(record)
            continue

        # Choose destination filename — sanitise the source path to a flat name
        safe_name = re.sub(r"[\\/:*?\"<>|]", "_", raw_path).strip("_")[:200]
        dest = output_dir / safe_name
        # Avoid collisions on filenames that differ only in non-safe chars
        counter = 0
        base = dest
        while dest.exists():
            counter += 1
            dest = base.parent / f"{base.stem}_{counter}{base.suffix}"

        try:
            shutil.copy2(src, dest)
        except OSError as exc:
            record["status"] = "error"
            record["error"] = f"copy failed: {exc}"
            manifest.append(record)
            continue

        record["sha256"] = digest
        record["dest"] = str(dest)
        record["status"] = "ok"
        seen_hashes[digest] = str(dest)
        print(f"  OK  {raw_path}  →  {dest.name}")
        manifest.append(record)

    log_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    return manifest


def _resolve_path(raw_path: str, search_dirs: list[Path]) -> Path | None:
    """Try to resolve a NAS path to an accessible file.

    Strategy:
    1. Try the path verbatim (works on Windows for UNC, or Linux with mount).
    2. If not found, extract just the filename and search each search_dir
       recursively for a file with that name (case-insensitive on Windows).
    3. Return the first match, or None.
    """
    try:
        p = Path(raw_path)
        if p.exists() and p.is_file():
            return p
    except Exception:
        pass

    # Fall back: search by filename
    filename = Path(raw_path).name
    if not filename:
        return None
    for sdir in search_dirs:
        if not sdir.is_dir():
            continue
        for candidate in sdir.rglob("*"):
            if candidate.is_file() and candidate.name.lower() == filename.lower():
                return candidate
    return None


def _fetch_with_search(
    nas_paths: list[str],
    output_dir: Path,
    log_path: Path,
    search_dirs: list[Path],
) -> list[dict]:
    """Like fetch_missing_files but also searches fallback dirs when a path is not found."""
    output_dir.mkdir(parents=True, exist_ok=True)

    existing: dict[str, dict] = {}
    if log_path.exists():
        try:
            for entry in json.loads(log_path.read_text(encoding="utf-8")):
                existing[entry["source"]] = entry
        except Exception:
            pass

    seen_hashes: dict[str, str] = {}
    for entry in existing.values():
        if entry.get("sha256") and entry.get("dest") and entry.get("status") == "ok":
            seen_hashes[entry["sha256"]] = entry["dest"]

    manifest: list[dict] = list(existing.values())
    already_done = {e["source"] for e in manifest}

    for raw_path in nas_paths:
        if raw_path in already_done:
            continue

        record: dict = {
            "source": raw_path, "resolved": None,
            "dest": None, "sha256": None, "status": None, "error": None,
        }

        src = _resolve_path(raw_path, search_dirs)
        if src is None:
            record["status"] = "not_found"
            record["error"] = f"not accessible and not found in search dirs"
            print(f"  NOT FOUND  {raw_path}", file=sys.stderr)
            manifest.append(record)
            continue

        record["resolved"] = str(src)
        if str(src) != raw_path:
            print(f"  FOUND (via search)  {raw_path}  →  {src}")

        if src.is_dir():
            record["status"] = "skipped_dir"
            record["error"] = "source is a directory"
            manifest.append(record)
            continue

        try:
            digest = _sha256(src)
        except OSError as exc:
            record["status"] = "error"
            record["error"] = f"cannot read: {exc}"
            manifest.append(record)
            continue

        if digest in seen_hashes:
            record["sha256"] = digest
            record["dest"] = seen_hashes[digest]
            record["status"] = "duplicate"
            record["error"] = f"duplicate of {seen_hashes[digest]}"
            manifest.append(record)
            continue

        safe_name = re.sub(r"[\\/:*?\"<>|]", "_", raw_path).strip("_")[:200]
        dest = output_dir / safe_name
        counter = 0
        base = dest
        while dest.exists():
            counter += 1
            dest = base.parent / f"{base.stem}_{counter}{base.suffix}"

        try:
            shutil.copy2(src, dest)
        except OSError as exc:
            record["status"] = "error"
            record["error"] = f"copy failed: {exc}"
            manifest.append(record)
            continue

        record["sha256"] = digest
        record["dest"] = str(dest)
        record["status"] = "ok"
        seen_hashes[digest] = str(dest)
        print(f"  OK  {raw_path}  →  {dest.name}")
        manifest.append(record)

    log_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    return manifest


def render_fetch_summary(manifest: list[dict]) -> str:
    counts: Counter = Counter(e["status"] for e in manifest)
    total_bytes = 0
    for e in manifest:
        if e.get("dest") and e.get("status") == "ok":
            try:
                total_bytes += Path(e["dest"]).stat().st_size
            except OSError:
                pass

    lines = [
        "",
        "---",
        "",
        "## Fetch results",
        "",
        f"| Status | Count |",
        f"|---|---|",
    ]
    for status, count in sorted(counts.items()):
        lines.append(f"| `{status}` | {count} |")
    lines += [
        f"",
        f"Total fetched size: {_fmt(total_bytes)}",
        f"",
        f"**Not found / inaccessible** ({counts.get('not_found', 0)} paths):",
    ]
    for e in manifest:
        if e["status"] == "not_found":
            lines.append(f"  - `{e['source']}`")
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
    ap.add_argument(
        "--fetch-dir", metavar="DIR",
        help="Fetch NAS/file-server paths found in the export into this local directory "
             "using the current OS session credentials (Windows auth / mounted share). "
             "Creates the directory if it does not exist.",
    )
    ap.add_argument(
        "--fetch-log", metavar="PATH", default="fetch-manifest.json",
        help="JSON manifest file recording every fetch attempt (default: fetch-manifest.json). "
             "Supports resumable runs — already-fetched paths are skipped.",
    )
    ap.add_argument(
        "--fetch-search-dirs", metavar="DIR", nargs="*",
        help="Additional directories to search when a NAS path is not directly accessible. "
             "The script will try each search dir as an alternative root for the filename.",
    )
    args = ap.parse_args(argv)

    rep = analyze(Path(args.source))
    report = render_report(rep)
    print(report)

    if args.fetch_dir:
        nas_paths = rep.get("nas_paths_all", [])
        if not nas_paths:
            print("\nNo NAS/file-server paths found — nothing to fetch.", flush=True)
        else:
            print(f"\nFetching {len(nas_paths)} NAS/file-server paths into {args.fetch_dir} ...", flush=True)
            search_dirs = [Path(d) for d in (args.fetch_search_dirs or [])]
            manifest = _fetch_with_search(
                nas_paths=nas_paths,
                output_dir=Path(args.fetch_dir),
                log_path=Path(args.fetch_log),
                search_dirs=search_dirs,
            )
            fetch_summary = render_fetch_summary(manifest)
            report += fetch_summary
            print(fetch_summary, flush=True)
            print(f"\nFetch manifest written to {args.fetch_log}", flush=True)

    if args.report:
        Path(args.report).write_text(report, encoding="utf-8")
        print(f"\nReport written to {args.report}", flush=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
