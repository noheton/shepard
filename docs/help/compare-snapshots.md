---
title: Compare two snapshots
description: Use the Snapshot diff tool to see what DataObjects were added, removed, or modified between two Collection snapshots.
permalink: /help/compare-snapshots/
layout: default
audience: user
---

# Compare two snapshots

A **snapshot** is a point-in-time capture of a Collection's contents — which DataObjects it
contained, and what their key metadata looked like at that moment. The Snapshot diff tool
compares any two snapshots and shows you exactly what changed between them.

> **Shortcut.** When you are already looking at a Collection's Snapshots pane, click
> **Compare with…** on any snapshot row. That opens the Snapshot diff tool with that
> snapshot pre-filled as the "A" side — skip to [step 3](#step-3-pick-snapshot-b) below.

## Step 1 — Create snapshots (if you haven't already)

Snapshots must be created before they can be compared. To create a snapshot:

1. Open the Collection you want to track.
2. Scroll to the **Snapshots** section on the Collection detail page.
3. Click **New Snapshot**, enter a descriptive name (e.g. `before-rework` or
   `after-ndt-pass`), and click **Save**.

Repeat at another point in time — for example, before and after a rework step — so you
have two snapshots to compare.

## Step 2 — Open the Snapshot diff tool

1. Click **Tools** in the top navigation bar.
2. Choose **Snapshot diff** from the tools list.

The tool opens with two empty dropdowns.

## Step 3 — Pick snapshot B

If you arrived via the **Compare with…** shortcut, Snapshot A is already filled in.
Otherwise:

1. Click the **Snapshot A (older)** dropdown and type the snapshot name, collection name,
   or the first few characters of its ID. Select the baseline you want to compare from.
2. Click the **Snapshot B (newer)** dropdown and select the snapshot you want to compare to.

Both dropdowns search across all snapshots you have access to — you can compare snapshots
from different collections.

## Step 4 — Compare

Click **Compare**. The tool calls `GET /v2/snapshots/{a}/diff/{b}` and displays the result
below the inputs.

The diff result lists:

| Field | Meaning |
|-------|---------|
| `added` | DataObjects present in B but not in A |
| `removed` | DataObjects present in A but not in B |
| `modified` | DataObjects present in both, but with changed metadata |

## Share or bookmark the comparison

Click **Share** to copy a bookmarkable URL to your clipboard. The URL encodes both
snapshot IDs as query parameters (`?a=…&b=…`), so anyone with access can open the
same comparison directly.

## Troubleshooting

| Message | Fix |
|---------|-----|
| *Both snapshot appIds are required* | Make sure both Snapshot A and Snapshot B are selected before clicking Compare. |
| *403 — permission denied* | You need at least read access on both Collections that own the snapshots. |
| *404 — snapshot not found* | The snapshot may have been deleted, or the appId in the URL is incorrect. Try selecting fresh snapshots from the dropdowns. |

## See also

- [Collection lineage](./collection-lineage.md) — trace the predecessor / successor chain
  across DataObjects
- [Provenance tracing](./provenance-tracing.md) — audit who changed what and when
- [Tools overview](../reference/tools.md) — all Tools cluster surfaces and their endpoints
