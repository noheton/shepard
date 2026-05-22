"""Bugs A, G (timeseries ordering+shape) + B, C, D, E (structured data).

Run: python -m unittest tests.test_ts_and_structured
"""

from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
if "mffd_v15" not in sys.modules:
    spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
    mffd_v15 = importlib.util.module_from_spec(spec)
    sys.modules["mffd_v15"] = mffd_v15
    spec.loader.exec_module(mffd_v15)
else:
    mffd_v15 = sys.modules["mffd_v15"]

from tests.conftest_stubs import FakeResponse, StubSession, install_stub  # noqa: E402


def _new_client():
    client = mffd_v15.ShepardClient(
        base="https://dest.example.com",
        api_key="test-key",
        bearer_token="",
        ai_agent="claude-opus-4-7; actedOnBehalfOf=fkrebs@nucli.de",
    )
    install_stub(client, StubSession())
    return client


class TestBugAGTimeseries(unittest.TestCase):
    """Bug A: TimeseriesReference body must carry non-empty timeseries[] (minItems:1).
       Bug G: linkage must happen AFTER container has channels."""

    def test_link_ts_refuses_with_no_channels(self):
        client = _new_client()
        result = client.link_ts_to_do(
            coll_id=515365, do_id=999, container_id=42,
            name="Trace-001", timeseries=[],
        )
        self.assertIsNone(result,
            "Bug G: must refuse to link when timeseries[] is empty")
        # No POST should hit the server at all
        post_calls = [c for c in client._s.calls if c.method == "POST"]
        self.assertEqual(post_calls, [],
            "Refusal must be local — no POST to the server")

    def test_link_ts_includes_required_fields(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 5000}))
        channels = [{
            "measurement": "afp",
            "device": "robot01",
            "location": "augsburg",
            "symbolicName": "tcp",
            "field": "temperature",
        }]
        result = client.link_ts_to_do(
            coll_id=515365, do_id=999, container_id=42,
            name="Trace-001", timeseries=channels,
            start_ms=1716000000000, end_ms=1716000300000,
        )
        self.assertEqual(result, 5000)
        last = client._s.last_call()
        self.assertEqual(last.method, "POST")
        # Required fields per TimeseriesReference schema
        for k in ("name", "timeseriesContainerId", "start", "end", "timeseries"):
            self.assertIn(k, last.json,
                f"Bug A: TimeseriesReference body must include {k!r}")
        self.assertEqual(last.json["timeseries"], channels)
        self.assertEqual(last.json["start"], 1716000000000)
        self.assertEqual(last.json["end"], 1716000300000)

    def test_link_ts_uses_wide_bracket_when_end_zero(self):
        """end_ms=0 means 'unknown' — fall back to a wide bracket so the POST
        validates without forcing the caller to compute min/max."""
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 5001}))
        channels = [{"measurement": "m", "device": "d", "location": "l",
                     "symbolicName": "s", "field": "f"}]
        client.link_ts_to_do(
            coll_id=515365, do_id=999, container_id=42,
            name="X", timeseries=channels, start_ms=0, end_ms=0,
        )
        last = client._s.last_call()
        self.assertGreater(last.json["end"], 0,
            "When end_ms=0, must fall back to a positive wide bracket")
        self.assertGreater(last.json["end"], last.json["start"])


class TestListTsChannels(unittest.TestCase):
    """list_ts_channels surfaces the 5-tuple identity for downstream linkage."""

    def test_list_ts_channels_extracts_5_tuple(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, [
            {
                "measurement": "afp", "device": "robot01", "location": "augsburg",
                "symbolicName": "tcp", "field": "temperature",
                "id": 1, "extraField": "ignored",
            },
            {
                "measurement": "afp", "device": "robot01", "location": "augsburg",
                "symbolicName": "tcp", "field": "force",
                "id": 2,
            },
        ]))
        channels = client.list_ts_channels(container_id=42)
        self.assertEqual(len(channels), 2)
        # 5-tuple keys only — no leakage of OGM internals
        self.assertEqual(set(channels[0].keys()),
                         {"measurement", "device", "location", "symbolicName", "field"})

    def test_list_ts_channels_skips_malformed_rows(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, [
            {"measurement": "m", "device": "d", "location": "l",
             "symbolicName": "s", "field": "f"},
            {"measurement": "m"},  # missing required keys
            "bogus-non-dict",
        ]))
        channels = client.list_ts_channels(container_id=42)
        self.assertEqual(len(channels), 1,
            "Malformed rows must be skipped, not crash the import")


class TestBugBStructuredPostNotPut(unittest.TestCase):
    """Bug B: structured payload upload uses POST (not PUT)."""

    def test_upload_structured_payload_uses_post(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"oid": "sd-oid-7"}))
        oid = client.upload_structured_payload(
            container_id=42, payload={"key": "value"}, name="run-007"
        )
        self.assertEqual(oid, "sd-oid-7")
        last = client._s.last_call()
        self.assertEqual(last.method, "POST",
            "Bug B: POST (not PUT) on /structuredDataContainers/{id}/payload")
        self.assertIn("/payload", last.url)


class TestBugDStructuredPayloadWrapper(unittest.TestCase):
    """Bug D: structured payload body is `{structuredData:{name}, payload:string}`."""

    def test_upload_structured_payload_emits_wrapper_with_json_string(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"oid": "sd-oid-1"}))
        inner = {"frame": 12, "weld": "OK"}
        client.upload_structured_payload(container_id=42, payload=inner, name="frame-12")
        last = client._s.last_call()
        # Required envelope keys
        self.assertIn("structuredData", last.json)
        self.assertEqual(last.json["structuredData"].get("name"), "frame-12")
        self.assertIn("payload", last.json)
        # payload field MUST be a string (JSON-encoded), not the dict itself
        self.assertIsInstance(last.json["payload"], str,
            "Bug D: payload field MUST be a JSON-encoded STRING (minLength:2)")
        # And it must decode back to the original
        decoded = json.loads(last.json["payload"])
        self.assertEqual(decoded, inner)


class TestBugCStructuredOidsRequired(unittest.TestCase):
    """Bug C: StructuredDataReference REQUIRES non-empty structuredDataOids[]."""

    def test_link_structured_refuses_empty_oids(self):
        client = _new_client()
        result = client.link_structured_to_do(
            coll_id=515365, do_id=999, container_id=42, name="x", oids=[]
        )
        self.assertFalse(result,
            "Bug C: must refuse to link when oids is empty")

    def test_link_structured_includes_oids_in_body(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 6000}))
        client.link_structured_to_do(
            coll_id=515365, do_id=999, container_id=42, name="report",
            oids=["sd-oid-1", "sd-oid-2"],
        )
        last = client._s.last_call()
        self.assertEqual(last.json.get("structuredDataOids"), ["sd-oid-1", "sd-oid-2"],
            "Bug C: oids must land in structuredDataOids[] field")
        self.assertEqual(last.json.get("structuredDataContainerId"), 42)
        self.assertEqual(last.json.get("name"), "report")


class TestBugEStructuredWrapperDecode(unittest.TestCase):
    """Bug E: download_structured must decode the inner payload string."""

    def test_download_structured_decodes_inner_json_string(self):
        client = _new_client()
        # Source returns the v5.4.0-shape wrapper array
        client._s.set_default(FakeResponse(200, [
            {
                "structuredData": {"name": "run-001"},
                "payload": '{"sensor":"vibration","peak_g":12.4}',
            }
        ]))
        result = client.download_structured(48297, 12345, 99)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["name"], "run-001")
        # The inner JSON string MUST be decoded to a dict, not kept as a string.
        self.assertEqual(result[0]["payload"], {"sensor": "vibration", "peak_g": 12.4},
            "Bug E: inner payload must be JSON-decoded for re-upload")

    def test_download_structured_handles_multi_wrapper_array(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, [
            {"structuredData": {"name": "a"}, "payload": '{"x":1}'},
            {"structuredData": {"name": "b"}, "payload": '[1,2,3]'},
        ]))
        result = client.download_structured(48297, 12345, 99)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]["payload"], {"x": 1})
        self.assertEqual(result[1]["payload"], [1, 2, 3])

    def test_download_structured_round_trip_no_corruption(self):
        """v14's bug: re-uploading the wrapper-as-payload caused
        double-encoding. v15 returns the decoded inner so re-upload
        round-trips cleanly."""
        client = _new_client()
        original = {"frame_id": 5, "force_drop_pct": 28.7, "outcome": "FAIL"}
        client._s.enqueue("GET", "/payload",
            FakeResponse(200, [{
                "structuredData": {"name": "ncr-001"},
                "payload": json.dumps(original),
            }]))
        client._s.enqueue("POST", "/structuredDataContainers/42/payload",
            FakeResponse(201, {"oid": "round-trip-oid"}))
        # download
        downloaded = client.download_structured(48297, 12345, 99)
        self.assertEqual(downloaded[0]["payload"], original)
        # re-upload
        client.upload_structured_payload(
            container_id=42, payload=downloaded[0]["payload"], name=downloaded[0]["name"]
        )
        # The body sent on re-upload must, when decoded, equal the original
        upload_call = [c for c in client._s.calls if c.method == "POST"][-1]
        re_encoded = upload_call.json["payload"]
        self.assertEqual(json.loads(re_encoded), original,
            "Round-trip must preserve original payload exactly — no double encoding")


if __name__ == "__main__":
    unittest.main()
