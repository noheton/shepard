"""v16.8 UPLOAD-FANOUT — MFFD_UPLOAD_WORKERS env parsing.

The local-mode file-byte upload loop (run_local_mode) is fanned out across a
ThreadPoolExecutor sized by UPLOAD_WORKERS. This guards the env-parsing contract
— specifically the empty-string case that bit PRESERVE_HIERARCHY_WORKERS before
its v16.3 `or "8"` rescue (a set-but-empty `.env` line yields '', which int('')
would crash on). Parsing is import-time, so each case reloads the module fresh
under a patched environment.

Run: python -m unittest tests.test_upload_fanout
"""

from __future__ import annotations

import importlib.util
import os
import sys
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"


def _load_with_env(value):
    """Re-exec the script module with MFFD_UPLOAD_WORKERS set to `value`.

    `value is None` removes the var entirely (tests the built-in default).
    Restores the prior environment before returning.
    """
    key = "MFFD_UPLOAD_WORKERS"
    prev = os.environ.get(key)
    try:
        if value is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = value
        modname = "mffd_v15_upload_envtest"
        spec = importlib.util.spec_from_file_location(modname, _SCRIPT)
        mod = importlib.util.module_from_spec(spec)
        # Register before exec: @dataclass resolves cls.__module__ via
        # sys.modules during class creation, which fails if absent.
        sys.modules[modname] = mod
        try:
            spec.loader.exec_module(mod)
        finally:
            sys.modules.pop(modname, None)
        return mod
    finally:
        if prev is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = prev


class TestUploadWorkersParsing(unittest.TestCase):
    def test_default_when_unset(self):
        self.assertEqual(_load_with_env(None).UPLOAD_WORKERS, 8)

    def test_empty_string_falls_back_to_default(self):
        # The bug class: a set-but-empty env line must not crash int('') and
        # must resolve to the built-in default, not 0 or an exception.
        self.assertEqual(_load_with_env("").UPLOAD_WORKERS, 8)

    def test_explicit_value_honored(self):
        self.assertEqual(_load_with_env("12").UPLOAD_WORKERS, 12)

    def test_floored_at_one(self):
        # 0 (or negative) would make ThreadPoolExecutor raise; floor keeps the
        # serial-fallback path reachable instead.
        self.assertEqual(_load_with_env("0").UPLOAD_WORKERS, 1)

    def test_one_is_serial_fallback_threshold(self):
        # UPLOAD_WORKERS <= 1 selects the original serial loop in run_local_mode.
        self.assertEqual(_load_with_env("1").UPLOAD_WORKERS, 1)


if __name__ == "__main__":
    unittest.main()
