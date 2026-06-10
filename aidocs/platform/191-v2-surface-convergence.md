---
name: v2 surface convergence — minimalist core, shapes + kinds, no bespoke endpoints
description: Target architecture converging the 232-endpoint /v2 surface onto a minimalist generic core (kind-discriminated references/containers + shapes/render + templates), file-format subtypes on FileReference, templates-as-SHACL-shapes with a JSON→SHACL compiler, a MAPPING_RECIPE template kind that dissolves the scene-graph and KRL namespaces, content-negotiated render output, a registry-driven admin config surface, and a narrow plugin-namespace allowlist (Jupyter, AAS) whose endpoints appear/disappear from OpenAPI with the plugin.
type: design
stage: feature-defined
last-stage-change: 2026-06-10
---

# 191 — v2 surface convergence

**Status:** feature-defined 2026-06-03. Operator-driven (endpoint review →
"highly extendable with minimalist core"). Supersedes the loose framing in the
endpoint-review chat; this is the SSOT.

## 0. TL;DR

The `/v2` surface has grown to **232 endpoints**. Domain verticals
(`thermography`, `svdx`, `krl`, `scene-graph`) live in core as bespoke REST
resources, rendering is done per-format, references/containers have a path per
kind, and the ~5 admin `:*Config` singletons are copy-paste. This doc converges
all of it onto seams that **mostly already exist**:

1. **One generic core surface.** References and containers are addressed
   generically with a `?kind=` discriminator; the kind selects the payload
   *family* (`file` / `timeseries` / `structured` / `uri`).
2. **File-format subtypes.** `krl`, `svdx`, `otvis`, `urdf`, `xit`, … are
   **`fileKind` subtypes of the single `FileReference`** — not new entities,
   families, or paths. A new filetype = a new `fileKind` + one format plugin.
3. **Templates ARE SHACL shapes.** A `ShepardTemplate` is authored as a thin
   predicate-IRI JSON DSL that **compiles to canonical SHACL**; the shape drives
   validation, create-form generation, rendering, and the agent contract.
4. **A new `MAPPING_RECIPE` template kind** derives outputs from existing
   references — dissolving the scene-graph and KRL namespaces into shapes.
5. **Rendering goes only through `/v2/shapes/render`** (content-negotiated:
   JSON view-model | PNG | glTF | URDF/USD). No per-format render endpoints.
6. **Admin consolidates** behind a registry-driven `/v2/admin/config/{feature}`.
7. **Plugins add no namespace** — except a narrow allowlist (**Jupyter**, **AAS**)
   whose paths appear/disappear from OpenAPI with the plugin's enable state.
8. **No v2 back-compat** — the fork's `/v2/` is pre-production; paths are
   replaced in place, frontend updated in lockstep. The frozen `/shepard/api/`
   upstream byte-compat surface is untouched.

## 1. Reuse survey (what already exists)

Per the project's reuse-before-reimplement rule, almost every seam is built:

- **Renderer SPI** — `ViewRecipeRenderer` (ServiceLoader, keyed by SHACL shape
  IRI) + `ViewRecipeRendererRegistry`; `POST /v2/shapes/render` already
  dispatches through it with **no core edits** to add a renderer
  (`backend/.../spi/view/ViewRecipeRenderer.java`,
  `.../spi/view/ViewRecipeRendererRegistry.java`,
  `.../v2/shapes/resources/ShapesRenderRest.java:185-201`).
- **PayloadKind SPI** — `.../spi/payload/PayloadKind.java`, ServiceLoader,
  discovered in `NeoConnector.connect()`. Existing kinds: Aas/Git/Hdf/
  Spatiotemporal/Video.
- **Plugin manifest** — `.../plugin/PluginManifest.java` (`onRegister/
  onUnregister`); the Jandex aggregator hang is fixed (commit `bd168e011`).
- **SHACL validation** — Apache Jena `JenaShaclValidator` behind
  `POST /v2/shapes/validate` (stateless: dataTurtle + shapeTurtle → report).
- **Templates already SHACL-capable** — `ShepardTemplate.body` reserves an
  optional `shapeGraph` (Turtle) field (`ShepardTemplateIO.java:34-42`);
  inheritance concatenates parent→child `shapeGraph` as `sh:and`
  (`TemplateInheritanceResolver:152-153`); the just-shipped `parentTemplateAppId`
  edge is the `sh:node`-equivalent (aidocs/integrations/123 §1.4).
- **One predicate source** — templates + the annotation picker both draw from
  `:Vocabulary`/`:Predicate` nodes + the `predicate_vocabulary` table (51+
  predicates, substrate + cardinality) + QUDT units + the meta-grammar in
  `backend/src/main/resources/shapes/view-recipe-meta.shacl.ttl`.
- **Designed intent** — aidocs/semantics/95 ("shapes as templates / views /
  agent contracts") + aidocs/semantics/98 (views-as-shapes) already name this
  end-state.

What is **missing** (the genuinely new pieces): a **structured-input → SHACL
shape-builder** (inverse of the validator; no programmatic generator exists
today), the **`MAPPING_RECIPE`** kind + a **`TransformExecutor`** SPI, the
**content-negotiated media** path on render, the **admin `ConfigRegistry`**, the
**`fileKind` discriminator** on FileReference, and the **plugin-namespace
allowlist + OpenAPI `OASFilter`**.

## 2. Target architecture — three REST tiers

### Tier 1 — Core generic surface (kind-agnostic; grows slowly)
- Identity/graph (unchanged): `/v2/collections`, `/v2/data-objects`,
  `/v2/projects`, `/v2/templates`, `/v2/annotations`, `/v2/provenance`,
  `/v2/snapshots`, `/v2/semantic/*`, `/v2/shapes/*`.
- **Unified references** — `/v2/references` with a `kind` discriminator
  (the *payload family*): `POST /v2/references?kind={family}`,
  `GET|PATCH|DELETE /v2/references/{appId}` (entity self-describes its kind),
  `GET /v2/references?kind={family}&dataObjectAppId=…`. Replaces
  `timeseries-references`, `uri-references`, `video-stream-references`,
  singleton `files`, `git-references`.
- **Unified containers** — `/v2/containers?kind={family}` (+ `/{appId}`)
  replacing `file-containers`, `timeseries-containers`,
  `structured-data-containers`, hdf.
- **Unified admin config** — `/v2/admin/config/{feature}` (§6).
- Already-generic kind-templated surfaces stay: `/v2/{kind}/{appId}/publish|publications`.

### Tier 2 — Kind plugins (default; **no namespace**)

**Two distinct levels of "kind":**
- **Payload family** (`PayloadKind`, existing SPI): `file`, `timeseries`,
  `structured`, `uri`, … — the storage substrate + reference/container shape;
  the `?kind=` discriminator. We do **not** add a family per file format.
- **File-format subtype** (`fileKind`, on a `FileReference`): `krl`, `svdx`,
  `otvis`, `urdf`, `xit`, `pdf`, … detected at upload (mime/extension/sniff),
  overridable. **This is the extensibility seam.** A new filetype = a new
  `fileKind` + one format plugin. No new entity/family/path.

So **krl / svdx / otvis / urdf / xit are subtypes of `FileReference`**, reached
as `/v2/references?kind=file` (optionally `&fileKind=urdf`); the ref
self-describes its `fileKind`. A format plugin contributes behavior via
ServiceLoader SPIs, zero new paths:
- **`FileFormatPlugin`** (generalizes the existing `FileParserPlugin`):
  `accepts(mime, filename)` → claims a `fileKind`; `parse(...)` →
  SemanticAnnotations on upload; optional `renderer()` (a `ViewRecipeRenderer`)
  and `transform()` (a `TransformExecutor`).
- **`ViewRecipeRenderer`** — SHACL shape IRI(s) → viewing flows through
  `/v2/shapes/render`.
- **`TransformExecutor`** (NEW) — backs a `MAPPING_RECIPE` materialization
  (e.g. the KRL sidecar). Refs in → derived ref/view out.

Consumers: **otvis, svdx, krl, urdf, xit** as `fileKind` subtypes; **video,
hdf, spatial** as their own payload families (they genuinely are a stream /
container / container).

### Tier 3 — Namespace-extending plugins (allowlisted, rare)
A plugin may own a path prefix only when it genuinely cannot fit the kind model:
- **Jupyter** — sidecar integration/config/proxy (`/v2/jupyter/*`).
- **AAS** — `/v2/aas/shells/...` mirrors the external **IDTA AAS REST** standard.

Opt-in via a `RestNamespaceContributor` marker on the `PluginManifest` declaring
the prefix. **Enable/disable is load-bearing:**
- A `ContainerRequestFilter` 404s the prefix when the plugin is disabled (reads
  the plugin/feature registry).
- A MicroProfile **`OASFilter`** strips the disabled plugin's paths from
  `/shepard/doc/openapi/v2.json` — so the spec reflects only enabled plugins.
  (Operator requirement: "a disabled AAS plugin also removes the endpoints from
  OpenAPI.")

## 3. Templates ARE shapes — the unifying spine

**A `ShepardTemplate` is a SHACL shape**, authored as a thin **JSON DSL whose
property entries reference vocabulary predicate IRIs**, deterministically
**compiled** to the canonical `shapeGraph`. Minimalist (plain-JSON write path,
no reasoner) + semantic-driven (every field is a vocabulary IRI). SHACL is the
derived validation/interchange/agent projection; inheritance-merge stays on the
JSON.

**One shape, five uses:**
| Use | Consumption |
|---|---|
| **Data** (DATAOBJECT/COLLECTION/…) | constrains entity properties → validate at instantiation + generate create-form |
| **View** (VIEW_RECIPE) | shape IRI → `ViewRecipeRenderer` (live) |
| **Mapping/Transform** (NEW `MAPPING_RECIPE`) | binds existing references → a derived ref or animated view (§4) |
| **Kind contract** (plugin) | a kind/format ships its data-shape + view-shape |
| **Agent contract** | the same shape is the machine-readable MCP/LLM contract |

**Two new backend pieces:**
1. **SHACL shape-builder** — `predicate-IRI JSON DSL → Turtle`. Round-trip
   tested against the existing hand-authored `.ttl`. Powers the editor and lets
   a kind/format declare its shape programmatically.
2. **Validation at instantiation** — close the dormant seam at
   `TemplateInstantiationRest:160-168`: after flattening the inherited shape,
   validate the to-be-created instance and 422 on violation.

**Template editor (the "great case"):** primitives palette from
`/v2/semantic/terms/search`, `/v2/semantic/vocabularies/{id}/predicates`,
`/v2/shapes/predicates` (predicates, datatypes, QUDT units, cardinalities,
value-sets — the same palette the annotation picker uses); compose property
shapes (path, datatype, min/maxCount, `sh:in`, nested `sh:node`); set the parent
template (inheritance, shipped) with inherited-vs-own preview (shipped in
`AdminTemplateDialog`); emit `shapeGraph` via the builder; the saved shape then
drives create-form generation, instantiation validation, and rendering — author
once, used five ways.

## 4. MAPPING_RECIPE — scene-graph and KRL dissolve

`MAPPING_RECIPE` is a template kind whose shape **binds existing references to a
derived output**, materialized through a generic path optionally backed by a
plugin `TransformExecutor`. It absorbs both verticals (operator's design):

- **Scene-graph** = a `MAPPING_RECIPE` **view-shape**: `urdfFileAppId`
  (`fileKind=urdf` FileReference; kinematic tree parsed on demand by its format
  plugin) + `jointTimeseriesAppId` (joint channels) + a channel→joint binding,
  rendered by the Trace3D-family renderer with play/scrub. **The
  `/v2/scene-graphs/*` namespace and the stored frames/joints graph go away.**
  `export.urdf/.usd` become renderers. Manual frame authoring folds into editing
  the URDF file.
- **KRL** = a `MAPPING_RECIPE` **transform-shape** bound to a `fileKind=krl`
  FileReference ("a FileReference on functional steroids"): inputs
  `srcFileAppId` + `urdfFileAppId` → derives a `TimeseriesReference` (joint
  trajectory). The KRL sidecar is the `TransformExecutor`. **No
  `/v2/krl/interpret`.**

## 5. Rendering — binary output via content negotiation

`/v2/shapes/render` gains media output (no per-format endpoints):
- `ViewRecipeRenderer` gains `Set<MediaType> producibleMedia()`.
- `RenderResponse` carries an optional `(mediaType, bytes|stream)`.
- `/v2/shapes/render` honours `Accept:` → `application/json` (view-model) |
  `image/png` (thermography heatmap) | `model/gltf+json` | URDF/USD.

## 6. Admin consolidation — registry-driven

- `ConfigDescriptor<T extends HasAppId>`: `featureName()`, `entityType()`,
  `service()`, `jsonSchema()` (validation + OpenAPI), `patchableFields()`.
- `@ApplicationScoped ConfigRegistry` holds descriptors; each `*ConfigService`
  registers on `@Observes StartupEvent`.
- `GET|PATCH /v2/admin/config/{feature}` resolves the descriptor, delegates to
  the service, centralizes role-check + RFC-7396 merge-patch + problem-JSON.
- Existing `:*Config` entities/services stay; the bespoke `*ConfigRest` classes
  collapse into the generic one. `ProvenanceCaptureFilter` capture and
  `@RolesAllowed("instance-admin")` survive unchanged. Old
  `/v2/admin/<feature>/config` paths deleted (pre-prod); admin panes repoint.

**Shipped 2026-06-03 (V2CONV-A4).** `ConfigDescriptor<T>` SPI +
`@ApplicationScoped ConfigRegistry` (CDI-bean discovery at `StartupEvent`,
fail-soft) + `ConfigPatchException` under
`de.dlr.shepard.v2.admin.config.spi`; generic `AdminConfigRest` adds
`GET /v2/admin/config` (feature listing) alongside the `{feature}` GET/PATCH.
Four descriptors landed — `ror`, `sql-timeseries`, `jupyter`, `semantic`. The
bespoke `SemanticConfigRest` / `SqlTimeseriesConfigRest` / `InstanceRorConfigRest`
/ `JupyterConfigRest` (shim) / `JupyterConfigPluginRest` (canonical) are deleted;
the `:*Config` entities/services and the public `GET /v2/jupyter/config` are
unchanged. Patch body is taken as a raw `JsonNode` (preserves RFC-7396
absent/null/value tri-state without a typed DTO; the built-in Quarkus
merge-patch filter is avoided per quarkus#33186/#37980). 43 JUnit tests;
new classes 75–100 % line coverage. A new configurable feature now needs only a
descriptor bean — no new REST class. See
`aidocs/agent-findings/v2conv-a4.md`.

## 7. Core → plugin extractions

| Core today | Move to | New shape |
|---|---|---|
| `v2.thermography.*` | `plugins/fileformat-thermography` | `fileKind=otvis` → `FileFormatPlugin` (analyze on upload) + `ViewRecipeRenderer` (heatmap PNG) |
| `v2.svdx.*` | `plugins/fileformat-svdx` | `fileKind=svdx` → `FileFormatPlugin` (parse `.svdx`+`.csv` → timeseries on upload); no REST |
| `v2.scenegraph.*` | **dissolved** | `MAPPING_RECIPE` view-shape (urdf + joint TS → played Trace3D); `/v2/scene-graphs/*` deleted |
| `v2.krl.*` | `plugins/krl-interpreter` | `MAPPING_RECIPE` transform-shape on a `fileKind=krl` ref; sidecar = `TransformExecutor`; `/v2/krl/*` deleted |

### 7a. A7 residual survey (2026-06-10) — no remaining zero-caller-AND-superseded resources

A caller-grep across `frontend/`, `e2e/`, `examples/`, `clients/python/`,
the MCP tools, and the rest of the backend — plus a live probe against the
running instance (v2 paths are mounted **bare** at `/v2/…`, not under
`/shepard/api/`) — found that **every remaining bespoke `/v2/` resource is
either still called or not actually superseded.** The A2/A3/A6/B4/B5 waves
already removed the genuinely-superseded-and-dead surfaces (per-kind base
CRUD, the scene-graph editor, KRL REST). Outstanding verdicts:

| Resource | Verdict | Reason |
|---|---|---|
| `/v2/thermography/*` (`ThermographyV2Rest`, fileformat-thermography) | **MIGRATE-FIRST** | 4 live FE callers (`DataObjectThermographyPane`, `DataObjectOtvisViewer`, `MultiPlayerThermographyTile`, `otvisViewer.ts`) + Vitest + showcase seed. Replacement `POST /v2/shapes/render` is live (probe → 405 POST-only). Build the heatmap/otvis-frame view-shapes + repoint FE before deleting (A7-THERMO-REST-DISSOLVE). |
| `/v2/svdx/ingest` (`SvdxIngestRest`, fileformat-svdx) | **DECOMMISSION-DECISION** | Real CSV→TimescaleDB ingest (parses TwinCAT `.csv`, writes channels via `TimeseriesService`), **NOT** an upload side-effect — the §7 "no REST at all" premise is wrong. Live (probe → 405). No FE caller; only the welding showcase seed. Not superseded → operator chooses keep (recommended) vs decommission. If kept, it is a deliberate non-Tier-3 exception: CSV→TS ingest has no generic-surface equivalent. |
| `/v2/hdf-containers/{appId}` + `/file` (`HdfContainerRest`, hdf5) | **MIGRATE-FIRST** | Live FE `pages/containers/hdf/[containerId]/index.vue` (GET/DELETE/`/file`). `/v2/containers?kind=hdf` list is live (probe → 200) but the `/{appId}/file` raw-download sub-resource is functional and **not** covered by the unified list — preserve it on the unified surface first (A7-HDF-UNIFY). |
| `/v2/collections/{appId}/scene-graph` (`CollectionSceneGraphRest`, hero-link) | **KEEP-FUNCTIONAL** | Live FE `CollectionSceneGraphHeader`. This is the link/unlink-a-MAPPING_RECIPE-to-a-Collection feature — distinct from the already-deleted `/v2/scene-graphs/*` editor; not superseded. |
| `/v2/collections/{appId}/dqr` (`CollectionDQRRest`, TPL10) | **UNUSED-NOT-SUPERSEDED** | Zero callers anywhere (mostly-stub DQR feature). Not superseded by any generic surface → out of scope for this consolidation pass; a separate dead-feature decision. |
| `/v2/admin/ledger/anchor` (`LedgerAnchorRest`) | **UNUSED-NOT-SUPERSEDED** | Live on instance (probe → 405) but zero callers. Tamper-evidence admin feature, not superseded by `/v2/admin/config` → out of scope. |

Net for this pass: **0 endpoints deleted** (no resource is both superseded
and zero-caller). The migrate-then-delete order lives in the A7-* rows.

## 8. No v2 back-compat (pre-production)

The fork's `/v2/` may change shape freely until production cut-over — paths are
renamed/merged/deleted in place with **no shims, no deprecation windows**; the
v2-only frontend is updated in the same PR. Independent of the **frozen
`/shepard/api/` upstream byte-compat surface** (third-party clients), which stays
untouched. Each PR keeps `aidocs/34` (upgrade ledger), `aidocs/44` (matrix), and
`docs/reference/*` current.

## 9. Phasing (backlog: V2CONV-* in aidocs/16)

**Track A — surface convergence**
- A1 render media contract (content negotiation + `producibleMedia()`).
- A2 unified `/v2/references?kind=` + `fileKind` discriminator (delete per-kind paths).
- A3 unified `/v2/containers?kind=`.
- A4 admin `ConfigRegistry` + `/v2/admin/config/{feature}`. **✓ shipped 2026-06-03.**
- A5 `RestNamespaceContributor` + plugin-gated registration + `OASFilter` (Jupyter + AAS).
- A6… core→plugin extractions (svdx → thermography → urdf+krl).

**Track B — templates-as-shapes spine** (interlocks: a kind's shape from B is
what A's generic surfaces validate/render against)
- B1 SHACL shape-builder (predicate-IRI JSON DSL → Turtle) + round-trip tests.
- B2 wire shape validation into `TemplateInstantiationRest` (422).
- B3 `MAPPING_RECIPE` kind + materialization path + `TransformExecutor` SPI.
- B4 scene-graph as `MAPPING_RECIPE` view-shape; delete `/v2/scene-graphs/*`.
- B5 KRL as `MAPPING_RECIPE` transform-shape; delete `/v2/krl/*`.
- B6 template editor (palette + composition + parent + live SHACL preview).
- B7 kinds declare shapes via the builder; ontology-driven create-form reads the shape.

## 10. Verification

- **Render**: `POST /v2/shapes/render` `Accept: image/png` on a thermography
  view-recipe → PNG; `Accept: application/json` → view-model. No
  `/v2/thermography/...` path.
- **References + fileKind**: `POST /v2/references?kind=file` with a `.urdf`
  upload → ref with `fileKind=urdf`; `GET /v2/references?kind=file&fileKind=urdf`
  lists it. Old `/v2/timeseries-references` returns 404 (deleted).
- **Plugin disable → OpenAPI**: `PATCH /v2/admin/plugins/aas {enabled:false}` →
  `/shepard/doc/openapi/v2.json` no longer contains `/v2/aas/*`; those paths 404;
  re-enable restores both. (Automated IT.)
- **Admin**: `GET|PATCH /v2/admin/config/semantic` ≡ old
  `/v2/admin/semantic/config`; `:Activity` still captured.
- **Templates-as-shapes**: a DATA template compiles to SHACL; instantiation with
  a violating attribute → 422; the same shape generates the create form.
- Gates per PR: `mvn verify -pl backend` (JaCoCo ≥60%), frontend
  `lint`/`test`/`typecheck`, `make redeploy-*` smoke, doc-stage index regen.

## 11. Cross-references
- `aidocs/platform/47` (plugin SPI seam — extends `FileParserPlugin` →
  `FileFormatPlugin`, adds `RestNamespaceContributor`, `TransformExecutor`).
- `aidocs/semantics/95`, `aidocs/semantics/98` (shapes as templates/views/agent
  contracts — this is their consolidation).
- `aidocs/integrations/123` (template inheritance — the `sh:node` bridge).
- `aidocs/integrations/122` (template `iconKey` — same template substrate).
- CLAUDE.md: API-version policy, plugin-first, views-as-shapes, frontend-v2-only,
  UI-never-asks-for-paths, surface-operator-knobs, evolve-in-new-namespace.
