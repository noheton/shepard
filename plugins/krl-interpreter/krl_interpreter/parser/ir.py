"""Intermediate representation (IR) for parsed KRL.

The IR is forward-only into trajectories (no round-trip back to
source). Every node carries ``(line, column)`` for warnings and
provenance — these are 1-indexed line / 0-indexed column to match
ANTLR4's ``Token`` shape.

See ``aidocs/integrations/117 §4.1`` for the shape contract.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional, Union


class MotionKind(str, Enum):
    """KRL motion primitive type."""

    PTP = "PTP"
    PTP_REL = "PTP_REL"
    LIN = "LIN"
    LIN_REL = "LIN_REL"
    CIRC = "CIRC"
    CIRC_REL = "CIRC_REL"


class FrameTarget(str, Enum):
    """Which active-frame slot a :class:`BaseToolSwitch` targets."""

    BASE = "$BASE"
    TOOL = "$TOOL"


# --------------------------------------------------------------------- #
# Pose-related leaves (no ``line/column`` on these — they're values, not
# statements; line info lives on the enclosing statement).
# --------------------------------------------------------------------- #


@dataclass
class FrameLiteral:
    """A ``{X 100, Y 0, Z 200, A 0, B 0, C 0}`` literal.

    Sparse literals (subset of fields) are allowed; missing fields
    default to ``0.0``. Unrecognised field names are stored in
    ``extras`` for the IK layer to consume.
    """

    x: float = 0.0
    y: float = 0.0
    z: float = 0.0
    a: float = 0.0
    b: float = 0.0
    c: float = 0.0
    extras: dict = field(default_factory=dict)

    @property
    def is_sparse(self) -> bool:
        """True if any of X/Y/Z/A/B/C was not explicitly set."""
        return bool(self.extras.get("_missing_fields"))


@dataclass
class E6PosLiteral:
    """A ``{X .., Y .., Z .., A .., B .., C .., E1 .., …, E6 ..}``
    literal — an ``E6POS`` frame plus up to 6 external-axis values."""

    frame: FrameLiteral
    e1: Optional[float] = None
    e2: Optional[float] = None
    e3: Optional[float] = None
    e4: Optional[float] = None
    e5: Optional[float] = None
    e6: Optional[float] = None


@dataclass
class VarRef:
    """Reference to a symbol resolved at interpret time."""

    name: str


Pose = Union[FrameLiteral, E6PosLiteral, VarRef]
Expr = object  # IR is forward-only — expressions are kept as opaque text strings.


# --------------------------------------------------------------------- #
# Statements.
# --------------------------------------------------------------------- #


@dataclass
class _Located:
    """Mixin: every statement carries source location."""

    line: int = 0
    column: int = 0


@dataclass
class Motion(_Located):
    """``PTP`` / ``LIN`` / ``CIRC`` motion primitive.

    ``aux`` is populated only for ``CIRC`` / ``CIRC_REL`` (the auxiliary
    via-point); ``None`` otherwise.
    """

    kind: MotionKind = MotionKind.PTP
    target: Pose = field(default_factory=FrameLiteral)
    aux: Optional[Pose] = None
    opts: List[str] = field(default_factory=list)


@dataclass
class Wait(_Located):
    """``WAIT SEC <n>`` or ``WAIT FOR <cond>`` (the latter degrades).

    ``seconds`` is set for ``WAIT SEC``; ``condition`` is the raw text
    of ``WAIT FOR <cond>``. Exactly one of the two is non-None.
    """

    seconds: Optional[float] = None
    condition: Optional[str] = None


@dataclass
class If(_Located):
    """``IF / THEN / ELSE / ENDIF`` block."""

    condition: str = ""
    then_block: List["Statement"] = field(default_factory=list)
    else_block: List["Statement"] = field(default_factory=list)


@dataclass
class For(_Located):
    """``FOR <var> = <start> TO <end> [STEP <step>] / ENDFOR``."""

    var: str = ""
    start: str = ""
    end: str = ""
    step: Optional[str] = None
    body: List["Statement"] = field(default_factory=list)


@dataclass
class While(_Located):
    """``WHILE <cond> / ENDWHILE``."""

    condition: str = ""
    body: List["Statement"] = field(default_factory=list)


@dataclass
class Loop(_Located):
    """``LOOP / ENDLOOP`` (unbounded; trajectory layer enforces a cap)."""

    body: List["Statement"] = field(default_factory=list)


@dataclass
class Exit(_Located):
    """``EXIT`` statement (terminates the enclosing ``LOOP``)."""


@dataclass
class Assign(_Located):
    """``<var> = <expr>`` assignment.

    ``expr`` is the raw expression text (the IR doesn't evaluate
    expressions at parse time)."""

    var: str = ""
    expr: str = ""


@dataclass
class VarDecl(_Located):
    """``DECL <type> <name> [= <expr>]`` declaration."""

    type_name: str = ""
    name: str = ""
    initial: Optional[str] = None


@dataclass
class BaseToolSwitch(_Located):
    """``$BASE = <frame>`` or ``$TOOL = <frame>``."""

    target: FrameTarget = FrameTarget.BASE
    frame: Pose = field(default_factory=FrameLiteral)


@dataclass
class UnsupportedConstruct(_Located):
    """A construct the parser tokenised but the IR doesn't honour.

    Tier-1 constructs that land here: ``BCO``, ``SPS``,
    ``INTERRUPT``/``ON INTERRUPT``, ``ANIN``/``ANOUT``, ``CONTINUE``,
    ``HALT``, ``#INCLUDE``.

    Distinct from :class:`~krl_interpreter.errors.Warning` — these are
    a *structured* list (queryable by the MCP
    ``krl_list_unsupported`` tool — see ``aidocs/integrations/117 §9.2``).
    """

    construct: str = ""
    reason: str = ""
    raw_text: str = ""


# A discriminated-union alias for the statement set.
Statement = Union[
    Motion,
    Wait,
    If,
    For,
    While,
    Loop,
    Exit,
    Assign,
    VarDecl,
    BaseToolSwitch,
    UnsupportedConstruct,
]


# --------------------------------------------------------------------- #
# Program.
# --------------------------------------------------------------------- #


@dataclass
class Program:
    """The parsed top-level module.

    For a ``.src`` file ``module_name`` is the ``DEF <name>`` /
    ``DEFFCT … <name>`` identifier; for ``.dat`` it's the ``DEFDAT
    <name>``. Bare statement streams have ``module_name=None``.
    """

    module_name: Optional[str] = None
    is_function: bool = False
    is_data_file: bool = False
    is_global: bool = False
    statements: List[Statement] = field(default_factory=list)

    def motion_count(self) -> int:
        """Convenience: how many :class:`Motion` statements at the top
        level (does *not* descend into bodies)."""
        return sum(1 for s in self.statements if isinstance(s, Motion))
