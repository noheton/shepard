"""
Unit tests for ``shepard.client.Client``.

The ``conftest.py`` installs a minimal ``shepard_client`` stub before any
import, so these tests run without the generated package installed.
"""

from __future__ import annotations

import pytest

import shepard
from shepard.client import Client, _class_name_to_module
from shepard.errors import ShepardNotFound
from shepard._proxies import _DomainProxy


# ---------------------------------------------------------------------------
# Constructor tests
# ---------------------------------------------------------------------------

def test_client_constructor_stores_host() -> None:
    sh = Client(host="https://example.com/shepard/api", apikey="key123")
    assert sh._api_client.configuration.host == "https://example.com/shepard/api"


def test_client_constructor_stores_apikey() -> None:
    sh = Client(host="https://example.com", apikey="my-key")
    assert sh._api_client.configuration.api_key == {"apikey": "my-key"}


def test_client_constructor_bearer_token() -> None:
    sh = Client(host="https://example.com", bearer_token="tok123")
    assert sh._api_client.configuration.access_token == "tok123"


def test_client_constructor_apikey_wins_over_bearer(recwarn) -> None:
    """When both apikey and bearer_token supplied, apikey wins and a warning is emitted."""
    sh = Client(host="https://example.com", apikey="k", bearer_token="t")
    assert sh._api_client.configuration.api_key == {"apikey": "k"}
    # Should have emitted a UserWarning
    assert any("Both apikey and bearer_token" in str(w.message) for w in recwarn.list)


def test_client_constructor_verify_ssl_true_by_default() -> None:
    sh = Client(host="https://example.com", apikey="k")
    assert sh._api_client.configuration.verify_ssl is True


def test_client_constructor_verify_ssl_false() -> None:
    sh = Client(host="https://example.com", apikey="k", verify_ssl=False)
    assert sh._api_client.configuration.verify_ssl is False


def test_client_constructor_ssl_ca_cert() -> None:
    sh = Client(host="https://example.com", apikey="k", ssl_ca_cert="/path/ca.pem")
    assert sh._api_client.configuration.ssl_ca_cert == "/path/ca.pem"


def test_client_constructor_timeout_stored() -> None:
    sh = Client(host="https://example.com", apikey="k", timeout=30.0)
    # The fake PoolManager should have stored the timeout.
    kw = sh._api_client.rest_client.pool_manager.connection_pool_kw
    assert kw.get("timeout") == 30.0


def test_client_list_cap_default() -> None:
    sh = Client(host="https://example.com", apikey="k")
    assert sh._list_cap == 10_000


def test_client_list_cap_custom() -> None:
    sh = Client(host="https://example.com", apikey="k", list_cap=500)
    assert sh._list_cap == 500


# ---------------------------------------------------------------------------
# Domain proxy lazy creation
# ---------------------------------------------------------------------------

def test_domain_proxy_is_created_lazily() -> None:
    sh = Client(host="https://example.com", apikey="k")
    assert "collections" not in sh._proxies
    _ = sh.collections
    assert "collections" in sh._proxies


def test_domain_proxy_is_cached() -> None:
    sh = Client(host="https://example.com", apikey="k")
    proxy1 = sh.collections
    proxy2 = sh.collections
    assert proxy1 is proxy2


def test_domain_proxy_is_domain_proxy_instance() -> None:
    sh = Client(host="https://example.com", apikey="k")
    assert isinstance(sh.collections, _DomainProxy)


def test_unknown_domain_raises_attribute_error() -> None:
    sh = Client(host="https://example.com", apikey="k")
    with pytest.raises(AttributeError, match="no attribute"):
        _ = sh.nonexistent_domain  # type: ignore[attr-defined]


@pytest.mark.parametrize("domain", [
    "collections", "dataobjects", "timeseries", "files",
    "structured_data", "spatial_data", "search", "api_keys",
    "subscriptions", "semantic", "users", "usergroups", "versionz",
])
def test_all_domains_accessible(domain: str) -> None:
    sh = Client(host="https://example.com", apikey="k")
    proxy = getattr(sh, domain)
    assert isinstance(proxy, _DomainProxy)


# ---------------------------------------------------------------------------
# Error mapping through proxy
# ---------------------------------------------------------------------------

def test_proxy_maps_404_to_not_found() -> None:
    from tests.conftest import FakeApiException  # noqa: PLC0415

    sh = Client(host="https://example.com", apikey="k")

    # Patch get_all_collections on the underlying api to raise ApiException(404).
    api_instance = object.__getattribute__(sh.collections, "_api")

    def _raise_404(**kwargs):
        raise FakeApiException(404, "not found")

    api_instance.get_all_collections = _raise_404

    with pytest.raises(ShepardNotFound):
        sh.collections.get_all_collections()


def test_proxy_raises_non_api_exception_unchanged() -> None:
    sh = Client(host="https://example.com", apikey="k")
    api_instance = object.__getattribute__(sh.collections, "_api")

    def _raise_value_error(**kwargs):
        raise ValueError("internal")

    api_instance.get_all_collections = _raise_value_error

    with pytest.raises(ValueError, match="internal"):
        sh.collections.get_all_collections()


# ---------------------------------------------------------------------------
# iter / list on proxy
# ---------------------------------------------------------------------------

def test_proxy_iter_returns_items() -> None:
    sh = Client(host="https://example.com", apikey="k")
    api_instance = object.__getattribute__(sh.collections, "_api")

    def _fetch(page: int = 0, size: int = 100, **_):
        if page == 0:
            return ["a", "b"]
        return []

    api_instance.get_all_collections = _fetch

    items = list(sh.collections.iter("get_all_collections"))
    assert items == ["a", "b"]


def test_proxy_list_raises_when_exceeds_cap() -> None:
    sh = Client(host="https://example.com", apikey="k", list_cap=3)
    api_instance = object.__getattribute__(sh.collections, "_api")

    def _fetch(page: int = 0, size: int = 100, **_):
        if page == 0:
            return [1, 2, 3, 4]
        return []

    api_instance.get_all_collections = _fetch

    with pytest.raises(ValueError, match="list_cap"):
        sh.collections.list("get_all_collections")


# ---------------------------------------------------------------------------
# Helper: _class_name_to_module
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("class_name, expected", [
    ("CollectionApi", "collection_api"),
    ("DataObjectApi", "data_object_api"),
    ("TimeseriesContainerApi", "timeseries_container_api"),
    ("ApikeyApi", "apikey_api"),
    ("SpatialDataContainerApi", "spatial_data_container_api"),
    ("VersionzApi", "versionz_api"),
    ("SemanticAnnotationApi", "semantic_annotation_api"),
])
def test_class_name_to_module(class_name: str, expected: str) -> None:
    assert _class_name_to_module(class_name) == expected


# ---------------------------------------------------------------------------
# shepard.__init__ re-exports
# ---------------------------------------------------------------------------

def test_init_exports_client() -> None:
    assert hasattr(shepard, "Client")


def test_init_exports_error_hierarchy() -> None:
    from shepard import (  # noqa: PLC0415
        ShepardBadRequest,
        ShepardConflict,
        ShepardError,
        ShepardForbidden,
        ShepardNotFound,
        ShepardServerError,
        ShepardUnauthorized,
        ShepardValidation,
    )
    assert issubclass(ShepardBadRequest, ShepardError)


def test_init_models_is_module_or_none() -> None:
    import types
    assert shepard.models is None or isinstance(shepard.models, types.ModuleType)
