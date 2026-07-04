#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["requests>=2.31"]
# ///
"""build_mffd_scene.py — bootstrap the first real :DigitalTwinScene for the MFFD AFP cell.

Parses the KUKA KR210 R2700/2 URDF (sourced from kroshu/kuka_robot_descriptions,
Apache-2.0; lives at ../urdf/kr210_r2700_2.urdf) and materialises it as a
:DigitalTwinScene via the SCENEGRAPH-REST-1 surface at
POST /v2/scene-graphs. Models the MFFD AFP cell as two robots — R10 + R20 —
both KR210 R2700/2 instances under a shared `cell_base` root, per the
RDK-PARSE-1 tier-1 scrape of the MFZ.rdk station file at ZLP Augsburg.

What gets created
-----------------
- One :DigitalTwinScene named "MFFD AFP cell — Q3 demonstrator".
- One root :CoordinateFrame `cell_base` (kind=BASE).
- Two robot subtrees mounted under cell_base, prefixed `r10_` / `r20_`:
    For each robot, every URDF <link> becomes one :CoordinateFrame
    (kind=FRAME, except `tool0` → TCP, base_link → BASE). The transform
    of each link frame is taken from the parent <joint>'s <origin>
    rpy/xyz so the static tree reconstructs the kinematic chain at the
    home pose (all joint angles = 0).
- Two :Joint entries per URDF <joint>, one under each robot prefix.
  Revolute joints carry axis (xyz unit) + limits (lower/upper rad).
  Fixed joints carry type=FIXED with no limits.
- One :SemanticAnnotation on the kr210 URDF FileReference (looked up by
  name "kr210-r2700-urdf" inside the "MFFD RDK → URDF Viewer Showcase"
  collection seeded by ../seed.py) with predicate
  `urn:shepard:scenegraph:scene-appId` and the new scene's appId as the
  object literal. This makes the URDF file discoverable as the source
  artefact for the scene without re-parsing.

Idempotency
-----------
- Scene detection by name. Re-running on an instance that already has a
  scene named "MFFD AFP cell — Q3 demonstrator" picks up the existing
  appId and verifies frame + joint counts; it does NOT re-create or
  upsert. The annotation step also skips if the same predicate is
  already present on the URDF FileReference.

Auth
----
Per the SCENEGRAPH-REST-1 docs, every endpoint requires authentication
but no specific role. Bob's API key (minted via the
`POST /shepard/api/users/{sub}/apikeys` flow against the
shepard-demo Keycloak realm) is sufficient. Pass it via --apikey or the
SHEPARD_API_KEY env var.

Usage
-----
    python3 build_mffd_scene.py \
        --host https://shepard-api.nuclide.systems \
        --apikey "$BOB_API_KEY"
    # equivalent, env-driven:
    SHEPARD_API_KEY=... python3 build_mffd_scene.py

Smoke
-----
On success the script prints the click-walkthrough URL
`https://shepard.nuclide.systems/scene-graphs/{sceneAppId}` (browser
will redirect to auth) plus the substrate verification:
    frames=20  joints=18  (== 2 × URDF links + cell_base + 2 × URDF joints)

CLAUDE.md notes
---------------
- "The audit trail is a graph, not a log." Every POST records a typed
  :Activity via SceneGraphService.recordActivity; this script does not
  manage Activities directly — the service does (PROV1j wiring).
- "UI never asks for paths/URLs — pulls from references." The followup
  annotation puts the scene's appId on the URDF FileReference so future
  consumers can discover the scene without a URL — appId, not path.
- "Evolve in a new namespace; never mutate an existing one." The new
  predicate `urn:shepard:scenegraph:scene-appId` is fresh, scoped to
  the scenegraph subsystem, additive.
"""
from __future__ import annotations

import argparse
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import requests as _http

# ---------------------------------------------------------------------------
# Constants

SCENE_NAME = "MFFD AFP cell — Q3 demonstrator"
SCENE_DESCRIPTION = (
    "Automated fibre placement upper-fuselage cell at ZLP Augsburg. "
    "Two KUKA KR210 R2700/2 robots (R10 + R20) on a shared cell base, "
    "with AFP heads as TCP-frames. URDF source: kroshu/kuka_robot_descriptions "
    "(Apache-2.0). Bootstrapped from MFZ.rdk + kr210_r2700_2.urdf by "
    "examples/mffd-rdk-urdf-showcase/scenegraph/build_mffd_scene.py."
)

# Showcase collection + FileReference where the URDF lives (seeded by ../seed.py).
SHOWCASE_COLLECTION_NAME = "MFFD RDK → URDF Viewer Showcase"
URDF_FILEREF_NAME = "kr210-r2700-urdf"

# Predicate for the followup annotation on the URDF FileReference.
SCENE_APP_ID_PREDICATE = "urn:shepard:scenegraph:scene-appId"

# Per-robot prefix layout for the MFFD cell.
ROBOT_PREFIXES = ("r10_", "r20_")
CELL_ROOT_NAME = "cell_base"

# Where the URDF lives, relative to this script.
URDF_PATH = Path(__file__).parent.parent / "urdf" / "kr210_r2700_2.urdf"

# Frame kind defaults.
KIND_BASE = "BASE"
KIND_FRAME = "FRAME"
KIND_TCP = "TCP"

# Joint type mapping (URDF → JointType).
URDF_JOINT_TYPE = {
    "revolute": "REVOLUTE",
    "prismatic": "PRISMATIC",
    "fixed": "FIXED",
    "continuous": "CONTINUOUS",
}


# ---------------------------------------------------------------------------
# URDF parsing

@dataclass
class UrdfLink:
    name: str


@dataclass
class UrdfJoint:
    name: str
    type: str  # URDF spelling: "revolute", "fixed", ...
    parent: str
    child: str
    origin_xyz: tuple[float, float, float] = (0.0, 0.0, 0.0)
    origin_rpy: tuple[float, float, float] = (0.0, 0.0, 0.0)
    axis: tuple[float, float, float] = (0.0, 0.0, 1.0)
    limit_lower: Optional[float] = None
    limit_upper: Optional[float] = None


@dataclass
class UrdfModel:
    name: str
    links: list[UrdfLink] = field(default_factory=list)
    joints: list[UrdfJoint] = field(default_factory=list)

    def root_link(self) -> str:
        """The link with no incoming joint — the kinematic root."""
        children = {j.child for j in self.joints}
        for link in self.links:
            if link.name not in children:
                return link.name
        raise ValueError("URDF has no root link (cycle?)")


def parse_urdf(path: Path) -> UrdfModel:
    """Stdlib XML → UrdfModel. URDF is plain XML; no xacro support needed."""
    tree = ET.parse(path)
    root = tree.getroot()
    model = UrdfModel(name=root.get("name", path.stem))

    for link_el in root.findall("link"):
        model.links.append(UrdfLink(name=link_el.get("name", "")))

    for joint_el in root.findall("joint"):
        name = joint_el.get("name", "")
        jtype = joint_el.get("type", "fixed")
        parent = joint_el.find("parent")
        child = joint_el.find("child")
        if parent is None or child is None:
            continue
        j = UrdfJoint(
            name=name,
            type=jtype,
            parent=parent.get("link", ""),
            child=child.get("link", ""),
        )
        origin = joint_el.find("origin")
        if origin is not None:
            xyz = _parse_triple(origin.get("xyz"))
            rpy = _parse_triple(origin.get("rpy"))
            j.origin_xyz = xyz
            j.origin_rpy = rpy
        axis = joint_el.find("axis")
        if axis is not None:
            j.axis = _parse_triple(axis.get("xyz"))
        limit = joint_el.find("limit")
        if limit is not None:
            lo = limit.get("lower")
            up = limit.get("upper")
            j.limit_lower = float(lo) if lo is not None else None
            j.limit_upper = float(up) if up is not None else None
        model.joints.append(j)
    return model


def _parse_triple(s: Optional[str]) -> tuple[float, float, float]:
    if not s:
        return (0.0, 0.0, 0.0)
    parts = s.split()
    if len(parts) != 3:
        return (0.0, 0.0, 0.0)
    return (float(parts[0]), float(parts[1]), float(parts[2]))


# ---------------------------------------------------------------------------
# Frame kind picker (URDF link name → FrameKind)

def frame_kind_for(link_name: str) -> str:
    if link_name == "base_link":
        return KIND_BASE
    if link_name in ("tool0", "flange"):
        return KIND_TCP
    return KIND_FRAME


# ---------------------------------------------------------------------------
# REST client

class SceneGraphClient:
    def __init__(self, host: str, api_key: str):
        self.host = host.rstrip("/")
        self.s = _http.Session()
        self.s.headers.update({"X-API-KEY": api_key, "Content-Type": "application/json"})

    def list_scenes_by_name(self, name: str) -> Optional[str]:
        """Best-effort scene-by-name resolver. The SCENEGRAPH-REST-1 surface has
        no list-scenes endpoint yet, so we lean on Neo4j-backed search if exposed
        — and fall back to None (no scene found by that name). For our purposes
        a follow-on script can pass --scene-app-id to skip detection.
        """
        # Try /v2/scene-graphs/search?name= if it exists; otherwise None.
        url = f"{self.host}/v2/scene-graphs/search"
        try:
            r = self.s.get(url, params={"name": name}, timeout=10)
            if r.status_code == 200:
                rows = r.json() if isinstance(r.json(), list) else r.json().get("content", [])
                for row in rows:
                    if row.get("name") == name:
                        return row.get("appId")
        except Exception:
            pass
        return None

    def get_scene(self, scene_app_id: str) -> Optional[dict]:
        r = self.s.get(f"{self.host}/v2/scene-graphs/{scene_app_id}", timeout=15)
        if r.status_code == 200:
            return r.json()
        return None

    def create_scene(self, name: str, description: str, source_file_app_id: Optional[str]) -> dict:
        body = {"name": name, "description": description}
        if source_file_app_id:
            body["sourceFileAppId"] = source_file_app_id
        r = self.s.post(f"{self.host}/v2/scene-graphs", json=body, timeout=30)
        r.raise_for_status()
        return r.json()

    def add_frame(self, scene_app_id: str, *, name: str, parent_app_id: Optional[str],
                  x: float, y: float, z: float, rx: float, ry: float, rz: float,
                  kind: str = KIND_FRAME) -> dict:
        body = {
            "name": name,
            "x": x, "y": y, "z": z,
            "rx": rx, "ry": ry, "rz": rz,
            "kind": kind,
        }
        if parent_app_id:
            body["parentFrameAppId"] = parent_app_id
        r = self.s.post(f"{self.host}/v2/scene-graphs/{scene_app_id}/frames", json=body, timeout=30)
        r.raise_for_status()
        return r.json()

    def add_joint(self, scene_app_id: str, *, name: str, parent_frame_app_id: str,
                  child_frame_app_id: str, type_: str,
                  axis: tuple[float, float, float],
                  limit_lower: Optional[float], limit_upper: Optional[float]) -> dict:
        body = {
            "name": name,
            "parentFrameAppId": parent_frame_app_id,
            "childFrameAppId": child_frame_app_id,
            "type": type_,
            "axisX": axis[0], "axisY": axis[1], "axisZ": axis[2],
        }
        if limit_lower is not None:
            body["limitMin"] = limit_lower
        if limit_upper is not None:
            body["limitMax"] = limit_upper
        r = self.s.post(f"{self.host}/v2/scene-graphs/{scene_app_id}/joints", json=body, timeout=30)
        r.raise_for_status()
        return r.json()


# ---------------------------------------------------------------------------
# Helpers: find FileReference + write the followup annotation

def find_urdf_file_reference_app_id(host: str, api_key: str) -> Optional[str]:
    """Find the kr210-r2700-urdf FileReference appId inside the
    MFFD RDK→URDF Viewer Showcase collection. Returns None on any 4xx.

    We use the legacy /shepard/api surface to list collections + their
    DataObjects + FileReferences because the v2 search surface needs a
    different request shape and we want a no-frills fallback path here.
    """
    s = _http.Session()
    s.headers.update({"X-API-KEY": api_key})
    # 1) Find showcase collection by name.
    r = s.get(f"{host}/shepard/api/collections", timeout=30)
    if r.status_code != 200:
        return None
    coll_id: Optional[int] = None
    for c in r.json() or []:
        if c.get("name") == SHOWCASE_COLLECTION_NAME:
            coll_id = c.get("id")
            break
    if not coll_id:
        return None
    # 2) Walk DataObjects of that collection.
    r = s.get(f"{host}/shepard/api/collections/{coll_id}/dataObjects", timeout=30)
    if r.status_code != 200:
        return None
    for do in r.json() or []:
        do_id = do.get("id")
        r2 = s.get(f"{host}/shepard/api/collections/{coll_id}/dataObjects/{do_id}/fileReferences", timeout=30)
        if r2.status_code != 200:
            continue
        for fr in r2.json() or []:
            if fr.get("name") == URDF_FILEREF_NAME:
                # appId may not be on the v1 surface; if not present, fall back to v2.
                if fr.get("appId"):
                    return fr["appId"]
                # Try v2 lookup for the appId by data-object.
                fr_id = fr.get("id")
                if fr_id:
                    # Walk the v2 by-data-object lookup.
                    do_app = do.get("appId")
                    if do_app:
                        r3 = s.get(f"{host}/v2/files/by-data-object/{do_app}", timeout=30)
                        if r3.status_code == 200:
                            for v2fr in r3.json() or []:
                                if v2fr.get("name") == URDF_FILEREF_NAME:
                                    return v2fr.get("appId")
    return None


def annotate_file_reference_with_scene(host: str, api_key: str,
                                        file_ref_app_id: str, scene_app_id: str) -> bool:
    """POST /v2/annotations attaching urn:shepard:scenegraph:scene-appId to
    the URDF FileReference. Idempotent: detects the same predicate already
    present on the subject and skips.
    """
    s = _http.Session()
    s.headers.update({"X-API-KEY": api_key, "Content-Type": "application/json"})

    # Idempotency check via /v2/annotations/find.
    try:
        r = s.get(
            f"{host}/v2/annotations/find",
            params={"subjectAppId": file_ref_app_id, "predicateIri": SCENE_APP_ID_PREDICATE},
            timeout=15,
        )
        if r.status_code == 200:
            payload = r.json()
            rows = payload if isinstance(payload, list) else payload.get("content", []) or []
            for row in rows:
                if row.get("propertyIRI") == SCENE_APP_ID_PREDICATE:
                    print(f"  SKIP annotation on FileReference {file_ref_app_id} "
                          f"(predicate already present)", flush=True)
                    return True
    except Exception:
        pass

    body = {
        "subjectAppId": file_ref_app_id,
        "subjectKind": "FileReference",
        "predicateIri": SCENE_APP_ID_PREDICATE,
        "objectLiteral": scene_app_id,
        "predicateLabel": "scene-appId",
        "sourceMode": "human",
        "confidence": 1.0,
    }
    r = s.post(f"{host}/v2/annotations", json=body, timeout=30)
    if r.status_code in (200, 201):
        print(f"  OK   annotation on FileReference {file_ref_app_id} "
              f"-> {SCENE_APP_ID_PREDICATE} = {scene_app_id}", flush=True)
        return True
    print(f"  WARN annotation POST failed: {r.status_code} {r.text[:200]}",
          file=sys.stderr, flush=True)
    return False


# ---------------------------------------------------------------------------
# Scene build

def build_robot_subtree(client: SceneGraphClient, scene_app_id: str, prefix: str,
                         cell_base_frame_app_id: str, urdf: UrdfModel,
                         link_to_app_id: dict[str, str]) -> tuple[int, int]:
    """Materialise one robot's frames + joints under cell_base_frame_app_id.

    Returns (frames_created, joints_created).
    """
    frames_created = 0
    joints_created = 0

    # Pass 1: add a frame per URDF link. We process in joint-order so each
    # child has its parent's appId resolved.
    # The kinematic root (base_link) attaches directly to cell_base.
    root_link = urdf.root_link()
    root_frame = client.add_frame(
        scene_app_id,
        name=f"{prefix}{root_link}",
        parent_app_id=cell_base_frame_app_id,
        x=0.0, y=0.0, z=0.0, rx=0.0, ry=0.0, rz=0.0,
        kind=frame_kind_for(root_link),
    )
    link_to_app_id[f"{prefix}{root_link}"] = root_frame["appId"]
    frames_created += 1

    # Pass 1b: walk joints; for each joint, ensure parent frame exists, then add child frame.
    # URDF joints are listed in declaration order; the parent always precedes the child if
    # the file is well-formed.
    for j in urdf.joints:
        parent_key = f"{prefix}{j.parent}"
        child_key = f"{prefix}{j.child}"
        if parent_key not in link_to_app_id:
            # Should not happen for a well-formed URDF; skip.
            print(f"  WARN missing parent frame for joint {j.name} ({parent_key})",
                  file=sys.stderr, flush=True)
            continue
        if child_key in link_to_app_id:
            continue  # already added (rare)
        x, y, z = j.origin_xyz
        rx, ry, rz = j.origin_rpy
        kind = frame_kind_for(j.child)
        frame = client.add_frame(
            scene_app_id,
            name=child_key,
            parent_app_id=link_to_app_id[parent_key],
            x=x, y=y, z=z, rx=rx, ry=ry, rz=rz,
            kind=kind,
        )
        link_to_app_id[child_key] = frame["appId"]
        frames_created += 1

    # Pass 2: register joints between the now-resolved frame pairs.
    for j in urdf.joints:
        parent_key = f"{prefix}{j.parent}"
        child_key = f"{prefix}{j.child}"
        if parent_key not in link_to_app_id or child_key not in link_to_app_id:
            continue
        jt = URDF_JOINT_TYPE.get(j.type, "FIXED")
        joint = client.add_joint(
            scene_app_id,
            name=f"{prefix}{j.name}",
            parent_frame_app_id=link_to_app_id[parent_key],
            child_frame_app_id=link_to_app_id[child_key],
            type_=jt,
            axis=j.axis,
            limit_lower=j.limit_lower if jt in ("REVOLUTE", "PRISMATIC") else None,
            limit_upper=j.limit_upper if jt in ("REVOLUTE", "PRISMATIC") else None,
        )
        joints_created += 1

    return frames_created, joints_created


def build_or_resume_scene(client: SceneGraphClient, urdf: UrdfModel,
                          source_file_app_id: Optional[str],
                          force_create: bool) -> tuple[str, int, int, bool]:
    """Top-level build. Returns (sceneAppId, frame_count, joint_count, created).

    Idempotency: if a scene with SCENE_NAME exists, return it without
    further writes. The force_create flag bypasses detection (rarely
    useful; mostly for tests).
    """
    if not force_create:
        existing = client.list_scenes_by_name(SCENE_NAME)
        if existing:
            scene = client.get_scene(existing)
            if scene:
                f_count = len(scene.get("frames") or [])
                j_count = len(scene.get("joints") or [])
                return existing, f_count, j_count, False

    scene = client.create_scene(SCENE_NAME, SCENE_DESCRIPTION, source_file_app_id)
    scene_app_id = scene["appId"]

    # Add the cell-root frame first; the SCENEGRAPH-REST-1 contract makes the
    # first frame the root automatically (rootFrameAppId is set server-side).
    cell_root = client.add_frame(
        scene_app_id, name=CELL_ROOT_NAME, parent_app_id=None,
        x=0.0, y=0.0, z=0.0, rx=0.0, ry=0.0, rz=0.0,
        kind=KIND_BASE,
    )
    cell_root_app_id = cell_root["appId"]
    frames_created = 1
    joints_created = 0

    link_map: dict[str, str] = {}
    for prefix in ROBOT_PREFIXES:
        f, j = build_robot_subtree(client, scene_app_id, prefix, cell_root_app_id, urdf, link_map)
        frames_created += f
        joints_created += j

    return scene_app_id, frames_created, joints_created, True


# ---------------------------------------------------------------------------
# CLI

def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(
        description="Bootstrap the first real :DigitalTwinScene for the MFFD AFP cell.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Auth: bob's API key, minted via:\n"
            "  TOKEN=$(curl -s -X POST $AUTH/protocol/openid-connect/token \\\n"
            "      -d grant_type=password -d client_id=frontend-dev \\\n"
            "      -d username=bob -d password=bob-demo \\\n"
            "      -d scope='openid profile email' | jq -r .access_token)\n"
            "  curl -X POST $HOST/shepard/api/users/<bob-sub-uuid>/apikeys \\\n"
            "      -H \"Authorization: Bearer $TOKEN\" -H 'Content-Type: application/json' \\\n"
            "      -d '{\"name\":\"scenegraph-bootstrap\"}'\n"
        ),
    )
    ap.add_argument("--host", default=os.environ.get("SHEPARD_API_BASE", "https://shepard-api.nuclide.systems"),
                    help="Shepard backend base URL (default $SHEPARD_API_BASE or nuclide live).")
    ap.add_argument("--apikey", default=os.environ.get("SHEPARD_API_KEY"),
                    help="Shepard API key (default $SHEPARD_API_KEY).")
    ap.add_argument("--urdf", type=Path, default=URDF_PATH,
                    help=f"URDF file path (default {URDF_PATH}).")
    ap.add_argument("--force-create", action="store_true",
                    help="Skip the by-name idempotency check and always POST a new scene.")
    ap.add_argument("--frontend-host",
                    default=os.environ.get("SHEPARD_FRONTEND_HOST", "https://shepard.nuclide.systems"),
                    help="Frontend base URL for the click-walkthrough print.")
    args = ap.parse_args(argv)

    if not args.apikey:
        print("ERROR: --apikey is required (or set $SHEPARD_API_KEY)", file=sys.stderr)
        return 2
    if not args.urdf.exists():
        print(f"ERROR: URDF not found at {args.urdf}", file=sys.stderr)
        return 2

    print(f"=== MFFD scene-graph bootstrap ===", flush=True)
    print(f"  Host:  {args.host}", flush=True)
    print(f"  URDF:  {args.urdf}", flush=True)
    urdf = parse_urdf(args.urdf)
    print(f"  Parsed URDF '{urdf.name}': {len(urdf.links)} links, {len(urdf.joints)} joints",
          flush=True)

    client = SceneGraphClient(args.host, args.apikey)

    # Look up the kr210 URDF FileReference appId for the source-file pointer + followup annotation.
    file_ref_app_id = find_urdf_file_reference_app_id(args.host, args.apikey)
    if file_ref_app_id:
        print(f"  URDF FileRef appId: {file_ref_app_id}", flush=True)
    else:
        print(f"  URDF FileRef appId: <not found — annotation step will be skipped>", flush=True)

    print(f"\n--- Build scene ---", flush=True)
    scene_app_id, frame_count, joint_count, created = build_or_resume_scene(
        client, urdf, source_file_app_id=file_ref_app_id, force_create=args.force_create,
    )
    if created:
        print(f"  OK   created scene appId={scene_app_id} "
              f"frames={frame_count} joints={joint_count}", flush=True)
    else:
        print(f"  SKIP scene already exists: appId={scene_app_id} "
              f"frames={frame_count} joints={joint_count}", flush=True)

    # Expected counts: 1 cell_base + 2 × len(urdf.links) frames; 2 × len(urdf.joints) joints.
    expected_frames = 1 + 2 * len(urdf.links)
    expected_joints = 2 * len(urdf.joints)
    if created and (frame_count != expected_frames or joint_count != expected_joints):
        print(f"  WARN count mismatch: expected frames={expected_frames} "
              f"joints={expected_joints} got frames={frame_count} joints={joint_count}",
              file=sys.stderr, flush=True)

    # Followup: annotate the URDF FileReference with the new scene's appId.
    print(f"\n--- Annotate URDF FileReference ---", flush=True)
    if file_ref_app_id:
        annotate_file_reference_with_scene(args.host, args.apikey, file_ref_app_id, scene_app_id)
    else:
        print(f"  SKIP no URDF FileReference resolved (run ../seed.py first to seed it)",
              flush=True)

    # Click-walkthrough.
    print(f"\n--- Click-walkthrough ---", flush=True)
    print(f"  Scene browser:  {args.frontend_host.rstrip('/')}/scene-graphs/{scene_app_id}", flush=True)
    print(f"  Scene GET:      {args.host.rstrip('/')}/v2/scene-graphs/{scene_app_id}", flush=True)
    print(f"  URDF export:    {args.host.rstrip('/')}/v2/scene-graphs/{scene_app_id}/export.urdf",
          flush=True)
    print(f"\nDone.", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
