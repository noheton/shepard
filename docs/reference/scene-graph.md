---
layout: default
title: Scene graph (reference)
permalink: /reference/scene-graph/
---

# Scene graph

A **scene-graph** in shepard is a 3D, articulated view of a robot or
machine cell, driven by a URDF file. As of **V2CONV-B4** (2026-06-04)
a scene-graph is **not** a stored frames/joints graph in the database —
it is a `MAPPING_RECIPE` template that *binds* the data it needs and is
played back on demand by the Trace3D-family 3D renderer.

> **Migrating from the old `/v2/scene-graphs/*` surface?** The bespoke
> scene-graph subsystem (the stored `:DigitalTwinScene` graph + the
> `/v2/scene-graphs/*` CRUD/export endpoints + the `scene_graph_*` MCP
> tools) was removed and converged into the generic MAPPING_RECIPE
> mechanism. See [§Migration](#migration-from-v2scene-graphs) below.

## What a scene-graph binds

A scene-graph `MAPPING_RECIPE` template body binds, by `appId`:

| Field | Required | What it is |
|---|---|---|
| `mappingRecipeShape` | yes | `http://semantics.dlr.de/shepard/transform#SceneGraphPlayShape` — the dispatch key. |
| `urdfFileReferenceAppId` | yes | A singleton **FileReference** (FR1b) carrying the robot's URDF XML. The single source of truth for the kinematic tree; parsed on demand. |
| `jointTimeseriesReferenceAppId` | no | A **TimeseriesReference** whose channels carry the joint values over time. Omit for a static pose. |
| `jointChannelBindings` | no | A JSON array binding each URDF joint to a TimeseriesChannel selector, e.g. `[{"joint":"joint_1","channelSelector":"…"}]`. |

The URDF FileReference **is** the kinematic data — links, joints,
transforms, axes, and limits all come from parsing the URDF. shepard
never stores a duplicate frames/joints graph.

## Creating a scene-graph

### From the UI (recommended)

Open a URDF FileReference's detail page. If the file looks like a robot
file (`.urdf`/`.rdk`, or carries `urn:shepard:rdk:*` / `urn:shepard:urdf:*`
annotations), a **"Create 3D view from this URDF"** button appears.
Clicking it mints a `MAPPING_RECIPE` template targeting `SceneGraphPlayShape`
and routes to the play page. A subsequent visit reads **"Open in 3D view"**.

The UI never asks you for a path or URL — it always works from the
FileReference `appId`.

### From the API

```bash
# 1. Create the MAPPING_RECIPE template.
curl -X POST /v2/templates \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{
    "name": "KR210 cell — 3D view",
    "templateKind": "MAPPING_RECIPE",
    "body": "{\"mappingRecipeShape\":\"http://semantics.dlr.de/shepard/transform#SceneGraphPlayShape\",\"urdfFileReferenceAppId\":\"<urdf-fileref-appId>\"}"
  }'
# → 201 { "appId": "<template-appId>", ... }

# 2. Materialize it into the play envelope.
curl -X POST /v2/mappings/<template-appId>/materialize \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{ "inputReferenceAppIds": { "urdfFileAppId": "<urdf-fileref-appId>" } }'
```

The materialize response is a `VIEW` result whose `viewModel` is the
**play envelope**:

```json
{
  "outputKind": "VIEW",
  "executor": "SceneGraphPlayTransformExecutor",
  "viewModel": {
    "kind": "scene-graph-play",
    "renderer": "urdf",
    "robotName": "kr210",
    "urdfFileReferenceAppId": "<urdf-fileref-appId>",
    "rootLink": "base_link",
    "frames": [ { "name": "base_link", "parent": null }, { "name": "link_1", "parent": "base_link" } ],
    "joints": [ { "name": "joint_1", "type": "revolute", "parent": "base_link", "child": "link_1", "origin": [0,0,0.675], "rpy": [0,0,0], "axis": [0,0,1], "limitLower": -3.14, "limitUpper": 3.14 } ],
    "jointChannelBindings": [],
    "playbackStatus": "STATIC_POSE"
  }
}
```

`playbackStatus` is `STATIC_POSE` when no joint timeseries is bound, or
`DECLARED` when a joint TimeseriesReference is bound (live channel
resolution lands with VIS-S1; until then the renderer shows the static
pose).

## Endpoints

| Method + path | Description |
|---|---|
| `POST /v2/templates` | Create a `MAPPING_RECIPE` scene-graph template (body targets `SceneGraphPlayShape`). |
| `POST /v2/mappings/{templateAppId}/materialize` | Materialize the scene-graph into a play envelope (the 3D view-model). |
| `GET /v2/collections/{appId}/scene-graph` | Resolve a Collection's hero 3D view (a MAPPING_RECIPE template appId). |
| `PUT /v2/collections/{appId}/scene-graph` | Link / replace the Collection's hero view with a MAPPING_RECIPE template appId. |
| `DELETE /v2/collections/{appId}/scene-graph` | Unlink the Collection's hero view (does not delete the template). |

All endpoints are under `/v2/…` (the fork's development shelf;
`/shepard/api/…` v1 is **not** touched).

## Collection hero view

A Collection's detail page can render a hero 3D view. Link one with:

```bash
curl -X PUT /v2/collections/<collection-appId>/scene-graph \
  -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' \
  -d '{ "sceneGraphAppId": "<mapping-recipe-template-appId>" }'
```

The JSON field is still `sceneGraphAppId` (so existing callers keep
working), but its value is now a `MAPPING_RECIPE` template appId. The
`PUT` rejects a non-MAPPING_RECIPE target with `422`.

## Permissions

- Creating a template and materializing it follow the standard template
  + reference permission walks (you need read access on the bound URDF
  FileReference's parent Collection).
- The Collection hero-link `PUT`/`DELETE` require **Write** on the
  Collection; `GET` requires **Read**.

## Migration from `/v2/scene-graphs/*`

| Old (removed) | New |
|---|---|
| `GET /v2/scene-graphs/{appId}` | `POST /v2/mappings/{templateAppId}/materialize` (returns the play envelope). |
| `POST /v2/scene-graphs` + frame/joint CRUD | Author the URDF; bind it in a `MAPPING_RECIPE` template. Edit the URDF FileReference to change the kinematics. |
| `GET /v2/scene-graphs/{appId}/export.urdf` | The URDF FileReference content endpoint `GET /v2/files/{appId}/content` — the URDF *is* the source. |
| `POST /v2/scene-graphs/from-urdf/{fileRefAppId}` | "Create 3D view from this URDF" button, or `POST /v2/templates` with a `SceneGraphPlayShape` body. |
| `scene_graph_*` MCP tools | Generic MAPPING_RECIPE MCP tools (tracked as `MCP-MAPPING-RECIPE-1`). |
| `urn:shepard:scenegraph:scene-appId` back-annotation | `urn:shepard:mapping:scenegraph-template-appId`. |

**Operators:** migration `V111__B4_dissolve_scenegraph.cypher` deletes
the old `:DigitalTwinScene` / `:CoordinateFrame` / `:Joint` graph and
clears stale Collection hero links. The deleted scene **data is not
recoverable** — snapshot the Neo4j graph before upgrading if you must
retain hand-built scenes.

## See also

- The `vis-trace3d` plugin reference (`plugins/vis-trace3d/docs/reference.md`)
  for the `SceneGraphPlayShape` + `SceneGraphPlayTransformExecutor`.
- `aidocs/platform/191` §decision-2 — the convergence rationale.
