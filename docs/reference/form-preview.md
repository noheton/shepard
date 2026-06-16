---
title: Form preview — reference
audience: user
---

# Form preview reference

**Feature IDs:** FORM-DESCRIPTOR-1, BTKVS-B2, BTKVS-C1  
**Route:** `/tools/form-preview`  
**API surface:** `/v2/` (this fork's development surface; upstream `/shepard/api/...` untouched)  
**Design doc:** `aidocs/integrations/125-btkvs-shacl-form-templates.md`

---

## Overview

The **Form preview** tool compiles a data-kind `ShepardTemplate`'s SHACL shape
into a renderer-friendly descriptor: groups, fields with DASH editor hints,
and a server-computed submit block. It is the write-direction mirror of the
shape-validator — the same `shapeGraph` Turtle that drives 422 violation
checking is the source compiled here.

This page covers:

1. The `GET /v2/templates/{templateAppId}/form` endpoint and its response
2. Which template kinds are form-renderable
3. DASH editor-hint scoring rules
4. The submit block and violation mapping
5. Excel export (`GET /v2/templates/{appId}/export`)
6. ETag caching
7. In-context entry (`?template=&focusAppId=`)
8. Worked API examples

---

## 1. Endpoint

```
GET /v2/templates/{templateAppId}/form
```

| Parameter | Type | Description |
|---|---|---|
| `templateAppId` | UUID v7 string (path) | `appId` of the `ShepardTemplate` to compile. |
| `If-None-Match` | string (header, optional) | ETag from a previous response; returns 304 on match. |

**Permissions:** Authenticated user (JWT / API key). No additional per-collection gate.

### Status codes

| Code | Meaning |
|---|---|
| 200 | Descriptor compiled successfully. |
| 304 | Not modified — ETag matched `If-None-Match`. |
| 401 | Authentication required. |
| 404 | No template with that `appId`. |
| 409 | Template is retired; choose a non-retired version. |
| 422 | Template not form-renderable (wrong `templateKind`, no `shapeGraph`, unparseable Turtle). |

All 4xx responses carry `Content-Type: application/problem+json` with a `detail`
field explaining which guard failed.

---

## 2. Form-renderable template kinds

Only **data-kind** templates — whose SHACL shape defines what a writer is
allowed to record — are form-renderable. `VIEW_RECIPE` shapes are the
read-direction (rendering, not writing) and return 422.

| `templateKind` | Form-renderable |
|---|---|
| `DATAOBJECT_RECIPE` | ✅ |
| `COLLECTION_RECIPE` | ✅ |
| `STRUCTURED_RECIPE` | ✅ |
| `VIEW_RECIPE` | ❌ (422 — rendering projection, not a write shape) |
| `MAPPING_RECIPE` | ❌ (422 — transformation recipe) |

A template must also carry a non-blank `shapeGraph` Turtle in its body (or
inherit one from a parent template) to be compilable. Templates without a
shape return 422 with a hint to use `POST /v2/shapes/build` to author one.

---

## 3. Response shape

```json
{
  "templateAppId":  "019e7243-f995-7914-be80-3a4b6e7f1d22",
  "templateKind":   "DATAOBJECT_RECIPE",
  "title":          "AFP layup step",
  "shapeIri":       "urn:shepard:mffd:AFPLayupStepShape",
  "groups": [
    { "id": "urn:shepard:form:group:identity",  "label": "Identity",  "order": 1.0 },
    { "id": "urn:shepard:form:group:process",   "label": "Process",   "order": 2.0 }
  ],
  "fields": [
    {
      "path":       "http://purl.org/dc/terms/title",
      "attributeKey": null,
      "label":      "Title",
      "description": "Short human-readable name for the layup step.",
      "group":      "urn:shepard:form:group:identity",
      "order":      1.0,
      "datatype":   "http://www.w3.org/2001/XMLSchema#string",
      "required":   true,
      "pattern":    null,
      "editor":     "http://datashapes.org/dash#TextFieldEditor",
      "singleLine": true,
      "placeholder": "e.g. AFP-Q1-Ply-05",
      "defaultValue": null,
      "options":    null,
      "visibleWhen": null,
      "cellMapping": null
    },
    {
      "path":       "urn:shepard:mffd:consolidationForce",
      "attributeKey": "consolidationForce",
      "label":      "Consolidation force (N)",
      "group":      "urn:shepard:form:group:process",
      "order":      2.0,
      "datatype":   "http://www.w3.org/2001/XMLSchema#decimal",
      "required":   false,
      "editor":     "http://datashapes.org/dash#TextFieldEditor",
      "cellMapping": { "sheet": "Process", "cell": "D12" }
    }
  ],
  "submit": {
    "method": "POST",
    "href":   "/v2/collections/{collectionAppId}/data-objects",
    "violationContract": "422 body carries violations[]{path, message} — path equals fields[].path"
  }
}
```

### Field properties

| Property | Source | Notes |
|---|---|---|
| `path` | `sh:path` (predicate IRI) | Byte-identical to `violations[].path` on 422 — maps error to field. |
| `attributeKey` | Derived from `urn:shepard:attribute:{key}` namespace | Short key to use in the instantiation request's `attributes` map. Null for non-attribute paths. |
| `label` | `sh:name` | Falls back to the IRI's local name. |
| `description` | `sh:description` | Help text shown below the input. |
| `group` | `sh:group` IRI | References a `groups[].id`. |
| `order` | `sh:order` | Fields with `sh:order` come first; unordered fields last, alphabetical. |
| `datatype` | `sh:datatype` | Full XSD IRI (e.g. `xsd:string`, `xsd:decimal`, `xsd:dateTime`). |
| `required` | `sh:minCount >= 1` | |
| `pattern` | `sh:pattern` | Regex the submitted value must satisfy. |
| `editor` | `dash:editor` or scoring default | See §4. |
| `singleLine` | `dash:singleLine` | |
| `placeholder` | `urn:shepard:form:placeholder` | Custom prompt text. |
| `defaultValue` | `sh:defaultValue` | Pre-filled literal. |
| `options` | `sh:in` | List of allowed literal values. |
| `visibleWhen` | `urn:shepard:form:visibleWhen` | JSON expression for conditional display. Presentation-only — server enforces `sh:property`. |
| `cellMapping` | `urn:btkvs:cell-mapping` + `urn:btkvs:sheet` | Excel export target (BTKVS-C2). |

---

## 4. DASH editor-hint scoring

When a property shape carries no explicit `dash:editor` the compiler derives
one from the shape's constraints, in priority order:

| Constraint present | Assigned editor |
|---|---|
| `sh:in` | `dash:EnumSelectEditor` |
| `sh:datatype = xsd:boolean` | `dash:BooleanSelectEditor` |
| `sh:datatype = xsd:date` | `dash:DatePickerEditor` |
| `sh:datatype = xsd:dateTime` | `dash:DateTimePickerEditor` |
| `dash:singleLine = false` | `dash:TextAreaEditor` |
| *(none of the above)* | `dash:TextFieldEditor` |

An explicit `dash:editor` on the property shape wins over the scoring default.

The UI renders these hints:

| Editor | UI component |
|---|---|
| `TextFieldEditor` | Single-line `<v-text-field>` |
| `TextAreaEditor` | Multi-line `<v-textarea>` |
| `EnumSelectEditor` | `<v-select>` with `options[]` |
| `BooleanSelectEditor` | `<v-checkbox>` or true/false `<v-select>` |
| `DatePickerEditor` | Date picker |
| `DateTimePickerEditor` | Date-time picker |

---

## 5. Submit block and violation mapping

The `submit` block is server-computed — the client never chooses an endpoint:

| `templateKind` | `submit.method` | `submit.href` |
|---|---|---|
| `DATAOBJECT_RECIPE` | `POST` | `/v2/collections/{collectionAppId}/data-objects` |
| `COLLECTION_RECIPE` | `POST` | `/v2/collections` |
| `STRUCTURED_RECIPE` | `POST` | `/v2/containers/structured-data/{containerId}/entries` |

`{collectionAppId}` and `{containerId}` are URI template variables filled at
submit time from the user's context — the client resolves them from the entity
the form is anchored to.

### Violation mapping

When the submit endpoint returns 422, its `violations[]` array carries objects
with the same `path` IRI as `fields[].path`. Mapping an error to its field is
a dictionary lookup — no client-side SHACL stack required:

```typescript
import { violationsByPath } from "~/composables/useTemplateForm";

const errorMap = violationsByPath(problem422Body);
// errorMap["http://purl.org/dc/terms/title"] = "Value is required"
// Map directly to the field whose path equals this key.
```

---

## 6. Excel export (BTKVS-C1)

```
GET /v2/templates/{templateAppId}/export?dataObjectAppId={dataObjectAppId}
Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

Downloads a pre-filled XLSX workbook. The same `urn:btkvs:cell-mapping` +
`urn:btkvs:sheet` annotations that surface in `fields[].cellMapping` drive the
workbook: the focused DataObject's attribute values land in the mapped cells.

| Parameter | Description |
|---|---|
| `templateAppId` | Template with `cellMapping` annotations. |
| `dataObjectAppId` | The DataObject instance whose attribute values to inject. |

**Response:** 200 + `Content-Disposition: attachment; filename="docket-{appId}.xlsx"`.
Content type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

---

## 7. ETag caching

The endpoint emits a weak ETag over the template `appId` + the SHA-256 of the
inheritance-flattened body:

```
ETag: W/"a3f9c2b1..."
```

On subsequent requests send `If-None-Match: W/"a3f9c2b1..."`. If the template
and its parent chain have not changed, the server returns **304 Not Modified**
with no body — useful for poll-heavy clients that re-render on template edits.

---

## 8. In-context entry

The global `/tools/form-preview` picker allows any authenticated user to
inspect any template by name or raw `appId`.

The canonical in-context entry is from the **"Record a …"** action menu on a
DataObject or Collection detail page. It routes here with query params:

```
/tools/form-preview?template=<templateAppId>&focusAppId=<entityAppId>
```

The page reads `?template` and auto-loads the descriptor with zero typing. The
`?focusAppId` is forwarded to the future edit-form prefill (FORM-UX-ACTIONBUTTON)
when the full in-place form ships.

An MCP agent or CI script can also call the endpoint directly:

```bash
curl -H "apikey: $API_KEY" \
  https://shepard.example.org/v2/templates/$TEMPLATE_APP_ID/form
```

---

## 9. Worked examples

### Inspect a template's form descriptor

```bash
TEMPLATE_APP_ID="019e7243-f995-7914-be80-3a4b6e7f1d22"

curl -s \
  -H "apikey: $API_KEY" \
  -H "Accept: application/json" \
  https://shepard.example.org/v2/templates/$TEMPLATE_APP_ID/form \
  | jq '{title: .title, fields: [.fields[] | {label, editor: (.editor | split("#") | last), required}]}'
```

Sample output:

```json
{
  "title": "AFP layup step",
  "fields": [
    { "label": "Title",               "editor": "TextFieldEditor",    "required": true  },
    { "label": "Consolidation force", "editor": "TextFieldEditor",    "required": false },
    { "label": "TCP temperature (°C)","editor": "TextFieldEditor",    "required": false },
    { "label": "Passed QA",           "editor": "BooleanSelectEditor","required": true  }
  ]
}
```

### Use ETag for efficient polling

```bash
# First request — get ETag
ETAG=$(curl -si -H "apikey: $API_KEY" \
  https://shepard.example.org/v2/templates/$TEMPLATE_APP_ID/form \
  | grep -i '^etag:' | awk '{print $2}' | tr -d '\r')

# Subsequent request — 304 if unchanged
curl -s -o /dev/null -w "%{http_code}" \
  -H "apikey: $API_KEY" \
  -H "If-None-Match: $ETAG" \
  https://shepard.example.org/v2/templates/$TEMPLATE_APP_ID/form
# → 304
```

### Download Excel docket for a DataObject

```bash
DO_APP_ID="019e8c17-abcd-7300-8f1a-000000000001"

curl -s \
  -H "apikey: $API_KEY" \
  -H "Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  "https://shepard.example.org/v2/templates/$TEMPLATE_APP_ID/export?dataObjectAppId=$DO_APP_ID" \
  --output docket.xlsx
```

---

## Data model

Form preview is a pure read projection — it introduces no new Neo4j entities.
It reads from:

| Entity | Role |
|---|---|
| `:ShepardTemplate` | Source template (`templateAppId`); supplies kind, title, `shapeGraph`. |
| `:ShepardTemplate` (parent chain) | Inherited via `parentTemplateAppId` → `TemplateInheritanceResolver` flattens before compile. |

The compiled descriptor is **not stored** — it is re-derived on every request
(with ETag caching to avoid redundant recompilation when the template is
unchanged).

---

## Related

- `docs/reference/tools.md` — Tools cluster overview (§6 Form preview entry)
- `docs/reference/view-recipes.md` — `VIEW_RECIPE` templates (the read-direction sibling)
- `docs/reference/template-editor.md` — authoring `ShepardTemplate` entities
- `aidocs/integrations/125-btkvs-shacl-form-templates.md` — BTKVS design doc (SHACL form templates + Excel)
