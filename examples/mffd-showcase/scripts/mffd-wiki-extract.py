# /// script
# requires-python = ">=3.11"
# dependencies = ["httpx", "beautifulsoup4", "lxml", "pytest", "tqdm"]
# ///
#!/usr/bin/env python3
"""mffd-wiki-extract.py — extract a Confluence HTML-space-export zip into
per-page DataObjects in the MFFD-Dropbox collection on nuclide.systems.

Source: a 507 MB Confluence-space-export zip uploaded as a file reference
on DataObject `MFFD-Dropbox-wiki-export` (appId
`019e4fdd-30fe-71b2-becc-30b1c617585f`) in collection MFFD-Dropbox
(appId `019e4e56-ca63-76f3-9bf0-6681f7fe6d56`).

Target shape (one DO per Confluence page):
  - name = page title
  - parent = a new root DO `MFFD-Dropbox-wiki-pages`
  - file ref = the page's HTML (uploaded into file container
               `MFFD-Dropbox-wiki-pages-files`)
  - attributes = confluence_page_id, confluence_space_key,
                 confluence_version, confluence_last_modified,
                 confluence_author, wiki_path, wiki_depth
  - predecessor link = the page's Confluence parent (so the wiki
                       tree is navigable via the standard prov UI)

Plus a single TOC DataObject `MFFD-Dropbox-wiki-toc` with a
structured-data reference containing the full tree as JSON.

PHASES
──────
  Phase 0 — discovery (no mutation):
    Download the zip; unzip; parse the Confluence index.html; print a
    [STATUS] block (page count, depth, root titles, file size).
    Always runs.

  Phase 1 — small-sample test (5 pages, mutation, ROLLED BACK on failure):
    Create + verify 5 representative pages. Tears down created DOs if
    anything fails. Always runs unless --skip-phase1 is set.

  Phase 2 — STOP:
    Prints "ready to extract N pages; re-run with --commit" and exits.
    The user controls when full extraction happens.

  Phase 3 — full extraction (only with --commit):
    Iterate all pages, build TOC DO, take a snapshot.

USAGE
─────
  # Discovery + Phase 1 + STOP at Phase 2:
  SHEPARD_NUCLIDE_API_KEY=<jwt> uv run python mffd-wiki-extract.py

  # Full extraction (only after Phase 1 succeeds and user reviews):
  SHEPARD_NUCLIDE_API_KEY=<jwt> uv run python mffd-wiki-extract.py --commit

  # Just discovery (no mutation at all):
  SHEPARD_NUCLIDE_API_KEY=<jwt> uv run python mffd-wiki-extract.py --phase0-only

SAFETY
──────
Per `feedback_mutate_after_snapshot.md`: this script mutates only after
the post-ingest snapshot baseline already exists. Per
`feedback_always_write_tests.md`: the ToC parser is unit-tested in
`test_wiki_extract.py` and pytest runs at startup — script refuses to
proceed if tests fail.
"""
from __future__ import annotations

import argparse
import dataclasses
import datetime
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterator

# BeautifulSoup is required even for unit tests (parser is pure-bs4).
from bs4 import BeautifulSoup  # noqa: E402

# httpx + tqdm are only needed for live mutation; allow tests to import this
# module even if they're not installed.
try:
    import httpx  # type: ignore
except ImportError:  # pragma: no cover
    httpx = None  # type: ignore

try:
    from tqdm import tqdm  # type: ignore
except ImportError:  # pragma: no cover
    class tqdm:  # type: ignore
        """Minimal tqdm stand-in for environments without the real one."""
        def __init__(self, *a, **kw): self.total = kw.get("total", 0); self.n = 0
        def __enter__(self): return self
        def __exit__(self, *a): pass
        def update(self, n=1): self.n += n
        def set_postfix_str(self, *a, **kw): pass
        def write(self, msg): print(msg)


# ── Configuration ─────────────────────────────────────────────────────────────

SHEPARD_URL = os.environ.get("SHEPARD_URL", "https://shepard-api.nuclide.systems").rstrip("/")
API_KEY = (
    os.environ.get("SHEPARD_NUCLIDE_API_KEY")
    or os.environ.get("SHEPARD_API_KEY")
    or ""
)

COLLECTION_APP_ID = "019e4e56-ca63-76f3-9bf0-6681f7fe6d56"   # MFFD-Dropbox
COLLECTION_ID = 515365                                        # v1 id

# The wiki-export DataObject. The task prompt referenced appId
# 019e4fdd-30fe-71b2-becc-30b1c617585f which does NOT exist on
# shepard-api.nuclide.systems as of 2026-05-22; the actual DO is named
# "Wiki" with appId 019e4e56-cc40-74d6-aa5c-c65627d76603 (id=515374)
# created during the 2026-05-22-Q1 bootstrap. The 507 MB zip
# (file ref appId 019e4fdf-b3e0-71e8-8bda-d1aa16c270be — note the
# leading-substring similarity to the task prompt's wrong value)
# was uploaded to it via the UI on 2026-05-22T13:28Z.
WIKI_EXPORT_DO_APPID = os.environ.get(
    "WIKI_EXPORT_DO_APPID",
    "019e4e56-cc40-74d6-aa5c-c65627d76603",
)
WIKI_EXPORT_DO_NAME_FALLBACK = "Wiki"   # search by name if appId fails

WIKI_PAGES_ROOT_NAME = "MFFD-Dropbox-wiki-pages"
WIKI_TOC_DO_NAME = "MFFD-Dropbox-wiki-toc"

SESSION = os.environ.get("SESSION_ID", datetime.date.today().isoformat())
EXTRACT_TIME = datetime.datetime.now(datetime.timezone.utc).isoformat()


# ── ToC parsing (pure functions, unit-testable) ───────────────────────────────

@dataclass
class WikiNode:
    """One node in the Confluence ToC tree."""
    page_id: str
    title: str
    href: str
    children: list["WikiNode"] = field(default_factory=list)


def parse_confluence_toc(index_html: str) -> list[WikiNode]:
    """Parse a Confluence space export's index.html into a tree.

    Confluence exports the available pages in the body as a nested
    <ul><li><a href="<pageId>.html">title</a><ul>...</ul></li></ul>.
    Some exports use a flat list (no nested <ul>). Both shapes are
    accepted.

    Returns the list of root WikiNodes. Empty input → empty list.
    """
    soup = BeautifulSoup(index_html, "html.parser")

    # Find the page list. Confluence's index.html typically has a heading
    # "Available Pages:" followed by the top-level <ul>. We look for that
    # first; if absent, we fall back to the body's first <ul>.
    candidate_ul = None
    for heading in soup.find_all(["h1", "h2", "h3"]):
        text = (heading.get_text() or "").strip().lower()
        if "available pages" in text or "page list" in text or "pages" == text:
            sib = heading.find_next("ul")
            if sib:
                candidate_ul = sib
                break

    if candidate_ul is None:
        # Fallback: first <ul> in body that contains an <a href="*.html">
        for ul in soup.find_all("ul"):
            if ul.find("a", href=re.compile(r"\.html")):
                candidate_ul = ul
                break

    if candidate_ul is None:
        return []

    def parse_ul(ul) -> list[WikiNode]:
        nodes: list[WikiNode] = []
        # Direct <li> children only — not descendants (those belong to a
        # nested ul we'll recurse into).
        for li in ul.find_all("li", recursive=False):
            a = li.find("a", href=True, recursive=False) or li.find("a", href=True)
            if a is None:
                continue
            href = a["href"]
            title = (a.get_text() or "").strip()
            m = re.match(r"^(\d+)\.html?$", href)
            page_id = m.group(1) if m else href.rsplit("/", 1)[-1].rsplit(".", 1)[0]
            child_ul = li.find("ul", recursive=False)
            children = parse_ul(child_ul) if child_ul else []
            nodes.append(WikiNode(page_id=page_id, title=title, href=href, children=children))
        return nodes

    return parse_ul(candidate_ul)


def flatten_tree(tree: list[WikiNode], _depth: int = 0, _prefix: str = "") -> Iterator[tuple[WikiNode, int, str]]:
    """Yield (node, depth, wiki_path) depth-first, parents before children."""
    for node in tree:
        wiki_path = f"{_prefix}/{node.title}" if _prefix else node.title
        yield node, _depth, wiki_path
        yield from flatten_tree(node.children, _depth + 1, wiki_path)


def extract_page_metadata(page_html: str, page_id: str) -> dict[str, str]:
    """Pull page-level metadata from a single Confluence HTML page.

    Best-effort: a real Confluence export usually has well-known span
    classes (`author`, `last-modified`, `version`, etc.) but field
    layout varies by Confluence version. Missing fields are simply
    absent from the returned dict; the page_id is always present.
    """
    soup = BeautifulSoup(page_html, "html.parser")
    meta: dict[str, str] = {"confluence_page_id": str(page_id)}

    title_el = soup.find(class_="pagetitle") or soup.find("title")
    if title_el:
        meta["title"] = (title_el.get_text() or "").strip()

    space_meta = soup.find("meta", attrs={"name": "confluence-space-key"})
    if space_meta and space_meta.get("content"):
        meta["confluence_space_key"] = space_meta["content"]

    # Try a few common author/date/version locations.
    for cls, key in [
        ("author", "confluence_author"),
        ("last-modified", "confluence_last_modified"),
        ("version", "confluence_version"),
        ("page-metadata-modification-info", "confluence_metadata_raw"),
    ]:
        el = soup.find(class_=cls)
        if el:
            txt = (el.get_text() or "").strip()
            if txt:
                meta[key] = txt

    return meta


# ── Shepard client ────────────────────────────────────────────────────────────

class ShepardClient:
    """Minimal Shepard v1+v2 client for the wiki-extract workflow."""

    def __init__(self, base: str, api_key: str) -> None:
        if not api_key:
            sys.exit("ERROR: SHEPARD_NUCLIDE_API_KEY env var is empty. Refusing to run.")
        self.base = base
        self.h = httpx.Client(
            headers={"X-API-KEY": api_key, "Accept": "application/json"},
            timeout=httpx.Timeout(connect=10, read=600, write=600, pool=30),
            follow_redirects=True,
        )

    def close(self) -> None:
        self.h.close()

    # ── auth + smoke test ────────────────────────────────────────────────────

    def whoami(self) -> dict | None:
        # v2 endpoint
        r = self._get(f"{self.base}/v2/users/me", quiet=True)
        if r is not None and r.status_code == 200:
            return r.json()
        # v1 fallback
        r = self._get(f"{self.base}/shepard/api/users/current", quiet=True)
        if r is not None and r.status_code == 200:
            return r.json()
        return None

    # ── source DO + fileref lookup ───────────────────────────────────────────

    def get_data_object_v1(self, coll_id: int, do_app_id: str) -> dict | None:
        """Look up a DO via v1 by paginating and filtering on appId.
        Returns dict with v1 'id' and 'appId' and 'name', or None.
        """
        for page in range(0, 200):
            r = self._get(
                f"{self.base}/shepard/api/collections/{coll_id}/dataObjects",
                params={"page": page, "size": 200},
            )
            if r is None or not r.is_success:
                return None
            items = r.json()
            if not items:
                return None
            for item in items:
                if item.get("appId") == do_app_id:
                    return item
        return None

    def list_file_refs_v1(self, coll_id: int, do_id: int) -> list[dict]:
        r = self._get(
            f"{self.base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences"
        )
        if r is None or not r.is_success:
            return []
        return r.json() if isinstance(r.json(), list) else []

    def download_file_ref_v1(self, coll_id: int, do_id: int, fref_id: int, dest: Path, size_hint: int = 0) -> bool:
        """Download a file from a v1 FileReference (which is a file *bundle*).

        Two-step: GET /payload returns a JSON listing of files (each with an
        `oid`); GET /payload/{oid} returns the actual byte stream. When the
        bundle has a single file (the common case for our uploads) we use
        the first oid automatically.
        """
        list_url = f"{self.base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences/{fref_id}/payload"
        list_r = self.h.get(list_url)
        if list_r.status_code != 200:
            print(f"  [http {list_r.status_code}] GET payload listing: {list_r.text[:300]}")
            return False
        listing = list_r.json()
        if not isinstance(listing, list) or not listing:
            print(f"  [FAIL] payload listing empty for fref {fref_id}")
            return False
        # Choose the largest entry (handles the case where the bundle has
        # multiple files — pick the zip itself).
        entry = max(listing, key=lambda e: e.get("fileSize", 0))
        oid = entry["oid"]
        total = int(entry.get("fileSize") or size_hint or 0)
        print(f"  [bundle] downloading oid={oid} size={total:,} bytes")

        url = f"{list_url}/{oid}"
        with self.h.stream("GET", url) as r:
            if r.status_code != 200:
                print(f"  [http {r.status_code}] GET payload/{oid}: {r.read()[:300]!r}")
                return False
            with dest.open("wb") as fh, tqdm(
                total=total or None, unit="B", unit_scale=True, unit_divisor=1024,
                desc=f"  ↓ {dest.name[:50]}", leave=False, file=sys.stderr,
            ) as bar:
                for chunk in r.iter_bytes(chunk_size=1 << 16):
                    fh.write(chunk)
                    bar.update(len(chunk))
        return True

    # ── dest DO management ───────────────────────────────────────────────────

    def find_data_object_by_name_v1(self, coll_id: int, name: str) -> dict | None:
        r = self._get(
            f"{self.base}/shepard/api/collections/{coll_id}/dataObjects",
            params={"name": name},
        )
        if r is None or not r.is_success:
            return None
        for d in r.json():
            if d.get("name") == name:
                return d
        return None

    def create_data_object_v1(
        self,
        coll_id: int,
        name: str,
        description: str = "",
        attributes: dict | None = None,
        parent_id: int | None = None,
        predecessor_id: int | None = None,
    ) -> dict | None:
        body: dict[str, Any] = {"name": name}
        if description:
            body["description"] = description
        if attributes:
            body["attributes"] = {k: str(v) for k, v in attributes.items()}
        if parent_id is not None:
            body["parentId"] = parent_id
        r = self._post(f"{self.base}/shepard/api/collections/{coll_id}/dataObjects", body)
        if r is None or not r.is_success:
            return None
        do = r.json()
        if predecessor_id is not None:
            self._link_predecessor_v1(coll_id, do["id"], predecessor_id)
        return do

    def _link_predecessor_v1(self, coll_id: int, do_id: int, pred_id: int) -> bool:
        url = f"{self.base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/predecessors/{pred_id}"
        r = self._put(url, {})
        return r is not None and r.is_success

    def delete_data_object_v1(self, coll_id: int, do_id: int) -> bool:
        url = f"{self.base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}"
        try:
            r = self.h.delete(url)
            return r.status_code in (200, 204)
        except Exception as exc:
            print(f"  [net] DELETE {url}: {exc}")
            return False

    # ── uploads ──────────────────────────────────────────────────────────────

    def upload_file_v2(self, parent_do_app_id: str, path_or_bytes: Path | bytes, display_name: str) -> bool:
        """Upload as v2 singleton file. Returns True on 2xx."""
        url = f"{self.base}/v2/files"
        params = {"parentDataObjectAppId": parent_do_app_id, "name": display_name}
        if isinstance(path_or_bytes, Path):
            with path_or_bytes.open("rb") as fh:
                r = self.h.post(url, params=params, files={"file": (display_name, fh)})
        else:
            r = self.h.post(url, params=params, files={"file": (display_name, io.BytesIO(path_or_bytes))})
        if not r.is_success:
            print(f"  [http {r.status_code}] v2 upload {display_name}: {r.text[:300]}")
        return r.is_success

    def upload_file_v1(self, coll_id: int, do_id: int, path_or_bytes: Path | bytes, display_name: str) -> bool:
        url = f"{self.base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences"
        if isinstance(path_or_bytes, Path):
            with path_or_bytes.open("rb") as fh:
                r = self.h.post(url, files={"file": (display_name, fh)})
        else:
            r = self.h.post(url, files={"file": (display_name, io.BytesIO(path_or_bytes))})
        if not r.is_success:
            print(f"  [http {r.status_code}] v1 upload {display_name}: {r.text[:300]}")
        return r.is_success

    def post_structured_data_ref_v1(self, coll_id: int, do_id: int, name: str, data: Any) -> bool:
        url = f"{self.base}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/structuredDataReferences"
        body = {"name": name, "data": data}
        r = self._post(url, body)
        return r is not None and r.is_success

    # ── snapshots ────────────────────────────────────────────────────────────

    def create_snapshot(self, coll_app_id: str, label: str, description: str = "") -> dict | None:
        url = f"{self.base}/v2/collections/{coll_app_id}/snapshots"
        body = {"label": label, "description": description or f"wiki-extract @ {EXTRACT_TIME}"}
        r = self._post(url, body)
        return r.json() if r is not None and r.is_success else None

    # ── low-level ────────────────────────────────────────────────────────────

    # Retry transient errors (Cloudflare 5xx, network blips). Capped backoff.
    _RETRY_STATUSES = {502, 503, 504, 520, 521, 522, 523, 524}

    def _request_with_retry(self, method: str, url: str, max_attempts: int = 5, **kw):
        last_exc: Exception | None = None
        for attempt in range(1, max_attempts + 1):
            try:
                r = self.h.request(method, url, **kw)
                if r.status_code in self._RETRY_STATUSES and attempt < max_attempts:
                    backoff = min(2 ** attempt, 30)
                    print(f"  [retry] {method} {url.split('?')[0]} http={r.status_code} attempt={attempt} sleep={backoff}s")
                    time.sleep(backoff)
                    continue
                return r
            except Exception as exc:
                last_exc = exc
                if attempt < max_attempts:
                    backoff = min(2 ** attempt, 30)
                    print(f"  [retry] {method} {url.split('?')[0]} exc={exc!r} attempt={attempt} sleep={backoff}s")
                    time.sleep(backoff)
                    continue
                raise
        if last_exc:
            raise last_exc
        return None

    def _get(self, url: str, params: dict | None = None, quiet: bool = False):
        try:
            r = self._request_with_retry("GET", url, params=params)
            if r is not None and not r.is_success and not quiet:
                print(f"  [http {r.status_code}] GET {url.split('?')[0]}: {r.text[:300]}")
            return r
        except Exception as exc:
            if not quiet:
                print(f"  [net] GET {url}: {exc}")
            return None

    def _post(self, url: str, body: dict):
        try:
            r = self._request_with_retry("POST", url, json=body)
            if r is not None and not r.is_success:
                print(f"  [http {r.status_code}] POST {url}: {r.text[:300]}")
            return r
        except Exception as exc:
            print(f"  [net] POST {url}: {exc}")
            return None

    def _put(self, url: str, body: dict):
        try:
            r = self._request_with_retry("PUT", url, json=body)
            if r is not None and not r.is_success:
                print(f"  [http {r.status_code}] PUT {url}: {r.text[:300]}")
            return r
        except Exception as exc:
            print(f"  [net] PUT {url}: {exc}")
            return None


# ── Phase 0 — discovery ───────────────────────────────────────────────────────

@dataclass
class DiscoveryResult:
    zip_path: Path
    unzip_dir: Path
    tree: list[WikiNode]
    total_pages: int
    max_depth: int
    root_titles: list[str]
    zip_size: int
    has_attachments_dir: bool
    sample_page_titles: list[str]


def find_zip_in_export_do(client: ShepardClient) -> tuple[dict, dict] | tuple[None, None]:
    """Look up the wiki-export DO and its zip fileref.
    Returns (do_dict, fref_dict) or (None, None) with an error printed.
    """
    print(f"[discovery] looking up wiki-export DO (appId={WIKI_EXPORT_DO_APPID[:8]}…)")
    do = client.get_data_object_v1(COLLECTION_ID, WIKI_EXPORT_DO_APPID)
    if do is None:
        print(f"  appId lookup empty — trying name fallback {WIKI_EXPORT_DO_NAME_FALLBACK!r}")
        do = client.find_data_object_by_name_v1(COLLECTION_ID, WIKI_EXPORT_DO_NAME_FALLBACK)
    if do is None:
        print("  [FAIL] could not find the wiki-export DataObject by appId or name.")
        print(f"         Searched collection id={COLLECTION_ID}")
        print(f"         appId tried: {WIKI_EXPORT_DO_APPID}")
        print(f"         name tried:  {WIKI_EXPORT_DO_NAME_FALLBACK!r}")
        print( "         Possible causes:")
        print( "           (a) the DO was never created — check the upstream task that should have created it")
        print( "           (b) the API key lacks read permission on this DO (try a fresh JWT)")
        print( "           (c) the appId/name in the script is wrong")
        return None, None

    print(f"  found DO: id={do['id']} name={do['name']!r}")
    refs = client.list_file_refs_v1(COLLECTION_ID, do["id"])
    if not refs:
        print("  [FAIL] wiki-export DO has no file references. The 507 MB zip has not been uploaded yet.")
        return None, None

    # Prefer the first .zip
    zip_ref = next((r for r in refs if (r.get("name") or "").lower().endswith(".zip")), refs[0])
    print(f"  found file ref: id={zip_ref.get('id')} name={zip_ref.get('name')!r} size={zip_ref.get('fileSize', '?')}")
    return do, zip_ref


def discover(client: ShepardClient, tmpdir: Path) -> DiscoveryResult | None:
    do, fref = find_zip_in_export_do(client)
    if do is None or fref is None:
        return None

    zip_path = tmpdir / (fref.get("name") or "wiki-export.zip")
    print(f"\n[discovery] downloading zip → {zip_path}")
    ok = client.download_file_ref_v1(
        COLLECTION_ID, do["id"], fref["id"], zip_path,
        size_hint=fref.get("fileSize") or 0,
    )
    if not ok:
        return None
    print(f"  downloaded: {zip_path.stat().st_size:,} bytes")

    unzip_dir = tmpdir / "unzipped"
    unzip_dir.mkdir(exist_ok=True)
    print(f"[discovery] unzipping → {unzip_dir}")
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(unzip_dir)

    # The export usually has either index.html at the root of the unzip dir
    # or one nesting level deep (a space-key directory). Discover.
    candidates = list(unzip_dir.rglob("index.html"))
    if not candidates:
        print("  [FAIL] no index.html found in the zip — is this really a Confluence space export?")
        # Diagnostic: list top-level entries
        print("  top of unzip dir:")
        for p in sorted(unzip_dir.iterdir())[:20]:
            print(f"    {p.name}")
        return None
    # Prefer the shallowest index.html
    index_path = sorted(candidates, key=lambda p: len(p.parts))[0]
    print(f"  found index.html at: {index_path.relative_to(unzip_dir)}")

    index_html = index_path.read_text(encoding="utf-8", errors="replace")
    tree = parse_confluence_toc(index_html)

    flat = list(flatten_tree(tree))
    total_pages = len(flat)
    max_depth = max((d for _, d, _ in flat), default=0)
    roots = [n.title for n in tree]
    sample_titles = [n.title for n, _, _ in flat[:10]]

    has_attachments = (index_path.parent / "attachments").is_dir() or any(
        p.is_dir() and p.name == "attachments" for p in unzip_dir.rglob("attachments")
    )

    print()
    print("════════════════════════════════════════════════════════════════")
    print("  [STATUS] Phase 0 — Discovery")
    print("════════════════════════════════════════════════════════════════")
    print(f"  zip path        : {zip_path}")
    print(f"  zip size        : {zip_path.stat().st_size:,} bytes ({zip_path.stat().st_size / 1024 / 1024:.1f} MB)")
    print(f"  unzip dir       : {unzip_dir}")
    print(f"  index.html      : {index_path.relative_to(unzip_dir)}")
    print(f"  total pages     : {total_pages}")
    print(f"  max tree depth  : {max_depth}")
    print(f"  root titles     : {len(roots)}")
    for t in roots[:10]:
        print(f"    - {t!r}")
    if len(roots) > 10:
        print(f"    … and {len(roots) - 10} more")
    print(f"  attachments dir : {'yes' if has_attachments else 'no'}")
    print(f"  sample pages    :")
    for t in sample_titles:
        print(f"    · {t!r}")
    print("════════════════════════════════════════════════════════════════")
    print()

    return DiscoveryResult(
        zip_path=zip_path,
        unzip_dir=unzip_dir,
        tree=tree,
        total_pages=total_pages,
        max_depth=max_depth,
        root_titles=roots,
        zip_size=zip_path.stat().st_size,
        has_attachments_dir=has_attachments,
        sample_page_titles=sample_titles,
    )


# ── Phase 1 — 5-page sample test ──────────────────────────────────────────────

def pick_sample_pages(tree: list[WikiNode]) -> list[tuple[WikiNode, int, str]]:
    """Pick 5 representative pages: 1 root, 2 nested, 2 leaves."""
    flat = list(flatten_tree(tree))
    if not flat:
        return []
    roots = [t for t in flat if t[1] == 0]
    nested = [t for t in flat if t[1] > 0 and t[0].children]
    leaves = [t for t in flat if not t[0].children]

    sample: list[tuple[WikiNode, int, str]] = []
    if roots:
        sample.append(roots[0])
    sample.extend(nested[:2])
    sample.extend([l for l in leaves[:2] if l not in sample])

    # Dedupe while preserving order, cap at 5
    seen: set[str] = set()
    out: list[tuple[WikiNode, int, str]] = []
    for s in sample:
        if s[0].page_id in seen:
            continue
        seen.add(s[0].page_id)
        out.append(s)
        if len(out) == 5:
            break
    # Pad from the flat list if we got fewer than 5
    for s in flat:
        if len(out) == 5:
            break
        if s[0].page_id not in seen:
            seen.add(s[0].page_id)
            out.append(s)
    return out


def find_page_html(unzip_dir: Path, page_id: str, href: str) -> Path | None:
    """Resolve a node's href to its actual file under unzip_dir."""
    # The index.html sits in `unzip_dir/<spacekey>/index.html`; pages are
    # siblings.  Try both same-dir and root.
    candidates = list(unzip_dir.rglob(href))
    if candidates:
        return candidates[0]
    # Try pageId.html
    candidates = list(unzip_dir.rglob(f"{page_id}.html"))
    if candidates:
        return candidates[0]
    return None


@dataclass
class Phase1Result:
    created_do_ids: list[int]
    failures: list[str]


def run_phase1(client: ShepardClient, root_do: dict, discovery: DiscoveryResult) -> Phase1Result:
    """Create 5 sample DOs; verify each; rollback on any failure."""
    sample = pick_sample_pages(discovery.tree)
    print(f"\n[phase1] selected {len(sample)} sample pages:")
    for node, depth, path in sample:
        print(f"  depth={depth} title={node.title!r} pageId={node.page_id}")

    created: list[int] = []
    failures: list[str] = []
    page_id_to_do: dict[str, dict] = {}

    for node, depth, path in sample:
        html_file = find_page_html(discovery.unzip_dir, node.page_id, node.href)
        if html_file is None:
            failures.append(f"page {node.page_id!r} ({node.title!r}): html file not found in zip")
            continue

        html_bytes = html_file.read_bytes()
        meta = extract_page_metadata(html_file.read_text(encoding="utf-8", errors="replace"), node.page_id)
        attrs = {
            **meta,
            "wiki_path": path,
            "wiki_depth": str(depth),
            "session": SESSION,
            "extract_time": EXTRACT_TIME,
            "phase": "phase1-test",
            "source_zip_path": str(discovery.zip_path.name),
        }

        do_name = f"phase1-test/{node.title}"[:200]

        # Parent: link to wiki-pages root for now (real Phase 3 will link to
        # the page's Confluence parent).
        do = client.create_data_object_v1(
            COLLECTION_ID,
            do_name,
            description=f"Phase 1 sample page from Confluence wiki (pageId={node.page_id}).",
            attributes=attrs,
            parent_id=root_do["id"],
        )
        if do is None:
            failures.append(f"create DO failed: {do_name!r}")
            continue

        created.append(do["id"])
        page_id_to_do[node.page_id] = do
        print(f"  [ok] created DO id={do['id']} appId={do.get('appId')} name={do_name!r}")

        # Upload the HTML
        ok = client.upload_file_v2(do["appId"], html_bytes, f"{node.page_id}.html")
        if not ok:
            ok = client.upload_file_v1(COLLECTION_ID, do["id"], html_bytes, f"{node.page_id}.html")
        if not ok:
            failures.append(f"upload html failed: {do_name!r}")

    # Verify
    print(f"\n[phase1] verifying {len(created)} created DOs...")
    for do_id in created:
        refs = client.list_file_refs_v1(COLLECTION_ID, do_id)
        if not refs:
            failures.append(f"verify failed: DO id={do_id} has no fileReferences")
        else:
            print(f"  ✓ DO {do_id}: {len(refs)} fileRef(s)")

    if failures:
        print(f"\n[phase1] {len(failures)} failure(s) — rolling back created DOs:")
        for fail in failures:
            print(f"  ✗ {fail}")
        for do_id in created:
            print(f"  [rollback] deleting DO {do_id}")
            client.delete_data_object_v1(COLLECTION_ID, do_id)
        return Phase1Result(created_do_ids=[], failures=failures)

    print(f"\n[phase1] all {len(created)} sample pages verified successfully")
    return Phase1Result(created_do_ids=created, failures=[])


# ── Phase 3 — full extraction (gated behind --commit) ─────────────────────────

def run_phase3_full_extract(
    client: ShepardClient,
    root_do: dict,
    discovery: DiscoveryResult,
) -> dict[str, Any]:
    """Iterate every page, create DOs, upload HTML, build TOC, snapshot."""
    flat = list(flatten_tree(discovery.tree))
    page_id_to_do: dict[str, dict] = {}
    success = 0
    failed: list[str] = []

    print(f"\n[phase3] full extraction: {len(flat)} pages")

    # First pass: create all DOs (without predecessor links yet).
    with tqdm(total=len(flat), desc="  creating DOs", unit="DO", file=sys.stderr) as bar:
        for node, depth, path in flat:
            bar.update(1)
            html_file = find_page_html(discovery.unzip_dir, node.page_id, node.href)
            if html_file is None:
                failed.append(f"{node.page_id}: html missing")
                continue
            html_bytes = html_file.read_bytes()
            meta = extract_page_metadata(html_file.read_text(encoding="utf-8", errors="replace"), node.page_id)
            attrs = {
                **meta,
                "wiki_path": path,
                "wiki_depth": str(depth),
                "session": SESSION,
                "extract_time": EXTRACT_TIME,
            }
            do = client.create_data_object_v1(
                COLLECTION_ID,
                node.title[:200],
                description=f"Confluence wiki page (pageId={node.page_id}).",
                attributes=attrs,
                parent_id=root_do["id"],
            )
            if do is None:
                failed.append(f"{node.page_id}: create failed")
                continue
            page_id_to_do[node.page_id] = do
            ok = client.upload_file_v2(do["appId"], html_bytes, f"{node.page_id}.html")
            if not ok:
                ok = client.upload_file_v1(COLLECTION_ID, do["id"], html_bytes, f"{node.page_id}.html")
            if not ok:
                failed.append(f"{node.page_id}: upload failed")
                continue
            success += 1

    # Second pass: predecessor wiring from the Confluence parent/child relation.
    print(f"\n[phase3] wiring predecessor links...")

    def walk(parent: WikiNode | None, children: list[WikiNode]) -> None:
        for child in children:
            if parent is not None:
                pdo = page_id_to_do.get(parent.page_id)
                cdo = page_id_to_do.get(child.page_id)
                if pdo and cdo:
                    client._link_predecessor_v1(COLLECTION_ID, cdo["id"], pdo["id"])
            walk(child, child.children)

    walk(None, discovery.tree)

    # TOC DO + structured-data ref
    print(f"\n[phase3] creating TOC DO...")
    toc_do = client.create_data_object_v1(
        COLLECTION_ID,
        WIKI_TOC_DO_NAME,
        description=f"Hierarchical TOC of Confluence wiki pages. Generated {EXTRACT_TIME}.",
        attributes={"session": SESSION, "extract_time": EXTRACT_TIME, "type": "wiki-toc"},
    )
    if toc_do:
        # Serialize tree to JSON
        def serialize(nodes: list[WikiNode]) -> list[dict]:
            return [
                {
                    "pageId": n.page_id,
                    "title": n.title,
                    "doAppId": page_id_to_do.get(n.page_id, {}).get("appId"),
                    "children": serialize(n.children),
                }
                for n in nodes
            ]
        tree_json = serialize(discovery.tree)
        client.post_structured_data_ref_v1(COLLECTION_ID, toc_do["id"], "wiki-tree", tree_json)

    # Snapshot
    print(f"\n[phase3] taking snapshot...")
    snap = client.create_snapshot(
        COLLECTION_APP_ID,
        label=f"wiki-extracted-{SESSION}@MFFD-Dropbox",
        description=f"After Phase 3 wiki extraction: {success} pages, {len(failed)} failed.",
    )

    print()
    print("════════════════════════════════════════════════════════════════")
    print("  [STATUS] Phase 3 — Full extraction")
    print("════════════════════════════════════════════════════════════════")
    print(f"  total pages   : {len(flat)}")
    print(f"  success       : {success}")
    print(f"  failed        : {len(failed)}")
    print(f"  toc_do_app_id : {toc_do.get('appId') if toc_do else '(none)'}")
    print(f"  snapshot_id   : {snap.get('appId') if snap else '(failed)'}")
    print("════════════════════════════════════════════════════════════════")
    if failed:
        print("\n  First 20 failures:")
        for f in failed[:20]:
            print(f"    ✗ {f}")
    return {"success": success, "failed": failed, "snapshot": snap, "toc_do": toc_do}


# ── Setup helpers ─────────────────────────────────────────────────────────────

def ensure_wiki_pages_root(client: ShepardClient) -> dict | None:
    existing = client.find_data_object_by_name_v1(COLLECTION_ID, WIKI_PAGES_ROOT_NAME)
    if existing:
        print(f"[setup] wiki-pages root DO already exists: id={existing['id']}")
        return existing
    print(f"[setup] creating wiki-pages root DO {WIKI_PAGES_ROOT_NAME!r}")
    do = client.create_data_object_v1(
        COLLECTION_ID,
        WIKI_PAGES_ROOT_NAME,
        description=(
            "Root of the Confluence wiki extraction tree. Each child DO is "
            "one Confluence page. Created by mffd-wiki-extract.py."
        ),
        attributes={
            "session": SESSION,
            "extract_time": EXTRACT_TIME,
            "type": "wiki-pages-root",
        },
    )
    return do


def run_self_tests() -> None:
    """Run the bundled pytest suite before doing anything dangerous."""
    test_file = Path(__file__).with_name("test_wiki_extract.py")
    if not test_file.exists():
        print(f"WARNING: {test_file.name} not found — skipping self-tests.")
        return
    print(f"[selftest] running {test_file.name} ...")
    result = subprocess.run(
        [sys.executable, "-m", "pytest", str(test_file), "-x", "-q"],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr, file=sys.stderr)
        sys.exit("ERROR: ToC parser self-tests failed. Refusing to mutate Shepard with a broken parser.")
    # Print a one-liner so the operator sees the suite ran.
    last_line = (result.stdout.strip().splitlines() or ["(no output)"])[-1]
    print(f"[selftest] OK — {last_line}")


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--commit", action="store_true",
                    help="Proceed to Phase 3 (full extraction). Without this flag the script "
                         "runs Phase 0 + Phase 1 and STOPS at Phase 2.")
    ap.add_argument("--phase0-only", action="store_true",
                    help="Run only Phase 0 (discovery) — no mutation at all.")
    ap.add_argument("--skip-phase1", action="store_true",
                    help="Skip the Phase 1 sample-test (NOT recommended; only use if Phase 1 has run successfully already).")
    args = ap.parse_args()

    run_self_tests()

    if not API_KEY:
        sys.exit("ERROR: set SHEPARD_NUCLIDE_API_KEY (or SHEPARD_API_KEY) env var.")

    client = ShepardClient(SHEPARD_URL, API_KEY)
    try:
        me = client.whoami()
        if me:
            print(f"[auth] whoami: {me.get('username') or me.get('sub') or me}")
        else:
            print("[auth] whoami: (no userinfo endpoint matched, continuing)")

        with tempfile.TemporaryDirectory(prefix="mffd-wiki-") as tmp:
            tmpdir = Path(tmp)
            discovery = discover(client, tmpdir)
            if discovery is None:
                return 2

            if args.phase0_only:
                print("[phase0-only] done. No mutation performed.")
                return 0

            # Ensure root DO exists (cheap and safe to create early).
            root = ensure_wiki_pages_root(client)
            if root is None:
                print("[FAIL] could not create or find the wiki-pages root DO.")
                return 3

            if not args.skip_phase1:
                p1 = run_phase1(client, root, discovery)
                if p1.failures:
                    print("[phase1] FAILED — see above. Phase 2 STOP — not proceeding.")
                    return 4

            if not args.commit:
                print()
                print("════════════════════════════════════════════════════════════════")
                print("  [PHASE 2 STOP]")
                print("════════════════════════════════════════════════════════════════")
                print(f"  Phase 0 + Phase 1 succeeded. Ready to extract {discovery.total_pages} pages.")
                print(f"  To proceed: re-run with the --commit flag.")
                print()
                print(f"    SHEPARD_NUCLIDE_API_KEY=... uv run python {Path(__file__).name} --commit")
                print("════════════════════════════════════════════════════════════════")
                return 0

            # Phase 3 — full extraction
            run_phase3_full_extract(client, root, discovery)
            return 0
    finally:
        client.close()


if __name__ == "__main__":
    sys.exit(main())
