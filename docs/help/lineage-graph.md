---
title: Navigate a large lineage graph
description: How to use zoom, filters, and the minimap to explore a Collection lineage graph at MFFD scale
permalink: /help/lineage-graph/
layout: default
audience: user
---
# Navigate a large lineage graph

The **Lineage** panel on a Collection page draws every DataObject and every
parent / predecessor relationship between them. For small Collections (a
handful of test runs) the graph fits on screen at the default zoom. For
campaign-scale Collections — thousands of tracks across years of
production — the same graph would be unreadable as a wall of dots. This
page shows you the affordances that keep it readable.

## Three zoom modes

The Lineage panel adapts to your zoom level. Scroll-wheel up to zoom in,
scroll-wheel down to zoom out, drag the canvas to pan. The mode in use is
shown in the caption underneath the chart (e.g. `zoom mode: detail`).

| Mode | What you see |
|---|---|
| **macro** (zoomed far out) | Process-type or ply bubbles. Bubble size scales with the number of underlying DataObjects; line thickness shows how many links cross between two bubbles. |
| **meso** | Every DataObject becomes its own node, but labels are hidden so the structure is legible. |
| **detail** (zoomed in) | Full labels, status colours, and the hover tooltip. |

## Filtering

The pill row at the top of the panel narrows the graph without reloading
anything:

- **Status** — pick one or more lifecycle statuses (DRAFT, READY,
  PUBLISHED, …). The chip shows the count of active selections.
- **Process type** — pick one or more process-type values (AFP layup,
  bridge weld, NDT, …). These come from the
  `urn:shepard:mffd:process-type` annotation; if your Collection doesn't
  carry that annotation yet, the menu will be empty.
- **Around DO N · depth ≤ K** — focus the graph on a specific
  DataObject's neighbourhood. (This filter is set by clicking the
  "Lineage" button on a DataObject's detail page; click the chip's `✕`
  to clear it.)

Filters compose: pick **Status = PUBLISHED** and **Process type = AFP
layup** and the graph shows only published AFP-layup datasets. The
**Reset** button clears all filters at once.

## The minimap

The small overview chart on the right shows the whole graph at low
resolution. Use it to keep your bearings while you pan around in the
main canvas — the minimap doesn't pan itself. The **Hide minimap**
button removes it if you want the full width for the main canvas.

On narrow viewports (under ~960 px wide) the minimap drops below the
main chart instead of sitting to the right.

## Click-through

- In **macro** mode, clicking a process-type bubble adds that process
  type to the filter pills — a fast drill-down.
- In **meso** or **detail** mode, clicking a node opens that
  DataObject's detail page.

## When to use the Timeline instead

The Lineage view answers structural questions: "which datasets feed
which?", "where does this dataset's data come from?". For temporal
questions — "how many tracks did we run per day?", "when did the NCRs
cluster?" — the **Timeline** panel (just above Lineage on the
Collection page) is the cardinality-friendly answer. See
[Collection timeline]({% link help/collection-timeline.md %}).
