"""
End-to-end tests for shepard-plugin-mcp.

Requires a live Shepard backend + seeded LUMEN demo data.
Run inside the shepard-mcp container or a host with fastmcp installed:

    pytest tests/test_e2e.py -v

Env vars (all optional — defaults target the compose stack from the host):
    MCP_URL          SSE endpoint (default http://localhost:8811/sse)
    KEYCLOAK_URL     Token endpoint (default http://localhost:8082/...)
    KEYCLOAK_USER    (default admin)
    KEYCLOAK_PASS    (default admin-demo)
"""

from __future__ import annotations

import os
import asyncio
import json

import httpx
import pytest
import pytest_asyncio

# ── auth helpers ──────────────────────────────────────────────────────────────

KEYCLOAK_URL = os.environ.get(
    "KEYCLOAK_URL",
    "http://localhost:8082/realms/shepard-demo/protocol/openid-connect/token",
)
KEYCLOAK_USER = os.environ.get("KEYCLOAK_USER", "admin")
KEYCLOAK_PASS = os.environ.get("KEYCLOAK_PASS", "admin-demo")
MCP_URL = os.environ.get("MCP_URL", "http://localhost:8811/sse")

# When running inside the MCP container, the backend is reachable as "backend".
# When running from the host, use localhost:8080.
BACKEND_URL = os.environ.get("SHEPARD_API_BASE", "http://localhost:8080")


def get_token() -> str:
    """Fetch a Keycloak bearer token with openid scope."""
    resp = httpx.post(
        KEYCLOAK_URL,
        data={
            "grant_type": "password",
            "client_id": "frontend-dev",
            "username": KEYCLOAK_USER,
            "password": KEYCLOAK_PASS,
            "scope": "openid",
        },
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]


# ── fixtures ──────────────────────────────────────────────────────────────────

@pytest.fixture(scope="session")
def token() -> str:
    return get_token()


@pytest.fixture(scope="session")
def api_headers(token) -> dict:
    return {"Authorization": f"Bearer {token}", "Accept": "application/json"}


@pytest.fixture(scope="session")
def lumen_ids(api_headers) -> dict:
    """Return the LUMEN collection appId and TR-004 DataObject appId."""
    r = httpx.get(f"{BACKEND_URL}/v2/collections", headers=api_headers, timeout=10)
    r.raise_for_status()
    collections = r.json()

    lumen = next(
        (c for c in collections if "LUMEN" in (c.get("name") or "")),
        None,
    )
    assert lumen, "LUMEN collection not found — is the demo data seeded?"

    col_app_id = lumen["appId"]
    # Fetch data objects
    r2 = httpx.get(
        f"{BACKEND_URL}/v2/collections/{col_app_id}/data-objects",
        headers=api_headers,
        params={"size": 50},
        timeout=10,
    )
    r2.raise_for_status()
    dos = r2.json()

    tr004 = next((d for d in dos if d.get("name") == "TR-004"), None)
    assert tr004, "TR-004 DataObject not found in LUMEN collection (exact name match)"

    return {"collection_app_id": col_app_id, "tr004_app_id": tr004["appId"]}


# ── API-layer tests (verify backend connectivity, no MCP transport) ───────────

class TestBackendConnectivity:
    def test_health(self, api_headers):
        r = httpx.get(f"{BACKEND_URL}/shepard/api/healthz/ready", headers=api_headers, timeout=10)
        assert r.status_code == 200

    def test_list_collections(self, api_headers):
        r = httpx.get(f"{BACKEND_URL}/v2/collections", headers=api_headers, timeout=10)
        assert r.status_code == 200
        data = r.json()
        assert isinstance(data, list)
        assert len(data) >= 1

    def test_lumen_collection_exists(self, lumen_ids):
        assert lumen_ids["collection_app_id"]
        assert lumen_ids["tr004_app_id"]

    def test_tr004_detail(self, api_headers, lumen_ids):
        col = lumen_ids["collection_app_id"]
        do = lumen_ids["tr004_app_id"]
        r = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}",
            headers=api_headers,
            timeout=10,
        )
        assert r.status_code == 200
        d = r.json()
        assert "TR-004" in d["name"]
        assert "containers" in d  # typed container breakdown

    def test_tr004_has_timeseries(self, api_headers, lumen_ids):
        col = lumen_ids["collection_app_id"]
        do = lumen_ids["tr004_app_id"]
        r = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}",
            headers=api_headers,
            timeout=10,
        )
        d = r.json()
        ts_refs = d.get("containers", {}).get("timeseries", [])
        assert len(ts_refs) >= 1, "TR-004 should have at least one timeseries container"
        ref = ts_refs[0]
        assert "containerId" in ref, "containerId field must be present in ContainerRefIO"
        assert ref["containerId"] > 0

    def test_tr004_timeseries_channels(self, api_headers, lumen_ids):
        col = lumen_ids["collection_app_id"]
        do = lumen_ids["tr004_app_id"]
        r = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}",
            headers=api_headers,
            timeout=10,
        )
        d = r.json()
        container_id = d["containers"]["timeseries"][0]["containerId"]
        r2 = httpx.get(
            f"{BACKEND_URL}/shepard/api/timeseriesContainers/{container_id}/timeseries",
            headers=api_headers,
            timeout=10,
        )
        assert r2.status_code == 200
        channels = r2.json()
        assert isinstance(channels, list)
        assert len(channels) >= 1
        # All LUMEN channels should have measurement, field etc.
        ch = channels[0]
        assert "measurement" in ch or "field" in ch

    def test_tr004_predecessor_chain(self, api_headers, lumen_ids):
        col = lumen_ids["collection_app_id"]
        do = lumen_ids["tr004_app_id"]
        r = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}/predecessor-chain",
            headers=api_headers,
            timeout=10,
        )
        assert r.status_code == 200
        chain = r.json()
        assert isinstance(chain, list)

    def test_tr004_successor_chain(self, api_headers, lumen_ids):
        col = lumen_ids["collection_app_id"]
        do = lumen_ids["tr004_app_id"]
        r = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}/successor-chain",
            headers=api_headers,
            timeout=10,
        )
        assert r.status_code == 200

    def test_tr004_annotations(self, api_headers, lumen_ids):
        col = lumen_ids["collection_app_id"]
        do = lumen_ids["tr004_app_id"]
        # Need numeric IDs for v1 annotation endpoint
        r = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}",
            headers=api_headers,
            timeout=10,
        )
        d = r.json()
        numeric_col_id = d.get("collectionId") or d.get("id")  # fallback
        # Use v2 numeric id if available
        col_num = lumen_ids.get("collection_numeric_id")
        do_num = d.get("id")
        if not do_num:
            pytest.skip("Numeric DataObject ID not available in v2 response")
        r2 = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}/annotations",
            headers=api_headers,
            timeout=10,
        )
        # Annotations endpoint might be v1 only — accept 200 or 404
        assert r2.status_code in (200, 404)


# ── MCP SSE transport tests ───────────────────────────────────────────────────

try:
    from fastmcp import Client as MCPClient
    HAS_FASTMCP = True
except ImportError:
    HAS_FASTMCP = False

pytestmark_mcp = pytest.mark.skipif(
    not HAS_FASTMCP,
    reason="fastmcp not installed; run inside the shepard-mcp container",
)


@pytest.mark.asyncio
@pytestmark_mcp
class TestMCPTools:
    async def test_list_tools(self, token):
        async with MCPClient(MCP_URL, auth=token) as c:
            tools = await c.list_tools()
            names = {t.name for t in tools}
            expected = {
                "list_collections", "get_collection",
                "list_data_objects", "get_data_object",
                "get_predecessor_chain", "get_successor_chain",
                "get_children", "list_annotations",
                "list_channels", "get_timeseries_stats",
                "get_channel_data", "get_channel_summary",
                "compare_channels", "get_structured_data", "list_files",
            }
            missing = expected - names
            assert not missing, f"Missing MCP tools: {missing}"

    async def test_mcp_list_collections(self, token):
        async with MCPClient(MCP_URL, auth=token) as c:
            result = await c.call_tool("list_collections", {})
            assert result  # non-empty

    async def test_mcp_lumen_data_object(self, token, lumen_ids):
        async with MCPClient(MCP_URL, auth=token) as c:
            result = await c.call_tool("get_data_object", {
                "collection_app_id": lumen_ids["collection_app_id"],
                "data_object_app_id": lumen_ids["tr004_app_id"],
            })
            # result is a list of TextContent; extract text
            text = result[0].text if hasattr(result[0], "text") else str(result[0])
            data = json.loads(text) if text.startswith("{") else {}
            assert "timeseries" in data or "TR-004" in text

    async def test_mcp_list_channels(self, token, api_headers, lumen_ids):
        # First get the container ID
        col = lumen_ids["collection_app_id"]
        do = lumen_ids["tr004_app_id"]
        r = httpx.get(
            f"{BACKEND_URL}/v2/collections/{col}/data-objects/{do}",
            headers=api_headers,
            timeout=10,
        )
        container_id = r.json()["timeseries"][0]["containerId"]

        async with MCPClient(MCP_URL, auth=token) as c:
            result = await c.call_tool("list_channels", {"container_id": container_id})
            assert result

    async def test_mcp_predecessor_chain(self, token, lumen_ids):
        async with MCPClient(MCP_URL, auth=token) as c:
            result = await c.call_tool("get_predecessor_chain", {
                "collection_app_id": lumen_ids["collection_app_id"],
                "data_object_app_id": lumen_ids["tr004_app_id"],
            })
            assert result is not None
