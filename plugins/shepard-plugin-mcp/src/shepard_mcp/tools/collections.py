"""MCP tools: Collections and DataObjects (MCP-1a)."""

from __future__ import annotations

from typing import Annotated

import httpx
from fastmcp import Context

from shepard_mcp.client import ShepardClient


def _token(ctx: Context) -> str:
    token = ctx.request_context.request.headers.get("authorization", "")
    if token.startswith("Bearer "):
        return token[7:]
    return token


def register(mcp):  # noqa: ANN001
    """Register collection + data-object tools on the FastMCP instance."""

    # ── Collections ──────────────────────────────────────────────────────

    @mcp.tool()
    async def list_collections(
        ctx: Context,
        name: Annotated[str | None, "Filter by name substring (case-insensitive)."] = None,
        page: Annotated[int, "Zero-based page number."] = 0,
        size: Annotated[int, "Page size (max 100)."] = 50,
    ) -> object:
        """List all Collections the caller has Read access to.

        Returns a page of {appId, name, description, dataObjectCount}.
        Use `name` to narrow results when there are many collections.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.list_collections(name=name, page=page, size=min(size, 100))
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def get_collection(
        ctx: Context,
        collection_app_id: Annotated[str, "UUID v7 of the Collection."],
    ) -> object:
        """Get a single Collection by its appId.

        Returns the full Collection with name, description, attributes, and counts.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_collection(collection_app_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    # ── DataObjects ───────────────────────────────────────────────────────

    @mcp.tool()
    async def list_data_objects(
        ctx: Context,
        collection_app_id: Annotated[str, "UUID v7 of the parent Collection."],
        name: Annotated[str | None, "Filter by name substring."] = None,
        page: Annotated[int, "Zero-based page number."] = 0,
        size: Annotated[int, "Page size (max 100)."] = 50,
    ) -> object:
        """List DataObjects inside a Collection.

        Each row includes: appId, name, status, and per-kind container counts
        (timeseriesCount, fileCount, structuredDataCount).

        Workflow: call get_data_object(collectionAppId, dataObjectAppId) on an
        individual row to get its containers and predecessor/successor chain.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.list_data_objects(
                    collection_app_id, name=name, page=page, size=min(size, 100)
                )
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def get_data_object(
        ctx: Context,
        collection_app_id: Annotated[str, "UUID v7 of the parent Collection."],
        data_object_app_id: Annotated[str, "UUID v7 of the DataObject."],
    ) -> object:
        """Get full detail for a DataObject.

        Returns:
        - appId, name, description, status, attributes
        - containers.timeseries[]: [{containerAppId, containerName, containerId, referenceId}]
        - containers.files[]:      [{containerAppId, containerName, containerId, referenceId}]
        - containers.structuredData[]: [{containerAppId, containerName, containerId, referenceId}]
        - predecessorSummaries, successorSummaries, childSummaries, parentSummary

        Use containerId with list_channels / get_channel_data / get_channel_summary.
        Use containerId with get_structured_data and list_files.
        Use referenceId with get_channel_data for the v1 payload path.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_data_object(collection_app_id, data_object_app_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    # ── Ancestry / provenance ─────────────────────────────────────────────

    @mcp.tool()
    async def get_predecessor_chain(
        ctx: Context,
        collection_app_id: Annotated[str, "UUID v7 of the Collection."],
        data_object_app_id: Annotated[str, "UUID v7 of the DataObject."],
        depth: Annotated[int, "Maximum chain depth (default 10)."] = 10,
    ) -> object:
        """Walk the predecessor chain (history) of a DataObject.

        Returns an ordered list of DataObjects from the oldest ancestor down to
        the direct predecessor, up to `depth` hops. Useful for understanding
        the full history of a test run or production step.

        Example: TR-006 → predecessor-chain → [TR-004, TR-005, TR-006-Investigation]
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_predecessor_chain(
                    collection_app_id, data_object_app_id, depth=depth
                )
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def get_successor_chain(
        ctx: Context,
        collection_app_id: Annotated[str, "UUID v7 of the Collection."],
        data_object_app_id: Annotated[str, "UUID v7 of the DataObject."],
        depth: Annotated[int, "Maximum chain depth (default 10)."] = 10,
    ) -> object:
        """Walk the successor chain (forward history) of a DataObject.

        Returns an ordered list of DataObjects from this node forward to its
        furthest successor, up to `depth` hops. Useful for understanding what
        happened after an anomaly: repair steps, re-tests, signoff events.

        Example: TR-004 → successor-chain → [TR-005, TR-006]
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_successor_chain(
                    collection_app_id, data_object_app_id, depth=depth
                )
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def get_children(
        ctx: Context,
        collection_app_id: Annotated[str, "UUID v7 of the Collection."],
        data_object_app_id: Annotated[str, "UUID v7 of the DataObject."],
    ) -> object:
        """List the direct child DataObjects of a parent DataObject.

        Children represent sub-investigations, sub-steps, or sub-experiments
        nested under the parent. Different from predecessors/successors which
        represent temporal/causal sequence.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_children(collection_app_id, data_object_app_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def list_annotations(
        ctx: Context,
        collection_id: Annotated[int, "Numeric OGM id of the Collection (the 'id' field from get_collection)."],
        data_object_id: Annotated[int, "Numeric OGM id of the DataObject (the 'id' field from get_data_object)."],
    ) -> object:
        """List all semantic annotations attached to a DataObject.

        Returns a list of {propertyName, propertyIRI, valueName, valueIRI,
        numericValue, unitIRI}. The numericValue + unitIRI fields enable
        machine-queryable constraint checking (e.g. temperature > max_allowed).

        Note: use the numeric 'id' field from get_data_object, not the appId.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.list_annotations(collection_id, data_object_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}
