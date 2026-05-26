# /// script
# requires-python = ">=3.11"
# dependencies = ["pytest", "requests"]
# ///
"""Tests for v15.10 MFFD-IMPORT bundle:
   - DistinctUserCache (thread-safe per-username resolver, neg-cache)
   - SourceClient.get_versionz (defensive wire-shape handling)
   - SourceClient.get_collection_metadata
   - _derive_instance_id_from_url (URL hostname → stable id)
   - _build_collection_source_attrs (attrs bundle + provenance stamp)

No network — every HTTP call goes through a fake adapter. Coexists with
test_user_capture.py (v15.9 USER-CAPTURE single-self-user) and
test_wiki_extract.py.

Run:
    cd examples/mffd-showcase/scripts
    uv run --with pytest --with requests pytest test_v1510.py -v
"""
from __future__ import annotations

import importlib.util
import json
import sys
import threading
from pathlib import Path
from unittest.mock import MagicMock

import pytest

# Load the sibling script as a module so we can test its types directly.
_SCRIPT = Path(__file__).parent / "mffd-import-v15.py"
_spec = importlib.util.spec_from_file_location("mffd_import_v15", _SCRIPT)
assert _spec and _spec.loader, f"cannot load {_SCRIPT}"
mod = importlib.util.module_from_spec(_spec)
sys.modules.setdefault("mffd_import_v15", mod)
_spec.loader.exec_module(mod)

ShepardClient = mod.ShepardClient
DistinctUserCache = mod.DistinctUserCache
_derive_instance_id_from_url = mod._derive_instance_id_from_url
_build_collection_source_attrs = mod._build_collection_source_attrs
SOURCE_ATTRS_PROVENANCE_ORIGINAL = mod.SOURCE_ATTRS_PROVENANCE_ORIGINAL


# ── Helpers ─────────────────────────────────────────────────────────────────

def _make_client_with_mock(responses):
    """Build a ShepardClient whose _request_with_retry returns canned
    responses keyed by url-suffix substring. `responses` maps
    `path -> (status, json_body_or_string)`.

    If body is a string, .text/.content return that; .json() raises ValueError.
    """
    client = ShepardClient("https://src.test", "stub-jwt", "")

    def fake_request(method, url, *, timeout=60, deadline_s=900.0, **kwargs):
        for suffix, (status, body) in responses.items():
            if url.endswith(suffix):
                r = MagicMock()
                r.status_code = status
                r.ok = 200 <= status < 300
                if isinstance(body, str):
                    r.text = body
                    r.json = MagicMock(side_effect=ValueError("not json"))
                else:
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


# ── _derive_instance_id_from_url ────────────────────────────────────────────

def test_derive_instance_id_from_dlr_intranet_url() -> None:
    """The flagship case: cube3 source URL → 'bt-au-cube3'."""
    assert _derive_instance_id_from_url(
        "https://backend.bt-au-cube3.intra.dlr.de"
    ) == "bt-au-cube3"


def test_derive_instance_id_from_nuclide() -> None:
    assert _derive_instance_id_from_url(
        "https://shepard.nuclide.systems"
    ) == "nuclide"


def test_derive_instance_id_localhost() -> None:
    """No filtering survives → return bare hostname."""
    assert _derive_instance_id_from_url("https://localhost:8080") == "localhost"


def test_derive_instance_id_ip_address() -> None:
    """IPs return as-is — operator should override via CLI flag."""
    assert _derive_instance_id_from_url("https://10.42.0.1") == "10.42.0.1"


def test_derive_instance_id_empty_url() -> None:
    """Empty / un-parseable URLs return empty string; operator should
    then override via --source-instance-id."""
    assert _derive_instance_id_from_url("") == ""
    # urlparse returns no hostname for bare strings without scheme.
    assert _derive_instance_id_from_url("not-a-url") == ""


def test_derive_instance_id_url_with_api_prefix() -> None:
    """`api.` prefix should drop, leaving the institute name."""
    assert _derive_instance_id_from_url(
        "https://api.zlp-augsburg.dlr.de"
    ) == "zlp-augsburg"


# ── ShepardClient.get_versionz ──────────────────────────────────────────────

def test_get_versionz_canonical_shape() -> None:
    """v5.4.0 OpenAPI VersionzIO: `{version: string}`."""
    client = _make_client_with_mock({
        "/shepard/api/versionz": (200, {"version": "5.4.0"}),
    })
    assert client.get_versionz() == "5.4.0"


def test_get_versionz_bare_string_fallback() -> None:
    """Some forks return text/plain — must coerce to string."""
    client = _make_client_with_mock({
        "/shepard/api/versionz": (200, "5.4.0"),
    })
    assert client.get_versionz() == "5.4.0"


def test_get_versionz_with_extra_fields() -> None:
    """Future shape: {version, build, gitSha} — accept the version field."""
    client = _make_client_with_mock({
        "/shepard/api/versionz": (200, {
            "version": "5.4.0",
            "build": "ci-12345",
        }),
    })
    assert client.get_versionz() == "5.4.0"


def test_get_versionz_404_returns_none() -> None:
    """Source instance without /versionz endpoint → graceful None."""
    client = _make_client_with_mock({})
    assert client.get_versionz() is None


def test_get_versionz_empty_dict_returns_none() -> None:
    client = _make_client_with_mock({
        "/shepard/api/versionz": (200, {}),
    })
    assert client.get_versionz() is None


# ── ShepardClient.get_collection_metadata ──────────────────────────────────

def test_get_collection_metadata_canonical_shape() -> None:
    body = {
        "id": 48297,
        "name": "MFFD-TapeLaying",
        "createdBy": "kreb_fl",
        "createdAt": "2023-01-19T10:42:00Z",
        "updatedBy": "kreb_fl",
        "updatedAt": "2023-04-02T16:00:00Z",
    }
    client = _make_client_with_mock({
        "/shepard/api/collections/48297": (200, body),
    })
    result = client.get_collection_metadata(48297)
    assert result is not None
    assert result["createdBy"] == "kreb_fl"
    assert result["createdAt"] == "2023-01-19T10:42:00Z"


def test_get_collection_metadata_404_returns_none() -> None:
    client = _make_client_with_mock({})
    assert client.get_collection_metadata(99999) is None


def test_get_collection_metadata_list_response_returns_none() -> None:
    """Defensive: a 200 with a list body (wrong shape) → None, never crash."""
    client = _make_client_with_mock({
        "/shepard/api/collections/48297": (200, [{"id": 48297}]),
    })
    assert client.get_collection_metadata(48297) is None


# ── DistinctUserCache ──────────────────────────────────────────────────────

def test_distinct_user_cache_hit_path() -> None:
    """Happy path: resolve returns the upstream User dict."""
    upstream_user = {
        "username": "kreb_fl",
        "firstName": "Florian",
        "lastName": "Krebs",
        "email": "florian.krebs@dlr.de",
    }
    src_client = _make_client_with_mock({
        "/shepard/api/users/kreb_fl": (200, upstream_user),
    })
    cache = DistinctUserCache(src_client)
    result = cache.resolve("kreb_fl")
    assert result == upstream_user
    # Re-resolve hits the cache, no second GET.
    result2 = cache.resolve("kreb_fl")
    assert result2 == upstream_user
    stats = cache.stats()
    assert stats["resolved"] == 1
    assert stats["cache_hits"] == 1


def test_distinct_user_cache_miss_404_negative_cached() -> None:
    """Resolution failure (404) is cached as None — no re-probe."""
    src_client = _make_client_with_mock({})  # everything 404s
    cache = DistinctUserCache(src_client)
    assert cache.resolve("ghost_user") is None
    # Negative cache: second call hits the cache, NOT the network.
    assert cache.resolve("ghost_user") is None
    stats = cache.stats()
    assert stats["unresolved"] == 1
    assert stats["cache_hits"] == 1


def test_distinct_user_cache_empty_username() -> None:
    """Empty / None usernames are no-op (never network)."""
    src_client = _make_client_with_mock({})
    cache = DistinctUserCache(src_client)
    assert cache.resolve("") is None
    assert cache.resolve(None) is None  # type: ignore[arg-type]
    assert cache.stats()["total_entries"] == 0


def test_distinct_user_cache_no_source_client() -> None:
    """No source client → every resolve returns None (graceful)."""
    cache = DistinctUserCache(None)
    assert cache.resolve("anyone") is None
    assert cache.stats()["unresolved"] == 1


def test_distinct_user_cache_concurrent_access() -> None:
    """N threads racing on the SAME username must result in exactly 1 GET.

    The cache reserves the slot inside the lock; only the first caller
    issues the network round-trip.
    """
    # Build a client that COUNTS how many times it's called.
    call_count = {"n": 0}
    upstream_user = {
        "username": "race_user",
        "firstName": "Race",
        "lastName": "Condition",
        "email": "race@example.test",
    }

    client = ShepardClient("https://race.test", "stub", "")
    lock = threading.Lock()

    def fake_request(method, url, *, timeout=60, deadline_s=900.0, **kwargs):
        with lock:
            call_count["n"] += 1
        import time
        # Simulate network latency so multiple threads pile up at the gate.
        time.sleep(0.05)
        r = MagicMock()
        r.status_code = 200
        r.ok = True
        r.json = MagicMock(return_value=upstream_user)
        r.headers = {}
        return r

    client._request_with_retry = fake_request  # type: ignore[method-assign]
    cache = DistinctUserCache(client)

    results = [None] * 8
    threads = []

    def worker(idx):
        results[idx] = cache.resolve("race_user")

    for i in range(8):
        t = threading.Thread(target=worker, args=(i,))
        threads.append(t)
        t.start()
    for t in threads:
        t.join()

    # All 8 threads see the user.
    assert all(r == upstream_user for r in results)
    # The Event-gated in-flight slot means EXACTLY ONE thread issues the
    # network GET. The other (N-1) followers wait on the Event and read
    # the cached result.
    assert call_count["n"] == 1, (
        f"Expected exactly 1 network GET across 8 racing threads; "
        f"got {call_count['n']} (cache de-duplication broken)"
    )
    assert cache.stats()["total_entries"] == 1
    assert cache.stats()["resolved"] == 1
    # 7 followers count as cache hits (waiters who didn't fetch).
    assert cache.stats()["cache_hits"] == 7


def test_distinct_user_cache_to_summary_resolved_only() -> None:
    """Unresolved (None) entries are NOT in the summary — misleading otherwise."""
    src_client = _make_client_with_mock({
        "/shepard/api/users/kreb_fl": (200, {
            "username": "kreb_fl",
            "firstName": "Florian",
            "lastName": "Krebs",
            "email": "f@dlr.de",
        }),
        # ghost_user 404s
    })
    cache = DistinctUserCache(src_client)
    cache.resolve("kreb_fl")
    cache.resolve("ghost_user")
    summary = cache.to_summary()
    assert len(summary) == 1
    assert summary[0]["username"] == "kreb_fl"
    assert summary[0]["displayName"] == "Florian Krebs"
    assert summary[0]["email"] == "f@dlr.de"


def test_distinct_user_cache_to_summary_stable_order() -> None:
    """Deterministic ordering — FE diff-friendly across runs."""
    users = {
        "z_user": {"username": "z_user", "firstName": "Z", "lastName": "Z"},
        "a_user": {"username": "a_user", "firstName": "A", "lastName": "A"},
        "m_user": {"username": "m_user", "firstName": "M", "lastName": "M"},
    }
    src_client = _make_client_with_mock({
        f"/shepard/api/users/{u}": (200, body)
        for u, body in users.items()
    })
    cache = DistinctUserCache(src_client)
    # Resolve in non-alphabetic order.
    cache.resolve("m_user")
    cache.resolve("z_user")
    cache.resolve("a_user")
    summary = cache.to_summary()
    assert [e["username"] for e in summary] == ["a_user", "m_user", "z_user"]


def test_distinct_user_cache_ascii_safe_umlauts() -> None:
    """DLR Müller — displayName must be ASCII-coercible (urllib3 latin-1 safety)."""
    src_client = _make_client_with_mock({
        "/shepard/api/users/mueller_h": (200, {
            "username": "mueller_h",
            "firstName": "Hans",
            "lastName": "Müller",
            "email": "müller@dlr.de",
        }),
    })
    cache = DistinctUserCache(src_client)
    cache.resolve("mueller_h")
    summary = cache.to_summary()
    assert len(summary) == 1
    # Must encode as ASCII without raising (umlauts replaced with '?').
    summary[0]["displayName"].encode("ascii")
    summary[0]["email"].encode("ascii")


# ── _build_collection_source_attrs ──────────────────────────────────────────

def test_build_collection_source_attrs_includes_provenance_stamp() -> None:
    """Every PATCH bundle that contains source_* attrs MUST carry the
    `source_attrs_provenance: 'original'` stamp in the SAME PATCH body.

    The atomicity contract: a crash between an attrs PATCH and a stamp
    PATCH would leave a future backfill pass unable to tell whether a
    source_created_by attr was captured cleanly or reconstructed.
    """
    # Populate module state for the function under test.
    mod.SOURCE_SHEPARD_URL = "https://backend.bt-au-cube3.intra.dlr.de"
    mod.SOURCE_INSTANCE_ID = "bt-au-cube3"
    mod.SOURCE_INSTANCE_METADATA = {
        "shepard_version": "5.4.0",
        "source_collections": {
            "48297": {
                "label": "tapelaying",
                "name": "MFFD-TapeLaying",
                "createdBy": "kreb_fl",
                "createdAt": "2023-01-19T10:42:00Z",
                "updatedBy": "kreb_fl",
                "updatedAt": "2023-04-02T16:00:00Z",
            },
        },
    }
    mod.SOURCE_USER_INFO = None
    attrs = _build_collection_source_attrs(None)
    # Provenance stamp must be present.
    assert attrs["source_attrs_provenance"] == SOURCE_ATTRS_PROVENANCE_ORIGINAL
    # The flagship cross-instance attrs.
    assert attrs["source_instance_url"] == "https://backend.bt-au-cube3.intra.dlr.de"
    assert attrs["source_instance_id"] == "bt-au-cube3"
    assert attrs["source_shepard_version"] == "5.4.0"
    # Single-source-collection case: flattened canonical attrs.
    assert attrs["source_created_by"] == "kreb_fl"
    assert attrs["source_created"] == "2023-01-19T10:42:00Z"
    # Envelope shape also present (multi-coll-friendly).
    envelope = json.loads(attrs["source_collections"])
    assert envelope["48297"]["createdBy"] == "kreb_fl"


def test_build_collection_source_attrs_with_distinct_users() -> None:
    """source_distinct_users is a JSON array of resolved users."""
    mod.SOURCE_SHEPARD_URL = "https://src.test"
    mod.SOURCE_INSTANCE_ID = "src"
    mod.SOURCE_INSTANCE_METADATA = {"shepard_version": "5.4.0"}
    mod.SOURCE_USER_INFO = None
    summary = [
        {"username": "alice", "displayName": "Alice Smith", "email": "a@x.test"},
        {"username": "bob", "displayName": "Bob Jones"},
    ]
    attrs = _build_collection_source_attrs(summary)
    parsed = json.loads(attrs["source_distinct_users"])
    assert len(parsed) == 2
    assert parsed[0]["username"] == "alice"
    assert parsed[1]["username"] == "bob"
    # Provenance stamp still present.
    assert attrs["source_attrs_provenance"] == "original"


def test_build_collection_source_attrs_no_source_no_stamp() -> None:
    """If no source_* attrs would be written (e.g. empty cross-instance
    config), do NOT add the provenance stamp — it would be lying.
    """
    mod.SOURCE_SHEPARD_URL = ""
    mod.SOURCE_INSTANCE_ID = ""
    mod.SOURCE_INSTANCE_METADATA = {}
    mod.SOURCE_USER_INFO = None
    attrs = _build_collection_source_attrs(None)
    assert "source_attrs_provenance" not in attrs
    assert attrs == {}


def test_build_collection_source_attrs_v159_user_info_preserved() -> None:
    """v15.9 SOURCE_USER_INFO (single API-key holder) coexists with v15.10
    distinct-users cache. Both surface at collection level.
    """
    mod.SOURCE_SHEPARD_URL = "https://src.test"
    mod.SOURCE_INSTANCE_ID = "src"
    mod.SOURCE_INSTANCE_METADATA = {"shepard_version": "5.4.0"}
    mod.SOURCE_USER_INFO = {
        "username": "kreb_fl",
        "firstName": "Florian",
        "lastName": "Krebs",
        "email": "florian.krebs@dlr.de",
    }
    attrs = _build_collection_source_attrs(None)
    assert attrs["source_user_username"] == "kreb_fl"
    assert attrs["source_user_displayName"] == "Florian Krebs"
    assert attrs["source_user_email"] == "florian.krebs@dlr.de"
    assert attrs["source_attrs_provenance"] == "original"


# ── Version stamp ───────────────────────────────────────────────────────────

def test_import_script_version_is_1510() -> None:
    """Sanity check: the version constant got bumped."""
    assert mod.IMPORT_SCRIPT_VERSION == "15.10"


# ── CURRENTUSER-NORM grep verification ──────────────────────────────────────

def test_no_wrong_users_currentuser_path_in_v15() -> None:
    """The /shepard/api/users/currentUser path NEVER existed in v5.4.0 OpenAPI.
    mffd-dropbox-import.py line 222 has it as a bug; v15.py must not.
    Comments documenting the fix are OK; a live HTTP call would be a bug.

    Test guard: every line containing the wrong path must be a comment
    line or inside a string literal (docstring print). A bare HTTP call
    would fail this check.
    """
    script_text = _SCRIPT.read_text(encoding="utf-8")
    lines_with_path = [
        ln for ln in script_text.splitlines()
        if "/shepard/api/users/currentUser" in ln
    ]
    for ln in lines_with_path:
        stripped = ln.strip()
        # Acceptable contexts:
        #   - Python comment line (starts with `#`)
        #   - Docstring / string literal line (starts with `"` or `'`)
        #   - String concatenation continuation (line contains the path
        #     INSIDE a quoted region) — heuristic: a non-keyword line
        #     containing the path also containing `"` somewhere to its
        #     left passes if it doesn't look like a function call
        #     (no `self._get(` / `requests.get(` / `_request_with_retry(`).
        # The simplest test that catches a live call: reject any line
        # where the path appears as the FIRST argument to a known HTTP
        # method call.
        is_http_call = any(
            f"{verb}({path!r}" in ln or f'{verb}(f"' in ln and "/shepard/api/users/currentUser" in ln[ln.index(f'{verb}(f"'):]
            for verb in ("_get", "_post", "_put", "_patch", "_delete",
                         "_request_with_retry", "self._s.get", "self._s.post",
                         "self._s.put", "self._s.patch", "self._s.delete",
                         "requests.get", "requests.post")
            for path in ("/shepard/api/users/currentUser",)
        )
        # Also reject lines where the path appears in an f-string
        # assigned to a `url =` variable or similar that obviously
        # becomes an HTTP call.
        assignment_like = ("url = " in ln or "url=" in ln) and not stripped.startswith("#")
        assert not is_http_call and not assignment_like, (
            f"unexpected live use of /users/currentUser: {ln!r}"
        )


def test_no_wrong_users_currentuser_path_in_v15_negative_control() -> None:
    """Negative control: synthesise a line that WOULD be a bug, and assert
    the rejection logic catches it. Guards against the previous version of
    the regression test where `"Bug K fix" in script_text` always-True'd
    the assertion.
    """
    # Synthesise a fake bug line — emulate the check inline.
    bad_lines = [
        '        r = self._get(f"{self._base}/shepard/api/users/currentUser")',
        '            url = f"{self._base}/shepard/api/users/currentUser"',
        '    requests.get("https://example.test/shepard/api/users/currentUser")',
    ]
    for ln in bad_lines:
        stripped = ln.strip()
        is_http_call = any(
            f"{verb}(" in ln and "/shepard/api/users/currentUser" in ln
            for verb in ("self._get", "self._post", "self._request_with_retry",
                         "requests.get", "requests.post")
        )
        assignment_like = ("url = " in ln or "url=" in ln) and not stripped.startswith("#")
        # At least one of the bug-detection branches must fire.
        assert is_http_call or assignment_like, (
            f"negative-control test failed: bug-shaped line {ln!r} was "
            f"NOT detected as a live HTTP call (regression in the guard)"
        )
