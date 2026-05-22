"""v15.1 — CSV-driven name mapping + dcterms:alternative annotation.

Reluctant Senior trust-G2: cube3 source names like
`tapelaying/Track 244 (Run 30239)` get rewritten to the operator's preferred
taxonomy (e.g. `TR-244-LH2-2026-04`) via `--name-mapping <csv>`. The original
name is preserved as `dcterms:alternative` annotation evidence so source-side
searches still resolve.

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_name_mapping
"""

from __future__ import annotations

import importlib.util
import sys
import tempfile
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

from tests.conftest_stubs import FakeResponse, StubSession, install_stub  # noqa: E402


def _new_client():
    client = mffd_v15.ShepardClient(
        base="https://dest.example.com",
        api_key="test-key",
        bearer_token="",
        ai_agent="claude-opus-4-7; actedOnBehalfOf=fkrebs@nucli.de",
    )
    install_stub(client, StubSession())
    return client


class TestLoadNameMapping(unittest.TestCase):
    """CSV loader: source-name,operator-name; header optional."""

    def _make_csv(self, content: str) -> Path:
        f = tempfile.NamedTemporaryFile(
            mode="w", suffix=".csv", delete=False, encoding="utf-8"
        )
        f.write(content)
        f.close()
        return Path(f.name)

    def test_simple_mapping(self):
        csv = self._make_csv(
            "tapelaying/Track 244 (Run 30239),TR-244-LH2-2026-04\n"
            "bridgewelding/Frame-12,FR-012-2026-05\n"
        )
        m = mffd_v15.load_name_mapping(csv)
        self.assertEqual(m["tapelaying/Track 244 (Run 30239)"],
                         "TR-244-LH2-2026-04")
        self.assertEqual(m["bridgewelding/Frame-12"],
                         "FR-012-2026-05")

    def test_header_row_skipped(self):
        csv = self._make_csv(
            "source,dest\n"
            "Tapelaying-2026-05-22,LumenLayup-2026-05-22\n"
        )
        m = mffd_v15.load_name_mapping(csv)
        self.assertNotIn("source", m)
        self.assertEqual(m["Tapelaying-2026-05-22"],
                         "LumenLayup-2026-05-22")

    def test_blank_lines_and_empty_fields_skipped(self):
        csv = self._make_csv(
            "Tapelaying-A,Renamed-A\n"
            "\n"
            ",empty-source-not-mapped\n"
            "empty-dest-not-mapped,\n"
            "Tapelaying-B,Renamed-B\n"
        )
        m = mffd_v15.load_name_mapping(csv)
        self.assertEqual(len(m), 2)
        self.assertEqual(m["Tapelaying-A"], "Renamed-A")
        self.assertEqual(m["Tapelaying-B"], "Renamed-B")

    def test_no_file_returns_empty(self):
        self.assertEqual(mffd_v15.load_name_mapping(None), {})
        self.assertEqual(
            mffd_v15.load_name_mapping(Path("/tmp/nonexistent-mffd-mapping.csv")),
            {},
        )


class TestAnnotateAlternativeName(unittest.TestCase):
    """When a DO is renamed via mapping, the source name lives on as
    `dcterms:alternative` annotation for source-side search continuity."""

    def test_emits_dcterms_alternative_annotation(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        ok = client.annotate_alternative_name(
            coll_id=515365, do_id=7777,
            original_name="tapelaying/Track 244 (Run 30239)",
            prov_repo_id=10, migration_repo_id=20,
        )
        self.assertTrue(ok)
        last = client._s.last_call()
        self.assertIn("/dataObjects/7777/semanticAnnotations", last.url)
        self.assertEqual(last.json["propertyIRI"],
                         "http://purl.org/dc/terms/alternative")
        # value embeds the original source name (URL-encoded spaces)
        self.assertIn("Track", last.json["valueIRI"])
        # Spaces must be encoded to keep it a valid URI
        self.assertNotIn(" ", last.json["valueIRI"])

    def test_empty_original_name_returns_false_no_call(self):
        client = _new_client()
        ok = client.annotate_alternative_name(
            coll_id=515365, do_id=7777,
            original_name="",
            prov_repo_id=10, migration_repo_id=20,
        )
        self.assertFalse(ok)
        self.assertEqual(client._s.calls, [])


if __name__ == "__main__":
    unittest.main()
