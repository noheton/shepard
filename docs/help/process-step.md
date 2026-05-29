---
layout: default
title: Model a process step
description: How to represent a manufacturing step, test run, or analysis stage as a DataObject with lineage, annotations, and data containers
permalink: /help/process-step/
audience: user
stage: deployed
---
# Model a process step

A **process step** in shepard is a regular
[DataObject](/reference/data-object/) that carries:

- a name and description of the step,
- one or more **predecessor** links pointing at the DataObjects it
  was produced from,
- data containers (timeseries channels, files, structured-data records)
  holding the step's actual measurements and outputs,
- semantic annotations capturing domain context (equipment, standards,
  operator, batch, …), and
- optionally, child DataObjects for investigation sub-trees or
  intermediate sub-steps.

This page walks through a concrete example — an AFP layup step
followed by an NDT inspection — but the pattern applies to any
sequential workflow: test campaigns, satellite commissioning phases,
analysis pipelines, or quality gates.

## Before you start

You need a **Collection** to hold the process steps. If you don't have
one yet, create it from the Collections page — every DataObject lives
inside exactly one Collection.

## Step 1: create the first step

1. Open the target Collection.
2. Click **New DataObject** (the `+` button at the top of the
   DataObjects list).
3. Fill in:
   - **Name** — something unambiguous: `AFP Layup — Q1 Run 1` or
     `Hot-fire TR-004`.
   - **Description** (optional) — what happened, what equipment was
     used, any context that does not fit an annotation.
   - **Status** — use `DRAFT` while the step is in progress; advance
     to `IN_REVIEW` or `READY` once results are finalised.
4. Leave **Predecessor** empty for the first step in a chain.
5. Click **Create**.

## Step 2: link the next step to its predecessor

When you create the step that follows:

1. Click **New DataObject** again.
2. In the **Predecessors** field, start typing the name of the
   previous step and pick it from the autocomplete.
3. Click **Create**.

The predecessor link appears immediately in the **Dataset Lineage**
graph on the Collection page. Dashed orange arrows point from a
predecessor to its successor; solid arrows indicate parent → child.

You can add or change predecessors at any time by editing the
DataObject (pencil icon) and updating the **Predecessors** field.

## Step 3: attach data to the step

Each process step can carry any combination of payload containers:

| What you have | What to create |
|---|---|
| Sensor time-series (force, temperature, pressure, vibration …) | Timeseries reference — see [Upload data](/help/upload-data/) |
| PDFs, images, CAD files, scan results | File reference |
| Structured measurement results (JSON) | Structured-data reference |
| A link to an external resource (ERP record, CAD tool URL) | URI reference |

From the DataObject detail page, click **Add Reference** and pick the
appropriate type.

## Step 4: annotate the step

Semantic annotations capture the context that makes the step findable
and comparable later:

1. On the DataObject detail page, find the **Annotations** panel and
   click **+**.
2. Pick a **Property** (e.g. "has instrument", "performed by", "is
   part of campaign", "material batch") and a **Value** (a controlled
   term from the loaded ontologies, or a free-text IRI if you need a
   custom term).
3. Click **Add**.

Typical annotations for a manufacturing step:

| Property | Example value |
|---|---|
| has instrument | Automated Fibre Placement robot ABC-001 |
| operator | `urn:example:persons:engineer-42` |
| material batch | `CF/LMPAEK-2024-BL-047` |
| performed in | LUMEN test facility, Test bench 2 |
| conforms to | DIN EN 9100 |

For bulk annotation across many DataObjects in a campaign, see
[Annotate a container with semantic tags](/help/annotate-container/).

## Step 5: handle a rework loop

If a step fails inspection and needs to be redone, **do not delete the
failed step** — that would erase the history. Instead:

1. Keep the failed step with status `IN_REVIEW` or create a dedicated
   investigation child (click **New DataObject**, set the failed step
   as **Parent**).
2. Create a new DataObject for the rework step, linking the failed
   step as its **Predecessor**.
3. Create the re-test or verification step, linking the rework step
   as its predecessor.

The lineage graph then shows the full chain: original → failure →
rework → re-test. An auditor walking the graph can see exactly what
happened without any information being lost.

Example chain for an AFP anomaly:

```
AFP Layup Q1 Run 1 (READY)
  └─► NDT Inspection Q1 Run 1 (IN_REVIEW — fail)
        └─► Rework AFP Q1 (READY)
              └─► NDT Inspection Q1 Run 2 (READY — pass)
```

## Viewing the full process chain

- **Lineage graph** — open the Collection, scroll to **Dataset
  Lineage**, and expand the panel. The graph renders all
  Predecessor/Successor and Parent/Child links in the collection.
- **Sidebar navigator** — the left sidebar on the Collection page
  shows a tree of DataObjects; expand a node with the chevron (`›`)
  to see its children and navigate quickly between steps.
- **Provenance panel** — open any DataObject and expand the
  **Provenance** panel to see who created it, what actions were taken,
  and when. See [Trace dataset provenance](/help/provenance-tracing/).

## REST equivalent

For programmatic ingestion (e.g. a test-rig script that creates a
DataObject at the end of each run):

```
POST /v2/collections/{collectionAppId}/data-objects
Content-Type: application/json

{
  "name": "Hot-fire TR-005",
  "description": "Hold / repair run after TR-004 anomaly.",
  "status": "DRAFT",
  "predecessorIds": ["{tr-004-appId}"]
}
```

`predecessorIds` is a list of DataObject `appId` values (UUID v7).
The full DataObject IO schema is documented at
`/shepard/doc/swagger-ui` under the **DataObject** tag.

## Further reading

- [Upload data](/help/upload-data/) — attach files, timeseries, and
  structured data to a DataObject.
- [Explore collection lineage](/help/collection-lineage/) — navigate
  the predecessor/successor graph.
- [Annotate a container with semantic tags](/help/annotate-container/) —
  attach ontology terms to the containers inside a DataObject.
- [Trace dataset provenance](/help/provenance-tracing/) — who did what
  on a DataObject and when.
