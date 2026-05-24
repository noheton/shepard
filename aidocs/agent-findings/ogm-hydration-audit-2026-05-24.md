---
stage: audited-by-personas
last-stage-change: 2026-05-24
purpose: sweep v2 DAOs for the RETURN-leaf-only Cypher pattern that triggered BUG-LJ-V1-COLL-ID
---

# OGM-HYDRATION-AUDIT — v2 DAO sweep (2026-05-24)

## §0 Verdict

A full read of every Cypher-emitting class under
`backend/src/main/java/de/dlr/shepard/v2/**` (10 files; 26 `RETURN`
statements; 7 `@NodeEntity`-typed entities exposed by those DAOs)
turned up **zero CONFIRMED v2 occurrences** of the BUG-LJ-V1-COLL-ID
defect class beyond the one already fixed in
`CollectionLabJournalEntriesDAO`. The sweep also surfaced **one v2
DEFERRED suspect** (`TimeseriesAnnotationDAO` — uses the canonical
`getReturnPart` helper, so it is already safe; flagged here only so a
future contributor doesn't regress it) and **one v1 DEFERRED suspect**
caught by the bonus check (`LabJournalEntryRevisionDAO.findByEntry`
returns leaf only and the leaf carries an INCOMING
`@Relationship`-typed back-reference, but no current downstream caller
walks it).

The reason the v2 surface is clean is structural rather than careful:
**every v2 `@NodeEntity` that backs a `RETURN`-bearing DAO is a flat
property bag**. None of `Watch`, `CollectionWatcher`, `Notification`,
`TimeseriesContainerChartView`, `ImportPlan`, `SqlTimeseriesConfig`,
or `InstanceRorConfig` declares a single `@Relationship` field. The
two v2 DAOs that *do* sit on a relationship-typed entity
(`CollectionLabJournalEntriesDAO` on `LabJournalEntry`,
`TimeseriesAnnotationDAO` on `TimeseriesAnnotation`) either now use
the canonical fix shape or were authored against `getReturnPart` from
day one. So the v2 fork's choice to store cross-entity references as
opaque `appId` strings (rather than OGM-managed edges) has
**accidentally inoculated the v2 surface** against the BUG-LJ-V1
shape — at the cost of giving up referential integrity at the graph
boundary (a separate audit item: see §8).

## §1 The defect pattern + the canonical fix

The defect is a **RETURN-leaf-only Cypher projection on an entity
whose IO/service layer reads an `@Relationship(direction = INCOMING)`
back-reference**. Neo4j-OGM 4.x hydrates the depth-1 OUTGOING
neighbourhood of nodes named in `RETURN` and that's it. Anything
requiring a walk back UP the graph — `lje.dataObject`,
`revision.labJournalEntry`, the `dataObject.parent` chain — returns
`null` silently. The downstream IO constructor then NPEs the moment
it touches `getX().getShepardId()` or `getX().getAppId()`.

Canonical fix (per `CypherQueryHelper#getReturnPart`, line 71):

```java
// Bad — silent null on lje.dataObject
"MATCH (c:Collection {appId:$id})-[*]->(lje:LabJournalEntry) " +
"WHERE NOT lje.deleted " +
"RETURN DISTINCT lje";

// Good — depth-1 neighbourhood path hydrates both directions
"MATCH (c:Collection {appId:$id})" +
"-[:has_dataobject]->(do:DataObject)" +
"-[:has_labjournalentry]->(lje:LabJournalEntry) " +
"WHERE (do.deleted IS NULL OR do.deleted = false) " +
"  AND (lje.deleted IS NULL OR lje.deleted = false) " +
"WITH DISTINCT lje " +
"MATCH path=(lje)-[*0..1]-(n) " +
"WHERE n.deleted = false OR n.deleted IS NULL " +
"RETURN lje, nodes(path), relationships(path)";
```

The helper builds exactly the latter shape. `CypherQueryHelper`
`getReturnPart(entity)` →
`getReturnPart(entity, Neighborhood.EVERYTHING, 1)` →
`"MATCH path=(entity)-[*0..1]-(n) WHERE n.deleted = FALSE OR n.deleted
IS NULL RETURN entity, nodes(path), relationships(path)"`. Use the
helper unless you have a specific reason to hand-roll (and if you do,
match its shape).

## §2 Audit method

The sweep ran in three passes; each cited file was opened and read in
full.

```bash
# Pass 1 — enumerate every v2 file that emits Cypher
grep -rE 'session\.query|@Query|findByQuery' \
  backend/src/main/java/de/dlr/shepard/v2/ --include="*.java" -l

# Pass 2 — enumerate every RETURN-bearing line under v2
grep -rE 'RETURN ' backend/src/main/java/de/dlr/shepard/v2/ \
  --include="*.java"

# Pass 3 — for each DAO, locate its @NodeEntity and grep for @Relationship
for entity in $(grep -E 'class.*extends GenericDAO' \
                  backend/src/main/java/de/dlr/shepard/v2/**/daos/*.java); do
  ...
done
```

For each `RETURN` hit, the protocol was:

1. **Is the projection scalar (`AS x, AS y, …`) or entity-typed (bare
   `RETURN n`)?** Scalar projections cannot trigger the bug — they
   never construct an OGM-managed entity, just primitive values from
   the response map.
2. **For entity-typed projections, does the entity declare any
   `@Relationship(direction = INCOMING)`?** No INCOMING relationship
   = no back-walk possible = no NPE class.
3. **If yes, does the IO constructor or any service method call
   `getX()` where X is the back-referenced node?** This is the
   trigger. The NPE manifests only when populated graph data + IO
   serialisation collide.

The classes touched by raw `session.query(...).queryResults()` (e.g.
`NukeService`, `PermissionAuditService`,
`SemanticTermSearchRest`) were checked separately for scalar-vs-entity
shape: all three return primitive columns (`AS mongoId`, `AS uri`,
`AS total`), not OGM-hydrated nodes — safe by construction.

## §3 Findings table

The full v2 inventory of `RETURN`-bearing classes, classified:

| ID | File:line | Cypher shape | IO/service back-walk | Severity | Failure trigger |
|---|---|---|---|---|---|
| OGM-HYDRATE-001 | `v2/labjournal/daos/CollectionLabJournalEntriesDAO.java:81` | `RETURN lje, nodes(path), relationships(path)` (after fix) | `LabJournalEntryIO` → `getDataObject().getShepardId()` | FIXED (BUG-LJ-V1-COLL-ID 2026-05-24) | Was: populated `:DataObject -[has_labjournalentry]-> :LabJournalEntry` (LUMEN demo, MFFD-Dropbox once entries land) |
| OGM-HYDRATE-002 | `v2/timeseries/daos/TimeseriesAnnotationDAO.java:14-33` | `getReturnPart("a")` — canonical helper | None (entity has no `@Relationship`) | MINOR (DEFERRED) | None today; would fire if `TimeseriesAnnotation` ever adds an INCOMING `@Relationship` back to `TimeseriesReference` AND the helper call is replaced with `RETURN a` |
| OGM-HYDRATE-003 | `v2/collection/daos/CollectionContainersDAO.java:25` | `RETURN DISTINCT id(cont) AS neoId, cont.appId AS appId, cont.name AS name, … AS containerType` | None — projects directly into `ContainerSummaryIO` constructor with primitive columns | NONE | Scalar projection; OGM not involved |
| OGM-HYDRATE-004 | `v2/collectionwatchers/daos/CollectionWatcherDAO.java:26,39,52,63,73` | `RETURN w …` (entity-typed) | `CollectionWatcher` is flat (zero `@Relationship` fields); cross-entity links stored as `collectionAppId` string property | NONE | No INCOMING relationship to hydrate |
| OGM-HYDRATE-005 | `v2/watches/daos/WatchDAO.java:23,35,46` | `RETURN w …` (entity-typed) | `Watch` is flat (zero `@Relationship` fields); `collectionAppId` + `containerAppId` are plain string properties | NONE | Same |
| OGM-HYDRATE-006 | `v2/notifications/daos/NotificationDAO.java:33,81` | `RETURN n …` (entity-typed; `count(n)` scalar elsewhere) | `Notification` is flat; `targetUsername` is a string, not an edge | NONE | Same |
| OGM-HYDRATE-007 | `v2/timeseriescontainer/daos/TimeseriesContainerChartViewDAO.java:33` | `RETURN v LIMIT 1` | `TimeseriesContainerChartView` is flat; `containerAppId` is a string | NONE | Same |
| OGM-HYDRATE-008 | `v2/importer/daos/ImportPlanDAO.java:32,47` | `RETURN p …` (entity-typed); `RETURN count(d) AS cnt`, `RETURN d.name AS name` scalar elsewhere | `ImportPlan` is flat (HasAppId only); `collectionAppId` is a string | NONE | Same |
| OGM-HYDRATE-009 | `v2/admin/sqltimeseries/daos/SqlTimeseriesConfigDAO.java` | uses inherited `findAll()`; no custom Cypher | `SqlTimeseriesConfig` is flat (singleton config) | NONE | Same |
| OGM-HYDRATE-010 | `v2/admin/ror/daos/InstanceRorConfigDAO.java` | uses inherited `findAll()`; no custom Cypher | `InstanceRorConfig` is flat (singleton config) | NONE | Same |
| OGM-HYDRATE-011 | `v2/admin/services/NukeService.java:96,123,142` | `RETURN DISTINCT n.mongoId AS mongoId` / `RETURN n` (within `FOREACH ... DETACH DELETE`) / `RETURN total` | Service reads scalar `row.get("n")`, never reconstructs entities | NONE | Scalar/deletion projections |
| OGM-HYDRATE-012 | `v2/admin/services/PermissionAuditService.java:38` | `RETURN id(e) AS id, e.appId AS appId, e.name AS name, labels(e) AS labels` | Scalar map projection only | NONE | Scalar |
| OGM-HYDRATE-013 | `v2/semantic/resources/SemanticTermSearchRest.java:96,146` | `RETURN r.uri AS uri, …` | Scalar map projection only | NONE | Scalar |

Bonus / v1 sweep (per the prompt's "any v1 DAO that recently grew
bulk endpoints" instruction; bounded to files touched after
2026-05-01 in `git log`):

| ID | File:line | Cypher shape | IO/service back-walk | Severity | Failure trigger |
|---|---|---|---|---|---|
| OGM-HYDRATE-V1-001 | `context/labJournal/daos/LabJournalEntryRevisionDAO.java:32-41` | `RETURN r ORDER BY r.revisionNumber DESC` (leaf only) | `LabJournalEntryRevision.labJournalEntry` is `@Relationship(direction = INCOMING)`. No current caller reads it — the one consumer in `LabJournalEntryService.java:90` only calls `.size()` on the returned list. **No `LabJournalEntryRevisionIO` exists in the codebase yet.** | MAJOR (DEFERRED) | Fires the moment a Revision-list REST endpoint or `LabJournalEntryRevisionIO` ships and reads `revision.getLabJournalEntry().getShepardId()` for cross-linking |
| OGM-HYDRATE-V1-002 | `context/labJournal/daos/LabJournalEntryDAO.java:43-51` | `getReturnPart("e")` — canonical helper | Already safe (`LabJournalEntry.dataObject` INCOMING is hydrated by the helper's path projection) | NONE | None |

## §4 Confirmed vs deferred

### CONFIRMED (clear failure path; symptomatic on populated demo data)

None for v2 beyond the already-shipped BUG-LJ-V1-COLL-ID fix
(`OGM-HYDRATE-001`).

### DEFERRED (suspect shape; downstream tolerates NULL today)

- **OGM-HYDRATE-002** — `TimeseriesAnnotationDAO`. *Tightens the
  moment* the canonical `getReturnPart` helper call is rewritten to
  a bare `RETURN a` AND `TimeseriesAnnotation` grows an
  `@Relationship(direction = INCOMING)` back to `TimeseriesReference`
  (likely after TA1b's annotation-list-by-DataObject endpoint). The
  current code is correct; this row exists so a contributor doing
  a "simplify the Cypher" refactor doesn't accidentally regress it.
- **OGM-HYDRATE-V1-001** — `LabJournalEntryRevisionDAO.findByEntry`.
  *Tightens the moment* a revision-list endpoint exposes
  `LabJournalEntryRevisionIO` and that IO reads
  `revision.getLabJournalEntry().getShepardId()` for the
  cross-collection links the J1d design doc anticipates. The fix is
  trivial (drop in `CypherQueryHelper.getReturnPart("r")` after the
  ORDER BY). Filing now because the v2 LJE fix proved that DAO-side
  fixes lag IO-side requirements by ~one quarter and pile up under
  audit pressure.

### Out-of-scope but worth a follow-up sweep

- **Spring Data-style repositories** — none exist in v2 (the prompt
  hinted at `**/repositories/*.java`; the `find` returned zero hits).
  Worth a confirming pass when the I1 timeseries-index work lands,
  since that's the most likely surface to introduce them.
- **Service-layer composite Cypher** — three v2 services emit raw
  Cypher (`NukeService`, `PermissionAuditService`, semantic search).
  All three return scalar columns today; if any of them grows an
  "expand the matched node into an entity" branch, it needs the same
  audit.

## §5 Cross-references

- **NEO-AUDIT-2026-05-24-008** (`aidocs/agent-findings/neo4j-n10s-design-audit-2026-05-24.md:721-724`)
  — sibling defect class. NEO-008 catalogues a Cartesian OPTIONAL
  MATCH on `DataObjectDAO.java:480-506` + `CollectionDAO.java:120-121`
  where the OGM hydrates entity neighbours into a Cartesian product
  rather than the intended set. **Same root cause as this audit's
  family** — both are "OGM hydration semantics surprised the DAO
  author" — but the failure mode differs:
  - This audit (`OGM-HYDRATE`): missing INCOMING edge → NPE on
    `.get*()` back-walk.
  - NEO-008 (`OGM-CARTESIAN`): duplicated OPTIONAL MATCH branches →
    quadratic row count, then DISTINCT papering over it.
  No findings here overlap with NEO-008's two file:line citations
  (`DataObjectDAO:480-506`, `CollectionDAO:120-121`) — both are v1
  paths outside today's bonus window, and both already have NEO-008
  as their canonical backlog row.

- **NEO-AUDIT-2026-05-24** §"Linkage to existing backlog rows"
  (line 782-784) explicitly anticipates this audit: `"BUG-LJ-V1-COLL-ID
  + OGM-HYDRATION-AUDIT (queued/shipped) — same family of OGM-
  relationship-hydration shape as NEO-008's Cartesian OPTIONAL
  MATCH."` This findings doc is the closure of that anticipated work.

- **`CypherQueryHelper.getReturnPart`** at
  `backend/src/main/java/de/dlr/shepard/common/util/CypherQueryHelper.java:51-86`
  — the canonical fix shape; cited once per the audit constraints.

## §6 Recommended fix shape (for the v2-DAO contributor)

Use `CypherQueryHelper.getReturnPart(entity)` (or `getReturnPart(entity,
depth)`) for every Cypher `RETURN` that touches an entity-typed node
**whose `@NodeEntity` carries any `@Relationship` field — INCOMING or
OUTGOING — that the downstream IO or service might read**. The helper
projects `nodes(path), relationships(path)` alongside the leaf so the
OGM hydrates the depth-1 neighbourhood in both directions.

Quick discriminator: if the entity has *zero* `@Relationship` fields,
a bare `RETURN e` is fine (and faster — no path expansion). All seven
v2 entities currently in this category live in §3 rows 004-010.

Before / after example for a hypothetical new DAO that would
otherwise replicate the BUG-LJ-V1 trap:

```java
// BAD — leaks the BUG-LJ-V1 trap if the entity has any INCOMING relationship
String cypher =
    "MATCH (c:Collection {appId: $id})-[:has_dataobject]->(d:DataObject) " +
    "WHERE NOT d.deleted " +
    "RETURN d ORDER BY d.createdAt DESC";
return findByQuery(cypher, Map.of("id", appId));

// GOOD — depth-1 neighbourhood hydrates DataObject.collection,
// DataObject.parent, DataObject.references, etc.
String cypher =
    "MATCH (c:Collection {appId: $id})-[:has_dataobject]->(d:DataObject) " +
    "WHERE NOT d.deleted " +
    "WITH DISTINCT d " +
    CypherQueryHelper.getReturnPart("d") +
    " ORDER BY d.createdAt DESC";
return findByQuery(cypher, Map.of("id", appId));
```

(Note: when ordering, place the `WITH DISTINCT entity` between the
MATCH and the helper's `MATCH path=...` so the ORDER BY clause can
sit cleanly after the helper's `RETURN entity, nodes(path),
relationships(path)`.)

## §7 Backlog rows to file (NOT mutating `aidocs/16` directly)

To avoid merge conflict with the in-flight POSTGRES-MULTITENANT agent
that is also editing `aidocs/16-dispatcher-backlog.md`, the rows below
are listed here for the main session to file in canonical:

Suggested section header in `aidocs/16`:
```
## OGM-HYDRATE-2026-05-24-* — DAO sweep findings
```

| ID | File:line | Fix (one line) | Size | Status |
|---|---|---|---|---|
| OGM-HYDRATE-2026-05-24-001 | `backend/src/main/java/de/dlr/shepard/context/labJournal/daos/LabJournalEntryRevisionDAO.java:32-41` | Replace `RETURN r ORDER BY r.revisionNumber DESC` with `WITH r ORDER BY r.revisionNumber DESC ` then append `CypherQueryHelper.getReturnPart("r")`; same DAO grows a regression test via testcontainer fixture that creates a revision + reads `getLabJournalEntry().getShepardId()` | XS | queued |
| OGM-HYDRATE-2026-05-24-002 | `backend/src/main/java/de/dlr/shepard/v2/timeseries/daos/TimeseriesAnnotationDAO.java:14-33` | DOC-ONLY: add a `// HYDRATION-LOCK` comment above the helper call so a future "simplify Cypher" refactor doesn't drop to bare `RETURN a` | XS | queued |
| OGM-HYDRATE-2026-05-24-003 | repository-wide (Sonar/SpotBugs rule) | Author a custom SpotBugs rule + AST check: any Cypher string literal whose `RETURN` clause is a bare entity variable, AND the variable corresponds to a `@NodeEntity` with at least one `@Relationship` field, fails the build. Catches the next BUG-LJ-V1 at PR time, not at MFFD-import time. | M | queued |
| OGM-HYDRATE-2026-05-24-004 | `backend/src/test/java/.../OgmHydrationRegressionTest.java` (new) | Testcontainer-based integration test: seed `:Collection → :DataObject → :LabJournalEntry` + a `LabJournalEntryRevision`; call every audited DAO `find*` method; assert that every `@Relationship(direction=INCOMING)` field on the returned entity is non-null. Closes the gap the BUG-LJ-V1 fix left open (the existing unit tests pass with the broken Cypher because they don't seed the INCOMING edge). | S | queued |

The third row is the structural fix that prevents this class of bug
from recurring. If it ships, this audit doesn't need a re-run.

## §8 Honest limits

What this sweep did **not** check:

- **v1 DAOs that did not gain bulk endpoints after 2026-05-01**. The
  full v1 catalogue (10 files matched the initial `RETURN DISTINCT`
  grep) was not opened end-to-end. The bonus check covered
  `LabJournalEntryDAO` + `LabJournalEntryRevisionDAO` because both
  were touched in May; the other eight (`SnapshotDAO`,
  `PublicationDAO`, `ShepardTemplateDAO`, `ActivityDAO`,
  `TimeseriesContainerDAO`, `RoleDAO`, `CollectionPropertiesDAO`,
  `StructuredDataContainerDAO`) were not opened. Any of them could
  carry the same shape if their `RETURN`-leaf-only Cypher overlaps
  with an INCOMING `@Relationship` on the underlying entity. Suggest:
  filing OGM-HYDRATE-2026-05-24-005 to extend this sweep to the
  v1 surface, with the same severity criteria.
- **Spring Data repositories outside the OGM**. None were found in
  v2; not confirmed for v1.
- **Service-layer composite calls** — services that call multiple DAO
  methods + reconcile in Java. If a service stitches together two DAO
  returns and relies on `entityA.getEntityB()` being populated by the
  *first* call, the same defect class can manifest even if both
  individual DAOs project entities correctly. Not in scope here.
- **Testcontainer-based integration tests** that would have caught
  these had they seeded the right graph shapes. The existing
  `*DAOTest.java` files (per the git status of this branch — eight
  v1 DAO tests are modified in the working tree) were not opened.
  The structural fix is row OGM-HYDRATE-2026-05-24-004 above.
- **The Neo4j-OGM 4.x upgrade-path** itself. The hydration semantics
  observed are from the 4.x line; an upgrade to OGM 5.x (if it
  exists; not researched here) might change the rules. The
  `aidocs/34` ledger row for the next OGM bump should explicitly
  call out a re-audit obligation.

The sweep took ~30 minutes of file reads + verification; the v2
clean-bill is high-confidence (every `RETURN`-bearing v2 file was
opened in full) and the v1 result is necessarily partial.
