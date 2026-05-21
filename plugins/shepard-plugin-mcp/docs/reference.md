# shepard-plugin-mcp — Reference

MCP (Model Context Protocol) server for Shepard. Exposes collections, data objects, timeseries, files, and structured data to AI agents via SSE.

## SSE Endpoint

```
https://shepard.nuclide.systems/mcp/sse
```

Claude remote connector URL (for claude.ai Settings → Integrations):
```
https://shepard.nuclide.systems/mcp/sse
```

## Authentication

Bearer token pass-through. The caller supplies a Shepard API token in the `Authorization: Bearer <token>` header. The MCP server forwards it on every Shepard API call. No separate MCP credentials.

## Available Tools

### Collections

| Tool | Parameters | Returns |
|---|---|---|
| `list_collections` | `name?`, `page?`, `size?` | Paged list of {appId, name, description, dataObjectCount} |
| `get_collection` | `collectionAppId` | Full Collection with name, description, attributes |

### DataObjects

| Tool | Parameters | Returns |
|---|---|---|
| `list_data_objects` | `collectionAppId`, `name?`, `page?`, `size?` | Paged list with per-kind container counts |
| `get_data_object` | `collectionAppId`, `dataObjectAppId` | Full detail: attributes, typed containers, predecessors/successors |
| `get_predecessor_chain` | `collectionAppId`, `dataObjectAppId`, `depth?` | Ordered list of ancestors |
| `get_successor_chain` | `collectionAppId`, `dataObjectAppId`, `depth?` | Ordered list of successors |
| `get_children` | `collectionAppId`, `dataObjectAppId` | Direct child DataObjects |
| `list_annotations` | `collectionId` (int), `dataObjectId` (int) | Semantic annotations with numeric values |

### Timeseries

| Tool | Parameters | Returns |
|---|---|---|
| `list_channels` | `containerId` (int) | All channels: measurement, device, location, symbolicName, field |
| `get_timeseries_stats` | `containerId` (int) | {pointCount, channelCount, estimatedSizeBytes, ...} |
| `get_channel_data` | `collectionId`, `dataObjectId`, `referenceId`, filters…, `maxPoints?` | [{timestamp, value}] LTTB-downsampled |
| `get_channel_summary` | `collectionId`, `dataObjectId`, `referenceId`, filters… | Server-side mean aggregation |
| `compare_channels` | `collectionId`, `dataObjectId`, `referenceId`, `measurements[]`, `groupBy?` | Multi-channel aligned dict |
| `get_structured_data` | `collectionId`, `dataObjectId`, `referenceId` | JSON payload |
| `list_files` | `collectionId`, `dataObjectId`, `referenceId` | File list with oid, name, mimeType, size |

## ID Types

`get_data_object` returns both ID forms in the response body:

```json
{
  "appId": "019e30b0-...",       // UUID v7 — used by tools that take *AppId params
  "id": 12345,                   // numeric — used by list_channels, get_channel_data, etc.
  "containers": {
    "timeseries": [{
      "containerAppId": "019e40a1-...",   // UUID of the container
      "containerName": "Hot-fire sensors",
      "containerId": 67890,              // numeric — pass to list_channels / get_timeseries_stats
      "referenceId": 1001,              // numeric — pass to get_channel_data
      "referenceAppId": "019e50b2-..."
    }]
  }
}
```

## Worked Example — TR-004 Anomaly Analysis

```
1. list_collections(name="LUMEN")
   → [{appId: "019e30b0-...", name: "LUMEN Engine Test Campaign 2024"}]

2. list_data_objects(collectionAppId="019e30b0-...", name="TR-004")
   → [{appId: "019e31c1-...", name: "TR-004 — Hot-fire run"}]

3. get_data_object(collectionAppId="019e30b0-...", dataObjectAppId="019e31c1-...")
   → containers.timeseries = [{containerAppId: "...", containerId: 67890, referenceId: 1001}]
   → id = 54321 (DataObject numeric id), collection.id = 12345

4. list_channels(containerId=67890)
   → [{measurement: "vibration", device: "turbopump", field: "rms_g"}, ...]

5. compare_channels(collectionId=12345, dataObjectId=54321, referenceId=1001,
                    measurements=["vibration", "thrust", "mixture_ratio"], groupBy=1000)
   → {"channels": {"vibration": [...], "thrust": [...], "mixture_ratio": [...]}}

6. get_successor_chain(collectionAppId="019e30b0-...", dataObjectAppId="019e31c1-...")
   → [TR-004-Investigation, TR-005-Hold, TR-006-Retest]
```

## Configuration

| Env var | Default | Description |
|---|---|---|
| `SHEPARD_API_BASE` | `http://backend:8080` | Shepard backend URL (internal Docker network) |
| `PORT` | `8811` | Listening port |

## Proxy Setup (Zoraxy)

Add a virtual directory rule on the `shepard.nuclide.systems` virtual host:

| Field | Value |
|---|---|
| Virtual path | `/mcp` |
| Target upstream | `localhost:8811` |
| Strip path prefix | ✓ |
