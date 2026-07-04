"""Warning / error surfaces for the KRL parser.

The walker collects ``Warning`` instances as it walks; callers receive
them as ``ParseResult.warnings`` (never raised). Hard parse failures
raise ``ParseError``.

Severity matches the sidecar contract in ``aidocs/integrations/117 §3.3``:

* ``INFO``  — informational; e.g. comment-only line ignored.
* ``WARN``  — degraded behaviour; trajectory still emitted.
* ``ERROR`` — would have prevented trajectory generation; for the
  parser layer this is rare — most ERRORs surface from the downstream
  IK stage.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class Severity(str, Enum):
    """Warning severity, mirrors the sidecar JSON shape."""

    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"


@dataclass(frozen=True)
class Warning:
    """A non-fatal observation from the walker.

    ``line`` / ``column`` are 1-indexed and 0-indexed respectively to
    match ANTLR4's ``Token`` shape.
    """

    line: int
    column: int
    severity: Severity
    message: str

    def __str__(self) -> str:  # pragma: no cover — convenience only
        return f"{self.severity.value} L{self.line}:{self.column} {self.message}"


class ParseError(Exception):
    """Raised when the .src / .dat is unparseable past the first
    statement (sidecar would return 400).

    Carries the first ANTLR4 syntax error as ``message`` + the
    ``(line, column)`` tuple.
    """

    def __init__(self, message: str, line: int = 0, column: int = 0):
        super().__init__(message)
        self.line = line
        self.column = column
