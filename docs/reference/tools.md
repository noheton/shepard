---
title: Tools cluster — reference
audience: user
---

# Tools cluster reference

**Feature IDs:** TOOLS-NAV-01, TPL2b (shapes/render), V2b-DIFF (snapshot diff), FORM-DESCRIPTOR-1 (form preview), V2CONV-B3 (materialize mapping)  
**API surface:** `/v2/` (this fork's development surface; upstream `/shepard/api/...` untouched)

---

## Overview

The **Tools** top-nav entry surfaces six research-tooling surfaces that were
previously buried under `/me#semantic` or reachable only by direct URL. Every
tool operates on entities by `appId`; no paths or URLs are exposed to the user.

| Surface | Route | Auth |
|---|---|---|
| Vocabularies | `/semantic/vocabularies` | Authenticated |
| SPARQL playground | `/semantic/sparql` | Authenticated |
| Shape validator | `/shapes/validate` | Authenticated |
| Snapshot diff | `/snapshots/diff` | Authenticated |
| Shapes render playground | `/shapes/render` | Authenticated |
| Form preview | `/tools/form-preview` | Authenticated |

A seventh surface — **Materialize mapping** (`/tools/materialize-mapping`) — is
reached in-context from a DataObject detail page via the "Materialize" action
button (TOOLS-CONTEXT-DO-MATERIALIZE). It is not listed in the Tools tiles
because its entry point is always in-context.

---

## 1. Vocabularies

**Route:** `/semantic/vocabularies`  
**Backend:** `GET /v2/admin/semantic/ontologies` (list loaded ontologies + predicate counts)

Browse the semantic repository: loaded ontologies, individual predicates (IRIs,
labels, cardinality constraints), and QUDT unit bindings. Read-only for all
authenticated users.

---

## 2. SPARQL playground

**Route:** `/semantic/sparql`

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/semantic/{repoAppId}/sparql?query=…` | Read-only SPARQL SELECT/CONSTRUCT (query in param) |
| POST | `/v2/semantic/{repoAppId}/sparql` | Read-only SPARQL SELECT/CONSTRUCT (query in body) |

**Parameters:**
- `repoAppId` — the `appId` of the `:SemanticRepository` node to query. The
  system repository is the global one seeded at startup; per-collection
  repositories have their own `appId`.

**Permissions:** Authenticated user. The endpoint runs read-only queries only.

### Worked example

```bash
curl -X POST \
  -H "apikey: $API_KEY" \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: application/sparql-results+json" \
  https://shepard.example.org/v2/semantic/$REPO_APP_ID/sparql \
  --data 'SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10'
```

Response: SPARQL JSON results (W3C format), 200 OK.

---

## 3. Shape validator

**Route:** `/shapes/validate`

### Endpoint

| Method | Path | Purpose |
|---|---|---|
| POST | `/v2/shapes/validate` | Run SHACL conformance check |

**Request body (JSON):**
```json
{
  "dataTurtle":  "# Turtle RDF of the candidate data graph\n@prefix ex: ...",
  "shapeTurtle": "# Turtle RDF of the SHACL shape graph\n@prefix sh: ..."
}
```

Both fields are required Turtle strings. The endpoint is stateless — it does
not read or write any stored entity; it is a pure function of its two inputs.

**Response (200 OK):**
```json
{
  "conforms": false,
  "violations": [
    {
      "severity": "Violation",
      "focusNode": "ex:myNode",
      "resultPath": "sh:name",
      "message": "Value must be of type xsd:string"
    }
  ]
}
```

### In-context entry

When navigated from a DataObject detail page with
`?focusAppId=<doAppId>&scope=data-object`, the validator pre-fills the data
graph textarea with the DataObject's Turtle subgraph (fetched from
`GET /v2/data-objects/{appId}/rdf`). When a `?templateAppId=<...>` is also
supplied, the shape-graph textarea is pre-filled from the template's
`shapeGraph` Turtle field.

### Who calls this

- MCP tool: an LLM agent constructing a candidate payload pre-flight validates
  before attempting a write that would otherwise round-trip a 422.
- UI form-builder: validates partial payloads at typing time.
- Plugin authors: quick sanity check on SHACL shape edits.

---

## 4. Snapshot diff

**Route:** `/snapshots/diff`

### Endpoint

| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/snapshots/{aAppId}/diff/{bAppId}` | Compare two snapshots |

**Parameters:**
- `aAppId` — the base snapshot (older)
- `bAppId` — the head snapshot (newer)

**Response (200 OK):**
```json
{
  "added":   [{ "entityAppId": "...", "revision": 3 }],
  "removed": [{ "entityAppId": "..." }],
  "changed": [{ "entityAppId": "...", "fromRevision": 1, "toRevision": 2 }],
  "unchangedCount": 42
}
```

Entities present in both snapshots with identical revision values are counted
in `unchangedCount` but not listed (the set can be large).

**Permissions:** Authenticated user required. No additional per-collection gate
is applied at this revision; a stricter gate can be added when cross-collection
diff is in scope.

### Inner workings

A Snapshot is a point-in-time record of every `VersionableEntity` reachable
from a Collection (see `docs/reference/snapshots.md`). The diff endpoint
compares `SnapshotEntry` sets: entities present in A but not B are `removed`;
present in B but not A are `added`; present in both but with different
`revision` values are `changed`.

---

## 5. Shapes render playground

**Route:** `/shapes/render`

### Endpoint

| Method | Path | Purpose |
|---|---|---|
| POST | `/v2/shapes/render` | Render a VIEW_RECIPE template against a focus entity |

**Request body (JSON):**
```json
{
  "templateAppId": "<UUID v7 of a VIEW_RECIPE ShepardTemplate>",
  "focusDataObjectAppId": "<UUID v7 of the DataObject to render against>"
}
```

**Response (200 OK):**
```json
{
  "renderer": "trace-3d",
  "channelBindings": [
    {
      "role": "x",
      "status": "DECLARED",
      "channelSelector": "{\"measurement\":\"AFP\",\"device\":\"tcp\",...}",
      "unit": "http://qudt.org/vocab/unit/MilliM"
    }
  ]
}
```

**Beta caveat (TPL2b):** Only `VIEW_RECIPE` templates are supported. Channel
bindings are returned with `status = "DECLARED"` — live resolution against the
focus DataObject's timeseries references ships in TPL2c once the TS-ID
migration (`aidocs/platform/87`) lands a stable single-key channel identity.

### Renderer dispatch

The frontend reads the `renderer` field and routes to:
- `"trace-3d"` or `"tresjs"` → `<Trace3DView>` (flat-array adapter + legend)
- `"table"` → inline `<v-table>` of channel values
- `"urdf"` → URDF viewer (resolution via `urdfFileAppId` from the FileReference)
- (unknown) → `<PlaceholderImplStatus>` noting the unsupported renderer

### ViewRecipeRenderer SPI

Renderers are registered via the `ViewRecipeRenderer` ServiceLoader SPI
(`de.dlr.shepard.spi.view.ViewRecipeRenderer`). Each renderer is keyed by the
SHACL shape IRI declared in the `VIEW_RECIPE` template body. No core edits are
needed to add a renderer — ship a new `@Named` CDI bean implementing the SPI.

---

## 6. Form preview

**Route:** `/tools/form-preview`

### Endpoint

| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/templates/{templateAppId}/form` | Compile a template's SHACL shape into a form descriptor |

**Parameters:**
- `templateAppId` — the `appId` of the `ShepardTemplate` whose shape to compile.
  Accepts any template kind, but `DATA_KIND` templates with a `shapeGraph` Turtle
  field produce the richest descriptors.

**Response (200 OK):**
```json
{
  "groups": [
    {
      "label": "Identity",
      "fields": [
        {
          "predicate": "http://purl.org/dc/terms/title",
          "label":     "Title",
          "editor":    "dash:SingleLineStringEditor",
          "required":  true,
          "datatype":  "xsd:string"
        }
      ]
    }
  ],
  "submitBlock": { "endpoint": "POST /v2/data-objects", "mode": "merge-patch" }
}
```

### Inner workings

`FormDescriptorCompiler` reads the template's `shapeGraph` Turtle (resolved
through the full `TemplateInheritanceResolver` chain, so parent shapes are
included). It enumerates `sh:property` paths and maps each to a DASH editor
hint, datatype, cardinality, and unit annotation. The result is a
JSON-serialisable form blueprint the UI renders via `<FormDescriptorViewer>`.

### In-context entry

The "Record a …" entries in `ActionMenuButton` route to this page pre-populated
with `?template=<templateAppId>&focusAppId=<entityAppId>`. Until the full
in-place edit form lands (FORM-UX-ACTIONBUTTON), this playground is the
canonical entry for data-kind templates.

---

## 7. Materialize mapping (in-context only)

**Route:** `/tools/materialize-mapping`  
**Entry point:** DataObject detail page → "Materialize" action (V2CONV-B3)  
**Full reference:** `docs/reference/materialize-mapping.md`

Not a standalone Tools-menu entry — always pre-populated from the source
DataObject's `appId`. A `MAPPING_RECIPE` template defines the transform;
the endpoint `POST /v2/mappings/{templateAppId}/materialize` derives a new
output reference (or an ephemeral view-model) from existing references bound
at run time via the `TransformExecutor` SPI.

---

## Data model

Tools do not introduce new Neo4j entities. They consume:

| Entity | Role |
|---|---|
| `:ShepardTemplate` (`VIEW_RECIPE` / `MAPPING_RECIPE`) | Drives render + form preview |
| `:SemanticRepository` | SPARQL target; `repoAppId` in the path |
| `:Snapshot` | Snapshot diff A / B endpoints |
| `:DataObject` | Focus entity for render, validate pre-fill, materialize |

---

> New here? See the task pages in `docs/help/` once screenshots are available
> (UIVERIFY-tools-cluster).
