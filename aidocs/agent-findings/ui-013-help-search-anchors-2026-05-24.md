---
stage: deployed
last-stage-change: 2026-05-24
---

# UI-2026-05-24-013 — in-page help search + per-heading anchors (CLOSED)

Closes UI-2026-05-24-013 (MINOR) from
`aidocs/agent-findings/ui-scrutinizer-2026-05-24.md`. The `/help` page was
a long single-column scroll with a tall left nav, no search-in-docs box,
and no per-section anchors — researchers had to scroll-and-skim instead
of jumping. This PR adds both.

## What I shipped

Two layers, both surgical:

1. **Heading anchors** in `frontend/utils/helpMarkdown.ts`
   - New `slugify(text)` + collision-aware `SlugRegistry` class.
   - Custom `marked.Renderer.heading()` that decorates every H2/H3/H4 with
     `id="<slug>"` + a `<a class="doc-heading-anchor" href="#slug">#</a>`
     prefix. H1 is left as the bare page title (no anchor — it's the
     headline, not a navigable section).
   - Slug registry is reset per `renderDocMarkdown()` call so IDs don't
     bleed between pages.

2. **In-page search** in `frontend/components/context/help/HelpFrame.vue`
   - Adds a Vuetify `v-text-field` at the top of the content area with
     placeholder `Search in this page (N sections)` + a clearable icon.
   - Markdown post-processor `wrapSectionsForSearch()` wraps each
     H2-introduced run in `<section class="doc-section"
     data-search-text="…">`. The pre-H2 intro stays unwrapped (always
     visible).
   - Filter is a DOM toggle, not a re-render: a `watch(searchQuery)` walks
     `.doc-section` nodes, comparing the lowercased query against the
     pre-computed `dataset.searchText`, and toggles a
     `doc-section--hidden` class. No marked re-parse, no jank on
     keystroke. Status line shows `N of M sections match` (and an empty
     state with a clear-search link when the query has no hits).

3. **Hash-scroll on async content** (the silent footgun the advisor
   flagged)
   - Cold-load `/help?page=X#slug` arrives with the markdown still
     fetching, so the browser's native hash scroll runs against an empty
     content area. After `loadPage()` resolves, we `nextTick()` then
     `document.getElementById(hash)?.scrollIntoView({ behavior: "smooth"
     })`. Same handler also re-runs on `route.hash` changes.
   - Anchor clicks are intercepted on `.doc-content` to update the URL
     hash via `router.replace()` (no full route reload) and smooth-scroll
     to the target.

## Search shape

**Client-side, in-page only** (per the task's "cheapest" recommendation):
filters the currently rendered page. No build-time index, no runtime
backend dependency, no new top-level deps.

The decision factors:

- A Vite-plugin precomputed cross-page index is plausibly >2h once
  wired (indexer + UI for cross-page nav semantics + display of
  match-snippets). The task allowed the cheap option, so I took it.
- Section-level collapse (no inline `<mark>` highlighting) sidesteps the
  hazard of corrupting hljs-rendered `<pre>` blocks.
- `data-search-text` is precomputed once at render time and lives on the
  DOM element, so each keystroke is a cheap `dataset.searchText.includes(q)`
  check on ~10–30 nodes — fast even on the densest doc page
  (`reference/api.md` has ~9 H2s).

## What I deliberately did NOT do

- **No `markdown-it-anchor`.** The project uses `marked`, not
  `markdown-it`. Overriding `renderer.heading` is the native idiom for
  this stack — saves a (small but real) dep and one more `package.json`
  drift surface.
- **No per-page heading TOC rail.** The task mentioned "Update the
  in-page TOC (left nav) to point at these anchors", but the current
  `HelpSidebar.vue` is a *page index*, not a heading TOC. Adding a
  per-page H2/H3 outline rail is a separate component and arguably out
  of scope for a 2-hour MINOR fix. The minimal interpretation —
  anchor links work, URL updates with `#slug`, browser handles scrolling
  — is shipped. **Follow-up candidate**: per-page right-side ToC rail.
- **No reading-time estimate.** The task said skip if the renderer
  doesn't expose word count cheaply — and `marked` doesn't. Skipped.
- **No cross-page search index.** Same reason: >2h to wire well; the
  in-page filter covers the immediate friction.

## Test results

### Unit (Vitest) — 44/44 pass

`frontend/tests/unit/helpMarkdown.test.ts` adds 17 new test cases:

- 6 `slugify` cases (lowercase, punctuation, collapse, trim, fallback,
  real-doc shapes)
- 4 `SlugRegistry` cases (first-occurrence, collisions, reset)
- 5 `renderDocMarkdown` heading-anchor cases (H2/H3 anchors, H1
  exemption, collision suffix, cross-call reset)
- 4 `wrapSectionsForSearch` cases (wrap, no-H2 passthrough,
  lowercase-attr, quote-escape, end-to-end integration)

Full backward compatibility: all 27 pre-existing helpMarkdown tests
still pass.

### Playwright (e2e) — 3/3 pass

`e2e/tests/ui-013-help-search-anchors.spec.ts`:

1. Typing `collection` on `/help?page=user-guide` filters sections:
   `> 0` visible, `> 0` hidden, `vis+hidden == total`. Clearing the
   input restores all sections.
2. Clicking the first heading anchor updates the URL hash to `#<slug>`
   and the target heading remains visible.
3. Cold-loading `/help?page=user-guide#<slug>` scrolls to the heading
   after the async fetch resolves.

```text
Running 3 tests using 1 worker
  ✓  1 search box filters sections on the current help page (2.5s)
  ✓  2 clicking a heading anchor updates the URL hash and scrolls (2.2s)
  ✓  3 loading /help?page=…#slug scrolls to the heading on cold load (2.3s)
  3 passed (8.3s)
```

### Deploy + smoke

`make redeploy-frontend` ran `build-frontend` (47s client + 48s SSR),
`image-frontend`, `deploy-frontend`, then 25/25 smoke checks PASS.

## Live URL to test

<https://shepard.nuclide.systems/help> — sign in, pick any doc page with
multiple H2 sections (User guide, Architecture, reference/api), type a
keyword in the search box at the top of the content area, hover any
heading to see the `#` anchor, click it to see the URL update.

## Files touched

- `frontend/utils/helpMarkdown.ts` — `slugify`, `SlugRegistry`,
  `renderer.heading` override, `wrapSectionsForSearch`, modified
  `renderDocMarkdown` to reset registry + post-process sections.
- `frontend/components/context/help/HelpFrame.vue` — search box +
  status line, content-root template ref, `applySearchFilter`,
  `scrollToHashIfPresent`, `onContentClick` (anchor interception),
  scoped CSS for `.help-search-bar`, unscoped CSS for `.doc-heading`,
  `.doc-heading-anchor`, `.doc-section--hidden`.
- `frontend/tests/unit/helpMarkdown.test.ts` — 17 new unit tests.
- `e2e/tests/ui-013-help-search-anchors.spec.ts` — 3 new Playwright e2e
  tests.

## Coordination

Stayed in lane: did not touch `pages/collections/**`, the home page,
section landing pages, `useCollectionWatch`, or any plugin docs. The
help docs themselves under `frontend/public/docs/help/**.md` are
unchanged (anchor IDs are generated at render time from the heading
text — no doc edits required).

## Follow-up candidates (not in this PR)

1. **Per-page H2/H3 TOC rail** on the right side of the content area
   (sticky), populated from `.doc-heading` ids after render. Would close
   the original task's full intent.
2. **Cross-page search index** built at `npm run build` time, indexing
   all `frontend/public/docs/**.md` into a single JSON blob.
   `flexsearch` or a precomputed inverted index would be the shape.
3. **Inline `<mark>` highlighting** of the matched substring within
   visible sections — requires skipping nodes inside `<pre>` to avoid
   corrupting hljs output.
