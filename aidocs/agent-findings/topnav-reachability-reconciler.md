---
stage: feature-defined
last-stage-change: 2026-05-30
---

# Top-Nav Reachability Reconciler — findings

**Date.** 2026-05-30.
**Agent.** Top-Nav Reachability Reconciler.
**Persona.** `flo` (Keycloak demo realm; role `user`; NOT `instance-admin`).
**Rule under review.** CLAUDE.md *"Always: every shipped feature is reachable from the top-nav before beta"* — codified after the 2026-05-30 SCENEGRAPH-REST-1-UI gap.
**Anchor files.**
- Top navbar: `frontend/components/layout/HeaderBar.vue:26-38` (desktop nav buttons) + `:223-225` (overflow menu Help/About/API Docs) + `:357-367` (mobile drawer mirrors).
- Admin tile grid: `frontend/pages/admin/index.vue:50-221` (28 landing tiles) — admin-only via `UnauthorizedView` (`:225-230`).
- `/me` profile hub: `frontend/pages/me/index.vue:19-67` (7 tiles: Profile / API Keys / MCP / Subscriptions / Git creds / AI settings / Semantic).
- Semantic sub-hub: `frontend/components/context/user/SemanticPane.vue:22-47` — the de-facto "research tooling" hub.
- Live nav confirmed via `curl https://shepard.nuclide.systems/` → href set: `/`, `/collections`, `/containers`, `/help`, `/about#version`, `/me#profile`, swagger-ui.

---

## Reconciliation summary

- **Total shipped/in-flight rows surveyed in `aidocs/44`:** ~190 (rows carrying `✓ shipped` or `🚧 in-flight` markers; some rows ship 5–10 sub-items).
- **Rows with no user-visible surface at all** (backend infrastructure, CI gates, security gates, schema-additive IO fields, SPI seams): ~95. **Not in scope** — the rule only fires when a feature has a UI surface that needs to be reachable. These rows are flagged "N/A" in the matrix.
- **Rows already self-flagged `⚙ BE ✓ / UI pending`** (the matrix already pre-demotes them; this audit confirms): ~14 (TM1a, AI1c, A5a-d, IMP1, IMP2, IMP-LOCK, IMP-DIAG, J1d, WW1, FS1e1, FS1e2, FS1e3). Already honour the rule.
- **Rows shipped with a UI but reachable only by deep URL / programmatic call: 7.** These are the demotion list.
- **Rows where the UI is admin-tile-reachable** (admin-gated features whose tile is in the `/admin` landing card grid): 28. All pass the admin clause of the rule.
- **Verdict.** The fork largely honours the rule for non-admin features. The demotion list is small (7 features in 4 functional clusters). The structural fix is high-leverage: one new top-level "Tools" menu in `HeaderBar.vue` (or one fourth/fifth quick-link in `SemanticPane.vue`) lifts every demoted row back to beta in one PR. The genuine blast-radius is concentrated on the view-recipe / shapes-render family (URDF, Trace3D, Thermography) and on scene-graphs, where there is **no inbound link from anywhere in the UI** — those are the SCENEGRAPH-REST-1-UI shape that triggered the rule.

---

## The reachability matrix

Compact form. One row per shipped/in-flight feature with a user-visible UI surface. Pure-backend rows are aggregated as a footer line. Source: `aidocs/44-fork-vs-upstream-feature-matrix.md`.

Legend for "Reachable from top-nav?":
- **✓ direct** — appears in `HeaderBar.vue` desktop nav or overflow menu.
- **✓ in-context** — affordance lives on a top-nav-reachable detail page (Collection / DataObject / Container) — counts under the rule.
- **✓ hub-tile** — reachable via `/admin` or `/me` tile grid, themselves top-nav-reachable.
- **✓ subnav** — reachable via a clearly-labelled subnav from a top-nav-reachable page.
- **✗ deep-URL** — only by typing the URL or following an external link. **Demotion candidate.**
- **N/A** — no UI surface; backend-only feature (rule does not apply).

### A. Collections + DataObjects + Containers core (top-nav anchors)

| Feature (aidocs/44) | Frontend route | Reachable? | Nav path | Status today | Suggested |
|---|---|---|---|---|---|
| Collections list + CRUD | `/collections` | ✓ direct | HeaderBar:27 → `/collections` | ✓ shipped | unchanged |
| Collection detail | `/collections/[id]` | ✓ subnav | Collections list → row click; also from CollectionSidebar | ✓ shipped | unchanged |
| Containers list (4 kinds) | `/containers`, `/containers/[type]` | ✓ direct | HeaderBar:28 | ✓ shipped | unchanged |
| DataObject detail | `/collections/[c]/dataobjects/[d]` | ✓ in-context | Collection → DataObjects panel | ✓ shipped | unchanged |
| FileReference detail | `/.../filereferences/[id]` | ✓ in-context | DataObject → unified data refs table | ✓ shipped | unchanged |
| StructuredDataReference detail | `/.../structureddatareferences/[id]` | ✓ in-context | same | ✓ shipped | unchanged |
| TimeseriesReference detail | `/.../timeseriesereferences/[id]` | ✓ in-context | same | ✓ shipped | unchanged |
| Personal landing digest (#43) | `/` | ✓ direct | HeaderBar:26 (Home) | ✓ shipped | unchanged |
| Global search (UI-002) | `/search` | ✓ direct | header search field + dropdown footer link | ✓ shipped | unchanged |
| Help docs (D1a) | `/help` | ✓ direct | HeaderBar overflow:223 | ✓ shipped | unchanged |
| Lab journal (J1a/J1b/J1e/REF-UNIFIED-TABLE-FR1B) | inline on DataObject detail | ✓ in-context | DataObject → Lab Journal expansion panel | ✓ shipped | unchanged |

### B. Reference-kind panels on DataObject detail

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| FR1a (FileReference bundle) UI | DataObject panel | ✓ in-context | DataObject detail → unified data refs table | ✓ shipped | unchanged |
| Singleton FR1b + Jupyter launch (J1e) | DataObject panel | ✓ in-context | unified data refs table | ✓ shipped (J1e) | unchanged |
| TimeseriesReference panel | DataObject panel | ✓ in-context | same | ✓ shipped | unchanged |
| StructuredDataReference panel | DataObject panel | ✓ in-context | same | ✓ shipped | unchanged |
| HdfReferencesPane (A5c) | DataObject panel | ✓ in-context | same | ✓ shipped (HdfReferencesPane on detail) | unchanged |
| HDF container browse (A5a-d) | none yet (`/containers/hdf/[id]` skeleton page exists) | **✗ deep-URL** | `/containers/hdf/[id]` not in nav; no list page | **⚙ pre-demoted** (UI pending per matrix) | already honours rule; queued |
| Git references (G1a-d) | unified data refs table | ✓ in-context | DataObject detail | ✓ shipped | unchanged |
| Video stream references + interval annotations (VID1a/b) | DataObject panel | ✓ in-context | DataObject → VideoStreamReferencesPane | ✓ shipped (UI3a) | unchanged |
| Video container page | `/containers/video/[id]` | **✗ deep-URL — partial** | container search results link here; no top-level Videos tab. `/containers` index lists by type, so reachable via the kind filter. | ✓ shipped | borderline — keep as ✓ given the `/containers` index covers all four kinds |
| Spatial container (`/containers/spatialdata/[id]`) | same shape as Video | ✓ via /containers index | acceptable | ✓ shipped | unchanged |

### C. Collection-landing affordances (FAIR / RDM cluster)

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Cite-this card (RDM-001) | inline | ✓ in-context | Collection detail | ✓ shipped | unchanged |
| Metadata Completeness widget (RDM-005) | inline | ✓ in-context | Collection detail | ✓ shipped | unchanged |
| Collection Lineage Graph (UI_LINEAGE1) | inline | ✓ in-context | Collection detail | ✓ shipped | unchanged |
| DataObject Provenance Graph (UI_PROV1) | inline | ✓ in-context | DataObject detail | ✓ shipped | unchanged |
| LIC1 license/access-rights chips | inline | ✓ in-context | Collection + DataObject detail | ✓ shipped | unchanged |
| FAIR2 ORCID badge / FAIR3 embargo chip | inline | ✓ in-context | DataObject detail | ✓ shipped | unchanged |
| FAIR7 DMP-snippet export | `POST /v2/collections/{id}/dmp-snippet` | **✗ no UI button** | no Vue surface yet | ⚙ BE ✓ / UI pending | already honours; queued. Add a "Download DMP" button beside Cite-this. |
| RO-Crate export (R2 series, UI8) | inline | ✓ in-context | Collection detail → Download as RO-Crate | ✓ shipped | unchanged |
| Regulatory Evidence Pack export (TPL14) | inline | ✓ in-context | Collection detail → "Regulatory Evidence Pack" button | ✓ shipped | unchanged |
| Independence proof badge (TPL11) | inline `IndependenceBadge.vue` | ✓ in-context | DataObject detail | ✓ shipped | unchanged |
| Snapshots create/list/delete/diff (V2a-e + UI1a) | `SnapshotsPane` on Collection detail | ✓ in-context | Collection detail → Snapshots pane | ✓ shipped | unchanged |
| Snapshot diff playground (`/snapshots/diff`) | `/snapshots/diff` | ✓ hub-tile (borderline) | only via `/me#semantic` quick-link (`SemanticPane.vue:42`); SnapshotsPane should also link here | ✓ shipped | add a "Compare" button row in SnapshotsPane that deep-links to `/snapshots/diff?a=…&b=…` (cheaper than nav move) |

### D. Timeseries + chart cluster

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Inline timeseries chart (TS_CHART1) | inline | ✓ in-context | Timeseries container page | ✓ shipped | unchanged |
| Channel preview mini-chart (TS_PREVIEW1) | inline | ✓ in-context | same | ✓ shipped | unchanged |
| Pinned channel tiles (UX-PIN1) | `PersonalDigest.vue` | ✓ direct | Home page | ✓ shipped | unchanged |
| Live-mode auto-refresh | inline | ✓ in-context | Timeseries container | ✓ shipped | unchanged |
| Container storage stats chip (TS_STATS1) | inline | ✓ in-context | same | ✓ shipped | unchanged |
| ChannelPreviewChart shepardId path (TS-IDc) | inline | ✓ in-context | TimeseriesMeasurementsTable rows | ✓ shipped | unchanged |
| Channel annotation create/list (TS-SEMANTIC-REST) | placeholder stub on TS container | **⚙ stub only** | placeholder | ⚙ BE ✓ / UI partial | matrix self-flags; queued |
| ViewRecipeBuilderDialog (TPL2a/b, M1 dispatch) | modal | ✓ in-context | TS container page → "View as 3D path" / "View as URDF" / "View as thermography" | ✓ shipped | unchanged |
| `/shapes/render` view playground (TPL2b) | `/shapes/render` | **✗ deep-URL** | only opened programmatically by `ViewRecipeBuilderDialog.vue:91,106,127` (target=_blank). Not in any nav-pad. | ✓ shipped | **demote pending fix.** The in-context path works for typical use (user goes from TS container → renderer), but the page is also a standalone playground — add to a "Tools" menu (see §Cheapest fixes). |
| URDF viewer (URDF-WEBVIEW-1 phase 1) | rendered inside `/shapes/render` | ✓ in-context (transitive) | TS container → ViewRecipeBuilderDialog → `/shapes/render?renderer=urdf` | ✓ shipped | borderline pass via TS container affordance. Acceptable. |
| Thermography view (OTVIS-VIEW-1 tier-1) | rendered inside `/shapes/render` | ✓ in-context (transitive) | same | ✓ shipped (tier-1) | acceptable as for URDF |
| Trace3D view | rendered inside `/shapes/render` | ✓ in-context (transitive) | same | ✓ shipped | acceptable |

### E. Semantic / annotation cluster

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Annotate via dialog (SEMA-V6-004, N1e picker, UI19) | `AnnotationDialog` modal | ✓ in-context | DataObject + Container detail → Annotate button | ✓ shipped | unchanged |
| Container-level annotations (SA-CONT) | inline | ✓ in-context | per-kind container detail page | ✓ shipped | unchanged |
| Container annotation panels | inline | ✓ in-context | same | ✓ shipped | unchanged |
| `/semantic` index | `/semantic/index.vue` | ✓ hub-tile | `/me#semantic` → "Vocabularies" | ✓ shipped | acceptable but 4-clicks-deep; consider promoting via Tools menu |
| `/semantic/vocabularies` browse | same | ✓ hub-tile | `/me#semantic` → Vocabularies tile (`SemanticPane.vue:24`) | ✓ shipped | acceptable |
| `/semantic/vocabularies/[vocabId]` predicates | same | ✓ in-context | vocabulary list → row click | ✓ shipped | unchanged |
| `/semantic/predicates/[iri]` detail | same | ✓ in-context | vocabulary detail → predicate row | ✓ shipped | unchanged |
| `/semantic/sparql` playground (N1f UI) | `/semantic/sparql` | ✓ hub-tile | `/me#semantic` → SPARQL tile (`SemanticPane.vue:30`) | ✓ shipped UI; matrix shows N1f UI as "🚧 / pending" — reconcile: the playground page exists and is reachable | promote N1f to ✓ in matrix; consider Tools-menu promotion. Separate runtime bug (404 on "internal" repo) is **not** a reachability issue. |
| `/shapes/validate` SHACL playground (SHACL-1) | `/shapes/validate` | ✓ hub-tile | `/me#semantic` → Shape validator tile (`SemanticPane.vue:36`) | ✓ shipped | acceptable |
| `urn:shepard:unit` auto-annotation (AI1v Phase-1) | invisible at UI; surfaces via chart axis | ✓ in-context | TS container → axis label | ✓ shipped | unchanged |

### F. Profile + settings (`/me` hub)

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Profile / ORCID / displayName (U1a/b/c) | `/me#profile` | ✓ direct | HeaderBar avatar → `/me#profile` (`:251`) | ✓ shipped | unchanged |
| API Keys (L5) | `/me#api-keys` | ✓ hub-tile | `/me` landing → API Keys tile | ✓ shipped | unchanged |
| MCP keys | `/me#mcp` | ✓ hub-tile | `/me` landing | ✓ shipped | unchanged |
| Subscriptions | `/me#subscriptions` | ✓ hub-tile | `/me` landing | ✓ shipped | unchanged |
| Git credentials (G1-cred) | `/me#git-credentials` | ✓ hub-tile | `/me` landing | ✓ shipped | unchanged |
| User preferences (U1d) | inert; no UI yet | **✗ no UI** | — | ⚙ BE ✓ / UI pending | already honours rule; queued |
| Avatar upload (UI-012) | `/me#profile` | ✓ in-context | profile pane | ✓ shipped | unchanged |
| AI settings (placeholder) | `/me#ai-settings` | ✓ hub-tile | `/me` landing | ⚙ stub | acceptable |
| Semantic substrate quick-links | `/me#semantic` | ✓ hub-tile | `/me` landing | ✓ shipped | acceptable |

### G. Notifications + watches

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Notifications (NTF1a) bell + panel | inline | ✓ direct | HeaderBar:229-242 | ✓ shipped | unchanged |
| Collection watches (CW1) | inline | ✓ in-context | Collection detail star button `CollectionWatchButton` at `[collectionId]/index.vue:317` | ✓ shipped | unchanged |
| Watched-collections list | implicit via PersonalDigest | ✓ direct | home digest | ✓ shipped | unchanged |
| Collection SSE event feed (P13) | invisible composable | ✓ in-context | wired into CollectionSidebar refresh | ✓ shipped | unchanged |
| Notifications admin config (NOTIFICATIONS_ADMIN tile) | placeholder | ✓ hub-tile (admin) | `/admin#notifications` (`admin/index.vue:166`) | placeholder | admin-clause OK |

### H. AI features

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| AI1a `shepard-plugin-ai` admin config | `/admin#ai-configuration` | ✓ hub-tile (admin) | admin landing card (`admin/index.vue:190`) | ✓ shipped | unchanged |
| AI1b detect-anomalies button | inline | ✓ in-context | timeseries reference panel — flagged in commit `d8f6615b` "AI1b-UI detect-anomalies button" | ✓ shipped | unchanged |
| AI1b anomaly results overlay | inline on chart | ✓ in-context | post-button | ✓ shipped | unchanged |
| AI1c quality score field | none — score computed, never displayed | **✗ no UI** | — | ⚙ BE ✓ / UI pending | already honours rule; queued |
| Wiki-writer (WW1) "generate journal entry" | none yet | **✗ no UI** | — | ⚙ BE ✓ / UI pending | already honours rule; queued |
| TPL9 f(ai)²r AI prov capture | invisible — surfaces in `/admin#activity-log` | ✓ hub-tile | admin activity log tile | ✓ shipped | unchanged |
| TM1a wall-clock offset edit | none | **✗ no UI** | — | ⚙ BE ✓ / UI pending | already honours rule; queued |

### I. Admin pane (instance-admin only)

`/admin` tile grid at `frontend/pages/admin/index.vue:50-221`. Every shipped admin feature has a tile. The rule's admin clause ("count when reachable from `/admin` tile grid AND the admin role-grant is documented") is **satisfied for all of these**. Admin role grant is documented at `aidocs/51 §10` + `docs/admin/runbooks/07-add-instance-admin.md`.

| Tile (AdminFragments) | Pane | Status under rule |
|---|---|---|
| FEATURE_TOGGLES | FeatureTogglesPane | ✓ admin-tile |
| PLUGINS | PluginsAdminPane | ✓ admin-tile |
| INSTANCE_HEALTH | AdminMetricsCard | ✓ admin-tile |
| STORAGE_OVERVIEW | AdminStoragePane | ✓ admin-tile |
| TEMPLATES | AdminTemplatesPane | ✓ admin-tile **(but see surprise §6)** |
| SEMANTIC_REPOSITORIES | SemanticRepositoryPane | ✓ admin-tile |
| ONTOLOGY_BUNDLES | OntologyBundlesAdminPane | ✓ admin-tile |
| SEMANTIC_CONFIG (SEMA-V6-014) | SemanticConfigPane | ✓ admin-tile |
| SPARQL_PLAYGROUND | SparqlPlaygroundPane | ✓ admin-tile (duplicates `/semantic/sparql`) |
| USER_GROUPS | UserGroupsPane | ✓ admin-tile |
| INSTANCE_ROR | InstanceRorPane | ✓ admin-tile |
| PERMISSION_AUDIT_LOG (F3) | PermissionAuditLogPane | ✓ admin-tile |
| ACTIVITY_LOG (#68) | AdminActivityLogPane | ✓ admin-tile |
| provenance-dashboard (PROV1e) | `/admin/provenance` standalone | ✓ admin-tile (tile carries `path:` override) |
| UNHIDE (UH1a/b/c) | UnhideAdminPane | ✓ admin-tile |
| LEGACY_V1 (V1COMPAT.0) | AdminLegacyV1Pane | ✓ admin-tile |
| FILE_MIGRATION (FS1e1) | placeholder pane | ⚙ matrix-flagged; placeholder honours rule |
| SQL_TIMESERIES (P10) | AdminSqlTimeseriesPane | ✓ admin-tile |
| NOTIFICATIONS_ADMIN | placeholder | ⚙ placeholder honours rule |
| INSTANCE_ADMINS | placeholder | ⚙ placeholder honours rule |
| USERS_ORCID | placeholder | ⚙ placeholder honours rule |
| USERS_GIT | placeholder | ⚙ placeholder honours rule |
| AI_CONFIG (AI1a) | placeholder | ⚙ placeholder honours rule |
| BACKUP | placeholder | ⚙ placeholder honours rule |
| ONTOLOGY_ALIGNMENT (TPL3a-lite) | placeholder | ⚙ placeholder honours rule |
| INSTANCE_REGISTRY (FE-PROV) | `/admin/instance-registry` standalone | ✓ admin-tile |
| JUPYTER (J1e) | placeholder | ✓ admin-tile (corrects the in-session escalation — see surprise §1) |

### J. Scene graphs — the canonical violation

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Scene-graph browser + editor (SCENEGRAPH-REST-1-UI) | `/scene-graphs/[appId]` | **✗ deep-URL** | **ZERO inbound links in the entire frontend** (verified: grep over `frontend/components/**` + `frontend/pages/**` finds only the page itself; no nav, no DataObject affordance, no FileReference affordance). Compounded: there is no `/scene-graphs/index.vue` list page — even if a link existed, the user would need to know an `appId`. | ✓ shipped | **demote to alpha** until reachable. This is the row that triggered the rule. |

### J2. Plugin family — operator-facing surfaces

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Plugin admin list (PM1a/b/c/d/e) | `/admin#plugins` | ✓ hub-tile (admin) | admin landing → Plugins | ✓ shipped | unchanged |
| Plugin signature verifier (PM1b2) | invisible — state surfaces in PluginsAdminPane state-chip | ✓ in-context | admin Plugins | ✓ shipped | unchanged |
| V1COMPAT.0 admin pane | `/admin#legacy-v1` | ✓ hub-tile (admin) | admin landing → Legacy v1 | ✓ shipped | unchanged |
| V1COMPAT.0 deprecation banner | global header chrome | ✓ direct | rendered above all v1 traffic by `V1DeprecationBanner` | ✓ shipped | unchanged |
| RDK plugin annotations (RDK-PARSE-1 tier-1) | surfaces as SemanticAnnotations on FileReference | ✓ in-context | FileReference detail → annotations panel | ✓ shipped (tier-1) | unchanged |
| Thermography plugin annotations (OTVIS-PARSE-1 tier-1) | same | ✓ in-context | same | ✓ shipped (tier-1) | unchanged |
| URDF viewer plugin frontend (URDF-WEBVIEW-1 phase 1) | rendered via `/shapes/render?renderer=urdf` | ✓ in-context (transitive) | TS container → ViewRecipeBuilder | ✓ shipped (phase 1) | unchanged |
| KRL interpreter UI (KRL-INTERPRETER-06) | `RunKrlPreviewDialog.vue` modal | ✓ in-context | FileReference (`.src`) → "Run / preview" button | 🚧 in-flight (UI branch) | unchanged once shipped |
| Spatiotemporal plugin (SPATIAL-V6-001) | no user-visible UI yet | N/A | — | ✓ shipped (backend rename) | N/A |
| Analytics-ts plugin (AT1) | invisible — wraps AI1b math | ✓ in-context (transitive via AI1b button) | TS reference detail | 🚧 partial | unchanged |

### J3. Process / templates / orchestration

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Templates CRUD (T1a-T1f) | `/admin#templates` only | ✓ hub-tile (admin) | admin landing → Templates | ✓ shipped | rule-compliant; consider a non-admin `/me#templates` (out of scope) |
| Template picker in CreateDataObjectDialog | inline modal | ✓ in-context | Collection → "Create from template" | ✓ shipped | unchanged |
| ImportLock / IMP-LOCK status | none | **✗ no UI** | — | ⚙ BE ✓ / UI pending | already honours rule |
| ImportDiagnostics / IMP-DIAG | none | **✗ no UI** | — | ⚙ BE ✓ / UI pending | already honours rule |
| Import jobs (IMP1/IMP2) | none | **✗ no UI** | — | ⚙ BE ✓ / UI pending | already honours rule |

### J4. Storage + migration

| Feature | Route | Reachable? | Nav path | Status | Suggested |
|---|---|---|---|---|---|
| Storage overview (FS1a) | `/admin#storage-overview` | ✓ hub-tile (admin) | admin landing | ✓ shipped | unchanged |
| File migration trigger + status (FS1e1/e2/e3) | `/admin#file-migration` (placeholder) | ✓ hub-tile (admin, placeholder pane) | admin landing | ⚙ BE ✓ / UI pending | already honours rule |
| Presigned upload + progress UI (FS1c/FS1f, task #135) | inline in upload dialogs | ✓ in-context | file container → Upload | ✓ shipped | unchanged |
| RO-Crate export presigned URL (FS1g) | "Download as RO-Crate" button on Collection detail | ✓ in-context | Collection detail | ✓ shipped | unchanged |

### K. Pure backend / infrastructure rows — rule N/A

The following ~95 rows in `aidocs/44` ship backend-only surfaces (no user-visible UI to navigate to) and are **outside the scope of the rule**: §1 DB connectivity / migrations / Flyway / SSE recovery / health-check separation; §2 feature-toggle registry mechanics; §3 most auth/security gates (`Bearer` mangle, header sanitisation, key file perms, Cypher injection, RFC 7807, fail-closed Neo4j guard, OIDC claim path); §4 L2a/L2b/L2c substrate moves, DB-OPT5 list payload diet, TS-OPT1/2/3 wire optimisations, TS-CORE-SCHEMA-01, TS-SEMANTIC-01 dual-write, NEO-AUDIT-003 backfill; §5 NDJSON ingest, R2 body-form export shape, P21x patch semantics; §7 wire-only IO additions (QA-1, FAIR-1, FAIR2 stamp, SEMA-V6-001/002/003/007/009/013); §8 IMPORT-W1..W3, PROV-V15.1, PROV-USER-MIRROR-ENDPOINT, M4I-a/b/f, SHACL-1b HMAC chain; §9 PR-1..PR-3 wire renames; §10 `/users/me` preferences endpoint; §11 J1b backend, UI-020 bulk fetch; §12 TA1a CRUD, AT1 SPI, AI1v-Phase-1, TM1a backend, TPL9, PROMPT-h2 field; §13 admin CLI verbs, ROR1 backend, A0 instance-admin role, F3 backend, IMP1/2/LOCK/DIAG backends; §13a most file-storage SPI work (FS1a, FS1b, FS1c presigned, FS1d sidecar, FS1f frontend wiring, FS1g, P23 TTL cap, FS1e3 rollback); §13b CI gates, security gates, plugin SPI (PM1a-e, PM1b2), V6 version bump, PayloadKind SPI, plugin extractions (HDF5, Git, spatiotemporal); §13c (charts already in-context); §13d compose / Helm; §15 versioning policy; §16a EXP1; §17 ecosystem clients.

**For each of these:** the rule is satisfied trivially (no UI = no nav obligation). Reviewers should not treat the absence of a nav entry for these rows as a violation.

---

## Demoted rows

Rows currently shown as **`✓ shipped`** in `aidocs/44` that under the strict reading of the rule must drop to **`alpha`** until a nav entry lands. Numbers are small — the structural fix is one component edit.

| Row ID | Surface | What's missing under the rule |
|---|---|---|
| **SCENEGRAPH-REST-1-UI** | `/scene-graphs/[appId]` browser + editor | Zero inbound links. No `/scene-graphs/` index page either, so a "nav entry" must also let the user pick which scene-graph to open. Either (a) add a `/scene-graphs/index.vue` list page + Tools-menu entry, or (b) surface a "Open in scene-graph editor" affordance on FileReference detail (RDK / URDF FileReference → linked `:DigitalTwinScene`). |
| **TPL2b — `/shapes/render` view playground** | standalone playground page | Page only opens via `window.open` from `ViewRecipeBuilderDialog`. The in-context entry (TS container → ViewRecipeBuilder → render) covers the typical flow; but a user with a `templateAppId + focusShepardId` they got from MCP / docs / an API call has no UI path. Demotion is borderline: pass on in-context coverage; promote to ✓ once a Tools-menu entry exists. **Recommend: keep ✓ for the in-context flow; do not demote.** |
| **`/shapes/validate` SHACL playground (SHACL-1)** | playground page | Reachable via `/me#semantic` → Shape validator (`SemanticPane.vue:36`). 4 clicks deep but reachable. **Borderline ✓.** Not strictly a demotion candidate. Mention only as "consider promoting to Tools." |
| **`/snapshots/diff` playground** | playground page | Reachable via `/me#semantic` (`SemanticPane.vue:42`). Also reachable in spirit from `SnapshotsPane` if we add a "Diff with…" row action. **Borderline ✓.** Not strictly a demotion. |
| **N1f SPARQL UI** | `/semantic/sparql` | Reachable via `/me#semantic`. The matrix at line 206 shows the row as `⚙ BE ✓ / UI pending`. **Reconciliation finding: promote to ✓ in matrix** — the UI exists at `frontend/pages/semantic/sparql/index.vue` and is hub-tile-reachable. The runtime bug (404 on default "internal" repo) flagged by the operator is a separate defect — not a reachability gap. |

**The hard demotion is exactly one row: SCENEGRAPH-REST-1-UI.** The others either already self-flag pre-demotion or are borderline pass under in-context / hub-tile clauses.

---

## Cheapest fixes

Grouped by mechanism, ordered by leverage.

### Fix 1 — promote `SemanticPane` to a top-level "Tools" menu

**Leverage: HIGH.** One edit to `HeaderBar.vue` (add a "Tools" button at `:29` between Containers and the admin gate) plus one edit to wire a new `/tools` landing page (or reuse `SemanticPane.vue`'s pattern at top-level). Pulls Scene Graphs, Shapes Render, Shape Validator, Snapshot Diff, Vocabularies, SPARQL out of the avatar→`/me`→Semantic tile chain (4 clicks) into 1 click. Closes the gap on Scene Graphs.

```vue
<!-- HeaderBar.vue desktop nav addition -->
<v-btn class="nav-item d-none d-md-inline-flex" to="/tools">Tools</v-btn>
```

A new `frontend/pages/tools/index.vue` is a 30-line page using `SectionIndexLanding` (same shape as `me/index.vue`). Tiles: Vocabularies / SPARQL / Shape validator / Snapshot diff / **Scene graphs** / Shapes render playground. Mobile drawer mirror at `HeaderBar.vue:367`.

### Fix 2 — minimum-viable: add scene-graphs as a fourth quick-link in `SemanticPane.vue`

**Leverage: LOW (but trivial).** Edit `frontend/components/context/user/SemanticPane.vue:22-47` to add:

```ts
{ to: "/scene-graphs", title: "Scene graphs", subtitle: "Browse 3D scenes + URDF exports", icon: "mdi-graph-outline" },
```

Then ship a `/scene-graphs/index.vue` list page that calls `GET /v2/scene-graphs` (already implemented; cf. `composables/useSceneGraph.ts`). One Vue page + one tile = scene-graphs promoted back to beta.

Pick Fix 1 unless the user wants the absolute minimum.

### Fix 3 — surface scene-graphs on FileReference detail (in-context)

**Leverage: MEDIUM — domain-correct.** The RDK parser (`RDK-PARSE-1`) is the natural producer of scene-graph entities. When a FileReference is annotated `urn:shepard:rdk:role = scene-graph-source` (or similar), the FileReference detail page should show a "Open in scene-graph editor" button. Same idea for URDF FileReferences. Closes the discoverability gap in the exact spot a user lands after uploading an RDK file. Pairs naturally with Fix 1 — global Tools entry + in-context affordance.

### Fix 4 — add "Compare snapshots" action in `SnapshotsPane`

**Leverage: LOW, but right shape.** A "Compare" button on each snapshot row that opens `/snapshots/diff?a=…&b=…` with the two appIds preselected. Closes the "snapshot diff is hub-tile-reachable but you have to type two appIds" friction. Independent of Fix 1.

### Fix 5 — add "Download DMP" button beside `CiteThisCard`

**Leverage: LOW.** `FAIR7` ships a backend endpoint but no UI button. One `<v-btn>` next to the Cite-this card on Collection detail → `GET /v2/collections/{id}/dmp-snippet`. Brings FAIR7 from `⚙` to `✓`.

---

## Sub-rows for `aidocs/16`

Drop the following rows into `aidocs/16-dispatcher-backlog.md` under the appropriate group (`UX` / `SCENEGRAPH` / `TOOLS-NAV`):

```markdown
| **TOOLS-NAV-01** | Promote `SemanticPane` pattern to a top-level **Tools** menu in `HeaderBar.vue`. Add `frontend/pages/tools/index.vue` `SectionIndexLanding` with tiles: Vocabularies, SPARQL, Shape validator, Snapshot diff, Scene graphs, Shapes render playground. Mirror in mobile drawer. Closes the SCENEGRAPH-REST-1-UI rule violation at the structural level. | M | UX | open | 2026-05-30 |
| **SCENEGRAPH-NAV-01** | Ship `frontend/pages/scene-graphs/index.vue` — list page calling `GET /v2/scene-graphs` (composable `useSceneGraph.list()` to be added if absent). Each row links to `/scene-graphs/{appId}`. Pre-requisite for TOOLS-NAV-01 row to land cleanly. | S | UX | open | 2026-05-30 |
| **SCENEGRAPH-NAV-02** | Surface "Open in scene-graph editor" affordance on FileReference detail when the reference is annotated with an RDK / URDF / scene-source role. Closes the in-context discoverability gap. | S | UX | open | 2026-05-30 |
| **SHAPES-RENDER-NAV-01** | Add `/shapes/render` and `/shapes/validate` to the Tools menu (TOOLS-NAV-01). Optional in-context fix: keep the programmatic-only `ViewRecipeBuilderDialog` entry as the primary flow. | S | UX | open | 2026-05-30 |
| **SNAPSHOTS-DIFF-NAV-01** | Add a "Compare" row action in `SnapshotsPane.vue` that deep-links `/snapshots/diff?a=…&b=…` with two snapshot appIds pre-selected. | S | UX | open | 2026-05-30 |
| **DMP-DOWNLOAD-NAV-01** | Add a "Download DMP" button beside `CiteThisCard.vue` on Collection detail; calls `GET /v2/collections/{id}/dmp-snippet`. Promotes FAIR7 from ⚙ to ✓. | XS | UX | open | 2026-05-30 |
| **MATRIX-N1F-RECONCILE-01** | Reconcile `aidocs/44` line 206 (SPARQL proxy N1f) — the row claims `UI pending` but the playground page exists at `frontend/pages/semantic/sparql/index.vue` and is hub-tile-reachable via `/me#semantic`. Promote to ✓ shipped. Runtime bug (404 on default "internal" repo) is a separate defect tracked elsewhere. | XS | DOCS | open | 2026-05-30 |
```

---

## What I found that surprised me

1. **The session-context "JupyterHub is per-user instead of admin" complaint is not a nav-reachability violation.** The JupyterHub admin singleton (J1e) DOES have a tile at `AdminFragments.JUPYTER` (`admin/index.vue:216`). Backend admin singleton `:JupyterConfig` exists. What the operator probably noticed is that the *frontend pane behind the tile is a placeholder* (`PlaceholderFragmentPane` in the wiring at lines 320-323) — the matrix at line 264 actually claims "admin tile under **JupyterHub link-out** + per-row launch button on `.ipynb` rows in the unified table" as shipped. **Reconciliation finding:** the launch button is shipped (verified in the unified data refs table) but the admin pane behind the tile may be the placeholder rather than the real `AdminJupyterPane`. Worth a probe outside this audit's scope.

2. **The `/admin#ontology-bundles` complaint is also not a strict rule violation — it's a permissions UX issue.** The tile exists in the admin landing card grid (`admin/index.vue:88-93`). The non-admin failure mode is `UnauthorizedView` (the same 403-style page for the entire `/admin` route). The operator's underlying concern — "non-admin researchers can't browse the seeded vocabularies" — is a separate question: should there be a *read-only* `/semantic/ontologies` view for everyone, with admin write-paths gated? That's a feature-add, not a nav-reachability fix. Worth filing as `ONTOLOGY-BROWSE-NONADMIN-01` separately.

3. **The `/semantic/sparql` 404-on-default-internal-repo is a runtime defect, not reachability.** Out of scope for this audit; flagged for separate tracking.

4. **`AdminTemplatesPane` (templates feature T1a-T1f shipped) is admin-only.** Non-admin researchers cannot list / create / pick templates from anywhere in the UI except the template-picker dropdown in `CreateDataObjectDialog`. This is technically rule-compliant (the template surface is admin-curated), but worth a memo: power users may want a `/me#templates` browse-mine surface.

5. **Two surfaces hide in plain sight: `/configuration` and `/user`.** Both still exist in `frontend/pages/` but redirect to `/admin` and `/me` respectively. They're not nav-reachable (good) and not actively used (good), but they're also not deleted (drift risk). Filed as `ROUTE-CLEANUP-LEGACY-01` candidate — out of audit scope but worth noting.

6. **The `SnapshotsPane` Compare flow is the only reachable path to `/snapshots/diff` for users who don't know about `/me#semantic`.** And `SnapshotsPane` does not currently link to `/snapshots/diff` (verified via grep). So the only inbound link is via `SemanticPane`. That's two transitive jumps from Home (avatar → /me → semantic tile → snapshot diff tile → diff page = 4 clicks). The Compare-button fix (Fix 4) is independently useful and brings it to 2 clicks for the natural flow.

7. **The view-recipe / shape-render cluster (URDF, Trace3D, Thermography) survives the audit because of the TS container → ViewRecipeBuilderDialog → external-tab `/shapes/render` path.** This is in-context, but it's also fragile: the dialog opens the render page with `window.open` in a new tab, breaking back-button navigation. Users hitting `/shapes/render` after closing the tab have no way back via UI. **Not a rule violation under in-context clause, but a UX brittleness flag.**

8. **The container-kind index page at `/containers/[type]` is undocumented in matrix rows but does the heavy lifting.** Filing kind-specific container pages (Video, HDF, Spatial) under the generic `/containers` index means a user can find them by filtering — without it, video and HDF containers would be deep-URL-only. Worth a one-line matrix mention.

---

## Persona check (per the audit brief)

**Reluctant Senior Researcher** — does strict nav-reachability serve them? **Yes, mostly.** Their muscle memory wants `/collections` and `/containers` at the top; both are there. They will never use `/shapes/validate` or `/scene-graphs/{appId}`, so demoting those costs them nothing. The one place the rule *hurts* them: bookmarking. A power user who wants to drop a SPARQL playground or a snapshot-diff bookmark on their desktop loses nothing — the deep URL still works. The rule is a *minimum* reachability bar, not a "no other entry points." So no friction.

**Digital Native Researcher** — does it serve them? **Partially.** A digital-native postdoc prefers the API and rarely the UI, so nav surface matters less. But: when they DO use the UI, they want one-click to the tools. The current `/me#semantic` chain is 4 clicks; that's friction. They benefit most from Fix 1 (Tools menu). Counter-argument: power users will memorise the deep URL after one visit and never click the nav again — so the structural cost of Tools-menu work pays off only for first-time discovery + onboarding. Worth doing, but not urgent.

**Verdict.** The rule is well-shaped. The fix list is short. SCENEGRAPH-REST-1-UI is the one strict demotion; everything else is reachable today (often via the `/me#semantic` quasi-hub) or already self-flagged as `⚙ UI pending` in the matrix. The high-leverage move is TOOLS-NAV-01: one component edit lifts six features from 4-click depth to 1-click depth and gives Scene Graphs a home.

---

## Bonus — `aidocs/44` rows that should be cross-referenced

A handful of in-flight or recently-shipped rows in `aidocs/44` would benefit from a one-line addendum referencing this audit's findings. Suggested edits (atomic, non-content-mutating):

| `aidocs/44` row | Current line | Suggested addendum |
|---|---|---|
| §7a SPARQL proxy (N1f) | line 206 — claims `⚙ BE ✓ / UI pending` | promote to ✓ shipped (UI exists at `frontend/pages/semantic/sparql/index.vue`, hub-tile-reachable). File `MATRIX-N1F-RECONCILE-01`. |
| §9 SCENEGRAPH-REST-1-UI (implicit row under URDF/RDK cluster) | not currently rowed | add row: "Scene-graph browser + editor — `/scene-graphs/{appId}` UI shipped 2026-05-30; **alpha until SCENEGRAPH-NAV-01 lands** (no nav inbound; see `aidocs/agent-findings/topnav-reachability-reconciler.md`)" |
| §11 J1d edit history | line 266 — `⚙ BE ✓ / UI pending` | already honours rule |
| §12 AI1c quality score | line 278 — `⚙ BE ✓ / UI pending` | already honours rule |
| §12 WW1 wiki-writer | line 284 — `⚙ BE ✓ / UI pending` | already honours rule; queue UX1 follow-up |
| §13c FAIR7 DMP-snippet (in §7 line 185) | claims ✓ shipped | reconcile: backend ✓ but no UI button; demote to ⚙ until `DMP-DOWNLOAD-NAV-01` ships. |
| §16a TPL2b shapes/render | line 493 — claims ✓ | borderline; pass via TS-container in-context. Note in row description that the standalone playground is opened via `window.open` only. |

## Where the rule's clauses bite hardest

- **"Hub-tile that is itself top-nav-reachable"** is the workhorse clause. The 28-tile `/admin` grid handles all admin features cleanly; the 7-tile `/me` grid + the 4-link `SemanticPane` quasi-hub handle most everything else. The pattern works.
- **"Subnav from a top-nav-reachable page"** is doing real work via Collection detail (snapshots, lineage, prov graph, watches, citation card, completeness widget) and DataObject detail (every reference panel + Lab Journal + provenance graph + AnnotationDialog + Annotate button). This is where Shepard's reachability story is strongest.
- **"Deep-URL-only stays alpha"** triggers exactly once today: SCENEGRAPH-REST-1-UI. The other candidates (`/shapes/render`, `/snapshots/diff`, `/shapes/validate`) all squeak through via in-context or hub-tile coverage. The marginal cost of TOOLS-NAV-01 is small enough that it's worth landing anyway — converts 4-click depth to 1-click depth for the research-tool cluster.
- **"Admin features count when reachable from `/admin` tile grid AND the admin role-grant is documented"** — both conditions met for every shipped admin feature. The grant runbook is `docs/admin/runbooks/07-add-instance-admin.md`. Reviewers should accept any new admin feature whose tile is added to `landingCards` in `admin/index.vue` AND whose role gate is documented.

## What the rule does NOT cover (worth memo, out of scope)

- **Discoverability quality.** Reachable ≠ findable. Tucking Scene Graphs into a 4-click `/me#semantic` chain is technically reachable; whether `flo` would ever discover it in a session is a different question. The Tools-menu fix (Fix 1) addresses discoverability, not just reachability.
- **Empty-state UX on hub-tile-reachable pages.** A non-admin user navigating to `/admin#ontology-bundles` sees an `UnauthorizedView` instead of a "this is admin-only — here's how to request access" message. The rule says nothing about this — but the operator's complaint suggests the empty / unauthorised state should improve.
- **Read-only browse paths for admin-curated content.** Templates, ontology bundles, and the activity log are admin-only today. The rule is satisfied (admin-tile-reachable), but power users may want read-only browse. Separate feature requests, separate review.

## Anchor citations

- Top nav code: `frontend/components/layout/HeaderBar.vue:26-38` (desktop), `:357-367` (mobile drawer), `:223-225` (overflow menu).
- Admin tile grid: `frontend/pages/admin/index.vue:50-221`.
- `/me` tile grid: `frontend/pages/me/index.vue:19-67`.
- Semantic quick-links hub: `frontend/components/context/user/SemanticPane.vue:22-47`.
- Scene-graph page: `frontend/pages/scene-graphs/[appId].vue` (entire file). No inbound `to="/scene-graphs"` link anywhere in `frontend/components/**` or `frontend/pages/**`.
- ViewRecipeBuilderDialog → `/shapes/render`: `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue:91,106,127`.
- Snapshot diff cross-link: `frontend/components/context/user/SemanticPane.vue:42`.
- Live `/` HTML href set (anon view): Home / Collections / Containers / Help / About / API Docs / `/me#profile` / DLR mark / ROR.

End.
