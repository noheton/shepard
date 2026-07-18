---
stage: deployed
last-stage-change: 2026-07-18
audience: user
layout: default
title: Read the NDT coverage grid
description: See at a glance which sections and modules of an MFFD upper-shell thermography campaign have been inspected — and which cells failed
permalink: /help/ndt-coverage-grid/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The feature shipped
> on 2026-05-28 (`MFFD-NDT-GRID-1`); this page documents its behaviour from
> the source as it stands at the backfill date.

<!-- backfill: DOCS-3A6/7 2026-07-18 -->

# Read the NDT coverage grid

For an MFFD upper-shell thermography (OTvis / NDT) campaign, the question you
ask first is *"have we inspected every part of the shell yet, and did anything
fail?"* The **NDT coverage grid** answers that in one glance: a 14×14 heat-map
of **Section** (rows) × **Module** (columns), coloured by how many measurements
landed in each cell.

## Where it lives

1. Open a **Collection** that holds MFFD thermography DataObjects.
2. On the Collection landing page, find the **NDT coverage grid** card.

The card only appears when the Collection actually carries grid-annotated
DataObjects — a cheap probe checks a sample first, so empty Collections don't
pay for a card that would be blank.

## How to read it

- **Colour = measurement count.** Cells use the *inferno* colormap — darker
  cells have few measurements, brighter cells have many. A cell with even a
  single measurement is floored to a visible brightness, so "inspected once" is
  never mistaken for "not inspected at all" (which stays black).
- **A red border means a failure.** Any cell whose DataObjects carry a
  `qsClassification = "NOK"` annotation gets a 2px red border. That's your
  *"go look here"* signal.
- **Hover for detail.** Hovering a cell shows *"Section S4 · Module M13 · 6
  measurements · layers: L8, L18"* — the exact coordinates, the count, and which
  layers were covered.
- **Click to drill in.** Clicking a cell selects the DataObjects bucketed into
  it, so the rest of the page can react (open the list, jump to a detail page).

## Basic vs. advanced

In **basic mode** the grid shows the colour (count) and the Section/Module
labels — the minimum you need to spot coverage holes and failures.

Turn on **advanced mode** and each cell additionally shows **per-cell layer
chips** (which plies were inspected in that Section/Module). Advanced mode only
ever *adds* to what basic shows — it never hides the colour or the labels.

On a narrow screen (below ~800px) the labels drop away but the colour grid — the
part that carries the coverage story — is preserved.

## When the red borders don't light up

The failure border keys on the `urn:shepard:mffd:qsClassification` annotation.
If your OTvis parser hasn't emitted that predicate yet, every cell reads as
passing (no red border) — the affordance is dormant, not broken. As soon as the
classification annotation is seeded on the DataObjects, the borders appear with
no change to the grid itself.

## See also

- `docs/help/cross-track-view.md` — compare one channel across every DataObject in a Collection
- `docs/help/annotating-data.md` — how the Section/Module/layer/`qsClassification` annotations get onto the data
- `docs/reference/ndt-coverage-grid.md` — the annotation predicates and data shape behind the grid
- `docs/reference/mffd-process-chain.md` — the wider MFFD process-chain reference
