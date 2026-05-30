# shepard-plugin-krl-interpreter — reference

**Audience.** Plugin authors and power users who need to know what
KRL the parser handles, how the IR is shaped, how warnings /
unsupported constructs surface, and how to call the backend wrapper
endpoint.

**Status.** KRL-INTERPRETER-02 (parser), KRL-INTERPRETER-03 (IK),
KRL-INTERPRETER-04 (sidecar), KRL-INTERPRETER-05 (backend REST), and
KRL-INTERPRETER-06 (frontend UI) are all **shipped**. See
[`aidocs/integrations/117-krl-interpreter.md`](../../../aidocs/integrations/117-krl-interpreter.md)
for the full system design.

---

## 1. What this module does

Given a KUKA `.src` (executable program) or `.dat` (companion data)
file, produce a Python intermediate representation (IR) that:

* preserves enough source structure to drive an IK back-solve
  (`KRL-INTERPRETER-03`);
* surfaces every unsupported tier-1 construct as a structured node
  (queryable later via the `krl_list_unsupported` MCP tool described
  in `aidocs/integrations/117 §9.2`);
* attaches `(line, column)` to every IR node for downstream audit
  trails.

The sidecar (`KRL-INTERPRETER-04`) exposes this as a REST endpoint.
The Shepard backend (`KRL-INTERPRETER-05`) wraps it behind
`POST /v2/krl/interpret`, which resolves `appId` references,
invokes the sidecar, and persists the resulting joint trajectory as a
`TimeseriesReference`.

## 2. KRL subset covered

Mirrors `aidocs/integrations/117 §4`. Tier-1 coverage:

| KRL construct                                  | Parser support  | IR node                          |
| ---------------------------------------------- | --------------- | -------------------------------- |
| `PTP <pose>` / `PTP_REL <pose>`                | full            | `Motion(kind=PTP|PTP_REL)`       |
| `LIN <pose>` / `LIN_REL <pose>`                | full            | `Motion(kind=LIN|LIN_REL)`       |
| `CIRC <aux>, <target>` / `CIRC_REL`            | full            | `Motion(kind=CIRC|CIRC_REL, aux)` |
| Motion options (`C_DIS`, `C_PTP`, …)           | captured raw    | `Motion.opts: list[str]`         |
| `WAIT SEC <n>`                                 | full            | `Wait(seconds=<n>)`              |
| `WAIT FOR <cond>`                              | **degraded**    | `Wait(condition=...)` + WARN     |
| `IF / THEN / ELSE / ENDIF`                     | full            | `If(condition, then, else)`      |
| `FOR <v>=<a> TO <b> [STEP <s>] / ENDFOR`       | full            | `For(var, start, end, step, body)` |
| `WHILE <cond> / ENDWHILE`                      | full            | `While(condition, body)`         |
| `LOOP / ENDLOOP` + `EXIT`                      | full            | `Loop(body)` + `Exit`            |
| `$BASE = <frame>` / `$TOOL = <frame>`          | full            | `BaseToolSwitch(target, frame)`  |
| `FRAME` literal `{X 100, Y 0, Z 200, A 0, B 0, C 0}` | full       | `FrameLiteral`                   |
| Sparse frame literal (subset of fields)        | full (defaults) | `FrameLiteral.extras['_missing_fields']` + INFO warning |
| `E6POS` literal (`E1`–`E6`)                    | full            | `E6PosLiteral`                   |
| `DECL <type> <name> [= <expr>]`                | full            | `VarDecl(type_name, name, initial)` |
| `<var> = <expr>`                               | full            | `Assign(var, expr)`              |
| `DEFDAT <name> PUBLIC?`                        | full            | `Program(is_data_file=True)`     |
| `BCO`                                          | **unsupported** | `UnsupportedConstruct + WARN`    |
| `SPS`                                          | **unsupported** | `UnsupportedConstruct + WARN`    |
| `INTERRUPT` / `ON ERROR` / `ON INTERRUPT`      | **unsupported** | `UnsupportedConstruct + WARN`    |
| `ANIN` / `ANOUT` (sensor IO)                   | **unsupported** | `UnsupportedConstruct + WARN`    |
| `CONTINUE` / `HALT`                            | **unsupported** | `UnsupportedConstruct + WARN`    |
| `#INCLUDE` cross-file resolution               | out of scope    | (tier-2)                         |
| `.kop` WorkVisual bundles                      | out of scope    | (operator extracts manually)     |

## 3. IR shape

```
Program
  ├─ module_name: str | None
  ├─ is_function: bool       # True for DEFFCT
  ├─ is_data_file: bool      # True for DEFDAT
  ├─ is_global: bool
  └─ statements: list[Statement]

Statement = Motion | Wait | If | For | While | Loop | Exit
          | Assign | VarDecl | BaseToolSwitch | UnsupportedConstruct

Motion(kind: MotionKind, target: Pose, aux: Pose | None, opts: list[str])
Wait(seconds: float | None, condition: str | None)   # exactly one set
If(condition: str, then_block: list[Statement], else_block: list[Statement])
For(var: str, start: str, end: str, step: str | None, body: list[Statement])
While(condition: str, body: list[Statement])
Loop(body: list[Statement])
Exit()
BaseToolSwitch(target: FrameTarget, frame: Pose)
Assign(var: str, expr: str)
VarDecl(type_name: str, name: str, initial: str | None)
UnsupportedConstruct(construct: str, reason: str, raw_text: str)

Pose = FrameLiteral | E6PosLiteral | VarRef
FrameLiteral(x, y, z, a, b, c, extras: dict)
E6PosLiteral(frame: FrameLiteral, e1..e6: float | None)
VarRef(name: str)
```

Every statement node also carries `line: int` and `column: int`.

Expression values (loop bounds, conditions, RHS of assignment) are
preserved as opaque source-text strings — the parser does not
evaluate them. The IK / interpret layer (`KRL-INTERPRETER-03`)
is the consumer that resolves them.

## 4. Warning + error surfaces

Three surfaces:

1. **`ParseResult.warnings`** — list of
   `krl_interpreter.errors.Warning`. Severity ∈ `INFO | WARN | ERROR`.
   Examples:
   * `INFO` — sparse frame literal (missing field defaults to 0).
   * `WARN` — `WAIT FOR` degraded; tier-1 unsupported construct
     (BCO / SPS / INTERRUPT / etc.).
   * `ERROR` — ANTLR4 syntax errors that did not abort the parse.

2. **`ParseResult.unsupported`** — list of `UnsupportedConstruct` IR
   nodes. This is the structured surface an audit lens or the MCP
   `krl_list_unsupported` tool consumes.

3. **`ParseError`** — raised only when the source is unparseable past
   the first statement (the sidecar's 400 case). Carries
   `line` + `column` from the first ANTLR4 syntax error.

## 5. Public Python API

```python
from krl_interpreter import parse, ParseResult, ParseError

result: ParseResult = parse(source_text, filename="Ply_5_layup.src")
result.program       # Program IR
result.warnings      # list[Warning]
result.unsupported   # list[UnsupportedConstruct]
```

Lower-level access for advanced use:

```python
from krl_interpreter.parser.walker import KrlIrBuilder
from krl_interpreter.parser.grammar.generated.KrlLexer import KrlLexer
from krl_interpreter.parser.grammar.generated.KrlParser import KrlParser
from antlr4 import CommonTokenStream, InputStream

lexer = KrlLexer(InputStream(source))
parser = KrlParser(CommonTokenStream(lexer))
tree = parser.program()
builder = KrlIrBuilder(filename="x.src")
program = builder.build(tree)
```

## 6. Grammar provenance

See [`krl_interpreter/parser/grammar/SOURCES.md`](../krl_interpreter/parser/grammar/SOURCES.md).
The `.g4` files are original work under MIT; regeneration is one
`java -jar antlr-4.13.2-complete.jar …` invocation.

## 7. Sidecar REST API (KRL-INTERPRETER-04)

The sidecar is a stateless FastAPI service. The Shepard backend
(KRL-INTERPRETER-05) is the upstream caller; the sidecar exposes no
host-side port (operator-opt-in via the `krl-interpreter` compose
profile; see [`docs/install.md`](install.md)).

Every response carries the header `X-KRL-Interpreter-Version: <semver>`.

### `POST /interpret`

Parse + IK-solve + emit a joint trajectory in one round-trip.

**Request body** (Pydantic v2 — full schema in
`krl_interpreter/sidecar/schemas.py`):

```json
{
  "srcText": "DEF p()\nPTP {X 1000, Y 0, Z 0, A 0, B 0, C 0}\nEND\n",
  "datText": null,
  "urdfPath": "/data/urdf/kr210.urdf",
  "baseFrame": null,
  "toolFrame": null,
  "seedPose": null,
  "timeStep": 0.01,
  "options": {
    "maxIterations": 100,
    "ikTolerance": 0.001,
    "motionDuration": 1.0,
    "maxIrIterations": 100000,
    "bcoAsWait": true
  }
}
```

**Required**: `srcText` (non-empty) + `urdfPath` (non-empty).
**Frames on the wire are metres + radians**; the backend converts from
KRL native millimetres before invoking the sidecar (the composer applies
the same mm -> m conversion at the IR -> IK boundary internally).

**Response — 200**:

```json
{
  "trajectory": [
    {"t": 0.01, "joints": [0.0, 0.0, 0.0, 0.0]},
    {"t": 0.02, "joints": [0.001, 0.0, 0.0, 0.0]}
  ],
  "warnings": [
    {"line": 12, "message": "unreachable: position residual 0.04 m exceeds tolerance 0.001 m", "severity": "warning"}
  ],
  "unsupportedConstructs": [
    {"construct": "INTERRUPT", "line": 47, "reason": "tier-1 unsupported"}
  ],
  "ikSolverStats": {
    "meanCycleMs": 12.4,
    "maxResidual": 0.00041,
    "failedPoses": 0,
    "totalPoses": 1872
  },
  "interpreterVersion": "0.1.0"
}
```

**Curl example** (from inside the Shepard compose network):

```bash
curl --fail --silent \
  -X POST http://krl-interpreter-sidecar:8000/interpret \
  -H "Content-Type: application/json" \
  -d @body.json | jq .
```

**Error codes**:

| Status | Meaning                                                                  |
| ------ | ------------------------------------------------------------------------ |
| 400    | KRL parse error (hard) or URDF load failure.                              |
| 422    | Pydantic validation error (e.g. `timeStep <= 0`, `srcText` empty).        |
| 501    | Async endpoints (`/interpret/async`, `/interpret/jobs/...`) — deferred.   |

### `POST /interpret/async` — DEFERRED (tier-2)

Returns 501 at tier-1. The async polling pattern (202 + jobId + GET
`/interpret/jobs/{jobId}` poll) is documented in
`aidocs/integrations/117 §6` but not implemented at tier-1; the sync
endpoint handles all current traffic shapes.

### `GET /health`

Liveness probe consumed by the compose healthcheck.

```json
{"status": "ok", "version": "0.1.0"}
```

### Composer semantics summary

| KRL IR node              | Composer behaviour (tier-1)                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------ |
| `Motion(PTP/LIN/CIRC)`   | Resolve `world = $BASE @ pose`; IK-solve; linear-interpolate joints from prev to current.   |
| `Wait(seconds)`          | Hold last joints for `seconds`; advance clock.                                              |
| `Wait(condition)`        | Warning + no-op (no offline equivalent).                                                    |
| `For(start, end, step)`  | Unroll at compile time. Non-integer bounds → warning + skip body.                          |
| `If(condition)`          | Literal `TRUE` / `FALSE` only; otherwise warning + skip both branches.                     |
| `While` / `Loop`         | Tier-1 cannot evaluate conditions; warning + skip body (or one-pass unroll for `LOOP`).    |
| `BaseToolSwitch($BASE)`  | Mutates active base for subsequent motions.                                                |
| `BaseToolSwitch($TOOL)`  | Stored but **not applied** to IK target at tier-1 (documented limitation).                 |
| `UnsupportedConstruct`   | Passed through to `unsupportedConstructs` list (no additional warning).                    |
| `Assign` / `VarDecl`     | Silently no-op (no expression evaluator at tier-1).                                        |

**Failed IK** → warning + hold previous joints across the motion
duration (clock still advances; failed-pose count incremented).

## 8. Backend wrapper endpoint — `POST /v2/krl/interpret` (KRL-INTERPRETER-05)

The backend wrapper resolves `FileReference` `appId`s to byte payloads,
calls the sidecar, persists the resulting joint-angle trajectory as a
new `TimeseriesReference` (channels named `joint_0 … joint_N`), and
records a `:KrlInterpretActivity` PROV-O activity.

### Authentication

`@Authenticated` — any logged-in user. Permission to write to the
target DataObject's collection is enforced by
`TimeseriesReferenceService.createReference()`. Returns `403` when the
caller lacks write access.

### Request body (`KrlInterpretRequestIO`)

| Field | Type | Required | Description |
| ----- | ---- | -------- | ----------- |
| `srcFileAppId` | `string` | **yes** | `appId` of the `FileReference` holding the KRL `.src` program. |
| `urdfFileAppId` | `string` | **yes** | `appId` of the `FileReference` holding the URDF XML. |
| `targetDataObjectAppId` | `string` | **yes** | `appId` of the DataObject to which the resulting `TimeseriesReference` is attached. |
| `timeseriesContainerAppId` | `string` | **yes** | `appId` of the `TimeseriesContainer` the trajectory data points are written to. (Tier-2 auto-mint deferred to `KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER`.) |
| `sceneAppId` | `string` | no | `appId` of a `:DigitalTwinScene` — provides default base/tool frame when not overridden by explicit frame fields. |
| `datFileAppIds` | `string[]` | no | `appId`s of companion `.dat` `FileReference`s. |
| `baseFrame` | `{x,y,z,rx,ry,rz}` | no | Base-frame override in metres + radians. Replaces the `$BASE` frame from the `.src`. |
| `toolFrame` | `{x,y,z,rx,ry,rz}` | no | Tool-frame override in metres + radians. |
| `seedPose` | `number[]` | no | Seed joint angles (radians) for IK convergence. Leave null for URDF zero pose. |
| `timeStep` | `number` | no | Trajectory sample step in seconds. Default: `0.01` (100 Hz). |
| `options` | `object` | no | Pass-through options to the sidecar: `ikTolerance`, `maxIterations`, `motionDuration`, `maxIrIterations`, `bcoAsWait`. |

### Response — 201 Created (`KrlInterpretResponseIO`)

```json
{
  "trajectoryAppId": "01900000-0000-7000-0000-000000000001",
  "activityAppId":   "01900000-0000-7000-0000-000000000002",
  "warnings": [
    {"line": 12, "severity": "WARN", "message": "WAIT FOR degraded — no offline equivalent"}
  ],
  "unsupportedConstructs": [
    {"construct": "INTERRUPT", "line": 47, "reason": "tier-1 unsupported"}
  ],
  "ikSolverStats": {
    "meanCycleMs": 12.4,
    "p99CycleMs": 18.7,
    "maxResidualMeters": 4.1e-4,
    "maxResidualRadians": null,
    "failedPoses": 0,
    "totalPoses": 1872,
    "solverName": "ikpy",
    "solverVersion": "3.3.4"
  },
  "interpreterVersion": "0.1.0"
}
```

### Worked curl example

```bash
curl --fail --silent \
  -X POST https://shepard.example.org/v2/krl/interpret \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "srcFileAppId":              "01900000-0000-7000-0000-0000000000aa",
    "urdfFileAppId":             "01900000-0000-7000-0000-0000000000bb",
    "targetDataObjectAppId":     "01900000-0000-7000-0000-0000000000cc",
    "timeseriesContainerAppId":  "01900000-0000-7000-0000-0000000000dd"
  }' | jq '{trajectoryAppId, activityAppId}'
```

Pass `X-AI-Agent: my-agent-id` to record the call as `sourceMode=ai`
in the `:KrlInterpretActivity` (EU AI Act Art. 50 disclosure shape).

### Error codes

| Status | Condition |
| ------ | --------- |
| 201 | Trajectory persisted; `trajectoryAppId` + `activityAppId` returned. |
| 400 | Malformed input (missing required field, unknown `appId`). |
| 401 | Authentication required. |
| 403 | Caller lacks write on the target DataObject's collection. |
| 422 | IK divergence above the configured tolerance. |
| 501 | KRL program contains a hard-stop construct (SPS / INTERRUPT / ANIN / ANOUT). |
| 502 | Sidecar unreachable or returned an unexpected error. (Operator: bring up the `krl-interpreter` compose profile.) |
| 504 | Sidecar call timed out (see `shepard.krl.sidecar.timeout-seconds`). |

### Provenance

Every successful call records a `:KrlInterpretActivity` (an
`:Activity` with the additional `KrlInterpretActivity` label) with:

* `USED` edges → src `FileReference`, URDF `FileReference`, optional
  `.dat` `FileReference`s, optional `:DigitalTwinScene`.
* `GENERATED` edge → the new trajectory `TimeseriesReference`.
* `WAS_ASSOCIATED_WITH` edge → `:User`.
* `sourceMode` = `"ai"` when `X-AI-Agent` header is present.

The filter-skip handoff (`PROP_SKIP_CAPTURE`) ensures exactly one
`:Activity` row per call — no duplicate generic-capture row.

### Output: trajectory timeseries

The `TimeseriesReference` created by a successful call writes one
channel per URDF joint: `joint_0`, `joint_1`, … `joint_N`. Each
channel is annotated with `urn:shepard:urdf:joint:joint_<n>` so the
URDF viewer (`URDF-WEBVIEW-1`) auto-binds joints to channels without
manual mapping. The `interpreterVersion` field in the response is
stored on the `:KrlInterpretActivity` for EN 9100 audit reproducibility.

### `:DigitalTwinScene` integration

When `sceneAppId` is supplied, the backend loads the scene's default
base/tool frames before calling the sidecar. Explicit `baseFrame` /
`toolFrame` fields override the scene's defaults. When both `sceneAppId`
and explicit frame fields are absent, the sidecar uses the frames
declared in the `.src` program itself.

## 9. Frontend UI (KRL-INTERPRETER-06)

The "Run / preview" button (`RunKrlPreviewButton.vue`) appears on the
`FileReference` detail page for any `.src` file. It is:

* Shown only when the file name ends in `.src` (case-insensitive).
* Disabled when the caller lacks write access on the parent collection.

Clicking it opens `RunKrlPreviewDialog.vue`, which collects:

**Required fields:**
- URDF `FileReference` picker (pre-selects the only candidate when
  exactly one URDF is in the DataObject).
- Target DataObject `appId` (defaults to the parent DataObject of the
  `.src` file).
- `TimeseriesContainer` `appId`.

**Advanced section (collapsed by default):**
- `.dat` companion file picker (pre-selects the same-stem `.dat` when
  found).
- `timeStep`, `ikTolerance`, `maxIterations`.
- Base/tool frame override toggles with six-axis input fields.
- Seed pose (comma-separated joint angles).

After submission, `KrlInterpretResultPanel.vue` renders:

* A status chip: "Interpreter resolved offline replay" (success) or
  HTTP error code with operator hint on 502.
* `trajectoryAppId` + `activityAppId` for audit drill-down.
* "Run preview" button linking to the URDF viewer
  (`/shapes/render?renderer=urdf&urdfUrl=…`).
* "Back to DataObject" navigation link.
* Warnings table (line, severity, message).
* Unsupported constructs table (construct, line, reason).
* IK convergence stats (mean cycle ms, p99 cycle ms, max residual,
  failed/total poses, solver name/version).

## 10. Deploy-time configuration (KRL-INTERPRETER-05)

Three `application.properties` keys (deploy-time only at tier-1;
runtime-mutable `:KrlInterpreterConfig` admin singleton deferred to
**KRL-CONFIG-1**):

| Key | Default | Description |
| --- | ------- | ----------- |
| `shepard.krl.sidecar.url` | `http://krl-interpreter-sidecar:8000` | Base URL of the KRL interpreter sidecar. Override when the sidecar runs on a non-default host or port. |
| `shepard.krl.sidecar.timeout-seconds` | `120` | Per-call HTTP timeout in seconds. Increase for very large programs (> 3000 poses). |
| `shepard.krl.sidecar.max-body-size-mb` | `16` | Guard against runaway payloads. The backend rejects requests whose summed file payloads exceed this value (MiB). |

## 11. Cross-references

* `aidocs/integrations/117-krl-interpreter.md` — full system design.
* `aidocs/integrations/113-urdf-viewer.md` — downstream consumer.
* `aidocs/integrations/110-file-format-parser-plugin.md §4.3` — RDK
  parse (sibling).
* `aidocs/data/85-coordinate-frame-tree.md` — frame schema the IK
  layer resolves targets against.
