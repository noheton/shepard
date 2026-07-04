---
title: BTKVS-B1 — form-as-SHACL-shape: one shapes UX for data out (views) and data in (forms)
stage: feature-defined
last-stage-change: 2026-06-12
audience: contributors + BT-KVS group external + frontend + ontologist
---

# 125 — BTKVS-B1: SHACL form templates — the unified shapes UX

**Status:** feature-defined 2026-06-12 · scope `BTKVS-B1` · the backlog row
that names this "where T1 stops being theoretical".

**Renumbering note:** the BTKVS-B1 backlog row originally named this doc
`aidocs/integrations/116-btkvs-shacl-form-templates.md`. Slot 116 was
meanwhile taken by [`116-btkvs-improved-schema.md`](116-btkvs-improved-schema.md)
(BTKVS-A4); numeric prefixes are unique per the SSOT rule, so this doc lands
at the next free slot, **125**. The `aidocs/16` row is updated to cite this
path.

**Inputs (read in this order):**
[`btkvs-docket-showcase-2026-05-29.md`](../agent-findings/btkvs-docket-showcase-2026-05-29.md)
(§4 form insight, §7.1 the open question this doc decides) ·
[`116-btkvs-improved-schema.md`](116-btkvs-improved-schema.md) (the docket
graph the forms must produce) ·
[`aidocs/platform/191-v2-surface-convergence.md`](../platform/191-v2-surface-convergence.md)
(the "templates ARE shapes" spine — §3 one-shape-five-uses table) ·
[`ux-shapes-displays-and-journeys-2026-06-12.md`](../agent-findings/ux-shapes-displays-and-journeys-2026-06-12.md)
(the render-side applicable-views contract this design must pair with) ·
[`aidocs/semantics/95`](../semantics/95-shacl-templates-and-individuals.md) §4
(shapes-as-forms widget catalogue) · [`aidocs/semantics/98`](../semantics/98-shapes-views-and-process-model.md).

---

## §0 Decision summary (the one page)

| # | Question | Decision |
|---|---|---|
| **D1** | New `FORM_RECIPE` TemplateKind vs reuse `VIEW_RECIPE`? | **Neither.** A form is the **write-direction projection of a DATA-kind shape** (`DATAOBJECT_RECIPE` / `COLLECTION_RECIPE` / `STRUCTURED_RECIPE`). No new TemplateKind; no VIEW_RECIPE overload. The same `shapeGraph` that V2CONV-B2 already validates at instantiation *is* the form — generating the form from anything else reintroduces the drift the spine exists to kill. §3 argues it; the opposing lens is included. |
| **D2** | The `shepard:formHint` vocabulary — invent `urn:shepard:form:*`? | **Adopt DASH + core SHACL first; mint only the residue.** Core SHACL non-validating characteristics (`sh:name`, `sh:description`, `sh:order`, `sh:group`/`sh:PropertyGroup`, `sh:defaultValue`) + DASH (`dash:editor`, `dash:viewer`, `dash:singleLine`, the `dash:Editor` instance catalogue) cover ~90 % of hint needs — DASH **is** the standard answer. Shepard mints: custom `dash:Editor` *individuals* (`shepard-ui:VocabularyAutocompleteEditor`, …) — sanctioned DASH extension shape — plus exactly two predicates DASH lacks: `urn:shepard:form:visibleWhen`, `urn:shepard:form:placeholder`. Excel cell-mapping stays domain-side: `urn:btkvs:cell-mapping` + `urn:btkvs:sheet`. §4. |
| **D3** | Render-endpoint shape for forms? | **Not `POST /v2/shapes/render`** (that endpoint is output-direction, VIEW_RECIPE-gated, focus-rooted — a create-form has no focus yet). New sibling **`GET /v2/templates/{templateAppId}/form`** returns a compiled, cacheable **form-descriptor JSON** (flattened inherited shape + hints + a server-computed `submit` block). The submit leg is the **existing** instantiation endpoint (`POST …/data-objects/from-template/{templateAppId}`, V2CONV-B2-validated) resp. the BTKVS-A3 decompose endpoint; its 422 grows a structured `violations[]` so SHACL `resultPath`s map back to form fields. §5 with worked examples. |
| **D4** | Alignment with the UX audit's applicable-views contract? | **One discovery endpoint for both directions:** `GET /v2/shapes/applicable?focusAppId=…` returns renderable views **and** fillable forms; discriminator field **`mode: "VIEW" \| "FORM"`**. One `ActionMenuButton` (the audit's `ViewMenuButton`, generalised) carries "View as…" (mode=VIEW) and "Record a…" (mode=FORM) groups on every detail page. §5.3. |
| **D5** | Excel ↔ JSON round-trip? | **The shape is the single schema spine.** `urn:btkvs:cell-mapping` annotations on property shapes drive export (shape walk → POI workbook) AND import (cells → candidate graph → SHACL validate → decompose). Retires `laufzettel-readout`'s hand-coded `ws.range("C4")` table, the xlwings/Excel-installed dependency, and `docket_version_upgrader` (shape versions = template versions). Contract-level spec in §6; implementation is BTKVS-C2. |

Persona verdicts (§7): **Role 5 RDM — approve** (completeness at point of
capture; rejects unbound free-text fallbacks and silent draft-skip-validation).
**Role 2 Ontologist — approve with governance conditions** (hints are
annotation-on-shape, non-validating — correctly separated; rejects any
parallel-to-DASH predicate minting).

---

## §1 Reuse survey (mandatory)

### 1.1 In-tree — almost the whole machine exists

| Seam | State | File / evidence |
|---|---|---|
| **SHACL shape-builder** (JSON DSL → canonical Turtle, deterministic, blank-node-free) | ✓ shipped (V2CONV-B1) | `backend/.../v2/shapes/builder/ShaclShapeBuilder.java` (+ `ShapeSpec`, `PropertyShapeSpec`, `InMember` with explicit IRI-vs-literal typing) |
| **Validate-on-instantiate** (the formerly dormant seam — now live) | ✓ shipped (V2CONV-B2) | `TemplateInstantiationRest.java:197-229` — flattens inherited `shapeGraph`, builds the candidate data Turtle (`urn:shepard:attribute:*`), `JenaShaclValidator.validate`, **422 on violation**. The form's server-side enforcement leg already exists; what's missing is only the structured violation wire shape (§5.2). |
| **SHACL validator** | ✓ shipped | `JenaShaclValidator` behind `POST /v2/shapes/validate` (stateless dataTurtle + shapeTurtle → report with `resultPath`/`value`/`message` findings) |
| **Template substrate** | ✓ shipped | `ShepardTemplate` (`templateKind` string; kinds today: `COLLECTION_RECIPE`, `DATAOBJECT_RECIPE`, `VIEW_RECIPE`, `MAPPING_RECIPE`, `STRUCTURED_RECIPE` — `TemplateBodyValidator.java:44-64`), `shapeGraph` body field (`ShepardTemplateIO.java:34-42`), single-parent inheritance with parent-ahead `shapeGraph` concat (`TemplateInheritanceResolver:152-153`), retirement flag, allow-lists, `iconKey` |
| **Render dispatch + content negotiation** | ✓ shipped (V2CONV-A1/A1b) | `ShapesRenderRest.java` — template-rooted + file-rooted dispatch, `params`, `FocusPayloadResolver`, `producibleMedia()`. Stays **untouched** by this design (D3 keeps forms off the render path). |
| **Predicate palette** | ✓ shipped | `:Vocabulary`/`:Predicate` nodes + `predicate_vocabulary` table (51+ predicates with substrate + cardinality) + QUDT units — the same palette the annotation picker and the planned template editor (V2CONV-B6) use |
| **Meta-grammar** | ✓ shipped | `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl` — the VIEW_RECIPE structural contract; §4.4 specifies its **sibling**, `form-hints-meta.shacl.ttl`, not an extension (hints attach to *data* shapes, not view recipes) |
| **Widget catalogue (designed)** | 📐 aidocs/semantics/95 §4 | 13 v1 widgets `sh:datatype`→Vuetify mapping (TPL1c) — this doc maps that catalogue onto DASH editor IRIs instead of implicit datatype switching (§4.2) |
| **Vocabulary autocomplete source** | ✓ shipped (BTKVS-A2) | `urn:btkvs:fiber` + `urn:btkvs:fabric` live in the internal semantic repo (9 fibers + 20 fabrics, n10s-imported, term-search-verified) — the `sh:class` → autocomplete widget has real data behind it from day one |
| **Docket graph target** | ✓ shipped (BTKVS-A1/A4) | `examples/btkvs-docket-showcase/seed.py` + doc 116 — 12 DOs + 11 StructuredDataReferences per docket; the form's submit decomposition target |

### 1.2 External — what the BT-KVS team runs today (studied read-only at `/tmp/nils-cc-csic-showcase/_unpacked/`; never copied into the repo)

- `frontend_streamlit-main/src/step_templates/*.json` — the form *shape* is a
  Python-loaded JSON dict per process step (`pyrolysis.json` etc.: flat
  key→default-value maps with nested `editor`, `mass_analysis`,
  `post_analysis` objects). No types, no required-ness, no enumerations —
  the Streamlit widgets re-encode all of that in code
  (`pages/docket_details.py`, `custom_st_elements.py`, `weave_selector.py`).
- `fastapi_simple_server-main/templates_api.py` — already fetches
  "post_analysis" templates **live from a Shepard StructuredDataContainer**
  (numeric container ids from env, name-matched DataObjects). The pattern is
  right; the carrier is bespoke. The form-descriptor endpoint (§5.1) is its
  typed replacement.
- `laufzettel-readout-main/src/docket_to_excel.py` — the Excel mapping is a
  hand-maintained `ws.range("C4").value = dict[...]` table over `Empty.xlsx`
  via **xlwings (requires a running Excel installation)**, including
  conditional cell choices per fiber type. This file is the strongest
  argument for D5: every schema change edits this Python by hand today.

### 1.3 External — state of the art (researched 2026-06-12)

- **DASH (Datashapes)** — [Form generation using SHACL and DASH](https://www.datashapes.org/forms.html)
  + [DASH vocabulary](https://datashapes.org/dash): `dash:editor` /
  `dash:viewer` annotations on property shapes; an editor catalogue
  (`dash:TextFieldEditor`, `dash:TextAreaEditor`, `dash:EnumSelectEditor`,
  `dash:AutoCompleteEditor`, `dash:DatePickerEditor`,
  `dash:BooleanSelectEditor`, `dash:URIEditor`, `dash:DetailsEditor` for
  nested forms, `dash:RichTextEditor`); `dash:singleLine`; a documented
  default-editor **scoring algorithm** (constraint-derived 0–100 scores when
  no explicit hint is given); `sh:group` + `sh:order` for layout. Explicitly
  vendor-neutral, explicitly extensible with custom `dash:Editor` instances.
  Used in production by TopBraid EDG.
- **shaperone** ([hypermedia-app/shaperone](https://github.com/hypermedia-app/shaperone),
  [forms.hypermedia.app](https://forms.hypermedia.app/)) — the most active
  OSS SHACL form renderer (web components, RDF/JS); consumes **DASH editor
  annotations**, demonstrates SPARQL-backed `dash:AutoCompleteEditor`
  (Wikidata live lookup — the exact shape of Shepard's vocabulary
  autocomplete). Validates that "shape + DASH hints → form" needs no
  vocabulary invention.
- **JSON-Schema form generators** (JSONForms, react-jsonschema-form
  [uiSchema docs](https://rjsf-team.github.io/react-jsonschema-form/docs/api-reference/uiSchema/),
  [2025 comparison](https://dev.to/yanggmtl/schema-driven-forms-in-react-comparing-rjsf-json-forms-uniforms-formio-and-formitiva-2fg2)) —
  the ecosystem-wide lesson: **data schema and UI schema must be separate
  layers but single-sourced**; the recurring failure mode is conditions
  described twice ("required if…" in the data schema vs "show if…" in the
  UI schema) drifting apart. This is direct evidence for D1 (no separate
  FORM_RECIPE that would re-state the data shape) and for keeping
  `visibleWhen` an annotation **on** the data shape, not a parallel artifact.
- **CEDAR** — O'Connor et al. 2024, *Scientific Data*: template-driven
  **spreadsheet** metadata entry with controlled-terminology cell validation
  and an interactive fix-errors loop — peer-reviewed validation of exactly
  the D5 Excel round-trip (researchers prefer spreadsheets; the template
  enforces the standard anyway). Sundaram et al. 2025, *GigaScience*:
  CEDAR-template-guided metadata standardisation lifted dataset retrieval
  recall 17.65 % → 62.87 % — the quantified payoff of structured capture
  that Role 5 cites in §7.

**Verdict:** nothing in this design invents a mechanism that doesn't already
exist either in-tree or as an adopted external standard. The genuinely new
pieces are: the form-descriptor compiler (§5.1), the structured-422 wire
shape (§5.2), the `mode` discriminator on applicable-discovery (§5.3), and
the two minted hint predicates (§4.3).

---

## §2 The unified shapes-UX model

The 191 §3 table, with the form row slotted in where it was implicitly
hiding all along — the **Data** row's "generate create-form" clause becomes
a first-class column instead of a parenthesis:

| Use | TemplateKind | Direction | Consumption surface |
|---|---|---|---|
| **Data contract** | `DATAOBJECT_RECIPE` / `COLLECTION_RECIPE` / `STRUCTURED_RECIPE` | — | shape constrains entity properties; validate at instantiation (V2CONV-B2, shipped) |
| **Form** *(this doc)* | **the same data-kind template** | **in** | `GET /v2/templates/{appId}/form` → descriptor → rendered form → POST instantiate/decompose → 422 violations map to fields |
| **View** | `VIEW_RECIPE` | out | `POST /v2/shapes/render` (content-negotiated; shipped) |
| **Mapping/Transform** | `MAPPING_RECIPE` | in+out | `POST /v2/mappings/{appId}/materialize` (shipped) |
| **Kind contract** | plugin-declared | — | a kind ships its data-shape + view-shape via the builder |
| **Agent contract** | any | — | the shape is the machine-readable MCP/LLM contract |

The symmetry the operator asked for, stated once: **a VIEW_RECIPE projects
an entity's data out through a shape; a form projects a shape out so a
human can push data in.** Both directions surface through the same
in-context discovery (`/v2/shapes/applicable`, §5.3), the same template
registry, the same inheritance/retirement/allow-list machinery, the same
SHACL substrate, and — crucially — the same validator: the constraints the
form renders client-side are byte-identical to the constraints
`TemplateInstantiationRest` enforces server-side, because both read the one
flattened `shapeGraph`.

What a user sees: on a Collection or DataObject detail page, one action
button. Its menu has two groups — *"View as…"* (3D trace, heatmap, docket
record one-pager) and *"Record a…"* (Pyrolysis step, Post-analysis,
new Docket). Two clicks either way. The 14-interaction/9-concept path the
UX audit measured collapses identically for both directions.

---

## §3 D1 — no `FORM_RECIPE`, no VIEW_RECIPE reuse: forms are the data shape's write projection

### 3.1 The two framings from findings §7.1, and why both lose

**Framing A — reuse `VIEW_RECIPE`** ("a form is just a renderer whose output
medium is an editable view that POSTs back"). One-substrate purity, zero new
enum values. It loses on three concrete contract mismatches:

1. `VIEW_RECIPE` bodies are **binding-shaped** (`renderer`,
   `channelBindings`, `viewRecipeShape` — `view-recipe-meta.shacl.ttl`).
   A docket form has none of that and needs all of what VIEW_RECIPE lacks:
   property constraints, datatypes, enumerations, required-ness. A
   "form-flavoured VIEW_RECIPE" would carry an empty view body plus a full
   data shape — i.e. it would *be* a data template wearing the wrong kind.
2. `POST /v2/shapes/render` is **focus-rooted** (`focusShepardId` resolves
   an existing entity's data). A create-form's entire point is that the
   entity doesn't exist yet.
3. The instantiation validator (V2CONV-B2) reads the **data-kind**
   template's `shapeGraph`. If the form is generated from a VIEW_RECIPE
   sibling, the rendered constraints and the enforced constraints live in
   two templates — drift by construction.

**Framing B — new `FORM_RECIPE` kind** ("write-path validation, submission
target, partial saves are distinct concerns deserving a kind"). It loses on
the same drift argument from the other side: a FORM_RECIPE must restate the
data shape (what fields, what types, what's required) that the
DATAOBJECT_RECIPE already owns — the JSON-Schema ecosystem's documented
failure mode (§1.3: "conditions described in two places… requiring
synchronization"). Every BT-KVS schema change would touch two templates.
It also doubles the registry surface (the findings doc's own cost note) and
forces the discovery endpoint, the allow-lists, the editor, and the MCP
contract to learn a kind whose only content is a pointer to another kind.

### 3.2 The decision

**A form is not a kind of template; it is a direction of consumption.**
The form for "create a Pyrolysis step" is the
`urn:btkvs:template:docket-step-pyrolysis` **DATAOBJECT_RECIPE** itself,
compiled to a form descriptor by §5.1. The form for "fill the docket
`:general` section" is a **STRUCTURED_RECIPE** (structured-payload skeleton
+ shapeGraph). Form-specific concerns find homes without a kind:

| Form concern | Home |
|---|---|
| Widget choice, layout, grouping, ordering | hint annotations *on* the data shape (§4) — non-validating, ignored by the validator per the SHACL spec's design for `sh:name`/`sh:order` |
| Submission target | server-computed `submit` block in the form descriptor (§5.1) — derived from templateKind + plugin payload-kind registration, never authored |
| Write-path validation | already shipped (V2CONV-B2); the form just consumes its 422 (§5.2) |
| Partial saves / drafts | out of scope v1; when needed it is an entity-state concern (DRAFT status on the created DO), not a template concern — see §9 risks |
| Multi-entity wizards (whole docket) | `sh:node` composition of data shapes (template inheritance + nested shapes, both shipped) — the descriptor compiler walks them (§5.1, `dash:DetailsEditor` nesting) |

**The loser's costs, honestly.** Choosing "no kind" means: (a) there is no
single registry query "give me all forms" — the answer is "all non-retired
data-kind templates", filtered by the applicable endpoint per context; (b)
a template author cannot mark a data template as *not* form-offerable except
via an annotation (`urn:shepard:form:hidden true` on the node shape — added
to §4.3's minted set); (c) if a future form genuinely has no 1:1 data shape
(e.g. a cross-entity batch-edit form), this decision must be revisited —
that is the named revisit trigger, recorded in §9.

### 3.3 Opposing lens (Role 3, API Minimalist — argued against my decision)

> "You congratulate yourself on minting no enum value, but you minted an
> endpoint instead. `GET /v2/templates/{appId}/form` is a *projection* the
> client could compute: it has the template (with `shapeGraph`) from
> `GET /v2/templates/{appId}` already — ship a TypeScript SHACL-to-form
> compiler and keep the server out of it. shaperone does exactly that,
> client-side, from raw Turtle. And if you *must* have a server artifact,
> why isn't it `Accept: application/schema+json` on the existing template
> GET, instead of a new path?"

The answer, conceded in part: the descriptor *is* a projection of existing
state — no new entity, no new store, which is the minimalist line that
matters most. It stays server-side for three reasons: (1) the **flattening**
(inheritance chain, parent-ahead shapeGraph concat) and the **submit-target
computation** (which endpoint, which plugin) require server knowledge the
client doesn't have without N+1 calls; (2) the descriptor is consumed by
**non-browser clients** — the BT-KVS Streamlit frontend (Python) and MCP
agents — for whom "ship a TS SHACL compiler" is no answer; (3) determinism +
cacheability: one compiled artifact, testable byte-for-byte, mirroring the
ShaclShapeBuilder's determinism contract. The `Accept`-flavour variant on
the template GET is a legitimate alternative spelling; the dedicated path is
chosen because the descriptor's cache key differs from the template's
(it varies with the inheritance chain) and because OpenAPI tooling models a
distinct response schema far better on a distinct path. The minimalist wins
one concretely: **no new TemplateKind, no new entity, no write surface** —
the entire feature is two GET projections (descriptor + applicable) over
state that already exists.

---

## §4 D2 — the form-hint vocabulary: DASH first, mint only the residue

### 4.1 The DASH verdict

The external research question — "check DASH seriously, it may BE the
standard answer instead of inventing `urn:shepard:form:*`" — resolves
**yes, DASH is the answer** for widget/viewer hints. It is vendor-neutral by
stated goal, extensible by design (custom `dash:Editor` instances are the
sanctioned pattern), implemented by the most active OSS renderer
(shaperone) and by TopBraid EDG, and it composes with the core-SHACL
non-validating characteristics Shepard's shapes already use (`sh:name`,
`sh:order` appear in `view-recipe-meta.shacl.ttl` today). Inventing
`urn:shepard:form:widget` would be exactly the mutate-a-shared-concern
anti-pattern the evolve-in-new-namespace rule exists to prevent — the new
namespace here **is** DASH-the-adopted-vocabulary, not a parallel one.

### 4.2 The three hint layers

**Layer 1 — core SHACL (no minting, no DASH needed):**

| Predicate | Form meaning |
|---|---|
| `sh:name` / `sh:description` | label / help text |
| `sh:order` | field order (decimal; unordered fields sort last, alphabetical — DASH's documented rule) |
| `sh:group` → `sh:PropertyGroup` (with its own `rdfs:label` + `sh:order`) | form sections — the docket's *General / Structure / Processing* panels |
| `sh:defaultValue` | pre-filled value |
| `sh:minCount ≥ 1` | required marker + client-side required validation |
| `sh:in`, `sh:datatype`, `sh:class`, `sh:pattern`, `sh:minInclusive`/`sh:maxInclusive`, `sh:node` | constraint-derived widget selection inputs (Layer 2 scoring) |

**Layer 2 — DASH (adopted verbatim):** `dash:editor` / `dash:viewer` on
property shapes; `dash:singleLine`; the standard editor catalogue mapped
onto the aidocs/95 §4 Vuetify widget set:

| DASH editor | Vuetify widget (95 §4 catalogue) |
|---|---|
| `dash:TextFieldEditor` | `v-text-field` |
| `dash:TextAreaEditor` | `v-textarea` |
| `dash:EnumSelectEditor` | `v-select` over `sh:in` |
| `dash:AutoCompleteEditor` | `v-autocomplete` over `sh:class` individuals |
| `dash:DatePickerEditor` / `dash:DateTimePickerEditor` | `v-date-picker` |
| `dash:BooleanSelectEditor` | `v-switch` |
| `dash:URIEditor` | IRI picker |
| `dash:DetailsEditor` | recursive inline sub-form (`sh:node`, lazily expanded) |

When no explicit `dash:editor` is present, the descriptor compiler applies
DASH's constraint-scoring default selection (e.g. `sh:in` present →
EnumSelect; `sh:datatype xsd:boolean` → BooleanSelect) — so an unannotated
shape still renders a sensible form, the Observable-Plot defaults-first
posture.

**Shepard-minted DASH editor individuals** (instances of `dash:Editor` in
the `shepard-ui:` namespace — extension, not mutation):

| Editor individual | Widget | Selected for |
|---|---|---|
| `shepard-ui:VocabularyAutocompleteEditor` | autocomplete over `/v2/semantic/terms/search` scoped to a vocabulary | `sh:class` whose class IRI lives in the internal semantic repo (e.g. `urn:btkvs:fiber` — the BTKVS-A2 payload renders here with zero extra wiring) |
| `shepard-ui:FileUploadEditor` | FR1b singleton upload widget | `sh:class shepard:FileReference` |
| `shepard-ui:UnitNumberEditor` | numeric input + unit pill | `qudt:unit` present on the property shape |
| `shepard-ui:ChannelPickerEditor` | TS channel picker with annotation preselection | `sh:class shepard:TimeseriesReference` |

**Layer 3 — minted predicates (the residue DASH genuinely lacks):**

| Predicate | Range | Meaning |
|---|---|---|
| `urn:shepard:form:visibleWhen` | string (JSON: `{"path": <predicate IRI>, "equals"/"in": …}`) | conditional visibility — *show `fabric_type` only when `fiber_material ∈ {Gewebe, Prepreg}`* (the live conditional in `docket_to_excel.py`). Presentation-only: the validator never reads it; conditional *required-ness* must be expressed in SHACL proper (`sh:or` branches), keeping the JSON-Schema two-places drift impossible by construction. |
| `urn:shepard:form:placeholder` | string | input placeholder text (distinct from `sh:description` help text) |
| `urn:shepard:form:hidden` | boolean (on the node shape) | template author opt-out from form offering (§3.2 cost (b)) |

That is the **entire** core minted set — three predicates. They are
documented in the organizing-ontology manifest per the namespace rule.

**Domain layer (vendor ontology, not core):** `urn:btkvs:cell-mapping`
(string, Excel `A1`-style cell ref) + `urn:btkvs:sheet` (string, worksheet
name) on property shapes — consumed only by the BTKVS plugin's Excel
round-trip (§6). Other domains mint their own siblings in their own
namespaces; core Shepard never learns Excel.

### 4.3 Governance

- All Layer-3 predicates ship in the organizing ontology with `rdfs:label`,
  `rdfs:comment`, and a `form-hints-meta.shacl.ttl` **sibling** of the
  view-recipe meta-shape (it constrains hint usage on data shapes — wrong
  to graft onto the view-recipe grammar, hence sibling not extension).
- The DASH vocabulary file (`dash.ttl`, W3C-document licence) is seeded into
  the internal semantic repository like QUDT — one admin-upload bundle, so
  `dash:editor` IRIs resolve in the term search and the template editor
  palette (row FORM-DASH-VOCAB, §8).
- The `ShaclShapeBuilder` JSON DSL grows optional per-property fields
  `editor`, `group`, `placeholder`, `visibleWhen`, `cellMapping` that compile
  to the corresponding annotations — authors never hand-write the Turtle.

---

## §5 D3 + D4 — endpoint contracts

### 5.1 `GET /v2/templates/{templateAppId}/form` — the form descriptor

Read-only, `@RolesAllowed("authenticated")`, cacheable
(`ETag` over template appId + flattened-body hash). Compiles: flatten
inheritance (`TemplateInheritanceResolver`) → parse `shapeGraph` (Jena) →
apply DASH default-editor scoring where no explicit hint → emit deterministic
descriptor. Optional `?focusAppId=` (edit-forms / "add child to this DO"):
prefills `values` from the focus entity and threads the focus into `submit`.

404 unknown template · 409 retired template · 422 when the flattened body
has no `shapeGraph` (a legacy attribute-bag template is not form-renderable;
message points at the shape-builder).

**Request:**

```http
GET /v2/templates/019f1234-aaaa-7bbb-cccc-dddddddddddd/form HTTP/1.1
Accept: application/json
```

**Response (docket `:general` section — the BTKVS-B2 acceptance shape):**

```json
{
  "templateAppId": "019f1234-aaaa-7bbb-cccc-dddddddddddd",
  "templateKind": "STRUCTURED_RECIPE",
  "title": "Docket — general section",
  "shapeIri": "urn:btkvs:shape:docket-general",
  "groups": [
    { "id": "urn:btkvs:group:identity", "label": "Identity", "order": 1 }
  ],
  "fields": [
    {
      "path": "urn:btkvs:docket:id",
      "label": "Docket ID",
      "group": "urn:btkvs:group:identity",
      "order": 1,
      "datatype": "http://www.w3.org/2001/XMLSchema#string",
      "required": true,
      "pattern": "^[A-Z][0-9]{3}$",
      "editor": "http://datashapes.org/dash#TextFieldEditor",
      "placeholder": "I123",
      "cellMapping": { "sheet": "Laufzettel C-C bzw C-C-SiC", "cell": "K1" }
    },
    {
      "path": "urn:btkvs:docket:project",
      "label": "Project",
      "order": 2,
      "required": true,
      "editor": "http://datashapes.org/dash#TextFieldEditor",
      "cellMapping": { "cell": "C4" }
    },
    {
      "path": "urn:btkvs:docket:projectLead",
      "label": "Project lead",
      "order": 3,
      "editor": "http://datashapes.org/dash#TextFieldEditor",
      "cellMapping": { "cell": "G4" }
    },
    {
      "path": "urn:btkvs:docket:deliveryDate",
      "label": "Delivery date",
      "order": 4,
      "datatype": "http://www.w3.org/2001/XMLSchema#date",
      "editor": "http://datashapes.org/dash#DatePickerEditor",
      "cellMapping": { "cell": "K4" }
    },
    {
      "path": "urn:btkvs:docket:comments",
      "label": "Comments",
      "order": 5,
      "editor": "http://datashapes.org/dash#TextAreaEditor",
      "singleLine": false
    }
  ],
  "submit": {
    "method": "POST",
    "href": "/v2/collections/{collectionAppId}/data-objects/from-template/019f1234-aaaa-7bbb-cccc-dddddddddddd",
    "violationContract": "problem+json violations[] keyed by field path"
  }
}
```

`submit.href` is computed: data-kind templates → the instantiation endpoint;
templates whose shape IRI is claimed by a plugin payload kind (BTKVS-C1's
docket kind) → the plugin's decompose endpoint
(`POST /v2/btkvs/dockets`). The client never chooses a target — the
UI-never-asks-for-paths rule applied to endpoints.

### 5.2 The submit leg + 422 → field-error mapping

No new write endpoint. The form POSTs to `submit.href`. The shipped
V2CONV-B2 validation already 422s on shape violation — but flattens findings
into one prose `detail` string (`TemplateInstantiationRest.java:216-227`).
This design upgrades the problem-JSON **additively** (row FORM-422-FIELDS):

```json
{
  "type": "/problems/template-instantiation.unprocessable",
  "title": "SHACL validation failed",
  "status": 422,
  "detail": "DataObject violates the template's SHACL shape (2 violations).",
  "violations": [
    {
      "path": "urn:btkvs:docket:id",
      "value": "123",
      "constraint": "http://www.w3.org/ns/shacl#PatternConstraintComponent",
      "message": "Value does not match pattern ^[A-Z][0-9]{3}$"
    },
    {
      "path": "urn:btkvs:docket:project",
      "constraint": "http://www.w3.org/ns/shacl#MinCountConstraintComponent",
      "message": "Required value is missing"
    }
  ]
}
```

`violations[].path` is the SHACL `resultPath` — the same predicate IRI the
descriptor's `fields[].path` carries, so the client's mapping is a dictionary
lookup, no heuristics. `JenaShaclValidator.Report.findings()` already
exposes `resultPath`/`value`/`message`; the change is wire-shape only.

### 5.3 D4 — one discovery for both directions

The UX audit specifies `GET /v2/shapes/render/applicable?focusAppId=…`
(views only). This design generalises the path to
**`GET /v2/shapes/applicable?focusAppId=…`** — pre-production, paths rename
freely per 191 §8; if SHAPES-APPLICABLE-1 lands first under `/render/…`, the
FORM mode is added there and the path renamed in the same lockstep PR. The
response is the audit's contract plus the discriminator:

```json
{
  "focusAppId": "019e73b4-a737-72b0-ac11-ba1064bb90fc",
  "applicable": [
    {
      "mode": "VIEW",
      "templateAppId": "…", "title": "Docket record (one-pager)",
      "renderer": "structured-record", "readiness": "READY",
      "prefill": { "templateAppId": "…", "focusShepardId": "…" }
    },
    {
      "mode": "FORM",
      "templateAppId": "019f1234-…", "title": "Record a Pyrolysis step",
      "readiness": "READY",
      "formUrl": "/v2/templates/019f1234-…/form?focusAppId=019e73b4-…"
    }
  ]
}
```

FORM applicability rules (server-side, same graph walk as VIEW): the
template is a non-retired data-kind template with a `shapeGraph`, not
`urn:shepard:form:hidden`, in the Collection's allow-list (when non-empty),
and contextually matched (a `btkvs:process-step` DATAOBJECT_RECIPE is
applicable on a `btkvs:docket-root` focus; a STRUCTURED_RECIPE on the DO
kinds its shape targets). One `ActionMenuButton.vue` (the audit's
`ViewMenuButton`, renamed) renders both groups — *"View as…"* / *"Record
a…"* — closing the operator's "one coherent shapes UX" requirement with a
single component on DataObject, Collection, reference, and container detail
pages.

### 5.4 What explicitly does NOT change

`POST /v2/shapes/render` is untouched: no `Accept: application/schema+json`
flavour, no form awareness. Render stays the output-direction surface;
adding a write-direction flavour would re-couple the two contracts D1 just
separated. `POST /v2/shapes/validate` is reused as-is by any client wanting
pre-submit validation without side effects (the Streamlit demo's live
field-validation path).

---

## §6 D5 — Excel ↔ JSON round-trip on the shape spine (contract level)

The same shape that renders the form drives both Excel directions via the
`urn:btkvs:cell-mapping` / `urn:btkvs:sheet` domain hints (§4.2). All
implementation is BTKVS-C2 inside `shepard-plugin-btkvs-docket`; this
section fixes the contract.

**Export** — `GET /v2/btkvs/dockets/{appId}/export.xlsx` → walks the
docket's flattened shape(s), reads instance values from the decomposed graph
(DO attributes + StructuredDataReference payloads), writes each value to its
mapped cell over the `Empty.xlsx` template workbook. Server-side **Apache
POI** — retiring `docket_to_excel.py`'s xlwings + requires-Excel-installed
dependency outright. Conditional cell choices (the fiber-type branch at
`docket_to_excel.py` C16/H16/C17) are expressed as `visibleWhen`-guarded
sibling property shapes, each with its own cell mapping — the conditional
lives once, in the shape.

**Import** — `POST /v2/btkvs/dockets/import` (multipart `.xlsx`) → reads
cells per mapping → builds the candidate data graph → **SHACL-validates
against the same shape** (422 with §5.2 `violations[]`, each violation
additionally carrying the offending `cell` so the user can fix the
spreadsheet) → on conformance, decomposes via the BTKVS-A3 path into the
doc-116 graph (linear Predecessor chain, post-analysis child DOs, editor
stamps → `:Activity`).

**What this retires for the BT-KVS group:** `laufzettel-readout`'s
hand-coded mapping table (becomes shape annotations), the xlwings/Excel
runtime dependency (POI), and `docket_version_upgrader` (a schema version
is a template version — retirement flag + inheritance carry v3→v4; the
import endpoint validates against the registered shape and reports exactly
which fields moved). A shape edit IS the format edit, for the web form and
both Excel directions simultaneously. The CEDAR line (O'Connor et al. 2024)
is the peer-reviewed precedent: spreadsheet entry + template-enforced
standards + interactive error-fixing is a published, working pattern.

---

## §7 Persona verdicts

**Role 5 — Research Data Manager (FAIR & archival).** *Approve.* "Shape-driven
forms move metadata quality enforcement to the **point of capture** — the
only place it's cheap. Required-ness (`sh:minCount`) makes completeness
visible while the operator still remembers the answer; `sh:class`-bound
fields over the `urn:btkvs:fiber` vocabulary mean the I in FAIR is satisfied
at entry, not retrofitted. The evidence is quantified: template-guided
metadata lifted retrieval recall from 17.65 % to 62.87 % (Sundaram 2025);
controlled-vocabulary-backed description measurably improves quality and
interpretability (Karimova 2019; O'Connor 2024). And the Excel leg matters
more than the web form: researchers demonstrably prefer spreadsheets —
validating them against the same shape instead of fighting that preference
is the CEDAR lesson. **What I reject:** (1) any free-text fallback field on
a vocabulary-bound property without an explicit 'propose new term' flow —
that's how `attributes`-bag debt was born; (2) draft saves that skip
validation indefinitely — a draft may bypass `minCount`, never datatype or
pattern; (3) shipping the form without surfacing which optional fields a
completeness meter counts — partial completeness must be visible, not
silent."

**Role 2 — Data & Process Ontologist.** *Approve with governance
conditions.* "The hints are **annotation-on-shape, not semantics-in-shape**
— correctly separated. SHACL itself blesses this: `sh:name`, `sh:order`,
`sh:group` are defined as non-validating property-shape characteristics
precisely for UI generation; `dash:editor` 'may simply be ignored by …
all constraint validators' (datashapes.org). The vocabulary-governance call
is right: adopting DASH instead of minting `urn:shepard:form:widget` avoids
a parallel vocabulary that every future renderer would have to map back to
the de-facto standard anyway. Three conditions: (1) the minted residue stays
at three predicates — every future 'just one more hint' goes through the
organizing-ontology manifest review, not ad-hoc minting; (2) `visibleWhen`
must never grow validation semantics — conditional *required-ness* is
`sh:or` in the shape proper, and the meta-shape should flag a `visibleWhen`
whose path doesn't exist in the shape; (3) `urn:btkvs:cell-mapping` is
domain-vendor vocabulary and must not leak into core — a second domain
wanting spreadsheet mapping mints its own or we promote a shared
`urn:shepard:tabular:*` namespace **then**, with a design note, not
preemptively. Pollution verdict: none, provided condition 1 holds."

**Opposing lens** for the main decision: §3.3 (Role 3, API Minimalist,
argued against the form-descriptor endpoint and for client-side compilation;
partially conceded, two wins recorded).

---

## §8 Phased backlog rows (mirrored into `aidocs/16`)

| ID | Item | Size | Notes |
|---|---|---|---|
| **BTKVS-B2** (refined) | **Acceptance: ONE shape — the docket `:general` section — registered + rendered as a working form.** Concretely: (1) author `urn:btkvs:shape:docket-general` via the `ShaclShapeBuilder` DSL incl. DASH editor hints + 2 cell-mappings, register as a `STRUCTURED_RECIPE` `:ShepardTemplate`; (2) `GET /v2/templates/{appId}/form` returns the §5.1 descriptor (needs FORM-DESCRIPTOR-1); (3) a tiny Streamlit demo fetches the descriptor, renders the form, POSTs to the submit target; (4) a pattern-violating Docket-ID round-trips as a §5.2 `violations[]` entry rendered as an inline field error. | S | Gates on FORM-DESCRIPTOR-1 + FORM-422-FIELDS. Supersedes the old "verify `POST /v2/shapes/render` produces a form-friendly response" wording — D3 keeps forms off the render path. |
| **FORM-DESCRIPTOR-1** | `GET /v2/templates/{templateAppId}/form` — descriptor compiler (flatten → Jena parse → DASH default-editor scoring → deterministic JSON; `?focusAppId=` prefill; computed `submit` block; ETag). JUnit round-trip tests against builder-emitted shapes. | M | The one genuinely new backend piece. |
| **FORM-422-FIELDS** | Structured `violations[]` (path/value/constraint/message) on the instantiation 422 problem-JSON; additive next to `detail`. Reuse `JenaShaclValidator.Report.findings()`. Same shape adopted by BTKVS-A3/C1 decompose + Excel import (with `cell` enrichment). | S | Wire-shape only; `TemplateInstantiationRest.java:216-227`. |
| **FORM-DASH-VOCAB** | Seed `dash.ttl` + the `shepard-ui:*Editor` individuals + the 3 `urn:shepard:form:*` predicates into the internal semantic repo; `form-hints-meta.shacl.ttl` sibling meta-shape; `ShaclShapeBuilder` DSL fields `editor`/`group`/`placeholder`/`visibleWhen`/`cellMapping`. | S | Unblocks hint authoring without hand-written Turtle. |
| **SHAPES-APPLICABLE-FORMS** | Add `mode: VIEW\|FORM` discriminator + FORM applicability rules (§5.3) to the applicable-discovery endpoint; generalise path to `/v2/shapes/applicable`. | S | Pairs with the UX audit's SHAPES-APPLICABLE-1 — land as one endpoint, not two. |
| **FORM-UX-ACTIONBUTTON** | `ViewMenuButton` → `ActionMenuButton` with "View as…" / "Record a…" groups; mount on DO/Collection/reference/container detail. Vitest + Playwright at 4K. | S | Gated on SHAPES-APPLICABLE-1 + -FORMS. The in-context-first rule's form leg. |
| **BTKVS-C2** (sharpened) | Excel round-trip per §6: POI export endpoint + import-validate-decompose endpoint; `violations[].cell` enrichment; retire xlwings/`docket_version_upgrader` equivalents. | M | Contract fixed here; implementation in `shepard-plugin-btkvs-docket` (BTKVS-C1). |

Phasing: FORM-DASH-VOCAB + FORM-DESCRIPTOR-1 + FORM-422-FIELDS →
**BTKVS-B2** (the validating slice) → SHAPES-APPLICABLE-FORMS +
FORM-UX-ACTIONBUTTON (the unified UX) → BTKVS-C1/C2 (the plugin + Excel).

---

## §9 Risks & revisit triggers

1. **The UX audit's three id-shape breaks (UX612-C1/C2/M6) are upstream of
   the unified UX.** Forms don't depend on them (the submit leg is healthy),
   but the *coherence* claim — one button, both directions — lands hollow if
   the VIEW half still 404s. Sequence the audit's fix wave before
   FORM-UX-ACTIONBUTTON.
2. **No-kind decision revisit trigger (named):** a form with no 1:1 data
   shape (cross-entity batch edit, ad-hoc survey). If that arrives,
   reconsider `FORM_RECIPE` *then*, with this doc's drift argument as the
   bar to clear.
3. **Draft/partial-save pressure.** BT-KVS dockets are filled over weeks;
   users will ask for "save half a Pyrolysis step". v1 answer: create the DO
   with status DRAFT and re-open the form `?focusAppId=` for editing —
   validation runs on every submit, required-gaps surface as warnings on
   DRAFT, hard 422 only on status promotion. If that proves insufficient, a
   draft store is an entity-state design, not a template one.
4. **DASH scoring divergence.** Our default-editor scoring must match the
   documented DASH algorithm closely enough that a shape authored in
   TopBraid/shaperone renders comparably in Shepard. Round-trip tests in
   FORM-DESCRIPTOR-1 pin the mapping table; divergences get documented in
   `form-hints-meta.shacl.ttl` comments.
5. **`visibleWhen` expression creep.** The JSON expression language must stay
   at `equals`/`in` on one path. Anything richer (cross-field arithmetic,
   regex) is a signal the constraint belongs in SHACL proper — reject in
   review (Role 2 condition 2).
6. **Streamlit-side adoption risk.** The BT-KVS group keeps their frontend;
   if the descriptor is awkward to consume from Python, they'll keep their
   JSON dicts and the validating slice fails its purpose. Mitigation: the
   BTKVS-B2 demo is written *as* the consumption example, and Nils reviews
   the descriptor shape before FORM-DESCRIPTOR-1 hardens (same
   accept/amend loop as doc 116 §8).

---

## References (added to `docs/_data/references.bib` in this PR)

- DASH Data Shapes — *Form Generation using SHACL and DASH*,
  https://www.datashapes.org/forms.html · vocabulary https://datashapes.org/dash
- shaperone — SHACL-driven form web components,
  https://github.com/hypermedia-app/shaperone · https://forms.hypermedia.app/
- react-jsonschema-form uiSchema (data-vs-UI-schema separation lesson),
  https://rjsf-team.github.io/react-jsonschema-form/docs/api-reference/uiSchema/
- O'Connor, M.J. et al. (2024). *Ensuring Adherence to Standards in
  Experiment-Related Metadata Entered Via Spreadsheets.* Scientific Data.
- Sundaram, S.S. et al. (2025). *Toward total recall: Enhancing data
  FAIRness through AI-driven metadata standardization.* GigaScience.
- Karimova, Y. (2019). *Flexible metadata models and controlled vocabularies
  for research data description in multiple domains.* Bull. IEEE TCDL.
