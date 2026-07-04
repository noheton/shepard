---
title: Run a mapping recipe (materialize a mapping)
description: Use the Materialize mapping tool to run a saved MAPPING_RECIPE template against your data and produce a derived output — for example, a joint trajectory from a KRL program, or a 3D scene graph from a URDF.
permalink: /help/materialize-a-mapping/
layout: default
audience: user
---

# Run a mapping recipe (materialize a mapping)

A **mapping recipe** is a reusable template that knows how to turn one or more existing
data references into something new — a joint trajectory, a 3D view, a computed result.
You pick the recipe, bind your data files to it, and Shepard runs the transform.

> **Tip — shortcut buttons.** Many workflows have a dedicated button on the data
> reference's detail page (for example, **Interpret as joint trajectory** on a `.src`
> KRL file). Those buttons open a guided dialog and use this same engine under the
> hood. Use the Tools page below when you want to run a recipe directly, or when
> there is no dedicated button for your recipe yet.

## Open the tool

1. Click **Tools** in the top navigation bar.
2. Choose **Materialize mapping** from the tools list.

The tool opens immediately — no data loaded yet.

## Arriving from a DataObject

If you clicked **Materialize** on a DataObject's action menu, the tool opens with
the DataObject's references already suggested in the input pickers (shown as a blue
chip at the top of the Inputs card). You still choose which references to bind.

## Fill in the inputs

### 1. Pick a recipe template

Click the **MAPPING_RECIPE template** field and start typing. The autocomplete
shows every `MAPPING_RECIPE` template available on your instance. Pick the one
you want to run.

Not sure which template to choose? Ask the person who set up the template, or
see the recipe description in the template editor (**Admin → Templates**).

### 2. Bind your references

Each recipe expects one or more named **input references** — the data it will
read. The Inputs card shows a row for each binding:

| Column | What to enter |
|--------|--------------|
| **Binding role** | The name of the input slot the recipe expects (e.g. `srcFileAppId`, `urdfFileAppId`). The template's description or documentation will list these. |
| **Reference** | The reference that fills that slot. Click the field and pick from the suggestions, or paste a reference appId directly. |

Click **Add binding** to add more rows. Click × to remove a row you don't need.

> **Tip.** When you arrived from a DataObject, the reference picker pre-loads that
> DataObject's references as suggestions. Select the right `.src`, `.urdf`, or other
> file from the dropdown.

### 3. Click Materialize

Click the blue **Materialize** button. The button shows a spinner while the recipe
runs (this can take a few seconds for compute-intensive transforms like KRL interpret
or scene-graph compilation).

## Reading the result

When the recipe finishes, a **Derived output** card appears below the inputs.

### New saved reference (most common)

If the output kind shows **REFERENCE**, the recipe produced a new data reference
in your DataObject — for example, a `TimeseriesReference` containing a joint
trajectory. You'll see the reference's appId. Go back to your DataObject to find
the new reference listed in its references panel.

### Live view

If the output kind shows **VIEW**, the recipe produced an ephemeral rendered view
(for example, a 3D scene graph). Shepard shows the view data below. The view is
not permanently saved — re-run the recipe to produce it again.

## Troubleshooting

| Message | Fix |
|---------|-----|
| *Enter the MAPPING_RECIPE template appId first* | Select a recipe in the template picker before clicking Materialize. |
| *404 — no executor registered* | The plugin that implements this recipe isn't installed on this Shepard instance. Ask your operator to install it. |
| *422 — missing input* | One or more required binding roles weren't filled in. Check which roles the recipe expects and add the missing rows. |
| *422 — not a MAPPING_RECIPE template* | The template you selected is a form template or view recipe, not a mapping recipe. Pick a different one. |
| *403 — permission denied* | You need write access on the DataObject whose references you're binding. |

## See also

- [Interpret a KRL program as a joint trajectory](./run-krl-preview.md) — the
  shortcut button that uses this engine for KRL programs
- [Materialize mapping — reference](../reference/materialize-mapping.md) — API
  details, executor SPI, provenance recording
- [Build a template](./build-a-template.md) — create your own MAPPING_RECIPE template
