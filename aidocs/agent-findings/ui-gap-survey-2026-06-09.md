---
stage: feature-defined
last-stage-change: 2026-06-10
---

# UI Gap Survey — 2026-06-09

Survey of the live frontend for backend features lacking a real (non-stub) UI surface.
Conducted 2026-06-10 as backlog row `UI-GAP-SURVEY-2026-06-09`.

Reference rules:
- `CLAUDE.md §"Always: ship a UI stub for every backend feature"` — stub-only is `alpha`, no stub is blocked.
- `CLAUDE.md §"Always: every shipped feature is reachable from the top-nav before beta"` — top-nav-unreachable = stays `alpha`.
- `CLAUDE.md §"Always: tool entry points are in-context first"` — primary entry = entity detail page, not Tools menu.

---

## What I found

### Method

1. Scanned `frontend/pages/` for `PlaceholderPageHeader`, `PlaceholderFragmentPane`, `PlaceholderImplStatus`, `PlaceholderRestDump` usage.
2. Checked `frontend/components/common/placeholder/placeholderRegistry.ts` for registered stub slugs.
3. Cross-referenced with `aidocs/16-dispatcher-backlog.md` MFFD-TPL-* and V2CONV shipped rows.
4. Walked `frontend/utils/toolsContext.ts` (`DATA_OBJECT_CONTEXT_TOOLS`) to confirm in-context entry points.
5. Inspected `frontend/components/context/snapshot/SnapshotsPane.vue` for Compare affordance.
6. Confirmed `frontend/components/container/timeseries/ChannelAnnotationsPane.vue` is a real implementation (not stub).

---

## Per-gap table

| Gap ID | Backend feature | Current surface | What a flo-grade surface needs | Persona who suffers | Size | Priority |
|---|---|---|---|---|---|---|
| **UI-GAP-1-materialize-mapping-wizard** | V2CONV-B3 `MAPPING_RECIPE` materializer (`POST /v2/mappings/{templateAppId}/materialize`) | `pages/tools/materialize-mapping.vue` — `PlaceholderPageHeader` + free-text appId inputs + raw JSON output dump | (1) Replace free-text `templateAppId` input with `TemplateAutocomplete` scoped to `MAPPING_RECIPE` kind. (2) Replace binding role/appId free-text pairs with a Reference picker that resolves from the current Collection context. (3) Output panel: when `outputKind=REFERENCE`, show a link to the derived reference's detail page instead of raw `appId` text; when `outputKind=VIEW_MODEL`, dispatch to the appropriate renderer (Trace3D / URDF / table) rather than `JSON.stringify`. (4) In-context entry point: "Materialize…" action in `EntityToolsMenu` for `data-object` scope when a `MAPPING_RECIPE` template is attached. Currently the Tools menu only has this entry for `VIEW_RECIPE` (shapes/render). | Digital Native (wants bookmarkable result URL), Flo (wants rendered output, not raw JSON) | M | High — V2CONV-B3 is shipped; the page is the only UI entry; output rendering is broken for non-REFERENCE outputs |
| **UI-GAP-2-mffd-process-chain-real** | MFFD-MAPPING-REST-1 `POST /v2/admin/mffd/process-chain-mapping` | `pages/admin/mffd-process-chain.vue` — `PlaceholderPageHeader` + working YAML upload form + counters + unresolved checklist | The page is functionally adequate for its purpose (YAML upload with counters). The `PlaceholderPageHeader` is cosmetic. Flo-grade polish: (1) Replace `PlaceholderPageHeader` with a real `<h4>` + description (remove the design-doc link from the page header; keep it in the operator runbook). (2) Add a "dry-run" toggle that calls the endpoint with `?dryRun=true` (if the backend supports it) before applying. (3) Add a "Download example YAML" button linking to the `scripts/mffd-process-chain-mapping.example.yaml` asset. | Instance admin (Operator Tomas) | S | Medium — functional but visually signals "work in progress" to admins |
| **UI-GAP-3-admin-config-browser** | V2CONV-A4 `GET /v2/admin/config` (list all registered config features) | No dedicated listing pane. The admin panel has per-feature bespoke panes (SemanticConfigPane, AdminSqlTimeseriesPane, AdminJupyterPane, InstanceRorPane) wired to individual fragments. `GET /v2/admin/config` exists and returns all registered descriptors but is never called from the UI. | (1) Add a new admin fragment `#config-overview` that calls `GET /v2/admin/config` and renders a read-only table: feature key, description, current value (JSON). Each row links to the specific existing pane if one exists, or falls through to a generic JSON PATCH editor for plugin-registered descriptors (e.g. future `unhide`, `ai`, `datacite` descriptors). (2) The generic PATCH editor is the key deliverable — any `ConfigDescriptor`-registered plugin gets admin editability without a bespoke pane. Size S for read-only listing; M for full generic PATCH editor. | Instance admin (Operator) | S (listing) / M (generic editor) | Medium — the per-feature panes work; the gap is discoverability of plugin-registered config keys and operator onboarding friction |
| **UI-GAP-4-mffd-template-create-wizards** | Five MFFD-TPL-* `DATAOBJECT_RECIPE` templates shipped 2026-06-09: `mffd-material-batch-data-shape`, `mffd-afp-course-data-shape`, `mffd-weld-step-data-shape`, `mffd-ndt-otvis-measurement-data-shape`, + the eight `V100` process-step templates. The T1e rule (CLAUDE.md) requires Create dialogs to pre-fill from parent templates when a `ShepardTemplate` is attached. | Generic `TemplatePickerDialog` works. The "Create DataObject from template" flow pre-fills `name` from the template but does NOT pre-populate the `urn:shepard:mffd:*` annotation fields from the template's `body.annotations[]` array. The user must manually re-enter the MFFD predicates after creation. | The template instantiation path (`TemplateInstantiationRest` + `TPL-INSTANTIATE-ANNOTATIONS-1` backlog row) needs to be implemented: when creating a DataObject with an attached template whose body carries an `annotations[]` array, `POST /v2/data-objects` (or the template create endpoint) should seed the listed annotations automatically. Frontend: a per-MFFD-kind guided wizard is aspirational (M); the minimum viable fix is wiring the annotation pre-fill from template body (tracked as `TPL-INSTANTIATE-ANNOTATIONS-1` in `aidocs/16`). | Flo (AFP researcher creating 200 AFP-course DataObjects per session) | M (wizard) / S (annotation pre-fill wiring) | High — 5 shipped templates with annotation bodies that are silently ignored; the pre-fill is the T1e rule requirement |
| **UI-GAP-5-mffd-render-ndt-grid** | MFFD-RENDER-NDT-GRID (backlog: `MFFD-RENDER-NDT-GRID` — `queued`) + MFFD-RENDER-AFP-THERMO-OVERLAY (backlog: `MFFD-RENDER-AFP-THERMO-OVERLAY` — `queued`). Both are `MAPPING_RECIPE` view-shapes. The `MffdNdtOtvisMeasurementKind` template shipped 2026-06-09 unblocks the grid renderer. | No `plugins/vis-ndt-grid/` or `plugins/vis-afp-thermo-overlay/` exist yet. These are queued M-size items. | (1) `vis-ndt-grid` plugin: S×M×L×F mosaic canvas, cell colour = mean ΔT or pass/fail from NDT quality annotations. (2) AFP thermo overlay: synced Trace3D + heatmap pane. Both follow the `plugins/vis-trace3d` plugin pattern. No current UI at all. | Flo (MFFD NDT researcher), IME auditor | M | High — these are the MFFD headliner visualisations; templates are shipped but the renderer plugins are not yet started |
| **UI-GAP-6-shapes-validate-inline-editor** | `SHAPES-V` — SHACL validation playground (`POST /v2/shapes/validate`). Backend shipped. | `pages/shapes/validate.vue` — two Turtle textareas, Run button, JSON report dump. `PlaceholderImplStatus` still mounted. The page has real prefill logic (`SHAPES-V-PREFILL-2-RDF-ENDPOINT` — DataObject RDF auto-load, template body shape extraction). | (1) Replace raw `<textarea>` pair with a CodeMirror / Monaco-lite editor with Turtle syntax highlighting (or at minimum a monospace `<v-textarea>` with line numbers). (2) Replace raw `JSON.stringify` validation report dump with a structured violations list (violation message, focus node, result severity chip). (3) Remove `PlaceholderImplStatus` footer once the editor polish lands. (4) Add per-violation "jump to DataObject" link when `resultPath` resolves to a known entity. | Digital Native, Reluctant Senior | S | Medium — page works end-to-end; polish removes "prototype" perception |
| **UI-GAP-7-placeholder-replace-ts-semantic-rest** | TS-SEMANTIC-REST — channel annotations (`GET/POST/DELETE /v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations`) | **Already superseded**: `ChannelAnnotationsPane.vue` was shipped (confirmed by 2026-06-02-21 dispatcher run) and is mounted at `pages/containers/timeseries/[containerId]/index.vue:296`. The `PLACEHOLDER-REPLACE-TS-SEMANTIC-REST` backlog row still shows `queued` — this is a **stale row**. | Mark `PLACEHOLDER-REPLACE-TS-SEMANTIC-REST` as `done` in `aidocs/16`. No frontend work needed. | n/a | XS (doc-only status flip) | Immediate — wrong status misleads routing |
| **UI-GAP-8-snapshot-diff-polish** | Snapshot diff (`GET /v2/snapshots/{a}/diff/{b}`) — in-context "Compare with…" per-row action in `SnapshotsPane`. | `SnapshotsPane.vue` at line 39–44 already has `compareInSnapshotDiff()` that `router.push`es to `/snapshots/diff?a=<appId>`. The row action exists. `pages/snapshots/diff.vue` has `PlaceholderImplStatus` but is functionally complete (autocomplete pickers, diff result). | (1) Remove `PlaceholderImplStatus` from `pages/snapshots/diff.vue` (backend is shipped, UI is real). (2) The global `/snapshots/diff` page needs a "Share / bookmark" button that copies the `?a=…&b=…` URL. (3) In `SnapshotsPane`, confirm the `isAllowedToEditCollection` gate is correct (compare should be read-only; confirm non-editors can use it). | Digital Native (wants bookmarkable diff URL), Reluctant Senior (wants quick audit comparison) | XS (remove PlaceholderImplStatus) / S (share button) | Medium |

---

## Deferred items (backend not yet shipped)

| Slug | Backend status | Deferred reason |
|---|---|---|
| `admin#ai-config` — `PlaceholderFragmentPane slug="ai-config"` | AI1a backend not shipped | Nothing to implement until AI1a lands |
| `admin#backup` — `PlaceholderFragmentPane slug="backup"` | PG-COLLAPSE-002 backend not shipped | Nothing to implement until backup backend lands |
| `me#ai-settings` — `PlaceholderFragmentPane slug="ai-settings"` | Per-user AI settings backend not shipped | Deferred with backend |

---

## Top-5 highest-impact items

Ordered by `operator value × number of users blocked`:

1. **UI-GAP-5-mffd-render-ndt-grid** (M): The MFFD NDT grid + AFP thermo overlay renderers are the flagship MFFD visualisations. Templates are shipped; plugins are not started. Zero UI available for the most operator-visible feature set.
2. **UI-GAP-4-mffd-template-create-wizards** (S/M): Five MFFD templates ship annotation bodies that are silently ignored at create time. Every AFP researcher creating process-step DataObjects must manually re-enter ~8 annotation predicates. The T1e rule explicitly requires annotation pre-fill; this is a structural compliance gap.
3. **UI-GAP-1-materialize-mapping-wizard** (M): The MAPPING_RECIPE materializer page uses bare text inputs and dumps raw JSON — the user cannot navigate to the derived output. In-context entry point from DataObject detail is also missing. V2CONV-B3 is shipped but effectively invisible as a capability.
4. **UI-GAP-3-admin-config-browser** (S+M): `GET /v2/admin/config` returns all registered descriptors but is never surfaced in the admin panel. Plugin-registered config (future `ai`, `datacite`, `unhide`) will be invisible to operators without a generic PATCH editor.
5. **UI-GAP-7-placeholder-replace-ts-semantic-rest** (XS): Stale `queued` status in `aidocs/16`. The channel annotations pane is shipped and mounted. One-line status flip; no code work needed.

---

## Gaps confirmed NOT present (false positives cleared)

- **shapes/render.vue in-context entry**: `EntityToolsMenu` (`DATA_OBJECT_CONTEXT_TOOLS`) includes `do-render` and `do-shacl` items gated on `attachedTemplateAppId`. In-context entry IS present per the "tool entry points are in-context first" rule. Not a gap.
- **shapes/validate.vue in-context entry**: Same — `do-shacl` item in `DATA_OBJECT_CONTEXT_TOOLS`. Not a gap.
- **SnapshotsPane "Compare with…" row action**: Exists at `SnapshotsPane.vue:39–44`. Not a gap (though `PlaceholderImplStatus` on the diff page should be removed — captured as UI-GAP-8).
- **ChannelAnnotationsPane**: Real implementation mounted at timeseries container page. The `PLACEHOLDER-REPLACE-TS-SEMANTIC-REST` backlog row is stale — captured as UI-GAP-7 (status flip only).
- **vis-ndt-grid / vis-afp-thermo-overlay plugins**: These are `queued` M items in `aidocs/16`, not stale. Filed as UI-GAP-5 for tracking.
