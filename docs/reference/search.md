---
title: Search — reference
audience: user
---

# Search

Shepard exposes three complementary search surfaces: a **header quick-search** for instant
entity lookup, an **advanced search page** for structured query-builder queries against
any scope, and an **MCP `search` tool** for agent-driven exploration. All three are
permission-gated to the caller's read-accessible Collections.

---

## 1. Header quick-search

The global search bar in the top navigation bar fans out across **Collections**,
**DataObjects**, and **Containers** in parallel (300 ms debounce) and renders
incremental results as each batch arrives. Results link directly to the entity's
detail page. Keyboard shortcut: activate with `Ctrl+K` / `Cmd+K` (platform-dependent).

---

## 2. Advanced search page (`/search`)

The advanced search page exposes the full query-builder surface. Select a **query
type** (Collection / DataObject / StructuredData / Reference / Timeseries / File
container), optionally scope to a specific Collection or DataObject, and author a
JSON query expression against entity properties.

### JSON query language

Queries are a tree of `AND`, `OR`, `NOT` nodes wrapping leaf predicates of the form:

```json
{ "property": "<field>", "operator": "<op>", "value": <scalar> }
```

Supported operators: `eq`, `ne`, `gt`, `lt`, `gte`, `lte`, `contains`.

Example — DataObjects whose name contains "TR" OR whose id is less than 12:

```json
{
  "OR": [
    { "property": "name",  "operator": "contains", "value": "TR" },
    { "NOT": { "property": "id", "operator": "gt", "value": 12 } }
  ]
}
```

---

## 3. REST endpoints

The search endpoints live on the **v1 compat surface** (`/shepard/api/search`). They
are frozen for upstream byte-compatibility; new search capabilities are added via the
MCP `search` tool (§4) and future `/v2/` additions.

All endpoints require authentication (`Authorization: Bearer <token>`).

### 3a. General search — `POST /shepard/api/search`

Searches across any combination of entity types within caller-scoped traversal paths.

**Request body** (`SearchBody`):

| field | type | description |
|-------|------|-------------|
| `scopes` | `SearchScope[]` | required — defines the graph traversal roots |
| `searchParams.query` | JSON query | the query expression tree |

`SearchScope` fields:

| field | type | description |
|-------|------|-------------|
| `collectionId` | `long` \| null | scope to a specific Collection (numeric OGM id) |
| `dataObjectId` | `long` \| null | scope to a specific DataObject (numeric OGM id) |
| `traversalRules` | `TraversalRules[]` | which entity types to traverse |

**Example — search DataObjects in collection 42 whose name contains "LOX":**

```http
POST /shepard/api/search
Content-Type: application/json
Authorization: Bearer <token>

{
  "scopes": [
    {
      "collectionId": 42,
      "traversalRules": ["DataObject"]
    }
  ],
  "searchParams": {
    "query": { "property": "name", "operator": "contains", "value": "LOX" }
  }
}
```

---

### 3b. Collection search — `POST /shepard/api/search/collections`

Paginated full-text search across Collections visible to the caller.

**Query parameters:**

| param | type | default | description |
|-------|------|---------|-------------|
| `page` | int | 0 | zero-based page index |
| `size` | int | — | page size |
| `orderBy` | string | `createdAt` | sort field (`name`, `createdAt`) |
| `orderDesc` | bool | `true` | descending order |

**Request body:**

```json
{
  "searchParams": {
    "query": { "property": "name", "operator": "contains", "value": "LUMEN" }
  }
}
```

**Response envelope** (`CollectionSearchResult`):

```json
{
  "results": [ { "id": 42, "appId": "...", "name": "LUMEN Campaign 2024", … } ],
  "totalResults": 1,
  "searchParams": { … }
}
```

---

### 3c. Container search — `POST /shepard/api/search/containers`

Paginated search across Timeseries, File, and Structured-Data containers. Same
`page`/`size`/`orderBy`/`orderDesc` params as §3b.

---

### 3d. User / user-group search — `POST /shepard/api/search/users` and `/usergroups`

Used by the permissions UI to add members to Collections. Not intended for direct
data discovery.

---

## 4. MCP `search` tool

The MCP tool collapses the four-kind fan-out into **one JSON-RPC round-trip**, making
it the preferred surface for AI agents exploring "what do we have on X?".

**Tool name:** `search`

**Parameters:**

| param | type | required | description |
|-------|------|----------|-------------|
| `query` | string | ✅ | case-insensitive substring matched against `name` + `description` |
| `kind` | string | no | `Collection` \| `DataObject` \| `Container` \| `Reference` — omit for all |
| `limit` | int | no | max items per response (default 20, max 100) |
| `offset` | int | no | zero-based offset for pagination (default 0) |

Kind taxonomy — each coarse kind covers these Neo4j labels:

| kind | labels |
|------|--------|
| `Collection` | `:Collection` |
| `DataObject` | `:DataObject` |
| `Container` | `:TimeseriesContainer`, `:FileContainer`, `:StructuredDataContainer` |
| `Reference` | `:TimeseriesReference`, `:SingletonFileReference`, `:FileReference`, `:URIReference`, `:StructuredDataReference`, `:DataObjectReference`, `:CollectionReference` |

**Response envelope:**

```json
{
  "items": [
    {
      "appId": "019600a1-...",
      "kind": "DataObject",
      "name": "TR-004 Anomaly Investigation",
      "snippet": "Turbopump vibration spike at t=8s. Peak 12g rms…"
    }
  ],
  "total": 3,
  "limit": 20,
  "offset": 0
}
```

`total` is the post-permission-filter count (entities the caller can read) *before*
pagination. `total > items.length` means more pages are available.

**Example — find everything about the anomaly test run:**

```
search(query="TR-004")
→ returns the DataObject, any Container and Reference whose name mentions TR-004
```

**Example — scope to DataObjects only:**

```
search(query="LOX", kind="DataObject")
```

**Example — paginate (page 2 at limit 20):**

```
search(query="AFP", limit=20, offset=20)
```

---

## 5. Permission model

All three surfaces apply the same permission gate: each result row is resolved to its
parent **Collection** anchor and tested against `PermissionsService.isAccessTypeAllowed(Read)`.
Rows the caller cannot read are excluded silently — the caller never learns they exist.

Anchor resolution per kind:

| row kind | collection anchor walk |
|----------|----------------------|
| Collection | itself |
| DataObject | `(:Collection)-[:has_dataobject]->(:DataObject)` |
| Container | `(:Collection)-[:has_dataobject]->(:DataObject)-[:has_reference]->()-[:is_in_container]->(container)` |
| Reference | `(:Collection)-[:has_dataobject]->(:DataObject)-[:has_reference]->(ref)` |

Orphaned rows (no reachable Collection anchor) are excluded fail-closed.

The MCP `search` tool applies this gate before pagination so `total` always reflects
permitted matches; the `/shepard/api/search` REST endpoints delegate scoping to the
caller via the `SearchScope[]` body.

---

## 6. Implementation notes

- **Substring match**: case-insensitive `CONTAINS` via `toLower()` on both the query
  term and `name` / `description` properties. No full-text index — suitable for
  LUMEN/MFFD scale (thousands of nodes); larger deployments should consult the
  Meilisearch-backed header composable for performance-critical paths.
- **Per-call result cap**: the MCP Cypher queries cap at 500 raw rows per label before
  permission filtering; requests that exceed this on very large datasets should use
  `kind=` scoping.
- **Deduplication**: rows matching multiple Neo4j labels (legacy dual-label nodes) are
  deduplicated by `(kind, appId)`.
