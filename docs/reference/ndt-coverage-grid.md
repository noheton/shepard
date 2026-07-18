---
stage: deployed
last-stage-change: 2026-07-18
audience: user
layout: default
title: NDT coverage grid
description: Reference for the MFFD 14x14 thermography coverage widget — the annotation predicates, cell-bucketing rule, and colour model behind it
permalink: /reference/ndt-coverage-grid/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The feature shipped
> on 2026-05-28 (`MFFD-NDT-GRID-1`); this page documents its behaviour from
> the source as it stands at the backfill date.

<!-- backfill: DOCS-3A6/7 2026-07-18 -->

# NDT coverage grid reference

**Feature ID:** MFFD-NDT-GRID-1  
**UI surface:** the *NDT coverage grid* card on the Collection landing page  
**Component:** `MffdNdtGridCard.vue` · pure helpers in `frontend/utils/mffdNdtGrid.ts`

For the casual task ("read the grid"), see `docs/help/ndt-coverage-grid.md`.
This page documents the data model the widget reads.

---

## What it renders

A 14×14 grid: **Section** (S1…S14) on one axis, **Module** (M1…M14) on the
other, for MFFD upper-shell OTvis / thermography campaigns. Each cell aggregates
the DataObjects that share the same `(section, module)` coordinate.

The grid is entirely **annotation-driven** — it reads semantic annotations off
the Collection's DataObjects. There is no NDT-specific schema; the `appId` plus
a handful of `urn:shepard:mffd:*` predicates are the whole contract, which is
why any Collection whose DataObjects carry these predicates gets the grid for
free.

---

## The annotation predicates

| Predicate | Meaning | Example value |
|---|---|---|
| `urn:shepard:mffd:section` | Grid row — shell section | `S4` |
| `urn:shepard:mffd:module` | Grid column — shell module | `M13` |
| `urn:shepard:mffd:layer` | Ply / layer covered (shown as chips in advanced mode; listed in the tooltip) | `L18` |
| `urn:shepard:mffd:frame` | Thermography frame within the measurement | `F4` |
| `urn:shepard:mffd:qsClassification` | Quality-status verdict; value `NOK` triggers the red failure border | `OK` / `NOK` / `unknown` |

A DataObject needs at least a `section` and a `module` to be placed on the grid;
DataObjects missing either are not bucketed (they contribute to no cell).

---

## Bucketing and colour model

- **Cell key.** DataObjects are bucketed by `cellKey(section, module)`
  (`extractGridPosition` reads the annotations; `bucketByGrid` groups them).
- **Cell colour.** Driven by the DataObject count in the cell via the *inferno*
  colormap. `colourForCount` floors any cell with ≥ 1 measurement at a minimum
  brightness (t = 0.15) so a single-measurement cell stays visible and is never
  confused with an empty (zero-count) cell.
- **Failure border.** `extractQsClassification` reads
  `qsClassification`; a cell where any bucketed DataObject reads `NOK` gets a
  red 2px border. `hasFailedMeasurement` returns `false` when the predicate is
  absent, so the border affordance lights up automatically once the
  classification is seeded — no code change required.

The pure helpers in `frontend/utils/mffdNdtGrid.ts` are unit-tested
(`frontend/tests/unit/mffdNdtGrid.test.ts`, 30 cases).

---

## Conditional mount

The card is mounted only when the Collection carries grid-annotated
DataObjects. `useMffdNdtGridProbe` runs a cheap probe — a DataObject list plus a
small (5-DataObject) annotation sample — before committing to a full render, so
Collections that have nothing to show pay only the probe cost.

---

## Interaction contract

| Event | Payload | Consumer |
|---|---|---|
| Hover | tooltip string (`Section … · Module … · N measurements · layers: …`) | inline |
| Click | `select` emit carrying the cell's bucketed DataObjects | Collection landing page wiring |

Basic mode renders colour + labels; advanced mode adds per-cell layer chips
(strict superset — advanced never removes what basic shows). Below ~800px
viewport the labels drop but the colour grid is preserved.

---

## Cross-references

- `docs/help/ndt-coverage-grid.md` — casual task page
- `docs/reference/mffd-process-chain.md` — MFFD process-chain reference
- `docs/reference/semantic-annotations.md` — how `urn:shepard:*` annotations work
- `docs/help/cross-track-view.md` — sibling Collection-landing analysis widget
