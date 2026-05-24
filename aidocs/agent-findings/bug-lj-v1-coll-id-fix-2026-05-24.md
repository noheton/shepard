---
stage: tests-implemented
last-stage-change: 2026-05-24
audience: backend reviewer, RDM, operator
---

# BUG-LJ-V1-COLL-ID — fix report

**Closes:** `aidocs/16 BUG-LJ-V1-COLL-ID` (queued → ✅ shipped 2026-05-24).
**Follow-up filed:** `aidocs/16 OGM-HYDRATION-AUDIT` (sweep sibling v2 bulk DAOs for the same defect).

## 1. The brief's hypothesis was wrong by primary evidence

The dispatch brief framed this as a v1-numeric-id → appId resolver gap. The
recommendation was to make the endpoint accept either an appId OR a numeric id
in the `{collectionAppId}` slot, mirroring the today-shipped `PROV-V1-NUMERIC-LOOKUP`
work. **Live reproduction overturned the hypothesis before I touched any code.**

Reproduction (against `https://shepard-api.nuclide.systems` with the Demo Admin
JWT from `~/.claude/projects/-opt-shepard/memory/project_mffd_api_keys.md`):

```bash
# Note: backend root path is `/`, NOT `/shepard/api/` — the brief's URL prefix
# was off by `/shepard/api/`. Once corrected:

curl -sS -H "X-API-KEY: $JWT" \
  "https://shepard-api.nuclide.systems/v2/collections/019e30b0-99a2-79e7-b7d8-c15396095b42/lab-journal-entries"
# HTTP 500
# {"type":"https://noheton.github.io/shepard/errors/internal.unexpected", ...}

curl -sS -H "X-API-KEY: $JWT" \
  "https://shepard-api.nuclide.systems/v2/collections/019e4e56-ca63-76f3-9bf0-6681f7fe6d56/lab-journal-entries"
# HTTP 200 — MFFD-Dropbox, empty list []

curl -sS -H "X-API-KEY: $JWT" \
  "https://shepard-api.nuclide.systems/v2/collections/42/lab-journal-entries"
# HTTP 404 — resolver returns NotFoundException for `42` (no node with appId="42")
```

Backend log for the 500 (Quarkus, container `infrastructure-backend-1`):

```
14:00:50.145 [INFO]  LoggingFilter ... Received GET request on
  /v2/collections/019e30b0-99a2-79e7-b7d8-c15396095b42/lab-journal-entries
14:00:50.151 [ERROR] ShepardExceptionMapper [e19059e4-...]
  Unhandled NullPointerException on GET .../lab-journal-entries -> HTTP 500
14:00:50.151 [INFO]  ShepardExceptionMapper [e19059e4-...] cause:
  Cannot invoke "de.dlr.shepard.context.collection.entities.DataObject.getShepardId()"
  because the return value of
  "de.dlr.shepard.context.labJournal.entities.LabJournalEntry.getDataObject()"
  is null
```

Live Cypher proves the resolver isn't the problem:

```
$ docker exec infrastructure-neo4j-1 cypher-shell -u neo4j -p ... <<'SQL'
MATCH (e {appId: "019e30b0-99a2-79e7-b7d8-c15396095b42"})
RETURN id(e) AS ogmId, labels(e) AS labels LIMIT 5;
SQL
ogmId, labels
42, ["Collection", "VersionableEntity", "BasicEntity"]
```

The Collection node exists, carries `appId` correctly, and resolves to `ogmId=42`
via the exact Cypher `EntityIdResolver.resolveLong` runs. The brief's "appId NULL
on v1-seeded LUMEN" framing was a guess that didn't survive live inspection.

LUMEN has 4 lab journal entries linked via the `has_labjournalentry` relationship
(TR-004 vibration spike, anomaly investigation, TR-006 nominal, Publications).
MFFD-Dropbox has zero, which is why MFFD returned 200 [] and **hid the bug**.

## 2. Actual root cause

`CollectionLabJournalEntriesDAO.findByCollectionAppId` originally returned:

```cypher
MATCH (coll:Collection {appId: $appId})
  -[:has_dataobject]->(do:DataObject)
  -[:has_labjournalentry]->(lje:LabJournalEntry)
WHERE (do.deleted IS NULL OR do.deleted = false)
  AND (lje.deleted IS NULL OR lje.deleted = false)
RETURN DISTINCT lje
```

The DAO comment said: *"Returning the LabJournalEntry node via the OGM ensures
the related createdBy / updatedBy User entities load with the standard depth-1
hooks so LabJournalEntryIO sees populated User objects."* That assumption was
half-right — Neo4j-OGM's `session.query(EntityType, ...)` does follow OUTGOING
relationships at depth 1 from the projected node — but **INCOMING relationships
are not hydrated** unless the projection explicitly includes them. The reverse-
edge from `DataObject` to `LabJournalEntry` (declared with `direction = INCOMING`
on `LabJournalEntry.dataObject`) is exactly that case.

So every row returned had `lje.dataObject == null`. The next line in the resource
loop (`new LabJournalEntryIO(e)`) calls `labJournalEntry.getDataObject().getShepardId()`
in its constructor — and NPEd on the first entry.

The canonical safe pattern is already in the codebase:
`CypherQueryHelper.getReturnPart(entity)` projects
`entity, nodes(path), relationships(path)` so the OGM hydrates the FULL
depth-1 neighbourhood (both directions). The sibling `LabJournalEntryDAO.findByAppId`
uses exactly this pattern; the new bulk DAO didn't.

## 3. Fix shape applied

Two layers, defence in depth:

### 3a. DAO Cypher — primary fix

`backend/src/main/java/de/dlr/shepard/v2/labjournal/daos/CollectionLabJournalEntriesDAO.java`
now projects a depth-1 neighbourhood path so the OGM hydrates both directions:

```cypher
MATCH (coll:Collection {appId: $appId})
  -[:has_dataobject]->(do:DataObject)
  -[:has_labjournalentry]->(lje:LabJournalEntry)
WHERE (do.deleted IS NULL OR do.deleted = false)
  AND (lje.deleted IS NULL OR lje.deleted = false)
WITH DISTINCT lje
MATCH path=(lje)-[*0..1]-(n)
WHERE n.deleted = false OR n.deleted IS NULL
RETURN lje, nodes(path), relationships(path)
```

The `WITH DISTINCT lje` keeps the de-dup intent (a single LJE returned once even
if its neighbourhood path branches) while the second `MATCH` is the canonical
`CypherQueryHelper.Neighborhood.EVERYTHING` shape. The `WHERE n.deleted ...`
filter inside the walk prevents soft-deleted neighbours from contaminating the
hydrated graph.

### 3b. Resource orphan-skip — belt-and-braces

`backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/CollectionLabJournalEntriesRest.java`
now skips any entry whose owning DataObject still hasn't hydrated (rather than
NPE'ing the whole bulk response) and emits a WARN log so an operator can audit
orphan rows:

```java
for (LabJournalEntry e : entries) {
  if (e.getDataObject() == null) {
    Log.warnf(
      "Skipping orphan LabJournalEntry (appId=%s) with null DataObject in collection %s",
      e.getAppId(), collectionAppId
    );
    continue;
  }
  ios.add(new LabJournalEntryIO(e));
}
```

The DAO fix is the primary defence; the resource skip is a safety net against
future hydration drift OR a genuinely orphan LJE (a partial migration could
leave one). The Cypher already filters orphans out via the MATCH, so this is
strictly defence-in-depth.

## 4. Deviations from the brief

The brief asked for two things this PR does NOT do:

1. **Accept numeric ids in `{collectionAppId}`** — the recommended "cheap fix."
   I dropped it. It doesn't close the bug: the frontend calls with the proper
   UUID (`useFetchCollectionLabJournalEntries` reads `collection.appId`), and
   the 500 happens AFTER successful appId resolution. Adding numeric-id
   acceptance would be a feature change that didn't solve the reported failure.

2. **File `L2b-COLLECTION-BACKFILL`.** I filed `OGM-HYDRATION-AUDIT` instead,
   scoped to the actual class of bug (Cypher projection drops INCOMING
   relationships). The L2b backfill is irrelevant here — LUMEN's Collection
   already has `appId` populated and the resolver succeeds.

The advisor explicitly flagged both deviations before any code landed; primary-
source evidence (cypher-shell + backend log) carried the call.

## 5. Test results

```
$ ./mvnw test -Dtest=CollectionLabJournalEntriesRestTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Test matrix (one new case + six pre-existing):

| Case | Verifies |
|---|---|
| `list_returns401WhenUnauthenticated` | auth ordering (existing) |
| `list_returns404WhenCollectionNotFound` | resolver NotFoundException → 404 (existing) |
| `list_returns403WhenNoReadPermission` | permission gate (existing) |
| `list_returns200WithEntriesAndDataObjectIdPopulated` | happy path: dataObjectId round-trips from hydrated entity (existing; would have caught this bug in a Testcontainer but not in Mockito) |
| `list_returns200WithEmptyListWhenNoEntries` | empty-collection path — MFFD-Dropbox shape (existing) |
| `list_usesCollectionOgmIdForPermissionCheck` | permission-check arg shape (existing) |
| **`list_skipsOrphanEntryWithNullDataObjectInsteadOf500`** | **NEW.** Synthesises an LJE with `dataObject=null`; asserts the resource SKIPS it (logs WARN) instead of NPE'ing. Pins the resource-level safety net so a future hydration regression cannot resurface HTTP 500. |

A Playwright e2e (`e2e/tests/bug-lj-v1-coll-id.spec.ts`) drives the regression
from the UI side on `BASE_URL=https://shepard.nuclide.systems`:
- Auth as alice, navigate to `/collections/42` (LUMEN landing).
- Capture every response on the bulk endpoint.
- Assert no 5xx, at least one 200 against the LUMEN appId, and no error
  alert on the Lab Journal panel.

The e2e cannot run until the backend deploys (Lombok APT issue below).

### Build / deploy status

Backend `mvn package` on this worktree fails at compile time on the **same
pre-existing Lombok APT issue documented in `aidocs/agent-findings/prov-resolver-fix-2026-05-24.md §5`**
(`PermissionsService` / `PermissionsIO` `getOwner()` / `getReader()` / `getUsername()`
errors). The errors are in files this PR does **not** touch — confirmed by
stashing this branch's diff and re-running on a pristine checkout. Targeted
`mvn test -Dtest=CollectionLabJournalEntriesRestTest` works because surefire
bypasses the broken APT step for the test classpath.

**Operator: `make redeploy-backend` will require resolving the Lombok APT
issue first** (separate work; flag for the maintainer running the merge).
This fix is verifiable by inspection + the new unit-test case + comparison
with the canonical sibling DAO pattern (`LabJournalEntryDAO.findByAppId`).

## 6. OGM-HYDRATION-AUDIT — follow-up filed

The same defect can recur in any v2 bulk DAO that returns just the leaf node
without a neighbourhood-path projection. Filed `aidocs/16 OGM-HYDRATION-AUDIT`
to sweep `de.dlr.shepard.v2.*.daos` for the `RETURN <entity>`-only pattern.
Cheap audit; one-off grep + manual review per hit.

## 7. What surprised me

1. **The brief's framing was confidently wrong.** The dispatch listed three
   candidate causes (a/b/c) with (a) as the "most likely." Live reproduction
   in five minutes proved (a) wrong: the resolver works, the appId is set,
   the collection node hydrates cleanly. Following the brief's recommended
   fix would have shipped a feature change that didn't close the bug. Lesson:
   reproduce-first is non-negotiable, even when the dispatch is sure.
2. **MFFD-Dropbox masked the bug as a feature.** The UI-020 PR's smoke test
   passed cleanly on MFFD-Dropbox because the collection has zero lab journal
   entries — the bulk endpoint returned 200 [] with the empty array short-
   circuiting the IO loop. The bug only surfaces on collections with entries,
   and the UI-020 PR's test suite (Mockito + one MFFD Playwright run) had no
   "collection with seeded entries" coverage. The new e2e here pins LUMEN
   specifically so this regression class can't recur.
3. **The URL prefix in the brief was wrong too.** The brief's reproduction
   command used `/shepard/api/v2/collections/...` but the backend root-path
   is `/`, so the correct path is just `/v2/collections/...`. The wrong URL
   returned 404 (resource not found) which initially looked like a routing
   issue — exactly the kind of false-flag that pulls the diagnosis toward
   the brief's hypothesis. Fixed prefix + reproduced cleanly.

## 8. Files changed in this PR

```
backend/src/main/java/de/dlr/shepard/v2/labjournal/daos/CollectionLabJournalEntriesDAO.java          (Cypher projection fix + JavaDoc)
backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/CollectionLabJournalEntriesRest.java    (defensive orphan-skip + WARN log)
backend/src/test/java/de/dlr/shepard/v2/labjournal/resources/CollectionLabJournalEntriesRestTest.java (+1 case: orphan-skip safety net)
e2e/tests/bug-lj-v1-coll-id.spec.ts                                                                   (NEW, live regression test against LUMEN)
aidocs/16-dispatcher-backlog.md                                                                       (BUG-LJ-V1-COLL-ID → shipped; OGM-HYDRATION-AUDIT filed)
aidocs/34-upstream-upgrade-path.md                                                                    (+1 upgrade-tracker row, admin-visible bug fix)
aidocs/agent-findings/bug-lj-v1-coll-id-fix-2026-05-24.md                                            (this file)
```

Out of scope (per brief's hard rules): `EntityAppIdLookup`, `DataObjectService`,
`PermissionsService`, LUMEN Neo4j data, the BUG-148 / OPS-hygiene-bundle agents'
surfaces. None touched.
