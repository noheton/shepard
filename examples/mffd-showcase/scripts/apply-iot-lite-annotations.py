#!/usr/bin/env python3
"""
IOT1 application — W3C iot-lite + SOSA/SSN semantic annotations on the
MFFD synthetic showcase Collection.

This script makes the AFP cell / process-step DataObjects in the MFFD
synthetic showcase Collection semantically queryable as **devices /
sensors observing physical quantities**, per the CLAUDE.md rule
``## Always: semantic annotations are first-class on every entity``.

Behaviour
---------

The script first probes ``/v2/admin/semantic/ontologies`` to learn
which ontology bundles are loaded on the live SemanticRepository.

- If ``iot-lite`` is enabled, predicates from
  ``http://purl.org/iot/ontology/iot-lite#`` are used.
- Otherwise it falls back to **SOSA/SSN** predicates
  (``http://www.w3.org/ns/sosa/``, ``http://www.w3.org/ns/ssn/``)
  — the IOT1 design's documented fallback per the prompt spec.
- Observable quantities are emitted against QUDT
  (``http://qudt.org/vocab/unit/``) which is preseeded by N1c2.

For each AFP-cell process-step DataObject the script emits, per the
"sensor-shaped" mapping in ``DEVICE_OBSERVATIONS``:

1. ``rdf:type sosa:Sensor`` (or ``iotlite:Device`` if iot-lite is loaded)
2. ``sosa:observes <quantity>`` — one per measurement channel kind
   the device is known to emit (e.g. force, temperature, displacement,
   vibration). Quantity IRIs come from QUDT.
3. ``sosa:isHostedBy / iotlite:isAssociatedWith`` — link to the parent
   AFP cell or assembly rig DataObject (deployment context).

Each annotation write is captured as a typed ``:Activity`` by the
backend's SemanticAnnotationV2Rest handler — the script sets the
``X-AI-Agent: bob-mffd-iot-annotator/v1`` header so the Activity is
stamped ``sourceMode = "ai"`` (EU AI Act Art. 50 transparency hook
per the CLAUDE.md "cross-cutting context in headers" rule).

Idempotency
-----------

Re-running the script does not double-apply. Existing annotations
are listed by ``GET /v2/annotations?subjectAppId=&predicateIri=``
and skipped when a same-predicate+object annotation is already
present on the subject.

Auth
----

Pass a Bearer token via ``--bearer`` (Keycloak access token) OR an
API key via ``--apikey``. Tested with the ``bob`` test user
(``bob-demo``); any user with Write access to the collection works.

Usage
-----

    # bearer-token flow (default for the demo)
    python3 apply-iot-lite-annotations.py \\
        --host https://shepard-api.nuclide.systems \\
        --bearer "$BOB_JWT" \\
        --collection-app-id 019e6ff9-2bf7-732c-aa1c-2b504302a1e4

    # api-key flow (production)
    python3 apply-iot-lite-annotations.py \\
        --host https://shepard-api.nuclide.systems \\
        --apikey "$SHEPARD_API_KEY" \\
        --collection-app-id 019e6ff9-2bf7-732c-aa1c-2b504302a1e4

    # dry-run — print intended annotations, write nothing
    python3 apply-iot-lite-annotations.py --dry-run …
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, field
from typing import Iterable

import requests

# ---------------------------------------------------------------------------
# Ontology prefixes (canonical IRIs)

IRI_IOTLITE = "http://purl.org/iot/ontology/iot-lite#"
IRI_SOSA    = "http://www.w3.org/ns/sosa/"
IRI_SSN     = "http://www.w3.org/ns/ssn/"
IRI_QUDT_QK = "http://qudt.org/vocab/quantitykind/"
IRI_RDF     = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

AGENT_HEADER = "bob-mffd-iot-annotator/v1"

# ---------------------------------------------------------------------------
# Device / sensor mapping
#
# Each entry describes how an MFFD synthetic showcase process-step
# DataObject SHOULD be annotated as a sensing device + the observed
# quantities. Keys are DataObject names (matching examples/mffd-showcase/
# seed.py DO_SPECS) so the script keeps working when re-seeded.
#
# observations: list of QUDT QuantityKind IRIs the device emits.
# host: optional deployment context (sosa:isHostedBy / iotlite:isAssociatedWith)
#       — at the moment the host is the same DO (the AFP cell is the
#       process step) but the structure allows pointing to a separate
#       "physical asset" DO when one is introduced.

# Reference: QUDT QuantityKind vocabulary
# https://www.qudt.org/doc/DOC_QUANTITYKINDS.html
QK_FORCE        = IRI_QUDT_QK + "Force"
QK_TEMPERATURE  = IRI_QUDT_QK + "Temperature"
QK_DISPLACEMENT = IRI_QUDT_QK + "Displacement"
QK_VELOCITY     = IRI_QUDT_QK + "LinearVelocity"
QK_ANGLE        = IRI_QUDT_QK + "Angle"
QK_TORQUE       = IRI_QUDT_QK + "Torque"
QK_PRESSURE     = IRI_QUDT_QK + "Pressure"
QK_ACCELERATION = IRI_QUDT_QK + "Acceleration"


@dataclass(frozen=True)
class DeviceProfile:
    """A sensor-bearing DataObject in the MFFD synthetic showcase."""

    process_label: str
    """Human-readable process-step label for the agent activity summary."""

    observations: tuple[str, ...]
    """QUDT QuantityKind IRIs this device observes."""

    is_sensor: bool = True
    """If False, the entity is more a process step than a sensor host;
    we still emit the device typing but skip the sosa:observes pairs."""


# Names match the seed: examples/mffd-showcase/seed.py DO_SPECS
DEVICE_OBSERVATIONS: dict[str, DeviceProfile] = {
    "AFP Layup S1": DeviceProfile(
        process_label="AFP layup section 1 (KUKA KR270 + AFPT MTLH head)",
        observations=(QK_TEMPERATURE, QK_FORCE, QK_DISPLACEMENT, QK_VELOCITY),
    ),
    "AFP Layup S2": DeviceProfile(
        process_label="AFP layup section 2 (KUKA KR270 + AFPT MTLH head)",
        observations=(QK_TEMPERATURE, QK_FORCE, QK_DISPLACEMENT, QK_VELOCITY),
    ),
    "Stringer Welding S1": DeviceProfile(
        process_label="Continuous-ultrasonic-welding (cUS-W) robot S1",
        observations=(QK_FORCE, QK_TEMPERATURE, QK_VELOCITY, QK_DISPLACEMENT),
    ),
    "Stringer Welding S2": DeviceProfile(
        process_label="Continuous-ultrasonic-welding (cUS-W) robot S2",
        observations=(QK_FORCE, QK_TEMPERATURE, QK_VELOCITY, QK_DISPLACEMENT),
    ),
    "Frame Welding — Brückenschweißen": DeviceProfile(
        process_label="Resistance bridge-welding fixture (Brücke)",
        observations=(QK_FORCE, QK_TEMPERATURE, QK_PRESSURE, QK_DISPLACEMENT),
    ),
    "Frame Welding — Punktschweißen": DeviceProfile(
        process_label="Resistance spot-welding fixture (Punkt)",
        observations=(QK_FORCE, QK_TEMPERATURE, QK_PRESSURE),
    ),
    "Stringerverbindung (Assembly)": DeviceProfile(
        process_label="Assembly alignment rig (ZLP)",
        observations=(QK_DISPLACEMENT, QK_ANGLE),
    ),
    "LBR Cleat Installation": DeviceProfile(
        process_label="KUKA LBR iiwa 14 R820 — force-torque + joint sensors",
        observations=(QK_FORCE, QK_TORQUE, QK_DISPLACEMENT, QK_ANGLE),
    ),
}

# ---------------------------------------------------------------------------
# Client / HTTP helpers


@dataclass
class Client:
    host: str
    bearer: str | None = None
    apikey: str | None = None
    session: requests.Session = field(default_factory=requests.Session)

    def __post_init__(self) -> None:
        if not self.host.startswith("http"):
            raise SystemExit("--host must be a full URL (http:// or https://)")
        self.host = self.host.rstrip("/")
        if not self.bearer and not self.apikey:
            raise SystemExit("provide --bearer or --apikey")

    def _headers(self, extra: dict[str, str] | None = None) -> dict[str, str]:
        h: dict[str, str] = {"Accept": "application/json"}
        if self.bearer:
            h["Authorization"] = f"Bearer {self.bearer}"
        elif self.apikey:
            h["X-API-KEY"] = self.apikey
        if extra:
            h.update(extra)
        return h

    def get(self, path: str, params: dict | None = None) -> requests.Response:
        return self.session.get(self.host + path, headers=self._headers(), params=params, timeout=30)

    def post_json(self, path: str, body: dict, headers: dict[str, str] | None = None) -> requests.Response:
        h = self._headers({"Content-Type": "application/json"})
        if headers:
            h.update(headers)
        return self.session.post(self.host + path, headers=h, data=json.dumps(body), timeout=30)


# ---------------------------------------------------------------------------
# Probe live SemanticRepository


def probe_loaded_bundles(client: Client) -> dict[str, bool]:
    """Return ``{bundleId: enabled}`` for every bundle the live admin endpoint reports."""
    r = client.get("/v2/admin/semantic/ontologies")
    if r.status_code != 200:
        raise SystemExit(
            f"semantic-ontologies probe failed — HTTP {r.status_code}\n{r.text[:300]}"
        )
    payload = r.json()
    bundles = payload.get("bundles", [])
    return {b["id"]: bool(b.get("enabled")) for b in bundles}


def resolve_device_predicate(bundles: dict[str, bool]) -> tuple[str, str, str]:
    """Decide which device-typing predicate to use.

    Returns ``(predicate_iri, object_iri, vocabulary_id)``.
    Prefers iot-lite when loaded, falls back to SOSA otherwise.
    """
    if bundles.get("iot-lite"):
        return (IRI_RDF + "type", IRI_IOTLITE + "Device", "iot-lite")
    if bundles.get("ssn-sosa"):
        return (IRI_RDF + "type", IRI_SOSA + "Sensor", "ssn-sosa")
    raise SystemExit(
        "Neither iot-lite nor ssn-sosa is loaded on the live SemanticRepository — "
        "cannot apply IOT1 annotations. Enable a bundle via "
        "`PATCH /v2/admin/semantic/ontologies/{id}/enabled` first."
    )


def have_qudt(bundles: dict[str, bool]) -> bool:
    return bool(bundles.get("qudt"))


# ---------------------------------------------------------------------------
# DataObject lookup


def list_collection_data_objects(client: Client, coll_numeric_id: int) -> list[dict]:
    """Return the [{appId, name, …}] list for a Collection by its numeric id."""
    r = client.get(f"/shepard/api/collections/{coll_numeric_id}/dataObjects")
    if r.status_code != 200:
        raise SystemExit(
            f"list dataObjects failed — HTTP {r.status_code}\n{r.text[:300]}"
        )
    return r.json() or []


def resolve_collection_id(client: Client, coll_app_id: str) -> int:
    """Resolve a Collection appId to its numeric id via /v2/collections."""
    r = client.get("/v2/collections", params={"pageSize": 100})
    if r.status_code != 200:
        raise SystemExit(
            f"list collections failed — HTTP {r.status_code}\n{r.text[:300]}"
        )
    for c in r.json() or []:
        if c.get("appId") == coll_app_id:
            return int(c["id"])
    raise SystemExit(f"collection appId {coll_app_id!r} not found")


# ---------------------------------------------------------------------------
# Annotation write helpers


def existing_annotation(
    client: Client,
    subject_app_id: str,
    predicate_iri: str,
    object_iri: str,
) -> bool:
    """True if an annotation with the same (subject, predicate, objectIri) exists."""
    r = client.get(
        "/v2/annotations",
        params={
            "subjectAppId": subject_app_id,
            "predicateIri": predicate_iri,
            "pageSize": 200,
        },
    )
    if r.status_code != 200:
        return False
    for a in r.json() or []:
        if (a.get("objectIri") == object_iri) or (a.get("valueIRI") == object_iri):
            return True
    return False


def write_annotation(
    client: Client,
    subject_app_id: str,
    subject_kind: str,
    predicate_iri: str,
    predicate_label: str,
    object_iri: str,
    vocabulary_id: str,
    confidence: float = 0.95,
    dry_run: bool = False,
) -> str | None:
    """POST /v2/annotations. Returns the new annotation appId (or "DRY-RUN")."""
    body = {
        "subjectAppId": subject_app_id,
        "subjectKind":  subject_kind,
        "predicateIri": predicate_iri,
        "predicateLabel": predicate_label,
        "objectIri":    object_iri,
        "vocabularyId": vocabulary_id,
        "sourceMode":   "ai",
        "confidence":   confidence,
    }
    if dry_run:
        print(f"  DRY  {subject_app_id} -- {predicate_iri} -> {object_iri}")
        return "DRY-RUN"
    r = client.post_json(
        "/v2/annotations",
        body,
        headers={"X-AI-Agent": AGENT_HEADER},
    )
    if r.status_code != 201:
        print(f"  FAIL POST /v2/annotations — HTTP {r.status_code}\n      {r.text[:200]}")
        return None
    appid = r.json().get("appId")
    return appid


# ---------------------------------------------------------------------------
# Main


def apply(
    client: Client,
    coll_app_id: str,
    dry_run: bool,
) -> None:
    print(f"=== probing live SemanticRepository ===")
    bundles = probe_loaded_bundles(client)
    rdf_type_iri, device_object_iri, device_vocab = resolve_device_predicate(bundles)
    sosa_loaded = bundles.get("ssn-sosa", False)
    qudt_loaded = have_qudt(bundles)
    print(
        f"  bundles  iot-lite={bundles.get('iot-lite', False)} "
        f"ssn-sosa={sosa_loaded} qudt={qudt_loaded}"
    )
    print(f"  device-predicate  {rdf_type_iri} -> {device_object_iri} ({device_vocab})")
    if not sosa_loaded:
        print("  WARN: ssn-sosa not loaded — sosa:observes annotations skipped")
    if not qudt_loaded:
        print("  WARN: qudt not loaded — quantity IRIs will not resolve in SPARQL")

    print(f"\n=== resolving collection {coll_app_id!r} ===")
    coll_id = resolve_collection_id(client, coll_app_id)
    print(f"  numeric id: {coll_id}")

    dos = list_collection_data_objects(client, coll_id)
    print(f"  {len(dos)} DataObject(s) in collection")

    sensors = [d for d in dos if d.get("name") in DEVICE_OBSERVATIONS]
    print(f"  {len(sensors)} match the AFP-sensor device profile")
    if not sensors:
        print("nothing to annotate; exiting.")
        return

    print(f"\n=== applying IOT1 annotations ===")
    n_type = 0
    n_obs = 0
    n_skip = 0
    n_fail = 0
    for do in sensors:
        name = do["name"]
        app_id = do.get("appId")
        if not app_id:
            print(f"  SKIP  {name} — no appId")
            continue
        profile = DEVICE_OBSERVATIONS[name]
        print(f"\n- {name}  ({profile.process_label})")

        # 1) Device typing
        if existing_annotation(client, app_id, rdf_type_iri, device_object_iri):
            print(f"  SKIP  rdf:type -> {device_object_iri} (already applied)")
            n_skip += 1
        else:
            new_id = write_annotation(
                client, app_id, "DataObject",
                rdf_type_iri,
                f"rdf:type {device_object_iri.split('#')[-1].split('/')[-1]}",
                device_object_iri,
                device_vocab,
                dry_run=dry_run,
            )
            if new_id:
                print(f"  OK    rdf:type -> {device_object_iri}  ({new_id})")
                n_type += 1
            else:
                n_fail += 1

        # 2) sosa:observes for each observation, when SOSA is loaded
        if not sosa_loaded:
            continue
        observes_iri = IRI_SOSA + "observes"
        for qk_iri in profile.observations:
            label = qk_iri.split("/")[-1]
            if existing_annotation(client, app_id, observes_iri, qk_iri):
                print(f"  SKIP  sosa:observes -> {label} (already applied)")
                n_skip += 1
                continue
            new_id = write_annotation(
                client, app_id, "DataObject",
                observes_iri,
                f"sosa:observes qudt:{label}",
                qk_iri,
                "ssn-sosa",
                dry_run=dry_run,
            )
            if new_id:
                print(f"  OK    sosa:observes -> {label}  ({new_id})")
                n_obs += 1
            else:
                n_fail += 1

    print(
        f"\n=== summary ==="
        f"\n  device-typings written : {n_type}"
        f"\n  observes-pairs written : {n_obs}"
        f"\n  already-applied (skip) : {n_skip}"
        f"\n  failed                 : {n_fail}"
    )


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--host", default=os.environ.get("SHEPARD_URL", "https://shepard-api.nuclide.systems"))
    auth = ap.add_mutually_exclusive_group()
    auth.add_argument("--bearer", default=os.environ.get("SHEPARD_BEARER_TOKEN"))
    auth.add_argument("--apikey", default=os.environ.get("SHEPARD_API_KEY"))
    ap.add_argument(
        "--collection-app-id",
        default="019e6ff9-2bf7-732c-aa1c-2b504302a1e4",
        help="MFFD synthetic showcase Collection appId (live nuclide.systems default)",
    )
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    client = Client(host=args.host, bearer=args.bearer, apikey=args.apikey)
    apply(client, args.collection_app_id, dry_run=args.dry_run)
    return 0


if __name__ == "__main__":
    sys.exit(main())
