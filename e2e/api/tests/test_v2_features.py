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
    # APISIMP-SA-CONT: the per-kind /v2/{kind}-containers/{id}/annotations
    # resources were dissolved into the unified polymorphic /v2/annotations
    # surface (subjectAppId + subjectKind), which is appId-keyed.
    container = _find_ts_container(http, SENSORS_CONTAINER_NAME)
    if container is None:
        pytest.skip(f"Container '{SENSORS_CONTAINER_NAME}' not found — run seed first")
    app_id = container.get("appId")
    if not app_id:
        pytest.skip("container has no appId on the v1 list payload")
    r = http.get("/v2/annotations", params={"subjectAppId": app_id})
    assert r.status_code == 200, (
        f"GET /v2/annotations?subjectAppId={app_id} returned {r.status_code}. "
        "Container annotations flow through the unified /v2/annotations surface."
    )
    body = r.json()
    assert isinstance(body, list) or "annotations" in body, (
        f"Expected list or 'annotations' key, got: {list(body.keys()) if isinstance(body, dict) else type(body)}"
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
