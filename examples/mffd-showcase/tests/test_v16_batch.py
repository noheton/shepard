"""BATCH-API-3 + BATCH-API-5 — importer-side tests for v16.5 batch DataObject creation.

Covers:
  1. Chunking: 250 items → 3 chunks of 100+100+50 batches.
  2. Fallback: batch endpoint returns 404 → switches to per-DO mode.
  3. Happy path: all items succeed → src_to_dest map populated correctly.
  4. Partial failure: some items in a chunk fail → failed items absent from
     src_to_dest, created items present; no abort.
  5. Batch path skips already-resumed DOs (resume safety).
  6. --no-batch-api forces per-DO (use_batch_api=False).
  7. Mixed batch+fallback: first chunk hits 404, remaining DOs handled per-DO.

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_v16_batch
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock, call, patch

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
if "mffd_v15" not in sys.modules:
    spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
    mffd_v15 = importlib.util.module_from_spec(spec)
    sys.modules["mffd_v15"] = mffd_v15
    spec.loader.exec_module(mffd_v15)
else:
    mffd_v15 = sys.modules["mffd_v15"]

from tests.conftest_stubs import FakeResponse, StubSession, install_stub  # noqa: E402


# ── Helpers ───────────────────────────────────────────────────────────────────

DEST_COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000099"
DEST_COLL_OGM_ID = 515365


def _make_src_do(do_id: int, name: str = "") -> mffd_v15.SourceDO:
    """Build a minimal SourceDO with only the fields Pass 1 reads."""
    return mffd_v15.SourceDO(
        do_id=do_id,
        name=name or f"DO-{do_id}",
        description="",
        attributes={},
        file_refs=None,
        ts_refs=None,
        structured_refs=None,
        parentId=None,
        predecessorIds=[],
        created="",
        modified="",
    )


def _make_batch_result(index: int, app_id: str) -> dict:
    """207 per-item success result."""
    return {"index": index, "status": "created", "appId": app_id}


def _make_batch_error(index: int, code: str = "INVALID_INPUT") -> dict:
    """207 per-item error result."""
    return {"index": index, "status": "error", "errorCode": code,
            "errorMessage": f"error at {index}"}


def _make_dest_client_with_batch(
    batch_responses: list[dict | None],
    dest_coll_app_id: str = DEST_COLL_APP_ID,
    dest_coll_ogm_id: int = DEST_COLL_OGM_ID,
) -> "mffd_v15.ShepardClient":
    """Build a ShepardClient stub that:
    - returns dest_coll_app_id on get_collection_app_id
    - returns each batch_response in sequence on create_data_objects_batch
    - returns a fake DO body on _get (for id-resolution after batch create)
    """
    client = mffd_v15.ShepardClient(
        base="https://dest.example.com",
        api_key="test-key",
        bearer_token="",
    )

    # Queue batch responses.
    batch_queue = list(batch_responses)

    def _mock_batch(items: list[dict]) -> list[dict] | None:
        if not batch_queue:
            return []
        return batch_queue.pop(0)

    client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]

    # get_collection_app_id: return fixed appId.
    client.get_collection_app_id = lambda coll_id: dest_coll_app_id  # type: ignore[method-assign]

    # _get: return a stub DO body for /v2/.../data-objects/<appId>  (id-resolution).
    app_id_to_ogm: dict[str, int] = {}
    _next_ogm = [100]

    def _mock_get(url: str, params: dict | None = None) -> FakeResponse | None:
        # id-resolution GETs look like /v2/collections/<coll_app>/data-objects/<do_app>
        if "/data-objects/" in url:
            do_app = url.rsplit("/", 1)[-1]
            if do_app not in app_id_to_ogm:
                app_id_to_ogm[do_app] = _next_ogm[0]
                _next_ogm[0] += 1
            return FakeResponse(200, {"id": app_id_to_ogm[do_app], "appId": do_app})
        return FakeResponse(200, {})

    client._get = _mock_get  # type: ignore[method-assign]

    return client


# ── Test cases ────────────────────────────────────────────────────────────────


class TestBatchChunking(unittest.TestCase):
    """250 source DOs → 3 POST calls: chunks of 100, 100, 50."""

    def test_250_items_chunked_into_3_batches(self):
        src_dos = [_make_src_do(i) for i in range(250)]

        # One result per call — 100+100+50 items each.
        def _make_chunk_results(start: int, size: int) -> list[dict]:
            return [_make_batch_result(i, f"app-{start + i}") for i in range(size)]

        batch_call_sizes: list[int] = []

        def _mock_batch(items: list[dict]) -> list[dict]:
            batch_call_sizes.append(len(items))
            return [_make_batch_result(i, f"app-chunk-{len(batch_call_sizes)}-{i}")
                    for i in range(len(items))]

        client = mffd_v15.ShepardClient(
            base="https://dest.example.com", api_key="key", bearer_token=""
        )
        client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]
        client.get_collection_app_id = lambda cid: DEST_COLL_APP_ID  # type: ignore[method-assign]
        _next = [1000]

        def _get(url: str, params: dict | None = None) -> FakeResponse | None:
            if "/data-objects/" in url:
                do_app = url.rsplit("/", 1)[-1]
                _next[0] += 1
                return FakeResponse(200, {"id": _next[0], "appId": do_app})
            return FakeResponse(200, {})

        client._get = _get  # type: ignore[method-assign]

        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, DEST_COLL_OGM_ID, "tapelaying", None, 48297,
            use_batch_api=True,
        )

        # 250 items → 3 batch calls (100 + 100 + 50).
        self.assertEqual(len(batch_call_sizes), 3, f"Expected 3 batch calls, got {len(batch_call_sizes)}")
        self.assertEqual(batch_call_sizes[0], 100)
        self.assertEqual(batch_call_sizes[1], 100)
        self.assertEqual(batch_call_sizes[2], 50)

        # All 250 source DOs should be in the result map.
        self.assertEqual(len(result), 250,
                         f"Expected 250 entries in src_to_dest, got {len(result)}")


class TestBatchFallbackOn404(unittest.TestCase):
    """First batch call returns None + _batch_supported=False → falls back to per-DO."""

    def test_fallback_to_per_do_when_batch_returns_none_and_unsupported(self):
        src_dos = [_make_src_do(i) for i in range(5)]

        per_do_calls: list[str] = []

        def _mock_batch(items: list[dict]) -> None:
            # Simulate 404 by returning None and setting _batch_supported=False.
            client._batch_supported = False
            return None

        def _mock_create_do(coll_id: int, name: str, **kw) -> dict:
            per_do_calls.append(name)
            return {"id": len(per_do_calls), "appId": f"per-do-{len(per_do_calls)}"}

        client = mffd_v15.ShepardClient(
            base="https://dest.example.com", api_key="key", bearer_token=""
        )
        client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]
        client.get_collection_app_id = lambda cid: DEST_COLL_APP_ID  # type: ignore[method-assign]
        client.create_data_object = _mock_create_do  # type: ignore[method-assign]

        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, DEST_COLL_OGM_ID, "tapelaying", None, 48297,
            use_batch_api=True,
        )

        # All 5 DOs should have been created via per-DO path.
        self.assertEqual(len(per_do_calls), 5, f"Expected 5 per-DO creates, got {len(per_do_calls)}")
        self.assertEqual(len(result), 5)


class TestBatchNoBatchApiFlag(unittest.TestCase):
    """use_batch_api=False forces per-DO; batch method never called."""

    def test_no_batch_api_flag_skips_batch_entirely(self):
        src_dos = [_make_src_do(i) for i in range(3)]

        batch_called = []

        def _mock_batch(items: list[dict]) -> list[dict]:
            batch_called.append(len(items))
            return [_make_batch_result(i, f"app-{i}") for i in range(len(items))]

        per_do_calls = []

        def _mock_create_do(coll_id: int, name: str, **kw) -> dict:
            per_do_calls.append(name)
            return {"id": len(per_do_calls), "appId": f"per-do-{len(per_do_calls)}"}

        client = mffd_v15.ShepardClient(
            base="https://dest.example.com", api_key="key", bearer_token=""
        )
        client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]
        client.get_collection_app_id = lambda cid: DEST_COLL_APP_ID  # type: ignore[method-assign]
        client.create_data_object = _mock_create_do  # type: ignore[method-assign]

        mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, DEST_COLL_OGM_ID, "tapelaying", None, 48297,
            use_batch_api=False,
        )

        # Batch method NEVER called.
        self.assertEqual(batch_called, [], "Batch endpoint should not be called when use_batch_api=False")
        # All DOs handled per-DO.
        self.assertEqual(len(per_do_calls), 3)


class TestBatchHappyPath(unittest.TestCase):
    """3 items, all succeed → src_to_dest populated correctly."""

    def test_happy_path_all_created(self):
        src_dos = [_make_src_do(10 + i) for i in range(3)]

        def _mock_batch(items: list[dict]) -> list[dict]:
            return [_make_batch_result(i, f"app-{10 + i}") for i in range(len(items))]

        client = mffd_v15.ShepardClient(
            base="https://dest.example.com", api_key="key", bearer_token=""
        )
        client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]
        client.get_collection_app_id = lambda cid: DEST_COLL_APP_ID  # type: ignore[method-assign]

        # id-resolution: return deterministic ogm ids from appId.
        def _get(url: str, params: dict | None = None) -> FakeResponse | None:
            if "/data-objects/" in url:
                do_app = url.rsplit("/", 1)[-1]
                # app-10 → ogm 1010, app-11 → 1011, ...
                suffix = int(do_app.split("-")[-1])
                return FakeResponse(200, {"id": 1000 + suffix, "appId": do_app})
            return FakeResponse(200, {})

        client._get = _get  # type: ignore[method-assign]

        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, DEST_COLL_OGM_ID, "tapelaying", None, 48297,
            use_batch_api=True,
        )

        self.assertEqual(len(result), 3)
        for i, src_do in enumerate(src_dos):
            self.assertIn(src_do.do_id, result)
            self.assertEqual(result[src_do.do_id]["dest_app_id"], f"app-{10 + i}")


class TestBatchPartialFailure(unittest.TestCase):
    """Chunk with 1 success + 1 failure → only success in src_to_dest."""

    def test_partial_failure_failed_items_absent_from_result(self):
        src_dos = [_make_src_do(1), _make_src_do(2)]

        def _mock_batch(items: list[dict]) -> list[dict]:
            # Item 0 succeeds, item 1 fails.
            return [
                _make_batch_result(0, "app-1"),
                _make_batch_error(1, "INVALID_INPUT"),
            ]

        client = mffd_v15.ShepardClient(
            base="https://dest.example.com", api_key="key", bearer_token=""
        )
        client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]
        client.get_collection_app_id = lambda cid: DEST_COLL_APP_ID  # type: ignore[method-assign]

        def _get(url: str, params: dict | None = None) -> FakeResponse | None:
            if "/data-objects/" in url:
                do_app = url.rsplit("/", 1)[-1]
                return FakeResponse(200, {"id": 999, "appId": do_app})
            return FakeResponse(200, {})

        client._get = _get  # type: ignore[method-assign]

        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, DEST_COLL_OGM_ID, "tapelaying", None, 48297,
            use_batch_api=True,
        )

        # Only src_do_id=1 (index 0) should be in the result.
        self.assertIn(1, result, "src_do_id=1 should be in result (succeeded)")
        self.assertNotIn(2, result, "src_do_id=2 should NOT be in result (failed)")


class TestBatchResumeSkipsKnownDOs(unittest.TestCase):
    """Already-persisted src_do_ids are skipped; batch only receives new DOs."""

    def test_resumed_dos_not_sent_to_batch(self):
        src_dos = [_make_src_do(i) for i in range(4)]

        # Simulate state: src_do_ids 0 and 1 already known.
        class FakeState:
            def get_dest_id(self, src_do_id: int) -> int | None:
                return 1000 + src_do_id if src_do_id in (0, 1) else None

            def get_dest_app_id(self, src_do_id: int) -> str | None:
                return f"resumed-{src_do_id}" if src_do_id in (0, 1) else None

            def set_dest_id(self, *a: Any, **kw: Any) -> None:
                pass

        batch_item_counts: list[int] = []

        def _mock_batch(items: list[dict]) -> list[dict]:
            batch_item_counts.append(len(items))
            return [_make_batch_result(i, f"new-app-{i}") for i in range(len(items))]

        client = mffd_v15.ShepardClient(
            base="https://dest.example.com", api_key="key", bearer_token=""
        )
        client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]
        client.get_collection_app_id = lambda cid: DEST_COLL_APP_ID  # type: ignore[method-assign]

        def _get(url: str, params: dict | None = None) -> FakeResponse | None:
            if "/data-objects/" in url:
                do_app = url.rsplit("/", 1)[-1]
                return FakeResponse(200, {"id": 2000, "appId": do_app})
            return FakeResponse(200, {})

        client._get = _get  # type: ignore[method-assign]

        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, DEST_COLL_OGM_ID, "tapelaying", FakeState(), 48297,
            use_batch_api=True,
        )

        # Only 2 new DOs (ids 2, 3) should have been sent to batch.
        self.assertEqual(batch_item_counts, [2],
                         f"Expected 1 batch call with 2 items, got {batch_item_counts}")

        # Result should contain all 4 (2 resumed + 2 newly created).
        self.assertEqual(len(result), 4)
        self.assertEqual(result[0]["dest_app_id"], "resumed-0")
        self.assertEqual(result[1]["dest_app_id"], "resumed-1")
        self.assertIn(2, result)
        self.assertIn(3, result)


class TestBatchApiSupportedFlag(unittest.TestCase):
    """_batch_supported flag behaviour."""

    def test_already_false_skips_batch_entirely(self):
        """If _batch_supported is already False, batch path is skipped."""
        src_dos = [_make_src_do(i) for i in range(2)]

        batch_called = []

        def _mock_batch(items: list[dict]) -> list[dict] | None:
            batch_called.append(len(items))
            return [_make_batch_result(i, f"app-{i}") for i in range(len(items))]

        per_do_calls = []

        def _mock_create_do(coll_id: int, name: str, **kw) -> dict:
            per_do_calls.append(name)
            return {"id": len(per_do_calls), "appId": f"per-do-{len(per_do_calls)}"}

        client = mffd_v15.ShepardClient(
            base="https://dest.example.com", api_key="key", bearer_token=""
        )
        # Pre-set _batch_supported = False (already probed, known absent).
        client._batch_supported = False
        client.create_data_objects_batch = _mock_batch  # type: ignore[method-assign]
        client.get_collection_app_id = lambda cid: DEST_COLL_APP_ID  # type: ignore[method-assign]
        client.create_data_object = _mock_create_do  # type: ignore[method-assign]

        mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, DEST_COLL_OGM_ID, "tapelaying", None, 48297,
            use_batch_api=True,
        )

        # Batch method should NOT be called; per-DO path used.
        self.assertEqual(batch_called, [],
                         "Batch must be skipped when _batch_supported=False")
        self.assertEqual(len(per_do_calls), 2)


if __name__ == "__main__":
    unittest.main()
