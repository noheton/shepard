"""DEFDAT (.dat companion file) parsing."""

from __future__ import annotations

from pathlib import Path

from krl_interpreter import parse
from krl_interpreter.parser.ir import VarDecl


def test_dat_file_with_mixed_declarations():
    fixture = Path(__file__).parent / "fixtures" / "vars.dat"
    result = parse(fixture.read_text())
    program = result.program

    assert program.is_data_file is True
    assert program.module_name == "cell_data"

    # All five top-level statements are VarDecls.
    decls = [s for s in program.statements if isinstance(s, VarDecl)]
    assert len(decls) == 5

    by_name = {d.name: d for d in decls}
    assert by_name["max_ply"].type_name == "INT"
    assert by_name["feed_rate"].type_name == "REAL"
    assert by_name["diagnostics_enabled"].type_name == "BOOL"
    assert by_name["home_frame"].type_name == "FRAME"
    assert by_name["pickup_pose"].type_name == "POS"
