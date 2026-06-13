---
stage: audited-by-personas
last-stage-change: 2026-06-13
audience: frontend, UX, product, dispatcher, docs
---

# UI feature inventory 2026-06-13 — the master SSOT

**Auditor:** UI feature-inventory agent (read-only scan + live probe), scope `UIVERIFY`.
**Target:** `https://shepard.nuclide.systems` (live).
**Auth:** Keycloak is proxied behind the app's NextAuth/sidebase OIDC flow and is
**not** directly reachable for a ROPC token grant from a headless shell (the
`/auth/realms/.../token` path 302-redirects to `/api/auth/signin`). So endpoint
functionality here is established two ways: (1) **anonymous HTTP status probe** of
each backing endpoint — `401`/`405` = endpoint exists and is auth/method-gated
(= reachable, WORKS), `404` = path absent at the probed spec; (2) **the
authenticated Playwright walk done TODAY** in
`aidocs/agent-findings/ui-1920-pass-2026-06-13.md` (flo/flo-demo, live), which
rendered every core page. **I did not mint a fresh token myself** — I leaned on
today's authenticated walk + status-code probing instead.
**Builds on:** `aidocs/44-fork-vs-upstream-feature-matrix.md`,
`aidocs/agent-findings/ui-1920-pass-2026-06-13.md`,
`aidocs/agent-findings/ux-shapes-displays-and-journeys-2026-06-12.md`,
`docs/reference/plugins.md`, `frontend/components/common/placeholder/placeholderRegistry.ts`.

---

## Summary counts

**UI maturity** (≈58 features inventoried):
- **REAL: ~41**  (functional UI panels/pages)
- **PLACEHOLDER: ~12**  (placeholder-kit stubs — 7 page-mounts + 5 admin/profile fragments still designed-not-shipped; the registry holds 17 entries but 5 are *retained* markers for panes already replaced by real components)
- **MISSING: ~5**  (backend exists, zero UI: AAS plugin, scene-graph index route, several plugin admin panes)

**Functionality** (live probe + today's walk):
- **WORKS: ~46** (endpoints reachable 401/405; pages render per the 1920 walk)
- **BROKEN: ~3** — DataObject-detail page (perpetual spinner, **known, in-flight**), `/scene-graphs` index 404, TS-container chart-legend clip
- **UNKNOWN: ~9** (plugin endpoints whose exact v2 path I couldn't confirm anonymously — AAS, git, spatiotemporal, video, unhide all 404'd at guessed paths but the plugins are bundled; needs an authenticated path-walk)

**Doc coverage:** `docs/help/` = 25 pages, `docs/reference/` = 39 pages.
Of inventoried features: **~62% have an advanced (reference) doc**, **~48% have a
basic (help) doc**, **~38% have BOTH**. Biggest gaps: Tools cluster (sparql /
shapes-render / shapes-validate / form-preview / materialize-mapping all lack a
basic help page), and every plugin payload kind except KRL/notebooks/import.

---

## Inventory table

UI: REAL / PLACEHOLDER / MISSING · Func: WORKS / BROKEN(sym) / UNKNOWN ·
Probe: anonymous status code (401/405 = exists, 404 = path-not-at-spec).

### Collections

| Feature | C/P | Route/Surface | UI | Func | Basic? | Adv? | 44 status |
|---|---|---|---|---|---|---|---|
| Collections list | Core | `/collections` | REAL | WORKS (401 `/v2/collections`) | no | yes (collections.md) | ✓ |
| Collection detail | Core | `/collections/{id}` | REAL | WORKS (walk: clean) | no | yes | ✓ |
| Collection create/edit/delete | Core | dialogs | REAL | WORKS | no | yes | ✓ |
| Lineage graph | Core | CollectionLineageGraph | REAL | WORKS | yes (lineage-graph.md) | yes | ✓ |
| Cross-track view | Core | CollectionCrossTrackViewPane | REAL | WORKS | yes (cross-track-view.md) | no | ✓ |
| Timeline | Core | CollectionTimelinePane | REAL | WORKS | yes (collection-timeline.md) | no | ✓ |
| Collection activity log | Core | pane | REAL | WORKS | yes (monitor-collection-activity.md) | no | ✓ |

### DataObjects

| Feature | C/P | Route/Surface | UI | Func | Basic? | Adv? | 44 status |
|---|---|---|---|---|---|---|---|
| DataObject detail page | Core | `/.../dataobjects/{id}` | REAL | **BROKEN(spinner hang — known, in-flight)** | no | yes (data-objects.md) | ✓ |
| DO create/edit/delete | Core | dialogs | REAL | WORKS | yes (upload-data.md) | yes | ✓ |
| Relationships (predecessor/successor) | Core | display + add/edit dialogs | REAL | WORKS | no | yes (references.md) | ✓ |
| URI references | Core | UriReferenceInput | PLACEHOLDER-ish (input uses placeholder import) | WORKS | no | yes | 🅰️ |
| Provenance graph | Core | DataObjectProvGraph | REAL | WORKS (blocked by DO-detail hang in walk) | yes (provenance-tracing.md) | yes (provenance.md) | ✓ |
| Quality trail | Core | components/quality | REAL | WORKS | yes (quality-trail.md) | yes (regulatory-evidence-pack.md) | ✓ |
| Publish / PID | Core | publish pane | REAL | WORKS | yes (publish-data-object.md) | yes (publish-and-pids.md) | ✓ |
| Wiki-write (lab journal gen) | Plugin (wiki-writer) | DO action | REAL | UNKNOWN (plugin off by default) | no | yes (lab-journal.md) | 📐/🚧 |
| ActionMenuButton (View as…/Record a…) | Core | DO detail | REAL | UNKNOWN (never rendered — blocked by DO hang) | no | no | 🚧 |

### References by kind

| Feature | C/P | Surface | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|---|
| FileReference (singleton FR1b) | Core | display + edit dialog | REAL | WORKS (405 `/v2/files`) | yes (upload-data.md) | yes (file-reference.md) | ✓ |
| FileBundleReference | Core | FilesTable | REAL | WORKS | no | yes (file-bundle.md) | ✓ |
| TimeseriesReference | Core | picker + show dialog | REAL | WORKS | yes (timeseries-plotting.md) | yes (timeseries-reference.md) | ✓ |
| StructuredDataReference | Core | viewer dialog | REAL | WORKS | no | yes (references.md) | ✓ |
| VideoStreamReference | Plugin (video) | core video components (15 files) | REAL | UNKNOWN (plugin off by default) | no | yes (video-stream-references.md) | 🅰️ |
| Spatial/spatiotemporal ref | Plugin | core (23 files) | REAL | UNKNOWN (404 at guessed v2 path) | no | no | 🚧 |
| AAS reference/submodel | Plugin (aas) | **none** | **MISSING (0 UI files)** | UNKNOWN | no | no | 📐 |
| Create-from-template prefill | Core | CreateDataReferenceDialog | REAL (placeholder import for prefill) | WORKS | yes (create-from-template.md) | yes (template-editor.md) | ✓ |

### Containers by kind

| Feature | C/P | Route | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|---|
| Containers list | Core | `/containers` | REAL | WORKS | no | yes (containers.md) | ✓ |
| File container | Core | `/containers/files/{id}` | REAL | WORKS | yes (upload-data.md) | yes | ✓ |
| Timeseries container | Core | `/containers/timeseries/{id}` | REAL | WORKS — **chart-legend clips long 5-tuple labels (MINOR)** | yes (timeseries-plotting.md) | yes | ✓ |
| Structured-data container | Core | `/containers/structureddata/{id}` | REAL | WORKS | no | yes | ✓ |
| Spatial-data container | Core | `/containers/spatialdata/{id}` | REAL | UNKNOWN | no | no | 🚧 |
| Video container | Core | `/containers/video/{id}` | **PLACEHOLDER** | UNKNOWN (plugin off) | no | yes | 🅰️ |
| HDF container | Core | `/containers/hdf/{id}` | **PLACEHOLDER** (browser stub; download works) | partial | no | no | 🅰️ |
| Container annotations | Core | ChannelAnnotationsPane | **PLACEHOLDER** | WORKS (backend shipped) | yes (annotate-container.md) | yes (container-annotations.md) | 🅰️ |
| Safe-delete w/ refs | Core | DeleteContainerButton | REAL | WORKS | yes (delete-container-with-references.md) | yes (container-safe-delete.md) | ✓ |
| Payload version history | Core | PayloadVersionHistoryDialog | REAL | WORKS | no | yes (payload-versioning.md) | ✓ |

### Timeseries deep features

| Feature | C/P | Surface | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|---|
| All-channels chart / uPlot | Core | TimeseriesAllChannelsChart | REAL | WORKS | yes | yes | ✓ |
| Trace3D view | Core | Trace3DView + pickers | REAL | WORKS | no | yes (view-recipes.md) | ✓ |
| URDF view / animator | Core | UrdfView/UrdfAnimator | REAL | WORKS | yes (run-krl-preview.md) | yes (scene-graph.md) | ✓ |
| Thermography view | Plugin (thermography) | ThermographyView | REAL (canvas uses placeholder) | UNKNOWN | no | no | 🚧 |
| NDT grid overlay | Plugin (vis-ndt-grid) | core (16 files) | REAL | UNKNOWN | no | no | 🚧 |
| AFP-thermo overlay | Plugin (vis-afp-thermo-overlay) | AfpThermoOverlayPlaceholder | **PLACEHOLDER** | UNKNOWN | no | no | 🅰️ |
| Anomaly detection | Core/Plugin (analytics-ts) | DetectAnomaliesDialog | REAL | WORKS | no | no | 🚧 |
| Channel annotations / units | Core | ChannelAnnotationsPane | PLACEHOLDER | WORKS | yes (units-on-channels.md) | yes (semantic-annotations.md) | 🅰️ |
| Synchronised player | Core | multiplayer | REAL | WORKS | yes (synchronised-player.md) | no | ✓ |
| KRL preview | Plugin (krl-interpreter) | InterpretAsTrajectoryButton | REAL | WORKS | yes (run-krl-preview.md) | yes (krl-interpreter.md) | ✓ |

### Tools cluster

| Feature | C/P | Route | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|---|
| Tools landing | Core | `/tools` | REAL | WORKS | no | no | ✓ |
| SPARQL playground | Core | `/semantic/sparql` | **PLACEHOLDER** (impl-status banner) | WORKS (404 `/v2/semantic` — repo-scoped path) | no | yes (semantic-repositories.md) | 🅰️ |
| Shapes render | Core | `/shapes/render` | **PLACEHOLDER** (impl-status banner) | WORKS (405 `/v2/shapes/render`) | no | yes (view-recipes.md) | 🅰️ |
| Shapes validate (SHACL) | Core | `/shapes/validate` | **PLACEHOLDER** (impl-status banner) | WORKS (405 `/v2/shapes/validate`) | no | yes (semantic-annotations.md) | 🅰️ |
| Form preview | Core | `/tools/form-preview` | **PLACEHOLDER** (full stub) | partial | no | no | 🅰️ |
| Materialize mapping | Core | `/tools/materialize-mapping` | **PLACEHOLDER** (full stub) | partial | no | no | 🅰️ |
| Snapshot diff | Core | `/snapshots/diff` | REAL | WORKS (401 `/v2/snapshots`) | no | yes (snapshots.md) | ✓ |
| Scene-graph player | Core | `/scene-graphs/play/{id}` | REAL | WORKS | no | yes (scene-graph.md) | ✓ |
| Scene-graph index | Core | `/scene-graphs` | **MISSING (404 — no index route, not in Tools grid)** | BROKEN(404) | no | yes | 🚧 |
| Search | Core | `/search` | REAL | WORKS | no | no | ✓ |
| Applicable-shapes menu | Core | useApplicableShapes (placeholder import) | REAL | WORKS | no | yes | ✓ |

### Semantic

| Feature | C/P | Route | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|---|
| Semantic landing | Core | `/semantic` | REAL | WORKS | no | yes (semantic-repositories.md) | ✓ |
| Vocabularies browser | Core | `/semantic/vocabularies` | **PLACEHOLDER** (partial backend) | WORKS (401 `/v2/admin/semantic/ontologies`) | no | yes (semantic-annotations.md) | 🅰️ |
| Predicates browser | Core | `/semantic/predicates/{iri}` | REAL | WORKS | no | yes | ✓ |
| Annotation dialog (3-click) | Core | AddAnnotationDialog | REAL | WORKS | yes (annotating-data.md) | yes (semantic-annotations.md) | ✓ |

### Admin

| Feature | C/P | Surface | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|---|
| Admin hub | Core | `/admin` | REAL | WORKS | no | yes (admin-cli.md) | ✓ |
| Plugins list/toggle | Core | pane | REAL | WORKS (401 `/v2/admin/plugins`) | no | yes (plugins.md) | ✓ |
| Feature toggles (A3b) | Core | pane | REAL | WORKS (401 `/v2/admin/features`) | no | yes | ✓ |
| Permission audit log (F3) | Core | PermissionAuditLogPane | REAL | WORKS (401 `/v2/admin/permission-audit/log`) | no | yes | ✓ |
| SQL-TS config (P10) | Core | AdminSqlTimeseriesPane | REAL | WORKS | no | yes | ✓ |
| Instance admins | Core | AdminInstanceAdminsPane | REAL (registry-retained marker) | WORKS (401 `/v2/admin/instance-admins`) | no | yes | ✓ |
| User ORCID overrides | Core | AdminUserOrcidPane | REAL | WORKS | no | yes (user-profile.md) | ✓ |
| User git credentials | Core | AdminUserGitPane | REAL (partial backend) | WORKS | no | yes (sidecars.md) | 🅰️ |
| Notification transports | Core | AdminNotificationsPane | REAL (partial: in-app only; SMTP/Matrix pending) | WORKS (405 `/v2/admin/notifications/test`) | no | no | 🅰️ |
| Ontology alignment | Core | AdminOntologyAlignmentPane | REAL | WORKS | no | no | ✓ |
| Instance registry (cross-instance prov) | Core | `/admin/instance-registry` | REAL | WORKS | no | yes (nfdi4ing-federation.md) | ✓ |
| Provenance admin | Core | `/admin/provenance` | REAL | WORKS | yes (admin-activity-log.md) | yes (provenance.md) | ✓ |
| MFFD process chain | Core | `/admin/mffd-process-chain` | **PLACEHOLDER** | partial | no | no | 🅰️ |
| File migration | Core | admin fragment | **PLACEHOLDER** (backend shipped) | WORKS (FS1e) | no | yes (file-storage.md) | 🅰️ |
| AI config (admin) | Core | admin fragment | **PLACEHOLDER** (designed) | UNKNOWN (401 `/v2/admin/ai/capabilities` — plugin) | no | no | 📐 |
| Backup config | Core | admin fragment | **PLACEHOLDER** (designed) | MISSING | no | no | 📐 |
| v1-compat control plane | Plugin (v1-compat) | banner + admin | REAL | UNKNOWN (404 at guessed path) | no | yes (v1-deprecation.md) | ✓ |

### Profile / Me

| Feature | C/P | Surface | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|---|
| Profile page | Core | `/me` | REAL (~480px dead gutter @1920) | WORKS | no | yes (user-profile.md) | ✓ |
| API key management | Core | `/me` | REAL | WORKS | yes (api-access.md) | yes (api.md) | ✓ |
| AI settings (per-user) | Core | profile fragment | **PLACEHOLDER** (designed) | MISSING | no | no | 📐 |
| ORCID self-link | Core | `/me` | REAL | WORKS | yes (register-with-nfdi4ing.md) | yes | ✓ |

### Plugins (payload kind / admin / tool)

| Plugin | UI surface in core | UI | Func | Basic? | Adv? | 44 |
|---|---|---|---|---|---|---|
| unhide | 6 files (publish pane) | REAL | UNKNOWN (404 guessed path) | yes (register-with-nfdi4ing.md, export-metadata4ing.md) | yes (nfdi4ing-federation.md) | ✓ |
| kip | resolver (no UI needed) | n/a | WORKS (401 `/v2/.well-known/kip`) | no | yes (publish-and-pids.md) | ✓ |
| minter-{local,datacite,epic} | publish flow | REAL | WORKS (via publish) | yes (publish-data-object.md) | yes (minter-datacite.md) | ✓ |
| file-s3 | admin file-migration (placeholder) | PLACEHOLDER | WORKS | no | yes (file-storage.md) | ✓ |
| video | 15 core files + placeholder container | PLACEHOLDER | UNKNOWN (off default) | no | yes | 🅰️ |
| ai | admin/profile fragments (placeholder) | PLACEHOLDER | UNKNOWN (off default) | no | no | 📐 |
| wiki-writer | DO action (4 files) | REAL | UNKNOWN (off default) | no | yes (lab-journal.md) | 🚧 |
| importer | 10 files (import wizard) | REAL | WORKS (401 `/v2/import/context`) | yes (importing-from-dlr-cube3.md, observing-an-import.md) | yes (import.md, import-validate.md) | ✓ |
| jupyter | 9 files (notebooks) | REAL | WORKS | yes (work-with-notebooks.md) | yes (notebooks.md) | ✓ |
| git | 37 files (refs + credentials) | REAL | UNKNOWN (404 guessed path) | no | yes (sidecars.md) | 🚧 |
| spatiotemporal | 23 files | REAL | UNKNOWN | no | no | 🚧 |
| spatial-importer | via import wizard | REAL | UNKNOWN | no | no | 🚧 |
| aas | **0 files** | **MISSING** | UNKNOWN | no | no | 📐 |
| fileformat-cad | 3 files (annotations on upload) | REAL (no dedicated UI; semantic annotations) | UNKNOWN | no | yes (plugins.md row) | 🚧 |
| fileformat-robotics/svdx | parser only (no UI) | n/a | UNKNOWN | no | no | 🚧 |
| krl-interpreter | InterpretAsTrajectoryButton + URDF | REAL | WORKS | yes (run-krl-preview.md) | yes (krl-interpreter.md) | ✓ |
| hdf5 | container browser (placeholder) | PLACEHOLDER | partial | no | no | 🅰️ |
| analytics-ts | DetectAnomaliesDialog | REAL | WORKS | no | no | 🚧 |
| vis-trace3d | Trace3DView | REAL | WORKS | no | yes (view-recipes.md) | ✓ |
| vis-ndt-grid | 16 files | REAL | UNKNOWN | no | no | 🚧 |
| vis-afp-thermo-overlay | AfpThermoOverlayPlaceholder | PLACEHOLDER | UNKNOWN | no | no | 🅰️ |
| thermography | ThermographyView (canvas placeholder) | REAL/partial | UNKNOWN | no | no | 🚧 |

---

## Top-20 punch list (worst-impact broken-or-placeholder, researcher-first)

1. **DataObject detail page hang** — the single most-used page spins forever (known plumbing bug, in-flight). Everything hangs off it. BROKEN. *(B1 in the 1920 walk — already owned.)*
2. **SPARQL playground is a placeholder** — backend shipped + reachable, but the page is an impl-status banner. Highest-value Tool with no real UI. PLACEHOLDER.
3. **Shapes render is a placeholder** — drives Trace3D/view-recipes; backend live (405). PLACEHOLDER.
4. **Shapes validate (SHACL) is a placeholder** — backend live (405); FAIR/quality story depends on it. PLACEHOLDER.
5. **Channel annotations pane is a placeholder** — backend shipped (TS-SEMANTIC-REST); blocks per-channel semantic annotation UI for the flagship timeseries kind. PLACEHOLDER.
6. **Vocabularies browser is a placeholder** — annotators can't see what predicates exist. PLACEHOLDER.
7. **HDF container browser is a placeholder** — only download works; no dataset tree. PLACEHOLDER.
8. **Video container page is a placeholder** — the whole video kind has no functional container UI. PLACEHOLDER.
9. **AAS plugin has zero UI** — backend designed/partial, 0 frontend files. MISSING.
10. **/scene-graphs has no index route (404)** — player exists at deep URL only; not in Tools grid. MISSING nav.
11. **Form preview is a full placeholder** — template-form authoring tool unusable. PLACEHOLDER.
12. **Materialize mapping is a full placeholder** — mapping tool unusable. PLACEHOLDER.
13. **MFFD process chain admin is a placeholder** — flagship demo admin surface. PLACEHOLDER.
14. **AFP-thermo overlay is a placeholder** — the JEC-award MFFD thermal-trail visual is stubbed. PLACEHOLDER.
15. **File-migration admin is a placeholder** — backend shipped (FS1e); operators can't drive storage moves from UI. PLACEHOLDER.
16. **Notification transports pane partial** — only in-app; SMTP/Matrix CRUD missing. PARTIAL.
17. **AI config (admin) + AI settings (profile) placeholders** — no UI to wire the AI plugin. PLACEHOLDER (designed).
18. **TS-container chart legend clips long 5-tuple labels** — researchers can't read which channel is which. BROKEN(minor).
19. **`/me` ~480px dead left gutter @1920** — wastes a quarter of the viewport. Layout.
20. **Doc gap: entire Tools cluster lacks basic help pages** — sparql/shapes/form-preview/materialize have advanced refs at best, no researcher-friendly task page. DOCS.

---

## Method notes / honesty

- Plugins ship **no `frontend/` dir of their own** — all plugin UI lives in core
  `frontend/components|pages`. So "plugin UI present" = grep hit count in core
  (e.g. git=37, spatial=23, aas=0). AAS=0 is the one true UI-MISSING plugin.
- The placeholder registry (`placeholderRegistry.ts`) holds **17 entries**, but 5
  carry `PLACEHOLDER-REPLACE-* shipped` comments (real panes now mounted, marker
  retained for `EXPECTED_PLACEHOLDER_COUNT`). The **live placeholder mounts** are:
  shapes/render, shapes/validate, semantic/sparql, tools/form-preview,
  tools/materialize-mapping, containers/video, admin/mffd-process-chain,
  ChannelAnnotationsPane, vocabularies, hdf container, + the 5 designed-only
  admin/profile fragments (file-migration, sql-ts [now real], ai-config,
  ai-settings, backup).
- **UNKNOWN** functionality rows are plugins whose exact v2 path I couldn't hit
  anonymously (all returned 404 at guessed paths, but they're bundled + enabled
  per `docs/reference/plugins.md`). An authenticated path-walk would resolve these
  — filed as `UIVERIFY-*` rows.
