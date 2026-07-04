#!/usr/bin/env python3
"""feat-templates-as-shapes — B6/B7: templates-as-shapes (SHACL-validated instantiation).

Proves V2CONV-B6/B7 + B2 end-to-end:

1. Create a DATAOBJECT_RECIPE :ShepardTemplate whose body carries a SHACL
   ``shapeGraph`` (Turtle) constraining the DataObject's attributes, plus the
   default ``dataobjects[0].attributes`` the instance is born with.
2. Instantiate a VALID DataObject from it via
   ``POST /v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId}``
   → expect **201**.
3. Instantiate from a second template that carries the SAME shapeGraph but
   shape-VIOLATING default attributes → expect **422** with the SHACL
   violation in the body.

The backend (``TemplateInstantiationRest``) validates the attributes baked into
the *template body* (``dataobjects[0].attributes``) — the instantiation request
body only carries an optional ``name`` override, NOT attributes. So the
conform/violate distinction lives in the template's own default attributes,
which is why we seed two templates: one valid, one violating.

The backend builds a tiny data graph ``<urn:shepard:instance:candidate>`` with
one ``<urn:shepard:attribute:KEY>`` predicate per attribute, then runs Apache
Jena SHACL against the template's ``shapeGraph``. So the shape targets
``urn:shepard:instance:candidate`` and constrains ``urn:shepard:attribute:*``
predicates.

References:
  * W3C SHACL — Shapes Constraint Language (sh:NodeShape, sh:property,
    sh:minCount, sh:datatype, sh:pattern, sh:in): https://www.w3.org/TR/shacl/
  * Apache Jena SHACL: https://jena.apache.org/documentation/shacl/

Run:
  /tmp/reseed-venv/bin/python examples/feature-showcase/feat-templates-as-shapes/seed.py \
      --host http://localhost:8080/shepard/api --apikey "$(cat /tmp/reseed_apikey.txt)"
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from _shared import (  # noqa: E402
    build_parser,
    collection_app_id,
    data_object_app_id,
    ensure_collection,
    log,
    make_ctx,
    print_outcome,
    reset_collection,
    section,
    v2_delete,
    v2_get,
    v2_post,
)

SLUG = "templates-as-shapes"
COLLECTION_NAME = f"feat-{SLUG}"
COLLECTION_DESCRIPTION = (
    "V2CONV-B6/B7 showcase (synthetic). A DATAOBJECT_RECIPE template carrying a "
    "SHACL shapeGraph; valid instances → 201, shape-violating instances → 422. "
    "No real DLR/MFFD data."
)
TEMPLATE_NAME = f"feat-{SLUG}-coupon"
TEMPLATE_NAME_BAD = f"feat-{SLUG}-coupon-violating"
TEMPLATE_KIND = "DATAOBJECT_RECIPE"

# A SHACL NodeShape targeting the candidate instance node the backend builds.
# Constraints (per W3C SHACL §4):
#   * urn:shepard:attribute:material — required (sh:minCount 1), one of an enum
#     (sh:in) — proves sh:in.
#   * urn:shepard:attribute:lot       — required, must match a LOT-#### pattern
#     (sh:pattern) — proves sh:pattern.
SHAPE_GRAPH = """\
@prefix sh:   <http://www.w3.org/ns/shacl#> .
@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
@prefix attr: <urn:shepard:attribute:> .

<urn:shepard:shape:coupon> a sh:NodeShape ;
  sh:targetNode <urn:shepard:instance:candidate> ;
  sh:property [
    sh:path attr:material ;
    sh:minCount 1 ;
    sh:datatype xsd:string ;
    sh:in ( "CF/LMPAEK" "CF/PEEK" "CF/PPS" ) ;
    sh:message "material must be present and one of CF/LMPAEK, CF/PEEK, CF/PPS" ;
  ] ;
  sh:property [
    sh:path attr:lot ;
    sh:minCount 1 ;
    sh:pattern "^LOT-[0-9]{4}$" ;
    sh:message "lot must match LOT-#### (four digits)" ;
  ] .
"""

# The template body is a JSON *string* (the :ShepardTemplate.body column). It
# carries the shapeGraph plus the default attributes a born-valid instance gets.
TEMPLATE_BODY = json.dumps(
    {
        "dataobjects": [
            {
                "name": "coupon-instance",
                "attributes": {"material": "CF/LMPAEK", "lot": "LOT-0042"},
            }
        ],
        "shapeGraph": SHAPE_GRAPH,
    }
)

# Same shape, but default attributes that VIOLATE it: material 'CF/EPOXY' is not
# in the sh:in enum and lot 'BAD' fails the LOT-#### pattern. Instantiating this
# template must yield 422.
TEMPLATE_BODY_BAD = json.dumps(
    {
        "dataobjects": [
            {
                "name": "coupon-bad",
                "attributes": {"material": "CF/EPOXY", "lot": "BAD"},
            }
        ],
        "shapeGraph": SHAPE_GRAPH,
    }
)


def find_template(ctx, name: str) -> dict | None:
    status, rows, _ = v2_get(ctx, f"/templates?kind={TEMPLATE_KIND}")
    if status != 200 or not isinstance(rows, list):
        return None
    return next((r for r in rows if r.get("name") == name and not r.get("retired")), None)


def ensure_template(ctx, name: str, body_json: str, description: str) -> str:
    existing = find_template(ctx, name)
    if existing is not None:
        log("SKIP", f"template '{name}'", existing["appId"])
        return existing["appId"]
    status, body, _ = v2_post(
        ctx,
        "/templates",
        {
            "name": name,
            "templateKind": TEMPLATE_KIND,
            "description": description,
            "body": body_json,
        },
    )
    if status != 201:
        log("FAIL", f"template create returned {status}: {str(body)[:160]}")
        sys.exit(1)
    app_id = body["appId"]
    log("OK", f"template '{name}'", app_id)
    return app_id


def instantiate(ctx, coll_app_id: str, template_app_id: str, name: str):
    """POST .../from-template/{templateAppId}. The request body carries only an
    optional ``name`` override; the attributes (and thus shape conformance) come
    from the template body itself."""
    return v2_post(
        ctx,
        f"/collections/{coll_app_id}/data-objects/from-template/{template_app_id}",
        {"name": name},
    )


def main() -> None:
    args = build_parser(__doc__.splitlines()[0]).parse_args()
    ctx = make_ctx(args)

    if args.reset:
        section("RESET")
        reset_collection(ctx, COLLECTION_NAME)
        for nm in (TEMPLATE_NAME, TEMPLATE_NAME_BAD):
            existing = find_template(ctx, nm)
            if existing:
                v2_delete(ctx, f"/templates/{existing['appId']}")
                log("OK", "retired prior template", existing["appId"])

    section("COLLECTION")
    coll = ensure_collection(ctx, COLLECTION_NAME, COLLECTION_DESCRIPTION)

    section("TEMPLATES (DATAOBJECT_RECIPE + shapeGraph)")
    template_app_id = ensure_template(
        ctx, TEMPLATE_NAME, TEMPLATE_BODY, "AFP coupon blueprint with a SHACL shape on material + lot."
    )
    template_bad_app_id = ensure_template(
        ctx, TEMPLATE_NAME_BAD, TEMPLATE_BODY_BAD, "Same shape, deliberately shape-violating defaults (expect 422)."
    )

    coll_app_id = collection_app_id(ctx, coll.id)

    section("VALID INSTANCE → expect 201")
    # Idempotent: reuse an existing 'coupon-valid' DataObject so re-runs don't
    # accrue duplicates (the from-template endpoint always creates a new node).
    existing_valid = next(
        (o for o in (ctx.data_object.get_all_data_objects(coll.id) or []) if o.name == "coupon-valid"),
        None,
    )
    valid_do_app_id = ""
    if existing_valid is not None:
        valid_do_app_id = data_object_app_id(ctx, coll.id, existing_valid.id)
        log("SKIP", "valid instance already present", valid_do_app_id)
    else:
        status, body, _ = instantiate(ctx, coll_app_id, template_app_id, "coupon-valid")
        if status == 201:
            valid_do_app_id = body.get("appId", "")
            log("OK", "valid instance created (201)", valid_do_app_id)
        else:
            log("FAIL", f"valid instance expected 201, got {status}: {str(body)[:160]}")

    section("SHAPE VIOLATION → expect 422")
    status, body, _ = instantiate(ctx, coll_app_id, template_bad_app_id, "coupon-invalid")
    if status == 422:
        log("OK", f"violation correctly rejected (422): {str(body)[:140]}")
    else:
        log("WARN", f"expected 422 on shape violation, got {status}: {str(body)[:160]}")

    section("VERIFY (read back the valid instance)")
    if valid_do_app_id:
        vs, _, _ = v2_get(ctx, f"/references?kind=uri&dataObjectAppId={valid_do_app_id}")
        # Listing its (empty) uri references with a 200/403 proves the appId
        # resolves and is permission-gated, i.e. the DataObject genuinely exists.
        log("OK" if vs in (200, 403) else "WARN", f"valid instance reachable by appId (refs list HTTP {vs})")

    print_outcome(
        SLUG,
        coll_app_id,
        {
            "template_valid": template_app_id,
            "template_violating": template_bad_app_id,
            "valid_instance": valid_do_app_id or "(none)",
        },
    )


if __name__ == "__main__":
    main()
