#!/usr/bin/env python3
"""feat-mffd-templates — MFFD demonstrator templates (synthetic mini-MFFD).

The keystone MFFD demonstrator. Instantiates the built-in MFFD process-step
``DATAOBJECT_RECIPE`` ``ShepardTemplate``s (seeded by the ``V100`` Neo4j
migration — ``MFFD AFP Layup``, ``MFFD Ultrasonic Welding``, ``MFFD NDT
Inspection``) into a small ``material-batch → AFP → weld → NDT`` lineage with
typed Predecessor edges and ``urn:shepard:mffd:material-batch`` IRI
back-references so every consuming step back-references the batch's appId.

The chain it builds (Predecessor edges left→right)::

    material-batch (B-2026-001, CF/LMPAEK)
        └─▶ AFP Course #1 ─▶ AFP Course #2 ─▶ Ultrasonic Weld ─▶ NDT (PASS)

Every consuming step carries the material-batch appId under
``urn:shepard:mffd:material-batch``, so a SPARQL/Cypher join reconstructs
"all process steps that consumed batch B-2026-001" — the DIN EN 9100
traceability promise.

Endpoints exercised
-------------------
  * ``GET  /v2/templates?kind=DATAOBJECT_RECIPE``                 (find V100 templates)
  * ``POST /v2/collections``                                     (the feat collection)
  * ``PUT  /v2/collections/{appId}/templates/allowed``           (template picker)
  * ``POST /v2/collections/{cid}/data-objects/from-template/...`` (5 instances)
  * ``POST /v2/collections/{cid}/data-objects`` w/ typedPredecessors
  * ``POST /v2/annotations``                                     (material-batch IRI back-refs)

Standards: ISO 10303 AP242 (process metadata), CHAMEO / EMMO (CF/LMPAEK
material + AFP/weld process terms), DIN EN 9100 (traceable rework-visible
process chain). NOT real DLR/MFFD data.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from _client import ShepardError, add_common_args, client_from_args  # noqa: E402

SLUG = "feat-mffd-templates"
COLLECTION_NAME = SLUG
COLLECTION_DESC = (
    "Synthetic mini-MFFD: material-batch (keystone) → 2 AFP courses → "
    "ultrasonic weld step → OTvis NDT measurement, instantiated from the V100 "
    "MFFD process-step DATAOBJECT_RECIPE templates with typed Predecessor edges "
    "and material-batch IRI back-references. NOT real DLR/MFFD data."
)

MATERIAL_BATCH = "B-2026-001"
MATERIAL = "CF/LMPAEK"
BATCH_PREDICATE = "urn:shepard:mffd:material-batch"

# V100 built-in template names we instantiate.
TPL_AFP = "MFFD AFP Layup"
TPL_WELD = "MFFD Ultrasonic Welding"
TPL_NDT = "MFFD NDT Inspection"


def _ok(msg: str) -> None:
    print(f"  OK   {msg}")


def _skip(msg: str) -> None:
    print(f"  SKIP {msg}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    add_common_args(parser)
    args = parser.parse_args()
    c = client_from_args(args)

    print(f"== {SLUG} ==")

    # Resolve the V100 MFFD templates (created by the Neo4j migration on bootstrap).
    templates: dict[str, dict] = {}
    for name in (TPL_AFP, TPL_WELD, TPL_NDT):
        t = c.find_template_by_name(name)
        if not t:
            print(f"FATAL: built-in template '{name}' not found. Is the V100 "
                  f"migration applied? (RESEED-FIND if missing on a fresh bootstrap.)",
                  file=sys.stderr)
            return 1
        templates[name] = t
        _ok(f"template '{name}' -> {t['appId']}")

    coll = c.ensure_collection(COLLECTION_NAME, COLLECTION_DESC, reset=args.reset)
    cid = coll["appId"]
    _ok(f"collection {COLLECTION_NAME} -> {cid}")

    # Link the three templates as allowed templates so the create-dialog picker shows them.
    try:
        c.put(
            f"/v2/collections/{cid}/templates/allowed",
            {"templateAppIds": [templates[n]["appId"] for n in (TPL_AFP, TPL_WELD, TPL_NDT)]},
        )
        _ok("linked MFFD templates as allowed-templates on collection")
    except ShepardError as e:
        _skip(f"allowed-template link HTTP {e.status}")

    # 1. Material batch keystone — a plain DataObject carrying the batch identity.
    batch = c.create_data_object(
        cid, f"Material Batch {MATERIAL_BATCH}",
        description=f"CF/LMPAEK prepreg batch {MATERIAL_BATCH} (synthetic). The keystone "
                    f"that every downstream process step back-references.",
        attributes={"material": MATERIAL, "batch-id": MATERIAL_BATCH, "form": "unidirectional-tape"},
    )
    _ok(f"material-batch DataObject -> {batch['appId']}")
    c.annotate(batch["appId"], "DataObject", "urn:shepard:material", literal=MATERIAL)
    c.annotate(batch["appId"], "DataObject", BATCH_PREDICATE, literal=MATERIAL_BATCH)

    # Prove template instantiation itself works (T1e picker path): instantiate one
    # genuine from-template DataObject. NOTE: the from-template body
    # (TemplateInstantiateRequest) takes only `name` — it cannot also carry
    # typedPredecessors, and a post-hoc PATCH of typedPredecessors is silently
    # ignored (only honoured at create time). So the *chain* below is built from
    # plain DataObjects with create-time typedPredecessors, seeding the template's
    # attribute schema manually. See RESEED-FIND in this seed's README.
    demo_instance = c.instantiate_template(
        cid, templates[TPL_AFP]["appId"], "AFP Layup (template-instantiated demo)",
    )
    _ok(f"from-template instance -> {demo_instance['appId']} "
        f"(attrs={demo_instance.get('attributes')})")

    chain_tail = batch["appId"]
    consumers: list[dict] = []

    # 2. Two AFP courses, each Predecessor-linked to the previous step (create-time).
    for course_no in (1, 2):
        do = c.create_data_object(
            cid, f"AFP Course #{course_no}",
            description="Synthetic AFP layup course (CF/LMPAEK).",
            attributes={
                "process-type": "AFP Layup", "step-number": "1",
                "operator-id": "OP-AFP-07",
                "started-at": f"2026-01-20T0{6 + course_no}:00:00Z",
                "tcp-temperature-c": "410", "consolidation-force-n": "1200",
                "ply-count": str(course_no), "roller-pressure-bar": "6",
                "equipment-id": "KR210-R2700-synthetic",
            },
            typed_predecessors=[{"predecessorAppId": chain_tail}],
        )
        consumers.append(do)
        chain_tail = do["appId"]
        _ok(f"AFP Course #{course_no} -> {do['appId']} (pred -> prior step)")

    # 3. Ultrasonic weld step.
    weld = c.create_data_object(
        cid, "Stringer Ultrasonic Weld",
        description="Synthetic ultrasonic stringer↔skin weld (CF/LMPAEK).",
        attributes={
            "process-type": "Ultrasonic Welding", "step-number": "2",
            "operator-id": "OP-WELD-03", "started-at": "2026-01-20T09:30:00Z",
            "frequency-khz": "20", "amplitude-um": "30", "hold-time-ms": "800",
            "weld-energy-j": "450", "equipment-id": "us-welder-synthetic",
        },
        typed_predecessors=[{"predecessorAppId": chain_tail}],
    )
    consumers.append(weld)
    chain_tail = weld["appId"]
    _ok(f"Ultrasonic Weld -> {weld['appId']} (pred -> AFP Course #2)")

    # 4. NDT inspection (PASS) — the EN 9100 quality gate.
    ndt = c.create_data_object(
        cid, "OTvis NDT Inspection (PASS)",
        description="Synthetic thermography NDT quality gate — result PASS.",
        attributes={
            "process-type": "NDT Inspection", "step-number": "5",
            "operator-id": "OP-NDT-02", "inspector-id": "INSP-11",
            "started-at": "2026-01-20T10:15:00Z",
            "inspection-method": "THERMOGRAPHY", "result": "PASS",
            "equipment-id": "edevis-otvis-synthetic",
        },
        typed_predecessors=[{"predecessorAppId": chain_tail}],
    )
    consumers.append(ndt)
    _ok(f"NDT Inspection -> {ndt['appId']} (pred -> weld)")

    # Material-batch IRI back-references on every consuming step.
    for do in consumers:
        c.annotate(do["appId"], "DataObject", BATCH_PREDICATE, literal=MATERIAL_BATCH)
    _ok(f"material-batch IRI back-references written on {len(consumers)} steps")

    # ---- verify against live ------------------------------------------------
    print("-- verify --")
    ndt_back = c.get(f"/v2/collections/{cid}/data-objects/{ndt['appId']}")
    preds = ndt_back.get("predecessorSummaries", []) or ndt_back.get("typedPredecessorSummaries", [])
    print(f"  NDT predecessors: {len(preds)} (expect >=1)")
    anns = c.get_annotations(weld["appId"])
    has_batch = any(a.get("predicateIri") == BATCH_PREDICATE for a in anns)
    print(f"  weld carries material-batch back-ref: {has_batch}")

    print("\nSUMMARY")
    print(f"  collection         {cid}")
    print(f"  material-batch     {batch['appId']}")
    for do in consumers:
        print(f"  step               {do['appId']}  {do['name']}")
    print(f"  frontend: https://shepard.nuclide.systems/collections/{cid}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
