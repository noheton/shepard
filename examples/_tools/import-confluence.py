"""import-confluence.py — import a Confluence HTML space export into Shepard.

Run analyze-confluence-export.py first to understand what is in the export and
flag any NAS paths or Jira links that need manual attention.

## What gets imported

| Confluence entity          | Shepard target                                      |
|----------------------------|-----------------------------------------------------|
| Space                      | Collection (new, or appended to existing via --cid) |
| Page                       | DataObject + LabJournalEntry (HTML preserved)       |
| Nested page                | DataObject with parent link to its parent DataObject|
| Page labels                | DataObject attribute: confluence_label=<label>      |
| Page metadata              | DataObject attributes: confluence_author, _created  |
| Attachment (any type)      | Logged in report; upload via --with-attachments     |
| NAS / file-server link     | DataObject attribute: unarchived_datasource=<path>  |
| Jira link                  | DataObject attribute: confluence_jira=<url>         |
| Internal Confluence link   | DataObject attribute: confluence_link=<target>      |
| External HTTP link         | DataObject attribute: confluence_external=<url>     |

## What is NOT imported

- Page history / previous revisions (HTML export has current revision only)
- Inline Confluence comments (not in HTML export)
- Confluence user permissions (import applies default Collection permissions)
- Space permissions (same reason)
- Confluence macro data (chart data, Gliffy diagrams) — recorded as attributes

## Usage

    python import-confluence.py \\
        --zip   path/to/confluence-space-export.zip \\
        --host  https://shepard.nuclide.systems \\
        --token YOUR_KEYCLOAK_ACCESS_TOKEN \\
        [--cid  COLLECTION_APP_ID]   # append to existing; omit to create new
        [--dry-run]                   # validate manifest, no writes
        [--report import-report.md]

    # Resume after a crash — pages with a .done marker are skipped:
    python import-confluence.py [same args]

    # With file attachment upload (larger + slower):
    python import-confluence.py [...] --with-attachments --max-mb 50

Pure stdlib. No extra dependencies.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from collections import defaultdict
from html.parser import HTMLParser
from pathlib import Path
from typing import Optional


# ── Constants ─────────────────────────────────────────────────────────────────

_API_V1 = "shepard/api"
_COLLECTIONS = "collections"
_DATA_OBJECTS = "dataObjects"
_LAB_JOURNAL_ENTRIES = "labJournalEntries"


# ── Logging ───────────────────────────────────────────────────────────────────

def _log(msg: str, *, level: str = "INFO") -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {level:5} {msg}", flush=True)


def _warn(msg: str) -> None:
    _log(msg, level="WARN")


def _err(msg: str) -> None:
    _log(msg, level="ERROR")


# ── HTML parser (Confluence page content) ─────────────────────────────────────

class ConfluencePageParser(HTMLParser):
    """Extracts page title, body HTML, labels, metadata, and links."""

    def __init__(self) -> None:
        super().__init__()
        self.title: str = ""
        self.author: str = ""
        self.created: str = ""
        self.labels: list[str] = []
        self.links: list[tuple[str, str]] = []  # (href, text)
        self.macros: list[str] = []
        self._in_title = False
        self._in_breadcrumb = False
        self._depth = 0
        # Body tracking — we capture the main content div
        self._in_body = False
        self._body_depth = 0
        self._body_parts: list[str] = []
        self._current_link_href = ""
        self._current_link_text = ""
        self._in_link = False

    # ── SAX-style handlers ──────────────────────────────────────────────────

    def handle_starttag(self, tag: str, attrs: list) -> None:
        attr = dict(attrs)
        self._depth += 1

        # Main content container in Confluence HTML exports
        if tag == "div" and attr.get("id") in ("main-content", "content", "wiki-content"):
            self._in_body = True
            self._body_depth = self._depth

        if self._in_body:
            self._body_parts.append(self._build_open_tag(tag, attrs))

        if tag == "title":
            self._in_title = True

        if tag == "a" and "href" in attr:
            self._current_link_href = attr["href"]
            self._current_link_text = ""
            self._in_link = True

        macro = attr.get("data-macro-name")
        if macro:
            self.macros.append(macro)

        # Confluence metadata (DC / meta tags)
        if tag == "meta":
            name = attr.get("name", "")
            content = attr.get("content", "")
            if "author" in name.lower():
                self.author = content
            elif "created" in name.lower() or "date" in name.lower():
                self.created = content

    def handle_endtag(self, tag: str) -> None:
        if self._in_body:
            self._body_parts.append(f"</{tag}>")
            if self._depth == self._body_depth and tag == "div":
                self._in_body = False

        self._depth -= 1

        if tag == "title":
            self._in_title = False

        if tag == "a" and self._in_link:
            if self._current_link_href:
                self.links.append((self._current_link_href, self._current_link_text.strip()))
            self._in_link = False

    def handle_data(self, data: str) -> None:
        if self._in_body:
            self._body_parts.append(data)
        if self._in_title and not self.title:
            self.title = data.strip()
        if self._in_link:
            self._current_link_text += data

    # ── Helpers ─────────────────────────────────────────────────────────────

    @staticmethod
    def _build_open_tag(tag: str, attrs: list) -> str:
        parts = [f"<{tag}"]
        for k, v in attrs:
            if v is None:
                parts.append(f" {k}")
            else:
                escaped = v.replace('"', "&quot;")
                parts.append(f' {k}="{escaped}"')
        parts.append(">")
        return "".join(parts)

    @property
    def body_html(self) -> str:
        return "".join(self._body_parts).strip()


# ── Link classification ────────────────────────────────────────────────────────

_NAS = re.compile(r"(\\\\[a-zA-Z]|/mnt/|/nfs/|/nas/|[A-Z]:\\)", re.IGNORECASE)
_JIRA = re.compile(r"jira\.", re.IGNORECASE)
_ATTACH = re.compile(r"attachments/")
_CONF_PAGE = re.compile(r"/display/|/pages/viewpage")


def classify_link(href: str) -> str:
    if not href or href.startswith(("javascript:", "mailto:", "#")):
        return "other"
    if _NAS.search(href):
        return "nas"
    if _ATTACH.search(href):
        return "attachment"
    if _CONF_PAGE.search(href):
        return "confluence_page"
    if _JIRA.search(href):
        return "jira"
    if href.startswith("http"):
        return "external"
    return "relative"


# ── Confluence export reader ───────────────────────────────────────────────────

class ConfluenceExport:
    """Read a Confluence HTML space export (ZIP or extracted directory)."""

    def __init__(self, source: Path) -> None:
        self.source = source
        self._zip: Optional[zipfile.ZipFile] = None
        if source.is_file() and source.suffix.lower() == ".zip":
            self._zip = zipfile.ZipFile(source)
            self._html_names = [
                n for n in self._zip.namelist()
                if n.endswith(".html") and not n.startswith("__MACOSX")
                and "index.html" not in n
            ]
            self._attach_dir_prefix = "attachments/"
        elif source.is_dir():
            self._zip = None
            self._html_names = [
                str(f.relative_to(source))
                for f in source.rglob("*.html")
                if f.is_file() and "index" not in f.name
            ]
            self._attach_dir_prefix = str(source / "attachments") + "/"
        else:
            raise ValueError(f"Not a .zip or directory: {source}")

    def html_names(self) -> list[str]:
        return self._html_names

    def read_html(self, name: str) -> str:
        if self._zip:
            return self._zip.read(name).decode("utf-8", errors="replace")
        return (self.source / name).read_text(encoding="utf-8", errors="replace")

    def attachment_names(self) -> list[str]:
        if self._zip:
            return [
                n for n in self._zip.namelist()
                if "/attachments/" in n and not n.endswith("/")
            ]
        attach_dir = self.source / "attachments"
        if not attach_dir.exists():
            return []
        return [
            str(f.relative_to(self.source))
            for f in attach_dir.rglob("*") if f.is_file()
        ]

    def read_bytes(self, name: str) -> bytes:
        if self._zip:
            return self._zip.read(name)
        return (self.source / name).read_bytes()


# ── Manifest builder ───────────────────────────────────────────────────────────

def build_page_tree(export: ConfluenceExport) -> list[dict]:
    """Parse all HTML pages and return a flat list of page dicts in BFS order."""
    raw_pages: list[dict] = []

    for html_name in export.html_names():
        try:
            content = export.read_html(html_name)
        except Exception as exc:
            _warn(f"Cannot read {html_name}: {exc}")
            continue

        parser = ConfluencePageParser()
        try:
            parser.feed(content)
        except Exception:
            pass

        # Infer parent from path depth (Confluence HTML exports nest pages as dirs)
        parts = Path(html_name).parts
        parent_path = str(Path(*parts[:-1])) if len(parts) > 1 else None

        # Collect links by category
        nas_links = []
        jira_links = []
        external_links = []
        attachment_refs = []
        confluence_links = []

        for href, _text in parser.links:
            kind = classify_link(href)
            if kind == "nas":
                nas_links.append(href)
            elif kind == "jira":
                jira_links.append(href)
            elif kind == "external":
                external_links.append(href)
            elif kind == "attachment":
                attachment_refs.append(href)
            elif kind == "confluence_page":
                confluence_links.append(href)

        raw_pages.append({
            "html_name": html_name,
            "path": str(Path(html_name).with_suffix("")),
            "parent_path": parent_path,
            "title": parser.title or Path(html_name).stem,
            "author": parser.author,
            "created": parser.created,
            "labels": parser.labels,
            "body_html": parser.body_html or content,  # fallback: full page
            "macros": parser.macros,
            "nas_links": list(set(nas_links)),
            "jira_links": list(set(jira_links)),
            "external_links": list(set(external_links)),
            "attachment_refs": list(set(attachment_refs)),
            "confluence_links": list(set(confluence_links)),
        })

    # Sort so parents always come before children
    by_path = {p["path"]: p for p in raw_pages}
    ordered: list[dict] = []
    added: set[str] = set()

    def _add(page: dict) -> None:
        if page["path"] in added:
            return
        if page["parent_path"] and page["parent_path"] in by_path:
            _add(by_path[page["parent_path"]])
        ordered.append(page)
        added.add(page["path"])

    for p in raw_pages:
        _add(p)

    return ordered


def build_attributes(page: dict) -> dict[str, str]:
    attrs: dict[str, str] = {}
    if page["author"]:
        attrs["confluence_author"] = page["author"]
    if page["created"]:
        attrs["confluence_created"] = page["created"]
    for label in page["labels"]:
        attrs[f"confluence_label_{label}"] = label
    for macro in set(page["macros"]):
        attrs[f"confluence_macro_{macro}"] = macro
    # NAS paths: flag as unarchived data sources (first 3)
    for i, path in enumerate(page["nas_links"][:3]):
        attrs[f"unarchived_datasource_{i}"] = path
    if len(page["nas_links"]) > 3:
        attrs["unarchived_datasource_count"] = str(len(page["nas_links"]))
    # Jira links (first 3)
    for i, url in enumerate(page["jira_links"][:3]):
        attrs[f"confluence_jira_{i}"] = url
    return attrs


# ── HTTP client ────────────────────────────────────────────────────────────────

class ShepardClient:
    """Minimal HTTP client for the Shepard v1 REST API."""

    def __init__(self, host: str, token: str) -> None:
        self.base = host.rstrip("/")
        self._token = token

    def _req(
        self,
        method: str,
        path: str,
        body: Optional[dict] = None,
        *,
        files: Optional[dict] = None,
        retries: int = 3,
    ) -> dict:
        url = f"{self.base}/{path.lstrip('/')}"
        data: Optional[bytes] = None
        headers = {"Authorization": f"Bearer {self._token}"}

        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
            headers["Accept"] = "application/json"

        for attempt in range(retries):
            try:
                req = urllib.request.Request(url, data=data, headers=headers, method=method)
                with urllib.request.urlopen(req, timeout=30) as resp:
                    raw = resp.read()
                    if raw:
                        return json.loads(raw)
                    return {}
            except urllib.error.HTTPError as exc:
                body_bytes = exc.read()
                if exc.code in (429, 502, 503, 504) and attempt < retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                raise RuntimeError(
                    f"HTTP {exc.code} {method} {url}: {body_bytes.decode(errors='replace')[:500]}"
                ) from exc
            except OSError as exc:
                if attempt < retries - 1:
                    time.sleep(2 ** attempt)
                    continue
                raise RuntimeError(f"Network error {method} {url}: {exc}") from exc

        raise RuntimeError(f"Gave up after {retries} attempts: {method} {url}")

    def get(self, path: str) -> dict:
        return self._req("GET", path)

    def post(self, path: str, body: dict) -> dict:
        return self._req("POST", path, body)

    # ── Collections ───────────────────────────────────────────────────────────

    def get_collection_by_appid(self, app_id: str) -> Optional[dict]:
        """Return a collection dict by appId (v2 endpoint), or None."""
        try:
            return self.get(f"v2/collections/{app_id}")
        except RuntimeError as exc:
            if "HTTP 404" in str(exc):
                return None
            raise

    def get_collection_by_id(self, coll_id: int) -> dict:
        return self.get(f"{_API_V1}/{_COLLECTIONS}/{coll_id}")

    def list_collections(self, name: Optional[str] = None) -> list[dict]:
        path = f"{_API_V1}/{_COLLECTIONS}"
        if name:
            path += f"?name={urllib.parse.quote(name)}"
        result = self.get(path)
        return result if isinstance(result, list) else result.get("content", [])

    def create_collection(self, name: str, description: str = "") -> dict:
        return self.post(f"{_API_V1}/{_COLLECTIONS}", {
            "name": name,
            "description": description,
        })

    # ── DataObjects ───────────────────────────────────────────────────────────

    def create_data_object(
        self,
        coll_id: int,
        name: str,
        *,
        description: str = "",
        attributes: Optional[dict] = None,
        parent_id: Optional[int] = None,
        predecessor_ids: Optional[list[int]] = None,
    ) -> dict:
        body: dict = {"name": name}
        if description:
            body["description"] = description
        if attributes:
            body["attributes"] = attributes
        if parent_id is not None:
            body["parentId"] = parent_id
        if predecessor_ids:
            body["predecessorIds"] = predecessor_ids
        return self.post(f"{_API_V1}/{_COLLECTIONS}/{coll_id}/{_DATA_OBJECTS}", body)

    # ── LabJournal ────────────────────────────────────────────────────────────

    def create_lab_journal(self, do_id: int, html_content: str) -> dict:
        return self.post(
            f"{_API_V1}/{_LAB_JOURNAL_ENTRIES}/?dataObjectId={do_id}",
            {"journalContent": html_content},
        )

    # ── Import validate (dry-run) ──────────────────────────────────────────────

    def validate_manifest(self, manifest: dict) -> dict:
        return self.post("v2/import/validate", manifest)


# ── Import state (resumable) ───────────────────────────────────────────────────

class ImportState:
    """Tracks which pages have been imported (via .done markers) for resumability."""

    def __init__(self, state_dir: Optional[Path]) -> None:
        self._dir = state_dir
        self._page_to_do_id: dict[str, int] = {}  # page path → numeric DataObject id
        if state_dir:
            state_dir.mkdir(parents=True, exist_ok=True)
            self._load()

    def _load(self) -> None:
        if self._dir is None:
            return
        index = self._dir / "index.json"
        if index.exists():
            try:
                self._page_to_do_id = json.loads(index.read_text())
            except Exception:
                pass

    def _save(self) -> None:
        if self._dir is None:
            return
        (self._dir / "index.json").write_text(json.dumps(self._page_to_do_id, indent=2))

    def is_done(self, page_path: str) -> bool:
        return page_path in self._page_to_do_id

    def mark_done(self, page_path: str, do_id: int) -> None:
        self._page_to_do_id[page_path] = do_id
        self._save()

    def get_do_id(self, page_path: str) -> Optional[int]:
        return self._page_to_do_id.get(page_path)


# ── Dry-run manifest generation ────────────────────────────────────────────────

def pages_to_manifest(pages: list[dict], collection_app_id: str) -> dict:
    """Convert parsed pages to an ImportManifestIO for dry-run validation."""
    data_objects = []
    for page in pages:
        attrs = build_attributes(page)
        parent_ref = None
        if page["parent_path"]:
            parent_ref = page["parent_path"]
        data_objects.append({
            "localRef": page["path"],
            "name": page["title"],
            "description": f"Imported from Confluence page: {page['html_name']}",
            "attributes": attrs,
            "parentRef": parent_ref,
            "predecessorRefs": [],
        })
    return {
        "collectionAppId": collection_app_id,
        "dataObjects": data_objects,
        "containers": [],
        "references": [],
        "agentContext": None,
    }


# ── Report builder ─────────────────────────────────────────────────────────────

def build_report(
    pages: list[dict],
    attachments: list[str],
    created_dos: list[dict],
    errors: list[str],
    dry_run: bool,
    commit_id: Optional[str] = None,
) -> str:
    lines = [
        "# Confluence Import Report",
        "",
        f"Mode: {'DRY-RUN (no writes)' if dry_run else 'EXECUTE'}",
        "",
        "## Summary",
        "",
        f"| Item | Count |",
        "|---|---|",
        f"| Pages found | {len(pages)} |",
        f"| Attachments found | {len(attachments)} |",
        f"| DataObjects {'would be' if dry_run else ''} created | {len(created_dos)} |",
        f"| Errors | {len(errors)} |",
    ]

    if commit_id:
        lines += ["", f"**Import plan commitId:** `{commit_id}`"]

    if errors:
        lines += ["", "## Errors", ""]
        for e in errors:
            lines.append(f"- {e}")

    # NAS paths across all pages
    all_nas: list[str] = []
    for p in pages:
        all_nas.extend(p.get("nas_links", []))
    if all_nas:
        lines += [
            "",
            "## Unarchived data sources (NAS / file-server paths)",
            "",
            "These paths were found in page content and are NOT imported.",
            "They are recorded as DataObject attributes (`unarchived_datasource_N`).",
            "",
        ]
        for path in sorted(set(all_nas))[:20]:
            lines.append(f"- `{path}`")
        if len(set(all_nas)) > 20:
            lines.append(f"- … and {len(set(all_nas)) - 20} more")

    # Jira links
    all_jira = sorted({url for p in pages for url in p.get("jira_links", [])})
    if all_jira:
        lines += ["", "## Jira links (recorded as attributes, not imported)", ""]
        for url in all_jira[:10]:
            lines.append(f"- {url}")
        if len(all_jira) > 10:
            lines.append(f"- … and {len(all_jira) - 10} more")

    # Attachments
    if attachments:
        lines += [
            "",
            "## Attachments (not imported — use --with-attachments to upload)",
            "",
            f"Total: {len(attachments)} files.",
        ]

    return "\n".join(lines)


# ── Execute import ─────────────────────────────────────────────────────────────

def execute_import(
    client: ShepardClient,
    pages: list[dict],
    coll_id: int,
    state: ImportState,
    *,
    dry_run: bool,
) -> tuple[list[dict], list[str]]:
    """Create DataObjects + LabJournalEntries in Shepard. Returns (created, errors)."""
    created = []
    errors = []
    total = len(pages)

    for i, page in enumerate(pages):
        path = page["path"]
        prefix = f"[{i+1}/{total}]"

        if state.is_done(path):
            _log(f"{prefix} SKIP (already done): {page['title']}")
            continue

        # Resolve parent DataObject id
        parent_do_id: Optional[int] = None
        if page["parent_path"]:
            parent_do_id = state.get_do_id(page["parent_path"])
            if parent_do_id is None:
                _warn(f"{prefix} Parent not found for {path!r} — creating as root")

        attrs = build_attributes(page)

        if dry_run:
            _log(f"{prefix} DRY-RUN: would create DO '{page['title']}' parent={parent_do_id}")
            fake_id = -(i + 1)
            state.mark_done(path, fake_id)
            created.append({"path": path, "title": page["title"], "id": fake_id})
            continue

        # Create DataObject
        try:
            do = client.create_data_object(
                coll_id,
                page["title"],
                description=f"Imported from Confluence: {page['html_name']}",
                attributes=attrs,
                parent_id=parent_do_id,
            )
            do_id = do["id"]
            _log(f"{prefix} Created DO id={do_id}: {page['title']}")
        except RuntimeError as exc:
            msg = f"Failed to create DO for '{page['title']}': {exc}"
            _err(msg)
            errors.append(msg)
            continue

        # Create LabJournalEntry
        html = page["body_html"] or f"<p><em>{page['title']}</em></p>"
        try:
            client.create_lab_journal(do_id, html)
            _log(f"         LabJournal created for DO id={do_id}")
        except RuntimeError as exc:
            msg = f"Failed to create LabJournal for DO id={do_id} '{page['title']}': {exc}"
            _warn(msg)
            errors.append(msg)
            # Not fatal — the DataObject exists; just no journal entry

        state.mark_done(path, do_id)
        created.append({"path": path, "title": page["title"], "id": do_id})

    return created, errors


# ── Main ──────────────────────────────────────────────────────────────────────

def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--zip", metavar="PATH", help="Confluence HTML export ZIP file")
    src.add_argument("--dir", metavar="PATH", help="Extracted Confluence HTML export directory")

    ap.add_argument(
        "--host", required=True, metavar="URL",
        help="Shepard base URL, e.g. https://shepard.nuclide.systems",
    )
    ap.add_argument(
        "--token", required=True, metavar="TOKEN",
        help="Keycloak access token (Bearer); obtain via Keycloak token endpoint or UI",
    )
    ap.add_argument(
        "--cid", metavar="APP_ID",
        help="Append to an existing Collection by its appId; omit to create a new Collection",
    )
    ap.add_argument(
        "--collection-name", metavar="NAME", default="Confluence Import",
        help="Name for the new Collection when --cid is omitted (default: 'Confluence Import')",
    )
    ap.add_argument(
        "--dry-run", action="store_true",
        help="Generate manifest and validate against POST /v2/import/validate — no writes",
    )
    ap.add_argument(
        "--state-dir", metavar="DIR", default=".confluence-import-state",
        help="Directory for resumable import markers (default: .confluence-import-state)",
    )
    ap.add_argument(
        "--report", metavar="PATH",
        help="Write the import report to this markdown file",
    )
    ap.add_argument(
        "--with-attachments", action="store_true",
        help="Upload page attachments as FileReferences (slower)",
    )
    ap.add_argument(
        "--max-mb", type=float, default=50.0, metavar="MB",
        help="Skip attachments larger than N MB when --with-attachments is set (default: 50)",
    )

    args = ap.parse_args(argv)

    # ── Load export ────────────────────────────────────────────────────────────
    source = Path(args.zip or args.dir)
    if not source.exists():
        ap.error(f"Source not found: {source}")

    _log(f"Loading Confluence export from {source}")
    try:
        export = ConfluenceExport(source)
    except ValueError as exc:
        ap.error(str(exc))

    _log(f"Found {len(export.html_names())} page(s)")
    attachments = export.attachment_names()
    _log(f"Found {len(attachments)} attachment(s)")

    # ── Parse pages ────────────────────────────────────────────────────────────
    _log("Parsing page content…")
    pages = build_page_tree(export)
    _log(f"Built page tree: {len(pages)} page(s)")

    client = ShepardClient(args.host, args.token)

    # ── Dry-run mode ───────────────────────────────────────────────────────────
    if args.dry_run:
        coll_appid = args.cid or "PLACEHOLDER-COLLECTION-APPID"
        manifest = pages_to_manifest(pages, coll_appid)
        _log(f"Manifest generated: {len(manifest['dataObjects'])} DataObject(s)")

        if args.cid:
            _log(f"Validating manifest against POST /v2/import/validate…")
            try:
                result = client.validate_manifest(manifest)
                commit_id = result.get("commitId")
                status = result.get("status", "?")
                errors_v = result.get("errors", [])
                warnings_v = result.get("warnings", [])
                _log(f"Validation result: status={status} commitId={commit_id}")
                for e in errors_v:
                    _err(f"Validation error: {e}")
                for w in warnings_v:
                    _warn(f"Validation warning: {w}")
            except RuntimeError as exc:
                _err(f"Validation request failed: {exc}")
                commit_id = None
        else:
            _log("No --cid provided — printing manifest without server validation")
            commit_id = None
            print(json.dumps(manifest, indent=2))

        report = build_report(pages, attachments, [], [], dry_run=True, commit_id=commit_id)
        print("\n" + report)
        if args.report:
            Path(args.report).write_text(report, encoding="utf-8")
            _log(f"Report written to {args.report}")
        return 0

    # ── Execute mode ──────────────────────────────────────────────────────────
    state = ImportState(Path(args.state_dir))

    # Resolve or create Collection
    coll_id: int
    if args.cid:
        _log(f"Looking up Collection by appId {args.cid!r}…")
        coll = client.get_collection_by_appid(args.cid)
        if coll is None:
            _err(f"Collection not found: {args.cid}")
            return 1
        coll_id = coll.get("id") or coll.get("shepardId")
        if coll_id is None:
            _err(f"Could not determine numeric id for collection {args.cid}")
            return 1
        _log(f"Using existing Collection id={coll_id} name={coll.get('name')!r}")
    else:
        _log(f"Creating new Collection {args.collection_name!r}…")
        try:
            coll = client.create_collection(args.collection_name)
            coll_id = coll["id"]
            _log(f"Created Collection id={coll_id}")
        except RuntimeError as exc:
            _err(f"Failed to create Collection: {exc}")
            return 1

    # Import pages
    _log(f"Importing {len(pages)} page(s) into Collection id={coll_id}…")
    created, errors = execute_import(client, pages, coll_id, state, dry_run=False)

    # Summary
    _log(f"Import complete: {len(created)} DataObject(s) created, {len(errors)} error(s)")
    if errors:
        _log("See the report for error details.")

    report = build_report(pages, attachments, created, errors, dry_run=False)
    print("\n" + report)
    if args.report:
        Path(args.report).write_text(report, encoding="utf-8")
        _log(f"Report written to {args.report}")

    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
