"""Frame literals + $BASE / $TOOL switching."""

from __future__ import annotations

from krl_interpreter import parse
from krl_interpreter.parser.ir import (
    BaseToolSwitch,
    FrameLiteral,
    FrameTarget,
)


def _stmts(src: str):
    return parse(src).program.statements


# --------------------------------------------------------------------- #
# Frame literal shapes.
# --------------------------------------------------------------------- #


def test_full_frame_literal_on_base_switch():
    src = "DEF p()\n$BASE = {X 100, Y 200, Z 300, A 10, B 20, C 30}\nEND\n"
    [sw] = _stmts(src)
    assert isinstance(sw, BaseToolSwitch)
    assert sw.target is FrameTarget.BASE
    assert isinstance(sw.frame, FrameLiteral)
    assert sw.frame.x == 100 and sw.frame.c == 30


def test_sparse_frame_literal_defaults_missing_to_zero():
    src = "DEF p()\n$TOOL = {X 50, Z 100}\nEND\n"
    [sw] = _stmts(src)
    assert isinstance(sw, BaseToolSwitch)
    assert sw.frame.x == 50 and sw.frame.z == 100
    # Y, A, B, C all default to zero.
    assert sw.frame.y == 0.0
    assert sw.frame.a == 0.0 and sw.frame.b == 0.0 and sw.frame.c == 0.0
    assert "_missing_fields" in sw.frame.extras


# --------------------------------------------------------------------- #
# $BASE / $TOOL switch targeting.
# --------------------------------------------------------------------- #


def test_base_switch_target_enum():
    src = "DEF p()\n$BASE = {X 0, Y 0, Z 0, A 0, B 0, C 0}\nEND\n"
    [sw] = _stmts(src)
    assert sw.target is FrameTarget.BASE


def test_tool_switch_target_enum():
    src = "DEF p()\n$TOOL = {X 0, Y 0, Z 100, A 0, B 0, C 0}\nEND\n"
    [sw] = _stmts(src)
    assert sw.target is FrameTarget.TOOL
