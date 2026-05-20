"""Integration tests: authentication gating.

These tests do NOT require seed data and should pass on a freshly deployed
stack before seed.py has run.
"""


def test_unauthenticated_returns_401(http_anon):
    r = http_anon.get("/shepard/api/collections")
    assert r.status_code in (401, 403), (
        f"Expected 401/403 for unauthenticated request, got {r.status_code}. "
        "A 404 means the REST class is missing from the JAR — check the build."
    )


def test_v2_endpoints_return_401_not_404(http_anon):
    endpoints = [
        "/v2/admin/features",
        "/v2/users/me/preferences",
        "/v2/instance/identity",
    ]
    for path in endpoints:
        r = http_anon.get(path)
        assert r.status_code in (401, 403), (
            f"{path} returned {r.status_code} for unauthenticated request; "
            "expected 401/403. A 404 means the REST class didn't compile into "
            "the image — check that the v2 classes are in the JAR."
        )


def test_authenticated_collections_list(http):
    r = http.get("/shepard/api/collections")
    assert r.status_code == 200, (
        f"Authenticated collections list returned {r.status_code}: {r.text[:200]}"
    )
    body = r.json()
    # Accept both paginated envelope and raw list
    assert "results" in body or isinstance(body, list), (
        "Expected a paginated envelope with 'results' key or a plain list"
    )
