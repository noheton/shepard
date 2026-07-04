#!/usr/bin/env python3
"""
Validate a MFFD process-chain mapping YAML file against a live Shepard
instance. Reports unresolved selectors per entry without mutating
anything; only structural YAML errors fail the exit code.

See aidocs/integrations/118-mffd-process-chain-mapping.md for the
schema. Cross-references: MFFD-AF-TRACK-MAPPING-1 in aidocs/16.

Exit codes:
  0 — YAML parsed, all selectors resolve >=1 DataObject.
  0 — YAML parsed, some selectors unresolved (printed as a checklist).
  2 — YAML malformed or schemaVersion not supported.
  3 — REST surface unreachable / authentication failed.

Example:
  python3 scripts/validate-mffd-process-chain-mapping.py \\
      --url https://shepard.example.org/v2 \\
      --api-key dev-key \\
      scripts/mffd-process-chain-mapping.example.yaml
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, field
from typing import Any
from urllib import error, parse, request

import yaml

# YAML schema version this script understands. Bumped only when the
# selector vocabulary changes shape.
SUPPORTED_SCHEMA_VERSION = 1

# Canonical YAML-key → predicate-URI mapping. Any other foo_bar key is
# normalised to "urn:shepard:mffd:foo-bar" automatically.
EXPLICIT_PREDICATE_MAP = {
    "process": "urn:shepard:mffd:process-type",
    "step_number": "urn:shepard:mffd:step-number",
    "ply_number": "urn:shepard:mffd:ply-number",
    "track_number": "urn:shepard:mffd:track-number",
    "part_name": "urn:shepard:mffd:part-name",
    "campaign_id": "urn:shepard:mffd:campaign-id",
    "cleat_id": "urn:shepard:mffd:cleat-id",
}

VALID_TRANSITION_KINDS = {"normal", "rework", "re-test", "concession"}


@dataclass
class UnresolvedSelector:
    """A YAML selector that did not match any DataObject."""

    line: int
    side: str  # "source" or "target"
    selector: dict[str, Any]
    reason: str


@dataclass
class ValidationReport:
    """Top-level outcome of a validation run."""

    entries: int = 0
    resolved: int = 0
    unresolved: list[UnresolvedSelector] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def all_resolved(self) -> bool:
        return not self.unresolved


def yaml_key_to_predicate(key: str) -> str:
    """Map a YAML selector key to its urn:shepard:mffd:* predicate IRI.

    Explicit entries in EXPLICIT_PREDICATE_MAP win. Anything else is
    normalised by hyphenating underscores under the mffd namespace, so
    new predicates require no code change here.
    """
    if key in EXPLICIT_PREDICATE_MAP:
        return EXPLICIT_PREDICATE_MAP[key]
    return "urn:shepard:mffd:" + key.replace("_", "-")


def parse_yaml(text: str) -> dict[str, Any]:
    """Parse the YAML text. Raises on malformed YAML."""
    try:
        doc = yaml.safe_load(text)
    except yaml.YAMLError as exc:
        raise SystemExit(f"YAML parse error: {exc}") from exc

    if not isinstance(doc, dict):
        raise SystemExit("Top-level YAML must be a mapping.")
    sv = doc.get("schemaVersion")
    if sv != SUPPORTED_SCHEMA_VERSION:
        raise SystemExit(
            f"Unsupported schemaVersion={sv!r} "
            f"(this script supports {SUPPORTED_SCHEMA_VERSION})."
        )
    mappings = doc.get("mappings")
    if not isinstance(mappings, list):
        raise SystemExit("'mappings' must be a list.")
    return doc


def collect_line_numbers(text: str) -> list[int]:
    """Best-effort recovery of one line number per mapping entry.

    PyYAML's safe_load discards line info; we re-parse with the lower
    level Loader to recover anchor positions. Falls back to indexes if
    the lower-level parse fails.
    """
    try:
        loader = yaml.SafeLoader(text)
        try:
            node = loader.get_single_node()
        finally:
            loader.dispose()
        if node is None:
            return []
        # Find the 'mappings' sequence node.
        for key_node, value_node in getattr(node, "value", []):
            if (
                getattr(key_node, "value", None) == "mappings"
                and getattr(value_node, "id", None) == "sequence"
            ):
                # SequenceNode children's start_mark carries the line.
                return [child.start_mark.line + 1 for child in value_node.value]
    except yaml.YAMLError:
        pass
    return []


class ShepardClient:
    """Minimal Shepard /v2 REST client for the validator."""

    def __init__(self, base_url: str, api_key: str | None, timeout: float = 30.0):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.timeout = timeout

    def list_data_objects_by_annotations(
        self,
        annotations: dict[str, str],
    ) -> list[dict[str, Any]]:
        """Return DataObjects matching all (predicate, value) pairs.

        Calls GET /v2/data-objects?annotation=<predicate>:<value>&...
        which is the canonical filter endpoint. Each predicate-value
        pair is sent as a separate `annotation` query parameter.
        """
        if not annotations:
            return []

        query_parts = [
            ("annotation", f"{predicate}:{value}")
            for predicate, value in annotations.items()
        ]
        url = (
            self.base_url
            + "/data-objects?"
            + parse.urlencode(query_parts)
        )
        req = request.Request(url, method="GET")
        req.add_header("Accept", "application/json")
        if self.api_key:
            req.add_header("X-API-Key", self.api_key)
        try:
            with request.urlopen(req, timeout=self.timeout) as resp:
                body = resp.read().decode("utf-8")
        except error.HTTPError as exc:
            if exc.code in (401, 403):
                raise SystemExit(
                    f"Authentication failed ({exc.code}). "
                    "Pass --api-key or set SHEPARD_API_KEY."
                ) from exc
            raise SystemExit(
                f"HTTP {exc.code} on {url}: {exc.reason}"
            ) from exc
        except error.URLError as exc:
            raise SystemExit(
                f"Could not reach {self.base_url}: {exc.reason}"
            ) from exc

        try:
            payload = json.loads(body)
        except json.JSONDecodeError as exc:
            raise SystemExit(f"Non-JSON response from {url}: {exc}") from exc

        # The endpoint may return either a plain list or a paginated
        # envelope ({items, total}). Handle both.
        if isinstance(payload, list):
            return payload
        if isinstance(payload, dict) and isinstance(payload.get("items"), list):
            return payload["items"]
        return []


def validate(
    doc: dict[str, Any],
    line_numbers: list[int],
    client: ShepardClient | None,
) -> ValidationReport:
    """Run the validation pass over the parsed YAML.

    When `client` is None, the validator runs in offline mode: it only
    checks structural shape (selectors non-empty, transitionKind in
    vocabulary) without hitting the REST surface.
    """
    report = ValidationReport()
    for idx, entry in enumerate(doc["mappings"]):
        report.entries += 1
        line = line_numbers[idx] if idx < len(line_numbers) else (idx + 1)

        if not isinstance(entry, dict):
            report.unresolved.append(
                UnresolvedSelector(line, "entry", {}, "Entry is not a mapping.")
            )
            continue

        source = entry.get("source")
        target = entry.get("target")
        kind = entry.get("transitionKind", "normal")

        if kind not in VALID_TRANSITION_KINDS:
            report.warnings.append(
                f"line {line}: transitionKind={kind!r} not in {sorted(VALID_TRANSITION_KINDS)}"
            )

        for side_name, selector in (("source", source), ("target", target)):
            if not isinstance(selector, dict) or not selector:
                report.unresolved.append(
                    UnresolvedSelector(
                        line, side_name, selector or {}, "Empty or non-mapping selector."
                    )
                )
                continue
            if client is None:
                # Offline mode: structural validation only.
                continue
            annotations = {
                yaml_key_to_predicate(k): str(v) for k, v in selector.items()
            }
            matches = client.list_data_objects_by_annotations(annotations)
            if not matches:
                report.unresolved.append(
                    UnresolvedSelector(
                        line,
                        side_name,
                        selector,
                        "No DataObjects match all selector predicates.",
                    )
                )

        if not any(
            u for u in report.unresolved if u.line == line
        ):
            report.resolved += 1

    return report


def render_report(report: ValidationReport, *, json_out: bool) -> str:
    if json_out:
        return json.dumps(
            {
                "entries": report.entries,
                "resolved": report.resolved,
                "unresolved": [
                    {
                        "line": u.line,
                        "side": u.side,
                        "selector": u.selector,
                        "reason": u.reason,
                    }
                    for u in report.unresolved
                ],
                "warnings": report.warnings,
            },
            indent=2,
        )
    out: list[str] = []
    out.append(
        f"Validated {report.entries} mapping(s); "
        f"{report.resolved} fully resolved, "
        f"{len(report.unresolved)} selector(s) unresolved."
    )
    if report.warnings:
        out.append("\nWarnings:")
        for w in report.warnings:
            out.append(f"  - {w}")
    if report.unresolved:
        out.append("\nUnresolved checklist (for the author to investigate):")
        for u in report.unresolved:
            sel = ", ".join(f"{k}={v!r}" for k, v in u.selector.items())
            out.append(f"  - line {u.line} [{u.side}]: {sel}  ({u.reason})")
    return "\n".join(out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Validate a MFFD process-chain mapping YAML against a live Shepard "
            "instance. See aidocs/integrations/118."
        )
    )
    parser.add_argument("yaml_file", help="Path to the mapping YAML file.")
    parser.add_argument(
        "--url",
        default=os.environ.get("SHEPARD_URL"),
        help="Shepard /v2 base URL (e.g. https://shepard.example.org/v2). "
        "Falls back to $SHEPARD_URL. Omit to run in offline mode.",
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("SHEPARD_API_KEY"),
        help="X-API-Key header value. Falls back to $SHEPARD_API_KEY.",
    )
    parser.add_argument(
        "--output",
        choices=("human", "json"),
        default="human",
        help="Output format (default: human).",
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Skip REST calls; structural validation only.",
    )
    args = parser.parse_args(argv)

    try:
        text = open(args.yaml_file, encoding="utf-8").read()
    except OSError as exc:
        print(f"Could not read {args.yaml_file}: {exc}", file=sys.stderr)
        return 2

    try:
        doc = parse_yaml(text)
    except SystemExit as exc:
        # parse_yaml raises SystemExit(msg) on YAML errors — convert to exit code 2.
        print(str(exc), file=sys.stderr)
        return 2

    client: ShepardClient | None = None
    if not args.offline and args.url:
        client = ShepardClient(args.url, args.api_key)

    line_numbers = collect_line_numbers(text)
    try:
        report = validate(doc, line_numbers, client)
    except SystemExit as exc:
        print(str(exc), file=sys.stderr)
        return 3

    print(render_report(report, json_out=args.output == "json"))
    # Unresolved selectors are NOT a non-zero exit — checklist is the
    # deliverable. Only structural YAML errors fail.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
