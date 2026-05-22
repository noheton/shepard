---
layout: default
title: View recipes (reference)
permalink: /reference/view-recipes/
---

# View recipes — `TemplateKind.VIEW_RECIPE`

A **view recipe** is a reusable, shape-driven projection. It
describes how to take a focus DataObject's payload channels and
project them onto a renderer-facing view — a 3D path, a heatmap,
a scatter plot, a layered chart — without binding the recipe to a
specific renderer or a specific dataset.

The recipe is **renderer-agnostic by design**: the same recipe
drives a TresJS / Three.js component today, an Isaac Sim USD scene
tomorrow, and a static PNG export through Plotly the day after.
The wire payload the consumer receives only knows about
**roles** (`x`, `y`, `z`, `color`, …), not pixels.

View recipes were added in TPL2a alongside a sibling
**process recipe** (`PROCESS_RECIPE` — manufacturing / process-chain
blueprints). Both are first-class `TemplateKind` values; both go
through `POST /v2/templates` and the standard Template CRUD
surface; both validate against a SHACL meta-shape.

## The two new TemplateKinds

| Kind | What it represents | Allowed top-level keys |
|---|---|---|
| `VIEW_RECIPE` | A shape-driven projection consumed by the upcoming `POST /v2/shapes/render` endpoint (#157). First concrete consumer: Trace3D (#142). | `view`, `shape`, or `renderer` |
| `PROCESS_RECIPE` | A process-chain blueprint — the MFFD AFP → bridge welding → frame welding → cleats chain is the worked example. | `process`, `steps`, or `stages` |

Both extend the existing `ShepardTemplate` entity. The
`TemplateBodyValidator` allow-list enforces that a `VIEW_RECIPE`
body carries one of the three view-keyed shapes, and a
`PROCESS_RECIPE` body carries one of the three process-keyed
shapes. Anything else is rejected at the validation gate.

## The meta-shape

The SHACL meta-shape lives at
`backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl`.
Every concrete view recipe (Trace3D, scatter, heatmap, …) ships
its own shape that `sh:node`s this meta-shape — so the structural
contract is shared and the renderer-specific fields layer on top.

The meta-shape declares two classes:

```turtle
shepard-ui:ViewRecipe a owl:Class .
shepard-ui:ChannelBinding a owl:Class .
```

### `ViewRecipe`

A recipe carries an identity (`dcterms:title`, optional
`dcterms:description`), a **renderer hint**, and one or more
**channel bindings**.

```turtle
shepard-ui:ViewRecipeShape a sh:NodeShape ;
    sh:targetClass shepard-ui:ViewRecipe ;

    sh:property [ sh:path dcterms:title ; sh:datatype xsd:string ;
                  sh:minCount 1 ; sh:name "View title" ; sh:order 1 ] ;
    sh:property [ sh:path dcterms:description ; sh:datatype xsd:string ;
                  sh:name "Description" ; sh:order 2 ] ;
    sh:property [ sh:path shepard-ui:renderer ; sh:datatype xsd:string ;
                  sh:minCount 1 ;
                  sh:in ( "tresjs" "echarts" "echarts-gl" "plotly"
                          "babylon" "isaac-usd" "custom" ) ;
                  sh:name "Renderer hint" ; sh:order 3 ] ;
    sh:property [ sh:path shepard-ui:hasChannelBinding ;
                  sh:node shepard-ui:ChannelBindingShape ;
                  sh:minCount 1 ;
                  sh:name "Channel bindings" ; sh:order 10 ] .
```

The **renderer hint** is informational. It tells the frontend
which component to mount when the recipe is consumed; it does
**not** change the wire payload. Swap the hint from `tresjs` to
`isaac-usd` and the same recipe drives a different renderer with
no code change in core.

### `ChannelBinding`

One binding per role the view consumes. Each binding declares the
**role name** (`x`, `y`, `z`, `time`, `color`, `size`, ...
conventions follow the renderer family), the **channel selector**
(the address of the channel on the focus DataObject's TS bag), an
optional **expected unit** (QUDT IRI), and a **required flag**.

```turtle
shepard-ui:ChannelBindingShape a sh:NodeShape ;
    sh:targetClass shepard-ui:ChannelBinding ;

    sh:property [ sh:path shepard-ui:role ; sh:datatype xsd:string ;
                  sh:minCount 1 ; sh:name "Role" ; sh:order 1 ] ;
    sh:property [ sh:path shepard-ui:channelSelector ; sh:datatype xsd:string ;
                  sh:minCount 1 ; sh:name "Channel selector" ; sh:order 2 ] ;
    sh:property [ sh:path qudt:unit ; sh:nodeKind sh:IRI ;
                  sh:name "Expected unit" ; sh:order 3 ] ;
    sh:property [ sh:path shepard-ui:required ; sh:datatype xsd:boolean ;
                  sh:name "Required" ; sh:order 4 ] .
```

The selector is today the **5-tuple JSON shape** —
`{measurement, device, location, symbolicName, field}` — because
the TS substrate is keyed on that 5-tuple. Once the TS-ID
migration ships its frontend-visible slice
(`aidocs/platform/87-timeseries-appid-migration.md`), the
selector becomes a single `shepardId` UUID. The recipe schema is
**forward-compatible**: a selector that's already a JSON object
stays valid, and a selector that's a bare UUID will be too.

If a `qudt:unit` is set, the future `POST /v2/shapes/render`
endpoint asserts unit-compatibility against the resolved channel
and surfaces a warning when units mismatch.

If `required = true` and the channel is missing on the focus DO,
the renderer refuses to render. If `required = false`, the
renderer **degrades gracefully** — drops the dimension and renders
what's available.

## Renderer enum

The seven allowed `renderer` values:

| Value | Family | Use |
|---|---|---|
| `tresjs` | Three.js Vue wrapper | 3D scenes in the shepard frontend. The default for Trace3D. |
| `echarts` | Apache ECharts | 2D charts. Today's inline timeseries chart already uses ECharts. |
| `echarts-gl` | ECharts GL | 3D charts via WebGL — alternative to TresJS for simpler 3D plots. |
| `plotly` | Plotly | Static + interactive plots, good for notebook integration. |
| `babylon` | Babylon.js | Industrial 3D — heavier than TresJS, used for digital-twin scenes. |
| `isaac-usd` | NVIDIA Isaac Sim | OpenUSD scene description for high-fidelity industrial simulation. |
| `custom` | (any) | Plugin escape hatch — the consumer plugin owns interpretation. |

Adding a renderer is an additive change to the `sh:in` enum and
the `TemplateBodyValidator` allow-list — no breaking change to
already-stored recipes.

## Trace3D — the first concrete consumer

The MFFD AFP TCP **thermal trail** is the acceptance test for
view recipes: the AFP robot's TCP position (X/Y/Z) over time,
coloured by TCP temperature, rendered as a 3D path the operator
can pan / zoom / scrub.

The Trace3D recipe binds four roles:

| Role | Selector | Expected unit |
|---|---|---|
| `x` | `{... symbolicName: "tcp_x"}` | `qudt:Metre` |
| `y` | `{... symbolicName: "tcp_y"}` | `qudt:Metre` |
| `z` | `{... symbolicName: "tcp_z"}` | `qudt:Metre` |
| `color` | `{... symbolicName: "tcp_temperature"}` | `qudt:DegreeCelsius` |

The recipe ships in the `shepard-plugin-trace3d` module (queued
behind PM1f's general-availability for plugin sidecar declaration).
The plugin's shape file `plugins/trace3d/shapes/trace3d-recipe.shacl.ttl`
extends the meta-shape with renderer-specific bindings.

## Lifecycle

View recipes go through the standard Template CRUD:

```http
POST   /v2/templates                          # create
GET    /v2/templates?kind=VIEW_RECIPE         # list by kind
GET    /v2/templates/{appId}                  # read
PATCH  /v2/templates/{appId}                  # update (copy-on-write)
DELETE /v2/templates/{appId}                  # retire
```

A recipe is **admin-managed** today — the same `admin` role gate
that protects every other Template kind. The casual researcher
**consumes** a recipe via the renderer; an admin **publishes** the
recipes the renderer can pick from.

## Validation

Two layers:

1. **`TemplateBodyValidator`** — fast, in-process check that the
   recipe body carries one of the three allowed top-level keys
   for its kind. Lives in `de.dlr.shepard.template.services`.
2. **`POST /v2/shapes/validate`** — SHACL-level structural
   validation against the meta-shape. Lives in
   `de.dlr.shepard.shacl.services.JenaShaclValidator` (shipped
   in SHACL-1).

A recipe that passes (1) but fails (2) is structurally well-formed
JSON but doesn't satisfy the SHACL contract — typically a
missing-required field, a wrong datatype, or an off-vocabulary
renderer value.

## Status

- **`TemplateKind` enum extension** — shipped (TPL2a).
- **Meta-shape** at
  `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl`
  — shipped.
- **`TemplateBodyValidator` allow-list update** — shipped, with
  two new unit tests (`processRecipeAcceptsAnyOfThreeKeys`,
  `viewRecipeAcceptsAnyOfThreeKeys`).
- **`POST /v2/shapes/render`** — queued (#157).
- **`shepard-plugin-trace3d`** with the first concrete recipe —
  queued (#142). The recipe is the acceptance test for the
  meta-shape and the plumbing on either side of it.

## See also

- `aidocs/semantics/95-shapes-as-templates-and-views.md §1, §2` —
  the design doc for shapes-as-templates and shapes-as-views.
- `aidocs/semantics/98-substrate-split-shacl-changeover.md §1.1` —
  the TemplateKind extension and the SHACL changeover roadmap.
- `aidocs/agent-findings/trace3d-spike.md` — the spike that picked
  TresJS for the first renderer.
- [Plugins reference]({{ '/reference/plugins' | relative_url }}) —
  the broader plugin SPI under which renderer plugins ship.
- `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl`
  — the meta-shape itself.
