---
stage: feature-defined
last-stage-change: 2026-05-23
---

# aidocs/86 — Scene Drive, Data Linking, and Replay (DR1 series)

**Date:** 2026-05-20
**Status:** Design
**Audience:** Contributors; DLR manufacturing/robotics researchers; platform architects.
**Purpose:** Specify the operational pipeline of the digital twin — streaming live ingest
data to Isaac Sim / Three.js, data linking (binding timeseries channels to robot joint
axes), a manipulatable scene graph with URDF export, and saving/sharing replay sessions.

Couples to: `aidocs/84` (DT1 — DigitalTwinScene, WebSocket StatePackets, sidecar renderer),
`aidocs/85` (CST1 — CoordinateFrame tree, chain-to traversal),
`aidocs/83` (PC1b — ICP alignment, RDK1 RoboDK, SA1 Spatial Analyzer),
`aidocs/81` (SB1 — DataBinding model, JOINT_ANGLE display mode).

---

### §1 The drive pipeline: ingest → scene

The core idea: a `TimeseriesReference` is the **animation axis** of a robot model. Time
is the index; joint angle channels are the axes. The scene scrubber moves along the time
axis and the robot moves with it. The same pipeline works for live ingest (the time axis
is "now") and historical replay (the time axis is a stored recording).

```
Real robot / sensor
  │  20 ms tick (50 Hz joint angles from KUKA RSI, OPC-UA, ROS 2 joint_states)
  ▼
sTC collector
  ├─► TimescaleDB write (persist)     ─── TimeseriesReference in shepard
  └─► FanOutBus (in-process, async)   ─── notify all active SceneSessions

FanOutBus
  ├─► ShepardNucleusSync              ─── omni.client.write_async(USD joint attr)
  │     └─► Nucleus → Isaac Sim       ─── physics + RTX render → WebRTC → browser
  └─► WsStateSession                  ─── StatePacket → Three.js URDF animation
```

The `FanOutBus` is a lightweight pub-sub inside the Quarkus backend — an in-memory
`@ApplicationScoped` dispatcher that sTC posts to on each batch write. Subscribers
(Nucleus sync, WebSocket state sessions) consume asynchronously. TimescaleDB gets the
durable write; downstream subscribers get the live fanout. Network drop of a subscriber
does not affect persistence.

**Why fan-out instead of polling Nucleus?**  
Polling would add 20–100 ms latency and a TimescaleDB read on every tick. The fan-out
path adds ≤1 ms — sTC's batch write triggers the bus directly, no additional DB query.
Latency target: robot joint moves → Isaac Sim renders updated frame → browser receives
WebRTC frame in **< 50 ms** on a local network.

---

### §2 Data linking model

"Data linking" is the act of declaring which timeseries channels drive which joints in
which URDF model. This is formalised as a `JOINT_ANGLE` `DataBinding` with an explicit
`channelMap`.

#### §2.1 `DataBinding` extension for joint drive

```cypher
(:DataBinding {
  displayMode:  "JOINT_ANGLE",
  channelMap:   String,   -- JSON object: { "ts_channel_name": "urdf_joint_name", ... }
  jointType:    String,   -- "REVOLUTE" | "PRISMATIC" | "CONTINUOUS" (default REVOLUTE)
  unitRad:      boolean,  -- true = channels are already radians; false = degrees → convert
  timeOffsetMs: long,     -- shift this binding's time axis by N ms (sensor latency correction)
})
  -[:BOUND_TO]-> (:TimeseriesReference)   -- the channel source
  -[:ON_SCENE]-> (:CadReference)          -- the URDF model being driven
```

`channelMap` example for a KUKA KR 30:

```json
{
  "A1": "joint_a1",
  "A2": "joint_a2",
  "A3": "joint_a3",
  "A4": "joint_a4",
  "A5": "joint_a5",
  "A6": "joint_a6"
}
```

The `CadReference` is a URDF bundle (CAD1a). The binding is the contract: "channel A1 in
this timeseries drives joint joint_a1 in this URDF." The renderer (Three.js URDFLoader or
Isaac Sim articulation controller) reads the binding and applies the joint values at the
current time.

#### §2.2 Data link creation API

```
POST /v2/cad-references/{cadRefAppId}/drive-links
{
  "timeseriesRefAppId": "ts-abc",
  "channelMap": { "A1": "joint_a1", "A2": "joint_a2", ... },
  "jointType": "REVOLUTE",
  "unitRad": false,
  "timeOffsetMs": 0
}
→ 201 { "bindingAppId": "binding-xyz" }
```

`GET /v2/cad-references/{cadRefAppId}/drive-links` lists all active `JOINT_ANGLE`
bindings for that URDF — multiple robots in a cell each have their own binding to their
own timeseries source.

#### §2.3 Multi-source data linking

A scene can mix data sources on a single time axis. The `FanOutBus` merges them:

| Source | Rate | Link type |
|---|---|---|
| Robot joint angles (KUKA RSI) | 50 Hz | `JOINT_ANGLE` binding |
| TCP force/torque (FTS sensor) | 1 kHz | `BADGE` or `GLYPH` binding |
| Thermocouple on tool head | 10 Hz | `BADGE` binding (`urdfLinkId: "tool0"`) |
| Part position (SA 6D tracking) | 5 Hz | `POSE_6D` binding |
| Process step events (PLC) | event-driven | `events[]` in StatePacket |

All channels share a common time base. The scrubber seeks all channels simultaneously;
each binding interpolates to the scrubber position at its own sample rate.

---

### §3 Manipulatable scene graph

The `DigitalTwinScene` (aidocs/84 §3) + CST1 `CoordinateFrame` tree together form the
scene graph. "Manipulatable" means researchers can modify it at runtime — add/remove
members, reposition them, change data links — without restarting anything.

#### §3.1 Scene graph operations

| Operation | API | Live effect |
|---|---|---|
| Add a robot model | `POST /v2/digital-twin-scenes/{id}/members` | SceneSession receives `member.added` SSE event → loads URDF |
| Reposition a member | `PATCH /v2/coordinate-frames/{frameId}/parent { matrix }` | SceneSession receives `frame.updated` → updates Group.matrix |
| Remove a member | `DELETE /v2/digital-twin-scenes/{id}/members/{cadRefAppId}` | SceneSession receives `member.removed` → disposes Three.js objects |
| Add a data link | `POST /v2/cad-references/{id}/drive-links` (§2.2) | FanOutBus adds subscriber → joint animation starts |
| Remove a data link | `DELETE /v2/data-bindings/{bindingAppId}` | FanOutBus removes subscriber → joint animation stops |
| Swap a robot model | DELETE member + POST member | Seamless — Three.js disposes old model, loads new GLB |

All mutations are captured by `ProvenanceCaptureFilter` (PROV1a) — who moved what and when
is in the audit trail.

#### §3.2 Scene graph manifest

`GET /v2/digital-twin-scenes/{appId}/manifest` returns the full scene as a single JSON
document: frame tree + mesh URLs + data link bindings + live stream endpoints. This is
the `SceneDescriptor` that both the Three.js and USD/Isaac Sim builders consume (aidocs/85 §the scene-build pipeline). Clients poll with `If-None-Match` / ETags for incremental updates;
the SSE event stream (§3.1) is the push notification that a new manifest is available.

---

### §4 URDF export from scene graph

The manipulatable scene graph is the **source of truth** for robot cell geometry. URDF
is one export format — the standard for ROS 2, RoboDK, Gazebo, MoveIt, and Isaac Sim
import. The scene graph round-trips: import URDF → edit in shepard → export URDF
(possibly modified).

#### §4.1 Export endpoint

```
GET /v2/digital-twin-scenes/{appId}/export.urdf
→ application/xml  (URDF file, self-contained with mesh file URIs)

GET /v2/digital-twin-scenes/{appId}/export.urdf.zip
→ application/zip  (URDF + all STL/GLB mesh files, importable into RoboDK directly)
```

Single-robot: `GET /v2/cad-references/{appId}/export.urdf` — exports just that URDF
bundle (round-trip for models that arrived as URDF uploads).

#### §4.2 Mapping: frame tree → URDF elements

| CST1 element | URDF element |
|---|---|
| `CoordinateFrame { frameType: "ROBOT_LINK", name: "shoulder" }` | `<link name="shoulder">` |
| `[:CHILD_OF { matrix, method: "URDF_JOINT" }]` edge | `<joint><origin rpy="..." xyz="..."/>` |
| `DataBinding { jointType: "REVOLUTE" }` on the edge's child frame | `<joint type="revolute"><axis xyz="0 0 1"/>` |
| `CadReference (GLB)` `[:IN_FRAME]` a link frame | `<visual><geometry><mesh filename="shoulder.stl"/></geometry></visual>` |
| `CoordinateFrame { frameType: "FIXED" }` | `<joint type="fixed">` |
| `CoordinateFrame { frameType: "WORLD", isRoot: true }` | `<link name="world"/>` + fixed joint to first robot base |

Joint limits and dynamics are stored as optional properties on the `[:CHILD_OF]` edge:

```cypher
[:CHILD_OF {
  ...existing fields...,
  jointLimitLower: double,    -- radians or metres
  jointLimitUpper: double,
  jointEffort:     double,    -- Nm or N
  jointVelocity:   double,    -- rad/s or m/s
  jointDamping:    double,
  jointFriction:   double,
}]
```

These fields are null for static (`FIXED`) edges. For URDF-origin `CadReferences`, the
importer populates them from the original `<joint>` element so they round-trip faithfully.

#### §4.3 Multi-robot URDF

A `DigitalTwinScene` with multiple robots exports as a multi-robot URDF: each robot is
a sub-tree rooted at its base frame, all joined to the world link with `type="fixed"`
at their `staticTransform` position. RoboDK and MoveIt 2 both handle multi-robot URDF.

---

### §5 ReplaySession: saving and sharing replays

A replay is a named, shareable, re-playable snapshot of a scene at a time range.

#### §5.1 `ReplaySession` entity

```cypher
(:ReplaySession {
  appId:          String
  name:           String           -- "AFP Run 2026-05-20 — hot spot investigation"
  sceneAppId:     String           -- DigitalTwinScene
  startMs:        long             -- start of the recording window (epoch ms)
  endMs:          long             -- end of the recording window
  speedFactor:    double           -- default playback speed (0.25, 0.5, 1.0, 2.0, 4.0)
  cameraTrack:    String           -- JSON: [{t, pos:[x,y,z], target:[x,y,z], fov}...]
                                   -- null = free-camera; defined = fixed cinematic track
  activeBindings: [String]         -- appIds of DataBindings active during this session
  notes:          String           -- researcher's markdown annotation
  createdBy:      String
  createdAt:      ZonedDateTime
  videoRefAppId:  String           -- null until rendered; appId of FileReference (MP4)
})
```

`activeBindings` records exactly which data links were live during the session — ensures
the replay plays back the same overlays the researcher was looking at, not whatever the
current scene configuration is.

#### §5.2 REST surface

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/digital-twin-scenes/{id}/replays` | Save a new replay (time range + camera track) |
| `GET` | `/v2/digital-twin-scenes/{id}/replays` | List replays (paginated) |
| `GET` | `/v2/replays/{appId}` | Get replay metadata + permalink |
| `PATCH` | `/v2/replays/{appId}` | Update name, notes, speedFactor |
| `DELETE` | `/v2/replays/{appId}` | Delete (does not delete underlying timeseries data) |
| `POST` | `/v2/replays/{appId}/render` | Submit to sidecar renderer → produces MP4 FileReference |
| `GET` | `/v2/replays/{appId}/manifest` | SceneDescriptor scoped to this replay's time range |

#### §5.3 Replay permalink and embedding

`GET /v2/replays/{appId}/manifest` returns a `SceneDescriptor` with the time range
baked in — the Three.js viewer opens at `startMs` and plays to `endMs`. The URL
`/view/replay/{appId}` (frontend route) is a shareable permalink that any authenticated
user can open to see exactly what the researcher saved.

Replay sessions are linked from `DataObject` via `[:HAS_REFERENCE]` — they are
first-class provenance-tracked entities. Embedding in a lab journal entry (J1, aidocs/37)
is a `[replay:{appId}]` shortcode that renders an inline playback card.

#### §5.4 Video render

`POST /v2/replays/{appId}/render` dispatches to the `shepard-sidecar-renderer` (aidocs/84
§4.3). The sidecar plays the replay at `speedFactor: 1.0` (real time), captures frames
at 30 fps, encodes MP4 (H.264, 1920×1080 or 4K), and uploads to S3 as a `FileReference`.
The `ReplaySession.videoRefAppId` is set when complete; a `NTF1a` notification
("Your render of AFP Run 2026-05-20 is ready") fires to the requesting researcher.

The video is shareable outside shepard — for reports, DLR internal presentations, or
embedding in a publication supplementary.

---

### §6 Full pipeline end-to-end

```
1. Researcher designs a robot cell in the scene graph editor (or imports URDF)
   └─ CadReferences (URDF bundles) → CoordinateFrames → DigitalTwinScene

2. Robot runs. sTC ingests joint angles → TimeseriesReference in TimescaleDB.
   FanOutBus fans out to:
     └─ ShepardNucleusSync → Isaac Sim → WebRTC → browser viewer
     └─ WsStateSession → Three.js viewer

3. Researcher opens the scene in the shepard viewer.
   SceneBuilder queries /manifest → loads URDF + pointclouds → attaches bindings.
   Joint angles from the FanOutBus drive the URDF joints in real time.

4. Interesting event occurs (thermal spike, deviation alarm).
   Researcher scrubs back 30 seconds to investigate:
     └─ scrubTo(t) → backend queries TimescaleDB → StatePacket I-frame → viewer rewinds

5. Researcher saves the 90-second window as a ReplaySession.
   Adds notes, marks the frame where the spike happened.

6. Researcher submits for video render.
   sidecar-renderer plays the replay → MP4 → FileReference → NTF1a notification.

7. Researcher exports the scene as URDF.
   Feeds into RoboDK to verify path is safe with the corrected calibration.
   RoboDK drives the model from the same TimeseriesReference (recorded joint angles).

8. Researcher publishes the DataObject with:
   - TimeseriesReference (raw joint angles)
   - ReplaySession (the 90-second window)
   - FileReference (MP4 render)
   - CadReference (the robot URDF)
   - PointCloudReference (part scan)
   - GeometryAnnotation (deviation region)
   All provenance-linked → one click to see the whole story.
```

---

### §7 Isaac Sim–specific: live ingest → drive path

When Isaac Sim is running (GPU station, CST1g + Nucleus), the `FanOutBus` subscriber
`ShepardNucleusSync` drives Isaac Sim joints directly:

```python
# ShepardNucleusSync (runs on the backend JVM as a MicroProfile reactive consumer)
# Pseudocode for the Python USD-write layer it delegates to:

async def on_joint_packet(packet: JointPacket):
    for joint_name, angle_rad in packet.joints.items():
        prim_path = f"/World/{packet.cadRefAppId}/joints/{joint_name}"
        await omni.client.write_async(
            stage_url=nucleus_stage_url,
            prim_path=prim_path,
            attr_name="drive:target:position",
            value=angle_rad,
            time=packet.t_us / 1e6    # USD timecode
        )
```

Isaac Sim reads these as articulation drive targets — PhysX drives the joints to the
target positions with the configured stiffness/damping. The result is physically
consistent: if the target is unreachable (joint limit, collision), Isaac Sim shows the
collision rather than silently clamping. This is the key advantage over pure kinematic
playback — you see physics violations in the recorded data.

For the **replay path**, the same USD write loop runs but reads from TimescaleDB instead
of the live FanOutBus, advancing the timecode at `speedFactor × real_time`:

```python
async def replay(session: ReplaySession):
    rows = await timescaledb.query(
        ts_ref_appid=session.timeRefAppId,
        start_ms=session.startMs, end_ms=session.endMs
    )
    for row in rows:
        await write_joints(row.joints, row.t)
        await asyncio.sleep((row.t - prev_t) / session.speedFactor)
```

---

### §8 Phasing

| ID | Scope | Gated on | Priority |
|---|---|---|---|
| DR1a | `FanOutBus` in-process pub-sub; sTC → bus → WsStateSession (Three.js live drive) | DT1c (WebSocket StatePacket) | High — live joint animation |
| DR1b | `JOINT_ANGLE` DataBinding `channelMap` field; drive-link CRUD API | SB1d (JOINT_ANGLE renderer) | High — makes data linking explicit |
| DR1c | `ReplaySession` entity + CRUD API + permalink frontend route | DR1a | Medium — save/share investigations |
| DR1d | Multi-source scrubber (seek all channels simultaneously to time t) | DR1a + DR1b | Medium — history replay |
| DR1e | URDF export (`/export.urdf` + `.urdf.zip`) with joint limits round-trip | CST1a (frame tree) | Medium — RoboDK + MoveIt integration |
| DR1f | `FanOutBus → ShepardNucleusSync` Isaac Sim live drive path | CST1g (USD export) + GPU station | Low — GPU hardware dependency |
| DR1g | Video render pipeline (`/replays/{id}/render` → sidecar → MP4 → NTF1a) | DR1c + DT1f (sidecar renderer) | Low — reporting use case |
| DR1h | Lab journal `[replay:{appId}]` shortcode embed | DR1c + J1 (lab journal) | Low — narrative integration |

---

### §9 See also

- `aidocs/84` — `DigitalTwinScene`, WebSocket StatePacket, sidecar renderer, historical replay
- `aidocs/85` — `CoordinateFrame` tree, URDF-joint edge properties, MCAP export
- `aidocs/83` — RDK1 RoboDK; SA1 Spatial Analyzer; PC1b ICP alignment
- `aidocs/81` — `DataBinding` model; `JOINT_ANGLE` display mode (SB1d); `POSE_6D` (SB2h)
- `aidocs/50` — Experiment orchestration; `Coordinator` as event source for `events[]` in StatePackets
