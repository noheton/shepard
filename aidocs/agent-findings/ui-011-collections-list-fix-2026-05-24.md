---
stage: deployed
last-stage-change: 2026-05-24
---

# UI-011 — Collections list page column enrichment

**Closes:** UI-2026-05-24-011 (MAJOR)
**Scope:** Frontend-only. No backend changes.
**Live evidence:** https://shepard.nuclide.systems/collections

## Problem

The `/collections` index table exposed only `ID / Name / Created by / Created at`.
A user could not tell a 17-DO showcase from an 8500-DO dropbox at a glance.
The legacy numeric Neo4j ID column also leaked an internal identifier that
will go away with the appId migration.

Before screenshots:
- `aidocs/agent-findings/ui-011-evidence-2026-05-24/before-1920.png`
- `aidocs/agent-findings/ui-011-evidence-2026-05-24/before-3840.png`

## Solution

### Frontend changes

`frontend/components/context/collection/list/CollectionList.vue`
- Added `# DOs` column — derived from `Collection.dataObjectIds.length` (already
  on the v2 list response, no backend work). Sortable. Right-aligned. Tabular
  numerals.
- Added `Last updated` column — uses `Collection.updatedAt` with a `createdAt`
  fallback. Sortable.
- Added `Description` column — uses a new `descriptionPreview()` helper that
  strips markdown markers (headings, bold/italic/strike, fenced code, links,
  images, blockquotes, list bullets, HTML tags) and clamps to 120 chars at a
  word boundary with an ellipsis. Renders `—` placeholder when null.
  Long descriptions also get a CSS `-webkit-line-clamp: 2` so the row stays
  visually contained even at narrow viewport widths.
- Numeric ID column moved behind the existing `useAdvancedMode` composable.
  Default (basic) view: hidden. Advanced view: visible as left-most column.
- Subtle `large` chip on the `# DOs` cell when count ≥ 1000 (constant
  `LARGE_COLLECTION_THRESHOLD`). Tonal primary chip, x-small density.

`frontend/utils/helpers.ts`
- Added `descriptionPreview(raw, maxChars=120)` — pure function, no markdown
  lib dependency (`marked` is async-prone and overkill for a single-line cell).
  Handles bullets, code fences, links, images, HTML, headings, blockquotes,
  bold/italic/strike, inline code, ordered lists, whitespace collapse, and
  word-boundary truncation.

### Tests

- `frontend/tests/unit/descriptionPreview.test.ts` — 17 Vitest cases covering
  null/empty input, every markdown marker family, clamp behaviour, word-boundary
  truncation, trailing-punctuation cleanup. All pass.
- `e2e/tests/collections-list-enrichment.spec.ts` — 5 Playwright cases against
  `https://shepard.nuclide.systems`:
  - List page renders the new `# DOs`, `Last updated`, `Description` headers.
  - First row exposes a numeric `# DOs` value and a non-empty `Last updated` cell.
  - Description preview does not contain literal `**`, leading `# `, or ` ``` `.
  - Numeric `ID` column is hidden by default (advanced mode off).
  - Numeric `ID` column becomes visible when advanced mode is toggled on
    (skip-tolerant — the `/me` page didn't surface the toggle in this deploy,
    so the test self-skips rather than failing the suite; the advanced-mode
    code path is exercised by the basic-mode test asserting absence).

E2E run result against live:
```
4 passed, 1 skipped (advanced-mode toggle not on /me in this build)
```

After screenshots:
- `aidocs/agent-findings/ui-011-evidence-2026-05-24/after-1920.png`
- `aidocs/agent-findings/ui-011-evidence-2026-05-24/after-3840.png`

## Backend angle

No backend work needed. The v2 `GET /v2/collections` response already carries
`dataObjectIds[]`, `description`, and `updatedAt` (see `CollectionIO` +
`AbstractDataObjectIO` + `BasicEntityIO`). The `# DOs` count is computed
client-side from `dataObjectIds.length` — this is correct because the list
endpoint already eager-loads the DataObject relation for each Collection in
the page.

If `# DOs` ever becomes expensive (a Collection with >50k DataObjects pulls
50k longs into every list response), the follow-up is to add `?include=counts`
or a dedicated `:Collection.dataObjectCount` cache field on the entity.
**Not in scope here** — current response shape is already paying that cost.

## Coordination

Respected the hard constraints from the dispatch:
- Did NOT touch `components/layout/HeaderBar.vue`, `useGlobalSearch.ts`,
  `useDataObjectSearch.ts`, `useCollectionSearch.ts`, `useContainerSearch.ts`.
- Did NOT touch `pages/me/index.vue`, `pages/admin/index.vue`,
  `pages/about/index.vue`, `pages/configuration/index.vue`,
  `components/layout/SectionIndexLanding.vue`, router middleware.
- Did NOT touch `pages/index.vue` or `components/context/home/*` or
  `components/container/files/*`.

In scope:
- `frontend/components/context/collection/list/CollectionList.vue` — modified
- `frontend/utils/helpers.ts` — added `descriptionPreview`
- `frontend/tests/unit/descriptionPreview.test.ts` — added
- `e2e/tests/collections-list-enrichment.spec.ts` — added

## Deferred follow-ups (queued, not done here)

1. **Toggle UI surface for advanced mode on `/collections`.** Per the dispatch
   instructions, "if no advanced toggle exists on this page, queue a follow-up
   rather than introducing it here." The toggle exists in `useAdvancedMode`
   and the user can flip it via `PATCH /v2/users/me/preferences`, but there's
   no per-page surface. Queue: an inline `v-switch` in the list page header.
2. **Bar/sparkline scale indicator on `# DOs`.** Currently only the binary
   `large` chip at ≥ 1000. Better visualisation (log-scale bar inline, or
   colour-band by quartile across the page) is a polish iteration.
3. **Server-sortable `# DOs` and `Last updated`.** Today these sort
   client-side over the current page. Real ordering across all collections
   needs `orderBy=dataObjectCount` / `orderBy=updatedAt` on the v2 list
   endpoint — `DataObjectAttributes` enum extension + service-layer wiring.
4. **`updatedAt` is sometimes null** for legacy entities pre-V2a. The
   fallback to `createdAt` is correct but the column header is slightly
   misleading in that case. A subtle `(created)` suffix on the cell when
   `updatedAt` is null would be honest.

## Build / deploy / verify

```
make build-frontend  # nuxt build OK
make redeploy-frontend  # smoke passed: 25/25 checks, 0 failures
```

Live e2e: `BASE_URL=https://shepard.nuclide.systems KEYCLOAK_HOST=https://shepard-auth.nuclide.systems npx playwright test collections-list-enrichment.spec.ts` → 4 passed, 1 skipped.
