---
stage: audited-by-personas
last-stage-change: 2026-05-24
audience: frontend, UX, QA
---

# UI Scrutinizer — live-shepard systematic walk, 2026-05-24

**Scrutinizer**: Playwright-driven traversal agent
**Target**: live `https://shepard.nuclide.systems`
**Auth**: alice / admin (Keycloak `shepard-demo` realm)
**Datasets**: LUMEN (coll 42, 17 DOs) + MFFD-Dropbox (coll 661923, 8514 DOs)
**Viewports**: 1920×1080 + 3840×2160 (4K) where layout-sensitive
**Evidence**: `ui-scrutinizer-2026-05-24-evidence/` (80 files, 5.6 MB; 26 screenshots, 24 JSON sidecars, plus DOM probes)
**Spec source**: `e2e/tests/ui-scrutinizer-2026-05-24*.spec.ts` (3 parts)

Sibling to the standing source-file audit at `aidocs/agent-findings/ux-auditor.md` (2026-05-21).
Where that doc reasons from `frontend/pages/**`, this one reasons from what an actual user sees.

---

## What I found

The shepard UI works in the **happy spot** — a researcher who logs in, clicks
into the LUMEN collection on a 1920×1080 monitor, opens TR-001, and reads its
references will have an OK time. The data flows, the panels render, the chips
are pretty. The basic loop of "find a collection → open a DO → see its
references" is honoured.

Everything outside that happy spot is uneven. The walk surfaced **one
operator-blocking bug** (BUG #139 — DataObject detail blanks at 4K),
**three full-class layout regressions** (every index page in `/me`,
`/admin`, `/about` lands on an empty content panel), **one
core-flow break** (header search returns no results for anything other
than collections, so the most natural "I need to find TR-004" interaction
silently fails), **at least four cosmetic-but-visible bugs on the home
page** (truncated card title mid-word, raw HTML/Markdown leaking through
description preview, missing landing imagery, missing avatar), and a
**pervasive console-error baseline** (every page logs an SSR/CSR
hydration mismatch + a `watches/me` 404, indicating both a missing
feature-flagged endpoint and a hydration-stability issue worth fixing
before more features layer on top).

The 4K bug deserves its own callout: it's not a CSS overflow, it's not a
hidden element, it's not a missing API call. The detail panel **renders
correctly with 86 KB of HTML** at full page height (4080 px), but its
flex/grid placement pushes it below the sidebar height (~530 px)
instead of beside the sidebar. So a user on a 4K display sees the
sidebar and ~1500 px of white space, scrolls down, and *only then*
finds the panel they expect to see immediately to the right. This is
**a layout-direction bug** at the breakpoint, almost certainly a
Vuetify `v-row`/`v-col` or display-class flipped against the wrong
`display-*-and-down` modifier. Cheap to fix; expensive in user trust
until it is.

The pages that work, work because they're built around the upstream
shepard data model and lean on patterns the v1 frontend already
solved. The pages that don't work mostly reflect the index-page
anti-pattern: the developer who built `/admin/*.vue` knew which
sub-pages exist but never picked one to be the default landing,
leaving the index slot empty. Three pages with the same pattern means
this is structural, not a one-off.

Organising against the canonical five-phase user journey
(`aidocs/frontend/01-user-research-findings-2024.md §1`):

- **Phase 1 Discovery** — the public landing page assets 404 (the
  airplane / satellite / solar photos), so an unauthenticated visitor
  sees broken images before they ever click anything. Worst possible
  first impression. (Captured under `Hidden gaps`.)
- **Phase 3 Create data structure** — Create-Data-Object dialog
  works, has a good 2-step shape, has rich-text editor for
  description. This is one of the best surfaces touched in the walk.
- **Phase 4 Import data** — File container shows a single .rdk file
  with no preview, no description, no obvious download. Same dataset
  shows 30+ pending thumbnail 404s. A researcher uploading a folder
  full of CAD files would not understand whether the upload landed.
- **Phase 5 Working with the data** — Search returns nothing for any
  query other than collection names; advanced search demands the user
  hand-write JSON DSL in a single-line input. Two of the most
  important "I want to find something" surfaces are effectively
  unusable.

The biggest single takeaway: **almost every individual page renders
something useful**, but the seams between them, the empty index
shells, and the responsive-layout bugs at non-standard viewports
combine to make the product feel half-finished when it isn't. The
fixes are mostly individually small.

---

## Opportunities

1. **Fix BUG #139 in one PR with a one-shot Vuetify breakpoint test.**
   The root cause is layout direction, not a missing component. A
   single `display-md-and-up` / `flex-row` swap on the collection
   layout should clear all DO-detail blankness at 4K. Land a
   `@media (min-width: 1920px)` Playwright snapshot test in the
   same PR so regressions surface immediately.
2. **Standard "section index landing" component.** `/me`, `/admin`,
   and `/about` all render an empty content panel when the user is at
   the section root. A small `<SectionIndexLanding :children="…" />`
   composable, used by all three, that auto-picks the first sub-route
   (or shows a quick-actions grid) would close three findings at once
   and become the convention for any future hub page.
3. **Make the header search actually search.** The largest single
   UX-friction win in the whole audit. Backend already has a `/search`
   endpoint; the header search box just needs to call it with a
   debounce and render hits as a dropdown. Even without backend
   changes, scoping the search to "collections+DOs in collections I
   can see" would close the most common researcher need ("find
   TR-004").
4. **Landing-page imagery refresh.** Three image assets 404 in the
   public landing path. Either ship the images or remove their
   references. Probably 30 minutes of work; visible to every visitor.
5. **Hydration audit.** *Every* visited page logs `Hydration
   completed but contains mismatches`. This is a Nuxt SSR/CSR drift
   that bleeds into chunked rehydration, layout flash, and (in some
   cases) lost focus. Worth a single ticket to identify the offending
   component before more features pile on.
6. **Default file thumbnails.** Every file row in the MFFD file
   container logs a thumbnail 404. The frontend asks for thumbnails;
   the backend never generates them. Either: (a) ship a
   thumbnail-generation job for image MIME types, (b) ship a typed
   default placeholder per MIME type (PDF, CAD, etc.), or (c) hide
   the thumbnail request when the file is not an image (least work,
   stops the noise).
7. **Index-page link visibility for `/v2/admin/*`.** When alice
   (non-admin) visits `/admin`, she's silently bounced to `/me`
   with an empty panel. That should be a 403 toast or a friendly
   "this section is for instance admins" landing — same as the
   admin user sees, but with disabled actions.

---

## Ideas

1. **Adaptive sidebar tree compaction.** MFFD sidebar at 4K stays
   200 px wide showing 10-character DO names ("TapeLaying-skeleton" →
   "TapeLaying-skel..."). At a 4K viewport with 3000 px of unused
   horizontal space, the sidebar could grow to its content width or
   gain a "double-pane" toggle (Finder-style miller column) to show
   parent + child simultaneously.
2. **Collection-card density toggle on home.** Home shows max 6 cards
   above the fold. A user with 30+ collections never gets a useful
   overview here. A density toggle (cards / table / dense) on the
   recent-collections panel is a small thing.
3. **Inline JSON DSL helper in /search.** The single-line JSON input
   on `/search` is unusable. Two ideas: (a) a "Build a query"
   visual-form mode that emits the DSL JSON below; (b) a real
   multi-line code editor with JSON syntax highlighting + schema
   autocomplete. Both are small components atop existing Vuetify.
4. **Render description markdown.** Home cards show `<p>Collection
   to exchange data</p>` and `**NOT REAL DLR/LUMEN data.**` literally.
   Server stores the raw markdown/HTML, but the home card preview
   strips one and shows the other untouched. A single
   `markdown-it`/`v-html` pass on the description in
   `RecentCollections.vue` fixes it.
5. **Truncate-with-ellipsis fix on home cards.** "LUMEN-Inspired
   Hotfire Test Campaig" cuts mid-word, no ellipsis. A CSS
   `text-overflow: ellipsis` + `white-space: nowrap` on the title
   class or `v-card-title` would fix it.
6. **Container-list link clarity.** The /containers list shows ID
   626776 next to ID 65 with no scaling cue. A small chip or
   prefix-by-type group header would clarify at a glance.
7. **"Open in new tab" affordance on DO sidebar.** A researcher
   wants to open TR-003 vs TR-004 side-by-side. The sidebar is a
   list of `<a>` links so middle-click works, but a hover
   "open-in-new-tab" icon would make this discoverable.

---

## Real-world impact

| Persona (from CLAUDE.md roles + 2024 UX research) | Hardest blocker for them in this walk |
|---|---|
| **Reluctant Senior Researcher** (own folder structure works) | Header search returns nothing for "TR-004" — confirms the suspicion that "this thing can't even find what I'm looking for." They close the tab. |
| **Interdisciplinary Researcher** (cross-test comparison) | BUG #139 at 4K. They use a wide monitor at the bench. The DO detail panel blanks; they have no idea why. They go back to PowerPoint and Excel. |
| **Compliance Auditor** (DIN EN 9100 trail) | Empty `/admin` and `/me` index pages — looks like a half-built system. Trust is the audit currency. |
| **Industrial Manufacturing Engineer** (shop-floor terminal) | File thumbnails 404; folder of CAD uploads becomes a wall of nameless rows. Plus the description showing raw markdown asterisks looks broken. |
| **Digital Native Researcher** (API-first, Jupyter) | Header search dead = falls back to API; that's their happy path anyway. Bigger impact: hydration warnings in every console = the front-end feels brittle and they'll look at the API instead. |
| **Research Data Manager** (FAIR steward) | Landing imagery 404 on public phase-1 Discovery surface; raw HTML in collection-preview cards. First impression is "untrusted alpha software." Hard to publish a DOI against a system whose home page is broken. |

Phase-by-phase breakdown:

- **Discovery (phase 1):** Broken landing imagery + hydration noise =
  loses the unauthenticated visitor before they've authenticated.
- **Create structure (phase 3):** Create-DO modal works well; the
  rich-text editor + 2-step Properties→Attributes split is the
  cleanest flow in the audit.
- **Import data (phase 4):** File container view shows uploaded
  files but no preview / no thumbnail / no MIME-type indicator;
  description field empty. An operator uploading wonders if their
  files survived.
- **Working with data (phase 5):** Header search dead, advanced
  search demands JSON DSL, no cross-DO comparison view, no clear
  "where did this data come from" overlay. The 2024 user research
  documented "I don't understand the search function" — this
  walk confirms the gap is still there.

---

## Gaps & blockers

- **`page.request` doesn't carry the OIDC session cookie**, so the
  spec couldn't enumerate container IDs through the API directly
  and had to scrape the rendered `/containers` table. Worked around;
  doesn't affect the findings, but a Playwright auth-state helper
  would let future scrutinizer specs make read-only API probes.
- **Couldn't open a TS container** — the `/containers` list showed
  no timeseries container in its top page (only files +
  structureddata at top); deeper pagination needed. Container view
  for TS is uncovered in this walk (TODO: see "What's left to walk"
  below).
- **Couldn't reach an MFFD deep-leaf DO** — the first MFFD DO ID
  scrapeable from the sidebar is 661928 (TapeLaying-skeleton,
  root). Track-level DOs (Track 239 etc.) are visible in the
  sidebar but the scrape didn't extract numeric IDs from their
  collapsed parent nodes. BUG #139 reproduces on the root MFFD DO
  at 4K (see `bug139-tr001-4k-fullpage.png`), so the deep-leaf
  exploration was not blocking — but is genuinely missing.
- **Container thumbnails 404** — captured but not reproduced as
  uploads, since the prompt says no mutations.

---

## What surprised me

1. **BUG #139 is not a render failure, it's a layout-placement
   failure.** The full-page 4K screenshot at
   `bug139-tr001-4k-fullpage.png` shows the detail panel **does
   render** with all 86 KB of HTML — it's just pushed below the
   sidebar's height instead of beside it. The viewport-only 4K
   shot makes it look gone; the full-page shot proves it isn't.
   This is a *much* cheaper fix than "missing detail component"
   would have been.
2. **The first 4 visited "index" pages — `/me`, `/admin`,
   `/about`, `/configuration` — all blank out the content panel
   when the user lands at the section root.** Three different
   sub-areas, same anti-pattern. This is structural; one
   refactor closes them all.
3. **Every page logs `Hydration completed but contains
   mismatches`.** Every single page. Including the home page.
   This is a pile of small drifts between SSR and CSR, often
   harmless individually, but it's been allowed to grow without
   any visible attention. New features will land on top of it.
4. **The container ID scale spread is huge** — 63 next to 626776.
   A user can't tell from looking that these are the same kind
   of thing. The migration to appId can't come fast enough; in
   the meantime, a type prefix in the list view would help.
5. **The header search returns nothing for anything except
   collection names** — and even when it does return collections,
   the dropdown doesn't appear, just a cleared field. This is
   filed in the standing ux-auditor.md but the live behaviour is
   *worse* than what the source-file audit captured: no spinner,
   no dropdown, no empty-state message.
6. **The collection-list table at `/collections` exposes raw
   Neo4j IDs (661923) as the primary ID column.** Same column
   reads "42" for LUMEN — those are wildly different scales.
   The contrast highlights both how thin the abstraction is
   and how confusing the heterogeneous-ID world will be to
   any user.
7. **Search-engine UX**: typing "TR-004" in the header gives no
   feedback — no dropdown opens, no character-count, no
   suggestion, no "no matches" message. The user is left
   wondering whether the search ran.

---

## Findings table

| ID | Severity | Phase / Route | Component (best guess) | Viewport | What I saw | Evidence | User impact | Fix shape |
|---|---|---|---|---|---|---|---|---|
| UI-2026-05-24-001 | CRITICAL | P3 / `/collections/.../dataobjects/...` | `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue` + collection layout | 4K only | DataObject detail panel renders below the sidebar (offset y≈580px) instead of beside it; viewport-only screenshot at 4K looks blank | `p3-01-lumen-do-detail-3840-viewport.png`, `p3-02-mffd-do-detail-3840-viewport.png`, `bug139-tr001-4k-fullpage.png`, `bug139-tr001-4k-dom.json` (proves DOM contains 86 KB of HTML and `<main>` has h=4080 px) | Researcher persona at 4K sees broken page; assumes data is missing. Phase 5. | Audit the `v-row` / `v-col` ordering in the collection layout, or the `display-md-and-down` modifier that should be `display-lg-and-up`. Land a Playwright 4K snapshot test in the same PR. |
| UI-2026-05-24-002 | CRITICAL | P5 / header | `components/layout/HeaderBar.vue` (per ux-auditor.md) | both | Typing "TR-004" produces no dropdown, no spinner, no empty-state message — the field just sits there | `p5-01-header-search-tr004-1920.png`, `p5-01-header-search.json` | Most common researcher search — finding a known DO by name — silently fails. Phase 5. | Wire the box to `/v2/search` or `/v2/dataobjects?name=…&collectionId=...` with a debounce; render hits as a Vuetify menu dropdown with kind chips. |
| UI-2026-05-24-003 | MAJOR | P1, P6 / `/me`, `/admin`, `/about`, `/configuration` | `pages/me/index.vue`, `pages/admin/index.vue`, `pages/about/index.vue`, `pages/configuration/index.vue` | both | Section index pages render empty content panel + sub-nav only; user has to click a sub-section to see content | `p1-06-about-1920.png`, `p1-07-me-1920.png`, `p1-08-admin-1920.png`, `p6-03-admin-as-admin-1920.png`, `p6-04-configuration-as-admin-1920.png` | Every persona; first impression of "is this section broken?" Phase 1 + 5. | Add a default sub-route redirect (e.g. `/me` → `/me/profile`), or render a quick-actions grid as the landing. |
| UI-2026-05-24-004 | MAJOR | P1 / `/admin` (as alice) | router middleware | both | alice (non-admin) goes to `/admin` and is silently redirected to `/me` with an empty panel; no 403, no toast, no "not authorized" message | `p1-08-admin-1920.png` (URL bar shows `/me`) | Operator confusion; appears the system is broken | Show a toast on unauth-redirect, OR render an `Unauthorized` shell page that links to `/me` rather than a silent bounce. |
| UI-2026-05-24-005 | MAJOR | P2 / `/collections/[id]` | every collection landing | both | Every collection-landing page fires `GET /v2/collections/<appId>/watches/me` which 404s; pollutes console + may break a "watch" feature flag | `p2-02-mffd-landing-1920.json` (404 captured); confirmed in `p2-01-lumen-landing-1920.json` too | Hidden — currently silent, but cascades into a console-error budget that drowns real errors | Ship the `watches/me` endpoint OR feature-flag the front-end call. The endpoint is presumed planned (it appears in the request despite returning 404). |
| UI-2026-05-24-006 | MAJOR | P1 / `/` home page | `RecentCollections.vue` (best guess) | both | LUMEN card title truncates mid-word "Hotfire Test Campaig" with no ellipsis; AI Exchange description shows raw `<p>...</p>` HTML; LUMEN description shows raw `**markdown**` asterisks; total 4 visible cosmetic bugs on home page in 5 cards | `p1-01-home-1920.png` | Every visitor; cumulative impression of unfinished software | (a) `text-overflow: ellipsis` on title CSS; (b) render description via `markdown-it` or strip HTML server-side; (c) consistent treatment for both formats. |
| UI-2026-05-24-007 | MAJOR | P1 / public landing (unauthenticated) | landing component | n/a | Public landing references `/assets/img/photo-aircraft.jpg`, `photo-satellite.jpg`, `photo-solar.jpg` — all 404 | Network errors in `p1-01-home-1920.json` and earlier captures | Phase 1 Discovery — unauthenticated visitor sees broken images on first impression | Ship the images OR remove the `<img>` references. ~30 min. |
| UI-2026-05-24-008 | MAJOR | P5 / `/search` | `pages/search/index.vue` | both | Advanced search query input is a single-line text field demanding raw JSON DSL `{ "OR": [ ... ` — text cut off after ~5 chars; no JSON formatting, no syntax help, no schema autocomplete, no example | `p1-04-search-1920.png` | Phase 5 — search is the highest-friction documented user need; this UI doubles down on the friction. | Add a JSON syntax-highlighted multi-line editor (CodeMirror) + an example query placeholder + a "Build query" wizard mode for the common AND/OR pattern. |
| UI-2026-05-24-009 | MAJOR | P4 / `/containers/files/...` | `components/container/files/...` | 1920 | Every file row logs `thumbnail?size=64` 404; no MIME-type icon shown to compensate; the .rdk file gets a generic page icon with no preview | `p4-01-files-626776-1920.png`; network 404s aggregated in `bug139` and other captures | Phase 4 import; operator can't tell which uploaded file is which without clicking | Either ship a thumbnail-generation backend job, OR ship typed default placeholders per MIME family, OR (cheapest) condition the request on `isImageMime(...)` and remove the noise. |
| UI-2026-05-24-010 | MAJOR | P1, P2 / all pages | Nuxt SSR | both | Every page logs `Hydration completed but contains mismatches`; no visible flash but the warning is a smell | Every `consoleErrors` array in `*.json` sidecars | Hidden — frontend brittleness, will surface as a real bug as features pile on | Audit which component generates the mismatch (date formatting? randomised IDs? Vuetify ripple?). A single offending component is usually the culprit. |
| UI-2026-05-24-011 | MAJOR | P1 / `/collections` | `pages/collections/index.vue` | 1920 | Collection list has only ID / Name / Created-by / Created-at columns. No object count, no last-updated, no description preview. Doesn't help a user pick. The ID column also exposes raw numeric Neo4j IDs (42 vs 661923) | `p1-02-collections-list-1920.png` | Phase 1 → 5 transition; user can't tell a 17-DO showcase from an 8500-DO dropbox at a glance | Add columns: `# DOs`, `Last updated`, short description; consider hiding the numeric ID column behind an "advanced" view (it'll go away with appId migration anyway). |
| UI-2026-05-24-012 | MAJOR | P1 / `/me` Profile | `pages/me/index.vue` | both | Profile sub-nav lists "Profile / API Keys / MCP / Subscriptions / Git Credentials" but no avatar upload affordance visible at the index; header avatar is a circle with "A" (no image) — confirms there's no upload path the user can find | `p1-07-me-1920.png` + header-bar avatar in `p6-01-create-dialog-button_has_text__Add__.png` | Phase 5; tied to the standing "contributor avatars" memory note | The Profile sub-page (when reached) likely has an upload; raise its discoverability OR show an avatar-upload affordance on hover over the header circle. |
| UI-2026-05-24-013 | MINOR | P1 / `/help` | `pages/help.vue` | 1920 | Help page is one long single-column scroll with a tall left nav; no search-in-docs box; no per-section anchor links | `p1-05-help-1920.png` | Every persona that hits a wall; "how do I do X" requires reading sequentially | Add a help-search box at the top + anchor links per H2; consider splitting into per-topic routes once the page exceeds N sections. |
| UI-2026-05-24-014 | MINOR | P2 / `/collections/[id]` | sidebar tree | 4K | At 4K the sidebar stays 200 px and truncates DO names to ~12 chars while 3000 px to the right is empty | `p2-02-mffd-landing-3840.png` | Researcher persona at 4K; readability hurt | Make sidebar width responsive to viewport, or expose a sidebar-width drag-resize handle. |
| UI-2026-05-24-015 | MINOR | P4 / `/containers/files/...` | file-container header | 1920 | The header label "Created at" wraps to a second line in the file row table (because the column header is two words), creating an off-by-one row alignment | `p4-01-files-626776-1920.png` | Cosmetic | `white-space: nowrap` on the column-header span. |
| UI-2026-05-24-016 | MINOR | P4 / `/containers/structureddata/...` | SD container | 1920 | "Referenced by" section opens automatically with a long list of TR-001..TR-014 each with semantic-annotation chips; useful, but the chip rendering is duplicated 5+ times per row | `p4-03-structureddata-65-1920.png` | Page is information-dense without grouping; harder to scan | Either group annotations per-row or limit visible annotations to 3 + "..N more" expandable. |
| UI-2026-05-24-017 | MINOR | P5 / LUMEN DO detail | description editing | 1920 | "Edit" button next to Description has no visible affordance for *which* part of the panel it edits; the editor pops below the line, not inline | `p5-04-lumen-do-full-1920.png` | Small confusion; clicking once doesn't show what's now editable | Inline edit visual cue (border, "Editing description" header) or treat Description as a `<EditableField>` rather than a section. |
| UI-2026-05-24-018 | HYPOTHESIS | P3 / DO detail | possibly `useFetchAllDataObjects` (per ux-auditor) | 1920 | Visiting MFFD DO detail page didn't immediately fire an obvious N+1, but the network capture is too coarse to confirm; ux-auditor.md already flagged this in `CollectionLineageGraph.vue` and `DataObjectProvGraph.vue` | inferred; not directly captured | Will be a real issue at MFFD scale once the prov/lineage graphs render | Confirm via Network tab on the deep MFFD DOs once BUG #139 is fixed (lineage graph won't render until then). |
| UI-2026-05-24-019 | HYPOTHESIS | P6 / Create-DO | dialog | 1920 | Create-Data-Object dialog opens cleanly and has a 2-step wizard shape. Couldn't test submission per "no mutations" rule | `p6-01-create-dialog-button_has_text__Add__.png` | None — this surface looked good | Cover with a non-destructive Playwright test in the next walk (fill + Cancel). |

---

## Top 5 highest-impact fixes

Ranked by **(user value × phase coverage) / effort**.

| Rank | Fix | Why first | Effort |
|---|---|---|---|
| **1** | **Fix BUG #139** (UI-001): swap the layout-direction modifier on the collection layout for `>=lg` viewports; add a Playwright 4K snapshot test in the same PR | Blocks the operator-facing demo on any 4K display, including ZLP-Augsburg shop-floor terminals. The DOM probe proves it's a placement bug not a render bug — 1-line CSS fix likely. | S — ½ day incl. test |
| **2** | **Wire header search to a real backend call** (UI-002): debounce + dropdown + show hits across collections, DOs, containers | The 2024 user research explicitly said "I don't understand the search function." Header search is the most-used surface that's currently dead. | M — 2-3 days |
| **3** | **Standard `<SectionIndexLanding>` component** (UI-003) used by `/me`, `/admin`, `/about` (and `/configuration` if it stays a section) | Closes 4 findings at once; structural fix; small dev work; instantly raises "polish" perception. | S — 1 day |
| **4** | **Home-page card cleanup pass** (UI-006, UI-007): render description markdown, truncate title with ellipsis, ship landing imagery, MIME-type icon for files | First impression of the product. Bundle as one "polish home" PR. | S — 1 day |
| **5** | **Silence the `watches/me` 404 + thumbnail 404 + Hydration warning** (UI-005, UI-009, UI-010) | Together these generate ~80% of the console-error noise. Once silenced, real errors become legible — and the "every page logs errors" smell disappears for any future dev who opens devtools. | M — 2 days (depends on whether watches/me ships or gets feature-flagged) |

---

## What's left to walk

The 40-turn budget for the walk was hit before these surfaces were covered. A follow-up scrutinizer should pick up here:

- **MFFD deep-leaf DataObject detail** — the spec found root MFFD DOs only (`TapeLaying-skeleton`, ID 661928). The interesting BUG #139 case is at depth 3-4 (Track / Frame / AF / Execution leaves). Expand all sidebar levels first, then click into a Track.
- **Timeseries container view** — TS containers weren't in the first 4 rows of `/containers` (the page only showed Files + SD); needs deeper pagination scrape or a direct ID from the LUMEN sidebar's `lumen-inspired-runlogs` Container reference.
- **Provenance + lineage graphs at MFFD scale** — once BUG #139 is fixed, render `DataObjectProvGraph.vue` against an MFFD DO and confirm the standing ux-auditor finding (~200 nodes = ECharts force layout lag).
- **Annotation create / edit flow** — opened `Create Data Object` dialog as a proxy for form structure but not the `AddAnnotationDialog`. Needs a click on an existing DO's "Semantic Annotations ADD" button.
- **Basic ↔ Advanced mode toggle** — code-level audit already done (ux-auditor.md); behavioural verification on live needed.
- **`/v2/admin/*` sub-pages as admin** — admin landing was empty; each sub-page (Feature Toggles, Plugins, Instance Health, Storage Overview, Templates, Semantic Repositories, User Groups, Research Organization, Permission Audit Log, Unhide, Legacy v1) deserves at least one click.
- **`/me/api-keys`, `/me/mcp`, `/me/subscriptions`, `/me/git-credentials`** — sub-pages of profile; first-time visitor needs a "what is this?" or default-state.
- **Help-page completeness** — does the help reflect what the UI does today? Walk it page-by-page and cross-check shipped features.
- **Mobile viewport (≤768 px)** — explicitly out of scope for this walk; the upstream user research notes shop-floor / tablet usage, so worth a dedicated walk.
- **Mutation flows under controlled conditions** — Create-Collection, Add-Annotation, Edit-Description; needs a sandbox collection to avoid trashing LUMEN/MFFD data.

---

## Reproducibility

To re-run this walk:

```bash
cd /opt/shepard/e2e
npx playwright test tests/ui-scrutinizer-2026-05-24.spec.ts --project=chromium
npx playwright test tests/ui-scrutinizer-2026-05-24-part2.spec.ts --project=chromium
npx playwright test tests/ui-scrutinizer-2026-05-24-part3.spec.ts --project=chromium
```

Each spec is independently runnable, captures evidence to
`/opt/shepard/aidocs/agent-findings/ui-scrutinizer-2026-05-24-evidence/`,
and never asserts (so they don't fail when the live behaviour
drifts). Use the JSON sidecars (`*-1920.json`, `*-3840.json`) for
console-error / network-error diffs between runs.

External references:

- Vuetify 3 responsive breakpoints (for BUG #139 fix):
  https://vuetifyjs.com/en/features/display-and-platform/
- Nuxt 3 hydration mismatch debugging (for UI-010):
  https://nuxt.com/docs/getting-started/data-fetching#hydration
- Prior screenshot walk (MFFD v16, 2026-05-23):
  `aidocs/agent-findings/mffd-v16-ui-screenshots-2026-05-23/README.md`
- Standing source-file audit (2026-05-21):
  `aidocs/agent-findings/ux-auditor.md`
- Canonical user-journey frame:
  `aidocs/frontend/01-user-research-findings-2024.md §1`
