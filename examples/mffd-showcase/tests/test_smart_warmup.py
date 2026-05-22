"""Tests for v15.2 smart warmup (IMPORT-W1 / W2 / W3).

Pure stdlib — no pytest, no requests-mock. Run:
    cd examples/mffd-showcase
    python -m unittest tests.test_smart_warmup

Conventions match `test_wire_shapes.py`: load the script as a module
without executing `main()`, replace `ShepardClient._s` with a `StubSession`
so we can drive the responses deterministically. The smart-warmup
orchestrator pokes the session via `client._s.get/post/delete` directly
(bypassing `_get`/`_post` which would collapse non-2xx to None) — that's
exactly the StubSession contract.
"""

from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path
from typing import Any


# ── Load both the script and the warmup module as fresh modules ──────────

_HERE = Path(__file__).resolve().parent
_SCRIPT_DIR = _HERE.parent / "scripts"

# 1. Load _smart_warmup first so the script can find it.
_warmup_spec = importlib.util.spec_from_file_location(
    "_smart_warmup", _SCRIPT_DIR / "_smart_warmup.py"
)
sw = importlib.util.module_from_spec(_warmup_spec)
sys.modules["_smart_warmup"] = sw
_warmup_spec.loader.exec_module(sw)

# 2. Then the main script (so the `from _smart_warmup import ...` succeeds).
sys.path.insert(0, str(_SCRIPT_DIR))
_script_spec = importlib.util.spec_from_file_location(
    "mffd_v15", _SCRIPT_DIR / "mffd-import-v15.py"
)
mffd_v15 = importlib.util.module_from_spec(_script_spec)
sys.modules["mffd_v15"] = mffd_v15
_script_spec.loader.exec_module(mffd_v15)

from tests.conftest_stubs import FakeResponse, StubSession, install_stub  # noqa: E402


# ── Minimal OpenAPI spec for shape-comparator tests ──────────────────────


def _minimal_spec() -> dict:
    """A tiny spec covering the shapes we exercise."""
    return {
        "openapi": "3.0.3",
        "paths": {
            "/users/{username}": {
                "get": {
                    "responses": {
                        "200": {
                            "content": {
                                "application/json": {
                                    "schema": {"$ref": "#/components/schemas/User"}
                                }
                            }
                        }
                    }
                }
            },
            "/collections": {
                "get": {
                    "responses": {
                        "200": {
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "type": "array",
                                        "items": {
                                            "$ref": "#/components/schemas/Collection"
                                        },
                                    }
                                }
                            }
                        }
                    }
                }
            },
            "/collections/{collectionId}/dataObjects": {
                "post": {
                    "responses": {
                        "201": {
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "$ref": "#/components/schemas/DataObject"
                                    }
                                }
                            }
                        }
                    }
                }
            },
        },
        "components": {
            "schemas": {
                "User": {
                    "required": ["username"],
                    "type": "object",
                    "properties": {
                        "username": {"type": "string"},
                        "email": {"type": "string", "nullable": True},
                    },
                },
                "Collection": {
                    "required": ["id", "name"],
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer"},
                        "name": {"type": "string"},
                        "description": {"type": "string", "nullable": True},
                    },
                },
                "DataObject": {
                    "required": ["id", "name"],
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer"},
                        "name": {"type": "string"},
                    },
                },
            }
        },
    }


def _new_client(base: str = "https://dest.example.com") -> Any:
    client = mffd_v15.ShepardClient(
        base=base,
        api_key="literal-source-key-no-redaction",
        bearer_token="",
        ai_agent=None,
    )
    install_stub(client, StubSession())
    return client


# ── WarmupReport ─────────────────────────────────────────────────────────


class TestWarmupReportToText(unittest.TestCase):

    def test_success_renders_status_ok(self):
        r = sw.WarmupReport(session_id="abc123")
        r.record_probe("GET", "https://x/y", "200", "200 ok", True, spec_match=True)
        r.ok()
        text = r.to_text()
        self.assertIn("status        : OK", text)
        self.assertIn("exit_code     : 0", text)
        self.assertIn("session_id    : abc123", text)
        self.assertIn("[OK ]", text)
        self.assertIn("[spec✓]", text)

    def test_failure_renders_all_diagnostic_keys(self):
        r = sw.WarmupReport(session_id="xyz")
        r.fail(
            verb="POST",
            url="https://x/y",
            expected="201",
            got="500: boom",
            exit_code=sw.EXIT_GENERIC,
            where_to_look="dest logs",
            what_to_try="curl -v",
            extra={"trace_id": "T123"},
        )
        text = r.to_text()
        for marker in [
            "verb          : POST",
            "url           : https://x/y",
            "expected      : 201",
            "got           : 500: boom",
            "where_to_look : dest logs",
            "what_to_try   : curl -v",
            "trace_id",
        ]:
            self.assertIn(marker, text)

    def test_to_json_roundtrips(self):
        r = sw.WarmupReport(session_id="rt")
        r.record_probe("GET", "https://x/y", "200", "200 ok", True)
        r.ok()
        blob = r.to_json()
        parsed = json.loads(blob)
        self.assertEqual(parsed["session_id"], "rt")
        self.assertTrue(parsed["success"])
        self.assertEqual(parsed["exit_code"], 0)
        self.assertEqual(len(parsed["probes"]), 1)

    def test_no_redactions_in_text_output(self):
        """`feedback_no_redactions.md`: error output includes the URL verbatim."""
        r = sw.WarmupReport()
        url = "https://x/y?token=literal-not-redacted"
        r.fail("GET", url, "200", "401", sw.EXIT_AUTH)
        text = r.to_text()
        self.assertIn("literal-not-redacted", text)
        self.assertNotIn("<redacted>", text)
        self.assertNotIn("<placeholder>", text)


# ── compare_against_openapi (IMPORT-W3) ─────────────────────────────────


class TestOpenApiShapeComparator(unittest.TestCase):

    def test_matching_shape_returns_ok(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec, "/users/{username}", "GET", {"username": "flo"}, 200
        )
        self.assertTrue(diff.ok)
        self.assertFalse(diff.skipped)
        self.assertEqual(diff.missing_required_fields, [])

    def test_missing_required_field_flagged(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec, "/users/{username}", "GET", {"email": "f@x"}, 200
        )
        self.assertFalse(diff.ok)
        self.assertIn("username", diff.missing_required_fields)

    def test_type_mismatch_flagged(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec, "/users/{username}", "GET", {"username": 42}, 200
        )
        self.assertFalse(diff.ok)
        self.assertTrue(any("username" in m for m in diff.type_mismatches))

    def test_ref_resolution_walks_components(self):
        """The User schema is a $ref — the comparator must resolve it."""
        spec = _minimal_spec()
        # If $ref resolution were broken, missing `username` would not be detected.
        diff = sw.compare_against_openapi(
            spec, "/users/{username}", "GET", {}, 200
        )
        self.assertFalse(diff.ok)
        self.assertIn("username", diff.missing_required_fields)

    def test_array_compares_first_item(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec,
            "/collections",
            "GET",
            [{"id": 1, "name": "ok"}],
            200,
        )
        self.assertTrue(diff.ok)

        diff_bad = sw.compare_against_openapi(
            spec, "/collections", "GET", [{"id": 1}], 200
        )
        self.assertFalse(diff_bad.ok)
        self.assertTrue(any("name" in f for f in diff_bad.missing_required_fields))

    def test_skipped_when_path_missing(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec, "/no/such/path", "GET", {}, 200
        )
        self.assertTrue(diff.ok)
        self.assertTrue(diff.skipped)

    def test_skipped_when_method_missing(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec, "/users/{username}", "DELETE", {}, 200
        )
        self.assertTrue(diff.ok)
        self.assertTrue(diff.skipped)

    def test_nullable_field_accepts_null(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec, "/users/{username}", "GET",
            {"username": "flo", "email": None}, 200,
        )
        self.assertTrue(diff.ok)

    def test_concrete_url_normalizes_to_template(self):
        spec = _minimal_spec()
        diff = sw.compare_against_openapi(
            spec,
            None,
            "GET",
            {"username": "x"},
            200,
            concrete_url="https://h/shepard/api/users/flo",
        )
        self.assertTrue(diff.ok)
        self.assertFalse(diff.skipped)

    def test_real_v54_spec_covers_collections_get(self):
        """Sanity: against the in-tree v5.4.0 spec, /collections GET matches."""
        spec_path = (
            Path(__file__).resolve().parents[3]
            / "backend/src/test/resources/fixtures/v5/openapi-5.4.0.json"
        )
        if not spec_path.exists():
            self.skipTest("v5.4.0 spec not present (worktree?)")
        spec = json.loads(spec_path.read_text())
        # The minimum required fields per the actual spec are id, name, ...
        body = [
            {
                "id": 1,
                "createdAt": "2024-01-01T00:00:00Z",
                "createdBy": "x",
                "updatedAt": None,
                "updatedBy": None,
                "name": "n",
                "dataObjectIds": [],
                "incomingIds": [],
            }
        ]
        diff = sw.compare_against_openapi(spec, "/collections", "GET", body, 200)
        self.assertTrue(
            diff.ok,
            msg=(
                f"shape mismatch against real spec: "
                f"missing={diff.missing_required_fields} "
                f"types={diff.type_mismatches}"
            ),
        )


# ── SmartWarmup orchestrator ─────────────────────────────────────────────


class TestSmartWarmupAuthFailure(unittest.TestCase):

    def test_auth_failure_emits_exit_code_2(self):
        dest = _new_client()
        dest._s.set_default(FakeResponse(401, {"error": "expired"}))
        w = sw.SmartWarmup(
            source_client=None,
            dest_client=dest,
            session_id="auth-fail",
            openapi_spec_path="/no/such/spec.json",  # forces spec-skip
        )
        with self.assertRaises(sw.WarmupAborted) as ctx:
            w.run()
        self.assertEqual(ctx.exception.exit_code, sw.EXIT_AUTH)
        self.assertIn("HTTP 401", ctx.exception.report.got)


class TestSmartWarmupWireShapeFailure(unittest.TestCase):

    def test_wire_shape_drift_emits_exit_code_6(self):
        """Successful auth, then /collections returns a non-list body."""
        dest = _new_client()
        # Order of calls: auth (/v2/users/me), read (/collections).
        dest._s.enqueue(
            "GET", "/v2/users/me",
            FakeResponse(200, {"sub": "flo", "username": "flo"}),
        )
        # Return something that violates the in-tree spec
        # (collections is an array of objects, not a bare string).
        dest._s.enqueue(
            "GET", "/shepard/api/collections",
            FakeResponse(200, [{"id": 1}]),  # missing required `name`, etc.
        )
        # Load the real v5.4.0 spec so we actually exercise drift detection.
        spec_path = (
            Path(__file__).resolve().parents[3]
            / "backend/src/test/resources/fixtures/v5/openapi-5.4.0.json"
        )
        if not spec_path.exists():
            self.skipTest("v5.4.0 spec not present")
        w = sw.SmartWarmup(
            source_client=None,
            dest_client=dest,
            session_id="drift",
            openapi_spec_path=str(spec_path),
        )
        with self.assertRaises(sw.WarmupAborted) as ctx:
            w.run()
        self.assertEqual(ctx.exception.exit_code, sw.EXIT_WIRE_SHAPE_DRIFT)
        # Diagnostic must mention what was missing.
        text = ctx.exception.report.to_text()
        self.assertIn("shape per OpenAPI", text)


class TestSmartWarmupSuccessPath(unittest.TestCase):

    def test_success_returns_ok_report(self):
        dest = _new_client()
        # Auth probe → ok
        dest._s.enqueue("GET", "/v2/users/me", FakeResponse(200, {"sub": "flo"}))
        # Read probe → ok (spec compare allowed to skip; use a valid empty list)
        dest._s.enqueue(
            "GET", "/shepard/api/collections", FakeResponse(200, [])
        )
        w = sw.SmartWarmup(
            source_client=None,
            dest_client=dest,
            session_id="ok",
            write_to_dest=False,
            probe_garage=False,
            openapi_spec_path="/no/such/spec.json",  # spec-skip everywhere
        )
        report = w.run()
        self.assertTrue(report.success)
        self.assertEqual(report.exit_code, sw.EXIT_OK)
        # At minimum: 2 successful probes (dest auth + dest read).
        self.assertGreaterEqual(len([p for p in report.probes if p["ok"]]), 2)


class TestSmartWarmupCleanup(unittest.TestCase):

    def test_failure_after_source_write_still_deletes_probe_do(self):
        """If write probe succeeds but the next step fails, the throwaway DO
        must still be DELETEd (defensive cleanup)."""
        dest = _new_client("https://dest.example.com")
        source = _new_client("https://source.example.com")
        # Dest auth ok.
        dest._s.enqueue("GET", "/v2/users/me", FakeResponse(200, {"sub": "flo"}))
        # Source auth ok.
        source._s.enqueue(
            "GET", "/shepard/api/users/flo",
            FakeResponse(200, {"username": "flo"}),
        )
        # Dest read ok.
        dest._s.enqueue(
            "GET", "/shepard/api/collections", FakeResponse(200, [])
        )
        # Source read ok.
        source._s.enqueue(
            "GET", "/shepard/api/collections", FakeResponse(200, [])
        )
        # Source write — succeeds with id=999.
        source._s.enqueue(
            "POST",
            "/shepard/api/collections/48297/dataObjects",
            FakeResponse(201, {"id": 999, "name": "_warmup_probe"}),
        )
        # Source DELETE on cleanup → 204.
        source._s.enqueue(
            "DELETE",
            "/shepard/api/collections/48297/dataObjects/999",
            FakeResponse(204, {}),
        )
        # Dest write fails with auth error (forces exit 7 and cleanup).
        dest._s.enqueue(
            "POST",
            "/shepard/api/collections/123/dataObjects",
            FakeResponse(403, {"error": "no write"}),
        )
        # Default for any other call — keeps test deterministic.
        dest._s.set_default(FakeResponse(200, []))

        w = sw.SmartWarmup(
            source_client=source,
            dest_client=dest,
            session_id="cleanup-test",
            source_collection_id=48297,
            dest_collection_id=123,
            write_to_source=True,
            write_to_dest=True,
            probe_garage=False,
            openapi_spec_path="/no/such/spec.json",
        )
        with self.assertRaises(sw.WarmupAborted) as ctx:
            w.run()
        self.assertEqual(ctx.exception.exit_code, sw.EXIT_WRITE_PERMISSION_DENIED)
        # Verify the throwaway source DO got DELETEd despite the abort.
        delete_calls = source._s.calls_to("/dataObjects/999")
        delete_methods = [c.method for c in delete_calls]
        self.assertIn("DELETE", delete_methods)


class TestSmartWarmupSourceUnreachable(unittest.TestCase):

    def test_dest_network_failure_emits_exit_code_3(self):
        class _BoomSession:
            headers: dict = {}

            def get(self, *_a, **_kw):
                raise ConnectionError("dns failure")

            def post(self, *_a, **_kw):
                raise ConnectionError("dns failure")

            def delete(self, *_a, **_kw):
                raise ConnectionError("dns failure")

        dest = _new_client()
        dest._s = _BoomSession()
        w = sw.SmartWarmup(
            source_client=None,
            dest_client=dest,
            session_id="unreach",
            openapi_spec_path="/no/such/spec.json",
        )
        with self.assertRaises(sw.WarmupAborted) as ctx:
            w.run()
        self.assertEqual(
            ctx.exception.exit_code, sw.EXIT_SOURCE_UNREACHABLE
        )


class TestSmartWarmupGarageProbe(unittest.TestCase):

    def test_garage_failure_emits_exit_code_4(self):
        dest = _new_client()
        dest._s.enqueue("GET", "/v2/users/me", FakeResponse(200, {"sub": "f"}))
        dest._s.enqueue(
            "GET", "/shepard/api/collections", FakeResponse(200, [])
        )

        # Inject a garage_preflight that returns (False, "gridfs"). This
        # mimics the real client surface used by the orchestrator.
        def fake_preflight(_app_id):
            return False, "backend still on GridFS — PV1a migration pending"

        dest.garage_preflight = fake_preflight  # type: ignore[attr-defined]

        w = sw.SmartWarmup(
            source_client=None,
            dest_client=dest,
            session_id="garage",
            dest_collection_app_id="dest-coll-app-id",
            write_to_dest=False,
            probe_garage=True,
            openapi_spec_path="/no/such/spec.json",
        )
        with self.assertRaises(sw.WarmupAborted) as ctx:
            w.run()
        self.assertEqual(ctx.exception.exit_code, sw.EXIT_GARAGE_DOWN)
        self.assertIn("gridfs", ctx.exception.report.got.lower())


class TestSmartWarmupExitCodeConstants(unittest.TestCase):
    """Per `feedback_warmup_fail_fast_diagnostic.md`: each failure class
    gets a distinct integer."""

    def test_each_exit_code_is_unique(self):
        codes = [
            sw.EXIT_OK,
            sw.EXIT_GENERIC,
            sw.EXIT_AUTH,
            sw.EXIT_SOURCE_UNREACHABLE,
            sw.EXIT_GARAGE_DOWN,
            sw.EXIT_OPERATOR_INTERRUPT,
            sw.EXIT_WIRE_SHAPE_DRIFT,
            sw.EXIT_WRITE_PERMISSION_DENIED,
        ]
        self.assertEqual(len(codes), len(set(codes)))

    def test_exit_codes_match_documented_contract(self):
        """The exit codes must match the contract documented in
        aidocs/16 IMPORT-W2 and feedback_warmup_fail_fast_diagnostic.md."""
        self.assertEqual(sw.EXIT_AUTH, 2)
        self.assertEqual(sw.EXIT_SOURCE_UNREACHABLE, 3)
        self.assertEqual(sw.EXIT_GARAGE_DOWN, 4)
        self.assertEqual(sw.EXIT_OPERATOR_INTERRUPT, 5)
        self.assertEqual(sw.EXIT_WIRE_SHAPE_DRIFT, 6)
        self.assertEqual(sw.EXIT_WRITE_PERMISSION_DENIED, 7)


if __name__ == "__main__":
    unittest.main()
