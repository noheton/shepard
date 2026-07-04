---
title: Compare a channel across tracks (cross-track view)
description: Plot one timeseries channel across every DataObject in a Collection
permalink: /help/cross-track-view/
layout: default
audience: user
---
# Compare a channel across tracks (cross-track view)

When you've got a Collection holding many similar runs — every layup
track in an AFP campaign, every hotfire test, every pour of a
production batch — and you want to see the *same* channel across all
of them at once, the Cross-track view is the affordance.

## Where it lives

1. Open a **Collection** detail page.
2. Scroll past the Dataset Lineage panel and expand
   **Cross-track view**.

Shepard loads every DataObject in the Collection (capped at 100;
larger Collections show a banner with the truncation count) and
renders one chart per DO in a 4-column small-multiples grid.

## What it shows

- **Cells share the same x and y axis range** — so you can compare
  cells directly. A taller spike in one cell really is a taller
  spike.
- **Hovering one cell highlights the same x position in every other
  cell** — useful for "what was happening at second 8 across every
  track?"
- **Clicking a cell** opens that DataObject's detail page.

DataObjects that don't have a channel matching the resolved
predicate render an empty cell labelled **no matching channel**.
The view doesn't drop them; you can still see at a glance which DOs
are missing the data you're looking for.

## Which channel does it pick?

The view ships with one predicate baked in:
`urn:shepard:afp:tcp-temperature-c` (TCP — tool-centre-point —
temperature for AFP layup). Shepard resolves each DataObject's
TimeseriesReferences, finds the channel whose **semantic annotation
predicate** matches, and plots that channel.

If a DataObject has more than one channel matching the predicate
(rare), the first by alphabetical `symbolicName` is picked.

A picker for swapping the predicate at runtime is on the roadmap
(`TPL-VIEW-EDITOR`). For now: if you need a different channel,
either re-tag the channels with a different `urn:shepard:*`
annotation or ask an admin to seed a new VIEW_RECIPE template.

## Behind the scenes

The cross-track view is the user-facing surface of the
`POST /v2/data-objects/cross-timeseries-bulk` endpoint. The same
endpoint is available to any caller (Python notebook, MCP tool,
external script) — see `docs/reference/collections.md` for the
request/response shape.

## See also

- `docs/help/timeseries-plotting.md` — single-DataObject channel
  charts.
- `docs/help/annotating-data.md` — how channel annotations get on
  the data in the first place.
- `docs/reference/collections.md` — Collection reference page.
