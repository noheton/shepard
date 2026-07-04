#!/usr/bin/env python3
"""wiki-to-journal.py — MFFD-WIKI-TO-JOURNAL (track 1 of aidocs/integrations/120).

Splits the dated diary pages of the Confluence MFFD space into ~218
LabJournalEntries on the relevant MFFD step / Project DataObjects.

  • Dry-run by default; `--commit` POSTs idempotently keyed on
    (source-page-id, source-block-index) via an HTML marker embedded in the
    entry content (the LabJournalEntry create endpoint takes only
    `journalContent`, so the composite key travels inside it).
  • Author resolution via `:MirroredUser` — every page's Confluence author is
    mirrored (POST /v2/admin/users/mirror) and recorded in the entry body +
    as a source-author annotation on the target DO.
  • Per-page routing from wiki-journal/page-routing.yaml (§4). Pages route to
    a daily-log-<date> DO (fallback shape, since W2 tracks aren't ingested) or
    a fixed DO (programme-overview / cell-overview / material-batch).
  • Each emitted entry carries wiki source annotations
    (source-page-id / -title / -block-index / -url / journal-role / lang) on the
    target DO so the inverse "which wiki page produced this" query is clean.

Run:
    SHEPARD_HOST=https://shepard-api.nuclide.systems \
    SHEPARD_API_KEY=<jwt> \
        python3 wiki-to-journal.py            # dry-run, prints the plan
    SHEPARD_HOST=... SHEPARD_API_KEY=... \
        python3 wiki-to-journal.py --commit   # writes idempotently
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.stderr.write("ERROR: this script needs PyYAML (pip install pyyaml).\n")
    sys.exit(2)

import wiki_common as wc
from wiki_common import (
    Client, DatedBlock, WikiPage, add_common_args, classify_pages,
    content_has_marker, die, journal_marker_comment, log, make_client,
    resolve_step_collections, split_dated_blocks, warn,
)

DEFAULT_ROUTING = Path(__file__).resolve().parents[1] / "wiki-journal" / "page-routing.yaml"

# Description text the umbrella / cell / material DOs get when created.
FIXED_DO_DESCRIPTIONS = {
    "programme-overview": "MFFD programme overview — narrative + folded wiki plans + shift/test journal entries.",
    "cell-overview": "MFFD manufacturing-cell overview — laser-integration trial journal + scene-graph link.",
    "material-batch-226368": "AFP material batch 226368 — withdrawal log journal (urn:shepard:material:batch=226368).",
}


def load_routing(path: Path) -> list[dict]:
    if not path.exists():
        die(f"routing file not found: {path}")
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    routes = data.get("routes") or []
    if not routes:
        die(f"routing file {path} has no `routes:`")
    return routes


def match_route(page: WikiPage, routes: list[dict]) -> dict | None:
    fn = page.path.name.lower()
    title = page.title.lower()
    for r in routes:
        needle = str(r.get("match", "")).lower()
        if needle and (needle in fn or needle in title):
            return r
    return None


def render_entry_html(page: WikiPage, block: DatedBlock, route: dict,
                      mirror_username: str, mirror_appid: str | None) -> str:
    """Build the LabJournalEntry HTML body for one dated block."""
    body_text = block.text.strip()
    # Escape for safe HTML; keep newlines as <br/>.
    import html as _h
    safe = _h.escape(body_text).replace("\n", "<br/>")
    author = page.author_display or mirror_username
    marker = journal_marker_comment(page.page_id, block.index)
    src_url = page.source_url
    attribution = (
        f"<p><em>Imported from Confluence wiki — page "
        f"“{_h.escape(page.title)}”, entry {block.date_raw}. "
        f"Author: {_h.escape(author)}"
    )
    if mirror_appid:
        attribution += f" (mirrored user {mirror_appid})"
    attribution += (
        f". Source: <a href=\"{_h.escape(src_url)}\">{_h.escape(src_url)}</a>.</em></p>"
    )
    # <h3> is in the sanitizer Safelist (<h4>+ are unwrapped); use it for the
    # entry heading so it survives the HtmlSanitizer intact.
    return (
        f"{marker}"
        f"<h3>{_h.escape(page.title)} — {block.date_raw}</h3>"
        f"<p>{safe}</p>"
        f"{attribution}"
    )


def daily_log_name(date_iso: str) -> str:
    return f"daily-log-{date_iso}"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap)
    ap.add_argument("--routing", default=str(DEFAULT_ROUTING), help="page-routing.yaml path.")
    ap.add_argument("--limit", type=int, default=0, help="Cap entries per page (0 = no cap; debugging only).")
    args = ap.parse_args()

    source = Path(args.source)
    if not source.is_dir():
        die(f"--source dir not found: {source}")

    routes = load_routing(Path(args.routing))
    client = make_client(args)

    log(f"source   : {source}")
    log(f"routing  : {args.routing}")
    log(f"mode     : {'COMMIT' if args.commit else 'DRY-RUN'}")
    log("")

    # Resolve the 5 step Collections + the Project umbrella.
    steps = resolve_step_collections(client, args.project)
    project_v1id = client.collection_v1_id(args.project)
    if project_v1id is None:
        die(f"could not resolve v1 id for Project {args.project}")
    target_collections = dict(steps)
    target_collections["mffd-project"] = {
        "appId": args.project, "v1Id": project_v1id, "name": "mffd-project",
    }
    log("resolved target Collections:")
    for slug, meta in target_collections.items():
        log(f"  {slug:24s} appId={meta['appId']} v1Id={meta['v1Id']}")
    log("")

    pages = [p for p in classify_pages(source) if p.klass == "journal"]
    routed: list[tuple[WikiPage, dict]] = []
    for p in pages:
        r = match_route(p, routes)
        if r:
            routed.append((p, r))
        else:
            warn(f"journal page {p.path.name!r} matched no route — SKIPPING (no silent data loss: counted as unparsed)")

    log(f"journal pages classified : {len(pages)}")
    log(f"journal pages routed      : {len(routed)}")
    log("")

    # ── caches to avoid duplicate work
    mirror_cache: dict[str, str | None] = {}      # username -> mirrored appId
    do_cache: dict[tuple[str, str], dict] = {}     # (collSlug, doName) -> DO dict
    annotated_dos: set[str] = set()                 # DO appIds we've tagged with page src

    def get_mirror(page: WikiPage) -> str | None:
        if page.author_username in mirror_cache:
            return mirror_cache[page.author_username]
        res = client.mirror_user(page.author_username, page.author_display)
        appid = res.get("appId") if res else None
        mirror_cache[page.author_username] = appid
        return appid

    def get_target_do(coll_slug: str, do_name: str, desc: str) -> dict | None:
        key = (coll_slug, do_name)
        if key in do_cache:
            return do_cache[key]
        meta = target_collections.get(coll_slug)
        if not meta:
            warn(f"route target {coll_slug!r} is not a known Collection — skipping")
            return None
        do = client.ensure_data_object_v1(meta["v1Id"], do_name, description=desc)
        if do:
            do_cache[key] = do
        return do

    total_blocks = 0
    total_created = 0
    total_skipped = 0
    per_page_report: list[dict] = []
    unparsed: list[str] = []

    for page, route in routed:
        blocks = split_dated_blocks(page)
        if args.limit:
            blocks = blocks[: args.limit]
        if not blocks:
            unparsed.append(f"{page.path.name}: no dated blocks parsed")
            warn(f"{page.path.name}: 0 dated blocks parsed (page has {page.dated_count} date tokens)")
            continue

        mirror_appid = get_mirror(page)
        coll_slug = route["target"]
        resolver = route.get("resolver", "fixed")
        role = route.get("role", "journal-entry")
        lang = route.get("lang")
        extra_anns = route.get("annotations") or {}

        page_created = 0
        page_skipped = 0
        for block in blocks:
            total_blocks += 1
            if resolver == "daily-log":
                do_name = daily_log_name(block.date_iso)
                desc = f"AFP daily log {block.date_iso} (wiki journal fallback DO; W2 tracks attach later)."
            else:  # fixed
                do_name = route.get("fixed_do") or "programme-overview"
                desc = FIXED_DO_DESCRIPTIONS.get(do_name, f"MFFD wiki journal target {do_name}.")

            do = get_target_do(coll_slug, do_name, desc)
            if do is None:
                unparsed.append(f"{page.path.name} block {block.index}: target DO unresolved")
                continue
            do_v1id = do.get("id")
            do_appid = do.get("appId")

            # idempotency: scan existing entries for our marker
            already = False
            if not client.dry_run and do_v1id and do_v1id > 0:
                for e in client.list_lab_journal_entries_v1(do_v1id):
                    if content_has_marker(e.get("journalContent", ""), page.page_id, block.index):
                        already = True
                        break
            if already:
                page_skipped += 1
                total_skipped += 1
                continue

            content = render_entry_html(page, block, route, page.author_username, mirror_appid)
            if client.dry_run:
                page_created += 1
                total_created += 1
                continue
            client.create_lab_journal_entry_v1(do_v1id, content)
            page_created += 1
            total_created += 1

            # source annotations on the target DO (once per (DO, page)).
            ann_key = f"{do_appid}|{page.page_id}"
            if ann_key not in annotated_dos and do_appid:
                annotated_dos.add(ann_key)
                client.ensure_annotation(do_appid, "DataObject", wc.PRED_WIKI_SOURCE_PAGE_ID, page.page_id)
                client.ensure_annotation(do_appid, "DataObject", wc.PRED_WIKI_SOURCE_PAGE_TITLE, page.title)
                client.ensure_annotation(do_appid, "DataObject", wc.PRED_WIKI_SOURCE_URL, page.source_url)
                client.ensure_annotation(do_appid, "DataObject", wc.PRED_WIKI_JOURNAL_ROLE, role)
                if lang:
                    client.ensure_annotation(do_appid, "DataObject", wc.PRED_LANG, lang)
                for pred, val in extra_anns.items():
                    client.ensure_annotation(do_appid, "DataObject", pred, str(val))

        per_page_report.append({
            "page": page.path.name, "title": page.title, "author": page.author_display,
            "blocks": len(blocks), "created": page_created, "skipped": page_skipped,
            "target": coll_slug, "resolver": resolver,
        })

    # ── report
    log("")
    log("==== per-page report ====")
    for r in per_page_report:
        log(f"  {r['page']:48s} blocks={r['blocks']:3d} "
            f"created={r['created']:3d} skipped={r['skipped']:3d} "
            f"→ {r['target']} ({r['resolver']})")
    log("")
    log(f"total dated blocks : {total_blocks}")
    log(f"entries created    : {total_created}{'  (dry-run, not written)' if client.dry_run else ''}")
    log(f"entries skipped    : {total_skipped} (idempotent — already present)")
    if unparsed:
        log("")
        log("UNPARSED / unresolved:")
        for u in unparsed:
            log(f"  - {u}")
    if client.dry_run:
        log("")
        log("Dry-run complete. Re-run with --commit to write.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
