---
title: Search across all your data
description: How to use the Shepard search page to find collections, data objects, references, containers, and structured data by name or property filter
permalink: /help/search-everything/
layout: default
audience: user
---
# Search across all your data

The **Search** page (`/search`) lets you find any Shepard entity by name or
property filter. A simple text box handles everyday lookups; an advanced query
builder handles complex property-and-operator filters for power users.

---

## Open search

Click **Search** in the top navigation bar, or press `/` anywhere in the UI
(keyboard shortcut) to jump to the search page with the cursor in the search
box.

---

## Simple search

Type any word or phrase into the search box and press **Enter** or click the
magnifying-glass button.

Shepard searches **Collection names** by default. Results appear in a table
below the search box. Click any row to open that entity.

**Examples**

| What you type | What you find |
|---------------|---------------|
| `MFFD` | All collections whose name contains "MFFD" |
| `TR-004` | Collections (or DataObjects if you change the type) named "TR-004" |
| `hotfire` | Any collection with "hotfire" in the name |

---

## Change what you're searching

Use the **Search type** dropdown to switch between:

| Type | What is searched |
|------|-----------------|
| **Collection** | Collection names (default) |
| **DataObject** | DataObject names within a collection |
| **Reference** | Reference names within a collection + data object |
| **Structured data** | Structured data records within a collection |
| **File container** | File container names |
| **Timeseries container** | Timeseries container names |
| **Structured container** | Structured data container names |

When you select a type that requires a scope (DataObject, Reference,
Structured data), additional dropdowns appear:

1. **Collection** — pick the collection to search within.
2. **DataObject** — optionally narrow to a specific DataObject inside the
   collection.
3. **Traversal rules** — optionally follow predecessor/successor links to
   include related DataObjects in the scope.

---

## Advanced query builder

Click **Advanced** to expand the query builder panel. This lets you filter by
any property using operator-value pairs.

The underlying format is a JSON query document. You can use the visual row
builder, or click **Edit as JSON** to paste a query directly.

### Supported operators

| Operator | Matches when … |
|----------|---------------|
| `contains` | The property value contains the search string (substring) |
| `eq` | Exact match |
| `gt` / `lt` | Greater / less than (numeric or date) |
| `gte` / `lte` | Greater-or-equal / less-or-equal |

Combine multiple conditions with **AND**, **OR**, or **NOT** by adding rows
and choosing the logical connector.

**Example — find DataObjects in collection `MFFD-Q1` whose name starts with "AFP":**

```json
{
  "property": "name",
  "operator": "contains",
  "value": "AFP"
}
```

Set the search type to **DataObject** and scope to the `MFFD-Q1` collection,
then click **Search**.

---

## Reading results

Results appear in a paginated table. Each row shows the entity's name and its
parent (collection or DataObject). Click any row to open the entity's detail
page.

Click **Reset** to clear all inputs and start a new search.

---

## Bookmarkable searches

Simple searches store the query in the URL (`?q=…`). Advanced searches store
the full query document and scope in the URL, so you can share or bookmark a
search with all filters intact.

---

## Related help

- [Collection lineage](/help/collection-lineage/) — navigate the predecessor/successor graph instead of searching by name
- [Annotating data](/help/annotating-data/) — add semantic annotations so your data is more searchable
- [Query the semantic graph with SPARQL](/help/query-with-sparql/) — run structured RDF queries across all annotations
