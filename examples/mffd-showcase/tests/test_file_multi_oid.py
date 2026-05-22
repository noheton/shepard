"""Bug D + F: multi-OID FileReference expansion (v15 wire-shape fix).

v14 silently dropped non-first payloads when a FileReference carried
multiple `fileOids[]`. v15 emits one FileRef per (ref_id, oid) pair,
and download_file_ref takes an explicit `oid` parameter so each
payload is addressed via its per-OID path.

Run: python -m unittest tests.test_file_multi_oid
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


def _new_client() -> "mffd_v15.ShepardClient":
    client = mffd_v15.ShepardClient(
        base="https://src.example.com",
        api_key="test-key",
        bearer_token="",
        ai_agent="claude-opus-4-7; actedOnBehalfOf=fkrebs@nucli.de",
    )
    install_stub(client, StubSession())
    return client


class TestBugDFMultiOidExpansion(unittest.TestCase):
    """Bug F: _fetch_file_refs must iterate fileOids[], not just the ref node."""

    def test_single_oid_produces_one_fileref(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, [
            {
                "id": 100,
                "name": "tapelaying-trace.csv",
                "fileSize": 1024,
                "fileOids": ["oid-a"],
            }
        ]))
        refs = client._fetch_file_refs(48297, 12345)
        self.assertEqual(len(refs), 1)
        self.assertEqual(refs[0].fref_id, 100)
        self.assertEqual(refs[0].oid, "oid-a")
        self.assertEqual(refs[0].name, "tapelaying-trace.csv")

    def test_multi_oid_expands_to_multiple_filerefs(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, [
            {
                "id": 200,
                "name": "afp-bundle",
                "fileSize": 4096,
                "fileOids": ["oid-1", "oid-2", "oid-3"],
            }
        ]))
        refs = client._fetch_file_refs(48297, 12345)
        self.assertEqual(len(refs), 3,
            "Bug F: 3 OIDs must produce 3 FileRef entries — not 1")
        # All share the same fref_id (they're members of the same FileReference)
        self.assertEqual({r.fref_id for r in refs}, {200})
        # But each carries its own OID
        self.assertEqual({r.oid for r in refs}, {"oid-1", "oid-2", "oid-3"})
        # Display names are disambiguated
        names = {r.name for r in refs}
        self.assertEqual(names, {"afp-bundle.0", "afp-bundle.1", "afp-bundle.2"})

    def test_legacy_ref_without_oids_still_yields_one_entry(self):
        """Backward-compat: pre-bundle FileReferences with no fileOids[]
        still produce a FileRef so v1-style /payload URL can be hit."""
        client = _new_client()
        client._s.set_default(FakeResponse(200, [
            {
                "id": 300,
                "name": "legacy.txt",
                "fileSize": 128,
                # no fileOids field at all (legacy refs)
            }
        ]))
        refs = client._fetch_file_refs(48297, 12345)
        self.assertEqual(len(refs), 1)
        self.assertEqual(refs[0].oid, "",
            "Legacy refs must yield empty oid so download_file_ref falls back to /payload")


class TestBugDPerOidPayloadPath(unittest.TestCase):
    """Bug D: download_file_ref must hit /payload/{oid} when oid is non-empty."""

    def test_download_uses_per_oid_path_when_oid_provided(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, raw_content=b"contents"))
        dest = Path("/tmp/v15-test-download")
        try:
            client.download_file_ref(
                coll_id=48297, do_id=12345, fref_id=200,
                dest=dest, oid="oid-2",
            )
        except Exception:
            pass  # tqdm or write may fail; we only check the URL was requested
        urls = [c.url for c in client._s.calls]
        self.assertTrue(
            any("/fileReferences/200/payload/oid-2" in u for u in urls),
            "Bug D: must address per-OID path /fileReferences/{id}/payload/{oid}; got: " + str(urls))

    def test_download_falls_back_to_bare_path_when_oid_empty(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, raw_content=b"contents"))
        dest = Path("/tmp/v15-test-download-legacy")
        try:
            client.download_file_ref(
                coll_id=48297, do_id=12345, fref_id=300,
                dest=dest, oid="",
            )
        except Exception:
            pass
        urls = [c.url for c in client._s.calls]
        # Bare /payload (no OID suffix) for legacy refs
        self.assertTrue(
            any(u.endswith("/fileReferences/300/payload") for u in urls),
            "Legacy ref with empty OID must use bare /payload endpoint; got: " + str(urls))


if __name__ == "__main__":
    unittest.main()
