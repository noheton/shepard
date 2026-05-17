"""
Pagination helpers for shepard list endpoints.

The backend returns flat arrays with ``?page&size`` query params; there is no
``Page<T>`` envelope, no ``total``, no ``content``.  Termination is by
"received fewer than ``size`` items" or "received an empty page".
"""

from __future__ import annotations

from typing import Callable, Generator, TypeVar

T = TypeVar("T")


def iter_pages(
    fetch: Callable[..., list[T]],
    /,
    page_size: int = 100,
    **kwargs: object,
) -> Generator[T, None, None]:
    """Yield every item from a paginated list endpoint.

    Args:
        fetch: Callable that accepts ``page`` and ``size`` keyword arguments
            and returns a list of items (the generated ``get_all_*`` methods).
        page_size: Number of items to request per page.  Defaults to 100.
        **kwargs: Extra keyword arguments forwarded to ``fetch`` on every call
            (e.g. filter parameters).

    Yields:
        Individual items from each page in order.

    Example::

        from shepard.pagination import iter_pages

        for col in iter_pages(api.get_all_collections, page_size=50):
            print(col.name)
    """
    page = 0
    while True:
        items: list[T] = fetch(page=page, size=page_size, **kwargs)
        if not items:
            return
        yield from items
        if len(items) < page_size:
            return
        page += 1
