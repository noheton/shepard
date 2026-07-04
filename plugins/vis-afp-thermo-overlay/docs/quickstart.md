---
stage: concept
last-stage-change: 2026-06-10
---

# vis-afp-thermo-overlay — quickstart

## What this does

Compare an AFP layup process trace with the NDT inspection result for the same tile —
in one view, synced on the same colour scale.

**You need:**
- An AFP course DataObject (from the `mffd-afp` collection) with timeseries data
- An OTvis NDT DataObject for the same tile (from the `mffd-ndt-thermography` collection)

**You get:**
- Left: 3D robot-head trajectory, colour-mapped by TCP temperature (or force/speed/pressure)
- Right: OTvis heatmap of the same tile after layup

## Steps (once slice 2 ships)

1. Open the AFP course DataObject detail page.
2. Click **"Open in AFP Thermo Overlay"** (appears once `vis-afp-thermo-overlay` is active).
3. The view pre-populates with the AFP DataObject appId.
4. Select the matching NDT DataObject from the tile-picker (filtered by same Section/Module).
5. Choose TCP channel, colour map, and view layout.
6. Click **Render**.

> **Slice 1 status:** the shape is declared and the plugin is registered. The render
> button and dual-pane view land in slice 3. Until then, the DataObject detail page
> shows a placeholder card with implementation status.

## Typical questions this answers

- "Did the consolidation-force dip at 14:32 correspond to the NDT-flagged void at S4·M13·L18?"
- "Which AFP courses had head-speed anomalies in tiles that later failed NDT?"
- "Show me the temperature profile for the ply-18 pass of the left-fuselage centre section."
