#!/usr/bin/env python3
"""
M4I-c — pyShacl validation acceptance test for the m4i DataObject shape.

Walks every DataObject in the LUMEN + MFFD seed Collections on a live
Shepard instance, requests the m4i JSON-LD projection via content
negotiation, runs the response through pyShacl against
`backend/src/main/resources/shapes/m4i-dataobject-shape.ttl`, and
reports per-DataObject pass/fail with a coverage percentage.

This script IS the M4I-c acceptance artefact — it tests against
real Shepard data, not unit-test fixtures. The expected outcome on
the live LUMEN + MFFD demos: 100% pass on the mandatory shape; some
DataObjects may fail the optional triples (e.g. no Activity yet, no
predecessors) — those failures are documented as data-shape gaps,
not renderer bugs.

Usage:

    pip install pyshacl rdflib requests
    python3 validate_m4i_shape.py \\
        --shepard-url https://shepard.nuclide.systems \\
        --api-key $SHEPARD_API_KEY \\
        --collection-id 019e6ffc-89a4-76b5-8dbb-15888646a904  # LUMEN

    python3 validate_m4i_shape.py \\
        --shepard-url https://shepard.nuclide.systems \\
        --api-key $SHEPARD_API_KEY \\
        --collection-id 019e6ff9-2bf7-732c-aa1c-2b504302a1e4  # MFFD

Or both at once:

    python3 validate_m4i_shape.py \\
        --shepard-url https://shepard.nuclide.systems \\
        --api-key $SHEPARD_API_KEY \\
        --collection-id 019e6ffc-89a4-76b5-8dbb-15888646a904 \\
        --collection-id 019e6ff9-2bf7-732c-aa1c-2b504302a1e4

Exit code: 0 when every DataObject passes (or all failures are on
optional triples); 1 when any DataObject fails on a mandatory triple
violation; 2 on a transport / fetch error.

Design source: aidocs/semantics/94 §4.3 (M4I-c acceptance criteria).
Shape source:  backend/src/main/resources/shapes/m4i-dataobject-shape.ttl
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Iterable

try:
    import requests
except ImportError:  # pragma: no cover
    sys.stderr.write("requests not installed — run `pip install requests`\n")
    sys.exit(2)

try:
    from pyshacl import validate
except ImportError:  # pragma: no cover
    sys.stderr.write("pyshacl not installed — run `pip install pyshacl rdflib`\n")
    sys.exit(2)

try:
    from rdflib import Graph
except ImportError:  # pragma: no cover
    sys.stderr.write("rdflib not installed — run `pip install rdflib`\n")
    sys.exit(2)


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_SHAPE = REPO_ROOT / "backend" / "src" / "main" / "resources" / "shapes" / "m4i-dataobject-shape.ttl"

M4I_PROFILE = 'application/ld+json; profile="https://w3id.org/nfdi4ing/metadata4ing/"'

# Properties from the shape that we treat as mandatory in the violation
# triage. Anything else (predecessor, successor, hasIdentifier, etc.) is
# optional per the §4.3 contract.
MANDATORY_PATHS = {
    "http://purl.org/dc/terms/identifier",
    "http://purl.org/dc/terms/title",
    "http://schema.org/dateCreated",
}


def fetch_dos(base: str, headers: dict, collection_id: str) -> list[dict]:
    """List every DataObject under a Collection (paginated)."""
    out: list[dict] = []
    page = 0
    while True:
        url = f"{base}/v2/collections/{collection_id}/data-objects?page={page}&size=200"
        r = requests.get(url, headers=headers, timeout=30)
        if r.status_code != 200:
            sys.stderr.write(
                f"fetch_dos: {url} -> {r.status_code}\n{r.text[:500]}\n"
            )
            sys.exit(2)
        items = r.json()
        if not items:
            return out
        out.extend(items)
        if len(items) < 200:
            return out
        page += 1


def fetch_m4i(base: str, headers: dict, collection_id: str, do_id: str) -> dict:
    """Fetch a single DataObject under the m4i profile."""
    url = f"{base}/v2/collections/{collection_id}/data-objects/{do_id}"
    h = dict(headers)
    h["Accept"] = M4I_PROFILE
    r = requests.get(url, headers=h, timeout=30)
    if r.status_code != 200:
        sys.stderr.write(
            f"fetch_m4i: {url} -> {r.status_code}\n{r.text[:500]}\n"
        )
        raise RuntimeError(f"non-200: {r.status_code}")
    return r.json()


def validate_one(shape_graph: Graph, body: dict) -> tuple[bool, list[dict]]:
    """Validate a single m4i JSON-LD body against the shape.
    Returns (passed, per-violation-records)."""
    data_graph = Graph()
    data_graph.parse(data=json.dumps(body), format="json-ld")
    conforms, _results_graph, results_text = validate(
        data_graph,
        shacl_graph=shape_graph,
        inference="none",
        debug=False,
        meta_shacl=False,
        advanced=True,
        js=False,
    )
    violations: list[dict] = []
    if not conforms:
        # pyshacl reports as plain text; parse the result graph for
        # structured access. The text is the readable summary.
        for line in results_text.splitlines():
            if "Result Path:" in line:
                # We just collect path strings as a coarse triage.
                path = line.split(":", 1)[1].strip()
                violations.append({"path": path})
    return conforms, violations


def classify_violations(violations: Iterable[dict]) -> tuple[int, int]:
    """Split violations into (mandatory_violations, optional_violations)."""
    m, o = 0, 0
    for v in violations:
        p = v.get("path", "")
        if any(mp in p for mp in MANDATORY_PATHS):
            m += 1
        else:
            o += 1
    return m, o


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--shepard-url", required=True, help="Base URL")
    ap.add_argument("--api-key", required=True, help="X-API-KEY value")
    ap.add_argument(
        "--collection-id",
        action="append",
        required=True,
        help="Collection appId (repeat for multiple)",
    )
    ap.add_argument(
        "--shape",
        default=str(DEFAULT_SHAPE),
        help=f"Path to m4i-dataobject-shape.ttl (default: {DEFAULT_SHAPE})",
    )
    ap.add_argument(
        "--limit", type=int, default=0, help="Per-collection DO limit (0 = all)"
    )
    args = ap.parse_args()

    shape_path = Path(args.shape)
    if not shape_path.exists():
        sys.stderr.write(f"shape file not found: {shape_path}\n")
        return 2
    shape_graph = Graph().parse(str(shape_path), format="turtle")

    base = args.shepard_url.rstrip("/")
    headers = {"X-API-KEY": args.api_key, "Accept": "application/json"}

    total = 0
    passed = 0
    mandatory_failures = 0
    optional_failures = 0
    fail_details: list[tuple[str, list[dict]]] = []

    for cid in args.collection_id:
        print(f"\n=== Collection {cid} ===")
        try:
            dos = fetch_dos(base, headers, cid)
        except SystemExit:
            return 2
        if args.limit > 0:
            dos = dos[: args.limit]
        print(f"  {len(dos)} DataObjects")

        for do in dos:
            total += 1
            appid = do.get("appId")
            if not appid:
                continue
            try:
                body = fetch_m4i(base, headers, cid, appid)
            except RuntimeError:
                fail_details.append((appid, [{"path": "TRANSPORT"}]))
                mandatory_failures += 1
                continue
            ok, violations = validate_one(shape_graph, body)
            if ok:
                passed += 1
            else:
                m, o = classify_violations(violations)
                mandatory_failures += 1 if m > 0 else 0
                optional_failures += 1 if (m == 0 and o > 0) else 0
                fail_details.append((appid, violations))

    coverage = 100.0 * passed / total if total else 0.0
    print("\n=== Summary ===")
    print(f"  total DataObjects:     {total}")
    print(f"  passed shape:          {passed}")
    print(f"  failed (mandatory):    {mandatory_failures}")
    print(f"  failed (optional):     {optional_failures}")
    print(f"  shape coverage:        {coverage:.1f}%")

    if fail_details and (mandatory_failures > 0 or args.limit > 0):
        print("\n=== Failure samples (first 10) ===")
        for appid, viols in fail_details[:10]:
            print(f"  {appid}")
            for v in viols[:3]:
                print(f"    - path: {v.get('path')}")

    return 0 if mandatory_failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
