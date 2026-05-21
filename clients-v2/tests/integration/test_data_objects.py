"""Integration tests for GET/POST/PATCH/DELETE /v2/collections/{appId}/data-objects."""

from __future__ import annotations

import uuid

import httpx
import pytest


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _create_data_object(
    http: httpx.Client, col_app_id: str, title: str
) -> dict:
    resp = http.post(
        f"/v2/collections/{col_app_id}/data-objects",
        json={"title": title, "description": "integration test — safe to delete"},
    )
    assert resp.status_code == 201, f"DataObject create failed: {resp.text}"
    return resp.json()


# ---------------------------------------------------------------------------
# List
# ---------------------------------------------------------------------------


def test_list_data_objects_returns_200(
    http: httpx.Client, managed_collection: dict
) -> None:
    col_id = managed_collection["appId"]
    resp = http.get(f"/v2/collections/{col_id}/data-objects")
    assert resp.status_code == 200, resp.text


def test_list_data_objects_is_list(
    http: httpx.Client, managed_collection: dict
) -> None:
    col_id = managed_collection["appId"]
    body = http.get(f"/v2/collections/{col_id}/data-objects").json()
    assert isinstance(body, list)


def test_list_data_objects_unknown_collection_returns_404(
    http: httpx.Client,
) -> None:
    fake_id = str(uuid.uuid4())
    resp = http.get(f"/v2/collections/{fake_id}/data-objects")
    assert resp.status_code == 404


# ---------------------------------------------------------------------------
# Create / Get / Delete lifecycle
# ---------------------------------------------------------------------------


def test_create_data_object(
    http: httpx.Client, managed_collection: dict, unique_name: str
) -> None:
    col_id = managed_collection["appId"]
    do = _create_data_object(http, col_id, unique_name)

    assert "appId" in do, f"Created DataObject has no appId: {do}"
    assert do.get("title") == unique_name


def test_get_data_object_by_app_id(
    http: httpx.Client, managed_collection: dict, unique_name: str
) -> None:
    col_id = managed_collection["appId"]
    do = _create_data_object(http, col_id, unique_name)
    do_id = do["appId"]

    resp = http.get(f"/v2/collections/{col_id}/data-objects/{do_id}")
    assert resp.status_code == 200, resp.text
    assert resp.json()["appId"] == do_id

    http.delete(f"/v2/collections/{col_id}/data-objects/{do_id}")


def test_get_unknown_data_object_returns_404(
    http: httpx.Client, managed_collection: dict
) -> None:
    col_id = managed_collection["appId"]
    fake_id = str(uuid.uuid4())
    resp = http.get(f"/v2/collections/{col_id}/data-objects/{fake_id}")
    assert resp.status_code == 404


def test_patch_data_object_title(
    http: httpx.Client, managed_collection: dict, unique_name: str
) -> None:
    col_id = managed_collection["appId"]
    do = _create_data_object(http, col_id, unique_name)
    do_id = do["appId"]

    new_title = f"patched-{uuid.uuid4().hex[:6]}"
    resp = http.patch(
        f"/v2/collections/{col_id}/data-objects/{do_id}",
        json={"title": new_title},
        headers={"Content-Type": "application/merge-patch+json"},
    )
    assert resp.status_code in (200, 204), f"PATCH failed: {resp.text}"

    fetched = http.get(
        f"/v2/collections/{col_id}/data-objects/{do_id}"
    ).json()
    assert fetched["title"] == new_title

    http.delete(f"/v2/collections/{col_id}/data-objects/{do_id}")


def test_delete_data_object(
    http: httpx.Client, managed_collection: dict, unique_name: str
) -> None:
    col_id = managed_collection["appId"]
    do = _create_data_object(http, col_id, unique_name)
    do_id = do["appId"]

    del_resp = http.delete(f"/v2/collections/{col_id}/data-objects/{do_id}")
    assert del_resp.status_code in (200, 204), f"DELETE failed: {del_resp.text}"

    get_resp = http.get(f"/v2/collections/{col_id}/data-objects/{do_id}")
    assert get_resp.status_code == 404, "DataObject still accessible after DELETE"


def test_data_object_appears_in_collection_list(
    http: httpx.Client, managed_collection: dict, unique_name: str
) -> None:
    col_id = managed_collection["appId"]
    do = _create_data_object(http, col_id, unique_name)
    do_id = do["appId"]

    body = http.get(f"/v2/collections/{col_id}/data-objects").json()
    ids = [item["appId"] for item in body]
    assert do_id in ids, f"Created DataObject {do_id} not found in list: {ids}"

    http.delete(f"/v2/collections/{col_id}/data-objects/{do_id}")
