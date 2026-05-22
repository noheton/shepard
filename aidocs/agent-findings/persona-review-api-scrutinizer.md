---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# API Scrutinizer — review of the `/v2/views` + view-shapes proposal

**Audited.** 2026-05-22. Regenerating a review lost to worktree cleanup.
**Stance.** Best API is the smallest API that solves the problem.
**Critic posture.** Adversarial. Bias toward cuts. Concept bloat is the
default failure mode, not the exception.

> **Surface note (read this first).** The cited design docs —
> `aidocs/semantics/98-mffd-process-shapes.md`,
> `aidocs/platform/100-mffd-views-workspace.md`,
> `aidocs/platform/101-view-shapes-and-spi.md` — **do not exist in
> this tree** (neither on `main`, nor in any worktree, nor in git
> history). The original review presumably critiqued them; the docs
> were lost with the worktree.
>
> This regenerated review is grounded in what does exist and what
> the proposal must be doing if it ships at all:
>
> 1. `aidocs/semantics/95-shacl-templates-and-individuals.md` —
>    Part 2 is explicitly titled "Shapes as views" and proposes that
>    list columns, detail tabs, and filter facets all derive from the
>    same SHACL shape used for the form. The architectural claim
>    (§3) treats this as a defining constraint.
> 2. `aidocs/workflows/54-templates-as-first-class-entity.md` —
>    `:ShepardTemplate` is shipped as the first-class entity with
>    versioning, soft-retire, allow-lists, instantiation provenance,
>    bulk import/export, and a dedicated `/v2/templates/...` surface.
> 3. The shipped `ShapesValidateRest` (`/v2/shapes/validate`,
>    commit `0f535314`) **explicitly disclaims a shape catalog**:
>    *"this endpoint does not maintain a shape catalog — Template
>    (`/v2/templates?kind=view`) carries that."* That decision is
>    already in the tree, as javadoc on a live endpoint.
>
> Given the missing docs, this review attacks the **most defensible
> hostile reading** of `/v2/views` + view-shapes: a new top-level
> resource with its own catalog, its own SPI, its own concept
> vocabulary. If the actual proposal is narrower, the verdict scales
> down accordingly. Either way the §5 path arguments stand.

---

## 1. Verdict — keep / change / cut

### TL;DR

**Cut `/v2/views` as a top-level resource. Fold every view operation
into `/v2/templates` with `?kind=view`.** That decision is already
made on the live `ShapesValidateRest` and in §7 of the templates
design. Re-litigating it via a new resource group is the bug.

| Surface | Verdict | Reason |
|---|---|---|
| `/v2/views` (top-level CRUD) | **CUT** | Duplicates `/v2/templates` with `kind=VIEW`. `:ShepardTemplate` already carries versioning, retire, allow-list, instantiation provenance, bulk import/export, audit — every feature the view registry would want. |
| `ViewShape` as a distinct entity | **CUT** | It's a `:ShepardTemplate` with `templateKind = VIEW_RECIPE` (extend the enum in `aidocs/54 §2.2`). SHACL is the body format. No new node label. |
| `ViewProvider` SPI as a code-shipping plugin extension point | **CHANGE** | If 95% of views are SHACL-driven renderers (Vuetify form + table + facets), there is no SPI seam. See §5 path-A for the manifest-only alternative. |
| "Slicer" / "Persona" / "Workspace" as new top-level concepts | **CUT unless they have at least one non-View consumer.** A concept that only exists because the view system needs it is the view system's internal vocabulary, not a Shepard primitive. |
| `/v2/shapes/validate` | **KEEP** | Already shipped, stateless, single-purpose, correctly disclaims catalog ownership. Exemplar. |
| `/v2/templates?kind=view` as the catalog | **KEEP — but reuse, don't reimplement.** Every operation already has a route in `aidocs/54 §5`. |

The one redundancy that has to go: **the existence of a `/v2/views`
URL prefix at all**, given that `/v2/templates?kind=view` is already
nominated for the catalog role.

---

## 2. Concept census

Concepts the (lost) proposal introduces, reconstructed from the
shipped `ShapesValidateRest` javadoc + the `aidocs/95` framing:

| # | New concept (proposed) | Already exists as | Verdict |
|---|---|---|---|
| 1 | `View` | A render-time projection of a SHACL shape over a target class. Already `aidocs/95 §6` ("Shapes as views"). | **Synonym for `:ShepardTemplate` with `kind=VIEW`.** Do not introduce. |
| 2 | `ViewShape` | A SHACL `sh:NodeShape` whose `sh:targetClass` is the rendered type, with render-hint properties. | **Same shape, same body, same SHACL graph.** A `ViewShape` is not a distinct class — it's the same shape used at render time instead of form time. |
| 3 | `ViewProvider` (SPI) | Plugin interface returning a Vue component bundle for a `(shape, mediaType)` pair. | **Almost certainly not needed.** See §5 path-A. The 80% case is SHACL + a small render-hint vocab + a generic Vuetify renderer. SPI seam justifies itself only if a plugin needs to bypass SHACL entirely (rare; specific). |
| 4 | `Slicer` | A predicate that filters/groups individuals before they reach the view. | **Probably a SHACL `sh:filterShape` or a SPARQL query template.** A new concept name is a tell that the SHACL/SPARQL grammar is being papered over. |
| 5 | `Persona` | A named user archetype with associated default views. | **Already exists** — `Role` (A0, `aidocs/51`) + per-user preferences (`U1c`). A new top-level "Persona" is concept-bloat unless it carries distinct authorisation semantics. |
| 6 | `Workspace` | A grouping of views + filters + state, scoped per user or per Collection. | **Probably should be a saved-search-style entity on the user, not a top-level resource.** Risk: this is `Collection` with a different name. |
| 7 | `ViewBundle` / `ViewComponent` | A deliverable JS/Vue artefact registered against a view. | If kept at all, this is a `:ShepardFile` (existing) referenced from the template body — not a new entity. |

**Total new concepts proposed: 7.**
**Concepts that survive scrutiny: 0 — every one collapses into an
existing primitive.** The two that I would defend if forced
(`Slicer`, `Workspace`) only earn their keep with a real second
consumer outside the view system. If the proposal can't name one,
they're internal vocabulary.

Compare to `aidocs/54 §2.2`'s existing `TemplateKind` enum:
`DATAOBJECT_RECIPE`, `COLLECTION_RECIPE`, `EXPERIMENT_RECIPE`,
`PROCESS_RECIPE`. Adding `VIEW_RECIPE` is one enum value, **not a
new resource group**. The cost of the latter is a JAX-RS resource
class, a service, a Neo4j label, a migration, an OpenAPI tag, a
test fixture, frontend client code, MCP tool generation, docs —
multiplied by every read/write/list/version operation. The cost of
the former is a five-line `enum` edit.

---

## 3. Endpoint census

Reconstructed hostile reading of `/v2/views` — the surface a
reasonable PR author would write if the proposal got merged
as-stated. Verdict against each.

| Method + path | Verdict | Reason |
|---|---|---|
| `GET /v2/views` | **CUT** | Replace with `GET /v2/templates?kind=view`. |
| `GET /v2/views/{appId}` | **CUT** | Replace with `GET /v2/templates/{appId}` (kind discriminator in body). |
| `POST /v2/views` | **CUT** | `POST /v2/templates` with `templateKind: "VIEW_RECIPE"`. |
| `PUT /v2/views/{appId}` | **CUT** | `PUT /v2/templates/{appId}` — copy-on-write versioning already shipped (`aidocs/54 §2.3`). |
| `DELETE /v2/views/{appId}` | **CUT** | `DELETE /v2/templates/{appId}` — soft-retire semantics already shipped. |
| `GET /v2/views/{appId}/usage` | **CUT** | `GET /v2/templates/{appId}/usage` — already specified. |
| `POST /v2/views/import`, `GET /v2/views/export` | **CUT** | `POST /v2/templates/import`, `GET /v2/templates/export` already cover this. |
| `GET /v2/views/{appId}/render?targetAppId=...` | **MERGE → `/v2/shapes/render`** if it ships at all. See §5 path-B; this is the one operation that has a faint case for a new endpoint. |
| `GET /v2/collections/{appId}/views` | **MERGE** → already exists as `GET /v2/collections/{appId}/allowed-templates?kind=view` per `aidocs/54 §5`. |
| `POST /v2/collections/{appId}/views/{viewAppId}` | **CUT** | Allow-list edge already specified for `:ALLOWS_TEMPLATE`. |
| `GET /v2/views/{appId}/component.js` (or similar) | **NEEDS-CLARIFICATION** — see §6. |
| `GET /v2/personas`, `POST /v2/personas/{name}/views` | **CUT** | If personas survive at all (§2 row 5), the binding lives on `User`/`Role`. |
| `GET /v2/workspaces`, `POST /v2/workspaces` | **CUT** | If `Workspace` survives at all, defer until §2 row 6 has a non-view consumer. |

**Proposed new endpoints under the hostile reading: ~13.**
**Endpoints that survive: 0 or 1 (`/v2/shapes/render`, conditional).**

---

## 4. Leaky-abstraction list

Things that would leak through a hastily-built `/v2/views` surface:

1. **SHACL property paths in JSON responses.** A view body that
   serialises `sh:path :gaNumber` as a raw IRI string forces the
   caller to know SHACL syntax. The frontend either parses Turtle
   (large dependency) or the backend pre-projects each property
   into a `{ name, label, datatype, widget }` row. **Pick one,
   document it.** Don't do both. (The shipped `/v2/shapes/validate`
   IO uses raw Turtle, which is acceptable for a developer-facing
   pre-flight check but **not** acceptable as a rendering payload
   for the UI.)
2. **Jena report nesting.** `JenaShaclValidator.Report` already
   leaks Jena's `Resource` / node abstractions into
   `ShapeValidationReportIO` unless the IO actively flattens them.
   Read the wire output before adopting it as the rendering payload.
3. **Neo4j IDs.** Any `viewId: long` field in a response is the
   `aidocs/agent-findings/api-scrutinizer.md` finding #1 repeating
   itself. The `:ShepardTemplate` design (`aidocs/54 §2.1`) already
   uses `appId` — reuse it; do not invent a `viewId` long.
4. **`appId` vs `shepardId`.** Per
   `feedback_appid_to_shepardid.md`: the Phase-1 rename is in
   flight. **Any new endpoint MUST emit `shepardId` on the wire.**
   A `/v2/views` resource shipped today with `appId` in its IO is a
   pre-broken endpoint — it'll need a Phase-1-style additive
   `@JsonProperty("shepardId")` on the IO subclass within the same
   release. Cleaner to not ship the resource and fold into
   `/v2/templates` (which gets the rename for free as part of the
   Phase-1 sweep).
5. **SHACL focus-node concept in URL.** If a "render this view
   against that individual" call materialises as
   `/v2/views/{appId}/render?focusNode=<IRI>`, the caller has to
   understand SHACL focus-node semantics to use it. The shepard
   primitive — a DataObject's `appId` — should be enough.
   Translate it server-side.
6. **Vue component registration via classpath service-loader.**
   A `ViewProvider` SPI that returns a `Class<? extends VueComponent>`
   confuses the deployment unit: the backend is Java, the
   component is shipped as built JS. The seam needs to be a URL or
   an opaque blob, not a Java type.

---

## 5. Arguments for different paths

### Path A — `/v2/views` as standalone resource (the proposal as written)

Pros:
- Discoverable in the OpenAPI listing as a dedicated tag.
- Allows view-specific operations (e.g. `/render`) to live on a
  view-shaped URL.
- Lets the view team ship without touching the templates module.

Cons:
- Duplicates `:ShepardTemplate`'s versioning, retire, allow-list,
  import/export, instantiation provenance, audit (`aidocs/54
  §2.3–§5, §10`). Every feature wished for the view registry is
  already specified and (per task tracker) being implemented.
- The shipped `ShapesValidateRest` javadoc (commit `0f535314`)
  **explicitly tells future maintainers** that the catalog lives
  at `/v2/templates?kind=view`. A `/v2/views` resource directly
  contradicts a documented decision already in the tree.
- Concept bloat: introduces `View` + `ViewShape` + `ViewProvider`
  + `Slicer` + `Persona` + `Workspace` for capabilities the
  existing primitives already express.
- Forces the MCP tool author to generate two different
  catalog-listing tools that do the same thing.
- Violates `feedback_ontology_first.md`: "before naming a new
  plugin / endpoint / table / SHACL shape, check what the
  ontology already declares about this."

### Path B — fold into `/v2/templates` (recommended)

Pros:
- One catalog, one entity, one set of versioning + retire + audit
  + allow-list code paths.
- Five-line `TemplateKind` enum edit covers the registry need.
- Honours the decision already shipped on
  `ShapesValidateRest.java`.
- The MCP catalog tool is `templates.list(kind: "VIEW_RECIPE")` —
  consistent with `kind: "DATAOBJECT_RECIPE"`.
- One operation has a real case for a dedicated URL —
  **render** — which can live on `/v2/shapes/render` (next to
  `validate`) rather than `/v2/views/{appId}/render`. Stateless,
  takes a template `appId` + a focus-node `shepardId`, returns
  the projected wire shape the frontend renderer wants. **One
  new endpoint, not thirteen.**

Cons:
- Slightly less discoverable under an OpenAPI tag called "Views";
  partly fixed by tagging the kind-filtered template endpoints
  with both `Templates (v2)` and `Views (v2)` (microprofile
  OpenAPI supports multi-tag).
- The "view" team has to coordinate with the "templates" team.
  This is a feature, not a bug — `:ShepardTemplate` is the unit
  of authorship and there should be one owner.

**Resolution.** Path B. Land `/v2/shapes/render` (stateless, like
`/v2/shapes/validate`) and `TemplateKind.VIEW_RECIPE`. Stop there.

### Path C — `ViewProvider` SPI as Java interface vs JSON manifest

Pros of a Java SPI:
- Type-safe registration; CDI-discoverable; matches the existing
  `PayloadKind` / `FileStorage` / `Minter` SPI family
  (`aidocs/47 §2`).

Cons of a Java SPI for this specific concern:
- A view is a frontend artefact. Java code can't render Vue. The
  SPI either (a) returns a URL pointing at a JS bundle (in which
  case the manifest is the actual payload — the Java class is
  ceremony) or (b) returns a server-side projection function (in
  which case it's better expressed as SHACL render-hints).
- Encourages plugins to bypass SHACL with bespoke render code,
  which fragments the rendering surface. The whole point of
  `aidocs/95 §6` is that the same shape drives form + view + agent.
  An SPI that lets plugins ship a non-SHACL view path defeats the
  architectural claim.

Pros of a JSON manifest:
- Declarative; no plugin code to ship for the 95% case.
- Render-hint vocab is editable at runtime (templates are runtime
  entities; `aidocs/54`).
- Plugin still has an escape hatch: a manifest field
  `customRenderer: { url: "..." }` lets a power plugin ship its
  own JS bundle when SHACL hints aren't enough. URL → frontend
  fetches with SRI digest verification (§6 clarification).

**Resolution.** Manifest-only by default. The Java SPI seam,
if it ships at all, exists only to register **new render-hint
vocabularies** (e.g. a chemistry plugin adds `chem:moleculeWidget`)
— not to ship arbitrary Vue code. That's an `OntologyContributor`
SPI, not a `ViewProvider` SPI; it already exists in the ontology
preseed plumbing (`aidocs/65`).

### Path D — view-component delivery: SRI URL vs opaque blob

Pros of an opaque blob (`GET /v2/views/{appId}/component.js`):
- Self-hosted; no external dependency.
- Auditable: instance admin can scan what code is served.

Cons of an opaque blob:
- The backend becomes a JS asset CDN.
- Storage layer (the actual binary) needs a home — either inside
  `:ShepardTemplate.body` (large blobs in a JSON field — bad) or
  in `:ShepardFile` referenced by the template (better, but now
  there's an indirection chain Template → File → MinIO → wire).
- Versioning of the component vs the shape becomes a coupled
  rebuild problem.

Pros of an SRI URL (`integrity: "sha384-..."` in the manifest):
- Aligns with how the frontend already loads dependencies
  (`nuxt.config.ts` lists CDN URLs with SRI hashes).
- Self-hosting is opt-in (set the URL to your own instance).
- No new asset-serving endpoint in core.

Cons of an SRI URL:
- Air-gapped instances need a self-host option (mirror the bundle
  via `:ShepardFile`).

**Resolution.** SRI URL by default, with a fallback path
`GET /v2/files/{appId}/raw` (already exists) for air-gapped
mirrors. No new asset endpoint.

---

## 6. `[NEEDS-CLARIFICATION]` blocks

```
[NEEDS-CLARIFICATION] Does `/v2/views` introduce a new catalog,
                      or is it a thin facade over `/v2/templates`?
  Context: ShapesValidateRest.java javadoc (commit 0f535314) says
           "Template (/v2/templates?kind=view) carries [the
           catalog]." If `/v2/views` is the catalog, that decision
           is reversed; if it's a facade, why does it exist?
  Options:
    A) No /v2/views resource at all — view operations live on
       /v2/templates with kind=VIEW_RECIPE. — pro: single source
       of truth, honours the shipped decision; con: callers must
       filter by kind to discover views.
    B) /v2/views as a read-only facade — GET only, delegates
       to /v2/templates. — pro: discoverability with no
       duplication; con: redirect or response-shape mismatch
       risk; deprecation cost when the facade goes away.
    C) /v2/views as the real catalog, /v2/templates loses
       VIEW_RECIPE. — pro: clear ownership; con: contradicts
       shipped doc + duplicates :ShepardTemplate machinery.
  Lean: A — the cheapest correct shape. The discoverability cost
        is fixed by multi-tagging in OpenAPI.
```

```
[NEEDS-CLARIFICATION] Is `ViewProvider` a Java SPI shipping Vue
                      code, or a manifest-only registration?
  Context: aidocs/47 §2 lists SPI family (PayloadKind,
           FileStorage, Minter). A ViewProvider that returns Vue
           components fits the pattern syntactically but breaks
           the deployment-unit boundary (Java backend can't
           render JS frontend).
  Options:
    A) No SPI; views are SHACL + render-hint manifest. Custom
       JS shipped as a fallback URL in the manifest with SRI.
       — pro: no plugin code for the 80% case; con: harder
       to enforce shape conformance for custom renderers.
    B) SPI registers render-hint vocabularies only (extends the
       SHACL vocab; Java code never renders). — pro: matches
       OntologyContributor pattern; con: a misnomer to call it
       ViewProvider.
    C) Full Java SPI with classloader-shipped Vue artefacts.
       — pro: type-safe; con: fragments rendering, defeats
       ontology-driven-UI claim, deployment-unit confusion.
  Lean: A — manifest-only. If a vocabulary extension is needed,
        that's the existing ontology-contribution path, not a
        ViewProvider SPI.
```

```
[NEEDS-CLARIFICATION] Do "Slicer", "Persona", "Workspace"
                      survive contact with §2's "one non-view
                      consumer" test?
  Context: feedback_ontology_first.md says check existing
           primitives before introducing a new concept. Each of
           these has at least one existing close-cousin in the
           tree (SHACL filter shape; Role + user preferences;
           saved-search).
  Options:
    A) Cut all three. View system reuses SHACL filter shapes
       for slicing, Role + per-user prefs for persona, and
       defers Workspace until a non-view consumer asks. —
       pro: minimum surface; con: view system has to express
       its needs in SHACL terms.
    B) Keep one, cut two. Persona has the strongest "real
       primitive" case (matches Role semantics); Slicer and
       Workspace are internal vocabulary. — pro: pragmatic;
       con: Persona is mostly a synonym for Role + defaults.
    C) Keep all three. — con: 3× concept bloat; risk of
       wrong-default lock-in.
  Lean: A — the test should be strict. The view system can
        re-propose any of these later with a real second
        consumer.
```

```
[NEEDS-CLARIFICATION] Where does `/render` live?
  Context: A "render this shape against this individual" call
           is the one operation with a faint case for a new
           endpoint (not covered by /v2/templates CRUD).
  Options:
    A) POST /v2/shapes/render — stateless, sibling to
       /v2/shapes/validate. Takes templateAppId +
       focusShepardId, returns the projected wire payload.
       — pro: matches the validate pattern; stateless;
       cacheable.
    B) GET /v2/templates/{appId}/render?focusShepardId=...
       — pro: lives on the template; con: GET with a stateful
       projection that can be expensive — questionable HTTP
       semantics.
    C) GET /v2/data-objects/{shepardId}/rendered?templateAppId=...
       — pro: caller-resource-shaped URL; con: cross-resource
       coupling; harder to discover from the template side.
  Lean: A — keep render-time concerns on /v2/shapes/ with
        validate, both stateless, both side-effect-free, both
        consume the same Turtle. One operation, not thirteen.
```

```
[NEEDS-CLARIFICATION] Do view shapes get a SHACL-targeting
                      escape hatch?
  Context: aidocs/95 §6 implies the same shape drives form +
           view. But a view typically needs read-time fields
           the form doesn't (computed properties, derived
           aggregates, summary stats). SHACL has no native
           "compute" operation.
  Options:
    A) Computed fields live in a separate "view-shape"
       (different sh:NodeShape, same targetClass) layered on
       top via sh:and. — pro: one entity, two shapes; con:
       still needs a render-hint vocab for the computation
       semantics.
    B) Computed fields are SPARQL CONSTRUCT queries embedded
       in the template body. — pro: powerful; con: SPARQL on
       hot-path render; query injection / authorisation surface.
    C) Computed fields are pre-materialised projections in
       Postgres (existing pattern). — pro: fast; con: stale-
       cache problem; new write path on every shape edit.
  Lean: A for the 90% case (label/group/order/widget hints);
        defer B/C until a real use case forces it.
```

---

## 7. Top 3 cuts that make the surface ship-able

1. **Delete the `/v2/views` URL prefix from the proposal.** Every
   CRUD operation on a view is already specified on `/v2/templates`
   with `kind=VIEW_RECIPE`. The shipped `ShapesValidateRest`
   javadoc already encodes this decision. A new resource group is a
   directly contradictory commit.

2. **Cut `Slicer`, `Persona`, `Workspace` from the v1 design.**
   Each is either (a) a synonym for an existing primitive (SHACL
   filter shape; Role + user prefs; saved-search) or (b) internal
   vocabulary of the view system. Re-propose with a real second
   consumer or not at all.

3. **Replace `ViewProvider` SPI with a manifest-only
   registration.** SHACL + a small render-hint vocab + a generic
   Vuetify renderer covers 95%. Plugin escape hatch is an SRI URL
   field in the manifest, not a Java service-loader hook. Aligns
   with `aidocs/47 §2`'s deployment-unit semantics and avoids
   shipping JS bundles through a Java classloader.

After these three cuts the surface added by the view system is:

- One enum value: `TemplateKind.VIEW_RECIPE` in
  `aidocs/54 §2.2`.
- One new endpoint: `POST /v2/shapes/render` (stateless, sibling
  of `/v2/shapes/validate`).
- One render-hint vocabulary contribution to the shipped ontology
  bundle.

That's the smallest API that solves the problem. The rest is
reuse. If the proposal needs more than this, the burden of proof
is on the proposer to name the consumer the existing primitives
can't serve.

---

## Appendix — pre-shipped decisions this review leans on

| Decision | Source |
|---|---|
| `:ShepardTemplate` has versioning, retire, allow-list, instantiation prov, import/export | `aidocs/workflows/54-templates-as-first-class-entity.md §2–§5` |
| `/v2/shapes/validate` is stateless and does not maintain a catalog; catalog lives at `/v2/templates?kind=view` | `backend/src/main/java/de/dlr/shepard/v2/shapes/resources/ShapesValidateRest.java` javadoc (commit `0f535314`) |
| Shapes drive form, view, and agent contracts from the same SHACL graph | `aidocs/semantics/95-shacl-templates-and-individuals.md §6` |
| `appId` → `shepardId` rename Phase 1 is on the v2 wire; v1 IO MUST stay byte-fidelity | `feedback_appid_to_shepardid.md`, `b943b1c5` |
| Ontology-first: check existing primitives before introducing a new concept | `feedback_ontology_first.md` |
| `feedback_agent_clarify_first.md` — emit options + lean, never bare questions | this review's §6 |

---

*— API Scrutinizer*
