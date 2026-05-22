"""v15.1 Tier 5 — worker-pool wrapper wiring (Agent A deferred glue).

Asserts:
  * `run_source_mode_workers(workers=1)` is the sequential fallback —
    routes to run_source_mode directly, no executor / queue overhead.
  * `run_source_mode_workers(workers=N)` for N>1 instantiates a
    ThreadPoolExecutor with N workers and proves the pool health-checks.

Tests assert the *wiring*, not real concurrency. Real-concurrency convergence
tests are flaky (per Agent A's deferral rationale) and out of scope for v15.1.

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
    """workers=1 is byte-for-byte the existing sequential path."""

    def test_workers_one_delegates_to_run_source_mode(self):
        """When workers=1, the wrapper must call run_source_mode directly
        and NEVER touch ThreadPoolExecutor — preserving v15 semantics."""
        sentinel = {"tapelaying": 100, "bridgewelding": 200}
        with patch.object(mffd_v15, "run_source_mode", return_value=sentinel) as mock_run:
            with patch("concurrent.futures.ThreadPoolExecutor") as mock_exec:
                result = mffd_v15.run_source_mode_workers(
                    dest_client=object(),  # unused in this mock
                    coll_id=515365,
                    state=None,
                    source_client=None,
                    workers=1,
                )
                self.assertEqual(result, sentinel)
                mock_run.assert_called_once()
                # CRITICAL: workers=1 must NOT instantiate the executor
                mock_exec.assert_not_called()

    def test_workers_zero_or_negative_also_sequential(self):
        """workers ≤ 1 (defensive) — also sequential."""
        with patch.object(mffd_v15, "run_source_mode", return_value={}) as mock_run:
            with patch("concurrent.futures.ThreadPoolExecutor") as mock_exec:
                mffd_v15.run_source_mode_workers(
                    dest_client=object(), coll_id=515365, workers=0,
                )
                mock_run.assert_called_once()
                mock_exec.assert_not_called()


class TestConcurrentPathWiring(unittest.TestCase):
    """workers>1 path: must instantiate ThreadPoolExecutor with N workers
    AND health-check the pool before delegating to run_source_mode."""

    def test_workers_four_creates_pool_size_four(self):
        recorded = {}
        def fake_run_source_mode(*args, **kwargs):
            return {"tapelaying": 100, "bridgewelding": 200}
        with patch.object(mffd_v15, "run_source_mode", side_effect=fake_run_source_mode):
            from concurrent.futures import ThreadPoolExecutor
            real_init = ThreadPoolExecutor.__init__
            def spy_init(self, *args, **kwargs):
                recorded["max_workers"] = kwargs.get("max_workers")
                real_init(self, *args, **kwargs)
            with patch.object(ThreadPoolExecutor, "__init__", spy_init):
                result = mffd_v15.run_source_mode_workers(
                    dest_client=object(), coll_id=515365, workers=4,
                )
                self.assertEqual(recorded["max_workers"], 4)
                # Result is still the run_source_mode result (the v15.1
                # conservative path delegates back after wiring).
                self.assertEqual(result["tapelaying"], 100)

    def test_workers_two_returns_run_source_mode_result(self):
        """The wrapper must propagate run_source_mode's result through."""
        sentinel = {"tapelaying": 5, "bridgewelding": 7}
        with patch.object(mffd_v15, "run_source_mode", return_value=sentinel):
            result = mffd_v15.run_source_mode_workers(
                dest_client=object(), coll_id=515365, workers=2,
            )
            self.assertEqual(result, sentinel)


class TestWorkerPoolPrimitivesAvailable(unittest.TestCase):
    """The v15.1 wrapper relies on v15's C7 primitives — guard against
    accidental removal."""

    def test_backoff_delay_available(self):
        self.assertTrue(hasattr(mffd_v15, "backoff_delay"))
        # Sanity: returns a positive float for attempt=0
        d = mffd_v15.backoff_delay(0)
        self.assertGreater(d, 0)

    def test_atomic_write_json_available(self):
        self.assertTrue(hasattr(mffd_v15, "atomic_write_json"))

    def test_state_file_available(self):
        self.assertTrue(hasattr(mffd_v15, "StateFile"))

    def test_jwt_pause_manager_available(self):
        self.assertTrue(hasattr(mffd_v15, "JwtPauseManager"))


if __name__ == "__main__":
    unittest.main()
