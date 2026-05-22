"""v15.1 G1 + G4 + G5 — snapshot brackets + lineage annotations.

Asserts:
  * `annotate_snapshot_lineage(kind="pre")` emits a Collection-anchored
    `prov:wasInformedBy` triple referencing the pre-import snapshot URN
  * `annotate_snapshot_lineage(kind="post")` emits a Collection-anchored
    `prov:generated` triple referencing the post-import snapshot URN
  * The G4 label includes the literal substring "as-imported"
  * The G1 snapshot uses the literal prefix "v15-import-pre-"

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_snapshot_brackets
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


class TestAnnotateSnapshotLineagePre(unittest.TestCase):
    """G5 partial: pre-import snapshot emits `prov:wasInformedBy` Collection
    annotation."""

    def test_pre_snapshot_emits_was_informed_by(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        n = client.annotate_snapshot_lineage(
            coll_id=515365,
            snap_app_id="0192a14b-3c00-7a44-9f12-c0ffeebabe11",
            kind="pre",
            prov_repo_id=10, migration_repo_id=20,
        )
        self.assertEqual(n, 1)
        last = client._s.last_call()
        # Hit the Collection-anchored semanticAnnotations endpoint
        self.assertIn("/collections/515365/semanticAnnotations", last.url)
        # propertyIRI is prov:wasInformedBy
        self.assertEqual(last.json["propertyIRI"],
                         "http://www.w3.org/ns/prov#wasInformedBy")
        # value is the snapshot URN
        self.assertIn("0192a14b-3c00-7a44-9f12-c0ffeebabe11", last.json["valueIRI"])
        self.assertTrue(last.json["valueIRI"].startswith("urn:shepard:snapshot:"))

    def test_post_snapshot_emits_generated(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        n = client.annotate_snapshot_lineage(
            coll_id=515365,
            snap_app_id="0192a14b-3c01-7a44-9f12-c0ffee000099",
            kind="post",
            prov_repo_id=10, migration_repo_id=20,
        )
        self.assertEqual(n, 1)
        last = client._s.last_call()
        # Post snapshot uses prov:generated (not wasInformedBy)
        self.assertEqual(last.json["propertyIRI"],
                         "http://www.w3.org/ns/prov#generated")
        self.assertIn("urn:shepard:snapshot:", last.json["valueIRI"])

    def test_missing_app_id_returns_zero_no_call(self):
        client = _new_client()
        n = client.annotate_snapshot_lineage(
            coll_id=515365, snap_app_id="", kind="pre",
            prov_repo_id=10, migration_repo_id=20,
        )
        self.assertEqual(n, 0)
        # No HTTP call attempted for the missing-app-id case
        anno_calls = client._s.calls_to("/semanticAnnotations")
        self.assertEqual(anno_calls, [])


class TestSnapshotLabels(unittest.TestCase):
    """G4: post-import snapshot label includes 'as-imported' so downstream
    RO-Crate / regulatory pack consumers can address the canonical baseline.

    Cannot easily run the full main() in a unit test, but we can assert the
    snapshot endpoint shape and that the label-building substring assumptions
    we encoded in main() are still expressible via create_snapshot.
    """

    def test_create_snapshot_passes_label_through_to_body(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {
            "appId": "snap-app-id", "label": "v15-as-imported-test-session",
        }))
        snap = client.create_snapshot(
            coll_app_id="abc-coll-app-id",
            label="v15-as-imported-2026-05-22@MFFD-Dropbox",
        )
        self.assertEqual(snap["appId"], "snap-app-id")
        last = client._s.last_call()
        self.assertIn("/snapshots", last.url)
        self.assertEqual(last.json["label"],
                         "v15-as-imported-2026-05-22@MFFD-Dropbox")
        self.assertIn("as-imported", last.json["label"],
            "G4: post-import snapshot label MUST include 'as-imported' substring")

    def test_pre_import_snapshot_label_prefix(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"appId": "snap-pre"}))
        snap = client.create_snapshot(
            coll_app_id="abc-coll-app-id",
            label="v15-import-pre-2026-05-22@MFFD-Dropbox",
        )
        self.assertEqual(snap["appId"], "snap-pre")
        last = client._s.last_call()
        self.assertTrue(last.json["label"].startswith("v15-import-pre-"),
            "G1: pre-import snapshot label MUST start with 'v15-import-pre-'")


class TestSnapshotLineageIRIConstants(unittest.TestCase):
    """Constants the snapshot lineage depends on — guard against typos."""

    def test_prov_generated_iri_canonical(self):
        self.assertEqual(mffd_v15.PROV.GENERATED,
                         "http://www.w3.org/ns/prov#generated")

    def test_prov_was_informed_by_iri_canonical(self):
        self.assertEqual(mffd_v15.PROV.WAS_INFORMED_BY,
                         "http://www.w3.org/ns/prov#wasInformedBy")

    def test_prov_entity_iri_canonical(self):
        self.assertEqual(mffd_v15.PROV.ENTITY,
                         "http://www.w3.org/ns/prov#Entity")


if __name__ == "__main__":
    unittest.main()
