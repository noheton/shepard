"""Integration tests: semantic annotations on seeded DataObjects."""

import pytest

pytestmark = pytest.mark.seed_required

EXPECTED_IRI_FRAGMENT = "shepard.dlr.de/ontologies/experiment"


def test_semantic_annotations_present(http, lumen_data_objects):
    found = False
    for name, do in list(lumen_data_objects.items())[:15]:
        do_id = do.get("id")
        r = http.get(f"/shepard/api/dataobjects/{do_id}/semanticAnnotations",
                     params={"page": 0, "size": 50})
        if r.status_code != 200:
            continue
        body = r.json()
        items = body.get("results", body) if isinstance(body, dict) else body
        for ann in items:
            predicate = ann.get("predicate", "") or ann.get("predicateIRI", "")
            if EXPECTED_IRI_FRAGMENT in predicate:
                found = True
                break
        if found:
            break

    assert found, (
        f"No semantic annotation with IRI fragment '{EXPECTED_IRI_FRAGMENT}' found "
        f"on any of the {len(lumen_data_objects)} seeded LUMEN DataObjects. "
        "Check that seed.py ran with the ontology preseed enabled."
    )
