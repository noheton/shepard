"""Composer-level tests (IR -> trajectory).

Walks small synthetic IR programs (constructed in-test or built from
the parser on the existing fixtures) through :class:`Composer` and
asserts the trajectory shape, warnings, and IK stats.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from krl_interpreter import parse
from krl_interpreter.ik import IkSolver
from krl_interpreter.parser.ir import (
    Assign,
    BaseToolSwitch,
    E6PosLiteral,
    FrameLiteral,
    FrameTarget,
    For,
    If,
    Loop,
    Motion,
    MotionKind,
    Program,
    UnsupportedConstruct,
    VarRef,
    Wait,
    While,
)
from krl_interpreter.sidecar.composer import (
    Composer,
    ComposerOptions,
)

FIXTURES = Path(__file__).parent / "ik_fixtures"


@pytest.fixture
def solver(two_link_urdf: str) -> IkSolver:
    return IkSolver(two_link_urdf)


# 1
def test_empty_program_produces_empty_trajectory(solver: IkSolver) -> None:
    program = Program()
    composer = Composer(solver=solver)
    result = composer.compose(program)
    assert result.trajectory == []
    assert result.total_poses == 0
    assert result.warnings == []


# 2
def test_single_ptp_produces_samples(solver: IkSolver) -> None:
    """Single PTP to a reachable XY target (planar arm — z must be 0)."""
    # Target = (1.0, 0.0, 0.0) m → KRL frame (1000, 0, 0) mm
    program = Program(
        statements=[
            Motion(
                line=1,
                kind=MotionKind.PTP,
                target=FrameLiteral(x=1000.0, y=0.0, z=0.0),
            )
        ]
    )
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.1, motion_duration=1.0),
    )
    result = composer.compose(program)
    # ceil(1.0 / 0.1) = 10 samples
    assert len(result.trajectory) == 10
    assert result.total_poses == 1
    # Trajectory clock increments by time_step.
    for i, pt in enumerate(result.trajectory):
        assert pt.t == pytest.approx((i + 1) * 0.1, abs=1e-9)


# 3
def test_for_loop_unrolls_at_compile_time(solver: IkSolver) -> None:
    """FOR 1 TO 3 should unroll to 3 motion iterations."""
    program = Program(
        statements=[
            For(
                line=1,
                var="i",
                start="1",
                end="3",
                step=None,
                body=[
                    Motion(
                        line=2,
                        kind=MotionKind.PTP,
                        target=FrameLiteral(x=500.0, y=0.0, z=0.0),
                    )
                ],
            )
        ]
    )
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.5, motion_duration=1.0),
    )
    result = composer.compose(program)
    # 3 iterations × ceil(1.0/0.5)=2 samples = 6
    assert len(result.trajectory) == 6
    assert result.total_poses == 3


# 4
def test_multi_motion_sequence_advances_clock(solver: IkSolver) -> None:
    program = Program(
        statements=[
            Motion(line=1, kind=MotionKind.PTP, target=FrameLiteral(x=1000.0)),
            Motion(line=2, kind=MotionKind.LIN, target=FrameLiteral(x=500.0)),
        ]
    )
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.5, motion_duration=1.0),
    )
    result = composer.compose(program)
    # 2 motions × 2 samples each
    assert len(result.trajectory) == 4
    # Clock at end ~= 2.0s
    assert result.trajectory[-1].t == pytest.approx(2.0, abs=1e-9)


# 5
def test_base_switch_changes_world_target(solver: IkSolver) -> None:
    """$BASE switch should change the resolved world target.

    Effect: same PTP frame, different $BASE → different IK target →
    different joint solution. We don't check the actual joint values
    (those depend on chain layout); we just check the composer
    accepted the BaseToolSwitch and emitted samples.
    """
    program = Program(
        statements=[
            BaseToolSwitch(
                line=1,
                target=FrameTarget.BASE,
                frame=FrameLiteral(x=200.0, y=0.0, z=0.0),
            ),
            Motion(
                line=2,
                kind=MotionKind.PTP,
                target=FrameLiteral(x=500.0, y=0.0, z=0.0),
            ),
        ]
    )
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.5, motion_duration=1.0),
    )
    result = composer.compose(program)
    assert result.total_poses == 1
    assert len(result.trajectory) == 2


# 6
def test_wait_advances_clock(solver: IkSolver) -> None:
    """WAIT SEC should advance the clock without IK solves."""
    program = Program(
        statements=[
            Motion(line=1, kind=MotionKind.PTP, target=FrameLiteral(x=1000.0)),
            Wait(line=2, seconds=0.5),
            Motion(line=3, kind=MotionKind.PTP, target=FrameLiteral(x=500.0)),
        ]
    )
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.25, motion_duration=1.0),
    )
    result = composer.compose(program)
    # motion(4 samples) + wait(2 samples) + motion(4 samples) = 10
    assert len(result.trajectory) == 10
    assert result.total_poses == 2  # wait doesn't IK


# 7
def test_unsupported_construct_passes_through(solver: IkSolver) -> None:
    program = Program(
        statements=[
            UnsupportedConstruct(
                line=5,
                construct="INTERRUPT",
                reason="tier-1 unsupported",
                raw_text="INTERRUPT ON 17",
            )
        ]
    )
    composer = Composer(solver=solver)
    result = composer.compose(program)
    assert len(result.unsupported_constructs) == 1
    assert result.unsupported_constructs[0].construct == "INTERRUPT"
    assert result.unsupported_constructs[0].line == 5


# 8
def test_unreachable_target_warns_and_continues(solver: IkSolver) -> None:
    """A target outside the planar arm's reach should produce a warning
    + failed_poses count but the composer continues to the next motion."""
    program = Program(
        statements=[
            # Reach is ~2.0m radius; (10, 0, 0) is way out.
            Motion(
                line=1,
                kind=MotionKind.PTP,
                target=FrameLiteral(x=10000.0, y=0.0, z=0.0),
            ),
            Motion(
                line=2,
                kind=MotionKind.PTP,
                target=FrameLiteral(x=1000.0, y=0.0, z=0.0),
            ),
        ]
    )
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.5, motion_duration=1.0),
    )
    result = composer.compose(program)
    # First motion failed but second succeeded.
    assert result.total_poses == 2
    assert result.failed_poses >= 1
    # Composer surfaced an IK warning.
    assert any("unreachable" in w.message.lower() for w in result.warnings)
    # Trajectory still emitted for both motions.
    assert len(result.trajectory) == 4


# Bonus: non-literal IF surfaces a warning (covers the warning branch).
def test_if_non_literal_warns_and_skips(solver: IkSolver) -> None:
    program = Program(
        statements=[
            If(
                line=1,
                condition="is_first_pass",
                then_block=[
                    Motion(line=2, kind=MotionKind.PTP, target=FrameLiteral(x=1000.0))
                ],
                else_block=[
                    Motion(line=3, kind=MotionKind.LIN, target=FrameLiteral(x=500.0))
                ],
            )
        ]
    )
    composer = Composer(solver=solver)
    result = composer.compose(program)
    # No motions emitted; warning surfaced.
    assert result.total_poses == 0
    assert any("IF condition" in w.message for w in result.warnings)


# --- Branch-coverage bonus tests -------------------------------------

def test_if_literal_true_walks_then_block(solver: IkSolver) -> None:
    program = Program(
        statements=[
            If(
                line=1,
                condition="TRUE",
                then_block=[Motion(line=2, kind=MotionKind.PTP, target=FrameLiteral(x=500.0))],
                else_block=[],
            )
        ]
    )
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.5, motion_duration=1.0),
    )
    result = composer.compose(program)
    assert result.total_poses == 1


def test_if_literal_false_walks_else_block(solver: IkSolver) -> None:
    program = Program(
        statements=[
            If(
                line=1,
                condition="false",  # case-insensitive
                then_block=[],
                else_block=[Motion(line=3, kind=MotionKind.PTP, target=FrameLiteral(x=500.0))],
            )
        ]
    )
    composer = Composer(solver=solver, options=ComposerOptions(time_step=0.5))
    result = composer.compose(program)
    assert result.total_poses == 1


def test_for_non_integer_bounds_warns(solver: IkSolver) -> None:
    program = Program(
        statements=[
            For(
                line=1,
                var="i",
                start="foo",
                end="bar",
                step=None,
                body=[Motion(line=2, kind=MotionKind.PTP, target=FrameLiteral(x=500.0))],
            )
        ]
    )
    composer = Composer(solver=solver)
    result = composer.compose(program)
    assert result.total_poses == 0
    assert any("FOR bounds" in w.message for w in result.warnings)


def test_for_with_step(solver: IkSolver) -> None:
    program = Program(
        statements=[
            For(
                line=1,
                var="i",
                start="0",
                end="4",
                step="2",  # 0, 2, 4 → 3 iterations
                body=[Motion(line=2, kind=MotionKind.PTP, target=FrameLiteral(x=500.0))],
            )
        ]
    )
    composer = Composer(solver=solver, options=ComposerOptions(time_step=0.5))
    result = composer.compose(program)
    assert result.total_poses == 3


def test_while_skips_with_warning(solver: IkSolver) -> None:
    program = Program(
        statements=[
            While(
                line=1,
                condition="i < 10",
                body=[Motion(line=2, kind=MotionKind.PTP, target=FrameLiteral(x=500.0))],
            )
        ]
    )
    composer = Composer(solver=solver)
    result = composer.compose(program)
    assert result.total_poses == 0
    assert any("WHILE" in w.message for w in result.warnings)


def test_loop_unrolls_once_with_warning(solver: IkSolver) -> None:
    program = Program(
        statements=[
            Loop(
                line=1,
                body=[Motion(line=2, kind=MotionKind.PTP, target=FrameLiteral(x=500.0))],
            )
        ]
    )
    composer = Composer(solver=solver, options=ComposerOptions(time_step=0.5))
    result = composer.compose(program)
    # One pass through the body.
    assert result.total_poses == 1
    assert any("LOOP" in w.message for w in result.warnings)


def test_assign_and_vardecl_silently_noop(solver: IkSolver) -> None:
    program = Program(
        statements=[
            Assign(line=1, var="i", expr="5"),
            Motion(line=2, kind=MotionKind.PTP, target=FrameLiteral(x=500.0)),
        ]
    )
    composer = Composer(solver=solver, options=ComposerOptions(time_step=0.5))
    result = composer.compose(program)
    assert result.total_poses == 1
    assert result.warnings == []  # Assign is silent


def test_var_ref_target_warns(solver: IkSolver) -> None:
    program = Program(
        statements=[
            Motion(line=1, kind=MotionKind.PTP, target=VarRef(name="home")),
        ]
    )
    composer = Composer(solver=solver)
    result = composer.compose(program)
    assert result.total_poses == 0
    assert any("variable reference" in w.message for w in result.warnings)


def test_wait_condition_warns(solver: IkSolver) -> None:
    program = Program(
        statements=[
            Wait(line=1, condition="$IN[1]"),
        ]
    )
    composer = Composer(solver=solver)
    result = composer.compose(program)
    assert any("WAIT FOR" in w.message for w in result.warnings)


def test_tool_switch_stored_but_not_applied(solver: IkSolver) -> None:
    """$TOOL is captured but does not affect the IK target at tier-1."""
    program = Program(
        statements=[
            BaseToolSwitch(
                line=1,
                target=FrameTarget.TOOL,
                frame=FrameLiteral(x=0, y=0, z=200),
            ),
        ]
    )
    composer = Composer(solver=solver)
    result = composer.compose(program)
    assert result.total_poses == 0
    # tool_frame stored.
    assert composer.tool_frame[2, 3] != 0.0


def test_e6pos_literal_resolves(solver: IkSolver) -> None:
    program = Program(
        statements=[
            Motion(
                line=1,
                kind=MotionKind.PTP,
                target=E6PosLiteral(frame=FrameLiteral(x=500.0)),
            )
        ]
    )
    composer = Composer(solver=solver, options=ComposerOptions(time_step=0.5))
    result = composer.compose(program)
    assert result.total_poses == 1


def test_initial_seed_used_for_first_solve(solver: IkSolver) -> None:
    """Providing an initial_seed sets last_joints so empty programs still
    emit one trajectory point."""
    seed = [0.0] * solver.chain_length
    program = Program()  # no statements
    composer = Composer(solver=solver, initial_seed=seed)
    result = composer.compose(program)
    # Empty program with seed → one t=0 point with the seed values.
    assert len(result.trajectory) == 1
    assert result.trajectory[0].t == 0.0


# Bonus: parsed ply5_layup.src runs end-to-end (smoke).
def test_ply5_fixture_parses_and_composes(solver: IkSolver) -> None:
    fixture = Path(__file__).parent / "sidecar_fixtures" / "ply5_layup.src"
    text = fixture.read_text()
    result = parse(text)
    composer = Composer(
        solver=solver,
        options=ComposerOptions(time_step=0.5, motion_duration=1.0),
    )
    out = composer.compose(result.program)
    # At least one motion got solved; INTERRUPT surfaced as unsupported.
    assert out.total_poses >= 1
    assert any(u.construct == "INTERRUPT" for u in out.unsupported_constructs)
