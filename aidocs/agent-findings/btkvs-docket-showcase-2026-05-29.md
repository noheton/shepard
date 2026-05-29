---
stage: feature-defined
last-stage-change: 2026-05-29
audience: contributors + RDM + KVS-group external
---

# BT-KVS docket showcase — C/C and C/SiC fabrication tracking

**Source:** Nils-Packet-fuer-Claude.zip (operator-uploaded 2026-05-29).
Operator question: *can we make this a consistent Shepard showcase, and
in particular — is form-based entry a prime use case for templates?*

**Verdict:** **YES — and this is the strongest single validator of the
T1/TPL1/TPL2/SHACL design line we have to date.** The package contains
working tooling that already implements "form rendered from a
template stored in Shepard"; making it the canonical T1 showcase is
the right move.

The package itself stays in `/root/.claude/uploads/` per
`feedback_uploads_never_in_repo.md`; this doc is the abstracted
findings + integration plan.

---

## 1. What the package contains

The DLR BT-KVS group (Bauteilfertigung & Konstruktion / KVS Software)
runs a docket tracking system for **C/C and C/SiC fiber-reinforced
composite parts** through a 4-5-step fabrication process:

1. **Polymerisation** (RTM, autoclave-like wet impregnation)
2. **Tempering** (240 °C / 2h cure)
3. **Pyrolysis** (1000 °C+ inert-atmosphere conversion to C-matrix —
   often two passes for densification)
4. **Siliconization** (liquid Si infiltration → SiC matrix for C/SiC parts)

Each step carries process parameters (cycle, oven, temperature,
pressure, gas) plus a uniform **post-analysis sub-shape**: NDT (CT,
Röntgen), sampling, density+porosity (Archimedes-style), strength
analysis, part measurement, damage.

**Five code components** in the package:

| Repo | Role |
|---|---|
| `laufzettel-readout` | Reads Excel "Laufzettel" form → v1 JSON. Contains `material_database.py` (`fiber_database` + `fabric_database` — controlled vocabularies for fibers HTA / T800 / ... and weaves 98110 / 98140 / ...). |
| `docket_version_upgrader` | `v1 → v2 → v3` JSON converters. v3 is the current canonical shape. |
| `fastapi_simple_server` | Current REST API with file-storage fallback (`./json_data/{key}.json`) + per-entity routers for fibers + weaves + **a `templates_api.py` that already talks live to a Shepard instance** (uses `shepard_client`, pulls "post_analysis" templates from a configured Process Definitions container). |
| `frontend_streamlit` | Streamlit web frontend — `landing_page.py` (docket list) + `docket_details.py` (form-based edit) + custom widgets `ProcessStepElement`, `Grid`, `WeaveSelector`, `OptionalTextInput`, `DictTable`. Form shapes driven by `STEP_TEMPLATES` Python dict. |
| `Empty.xlsx` + `example.xlsx` | The legacy Excel form (input/output for the readout module) + a populated example. |

Plus `Shepard_Database_Setup.drawio` (the operator's hand-drawn schema
sketch) and `Targets.md` (5 work items for Claude — see §5 below).

---

## 2. v3 docket JSON shape (the data model)

Decoded from `example.json`:

```
docket (root)
├── docket_version: "v3"
├── general
│   ├── id: "I123"
│   ├── project, project_lead, ktr (Kostenträger / cost-center)
│   ├── delivery_date, comments
├── structure
│   ├── geometry: "Platte"
│   ├── dimensions_part / dimensions_cfk (each {values, units})
│   ├── fvc (fiber volume content)
│   ├── precursor, additive (boolean + description)
│   ├── reinforcement
│   │   ├── layer_count, sizing, preprocessing, orientation
│   │   ├── weave: { type, weave, manufacturer, area_density, layer_thickness, filament_count }
│   │   └── fiber: { name, material, density, diameter, cte, thermal_conductivity, filaments_per_yarn }
└── processing[]     # ordered list of steps
    ├── polymerisation: { method, method_description, editor, post_analysis }
    ├── tempering: { executed, cycle, editor, mass_analysis, post_analysis }
    ├── pyrolysis: { executed, cycle, burn_id, temperature, editor, mass_analysis, post_analysis }   # may repeat
    └── siliconization: { executed, cycle, burn_id, temperature, editor, mass_analysis, post_analysis }
```

Every step shares the **same `post_analysis` sub-shape**:

```
post_analysis
├── ndt[] : [{ method: "CT"|"Röntgen"|..., method_description, editor }, ...]
├── sampling: { execution, dimensions, editor }
├── density_porosity: { execution, mass_dry, mass_uw, mass_wet, open_porosity, density, editor }
├── strength_analysis: { execution, type: "Kriechprobe"|... }
├── part_measurement: { execution, thickness }
└── damage: { status, comment }
```

`editor` is the standard `{name, date}` tuple that appears throughout —
every action carries a who+when stamp (de-facto provenance).

---

## 3. Mapping to Shepard primitives (the integration plan)

The operator's drawio sketch already proposes most of this. Decoded
labels + a sanity-pass refinement:

| Docket concept | Shepard primitive | Notes |
|---|---|---|
| Docket Collection | `:Collection` | One per project / one per fabrication campaign. |
| Each docket | `:DataObject` (root) | `general` fields go on `attributes` map (TPL4 dual-write path); the rest land as child DOs. |
| General section | `:StructuredDataReference` on the root DO | Single-blob JSON; lightweight, no need for its own DO. |
| Structure section | `:DataObject` (child of root) | Has its own reinforcement child DO + fiber/weave xrefs. |
| Reinforcement | `:DataObject` (child of structure) | Cross-references to Fiber Material + Weave controlled-vocab containers (drawio "Container" boxes). |
| Each processing step | **`:DataObject` (child of root, ordered via predecessor/successor)** | Polymerisation → Tempering → Pyrolysis → ... ordering captured as `Predecessor` links per the f(ai)²r principle. |
| `post_analysis` per step | `:StructuredDataReference` on the step DO | Optional: split NDT into its own child if per-method `:Activity` provenance matters (today: just a JSON blob is fine). |
| Process curve (temp/pressure during firing) | `:TimeseriesContainer` + `:TimeseriesReference` | The drawio "Oven Collection" + "Process Curve" boxes — each firing logs a time-series. |
| Oven Collection | `:Collection` (cross-cutting) | One oven runs many firings; each `Pyrolysis` / `Siliconization` step DO links to an `:DataObject` in the oven Collection via Predecessor. |
| Additive / Precursor / Fiber Material vocabularies | **`:SemanticRepository` + `:SemanticVocabularyProvider`** | The drawio shows these as containers; Shepard's right shape is the semantic-vocab system (N1c) — each entry is a named individual, annotations on docket DOs reference IRIs. |
| Editor `{name, date}` stamps | **`:Activity` rows** (PROV-O) | The dual `sourceMode=human` + `agentUsername` shape captures this for free; no custom field needed. Operator gets free audit trail per `aidocs/platform/24`. |

**Key refinement vs. the drawio sketch:** the drawio uses
"Container" boxes for Additive / Precursor / Fiber Material catalogs.
Shepard's correct primitive here is the **`:SemanticRepository`**
(N1c, shipped) — controlled vocabularies as semantic individuals, not
as a Container kind. The fabrication-domain ontology (`urn:btkvs:*`)
becomes a vendor ontology per the semantic-config admin pattern.

---

## 4. **The form-based-entry insight — this is the canonical T1 showcase**

The operator's headline observation is the most important architectural
finding: **the Streamlit docket form should not be driven by hard-coded
Python dicts. It should be driven by SHACL shapes stored in Shepard's
`:ShepardTemplate` registry, fetched live, and rendered to a form
component dynamically.**

This is what T1 (Templates), TPL1+TPL2 (Shapes as templates + views),
the SHACL changeover, and the `:ShepardTemplate {templateKind: VIEW_RECIPE}`
work shipped over the past month were all designed for.

### Today's BT-KVS state

- `frontend_streamlit/pages/docket_details.py` reads `STEP_TEMPLATES`
  from `src/step_templates.py` (Python dict). Adding a new step
  type → Python code change → redeploy.
- `fastapi_simple_server/templates_api.py` **already** pulls
  `post_analysis` templates from a Shepard `StructuredDataContainer`
  named "BT-KVS - Templates - Post Analysis" + a Process Definitions
  container. The pattern is **right** — it's just bespoke wiring.

### The properly-Shepard-shaped target

1. Register `urn:btkvs:template:docket-step-polymerisation`
   (and tempering, pyrolysis, siliconization) as
   `:ShepardTemplate` nodes with `templateKind=VIEW_RECIPE` (or a new
   `templateKind=FORM_RECIPE` — see §6 question) carrying SHACL shapes
   describing fields, types, units, required-ness, enumeration
   constraints, vocabulary references.
2. Streamlit frontend fetches templates via `GET /v2/templates?kind=FORM_RECIPE`
   (or via `POST /v2/shapes/render` if rendered server-side).
3. A small form-component library reads a SHACL shape → renders Vuetify
   form fields (`shacl:datatype xsd:string` → `<v-text-field>`,
   `shacl:in` → `<v-select>`, `shacl:class :Fiber` → `<v-autocomplete>`
   that lists the fiber semantic vocabulary, etc.).
4. Add a new docket field → edit the SHACL shape in Shepard → form
   updates live across all instances. **Zero Python code change.**
5. Same shape drives:
   - Streamlit form (today's BT-KVS frontend)
   - Shepard's own Vue/Vuetify form (the TPL2/T1 UI work in progress)
   - Validation when a docket is uploaded (SHACL validation against the shape)
   - Round-trip Excel export (the v3 → Excel converter Nils asked for can read the same SHACL to know which cell maps to which field)

The operator already pointed at this — "form based entry could be a
prime use case for templates" — and the existing FastAPI code proves
the pattern is workable. This package elevates T1 from a "designed +
in-flight" feature to a "validated by an external team running a real
production process" feature.

---

## 5. Mapping to Targets.md (operator's work items)

The operator's `Targets.md` lists 5 tasks. Mapping each to a Shepard-side
deliverable:

| Operator task | Shepard-side mirror |
|---|---|
| 1. Update `laufzettel-readout` to v3 | Out of scope for Shepard; pure operator-side python work. |
| 2. v3 JSON → Excel converter | Out of scope for Shepard; pure operator-side. But the SHACL-driven shape (§4) means the converter can read the shape to know the cell mapping. |
| 3. **Create Shepard Setup** | **This is the prime Shepard deliverable.** Two PRs: (a) seed script + showcase Collection structure following §3 above; (b) the SHACL templates per §4 registered via TPL5 git-ontology ingestion or `:ShepardTemplate` upload. |
| 4. Frontend Excel upload + auto-convert | Mostly operator-side. Shepard contribution: ensure the SHACL shape can drive the Excel → JSON parse direction (same shape, different consumer). |
| 5. Frontend Excel download | Same as (2) — operator-side with the SHACL shape as the schema spine. |

---

## 6. Concrete next steps (proposed sequence)

### Phase A — lightweight: ship as a third showcase (XS-S, days)

Mirror the LUMEN / MFFD / microsections pattern under
`examples/btkvs-docket-showcase/`:

1. `examples/btkvs-docket-showcase/seed.py` — reads `example.json`,
   creates the Collection structure per §3, uploads StructuredData
   payloads, links Predecessor/Successor between process steps.
2. `examples/btkvs-docket-showcase/SHOWCASE.md` — narrates the C/C and
   C/SiC fabrication chain + the "post_analysis is uniform across
   steps" insight + the "editor stamps → :Activity provenance" mapping.
3. `examples/btkvs-docket-showcase/raw-data/example.json` (gitignored
   like microsections).
4. A controlled-vocab Cypher seed creating `:SemanticRepository`
   `urn:btkvs:fiber` + `urn:btkvs:fabric` + `urn:btkvs:precursor` +
   `urn:btkvs:additive` semantic-individuals.

### Phase B — the validating slice: SHACL templates as forms (S-M)

5. `aidocs/integrations/116-btkvs-shacl-form-templates.md` design doc.
6. Register a minimal subset: ONE SHACL shape (the docket-root general
   section is good first target) as a `:ShepardTemplate` with
   `templateKind=VIEW_RECIPE` (or new `FORM_RECIPE` — see open question
   below).
7. Server-side render endpoint already exists (`POST /v2/shapes/render`
   from TPL2). Validate it can produce a form-friendly response.
8. A tiny Streamlit (or Vue) demo that fetches the shape + renders it
   as a form.

### Phase C — the full integration (M-L, weeks)

9. Bridge plugin `shepard-plugin-btkvs-docket` — extends the
   FastAPI-server pattern as a shepard plugin: handles the v3 docket
   JSON shape as a typed payload kind, routes uploads via SHACL
   validation, exposes `POST /v2/btkvs/dockets`.
10. Excel ↔ JSON round-trip driven by the SHACL shape (the
    `urn:btkvs:cell-mapping` predicate could carry per-field cell
    coordinates).

---

## 7. Open questions

These need an operator + persona-board read before implementation:

1. **`VIEW_RECIPE` vs new `FORM_RECIPE` template kind?** Today's
   `:ShepardTemplate.templateKind` enum has `VIEW_RECIPE` (for the
   Trace3D / spatial / thermography renderers). A docket form is
   logically a different consumer of the same SHACL shape — it
   *renders* the shape as input controls, not visualisation. Adding
   a `FORM_RECIPE` kind feels right but doubles the registry surface.
   Alternative: one kind, multiple SHACL property hints
   (`shepard:formHint` annotations) that tell the consumer how to
   render. Lighter. **Decide in Phase B step 5.**
2. **Semantic-vocab vs Container for material catalogs?** The operator's
   drawio uses Containers. Shepard's right shape (per N1c) is
   `:SemanticRepository`. Operator-friendly transitional shape
   (Phase A) might use a Container with controlled `attributes`; the
   N1c migration is a Phase C move.
3. **Editor stamps → who is the user?** The docket JSON's `editor.name`
   field is free-text. Mapping to a `:User` requires either
   ORCID-claim resolution OR a name-string lookup with collision
   handling. Suggest: keep `editor.name` as a string for now, file
   `BTKVS-USER-RESOLVE` as a Phase C follow-up.
4. **Process curves as TS or as files?** Pyrolysis logs temperature
   and pressure curves; in the drawio they're labeled "Curve". If
   curves are sampled at high rate → `:TimeseriesContainer`. If they're
   logged as PDF / chart exports → file references. Operator decision.

---

## 8. Why this matters

Three reasons this is worth prioritising:

1. **External-team validation.** Nils + Dennis at BT-KVS already wrote
   a Shepard client + a form-driven frontend. They're running this in
   production. A Shepard-side properly-shaped showcase + plugin =
   their workflow becomes the canonical T1 demo, not a side-project.
2. **The T1 / TPL design pivot has been theory for weeks.** This
   package is the concrete realistic dataset that proves it. We've
   been writing "shapes as templates" docs without a load-bearing
   external consumer. BT-KVS is the consumer.
3. **The MFFD + LUMEN showcases prove timeseries + spatial; this proves
   structured-data + forms + cross-cutting controlled vocabularies.**
   Three showcases × three concern axes = the case study any external
   evaluator needs.

The microsections showcase (shipped today) handles file-based
analysis tracking. BT-KVS handles structured-form-based fabrication
tracking. The pair covers the "researcher-facing data captured via
forms" use case end-to-end.
