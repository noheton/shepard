---
layout: default
title: Scene graph (reference)
permalink: /reference/scene-graph/
audience: user
---

# Scene graph

A `DigitalTwinScene` is a kinematic-tree composite that groups one
root `CoordinateFrame` plus its descendants and the `Joint`s that
connect them. Scenes are produced by RDK / URDF parsers, by hand-build
via the REST surface, or by an AI agent driving the MCP tools.

The substrate ships in
[DT1-PHASE-0]({{ '/' | absolute_url }}#dt1-phase-0); the REST + MCP +
URDF surface ships in **SCENEGRAPH-REST-1**.

## Endpoints

All endpoints are under `/v2/scene-graphs` (the fork's development
shelf; `/shepard/api/…` v1 is **not** touched).

| Method + path | Description |
|---|---|
| `GET /v2/scene-graphs/{appId}` | Full scene tree (frames + joints). `Accept: application/ld+json` for JSON-LD framing. |
| `GET /v2/scene-graphs/{appId}/export.urdf` | URDF XML export (`application/xml`). |
| `GET /v2/scene-graphs/{appId}/export.usd` | **503** placeholder; queued under `ISAAC-USD-EXPORT-1`. |
| `POST /v2/scene-graphs` | Create empty scene; mints `appId`. |
| `POST /v2/scene-graphs/{appId}/frames` | Add a frame. First frame becomes root. |
| `PATCH /v2/scene-graphs/{appId}/frames/{frameAppId}` | Patch single-frame fields. Empty-string `parentFrameAppId` clears parent. |
| `DELETE /v2/scene-graphs/{appId}/frames/{frameAppId}` | Hard-delete frame + descendants via `:HAS_PARENT_FRAME*`. |
| `POST /v2/scene-graphs/{appId}/joints` | Register a joint between two existing frames. |
| `DELETE /v2/scene-graphs/{appId}/joints/{jointAppId}` | Delete a joint. |

Auth: `@Authenticated` only (any authenticated user). Per-scene
permission walks are queued as `SCENEGRAPH-PERMS-1`.

### URDF export — what's emitted

- `<robot name="…">` shell, name = scene's `name` (or
  `scene_<appId>` if unnamed).
- One `<link name="…"/>` per `CoordinateFrame`.
- One `<joint name="…" type="…">` per `Joint` with `<parent
  link="…"/>`, `<child link="…"/>`, `<origin xyz="…" rpy="…"/>`
  (from the child frame's local transform), `<axis xyz="…"/>`, and
  `<limit lower="…" upper="…"/>` for `REVOLUTE` / `PRISMATIC`.

### URDF export — what's NOT emitted

- `<visual>` / `<collision>` blocks — meshes live on `FileReference`
  payloads on the source DataObject; resolution is the client's
  responsibility. URDF-WEBVIEW-1 phase 2 will hand-stitch when
  consuming the export.
- `<inertial>` — mass / inertia tensors are not on the scaffold; CAD1b
  / SB1d add that surface later.
- `<transmission>` — ROS-control transmissions out of scope.

## Provenance

Every mutation records a typed `:Activity` via
`ProvenanceService.record()`:

- `actionKind` ∈ `CREATE` / `UPDATE` / `DELETE`.
- `targetKind = "DigitalTwinScene"`, `targetAppId` = scene appId.
- `sourceMode` from the `X-AI-Agent` header — `"ai"` when present
  with `agentId` set to the header value; `"human"` otherwise.
  PROV1j-compliant; closes the EU AI Act Art. 50 disclosure at the
  audit-log layer when an AI drives the call.
- PROV-O edges: `(:Activity)-[:WAS_ASSOCIATED_WITH]->(:User)`,
  `(:Activity)-[:GENERATED|USED]->(:BasicEntity)` (via
  `ActivityDAO.wireEdges()`).
- **Supplementary** `(:Activity)-[:WAS_DERIVED_FROM]->(:Activity)`
  edge linking the new activity to the most-recent prior activity
  for the same scene appId — gives the audit chain a graph-walk
  rather than just a time-ordered scroll.

The skip-capture handoff is set on the request context so the
`ProvenanceCaptureFilter` does not emit a duplicate generic Activity.

## MCP tools

Available at the native Quarkus MCP endpoint (`/v2/mcp/sse`). All
tools route through the same `SceneGraphService`, so `:Activity` +
`:WAS_DERIVED_FROM` writes happen identically to the REST path.

| Tool name | Purpose |
|---|---|
| `scene_graph_get` | Load scene by appId — full frame tree + joints. |
| `scene_graph_create` | Create an empty scene. |
| `scene_graph_add_frame` | Add a frame; first frame becomes root. |
| `scene_graph_patch_frame` | Mutate frame transform / parent / kind / name. |
| `scene_graph_delete_frame` | Hard-delete frame subtree. |
| `scene_graph_register_joint` | Register a joint between two frames. |
| `scene_graph_delete_joint` | Delete a joint. |
| `scene_graph_export_urdf` | Export scene as URDF XML string. |

## Worked example

Create an empty scene, add a base + tool frame, register a fixed
joint, and export URDF:

```bash
APIBASE=https://shepard-api.example.org
TOKEN="<JWT or X-API-KEY>"

# 1. Create the scene.
SCENE=$(curl -fsS -X POST "$APIBASE/v2/scene-graphs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"two-frame","description":"smoke-test"}' | jq -r .appId)

# 2. Add the root frame.
ROOT=$(curl -fsS -X POST "$APIBASE/v2/scene-graphs/$SCENE/frames" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"base_link","kind":"BASE"}' | jq -r .appId)

# 3. Add the tool frame.
TOOL=$(curl -fsS -X POST "$APIBASE/v2/scene-graphs/$SCENE/frames" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"tool0\",\"parentFrameAppId\":\"$ROOT\",\"z\":0.5,\"kind\":\"TCP\"}" | jq -r .appId)

# 4. Register a fixed joint.
curl -fsS -X POST "$APIBASE/v2/scene-graphs/$SCENE/joints" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"tool_mount\",\"parentFrameAppId\":\"$ROOT\",\"childFrameAppId\":\"$TOOL\",\"type\":\"FIXED\"}" >/dev/null

# 5. Export URDF.
curl -fsS "$APIBASE/v2/scene-graphs/$SCENE/export.urdf" \
  -H "Authorization: Bearer $TOKEN"
```

## Audit-trail walk

Find every mutation on a scene plus the user who made it, in order:

```cypher
MATCH (a:Activity {targetAppId: $sceneAppId})-[:WAS_ASSOCIATED_WITH]->(u:User)
RETURN u.username, a.actionKind, a.sourceMode, a.agentId,
       a.summary, a.startedAtMillis
ORDER BY a.startedAtMillis ASC;
```

Walk only the derivation chain — useful when the activity feed is
busy and you only want the linked edits to one scene:

```cypher
MATCH (latest:Activity {targetAppId: $sceneAppId})
WITH latest ORDER BY latest.startedAtMillis DESC LIMIT 1
MATCH path = (latest)-[:WAS_DERIVED_FROM*0..]->(prior:Activity)
RETURN prior.appId, prior.actionKind, prior.summary, prior.startedAtMillis;
```

## Cross-references

- Substrate scaffold: [DT1-PHASE-0]({{ '/' | absolute_url }}aidocs/16-dispatcher-backlog.md)
- Renderer (URDF browser): URDF-WEBVIEW-1
- Producer (RDK parser): RDK-PARSE-2
- Permission anchor (queued): SCENEGRAPH-PERMS-1
- Graph browser UI (queued): SCENEGRAPH-REST-1-UI
- Design notes: `aidocs/data/85-coordinate-frame-tree.md`
