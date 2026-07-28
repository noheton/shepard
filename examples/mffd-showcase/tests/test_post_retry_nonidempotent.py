"""MFFD-POST-RETRY-NONIDEMPOTENT — a read-timeout must not re-send a POST.

The 2026-07-21 tapelaying tail produced **12 empty duplicate**
`Bridgewelding-*` DataObjects: `_request_with_retry` caught
`requests.exceptions.Timeout` and blindly re-POSTed the create for the whole
900 s reconnect window. A read-timeout means the request *was* sent and the
server may have applied it — only the response was lost — so re-sending a
non-idempotent method duplicates the entity. The backend has no
Idempotency-Key support to lean on.

Verified here:
  1. POST + read-timeout  → returns None immediately, sent exactly ONCE
     (caller's find-or-create then re-resolves);
  2. GET  + read-timeout  → still retried (idempotent, safe to re-send);
  3. POST + ConnectionError → still retried (request most likely never
     reached the app, so it cannot have been applied).

Run: python -m unittest tests.test_post_retry_nonidempotent
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

import requests

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
if "mffd_v15" not in sys.modules:
    _spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
    mffd_v15 = importlib.util.module_from_spec(_spec)
    sys.modules["mffd_v15"] = mffd_v15
    _spec.loader.exec_module(mffd_v15)
else:
    mffd_v15 = sys.modules["mffd_v15"]


class _CountingSession:
    """Session stub that raises a chosen exception a fixed number of times."""

    def __init__(self, exc, fail_times=99, ok_status=201):
        self._exc = exc
        self._fail_times = fail_times
        self._ok_status = ok_status
        self.calls = 0

    def request(self, method, url, timeout=None, **kwargs):
        self.calls += 1
        if self.calls <= self._fail_times:
            raise self._exc
        resp = requests.Response()
        resp.status_code = self._ok_status
        return resp


def _client_with(session):
    """A Cube3Client-ish instance with the session swapped for our stub.

    Built via __new__ so we don't run the real __init__ (which wants a live
    host + token); only the attributes _request_with_retry touches are set.
    """
    cls = None
    for name in dir(mffd_v15):
        obj = getattr(mffd_v15, name)
        if isinstance(obj, type) and hasattr(obj, "_request_with_retry"):
            cls = obj
            break
    assert cls is not None, "no class exposing _request_with_retry found"
    c = cls.__new__(cls)
    c._s = session
    c._telemetry = None
    c._refresh_session = lambda: None
    return c


class PostRetryNonIdempotentTest(unittest.TestCase):
    def test_post_timeout_is_not_resent(self):
        """A POST that read-times-out is sent exactly once, then bails to caller."""
        sess = _CountingSession(requests.exceptions.ReadTimeout("read timed out"))
        client = _client_with(sess)

        result = client._request_with_retry(
            "POST", "https://dest/v2/collections/x/data-objects", deadline_s=30.0
        )

        self.assertIsNone(result, "POST timeout must return None, not a response")
        self.assertEqual(
            sess.calls, 1, "POST must NOT be re-sent after a read-timeout"
        )

    def test_get_timeout_is_still_retried(self):
        """GET is idempotent — the reconnect loop keeps retrying it."""
        sess = _CountingSession(
            requests.exceptions.ReadTimeout("read timed out"),
            fail_times=2,
            ok_status=200,
        )
        client = _client_with(sess)

        result = client._request_with_retry(
            "GET", "https://dest/v2/collections", deadline_s=60.0
        )

        self.assertIsNotNone(result)
        self.assertEqual(result.status_code, 200)
        self.assertGreater(sess.calls, 1, "GET should have been retried")

    def test_post_connection_error_is_still_retried(self):
        """A ConnectionError means the request likely never landed — safe to retry."""
        sess = _CountingSession(
            requests.exceptions.ConnectionError("conn refused"),
            fail_times=2,
            ok_status=201,
        )
        client = _client_with(sess)

        result = client._request_with_retry(
            "POST", "https://dest/v2/collections/x/data-objects", deadline_s=60.0
        )

        self.assertIsNotNone(result)
        self.assertEqual(result.status_code, 201)
        self.assertGreater(
            sess.calls, 1, "POST should still retry a pre-send ConnectionError"
        )


if __name__ == "__main__":
    unittest.main()
