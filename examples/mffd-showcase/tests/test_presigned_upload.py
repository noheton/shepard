"""Presigned-URL upload flow + Garage pre-flight probe (v15 §6 + §9 + §13).

Run: python -m unittest tests.test_presigned_upload
"""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

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


class TestGaragePreflight(unittest.TestCase):
    """garage_preflight returns (True, '') only when Garage is active."""

    def test_preflight_passes_on_200(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"uploadUrl": "https://garage/.../x", "oid": "xx"}))
        ok, reason = client.garage_preflight("019e4e56-ca63-76f3-9bf0-6681f7fe6d56")
        self.assertTrue(ok)
        self.assertEqual(reason, "")

    def test_preflight_fails_on_404(self):
        client = _new_client()
        client._s.set_default(FakeResponse(404, {}))
        # FakeResponse.text defaults to "" so the 'gridfs' check is false; expect 404 branch.
        ok, reason = client.garage_preflight("0xdeadbeef")
        self.assertFalse(ok)
        self.assertIn("404", reason)
        self.assertIn("FS1b", reason)  # Hints at the missing endpoint

    def test_preflight_fails_with_runbook_on_503_gridfs(self):
        client = _new_client()
        r = FakeResponse(503, {})
        r.text = "GridFS provider is still active; switch to s3 first"
        client._s.set_default(r)
        ok, reason = client.garage_preflight("0xdeadbeef")
        self.assertFalse(ok)
        self.assertIn("Garage", reason)
        self.assertIn("runbook", reason.lower())
        self.assertIn("layout assign", reason)  # The shell commands operators need

    def test_exit_code_constant_is_4(self):
        # The shell wrapper must distinguish "needs Garage" from generic failure.
        self.assertEqual(mffd_v15.ShepardClient.EXIT_GARAGE_INACTIVE, 4)


class TestPresignedFlow(unittest.TestCase):
    """3-step presigned upload: upload-url → PUT → commit → linkFileViaOid."""

    def test_upload_url_request_returns_url_and_oid(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {
            "uploadUrl": "https://garage.example.com/shepard-files/abc?sig=xxx",
            "oid": "ffaa1234",
            "expiresAt": "2026-05-22T22:00:00Z",
        }))
        result = client.upload_url_request("0xappid", "test.csv")
        self.assertIsNotNone(result)
        upload_url, oid = result
        self.assertEqual(oid, "ffaa1234")
        self.assertTrue(upload_url.startswith("https://garage."))
        # Verify the POST shape (Step 1 body)
        last = client._s.last_call()
        self.assertEqual(last.method, "POST")
        self.assertIn("/v2/file-containers/0xappid/upload-url", last.url)
        self.assertEqual(last.json, {"fileName": "test.csv"})

    def test_upload_url_request_returns_none_on_malformed_response(self):
        client = _new_client()
        client._s.set_default(FakeResponse(200, {"unexpected": "no uploadUrl here"}))
        result = client.upload_url_request("0xappid", "test.csv")
        self.assertIsNone(result)

    def test_upload_url_commit_step3_body_shape(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 42, "oid": "ff", "fileName": "t.csv"}))
        result = client.upload_url_commit("0xappid", "ff", "t.csv")
        self.assertIsNotNone(result)
        last = client._s.last_call()
        self.assertEqual(last.method, "POST")
        self.assertIn("/upload-url/commit", last.url)
        self.assertEqual(last.json, {"oid": "ff", "fileName": "t.csv"})

    def test_link_file_via_oid_creates_fileReference_with_fileOids(self):
        """Step 4: the v5-compat FileReference must carry fileOids:[oid] +
        fileContainerId, matching Bug F's wire shape."""
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 5555}))
        result = client.link_file_via_oid(
            coll_id=515365, do_id=999, file_container_id=88,
            name="afp-bundle", oid="ff-1",
        )
        self.assertEqual(result, 5555)
        last = client._s.last_call()
        self.assertEqual(last.json.get("name"), "afp-bundle")
        self.assertEqual(last.json.get("fileOids"), ["ff-1"])
        self.assertEqual(last.json.get("fileContainerId"), 88)

    def test_presigned_upload_full_3_step_sequence(self):
        """End-to-end: presigned_upload chains 3 calls in the right order."""
        client = _new_client()
        # Step 1 — backend returns the presigned URL
        client._s.enqueue("POST", "/upload-url",
            FakeResponse(201, {"uploadUrl": "https://garage/put-here", "oid": "oid-7"}))
        # Step 3 — commit succeeds
        client._s.enqueue("POST", "/upload-url/commit",
            FakeResponse(201, {"id": 99, "oid": "oid-7"}))

        # Step 2 is a bare requests.put — mock at the module level
        with tempfile.NamedTemporaryFile(delete=False) as tmp:
            tmp.write(b"some test bytes\n" * 100)
            tmp_path = Path(tmp.name)

        try:
            with patch.object(mffd_v15.requests, "put") as mock_put:
                mock_put.return_value = MagicMock(status_code=200, text="")
                oid = client.presigned_upload("0xappid", tmp_path)

            self.assertEqual(oid, "oid-7")

            # Verify the call sequence
            posts = [c for c in client._s.calls if c.method == "POST"]
            self.assertEqual(len(posts), 2, "Step 1 (upload-url) + Step 3 (commit) = 2 POSTs")
            self.assertIn("/upload-url", posts[0].url)
            self.assertNotIn("/commit", posts[0].url)
            self.assertIn("/upload-url/commit", posts[1].url)

            # Verify the direct-to-Garage PUT happened with the file bytes
            mock_put.assert_called_once()
            args, kwargs = mock_put.call_args
            self.assertEqual(args[0], "https://garage/put-here")
            self.assertIn(b"some test bytes", kwargs.get("data") or args[1])
        finally:
            tmp_path.unlink()


if __name__ == "__main__":
    unittest.main()
