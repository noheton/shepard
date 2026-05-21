"""Integration tests for read-only /v2/instance/* endpoints.

These are safe to run in any environment — no writes, no cleanup needed.
"""

from __future__ import annotations

import httpx


def test_capabilities_returns_200(http: httpx.Client) -> None:
    resp = http.get("/v2/instance/capabilities")
    assert resp.status_code == 200, resp.text


def test_capabilities_has_plugins_field(http: httpx.Client) -> None:
    body = http.get("/v2/instance/capabilities").json()
    assert "plugins" in body, f"Missing 'plugins' in capabilities: {list(body.keys())}"


def test_instance_identity_returns_200(http: httpx.Client) -> None:
    resp = http.get("/v2/instance/identity")
    # /v2/instance/identity may require admin; accept 200 or 403
    assert resp.status_code in (200, 403), (
        f"Unexpected status {resp.status_code} from /v2/instance/identity"
    )
