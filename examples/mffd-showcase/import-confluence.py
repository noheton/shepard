#!/usr/bin/env python3
"""
Task #137 — Import MFFD Confluence HTML export into Shepard.

Reads 111 content pages from the Confluence HTML export and creates:
  1. One "MFFD Wiki — Table of Contents" DataObject with a lab-journal index entry.
  2. One DataObject per page with three semantic annotations each.

API surface used:
  POST /v2/collections/{collAppId}/data-objects           — create DO
  POST /shepard/api/labJournalEntries?dataObjectId={id}   — add lab-journal entry (v1)
  POST /v2/annotations                                    — add semantic annotation

Run:
  cd examples/mffd-showcase && python3 import-confluence.py
"""

import json
import os
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests
from bs4 import BeautifulSoup

# ── config ─────────────────────────────────────────────────────────────────────

API_BASE = "https://shepard-api.nuclide.systems"
API_KEY = (
    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJlZTRjMDEwZi1kNjQ4LTQ2MzAtYWVhNi1iODFl"
    "ZjJhOWMyOTYiLCJpc3MiOiJodHRwOi8vc2hlcGFyZC1hcGkubnVjbGlkZS5zeXN0ZW1zLyIs"
    "Im5iZiI6MTc3OTQzMTY1MiwiaWF0IjoxNzc5NDMxNjUyLCJqdGkiOiI3NmMyMGM2MC1hMzVm"
    "LTQwMjQtODdhNi0xYjU0ZGRiMzcxZWIifQ.ZBY9YQZyje_ketIGB2za50H76XR-oYmCWy6w"
    "HdySBX3o2mhWgGCASrjjkmIyRDwlmQfM4MR-BtTUzS7Vp1XTROERu3AbiF-y-7CWmxHWvP0"
    "NVJ1Cl_EjdcXJjztnU8rjb-jTY5t1WOQeSgBszMDq8cwNY-67w4Xj5tyvQRq7i928kIHFiep"
    "fKg6mCHo6JVHMIdJyUHKri9J1GmbopdM7pdpN074BYxYzZQ8qCgMDN2MrMq37HjDwFrhhu1y"
    "7BPDJuglCXdM0jtU--L5aSZyENcMCiwZQyPf6Bf3AX7ddY2EDsNtB7xFgeJ7XtHVTs4yItHZ"
    "mm0TTdb1-Q7lFQ19-Cg"
)
COLL_APPID = "019e55f3-75fb-7ef3-84fc-6238566b63ea"
COLL_NUM_ID = 661923
HTML_DIR = os.path.join(
    os.path.dirname(__file__),
    "raw-data/mffd-data/mffd-confluence-space-export/MFFD",
)
MAX_WORKERS = 4
MAX_RETRIES = 3
DESC_MAX_CHARS = 400


# ── HTTP helpers ───────────────────────────────────────────────────────────────

def _headers():
    return {"X-API-KEY": API_KEY, "Content-Type": "application/json"}


def _post_with_retry(url: str, payload: dict) -> dict:
    """POST with exponential backoff on 429 / 5xx. Raises on persistent failure."""
    delay = 1
    last_exc = None
    for attempt in range(MAX_RETRIES):
        try:
            r = requests.post(url, headers=_headers(), json=payload, timeout=30)
            if r.status_code in (429, 500, 502, 503, 504):
                print(f"    [retry {attempt+1}/{MAX_RETRIES}] HTTP {r.status_code} on {url}", flush=True)
                time.sleep(delay)
                delay *= 2
                continue
            r.raise_for_status()
            return r.json()
        except requests.RequestException as exc:
            last_exc = exc
            print(f"    [retry {attempt+1}/{MAX_RETRIES}] {exc}", flush=True)
            time.sleep(delay)
            delay *= 2
    raise RuntimeError(f"Failed after {MAX_RETRIES} attempts: {last_exc}")


# ── Phase 1: parse HTML files ──────────────────────────────────────────────────

def parse_page(filepath: str) -> dict:
    """Extract metadata from a single Confluence HTML file."""
    fname = os.path.basename(filepath)
    with open(filepath, encoding="utf-8") as fh:
        soup = BeautifulSoup(fh.read(), "html.parser")

    # Title: strip "MFFD : " prefix
    raw_title = soup.title.string.strip() if soup.title else fname
    title = re.sub(r"^MFFD\s*:\s*", "", raw_title).strip() or fname

    # Breadcrumbs
    bc_div = soup.find(id="breadcrumb-section")
    breadcrumbs = [a.get_text(strip=True) for a in bc_div.find_all("a")] if bc_div else []

    # Section = last breadcrumb beyond "MFFD" and "MFFD Startseite", else "Root"
    section = breadcrumbs[-1] if len(breadcrumbs) > 2 else "Root"

    # Breadcrumb path (human-readable chain)
    bc_path = " > ".join(breadcrumbs) if breadcrumbs else "MFFD"

    # Page ID from filename numeric suffix
    m = re.search(r"_?(\d+)\.html$", fname)
    page_id = m.group(1) if m else "unknown"

    # Description: plain text from main-content, first DESC_MAX_CHARS chars
    mc = soup.find(id="main-content")
    if mc:
        text = " ".join(mc.get_text(separator=" ").split())
        description = text[:DESC_MAX_CHARS]
        if len(text) > DESC_MAX_CHARS:
            description = description.rstrip() + "…"
    else:
        description = ""

    return {
        "title": title,
        "section": section,
        "breadcrumbs": breadcrumbs,
        "bc_path": bc_path,
        "page_id": page_id,
        "description": description,
        "filename": fname,
    }


def load_all_pages() -> list[dict]:
    files = sorted(
        f for f in os.listdir(HTML_DIR) if f.endswith(".html") and f != "index.html"
    )
    pages = []
    for fname in files:
        pages.append(parse_page(os.path.join(HTML_DIR, fname)))
    return pages


# ── Phase 2: TOC DataObject ────────────────────────────────────────────────────

def build_toc_journal_body(pages: list[dict]) -> str:
    """Build the markdown table for the TOC lab journal entry."""
    # Sort by section, then title
    sorted_pages = sorted(pages, key=lambda p: (p["section"].lower(), p["title"].lower()))

    lines = [
        "# Index of all MFFD wiki pages",
        "",
        f"Auto-imported from Confluence HTML export — {len(pages)} pages.",
        "",
        "| Section | Title | Confluence ID |",
        "| --- | --- | --- |",
    ]
    for p in sorted_pages:
        # Escape pipe chars in titles/sections
        section = p["section"].replace("|", "\\|")
        title = p["title"].replace("|", "\\|")
        lines.append(f"| {section} | {title} | {p['page_id']} |")

    return "\n".join(lines)


def create_toc_do(pages: list[dict]) -> dict:
    """Create the TOC DataObject and add the index lab journal entry."""
    print("\n[Phase 2] Creating TOC DataObject…", flush=True)
    payload = {
        "name": "MFFD Wiki — Table of Contents",
        "description": (
            "Structured Confluence wiki export from MFFD ZLP Augsburg. "
            f"{len(pages)} pages covering AFP layup process, welding (bridge + "
            "ultrasonic + resistance), calibration, test logs, materials, QA, "
            "and lessons learned."
        ),
        "status": "PUBLISHED",
    }
    do = _post_with_retry(
        f"{API_BASE}/v2/collections/{COLL_APPID}/data-objects", payload
    )
    do_appid = do["appId"]
    do_num_id = do["id"]
    print(f"  Created TOC DO: appId={do_appid} id={do_num_id}", flush=True)

    # Lab journal entry (v1 path, uses numeric id)
    journal_body = build_toc_journal_body(pages)
    lj_payload = {"journalContent": journal_body}
    lj_url = f"{API_BASE}/shepard/api/labJournalEntries?dataObjectId={do_num_id}"
    lj = _post_with_retry(lj_url, lj_payload)
    print(f"  Created lab journal entry: id={lj.get('id')} appId={lj.get('appId')}", flush=True)

    return {"appId": do_appid, "numericId": do_num_id}


# ── Phase 3: per-page DataObjects ─────────────────────────────────────────────

def create_annotation(do_appid: str, predicate: str, value: str) -> None:
    """Post a single semantic annotation against a DataObject."""
    payload = {
        "subjectAppId": do_appid,
        "subjectKind": "DataObject",
        "predicateIri": predicate,
        "objectLiteral": value,
        "vocabularyId": None,
    }
    _post_with_retry(f"{API_BASE}/v2/annotations", payload)


def import_page(idx: int, total: int, page: dict) -> dict:
    """
    Create one DataObject for a Confluence page, then attach 3 annotations.
    Returns a result dict with status, appId, numericId, and title.
    """
    title = page["title"]
    if idx % 10 == 1 or idx == total:
        print(f"  [{idx}/{total}] Creating: {title}", flush=True)

    try:
        do_payload = {
            "name": title,
            "description": page["description"] or f"MFFD wiki page (Confluence ID {page['page_id']})",
            "status": "PUBLISHED",
        }
        do = _post_with_retry(
            f"{API_BASE}/v2/collections/{COLL_APPID}/data-objects", do_payload
        )
        do_appid = do["appId"]
        do_num_id = do["id"]

        # 3 annotations
        create_annotation(do_appid, "urn:shepard:mffd:confluence_page_id", page["page_id"])
        create_annotation(do_appid, "urn:shepard:mffd:confluence_section", page["section"])
        create_annotation(do_appid, "urn:shepard:mffd:confluence_breadcrumb_path", page["bc_path"])

        return {
            "status": "ok",
            "title": title,
            "appId": do_appid,
            "numericId": do_num_id,
            "page_id": page["page_id"],
        }
    except Exception as exc:
        print(f"  [ERROR] {title}: {exc}", file=sys.stderr, flush=True)
        return {"status": "error", "title": title, "error": str(exc)}


def import_all_pages(pages: list[dict]) -> list[dict]:
    print(f"\n[Phase 3] Importing {len(pages)} pages with {MAX_WORKERS} workers…", flush=True)
    results = [None] * len(pages)
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        futures = {
            pool.submit(import_page, i + 1, len(pages), page): i
            for i, page in enumerate(pages)
        }
        for fut in as_completed(futures):
            idx = futures[fut]
            try:
                results[idx] = fut.result()
            except Exception as exc:
                results[idx] = {
                    "status": "error",
                    "title": pages[idx]["title"],
                    "error": str(exc),
                }
    return results


# ── Phase 4: summary ───────────────────────────────────────────────────────────

def print_summary(toc: dict, results: list[dict]) -> None:
    ok = [r for r in results if r and r.get("status") == "ok"]
    errors = [r for r in results if r and r.get("status") == "error"]

    print("\n" + "=" * 70)
    print("IMPORT COMPLETE")
    print("=" * 70)
    print(f"  Total pages processed : {len(results)}")
    print(f"  Successes             : {len(ok)}")
    print(f"  Errors                : {len(errors)}")
    print()
    toc_url = (
        f"https://shepard.nuclide.systems/collections/{toc['numericId']}"
        f"/dataobjects/{toc['numericId']}"
    )
    # Use appId-based URL (more stable, works with v2 routing)
    toc_url_appid = (
        f"https://shepard.nuclide.systems/collections/{COLL_APPID}"
        f"/dataobjects/{toc['appId']}"
    )
    print(f"  TOC DataObject (appId URL) : {toc_url_appid}")
    print(f"  TOC DataObject appId       : {toc['appId']}")
    print(f"  TOC DataObject numeric id  : {toc['numericId']}")

    if errors:
        print("\nFailed pages:")
        for r in errors:
            print(f"  - {r.get('title')}: {r.get('error')}")
    print()


# ── main ───────────────────────────────────────────────────────────────────────

def main():
    print("=" * 70)
    print("Task #137 — MFFD Confluence → Shepard DataObject import")
    print("=" * 70)

    # Phase 1
    print("\n[Phase 1] Parsing HTML files…", flush=True)
    pages = load_all_pages()
    print(f"  Parsed {len(pages)} content pages.", flush=True)

    # Show section breakdown
    from collections import Counter
    sections = Counter(p["section"] for p in pages)
    print("\n  Section breakdown:")
    for sec, count in sorted(sections.items(), key=lambda x: -x[1]):
        print(f"    {count:3d}  {sec}")

    # Phase 2
    toc = create_toc_do(pages)

    # Phase 3
    results = import_all_pages(pages)

    # Phase 4
    print_summary(toc, results)


if __name__ == "__main__":
    main()
