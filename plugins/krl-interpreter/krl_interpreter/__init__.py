"""shepard-plugin-krl-interpreter — KRL parser + IR + IK back-solver.

Offline interpreter for KUKA KRL programs. See aidocs/integrations/117.

Public API — parser (KRL-INTERPRETER-02)::

    >>> from krl_interpreter import parse
    >>> result = parse(open("Ply_5_layup.src").read())
    >>> result.program.statements
    >>> result.warnings
    >>> result.unsupported

Public API — IK (KRL-INTERPRETER-03)::

    >>> from krl_interpreter.ik import IkSolver, TargetPose
    >>> solver = IkSolver("kr210l150.urdf", base_link="base_link")
    >>> result = solver.solve(TargetPose(x=1.8, y=0.2, z=1.7))
"""

from krl_interpreter.parser import parse, ParseResult  # noqa: F401
from krl_interpreter.parser.ir import (  # noqa: F401
    Program,
    Statement,
    Motion,
    MotionKind,
    Wait,
    If,
    For,
    While,
    Loop,
    Exit,
    Assign,
    VarDecl,
    FrameLiteral,
    E6PosLiteral,
    BaseToolSwitch,
    UnsupportedConstruct,
)
from krl_interpreter.errors import Warning, ParseError, Severity  # noqa: F401

__version__ = "0.1.0"
