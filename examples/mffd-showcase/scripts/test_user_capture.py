# /// script
# requires-python = ">=3.11"
# dependencies = ["pytest", "requests"]
# ///
"""Tests for v15.9 MFFD-IMPORT-USER-CAPTURE source-user identity capture.

These tests exercise the SourceClient.resolve_self() path + the
X-Source-User-* header injection on the Session. No network — every
HTTP call goes through a fake adapter that returns canned responses.

Per `feedback_always_write_tests.md`: every new feature ships with
unit tests in the same PR. The smart-warmup module is the integration
test surface; this file covers the pure logic.

Run:
    cd examples/mffd-showcase/scripts
    uv run --with pytest --with requests pytest test_user_capture.py -v

Or:
    python3 -m pytest examples/mffd-showcase/scripts/test_user_capture.py -v
"""
from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

# Load the sibling script as a module so we can test its types directly.
_SCRIPT = Path(__file__).parent / "mffd-import-v15.py"
_spec = importlib.util.spec_from_file_location("mffd_import_v15", _SCRIPT)
assert _spec and _spec.loader, f"cannot load {_SCRIPT}"
mod = importlib.util.module_from_spec(_spec)
sys.modules["mffd_import_v15"] = mod
_spec.loader.exec_module(mod)

ShepardClient = mod.ShepardClient


# ── JWT decode (pure helper) ────────────────────────────────────────────────

def _make_jwt(sub: str) -> str:
    """Forge a JWT-shaped token with the given `sub` claim. Header + signature
    are stubs (the v15.9 code only reads the body)."""
    import base64
    header = base64.urlsafe_b64encode(b'{"alg":"none"}').rstrip(b"=").decode()
    body = base64.urlsafe_b64encode(json.dumps({"sub": sub}).encode()).rstrip(b"=").decode()
    sig = "stub-signature"
    return f"{header}.{body}.{sig}"


def test_decode_jwt_sub_username() -> None:
    """JWT with literal username sub (cube3 / kreb_fl shape)."""
    token = _make_jwt("kreb_fl")
    assert ShepardClient._decode_jwt_sub(token) == "kreb_fl"


def test_decode_jwt_sub_uuid() -> None:
    """JWT with UUID sub (nuclide fork shape)."""
    token = _make_jwt("ee4c010f-d648-4630-aea6-b81ef2a9c296")
    assert ShepardClient._decode_jwt_sub(token) == "ee4c010f-d648-4630-aea6-b81ef2a9c296"


def test_decode_jwt_sub_malformed_returns_none() -> None:
    """Garbage in → None out, never an exception."""
    assert ShepardClient._decode_jwt_sub("not.a.jwt") is None
    assert ShepardClient._decode_jwt_sub("") is None
    assert ShepardClient._decode_jwt_sub("only-one-segment") is None
    # mypy-incompatible but guards against caller bug
    assert ShepardClient._decode_jwt_sub(None) is None  # type: ignore[arg-type]


def test_decode_jwt_sub_missing_sub_returns_none() -> None:
    """JWT body without `sub` claim → None."""
    import base64
    body = base64.urlsafe_b64encode(json.dumps({"iat": 12345}).encode()).rstrip(b"=").decode()
    token = f"e30.{body}.sig"
    assert ShepardClient._decode_jwt_sub(token) is None


# ── ASCII-safe header coercion (pure helper) ────────────────────────────────

def test_ascii_safe_header_passes_ascii_unchanged() -> None:
    assert ShepardClient._ascii_safe_header("Florian Krebs") == "Florian Krebs"
    assert ShepardClient._ascii_safe_header("kreb_fl") == "kreb_fl"
    assert ShepardClient._ascii_safe_header("flo@dlr.de") == "flo@dlr.de"


def test_ascii_safe_header_replaces_umlauts() -> None:
    """DLR names with umlauts must not crash urllib3's latin-1 header encoder."""
    out = ShepardClient._ascii_safe_header("Müller")
    # Replacement char is "?" (codec='replace'); important is: pure ASCII bytes.
    assert out.encode("ascii") == out.encode("ascii")
    assert "M" in out  # leading char survived


def test_ascii_safe_header_empty_returns_empty() -> None:
    assert ShepardClient._ascii_safe_header("") == ""
    assert ShepardClient._ascii_safe_header(None) == ""  # type: ignore[arg-type]


# ── resolve_self() — the main feature ────────────────────────────────────────

def _make_client_with_mock(responses: dict[str, tuple[int, dict | list | None]]):
    """Build a ShepardClient whose _request_with_retry returns canned
    responses keyed by url-suffix substring. `responses` maps `path -> (status, json)`.
    """
    jwt = _make_jwt("kreb_fl")
    client = ShepardClient("https://example.test", jwt, "")

    def fake_request(method, url, *, timeout=60, deadline_s=900.0, **kwargs):
        for suffix, (status, body) in responses.items():
            if url.endswith(suffix):
                r = MagicMock()
                r.status_code = status
                r.ok = 200 <= status < 300
                r.json = MagicMock(return_value=body)
                r.headers = {}
                return r
        # No match → simulate 404
        r = MagicMock()
        r.status_code = 404
        r.ok = False
        r.json = MagicMock(side_effect=ValueError("no body"))
        r.headers = {}
        return r

    client._request_with_retry = fake_request  # type: ignore[method-assign]
    return client


def test_resolve_self_success_via_jwt_sub() -> None:
    """Happy path: JWT sub → GET /users/{sub} returns the upstream User shape."""
    upstream_user = {
        "username": "kreb_fl",
        "firstName": "Florian",
        "lastName": "Krebs",
        "email": "florian.krebs@dlr.de",
        "subscriptionIds": [12, 34],
        "apiKeyIds": ["uuid-a", "uuid-b"],
    }
    client = _make_client_with_mock({
        "/shepard/api/users/kreb_fl": (200, upstream_user),
    })
    result = client.resolve_self()
    assert result is not None
    assert result["username"] == "kreb_fl"
    assert result["firstName"] == "Florian"
    assert result["email"] == "florian.krebs@dlr.de"


def test_resolve_self_falls_back_to_users_no_path_on_404() -> None:
    """v5 strict instances may not have a /users/{username} route for
    SSO-derived JWT subs. Fall back to /users (no path) which the fork
    treats as 'self'.
    """
    fork_self = {
        "username": "ee4c010f-d648-4630-aea6-b81ef2a9c296",
        "firstName": "Flo",
        "lastName": "Researcher",
        "email": "flo@demo.shepard.local",
        "effectiveDisplayName": "Flo Researcher",
        "appId": "019e3ce9-6744-7f19-8759-841ce3e01a82",
    }
    client = _make_client_with_mock({
        "/shepard/api/users/kreb_fl": (404, None),
        "/shepard/api/users": (200, fork_self),
    })
    result = client.resolve_self()
    assert result is not None
    assert result["username"] == "ee4c010f-d648-4630-aea6-b81ef2a9c296"
    assert result["effectiveDisplayName"] == "Flo Researcher"


def test_resolve_self_bails_on_upstream_list_response() -> None:
    """Strict upstream returns a LIST on /users (no path). v15.9 must NOT
    pick the first list element — that would silently mis-attribute every
    DO to a random user. Returns None instead.
    """
    client = _make_client_with_mock({
        "/shepard/api/users/kreb_fl": (404, None),
        "/shepard/api/users": (200, [{"username": "admin"}, {"username": "kreb_fl"}]),
    })
    assert client.resolve_self() is None


def test_resolve_self_returns_none_on_403() -> None:
    """JWT lacks read permission for /users (some institutes restrict).
    Must return None, never crash.
    """
    client = _make_client_with_mock({
        "/shepard/api/users/kreb_fl": (403, None),
        "/shepard/api/users": (403, None),
    })
    assert client.resolve_self() is None


def test_resolve_self_returns_none_on_jwt_decode_failure() -> None:
    """Garbage JWT → no sub → no /users/{sub} probe. Fallback /users (no
    path) is still tried; if that 404s too, return None.
    """
    client = ShepardClient("https://example.test", "totally-broken-jwt", "")
    fake_resps = {"/shepard/api/users": (404, None)}

    def fake_request(method, url, *, timeout=60, deadline_s=900.0, **kwargs):
        for suffix, (status, body) in fake_resps.items():
            if url.endswith(suffix):
                r = MagicMock()
                r.status_code = status
                r.ok = 200 <= status < 300
                r.json = MagicMock(return_value=body)
                return r
        r = MagicMock()
        r.status_code = 404
        r.ok = False
        return r

    client._request_with_retry = fake_request  # type: ignore[method-assign]
    assert client.resolve_self() is None


def test_resolve_self_returns_none_when_no_request_succeeds() -> None:
    """Network blip: _request_with_retry returns None for everything."""
    jwt = _make_jwt("kreb_fl")
    client = ShepardClient("https://example.test", jwt, "")
    client._request_with_retry = lambda *a, **kw: None  # type: ignore[method-assign]
    assert client.resolve_self() is None


# ── apply_source_user_headers() ─────────────────────────────────────────────

def test_apply_source_user_headers_sets_all_fields() -> None:
    client = ShepardClient("https://dest.test", _make_jwt("dest-uuid"), "")
    su = {
        "username": "kreb_fl",
        "firstName": "Florian",
        "lastName": "Krebs",
        "email": "florian.krebs@dlr.de",
        "effectiveDisplayName": "Florian Krebs",
    }
    applied = client.apply_source_user_headers(su, source_instance_url="https://cube3.dlr.de")
    assert applied is True
    h = client._s.headers
    assert h["X-Source-User-Username"] == "kreb_fl"
    assert h["X-Source-User-DisplayName"] == "Florian Krebs"
    assert h["X-Source-User-Email"] == "florian.krebs@dlr.de"
    assert h["X-Source-User-Instance"] == "https://cube3.dlr.de"


def test_apply_source_user_headers_none_user_is_noop() -> None:
    """Graceful degradation: passing None must return False and add
    NO headers (don't pollute the session with stale state).
    """
    client = ShepardClient("https://dest.test", _make_jwt("dest"), "")
    applied = client.apply_source_user_headers(None)
    assert applied is False
    assert "X-Source-User-Username" not in client._s.headers
    assert "X-Source-User-Email" not in client._s.headers


def test_apply_source_user_headers_constructs_display_from_names() -> None:
    """When the upstream payload lacks `effectiveDisplayName`, build it
    from firstName + lastName.
    """
    client = ShepardClient("https://dest.test", _make_jwt("dest"), "")
    client.apply_source_user_headers({
        "username": "kreb_fl",
        "firstName": "Florian",
        "lastName": "Krebs",
    })
    assert client._s.headers["X-Source-User-DisplayName"] == "Florian Krebs"


def test_apply_source_user_headers_falls_back_to_username_when_no_name() -> None:
    """If no first/last/effective name, display becomes the username so
    we never send an empty header.
    """
    client = ShepardClient("https://dest.test", _make_jwt("dest"), "")
    client.apply_source_user_headers({"username": "kreb_fl"})
    assert client._s.headers["X-Source-User-DisplayName"] == "kreb_fl"


def test_apply_source_user_headers_skips_empty_email() -> None:
    """Missing email — don't add an empty header (would be misleading)."""
    client = ShepardClient("https://dest.test", _make_jwt("dest"), "")
    client.apply_source_user_headers({"username": "kreb_fl"})
    assert "X-Source-User-Email" not in client._s.headers


def test_apply_source_user_headers_handles_umlauts() -> None:
    """DLR Müller — must NOT raise UnicodeEncodeError on the next write."""
    client = ShepardClient("https://dest.test", _make_jwt("dest"), "")
    client.apply_source_user_headers({
        "username": "mueller_h",
        "firstName": "Hans",
        "lastName": "Müller",
    })
    # If the value passed the ASCII coercion, this won't raise.
    client._s.headers["X-Source-User-DisplayName"].encode("ascii")
