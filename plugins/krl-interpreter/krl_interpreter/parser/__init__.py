"""KRL parser entry point.

Exposes :func:`parse` for the 5-line quickstart in ``docs/quickstart.md``.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List

from antlr4 import CommonTokenStream, InputStream
from antlr4.error.ErrorListener import ErrorListener

from krl_interpreter.errors import ParseError, Severity, Warning
from krl_interpreter.parser.grammar.generated.KrlLexer import KrlLexer
from krl_interpreter.parser.grammar.generated.KrlParser import KrlParser
from krl_interpreter.parser.ir import Program, UnsupportedConstruct
from krl_interpreter.parser.walker import KrlIrBuilder


@dataclass
class ParseResult:
    """Output of :func:`parse`.

    See ``aidocs/integrations/117 §3.3`` for the sidecar shape this
    feeds into (the sidecar's ``warnings`` and ``unsupportedConstructs``
    arrays mirror these fields).
    """

    program: Program
    warnings: List[Warning] = field(default_factory=list)
    unsupported: List[UnsupportedConstruct] = field(default_factory=list)


class _CollectingErrorListener(ErrorListener):
    """ANTLR4 error listener that captures syntax errors instead of
    printing them to stderr (which is ANTLR4's default).
    """

    def __init__(self) -> None:
        super().__init__()
        self.errors: List[Warning] = []

    def syntaxError(  # noqa: N802 — ANTLR4 API name
        self, recognizer, offendingSymbol, line, column, msg, e
    ) -> None:
        self.errors.append(
            Warning(
                line=line,
                column=column,
                severity=Severity.ERROR,
                message=f"syntax error: {msg}",
            )
        )


def parse(source: str, *, filename: str = "<src>") -> ParseResult:
    """Parse a KRL `.src` or `.dat` source string into an IR.

    Raises :class:`ParseError` if the source cannot be parsed past the
    first statement. Warnings collected during the walk (unsupported
    constructs, sparse frame fields, …) are returned on the result.

    :param source: the file contents.
    :param filename: optional name used in error messages.
    """
    input_stream = InputStream(source)
    lexer = KrlLexer(input_stream)
    token_stream = CommonTokenStream(lexer)
    parser = KrlParser(token_stream)

    lexer.removeErrorListeners()
    parser.removeErrorListeners()
    listener = _CollectingErrorListener()
    lexer.addErrorListener(listener)
    parser.addErrorListener(listener)

    tree = parser.program()

    builder = KrlIrBuilder(filename=filename)
    program = builder.build(tree)

    # Surface any lexer / parser errors as ERROR warnings; if no
    # statements were extracted and we have errors, that's a hard
    # parse failure.
    all_warnings = listener.errors + builder.warnings
    if listener.errors and not program.statements:
        first = listener.errors[0]
        raise ParseError(first.message, line=first.line, column=first.column)

    return ParseResult(
        program=program,
        warnings=all_warnings,
        unsupported=builder.unsupported,
    )
