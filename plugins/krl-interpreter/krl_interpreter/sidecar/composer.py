"""IR -> joint-trajectory composer.

The composer walks a parsed :class:`~krl_interpreter.parser.ir.Program`
linearly, mutates ``$BASE`` / ``$TOOL`` state during the walk, calls
the IK back-solver per Cartesian target, and emits a list of
:class:`TrajectoryPoint` (``t``, ``joints``).

Tier-1 (KRL-INTERPRETER-04) scope:

* PTP / LIN / CIRC all interpolate joints **linearly** in joint space
  from the previous solved pose to the current solved pose; full
  Cartesian interpolation of LIN / CIRC is deferred to tier-2 (the
  difference vanishes on small motion segments anyway).
* Each motion advances the trajectory clock by a fixed
  ``options.motion_duration`` (default 1.0 s); the number of samples
  per motion is ``ceil(motion_duration / time_step)``.
* ``Wait(seconds=...)`` advances the clock by ``seconds`` and holds
  the last joint vector for the duration.
* ``$BASE`` / ``$TOOL`` are mutated by :class:`BaseToolSwitch`
  statements during the walk. Target frames are composed as
  ``world_target = base @ pose`` (tier-1 ignores ``$TOOL`` rotation;
  this is documented in ``docs/reference.md`` §"Sidecar REST API").
* ``For`` is unrolled at compile time (KRL ``FOR`` bounds are static).
  Non-integer-literal bounds become a warning + skipped body.
* ``If`` tier-1 evaluates only literal ``TRUE`` / ``FALSE`` conditions;
  non-constant conditions are a warning + both branches are skipped.
* ``While`` / ``Loop`` are capped at ``options.max_iterations`` (the
  request-level safety cap), warning when the cap is hit.
* ``UnsupportedConstruct`` IR nodes are passed through to the result's
  ``unsupported_constructs`` list (no additional warning — the parser
  has already attached one).

KRL frames are in **millimetres**; the composer converts at the IR ->
IK boundary by multiplying ``x/y/z`` by ``UNIT_MM_TO_M`` (0.001).
Rotation values pass through unchanged (radians).
"""

from __future__ import annotations

import math
import time
from dataclasses import dataclass, field
from typing import List, Optional, Sequence

import numpy as np
from scipy.spatial.transform import Rotation

from krl_interpreter.ik import IkSolver, LastSolutionSeed, TargetPose
from krl_interpreter.parser.ir import (
    Assign,
    BaseToolSwitch,
    E6PosLiteral,
    Exit,
    For,
    FrameLiteral,
    FrameTarget,
    If,
    Loop,
    Motion,
    MotionKind,
    Program,
    Statement,
    UnsupportedConstruct,
    VarDecl,
    VarRef,
    Wait,
    While,
)

# KRL frames are in millimetres; URDFs in metres. Convert at the
# IR -> IK boundary so the IK solver never sees mm.
UNIT_MM_TO_M = 0.001


# ----------------------------------------------------------------- #
# Public data types
# ----------------------------------------------------------------- #


@dataclass
class TrajectoryPoint:
    """One sample on the emitted trajectory."""

    t: float
    joints: List[float]


@dataclass
class ComposerOptions:
    """Composer-level tunables. Maps 1:1 to ``InterpretOptions`` on the
    REST surface; defaults match :class:`schemas.InterpretOptions`."""

    time_step: float = 0.01
    motion_duration: float = 1.0
    max_iterations: int = 100  # IK-solver iteration cap (informational)
    ik_tolerance: float = 1e-3
    max_ir_iterations: int = 100000  # safety cap on WHILE / LOOP unroll


@dataclass
class ComposerWarning:
    """Composer-side warning. Lifted into the response body as an
    :class:`schemas.InterpretWarning`."""

    line: int
    message: str
    severity: str = "warning"  # "warning" | "error"


@dataclass
class ComposerResult:
    """Output of :meth:`Composer.compose`."""

    trajectory: List[TrajectoryPoint] = field(default_factory=list)
    warnings: List[ComposerWarning] = field(default_factory=list)
    unsupported_constructs: List[UnsupportedConstruct] = field(default_factory=list)
    # IK aggregate stats
    total_poses: int = 0
    failed_poses: int = 0
    mean_cycle_ms: float = 0.0
    max_residual: float = 0.0


# ----------------------------------------------------------------- #
# Frame math (mm -> m + ABC convention swap, tier-1 simplified)
# ----------------------------------------------------------------- #


def _frame_to_matrix(f: FrameLiteral) -> np.ndarray:
    """KRL ``FrameLiteral`` (mm + ZYX intrinsic ABC degrees-or-radians?) ->
    4x4 homogeneous transform (m + XYZ intrinsic radians).

    KRL convention: A,B,C are ZYX intrinsic in **radians** when stored
    on the IR (the parser surfaces raw values; the design doc §4.1 calls
    this out). Tier-1 the composer treats them as radians; if the input
    is degrees we get garbage — documented as a known pitfall in
    ``docs/reference.md``.
    """
    m = np.eye(4)
    m[:3, :3] = Rotation.from_euler("ZYX", [f.a, f.b, f.c]).as_matrix()
    m[:3, 3] = [f.x * UNIT_MM_TO_M, f.y * UNIT_MM_TO_M, f.z * UNIT_MM_TO_M]
    return m


def _frame_from_payload(x: float, y: float, z: float, rx: float, ry: float, rz: float) -> np.ndarray:
    """``FramePayload`` (m + radians, XYZ intrinsic) -> 4x4."""
    m = np.eye(4)
    m[:3, :3] = Rotation.from_euler("xyz", [rx, ry, rz]).as_matrix()
    m[:3, 3] = [x, y, z]
    return m


def _matrix_to_target_pose(m: np.ndarray) -> TargetPose:
    """4x4 -> :class:`TargetPose` (xyz + XYZ intrinsic euler)."""
    pos = m[:3, 3]
    rx, ry, rz = Rotation.from_matrix(m[:3, :3]).as_euler("xyz")
    return TargetPose(
        x=float(pos[0]),
        y=float(pos[1]),
        z=float(pos[2]),
        rx=float(rx),
        ry=float(ry),
        rz=float(rz),
    )


# ----------------------------------------------------------------- #
# Composer
# ----------------------------------------------------------------- #


class Composer:
    """IR walker that builds a joint trajectory by calling
    :class:`IkSolver` per Cartesian target.

    Stateful per-instance for the duration of one compose call:
    ``$BASE`` / ``$TOOL`` mutate during the walk; the seed strategy
    carries the previous IK solution forward.
    """

    def __init__(
        self,
        solver: IkSolver,
        options: Optional[ComposerOptions] = None,
        base_frame: Optional[np.ndarray] = None,
        tool_frame: Optional[np.ndarray] = None,
        initial_seed: Optional[Sequence[float]] = None,
    ):
        self.solver = solver
        self.options = options or ComposerOptions()
        self.base_frame = base_frame if base_frame is not None else np.eye(4)
        self.tool_frame = tool_frame if tool_frame is not None else np.eye(4)
        self.seed_strategy = LastSolutionSeed()
        if initial_seed is not None:
            self.seed_strategy.update(list(initial_seed))
        self._last_joints: Optional[List[float]] = (
            list(initial_seed) if initial_seed is not None else None
        )
        self._clock: float = 0.0
        self._cycle_times_ms: List[float] = []
        self._max_residual: float = 0.0
        self._failed_poses: int = 0
        self._total_poses: int = 0
        self._loop_iterations: int = 0  # IR-unroll safety counter

    # -- public ------------------------------------------------------

    def compose(self, program: Program) -> ComposerResult:
        result = ComposerResult()
        self._walk(program.statements, result)

        # If no motions touched the trajectory and we have a seed,
        # emit a single sample at t=0 with the seed (so the trajectory
        # is never empty even for an Wait-only program). This is an
        # intentional ergonomic call; tests verify it.
        if not result.trajectory and self._last_joints is not None:
            result.trajectory.append(
                TrajectoryPoint(t=0.0, joints=list(self._last_joints))
            )

        result.total_poses = self._total_poses
        result.failed_poses = self._failed_poses
        result.max_residual = self._max_residual
        if self._cycle_times_ms:
            result.mean_cycle_ms = sum(self._cycle_times_ms) / len(self._cycle_times_ms)
        return result

    # -- walker ------------------------------------------------------

    def _walk(self, statements: List[Statement], result: ComposerResult) -> None:
        for stmt in statements:
            if self._loop_iterations > self.options.max_ir_iterations:
                result.warnings.append(
                    ComposerWarning(
                        line=getattr(stmt, "line", 0),
                        message=(
                            f"IR unroll cap {self.options.max_ir_iterations} reached;"
                            " aborting walk"
                        ),
                        severity="error",
                    )
                )
                return
            self._dispatch(stmt, result)

    def _dispatch(self, stmt: Statement, result: ComposerResult) -> None:
        if isinstance(stmt, Motion):
            self._do_motion(stmt, result)
        elif isinstance(stmt, Wait):
            self._do_wait(stmt, result)
        elif isinstance(stmt, BaseToolSwitch):
            self._do_base_tool_switch(stmt, result)
        elif isinstance(stmt, For):
            self._do_for(stmt, result)
        elif isinstance(stmt, If):
            self._do_if(stmt, result)
        elif isinstance(stmt, While):
            self._do_while(stmt, result)
        elif isinstance(stmt, Loop):
            self._do_loop(stmt, result)
        elif isinstance(stmt, UnsupportedConstruct):
            result.unsupported_constructs.append(stmt)
        elif isinstance(stmt, (Assign, VarDecl, Exit)):
            # Tier-1: silently no-op. The expression evaluator is a
            # tier-2 concern; KRL programs we care about today only
            # use Assign/VarDecl for FOR-loop counters which we don't
            # need to honour (FOR uses static bounds).
            return
        else:
            # Defensive: an IR node type the composer doesn't recognise.
            # Surface as warning, do not crash.
            result.warnings.append(
                ComposerWarning(
                    line=getattr(stmt, "line", 0),
                    message=f"unhandled IR statement: {type(stmt).__name__}",
                    severity="warning",
                )
            )

    # -- motion ------------------------------------------------------

    def _resolve_target(self, target, line: int, result: ComposerResult) -> Optional[FrameLiteral]:
        """Resolve a Pose IR node to a :class:`FrameLiteral` in the
        current frame context. Returns ``None`` if unresolvable.
        """
        if isinstance(target, FrameLiteral):
            return target
        if isinstance(target, E6PosLiteral):
            return target.frame
        if isinstance(target, VarRef):
            result.warnings.append(
                ComposerWarning(
                    line=line,
                    message=(
                        f"variable reference '{target.name}' as motion target"
                        " not resolvable (tier-1 has no expression evaluator)"
                    ),
                )
            )
            return None
        result.warnings.append(
            ComposerWarning(
                line=line,
                message=f"unknown pose type {type(target).__name__}",
            )
        )
        return None

    def _do_motion(self, stmt: Motion, result: ComposerResult) -> None:
        target_frame = self._resolve_target(stmt.target, stmt.line, result)
        if target_frame is None:
            return

        # Compose world target: world = base @ pose. (Tool integration
        # deferred — tier-1 docs document this.)
        pose_matrix = _frame_to_matrix(target_frame)
        world_matrix = self.base_frame @ pose_matrix
        target_pose = _matrix_to_target_pose(world_matrix)

        # IK solve with seed = last solution.
        seed = self.seed_strategy.next_seed(self.solver.chain_length)
        t0 = time.perf_counter()
        ik_result = self.solver.solve(
            target_pose,
            seed=seed,
            tolerance=self.options.ik_tolerance,
            max_iterations=self.options.max_iterations,
        )
        cycle_ms = (time.perf_counter() - t0) * 1000.0
        self._cycle_times_ms.append(cycle_ms)
        self._total_poses += 1
        if ik_result.residual > self._max_residual and math.isfinite(ik_result.residual):
            self._max_residual = ik_result.residual

        # IK warnings -> composer warnings.
        for w in ik_result.warnings:
            result.warnings.append(
                ComposerWarning(
                    line=stmt.line,
                    message=f"{w.kind}: {w.message}",
                    severity="warning" if w.severity != "ERROR" else "error",
                )
            )

        if not ik_result.converged:
            # Hold previous joints; advance the clock anyway so motion
            # ordering stays consistent.
            self._failed_poses += 1
            self._emit_hold(stmt.kind, result)
            return

        # Joint-space interpolation from previous joints to new.
        self._emit_interpolated(ik_result.joints, result)
        self.seed_strategy.update(ik_result.joints)
        self._last_joints = list(ik_result.joints)

    def _emit_hold(self, kind: MotionKind, result: ComposerResult) -> None:
        """Failed IK: hold previous joints (or zeros) across the motion
        duration."""
        duration = self.options.motion_duration
        step = self.options.time_step
        n_samples = max(1, int(math.ceil(duration / step)))
        last = self._last_joints if self._last_joints is not None else [0.0] * self.solver.chain_length
        for i in range(n_samples):
            t = self._clock + (i + 1) * step
            result.trajectory.append(TrajectoryPoint(t=t, joints=list(last)))
        self._clock += n_samples * step
        # Document with one warning.
        _ = kind

    def _emit_interpolated(self, target_joints: List[float], result: ComposerResult) -> None:
        """Linear interpolation in joint space from prev joints to target,
        emitting ``ceil(motion_duration / time_step)`` samples."""
        duration = self.options.motion_duration
        step = self.options.time_step
        n_samples = max(1, int(math.ceil(duration / step)))
        prev = self._last_joints if self._last_joints is not None else list(target_joints)

        # If chain length changed (defensive), reset prev.
        if len(prev) != len(target_joints):
            prev = list(target_joints)

        for i in range(1, n_samples + 1):
            alpha = i / n_samples
            interp = [
                p + alpha * (t - p) for p, t in zip(prev, target_joints)
            ]
            t = self._clock + i * step
            result.trajectory.append(TrajectoryPoint(t=t, joints=interp))
        self._clock += n_samples * step

    # -- wait --------------------------------------------------------

    def _do_wait(self, stmt: Wait, result: ComposerResult) -> None:
        if stmt.condition is not None and stmt.seconds is None:
            result.warnings.append(
                ComposerWarning(
                    line=stmt.line,
                    message=(
                        f"WAIT FOR <{stmt.condition}> not supported offline;"
                        " treating as zero-duration"
                    ),
                )
            )
            return
        seconds = stmt.seconds or 0.0
        if seconds <= 0.0:
            return
        # Hold the last joints for `seconds`.
        step = self.options.time_step
        n_samples = max(1, int(math.ceil(seconds / step)))
        last = self._last_joints if self._last_joints is not None else [0.0] * self.solver.chain_length
        for i in range(1, n_samples + 1):
            t = self._clock + i * step
            result.trajectory.append(TrajectoryPoint(t=t, joints=list(last)))
        self._clock += n_samples * step

    # -- frame switches ---------------------------------------------

    def _do_base_tool_switch(self, stmt: BaseToolSwitch, result: ComposerResult) -> None:
        frame = self._resolve_target(stmt.frame, stmt.line, result)
        if frame is None:
            return
        m = _frame_to_matrix(frame)
        if stmt.target == FrameTarget.BASE:
            self.base_frame = m
        elif stmt.target == FrameTarget.TOOL:
            # Tier-1: stored but not applied to the IK target (documented).
            self.tool_frame = m

    # -- control flow -----------------------------------------------

    @staticmethod
    def _try_parse_int(s: str) -> Optional[int]:
        try:
            return int(s.strip())
        except (ValueError, AttributeError):
            return None

    def _do_for(self, stmt: For, result: ComposerResult) -> None:
        start = self._try_parse_int(stmt.start)
        end = self._try_parse_int(stmt.end)
        step_val = self._try_parse_int(stmt.step) if stmt.step else 1
        if start is None or end is None or step_val is None or step_val == 0:
            result.warnings.append(
                ComposerWarning(
                    line=stmt.line,
                    message=(
                        f"FOR bounds not integer-literal "
                        f"(start={stmt.start!r}, end={stmt.end!r}, step={stmt.step!r});"
                        " skipping loop body"
                    ),
                )
            )
            return
        # KRL FOR is inclusive on both ends.
        rng = range(start, end + (1 if step_val > 0 else -1), step_val)
        for _ in rng:
            self._loop_iterations += 1
            if self._loop_iterations > self.options.max_ir_iterations:
                result.warnings.append(
                    ComposerWarning(
                        line=stmt.line,
                        message=(
                            f"FOR cap {self.options.max_ir_iterations} reached;"
                            " stopping unroll"
                        ),
                        severity="error",
                    )
                )
                return
            self._walk(stmt.body, result)

    def _do_if(self, stmt: If, result: ComposerResult) -> None:
        cond = (stmt.condition or "").strip().upper()
        if cond == "TRUE":
            self._walk(stmt.then_block, result)
        elif cond == "FALSE":
            self._walk(stmt.else_block, result)
        else:
            result.warnings.append(
                ComposerWarning(
                    line=stmt.line,
                    message=(
                        f"IF condition '{stmt.condition}' not literal TRUE/FALSE;"
                        " tier-1 skips both branches"
                    ),
                )
            )

    def _do_while(self, stmt: While, result: ComposerResult) -> None:
        # Tier-1 has no expression evaluator. We cap at max_iterations
        # but in practice we won't iterate at all because we can't
        # evaluate the condition. Surface a warning + skip.
        result.warnings.append(
            ComposerWarning(
                line=stmt.line,
                message=(
                    f"WHILE '{stmt.condition}' not evaluable offline;"
                    " tier-1 skips body"
                ),
            )
        )

    def _do_loop(self, stmt: Loop, result: ComposerResult) -> None:
        # Bounded LOOP unroll — same as WHILE we can't evaluate
        # EXIT conditions, so tier-1 unrolls once and surfaces a
        # warning. Real LOOPs in KRL programs would saturate the cap;
        # this is conservative.
        cap = self.options.max_iterations
        for i in range(cap):
            self._loop_iterations += 1
            if self._loop_iterations > self.options.max_ir_iterations:
                result.warnings.append(
                    ComposerWarning(
                        line=stmt.line,
                        message=(
                            f"LOOP cap {self.options.max_ir_iterations} reached;"
                            " stopping unroll"
                        ),
                        severity="error",
                    )
                )
                return
            self._walk(stmt.body, result)
            if i == 0:
                result.warnings.append(
                    ComposerWarning(
                        line=stmt.line,
                        message=(
                            "LOOP / EXIT semantics not evaluated offline;"
                            f" tier-1 unrolls once (cap {cap})"
                        ),
                    )
                )
            # Tier-1: bail after one pass; no way to evaluate EXIT.
            return
