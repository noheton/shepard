"""BT-KVS controlled-vocab seed (BTKVS-A2).

Reads the ``fiber_database`` + ``fabric_database`` Python dicts shipped in
``laufzettel-readout/src/material_database.py`` (operator-supplied,
lives at ``/tmp/nils-cc-csic-showcase/_unpacked/laufzettel-readout-main/src/``
per ``feedback_uploads_never_in_repo.md`` — never copied into this
repo), generates an in-memory Turtle ontology bundle, and multipart-
uploads it via the N1c2 admin endpoint::

    POST /v2/admin/semantic/ontologies
        file=<TTL bytes>
        metadata={"id":"btkvs-materials", ...}

Every fiber individual lands as ``urn:btkvs:fiber:<KEY>`` (e.g.
``urn:btkvs:fiber:HTA``, ``urn:btkvs:fiber:T800``) typed as
``urn:btkvs:Fiber``.  Every fabric individual lands as
``urn:btkvs:fabric:<KEY>`` (e.g. ``urn:btkvs:fabric:98140``,
``urn:btkvs:fabric:Style_840``) typed as ``urn:btkvs:Fabric``.  Material
properties (density, diameter, weave, manufacturer, …) ride as
datatype-property triples on the individual, with predicates under
``urn:btkvs:material:<property>``.

Idempotent: the bundle id is fixed (``btkvs-materials``).  On first
run the upload succeeds with ``201 Created``; on subsequent runs the
admin endpoint replies ``409 Conflict`` (``semantic.bundle.duplicate``)
which this script swallows as a SKIP.  ``--reset`` DELETEs the bundle
first.

Usage::

    python3 seed_vocab.py --host https://shepard-api.nuclide.systems \\
                          --apikey <token> \\
                          [--source /path/to/laufzettel-readout-main/src]

The default ``--source`` is the operator-uploaded location at
``/tmp/nils-cc-csic-showcase/_unpacked/laufzettel-readout-main/src``.

NOTE on consumption (see SHOWCASE.md §"Open questions"): the N1c2
ontology loader unconditionally ingests datatype triples into the
SPARQL endpoint, but the term-search UI may today index only
``rdfs:label`` strings on individuals.  This seed gives every
individual both ``a urn:btkvs:Fiber`` (class membership) and an
``rdfs:label`` so it shows up in semantic-term search regardless;
the datatype-property triples (density, area_density, …) are then
queryable via SPARQL even when the search UI ignores them.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any


BUNDLE_ID = "btkvs-materials"
BUNDLE_NAME = "BT-KVS Materials (fibers + fabrics)"
BUNDLE_IRI_PREFIX = "urn:btkvs:"
BUNDLE_CANONICAL_URL = "urn:btkvs:"
BUNDLE_LICENSE = "CC-BY-4.0"


# ---------------------------------------------------------------------------
# Material-database loader (no copy into repo).


def load_material_db(source_dir: Path) -> tuple[dict, dict]:
    """Import ``material_database.py`` from the operator-uploaded location
    (typically ``/tmp/nils-cc-csic-showcase/_unpacked/laufzettel-readout-main/src/``)
    without copying it into the repo.  Returns ``(fiber_database, fabric_database)``.
    """
    target = source_dir / "material_database.py"
    if not target.exists():
        raise SystemExit(
            f"material_database.py not found at {target}.  "
            "Pass --source pointing at the laufzettel-readout/src directory."
        )
    spec = importlib.util.spec_from_file_location("material_database", target)
    if spec is None or spec.loader is None:
        raise SystemExit(f"could not import {target}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.fiber_database, module.fabric_database


# ---------------------------------------------------------------------------
# Turtle generator (stdlib-only — small enough to hand-roll safely).


# Material-property keys → predicate localname.  Both fiber and fabric
# dicts use the same key names; we share the predicate vocab.
PROPERTY_PREDICATES = {
    "density": "density",
    "diameter": "diameter",
    "fabric_type": "fabricType",
    "fiber_type": "fiberType",
    "weave": "weave",
    "manufacturer": "manufacturer",
    "weight_per_area": "weightPerArea",
    "filament_count": "filamentCount",
    "fiber_density": "fiberDensity",
    "fiber_diameter": "fiberDiameter",
    "layer_thickness": "layerThickness",
    "tensile_strength": "tensileStrength",
    "youngs_modulus": "youngsModulus",
    "bending_strength": "bendingStrength",
}


def _ttl_individual_iri(category: str, key: Any) -> str:
    """Mint a stable IRI for a fiber / fabric key.

    Keys can be strings (``"HTA"``, ``"Style 840"``) or ints
    (``98140``).  Whitespace is replaced with ``_`` to keep the IRI
    URL-safe; everything else stays verbatim so a re-run produces the
    same IRI.
    """
    safe = str(key).replace(" ", "_")
    return f"urn:btkvs:{category}:{safe}"


def _ttl_quoted_string(s: str) -> str:
    # Turtle-quote a string literal, escaping ``"`` and ``\\``.  Multi-line
    # values fall back to Turtle's triple-quote string syntax for safety.
    s = s.replace("\\", "\\\\").replace('"', '\\"')
    if "\n" in s:
        return f'"""{s}"""'
    return f'"{s}"'


def _ttl_render_value(v: Any) -> str | None:
    """Render a Python value as a Turtle literal.  Returns ``None`` for
    values that should be skipped (``None``, empty strings)."""
    if v is None:
        return None
    if isinstance(v, bool):
        # NOTE: must come before ``int`` (Python bool is subclass of int).
        return f'"{str(v).lower()}"^^<http://www.w3.org/2001/XMLSchema#boolean>'
    if isinstance(v, int):
        return f'"{v}"^^<http://www.w3.org/2001/XMLSchema#integer>'
    if isinstance(v, float):
        return f'"{v}"^^<http://www.w3.org/2001/XMLSchema#decimal>'
    if isinstance(v, str):
        if not v.strip():
            return None
        return _ttl_quoted_string(v)
    # Unknown type — render as string for safety.
    return _ttl_quoted_string(str(v))


def _ttl_render_individual(iri: str, category: str, key: Any, props: dict) -> list[str]:
    """Render one fiber / fabric individual as Turtle lines.

    Layout::

        <urn:btkvs:fiber:HTA>
            a <urn:btkvs:Fiber> ;
            rdfs:label "HTA" ;
            <urn:btkvs:material:density> "1.76"^^xsd:decimal ;
            ...
        .
    """
    class_localname = "Fiber" if category == "fiber" else "Fabric"
    lines = [f"<{iri}>"]
    lines.append(f"    a <urn:btkvs:{class_localname}> ;")
    lines.append(f"    <http://www.w3.org/2000/01/rdf-schema#label> {_ttl_quoted_string(str(key))} ;")
    triples: list[str] = []
    for raw_key, predicate_localname in PROPERTY_PREDICATES.items():
        rendered = _ttl_render_value(props.get(raw_key))
        if rendered is None:
            continue
        triples.append(f"    <urn:btkvs:material:{predicate_localname}> {rendered}")
    if triples:
        # All triples except the last get a trailing semicolon; the
        # last gets a period.  We've already emitted the ``rdfs:label``
        # line with a trailing semicolon above, so every entry here
        # ends with ` ;` except the final one which is closed by ` .`.
        for i, t in enumerate(triples):
            sep = " ;" if i < len(triples) - 1 else " ."
            lines.append(t + sep)
    else:
        # No triples to emit — close the label line with `.`.
        lines[-1] = lines[-1].rstrip(" ;") + " ."
    lines.append("")  # blank line between individuals
    return lines


def generate_ttl(fiber_db: dict, fabric_db: dict) -> bytes:
    """Build the in-memory Turtle bundle.

    The bundle declares two classes (``urn:btkvs:Fiber``,
    ``urn:btkvs:Fabric``), 14 datatype-property descriptions (one per
    material attribute we surface), and one individual per fiber +
    one per fabric.
    """
    out: list[str] = []
    # Header — prefixes plus ontology metadata.
    out.append("@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .")
    out.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .")
    out.append("@prefix owl:  <http://www.w3.org/2002/07/owl#> .")
    out.append("@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .")
    out.append("@prefix btkvs: <urn:btkvs:> .")
    out.append("")
    out.append("<urn:btkvs:btkvs-materials>")
    out.append("    a owl:Ontology ;")
    out.append("    rdfs:label \"BT-KVS Materials — fibers + fabrics\" ;")
    out.append("    rdfs:comment \"Controlled vocabulary of fiber types and fabric weaves used in the BT-KVS C/SiC fabrication line (Polymerisation → Tempering → Pyrolysis → Siliconization). Seeded from laufzettel-readout/material_database.py per BTKVS-A2.\" .")
    out.append("")

    # Classes.
    out.append("<urn:btkvs:Fiber>")
    out.append("    a owl:Class ;")
    out.append("    rdfs:label \"Fiber\" ;")
    out.append("    rdfs:comment \"A reinforcement fiber type used in C/C and C/SiC composite parts.\" .")
    out.append("")
    out.append("<urn:btkvs:Fabric>")
    out.append("    a owl:Class ;")
    out.append("    rdfs:label \"Fabric\" ;")
    out.append("    rdfs:comment \"A reinforcement fabric weave (catalog number or named style) — domain entry pairs with a Fiber.\" .")
    out.append("")

    # Datatype properties — declare each predicate once with a label.
    for raw_key, predicate_localname in PROPERTY_PREDICATES.items():
        out.append(f"<urn:btkvs:material:{predicate_localname}>")
        out.append("    a owl:DatatypeProperty ;")
        out.append(f"    rdfs:label \"{predicate_localname}\" ;")
        out.append(f"    rdfs:comment \"Material attribute: {raw_key}.\" .")
        out.append("")

    # Individuals.
    for key, props in sorted(fiber_db.items(), key=lambda kv: str(kv[0])):
        iri = _ttl_individual_iri("fiber", key)
        out.extend(_ttl_render_individual(iri, "fiber", key, props or {}))
    for key, props in sorted(fabric_db.items(), key=lambda kv: str(kv[0])):
        iri = _ttl_individual_iri("fabric", key)
        out.extend(_ttl_render_individual(iri, "fabric", key, props or {}))

    return "\n".join(out).encode("utf-8")


# ---------------------------------------------------------------------------
# HTTP client (mirrors seed.py).


@dataclass
class Api:
    host: str
    apikey: str

    def _url(self, path: str) -> str:
        return f"{self.host.rstrip('/')}{path}"

    def get(self, path: str, **kw: Any) -> Any:
        return self._req("GET", path, **kw)

    def delete(self, path: str) -> Any:
        return self._req("DELETE", path)

    def _req(
        self,
        method: str,
        path: str,
        *,
        json_body: Any = None,
        params: dict[str, str] | None = None,
    ) -> Any:
        url = self._url(path)
        if params:
            url = f"{url}?{urllib.parse.urlencode(params)}"
        headers = {"X-API-KEY": self.apikey, "Accept": "application/json"}
        data: bytes | None = None
        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = resp.read()
                if not body:
                    return None
                if "application/json" in resp.headers.get("Content-Type", ""):
                    return json.loads(body)
                return body
        except urllib.error.HTTPError as e:
            raise RuntimeError(
                f"HTTP {e.code} on {method} {path}: {e.read()[:400]!r}"
            ) from e

    def upload_ontology(
        self, ttl_bytes: bytes, metadata: dict
    ) -> tuple[int, bytes]:
        """Multipart POST to /v2/admin/semantic/ontologies.

        Returns ``(status_code, raw_body)`` — caller distinguishes
        between 201 (created), 409 (duplicate), and the rest.
        """
        boundary = f"----btkvs-ontology-{uuid.uuid4().hex}"
        b = f"--{boundary}".encode("utf-8")
        crlf = b"\r\n"
        parts: list[bytes] = []
        # file part
        parts.append(b + crlf)
        parts.append(
            b'Content-Disposition: form-data; name="file"; filename="' +
            BUNDLE_ID.encode("utf-8") + b'.ttl"' + crlf
        )
        parts.append(b"Content-Type: text/turtle" + crlf + crlf)
        parts.append(ttl_bytes + crlf)
        # metadata part
        parts.append(b + crlf)
        parts.append(
            b'Content-Disposition: form-data; name="metadata"' + crlf
        )
        parts.append(b"Content-Type: application/json" + crlf + crlf)
        parts.append(json.dumps(metadata).encode("utf-8") + crlf)
        # closing boundary
        parts.append(f"--{boundary}--".encode("utf-8") + crlf)
        body = b"".join(parts)
        url = self._url("/v2/admin/semantic/ontologies")
        req = urllib.request.Request(
            url,
            data=body,
            headers={
                "X-API-KEY": self.apikey,
                "Accept": "application/json",
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "Content-Length": str(len(body)),
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                return resp.status, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()


# ---------------------------------------------------------------------------
# Main flow


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", required=True)
    p.add_argument("--apikey", required=True)
    p.add_argument(
        "--source",
        default="/tmp/nils-cc-csic-showcase/_unpacked/laufzettel-readout-main/src",
        help="Path to the directory holding material_database.py (operator upload).",
    )
    p.add_argument(
        "--reset",
        action="store_true",
        help="DELETE the existing btkvs-materials bundle first, then upload.",
    )
    args = p.parse_args(argv)

    fiber_db, fabric_db = load_material_db(Path(args.source))
    ttl = generate_ttl(fiber_db, fabric_db)
    print(
        f"generated Turtle bundle: {len(ttl)} bytes, "
        f"{len(fiber_db)} fibers, {len(fabric_db)} fabrics"
    )

    api = Api(host=args.host, apikey=args.apikey)
    if args.reset:
        # Best-effort delete; ignore 404 / 409 builtin-not-removable.
        try:
            api.delete(f"/v2/admin/semantic/ontologies/{BUNDLE_ID}")
            print(f"deleted existing bundle: {BUNDLE_ID}")
        except RuntimeError as e:
            print(f"delete returned (continuing): {e}")

    metadata = {
        "id": BUNDLE_ID,
        "name": BUNDLE_NAME,
        "iriPrefix": BUNDLE_IRI_PREFIX,
        "canonicalUrl": BUNDLE_CANONICAL_URL,
        "license": BUNDLE_LICENSE,
    }
    status, body = api.upload_ontology(ttl, metadata)
    if status == 201:
        print(f"OK     uploaded bundle '{BUNDLE_ID}'")
        return 0
    if status == 409:
        print(
            f"SKIP   bundle '{BUNDLE_ID}' already exists "
            f"(409 — duplicate; re-run with --reset to replace)"
        )
        return 0
    print(f"FAIL   status={status}  body={body[:400]!r}")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
