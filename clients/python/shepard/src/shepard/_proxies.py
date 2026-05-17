"""
Domain proxy wrappers around the generated ``*Api`` classes.

Each ``_DomainProxy`` instance wraps one generated ``*Api`` instance and:

- Forwards all method calls via ``__getattr__`` (so ``dir(sh.collections)``
  is identical to ``CollectionApi``).
- Catches ``ApiException`` on every forwarded call and re-raises it as the
  appropriate ``ShepardError`` subclass.
- Exposes ``iter(fetch_method, *, page_size, **kwargs)`` and
  ``list(fetch_method, *, page_size, **kwargs)`` convenience wrappers that
  hide the ``?page&size`` plumbing.

The proxy does **not** re-implement or rename any generated method — it is a
pure forwarding layer so future generator regenerations require no code change
here.
"""

from __future__ import annotations

import warnings
from typing import Generator, Iterator

from shepard.errors import raise_for_status
from shepard.pagination import iter_pages


class _DomainProxy:
    """Generic forwarding proxy around a single generated ``*Api`` instance."""

    def __init__(self, api_instance: object, list_cap: int) -> None:
        # Use object.__setattr__ to avoid triggering our own __getattr__ during
        # construction.
        object.__setattr__(self, "_api", api_instance)
        object.__setattr__(self, "_list_cap", list_cap)

    # ------------------------------------------------------------------
    # Attribute forwarding
    # ------------------------------------------------------------------

    def __getattr__(self, name: str) -> object:
        attr = getattr(object.__getattribute__(self, "_api"), name)
        if not callable(attr):
            return attr

        def _wrapped(*args: object, **kwargs: object) -> object:
            try:
                return attr(*args, **kwargs)
            except Exception as exc:
                if hasattr(exc, "status"):
                    raise_for_status(exc)
                raise

        return _wrapped

    def __dir__(self) -> list[str]:  # type: ignore[override]
        own = list(object.__dir__(self))
        api_dir = dir(object.__getattribute__(self, "_api"))
        return sorted(set(own) | set(api_dir))

    # ------------------------------------------------------------------
    # Pagination helpers
    # ------------------------------------------------------------------

    def iter(
        self,
        fetch_method: str,
        *,
        page_size: int = 100,
        **kwargs: object,
    ) -> Generator[object, None, None]:
        """Lazily iterate over all pages of a list endpoint.

        Args:
            fetch_method: Name of the generated ``get_all_*`` method to call
                (e.g. ``"get_all_collections"``).
            page_size: Items per page.  Defaults to 100.
            **kwargs: Extra filters forwarded to the underlying API method on
                every page request.

        Yields:
            Individual items across all pages.

        Example::

            for col in sh.collections.iter("get_all_collections"):
                process(col)
        """
        api = object.__getattribute__(self, "_api")
        fetch = getattr(api, fetch_method)

        def _fetching_wrapper(**kw: object) -> list[object]:
            try:
                return fetch(**kw)
            except Exception as exc:
                if hasattr(exc, "status"):
                    raise_for_status(exc)
                raise

        yield from iter_pages(_fetching_wrapper, page_size=page_size, **kwargs)

    def list(
        self,
        fetch_method: str,
        *,
        page_size: int = 100,
        **kwargs: object,
    ) -> list[object]:
        """Return all items from a list endpoint as a Python ``list``.

        Raises ``ValueError`` when the result set exceeds ``Client.list_cap``
        (default 10 000).  Use ``.iter()`` for large result sets.

        Args:
            fetch_method: Name of the generated ``get_all_*`` method to call.
            page_size: Items per page.  Defaults to 100.
            **kwargs: Extra filters forwarded to the underlying API method.

        Returns:
            A list of all items fetched.
        """
        list_cap: int = object.__getattribute__(self, "_list_cap")
        items = list(
            self.iter(fetch_method, page_size=page_size, **kwargs)
        )
        if len(items) > list_cap:
            raise ValueError(
                f"Result set has {len(items)} items, which exceeds "
                f"list_cap={list_cap}.  Use .iter() for large result sets "
                f"or construct the Client with a higher list_cap."
            )
        return items


# ---------------------------------------------------------------------------
# Domain map: wrapper attribute name → generated Api class name
# ---------------------------------------------------------------------------

_DOMAIN_MAP: dict[str, str] = {
    "collections": "CollectionApi",
    "dataobjects": "DataObjectApi",
    "timeseries": "TimeseriesContainerApi",
    "files": "FileContainerApi",
    "structured_data": "StructuredDataContainerApi",
    "spatial_data": "SpatialDataContainerApi",
    "search": "SearchApi",
    "api_keys": "ApikeyApi",
    "subscriptions": "SubscriptionApi",
    "semantic": "SemanticAnnotationApi",
    "users": "UserApi",
    "usergroups": "UsergroupApi",
    "versionz": "VersionzApi",
}

# Classes the wrapper deliberately does not surface (accessed via sh.raw).
_INTENTIONALLY_UNWRAPPED: frozenset[str] = frozenset()


def _check_coverage(shepard_client_api_module: object) -> None:
    """Warn about generated ``*Api`` classes not wrapped by ``_DOMAIN_MAP``.

    Called at ``Client.__init__`` time so operators learn about new resources
    without breaking existing code.  New ``*Api`` classes are accessible via
    ``sh._raw`` in the meantime.
    """
    wrapped_api_names = set(_DOMAIN_MAP.values())
    try:
        api_dir = dir(shepard_client_api_module)
    except Exception:  # noqa: BLE001
        return
    for name in api_dir:
        if name.endswith("Api") and not name.startswith("_"):
            if name not in wrapped_api_names and name not in _INTENTIONALLY_UNWRAPPED:
                warnings.warn(
                    f"shepard: generated class {name!r} is not wrapped — "
                    f"accessible as sh._raw.{name}",
                    stacklevel=4,
                )
