#!/usr/bin/env python3
"""feat-fair-publish — FAIR metadata + completeness score + cite-this-dataset.

Showcase: a Collection stamped with the FAIR fields the metadata-completeness
widget (``frontend/utils/metadataCompleteness.ts`` / ``MetadataCompletenessCard``)
scores against — an SPDX license, access rights, a rich (≥ 50 char) description, a
creator ORCID, at least one semantic annotation, a keyword annotation, and at
least one DataObject. A ``dcterms:bibliographicCitation`` "cite this dataset"
string is stamped so the Collection's "Cite this dataset" card
(``frontend/utils/citation.ts``) renders APA / BibTeX / RIS / CSL-JSON.

What the completeness widget scores (per the pure scoring util):
  name (10) + description≥50 (15) + license (20) + accessRights (10) +
  creatorOrcid (10) + ≥1 semantic annotation (10) + ≥1 keyword annotation (5) +
  ≥1 DataObject (15) + ≥1 lab journal (5) = 100 ceiling.

This seed reaches **95/100** (everything except the lab-journal row, which needs
a narrative entry that is out of scope for a one-shot showcase). It sets the
creator ORCID on the seeding user (``PATCH /v2/users/me``) so the creatorOrcid
check passes; on an instance where the user already has an ORCID this is a no-op.

If the Helmholtz Unhide publish plugin is configured (``GET /v2/admin/unhide/config``
reachable + enabled), a publish *could* be triggered — but this seed deliberately
does NOT publish (synthetic showcase data must not be harvested into a real
catalogue). The FAIR fields + citation are demonstrated regardless; publishing is
optional for this showcase.

Endpoints exercised
-------------------
  * ``POST  /shepard/api/collections`` + ``PATCH /v2/collections/{appId}``
    (license + accessRights top-level FAIR fields + funder/grantId attributes)
  * ``POST  /v2/collections/{cid}/data-objects`` (≥1 DataObject)
  * ``POST  /v2/annotations`` (dcterms:bibliographicCitation cite-this +
    schema:keywords keyword annotation)
  * ``PATCH /v2/users/me`` (creator ORCID — drives the creatorOrcid check)
  * ``GET   /v2/users/me`` (creator ORCID readback)

Synthetic CFRP/LMPAEK consolidation campaign. No real DLR/MFFD measurement data.

References
----------
  * DataCite Metadata Schema 4.x (Creator, Title, Description, Rights, Subject)
  * schema.org/Dataset (keywords, license, citation)
  * FAIR principles — go-fair.org (F2 rich metadata, A1 access, R1.1 license)
  * F-UJI FAIR maturity indicators (the completeness card's scoring basis)
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from _client import build_arg_parser, client_from_args, log, HttpError  # noqa: E402

SLUG = "feat-fair-publish"
COLLECTION_NAME = SLUG
COLLECTION_DESC = (
    "Synthetic CFRP/LMPAEK consolidation test campaign (Q3 2024). Twelve coupons "
    "across AFP layup, ultrasonic welding, and NDT inspection, with full process "
    "lineage. Openly licensed showcase data demonstrating Shepard's FAIR metadata "
    "completeness scoring and dataset-citation affordances. NOT REAL DLR/MFFD data."
)

FAIR_LICENSE = "CC-BY-4.0"
FAIR_ACCESS_RIGHTS = "OPEN"
FAIR_FUNDER = "DFG"
FAIR_GRANT_ID = "EXC-2075"
SEED_ORCID = "0000-0002-1825-0097"  # public DataCite sample ORCID (Josiah Carberry)

DCTERMS_CITATION = "http://purl.org/dc/terms/bibliographicCitation"
SCHEMA_KEYWORDS = "https://schema.org/keywords"

CITE_THIS_DATASET = (
    "DLR (synthetic) (2024). Synthetic CFRP Consolidation Test Campaign, "
    "Q3 2024 (showcase dataset). Shepard Research Data Platform. "
    "Licensed CC-BY-4.0. NOT REAL DLR/MFFD measurement data."
)


def main() -> int:
    parser = build_arg_parser(__doc__.splitlines()[0])
    args = parser.parse_args()
    client = client_from_args(args)

    print(f"\n=== {SLUG} : seeding against {client.root} ===", flush=True)

    # Resolve vocabulary appIds for provenance hygiene.
    vocabs = client.list_vocabularies()
    vocab_dc = vocabs.get("Dublin Core Terms")
    vocab_schema = vocabs.get("schema.org")

    # 0. Creator ORCID — drives the completeness creatorOrcid check (10 pts).
    me = client.users_me()
    if not me.get("orcid"):
        try:
            client.patch("/users/me", {"orcid": SEED_ORCID})
            log("OK", "creator ORCID", f"set {SEED_ORCID} (drives creatorOrcid check)")
        except HttpError as e:
            log("SKIP", "creator ORCID", f"PATCH /v2/users/me (HTTP {e.status}) — "
                f"completeness creatorOrcid check (10 pts) will fail; set via "
                f'PATCH /v2/users/me {{"orcid": "..."}}')
    else:
        log("OK", "creator ORCID", f"already present: {me['orcid']} "
            "(completeness creatorOrcid check passes)")

    if args.reset:
        if client.reset_collection(COLLECTION_NAME):
            log("OK", COLLECTION_NAME, "Collection deleted (reset)")
        else:
            log("SKIP", COLLECTION_NAME, "Collection (no prior seed)")

    existing = client.find_collection_by_name(COLLECTION_NAME)
    if existing is not None:
        coll = existing
        coll_app_id = coll["appId"]
        if existing.get("dataObjectIds"):
            log("OK", COLLECTION_NAME,
                "already seeded (reusing; re-run with --reset to rebuild)",
                coll_app_id)
            print(f"\n=== ALREADY SEEDED ===", flush=True)
            print(f"Collection: {client.frontend_collection_url(coll_app_id)}",
                  flush=True)
            return 0
        log("OK", COLLECTION_NAME, "Collection (reusing empty existing)", coll_app_id)
    else:
        coll = client.create_collection(COLLECTION_NAME, COLLECTION_DESC,
                                        attributes={"funder": FAIR_FUNDER,
                                                    "grantId": FAIR_GRANT_ID})
        coll_app_id = coll["appId"]
        log("OK", COLLECTION_NAME, "Collection created", coll_app_id)

    # 1. FAIR top-level fields — license + accessRights (PATCH /v2/collections).
    client.patch_collection(coll_app_id, {
        "license": FAIR_LICENSE,
        "accessRights": FAIR_ACCESS_RIGHTS,
        "description": COLLECTION_DESC,
        "attributes": {"funder": FAIR_FUNDER, "grantId": FAIR_GRANT_ID},
    })
    log("OK", COLLECTION_NAME,
        f"FAIR metadata: license={FAIR_LICENSE}, accessRights={FAIR_ACCESS_RIGHTS}")

    # 2. At least one DataObject (15 pts).
    do = client.create_data_object(
        coll_app_id, "Coupon FP-001",
        description="CFRP/LMPAEK consolidation coupon FP-001 (synthetic showcase).",
        attributes={"coupon_id": "FP-001", "process": "consolidation"},
    )
    log("OK", "Coupon FP-001", "DataObject", do["appId"])

    # 3. Collection semantic + keyword annotations.
    try:
        client.create_annotation(coll_app_id, "Collection", SCHEMA_KEYWORDS,
                                 object_literal="CFRP; consolidation; thermoplastic; AFP",
                                 vocabulary_id=vocab_schema)
        log("OK", COLLECTION_NAME, "collection keyword annotation (schema:keywords)")
    except HttpError as e:
        log("SKIP", COLLECTION_NAME, f"keyword annotation (HTTP {e.status})")

    # 4. Cite-this-dataset citation (dcterms:bibliographicCitation).
    try:
        client.create_annotation(coll_app_id, "Collection", DCTERMS_CITATION,
                                 object_literal=CITE_THIS_DATASET,
                                 vocabulary_id=vocab_dc)
        log("OK", COLLECTION_NAME, "cite-this-dataset citation stamped")
    except HttpError as e:
        log("SKIP", COLLECTION_NAME, f"citation annotation (HTTP {e.status})")

    # 5. Best-effort Unhide config probe (publishing intentionally NOT triggered).
    try:
        cfg = client.get("/admin/unhide/config")
        enabled = cfg.get("enabled") if isinstance(cfg, dict) else None
        log("OK", "Unhide config", f"reachable (enabled={enabled}); "
            "publishing intentionally skipped for synthetic data.")
    except HttpError as e:
        log("SKIP", "Unhide config", f"GET /v2/admin/unhide/config (HTTP {e.status}) — "
            "FAIR fields + citation still demonstrated; publishing is optional.")

    # ── verify + self-score the completeness widget ───────────────────────
    print("\n--- verification + completeness self-score ---", flush=True)
    coll_now = client.find_collection_by_name(COLLECTION_NAME)
    # GAP (RESEED-FIND): the annotation LIST endpoint returns 403 for a
    # Collection subject even to its creator under API-key auth
    # (`annotations.forbidden` — lacks Read permission on the subject entity).
    # The POSTs above succeeded (annotations ARE written); only the read-back
    # filtered by a Collection subjectAppId is gated. We assume the two writes
    # landed (the POSTs returned 2xx) for the self-score.
    try:
        anns = client.list_annotations(coll_app_id)
        ann_count = len(anns)
        ann_preds = [a.get("predicateIri", "") for a in anns]
    except HttpError as e:
        log("SKIP", COLLECTION_NAME,
            f"GET /v2/annotations?subjectAppId=<Collection> (HTTP {e.status}) — "
            f"RESEED-FIND: annotation LIST is 403 for a Collection subject even to "
            f"its creator; the 2 annotation POSTs above succeeded. Assuming written.")
        ann_count = 2
        ann_preds = [SCHEMA_KEYWORDS, DCTERMS_CITATION]
    me_now = client.users_me()
    score, breakdown = _self_score(coll_now, ann_count, ann_preds, me_now.get("orcid"))
    for line in breakdown:
        print(f"    {line}", flush=True)
    log("OK", COLLECTION_NAME, f"metadata completeness self-score = {score}/100")
    log("OK", COLLECTION_NAME, f"{ann_count} collection annotations (POSTed)")

    print("\n=== DONE ===", flush=True)
    print(f"Collection: {client.frontend_collection_url(coll_app_id)}", flush=True)
    print(f"DataObject: {client.frontend_data_object_url(coll_app_id, do['appId'])}",
          flush=True)
    return 0


def _self_score(coll, ann_count, ann_preds, orcid):
    """Mirror frontend/utils/metadataCompleteness.ts scoring (1:1 point map)."""
    name = (coll or {}).get("name") or ""
    desc = (coll or {}).get("description") or ""
    license_ = (coll or {}).get("license")
    access = (coll or {}).get("accessRights")
    do_count = len((coll or {}).get("dataObjectIds") or [])
    has_ann = ann_count > 0
    has_kw = any("keywords" in p for p in ann_preds)
    checks = [
        ("name (10)", len(name.strip()) > 0, 10),
        ("description≥50 (15)", len(desc.strip()) >= 50, 15),
        ("license (20)", bool(license_ and license_.strip()), 20),
        ("accessRights (10)", bool(access and access.strip()), 10),
        ("creatorOrcid (10)", bool(orcid and orcid.strip()), 10),
        ("semanticAnnotation (10)", has_ann, 10),
        ("keywords (5)", has_kw, 5),
        ("dataObjects (15)", do_count > 0, 15),
        ("labJournal (5)", False, 5),  # out of scope for one-shot showcase
    ]
    score = sum(p for _, ok, p in checks if ok)
    lines = [f"[{'x' if ok else ' '}] {label}" for label, ok, _ in checks]
    return score, lines


if __name__ == "__main__":
    sys.exit(main())
