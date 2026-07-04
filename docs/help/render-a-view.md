---
title: Render a view recipe
description: Use the Shapes render playground to run a VIEW_RECIPE template against a DataObject and see your data as a 3D trace, table, URDF robot model, or thermal overlay.
permalink: /help/render-a-view/
layout: default
audience: user
---

# Render a view recipe

A **view recipe** is a reusable template that describes how to project a DataObject's
timeseries channels onto a visual — a 3D trace, a data table, a robot model pose, a
thermal overlay. The Shapes render playground lets you pick a template and a DataObject
and see the result immediately.

> **Shortcut.** When you open a DataObject that has a compatible view recipe attached
> (via a template), an **Open in render playground** button appears on the DataObject
> detail page. That button pre-fills the inputs and takes you straight to the rendered
> view — skip the steps below.

## Open the playground

1. Click **Tools** in the top navigation bar.
2. Choose **Shapes render** from the tools list.

The playground opens with empty inputs.

## Fill in the inputs

### 1. Choose a DataObject

Either:
- Use the **Collection → DataObject** pickers to browse to the DataObject you want to
  visualise, **or**
- Toggle **Enter appId directly** and paste the DataObject's appId (UUID v7).

### 2. Pick a view recipe template

Click the **VIEW_RECIPE template** field and start typing the template name. The
autocomplete shows every `VIEW_RECIPE` template available on your instance.

Not sure which template to pick? View recipes are listed under **Admin → Templates**
(filter by kind = `VIEW_RECIPE`). The template description explains which channel roles
it expects.

### 3. Click Render

Click **Render**. The playground sends your template and DataObject to
`POST /v2/shapes/render`, which returns a set of **channel bindings** — one row per
role the recipe needs (`x`, `y`, `z`, `color`, …).

> **Beta note.** Bindings come back with `status = DECLARED`. The channel selector the
> recipe specifies (e.g. `tcp_position.x`) must already exist in the DataObject's
> linked timeseries containers for the render to succeed. If a role can't be resolved,
> the playground shows which selector is missing.

## Reading the rendered output

What you see depends on the recipe's `renderer` field:

| Renderer | What you see |
|----------|-------------|
| `trace-3d` / `tresjs` | Interactive 3D path with a colour-mapped fourth dimension (e.g. force magnitude). Use the playback controls to scrub through time. |
| `table` | A tabular view of the channel values at each timestamp. |
| `urdf` | A robot model loaded from a URDF file reference, driven by joint data from the timeseries channels. |
| `thermography` | A thermal overlay on an AFP trajectory, driven by temperature channel data. |

### Playback controls (3D renderer)

| Control | What it does |
|---------|-------------|
| ▶ / ⏸ | Play / pause the time animation |
| Scrubber | Jump to any point in the recording |
| Speed | Choose 0.1× to 5× (or enter a custom multiplier) |
| Reset | Return to the start |

## Troubleshooting

| Message | Fix |
|---------|-----|
| *No renderer registered for this kind* | The plugin that implements this renderer isn't installed. Ask your operator to install the matching renderer plugin. |
| *Template not found* | The template appId you entered doesn't exist on this instance, or you don't have read access to it. |
| *422 — wrong template kind* | The template you selected is a `MAPPING_RECIPE` or form template, not a `VIEW_RECIPE`. Pick a different template. |
| *Channel not found for role `<role>`* | The channel selector the recipe specifies (`measurement.device.location.symbolicName.field`) doesn't match any channel in the DataObject's timeseries containers. Check the channel names in the timeseries container. |
| *403 — permission denied* | You need read access on the DataObject and on the template. |

## See also

- [View recipes — reference](../reference/view-recipes.md) — how view recipe templates
  work, the channel role schema, and the render SPI
- [Build a template](./build-a-template.md) — create your own VIEW_RECIPE template
- [Tools overview](../reference/tools.md) — all Tools cluster surfaces and their endpoints
