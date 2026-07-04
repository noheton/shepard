---
title: KRL interpreter
description: Reference for the KRL trajectory MAPPING_RECIPE transform (KrlTrajectoryShape)
permalink: /reference/krl-interpreter/
layout: default
audience: contributor
---
# KRL interpreter — reference

Shepard interprets a KUKA Robot Language (`.src`/`.krl`) program against a
URDF, applies offline IK, and persists the resulting joint trajectory as a
new `TimeseriesReference` you can replay in the 3D viewer. The interpreter
itself runs as an operator-opt-in sidecar (`shepard-plugin-krl-interpreter`).

**V2CONV-B5 — converged into the generic MAPPING_RECIPE mechanism.** KRL
interpret is no longer a bespoke `POST /v2/krl/interpret` endpoint. It is now a
**MAPPING_RECIPE** template targeting the `KrlTrajectoryShape`, materialized
through the generic `POST /v2/mappings/{templateAppId}/materialize` dispatch.
This is the transform-direction sibling of the scene-graph dissolution
(V2CONV-B4): scene-graph materializes a **view** envelope; KRL materializes a
derived **reference** (the joint trajectory).

Design rationale:
[aidocs/platform/191 §decision-2](https://github.com/dlr-shepard/shepard/blob/main/aidocs/platform/191-v2-surface-convergence.md)
and the sidecar protocol in
[aidocs/integrations/117](https://github.com/dlr-shepard/shepard/blob/main/aidocs/integrations/117-krl-interpreter.md).

## The shape

`KrlTrajectoryShape` —
`http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape`
(SHACL: `backend/src/main/resources/shapes/krl-trajectory.shacl.ttl`).

A MAPPING_RECIPE template body targeting it:

```json
{
  "templateKind": "MAPPING_RECIPE",
  "mappingRecipeShape": "http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape",
  "srcFileReferenceAppId": "0192...",
  "urdfFileReferenceAppId": "0192...",
  "targetDataObjectAppId": "0192...",
  "timeseriesContainerAppId": "0192...",
  "datFileReferenceAppIds": "[\"0192...\"]"
}
```

| Body field | Required | Notes |
|---|---|---|
| `srcFileReferenceAppId` | yes | FileReference appId for the KRL `.src`/`.krl` program |
| `urdfFileReferenceAppId` | yes | FileReference appId for the URDF kinematic tree |
| `targetDataObjectAppId` | yes | Where the derived trajectory `TimeseriesReference` attaches |
| `timeseriesContainerAppId` | yes | The container the trajectory channels (`joint_0 … joint_N`) are written to |
| `datFileReferenceAppIds` | no | JSON array string of `.dat` companion FileReference appIds |

## REST surface

### `POST /v2/mappings/{templateAppId}/materialize`

The generic materialize endpoint. Resolves the recipe's `mappingRecipeShape`
IRI → the `KrlTrajectoryTransformExecutor`
(`de.dlr.shepard.v2.transform.krl.KrlTrajectoryTransformExecutor`), which
resolves the `.src` + URDF bytes, calls the sidecar, persists the trajectory,
and returns the derived reference appId.

**Request body** (`MaterializeRequestIO`) — bind the input reference appIds by
role; the executor falls back to the template body fields when a role binding
is absent:

```json
{ "inputReferenceAppIds": {
    "srcFileAppId": "0192...",
    "urdfFileAppId": "0192..." } }
```

**Response (`200`)** — `MaterializeResponseIO`:

```json
{
  "templateAppId": "0192...",
  "outputKind": "REFERENCE",
  "derivedReferenceAppId": "0192...",
  "executor": "KrlTrajectoryTransformExecutor"
}
```

`derivedReferenceAppId` is the newly minted joint-trajectory
`TimeseriesReference`.

**Status codes** (from the generic materialize dispatcher):

| Status | Meaning |
|---|---|
| 200 | Trajectory materialized; `derivedReferenceAppId` set. |
| 401 | Authentication required. |
| 404 | Template not found, or no executor registered for the shape IRI (the `krl-interpreter` plugin / sidecar may not be installed). |
| 422 | Body not a MAPPING_RECIPE / no `mappingRecipeShape`, or a typed executor failure — missing input, unresolvable input, **or the sidecar is unreachable** (operator hasn't enabled the `krl-interpreter` compose profile). |

> The bespoke `/v2/krl/interpret` 502/504/501 sidecar-status mapping is gone —
> a down or erroring sidecar now surfaces as a single `422` transform error with
> the operator hint in the message body. This is the converged failure shape; a
> recipe whose sidecar isn't running is a recoverable 4xx, never a 500.

## Frontend UI

The **"Interpret as joint trajectory"** button is mounted on the `.src`/`.krl`
FileReference detail page
(`frontend/components/container/file/InterpretAsTrajectoryButton.vue`). It is the
in-context entry point (CLAUDE.md "tool entry points are in-context first").

| Surface | Purpose |
|---|---|
| `InterpretAsTrajectoryButton.vue` | Conditional render on `.src`/`.krl` files; opens the dialog; creates + materializes the recipe. |
| `useKrlTrajectory.ts` | Builds the MAPPING_RECIPE body, creates the template (`POST /v2/templates`), materializes it (`useMaterializeMapping`). |
| `interpretAsTrajectoryHelpers.ts` | Pure picker + validity helpers. |

The dialog gathers the URDF picker + target DataObject (defaulted to the `.src`
parent) + TimeseriesContainer + optional `.dat` companions, then creates the
template and materializes it, linking the derived `TimeseriesReference`.

## KRL coverage (tier-1)

| Construct | Status |
|---|---|
| `PTP`, `LIN`, `CIRC` motion | ✓ supported |
| `WAIT SEC <n>` | ✓ supported |
| `WAIT FOR <cond>` | ⚙ degraded — emits a WARN |
| `IF / FOR / WHILE / LOOP` flow control | ✓ supported |
| Variable assignment, FRAME/E6POS literals | ✓ supported |
| `$BASE` / `$TOOL` reassignment | ✓ supported |
| `SPS`, `INTERRUPT`, `ANIN`, `ANOUT` | ✗ HARD-STOP (sidecar refuses) |
| `BCO` (block coincidence) | ⚙ skipped silently |
| `#INCLUDE` | ⚙ tier-1 ignores; tier-2 follows |
| `.kop` WorkVisual bundles | ✗ out of scope |

See [aidocs/integrations/117 §4](https://github.com/dlr-shepard/shepard/blob/main/aidocs/integrations/117-krl-interpreter.md#4-krl-subset-covered-tier-1)
for the full table with KRL manual citations.

## Provenance

Every successful materialization records a `:KrlTrajectoryActivity` (the
converged successor of the bespoke `:KrlInterpretActivity` — existing nodes are
relabelled in place by migration V112, never deleted) with PROV-O edges:

- `USED → :FileReference` (the `.src`/`.krl` program)
- `USED → :FileReference` (the URDF)
- `USED → :FileReference` (each `.dat` companion, optional)
- `GENERATED → :TimeseriesReference` (the derived joint trajectory)
- `WAS_ASSOCIATED_WITH → :User`

The activity carries the interpreter version, IK solver name + version,
mean/p99 IK cycle ms, residuals, and warning + unsupported-construct counts.
When the materialize is AI-driven (the recipe body carries an `aiAgent` field),
`sourceMode=ai` + `agentId` are recorded per the EU AI Act Art. 50 disclosure
shape.

## Configuration (deploy-time)

| Key | Default | Notes |
|---|---|---|
| `shepard.krl.sidecar.url` | `http://krl-interpreter-sidecar:8000` | Where the sidecar listens. |
| `shepard.krl.sidecar.timeout-seconds` | `120` | Per-request timeout. |
| `shepard.krl.sidecar.max-body-size-mb` | `16` | Payload cap. |

Tier-2 (runtime-mutable `:KrlInterpreterConfig`) is queued under
[`KRL-CONFIG-1`](https://github.com/dlr-shepard/shepard/blob/main/aidocs/16-dispatcher-backlog.md).
The full plugin extraction to `plugins/krl-interpreter` is deferred to
[`V2CONV-A6`](https://github.com/dlr-shepard/shepard/blob/main/aidocs/16-dispatcher-backlog.md);
the executor lives in-tree for now.

## Related

- [Task: Run / preview a KRL program](/help/run-krl-preview/).
- [Materialize a MAPPING_RECIPE](/reference/scene-graph/) — the sibling view-direction transform.
- [Design doc — aidocs/platform/191](https://github.com/dlr-shepard/shepard/blob/main/aidocs/platform/191-v2-surface-convergence.md).
- [Sidecar protocol — aidocs/integrations/117](https://github.com/dlr-shepard/shepard/blob/main/aidocs/integrations/117-krl-interpreter.md).
