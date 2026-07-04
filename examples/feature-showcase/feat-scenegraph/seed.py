"""feat-scenegraph — B4: a scene-graph as a MAPPING_RECIPE (URDF + joint TS → played Trace3D).

Feature it proves
-----------------
Per aidocs/platform/191 decision #2, scene-graph is NOT its own endpoint anymore —
it is a MAPPING_RECIPE shape materialized through the generic
``POST /v2/mappings/{templateAppId}/materialize`` dispatch. The ``vis-trace3d``
plugin's ``SceneGraphPlayTransformExecutor`` (``TransformExecutor`` SPI) claims the
``http://semantics.dlr.de/shepard/transform#SceneGraphPlayShape`` IRI: it resolves +
parses a URDF FileReference (the kinematic tree, parsed on demand — never a stored
:DigitalTwinScene), reads a channel→joint binding plan, and returns a VIEW result —
the "play envelope" (frame tree + joint binding plan) a Trace3D-family renderer
plays back.

What this seed does
-------------------
  1. Creates the ``feat-scenegraph`` Collection + a "3-DOF Robot Cell" DataObject.
  2. Uploads a *synthetic* 3-joint URDF as a singleton FileReference (FR1b,
     POST /v2/files) — one file, NOT a FileBundle.
  3. Creates a joint-trajectory TimeseriesContainer + TimeseriesReference (the
     joint channels the bindings reference).
  4. Mints a MAPPING_RECIPE template declaring the SceneGraphPlayShape IRI + the
     URDF/joint-TS bindings + a channel→joint binding map.
  5. POSTs ``/v2/mappings/{templateAppId}/materialize`` and asserts the returned
     play-envelope view has the URDF's frames/joints + the binding plan.

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

SLUG = "feat-scenegraph"
COLLECTION_DESC = (
    "Scene-graph showcase: a synthetic 3-DOF URDF + joint-trajectory TimeseriesReference "
    "materialized as a played Trace3D scene-graph via POST /v2/mappings/{appId}/materialize "
    "(vis-trace3d SceneGraphPlayTransformExecutor). Synthetic data only — NOT real DLR data."
)

SCENE_GRAPH_PLAY_SHAPE_IRI = "http://semantics.dlr.de/shepard/transform#SceneGraphPlayShape"

# Minimal synthetic 3-joint serial robot URDF (obviously synthetic; base→a→b→tcp).
SYNTHETIC_URDF = """<?xml version="1.0"?>
<robot name="synthetic_3dof_cell">
  <link name="base_link"/>
  <link name="link_a"/>
  <link name="link_b"/>
  <link name="tcp"/>

  <joint name="joint_1" type="revolute">
    <parent link="base_link"/>
    <child link="link_a"/>
    <origin xyz="0 0 0.30" rpy="0 0 0"/>
    <axis xyz="0 0 1"/>
    <limit lower="-3.14159" upper="3.14159" effort="100" velocity="2.0"/>
  </joint>

  <joint name="joint_2" type="revolute">
    <parent link="link_a"/>
    <child link="link_b"/>
    <origin xyz="0 0 0.40" rpy="0 0 0"/>
    <axis xyz="0 1 0"/>
    <limit lower="-2.09439" upper="2.09439" effort="80" velocity="2.0"/>
  </joint>

  <joint name="joint_3" type="revolute">
    <parent link="link_b"/>
    <child link="tcp"/>
    <origin xyz="0 0 0.25" rpy="0 0 0"/>
    <axis xyz="0 1 0"/>
    <limit lower="-2.61799" upper="2.61799" effort="40" velocity="3.0"/>
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
        coll_app, "3-DOF Robot Cell",
        description="Synthetic 3-joint serial robot — URDF + joint trajectory."
    )
    do_app = do["appId"]
    log("create", "3-DOF Robot Cell", "DataObject", do_app)

    # URDF FileReference (FR1b singleton — one file).
    urdf_ref = c.upload_file(do_app, "synthetic_3dof_cell.urdf", SYNTHETIC_URDF.encode("utf-8"))
    urdf_app = urdf_ref["appId"]
    log("upload", "synthetic_3dof_cell.urdf", "FileReference (FR1b)", urdf_app)

    # Joint-trajectory TimeseriesContainer + TimeseriesReference.
    tsc = _ensure_container(c, f"{SLUG}-joints")
    log("ensure", tsc["name"], "TimeseriesContainer", tsc["appId"])
    joint_ref = c.post(
        f"/references?kind=timeseries&dataObjectAppId={do_app}",
        {
            "name": "joint-trajectory",
            "start": 0,
            "end": 10_000,
            "timeseriesContainerId": tsc["id"],
            "timeseries": [
                {"measurement": "robot", "device": "controller", "location": "cell",
                 "symbolicName": f"joint_{i}", "field": "position"}
                for i in (1, 2, 3)
            ],
        },
    )
    joint_ts_app = joint_ref["appId"]
    log("create", "joint-trajectory", "TimeseriesReference", joint_ts_app)

    # MAPPING_RECIPE template declaring the SceneGraphPlayShape IRI + bindings.
    joint_channel_bindings = [
        {"joint": f"joint_{i}",
         "channelSelector": json.dumps(
             {"measurement": "robot", "device": "controller", "location": "cell",
              "symbolicName": f"joint_{i}", "field": "position"})}
        for i in (1, 2, 3)
    ]
    recipe_body = {
        "mappingRecipeShape": SCENE_GRAPH_PLAY_SHAPE_IRI,
        "transform": "scene-graph-play",
        "urdfFileReferenceAppId": urdf_app,
        "jointTimeseriesReferenceAppId": joint_ts_app,
        "jointChannelBindings": joint_channel_bindings,
    }
    # Body pins the URDF/joint-TS appIds, which change on every --reset (templates are
    # not collection-scoped). PATCH the body to current appIds on reuse; create fresh
    # otherwise. (The materialize call also passes inputReferenceAppIds which take
    # precedence, but keeping the body current avoids a stale-pin foot-gun.)
    existing_tmpl = _find_mapping_template(c, f"{SLUG}-play-recipe")
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
                "name": f"{SLUG}-play-recipe",
                "templateKind": "MAPPING_RECIPE",
                "body": json.dumps(recipe_body),
                "description": "Scene-graph play recipe — URDF + joint TS → played Trace3D (synthetic).",
                "tags": ["scenegraph", "robotics", "showcase", "synthetic"],
            },
        )
        log("create", tmpl["name"], "MAPPING_RECIPE template", tmpl["appId"])
    tmpl_app = tmpl["appId"]

    # ── Materialize the scene-graph play envelope ────────────────────────────
    materialize_body = {
        "inputReferenceAppIds": {
            "urdfFileAppId": urdf_app,
            "jointTimeseriesAppId": joint_ts_app,
        }
    }
    try:
        result = c.post(f"/mappings/{tmpl_app}/materialize", materialize_body)
    except HttpError as e:
        if e.status == 404 and "not registered" in e.body:
            log("GAP", "no TransformExecutor for SceneGraphPlayShape", "vis-trace3d",
                "plugin disabled? Enable: PATCH /v2/admin/plugins/vis-trace3d {enabled:true} + restart")
            _summary(c, coll_app, do_app, urdf_app, joint_ts_app, tmpl_app, played=False)
            return 0
        raise

    view = result.get("view") or result.get("derivedView") or result
    # The play envelope is nested under the materialize response's view payload.
    envelope = _extract_envelope(result)
    frames = envelope.get("frames", [])
    joints = envelope.get("joints", [])
    bindings = envelope.get("jointChannelBindings", [])
    assert envelope.get("kind") == "scene-graph-play", f"unexpected envelope: {json.dumps(envelope)[:200]}"
    assert len(frames) == 4, f"expected 4 frames (links), got {len(frames)}"
    assert len(joints) == 3, f"expected 3 joints, got {len(joints)}"
    log("materialize", f"robot={envelope.get('robotName')}", "play-envelope",
        f"{len(frames)} frames / {len(joints)} joints / {len(bindings)} bindings / "
        f"playbackStatus={envelope.get('playbackStatus')}")

    _summary(c, coll_app, do_app, urdf_app, joint_ts_app, tmpl_app, played=True)
    return 0


def _extract_envelope(result: dict) -> dict:
    """The materialize response wraps the executor's view payload; find the play envelope."""
    for key in ("view", "viewModel", "derivedView", "result", "payload"):
        v = result.get(key)
        if isinstance(v, dict) and v.get("kind") == "scene-graph-play":
            return v
    if result.get("kind") == "scene-graph-play":
        return result
    # Deep-search one level for the envelope.
    for v in result.values():
        if isinstance(v, dict) and v.get("kind") == "scene-graph-play":
            return v
    return result


def _summary(c, coll_app, do_app, urdf_app, joint_ts_app, tmpl_app, played):
    print()
    log("DONE", SLUG, "Collection", coll_app)
    print(f"   Frontend:   {c.frontend_collection_url(coll_app)}")
    print(f"   DataObject: {c.frontend_data_object_url(coll_app, do_app)}")
    print(f"   URDF FileReference appId: {urdf_app}")
    print(f"   Joint TimeseriesReference appId: {joint_ts_app}")
    print(f"   MAPPING_RECIPE template appId: {tmpl_app}")
    print(f"   Scene-graph play: {'OK (materialized)' if played else 'GAP — executor not registered'}")


if __name__ == "__main__":
    raise SystemExit(main())
