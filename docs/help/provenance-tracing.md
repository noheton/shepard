---
title: Trace dataset provenance
description: How to view who acted on a dataset and when
permalink: /help/provenance-tracing/
layout: default
audience: user
---
# Trace dataset provenance

Shepard records every significant action on a dataset as a provenance activity: who created it, who updated it, who read it, who deleted a reference, and so on.

## Provenance graph

1. Open a **Collection** and navigate to a dataset.
2. Scroll to the **Provenance Graph** panel and expand it.
3. The graph shows:
   - The **dataset** itself (blue square, centre).
   - **Predecessor / child datasets** it is related to (orange circles).
   - **Agents** (users) who acted on it (purple diamonds).
   - **Edges** labelled with the action kinds and counts per agent.

**Interacting:**

- **Scroll** to zoom.
- **Drag** nodes to rearrange.
- **Hover** for details (action counts per agent, relationship types).

## Activity feed

For a time-based view, open the **Activity** panel on the **Collection** page. The sparkline charts show CREATE / UPDATE / DELETE counts over the last 30 days across all datasets in the collection.

## Provenance in export

When you export a collection as an **RO-Crate** (the *Download as RO-Crate* button on the collection page), the provenance trail is embedded in the crate metadata following the RO-Crate Profile specification. Each activity maps to a `CreateAction` / `UpdateAction` / `DeleteAction` entity in `ro-crate-metadata.json`.
