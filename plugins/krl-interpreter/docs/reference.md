# shepard-plugin-krl-interpreter — reference

This page is the comprehensive reference for the KRL interpreter plugin.
It complements the design doc at
[`aidocs/integrations/117-krl-interpreter.md`](../../../aidocs/integrations/117-krl-interpreter.md);
the design doc is the rationale and persona-board lens, this page is
the per-surface contract.

> The parser section (KRL constructs, IR shape, `.dat` resolution) is
> owned by KRL-INTERPRETER-02 and lands in the same file. This pass
> covers the **IK back-solver** surface (KRL-INTERPRETER-03).

## IK back-solver

### Module map

| Module | Role |
|---|---|
| `krl_interpreter.ik.solver` | `IkSolver` — per-URDF solver wrapping `ikpy.chain.Chain`. |
| `krl_interpreter.ik.urdf_loader` | `UrdfLoader` — URDF → ikpy chain; movable-joint index extraction. |
| `krl_interpreter.ik.seed_strategy` | `SeedStrategy` interface + `LastSolutionSeed`, `NamedPoseSeed`, `ZeroSeed`. |
| `krl_interpreter.ik.types` | `TargetPose`, `IkRequest`, `IkResult`, `IkWarning` dataclasses. |

### `IkSolver` API

```python
from krl_interpreter.ik import IkSolver, TargetPose

solver = IkSolver(
    urdf_path="/path/to/kr210l150.urdf",
    base_link="base_link",   # optional; default = ikpy auto-detect
    tip_link=None,           # reserved for ikpy 4.x; ignored on 3.4
)

result = solver.solve(
    target=TargetPose(x=1.8, y=0.2, z=1.7, rx=0.0, ry=0.0, rz=0.0),
    seed=None,               # full ikpy-chain-length list[float] or None
    tolerance=1e-3,          # metres; controls converged() decision
    max_iterations=100,      # accepted but currently informational
)

result.joints       # list[float] — FULL ikpy chain array
result.residual     # float — position error in metres after FK
result.iterations   # int — -1 (ikpy does not surface count)
result.converged    # bool — residual <= tolerance
result.warnings     # list[IkWarning]
```

#### Joint-array contract (read this if you are writing the sidecar -04)

`IkResult.joints` is the **full** ikpy chain array. Length matches
`solver.chain_length`. Indices of fixed URDF links are always `0.0`.
To get the movable-joint subset for emitting trajectory channels:

```python
movable = solver.movable_joint_indices   # e.g. [1, 2, 3, 4, 5, 6] on KR210
names   = solver.movable_joint_names     # e.g. ["joint_a1", ..., "joint_a6"]
channels = [result.joints[i] for i in movable]
```

The corresponding TS channels carry the
`urn:shepard:urdf:joint:<name>` annotation so URDF-WEBVIEW-1 auto-binds
without channel picking (per `feedback_annotation_preselection_principle.md`).

### Seed strategies

| Strategy | Behaviour | When to use |
|---|---|---|
| `ZeroSeed` | Always seeds at `[0.0] * chain_length`. | First pose of a program if no named pose available; reproducible behaviour for tests. |
| `LastSolutionSeed` | Returns the previous successful solve; falls back to `None` (solver's default = URDF zero) on the first call. | Default for sidecar trajectory generation — mimics KRC `$RC_OLDPOS` per design doc §5.3. |
| `NamedPoseSeed(table, name)` | Pulls a named pose from an operator-supplied table (typically derived from `.dat` `HOME`/`PARK`). Returns `None` on unknown name. | Operator wants a specific reference posture. |

Implementing a new strategy: subclass `SeedStrategy` and override
`next_seed(chain_len)`. Optionally override `update(solution)` to track
history.

### Convergence semantics

- **Convergence is computed in this module, not by ikpy.** We
  forward-kinematic the IK output and compare position to the target.
- **Orientation residual is not consulted at tier-1.** The design doc
  defers orientation-aware convergence to tier-2.
- **Unreachable targets do not raise.** `solve()` returns
  `converged=False` plus a single `IkWarning(kind="unreachable", ...)`.
  The sidecar surfaces the warning; the run continues.
- **Iterations is `-1`.** ikpy does not expose its scipy iteration
  count via the public API. The field is on the dataclass for a future
  scipy-direct backend.

### Tolerance / max-iterations trade-off

| Tolerance | Effect | Note |
|---|---|---|
| `1e-4` m | Very tight | Diminishing returns — KRC interpolator itself quantises poses; few KRL programs benefit. |
| `1e-3` m | **Default** (per design doc) | The right floor for AFP layup precision. |
| `1e-2` m | Loose | Mean cycle time drops; visible jitter in the URDF viewer. |

`max_iterations` is accepted on the request and recorded in the
:KrlInterpretActivity Activity for audit, but the underlying ikpy 3.4
public API does not accept a max-iter knob. Tier-2 (the optional
`pinocchio` polish) will honour the field.

### URDF loader notes

`UrdfLoader.load(urdf_path, base_elements=None, active_links_mask=None)`
forwards to `ikpy.chain.Chain.from_urdf_file`. The `base_elements`
override is required for KUKA cell URDFs that mount the arm on a
fixture link (e.g. an LBR on an LBR iiwa cart); pass
`["base_link"]` (or the actual arm root) to keep ikpy from grabbing
the wrong tip.

`UrdfLoader.movable_joint_indices(chain)` returns the chain-array
indices whose URDF joint type is not `fixed`. Synthetic ikpy
`OriginLink`s are also excluded.

### Failure modes

| Condition | Behaviour |
|---|---|
| Target outside reachable workspace | `converged=False` + `IkWarning(kind="unreachable")`. Mean cycle time on a failed solve increases modestly; no retry. |
| Seed length mismatch | `IkWarning(kind="seed_shape_mismatch")` + zero seed substituted. |
| ikpy raises (NaN / singular Jacobian — rare) | `IkWarning(kind="solver_error", severity="ERROR")` + `residual=inf`. |

### Benchmark (KR210, 100 random-walk reachable poses)

Smoke-tested at module-test time:

| Metric | Value (local 4-core 2 GHz container) |
|---|---|
| mean cycle | ≈ 13 ms |
| p50 | ≈ 9 ms |
| p99 | ≈ 110 ms |
| Floor asserted in CI | 50 ms mean (slack for slow runners) |

The design-doc methodology (1 000 random-uniform poses over full joint
ranges) is the tier-2 characterisation target; the in-CI benchmark
uses a smooth pseudo-trajectory with `LastSolutionSeed`, which is what
real KRL programs produce. Tighten the asserted floor when a stable
dedicated runner is provisioned (`KRL-INTERPRETER-BENCHMARK-CI` row).

### Dependencies

| Package | Version | Licence |
|---|---|---|
| `ikpy` | `>= 3.4` | Apache-2.0 |
| `numpy` | `>= 1.24` | BSD-3 |
| `scipy` | `>= 1.10` | BSD-3 (transitive of `ikpy`, made explicit for Euler conversion) |

No GPL / AGPL / SSPL deps; all transitives clear per design-doc §2.2.
