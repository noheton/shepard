"""
pytest conftest — install a minimal ``shepard_client`` stub into ``sys.modules``
before any test module is imported.

This lets the test suite run without the generated package (which lives on a
private GitLab PyPI index) installed.  The stub is deliberately minimal: it
only implements the attributes that ``shepard.client`` and ``shepard._proxies``
actually touch at construction and call time.
"""

from __future__ import annotations

import sys
import types


# ---------------------------------------------------------------------------
# Fake ApiException
# ---------------------------------------------------------------------------

class FakeApiException(Exception):
    def __init__(self, status: int, body: str = "", headers: dict | None = None) -> None:
        super().__init__(f"({status}) {body}")
        self.status = status
        self.body = body
        self.headers = headers or {}


# ---------------------------------------------------------------------------
# Fake Configuration
# ---------------------------------------------------------------------------

class FakeConfiguration:
    def __init__(self, host: str = "") -> None:
        self.host = host
        self.api_key: dict[str, str] = {}
        self.access_token: str | None = None
        self.verify_ssl: bool = True
        self.ssl_ca_cert: str | None = None


# ---------------------------------------------------------------------------
# Fake pool manager / rest client / ApiClient
# ---------------------------------------------------------------------------

class FakePoolManager:
    def __init__(self) -> None:
        self.connection_pool_kw: dict = {}


class FakeRestClient:
    def __init__(self) -> None:
        self.pool_manager = FakePoolManager()


class FakeApiClient:
    def __init__(self, configuration: FakeConfiguration | None = None) -> None:
        self.configuration = configuration or FakeConfiguration()
        self.rest_client = FakeRestClient()
        self.default_headers: dict = {}


# ---------------------------------------------------------------------------
# Fake CollectionApi (and a generic factory for other *Api stubs)
# ---------------------------------------------------------------------------

class FakeCollectionApi:
    def __init__(self, api_client: FakeApiClient) -> None:
        self._api_client = api_client

    def get_all_collections(self, page: int = 0, size: int = 100, **kwargs):
        return []

    def create_collection(self, collection=None):
        return collection

    def export_collection(self, collection_id: int = 0) -> bytes:
        return b"FAKE-RO-CRATE-CONTENT"


def _make_fake_api_class(name: str) -> type:
    """Return a minimal stub for any ``*Api`` class."""
    def __init__(self, api_client):
        self._api_client = api_client

    return type(name, (), {"__init__": __init__})


# ---------------------------------------------------------------------------
# Build the stub module tree and inject into sys.modules
# ---------------------------------------------------------------------------

def _make_stub_shepard_client() -> None:
    # Root package
    root = types.ModuleType("shepard_client")
    root.Configuration = FakeConfiguration
    root.ApiClient = FakeApiClient
    root.ApiException = FakeApiException

    # shepard_client.api sub-package
    api_pkg = types.ModuleType("shepard_client.api")

    # Expose all Api classes the _DOMAIN_MAP references plus a couple of extras
    # so the coverage check emits warnings rather than blowing up.
    api_class_names = [
        "CollectionApi",
        "DataObjectApi",
        "TimeseriesContainerApi",
        "FileContainerApi",
        "StructuredDataContainerApi",
        "SpatialDataContainerApi",
        "SearchApi",
        "ApikeyApi",
        "SubscriptionApi",
        "SemanticAnnotationApi",
        "UserApi",
        "UsergroupApi",
        "VersionzApi",
    ]
    for cls_name in api_class_names:
        if cls_name == "CollectionApi":
            cls = FakeCollectionApi
        else:
            cls = _make_fake_api_class(cls_name)
        setattr(api_pkg, cls_name, cls)

        # Also create a per-class module (shepard_client.api.collection_api, …)
        from shepard.client import _class_name_to_module  # noqa: PLC0415
        mod_name = f"shepard_client.api.{_class_name_to_module(cls_name)}"
        mod = types.ModuleType(mod_name)
        setattr(mod, cls_name, cls)
        sys.modules[mod_name] = mod

    root.api = api_pkg

    # shepard_client.models sub-package (minimal)
    models_pkg = types.ModuleType("shepard_client.models")
    root.models = models_pkg

    sys.modules["shepard_client"] = root
    sys.modules["shepard_client.api"] = api_pkg
    sys.modules["shepard_client.models"] = models_pkg


# Install the stub before any test module imports ``shepard``
_make_stub_shepard_client()
