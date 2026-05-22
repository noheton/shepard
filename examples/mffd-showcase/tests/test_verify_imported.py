"""v15.1 — independent verifier (Reluctant Senior minimum).

The producer (run_source_mode) counts what it *intended* to upload.
verify_imported walks the dest collection independently and counts what
*actually landed* — separate code paths, so a v14-style silent corruption
(Bug E) cannot pass an under-counted batch as success.

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_verify_imported
"""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
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


class TestVerifyImportedReportShape(unittest.TestCase):
    """Asserts the shape of the verifier report — not the values (since the
    stub returns deterministic but synthetic data)."""

    def _build_walker_responses(self, stub: StubSession,
                                base="https://dest.example.com",
                                coll_id=515365):
        """Set up StubSession with responses for the verifier's walk."""
        # Page 0 — three DOs by pattern (one per bucket)
        page0 = FakeResponse(
            200,
            [
                {"id": 100, "name": "Tapelaying-2026-05-22"},
                {"id": 101, "name": "Bridgewelding-2026-05-22"},
                {"id": 102, "name": "WikiDump-skeleton"},
            ],
            headers={"X-Total-Pages": "1"},
        )
        # We want page 0 to be paginated-complete; the verifier will then
        # call /fileReferences, /timeseriesReferences, /structuredDataReferences
        # for each DO. Use set_default for the no-data ones, enqueue specifics.
        # NOTE: requests passes `params` separately; the URL itself has no query
        # string. Match on the bare path. Order matters because the verifier
        # also calls `/dataObjects/{id}` (single-DO GET) for the DAG walk —
        # we put the listings response first so it's drained before the
        # per-DO walks land on default.
        stub.enqueue("GET", f"/collections/{coll_id}/dataObjects", page0)
        # File refs: DO 100 has 2 file refs (one with size, one without)
        stub.enqueue(
            "GET", "/dataObjects/100/fileReferences",
            FakeResponse(200, [
                {"id": 1, "name": "a.bin", "fileSize": 1024},
                {"id": 2, "name": "b.bin", "fileOids": ["oid-zzz"]},
            ]),
        )
        # TS refs: DO 100 has 1 ref with channels
        stub.enqueue(
            "GET", "/dataObjects/100/timeseriesReferences",
            FakeResponse(200, [
                {"id": 3, "name": "ts-ref", "timeseries": [{"measurement": "x"}]},
            ]),
        )
        # Structured refs: DO 101 has 1
        stub.enqueue(
            "GET", "/dataObjects/101/structuredDataReferences",
            FakeResponse(200, [{"id": 4, "name": "sd-ref"}]),
        )

    def test_report_has_expected_keys(self):
        client = _new_client()
        self._build_walker_responses(client._s)
        # Default response for any sub-ref endpoint we didn't enqueue:
        client._s.set_default(FakeResponse(200, []))

        report = mffd_v15.verify_imported(client, coll_id=515365,
                                          report_path=None)
        for key in (
            "session", "verified_at", "collection_id", "dest_url",
            "dos_total", "dos_by_pattern",
            "file_refs_total", "file_refs_nonzero",
            "ts_refs_total", "ts_refs_with_channels",
            "structured_refs_total", "dag_spot_checks", "warnings",
        ):
            self.assertIn(key, report, f"verifier report must include {key!r}")

    def test_pattern_buckets_correct(self):
        client = _new_client()
        self._build_walker_responses(client._s)
        client._s.set_default(FakeResponse(200, []))

        report = mffd_v15.verify_imported(client, coll_id=515365)
        self.assertEqual(report["dos_total"], 3)
        self.assertEqual(report["dos_by_pattern"]["tapelaying"], 1)
        self.assertEqual(report["dos_by_pattern"]["bridgewelding"], 1)
        self.assertEqual(report["dos_by_pattern"]["skeleton"], 1)

    def test_file_refs_counted_with_nonzero_size(self):
        client = _new_client()
        self._build_walker_responses(client._s)
        client._s.set_default(FakeResponse(200, []))

        report = mffd_v15.verify_imported(client, coll_id=515365)
        # DO 100 had 2 file refs: one with fileSize=1024 (counted as nonzero),
        # one with fileOids (also counted as nonzero per heuristic)
        self.assertGreaterEqual(report["file_refs_nonzero"], 2)

    def test_writes_report_to_file_when_path_given(self):
        client = _new_client()
        self._build_walker_responses(client._s)
        client._s.set_default(FakeResponse(200, []))

        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "mffd-verify-test.json"
            report = mffd_v15.verify_imported(client, coll_id=515365,
                                              report_path=out)
            self.assertTrue(out.exists())
            persisted = json.loads(out.read_text())
            self.assertEqual(persisted["collection_id"], 515365)
            self.assertEqual(persisted["dos_total"], report["dos_total"])


if __name__ == "__main__":
    unittest.main()
