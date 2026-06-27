"""BT-KVS docket showcase — seed script (BTKVS-A1).

Creates the **BT-KVS Docket — C/SiC fabrication campaign (synthetic)**
Collection plus the full DataObject graph for one canonical v3 docket
(``I123``: an RTM-polymerised C/SiC plate that goes Polymerisation →
Tempering → Pyrolysis (pass 1) → Pyrolysis (pass 2) → Siliconization).

The seed mirrors the LUMEN / MFFD / microsections precedent (stdlib-only
``urllib`` + multipart) and applies the three BTKVS-A4 refinements
(`aidocs/integrations/116-btkvs-improved-schema.md`):

1.  **Linear ``:Predecessor`` chain on the five process-step DOs** —
    each step ``typedPredecessor`` ``prov:wasInformedBy`` the previous
    one (PROV1k, shipped via ``TypedPredecessorIO``).
2.  **One ``btkvs:post-analysis`` child DO per step** carrying the
    ``post_analysis`` JSON as a ``:StructuredDataReference``.
3.  Editor stamps → ``:Activity`` PROV-O rows — **deferred**. See the
    TODO at ``_decode_editor`` below; the v3 ``editor.{name,date}``
    tuples land as ``SemanticAnnotations`` (``urn:shepard:btkvs:editor-*``
    predicates) for now, full ``:Activity`` decomposition happens in
    ``BTKVS-A3`` (the server-side decompose endpoint that will own this
    graph creation natively).

Hits the live Shepard at ``https://shepard-api.nuclide.systems``
(override via ``--host``).

DataObject layout (one docket → ~12 DOs + 1 Collection)::

    Collection "BT-KVS Docket — C/SiC fabrication campaign (synthetic)"
    └─ Docket I123                  (general attrs)
       ├─ Structure I123             (reinforcement/fiber/weave attrs + structure JSON SDR)
       ├─ Polymerisation             (step JSON SDR)                  stepIndex=1
       │  └─ Post-analysis: Polymerisation     (post_analysis JSON SDR)
       ├─ Tempering                  (step JSON SDR)  ←Predecessor─   stepIndex=2
       │  └─ Post-analysis: Tempering          (post_analysis JSON SDR)
       ├─ Pyrolysis (pass 1)         (step JSON SDR)  ←Predecessor─   stepIndex=3
       │  └─ Post-analysis: Pyrolysis (pass 1) (post_analysis JSON SDR)
       ├─ Pyrolysis (pass 2)         (step JSON SDR)  ←Predecessor─   stepIndex=4
       │  └─ Post-analysis: Pyrolysis (pass 2) (post_analysis JSON SDR)
       └─ Siliconization             (step JSON SDR)  ←Predecessor─   stepIndex=5
          └─ Post-analysis: Siliconization     (post_analysis JSON SDR)

Usage::

    python3 seed.py --host https://shepard-api.nuclide.systems --apikey <token>

Idempotent — by-name lookup on Collection + every DO before create;
safe to re-run.  ``--reset`` deletes the Collection (cascades into its
DOs + SDRs) and recreates from scratch.

Source docket: ``/tmp/nils-cc-csic-showcase/Nils_Packet_fuer_Claude/example.json``
— the operator-uploaded canonical v3 example.  This script ships a
**pinned in-memory copy** of that JSON below so it stays self-contained
and runs without ``/tmp`` mounts (per
``feedback_uploads_never_in_repo.md`` — never copy raw operator
artefacts into the repo, only the abstracted seed shape).
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


# ---------------------------------------------------------------------------
# Constants

COLLECTION_NAME = "BT-KVS Docket — C/SiC fabrication campaign (synthetic)"
COLLECTION_DESCRIPTION = (
    "BTKVS-A1 showcase. One canonical v3 docket (I123) modelled per the "
    "BTKVS-A4 improved schema (aidocs/integrations/116): linear "
    "Predecessor chain on the five process-step DataObjects, per-step "
    "post-analysis child DOs, JSON sub-sections riding as "
    "StructuredDataReferences. Source data: a synthetic C/SiC plate "
    "fabrication docket (Polymerisation → Tempering → Pyrolysis pass 1 "
    "→ Pyrolysis pass 2 → Siliconization). NOT REAL DLR/BT-KVS data — a "
    "decomposition demonstrator for the T1 template-as-form line of "
    "work. See examples/btkvs-docket-showcase/SHOWCASE.md."
)

# Per-step relationship type for the linear chain (PROV1k allowed values).
PROV_WAS_INFORMED_BY = "prov:wasInformedBy"

# Semantic annotation predicates — urn:shepard:btkvs namespace.
# All metadata travels as SemanticAnnotations, not the legacy attributes bag.
_A = "urn:shepard:btkvs:"
ANNOTATION_PREDICATES: dict[str, str] = {
    "btkvs_kind":                _A + "kind",
    "docket_id":                 _A + "docket-id",
    "docket_version":            _A + "docket-version",
    "project":                   _A + "project",
    "project_lead":              _A + "project-lead",
    "ktr":                       _A + "ktr",
    "delivery_date":             _A + "delivery-date",
    "comments":                  _A + "comments",
    "geometry":                  _A + "geometry",
    "fvc":                       _A + "fvc",
    "precursor":                 _A + "precursor",
    "additive":                  _A + "additive",
    "additive_description":      _A + "additive-description",
    "reinforcement_layer_count": _A + "reinforcement-layer-count",
    "reinforcement_orientation": _A + "reinforcement-orientation",
    "weave_type":                _A + "weave-type",
    "weave_pattern":             _A + "weave-pattern",
    "weave_area_density":        _A + "weave-area-density",
    "fiber_material":            _A + "fiber-material",
    "fiber_density":             _A + "fiber-density",
    "step_kind":                 _A + "step-kind",
    "step_index":                _A + "step-index",
    "method":                    _A + "method",
    "executed":                  _A + "executed",
    "cycle":                     _A + "cycle",
    "burn_id":                   _A + "burn-id",
    "temperature":               _A + "temperature",
    "editor_name":               _A + "editor-name",
    "editor_date":               _A + "editor-date",
    "parent_step_kind":          _A + "parent-step-kind",
    "parent_step_index":         _A + "parent-step-index",
}


# ---------------------------------------------------------------------------
# Pinned v3 docket payload (one canonical example — synthetic data).
#
# This is the operator's example.json embedded so the seed runs without
# /tmp mounts.  The narrative is intentionally identical to the upstream
# v3 example so future BTKVS work (server-side decompose endpoint, SHACL
# shape validation) has a stable golden-test input.

DOCKET_I123: dict[str, Any] = {
    "docket_version": "v3",
    "general": {
        "id": "I123",
        "project": "SuperDuperProject",
        "project_lead": "Me",
        "delivery_date": None,
        "ktr": 123456,
        "comments": "Allgemeines Test Bemerkung",
    },
    "structure": {
        "geometry": "Platte",
        "geometry_comment": None,
        "dimensions_part": {"values": [250, 200, 3], "units": ["L [mm]", "B [mm]", "d [mm]"]},
        "dimensions_cfk": {"values": [250, 200, 3.5], "units": ["L [mm]", "B [mm]", "d [mm]"]},
        "fvc": 42,
        "precursor": "sonst.",
        "precursor_description": "MF70",
        "additive": True,
        "additive_description": "Kleber",
        "comments": None,
        "reinforcement": {
            "layer_count": 29,
            "sizing": True,
            "sizing_description": "Vom Hersteller",
            "preprocessing": False,
            "preprocessing_description": None,
            "orientation": "0/90°",
            "orientation_description": None,
            "comments": "Fasermaterial Beschreibung",
            "weave": {
                "type": 98141,
                "fiber": None,
                "weave": "Kö 2/2",
                "manufacturer": None,
                "area_density": 200,
                "layer_thickness": None,
                "filament_count": None,
            },
            "fiber": {
                "name": None,
                "material": "HTA",
                "density": 1.76,
                "diameter": None,
                "cte": None,
                "thermal_conductivity": None,
                "filaments_per_yarn": None,
            },
        },
    },
    "processing": [
        {
            "polymerisation": {
                "method": "RTM",
                "method_description": "RTM Zyklus",
                "editor": {"name": "Me", "date": "2025-11-24T00:00:00"},
                "post_analysis": {
                    "ndt": [
                        {"method": "CT", "method_description": None, "editor": {"name": "Not Me", "date": None}},
                        {"method": "Röntgen", "method_description": None, "editor": {"name": None, "date": None}},
                    ],
                    "sampling": {"execution": True, "dimensions": "2x4x1", "editor": {"name": "Test", "date": "2026-01-01T00:00:00"}},
                    "density_porosity": {"execution": True, "mass_dry": None, "mass_uw": None, "mass_wet": None, "open_porosity": None, "density": None, "editor": {"name": None, "date": None}},
                    "strength_analysis": {"execution": True, "type": "Kriechprobe"},
                    "part_measurement": {"execution": True, "thickness": 3.13},
                    "damage": {"status": True, "comment": None},
                },
            }
        },
        {
            "tempering": {
                "executed": "ja",
                "cycle": "240°C/2h",
                "editor": {"name": "Me", "date": "2025-12-01T00:00:00"},
                "mass_analysis": {"mass_before": 2, "mass_after": 1, "mass_delta_g": -1, "mass_delta_percent": -50.0},
                "post_analysis": {
                    "density_porosity": {"execution": True, "mass_dry": 5, "mass_uw": 3, "mass_wet": 6, "open_porosity": 0.3333333333333333, "density": 1.6666666666666667, "editor": {"name": "Not Me", "date": "2025-12-02T00:00:00"}},
                    "strength_analysis": {"execution": False, "type": "Kriechprobe"},
                    "part_measurement": {"execution": True, "thickness": 3.12},
                },
            }
        },
        {
            "pyrolysis": {
                "executed": "ja",
                "cycle": "100 Tage",
                "burn_id": "OP123",
                "temperature": 1000,
                "editor": {"name": "Me", "date": "2026-01-29T00:00:00"},
                "weighting_executed": "ja",
                "weighting_mass": None,
                "weighting_comment": None,
                "mass_analysis": {"mass_before": 5, "mass_after": 4, "mass_delta_g": -1, "mass_delta_percent": -20.0},
                "post_analysis": {
                    "density_porosity": {"execution": True, "mass_dry": 5, "mass_uw": 2, "mass_wet": 7, "open_porosity": 0.4, "density": 1.0, "editor": {"name": "Me", "date": "2026-02-09T00:00:00"}},
                    "strength_analysis": {"execution": False, "type": "Kriechprobe"},
                    "part_measurement": {"execution": True, "thickness": None},
                },
            }
        },
        {
            "pyrolysis": {
                "executed": "ja",
                "cycle": None,
                "burn_id": None,
                "temperature": None,
                "editor": {"name": None, "date": None},
                "weighting_executed": "ja",
                "weighting_mass": None,
                "weighting_comment": None,
                "mass_analysis": {"mass_before": None, "mass_after": None, "mass_delta_g": None, "mass_delta_percent": None},
                "post_analysis": {
                    "strength_analysis": {"execution": False, "type": "Kriechprobe"},
                    "part_measurement": {"execution": True, "thickness": None},
                },
            }
        },
        {
            "siliconization": {
                "executed": "ja",
                "cycle": "SP12345",
                "burn_id": "OS123",
                "temperature": 1142,
                "editor": {"name": "Me", "date": "2026-02-12T00:00:00"},
                "mass_analysis": {"mass_before": 6, "mass_after": 2, "mass_delta_g": -4, "mass_delta_percent": -66.66666666666667},
                "si_mass_percent": 1.0,
                "si_mass_g": 6.0,
                "si_distribution": "sonst.",
                "si_direction": "liegend",
                "post_analysis": {
                    "density_porosity": {"execution": True, "mass_dry": 8, "mass_uw": 5, "mass_wet": 10, "open_porosity": 0.4, "density": 1.6, "editor": {"name": "Me", "date": "2026-02-18T00:00:00"}},
                    "strength_analysis": {"execution": False, "type": "Kriechprobe"},
                    "part_measurement": {"execution": True, "thickness": None},
                    "general_comments": "nicht voll und kaputt",
                },
            }
        },
    ],
}


# ---------------------------------------------------------------------------
# Minimal HTTP helpers (no SDK dependency, mirrors microsections precedent)


def _log(status: str, name: str, kind: str = "", extra: str = "") -> None:
    """Render ``STATUS  name  (kind, extra)`` lines for grep-friendly output."""
    tail = ""
    if kind or extra:
        tail = f" ({kind}" + (f", {extra}" if extra else "") + ")"
    print(f"{status:<6} {name}{tail}", flush=True)


@dataclass
class Api:
    host: str
    apikey: str

    def _url(self, path: str) -> str:
        return f"{self.host.rstrip('/')}{path}"

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
            with urllib.request.urlopen(req, timeout=120) as resp:
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

    def get(self, path: str, **kw: Any) -> Any:
        return self._req("GET", path, **kw)

    def post(self, path: str, **kw: Any) -> Any:
        return self._req("POST", path, **kw)

    def delete(self, path: str) -> Any:
        return self._req("DELETE", path)


# ---------------------------------------------------------------------------
# Idempotent lookup-or-create helpers


def find_or_create_collection(api: Api, *, reset: bool) -> dict:
    listing = api.get("/v2/collections", params={"pageSize": "200"})
    rows = listing if isinstance(listing, list) else listing.get("content", [])
    for c in rows:
        if c.get("name") == COLLECTION_NAME:
            if reset:
                api.delete(f"/v2/collections/{c['appId']}")
                _log("RESET", COLLECTION_NAME, "Collection")
                break
            _log("SKIP", COLLECTION_NAME, "Collection (exists)", c["appId"])
            return c
    body = {"name": COLLECTION_NAME, "description": COLLECTION_DESCRIPTION}
    created = api.post("/v2/collections", json_body=body)
    _log("OK", COLLECTION_NAME, "Collection", created["appId"])
    return created


def list_data_objects(api: Api, collection_app_id: str) -> list[dict]:
    listing = api.get(
        f"/v2/collections/{collection_app_id}/data-objects",
        params={"pageSize": "500"},
    )
    return listing if isinstance(listing, list) else listing.get("content", [])


def find_data_object_by_name(rows: list[dict], name: str) -> dict | None:
    for do in rows:
        if do.get("name") == name:
            return do
    return None


def apply_annotations(api: Api, subject_app_id: str, attrs: dict) -> None:
    """Write key/value pairs as SemanticAnnotations on a DataObject.

    Idempotent: fetches existing predicateIris for the subject and skips
    any that are already present.  Unknown keys (not in ANNOTATION_PREDICATES)
    are silently skipped rather than written to the legacy attributes bag.
    """
    try:
        resp = api.get("/v2/annotations", params={
            "subjectAppId": subject_app_id,
            "pageSize": 200,
        })
        items = resp.get("items", resp) if isinstance(resp, dict) else (resp or [])
        existing_iris = {
            a.get("predicateIri") or a.get("propertyIri")
            for a in (items or [])
            if a
        }
    except Exception:
        existing_iris = set()

    for key, value in attrs.items():
        pred_iri = ANNOTATION_PREDICATES.get(key)
        if not pred_iri:
            continue
        if pred_iri in existing_iris:
            _log("SKIP", f"{key}={value!r}", "Annotation (exists)")
            continue
        api.post("/v2/annotations", json_body={
            "subjectAppId": subject_app_id,
            "subjectKind": "DataObject",
            "predicateIri": pred_iri,
            "objectLiteral": str(value),
        })
        _log("OK", f"{key}={value!r}", "Annotation", pred_iri)


def create_data_object(
    api: Api,
    collection_app_id: str,
    name: str,
    *,
    attributes: dict[str, str],
    parent_app_id: str | None = None,
    predecessor_app_id: str | None = None,
    v1ids: "V1IdResolver | None" = None,
) -> dict:
    """Create a v2 DataObject, appId-native wherever the surface allows.

    APISIMP DROP-LEGACY-ID (2026-06-12) removed the numeric ``id`` field
    from v2 Collection and DataObject responses, so the seed never reads
    ``["id"]`` from a v2 response.  Three create shapes:

    - **predecessor present** → ``POST /v2/collections/{cid}/data-objects``
      with ``typedPredecessors[]`` (appId-native, PROV1k).  A hierarchical
      parent on the *same* create still needs the numeric ``parentId`` —
      no v2 create accepts ``parentAppId`` + ``typedPredecessors`` together
      (single create: numeric ``parentId`` only; batch create:
      ``parentAppId`` but no predecessors) — resolved via the documented
      v1 seam (``V1IdResolver``; gap row BTKVS-A1-SEED-V2REFS in aidocs/16).
    - **parent only** → ``POST /v2/data-objects/batch`` (single item) —
      the only v2 create that accepts ``parentAppId`` (MFFD-BATCH-01).
    - **neither** → plain ``POST /v2/collections/{cid}/data-objects``.
    """
    if predecessor_app_id is not None:
        body: dict[str, Any] = {
            "name": name,
            "typedPredecessors": [
                {
                    "predecessorAppId": predecessor_app_id,
                    "relationshipType": PROV_WAS_INFORMED_BY,
                }
            ],
        }
        if parent_app_id is not None:
            assert v1ids is not None
            coll_v1_id = v1ids.collection_id(api, collection_app_id)
            body["parentId"] = v1ids.data_object_id(
                api, coll_v1_id, parent_app_id, context=f"parent of {name}"
            )
        return api.post(
            f"/v2/collections/{collection_app_id}/data-objects", json_body=body
        )
    if parent_app_id is not None:
        resp = api.post(
            "/v2/data-objects/batch",
            json_body=[
                {
                    "collectionAppId": collection_app_id,
                    "name": name,
                    "parentAppId": parent_app_id,
                }
            ],
        )
        item = ((resp or {}).get("results") or [{}])[0]
        if item.get("status") != "created":
            raise RuntimeError(
                f"batch create failed for {name!r}: "
                f"{item.get('errorCode')}: {item.get('errorMessage')}"
            )
        # Batch results carry only the appId — synthesize the row shape the
        # by-name SKIP cache needs.
        return {"name": name, "appId": item["appId"]}
    return api.post(
        f"/v2/collections/{collection_app_id}/data-objects",
        json_body={"name": name},
    )


def find_or_create_data_object(
    api: Api,
    collection_app_id: str,
    rows_cache: list[dict],
    name: str,
    *,
    attributes: dict[str, str],
    parent_app_id: str | None = None,
    predecessor_app_id: str | None = None,
    kind_label: str = "DataObject",
    v1ids: "V1IdResolver | None" = None,
) -> dict:
    existing = find_data_object_by_name(rows_cache, name)
    if existing is not None:
        _log("SKIP", name, f"{kind_label} (exists)", existing["appId"])
        return existing
    created = create_data_object(
        api,
        collection_app_id,
        name,
        attributes=attributes,
        parent_app_id=parent_app_id,
        predecessor_app_id=predecessor_app_id,
        v1ids=v1ids,
    )
    apply_annotations(api, created["appId"], attributes)
    rows_cache.append(created)
    _log("OK", name, kind_label, created["appId"])
    return created


# ---------------------------------------------------------------------------
# Numeric-id resolution — the single documented v1 seam (CLAUDE.md
# named-v1-fallback pattern)
#
# APISIMP DROP-LEGACY-ID (2026-06-12) removed the numeric ``id`` field from
# v2 Collection and DataObject responses; appId (UUID v7) is the only
# identifier on the v2 wire.  Two operations in this seed remain v1-only
# and still address entities by numeric id (checked 2026-06-12 against the
# live OpenAPI: the unified ``POST /v2/references`` registers NO
# ``structureddata`` kind handler — core kinds are collection / dataobject /
# file / timeseries / uri — and ``/v2/containers/{appId}`` has no payload
# POST):
#
#   POST /shepard/api/structuredDataContainers/{id}/payload
#   POST /shepard/api/collections/{cid}/dataObjects/{doid}/structuredDataReferences
#
# This resolver is therefore the only place numeric ids enter the seed.
# Gap tracked as BTKVS-A1-SEED-V2REFS in aidocs/16-dispatcher-backlog.md.


class V1IdResolver:
    """Lazily map appId → numeric (legacy long) id via the frozen v1 lists."""

    def __init__(self) -> None:
        self._collections: dict[str, int] = {}
        self._data_objects: dict[int, dict[str, int]] = {}

    def collection_id(self, api: Api, collection_app_id: str) -> int:
        if collection_app_id not in self._collections:
            listing = api.get("/shepard/api/collections") or []
            for c in listing:
                if c.get("appId") and c.get("id") is not None:
                    self._collections[c["appId"]] = c["id"]
        if collection_app_id not in self._collections:
            raise RuntimeError(
                f"v1 fallback could not resolve a numeric id for Collection {collection_app_id}"
            )
        return self._collections[collection_app_id]

    def data_object_id(
        self, api: Api, collection_v1_id: int, data_object_app_id: str, *, context: str = ""
    ) -> int:
        cache = self._data_objects.setdefault(collection_v1_id, {})
        if data_object_app_id not in cache:
            listing = (
                api.get(f"/shepard/api/collections/{collection_v1_id}/dataObjects") or []
            )
            for do in listing:
                if do.get("appId") and do.get("id") is not None:
                    cache[do["appId"]] = do["id"]
        if data_object_app_id not in cache:
            raise RuntimeError(
                "v1 fallback could not resolve a numeric id for DataObject "
                f"{data_object_app_id}" + (f" ({context})" if context else "")
            )
        return cache[data_object_app_id]


# ---------------------------------------------------------------------------
# StructuredData helpers
#
# SDC create:   POST /v2/containers?kind=structured-data        (V2CONV-A3, appId-native)
# SDC payload:  POST /shepard/api/structuredDataContainers/{id}/payload
#               — v1-only; no payload POST exists on /v2/containers (checked
#               live OpenAPI 2026-06-12); needs the numeric SDC id.
# SDR create:   POST /shepard/api/collections/{cid}/dataObjects/{doid}/structuredDataReferences
#               — v1-only; the unified POST /v2/references has no
#               kind=structureddata handler yet (BTKVS-A1-SEED-V2REFS).


def find_or_create_sdc(api: Api, name: str, sdc_cache: dict[str, dict]) -> dict:
    """Find or create a (freestanding) StructuredDataContainer with this name.

    SDCs are not tied to a Collection on create; the link is via the
    SDR.  The by-name lookup runs on the v1 list because the (v1-only)
    payload upload below still needs the numeric SDC id, which the v2
    ``ContainerV2IO`` deliberately never carries.  Creation goes through
    the unified v2 surface (``POST /v2/containers?kind=structured-data``).
    Cached per-process so the seed only does the list-call once per name.
    """
    if name in sdc_cache:
        return sdc_cache[name]

    def _v1_lookup() -> dict | None:
        listing = api.get("/shepard/api/structuredDataContainers")
        if isinstance(listing, list):
            for sdc in listing:
                if sdc.get("name") == name:
                    return sdc
        return None

    found = _v1_lookup()
    if found is not None:
        sdc_cache[name] = found
        _log("SKIP", name, "SDC (exists)", str(found.get("id", "")))
        return found
    created = api.post(
        "/v2/containers", params={"kind": "structured-data"}, json_body={"name": name}
    )
    # The v2 create response is appId-only; re-read the v1 list for the
    # numeric id the v1-only payload upload requires.
    found = _v1_lookup()
    if found is None:
        raise RuntimeError(
            f"SDC {name!r} created via /v2/containers but not visible on the v1 list"
        )
    sdc_cache[name] = found
    _log("OK", name, "SDC", str(created.get("appId", "")))
    return found


def upload_payload_to_sdc(
    api: Api, sdc_id: int, payload_name: str, payload_obj: Any
) -> str:
    """POST a JSON-stringified payload into an SDC, return the oid."""
    body = {
        "structuredData": {"name": payload_name},
        "payload": json.dumps(payload_obj, ensure_ascii=False),
    }
    result = api.post(
        f"/shepard/api/structuredDataContainers/{sdc_id}/payload",
        json_body=body,
    )
    # Response is `StructuredData` — has {oid, name, createdAt}.
    return result["oid"]


def find_or_create_sdr(
    api: Api,
    collection_id: int,
    data_object_id: int,
    *,
    name: str,
    sdc_id: int,
    sdc_name: str,
    payload_obj: Any,
) -> dict | None:
    """Idempotent SDR creation on a DataObject (v1-only operation).

    The unified ``POST /v2/references`` registers no ``structureddata``
    kind handler (checked 2026-06-12; BTKVS-A1-SEED-V2REFS), so both the
    by-name probe and the create stay on the frozen v1 surface and take
    the numeric ids resolved via ``V1IdResolver``.

    Probes existing SDRs on the DO; if one matches by ``name`` it is
    skipped (we do NOT verify payload equality — the by-name match is
    deemed sufficient for the showcase, and an SDR rename + re-run
    isn't a supported operation).

    Returns the created SDR IO or None on skip.
    """
    listing = api.get(
        f"/shepard/api/collections/{collection_id}/dataObjects/{data_object_id}/structuredDataReferences"
    )
    if isinstance(listing, list):
        for sdr in listing:
            if sdr.get("name") == name:
                _log("SKIP", name, "SDR (exists)", str(sdr.get("id", "")))
                return None
    # Upload payload → oid
    oid = upload_payload_to_sdc(api, sdc_id, name, payload_obj)
    # Create SDR
    body = {
        "name": name,
        "structuredDataContainerId": sdc_id,
        "structuredDataOids": [oid],
    }
    created = api.post(
        f"/shepard/api/collections/{collection_id}/dataObjects/{data_object_id}/structuredDataReferences",
        json_body=body,
    )
    _log("OK", name, f"SDR (via SDC {sdc_name})", str(created.get("id", "")))
    return created


# ---------------------------------------------------------------------------
# Step extraction helpers


# Stable label per processing[] entry — must be deterministic so re-runs
# look up the same DO by name.  Pyrolysis repeats are disambiguated by
# pass-number (BTKVS-A4 §5.5 — recommend `name="Pyrolysis"` +
# `attributes.pass="N"`; the seed uses the human-readable form to keep
# the demo legible).
STEP_KIND_TO_LABEL = {
    "polymerisation": "Polymerisation",
    "tempering": "Tempering",
    "pyrolysis": "Pyrolysis",
    "siliconization": "Siliconization",
}


def step_info(idx: int, step_entry: dict) -> tuple[str, str, dict]:
    """Return ``(kind, display_name, body)`` for a processing[] entry.

    Pyrolysis passes are disambiguated by counting occurrences up to
    and including ``idx``.
    """
    (kind, body), = step_entry.items()
    label = STEP_KIND_TO_LABEL.get(kind, kind.capitalize())
    return kind, label, body


def _decode_editor(editor: dict | None) -> dict[str, str]:
    """Surface ``editor.{name,date}`` as SemanticAnnotation keys.

    Returns a dict with ``editor_name`` / ``editor_date`` keys that
    ``apply_annotations()`` will map to ``urn:shepard:btkvs:editor-name``
    and ``urn:shepard:btkvs:editor-date`` predicates.

    TODO (BTKVS-A4 §2.3, deferred): every ``editor`` tuple in the v3
    docket should produce one ``:Activity`` PROV-O node
    (``sourceMode='human'``, ``WAS_ASSOCIATED_WITH`` → ``:User``,
    ``GENERATED`` → the step or post-analysis DO).  The
    server-side decompose endpoint (BTKVS-A3) will own this; until then
    the editor fields travel as SemanticAnnotations so the BT-KVS team
    can see the data is captured without the seed minting ``:Activity``
    rows directly (which would require API surface that doesn't yet exist
    for arbitrary editor-name strings).
    """
    if not editor:
        return {}
    out: dict[str, str] = {}
    if editor.get("name"):
        out["editor_name"] = str(editor["name"])
    if editor.get("date"):
        out["editor_date"] = str(editor["date"])
    return out


# ---------------------------------------------------------------------------
# Main seed flow


def seed(api: Api, *, reset: bool) -> None:
    coll = find_or_create_collection(api, reset=reset)
    coll_app_id = coll["appId"]
    # Numeric ids are gone from v2 responses (APISIMP DROP-LEGACY-ID); the
    # resolver below is the documented v1 seam for the two v1-only calls
    # (SDC payload upload + SDR create).
    v1ids = V1IdResolver()

    # Single per-Collection cache to avoid listing N times in the loop.
    rows = list_data_objects(api, coll_app_id)
    # Single SDC per Collection.  All payloads in this docket ride here.
    sdc_cache: dict[str, dict] = {}
    sdc = find_or_create_sdc(api, f"BTKVS dockets — {COLLECTION_NAME}", sdc_cache)
    sdc_id = sdc["id"]  # numeric — from the v1 list; payload upload is v1-only
    sdc_name = sdc["name"]
    coll_v1_id = v1ids.collection_id(api, coll_app_id)

    def do_v1_id(do: dict) -> int:
        return v1ids.data_object_id(
            api, coll_v1_id, do["appId"], context=do.get("name", "")
        )

    # ------------------------------------------------------------------
    # Root docket DO  (kind=btkvs:docket-root)
    g = DOCKET_I123["general"]
    docket_name = f"Docket {g['id']}"
    docket_attrs = {
        "btkvs_kind": "docket-root",
        "docket_id": str(g["id"]),
        "docket_version": str(DOCKET_I123.get("docket_version", "v3")),
        "project": str(g.get("project") or ""),
        "project_lead": str(g.get("project_lead") or ""),
        "ktr": str(g.get("ktr") or ""),
        "delivery_date": str(g.get("delivery_date") or ""),
        "comments": str(g.get("comments") or ""),
    }
    docket = find_or_create_data_object(
        api,
        coll_app_id,
        rows,
        docket_name,
        attributes={k: v for k, v in docket_attrs.items() if v != ""},
        kind_label="DataObject (docket-root)",
    )
    docket_app_id = docket["appId"]

    # ------------------------------------------------------------------
    # Structure DO  (child of docket; kind=btkvs:structure)
    s = DOCKET_I123["structure"]
    reinf = s.get("reinforcement", {}) or {}
    weave = reinf.get("weave") or {}
    fiber = reinf.get("fiber") or {}
    structure_attrs = {
        "btkvs_kind": "structure",
        "geometry": str(s.get("geometry") or ""),
        "fvc": str(s.get("fvc") or ""),
        "precursor": str(s.get("precursor") or ""),
        "additive": str(s.get("additive") if s.get("additive") is not None else ""),
        "additive_description": str(s.get("additive_description") or ""),
        "reinforcement_layer_count": str(reinf.get("layer_count") or ""),
        "reinforcement_orientation": str(reinf.get("orientation") or ""),
        "weave_type": str(weave.get("type") or ""),
        "weave_pattern": str(weave.get("weave") or ""),
        "weave_area_density": str(weave.get("area_density") or ""),
        "fiber_material": str(fiber.get("material") or ""),
        "fiber_density": str(fiber.get("density") or ""),
    }
    structure_do = find_or_create_data_object(
        api,
        coll_app_id,
        rows,
        f"Structure {g['id']}",
        attributes={k: v for k, v in structure_attrs.items() if v != ""},
        parent_app_id=docket_app_id,
        kind_label="DataObject (structure)",
    )
    # Attach the structure JSON section as an SDR.
    find_or_create_sdr(
        api,
        collection_id=coll_v1_id,
        data_object_id=do_v1_id(structure_do),
        name="structure.json",
        sdc_id=sdc_id,
        sdc_name=sdc_name,
        payload_obj=s,
    )

    # ------------------------------------------------------------------
    # Process-step chain — linear :Predecessor edges per BTKVS-A4 §2.1.
    prev_step_app_id: str | None = None
    pyrolysis_pass = 0  # disambiguates repeating pyrolysis entries

    for idx, step_entry in enumerate(DOCKET_I123["processing"], start=1):
        kind, label, body = step_info(idx, step_entry)
        if kind == "pyrolysis":
            pyrolysis_pass += 1
            step_name = f"{label} (pass {pyrolysis_pass})"
        else:
            step_name = label

        editor_attrs = _decode_editor(body.get("editor"))
        step_attrs: dict[str, str] = {
            "btkvs_kind": "process-step",
            "step_kind": kind,
            "step_index": str(idx),
            **{
                k: str(body.get(k))
                for k in ("method", "executed", "cycle", "burn_id", "temperature")
                if body.get(k) not in (None, "")
            },
            **editor_attrs,
        }
        step_do = find_or_create_data_object(
            api,
            coll_app_id,
            rows,
            step_name,
            attributes=step_attrs,
            parent_app_id=docket_app_id,
            predecessor_app_id=prev_step_app_id,
            kind_label=f"DataObject (process-step, {kind})",
            v1ids=v1ids,
        )

        # Attach the step JSON (minus the nested post_analysis — that
        # lives on the post-analysis child DO per BTKVS-A4 §2.2).
        step_payload = {k: v for k, v in body.items() if k != "post_analysis"}
        find_or_create_sdr(
            api,
            collection_id=coll_v1_id,
            data_object_id=do_v1_id(step_do),
            name=f"step-{idx}-{kind}.json",
            sdc_id=sdc_id,
            sdc_name=sdc_name,
            payload_obj=step_payload,
        )

        # Post-analysis child DO with the post_analysis JSON section.
        post_analysis = body.get("post_analysis")
        if post_analysis is not None:
            pa_name = f"Post-analysis: {step_name}"
            pa_attrs = {
                "btkvs_kind": "post-analysis",
                "parent_step_kind": kind,
                "parent_step_index": str(idx),
            }
            pa_do = find_or_create_data_object(
                api,
                coll_app_id,
                rows,
                pa_name,
                attributes=pa_attrs,
                parent_app_id=step_do["appId"],
                kind_label=f"DataObject (post-analysis, {kind})",
            )
            find_or_create_sdr(
                api,
                collection_id=coll_v1_id,
                data_object_id=do_v1_id(pa_do),
                name=f"post-analysis-{idx}-{kind}.json",
                sdc_id=sdc_id,
                sdc_name=sdc_name,
                payload_obj=post_analysis,
            )

        prev_step_app_id = step_do["appId"]

    print()
    print("seed complete.")
    print(f"  Collection appId:  {coll_app_id}")
    print(f"  Collection id:     {coll_v1_id}")
    print(f"  Docket DO appId:   {docket_app_id}")


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--host", required=True,
        help="Shepard API base, e.g. https://shepard-api.nuclide.systems",
    )
    p.add_argument(
        "--apikey", required=True,
        help="X-API-KEY token for an instance-admin or write-capable user",
    )
    p.add_argument(
        "--reset", action="store_true",
        help="Delete and recreate the Collection (destructive)",
    )
    args = p.parse_args(argv)
    api = Api(host=args.host, apikey=args.apikey)
    seed(api, reset=args.reset)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
