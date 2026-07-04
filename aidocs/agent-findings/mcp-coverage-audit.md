---
name: MCP tool coverage audit — REST × MCP inventory table
description: Maps every v2 REST endpoint domain to its MCP tool counterpart (or absence). Drives MCP-COV-02, -07, and the structured-data body-retrieval gap.
type: audit
stage: feature-defined
last-stage-change: 2026-06-29
---

# MCP-COV-01-AUDIT — REST × MCP coverage inventory

**Generated**: fire-306, 2026-06-29  
**Source**: 13 MCP tool classes (`de.dlr.shepard.v2.mcp.*`), all v2 REST resources  
**Purpose**: drives MCP-COV-02 (core CRUD), MCP-COV-07 (SPARQL/browse), and any follow-on rows

## TL;DR

| Domain | MCP coverage | Status |
|--------|-------------|--------|
| Collections & DataObjects | `list_collections`, `list_data_objects`, `get_data_object` | ✅ full read |
| Lineage walk | `get_predecessor_chain`, `get_successor_chain` | ✅ |
| Semantic annotations | 12 tools (CRUD + bulk + stubs) | ✅ full (2 stubs pending SEMA-V6-008) |
| Timeseries data | `list_channels`, `ts_describe`, `ts_query_multi`, `get_channel_data` | ✅ |
| Timeseries annotations | `list_channel_annotations`, `create_channel_annotation` | ✅ |
| Files (singleton FR1b) | `file_upload`, `file_content`, `list_files` | ✅ |
| Lab journal | `lab_journal_{list,create,update,delete}` | ✅ |
| Projects | `get_project`, `list_projects`, `get_project_sub_collections`, `query_project_by_annotation` | ✅ |
| Watches (collection) | `watch_{list,add,remove}` | ✅ |
| Shapes & REP | `shape_render`, `shape_validate`, `rep_export` | ✅ |
| AI capabilities | `ai_capabilities`, `ai_invoke` | ✅ |
| Mappings / MAPPING_RECIPE | `mapping_list`, `mapping_materialize` | ✅ |
| Versions (Collection) | `version_list`, `version_get` | ✅ |
| Scene graph | `scene_graph_*`, `scene_list`, `scene_create_from_urdf` (MCP-COV-08) | ✅ |
| Search | `search` | ✅ |
| Structured data body | `list_structured_data` (metadata only; body deferred) | ⚠️ partial |
| Ontology browse / SPARQL | vocabularies/predicates via annotation tools only; no `sparql_query` | ⚠️ partial → MCP-COV-07 |
| Container CRUD | — | ❌ gap → MCP-COV-02 |
| Reference CRUD | — | ❌ gap → MCP-COV-02 |
| Snapshots | — | ❌ gap → MCP-COV-06-SNAPSHOTS |
| Publication / PID | — | ❌ not agent-facing yet |
| Templates | `mapping_materialize` (MAPPING_RECIPE only) | ❌ generic TPL deferred |
| Provenance graph | — | ❌ gap → MCP-COV-11 (partially: chain walkers done) |
| Admin (all) | — | ❌ intentional (operator-facing) |
| Import / Export jobs | — | ❌ async, not yet MCP-compatible |
| Notifications | — | ❌ user-UI-facing |
| Users / groups | — | ❌ not agent-facing |

---

## Part 1: MCP tool inventory (50 tools, 13 classes)

| Tool name | Class | Domain | REST counterpart |
|-----------|-------|--------|-----------------|
| `ai_capabilities` | AiMcpTools | AI | none (plugin SPI) |
| `ai_invoke` | AiMcpTools | AI | none (plugin SPI) |
| `list_vocabularies` | AnnotationMcpTools | Annotations | `GET /v2/semantic/vocabularies` |
| `search_predicates` | AnnotationMcpTools | Annotations | `GET /v2/semantic/terms/search` |
| `search_values` | AnnotationMcpTools | Annotations | `GET /v2/semantic/terms/search` (value facet) |
| `get_annotation` | AnnotationMcpTools | Annotations | `GET /v2/annotations/{appId}` |
| `create_annotation` | AnnotationMcpTools | Annotations | `POST /v2/annotations` |
| `update_annotation` | AnnotationMcpTools | Annotations | `PUT /v2/annotations` |
| `delete_annotation` | AnnotationMcpTools | Annotations | `DELETE /v2/annotations` |
| `suggest_annotations` | AnnotationMcpTools | Annotations | — (501 stub, SEMA-V6-008) |
| `find_annotated` | AnnotationMcpTools | Annotations | `GET /v2/annotations?subjectAppId=…` |
| `semantic_annotate_bulk` | AnnotationMcpTools | Annotations | — (MCP-only, REST backlog SEMANTIC-ANNOTATE-BULK-REST-1) |
| `find_similar_annotated` | AnnotationMcpTools | Annotations | — (501 stub, SEMA-V6-008) |
| `list_collections` | CollectionMcpTools | Collections | `GET /v2/collections` |
| `list_data_objects` | CollectionMcpTools | Collections | `GET /v2/collections/{id}/data-objects` |
| `get_data_object` | CollectionMcpTools | Collections | `GET /v2/collections/{id}/data-objects/{appId}` |
| `list_files` | ContentMcpTools | Files | `GET /v2/containers` (FileContainer) |
| `list_structured_data` | ContentMcpTools | Structured Data | `GET /v2/containers` (StructuredDataContainer) — metadata only |
| `list_annotations` | ContentMcpTools | Annotations | `GET /v2/annotations?subjectAppId=…` |
| `file_upload` | ContentMcpTools | Files | `POST /v2/references?kind=file` + `PUT /v2/references/{appId}/content` |
| `file_content` | ContentMcpTools | Files | `GET /v2/files/{appId}/content` (or kind=file) |
| `lab_journal_list` | LabJournalMcpTools | Lab Journal | `GET /v2/lab-journal` |
| `lab_journal_create` | LabJournalMcpTools | Lab Journal | `POST /v2/lab-journal` (via service) |
| `lab_journal_update` | LabJournalMcpTools | Lab Journal | `PATCH /v2/lab-journal` |
| `lab_journal_delete` | LabJournalMcpTools | Lab Journal | `DELETE /v2/lab-journal` |
| `get_predecessor_chain` | LineageMcpTools | Lineage | `GET /v2/collections/{id}/data-objects/{appId}/predecessor-chain` |
| `get_successor_chain` | LineageMcpTools | Lineage | `GET /v2/collections/{id}/data-objects/{appId}/successor-chain` |
| `mapping_list` | MappingsMcpTools | Templates | `GET /v2/templates?kind=MAPPING_RECIPE` |
| `mapping_materialize` | MappingsMcpTools | Templates | `POST /v2/mappings` |
| `get_project` | ProjectMcpTools | Projects | `GET /v2/projects/{appId}` |
| `list_projects` | ProjectMcpTools | Projects | `GET /v2/projects` |
| `get_project_sub_collections` | ProjectMcpTools | Projects | `GET /v2/projects/{appId}/sub-collections` |
| `query_project_by_annotation` | ProjectMcpTools | Projects | `GET /v2/projects?predicate=…&value=…` |
| `search` | SearchMcpTools | Search | `GET /v2/search` |
| `shape_render` | ShapesMcpTools | Shapes | `POST /v2/shapes/render` |
| `shape_validate` | ShapesMcpTools | Shapes | `POST /v2/shapes/validate` |
| `rep_export` | ShapesMcpTools | REP | `POST /v2/collections/{id}/rep-export` |
| `list_channels` | TimeseriesMcpTools | Timeseries | `GET /v2/containers/{appId}/channels` (5-tuple list) |
| `get_channel_data` | TimeseriesMcpTools | Timeseries | `GET /v2/containers/{appId}/channels/{ch}/data` (deprecated; use `ts_query_multi`) |
| `ts_describe` | TimeseriesMcpTools | Timeseries | `GET /v2/containers/{appId}/channels` (with shepardId) |
| `ts_query_multi` | TimeseriesMcpTools | Timeseries | `POST /v2/sql/timeseries` or bulk data endpoint |
| `list_channel_annotations` | TimeseriesMcpTools | Timeseries | `GET /v2/references/{appId}/annotations` |
| `create_channel_annotation` | TimeseriesMcpTools | Timeseries | `POST /v2/references/{appId}/annotations` |
| `version_list` | VersionMcpTools | Versions | `GET /v2/collections/{id}/snapshots` (Collection versions) |
| `version_get` | VersionMcpTools | Versions | `GET /v2/snapshots/{snapshotAppId}` |
| `watch_list` | WatchMcpTools | Watches | `GET /v2/collections/{id}/watches` |
| `watch_add` | WatchMcpTools | Watches | `POST /v2/collections/{id}/watches` |
| `watch_remove` | WatchMcpTools | Watches | `DELETE /v2/collections/{id}/watches` |
| `scene_graph_*` (5 tools) | SceneGraphMcpTools | Scene Graph | `GET/POST/PUT /v2/collections/{id}/scene-graph` |
| `scene_list`, `scene_create_from_urdf` | SceneGraphMcpTools | Scene Graph | `GET /v2/scene-graphs`, `POST /v2/scene-graphs/from-urdf/{appId}` |

---

## Part 2: v2 REST endpoint domains × MCP coverage

### Collections & DataObjects

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/collections` | GET (list) | `list_collections` | ✅ full |
| `/v2/collections` | POST | — | ❌ gap (MCP-COV-02) |
| `/v2/collections/{appId}` | PATCH | — | ❌ gap (MCP-COV-02) |
| `/v2/collections/{appId}` | DELETE | — | ❌ gap (MCP-COV-02) |
| `/v2/collections/{id}/data-objects` | GET (list) | `list_data_objects` | ✅ full |
| `/v2/collections/{id}/data-objects/{appId}` | GET | `get_data_object` | ✅ full |
| `/v2/collections/{id}/data-objects` | POST | — | ❌ gap (MCP-COV-02) |
| `/v2/collections/{id}/data-objects/{appId}` | PATCH | — | ❌ gap (MCP-COV-02) |
| `/v2/collections/{id}/data-objects/{appId}` | DELETE | — | ❌ gap (MCP-COV-02) |
| `/v2/collections/{id}/data-objects/{appId}/predecessor-chain` | GET | `get_predecessor_chain` | ✅ |
| `/v2/collections/{id}/data-objects/{appId}/successor-chain` | GET | `get_successor_chain` | ✅ |
| `/v2/collections/{id}/data-objects/from-template` | POST | — | ❌ (template instantiation) |
| `/v2/collections/{id}/properties` | GET/PATCH | — | ❌ low-priority |
| `/v2/collections/{id}/templates` | GET/POST/PUT | `mapping_list` (MAPPING_RECIPE only) | ⚠️ partial |
| `/v2/collections/{id}/scene-graph` | GET/PUT/DELETE | `scene_graph_get/create` | ✅ |
| `/v2/collections/{id}/timeline` | GET | — | ❌ not agent-facing |
| `/v2/collections/{id}/dqr` | GET/POST/DELETE | — | ❌ operator-initiated |
| `/v2/collections/{id}/events` | GET | — | ❌ not agent-facing |
| `/v2/collections/{id}/publication-state` | GET/PATCH | — | ❌ not yet (PID workflow) |
| `/v2/collections/{id}/snapshots` | GET/POST | `version_list` (read only) | ⚠️ partial — no create |
| `/v2/collections/{id}/watches` | GET/POST/DELETE | `watch_*` | ✅ |
| `/v2/collections/{id}/watched-containers` | GET/POST/DELETE | — | ❌ per-container watches not exposed |
| `/v2/collections/{id}/lab-journal-entries` | GET | `lab_journal_list` | ✅ (collection-scoped) |

### Containers (Timeseries / File / StructuredData)

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/containers` | GET (list) | — (individual list tools: `list_channels`, `list_files`, `list_structured_data`) | ⚠️ partial |
| `/v2/containers` | POST (create) | — | ❌ gap (MCP-COV-02) |
| `/v2/containers/{appId}` | PATCH / DELETE | — | ❌ gap (MCP-COV-02) |
| `/v2/containers/{appId}/channels` | GET | `list_channels`, `ts_describe` | ✅ |
| `/v2/containers/{appId}/channels/{ch}/data` | GET | `get_channel_data` (deprecated), `ts_query_multi` | ✅ |
| `/v2/sql/timeseries` | POST | `ts_query_multi` | ✅ |
| `/v2/containers/{appId}/publication-state` | GET/PATCH | — | ❌ |
| `/v2/data-objects/cross-timeseries-bulk` | POST | — | ❌ admin-batch |
| `/v2/data-objects/batch` | POST | — | ❌ admin-batch |

### References (Files / Bundles / URI / Timeseries / StructuredData)

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/references?kind=…` | GET (list) | `list_files` (FileRef), `list_channels` (TSRef) | ⚠️ partial |
| `/v2/references?kind=…` | POST | `file_upload` (kind=file) | ⚠️ partial (file only) |
| `/v2/references/{appId}` | GET | — | ❌ gap (MCP-COV-02) |
| `/v2/references/{appId}` | PATCH | — | ❌ gap (MCP-COV-02) |
| `/v2/references/{appId}` | DELETE | — | ❌ gap (MCP-COV-02) |
| `/v2/references/{appId}/content` | GET | `file_content` | ✅ (file only, ≤10 MiB) |
| `/v2/references/{appId}/content` | PUT | `file_upload` (two-step) | ✅ |
| `/v2/references/{appId}/annotations` | GET | `list_channel_annotations`, `list_annotations` | ✅ |
| `/v2/references/{appId}/annotations` | POST/PATCH/DELETE | `create_channel_annotation` | ⚠️ partial (TS channels only) |
| `/v2/references/{appId}/detect-anomalies` | POST | — | ❌ AI-flagged, not yet agent-facing |
| `/v2/bundles` | all verbs | — | ❌ legacy path (tombstoned 410) |
| `/v2/files/{appId}` | all verbs | — | ❌ legacy path (tombstoned 410) |

### Semantic Annotations & Vocabularies

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/annotations` | GET/POST/PUT/DELETE | `{get,create,update,delete}_annotation`, `find_annotated` | ✅ full |
| `/v2/annotations` (bulk) | — | `semantic_annotate_bulk` | ✅ (MCP-only; REST SEMANTIC-ANNOTATE-BULK-REST-1 queued) |
| `/v2/semantic/vocabularies` | GET | `list_vocabularies` | ✅ |
| `/v2/semantic/terms/search` | GET | `search_predicates`, `search_values` | ✅ |
| `/v2/semantic/predicates` | GET | `search_predicates` (partial) | ⚠️ stats not exposed |
| `/v2/semantic/ontology/alignment` | GET | — | ❌ gap → MCP-COV-07 |
| `/v2/semantic` (SPARQL) | GET/POST | — | ❌ gap → MCP-COV-07 |
| `/v2/vocabularies/personal` | GET/POST | — | ❌ low priority |
| `/v2/admin/semantic/*` | all | — | ❌ admin |

### Provenance

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/provenance/activities` | GET | — | ❌ gap → MCP-COV-11 |
| `/v2/provenance/entity/{appId}` | GET | — | ❌ gap → MCP-COV-11 |
| `/v2/provenance/*` (JSON-LD, PROV-N) | GET | — | ❌ gap → MCP-COV-11 |
| predecessor/successor chain | GET | `get_predecessor_chain`, `get_successor_chain` | ✅ partial (chain walk done) |

### Search & Discovery

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/search` | GET | `search` | ✅ |
| `/v2/projects` | GET | `list_projects`, `get_project`, `query_project_by_annotation` | ✅ |

### Shapes / Templates / Snapshots

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/shapes/render` | POST | `shape_render` | ✅ |
| `/v2/shapes/validate` | POST | `shape_validate` | ✅ |
| `/v2/shapes/applicable` | GET | — | ❌ low priority |
| `/v2/shapes/predicates` | GET | — | ❌ low priority |
| `/v2/templates` | GET/POST/PATCH/DELETE | `mapping_list` (MAPPING_RECIPE only) | ⚠️ partial |
| `/v2/templates/{id}/form` | GET | — | ❌ gap |
| `/v2/templates/{id}/export` | GET | — | ❌ gap |
| `/v2/snapshots` | GET (list) | — | ❌ gap → MCP-COV-06-SNAPSHOTS |
| `/v2/snapshots/{appId}` | GET | `version_get` | ⚠️ Version ≠ Snapshot (different entities) |
| `/v2/snapshots/{aAppId}/diff/{bAppId}` | GET | — | ❌ gap → MCP-COV-06-SNAPSHOTS |
| `/v2/collections/{id}/snapshots` | POST | — | ❌ gap → MCP-COV-06-SNAPSHOTS |
| `/v2/collections/{id}/snapshots/{id}/data-objects` | GET | — | ❌ gap |

### Lab Journal / Notebooks

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/lab-journal` | GET/POST/PATCH/DELETE | `lab_journal_*` | ✅ |
| `/v2/lab-journal/{appId}/notebooks` | GET | — | ❌ Jupyter notebooks not agent-facing |

### Mappings

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/mappings` | POST | `mapping_materialize` | ✅ |

### Publications / PID

| REST path | Verb | MCP tool | Parity |
|-----------|------|----------|--------|
| `/v2/{kind}/{appId}/publish` | POST/DELETE | — | ❌ not yet agent-facing |
| `/v2/publications` | GET | — | ❌ not yet agent-facing |
| `/v2/{kind}/{appId}/publications` | GET | — | ❌ not yet agent-facing |

### Admin (intentionally no MCP)

All `/v2/admin/*` endpoints: bootstrap, config, features, metrics, storage, plugins, users, semantic/ontologies, notifications, ledger, instances, file migration → **intentionally no MCP coverage** (operator-facing, not agent-facing).

### Import / Export (no MCP — async, not yet MCP-compatible)

`/v2/import/*` (jobs, lock, diagnostics, context) → **no MCP**; asynchronous long-running jobs are not a pattern MCP handles cleanly today.

### Users / Identity / Notifications (no MCP — user-UI-facing)

`/v2/users/*`, `/v2/user-groups/*`, `/v2/me/*`, `/v2/notifications/*` → **no MCP** for current scope.

---

## Part 3: Coverage gap priority table (drives new MCP-COV-* rows)

| Gap | Affected REST paths | Driving use case | Blocking backlog row | Priority |
|-----|---------------------|-----------------|---------------------|----------|
| Container + Reference CRUD | `POST/PATCH/DELETE /v2/containers`, `POST/PATCH/DELETE /v2/references?kind=…` | Agent can read but not create data | MCP-COV-02 | **HIGH** |
| SPARQL query + ontology browse | `GET/POST /v2/semantic`, `GET /v2/semantic/ontology/alignment` | Semantic reasoning in agent loop | MCP-COV-07 | **HIGH** |
| Snapshot CRUD + diff | `GET/POST /v2/snapshots`, `GET /v2/snapshots/{a}/diff/{b}` | Agent-authored version checkpoints | MCP-COV-06-SNAPSHOTS | **MEDIUM** |
| Provenance query | `GET /v2/provenance/*` | "Why was this data created?" audit trail | MCP-COV-11 | **MEDIUM** |
| Structured data body | `GET /v2/containers/{appId}/structured-data/{oid}/body` | Agents reading JSON payloads | Phase 2 of MCP-COV-04 | **MEDIUM** |
| Template instantiation | `POST /v2/collections/{id}/data-objects/from-template` | Agent-driven data creation from schema | TEMPLATE-MCP-1 (future) | LOW |
| Publication / PID workflow | `POST /v2/{kind}/{appId}/publish` | Agent-triggered DOI minting | PID-MCP-1 (future) | LOW |
| Per-container watches | `POST/DELETE /v2/collections/{id}/watched-containers` | Fine-grained container subscriptions | low, user-facing | LOW |

---

## Part 4: Shape parity notes

- **Bulk vs. single**: `semantic_annotate_bulk` has no REST counterpart yet (SEMANTIC-ANNOTATE-BULK-REST-1 queued). `ts_query_multi` covers the timeseries multi-channel gap efficiently.
- **Missing 5-tuple exposure**: `list_channels` still returns the 5-tuple; `ts_describe` adds the `shepardId` field. TS-ID migration will eventually replace the 5-tuple entirely — MCP tools should not embed the 5-tuple more deeply.
- **MCP tool stubs** (`suggest_annotations`, `find_similar_annotated`): return 501 with a clear message; do not surface partial AI results. Unblocked by SEMA-V6-008.
- **`file_content` / `file_upload` cap at 10 MiB**: larger transfers require the REST multipart path; tool descriptions say so explicitly. This is correct and intentional per MCP transport limits.
- **`version_get` vs snapshots**: `VersionMcpTools` operates on `VersionService` (Collection-level version graph), not `SnapshotService` (per-collection save-points). They are separate entities. MCP-COV-06-SNAPSHOTS is the gap.
