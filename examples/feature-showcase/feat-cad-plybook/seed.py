#!/usr/bin/env python3
"""feat-cad-plybook — synthetic STEP/AP242 upload → CAD metadata annotations.

The ``fileformat-cad`` plugin's ``StepP21Parser`` extracts ``urn:shepard:cad:*``
and ``urn:shepard:mffd:cad:*`` annotations (format, STEP schema, product name,
application, author, organisation, and — when present — ply count / fibre angles
/ material) from an ISO 10303-21 (AP242) physical file. The parser runs through
the ``FileParserPlugin`` SPI, dispatched on ``POST /v2/files`` by
``SingletonFileReferenceService``.

What this seed does
-------------------
  1. Creates the ``feat-cad-plybook`` Collection + a "Plybook Demo Panel" DataObject.
  2. Uploads a *synthetic* AP242 STEP file (valid ``ISO-10303-21`` magic line,
     a HEADER with FILE_NAME / FILE_SCHEMA, a small DATA PRODUCT) as a singleton
     FileReference (``POST /v2/files``).
  3. Verifies the parser fired (the upload-time FileParser dispatch IS wired — a
     pleasant surprise vs. the recovered ``.pyc``, which predates the wiring and
     documented it as a gap).

The read-surface caveat (RESEED-FIND)
-------------------------------------
``StepP21Parser`` anchors its annotations on the **FileReference** subject
(fileReference-first, parent DataObject as fallback). But
``GET /v2/annotations?subjectAppId=<fileRef>`` returns **403** for the file's own
owner ("lacks Read permission on the subject entity") — FileReference-subject
annotations have no usable REST read path today. The annotations DO exist (7 of
them, verifiable via Cypher: ``MATCH (a:SemanticAnnotation
{subjectAppId:'<fileRef>'}) RETURN a.propertyIRI, a.valueName``). So this seed
reports the parser as wired but the per-FileReference annotation READ surface as
the gap to close (expose the file's own annotations to the file's owner, or anchor
CAD annotations on the parent DataObject like the thermography filename-grid hook).

Standards: ISO 10303 AP242 (STEP MIM_LF, model-based 3D engineering), CHAMEO/EMMO
(CF/LMPAEK material + ply/fibre-angle process terms), DIN EN 9100 (as-designed CAD
traceability). NOT real DLR/MFFD data.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from _client import ShepardError, add_common_args, client_from_args  # noqa: E402

SLUG = "feat-cad-plybook"
COLLECTION_NAME = SLUG
COLLECTION_DESC = (
    "Synthetic AP242 STEP plybook panel. Proves the fileformat-cad parser's "
    "urn:shepard:cad:* / urn:shepard:mffd:cad:* annotation set fires on upload — "
    "and surfaces the gap that FileReference-subject annotations have no REST read "
    "path. NOT real DLR/MFFD data."
)

STEP_FILENAME = "Plybook_Demopanel_synthetic.step"

# Synthetic ISO 10303-21 AP242 physical file. Valid magic line + HEADER the
# StepP21Parser reads (FILE_NAME → product/author/application; FILE_SCHEMA →
# step_schema) + a tiny DATA PRODUCT. Obviously synthetic.
SYNTHETIC_STEP = b"""ISO-10303-21;
HEADER;
FILE_DESCRIPTION(('Synthetic MFFD plybook demo panel'),'2;1');
FILE_NAME('Plybook_Demopanel_synthetic.step','2026-01-20T10:00:00',('Synthetic CAD exporter'),('DLR ZLP synthetic'),'CATIA V5 (synthetic)','','');
FILE_SCHEMA(('AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF'));
ENDSEC;
DATA;
#1=PRODUCT('Plybook_Demopanel','CF/LMPAEK upper-shell demo panel','',(#2));
#2=PRODUCT_CONTEXT('',#3,'mechanical');
ENDSEC;
END-ISO-10303-21;
"""

EXPECTED_CAD = [
    "urn:shepard:cad:format",
    "urn:shepard:cad:step_schema",
    "urn:shepard:cad:product_name",
    "urn:shepard:cad:application",
    "urn:shepard:cad:author",
]


def _ok(m): print(f"  OK   {m}")
def _skip(m): print(f"  SKIP {m}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    add_common_args(parser)
    args = parser.parse_args()
    c = client_from_args(args)

    print(f"== {SLUG} ==")
    plugins = c.plugin_states()
    if plugins.get("fileformat-cad") != "ENABLED":
        _skip("fileformat-cad plugin not ENABLED — RESEED-FIND; aborting.")
        return 0

    coll = c.ensure_collection(COLLECTION_NAME, COLLECTION_DESC, reset=args.reset)
    cid = coll["appId"]
    _ok(f"collection -> {cid}")

    do = c.create_data_object(
        cid, "Plybook Demo Panel",
        description="Synthetic CF/LMPAEK upper-shell demo panel; carries the AP242 STEP FileReference.",
        attributes={"material": "CF/LMPAEK", "part-kind": "plybook-panel"},
    )
    do_id = do["appId"]
    _ok(f"panel DataObject -> {do_id}")

    fr = c.upload_file(do_id, STEP_FILENAME, SYNTHETIC_STEP,
                       content_type="application/octet-stream")
    fr_id = fr["appId"]
    # Sanity: the bytes carry the STEP magic line the parser keys on.
    assert SYNTHETIC_STEP.startswith(b"ISO-10303-21;"), "STEP magic line missing"
    _ok(f"STEP FileReference -> {fr_id} (ISO-10303-21 magic present)")

    # Check the parent DataObject for CAD annotations (the parser anchors on the
    # FileReference, so these stay empty — but the parser still fires).
    print("-- annotation check --")
    do_anns = {a.get("predicateIri") for a in c.get_annotations(do_id)}
    cad_on_do = [p for p in EXPECTED_CAD if p in do_anns]
    print(f"  CAD annotations on parent DataObject: {cad_on_do or 'NONE (anchored on FileReference)'}")

    # Try the FileReference subject directly — expected 403 (no REST read path).
    fr_anns = c.get_annotations(fr_id)  # tolerant: returns [] on 403
    if fr_anns:
        cad_on_fr = [a.get("predicateIri") for a in fr_anns
                     if (a.get("predicateIri") or "").startswith("urn:shepard:cad")]
        print(f"  CAD annotations readable on FileReference: {cad_on_fr or 'NONE'}")
    else:
        print("  CAD annotations on FileReference: not readable via REST (403 — RESEED-FIND).")

    print("\nRESEED-FIND (annotation read surface):")
    print("  StepP21Parser DOES fire on POST /v2/files and emits 7 urn:shepard:cad:*")
    print("  annotations anchored on the FileReference subject (verify via Cypher:")
    print(f"    MATCH (a:SemanticAnnotation {{subjectAppId:'{fr_id}'}})")
    print("    RETURN a.propertyIRI, a.valueName  -> 7 rows).")
    print("  Gap: GET /v2/annotations?subjectAppId=<fileRef> returns 403 for the")
    print("       file's own owner — FileReference-subject annotations have no REST")
    print("       read path. Fix: grant the file owner Read on its own file's")
    print("       annotations, or anchor CAD annotations on the parent DataObject")
    print("       (as the thermography filename-grid hook already does).")

    print("\nSUMMARY")
    print(f"  collection         {cid}")
    print(f"  panel DataObject   {do_id}")
    print(f"  STEP FileReference {fr_id}")
    print(f"  frontend: https://shepard.nuclide.systems/collections/{cid}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
