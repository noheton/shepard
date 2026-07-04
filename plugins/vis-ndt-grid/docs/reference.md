---
stage: concept
last-stage-change: 2026-06-09
---

# vis-ndt-grid — reference

NDT thermography tile-grid mosaic renderer for the MFFD upper-fuselage AFP process.

## Plugin identity

| Field | Value |
|---|---|
| Plugin ID | `vis-ndt-grid` |
| Version | `1.0.0-SNAPSHOT` |
| Shepard compatibility | `>=6.0.0-SNAPSHOT,<7` |
| Licence | Apache-2.0 |
| Backlog row | `MFFD-RENDER-NDT-GRID` in `aidocs/16-dispatcher-backlog.md` |

## What this plugin does

Renders a Shepard Collection of `mffd:ndt-otvis-measurement` DataObjects as a
2D S×M×L×F tile-grid mosaic:

- **Rows / columns** — any two of the four SMFL axes (Section, Module, Layer, Frame)
- **Cell colour** — mean ΔT from the OTvis thermography sequence (quantitative) or
  pass/fail/review quality disposition from a semantic annotation (AS9100 §8.7 traceability)
- **Canonical use case** — "Show me layer L18 of the MFFD Q1 upper-fuselage AFP track"
  → set `layerFilter=L18`, `rowDimension=section`, `columnDimension=module`

## Slice status

| Slice | Contents | Status |
|---|---|---|
| 1 (this module) | `VisNdtGridPluginManifest` + `NdtGridShape` SHACL + `NdtGridCanvas.vue` placeholder | ✓ shipped |
| 2 | `NdtGridTransformExecutor` — resolves S×M×L×F grid from the Collection | ⏳ queued |
| 3 | `NdtGridView.vue` — Canvas 2D mosaic renderer + Vitest coverage | ⏳ queued |

## Shape: NdtGridShape

**Shape IRI:** `http://semantics.dlr.de/shepard/transform#NdtGridShape`
**Template kind:** `MAPPING_RECIPE`
**SHACL resource:** `/shapes/ndt-grid.shacl.ttl` (classpath)

### Properties

| Property | Type | Required | Default | Notes |
|---|---|---|---|---|
| `ndtgrid:collectionAppId` | `xsd:string` | ✓ | — | appId of the NDT OTvis Collection |
| `ndtgrid:rowDimension` | `xsd:string` (enum) | — | `layer` | Row axis: `section`, `module`, `layer`, `frame` |
| `ndtgrid:columnDimension` | `xsd:string` (enum) | — | `section-module` | Column axis: `section`, `module`, `layer`, `frame`, `section-module` |
| `ndtgrid:colourMode` | `xsd:string` (enum) | — | `mean-delta-t` | `mean-delta-t` or `pass-fail` |
| `ndtgrid:colourMap` | `xsd:string` (enum) | — | `hot` | `hot`, `plasma`, `inferno`, `gray`, `rdbu`, `viridis` |
| `ndtgrid:qualityAnnotationPredicate` | `xsd:string` | — | `urn:shepard:mffd:ndt-quality` | Predicate for pass-fail quality value |
| `ndtgrid:layerFilter` | `xsd:string` | — | (all layers) | e.g. `L18` |
| `ndtgrid:sectionFilter` | `xsd:string` | — | (all sections) | e.g. `S4` |
| `ndtgrid:moduleFilter` | `xsd:string` | — | (all modules) | e.g. `M13` |

### Worked MAPPING_RECIPE template body

```json
{
  "templateKind": "MAPPING_RECIPE",
  "mappingRecipeShape": "http://semantics.dlr.de/shepard/transform#NdtGridShape",
  "collectionAppId": "019e8000-0000-7000-8000-000000000042",
  "rowDimension": "section",
  "columnDimension": "module",
  "colourMode": "mean-delta-t",
  "colourMap": "hot",
  "layerFilter": "L18"
}
```

### Materialize endpoint (slice 2)

```
POST /v2/mappings/{templateAppId}/materialize
Content-Type: application/json

{ "inputReferenceAppIds": {} }
```

Response (slice 2 view envelope — not yet available in slice 1):
```json
{
  "outputKind": "VIEW",
  "viewModel": {
    "kind": "ndt-grid",
    "rowDimension": "section",
    "columnDimension": "module",
    "colourMode": "mean-delta-t",
    "colourMap": "hot",
    "rows": ["S4", "S5", "S6"],
    "columns": ["M13", "M14", "M15"],
    "cells": [
      { "row": "S4", "col": "M13", "value": 2.37, "status": "RESOLVED" },
      { "row": "S4", "col": "M14", "value": 1.89, "status": "RESOLVED" }
    ]
  }
}
```

## Frontend component

`frontend/components/shapes/NdtGridCanvas.vue` — placeholder stub in slice 1.
Slice 3 replaces it with the real Canvas 2D mosaic renderer.

## Related

- `MffdNdtOtvisMeasurementKind` — the DATAOBJECT_RECIPE template that seeds the
  annotation predicates (`urn:shepard:mffd:ndt-{section,module,layer,frame}`) the
  executor queries.
- `MffdApprovalGateKind` — the DATAOBJECT_RECIPE for quality-gate DataObjects that
  the pass-fail colour mode reads from.
- `plugins/vis-trace3d/` — the Trace3D VIEW_RECIPE sibling (SceneGraphPlay
  MAPPING_RECIPE pattern that NDT-grid mirrors).
