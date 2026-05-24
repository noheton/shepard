---
stage: deployed
last-stage-change: 2026-05-24
---

# UX Pattern D — count badges on DataObject reference panels

**Date:** 2026-05-24
**Worktree:** `agent-a1f2dfbde9d405b90`
**Closes:** UX Pattern D (UX Scrutinizer flag — single Pareto-optimal change for DO detail)
**Backlog row:** `UI-022` (added below in `aidocs/16`)

## What I found

The DataObject detail page (`pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue`)
mounts six v-expansion-panels plus two always-visible sections. Four panel titles
already carried `(N)` low-emphasis count badges (Attributes, Lab Journal, Data
References, Relationships) via the existing `ExpansionPanelItem.vue` convention
(`text-h5 text-low-emphasis` `(N)` span, rendered whenever `count` is defined
including 0).

Missing badges:

| Surface | Type | Count source | Was missing |
|---|---|---|---|
| Jupyter Notebooks panel title | `<ExpansionPanelItem>` | `notebooks.length` from `useFetchNotebooks` | yes |
| Semantic Annotations section header | always-visible `<section>` | `annotations.length` via existing `@annotations` emit | yes |
| Git References inner `<h5>` | section header inside Data References panel | `gitReferences.length` from `useFetchGitReferences` | yes |
| Video References inner `<h5>` | section header inside Data References panel | `references.length` from `useFetchVideoStreamReferences` | yes |

Provenance was intentionally skipped — counts there would require an extra
`/v2/provenance/activities` round-trip not currently fetched on the detail
page, and Provenance was not on the user's list.

## Decision: zero-handling

Followed the existing codebase convention (`ExpansionPanelItem.vue:21–25`):
**show `(N)` in `text-low-emphasis` colour even when N=0**. Rationale:

1. Consistency — the four pre-existing panel-title badges already render `(0)`
   that way (e.g. Attributes on a freshly-created DataObject).
2. Affordance — `(0)` is a strong "this section is empty" signal that
   actively saves the user a click. Hide-when-zero forces the user to
   re-check whether the section just hasn't loaded yet.
3. The user's hint ("I'd suggest hide when 0, but defer to existing patterns")
   was an explicit deferral to convention. Convention says show.

## What changed

Per-file edits (all frontend-only):

1. **`frontend/components/context/lab-journal/DataObjectNotebooksPane.vue`** —
   added `defineEmits(["numberOfEntriesChanged"])` + `watch(notebooks, list =>
   emit(..., list?.length ?? 0), { immediate: true })`. Mirrors the
   `DataObjectLabJournalEntryList` pattern (emits on fetch resolution + on
   any subsequent change).

2. **`frontend/components/context/dataobject/GitReferencesPane.vue`** —
   appended a `<span class="text-low-emphasis ml-1" data-testid="git-references-count">({{ gitReferences.length }})</span>`
   to the inner `<h5>Git References</h5>` header.

3. **`frontend/components/context/dataobject/VideoStreamReferencesPane.vue`** —
   same shape, `video-references-count` testid, sourced from
   `references.length`.

4. **`frontend/pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue`** —
   - Added `useCounter()` instance for Jupyter notebooks
     (`numberOfNotebookEntries` + `onNotebookCountChanged`).
   - Wired `<DataObjectNotebooksPane @number-of-entries-changed="onNotebookCountChanged">`
     and `:count="numberOfNotebookEntries"` on the matching `<ExpansionPanelItem>`.
   - Added `numberOfSemanticAnnotations = ref<number | undefined>(undefined)`
     + `onAnnotationsLoaded(annotations)` handler.
   - Wired the always-visible Semantic Annotations section header with a
     `<span v-if="...defined" data-testid="semantic-annotations-count">`
     badge + `<SemanticAnnotationList @annotations="onAnnotationsLoaded">`
     listener (the emit already existed in the component, just unused on
     this page).

## Tests

**Playwright e2e:** `e2e/tests/ux-pattern-d-count-badges.spec.ts`
(2 tests, both passing against `https://shepard.nuclide.systems` after the
worktree deploy).

Test 1 — `LUMEN TR-007 detail page shows count badges on every reference panel`:
- Loads `/collections/42/dataobjects/51` (TR-007 — has annotations + 3 data
  references).
- Asserts at least one panel title contains `(N>0)` (regression guard for
  the four pre-existing panel counts).
- Asserts `getByTestId("semantic-annotations-count")` is visible after the
  annotations fetch resolves (waits for first chip first).
- Expands the Data References panel if collapsed, then asserts the
  `git-references-count` and `video-references-count` badges are visible
  with `(\d+)` shape.
- Expands the Jupyter Notebooks panel and asserts the title gains the
  `(N)` suffix (Vuetify expansion-panels are lazy; the inner pane mounts
  on first expansion and emits the count then).

Test 2 — `zero-handling — low-emphasis (0) renders for empty sections`:
- LUMEN TR-007 has 0 git refs + 0 video refs (synthetic seed never wires
  git/video to LUMEN DOs).
- Asserts the git-references-count badge IS PRESENT with `(\d+)` shape
  (rather than hidden) and carries the `text-low-emphasis` class.
- Locks in the codebase convention so a future "hide when 0" change
  fails noisily.

**Vitest:** skipped — no factored helper introduced; all changes are
template-wiring of existing composables. Existing
`DataObjectLabJournalEntryList` similarly has no Vitest coverage; the
Playwright e2e is the appropriate test layer.

### Test run

```
$ BASE_URL=https://shepard.nuclide.systems npx playwright test \
    tests/ux-pattern-d-count-badges.spec.ts
  ✓ 1 [chromium] LUMEN TR-007 detail page shows count badges on every reference panel (12.8s)
  ✓ 2 [chromium] zero-handling — low-emphasis (0) renders for empty sections (19.9s)
  2 passed (33.8s)
```

### Smoke

```
$ make smoke
  PASS: 25    FAIL: 0
```

## Live URL

Verify in browser:
- LUMEN TR-007 (populated): https://shepard.nuclide.systems/collections/42/dataobjects/51
- Look for: "Semantic Annotations (6)", "Attributes (10)", "Data References (3)",
  "Relationships (2)" on load. Expand "Data References" → see "Git References (0)"
  and "Video References (0)" inner headers. Expand "Jupyter Notebooks" → see
  "Jupyter Notebooks (0)" after the fetch resolves.

## Real-world impact

A DLR researcher landing on a DataObject detail page no longer has to expand
each accordion to discover whether it's worth opening. The seven badges
(Attributes / Lab Journal / Data References / Relationships /
Semantic Annotations / Jupyter Notebooks / Git References / Video References)
turn the page from "fish in dark" into "scannable at a glance" — matching
the UX Scrutinizer's Pareto-optimal flag.

This is particularly meaningful for the MFFD ingest pipeline where ~8500
DOs vary widely in their annotation/reference density. An auditor walking
the AFP→NDT→Rework chain can now see at a glance which steps carry rich
sensor metadata vs. which are empty scaffolding (recall:
`project_sd_retrieval_path.md` — empty/scaffolding DOs are valid, not
anomalous; counts make their emptiness scannable rather than ambiguous).

## Gaps & blockers

- **Lab Journal pre-existing gap**: The Lab Journal panel title shows no
  `(0)` badge on DOs with no entries because `DataObjectLabJournalEntryList`
  only emits its count after the fetch resolves AND the panel has been
  expanded once (Vuetify lazy mounting). My Jupyter Notebooks count has the
  same shape — first-expansion-then-count. To make these counts appear
  on load without expansion, the fetch would need to be lifted to the page
  level (next-step refactor; out of scope for this PR).
- **Provenance panel** has no count (would require pre-fetching
  `/v2/provenance/activities`; out of scope).
- **Per-kind breakdown in Data References title** (`(12 file, 3 ts, 1 sd)`)
  was intentionally not added — the panel already has per-kind filter chips
  with counts inside when expanded, and a verbose title was deemed noisier
  than the aggregate `(N)`.

## What surprised me

1. **Vuetify lazy expansion-panel mount** invalidated my initial assumption
   that all panel-title counts would update on page load. They only update
   on first expansion — and even after that, the Lab Journal panel on
   LUMEN TR-007 shows no count badge because the entries fetch resolves to
   an empty array, BUT the visible YAML page snapshot can race the emit
   propagation. This explains why the pre-existing `Lab Journal` panel
   title sometimes shows no badge in screenshots.

2. **Worktree docker build cache subtlety** — my first `make redeploy-frontend`
   appeared to succeed but the running container shipped pre-edit JS. A
   `rm -rf .output .nuxt && npm run build && docker build` re-run produced
   the expected image. Likely root cause: an earlier failed build attempt
   left a stale `.output` that the second build didn't fully overwrite.
   Operator lesson: when verifying a frontend deploy, `docker exec
   <frontend-container> grep -l <new-testid> /app/server/chunks/build/*.mjs`
   is the authoritative check, not just smoke-pass.

3. **The `SemanticAnnotationList` component already emitted `@annotations`**
   with the full annotation array — the page just never listened. Zero new
   plumbing needed on the component side, which made the annotations badge
   the cheapest of the four to add.

## Backlog flip

Added new row `UI-022` to `aidocs/16-dispatcher-backlog.md` marked
`✅ shipped`.
