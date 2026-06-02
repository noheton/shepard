"""Tests for scripts/validate-mffd-process-chain-mapping.py.

MFFD-AF-TRACK-MAPPING-1 — validation script test suite.

Covers:
  * happy path: well-formed YAML, all selectors resolve in offline mode.
  * malformed YAML: non-zero exit.
  * unresolved selectors when REST mock returns empty: exit 0 + checklist.
  * unsupported schemaVersion: exit 2.
  * predicate-key normalisation (foo_bar → urn:shepard:mffd:foo-bar).
  * transitionKind vocabulary: invalid value surfaces as a warning.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

# Load the script as a module — its filename has hyphens so a plain
# `import` won't work. Resolves relative to this test file.
SCRIPTS_DIR = Path(__file__).resolve().parent.parent
SCRIPT_PATH = SCRIPTS_DIR / "validate-mffd-process-chain-mapping.py"
EXAMPLE_YAML = SCRIPTS_DIR / "mffd-process-chain-mapping.example.yaml"

spec = importlib.util.spec_from_file_location("validate_mapping", SCRIPT_PATH)
assert spec is not None and spec.loader is not None
validate_mapping = importlib.util.module_from_spec(spec)
sys.modules["validate_mapping"] = validate_mapping
spec.loader.exec_module(validate_mapping)


# ─── pure-helper tests ──────────────────────────────────────────────────────

def test_yaml_key_to_predicate_explicit_keys():
    assert validate_mapping.yaml_key_to_predicate("process") == "urn:shepard:mffd:process-type"
    assert validate_mapping.yaml_key_to_predicate("track_number") == "urn:shepard:mffd:track-number"
    assert validate_mapping.yaml_key_to_predicate("ply_number") == "urn:shepard:mffd:ply-number"
    assert validate_mapping.yaml_key_to_predicate("part_name") == "urn:shepard:mffd:part-name"


def test_yaml_key_to_predicate_unknown_key_normalises():
    # Falls back to "urn:shepard:mffd:" + hyphenated key.
    assert validate_mapping.yaml_key_to_predicate("foo_bar") == "urn:shepard:mffd:foo-bar"
    assert validate_mapping.yaml_key_to_predicate("station_id") == "urn:shepard:mffd:station-id"


# ─── parse_yaml tests ───────────────────────────────────────────────────────

def test_parse_yaml_happy_path():
    text = (
        "schemaVersion: 1\n"
        "mappings:\n"
        "  - source: {process: afp-layup}\n"
        "    target: {process: bridge-welding}\n"
        "    transitionKind: normal\n"
    )
    doc = validate_mapping.parse_yaml(text)
    assert doc["schemaVersion"] == 1
    assert len(doc["mappings"]) == 1


def test_parse_yaml_unsupported_schema_version_raises():
    text = "schemaVersion: 99\nmappings: []\n"
    with pytest.raises(SystemExit) as exc:
        validate_mapping.parse_yaml(text)
    assert "Unsupported schemaVersion" in str(exc.value)


def test_parse_yaml_malformed_yaml_raises():
    text = "this is: not: valid: yaml:\n  - {unbalanced"
    with pytest.raises(SystemExit) as exc:
        validate_mapping.parse_yaml(text)
    assert "YAML parse error" in str(exc.value)


def test_parse_yaml_mappings_not_a_list_raises():
    text = "schemaVersion: 1\nmappings: notalist\n"
    with pytest.raises(SystemExit):
        validate_mapping.parse_yaml(text)


def test_parse_yaml_top_level_not_mapping_raises():
    text = "- just\n- a\n- list\n"
    with pytest.raises(SystemExit):
        validate_mapping.parse_yaml(text)


# ─── validate() tests ───────────────────────────────────────────────────────

def test_validate_offline_well_formed():
    text = EXAMPLE_YAML.read_text(encoding="utf-8")
    doc = validate_mapping.parse_yaml(text)
    line_numbers = validate_mapping.collect_line_numbers(text)
    report = validate_mapping.validate(doc, line_numbers, client=None)
    assert report.entries == 15
    assert report.resolved == 15
    assert not report.unresolved


def test_validate_offline_flags_invalid_transition_kind_as_warning():
    text = (
        "schemaVersion: 1\n"
        "mappings:\n"
        "  - source: {process: a}\n"
        "    target: {process: b}\n"
        "    transitionKind: bogus\n"
    )
    doc = validate_mapping.parse_yaml(text)
    report = validate_mapping.validate(doc, [], client=None)
    assert len(report.warnings) == 1
    assert "bogus" in report.warnings[0]


def test_validate_offline_empty_selector_flags_unresolved():
    text = (
        "schemaVersion: 1\n"
        "mappings:\n"
        "  - source: {}\n"
        "    target: {process: bridge-welding}\n"
    )
    doc = validate_mapping.parse_yaml(text)
    report = validate_mapping.validate(doc, [], client=None)
    assert any(u.side == "source" for u in report.unresolved)


def test_validate_with_mock_client_reports_unresolved():
    """When the REST client returns no matches, those selectors are unresolved."""

    class MockClient:
        def list_data_objects_by_annotations(self, annotations):  # noqa: D401
            return []  # always empty

    text = (
        "schemaVersion: 1\n"
        "mappings:\n"
        "  - source: {process: afp-layup, track_number: 1}\n"
        "    target: {process: bridge-welding, part_name: AF_3}\n"
    )
    doc = validate_mapping.parse_yaml(text)
    report = validate_mapping.validate(doc, [3], client=MockClient())
    # Both source and target unresolved → 2 entries in the checklist.
    assert len(report.unresolved) == 2
    assert all(u.line == 3 for u in report.unresolved)


def test_validate_with_mock_client_reports_resolved():
    """When the REST client returns matches, no unresolved selectors."""

    class MockClient:
        def list_data_objects_by_annotations(self, annotations):  # noqa: D401
            return [{"appId": "abc-123", "name": "match"}]

    text = (
        "schemaVersion: 1\n"
        "mappings:\n"
        "  - source: {process: afp-layup, track_number: 1}\n"
        "    target: {process: bridge-welding, part_name: AF_3}\n"
    )
    doc = validate_mapping.parse_yaml(text)
    report = validate_mapping.validate(doc, [3], client=MockClient())
    assert not report.unresolved
    assert report.resolved == 1


# ─── render_report tests ────────────────────────────────────────────────────

def test_render_report_human_output_lists_unresolved():
    report = validate_mapping.ValidationReport(
        entries=2,
        resolved=1,
        unresolved=[
            validate_mapping.UnresolvedSelector(
                line=42,
                side="source",
                selector={"process": "afp-layup", "track_number": 999},
                reason="No matches.",
            )
        ],
    )
    text = validate_mapping.render_report(report, json_out=False)
    assert "line 42" in text
    assert "track_number=999" in text


def test_render_report_json_output_is_parseable():
    import json

    report = validate_mapping.ValidationReport(entries=1, resolved=1)
    out = validate_mapping.render_report(report, json_out=True)
    payload = json.loads(out)
    assert payload["entries"] == 1
    assert payload["resolved"] == 1
    assert payload["unresolved"] == []


# ─── main() entrypoint tests ────────────────────────────────────────────────

def test_main_example_yaml_offline_exits_zero(capsys):
    rc = validate_mapping.main(["--offline", str(EXAMPLE_YAML)])
    captured = capsys.readouterr()
    assert rc == 0
    assert "Validated 15 mapping(s)" in captured.out


def test_main_missing_file_exits_2():
    rc = validate_mapping.main(["--offline", "/nonexistent/path/file.yaml"])
    assert rc == 2


def test_main_malformed_yaml_exits_2(tmp_path, capsys):
    bad = tmp_path / "bad.yaml"
    bad.write_text("schemaVersion: 1\nmappings:\n  - {unbalanced: yaml: nope")
    rc = validate_mapping.main(["--offline", str(bad)])
    assert rc == 2
