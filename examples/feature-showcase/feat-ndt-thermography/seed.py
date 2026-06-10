#!/usr/bin/env python3
"""feat-ndt-thermography — synthetic Edevis OTvis NDT scan → thermography surfaces.

The ``fileformat-thermography`` plugin turns an Edevis OTvis active-lock-in NDT
recording into:
  * ``urn:shepard:thermography:*`` acquisition annotations (frame rate, excitation
    device, recording type, campaign, resolution) parsed from the OTvis archive's
    ``content.xml`` manifest, and
  * ``urn:shepard:mffd:{section,module,layer,frame}`` grid annotations parsed from
    the ``S<n>_M<n>_L<n>_F<n>.OTvis`` filename (the MFFD S/M/L/F grid).
It also exposes a decode REST surface:
  * ``GET  /v2/thermography/otvis/{fileReferenceAppId}/frames`` (OTVIS-VIEWER), and
  * ``POST /v2/thermography/analyze`` (FileBundle of TIFFs → ``urn:shepard:ndt:*``).

What this seed does
-------------------
  1. Creates ``feat-ndt-thermography`` + an "NDT Scan S4_M13_L18_F4" DataObject.
  2. Uploads a small, obviously-synthetic ``S4_M13_L18_F4.OTvis`` file as a
     singleton FileReference — the filename carries the S/M/L/F grid.
  3. Probes the OTvis decode REST (``/v2/thermography/otvis/{appId}/frames``) and
     polls ``GET /v2/annotations`` for the thermography + grid predicates.

What's wired vs. what needs a real archive
------------------------------------------
The ``OTvisParser`` (a ``FileParserPlugin`` SPI implementation) **is** wired into
the ``POST /v2/files`` upload path for thermography: uploading
``S4_M13_L18_F4.OTvis`` emits the four ``urn:shepard:mffd:{section,module,layer,
frame}`` grid annotations from the filename automatically (verified by this seed
— a pleasant surprise, since the recovered ``.pyc`` predates the wiring and
documented this as a gap). The ``urn:shepard:thermography:*`` manifest annotations
(frame rate, excitation device, …) additionally require a *real* OTvis tar whose
``content.xml`` parses; our small synthetic stub is not a real tar, so the decode
REST (``/v2/thermography/otvis/{appId}/frames``) cleanly returns 422 and the
manifest annotations stay absent. Expected-vs-actual reported below.

Standards: active-thermography NDT (ISO 10878 / ASTM E2582 infrared flash-
thermography family), Edevis OTvis, DIN EN 9100 (inspection-record traceability).
NOT real DLR/MFFD data.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from _client import ShepardError, add_common_args, client_from_args  # noqa: E402

SLUG = "feat-ndt-thermography"
COLLECTION_NAME = SLUG
COLLECTION_DESC = (
    "Synthetic Edevis OTvis NDT scan (S4_M13_L18_F4). Proves the "
    "fileformat-thermography acquisition + S/M/L/F grid annotation set and the "
    "OTvis decode REST surface — surfaces the upload-time parser-wiring gap. "
    "NOT real DLR/MFFD data."
)

OTVIS_FILENAME = "S4_M13_L18_F4.OTvis"
# What the OTvisParser would emit (filename grid + content.xml manifest).
EXPECTED_THERMO = [
    "urn:shepard:thermography:frameRate_Hz",
    "urn:shepard:thermography:excitationDevice",
    "urn:shepard:thermography:recordingType",
    "urn:shepard:thermography:campaign",
    "urn:shepard:thermography:resolution",
]
EXPECTED_GRID = {
    "urn:shepard:mffd:section": "4",
    "urn:shepard:mffd:module": "13",
    "urn:shepard:mffd:layer": "18",
    "urn:shepard:mffd:frame": "4",
}

# Obviously-synthetic OTvis stub. A real OTvis is a tar holding a content.xml
# manifest; we ship a tiny manifest-shaped placeholder so the bytes are
# self-describing (and decode REST cleanly reports 422 "not a usable tar").
SYNTHETIC_OTVIS = (
    b"SYNTHETIC-OTVIS-PLACEHOLDER (not a real Edevis archive)\n"
    b"<OTvisRecording>\n"
    b"  <ModuleName>MFFD-UpperShell-Synthetic</ModuleName>\n"
    b"  <Campaign>feat-showcase-synthetic</Campaign>\n"
    b"  <RecordingType>lock-in</RecordingType>\n"
    b"  <FrameRate_Hz>50</FrameRate_Hz>\n"
    b"  <ExcitationDevice>halogen-flash-synthetic</ExcitationDevice>\n"
    b"  <Resolution>640x512</Resolution>\n"
    b"</OTvisRecording>\n"
)


def _ok(m): print(f"  OK   {m}")
def _skip(m): print(f"  SKIP {m}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    add_common_args(parser)
    args = parser.parse_args()
    c = client_from_args(args)

    print(f"== {SLUG} ==")
    plugins = c.plugin_states()
    if plugins.get("fileformat-thermography") != "ENABLED":
        _skip("fileformat-thermography plugin not ENABLED — RESEED-FIND; aborting.")
        return 0

    coll = c.ensure_collection(COLLECTION_NAME, COLLECTION_DESC, reset=args.reset)
    cid = coll["appId"]
    _ok(f"collection -> {cid}")

    do = c.create_data_object(
        cid, "NDT Scan S4_M13_L18_F4",
        description="Synthetic Edevis OTvis lock-in NDT scan at grid S4/M13/L18/F4.",
        attributes={"inspection-method": "THERMOGRAPHY", "grid": "S4_M13_L18_F4"},
    )
    do_id = do["appId"]
    _ok(f"NDT-scan DataObject -> {do_id}")

    fr = c.upload_file(do_id, OTVIS_FILENAME, SYNTHETIC_OTVIS,
                       content_type="application/octet-stream")
    fr_id = fr["appId"]
    _ok(f"OTvis FileReference -> {fr_id}")

    # Probe the OTvis decode REST (expected 422 on a non-tar synthetic stub).
    print("-- OTvis decode REST probe --")
    try:
        c.get(f"/v2/thermography/otvis/{fr_id}/frames")
        _ok("frames decoded (unexpected for a synthetic stub)")
    except ShepardError as e:
        if e.status == 422:
            _ok("/v2/thermography/otvis/{appId}/frames -> 422 (decode REST live; "
                "synthetic stub not a real tar — expected)")
        elif e.status == 404:
            _skip("decode REST 404 — endpoint not deployed (RESEED-FIND)")
        else:
            _skip(f"decode REST HTTP {e.status}")

    # Poll annotations for the parser predicates (expected absent — parser not wired).
    print("-- annotation check --")
    anns = c.get_annotations(do_id) + c.get_annotations(fr_id)
    present = {a.get("predicateIri") for a in anns}
    thermo_present = [p for p in EXPECTED_THERMO if p in present]
    grid_present = [p for p in EXPECTED_GRID if p in present]
    print(f"  thermography manifest annotations present: {thermo_present or 'NONE'}")
    print(f"  S/M/L/F grid annotations present: {grid_present or 'NONE'}")

    if grid_present:
        print("  -> OTvisParser filename-grid hook IS wired into POST /v2/files (good).")
    if not thermo_present:
        print("\nNOTE (expected — synthetic stub, not a RESEED-FIND):")
        print("  urn:shepard:thermography:* manifest annotations need a REAL OTvis tar")
        print("  whose content.xml parses. Our synthetic stub isn't a tar, so the decode")
        print("  REST returns 422 and the manifest annotations stay absent. On a real")
        print("  archive the parser would additionally emit:")
        print(f"    {EXPECTED_THERMO}")

    print("\nSUMMARY")
    print(f"  collection           {cid}")
    print(f"  NDT-scan DataObject  {do_id}")
    print(f"  OTvis FileReference  {fr_id}")
    print(f"  frontend: https://shepard.nuclide.systems/collections/{cid}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
