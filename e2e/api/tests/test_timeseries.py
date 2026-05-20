"""Integration tests: timeseries container and channel data."""

import pytest

pytestmark = pytest.mark.seed_required

SENSORS_CONTAINER_NAME = "lumen-inspired-sensors"
# Matches CHANNELS constant in examples/lumen-showcase/seed.py
MIN_CHANNEL_COUNT = 25
PROBE_CHANNEL = "pc_chamber"


def _find_ts_container(http, name: str):
    r = http.get("/shepard/api/timeseriescontainers", params={"page": 0, "size": 100})
    assert r.status_code == 200, f"List TS containers failed: {r.status_code}"
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    return next((c for c in items if c.get("name") == name), None)


def test_lumen_sensors_container_exists(http):
    container = _find_ts_container(http, SENSORS_CONTAINER_NAME)
    assert container is not None, (
        f"Timeseries container '{SENSORS_CONTAINER_NAME}' not found. "
        "Run examples/lumen-showcase/seed.py first."
    )


def test_timeseries_channel_count(http):
    container = _find_ts_container(http, SENSORS_CONTAINER_NAME)
    assert container is not None, f"Container '{SENSORS_CONTAINER_NAME}' not found"
    cid = container["id"]
    r = http.get(f"/shepard/api/timeseriescontainers/{cid}/timeseries",
                 params={"page": 0, "size": 200})
    assert r.status_code == 200
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    assert len(items) >= MIN_CHANNEL_COUNT, (
        f"Expected >= {MIN_CHANNEL_COUNT} timeseries channels, found {len(items)}"
    )


def test_timeseries_data_points_present(http):
    container = _find_ts_container(http, SENSORS_CONTAINER_NAME)
    assert container is not None
    cid = container["id"]

    # Find the probe channel
    r = http.get(f"/shepard/api/timeseriescontainers/{cid}/timeseries",
                 params={"page": 0, "size": 200})
    assert r.status_code == 200
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    channel = next((ts for ts in items if PROBE_CHANNEL in ts.get("name", "")), None)
    assert channel is not None, (
        f"Could not find timeseries channel containing '{PROBE_CHANNEL}'. "
        f"Available: {[ts.get('name') for ts in items[:10]]}"
    )

    ts_id = channel["id"]
    r2 = http.get(
        f"/shepard/api/timeseriescontainers/{cid}/timeseries/{ts_id}/data",
        params={"page": 0, "size": 10},
    )
    assert r2.status_code == 200, f"TS data query failed: {r2.status_code} {r2.text}"
    body2 = r2.json()
    data = body2.get("results", body2) if isinstance(body2, dict) else body2
    assert len(data) > 0, (
        f"No data points returned for channel '{PROBE_CHANNEL}' (ts_id={ts_id})"
    )
