"""
Unit tests for ``shepard.pagination.iter_pages``.

No live server required.
"""

from __future__ import annotations

import pytest

from shepard.pagination import iter_pages


def _make_paginated_fetch(pages: list[list[int]]):
    """Return a fetch callable that yields pages from a pre-built list."""
    call_log: list[tuple[int, int]] = []

    def fetch(*, page: int = 0, size: int = 100, **_kwargs) -> list[int]:
        call_log.append((page, size))
        if page < len(pages):
            return pages[page]
        return []

    fetch.call_log = call_log  # type: ignore[attr-defined]
    return fetch


# ---------------------------------------------------------------------------
# Core iteration behaviour
# ---------------------------------------------------------------------------

def test_iter_pages_three_pages_last_partial() -> None:
    """3 pages of 5 + 5 + 2 items → 12 items total."""
    fetch = _make_paginated_fetch([[1, 2, 3, 4, 5], [6, 7, 8, 9, 10], [11, 12]])
    result = list(iter_pages(fetch, page_size=5))
    assert result == list(range(1, 13))


def test_iter_pages_empty_first_page_yields_nothing() -> None:
    fetch = _make_paginated_fetch([[]])
    result = list(iter_pages(fetch, page_size=10))
    assert result == []


def test_iter_pages_exact_page_size_reads_extra_empty_page() -> None:
    """When last page has exactly ``page_size`` items, one extra call is made."""
    # 10 items, page_size=10 → page 0 full (10 items) → page 1 empty → stop
    fetch = _make_paginated_fetch([list(range(10)), []])
    result = list(iter_pages(fetch, page_size=10))
    assert result == list(range(10))
    # page 0 and page 1 were both fetched
    assert fetch.call_log == [(0, 10), (1, 10)]  # type: ignore[attr-defined]


def test_iter_pages_single_partial_page() -> None:
    fetch = _make_paginated_fetch([[42, 43]])
    result = list(iter_pages(fetch, page_size=100))
    assert result == [42, 43]


def test_iter_pages_is_a_generator() -> None:
    """iter_pages returns a generator, not a list."""
    import types
    fetch = _make_paginated_fetch([[1]])
    gen = iter_pages(fetch, page_size=10)
    assert isinstance(gen, types.GeneratorType)


# ---------------------------------------------------------------------------
# kwargs forwarding
# ---------------------------------------------------------------------------

def test_iter_pages_forwards_kwargs() -> None:
    received_kwargs: list[dict] = []

    def fetch(*, page: int = 0, size: int = 100, **kwargs) -> list[str]:
        received_kwargs.append(kwargs)
        if page == 0:
            return ["a"]
        return []

    list(iter_pages(fetch, page_size=10, filter_by="active"))
    assert all(kw == {"filter_by": "active"} for kw in received_kwargs)


# ---------------------------------------------------------------------------
# Page number sequencing
# ---------------------------------------------------------------------------

def test_iter_pages_calls_sequential_pages() -> None:
    fetch = _make_paginated_fetch([[0, 1], [2, 3], [4]])
    list(iter_pages(fetch, page_size=2))
    pages_called = [p for p, _ in fetch.call_log]  # type: ignore[attr-defined]
    assert pages_called == [0, 1, 2]


def test_iter_pages_passes_size_to_fetch() -> None:
    fetch = _make_paginated_fetch([[1, 2]])
    list(iter_pages(fetch, page_size=42))
    _, size = fetch.call_log[0]  # type: ignore[attr-defined]
    assert size == 42
