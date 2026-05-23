"""Tests for v15.11 IMPORT-DIAG — structured diagnostic instrumentation.

Pure stdlib unittest. Run from the showcase root:

    cd examples/mffd-showcase
    python3 -m unittest tests.test_diag

Covers (per `aidocs/integrations/95 §Pattern 19`):
  1. DiagSink event-line wire shape (one JSON line, required fields)
  2. Atomic single-line writes under concurrent emission (no interleaving)
  3. Mode gating — quiet/normal/verbose/http-trace + non-2xx always emitted
  4. Error classification → diagnostic_hint mapping (all 9 hint codes)
  5. JWT age + has-exp decode (well-formed + malformed + empty)
  6. Credential masking — emits sha256:... only, never plaintext fragments
  7. Summary rolling-window aggregation (counts + avg + p95)
  8. Payload-field truncation (large strings → ...[truncated])
  9. PIPE_BUF line bound (massive payloads → _overflow marker)
 10. Telemetry-mirror best-effort (failures don't break emit)
"""

from __future__ import annotations

import datetime
import importlib.util
import io
import json
import os
import sys
import threading
import time
import unittest
from pathlib import Path
from typing import Any


# ── Load the script as a module without running main() ───────────────────

_HERE = Path(__file__).resolve().parent
_SCRIPT_DIR = _HERE.parent / "scripts"

# Make sure _smart_warmup is importable (the script does `from _smart_warmup`).
sys.path.insert(0, str(_SCRIPT_DIR))

_script_spec = importlib.util.spec_from_file_location(
    "mffd_v15_diag", _SCRIPT_DIR / "mffd-import-v15.py"
)
mffd = importlib.util.module_from_spec(_script_spec)
sys.modules["mffd_v15_diag"] = mffd
_script_spec.loader.exec_module(mffd)

DiagSink = mffd.DiagSink


# ── Helpers ──────────────────────────────────────────────────────────────


class _StderrCapture:
    """Temporarily redirect fd 2 to a temp file so we can capture os.write(2,...)
    calls from DiagSink. Backed by tempfile (not pipe) so concurrent emission
    in TestAtomicWrites doesn't deadlock on PIPE_BUF saturation. Restores fd 2
    on exit and returns the captured bytes via `.captured`."""

    def __init__(self) -> None:
        import tempfile
        self._saved_fd = -1
        self._tmpfile = tempfile.TemporaryFile(mode="w+b")
        self.captured = b""

    def __enter__(self) -> "_StderrCapture":
        self._saved_fd = os.dup(2)
        os.dup2(self._tmpfile.fileno(), 2)
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        # Flush + restore stderr to original fd.
        try:
            os.fsync(2)
        except Exception:
            pass
        os.dup2(self._saved_fd, 2)
        os.close(self._saved_fd)
        self._tmpfile.seek(0)
        self.captured = self._tmpfile.read()
        self._tmpfile.close()

    def lines(self) -> list[dict]:
        """Parse the captured bytes into a list of decoded JSON events."""
        out: list[dict] = []
        for raw in self.captured.split(b"\n"):
            if not raw.strip():
                continue
            try:
                out.append(json.loads(raw.decode("utf-8", "replace")))
            except json.JSONDecodeError:
                # Interleaved line — surface to the test for an assertion.
                out.append({"_raw_decode_error": raw[:100].decode("latin-1", "replace")})
        return out


# ── 1. Event-line wire shape ─────────────────────────────────────────────


class TestEventShape(unittest.TestCase):
    """Every emitted event has the required schema fields + payload sub-doc."""

    def test_emit_produces_one_json_line_with_required_keys(self):
        sink = DiagSink("15.11", "test-session", mode="normal")
        with _StderrCapture() as cap:
            sink.emit("startup", {"hello": "world"})
        lines = cap.lines()
        self.assertEqual(len(lines), 1, "expected exactly one JSON line emitted")
        ev = lines[0]
        for required in ("t", "v", "session", "pid", "kind", "corr", "payload"):
            self.assertIn(required, ev, f"missing required field {required!r}")
        self.assertEqual(ev["v"], "15.11")
        self.assertEqual(ev["session"], "test-session")
        self.assertEqual(ev["kind"], "startup")
        self.assertEqual(ev["payload"], {"hello": "world"})

    def test_timestamp_is_iso_utc(self):
        sink = DiagSink("15.11", "s", mode="normal")
        with _StderrCapture() as cap:
            sink.emit("startup", {})
        ev = cap.lines()[0]
        # Must parse as ISO and carry timezone info.
        parsed = datetime.datetime.fromisoformat(ev["t"])
        self.assertIsNotNone(parsed.tzinfo, "timestamp must be timezone-aware")

    def test_corr_id_passes_through(self):
        sink = DiagSink("15.11", "s", mode="normal")
        with _StderrCapture() as cap:
            sink.emit("do_start", {"x": 1}, corr="tapelaying:42")
        ev = cap.lines()[0]
        self.assertEqual(ev["corr"], "tapelaying:42")


# ── 2. Atomic writes under concurrency ──────────────────────────────────


class TestAtomicWrites(unittest.TestCase):
    """Concurrent emission from N threads MUST produce N parseable JSON lines —
    no interleaving, no split lines, no JSON decode errors. This is the
    load-bearing safety guarantee for self-diagnosable logs."""

    def test_concurrent_emission_no_interleaving(self):
        sink = DiagSink("15.11", "s", mode="normal")
        n_threads = 8
        events_per_thread = 50

        def worker(idx: int) -> None:
            for j in range(events_per_thread):
                sink.emit("do_done", {
                    "thread": idx,
                    "seq": j,
                    "src_do_name": f"do-{idx}-{j}",
                    "duration_ms": idx * j,
                })

        with _StderrCapture() as cap:
            threads = [threading.Thread(target=worker, args=(i,))
                       for i in range(n_threads)]
            for t in threads:
                t.start()
            for t in threads:
                t.join()

        lines = cap.lines()
        # Every line must parse — interleaving would produce decode errors.
        decode_errors = [l for l in lines if "_raw_decode_error" in l]
        self.assertEqual(decode_errors, [],
                         f"interleaved write produced decode error: {decode_errors[:3]}")
        # Total count must match.
        self.assertEqual(len(lines), n_threads * events_per_thread)


# ── 3. Mode gating ──────────────────────────────────────────────────────


class TestModeGating(unittest.TestCase):
    """quiet/normal/verbose/http-trace allow progressively more kinds."""

    def test_quiet_suppresses_iter_start_but_keeps_errors(self):
        sink = DiagSink("15.11", "s", mode="quiet")
        with _StderrCapture() as cap:
            sink.emit("iter_start", {"step": "tapelaying"})  # suppressed
            sink.emit("do_error", {"x": 1})  # kept
            sink.emit("startup", {})  # kept
        kinds = [ev["kind"] for ev in cap.lines()]
        self.assertNotIn("iter_start", kinds)
        self.assertIn("do_error", kinds)
        self.assertIn("startup", kinds)

    def test_normal_emits_do_start_done_skip(self):
        sink = DiagSink("15.11", "s", mode="normal")
        with _StderrCapture() as cap:
            sink.emit("do_start", {})
            sink.emit("do_done", {})
            sink.emit("do_skip", {"reason": "state-file-says-done"})
        kinds = [ev["kind"] for ev in cap.lines()]
        self.assertEqual(kinds, ["do_start", "do_done", "do_skip"])

    def test_non_2xx_http_response_always_emitted_regardless_of_mode(self):
        # Even in `quiet` (the most-suppressing mode), an http_response with
        # status >= 400 must reach the operator.
        sink = DiagSink("15.11", "s", mode="quiet")
        with _StderrCapture() as cap:
            sink.emit("http_response", {"status": 403, "path": "/foo"})
            sink.emit("http_response", {"status": 200, "path": "/bar"})  # suppressed
        lines = cap.lines()
        self.assertEqual(len(lines), 1)
        self.assertEqual(lines[0]["payload"]["status"], 403)

    def test_http_trace_emits_2xx_too(self):
        sink = DiagSink("15.11", "s", mode="http-trace")
        with _StderrCapture() as cap:
            sink.emit("http_response", {"status": 200, "path": "/ok"})
            sink.emit("http_request", {"verb": "GET", "path": "/x"})
        lines = cap.lines()
        kinds = [ev["kind"] for ev in lines]
        self.assertIn("http_response", kinds)
        self.assertIn("http_request", kinds)


# ── 4. Error classification ─────────────────────────────────────────────


class TestErrorClassification(unittest.TestCase):
    """Pure-function classify_error returns the right hint per status."""

    def _hint(self, status: int | None, body: str | None = None) -> str:
        hint, _action = DiagSink.classify_error(status, body)
        return hint

    def test_401_is_jwt(self):
        self.assertEqual(self._hint(401), "jwt-expired-or-rotated")

    def test_403_is_permission(self):
        self.assertEqual(self._hint(403), "permission-denied")

    def test_404_is_not_found(self):
        self.assertEqual(self._hint(404), "not-found")

    def test_409_is_conflict(self):
        self.assertEqual(self._hint(409), "conflict")

    def test_429_is_rate_limited(self):
        self.assertEqual(self._hint(429), "rate-limited")

    def test_500_is_server_side(self):
        self.assertEqual(self._hint(500), "server-side")
        self.assertEqual(self._hint(503), "server-side")

    def test_400_with_quarkus_violations_is_bean_validation(self):
        body = '{"violations":[{"field":"name","message":"required"}]}'
        self.assertEqual(self._hint(400, body), "bean-validation")

    def test_400_without_violations_is_client_error(self):
        self.assertEqual(self._hint(400, "bad shape"), "client-error")

    def test_none_status_is_timeout(self):
        self.assertEqual(self._hint(None), "timeout-or-network")

    def test_action_is_imperative_string(self):
        # Every hint must come with a non-empty human action.
        for status in (401, 403, 404, 409, 429, 500, 400, None):
            _hint, action = DiagSink.classify_error(status, None)
            self.assertIsInstance(action, str)
            self.assertGreater(len(action), 5)


# ── 5. JWT decode ───────────────────────────────────────────────────────


class TestJwtDecode(unittest.TestCase):
    """JWT helpers must never raise; well-formed payloads return values."""

    def _make_jwt(self, payload: dict) -> str:
        import base64
        header = base64.urlsafe_b64encode(b'{"alg":"none"}').rstrip(b"=").decode()
        body = base64.urlsafe_b64encode(
            json.dumps(payload).encode()
        ).rstrip(b"=").decode()
        return f"{header}.{body}.sig"

    def test_age_from_recent_iat(self):
        token = self._make_jwt({"iat": int(time.time()) - 60, "sub": "abc"})
        age = DiagSink.jwt_age_seconds(token)
        self.assertIsNotNone(age)
        self.assertGreaterEqual(age, 59)
        self.assertLessEqual(age, 65)

    def test_age_from_malformed_returns_none(self):
        for garbage in (None, "", "not.a.jwt", "only.one", "bad..." * 5):
            self.assertIsNone(DiagSink.jwt_age_seconds(garbage),
                              f"expected None for {garbage!r}")

    def test_has_exp_true_when_exp_claim_present(self):
        token = self._make_jwt({"iat": 0, "exp": 12345, "sub": "x"})
        self.assertTrue(DiagSink.jwt_has_exp(token))

    def test_has_exp_false_when_absent(self):
        token = self._make_jwt({"iat": 0, "sub": "x"})
        self.assertFalse(DiagSink.jwt_has_exp(token))


# ── 6. Credential masking ───────────────────────────────────────────────


class TestCredentialMasking(unittest.TestCase):
    """Tokens are hashed; the plaintext must NEVER appear in any emitted line."""

    def test_mask_produces_sha256_prefix(self):
        masked = DiagSink.mask_credential("eyJhbGciOi.payload.sig")
        self.assertTrue(masked.startswith("sha256:"))
        self.assertEqual(len(masked), len("sha256:") + 12)

    def test_mask_none_is_literal_none(self):
        self.assertEqual(DiagSink.mask_credential(None), "none")
        self.assertEqual(DiagSink.mask_credential(""), "none")

    def test_emitted_events_do_not_contain_plaintext_token(self):
        sink = DiagSink("15.11", "s", mode="normal")
        secret = "eyJDONOTEMITME.PAYLOAD_PLAINTEXT.SIG_VALUE_DO_NOT_LEAK"
        with _StderrCapture() as cap:
            sink.emit("startup", {
                "dest_token_masked": DiagSink.mask_credential(secret),
                "source_token_masked": DiagSink.mask_credential(secret),
            })
        captured = cap.captured.decode("utf-8", "replace")
        # No substring of the plaintext token should appear anywhere.
        for fragment in ("DONOTEMITME", "PAYLOAD_PLAINTEXT", "SIG_VALUE_DO_NOT_LEAK"):
            self.assertNotIn(fragment, captured,
                             f"plaintext fragment {fragment!r} leaked into emit")


# ── 7. Summary rolling-window aggregation ───────────────────────────────


class TestSummaryAggregation(unittest.TestCase):
    """Pure-function _aggregate_window returns correct rollup numbers."""

    def test_empty_window_yields_zeros(self):
        agg = DiagSink._aggregate_window([], 60.0)
        self.assertEqual(agg["do_done_count"], 0)
        self.assertEqual(agg["do_error_count"], 0)
        self.assertEqual(agg["avg_do_duration_ms"], 0.0)
        self.assertEqual(agg["p95_do_duration_ms"], 0.0)

    def test_counts_and_avg(self):
        window = [
            {"k": "do_done", "p": {"duration_ms": 100}},
            {"k": "do_done", "p": {"duration_ms": 200}},
            {"k": "do_done", "p": {"duration_ms": 300}},
            {"k": "do_error", "p": {}},
            {"k": "do_skip", "p": {}},
            {"k": "http_response", "p": {"status": 403}},
            {"k": "http_response", "p": {"status": 500}},
            {"k": "http_response", "p": {"status": 429}},
        ]
        agg = DiagSink._aggregate_window(window, 60.0)
        self.assertEqual(agg["do_done_count"], 3)
        self.assertEqual(agg["do_error_count"], 1)
        self.assertEqual(agg["do_skip_count"], 1)
        self.assertEqual(agg["avg_do_duration_ms"], 200.0)
        self.assertEqual(agg["http_4xx_count"], 1)
        self.assertEqual(agg["http_5xx_count"], 1)
        self.assertEqual(agg["http_429_count"], 1)

    def test_p95_calculation(self):
        # 20 durations: 10, 20, ..., 200. p95 should be ≈ 190 (index 18).
        window = [{"k": "do_done", "p": {"duration_ms": (i + 1) * 10}}
                  for i in range(20)]
        agg = DiagSink._aggregate_window(window, 60.0)
        self.assertEqual(agg["p95_do_duration_ms"], 190.0)


# ── 8. Payload-field truncation ─────────────────────────────────────────


class TestPayloadTruncation(unittest.TestCase):
    """Large string values get truncated so the JSON line stays bounded."""

    def test_500_char_field_passes_through_untruncated(self):
        sink = DiagSink("15.11", "s", mode="normal")
        body = "x" * 500
        with _StderrCapture() as cap:
            sink.emit("do_error", {"error_body_truncated": body})
        ev = cap.lines()[0]
        self.assertEqual(ev["payload"]["error_body_truncated"], body)

    def test_oversized_field_is_truncated_with_marker(self):
        sink = DiagSink("15.11", "s", mode="normal")
        body = "y" * 2000
        with _StderrCapture() as cap:
            sink.emit("do_error", {"error_body_truncated": body})
        ev = cap.lines()[0]
        v = ev["payload"]["error_body_truncated"]
        self.assertTrue(v.endswith("...[truncated]"))
        self.assertEqual(len(v), 500 + len("...[truncated]"))


# ── 9. PIPE_BUF line bound ──────────────────────────────────────────────


class TestPipeBufBound(unittest.TestCase):
    """Massive payloads collapse to an _overflow marker so the line stays
    inside PIPE_BUF (4096 bytes on Linux) — the kernel atomicity boundary."""

    def test_overflow_when_payload_exceeds_pipe_buf(self):
        sink = DiagSink("15.11", "s", mode="normal")
        # 30 fields × 500 chars each → way past PIPE_BUF even after per-field
        # truncation (500-char fields × 30 ~= 15 000 bytes raw before JSON).
        big = {f"f{i}": "z" * 500 for i in range(30)}
        with _StderrCapture() as cap:
            sink.emit("do_error", big)
        captured = cap.captured
        # Single line.
        line_count = captured.count(b"\n")
        self.assertEqual(line_count, 1)
        # Total line length bounded.
        self.assertLessEqual(len(captured), 4096)
        # Marker present.
        self.assertIn(b'"_overflow":true', captured)


# ── 10. Telemetry-mirror best-effort ────────────────────────────────────


class TestTelemetryMirror(unittest.TestCase):
    """A failing telemetry.event must NEVER break DiagSink.emit."""

    def test_telemetry_failure_does_not_break_emit(self):
        class BrokenTel:
            def event(self, *a, **kw):
                raise RuntimeError("telemetry down")

        sink = DiagSink("15.11", "s", mode="normal", telemetry=BrokenTel())
        with _StderrCapture() as cap:
            sink.emit("do_done", {"x": 1})  # must succeed
        lines = cap.lines()
        self.assertEqual(len(lines), 1)
        self.assertEqual(lines[0]["kind"], "do_done")

    def test_telemetry_mirror_called_on_normal_events(self):
        calls: list[tuple] = []

        class CapTel:
            def event(self, level, name, **kw):
                calls.append((level, name, kw))

        sink = DiagSink("15.11", "s", mode="normal", telemetry=CapTel())
        with _StderrCapture():
            sink.emit("do_done", {"src_do_name": "n", "duration_ms": 5})
            sink.emit("do_error", {"src_do_name": "n", "where": "file"})
        self.assertEqual(len(calls), 2)
        levels = [c[0] for c in calls]
        self.assertIn("info", levels)
        self.assertIn("error", levels)


# ── 11. Version constant pinned to 15.11 ────────────────────────────────


class TestVersionConstant(unittest.TestCase):
    def test_version_is_15_11(self):
        self.assertEqual(mffd.IMPORT_SCRIPT_VERSION, "15.11")

    def test_default_diag_mode_is_normal(self):
        self.assertEqual(mffd.DIAG_MODE, "normal")


# ── 12. Module-level _diag_emit no-op safety ─────────────────────────────


class TestModuleHelperSafety(unittest.TestCase):
    """`_diag_emit` is a module-level helper that no-ops when DiagSink isn't
    installed yet (i.e. during test loading or before main()). This is the
    contract the per-DO body relies on."""

    def test_diag_emit_is_noop_when_diag_is_none(self):
        # Save + reset.
        saved = mffd._DIAG
        try:
            mffd._DIAG = None
            # Must not raise.
            mffd._diag_emit("do_start", {"x": 1})
            mffd._diag_emit("do_done", None, corr="abc")
        finally:
            mffd._DIAG = saved


if __name__ == "__main__":
    unittest.main()
