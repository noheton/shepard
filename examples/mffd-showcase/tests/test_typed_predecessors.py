"""v15.1 G7 — typed predecessor edges via `prov:wasInformedBy` annotations.

The MFFD DAG (predecessorIds[]) lives in Neo4j only after v14/v15's wire-shape
fix (Bug I). v15.1 mirrors each predecessor edge into the SHACL graph as
`<do> prov:wasInformedBy do:<predAppId>` so:

  * ODIX can walk the lineage via SPARQL (not Cypher)
  * RO-Crate export includes the lineage
  * The substrate-split principle (feedback_shacl_single_source_of_truth.md)
    stays intact — domain lineage is in the SHACL graph

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_typed_predecessors
"""

from __future__ import annotations

import importlib.util
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


class TestAnnotateTypedPredecessor(unittest.TestCase):

    def test_emits_was_informed_by_annotation(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        ok = client.annotate_typed_predecessor(
            coll_id=515365, do_id=7777,
            pred_app_id="0192a14b-3c01-7a44-9f12-c0ffee000010",
            prov_repo_id=10, migration_repo_id=20,
        )
        self.assertTrue(ok)
        last = client._s.last_call()
        # Hit the DO-anchored semanticAnnotations endpoint
        self.assertIn("/dataObjects/7777/semanticAnnotations", last.url)
        # propertyIRI is prov:wasInformedBy (the right kind for a normal
        # process-step predecessor — rework would be prov:wasRevisionOf)
        self.assertEqual(last.json["propertyIRI"],
                         "http://www.w3.org/ns/prov#wasInformedBy")
        # valueIRI is the predecessor DO URN
        self.assertEqual(
            last.json["valueIRI"],
            "urn:shepard:dataObject:0192a14b-3c01-7a44-9f12-c0ffee000010",
        )

    def test_missing_pred_app_id_returns_false_no_call(self):
        client = _new_client()
        ok = client.annotate_typed_predecessor(
            coll_id=515365, do_id=7777,
            pred_app_id="",
            prov_repo_id=10, migration_repo_id=20,
        )
        self.assertFalse(ok)
        # No HTTP call attempted
        self.assertEqual(client._s.calls, [])

    def test_multiple_predecessors_yield_multiple_calls(self):
        """A merge DO with N predecessors must produce N wasInformedBy
        annotations (one per pred). Caller iterates."""
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        preds = [
            "0192a14b-3c01-7a44-9f12-c0ffee000001",
            "0192a14b-3c01-7a44-9f12-c0ffee000002",
            "0192a14b-3c01-7a44-9f12-c0ffee000003",
        ]
        for pred in preds:
            client.annotate_typed_predecessor(
                coll_id=515365, do_id=8000,
                pred_app_id=pred,
                prov_repo_id=10, migration_repo_id=20,
            )
        anno_calls = client._s.calls_to(
            "/dataObjects/8000/semanticAnnotations"
        )
        self.assertEqual(len(anno_calls), 3)
        # All three predecessor IRIs present
        emitted_iris = {c.json["valueIRI"] for c in anno_calls}
        expected_iris = {f"urn:shepard:dataObject:{p}" for p in preds}
        self.assertEqual(emitted_iris, expected_iris)


if __name__ == "__main__":
    unittest.main()
