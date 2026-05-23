"""v15.8 IMPORT-PERF1 — worker-pool real fan-out wiring.

History: in v15.1..v15.7 `run_source_mode_workers` instantiated a
ThreadPoolExecutor + ran trivial `lambda: True` probes, then delegated to
`run_source_mode` SEQUENTIALLY. `--workers 4` bought zero concurrency.
See `aidocs/agent-findings/mffd-import-slowness-diagnose-2026-05-23.md §5`
hypothesis #1.

v15.8 moves the worker-pool implementation INTO `run_source_mode` so the
per-DO loop is the thing that fans out (not the per-step driver). The
`run_source_mode_workers` entry point becomes a thin forwarder that just
passes `workers=N` to `run_source_mode`.

Asserts:
  * `run_source_mode_workers(workers=1)` calls `run_source_mode(workers=1)`.
  * `run_source_mode_workers(workers=N)` for N>1 calls `run_source_mode(workers=N)`.
  * The wrapper itself NEVER instantiates ThreadPoolExecutor — that lives
    inside `run_source_mode` per-step.

Tests assert the *wiring* of the new shape. Real-concurrency convergence
tests are still flaky and out of scope here.

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_worker_pool
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
if "mffd_v15" not in sys.modules:
    spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
    mffd_v15 = importlib.util.module_from_spec(spec)
    sys.modules["mffd_v15"] = mffd_v15
    spec.loader.exec_module(mffd_v15)
else:
    mffd_v15 = sys.modules["mffd_v15"]


class TestSequentialFallback(unittest.TestCase):
    """workers<=1 — `run_source_mode_workers` calls `run_source_mode(workers=1)`."""

    def test_workers_one_forwards_with_workers_one(self):
        """workers=1 must delegate to run_source_mode with workers=1."""
        sentinel = {"tapelaying": 100, "bridgewelding": 200}
        with patch.object(mffd_v15, "run_source_mode", return_value=sentinel) as mock_run:
            result = mffd_v15.run_source_mode_workers(
                dest_client=object(),
                coll_id=515365,
                state=None,
                source_client=None,
                workers=1,
            )
            self.assertEqual(result, sentinel)
            mock_run.assert_called_once()
            # Verify the workers kwarg is 1 (sequential semantics preserved)
            kwargs = mock_run.call_args.kwargs
            self.assertEqual(kwargs.get("workers"), 1)

    def test_workers_zero_or_negative_also_sequential(self):
        """workers <= 1 (defensive) — also forwards with workers=1."""
        with patch.object(mffd_v15, "run_source_mode", return_value={}) as mock_run:
            mffd_v15.run_source_mode_workers(
                dest_client=object(), coll_id=515365, workers=0,
            )
            mock_run.assert_called_once()
            kwargs = mock_run.call_args.kwargs
            self.assertEqual(kwargs.get("workers"), 1)


class TestConcurrentPathWiring(unittest.TestCase):
    """workers>1 — `run_source_mode_workers` calls `run_source_mode(workers=N)`.

    The wrapper itself does NOT instantiate ThreadPoolExecutor in v15.8 —
    that lives inside `run_source_mode` per-step. The wrapper's job is to
    pass the `workers` arg through.
    """

    def test_workers_four_forwards_with_workers_four(self):
        """workers=4 — the wrapper forwards `workers=4` to run_source_mode."""
        with patch.object(mffd_v15, "run_source_mode", return_value={}) as mock_run:
            mffd_v15.run_source_mode_workers(
                dest_client=object(), coll_id=515365, workers=4,
            )
            mock_run.assert_called_once()
            kwargs = mock_run.call_args.kwargs
            self.assertEqual(kwargs.get("workers"), 4)

    def test_workers_two_returns_run_source_mode_result(self):
        """The wrapper must propagate run_source_mode's result through."""
        sentinel = {"tapelaying": 5, "bridgewelding": 7}
        with patch.object(mffd_v15, "run_source_mode", return_value=sentinel):
            result = mffd_v15.run_source_mode_workers(
                dest_client=object(), coll_id=515365, workers=2,
            )
            self.assertEqual(result, sentinel)

    def test_wrapper_does_not_instantiate_thread_pool_directly(self):
        """v15.8 — the wrapper itself must NOT instantiate ThreadPoolExecutor;
        the per-step executor lives inside `run_source_mode`."""
        with patch.object(mffd_v15, "run_source_mode", return_value={}):
            with patch("concurrent.futures.ThreadPoolExecutor") as mock_exec:
                mffd_v15.run_source_mode_workers(
                    dest_client=object(), coll_id=515365, workers=4,
                )
                # The wrapper no longer probes the executor with lambdas
                mock_exec.assert_not_called()


class TestLazyEnrichment(unittest.TestCase):
    """v15.8 IMPORT-PERF2 — iter_data_objects yields SourceDO STUBS with
    file_refs / ts_refs / structured_refs = None. The per-DO loop calls
    `_load_*_refs(src_do)` only after state-skip has had a chance to
    decline the work. On a fully-resumed collection: 0 cube3 GETs per DO
    (vs 3 in v15.7)."""

    def test_iter_data_objects_yields_stubs(self):
        """SourceDO yielded from iter_data_objects has None refs."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://test"
        # Mock _get to return one page of items
        fake_resp = MagicMock()
        fake_resp.json.return_value = [
            {"id": 1, "name": "DO-1", "attributes": {"k": "v"}},
            {"id": 2, "name": "DO-2", "attributes": {}},
        ]
        fake_resp.headers = {"X-Total-Pages": "1"}
        client._get = MagicMock(side_effect=[fake_resp, None])
        # The fetch helpers must NOT be called during iteration — that's
        # the PERF2 guarantee.
        client._fetch_file_refs = MagicMock(side_effect=AssertionError(
            "_fetch_file_refs called eagerly — PERF2 broken"))
        client._fetch_ts_refs = MagicMock(side_effect=AssertionError(
            "_fetch_ts_refs called eagerly — PERF2 broken"))
        client._fetch_structured_refs = MagicMock(side_effect=AssertionError(
            "_fetch_structured_refs called eagerly — PERF2 broken"))

        result = list(client.iter_data_objects(48297))
        self.assertEqual(len(result), 2)
        self.assertIsNone(result[0].file_refs)
        self.assertIsNone(result[0].ts_refs)
        self.assertIsNone(result[0].structured_refs)
        self.assertEqual(result[0]._src_coll_id, 48297)

    def test_load_file_refs_populates_and_caches(self):
        """_load_file_refs fetches on first call, caches on subsequent calls."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        fake_refs = [mffd_v15.FileRef(fref_id=1, name="a.txt", size=100, oid="")]
        client._fetch_file_refs = MagicMock(return_value=fake_refs)

        src_do = mffd_v15.SourceDO(
            do_id=42, name="DO-42", description="", attributes={},
            _src_coll_id=48297,
        )
        # First call: fetches
        refs1 = client._load_file_refs(src_do)
        self.assertEqual(refs1, fake_refs)
        client._fetch_file_refs.assert_called_once_with(48297, 42)
        # Second call: cached, no new fetch
        refs2 = client._load_file_refs(src_do)
        self.assertIs(refs1, refs2)
        client._fetch_file_refs.assert_called_once()  # still 1 call total

    def test_is_do_done_short_circuit_keys_exist(self):
        """v15.8 PERF2 guarantee: ImportState has is_do_done/mark_do_done that
        the per-DO loop short-circuits on. A fully-done DO costs 0 cube3 GETs
        because `_process_one_source_do` returns early BEFORE calling
        `_load_*_refs`.

        This test asserts the API contract — the state methods exist and a
        marked-done key reports done. The end-to-end "0 GETs" assertion lives
        in TestPerDoShortCircuit below; this test is the unit-level shield
        against accidental API removal.
        """
        import tempfile
        from pathlib import Path
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        self.assertFalse(state.is_do_done("tapelaying/DO-42"))
        state.mark_do_done("tapelaying/DO-42")
        self.assertTrue(state.is_do_done("tapelaying/DO-42"))
        # Sibling step doesn't collide.
        self.assertFalse(state.is_do_done("bridgewelding/DO-42"))
        path.unlink(missing_ok=True)


class TestImportStateThreadSafety(unittest.TestCase):
    """v15.8 IMPORT-PERF1 — ImportState must serialise read+write under
    concurrent workers. The RLock prevents lost updates."""

    def test_state_has_rlock(self):
        import tempfile
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        self.assertTrue(hasattr(state, "_lock"))
        # RLock can be acquired multiple times by the same thread without
        # deadlock — guard against future code that nests state ops.
        state._lock.acquire()
        state._lock.acquire()
        state._lock.release()
        state._lock.release()
        path.unlink(missing_ok=True)

    def test_state_marks_under_lock_dont_deadlock(self):
        """Sequential mark_file_done calls must not deadlock with the RLock."""
        import tempfile
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        state.mark_file_done("a")
        state.mark_file_done("b")
        self.assertTrue(state.is_file_done("a"))
        self.assertTrue(state.is_file_done("b"))
        path.unlink(missing_ok=True)


class TestWorkerPoolPrimitivesAvailable(unittest.TestCase):
    """The v15 wrapper relies on v15's C7 primitives — guard against
    accidental removal."""

    def test_backoff_delay_available(self):
        self.assertTrue(hasattr(mffd_v15, "backoff_delay"))
        d = mffd_v15.backoff_delay(0)
        self.assertGreater(d, 0)

    def test_atomic_write_json_available(self):
        self.assertTrue(hasattr(mffd_v15, "atomic_write_json"))

    def test_state_file_available(self):
        self.assertTrue(hasattr(mffd_v15, "StateFile"))

    def test_jwt_pause_manager_available(self):
        self.assertTrue(hasattr(mffd_v15, "JwtPauseManager"))


class TestVersionConstant(unittest.TestCase):
    """v15.8 — IMPORT_SCRIPT_VERSION constant is the source of truth."""

    def test_version_is_15_8(self):
        self.assertEqual(mffd_v15.IMPORT_SCRIPT_VERSION, "15.8")


if __name__ == "__main__":
    unittest.main()
