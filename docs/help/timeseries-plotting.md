---
title: Plot timeseries data
description: How to view interactive charts for sensor channels
permalink: /help/timeseries-plotting/
layout: default
---

# Plot timeseries data

Shepard renders time-series channels as interactive charts with zoom, pan, and crosshair tooltips — no download or separate tool needed.

## From a dataset (DataObject)

1. Open a **Collection** and navigate to a dataset that has timeseries references.
2. Expand the **Data References** panel (opens by default).
3. Click the **Graph & Metrics** button next to a timeseries reference entry.
4. The chart shows all channels overlaid. Use the check-boxes on the left to show/hide individual channels.

**Chart controls:**

| Action | Result |
|---|---|
| Mouse wheel / pinch | Zoom in/out on the time axis |
| Click & drag on the chart | Pan left/right |
| Drag the slider below the chart | Adjust the visible time window |
| Click a channel name | Expand its statistics and annotations |
| Toolbox icons (top right) | Zoom reset · Save chart as PNG |

## From a timeseries container

1. Open **Containers → Timeseries** and select a container.
2. The channel table lists every available channel (measurement · device · location · …).
3. Expand any row using the **▶** toggle to see a live preview chart for that channel, along with its semantic annotations.

The preview chart uses 1-second averages so it loads quickly even for large datasets. Navigate to the linked dataset for full-resolution data.

## Semantic annotations on channels

Channels can carry semantic annotations (ontology terms) visible in the expanded row. To add one:

1. Expand the channel row.
2. Click **Add annotation** (requires *Edit data* permission on the container).
3. Search for an ontology term or paste an IRI directly.

See [Create a semantic annotation](../reference/semantic-annotation.md) for the full annotation workflow.
