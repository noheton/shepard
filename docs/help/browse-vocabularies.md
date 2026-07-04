---
title: Browse vocabularies and predicates
description: Find the controlled vocabulary terms used to annotate data in Shepard, and see which ones are in use on a given collection or data object.
permalink: /help/browse-vocabularies/
layout: default
audience: user
---

# Browse vocabularies and predicates

A **vocabulary** is a set of standardised property names (called **predicates**) used to
annotate data. Examples: Dublin Core, DataCite, CHAMEO, or Shepard's own MFFD terms.
Browsing vocabularies lets you find the right predicate before you annotate, and verify
what terms are already in use across your data.

---

## Open the vocabulary browser

**From any page:**

1. Click **Tools** in the top navigation bar.
2. Choose **Vocabularies** from the menu.

You see a list of every vocabulary that has been loaded into Shepard (enabled or not).

**From a collection or data object:**

1. Open the collection or data object detail page.
2. Scroll to the **Semantic Annotations** panel.
3. Click **Show terms used by this collection** (or **… by this data object**).

The vocabulary browser opens pre-filtered to only the vocabularies whose terms actually
appear on that entity — a much shorter list when you already know what you annotated with.

---

## Read the vocabulary list

Each row shows:

| Column | Meaning |
|--------|---------|
| **Label** | Human-readable name (e.g. "Dublin Core Terms") |
| **Prefix** | Short alias (e.g. `dcterms:`) used in annotation dialogs |
| **URI** | Full namespace URI |
| **Type** | `owl`, `skos`, `rdfs`, or custom |
| **Enabled** | Whether terms from this vocabulary can be selected when annotating |

Disabled vocabularies are listed but their terms do not appear in annotation autocomplete.
Only an instance admin can enable or disable a vocabulary.

---

## Browse predicates inside a vocabulary

Click a vocabulary row to open its predicate list.

The predicate list shows every property declared inside that vocabulary:

| Column | Meaning |
|--------|---------|
| **URI** | Full predicate URI (e.g. `http://purl.org/dc/terms/creator`) |
| **Label** | Short display name |
| **Object type** | Expected value type: literal, IRI, integer, decimal, date… |
| **Cardinality** | How many values are allowed (e.g. `0..*` for many) |
| **Required** | Whether the predicate is mandatory in SHACL templates that reference it |

Use the **search box** at the top of the list to filter by URI or label.

Click a predicate row to see its full detail page, including a list of all entities
in the system that carry that annotation.

---

## Tips

- **Finding the right term:** type a keyword in the predicate search box. The
  autocomplete in the Annotate dialog also searches all enabled vocabularies.
- **Checking annotation coverage:** open a collection, click "Show terms used by this
  collection" — you see at a glance which vocabulary families are in use on your data.
- **New vocabularies:** ask your instance admin to upload an OWL/SKOS ontology file via
  the **Admin > Ontology bundles** pane. Once uploaded and enabled, its predicates
  appear automatically in this browser and in annotation autocomplete.

---

## Related help pages

- [Annotating data with semantic tags](annotating-data.md)
- [Query with SPARQL](query-with-sparql.md)
- [Validate data against a shape](validate-against-a-shape.md)
