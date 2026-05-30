# shepard-plugin-krl-interpreter — quickstart

**Audience.** A researcher who wants to run a KUKA `.src` program
against a URDF and see the resulting robot trajectory inside Shepard.

---

## What you need before starting

1. A DataObject in a Shepard Collection where you have **write** access.
2. A KUKA `.src` file uploaded as a `FileReference` in that DataObject.
3. A URDF file (`.urdf`) uploaded as a `FileReference` in the **same**
   DataObject (or another DataObject in the same Collection).
4. An existing `TimeseriesContainer` in that DataObject to receive the
   trajectory. (Tier-2 will auto-mint this — tracked as
   `KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER`. Until then, create
   one manually from the DataObject detail page.)

> **Operator prerequisite:** The KRL interpreter sidecar must be running.
> If your Shepard instance shows a 502 error when you click "Resolve",
> ask your operator to bring up the `krl-interpreter` compose profile.
> See [`install.md`](install.md) for details.

---

## Step 1 — Navigate to the `.src` FileReference

Open the DataObject detail page → expand the **Files** panel → click
the `.src` file's row to open its `FileReference` detail page.

## Step 2 — Click "Run / preview"

A **"Run / preview"** button (with a play icon) appears in the action
bar at the top of the `.src` FileReference page. It is only visible
for `.src` files. If you see it greyed out, you need write access on
the parent Collection.

Click it. The **Run / preview KRL program** dialog opens.

## Step 3 — Fill in the required fields

| Field | What to enter |
| ----- | ------------- |
| **URDF FileReference** | Pick the URDF file from the dropdown. If exactly one `.urdf` exists in the DataObject, it is pre-selected. |
| **Target DataObject appId** | Defaults to the same DataObject as the `.src`. Change this to attach the trajectory to a different DataObject. |
| **TimeseriesContainer appId** | Paste the `appId` of the container that will receive the trajectory data points. |

## Step 4 — (Optional) Advanced options

Expand the **Advanced** section to configure:
- `.dat` companion files (pre-selected when a same-stem `.dat` is found).
- Trajectory sample rate (`timeStep`, default 10 ms / 100 Hz).
- IK solver settings (`ikTolerance`, `maxIterations`).
- Base/tool frame overrides (toggle on to enter six-axis values in metres/radians).
- Seed pose for IK convergence (comma-separated joint angles in radians).

## Step 5 — Click "Resolve"

The dialog sends the request to `POST /v2/krl/interpret`. Large programs
(> 1000 poses) may take 10–60 seconds. A progress bar and a hint
("Still running… large programs can take 30 s or more") appear if the
call runs long.

## Step 6 — Review the result

On success, the result panel shows:

- A green **"Interpreter resolved offline replay"** chip.
- The `trajectoryAppId` of the new `TimeseriesReference`.
- The `activityAppId` of the `:KrlInterpretActivity` row
  (for audit drill-down).
- A **"Run preview"** button that opens the URDF viewer with the
  trajectory auto-bound to the URDF joints.
- A **"Back to DataObject"** link.
- Any parser warnings (degraded constructs, sparse frames, etc.).
- Any unsupported KRL constructs (e.g. `INTERRUPT`, `SPS`).
- IK convergence statistics (mean cycle time, max position residual,
  failed / total poses).

On **502 error**: the sidecar is not running. Contact your operator to
bring up the `krl-interpreter` compose profile.

On **501 error**: the `.src` file contains a hard-stop construct
(`SPS`, `INTERRUPT`, `ANIN`, or `ANOUT`) that the tier-1 interpreter
cannot process offline. Remove or comment out the construct and retry.

## What happens in the background

1. The backend resolves the `srcFileAppId` and `urdfFileAppId` to file
   payloads and forwards them to the KRL interpreter sidecar.
2. The sidecar parses the `.src`, back-solves IK against the URDF,
   and returns a joint-angle trajectory.
3. The backend writes the trajectory as a `TimeseriesReference` with
   channels `joint_0`, `joint_1`, … `joint_N`, each annotated with
   `urn:shepard:urdf:joint:joint_<n>`.
4. A `:KrlInterpretActivity` PROV-O node is written to Neo4j with USED
   edges to the source inputs and a GENERATED edge to the new reference.

The trajectory label "interpreter-resolved offline replay" is
intentional — it is **not** a fidelity replica of the KRC controller's
runtime motion. See `aidocs/integrations/117 §13.1` for the limitations.

---

## Parse a `.src` file in Python (developer shortcut)

If you want to inspect the IR directly without going through the UI:

```python
from krl_interpreter import parse

result = parse(open("Ply_5_layup.src").read(), filename="Ply_5_layup.src")
print(f"module={result.program.module_name}, statements={len(result.program.statements)}")
print(f"unsupported={[(u.construct, u.line) for u in result.unsupported]}")
for warning in result.warnings:
    print(warning)
```

See [`reference.md §5`](reference.md) for the full Python API and IR shape.
