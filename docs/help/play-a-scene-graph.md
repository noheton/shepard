---
title: Play a robot scene graph in 3D
description: How to open the scene-graph 3D viewer and explore a robot's frames, joints, and live channel bindings
permalink: /help/play-a-scene-graph/
layout: default
audience: user
---
# Play a robot scene graph in 3D

The **scene-graph 3D viewer** renders a URDF robot model interactively in the
browser. You can orbit, zoom, and inspect the robot's frame tree, joint
definitions, and — when timeseries bindings are configured — see which
channels drive which joints during a test run.

---

## What you need

- A DataObject with a URDF [FileReference](/help/upload-data/) (a `.urdf` file
  stored as a singleton file reference).
- A **scene-graph play template** (type `MAPPING_RECIPE`) bound to that URDF
  reference. Templates are created in the Templates section; your administrator
  or a colleague typically sets these up once per robot type.

---

## Open the viewer

### From a FileReference detail page (recommended)

1. Open the DataObject that holds the URDF file reference.
2. In the **References** panel, click the URDF file reference row.
3. On the reference detail page, click **Open in scene-graph editor** (top
   right, `mdi-cube-scan` icon).

The viewer opens at `/scene-graphs/play/<templateAppId>`, pre-loaded with the
bound URDF and channel bindings from the template.

### From the top navigation

Choose **Tools → Scene-graph viewer** from the top bar. You will be prompted
to select a template; paste or pick the template's appId from the template
list page.

---

## Navigate the 3D view

| Interaction | Effect |
|-------------|--------|
| Left-drag | Orbit / rotate around the model |
| Right-drag or two-finger drag | Pan |
| Scroll wheel or pinch | Zoom in / out |
| Double-click a link | Centre on that link |

The header bar shows the robot's name and a **playback status** chip when the
template carries channel bindings (e.g. `BINDINGS_LOADED`, `NO_CHANNELS`).

The summary card at the bottom of the page reports:
- **Frames** — total coordinate frames in the URDF
- **Joints** — total joints (revolute, prismatic, fixed, …)
- **Joint bindings** — how many joints are wired to a timeseries channel

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| "This template did not materialize into a 3D view" | The template's executor is not `vis-trace3d`, or it returned a non-VIEW output kind | Check the template's `executor` field — it must be `vis-trace3d`. |
| "The play envelope is missing its URDF FileReference appId" | The template was created without binding a URDF file reference | Edit the template and set the `urdfFileReferenceAppId` binding. |
| "Failed to materialize the 3D view" | The URDF file cannot be fetched, or mesh resources (`.dae`, `.stl`) are missing from the file bundle | Ensure all mesh files referenced in the URDF are uploaded to the same DataObject or accessible via a co-located file bundle. |
| Blank canvas — no robot visible | URDF parses successfully but the robot has no visible geometry | Open the browser console for URDF loader warnings about missing mesh files. |

---

## Related help

- [Upload data](/help/upload-data/) — uploading the URDF file as a FileReference
- [Build a template](/help/build-a-template/) — creating a scene-graph play template
- [Run KRL preview](/help/run-krl-preview/) — kinematic recipe language for motion playback
