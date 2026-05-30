"""shepard-plugin-krl-interpreter — KRL parser + IR (KRL-INTERPRETER-02).

Public API:

    >>> from krl_interpreter import parse
    >>> result = parse(open("Ply_5_layup.src").read())
    >>> result.program.statements
    >>> result.warnings
    >>> result.unsupported

See ``docs/quickstart.md`` for the 5-line snippet and ``docs/reference.md``
for the full KRL subset.
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
