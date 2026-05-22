"""Shared test stubs for v15 import-script wire-shape tests.

Pure stdlib: no requests-mock, no pytest. The tests assert that the
JSON bodies our client emits match the DLR v5.4.0 OpenAPI's required
fields — that's pure dict introspection, no HTTP layer needed.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any
from unittest.mock import MagicMock


@dataclass
class RecordedCall:
    """One captured HTTP call against the stub session."""
    method: str
    url: str
    json: Any | None = None
    files: Any | None = None
    data: Any | None = None
    params: Any | None = None
    headers: dict[str, str] = field(default_factory=dict)
    timeout: Any | None = None


class FakeResponse:
    """Minimal stand-in for requests.Response — only the attributes our
    client touches."""

    def __init__(self, status_code: int = 200, body: Any = None,
                 raw_content: bytes | None = None,
                 headers: dict[str, str] | None = None) -> None:
        self.status_code = status_code
        self.ok = 200 <= status_code < 300
        self._body = body if body is not None else {}
        self.content = raw_content if raw_content is not None else b""
        self.text = ""
        self.headers = headers or {}
        self.url = ""

    def json(self) -> Any:
        return self._body

    def iter_content(self, chunk_size: int = 65536):
        yield self.content


class StubSession:
    """Stand-in for requests.Session — records calls, replays canned responses.

    Use:
      s = StubSession()
      s.enqueue("POST", "https://dest/.../timeseriesContainers",
                FakeResponse(201, {"id": 99}))
      s.post("https://dest/.../timeseriesContainers", json={"name": "x"})
      assert s.calls[-1].json == {"name": "x"}
    """

    def __init__(self) -> None:
        self.calls: list[RecordedCall] = []
        self.headers: dict[str, str] = {}
        self._queue: dict[tuple[str, str], list[FakeResponse]] = {}
        self._default: FakeResponse = FakeResponse(200, {"id": 1, "oid": "fake-oid"})

    # --- API mirroring requests.Session ---

    def get(self, url: str, *, params=None, timeout=None, stream=False, **kw):
        return self._record("GET", url, params=params, timeout=timeout)

    def post(self, url: str, *, json=None, files=None, data=None, params=None,
             timeout=None, **kw):
        return self._record("POST", url, json=json, files=files, data=data,
                            params=params, timeout=timeout)

    def put(self, url: str, *, json=None, data=None, timeout=None, **kw):
        return self._record("PUT", url, json=json, data=data, timeout=timeout)

    def patch(self, url: str, *, json=None, data=None, timeout=None, **kw):
        return self._record("PATCH", url, json=json, data=data, timeout=timeout)

    def delete(self, url: str, **kw):
        return self._record("DELETE", url, timeout=kw.get("timeout"))

    def request(self, method: str, url: str, **kw):
        return self._record(method, url, **kw)

    # --- Helpers ---

    def enqueue(self, method: str, url_substring: str, response: FakeResponse) -> None:
        """Queue a canned response for any future call matching method + URL substring."""
        self._queue.setdefault((method, url_substring), []).append(response)

    def set_default(self, response: FakeResponse) -> None:
        self._default = response

    def _record(self, method: str, url: str, **kw) -> FakeResponse:
        call = RecordedCall(
            method=method, url=url,
            json=kw.get("json"),
            files=kw.get("files"),
            data=kw.get("data"),
            params=kw.get("params"),
            headers=dict(self.headers),
            timeout=kw.get("timeout"),
        )
        self.calls.append(call)
        # Match canned response by URL substring (matches /shepard/api/x/y).
        for (m, sub), responses in self._queue.items():
            if m == method and sub in url and responses:
                return responses.pop(0)
        return self._default

    # --- Convenience filter ---

    def calls_to(self, *url_substrings: str) -> list[RecordedCall]:
        return [c for c in self.calls
                if all(sub in c.url for sub in url_substrings)]

    def last_call(self) -> RecordedCall | None:
        return self.calls[-1] if self.calls else None


def install_stub(client, stub: StubSession) -> None:
    """Replace the client's _s (requests.Session) with the stub."""
    client._s = stub
    # Replicate the headers the real Session would have set in __init__.
    # The client's _post/_get/_get_raw call self._s.<method>; whatever
    # auth headers we have on the real session must mirror onto the stub.
    real_headers = getattr(client, "_real_headers", None) or {}
    stub.headers.update(real_headers)
