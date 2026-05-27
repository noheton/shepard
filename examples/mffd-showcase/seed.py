"""Idempotent seeder for the MFFD AFP manufacturing showcase.

Creates the process-chain Collection plus 12 DataObjects (AFP layup S1/S2 →
NDT → rework → stringer cUS-W welding → frame bridge welding → assembly →
LBR cleat installation) in a running Shepard backend. Re-running is safe
(by-name lookup before create); ``--reset`` deletes the Collection first.

Usage
-----
    python seed.py --host https://shepard-api.nuclide.systems --apikey XXX
    python seed.py --host http://localhost:8080 --apikey XXX --reset

Engineering context
-------------------
Narrative shaped after the MFFD upper-fuselage demonstrator manufactured at
ZLP Augsburg (DLR) using CF/LMPAEK thermoplastic CFRP without autoclave.
The MFFD won the JEC World Innovation Award 2025 (Aerospace — Parts).

Key references:
  Deden et al. (2023) SAMPE Europe, eLib 199804 — AFP process + machine details
  Endraß et al. (2024) Polymertec, eLib 209558 — upper shell manufacturing + assembly
  Gardiner (2023) CompositesWorld — process chain + LBR iiwa cleat detail
  Schiel et al. (2020) Adv. Manuf. Polym. Compos. Sci. — AFP parameter ranges

All channel values are synthetic (numpy.default_rng(2024) via data/generate.py).
NOT real DLR MFFD data.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Iterable

import os

import requests as _http

from shepard_client import (  # type: ignore
    ApiClient,
    Collection,
    CollectionApi,
    CollectionSearchBody,
    CollectionSearchParams,
    Configuration,
    ContainerType,
    DataObject,
    DataObjectApi,
    FileContainer,
    FileContainerApi,
    FileReference,
    FileReferenceApi,
    Permissions,
    PermissionType,
    SearchApi,
    StructuredData,
    StructuredDataContainer,
    StructuredDataContainerApi,
    StructuredDataPayload,
    StructuredDataReference,
    StructuredDataReferenceApi,
    Timeseries,
    TimeseriesContainer,
    TimeseriesContainerApi,
    TimeseriesDataPoint,
    TimeseriesReference,
    TimeseriesReferenceApi,
    TimeseriesWithDataPoints,
)

# ---------------------------------------------------------------------------
# Constants

COLLECTION_NAME = "MFFD Upper Shell — AFP Process Chain (synthetic)"
COLLECTION_DESCRIPTION = (
    "Synthetic showcase dataset for shepard. **NOT REAL DLR MFFD data.** "
    "Shaped after the MFFD upper-fuselage demonstrator manufactured at "
    "ZLP Augsburg (DLR) using CF/LMPAEK thermoplastic CFRP without autoclave. "
    "The MFFD won the JEC World Innovation Award 2025 (Aerospace — Parts). "
    "All channel values are synthetic outputs of data/generate.py "
    "(numpy.default_rng(2024)). Used to exercise shepard's feature surface end-to-end."
)

COLLECTION_ATTRIBUTES = {
    "material": "CF/LMPAEK",
    "process": "AFP + thermoplastic welding",
    "platform": "MFFD upper shell demonstrator",
    "location": "ZLP Augsburg, DLR",
    "year": "2024",
    "note": "Synthetic showcase data — NOT real DLR MFFD data",
}

# Shared container names — one per payload kind
TSC_NAME = "mffd-sensors"
FC_NAME  = "mffd-files"
SC_NAME  = "mffd-runlogs"

# Bulk upload chunk size for timeseries
TIMESERIES_CHUNK = 1000

# ---------------------------------------------------------------------------
# DataObject definitions
# Each tuple: (name, status, attributes)
# Predecessor wiring done separately after all DOs are created.

DO_SPECS: list[tuple[str, str, dict]] = [
    (
        "AFP Layup S1",
        "READY",
        {"section": "S1", "process": "AFP", "equipment": "AFPT MTLH / KUKA KR270 R2700", "anomaly": "DEF-001"},
    ),
    (
        "NDT Inspection S1 — FAIL",
        "READY",
        {"section": "S1", "process": "Active Thermography NDT", "result": "FAIL", "defect": "DEF-001"},
    ),
    (
        "Rework S1",
        "READY",
        {"section": "S1", "process": "Hand Rework", "reference": "REWORK-S1-001"},
    ),
    (
        "NDT Re-inspection S1 — PASS",
        "READY",
        {"section": "S1", "process": "Active Thermography NDT", "result": "PASS"},
    ),
    (
        "AFP Layup S2",
        "READY",
        {"section": "S2", "process": "AFP", "equipment": "AFPT MTLH / KUKA KR270 R2700"},
    ),
    (
        "NDT Inspection S2 — PASS",
        "READY",
        {"section": "S2", "process": "Active Thermography NDT", "result": "PASS"},
    ),
    (
        "Stringer Welding S1",
        "READY",
        {"section": "S1", "process": "Continuous Ultrasonic Welding (cUS-W)", "stringers": "8"},
    ),
    (
        "Stringer Welding S2",
        "READY",
        {"section": "S2", "process": "Continuous Ultrasonic Welding (cUS-W)", "stringers": "8"},
    ),
    (
        "Frame Welding — Brückenschweißen",
        "READY",
        {"process": "Resistance Bridge Welding", "step": "bruecke", "channels": "14"},
    ),
    (
        "Frame Welding — Punktschweißen",
        "READY",
        {"process": "Resistance Bridge Welding", "step": "punkt", "channels": "14"},
    ),
    (
        "Stringerverbindung (Assembly)",
        "READY",
        {"process": "Assembly Alignment", "channels": "4"},
    ),
    (
        "LBR Cleat Installation",
        "READY",
        {"process": "LBR iiwa Robot", "cleats": "42", "robot": "KUKA LBR iiwa 14 R820"},
    ),
]

# Predecessor wiring: {do_name: [predecessor_names]}
# DO 9 (Frame Welding — Brückenschweißen) has TWO predecessors.
PREDECESSORS: dict[str, list[str]] = {
    "NDT Inspection S1 — FAIL":       ["AFP Layup S1"],
    "Rework S1":                       ["NDT Inspection S1 — FAIL"],
    "NDT Re-inspection S1 — PASS":     ["Rework S1"],
    "NDT Inspection S2 — PASS":        ["AFP Layup S2"],
    "Stringer Welding S1":             ["NDT Re-inspection S1 — PASS"],
    "Stringer Welding S2":             ["NDT Inspection S2 — PASS"],
    "Frame Welding — Brückenschweißen": ["Stringer Welding S1", "Stringer Welding S2"],
    "Frame Welding — Punktschweißen":  ["Frame Welding — Brückenschweißen"],
    "Stringerverbindung (Assembly)":   ["Frame Welding — Punktschweißen"],
    "LBR Cleat Installation":          ["Stringerverbindung (Assembly)"],
}

# Timeseries channel files per DO (prefix → list of channel stems)
# These map a DO name to the CSV file prefix pattern in data/timeseries/
DO_TIMESERIES: dict[str, str] = {
    "AFP Layup S1":                    "afp-s1",
    "AFP Layup S2":                    "afp-s2",
    "Stringer Welding S1":             "stringer-s1",
    "Stringer Welding S2":             "stringer-s2",
    "Frame Welding — Brückenschweißen": "frame-bruecke",
    "Frame Welding — Punktschweißen":  "frame-punkt",
    "Stringerverbindung (Assembly)":   "assembly",
    "LBR Cleat Installation":          "lbr",
}

# Structured data files per DO (DO name → list of JSON filenames)
DO_STRUCTURED: dict[str, list[str]] = {
    "NDT Inspection S1 — FAIL":    ["ndt-s1-report.json"],
    "NDT Re-inspection S1 — PASS": ["ndt-s1-recheck.json"],
    "NDT Inspection S2 — PASS":    ["ndt-s2-report.json"],
    "Stringer Welding S1":         ["stringer-s1-quality.json"],
    "Stringer Welding S2":         ["stringer-s2-quality.json"],
}

# File references per DO (DO name → list of Markdown filenames)
DO_FILES: dict[str, list[str]] = {
    "AFP Layup S1":          ["afp-layup-recipe-s1.md"],
    "AFP Layup S2":          ["afp-layup-recipe-s2.md"],
    "Rework S1":             ["rework-s1-protocol.md"],
    "Stringer Welding S1":   ["welding-protocol.md"],
    "LBR Cleat Installation":["lbr-cleat-spec.md"],
}

# Device names for timeseries 5-tuple by prefix
DEVICE_FOR_PREFIX: dict[str, str] = {
    "afp-s1":          "AFP-AFPT-MTLH-S1",
    "afp-s2":          "AFP-AFPT-MTLH-S2",
    "stringer-s1":     "cUSW-Robot-S1",
    "stringer-s2":     "cUSW-Robot-S2",
    "frame-bruecke":   "BridgeWelder-bruecke",
    "frame-punkt":     "BridgeWelder-punkt",
    "assembly":        "AssemblyRig-ZLP",
    "lbr":             "KUKA-LBR-iiwa-14",
}


# ---------------------------------------------------------------------------
# Helpers

def _log(action: str, name: str, kind: str, ident: object = "") -> None:
    suffix = f", {ident}" if ident != "" else ""
    print(f"{action:<6} {name} ({kind}{suffix})", flush=True)


def _query(name: str) -> str:
    return f'{{"property":"name","value":"{name}","operator":"eq"}}'


# ---------------------------------------------------------------------------
# API wrapper dataclass

from dataclasses import dataclass


@dataclass
class Apis:
    client: ApiClient
    collection: CollectionApi
    data_object: DataObjectApi
    file_container: FileContainerApi
    file_reference: FileReferenceApi
    structured_container: StructuredDataContainerApi
    structured_reference: StructuredDataReferenceApi
    timeseries_container: TimeseriesContainerApi
    timeseries_reference: TimeseriesReferenceApi
    search: SearchApi


# ---------------------------------------------------------------------------
# Lookup helpers

def _find_collection_by_name(apis: Apis, name: str) -> Collection | None:
    body = CollectionSearchBody(
        searchParams=CollectionSearchParams(query=_query(name))
    )
    res = apis.search.search_collections(body)
    for c in res.results or []:
        if c.name == name:
            return c
    return None


def _find_container(apis: Apis, name: str, ctype: ContainerType):
    from shepard_client import ContainerSearchBody, ContainerSearchParams  # local import
    body = ContainerSearchBody(
        searchParams=ContainerSearchParams(query=_query(name), queryType=ctype)
    )
    res = apis.search.search_containers(body)
    for c in res.results or []:
        if c.name == name:
            return c
    return None


def _find_data_object_by_name(apis: Apis, coll_id: int, name: str) -> DataObject | None:
    objs = apis.data_object.get_all_data_objects(coll_id) or []
    for o in objs:
        if o.name == name:
            return o
    return None


def _ensure_public(api_obj, get_perms_fn, set_perms_fn, entity_id: int) -> None:
    try:
        perms: Permissions = get_perms_fn(entity_id)
    except Exception as e:
        _log("SKIP", f"ensure-public (entity_id={entity_id})", f"403 — ({str(e)[:60]})")
        return
    if perms.permission_type != PermissionType.PUBLIC:
        try:
            perms.permission_type = PermissionType.PUBLIC
            set_perms_fn(entity_id, perms)
        except Exception as e:
            _log("SKIP", f"set-public (entity_id={entity_id})", f"403 — ({str(e)[:60]})")


# ---------------------------------------------------------------------------
# Container helpers

def ensure_timeseries_container(apis: Apis, name: str) -> TimeseriesContainer:
    existing = _find_container(apis, name, ContainerType.TIMESERIES)
    if existing is not None:
        _ensure_public(
            apis.timeseries_container,
            apis.timeseries_container.get_timeseries_permissions,
            apis.timeseries_container.edit_timeseries_permissions,
            existing.id,
        )
        _log("SKIP", name, "TimeseriesContainer", existing.id)
        return existing
    tsc = TimeseriesContainer(name=name)
    tsc = apis.timeseries_container.create_timeseries_container(tsc)
    _ensure_public(
        apis.timeseries_container,
        apis.timeseries_container.get_timeseries_permissions,
        apis.timeseries_container.edit_timeseries_permissions,
        tsc.id,
    )
    _log("OK", name, "TimeseriesContainer", tsc.id)
    return tsc


def ensure_file_container(apis: Apis, name: str) -> FileContainer:
    existing = _find_container(apis, name, ContainerType.FILE)
    if existing is not None:
        _ensure_public(
            apis.file_container,
            apis.file_container.get_file_permissions,
            apis.file_container.edit_file_permissions,
            existing.id,
        )
        _log("SKIP", name, "FileContainer", existing.id)
        return existing
    fc = FileContainer(name=name)
    fc = apis.file_container.create_file_container(fc)
    _ensure_public(
        apis.file_container,
        apis.file_container.get_file_permissions,
        apis.file_container.edit_file_permissions,
        fc.id,
    )
    _log("OK", name, "FileContainer", fc.id)
    return fc


def ensure_structured_container(apis: Apis, name: str) -> StructuredDataContainer:
    existing = _find_container(apis, name, ContainerType.STRUCTUREDDATA)
    if existing is not None:
        _ensure_public(
            apis.structured_container,
            apis.structured_container.get_structured_data_permissions,
            apis.structured_container.edit_structured_data_permissions,
            existing.id,
        )
        _log("SKIP", name, "StructuredDataContainer", existing.id)
        return existing
    sc = StructuredDataContainer(name=name)
    sc = apis.structured_container.create_structured_data_container(sc)
    _ensure_public(
        apis.structured_container,
        apis.structured_container.get_structured_data_permissions,
        apis.structured_container.edit_structured_data_permissions,
        sc.id,
    )
    _log("OK", name, "StructuredDataContainer", sc.id)
    return sc


# ---------------------------------------------------------------------------
# Collection

def ensure_collection(apis: Apis) -> Collection:
    existing = _find_collection_by_name(apis, COLLECTION_NAME)
    if existing is not None:
        if (existing.description or "") != COLLECTION_DESCRIPTION:
            existing.description = COLLECTION_DESCRIPTION
            existing = apis.collection.update_collection(existing.id, existing)
            _log("UPDATE", COLLECTION_NAME, "Collection", existing.id)
        else:
            _log("SKIP", COLLECTION_NAME, "Collection", existing.id)
        return existing
    coll = Collection(
        name=COLLECTION_NAME,
        description=COLLECTION_DESCRIPTION,
        attributes=COLLECTION_ATTRIBUTES,
    )
    coll = apis.collection.create_collection(coll)
    perms = apis.collection.get_collection_permissions(coll.id)
    perms.permission_type = PermissionType.PUBLIC
    apis.collection.edit_collection_permissions(coll.id, perms)
    _log("OK", COLLECTION_NAME, "Collection", coll.id)
    return coll


# ---------------------------------------------------------------------------
# Reset

def reset(apis: Apis) -> None:
    """Delete the showcase Collection and shared containers."""
    existing = _find_collection_by_name(apis, COLLECTION_NAME)
    if existing is None:
        _log("SKIP", COLLECTION_NAME, "Collection (no prior seed)")
    else:
        apis.collection.delete_collection(existing.id)
        _log("OK", COLLECTION_NAME, "Collection deleted", existing.id)
    for name, ctype, delete_fn in (
        (TSC_NAME, ContainerType.TIMESERIES, apis.timeseries_container.delete_timeseries_container),
        (FC_NAME,  ContainerType.FILE,        apis.file_container.delete_file_container),
        (SC_NAME,  ContainerType.STRUCTUREDDATA, apis.structured_container.delete_structured_data_container),
    ):
        c = _find_container(apis, name, ctype)
        if c is None:
            _log("SKIP", name, f"{ctype} (no prior seed)")
            continue
        try:
            delete_fn(c.id)
            _log("OK", name, f"{ctype} deleted", c.id)
        except Exception as exc:
            _log("SKIP", name, f"delete failed: {str(exc)[:80]}")


# ---------------------------------------------------------------------------
# DataObjects

def ensure_data_objects(apis: Apis, coll: Collection) -> dict[str, DataObject]:
    """Create or retrieve all 12 DataObjects.  Predecessor links are
    applied in a second pass after all DOs exist."""
    dos: dict[str, DataObject] = {}
    for (name, status, attributes) in DO_SPECS:
        existing = _find_data_object_by_name(apis, coll.id, name)
        if existing is not None:
            _log("SKIP", name, "DataObject", existing.id)
            dos[name] = existing
            continue
        do = DataObject(
            name=name,
            description=f"MFFD process step: {name} (synthetic).",
            attributes=attributes,
        )
        do = apis.data_object.create_data_object(coll.id, do)
        _log("OK", name, "DataObject", do.id)
        dos[name] = do
    return dos


def wire_predecessors(apis: Apis, coll: Collection, dos: dict[str, DataObject]) -> None:
    """Add predecessor links.  Idempotent: skips if already present."""
    for do_name, pred_names in PREDECESSORS.items():
        do = dos.get(do_name)
        if do is None:
            continue
        pred_ids = [dos[p].id for p in pred_names if p in dos]
        current_preds = list(do.predecessor_ids or [])
        missing = [pid for pid in pred_ids if pid not in current_preds]
        if not missing:
            continue
        do.predecessor_ids = current_preds + missing
        do = apis.data_object.update_data_object(coll.id, do.id, do)
        dos[do_name] = do
        _log("UPDATE", f"{do_name} (predecessor links)", "DataObject", do.id)


# ---------------------------------------------------------------------------
# Timeseries upload

def _read_csv_points(csv_path: Path) -> list[TimeseriesDataPoint]:
    out: list[TimeseriesDataPoint] = []
    with csv_path.open() as f:
        r = csv.reader(f)
        next(r, None)  # header
        for row in r:
            if not row:
                continue
            out.append(TimeseriesDataPoint(timestamp=int(row[0]), value=float(row[1])))
    return out


def _chunked(seq: list, size: int) -> Iterable[list]:
    for i in range(0, len(seq), size):
        yield seq[i: i + size]


def upload_do_timeseries(
    apis: Apis,
    coll: Collection,
    do: DataObject,
    tsc: TimeseriesContainer,
    data_dir: Path,
    prefix: str,
) -> TimeseriesReference | None:
    """Upload all CSV files matching `prefix-*.csv` and create a TimeseriesReference."""
    ref_name = f"{prefix}-sensors"
    existing_refs = apis.timeseries_reference.get_all_timeseries_references(coll.id, do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "TimeseriesReference", r.id)
            return r

    csv_files = sorted((data_dir / "timeseries").glob(f"{prefix}-*.csv"))
    if not csv_files:
        _log("SKIP", ref_name, f"TimeseriesReference (no CSVs matching {prefix}-*.csv)")
        return None

    device = DEVICE_FOR_PREFIX.get(prefix, prefix)
    timeseries_in_do: list[Timeseries] = []
    t0_ns: int | None = None
    tn_ns: int | None = None

    for csv_path in csv_files:
        # Channel name = filename stem minus the prefix and hyphen
        channel = csv_path.stem[len(prefix) + 1:]  # e.g. "afp-s1-nip_point_temp_C" → "nip_point_temp_C"
        points = _read_csv_points(csv_path)
        if not points:
            continue
        t0_ns = points[0].timestamp if t0_ns is None else min(t0_ns, points[0].timestamp)
        tn_ns = points[-1].timestamp if tn_ns is None else max(tn_ns, points[-1].timestamp)

        ts = Timeseries(
            measurement="mffd",
            device=device,
            location="ZLP-Augsburg",
            symbolicName=f"{prefix}_{channel}",
            field=channel,
        )
        chunks = list(_chunked(points, TIMESERIES_CHUNK))
        created = apis.timeseries_container.create_timeseries(
            timeseries_container_id=tsc.id,
            timeseries_with_data_points=TimeseriesWithDataPoints(
                timeseries=ts, points=chunks[0]
            ),
        )
        for extra in chunks[1:]:
            apis.timeseries_container.create_timeseries(
                timeseries_container_id=tsc.id,
                timeseries_with_data_points=TimeseriesWithDataPoints(
                    timeseries=Timeseries(
                        measurement=created.measurement,
                        device=created.device,
                        location=created.location,
                        symbolicName=created.symbolic_name,
                        field=channel,
                    ),
                    points=extra,
                ),
            )
        timeseries_in_do.append(created)

    if not timeseries_in_do:
        return None

    tsr = TimeseriesReference(
        name=ref_name,
        start=t0_ns,
        end=tn_ns,
        timeseries=timeseries_in_do,
        timeseriesContainerId=tsc.id,
    )
    tsr = apis.timeseries_reference.create_timeseries_reference(coll.id, do.id, tsr)
    _log("OK", ref_name, "TimeseriesReference", tsr.id)
    return tsr


# ---------------------------------------------------------------------------
# Spatial axis role annotations (TS-AXIS-AUTO)

# Maps a channel field name to its Trace3D axis role.
# Applies to any channel whose `field` matches the key.
# For the LBR iiwa the spatial channels are force_{x,y,z}_N.
# For AFP TCP the channels would be tcp_{x,y,z}_mm / tcp_{rx,ry,rz}_deg
# (not present in synthetic CSV data yet; included here for real-data runs).
AXIS_ROLES_BY_FIELD: dict[str, str] = {
    # LBR iiwa force/torque spatial axes
    "force_x_N": "x",
    "force_y_N": "y",
    "force_z_N": "z",
    "torque_x_Nm": "rot_a",
    "torque_y_Nm": "rot_b",
    "torque_z_Nm": "rot_c",
    # AFP TCP spatial channels (present in real ZLP data)
    "tcp_x_mm":  "x",
    "tcp_y_mm":  "y",
    "tcp_z_mm":  "z",
    "tcp_rx_deg": "rot_a",
    "tcp_ry_deg": "rot_b",
    "tcp_rz_deg": "rot_c",
}


def annotate_spatial_roles(
    host: str,
    api_key: str,
    container_id: int,
    prefix: str,
) -> None:
    """
    TS-AXIS-AUTO — write axis-role annotations for channels in a timeseries
    container whose device matches ``prefix`` and whose field name maps to a
    known spatial role.

    Filtering by device is critical: the container holds channels from multiple
    instruments (LBR iiwa, AFP robot, …) that share the same field names (e.g.
    ``force_x_N``). Annotating all of them as role "x" would leave duplicate
    role entries in Neo4j. The backend's first-wins policy would silently pick
    whichever channel appears first in the channel listing rather than the one
    the caller intended.

    Calls:
      GET  /v2/timeseries-containers/{containerId}/channels
      POST /v2/timeseries-containers/{containerId}/channels/{shepardId}/annotations

    Idempotent: existing annotations for the same predicate are not checked;
    the UI deduplicates via first-wins at read time. Running twice is safe.
    """
    device_filter = DEVICE_FOR_PREFIX.get(prefix, prefix)
    v2 = _v2_base(host)
    headers = {"X-API-KEY": api_key, "Content-Type": "application/json"}

    # Fetch all channels for this container (paginated)
    all_channels: list[dict] = []
    page = 0
    page_size = 200
    while True:
        resp = _http.get(
            f"{v2}/timeseries-containers/{container_id}/channels",
            params={"page": page, "size": page_size},
            headers=headers,
            timeout=30,
        )
        resp.raise_for_status()
        batch = resp.json()
        if not batch:
            break
        all_channels.extend(batch)
        if len(batch) < page_size:
            break
        page += 1

    annotated = 0
    for ch in all_channels:
        # Only annotate channels belonging to this device prefix
        if ch.get("device") != device_filter:
            continue
        shepard_id = ch.get("shepardId")
        field = ch.get("field", "")
        role = AXIS_ROLES_BY_FIELD.get(field)
        if not shepard_id or not role:
            continue
        resp = _http.post(
            f"{v2}/timeseries-containers/{container_id}/channels/{shepard_id}/annotations",
            json={"value": role},
            headers=headers,
            timeout=30,
        )
        if resp.status_code in (200, 201):
            annotated += 1
        else:
            _log("WARN", f"channel {shepard_id} device={device_filter} field={field}", "axis annotation", resp.status_code)

    _log("OK", f"prefix={prefix} device={device_filter}", "axis annotations", annotated)


# ---------------------------------------------------------------------------
# Structured data upload

def upload_do_structured(
    apis: Apis,
    coll: Collection,
    do: DataObject,
    sc: StructuredDataContainer,
    data_dir: Path,
    json_files: list[str],
) -> None:
    ref_name = f"{do.name.lower().replace(' ', '-').replace('—', '').replace('(', '').replace(')', '').replace('--', '-')}-runlog"
    existing_refs = apis.structured_reference.get_all_structured_data_references(coll.id, do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "StructuredDataReference", r.id)
            return

    oids: list[str] = []
    for fname in json_files:
        path = data_dir / "structured" / fname
        if not path.exists():
            _log("SKIP", fname, "structured JSON (missing)")
            continue
        payload = StructuredDataPayload(
            structuredData=StructuredData(name=fname),
            payload=path.read_text(encoding="utf-8"),
        )
        sd = apis.structured_container.create_structured_data(sc.id, payload)
        oids.append(sd.oid)

    if not oids:
        return

    sr = StructuredDataReference(
        name=ref_name,
        dataObjectId=do.id,
        structuredDataContainerId=sc.id,
        structuredDataOids=oids,
    )
    apis.structured_reference.create_structured_data_reference(coll.id, do.id, sr)
    _log("OK", ref_name, "StructuredDataReference", oids[0])


# ---------------------------------------------------------------------------
# File upload

def _v2_base(host: str) -> str:
    return re.sub(r"/shepard/api/?$", "/v2", host.rstrip("/"))


def _get_fc_app_id(host: str, api_key: str, fc_id: int) -> str:
    resp = _http.get(f"{host}/fileContainers/{fc_id}", headers={"X-API-KEY": api_key}, timeout=30)
    resp.raise_for_status()
    return resp.json()["appId"]


def _upload_file_presigned(v2_base: str, api_key: str, container_app_id: str, path: Path) -> str:
    fname = path.name
    # Step 1: obtain presigned PUT URL + oid
    r1 = _http.post(
        f"{v2_base}/file-containers/{container_app_id}/upload-url",
        json={"fileName": fname},
        headers={"X-API-KEY": api_key},
        timeout=30,
    )
    r1.raise_for_status()
    d = r1.json()
    upload_url, oid = d["uploadUrl"], d["oid"]
    # Step 2: PUT bytes directly to S3 — Content-Disposition is part of the signed headers
    data = path.read_bytes()
    r2 = _http.put(upload_url, data=data, timeout=60,
                   headers={"Content-Disposition": f'attachment; filename="{fname}"'})
    r2.raise_for_status()
    # Step 3: commit — registers the ShepardFile node
    ctype = "text/markdown" if fname.endswith(".md") else "application/octet-stream"
    r3 = _http.post(
        f"{v2_base}/file-containers/{container_app_id}/upload-url/commit",
        json={"oid": oid, "fileName": fname, "contentType": ctype, "fileSize": len(data)},
        headers={"X-API-KEY": api_key},
        timeout=30,
    )
    r3.raise_for_status()
    return oid


def upload_do_files(
    apis: Apis,
    coll: Collection,
    do: DataObject,
    fc: FileContainer,
    data_dir: Path,
    md_files: list[str],
) -> None:
    ref_name = f"{do.name.lower().replace(' ', '-').replace('—', '').replace('(', '').replace(')', '').replace('--', '-')}-files"
    existing_refs = apis.file_reference.get_all_file_references(coll.id, do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "FileReference", r.id)
            return

    host = apis.client.configuration.host.rstrip("/")
    api_key = (apis.client.configuration.api_key or {}).get("apikey", "")
    v2 = _v2_base(host)
    fc_app_id = _get_fc_app_id(host, api_key, fc.id)

    oids: list[str] = []
    for fname in md_files:
        path = data_dir / "files" / fname
        if not path.exists():
            _log("SKIP", fname, "file (missing)")
            continue
        oid = _upload_file_presigned(v2, api_key, fc_app_id, path)
        oids.append(oid)

    if not oids:
        return

    fr = FileReference(
        name=ref_name,
        dataObjectId=do.id,
        fileContainerId=fc.id,
        fileOids=oids,
    )
    fr = apis.file_reference.create_file_reference(coll.id, do.id, fr)
    _log("OK", ref_name, "FileReference", fr.id)


# ---------------------------------------------------------------------------
# Main seed orchestration

def seed(apis: Apis, data_dir: Path) -> None:
    print("\n=== MFFD AFP Showcase Seed ===\n", flush=True)

    # Shared containers
    tsc = ensure_timeseries_container(apis, TSC_NAME)
    fc  = ensure_file_container(apis, FC_NAME)
    sc  = ensure_structured_container(apis, SC_NAME)

    # Collection
    coll = ensure_collection(apis)

    # DataObjects (12 steps)
    dos = ensure_data_objects(apis, coll)

    # Predecessor wiring
    wire_predecessors(apis, coll, dos)

    # Timeseries uploads
    print("\n--- Timeseries ---", flush=True)
    for do_name, prefix in DO_TIMESERIES.items():
        do = dos.get(do_name)
        if do is None:
            continue
        upload_do_timeseries(apis, coll, do, tsc, data_dir, prefix)

    # Spatial axis role annotations (TS-AXIS-AUTO)
    print("\n--- Spatial axis role annotations ---", flush=True)
    host = apis.client.configuration.host.rstrip("/")
    api_key = apis.client.configuration.api_key.get("apikey", "")
    annotate_spatial_roles(host, api_key, tsc.id, "lbr")
    # AFP channels use the same container; annotate when real TCP data is present.
    annotate_spatial_roles(host, api_key, tsc.id, "afp-s1")

    # Structured data uploads
    print("\n--- Structured data ---", flush=True)
    for do_name, json_files in DO_STRUCTURED.items():
        do = dos.get(do_name)
        if do is None:
            continue
        upload_do_structured(apis, coll, do, sc, data_dir, json_files)

    # File uploads
    print("\n--- Files ---", flush=True)
    for do_name, md_files in DO_FILES.items():
        do = dos.get(do_name)
        if do is None:
            continue
        upload_do_files(apis, coll, do, fc, data_dir, md_files)

    print(f"\nDone.  Collection id={coll.id}", flush=True)

    # Hero image — upload to Garage S3 then PATCH heroImageUrl on the collection.
    best_effort_hero_image(apis, coll, Path(__file__).parent)


# ---------------------------------------------------------------------------
# Hero image

HERO_IMAGE_URL = "https://shepard.nuclide.systems/static/mffd-hero.webp"
HERO_IMAGE_S3_KEY = "mffd-hero.webp"


def _collection_app_id(coll: Collection, apis: "Apis") -> str | None:
    """Read raw JSON to extract appId (pydantic model drops unknown fields)."""
    host = apis.client.configuration.host.rstrip("/")
    api_key = apis.client.configuration.api_key.get("apikey", "")
    try:
        resp = _http.get(f"{host}/collections/{coll.id}", headers={"X-API-KEY": api_key}, timeout=5)
        resp.raise_for_status()
        return resp.json().get("appId") or None
    except Exception:
        return None


def best_effort_hero_image(apis: "Apis", coll: Collection, seed_dir: Path) -> None:
    """Upload hero.webp to Garage S3 (best-effort) and PATCH heroImageUrl."""
    hero_path = seed_dir / "hero.webp"
    host = apis.client.configuration.host.rstrip("/")
    api_key = apis.client.configuration.api_key.get("apikey", "")

    # S3 upload — requires boto3 + Garage endpoint accessible from seed host.
    # Creds default to the nuclide dev box; override via GARAGE_* env vars.
    if hero_path.exists():
        s3_endpoint  = os.environ.get("GARAGE_ENDPOINT",    "http://127.0.0.1:3900")
        s3_access    = os.environ.get("GARAGE_ACCESS_KEY",  "GK6f1eb80a3f7237cda3cf5830")
        s3_secret    = os.environ.get("GARAGE_SECRET_KEY",  "a01a7b7b0d5a694aa8817b7657835b688ee4bf422fdf3df3a4c8b8137bf1e5f5")
        s3_bucket    = os.environ.get("GARAGE_BUCKET",      "shepard-files")
        try:
            import boto3  # type: ignore
            from botocore.config import Config  # type: ignore
            s3 = boto3.client(
                "s3", endpoint_url=s3_endpoint,
                aws_access_key_id=s3_access, aws_secret_access_key=s3_secret,
                region_name="garage-region",
                config=Config(s3={"addressing_style": "path"}),
            )
            s3.put_object(
                Bucket=s3_bucket, Key=HERO_IMAGE_S3_KEY,
                Body=hero_path.read_bytes(),
                ContentType="image/webp",
                CacheControl="public, max-age=31536000, immutable",
            )
            _log("OK", "hero.webp", f"S3/{HERO_IMAGE_S3_KEY}", s3_bucket)
        except Exception as exc:
            _log("SKIP", "hero.webp", f"S3 upload ({str(exc)[:60]})")

    # PATCH heroImageUrl via v2 REST.
    app_id = _collection_app_id(coll, apis)
    if not app_id:
        _log("SKIP", HERO_IMAGE_URL, "heroImageUrl PATCH (no appId)")
        return
    v2 = _v2_base(host)
    try:
        resp = _http.patch(
            f"{v2}/collections/{app_id}",
            json={"heroImageUrl": HERO_IMAGE_URL},
            headers={"X-API-KEY": api_key},
            timeout=10,
        )
        resp.raise_for_status()
        _log("OK", COLLECTION_NAME, f"heroImageUrl → {HERO_IMAGE_URL}")
    except Exception as exc:
        _log("SKIP", HERO_IMAGE_URL, f"heroImageUrl PATCH ({str(exc)[:60]})")


# ---------------------------------------------------------------------------
# CLI entry point

def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(
        description="Idempotent seeder for the MFFD AFP manufacturing showcase.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  python seed.py --host https://shepard-api.nuclide.systems --apikey XXX\n"
            "  python seed.py --host http://localhost:8080 --apikey XXX --reset\n"
        ),
    )
    ap.add_argument("--host",   required=True, help="Shepard backend base URL (e.g. http://localhost:8080/shepard/api)")
    ap.add_argument("--apikey", required=True, help="API key with write access")
    ap.add_argument("--reset",  action="store_true", help="Delete existing collection and containers before seeding")
    ap.add_argument("--data",   default=None, help="Path to data/ directory (default: <script_dir>/data)")
    args = ap.parse_args(argv)

    data_dir = Path(args.data) if args.data else Path(__file__).parent / "data"
    if not data_dir.exists():
        print(f"ERROR: data directory not found: {data_dir}", file=sys.stderr)
        print("Run: python3 data/generate.py --out data", file=sys.stderr)
        sys.exit(1)

    cfg = Configuration(host=args.host, api_key={"apikey": args.apikey})
    client = ApiClient(configuration=cfg)

    apis = Apis(
        client=client,
        collection=CollectionApi(client),
        data_object=DataObjectApi(client),
        file_container=FileContainerApi(client),
        file_reference=FileReferenceApi(client),
        structured_container=StructuredDataContainerApi(client),
        structured_reference=StructuredDataReferenceApi(client),
        timeseries_container=TimeseriesContainerApi(client),
        timeseries_reference=TimeseriesReferenceApi(client),
        search=SearchApi(client),
    )

    if args.reset:
        print("=== RESET ===", flush=True)
        reset(apis)
        print(flush=True)

    seed(apis, data_dir)


if __name__ == "__main__":
    main()
