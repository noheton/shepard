---
title: A view recipe shows declared bindings only
description: Why a VIEW_RECIPE template's channel bindings come back with status=DECLARED
permalink: /help/plugin-renderer-not-loaded/
layout: default
audience: user
---
# A view recipe shows "declared" bindings only

When you render a `VIEW_RECIPE` template via the dataset detail page or the
`POST /v2/shapes/render` API, each channel binding comes back with a `status`
field. If every binding reads `"DECLARED"` (instead of `"OK"` / `"MISSING"` /
`"UNIT_MISMATCH"`), the plugin that handles the recipe's shape is **not
installed on this Shepard instance**.

## What "DECLARED" means

The Shepard core ships a fallback projection path that returns the template's
channel-binding declarations as-is — no channel resolution against the focus
DataObject is attempted. Every binding then reads:

```json
{
  "role": "x",
  "channelSelector": "...",
  "status": "DECLARED",
  "resolved": null
}
```

This is by design: it lets a client (frontend, MCP agent) inspect the recipe
even when the plugin family that resolves it (e.g. `shepard-plugin-vis-trace3d`,
`shepard-plugin-vis-cad`) isn't installed. The wire shape is identical to the
"plugin installed and active" case — only the `status` codes differ.

## How to get live bindings

Ask the operator to install the plugin that handles the recipe's
`viewRecipeShape` IRI. The shape IRI appears in the recipe's body, e.g.

```json
"viewRecipeShape": "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape"
```

That IRI is owned by `shepard-plugin-vis-trace3d`. Once an operator drops the
plugin JAR into `backend/plugins/` and enables it
(`shepard.plugins.vis-trace3d.enabled=true`), the next render call returns
`status=OK` / `MISSING` / `UNIT_MISMATCH` per binding — the renderer actually
resolves the channels.

The operator can list installed plugins via `GET /v2/admin/plugins` or
`shepard-admin plugins status`.

## What if the recipe declares no shape IRI?

A recipe body without a `viewRecipeShape` field always falls through to the
in-tree `DECLARED` projection — there's no dispatch key. Authors of new
VIEW_RECIPE templates should always include the `viewRecipeShape` field; see
the [ViewRecipeRenderer SPI reference](/reference/view-recipe-spi/) for the
field's contract.
