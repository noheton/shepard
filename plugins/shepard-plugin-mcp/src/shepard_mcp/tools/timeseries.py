"""MCP tools: Timeseries containers and channel data (MCP-1b)."""

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


def _lttb(points: list[dict], max_points: int) -> list[dict]:
    """Largest-Triangle-Three-Buckets downsampling (pure Python).

    Reduces a list of {timestamp, value} dicts to at most max_points while
    preserving the visual shape. O(n) time, no external dependencies.
    """
    n = len(points)
    if n <= max_points:
        return points

    bucket_size = (n - 2) / (max_points - 2)
    sampled = [points[0]]
    a = 0

    for i in range(max_points - 2):
        avg_start = int((i + 1) * bucket_size) + 1
        avg_end = int((i + 2) * bucket_size) + 1
        avg_end = min(avg_end, n)

        avg_x = sum(j for j in range(avg_start, avg_end)) / (avg_end - avg_start)
        avg_y = sum(p.get("value", 0) or 0 for p in points[avg_start:avg_end]) / (avg_end - avg_start)

        range_start = int(i * bucket_size) + 1
        range_end = int((i + 1) * bucket_size) + 1

        max_area = -1.0
        best = range_start
        ax = a
        ay = points[a].get("value", 0) or 0.0

        for j in range(range_start, range_end):
            bx = j
            by = points[j].get("value", 0) or 0.0
            area = abs((ax - avg_x) * (by - ay) - (ax - bx) * (avg_y - ay)) * 0.5
            if area > max_area:
                max_area = area
                best = j

        sampled.append(points[best])
        a = best

    sampled.append(points[-1])
    return sampled


def register(mcp):  # noqa: ANN001
    """Register timeseries tools on the FastMCP instance."""

    @mcp.tool()
    async def list_channels(
        ctx: Context,
        container_id: Annotated[
            int,
            "Numeric OGM id of the TimeseriesContainer — the 'containerId' field "
            "inside containers.timeseries[] from get_data_object.",
        ],
    ) -> object:
        """List all timeseries channels available in a container.

        Returns a list of channel descriptors. Each descriptor has:
        measurement, device, location, symbolicName, field — the 5-tuple
        that identifies a channel in the v1 API.

        Pass any of these values to get_channel_data or get_channel_summary
        to fetch actual sensor readings.

        Example: list_channels(containerId=1234) →
          [{measurement: "vibration", device: "turbopump", field: "rms_g"}, ...]
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.list_channels(container_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def get_timeseries_stats(
        ctx: Context,
        container_id: Annotated[
            int,
            "Numeric OGM id of the TimeseriesContainer — the 'containerId' field "
            "inside containers.timeseries[] from get_data_object.",
        ],
    ) -> object:
        """Get storage and ingest statistics for a timeseries container.

        Returns: {pointCount, channelCount, estimatedSizeBytes,
        recentPointsLast10s, ingestRateBytesPerSec}.

        Useful for understanding data volume before fetching raw channel data.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_timeseries_stats(container_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def get_channel_data(
        ctx: Context,
        collection_id: Annotated[int, "Numeric OGM id of the Collection (the 'id' field from get_collection)."],
        data_object_id: Annotated[int, "Numeric OGM id of the DataObject (the 'id' field from get_data_object)."],
        reference_id: Annotated[
            int,
            "Numeric OGM id of the timeseries reference node — the 'referenceId' field "
            "inside containers.timeseries[] from get_data_object.",
        ],
        measurement: Annotated[str | None, "Filter by measurement name (e.g. 'vibration')."] = None,
        device: Annotated[str | None, "Filter by device tag (e.g. 'turbopump')."] = None,
        location: Annotated[str | None, "Filter by location tag."] = None,
        symbolic_name: Annotated[str | None, "Filter by symbolic name tag."] = None,
        field: Annotated[str | None, "Filter by field name (e.g. 'rms_g')."] = None,
        group_by: Annotated[
            int | None,
            "Time bucket size in milliseconds for aggregation. "
            "E.g. 1000 = 1-second buckets. Omit for raw data.",
        ] = None,
        function: Annotated[
            str | None,
            "Aggregation function: MEAN, MAX, MIN, SUM, COUNT. Requires group_by.",
        ] = None,
        max_points: Annotated[
            int,
            "Maximum data points to return after LTTB downsampling. Default 2000.",
        ] = 2000,
    ) -> object:
        """Fetch raw or aggregated timeseries data for one or more channels.

        Specify measurement and/or field to select a specific channel.
        Omit both to retrieve all channels (may be large — set group_by + function
        for aggregated overview).

        Returns a list of {time, [channel_field]: value} rows, downsampled to
        max_points using LTTB when the raw result exceeds that count.

        Critical use case: get_channel_data(collection_id=.., data_object_id=..,
        reference_id=.., measurement="vibration", field="rms_g") fetches the
        turbopump vibration trace for TR-004 anomaly analysis.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                result = await c.get_channel_data(
                    collection_id=collection_id,
                    data_object_id=data_object_id,
                    reference_id=reference_id,
                    measurement=measurement,
                    device=device,
                    location=location,
                    symbolic_name=symbolic_name,
                    field=field,
                    group_by=group_by,
                    function=function,
                )
                if isinstance(result, list) and len(result) > max_points:
                    result = _lttb(result, max_points)
                    return {
                        "data": result,
                        "downsampled": True,
                        "originalCount": len(result),
                        "note": f"LTTB downsampled to {max_points} points. Use group_by for aggregation.",
                    }
                return result
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def get_channel_summary(
        ctx: Context,
        collection_id: Annotated[int, "Numeric OGM id of the Collection."],
        data_object_id: Annotated[int, "Numeric OGM id of the DataObject."],
        reference_id: Annotated[int, "Numeric id of the timeseries reference node."],
        measurement: Annotated[str | None, "Filter by measurement name."] = None,
        device: Annotated[str | None, "Filter by device tag."] = None,
        field: Annotated[str | None, "Filter by field name."] = None,
    ) -> object:
        """Get a statistical summary (min/max/mean/count) for a channel.

        Uses server-side aggregation to avoid transferring raw data.
        Returns one row per matching channel with min, max, mean, count.

        Use this to quickly characterise a channel before fetching raw data:
        e.g. "what is the peak vibration in TR-004?" → max value of the rms_g field.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_channel_data(
                    collection_id=collection_id,
                    data_object_id=data_object_id,
                    reference_id=reference_id,
                    measurement=measurement,
                    device=device,
                    field=field,
                    function="MEAN",
                    group_by=None,
                )
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def compare_channels(
        ctx: Context,
        collection_id: Annotated[int, "Numeric OGM id of the Collection."],
        data_object_id: Annotated[int, "Numeric OGM id of the DataObject."],
        reference_id: Annotated[int, "Numeric id of the timeseries reference node."],
        measurements: Annotated[
            list[str],
            "List of measurement names to fetch (e.g. ['vibration', 'thrust', 'mixture_ratio']).",
        ],
        field: Annotated[str | None, "Shared field name across all measurements (if uniform)."] = None,
        group_by: Annotated[int | None, "Time bucket size in ms for alignment. Recommended: 1000."] = 1000,
        max_points: Annotated[int, "Max points per channel after downsampling. Default 1000."] = 1000,
    ) -> object:
        """Fetch multiple channels from the same container for side-by-side comparison.

        Fetches each measurement separately, then returns them as a dict of
        {measurement_name: [data_points]}. All series are time-aligned via
        server-side grouping (group_by in ms).

        Critical use case: compare vibration + thrust + mixture_ratio for TR-004
        to understand the causal sequence of the turbopump anomaly.
        """
        async with ShepardClient(_token(ctx)) as c:
            results = {}
            errors = {}
            for m in measurements:
                try:
                    data = await c.get_channel_data(
                        collection_id=collection_id,
                        data_object_id=data_object_id,
                        reference_id=reference_id,
                        measurement=m,
                        field=field,
                        group_by=group_by,
                        function="MEAN",
                    )
                    if isinstance(data, list) and len(data) > max_points:
                        data = _lttb(data, max_points)
                    results[m] = data
                except httpx.HTTPStatusError as e:
                    errors[m] = {"error": str(e), "status": e.response.status_code}

            return {"channels": results, "errors": errors or None}

    @mcp.tool()
    async def get_structured_data(
        ctx: Context,
        collection_id: Annotated[int, "Numeric OGM id of the Collection."],
        data_object_id: Annotated[int, "Numeric OGM id of the DataObject."],
        reference_id: Annotated[
            int,
            "Numeric OGM id of the structured-data reference node — the 'referenceId' field "
            "inside containers.structuredData[] from get_data_object.",
        ],
    ) -> object:
        """Fetch the JSON payload of a structured-data container.

        Structured-data containers hold arbitrary JSON documents attached to a
        DataObject — test parameters, run configs, process settings, etc.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.get_structured_data(collection_id, data_object_id, reference_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}

    @mcp.tool()
    async def list_files(
        ctx: Context,
        collection_id: Annotated[int, "Numeric OGM id of the Collection."],
        data_object_id: Annotated[int, "Numeric OGM id of the DataObject."],
        reference_id: Annotated[
            int,
            "Numeric OGM id of the file-bundle reference node — the 'referenceId' field "
            "inside containers.files[] from get_data_object.",
        ],
    ) -> object:
        """List all files in a file-bundle container.

        Returns a list of {oid, name, mimeType, size, createdAt}.
        Use the 'oid' to construct a download URL if needed.
        """
        async with ShepardClient(_token(ctx)) as c:
            try:
                return await c.list_files(collection_id, data_object_id, reference_id)
            except httpx.HTTPStatusError as e:
                return {"error": str(e), "status": e.response.status_code}
