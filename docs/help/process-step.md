---
layout: default
title: Record a process step
permalink: /help/process-step/
audience: user
---
# Record a process step

A **process step** in shepard is an ordinary data object that is linked to
one or more *predecessor* data objects. The predecessor link says "this step
came after that one" — building a traceable process chain from raw input to
final result.

Use this pattern for: test runs in a campaign (TR-001 → TR-002 → …),
manufacturing steps (AFP layup → NDT inspection → rework → re-inspection),
or any workflow where the output of one step feeds into the next.

---

## Before you start

- You need at least **Writer** permission on the collection.
- The predecessor data object must already exist in the same collection.

---

## Create the process step

1. Open the collection.
2. Click **Create data object** (the **+** button in the data objects panel or
   the top-right area of the collection page).
3. The **Create Data Object** dialog opens. If templates are configured for
   this collection and you are in basic mode, a template picker appears first —
   choose the appropriate template, or click **Start from blank** to continue.
4. In **Step 1: Properties / Relationships**, fill in:
   - **Name** — a short, descriptive name for this step
     (e.g. `TR-006 Re-test after repair`).
   - **Description** — what this step captures, including any relevant
     context (test conditions, process parameters, anomaly status).
5. Under **Relationships**, click **Add Predecessor**.
   - Start typing the name or ID of the prior step and select it from the
     autocomplete list (e.g. `TR-005 Hold / repair`).
   - Add more predecessors with additional **Add Predecessor** buttons if this
     step has multiple inputs (e.g. a merge step that consolidates two parallel
     tracks).
6. Optionally set a **Parent** — to nest this step inside a sub-tree within
   the collection hierarchy.
7. Click **Next** to go to **Step 2: Attributes**, then **Create**.

shepard navigates to the new data object. The predecessor link is immediately
visible in the **Provenance** graph and the **Ancestor Chain** panel on the
detail page.

---

## Add data to the process step

Once the data object is created, attach the data produced in this step:

- **Files** (reports, images, CAD outputs) — use the **Files** panel →
  **Upload** button. See [Upload data](/help/upload-data/).
- **Timeseries** (sensor channels, test measurements) — use the
  **Timeseries** panel → **Add reference**.
- **Annotations** — tag the step with controlled vocabulary terms
  (e.g. `propellant: LOX/LH2`, `operator: alice`). See
  [Annotating data](/help/annotating-data/).

---

## Edit the predecessor link later

Predecessor links are editable after creation:

1. Open the data object detail page.
2. Click the **Edit** button (pencil icon) in the header.
3. In the edit dialog, scroll to **Relationships** → **Predecessor** and
   add, change, or remove predecessor links.
4. Click **Save**.

---

## View the process chain

- **Ancestor Chain panel** — on the data object detail page, shows the
  full predecessor chain as a collapsible list.
- **Provenance graph** — on the data object detail page, shows the chain
  as an interactive graph. Pan and zoom to explore multi-step lineages.
- **Collection lineage view** — on the collection page, shows all data
  objects and their predecessor links across the entire collection. Useful
  for reviewing a full campaign at a glance.

---

## Typical patterns

**Linear campaign:**
```
TR-001  →  TR-002  →  TR-003  →  TR-004 (anomaly)
                                    ↓
                              TR-005 (hold / repair)
                                    ↓
                              TR-006 (re-test)
```

**Parallel merge:**
```
AFP layup Q1  ──┐
                ├─→  Frame welding  →  Assembly
AFP layup Q2  ──┘
```

Each arrow is a predecessor link. shepard stores and renders all of them.

---

## See also

- [Collection lineage graph](/help/collection-lineage/) — visualise the full
  process chain across a collection.
- [Provenance tracing](/help/provenance-tracing/) — trace who did what and when.
- [Upload data](/help/upload-data/) — attach files and timeseries to a process step.
- [Annotating data](/help/annotating-data/) — tag process steps with controlled
  vocabulary terms.
