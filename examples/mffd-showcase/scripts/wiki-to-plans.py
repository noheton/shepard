#!/usr/bin/env python3
"""wiki-to-plans.py — MFFD-WIKI-TO-PLANS (track 2 of aidocs/integrations/120).

Folds the MFFD plan documents into the Project's `programme-overview`
DataObject:

  • Attaches each plan's original HTML as a singleton FileReference
    (POST /v2/files — NOT a FileBundleReference, per CLAUDE.md) on the
    `programme-overview` DO in the MFFD Project Collection.
  • Folds a readable narrative (rendered plain text of each plan) into the DO's
    description (operator decision 2026-06-02 = "both"; §120 §1.2 / §6 row 2).
  • Annotates each plan attachment's source DO with wiki source + plan-category.

Idempotency:
  • The programme-overview DO is created once (ensure-by-name).
  • Each plan FileReference is matched by name (`<page-title>.html`) — re-runs
    skip an already-attached plan.
  • The folded narrative is rebuilt deterministically each run; on --commit it
    PATCHes the description only when it differs.

Run:
    SHEPARD_HOST=... SHEPARD_API_KEY=... python3 wiki-to-plans.py            # dry-run
    SHEPARD_HOST=... SHEPARD_API_KEY=... python3 wiki-to-plans.py --commit   # writes
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import wiki_common as wc
from wiki_common import (
    Client, WikiPage, add_common_args, classify_pages, die, log, make_client, warn,
    html_to_text, _main_content,
)

PROGRAMME_OVERVIEW_DO = "programme-overview"

# The five plan documents (§120 §1.2). Matched by filename/title substring;
# category drives urn:shepard:wiki:plan-category. The classifier tags these as
# `plan`; this table narrows to the canonical five + assigns categories.
PLAN_CATEGORIES = [
    ("project-plan", "project-plan"),
    ("plan-for-the-test-shell", "manufacture-plan"),
    ("painlist-prozessablauf", "painlist"),
    ("painlist-tps", "painlist"),
    ("tlz-roadmap", "roadmap"),
]


def plan_category(page: WikiPage) -> str | None:
    fn = page.path.name.lower()
    title = page.title.lower()
    for needle, cat in PLAN_CATEGORIES:
        if needle in fn or needle.replace("-", " ") in title or needle in title:
            return cat
    return None


def build_narrative(plans: list[tuple[WikiPage, str]]) -> str:
    """Fold each plan's readable text into one Markdown-ish narrative block."""
    parts = [
        "# MFFD Programme Overview",
        "",
        "Folded narrative of the MFFD project planning documents "
        "(imported from the Confluence MFFD space). Each plan's original HTML "
        "is attached as a FileReference on this DataObject; the readable summary "
        "below gives the discoverability.",
        "",
    ]
    for page, cat in plans:
        text = html_to_text(str(_main_content(page.html)))
        # cap each plan's folded text so the description stays manageable
        snippet = text.strip()
        if len(snippet) > 4000:
            snippet = snippet[:4000].rstrip() + "\n…(truncated; see attached FileReference for the full plan)"
        parts.append(f"## {page.title}  ({cat})")
        parts.append(f"_Source: {page.source_url}_")
        parts.append("")
        parts.append(snippet)
        parts.append("")
    return "\n".join(parts).strip()


def get_programme_overview_do(client: Client, project_v1id: int) -> dict | None:
    desc = "MFFD programme overview — narrative + folded wiki plans + shift/test journal entries."
    return client.ensure_data_object_v1(project_v1id, PROGRAMME_OVERVIEW_DO, description=desc)


def patch_do_description_v1(client: Client, coll_v1id: int, do: dict, new_desc: str) -> None:
    """Update the programme-overview DO description via v1 PUT (full object)."""
    if client.dry_run:
        log(f"    [dry-run] would PATCH description ({len(new_desc)} chars) on DO {do.get('name')}")
        return
    if (do.get("description") or "").strip() == new_desc.strip():
        log("    description already current — SKIP")
        return
    body = {
        "name": do["name"],
        "description": new_desc,
        "attributes": do.get("attributes") or {},
    }
    if do.get("parentId") is not None:
        body["parentId"] = do["parentId"]
    r = client._request(
        "PUT", f"/shepard/api/collections/{coll_v1id}/dataObjects/{do['id']}",
        json_body=body, mutating=True,
    )
    if r is None or r.status_code not in (200, 201):
        warn(f"description PUT returned {getattr(r, 'status_code', '?')} — narrative not folded")
    else:
        log("    description updated (narrative folded)")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap)
    args = ap.parse_args()

    source = Path(args.source)
    if not source.is_dir():
        die(f"--source dir not found: {source}")

    client = make_client(args)
    project_v1id = client.collection_v1_id(args.project)
    if project_v1id is None:
        die(f"could not resolve v1 id for Project {args.project}")

    log(f"source : {source}")
    log(f"mode   : {'COMMIT' if args.commit else 'DRY-RUN'}")
    log(f"project: {args.project} (v1Id={project_v1id})")
    log("")

    # Pick the canonical five plan documents.
    all_pages = classify_pages(source)
    plans: list[tuple[WikiPage, str]] = []
    for p in all_pages:
        cat = plan_category(p)
        if cat:
            plans.append((p, cat))
    # de-dup by page_id, stable order
    seen = set()
    plans = [(p, c) for (p, c) in plans if not (p.page_id in seen or seen.add(p.page_id))]

    log(f"plan documents selected : {len(plans)}")
    for p, c in plans:
        log(f"  {p.path.name:48s} → {c}")
    log("")

    if not plans:
        die("no plan documents matched — check the dump path / PLAN_CATEGORIES table.")

    do = get_programme_overview_do(client, project_v1id)
    if do is None:
        die("could not create/find programme-overview DO")
    do_appid = do.get("appId")
    do_v1id = do.get("id")
    log(f"programme-overview DO : appId={do_appid} v1Id={do_v1id}")
    log("")

    # ── attach each plan as a singleton FileReference (idempotent by name).
    # Use the v2 by-data-object listing — the v1 /fileReferences listing does
    # NOT surface v2 singleton FileReferences (project_v5_list_visibility_bug).
    existing_names: set[str] = set()
    if not client.dry_run and do_appid:
        for fr in client.list_file_refs_v2(do_appid):
            n = fr.get("name") or ""
            existing_names.add(n)

    attached = 0
    skipped = 0
    unparsed: list[str] = []
    for page, cat in plans:
        fname = f"{page.title.replace('/', '-')}.html"
        if fname in existing_names:
            log(f"  SKIP   {fname} — already attached")
            skipped += 1
            continue
        if client.dry_run:
            log(f"  [dry-run] would attach singleton FileReference {fname!r} ({len(page.html)} bytes)")
            attached += 1
            continue
        res = client.upload_singleton_file_v2(do_appid, fname, page.html.encode("utf-8"))
        if res is None:
            unparsed.append(f"{page.path.name}: file upload failed")
            continue
        attached += 1
        log(f"  ATTACH {fname} → fileRef appId={res.get('appId')}")
        # plan annotations on the programme-overview DO
        client.ensure_annotation(do_appid, "DataObject", wc.PRED_WIKI_PLAN_DOCUMENT, "true")
        client.ensure_annotation(do_appid, "DataObject", wc.PRED_WIKI_PLAN_CATEGORY, cat)
        client.ensure_annotation(do_appid, "DataObject", wc.PRED_WIKI_SOURCE_PAGE_ID, page.page_id)

    log("")

    # ── fold the narrative into the description
    log("folding narrative into programme-overview description...")
    narrative = build_narrative(plans)
    if not client.dry_run and do_v1id and do_v1id > 0:
        # re-fetch to get current description
        fresh = client.find_data_object_by_name_v1(project_v1id, PROGRAMME_OVERVIEW_DO) or do
        patch_do_description_v1(client, project_v1id, fresh, narrative)
    else:
        log(f"    [dry-run] narrative is {len(narrative)} chars; first 200:")
        log("    " + narrative[:200].replace("\n", " "))

    log("")
    log(f"plans attached : {attached}{'  (dry-run)' if client.dry_run else ''}")
    log(f"plans skipped  : {skipped} (idempotent — already attached)")
    if unparsed:
        log("UNPARSED:")
        for u in unparsed:
            log(f"  - {u}")
    if client.dry_run:
        log("")
        log("Dry-run complete. Re-run with --commit to write.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
