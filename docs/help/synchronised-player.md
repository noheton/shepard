---
title: Scrub a multi-payload DataObject (synchronised player)
description: Play a single timeline across timeseries, video, thermography, and spatial payloads
permalink: /help/synchronised-player/
layout: default
audience: user
---
# Scrub a multi-payload DataObject (synchronised player)

A DataObject in shepard can hold many payloads at once — a timeseries channel
recording, a robot-camera video, a thermography frame bundle, a spatial
line-scan of the same operation. The **synchronised player** binds every
payload to a single time cursor, so when you scrub the toolbar you scrub the
TS chart AND the video playhead AND any other payload that knows about time.

It is for answering "what was happening at t=8.2 s, across all the sensors?"
without flipping between four tabs.

## Where it lives

1. Open a **DataObject** detail page.
2. Scroll past the Data References / Relationships / Spatial-data panels.
3. Expand **Synchronised player**.

The panel **only appears when the DataObject carries at least two distinct
payload kinds**. A DO with only timeseries (or only video) shows no
synchronised-player section — there is no point in a "multi" with one tile.

## What you can do

- **Press play** to advance the cursor. Every tile reads the cursor and
  updates its representation: the TS chart slides its marker, the video
  seeks, the spatial readout updates its t value.
- **Drag the scrubber** to a specific time. Same effect — every tile jumps
  to that time in lock-step.
- **Change the playback rate** (0.5×, 1×, 2×, 4×). Useful for long
  recordings.
- **Hover the TS chart** to jump the cursor to that point.
- **Use the native video controls** to seek the video — the cursor and the
  other tiles follow.
- **Press rewind** to jump to the start of the playable range.

## What "playable range" means

Different payloads cover different time windows. A 12-second video and a
30-second timeseries on the same DO have overlapping but different ranges.
The scrubber respects the **intersection** of every tile's range — you can
only scrub to times where every tile has data.

A tile that does not have a time axis (a static pointcloud, the composite
thermography heatmap) does **not** constrain the range. It just renders a
"highlight at t" representation when t changes.

## What syncs today, what's a placeholder

This is a v1 surface, and the brief is honest about it:

- **Timeseries tile** — syncs both directions. Hover writes the cursor;
  scrubbing draws a marker.
- **Video tile** — syncs both directions. Native controls write the cursor;
  scrubbing seeks the video.
- **Thermography tile** — shows the bundle's quality chip + summary stats
  only. Genuine frame-by-frame sync needs a frame-strip view, tracked as
  `MFFD-MULTIPLAYER-THERMO-1` in the backlog.
- **Spatial tile** — shows the playhead's current t as a numeric readout.
  Animated brush-head marker on the trajectory is tracked as
  `MFFD-MULTIPLAYER-SPATIAL-1`.

The thermography and spatial tiles still appear when those payloads exist on
the DataObject, so the player gives you a sense of "everything that belongs
to this one event" — even if not everything scrubs yet.

## Troubleshooting

| You see | What it means |
| --- | --- |
| No "Synchronised player" panel | The DataObject has fewer than two distinct payload kinds — only TS, only video, etc. Add a second payload or use the per-kind panes instead. |
| Scrubber is disabled with a banner | No tile has reported a playable range yet — timeseries / video metadata is still loading. Give it a moment. |
| Tile order is wrong for my workflow | v1 uses a hardcoded order: TS → video → thermography → spatial. Per-DataObject reordering via a semantic annotation is tracked as `MFFD-MULTIPLAYER-CONFIG-1`. |
| I want to play three DOs side by side | Multi-DO sync is tracked as `MFFD-MULTIPLAYER-MULTI-DO-1`; today the player is single-DO. |

## What it is not (yet)

- It does not export the synchronised view as a mosaic video (that's
  `MFFD-MULTIPLAYER-EXPORT-1`).
- It does not let you annotate regions while playing (that's
  `MFFD-MULTIPLAYER-ANNOTATE-1`).
- It does not stream live data in real-time (that's `MFFD-MULTIPLAYER-LIVE-1`).

Those are queued; the `MFFD-MULTIPLAYER-*` family in
`aidocs/16-dispatcher-backlog.md` is the authoritative list.
