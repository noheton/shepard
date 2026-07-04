#!/usr/bin/env python3
"""feat-ncr-disposition — Non-Conformance Report (NCR) + disposition + rework chain.

Showcase: AAA2 — a quality non-conformance flow. A CFRP/LMPAEK test coupon FAILs
ultrasonic inspection, an NCR is raised with a REWORK disposition (EN 9100 §8.7 /
EASA Part 21 G), a rework DataObject is linked by a typed ``fair2r:repairs`` edge,
and a re-inspection PASSes and closes the NCR. The anomaly → hold → rework →
re-test-PASS sequence mirrors how the LUMEN showcase models TR-004.

How NCR / disposition are recorded
----------------------------------
The EN 9100 quality statuses (``NCR_OPEN``, ``ON_HOLD``, ``CONCESSION_PENDING``,
``REJECTED``, ``CERTIFIED``) are **role-gated** on the DataObject ``status`` field
(``StatusTransitionGuard`` requires the ``quality-engineer`` role). API-key auth
carries NO such role, so the seed records the NCR posture in the canonical
``:SemanticAnnotation`` store instead — queryable via the annotation picker, the
SPARQL playground, and the MCP tools — exactly as the LUMEN showcase does for
TR-004. The seed ALSO attempts to set the real ``status`` field best-effort; the
403 is logged as a SKIP (a real finding, not a failure) and the annotation store
remains the source of truth.

Endpoints exercised
-------------------
  * ``POST  /shepard/api/collections`` (Collection, mints appId)
  * ``POST  /v2/collections/{cid}/data-objects`` (with ``typedPredecessors``)
  * ``POST  /v2/annotations`` (SEMA-V6 NCR status + disposition + inspection result)
  * ``PATCH /v2/collections/{cid}/data-objects/{did}`` (best-effort quality status)

Synthetic generic CFRP "test coupon" data. No real DLR/MFFD IP.

References
----------
  * DIN EN 9100:2018 §8.7 "Control of nonconforming outputs" (accept-as-is /
    rework / repair / scrap disposition vocabulary)
  * EASA Part 21 Subpart G — documented non-conformance resolution
  * W3C PROV-O + the fork's fair2r:repairs typed predecessor relationship
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from _client import build_arg_parser, client_from_args, log, HttpError  # noqa: E402

SLUG = "feat-ncr-disposition"
COLLECTION_NAME = SLUG
COLLECTION_DESC = (
    "Showcase: NCR status + disposition + rework Predecessor chain (AAA2). A test "
    "coupon FAILs inspection, an NCR is raised with a REWORK disposition, a rework "
    "DataObject is linked by a typed fair2r:repairs edge, and a re-inspection "
    "PASSes. NCR posture recorded in the canonical :SemanticAnnotation store. "
    "Synthetic CFRP coupon data — NOT REAL DLR/MFFD data."
)

# ── controlled-vocabulary IRIs (organising experiment ontology) ──────────────
EXP_NS = "https://shepard.dlr.de/ontologies/experiment#"
PROP_NCR_STATUS = EXP_NS + "NcrStatus"
PROP_DISPOSITION = EXP_NS + "Disposition"
PROP_INSPECTION = EXP_NS + "InspectionResult"

VAL_NCR_OPEN = EXP_NS + "NcrOpen"
VAL_NCR_CLOSED = EXP_NS + "NcrClosed"
VAL_DISP_REWORK = EXP_NS + "Rework"
VAL_INSP_FAIL = EXP_NS + "Fail"
VAL_INSP_PASS = EXP_NS + "Pass"

DCTERMS_DESC = "http://purl.org/dc/terms/description"


def _annotate(client, app_id, predicate, *, iri=None, literal=None, label=None):
    try:
        client.create_annotation(app_id, "DataObject", predicate,
                                  object_iri=iri, object_literal=literal,
                                  predicate_label=label)
        return True
    except HttpError as e:
        log("SKIP", f"{predicate} on {app_id}", f"annotation (HTTP {e.status})")
        return False


def _try_set_status(client, coll_app_id, do_app_id, status):
    """Best-effort: the quality statuses are role-gated, so a 403 is expected."""
    try:
        client.patch_data_object(coll_app_id, do_app_id, {"status": status})
        log("OK", do_app_id, f"status set = {status}")
    except HttpError as e:
        if e.status == 403:
            log("SKIP", do_app_id,
                f"status '{status}' role-gated to quality-engineer "
                f"(HTTP 403) — NCR posture recorded via annotations instead")
        else:
            log("SKIP", do_app_id, f"status PATCH (HTTP {e.status})")


def main() -> int:
    parser = build_arg_parser(__doc__.splitlines()[0])
    args = parser.parse_args()
    client = client_from_args(args)

    print(f"\n=== {SLUG} : seeding against {client.root} ===", flush=True)

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

    # 1. Coupon CFRP-AFP-017 — FAILs ultrasonic inspection → NCR raised.
    coupon = client.create_data_object(
        coll_app_id, "Coupon CFRP-AFP-017",
        description="CFRP/LMPAEK consolidation coupon, ply-drop region. Inspected: FAIL.",
        attributes={"coupon_id": "CFRP-AFP-017", "process": "AFP-layup"},
    )
    coupon_app = coupon["appId"]
    log("OK", "Coupon CFRP-AFP-017", "DataObject (FAIL → NCR)", coupon_app)

    _annotate(client, coupon_app, PROP_INSPECTION, iri=VAL_INSP_FAIL,
              label="Inspection result")
    _annotate(client, coupon_app, PROP_NCR_STATUS, iri=VAL_NCR_OPEN,
              label="NCR status")
    _annotate(client, coupon_app, PROP_DISPOSITION, iri=VAL_DISP_REWORK,
              label="Disposition")
    _annotate(client, coupon_app, DCTERMS_DESC,
              literal="NCR-2024-017: porosity exceeds 2% acceptance limit in "
                      "ply-drop region. Disposition: REWORK.")
    _try_set_status(client, coll_app_id, coupon_app, "NCR_OPEN")

    # 2. Rework DataObject — typed fair2r:repairs edge back to the failed coupon.
    rework = client.create_data_object(
        coll_app_id, "Coupon CFRP-AFP-017-RW1",
        description="Rework of CFRP-AFP-017: re-consolidated at +10 C per NCR-2024-017.",
        attributes={"coupon_id": "CFRP-AFP-017-RW1", "ncr": "NCR-2024-017"},
        typed_predecessors=[{"predecessorAppId": coupon_app,
                             "relationshipType": "fair2r:repairs"}],
    )
    rework_app = rework["appId"]
    log("OK", "Coupon CFRP-AFP-017-RW1", "DataObject (rework, fair2r:repairs)", rework_app)

    # 3. Re-inspection PASS → NCR closed.
    _annotate(client, rework_app, PROP_INSPECTION, iri=VAL_INSP_PASS,
              label="Inspection result")
    _annotate(client, rework_app, PROP_NCR_STATUS, iri=VAL_NCR_CLOSED,
              label="NCR status")
    _annotate(client, rework_app, DCTERMS_DESC,
              literal="Re-inspection PASS — porosity within limits. NCR-2024-017 closed.")
    _try_set_status(client, coll_app_id, rework_app, "CERTIFIED")

    # ── verify against live ───────────────────────────────────────────────
    print("\n--- verification (GET back) ---", flush=True)
    for label, app in (("Coupon CFRP-AFP-017", coupon_app),
                       ("Coupon CFRP-AFP-017-RW1", rework_app)):
        anns = client.list_annotations(app)
        preds = [a.get("predicateIri") for a in anns]
        log("OK", label, f"{len(anns)} annotations read back", ", ".join(
            p.rsplit("#", 1)[-1].rsplit("/", 1)[-1] for p in preds))

    print("\n=== DONE ===", flush=True)
    print(f"Collection:  {client.frontend_collection_url(coll_app_id)}", flush=True)
    print(f"Coupon (FAIL → NCR_OPEN → REWORK): "
          f"{client.frontend_data_object_url(coll_app_id, coupon_app)}", flush=True)
    print(f"Rework (fair2r:repairs → PASS → NCR closed): "
          f"{client.frontend_data_object_url(coll_app_id, rework_app)}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
