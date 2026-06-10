"""feat-krl-transform — B5: KRL .src + URDF → derived joint-trajectory TimeseriesReference.

Feature it proves
-----------------
The ``krl-interpreter`` plugin dissolves the bespoke KRL interpret subsystem into the
generic MAPPING_RECIPE mechanism (aidocs/platform/191 #2; aidocs/integrations/117).
Its ``KrlTrajectoryTransformExecutor`` (``TransformExecutor`` SPI) claims the
``http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape`` IRI: a MAPPING_RECIPE
binds a KRL ``.src`` FileReference + a URDF FileReference, and materializing it
(``POST /v2/mappings/{templateAppId}/materialize``) resolves the bytes, calls the KRL
interpreter sidecar (forward-kinematics over the URDF), persists the resulting joint
trajectory as a NEW TimeseriesReference, and returns a REFERENCE result carrying that
derived reference's appId.

What this seed does
-------------------
  1. Creates the ``feat-krl-transform`` Collection + a "KR3 Pick Program" DataObject.
  2. Uploads a *synthetic* tiny KRL ``.src`` + a synthetic URDF as singleton
     FileReferences (FR1b — one file each, POST /v2/files).
  3. Creates a TimeseriesContainer for the derived trajectory.
  4. Mints a MAPPING_RECIPE template declaring the KrlTrajectoryShape IRI + the
     src/URDF bindings + targetDataObjectAppId + timeseriesContainerAppId.
  5. POSTs ``/v2/mappings/{templateAppId}/materialize`` and asserts a derived
     TimeseriesReference appId came back. If the KRL sidecar is down / the plugin
     is disabled, the seed notes the gap and skips gracefully.

Synthetic data only — no real DLR/MFFD IP.

Usage
-----
    python seed.py --apikey "$API_KEY"
    python seed.py --apikey "$API_KEY" --reset
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from _client import HttpError, V2Client, build_arg_parser, client_from_args, log  # noqa: E402

SLUG = "feat-krl-transform"
COLLECTION_DESC = (
    "KRL transform showcase: a synthetic KUKA KRL .src + URDF materialized into a "
    "derived joint-trajectory TimeseriesReference via the krl-interpreter "
    "MAPPING_RECIPE transform (POST /v2/mappings/{appId}/materialize). "
    "Synthetic data only — NOT real DLR data."
)

KRL_TRAJECTORY_SHAPE_IRI = "http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape"

# Minimal synthetic KUKA KRL .src — a couple of PTP/LIN motions over 3 axes.
SYNTHETIC_KRL_SRC = """&ACCESS RVP
DEF synthetic_pick( )
  ; --- synthetic KRL demo program (NOT a real DLR robot program) ---
  DECL AXIS HOME
  HOME = {A1 0, A2 -90, A3 90, A4 0, A5 0, A6 0}

  PTP HOME
  PTP {A1 30, A2 -60, A3 75, A4 0, A5 15, A6 0}
  LIN {X 600, Y 0, Z 400, A 0, B 90, C 0}
  PTP {A1 -30, A2 -60, A3 75, A4 0, A5 15, A6 0}
  PTP HOME
END
"""

# Synthetic 3-joint URDF the interpreter resolves forward kinematics against.
SYNTHETIC_URDF = """<?xml version="1.0"?>
<robot name="synthetic_kr3">
  <link name="base_link"/>
  <link name="link_1"/>
  <link name="link_2"/>
  <link name="flange"/>
  <joint name="a1" type="revolute">
    <parent link="base_link"/><child link="link_1"/>
    <origin xyz="0 0 0.35" rpy="0 0 0"/><axis xyz="0 0 1"/>
    <limit lower="-2.96706" upper="2.96706" effort="100" velocity="3.0"/>
  </joint>
  <joint name="a2" type="revolute">
    <parent link="link_1"/><child link="link_2"/>
    <origin xyz="0 0 0.40" rpy="0 0 0"/><axis xyz="0 1 0"/>
    <limit lower="-2.0944" upper="2.0944" effort="80" velocity="3.0"/>
  </joint>
  <joint name="a3" type="revolute">
    <parent link="link_2"/><child link="flange"/>
    <origin xyz="0 0 0.30" rpy="0 0 0"/><axis xyz="0 1 0"/>
    <limit lower="-2.61799" upper="2.61799" effort="40" velocity="4.0"/>
  </joint>
</robot>
"""


def _ensure_container(c: V2Client, name: str) -> dict:
    rows = c.get("/containers?kind=timeseries") or []
    rows = rows if isinstance(rows, list) else rows.get("content", [])
    for row in rows:
        if row.get("name") == name:
            return row
    return c.post("/containers?kind=timeseries", {"name": name})


def _find_mapping_template(c: V2Client, name: str) -> dict | None:
    rows = c.get("/templates?kind=MAPPING_RECIPE") or []
    rows = rows if isinstance(rows, list) else rows.get("content", [])
    for row in rows:
        if row.get("name") == name:
            return row
    return None


def main(argv: list[str] | None = None) -> int:
    parser = build_arg_parser(__doc__)
    args = parser.parse_args(argv)
    try:
        c = client_from_args(args)
    except SystemExit as e:
        log("ERROR", str(e), "")
        return 2

    if args.reset and c.reset_collection(SLUG):
        log("reset", SLUG, "Collection", "deleted existing")

    existing = c.find_collection_by_name(SLUG)
    if existing:
        coll = existing
        log("reuse", SLUG, "Collection", coll["appId"])
    else:
        coll = c.create_collection(SLUG, COLLECTION_DESC)
        log("create", SLUG, "Collection", coll["appId"])
    coll_app = coll["appId"]

    do = c.create_data_object(
        coll_app, "KR3 Pick Program",
        description="Synthetic KUKA KRL pick program + URDF → derived joint trajectory."
    )
    do_app = do["appId"]
    log("create", "KR3 Pick Program", "DataObject", do_app)

    # FR1b singleton uploads — one .src, one .urdf.
    src_ref = c.upload_file(do_app, "synthetic_pick.src", SYNTHETIC_KRL_SRC.encode("utf-8"))
    src_app = src_ref["appId"]
    log("upload", "synthetic_pick.src", "FileReference (FR1b)", src_app)

    urdf_ref = c.upload_file(do_app, "synthetic_kr3.urdf", SYNTHETIC_URDF.encode("utf-8"))
    urdf_app = urdf_ref["appId"]
    log("upload", "synthetic_kr3.urdf", "FileReference (FR1b)", urdf_app)

    # TimeseriesContainer the derived trajectory writes into.
    tsc = _ensure_container(c, f"{SLUG}-trajectory")
    tsc_app = tsc["appId"]
    log("ensure", tsc["name"], "TimeseriesContainer", tsc_app)

    # MAPPING_RECIPE declaring the KrlTrajectoryShape IRI + targets.
    recipe_body = {
        "mappingRecipeShape": KRL_TRAJECTORY_SHAPE_IRI,
        "transform": "krl-trajectory",
        "srcFileReferenceAppId": src_app,
        "urdfFileReferenceAppId": urdf_app,
        "targetDataObjectAppId": do_app,
        "timeseriesContainerAppId": tsc_app,
    }
    # The recipe body pins targetDataObjectAppId + timeseriesContainerAppId, which are
    # collection-scoped and change on every --reset. Templates are NOT collection-scoped,
    # so a reused template would carry a stale (deleted) DataObject appId. PATCH the body
    # to the current appIds when reusing; create fresh otherwise.
    existing_tmpl = _find_mapping_template(c, f"{SLUG}-krl-recipe")
    if existing_tmpl:
        # Template PATCH @Consumes application/json (NOT merge-patch); call _request directly.
        tmpl = c._request(
            "PATCH", f"{c.v2}/templates/{existing_tmpl['appId']}",
            body={"body": json.dumps(recipe_body)}, content_type="application/json")
        log("patch", tmpl["name"], "MAPPING_RECIPE template (refreshed body)", tmpl["appId"])
    else:
        tmpl = c.post(
            "/templates",
            {
                "name": f"{SLUG}-krl-recipe",
                "templateKind": "MAPPING_RECIPE",
                "body": json.dumps(recipe_body),
                "description": "KRL .src + URDF → derived joint trajectory (synthetic).",
                "tags": ["krl", "robotics", "showcase", "synthetic"],
            },
        )
        log("create", tmpl["name"], "MAPPING_RECIPE template", tmpl["appId"])
    tmpl_app = tmpl["appId"]

    # ── Materialize: KRL interpret → derived TimeseriesReference ─────────────
    materialize_body = {
        "inputReferenceAppIds": {
            "srcFileAppId": src_app,
            "urdfFileAppId": urdf_app,
        }
    }
    derived_app = None
    try:
        result = c.post(f"/mappings/{tmpl_app}/materialize", materialize_body)
        derived_app = (
            result.get("derivedReferenceAppId")
            or result.get("referenceAppId")
            or (result.get("reference") or {}).get("appId")
            or result.get("appId")
        )
        if derived_app:
            log("materialize", "KRL interpret", "derived TimeseriesReference", derived_app)
            # Verify the derived reference resolves on the target DataObject.
            ref = c.get(f"/references/{derived_app}")
            assert ref.get("appId") == derived_app
            log("verify", "derived reference resolves", ref.get("kind", "timeseries"), derived_app)
        else:
            log("GAP", "materialize returned no derived appId", "krl-interpreter",
                json.dumps(result)[:160])
    except HttpError as e:
        if e.status == 404 and "not registered" in e.body:
            log("GAP", "no TransformExecutor for KrlTrajectoryShape", "krl-interpreter",
                "plugin disabled? Enable: PATCH /v2/admin/plugins/krl-interpreter {enabled:true} + restart")
        elif e.status in (422, 400) and ("sidecar" in e.body.lower() or "interpret" in e.body.lower()):
            log("GAP", "KRL interpreter sidecar unavailable/errored", f"HTTP {e.status}",
                e.body[:200])
        else:
            log("GAP", "materialize failed", f"HTTP {e.status}", e.body[:200])

    print()
    log("DONE", SLUG, "Collection", coll_app)
    print(f"   Frontend:   {c.frontend_collection_url(coll_app)}")
    print(f"   DataObject: {c.frontend_data_object_url(coll_app, do_app)}")
    print(f"   KRL .src FileReference appId:  {src_app}")
    print(f"   URDF FileReference appId:      {urdf_app}")
    print(f"   MAPPING_RECIPE template appId: {tmpl_app}")
    print(f"   Derived trajectory ref appId:  {derived_app or 'GAP — sidecar/plugin'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
