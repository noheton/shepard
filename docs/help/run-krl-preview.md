---
title: Interpret a KRL program as a joint trajectory
description: Click a .src file, pick a URDF, and produce a joint trajectory you can replay in the 3D viewer.
permalink: /help/run-krl-preview/
layout: default
audience: user
---
# Interpret a KRL program as a joint trajectory

Shepard can resolve a KUKA Robot Language (`.src`/`.krl`) program against a URDF
and produce a joint trajectory you can replay in the 3D viewer — no
RoboDK / Windows VM round-trip required.

The whole flow is two clicks once the inputs are in your collection.

## What you need before you start

- A `.src`/`.krl` FileReference attached to a DataObject (the KRL program).
- A `.urdf` FileReference in the same DataObject (the kinematic model).
- A TimeseriesContainer to receive the resulting trajectory.
- Optional: a `.dat` companion (KRL frame literals + pose constants).

## How to run it

1. Open the **FileReference detail page** for the `.src`/`.krl` program.
   Shepard shows a primary **Interpret as joint trajectory** button at the top
   of the page.
2. Click **Interpret as joint trajectory**. The dialog opens.
3. Pick a URDF FileReference. If the DataObject has exactly one `.urdf`,
   it's preselected for you.
4. Confirm the **Target DataObject** (defaults to the parent of the `.src`)
   and pick a **TimeseriesContainer** to write the trajectory into.
5. *(Optional)* Add `.dat` companion files.
6. Click **Interpret**.
   - Behind the scenes, Shepard creates a **MAPPING_RECIPE** (the reusable
     "interpret this program against this robot" recipe) and materializes it.
   - On success, the dialog shows the produced **TimeseriesReference appId**
     and a link back to the DataObject where it now lives.

## What "MAPPING_RECIPE" means here

Interpreting a KRL program is one example of a *mapping recipe* — a reusable
template that binds existing data (your `.src` + URDF) and derives a new output
(the joint trajectory). The same generic mechanism powers the scene-graph 3D
view. You don't need to know any of this to use the button; it just means the
recipe is saved and re-runnable.

## When the sidecar isn't running

The KRL interpreter sidecar is operator-opt-in. If the sidecar hasn't been
brought up, the dialog shows a friendly error with the fix:

> Bring up the sidecar with
> `COMPOSE_PROFILES=krl-interpreter docker compose up -d`

Ask your operator to enable the `krl-interpreter` compose profile, or see
[the installation guide](https://github.com/dlr-shepard/shepard/blob/main/plugins/krl-interpreter/docs/install.md).

## A note on offline replay

The trajectory this produces is an **interpreter-resolved offline replay** of
the program — not the as-executed motion the KRC controller will run. For
last-millimetre fidelity you'd want a live cell capture; see the
[URDF + live-twin](https://github.com/dlr-shepard/shepard/blob/main/aidocs/data/84-live-digital-twin.md)
roadmap for that.

## What gets recorded

Every successful interpret writes a `:KrlTrajectoryActivity` PROV-O node to
Neo4j with edges to the `.src`/`.krl`, `.urdf`, `.dat`, and the produced
trajectory. The activity records the interpreter version, IK solver version +
stats, and warning + unsupported-construct counts — so months later, an EN 9100
audit can reproduce the trajectory and explain every divergence.

## Troubleshooting

- **"missing input"**: the URDF, target DataObject, and TimeseriesContainer
  appIds are all required.
- **403 / permission**: you need write access on the target DataObject's
  collection.
- **404 — no executor registered**: the `krl-interpreter` plugin / sidecar may
  not be installed on this instance. Ask your operator.
- **422 — sidecar unreachable / errored**: the sidecar isn't running, or hit a
  HARD-STOP construct (SPS, INTERRUPT, ANIN/ANOUT), or couldn't converge. See
  the [unsupported-constructs list](/reference/krl-interpreter/).
