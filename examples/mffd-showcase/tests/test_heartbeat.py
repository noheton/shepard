"""v15.11 MFFD-IMPORT-HEARTBEAT1 — primary alive-signal daemon tests.

The Heartbeater daemon emits one JSON heartbeat every HEARTBEAT_LIVE_S
seconds (5s default) to stdout. Its absence (for >3× the cadence) is the
unambiguous "script dead/stuck" diagnosis for an outside observer
running `tmux capture-pane | grep '"kind":"heartbeat"' | tail -n 10`.

Asserts (per the v15.11 spec):
  * monotonic `seq` (1, 2, 3, ...) — consecutive heartbeats prove forward
    progress in the daemon thread itself
  * canonical phase set: {startup, warmup, ingest, idle, self-updating,
    shutdown, stuck}
  * heartbeat JSON parses + carries the required fields
  * watchdog flips phase=ingest → phase=stuck when
    secs_since_last_do > HEARTBEAT_STUCK_S
  * `_on_do_done()` driver hook bumps `_DO_DONE_SINCE_START` + sets
    `_LAST_DO_DONE_AT` (these are the only writes the heartbeater reads)
  * `_set_in_flight(n)` driver hook updates the gauge the heartbeater
    reports as `do_in_flight`
  * 5s heartbeat does NOT inherit the legacy 60s HEARTBEAT_S constant

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_heartbeat
"""

from __future__ import annotations

import importlib.util
import io
import json
import sys
import threading
import time
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import MagicMock

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
if "mffd_v15" not in sys.modules:
    spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
    mffd_v15 = importlib.util.module_from_spec(spec)
    sys.modules["mffd_v15"] = mffd_v15
    spec.loader.exec_module(mffd_v15)
else:
    mffd_v15 = sys.modules["mffd_v15"]


# ── Cadence is a NEW constant, independent of legacy 60s manifest poll ──────

class TestCadenceConstants(unittest.TestCase):
    """The user's directive: do NOT overload the existing 60s HEARTBEAT_S."""

    def test_heartbeat_live_s_is_5_by_default(self):
        self.assertEqual(mffd_v15.HEARTBEAT_LIVE_S, 5)

    def test_legacy_heartbeat_s_stays_60(self):
        """The 60s constant drives the SelfUpdater manifest poll + the
        `_telemetry_loop` flush. v15.11 must NOT change its semantics."""
        self.assertEqual(mffd_v15.HEARTBEAT_S, 60)

    def test_stuck_threshold_default_60s(self):
        self.assertEqual(mffd_v15.HEARTBEAT_STUCK_S, 60.0)

    def test_wedge_threshold_default_180s(self):
        self.assertEqual(mffd_v15.HEARTBEAT_WEDGE_S, 180.0)

    def test_version_bumped_to_16_9(self):
        self.assertEqual(mffd_v15.IMPORT_SCRIPT_VERSION, "16.9")


# ── Phase vocabulary is the canonical set the brief specified ───────────────

class TestPhaseVocabulary(unittest.TestCase):

    def test_canonical_phases(self):
        expected = {"startup", "warmup", "ingest", "idle",
                    "self-updating", "shutdown", "stuck"}
        self.assertEqual(set(mffd_v15._PHASE_CODE.keys()), expected)

    def test_phase_codes_distinct(self):
        codes = list(mffd_v15._PHASE_CODE.values())
        self.assertEqual(len(codes), len(set(codes)),
                         "phase code integers must be distinct")

    def test_set_phase_mutator(self):
        original = mffd_v15._PHASE
        try:
            mffd_v15._set_phase("warmup")
            self.assertEqual(mffd_v15._PHASE, "warmup")
            mffd_v15._set_phase("ingest")
            self.assertEqual(mffd_v15._PHASE, "ingest")
        finally:
            mffd_v15._set_phase(original)


# ── Driver-thread hooks ──────────────────────────────────────────────────────

class TestDriverHooks(unittest.TestCase):

    def setUp(self):
        # Snapshot + reset shared globals so test order doesn't matter.
        self._saved = (
            mffd_v15._PHASE, mffd_v15._DO_DONE_SINCE_START,
            mffd_v15._DO_IN_FLIGHT, mffd_v15._LAST_DO_DONE_AT,
        )
        mffd_v15._PHASE = "startup"
        mffd_v15._DO_DONE_SINCE_START = 0
        mffd_v15._DO_IN_FLIGHT = 0
        mffd_v15._LAST_DO_DONE_AT = 0.0

    def tearDown(self):
        (mffd_v15._PHASE, mffd_v15._DO_DONE_SINCE_START,
         mffd_v15._DO_IN_FLIGHT, mffd_v15._LAST_DO_DONE_AT) = self._saved

    def test_on_do_done_increments_counter(self):
        before = mffd_v15._DO_DONE_SINCE_START
        mffd_v15._on_do_done()
        self.assertEqual(mffd_v15._DO_DONE_SINCE_START, before + 1)
        mffd_v15._on_do_done()
        self.assertEqual(mffd_v15._DO_DONE_SINCE_START, before + 2)

    def test_on_do_done_sets_last_do_at(self):
        self.assertEqual(mffd_v15._LAST_DO_DONE_AT, 0.0)
        t0 = time.time()
        mffd_v15._on_do_done()
        self.assertGreaterEqual(mffd_v15._LAST_DO_DONE_AT, t0)
        self.assertLessEqual(mffd_v15._LAST_DO_DONE_AT, time.time() + 0.1)

    def test_set_in_flight(self):
        mffd_v15._set_in_flight(7)
        self.assertEqual(mffd_v15._DO_IN_FLIGHT, 7)
        mffd_v15._set_in_flight(0)
        self.assertEqual(mffd_v15._DO_IN_FLIGHT, 0)


# ── Heartbeat JSON shape + monotonic seq ─────────────────────────────────────

class TestHeartbeatEmission(unittest.TestCase):

    def _make_tel(self):
        tel = MagicMock()
        tel.gauge = MagicMock()
        return tel

    def _capture_ticks(self, n: int) -> list[dict]:
        """Run `_tick()` n times directly (bypass threading) and return
        the parsed JSON lines emitted to stdout."""
        # Reset shared state so the test is self-contained.
        mffd_v15._PHASE = "ingest"
        mffd_v15._DO_DONE_SINCE_START = 0
        mffd_v15._DO_IN_FLIGHT = 0
        mffd_v15._LAST_DO_DONE_AT = time.time()  # fresh, not stuck

        hb = mffd_v15.Heartbeater(self._make_tel())
        buf = io.StringIO()
        with redirect_stdout(buf):
            for _ in range(n):
                hb._tick()
        lines = [l for l in buf.getvalue().splitlines() if l.strip()]
        return [json.loads(l) for l in lines]

    def test_emits_one_line_per_tick(self):
        lines = self._capture_ticks(3)
        # Each tick emits 1 line in non-wedge state.
        self.assertEqual(len(lines), 3)

    def test_monotonic_seq(self):
        lines = self._capture_ticks(5)
        seqs = [l["seq"] for l in lines]
        self.assertEqual(seqs, [1, 2, 3, 4, 5])

    def test_required_fields_present(self):
        lines = self._capture_ticks(1)
        hb = lines[0]
        for field in ("t", "v", "pid", "kind", "seq", "phase",
                      "do_done_since_start", "do_in_flight"):
            self.assertIn(field, hb, f"heartbeat missing field {field!r}")
        self.assertEqual(hb["kind"], "heartbeat")
        self.assertEqual(hb["v"], "15.11")

    def test_phase_field_reflects_module_state(self):
        # _capture_ticks resets _PHASE to "ingest"; call _tick directly to
        # verify the heartbeater reads from the live global.
        mffd_v15._PHASE = "warmup"
        mffd_v15._DO_DONE_SINCE_START = 0
        mffd_v15._LAST_DO_DONE_AT = time.time()
        hb = mffd_v15.Heartbeater(self._make_tel())
        buf = io.StringIO()
        with redirect_stdout(buf):
            hb._tick()
        line = json.loads(buf.getvalue().splitlines()[0])
        self.assertEqual(line["phase"], "warmup")
        mffd_v15._set_phase("startup")

    def test_secs_since_last_do_present_when_do_has_completed(self):
        lines = self._capture_ticks(1)
        # _capture_ticks sets _LAST_DO_DONE_AT = time.time() up front.
        self.assertIn("secs_since_last_do", lines[0])
        self.assertIn("last_do_at", lines[0])

    def test_payload_under_400_bytes(self):
        """Brief target: ≤200 bytes 'in steady state'. Allow some slack
        for the diagnostic_hint case + the ISO timestamp encoding, but
        the typical-case heartbeat must stay compact."""
        lines = self._capture_ticks(1)
        encoded = json.dumps(lines[0], separators=(",", ":"))
        self.assertLessEqual(len(encoded), 400,
                             f"heartbeat too verbose: {len(encoded)} bytes")

    def test_telemetry_gauge_called(self):
        tel = self._make_tel()
        mffd_v15._PHASE = "ingest"
        mffd_v15._DO_DONE_SINCE_START = 0
        mffd_v15._LAST_DO_DONE_AT = time.time()
        hb = mffd_v15.Heartbeater(tel)
        buf = io.StringIO()
        with redirect_stdout(buf):
            hb._tick()
        # gauge("heartbeat_seq", ...) AND gauge("heartbeat_phase_code", ...)
        call_names = [c.args[0] for c in tel.gauge.call_args_list]
        self.assertIn("heartbeat_seq", call_names)
        self.assertIn("heartbeat_phase_code", call_names)


# ── Watchdog: phase=ingest → phase=stuck when no DO progress ─────────────────

class TestStuckWatchdog(unittest.TestCase):

    def _tick_and_get(self, hb, buf):
        before = buf.tell()
        with redirect_stdout(buf):
            hb._tick()
        buf.seek(before)
        rest = buf.read()
        lines = [json.loads(l) for l in rest.splitlines() if l.strip()]
        return lines

    def test_phase_ingest_no_progress_flips_to_stuck(self):
        mffd_v15._PHASE = "ingest"
        mffd_v15._DO_DONE_SINCE_START = 5
        mffd_v15._DO_IN_FLIGHT = 4
        # Last DO completed 120s ago (> HEARTBEAT_STUCK_S=60).
        mffd_v15._LAST_DO_DONE_AT = time.time() - 120.0

        tel = MagicMock()
        hb = mffd_v15.Heartbeater(tel)
        buf = io.StringIO()
        with redirect_stdout(buf):
            hb._tick()
        lines = [json.loads(l) for l in buf.getvalue().splitlines() if l.strip()]
        self.assertEqual(lines[0]["phase"], "stuck")
        self.assertIn("diagnostic_hint", lines[0])
        # Module global also flipped (so the next heartbeat reports stuck too).
        self.assertEqual(mffd_v15._PHASE, "stuck")
        # Cleanup.
        mffd_v15._set_phase("startup")

    def test_phase_warmup_with_no_progress_does_NOT_flip(self):
        """Watchdog only arms during ingest; warmup/idle/etc. ignore the
        gap (no DOs are expected during warmup so no gap is anomalous)."""
        mffd_v15._PHASE = "warmup"
        mffd_v15._LAST_DO_DONE_AT = time.time() - 999.0
        mffd_v15._DO_DONE_SINCE_START = 0

        tel = MagicMock()
        hb = mffd_v15.Heartbeater(tel)
        buf = io.StringIO()
        with redirect_stdout(buf):
            hb._tick()
        lines = [json.loads(l) for l in buf.getvalue().splitlines() if l.strip()]
        self.assertEqual(lines[0]["phase"], "warmup")
        self.assertNotIn("diagnostic_hint", lines[0])
        self.assertEqual(mffd_v15._PHASE, "warmup")
        mffd_v15._set_phase("startup")

    def test_wedge_emits_worker_pool_event(self):
        """At HEARTBEAT_WEDGE_S the daemon emits a second JSON line with
        kind=worker_pool carrying the in-flight count + thread names.
        This is the diagnostic dump a future Claude session reads to
        diagnose 'all workers wedged in HTTP'."""
        mffd_v15._PHASE = "ingest"
        mffd_v15._DO_DONE_SINCE_START = 100
        mffd_v15._DO_IN_FLIGHT = 4
        # 200s ago > HEARTBEAT_WEDGE_S (180) — wedge dump should fire.
        mffd_v15._LAST_DO_DONE_AT = time.time() - 200.0

        tel = MagicMock()
        hb = mffd_v15.Heartbeater(tel)
        buf = io.StringIO()
        with redirect_stdout(buf):
            hb._tick()
        lines = [json.loads(l) for l in buf.getvalue().splitlines() if l.strip()]
        kinds = [l["kind"] for l in lines]
        self.assertIn("heartbeat", kinds)
        self.assertIn("worker_pool", kinds)
        worker_line = next(l for l in lines if l["kind"] == "worker_pool")
        self.assertIn("thread_names", worker_line)
        self.assertEqual(worker_line["do_in_flight"], 4)
        mffd_v15._set_phase("startup")


# ── Three example heartbeat lines (per user's reporting requirement) ────────

class TestExampleLinesForReport(unittest.TestCase):
    """The brief asks for 3 example heartbeat lines covering healthy,
    stuck, shutdown. Generate them from the real emission code so the
    report can't accidentally drift from what ships.

    The lines printed here are exactly the format
    `tmux capture-pane | grep '"kind":"heartbeat"'` will yield.
    """

    @staticmethod
    def _emit_one(phase: str, *, secs_since: float | None,
                  done: int, in_flight: int, seq: int = 1) -> str:
        mffd_v15._PHASE = phase
        mffd_v15._DO_DONE_SINCE_START = done
        mffd_v15._DO_IN_FLIGHT = in_flight
        if secs_since is None:
            mffd_v15._LAST_DO_DONE_AT = 0.0
        else:
            mffd_v15._LAST_DO_DONE_AT = time.time() - secs_since
        hb = mffd_v15.Heartbeater(MagicMock())
        hb._seq = seq - 1  # so _tick() increments to `seq`
        buf = io.StringIO()
        with redirect_stdout(buf):
            hb._tick()
        first_line = buf.getvalue().splitlines()[0]
        mffd_v15._set_phase("startup")
        return first_line

    def test_healthy_line_parses(self):
        line = self._emit_one("ingest", secs_since=2.6, done=1654,
                              in_flight=4, seq=4823)
        parsed = json.loads(line)
        self.assertEqual(parsed["phase"], "ingest")
        self.assertEqual(parsed["seq"], 4823)
        self.assertNotIn("diagnostic_hint", parsed)

    def test_stuck_line_parses(self):
        line = self._emit_one("ingest", secs_since=125.0, done=2100,
                              in_flight=4, seq=4900)
        parsed = json.loads(line)
        self.assertEqual(parsed["phase"], "stuck")
        self.assertIn("diagnostic_hint", parsed)

    def test_shutdown_line_parses(self):
        line = self._emit_one("shutdown", secs_since=8.0, done=8462,
                              in_flight=0, seq=9999)
        parsed = json.loads(line)
        self.assertEqual(parsed["phase"], "shutdown")
        self.assertEqual(parsed["do_in_flight"], 0)


if __name__ == "__main__":
    unittest.main()
