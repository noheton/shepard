"""Integration tests: file container and file reference seed data."""

import pytest

pytestmark = pytest.mark.seed_required

ARTIFACTS_CONTAINER_NAME = "lumen-inspired-artifacts"


def _find_file_container(http, name: str):
    r = http.get("/shepard/api/filebundlecontainers", params={"page": 0, "size": 100})
    if r.status_code == 404:
        # Some builds expose this under a different path; skip gracefully.
        pytest.skip(f"File bundle container list endpoint not available: {r.status_code}")
    assert r.status_code == 200, f"List file containers failed: {r.status_code}"
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    return next((c for c in items if c.get("name") == name), None)


def test_lumen_artifacts_container_exists(http):
    container = _find_file_container(http, ARTIFACTS_CONTAINER_NAME)
    assert container is not None, (
        f"File container '{ARTIFACTS_CONTAINER_NAME}' not found. "
        "Run examples/lumen-showcase/seed.py first."
    )


def test_file_references_present(http):
    container = _find_file_container(http, ARTIFACTS_CONTAINER_NAME)
    assert container is not None
    cid = container["id"]
    r = http.get(
        f"/shepard/api/filebundlecontainers/{cid}/filebundles",
        params={"page": 0, "size": 50},
    )
    if r.status_code == 404:
        pytest.skip("File bundles endpoint not available under this path")
    assert r.status_code == 200, f"List file bundles failed: {r.status_code}"
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    assert len(items) >= 1, (
        f"Expected >= 1 file reference in '{ARTIFACTS_CONTAINER_NAME}', found {len(items)}"
    )
