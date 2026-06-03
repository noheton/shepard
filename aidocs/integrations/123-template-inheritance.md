---
name: ShepardTemplate inheritance — child extends parent, fields flatten with child-override
description: Every ShepardTemplate gains an optional parentTemplateAppId edge; a child template inherits its parent's body fields, reference-creation hints (T1e), iconKey and semantic-annotation defaults, with child entries overriding parent on key collision. Single-parent for v1; cycle-guarded; copy-on-write-versioned.
type: design
stage: feature-defined
last-stage-change: 2026-06-03
---

# 123 — ShepardTemplate inheritance

**Status:** feature-defined 2026-06-03 (operator: *"data object from template
generation — template editor for admin templates should support inheritance"*).
**Pairs with:** `aidocs/54` (first-class template), `aidocs/95`/`aidocs/semantics/98`
(SHACL templates + views-as-shapes), `aidocs/integrations/122` (template icons).

## 0. TL;DR

A `ShepardTemplate` gains a nullable `parentTemplateAppId`. A **child** inherits
the **flattened** definition of its parent (recursively): JSON-DSL body fields,
reference-creation hints (T1e), `iconKey`, and semantic-annotation defaults.
**Child entries override parent** on key collision (last-writer-wins, child wins).
A `TemplateInheritanceResolver` flattens the chain at read- / instantiation-time;
the stored body stays the child's own delta. Single-parent only for v1.
Cycles are rejected at write-time and defended again at flatten-time.

## 1. Reuse survey (mandatory)

Three mechanisms could express "this template extends that one":

### 1.1 SHACL `sh:node` / `sh:and` composition
SHACL already has an inheritance idiom: `sh:node` on a node shape pulls in
(recursively) every property shape of the referenced shape — the spec treats
`rdfs:subClassOf` and `sh:node` "uniformly … sh:node being an extension and
inheritance mechanism similar to subclassing" (TopQuadrant EDG docs; w3.org
SHACL §4.8.1). `sh:and` is the conjunction (all member shapes must hold). This
is the RDF-native equivalent of JSON-Schema `allOf` / XSD `extension`.

**But** — Shepard templates do not store a live SHACL graph that the backend
reasons over. The `body` is an **opaque JSON-DSL string** (`aidocs/54 §7`,
`TemplateBodyValidator`); SHACL only appears as an *optional* embedded
`shapeGraph` Turtle field that the **frontend** extracts for the validation
playground (`SHAPES-V-PREFILL-3-EXTRACT-SHACL`, `frontend/utils/shaclTemplateBody.ts`).
There is **no in-process SHACL engine** on the DO-generation hot path, and the
DO-from-template instantiation reads plain JSON (`dataobjects[0].attributes`),
not RDF. Building a SHACL reasoner into the instantiation path to get
inheritance would be a framework-sized detour for a field-merge problem.

### 1.2 TPL3 upper-ontology class hierarchy
The upper-ontology bootstrap (`aidocs/16` TPL3) implies a *class* hierarchy
(`urn:shepard:*` types with `rdfs:subClassOf`). That hierarchy is about
**semantic typing of instances**, not about **authoring-time recipe reuse**.
A template-inheritance edge is the right grain for the *editor* affordance the
operator asked for ("template editor … should support inheritance"); aligning
template inheritance to the type hierarchy is a future refinement, not v1.

### 1.3 `parentTemplateAppId` edge + flatten resolver — **CHOSEN**
A nullable `parentTemplateAppId` property on `:ShepardTemplate` plus a
`TemplateInheritanceResolver` that walks parent→child and deep-merges. This is
the JSON-Schema `allOf` / XSD `extension` shape expressed at the grain Shepard
already operates on (opaque JSON body, appId-keyed entities, copy-on-write
versioning). It composes *with* §1.1: when a body carries a `shapeGraph`, the
flattener concatenates the parent's `shapeGraph` ahead of the child's, which is
exactly `sh:and` semantics over the two graphs — so a future SHACL consumer
gets composition for free without the backend ever parsing RDF.

### 1.4 The opposing lens — API Minimalist
An API-Minimalist reviewer (`aidocs/agent-findings/api-scrutinizer.md` lens)
would resist a **new edge**: "if SHACL `sh:node` already composes shapes, a
`parentTemplateAppId` is a parallel mechanism — redundancy, the #1 sin."
**Engaged:** the objection is correct *if the substrate were live SHACL*. It is
not — templates are opaque JSON the backend does not interpret as RDF, so
`sh:node` composition is unreachable from the instantiation path without adding
a reasoner (verbosity + wrong-layer, the #4/#5 sins, which dwarf the one-field
cost). The edge is **additive, nullable, one property** — the cheapest possible
shape — and it *enables* `sh:node`-equivalent graph concatenation rather than
competing with it. The Minimalist's own ranking favours the edge here: a single
nullable field that no existing caller must change beats a SHACL engine that
every caller's latency would pay for. We adopt the edge; we do **not** add a
second composition syntax inside the JSON body (no `allOf` array) — the edge is
the *only* inheritance mechanism, which is what keeps it minimal.

## 2. Inheritance semantics

- **Single parent for v1.** Matches Java `extends`, XSD `extension` (single
  base), and most schema systems' default. Multiple inheritance reintroduces
  the diamond / linearization problem (C3, MRO) for no demonstrated authoring
  need. A child needing fields from two parents composes them by making one the
  parent and copying the other's deltas, or we revisit multi-parent in v2 via an
  ordered `parentTemplateAppIds[]` with documented left-to-right precedence.
- **Field-resolution order: parent first, child overrides.** The resolver
  deep-merges the parent's flattened body into a result map, then overlays the
  child's body on top. On key collision the **child wins** (last-writer-wins,
  child last). This is `allOf`-with-override / XSD restriction-on-extension.
- **What is inherited:**
  - **Body field/property definitions** — `dataobjects[0].attributes`,
    `dataObject`, `collection`, `experiment.*`, etc. Deep object-merge per key.
    Arrays merge **positionally**: at each index, two objects deep-merge (overlay
    wins per key), scalars/mixed replace, and base indices the child does not
    cover are retained. This is the rule that lets the canonical
    `dataobjects[0].attributes` shape inherit a parent's attributes while the
    child overrides individual keys — a wholesale array replacement would silently
    drop the parent's other attributes (the failure the resolver test guards).
  - **Reference-creation hints (T1e)** — channel keys, default windows, clip
    bounds, bundle layouts, URI relationship types, schema skeletons. These live
    in the body, so they flatten with the body. A child adds a hint without
    redeclaring the parent's.
  - **`iconKey`** — inherited when the child's is null; child's own value wins.
  - **Semantic-annotation defaults** — body `annotations[]` / `defaults`
    flatten with body merge (parent annotations + child annotations; child
    overrides on same predicate).
- **Cycle prevention.** Write-time: `PATCH`/`POST` rejects with 400 when the
  proposed `parentTemplateAppId` is the template itself or appears anywhere in
  the proposed parent's ancestor chain. Flatten-time: the resolver carries a
  `visited` set and aborts (logged WARN, returns the partial flatten) if a cycle
  is somehow present — fail-soft, never an infinite loop.
- **Copy-on-write interaction.** Templates are COW-versioned (`aidocs/54 §7`):
  each edit mints a new node + retires the prior. `parentTemplateAppId` is a
  *stable* appId reference. Because `findByAppId` resolves a specific node and a
  child's resolver re-reads the parent at flatten-time, **a child created from
  template T sees T's then-current fields at the moment of DO generation**.
  Existing DataObjects already generated are unaffected by a later parent edit —
  they were materialised at generation time (the `:CREATED_FROM_TEMPLATE` edge
  records `templateVersion`). This respects the "templates are copy-on-write
  versioned" contract: editing a parent does not retroactively rewrite children
  or their past instantiations.
- **Parent-of-different-kind.** A child must share its parent's `templateKind`
  (a DATAOBJECT_RECIPE cannot extend a COLLECTION_RECIPE). Enforced at write.

## 3. Migration (additive + nullable)

- **`V110__Template_parent_inheritance.cypher`** — NOOP forward migration
  (Neo4j is schema-less; the additive nullable `parentTemplateAppId` property
  needs no DDL). Documents the property + operator verify query. Optional
  index `CREATE INDEX ... IF NOT EXISTS FOR (t:ShepardTemplate) ON (t.parentTemplateAppId)`
  to make ancestor-walk cheap.
- **`V110_R__Template_parent_inheritance.cypher`** — rollback twin: drop the
  index, strip `parentTemplateAppId` from every `:ShepardTemplate`. Idempotent.
- No backfill: pre-feature templates have a null parent and behave exactly as
  before (the resolver short-circuits when parent is null — returns the body
  unchanged).

## 4. Surfaces (definition of done)

- **Backend:** `parentTemplateAppId` on entity + all three IO shapes (nullable
  `@Schema`); `TemplateInheritanceResolver` (flatten chain, cycle guard,
  deep-merge with child-override); cycle + kind-match guards in
  `ShepardTemplateRest` POST/PATCH; `nextVersionOf` carries the parent ref
  through COW; instantiation flattens before extracting attributes.
- **Frontend:** `AdminTemplateDialog.vue` parent-template picker (appId-keyed,
  scoped to same-kind non-retired templates, self + descendants excluded to
  prevent UI cycles); an **inherited-fields read-only preview** that shows the
  flattened parent contribution distinct from the child's own body — visible in
  *both* basic and advanced mode (advanced is a strict superset). DO-from-
  template wizard pre-fills from the flattened field set.
- **REST/MCP parity:** inheritance rides the existing `/v2/templates` CRUD and
  the `from-template` instantiation endpoint; no new endpoint. The flattened
  view is exposed via a `?flatten=true` query param on `GET /v2/templates/{appId}`.

## 5. Trackers

`aidocs/34` (admin-visible: new nullable template field + V110 migration),
`aidocs/44` (TPL-INHERIT row), `aidocs/42` (user-visible: template inheritance
in the editor). Backlog: `aidocs/16` TPL-INHERIT.
