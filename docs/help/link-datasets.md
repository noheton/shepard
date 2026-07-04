---
title: Link datasets — predecessor, successor, and cross-references
description: How to connect DataObjects with provenance links, cross-references, and external URIs — and how to read the relationships table
permalink: /help/link-datasets/
layout: default
audience: user
---
# Link datasets: predecessor, successor, and cross-references

**What this is for.** Linking two DataObjects tells Shepard — and anyone who
reads your data later — that one dataset was produced from, or is related to,
another. That history is the backbone of FAIR-R (reusability) and DIN EN 9100
audit trails.

**Before you start.** You need *write* access on the Collection and at least one
DataObject to link from.

---

## Types of relationship

The **Add New Relationship** dialog offers four kinds:

| Kind | What it means | Example |
|---|---|---|
| **Predecessor** | The other DataObject came *before* this one in the process chain | TR-006 has TR-005 as predecessor after a repair |
| **Successor** | The other DataObject comes *after* this one | TR-004 (anomaly) has TR-005 (investigation) as successor |
| **Custom → Collection** | A named cross-reference to another whole Collection | Link a "raw campaign" DataObject to a "processed results" Collection |
| **Custom → Data Object** | A named cross-reference to a DataObject in any Collection | Link a component specification to a test result |
| **Custom → URI** | A link to an external resource | Link to a calibration certificate on a document server |

> **Predecessor vs. successor:** Adding "TR-005 is a successor of TR-004" is
> equivalent to adding "TR-004 is a predecessor of TR-005" — the link is
> directional but recorded once. Use whichever direction feels natural when
> you're standing on the source DataObject.

---

## Add a relationship

1. Open the DataObject you want to link *from*.
2. Scroll to the **Relationships** panel and click **+ Add Relationship**
   (the button in the top-right corner of the panel, or the floating **+** button).
3. The **Add New Relationship** dialog opens.
4. Pick the relationship **Type** from the first dropdown.
5. Fill in the target:
   - For **Predecessor / Successor**: search for the other DataObject by name.
   - For **Custom → Collection** or **Custom → Data Object**: search for the
     target entity and enter a short **Reference name** (e.g. "calibration run"
     or "raw data source").
   - For **Custom → URI**: paste the URL and give the reference a **Name**.
     You may also optionally set a **Relationship label** (e.g. "references",
     "isDerivedFrom").
6. Click **Save**.

The new relationship appears in the **Relationships** table immediately.

---

## Typed predecessor chips (provenance detail)

When a predecessor link carries a PROV-O relationship type, a small coloured
chip appears next to the "Predecessor" label in the table:

| Chip | Colour | Meaning |
|---|---|---|
| **informed by** | grey | Generic informational dependency — the predecessor informed the work |
| **revision of** | blue | Direct revision or correction — e.g. a re-test after a fix |
| **repairs** | orange | Rework / NCR-repair — the successor was produced to resolve a non-conformance in the predecessor |
| **concession** | amber | The predecessor's output was accepted under a concession (use-as-is) and this dataset records that decision |

These types are set by API or import manifest; the UI dialog currently creates
untyped predecessor links. Hover the chip to see the full PROV-O predicate IRI.

---

## View relationships

Every DataObject has a **Relationships** panel on its detail page. The table has
four columns:

| Column | What it shows |
|---|---|
| **Relationship** | The link type (Predecessor, Successor, Collection Reference, Data Object Reference, URI Reference) plus any typed chip |
| **Name** | The referenced entity's name — click it to jump to that DataObject or Collection |
| **Information** | The reference kind and any semantic annotations attached to the reference itself |
| **Created** | When the link was added and by whom |

Predecessor and successor links also appear in the **Lineage** panel on the
Collection page, where they are drawn as dashed orange arrows. See
[Navigate a large lineage graph]({% link help/lineage-graph.md %}) for zoom
modes, filters, and click-through navigation.

---

## Annotate a relationship

Collection references, Data Object references, and URI references are
*annotatable* — you can attach semantic terms to the link itself (e.g. to say
what *kind* of relationship it represents in your domain vocabulary):

1. Hover the relationship row in the table.
2. Click the **tag icon** (🏷) that appears on the right.
3. The annotation dialog opens pre-scoped to this reference. Follow the
   same steps as [Annotating data with semantic tags]({% link help/annotating-data.md %}).

Predecessor and successor links are not separately annotatable (the typed chip
is set at creation time via API).

---

## Edit a URI reference

URI references support in-place editing:

1. Hover the row in the **Relationships** table.
2. Click the **pencil icon** (✏) that appears.
3. Update the **Name**, **URI**, or **Relationship label**.
4. Click **Save**.

Predecessor, successor, Collection, and Data Object references cannot be edited
after creation — delete and re-add if the target changes.

---

## Delete a relationship

1. Hover the row in the **Relationships** table.
2. Click the **bin icon** (🗑) that appears.
3. Confirm the deletion.

Deletion removes the link but leaves both DataObjects intact.

---

## Parent vs. predecessor

The **Edit DataObject** dialog has a separate **Parent** field. Parent/child
encodes *structural containment* (e.g. a test run lives inside a campaign
DataObject). Predecessor/successor encodes *process provenance* (e.g. a
processed result was derived from a raw measurement run). Both show in the
Lineage graph — parents as solid arrows, predecessors as dashed orange arrows.

---

## If something looks wrong

| Symptom | Likely cause | Fix |
|---|---|---|
| Relationship table is empty after adding | The page may not have refreshed | Scroll away and back, or hard-refresh the page |
| Target DataObject doesn't appear in the search | It is in a different Collection and you used the wrong type | For cross-Collection links use **Custom → Data Object**; the search then spans all Collections you can read |
| Tag icon is greyed out | The row is a Predecessor or Successor link, which is not annotatable | Annotate the DataObject itself instead |
| Typed predecessor chip is missing | The link was created without a relationship type | Set the type via the API or the import manifest; the UI dialog creates untyped links |

---

> Power user? See the
> [relationships reference](/reference/relationships/) for the entity model,
> REST endpoints, and PROV-O predicate IRIs.

## See also

- [Navigate a large lineage graph]({% link help/lineage-graph.md %})
- [Explore collection lineage]({% link help/collection-lineage.md %})
- [Annotating data with semantic tags]({% link help/annotating-data.md %})
- [Provenance tracing]({% link help/provenance-tracing.md %})
