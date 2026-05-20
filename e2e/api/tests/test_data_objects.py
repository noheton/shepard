"""Integration tests: DataObject seed data integrity and predecessor chains."""

import pytest

pytestmark = pytest.mark.seed_required

HOLD_DAYS = {5, 12}
ANOMALY_NAME = "Anomaly Investigation — TR-004 Fuel Turbopump"


def test_fifteen_runs_exist(lumen_data_objects):
    tr_names = [n for n in lumen_data_objects if n.startswith("TR-")]
    assert len(tr_names) == 15, (
        f"Expected 15 TR-0xx DataObjects, found {len(tr_names)}: {sorted(tr_names)}"
    )


def test_hold_days_not_fired(lumen_data_objects):
    for n in HOLD_DAYS:
        name = f"TR-{n:03d}"
        do = lumen_data_objects.get(name)
        assert do is not None, f"DataObject {name} not found in LUMEN collection"
        attrs = do.get("attributes") or {}
        is_fired = attrs.get("is_fired", "")
        assert is_fired == "false", (
            f"{name} should not be fired but is_fired={is_fired!r}"
        )


def test_anomaly_investigation_under_tr004(http, lumen_collection, lumen_data_objects):
    tr004 = lumen_data_objects.get("TR-004")
    assert tr004 is not None, "TR-004 not found in LUMEN collection"

    coll_id = lumen_collection["id"]
    r = http.get(f"/shepard/api/collections/{coll_id}/dataobjects")
    assert r.status_code == 200
    body = r.json()
    all_dos = body.get("results", body) if isinstance(body, dict) else body

    anomaly = next((o for o in all_dos if o.get("name") == ANOMALY_NAME), None)
    assert anomaly is not None, (
        f"'{ANOMALY_NAME}' not found. "
        f"Names present: {[o.get('name') for o in all_dos[:10]]}"
    )
    assert anomaly.get("parentId") == tr004["id"], (
        f"Anomaly investigation parentId={anomaly.get('parentId')!r} "
        f"but TR-004 id={tr004['id']!r}"
    )


def test_predecessor_chain(lumen_data_objects):
    tr001 = lumen_data_objects.get("TR-001")
    tr002 = lumen_data_objects.get("TR-002")
    assert tr001 is not None, "TR-001 not found"
    assert tr002 is not None, "TR-002 not found"
    pred_ids = tr002.get("predecessorIds") or []
    assert tr001["id"] in pred_ids, (
        f"TR-001 id {tr001['id']!r} not in TR-002 predecessorIds: {pred_ids}"
    )


def test_tr006_has_two_predecessors(lumen_data_objects):
    tr006 = lumen_data_objects.get("TR-006")
    assert tr006 is not None, "TR-006 not found"
    pred_ids = tr006.get("predecessorIds") or []
    assert len(pred_ids) == 2, (
        f"TR-006 should have 2 predecessors (TR-005 + anomaly investigation), "
        f"found {len(pred_ids)}: {pred_ids}"
    )
