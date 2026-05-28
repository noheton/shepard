# /// script
# requires-python = ">=3.11"
# dependencies = ["pytest", "requests"]
# ///
"""Tests for Q7 / task #145 — BUG-FILEREF-TRUNCATION (2026-05-28).

Two distinct bugs were collapsed under "Q7 fileRef parser bug":

  1. **Forgotten-oid call site** (the primary defect). The worker fan-out in
     `mffd-import-v15.py` called `download_file_ref(...)` with 5 positional
     args, dropping the `oid` keyword. With `oid=""` the function hit the
     bare `.../fileReferences/{id}/payload` endpoint, which per v5.4.0
     `FileReferenceRest.getFiles` returns **a JSON array of `ShepardFile`
     metadata**, not the file bytes. The importer wrote that JSON to disk
     and uploaded it to dest as if it were the file payload.

  2. **Silent stream truncation** (the latent defect). `iter_content()` on
     a `stream=True` response can exit without raising when the upstream
     connection closes mid-transfer — `requests` does not verify
     `Content-Length` (psf/requests#2275, #4227, #6512). Even after fixing
     bug 1, a connection drop during the byte stream would leave a
     truncated file on disk and `download_file_ref` returned True.

This test exercises the post-fix shape:
  - Truncated stream → unlink + return False + emit `file_truncated`
  - Full stream         → return True, bytes on disk match expected
  - Missing Content-Length → return True, emit `file_unverified` WARN
  - Forgotten-oid regression → confirm the worker now passes oid through

Run:
    cd examples/mffd-showcase/scripts
    uv run --with pytest --with requests pytest test_fileref_truncation.py -v
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

# Load the sibling script as a module so we can patch its symbols directly.
_SCRIPT = Path(__file__).parent / "mffd-import-v15.py"
_spec = importlib.util.spec_from_file_location("mffd_import_v15", _SCRIPT)
assert _spec and _spec.loader, f"cannot load {_SCRIPT}"
mod = importlib.util.module_from_spec(_spec)
sys.modules.setdefault("mffd_import_v15", mod)
_spec.loader.exec_module(mod)

ShepardClient = mod.ShepardClient


# ── Helpers ─────────────────────────────────────────────────────────────────


def _make_streaming_response(
    chunks: list[bytes],
    content_length: int | None,
    status: int = 200,
) -> MagicMock:
    """Build a fake requests.Response that behaves like stream=True.

    `chunks` is the byte sequence iter_content() will yield. `content_length`
    is what the server advertised in its header — set to None to simulate a
    chunked response with no Content-Length.
    """
    r = MagicMock()
    r.status_code = status
    r.ok = 200 <= status < 300
    r.headers = {}
    if content_length is not None:
        r.headers["Content-Length"] = str(content_length)
    # iter_content() must be a callable that returns a fresh iterator each
    # call — MagicMock's default would return the same MagicMock instance.
    r.iter_content = MagicMock(side_effect=lambda chunk_size=65536: iter(chunks))
    return r


def _make_client_with_session(get_response: MagicMock) -> ShepardClient:
    """Build a ShepardClient whose internal Session returns `get_response`
    from any GET call. Bypasses `_request_with_retry` because the production
    code path under test (`download_file_ref`) calls `self._s.get()`
    directly.
    """
    client = ShepardClient("https://src.test", "stub-jwt", "")
    fake_session = MagicMock()
    fake_session.get = MagicMock(return_value=get_response)
    client._s = fake_session  # type: ignore[assignment]
    return client


# ── Truncation guard ────────────────────────────────────────────────────────


def test_download_truncated_stream_returns_false_and_unlinks(tmp_path: Path) -> None:
    """Server advertises 1000 bytes, iter_content yields 500. The fix must
    catch the mismatch, unlink the partial file, and return False."""
    dest = tmp_path / "broken.bin"
    chunks = [b"A" * 500]  # short by 500
    resp = _make_streaming_response(chunks, content_length=1000)
    client = _make_client_with_session(resp)

    ok = client.download_file_ref(
        coll_id=1, do_id=2, fref_id=3, dest=dest, oid="abc",
    )

    assert ok is False
    assert not dest.exists(), (
        "truncated file must be unlinked so it isn't mistaken for a real payload"
    )


def test_download_full_stream_returns_true(tmp_path: Path) -> None:
    """Bytes-on-disk == Content-Length → success path."""
    dest = tmp_path / "full.bin"
    payload = b"X" * 1000
    chunks = [payload[i:i + 250] for i in range(0, len(payload), 250)]
    resp = _make_streaming_response(chunks, content_length=1000)
    client = _make_client_with_session(resp)

    ok = client.download_file_ref(
        coll_id=1, do_id=2, fref_id=3, dest=dest, oid="abc",
    )

    assert ok is True
    assert dest.exists()
    assert dest.stat().st_size == 1000
    assert dest.read_bytes() == payload


def test_download_missing_content_length_accepts(tmp_path: Path) -> None:
    """Chunked transfer encoding (Confluence / proxy paths) omits
    Content-Length. We can't verify, so accept-and-WARN — failing all
    chunked responses would re-create the completeness problem in reverse.
    """
    dest = tmp_path / "chunked.bin"
    chunks = [b"Y" * 600]
    resp = _make_streaming_response(chunks, content_length=None)
    client = _make_client_with_session(resp)

    ok = client.download_file_ref(
        coll_id=1, do_id=2, fref_id=3, dest=dest, oid="abc",
    )

    assert ok is True
    assert dest.exists()
    assert dest.stat().st_size == 600


def test_download_zero_byte_stream_against_advertised_size(tmp_path: Path) -> None:
    """The pathological MFFD shape: server says 4242 bytes, iter_content
    yields nothing (connection reset before first chunk). Must be caught.
    """
    dest = tmp_path / "zero.bin"
    resp = _make_streaming_response(chunks=[], content_length=4242)
    client = _make_client_with_session(resp)

    ok = client.download_file_ref(
        coll_id=1, do_id=2, fref_id=3, dest=dest, oid="abc",
    )

    assert ok is False
    assert not dest.exists()


def test_download_zero_zero_is_treated_as_success(tmp_path: Path) -> None:
    """An empty file with Content-Length: 0 is a legitimate edge case (a
    legacy 0-byte upload). Don't false-fail it — 0 == 0."""
    dest = tmp_path / "empty.bin"
    resp = _make_streaming_response(chunks=[], content_length=0)
    client = _make_client_with_session(resp)

    ok = client.download_file_ref(
        coll_id=1, do_id=2, fref_id=3, dest=dest, oid="abc",
    )

    assert ok is True
    assert dest.exists()
    assert dest.stat().st_size == 0


def test_download_http_error_returns_false_and_no_partial(tmp_path: Path) -> None:
    """A 404 from the source must not leave a partial file on disk."""
    dest = tmp_path / "missing.bin"
    resp = _make_streaming_response(chunks=[], content_length=None, status=404)
    client = _make_client_with_session(resp)

    ok = client.download_file_ref(
        coll_id=1, do_id=2, fref_id=3, dest=dest, oid="abc",
    )

    assert ok is False
    assert not dest.exists()


# ── Forgotten-oid regression ────────────────────────────────────────────────


def test_download_file_ref_with_oid_hits_oid_path(tmp_path: Path) -> None:
    """When `oid` is provided, the URL must address the specific payload
    via `/payload/{oid}` — NOT the bare `/payload` (which returns metadata,
    not bytes, per v5.4.0 FileReferenceRest.getFiles).
    """
    dest = tmp_path / "with_oid.bin"
    resp = _make_streaming_response([b"Z" * 100], content_length=100)
    client = _make_client_with_session(resp)

    client.download_file_ref(
        coll_id=42, do_id=99, fref_id=7, dest=dest, oid="deadbeef-cafe",
    )

    # The Session.get mock captured the URL — assert /payload/deadbeef-cafe
    # was requested, NOT the bare /payload metadata endpoint.
    called_url = client._s.get.call_args[0][0]  # type: ignore[attr-defined]
    assert called_url.endswith(
        "/collections/42/dataObjects/99/fileReferences/7/payload/deadbeef-cafe"
    ), f"expected /payload/<oid> URL, got: {called_url}"


def test_download_file_ref_without_oid_hits_bare_payload_path(tmp_path: Path) -> None:
    """The empty-oid fallback (legacy single-payload refs) still hits the
    bare /payload path. This is intentional — kept for v1-compat sources
    that pre-date the multi-OID schema."""
    dest = tmp_path / "no_oid.bin"
    resp = _make_streaming_response([b"L" * 50], content_length=50)
    client = _make_client_with_session(resp)

    client.download_file_ref(
        coll_id=42, do_id=99, fref_id=7, dest=dest, oid="",
    )

    called_url = client._s.get.call_args[0][0]  # type: ignore[attr-defined]
    assert called_url.endswith(
        "/collections/42/dataObjects/99/fileReferences/7/payload"
    ), f"expected bare /payload URL, got: {called_url}"


# ── Diagnostic emit ─────────────────────────────────────────────────────────


def test_truncation_emits_file_truncated_diag(tmp_path: Path, monkeypatch) -> None:
    """The IMPORT-DBG1 surface — a future operator must be able to grep
    `kind:file_truncated` to see whether their ingest hit this bug."""
    events: list[tuple[str, dict]] = []

    def fake_emit(kind: str, payload: dict | None = None, corr: str | None = None) -> None:
        events.append((kind, payload or {}))

    monkeypatch.setattr(mod, "_diag_emit", fake_emit)

    dest = tmp_path / "diag.bin"
    resp = _make_streaming_response([b"Q" * 200], content_length=500)
    client = _make_client_with_session(resp)

    client.download_file_ref(
        coll_id=1, do_id=2, fref_id=314, dest=dest, oid="trunc-oid",
        corr="corr-q7",
    )

    truncated = [e for e in events if e[0] == "file_truncated"]
    assert len(truncated) == 1, f"expected one file_truncated event, got {events!r}"
    _, payload = truncated[0]
    assert payload["fref_id"] == 314
    assert payload["oid"] == "trunc-oid"
    assert payload["expected_bytes"] == 500
    assert payload["actual_bytes"] == 200
    assert payload["url_path"].endswith("/payload/trunc-oid")


def test_missing_content_length_emits_file_unverified_diag(tmp_path: Path, monkeypatch) -> None:
    """Chunked responses can't be verified — emit a flag event so the
    operator can see the unverifiable subset."""
    events: list[tuple[str, dict]] = []

    def fake_emit(kind: str, payload: dict | None = None, corr: str | None = None) -> None:
        events.append((kind, payload or {}))

    monkeypatch.setattr(mod, "_diag_emit", fake_emit)

    dest = tmp_path / "unverified.bin"
    resp = _make_streaming_response([b"W" * 77], content_length=None)
    client = _make_client_with_session(resp)

    client.download_file_ref(
        coll_id=1, do_id=2, fref_id=42, dest=dest, oid="some-oid",
    )

    unverified = [e for e in events if e[0] == "file_unverified"]
    assert len(unverified) == 1
    assert unverified[0][1]["fref_id"] == 42
    assert unverified[0][1]["actual_bytes"] == 77
