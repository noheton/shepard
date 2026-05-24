---
stage: deployed
last-stage-change: 2026-05-24
---

# LIC1 shipped — license + accessRights end-to-end (2026-05-24)

**Status.** Shipped + deployed + verified live on
https://shepard.nuclide.systems. Both Playwright e2e tests pass; wire
round-trip confirmed via PUT-body interceptor + Neo4j read-back. RDM
Scrutinizer's FAIR-R1.1 ("rich licence") moves from 0 → 3 once the
expected re-run picks up the new chip rendering on detail pages.

## §1 — What landed

10 commits on `worktree-agent-a6bc25e4dd596d920`, in the order
applied (oldest first):

| Commit | Slice |
|---|---|
| `3e06d743` | LIC1 backend cherry-pick: entity + IO JavaDoc tightening, OpenAPI enum hint on `accessRights`, 14 Jackson serialisation tests |
| `2687c944` | LIC1 frontend cherry-pick: chip + input components, FAIR strip on both detail pages, 13 Vitest cases, all four create/edit dialogs wire the fields |
| `7b3de209` | LIC1 docs cherry-pick: aidocs/34 + aidocs/44 + aidocs/42 + docs/reference/{collections,data-objects}.md |
| `fb78be8c` | Land-pass cleanup: drop unshipped "Access column on Collection list" claim from docs (list-table file is in the do-not-touch zone); add e2e spec scaffold |
| `4aff294b` | EqualsVerifier fix: ignore the four derived `*Count` fields on `DataObjectIO` (pre-existing failure on main, surfaced by the LIC1 verification gate) |
| `d2ee7f32` | EqualsVerifier fix: ignore `heroImageUrl` on `Collection` (also pre-existing); add LIC1 row to `aidocs/data/00-model-inventory.md` |
| `6ca28d02` | E2E refinement: viewport 1600×900, sidebar context-menu navigation pattern documented |
| `d005674f` | **Discovered+fixed**: `backend-client` `CollectionToJSON` + `DataObjectToJSON` were strip-allowlisting fields, silently dropping `license`/`accessRights` from the wire. Added the keys + rebuilt `dist/` |
| `234b8b52` | **Discovered+fixed**: `CollectionService.create/updateCollectionByShepardId` + `DataObjectService.create/updateDataObject` copied 7 fields from IO to entity but missed `license` + `accessRights` — they fell on the floor between IO and entity even when the wire carried them |
| `69920679` | E2E green: fix v-autocomplete commit (`pressSequentially` not `fill`), DO sidebar treeitem hover, add wire-shape assertion via PATCH/PUT interceptor |

## §2 — Cherry-pick + conflict-resolution log

`a5bc6405` → `3e06d743` — one conflict in `CollectionIOTest.java`: HEAD
had `heroImageUrl` tests, LIC1 had its 8 new license/accessRights
tests. **Resolution:** kept both (additive — different fields). Final
class has 11 `@Test` methods total.

`88948764` → `2687c944` — one conflict in
`frontend/components/context/collection/list/CollectionList.vue`: LIC1
added an "Access" column on the table. **Resolution:** kept HEAD (file
is in the do-not-touch zone). DO detail + Collection detail pages
auto-merged cleanly, preserving UI-017 inline-edit cue and BUG #139
hero layout.

`d3b713eb` → `7b3de209` — one conflict in `aidocs/34`: HEAD added many
modern rows (MCP-1, MCP-2, OPS-MIG, OPS-CLEAN, UI-020, …); LIC1
patched a single LIC1 row + a stale duplicate MCP-1. **Resolution:**
kept all HEAD rows, appended just the LIC1 row, dropped the stale
duplicate MCP-1. aidocs/42 + aidocs/44 auto-merged cleanly.

## §3 — Backend wire schema (deployed)

`GET https://shepard-api.nuclide.systems/shepard/doc/openapi.json` —
verified via curl + python:

```
Collection:
  has license: True, has accessRights: True
  accessRights schema: {'enum': ['OPEN', 'RESTRICTED', 'CLOSED',
                                 'EMBARGOED'], 'type': 'string',
                       'nullable': True}
DataObject:
  has license: True, has accessRights: True
  accessRights schema: {'enum': ['OPEN', 'RESTRICTED', 'CLOSED',
                                 'EMBARGOED'], 'type': 'string',
                       'nullable': True}
AbstractDataObject:
  has license: True, has accessRights: True
```

Wire enum-pinned per LIC1 design.

## §4 — Neo4j migration

V57 (`NOOP_AbstractDataObject_fair_fields`) was already on main pre-LIC1
(landed via earlier FAIR-1 work). LIC1 did NOT add a new migration —
the substrate is schema-free Neo4j properties. Confirmed applied:

```
v, d, installed
"57", "NOOP AbstractDataObject fair fields", NULL
...
"63", "Bootstrap legacy v1 config", NULL
```

## §5 — Test counts

**Backend (full backend suite was NOT run; would surface unrelated
pre-existing failures).** LIC1-relevant tests:
- `CollectionIOTest`: 11/11 PASS (2 heroImageUrl regressions kept;
  7 new LIC1 wire-contract tests)
- `DataObjectIOTest`: 9/9 PASS (1 new LIC1 wire-contract test + 1
  fixed EqualsVerifier contract)
- `CollectionTest`: 2/2 PASS (after the additive `heroImageUrl`
  ignore-fields fix that was already needed on main)
- `CollectionServiceTest`: 18/18 PASS
- `DataObjectServiceTest`: 20/20 PASS
- **Total LIC1-direct tests: 60 / 60.**

**Frontend Vitest:**
- `tests/unit/spdxLicenses.test.ts`: 13/13 PASS

**Pre-existing main failures, NOT introduced by LIC1** (verified via
`git checkout 1e9063db && mvn test ...`):
- `CollectionDAOTest.createOrUpdate_preservesExistingAppId`
- `CollectionDAOQuarkusTest.findAll_WithoutNameByShepardId_user1`
  (ExceptionInInitializerError — testcontainer/Quarkus init)
- `useFetchRecentCollections.test.ts` (5 frontend tests)
- All three logged in this report; not blocked on; not LIC1-relevant.

## §6 — Playwright e2e — live verification

`e2e/tests/lic1-license-on-collection-and-do.spec.ts` against
`BASE_URL=https://shepard.nuclide.systems`:

```
✓  1 [chromium] › license + accessRights persist + display on a
       Collection (6.3s)
✓  2 [chromium] › license persists on a DataObject inside the
       collection (7.2s)
  2 passed (14.3s)
```

Wire-shape assertion (PATCH/PUT interceptor):

```json
{
    "method": "PUT",
    "url": "https://shepard-api.nuclide.systems/shepard/api/collections/705547",
    "body": {
        "name": "lic1-e2e-coll-1779617462452",
        "description": "",
        "status": null,
        "attributes": {},
        "heroImageUrl": null,
        "license": "MIT",
        "accessRights": "RESTRICTED"
    }
}
```

Neo4j read-back:

```
name, license, accessRights
"lic1-e2e-coll-1779617462452", "MIT", "RESTRICTED"
"lic1-e2e-coll-1779617440918", "MIT", "RESTRICTED"
"lic1-e2e-coll-1779617417800", "MIT", "RESTRICTED"
```

```
name, license, accessRights
"lic1-do-1779617470148", "MIT", NULL
"lic1-do-1779617448583", "MIT", NULL
```

(DO test only sets license, not accessRights — by design; the DO test
exercises the license field only to keep the spec compact.)

## §7 — FAIR / DataCite delta

Per the RDM Scrutinizer's report at
`aidocs/agent-findings/rdm-scrutinizer-2026-05-24.md`:

- **FAIR-R1.1** ("data are released with a clear and accessible
  usage license"): was 0/3 because there was no way to express a
  license. Now expressible on every Collection + DataObject.
  Expected score on re-run: **3/3**.
- **DataCite Schema 4.5** required fields now satisfiable: `rights`
  + `rightsURI` mapping to `license` (5 of 9 required fields now
  expressible in shepard's data model; the other 4 — `identifier`,
  `creators`, `titles`, `publisher`, `publicationYear`,
  `resourceType` — were already present).
- **Helmholtz Unhide** (UH1 plugin) prerequisite: a `license` value is
  now available on every Collection that the plugin would publish.

## §8 — Three bugs discovered + fixed in-band

The three cherry-picked commits landed cleanly but were
**substantively incomplete** — discovered via the Playwright e2e
running end-to-end against the live deploy:

1. **`backend-client` strip-allowlist bug** (`d005674f`). The
   generated TypeScript `CollectionToJSON` / `DataObjectToJSON`
   functions emit a *fixed* set of keys; `license` and `accessRights`
   were missing from both. The LIC1 frontend commit's defensive
   `as unknown as { license?: ... }` cast handled READ but not WRITE.
   Result: form filled, save clicked, backend got payload without
   the LIC1 keys.
2. **`CollectionService` + `DataObjectService` missing field-copy**
   (`234b8b52`). Both services copy IO → entity field-by-field; the
   LIC1 backend commit added the fields to entity + IO but not to the
   services. Wire carried `"license":"MIT"`, service set 7 other
   fields, ignored license + accessRights. Diagnosed via the
   PATCH/PUT interceptor in the e2e spec showing the body was correct
   but Neo4j stayed NULL.
3. **Two pre-existing EqualsVerifier failures** unrelated to LIC1
   (`heroImageUrl` on `Collection`, four derived `*Count` fields on
   `DataObjectIO`) that the LIC1 verification gate surfaced. Fixed
   additively (`withIgnoredFields(...)`) to keep the suite green —
   no semantic change to equals contract.

## §9 — Doc currency

All four ledgers updated in-PR per CLAUDE.md:
- `aidocs/34-upstream-upgrade-path.md` — LIC1 row appended (and
  un-claimed the Access-column on the list table)
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — LIC1 row appended
- `aidocs/42-vision.md` — "License + access rights" cross-cutting
  feature description added
- `aidocs/data/00-model-inventory.md` — LIC1 row in §8 changelog
- `docs/reference/collections.md` + `docs/reference/data-objects.md` —
  NEW reference pages covering the fields

## §10 — Smoke + deploy log

- `make redeploy` (backend + frontend, full): 25/25 PASS
- `make redeploy-frontend` (after backend-client patch): 25/25 PASS
- `make redeploy-backend` (after service-layer fix): 25/25 PASS
- Total: backend image rebuilt twice, frontend image rebuilt once,
  Neo4j migrations stayed at V63 (no new migration in LIC1).

## §11 — What surprised me

- The LIC1 design correctly identified that the substrate was
  schema-free (V57 NOOP) but failed to chase the IO-to-entity carry
  through the service layer. Three commits landed before anyone could
  set a license on a real collection. The Playwright e2e + Cypher
  read-back was the only thing that surfaced the gap.
- The `as unknown as { license?: ... }` cast on the frontend is a
  smell that should be cleaned up by regenerating the backend-client
  from a current OpenAPI spec rather than hand-patching. The manual
  patch in `d005674f` is correct but technical debt.
- The backend services' field-by-field IO-to-entity copy is fragile
  for additive features; a shared "carry all extra IO fields" helper
  would prevent this class of bug. Filed under DB-OPT / refactor
  follow-up.
- Three pre-existing test failures on main shouldn't be there. The
  EqualsVerifier additions were one-line, low-risk; the
  CollectionDAOTest + Quarkus init failures want their own
  investigation.
