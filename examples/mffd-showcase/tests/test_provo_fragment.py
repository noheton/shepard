"""PROV-O batch writeback (v15 §10) — typed-triple SemanticAnnotations.

Asserts:
  - Predicates class strings match V61's MERGEd Resource URIs (byte-equal)
  - emit_prov_for_migration emits the right 2 or 3 annotations per DO
  - emit_batch_summary emits 8 annotations (one per V61-registered predicate)
  - ETA publisher PATCHes the right attribute keys on the dest collection

Run: python -m unittest tests.test_provo_fragment
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


# The 10 V61-registered URIs — must match exactly what V61__v15_prov_predicates.cypher MERGEs.
V61_REGISTERED_IRIS = {
    "http://semantics.dlr.de/shepard-upper#targetCollection",
    "http://semantics.dlr.de/shepard-upper#filesUploaded",
    "http://semantics.dlr.de/shepard-upper#timeseriesImported",
    "http://semantics.dlr.de/shepard-upper#structuredPayloads",
    "http://semantics.dlr.de/shepard-upper#batchSequence",
    "http://semantics.dlr.de/shepard-upper#throughputBytesPerSec",
    "http://semantics.dlr.de/shepard-upper#retryCount",
    "http://semantics.dlr.de/shepard-upper#sourceInstance",
    "http://semantics.dlr.de/shepard-upper#role-executor",
    "http://semantics.dlr.de/shepard-upper#role-operator",
}


def _new_client():
    client = mffd_v15.ShepardClient(
        base="https://dest.example.com",
        api_key="test-key",
        bearer_token="",
        ai_agent="claude-opus-4-7; actedOnBehalfOf=fkrebs@nucli.de",
    )
    install_stub(client, StubSession())
    return client


class TestPredicateConstantsMatchV61(unittest.TestCase):
    """The Predicates class must mirror V61's MERGEd URIs byte-for-byte.

    A typo here = a silent 404 from the SemanticAnnotation endpoint
    (n10s returns empty for unknown IRIs); the migrated import would
    appear to succeed but no provenance would be visible to SPARQL queries.
    """

    def test_all_eight_predicates_present(self):
        actual = {
            mffd_v15.Predicates.TARGET_COLLECTION,
            mffd_v15.Predicates.FILES_UPLOADED,
            mffd_v15.Predicates.TIMESERIES_IMPORTED,
            mffd_v15.Predicates.STRUCTURED_PAYLOADS,
            mffd_v15.Predicates.BATCH_SEQUENCE,
            mffd_v15.Predicates.THROUGHPUT_BYTES_PER_SEC,
            mffd_v15.Predicates.RETRY_COUNT,
            mffd_v15.Predicates.SOURCE_INSTANCE,
        }
        # Subset check — all 8 must be in V61's registered set.
        missing = actual - V61_REGISTERED_IRIS
        self.assertEqual(missing, set(),
            f"Predicates not registered by V61 — typo will silently 404: {missing}")

    def test_both_roles_present(self):
        actual = {
            mffd_v15.Predicates.ROLE_EXECUTOR,
            mffd_v15.Predicates.ROLE_OPERATOR,
        }
        missing = actual - V61_REGISTERED_IRIS
        self.assertEqual(missing, set(),
            f"Role individuals not registered by V61: {missing}")

    def test_namespace_constant_canonical(self):
        self.assertEqual(mffd_v15.Predicates.NS,
                         "http://semantics.dlr.de/shepard-upper#")


class TestEmitProvForMigration(unittest.TestCase):
    """One DO migration → wasDerivedFrom + wasGeneratedBy [+ wasInformedBy]."""

    def test_emit_without_predecessor_creates_2_annotations(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        n = client.emit_prov_for_migration(
            coll_id=515365, dest_do_id=999,
            src_coll_id=48297, src_do_id=12345,
            session_id="2026-05-22-Q1",
            prov_repo_id=1, migration_repo_id=2,
        )
        self.assertEqual(n, 2,
            "Without predecessor: wasDerivedFrom + wasGeneratedBy = 2 annotations")
        posts = [c for c in client._s.calls if c.method == "POST"]
        self.assertEqual(len(posts), 2)
        predicates = [c.json["propertyIRI"] for c in posts]
        self.assertIn("http://www.w3.org/ns/prov#wasDerivedFrom", predicates)
        self.assertIn("http://www.w3.org/ns/prov#wasGeneratedBy", predicates)

    def test_emit_with_predecessor_creates_3_annotations(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        n = client.emit_prov_for_migration(
            coll_id=515365, dest_do_id=999,
            src_coll_id=48297, src_do_id=12345,
            session_id="2026-05-22-Q1",
            prov_repo_id=1, migration_repo_id=2,
            pred_do_id=888,
        )
        self.assertEqual(n, 3)
        predicates = [c.json["propertyIRI"] for c in client._s.calls if c.method == "POST"]
        self.assertIn("http://www.w3.org/ns/prov#wasInformedBy", predicates)

    def test_emit_uses_urn_shape_for_source_uri(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.emit_prov_for_migration(
            coll_id=515365, dest_do_id=999,
            src_coll_id=48297, src_do_id=12345,
            session_id="2026-05-22-Q1",
            prov_repo_id=1, migration_repo_id=2,
        )
        # Find the wasDerivedFrom annotation
        derived = next(c for c in client._s.calls
                       if c.method == "POST" and "wasDerivedFrom" in c.json.get("propertyIRI", ""))
        self.assertEqual(derived.json["valueIRI"],
                         "urn:shepard:src:48297:dataObject:12345",
                         "Source URI must use urn:shepard:src:<coll>:dataObject:<id> shape")


class TestEmitBatchSummary(unittest.TestCase):
    """A batch's summary = 8 annotations using V61-registered predicates."""

    def test_emit_batch_summary_produces_8_annotations(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        n = client.emit_batch_summary(
            coll_id=515365, anchor_do_id=999,
            prov_repo_id=1, migration_repo_id=2,
            batch_seq=47,
            files_uploaded=93,
            timeseries_imported=14,
            structured_payloads=7,
            throughput_bps=4.21e7,
            retry_count=0,
            source_instance="nuclide-edge-dropbox",
            target_collection_app_id="019e4e56-ca63-76f3-9bf0-6681f7fe6d56",
        )
        self.assertEqual(n, 8,
            "Per aidocs/93 §10: one annotation per V61-registered predicate")
        posts = [c for c in client._s.calls if c.method == "POST"]
        self.assertEqual(len(posts), 8)

    def test_emit_batch_summary_uses_only_v61_registered_predicates(self):
        """Each annotation's propertyIRI MUST be in the V61-registered set."""
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.emit_batch_summary(
            coll_id=515365, anchor_do_id=999,
            prov_repo_id=1, migration_repo_id=2,
            batch_seq=1, files_uploaded=0, timeseries_imported=0,
            structured_payloads=0, throughput_bps=0.0, retry_count=0,
            source_instance="test", target_collection_app_id="x",
        )
        emitted_predicates = {c.json["propertyIRI"]
                              for c in client._s.calls if c.method == "POST"}
        # All emitted predicates must be in V61's set; if any isn't, the
        # SemanticAnnotation POST would silently 404 in production.
        unregistered = emitted_predicates - V61_REGISTERED_IRIS
        self.assertEqual(unregistered, set(),
            f"emit_batch_summary uses unregistered predicates: {unregistered}")

    def test_emit_batch_summary_carries_numeric_values(self):
        """The shepard:filesUploaded etc. predicates carry numericValue —
        consumers (Trace3D, ETA dashboards) can read the scalar directly."""
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.emit_batch_summary(
            coll_id=515365, anchor_do_id=999,
            prov_repo_id=1, migration_repo_id=2,
            batch_seq=47, files_uploaded=93, timeseries_imported=14,
            structured_payloads=7, throughput_bps=4.21e7, retry_count=0,
            source_instance="test", target_collection_app_id="x",
        )
        # Find the filesUploaded annotation; assert numericValue is set.
        files_call = next(c for c in client._s.calls
                          if c.json and c.json.get("propertyIRI", "").endswith("filesUploaded"))
        self.assertEqual(files_call.json.get("numericValue"), 93.0)


class TestPatchCollectionAttributes(unittest.TestCase):
    """ETA publisher PATCHes the dest collection with import progress."""

    def test_patch_attributes_emits_correct_body(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, {"id": 515365}))
        ok = client.patch_collection_attributes(515365, {
            "import_eta": "2026-05-22T18:00:00Z",
            "import_progress": "1500/8383",
            "import_throughput_dos_per_min": "42.5",
            "import_last_heartbeat": "2026-05-22T15:30:00Z",
        })
        self.assertTrue(ok)
        # Either PATCH or PUT — both are acceptable per spec. The first call
        # is the PATCH attempt; fall back to PUT happens only when PATCH fails.
        first = client._s.calls[0]
        self.assertIn(first.method, ("PATCH", "PUT"))
        self.assertIn("/collections/515365", first.url)
        # Attribute keys all present in the body
        attrs = first.json.get("attributes") if first.json else None
        self.assertIsNotNone(attrs)
        for k in ("import_eta", "import_progress",
                  "import_throughput_dos_per_min", "import_last_heartbeat"):
            self.assertIn(k, attrs, f"ETA attribute {k!r} must be in PATCH body")


if __name__ == "__main__":
    unittest.main()
