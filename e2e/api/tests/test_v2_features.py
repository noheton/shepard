"""Integration tests: /v2/ fork-specific endpoints."""

import pytest

pytestmark = pytest.mark.seed_required

SENSORS_CONTAINER_NAME = "lumen-inspired-sensors"


def _find_ts_container(http, name: str):
    r = http.get("/shepard/api/timeseriescontainers", params={"page": 0, "size": 100})
    assert r.status_code == 200
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    return next((c for c in items if c.get("name") == name), None)


def test_admin_features_list(http):
    r = http.get("/v2/admin/features")
    assert r.status_code == 200, (
        f"GET /v2/admin/features returned {r.status_code}. "
        "This endpoint was introduced in this fork (A3b). "
        "If it returns 404, the v2 admin REST class is missing."
    )
    body = r.json()
    # Accept array or dict with a 'features' key
    assert isinstance(body, (list, dict)), f"Unexpected response shape: {body!r}"


def test_container_annotations_endpoint(http):
    container = _find_ts_container(http, SENSORS_CONTAINER_NAME)
    if container is None:
        pytest.skip(f"Container '{SENSORS_CONTAINER_NAME}' not found — run seed first")
    cid = container["id"]
    r = http.get(f"/v2/timeseries-containers/{cid}/annotations")
    assert r.status_code == 200, (
        f"GET /v2/timeseries-containers/{cid}/annotations returned {r.status_code}. "
        "This is a fork-specific endpoint; a 404 means it's missing from the image."
    )
    body = r.json()
    assert "annotations" in body or isinstance(body, list), (
        f"Expected 'annotations' key or list, got: {list(body.keys()) if isinstance(body, dict) else type(body)}"
    )


def test_chart_view_roundtrip(http):
    container = _find_ts_container(http, SENSORS_CONTAINER_NAME)
    if container is None:
        pytest.skip(f"Container '{SENSORS_CONTAINER_NAME}' not found — run seed first")
    cid = container["id"]

    # Write a chart view
    view_payload = {"selectedChannels": ["pc_chamber", "pc_ox_manifold"], "yAxisScale": "linear"}
    patch_r = http.patch(
        f"/v2/timeseries-containers/{cid}/chart-view",
        json=view_payload,
        headers={"Content-Type": "application/json"},
    )
    assert patch_r.status_code in (200, 204), (
        f"PATCH chart-view returned {patch_r.status_code}: {patch_r.text[:300]}"
    )

    # Read it back
    get_r = http.get(f"/v2/timeseries-containers/{cid}/chart-view")
    assert get_r.status_code == 200, (
        f"GET chart-view returned {get_r.status_code}: {get_r.text[:300]}"
    )
    stored = get_r.json()
    channels = stored.get("selectedChannels") or []
    assert "pc_chamber" in channels, (
        f"Expected 'pc_chamber' in stored selectedChannels, got: {channels}"
    )
