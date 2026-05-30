---
stage: fragment
last-stage-change: 2026-05-30
---

# MCP-COV-01-AUDIT: REST × MCP Endpoint Inventory

Generated: 2026-05-30
Scope: all `/v2/` REST endpoints (`backend/src/main/java/de/dlr/shepard/v2/`) vs all MCP tools (`de.dlr.shepard.v2.mcp.*`).
Drives: every `MCP-COV-*` sub-row in `aidocs/16-dispatcher-backlog.md`.

---

## MCP Tool Inventory (29 tools, 6 classes)

| Tool name | Class | Kind |
|-----------|-------|------|
| `list_collections` | CollectionMcpTools | read |
| `list_data_objects` | CollectionMcpTools | read |
| `get_data_object` | CollectionMcpTools | read |
| `list_channels` | TimeseriesMcpTools | read (legacy 5-tuple) |
| `get_channel_data` | TimeseriesMcpTools | read (legacy 5-tuple, deprecated multi-channel) |
| `ts_describe` | TimeseriesMcpTools | read (shepardId) |
| `ts_query_multi` | TimeseriesMcpTools | read (shepardId, bulk) |
| `list_channel_annotations` | TimeseriesMcpTools | read |
| `create_channel_annotation` | TimeseriesMcpTools | write |
| `list_files` | ContentMcpTools | read |
| `list_structured_data` | ContentMcpTools | read (index only) |
| `list_annotations` | ContentMcpTools | read |
| `file_upload` | ContentMcpTools | write |
| `file_content` | ContentMcpTools | read |
| `list_vocabularies` | AnnotationMcpTools | read |
| `search_predicates` | AnnotationMcpTools | read |
| `search_values` | AnnotationMcpTools | read |
| `get_annotation` | AnnotationMcpTools | read |
| `create_annotation` | AnnotationMcpTools | write |
| `update_annotation` | AnnotationMcpTools | write |
| `delete_annotation` | AnnotationMcpTools | write |
| `suggest_annotations` | AnnotationMcpTools | stub (501, gated on SEMA-V6-008) |
| `find_annotated` | AnnotationMcpTools | read |
| `semantic_annotate_bulk` | AnnotationMcpTools | write (bulk, MCP-COV-05) |
| `find_similar_annotated` | AnnotationMcpTools | stub (501, gated on SEMA-V6-008) |
| `get_predecessor_chain` | LineageMcpTools | read (MCP-COV-11) |
| `get_successor_chain` | LineageMcpTools | read (MCP-COV-11) |
| `scene_graph_get` | SceneGraphMcpTools | read |
| `scene_graph_add_frame` | SceneGraphMcpTools | write |
| `scene_graph_patch_frame` | SceneGraphMcpTools | write |
| `scene_graph_delete_frame` | SceneGraphMcpTools | write |
| `scene_graph_register_joint` | SceneGraphMcpTools | write |
| `scene_graph_delete_joint` | SceneGraphMcpTools | write |
| `scene_graph_export_urdf` | SceneGraphMcpTools | read |
| `scene_graph_create` | SceneGraphMcpTools | write |

---

## REST x MCP Endpoint Table

Shape parity legend:
- **single** — MCP tool exists, matching single-item shape
- **bulk** — MCP tool exists, matching list/bulk shape
- **partial** — MCP tool exists but covers only part of the REST surface
- **missing** — no MCP tool covers this endpoint
- **n/a** — admin/infra endpoint, MCP coverage not expected

### Collections

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/collections` | GET | `list_collections` | bulk | REST supports richer filters (status); MCP caps at 200/page |
| `/v2/collections` | POST | -- | missing | No create-collection tool |
| `/v2/collections/{collectionAppId}` | GET | -- | missing | No single-collection detail tool (get_data_object gives summaries only) |
| `/v2/collections/{collectionAppId}` | PATCH | -- | missing | No update-collection tool |
| `/v2/collections/{collectionAppId}` | DELETE | -- | missing | No delete-collection tool |
| `/v2/collections/{appId}/properties` | GET | -- | missing | Collection key-value properties not in MCP |
| `/v2/collections/{appId}/properties` | PATCH | -- | missing | |
| `/v2/collections/{appId}/export-url` | POST | -- | missing | Export URL generation not in MCP |
| `/v2/collections/{appId}/dmp-snippet` | GET | -- | missing | DMP snippet not in MCP |
| `/v2/collections/{appId}/export/regulatory-evidence` | POST | -- | missing | Reg-evidence export not in MCP |
| `/v2/collections/{appId}/export/regulatory-evidence/latest` | GET | -- | missing | |
| `/v2/collections/{collectionAppId}/referenced-containers` | GET | -- | missing | Container enumeration not in MCP |
| `/v2/collections/{collectionAppId}/events` | GET | -- | missing | SSE event stream not in MCP |
| `/v2/collections/{collectionAppId}/snapshots` | POST | -- | missing | Snapshot create not in MCP |
| `/v2/collections/{collectionAppId}/snapshots` | GET | -- | missing | Snapshot list not in MCP |
| `/v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects` | GET | -- | missing | Pinned-read not in MCP |
| `/v2/collections/{collectionAppId}/watches` | GET | -- | missing | Watch list not in MCP |
| `/v2/collections/{collectionAppId}/watches/me` | GET | -- | missing | |
| `/v2/collections/{collectionAppId}/watches/me` | POST | -- | missing | |
| `/v2/collections/{collectionAppId}/watches/{watchAppId}` | DELETE | -- | missing | |
| `/v2/collections/{collectionAppId}/watched-containers` | GET | -- | missing | |
| `/v2/collections/{collectionAppId}/watched-containers` | POST | -- | missing | |
| `/v2/collections/{collectionAppId}/watched-containers/{watchAppId}` | DELETE | -- | missing | |
| `/v2/collections/{collectionAppId}/lab-journal-entries` | GET | -- | missing | Lab journal list not in MCP |
| `/v2/collections/{appId}/templates` | GET | -- | missing | Template surface not in MCP |
| `/v2/collections/{appId}/templates/allowed` | GET | -- | missing | |
| `/v2/collections/{appId}/templates/used` | GET | -- | missing | |
| `/v2/collections/{appId}/templates/allowed` | PUT | -- | missing | |
| `/v2/collections/{appId}/templates/from/{templateAppId}` | POST | -- | missing | |
| `/v2/collections/{collectionAppId}/dqr` | GET | -- | missing | DQR not in MCP |
| `/v2/collections/{collectionAppId}/dqr/{dqrAppId}` | POST | -- | missing | |
| `/v2/collections/{collectionAppId}/dqr/{dqrAppId}` | DELETE | -- | missing | |
| `/v2/collections/{collectionAppId}/dqr/evaluate` | POST | -- | missing | |
| `/v2/collections/{collectionAppId}/data-objects/from-template/{templateAppId}` | POST | -- | missing | Template instantiation not in MCP |

### DataObjects

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/collections/{collectionAppId}/data-objects` | GET | `list_data_objects` | bulk | REST supports richer filter params; MCP caps at 200 with name filter only |
| `/v2/collections/{collectionAppId}/data-objects` | POST | -- | missing | No create-dataobject tool |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}` | GET | `get_data_object` | single | Good parity; MCP forces depth-2 OGM load to populate container appIds |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}` | PATCH | -- | missing | No update-dataobject tool |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}` | DELETE | -- | missing | No delete-dataobject tool |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/predecessors` | GET | `get_data_object` (predecessorSummaries) | partial | First-level only; use `get_predecessor_chain` for transitive walk |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/successors` | GET | `get_data_object` (successorSummaries) | partial | Same limitation |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/children` | GET | `get_data_object` (childSummaries) | partial | First-level only |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/predecessor-chain` | GET | `get_predecessor_chain` | single | Good parity; MCP-COV-11 |
| `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/successor-chain` | GET | `get_successor_chain` | single | Good parity; MCP-COV-11 |
| `/v2/data-objects/batch` | POST | -- | missing | Batch DataObject create not in MCP |
| `/v2/data-objects/{appId}/rdf` | GET | -- | missing | RDF export not in MCP |

### Timeseries Containers and Channels

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/timeseries-containers/{containerId}/channels` | GET | `list_channels` / `ts_describe` | bulk | `list_channels` returns 5-tuple; `ts_describe` returns shepardId-addressed list -- prefer `ts_describe` |
| `/v2/timeseries-containers/{containerId}/channels/spatial-roles` | GET | -- | missing | Spatial role metadata not in MCP |
| `/v2/timeseries-containers/{containerId}/channels/{shepardId}/data` | GET | `get_channel_data` (via 5-tuple) | partial | `get_channel_data` accepts 5-tuple; REST uses numeric containerId, MCP uses appId |
| `/v2/timeseries-containers/{containerId}/channels/data/bulk` | POST | `ts_query_multi` | bulk | Good parity; `ts_query_multi` wraps the TS-OPT2 bulk endpoint |
| `/v2/timeseries-containers/{containerId}/channels/{shepardId}/data/ingest` | POST | -- | missing | Write-path (ingest) not in MCP |
| `/v2/timeseries-containers/{containerId}/linked-data-objects` | GET | -- | missing | Reverse-link lookup not in MCP |
| `/v2/timeseries-containers/{containerId}` | DELETE | -- | missing | Container delete not in MCP |
| `/v2/timeseries-containers/{containerId}/annotations` | GET | -- | missing | Container-level annotations not in MCP (distinct from channel annotations) |
| `/v2/timeseries-containers/{containerId}/annotations` | POST | -- | missing | |
| `/v2/timeseries-containers/{containerId}/annotations/{annotationId}` | DELETE | -- | missing | |
| `/v2/timeseries-containers/{containerId}/temporal-annotations` | GET | -- | missing | Temporal annotations not in MCP |
| `/v2/timeseries-containers/{containerId}/temporal-annotations` | POST | -- | missing | |
| `/v2/timeseries-containers/{containerId}/temporal-annotations/{annotationAppId}` | GET | -- | missing | |
| `/v2/timeseries-containers/{containerId}/temporal-annotations/{annotationAppId}` | PATCH | -- | missing | |
| `/v2/timeseries-containers/{containerId}/temporal-annotations/{annotationAppId}` | DELETE | -- | missing | |
| `/v2/timeseries-containers/{containerId}/chart-view` | GET | -- | missing | Chart-view config not in MCP |
| `/v2/timeseries-containers/{containerId}/chart-view` | PATCH | -- | missing | |
| `/v2/timeseries-containers/{containerId}/stats` | GET | -- | missing | Stats endpoint not in MCP |
| `/v2/timeseries-containers/{containerAppId}/channels/live-window` | GET | -- | missing | Live-window not in MCP |
| `/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations` | GET | `list_channel_annotations` | single | Good parity |
| `/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations` | POST | `create_channel_annotation` | single | Good parity |
| `/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations/{annotationId}` | DELETE | -- | missing | Delete channel annotation not in MCP |

### Timeseries References

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/timeseries-references/{appId}` | PATCH | -- | missing | Edit timeseries reference metadata not in MCP |
| `/v2/timeseries-references/{refAppId}/annotations` | GET | -- | missing | Reference-level annotations not in MCP |
| `/v2/timeseries-references/{refAppId}/annotations` | POST | -- | missing | |
| `/v2/timeseries-references/{refAppId}/annotations/{annotationAppId}` | GET | -- | missing | |
| `/v2/timeseries-references/{refAppId}/annotations/{annotationAppId}` | PATCH | -- | missing | |
| `/v2/timeseries-references/{refAppId}/annotations/{annotationAppId}` | DELETE | -- | missing | |
| `/v2/timeseries-references/{refAppId}/detect-anomalies` | POST | -- | missing | Anomaly detection not in MCP; natural AI workflow surface |
| `/v2/sql/timeseries` | POST | -- | missing | SQL timeseries bulk-write not in MCP |

### File Containers

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/file-containers/{containerAppId}/upload-url` | POST | -- | missing | Presigned upload URL not in MCP (use `file_upload` for <= 10 MiB) |
| `/v2/file-containers/{containerAppId}/upload-url/commit` | POST | -- | missing | |
| `/v2/file-containers/{containerAppId}/files/{oid}/download-url` | GET | -- | missing | Presigned download URL not in MCP (use `file_content` for <= 10 MiB) |
| `/v2/file-containers/{containerId}/linked-data-objects` | GET | -- | missing | Reverse-link lookup not in MCP |
| `/v2/file-containers/{containerId}` | DELETE | -- | missing | Container delete not in MCP |
| `/v2/file-containers/{containerId}/annotations` | GET | -- | missing | Container-level annotations not in MCP |
| `/v2/file-containers/{containerId}/annotations` | POST | -- | missing | |
| `/v2/file-containers/{containerId}/annotations/{annotationId}` | DELETE | -- | missing | |
| `/v2/file-containers/{containerAppId}/payload/{oid}/thumbnail` | GET | -- | missing | Thumbnail not in MCP |
| `/v2/file-containers/{containerAppId}/files/{originalName}/versions` | GET | -- | missing | Version history not in MCP |

### File References (Singleton FR1b)

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/files` | POST | `file_upload` | single | MCP capped at 10 MiB; REST supports arbitrary-size multipart |
| `/v2/files/by-data-object/{dataObjectAppId}` | GET | -- | missing | List singletons by parent not in MCP; workaround: `get_data_object -> fileReferenceAppIds[]` |
| `/v2/files/{appId}` | GET | -- | missing | Reference metadata not in MCP (only bytes via `file_content`) |
| `/v2/files/{appId}/content` | GET | `file_content` | single | MCP capped at 10 MiB; REST supports Range requests |
| `/v2/files/{appId}` | PATCH | -- | missing | Edit file reference metadata not in MCP |
| `/v2/files/{appId}` | DELETE | -- | missing | Delete file reference not in MCP |

### File Bundle References

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/bundles` | GET | -- | missing | Bundle list not in MCP (note: `list_files` targets FileContainer, different shape) |
| `/v2/bundles/{bundleAppId}` | GET | -- | missing | Bundle detail not in MCP |
| `/v2/bundles/{bundleAppId}/groups` | GET | -- | missing | Bundle groups not in MCP |
| `/v2/bundles/{bundleAppId}/groups` | POST | -- | missing | |
| `/v2/bundles/{bundleAppId}/groups/{groupAppId}` | GET | -- | missing | |
| `/v2/bundles/{bundleAppId}/groups/{groupAppId}` | PATCH | -- | missing | |
| `/v2/bundles/{bundleAppId}/groups/{groupAppId}` | DELETE | -- | missing | |
| `/v2/bundles/{bundleAppId}/groups/{groupAppId}/files` | POST | -- | missing | |

### Structured Data Containers

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/structured-data-containers/{containerId}/linked-data-objects` | GET | -- | missing | Reverse-link not in MCP |
| `/v2/structured-data-containers/{containerId}` | DELETE | -- | missing | Container delete not in MCP |
| `/v2/structured-data-containers/{containerId}/annotations` | GET | -- | missing | Container annotations not in MCP |
| `/v2/structured-data-containers/{containerId}/annotations` | POST | -- | missing | |
| `/v2/structured-data-containers/{containerId}/annotations/{annotationId}` | DELETE | -- | missing | |
| `/v2/structured-data-containers/{containerAppId}/files/{originalName}/versions` | GET | -- | missing | Version history not in MCP |

Note: `list_structured_data` lists index entries inside a container only; no body-retrieval tool exists (Phase 2 roadmap item noted in the tool description).

### Semantic Annotations (v2 REST surface)

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/annotations` | GET | `find_annotated` | partial | `find_annotated` covers Mode B (predicate+value search) with pagination |
| `/v2/annotations/find` | GET | `find_annotated` | single | Good parity for subject-scoped lookup (Mode A) |
| `/v2/annotations/{appId}` | GET | `get_annotation` | single | Good parity |
| `/v2/annotations/{appId}/export/turtle` | GET | -- | missing | Turtle export not in MCP |
| `/v2/annotations/{appId}` | POST | `create_annotation` | single | Good parity; also `semantic_annotate_bulk` for batch |
| `/v2/annotations/{appId}` | PUT | `update_annotation` | single | Good parity |
| `/v2/annotations/{appId}` | DELETE | `delete_annotation` | single | Good parity |

### Semantic Vocabularies and SPARQL

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/semantic/vocabularies` | GET | `list_vocabularies` | bulk | Good parity |
| `/v2/semantic/vocabularies/{vocabId}/predicates` | GET | `search_predicates` | partial | MCP adds free-text search; REST is paginated enumeration |
| `/v2/semantic/vocabularies/used-by/{entityAppId}` | GET | -- | missing | Vocab-by-entity lookup not in MCP |
| `/v2/semantic/terms/search` | GET | `search_predicates` / `search_values` | partial | MCP covers predicate + value search separately; REST is unified |
| `/v2/semantic/{repoAppId}/sparql` | GET | -- | missing | SPARQL query not in MCP; significant gap for AI graph queries |
| `/v2/semantic/{repoAppId}/sparql` | POST | -- | missing | Same -- POST form |
| `/v2/semantic/ontology/alignment` | GET | -- | missing | Ontology alignment not in MCP |

### Scene Graph

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/scene-graphs` | GET | -- | missing | List all scenes not in MCP |
| `/v2/scene-graphs/{appId}` | GET | `scene_graph_get` | single | Good parity |
| `/v2/scene-graphs/{appId}/export.urdf` | GET | `scene_graph_export_urdf` | single | Good parity |
| `/v2/scene-graphs/{appId}/export.usd` | GET | -- | missing | USD export not in MCP |
| `/v2/scene-graphs/{appId}/frames` | POST | `scene_graph_add_frame` | single | Good parity |
| `/v2/scene-graphs/{appId}/frames/{frameAppId}` | PATCH | `scene_graph_patch_frame` | single | Good parity |
| `/v2/scene-graphs/{appId}/frames/{frameAppId}` | DELETE | `scene_graph_delete_frame` | single | Good parity |
| `/v2/scene-graphs/{appId}/joints` | POST | `scene_graph_register_joint` | single | Good parity |
| `/v2/scene-graphs/{appId}/joints/{jointAppId}` | DELETE | `scene_graph_delete_joint` | single | Good parity |
| `/v2/scene-graphs/from-urdf/{fileReferenceAppId}` | POST | -- | missing | URDF import not in MCP; agents must use REST to parse |
| `/v2/krl/interpret` | POST | -- | missing | KRL interpret not in MCP |

Note: `scene_graph_create` fills a gap -- REST creates scenes via POST to `/v2/scene-graphs` (list resource); no direct match needed.

### Templates

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/templates` | GET | -- | missing | Template list not in MCP |
| `/v2/templates/{appId}` | GET | -- | missing | Template detail not in MCP |
| `/v2/templates` | POST | -- | missing | Template create not in MCP |
| `/v2/templates/{appId}` | PATCH | -- | missing | Template edit not in MCP |
| `/v2/templates/{appId}` | DELETE | -- | missing | Template delete not in MCP |
| `/v2/templates/tags` | GET | -- | missing | |
| `/v2/templates/export` | GET | -- | missing | |
| `/v2/templates/export` | POST | -- | missing | Template import not in MCP |

### Snapshots

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/snapshots/{snapshotAppId}` | GET | -- | missing | Snapshot detail not in MCP |
| `/v2/snapshots/{snapshotAppId}/manifest` | GET | -- | missing | Manifest not in MCP |
| `/v2/snapshots/{snapshotAppId}` | DELETE | -- | missing | |
| `/v2/snapshots/{aAppId}/diff/{bAppId}` | GET | -- | missing | Snapshot diff not in MCP; notable gap for AI change analysis |

### Provenance

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/provenance/activities` | GET | -- | missing | Activity list not in MCP; significant gap for AI audit trail queries |
| `/v2/provenance/entity/{appId}` | GET | -- | missing | Entity provenance not in MCP |
| `/v2/provenance/count` | GET | -- | missing | |
| `/v2/provenance/stats` | GET | -- | missing | |

Note: GET /v2/provenance/activities has multiple variants (with different query params for filtering by entity, by user, by date).

### Import

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/import/validate` | POST | -- | missing | Import plan generation not in MCP |
| `/v2/import/plans/{commitId}` | GET | -- | missing | Plan retrieval not in MCP |
| `/v2/import/context` | GET | -- | missing | Agent context endpoint explicitly designed for LLM use but has no MCP tool |
| `/v2/import/jobs` | POST | -- | missing | Import job commit not in MCP |
| `/v2/import/lock` | GET | -- | missing | Lock list not in MCP |
| `/v2/import/lock/{lockId}/heartbeat` | POST | -- | missing | |
| `/v2/import/lock/{lockId}/release` | POST | -- | missing | |
| `/v2/import/lock/{lockId}/abandon` | POST | -- | missing | |
| `/v2/import/lock/{lockId}` | DELETE | -- | missing | |
| `/v2/import/diagnostics/{runId}` | GET | -- | missing | Import diagnostics not in MCP |
| `/v2/import/runs` | GET | -- | missing | |
| `/v2/import/diagnostics/{runId}/events` | POST | -- | missing | |
| `/v2/import/diagnostics/{runId}/events/batch` | POST | -- | missing | |

### Quality / DQR / Independence Proof

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/quality/independence-proof` | POST | -- | missing | Independence proof not in MCP |

### Publish and Publications

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/{kind}/{appId}/publish` | POST | -- | missing | Publication workflows not in MCP |
| `/v2/{kind}/{appId}/publish` | DELETE | -- | missing | |
| `/v2/{kind}/{appId}/publications` | GET | -- | missing | |

### Shapes (SHACL)

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/shapes/predicates` | GET | -- | missing | Shape predicates not in MCP |
| `/v2/shapes/render` | POST | -- | missing | Shape render not in MCP |
| `/v2/shapes/validate` | POST | -- | missing | SHACL validation not in MCP |

### Lab Journal

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/lab-journal/{entryAppId}/history` | GET | -- | missing | Lab journal history not in MCP |
| `/v2/lab-journal/{appId}/render` | GET | -- | missing | Lab journal render not in MCP |
| `/v2/lab-journal/{dataObjectAppId}/notebooks` | GET | -- | missing | Notebook list not in MCP |

### URI References

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/uri-references/{appId}` | PATCH | -- | missing | URI reference edit not in MCP |

### Notifications

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/notifications` | GET | -- | missing | Notification inbox not in MCP |
| `/v2/notifications/count` | GET | -- | missing | |
| `/v2/notifications/{appId}/read` | PATCH | -- | missing | |
| `/v2/notifications/{appId}` | DELETE | -- | missing | |

### User and Me

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/users/me` | GET | -- | missing | Me endpoint not in MCP |
| `/v2/users/me` | PATCH | -- | missing | |
| `/v2/users/me/preferences` | GET | -- | missing | |
| `/v2/users/me/preferences` | PATCH | -- | missing | |
| `/v2/users/me/avatar` | PUT | -- | missing | |
| `/v2/users/me/avatar` | DELETE | -- | missing | |
| `/v2/users/{appId}/avatar` | GET | -- | missing | |
| `/v2/me/role-in/{collectionAppId}` | GET | -- | missing | Role-check not in MCP |

### Vocabularies (Personal)

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/vocabularies/personal` | POST | -- | missing | Personal vocabulary management not in MCP |
| `/v2/vocabularies/personal` | GET | -- | missing | |

### Instance Identity and Capabilities

| REST Method + Path | Verb | MCP Tool | Shape Parity | Efficiency Note |
|---|---|---|---|---|
| `/v2/instance/identity` | GET | -- | missing | Instance identity not in MCP |
| `/v2/instance/capabilities` | GET | -- | missing | Instance capabilities not in MCP |
| `/v2/jupyter/config` | GET | -- | n/a | JupyterHub config public endpoint |

### Admin Endpoints (n/a for MCP)

All `/v2/admin/` endpoints are operator-facing and are not expected as MCP tools. They are enumerated in the codebase under `de.dlr.shepard.v2.admin.*` and include: feature toggles, metrics, storage admin, instance admin, bootstrap, semantic admin/config, plugin admin, ledger, notifications, SQL timeseries config, and user management.

---

## Summary: Coverage Assessment by Area

### Well-covered areas

| Area | Coverage | Notes |
|------|----------|-------|
| DataObject navigation | High | `list_collections` -> `list_data_objects` -> `get_data_object` chain works well |
| Lineage traversal | High | Deep chains via `get_predecessor_chain` / `get_successor_chain` (MCP-COV-11) |
| Timeseries read | High | Dual path: legacy 5-tuple and new shepardId path with bulk endpoint |
| Semantic annotations | High | Full CRUD + bulk write + vocabulary/predicate search |
| Scene graph | High | Full frame + joint CRUD, URDF export, create |
| Singleton file I/O | Medium | Core happy path covered; capped at 10 MiB; metadata/delete missing |

### Significant gaps

| Area | Gap | Priority |
|------|-----|----------|
| Collection/DataObject writes | No create/update/delete for Collections or DataObjects | High -- breaks agent-only dataset seeding workflows |
| SPARQL | No `sparql_query` tool | High -- natural language for the semantic substrate; N MCP calls cannot substitute one SPARQL query |
| Provenance | No activity list/entity-provenance tools | High -- agents cannot reason about audit trail via MCP |
| Import pipeline | Entire import lifecycle absent | High -- `GET /v2/import/context` is explicitly designed for LLM manifest generation but has no MCP counterpart |
| Snapshots | No create/read/diff | Medium -- AI-driven change analysis requires snapshot diff |
| Templates | No template tools | Medium -- agents cannot read or instantiate templates |
| SHACL shapes | No validate/render tools | Medium -- agents cannot validate DataObjects or render view-recipes |
| Structured data body | `list_structured_data` is index-only | Medium -- agents must fall back to legacy REST for JSON body |
| Temporal annotations | No temporal annotation CRUD | Medium -- time-bounded annotations not accessible via MCP |
| Container annotations | No container-level annotation tools | Low -- per-container annotations distinct from per-channel |
| Anomaly detection | No `detect_anomalies` tool | Medium -- natural AI workflow surface |
| Lab journal | No lab journal tools | Low |

### Quantitative summary

| Category | Total REST Endpoints | With MCP Coverage | Coverage % |
|----------|---------------------|-------------------|------------|
| Collections (CRUD + sub-resources) | 34 | 3 | 9% |
| DataObjects (CRUD + lineage) | 12 | 5 | 42% |
| Timeseries (channels + containers) | 22 | 7 | 32% |
| File containers + references | 16 | 3 | 19% |
| Semantic annotations | 7 | 6 | 86% |
| Semantic vocabularies / SPARQL | 7 | 3 | 43% |
| Scene graph | 11 | 8 | 73% |
| Import pipeline | 13 | 0 | 0% |
| Templates | 8 | 0 | 0% |
| Snapshots | 5 | 0 | 0% |
| Provenance | 9 | 0 | 0% |
| Quality / DQR / Shapes | 8 | 0 | 0% |
| Notifications / User / Me | 11 | 0 | 0% |
| Lab journal | 3 | 0 | 0% |
| Publish / Publications | 3 | 0 | 0% |
| Admin (n/a) | ~30 | n/a | n/a |
| **Total (excl. admin)** | **~169** | **~35** | **~21%** |

---

## MCP Sub-Rows Driven by This Audit

| Row ID | Feature | Gap identified |
|--------|---------|----------------|
| MCP-COV-02 | DataObject create/update/delete | Collections + DataObjects write section |
| MCP-COV-03 | `ts_describe` + `ts_query_multi` | Already shipped; covers timeseries shepardId gap |
| MCP-COV-04 | `file_upload` + `file_content` | Already shipped; covers singleton FR1b gap |
| MCP-COV-05 | `semantic_annotate_bulk` | Already shipped; covers bulk annotation gap |
| MCP-COV-06 | SPARQL tool | `/v2/semantic/{repoAppId}/sparql` gap |
| MCP-COV-07 | Semantic SPARQL + further annotation surface | `/v2/semantic/sparql` gap + annotation export |
| MCP-COV-08 | Provenance tools | `/v2/provenance/*` gap |
| MCP-COV-09 | Import pipeline tools | `/v2/import/*` gap (especially `/context` for AI) |
| MCP-COV-10 | Snapshot diff tool | `/v2/snapshots/{a}/diff/{b}` gap |
| MCP-COV-11 | `get_predecessor_chain` / `get_successor_chain` | Already shipped; covers transitive lineage gap |
| MCP-COV-12 | Template tools | `/v2/templates/*` gap |
| MCP-COV-13 | SHACL validate/render tools | `/v2/shapes/*` gap |
| MCP-COV-14 | Temporal annotation tools | `/v2/timeseries-containers/{id}/temporal-annotations` gap |
| MCP-COV-15 | Structured data body retrieval | `list_structured_data` Phase 2 body-fetch |
| MCP-COV-16 | Anomaly detection tool | `/v2/timeseries-references/{id}/detect-anomalies` gap |
