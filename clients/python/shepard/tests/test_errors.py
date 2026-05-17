"""
Unit tests for ``shepard.errors``.

No live server or generated package required (conftest installs stubs).
"""

from __future__ import annotations

import pytest

from shepard.errors import (
    ShepardBadRequest,
    ShepardConflict,
    ShepardError,
    ShepardForbidden,
    ShepardNotFound,
    ShepardServerError,
    ShepardUnauthorized,
    ShepardValidation,
    raise_for_status,
)


# ---------------------------------------------------------------------------
# Helper: fake ApiException
# ---------------------------------------------------------------------------

class _FakeApiException(Exception):
    def __init__(self, status: int, body: str = "some error", headers: dict | None = None) -> None:
        self.status = status
        self.body = body
        self.headers = headers or {"X-Request-Id": "abc"}


# ---------------------------------------------------------------------------
# Test raise_for_status — status code mapping
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("status, expected_cls", [
    (400, ShepardBadRequest),
    (401, ShepardUnauthorized),
    (403, ShepardForbidden),
    (404, ShepardNotFound),
    (409, ShepardConflict),
    (422, ShepardValidation),
    (500, ShepardServerError),
    (503, ShepardServerError),
])
def test_raise_for_status_maps_status_codes(status: int, expected_cls: type) -> None:
    exc = _FakeApiException(status)
    with pytest.raises(expected_cls):
        raise_for_status(exc)


def test_raise_for_status_unknown_4xx_maps_to_base() -> None:
    exc = _FakeApiException(418)  # I'm a teapot
    with pytest.raises(ShepardError) as exc_info:
        raise_for_status(exc)
    # Should NOT be a subclass of ShepardServerError
    assert type(exc_info.value) is ShepardError


def test_raise_for_status_carries_status_body_headers() -> None:
    exc = _FakeApiException(404, body="not found", headers={"X-Trace": "123"})
    with pytest.raises(ShepardNotFound) as exc_info:
        raise_for_status(exc)
    err = exc_info.value
    assert err.status == 404
    assert err.body == "not found"
    assert err.headers == {"X-Trace": "123"}


def test_raise_for_status_no_status_attribute() -> None:
    """Non-ApiException (no .status) maps to ShepardError(0, ...)."""
    exc = ValueError("network error")
    with pytest.raises(ShepardError) as exc_info:
        raise_for_status(exc)
    assert exc_info.value.status == 0


def test_raise_for_status_chained_cause() -> None:
    """The original exception is preserved as __cause__."""
    exc = _FakeApiException(404)
    with pytest.raises(ShepardNotFound) as exc_info:
        raise_for_status(exc)
    assert exc_info.value.__cause__ is exc


# ---------------------------------------------------------------------------
# Test ShepardError base class
# ---------------------------------------------------------------------------

def test_shepard_error_str() -> None:
    err = ShepardError(404, "not found")
    assert "404" in str(err)
    assert "not found" in str(err)


def test_shepard_error_empty_headers_default() -> None:
    err = ShepardError(500, "boom")
    assert err.headers == {}


def test_shepard_error_is_exception() -> None:
    err = ShepardError(400, "bad")
    assert isinstance(err, Exception)


# ---------------------------------------------------------------------------
# Test subclass hierarchy
# ---------------------------------------------------------------------------

def test_all_subclasses_are_shepard_errors() -> None:
    for cls in (
        ShepardBadRequest,
        ShepardUnauthorized,
        ShepardForbidden,
        ShepardNotFound,
        ShepardConflict,
        ShepardValidation,
        ShepardServerError,
    ):
        err = cls(999, "test")
        assert isinstance(err, ShepardError)
