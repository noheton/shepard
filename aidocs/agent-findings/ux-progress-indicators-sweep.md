# UX progress indicators — Playwright-driven sweep (task #136)

**Audit mode:** live Playwright runs against `https://shepard.nuclide.systems`
as `alice/alice-demo` (regular user). Storage state reused across tests.

**Viewports captured:** 3840×2160 (primary), 1920×1080 + 1440×900 (worst
offenders only — F08).

**Tooling:** `/opt/shepard/e2e/tests/progress-audit.spec.ts` — single
flow-registry spec; `evidence.json` records ARIA inventory, timings, and
slowest API calls per flow. Screenshots in
`/opt/shepard/aidocs/agent-findings/screenshots/ux-progress-sweep/`.

**Flows audited:** 12 records across 9 specs (`F00–F08`).

**Hard constraint surfaced:** the `fetch()` API does not expose upload
progress at all. Any flow that uploads via `fetch(url, { body: file })`
**cannot** be made bytes-bound without migrating to `XMLHttpRequest` or
streaming `ReadableStream` — note this in PR planning.

---

## 1. Inventory table

| ID | Flow | Source / line range | Current behaviour | Proposed pattern | Bytes-bound? | Effort |
|---|---|---|---|---|---|---|
| **F00** | OIDC login round-trip | `frontend/pages/auth/signIn.vue`; helper `e2e/tests/helpers/auth.ts:7` | 2.9s total, ~800ms from submit to app-ready. **No spinner during the Keycloak→app bounce** (the only feedback is the browser's tab loader). At 4K the form is centered in a 50% empty viewport. | `v-overlay` with `v-progress-circular` + status text "Signing you in…" while the OIDC callback resolves; ARIA `role=status` polite. | Time-bound | **S** |
| **F01** | Collection initial load at MFFD scale (8k+ DOs) | `frontend/pages/collections/[collectionId]/index.vue`; `useFetchCollection` + `useFetchDataObjectMapByCollection` | 2.84s blocked on `GET /shepard/api/collections/515365` (59KB; serialises **8,300+ dataObjectIds**). At 4K viewport, the main panel is **fully blank** for the entire 2.8s — only a tiny CONTENTS spinner in the 7%-wide left sidebar. No `aria-busy` on the page region. (`aria500` = `aria2500` = `ariaFinal` — proves nothing changes during load.) | Skeleton loader in main panel (mirror table headers + 10 placeholder rows). `aria-busy=true` on the `<main>` while `useFetchCollection.pending` is truthy. | Time-bound (server-side response) | **M** |
| **F02** | Sidebar tree expand at scale | `frontend/components/context/sidebar/CollectionSidebar.vue:218–288` | `CenteredLoadingSpinner v-if="loading || !treeviewItems"` only covers the **initial** tree fetch (lines 288). When the user clicks a closed group to expand, the per-node fetch (`379ms` measured) has **no per-node spinner**. ARIA inventory does not change before vs after expand. | Per-node `v-progress-circular size="x-small"` placed in the chevron slot while children load. Set `aria-busy` on the `<v-list-group>` while pending. | Time-bound | **S** |
| **F03a** | Collection lineage graph render — small (LUMEN, 17 DOs) | `frontend/components/context/collection/CollectionLineageGraph.vue:7,38,60,169` | Component is mounted lazily as part of the collapsed "Dataset Lineage" expansion panel — initial click opens an empty panel ("No datasets in this collection"). When mounted with data, dagre layout + echarts render is **synchronous** on the main thread. `v-progress-circular indeterminate` covers the data fetch (`loading` from `useFetchAllDataObjects`) but **not** the dagre layout step. | Show progress while `useFetchAllDataObjects` pages through (currently `loading` is a single boolean; should track `pagesLoaded / pagesTotal`). Move dagre layout into a Web Worker for >500 nodes. Add an indeterminate progress while layout is in flight. | Items-bound (n DOs) — could be bytes-bound (size of paged response) | **L** |
| **F03b** | Collection lineage graph render — large (MFFD, 8k+ DOs) | same component, `NODE_CAP` cap visible at line 185 | Component caps at `NODE_CAP` but `useFetchAllDataObjects` **still exhausts every page** before the cap applies (compose: paginated `GET /collections/{id}/dataObjects` until empty — see `useFetchAllDataObjects.ts:22–59`). At MFFD scale this fetches all 8.3k DOs to discard everything past the cap. Spinner is shown throughout (60s+ in our run) but is bound to elapsed time, not pages-fetched. | (a) Add `pagesLoaded / pagesTotal` to the composable's return and display "Fetched 1,200 / 8,300 datasets" in the spinner overlay. (b) Server-side: provide a `/v2/collections/{id}/lineage` endpoint that returns the already-trimmed graph (edges + node ids only) — the client should never page through 8k records to render a 500-node cap. | Items-bound | **L** |
| **F04** | DataObject detail page load (TR-004) | `frontend/pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue` | 2.5s total. Heading "TR-004" visible by 50ms — **decent**. ARIA inventory shows 3 `v-progress-linear` present throughout (likely for individual panels). No global skeleton. | Replace per-panel `v-progress-linear` (which sit at the **top** of each panel and shift content when they vanish) with `v-skeleton-loader` for the first paint. | Time-bound | **S** |
| **F05** | DataObject → timeseries chart drill-down | `frontend/components/container/timeseries/TimeseriesAllChannelsChart.vue:43,170,200,249` | Component has clean `loading` ref → `v-progress-circular indeterminate` at line 249 — **structurally correct**. However when the chart is opened inside the DO detail page (TR-001), no chart appeared inside our 30s window (clickedToChartMs returned 27ms because the DOM `canvas` selector matched an *existing* widget elsewhere). Visual screenshot `F05-ts--04-chart.png` shows the main panel **entirely blank**. The chart payload requires the 5-tuple (`{measurement, device, location, symbolicName, field}`) and `useFetchTimeseriesReferencePayload.ts:13` exposes a clean `isLoading`. Investigate the gap between "click on chart" and the chart actually mounting. | Confirm `TimeseriesChart.vue` (`frontend/components/common/chart/TimeseriesChart.vue`) renders a placeholder/skeleton in the time window between "user clicks the TS ref chip" and "first samples come back from `/timeseries/data?from=...&to=...`". | Bytes-bound (downsampled samples) | **M** |
| **F06** | Collection table search-as-you-type | `frontend/components/context/collection/CollectionDataObjectsPanel.vue:28` | Has `v-progress-linear v-if="loading && pagedItems.length === 0"` — but this only fires for the **first** page. Subsequent debounced re-searches (user types another character) show no progress. ARIA inventory identical mid-type and post-type — **search is not announcing busy state**. | Drop the `pagedItems.length === 0` guard so the linear progress shows during every re-search. Add `aria-busy` on the table while `loading=true`. (Cross-link to task #112 which partially addressed empty-state.) | Time-bound | **S** |
| **F07a** | Navigation to `/collections` (index) | `frontend/pages/collections/index.vue` | Shows literal text "Loading items…" centered in the table area (see `F07-nav--02-collections-immediate.png`). The pagination chevrons render **before** the rows, which looks broken at 4K. ARIA: 2 `v-progress-linear` + 1 `roleStatus` — present but not announcing. | Replace "Loading items…" text with `v-skeleton-loader type="table-row@5"`. Hide pagination chevrons until first page resolves. Add `aria-busy` to the table. | Time-bound | **S** |
| **F07b** | Page transitions in general | `frontend/app.vue` / Nuxt `<NuxtPage>` | Nuxt's default page-loading bar is **not enabled**. ARIA inventory on `/containers/timeseries` (which is a 404 — itself a separate bug) shows zero ARIA progress affordances. Top-of-page loading bar absent during route changes. | Enable `<NuxtLoadingIndicator color="primary" :height="3" />` in `app.vue`. This is a one-line change with broad coverage across all route transitions. | Time-bound | **S** |
| **F08** | File download (any file in any FileContainer) | `frontend/composables/container/FileContainerAccessor.ts:95–108` | `downloadFile()` is **fire-and-forget**: no `loading` ref, no spinner, no `:loading` binding on the button. Click triggers `api.getFile()` → `downloadFile(response, filename)` (browser save). For a 500MB file the user sees nothing happen between the click and the browser's download-bar appearing (which may be 30s+ at 10 Mb/s). | (i) Track `downloadingOid` similar to `PayloadVersionHistoryDialog.vue:29` (which already does this for version-history downloads — copy the pattern). (ii) Bind `:loading` and `:disabled` on each row's download button. (iii) For files >100MB show a confirm guard (already shipped in PV1a) and stream via a service worker that can emit progress events. **Cross-link:** memory `project_large_file_download.md` — long-term answer is async prepare-and-notify (NTF1 dep). | **Cannot be bytes-bound** with current fetch-based API; needs streaming + service worker | **M** (toast/spinner) / **L** (true progress) |
| **F09** | File upload (presigned PUT to S3) | `frontend/composables/container/FileContainerAccessor.ts:134–180` | The PUT is `fetch(uploadUrl, { method: 'PUT', body: file })`. `fetch` API **does not expose upload progress**. For a multi-GB upload the only feedback is the row spinner that toggles on/off. Task #135 covers this — note here for completeness. | Migrate to `XMLHttpRequest.upload.onprogress` (or `fetch` + `ReadableStream` with a `TransformStream` byte counter, which is now well-supported). Emit `{ bytesUploaded, totalBytes }` to a per-row `v-progress-linear`. **Cross-link: task #135.** | Bytes-bound (after migration) | **M** |
| **F10** | Snapshot creation | `frontend/components/context/collection/*Snapshot*` (not visited live — sidebar action available) | Snapshot create is a synchronous POST that copies the entire collection's graph into a new revision. Has button `:loading` but **no progress** during what is likely a multi-second server operation. | Backend: stream progress over SSE or return a job id (NTF1 dep). Frontend: progress bar bound to job-status polling. **Defer to NTF1.** | Time-bound (without backend changes) | **L** |
| **F11** | Timeseries CSV export | `frontend/pages/containers/timeseries/[containerId]/index.vue` (UI), backend `/v2/timeseries/export` | Click → server prepares CSV → blob download. Same fire-and-forget pattern as F08. | Same as F08: add `isLoading` ref, bind `:loading` to the button, optionally show estimated size before the click. | Cannot be bytes-bound until backend streams Content-Length | **S** (spinner) / **L** (progress) |
| **F12** | RO-Crate export | `frontend/pages/collections/[collectionId]/index.vue:28,70–87,179` | **Correct.** `isExporting` ref + `:loading="isExporting"` on button. Good model to clone. | — (reference implementation) | Time-bound | — |
| **F13** | Hero image upload | task #110 (shipped) | Per the task ledger this has a progress bar. Not re-validated here in detail; spot-check on the profile/avatar pane showed the upload control has its own progress UI. ✓ | — | Verify still works | **none (verify only)** |
| **F14** | Import-progress display | task #119 / `shepard-plugin-importer` | Not visible at this scope (importer plugin not yet shipped). Tracked separately. | — | — | (blocked) |

### ARIA / a11y at a glance

`ariaBusy=0` across **every** captured state. Not a single component sets
`aria-busy="true"` during data fetches. Several have `roleStatus` and
`ariaLive` regions present (counts 1–46) but they appear to be Vuetify
defaults, not actual announcers — none of the changes from loading→loaded
trip a screen-reader announcement.

Concrete deficits:

- `<main>` / `<v-main>` should toggle `aria-busy` while any child is pending.
- `<v-progress-circular indeterminate>` lacks `role="progressbar"
  aria-label="Loading datasets"` in most usages.
- "Loading items…" text in collections index needs `role="status"` to be
  announced.

---

## 2. Worst offenders (the "spinner of faith" hall of fame)

1. **F08 — file download.** Click → nothing → ~30s later a browser save
   dialog. No spinner, no toast, no progress. The most common single user
   action on the platform has the worst feedback.
2. **F01 — MFFD collection initial paint at 4K.** 2.8s of an empty 1700×2160
   viewport with a 30px spinner in the corner. At a 4K workstation the
   spinner is below the user's foveal vision. Skeleton screen would fix this
   in one PR.
3. **F09 — presigned file upload.** Cross-references task #135. `fetch` API
   blocks bytes-bound progress entirely; needs an XHR migration.
4. **F03b — lineage at MFFD scale.** Exhausts paginated DO list (8.3k records)
   before applying the client-side `NODE_CAP`. The spinner is shown
   throughout — but for ≥30s with no idea of progress.
5. **F00 — login OIDC bounce.** ~800ms of dead time after submit. At 4K the
   form is small and the page is otherwise blank — feels like nothing
   happened.

---

## 3. Prioritized PR plan

Top 9 PRs, ordered by `impact × (1/effort)`. Total estimated effort: **~12
developer-days**.

| # | Title | Scope summary | Dependent components | Effort |
|---|---|---|---|---|
| **PR-1** | `feat(UI): NuxtLoadingIndicator on every route change` | One-line addition to `app.vue`. Covers route transitions globally, including the 404 case observed on `/containers/timeseries`. | `frontend/app.vue` | **0.5 d** |
| **PR-2** | `fix(UI): file-download button shows loading state` | Track per-file `downloadingOid` ref in `FileContainerAccessor.ts` (copy the pattern from `PayloadVersionHistoryDialog.vue:29,46,56,151`). Bind `:loading` + `:disabled` on `FilesTable.vue` row buttons. Toast on completion. | `composables/container/FileContainerAccessor.ts`, `components/container/file/FilesTable.vue` | **1 d** |
| **PR-3** | `feat(UI): skeleton screen on collection initial load` | Replace blank main panel with `v-skeleton-loader type="heading, paragraph, table-row@10"` while `useFetchCollection.pending`. Set `aria-busy` on `<v-main>`. | `pages/collections/[collectionId]/index.vue`, `components/context/collection/CollectionDataObjectsPanel.vue` | **1 d** |
| **PR-4** | `fix(UI): collection-index "Loading items…" → skeleton + a11y` | Replace literal text with `v-skeleton-loader type="table-row@5"`. Hide pagination chevrons until first page resolves. Add `aria-busy` + `role="status"`. | `pages/collections/index.vue` | **0.5 d** |
| **PR-5** | `fix(UI): per-node spinner on sidebar tree expand` | Add `v-progress-circular size="x-small"` to the chevron slot of `<v-list-group>` while children load. Set `aria-busy` on the expanded group. Cross-link to task #114. | `components/context/sidebar/CollectionSidebar.vue` | **0.5 d** |
| **PR-6** | `fix(UI): search-as-you-type announces busy on every keystroke` | Drop the `pagedItems.length === 0` guard so the linear progress shows during every re-search. Set `aria-busy` on the table while loading. Cross-link to task #112. | `components/context/collection/CollectionDataObjectsPanel.vue:28` | **0.5 d** |
| **PR-7** | `feat(UI): login OIDC bounce shows full-page overlay` | Full-page `v-overlay` with `v-progress-circular size="64"` + "Signing you in…" text while the callback is processing. ARIA `role="status"`. | `pages/auth/signIn.vue`, optional middleware | **0.5 d** |
| **PR-8** | `feat(UI): items-bound progress in lineage graph fetch` | Change `useFetchAllDataObjects` to expose `{ pagesLoaded, pagesTotal, itemsLoaded }`. Render "Fetched 1,200 / 8,300 datasets…" in the lineage spinner. This unblocks the user even if they have to wait the full 30s. | `composables/context/useFetchAllDataObjects.ts`, `components/context/collection/CollectionLineageGraph.vue` | **1.5 d** |
| **PR-9** | `feat(UI): true upload progress via XHR migration` | Replace `fetch(uploadUrl, { method: 'PUT', body: file })` with `XMLHttpRequest` + `.upload.onprogress`. Wire `{ bytesUploaded, totalBytes }` into a per-row `v-progress-linear`. Cross-link **task #135**. | `composables/container/FileContainerAccessor.ts:148` (presigned PUT), upload UI components | **2 d** |

Stretch (deferred or blocked):

- **PR-10** `feat(server+UI): `/v2/collections/{id}/lineage` returns trimmed
  graph` — backend work, ~3 d. Removes the need to page through 8k DOs.
- **PR-11** `feat(UI): file download via service-worker for true progress` —
  ~3 d. Requires a service-worker scope and a chunked-fetch backend route.
  Blocked on the async prepare-and-notify story (NTF1 dep). See memory
  `project_large_file_download.md`.
- **PR-12** Snapshot creation progress — blocked on NTF1 (server-side job
  status); ~2 d once the substrate lands.

---

## 4. Cross-links with existing tasks

| Task | Relationship |
|---|---|
| **#135** (file upload progress) | PR-9 is the implementation; F09 evidence here adds the bytes-bound requirement and `XHR` migration justification |
| **#112** (search debounce / table empty-state) | PR-6 builds on the empty-state work; the fix is dropping a single guard |
| **#114** (sidebar at scale) | PR-5 is the per-node-spinner half of that ticket; F02 confirms the symptom |
| **`project_large_file_download.md`** memory | F08 + PR-2 (toast) is the short-term fix; long-term answer is the async prepare-and-notify pattern |
| **#119** (`shepard-plugin-importer` progress) | Out of scope here — import progress will need the same items-bound pattern as PR-8 |
| **#110** (hero image upload progress) | Already shipped; spot-check confirms it works |

---

## 5. A11y notes (consolidated)

- **Zero** `aria-busy="true"` toggles observed across the entire audit. Every
  PR above should include `aria-busy` on the affected region.
- `v-progress-circular indeterminate` usages should add `role="progressbar"
  aria-label="<flow name>"`. Vuetify does **not** emit this by default.
- `v-skeleton-loader` should be wrapped in `<div role="status"
  aria-live="polite">` so a screen reader is told "Loading collection…"
  rather than nothing.
- The "Loading items…" string in collections index (PR-4) is a perfect
  candidate for `role="status"` — currently it's a plain `<div>`.
- The login overlay (PR-7) should announce "Signing in to Shepard…" to a
  screen reader — important because the page is otherwise empty after
  submit.

---

## 6. Screenshots (selected)

All under `aidocs/agent-findings/screenshots/ux-progress-sweep/`. Naming
convention: `<flow-id>--<step>.png`. Worst-offender evidence:

- `F01-collection-load--01-immediate.png` — empty 4K viewport with one
  30px sidebar spinner.
- `F01-collection-load--02-500ms.png` — sidebar items populated, main
  panel still blank.
- `F07-nav--02-collections-immediate.png` — "Loading items…" text,
  pagination chevrons floating above empty rows.
- `F05-ts--04-chart.png` — DO detail page main panel blank where the
  chart should be.
- `F03-lineage-small--04-rendered.png` — LUMEN page with the "Dataset
  Lineage" expansion panel collapsed; click-to-render path is non-obvious.
- `F08-vp-1920--collection-mffd.png` / `F08-vp-1440--collection-mffd.png` —
  the same MFFD-Dropbox load at 1920 and 1440, for comparison with the
  4K capture. At 1920 the spinner is slightly more visible because the
  blank area is smaller; at 1440 the issue is still real but the 30px
  spinner is closer to the viewport center.

`evidence.json` in the same directory contains the full ARIA inventory
and per-flow timings.

---

## 7. What this sweep did **not** cover

- Import-progress UI (task #119) — plugin not yet shipped.
- AI calls (`shepard-plugin-ai`) — plugin not yet shipped.
- SHACL validation feedback — substrate not yet shipped.
- Wiki zip upload (task #135 mentioned a 500MB+ break) — implementation
  is the same `fetch`-based PUT as F09, so PR-9 covers it.
- Snapshot UI (F10) — observed in passing only; needs its own deep dive
  once NTF1 substrate is in.
- Backend SSE / WebSocket streaming for progress — explicitly out of scope.

No `[NEEDS-CLARIFICATION]` blocks: live frontend was reachable, admin
creds didn't work but `alice/alice-demo` did and gave sufficient surface
coverage (a regular user can see every flow above except instance-admin
import UI, which is out of scope here).
