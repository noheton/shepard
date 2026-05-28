---
layout: default
title: ViewRecipeRenderer SPI (reference)
permalink: /reference/view-recipe-spi/
audience: plugin-author
---
# `ViewRecipeRenderer` SPI reference

The `ViewRecipeRenderer` SPI is the **cross-cutting backend extension point**
that the VIS-* plugin family (`shepard-plugin-vis-trace3d`,
`shepard-plugin-vis-cad`, `shepard-plugin-vis-fem`,
`shepard-plugin-vis-volume`) plugs into. The in-tree `POST /v2/shapes/render`
endpoint reads a `VIEW_RECIPE` template's `viewRecipeShape` IRI and
dispatches the projection to whichever renderer registers for that IRI;
when no plugin claims it, the endpoint falls back to the in-tree
`DECLARED`-status projection it shipped in TPL2b.

This page is for **plugin authors writing a new renderer**. Operators don't
configure this SPI directly — installing a plugin that ships a renderer is
enough.

## Where the pieces live

- `de.dlr.shepard.spi.view.ViewRecipeRenderer` — the interface every renderer
  implements.
- `de.dlr.shepard.spi.view.ViewRecipeRendererRegistry` — the
  `@ApplicationScoped` registry that ServiceLoader-loads renderers at
  startup and serves `resolve(shapeIri)` lookups.
- `de.dlr.shepard.spi.view.RenderRequest` / `RenderResponse` —
  internal SPI request/response shapes (POJO records).
- `de.dlr.shepard.spi.view.RenderException` — typed exception for the 422
  branch of the dispatcher.
- `de.dlr.shepard.v2.shapes.resources.ShapesRenderRest` — the
  `POST /v2/shapes/render` endpoint that dispatches.

## The dispatch key

Each `VIEW_RECIPE` template body declares a `viewRecipeShape` field naming
the SHACL `sh:NodeShape` it targets:

```json
{
  "viewRecipeShape": "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape",
  "renderer": "tresjs",
  "channelBindings": [
    {"role":"x","channelSelector":"...","required":true},
    {"role":"y","channelSelector":"...","required":true},
    {"role":"z","channelSelector":"...","required":true}
  ]
}
```

The `renderer` field (e.g. `"tresjs"`) is a **frontend-component hint** — a
Vue component selector. The `viewRecipeShape` IRI is the **backend-resolver
dispatch key**. They serve different layers and must not be conflated:
multiple shapes can share a renderer hint (every Three.js view declares
`renderer = "tresjs"`); only the shape IRI uniquely names the backend
resolver.

## Writing a renderer

A renderer is a plain POJO (NOT a CDI bean) registered through
`META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer` in the plugin
JAR. This matches the established `PayloadKind` (`de.dlr.shepard.spi.payload`)
shape and sidesteps the Quarkus `@Inject ServiceLoader` dance.

```java
package de.dlr.shepard.plugins.vistrace3d;

import de.dlr.shepard.spi.view.*;
import java.util.Set;

public final class Trace3DRenderer implements ViewRecipeRenderer {

  private static final String SHAPE_IRI =
    "http://semantics.dlr.de/shepard-ui/trace3d#Trace3DViewShape";

  @Override
  public Set<String> supportedShapeIris() {
    return Set.of(SHAPE_IRI);
  }

  @Override
  public RenderResponse render(RenderRequest req) {
    // Parse template body, resolve channels on the focus DataObject,
    // populate per-binding status codes (OK / MISSING / UNIT_MISMATCH).
    // ...
    return new RenderResponse(
      req.templateAppId(),
      req.focusShepardId(),
      "tresjs",
      List.of(/* ChannelBindingProjection entries */)
    );
  }

  @Override
  public String name() {
    return "Trace3DRenderer";
  }
}
```

Then register it in `src/main/resources/META-INF/services/de.dlr.shepard.spi.view.ViewRecipeRenderer`:

```
de.dlr.shepard.plugins.vistrace3d.Trace3DRenderer
```

## Status vocabulary

A renderer populates per-binding `status` codes on the
`ChannelBindingProjection` entries it returns. The vocabulary is:

| Code | When |
| --- | --- |
| `OK` | Channel found on the focus DataObject; `resolved.channelRef` is set. |
| `MISSING` | Channel not found. A binding with `required=true` + `MISSING` means the renderer cannot operate. |
| `UNIT_MISMATCH` | Channel found but its unit IRI differs from the binding's declared `unit`. |
| `DECLARED` | Reserved for the in-tree fallback path — a renderer should NOT emit this. |

The dispatcher copies these codes through to the wire response unchanged. The
frontend renderer (Vue component) branches on them.

## Failure handling

Throw `RenderException(code, message)` for typed renderer failures:

```java
if (!root.has("xChannel")) {
  throw new RenderException("render.body.invalid", "missing required `xChannel`");
}
```

The dispatcher translates this into HTTP 422 with an RFC 7807-shaped body
carrying `code` + `error` + `renderer`. Unknown codes fall through to HTTP 500
with `type=render.internal-error`.

Per-binding resolution failures (channel missing, unit mismatch) should be
surfaced as `status` codes on the binding, NOT as exceptions — the dispatcher
returns 200 with the per-binding status so the frontend can degrade gracefully.

## Failure modes the registry handles

- **Duplicate shape IRI across renderers** — fail-fast at startup with a
  clear `IllegalStateException` naming both registrants. Two plugins
  claiming the same shape IRI is a build-time packaging defect; refusing
  to start is the actionable signal.
- **Renderer's `supportedShapeIris()` throws** — registry logs at WARN
  and skips the renderer; other renderers continue to register.
- **Renderer returns null/blank IRIs** — those entries are silently
  skipped (with a WARN); the renderer's other claims still register.
- **No renderer registered for a shape IRI** — dispatcher falls back to
  the in-tree `DECLARED`-status projection. This is what makes the
  template wire shape work for clients whose plugin isn't installed.

## Cross-references

- `aidocs/16-dispatcher-backlog.md` — VIS-S1 row (this SPI) +
  VIS-T1 / VIS-C1 / VIS-F1 / VIS-V1 (downstream consumers)
- `aidocs/semantics/98-shapes-views-and-process-model.md §1.2` — the
  `POST /v2/shapes/render` endpoint contract
- `aidocs/agent-findings/trace3d-spike.md §3` — the frame envelope shape
  that `RenderResponse` mirrors
- `aidocs/34-upstream-upgrade-path.md` — operator-facing change ledger
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — feature-matrix row
- `de.dlr.shepard.spi.payload.PayloadKind` — the P-series ServiceLoader
  SPI shape this builds on

## What plugins must do

Plugin authors writing a new VIS-* renderer must:

1. Declare the SHACL shape file (`src/main/resources/shapes/<view>.shacl.ttl`)
   extending `shepard-ui:ViewRecipeShape` per
   `aidocs/semantics/98 §1.1` + `aidocs/agent-findings/trace3d-spike.md §2`.
2. Implement `ViewRecipeRenderer` and register it via `META-INF/services/`.
3. Ship plugin docs (`plugins/<id>/docs/reference.md`, `quickstart.md`,
   `install.md`) per the CLAUDE.md "plugins ship their own documentation"
   rule.
