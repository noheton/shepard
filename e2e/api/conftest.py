"""Session-scoped fixtures for the shepard API integration test suite.

Loads .env.integration (if present) so developers can keep credentials outside
the repo without touching CI environment variables.
"""

import os
import pytest
import httpx
from dotenv import load_dotenv

# Load .env.integration from the e2e/api/ directory if it exists.
load_dotenv(dotenv_path=os.path.join(os.path.dirname(__file__), ".env.integration"))

BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080/shepard/api")
# Strip trailing /shepard/api to get the root URL for /v2/ endpoints.
BACKEND_ROOT = BACKEND_URL.removesuffix("/shepard/api")


# ── Auth ───────────────────────────────────────────────────────────────────────

@pytest.fixture(scope="session")
def api_key() -> str:
    """Obtain an API key for the demo admin user.

    Priority:
    1. SHEPARD_API_KEY env var — fastest; set in CI secrets or .env.integration.
    2. Keycloak ROPC flow — mirrors entrypoint.sh exactly.
    """
    if key := os.getenv("SHEPARD_API_KEY"):
        return key
    from helpers.auth import fetch_api_key_via_keycloak
    return fetch_api_key_via_keycloak(
        backend_url=BACKEND_URL,
        kc_url=os.getenv("KC_URL", "http://localhost:8082"),
        realm=os.getenv("KC_REALM", "shepard-demo"),
        client_id=os.getenv("KC_CLIENT_ID", "frontend-dev"),
        username=os.getenv("KC_ADMIN_USER", "admin"),
        password=os.getenv("KC_ADMIN_PASS", "admin-demo"),
    )


# ── HTTP clients ───────────────────────────────────────────────────────────────

@pytest.fixture(scope="session")
def http(api_key) -> httpx.Client:
    """Authenticated httpx client for /v2/ and error-shape tests."""
    return httpx.Client(
        base_url=BACKEND_ROOT,
        headers={"apikey": api_key},
        verify=False,
        timeout=15.0,
    )


@pytest.fixture(scope="session")
def http_anon() -> httpx.Client:
    """Unauthenticated httpx client for auth-gating tests."""
    return httpx.Client(base_url=BACKEND_ROOT, verify=False, timeout=10.0)


# ── Seeded collection fixtures ─────────────────────────────────────────────────

LUMEN_NAME = "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)"


@pytest.fixture(scope="session")
def lumen_collection(http):
    """Locate the seeded LUMEN collection by its canonical name.

    Fails fast with a readable message if seed.py hasn't run — catches the
    'suite ran before seeder finished' footgun before any test fires.
    """
    r = http.get(
        "/shepard/api/collections",
        params={"page": 0, "size": 200},
    )
    assert r.status_code == 200, f"Could not list collections: {r.status_code}"
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    matches = [c for c in items if c.get("name") == LUMEN_NAME]
    if not matches:
        pytest.fail(
            f"Seed collection '{LUMEN_NAME}' not found. "
            "Run examples/lumen-showcase/seed.py before the integration suite."
        )
    return matches[0]


@pytest.fixture(scope="session")
def lumen_data_objects(http, lumen_collection):
    """All DataObjects in the LUMEN collection, indexed by name."""
    coll_id = lumen_collection["id"]
    r = http.get(f"/shepard/api/collections/{coll_id}/dataobjects")
    assert r.status_code == 200, f"Could not list DataObjects: {r.status_code}"
    body = r.json()
    items = body.get("results", body) if isinstance(body, dict) else body
    return {o["name"]: o for o in items}


# ── Ephemeral CRUD fixtures ────────────────────────────────────────────────────

@pytest.fixture()
def temp_collection(http):
    """Create a throwaway collection; delete it after the test.

    Uses a uuid suffix so concurrent/interrupted runs don't collide on name.
    """
    import uuid
    name = f"__integration-test-temp__{uuid.uuid4().hex[:8]}"
    r = http.post(
        "/shepard/api/collections",
        json={"name": name},
    )
    assert r.status_code in (200, 201), f"Could not create temp collection: {r.status_code} {r.text}"
    coll = r.json()
    yield coll
    try:
        http.delete(f"/shepard/api/collections/{coll['id']}")
    except Exception:
        pass
