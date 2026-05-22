"""Wire-shape tests for v15 import-script (Bugs D, G, H, L, E, B, C, I, K).

Each test asserts the JSON body our client emits matches the DLR v5.4.0
OpenAPI's required-fields contract — that's the regression that v14
silently broke.

Pure stdlib — no pytest, no requests-mock. Run:
    cd examples/mffd-showcase
    python -m unittest tests.test_wire_shapes
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

# Load the script as a module without executing main() — PEP 723 header
# and CLI entry are below the class definitions we test.
_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
mffd_v15 = importlib.util.module_from_spec(spec)
sys.modules["mffd_v15"] = mffd_v15

# Suppress the script's `import sys; print(...)` ImportError handling.
spec.loader.exec_module(mffd_v15)

from tests.conftest_stubs import FakeResponse, StubSession, install_stub  # noqa: E402


def _new_client() -> "mffd_v15.ShepardClient":
    """Build a client with a stubbed Session."""
    client = mffd_v15.ShepardClient(
        base="https://dest.example.com",
        api_key="test-key",
        bearer_token="",
        ai_agent="claude-opus-4-7; actedOnBehalfOf=fkrebs@nucli.de",
    )
    install_stub(client, StubSession())
    return client


class TestBugLReadOnlyTsContainerType(unittest.TestCase):
    """Bug L: TimeseriesContainer.type is readOnly per v5.4.0 — don't send it."""

    def test_create_ts_container_does_not_send_type_field(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 42}))
        client.create_ts_container("test-container")
        last = client._s.last_call()
        self.assertEqual(last.method, "POST")
        self.assertIn("/timeseriesContainers", last.url)
        self.assertIsNotNone(last.json)
        self.assertNotIn("type", last.json,
            "Bug L: type field is readOnly, must NOT be sent on create")
        self.assertEqual(last.json.get("name"), "test-container")


class TestBugHCsvFormatEnum(unittest.TestCase):
    """Bug H: DLR v5.4.0 csv_format enum is {ROW, COLUMN} — WIDE is fork-only."""

    def test_export_ts_uses_column_format_not_wide(self):
        client = _new_client()
        # Provide a non-empty body so the Bug Q empty-body check passes.
        client._s.set_default(FakeResponse(200, raw_content=b"time,value\n0,1.0\n"))
        client.export_ts(48297, 12345, 99)
        last = client._s.last_call()
        self.assertEqual(last.method, "GET")
        self.assertIn("/export", last.url)
        self.assertIsNotNone(last.params)
        self.assertEqual(last.params.get("csv_format"), "COLUMN",
            "Bug H: csv_format must be COLUMN (DLR v5.4.0 enum), not WIDE")
        self.assertNotIn("WIDE", str(last.params),
            "Bug H: WIDE is a fork-only invention not in DLR v5.4.0 enum")


class TestBugQEmptyTsBody(unittest.TestCase):
    """Bug Q: empty 200 response from TS export indicates a placeholder, not data."""

    def test_export_ts_returns_none_on_empty_body(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, raw_content=b""))
        result = client.export_ts(48297, 12345, 99)
        self.assertIsNone(result,
            "Bug Q: empty body must be treated as missing payload, not success")


class TestBugKCurrentUserFallback(unittest.TestCase):
    """Bug K: /shepard/api/users/currentUser doesn't exist in DLR v5.4.0."""

    def test_warmup_does_not_fall_back_to_current_user(self):
        client = _new_client()
        # /v2/users/me responds 404 to simulate "non-fork" instance.
        client._s.set_default(FakeResponse(404, {"error": "not found"}))
        result = client.warmup()
        self.assertFalse(result,
            "Bug K: warmup must fail fast when /v2/users/me is unavailable")
        urls = [c.url for c in client._s.calls]
        self.assertFalse(any("/users/currentUser" in u for u in urls),
            "Bug K: must NOT fall back to /shepard/api/users/currentUser — endpoint never existed")


class TestBugRSourceWarmup(unittest.TestCase):
    """Bug R: cross-instance script needs a source-side warmup distinct from dest."""

    def test_warmup_source_probes_collections_endpoint(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, [], headers={"X-Total-Count": "12"}))
        result = client.warmup_source()
        self.assertTrue(result,
            "warmup_source must succeed against a vanilla v5.4.0 listings endpoint")
        last = client._s.last_call()
        self.assertEqual(last.method, "GET")
        self.assertIn("/shepard/api/collections", last.url,
            "warmup_source must use the upstream-compatible v1 listings endpoint")
        # Must NOT probe /v2/users/me — the source is strict v5.4.0.
        v2_urls = [c.url for c in client._s.calls if "/v2/" in c.url]
        self.assertEqual(v2_urls, [],
            "warmup_source must not touch any /v2/ endpoint")


class TestXAIAgentHeader(unittest.TestCase):
    """Per aidocs/93 §10: X-AI-Agent is set once on the session, sent on every call."""

    def test_x_ai_agent_header_is_set_on_session(self):
        client = _new_client()
        # The stub mirrors the real session headers via install_stub.
        # Real session attribute also carries it.
        # Verify via the underlying ShepardClient _s.headers state.
        # The stub copies these headers — assert the real client's _s.headers
        # has the value before install_stub overrode it.
        real_client = mffd_v15.ShepardClient(
            base="https://dest.example.com",
            api_key="test-key",
            bearer_token="",
            ai_agent="claude-opus-4-7; actedOnBehalfOf=fkrebs@nucli.de",
        )
        self.assertIn("X-AI-Agent", real_client._s.headers,
            "X-AI-Agent header must be set in ShepardClient __init__ when ai_agent is provided")
        self.assertIn("claude-opus-4-7", real_client._s.headers["X-AI-Agent"])
        self.assertIn("actedOnBehalfOf", real_client._s.headers["X-AI-Agent"])

    def test_x_ai_agent_header_absent_when_not_provided(self):
        client = mffd_v15.ShepardClient(
            base="https://dest.example.com",
            api_key="test-key",
            bearer_token="",
        )
        self.assertNotIn("X-AI-Agent", client._s.headers,
            "X-AI-Agent header must NOT be set when ai_agent is None — backward-compat")


if __name__ == "__main__":
    unittest.main()
