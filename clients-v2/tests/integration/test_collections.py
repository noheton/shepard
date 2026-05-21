"""Integration tests for GET/POST/PATCH/DELETE /v2/collections."""

from __future__ import annotations

import uuid

import httpx
import pytest


# ---------------------------------------------------------------------------
# List
# ---------------------------------------------------------------------------


def test_list_collections_returns_200(http: httpx.Client) -> None:
    resp = http.get("/v2/collections")
    assert resp.status_code == 200, resp.text


def test_list_collections_is_list(http: httpx.Client) -> None:
    body = http.get("/v2/collections").json()
    assert isinstance(body, list), f"Expected list, got {type(body).__name__}"


def test_list_collections_items_have_app_id(http: httpx.Client) -> None:
    body = http.get("/v2/collections").json()
    for item in body[:5]:
        assert "appId" in item, f"Collection item missing 'appId': {item}"


# ---------------------------------------------------------------------------
# Create / Get / Delete lifecycle
# ---------------------------------------------------------------------------


def test_create_collection_returns_201(
    http: httpx.Client, managed_collection: dict
) -> None:
    assert managed_collection.get("appId"), "Created collection has no appId"


def test_create_collection_echoes_title(
    http: httpx.Client, managed_collection: dict, unique_name: str
) -> None:
    assert managed_collection.get("title") == unique_name


def test_get_collection_by_app_id(
    http: httpx.Client, managed_collection: dict
) -> None:
    app_id = managed_collection["appId"]
    resp = http.get(f"/v2/collections/{app_id}")
    assert resp.status_code == 200, resp.text
    assert resp.json()["appId"] == app_id


def test_get_unknown_collection_returns_404(http: httpx.Client) -> None:
    fake_id = str(uuid.uuid4())
    resp = http.get(f"/v2/collections/{fake_id}")
    assert resp.status_code == 404, (
        f"Expected 404 for unknown appId, got {resp.status_code}"
    )


def test_patch_collection_title(
    http: httpx.Client, managed_collection: dict
) -> None:
    app_id = managed_collection["appId"]
    new_title = f"patched-{uuid.uuid4().hex[:6]}"
    resp = http.patch(
        f"/v2/collections/{app_id}",
        json={"title": new_title},
        headers={"Content-Type": "application/merge-patch+json"},
    )
    assert resp.status_code in (200, 204), f"PATCH failed: {resp.text}"

    fetched = http.get(f"/v2/collections/{app_id}").json()
    assert fetched["title"] == new_title, (
        f"Title not updated: expected '{new_title}', got '{fetched.get('title')}'"
    )


def test_delete_collection(http: httpx.Client, unique_name: str) -> None:
    resp = http.post(
        "/v2/collections",
        json={"title": unique_name, "description": "delete-test"},
    )
    assert resp.status_code == 201
    app_id = resp.json()["appId"]

    del_resp = http.delete(f"/v2/collections/{app_id}")
    assert del_resp.status_code in (200, 204), f"DELETE failed: {del_resp.text}"

    get_resp = http.get(f"/v2/collections/{app_id}")
    assert get_resp.status_code == 404, (
        "Collection still accessible after DELETE"
    )


def test_create_requires_title(http: httpx.Client) -> None:
    resp = http.post("/v2/collections", json={"description": "no title"})
    assert resp.status_code in (400, 422), (
        f"Expected 400/422 for missing title, got {resp.status_code}"
    )
