"""Idempotent seeder for the LUMEN-inspired hot-fire showcase.

Synthesizes the campaign Collection plus seven test runs into a running
shepard backend. Re-running the script produces the same final entity tree
(by-name lookup before create); ``--reset`` deletes the showcase Collection
first.

Usage
-----
    python seed.py --host https://backend.example/shepard/api --apikey XXX
    python seed.py --host http://localhost:8080/shepard/api --apikey XXX --reset

The script only depends on numpy, the existing ``shepard-client`` Python
package, and the standard library.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterable

# numpy is used by data/generate.py and indirectly by shepard-client.
# We import it lazily inside main() so --help works even without numpy.

# shepard-client imports — kept compact so this module reads as a tour.
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
    LabJournalEntry,
    LabJournalEntryApi,
    Permissions,
    PermissionType,
    SearchApi,
    SemanticAnnotation,
    SemanticAnnotationApi,
    SemanticRepository,
    SemanticRepositoryApi,
    SemanticRepositoryType,
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
# Constants — tied to data/generate.py so the two stay in sync.

COLLECTION_NAME = "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)"
COLLECTION_DESCRIPTION = (
    "Synthetic showcase dataset for shepard. **NOT REAL DLR/LUMEN data.** "
    "Loosely inspired by the DLR LUMEN demonstrator at Lampoldshausen "
    "(https://www.dlr.de/en/ra/research-transfer/projects/project-archive/"
    "liquid-upper-stage-demonstrator-engine-lumen). All numerical values "
    "are deterministic synthetic outputs of `data/generate.py` "
    "(numpy.random.default_rng(2024)). Used to exercise shepard's feature "
    "surface end-to-end."
)
N_RUNS = 15
HOLD_DAYS = {5, 12}  # TR-005 bearing teardown, TR-012 pre-cert inspection
ANOMALY_RUN = 4       # TR-004 fuel-turbopump vibration anomaly

CHANNELS: list[tuple[str, str]] = [
    ("pc_chamber",    "bar"),
    ("pc_nozzle",     "bar"),
    ("rpm_fuel_pump", "rpm"),
    ("rpm_lox_pump",  "rpm"),
    ("mdot_fuel",     "kg/s"),
    ("mdot_lox",      "kg/s"),
    ("tc_chamber",    "K"),
    ("vib_fuel_pump", "g_rms"),
    ("vib_lox_pump",  "g_rms"),
    ("t_coolant_out", "K"),
    ("p_inj_fuel",    "bar"),
    ("p_inj_lox",     "bar"),
    ("p_tank_fuel",   "bar"),
    ("p_tank_lox",    "bar"),
    ("tc_nozzle",     "K"),
    ("tc_injector",   "K"),
    ("t_coolant_in",  "K"),
    ("t_lox_inlet",   "K"),
    ("thrust_kn",     "kN"),
    ("valve_fuel",    "pct"),
    ("valve_lox",     "pct"),
    ("vib_chamber",   "g_rms"),
    ("strain_nozzle", "ustrain"),
    ("acc_gimbal_x",  "g"),
    ("acc_gimbal_y",  "g"),
]

PHASES: list[tuple[str, float, float]] = [
    ("precool",      0.0,  2.0),
    ("ignition",     2.0,  3.0),
    ("ramp_up",      3.0,  9.0),
    ("steady_state", 9.0, 22.0),
    ("throttle",    22.0, 26.0),
    ("shutdown",    26.0, 28.0),
    ("purge",       28.0, 30.0),
]

# Semantic IRI namespaces.
# Phase properties and values use the canonical shepard-experiment ontology
# (pre-seeded by OntologySeedService) for phases that have an exact concept.
# The seven hotfire sub-phases are more granular than the generic ExperimentPhase
# scheme, so five are mapped to the closest shex: concept and two that are
# rocket-engine-specific (ignition, throttle) keep a DLR showcase namespace.
DLR_NS = "https://shepard.dlr.de/showcase/lumen-inspired#"
SHEX_NS = "https://shepard.dlr.de/ontologies/experiment#"

PROP_PHASE = SHEX_NS + "ExperimentPhase"      # skos:ConceptScheme as predicate
PROP_QUALITY = SHEX_NS + "QualityFlag"        # quality-flag predicate

VAL_PHASE: dict[str, str] = {
    "precool":      SHEX_NS + "Preparation",   # pre-test cooling → Preparation
    "ignition":     DLR_NS  + "phase/ignition", # engine-specific, no shex match
    "ramp_up":      SHEX_NS + "TestRun",        # thrust ramp → start of TestRun
    "steady_state": SHEX_NS + "TestRun",        # nominal burn → TestRun
    "throttle":     DLR_NS  + "phase/throttle", # throttle sweep, engine-specific
    "shutdown":     SHEX_NS + "Cooldown",       # engine shutdown → Cooldown
    "purge":        SHEX_NS + "PostProcessing", # propellant purge → PostProcessing
}
VAL_VIBRATION_ANOMALY = SHEX_NS + "QualitySuspect"  # TR-004 anomaly run

# Bulk upload chunk size for timeseries (sample count per request).
TIMESERIES_CHUNK = 1000

# ---------------------------------------------------------------------------
# Helpers


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
    journal: LabJournalEntryApi
    annotation: SemanticAnnotationApi
    semantic_repo: SemanticRepositoryApi
    search: SearchApi


def _log(action: str, name: str, kind: str, ident: object = "") -> None:
    """Print a uniform `OK / SKIP / UPDATE name (kind, id)` line to stdout."""
    suffix = f", {ident}" if ident != "" else ""
    print(f"{action:<6} {name} ({kind}{suffix})", flush=True)


def _query(name: str) -> str:
    return f'{{"property":"name","value":"{name}","operator":"eq"}}'


def _find_collection_by_name(apis: Apis, name: str) -> Collection | None:
    body = CollectionSearchBody(
        searchParams=CollectionSearchParams(query=_query(name))
    )
    res = apis.search.search_collections(body)
    for c in res.results or []:
        if c.name == name:
            return c
    return None


def _find_child_data_object(apis: Apis, collection_id: int, name: str, parent_id: int | None) -> DataObject | None:
    """List the collection's data objects and return the first match by
    (name, parentId). The list endpoint is small for our showcase (≤10
    objects), so we don't bother with paging."""
    objs = apis.data_object.get_all_data_objects(collection_id)
    for o in objs or []:
        if o.name == name and (o.parent_id or None) == parent_id:
            return o
    return None


def _ensure_public(api, get_perms_fn, set_perms_fn, entity_id: int) -> None:
    """Make a container readable by everyone in the dev stack — keeps the
    showcase explorable without per-user role plumbing.

    The seed's three logical roles (campaign_lead / analyst / reviewer)
    are documented in the README and surfaced via the two API keys created
    at the end of the seed; group-based RBAC requires an admin and is left
    as the operator's responsibility.

    Soft-fail on 403: an entity created by a different admin user in a
    prior seed run leaves the seeder without MANAGE rights to read the
    permissions object. The entity is still usable by the original
    owner; we just can't flip its permission-type to PUBLIC. Log + skip
    rather than crash the whole seed."""
    try:
        perms: Permissions = get_perms_fn(entity_id)
    except Exception as e:
        _log("SKIP", f"ensure-public (entity_id={entity_id})", f"403 — stale ownership? ({str(e)[:60]})")
        return
    if perms.permission_type != PermissionType.PUBLIC:
        try:
            perms.permission_type = PermissionType.PUBLIC
            set_perms_fn(entity_id, perms)
        except Exception as e:
            _log("SKIP", f"set-public (entity_id={entity_id})", f"403 — ({str(e)[:60]})")


# ---------------------------------------------------------------------------
# Seed steps


def reset(apis: Apis) -> None:
    """Delete the showcase Collection by name and the three named containers.

    Containers are deleted because re-uploading the same Timeseries into a
    container that already has it would otherwise produce duplicates. The
    three names below are unique to the showcase, so this is safe."""
    existing = _find_collection_by_name(apis, COLLECTION_NAME)
    if existing is None:
        _log("SKIP", COLLECTION_NAME, "Collection (no prior seed)")
    else:
        apis.collection.delete_collection(existing.id)
        _log("OK", COLLECTION_NAME, "Collection deleted", existing.id)
    for name, ctype, delete_fn in (
        ("lumen-inspired-sensors", ContainerType.TIMESERIES, apis.timeseries_container.delete_timeseries_container),
        ("lumen-inspired-artifacts", ContainerType.FILE, apis.file_container.delete_file_container),
        ("lumen-inspired-runlogs", ContainerType.STRUCTUREDDATA, apis.structured_container.delete_structured_data_container),
    ):
        c = _find_container(apis, name, ctype)
        if c is None:
            _log("SKIP", name, f"{ctype} (no prior seed)")
            continue
        try:
            delete_fn(c.id)
            _log("OK", name, f"{ctype} deleted", c.id)
        except Exception as exc:  # pragma: no cover
            _log("SKIP", name, f"delete failed: {str(exc)[:80]}")


def ensure_collection(apis: Apis) -> Collection:
    existing = _find_collection_by_name(apis, COLLECTION_NAME)
    if existing is not None:
        # Update writeable fields if drifted.
        if (existing.description or "") != COLLECTION_DESCRIPTION:
            existing.description = COLLECTION_DESCRIPTION
            existing = apis.collection.update_collection(existing.id, existing)
            _log("UPDATE", COLLECTION_NAME, "Collection", existing.id)
        else:
            _log("SKIP", COLLECTION_NAME, "Collection", existing.id)
        return existing
    coll = Collection(name=COLLECTION_NAME, description=COLLECTION_DESCRIPTION)
    coll = apis.collection.create_collection(coll)
    perms = apis.collection.get_collection_permissions(coll.id)
    perms.permission_type = PermissionType.PUBLIC
    apis.collection.edit_collection_permissions(coll.id, perms)
    _log("OK", COLLECTION_NAME, "Collection", coll.id)
    return coll


def ensure_run_data_objects(apis: Apis, coll: Collection) -> dict[int, DataObject]:
    """TR-001 .. TR-015 + the analysis sub-tree. Predecessors form a chain;
    TR-006 also receives the analysis DataObject as a predecessor so the
    narrative reads investigation -> bearing replaced -> re-tested."""
    runs: dict[int, DataObject] = {}
    prev: DataObject | None = None
    for n in range(1, N_RUNS + 1):
        name = f"TR-{n:03d}"
        scheduled = (datetime(2024, 7, 8, 9, 0, 0, tzinfo=timezone.utc) + timedelta(days=(n - 1) * 3)).isoformat()
        attributes = {
            "test_run_id": name,
            "date": scheduled,
            "bench": "P3-Lampoldshausen",
            "propellant": "LOX/LCH4",
            "target_thrust_kN": "25",
            "target_mixture_ratio": "3.4",
            "duration_s": "30" if n not in HOLD_DAYS else "0",
            "test_engineer": [
                "T. Marek", "S. Holzwarth", "A. Reuter", "T. Marek", "L. Voss",
                "T. Marek", "A. Reuter", "S. Holzwarth", "T. Marek", "L. Voss",
                "A. Reuter", "T. Marek", "S. Holzwarth", "A. Reuter", "T. Marek",
            ][n - 1],
            "notes_brief": _short_note_for_run(n),
            "is_fired": "true" if n not in HOLD_DAYS else "false",
        }
        existing = _find_child_data_object(apis, coll.id, name, parent_id=None)
        if existing is not None:
            # Update attributes if they drifted; predecessor chain is
            # write-once for our purposes.
            if existing.attributes != attributes:
                existing.attributes = attributes
                existing = apis.data_object.update_data_object(coll.id, existing.id, existing)
                _log("UPDATE", name, "DataObject", existing.id)
            else:
                _log("SKIP", name, "DataObject", existing.id)
            runs[n] = existing
            prev = existing
            continue
        do = DataObject(
            name=name,
            description=f"Hot-fire test run {name} (synthetic).",
            attributes=attributes,
            predecessorIds=[prev.id] if prev is not None else None,
        )
        do = apis.data_object.create_data_object(coll.id, do)
        _log("OK", name, "DataObject", do.id)
        runs[n] = do
        prev = do
    return runs


def _short_note_for_run(n: int) -> str:
    notes = {
        1:  "Bench commissioning fire. All sensors green.",
        2:  "Repeat reference fire. Vibration nominal.",
        3:  "Reference fire pre-anomaly. Fuel-pump vibration trending +0.4 g rms vs TR-001.",
        4:  "Vibration spike on fuel turbopump during ramp_up at t=8s, peak ~12 g rms.",
        5:  "Hold day. Bearing teardown / replacement / re-balance. No fire.",
        6:  "Re-test post bearing replacement. Fuel-pump vibration nominal.",
        7:  "Confirmation fire. Phase 1 campaign complete.",
        8:  "Phase 2 commissioning fire. All sensors nominal. Baseline re-established.",
        9:  "Mixture ratio sweep — o/f stepped to 3.6 during throttle point.",
        10: "Throttle-deep test — 40% thrust sustained 6 s. Stable combustion.",
        11: "New LOX batch LOX-2024-04 validated. All channels within Phase 1 envelope.",
        12: "Hold day. Pre-certification inspection: nozzle survey + injector dye-penetrant. No anomalies.",
        13: "Pre-certification reference fire. All channels within Phase 1 envelope. Cleared for qualification.",
        14: "Qualification fire 1. Full test matrix. Strain and gimbal nominal throughout.",
        15: "Qualification fire 2 — repeat confirmation. Campaign complete. All channels within spec.",
    }
    return notes[n]


def ensure_anomaly_investigation(
    apis: Apis, coll: Collection, runs: dict[int, DataObject], close_anomaly: bool
) -> DataObject:
    """The investigation DataObject lives under TR-004 and is a predecessor
    of TR-006. ``close_anomaly`` flips the deterministic `closed_at`
    attribute on the second-pass v2 export."""
    name = "Anomaly Investigation — TR-004 Fuel Turbopump"
    parent = runs[ANOMALY_RUN]
    closed_at = "2024-07-22T16:00:00+00:00" if close_anomaly else ""
    attributes = {
        "severity": "HIGH",
        "hypothesis": "thrust-bearing precursor",
        "scope": "fuel turbopump",
        "closed_at": closed_at,
    }
    existing = _find_child_data_object(apis, coll.id, name, parent_id=parent.id)
    if existing is not None:
        if existing.attributes != attributes:
            existing.attributes = attributes
            existing = apis.data_object.update_data_object(coll.id, existing.id, existing)
            _log("UPDATE", name, "DataObject", existing.id)
        else:
            _log("SKIP", name, "DataObject", existing.id)
        # Make sure TR-006 lists this DataObject as a predecessor.
        retest = runs[6]
        preds = list(retest.predecessor_ids or [])
        if existing.id not in preds:
            retest.predecessor_ids = preds + [existing.id]
            retest = apis.data_object.update_data_object(coll.id, retest.id, retest)
            runs[6] = retest
            _log("UPDATE", "TR-006 (predecessor link)", "DataObject", retest.id)
        return existing
    do = DataObject(
        name=name,
        description="Investigation sub-tree triggered by the TR-004 fuel-turbopump vibration spike (synthetic).",
        attributes=attributes,
        parentId=parent.id,
        predecessorIds=[parent.id],
    )
    do = apis.data_object.create_data_object(coll.id, do)
    _log("OK", name, "DataObject", do.id)
    # Now link TR-006 as a successor of the investigation.
    retest = runs[6]
    preds = list(retest.predecessor_ids or [])
    if do.id not in preds:
        retest.predecessor_ids = preds + [do.id]
        retest = apis.data_object.update_data_object(coll.id, retest.id, retest)
        runs[6] = retest
        _log("UPDATE", "TR-006 (predecessor link)", "DataObject", retest.id)
    return do


# ---- shared containers (one per kind, by-name lookup) ---------------------


def _find_container(apis: Apis, name: str, ctype: ContainerType):
    from shepard_client import ContainerSearchBody, ContainerSearchParams  # local import keeps top-level lean
    body = ContainerSearchBody(
        searchParams=ContainerSearchParams(query=_query(name), queryType=ctype)
    )
    res = apis.search.search_containers(body)
    for c in res.results or []:
        if c.name == name:
            return c
    return None


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


# ---- timeseries upload -----------------------------------------------------


def _read_csv_points(csv_path: Path) -> list[TimeseriesDataPoint]:
    out: list[TimeseriesDataPoint] = []
    with csv_path.open() as f:
        r = csv.reader(f)
        next(r, None)  # header
        for row in r:
            if not row:
                continue
            ts_ns = int(row[0])
            v = float(row[1])
            out.append(TimeseriesDataPoint(timestamp=ts_ns, value=v))
    return out


def _chunked(seq: list, size: int) -> Iterable[list]:
    for i in range(0, len(seq), size):
        yield seq[i : i + size]


def upload_run_timeseries(
    apis: Apis,
    coll: Collection,
    run_do: DataObject,
    tsc: TimeseriesContainer,
    data_dir: Path,
    run_idx: int,
) -> TimeseriesReference | None:
    """Create one Timeseries per channel in `tsc`, batch-uploading 1000 rows
    at a time. Then create a TimeseriesReference on the run's DataObject
    that bundles all 10 timeseries for that run."""
    if run_idx in HOLD_DAYS:
        _log("SKIP", f"tr-{run_idx:03d}-sensors", "TimeseriesReference (hold day)")
        return None
    ref_name = f"tr-{run_idx:03d}-sensors"
    # If a reference already exists for this run on this DO, reuse.
    # operationId: getAllTimeseriesReferences -> get_all_timeseries_references
    existing_refs = apis.timeseries_reference.get_all_timeseries_references(coll.id, run_do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "TimeseriesReference", r.id)
            return r
    timeseries_in_run: list[Timeseries] = []
    run_t0_ns: int | None = None
    run_tn_ns: int | None = None
    for chan, unit in CHANNELS:
        csv_path = data_dir / "timeseries" / f"tr-{run_idx:03d}-{chan}.csv"
        if not csv_path.exists():
            _log("SKIP", csv_path.name, "csv (missing)")
            continue
        points = _read_csv_points(csv_path)
        if not points:
            continue
        run_t0_ns = points[0].timestamp if run_t0_ns is None else min(run_t0_ns, points[0].timestamp)
        run_tn_ns = points[-1].timestamp if run_tn_ns is None else max(run_tn_ns, points[-1].timestamp)
        ts = Timeseries(
            measurement="hotfire",
            device=f"tr-{run_idx:03d}-bench-daq",
            location="P3-Lampoldshausen",
            symbolicName=chan,
            field=chan,
        )
        # Upload first chunk via create_timeseries; remaining chunks via
        # the bulk-append endpoint. The shepard-client method names follow
        # the OpenAPI generator convention, see clients/tests/python.
        chunks = list(_chunked(points, TIMESERIES_CHUNK))
        first = chunks[0]
        created = apis.timeseries_container.create_timeseries(
            timeseries_container_id=tsc.id,
            timeseries_with_data_points=TimeseriesWithDataPoints(
                timeseries=ts, points=first
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
                        field=chan,
                    ),
                    points=extra,
                ),
            )
        timeseries_in_run.append(created)
    tsr = TimeseriesReference(
        name=ref_name,
        start=run_t0_ns,
        end=run_tn_ns,
        timeseries=timeseries_in_run,
        timeseriesContainerId=tsc.id,
    )
    tsr = apis.timeseries_reference.create_timeseries_reference(coll.id, run_do.id, tsr)
    _log("OK", ref_name, "TimeseriesReference", tsr.id)
    return tsr


# ---- file + structured uploads --------------------------------------------


def upload_run_files(apis: Apis, coll: Collection, run_do: DataObject, fc: FileContainer, data_dir: Path, run_idx: int) -> None:
    """One CAD stub + one test report per run as a single FileReference."""
    ref_name = f"tr-{run_idx:03d}-files"
    # operationId: getAllFileReferences -> get_all_file_references
    existing_refs = apis.file_reference.get_all_file_references(coll.id, run_do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "FileReference", r.id)
            return
    files = [
        data_dir / "files" / f"tr-{run_idx:03d}-cad-stub.bin",
        data_dir / "files" / f"tr-{run_idx:03d}-test-report.md",
    ]
    oids: list[str] = []
    for path in files:
        if not path.exists():
            continue
        sf = apis.file_container.create_file(fc.id, str(path))
        oids.append(sf.oid)
    if not oids:
        return
    fr = FileReference(
        name=ref_name,
        dataObjectId=run_do.id,
        fileContainerId=fc.id,
        fileOids=oids,
    )
    fr = apis.file_reference.create_file_reference(coll.id, run_do.id, fr)
    _log("OK", ref_name, "FileReference", fr.id)


def upload_run_structured(apis: Apis, coll: Collection, run_do: DataObject, sc: StructuredDataContainer, data_dir: Path, run_idx: int) -> None:
    ref_name = f"tr-{run_idx:03d}-runlog"
    # operationId: getAllStructuredDataReferences -> get_all_structured_data_references
    existing_refs = apis.structured_reference.get_all_structured_data_references(coll.id, run_do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "StructuredDataReference", r.id)
            return
    runlog_path = data_dir / "structured" / f"tr-{run_idx:03d}-runlog.json"
    if not runlog_path.exists():
        return
    payload = StructuredDataPayload(
        structuredData=StructuredData(name=runlog_path.name),
        payload=runlog_path.read_text(encoding="utf-8"),
    )
    sd = apis.structured_container.create_structured_data(sc.id, payload)
    sr = StructuredDataReference(
        name=ref_name,
        dataObjectId=run_do.id,
        structuredDataContainerId=sc.id,
        structuredDataOids=[sd.oid],
    )
    apis.structured_reference.create_structured_data_reference(coll.id, run_do.id, sr)
    _log("OK", ref_name, "StructuredDataReference", sd.oid)


# ---- lab-journal entries ---------------------------------------------------


JOURNAL_TR4 = (
    "Vibration spike on fuel-turbopump observed during ramp_up at t=8s, "
    "sustained ~0.5s peaking 12 g rms. Engine completed steady_state "
    "nominally; suspect bearing precursor. Recommending teardown."
)
JOURNAL_INVESTIGATION = (
    "Thrust bearing inner race shows incipient spalling. Replaced. "
    "Re-balance verified. Cleared for re-test."
)
JOURNAL_TR6 = (
    "Fuel-pump vibration nominal across the full burn (peak 3.6 g rms in "
    "ramp_up, ≤2.4 g in steady_state). Bearing replacement confirmed effective."
)


def ensure_lab_journal(apis: Apis, target: DataObject, body: str, marker: str) -> None:
    """Idempotent: each journal entry on the target carries a `[showcase:<marker>]`
    sentinel so re-runs don't duplicate it."""
    sentinel = f"[showcase:{marker}]"
    # operationId: getLabJournalsByCollection (despite the name, it's filtered by dataObjectId).
    entries = apis.journal.get_lab_journals_by_collection(data_object_id=target.id) or []
    for e in entries:
        if sentinel in (e.journal_content or ""):
            _log("SKIP", f"{target.name}/{marker}", "LabJournalEntry", e.id)
            return
    entry = LabJournalEntry(dataObjectId=target.id, journalContent=f"{body}\n\n{sentinel}")
    e = apis.journal.create_lab_journal(target.id, entry)
    _log("OK", f"{target.name}/{marker}", "LabJournalEntry", e.id)


# ---- semantic annotations --------------------------------------------------


SEMANTIC_REPO_NAME = "shepard-showcase-local"


def ensure_semantic_repo(apis: Apis) -> SemanticRepository:
    """Semantic repository entry for the showcase annotations.

    Most value IRIs use the pre-seeded shepard-experiment ontology
    (https://shepard.dlr.de/ontologies/experiment) which ships with shepard.
    Engine-specific phases that have no exact concept use the DLR showcase
    namespace (https://shepard.dlr.de/showcase/lumen-inspired#).
    The endpoint is informational; shepard accepts opaque IRIs regardless."""
    # operationId: getAllSemanticRepositories
    repos = apis.semantic_repo.get_all_semantic_repositories() or []
    for r in repos:
        if r.name == SEMANTIC_REPO_NAME:
            _log("SKIP", SEMANTIC_REPO_NAME, "SemanticRepository", r.id)
            return r
    repo = SemanticRepository(
        name=SEMANTIC_REPO_NAME,
        type=SemanticRepositoryType.SPARQL,
        endpoint="https://shepard.dlr.de/ontologies/experiment",
    )
    repo = apis.semantic_repo.create_semantic_repository(repo)
    _log("OK", SEMANTIC_REPO_NAME, "SemanticRepository", repo.id)
    return repo


def annotate_phase_boundaries(
    apis: Apis,
    coll: Collection,
    run_do: DataObject,
    tsr: TimeseriesReference | None,
    repo: SemanticRepository,
    is_anomaly_run: bool,
) -> None:
    """Tag each TimeseriesReference with the seven phase-of-burn IRIs.
    For TR-004 also tag the `dlr:vibration-anomaly` IRI."""
    if tsr is None:
        return
    for phase, _, _ in PHASES:
        ann = SemanticAnnotation(
            propertyIRI=PROP_PHASE,
            propertyRepositoryId=repo.id,
            valueIRI=VAL_PHASE[phase],
            valueRepositoryId=repo.id,
        )
        try:
            apis.annotation.create_reference_annotation(
                collection_id=coll.id,
                data_object_id=run_do.id,
                reference_id=tsr.id,
                semantic_annotation=ann,
            )
        except Exception as exc:  # pragma: no cover — best-effort
            _log("SKIP", f"{run_do.name}/{phase}", "SemanticAnnotation", str(exc)[:60])
            continue
    if is_anomaly_run:
        anomaly = SemanticAnnotation(
            propertyIRI=PROP_QUALITY,
            propertyRepositoryId=repo.id,
            valueIRI=VAL_VIBRATION_ANOMALY,
            valueRepositoryId=repo.id,
        )
        try:
            apis.annotation.create_reference_annotation(
                collection_id=coll.id,
                data_object_id=run_do.id,
                reference_id=tsr.id,
                semantic_annotation=anomaly,
            )
            _log("OK", f"{run_do.name}/anomaly", "SemanticAnnotation")
        except Exception as exc:  # pragma: no cover
            _log("SKIP", f"{run_do.name}/anomaly", "SemanticAnnotation", str(exc)[:60])


# ---- publications DataObject (best-effort) ---------------------------------

LUMEN_PUBLICATIONS = [
    {
        "title": "Hot-Fire Testing and System Analysis of the LUMEN Liquid Upper Stage Demonstrator Engine",
        "authors": "Dresia, K. et al.",
        "year": 2025,
        "url": "https://elib.dlr.de/219029/",
        "venue": "Space Propulsion 2024, Glasgow",
    },
    {
        "title": "LUMEN: Versatile Test Bed for Rocket Engine Components: Hot-Fire Test Results",
        "authors": "Traudt, T. et al.",
        "year": 2024,
        "url": "https://elib.dlr.de/213229/",
        "venue": "Space Propulsion 2024",
    },
    {
        "title": "Virtual Sensing for Fault Detection within the LUMEN Fuel Turbopump Test Campaign",
        "authors": "Kurudzija, E. et al.",
        "year": 2024,
        "url": "https://elib.dlr.de/214022/",
        "venue": "Space Propulsion 2024",
    },
    {
        "title": "LUMEN: Putting into Operation a Flexible Test Bed",
        "authors": "Traudt, T. et al.",
        "year": 2025,
        "url": "https://elib.dlr.de/218874/",
        "venue": "AIAA SciTech 2025",
    },
    {
        "title": "LUMEN: Results of the Acceptance Tests",
        "authors": "Traudt, T. et al.",
        "year": 2024,
        "url": "https://elib.dlr.de/213230/",
        "venue": "Space Propulsion 2024",
    },
    {
        "title": "Combustion Stability Analysis of the LUMEN Demonstrator Engine",
        "authors": "Oschwald, M. et al.",
        "year": 2024,
        "url": "https://elib.dlr.de/213231/",
        "venue": "Space Propulsion 2024",
    },
    {
        "title": "Model-Based Closed-Loop Throttling of the LUMEN Demonstrator Engine",
        "authors": "Dresia, K. et al.",
        "year": 2024,
        "url": "https://elib.dlr.de/213228/",
        "venue": "Space Propulsion 2024",
    },
]


def best_effort_publications(apis: Apis, coll: Collection, sc: StructuredDataContainer) -> None:
    """Create a 'Publications' DataObject containing real DLR elib.dlr.de references.

    The publications point to actual LUMEN research papers — demonstrating how
    shepard can link a dataset to its associated literature. Uses a best-effort
    pattern: failures are logged but don't abort the seed."""
    name = "Publications"
    existing = _find_child_data_object(apis, coll.id, name, parent_id=None)
    if existing is not None:
        _log("SKIP", name, "DataObject", existing.id)
        pub_do = existing
    else:
        do = DataObject(
            name=name,
            description=(
                "Real DLR/LUMEN publications from elib.dlr.de. "
                "This DataObject demonstrates linking a dataset to its associated literature. "
                "**Publications are real; all measurement data in this showcase is synthetic.**"
            ),
            attributes={"kind": "literature", "source": "elib.dlr.de"},
        )
        pub_do = apis.data_object.create_data_object(coll.id, do)
        _log("OK", name, "DataObject", pub_do.id)

    # Structured data: one JSON record per publication.
    ref_name = "lumen-elib-publications"
    existing_refs = apis.structured_reference.get_all_structured_data_references(coll.id, pub_do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "StructuredDataReference", r.id)
            return
    try:
        payload_json = json.dumps({"publications": LUMEN_PUBLICATIONS}, indent=2, ensure_ascii=False)
        payload = StructuredDataPayload(
            structuredData=StructuredData(name="lumen-publications.json"),
            payload=payload_json,
        )
        sd = apis.structured_container.create_structured_data(sc.id, payload)
        sr = StructuredDataReference(
            name=ref_name,
            dataObjectId=pub_do.id,
            structuredDataContainerId=sc.id,
            structuredDataOids=[sd.oid],
        )
        apis.structured_reference.create_structured_data_reference(coll.id, pub_do.id, sr)
        _log("OK", ref_name, "StructuredDataReference (publications)", sd.oid)
    except Exception as exc:  # pragma: no cover
        _log("SKIP", ref_name, f"publications structured data: {str(exc)[:80]}")

    # Lab journal: markdown table linking to each paper with a clickable URL.
    marker = "publications-index"
    sentinel = f"[showcase:{marker}]"
    entries = apis.journal.get_lab_journals_by_collection(data_object_id=pub_do.id) or []
    for e in entries:
        if sentinel in (e.journal_content or ""):
            _log("SKIP", f"{name}/{marker}", "LabJournalEntry", e.id)
            return
    rows = "\n".join(
        f"| [{p['title']}]({p['url']}) | {p['authors']} | {p['year']} | {p['venue']} |"
        for p in LUMEN_PUBLICATIONS
    )
    journal_body = (
        "## LUMEN Publications on DLR eLIB\n\n"
        "These are **real** publications from the DLR electronic library "
        "(elib.dlr.de). The measurement data in this showcase is synthetic "
        "but inspired by the parameters described in these papers.\n\n"
        "| Title | Authors | Year | Venue |\n"
        "|---|---|---|---|\n"
        + rows
        + f"\n\n{sentinel}"
    )
    try:
        entry = LabJournalEntry(dataObjectId=pub_do.id, journalContent=journal_body)
        e = apis.journal.create_lab_journal(pub_do.id, entry)
        _log("OK", f"{name}/publications-index", "LabJournalEntry", e.id)
    except Exception as exc:  # pragma: no cover
        _log("SKIP", f"{name}/publications-index", f"LabJournalEntry: {str(exc)[:80]}")


# ---- turbine run template (best-effort; v2 endpoint) -----------------------

HOTFIRE_TEMPLATE_BODY = json.dumps({
    "templateKind": "DATAOBJECT_RECIPE",
    "name": "Rocket Engine Hot-Fire Test Run",
    "description": (
        "Standard data structure for a liquid-propellant rocket engine hot-fire test run. "
        "Creates a DataObject with mandatory test metadata, a timeseries reference slot for DAQ data, "
        "a file slot for the test report, and a structured-data slot for the run log."
    ),
    "attributes": [
        {"name": "test_id",        "type": "STRING",  "required": True,  "hint": "Unique test identifier, e.g. TR-001"},
        {"name": "campaign",       "type": "STRING",  "required": True,  "hint": "Campaign name, e.g. Q3-2024"},
        {"name": "test_engineer",  "type": "STRING",  "required": True,  "hint": "Lead test engineer (full name)"},
        {"name": "test_date",      "type": "STRING",  "required": True,  "hint": "ISO 8601 date, e.g. 2024-07-15"},
        {"name": "facility",       "type": "ENUM",    "required": True,  "allowed": ["P3-Lampoldshausen", "P4.1-Lampoldshausen", "P8-Lampoldshausen", "Other"]},
        {"name": "propellant_lox", "type": "BOOL",    "required": True,  "hint": "True if LOX was used as oxidiser"},
        {"name": "propellant_fuel","type": "ENUM",    "required": True,  "allowed": ["LH2", "LCH4", "Ethanol", "Other"]},
        {"name": "target_thrust_kn","type": "STRING", "required": False, "hint": "Nominal target thrust in kN"},
        {"name": "burn_duration_s", "type": "STRING", "required": False, "hint": "Planned burn duration in seconds"},
        {"name": "is_fired",       "type": "BOOL",    "required": True,  "hint": "False for hold / inspection days"},
        {"name": "outcome",        "type": "ENUM",    "required": False, "allowed": ["nominal", "anomaly", "abort", "hold"]},
        {"name": "notes_brief",    "type": "STRING",  "required": False, "hint": "One-sentence summary for the run list"},
    ],
    "fileSlots": [
        {
            "name": "test_report",
            "allowedMimeTypes": ["text/markdown", "application/pdf", "text/plain"],
            "required": True,
            "hint": "Post-test report (Markdown or PDF)",
        },
        {
            "name": "cad_snapshot",
            "allowedMimeTypes": ["application/octet-stream", "model/step", "model/iges"],
            "required": False,
            "hint": "CAD model snapshot at test configuration",
        },
    ],
    "references": [
        {
            "kind": "TimeseriesReference",
            "name": "daq_sensors",
            "hint": "Timeseries DAQ channels for this run (link to existing TimeseriesContainer)",
            "required": False,
        },
        {
            "kind": "StructuredDataReference",
            "name": "run_log",
            "hint": "Machine-readable run log JSON (link to StructuredDataContainer)",
            "required": False,
        },
    ],
})


def best_effort_ror_preseed(apis: Apis) -> None:
    """Preseed the demo instance's :InstanceRorConfig with DLR's ROR id.

    The About → Organization pane reads this back and fetches richer details
    from ror.org client-side. Idempotent: re-runs are no-ops once the rorId is
    set (the PATCH endpoint accepts the same value without complaint).
    Requires instance-admin; skips on 401/403/404 the same way other best-effort
    helpers do."""
    try:
        import urllib.error
        import urllib.request
    except Exception:  # pragma: no cover
        _log("SKIP", "ror preseed", "urllib unavailable")
        return

    host = apis.client.configuration.host.rstrip("/")
    v2_base = host
    if v2_base.endswith("/shepard/api"):
        v2_base = v2_base[: -len("/shepard/api")]
    elif v2_base.endswith("/shepard/api/"):
        v2_base = v2_base[: -len("/shepard/api/")]

    url = f"{v2_base}/v2/admin/instance/ror"
    # Backend's API-key filter requires the X-API-KEY header (case-sensitive)
    # for /v2/ admin endpoints — the upstream-client's lowercase `apikey`
    # configuration works for the /shepard/api/... surface (which the rest
    # of this seeder uses) but not here. Send both so the filter picks
    # whichever it's configured to accept.
    api_key = apis.client.configuration.api_key.get("apikey", "")
    headers = {
        "X-API-KEY": api_key,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    # DLR — Deutsches Zentrum für Luft- und Raumfahrt e.V.
    body = json.dumps({
        "rorId": "04bwf3e34",
        "organizationName": "Deutsches Zentrum für Luft- und Raumfahrt e.V. (DLR)",
    }).encode("utf-8")

    try:
        req = urllib.request.Request(url, data=body, headers=headers, method="PATCH")
        with urllib.request.urlopen(req, timeout=5) as resp:
            _log("OK", "ror preseed", "InstanceRorConfig", "04bwf3e34")
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            _log("SKIP", "ror preseed", f"InstanceRorConfig (not admin: HTTP {e.code})")
        elif e.code in (404, 501):
            _log("SKIP", "ror preseed", "InstanceRorConfig (endpoint not deployed)")
        else:
            _log("SKIP", "ror preseed", f"InstanceRorConfig HTTP {e.code}")
    except Exception as exc:  # pragma: no cover
        _log("SKIP", "ror preseed", f"InstanceRorConfig error: {str(exc)[:60]}")


def best_effort_user_orcid_preseed(apis: Apis) -> None:
    """Preseed demo users' shepard-side ORCID via the admin endpoint
    `PATCH /v2/admin/users/{username}/orcid`.

    The target user must already exist on the shepard side (i.e. they
    have logged in at least once so UserinfoService minted their User
    node). On a fresh deploy where the demo user hasn't logged in yet,
    this PATCH returns 404 — we log SKIP and the operator re-runs the
    seeder after the first login. The endpoint is idempotent.

    User pairs (username, orcid) live in DEMO_USER_ORCIDS below."""
    DEMO_USER_ORCIDS = [
        ("flo", "0000-0001-6033-801X"),
    ]
    try:
        import urllib.error
        import urllib.request
    except Exception:  # pragma: no cover
        _log("SKIP", "user orcid preseed", "urllib unavailable")
        return

    host = apis.client.configuration.host.rstrip("/")
    v2_base = host
    if v2_base.endswith("/shepard/api"):
        v2_base = v2_base[: -len("/shepard/api")]
    elif v2_base.endswith("/shepard/api/"):
        v2_base = v2_base[: -len("/shepard/api/")]

    api_key = apis.client.configuration.api_key.get("apikey", "")
    headers = {
        "X-API-KEY": api_key,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

    for username, orcid in DEMO_USER_ORCIDS:
        url = f"{v2_base}/v2/admin/users/{username}/orcid"
        body = json.dumps({"orcid": orcid}).encode("utf-8")
        try:
            req = urllib.request.Request(url, data=body, headers=headers, method="PATCH")
            with urllib.request.urlopen(req, timeout=5) as resp:
                _log("OK", username, "User.orcid", orcid)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                _log("SKIP", username, "User.orcid (user not yet logged in)")
            elif e.code in (401, 403):
                _log("SKIP", username, f"User.orcid (not admin: HTTP {e.code})")
            elif e.code in (404, 501):
                _log("SKIP", username, "User.orcid (endpoint not deployed)")
            else:
                _log("SKIP", username, f"User.orcid HTTP {e.code}")
        except Exception as exc:  # pragma: no cover
            _log("SKIP", username, f"User.orcid error: {str(exc)[:60]}")


def _data_object_app_id(do: DataObject, apis: "Apis | None" = None) -> str | None:
    """Pull the appId off a DataObject defensively — the upstream-generated
    client doesn't expose it on the typed model, but it's on the wire.
    Falls back to a v2 API lookup when apis is provided.
    Returns None if the response shape lacks it (very old backend builds)."""
    raw = getattr(do, "_appId", None) or getattr(do, "appId", None)
    if raw:
        return str(raw)
    # The upstream model stores unknown fields under additional_properties.
    extras = getattr(do, "additional_properties", None) or {}
    if isinstance(extras, dict):
        v = extras.get("appId")
        if v:
            return str(v)
    # Fall back to v2 endpoint — pydantic drops unknown fields so appId
    # never lands on the typed model even though the backend sends it.
    if apis is not None:
        return _v2_lookup_do_app_id(apis, do)
    return None


def _v2_lookup_do_app_id(apis: "Apis", do: DataObject) -> str | None:
    """Fetch appId for a DataObject by hitting the v1 single-object endpoint
    and reading the raw JSON (pydantic drops unknown fields; the raw JSON has
    appId). Falls back to the v2 collection list scan on any 4xx/5xx."""
    try:
        import json
        import urllib.request

        host = apis.client.configuration.host.rstrip("/")
        v1_base = host  # e.g. http://…/shepard/api
        api_key = apis.client.configuration.api_key.get("apikey", "")
        headers = {"X-API-KEY": api_key, "Accept": "application/json"}

        # Direct v1 DataObject endpoint — returns appId in the JSON even
        # though the upstream pydantic model drops it as an unknown field.
        url = f"{v1_base}/collections/{do.collection_id}/dataObjects/{do.id}"
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read())
        return data.get("appId") or None
    except Exception:
        return None


def best_effort_git_references(
    apis: Apis,
    targets: list[tuple[DataObject, str, str, str | None]],
) -> None:
    """Seed Git references showcasing the analysis scripts in this repo.

    Each tuple is (data_object, repo_url, path, ref). Idempotent — checks
    the existing list and skips duplicates. Requires the /v2/data-objects/
    {appId}/git-references endpoint (PL1d). Skips on 401/404 like other
    best-effort helpers.

    The "demo data" the user asked to link is this repository's own
    examples/lumen-showcase/notebooks/ folder, which contains the
    anomaly-analysis notebook + (when added) the SQL channel summary
    script. The repo URL points at noheton/shepard — replace with your
    fork's URL when forking this seed."""
    try:
        import urllib.error
        import urllib.request
    except Exception:  # pragma: no cover
        _log("SKIP", "git references", "urllib unavailable")
        return

    host = apis.client.configuration.host.rstrip("/")
    v2_base = host
    if v2_base.endswith("/shepard/api"):
        v2_base = v2_base[: -len("/shepard/api")]
    elif v2_base.endswith("/shepard/api/"):
        v2_base = v2_base[: -len("/shepard/api/")]

    apikey = apis.client.configuration.api_key.get("apikey", "")

    for (do, repo_url, path, ref) in targets:
        app_id = _data_object_app_id(do, apis)
        if not app_id:
            _log("SKIP", f"{do.name}/{path}", "GitReference (no appId on DO)")
            continue
        base = f"{v2_base}/v2/data-objects/{app_id}/git-references"

        # List existing to skip duplicates.
        existing: list[dict] = []
        try:
            req = urllib.request.Request(
                base,
                headers={"X-API-KEY": apikey, "Accept": "application/json"},
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=5) as resp:
                existing = json.loads(resp.read().decode("utf-8")) or []
        except urllib.error.HTTPError as e:
            if e.code in (404, 501):
                _log("SKIP", f"{do.name}/{path}", "GitReference (endpoint not deployed)")
                continue
            if e.code in (401, 403):
                _log("SKIP", f"{do.name}/{path}", f"GitReference (auth: HTTP {e.code})")
                continue
        except Exception as exc:  # pragma: no cover
            _log("SKIP", f"{do.name}/{path}", f"GitReference list error: {str(exc)[:60]}")
            continue

        if any(
            isinstance(g, dict)
            and g.get("repoUrl") == repo_url
            and g.get("path") == path
            and (g.get("ref") or None) == ref
            for g in existing
        ):
            _log("SKIP", f"{do.name}/{path}", "GitReference (already present)")
            continue

        # Create.
        body_obj: dict[str, str] = {"repoUrl": repo_url, "path": path}
        if ref:
            body_obj["ref"] = ref
        body = json.dumps(body_obj).encode("utf-8")
        try:
            req = urllib.request.Request(
                base,
                data=body,
                headers={
                    "X-API-KEY": apikey,
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                },
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=5) as resp:
                created = json.loads(resp.read().decode("utf-8"))
                _log("OK", f"{do.name}/{path}", "GitReference", created.get("appId", resp.status))
        except urllib.error.HTTPError as e:
            _log("SKIP", f"{do.name}/{path}", f"GitReference create HTTP {e.code}")
        except Exception as exc:  # pragma: no cover
            _log("SKIP", f"{do.name}/{path}", f"GitReference create error: {str(exc)[:60]}")


def best_effort_template(apis: Apis) -> None:
    """Seed a 'Rocket Engine Hot-Fire Test Run' ShepardTemplate via the v2 API.

    Requires instance-admin; skips gracefully on 401/403 or if the
    /v2/templates endpoint is not yet deployed."""
    try:
        import urllib.error
        import urllib.request
    except Exception:  # pragma: no cover
        _log("SKIP", "hotfire template", "ShepardTemplate (urllib unavailable)")
        return

    host = apis.client.configuration.host.rstrip("/")
    # v2 endpoint lives at the backend root, not the /shepard/api prefix.
    # Derive the v2 base: strip /shepard/api suffix if present.
    v2_base = host
    if v2_base.endswith("/shepard/api"):
        v2_base = v2_base[: -len("/shepard/api")]
    elif v2_base.endswith("/shepard/api/"):
        v2_base = v2_base[: -len("/shepard/api/")]

    list_url = f"{v2_base}/v2/templates?kind=DATAOBJECT_RECIPE"
    create_url = f"{v2_base}/v2/templates"
    headers = {
        "X-API-KEY": apis.client.configuration.api_key.get("apikey", ""),
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    template_name = "Rocket Engine Hot-Fire Test Run"

    # Check if already present.
    try:
        req = urllib.request.Request(list_url, headers=headers, method="GET")
        with urllib.request.urlopen(req, timeout=5) as resp:
            existing = json.loads(resp.read().decode("utf-8"))
            if isinstance(existing, list):
                for t in existing:
                    if isinstance(t, dict) and t.get("name") == template_name:
                        _log("SKIP", template_name, "ShepardTemplate", t.get("appId", ""))
                        return
            elif isinstance(existing, dict):
                for t in existing.get("content", existing.get("results", [])):
                    if isinstance(t, dict) and t.get("name") == template_name:
                        _log("SKIP", template_name, "ShepardTemplate", t.get("appId", ""))
                        return
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            _log("SKIP", template_name, f"ShepardTemplate (not admin: HTTP {e.code})")
            return
        if e.code in (404, 501):
            _log("SKIP", template_name, "ShepardTemplate (endpoint not deployed)")
            return
        _log("SKIP", template_name, f"ShepardTemplate list error HTTP {e.code}")
        return
    except Exception as exc:  # pragma: no cover
        _log("SKIP", template_name, f"ShepardTemplate list error: {str(exc)[:60]}")
        return

    # Create the template.
    body = json.dumps({
        "name": template_name,
        "kind": "DATAOBJECT_RECIPE",
        "body": HOTFIRE_TEMPLATE_BODY,
        "description": (
            "Standard data structure for a liquid-propellant rocket engine hot-fire test run. "
            "Attributes cover test ID, campaign, engineer, date, facility, propellant, "
            "thrust target, and outcome. File slots for test report and CAD snapshot. "
            "Timeseries and structured-data reference slots for DAQ and run-log binding."
        ),
        "tags": ["rocket-engine", "hot-fire", "liquid-propellant", "LUMEN", "test-campaign"],
    }).encode("utf-8")
    try:
        req = urllib.request.Request(create_url, data=body, headers=headers, method="POST")
        with urllib.request.urlopen(req, timeout=5) as resp:
            created = json.loads(resp.read().decode("utf-8"))
            _log("OK", template_name, "ShepardTemplate", created.get("appId", resp.status))
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            _log("SKIP", template_name, f"ShepardTemplate create (not admin: HTTP {e.code})")
        elif e.code in (409,):
            _log("SKIP", template_name, "ShepardTemplate (already exists)")
        else:
            _log("SKIP", template_name, f"ShepardTemplate create HTTP {e.code}")
    except Exception as exc:  # pragma: no cover
        _log("SKIP", template_name, f"ShepardTemplate create error: {str(exc)[:60]}")


# ---- collection versions (best-effort; feature-toggled) -------------------


def best_effort_versions(apis: Apis, coll: Collection) -> None:
    """Try to create v0 / v1 / v2 markers via the versioning endpoint.

    The endpoint is gated behind a Quarkus feature toggle and may not be
    exposed by the deployed backend; on 404/501/error we log SKIP. The
    notebook and showcase doc both treat versioning as optional surface."""
    host = apis.client.configuration.host.rstrip("/")
    url = f"{host}/collections/{coll.id}/versions"
    headers = {
        "X-API-KEY": apis.client.configuration.api_key.get("apikey", ""),
        "Content-Type": "application/json",
    }
    versions = [
        ("v0", "Campaign in progress (TR-001..TR-003 fired)."),
        ("v1", "Campaign complete; anomaly investigation open."),
        ("v2", "Post-anomaly addendum after TR-006 re-test."),
    ]
    try:
        import urllib.error
        import urllib.request
    except Exception:  # pragma: no cover
        _log("SKIP", "collection versions", "Version (urllib unavailable)")
        return
    for label, note in versions:
        body = json.dumps({"label": label, "description": note}).encode("utf-8")
        req = urllib.request.Request(url, data=body, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                _log("OK", label, "Version", resp.status)
        except urllib.error.HTTPError as e:
            if e.code in (404, 501):
                _log("SKIP", label, "Version (feature toggle off)")
                return  # giving up on the rest if endpoint isn't there
            _log("SKIP", label, f"Version HTTP {e.code}")
        except Exception as exc:  # pragma: no cover
            _log("SKIP", label, f"Version error: {str(exc)[:60]}")
            return


# ---- API keys --------------------------------------------------------------


def best_effort_api_keys(apis: Apis, owning_username: str) -> dict[str, str]:
    """Create campaign_lead_key (no expiry) and reviewer_key (validUntil
    intent recorded in seed log).

    The current `ApiKeyIO` schema does not yet carry a `validUntil` field
    (see backend/src/main/.../auth/apikey/io/ApiKeyIO.java). The seed
    therefore creates two keys with intent-bearing names and prints the
    intended `validUntil` so an operator can wire it through once the
    field ships. This is intentional — see L5 in the dispatcher backlog."""
    out: dict[str, str] = {}
    try:
        from shepard_client import ApiKeyApi  # type: ignore
    except Exception as exc:  # pragma: no cover
        _log("SKIP", "api keys", f"ApiKeyApi unavailable: {str(exc)[:60]}")
        return out
    api = ApiKeyApi(apis.client)
    try:
        existing = api.get_all_api_keys(owning_username) or []
    except Exception as exc:  # pragma: no cover
        _log("SKIP", "api keys", f"list failed: {str(exc)[:60]}")
        return out
    have = {k.name: k for k in existing}
    intents = [
        ("campaign_lead_key", None),
        (
            "reviewer_key (intended validUntil="
            + (datetime.now(timezone.utc) + timedelta(days=90)).isoformat()
            + ")",
            (datetime.now(timezone.utc) + timedelta(days=90)).isoformat(),
        ),
    ]
    for name, valid_until in intents:
        if name in have:
            _log("SKIP", name, "ApiKey", have[name].uid)
            continue
        try:
            from shepard_client import ApiKeyIO  # type: ignore  # generator artefact
            payload = ApiKeyIO(name=name)
            created = api.create_api_key(owning_username, payload)
            out[name] = str(created.uid)
            _log("OK", name, "ApiKey", created.uid)
        except Exception as exc:  # pragma: no cover
            _log("SKIP", name, f"ApiKey error: {str(exc)[:60]}")
    return out



def best_effort_git_credential_preseed(
    apis: Apis,
    targets: list[tuple[str, str, str, str | None]],
) -> None:
    """Preseed git credentials for demo users via the admin endpoint.

    Each tuple is (username, host, git_username, pat).
    The PAT is not hardcoded — pass via env var GITHUB_PAT or the --github-pat
    argument.  If a credential for the same host already exists it is replaced.

    Requires the /v2/admin/users/{username}/git-credentials endpoint and the
    shepard.secrets.encryption-key config to be set. Skips gracefully on
    404/503 (endpoint not deployed or key not configured).
    """
    try:
        import urllib.error
        import urllib.request
    except Exception:  # pragma: no cover
        _log("SKIP", "git credential preseed", "urllib unavailable")
        return

    host = apis.client.configuration.host.rstrip("/")
    v2_base = host
    if v2_base.endswith("/shepard/api"):
        v2_base = v2_base[: -len("/shepard/api")]
    elif v2_base.endswith("/shepard/api/"):
        v2_base = v2_base[: -len("/shepard/api/")]

    headers = {
        "X-API-KEY": apis.client.configuration.api_key.get("apikey", ""),
        "Content-Type": "application/json",
        "Accept": "application/json",
    }

    for username, git_host, git_username, pat in targets:
        if not pat:
            _log("SKIP", f"{username}/{git_host}", "GitCredential (no PAT provided)")
            continue
        label = f"{username}/{git_host}"
        url = f"{v2_base}/v2/admin/users/{username}/git-credentials"
        body = json.dumps({
            "host": git_host,
            "username": git_username,
            "pat": pat,
            "displayName": f"{git_host} — {git_username}",
        }).encode("utf-8")
        try:
            req = urllib.request.Request(url, data=body, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=10) as resp:
                result = json.loads(resp.read().decode("utf-8"))
                _log("OK", label, "GitCredential", result.get("appId", resp.status))
        except urllib.error.HTTPError as e:
            if e.code == 503:
                _log("SKIP", label, "GitCredential (encryption key not configured — set SECRETS_ENCRYPTION_KEY)")
            elif e.code == 404:
                _log("SKIP", label, "GitCredential (user not found — must log in first)")
            elif e.code in (401, 403):
                _log("SKIP", label, f"GitCredential (not admin: HTTP {e.code})")
            elif e.code == 501:
                _log("SKIP", label, "GitCredential (endpoint not deployed)")
            else:
                _log("SKIP", label, f"GitCredential HTTP {e.code}")
        except Exception as exc:  # pragma: no cover
            _log("SKIP", label, f"GitCredential error: {str(exc)[:80]}")


def best_effort_video_reference(
    apis: Apis,
    data_object: DataObject,
    youtube_url: str,
    ref_name: str,
) -> None:
    """Download a YouTube video and upload it as a VideoStreamReference.

    Demonstrates multi-modal data linking: the hot-fire video is attached
    to the same DataObject as timeseries sensor data and semantic phase
    annotations, so a viewer can correlate the visual phases (ignition
    transient, steady-state burn, throttle-down, shutdown) with the
    instrument readings.

    Requires yt-dlp and the /v2/data-objects/{appId}/video-stream-references
    endpoint. Skips gracefully when either is absent. The video is downloaded
    to a temp file and cleaned up after upload.
    """
    try:
        import urllib.error
        import urllib.parse
        import urllib.request
    except Exception:  # pragma: no cover
        _log("SKIP", ref_name, "VideoStreamReference (urllib unavailable)")
        return

    app_id = _data_object_app_id(data_object, apis)
    if not app_id:
        _log("SKIP", ref_name, "VideoStreamReference (no appId on DO)")
        return

    host = apis.client.configuration.host.rstrip("/")
    v2_base = host
    if v2_base.endswith("/shepard/api"):
        v2_base = v2_base[: -len("/shepard/api")]
    elif v2_base.endswith("/shepard/api/"):
        v2_base = v2_base[: -len("/shepard/api/")]

    list_url = f"{v2_base}/v2/data-objects/{app_id}/video-stream-references"
    api_key = apis.client.configuration.api_key.get("apikey", "")

    # Check if already uploaded.
    try:
        req = urllib.request.Request(
            list_url,
            headers={"X-API-KEY": api_key, "Accept": "application/json"},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            existing = json.loads(resp.read().decode("utf-8"))
            if isinstance(existing, list) and any(
                v.get("name") == ref_name for v in existing
            ):
                _log("SKIP", ref_name, "VideoStreamReference (already present)")
                return
    except urllib.error.HTTPError as e:
        if e.code in (404, 501):
            _log("SKIP", ref_name, "VideoStreamReference (endpoint not deployed)")
            return
        if e.code in (401, 403):
            _log("SKIP", ref_name, f"VideoStreamReference (auth: HTTP {e.code})")
            return
    except Exception as exc:  # pragma: no cover
        _log("SKIP", ref_name, f"VideoStreamReference list error: {str(exc)[:60]}")
        return

    # Download via yt-dlp (360p mp4 — small, self-contained).
    import tempfile
    import os
    import shutil
    tmp_path = None
    try:
        yt_dlp_bin = shutil.which("yt-dlp")
        if yt_dlp_bin is None:
            _log("SKIP", ref_name, "VideoStreamReference (yt-dlp not installed)")
            return
        tmp_fd, tmp_path = tempfile.mkstemp(suffix=".mp4")
        os.close(tmp_fd)
        import subprocess
        result = subprocess.run(
            [yt_dlp_bin, "-f", "18", "-o", tmp_path, "--no-playlist", "--force-overwrites", youtube_url],
            capture_output=True, text=True, timeout=120,
        )
        if result.returncode != 0:
            _log("SKIP", ref_name, f"VideoStreamReference (yt-dlp failed: {result.stderr[:80]})")
            return
        if not os.path.exists(tmp_path) or os.path.getsize(tmp_path) == 0:
            _log("SKIP", ref_name, "VideoStreamReference (empty download)")
            return
    except Exception as exc:  # pragma: no cover
        _log("SKIP", ref_name, f"VideoStreamReference (download error: {str(exc)[:60]})")
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)
        return

    # Upload as multipart.
    try:
        import mimetypes
        boundary = "shepardseedvid"
        with open(tmp_path, "rb") as fh:
            video_bytes = fh.read()
        body = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="lumen-hotfire.mp4"\r\n'
            f"Content-Type: video/mp4\r\n\r\n"
        ).encode("utf-8") + video_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")
        upload_url = f"{list_url}?name={urllib.parse.quote(ref_name)}"
        req = urllib.request.Request(
            upload_url,
            data=body,
            headers={
                "X-API-KEY": api_key,
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "Accept": "application/json",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            created = json.loads(resp.read().decode("utf-8"))
            _log("OK", ref_name, "VideoStreamReference", created.get("appId", resp.status))
    except urllib.error.HTTPError as e:
        if e.code == 503:
            _log("SKIP", ref_name, "VideoStreamReference (no file storage adapter configured)")
        else:
            _log("SKIP", ref_name, f"VideoStreamReference upload HTTP {e.code}")
    except Exception as exc:  # pragma: no cover
        _log("SKIP", ref_name, f"VideoStreamReference upload error: {str(exc)[:80]}")
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)


# ---------------------------------------------------------------------------
# Top-level entry point


def make_apis(host: str, apikey: str) -> Apis:
    conf = Configuration(host=host, api_key={"apikey": apikey})
    client = ApiClient(configuration=conf)
    return Apis(
        client=client,
        collection=CollectionApi(client),
        data_object=DataObjectApi(client),
        file_container=FileContainerApi(client),
        file_reference=FileReferenceApi(client),
        structured_container=StructuredDataContainerApi(client),
        structured_reference=StructuredDataReferenceApi(client),
        timeseries_container=TimeseriesContainerApi(client),
        timeseries_reference=TimeseriesReferenceApi(client),
        journal=LabJournalEntryApi(client),
        annotation=SemanticAnnotationApi(client),
        semantic_repo=SemanticRepositoryApi(client),
        search=SearchApi(client),
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--host", default=os.environ.get("BACKEND_URL"), help="shepard backend base URL (e.g. http://localhost:8080/shepard/api)")
    p.add_argument("--apikey", default=os.environ.get("API_KEY"), help="API key with admin/owner privileges")
    p.add_argument("--reset", action="store_true", help="delete the showcase Collection by name first")
    p.add_argument(
        "--data-dir",
        type=Path,
        default=Path(__file__).resolve().parent / "data",
        help="path to the generated synthetic data (default: %(default)s)",
    )
    p.add_argument(
        "--username",
        default=os.environ.get("SHEPARD_USERNAME", "admin"),
        help="username that owns the api keys created at the end of the seed",
    )
    p.add_argument(
        "--close-anomaly",
        action="store_true",
        help="set the anomaly-investigation closed_at attribute (use after running TR-006); the README documents this two-pass workflow",
    )
    p.add_argument(
        "--regenerate",
        action="store_true",
        help="re-run data/generate.py before seeding (useful after editing the generator)",
    )
    p.add_argument(
        "--github-pat",
        default=os.environ.get("GITHUB_PAT"),
        help="GitHub Personal Access Token for git credential preseed (flo's account). "
             "Also readable from the GITHUB_PAT env var. Skips git credential preseed when absent.",
    )
    args = p.parse_args(argv)
    if not args.host or not args.apikey:
        p.error("--host and --apikey are required (or set BACKEND_URL / API_KEY env vars)")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.regenerate:
        from data import generate  # type: ignore
        generate.main(["--out", str(args.data_dir)])
    if not (args.data_dir / "manifest.json").exists():
        print("error: data not generated. Run `python data/generate.py` first or pass --regenerate.", file=sys.stderr)
        return 2
    apis = make_apis(args.host, args.apikey)

    if args.reset:
        reset(apis)

    # Instance identity preseed — run FIRST, before anything that can crash.
    # The :InstanceRorConfig PATCH is independent of every other seed step
    # and we want the About → Organization pane populated even when later
    # steps blow up (e.g. an existing file container with stale perms).
    best_effort_ror_preseed(apis)
    # Per-user ORCID preseed — same defensive ordering, runs early so a
    # crash in the data-upload path doesn't keep flo's ORCID off the
    # avatar. Idempotent; SKIPs gracefully if the user hasn't logged in
    # yet (no :User node).
    best_effort_user_orcid_preseed(apis)

    coll = ensure_collection(apis)
    runs = ensure_run_data_objects(apis, coll)
    investigation = ensure_anomaly_investigation(apis, coll, runs, close_anomaly=args.close_anomaly)

    tsc = ensure_timeseries_container(apis, "lumen-inspired-sensors")
    try:
        fc = ensure_file_container(apis, "lumen-inspired-artifacts")
    except Exception as e:
        # A stale file container left over from an earlier seed run with a
        # different admin user can 403 the permissions probe inside
        # _ensure_public. Don't let it kill the whole seed — every other
        # step (lab journals, ROR, templates, git refs) is independent.
        print(f"warn: file container ensure failed ({str(e)[:120]}); skipping file uploads.", file=sys.stderr)
        fc = None
    sc = ensure_structured_container(apis, "lumen-inspired-runlogs")

    try:
        repo = ensure_semantic_repo(apis)
    except Exception as e:
        print(f"warn: semantic repository unavailable ({e}); phase-boundary annotations will be skipped.", file=sys.stderr)
        repo = None

    for n in range(1, N_RUNS + 1):
        run_do = runs[n]
        tsr = upload_run_timeseries(apis, coll, run_do, tsc, args.data_dir, n)
        if fc is not None:
            upload_run_files(apis, coll, run_do, fc, args.data_dir, n)
        upload_run_structured(apis, coll, run_do, sc, args.data_dir, n)
        if repo is not None:
            annotate_phase_boundaries(apis, coll, run_do, tsr, repo, is_anomaly_run=(n == ANOMALY_RUN))

    # Lab journals
    ensure_lab_journal(apis, runs[ANOMALY_RUN], JOURNAL_TR4, "tr4-debrief")
    ensure_lab_journal(apis, investigation, JOURNAL_INVESTIGATION, "tr4-finding")
    ensure_lab_journal(apis, runs[6], JOURNAL_TR6, "tr6-retest")

    # Publications DataObject + turbine template (best-effort).
    best_effort_publications(apis, coll, sc)
    best_effort_template(apis)

    # ROR preseed already ran at top of main() so it lands even if a
    # later step crashes — no need to repeat here.

    # Git references — link the LUMEN run data objects to the analysis
    # scripts in this repo's examples/lumen-showcase/notebooks/ folder, so
    # users see how external code consumes shepard data.
    GIT_DEMO_REPO = "https://github.com/noheton/shepard"
    GIT_DEMO_REF = "main"
    best_effort_git_references(apis, [
        (runs[ANOMALY_RUN], GIT_DEMO_REPO,
         "examples/lumen-showcase/notebooks/anomaly-analysis.ipynb", GIT_DEMO_REF),
        (investigation, GIT_DEMO_REPO,
         "examples/lumen-showcase/notebooks/anomaly-analysis.ipynb", GIT_DEMO_REF),
        # SQL summary showcase — uses /v2/sql/timeseries (P10) to compute
        # per-channel min/max/mean/stddev across the campaign.
        (runs[6], GIT_DEMO_REPO,
         "examples/lumen-showcase/notebooks/sql-channel-summary.py", GIT_DEMO_REF),
    ])

    # LUMEN hot-fire video — attached to the investigation DataObject so
    # viewers can correlate visual phases (ignition transient, steady-state
    # burn, throttle-down, shutdown) with the instrument timeseries and
    # semantic phase-boundary annotations that live on the same DO.
    # Source: "DLR LUMEN – The Liquid Upper Stage Demonstrator Engine" (official DLR channel, 2:55).
    # https://www.youtube.com/watch?v=WTrfouUTYBg
    best_effort_video_reference(
        apis,
        investigation,
        "https://www.youtube.com/watch?v=WTrfouUTYBg",
        "LUMEN – The Liquid Upper Stage Demonstrator Engine (DLR, 2024)",
    )

    # Git credential preseed — DB-only, no Keycloak dependency (same as ORCID).
    # PAT passed via --github-pat or GITHUB_PAT env var; skips when absent.
    # flo must have logged in at least once for the :User node to exist;
    # the preseed gracefully SKIPs with HTTP 404 if the node isn't there yet.
    best_effort_git_credential_preseed(apis, [
        ("flo", "github.com", "noheton", args.github_pat),
    ])

    # Versions + api keys (best-effort).
    best_effort_versions(apis, coll)
    best_effort_api_keys(apis, args.username)

    print(
        "\nseed complete.\n"
        f"  Collection id: {coll.id}\n"
        f"  Investigation DataObject id: {investigation.id}\n"
        f"  TR-004 id: {runs[ANOMALY_RUN].id}\n"
        f"  TR-006 id: {runs[6].id}\n"
        "Next: open `notebooks/anomaly-analysis.ipynb` (see README) "
        "or run with --close-anomaly after you publish the investigation finding.",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
