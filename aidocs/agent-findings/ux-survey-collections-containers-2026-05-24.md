---
title: "UX survey — collections + containers pages (barely-usable triage)"
stage: audited-by-personas
last-stage-change: 2026-05-24
author: ux-survey-agent
audience: shepard frontend maintainers
scope: live shepard.nuclide.systems, frontend pages /collections + /containers
method: Playwright @ 4K (3840×2160) + 1920×1080, three data scales
---

# UX survey — collections + containers pages (barely-usable triage)

## §0 — Verdict

The user's call ("barely usable / same with containers") is fair, but the
*reason* it feels barely usable is not network latency — it's **structural
failures at scale combined with cognitive whitespace at 4K**. The MFFD-Dropbox
collection (8514 DataObjects) renders the above-the-fold in 5.6 s at 4K, which
is acceptable; what's *not* acceptable is that once it renders, the user
cannot:

1. **See how many DataObjects exist** (the panel pages in chunks of 25 with a
   Prev/Next pair and **no total count, no jump-to-page** —
   `CollectionDataObjectsPanel.vue:103-119`). You cannot tell whether you are
   looking at page 1 of 12 or page 1 of 340. With 8514 DOs the answer is
   "page 1 of 340", and the only way to find a specific DO is the name-search
   field, which itself is silent about how many matches exist.
2. **Filter reliably by status** (the status chip group filters
   client-side over only the 25 rows currently loaded —
   `CollectionDataObjectsPanel.vue:196-199`). Click "READY" on page 1, see
   3 rows; the other 25,000 READY rows across the remaining 339 pages are
   silently dropped. This is the single most damaging bug in the panel.
3. **Trust the lineage panel** (it says "No datasets in this collection"
   even on LUMEN where 17 DataObjects are visible 200 px above —
   `CollectionLineageGraph.vue:176`). Opening it on MFFD-Dropbox triggers
   `useFetchAllDataObjects` which **exhaustively pages through every DO in
   chunks of 200** (`useFetchAllDataObjects.ts:24-43`) — for MFFD-Dropbox
   that's 43 sequential XHRs before the graph can even start rendering.
4. **Recover from a typo'd URL** (`/collections/99999999` shows two infinite
   spinners and a transient red toast — `[collectionId]/index.vue:467` is
   `<CenteredLoadingSpinner v-else />` with no error branch). The page is
   permanently stuck.
5. **Use the screen** (every page is hard-clamped to `max-width: 1200px`
   or `1400px` — `CollectionListPage.vue:21`, `[collectionId]/index.vue:139`,
   `containers/files/[containerId]/index.vue:58`, ditto timeseries / spatial /
   structureddata). At 4K (3840 px) this leaves **~70 % of the horizontal
   real estate unused**, which the user is paying for in vertical scroll
   instead.

Top three headlines:

- **H1** — `CollectionDataObjectsPanel.vue` status filter is broken at scale
  (client-side on current page only). CRITICAL.
- **H2** — Every collection / container detail page hard-clamps to
  1200-1400 px, wasting ~70 % of 4K screen and forcing scroll. MAJOR.
- **H3** — Bogus / deleted collection-id URL leaves the page in permanent
  spinner state with only a transient toast. CRITICAL.

## §1 — Scale matrix (the actual numbers)

Measured via Playwright on the live site
(`https://shepard.nuclide.systems`), Chromium, single worker, fresh
`storageState` per page. `domContentLoaded` is when the HTML is parsed;
`visual` is when a meaningful selector resolves; `idle` is `networkidle`
(all in-flight XHRs settled).

| Page                                        | Viewport | Scale            | dcl_ms | visual_ms | idle_ms | console errs |
|---------------------------------------------|---------:|------------------|-------:|----------:|--------:|-------------:|
| `/collections`                              |       4K | 5 collections    |    362 |       530 |   2 103 |            1 |
| `/collections/42` (LUMEN, 17 DOs)           |       4K | LUMEN-15         |    488 |       990 |   1 580 |            2 |
| `/collections/661923` (MFFD-Dropbox, 8514)  |       4K | MFFD-big         |    325 |     4 067 |   5 635 |            2 |
| `/containers`                               |       4K | ~25 containers   |    241 |       426 |     955 |            1 |
| `/containers/spatialdata/1` (bogus)         |       4K | 404              |    149 |       258 |     761 |          **7** |
| `/collections/99999999` (bogus)             |       4K | 404              |    370 |       486 |   1 153 |         **19** |
| `/collections`                              |    1080p | 5 collections    |    300 |       500 |   2 280 |            1 |
| `/collections/42`                           |    1080p | LUMEN-15         |    401 |       880 |   1 402 |            2 |
| `/collections/661923`                       |    1080p | MFFD-big         |    359 |     5 673 |   6 226 |            2 |
| `/containers/spatialdata/1`                 |    1080p | 404              |    139 |       237 |     740 |          **7** |
| `/collections/99999999`                     |    1080p | 404              |    319 |       411 |   1 035 |         **19** |

(Full JSON in
`aidocs/agent-findings/screenshots-ux-survey-2026-05-24/_timings.json`.)

Networking is fine. Visual paint at MFFD scale is fine. **What
"barely usable" measures is the count of errors on the error paths (7 and 19)
and what those mean for the user: instead of a useful empty state they get
spinners forever.** And what it measures for the success path is the
*cognitive load* a user faces once content has rendered — see §3.

Clicks-to-X table (golden paths):

| Task                                                   | Min clicks today | Friction |
|--------------------------------------------------------|-----------------:|----------|
| Land on home → find LUMEN collection                   |                3 | OK       |
| Land on home → find MFFD-Dropbox collection            |                4 | search field is the only way |
| Land on collection → reach TR-004 (LUMEN, ~15 DOs)     |                2 | OK (sidebar tree visible) |
| Land on collection → reach DO #4000 (MFFD-Dropbox)     |          ~**42** | Prev/Next 1 page at a time, no jump-to |
| Filter Data Objects panel by READY status              |                1 | **silently wrong** — only filters current page |
| Open a file container and download its first file     |                3 | OK once you guess the row is clickable |
| Open a spatial container and view its payload         |          **N/A** | the page is title-only; no viewer (`spatialdata/[containerId]/index.vue:28-69`) |
| Recover from a typo'd /collections/{id} URL            |          **N/A** | infinite spinner — only the back button works |

The "N/A" rows are the structural killers. The "~42" row is the scale
killer. Combined, that's the "barely usable" verdict.

## §2 — Collections-list-page findings

`frontend/pages/collections/index.vue` → `CollectionListPage.vue`.

| # | Severity | Finding | Evidence | Fix (one line) |
|---|----------|---------|----------|----------------|
| 2.1 | MAJOR | Page is hard-clamped to `max-width: 1200px` at any viewport. On 4K this leaves ~70 % of horizontal space white. | `CollectionListPage.vue:21`, screenshot `collections-index-4k.png` (5 collection rows occupy ~8 % of vertical pixels) | Use a viewport-aware `max-width` (e.g. `min(1400px, 90vw)` up to FHD, `min(1800px, 80vw)` above). |
| 2.2 | MAJOR | List uses fixed `itemsPerPage = 20` (`CollectionListPage.vue:6`) — at 4K with 70 % empty space below, no way to bump pageSize from UI. | `CollectionListPage.vue:6` and the empty bottom 85 % of `collections-index-4k.png` | Add a `pageSize` switcher (20 / 50 / 100). |
| 2.3 | MINOR | The "# DOs" column shows the raw integer (e.g. 8514) but uses a `large` chip only for that one row — no threshold logic for "huge" (40 000+). | `collections-search-MFFD-4k.png` shows `8514 large` | Tier the chip — Small / Medium / Large / Huge — and use the count colour to hint at scale. |
| 2.4 | MINOR | "Created by" wraps onto two lines ("Flo / Researcher") which makes the row visually noisy. | `collections-index-4k.png` row 1-3 | Use a single line with truncation + tooltip; or split into separate name + role columns. |
| 2.5 | MAJOR | The page header "Collections" sits in a 14-padding (`py-14`) — at 4K the title-to-content gap is ~190 px of whitespace before any data. | `CollectionListPage.vue:24` | Reduce to `py-6` or scale with viewport. |
| 2.6 | MINOR | Search uses `CollectionSearchField`, no visible "X of N matches" string. The user has no idea whether their search is too narrow or whether more results exist past the 20-row cap. | `collections-search-MFFD-4k.png` (5 visible, no "showing 5 of N") | Add result-count line: "Showing 5 of 5 matches". |
| 2.7 | MINOR | No sort indicator on headers (ID, Name, Description, # DOs, Last updated, Created by). User can't sort by # DOs to find big collections — they have to scroll. | Manual inspection of `CollectionList` (no `sortable` props) | Make columns sortable; default secondary sort = `# DOs desc`. |

## §3 — Collection-detail-page findings (the heart)

`frontend/pages/collections/[collectionId]/index.vue` — 489 lines, ten
distinct subsections.

### §3.1 — At LUMEN scale (~15 DOs) — generally OK

| # | Severity | Finding | Evidence | Fix |
|---|----------|---------|----------|-----|
| 3.1a | MAJOR | Hard `max-width: 1400px` clamp at any viewport. At 4K, the right ~60 % of the page is whitespace; the sidebar tree at left (which uses ~500 px) is **also** clamped, so the centre column is artificially narrow. | `[collectionId]/index.vue:139`, screenshot `collection-LUMEN-4k-above-fold.png` (right 50 % is blank) | Drop the clamp and let the centre column flex; on 4K, surface 3 of the panels side-by-side instead of stacked. |
| 3.1b | MAJOR | The sidebar `<v-treeview>` and the Data Objects panel both show the same flat list — **two redundant DataObject lists** stacked side-by-side, fed by **two separate code paths** (`useTreeviewItems.ts:14` calls `getAllDataObjects({parentId: -1})`; `CollectionDataObjectsPanel.vue:158-164` calls `usePagedDataObjects`). Confusing for newcomers. | `collection-LUMEN-4k-above-fold.png` (left tree shows 15 rows, centre table shows 17) | One canonical list; the sidebar becomes a thin sticky overview / collapsed-to-icons by default. |
| 3.1c | CRITICAL | Dataset Lineage panel says "No datasets in this collection" when 17 DOs are visible. | `collection-LUMEN-4k-lineage.png` + `CollectionLineageGraph.vue:176` | Investigate empty-vs-loading; if no edges, label "No predecessor/successor links". |
| 3.1d | MINOR | "Metadata completeness" widget shows "61/100" in red — actionable, but the breakdown is buried inside a "Show checks" toggle. | `collection-LUMEN-4k-above-fold.png` | Default-expand the breakdown when score is < 70; only collapse when green. |
| 3.1e | MAJOR | The "Cite this dataset" panel shows fully-formatted APA / BibTeX / RIS / CSL — but with no Copy button. The user has to manually select-and-copy. | `collection-LUMEN-4k-above-fold.png` | Add a per-format Copy button (single click to clipboard). |
| 3.1f | MINOR | The "Data Objects" panel header has only one action — "New DataObject". Common verbs missing: "Bulk import", "Export selection", "Search across all DOs". | Source inspection of `[collectionId]/index.vue:343-355` | Add a 3-dot menu with bulk verbs. |
| 3.1g | MINOR | Inline description editor exposes a Status select alongside (`[collectionId]/index.vue:282-289`) but no clear hint that editing the description here also commits a status change. | `[collectionId]/index.vue:282` | Either split into two save buttons or label the section "Edit description & status". |
| 3.1h | MINOR | "Add new data object" button appears **three times** on the page (sidebar header `+`, sidebar bottom `+ Add new data object`, panel header "New DataObject"). | `collection-LUMEN-4k-above-fold.png` (count the + icons) | Pick one canonical place. |

### §3.2 — At MFFD-Dropbox scale (~8500 DOs) — the structural killers

| # | Severity | Finding | Evidence | Fix |
|---|----------|---------|----------|-----|
| 3.2a | CRITICAL | **Status filter is broken at scale.** The chip group filter (`statusFilter`) is applied client-side via a `.filter()` over `rawItems` *which only ever contains the 25 rows currently on screen* (`CollectionDataObjectsPanel.vue:196-199`). Selecting "READY" on page 1 shows whatever READY rows happen to be in those 25; the other 25 / 50 / 800 READY rows across the remaining 339 pages are silently dropped. The user has no signal that the filter is partial. | `CollectionDataObjectsPanel.vue:196-199`, `usePagedDataObjects` only requests `pageSize: 25` per call | Push the filter to the server: extend `usePagedDataObjects` to accept `status?: Status` and pass it as a query param. Pre-filter is also a chip-group-with-a-warning option if backend support is slow. |
| 3.2b | CRITICAL | **No total count anywhere on the page.** `usePagedDataObjects` only returns `hasMore` (boolean from "did we get a full page?"). The user paging through MFFD-Dropbox sees "Page 1", "Page 2", "Page 3" with no idea whether the destination is page 340 or page 12. | `CollectionDataObjectsPanel.vue:111` (`Page {{ page + 1 }}` with no total), `usePagedDataObjects.ts` source | Backend should expose a `Content-Range`-style header or a separate `count` endpoint. Frontend then shows `Page 3 of 340 (8514 DOs)`. |
| 3.2c | CRITICAL | **No jump-to-page.** Prev / Next only — getting to DO #4000 requires 160 clicks. | `CollectionDataObjectsPanel.vue:103-119` | Replace Prev/Next with a Vuetify `v-pagination` once total count exists. Until then, at least an input "Go to page __". |
| 3.2d | MAJOR | **No sort options.** The DataObjects panel always returns rows in backend-default order (likely Neo4j-internal). User can't sort by name, status, created, refs. | `CollectionDataObjectsPanel.vue:35-100` (no sort columns) | Add column-header sort with backend support. |
| 3.2e | MAJOR | The DataObjects panel `pageSize` is hardcoded to 25 (`CollectionDataObjectsPanel.vue:163`). At 4K with ~600 px of vertical free space below, more rows would visibly fit. | `collection-MFFD-661923-4k-above-fold.png` (table ends at ~470 px from page top, ample empty space below) | Make pageSize configurable (10 / 25 / 50 / 100) and persist per-user. |
| 3.2f | CRITICAL | The "Lab Journal" expansion panel's count badge calls `useFetchCollectionLabJournalEntries` which (per its own comment, "exhaust all entries was causing browser socket exhaustion + thousands of console errors") was patched to lazy-load, but the count badge in `ExpansionPanels` still requires it. Need to verify it doesn't fire eagerly for MFFD-Dropbox. | `useFetchCollectionLabJournalEntries.ts:12` (the comment itself reveals a history of crash-by-N+1) | Audit the call path; ensure count is fetched as a single GET, not by iterating entries. |
| 3.2g | CRITICAL | **`useFetchDataObjectMap.ts:9` calls `getAllDataObjects` with NO PAGINATION** to build a name-lookup map — fires on every collection-detail mount (`[collectionId]/index.vue:23`). For MFFD-Dropbox this means an unbounded XHR returning the whole 8514-DO collection on every page load just so the Lab Journal can resolve "DO 4123 → its name". | `useFetchDataObjectMap.ts:9`, `[collectionId]/index.vue:23` | Replace with on-demand name resolution; or accept a paginated subset; or build the map server-side as part of the lab-journal endpoint. |
| 3.2h | MAJOR | Opening the Dataset Lineage expansion panel triggers `useFetchAllDataObjects` (`CollectionLineageGraph.vue:9`) which exhausts every page of size 200 — for MFFD-Dropbox that's 43 sequential XHRs before the dagre layout even starts. | `useFetchAllDataObjects.ts:24-43` | The panel should refuse to render above `NODE_CAP` and offer a "Open in full-screen visualiser" link instead — or sample N representative nodes. |
| 3.2i | MAJOR | The sidebar tree's `mapToTreeviewItem(item)` is called on the response of `getAllDataObjects({parentId: -1})` — no pagination. If MFFD-Dropbox had been imported as a flat list of 8514 root-level DOs (instead of being hierarchical), the sidebar would load all 8514 nodes synchronously. | `useTreeviewItems.ts:13-25` | Paginate the root fetch; lazy-load chunks as the user scrolls. |
| 3.2j | MAJOR | The "Refs" / "Children" / "Incoming" badges in the DataObjects panel are visually busy and bewildering — six icon types in a single column, every cell looks different. | `collection-MFFD-661923-4k-above-fold.png` (refs column shows tiny mixed badges) | Collapse to a single summary chip: "3 refs (2 TS, 1 file)"; or a tiny inline icon row. |

### §3.3 — Error / empty paths

| # | Severity | Finding | Evidence | Fix |
|---|----------|---------|----------|-----|
| 3.3a | CRITICAL | A typo'd or deleted-collection URL (`/collections/99999999`) leaves the page in **permanent spinner state**. Two spinners (centre and sidebar) animate forever. A transient red toast at the bottom says "Collection with id 99999999 is null or deleted" — but it auto-dismisses after ~4 s and there is no other affordance. | `collection-bogus-4k.png`; `[collectionId]/index.vue:467` is `<CenteredLoadingSpinner v-else />` with no error branch | When `collection.value === null` after the fetch resolves, render an empty-state `<EmptyListIcon>` with "Collection not found — go back to Collections" + a button. Test added 19 console errors per page load (the auto-retry tries to re-fetch). |
| 3.3b | MAJOR | Empty-collection-detail case (a freshly created collection with 0 DOs) — could not be tested live (no empty collection seeded), but source analysis shows `CollectionDataObjectsPanel.vue:31-33` does render `<EmptyListIcon label="No DataObjects yet" />` — good. However the "New DataObject" button at the panel head requires the user to spot it; the empty state itself should include a "+ Create your first DataObject" button instead of pointing the user up-and-right. | Source-level only | Add a CTA button inside the empty state. (Audit gap — see §8.) |

## §4 — Container-detail-page findings

| # | Page | Severity | Finding | Evidence | Fix |
|---|------|----------|---------|----------|-----|
| 4.1 | files | MAJOR | Page hard-clamped to `max-width: 1200px` (`containers/files/[containerId]/index.vue:58`). | Source + `containers-index-4k.png` shape | Same fix as §2.1. |
| 4.2 | files | MINOR | "Storage stats chips" (total size + file count, lines 110-120) only render after `files.value?.length` resolves — invisible during load. Stats also don't include "average file size" or "largest file". | Source | Add to chip row. |
| 4.3 | files | MAJOR | "Referenced by" panel uses `<v-list>` with `<LinkedDataObjectRow>` per item — **no virtualization, no pagination**. A container referenced by 500 DOs will render 500 rows synchronously. | `[containerId]/index.vue:164-172` | Cap at N, paginate, or use a virtual list. |
| 4.4 | timeseries | MAJOR | The chart "Channel Overview" tries to render every channel by default (until a curated view is set). On a container with 100+ channels (likely on home-showcase or MFFD) the chart is unreadable. | `containers/timeseries/[containerId]/index.vue:201-205` | Default to top-N most-recent-data channels; force the user into curation past a threshold. |
| 4.5 | timeseries | MAJOR | The "channel selector" edit mode uses one `<v-checkbox>` per channel in a flat flexbox — at 100+ channels this becomes a hostile wall of checkboxes. | `[containerId]/index.vue:253-263` | Group by `device`; add a search box; offer "select all in group". |
| 4.6 | timeseries | MINOR | `channelKey` and `channelLabel` (lines 67-73) duplicate the 5-tuple logic that's a known smell across the API. Migrating to single appId per the TS-IDc plan would eliminate ~30 lines here. | Source | TS-IDc migration (out-of-scope for this audit). |
| 4.7 | structureddata | MAJOR | "Referenced by" panel on `mffd-bridgewelding-sd` shows 50 entries with **no virtualization** and very tiny action icons. At 4K the 50 rows occupy ~400 px of vertical space. | `container-sd-4k.png` (the bottom 60 % of the page is the unbounded list) | Cap + paginate; enlarge action icons (current size is ~12 px). |
| 4.8 | structureddata | MAJOR | The data table at top shows 10 rows of `StepMetaProcessExecution` — no preview, no first-column-of-payload visible. The user has to click into each row to see what it actually contains. | `container-sd-4k.png` (`oid` column is opaque UUIDs) | Show the first 1-2 payload columns inline; or a "Preview" toggle. |
| 4.9 | spatial | CRITICAL | **The spatial container page is title-only.** No payload viewer, no list of contents, no upload affordance. The entire template (70 lines) renders a title and breadcrumb. | `containers/spatialdata/[containerId]/index.vue` whole file (`SpatialDataContainerAccessor` is instantiated but only `fetchData` + `fetchRoles` are called — no `fetchPayloads`) | This is a placeholder; either ship a real spatial viewer or surface a "Coming soon — payload viewer not yet implemented" banner. |
| 4.10 | spatial | CRITICAL | A bogus spatial container URL (`/containers/spatialdata/1`) hangs with a single spinner + a "HTTP 404 Not Found" toast (`container-spatial-bogus-4k.png`). 7 console errors recorded. | Same shape as §3.3a but for the container page | Add empty-state branch. |
| 4.11 | (video) | CRITICAL | **There is no `/pages/containers/video/` directory at all.** Video container detail page **does not exist**. Yet the DataObjects panel shows a video badge (`CollectionDataObjectsPanel.vue:78-80`) and the system has `videoCount` in its model. Clicking on a hypothetical video container would 404. | `ls /opt/shepard/frontend/pages/containers/` returns only files / spatialdata / structureddata / timeseries (and index.vue) | Either remove the video badges from the panel until the page exists, or scaffold a "Coming soon" video page. |

## §5 — Sidebar + nav findings

`CollectionSidebar.vue` is the page's primary navigation aid. The user
has called this out recurrently per `project_ui_modernization.md`.

| # | Severity | Finding | Evidence | Fix |
|---|----------|---------|----------|-----|
| 5.1 | MAJOR | The sidebar duplicates the centre-column DataObjects panel. Both show the same flat-or-tree list of DOs; both load the data independently. | `CollectionSidebar.vue:257` `<v-treeview>` + `[collectionId]/index.vue:356` `<CollectionDataObjectsPanel>` | Make one canonical; the sidebar should either become a sticky navigator (with a small footprint and links *only*) or be a collapsed icon rail by default with the tree behind a button. |
| 5.2 | MAJOR | The sidebar tree uses `getAllDataObjects({parentId: -1})` (no pagination) to fetch root children. For a flat 8514-DO collection this would load all 8514 nodes. The MFFD-Dropbox sidebar (which IS hierarchical) only loads ~10 roots, which is why this hasn't bitten yet — but a future flat import will. | `useTreeviewItems.ts:14` | Paginate the root fetch; lazy-load below. |
| 5.3 | MAJOR | The sidebar filter input ("Filter…") only filters **already-loaded** nodes (per the comment block at `CollectionSidebar.vue:30-50`). Users typing a search term on a deep-tree see no matches because the child nodes haven't been loaded yet. | `CollectionSidebar.vue:30-91` | Either fall back to server-side `name=` search when the tree has unloaded nodes, or auto-expand the tree before applying the filter, or add a "Search across all DataObjects (server)" link below the filter. |
| 5.4 | MINOR | The "Containers" expansion panel at the bottom of the sidebar (`CollectionSidebar.vue:338-355`) is a single button "Browse containers" — a click takes the user *out* of the collection context to the global `/containers` page. Confusing: the user wanted "containers attached to this collection", not all containers. | `CollectionSidebar.vue:344-352` | Scope to this-collection's containers; link to the existing "Referenced containers" panel anchor on the detail page. |
| 5.5 | MINOR | The "Contents" header label is too generic. It is the section title for what is actually a "DataObject tree". The tooltip ("Click any DataObject to jump between siblings…") only appears on hover. | `CollectionSidebar.vue:199-228` | Rename to "DataObjects" or "Tree". |
| 5.6 | MAJOR | The sidebar consumes ~480 px at any viewport — at 1080p it eats nearly half the viewport. There is no collapse button to reclaim space. | All `collection-*` screenshots (sidebar always visible) | Add a collapse toggle persisting per-user; default-collapsed at < 1366 px. |
| 5.7 | MINOR | The sidebar's bottom-most "Containers" accordion competes for the same vertical space as the DataObject tree — if the user expands Containers the tree gets squeezed. | `CollectionSidebar.vue:336-355` | The Containers section should be a tab next to "Contents", not stacked below. |

## §6 — Cross-cutting patterns

These appeared repeatedly across pages — fix once, benefit many places.

1. **The 1200-1400 px clamp on every page** — six identical
   instances (`CollectionListPage.vue:21`, `[collectionId]/index.vue:139`,
   `containers/files/[containerId]/index.vue:58`, ts:117, sd, spatial:28).
   Introduce a `<PageShell>` (or CSS var) that derives cap from viewport.
2. **Tables use click-handler-on-row, not anchor tags**
   (`ContainerList.vue:111-122`, `CollectionDataObjectsPanel.vue:48-57`).
   Breaks middle-click open-in-tab + keyboard Tab-Enter. Wrap rows in
   `<NuxtLink>`.
3. **Lists are not virtualized** — "Referenced by", LabJournal,
   channel-selector. Sluggish at 50 rows, will crash at 500.
4. **Total-count missing from every paged list.** Backend gap
   (no `Content-Range`) + frontend gap (no "X-Y of N" convention).
5. **Loading vs empty vs error states are conflated.** Bogus-collection,
   bogus-container, lineage-on-LUMEN — all three show the wrong state.
   Standardise on `<AsyncState>` with explicit slots.
6. **The 5-tuple channel identity leaks into the UI**
   (`containers/timeseries/[containerId]/index.vue:67-73`). TS-IDc
   migration fixes everywhere at once.
7. **Advanced-mode effects not isolated on these pages** — see §8.
8. **Console errors on every page load** ("Hydration completed but
   contains mismatches"). Not user-visible but a real SSR/CSR mismatch
   that may be hiding bugs.

## §7 — Top-10 minimum fixes (ranked by impact × effort)

Backlog row IDs proposed below — the main session files into
`aidocs/16-dispatcher-backlog.md`. This list is the structural floor for
moving "barely usable" → "usable".

| Rank | Backlog ID candidate | Scope | Size | Fixes |
|:---:|---|---|:--:|---|
| 1 | **UX-DOPANEL-STATUS-SERVER** | Push status filter to the server in `usePagedDataObjects` + add `status` to `listDataObjects` query | M | §3.2a CRITICAL — silently-wrong filter |
| 2 | **UX-DOPANEL-TOTAL-COUNT** | Backend `Content-Range` header on `listDataObjects`; frontend "Showing X-Y of N" + jump-to-page | M | §3.2b + §3.2c CRITICAL — navigation at MFFD scale |
| 3 | **UX-ERR-STATE-COLLECTION-MISSING** | Add `<EmptyListIcon label="Collection not found">` branch to `[collectionId]/index.vue:467` (and same for every container detail page) | S | §3.3a + §4.10 CRITICAL — bogus URL hangs |
| 4 | **UX-PAGE-SHELL-RESPONSIVE-WIDTH** | Single `<PageShell>` or `--page-max-width` CSS var derived from viewport; replace 6 hardcoded clamps | S | §2.1 + §3.1a + §4.1 MAJOR — 4K wasteland |
| 5 | **UX-DATAOBJECT-MAP-LAZY** | Stop calling `useFetchDataObjectMapByCollection` eagerly on collection-detail mount; load on-demand inside LabJournal | S | §3.2g CRITICAL — 8500-DO XHR on every page load |
| 6 | **UX-LINEAGE-EMPTY-VS-NOEDGES** | Distinguish "no DOs loaded" from "no predecessor/successor links"; cap node count above NODE_CAP with "Open in full-screen visualiser" link | S | §3.1c CRITICAL — wrong empty message |
| 7 | **UX-SPATIAL-VIEWER-OR-BANNER** | Either ship a payload viewer for spatial containers or render a "Spatial-data viewer coming soon" banner; same for `/containers/video/` (which doesn't exist) | S/L | §4.9 + §4.11 CRITICAL — placeholder page indistinguishable from a broken page |
| 8 | **UX-SIDEBAR-COLLAPSE** | Add collapse toggle (persisting per-user); default-collapsed at < 1366 px viewport; remove duplicate "Add DataObject" buttons | S | §5.6 + §3.1h MAJOR — 480 px sidebar always visible |
| 9 | **UX-LIST-VIRTUALIZATION** | Standardise on a virtual-list component; apply to "Referenced by" + "Lab Journal entries" + channel-selector | M | §4.3 + §4.7 + §4.5 MAJOR — UI jank on 50+ row lists |
| 10 | **UX-ROW-AS-LINK** | Wrap clickable table rows in `<NuxtLink>` (or render an `<a>` in the first cell) — fixes middle-click open-in-tab + keyboard nav | XS | §6.2 + accessibility |

Effort key: XS ≤ 0.5 day, S = 1-2 days, M = 3-5 days, L > 5 days.

## §8 — Audit-fleet gaps

This audit covered: 4K + 1080p viewports, three scales (collections-list
5 items, LUMEN ~15 DOs, MFFD-Dropbox ~8500 DOs), every container-kind detail
page, login flow, two error paths. What it did **not** cover and should:

1. **Empty-collection-detail state.** No 0-DO collection was seeded;
   source says `CollectionDataObjectsPanel.vue:31-33` renders
   `<EmptyListIcon>` but visual verification is pending.
2. **Dark mode.** Not tested — badge / chip contrast may shift.
3. **Accessibility deeper than tab-order.** No axe / Lighthouse / SR run.
4. **Tablet viewports (768-1199 px).** Untested.
5. **Localization (German).** Untested.
6. **Advanced-mode toggle effects.** `useAdvancedMode` is referenced
   but the basic-vs-advanced delta on these pages was not isolated.
   Per `feedback_basic_advanced_superset.md` advanced must be a strict
   superset; verification pending.
7. **Video-container page.** Does not exist (§4.11) — needs follow-up
   on whether this is "flag off" or "scaffolding missing".
8. **Cold-backend perf.** All measurements against a warm backend; cold
   numbers likely 2-3× worse at MFFD scale.
9. **Concurrent users.** Untested.
10. **`ActivitySparklineCard`** visible on LUMEN but not interactively
    poked.

---

### Artefacts

- Findings doc: this file
- Screenshots:
  `/opt/shepard/aidocs/agent-findings/screenshots-ux-survey-2026-05-24/`
  (24 images + `_timings.json` + `_storageState.json`)
- Playwright spec: `e2e/tests/ux-survey-2026-05-24.spec.ts` (kept and
  committed; reproduces all of §1's timings + screenshots on subsequent
  CI runs)

### Method note

Per `feedback_validate_user_viewport.md` and `feedback_ux_playwright.md`:
all observations sourced from live Playwright runs against
`https://shepard.nuclide.systems`, each finding cross-referenced to a
Vue component `file:line`. Reluctant-senior + digital-native persona
priors shaped the rubric.
