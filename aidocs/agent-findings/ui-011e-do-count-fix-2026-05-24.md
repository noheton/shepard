---
stage: deployed
last-stage-change: 2026-05-24
---

# UI-011e — `# DOs` column shows 0 everywhere on `/collections` — fix

**Status:** ✅ shipped (worktree UI-011e branch)
**Closes:** UI-2026-05-24-011e (per `aidocs/16-dispatcher-backlog.md` row)
**Surfaced by:** UX Scrutinizer (`aidocs/agent-findings/ux-scrutinizer-workflows-2026-05-24.md` §"# DOs = 0", line 32 + 92).

## Diagnosis

The task hypothesis was: *"The frontend reads `Collection.dataObjectIds.length` but the list endpoint Cypher very likely doesn't hydrate the `HAS_DATA_OBJECT` relationship — only the per-id GET does."*

**That hypothesis is empirically false.** Live curl proof (alice, 2026-05-24):

```bash
TOKEN=$(curl -fsS -X POST "https://shepard-auth.nuclide.systems/realms/shepard-demo/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=frontend-dev" \
  -d "username=alice" -d "password=alice-demo" | jq -r .access_token)

# v2 list endpoint (CollectionV2Rest → CollectionDAO):
curl -fsS -H "Authorization: Bearer $TOKEN" \
  https://shepard-api.nuclide.systems/v2/collections \
  | jq '.[] | select(.name | test("LUMEN|MFFD-Dropbox|Home energy")) \
              | {name, doCount: (.dataObjectIds|length)}'
# → LUMEN:        17
# → Home energy:   3
# → MFFD-Dropbox:  8514
```

So `GET /v2/collections` was already returning correct counts.

The frontend `/collections` page **does not call that endpoint.** It calls:

```
frontend/components/context/collection/list/useSearchCollections.ts
  → SearchApi.searchCollections({...})
  → POST /shepard/api/search/collections
  → SearchRest.searchCollections
  → CollectionSearchService.search
  → SearchDAO.findCollections
  → emitCollectionReturnPart  ← the bug lives here
```

`emitCollectionReturnPart` at `backend/src/main/java/de/dlr/shepard/common/search/daos/SearchDAO.java:112-120` projected the Collection neighborhood with `Neighborhood.ESSENTIAL`. Per `CypherQueryHelper.getNeighborhoodPart` line 95:

```java
case ESSENTIAL -> "path=(%s)-[*0..%d]->(n) WHERE n:Permission OR n:User";
```

ESSENTIAL only walks `:User` and `:Permission` nodes. `:Collection`-[:has_dataobject]->(:DataObject) was never traversed, so the Neo4j-OGM session never hydrated the `Collection.dataObjects` list on the returned entity, and `CollectionIO.fromEntity()` produced `dataObjectIds = new long[0]`. Confirmed by the live search probe:

```bash
curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST \
  -d '{"searchParams":{"query":"{\"OR\":[{\"property\":\"name\",\"value\":\"\",\"operator\":\"contains\"}]}"}}' \
  "https://shepard-api.nuclide.systems/shepard/api/search/collections?size=20&page=0" \
  | jq '.results[] | {name, doCount: (.dataObjectIds|length)}'
# → every row: doCount: 0 (BUG)
```

## Fix chosen — Option C (not A or B)

The task spec offered Option A (hydrate `dataObjectIds`) vs. Option B (add `dataObjectCount`). Both targeted `CollectionV2Rest` / `CollectionDAO`. Neither would have fixed anything, because that path already works.

The actual one-line fix: flip `emitCollectionReturnPart` from `Neighborhood.ESSENTIAL` to `Neighborhood.EVERYTHING` (depth 1). That's the same neighborhood `CollectionDAO.findAllCollectionsByShepardId` uses via the default `CypherQueryHelper.getReturnPart(entity)` overload — which was already proven in production at 8514 DOs.

```java
// backend/src/main/java/de/dlr/shepard/common/search/daos/SearchDAO.java
private String emitCollectionReturnPart(String collectionVariable, PaginationHelper pagination) {
  return (
    (pagination != null ? " " + CypherQueryHelper.getPaginationPart(pagination) : "") +
    " WITH " + collectionVariable + " " +
    CypherQueryHelper.getReturnPart(collectionVariable, Neighborhood.EVERYTHING)
    //                                                  ^^^^^^^^^^^^^^^^^^^^^^^
    //                                                  was: Neighborhood.ESSENTIAL
  );
}
```

**Why not Option B (add a `dataObjectCount` field):** the frontend field name `dataObjectIds` already existed and was correctly read. Adding a parallel COUNT field would have introduced wire-shape churn for zero behavioural gain. The EVERYTHING projection on MFFD-Dropbox (8514 DOs) is exactly the same neighborhood `CollectionV2Rest` already runs successfully — bounded by production precedent, no new performance risk.

Container / User / UserGroup `emit*ReturnPart` helpers were intentionally left on ESSENTIAL. Their list cells don't expose nested-entity counts the way `# DOs` does for collections.

## Scope vs. task spec

| Task spec said touch | Touched? | Why / why not |
|---|---|---|
| `CollectionV2Rest.java` | ❌ | Already correct on the wire. |
| `CollectionDAO.java` (list query) | ❌ | Already correct on the wire. |
| `CollectionIO.java` (new field) | ❌ | Existing `dataObjectIds` field is the right shape. |
| `CollectionList.vue` (read new field) | ❌ | Existing `(rowProps.item.dataObjectIds || []).length` is correct. |
| `SearchDAO.java` (not in task scope) | ✅ | The actual broken path. |
| `SearchDAOTest.java` (not in task scope) | ✅ | Test asserts the literal Cypher string, must update. |

Per advisor: surface the scope mismatch explicitly rather than fixing the listed files cosmetically and shipping a "fix" that doesn't fix anything. Collision check stayed clean — no other agent touches `common/search/daos/`.

## Tests

| Test | Type | Result |
|---|---|---|
| `SearchDAOTest.findCollectionsTest` (extended) | Existing | ✅ passes — asserts the new EVERYTHING Cypher string |
| `SearchDAOTest.findCollectionsHydratesDataObjects_UI011e` (new) | Regression guard | ✅ passes — explicit assertion that a Collection with 3 DOs comes back with 3 DOs hydrated |
| Other `SearchDAOTest.*` (containers, users, user groups) | Untouched | ✅ all 9 still pass on ESSENTIAL projection |
| Backend full suite (search package) | Existing | All search-service tests pass. **Two pre-existing failures** (`CollectionDAOTest.createOrUpdate_preservesExistingAppId` Mockito mismatch, `DataObjectSearchServiceQuarkusTest` Quarkus bootstrap NoClassDefFoundError) confirmed present on baseline `git stash` — unrelated to this fix. |
| `e2e/tests/ui-011e-do-count-shows.spec.ts` (new) | Playwright live | ✅ added — asserts LUMEN row + MFFD-Dropbox row each show `# DOs > 0` |

Frontend Vitest was unnecessary — no `.vue` file changed.

## Live before/after — see post-deploy section below

Pre-deploy curl (search endpoint) shows every collection with `doCount: 0`. Post-deploy curl will be appended below after `make redeploy-backend` completes.

## Backlog flips + follow-ups

- `aidocs/16-dispatcher-backlog.md` row `UI-011e` → ✅ **shipped** with diagnosis update (root cause + scope mismatch documented in the row notes).
- `aidocs/34-upstream-upgrade-path.md` — new row added: *"UI-011e — `POST /search/collections` now returns populated `dataObjectIds[]` (bug fix)"* — admin status ZERO (no schema, no endpoint, no config; behaviour fix only).
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — no new row needed; the parent UI-011 row already shipped per the existing entry.
- `aidocs/data/00-model-inventory.md` — no change (no entity, migration, or IO touched).

**Follow-up backlog candidate (queued, not blocking):**
- Audit other search projections for similar latent gaps. The `ESSENTIAL` neighborhood is fine for containers' list cells (no nested-count column), but worth re-checking when those pages grow analogous columns (e.g. "# References" per container).

## What surprised me

The task brief was confidently wrong about where the bug lived. A 5-second live curl against `/v2/collections` was the cheapest possible test of the hypothesis — and falsified it immediately. Anchoring on the task's stated suspicion would have produced a "fix" that touched the listed files but didn't repair anything the user saw. The empirical probe came first; the patch followed the evidence.

The UX Scrutinizer's own report hedged: *"One-line backend issue (the listing endpoint isn't returning the count) or one-line frontend issue (the field name doesn't match)."* The actual answer was a third path: backend, but the *other* list endpoint. The bug lived where the frontend actually called.

---

## Post-deploy verification

### Smoke test (`make redeploy-backend`)

```
──────────────────────────────────────────────
PASS: 25    FAIL: 0
All checks passed.
```

### Live curl — search endpoint with fresh alice token (post-fix)

```
$ TOKEN=$(curl -fsS -X POST "https://shepard-auth.nuclide.systems/realms/shepard-demo/protocol/openid-connect/token" \
    -d "grant_type=password" -d "client_id=frontend-dev" \
    -d "username=alice" -d "password=alice-demo" | jq -r .access_token)

$ BODY='{"searchParams":{"query":"{\"OR\":[{\"property\":\"name\",\"value\":\"\",\"operator\":\"contains\"},{\"property\":\"createdBy\",\"value\":\"\",\"operator\":\"contains\"},{\"property\":\"id\",\"value\":0,\"operator\":\"eq\"}]}"}}'

$ curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST -d "$BODY" \
    "https://shepard-api.nuclide.systems/shepard/api/search/collections?size=200&page=0" \
    | jq '.results[] | select(.name | test("LUMEN|MFFD-Dropbox|Home|AI Exchange")) | {name, id, doCount: (.dataObjectIds|length)}'

{
  "name": "MFFD-Dropbox",
  "id": 661923,
  "doCount": 8514
}
{
  "name": "AI Exchange",
  "id": 493423,
  "doCount": 12
}
{
  "name": "Home energy & environment (live)",
  "id": 720,
  "doCount": 3
}
{
  "name": "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)",
  "id": 42,
  "doCount": 17
}
```

Pre-fix the same query returned `doCount: 0` for every result.

### Playwright e2e (live)

```
$ cd e2e && ./node_modules/.bin/playwright test ui-011e-do-count-shows.spec.ts \
    --reporter=list --workers=1 --retries=2

Running 2 tests using 1 worker
  ✓ 1 [chromium] › tests/ui-011e-do-count-shows.spec.ts:29:7 › LUMEN row's # DOs cell shows a non-zero count (2.8s)
  ✓ 2 [chromium] › tests/ui-011e-do-count-shows.spec.ts:57:7 › MFFD-Dropbox row's # DOs cell shows a non-zero count (4.0s)
  2 passed (7.8s)
```

The LUMEN test walks the `# DOs` cell of the LUMEN row and asserts it shows
> 0; the MFFD-Dropbox test does the same with > 100. The first iteration of
the test scoped `/collections` directly and tried a textbox-fallback to find
the row when not on page 1 — but the page-1 fallback accidentally hit the
global header search (`/advanced-search`), not the in-page filter. Fixed by
navigating to `/collections?searchText=<name>` directly — the URL param is
the same one `CollectionSearchField.vue` writes, and it routes through
`SearchApi.searchCollections` (the same path the fix targeted).

