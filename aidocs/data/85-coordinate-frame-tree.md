# aidocs/85 — Coordinate Frame Tree (CST1)

**Date:** 2026-05-20
**Status:** Design
**Audience:** Contributors; DLR manufacturing/robotics researchers; platform architects.
**Purpose:** Specify a first-class coordinate frame tree — a directed Neo4j graph of
coordinate frames with transformation edges — enabling multi-sensor calibration, point-cloud
alignment chains, digital-twin scene composition, and ROS TF2-compatible frame exports.

Couples to: `aidocs/78` (CAD annotator — URDF, CadReference),
`aidocs/81` (spatial data binding — DataBinding),
`aidocs/83` (point cloud — PC1b `[:ALIGNED_TO]`, SA1 ground truth, RDK1 RoboDK),
`aidocs/84` (live digital twin — DigitalTwinScene static transforms, JOINT_ANGLE kinematic chain).

---

### §1 Motivation: why the `[:ALIGNED_TO]` edge is not enough

aidocs/83 PC1b defines:

```cypher
(:PointCloudReference)-[:ALIGNED_TO { matrix:[16], rmse, alignedAt, algorithm }]->(:CadReference)
```

This works for a single point-cloud-to-CAD alignment. It breaks down as soon as there are
multiple sensors, multiple scans, or a need to express "where is scan B relative to scan A,
given that both were aligned to the CAD independently?"

**The missing primitive is a graph of frames** — not a graph of data nodes. Each `PointCloudReference`,
`CadReference`, or `UltrasonicScanReference` lives in some coordinate frame. Transforms between
frames are edges in that graph. Composing a chain of edges gives the full transform between any
two nodes in the tree, regardless of whether the data nodes are directly connected.

This is the same structure as:

| System | Frame graph name | Transform edge |
|---|---|---|
| ROS TF2 | TF tree | static/dynamic transforms |
| NVIDIA Omniverse / USD | Xform hierarchy | UsdGeomXformable |
| OpenGL / Three.js | scene graph | `Matrix4.multiply` |
| URDF | link tree | `<joint>` elements |
| SLAM | pose graph | edges from ICP / loop closure |

shepard's `CoordinateFrame` tree is the data-management equivalent: a persistent, versioned,
time-aware graph of coordinate frames stored in Neo4j.

**What the tree enables that isolated transform edges do not:**

1. **Chaining** — express any scan in the world frame by multiplying edges along the path.
2. **Multi-sensor calibration** — store `camera_T_lidar`, `lidar_T_base`, `base_T_world`
   as separate edges; compose them without re-running alignment.
3. **Temporal transforms** — a moving workpiece has a different `part_T_world` at each timestep;
   store the history of transforms as multiple edges with `validFromMs` / `validUntilMs`.
4. **Ground-truth anchoring** — mark one frame as `frameType: "WORLD"` and all other frames
   express themselves relative to it; SA measurements become the authoritative world frame.
5. **ROS TF2 export** — the same graph structure, same matrix convention. A researcher
   who knows ROS can immediately understand the frame tree without learning new concepts.

---

### §2 Neo4j model

#### `(:CoordinateFrame)` node

```cypher
(:CoordinateFrame {
  appId:       String          -- unique /v2/ appId
  name:        String          -- short label: "world" | "part_nominal" | "scan_head" | "robot_base"
  description: String          -- optional prose
  frameType:   String          -- see table below
  createdBy:   String          -- username
  createdAtMs: long            -- epoch millis
  isRoot:      boolean         -- true for the world frame (no parent edge)
})
```

| `frameType` | Meaning |
|---|---|
| `WORLD` | Global reference frame. At most one per `DigitalTwinScene`. Typically SA ground-truth or cell-floor anchor. |
| `FIXED` | Static frame rigidly attached to a world-space position (fixture, table corner). |
| `ROBOT_LINK` | A URDF link frame; its transform to parent is driven by joint angles (time-varying). |
| `SENSOR` | A sensor's native frame (scanner head, camera, IMU). |
| `PART` | A part's reference frame (CAD nominal or measured). |
| `TOOL` | A tool-centre-point (TCP) frame; child of an end-effector `ROBOT_LINK`. |
| `CUSTOM` | Any other user-defined frame. |

#### `[:CHILD_OF]` edge

Direction: from the **child frame** to the **parent frame**.
Matrix convention: **parent_T_child** — a point expressed in the child frame is transformed to
the parent frame by `p_parent = matrix * p_child`. Row-major, homogeneous, identical to
`THREE.Matrix4` and NumPy/Open3D conventions.

```cypher
(:CoordinateFrame)-[:CHILD_OF {
  matrix:       [double × 16],   -- parent_T_child, row-major homogeneous 4×4
  rmse:         double,           -- optional: RMS residual (mm) from alignment
  method:       String,           -- "STATIC" | "ICP" | "RANSAC" | "SA_TRACKER" | "MANUAL" | "URDF_JOINT"
  validFromMs:  long,             -- null = from the beginning of time
  validUntilMs: long,             -- null = open-ended (current best estimate)
  source:       String,           -- appId of the PointCloudReference ICP, SA session, etc.
  converged:    Boolean,          -- ICP / RANSAC: did the algorithm converge?
  createdBy:    String,
  createdAtMs:  long
}]->(:CoordinateFrame)
```

Multiple `[:CHILD_OF]` edges from the same child to the same parent with non-overlapping
`validFromMs`/`validUntilMs` form a **temporal transform history** — the frame moved over time.
Edges with `validFromMs = null AND validUntilMs = null` are interpreted as "always valid" (STATIC).

#### Association edges (data nodes → frame nodes)

Data nodes declare which frame they live in via a lightweight `[:IN_FRAME]` edge:

```cypher
(:PointCloudReference)-[:IN_FRAME]->(:CoordinateFrame)
(:CadReference)-[:IN_FRAME]->(:CoordinateFrame)
(:UltrasonicScanReference)-[:IN_FRAME]->(:CoordinateFrame)
(:GeometryAnnotation)-[:IN_FRAME]->(:CoordinateFrame)   -- optional: for annotations not embedded in a URDF
```

When a `PointCloudReference` and a `CadReference` point to frames A and B respectively, and
A is an ancestor of B (or vice versa) in the frame tree, the viewer can compose the chain
automatically to co-render them without an explicit `[:ALIGNED_TO]` edge.

#### Relationship to `[:ALIGNED_TO]` (PC1b)

The existing `[:ALIGNED_TO]` edge (aidocs/83 PC1b) is kept as a **shortcut** for the common
single-alignment case. When PC1b creates an alignment, it additionally:

1. Creates a `CoordinateFrame` for the `PointCloudReference` if one does not exist (type `SENSOR`).
2. Uses the `CadReference`'s frame as the parent (creating it as type `PART` if needed).
3. Writes a `[:CHILD_OF {method:"ICP", matrix:..., rmse:..., ...}]` edge from the scan frame
   to the part frame.

This means `[:ALIGNED_TO]` is derived from the frame tree and can eventually be deprecated,
but it is not removed yet — existing queries and the viewer still use it as a fast lookup.

---

### §3 REST API

All paths under `/v2/coordinate-frames`. Authentication: standard Bearer token.

#### Frame CRUD

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/coordinate-frames` | List all frames (paginated; filter by `frameType`, `name`) |
| `POST` | `/v2/coordinate-frames` | Create a new frame |
| `GET` | `/v2/coordinate-frames/{appId}` | Get frame metadata |
| `PATCH` | `/v2/coordinate-frames/{appId}` | Update `name`, `description`, `frameType` |
| `DELETE` | `/v2/coordinate-frames/{appId}` | Delete frame (blocked if it has children) |

`POST /v2/coordinate-frames` body:

```json
{
  "name": "scan_head",
  "frameType": "SENSOR",
  "description": "GOM ATOS scan head, session 2026-05-20"
}
```

#### Transform management

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/coordinate-frames/{childAppId}/parent` | Set or replace the parent + transform |
| `DELETE` | `/v2/coordinate-frames/{childAppId}/parent` | Detach from parent (makes frame a root) |
| `GET` | `/v2/coordinate-frames/{childAppId}/transforms` | List all `[:CHILD_OF]` edges (history) |

`POST /v2/coordinate-frames/{childAppId}/parent` body:

```json
{
  "parentAppId": "part-frame-abc",
  "matrix": [1,0,0,0, 0,1,0,0, 0,0,1,0, 12.5,0.0,-3.2,1],
  "method": "ICP",
  "rmse": 0.23,
  "converged": true,
  "validFromMs": null,
  "validUntilMs": null,
  "source": "pc-ref-xyz"
}
```

#### Chain traversal

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/coordinate-frames/{fromAppId}/chain-to/{toAppId}` | Composed 4×4 matrix from `from` to `to` |
| `GET` | `/v2/coordinate-frames/{fromAppId}/chain-to/{toAppId}?t=<ms>` | Composed matrix at timestamp t (temporal) |
| `GET` | `/v2/coordinate-frames/{appId}/tree` | Full subtree (frame graph rooted at `appId`) |
| `GET` | `/v2/coordinate-frames/{appId}/ancestors` | Ordered path to root |

`GET /v2/coordinate-frames/{fromAppId}/chain-to/{toAppId}` response:

```json
{
  "fromAppId": "scan-frame-abc",
  "toAppId": "world-frame-xyz",
  "matrix": [/* 16 doubles — from_T_to */],
  "path": ["scan-frame-abc", "part-frame-def", "world-frame-xyz"],
  "rmseMax": 0.45,
  "allConverged": true,
  "queryTimeMs": 1726300800000
}
```

Server-side traversal: Cypher `shortestPath` along `[:CHILD_OF]` edges; matrix products computed
in Java (or delegated to a Python sidecar for batch transforms). For `?t=<ms>`, the traversal
selects the `[:CHILD_OF]` edge whose `validFromMs ≤ t < validUntilMs` (or the null-bounded
static edge when no temporal edge covers t).

#### Data-node frame association

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/pointcloud-references/{appId}/frame` | Set the frame for a PointCloudReference |
| `GET` | `/v2/pointcloud-references/{appId}/frame` | Get the frame for a PointCloudReference |
| `POST` | `/v2/cad-references/{appId}/frame` | Set the frame for a CadReference |
| `GET` | `/v2/cad-references/{appId}/frame` | Get the frame for a CadReference |

---

### §4 Temporal transforms and moving frames

For frames that move over time (a workpiece being picked up, a scanner traversing a part,
a robot TCP at each waypoint), multiple `[:CHILD_OF]` edges cover non-overlapping time windows:

```cypher
-- Workpiece moved at 14:30
(:part_frame)-[:CHILD_OF { validFromMs: null, validUntilMs: 1716204600000, matrix: T1 }]->(:world_frame)
(:part_frame)-[:CHILD_OF { validFromMs: 1716204600000, validUntilMs: null, matrix: T2 }]->(:world_frame)
```

When a new static transform is set via `POST /v2/coordinate-frames/{childAppId}/parent`, the backend
automatically closes the current open-ended edge (sets its `validUntilMs = now`) before writing the
new edge. The full transform history is preserved — no transforms are ever deleted on update.

For **ROBOT_LINK** frames, transforms are driven by joint angles rather than static edges. These frames
do not have `[:CHILD_OF]` edges in Neo4j — their runtime transform comes from the URDF kinematic chain
evaluated at the joint angles stored in the `TimeseriesReference`. The `chain-to` endpoint handles this:
when the path passes through a `ROBOT_LINK` frame, it queries the joint-angle timeseries at `?t=<ms>`
and evaluates the forward-kinematics inline (delegating to the Open3D/PyKDL sidecar).

---

### §5 Scene graph composition: replacing `DigitalTwinScene` static transforms

aidocs/84 §3.1 defines `DigitalTwinScene.[:HAS_MEMBER { staticTransform:[16] }]`. That design
uses the matrix as a property of the membership edge — adequate for v1 but not extensible to
temporal transforms or chaining.

**CST1 extends this:** each `CadReference` member in a `DigitalTwinScene` is associated with a
`CoordinateFrame`. The scene graph is the subgraph of the frame tree containing those frames.
The `[:HAS_MEMBER { staticTransform }]` edge is deprecated in favour of:

```
CadReference -[:IN_FRAME]-> CoordinateFrame -[:CHILD_OF]-> ... -[:CHILD_OF]-> world_frame
```

The `DigitalTwinScene` entity gains a `worldFrameAppId` field pointing to its root `CoordinateFrame`.
The viewer calls `GET /v2/coordinate-frames/{worldFrameAppId}/tree` on scene load to reconstruct
the full scene graph in a single request.

**Migration:** `[:HAS_MEMBER { staticTransform }]` is the v1 path; CST1 is the v2 path.
Both are supported during a transition window. The admin endpoint
`POST /v2/digital-twin-scenes/{appId}/migrate-to-frame-tree` converts all existing
`staticTransform` edge properties into `CoordinateFrame` nodes + `[:CHILD_OF]` edges.

---

### §6 ROS TF2 compatibility

The frame tree is structurally identical to ROS TF2:

| ROS TF2 concept | CST1 equivalent |
|---|---|
| frame id (string) | `CoordinateFrame.name` |
| parent frame | the target of `[:CHILD_OF]` |
| `geometry_msgs/TransformStamped` | `[:CHILD_OF { matrix, validFromMs }]` |
| `/tf_static` topic | `[:CHILD_OF]` edges with `validFromMs = null` |
| `/tf` topic | `[:CHILD_OF]` edges with explicit `validFromMs` (time-series of transforms) |

Export endpoint:

```
GET /v2/coordinate-frames/export?format=rosbag2&fromMs=<t>&toMs=<t>
→ mcap file (ROS 2 bag, MCAP format) containing /tf + /tf_static topics
```

This lets a researcher open the exported bag in Foxglove Studio, RViz2, or any ROS 2 tool
and see the same frame tree that shepard has stored — bridging the shepard world with the
ROS ecosystem without requiring a live ROS bridge.

---

### §7 External tool integration: RViz2, Isaac Sim, RoboDK

#### RViz2 / Foxglove Studio

**RViz2** is a desktop Qt application — not embeddable in a browser. Researchers who
prefer it can use the ROS bag export (§6) to replay the frame tree + any associated
sensor data in RViz2 locally.

**Foxglove Studio** (https://foxglove.dev) is the browser-based equivalent: open-source,
handles URDF, point clouds, images, joint states, TF trees, and custom sensor panels.
It connects over WebSocket (Foxglove WebSocket protocol) or reads MCAP files directly.
The shepard live-state WebSocket (aidocs/84 §4.2) can be bridged to Foxglove's protocol
via a thin adapter, making Foxglove an alternative viewer for the digital twin that is:
- Zero-install (runs in the browser)
- Familiar to ROS users
- Embeddable as an `<iframe>` in the shepard UI (Foxglove supports CORS-free embedding)

Recommended phasing: ship the MCAP export first (one-shot, no live bridge needed), then
add the live Foxglove WebSocket adapter as an optional sidecar (`shepard-foxglove-bridge`).

#### NVIDIA Isaac Sim

Isaac Sim does not have a traditional embeddable web component. Its web integration
works as follows:

- **Omniverse Kit App Streaming (WebRTC):** Isaac Sim (running on an RTX GPU server) streams
  its rendered 3D viewport to a browser via WebRTC video. The browser receives a video feed;
  mouse/keyboard events are forwarded back over a WebRTC data channel. This is the same
  architecture as the `shepard-sidecar-renderer` (aidocs/84 §4.3) — functionally equivalent,
  but using NVIDIA's stack on NVIDIA hardware.

- **What this means for integration:** Isaac Sim cannot be embedded as a `<canvas>` inside the
  shepard UI. The integration model is **round-trip via USD export**:
  1. shepard exports the `DigitalTwinScene` as USD (USD Xform hierarchy, one prim per
     `CoordinateFrame`; mesh prims from the `CadReference` GLB/STEP files via USD conversion).
  2. Isaac Sim loads the USD stage; researchers can add physics simulation, robot controllers,
     sensor noise models, etc.
  3. Results (measured trajectories, sensor readings) are exported back to shepard via the
     sTC collector or the REST ingest API.

- **USD export endpoint (new):** `GET /v2/digital-twin-scenes/{appId}/export.usd`
  — generates a USD stage from the frame tree + CadReferences. This is a natural extension
  of the SDF export already designed in aidocs/78 §7.

- **Requirement:** RTX-class GPU server, full NVIDIA Omniverse stack (~20 GB). Not a default
  dependency. Gate behind `shepard.isaac-sim.enabled` feature toggle.

#### RoboDK

RoboDK integration is already fully designed in aidocs/83 §5 (RDK1). In CST1 terms:

- RoboDK robot cells are URDF bundles (`CadReference`, type `ROBOT_LINK` frames).
- RoboDK's calibrated TCP offset becomes a `[:CHILD_OF {method:"MANUAL"}]` edge from the
  tool0 frame to the robot base frame.
- The SA → RoboDK → shepard calibration chain (aidocs/83 RDK1 §3) maps to:
  `SA_ground_truth_frame → robot_base_frame → tool0_frame → part_frame` — a path in the
  CST1 frame tree.

---

### §8 Phasing

| ID | Scope | Gated on | Priority |
|---|---|---|---|
| CST1a | `CoordinateFrame` Neo4j entity + V54 migration; frame CRUD REST; `[:IN_FRAME]` edges for PointCloudReference + CadReference | none (standalone) | High — enables all chaining |
| CST1b | `chain-to` traversal endpoint; tree query endpoint; static matrix composition in Java | CST1a | High |
| CST1c | PC1b integration: ICP alignment writes `[:CHILD_OF]` edge in addition to `[:ALIGNED_TO]` | CST1a + PC1b | High |
| CST1d | Temporal transform history; `validFromMs`/`validUntilMs` on `[:CHILD_OF]`; `?t=<ms>` query param | CST1b | Medium — needed for dynamic scenes |
| CST1e | `DigitalTwinScene` migration from `staticTransform` edge props to frame tree | CST1a + DT1a | Medium |
| CST1f | MCAP/ROS bag export (`/export?format=rosbag2`) | CST1a | Medium — low effort, high value for ROS users |
| CST1g | USD export (`/export.usd`) for Isaac Sim | CST1e | Low — NVIDIA hardware dependency |
| CST1h | Foxglove WebSocket bridge sidecar | CST1b + DT1c | Low — optional viewer |
| CST1i | ROBOT_LINK FK evaluation in `chain-to` (sidecar delegation) | CST1b + SB1d | Low — only needed when path crosses dynamic joints |

---

### §9 V54 Neo4j migration

```cypher
-- V54__Add_CoordinateFrame_node.cypher
-- Idempotent: IF NOT EXISTS guards.
-- Operator action: none required.

CREATE CONSTRAINT CoordinateFrame_appId_unique IF NOT EXISTS
  FOR (n:CoordinateFrame)
  REQUIRE n.appId IS UNIQUE;

CREATE INDEX CoordinateFrame_name IF NOT EXISTS
  FOR (n:CoordinateFrame)
  ON (n.name);
```

---

### §10 See also

- `aidocs/78` — `CadReference`, URDF bundles, `GeometryAnnotation`
- `aidocs/81` — `DataBinding` model; SB1 display modes
- `aidocs/83` — PC1b `[:ALIGNED_TO]`, SA1 ground-truth anchoring, RDK1 RoboDK
- `aidocs/84` — `DigitalTwinScene`, `DigitalTwinSnapshot`, live state streaming
