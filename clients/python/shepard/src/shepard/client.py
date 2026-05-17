"""
``Client`` — the three-line entry point into shepard.

Usage::

    import shepard

    sh = shepard.Client(
        host="https://backend.shepard.example.com/shepard/api",
        apikey="sk-...",
    )
    created = sh.collections.create_collection(collection=Collection(name="X"))

All domain attributes (``sh.collections``, ``sh.timeseries``, …) are created
lazily on first access so importing ``shepard`` is cheap even when only one
domain is used.

Workflow helpers (``ro_crate``, ``to_pandas``, ``to_excel``) are on the
``Client`` class directly because they span multiple generated APIs.
``to_pandas`` and ``to_excel`` are stubs pending P10 SQL timeseries wiring;
``ro_crate`` is fully operational and delegates to ``CollectionApi``.
"""

from __future__ import annotations

import warnings
from pathlib import Path
from typing import TYPE_CHECKING

from shepard._proxies import _DOMAIN_MAP, _DomainProxy, _check_coverage
from shepard.errors import raise_for_status

if TYPE_CHECKING:
    import pandas as pd  # noqa: F401 (type-check only)


class Client:
    """Convenience wrapper around the generated ``shepard_client``.

    Args:
        host: Base URL of the shepard backend, e.g.
            ``"https://backend.shepard.example.com/shepard/api"``.
        apikey: API key (mutually exclusive with *bearer_token*).
        bearer_token: Bearer JWT (forward-compat for future JWT-based auth;
            mutually exclusive with *apikey*).
        timeout: Request timeout in seconds.  ``None`` means the generated
            client's default (no explicit timeout).
        verify_ssl: Whether to verify TLS certificates.  Defaults to ``True``.
        ssl_ca_cert: Path to a custom CA certificate bundle.
        list_cap: Maximum items that ``.list()`` on a domain proxy will
            return before raising ``ValueError``.  Defaults to 10 000.
    """

    def __init__(
        self,
        host: str,
        apikey: str | None = None,
        bearer_token: str | None = None,
        timeout: float | None = None,
        verify_ssl: bool = True,
        ssl_ca_cert: str | None = None,
        list_cap: int = 10_000,
    ) -> None:
        import shepard_client  # deferred so "import shepard" stays cheap

        if apikey and bearer_token:
            warnings.warn(
                "Both apikey and bearer_token supplied; apikey takes precedence.",
                stacklevel=2,
            )

        cfg = shepard_client.Configuration(host=host)
        if apikey:
            cfg.api_key = {"apikey": apikey}
        if bearer_token and not apikey:
            cfg.access_token = bearer_token
        cfg.verify_ssl = verify_ssl
        cfg.ssl_ca_cert = ssl_ca_cert

        self._api_client = shepard_client.ApiClient(configuration=cfg)

        if timeout is not None:
            # urllib3 PoolManager stores timeout in connection_pool_kw.
            try:
                self._api_client.rest_client.pool_manager.connection_pool_kw[
                    "timeout"
                ] = timeout
            except AttributeError:
                pass  # Different urllib3 version; best-effort.

        self._list_cap = list_cap
        self._proxies: dict[str, _DomainProxy] = {}

        # Expose raw api module for advanced use and coverage check.
        self._shepard_client = shepard_client
        try:
            import shepard_client.api as _api_module  # noqa: PLC0415
            _check_coverage(_api_module)
            self._raw = _api_module
        except ImportError:
            self._raw = None

    # ------------------------------------------------------------------
    # Lazy domain proxy attributes
    # ------------------------------------------------------------------

    def __getattr__(self, name: str) -> _DomainProxy:
        if name not in _DOMAIN_MAP:
            raise AttributeError(
                f"{type(self).__name__!r} object has no attribute {name!r}. "
                f"Available domains: {sorted(_DOMAIN_MAP)}"
            )
        if name not in self._proxies:
            self._proxies[name] = self._make_proxy(name)
        return self._proxies[name]

    def _make_proxy(self, domain_name: str) -> _DomainProxy:
        api_class_name = _DOMAIN_MAP[domain_name]
        # Dynamic import: from shepard_client.api.<module> import <Class>
        # The generator places each Api class in its own module named after the
        # class in snake_case: CollectionApi → collection_api, etc.
        module_name = _class_name_to_module(api_class_name)
        try:
            import importlib  # noqa: PLC0415
            mod = importlib.import_module(
                f"shepard_client.api.{module_name}"
            )
            api_cls = getattr(mod, api_class_name)
        except (ImportError, AttributeError) as exc:
            raise ImportError(
                f"Could not import {api_class_name} from shepard_client. "
                f"Make sure shepard-client>=5.2 is installed."
            ) from exc
        api_instance = api_cls(self._api_client)
        return _DomainProxy(api_instance, self._list_cap)

    # ------------------------------------------------------------------
    # Workflow helpers
    # ------------------------------------------------------------------

    def ro_crate(self, collection_id: int, path: str | Path) -> Path:
        """Download a collection's RO-Crate export to disk.

        Delegates to ``CollectionApi.export_collection()`` so authentication
        headers (API key, bearer token) are applied by the generated client's
        normal request pipeline rather than being assembled manually.

        Args:
            collection_id: Numeric ID of the collection to export.
            path: Destination file path (e.g. ``/tmp/export.zip``).

        Returns:
            Resolved ``Path`` of the written file.
        """
        dest = Path(path)
        dest.parent.mkdir(parents=True, exist_ok=True)

        # Call through the generated CollectionApi so auth headers are
        # applied by update_params_for_auth() — bypassing the rest_client
        # directly would skip the auth step and return 401.
        result: bytes = self.collections.export_collection(collection_id=collection_id)
        with dest.open("wb") as fh:
            fh.write(result if isinstance(result, (bytes, bytearray)) else result)
        return dest.resolve()

    def to_pandas(
        self,
        timeseries_id: int,
        *,
        container_id: int | None = None,
        start: object = None,
        end: object = None,
        fields: list[str] | None = None,
        chunksize: int | None = None,
    ) -> "pd.DataFrame":
        """Load timeseries data into a pandas DataFrame.

        .. note::

            **Not yet implemented — pending P10 SQL timeseries backend.**

            This method will be wired once ``POST /v2/sql/timeseries`` ships
            (see ``aidocs/ops/27-convenience-clients-design.md`` §10 Phase 4 and
            the P10 design).  Until then, read timeseries data points via the
            generated ``TimeseriesContainerApi`` directly:

            .. code-block:: python

                api = sh.timeseries  # _DomainProxy → TimeseriesContainerApi
                data = api.get_all_timeseries_data_points(
                    timeseries_container_id=container_id,
                    timeseries_id=timeseries_id,
                )

        Raises:
            NotImplementedError: Always, until P10 ships.
        """
        raise NotImplementedError(
            "to_pandas is pending P10 SQL timeseries wiring "
            "(aidocs/ops/27-convenience-clients-design.md §10 Phase 4).  "
            "Once POST /v2/sql/timeseries ships this method will work "
            "without a signature change.  Until then, call "
            "sh.timeseries.<get_all_...>() directly via the generated API."
        )

    def to_excel(
        self,
        timeseries_id: int,
        path: str | Path,
        *,
        container_id: int | None = None,
        start: object = None,
        end: object = None,
        fields: list[str] | None = None,
    ) -> Path:
        """Export timeseries data to an Excel file.

        .. note::

            **Not yet implemented — pending P10 SQL timeseries backend.**

            Delegates to :meth:`to_pandas`; blocked on the same P10 wiring.

        Raises:
            NotImplementedError: Always, until P10 ships.
        """
        raise NotImplementedError(
            "to_excel is pending P10 SQL timeseries wiring "
            "(aidocs/ops/27-convenience-clients-design.md §10 Phase 4).  "
            "See to_pandas docstring for the interim workaround."
        )


# ---------------------------------------------------------------------------
# Helper: convert CamelCase class name to snake_case module name
# ---------------------------------------------------------------------------

def _class_name_to_module(class_name: str) -> str:
    """Convert ``CollectionApi`` → ``collection_api``."""
    import re  # noqa: PLC0415
    # Insert underscore before uppercase letters that follow lowercase letters
    # or digits, then lowercase the result.
    s1 = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", class_name)
    s2 = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", s1)
    return s2.lower()
