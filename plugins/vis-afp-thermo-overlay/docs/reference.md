---
stage: concept
last-stage-change: 2026-06-10
---

# vis-afp-thermo-overlay — reference

## Overview

`shepard-plugin-vis-afp-thermo-overlay` renders a synced dual-pane view for MFFD AFP
layup quality review. It accepts an AFP process DataObject (carrying TCP timeseries) and
an OTvis NDT DataObject for the same tile, and produces:

- **Left pane:** Trace3D 3D trajectory of the AFP robot head during layup, vertex-coloured
  by a selected TCP channel (temperature / consolidation-force / head-speed / nip-pressure).
- **Right pane:** OTvis thermography heatmap from the post-layup NDT inspection of the same
  (Section, Module, Layer) tile.

The canonical use case: "Did the consolidation-force anomaly at ply 18 of tile S4·M13
correlate with the NDT-flagged delamination zone?"

## Status (slice 1)

| Slice | Status | Description |
|---|---|---|
| 1 — shape + manifest | ✅ shipped | `AfpThermoOverlayShape` SHACL + `VisAfpThermoOverlayPluginManifest` |
| 2 — executor | ⏳ queued | `AfpThermoOverlayTransformExecutor` — resolves AFP TCP + NDT data, returns VIEW envelope |
| 3 — renderer | ⏳ queued | `AfpThermoOverlayCanvas.vue` — synced dual-pane Vue renderer, Vitest + Playwright |

## SHACL shape

**Shape IRI:** `http://semantics.dlr.de/shepard/transform#AfpThermoOverlayShape`

**Template kind:** `MAPPING_RECIPE`

### Required bindings

| Property | Datatype | Description |
|---|---|---|
| `afpthermo:afpDataObjectAppId` | `xsd:string` | appId of the AFP course DataObject (must carry TimeseriesReference with TCP channels) |
| `afpthermo:ndtDataObjectAppId` | `xsd:string` | appId of the OTvis NDT DataObject for the same tile |

### Optional bindings

| Property | Datatype | sh:in | Default | Description |
|---|---|---|---|---|
| `afpthermo:section` | `xsd:string` | — | from AFP annotation | MFFD section label (e.g. `S4`) |
| `afpthermo:module` | `xsd:string` | — | from AFP annotation | MFFD module label (e.g. `M13`) |
| `afpthermo:plyNumber` | `xsd:integer` | — | all plies | Clip Trace3D trajectory to this ply |
| `afpthermo:courseNumber` | `xsd:integer` | — | all courses | Clip to this course within the ply |
| `afpthermo:tcpChannel` | `xsd:string` | `tcp-temperature`, `consolidation-force`, `head-speed`, `nip-pressure` | `tcp-temperature` | TCP channel mapped to vertex colour |
| `afpthermo:colourMap` | `xsd:string` | `hot`, `inferno`, `plasma`, `viridis`, `rdbu` | `hot` | Colour map for both panes |
| `afpthermo:syncMode` | `xsd:string` | `side-by-side`, `overlay`, `split` | `side-by-side` | View layout |
| `afpthermo:timeWindowStartUs` | `xsd:long` | — | full range | Start of AFP timeseries clip (µs epoch) |
| `afpthermo:timeWindowEndUs` | `xsd:long` | — | full range | End of AFP timeseries clip (µs epoch) |

## REST endpoints

### Validate a recipe body

```http
POST /v2/shapes/validate
Content-Type: application/json

{
  "dataTurtle": "<MAPPING_RECIPE node targeting AfpThermoOverlayShape>",
  "shapeTurtle": "<inline shape TTL or null — uses classpath resource if null>"
}
```

### Materialize (slice 2 required)

```http
POST /v2/mappings/{templateAppId}/materialize
Content-Type: application/json

{ "inputReferenceAppIds": {} }
```

**Response (slice 2):**

```json
{
  "outputKind": "VIEW",
  "viewModel": {
    "kind": "afp-thermo-overlay",
    "syncMode": "side-by-side",
    "tcpChannel": "tcp-temperature",
    "colourMap": "hot",
    "colourScaleMin": 28.4,
    "colourScaleMax": 94.7,
    "trace3d": {
      "frames": [ { "x": 1.23, "y": 0.45, "z": 0.12, "colour": 0.63 } ]
    },
    "ndtHeatmap": {
      "width": 512, "height": 512,
      "pixelData": "<base64-encoded float32 array>"
    }
  }
}
```

## Neo4j entities

No new Neo4j entities in slice 1. The executor (slice 2) reads from:
- AFP DataObject → `TimeseriesReference` → TCP channels in TimescaleDB
- NDT DataObject → `FileReference` → OTvis `.otvis` file in Garage/GridFS

## Admin config

No runtime-configurable keys in slice 1.

Slice 2 may add a `:VisAfpThermoOverlayConfig` singleton and
`GET/PATCH /v2/admin/vis-afp-thermo-overlay/config` (following the A3b/N1c2/UH1a pattern)
for cache TTL and default colour-map settings.

## TCP channels

| Channel name | Unit | Description |
|---|---|---|
| `tcp-temperature` | °C | Tool-center-point temperature during AFP head pass |
| `consolidation-force` | N | Normal force of the consolidation roller on the tow |
| `head-speed` | m/min | Linear speed of the AFP head |
| `nip-pressure` | bar | Nip-roll hydraulic pressure |
