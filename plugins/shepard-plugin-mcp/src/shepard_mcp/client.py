"""
Async HTTP client for the Shepard API.

Usage pattern (per request):
    async with ShepardClient(base_url, token) as c:
        data = await c.get("/v2/collections")

All methods raise httpx.HTTPStatusError on 4xx/5xx.
"""

from __future__ import annotations

import os
import httpx


_DEFAULT_BASE = os.environ.get("SHEPARD_API_BASE", "http://localhost:8080")


class ShepardClient:
    """Thin async wrapper around httpx for Shepard API calls.

    Instantiate per-request with the caller's bearer token so the server
    remains stateless and forwards auth correctly.
    """

    def __init__(self, token: str, base_url: str = _DEFAULT_BASE) -> None:
        self._base = base_url.rstrip("/")
        self._headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
        }
        self._client: httpx.AsyncClient | None = None

    async def __aenter__(self) -> "ShepardClient":
        self._client = httpx.AsyncClient(
            base_url=self._base,
            headers=self._headers,
            timeout=30.0,
        )
        return self

    async def __aexit__(self, *_: object) -> None:
        if self._client:
            await self._client.aclose()

    # ── low-level helpers ─────────────────────────────────────────────────

    async def get(self, path: str, **params: object) -> object:
        assert self._client, "use as async context manager"
        filtered = {k: v for k, v in params.items() if v is not None}
        r = await self._client.get(path, params=filtered)
        r.raise_for_status()
        return r.json()

    async def post(self, path: str, body: dict) -> object:
        assert self._client, "use as async context manager"
        r = await self._client.post(path, json=body)
        r.raise_for_status()
        return r.json()

    async def delete(self, path: str) -> None:
        assert self._client, "use as async context manager"
        r = await self._client.delete(path)
        r.raise_for_status()

    # ── convenience wrappers ──────────────────────────────────────────────

    # Collections
    async def list_collections(self, name: str | None = None, page: int = 0, size: int = 50) -> object:
        return await self.get("/v2/collections", name=name, page=page, size=size)

    async def get_collection(self, collection_app_id: str) -> object:
        return await self.get(f"/v2/collections/{collection_app_id}")

    # DataObjects
    async def list_data_objects(
        self, collection_app_id: str, name: str | None = None, page: int = 0, size: int = 50
    ) -> object:
        return await self.get(
            f"/v2/collections/{collection_app_id}/data-objects",
            name=name, page=page, size=size,
        )

    async def get_data_object(self, collection_app_id: str, data_object_app_id: str) -> object:
        return await self.get(
            f"/v2/collections/{collection_app_id}/data-objects/{data_object_app_id}"
        )

    async def get_predecessors(self, collection_app_id: str, data_object_app_id: str) -> object:
        return await self.get(
            f"/v2/collections/{collection_app_id}/data-objects/{data_object_app_id}/predecessors"
        )

    async def get_successors(self, collection_app_id: str, data_object_app_id: str) -> object:
        return await self.get(
            f"/v2/collections/{collection_app_id}/data-objects/{data_object_app_id}/successors"
        )

    async def get_children(self, collection_app_id: str, data_object_app_id: str) -> object:
        return await self.get(
            f"/v2/collections/{collection_app_id}/data-objects/{data_object_app_id}/children"
        )

    async def get_predecessor_chain(
        self, collection_app_id: str, data_object_app_id: str, depth: int = 10
    ) -> object:
        return await self.get(
            f"/v2/collections/{collection_app_id}/data-objects/{data_object_app_id}/predecessor-chain",
            depth=depth,
        )

    async def get_successor_chain(
        self, collection_app_id: str, data_object_app_id: str, depth: int = 10
    ) -> object:
        return await self.get(
            f"/v2/collections/{collection_app_id}/data-objects/{data_object_app_id}/successor-chain",
            depth=depth,
        )

    # Annotations
    async def list_annotations(self, collection_id: int, data_object_id: int) -> object:
        return await self.get(
            f"/shepard/api/collections/{collection_id}/dataObjects/{data_object_id}/semanticAnnotations"
        )

    # Timeseries — v2 stats (uses numeric container OGM id)
    async def get_timeseries_stats(self, container_id: int) -> object:
        return await self.get(f"/v2/timeseries-containers/{container_id}/stats")

    # Timeseries — v1 channel list (uses numeric container OGM id)
    async def list_channels(self, container_id: int) -> object:
        return await self.get(f"/shepard/api/timeseriesContainers/{container_id}/timeseries")

    # Timeseries — v1 payload (uses numeric collection/dataObject/reference ids)
    async def get_channel_data(
        self,
        collection_id: int,
        data_object_id: int,
        reference_id: int,
        measurement: str | None = None,
        device: str | None = None,
        location: str | None = None,
        symbolic_name: str | None = None,
        field: str | None = None,
        group_by: int | None = None,
        function: str | None = None,
    ) -> object:
        return await self.get(
            f"/shepard/api/collections/{collection_id}/dataObjects/{data_object_id}"
            f"/timeseriesReferences/{reference_id}/payload",
            measurement=measurement,
            device=device,
            location=location,
            symbolicName=symbolic_name,
            field=field,
            groupBy=group_by,
            function=function,
        )

    # Structured data — v1 payload (uses numeric collection/dataObject/reference ids)
    async def get_structured_data(
        self, collection_id: int, data_object_id: int, reference_id: int
    ) -> object:
        return await self.get(
            f"/shepard/api/collections/{collection_id}/dataObjects/{data_object_id}"
            f"/structuredDataReferences/{reference_id}/payload"
        )

    # Files — v1 list (uses numeric collection/dataObject/reference ids)
    async def list_files(
        self, collection_id: int, data_object_id: int, reference_id: int
    ) -> object:
        return await self.get(
            f"/shepard/api/collections/{collection_id}/dataObjects/{data_object_id}"
            f"/fileBundleReferences/{reference_id}/payload"
        )

    # Instance info
    async def get_health(self) -> object:
        return await self.get("/shepard/api/healthz/ready")
