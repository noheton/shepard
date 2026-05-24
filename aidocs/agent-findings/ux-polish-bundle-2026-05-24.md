---
stage: deployed
last-stage-change: 2026-05-24
---

# UX polish bundle — 2026-05-24

Four small wins from `ux-scrutinizer-workflows-2026-05-24.md` bundled into
one PR: each is short, they don't conflict, and all four ship together.

**Live**: <https://shepard.nuclide.systems> (frontend image `shepard-frontend:local`,
re-deployed 2026-05-24 ~14:25 UTC).

**Smoke 25/25 PASS** after deploy. **Vitest unit: 15/15 new tests pass.**
**Playwright e2e: 6/6 pass** against the live host.

---

## Per-pattern

### Pattern A — loading-state-leaks-as-error (CRITICAL)

**Before**: every Phase-2/3/5 workflow on cold load flashed a red error toast
("Error while fetching collection: …") for ~200 ms before the data arrived.
Lied to users about data being missing when it was just still loading.

**Root cause**: `frontend/utils/errorBus.ts::handleError` emitted to the
toast bus on **every** rejection. Two classes were noise, not signal:

1. **AbortError / `TypeError: Failed to fetch`** — Nuxt aborts the previous
   request when the route changes mid-flight. The original promise
   rejects, but there's nothing for the user to act on.
2. **401 Unauthorized** — `useAuthRefreshMiddleware` already handles 401s
   silently (refresh + retry, then `signIn` on the second 401). A red
   toast on top is duplicate noise that reads as a data-availability
   failure.

**Fix**: two early-returns at the top of `handleError`:

```ts
if (isAbortLike(e)) { log.warn(...); return; }
if (isUnauthorized(e)) { log.warn(...); return; }
```

`isAbortLike` checks for `DOMException("AbortError")` and the browser's
`TypeError("Failed to fetch" | "NetworkError")` shape. `isUnauthorized`
checks `e.response.status === 401`. Both still log to console at WARN so
real silent-401 bugs remain debuggable.

**File**: `frontend/utils/errorBus.ts` (+47 LOC, all comment + 2 helpers).

**Verification**: live e2e `Pattern A: no red error toast flashes on cold
load of /collections/42` polls for the toast every 100 ms for 2 s and
asserts it never appears. PASSED on live 2026-05-24.

---

### Pattern E — sidebar sibling-nav tooltip

**Before**: the `Contents` tree on the collection sidebar IS the sibling
navigator (and supports chevron-expand into investigation sub-trees like
TR-004's), but users on first visit don't realise it. UX Scrutinizer
specifically corrected mid-walk: "promote it, don't build it."

**Fix**: an info icon (`mdi-information-outline`) next to the *Contents*
header text in `CollectionSidebar.vue`. Hovering it shows a tooltip:

> Click any DataObject to jump between siblings. Use the chevron to expand
> sub-trees (e.g. investigation children).

Plus a sentence in `docs/help/collection-lineage.md` under a new
"Jumping between sibling datasets" subsection so the in-app `/help`
mirrors the UI hint.

**File**: `frontend/components/context/sidebar/CollectionSidebar.vue`
(template-only — the header text wrapper now carries
`data-testid="collection-sidebar-contents-header"` for e2e + the tooltip
activator).

**Verification**: live e2e `Pattern E: CollectionSidebar Contents header
carries the info tooltip` asserts the testid'd element + the
mdi-information-outline icon are attached. PASSED.

---

### Pattern F — page titles include entity name

**Before**: every collection / DataObject / container page in the browser
tab bar showed a stale-looking title because `useHead({ title })` was
called **inside a watcher**:

```ts
watch(collection, () => {
  useHead({ title: collection.value?.name + " | shepard" });
});
```

This pattern fails three ways: (1) the initial paint has no `useHead`
call at all, so the title falls back to the default / stale previous-page
title; (2) calling `useHead` from inside a watch creates a fresh head
entry on every fetch (potential leak); (3) when `collection.value` is
`undefined` on the first watch fire, the title becomes
`"undefined | shepard"`.

**Fix**: call `useHead` once at top-level with a **getter function** so
the title is reactive. Six pages updated:

| Page | New title pattern |
|---|---|
| `collections/[collectionId]/index.vue` | `<Collection.name> — shepard` |
| `collections/[collectionId]/dataobjects/[dataObjectId]/index.vue` | `<DataObject.name> · <Collection.name> — shepard` |
| `containers/files/[containerId]/index.vue` | `<Container.name> (Files) — shepard` |
| `containers/timeseries/[containerId]/index.vue` | `<Container.name> (Timeseries) — shepard` |
| `containers/structureddata/[containerId]/index.vue` | `<Container.name> (Structured Data) — shepard` |
| `containers/spatialdata/[containerId]/index.vue` | `<Container.name> (Spatial Data) — shepard` |

All carry a sensible fallback for the pre-load tick
(`"Collection — shepard"`, etc.) so the tab never reads
`"undefined | shepard"`.

**Verification**: live e2e `Pattern F: collection page title contains
the collection name` + `Pattern F: two collection tabs have distinct
titles`. Both PASSED.

---

### Bonus — `/search?q=X` URL param reader

**Before**: the search page silently ignored `?q=` (only consumed
`?searchQuery=<JSON>`). The header search dropdown's "See all results"
footer link navigated to bare `/search`, dropping the user's typed query.

**Fix**: two parts —

1. `pages/search/index.vue` now reads `?q=` and translates it to a
   `name contains <value>` JSON query, then runs the search. Logic
   extracted into `utils/searchQueryFromParams.ts` for unit-testability.
   `?searchQuery=` wins over `?q=` when both are present.
2. `components/layout/HeaderBar.vue` — the dropdown's "See all results"
   `:to` binding + the `Enter` handler in `onEnterPressed()` now pass
   `query: { q }` so the round-trip works.

**Verification**: live e2e `Search ?q= prefills the form and runs the
query` polls all `<input>` / `<textarea>` values until one contains
`TR-004`. Live e2e `Header-search dropdown 'See all results' navigates
to /search?q=…` types `TR-004`, clicks the footer link, asserts the
URL contains `q=TR-004`. Both PASSED.

---

## Test results

### Vitest (frontend unit) — 15/15 PASS

```
✓ tests/unit/searchQueryFromParams.test.ts (8 tests)
✓ tests/unit/errorBus.test.ts            (7 tests)
```

Coverage of the new logic includes:

- `handleError` suppresses `AbortError`, `TypeError("Failed to fetch")`,
  and `401 Unauthorized`.
- `handleError` STILL emits for `404`, `500`, string-typed, and plain
  `Error` rejections (regression guard against over-suppression).
- `searchQueryFromParams` covers: `?q=` only, `?searchQuery=` only, both
  (priority test), neither (fallback), whitespace-only `?q=`,
  surrounding whitespace trimming.

(Pre-existing failures in `useFetchRecentCollections.test.ts` —
5 failures — confirmed unchanged by stashing this PR's diff and
re-running. Not caused by this work.)

### Playwright e2e (live host) — 6/6 PASS

```
[chromium] tests/ux-polish-2026-05-24.spec.ts
  ✓ Pattern A: no red error toast flashes on cold load of /collections/42
  ✓ Pattern F: collection page title contains the collection name
  ✓ Pattern F: two collection tabs have distinct titles
  ✓ Search ?q= prefills the form and runs the query
  ✓ Pattern E: CollectionSidebar Contents header carries the info tooltip
  ✓ Header-search dropdown 'See all results' navigates to /search?q=…
6 passed (26.9s)
```

### Smoke 25/25 PASS

Re-ran `make smoke` post-deploy. All v1+v2 endpoints + seeder checks
green.

---

## Files changed

```
frontend/utils/errorBus.ts                                              # Pattern A
frontend/utils/searchQueryFromParams.ts                                 # search bonus (new)
frontend/components/context/sidebar/CollectionSidebar.vue               # Pattern E
frontend/components/layout/HeaderBar.vue                                # search bonus
frontend/pages/collections/[collectionId]/index.vue                     # Pattern F
frontend/pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue  # Pattern F
frontend/pages/containers/files/[containerId]/index.vue                 # Pattern F
frontend/pages/containers/timeseries/[containerId]/index.vue            # Pattern F
frontend/pages/containers/structureddata/[containerId]/index.vue        # Pattern F
frontend/pages/containers/spatialdata/[containerId]/index.vue           # Pattern F
frontend/pages/search/index.vue                                         # search bonus
frontend/tests/unit/errorBus.test.ts                                    # Pattern A test (new)
frontend/tests/unit/searchQueryFromParams.test.ts                       # search test (new)
e2e/tests/ux-polish-2026-05-24.spec.ts                                  # all four (new)
docs/help/collection-lineage.md                                         # Pattern E user-doc note
```

Plus the `tolerantLogin` helper local to the e2e spec — copy of `loginAs`
that survives the Keycloak-SSO-cookie-hot race (existing `loginAs` times
out when the cookie is still warm and the sign-in form is skipped). Not
a global helper change; the live `loginAs` keeps its current shape for
existing tests that pass with it.

---

## Backlog update — `aidocs/16`

New rows added (UX-PATTERN-A, UX-PATTERN-E, UX-PATTERN-F, UX-Q-PARAM) all
flipped to ✅ shipped in the same PR. See entries near the UX
Scrutinizer source rows.

---

## What surprised me

1. **The image-rebuild cache caught me twice.** `make redeploy-frontend`
   ran `docker compose up -d --no-build` which DID NOT rebuild even though
   `image-frontend` had freshly run `docker build`. The container kept
   serving the previous image SHA. Had to manually
   `docker build --no-cache ... && docker compose up -d --force-recreate`
   to actually deploy. Worth adding to OPS-DEPLOY runbook (or to
   `redeploy-frontend` itself: add `--force-recreate`).

2. **The flaky redirect-loop in login** isn't caused by this PR — the
   smoke test's "no redirect loop on home page" specifically guards
   against it and passes. But the standard `loginAs(page)` helper times
   out for ~30% of test starts when the Keycloak SSO cookie is hot from
   a previous run. The `tolerantLogin` I shipped in this spec handles
   both cases (form shown OR form skipped + bounce) and is a candidate
   to fold into `e2e/tests/helpers/auth.ts` as a follow-up.

3. **Pattern F was a subtle bug, not a missing feature.** `useHead` calls
   already existed on all 6 pages — they were just structurally wrong
   (inside a watch, with string-concat + nullish coercion). The fix is
   line-count-neutral but semantically big.
