---
title: One spatial integration — spatial as a unified Reference kind
stage: feature-defined
last-stage-change: 2026-06-10
audience: contributor
supersedes: []
related:
  - aidocs/data/90-spatial-as-temporal-sweep.md (SPATIAL-V6 substrate + viewer SSOT — this doc EXTENDS it; §3/§4 here reference §3/§5/§7 there)
  - aidocs/integrations/113-mffd-real-data-import-plan.md (W7 spatial-importer pass — §6 here re-frames it as automatic-on-upload)
  - aidocs/integrations/110-file-format-parser-plugin.md (FileParserPlugin SPI — §6 here decides why spatial promotion is NOT a plain FileParserPlugin)
  - aidocs/agent-findings/plugin-v2-only-audit.md (frozen v1 spatial REST + PLUGIN-V2-001 sibling shelf)
  - aidocs/platform/191-v2-conformance-sweep.md (ReferenceKindHandler SPI + render-SPI §7b that §4/§5 plug into)
---

# 124 — One spatial integration: spatial as a unified Reference kind

> **Operator directive (2026-06-10, verbatim, from a DataObject detail
> page):** *"spatial data should be also a reference, also reconcile
> overlap with spatiotemporal, so we end up with one spatial
> integration."*

This doc is the **SSOT for the spatial *unification* concern**. It does
**not** re-open the SPATIAL-V6 substrate/viewer design — that SSOT is
[`aidocs/data/90`](../data/90-spatial-as-temporal-sweep.md) and stays
authoritative for the schema, the brush-trace renderer, and the frame
registry. 124 answers the two questions 90 left open:

1. **Surface shape** — spatial must appear as a kind in the unified
   `/v2/references?kind=…` Data-References surface, addressed by
   `appId`, created/listed/deleted like every other reference — not as
   a bolt-on container-promotion side-channel rendered in its own
   "Spatial data" panel.
2. **Module convergence** — there must be *one* spatial integration.
   Today there are two faces: a `spatial`-lineage container-promotion
   flow (the panel + `shepard-plugin-spatial-importer`) and the
   `shepard-plugin-spatiotemporal` plugin that owns the substrate +
   the frozen upstream-byte-compat REST. They converge here.

---

## §1 — What the operator saw, and why it's two problems

On the DataObject detail page (`feat-unified-refs-containers` /
`unified-demo-do`) the unified **Data References** table already renders
File / TimeSeries / Structured Data / File Bundle / Notebook / Git /
Video tabs from the `/v2/references?kind=` surface. **Below** it sits a
separate `DataObjectSpatialContainersPane.vue` reading:

> *"No spatial data containers on this DataObject. Run the
> spatial-importer pass to promote pointcloud / trajectory file
> references into SpatialDataContainers."*

That single empty-state line encodes both defects:

- **"is not a reference":** spatial is shown as *containers*, in its own
  panel, created by a *manual importer pass* — not as a "Spatial (N)"
  tab in the unified references table created by `POST /v2/references`.
- **"two integrations":** the panel speaks "SpatialDataContainer +
  spatial-importer" (the `spatial` lineage), while the substrate, the
  PayloadKind, the entities, and the frozen v1 REST all live in
  `shepard-plugin-spatiotemporal`.

The good news from the inventory (§2): **the underlying model is
already right.** `SpatialDataReference extends BasicReference` and points
at a `SpatialDataContainer` via `[:IS_IN_CONTAINER]` — structurally
identical to `FileReference → FileContainer`. The container is *already*
an implementation detail behind a reference. What's missing is the wiring
into the unified surface and a single coherent frontend story. This is a
**surface + convergence** job, not a data-model rewrite.

---

## §2 — Inventory (live surfaces, with file paths)

### 2.1 The `spatiotemporal` plugin (substrate + entities + frozen REST)

| Concern | Path |
|---|---|
| PayloadKind impl | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/plugins/spatiotemporal/SpatiotemporalPayloadKind.java` (`name()="spatiotemporal"`) |
| Plugin manifest | `…/plugins/spatiotemporal/SpatiotemporalPluginManifest.java` |
| Reference entity | `…/context/references/spatialdata/entities/SpatialDataReference.java` (`extends BasicReference`; `[:IS_IN_CONTAINER] → SpatialDataContainer`) |
| Reference DAO/service/IO | `…/context/references/spatialdata/{daos,services,io}/…` |
| **FROZEN v1 reference REST** | `…/context/references/spatialdata/endpoints/SpatialDataReferenceRest.java` — `@Path(Constants.SHEPARD_API + …/spatialDataReferences)`, numeric `Long` ids |
| Container entity | `…/data/spatialdata/model/SpatialDataContainer.java` |
| **FROZEN v1 container REST** | `…/data/spatialdata/endpoints/SpatialDataPointRest.java` — `@Path(Constants.SHEPARD_API + spatialDataContainers)`, numeric `Long containerId` |
| Substrate (PostGIS hypertable) | `…/data/spatialdata/repositories/SpatialProfileRepository.java`, `…/SpatialDataPointRepository.java` |
| Green-field schema | `plugins/spatiotemporal/src/main/resources/db/spatial/migration/V2.0.0__green_field_schema.sql` |
| Vocabulary SPI impl | `…/plugins/spatiotemporal/GeoTimeVocabularyProvider.java` |

Frozen v1 paths confirmed present in `backend/src/test/resources/fixtures/v5/openapi-5.4.0.json`:
`/spatialDataContainers{,/{id},/{id}/payload,/{id}/permissions,/{id}/roles}` and
`/collections/{c}/dataObjects/{d}/spatialDataReferences{,/{id},/{id}/payload}`.

### 2.2 The `spatial` lineage (importer + panel + container-only frontend)

| Concern | Path |
|---|---|
| Importer (Python sidecar, **no Java**) | `plugins/spatial-importer/cli/{main,linescan}.py` — promotes ASCII pointcloud/trajectory + TPS-raw PNG chunks into `SpatialDataContainer`s via the **v1** `/shepard/api/spatialDataContainers/…` REST |
| **Bolt-on panel** | `frontend/components/context/dataobject/DataObjectSpatialContainersPane.vue` (the empty-state the operator saw) |
| Container-detail viewer | `frontend/pages/containers/spatialdata/[containerId]/index.vue` + `frontend/components/shapes/SpatialPointsCanvas.vue` |
| Per-DO ref fetch (v1, numeric) | `frontend/composables/context/useSpatialDataReferencesForDataObject.ts` |
| Container accessors (v1) | `frontend/composables/container/SpatialDataContainerAccessor.ts`, `…/data/useCreateSpatialDataContainer.ts` |
| Multiplayer tile | `frontend/components/context/multiplayer/MultiPlayerSpatialTile.vue` |
| Residual decommissioned module | `plugins/spatial/docs/reference.md` (redirect stub only — SPATIAL-V6-001 already renamed the Java module) |

**Finding:** `spatial` is no longer a *Java module* — SPATIAL-V6-001
already renamed it to `spatiotemporal`. What survives of the `spatial`
lineage is (a) the Python `spatial-importer` sidecar, (b) the frontend
`*SpatialContainers*` panel + container-only composables, and (c) the
`spatial-importer` calling the **frozen v1** container REST. The
"two integrations" the operator perceives is really *one plugin* fronted
by *two frontend stories* (container-promotion vs. unified-reference).

### 2.3 The unified-reference machinery to plug into

| Concern | Path |
|---|---|
| Handler SPI | `backend/.../v2/references/spi/ReferenceKindHandler.java` (`kind()`, `owns()`, `findByAppId()`, `toIO()`, `create()`, `patch()`, `delete()`, `listByDataObject()`) |
| Dispatcher | `…/v2/references/services/ReferencesV2Service.java` (`@Any Instance<ReferenceKindHandler>`; `registeredKinds()`) |
| Unified REST | `…/v2/references/resources/ReferencesV2Rest.java` (`/v2/references?kind=`) |
| In-tree handlers | `…/v2/references/handlers/{File,Timeseries,Uri}ReferenceKindHandler.java` |
| **Plugin handlers (the template to copy)** | `plugins/video/.../handlers/VideoStreamReferenceKindHandler.java`, `plugins/git/.../GitReferenceKindHandler.java`, `plugins/hdf5/.../HdfReferenceKindHandler.java` |
| Frontend unified table | `frontend/components/context/display-components/data-references/DataObjectDataReferencesTable.vue` |
| PayloadKind SPI | `backend/.../spi/payload/PayloadKind.java` (`shapeDescriptor()` + `viewShapeDescriptor()` hooks already present) |
| FileParser SPI | `backend/.../spi/fileparser/FileParserPlugin.java` (annotation-only, fire-and-forget on singleton upload) |
| Render SPI | `backend/.../spi/view/{ViewRecipeRenderer,ViewRecipeRendererRegistry}.java` (`POST /v2/shapes/render`) |

The CDI-discovery handler SPI is the seam. A plugin-resident
`SpatialDataReferenceKindHandler` (the Video handler is a 140-line
template) is all the backend wiring spatial needs to appear as
`kind=spatial` in the unified surface.

---

## §3 — Target architecture: one spatial integration

### 3.1 Spatial as a reference kind (the surface fix)

Ship a **plugin-resident `SpatialDataReferenceKindHandler`** in
`shepard-plugin-spatiotemporal` implementing `ReferenceKindHandler` with
`kind() = "spatial"`. It delegates straight to the existing
`SpatialDataReferenceService` / `SpatialDataReferenceDAO` (no new
storage). The Video handler is the verbatim template:

- `owns()` → `instanceof SpatialDataReference`
- `findByAppId()` → `SpatialDataReferenceService.findByAppId(appId)`
  (add the appId-keyed finder; mirrors the L2d appId-keyed pattern)
- `toIO()` → project `geometryFilter`, `measurementsFilter`,
  `startTime`, `endTime`, `metadata`, `limit`, `skip`, plus the
  resolved `spatialDataContainer` appId, into `ReferenceV2IO.payload`
- `create()` → mint a `SpatialDataReference` bound to an *existing*
  `SpatialDataContainer` appId (the JSON-create path); see §3.3 for how
  the container gets there
- `patch()` / `delete()` / `listByDataObject()` → delegate

Result: `GET/POST/DELETE /v2/references?kind=spatial` works, and the
frontend renders a **"Spatial (N)" tab** next to File / TimeSeries /
Video in `DataObjectDataReferencesTable.vue`. The bolt-on
`DataObjectSpatialContainersPane.vue` is **deleted**.

### 3.2 The SpatialDataContainer-behind-reference decision

**Decision: the `SpatialDataContainer` stays — as an implementation
detail behind the reference, exactly like `FileContainer` is behind
`FileReference` and `TimeseriesContainer` behind `TimeseriesReference`.**

This is *already true in the model* (§2.1: `SpatialDataReference
[:IS_IN_CONTAINER] SpatialDataContainer`). The PostGIS hypertable
genuinely needs a container-scoped home for `profile` rows — a brush
trace is millions of `(time, profile, anchor, measurements)` rows that
cannot live "inside" a Neo4j reference node. The container is the
storage seam; the reference is the addressing seam. The unification is
**not** "delete the container" — it is "stop *surfacing* the container
as a first-class user concept." Users address spatial by reference
`appId`; the container is resolved behind it, never picked from a list.

This mirrors the CLAUDE.md "UI never asks for paths/URLs — pulls from
references" and "singleton FileReference for one-file uploads" rules:
the reference is the single addressing layer; the container is the
backend's dereference target.

### 3.3 One module: convergence map

| Today | After SPATIAL-UNIFY |
|---|---|
| `shepard-plugin-spatiotemporal` (substrate + entities + frozen REST + vocab) | **survives — the one spatial integration** |
| `shepard-plugin-spatial` Java module | already gone (SPATIAL-V6-001); residual `plugins/spatial/docs` redirect stub → delete |
| `shepard-plugin-spatial-importer` (Python sidecar) | **becomes a parse-on-upload trigger** (§6); the *manual pass* framing is retired |
| `SpatialDataContainer` | stays, demoted to impl-detail-behind-reference (§3.2) |
| Bolt-on `DataObjectSpatialContainersPane.vue` | **deleted**; replaced by the unified "Spatial (N)" tab |
| Frozen v1 `SpatialData*Rest` | stays byte-compatible (§5), unextended |
| New surface | `/v2/references?kind=spatial` + `/v2/spatial-containers/{appId}` shelf (PLUGIN-V2-001) |

`PayloadKind.name()` remains `"spatiotemporal"` (the kind that registers
entity packages + SHACL/view shapes). The *reference kind token* exposed
on the wire is `"spatial"` — this is deliberate and matches the operator's
language and the user-facing tab label. (The two names answer different
questions: `PayloadKind.name` is the schema-registration identity;
`ReferenceKindHandler.kind` is the wire/UI token. The Video plugin shows
the same split is fine — its PayloadKind and its handler token need not be
byte-identical.)

---

## §4 — The viewer: render through `/v2/shapes/render`

Per the just-shipped render-SPI work (`aidocs/platform/191 §7b`,
V2CONV-A1b-RENDER-PARAMS, the thermography `ViewRecipeRenderer`
dissolve), the spatial viewer must render through `POST
/v2/shapes/render` — **not** a bespoke `/v2/spatial-containers/{id}/png`
endpoint. The existing `SpatialPointsCanvas.vue` (Three.js pointcloud /
trajectory / brush-trace modes) stays as the *client* renderer; the
*server-side* loft path (SPATIAL-V6-003, `ST_MakePolyhedralSurface` →
glTF) becomes a `ViewRecipeRenderer` claiming `shepard:BrushTraceShape`
(SPATIAL-V6-004 already wires this). The DataObject-detail entry point
is the in-context "Render this view" / "Open in 3D viewer" button on the
spatial reference row (CLAUDE.md "tool entry points are in-context
first"), pre-populated with the reference `appId` — never a pasted id.

No new viewer work is *created* here; SPATIAL-UNIFY only **re-roots** the
viewer entry from the container-detail page onto the unified reference
row, and confirms the render path is `/v2/shapes/render`.

---

## §5 — Keep the frozen surface (compat carrier)

Per CLAUDE.md "plugins build on /v2/ + appId §4" and the SPATIAL-V6-003 /
PLUGIN-V2-001 sibling-shelf rule:

- `SpatialDataReferenceRest` (`/shepard/api/…/spatialDataReferences`,
  numeric `Long`) and `SpatialDataPointRest`
  (`/shepard/api/spatialDataContainers`, numeric `Long`) are **frozen
  byte-compat** — they appear in `openapi-5.4.0.json` and third-party
  upstream clients depend on them. **Do not rewrite them in place.**
- The fork's own callers (frontend composables, the spatial-importer
  sidecar, MCP, showcase seeds) migrate to the `/v2/` shelf:
  `/v2/references?kind=spatial` for reference CRUD,
  `/v2/spatial-containers/{appId}` for container CRUD (PLUGIN-V2-001),
  `/v2/spatial-containers/{appId}/trace` for the loft render
  (SPATIAL-V6-003).
- v1 sunset is per-operator (`project_v1_sunset_strategy.md`); the
  frozen resources stay callable indefinitely.

**Verification gate** (SPATIAL-UNIFY-007): an integration test asserts
the frozen v1 `spatialDataReferences` + `spatialDataContainers` request/
response shapes remain byte-identical to `openapi-5.4.0.json` after the
v2 shelf + handler land.

---

## §6 — Importer: from manual pass to parse-on-upload

The operator's directive implies the manual "run the spatial-importer
pass" UX should disappear. **Decision: promotion becomes
automatic-on-upload, but spatial is NOT a plain `FileParserPlugin`.**

Why not a `FileParserPlugin`: that SPI (`spi/fileparser`) is an
**annotation-only, fire-and-forget** side-effect on singleton File
upload — it writes `urn:shepard:*` predicates and emits *zero*
containers/rows (see svdx/otvis/rdk: they annotate, they do not stream
millions of PostGIS rows). Spatial promotion streams a whole hypertable
of points and mints a container — that is a heavier, longer, failure-
prone operation that must be **completeness-non-negotiable** (retry-
forever; `feedback_completeness_nonnegotiable.md`) and must not block
the upload response.

**Shape:** keep the Python `spatial-importer` as the worker (it owns the
parse dependency tree + fixtures + retry/backoff), but trigger it
**automatically** when a pointcloud/trajectory/TPS-raw FileReference
lands, instead of via a hand-run CLI pass. Two viable triggers (decide
in SPATIAL-UNIFY-001 research):

1. **Lightweight `FileParserPlugin` as a classifier-and-enqueue:** a
   tiny in-tree parser recognises the spatial filename shapes on upload,
   writes a `urn:shepard:spatial:promote-pending` annotation + a
   `:SpatialPromotionJob` queue node; the Python sidecar polls the queue
   and does the heavy streaming. The parser stays annotation-only (SPI-
   compliant); the heavy lift stays in the sidecar.
2. **Subscription/webhook:** the sidecar subscribes to FileReference-
   created events filtered by the spatial filename classifier.

Either way the *user* sees: upload a `.csv` pointcloud → a "Spatial (N)"
reference appears (eventually-consistent, with a "promoting…" state) →
no manual pass. The promotion writes a typed `:Activity`
(`ProvenanceService.record()`, `actionKind=EXECUTE`,
`sourceMode=ai|collaborative` as appropriate) so the audit trail shows
who/what promoted which FileReference into which spatial reference.

---

## §7 — Annotatable + provenance from day one

Spatial references are annotatable at zero cost — the `appId` is the
handle, so `AnnotationDialog` + the MCP annotation tools work the moment
the `kind=spatial` handler returns appId-keyed IO (CLAUDE.md "semantic
annotations are first-class on every entity"). The promotion path
records a typed `:Activity` (§6). No per-kind annotation entity is
created (anti-pattern).

---

## §8 — Migration (additive, nullable, idempotent)

Existing `:SpatialDataContainer` + `:SpatialDataReference` nodes are
**already `BasicReference`-shaped and already carry `appId`** (the L2d/
appId mint applies to every `HasAppId` entity). So the migration is
mostly **read-path** — the new `kind=spatial` handler resolves the same
nodes. The only data touch:

- **Backfill rollup (idempotent):** ensure every legacy
  `:SpatialDataReference` has a resolvable `appId` and an `[:IS_IN_CONTAINER]`
  edge; ship `V###__backfill_spatial_reference_appids.cypher` with a
  `V###_R__` rollback twin, `IF NOT EXISTS` / `WHERE appId IS NULL`
  guards (mirrors NEO-AUDIT-003's `V82__mint_timeseries_appids`). No
  PostGIS DDL change — the hypertable is untouched.
- **No backfill of point rows.** The container→reference relationship is
  unchanged; only the *surface* changes.

Per CLAUDE.md migration rules: idempotent, fail-fast, rollback twin,
operator-runbook pointer in the migration header. Regression test
deferred-but-tracked (SPATIAL-UNIFY-006 references a Testcontainer pre/
post `appId IS NULL` count assertion).

---

## §9 — Persona-board argument

### Data & Process Ontologist lens

Spatial data in MFFD/PLUTO is *time-varying engineering geometry* (AFP
TCP sweeps, robot torch paths, NDT line scans, mission trajectories).
The ontologist's concern is **cross-cutting queryability**: "show every
spatial artefact anchored in frame F, after time T, annotated
`m4i:realizesMethod = ultrasonic-cscan`." Today the bolt-on container
panel hides spatial outside the reference graph, so that query has to
special-case `:SpatialDataContainer` separately from every other payload
kind. Making spatial a **uniform `kind=spatial` reference** puts it on
the same `appId` + `:SemanticAnnotation` + `:Activity` substrate as File/
TS/Video — so ISO 19115 geographic-metadata predicates, OGC GeoSPARQL
`geo:hasGeometry` (already contributed by `GeoTimeVocabularyProvider`),
and CCSDS mission-trajectory predicates all annotate spatial references
the same way they annotate any other reference. **One reference kind is
the ontology win**: spatial stops being a special case in every
cross-cutting query, export (RO-Crate/DataCite), and SHACL shape.

### API Scrutinizer lens

Two integrations is the same smell as the timeseries 5-tuple: a separate
`SpatialDataContainer` surfaced as a *first-class user concept* forces a
caller to do `list containers → pick container → list references in
container → resolve` instead of `list references?kind=spatial`. The
minimalist verdict: **one kind, one create verb, one list verb, one
delete verb — `/v2/references?kind=spatial`**, identical envelope to
every other kind. The frozen v1 numeric-id `spatialDataReferences` +
`spatialDataContainers` resources are redundant *for the fork's own
callers* and survive only as a compat carrier (not a surface we extend).
The scrutinizer signs off on the container surviving **behind** the
reference (it's storage, not a caller concept) but rejects any new
endpoint that makes the caller name a container to reach spatial bytes.

### Opposing lens (where a separate SpatialDataContainer is justified)

A genuinely strong case for a first-class container exists: **huge point
clouds and brush-trace hypertables need chunked, container-scoped
storage** (PostGIS hypertable partitioning, BRIN/GIST indexes, 7-day
compression, COPC/Potree LOD tiling per PC-LOD1). A 50M-point cloud or a
30-minute AFP sweep is not "a reference" in any storage sense — it is a
container of millions of rows that the reference merely *points into*,
and multiple references may window the same container (different
geometry/measurement filters over one substrate). The reconciliation:
the **reference-with-container-behind-it** shape serves this perfectly —
it is exactly how `TimeseriesReference` windows a `TimeseriesContainer`
of 81M points. The container keeps every storage advantage; what changes
is only that the *user* never picks a container from a list. So the
opposing lens does not argue for *surfacing* two integrations — it argues
for *keeping the container as a storage primitive*, which §3.2 does.

---

## §10 — Ordered SPATIAL-UNIFY backlog (summary)

Full rows land in `aidocs/16`. Order:

1. **SPATIAL-UNIFY-001** (research) — trigger decision (FileParser-
   classifier-enqueue vs subscription), reference-kind token (`spatial`)
   vs PayloadKind name (`spatiotemporal`) reconciliation, appId finder.
2. **SPATIAL-UNIFY-002** (backend) — `SpatialDataReferenceKindHandler` in
   `shepard-plugin-spatiotemporal` + `findByAppId` + JSON-create; unit
   tests (Video-handler-shaped).
3. **SPATIAL-UNIFY-003** (frontend) — "Spatial (N)" tab in the unified
   references table; **delete** `DataObjectSpatialContainersPane.vue` +
   migrate composables to `useV2ShepardApi`; Vitest + Playwright 4K.
4. **SPATIAL-UNIFY-004** (importer-as-parser) — automatic promotion
   trigger; retire the manual pass framing; `:Activity` on promote.
5. **SPATIAL-UNIFY-005** (viewer-via-render) — re-root the viewer entry
   onto the spatial reference row; confirm `/v2/shapes/render` path
   (folds into SPATIAL-V6-004).
6. **SPATIAL-UNIFY-006** (migration) — `V###` appId-backfill + rollback
   twin; Testcontainer pre/post assertion.
7. **SPATIAL-UNIFY-007** (frozen-surface verification) — byte-compat IT
   against `openapi-5.4.0.json`; PLUGIN-V2-001 `/v2/spatial-containers/
   {appId}` shelf for the fork's own callers.

Trackers (same-PR rule): `aidocs/34` (BREAKING surface note),
`aidocs/42` (vision payload-kind table — spatial moves to "in the box"),
`aidocs/44` (SPATIAL-UNIFY rows), `aidocs/data/00-model-inventory.md`.

---

## §11 — Highest-risk open question for the operator

**Should the automatic spatial promotion be eager (block nothing, but
fire on *every* matching FileReference upload) or opt-in per
Collection/DataObject?** Eager promotion is the cleanest "no manual
pass" UX, but it means *any* `.csv`/`.ply`/`.las` upload that matches the
spatial filename classifier silently spawns a PostGIS hypertable + a
spatial reference — which could surprise an operator who uploaded a CSV
that merely *looks* like a pointcloud, and could be costly at MFFD scale
(every TPS-raw chunk auto-promoting). The alternative — a per-Collection
`spatial.auto-promote` toggle (`:*Config` admin knob) defaulting off —
is safer but reintroduces a (one-time, admin-level) opt-in step. Which
posture does the operator want as the default?
