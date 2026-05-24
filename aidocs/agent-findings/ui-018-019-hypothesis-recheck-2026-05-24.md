---
stage: feature-defined
last-stage-change: 2026-05-24
---

# UI Hypothesis Re-check 2026-05-24 — UI-018 + UI-019

Observation-only re-walk against live `shepard.nuclide.systems`, post BUG #139
fix (commit `5913ca20`). No source mutations; new Playwright specs added so the
verdicts hold in CI going forward.

## TL;DR

| ID     | Original hypothesis                                              | Verdict      | Why                                                                                                  |
|--------|------------------------------------------------------------------|--------------|------------------------------------------------------------------------------------------------------|
| UI-018 | N+1 fetches by lineage/prov graphs on deep MFFD DO detail        | **CLOSED**   | Both graphs batch-paginate via `useFetchAllDataObjects` (page size 200). Zero per-DO GETs observed.  |
| UI-019 | Create-DO dialog might error mid-wizard before reaching submit   | **CLEAN**    | Fill→Next→Cancel completes with 0 console errors, 0 network errors, 0 mutation calls.                |

**Bonus finding (new bug filed as UI-020):** the re-walk surfaced a
**confirmed N+1 elsewhere**: `CollectionLabJournalEntryList.vue` fires one
`GET /shepard/api/labJournalEntries?dataObjectId=N` per DataObject when a
collection is rendered. On MFFD-Dropbox (~8500 DOs) this is **8514 concurrent
requests** in a single `Promise.all`. Browser may even refuse the socket
above some N. See "Bonus finding" below.

## UI-018 — N+1 on lineage / provenance graphs (CLOSED)

### Method

Playwright spec `e2e/tests/ui-018-n-plus-1-recheck.spec.ts` walks four pages
at 1920×1080 after auth as `alice/alice-demo`:

1. `/collections/661923/dataobjects/661958` — deep MFFD DataObject detail
   (DataObjectProvGraph in collapsed accordion)
2. `/collections/42/dataobjects/51` — LUMEN TR-007 (DataObject with
   predecessor + successor relationships; DataObjectProvGraph)
3. `/collections/42` — LUMEN collection landing (CollectionLineageGraph;
   15 TR nodes)
4. `/collections/661923` — MFFD-Dropbox collection landing
   (CollectionLineageGraph; ~8500 DataObjects → capped at 150 nodes by the
   component)

For each page: open every `.v-expansion-panel-title` so the graph components
mount and their fetches fire, then capture all network requests, group by
URL "shape" (numeric IDs replaced with `:id`), and flag any shape that fires
>10× as a suspect.

### Evidence

Captures live in `aidocs/agent-findings/ui-018-019-evidence-2026-05-24/`:

| Page                | Total API reqs | Max repeat in lineage/prov shape | Verdict                          |
|---------------------|----------------|----------------------------------|----------------------------------|
| MFFD DO 661958      | 41             | 4× `/collections/:id/dataObjects` | CLOSED — paginated, not per-DO |
| LUMEN DO 51         | 39             | 4× `/collections/42/dataObjects`  | CLOSED                         |
| LUMEN coll 42       | 47             | 3× `/collections/42/dataObjects`  | CLOSED                         |
| MFFD-Dropbox 661923 | 8541           | 3× `/collections/:id/dataObjects` | CLOSED for lineage              |

### Why the graphs are clean

Both `CollectionLineageGraph.vue` and `DataObjectProvGraph.vue` share a single
data source: `composables/context/useFetchAllDataObjects.ts`. That composable
paginates with `PAGE = 200` against:

- `GET /v2/collections/{appId}/data-objects?page=X&size=200` (v2 path), or
- `GET /shepard/api/collections/{id}/dataObjects?page=X&size=200` (v1 fallback)

So a 17-DO collection = 1 list call; a 200-DO collection = 1 call; an
8000-DO collection = 40 calls. Edge-and-relationship data (`predecessorIds`,
`parentId`) ride inside the `DataObjectListItemV2` payload — there is no
per-node fetch. `DataObjectProvGraph.vue` adds **one** extra call,
`listActivities({ targetAppId, limit: 50 })`, regardless of node count.

The original ux-auditor suspicion is therefore not supported by the data.
Closing UI-018.

## UI-019 — Create-DO dialog fill+cancel (CLEAN)

### Method

Playwright spec `e2e/tests/ui-019-create-do-fillcancel.spec.ts`:

1. Auth as `alice/alice-demo`
2. Visit `/collections/42` (LUMEN)
3. Click "Add new data object" in sidebar
4. Dialog opens directly as the blank form (LUMEN has no collection-template
   seed — `CreateDataObjectDialog.vue` falls through to `mode = "form"`)
5. Fill name field with `UI-019-test-do-DELETE-ME-<ts>`
6. Click **Next** → advances to step 2 (Attributes)
7. Verify both **Cancel** and **Create** buttons are visible
8. Click **Cancel** (NEVER Create)
9. Assert dialog closed; reload page; assert no DataObject with the test
   name exists in the rendered sidebar tree

The test records every console error, every 4xx/5xx response, and every
mutation-shaped request (`POST|PUT|PATCH|DELETE` hitting `dataObjects` URL
families). Errors are tagged with a phase label so we can separate the
ones caused by the dialog from pre-existing page-load noise.

### Result

| Metric                                                  | Observed | Expected |
|---------------------------------------------------------|----------|----------|
| Mutation calls during fill+cancel                       | **0**    | 0        |
| New DataObject visible after reload                     | **false**| false    |
| Console errors raised **during the dialog phases**      | **0**    | 0        |
| Network 4xx/5xx raised **during the dialog phases**     | **0**    | 0        |
| Console errors raised on page-load (pre-existing)       | 6        | — (out of scope; tracked as UI-005, UI-010) |
| Network 4xx/5xx on page-load (pre-existing)             | 2        | — (UI-005 `watches/me` 404 etc.)            |

The dialog is clean. The stepper transitions cleanly from step 1 to step 2.
Cancel closes the dialog without firing any persistence calls. No console
or network noise is introduced by opening/filling/cancelling the dialog.

Closing UI-019.

## Bonus finding (filed as UI-020) — labJournal N+1

While running UI-018, the per-shape network histogram on the MFFD-Dropbox
collection landing reported:

```
[8514×] https://shepard-api.nuclide.systems/shepard/api/labJournalEntries
   [4×] https://shepard-api.nuclide.systems/shepard/api/collections/:id/roles
   [3×] https://shepard-api.nuclide.systems/shepard/api/collections/:id
   [3×] https://shepard-api.nuclide.systems/shepard/api/collections/:id/dataObjects
   [2×] https://shepard.nuclide.systems/api/auth/session
```

`labJournalEntries` is called **8514 times** on a single collection landing
because `frontend/components/context/lab-journal/CollectionLabJournalEntryList.vue`
maps every DataObject id to its own `getLabJournalsByCollection({ dataObjectId })`
call inside a single `Promise.all` (lines 21–33). LUMEN (17 DOs) does this
17 times — undetectable. MFFD-Dropbox (~8500 DOs) does it 8514 times.

This is a real, production-scale performance bug that the original UI-018
walk would have caught if the lineage/prov hypothesis hadn't dominated the
analysis. Filed as a new backlog row **UI-020** with a concrete fix shape
(backend bulk endpoint + flip the frontend caller). The browser likely
chokes well before all 8514 sockets resolve.

**Side-effect of UI-020 in console:** the same MFFD-Dropbox landing
generates **8311 console errors** (one per failed lab-journal request, as
many resolve with errors when the request flood saturates). LUMEN with 17
DOs generates 0 such errors. The console error count scales linearly with
DataObject count — strong corroboration that the lab-journal call is the
source. Fixing UI-020 should drop the console error count on collection
landings by 4 orders of magnitude.

## Files committed

- `e2e/tests/ui-018-n-plus-1-recheck.spec.ts` — new
- `e2e/tests/ui-019-create-do-fillcancel.spec.ts` — new
- `aidocs/agent-findings/ui-018-019-hypothesis-recheck-2026-05-24.md` — this report
- `aidocs/agent-findings/ui-018-019-evidence-2026-05-24/` — screenshots +
  per-page network-shape JSON
- `aidocs/16-dispatcher-backlog.md` — UI-018 + UI-019 flipped to ✅ CLOSED;
  new UI-020 row added (queued, M, severity = performance)

## Open follow-ups

- UI-020 (new): the labJournal N+1. Need a backend bulk endpoint + a
  one-line frontend swap. M-effort.
- UI-005 + UI-010 (pre-existing): the page-load console/network noise that
  shows up in UI-019's "all errors" tally but is unrelated to the dialog.
  Those rows already exist in the backlog; no action here.
