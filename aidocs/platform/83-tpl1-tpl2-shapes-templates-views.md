---
stage: feature-defined
last-stage-change: 2026-05-26
---
# TPL1+TPL2: Shapes as Templates + Views — M1 milestone tracker

This document is the **M1 milestone implementation tracker** for the
Shapes-as-Templates (TPL1) and Shapes-as-Views (TPL2) work streams.
It is a tracker and plan, not a design SSOT. Canonical design lives in:

- `aidocs/semantics/95-shacl-templates-and-individuals.md` — SHACL template
  and individual design
- `aidocs/semantics/98-shapes-views-and-process-model.md` — VIEW_RECIPE
  render pipeline, channel-binding wire format, frontend dispatch pattern

---

## What shipped (M0 — foundation)

| Item | Where |
|---|---|
| `ShepardTemplate` Neo4j entity with `templateKind` field | `de.dlr.shepard.template.entities.ShepardTemplate` |
| `TemplateBodyValidator` — validates template body JSON by kind | `de.dlr.shepard.template.services.TemplateBodyValidator` |
| `VIEW_RECIPE` added to the templateKind allow-list (TPL2a) | `TemplateBodyValidator` + `ShepardTemplate` |
| Meta-shape: `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl` | Defines `shepard-ui:ViewRecipe` + `shepard-ui:ChannelBinding` |
| `POST /v2/shapes/render` — stateless VIEW_RECIPE projection (TPL2b) | `ShapesRenderRest` — returns `{renderer, channelBindings[]}` |
| `ShapesRenderResponseIO` with nested `ChannelBindingProjectionIO` | Wire record; status vocabulary: DECLARED / OK / MISSING / UNIT_MISMATCH |
| `Trace3DView.vue` — VIEW_RECIPE renderer for Trace3D (task #142) | `frontend/components/container/timeseries/Trace3DView.vue` |
| `Trace3DCanvas.vue` — Three.js scene (task #142) | `frontend/components/shapes/Trace3DCanvas.vue` |
| `shapes/render.vue` — playground page with bulk channel fetch | `frontend/pages/shapes/render.vue` |
| `predicate_vocabulary` Postgres table + `GET /v2/shapes/predicates` (SHACL-PR5) | 51-predicate substrate-routing vocabulary |
| `POST /v2/shapes/validate` — Jena SHACL validator (SHACL-1) | `ShapesValidateRest` |

---

## What TPL1 adds (Shapes as Templates)

TPL1 makes `DATAOBJECT_RECIPE` and `COLLECTION_RECIPE` templates actively
useful: a researcher selects a template and creates a DataObject or Collection
whose annotations are pre-populated from the template body's `defaults` map and
whose required fields are enforced by the template's `requiredProperties[]`
SHACL shapes.

### Key new capabilities

1. **Template-driven DataObject creation** — `POST /v2/dataobjects` gains an
   optional `templateAppId` field. When present the backend looks up the
   `DATAOBJECT_RECIPE` template, extracts `defaults` from the body JSON, and
   pre-populates the DataObject's semantic annotations via
   `ProvenanceCaptureFilter`-tracked batch writes. Same pattern for
   `POST /v2/collections`.

2. **Conformance check at save time** — if the template body declares
   `requiredProperties[]`, the backend validates the DataObject's annotations
   against each property shape before returning 200. Failure returns 422 with a
   human-readable validation report (same shape as `POST /v2/shapes/validate`).

3. **Frontend template picker** — a `TemplatePickerDialog.vue` component in
   the DataObject creation flow shows available `DATAOBJECT_RECIPE` templates,
   filtered by Collection kind where applicable. Selecting a template pre-fills
   the annotation form and marks required fields.

4. **`GET /v2/templates?kind=dataobject`** surface extended with
   `?conformsTo=<shapeIRI>` filter — lets the frontend show only templates
   compatible with the current Collection's declared shape.

### Backend body structure expected (DATAOBJECT_RECIPE)

```json
{
  "kind": "DATAOBJECT_RECIPE",
  "defaults": {
    "dcterms:subject":    "AFP layup",
    "m4i:method":         "http://example.org/afp-layup",
    "shepard:qaStatus":   "PENDING"
  },
  "requiredProperties": [
    {
      "path":     "dcterms:subject",
      "datatype": "xsd:string",
      "minCount": 1
    }
  ]
}
```

### Design SSOT
`aidocs/semantics/95-shacl-templates-and-individuals.md §Part 1` (template
definition contract) and `§Part 6` (writable layer — how defaults are applied).

---

## What TPL2 adds (Shapes as Views)

TPL2 closes the loop between a VIEW_RECIPE template and an interactive frontend
renderer. M0 shipped the backend endpoint and the Trace3D renderer component.
M1 adds the **frontend dispatch layer** that routes the `renderer` hint from
`POST /v2/shapes/render` to the correct Vue component.

### Key new capabilities (M1 delta over M0)

1. **Renderer dispatch in `shapes/render.vue`** — the page now branches on
   `renderer.value` after `POST /v2/shapes/render` returns:
   - `"trace-3d"` or `"tresjs"` → `<Trace3DView>` (flat-array adapter over
     `Trace3DCanvas`)
   - `"table"` → `<BindingTableRenderer>` (inline `<v-table>` of resolved
     channel values — useful for scalar/status templates)
   - anything else → `<PlaceholderImplStatus>` showing the unsupported renderer
     hint and pointing to the backlog row

2. **`Trace3DView` wired as the canonical `trace-3d` renderer** — `shapes/render.vue`
   passes the already-assembled flat arrays (`xData[]`, `yData[]`, `zData[]`,
   `valueData[]`) to `Trace3DView` rather than `Trace3DCanvas` directly,
   activating the color-bar legend, color-scheme prop, and `valueLabel` display.

3. **MFFD demonstrator shape** — `examples/mffd-showcase/shapes.py` seeds a
   concrete VIEW_RECIPE template using MFFD LBR iiwa force-torque channels.
   Serves as the acceptance test for the full TPL2 pipeline.

### Frontend dispatch pattern

```
POST /v2/shapes/render
  └─ body.renderer
       ├─ "trace-3d" | "tresjs"  → <Trace3DView :xData :yData :zData :valueData />
       ├─ "table"                 → <BindingTableRenderer :bindings />
       └─ (unknown)               → <PlaceholderImplStatus notes="renderer: {value}" />
```

The `renderer` field is set by the template body's top-level `"renderer"` key.
The frontend is the single authoritative dispatcher; no server-side branching is
needed. Custom renderer support (SRI URL override) is a TPL2d item — the slot is
reserved in the dispatch switch as an `else if (renderer.startsWith("custom:"))` guard.

### Design SSOT
`aidocs/semantics/98-shapes-views-and-process-model.md §1.1–§2.3`.

---

## Data model

### ShepardTemplate node

```
:ShepardTemplate
  appId:        "018f…"
  name:         "MFFD LBR Force Trace"
  templateKind: "VIEW_RECIPE"          ← gated by TemplateBodyValidator
  version:      1
  body:         "{\"renderer\":\"tresjs\",\"channelBindings\":[…]}"
  retired:      false
```

`templateKind` valid values (full allow-list as of M1):
`DATAOBJECT_RECIPE`, `COLLECTION_RECIPE`, `EXPERIMENT_RECIPE`,
`AAS_SUBMODEL_TEMPLATE`, `PROCESS_RECIPE`, `VIEW_RECIPE`

### VIEW_RECIPE body structure

```json
{
  "renderer": "tresjs",
  "channelBindings": [
    {
      "role":            "x",
      "channelSelector": "{\"measurement\":\"LBR\",\"device\":\"iiwa1\",\"location\":\"ZLP\",\"symbolicName\":\"lbr_force_torque\",\"field\":\"force_x_N\"}",
      "unit":            "http://qudt.org/vocab/unit/N",
      "required":        true
    },
    {
      "role":            "y",
      "channelSelector": "{\"measurement\":\"LBR\",\"device\":\"iiwa1\",\"location\":\"ZLP\",\"symbolicName\":\"lbr_force_torque\",\"field\":\"force_y_N\"}",
      "unit":            "http://qudt.org/vocab/unit/N",
      "required":        true
    },
    {
      "role":            "z",
      "channelSelector": "{\"measurement\":\"LBR\",\"device\":\"iiwa1\",\"location\":\"ZLP\",\"symbolicName\":\"lbr_force_torque\",\"field\":\"force_z_N\"}",
      "unit":            "http://qudt.org/vocab/unit/N",
      "required":        true
    },
    {
      "role":            "color",
      "channelSelector": "{\"measurement\":\"LBR\",\"device\":\"iiwa1\",\"location\":\"ZLP\",\"symbolicName\":\"lbr_force_torque\",\"field\":\"torque_z_Nm\"}",
      "unit":            "http://qudt.org/vocab/unit/N-M",
      "required":        false
    }
  ]
}
```

Post TS-ID migration (`aidocs/platform/87`), `channelSelector` becomes a single
`shepardId` string rather than a 5-tuple JSON blob.

---

## MFFD demonstrator acceptance test

The acceptance test for the full TPL2 pipeline uses the MFFD LBR iiwa
force-torque data (synthetic, generated by `examples/mffd-showcase/data/generate.py`):

- **Channels**: `force_x_N`, `force_y_N`, `force_z_N` (x/y/z roles) +
  `torque_z_Nm` (color/scalar role)
- **Sampling rate**: 10 Hz, 120 s (1 200 points per channel)
- **Physical meaning**: The LBR iiwa performs 42 rivet/cleat insertion cycles
  (reach → insert → withdraw at 2.7 s each) during stringer attachment. The
  force-torque trajectory traces a repeating 3D pattern; colour encodes the
  torque about the tool axis.

### Pass criteria

1. `python3 examples/mffd-showcase/shapes.py --url <host> --api-key <token>`
   exits 0 and prints the created template appId.
2. `POST /v2/shapes/render` with that appId returns `renderer = "tresjs"` and
   4 channel bindings with `status = "DECLARED"`.
3. `frontend/pages/shapes/render.vue` — entering the template appId + any
   DataObject appId fetches the bindings and displays the renderer chip
   `tresjs` as `trace-3d`.
4. Entering the LBR timeseries container ID + clicking "Render 3D" fetches
   channels, assembles TracePoints, and renders the `<Trace3DView>` component
   (not `<Trace3DCanvas>` directly).
5. The colour-bar legend appears below the canvas with the `torque_z_Nm` label.

### Test file
`examples/mffd-showcase/shapes.py` — standalone script, no seed.py dependency.

---

## Implementation plan (6 PRs)

| PR | Title | Scope | Status |
|---|---|---|---|
| **M1-PR1** | `feat(shapes): renderer dispatch in shapes/render.vue` | Edit `frontend/pages/shapes/render.vue` to branch on `renderer.value`; wire `<Trace3DView>` for trace-3d, table for table, placeholder for unknown | **This PR** |
| **M1-PR2** | `feat(shapes): MFFD LBR force-trace VIEW_RECIPE demonstrator` | Create `examples/mffd-showcase/shapes.py` | **This PR** |
| **M1-PR3** (TPL1-a) | `feat(templates): DATAOBJECT_RECIPE defaults hydration` | Backend: `POST /v2/dataobjects` gains `templateAppId`; `TemplateDefaultsHydrator` service; Neo4j migration none | Queued |
| **M1-PR4** (TPL1-b) | `feat(templates): template picker dialog in DataObject creation` | Frontend: `TemplatePickerDialog.vue` + integration into `AddDataObjectDialog.vue` | Queued |
| **M1-PR5** (TPL2c) | `feat(shapes): live channel resolution (TPL2c)` | Replace `status=DECLARED` with real OK/MISSING resolution; requires `aidocs/platform/87` TS-ID migration first | Blocked on TS-ID |
| **M1-PR6** (TPL2d) | `feat(shapes): custom renderer SRI URL support` | `renderer.startsWith("custom:")` dispatch; SRI hash verification of remote component bundle | Deferred |

PRs M1-PR1 and M1-PR2 ship together as the #83 commit.

---

## Cross-references

- `aidocs/semantics/95-shacl-templates-and-individuals.md` — canonical SHACL
  template design (Parts 1–7)
- `aidocs/semantics/98-shapes-views-and-process-model.md` — VIEW_RECIPE pipeline
  and wire format
- `aidocs/agent-findings/trace3d-spike.md` — TresJS library selection rationale
- `aidocs/platform/87-timeseries-appid-migration.md` — TS-ID migration (TPL2c
  blocker)
- `aidocs/34-upstream-upgrade-path.md` — operator upgrade notes for TPL2a/TPL2b
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — feature matrix row (TPL2 +
  SHACL-1)
- `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl` — meta-shape
  constraining VIEW_RECIPE bodies
- Task `#83` (this milestone), `#142` (Trace3D component), `#157` (shapes/render
  endpoint)
