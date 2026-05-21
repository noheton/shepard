"""Idempotent seeder for the MFFD AFP manufacturing showcase.

Models the process chain of the MFFD upper fuselage demonstrator (ZLP Augsburg, DLR):

    AFP Layup Q1 ──► NDT Q1 [FAIL] ──► Rework Q1 ──► NDT Q1 Recheck [PASS] ──► Stringer Q1 ──┐
    AFP Layup Q2 ──────────────────────────────────► NDT Q2 [PASS]           ──► Stringer Q2 ──┤
                                                                                                 ├──► Frame Welding (Punkt) ──► Frame Welding (Brücke) ──► Stringerverbindung ──► LBR Cleats
                                                                                                 └───────────────────────────────────────────────────────────────────────────────────────────┘

The delamination anomaly on Q1 (45 cm² at rib station 7) mirrors the LUMEN TR-004
vibration anomaly — both demonstrate a rework loop that a folder structure cannot
capture but Shepard's Predecessor/Successor graph makes self-evident.

All measurement values are deterministic synthetic outputs of ``data/generate.py``.
**NOT real DLR MFFD data.**

Usage::

    python seed.py --host https://backend.example/shepard/api --apikey XXX
    python seed.py --host http://localhost:8080/shepard/api --apikey XXX --reset
    python seed.py --host … --apikey … --regenerate   # re-run data/generate.py first
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

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
# Collection identity

COLLECTION_NAME = "MFFD Upper Fuselage Demonstrator — AFP Manufacturing Campaign (synthetic)"
COLLECTION_DESCRIPTION = (
    "Synthetic showcase dataset for Shepard.  **NOT real DLR MFFD data.**  "
    "Inspired by the MFFD upper fuselage demonstrator manufactured at ZLP Augsburg "
    "(DLR) using CF/LMPAEK thermoplastic CFRP without autoclave.  "
    "The MFFD won the JEC World Innovation Award 2025 (Aerospace — Parts).  "
    "All numerical values are deterministic synthetic outputs of "
    "`data/generate.py` (numpy.random.default_rng(2024)).  "
    "Used to exercise Shepard's feature surface — provenance graph, "
    "quality-gate chains, multi-modal data, semantic annotations — "
    "in a manufacturing context."
)

# ---------------------------------------------------------------------------
# Semantic IRI namespaces

MFFD_NS  = "https://shepard.dlr.de/showcase/mffd-inspired#"
SHEX_NS  = "https://shepard.dlr.de/ontologies/experiment#"

PROP_PROCESS_STEP       = MFFD_NS + "ProcessStep"
PROP_QUARTER_PANEL      = MFFD_NS + "QuarterPanel"
PROP_INSPECTION_OUTCOME = MFFD_NS + "InspectionOutcome"
PROP_QUALITY            = SHEX_NS + "QualityFlag"

VAL_STEP = {
    "afp_q1":          MFFD_NS + "AFPLayup",
    "afp_q2":          MFFD_NS + "AFPLayup",
    "ndt_q1":          MFFD_NS + "NDTThermography",
    "ndt_q2":          MFFD_NS + "NDTThermography",
    "rework_q1":       MFFD_NS + "Rework",
    "ndt_q1_recheck":  MFFD_NS + "NDTThermography",
    "stringer_q1":     MFFD_NS + "StringerWelding",
    "stringer_q2":     MFFD_NS + "StringerWelding",
    "frame_punkt":     MFFD_NS + "SpotWelding",
    "frame_bruecke":   MFFD_NS + "BridgeWelding",
    "assembly":        MFFD_NS + "Stringerverbindung",
    "lbr_cleats":      MFFD_NS + "CleatInstallation",
}
VAL_PANEL = {
    "afp_q1":          MFFD_NS + "Q1",
    "afp_q2":          MFFD_NS + "Q2",
    "ndt_q1":          MFFD_NS + "Q1",
    "ndt_q2":          MFFD_NS + "Q2",
    "rework_q1":       MFFD_NS + "Q1",
    "ndt_q1_recheck":  MFFD_NS + "Q1",
    "stringer_q1":     MFFD_NS + "Q1",
    "stringer_q2":     MFFD_NS + "Q2",
    "frame_punkt":     MFFD_NS + "Full",
    "frame_bruecke":   MFFD_NS + "Full",
    "assembly":        MFFD_NS + "Full",
    "lbr_cleats":      MFFD_NS + "Full",
}
VAL_OUTCOME = {
    "ndt_q1":         MFFD_NS + "InspectionFail",
    "ndt_q2":         MFFD_NS + "InspectionPass",
    "ndt_q1_recheck": MFFD_NS + "InspectionPass",
}
VAL_QUALITY_SUSPECT = SHEX_NS + "QualitySuspect"

# ---------------------------------------------------------------------------
# DataObject definitions

STEP_DEFS: list[dict] = [
    {
        "key": "afp_q1",
        "name": "AFP Layup — Q1 Shell",
        "description": (
            "Automated Fiber Placement of Quarter Shell 1 (synthetic).  "
            "CF/LMPAEK 16 mm tape, 18 plies, stacking [0/90/±45]s.  "
            "**Anomaly at ply 5 (t = 280–320 s): consolidation force drop + TCP temp spike.**  "
            "Flagged for NDT re-inspection."
        ),
        "attributes": {
            "process_step": "AFP Layup",
            "quarter_panel": "Q1",
            "material_batch": "CF-LMPAEK-2024-07-A",
            "ply_count": "18",
            "stacking_sequence": "[0/90/+45/-45/0/90/+45/-45/0/90]s",
            "equipment": "AFP Robot 01 (Coriolis Composites A-PPT 16)",
            "location": "ZLP Augsburg",
            "layup_start": "2024-07-15T07:00:00Z",
        },
    },
    {
        "key": "afp_q2",
        "name": "AFP Layup — Q2 Shell",
        "description": (
            "Automated Fiber Placement of Quarter Shell 2 (synthetic).  "
            "Same recipe as Q1; different material lot.  All 18 plies within specification."
        ),
        "attributes": {
            "process_step": "AFP Layup",
            "quarter_panel": "Q2",
            "material_batch": "CF-LMPAEK-2024-07-B",
            "ply_count": "18",
            "stacking_sequence": "[0/90/+45/-45/0/90/+45/-45/0/90]s",
            "equipment": "AFP Robot 01 (Coriolis Composites A-PPT 16)",
            "location": "ZLP Augsburg",
            "layup_start": "2024-07-15T08:00:00Z",
        },
    },
    {
        "key": "ndt_q1",
        "name": "NDT Thermography — Q1 (FAIL)",
        "description": (
            "Active thermography inspection of Q1 shell (synthetic).  "
            "**Result: FAIL.**  Delamination DEF-001 found at rib station 7 "
            "(45.2 cm² > 20 cm² acceptance limit, depth 0.4 mm).  "
            "Root cause: AFP ply-5 consolidation force drop (see predecessor data)."
        ),
        "attributes": {
            "process_step": "NDT Thermography",
            "quarter_panel": "Q1",
            "inspection_date": "2024-07-16",
            "inspector": "C. Wagner",
            "result": "FAIL",
            "defect_id": "DEF-001",
            "defect_area_cm2": "45.2",
            "acceptance_limit_cm2": "20.0",
            "disposition": "rework required",
        },
    },
    {
        "key": "ndt_q2",
        "name": "NDT Thermography — Q2 (PASS)",
        "description": (
            "Active thermography inspection of Q2 shell (synthetic).  "
            "**Result: PASS.**  No defects above 20 cm² threshold.  "
            "Q2 cleared for stringer welding."
        ),
        "attributes": {
            "process_step": "NDT Thermography",
            "quarter_panel": "Q2",
            "inspection_date": "2024-07-16",
            "inspector": "C. Wagner",
            "result": "PASS",
        },
    },
    {
        "key": "rework_q1",
        "name": "Rework — Q1 Rib Station 7 (REWORK-Q1-001)",
        "description": (
            "Local repair of delamination DEF-001 (synthetic).  "
            "Abrasion + hand layup of 2 additional CF/LMPAEK plies; "
            "consolidation press at 6.0 bar / 160 °C / 45 min.  "
            "Released for re-inspection."
        ),
        "attributes": {
            "process_step": "Rework",
            "quarter_panel": "Q1",
            "rework_id": "REWORK-Q1-001",
            "rework_date": "2024-07-17",
            "operator": "A. Fischer",
            "defect_reference": "DEF-001",
            "rework_area_cm2": "45.0",
            "method": "abrasion + hand layup + press consolidation",
        },
    },
    {
        "key": "ndt_q1_recheck",
        "name": "NDT Thermography — Q1 Re-check (PASS)",
        "description": (
            "Re-inspection of Q1 after rework REWORK-Q1-001 (synthetic).  "
            "**Result: PASS.**  No defects above threshold at rib station 7 "
            "or adjacent areas.  Q1 cleared for stringer welding."
        ),
        "attributes": {
            "process_step": "NDT Thermography",
            "quarter_panel": "Q1",
            "inspection_date": "2024-07-18",
            "inspector": "C. Wagner",
            "result": "PASS",
            "rework_reference": "REWORK-Q1-001",
        },
    },
    {
        "key": "stringer_q1",
        "name": "Stringer Welding — Q1",
        "description": (
            "Continuous resistance welding of 8 stringers onto Q1 shell (synthetic).  "
            "All stringers within specification; peak zone temp 320–340 °C, "
            "pull tests ≥ 2 500 N."
        ),
        "attributes": {
            "process_step": "Stringer Welding",
            "quarter_panel": "Q1",
            "stringer_count": "8",
            "operator": "R. Hoffmann",
            "weld_date": "2024-07-19",
            "result": "PASS",
        },
    },
    {
        "key": "stringer_q2",
        "name": "Stringer Welding — Q2",
        "description": (
            "Continuous resistance welding of 8 stringers onto Q2 shell (synthetic).  "
            "All stringers within specification."
        ),
        "attributes": {
            "process_step": "Stringer Welding",
            "quarter_panel": "Q2",
            "stringer_count": "8",
            "operator": "K. Neumann",
            "weld_date": "2024-07-21",
            "result": "PASS",
        },
    },
    {
        "key": "frame_punkt",
        "name": "Frame Welding — Punktschweißen",
        "description": (
            "Spot welding (Punktschweißen) of frame clips to panel (synthetic).  "
            "Short high-current pulses (4 500 A / 0.5 s) at 120 positions."
        ),
        "attributes": {
            "process_step": "Frame Spot Welding",
            "quarter_panel": "Full",
            "weld_spots": "120",
            "weld_date": "2024-07-23",
            "result": "PASS",
        },
    },
    {
        "key": "frame_bruecke",
        "name": "Frame Welding — Brückenschweißen",
        "description": (
            "Bridge welding (Brückenschweißen) connecting frame strap clips (synthetic).  "
            "Completes the frame integration before panel assembly."
        ),
        "attributes": {
            "process_step": "Frame Bridge Welding",
            "quarter_panel": "Full",
            "weld_date": "2024-07-23",
            "result": "PASS",
        },
    },
    {
        "key": "assembly",
        "name": "Stringerverbindung — Q1 + Q2 Assembly",
        "description": (
            "Longitudinal assembly join (Stringerverbindung) of Q1 and Q2 shells "
            "into the full upper fuselage panel (synthetic).  "
            "Precision alignment: dx/dy < 0.1 mm before clamping.  "
            "Resistance welding of the longitudinal joint; 120 weld spots nominal."
        ),
        "attributes": {
            "process_step": "Stringerverbindung",
            "quarter_panel": "Full",
            "assembly_date": "2024-07-25",
            "alignment_tolerance_mm": "0.1",
            "result": "PASS",
        },
    },
    {
        "key": "lbr_cleats",
        "name": "Cleat Installation — LBR iiwa",
        "description": (
            "Installation of 42 titanium cleats by KUKA LBR iiwa 14 collaborative robot "
            "(synthetic).  Nominal insertion force −25 N ± 5 N; "
            "all force-torque readings within specification."
        ),
        "attributes": {
            "process_step": "Cleat Installation",
            "quarter_panel": "Full",
            "cleat_count": "42",
            "cleat_material": "Ti-6Al-4V",
            "robot": "KUKA LBR iiwa 14 R820",
            "install_date": "2024-07-26",
            "result": "PASS",
        },
    },
]

# Predecessor key-map: key → list of predecessor keys
PREDECESSOR_MAP: dict[str, list[str]] = {
    "ndt_q1":         ["afp_q1"],
    "ndt_q2":         ["afp_q2"],
    "rework_q1":      ["ndt_q1"],
    "ndt_q1_recheck": ["rework_q1"],
    "stringer_q1":    ["ndt_q1_recheck"],
    "stringer_q2":    ["ndt_q2"],
    "frame_punkt":    ["stringer_q1", "stringer_q2"],
    "frame_bruecke":  ["frame_punkt"],
    "assembly":       ["frame_bruecke"],
    "lbr_cleats":     ["assembly"],
}

# Timeseries definitions per step key
# (prefix matches data/timeseries/<prefix>-<channel>.csv)
TS_DEFS: dict[str, dict] = {
    "afp_q1": {
        "prefix": "afp-q1",
        "channels": ["tcp_temp_C", "consolidation_force_N", "layup_speed_mm_s",
                     "head_temp_C", "substrate_temp_C", "nip_pressure_bar"],
        "measurement": "mffd_afp_q1",
        "device": "afp-robot-01",
        "hz": 1,
    },
    "afp_q2": {
        "prefix": "afp-q2",
        "channels": ["tcp_temp_C", "consolidation_force_N", "layup_speed_mm_s",
                     "head_temp_C", "substrate_temp_C", "nip_pressure_bar"],
        "measurement": "mffd_afp_q2",
        "device": "afp-robot-01",
        "hz": 1,
    },
    "stringer_q1": {
        "prefix": "stringer-q1",
        "channels": ["weld_current_A", "weld_voltage_V", "weld_force_N",
                     "weld_zone_temp_C", "traverse_speed_mm_s"],
        "measurement": "mffd_stringer_q1",
        "device": "cweld-01",
        "hz": 10,
    },
    "stringer_q2": {
        "prefix": "stringer-q2",
        "channels": ["weld_current_A", "weld_voltage_V", "weld_force_N",
                     "weld_zone_temp_C", "traverse_speed_mm_s"],
        "measurement": "mffd_stringer_q2",
        "device": "cweld-01",
        "hz": 10,
    },
    "frame_punkt": {
        "prefix": "frame-punkt",
        "channels": ["spot_current_A", "spot_voltage_V", "spot_force_N",
                     "electrode_temp_C", "displacement_mm"],
        "measurement": "mffd_frame_punkt",
        "device": "sweld-01",
        "hz": 10,
    },
    "frame_bruecke": {
        "prefix": "frame-bruecke",
        "channels": ["spot_current_A", "spot_voltage_V", "spot_force_N",
                     "electrode_temp_C", "displacement_mm"],
        "measurement": "mffd_frame_bruecke",
        "device": "sweld-01",
        "hz": 10,
    },
    "assembly": {
        "prefix": "assembly",
        "channels": ["alignment_dx_mm", "alignment_dy_mm", "clamp_force_kN", "joint_temp_C"],
        "measurement": "mffd_assembly",
        "device": "alignment-sys-01",
        "hz": 1,
    },
    "lbr_cleats": {
        "prefix": "lbr",
        "channels": [
            "j1_deg", "j2_deg", "j3_deg", "j4_deg", "j5_deg", "j6_deg", "j7_deg",
            "force_x_N", "force_y_N", "force_z_N",
            "torque_x_Nm", "torque_y_Nm", "torque_z_Nm",
        ],
        "measurement": "mffd_lbr_cleats",
        "device": "lbr-iiwa-01",
        "hz": 10,
    },
}

# Structured data uploads: step key → list of (json_filename, ref_name)
STRUCTURED_DEFS: dict[str, list[tuple[str, str]]] = {
    "ndt_q1":         [("ndt-q1-report.json", "ndt-q1-report")],
    "ndt_q2":         [("ndt-q2-report.json", "ndt-q2-report")],
    "ndt_q1_recheck": [("ndt-q1-recheck.json", "ndt-q1-recheck")],
    "stringer_q1":    [("stringer-q1-quality.json", "stringer-q1-quality")],
    "stringer_q2":    [("stringer-q2-quality.json", "stringer-q2-quality")],
}

# File uploads: step key → list of (filename, ref_name)
FILE_DEFS: dict[str, list[tuple[str, str]]] = {
    "afp_q1":     [("afp-layup-recipe-q1.md", "afp-layup-recipe-q1")],
    "afp_q2":     [("afp-layup-recipe-q2.md", "afp-layup-recipe-q2")],
    "rework_q1":  [("rework-q1-protocol.md",  "rework-q1-protocol")],
    "stringer_q1": [("welding-protocol.md",    "welding-protocol-q1")],
    "stringer_q2": [("welding-protocol.md",    "welding-protocol-q2")],
    "lbr_cleats": [("lbr-cleat-spec.md",      "lbr-cleat-spec")],
}

# Lab journal entries: step key → (body, marker)
JOURNAL_DEFS: dict[str, tuple[str, str]] = {
    "afp_q1": (
        "Ply 5 anomaly flag: consolidation force drop from ~280 N to ~190 N "
        "at t = 280 s, with simultaneous TCP temperature spike (+18 °C).  "
        "Duration 40 s.  Process continued within safety interlock limits.  "
        "Flagged for NDT review after cooling.",
        "afp-q1-anomaly-flag",
    ),
    "ndt_q1": (
        "Active thermography inspection Q1: FAIL.  "
        "Delamination DEF-001 at rib station 7, area 45.2 cm², depth 0.4 mm.  "
        "Exceeds acceptance limit (20 cm²).  "
        "Root cause: AFP ply-5 consolidation drop + TCP excursion during reel change.  "
        "Rework order REWORK-Q1-001 raised.",
        "ndt-q1-fail",
    ),
    "rework_q1": (
        "Rework REWORK-Q1-001 completed.  "
        "Delamination area abraded (120-grit), cleaned with IPA.  "
        "Two additional CF/LMPAEK plies applied (0°/90°).  "
        "Consolidation press: 6.0 bar, 160 °C, 45 min.  "
        "Cool-down complete.  Released for re-inspection.",
        "rework-q1-complete",
    ),
    "ndt_q1_recheck": (
        "Re-inspection Q1 after rework: PASS.  "
        "No defects above 20 cm² threshold at rib station 7 or adjacent areas.  "
        "Q1 shell cleared for stringer welding.",
        "ndt-q1-recheck-pass",
    ),
    "stringer_q1": (
        "Stringer welding Q1 complete.  "
        "All 8 stringers within spec: peak zone temp 320–340 °C, "
        "avg force 510–525 N, pull tests 2 720–2 890 N (min 2 500 N).  "
        "Campaign proceeding to Q2.",
        "stringer-q1-complete",
    ),
    "assembly": (
        "Q1–Q2 Stringerverbindung complete.  "
        "Longitudinal alignment: dx = 0.08 mm, dy = 0.06 mm (tolerance 0.10 mm).  "
        "Clamp force 45 kN.  All 120 weld spots nominal.  "
        "Full panel released for LBR cleat installation.",
        "assembly-complete",
    ),
    "lbr_cleats": (
        "LBR iiwa cleat installation complete.  "
        "42 cleats installed; nominal insertion force −25 N ± 4 N (all within ±5 N limit).  "
        "Torque values within specification.  "
        "Full panel assembly complete.  Campaign closed.",
        "lbr-complete",
    ),
}

TIMESERIES_CHUNK = 1000

# ---------------------------------------------------------------------------
# API helper

LOCATION = "ZLP-Augsburg"


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
    suffix = f", {ident}" if ident != "" else ""
    print(f"{action:<6} {name} ({kind}{suffix})", flush=True)


def _query(name: str) -> str:
    return f'{{"property":"name","value":"{name}","operator":"eq"}}'


def _chunked(seq: list, size: int) -> Iterable[list]:
    for i in range(0, len(seq), size):
        yield seq[i : i + size]


def _v2_base(apis: Apis) -> str:
    host = apis.client.configuration.host.rstrip("/")
    if host.endswith("/shepard/api"):
        return host[: -len("/shepard/api")]
    return host


def _apikey(apis: Apis) -> str:
    return apis.client.configuration.api_key.get("apikey", "")


# ---------------------------------------------------------------------------
# Collection

def _find_collection(apis: Apis) -> Collection | None:
    body = CollectionSearchBody(
        searchParams=CollectionSearchParams(query=_query(COLLECTION_NAME))
    )
    res = apis.search.search_collections(body)
    for c in res.results or []:
        if c.name == COLLECTION_NAME:
            return c
    return None


def ensure_collection(apis: Apis) -> Collection:
    existing = _find_collection(apis)
    if existing is not None:
        _log("SKIP", COLLECTION_NAME, "Collection", existing.id)
        return existing
    coll = Collection(name=COLLECTION_NAME, description=COLLECTION_DESCRIPTION)
    coll = apis.collection.create_collection(coll)
    _log("OK", COLLECTION_NAME, "Collection", coll.id)
    return coll


def reset(apis: Apis) -> None:
    coll = _find_collection(apis)
    if coll is None:
        return
    apis.collection.delete_collection(coll.id)
    _log("DEL", COLLECTION_NAME, "Collection", coll.id)


# ---------------------------------------------------------------------------
# DataObjects

def _find_do(apis: Apis, coll: Collection, name: str) -> DataObject | None:
    objs = apis.data_object.get_all_data_objects(coll.id) or []
    for o in objs:
        if o.name == name:
            return o
    return None


def ensure_data_objects(apis: Apis, coll: Collection) -> dict[str, DataObject]:
    steps: dict[str, DataObject] = {}
    for defn in STEP_DEFS:
        key  = defn["key"]
        name = defn["name"]
        existing = _find_do(apis, coll, name)
        if existing is not None:
            steps[key] = existing
            _log("SKIP", name, "DataObject", existing.id)
            continue
        do = DataObject(
            name=name,
            description=defn["description"],
            attributes=defn["attributes"],
        )
        do = apis.data_object.create_data_object(coll.id, do)
        steps[key] = do
        _log("OK", name, "DataObject", do.id)
    return steps


def wire_predecessors(apis: Apis, coll: Collection, steps: dict[str, DataObject]) -> None:
    """Set predecessor links once the full step dict is populated."""
    for key, pred_keys in PREDECESSOR_MAP.items():
        do = steps[key]
        pred_ids = [steps[pk].id for pk in pred_keys]
        current = list(do.predecessor_ids or [])
        missing = [pid for pid in pred_ids if pid not in current]
        if not missing:
            _log("SKIP", do.name, "predecessor links (already set)")
            continue
        do.predecessor_ids = current + missing
        do = apis.data_object.update_data_object(coll.id, do.id, do)
        steps[key] = do
        pred_names = ", ".join(steps[pk].name for pk in pred_keys)
        _log("OK", do.name, f"predecessors → {pred_names}")


# ---------------------------------------------------------------------------
# Shared containers

def _find_container(apis: Apis, name: str, ctype: ContainerType):
    from shepard_client import ContainerSearchBody, ContainerSearchParams  # type: ignore
    body = ContainerSearchBody(
        searchParams=ContainerSearchParams(query=_query(name), queryType=ctype)
    )
    res = apis.search.search_containers(body)
    for c in res.results or []:
        if c.name == name:
            return c
    return None


def _ensure_public(api, get_fn, set_fn, entity_id: int) -> None:
    try:
        perms = get_fn(entity_id)
        if any(p.permission_type == PermissionType.READ for p in (perms or [])):
            return
    except Exception:
        pass
    try:
        set_fn(entity_id, [Permissions(permissionType=PermissionType.READ)])
    except Exception:
        pass


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
    tsc = apis.timeseries_container.create_timeseries_container(TimeseriesContainer(name=name))
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
    fc = apis.file_container.create_file_container(FileContainer(name=name))
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
    sc = apis.structured_container.create_structured_data_container(StructuredDataContainer(name=name))
    _ensure_public(
        apis.structured_container,
        apis.structured_container.get_structured_data_permissions,
        apis.structured_container.edit_structured_data_permissions,
        sc.id,
    )
    _log("OK", name, "StructuredDataContainer", sc.id)
    return sc


# ---------------------------------------------------------------------------
# Timeseries upload

def _read_csv(path: Path) -> list[TimeseriesDataPoint]:
    pts: list[TimeseriesDataPoint] = []
    with path.open() as f:
        r = csv.reader(f)
        next(r, None)
        for row in r:
            if row:
                pts.append(TimeseriesDataPoint(timestamp=int(row[0]), value=float(row[1])))
    return pts


def upload_step_timeseries(
    apis: Apis,
    coll: Collection,
    do: DataObject,
    tsc: TimeseriesContainer,
    data_dir: Path,
    key: str,
) -> TimeseriesReference | None:
    if key not in TS_DEFS:
        return None
    tdef = TS_DEFS[key]
    ref_name = f"mffd-{key.replace('_', '-')}-sensors"
    existing_refs = apis.timeseries_reference.get_all_timeseries_references(coll.id, do.id) or []
    for r in existing_refs:
        if r.name == ref_name:
            _log("SKIP", ref_name, "TimeseriesReference", r.id)
            return r

    ts_list: list[Timeseries] = []
    t0_ns: int | None = None
    tn_ns: int | None = None
    for chan in tdef["channels"]:
        csv_path = data_dir / "timeseries" / f"{tdef['prefix']}-{chan}.csv"
        if not csv_path.exists():
            _log("SKIP", csv_path.name, "csv (missing)")
            continue
        pts = _read_csv(csv_path)
        if not pts:
            continue
        t0_ns = pts[0].timestamp if t0_ns is None else min(t0_ns, pts[0].timestamp)
        tn_ns = pts[-1].timestamp if tn_ns is None else max(tn_ns, pts[-1].timestamp)
        ts = Timeseries(
            measurement=tdef["measurement"],
            device=tdef["device"],
            location=LOCATION,
            symbolicName=chan,
            field=chan,
        )
        chunks = list(_chunked(pts, TIMESERIES_CHUNK))
        created = apis.timeseries_container.create_timeseries(
            timeseries_container_id=tsc.id,
            timeseries_with_data_points=TimeseriesWithDataPoints(timeseries=ts, points=chunks[0]),
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
        ts_list.append(created)

    if not ts_list:
        return None
    tsr = TimeseriesReference(
        name=ref_name,
        start=t0_ns,
        end=tn_ns,
        timeseries=ts_list,
        timeseriesContainerId=tsc.id,
    )
    tsr = apis.timeseries_reference.create_timeseries_reference(coll.id, do.id, tsr)
    _log("OK", ref_name, "TimeseriesReference", tsr.id)
    return tsr


# ---------------------------------------------------------------------------
# Structured data upload

def upload_step_structured(
    apis: Apis,
    coll: Collection,
    do: DataObject,
    sc: StructuredDataContainer,
    data_dir: Path,
    key: str,
) -> None:
    for (fname, ref_name) in STRUCTURED_DEFS.get(key, []):
        existing = apis.structured_reference.get_all_structured_data_references(coll.id, do.id) or []
        if any(r.name == ref_name for r in existing):
            _log("SKIP", ref_name, "StructuredDataReference")
            continue
        path = data_dir / "structured" / fname
        if not path.exists():
            _log("SKIP", path.name, "json (missing)")
            continue
        sd = apis.structured_container.create_structured_data(
            sc.id,
            StructuredDataPayload(
                structuredData=StructuredData(name=fname),
                payload=path.read_text(encoding="utf-8"),
            ),
        )
        sr = StructuredDataReference(
            name=ref_name,
            dataObjectId=do.id,
            structuredDataContainerId=sc.id,
            structuredDataOids=[sd.oid],
        )
        apis.structured_reference.create_structured_data_reference(coll.id, do.id, sr)
        _log("OK", ref_name, "StructuredDataReference", sd.oid)


# ---------------------------------------------------------------------------
# File upload

def upload_step_files(
    apis: Apis,
    coll: Collection,
    do: DataObject,
    fc: FileContainer,
    data_dir: Path,
    key: str,
) -> None:
    for (fname, ref_name) in FILE_DEFS.get(key, []):
        existing = apis.file_reference.get_all_file_references(coll.id, do.id) or []
        if any(r.name == ref_name for r in existing):
            _log("SKIP", ref_name, "FileReference")
            continue
        path = data_dir / "files" / fname
        if not path.exists():
            _log("SKIP", path.name, "file (missing)")
            continue
        sf = apis.file_container.create_file(fc.id, str(path))
        fr = FileReference(
            name=ref_name,
            dataObjectId=do.id,
            fileContainerId=fc.id,
            fileOids=[sf.oid],
        )
        apis.file_reference.create_file_reference(coll.id, do.id, fr)
        _log("OK", ref_name, "FileReference", sf.oid)


# ---------------------------------------------------------------------------
# Lab journal

def ensure_journal(apis: Apis, do: DataObject, body: str, marker: str) -> None:
    sentinel = f"[showcase:{marker}]"
    entries = apis.journal.get_lab_journals_by_collection(data_object_id=do.id) or []
    for e in entries:
        if sentinel in (e.journal_content or ""):
            _log("SKIP", f"{do.name}/{marker}", "LabJournalEntry", e.id)
            return
    entry = LabJournalEntry(dataObjectId=do.id, journalContent=f"{body}\n\n{sentinel}")
    e = apis.journal.create_lab_journal(do.id, entry)
    _log("OK", f"{do.name}/{marker}", "LabJournalEntry", e.id)


# ---------------------------------------------------------------------------
# Semantic annotations

SEMANTIC_REPO_NAME_INTERNAL = "Built-in Semantic Store (n10s)"


def ensure_semantic_repo(apis: Apis) -> SemanticRepository | None:
    host = apis.client.configuration.host.rstrip("/")
    api_key = _apikey(apis)
    req = urllib.request.Request(
        f"{host}/semanticRepositories",
        headers={"X-API-KEY": api_key, "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
    except Exception as exc:
        print(f"warn: semantic repository unavailable ({exc}); annotations will be skipped.", file=sys.stderr)
        return None
    repos_raw: list[dict] = data if isinstance(data, list) else []
    for r in repos_raw:
        if r.get("type") == "INTERNAL" or r.get("name") == SEMANTIC_REPO_NAME_INTERNAL:
            repo = SemanticRepository.model_construct(
                id=r["id"],
                name=r.get("name", SEMANTIC_REPO_NAME_INTERNAL),
            )
            _log("OK", SEMANTIC_REPO_NAME_INTERNAL, "SemanticRepository", repo.id)
            return repo
    print("warn: INTERNAL semantic repository not found; skipping semantic annotations.", file=sys.stderr)
    return None


def annotate_step(
    apis: Apis,
    coll: Collection,
    do: DataObject,
    repo: SemanticRepository,
    key: str,
    is_afp_q1_anomaly: bool = False,
) -> None:
    annotations: list[tuple[str, str]] = [
        (PROP_PROCESS_STEP, VAL_STEP.get(key, MFFD_NS + "Unknown")),
        (PROP_QUARTER_PANEL, VAL_PANEL.get(key, MFFD_NS + "Unknown")),
    ]
    if key in VAL_OUTCOME:
        annotations.append((PROP_INSPECTION_OUTCOME, VAL_OUTCOME[key]))
    if is_afp_q1_anomaly:
        annotations.append((PROP_QUALITY, VAL_QUALITY_SUSPECT))

    for prop_iri, val_iri in annotations:
        ann = SemanticAnnotation(
            propertyIRI=prop_iri,
            propertyRepositoryId=repo.id,
            valueIRI=val_iri,
            valueRepositoryId=repo.id,
        )
        try:
            apis.annotation.create_data_object_annotation(
                collection_id=coll.id,
                data_object_id=do.id,
                semantic_annotation=ann,
            )
        except Exception as exc:
            _log("SKIP", f"{do.name}/{prop_iri.split('#')[-1]}", "SemanticAnnotation", str(exc)[:60])


# ---------------------------------------------------------------------------
# Best-effort v2 helpers

def best_effort_ror_preseed(apis: Apis) -> None:
    url = f"{_v2_base(apis)}/v2/admin/instance/ror"
    body = json.dumps({"rorId": "04bwf3e34",
                       "organizationName": "Deutsches Zentrum für Luft- und Raumfahrt e.V. (DLR)"}).encode()
    headers = {"X-API-KEY": _apikey(apis), "Content-Type": "application/json"}
    try:
        req = urllib.request.Request(url, data=body, headers=headers, method="PATCH")
        with urllib.request.urlopen(req, timeout=5):
            _log("OK", "ror preseed", "InstanceRorConfig", "04bwf3e34")
    except urllib.error.HTTPError as e:
        _log("SKIP", "ror preseed", f"InstanceRorConfig HTTP {e.code}")
    except Exception as exc:
        _log("SKIP", "ror preseed", f"InstanceRorConfig error: {str(exc)[:60]}")


def best_effort_activity_tags(apis: Apis, coll: Collection, n_steps: int) -> None:
    """Touch the collection via v2 PATCH once per process step to generate Activity records."""
    coll_app_id = _get_collection_app_id(apis, coll)
    if not coll_app_id:
        _log("SKIP", coll.name, "activity tags (no appId)")
        return
    url = f"{_v2_base(apis)}/v2/collections/{coll_app_id}"
    headers = {
        "X-API-KEY": _apikey(apis),
        "Content-Type": "application/merge-patch+json",
    }
    body = json.dumps({"description": COLLECTION_DESCRIPTION}).encode()
    ok = 0
    for _ in range(n_steps):
        try:
            req = urllib.request.Request(url, data=body, headers=headers, method="PATCH")
            with urllib.request.urlopen(req, timeout=10):
                ok += 1
        except Exception:
            pass
    if ok:
        _log("OK", coll.name, f"activity tags ({ok}/{n_steps})", coll_app_id)


def _get_collection_app_id(apis: Apis, coll: Collection) -> str | None:
    host = apis.client.configuration.host.rstrip("/")
    headers = {"X-API-KEY": _apikey(apis), "Accept": "application/json"}
    try:
        req = urllib.request.Request(f"{host}/collections/{coll.id}", headers=headers)
        with urllib.request.urlopen(req, timeout=5) as resp:
            return json.loads(resp.read()).get("appId")
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Entry point

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
    p.add_argument("--host",     default=os.environ.get("BACKEND_URL"),
                   help="Shepard backend base URL (e.g. http://localhost:8080/shepard/api)")
    p.add_argument("--apikey",   default=os.environ.get("API_KEY"),
                   help="API key with admin/owner privileges")
    p.add_argument("--reset",    action="store_true",
                   help="delete the MFFD Collection first (full re-seed)")
    p.add_argument("--data-dir", type=Path,
                   default=Path(__file__).resolve().parent / "data",
                   help="path to generated synthetic data (default: %(default)s)")
    p.add_argument("--regenerate", action="store_true",
                   help="re-run data/generate.py before seeding")
    args = p.parse_args(argv)
    if not args.host or not args.apikey:
        p.error("--host and --apikey are required (or set BACKEND_URL / API_KEY)")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    if args.regenerate:
        from data import generate  # type: ignore
        generate.main(["--out", str(args.data_dir)])

    if not (args.data_dir / "manifest.json").exists():
        print(
            "error: data not generated. Run `python data/generate.py` first or pass --regenerate.",
            file=sys.stderr,
        )
        return 2

    apis = make_apis(args.host, args.apikey)

    if args.reset:
        reset(apis)

    best_effort_ror_preseed(apis)

    coll  = ensure_collection(apis)
    steps = ensure_data_objects(apis, coll)
    wire_predecessors(apis, coll, steps)

    tsc = ensure_timeseries_container(apis, "mffd-process-telemetry")
    try:
        fc = ensure_file_container(apis, "mffd-process-documents")
    except Exception as e:
        print(f"warn: file container unavailable ({str(e)[:80]}); skipping file uploads.", file=sys.stderr)
        fc = None
    sc = ensure_structured_container(apis, "mffd-quality-records")

    try:
        repo = ensure_semantic_repo(apis)
    except Exception as e:
        print(f"warn: semantic repo unavailable ({e}); annotations skipped.", file=sys.stderr)
        repo = None

    for defn in STEP_DEFS:
        key = defn["key"]
        do  = steps[key]
        upload_step_timeseries(apis, coll, do, tsc, args.data_dir, key)
        upload_step_structured(apis, coll, do, sc, args.data_dir, key)
        if fc is not None:
            upload_step_files(apis, coll, do, fc, args.data_dir, key)
        if key in JOURNAL_DEFS:
            body, marker = JOURNAL_DEFS[key]
            ensure_journal(apis, do, body, marker)
        if repo is not None:
            annotate_step(
                apis, coll, do, repo, key,
                is_afp_q1_anomaly=(key == "afp_q1"),
            )

    best_effort_activity_tags(apis, coll, len(STEP_DEFS))

    print("\nMFFD showcase seeding complete.", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
