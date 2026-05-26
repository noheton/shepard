---
stage: fragment
last-stage-change: 2026-05-23
---

# 33 — Frontend / UI Workflow Analysis + Suggestions

Snapshot 2026-05-07. Read against the code in `frontend/` (Nuxt 3
+ Vue 3 + Vuetify 3 — *not* Angular, despite earlier wording in the
brief) and the generated TypeScript client at
`backend-client/` (consumed via the `@dlr-shepard/backend-client`
file dependency in `frontend/package.json:11`). Backlog IDs (L4 / R5
/ R8 / R2 / P10 / P12 / P13 / P16) refer to the dispatcher backlog;
forward design-doc references fall back to `aidocs/13`,
`aidocs/14`, `aidocs/15`, and to the user inputs at
`aidocs/input/input_raw.md`.

## 1. Summary

The frontend is a Nuxt 3 single-tenant Vuetify app whose every
non-trivial workflow is "click → modal stepper → submit", driven by
direct calls into the auto-generated TS client (no shared service
layer; each page wires its own `useShepardApi(SomeApi)` calls). The
single highest-leverage UX fix is to **replace the JSON-only
"Advanced Search" page (`frontend/pages/search/index.vue`) with a
search-as-you-type bar that lives in the header**: today the user
must hand-write a JSON predicate (with no schema-aware editor and a
rigid initial template) before any search is possible, which gates
every discovery workflow behind a JSON skill the typical researcher
does not have (cf. `aidocs/input/input_raw.md:91`,
`aidocs/input/input_raw.md:96`, `aidocs/semantics/13-search-improvements.md`).

## 2. Workflow inventory

The header (`frontend/components/layout/HeaderBar.vue:7-15`) names
five top-level destinations: Collections, Containers, Configuration,
Advanced Search, About. Pages live under `frontend/pages/`. The
inventory below names the 11 user-facing workflows visible from the
routing + components.

| # | Workflow | Pages | Key API calls (generated client) | Click cost (rough) |
|---|---|---|---|---|
| W1 | Browse / find a collection | `pages/collections/index.vue`, `components/context/collection/list/CollectionListPage.vue:5-55`, `useSearchCollections.ts:36-55` | `SearchApi.searchCollections` | 1 click + free-text search |
| W2 | Create a collection | `components/context/collection/create-dialog/CreateCollectionDialog.vue:25-73` (2-step `StepperDialog`) | `CollectionApi.createCollection`, `getCollectionPermissions`, `editCollectionPermissions` (3 round-trips) | 5+ (Create → Name → Description → Permission → Next → Attributes → Create) |
| W3 | Open / edit a collection (description, attributes, lab journal) | `pages/collections/[collectionId]/index.vue`, `EditCollectionDescriptionDialog.vue`, `EditCollectionAttributesDialog.vue` | `CollectionApi.getCollection`, `updateCollection` per panel | 3+ per field (Edit → modal → Save) |
| W4 | Navigate hierarchical data objects | sidebar tree at `components/context/sidebar/CollectionSidebar.vue:101-117`, `useTreeviewItems.ts:12-25` | `DataObjectApi.getAllDataObjects` (per parent), `getDataObject` (lookup-by-id for path expansion) | 1 per level — N for a deep tree |
| W5 | Create a data object | `CreateDataObjectDialog.vue:67-135` (2-step stepper) | `DataObjectApi.createDataObject` | 5+ |
| W6 | Add a data reference (file / structured / timeseries) | `CreateDataReferenceDialog.vue:36-127` + pickers | `FileContainerApi.getAllFiles`, `TimeseriesContainerApi.getTimeseriesOfContainer`, `StructuredDataContainerApi.getAllStructuredDatas`, then `FileReferenceApi.createFileReference` / `TimeseriesReferenceApi.createTimeseriesReference` / `StructuredDataReferenceApi.createStructuredDataReference` | 6+ (Add → name → container → list-load → pick item(s) → submit) |
| W7 | Upload file(s) into the collection | `DataObjectFileUpload.vue` + `DataObjectFileUploadDialog.vue:152-189` | per-file `FileContainerAccessor.uploadFile` (sequential `Promise.all`), then `addFileReference`, optional `updateCollection` | 3 (drop → confirm name/container → upload) — but upload is unbounded per file |
| W8 | View timeseries metrics + chart | `pages/.../timeseriesereferences/[timeseriesReferenceId]/index.vue:60-220`, `ShowTimeseriesReferenceDialog.vue:62-203` | `TimeseriesReferenceApi.exportTimeseriesPayload` (CSV download), per-series `useFetchTimeseriesReferenceMetrics`, `useFetchTimeseries`, `useFetchTimeseriesAnnotations` | 4+ (open ref → check rows → "Metrics & Plotter" → expand each series) |
| W9 | Annotate any entity with a semantic term | `AddAnnotationDialog.vue:63-85`, `SemanticAnnotationList.vue` | `SemanticRepositoryApi.getAll*` then per-entity-type annotation API | 4 (Annotate → paste IRI → pick repo → ×2 → Add) |
| W10 | Edit permissions / share | `EditPermissionsDialog.vue:90-166`, `MemberAutocomplete.vue` | per-entity `getPermissions`, `editPermissions`, `getOwner`, `getRoles` | 4+ (Edit → search member → role → Add → Save) |
| W11 | Run advanced (JSON-predicate) search | `pages/search/index.vue:107-146`, `searchService.ts:46-76` | `SearchApi.search`, `SearchApi.searchContainers` | 2-30 depending on whether the user has a working JSON template |
| W12 | Manage user / API keys / subscriptions / configuration | `pages/user/index.vue`, `pages/configuration/index.vue`, `ApiKeyPane.vue`, `SubscriptionsPane.vue`, `UserGroupsPane.vue`, `SemanticRepositoryPane.vue` | per-pane CRUD against `UserApi`, `UserGroupApi`, `SemanticRepositoryApi`, `SubscriptionApi` | 3+ per change |

There is a `common/ComingSoon.vue` placeholder used in places (search
the components tree); a "lab journal" (`LabJournalNewEntry.vue`) is
inline-editing only on data objects and collections; "spatial data"
references are out of scope (`CreateDataReferenceDialog.vue:139-141`
explicitly says `not implemented yet`).

## 3. Friction by workflow

W1 — Collection list / search.
- **Friction (search shape).** The "search" is an Enter-to-submit text field
  (`components/common/data-table/SearchField.vue:29-30`) — no
  type-ahead, no fielded filters, no semantic-annotation filter
  surfaced at all. Search is over a SQL-like predicate built by
  `buildQueryString` per a single text parameter (matches by ID,
  Name, or Created by — see placeholder
  `CollectionSearchField.vue:27`).
- **Friction (pagination).** `useSearchCollections` requests `page=N`,
  size=20, then hides total count behind a vague "Found X results"
  hint (`useSearchCollections.ts:79-86`). The table footer at
  `CollectionList.vue:111-119` shows numbered pages; the user has no
  per-page selector.

W2 — Create collection.
- **Click cost.** `StepperDialog` mandates two steps even though
  step 2 is just "Attributes" (`CreateCollectionDialog.vue:114-127`).
  Step 1 already mixes "Properties" with a Permission-Type select —
  the split is artificial.
- **Round-trip cost.** Three sequential round-trips
  (`createCollection`, `getCollectionPermissions`,
  `editCollectionPermissions`) on a single Create button click
  (`CreateCollectionDialog.vue:25-73`). Any failure between them
  leaves the collection orphaned with default permissions and no
  rollback.

W3 — Collection detail editing.
- **Click cost (modal-per-field).** Description and attributes each
  open their own modal
  (`pages/collections/[collectionId]/index.vue:64-99`) instead of
  in-place "click to edit" — three clicks to change one description.
- **Click cost (lab journal).** New journal entries require focus on
  textarea → second textarea opens with editor → Save
  (`LabJournalNewEntry.vue:50-75`). It's two-step for a free-text
  note.

W4 — Tree navigation.
- **Click cost.** Each level fires a separate `getAllDataObjects?parentId=...`
  call (`useTreeviewItems.ts:124-144`). For deep hierarchies the
  user pays one round-trip per chevron click and must wait for
  `loadChildrenOfItem` to resolve before the chevron expands.
- **Discovery.** No way to jump to a data object by name from the
  tree — only the global Advanced Search page; the tree itself has
  no filter (cf. `aidocs/input/input_raw.md:96` — proposed
  search-as-you-type with switchable tree / graph view).
- **Refresh cost.** `refreshItems` re-loads every previously opened
  branch (`useTreeviewItems.ts:75-83`); after creating a child via
  the context menu the whole loaded subtree refetches.

W5 — Create data object.
- **Stepper friction.** Same as W2 — two steps for a flow that is
  one form. `parentId` is preset only when entered from the
  context menu; the "Predecessor" picker (`PredecessorInput.vue`)
  uses an autocomplete with a placeholder `-1` entry that has to be
  cleaned up at submit time
  (`CreateDataObjectDialog.vue:43-44`) — that placeholder is a
  data-model leak into the UI.

W6 — Add data reference.
- **Click cost.** Opening the dialog triggers a full
  container listing (`getAllFiles` / `getTimeseriesOfContainer` /
  `getAllStructuredDatas`) — *all* items, no pagination on the
  picker (`FileReferencePicker.vue:33-48`). For a container with
  thousands of files this is a long blocking call with only a
  generic spinner.
- **No drag-and-drop.** The dialog is a strict form. The
  alternative `DataObjectFileUpload` drag-and-drop creates a new
  reference from local files but cannot pick an existing OID.
- **Spatial data missing.** Console-logged stub
  (`CreateDataReferenceDialog.vue:139-141`).

W7 — File upload.
- **Progress feedback.** The dialog shows a card-level loading bar
  (`<v-card :loading="uploading">`,
  `DataObjectFileUploadDialog.vue:216`) and a `successCount` counter
  but no per-file progress bar. Errors degrade to "only N of M
  uploaded successfully" (`DataObjectFileUploadDialog.vue:158-164`)
  — no listing of *which* files failed or why.
- **Sequential semantics, parallel UI.** `Promise.all(map(uploadFile))`
  fires uploads in parallel even on slow connections; no chunking
  / no resumable uploads; no abort. P12 (S3-presigned) addresses
  this server-side.

W8 — Timeseries detail / metrics / chart.
- **Click cost.** "Metrics and Plotter" is a separate modal
  (`ShowTimeseriesReferenceDialog.vue`) that lazy-fetches per-series
  metrics + annotations + actual datapoints in three parallel
  chains (`useFetchTimeseriesReferenceMetrics`, `useFetchTimeseries`,
  `useFetchTimeseriesAnnotations`,
  `ShowTimeseriesReferenceDialog.vue:69-122`). On a 7-series
  selection that is 21 round-trips before the chart renders.
- **Download path.** `exportTimeseriesPayload` streams CSV through
  the API and `URL.createObjectURL`s a blob
  (`...timeseriesReferenceId/index.vue:68-81`,
  `utils/downloadFile.ts:3-9`); the whole CSV is buffered in
  browser memory. There is no "Download as Excel" / no "Download
  for time-range" / no presigned redirect.
- **Selection cap.** Hard-coded `MaxSelectableItems = 7`
  (`...index.vue:13`) — a UX guardrail driven by the chart-color
  palette length (`ShowTimeseriesReferenceDialog.vue:167-175`),
  surfaced as a counter in the toolbar.

W9 — Semantic annotation.
- **Form complexity.** The user must paste an IRI (twice — for
  property and value), and pick a repository (twice). Tooltips
  literally say "Refer to your ontology server to get the link"
  (`AddAnnotationDialog.vue:128-131`) — i.e. leave shepard, find an
  IRI, come back. This is precisely the case that
  `aidocs/archive/semantics/14-semantic-improvements.md` wants to fix with
  search-as-you-type over labels.
- **Validation.** Submit is gated only on "all four non-empty"
  (`AddAnnotationDialog.vue:48-54`); no IRI sanity check, no
  preview of the term name, no detection of duplicates.

W10 — Permissions.
- **Form complexity.** The dialog mixes
  *general permission type* with *per-member additional
  permissions* (`EditPermissionsDialog.vue:193-285`); roles auto-
  imply (manager → writer → reader, `EditPermissionsDialog.vue:112-124`)
  but the implication is invisible in the UI until the user clicks
  Add and the chips appear.
- **Loading feedback.** Three sequential awaits (`fetchPermissions`,
  `fetchRoles`, `fetchOwner` — `EditPermissionsDialog.vue:33-40`)
  with no spinner — the dialog opens, blank, until they all
  resolve.

W11 — Advanced Search.
- **The single biggest friction.** The default state is a
  hard-coded JSON template
  (`pages/search/index.vue:24-43`) that the user must edit. There
  is no schema autocomplete; switching to "Code view" toggles a
  separate `JsonEditor` (`pages/search/index.vue:281-285`). The
  page asks the user to learn the predicate DSL (cf. the
  `input_raw.md:4822` user thread asking for help with a query).
- **No saved searches, no history.** Submitted queries serialise
  into the URL (`setAllQueryParam`, lines 148-161) but there's no
  list of past searches.
- **Result rendering.** `SearchResultList` (one column of links) —
  no preview, no facets, no per-type grouping.

W12 — User / configuration.
- **Click cost.** Each pane is a separate URL fragment
  (`#profile`, `#apikeys`, `#subscriptions`,
  `userMenuItems.ts`) under a `PaneLayout` — usable but no
  search/filter when there are many keys / subscriptions.

## 4. The five workflows to fix first

Ranked by leverage (impact × frequency × backlog alignment).

1. **W11 → search-as-you-type with a switchable tree / graph view
   (L4).** Replace `pages/search/index.vue` with a header-mounted
   typeahead that delegates to the unified search endpoint from
   `aidocs/13`. Two-line change in the user model: type a term,
   see ranked hits across collections / data objects / references /
   ontology terms; cmd-K opens a tree / graph view of the same
   results. Depends on the unified search endpoint (`aidocs/13 §3-4`)
   and on the term-search facet from `aidocs/14 §6` for ontology
   completion. Size: M (UI) + L (backend already tracked under
   `aidocs/13`).

2. **W6 / W7 → unified "Add data" panel (no new backlog ID — folds
   into existing UX backlog).** Collapse `DataObjectFileUpload` and
   `CreateDataReferenceDialog` into one panel where the choice is
   "from your machine" vs "from an existing container", with the
   container picker pre-filtered to the collection's
   `defaultFileContainerId`. Removes the
   "what-is-a-container?" wall the user hits today
   (`CreateDataReferenceDialog.vue:230-237`). Depends on no backend
   work; benefits from P12 (presigned S3 PUT) once that ships. Size:
   M.

3. **W7 → live progress on uploads (P13, SSE).** Replace the
   single card-level spinner with per-file progress + abortable
   uploads, driven from the SSE change-feed proposed in P13. Show a
   persistent "uploads" tray (Submissions-Panel-style — see
   `aidocs/input/input_raw.md:1028`). Depends on P13 (SSE) and
   ideally P12 (presigned). Size: M (UI), S once SSE lands.

4. **W8 → "Download as Excel / for time-range" using P10
   `to_excel` + P16 convenience client.** Replace the single
   "exportTimeseriesPayload → CSV" button with a small panel that
   builds a SQL projection (or a structured "between t1 and t2"
   form) and POSTs to the SQL-over-HTTP endpoint, the response
   streamed as `xlsx`. Depends on P10 + P16 (`shepard-ts.to_excel`).
   Size: S (UI on top of P10).

5. **W2 / W3 / W5 → in-place editing + stepper-collapse for
   single-page forms.** Replace `StepperDialog` with a single-form
   `FormDialog` where the second "step" is just an expandable
   "Attributes" section (Vuetify `v-expansion-panels` already in
   use elsewhere). For W3 (description, attributes), turn the
   modal into click-to-edit inline — Tiptap is already a dependency
   (`frontend/package.json:14-30`). Depends on no backend; this is
   pure UX cleanup. Size: S-M.

## 5. Integration with backend evolution

Each post-R2 / post-P10 / post-P12 / post-P13 / post-P16 backend
shift unlocks a concrete frontend simplification.

- **P10 SQL → "Download as Excel" button (W8, W6 structured-data
  preview).** Today CSV-only via `exportTimeseriesPayload`. After
  P10 ships SQL-over-HTTP plus a `to_excel` shortcut, the UI can
  add a small "Download as Excel" / "Download as CSV" / "Download as
  Parquet" radio next to the existing button, posting a SELECT to
  the new endpoint. The browser handles the response as a stream;
  no need for the `URL.createObjectURL` blob path
  (`utils/downloadFile.ts:3-9`).
- **P12 S3-presigned → "Download large file" + "Upload large file"
  (W7, W8 CSV).** Replace the
  stream-through-API path (`getFilePayload`,
  `pages/.../filereferences/.../index.vue:97-108`) with a 302
  redirect to the presigned URL; replace `containerAccessor.uploadFile`
  with `getPresignedPut → fetch(PUT, file)`. Frontend code shrinks;
  upload progress becomes a native `XMLHttpRequest.upload.onprogress`
  bar.
- **P13 SSE → live progress on long operations (W2 multi-call,
  W7 multi-file, future W4 bulk-create).** Open an EventSource on
  the user's session. Workflows that currently show a binary
  "loading / done" toast become "1 of N (filename) — Cancel". The
  same SSE tail can drive a "Notifications" badge in the header
  (no equivalent today).
- **P16 `shepard-ts` convenience client → kill the per-component
  glue.** Today every page imports `useShepardApi(SomeApi)` and
  hand-writes `.then / .catch / handleError` (look at
  `pages/.../timeseriesereferences/.../index.vue:69-101` — the
  same pattern repeats 30+ times across the codebase). Replace
  with `await shepard.timeseries(id).download({format: "xlsx"})`,
  giving pagination iterators (W1, W4, W6 listing pages can stop
  hand-rolling page math) and typed errors (W7 surfaces "which file
  failed and why" by inspecting the error object).
- **R2 selectivity → "Choose what to export" panel (W3, W8).**
  Once R2 ships an `ExportSelection` body (selected
  collections / data objects / reference types / time ranges), the
  UI can mount a tree / checkbox panel inside the collection page
  ("Export" expansion item next to "Lab Journal") that builds the
  body and POSTs to the export endpoint. Pair with P13 SSE for job
  progress.
- **L4 / aidocs/14 search-as-you-type → header search bar replaces
  W11.** As above (§4 #1).

## 6. R5 — UI tests recommendation

**Pick Playwright** over Cypress. Two-line defence: the codebase is
Nuxt 3 + Vuetify, both of which Playwright handles out of the box
(no Cypress-specific Vuetify quirks); Playwright's
`@playwright/test` runs the same way under CI and on local dev,
parallelises across workers natively, and supports network
mocking against the generated `@dlr-shepard/backend-client` without
needing the `cy.intercept` ergonomics work. Cypress gives a slightly
nicer time-travel debugger, which does not outweigh Playwright's
cross-browser story (the maintainer has not stated a single-browser
constraint, see §8).

The first three smoke tests should land before any redesign work
(in priority order):

1. **W1 + W4 + W3 — happy path "browse and read".** Sign in, click
   "Collections", click the first row, expand a tree node, click a
   data object, assert breadcrumbs and that "Description" is
   visible. This covers `HeaderBar.vue`, `CollectionListPage.vue`,
   `CollectionSidebar.vue`, `pages/collections/[collectionId]/index.vue`,
   `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue`
   in one assertion chain.
2. **W2 — "create collection".** Open dialog, Step 1 fields, Next,
   Step 2 attributes, Create, land on detail page. Asserts the
   three-call sequence in `CreateCollectionDialog.vue:25-73` does
   not regress.
3. **W7 — "upload a file via drag-and-drop".** Drop a fixture file
   on the data-object page, fill Reference Name, click Upload,
   assert the new reference is in the table. Covers
   `DataObjectFileUpload.vue` + `DataObjectFileUploadDialog.vue` +
   the `FileContainerAccessor.uploadFile` happy path.

These three together exercise auth, header, list, detail, sidebar,
stepper dialog, file upload, and the most-used composables.

## 7. R8 — DLR Corporate-Design theming recommendation

The CD reference HTM files (`Richtlinien zur visuellen Gestaltung
(CD-Handbuch).htm`, `– Kapitel 2 Motion-CI.htm`, `– Kapitel 3
Digitale Medien.htm`, `Kapitel 4 Printmedien.htm`, etc.) lived
under `aidocs/input/` only as informative scaffolding for the
design phase and were **removed in commit `a5b2f85`** — they
contained empty / placeholder content and could not serve as the
canonical source for a CD implementation. Before R8 starts the
maintainer must obtain the canonical CD assets from DLR-internal
sources (DLR Corporate Communications / Marken-Portal): logo SVGs,
the official CD colour palette (HEX + RGB + the dark-mode
mapping), the type families (DLR's house font and substitution
rules), and the Motion-CI timing / easing rules. Without that the
work risks shipping a "DLR-ish" theme that the CD office rejects.

Existing design-doc context lives in `aidocs/19` §F-equivalent /
`aidocs/20 §E12` (Epic E12, UX & ecosystem) / `aidocs/21 §6` —
those are the chapter-level briefs around CD theming.

### 7.1 What the DLR CD covers

The chapter titles preserved in the now-removed `*.htm` reference
set scope what a CD-compliant shepard must respect:

- **Visual fundamentals** (`Richtlinien zur visuellen Gestaltung`)
  — logo, clear-space, palette, typography, photographic style.
- **Geschäftspapierausstattung** — letterhead / business-paper
  templates. Largely irrelevant for a web app, but exported PDFs
  (post-R2 PDF mode if it lands) should respect the same masthead
  rules.
- **Motion-CI** — motion-design rules: timing, easing, when to
  animate vs not. Maps to Vuetify's `transition` props on dialogs /
  sheets / fade-in patterns; a noisy app violates Motion-CI.
- **Digitale Medien** — explicit web guidance: link colours, focus
  rings, icon style, dark-mode mapping. The most directly relevant
  chapter for shepard's frontend.
- **Printmedien** — print-medium guidance; for shepard, this maps
  to generated PDF / RO-Crate cover-page styling once those exist.
- **Standorte** — site / location pages; for shepard, this is the
  app footer and any "About" page that lists DLR institute
  affiliation.

### 7.2 Compliance audit checklist

When the canonical CD assets arrive, audit the existing app
against each item below. Every row has a target file or component
to verify against; every result is binary (`pass` / `fail`). The
checklist is the deliverable of the audit phase that **must
precede** any token-mapping work in §7.3 — without it the team
will discover compliance gaps mid-implementation.

| Area | What to verify | Where to look |
|---|---|---|
| Colour palette | Every named token in the Vuetify theme matches a CD palette entry; no raw `#abc123` literals in components | `nuxt.config.ts` theme block; `git grep '#[0-9a-fA-F]\{3,8\}' frontend/components frontend/pages` |
| Type stack | DLR house font loaded via `@nuxt/fonts` with documented fallbacks; no hard-coded `font-family` | `frontend/app.vue`, `nuxt.config.ts`, `git grep 'font-family' frontend/` |
| Logo + clear-space | DLR logo SVG used at the canonical aspect ratio; clear-space rule respected in the header | `HeaderBar.vue` |
| Focus / link styling (Digitale Medien) | Link colour matches the CD palette; focus rings 2 px solid in the CD accent; dark-mode mapping in place | `assets/`, component-level `:focus-visible` rules |
| Motion-CI | All transitions ≤ 200 ms ease-out (or whatever CD specifies); no custom `cubic-bezier` outside the CD set | Vuetify `transition` props; CSS `transition` declarations |
| Dark-mode mapping | The `dark` Vuetify theme uses CD-specified dark-mode equivalents (not naive inversions) | `nuxt.config.ts` |
| Logo in exports | RO-Crate exports + any future generated PDFs include the DLR logo per Geschäftspapierausstattung rules | `ExportBuilder` + the future PDF emitter |
| Footer / Standorte | App footer carries the DLR institute name + Impressum / Datenschutz links per Standorte | `layouts/default.vue` |
| Accessibility (CD-adjacent) | WCAG AA contrast across every token combination; CD palette honestly checked, not hand-waved | `axe-core` pass via Playwright |
| Favicon + OG image | DLR-branded favicon variants (16/32/180/512); OG / Twitter images use the CD masthead | `public/`, head meta tags |

### 7.3 Implementation in Vuetify (once §7.2 passes)

Theming is a Vuetify-native exercise. The header already toggles a
`light` ↔ `dark` theme (`HeaderBar.vue:53-56`); the existing
tokens are colour names like `canvas`, `treeview`, `textbody1`,
`divider1`, `divider2`, `primary`, `low-emphasis`,
`medium-emphasis` (used everywhere — e.g. `CollectionSidebar.vue:62`,
`EditPermissionsDialog.vue:194`). The right move is to add a `dlr`
Vuetify theme (alongside `light` and `dark`) populating *those
same token names* with the CD palette, then map the DLR fonts via
`@nuxt/fonts`. That keeps the diff small and contained to
`nuxt.config.ts` + a new theme file plus a font-import line —
component code does not change because tokens stay token-named.

### 7.4 Compliance mechanism (verify and enforce over time)

CD compliance is a moving target — easy to land once, hard to
keep. Three mechanisms, ordered by ROI:

1. **Token guard (lint, S).** A CI lint that fails the build on
   hard-coded colour literals, hard-coded `font-family`, and
   hard-coded `transition` durations under `frontend/components`
   and `frontend/pages`. Mirrors the existing P17b pattern
   (`scripts/check-schema-name.sh` plus a baseline allowlist) —
   same shape applied to frontend tokens.
2. **Visual regression on the §7.2 surfaces (M).** A Playwright
   suite (per §6) capturing canonical screenshots for the
   checklist's representative components (`HeaderBar`,
   `CollectionSidebar`, `EditPermissionsDialog`, the Advanced
   Search page, dark-mode variants) and failing on pixel-difference
   threshold. Snapshots regenerate behind a manual flag, never
   silently.
3. **Brand review (process, no code).** A pre-release checklist
   item that files an issue against DLR Corporate Communications
   for every token-set or asset change, retaining the approval as a
   signed-off comment. Cheap insurance against the "DLR-ish" theme
   trap.

### 7.5 Drift surfaces

Where shepard most easily drifts from CD over time. Audit each
when triaging an R8 follow-on:

- **Third-party Vuetify components** that ship their own colours
  (date pickers, file inputs). Verify at component-adoption time;
  the token guard from §7.4 surfaces the most obvious offenders.
- **Markdown / HTML rendering** in lab-journal entries
  (`LabJournalEntry`) — users supply content; the CD applies to
  the **rendering wrapper**'s chrome, not the user's content.
- **Error pages** (`error.vue`) — frequently forgotten in token
  migrations because the happy-path components dominate review.
- **Loading / empty / skeleton states** that hard-code "Loading…"
  placeholders in default Vuetify gray.
- **Generated PDFs / RO-Crate cover pages** once those exist
  (Printmedien chapter) — out of frontend's hands but in scope for
  the CD audit.
- **Email templates** if/when shepard sends notifications (none
  today; would follow Geschäftspapierausstattung rules).
- **Favicon and OG images** drift independently of the app theme;
  audit them whenever the Marken-Portal assets refresh.

### 7.6 Out of scope for R8

The following are *adjacent* to CD but should not be folded in:

- **Internationalisation.** German / English copy alignment is a
  separate workstream; the CD covers visual identity, not language.
- **Accessibility beyond WCAG AA.** AAA conformance, screen-reader
  story, keyboard-only flows — all worth doing, but a separate
  audit (cite under R5 if the team funds it).
- **Marketing site / landing page.** Out of repo. The DLR Corporate
  Communications team owns external-facing branding.

## 8. Things to deliberately *not* do

1. **Do not rewrite the frontend in React / Svelte / "modern X".**
   The Vue 3 + Vuetify stack is current, well-supported, and the
   maintainer has invested in `composables/` and a generated TS
   client wired to it. A rewrite delays everything in §4.
2. **Do not hand-roll a parallel UI library.** Vuetify 3.12 is the
   adopted system (`frontend/package.json:46`); `@mdi/font` is the
   icon set. Resist the temptation to bring in shadcn-vue,
   PrimeVue, or Material Web on the side. New components should
   use existing wrappers (`common/dialog/FormDialog.vue`,
   `common/data-table/DataTable.vue`).
3. **Do not move state to the URL beyond what's already in
   `useCollectionListQueryParams.ts` / `useContainerListQueryParams.ts`
   until P16 lands.** Today the page-level "search params" pattern
   works because each page hand-builds query strings (e.g.
   `pages/search/index.vue:148-161`); the convenience-wrapper
   landing will give pagination iterators that change the right
   shape of "what should be in the URL" — premature URL-stating
   creates a migration burden.
4. **Do not ship a redesign before R5 (Playwright smoke tests) is
   in CI.** Pure UX redesign without regression coverage will
   regress the auth + multi-step-stepper flows; the three smoke
   tests in §6 are the floor.
5. **Do not unify the "create" dialogs into a single generic
   form.** Tempting (collection / data object / container all use
   the stepper) but the field semantics and round-trip patterns
   differ enough that a generic form would be either
   over-parameterised or restrictive. Convergence belongs at the
   shared component level (`StepperDialog`, `FormDialog`, the
   `*Input` family in `components/context/input-components/`),
   which is already where it sits.

## 9. Open questions for maintainer

1. **Browser target.** Is this a Chrome / Edge intra.dlr.de
   target, or do users hit it from Firefox / Safari? Affects
   Playwright cross-browser-test scope.
2. **Mobile / narrow screen.** Does anyone open shepard on a
   tablet or phone? The current layouts hard-code
   `max-width: 1000px` / `1200px` repeatedly
   (`CollectionListPage.vue:14`, `pages/.../dataObjectId/index.vue:41`,
   `ShowTimeseriesReferenceDialog.vue:245`). If yes, a
   responsive-design pass is implied.
3. **Persona priority.** Researcher, lab admin, project manager,
   or data engineer? §4 prioritises researcher (W11 first) — does
   the maintainer agree, given the
   `input_raw.md:91, 96, 98` (templates) signal?
4. **Real user-research signal beyond the maintainer's own
   intuition.** Are there session recordings, support tickets, or
   a usability study (DLR Wiki, `input_raw.md:3861`) we can mine
   to validate the §4 priorities?
5. **OIDC / IDP target for sign-in.** `pages/auth/signIn.vue:1-3`
   notes the workaround for issue #399. Is the production IDP
   stable? The `nuxt-auth` 0.10 stack
   (`frontend/package.json:13`) constrains what session shape
   the SSE notifications tray (§4 #3) can use.
6. **Delete the `ComingSoon.vue` component or wire it.** Five+
   places import it; either a feature backlog item is implied or
   the component is dead code.
