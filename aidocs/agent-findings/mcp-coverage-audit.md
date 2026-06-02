---
stage: audited-by-personas
last-stage-change: 2026-06-02
fire: 2026-06-02-02
---

# MCP Coverage Audit — MCP-COV-01-AUDIT

Inventory of every v2 REST endpoint against every MCP tool.
Scan base: `backend/src/main/java/de/dlr/shepard/v2/`.

---

## Section 1: REST → MCP Coverage Table

Key for **Parity** column:
- `full` — MCP tool covers the same semantics with equal or richer shape
- `partial` — MCP tool covers a subset (e.g. read-only, or single vs. bulk)
- `missing` — no MCP tool exists for this endpoint

### 1.1 Annotations (`/v2/annotations`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/annotations` (list, paginated) | `list_annotations` (ContentMcpTools) | partial | `list_annotations` takes a containerAppId (FileContainer/StructuredData); does not expose the global annotation index with `subjectAppId` filter |
| `GET /v2/annotations/find` | `find_annotated` (AnnotationMcpTools) | full | Dual-mode: by subject appId or by predicate+value |
| `GET /v2/annotations/{appId}` | `get_annotation` | full | |
| `GET /v2/annotations/{appId}/export/turtle` | — | missing | Turtle RDF export of a single annotation |
| `POST /v2/annotations` | `create_annotation` | full | Includes bulk variant `semantic_annotate_bulk` (max 100/call) |
| `PUT /v2/annotations/{appId}` | `update_annotation` | full | |
| `DELETE /v2/annotations/{appId}` | `delete_annotation` | full | |

### 1.2 Collections (`/v2/collections`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/collections` (list) | `list_collections` | full | Paginated with optional name filter |
| `GET /v2/collections/{appId}` | — | missing | Get single collection by appId; `list_collections` returns summaries only, not full collection detail |
| `POST /v2/collections` | — | missing | Create collection |
| `PATCH /v2/collections/{appId}` | — | missing | Update collection metadata |
| `DELETE /v2/collections/{appId}` | — | missing | Delete collection |
| `GET /v2/collections/{appId}/referenced-containers` | — | missing | List all containers referenced in a collection |
| `POST /v2/collections/{appId}/export-url` | — | missing | Generate export URL |
| `GET /v2/collections/{appId}/properties` | — | missing | Read collection properties |
| `PATCH /v2/collections/{appId}/properties` | — | missing | Update collection properties |
| `GET /v2/collections/{appId}/publication-state` | — | missing | |
| `PATCH /v2/collections/{appId}/publication-state` | — | missing | |
| `GET /v2/collections/{appId}/dmp-snippet` | — | missing | DMP snippet export |
| `POST /v2/collections/{appId}/export/regulatory-evidence` | `rep_export` (ShapesMcpTools) | partial | MCP tool takes collectionAppId + profile; async REST; MCP is synchronous wrapper |
| `GET /v2/collections/{appId}/export/regulatory-evidence/latest` | — | missing | Fetch latest REP export |

### 1.3 Collection Templates

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/collections/{appId}/templates/allowed` | — | missing | |
| `GET /v2/collections/{appId}/templates/used` | — | missing | |
| `PUT /v2/collections/{appId}/templates/allowed` | — | missing | |
| `POST /v2/collections/{appId}/templates/from/{templateAppId}` | — | missing | |

### 1.4 Collection Snapshots (`/v2/collections/{appId}/snapshots`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `POST /v2/collections/{appId}/snapshots` | — | missing | Create snapshot |
| `GET /v2/collections/{appId}/snapshots` | — | missing | List snapshots for a collection |
| `GET /v2/snapshots` | — | missing | List all snapshots globally |
| `GET /v2/snapshots/{appId}` | — | missing | Read single snapshot |
| `GET /v2/snapshots/{appId}/manifest` | — | missing | Snapshot manifest |
| `DELETE /v2/snapshots/{appId}` | — | missing | Delete snapshot |
| `GET /v2/snapshots/{a}/diff/{b}` | — | missing | Diff two snapshots |
| `GET /v2/collections/{collId}/snapshots/{snapshotId}/data-objects` | — | missing | List DataObjects at pinned snapshot |

### 1.5 Collection Lab Journal (`/v2/collections/{appId}/lab-journal-entries`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/collections/{appId}/lab-journal-entries` | `lab_journal_list` (LabJournalMcpTools) | full | |
| `GET /v2/lab-journal/{appId}/history` | — | missing | Edit history of a journal entry |
| `GET /v2/lab-journal/{appId}/render` | — | missing | Rendered HTML of a journal entry |
| `GET /v2/lab-journal/{dataObjectAppId}/notebooks` | — | missing | Jupyter notebooks linked to a DataObject |

MCP also provides `lab_journal_create`, `lab_journal_update`, `lab_journal_delete` with no direct REST counterpart at these paths — they call the service layer directly.

### 1.6 Collection Watches and Events

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/collections/{appId}/watches` | `watch_list` (WatchMcpTools) | full | |
| `GET /v2/collections/{appId}/watches/me` | — | missing | Caller's own watch on a specific collection |
| `POST /v2/collections/{appId}/watches` | `watch_add` | full | |
| `DELETE /v2/collections/{appId}/watches/me` | `watch_remove` | full | |
| `GET /v2/collections/{appId}/watched-containers` | — | missing | Per-container watch list (WATCH1 surface) |
| `POST /v2/collections/{appId}/watched-containers` | — | missing | |
| `DELETE /v2/collections/{appId}/watched-containers/{watchId}` | — | missing | |
| `GET /v2/collections/{appId}/events` | — | missing | Collection event stream |

### 1.7 Collection Quality / DQR

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/collections/{appId}/dqr` | — | missing | List DQR rules |
| `POST /v2/collections/{appId}/dqr` | — | missing | Create DQR rule |
| `DELETE /v2/collections/{appId}/dqr/{dqrId}` | — | missing | |
| `POST /v2/collections/{appId}/dqr/evaluate` | — | missing | Trigger DQR evaluation |
| `POST /v2/quality/independence-proof` | — | missing | Independence proof |

### 1.8 Data Objects (`/v2/collections/{cId}/data-objects`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/collections/{cId}/data-objects` | `list_data_objects` | full | Paginated; name filter |
| `GET /v2/collections/{cId}/data-objects/{appId}` | `get_data_object` | full | Rich response including containers, predecessor/successor summaries |
| `POST /v2/collections/{cId}/data-objects` | — | missing | Create DataObject |
| `PATCH /v2/collections/{cId}/data-objects/{appId}` | — | missing | Update DataObject metadata / status |
| `DELETE /v2/collections/{cId}/data-objects/{appId}` | — | missing | Delete DataObject |
| `GET /v2/collections/{cId}/data-objects/{appId}/predecessors` | — | missing | Direct predecessors (first-hop only) |
| `GET /v2/collections/{cId}/data-objects/{appId}/successors` | — | missing | Direct successors |
| `GET /v2/collections/{cId}/data-objects/{appId}/children` | — | missing | Child DataObjects |
| `GET /v2/collections/{cId}/data-objects/{appId}/predecessor-chain` | `get_predecessor_chain` (LineageMcpTools) | full | Transitive walk, depth default 10 |
| `GET /v2/collections/{cId}/data-objects/{appId}/successor-chain` | `get_successor_chain` | full | |
| `POST /v2/data-objects/batch` | — | missing | Batch create DataObjects |
| `GET /v2/data-objects/{appId}/rdf` | — | missing | RDF export of DataObject annotations |
| `POST /v2/collections/{cId}/data-objects/from-template/{templateId}` | — | missing | Instantiate DataObject from template |

### 1.9 Timeseries References (`/v2/timeseries-references`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `PATCH /v2/timeseries-references/{appId}` | — | missing | Edit timeseries reference metadata |
| `POST /v2/timeseries-references/{appId}/detect-anomalies` | — | missing | Anomaly detection on a TS reference |
| `GET /v2/timeseries-references/{appId}/annotations` (list) | `list_channel_annotations` (TimeseriesMcpTools) | partial | MCP operates on channelShepardId; REST operates on refAppId — different scopes |
| `POST /v2/timeseries-references/{appId}/annotations` | `create_channel_annotation` | partial | REST annotates the reference; MCP annotates a channel by shepardId |
| `GET /v2/timeseries-references/{appId}/annotations/{annotId}` | — | missing | Get single TS reference annotation |
| `PATCH /v2/timeseries-references/{appId}/annotations/{annotId}` | — | missing | Update TS reference annotation |
| `DELETE /v2/timeseries-references/{appId}/annotations/{annotId}` | — | missing | |

### 1.10 Timeseries Containers (`/v2/timeseries-containers`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/timeseries-containers/{cId}/channels` | `list_channels` (TimeseriesMcpTools) | full | |
| `GET /v2/timeseries-containers/{cId}/channels/spatial-roles` | — | missing | Spatial role assignments on channels |
| `GET /v2/timeseries-containers/{cId}/channels/{shepardId}/data` | `get_channel_data` | full | Deprecated in favour of `ts_query_multi` |
| `POST /v2/timeseries-containers/{cId}/channels/data/bulk` | `ts_query_multi` | full | Multi-channel efficient query; bulk REST exists, MCP is single-call equivalent |
| `POST /v2/timeseries-containers/{cId}/channels/{shepardId}/data/ingest` | — | missing | Write timeseries data to a channel |
| `GET /v2/timeseries-containers/{cId}/channels/live-window` | — | missing | Live sliding-window query |
| `GET /v2/timeseries-containers/{cId}/channels/{shepardId}/annotations` | `list_channel_annotations` | full | |
| `POST /v2/timeseries-containers/{cId}/channels/{shepardId}/annotations` | `create_channel_annotation` | full | |
| `DELETE /v2/timeseries-containers/{cId}/channels/{shepardId}/annotations/{annotId}` | — | missing | |
| `GET /v2/timeseries-containers/{cId}/stats` | — | missing | Container-level stats (row count, time range) |
| `GET /v2/timeseries-containers/{cId}/chart-view` | — | missing | Persisted chart view config |
| `PATCH /v2/timeseries-containers/{cId}/chart-view` | — | missing | Update chart view config |
| `GET /v2/timeseries-containers/{cId}/temporal-annotations` (list) | — | missing | Event-marker annotations on container |
| `POST /v2/timeseries-containers/{cId}/temporal-annotations` | — | missing | |
| `GET /v2/timeseries-containers/{cId}/temporal-annotations/{annotId}` | — | missing | |
| `PATCH /v2/timeseries-containers/{cId}/temporal-annotations/{annotId}` | — | missing | |
| `DELETE /v2/timeseries-containers/{cId}/temporal-annotations/{annotId}` | — | missing | |
| `GET /v2/timeseries-containers/{cId}/annotations` | — | missing | Legacy semantic annotations on container |
| `POST /v2/timeseries-containers/{cId}/annotations` | — | missing | |
| `DELETE /v2/timeseries-containers/{cId}/annotations/{annotId}` | — | missing | |
| `GET /v2/timeseries-containers/{cId}/linked-data-objects` | — | missing | DataObjects linked to this container |
| `DELETE /v2/timeseries-containers/{cId}` | — | missing | Delete container |
| `POST /v2/sql/timeseries` | — | missing | SQL-dialect timeseries query |

Also backed by `ts_describe` (describe container metadata + available channels + value types).

### 1.11 File References (`/v2/files`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `POST /v2/files` | `file_upload` (ContentMcpTools) | partial | MCP uses base64 payload, 10 MiB cap; REST takes multipart, no cap |
| `GET /v2/files/by-data-object/{appId}` | `list_files` | full | |
| `GET /v2/files/{appId}` | — | missing | Get FileReference metadata |
| `GET /v2/files/{appId}/content` | `file_content` | full | Returns base64; REST returns raw bytes |
| `PATCH /v2/files/{appId}` | — | missing | Edit FileReference metadata |
| `DELETE /v2/files/{appId}` | — | missing | Delete FileReference |
| `GET /v2/file-containers/{cId}/linked-data-objects` | — | missing | |
| `DELETE /v2/file-containers/{cId}` | — | missing | |
| `POST /v2/file-containers/{cId}/upload-url` | — | missing | Presigned S3 upload |
| `POST /v2/file-containers/{cId}/upload-url/commit` | — | missing | |
| `GET /v2/file-containers/{cId}/files/{oid}/download-url` | — | missing | Presigned S3 download |
| `GET /v2/file-containers/{cId}/annotations` | — | missing | |
| `POST /v2/file-containers/{cId}/annotations` | — | missing | |
| `DELETE /v2/file-containers/{cId}/annotations/{annotId}` | — | missing | |
| `GET /v2/file-containers/{cId}/payload/{oid}/thumbnail` | — | missing | |
| `GET /v2/file-containers/{cId}/files/{name}/versions` | — | missing | File version history |

### 1.12 File Bundle References (`/v2/bundles`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/bundles/{appId}` | — | missing | Get bundle metadata |
| `GET /v2/bundles/{appId}/groups` | — | missing | List groups in bundle |
| `POST /v2/bundles/{appId}/groups` | — | missing | |
| `GET /v2/bundles/{appId}/groups/{groupId}` | — | missing | |
| `PATCH /v2/bundles/{appId}/groups/{groupId}` | — | missing | |
| `DELETE /v2/bundles/{appId}/groups/{groupId}` | — | missing | |
| `POST /v2/bundles/{appId}/groups/{groupId}/files` | — | missing | Upload file to group |

### 1.13 URI References (`/v2/uri-references`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `PATCH /v2/uri-references/{appId}` | — | missing | Edit URI reference |

### 1.14 Structured Data Containers (`/v2/structured-data-containers`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/structured-data-containers/{cId}/linked-data-objects` | — | missing | |
| `DELETE /v2/structured-data-containers/{cId}` | — | missing | |
| `GET /v2/structured-data-containers/{cId}/annotations` | — | missing | |
| `POST /v2/structured-data-containers/{cId}/annotations` | — | missing | |
| `DELETE /v2/structured-data-containers/{cId}/annotations/{annotId}` | — | missing | |
| `GET /v2/structured-data-containers/{cId}/files/{name}/versions` | — | missing | Version history for structured data payload |

`list_structured_data` (ContentMcpTools) lists structured data records within a container — no direct REST counterpart; calls service layer directly.

### 1.15 Scene Graphs (`/v2/scene-graphs`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/scene-graphs` | `scene_list` (SceneGraphMcpTools) | full | |
| `GET /v2/scene-graphs/{appId}` | `scene_graph_get` | full | |
| `GET /v2/scene-graphs/{appId}/export.urdf` | `scene_graph_export_urdf` | full | |
| `GET /v2/scene-graphs/{appId}/export.usd` | — | missing | USD export (no MCP equivalent) |
| `POST /v2/scene-graphs` (create) | `scene_graph_create` | full | |
| `POST /v2/scene-graphs/{appId}/frames` | `scene_graph_add_frame` | full | |
| `PATCH /v2/scene-graphs/{appId}/frames/{frameId}` | `scene_graph_patch_frame` | full | |
| `DELETE /v2/scene-graphs/{appId}/frames/{frameId}` | `scene_graph_delete_frame` | full | |
| `POST /v2/scene-graphs/{appId}/joints` | `scene_graph_register_joint` | full | |
| `DELETE /v2/scene-graphs/{appId}/joints/{jointId}` | `scene_graph_delete_joint` | full | |
| `POST /v2/scene-graphs/from-urdf/{fileRefAppId}` | `scene_create_from_urdf` | full | |

### 1.16 Semantic / Vocabulary (`/v2/semantic`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/semantic/{repoId}/sparql` | — | missing | SPARQL GET (MCP-COV-07 target) |
| `POST /v2/semantic/{repoId}/sparql` | — | missing | SPARQL POST (MCP-COV-07 target) |
| `GET /v2/semantic/terms/search` | `search_predicates`, `search_values` (AnnotationMcpTools) | partial | REST is unified; MCP splits into predicates-only and values-only tools |
| `GET /v2/semantic/ontology/alignment` | — | missing | Ontology alignment report |
| `GET /v2/semantic/vocabularies` | `list_vocabularies` | full | |
| `GET /v2/semantic/vocabularies/{id}/predicates` | `search_predicates` | partial | REST takes vocabId path param; MCP uses optional vocabularyAppId filter |
| `GET /v2/semantic/vocabularies/used-by/{entityAppId}` | — | missing | Vocabularies used by an entity |
| `GET /v2/semantic/predicates/{predicateB64}/stats` | — | missing | Predicate usage statistics |

`suggest_annotations` (AnnotationMcpTools) has no REST counterpart — calls service layer directly. `similar_entities` likewise.

### 1.17 Shapes (`/v2/shapes`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/shapes/predicates` | — | missing | List available shape predicates |
| `POST /v2/shapes/render` | `shape_render` (ShapesMcpTools) | full | |
| `POST /v2/shapes/validate` | `shape_validate` | full | |

### 1.18 Templates (`/v2/templates`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/templates` | — | missing | List templates |
| `GET /v2/templates/{appId}` | — | missing | Get template |
| `POST /v2/templates` | — | missing | Create template |
| `PATCH /v2/templates/{appId}` | — | missing | Update template |
| `DELETE /v2/templates/{appId}` | — | missing | Retire template |
| `GET /v2/templates/tags` | — | missing | List template tags |
| `GET /v2/templates/export` | — | missing | Export templates |
| `POST /v2/templates/import` | — | missing | Import templates |

### 1.19 Provenance (`/v2/provenance`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/provenance/activities` (3 content-negotiation overloads: JSON, JSON-LD, PROV-N) | — | missing | Activity list; MCP-COV-11 remainder |
| `GET /v2/provenance/entity/{appId}` (3 overloads) | — | missing | Entity provenance |
| `GET /v2/provenance/count` (2 overloads) | — | missing | Activity count |

### 1.20 KRL (`/v2/krl`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `POST /v2/krl/interpret` | — | missing | KRL script interpret (MCP-COV-09 target) |

### 1.21 Import (`/v2/import`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `POST /v2/import/validate` | — | missing | Validate + seal import manifest |
| `GET /v2/import/plans/{commitId}` | — | missing | Read sealed import plan |
| `GET /v2/import/context` | — | missing | Context blob for LLM manifest generation |
| `POST /v2/import/jobs` | — | missing | Execute import job |
| `GET /v2/import/lock` | — | missing | Get current import lock |
| `POST /v2/import/lock` | — | missing | Acquire import lock |
| `POST /v2/import/lock/{lockId}/heartbeat` | — | missing | Heartbeat |
| `POST /v2/import/lock/{lockId}/release` | — | missing | Release lock |
| `POST /v2/import/lock/{lockId}/abandon` | — | missing | Abandon lock |
| `DELETE /v2/import/lock/{lockId}` | — | missing | Delete lock |
| `GET /v2/import/diagnostics/{runId}` | — | missing | Import run diagnostics |
| `GET /v2/import/runs` | — | missing | List import runs |
| `POST /v2/import/diagnostics/{runId}/events` | — | missing | Log import event |
| `POST /v2/import/diagnostics/{runId}/events/batch` | — | missing | Batch log events |

### 1.22 Publish (`/v2/{kind}/{appId}/publish`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `POST /v2/{kind}/{appId}/publish` | — | missing | Publish entity to external target |
| `DELETE /v2/{kind}/{appId}/publish` | — | missing | Unpublish |
| `GET /v2/{kind}/{appId}/publications` | — | missing | List publications for entity |

### 1.23 Versions

No explicit version REST resource found in v2 — versions are managed via upstream surface. `version_list` and `version_get` (VersionMcpTools) call `VersionService` directly.

### 1.24 Notifications (`/v2/notifications`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/notifications` | — | missing | List notifications for caller |
| `GET /v2/notifications/count` | — | missing | Unread notification count |
| `PATCH /v2/notifications/{appId}/read` | — | missing | Mark read |
| `DELETE /v2/notifications/{appId}` | — | missing | Dismiss notification |
| `POST /v2/admin/notifications/test` | — | missing | Send test notification |
| `GET /v2/admin/notifications/transports` | — | missing | List transports |
| `POST /v2/admin/notifications/transports` | — | missing | |
| `PATCH /v2/admin/notifications/transports/{appId}` | — | missing | |
| `DELETE /v2/admin/notifications/transports/{appId}` | — | missing | |

### 1.25 Users and Profile (`/v2/users/me`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/users/me` | — | missing | Caller's profile |
| `PATCH /v2/users/me` | — | missing | Update profile |
| `GET /v2/users/me/preferences` | — | missing | |
| `PATCH /v2/users/me/preferences` | — | missing | |
| `PUT /v2/users/me/avatar` | — | missing | |
| `DELETE /v2/users/me/avatar` | — | missing | |
| `GET /v2/users/{appId}/avatar` | — | missing | |
| `GET /v2/me/role-in/{collectionAppId}` | — | missing | Caller's role in a collection |

### 1.26 Vocabularies (`/v2/vocabularies/personal`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `POST /v2/vocabularies/personal` | — | missing | Create personal vocabulary entry |
| `GET /v2/vocabularies/personal` | — | missing | List personal vocabulary entries |

### 1.27 Instance (`/v2/instance`)

| REST Endpoint | MCP Tool | Parity | Notes |
|---|---|---|---|
| `GET /v2/instance/capabilities` | `ai_capabilities` (AiMcpTools) | partial | MCP surfaces AI capabilities only; REST returns full instance capability map |
| `GET /v2/instance/identity` | — | missing | Instance identity (appId, name, ROR) |

### 1.28 Admin Endpoints (`/v2/admin`)

All admin endpoints lack MCP coverage (by design — admin operations should remain out of the MCP attack surface for now):

- `GET/PATCH /v2/admin/features/{name}` — feature toggles
- `GET /v2/admin/metrics-summary`
- `GET /v2/admin/storage-overview`
- `POST /v2/admin/bootstrap`
- `GET/POST/DELETE /v2/admin/instance-admins` + `GET /v2/admin/permission-audit`
- `POST /v2/admin/instance/nuke`
- `GET/POST/DELETE/POST-ingest /v2/admin/semantic/git-sources/{appId}`
- `POST /v2/admin/semantic/refresh-ontologies`, `/refresh-snapshots`
- `GET/POST/DELETE /v2/admin/semantic/ontologies`
- `GET/PATCH /v2/admin/semantic/config`
- `GET/PATCH /v2/admin/jupyter/config`
- `GET/PATCH /v2/admin/plugins/jupyter/config`
- `GET /v2/jupyter/config` (public read)
- `POST/GET /v2/admin/ledger/anchor` + per-DataObject anchors
- `GET/PATCH /v2/admin/plugins`
- `GET/PATCH /v2/admin/instance/ror`
- `GET/PATCH /v2/admin/sql-timeseries/config`
- `POST/GET/POST-rollback /v2/admin/files/migrate`
- `GET /v2/admin/storage`
- `POST/GET/POST-rotate /v2/admin/users/{username}/git-credentials`
- `PATCH /v2/admin/users/{username}/orcid`
- `POST /v2/admin/users/mirror`
- `GET/PATCH /v2/admin/instances` (instance registry)

**Admin coverage note:** These ~35 endpoints are intentionally `missing` from MCP. The threat model for MCP clients (LLM agents) excludes admin mutations. Any future `admin_*` tool would require explicit instance-admin gating at the MCP layer.

---

## Section 2: MCP Tools Not Backed by REST

These MCP tools call service or DAO layers directly, with no direct REST counterpart:

| Tool Name | Class | Implementation | Notes |
|---|---|---|---|
| `suggest_annotations` | AnnotationMcpTools | Calls `SemanticAnnotationService.suggest()` | AI-assisted annotation suggestions; no REST equivalent |
| `similar_entities` | AnnotationMcpTools | Calls similarity scoring on annotation graph | Cross-entity similarity; no REST equivalent |
| `semantic_annotate_bulk` | AnnotationMcpTools | Server-side loop over `create_annotation` | REST equivalent `POST /v2/semantic-annotations/bulk` queued as `SEMANTIC-ANNOTATE-BULK-REST-1` |
| `lab_journal_create` | LabJournalMcpTools | Calls `LabJournalEntryService` | `POST /v2/lab-journal` not present as a standalone v2 endpoint; entry creation is via the collection sub-resource |
| `lab_journal_update` | LabJournalMcpTools | Service layer | Corresponding PATCH not found in v2 REST scan |
| `lab_journal_delete` | LabJournalMcpTools | Service layer | |
| `version_list` | VersionMcpTools | `VersionService.findByCollectionId()` | No dedicated version list in v2 REST |
| `version_get` | VersionMcpTools | DAO query by OGM uid | |
| `list_structured_data` | ContentMcpTools | `StructuredDataContainerService` | Lists structured data records; no v2 REST equivalent found |
| `ai_capabilities` | AiMcpTools | `AiRegistry` + enum introspection | Partial overlap with `GET /v2/instance/capabilities`; MCP surface is AI-specific |
| `ai_invoke` | AiMcpTools | `LlmProvider` SPI + local-noop fallback | No REST equivalent for agent-to-AI invocation |
| `search` | SearchMcpTools | Unified Cypher fan-out across entity kinds | `GET /v2/collections?name=` etc. are per-kind; MCP is cross-kind; no unified REST search endpoint |

---

## Section 3: Coverage Summary

| Metric | Count |
|---|---|
| Total REST endpoints scanned (all `*Rest.java` under `/v2/`) | **197** |
| Admin-only endpoints (intentionally out of MCP scope) | **~35** |
| Non-admin REST endpoints (MCP-eligible) | **~162** |
| Covered by MCP (full or partial) | **~37** |
| **Missing MCP coverage (non-admin)** | **~125** |
| MCP tools total (`@Tool`-annotated methods) | **52** |
| MCP tools with no REST counterpart (service-layer direct) | **12** |

**Coverage of non-admin REST surface: ~23%** (37 / 162).
**Coverage of total REST surface: ~19%** (37 / 197).

> Counting methodology: each distinct HTTP method + path combination is counted once. Content-negotiation overloads (same method + path, different `Accept:` header, e.g. the three provenance `GET /activities` overloads) are counted as one logical endpoint.

### Top 5 Highest-Value Gaps

1. **DataObject CRUD** — `POST`, `PATCH`, `DELETE` on `/v2/collections/{cId}/data-objects`. An agent can read DataObjects but cannot create, update status, or delete them. Blocks any agent-driven data-entry or status-workflow automation. Highest frequency, highest impact.

2. **Snapshot read** — `GET /v2/snapshots/{appId}`, list, manifest, diff, and pinned-read. An agent cannot read the history of a Collection, compare states, or answer "what did this DataObject look like at snapshot T?". The diff endpoint is especially valuable for audit trail automation. MCP-COV-06 row notes snapshots as a follow-up.

3. **Temporal annotations on timeseries containers** — `GET/POST/PATCH/DELETE /v2/timeseries-containers/{cId}/temporal-annotations`. Event-marker annotations (e.g. "anomaly window", "valve open/close"). An agent processing timeseries data for anomaly investigation needs to read and write these. Critical for the LUMEN TR-004 use case.

4. **SPARQL query** — `GET/POST /v2/semantic/{repoId}/sparql`. An agent able to issue SPARQL has full semantic queryability across the annotation graph. Blocked as MCP-COV-07-SEMANTIC-SPARQL (now unblocked by this audit).

5. **Import workflow** — `POST /v2/import/validate` + `GET /v2/import/context` + `POST /v2/import/jobs`. The context endpoint is explicitly designed for LLM manifest generation (`AgentContextIO`), yet the import pipeline has zero MCP coverage. An agent that can see a directory of files and generate then execute an import plan would dramatically reduce data-entry friction. MCP-COV-09 depends on this.

---

## Section 4: Recommended Next Rows

In priority order:

### MCP-COV-02-CORE-CRUD (already queued — refine scope)

**Gap:** DataObject create / update / delete. Also Collection create/update/delete.

**Implementation sketch:**
- `create_data_object(collectionAppId, name, description?, status?)` → calls `DataObjectV2Rest` service → returns `{appId, name}`.
- `update_data_object(dataObjectAppId, name?, description?, status?)` → `PATCH /v2/collections/{cId}/data-objects/{appId}` via service; collectionAppId resolved from DAO.
- `delete_data_object(dataObjectAppId)` → DELETE via service.
- `create_collection(name, description?)` / `delete_collection(appId)` analogously.
- Permission: write gate via `PermissionsService.checkWriteAccess()` (same pattern as `ContentMcpTools.fileUpload`).

**Value:** Unblocks any agent-driven workflow that writes research data, not just reads it.

---

### MCP-COV-06-SNAPSHOTS (identified in backlog as follow-up to MCP-COV-06)

**Gap:** Snapshot create, list, read, diff, pinned-read.

**Implementation sketch:**
- `snapshot_create(collectionAppId, label?)` → `POST /v2/collections/{cId}/snapshots` → returns `{snapshotAppId, label, createdAt}`.
- `snapshot_list(collectionAppId, page?, size?)` → `GET /v2/collections/{cId}/snapshots`.
- `snapshot_diff(aAppId, bAppId)` → `GET /v2/snapshots/{a}/diff/{b}` → returns diff summary; response size-capped.
- Read-only tools are low risk; create is write-gated.

**Value:** Lets an agent answer "what changed between TR-004 and TR-006?" and write automated checkpoints after import jobs complete.

---

### MCP-COV-07-SEMANTIC-SPARQL (already queued — now unblocked by this audit)

**Gap:** SPARQL GET and POST on `/v2/semantic/{repoId}/sparql`.

**Implementation sketch:**
- `sparql_query(repoAppId, query, format?)` → dispatches to `GET /v2/semantic/{repoId}/sparql?query=…` for SELECT/ASK. UPDATE blocked (read-only constraint enforced server-side).
- Response: raw SPARQL JSON results object, size-capped at 1000 rows.
- `list_vocabularies` already provides `repoAppId` values; caller composes the query.
- Optionally add `semantic_search(predicateIri, value, subjectKind?, page?)` wrapping a canned SPARQL template — lower friction for the "find all DataObjects annotated with X=Y" case.

**Value:** Full semantic queryability. An agent can answer arbitrary annotation-graph questions (e.g. "which process steps used consolidation pressure > 3 MPa?") without relying on fixed MCP tool shapes. Highest-leverage single tool for AI-assisted analysis.

---

*End of audit. Generated 2026-06-02 from `backend/src/main/java/de/dlr/shepard/v2/` REST scan (97 `*Rest.java` files) and `de.dlr.shepard.v2.mcp/` MCP scan (52 `@Tool`-annotated methods across 12 tool classes).*
