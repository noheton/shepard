---
layout: default
title: MCP tools reference
permalink: /reference/mcp-tools/
audience: advanced
---
# MCP tools reference

Shepard exposes a **Model Context Protocol (MCP) server** at `/v2/mcp/sse` — a
native Quarkus MCP endpoint that any MCP-compatible client (Claude Desktop,
Claude Code, LobeChat, LangChain MCP adapters, etc.) can connect to. Once
connected, the client receives a catalogue of ~50 tools covering collections,
data objects, semantic annotations, timeseries, files, lab journals, shapes,
lineage, scene graphs, and more.

**Design doc:** `aidocs/platform/30-mcp-plugin-design.md`  
**Feature ID:** MCP1d (core), MCP-V2 (v2 surface)

---

## Connection and authentication

**Endpoint:** `https://<your-shepard-host>/v2/mcp/sse`

Two credential forms are accepted — the server tries OIDC first, falls through
to API-key:

| Credential form | Header | When to use |
|---|---|---|
| OIDC JWT (interactive) | `Authorization: Bearer <oidc-jwt>` | Browser-based SSO flows |
| Shepard API key | `Authorization: Bearer <api-key-jws>` | Automated agents, desktop apps |

Obtain an API key: **Your profile → API keys → Generate**. Keys carry an
expiry; expired keys receive a `WWW-Authenticate: ApiKey error="expired"`
response.

### Claude Desktop `~/.claude.json` example

```json
{
  "mcpServers": {
    "shepard": {
      "type": "sse",
      "url": "https://shepard.example.org/v2/mcp/sse",
      "headers": {
        "Authorization": "Bearer <your-api-key>"
      }
    }
  }
}
```

---

## Tool catalogue

Tools are grouped by domain. Every tool returns a JSON text result.
`appId` values throughout are UUID v7 strings — stable, cross-substrate
identifiers. **Never pass numeric Neo4j node IDs** — those are internal
implementation details and change across migrations.

---

### Collections and data objects

| Tool | Description |
|------|-------------|
| `list_collections` | List all collections visible to the caller. Returns `appId`, `name`, `description`, `tags`, `createdAt`. Paginated (`pageSize`, `pageNumber`). |
| `list_data_objects` | List DataObjects in a Collection. Requires `collectionAppId`. Returns `appId`, `name`, `description`, `kind`, `status`, `createdAt`. Paginated. |
| `get_data_object` | Fetch full DataObject record including container references and tag set. Requires `dataObjectAppId`. |

**Example: explore a collection**
```
list_collections → pick appId of "LUMEN Hotfire Campaign"
list_data_objects(collectionAppId="019e...") → find "TR-004 Anomaly Run"
get_data_object(dataObjectAppId="01a2...") → see containers, tags, status
```

---

### Search

| Tool | Description |
|------|-------------|
| `search` | Full-text + semantic search across collections, data objects, containers, and references. Supports `kind` filter, `collectionAppId` scope, `pageSize`/`pageNumber`. Returns `appId`, `kind`, `name`, snippet. |

See `docs/reference/search.md` for the full JSON query DSL and `kind` taxonomy.

**Permission note (SEARCH-MCP-PERMS-1):** `search` returns items the caller
can Read on. Unauthenticated callers see only publicly-accessible items (where
the parent Collection's permissions include the public reader group).

---

### Semantic annotations

| Tool | Description |
|------|-------------|
| `list_vocabularies` | List all vocabulary repositories (ontologies) loaded on the instance. Returns `appId`, `name`, `iri`, `termCount`. Use `appId` values when filtering `search_predicates`. |
| `search_predicates` | Search vocabulary predicates by text. Optional `vocabularyAppId` narrows to one ontology. Returns `uri`, `label`, `definition`, `vocabularyName`. |
| `search_values` | Find controlled-vocabulary values for a predicate (for enum-like predicates). Requires `predicateIri`; optional `filter` text + `limit`. |
| `get_annotation` | Fetch a single annotation by `annotationAppId`. Returns predicate IRI, value, unit, confidence, `sourceMode`, provenance chain. |
| `find_annotated` | **Mode A** (by subject): supply `subjectAppId` → all annotations on that entity. **Mode B** (by predicate/value): supply `predicateIri` + optional `valueName`/`valueIRI` → entities across the instance that carry this annotation. |
| `create_annotation` | Annotate any entity. Required: `subjectAppId`, `subjectKind`, `propertyIRI`. Supply one of `valueName`, `valueIRI`, or `numericValue` (+ `unitIRI`). Optional: `sourceMode` (`human`\|`ai`\|`collaborative`), `confidence` (AI path). |
| `update_annotation` | Patch an annotation's value, unit, property label, vocabulary link, confidence, or validity window. Null fields retain existing values. |
| `delete_annotation` | Remove an annotation by `annotationAppId`. |

**Example: annotate a DataObject as an AI agent**
```
search_predicates(q="propellant") → pick uri "urn:mffd:material:propellant"
create_annotation(
  subjectAppId="01a2...", subjectKind="DataObject",
  propertyIRI="urn:mffd:material:propellant",
  valueName="LOX/LH2",
  sourceMode="ai", confidence=0.95
)
```

---

### Timeseries

| Tool | Description |
|------|-------------|
| `list_channels` | List timeseries channels in a container. Requires `containerAppId`. Returns `appId`, `measurement`, `device`, `location`, `symbolicName`, `field`, `unit`. |
| `get_channel_data` | Fetch raw samples for a channel. Requires `channelAppId` (UUID v7). Optional: `startMs`/`endMs` epoch range, `limitRows`. Returns `t` (epoch ms array) + `v` (value array). |
| `ts_describe` | Statistical summary of a channel over a time window: min, max, mean, stddev, count, first/last sample. Faster than loading raw data. |
| `ts_query_multi` | Fetch multiple channels in one call. Requires `channelAppIds` (array). Downsamples to `maxPointsPerChannel` (default 500). Returns one time vector + value array per channel. |
| `list_channel_annotations` | List semantic annotations on a timeseries channel. Requires `channelAppId`. Returns `appId` (UUID v7), predicate IRI, value. |
| `create_channel_annotation` | Annotate a channel. Requires `channelAppId`, `propertyVocabAppId` (from `list_vocabularies`), `valueVocabAppId` (from `list_vocabularies`). |

**Example: investigate TR-004 turbopump anomaly**
```
list_channels(containerAppId="01b3...") → find "turbopump/vibration/rms_g"
ts_describe(channelAppId="019f...", startMs=1718000000000, endMs=1718000060000)
  → max=12.4, mean=1.2, stddev=2.8  ← spike confirmed
get_channel_data(channelAppId="019f...", startMs=1718000008000, endMs=1718000012000)
  → raw 8–12s window around the spike
```

---

### Files and content

| Tool | Description |
|------|-------------|
| `list_files` | List FileReferences on a DataObject. Requires `dataObjectAppId`. Returns `appId`, `name`, `fileKind`, `fileSize`, `createdAt`. |
| `list_structured_data` | List StructuredDataReferences (JSON/tabular payloads). Requires `dataObjectAppId`. |
| `list_annotations` | List SemanticAnnotations on an entity. Requires `subjectAppId` and `subjectKind`. |
| `file_upload` | Upload a file to a DataObject as a singleton FileReference. Requires `dataObjectAppId`, `filename`, `base64Content`, `mimeType`. Returns new `appId`. |
| `file_content` | Fetch the bytes of a FileReference. Requires `fileReferenceAppId`. Returns `base64Content`, `filename`, `mimeType`. For files ≤ 1 MB. |

---

### Lab journal

| Tool | Description |
|------|-------------|
| `lab_journal_list` | List journal entries on a DataObject. Requires `dataObjectAppId`. Returns `appId`, `content` (markdown), `createdAt`, `authorDisplayName`. |
| `lab_journal_create` | Create a new journal entry. Requires `dataObjectAppId`, `content` (CommonMark markdown). |
| `lab_journal_update` | Edit an existing entry. Requires `entryAppId`, `content`. |
| `lab_journal_delete` | Delete an entry. Requires `entryAppId`. |

---

### Lineage

| Tool | Description |
|------|-------------|
| `get_predecessor_chain` | Walk the Predecessor graph backward from a DataObject. Requires `dataObjectAppId`. Optional `maxDepth` (default 10). Returns typed predecessor edges and their DataObject names/appIds. |
| `get_successor_chain` | Walk the Successor graph forward. Same parameters. |

---

### Shapes, validation, and export

| Tool | Description |
|------|-------------|
| `shape_render` | Render a DataObject's data through a view-recipe template (table, chart, URDF viewer, etc.). Requires `dataObjectAppId`, `templateAppId`. Returns rendered HTML or structured output. |
| `shape_validate` | Validate a DataObject against a SHACL shape template. Requires `dataObjectAppId`, `templateAppId`. Returns SHACL validation report: `conforms`, violations list with path + message. |
| `rep_export` | Build a Regulatory Evidence Pack (BagIt 1.0 bag + RO-Crate 1.1 metadata + PROV-O provenance graph) for a Collection. Requires `collectionAppId`. Returns `base64` (bag bytes) for bags ≤ 1 MB. |

---

### Mappings (MAPPING_RECIPE)

| Tool | Description |
|------|-------------|
| `mapping_list` | List MAPPING_RECIPE templates available on the instance. Returns `appId`, `name`, `description`, `inputKinds`, `outputKind`. |
| `mapping_materialize` | Run a mapping recipe: bind input reference appIds and invoke the registered transform executor. Returns output `referenceAppId` or `viewModelJson`. |

---

### Watching collections

| Tool | Description |
|------|-------------|
| `watch_list` | List Collections the caller currently watches. |
| `watch_add` | Subscribe to new-DataObject notifications on a Collection. Requires `collectionAppId`. |
| `watch_remove` | Unsubscribe from a Collection. Requires `collectionAppId`. |

---

### AI capabilities

| Tool | Description |
|------|-------------|
| `ai_capabilities` | List AI capability slots and their configured providers (e.g. `TEXT → LocalTeiEmbeddingProvider`, `FAST_TEXT → RemoteTeiEmbeddingProvider`). Shows which slots are active. |
| `ai_invoke` | Invoke an AI capability by slot name (`TEXT`, `FAST_TEXT`). Requires `capability`, `prompt`. Returns `result` text. Used for shepherd-side inference (embedding, summarisation) without the client managing the model. |

---

### Projects

| Tool | Description |
|------|-------------|
| `get_project` | Fetch project metadata by `projectAppId`. |
| `list_projects` | List all projects visible to the caller. |
| `get_project_sub_collections` | List collections that belong to a project. Requires `projectAppId`. |
| `query_project_by_annotation` | Find projects annotated with a given predicate/value pair. |

---

### Snapshots (versions)

| Tool | Description |
|------|-------------|
| `version_list` | List snapshots of a Collection. Requires `collectionAppId`. Returns `appId`, `name`, `createdAt`, `description`. |
| `version_get` | Fetch snapshot detail (entity list + revision numbers at snapshot time). Requires `snapshotAppId`. |

---

### Scene graph

Scene graph MCP tools (`scene_graph_get`, `scene_graph_create`,
`scene_graph_add_frame`, `scene_graph_patch_frame`, `scene_graph_delete_frame`,
`scene_graph_register_joint`, `scene_graph_delete_joint`,
`scene_graph_export_urdf`, `scene_graph_list`) live in
`SceneGraphMcpTools` (feature module `v2/scenegraph`). They follow the same
authentication model and are served from the same `/v2/mcp/sse` endpoint.

Permission model: scene-graph read/write follows the parent Collection's
permissions (via the scene's `sourceFileAppId → FileReference → DataObject →
Collection` walk). Hand-built scenes (no source file) are owner-only until
`ownerCollectionAppId` ships (SCENEGRAPH-PERMS-2).

---

## Worked end-to-end example: annotate and validate an MFFD DataObject

```
# 1. Find the DataObject
search(q="AFP layup ply 5 anomaly", kind="DataObject")
  → appId="01c4..."

# 2. Discover what predicates apply
search_predicates(q="consolidation force")
  → uri="urn:mffd:process:consolidationForce", label="Consolidation force"

# 3. Annotate with AI provenance
create_annotation(
  subjectAppId="01c4...", subjectKind="DataObject",
  propertyIRI="urn:mffd:process:consolidationForce",
  numericValue=18.3, unitIRI="http://qudt.org/vocab/unit/N",
  sourceMode="ai", confidence=0.87
)

# 4. Validate against the AFP template
list_collections → find "MFFD Process Chain" → list_data_objects → ...
shape_validate(dataObjectAppId="01c4...", templateAppId="01b9...")
  → conforms=false, violations=[{path: "mffd:ndtStatus", message: "Required"}]
```

---

## Limitations and known issues

| ID | Issue | Status |
|----|-------|--------|
| SEARCH-MCP-PERMS-1 | `search` results respect the caller's Read permissions, but unauthenticated / public-reader filtering uses the instance's "public reader group" convention — not a dedicated public-access flag. | Known; tracked in aidocs/16 |
| MCP-CHAN-ADDR | Timeseries channels are identified by UUID v7 `channelAppId` in the MCP surface. The REST surface still uses the 5-tuple `{measurement,device,location,symbolicName,field}` for historic callers (TS-IDb migration pending). | In-progress |
| MCP-SCENE-PERMS-2 | Scene graphs without a source file (hand-built) are accessible to any authenticated user; `ownerCollectionAppId` anchor pending. | Tracked SCENEGRAPH-PERMS-2 |
| FILE-SIZE | `file_content` returns base64 for files ≤ 1 MB. Larger files require the REST `GET /v2/references/{appId}/content` presigned-URL flow. | By design |
