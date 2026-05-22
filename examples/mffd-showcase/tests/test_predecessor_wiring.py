"""Bug I: predecessor wiring via DataObject.predecessorIds[] body field.

v14 PUT'd /collections/{c}/dataObjects/{d}/predecessors/{predId} which
DOES NOT EXIST in DLR v5.4.0. v15 sets predecessorIds in the DataObject
body at POST time (or via PUT for cross-step wiring).

Run: python -m unittest tests.test_predecessor_wiring
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


class TestBugIPredecessorInBody(unittest.TestCase):
    """Bug I: predecessorIds must land in the DataObject POST body, not a phantom path."""

    def test_create_with_single_predecessor_sets_id_in_body(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 7777, "name": "x"}))
        do = client.create_data_object(
            coll_id=515365, name="Track-001", predecessor_id=42,
        )
        self.assertEqual(do["id"], 7777)
        # Exactly one POST — no phantom PUT to /predecessors/{id}
        post_calls = [c for c in client._s.calls if c.method == "POST"]
        put_calls = [c for c in client._s.calls if c.method == "PUT"]
        self.assertEqual(len(post_calls), 1,
            "Single round-trip: one POST creates the DO with predecessor in body")
        self.assertEqual(put_calls, [],
            "Bug I: must NOT call the phantom /predecessors/{id} PUT endpoint")
        self.assertEqual(post_calls[0].json.get("predecessorIds"), [42],
            "Bug I: predecessorIds must be in the create body")

    def test_create_with_multi_predecessors(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 8000}))
        client.create_data_object(
            coll_id=515365, name="Merge-Point",
            predecessor_ids=[100, 200, 300],
        )
        last = client._s.last_call()
        self.assertEqual(last.json.get("predecessorIds"), [100, 200, 300])

    def test_create_without_predecessor_omits_field(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 9000}))
        client.create_data_object(coll_id=515365, name="Orphan")
        last = client._s.last_call()
        # When no predecessor, the field is absent — not an empty list (clean POST)
        self.assertNotIn("predecessorIds", last.json,
            "When no predecessor, predecessorIds must not appear in body")

    def test_no_phantom_predecessors_path_in_any_request(self):
        """Catch-all: the phantom /predecessors/{id} path must never be hit."""
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.create_data_object(coll_id=515365, name="x", predecessor_id=99)
        client.create_data_object(coll_id=515365, name="y", predecessor_ids=[99, 100])
        all_urls = [c.url for c in client._s.calls]
        phantom = [u for u in all_urls if "/predecessors/" in u]
        self.assertEqual(phantom, [],
            f"Bug I: /predecessors/{'{id}'} path must never appear in any request; got: {phantom}")


class TestSetPredecessorsPostHoc(unittest.TestCase):
    """set_predecessors: PUT on the DataObject body for cross-step wiring."""

    def test_set_predecessors_strips_read_only_fields(self):
        client = _new_client()
        # GET returns the full DO including readOnly fields
        client._s.enqueue("GET", "/dataObjects/777", FakeResponse(200, {
            "id": 777, "name": "x", "collectionId": 515365,
            "createdAt": "2026-05-22", "createdBy": "user-1",
            "updatedAt": "2026-05-22", "updatedBy": "user-1",
            "predecessorIds": [],
            "successorIds": [1, 2], "childrenIds": [], "parentId": None,
            "referenceIds": [10, 11, 12], "incomingIds": [],
            "attributes": {"key": "value"},
        }))
        client._s.enqueue("PUT", "/dataObjects/777", FakeResponse(200, {"id": 777}))

        result = client.set_predecessors(coll_id=515365, do_id=777, pred_ids=[5, 6])
        self.assertTrue(result)

        put_calls = [c for c in client._s.calls if c.method == "PUT"]
        self.assertEqual(len(put_calls), 1)
        put_body = put_calls[0].json
        # predecessorIds correctly set
        self.assertEqual(put_body.get("predecessorIds"), [5, 6])
        # readOnly fields stripped — server rejects them per v5.4.0 schema
        for k in ("id", "createdAt", "createdBy", "updatedAt", "updatedBy",
                  "collectionId", "referenceIds", "successorIds",
                  "childrenIds", "parentId", "incomingIds"):
            self.assertNotIn(k, put_body,
                f"set_predecessors must strip readOnly field {k!r} before PUT")
        # User-set fields preserved
        self.assertEqual(put_body.get("name"), "x")
        self.assertEqual(put_body.get("attributes"), {"key": "value"})


if __name__ == "__main__":
    unittest.main()
