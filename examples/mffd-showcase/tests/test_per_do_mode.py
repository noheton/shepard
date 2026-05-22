"""v15.1 G2 + G8 — per-DO F(AI)²R modeOfProduction + wasAcceptedAs.

EU AI Act Art. 50 (effective 2026-08-02) requires per-artefact machine-readable
AI marking. v14/v15 emitted batch-level only — v15.1 brings it down to per-DO
visibility via three annotations:

  1. <do> fair2r:modeOfProduction       fair2r:mode/ai
  2. <do> fair2r:wasAcceptedAs          fair2r:auto-applied
  3. <do> fair2r:wasGeneratedByAi       agent:claude-opus-4-7

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_per_do_mode
"""

from __future__ import annotations

import importlib.util
import sys
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


class TestFair2rNamespaceConstants(unittest.TestCase):
    """Vendor-namespace IRIs are fixed — never mint per-instance.

    Source: https://noheton.org/f-ai-r/ (vendored at TPL9a, see
    backend/src/main/resources/shapes/fair2r-shapes.ttl).
    """

    def test_vendor_ns_canonical(self):
        self.assertEqual(mffd_v15.Fair2r.NS,
                         "https://noheton.org/f-ai-r/ns#")
        self.assertEqual(mffd_v15.Fair2r.VERIF_NS,
                         "https://noheton.org/f-ai-r/ns/verif#")
        self.assertEqual(mffd_v15.Fair2r.AGENT_NS,
                         "https://noheton.org/f-ai-r/agent/")

    def test_predicate_iris_built_from_canonical_ns(self):
        self.assertEqual(mffd_v15.Fair2r.MODE_OF_PRODUCTION,
                         "https://noheton.org/f-ai-r/ns#modeOfProduction")
        self.assertEqual(mffd_v15.Fair2r.WAS_ACCEPTED_AS,
                         "https://noheton.org/f-ai-r/ns#wasAcceptedAs")
        self.assertEqual(mffd_v15.Fair2r.WAS_GENERATED_BY_AI,
                         "https://noheton.org/f-ai-r/ns#wasGeneratedByAi")

    def test_canonical_agent_iri_for_claude(self):
        """The vendor IRI for Claude — must match what fair2r-shapes.ttl
        declares (line 28: `fair2r:AIAgent rdfs:subClassOf prov:SoftwareAgent`)
        and what the OECD AI Act Art-50 marker expects."""
        self.assertEqual(mffd_v15.Fair2r.AGENT_CLAUDE_OPUS_4_7,
                         "https://noheton.org/f-ai-r/agent/claude-opus-4-7")


class TestAnnotateDoModeOfProduction(unittest.TestCase):
    """G2 + G8: per-DO F(AI)²R triples emit on every step DO creation."""

    def test_emits_three_annotations_per_do(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        n = client.annotate_do_mode_of_production(
            coll_id=515365, do_id=7777,
            prov_repo_id=10, fair2r_repo_id=30,
        )
        self.assertEqual(n, 3,
            "v15.1 G2+G8: must emit exactly 3 fair2r triples (modeOfProduction, wasAcceptedAs, wasGeneratedByAi)")

    def test_all_three_predicates_present_in_calls(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.annotate_do_mode_of_production(
            coll_id=515365, do_id=7777,
            prov_repo_id=10, fair2r_repo_id=30,
        )
        anno_calls = client._s.calls_to(
            "/dataObjects/7777/semanticAnnotations"
        )
        self.assertEqual(len(anno_calls), 3)
        predicates = {c.json["propertyIRI"] for c in anno_calls}
        self.assertIn("https://noheton.org/f-ai-r/ns#modeOfProduction", predicates)
        self.assertIn("https://noheton.org/f-ai-r/ns#wasAcceptedAs", predicates)
        self.assertIn("https://noheton.org/f-ai-r/ns#wasGeneratedByAi", predicates)

    def test_mode_value_is_ai(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.annotate_do_mode_of_production(
            coll_id=515365, do_id=7777,
            prov_repo_id=10, fair2r_repo_id=30,
        )
        mode_calls = [c for c in client._s.calls
                      if c.json and c.json.get("propertyIRI") ==
                      "https://noheton.org/f-ai-r/ns#modeOfProduction"]
        self.assertEqual(len(mode_calls), 1)
        self.assertEqual(mode_calls[0].json["valueIRI"],
                         "https://noheton.org/f-ai-r/ns#mode/ai")

    def test_acceptance_value_is_auto_applied(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.annotate_do_mode_of_production(
            coll_id=515365, do_id=7777,
            prov_repo_id=10, fair2r_repo_id=30,
        )
        accept_calls = [c for c in client._s.calls
                        if c.json and c.json.get("propertyIRI") ==
                        "https://noheton.org/f-ai-r/ns#wasAcceptedAs"]
        self.assertEqual(len(accept_calls), 1)
        # Value must end with 'auto-applied' — the acceptance-ladder rung for
        # AI agents acting without per-DO human review (per
        # project_ai_human_collab_provenance.md acceptance ladder table)
        self.assertTrue(accept_calls[0].json["valueIRI"].endswith("auto-applied"),
            f"acceptance value must end with 'auto-applied', got {accept_calls[0].json['valueIRI']!r}")
        # Specifically: must be the vendor `verif#auto-applied` IRI
        self.assertEqual(accept_calls[0].json["valueIRI"],
                         "https://noheton.org/f-ai-r/ns/verif#auto-applied")

    def test_agent_iri_is_canonical_claude(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.annotate_do_mode_of_production(
            coll_id=515365, do_id=7777,
            prov_repo_id=10, fair2r_repo_id=30,
        )
        agent_calls = [c for c in client._s.calls
                       if c.json and c.json.get("propertyIRI") ==
                       "https://noheton.org/f-ai-r/ns#wasGeneratedByAi"]
        self.assertEqual(len(agent_calls), 1)
        self.assertEqual(agent_calls[0].json["valueIRI"],
                         "https://noheton.org/f-ai-r/agent/claude-opus-4-7")


if __name__ == "__main__":
    unittest.main()
