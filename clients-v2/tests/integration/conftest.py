"""Integration test fixtures for the shepard /v2/ API.

These tests require a running shepard instance.  Set:

    SHEPARD_URL      base URL, e.g. https://shepard.example.com  (no trailing slash)
    SHEPARD_API_KEY  a valid Keycloak Bearer token (JWT)

Both variables must be present; any test that depends on the fixtures will be
skipped automatically when they are absent.

Design note
-----------
The Kiota-generated client (shepard_v2/) is built by `make -C clients-v2
generate-python` and is empty until that step runs.  These tests use ``httpx``
directly so they can be written and maintained before the client is generated.
The Kiota runtime fixtures (``request_adapter``, ``v2_client``) are wired up
so they can be used as-is once the generated package is populated.
"""

from __future__ import annotations

import os
import uuid

import httpx
import pytest

# ---------------------------------------------------------------------------
# Skip sentinels
# ---------------------------------------------------------------------------

_URL_VAR = "SHEPARD_URL"
_KEY_VAR = "SHEPARD_API_KEY"

_missing = [v for v in (_URL_VAR, _KEY_VAR) if not os.environ.get(v)]
_integration_skip = pytest.mark.skipif(
    bool(_missing),
    reason=f"Integration tests require env vars: {', '.join(_missing) or 'none missing'}",
)


# ---------------------------------------------------------------------------
# Core fixtures
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def base_url() -> str:
    url = os.environ.get(_URL_VAR, "")
    if not url:
        pytest.skip(f"{_URL_VAR} not set — skipping integration tests")
    return url.rstrip("/")


@pytest.fixture(scope="session")
def api_key() -> str:
    key = os.environ.get(_KEY_VAR, "")
    if not key:
        pytest.skip(f"{_KEY_VAR} not set — skipping integration tests")
    return key


@pytest.fixture(scope="session")
def auth_headers(api_key: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {api_key}"}


@pytest.fixture(scope="session")
def http(base_url: str, auth_headers: dict[str, str]) -> httpx.Client:
    """A pre-configured httpx client for the session."""
    with httpx.Client(
        base_url=base_url,
        headers={**auth_headers, "Content-Type": "application/json"},
        timeout=30.0,
    ) as client:
        yield client


# ---------------------------------------------------------------------------
# Kiota runtime fixtures (ready for use once generate-python has run)
# ---------------------------------------------------------------------------


@pytest.fixture(scope="session")
def request_adapter(base_url: str, api_key: str):
    """Kiota HttpxRequestAdapter wired with bearer-token auth.

    Returns None and skips the test gracefully when the Kiota runtime is not
    installed or the generated client has not yet been generated.
    """
    try:
        from kiota_abstractions.authentication import (
            AnonymousAuthenticationProvider,
        )
        from kiota_http.httpx_request_adapter import HttpxRequestAdapter
    except ImportError:
        pytest.skip("kiota-http not installed — skipping Kiota adapter fixture")

    class _BearerProvider(AnonymousAuthenticationProvider):
        """Injects the static bearer token into every request."""

        async def authenticate_request(self, request, additional_auth_context=None):
            request.headers["Authorization"] = f"Bearer {api_key}"

    adapter = HttpxRequestAdapter(authentication_provider=_BearerProvider())
    adapter.base_url = base_url
    return adapter


@pytest.fixture(scope="session")
def v2_client(request_adapter):
    """ShepardV2Client instance (requires generated code + Kiota runtime)."""
    import importlib
    import inspect

    try:
        mod = importlib.import_module("shepard_v2")
    except ImportError:
        pytest.skip("shepard_v2 package not importable — run make generate-python first")

    candidates = [
        getattr(mod, name)
        for name in dir(mod)
        if name.lower().endswith("client") and inspect.isclass(getattr(mod, name))
    ]
    if not candidates:
        pytest.skip("No *Client class found in shepard_v2 — run make generate-python")

    client_cls = candidates[0]
    return client_cls(request_adapter)


# ---------------------------------------------------------------------------
# Resource-lifecycle helpers
# ---------------------------------------------------------------------------


@pytest.fixture
def unique_name() -> str:
    """A short name that is unique per test invocation."""
    return f"pytest-{uuid.uuid4().hex[:8]}"


@pytest.fixture
def managed_collection(http: httpx.Client, unique_name: str):
    """Create a Collection for a test and delete it afterwards."""
    resp = http.post(
        "/v2/collections",
        json={"title": unique_name, "description": "integration test — safe to delete"},
    )
    assert resp.status_code == 201, f"Collection create failed: {resp.text}"
    col = resp.json()
    yield col
    http.delete(f"/v2/collections/{col['appId']}")
