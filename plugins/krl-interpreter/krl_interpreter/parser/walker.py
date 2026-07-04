"""ANTLR4 ParseTree → :class:`~krl_interpreter.parser.ir.Program`.

The walker uses ANTLR4's parse-tree visitor pattern (not the listener)
because the IR transformations need to *return* nodes — listeners only
mutate side state. Despite the docstring elsewhere mentioning a
"listener", we use the ParseTree directly via ``ctx`` accessors;
that's idiomatic ANTLR4 Python.

Output via :meth:`KrlIrBuilder.build`:

* ``program`` — the :class:`Program` IR.
* ``warnings`` — list of :class:`~krl_interpreter.errors.Warning`.
* ``unsupported`` — list of :class:`~krl_interpreter.parser.ir.UnsupportedConstruct`.

Tier-1 known degradations (each warns once per program):

* ``WAIT FOR <cond>`` — degraded to ``Wait(seconds=0)`` (no sensor model).
* Sparse frame literal — missing field defaults to ``0.0``; one INFO
  warning per occurrence.
* ``BCO`` / ``SPS`` / ``INTERRUPT`` / ``ANIN`` / ``ANOUT`` / ``CONTINUE``
  / ``HALT`` — recorded as :class:`UnsupportedConstruct` + a WARN.
"""

from __future__ import annotations

from typing import List, Optional

from krl_interpreter.errors import Severity, Warning
from krl_interpreter.parser.grammar.generated.KrlParser import KrlParser
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

# Fields on a FrameLiteral and their attribute mapping. Anything else
# encountered (``E1`` … ``E6``, unknown keys) is funneled into the
# E6POS shape or the ``extras`` bag.
_FRAME_FIELDS = {
    "X": "x",
    "Y": "y",
    "Z": "z",
    "A": "a",
    "B": "b",
    "C": "c",
}
_E6_FIELDS = {f"E{i}": f"e{i}" for i in range(1, 7)}


class KrlIrBuilder:
    """Walks an ANTLR4 ``ProgramContext`` and produces an IR.

    Constructed per parse. Not re-entrant — instantiate one per file.
    """

    def __init__(self, *, filename: str = "<src>") -> None:
        self.filename = filename
        self.warnings: List[Warning] = []
        self.unsupported: List[UnsupportedConstruct] = []
        self._wait_for_warned = False

    # ------------------------------------------------------------------ #
    # Entry.
    # ------------------------------------------------------------------ #

    def build(self, ctx: KrlParser.ProgramContext) -> Program:
        if ctx is None:  # pragma: no cover — defensive
            return Program()

        src = ctx.srcModule()
        dat = ctx.datModule()
        if src is not None:
            return self._build_src(src)
        if dat is not None:
            return self._build_dat(dat)

        block = ctx.statementBlock()
        statements = self._build_block(block) if block is not None else []
        return Program(statements=statements)

    # ------------------------------------------------------------------ #
    # Top-level shapes.
    # ------------------------------------------------------------------ #

    def _build_src(self, ctx: KrlParser.SrcModuleContext) -> Program:
        is_function = ctx.DEFFCT() is not None
        is_global = ctx.GLOBAL() is not None
        name_token = ctx.NAME()
        module_name = name_token.getText() if name_token is not None else None
        statements = self._build_block(ctx.statementBlock())
        return Program(
            module_name=module_name,
            is_function=is_function,
            is_data_file=False,
            is_global=is_global,
            statements=statements,
        )

    def _build_dat(self, ctx: KrlParser.DatModuleContext) -> Program:
        name_token = ctx.NAME()
        module_name = name_token.getText() if name_token is not None else None
        statements: List[Statement] = []
        decl_block = ctx.datDeclBlock()
        if decl_block is not None:
            for decl in decl_block.varDecl():
                statements.append(self._build_var_decl(decl))
        return Program(
            module_name=module_name,
            is_function=False,
            is_data_file=True,
            is_global=False,
            statements=statements,
        )

    def _build_block(
        self, ctx: Optional[KrlParser.StatementBlockContext]
    ) -> List[Statement]:
        if ctx is None:
            return []
        out: List[Statement] = []
        for stmt_ctx in ctx.statement():
            ir = self._build_statement(stmt_ctx)
            if ir is not None:
                out.append(ir)
        return out

    # ------------------------------------------------------------------ #
    # Statement dispatch.
    # ------------------------------------------------------------------ #

    def _build_statement(
        self, ctx: KrlParser.StatementContext
    ) -> Optional[Statement]:
        if ctx.motionStmt() is not None:
            return self._build_motion(ctx.motionStmt())
        if ctx.waitStmt() is not None:
            return self._build_wait(ctx.waitStmt())
        if ctx.ifStmt() is not None:
            return self._build_if(ctx.ifStmt())
        if ctx.forStmt() is not None:
            return self._build_for(ctx.forStmt())
        if ctx.whileStmt() is not None:
            return self._build_while(ctx.whileStmt())
        if ctx.loopStmt() is not None:
            return self._build_loop(ctx.loopStmt())
        if ctx.exitStmt() is not None:
            line, col = self._loc(ctx.exitStmt())
            return Exit(line=line, column=col)
        if ctx.frameSwitchStmt() is not None:
            return self._build_frame_switch(ctx.frameSwitchStmt())
        if ctx.varDecl() is not None:
            return self._build_var_decl(ctx.varDecl())
        if ctx.assignStmt() is not None:
            return self._build_assign(ctx.assignStmt())
        if ctx.unsupportedStmt() is not None:
            return self._build_unsupported(ctx.unsupportedStmt())
        return None

    # ------------------------------------------------------------------ #
    # Motion + WAIT.
    # ------------------------------------------------------------------ #

    def _build_motion(self, ctx: KrlParser.MotionStmtContext) -> Motion:
        line, col = self._loc(ctx)
        if isinstance(ctx, KrlParser.CircMotionContext):
            kw = ctx.CIRC()
            kind = MotionKind.CIRC
            if kw is None:
                kw = ctx.CIRC_REL()
                kind = MotionKind.CIRC_REL
            poses = ctx.poseExpr()
            aux = self._build_pose(poses[0])
            target = self._build_pose(poses[1])
            opts = self._build_motion_opts(ctx.motionOpts())
            return Motion(
                line=line, column=col, kind=kind, target=target, aux=aux, opts=opts
            )
        # ptpLinMotion alternative
        if ctx.PTP() is not None:
            kind = MotionKind.PTP
        elif ctx.PTP_REL() is not None:
            kind = MotionKind.PTP_REL
        elif ctx.LIN() is not None:
            kind = MotionKind.LIN
        else:
            kind = MotionKind.LIN_REL
        pose_ctx = ctx.poseExpr()
        # For labelled-alt PtpLinMotionContext, poseExpr() returns a
        # single context; for the (unlabelled) circ path we handle
        # both poses above via poseExpr(i).
        if isinstance(pose_ctx, list):
            pose_ctx = pose_ctx[0]
        target = self._build_pose(pose_ctx)
        opts = self._build_motion_opts(ctx.motionOpts())
        return Motion(line=line, column=col, kind=kind, target=target, opts=opts)

    def _build_motion_opts(
        self, ctx: Optional[KrlParser.MotionOptsContext]
    ) -> List[str]:
        if ctx is None:
            return []
        return [n.getText() for n in ctx.NAME()]

    def _build_wait(self, ctx: KrlParser.WaitStmtContext) -> Wait:
        line, col = self._loc(ctx)
        if isinstance(ctx, KrlParser.WaitSecContext):
            seconds_text = self._raw(ctx.expr())
            try:
                seconds = float(seconds_text)
            except ValueError:
                seconds = 0.0
                self.warnings.append(
                    Warning(
                        line=line,
                        column=col,
                        severity=Severity.WARN,
                        message=(
                            f"WAIT SEC expects a numeric literal at parse "
                            f"time; got {seconds_text!r} — degrading to 0s"
                        ),
                    )
                )
            return Wait(line=line, column=col, seconds=seconds)
        # WaitForContext: no sensor model — emit one WARN per program.
        if not self._wait_for_warned:
            self.warnings.append(
                Warning(
                    line=line,
                    column=col,
                    severity=Severity.WARN,
                    message=(
                        "WAIT FOR <cond> degraded to WAIT SEC 0 — the offline "
                        "interpreter has no sensor model (tier-1)"
                    ),
                )
            )
            self._wait_for_warned = True
        return Wait(line=line, column=col, condition=self._raw(ctx.expr()))

    # ------------------------------------------------------------------ #
    # Flow control.
    # ------------------------------------------------------------------ #

    def _build_if(self, ctx: KrlParser.IfStmtContext) -> If:
        line, col = self._loc(ctx)
        blocks = ctx.statementBlock()
        then_body = self._build_block(blocks[0])
        else_body = self._build_block(blocks[1]) if len(blocks) > 1 else []
        return If(
            line=line,
            column=col,
            condition=self._raw(ctx.expr()),
            then_block=then_body,
            else_block=else_body,
        )

    def _build_for(self, ctx: KrlParser.ForStmtContext) -> For:
        line, col = self._loc(ctx)
        exprs = ctx.expr()
        start = self._raw(exprs[0]) if len(exprs) >= 1 else ""
        end = self._raw(exprs[1]) if len(exprs) >= 2 else ""
        step = self._raw(exprs[2]) if len(exprs) >= 3 else None
        return For(
            line=line,
            column=col,
            var=ctx.NAME().getText(),
            start=start,
            end=end,
            step=step,
            body=self._build_block(ctx.statementBlock()),
        )

    def _build_while(self, ctx: KrlParser.WhileStmtContext) -> While:
        line, col = self._loc(ctx)
        return While(
            line=line,
            column=col,
            condition=self._raw(ctx.expr()),
            body=self._build_block(ctx.statementBlock()),
        )

    def _build_loop(self, ctx: KrlParser.LoopStmtContext) -> Loop:
        line, col = self._loc(ctx)
        return Loop(line=line, column=col, body=self._build_block(ctx.statementBlock()))

    # ------------------------------------------------------------------ #
    # $BASE / $TOOL.
    # ------------------------------------------------------------------ #

    def _build_frame_switch(
        self, ctx: KrlParser.FrameSwitchStmtContext
    ) -> BaseToolSwitch:
        line, col = self._loc(ctx)
        target = FrameTarget.BASE if ctx.BASE() is not None else FrameTarget.TOOL
        if ctx.frameLiteral() is not None:
            frame = self._build_frame_literal(ctx.frameLiteral())
        else:
            frame = VarRef(name=ctx.NAME().getText())
        return BaseToolSwitch(line=line, column=col, target=target, frame=frame)

    # ------------------------------------------------------------------ #
    # Declarations + assignments.
    # ------------------------------------------------------------------ #

    def _build_var_decl(self, ctx: KrlParser.VarDeclContext) -> VarDecl:
        line, col = self._loc(ctx)
        type_name = ctx.typeName().getText()
        var_name = ctx.NAME().getText()
        initial = self._raw(ctx.expr()) if ctx.expr() is not None else None
        return VarDecl(
            line=line, column=col, type_name=type_name, name=var_name, initial=initial
        )

    def _build_assign(self, ctx: KrlParser.AssignStmtContext) -> Assign:
        line, col = self._loc(ctx)
        name_node = ctx.NAME() or ctx.DOLLAR_NAME()
        var = name_node.getText() if name_node is not None else ""
        return Assign(line=line, column=col, var=var, expr=self._raw(ctx.expr()))

    # ------------------------------------------------------------------ #
    # Unsupported constructs.
    # ------------------------------------------------------------------ #

    def _build_unsupported(
        self, ctx: KrlParser.UnsupportedStmtContext
    ) -> UnsupportedConstruct:
        line, col = self._loc(ctx)
        raw = self._raw(ctx)
        # Pick the construct label from the alternative; first child is
        # always the keyword token under the unsupportedStmt umbrella.
        first_token = ctx.start.text if ctx.start is not None else ""
        construct = first_token.upper()
        reason_map = {
            "BCO": "BCO blocks are runtime-only; offline interpreter skips them",
            "SPS": "SPS (parallel submit-interpreter) has no offline equivalent",
            "INTERRUPT": "INTERRUPT / ON INTERRUPT depends on runtime signals",
            "ON": "ON ERROR / ON INTERRUPT depends on runtime signals",
            "ANIN": "ANIN sensor advance has no offline model",
            "ANOUT": "ANOUT sensor output has no offline model",
            "CONTINUE": "CONTINUE depends on KRC interpolator state",
            "HALT": "HALT terminates the live interpreter — skipped offline",
        }
        reason = reason_map.get(construct, "unsupported tier-1 construct")
        unsupported = UnsupportedConstruct(
            line=line,
            column=col,
            construct=construct,
            reason=reason,
            raw_text=raw,
        )
        self.unsupported.append(unsupported)
        self.warnings.append(
            Warning(
                line=line,
                column=col,
                severity=Severity.WARN,
                message=f"{construct}: {reason}",
            )
        )
        return unsupported

    # ------------------------------------------------------------------ #
    # Pose / frame literal helpers.
    # ------------------------------------------------------------------ #

    def _build_pose(self, ctx: KrlParser.PoseExprContext) -> object:
        if ctx.frameLiteral() is not None:
            lit = self._build_frame_literal(ctx.frameLiteral())
            # If any E1..E6 fields were collected, promote to E6POS.
            e6_present = {k: lit.extras.pop(k, None) for k in _E6_FIELDS}
            if any(v is not None for v in e6_present.values()):
                # Move the special-bucket back out of extras into proper E6.
                return E6PosLiteral(
                    frame=lit,
                    e1=e6_present.get("E1"),
                    e2=e6_present.get("E2"),
                    e3=e6_present.get("E3"),
                    e4=e6_present.get("E4"),
                    e5=e6_present.get("E5"),
                    e6=e6_present.get("E6"),
                )
            return lit
        return VarRef(name=ctx.NAME().getText())

    def _build_frame_literal(
        self, ctx: KrlParser.FrameLiteralContext
    ) -> FrameLiteral:
        lit = FrameLiteral()
        seen = set()
        for field_ctx in ctx.frameField():
            name = field_ctx.NAME().getText().upper()
            value_text = self._raw(field_ctx.expr())
            try:
                value = float(value_text)
            except ValueError:
                # Leave it as text in extras; the IK layer may resolve a var.
                lit.extras[name] = value_text
                continue
            if name in _FRAME_FIELDS:
                setattr(lit, _FRAME_FIELDS[name], value)
                seen.add(name)
            elif name in _E6_FIELDS:
                # Bucket E1..E6 in extras with their raw key for the
                # E6POS promotion in _build_pose.
                lit.extras[name] = value
            else:
                lit.extras[name] = value
        missing = set(_FRAME_FIELDS) - seen
        if missing:
            lit.extras["_missing_fields"] = sorted(missing)
            line = ctx.start.line if ctx.start is not None else 0
            col = ctx.start.column if ctx.start is not None else 0
            self.warnings.append(
                Warning(
                    line=line,
                    column=col,
                    severity=Severity.INFO,
                    message=(
                        f"sparse frame literal — missing fields default to 0: "
                        f"{', '.join(sorted(missing))}"
                    ),
                )
            )
        return lit

    # ------------------------------------------------------------------ #
    # Misc.
    # ------------------------------------------------------------------ #

    @staticmethod
    def _loc(ctx) -> tuple:
        if ctx is None or ctx.start is None:
            return (0, 0)
        return (ctx.start.line, ctx.start.column)

    @staticmethod
    def _raw(ctx) -> str:
        """Return the raw source text of a parser context, faithful to
        whitespace within the original token stream. Used for opaque
        expression bodies (the IR doesn't evaluate expressions)."""
        if ctx is None:
            return ""
        # ParseTreeContext exposes getText() which concatenates leaves;
        # for IF/FOR/WHILE conditions and assignments this is the right
        # forward-only shape (whitespace is not semantic in KRL expr).
        return ctx.getText()
