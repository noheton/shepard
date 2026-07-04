"""Tier-1 flow control: IF / FOR / WHILE / LOOP.

Three tests per construct covering happy-path, nested-body, and empty
or single-statement edge cases.
"""

from __future__ import annotations

from krl_interpreter import parse
from krl_interpreter.parser.ir import (
    Exit,
    For,
    If,
    Loop,
    Motion,
    UnsupportedConstruct,
    While,
)


def _stmts(src: str):
    return parse(src).program.statements


# --------------------------------------------------------------------- #
# IF / THEN / ELSE / ENDIF.
# --------------------------------------------------------------------- #


def test_if_then_only():
    src = (
        "DEF p()\n"
        "IF x > 0 THEN\n"
        "  PTP {X 100, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ENDIF\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, If)
    assert node.condition == "x>0"
    assert len(node.then_block) == 1 and isinstance(node.then_block[0], Motion)
    assert node.else_block == []


def test_if_with_else_branch():
    src = (
        "DEF p()\n"
        "IF x == 5 THEN\n"
        "  PTP {X 100, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ELSE\n"
        "  LIN {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ENDIF\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, If)
    assert len(node.then_block) == 1
    assert len(node.else_block) == 1


def test_nested_if():
    src = (
        "DEF p()\n"
        "IF a > 0 THEN\n"
        "  IF b > 0 THEN\n"
        "    PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "  ENDIF\n"
        "ENDIF\n"
        "END\n"
    )
    [outer] = _stmts(src)
    assert isinstance(outer, If)
    inner = outer.then_block[0]
    assert isinstance(inner, If)
    assert inner.condition == "b>0"


# --------------------------------------------------------------------- #
# FOR / ENDFOR.
# --------------------------------------------------------------------- #


def test_for_with_step():
    src = (
        "DEF p()\n"
        "FOR i = 1 TO 10 STEP 2\n"
        "  PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ENDFOR\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, For)
    assert node.var == "i"
    assert node.start == "1" and node.end == "10" and node.step == "2"


def test_for_without_step():
    src = (
        "DEF p()\n"
        "FOR j = 0 TO 5\n"
        "  LIN {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ENDFOR\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, For)
    assert node.step is None


def test_nested_for_double_loop():
    src = (
        "DEF p()\n"
        "FOR i = 1 TO 3\n"
        "  FOR j = 1 TO 4\n"
        "    PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "  ENDFOR\n"
        "ENDFOR\n"
        "END\n"
    )
    [outer] = _stmts(src)
    assert isinstance(outer, For)
    inner = outer.body[0]
    assert isinstance(inner, For)
    assert inner.var == "j"


# --------------------------------------------------------------------- #
# WHILE / ENDWHILE.
# --------------------------------------------------------------------- #


def test_while_basic():
    src = (
        "DEF p()\n"
        "WHILE counter < 10\n"
        "  PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ENDWHILE\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, While)
    assert node.condition == "counter<10"
    assert len(node.body) == 1


def test_while_compound_condition():
    src = (
        "DEF p()\n"
        "WHILE a == 1 AND b == 2\n"
        "  LIN {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ENDWHILE\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, While)
    assert "AND" in node.condition.upper()


def test_while_inside_if():
    src = (
        "DEF p()\n"
        "IF safe THEN\n"
        "  WHILE busy\n"
        "    PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "  ENDWHILE\n"
        "ENDIF\n"
        "END\n"
    )
    [outer] = _stmts(src)
    assert isinstance(outer, If)
    inner = outer.then_block[0]
    assert isinstance(inner, While)


# --------------------------------------------------------------------- #
# LOOP / ENDLOOP + EXIT.
# --------------------------------------------------------------------- #


def test_loop_with_exit():
    src = (
        "DEF p()\n"
        "LOOP\n"
        "  PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "  EXIT\n"
        "ENDLOOP\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, Loop)
    assert len(node.body) == 2
    assert isinstance(node.body[1], Exit)


def test_loop_without_exit_still_parses():
    # Tier-1: trajectory layer will cap unrolls; parser shouldn't care.
    src = (
        "DEF p()\n"
        "LOOP\n"
        "  LIN {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
        "ENDLOOP\n"
        "END\n"
    )
    [node] = _stmts(src)
    assert isinstance(node, Loop)
    assert not any(isinstance(s, Exit) for s in node.body)


def test_loop_with_nested_if_exit():
    src = (
        "DEF p()\n"
        "LOOP\n"
        "  IF done THEN\n"
        "    EXIT\n"
        "  ENDIF\n"
        "ENDLOOP\n"
        "END\n"
    )
    [loop] = _stmts(src)
    assert isinstance(loop, Loop)
    inner_if = loop.body[0]
    assert isinstance(inner_if, If)
    assert isinstance(inner_if.then_block[0], Exit)


# --------------------------------------------------------------------- #
# Unsupported constructs surface here too (interleaved with flow).
# --------------------------------------------------------------------- #


def test_interrupt_recorded_as_unsupported():
    src = "DEF p()\nINTERRUPT ON 17\nEND\n"
    result = parse(src)
    unsupported = result.unsupported
    assert len(unsupported) == 1
    assert unsupported[0].construct == "INTERRUPT"
    # Also surfaced as a top-level statement of type UnsupportedConstruct.
    assert any(
        isinstance(s, UnsupportedConstruct) for s in result.program.statements
    )
