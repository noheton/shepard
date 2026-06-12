---
layout: default
title: Template editor (reference)
permalink: /reference/template-editor/
audience: user
---
# Template editor

The visual **template editor** lets an instance-admin compose a
`ShepardTemplate` — which is a SHACL *shape* — by picking semantic predicates
from the vocabulary palette, without hand-writing Turtle. It is the
operator-facing surface of the "templates ARE shapes" model: one authored shape
drives the create form, instantiation validation, rendering, and the agent
contract.

For the task walkthrough see [Build a template](../help/build-a-template.md).

## Where to find it

**Admin → Templates → New template** (or the edit pencil on an existing row).
The dialog's **Body** mode toggle switches between:

- **Visual** — the shape editor (default).
- **Raw JSON** — the underlying JSON DSL, for power users.

## What the editor produces

The editor stores two things on the template's `body` (a JSON object):

| Key          | Meaning                                                                 |
|--------------|-------------------------------------------------------------------------|
| `editorState`| The editor's row model — reopened when you edit the template again.      |
| `shapeGraph` | The compiled SHACL Turtle — read by validation, the create form, and the SHACL playground (`/shapes/validate`). |

Bodies authored elsewhere (raw Turtle, hand-written JSON) have no `editorState`,
so the dialog opens them in the **Raw JSON** tab.

## The composition model

A template shape is one SHACL `sh:NodeShape`:

- **Shape IRI** — the node-shape's identifier (optional; the server mints a
  deterministic one when omitted).
- **Target class** — `sh:targetClass`; which entities the shape applies to.
- **Closed** — `sh:closed`; when on, undeclared predicates are rejected.
- **Property rows** — each is a `sh:PropertyShape`:

  | Field        | SHACL constraint | Notes                                              |
  |--------------|------------------|----------------------------------------------------|
  | Predicate IRI| `sh:path`        | Required. The metadata field this constrains.      |
  | Datatype     | `sh:datatype`    | Literal type; empty = the object is an IRI.         |
  | minCount     | `sh:minCount`    | Set to 1 to require the field.                      |
  | maxCount     | `sh:maxCount`    | Cap the value count.                                |
  | Allowed values | `sh:in`        | Pick-list; each member is a literal or a term IRI.  |
  | Nested node shape | `sh:node`   | The value must satisfy another node shape.          |

## The palette

The predicate palette merges two read-only sources:

- `GET /v2/shapes/predicates` — the curated substrate-routing vocabulary
  (carries a cardinality hint used to pre-fill min/maxCount).
- `GET /v2/semantic/terms/search?q=…` — ontology-term autocomplete (n10s),
  for predicates not yet in the routing table.

Clicking a palette item adds a pre-filled property row.

## Live preview and validation

| Endpoint                 | Role           | Used for                                                |
|--------------------------|----------------|---------------------------------------------------------|
| `POST /v2/shapes/build`  | authenticated  | Compile the editor DSL → canonical SHACL Turtle (live preview). New in V2CONV-B6. |
| `POST /v2/shapes/validate` | authenticated| Round-trip-validate a sample data graph against the compiled shape. |

`POST /v2/shapes/build` request body (mirrors `ShapeBuildRequestIO`):

```json
{
  "shapeIri": "urn:shepard:shape:demo",
  "targetClass": "http://semantics.dlr.de/shepard#DataObject",
  "closed": false,
  "properties": [
    {
      "path": "http://semantics.dlr.de/shepard#name",
      "datatype": "http://www.w3.org/2001/XMLSchema#string",
      "minCount": 1,
      "maxCount": 1,
      "in": [{ "value": "READY", "kind": "LITERAL" }],
      "node": null
    }
  ]
}
```

Response:

```json
{
  "shapeIri": "urn:shepard:shape:demo",
  "shapeGraph": "@prefix sh: <http://www.w3.org/ns/shacl#> .\n…",
  "error": null
}
```

A structurally invalid DSL (e.g. a blank predicate path) returns **400** with a
human-readable reason in `error`; the editor surfaces it inline.

## Inheritance

The **Extends** picker sets `parentTemplateAppId`. The child inherits the
parent's fields; the child's own rows override on collision. The picker is
scoped to same-kind, non-retired, non-cyclic templates. Inherited fields are
shown read-only above the body. See the templates reference for the full
copy-on-write versioning model.

## Forms from shapes — `GET /v2/templates/{appId}/form`

A data-kind template's shape doubles as a **form**: the descriptor endpoint
compiles the flattened `shapeGraph` into groups + fields (label, order,
required-ness, regex pattern, enum options, [DASH](https://datashapes.org/dash)
editor hints with constraint-scoring defaults) plus a server-computed `submit`
block pointing at the instantiation endpoint. Submitting values that violate
the shape returns **422** whose problem-JSON carries a structured
`violations[]` — each entry's `path` equals the descriptor's `fields[].path`,
so rendering an inline field error is a dictionary lookup. Caller-supplied
values ride the instantiation request's `attributes` map (keys = the
descriptor's `fields[].attributeKey`) and merge over the template's defaults
before validation. Retired templates answer 409; templates without a
`shapeGraph` or with a non-data kind answer 422. A minimal in-app preview
lives at **Tools → Form preview**; a Python consumption example ships at
`examples/btkvs-docket-showcase/form_demo.py`.

## Excel export from shapes — `GET /v2/templates/{appId}/export`

The same shape that drives the form drives a **spreadsheet projection**:
property shapes annotated with `urn:btkvs:cell-mapping` (A1-style cell
reference) and optionally `urn:btkvs:sheet` (worksheet name) place the focused
DataObject's attribute values into a generated `.xlsx` workbook —
`GET /v2/templates/{appId}/export?dataObjectAppId=<doAppId>` returns it with a
`Content-Disposition` download filename. Fields without cell-mappings are
skipped silently; an absent attribute value leaves its cell empty. Templates
whose shapes carry no cell-mappings answer 409 (nothing to place); unknown
template or DataObject answer 404; the caller needs Read on the DataObject's
Collection. A "Download Excel" button lives on **Tools → Form preview**; a
Python download example ships at
`examples/btkvs-docket-showcase/export_demo.py`. The Excel **import**
direction (workbook → cells → SHACL validation → DataObject) is planned —
see `BTKVS-C2`.

## Permissions

Creating and editing templates requires the **instance-admin** role. The build
and validate endpoints themselves are open to any authenticated user (they read
no stored data) — only persistence (`POST /v2/templates`) is admin-gated.

## See also

- [Build a template](../help/build-a-template.md) — task walkthrough.
- [Create from a template](../help/create-from-template.md) — the consumer side.
