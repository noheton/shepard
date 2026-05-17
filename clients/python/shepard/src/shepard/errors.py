"""
ShepardError hierarchy — maps HTTP status codes from ApiException to typed
Python exceptions so callers can write ``except ShepardNotFound:`` without
inspecting ``exc.status``.
"""

from __future__ import annotations


class ShepardError(Exception):
    """Base class for all shepard client errors."""

    def __init__(self, status: int, body: str, headers: dict | None = None) -> None:
        super().__init__(f"HTTP {status}: {body}")
        self.status = status
        self.body = body
        self.headers = headers or {}


class ShepardBadRequest(ShepardError):
    """400 Bad Request."""


class ShepardUnauthorized(ShepardError):
    """401 Unauthorized."""


class ShepardForbidden(ShepardError):
    """403 Forbidden."""


class ShepardNotFound(ShepardError):
    """404 Not Found."""


class ShepardConflict(ShepardError):
    """409 Conflict."""


class ShepardValidation(ShepardError):
    """422 Unprocessable Entity."""


class ShepardServerError(ShepardError):
    """5xx Server Error."""


_STATUS_MAP: dict[int, type[ShepardError]] = {
    400: ShepardBadRequest,
    401: ShepardUnauthorized,
    403: ShepardForbidden,
    404: ShepardNotFound,
    409: ShepardConflict,
    422: ShepardValidation,
}


def raise_for_status(exc: Exception) -> None:
    """Re-raise an ApiException as the appropriate ShepardError subclass.

    Inspects ``exc.status``, ``exc.body``, and ``exc.headers`` (all
    attributes set by the generated ``ApiException``).  Never returns
    normally — always raises.
    """
    status = getattr(exc, "status", None)
    if status is None:
        raise ShepardError(0, str(exc)) from exc
    body: str = getattr(exc, "body", "") or ""
    headers: dict = dict(getattr(exc, "headers", {}) or {})
    if status >= 500:
        cls: type[ShepardError] = ShepardServerError
    else:
        cls = _STATUS_MAP.get(status, ShepardError)
    raise cls(status, body, headers) from exc
