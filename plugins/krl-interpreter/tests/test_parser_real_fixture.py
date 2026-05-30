"""End-to-end parse of the synthetic MFFD-style fixture.

Mirrors the §14 acceptance test from ``aidocs/integrations/117``:
the fixture exercises every tier-1 construct in one program.
"""

from __future__ import annotations

from pathlib import Path

from krl_interpreter import parse
from krl_interpreter.errors import Severity
from krl_interpreter.parser.ir import (
    BaseToolSwitch,
    For,
    If,
    Motion,
    MotionKind,
    UnsupportedConstruct,
    Wait,
)


def _load(name: str) -> str:
    return (Path(__file__).parent / "fixtures" / name).read_text()


def test_hello_world_smoke():
    result = parse(_load("hello_world.src"))
    p = result.program
    assert p.module_name == "hello_world"
    assert p.is_function is False
    assert p.is_data_file is False
    kinds = [s.kind for s in p.statements if isinstance(s, Motion)]
    assert kinds == [MotionKind.PTP, MotionKind.LIN]
    # WAIT SEC 1 between them.
    assert any(isinstance(s, Wait) and s.seconds == 1.0 for s in p.statements)


def test_layup_loop_fixture_nested_for():
    result = parse(_load("layup_loop.src"))
    p = result.program
    [outer] = [s for s in p.statements if isinstance(s, For)]
    assert outer.var == "ply"
    inner = next(s for s in outer.body if isinstance(s, For))
    assert inner.var == "pass" and inner.step == "1"
    # Three motions per innermost body.
    motions = [s for s in inner.body if isinstance(s, Motion)]
    assert len(motions) == 3


def test_frame_switching_fixture_emits_switches():
    result = parse(_load("frame_switching.src"))
    p = result.program
    switches = [s for s in p.statements if isinstance(s, BaseToolSwitch)]
    assert len(switches) == 3  # 2x $BASE + 1x $TOOL


def test_ply5_layup_fixture_full_shape():
    result = parse(_load("ply5_layup.src"))
    p = result.program

    # The synthetic ply-5 fixture is a `DEF` (not `DEFFCT`).
    assert p.module_name == "Ply_5_layup"
    assert p.is_function is False

    # At the top level we expect: 2 $BASE/$TOOL switches, 3 VarDecls,
    # 1 Assign (ply = 5), 2 PTPs, 1 FOR, 1 UnsupportedConstruct.
    motion_total = 0
    if_total = 0

    def _count(stmts):
        nonlocal motion_total, if_total
        for s in stmts:
            if isinstance(s, Motion):
                motion_total += 1
            elif isinstance(s, If):
                if_total += 1
                _count(s.then_block)
                _count(s.else_block)
            elif isinstance(s, For):
                _count(s.body)

    _count(p.statements)

    # Parser does NOT unroll the FOR. Count is therefore:
    #   2 top-level PTPs (approach + park)
    # + 2 IF/ELSE branches (1 motion each, both branches are walked)
    # + 3 motions in the FOR body after the IF (LIN, CIRC, LIN)
    # = 7 distinct motion statements in the IR.
    assert motion_total == 7, f"expected 7 motions, got {motion_total}"
    assert if_total == 1

    # Unsupported INTERRUPT was captured.
    unsupported = result.unsupported
    assert any(u.construct == "INTERRUPT" for u in unsupported)

    # And a corresponding WARN landed for it.
    warns = [w for w in result.warnings if w.severity is Severity.WARN]
    assert any("INTERRUPT" in w.message for w in warns)
