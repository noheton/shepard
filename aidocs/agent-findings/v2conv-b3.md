---
stage: feature-defined
last-stage-change: 2026-06-03
---

# V2CONV-B3 — MAPPING_RECIPE kind + TransformExecutor SPI + materialization path

Implementation findings for the generic mapping-recipe machinery (the enabler
that later dissolves the scene-graph + KRL namespaces, V2CONV-B4/B5).

## What I found

- **The seam already existed, twice over.** `ViewRecipeRenderer` +
  `ViewRecipeRendererRegistry` (`backend/.../spi/view/`) is a complete, battle-
  tested ServiceLoader SPI keyed by SHACL shape IRI, with a fail-soft `resolve`
  and a fail-fast-on-duplicate startup posture. V2CONV-B3 is its *transform-
  direction sibling* — I mirrored it 1:1 in `de.dlr.shepard.spi.transform`
  rather than inventing a new pattern. The `PayloadKind` P-series SPI is the
  third instance of the same shape; this is now a well-established in-repo
  convention.
- **`templateKind` is free-form.** It's a plain `@Property("templateKind")`
  String on `ShepardTemplate` validated by `TemplateBodyValidator.expectedKeys()`
  (a `switch`). Adding `MAPPING_RECIPE` was a one-line `case` + a javadoc bullet
  — no enum migration, no schema change (Neo4j is schemaless; the body is an
  opaque String). This is exactly the "evolve in a new namespace, additive +
  nullable" principle paying off.
- **`ProvenanceService.record()` already swallows write failures** (best-effort
  by design, `aidocs/55 §4`). I still wrapped the call site in its own try/catch
  + the `PROP_SKIP_CAPTURE` handoff so the materialize mutation yields exactly
  one Activity (per the "handlers that record their own Activity hand off
  skip-capture" rule).

## Opportunities

- **The `MAPPING_RECIPE` shape now lets every transform be a plugin.** Once a
  plugin registers a `TransformExecutor` against its recipe's `mappingRecipeShape`
  IRI, a "derive this" action lights up with *zero* new REST surface. That's the
  whole point of B4/B5: the KRL sidecar becomes a `TransformExecutor` returning a
  REFERENCE (joint-trajectory TimeseriesReference); the scene-graph play becomes
  a `TransformExecutor` returning a VIEW (the play envelope). Both delete a
  bespoke `/v2/...` namespace.
- **`TransformResult` already models both output directions** (REFERENCE | VIEW),
  so B4 (VIEW) and B5 (REFERENCE) need no SPI change — only their executor impls.

## Ideas

- **In-context entry first.** The Tools page (`/tools/materialize-mapping`) is
  the entry-less fallback. The canonical entry per the "tools in-context first"
  rule is a "Materialize…" action on a FileReference / DataObject detail page,
  pre-populated with that entity's appId as the first binding. Filed as a
  follow-up thought; not built in B3 since the recipe needs ≥2 inputs to be
  interesting and the detail-page picker is its own UI task.
- **A generic `TransformExecutor` admin listing** (`GET /v2/admin/transform-
  executors`) mirroring the planned `GET /v2/admin/view-recipe-renderers` would
  let an operator see which transform shapes are claimable on a given deploy —
  useful when a recipe 404s with `transform.executor.not-registered`.

## Real-world impact

- A researcher with a `.urdf` file + a joint-angle timeseries can, post-B4,
  click "Materialize → scene-graph play" and scrub the robot — *without* the
  scene-graph ever having had a stored frames/joints graph. The recipe + the two
  references ARE the scene. Same for KRL: the `.src` + URDF → a derived
  trajectory reference, recorded with full provenance for the EN 9100 audit.
- The identity default means the path is demoable today, before any of that:
  author a `MAPPING_RECIPE` pointing at the identity shape, POST one reference
  appId, get it echoed back as the "derived" reference.

## Gaps & blockers

- **No real derived-entity minting yet.** The identity default *points at* an
  existing reference rather than minting a new one. A genuine derive (KRL → new
  TimeseriesReference) is B5's job and needs the reference-creation services
  injected into the executor — out of scope for the generic machinery.
- **`mappingRecipeShape` is parsed from the body, not SHACL-validated.** B3
  treats it as an opaque dispatch key (same as `viewRecipeShape` in the render
  endpoint today). Full shape validation of a MAPPING_RECIPE body against a
  meta-shape is a B1/B6 concern.
- **The Tools page is a placeholder-grade stub.** It does the job (pick template
  appId + bind references + see output) but isn't a polished picker — references
  are typed appIds, not a reference-picker dialog. Acceptable for `alpha`; a
  REF-EDIT-style follow-up would add the picker.

## What surprised me

- **How little new code the generic enabler actually needs.** The entire SPI +
  registry + endpoint + default + IO is ~700 LoC, almost all of it a faithful
  mirror of the renderer SPI. The hard architectural work was done by whoever
  shipped `ViewRecipeRenderer`; B3 is mostly "apply the same pattern in the other
  direction." That's the reuse-before-reimplement rule working as intended.
- **`TransformRequest`'s `Map.copyOf` is unordered** — it bit my first no-op test
  (I assumed "first input" was deterministic across multiple bindings; it isn't,
  since `Map.copyOf` is HashMap-backed). The fix was to make the multi-input test
  order-agnostic. Worth a note for B5: if the KRL executor needs a *specific*
  input by role, it must look it up by key (`inputReferenceAppIds().get("srcFileAppId")`),
  never rely on iteration order. The role-keyed map is there precisely so it
  doesn't have to.

## Research consulted

- In-repo reference impl: `de.dlr.shepard.spi.view.ViewRecipeRenderer` +
  `ViewRecipeRendererRegistry` + `ShapesRenderRest` (the SPI I mirrored).
- **Context7** (`/quarkusio/quarkus`) for the Quarkus CDI + ServiceLoader
  registry pattern (confirming the `@ApplicationScoped` + StartupEvent-discovery
  shape the renderer registry already uses is idiomatic).
- **WebSearch** "SHACL shape as transform/mapping recipe": the W3C SHACL working
  draft (2026-05-16) + the *A Review of SHACL: From Data Validation to Schema
  Reasoning* (arXiv 2112.01441) and the *Semantic Object Mapping using SHACL-
  Validated …* paper (Semantic Web Journal swj2656) confirm SHACL shapes are
  legitimately used beyond validation — for UI building, code generation, and
  **data integration / transformation** — which is exactly the MAPPING_RECIPE
  framing: a shape that *binds* inputs to a derived output, dispatched to an
  executor keyed by the shape IRI.
