"""Integration tests for GET /v2/users/me."""

from __future__ import annotations

import httpx
import pytest


def test_me_returns_200(http: httpx.Client) -> None:
    resp = http.get("/v2/users/me")
    assert resp.status_code == 200, resp.text


def test_me_has_required_fields(http: httpx.Client) -> None:
    body = http.get("/v2/users/me").json()
    for field in ("username", "appId"):
        assert field in body, f"Missing field '{field}' in /v2/users/me response"


def test_me_app_id_is_uuid(http: httpx.Client) -> None:
    import uuid

    body = http.get("/v2/users/me").json()
    app_id = body.get("appId", "")
    try:
        uuid.UUID(app_id)
    except ValueError:
        pytest.fail(f"appId '{app_id}' is not a valid UUID")


def test_me_username_nonempty(http: httpx.Client) -> None:
    body = http.get("/v2/users/me").json()
    assert body.get("username"), "username must be a non-empty string"


def test_me_unauthenticated_returns_401(base_url: str) -> None:
    with httpx.Client(base_url=base_url, timeout=10.0) as anon:
        resp = anon.get("/v2/users/me")
    assert resp.status_code == 401, (
        f"Expected 401 for unauthenticated /v2/users/me, got {resp.status_code}"
    )
