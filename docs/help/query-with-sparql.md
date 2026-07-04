---
title: Query the semantic graph with SPARQL
description: How to use the SPARQL playground to run read-only SELECT and ASK queries against Shepard's annotation store
permalink: /help/query-with-sparql/
layout: default
audience: user
---
# Query the semantic graph with SPARQL

Shepard's semantic substrate stores every [semantic annotation](/help/annotating-data/)
as a triple in an n10s-managed RDF graph. The **SPARQL playground** lets you write
`SELECT` and `ASK` queries against that graph and inspect the results in a table or
raw JSON — directly from the browser.

---

## Open the playground

From the top navigation bar choose **Tools → SPARQL playground**, or go to
`/semantic/sparql` directly.

You can also open it pre-filled from an entity's context menu:

- On a **Collection** detail page: **⋮ → Query this collection's annotations**
- On a **DataObject** detail page: **⋮ → Query this entity's annotations**

Either link appends `?focusAppId=<appId>&scope=collection|data-object` so the
query is seeded with a `FILTER` for that entity and the URL remains bookmarkable.

---

## Run a query

1. Leave the **Repository ID** field set to `internal` (the default n10s-backed store
   that holds all annotations you create through the UI or the MCP tools).
2. Type or paste a SPARQL `SELECT` or `ASK` query into the editor.
3. Click **Run query** or press **Ctrl+Enter**.

Results appear below the editor:

- **Table** (default) — one column per projected variable.  Click any column header
  to sort.  Switch to **JSON** to see the raw SPARQL 1.1 JSON result set.
- **ASK** — shows `true` / `false` as a coloured badge.

Click **Reset** to restore the default example query.

---

## Example queries

### List all annotations on a specific DataObject

Replace `<appId>` with the DataObject's appId (visible in the URL or in the
**Semantic Annotations** panel on its detail page).

```sparql
PREFIX schema: <https://schema.org/>

SELECT ?predicate ?object
WHERE {
  ?s schema:identifier "<appId>" .
  ?s ?predicate ?object .
}
```

### Find all DataObjects annotated with a given term

```sparql
PREFIX oa: <http://www.w3.org/ns/oa#>

SELECT ?subject ?value
WHERE {
  ?subject oa:hasBody ?value .
  FILTER(CONTAINS(STR(?value), "turbopump"))
}
LIMIT 50
```

### Check whether a specific annotation exists (ASK)

```sparql
ASK {
  ?s <urn:shepard:thermography:recordingType> "evaluation" .
}
```

---

## Restrictions

Only `SELECT` and `ASK` are accepted. `INSERT`, `DELETE`, `UPDATE`, `DROP`,
`CLEAR`, `LOAD`, and `COPY` are rejected by the server-side
`SparqlQueryValidator` and return a 400 error. Use the
[annotation API](/reference/semantic-annotations/) or the
[MCP tools](/help/annotating-data/#annotating-many-entities-at-once-batch-via-mcp)
to write data.

---

## Admin SPARQL access

The admin SPARQL panel at `/admin#sparql-playground` uses the same query engine
and accepts the same syntax. It is restricted to `instance-admin` users and is
intended for schema-level inspection rather than day-to-day annotation queries.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| 401 Unauthorized | Session expired | Reload and log in again |
| 400 Bad request | Mutation keyword in query | Use `SELECT` or `ASK` only |
| 0 rows returned | No matching triples | Check the predicate IRI (copy it from the Annotations panel) |
| Repository not found | Typo in Repository ID | Use `internal` for the default annotation store |

---

## See also

- [Annotating data with semantic tags](/help/annotating-data/)
- [Semantic annotations reference](/reference/semantic-annotations/)
- [Browse vocabularies](/help/browse-vocabularies/) — find predicate IRIs to use in queries
