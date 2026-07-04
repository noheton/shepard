"""Tier-1 motion primitives: PTP / LIN / CIRC / WAIT.

Each primitive has at least one happy-path test + an edge case
(rel variant, sparse frame, named-pose, etc.). ~5 tests per primitive.
"""

from __future__ import annotations

import pytest

from krl_interpreter import parse
from krl_interpreter.errors import Severity
from krl_interpreter.parser.ir import (
    E6PosLiteral,
    FrameLiteral,
    Motion,
    MotionKind,
    VarRef,
    Wait,
)


def _first_motion(src: str) -> Motion:
    result = parse(src)
    return next(s for s in result.program.statements if isinstance(s, Motion))


# --------------------------------------------------------------------- #
# PTP.
# --------------------------------------------------------------------- #


def test_ptp_full_frame_literal():
    src = "DEF p()\nPTP {X 100, Y 50, Z 200, A 10, B 20, C 30}\nEND\n"
    m = _first_motion(src)
    assert m.kind is MotionKind.PTP
    assert isinstance(m.target, FrameLiteral)
    assert (m.target.x, m.target.y, m.target.z) == (100, 50, 200)
    assert (m.target.a, m.target.b, m.target.c) == (10, 20, 30)


def test_ptp_named_pose_target():
    src = "DEF p()\nPTP home_pose\nEND\n"
    m = _first_motion(src)
    assert isinstance(m.target, VarRef)
    assert m.target.name == "home_pose"


def test_ptp_rel_distinct_kind():
    src = "DEF p()\nPTP_REL {X 10, Y 0, Z 0, A 0, B 0, C 0}\nEND\n"
    m = _first_motion(src)
    assert m.kind is MotionKind.PTP_REL


def test_ptp_with_motion_opts():
    src = "DEF p()\nPTP {X 0, Y 0, Z 0, A 0, B 0, C 0} C_DIS\nEND\n"
    m = _first_motion(src)
    assert m.opts == ["C_DIS"]


def test_ptp_sparse_frame_warns_info():
    src = "DEF p()\nPTP {X 100, Z 200}\nEND\n"
    result = parse(src)
    sparse_warnings = [w for w in result.warnings if w.severity is Severity.INFO]
    assert sparse_warnings, "sparse frame literal must emit an INFO warning"
    # Y/A/B/C default to 0.
    target = result.program.statements[0].target
    assert target.x == 100 and target.z == 200
    assert target.y == 0 and target.a == 0


# --------------------------------------------------------------------- #
# LIN.
# --------------------------------------------------------------------- #


def test_lin_basic():
    src = "DEF p()\nLIN {X 1, Y 2, Z 3, A 4, B 5, C 6}\nEND\n"
    m = _first_motion(src)
    assert m.kind is MotionKind.LIN
    assert m.target.x == 1 and m.target.c == 6


def test_lin_rel_variant():
    src = "DEF p()\nLIN_REL {X 10, Y 0, Z 0, A 0, B 0, C 0}\nEND\n"
    m = _first_motion(src)
    assert m.kind is MotionKind.LIN_REL


def test_lin_with_named_target_and_opts():
    src = "DEF p()\nLIN pickup_point C_DIS C_PTP\nEND\n"
    m = _first_motion(src)
    assert isinstance(m.target, VarRef)
    assert m.target.name == "pickup_point"
    assert m.opts == ["C_DIS", "C_PTP"]


def test_lin_e6pos_with_external_axes():
    src = (
        "DEF p()\n"
        "LIN {X 100, Y 0, Z 0, A 0, B 0, C 0, E1 45, E2 -10}\n"
        "END\n"
    )
    m = _first_motion(src)
    assert isinstance(m.target, E6PosLiteral)
    assert m.target.e1 == 45 and m.target.e2 == -10
    assert m.target.e3 is None
    assert m.target.frame.x == 100


def test_lin_negative_coords():
    src = "DEF p()\nLIN {X -100, Y -50, Z 200, A 0, B 0, C 0}\nEND\n"
    m = _first_motion(src)
    assert m.target.x == -100 and m.target.y == -50


# --------------------------------------------------------------------- #
# CIRC.
# --------------------------------------------------------------------- #


def test_circ_has_aux_and_target():
    src = (
        "DEF p()\n"
        "CIRC {X 100, Y 100, Z 0, A 0, B 0, C 0}, {X 200, Y 0, Z 0, A 0, B 0, C 0}\n"
        "END\n"
    )
    m = _first_motion(src)
    assert m.kind is MotionKind.CIRC
    assert m.aux is not None and m.target is not None
    assert m.aux.x == 100 and m.target.x == 200


def test_circ_rel_variant():
    src = (
        "DEF p()\n"
        "CIRC_REL {X 50, Y 50, Z 0, A 0, B 0, C 0}, {X 100, Y 0, Z 0, A 0, B 0, C 0}\n"
        "END\n"
    )
    m = _first_motion(src)
    assert m.kind is MotionKind.CIRC_REL


def test_circ_with_named_poses():
    src = "DEF p()\nCIRC arc_mid, arc_end\nEND\n"
    m = _first_motion(src)
    assert isinstance(m.aux, VarRef) and m.aux.name == "arc_mid"
    assert isinstance(m.target, VarRef) and m.target.name == "arc_end"


def test_circ_carries_source_location():
    src = "DEF p()\n\nCIRC arc_mid, arc_end\nEND\n"
    m = _first_motion(src)
    assert m.line == 3  # 1-indexed


# --------------------------------------------------------------------- #
# WAIT.
# --------------------------------------------------------------------- #


def test_wait_sec_numeric():
    src = "DEF p()\nWAIT SEC 1.5\nEND\n"
    result = parse(src)
    wait = next(s for s in result.program.statements if isinstance(s, Wait))
    assert wait.seconds == 1.5
    assert wait.condition is None


def test_wait_sec_integer():
    src = "DEF p()\nWAIT SEC 3\nEND\n"
    result = parse(src)
    wait = next(s for s in result.program.statements if isinstance(s, Wait))
    assert wait.seconds == 3.0


def test_wait_for_degrades_with_warn():
    src = "DEF p()\nWAIT FOR sensor_ready == TRUE\nEND\n"
    result = parse(src)
    wait = next(s for s in result.program.statements if isinstance(s, Wait))
    assert wait.condition is not None
    assert wait.seconds is None
    warn_msgs = [w for w in result.warnings if w.severity is Severity.WARN]
    assert any("WAIT FOR" in w.message for w in warn_msgs)


def test_wait_for_only_warns_once_per_program():
    src = (
        "DEF p()\n"
        "WAIT FOR a == 1\n"
        "WAIT FOR b == 2\n"
        "WAIT FOR c == 3\n"
        "END\n"
    )
    result = parse(src)
    wait_for_warns = [
        w
        for w in result.warnings
        if w.severity is Severity.WARN and "WAIT FOR" in w.message
    ]
    assert len(wait_for_warns) == 1


def test_wait_sec_non_numeric_degrades():
    src = "DEF p()\nWAIT SEC delay_var\nEND\n"
    result = parse(src)
    wait = next(s for s in result.program.statements if isinstance(s, Wait))
    assert wait.seconds == 0.0
    assert any("numeric" in w.message for w in result.warnings)
