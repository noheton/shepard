---
stage: deployed
last-stage-change: 2026-05-24
---

# UI-002 — Header-search dropdown fix (2026-05-24)

Closes scrutinizer finding **UI-2026-05-24-002** (CRITICAL): typing in the
header search box produced no dropdown, no spinner, no empty-state, no
results. Researchers could not find a known DataObject by name from the
header.

## What I changed

### New code
- `frontend/composables/context/useGlobalSearch.ts` — composer that wires
  the three existing search composables, debounces (300ms default), runs
  the three kinds in parallel, aggregates loading / error / empty state,
  exposes per-kind result arrays trimmed to configurable limits, and
  cleans up its timer on unmount.
- `frontend/tests/unit/useGlobalSearch.test.ts` — 10 Vitest tests
  covering: empty / whitespace queries fire no requests, debounce
  coalescing, multi-kind population, the global (no-collectionId) scope
  on the DataObject call, empty-state, error capture, per-kind limits,
  reset, and clear-mid-search.
- `e2e/tests/header-search.spec.ts` — 4 Playwright e2e tests against
  the live deploy: known-DO match (`TR-004`), no-match empty state,
  empty-focus fires no requests, click-row navigates to detail route.

### Modified
- `frontend/components/layout/HeaderBar.vue` — replaced the
  collection-only `v-autocomplete` with a `v-text-field` + `v-menu` +
  `v-list` dropdown. Three sections with kind chips, loading hint,
  empty state ("No matches for '…'"), error state ("Search temporarily
  unavailable"), footer link to `/search`. Keyboard: Down focuses
  first result, Esc closes, Enter jumps to Advanced. Mobile-friendly
  (`min-width: 320`).
- `frontend/composables/context/useDataObjectSearch.ts` — first param
  relaxed from `collectionId: number` to `collectionId: number |
  undefined`; when undefined the scope is built without `collectionId`,
  hitting `DataObjectSearchService.java:38`'s
  "no CollectionId and no DataObjectId" branch (global search across
  every collection the user can read). Result interface gains
  `collectionId?: number` populated from the parallel
  `resultSet`/`ResultTriple` array so the dropdown can build a navigation
  route. All three existing callers (`DataObjectAutocomplete.vue`,
  `DataObjectPrefillableInput.vue`, `AddRelationshipAutocomplete.vue`)
  continue to pass a numeric `props.collectionId`, so behaviour is
  unchanged for them.
- `frontend/composables/context/useCollectionSearch.ts`,
  `useDataObjectSearch.ts`, `useContainerSearch.ts` — `startSearch()`
  now returns `Promise<void>` so a composer can observe per-kind
  rejection. Legacy fire-and-forget callers keep working.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — new row §6
  "UI-002 — Global header-search dropdown" marked shipped.

### No backend changes
Endpoints used:
- `POST /shepard/api/search` with `queryType: "Collection"` and
  `queryType: "DataObject"` (scope omitting `collectionId` ⇒ global).
- `POST /shepard/api/searchContainers` with `queryType: "BASIC"`.

All three already supported the wire shape. The dispatch on null
`collectionId` for global DataObject search was already implemented in
`DataObjectSearchService.java:38`.

## Test results

### Vitest
```
✓ tests/unit/useGlobalSearch.test.ts (10 tests) 18ms
  ✓ does not fire any request when the query is empty
  ✓ does not fire when the query is only whitespace
  ✓ debounces — typing many chars within the window fires only once per kind
  ✓ populates collections + dataobjects + containers from a single query
  ✓ DataObject search runs WITHOUT collectionId in scope (global mode)
  ✓ isEmpty is true when query is non-empty and all kinds return zero results
  ✓ captures error and sets a user-facing message when a kind errors
  ✓ respects per-kind limits
  ✓ reset() clears query + results
  ✓ clearing the query mid-search wipes results immediately

Test Files  1 passed (1)
     Tests  10 passed (10)
```

Full suite: 128 passed / 5 pre-existing failures in
`useFetchRecentCollections.test.ts` (unrelated — confirmed they fail
on `main` without my changes too).

### Playwright (live, `BASE_URL=https://shepard.nuclide.systems`)
```
✓ Header search dropdown (UI-002) › typing a known DataObject name
   produces a matching dataobject row (3.0s)
✓ Header search dropdown (UI-002) › typing a string with no matches
   shows the empty state (3.3s)
✓ Header search dropdown (UI-002) › focusing the empty input does not
   fire a search request (2.6s)
✓ Header search dropdown (UI-002) › clicking a result navigates to
   its detail route (3.1s)

4 passed (12.9s)
```

Note: `KEYCLOAK_HOST=https://shepard-auth.nuclide.systems` is required
when running against the public deploy (the helper default
`http://192.168.1.49:8082` is for in-host OIDC). This is a pre-existing
gotcha of `e2e/tests/helpers/auth.ts`, not something this PR introduces.

### Smoke
`make redeploy-frontend` ran to completion: 25/25 smoke checks green,
demo seeders (`lumen-showcase`, `home-showcase`) exit 0.

## Live URL
`https://shepard.nuclide.systems` — log in as `flo / flo-demo`, type
`TR-004` in the header search box. Dropdown opens within ~300ms, shows
matching DataObject rows under the "Data objects" section. Try `lumen`
for a multi-section dropdown (matches the LUMEN collection too). Try
`homarrxyzdoesnotexist` for the empty-state.

Evidence screenshots:
- `aidocs/agent-findings/ui-002-evidence-2026-05-24/header-search-tr004-results.png`
- `aidocs/agent-findings/ui-002-evidence-2026-05-24/header-search-empty-state.png`

## Deferred / follow-ups

1. **Advanced Search `?q=` pass-through.** The footer "Advanced search
   for '<query>' →" navigates to `/search` but the current Advanced
   Search page consumes `searchQuery=<json>`, not a plain `?q=`.
   Suggested follow-up backlog row: extend `/search/index.vue` to seed
   `jsonQuery` from `?q=…` when present (wraps `q` into
   `{"property":"name","operator":"contains","value":"<q>"}`). One small
   PR; not in this scope to keep the diff tight.
2. **Container kind icons.** Currently all container rows show a single
   `mdi-database-outline` subheader; could specialise per container type
   (file / timeseries / structured / spatial) for at-a-glance scanning.
   Low-impact, fold into a future polish pass.
3. **Recent searches / search history.** Already a backlog row in
   `aidocs/44 §6` — out of scope.
4. **e2e auth helper default.** `e2e/tests/helpers/auth.ts:3` defaults
   `KC` to a private IP; should default to
   `https://shepard-auth.nuclide.systems` (or branch on `BASE_URL`).
   Pre-existing; not blocking.

## Coordination

Per the dispatch warning, another agent (UI-003/004) was concurrently
touching `pages/me/`, `pages/admin/`, `pages/about/`,
`pages/configuration/`, and router middleware. I did not touch any of
those files. Their `SectionIndexLanding.vue`, `UnauthorizedView.vue`,
`sectionLanding.ts`, and `section-index-landing.spec.ts` are visible in
the tree but on a separate scope.

## Commit
`feat(header-search): wire dropdown with debounced multi-kind results — closes UI-002`
