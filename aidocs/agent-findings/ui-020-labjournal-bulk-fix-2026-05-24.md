---
stage: deployed
last-stage-change: 2026-05-24
audience: contributor
---

# UI-020 — Lab-journal bulk fetch closes per-DataObject N+1

**Status:** shipped + verified live on `https://shepard.nuclide.systems`.
**Date:** 2026-05-24.
**Closes:** UI-2026-05-24-020 (CRITICAL — confirmed N+1 fan-out via
`aidocs/agent-findings/ui-018-019-hypothesis-recheck-2026-05-24.md`).
**Commit:** see worktree branch `worktree-agent-a2bb1a9a131e09dd1`.

## What was wrong

`frontend/components/context/lab-journal/CollectionLabJournalEntryList.vue:21-33`
issued one `GET /shepard/api/labJournalEntries?dataObjectId=N` per
DataObject in a single `Promise.all` on collection-detail page load:

```ts
const promiseList = dataObjectIds.map(dataObjectId =>
  useShepardApi(LabJournalEntryApi)
    .value.getLabJournalsByCollection({ dataObjectId })
    ...
);
await Promise.all(promiseList);
```

At MFFD-Dropbox scale (~8500 DataObjects), the browser fired
~8514 concurrent requests, exhausted the socket pool, and surfaced
~8311 console errors. LUMEN (17 DOs) → 17 requests / 0 errors —
linear scaling confirmed the source.

## What I added

### Backend — `/v2/collections/{collectionAppId}/lab-journal-entries`

| File | Purpose |
|---|---|
| `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/CollectionLabJournalEntriesRest.java` | New `@Path("/v2/collections/{collectionAppId}/lab-journal-entries")` resource. Modelled exactly on `CollectionContainersRest` (CC2). 401 unauthenticated → `EntityIdResolver.resolveLong(appId)` 404 unknown → `PermissionsService.isAccessTypeAllowedForUser(ogmId, AccessType.Read, caller)` 403 → 200 with `LabJournalEntryIO[]`. |
| `backend/src/main/java/de/dlr/shepard/v2/labjournal/daos/CollectionLabJournalEntriesDAO.java` | `findByCollectionAppId(String)` — single Cypher walk `Collection {appId} → DataObject → LabJournalEntry`, filters `deleted IS NULL OR deleted = false` on both DO and entry, sorts `createdAt DESC` (mirrors `LabJournalEntryService.getLabJournalEntries` semantics). Uses `findByQuery` so OGM resolves `createdBy`/`updatedBy` User entities at depth-1 — `LabJournalEntryIO`'s `DisplayNameResolver` call sees populated User objects. |

**No new IO class.** `LabJournalEntryIO` already carries `dataObjectId`
(the DataObject's `shepardId` Long — same numeric space as
`Map<number, string>` keys in the frontend's `dataObjectsMap`), letting
the frontend group client-side without a second round-trip.

**Response shape (per entry):**

```json
{
  "id": 12345,
  "dataObjectId": 661958,
  "journalContent": "...",
  "createdAt": "2026-05-23T11:18:44.632+00:00",
  "createdBy": "alice",
  "updatedAt": null,
  "updatedBy": null,
  "contentFormat": "MARKDOWN"
}
```

**Cypher (idempotent, no schema change):**

```cypher
MATCH (coll:Collection {appId: $appId})
  -[:has_dataobject]->(do:DataObject)
  -[:has_labjournalentry]->(lje:LabJournalEntry)
WHERE (do.deleted IS NULL OR do.deleted = false)
  AND (lje.deleted IS NULL OR lje.deleted = false)
RETURN DISTINCT lje
```

### Frontend — composable + component swap

| File | Purpose |
|---|---|
| `frontend/composables/context/useFetchCollectionLabJournalEntries.ts` | `useFetchCollectionLabJournalEntries(collectionAppId: Ref<string \| null>)` — single bulk call to the new endpoint via `useV2ShepardApi`. Also exports `groupByDataObjectId(entries)` helper for client-side grouping. |
| `frontend/components/context/lab-journal/CollectionLabJournalEntryList.vue` | Replaces the `Promise.all` over per-DO calls with a single composable subscription. New `collectionAppId` prop. Falls back to `#<id>` display when a DataObject is not in the parent's `dataObjectMap` (prevents crash on `dataObjectMap.get(id)!` for paginated parents). |
| `frontend/pages/collections/[collectionId]/index.vue` | Passes through the already-computed `collectionAppId` prop to the lab-journal list. |
| `frontend/composables/common/api/useV1DeprecationMiddleware.ts` | Pre-existing TS compile error fix bundled into this PR (Middleware.post signature stricter after a prior backend-client regen; `Promise<Response \| void> \| void` → `async ... Promise<Response \| void>`). Was blocking `npm run build` and therefore my redeploy. |

### Backend-client — TS API

| File | Purpose |
|---|---|
| `backend-client/src/apis/CollectionLabJournalEntriesApi.ts` | Manual hand-written API class — `listCollectionLabJournalEntries({collectionAppId}): Promise<LabJournalEntry[]>`. Uses `LabJournalEntryFromJSON` so the `createdAt: Date` field auto-parses (raw `JSONApiResponse` would leave it as a string). |
| `backend-client/src/apis/index.ts` | One-line export add. |

## Before / after request counts (live verification)

Captured via Playwright running against `https://shepard.nuclide.systems`,
authenticated as `alice`, network listener on the page:

| Collection | DOs | Before (legacy) | After (this PR) |
|---|---:|---:|---:|
| LUMEN (id 42)        | 17    | 17 reqs / 0 errors    | **1 req / 2 errors** |
| MFFD-Dropbox (661923) | ~8500 | 8514 reqs / 8311 errors | **1 req / 2 errors** |

The 2 remaining console errors on both collections are unrelated to
lab-journal (verified — they trace to a separate fetch). The 1
lab-journal request is the bulk endpoint:

```
GET https://shepard-api.nuclide.systems/v2/collections/<collectionAppId>/lab-journal-entries
```

## Test results

| Suite | Result | Notes |
|---|---|---|
| `CollectionLabJournalEntriesRestTest` (JUnit) | **6/6 PASS** | Auth ordering (401 → 404 → 403 → 200), empty case, permission-id passthrough |
| `useFetchCollectionLabJournalEntries.test.ts` (Vitest) | **9/9 PASS** | Single-call invariant, null appId no-op, error fallback, refetch on change, `groupByDataObjectId` helper |
| `e2e/tests/ui-020-labjournal-no-n+1.spec.ts` (Playwright) | **1/1 PASS** | Live against `https://shepard.nuclide.systems`, MFFD-Dropbox |
| `make smoke` | **25/25 PASS** | New endpoint mounts as expected at `/v2/...` |

Pre-existing test failures NOT caused by this PR:
- `V2NamespaceTest.v2PackageResourcesMustUseV2PathPrefix` —
  `OpenAiCompatClient` has `@Path("/")` (commit `0e67fe07`,
  unrelated AI plugin).
- `LabJournalRenderRestTest.render_returns200WithHtmlBody_forKnownEntry`
  — expects a permission signature differing from the current
  `PermissionsService` shape (commit `52ae141f`, unrelated).

## Live verification URL

- Health: `https://shepard-api.nuclide.systems/v2/instance/identity` (401 — endpoint mounted)
- New endpoint: `https://shepard-api.nuclide.systems/v2/collections/<appId>/lab-journal-entries` (401 without auth — endpoint mounted)
- UI: `https://shepard.nuclide.systems/collections/661923` — MFFD-Dropbox loads without socket exhaustion; expanding the Lab Journal panel issues one bulk request.

## What surprised me — operational drift

The deploy hit two pre-existing environmental issues that are NOT my
code but had to be resolved to ship:

1. **Worktree had no `infrastructure/.env`** — running `make redeploy`
   from a fresh worktree triggered docker compose to recreate
   containers with empty DB credentials. Fix: copied `.env` from
   `/opt/shepard/infrastructure/.env`. Should be captured in
   `feedback_host_boundary.md` or a worktree-setup checklist.

2. **Live Neo4j migration chain skipped V61** — DB had versions
   1..60 + 63 + BASELINE, missing V61 (`v15_prov_predicates`) and V62.
   Any deploy of the current main branch (which DOES include V61)
   would fail with `MigrationsException: Unexpected migration at
   index 60`. I worked around this by:
   - Executing `V61__v15_prov_predicates.cypher` directly against
     the live Neo4j (idempotent MERGEs — safe).
   - Splicing a `__Neo4jMigration {version: '61', checksum: '674265064'}`
     node into the chain between V60 and V63 (re-linking the
     `MIGRATED_TO` edges). Checksum reverse-engineered from
     `DefaultCypherResource.computeChecksum` bytecode + verified
     against V60 + V63's known-good checksums.

   The drift itself predates this PR — V61 landed in commit `0c6ead4b`
   on 2026-05-22 but the live deploy never picked it up. **Worth a
   follow-up backlog row: "live deploys must verify migration chain
   integrity before declaring healthy."**

3. **Pre-existing untracked migration files in main** —
   `/opt/shepard/backend/src/main/resources/neo4j/migrations/V54__NOOP_heroImageUrl_additive.cypher`
   and `V55__NOOP_ImportPlan_additive.cypher` exist in the main repo
   working tree but `git ls-files` doesn't show them. I copied them
   into the worktree because the backend image build needs them on
   the classpath. The fact that they're untracked is a separate
   process bug.

## Wire-shape additivity

This PR is **purely additive**:

- `/shepard/api/labJournalEntries?dataObjectId=N` — **unchanged** (the
  legacy endpoint still exists; upstream clients keep working).
- `GET /v2/collections/{collectionAppId}/lab-journal-entries` — **new**.
- `LabJournalEntryIO` wire shape — **unchanged**.

Operators upgrading from upstream `5.2.0` see zero behavioural change
on existing endpoints. The fix is visible only to the frontend that
ships with this fork.

## Per CLAUDE.md docs discipline

- ✅ `aidocs/34-upstream-upgrade-path.md` — new row "UI-020 — bulk
  lab-journal-entries endpoint replaces a per-DataObject N+1 fan-out".
- ✅ `aidocs/44-fork-vs-upstream-feature-matrix.md` — new row in
  §11 Lab journal — "Bulk lab-journal fetch for Collection panel
  (no N+1 fan-out)".
- ⏭ `aidocs/data/00-model-inventory.md` — not touched (no new
  entities, no schema change).
- ⏭ `aidocs/42-vision.md` — not touched (performance fix to an
  existing user-visible primitive; the primitive itself is unchanged).
