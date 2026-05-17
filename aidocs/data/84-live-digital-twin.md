# aidocs/84 — Live Digital Twin: Moving Objects, Production Cell Scene, and State Streaming

**Date:** 2026-05-16
**Status:** Design
**Audience:** Contributors; DLR manufacturing/robotics researchers; platform architects.
**Purpose:** Specify the live digital twin extension — a real-time 3D scene where robot
joints move from live sensor feeds, `DataBinding` annotations track with their parent
URDF links, and the full scene state is delivered to viewers as a compact binary stream
(with an optional server-side-rendered video path for wall displays and VR).

Couples to: `aidocs/78` (CAD annotator — URDF scene graph, `CadReference`),
`aidocs/81` (spatial data binding — `DataBinding` model, SSE live mode),
`aidocs/83` (point cloud + overlay — `POSE_6D`, `JOINT_ANGLE`, live streaming),
`aidocs/53` (video plugin — live camera feed PiP), `aidocs/40 §3` (sTC collector).

---

### §1 What changes in the live digital twin vs the static viewer

The static viewer (aidocs/78–83) assumes geometry is fixed at upload time and
measurements are looked up against that fixed geometry. A live digital twin breaks
this assumption on two fronts:

**1. Geometry is dynamic.** A robot arm is not a static mesh — it is a kinematic chain
whose link positions are functions of the current joint angles. At each timestep the
whole robot's world-space geometry changes. Every `DataBinding` annotation that lives
in a robot link's local frame moves with it.

**2. The scene is composite.** A production cell contains multiple robots, static fixtures,
moving conveyors, workpieces being manipulated, human operator safety zones. No single
URDF file captures all of this. The digital twin scene graph is a composition of multiple
`CadReference` (URDF bundles and static meshes) with declared transforms between them.

**3. State is high-rate and continuous.** A KUKA robot at 50 Hz produces 6 joint angles
every 20 ms. A resistance welding cell records 8 thermocouple channels at 10 Hz and
current + voltage at 1 kHz. The viewer must consume this stream at source rate without
a request-response round-trip per sample.

**4. Latency matters.** A safety-zone overlay on a running robot cell must be < 100 ms
behind reality. A process monitoring dashboard for a 4-hour AFP run tolerates 1–2 s.
The same streaming infrastructure serves both, with different subscriber configurations.

---

### §2 Moving-frame DataBindings

#### §2.1 The problem

In the static model (aidocs/81 §2), a `DataBinding` is linked from a `GeometryAnnotation`
whose world-space position is fixed at annotation creation time. If the annotation lives
on a robot wrist link, the displayed badge/glyph/heatmap stays at the wrist's initial
position even as the robot moves — it drifts away from the actual sensor location.

#### §2.2 The fix: link-frame annotations

`GeometryAnnotation` (aidocs/78 §6) already stores a position in the CAD model's
coordinate frame. For URDF-backed `CadReference` nodes, every face index maps to a
specific URDF link. The extension is to make this link-frame binding explicit:

```cypher
(:GeometryAnnotation {
  ...existing fields...
  urdfLinkId:   String   -- e.g. "tool0", "wrist_3_link" — null for static geometry
  localPosX:    double   -- position in the named link's local frame
  localPosY:    double
  localPosZ:    double
})
```

When `urdfLinkId` is set, the viewer computes the annotation's world-space position at
each timestep by applying the forward kinematics chain:

```
world_T_link = FK(joint_angles_at_t, urdfLinkId)
world_pos    = world_T_link * [localPosX, localPosY, localPosZ, 1]
```

The `JOINT_ANGLE` DataBinding (SB1d) already drives the kinematic chain. The annotation
simply consumes the FK result to place itself. No new backend model is needed — this is
a viewer-side computation using the joint angles already in the live state stream.

#### §2.3 Sensor-on-moving-object pattern

The canonical ZLP pattern: a thermocouple is glued to the AFP tool head. The tool is
the `tool0` link of a KUKA KR 30. The thermocouple's `GeometryAnnotation` has
`urdfLinkId: "tool0"` and `localPos*` set to the thermocouple's position in tool space.
At each timestep:

1. The `JOINT_ANGLE` binding updates the robot's kinematic chain from the live joint
   angles.
2. The thermocouple's `BADGE` binding reads the temperature from the live timeseries.
3. The viewer composites: "temperature X at world position FK(tool0, joint_angles_t)".

The temperature label moves with the robot. When the robot is at the welding point,
the temperature badge is at the welding point. When the robot moves away, the badge
moves with it.

---

### §3 Production cell scene graph

#### §3.1 `DigitalTwinScene` entity

A production cell is modelled as a `DigitalTwinScene` — a composite scene with multiple
`CadReference` members and declared static transforms between them.

```cypher
(:DigitalTwinScene {
  appId:         String
  name:          String   -- "ZLP MFC Robot Cell A"
  frameId:       String   -- world frame name, e.g. "world" or "cell_base"
  liveEnabled:   boolean  -- whether live state streaming is active
  streamUrl:     String   -- WebSocket endpoint for live state
})
  -[:HAS_MEMBER {
    staticTransform: [double × 16],  -- 4×4 world_T_member at t=0 (null if URDF root)
    role: String                      -- "ROBOT" | "FIXTURE" | "WORKPIECE" | "SAFETY_ZONE"
  }]->
(:CadReference)   -- one edge per member; URDF bundles or static meshes
```

A `DataObject` can link to a `DigitalTwinScene` via `[:HAS_REFERENCE]` exactly like any
other Reference kind, making the scene a first-class provenance-tracked entity.

#### §3.2 Multi-robot composition

A typical ZLP MFC cell:

```
DigitalTwinScene "MFC Cell A"
  └─ CadReference (KUKA KR 30, URDF)       role=ROBOT       transform=T_robot1
  └─ CadReference (KUKA KR 16, URDF)       role=ROBOT       transform=T_robot2
  └─ CadReference (AFP tool head, URDF)    role=FIXTURE      transform=null (child of KR30 tool0)
  └─ CadReference (workpiece fixture, PLY) role=FIXTURE      transform=T_fixture
  └─ CadReference (cell enclosure, STEP)   role=FIXTURE      transform=T_enclosure
  └─ CadReference (safety zones, STEP)     role=SAFETY_ZONE  transform=T_zones
```

Each `CadReference` member has its own `GeometryAnnotation` entries. Annotations on
the KUKA members have `urdfLinkId` set; annotations on the workpiece fixture are static.

#### §3.3 Dynamic members (workpieces being manipulated)

When a CFRP panel is picked up by the robot and placed on the layup mold, the panel
itself moves. Model this as a `CadReference` member with `role: "WORKPIECE"` whose
`staticTransform` is updated as an event (not continuously) via:

```
PATCH /v2/digital-twin-scenes/{sceneAppId}/members/{cadRefAppId}
{ "staticTransform": [...] }
```

The viewer interpolates between the last two known transforms for smooth visual
transitions. The transform history is stored as `[:TRANSFORMED_AT {matrix, timestamp}]`
edges — a lightweight provenance trail of where the workpiece was at each step.

---

### §4 Live state streaming

#### §4.1 State packet format

The live state channel delivers compact binary packets over WebSocket. Each packet
covers one timestep and contains only *changed* values (delta encoding — analogous to
a P-frame in a video codec):

```
StatePacket {
  t:          uint64       -- Unix time in microseconds
  joints: [   -- one entry per robot member with changed joints
    { cadRefAppId: string, channels: { <channelName>: float32 } }
  ]
  sensors: [  -- one entry per DataBinding with a live value change
    { bindingAppId: string, value: float64, unit: string }
  ]
  poses: [    -- one entry per POSE_6D binding with a new measurement
    { bindingAppId: string, x,y,z: float32, qx,qy,qz,qw: float32 }
  ]
  events: [   -- discrete events (AE hit, process step, alarm)
    { kind: string, payload: bytes }
  ]
}
```

Encoding: **CBOR** (RFC 7049, compact binary JSON superset, well-supported in Java,
Python, JavaScript). A full joint packet for a 6-DOF robot is ~80 bytes; a typical
50 Hz cell (2 robots + 20 sensor channels) produces ~15 KB/s — easily within
WebSocket bandwidth.

I-frames (full state snapshots) are sent on connection and every 10 s thereafter,
so a late-joining viewer can reconstruct the full scene without replaying history.

#### §4.2 WebSocket endpoint

```
GET /v2/digital-twin-scenes/{sceneAppId}/live
Upgrade: websocket

→ server sends StatePackets at source rate
← client may send:
     { "subscribe": ["cadRefAppId1", "cadRefAppId2"] }   // filter to specific members
     { "scrubTo": <unix_us> }                             // switch to historical replay
```

The endpoint is served by a Quarkus `@ServerEndpoint` (`jakarta.websocket`). The
backend reads from:
- sTC (shepard-timeseries-collector) for sensor channels
- A direct robot API adapter (RoboDK, KUKA RSI, OPC/UA) for joint angles
- SA 6D tracking via the `SpatialAnalyzerTrigger` (IL1)

Backpressure: slow subscribers are queued up to 1 s of history; beyond that they
receive the next I-frame and drop intermediate packets. The subscriber is notified
with a `{ "gap": true, "droppedPackets": N }` message.

#### §4.3 Server-side rendering (video stream output)

For wall displays, VR headsets, and cases where browser WebGL is not available, a
`shepard-sidecar-renderer` (headless Chromium with Three.js, or Babylon.js NativeEngine
on a GPU-equipped VM) reads the same WebSocket state stream and outputs a WebRTC video
stream. The browser receives an `<video>` element instead of a `<canvas>`:

```
                    ┌─────────────────────────────────────┐
sTC / robot API ──→ │  LiveTwinSession (Quarkus WebSocket) │ ──→ WS state stream
                    └─────────────────────────────────────┘
                             │                    │
                    browser (Three.js)    sidecar-renderer
                    renders client-side   (headless Chromium)
                                                  │
                                           WebRTC video stream
                                                  │
                                   wall display / VR headset / low-power device
```

The sidecar is optional (`shepard.renderer.enabled=false` default). It adds a GPU
dependency and a Docker service (`ghcr.io/dlr-shepard/shepard-renderer`). When
disabled, wall displays use the browser path (any PC in the cell can run a browser).

**This is the "state supplied as a video stream" architecture:** the video IS the
rendered live 3D scene. The sidecar's job is to turn the same state packets the browser
renders client-side into a compressed video feed at 30–60 fps, < 50 ms glass-to-glass
latency over a local network (using WebRTC's built-in H.264/VP9 encoder).

**Interactive video mode:** WebRTC natively supports bidirectional data channels alongside
the video track. The sidecar receives mouse, touch, and keyboard events from the client
over a WebRTC data channel and responds by updating the camera position or viewpoint in
the headless renderer. The updated view is immediately encoded and pushed as the next
video frame. This is the CloudXR / NVIDIA Omniverse architecture: the video stream is
interactive even though the client renders nothing locally. A researcher on a thin client
or a VR headset navigates the full 3D scene as if running Three.js locally — the server
does all the heavy lifting. The round-trip cost (event → render → encode → decode) adds
~20–40 ms on a local network, imperceptible for camera navigation.

Data channel message format (thin, client → sidecar):
```
{ "type": "MOUSE_MOVE", "dx": float, "dy": float }
{ "type": "ORBIT",      "dTheta": float, "dPhi": float }
{ "type": "ZOOM",       "delta": float }
{ "type": "CLICK",      "screenX": int, "screenY": int }   → sidecar raycasts, returns picked annotation
{ "type": "SCRUB",      "t": uint64 }                       → switches to historical replay at t
```

The `CLICK` + raycast path means clicking on the video frame still selects annotations
and opens their data panels — the interactive video is functionally equivalent to the
local browser renderer for all standard interactions.

#### §4.4 Historical replay

When the viewer switches from live to historical mode (`scrubTo` message), the backend
serves the same state packet format from the stored timeseries + structured-data
References (no SSE replay needed — the WebSocket session mode changes). The scrubber
position drives the backend query:

```
GET /v2/digital-twin-scenes/{sceneAppId}/state-at?t=<unix_us>
→ { StatePacket full I-frame at timestamp t }
```

This means the same viewer code handles live and historical replay: it consumes
StatePackets in both cases. The only difference is whether packets arrive at source
rate or at scrubber-controlled rate.

---

### §5 `:LiveTwinConfig` admin entity

Follows the `:*Config` admin pattern (CLAUDE.md):

```cypher
(:LiveTwinConfig {
  appId:              "live-twin-config",
  enabled:            false,
  maxConcurrentSessions: 50,
  iFrameIntervalSec:  10,
  maxQueueDepthMs:    1000,
  rendererEnabled:    false,
  rendererUrl:        null,        -- URL of shepard-sidecar-renderer
  defaultSampleRateHz: 10,         -- default subscriber rate; source may be faster
  allowedSourceKinds: ["STC", "OPC_UA", "KUKA_RSI", "ROBODK", "SA_6D"]
})
```

Admin REST: `GET/PATCH /v2/admin/live-twin/config`. CLI: `shepard-admin live-twin
{status, enable, disable, set-renderer-url, ...}`.

---

### §6 Neo4j `LiveTwinSession` model (for audit and debugging)

```cypher
(:LiveTwinSession {
  appId:        String
  sceneAppId:   String
  startedAt:    ZonedDateTime
  endedAt:      ZonedDateTime   -- null while live
  subscriberIp: String          -- for audit
  mode:         String          -- "LIVE" | "REPLAY"
  packetssSent: long
  droppedPackets: long
})
```

Sessions are written by `ProvenanceCaptureFilter` (PROV1a) — an admin can see who
has been watching the digital twin and for how long.

---

### §7 Key interactions (end-to-end)

**Scenario: AFP robot run, live monitoring**

1. Operator opens the ZLP MFC Cell A `DigitalTwinScene` in the shepard viewer.
2. Browser connects to the WebSocket endpoint; receives an I-frame with the current
   robot pose and all sensor values.
3. The KUKA KR 30 starts its AFP program. sTC (connected to the KUKA RSI interface)
   pushes joint angle packets at 50 Hz.
4. The viewer animates the Three.js URDF at 50 Hz (JOINT_ANGLE). The AFP tool head
   moves. The thermocouple temperature badge (`BADGE` DataBinding, `urdfLinkId: "tool0"`)
   moves with the tool head — temperature shown at the exact layup position.
5. The SA T-Scan was run before the layup to anchor the part in the cell frame.
   The deviation map (PC1e surface matching) is already displayed on the workpiece —
   green where within tolerance, red where not. This static overlay stays visible
   throughout the run.
6. At layup completion, the operator switches the viewer to historical replay and
   scrubs to the moment of maximum tool temperature to investigate a hot spot.
7. The `shepard-sidecar-renderer` is active on the control room wall display —
   the same scene at 30 fps video, visible from across the room without a browser.

**Scenario: Robot calibration with SA 6D tracking**

1. Robot runs a calibration path (sequence of target poses).
2. SA T-Scan + 6DOF probe records TCP pose at each target.
3. The POSE_6D DataBinding (SB2h) shows the SA-measured TCP as an animated red triad.
4. The JOINT_ANGLE DataBinding shows the robot's kinematic prediction as a blue triad.
5. The gap between red and blue at each target is the calibration error — visible
   directly in the viewer, no spreadsheet needed.

---

---

### §7 Digital twin snapshots (screenshots)

A **digital twin snapshot** captures the rendered state at a specific moment — both the
visual (a PNG of the 3D view with all data overlays) and the state (the StatePacket at
that timestamp). Snapshots are useful for:

- **Reports**: "this is what the cell looked like at the moment of the temperature spike"
- **Alarms**: auto-snapshot when a threshold is crossed (DataBinding `defectThreshold`)
- **Cycle records**: one snapshot per completed AFP ply, automatically
- **Documentation**: annotated "here is where the deviation was found"

#### Snapshot model

```cypher
(:DigitalTwinSnapshot {
  appId:         String
  sceneAppId:    String           -- parent DigitalTwinScene
  capturedAt:    ZonedDateTime
  trigger:       String           -- "MANUAL" | "ALARM" | "CYCLE_END" | "SCHEDULED"
  imageRefAppId: String           -- FileReference containing the PNG
  statePacketT:  uint64           -- Unix µs — pointer into timeseries for full state replay
  viewConfig:    String           -- JSON: camera position, FOV, active overlays
})
```

The `imageRefAppId` is a regular `FileReference` (PNG, stored on S3 via the FS1 SPI) —
snapshotting reuses the existing file upload pipeline. The `statePacketT` timestamp is
the state-at pointer; any viewer can replay the exact moment by sending
`scrubTo: statePacketT` to the WebSocket endpoint.

#### REST surface

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/digital-twin-scenes/{appId}/snapshots` | Manual snapshot — accepts `viewConfig` override |
| `GET` | `/v2/digital-twin-scenes/{appId}/snapshots` | List snapshots (paginated, filterable by trigger/time) |
| `GET` | `/v2/digital-twin-scenes/{appId}/snapshots/{snapAppId}` | Get snapshot metadata |
| `GET` | `/v2/digital-twin-scenes/{appId}/snapshots/{snapAppId}/image` | Download PNG (proxies FileReference) |
| `DELETE` | `/v2/digital-twin-scenes/{appId}/snapshots/{snapAppId}` | Delete snapshot + FileReference |

#### Client-side capture vs server-side capture

When the viewer is running in the browser, `POST .../snapshots` triggers the browser to
call `canvas.toBlob()` on the Three.js renderer, upload the blob as a `FileReference`,
and then POST the snapshot metadata. This is the fast path — no server-side rendering
needed.

When the sidecar renderer is active (DT1f), the server captures a frame directly from
the WebRTC encoder and uploads it — no browser round-trip. This is required for
automated alarm snapshots where no browser may be connected.

#### Alarm-triggered snapshots

DataBinding PATCH adds an optional `alarmSnapshot: boolean` field. When the live value
crosses `defectThreshold` (SB2g) or exceeds `maxValue` (any HEATMAP binding), the
backend fires `POST .../snapshots` with `trigger: "ALARM"` automatically (if the
sidecar renderer is available; otherwise the event is logged and a UI notification
prompts the researcher to snapshot manually).

---

### §8 Phasing

| ID | Scope | Gated on | Priority |
|---|---|---|---|
| DT1a | `DigitalTwinScene` entity + admin REST + viewer composite scene (multi-URDF, static transforms) | CAD1b (URDF + annotation model) | High — enables multi-robot cell |
| DT1b | Moving-frame `GeometryAnnotation` (`urdfLinkId` + `localPos*` fields); FK-driven annotation position in viewer | JOINT_ANGLE (SB1d) + DT1a | High — annotations move with robot |
| DT1c | WebSocket live state stream (`/v2/digital-twin-scenes/{appId}/live`); CBOR StatePacket; I-frame every 10 s | sTC + DT1a | High — the core live stream |
| DT1d | Multi-source fan-in (sTC + OPC/UA + KUKA RSI + RoboDK) feeding the WebSocket | DT1c | Medium — OPC/UA adapter is the next source after sTC |
| DT1e | Historical replay via `scrubTo` WebSocket message + `/v2/digital-twin-scenes/{appId}/state-at` endpoint | DT1c + SB1 timeseries stored | Medium |
| DT1f | `shepard-sidecar-renderer` optional Docker service; WebRTC video output | DT1c | Low — wall display / VR use case; GPU dependency |
| DT1g | Dynamic workpiece member transform updates + `[:TRANSFORMED_AT]` history | DT1a | Low — needed for pick-and-place workflows |

---

### §9 See also

- `aidocs/78` — `CadReference`, URDF bundles, `GeometryAnnotation` face-index encoding
- `aidocs/81` — `DataBinding` model, display modes, SSE live mode (DT1c supersedes SSE for scene-level streaming)
- `aidocs/83` — `JOINT_ANGLE`, `POSE_6D`, point cloud, surface-annotation matching
- `aidocs/53` — Video plugin; `CAMERA_FRUSTUM + PiP` (SB2a) continues to use VID1 for camera feeds
- `aidocs/50` — Experiment orchestration; the `Coordinator` service is the natural source of `events[]` in StatePackets

### §10 Prior art / UI inspiration

**ROSbag viewers** are the closest existing design reference for the digital twin UI:

- **[Foxglove Studio](https://foxglove.dev)** — open-source multi-panel layout, synchronized seek bar across all panels, camera/image panels, 2D/3D plot panels, topic browser, message inspector. Panel layout is drag-and-drop and persisted as a JSON layout file. The "subscribe to topic, render at rate" mental model maps directly to the DT1 `StatePacket` stream + panel system.
- **[PlotJuggler](https://github.com/facontidavide/PlotJuggler)** — time-synchronized multi-channel time-series plotting; the "drag a topic onto a plot" UX is a strong inspiration for the DT1c panel → `DataBinding` wiring flow.
- **[ROSboard](https://github.com/dheera/rosboard)** — web-based, zero-install ROS topic viewer; demonstrates that a WebSocket + React approach can handle sensor rates (IMU at 100 Hz, camera at 30 Hz) without dropped frames on a typical laptop browser tab.

Key UX patterns to adopt from these tools:
- **Global seek bar** with live/historical toggle — clicking past data enters scrub mode; clicking "live" re-subscribes.
- **Panel chooser palette** — "Add panel → Plot / Camera / 3D / State table / Log" matching the DT1 panel types.
- **Topic / channel sidebar** — lists all active `DataBinding` channels with live value preview; drag onto a panel to add a trace.
- **Playback speed controls** (0.25×, 0.5×, 1×, 2×, 4×) for historical scrub mode.
- **Layout persistence** — save the current panel arrangement as a named `DashboardReference` (AI1e shape) so the researcher reopens the same view next session.
