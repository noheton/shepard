---
title: Run / preview a KRL program
description: Click a .src file, pick a URDF, and produce a joint trajectory you can replay in the URDF viewer.
permalink: /help/run-krl-preview/
layout: default
audience: user
---
# Run / preview a KRL program

Shepard can resolve a KUKA Robot Language (`.src`) program against a URDF and
produce a 6-channel joint trajectory you can replay in the URDF viewer — no
RoboDK / Windows VM round-trip required.

The whole flow is two clicks once the inputs are in your collection.

## What you need before you start

- A `.src` FileReference attached to a DataObject (the KRL program).
- A `.urdf` FileReference in the same DataObject (the kinematic model).
- A TimeseriesContainer to receive the resulting trajectory (tier-2 will
  auto-mint this — until then, pick an existing one).
- Optional: a `.dat` companion (KRL frame literals + pose constants).

## How to run it

1. Open the **FileReference detail page** for the `.src` program.
   Shepard shows a primary **Run / preview** button at the top of the page.
2. Click **Run / preview**. The "Run / preview KRL program" dialog opens.
3. Pick a URDF FileReference. If the DataObject has exactly one `.urdf`,
   it's preselected for you.
4. Confirm the **Target DataObject** (defaults to the parent of the
   `.src`) and pick a **TimeseriesContainer** to write the trajectory into.
5. *(Optional)* Expand **Advanced** to override base / tool frames, set a
   seed pose, or tweak the IK tolerance + sampling rate.
6. Click **Resolve**.
   - On success, the result panel shows the produced `trajectoryAppId`
     plus the IK convergence stats and any warnings.
   - Click **Run preview** to open the URDF viewer with the URDF
     pre-loaded.

## When the sidecar isn't running

The KRL interpreter sidecar is operator-opt-in. If the sidecar hasn't been
brought up, the result panel shows a friendly hint:

> The KRL interpreter sidecar isn't reachable. Run
> `COMPOSE_PROFILES=krl-interpreter docker compose up -d` to bring it up
> (operator action).

Ask your operator to enable the `krl-interpreter` compose profile, or see
[the installation guide](https://github.com/dlr-shepard/shepard/blob/main/plugins/krl-interpreter/docs/install.md).

## A note on offline replay

The trajectory this produces is an **interpreter-resolved offline replay**
of the program — not the as-executed motion the KRC controller will run.
Per EN 9100 audit guidance, the result panel marks every trajectory with
this label. For last-millimetre fidelity you'd want a live cell capture;
see the [URDF + live-twin](https://github.com/dlr-shepard/shepard/blob/main/aidocs/data/84-live-digital-twin.md)
roadmap for that.

## What gets recorded

Every successful interpret writes a `:KrlInterpretActivity` PROV-O node
to Neo4j with PROV-O edges to the `.src`, `.urdf`, `.dat`, and the produced
trajectory. The activity records the interpreter version, IK solver
version + stats, and unsupported-construct list — so months later, an
EN 9100 audit can reproduce the trajectory and explain every divergence.

## Troubleshooting

- **HTTP 400 — missing field**: the URDF, target DataObject, and
  TimeseriesContainer appIds are all required.
- **HTTP 403**: you need write access on the target DataObject's
  collection.
- **HTTP 422**: the IK back-solver couldn't converge on more than 5% of
  poses. Try a different seed pose or check the URDF joint limits.
- **HTTP 501**: the `.src` uses a HARD-STOP construct (SPS, INTERRUPT,
  ANIN/ANOUT). These aren't supported in offline replay — see the
  [unsupported-constructs list in the reference page](/reference/krl-interpreter/).
- **HTTP 504**: interpretation exceeded the sidecar timeout. Raise
  `shepard.krl.sidecar.timeout-seconds` or split the program.
