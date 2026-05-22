"""v15.1 — FAIR R1 license + accessRights population on DOs + Collections.

Asserts:
  * create_data_object threads `license` + `access_rights` into POST body
  * create_collection threads `license` + `access_rights` into POST body
  * ensure_dest_do passes license through
  * Annotation helper emits dcterms:license + dcterms:accessRights on collection

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_license_population
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


class TestCreateDataObjectLicensePassThrough(unittest.TestCase):
    """v15.1: license + accessRights land in the create-DO body."""

    def test_create_do_with_license_sets_field_in_body(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1, "name": "x"}))
        client.create_data_object(
            coll_id=515365, name="TR-001",
            license="CC-BY-4.0",
            access_rights="public-with-attribution",
        )
        last = client._s.last_call()
        self.assertEqual(last.json["license"], "CC-BY-4.0")
        self.assertEqual(last.json["accessRights"], "public-with-attribution")

    def test_create_do_without_license_omits_field(self):
        """When no license supplied, field is absent (clean POST)."""
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.create_data_object(coll_id=515365, name="x")
        last = client._s.last_call()
        self.assertNotIn("license", last.json,
            "Without --default-license, field must not appear in body")
        self.assertNotIn("accessRights", last.json,
            "Without --default-access-rights, field must not appear in body")


class TestCreateCollectionLicense(unittest.TestCase):
    """v15.1: collection-level license hydration on create."""

    def test_create_collection_with_license_sets_fields(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 99, "appId": "abc"}))
        client.create_collection(
            "MFFD-Dropbox",
            description="MFFD dropbox",
            attrs={"domain": "MFFD"},
            license="proprietary",
            access_rights="restricted access",
        )
        last = client._s.last_call()
        self.assertEqual(last.json["license"], "proprietary")
        self.assertEqual(last.json["accessRights"], "restricted access")


class TestEnsureDestDoThreadsLicense(unittest.TestCase):
    """ensure_dest_do is the helper SOURCE-mode uses for the step DO — must
    thread license through to create_data_object."""

    def test_ensure_dest_do_threads_license_when_creating(self):
        client = _new_client()
        # First call: find returns empty list (no existing DO).
        client._s.enqueue("GET", "/dataObjects", FakeResponse(200, []))
        # Second call: POST create returns the new DO.
        client._s.enqueue("POST", "/dataObjects", FakeResponse(201, {
            "id": 555, "name": "Tapelaying-test", "appId": "do-app-id"
        }))
        do = mffd_v15.ensure_dest_do(
            client,
            coll_id=515365,
            name="Tapelaying-test",
            description="step DO",
            attrs={"campaign": "MFFD"},
            license="proprietary",
            access_rights="restricted access",
        )
        self.assertIsNotNone(do)
        # The POST must carry license fields
        post_calls = [c for c in client._s.calls if c.method == "POST"]
        self.assertEqual(len(post_calls), 1)
        self.assertEqual(post_calls[0].json["license"], "proprietary")
        self.assertEqual(post_calls[0].json["accessRights"], "restricted access")


class TestLicenseIriBuilder(unittest.TestCase):
    """license_iri must map SPDX ids to spdx.org IRIs; non-SPDX to URN."""

    def test_spdx_id_yields_spdx_iri(self):
        self.assertEqual(
            mffd_v15.license_iri("CC-BY-4.0"),
            "https://spdx.org/licenses/CC-BY-4.0",
        )
        self.assertEqual(
            mffd_v15.license_iri("Apache-2.0"),
            "https://spdx.org/licenses/Apache-2.0",
        )

    def test_proprietary_yields_spdx_form_too(self):
        # "proprietary" is alphanumeric — also gets spdx.org treatment
        # (the SPDX exception list includes things like LicenseRef-…)
        out = mffd_v15.license_iri("proprietary")
        self.assertEqual(out, "https://spdx.org/licenses/proprietary")

    def test_freeform_with_spaces_falls_to_urn(self):
        out = mffd_v15.license_iri("My License v1.0")
        self.assertTrue(out.startswith("urn:license:"),
            f"freeform license should fall back to URN, got {out!r}")

    def test_empty_yields_empty(self):
        self.assertEqual(mffd_v15.license_iri(""), "")
        self.assertEqual(mffd_v15.license_iri(None), "")  # type: ignore[arg-type]


class TestAnnotateCollectionLicense(unittest.TestCase):
    """v15.1: dcterms:license + dcterms:accessRights mirror as Collection
    semantic annotations (FAIR R1.1 — machine-readable in SHACL graph)."""

    def test_emits_both_annotations_when_both_supplied(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        n = client.annotate_collection_license(
            coll_id=515365,
            license_id="CC-BY-4.0",
            access_rights_id="public-with-attribution",
            dcterms_repo_id=40, value_repo_id=50,
        )
        self.assertEqual(n, 2)
        # Both calls must hit Collection-anchored semanticAnnotations
        anno_calls = client._s.calls_to(
            "/collections/515365/semanticAnnotations"
        )
        self.assertEqual(len(anno_calls), 2)
        predicates = {c.json["propertyIRI"] for c in anno_calls}
        self.assertIn("http://purl.org/dc/terms/license", predicates)
        self.assertIn("http://purl.org/dc/terms/accessRights", predicates)

    def test_license_value_is_spdx_iri(self):
        client = _new_client()
        client._s.set_default(FakeResponse(201, {"id": 1}))
        client.annotate_collection_license(
            coll_id=515365,
            license_id="CC-BY-4.0",
            access_rights_id=None,
            dcterms_repo_id=40, value_repo_id=50,
        )
        lic_calls = [c for c in client._s.calls
                     if c.json and c.json.get("propertyIRI") ==
                     "http://purl.org/dc/terms/license"]
        self.assertEqual(len(lic_calls), 1)
        self.assertEqual(lic_calls[0].json["valueIRI"],
                         "https://spdx.org/licenses/CC-BY-4.0")


if __name__ == "__main__":
    unittest.main()
