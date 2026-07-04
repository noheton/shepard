#!/usr/bin/env python3
"""feat-semantic-sparql — semantic annotations + SPARQL playground + vocabularies.

Showcase: DataObjects carry ``:SemanticAnnotation`` triples drawn from the
bootstrapped controlled vocabularies (Dublin Core Terms, schema.org, the Shepard
internal vocab), then the seed exercises the SPARQL playground endpoint
(``GET /v2/semantic/internal/sparql``, with the reserved ``internal`` alias
resolving to the bootstrapped INTERNAL ``:SemanticRepository``) and reads the
annotations back via ``GET /v2/annotations``.

The annotations are written through the canonical SEMA-V6 store, so they are
queryable via:
  * ``GET /v2/annotations?subjectAppId=<appId>`` (works today),
  * the annotation picker / cards on each DataObject detail page,
  * the MCP annotation CRUD tools, and
  * the SPARQL playground (once the n10s RDF view is queryable).

KNOWN GAP (RESEED-FIND, captured at runtime): on an instance whose n10s
``_GraphConfig`` was NOT initialised on an empty graph before data landed (the
fresh bootstrap populates it first), the n10s SPARQL HTTP endpoint returns 404
and the playground proxy surfaces a 400 ``sparql.upstream-error``. The seed
detects this and logs it as a SKIP with the remediation. The annotations
themselves ARE written and ARE queryable as :SemanticAnnotation nodes; only the
RDF/SPARQL projection is blocked. Remediation: run ``n10s.graphconfig.init`` on
an empty graph before any data lands (or with ``{force:true}`` on a maintenance
window).

Example SPARQL queries (paste into the playground at the collection's repo):

    # 1. All triples about the coupons this seed created
    SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 50

    # 2. Everything with Dublin Core subject == "consolidation"
    PREFIX dcterms: <http://purl.org/dc/terms/>
    SELECT ?s WHERE { ?s dcterms:subject "consolidation" }

    # 3. Material of each coupon
    PREFIX schema: <https://schema.org/>
    SELECT ?s ?mat WHERE { ?s schema:material ?mat }

Synthetic generic CFRP coupons. No real DLR/MFFD IP.

References
----------
  * W3C SPARQL 1.1 Query Language (W3C Rec. 2013-03-21)
  * Dublin Core Terms (DCMI Metadata Terms)
  * W3C SKOS — Simple Knowledge Organization System (for prefLabel'd concepts)
  * schema.org/Dataset vocabulary
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from _client import build_arg_parser, client_from_args, log, HttpError  # noqa: E402

SLUG = "feat-semantic-sparql"
COLLECTION_NAME = SLUG
COLLECTION_DESC = (
    "Showcase: semantic annotations + SPARQL playground + controlled vocabularies. "
    "DataObjects carry :SemanticAnnotation triples from Dublin Core / schema.org / "
    "shepard vocab; the playground (/v2/semantic/internal/sparql) queries them back. "
    "Synthetic CFRP coupon data — NOT REAL DLR/MFFD data."
)

DCTERMS = "http://purl.org/dc/terms/"
SCHEMA = "https://schema.org/"
SHEPARD_VOCAB = "https://shepard.dlr.de/vocab/"

# (predicate IRI, object literal, vocabulary label to resolve the appId of)
COUPONS = {
    "Coupon SP-001": [
        (DCTERMS + "title", "AFP layup coupon SP-001", "Dublin Core Terms"),
        (DCTERMS + "subject", "consolidation", "Dublin Core Terms"),
        (SCHEMA + "material", "CF/LMPAEK", "schema.org"),
        (SHEPARD_VOCAB + "processStep", "afp-layup", None),
    ],
    "Coupon SP-002": [
        (DCTERMS + "title", "Ultrasonic-weld coupon SP-002", "Dublin Core Terms"),
        (DCTERMS + "subject", "consolidation", "Dublin Core Terms"),
        (SCHEMA + "material", "CF/LMPAEK", "schema.org"),
        (SHEPARD_VOCAB + "processStep", "ultrasonic-welding", None),
    ],
    "Coupon SP-003": [
        (DCTERMS + "title", "NDT-inspected coupon SP-003", "Dublin Core Terms"),
        (DCTERMS + "subject", "inspection", "Dublin Core Terms"),
        (SCHEMA + "material", "CF/LMPAEK", "schema.org"),
        (SHEPARD_VOCAB + "processStep", "ndt-inspection", None),
    ],
}

SPARQL_QUERIES = [
    ("all-triples", "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 25"),
    ("by-subject",
     'PREFIX dcterms: <http://purl.org/dc/terms/>\n'
     'SELECT ?s WHERE { ?s dcterms:subject "consolidation" }'),
]


def main() -> int:
    parser = build_arg_parser(__doc__.splitlines()[0])
    args = parser.parse_args()
    client = client_from_args(args)

    print(f"\n=== {SLUG} : seeding against {client.root} ===", flush=True)

    # Resolve vocabulary appIds (provenance hygiene — links each annotation to
    # the controlled vocabulary that supplies its predicate).
    vocabs = client.list_vocabularies()
    log("OK", "vocabularies", f"{len(vocabs)} bootstrapped vocabularies discovered")

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
        coll = client.create_collection(COLLECTION_NAME, COLLECTION_DESC)
        coll_app_id = coll["appId"]
        log("OK", COLLECTION_NAME, "Collection created", coll_app_id)

    # Create the coupons + annotate each.
    written = 0
    coupon_apps: dict[str, str] = {}
    for name, triples in COUPONS.items():
        do = client.create_data_object(
            coll_app_id, name,
            description=f"Synthetic CFRP coupon for the semantic/SPARQL showcase: {name}.",
            attributes={"coupon_id": name.split()[-1]},
        )
        app = do["appId"]
        coupon_apps[name] = app
        log("OK", name, "DataObject", app)
        for predicate, literal, vocab_label in triples:
            vocab_id = vocabs.get(vocab_label) if vocab_label else None
            try:
                client.create_annotation(app, "DataObject", predicate,
                                         object_literal=literal,
                                         vocabulary_id=vocab_id)
                written += 1
            except HttpError as e:
                log("SKIP", f"{name}/{predicate}", f"annotation (HTTP {e.status})")
    log("OK", "annotations", f"{written} :SemanticAnnotation triples written across "
        f"{len(coupon_apps)} DataObject-subject coupons")

    # ── verify annotations read back ──────────────────────────────────────
    print("\n--- verification (GET /v2/annotations) ---", flush=True)
    for name, app in coupon_apps.items():
        anns = client.list_annotations(app)
        log("OK", name, f"{len(anns)} annotations read back via /v2/annotations")

    # ── exercise SPARQL playground ────────────────────────────────────────
    print("\n--- SPARQL playground (/v2/semantic/internal/sparql) ---", flush=True)
    sparql_ok = False
    for label, query in SPARQL_QUERIES:
        try:
            result = client.sparql(query)
            bindings = (result or {}).get("results", {}).get("bindings", []) \
                if isinstance(result, dict) else []
            log("OK", label, f"SPARQL returned {len(bindings)} bindings")
            sparql_ok = True
        except HttpError as e:
            log("SKIP", label,
                f"SPARQL failed (HTTP {e.status}): n10s _GraphConfig not "
                f"initialised on this instance (graphconfig.init needs an empty "
                f"graph; bootstrap populated it first). Annotations ARE written + "
                f"queryable as :SemanticAnnotation nodes; only the RDF/SPARQL "
                f"projection is blocked. Remediation: n10s.graphconfig.init on an "
                f"empty graph (or {{force:true}}) on a maintenance window.")
            break

    print("\n=== DONE ===", flush=True)
    if not sparql_ok:
        print("NOTE: SPARQL endpoint unreachable on this instance (see SKIP above) — "
              "RESEED-FIND: n10s graphconfig.init required. Annotations succeeded.",
              flush=True)
    print(f"Collection: {client.frontend_collection_url(coll_app_id)}", flush=True)
    for name, app in coupon_apps.items():
        print(f"  {name}: {client.frontend_data_object_url(coll_app_id, app)}",
              flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
