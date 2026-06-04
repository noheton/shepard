#!/usr/bin/env python3
"""wiki_common.py — shared parser + Shepard client for the three MFFD
wiki-transformation tracks (per aidocs/integrations/120-mffd-wiki-transformation.md).

This module carries the load-bearing, pure (testable) functions:

  • classify_pages()      — split the Confluence dump into journal / plan /
                            reference classes (§120 §1).
  • split_dated_blocks()  — segment a journal page into dated entries
                            (§120 §2.1, the ~218-entry target).
  • extract_author()      — pull the Confluence author from a page's HTML head
                            (`Created by <span class='author'>...`), maps to the
                            `:MirroredUser` mirror shape (§120 §3).
  • mine_acronyms()       — extract candidate `urn:shepard:mffd:term:<acronym>`
                            controlled-vocab terms (§120 §1.3 / §2.3).
  • html_to_text()        — render a Confluence HTML body to readable plain text
                            (preserves list/paragraph structure).

…and the live-side glue:

  • Client                — a thin requests.Session wrapper carrying the API
                            methods the three track scripts need (collections,
                            data-objects v1, files v2, lab-journal entries v1,
                            semantic annotations v2, mirrored users v2 admin),
                            each with retry-forever-with-backoff per CLAUDE.md
                            "completeness non-negotiable".

The three track scripts (wiki-to-journal.py, wiki-to-plans.py,
wiki-to-glossary.py) import from here. The dump is processed in place; only
abstracted structure/terms land as data (per feedback_mffd_structure_not_content
+ "Uploads NEVER in public repo").
"""
from __future__ import annotations

import argparse
import hashlib
import html as _html
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterable, Optional

try:
    import requests
except ImportError:  # pragma: no cover
    sys.stderr.write("ERROR: this script needs `requests` (pip install requests).\n")
    raise

try:
    from bs4 import BeautifulSoup  # type: ignore
except ImportError:  # pragma: no cover
    sys.stderr.write("ERROR: this script needs `beautifulsoup4` (pip install beautifulsoup4).\n")
    raise


# ── constants ────────────────────────────────────────────────────────────────

DEFAULT_HOST = "https://shepard-api.nuclide.systems"
DEFAULT_SOURCE = "/mnt/pve/unas/dump/dataset/wiki/MFFD"

CONFLUENCE_BASE = "https://wiki.dlr.de"  # the dump's data-base-url
MIRROR_SOURCE_INSTANCE = "confluence-dlr"

PROJECT_APPID = "019e8c48-e7bc-760b-b870-e7aab5527e1a"

# predicate namespaces (per aidocs/integrations/120)
PRED_WIKI_SOURCE_PAGE_ID = "urn:shepard:wiki:source-page-id"
PRED_WIKI_SOURCE_PAGE_TITLE = "urn:shepard:wiki:source-page-title"
PRED_WIKI_SOURCE_BLOCK_INDEX = "urn:shepard:wiki:source-block-index"
PRED_WIKI_SOURCE_URL = "urn:shepard:wiki:source-url"
PRED_WIKI_JOURNAL_ROLE = "urn:shepard:wiki:journal-role"
PRED_WIKI_PLAN_DOCUMENT = "urn:shepard:wiki:plan-document"
PRED_WIKI_PLAN_CATEGORY = "urn:shepard:wiki:plan-category"
PRED_MFFD_MENTIONS = "urn:shepard:mffd:mentions"
PRED_MFFD_TERM_PREFIX = "urn:shepard:mffd:term:"
PRED_LANG = "urn:shepard:lang"

# Marker embedded into LabJournalEntry HTML so re-runs are idempotent. The
# LabJournalEntry create endpoint takes only `journalContent`, so the composite
# idempotency key (source-page-id, source-block-index) must live inside the
# content. We use an HTML comment the sanitizer keeps out of the rendered view.
IDEMPOTENCY_MARKER_PREFIX = "wiki-journal-key:"


# ── logging ──────────────────────────────────────────────────────────────────

def log(msg: str = "") -> None:
    sys.stdout.write(msg + "\n")
    sys.stdout.flush()


def warn(msg: str) -> None:
    sys.stdout.write(f"  WARN: {msg}\n")
    sys.stdout.flush()


def die(msg: str) -> None:
    sys.stderr.write(f"FATAL: {msg}\n")
    sys.exit(1)


# ── pure parsing helpers (unit-tested) ───────────────────────────────────────

_DATE_RE = re.compile(r"\b([0-3]?\d)\.([0-1]?\d)\.(20\d{2})\b")
_TITLE_RE = re.compile(r"<title>\s*(?:MFFD\s*:\s*)?(.*?)\s*</title>", re.I | re.S)
_AUTHOR_RE = re.compile(r"Created by\s*<span class='author'>\s*(.*?)\s*</span>", re.S)
_EDITOR_RE = re.compile(r"last updated by\s*<span class='editor'>\s*(.*?)\s*</span>", re.S)
_UNKNOWN_USER_RE = re.compile(r"Unknown User\s*\(([^)]+)\)")
_PAGEID_IN_NAME_RE = re.compile(r"_(\d{6,})\.html?$")
_PAGEID_NUMERIC_RE = re.compile(r"^(\d{6,})\.html?$")


@dataclass
class WikiPage:
    """One Confluence page parsed from the dump."""
    path: Path
    page_id: str
    title: str
    author_raw: str            # e.g. "Unknown User (dede_di)" or "Mayer, Monika"
    author_username: str       # the mirror sourceUsername (login or slugged name)
    author_display: str        # human display name
    html: str
    dated_count: int           # number of unique DD.MM.YYYY tokens
    klass: str = "reference"   # journal | plan | reference

    @property
    def source_url(self) -> str:
        return f"{CONFLUENCE_BASE}/pages/viewpage.action?pageId={self.page_id}"


def page_id_from_filename(name: str) -> str:
    """Resolve the Confluence page id from a dump filename.

    Filenames are either `<slug>_<pageid>.html` or `<pageid>.html`.
    """
    m = _PAGEID_IN_NAME_RE.search(name)
    if m:
        return m.group(1)
    m = _PAGEID_NUMERIC_RE.match(name)
    if m:
        return m.group(1)
    # last resort: strip extension
    return name.rsplit(".", 1)[0]


def _slug_username(display: str) -> str:
    """Turn a 'Lastname, Firstname' display into a stable mirror username."""
    s = display.strip().lower()
    s = re.sub(r"[,\s]+", "-", s)
    s = re.sub(r"[^a-z0-9\-]", "", s)
    s = re.sub(r"-+", "-", s).strip("-")
    return s or "wiki-import-anonymous"


def extract_author(html: str) -> tuple[str, str, str]:
    """Return (author_raw, mirror_username, display_name) for a page.

    Confluence stores `Created by <span class='author'> Unknown User (login)</span>`.
    When the author is `Unknown User (login)` we use `login` as the mirror
    username and `login` as the display fallback. When it is a real name
    (`Lastname, Firstname`) we slug it for the username and keep the name as
    display. Falls back to the editor span, then to the anonymous sentinel.
    """
    m = _AUTHOR_RE.search(html) or _EDITOR_RE.search(html)
    if not m:
        return ("", "wiki-import-anonymous", "Wiki import (anonymous)")
    raw = re.sub(r"\s+", " ", m.group(1)).strip()
    uu = _UNKNOWN_USER_RE.search(raw)
    if uu:
        login = uu.group(1).strip()
        return (raw, login, login)
    # Real name shape "Lastname, Firstname"
    return (raw, _slug_username(raw), raw)


def extract_title(html: str, fallback: str) -> str:
    m = _TITLE_RE.search(html)
    if m:
        t = re.sub(r"\s+", " ", m.group(1)).strip()
        return t or fallback
    return fallback


def _main_content(html: str) -> BeautifulSoup:
    soup = BeautifulSoup(html, "html.parser")
    node = soup.find(id="main-content") or soup.find(id="content") or soup
    return node


def html_to_text(fragment_html: str) -> str:
    """Render an HTML fragment to readable plain text.

    Preserves paragraph + list-item line breaks; collapses inline whitespace;
    drops images/attachments (kept as `[image]` placeholders so the entry text
    still notes one was present).
    """
    soup = BeautifulSoup(fragment_html, "html.parser")
    for img in soup.find_all("img"):
        img.replace_with("[image]")
    out: list[str] = []

    def walk(node, depth: int = 0):
        for child in node.children:
            name = getattr(child, "name", None)
            if name is None:
                txt = str(child)
                if txt.strip():
                    out.append(txt)
                continue
            if name in ("p", "div", "br", "h1", "h2", "h3", "h4", "tr"):
                # open block-level elements on their own line so a dated
                # heading (e.g. "<p><strong>07.12.2022 …") never gets glued to
                # the tail of a preceding list item.
                out.append("\n")
                walk(child, depth)
                out.append("\n")
            elif name == "li":
                out.append("\n" + "  " * depth + "- ")
                walk(child, depth + 1)
            elif name in ("ul", "ol"):
                walk(child, depth + 1)
            elif name in ("td", "th"):
                walk(child, depth)
                out.append(" | ")
            else:
                walk(child, depth)

    walk(soup)
    text = "".join(out)
    text = _html.unescape(text)
    # collapse runs of blank lines + trailing spaces
    lines = [re.sub(r"[ \t]+", " ", ln).rstrip() for ln in text.splitlines()]
    cleaned: list[str] = []
    blank = False
    for ln in lines:
        if not ln.strip():
            if not blank:
                cleaned.append("")
            blank = True
        else:
            cleaned.append(ln.strip())
            blank = False
    return "\n".join(cleaned).strip()


@dataclass
class DatedBlock:
    index: int
    date_iso: str       # YYYY-MM-DD
    date_raw: str       # DD.MM.YYYY as found
    heading: str        # the heading line / row label
    text: str           # rendered body of the block


def _iso_date(d: str, m: str, y: str) -> Optional[str]:
    try:
        dd, mm, yy = int(d), int(m), int(y)
        if not (1 <= dd <= 31 and 1 <= mm <= 12):
            return None
        return f"{yy:04d}-{mm:02d}-{dd:02d}"
    except ValueError:
        return None


def split_dated_blocks(page: WikiPage) -> list[DatedBlock]:
    """Segment a journal page into dated blocks.

    Two shapes are handled:

      1. Paragraph-headed diary (Legetagebuch, Versuchlog): the rendered text
         has lines that *start* with a DD.MM.YYYY date; each such line opens a
         new block that runs until the next dated line.
      2. Table-row dated logs (Schichtplan, Materialinfo, Laser-Integration):
         each table row carries a date in a cell; we emit one block per row.

    The method renders the main-content to text first (so table cells become
    ` | `-joined lines) and then segments on dated line-starts. This single
    code path covers both shapes because html_to_text() turns every <tr> into
    its own line.
    """
    main = _main_content(page.html)
    text = html_to_text(str(main))
    lines = text.splitlines()

    blocks: list[DatedBlock] = []
    current: Optional[DatedBlock] = None
    idx = 0

    # A "dated line" is one whose first 40 chars contain a date. We anchor on
    # the *earliest* date in the line as the block date.
    for ln in lines:
        head = ln[:60]
        m = _DATE_RE.search(head)
        if m:
            iso = _iso_date(m.group(1), m.group(2), m.group(3))
            if iso:
                # close the previous block
                if current is not None:
                    blocks.append(current)
                heading = re.sub(r"\s+", " ", ln).strip()
                current = DatedBlock(
                    index=idx, date_iso=iso, date_raw=m.group(0),
                    heading=heading[:300], text=ln.strip(),
                )
                idx += 1
                continue
        if current is not None:
            # accumulate body lines for paragraph-headed shape
            if ln.strip():
                current.text += "\n" + ln.strip()
    if current is not None:
        blocks.append(current)

    # de-dup degenerate blocks (a date line with no body but identical to next)
    return blocks


def classify_pages(source_dir: Path, journal_min_dates: int = 5) -> list[WikiPage]:
    """Walk the dump and classify every page.

    journal  → >= journal_min_dates unique DD.MM.YYYY tokens
    plan     → title/filename matches the plan keyword set
    reference→ everything else
    """
    plan_kw = re.compile(
        r"(project[\s\-]?plan|painlist|roadmap|architecture|"
        r"plan[\s\-]for|schichtplan|legeplan|stufenversuche|"
        r"test[\s\-]?log|versuchlog|versuch|steering)",
        re.I,
    )
    # journal filenames the spec pins (so a low date count from odd markup
    # still routes to the journal track).
    journal_force = re.compile(
        r"(legetagebuch|legeplan|schichtplan|materialinfo|laser-integration|versuchlog)",
        re.I,
    )

    pages: list[WikiPage] = []
    for path in sorted(source_dir.glob("*.html")):
        if path.name in ("index.html",):
            continue
        html = path.read_text(encoding="utf-8", errors="replace")
        page_id = page_id_from_filename(path.name)
        title = extract_title(html, path.stem)
        author_raw, username, display = extract_author(html)
        dates = {f"{a}.{b}.{c}" for (a, b, c) in _DATE_RE.findall(html)}
        ndates = len(dates)

        if ndates >= journal_min_dates or journal_force.search(path.name) or journal_force.search(title):
            klass = "journal"
        elif plan_kw.search(path.name) or plan_kw.search(title):
            klass = "plan"
        else:
            klass = "reference"

        pages.append(WikiPage(
            path=path, page_id=page_id, title=title,
            author_raw=author_raw, author_username=username, author_display=display,
            html=html, dated_count=ndates, klass=klass,
        ))
    return pages


# ── acronym / glossary mining ────────────────────────────────────────────────

# A curated expansion table for the canonical campaign terms (§120 §1.3 / §8
# acceptance: AFP, TPS, FSD, NDT, LBR, MFFD, MFZ, AF must appear with
# expansions). Terms not in the table are still emitted with a low-confidence
# null expansion so the inverse `mentions` query stays complete.
KNOWN_EXPANSIONS = {
    "MFFD": "Multifunctional Fuselage Demonstrator",
    "AFP": "Automated Fibre Placement",
    "TPS": "Tape Placement System",
    "FSD": "Fibre Steering Device",
    "NDT": "Non-Destructive Testing",
    "LBR": "Leichtbauroboter (KUKA lightweight robot)",
    "MFZ": "Multifunktionale Fertigungszelle (multifunctional manufacturing cell)",
    "AF": "Aufgeschäumtes Frame / AF part",
    "MTLH": "Material-Tape-Lay-Head",
    "DA": "Datenauswertung (data evaluation)",
    "AU": "Augsburg",
    "WP": "Work Package",
    "CW": "Calendar Week",
    "TPS/FSD": "Tape Placement System / Fibre Steering Device",
    "PG": "Ply Group",
    "TCP": "Tool Center Point",
    "RDK": "RoboDK cell file",
    "URDF": "Unified Robot Description Format",
    "TLZ": "Technologie-Leistungszentrum",
    "ST": "Stringer",
    "TPC": "Thermoplastic Composite",
    "CF": "Carbon Fibre",
    "LMPAEK": "Low-Melt Poly-Aryl-Ether-Ketone",
    "DRG": "Datenrückgewinnung / data recorder",
    "QI": "Quasi-Isotropic",
}


def load_stopwords(path: Optional[Path]) -> set[str]:
    base = {
        "HTTP", "HTTPS", "URL", "HTML", "PDF", "CSV", "JPG", "PNG", "XML",
        "JSON", "API", "ID", "OK", "TODO", "FAQ", "ZIP", "UTF", "GMBH",
        "DLR", "CC", "BY", "SA", "AND", "THE", "FOR", "MFFD",  # MFFD handled explicitly
    }
    # keep MFFD in expansions but allow it; remove from stop so it still mines.
    base.discard("MFFD")
    if path and path.exists():
        for ln in path.read_text(encoding="utf-8").splitlines():
            ln = ln.strip()
            if ln and not ln.startswith("#"):
                base.add(ln.upper())
    return base


# A candidate acronym is >=2 *consecutive* capital letters (so "MFFD", "AFP",
# "NDT", "TPS/FSD"), optionally followed by digits — but NOT a single letter +
# digits ("A1", "A320", "F4"), which are cell/ply labels, not vocabulary.
_ACRONYM_RE = re.compile(r"\b([A-Z]{2,6}[0-9]{0,3}(?:/[A-Z]{2,6}[0-9]{0,3})?)\b")


def mine_acronyms(pages: Iterable[WikiPage], stopwords: set[str]) -> dict[str, dict[str, Any]]:
    """Mine candidate controlled-vocab acronyms across the reference pages.

    Returns { acronym: { 'term': <expansion-or-None>, 'source_pages': [ids],
                         'confidence': 'high'|'medium'|'low', 'count': N } }
    """
    acc: dict[str, dict[str, Any]] = {}
    for page in pages:
        text = html_to_text(str(_main_content(page.html)))
        found_here: set[str] = set()
        for m in _ACRONYM_RE.finditer(text):
            acr = m.group(1)
            if acr in stopwords:
                continue
            if acr.isdigit():
                continue
            found_here.add(acr)
        for acr in found_here:
            row = acc.setdefault(acr, {
                "term": KNOWN_EXPANSIONS.get(acr),
                "source_pages": [],
                "count": 0,
            })
            row["source_pages"].append(page.page_id)
            row["count"] += 1
    # confidence: known expansion → high; >=3 pages → medium; else low.
    for acr, row in acc.items():
        if row["term"]:
            row["confidence"] = "high"
        elif len(row["source_pages"]) >= 3:
            row["confidence"] = "medium"
        else:
            row["confidence"] = "low"
    return acc


# ── idempotency keys ─────────────────────────────────────────────────────────

def journal_idempotency_token(page_id: str, block_index: int) -> str:
    return f"{IDEMPOTENCY_MARKER_PREFIX}{page_id}:{block_index}"


def journal_marker_css(page_id: str, block_index: int) -> str:
    """The CSS-property token that carries the idempotency key.

    The backend HtmlSanitizer (jsoup `basicWithImages` Safelist) strips HTML
    comments and `data-*` attributes but preserves the `style` attribute value
    verbatim — including custom CSS properties. So the (page-id, block-index)
    key travels as a `--wjk` custom property on a hidden span. The CSS value
    separator is `:`, so the key uses `-` between page-id and block-index.
    """
    return f"--wjk:{page_id}-{block_index}"


def journal_marker_comment(page_id: str, block_index: int) -> str:
    """The hidden-span marker embedded into journalContent for idempotency.

    Rendered invisibly (`display:none`); survives jsoup sanitization (verified
    against the live HtmlSanitizer). A GET-list of the DO's entries matches the
    `--wjk:<page>-<block>` substring to detect a prior import.
    """
    css = journal_marker_css(page_id, block_index)
    return f"<span style=\"display:none;{css}\"></span>"


def content_has_marker(content: str, page_id: str, block_index: int) -> bool:
    if not content:
        return False
    return journal_marker_css(page_id, block_index) in content


# ── live Shepard client ──────────────────────────────────────────────────────

_RETRY_STATUSES = {429, 500, 502, 503, 504, 520, 521, 522, 523, 524}


@dataclass
class Client:
    host: str
    api_key: str
    dry_run: bool = False
    session: Optional[requests.Session] = None
    _coll_v1id_cache: dict = field(default_factory=dict)

    def __post_init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "X-API-KEY": self.api_key,
            "Accept": "application/json",
        })

    # ── low-level with retry-forever-with-backoff (completeness non-negotiable)
    def _request(self, method: str, path: str, *, json_body=None, params=None,
                 files=None, data=None, mutating: bool, max_seconds: int = 600):
        if self.dry_run and mutating:
            log(f"    [dry-run] {method} {path}")
            return None
        url = f"{self.host.rstrip('/')}{path}"
        attempt = 0
        deadline = time.time() + max_seconds
        while True:
            attempt += 1
            try:
                headers = {}
                if json_body is not None:
                    headers["Content-Type"] = "application/json"
                resp = self.session.request(
                    method, url, timeout=120,
                    json=json_body if json_body is not None else None,
                    params=params, files=files, data=data, headers=headers,
                )
                if resp.status_code in _RETRY_STATUSES and time.time() < deadline:
                    backoff = min(2 ** min(attempt, 6), 30)
                    warn(f"{method} {path} → {resp.status_code}; retry {attempt} in {backoff}s")
                    time.sleep(backoff)
                    continue
                return resp
            except requests.RequestException as e:
                if time.time() >= deadline:
                    die(f"HTTP error on {method} {path} after {attempt} attempts: {e}")
                backoff = min(2 ** min(attempt, 6), 30)
                warn(f"{method} {path} exc={e!r}; retry {attempt} in {backoff}s")
                time.sleep(backoff)

    # ── identity smoke test
    def whoami(self) -> Optional[dict]:
        r = self._request("GET", "/v2/users/me", mutating=False)
        if r is not None and r.status_code == 200:
            return r.json()
        return None

    # ── collections
    def get_collection_v2(self, app_id: str) -> Optional[dict]:
        r = self._request("GET", f"/v2/collections/{app_id}", mutating=False)
        if r is None or r.status_code != 200:
            return None
        return r.json()

    def collection_v1_id(self, app_id: str) -> Optional[int]:
        if app_id in self._coll_v1id_cache:
            return self._coll_v1id_cache[app_id]
        c = self.get_collection_v2(app_id)
        if c and "id" in c:
            self._coll_v1id_cache[app_id] = int(c["id"])
            return int(c["id"])
        return None

    def sub_collections(self, project_app_id: str) -> list[dict]:
        r = self._request("GET", f"/v2/projects/{project_app_id}/sub-collections", mutating=False)
        if r is None or r.status_code != 200:
            return []
        d = r.json()
        if isinstance(d, list):
            return d
        return d.get("content") or d.get("subCollections") or []

    # ── data objects (v1)
    def list_data_objects_v1(self, coll_v1_id: int) -> list[dict]:
        out: list[dict] = []
        page = 0
        while True:
            r = self._request(
                "GET",
                f"/shepard/api/collections/{coll_v1_id}/dataObjects",
                params={"page": page, "size": 200}, mutating=False,
            )
            if r is None or r.status_code >= 400:
                break
            rows = r.json()
            if not rows:
                break
            out.extend(rows)
            if len(rows) < 200:
                break
            page += 1
        return out

    def find_data_object_by_name_v1(self, coll_v1_id: int, name: str) -> Optional[dict]:
        r = self._request(
            "GET", f"/shepard/api/collections/{coll_v1_id}/dataObjects",
            params={"name": name}, mutating=False,
        )
        if r is None or r.status_code >= 400:
            return None
        for d in r.json():
            if d.get("name") == name:
                return d
        return None

    def create_data_object_v1(self, coll_v1_id: int, name: str, description: str = "",
                              attributes: Optional[dict] = None,
                              parent_id: Optional[int] = None) -> Optional[dict]:
        body: dict[str, Any] = {"name": name}
        if description:
            body["description"] = description
        if attributes:
            body["attributes"] = {k: str(v) for k, v in attributes.items()}
        if parent_id is not None:
            body["parentId"] = parent_id
        r = self._request("POST", f"/shepard/api/collections/{coll_v1_id}/dataObjects",
                          json_body=body, mutating=True)
        if r is None:
            return None
        if r.status_code not in (200, 201):
            die(f"create DO {name!r} failed: {r.status_code} {r.text[:200]}")
        return r.json()

    def ensure_data_object_v1(self, coll_v1_id: int, name: str, description: str = "",
                              attributes: Optional[dict] = None,
                              parent_id: Optional[int] = None) -> Optional[dict]:
        existing = self.find_data_object_by_name_v1(coll_v1_id, name)
        if existing:
            return existing
        if self.dry_run:
            return {"id": -1, "appId": f"<dry-run-{_slug_username(name)}>", "name": name}
        return self.create_data_object_v1(coll_v1_id, name, description, attributes, parent_id)

    # ── lab journal entries (v1)
    def list_lab_journal_entries_v1(self, do_v1_id: int) -> list[dict]:
        r = self._request(
            "GET", "/shepard/api/labJournalEntries/",
            params={"dataObjectId": do_v1_id}, mutating=False,
        )
        if r is None or r.status_code >= 400:
            return []
        d = r.json()
        return d if isinstance(d, list) else []

    def create_lab_journal_entry_v1(self, do_v1_id: int, journal_content: str) -> Optional[dict]:
        r = self._request(
            "POST", "/shepard/api/labJournalEntries/",
            params={"dataObjectId": do_v1_id},
            json_body={"journalContent": journal_content}, mutating=True,
        )
        if r is None:
            return None
        if r.status_code not in (200, 201):
            die(f"create labJournalEntry on DO {do_v1_id} failed: {r.status_code} {r.text[:300]}")
        return r.json()

    # ── files (v2 singleton)
    def list_file_refs_v2(self, do_app_id: str) -> list[dict]:
        """List singleton FileReferences on a DataObject (v2).

        The v1 `/fileReferences` listing does NOT surface v2-uploaded singleton
        FileReferences (the v5 list-visibility gap, project_v5_list_visibility_bug),
        so idempotency MUST use this v2 by-data-object endpoint.
        """
        r = self._request(
            "GET", f"/v2/files/by-data-object/{do_app_id}", mutating=False,
        )
        if r is None or r.status_code != 200:
            return []
        d = r.json()
        return d if isinstance(d, list) else d.get("content", [])

    def upload_singleton_file_v2(self, parent_do_app_id: str, name: str,
                                 content: bytes) -> Optional[dict]:
        import io
        r = self._request(
            "POST", "/v2/files",
            params={"parentDataObjectAppId": parent_do_app_id, "name": name},
            files={"file": (name, io.BytesIO(content))}, mutating=True,
        )
        if r is None:
            return None
        if r.status_code not in (200, 201):
            die(f"upload file {name!r} failed: {r.status_code} {r.text[:300]}")
        return r.json()

    def list_file_references_v1(self, coll_v1_id: int, do_v1_id: int) -> list[dict]:
        r = self._request(
            "GET",
            f"/shepard/api/collections/{coll_v1_id}/dataObjects/{do_v1_id}/fileReferences",
            mutating=False,
        )
        if r is None or r.status_code >= 400:
            return []
        d = r.json()
        return d if isinstance(d, list) else []

    # ── semantic annotations (v2)
    def list_annotations(self, subject_app_id: str, subject_kind: str,
                         predicate_iri: Optional[str] = None) -> list[dict]:
        # The /v2/annotations list caps pageSize at 200 (a 400 otherwise — which
        # would silently break idempotency by returning []), so we paginate.
        out: list[dict] = []
        page = 0
        while True:
            params = {
                "subjectAppId": subject_app_id, "subjectKind": subject_kind,
                "page": page, "pageSize": 200,
            }
            if predicate_iri:
                params["predicateIri"] = predicate_iri
            r = self._request("GET", "/v2/annotations", params=params, mutating=False)
            if r is None or r.status_code != 200:
                break
            d = r.json()
            rows = d if isinstance(d, list) else d.get("content", [])
            if not rows:
                break
            out.extend(rows)
            if len(rows) < 200:
                break
            page += 1
        return out

    def create_annotation(self, subject_app_id: str, subject_kind: str,
                          predicate_iri: str, object_literal: str,
                          predicate_label: Optional[str] = None) -> Optional[dict]:
        body = {
            "subjectAppId": subject_app_id,
            "subjectKind": subject_kind,
            "predicateIri": predicate_iri,
            "objectLiteral": object_literal,
            "sourceMode": "human",
        }
        if predicate_label:
            body["predicateLabel"] = predicate_label
        r = self._request("POST", "/v2/annotations", json_body=body, mutating=True)
        if r is None:
            return None
        if r.status_code not in (200, 201):
            die(f"create annotation {predicate_iri}={object_literal!r} on "
                f"{subject_kind}/{subject_app_id} failed: {r.status_code} {r.text[:200]}")
        return r.json()

    def ensure_annotation(self, subject_app_id: str, subject_kind: str,
                          predicate_iri: str, object_literal: str,
                          predicate_label: Optional[str] = None) -> tuple[Optional[dict], bool]:
        """Idempotent annotation. Returns (annotation, created?)."""
        existing = self.list_annotations(subject_app_id, subject_kind, predicate_iri)
        for ann in existing:
            iri = ann.get("predicateIri") or ann.get("propertyIRI")
            val = ann.get("objectLiteral") or ann.get("valueName")
            if iri == predicate_iri and val == object_literal:
                return ann, False
        if self.dry_run:
            return None, True
        return self.create_annotation(subject_app_id, subject_kind, predicate_iri,
                                      object_literal, predicate_label), True

    # ── mirrored users (v2 admin)
    def mirror_user(self, source_username: str, display_name: Optional[str] = None,
                    email: Optional[str] = None) -> Optional[dict]:
        body = {
            "sourceInstance": MIRROR_SOURCE_INSTANCE,
            "sourceUsername": source_username,
        }
        if display_name:
            body["sourceDisplayName"] = display_name
        if email:
            body["sourceEmail"] = email
        r = self._request("POST", "/v2/admin/users/mirror", json_body=body, mutating=True)
        if r is None:
            return None
        if r.status_code not in (200, 201):
            warn(f"mirror user {source_username!r} → {r.status_code} {r.text[:200]}")
            return None
        return r.json()


# ── shared CLI scaffolding ───────────────────────────────────────────────────

def add_common_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--source", default=os.environ.get("MFFD_WIKI_SOURCE", DEFAULT_SOURCE),
                        help=f"Confluence dump dir (env MFFD_WIKI_SOURCE; default {DEFAULT_SOURCE}).")
    parser.add_argument("--host", default=os.environ.get("SHEPARD_HOST", DEFAULT_HOST),
                        help="Shepard host URL (env SHEPARD_HOST).")
    parser.add_argument("--api-key", default=os.environ.get("SHEPARD_API_KEY"),
                        help="Shepard API key — sent as X-API-KEY (env SHEPARD_API_KEY).")
    parser.add_argument("--commit", action="store_true",
                        help="Write to Shepard. Default is dry-run (no writes).")
    parser.add_argument("--project", default=os.environ.get("MFFD_PROJECT_APPID", PROJECT_APPID),
                        help="MFFD Project Collection appId.")


def make_client(args) -> Client:
    if not args.host:
        die("SHEPARD_HOST not set (env or --host).")
    if not args.api_key:
        die("SHEPARD_API_KEY not set (env or --api-key).")
    client = Client(host=args.host, api_key=args.api_key, dry_run=not args.commit)
    me = client.whoami()
    if me:
        log(f"[auth] {me.get('effectiveDisplayName') or me.get('username')}")
    else:
        warn("whoami returned no body — continuing (the key may still be valid).")
    return client


def resolve_step_collections(client: Client, project_app_id: str) -> dict[str, dict]:
    """Map step slug → { appId, v1Id, name } for the 5 MFFD step Collections."""
    out: dict[str, dict] = {}
    for sub in client.sub_collections(project_app_id):
        name = sub.get("name")
        app_id = sub.get("appId")
        if not name or not app_id:
            continue
        v1id = client.collection_v1_id(app_id)
        out[name] = {"appId": app_id, "v1Id": v1id, "name": name}
    return out
