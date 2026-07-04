---
title: Search ontology terms
description: Type into a box and watch every loaded ontology term filter live, then see each matched term in its place — as a tree or a graph.
permalink: /help/search-ontology-terms/
layout: default
audience: user
---

# Search ontology terms

Before you annotate data you usually want to find the *right* term — and see which
vocabulary it comes from. The **ontology term search** lets you type a keyword and
watch every loaded term filter live, then shows each match **in its place** in the
ontology: grouped under the vocabulary it belongs to, as a tree or an interactive
graph.

This is a read-only discovery tool. It does not change any data; it helps you decide
which term to pick when you do annotate.

---

## Open the search

**From the Semantic hub:**

1. Open your profile (top-right) and choose the **Semantic** section, or go to
   `/me#semantic`.
2. Click **Ontology search**.

**From the Vocabularies page:**

1. Go to **Tools → Vocabularies** (or `/semantic/vocabularies`).
2. Click the **Search ontology terms** button at the top of the page.

Either way you land on `/semantic/search` with the cursor already in the search box.

---

## Search as you type

Start typing — results update on their own after a short pause (no Enter key needed).

- **Minimum two characters.** One letter is too broad, so nothing is searched until
  you have typed at least two.
- **Live.** As you keep typing, the list narrows to match. Only your most recent
  keystrokes count — older, slower searches never override what you typed last.
- **Match on label, synonym, or URI.** Terms are found by their human label, their
  alternate labels and synonyms, or their full URI.

If nothing matches, you see a short "no terms found" note — try a different keyword or
a shorter fragment.

---

## Read the results

Matched terms are grouped by the **vocabulary (namespace)** they belong to. Each group
header shows a short prefix (e.g. `dcterms`, `rdf-schema`) so you can tell at a glance
which ontology a term comes from.

You can switch between two views:

| View | What it shows |
|------|----------------|
| **Tree** | An expandable list — one row per vocabulary, the matching terms nested underneath. Best for scanning labels and descriptions. |
| **Graph** | An interactive node-link diagram — each vocabulary is a hub, each matched term hangs off it. Best for seeing the shape of a result at a glance. |

In either view, each term shows its label and (when available) a short description.

---

## Act on a term

Click a term to follow it through to:

- its **predicate detail page**, which lists every entity in Shepard already annotated
  with that term, and
- the **SPARQL playground**, pre-filled with a query for that term.

That makes the search a natural starting point for "find the term, then see who uses
it".

---

## Tips

- **Not sure of the exact word?** Type a fragment — synonyms and alternate labels are
  searched too, so `creat` finds *Creator*.
- **Two terms with the same name?** The vocabulary grouping tells them apart — e.g. a
  `title` from Dublin Core sits under `dcterms`, a different `title` under another
  namespace.
- **Nothing loads at all?** The instance may have no ontologies seeded yet. Ask your
  instance admin to upload an OWL/SKOS bundle via **Admin → Ontology bundles**; once
  enabled, its terms appear here automatically.

---

## Related help pages

- [Browse vocabularies and predicates](browse-vocabularies.md)
- [Annotating data with semantic tags](annotating-data.md)
- [Query the semantic graph with SPARQL](query-with-sparql.md)
