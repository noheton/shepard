#!/usr/bin/env python3
"""feat-welding-svdx — synthetic welding timeseries + companion process video.

An MFFD stringer-welding run carries (a) a welding ``TimeseriesReference`` with
the TwinCAT-Scope channels a ``.svdx`` recording would produce (converter power,
weld force, nip-point temperature) and (b) a companion GoPro process
``VideoReference``. Plugins in scope: ``fileformat-svdx`` (welding timeseries)
and ``video`` (companion clip).

Why the TimeseriesReference path, not the ``.svdx`` binary
----------------------------------------------------------
The shipped, wired svdx surface is ``POST /v2/svdx/ingest`` — it ingests the
operator-generated TwinCAT-Scope-Export-Tool ``.csv`` *sibling* of a real
``.svdx`` (16-byte binary envelope + trailing ``<ScopeProject>`` XML), mapping
5-tuple channel identities (measurement=projectName, device=NetID,
location=Port, symbolicName=SymbolName, field=display name) into TimescaleDB.
The CSV format is tab-delimited with FILETIME timestamps and repeating per-
channel key/value header blocks (see ``TcScopeCsvParser``) — faking a byte-
valid synthetic pair is fragile. Per the showcase spec ("svdx plugin path if a
synthetic .svdx is feasible, else a TimeseriesReference with welding channels +
companion VideoReference"), this seed takes the TimeseriesReference fallback:
real, queryable welding channels without faking a binary TwinCAT recording.
The ``/v2/svdx/ingest`` path is exercised against real ZLP ``.svdx``/``.csv``
pairs by the MFFD-STRINGER-SVDX-INGEST-1 import job, not here.

Standards: Beckhoff TwinCAT Scope ``.svdx`` (the recording format this stands
in for), CHAMEO/EMMO (ultrasonic-welding process terms), DIN EN 9100
(per-run sensor trace traceability). NOT real DLR/MFFD data.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from _client import ShepardError, add_common_args, client_from_args  # noqa: E402

SLUG = "feat-welding-svdx"
COLLECTION_NAME = SLUG
COLLECTION_DESC = (
    "Synthetic MFFD stringer-welding run: welding TimeseriesReference (converter "
    "power, weld force, nip-point temp) + companion GoPro process VideoReference. "
    "Exercises fileformat-svdx (welding channels) + video. NOT real DLR/MFFD data."
)

# 5-tuple welding channels, mirroring measurement=project, device=NetID,
# location=Port, symbolicName=SymbolName, field=display name. The 5-tuple fields
# reject Space/Comma/Point/Slash, so the TwinCAT NetID dots are rendered with
# underscores (5_62_1_10_3_1) and SymbolName dots likewise.
CHANNELS = [
    {"measurement": "mffd_stringer_weld", "device": "netid_5_62_1_10_3_1",
     "location": "port_851", "symbolicName": "Weld_rConverterPower", "field": "converter_power_W"},
    {"measurement": "mffd_stringer_weld", "device": "netid_5_62_1_10_3_1",
     "location": "port_851", "symbolicName": "Weld_rWeldForce", "field": "weld_force_N"},
    {"measurement": "mffd_stringer_weld", "device": "netid_5_62_1_10_3_1",
     "location": "port_851", "symbolicName": "Weld_rNipPointTemp", "field": "nip_point_temp_C"},
]


def _ok(m): print(f"  OK   {m}")
def _skip(m): print(f"  SKIP {m}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    add_common_args(parser)
    args = parser.parse_args()
    c = client_from_args(args)

    print(f"== {SLUG} ==")
    plugins = c.plugin_states()
    if plugins.get("fileformat-svdx") != "ENABLED":
        _skip("fileformat-svdx plugin not ENABLED — RESEED-FIND; running TS path anyway.")

    coll = c.ensure_collection(COLLECTION_NAME, COLLECTION_DESC, reset=args.reset)
    cid = coll["appId"]
    _ok(f"collection -> {cid}")

    do = c.create_data_object(
        cid, "Stringer Weld Run (ultrasonic/continuous)",
        description="Synthetic ultrasonic/continuous stringer weld run + sensors + video.",
        attributes={
            "process-type": "Ultrasonic Welding", "weld-subtype": "ultrasonic",
            "weld-mode": "continuous", "stringer-id": "STR-Q1-04",
        },
    )
    do_id = do["appId"]
    _ok(f"weld-run DataObject -> {do_id}")

    # Welding TimeseriesReference (the .svdx stand-in).
    tsc = c.create_timeseries_container("feat-welding-ts-container")
    _ok(f"TimeseriesContainer -> {tsc['appId']}")
    start = 1768900000000  # synthetic epoch millis
    end = start + 30_000
    ts_ref = c.create_timeseries_reference(
        do_id, "svdx-welding-trace", tsc, CHANNELS, start, end,
    )
    _ok(f"welding TimeseriesReference created (3 channels) -> {ts_ref['appId']}")

    # Annotate the welding subtype/mode (MFFD-STRINGER-WELDSUBTYPE-1).
    c.annotate(do_id, "DataObject", "urn:shepard:mffd:weld-subtype", literal="ultrasonic")
    c.annotate(do_id, "DataObject", "urn:shepard:mffd:weld-mode", literal="continuous")
    _ok("weld-subtype / weld-mode annotations written")

    # Companion process video (best-effort: the video plugin REST may be inactive).
    video_ref = _upload_video(c, do_id)

    # ---- verify ------------------------------------------------------------
    print("-- verify --")
    refs = c.get(f"/v2/collections/{cid}/data-objects/{do_id}")
    print(f"  timeseriesReferenceCount: {refs.get('timeseriesReferenceCount')}")
    anns = c.get_annotations(do_id)
    print(f"  weld annotations: {[a.get('predicateIri') for a in anns if 'weld' in (a.get('predicateIri') or '')]}")

    print("\nSUMMARY")
    print(f"  collection            {cid}")
    print(f"  weld-run DataObject   {do_id}")
    print(f"  TimeseriesReference   {ts_ref['appId']}")
    print(f"  VideoReference        {video_ref or '(skipped — video plugin REST inactive)'}")
    print(f"  frontend: https://shepard.nuclide.systems/collections/{cid}")
    return 0


def _upload_video(c, do_id: str):
    """Upload a tiny synthetic .mp4 as a VideoReference via the multipart
    ``POST /v2/data-objects/{appId}/video-stream-references`` path (the ``video``
    plugin). The upload + download work; only the sister
    ``GET /v2/admin/video/config`` admin endpoint 500s because the ``VideoConfig``
    entity isn't OGM-registered (RESEED-FIND — doesn't affect this seed). Skip
    gracefully if the upload itself ever 404s."""
    # Obviously-synthetic mp4 stub (valid ftyp box header + marker bytes).
    clip = b"\x00\x00\x00\x18ftypmp42\x00\x00\x00\x00mp42isomSYNTHETIC-STRINGER-WELD-GOPRO-CLIP"
    try:
        resp = c.upload_multipart(
            f"/v2/data-objects/{do_id}/video-stream-references",
            query={"name": "stringer-weld-process.mp4"},
            field="file", filename="stringer-weld-process.mp4",
            content=clip, content_type="video/mp4",
        )
        ref = resp.get("appId") if isinstance(resp, dict) else None
        _ok(f"companion VideoReference -> {ref}")
        return ref
    except ShepardError as e:
        _skip(f"video upload HTTP {e.status} — video plugin REST inactive (RESEED-FIND)")
        return None


if __name__ == "__main__":
    sys.exit(main())
