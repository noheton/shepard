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

# Project-local IRI placeholders. These are intentionally not resolvable —
# shepard accepts them as opaque IRIs, and a future ontology server can
# replace them with real PURLs. The phase IRIs follow a flat scheme
# `dlr:phase/<phase_name>` so they read well in the UI.
DLR_NS = "https://shepard.dlr.de/showcase/lumen-inspired#"
PROP_PHASE = DLR_NS + "phase"
PROP_ANOMALY = DLR_NS + "anomaly"
VAL_PHASE = {p[0]: f"{DLR_NS}phase/{p[0]}" for p in PHASES}
VAL_VIBRATION_ANOMALY = DLR_NS + "vibration-anomaly"

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
    as the operator's responsibility."""
    perms: Permissions = get_perms_fn(entity_id)
    if perms.permission_type != PermissionType.PUBLIC:
        perms.permission_type = PermissionType.PUBLIC
        set_perms_fn(entity_id, perms)


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
    """A fake SPARQL repo entry pointing at a project-local IRI namespace.

    The endpoint URL is intentionally unreachable; the IRIs are opaque
    placeholders. A future ontology server can replace them."""
    # operationId: getAllSemanticRepositories
    repos = apis.semantic_repo.get_all_semantic_repositories() or []
    for r in repos:
        if r.name == SEMANTIC_REPO_NAME:
            _log("SKIP", SEMANTIC_REPO_NAME, "SemanticRepository", r.id)
            return r
    repo = SemanticRepository(
        name=SEMANTIC_REPO_NAME,
        type=SemanticRepositoryType.SPARQL,
        endpoint="https://shepard.dlr.de/showcase/lumen-inspired",
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
            propertyIRI=PROP_ANOMALY,
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


# ---- collection versions (best-effort; feature-toggled) -------------------


def best_effort_versions(apis: Apis, coll: Collection) -> None:
    """Try to create v0 / v1 / v2 markers via the versioning endpoint.

    The endpoint is gated behind a Quarkus feature toggle and may not be
    exposed by the deployed backend; on 404/501/error we log SKIP. The
    notebook and showcase doc both treat versioning as optional surface."""
    host = apis.client.configuration.host.rstrip("/")
    url = f"{host}/collections/{coll.id}/versions"
    headers = {
        "apikey": apis.client.configuration.api_key.get("apikey", ""),
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

    coll = ensure_collection(apis)
    runs = ensure_run_data_objects(apis, coll)
    investigation = ensure_anomaly_investigation(apis, coll, runs, close_anomaly=args.close_anomaly)

    tsc = ensure_timeseries_container(apis, "lumen-inspired-sensors")
    fc = ensure_file_container(apis, "lumen-inspired-artifacts")
    sc = ensure_structured_container(apis, "lumen-inspired-runlogs")

    try:
        repo = ensure_semantic_repo(apis)
    except Exception as e:
        print(f"warn: semantic repository unavailable ({e}); phase-boundary annotations will be skipped.", file=sys.stderr)
        repo = None

    for n in range(1, N_RUNS + 1):
        run_do = runs[n]
        tsr = upload_run_timeseries(apis, coll, run_do, tsc, args.data_dir, n)
        upload_run_files(apis, coll, run_do, fc, args.data_dir, n)
        upload_run_structured(apis, coll, run_do, sc, args.data_dir, n)
        if repo is not None:
            annotate_phase_boundaries(apis, coll, run_do, tsr, repo, is_anomaly_run=(n == ANOMALY_RUN))

    # Lab journals
    ensure_lab_journal(apis, runs[ANOMALY_RUN], JOURNAL_TR4, "tr4-debrief")
    ensure_lab_journal(apis, investigation, JOURNAL_INVESTIGATION, "tr4-finding")
    ensure_lab_journal(apis, runs[6], JOURNAL_TR6, "tr6-retest")

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
