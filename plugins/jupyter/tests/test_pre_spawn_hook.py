"""Unit tests for the J1e-PR-06-AUTOFETCH-01 pre-spawn helpers.

The hook itself (`_pre_spawn_hook`) needs a live JupyterHub spawner +
docker daemon to exercise meaningfully — those paths are validated by
the make-redeploy smoke test. These unit tests cover the pure helpers
the hook delegates to: URL allowlist validation, filename derivation +
sanitization, and README rendering. Together they form the SSRF defense
and the user-facing failure-surfacing contract.

Run from the repo root:

    python3 -m unittest plugins.jupyter.tests.test_pre_spawn_hook -v
"""

from __future__ import annotations

import importlib.util
import os
import sys
import types
import unittest
from datetime import datetime, timezone


def _load_module():
    """Load jupyterhub_config.py as a plain module so we can import its
    helpers without needing JH's `get_config` injection. We stub the
    `get_config` builtin + the `os.environ` keys the module reads at
    import time, then strip out the global config assignments after."""
    path = os.path.join(
        os.path.dirname(__file__),
        "..",
        "config",
        "jupyterhub_config.py",
    )
    src = open(path, encoding="utf-8").read()
    # Stub the env vars the module reads at import (OIDC issuer etc.) so
    # we don't trip KeyError before reaching the helpers we want to test.
    os.environ.setdefault("SHEPARD_OIDC_ISSUER_URL", "https://example/realm")
    os.environ.setdefault("JUPYTERHUB_KEYCLOAK_CLIENT_ID", "jupyterhub")
    os.environ.setdefault("JUPYTERHUB_KEYCLOAK_CLIENT_SECRET", "stub")
    module = types.ModuleType("jupyterhub_config_under_test")
    module.__file__ = path

    class _StubConfig:
        def __getattr__(self, name):
            return self

        def __setattr__(self, name, value):
            pass

    module.__dict__["get_config"] = lambda: _StubConfig()
    exec(compile(src, path, "exec"), module.__dict__)
    return module


CONFIG = _load_module()


class TestAllowlist(unittest.TestCase):
    ALLOWED = {"shepard.nuclide.systems", "shepard-api.nuclide.systems"}

    def test_allowed_host_passes(self):
        self.assertTrue(
            CONFIG.is_url_allowed(
                "https://shepard-api.nuclide.systems/v2/files/abc", self.ALLOWED
            )
        )

    def test_disallowed_host_blocked(self):
        self.assertFalse(
            CONFIG.is_url_allowed("https://evil.example.com/payload.bin", self.ALLOWED)
        )

    def test_non_http_scheme_blocked(self):
        # file:// and javascript: must never reach the fetcher.
        self.assertFalse(
            CONFIG.is_url_allowed("file:///etc/passwd", self.ALLOWED)
        )
        self.assertFalse(
            CONFIG.is_url_allowed("javascript:alert(1)", self.ALLOWED)
        )

    def test_empty_or_garbage_blocked(self):
        self.assertFalse(CONFIG.is_url_allowed("", self.ALLOWED))
        self.assertFalse(CONFIG.is_url_allowed(None, self.ALLOWED))  # type: ignore[arg-type]
        self.assertFalse(CONFIG.is_url_allowed("not a url at all", self.ALLOWED))


class TestFilenameDerivation(unittest.TestCase):
    def test_content_disposition_plain(self):
        self.assertEqual(
            CONFIG.derive_filename(
                "https://example/x",
                'attachment; filename="report.pdf"',
            ),
            "report.pdf",
        )

    def test_content_disposition_rfc5987(self):
        self.assertEqual(
            CONFIG.derive_filename(
                "https://example/x",
                "attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf",
            ),
            "résumé.pdf",
        )

    def test_falls_back_to_url_path(self):
        self.assertEqual(
            CONFIG.derive_filename(
                "https://shepard-api.nuclide.systems/v2/files/abc/payload/report.csv",
                None,
            ),
            "report.csv",
        )

    def test_falls_back_to_default_when_path_empty(self):
        self.assertEqual(
            CONFIG.derive_filename("https://shepard-api.nuclide.systems/", None),
            "download.bin",
        )


class TestFilenameSanitization(unittest.TestCase):
    def test_path_traversal_stripped(self):
        self.assertEqual(
            CONFIG.sanitize_filename("../../etc/passwd"), "passwd"
        )

    def test_leading_dots_stripped(self):
        # Hidden-file dotnames would let an upload occupy a name the
        # kernel won't display by default. Strip + fall back.
        self.assertEqual(CONFIG.sanitize_filename("..."), "download.bin")

    def test_max_length_enforced(self):
        long_name = ("a" * 400) + ".bin"
        result = CONFIG.sanitize_filename(long_name)
        self.assertLessEqual(len(result), 255)
        self.assertTrue(result.endswith(".bin"))

    def test_empty_becomes_default(self):
        self.assertEqual(CONFIG.sanitize_filename(""), "download.bin")
        self.assertEqual(CONFIG.sanitize_filename("/"), "download.bin")


class TestReadmeRendering(unittest.TestCase):
    FIXED_TS = datetime(2026, 5, 29, 12, 0, 0, tzinfo=timezone.utc)

    def test_ok_status(self):
        out = CONFIG.render_readme(
            "report.pdf",
            "https://shepard-api.nuclide.systems/v2/files/abc",
            "OK",
            fetched_at=self.FIXED_TS,
        )
        self.assertIn("# Shepard import — report.pdf", out)
        self.assertIn("- Status: OK", out)
        self.assertIn("2026-05-29T12:00:00+00:00", out)
        self.assertIn("do NOT write back to Shepard", out)

    def test_allowlist_miss_status_documented(self):
        out = CONFIG.render_readme(
            "payload.bin",
            "https://evil.example.com/payload.bin",
            "allowlist-miss",
            fetched_at=self.FIXED_TS,
        )
        self.assertIn("- Status: allowlist-miss", out)
        self.assertIn("https://evil.example.com/payload.bin", out)


if __name__ == "__main__":
    unittest.main()
