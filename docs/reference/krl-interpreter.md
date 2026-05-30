---
title: KRL interpreter
description: Reference for POST /v2/krl/interpret and the Run / preview UI
permalink: /reference/krl-interpreter/
layout: default
audience: contributor
---
# KRL interpreter — reference

Shepard ships a KUKA Robot Language interpreter as an operator-opt-in
sidecar (`shepard-plugin-krl-interpreter`). The interpreter takes a
`.src` program + a URDF, applies offline IK, and persists a 6-channel
joint trajectory as a `TimeseriesReference` you can replay in the URDF
viewer.

Design rationale: [aidocs/integrations/117-krl-interpreter.md](https://github.com/dlr-shepard/shepard/blob/main/aidocs/integrations/117-krl-interpreter.md).

## REST surface

### `POST /v2/krl/interpret`

Resolves a KRL `.src` against a URDF and persists the joint trajectory.

**Request body** (`KrlInterpretRequestIO`):

| Field | Type | Required | Notes |
|---|---|---|---|
| `srcFileAppId` | string (UUID v7) | yes | FileReference appId for the KRL `.src` |
| `urdfFileAppId` | string (UUID v7) | yes | FileReference appId for the URDF |
| `targetDataObjectAppId` | string | yes | Where the trajectory attaches |
| `timeseriesContainerAppId` | string | yes | The container the trajectory bytes are written to (tier-2 auto-mints) |
| `datFileAppIds` | string[] | no | `.dat` companion FileReferences |
| `sceneAppId` | string | no | `:DigitalTwinScene` for default base/tool frames |
| `baseFrame` | `{x,y,z,rx,ry,rz}` | no | Override `$BASE` |
| `toolFrame` | `{x,y,z,rx,ry,rz}` | no | Override `$TOOL` |
| `seedPose` | number[] | no | IK seed joint vector |
| `timeStep` | number | no | Sample step in s; defaults to `0.01` |
| `options` | object | no | Pass-through `{ikTolerance, maxIterations, …}` |

**Headers honoured:**

- `Authorization: Bearer <token>` (required).
- `X-AI-Agent: <agent-id>` (optional). When set, the recorded
  `:KrlInterpretActivity` carries `sourceMode=ai` + `agentId=<value>`
  per the EU AI Act Art. 50 disclosure shape.

**Response (`201 Created`)** — `KrlInterpretResponseIO`:

```json
{
  "trajectoryAppId": "0192...",
  "activityAppId": "0192...",
  "warnings": [{"line": 12, "severity": "WARN", "message": "WAIT FOR skipped"}],
  "unsupportedConstructs": [
    {"construct": "SPS", "line": 1, "reason": "HARD-STOP — no offline equivalent"}
  ],
  "ikSolverStats": {
    "meanCycleMs": 12.4,
    "p99CycleMs": 38.1,
    "maxResidualMeters": 0.00041,
    "maxResidualRadians": 0.0008,
    "failedPoses": 0,
    "totalPoses": 1872,
    "solverName": "ikpy",
    "solverVersion": "3.4.2"
  },
  "interpreterVersion": "0.1.0"
}
```

**Status codes:**

| Status | Meaning |
|---|---|
| 201 | Trajectory persisted. |
| 400 | Malformed input (missing required field, unknown appId). |
| 401 | Authentication required. |
| 403 | Caller lacks write on the target DataObject's collection. |
| 422 | IK divergence above the configured threshold. |
| 501 | KRL HARD-STOP construct present (SPS, INTERRUPT, ANIN, ANOUT). |
| 502 | Sidecar unreachable — operator hasn't enabled the `krl-interpreter` compose profile. |
| 504 | Sidecar call timed out. |

## Frontend UI

The "Run / preview" button is mounted on the `.src` FileReference
detail page (`frontend/components/container/file/RunKrlPreviewButton.vue`).
Components shipped with KRL-INTERPRETER-06:

| Component | Purpose |
|---|---|
| `RunKrlPreviewButton.vue` | Conditional render on `.src` files; opens the dialog. |
| `RunKrlPreviewDialog.vue` | Modal that gathers the request body. |
| `KrlInterpretResultPanel.vue` | Post-run summary; renders the operator-hint on 502. |
| `useKrlInterpret.ts` | Typed wrapper around `POST /v2/krl/interpret`. |

## KRL coverage (tier-1)

| Construct | Status |
|---|---|
| `PTP`, `LIN`, `CIRC` motion | ✓ supported |
| `WAIT SEC <n>` | ✓ supported |
| `WAIT FOR <cond>` | ⚙ degraded — emits a WARN |
| `IF / FOR / WHILE / LOOP` flow control | ✓ supported |
| Variable assignment, FRAME/E6POS literals | ✓ supported |
| `$BASE` / `$TOOL` reassignment | ✓ supported |
| `SPS`, `INTERRUPT`, `ANIN`, `ANOUT` | ✗ HARD-STOP (501) |
| `BCO` (block coincidence) | ⚙ skipped silently |
| `#INCLUDE` | ⚙ tier-1 ignores; tier-2 follows |
| `.kop` WorkVisual bundles | ✗ out of scope |

See [aidocs/integrations/117 §4](https://github.com/dlr-shepard/shepard/blob/main/aidocs/integrations/117-krl-interpreter.md#4-krl-subset-covered-tier-1) for the full table with KRL manual citations.

## Provenance

Every successful interpret records a `:KrlInterpretActivity` with PROV-O
edges:

- `USED → :FileReference {kind=src}`
- `USED → :FileReference {kind=urdf}`
- `USED → :FileReference {kind=dat}` (optional)
- `USED → :DigitalTwinScene` (optional)
- `GENERATED → :TimeseriesReference`
- `WAS_ASSOCIATED_WITH → :User`

The activity carries the interpreter version, IK solver name + version,
mean/p99 IK cycle ms, residuals, and the unsupported-construct count.

## Configuration (deploy-time)

| Key | Default | Notes |
|---|---|---|
| `shepard.krl.sidecar.url` | `http://krl-interpreter:8080` | Where the sidecar listens. |
| `shepard.krl.sidecar.timeout-seconds` | `120` | Per-request timeout. |
| `shepard.krl.sidecar.max-body-size-mb` | `25` | Payload cap. |

Tier-2 (runtime-mutable `:KrlInterpreterConfig`) is queued under
[`KRL-CONFIG-1`](https://github.com/dlr-shepard/shepard/blob/main/aidocs/16-dispatcher-backlog.md).

## Related

- [Task: Run / preview a KRL program](/help/run-krl-preview/).
- [Plugin reference: `shepard-plugin-krl-interpreter`](/reference/plugins/).
- [URDF viewer](/reference/scene-graph/) — the consumer of the trajectory.
- [Design doc — aidocs/integrations/117](https://github.com/dlr-shepard/shepard/blob/main/aidocs/integrations/117-krl-interpreter.md).
