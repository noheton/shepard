---
stage: deployed
last-stage-change: 2026-05-23
---

# 85 — UI Overhaul: Critique, Opportunities, and Roadmap

**Status.** Living design doc — initial draft 2026-05-17.
**Audience.** Contributors, frontend engineers.
**Relates to.** `aidocs/33` (frontend workflow baseline), `aidocs/58` (ergonomics cluster),
`aidocs/42` (researcher-facing vision), `aidocs/16` (backlog), `aidocs/ops/86-ui-changelog.md` (change log).

---

## 1. Current State Critique

### 1.1 Architecture is sound

The frontend is a Nuxt 3 SPA on Vuetify 3, composed cleanly:

- Three layout tiers: header-only, collection (sidebar + content), pane (menu + content panel)
- Auto-import conventions for components and composables
- Composables isolate API calls (`useShepardApi`, `useV2ShepardApi`)
- Theme system with light/dark, Work Sans, semantic colour tokens
- `ExpansionPanelItem` pattern for collapsible sections

The codebase is consistent and readable. These strengths should be preserved — the overhaul
is additive, not a rewrite.

### 1.2 Navigation friction — the core problem

Every meaningful action in the current UI follows one of two patterns:

```
Pattern A (modal stepper):   browse → click "Add" → 5–7 modal steps → submit → refresh
Pattern B (pane navigation): header nav → pane menu → scroll → click
```

Neither pattern is wrong, but together they produce a **high click cost for discovery and
for the most frequent researcher operations** (open a dataset, attach a file, check lab notes).

Specific pain points (sourced from aidocs/33 §2 and the Explore audit):

| # | Pain | Evidence |
|---|---|---|
| P1 | Search requires hand-written JSON predicates | `pages/search/index.vue`; aidocs/33 §1 |
| P2 | Data reference creation is 5+ clicks + modal-stepper after three menus | aidocs/33 W4 |
| P3 | Collection sidebar has no search/filter — unwieldy past 20 objects | Explore audit |
| P4 | Container/Collection duality: users must navigate separately to containers to discover data | aidocs/33 §3, aidocs/58 §5 |
| P5 | Newly-shipped features not discoverable until the user happens on the right pane | Audit (Snapshots, Video, Templates) |
| P6 | JupyterHub URL must be configured per-user in a buried cog | `DataObjectNotebooksPane.vue` |
| P7 | Git credentials require a profile round-trip before git reference creation works | User profile pane; aidocs/38 |
| P8 | Publishing button appears only on data object detail, no discovery path | `PublishButton.vue`; aidocs/KIP1 |

### 1.3 Features shipped in backend but invisible in UI

The following are implemented but either not wired into the UI at all or wired too obscurely:

| Feature | Backend status | UI status |
|---|---|---|
| Snapshots (V2a–V2e) | ✓ Full CRUD, diff, pinned read | ✗ No UI |
| Video references (VID1a) | ✓ Upload + ffprobe metadata | ✗ No UI |
| Templates (T1a–T1f) | ✓ Full admin CRUD, instantiation | ✗ No UI |
| PayloadVersion history (PV1a) | ✓ Per-revision download | ✗ No UI |
| RO-Crate export (G1c) | ✓ PINNED_SNAPSHOT mode | ✗ No download button in UI |
| Admin metrics summary (A3b1) | ✓ `/v2/admin/metrics-summary` | ✗ Not surfaced in admin page |
| Unhide publish integration (UH1) | ✓ Config + harvest API | ✗ No UI |

---

## 2. Opportunities

### 2.1 Quick wins (≤ 1 day each)

| ID | Opportunity | Where |
|---|---|---|
| QW1 | **Global search bar in HeaderBar** — replace Advanced Search nav link with a Cmd+K / type-to-search input that queries the existing search API | `HeaderBar.vue` |
| QW2 | **Sidebar search/filter** — add a text input at the top of `CollectionSidebar.vue` to filter data objects by name client-side | `CollectionSidebar.vue` |
| QW3 | **JupyterHub URL in user profile** — add `editor.preferredJupyter` field to `ProfilePane.vue` alongside ORCID; this avoids the cog-hunt in DataObjectNotebooksPane | `ProfilePane.vue` |
| QW4 | **Git credentials shortcut** — when GitReferencesPane has no credentials, show "Add git credentials → Profile → Git Credentials" inline link | `GitReferencesPane.vue` |
| QW5 | **Publish deep link** — add a `/help/publish` route and a "Learn about publishing" tooltip on the Publish button | `PublishButton.vue` |
| QW6 | **Admin metrics card** — surface `GET /v2/admin/metrics-summary` data as a simple card in `/admin` page | `AdminPage.vue` |

### 2.2 Medium efforts (1–3 days each)

| ID | Opportunity | Notes |
|---|---|---|
| UI1a | **Snapshots UI** — create/list/delete snapshots per collection; show manifest; diff viewer | Backed by V2b–V2e endpoints |
| UI2a | **Templates browser** — admin page for browsing, creating, editing templates; instantiate-from-template on collection creation dialog | Backed by T1a–T1f |
| UI3a | **Video reference viewer** — inline HLS player in FileReferencePage or a dedicated video pane | Backed by VID1a; HLS.js already available |
| UI4a | **PayloadVersion history panel** — on FileReference detail page, show version list with download-by-version links | Backed by PV1a |
| UI5a | **Drag-and-drop onto sidebar tree** — move data objects by dragging within the tree (reparent via PATCH) | aidocs/58 §2 |
| UI6a | **RO-Crate export button** — "Download as RO-Crate" action on collection or data object detail | Backed by G1c |

### 2.3 Larger bets (design required)

| ID | Opportunity | Notes |
|---|---|---|
| UI7 | **Graph view** — interactive force-directed graph of collection → data object → reference → annotation relationships | aidocs/58 §3; candidate: vue-force-graph |
| UI8 | **Inline data reference creation from container view** — "Attach to data object" button in container file list instead of only in data object detail | aidocs/58 §4 |
| UI9 | **Snapshot diff viewer** — visual diff of two snapshot manifests (added/removed/changed entities with revision badges) | V2e diff endpoint ready |
| UI10 | **@-mention internal references** — Tiptap extension for `@entity/appId` autocomplete in lab journal | aidocs/58 §4 |
| UI11 | **Unified Unhide / publish status panel** — show current publish state, harvest queue, PID metadata in one place | UH1 + KIP1 |

---

## 3. Component System Gaps

Beyond individual features, several structural improvements would reduce code duplication and
improve consistency:

| Gap | Current state | Target state |
|---|---|---|
| No global Cmd+K command palette | – | `CommandPalette.vue` composable + hotkey wiring |
| ExpansionPanel order not user-configurable | Hard-coded | Persisted ordering via user preferences |
| No skeleton loaders | Spinner on all loads | Vuetify skeleton-loader for primary content areas |
| No toast queue | Single snackbar ref | `useNotifications()` composable with queue + undo action |
| No breadcrumb entity names | "collectionId" literal | Fetch entity name and replace ID segment |
| Data table has no column sorting persistence | Per-render default | Persist column sort to localStorage/user prefs |

---

## 4. UI Backlog Identifiers

New backlog IDs for dispatch (`aidocs/16`):

| ID | Title | Size | Gate |
|---|---|---|---|
| QW1 | Global search bar in HeaderBar | XS | P-series search API |
| QW2 | Sidebar object filter | XS | none |
| QW3 | JupyterHub URL in profile | XS | U1d (preferences, done) |
| QW4 | Git credentials shortcut in GitReferencesPane | XS | G1-cred done |
| QW5 | Publish deep-link tooltip | XS | KIP1e done |
| QW6 | Admin metrics card | XS | A3b1 backend done |
| UI1a | Snapshots UI (create / list / delete + diff view) | M | V2b–V2e done |
| UI2a | Templates browser + instantiation UI | M | T1a–T1f done |
| UI3a | Video reference inline viewer | S | VID1a done |
| UI4a | PayloadVersion history panel on FileReference | S | PV1a (in-flight) |
| UI5a | Drag-and-drop tree reparenting | M | aidocs/58 §2 |
| UI6a | RO-Crate export download button | XS | G1c done |
| UI7 | Graph view | XL | design pending |
| UI8 | Inline attach-to-data-object from container | M | aidocs/58 §4 |
| UI9 | Snapshot diff viewer | M | V2e done |
| UI10 | @-mention internal references in lab journal | M | aidocs/58 §4 |
| UI11 | Unified publish/Unhide status panel | M | UH1 + KIP1 |

---

## 5. Screenshot Pipeline

An E2E + screenshot pipeline exists at `/opt/shepard/e2e/` using Playwright. Current state:

- **Tests**: smoke (home + healthz), auth, navigation, collections — all against `BASE_URL`
- **CI**: Not wired into main CI; artifacts stored in `test-results/`
- **Screenshot capture**: `onlyOnFailure` mode (no success screenshots)

For the UI overhaul tracking, the pipeline should:

1. Add success screenshots on key pages (toggle to `on` for visual regression runs)
2. Wire one `npx playwright test --reporter=html` job into `ci.yml` on push to `main`
3. Track before/after screenshots per UI PR in the `aidocs/ops/86-ui-changelog.md`

See `aidocs/ops/86-ui-changelog.md §2` for the screenshot convention.

---

## 6. Recommended Priority Order

Phase 1 (quick wins, no design risk):
1. QW2 — sidebar filter (10 lines of code, immediate ergonomic gain)
2. QW3 — JupyterHub URL in profile
3. QW6 — admin metrics card
4. UI6a — RO-Crate export button
5. QW1 — global search bar (slightly more involved; needs debounce + results dropdown)

Phase 2 (shipped-backend exposure):
6. UI4a — PayloadVersion history panel (gated on PV1a merge)
7. UI1a — Snapshots UI (V2b–V2e all done)
8. UI2a — Templates browser
9. UI3a — Video viewer

Phase 3 (larger UX rethinks):
10. UI5a — drag-and-drop tree
11. UI9 — snapshot diff viewer
12. UI8, UI7, UI10, UI11 as capacity allows

---

## 7. Open Questions — Resolved

1. **Collection/container duality** — surfaced inside the collection sidebar as a second tree
   (not a separate top-level nav). The duality design enables "views" on data from different
   perspectives; a separate `/containers` top-level nav breaks the conceptual model for
   non-IT users. Full design rationale in `aidocs/ops/87-collection-container-duality.md`.
2. **Global command palette scope** — shepard-wide: collections, data objects, and containers.
3. **Template instantiation UX** — migrate to a separate `/templates` route. The current
   CreateCollectionDialog wizard becomes "use basic template" (one option among many).
   Customized collection creation forms with mandatory fields (e.g. cost-center number) become
   template-defined. This makes the system more flexible and is easier to maintain in future.
4. **Snapshot UI home** — collection detail page tab, visible to owners and managers only.
5. **Drag-and-drop ordering (UI5a)** — sort order must be stored server-side so all users see
   the same ordering. "Resort" is a Manage-level permission on the Collection. This is a
   design constraint for UI5a implementation.
