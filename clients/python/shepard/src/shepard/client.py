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

Workflow helpers (``to_pandas``, ``to_excel``, ``ro_crate``) are on the
``Client`` class directly because they span multiple generated APIs.  The
pandas/openpyxl helpers require ``pip install 'shepard[pandas]'`` /
``pip install 'shepard[excel]'`` — they raise ``ImportError`` at call-site
(not at import time) when the extras are missing.
"""

from __future__ import annotations

import shutil
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

        Streams the response body (``ro-crate-metadata.json`` archive) to
        *path* using urllib3's ``preload_content=False`` to avoid buffering
        the entire archive in memory.

        Args:
            collection_id: Numeric ID of the collection to export.
            path: Destination file path (e.g. ``/tmp/export.zip``).

        Returns:
            Resolved ``Path`` of the written file.
        """
        dest = Path(path)
        dest.parent.mkdir(parents=True, exist_ok=True)

        # Use the generated CollectionApi's export method which returns
        # a file-like or bytes response.  We call _api_client directly
        # to get a streaming urllib3 response.
        url = f"/collections/{collection_id}/export"
        try:
            response = self._api_client.rest_client.GET(
                self._api_client.configuration.host.rstrip("/") + url,
                headers=self._api_client.default_headers,
                preload_content=False,
            )
            with dest.open("wb") as fh:
                shutil.copyfileobj(response, fh)
            response.release_conn()
        except Exception as exc:
            if hasattr(exc, "status"):
                raise_for_status(exc)
            raise
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

        Requires ``pip install 'shepard[pandas]'``.

        This is the pre-P10 implementation: it paginates
        ``GET /timeseriesContainers/{cid}/timeseries/{tid}/payload`` and
        concatenates pages into a single DataFrame.  Once P10 ships, the
        implementation will switch to a single streaming SQL call without
        changing this signature.

        Args:
            timeseries_id: Numeric ID of the timeseries.
            container_id: ID of the parent TimeseriesContainer.  Required by
                the backend endpoint if not already implicit in the path.
            start: Optional start timestamp (datetime or ISO-8601 string).
            end: Optional end timestamp (datetime or ISO-8601 string).
            fields: Optional list of field names to include.
            chunksize: If set, process data in chunks of this many rows to
                bound peak memory usage.

        Returns:
            A ``pandas.DataFrame`` with one row per data point.
        """
        try:
            import pandas as pd  # noqa: PLC0415
        except ImportError as exc:
            raise ImportError(
                "to_pandas requires pandas.  "
                "Install with: pip install 'shepard[pandas]'"
            ) from exc

        if container_id is None:
            raise ValueError(
                "container_id is required for to_pandas (pre-P10 shape). "
                "Pass the TimeseriesContainer's numeric ID."
            )

        proxy = self.timeseries  # type: _DomainProxy
        api = object.__getattribute__(proxy, "_api")

        kwargs: dict[str, object] = {}
        if start is not None:
            kwargs["start"] = start
        if end is not None:
            kwargs["end"] = end
        if fields is not None:
            kwargs["fields"] = fields

        chunks: list["pd.DataFrame"] = []
        page = 0
        page_size = chunksize or 1000

        while True:
            try:
                data = api.get_all_data_points(
                    timeseries_container_id=container_id,
                    timeseries_id=timeseries_id,
                    page=page,
                    size=page_size,
                    **kwargs,
                )
            except Exception as exc:
                if hasattr(exc, "status"):
                    raise_for_status(exc)
                raise

            if not data:
                break

            # The generated client returns pydantic models; convert to dicts.
            rows = [
                item.model_dump() if hasattr(item, "model_dump") else vars(item)
                for item in data
            ]
            chunks.append(pd.DataFrame(rows))

            if len(data) < page_size:
                break
            page += 1

        if not chunks:
            return pd.DataFrame()
        return pd.concat(chunks, ignore_index=True)

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

        Requires ``pip install 'shepard[excel]'``.

        Args:
            timeseries_id: Numeric ID of the timeseries.
            path: Destination ``.xlsx`` file path.
            container_id: ID of the parent TimeseriesContainer.
            start: Optional start timestamp.
            end: Optional end timestamp.
            fields: Optional list of field names.

        Returns:
            Resolved ``Path`` of the written file.
        """
        try:
            import pandas as pd  # noqa: PLC0415, F401
            import openpyxl  # noqa: PLC0415, F401
        except ImportError as exc:
            raise ImportError(
                "to_excel requires pandas + openpyxl.  "
                "Install with: pip install 'shepard[excel]'"
            ) from exc

        df = self.to_pandas(
            timeseries_id,
            container_id=container_id,
            start=start,
            end=end,
            fields=fields,
        )
        dest = Path(path)
        dest.parent.mkdir(parents=True, exist_ok=True)
        df.to_excel(str(dest), index=False, engine="openpyxl")
        return dest.resolve()


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
