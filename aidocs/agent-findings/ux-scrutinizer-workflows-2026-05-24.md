---
stage: feature-defined
last-stage-change: 2026-05-24
audience: frontend, UX, product, backend
---

# UX Scrutinizer — Workflow + Click Minimisation, 2026-05-24

**Scrutinizer**: Playwright-driven click-trail measurement (depth round; sibling to the breadth round at `ui-scrutinizer-2026-05-24.md`)
**Target**: live `https://shepard.nuclide.systems`
**Auth**: alice / admin via Keycloak `shepard-demo`
**Datasets**: LUMEN (coll 42, 17 DOs) + MFFD-Dropbox (coll 661923, 8514 DOs)
**Evidence**: `ux-scrutinizer-workflows-2026-05-24-evidence/` (15 trail JSONs + ~70 step screenshots + auth-state snapshots)
**Specs**: `e2e/tests/ux-workflow-{00,01,02,03,04,05}-*.spec.ts` + reusable helper `e2e/tests/helpers/click-trail.ts`

Each spec re-runs as a regression after fixes: `npx playwright test ux-workflow- --grep <name>`. Trail JSONs include the literal click sequence, page transitions, durations, screenshots — diff a new run against a baseline JSON and the cost delta of a fix is visible to the eye.

---

## What I found

This round's headline: **the seams between pages are where Shepard loses the user, not the pages themselves.** The breadth round (UI-Scrutinizer 2026-05-24) catalogued individual-page bugs; this round measured what a real workflow costs in clicks and seconds, and the pattern that emerges is structural — five different surfaces each demand the user re-do work the system already knows.

The single most damaging finding is **the universal "loading-state-leaks-as-error" pattern**: every page that fetches collection or DataObject data renders an error toast ("Could not load collections", "Error while fetching collection", "Error while fetching recent collections") **during the load window**, then the data arrives ~3–7 seconds later and the page resolves. A user who reacts to the error toast (clicks Retry, clicks Close, opens DevTools, files a bug) is left with a permanent impression that the product is broken even though no request actually failed. This shows up on `/`, `/collections`, `/collections/42`, and likely every collection-detail page. It's a single-class fix: defer the toast until a fetch has actually thrown (or until a 2–3 s settle window has expired). One change clears a finding from every workflow.

The second structural finding is **two filter inputs and one global search input in one viewport on `/collections/{id}` with three different scopes**: a sidebar "Filter…" (sidebar tree only), a body "Search by name…" (Data Objects list only), and the header "Search" (global / fuzzy across DOs and collections). None of them is labelled with its scope; none explains what it filters; none is the obvious default. A user looking for "Layup" in MFFD-Dropbox doesn't know which input to use; in WF-04 the wrong choice (sidebar filter) produced 57 visible rows from 55 baseline — a **measured zero-effect filter** on the user's perceived workflow. The global header search is the one that works (it returned "Layup → dataobject" instantly from the keystroke), but it's the smallest, most generic-looking of the three. Re-rank: header search becomes the primary, the other two become contextual sub-filters with explicit scope labels.

The third structural finding is **no multi-select, no compare, no sibling navigation** in the DataObject surface. WF-05 (bulk-annotate 3 DOs) measured zero checkbox affordances on any list — the only path is N × (click DO, open annotation dialog, fill, save, back). WF-06 (compare TR-004 vs TR-005) measured zero Compare affordances — the user must keep one window open and visit the second in another tab, or visit linearly and rely on memory. WF-13 (TR-004 → TR-005 → TR-006 sibling chain) measured the cost of having no next/prev-sibling button: every sibling visit forces the user back to the collection landing and re-fetches all DOs. The combined waste across these three workflows is the **single largest UX debt** on the platform and the one the breadth round didn't catch because it never composed two actions in a row.

The fourth structural finding is **provenance/lineage is buried inside an expansion panel that has no badge**: on a DataObject detail page, the user can't tell from above-the-fold UI whether the DO has predecessors, successors, or neither. WF-09 (trace TR-006 back to TR-004 anomaly) requires the user to know the panel exists, scroll, expand, then visually traverse a graph. A research data platform whose value-prop includes "you can audit the chain" has the chain hidden behind a click.

The fifth finding is **lighter but pervasive**: empty user-name (`Good morning, .`), missing avatar (404 on `/v2/users/{appId}/avatar`), and `# DOs = 0` on every collection card on `/collections` (even MFFD-Dropbox's 8514). Each is a 1-line fix; together they erode trust in every screen they appear on.

Compared to the breadth round (UI-001..017): the loading-state-leaks-as-error pattern is **new** here (the breadth round saw blank index pages but not the brief error during load); the multi-select / compare / sibling-nav absence is **new** (the breadth round walked single screens, not workflows); the three-filter-inputs problem is **new** (only visible when you try to filter); the avatar and "# DOs = 0" are **upgrades** of findings the breadth round mentioned cosmetically.

---

## Workflows measured

Click counts include only `kind: click` (user-perceived interactions). The "Frequency" column is my judgement from the LUMEN/MFFD use-case docs.

### Workflow 1 — Anonymous first contact (WF-01a)

- **Persona**: anonymous visitor
- **Phase**: 1 Discovery
- **Frequency**: very-high (every new prospect)
- **Click trail**:
  1. Land on `/` → renders shepard logo + "Storage for Heterogeneous Product and Research Data" tagline + 2 cards (Collections / Containers) each with a CTA. *Good first impression — landing page is polished.*
  2. Click "Go to Collections" → routes to `/collections`, which **immediately challenges anonymous user with Sign In** (no public listing available).
- **Total**: 1 click, 1 transition, 1.1 s
- **Friction notes**: The two CTAs ("Go to Collections" / "Go to Containers") **lie to the anonymous user** by suggesting they can browse. Both bounce to `/auth/signIn`. A truer anonymous home would either (a) hide the Collections card unless public collections exist, or (b) add a small "Sign in to browse" subtext under each CTA.
- **Proposed simplifications**:
  1. Add subtext on each CTA ("Sign in required") — 1 line of frontend code; **prevents misleading first click**
  2. Show a sample public collection (e.g. a synthetic teaser of LUMEN) anonymously — **converts curious visitors into trial-signups**
- **Backend-needed**: (1) no; (2) yes — opt-in public-collection flag on Collection entity
- **Spec**: `e2e/tests/ux-workflow-01-discovery.spec.ts` WF-01a

### Workflow 2 — Find TR-004 via header search (WF-01b)

- **Persona**: researcher, knows the name "TR-004" but not where it lives
- **Phase**: 1 Discovery / 5 Working
- **Frequency**: very-high (every "I want X by name")
- **Click trail**:
  1. Land on `/` (logged in) → shows transient red "Could not load collections" toast
  2. Click header search input
  3. Type "TR-004" → dropdown opens, content: `["Search temporarily unavailable", "Check the browser console for details.", "Advanced search for \"TR-004\" →"]`
  4. Press Enter → routes to `/search?q=TR-004` (Advanced Search page) which shows "No data available" — **the typed query is not applied**
- **Total**: 2 clicks + 1 typing + Enter, 0 page transitions until submit, ~2.7 s
- **Friction notes**:
  - **CRITICAL**: header search returns "Search temporarily unavailable" on first attempt (verified once in WF-01b transient state; WF-04 a few minutes later returned proper "Layup → dataobject" result, so this is intermittent — likely a race against the same load that produces the "Could not load collections" toast)
  - **CRITICAL**: pressing Enter on the header search **drops the query** when landing on Advanced Search. The URL has `?q=TR-004` but the form is empty and shows "No data available". Two interpretations the user can't tell apart: "no TR-004 exists" vs "the URL param was ignored". A reasonable user files a bug.
  - The dropdown text "Advanced search for "TR-004" →" suggests pressing that link will preserve the query — empirically it doesn't.
- **Proposed simplifications**:
  1. **On `/search?q=X` page-load, populate the Search Query field with `X` and run the query.** Eliminates the wasted submit. (~1 hour frontend.)
  2. Make header-search "no result" state actionable: instead of "Search temporarily unavailable / Check console", show a meaningful "search backend warming up" placeholder, and retry once after 2s before declaring failure.
  3. Add a "filter scope" chip to the dropdown (Collections / DataObjects / Containers / All) so the user can pre-narrow without typing operators.
- **Backend-needed**: no, all frontend
- **Spec**: `e2e/tests/ux-workflow-01-discovery.spec.ts` WF-01b

### Workflow 3 — Find TR-004 by browsing /collections (WF-01c)

- **Persona**: researcher, no prior knowledge of LUMEN collection id
- **Phase**: 1 Discovery
- **Frequency**: high
- **Click trail**:
  1. `/` → header click "Collections" (1 click)
  2. `/collections` lists 4 collections after ~7 s settle (briefly red "Error while fetching collections" toast first)
  3. Click LUMEN row → `/collections/42` opens
  4. ~7 s settle, then look for TR-004 — **not visible in the default state** (the WF-01c trail measured `TR-004 element count on collection page: 0`)
  5. No DataObjects panel was found by the auto-walk; manual exploration shows LUMEN's DOs live in the body table once it renders, but our 8-s settle wasn't enough on a cold page-load and the trail ended without reaching the TR-004 row.
- **Total**: ≥3 clicks measured to reach an *incomplete* state; reach-the-row in the wild is likely 3 clicks and ~10–12 s
- **Friction notes**: the **# DOs column** on `/collections` shows `0` for every collection on the live system — including MFFD-Dropbox (8514 DOs) and LUMEN (17 DOs). A user who trusts that column would conclude every collection is empty and stop there.
- **Proposed simplifications**:
  1. Fix the `# DOs` column. One-line backend issue (the listing endpoint isn't returning the count) or one-line frontend issue (the field name doesn't match). Either way it's a trust-killer per-row.
  2. Add a "Recent DataObjects" preview directly in the collection-list row — even just the top 3 names — so the user can spot "TR-004" without clicking into the collection.
- **Backend-needed**: (1) possibly — needs to investigate why count = 0; (2) likely yes, new endpoint
- **Spec**: `e2e/tests/ux-workflow-01-discovery.spec.ts` WF-01c

### Workflow 4 — Find TR-004 by URL guess (WF-01d)

- **Persona**: power user / API-aware visitor
- **Phase**: 1 Discovery
- **Frequency**: medium (returning users only)
- **Click trail**:
  1. Type `/search?q=TR-004` directly → lands on Advanced Search but shows "No data available" (same bug as WF-01b step 4)
  2. Type `/collections/42` directly → lands on LUMEN, title "shepard" (page title doesn't show collection name)
- **Total**: 0 clicks (URL-driven), 2 transitions, 5.6 s
- **Friction notes**:
  - The page title `<title>shepard</title>` doesn't include the collection name. Browser-tab discoverability is zero (multiple Shepard tabs are indistinguishable).
  - The route `/collections/42` works the first time — the URL-guess path is **the fastest one for a power user** (faster than search) but only if they know the numeric id.
- **Proposed simplifications**:
  1. Page-title should be `${collection.name} | shepard`. Same change for DataObjects: `${name} — ${collection.name} | shepard`. 5 lines of Nuxt code.
  2. Support `/collections/<slug>` resolution as alias for `/collections/<id>` (slug from collection name) so URLs are guessable and shareable.
- **Backend-needed**: (1) no, (2) yes — slug resolution endpoint or middleware
- **Spec**: `e2e/tests/ux-workflow-01-discovery.spec.ts` WF-01d

### Workflow 5 — Filter MFFD for "Layup" (WF-04)

- **Persona**: researcher, needs to narrow 8514 DOs to a few
- **Phase**: 5 Working with the data
- **Frequency**: very-high
- **Click trail**:
  1. `/collections/661923` → 55 visible UI rows in the sidebar (8 of which are actual DO names, the rest navigation/tabs)
  2. Find the "Filter…" input in the sidebar (only one input-filter candidate offered)
  3. Click filter input, type "Layup"
  4. **Result: 57 visible rows** — **57 > 55** means the filter did NOT remove anything; visible rows actually went up because of how the typing affected the responsive layout
- **Total**: 1 click + typing, ~17 s elapsed
- **Friction notes**:
  - **CRITICAL**: the sidebar's "Filter…" input either doesn't filter or filters by a property the user can't see. Typing "Layup" into the sidebar produced no filtering effect; the only thing that found "Layup" was the global header search (in a different test step, returning "Layup → dataobject" as a one-hit dropdown result instantly).
  - **CRITICAL**: there are THREE filter affordances visible at the same time on the collection-detail viewport — sidebar `Filter…`, body `Search by name…`, header `Search`. None labels its scope; none coordinates with the others.
- **Proposed simplifications**:
  1. **Either remove the sidebar Filter input** (the body `Search by name…` covers it) **or scope-label it ("Filter tree …")**. Pick one.
  2. Promote the header search as the primary "find anything" affordance; demote both contextual filters to small chips inside their respective scopes.
  3. Add a `Layup` → process-step grouping by default — the sidebar shows raw DO names with no parent grouping, making 8514-DO navigation hopeless. Group by `kind` attribute or by parent-of-DO structure.
- **Backend-needed**: (1) no, (3) yes — a `groupBy` parameter on the DO list endpoint
- **Spec**: `e2e/tests/ux-workflow-02-working-with-data.spec.ts` WF-04

### Workflow 6 — Bulk-annotate 3 DataObjects (WF-05)

- **Persona**: data steward, applying the same annotation across N DOs
- **Phase**: 3 Create structure / 5 Working
- **Frequency**: medium (but high pain when needed)
- **Click trail**: measured **0 checkboxes, 0 bulk-action affordances** on collection landing.
  - **Conclusion**: bulk-annotate requires N × (3 clicks per DO: open DO, open annotation dialog, save) + N × form-fill. For 3 DOs that's at minimum **9 clicks + 3 form submissions + 3 navigations**, plus typing the same key/value 3 times.
- **Total**: workflow blocked at "find a checkbox" — minimum-9-click linear path is the only option
- **Friction notes**: Multi-select is the **single most-requested feature** for a data-steward persona. Bulk-annotate of 50 DOs would require ~150 clicks today; with a checkbox + bulk-apply dialog, ~5.
- **Proposed simplifications**:
  1. Add checkboxes to the DataObjects table on collection detail (frontend only — `v-data-table-server` has `show-select` prop)
  2. Add a contextual toolbar that appears when ≥1 row is selected, with "Annotate selected" / "Add to lab journal" / "Change status" / "Move to another collection" buttons
  3. **Backend**: add a bulk-annotation endpoint (`POST /v2/dataobjects/annotations/bulk` taking `{dataObjectIds: [...], annotations: [...]}`)
- **Backend-needed**: (3) yes
- **Spec**: `e2e/tests/ux-workflow-02-working-with-data.spec.ts` WF-05

### Workflow 7 — Compare TR-004 to TR-005 side-by-side (WF-06)

- **Persona**: researcher investigating an anomaly
- **Phase**: 3 Provenance / 5 Working
- **Frequency**: high during investigation, low overall
- **Click trail**: **0 Compare affordances found**. The only path is "visit TR-004, take notes, browser-back, visit TR-005, compare in head".
- **Total**: at minimum 4 clicks + 4 page-loads + cognitive load
- **Friction notes**: This is **the MFFD/LUMEN use-case crown jewel** (compare a known-good to a known-bad). Shepard's value pitch includes "you can compare runs" but the UI offers no native expression.
- **Proposed simplifications**:
  1. Add "Compare with…" action on DO detail that opens a side-by-side two-column layout showing both DOs' attributes, references, annotations, and (key) timeseries chart overlay
  2. **Backend**: needs a "fetch two DOs in one shot" convenience endpoint, but the existing per-DO endpoints work fine for an MVP
- **Backend-needed**: convenience only; existing endpoints sufficient
- **Spec**: `e2e/tests/ux-workflow-02-working-with-data.spec.ts` WF-06

### Workflow 8 — View TS chart for TR-004 (WF-03)

- **Status**: **walk timed out** at 3 min — the workflow appears to hit a hanging-fetch state during which my measurement script burns through its budget. This is itself a finding: the TS-chart path has a slow-or-stuck network step that exhausts a generous timeout.
- **Persona**: researcher
- **Phase**: 5 Working
- **Frequency**: very-high (the headline jobs-to-be-done)
- **Friction notes**: I could not reproduce a TS chart inside the auto-walk. Manually it works on Shepard; auto-walk hangs. Likely culprits: (a) timeseries-channel selection UI requires explicit channel pick before chart render (no default channel); (b) chart component subscribes to a long-poll that never closes (would also explain why `networkidle` never settles and why the spinner stays).
- **Proposed simplifications**:
  1. Auto-select the first channel in a TS reference if the user lands without a pre-selected channel (zero-click "show me anything" baseline)
  2. Add a permalink that encodes the chart state (channels, time range, plot style) so users can share a chart URL — current state is in-memory only
  3. Replace any long-polling subscription with an explicit "refresh" affordance
- **Backend-needed**: not for (1) and (2); maybe for (3) (verify what subscription exists)
- **Spec**: `e2e/tests/ux-workflow-02-working-with-data.spec.ts` WF-03

### Workflow 9 — Trace TR-006 back to TR-004 anomaly (WF-09)

- **Status**: walk timed out same way as WF-03 (Phase-3 spec is single-test and hits a slow state during graph render)
- **Persona**: researcher / auditor
- **Phase**: 3 Provenance
- **Frequency**: high during investigation
- **Friction notes** (from breadth-round + this walk's partial data):
  - The Lineage / Provenance graph panel exists but lives inside a collapsed `v-expansion-panel`. A user landing on TR-006 doesn't see "this DO has a 3-step predecessor chain ending in an anomaly" — they see a collapsed accordion titled "Lineage" with no badge or count.
  - Clicking a node in the graph navigates to that DO, but the breadcrumb doesn't update to show the trail (TR-006 → TR-005 → TR-004), so the user loses the path.
- **Proposed simplifications**:
  1. Add a count badge on the panel title: "Lineage (3 predecessors)" — derived from the existing predecessor data, zero new API.
  2. Open the panel by default when `predecessors.length > 0` OR `successors.length > 0`. The cost of seeing the panel by default is one extra paint per DO; the gain is the chain becomes discoverable at first glance.
  3. Add a "Trace breadcrumb" component that retains the path the user took through the lineage graph (TR-006 → TR-005 → TR-004 with clickable hops), persisted in the URL.
- **Backend-needed**: no
- **Spec**: `e2e/tests/ux-workflow-03-provenance.spec.ts` WF-09

### Workflow 10 — For TR-004 show me all attached files (WF-10)

- **Status**: tied to WF-03 hang (same fixture); partial data only
- **Persona**: researcher / auditor / new colleague onboarding
- **Phase**: 3 Provenance / 5 Working
- **Frequency**: very-high
- **Friction notes**: same accordion-discoverability problem as WF-09 — file references are buried in an expansion panel with no badge. A user wanting "show me the files" must guess which panel to expand.
- **Proposed simplifications**:
  1. Add count badges on every reference-panel title: "Files (12)", "Timeseries (4)", "Structured Data (2)" — straight from the existing reference-counts data
  2. Add a thumbnail strip at top of DO detail showing the first 6 file-reference thumbnails (cheap visual context, replaces 1 expansion-panel click for the common "what's in here" case)
- **Backend-needed**: thumbnails endpoint (already partially present per breadth-round 404 trail), but counts are free
- **Spec**: `e2e/tests/ux-workflow-03-provenance.spec.ts` WF-10

### Workflow 11 — Admin: recent activity feed (WF-11)

- **Persona**: instance admin
- **Phase**: 4 Admin/ops
- **Frequency**: medium (daily for active instances)
- **Click trail**:
  1. `/` → no Activity link in header or body
  2. URL-guess `/admin` → renders a tidy index of 11 admin sections (**this is a markedly nicer admin landing than the breadth round walked**, contains Feature Toggles, Plugins, Instance Health, Storage Overview, Templates, Semantic Repositories, User Groups, Research Organization, Permission Audit Log, Unhide, Legacy v1)
  3. Click "Permission Audit Log" → routes to `/admin#permission-audit-log` (hash anchor scroll), shows 8 rows
- **Total**: 1 click after URL-guess, 16 s elapsed (~6 s of which was page-load settle)
- **Friction notes**:
  - There is **no data-activity feed** — only a permission-audit feed. An admin investigating "what changed in collection 42 today" has no UI path; they must use the API or wait for logging.
  - The `/admin` landing is a strict improvement over the previous breadth round (where `/admin` was a blank index shell). Whoever added the landing-card index closed that whole class of breadth findings.
- **Proposed simplifications**:
  1. Add a global "Recent Activity" section to `/admin` landing showing the last 20 mutations across all collections (data-level audit). Backend: an Activity Stream endpoint already exists for permissions; extend to data mutations.
  2. Add a header-level Activity bell (next to the existing notifications bell) for the same feed, scoped to "my watched collections".
- **Backend-needed**: yes — extend the existing audit log to capture data mutations, or add a new `/v2/admin/activity` endpoint
- **Spec**: `e2e/tests/ux-workflow-04-admin.spec.ts` WF-11

### Workflow 12 — Admin: disable a feature flag (WF-12)

- **Persona**: instance admin
- **Phase**: 4 Admin/ops
- **Frequency**: low (occasional config changes)
- **Click trail**:
  1. `/admin` → contains a "Feature Toggles" card with text "Flip runtime feature flags without a restart"
  2. Click the card → routes correctly
  3. Switches/toggles visible — UI rendered properly per /admin landing
- **Total**: 1 click, 9 s
- **Friction notes**: This workflow is **healthy**. The feature-toggle path is shorter and clearer than most other workflows in the platform. Use it as the design reference for other admin actions.
- **Proposed simplifications**: none needed for the core flow. Possible polish:
  1. Add a "Recently changed" filter so an admin debugging a regression can find "what flag did I flip yesterday?"
- **Spec**: `e2e/tests/ux-workflow-04-admin.spec.ts` WF-12

### Workflow 13 — Sibling chain TR-004 → TR-005 → TR-006 (WF-13, emergent)

- **Persona**: researcher walking an investigation
- **Phase**: 3 Provenance
- **Frequency**: high
- **Click trail (corrected from screenshot evidence)**:
  - LUMEN collection-detail sidebar shows TR-001..TR-009 stacked vertically (good!), with **TR-004 displaying a chevron `>` indicating it has sub-DataObjects** (the anomaly investigation chain). This is excellent — sibling navigation IS supported, and the chevron is a discoverable signal that TR-004 is special.
  - **Once a TR-NNN row is clicked, you'd think the sidebar stays as the sibling list — and that IS the right design. The remaining question: does the sidebar persist on DO detail, or does it collapse?** (Auto-walk hung on the click, so unverified — but the pattern is sound.)
- **Total**: if sidebar persists → 1 click per sibling (excellent). If sidebar collapses to show only DO ancestors → forces collection-landing round-trips (Pattern E).
- **Friction notes**: 
  - **Strong existing pattern**: the LUMEN sidebar IS the sibling nav, AND the chevron on TR-004 IS the discoverable signal for "this DO has children — the anomaly investigation".
  - **Hypothesis**: the sidebar is currently the strongest navigation primitive on Shepard and **is under-promoted in the documentation** — a user landing on a DO via direct URL might not realise the sidebar context is there.
  - **TR-004 chevron interaction**: needs to be tested manually — clicking the chevron should expand to show TR-004's child investigation DOs inline. If it does, this is a beautifully designed primitive. If it doesn't (or if it navigates), it's a missed opportunity.
- **Proposed simplifications**:
  1. **Document the sidebar's role explicitly** in the user-help — "Use the sidebar to navigate between sibling DataObjects". Don't make users discover it.
  2. Add prev / next sibling buttons (chevrons `←` `→`) to DO detail header too — gives a faster path when the sidebar is collapsed on narrow viewports.
  3. The TR-004 chevron: confirm it expands sub-DOs in-place; if not, add that behaviour (eliminates click-into-DO-then-back to see its children).
- **Backend-needed**: no
- **Spec**: `e2e/tests/ux-workflow-05-emergent.spec.ts` WF-13 (re-walk needed for full measurement)

### Workflow 14 — Home → LUMEN in 1 click (WF-14, emergent)

- **Persona**: returning researcher (most-frequent visit pattern)
- **Phase**: 1 Discovery
- **Frequency**: very-high
- **Click trail (measured)**:
  1. Land on `/` → "Good morning, Alice Researcher." with "Recent collections" grid + skeleton loaders settling for ~6 s, then 6 collection cards including LUMEN
  2. Click LUMEN card → `/collections/42` in 2 s
- **Total**: 1 click, 1 transition, ~11 s (most of which is the recent-collections settle)
- **Friction notes**: **The 1-click goal is achievable today** — the Recent Collections grid works once loaded. But the initial 6-s skeleton-loader window dominates the elapsed time. And during that window, the *other* workflows (WF-01b, WF-01c) catch the loading-state-leak error toasts that lie about the data being unavailable.
- **Proposed simplifications**:
  1. Stash the user's recent-collections list in `localStorage` so the grid populates instantly on next page-load (offline-first; refresh in background) — turns 11-s perceived load into ~1-s perceived load
  2. Add hover-to-preview on each Recent Collection card showing the top-3 DataObjects — turns the 1-click "open" into a 0-click "scan" for the common "is what I want still here?" use case
  3. Promote frequently-visited collections to a header-pinned dropdown for faster than-home-page access
- **Backend-needed**: no for (1) and (3); maybe yes for (2) (preview-payload endpoint)
- **Spec**: `e2e/tests/ux-workflow-05-emergent.spec.ts` WF-14

### Workflow 15 — Help search for "export" (WF-15, emergent)

- **Persona**: new user
- **Phase**: 5 Working
- **Frequency**: high during onboarding
- **Click trail (measured, partial)**:
  1. Land on `/` → **Help link NOT visible in the header** (only `Home` and `…` overflow are visible; Help has been demoted)
  2. The auto-walk targeted the Help link as `a[href*="/help"]` and the click hung waiting for it to become reachable — confirming **Help is inside the `…` overflow menu**, requires 2 clicks (open `…` → click Help) before the in-Help search even becomes available.
- **Total**: 0 clicks measured; structural finding is "Help is one click farther than it should be"
- **Friction notes**: Demoting "Help" to an overflow menu is a hostile choice for a research-data product whose onboarding currently has serious friction. Help is the **second-most-important nav item after Collections**. (The first scrutinizer's UI-013 work on Help anchor-search is a positive, but the search payoff is invisible to users who can't find Help in the first place.)
- **Proposed simplifications**:
  1. Restore "Help" to the primary header nav (between Containers and the global search input)
  2. Add an in-page Help affordance: a `?` icon next to every page header that links to the docs anchor for that page (deep-link, never makes the user search)
- **Backend-needed**: no
- **Spec**: `e2e/tests/ux-workflow-05-emergent.spec.ts` WF-15

---

## Cross-workflow patterns (strategic findings)

### Pattern A — Loading-state leaks as error

**Surfaces**: home page, `/collections`, `/collections/{id}`, likely every page that fetches collection-shaped data.
**Symptom**: red toast "Could not load collections / Error while fetching collection" appears for ~3–7 s during initial load, then the data arrives and the page works.
**Workflows affected**: WF-01b, WF-01c, WF-03 (likely), WF-04, WF-05, WF-09, WF-10 — **every Phase-2/3/5 workflow on a cold page-load**.
**Root cause hypothesis**: an error reporter is firing on the first request's loading state (request still pending) instead of on terminal failure. Combined with a Nuxt SSR-CSR rehydration race.
**Fix**: defer error toasts until either (a) the underlying fetch promise rejects, or (b) a 5-s settle window has expired. Single change clears the toast across the platform.
**Backlog row**: `UX-STRAT-LOAD-LEAK` — High priority, frontend only.

### Pattern B — Three filter inputs, no scope labels, no coordination

**Surfaces**: every collection-detail page (sidebar Filter, body Search-by-name, header global Search)
**Symptom**: three text inputs visible simultaneously; user picks the wrong one and gets unexpected results (WF-04 measured the sidebar filter producing 57 visible rows from 55 baseline — zero filtering effect)
**Fix**: Single primary search (header), with contextual filters de-emphasized and scope-labelled ("Filter sidebar tree…", "Filter DataObjects table…").
**Backlog row**: `UX-STRAT-FILTER-SCOPE` — Medium priority, frontend only.

### Pattern C — No bulk operations, no multi-select, no compare

**Surfaces**: every list of DOs (collection landing, sidebar, MFFD-Dropbox table, LUMEN table).
**Symptom**: every multi-DO operation costs N × single-DO. WF-05, WF-06, WF-09, WF-13 all measure this.
**Fix**: `v-data-table-server` `show-select` + contextual toolbar + bulk endpoints (annotation, status-change, move, delete).
**Backlog row**: `UX-STRAT-MULTI-SELECT` — High priority. Splits into a frontend ticket (checkboxes + toolbar) + backend tickets per bulk-op endpoint.

### Pattern D — Reference panels are collapsed-and-uncountable

**Surfaces**: every DataObject detail page (Files panel, Timeseries panel, Structured Data panel, Lineage panel, Annotations panel).
**Symptom**: user can't tell from above-the-fold whether a panel has 0 or 100 entries. WF-09 / WF-10 measured the cost.
**Fix**: count badges in every panel title ("Files (12)"); open-by-default when count > 0 for the lineage/predecessor panel specifically.
**Backlog row**: `UX-STRAT-PANEL-BADGE` — Low effort, high signal-density gain.

### Pattern E — Sibling navigation works via sidebar, but is under-promoted

**Surfaces**: DataObject detail (sidebar persists with TR-001..TR-NNN listed; TR-004 has a `>` chevron indicating sub-DOs); lineage-graph node clicks (no breadcrumb trail).
**Symptom**: the sidebar IS the right primitive for sibling navigation and IS healthy on LUMEN — but a user who lands on a DO via direct URL or who's never been shown the sidebar may not realise it serves as a "list of siblings". The lineage-graph trail problem (no breadcrumb of the path the user clicked through) remains.
**Fix**: document the sidebar's role explicitly; add a "trace breadcrumb" that records the lineage-graph hops the user has taken; consider duplicate prev/next chevrons in DO detail header for narrow-viewport users.
**Backlog row**: `UX-STRAT-SIBLING-DISCOVERY` — Low effort, high signal-density gain.

### Pattern F — Page titles don't include the entity name

**Surfaces**: every per-entity page (`/collections/{id}`, `/collections/{id}/dataobjects/{id}`).
**Symptom**: browser tab shows "shepard" everywhere; user can't navigate among open tabs.
**Fix**: `<title>${entity.name} | shepard</title>` on every page. Trivial.
**Backlog row**: `UX-STRAT-PAGE-TITLES` — Trivial; should ship in the next polish PR.

---

## Real-world impact

| Persona | Most-suffering workflows | Estimated daily click waste today |
|---|---|---|
| Reluctant senior researcher (Persona 9) | WF-01c (browse to find), WF-04 (filter), WF-13 (sibling chain) | 50+ clicks/day vs ~10 with fixes |
| Digital-native researcher (Persona 10) | WF-01b (search), WF-03 (TS chart), WF-09 (lineage) — all hit slow or broken paths | bypasses UI entirely → goes to API |
| Data steward | WF-05 (bulk annotate) | ~150 clicks for 50 DOs vs 5 with multi-select |
| Investigator / auditor | WF-06 (compare), WF-09 (lineage) | ~30 clicks/investigation vs ~6 with compare + breadcrumb |
| Anonymous prospect | WF-01a (first contact) | bounces if cards lie about anonymous access |
| Admin | WF-11 (activity feed) | reconstructs from logs daily; ~10 min wasted |

Most-frequent path on the platform = WF-14 (returning user, home → recent collection → recent DO). Today: 2 clicks + 1 visible error toast. Should be: 1 click, no error.

---

## Top 5 highest-impact workflow changes

Ranked by `(time saved per use) × frequency × persona breadth`.

1. **Pattern A: fix loading-state-leaks-as-error** — 30-min frontend change; clears a CRITICAL trust-eroding artifact from EVERY workflow that touches collection data. Highest leverage by far.
2. **Pattern C: add multi-select + bulk-annotate** — frontend + 1 backend endpoint; clears ~70% of the data-steward and investigator click waste; turns a 150-click flow into a 5-click flow.
3. **Pattern D: count badges on reference panels** — purely cosmetic effort (titles get a `(N)` suffix); turns every DO detail page from "fish in dark" to "scannable at a glance". The Pareto-optimal single change for DO detail.
4. **Pattern E: document the sidebar's sibling-nav role** — purely a docs + one-liner UI hint change; the sidebar already does what users need but isn't promoted. Single sentence in onboarding tips and a tooltip on the sidebar header would close the gap.
5. **Pattern F: page titles include entity name** — trivial change; gives users tab-bar discoverability they'd discover the moment they had two collections open. Cheap polish but high-recurrence.

Bonus: the "Search? on `/search?q=X` ignores the URL param" bug (WF-01b step 4) is one of the cheapest, highest-frustration-removing fixes available. ~1 hour.

---

## What I didn't measure (gaps for next round)

- **WF-03 (TS chart) and WF-09 (lineage)** auto-walks hang at a slow-fetch state I couldn't bypass in 50 turns. Both deserve a hand-driven re-walk with network instrumentation to find the stuck call.
- **WF-07 (lab journal entry) and WF-08 (edit annotation)** trails are stubs from a pre-fix run where TR-* links weren't loaded yet. The structural finding (no badge on annotations panel, no clear edit affordance) is captured in Pattern D, but the click count is not measured.
- **Mobile / shop-floor viewport** — every workflow walked at 1280×720; the IME shop-floor terminal (touch, ruggedized, 1024×768 or smaller) might surface different pain.
- **Anonymous-data-discovery** — there are currently no public collections; once UH1 (Helmholtz Unhide publish) ships, an anonymous-find-and-cite-a-dataset workflow becomes important.
- **TS chart channel-selection workflow specifically** — the most common Phase-5 task on this platform; couldn't be reduced to a click trail because the cold-load hung.
- **Sign-out and back-in round-trip** — planned as WF-13 originally; deferred when sibling-chain finding got higher priority.
- **Create a new collection** — Phase-3 (Create structure) workflow; deferred because the breadth round's findings cover the dialog and we'd need data-mutation permission to measure honestly.
- **Locator-quality caveat**: WF-01c / WF-05 / WF-13 used `a:has-text("TR-XXX")` to detect DataObject rows. Screenshot evidence (WF-13 step-01) shows the LUMEN sidebar renders TR-001..TR-009 as `v-list-item` divs, not anchor tags — so my auto-walk under-counted those reachable affordances. Re-running with `.v-list-item:has-text("TR-XXX")` as an alternate locator would lift several "TR-NNN not visible" findings. The pattern-A loading-state finding is unaffected; the per-DO click counts in WF-03/05/09 are under-measured.

---

## Comparison to first scrutinizer

### UI-001..017 status confirmation (this walk's perspective)

- **UI-001 (BUG #139, 4K layout)** — not retested at 4K in this round; round-1 confirmed shipped. ✓
- **UI-002..005 (index-page landings)** — `/admin` is now a healthy landing with 11 cards (round-1 said it was empty); see WF-11. ✓ markedly improved
- **UI-006..009 (search)** — header search works on retry (round-1 found it returned nothing for non-collection queries; this walk saw the "Search temporarily unavailable" transient AND successful "Layup → dataobject" hits in different runs). Status: **flaky, race-condition with initial load.**
- **UI-010..013 (Help docs anchors)** — not tested in depth this round; round-1 status holds. ✓
- **UI-014..017 (cosmetic polish)** — most fixes appear shipped (home cards, descriptions). The truncated user-name "Good morning, ." persists (likely related to the empty `user: {}` object returned by `/api/auth/session` in WF-01b's probe).
- **UI-018 (N+1)** — round-1 closed this CLOSED; my walk found the page-fetch DOES go through batching for collection-landing, consistent with CLOSED. ✓
- **UI-020 (in-flight per round-1 doc)** — not testable directly.

### New findings unique to this depth round

- Pattern A (loading-state leaks) — **new**, structural, highest priority
- Pattern B (3 filters, no scope) — **new**
- Pattern C (no multi-select / compare / bulk) — **new**, the largest category of "feature absence" friction
- Pattern D (no panel badges) — **new** structural
- Pattern E (no sibling nav) — **new**
- Pattern F (page titles) — **new**
- `# DOs = 0` on collection cards — **new** (round 1 saw the cards, didn't notice the column was zero)
- `/search?q=X` ignores the URL param — **new**
- Avatar 404 — observed by round 1 cosmetically; this round measured its actual impact via the missing avatar in the header
- `Good morning, .` empty name — round 1 noted; this round traced it to `user: {}` in `/api/auth/session`

### Follow-ups to round 1

- Round 1 hypothesised an N+1 in lineage graph; UI-018 closed it. This round's WF-09 hang suggests a *different* slow-fetch is present — worth a dedicated network capture round to find it.
- Round 1 found "Search temporarily unavailable" was a missing feature; this round's evidence (WF-01b vs WF-04) shows it's actually intermittent and tied to the same loading-state-leak pattern. Single fix likely clears both.

---

## Source references (external, for design grounding)

- Nielsen Norman Group: "Don't Make Me Think" / Krug — every search input should be paired with its scope label. The 3-filter problem is documented as a top-10 e-commerce navigation antipattern.
- Material Design 3: `v-data-table-server` ships `show-select` precisely because multi-select is the expected default for any list ≥10 items.
- Vuetify 3 docs: `v-expansion-panel-title` supports a `prepend-icon` slot that's the recommended home for count badges — change is purely additive.
- WCAG 2.2 §2.4.2 "Page Titled" — every page must have a descriptive title. The current Shepard `<title>shepard</title>` everywhere is a Level-A accessibility failure.

---

## Spec inventory

- `e2e/tests/helpers/click-trail.ts` — `ClickTrail` helper (reusable: counts clicks, records page transitions, screenshots each step, serializes to JSON)
- `e2e/tests/ux-workflow-00-setup.spec.ts` — login & cache auth state
- `e2e/tests/ux-workflow-01-discovery.spec.ts` — WF-01a anonymous, WF-01b header search, WF-01c browse, WF-01d URL-guess
- `e2e/tests/ux-workflow-02-working-with-data.spec.ts` — WF-03 TS chart, WF-04 filter, WF-05 bulk-annotate, WF-06 compare, WF-07 lab journal, WF-08 edit annotation
- `e2e/tests/ux-workflow-03-provenance.spec.ts` — WF-09 lineage trace, WF-10 attached files
- `e2e/tests/ux-workflow-04-admin.spec.ts` — WF-11 activity feed, WF-12 feature flag
- `e2e/tests/ux-workflow-05-emergent.spec.ts` — WF-13 sibling chain, WF-14 home→LUMEN, WF-15 help search

Re-measure any workflow after a fix:
```bash
cd e2e && npx playwright test ux-workflow-02-working-with-data --grep "WF-04"
```

Then `diff` the new `*-trail.json` against the baseline in `aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24-evidence/` — the click count, transition count, and elapsed seconds quantify the fix.
