---
title: Explore collection lineage
description: How to visualise parent/child and predecessor/successor relationships between datasets
permalink: /help/collection-lineage/
layout: default
audience: user
---
# Explore collection lineage

Datasets (DataObjects) in a collection can be linked by two types of relationship:

- **Parent / child** — a dataset is structurally contained within another (e.g. a test run within a campaign).
- **Predecessor / successor** — a dataset was produced from another in a processing step (e.g. a processed result derived from raw data).

## Lineage graph

1. Open a **Collection**.
2. Scroll to the **Dataset Lineage** panel and expand it.
3. The graph renders automatically once the datasets load.

**Reading the graph:**

| Symbol | Meaning |
|---|---|
| Solid arrow | Parent → child relationship |
| Dashed orange arrow | Predecessor → successor relationship |
| Node colour | Lifecycle status (see legend below the graph) |

**Interacting:**

- **Scroll** to zoom in/out.
- **Drag** a node to rearrange the layout.
- **Hover** a node to see the dataset name, status, and description.
- **Click** a node to highlight its directly connected neighbours.

## Setting relationships

Relationships are set when creating or editing a dataset:

1. Open or create a dataset.
2. In the **Edit** dialog (pencil icon), scroll to the **Relationships** section.
3. Set the **Parent** or **Predecessors** fields.

Changes appear in the lineage graph after the panel refreshes.
