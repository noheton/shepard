---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 30 — shepard-plugin-mcp: Full-Parity MCP Endpoint

**Status:** Design  
**Audience:** Contributors, plugin developers, AI tool integrators  
**Depends on:** L2d (appId-first v2 surface), TS-IDa (timeseries appId backfill), IMP1 (import validate)  
**Informed by:** `aidocs/platform/56-v2-api-simplification-output-profiles-mcp.md` (MCP-friendly OpenAPI annotations)  
**Blast radius:** Medium — new service, no changes to existing backend

---

## 1. The Problem

A live conversation (2026-05-21, stored in project memory) showed an AI assistant
trying to analyse the LUMEN TR-004 anomaly via a Shepard MCP server. Two critical
failures:

**Failure A — Missing timeseries tools.** The agent found TR-004 DataObject
(`timeseriesReferenceCount: 1`) but had no tool to access the sensor data. It
synthesized a fake report from the text attributes instead of reading real channels.

**Failure B — referenceIds confusion.** TR-004 has `referenceIds: [331, 335, 337, 1077]`.
These are `DataObjectReference` node IDs — the join-record between a DataObject and
its container. The agent called `get_data_object(data_object_id=331)` →
`GET /shepard/api/collections/42/dataObjects/331` → 404. The field name `referenceIds`
gives no hint it contains reference-record IDs, not DataObject IDs.

**Root cause:** The MCP server had navigation tools (collections, data objects, lab
journal) but zero payload-access tools. An analyst agent can find the data but cannot
read it.

**Principle established:** The MCP surface must have **full API parity** with the v2
surface. Every operation a human can perform via the frontend or REST client must be
available as an MCP tool. Plugins bring their own tools.

---

## 2. Design Principles

1. **API parity** — one MCP tool per meaningful v2 operation. Not a thin wrapper over
   one endpoint; a tool per logical action.
2. **Plugin SPI** — each `shepard-plugin-*` registers its own tools. The MCP server
   aggregates them at startup. A video plugin brings `list_video_containers`,
   `get_video_metadata`. A spatial plugin brings `get_spatial_region_data`.
3. **No `"null"` string params** — every optional parameter is truly optional; callers
   omit it, not pass the string `"null"`. (The existing `search_data_objects`
   `collection_id="null"` bug is the anti-pattern.)
4. **Self-describing** — tool descriptions carry semantic hints about what analysis
   each tool enables, what the data looks like, and what downstream tools to call next.
5. **appId-first** — all tools accept `appId` (UUID v7) as the primary key. Legacy
   numeric IDs accepted as fallback where they exist in v1, but v2 tools use appId.
6. **Timeseries via appId** — once TS-IDa ships, `get_channel_data` accepts
   `timeseriesAppId` (single param). Until then, accepts both 5-tuple and appId.

---

## 3. Tool Inventory (Full Parity)

### 3.1 Collections

| Tool | Parameters | Returns |
|---|---|---|
| `list_collections` | `page?`, `size?`, `name?` | list of {appId, name, description, dataObjectCount} |
| `get_collection` | `collectionAppId` | full collection + attribute map |
| `create_collection` | `name`, `description?`, `attributes?` | created collection |
| `update_collection` | `collectionAppId`, `name?`, `description?`, `attributes?` | updated |
| `delete_collection` | `collectionAppId` | 204 |

### 3.2 DataObjects

| Tool | Parameters | Returns |
|---|---|---|
| `list_data_objects` | `collectionAppId`, `page?`, `size?`, `name?` | list of {appId, name, status, containerSummary} |
| `get_data_object` | `dataObjectAppId` | full DataObject including **containerRefs** (broken out by kind — see §4) |
| `search_data_objects` | `query`, `collectionAppId?` | ranked list |
| `create_data_object` | `collectionAppId`, `name`, `description?`, `status?`, `attributes?` | created |
| `update_data_object` | `dataObjectAppId`, fields... | updated |
| `set_predecessor` | `dataObjectAppId`, `predecessorAppId` | relation created |
| `set_parent` | `dataObjectAppId`, `parentAppId` | relation created |
| `get_predecessor_chain` | `dataObjectAppId`, `depth?` | ordered list of DataObjects |
| `get_successor_chain` | `dataObjectAppId`, `depth?` | ordered list |
| `get_children` | `dataObjectAppId` | child DataObjects |

### 3.3 Timeseries (the critical missing layer)

| Tool | Parameters | Returns |
|---|---|---|
| `list_timeseries_containers` | `dataObjectAppId` | list of {containerAppId, name, channelCount, timeRange} |
| `list_channels` | `containerAppId` | list of {timeseriesAppId?, measurement, device, location, symbolicName, field, unit, rowCount, timeRange} |
| `get_channel_data` | `containerAppId`, `timeseriesAppId OR (measurement + field)`, `startTime?`, `endTime?`, `maxPoints?=1000` | [{timestamp, value}] with downsampling note |
| `get_channel_summary` | `containerAppId`, `timeseriesAppId OR (measurement + field)` | {min, max, mean, std, count, firstTimestamp, lastTimestamp} |
| `compare_channels` | `containerAppId`, `channels[]`, `startTime?`, `endTime?` | multi-channel aligned series for side-by-side analysis |

`maxPoints` defaults to 1000 and applies LTTB downsampling for large ranges.
`compare_channels` is the tool the LUMEN TR-004 analysis needed: pull vibration +
thrust + mixture ratio aligned in time to understand the anomaly causally.

### 3.4 File Containers

| Tool | Parameters | Returns |
|---|---|---|
| `list_file_containers` | `dataObjectAppId` | list of {containerAppId, name, fileCount, totalSize} |
| `list_files` | `containerAppId` | list of {fileId, name, size, mimeType, createdAt} |
| `get_file_text` | `containerAppId`, `fileId`, `maxChars?=50000` | text content (for .txt, .md, .csv, .json, .pdf-via-extract) |

`get_file_text` is the tool for reading lab notes PDFs, CSV summary tables, and
inspection reports — the "opaque file" problem today.

### 3.5 Structured Data Containers

| Tool | Parameters | Returns |
|---|---|---|
| `list_structured_containers` | `dataObjectAppId` | list of {containerAppId, name} |
| `get_structured_data` | `containerAppId` | the JSON payload |

### 3.6 Annotations

| Tool | Parameters | Returns |
|---|---|---|
| `list_annotations` | `dataObjectAppId` | [{key, value, ontologyClass?, ontologyIri?}] |
| `create_annotation` | `dataObjectAppId`, `key`, `value`, `ontologyClassIri?` | created |
| `delete_annotation` | `annotationAppId` | 204 |
| `search_ontology_classes` | `query`, `repositoryAppId?` | [{iri, label, description}] |

### 3.7 Lab Journal

| Tool | Parameters | Returns |
|---|---|---|
| `list_lab_journal` | `dataObjectAppId`, `page?`, `size?` | [{id, title, preview, createdAt}] |
| `get_lab_journal_entry` | `entryId` | {title, content (markdown), author, timestamps} |
| `create_lab_journal_entry` | `dataObjectAppId`, `title`, `content` | created |

### 3.8 Import

| Tool | Parameters | Returns |
|---|---|---|
| `get_import_context` | `collectionAppId`, `includeSemanticGraph?=false` | {fingerprint, dataObjectCount, availableOntologyTerms?} |
| `validate_import` | `manifest` (ImportManifestIO JSON) | {commitId, status, summary, warnings, errors} |
| `get_import_plan` | `commitId` | plan status + expiry |

### 3.9 Instance / Discovery

| Tool | Parameters | Returns |
|---|---|---|
| `describe_instance` | — | {version, features[], plugins[]} |
| `list_tools` | — | all available tools including plugin-contributed ones |

`list_tools` is the meta-tool: an AI agent's first call. It returns a description of
every available tool including plugin-contributed tools, enabling the agent to build
its own plan.

---

## 4. Fix for the `referenceIds` Confusion

`get_data_object` currently returns `referenceIds: [331, 335, 337]` — numeric IDs of
`DataObjectReference` nodes, not of DataObjects. No tool accepts these IDs. This
causes 404s when an agent passes them to `get_data_object`.

**Fix:** the MCP tool response breaks out containers by kind:

```json
{
  "appId": "019e30b0-9e96-...",
  "name": "TR-004",
  "attributes": { ... },
  "status": "PUBLISHED",
  "containers": {
    "timeseries": [
      { "containerAppId": "019e30b0-...", "name": "Hot-fire sensors", "channelCount": 12 }
    ],
    "files": [
      { "containerAppId": "019e30b0-...", "name": "Test report", "fileCount": 2 }
    ],
    "structuredData": [
      { "containerAppId": "019e30b0-...", "name": "Run parameters" }
    ]
  },
  "predecessors": [{ "appId": "...", "name": "TR-003" }],
  "successors": [{ "appId": "...", "name": "TR-005" }, { "appId": "...", "name": "Anomaly Investigation" }],
  "children": [{ "appId": "...", "name": "Anomaly Investigation — TR-004 Fuel Turbopump" }]
}
```

The raw `referenceIds` field is omitted from the MCP response entirely. The agent
can immediately call `list_channels(containerAppId)` or `get_file_text(containerAppId, fileId)`.

---

## 5. Plugin Tool SPI

Every `shepard-plugin-*` can contribute MCP tools. The interface:

```python
# plugins/<plugin-id>/mcp_tools.py
class McpToolProvider:
    def get_tool_definitions(self) -> list[ToolDefinition]:
        """Return tool schemas — name, description, parameter schema."""
        ...

    def execute_tool(self, tool_name: str, args: dict, auth_token: str) -> dict:
        """Execute a tool call. Receives the caller's auth token for Shepard API calls."""
        ...
```

The MCP server discovers providers by scanning for `mcp_tools.py` in registered
plugin directories (configured via `SHEPARD_PLUGIN_DIRS` env var or the admin config).

At startup, `list_tools` returns the union of core tools + all plugin-contributed tools.
Plugin tools are namespaced: `video__list_annotations`, `spatial__get_region_data`.

### Example: video plugin tools

```python
# shepard-plugin-video/mcp_tools.py
tools = [
    ToolDefinition(
        name="video__list_containers",
        description="List video stream containers attached to a DataObject. "
                    "Returns containerAppId and playback metadata.",
        parameters={"dataObjectAppId": {"type": "string", "required": True}}
    ),
    ToolDefinition(
        name="video__get_annotation_at_time",
        description="Get spatial region annotations at a specific timestamp "
                    "in a video container. Useful for correlating sensor anomalies "
                    "with visual events.",
        parameters={
            "containerAppId": {"type": "string", "required": True},
            "timestampMs": {"type": "integer", "required": True}
        }
    ),
]
```

---

## 6. Implementation Shape

**Stack:** Python + FastMCP (matches existing server; `fastmcp: wrap_result: true`
already present in production). Single process, stateless, auth via bearer token
forwarding to Shepard backend.

**Location:** `plugins/shepard-plugin-mcp/` — own module, own Docker image,
own compose profile (`--profile mcp`).

**Config:**
```yaml
# infrastructure/docker-compose.override.yml
shepard-mcp:
  image: shepard-plugin-mcp:local
  environment:
    SHEPARD_API_BASE: http://backend:8080
    SHEPARD_PLUGIN_DIRS: /plugins/video,/plugins/spatial
  ports:
    - "8811:8811"
  profiles: [mcp]
```

**Proxy rule (Zoraxy):** Add a virtual directory rule on the `shepard.nuclide.systems`
virtual host:

| Field | Value |
|---|---|
| Virtual path | `/mcp` |
| Target upstream | `localhost:8811` |
| Strip path prefix | ✓ (Zoraxy "virtual directory" mode) |

Public URL: `https://shepard.nuclide.systems/mcp`

FastMCP SSE endpoint (Claude remote connector): `https://shepard.nuclide.systems/mcp/sse`

The MCP server itself serves at path root `/`; Zoraxy strips the `/mcp` prefix before
forwarding. No subdomain needed — the plugin lives under the main Shepard URL.

**Auth:** The MCP server receives a bearer token from the caller (e.g. the AI client)
and forwards it on every Shepard API call. No separate MCP credentials. The tool
descriptions carry `x-mcp-side-effects: write` hints for tools that mutate state,
consistent with `aidocs/56`.

**Claude remote connector config (operator adds in claude.ai settings):**
```json
{
  "name": "Shepard",
  "url": "https://shepard.nuclide.systems/mcp/sse",
  "authorization_type": "oauth2",
  "oauth2": {
    "authorization_url": "https://auth.nuclide.systems/realms/shepard-demo/protocol/openid-connect/auth",
    "token_url": "https://auth.nuclide.systems/realms/shepard-demo/protocol/openid-connect/token",
    "client_id": "mcp-client",
    "scopes": ["openid", "profile", "email"]
  }
}
```
The `mcp-client` Keycloak client is public (PKCE) — no client secret required for
the browser-side OIDC flow. The resulting bearer token is the user's Shepard token,
forwarded transparently to all backend calls.

---

## 7. Recommended Build Sequence

```
MCP-1a  Core navigation tools (list_collections, list_data_objects, get_data_object
         with fixed container breakdown, get_predecessor_chain)                ← unblocks basic graph traversal

MCP-1b  Timeseries tools (list_timeseries_containers, list_channels,
         get_channel_data, get_channel_summary, compare_channels)              ← unblocks sensor analysis

MCP-1c  File + structured data tools (get_file_text, get_structured_data)     ← unblocks document analysis

MCP-1d  Annotation + lab journal tools                                         ← unblocks curation workflows

MCP-1e  Import tools (get_import_context, validate_import)                    ← unblocks agentic import

MCP-1f  Plugin SPI + describe_instance + list_tools                           ← unblocks plugin ecosystem

MCP-2   Plugin implementations (video tools, spatial tools, …)               ← per plugin
```

MCP-1a + MCP-1b together are the minimum viable server for the LUMEN TR-004 analysis
use case — a researcher asking "show me what happened during the turbopump anomaly"
gets real sensor data, not fabricated text.

---

## 8. Test Plan

- MCP-1a: `get_data_object(tr004AppId)` returns `containers.timeseries` list, not `referenceIds`
- MCP-1b: `get_channel_data(containerAppId, measurement="vibration", field="rms")` returns
  30-second timeseries showing spike at t=8s; `get_channel_summary` shows max ≈ 12g
- MCP-1c: `get_file_text(containerAppId, fileId)` returns readable text of the test report PDF
- MCP-1f: `list_tools` returns core tools + video plugin tools when video plugin is active
- Regression: `search_data_objects` with no `collectionAppId` does not crash with
  "unable to parse string as an integer" (the `"null"` string bug, fixed by making
  param truly optional)
