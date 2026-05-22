"""StateFile + atomic_write_json (v15 §5 single state-writer thread).

Run: python -m unittest tests.test_state_writer
"""

from __future__ import annotations

import importlib.util
import json
import os
import sys
import tempfile
import time
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


class TestAtomicWriteJson(unittest.TestCase):
    def test_atomic_write_creates_file(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "state.json"
            mffd_v15.atomic_write_json(path, {"a": 1, "b": [2, 3]})
            self.assertTrue(path.exists())
            with path.open() as fh:
                loaded = json.load(fh)
            self.assertEqual(loaded, {"a": 1, "b": [2, 3]})

    def test_atomic_write_no_tmp_left_behind(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "state.json"
            mffd_v15.atomic_write_json(path, {"a": 1})
            tmp = path.with_name(path.name + ".tmp")
            self.assertFalse(tmp.exists(),
                "Atomic rename must remove the .tmp pivot file")

    def test_atomic_write_overwrites_existing(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "state.json"
            mffd_v15.atomic_write_json(path, {"v": 1})
            mffd_v15.atomic_write_json(path, {"v": 2})
            with path.open() as fh:
                loaded = json.load(fh)
            self.assertEqual(loaded, {"v": 2})

    def test_atomic_write_creates_parent_dirs(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "nested" / "deep" / "state.json"
            mffd_v15.atomic_write_json(path, {"x": 1})
            self.assertTrue(path.exists())


class TestStateFile(unittest.TestCase):
    def test_initial_state_is_empty(self):
        with tempfile.TemporaryDirectory() as td:
            sf = mffd_v15.StateFile(Path(td) / "s.json")
            snap = sf.snapshot()
            self.assertEqual(snap["completed_files"], [])
            self.assertEqual(snap["completed_ts"], [])
            self.assertEqual(snap["completed_structured"], [])
            self.assertEqual(snap["batch_sequence"], 0)

    def test_record_and_query_completion(self):
        with tempfile.TemporaryDirectory() as td:
            sf = mffd_v15.StateFile(Path(td) / "s.json")
            sf.record_file("src:48297:fileRef:100:oid:a")
            self.assertTrue(sf.is_file_done("src:48297:fileRef:100:oid:a"))
            self.assertFalse(sf.is_file_done("src:48297:fileRef:100:oid:b"))

    def test_record_dedupes(self):
        with tempfile.TemporaryDirectory() as td:
            sf = mffd_v15.StateFile(Path(td) / "s.json")
            sf.record_file("x")
            sf.record_file("x")
            self.assertEqual(sf.snapshot()["completed_files"], ["x"])

    def test_do_mapping_round_trip(self):
        with tempfile.TemporaryDirectory() as td:
            sf = mffd_v15.StateFile(Path(td) / "s.json")
            sf.map_do(12345, 99999)
            self.assertEqual(sf.get_dest_do(12345), 99999)
            self.assertIsNone(sf.get_dest_do(0))

    def test_batch_sequence_monotonic(self):
        with tempfile.TemporaryDirectory() as td:
            sf = mffd_v15.StateFile(Path(td) / "s.json")
            seqs = [sf.next_batch_seq() for _ in range(5)]
            self.assertEqual(seqs, [1, 2, 3, 4, 5])

    def test_persist_force_writes_to_disk(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "s.json"
            sf = mffd_v15.StateFile(path)
            sf.record_file("x")
            self.assertTrue(sf.persist(force=True))
            self.assertTrue(path.exists())

    def test_persist_throttles_until_threshold(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "s.json"
            sf = mffd_v15.StateFile(path)
            # Single record should NOT trigger an automatic flush
            sf.record_file("x")
            self.assertFalse(sf.persist(force=False),
                "Single record below the 100-event / 30s threshold must not flush")

    def test_state_round_trips_through_disk(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "s.json"
            sf = mffd_v15.StateFile(path)
            sf.record_file("file:1")
            sf.record_ts("ts:1")
            sf.record_structured("sd:1")
            sf.map_do(1, 100)
            sf.persist(force=True)

            # Reload and verify
            sf2 = mffd_v15.StateFile(path)
            self.assertTrue(sf2.is_file_done("file:1"))
            self.assertTrue(sf2.is_ts_done("ts:1"))
            self.assertTrue(sf2.is_structured_done("sd:1"))
            self.assertEqual(sf2.get_dest_do(1), 100)

    def test_corrupt_state_file_starts_fresh(self):
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "s.json"
            path.write_text("not valid json {", encoding="utf-8")
            # Must not raise — corrupt state silently falls back to fresh
            sf = mffd_v15.StateFile(path)
            self.assertEqual(sf.snapshot()["completed_files"], [])


if __name__ == "__main__":
    unittest.main()
