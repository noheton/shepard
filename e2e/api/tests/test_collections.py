"""Integration tests: collection CRUD and seed data integrity."""

import pytest
from conftest import LUMEN_NAME

pytestmark = pytest.mark.seed_required


def test_lumen_collection_exists(lumen_collection):
    assert lumen_collection["name"] == LUMEN_NAME
    desc = (lumen_collection.get("description") or "").lower()
    assert "synthetic" in desc, f"Expected 'synthetic' in description, got: {desc!r}"


def test_lumen_collection_is_public(http, lumen_collection):
    coll_id = lumen_collection["id"]
    r = http.get(f"/shepard/api/collections/{coll_id}/permissions")
    assert r.status_code == 200, f"GET permissions failed: {r.status_code} {r.text}"
    perm_type = r.json().get("permissionType", "")
    assert perm_type == "PUBLIC", f"Expected PUBLIC, got: {perm_type!r}"


def test_crud_round_trip(temp_collection, http):
    coll_id = temp_collection["id"]
    # Read back
    r = http.get(f"/shepard/api/collections/{coll_id}")
    assert r.status_code == 200
    assert r.json()["name"] == temp_collection["name"]

    # Delete (fixture teardown deletes too, but we test explicit delete here)
    del_r = http.delete(f"/shepard/api/collections/{coll_id}")
    assert del_r.status_code in (200, 204), f"Delete failed: {del_r.status_code}"

    # Verify 404
    get_r = http.get(f"/shepard/api/collections/{coll_id}")
    assert get_r.status_code == 404, (
        f"Expected 404 after delete, got {get_r.status_code}"
    )


def test_list_includes_lumen(http):
    r = http.get("/shepard/api/collections", params={"page": 0, "size": 200})
    assert r.status_code == 200
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    names = [c.get("name", "") for c in items]
    assert LUMEN_NAME in names, (
        f"LUMEN collection not found in list. First 5 names: {names[:5]}"
    )
