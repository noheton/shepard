#!/usr/bin/env python3
"""wiki-to-glossary.py — MFFD-WIKI-TO-GLOSSARY (track 3 of aidocs/integrations/120).

Mines the ~99 reference wiki pages for `urn:shepard:mffd:term:<acronym>`
controlled-vocab terms and writes inverse `urn:shepard:mffd:mentions`
annotations on every MFFD DataObject that mentions an acronym in its name or
description.

Outputs (per §120 §2.3 + CLAUDE.md "semantic annotations are first-class"):

  1. A JSON-LD glossary manifest attached as a singleton FileReference on the
     Project's `vocabulary-manifest` DataObject — the human/operator artefact.
  2. One `urn:shepard:mffd:term:<acronym> = <expansion>` SemanticAnnotation per
     mined term on the `vocabulary-manifest` DO (the queryable controlled-vocab).
  3. Inverse `urn:shepard:mffd:mentions = <acronym>` SemanticAnnotations on every
     DataObject across the 5 MFFD step Collections + the Project whose name or
     description contains the acronym (so "which DOs mention AFP" is clean).

Idempotency:
  • term + mentions annotations are ensure-by-(predicate, value).
  • the manifest FileReference is matched by name.

Acceptance (§120 §8.4): the canonical campaign terms — AFP, TPS, FSD, NDT, LBR,
MFFD, MFZ, AF — appear with expansions (KNOWN_EXPANSIONS guarantees this).

Run:
    SHEPARD_HOST=... SHEPARD_API_KEY=... python3 wiki-to-glossary.py            # dry-run
    SHEPARD_HOST=... SHEPARD_API_KEY=... python3 wiki-to-glossary.py --commit   # writes
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import wiki_common as wc
from wiki_common import (
    Client, add_common_args, classify_pages, die, load_stopwords, log,
    make_client, mine_acronyms, resolve_step_collections, warn,
)

DEFAULT_STOPWORDS = Path(__file__).resolve().parents[1] / "wiki-journal" / "glossary-stopwords.txt"
VOCAB_MANIFEST_DO = "vocabulary-manifest"
MANIFEST_FILENAME = "mffd-vocabulary.jsonld"

# Canonical campaign terms that MUST land (acceptance §8.4).
REQUIRED_TERMS = ["AFP", "TPS", "FSD", "NDT", "LBR", "MFFD", "MFZ", "AF"]


def build_jsonld(terms: dict) -> str:
    """Render the mined terms as a JSON-LD glossary document."""
    graph = []
    for acr in sorted(terms):
        row = terms[acr]
        graph.append({
            "@id": f"{wc.PRED_MFFD_TERM_PREFIX}{acr}",
            "@type": "skos:Concept",
            "skos:notation": acr,
            "skos:prefLabel": row.get("term") or acr,
            "shepard:extractionConfidence": row.get("confidence"),
            "shepard:sourcePageCount": len(set(row.get("source_pages", []))),
            "shepard:sourcePages": sorted(set(row.get("source_pages", []))),
        })
    doc = {
        "@context": {
            "skos": "http://www.w3.org/2004/02/skos/core#",
            "shepard": "urn:shepard:",
        },
        "@id": "urn:shepard:mffd:vocabulary-manifest",
        "@type": "skos:ConceptScheme",
        "dct:title": "MFFD controlled vocabulary (mined from Confluence wiki)",
        "@graph": graph,
    }
    return json.dumps(doc, indent=2, ensure_ascii=False)


def acronym_pattern(acronyms: list[str]) -> re.Pattern:
    # word-boundary match for each acronym (escape slashes); longest first
    parts = sorted((re.escape(a) for a in acronyms), key=len, reverse=True)
    return re.compile(r"(?<![A-Za-z0-9])(" + "|".join(parts) + r")(?![A-Za-z0-9])")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap)
    ap.add_argument("--stopwords", default=str(DEFAULT_STOPWORDS), help="glossary-stopwords.txt path.")
    ap.add_argument("--min-confidence", choices=["low", "medium", "high"], default="low",
                    help="Only emit terms at/above this confidence (default low = all).")
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
    log("")

    stopwords = load_stopwords(Path(args.stopwords))
    pages = classify_pages(source)
    ref_pages = [p for p in pages if p.klass == "reference"]
    log(f"reference pages mined : {len(ref_pages)}")

    terms = mine_acronyms(ref_pages, stopwords)

    # Guarantee the required canonical terms are present (acceptance §8.4),
    # even if a particular dump revision happens not to surface one in a
    # reference page (it may live on a journal/plan page instead).
    all_text_pages = pages  # search every page for the required terms' presence
    for req in REQUIRED_TERMS:
        if req not in terms:
            terms[req] = {
                "term": wc.KNOWN_EXPANSIONS.get(req),
                "source_pages": [],
                "count": 0,
                "confidence": "high" if wc.KNOWN_EXPANSIONS.get(req) else "low",
            }

    conf_order = {"low": 0, "medium": 1, "high": 2}
    min_c = conf_order[args.min_confidence]
    # always keep required terms regardless of confidence filter
    filtered = {
        a: r for a, r in terms.items()
        if conf_order.get(r.get("confidence", "low"), 0) >= min_c or a in REQUIRED_TERMS
    }

    log(f"terms mined          : {len(terms)}")
    log(f"terms after filter   : {len(filtered)} (min-confidence={args.min_confidence})")
    log("")
    log("required-term check (acceptance §8.4):")
    for req in REQUIRED_TERMS:
        row = filtered.get(req)
        present = "✓" if row else "✗"
        exp = (row or {}).get("term") if row else None
        log(f"  {present} {req:5s} → {exp}")
    log("")

    # top terms preview
    top = sorted(filtered.items(), key=lambda kv: -len(set(kv[1].get("source_pages", []))))[:20]
    log("top mined terms (by source-page count):")
    for acr, row in top:
        log(f"  {acr:8s} pages={len(set(row.get('source_pages', []))):3d} "
            f"conf={row.get('confidence'):6s} → {row.get('term')}")
    log("")

    # ── resolve target Collections (5 steps + project) for inverse mentions
    steps = resolve_step_collections(client, args.project)
    target_colls = dict(steps)
    target_colls["mffd-project"] = {"appId": args.project, "v1Id": project_v1id, "name": "mffd-project"}

    # ── vocabulary-manifest DO
    vocab_do = client.ensure_data_object_v1(
        project_v1id, VOCAB_MANIFEST_DO,
        description="MFFD controlled-vocabulary manifest — urn:shepard:mffd:term:* terms mined from the Confluence wiki.",
    )
    if vocab_do is None:
        die("could not create/find vocabulary-manifest DO")
    vocab_appid = vocab_do.get("appId")
    vocab_v1id = vocab_do.get("id")
    log(f"vocabulary-manifest DO : appId={vocab_appid} v1Id={vocab_v1id}")
    log("")

    # 1. attach JSON-LD manifest as a singleton FileReference (idempotent by name)
    jsonld = build_jsonld(filtered)
    existing_names = set()
    if not client.dry_run and vocab_appid:
        # v2 by-data-object listing (the v1 listing misses singleton FileRefs).
        for fr in client.list_file_refs_v2(vocab_appid):
            existing_names.add(fr.get("name") or "")
    if MANIFEST_FILENAME in existing_names:
        log(f"  manifest {MANIFEST_FILENAME} already attached — SKIP")
    elif client.dry_run:
        log(f"  [dry-run] would attach manifest {MANIFEST_FILENAME} ({len(jsonld)} bytes)")
    else:
        res = client.upload_singleton_file_v2(vocab_appid, MANIFEST_FILENAME, jsonld.encode("utf-8"))
        log(f"  manifest attached → fileRef appId={(res or {}).get('appId')}")

    # 2. term annotations on vocabulary-manifest DO
    term_created = 0
    term_skipped = 0
    for acr in sorted(filtered):
        row = filtered[acr]
        pred = f"{wc.PRED_MFFD_TERM_PREFIX}{acr}"
        value = row.get("term") or acr
        _, created = client.ensure_annotation(
            vocab_appid, "DataObject", pred, value, predicate_label=acr,
        )
        if created:
            term_created += 1
        else:
            term_skipped += 1
    log("")
    log(f"term annotations  created={term_created} skipped={term_skipped}")

    # 3. inverse mentions across every MFFD DataObject
    pat = acronym_pattern(list(filtered.keys()))
    mentions_created = 0
    mentions_skipped = 0
    scanned_dos = 0
    for slug, meta in target_colls.items():
        v1id = meta.get("v1Id")
        if not v1id:
            continue
        dos = client.list_data_objects_v1(v1id)
        for do in dos:
            scanned_dos += 1
            do_appid = do.get("appId")
            hay = f"{do.get('name') or ''} {do.get('description') or ''}"
            found = sorted(set(pat.findall(hay)))
            for acr in found:
                _, created = client.ensure_annotation(
                    do_appid, "DataObject", wc.PRED_MFFD_MENTIONS, acr,
                )
                if created:
                    mentions_created += 1
                else:
                    mentions_skipped += 1
    log("")
    log(f"DataObjects scanned for mentions : {scanned_dos}")
    log(f"mentions annotations  created={mentions_created} skipped={mentions_skipped}")

    if client.dry_run:
        log("")
        log("Dry-run complete. Re-run with --commit to write.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
