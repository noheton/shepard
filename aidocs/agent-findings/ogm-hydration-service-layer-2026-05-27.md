---
stage: audited-by-personas
last-stage-change: 2026-05-27
purpose: service-layer composite-call hydration audit — OGM-HYDRATE-2026-05-24-004
---

# OGM-HYDRATE-2026-05-24-004 — Service-layer composite-call hydration audit (2026-05-27)

## What I found

### Scope and methodology

The audit covered all service classes under:
- `backend/src/main/java/de/dlr/shepard/v2/**/services/` (86 files)
- `backend/src/main/java/de/dlr/shepard/**/services/` (legacy v1, ~55 files)

The task defined the defect class as: a service method calls a DAO/repository to
fetch an entity (single return), then in the **same method** walks
`.getSomething()` or `.getX().getY()` on the returned entity, where
`getSomething()` is a field annotated with `@Relationship` (particularly
`direction = INCOMING`) in the entity class, and the DAO query does not hydrate
that field.

#### Critical disambiguation established during the audit

The BUG-LJ-V1-COLL-ID defect class **only fires from `session.query(type, cypher, params)`
when the Cypher projection is `RETURN entityVar` without a depth-1 path expansion**.
Neo4j-OGM 4.x processes that shape differently from `session.load()`:

| Fetch path | OUTGOING @Relationship | INCOMING @Relationship |
|---|---|---|
| `session.load(type, id, depth=1)` (`findByNeo4jId`) | hydrated | **hydrated** |
| `session.query(type, cypher)` with `RETURN e, nodes(path), relationships(path)` (`getReturnPart`) | hydrated | **hydrated** |
| `session.query(type, cypher)` with bare `RETURN e` | hydrated | **null** (BUG-LJ-V1 class) |

This discrimination is the single key fact for the entire audit. Once established,
most service methods resolve cleanly:

- **All `findByNeo4jId` calls** (`GenericDAO.findByNeo4jId` → `session.load(type, id, 1)`)
  hydrate BOTH directions. No INCOMING field can be null via this path.
- **All `VersionableEntityDAO.findByShepardId` calls** use `getReturnPart("o")` =
  `MATCH path=(o)-[*0..1]-(n) WHERE n.deleted=FALSE ... RETURN o, nodes(path), relationships(path)`.
  This is `Neighborhood.EVERYTHING` — both directions hydrated.
- **All `getReturnPart(entity)` DAO methods** are safe by construction.

#### Entities with INCOMING @Relationship fields (the risk surface)

| Entity | INCOMING field | Direction annotation |
|---|---|---|
| `DataObject` | `collection` (Collection) | `@Relationship(HAS_DATAOBJECT, INCOMING)` |
| `DataObject` | `predecessors` (List<DataObject>) | `@Relationship(HAS_SUCCESSOR, INCOMING)` |
| `DataObject` | `parent` (DataObject) | `@Relationship(HAS_CHILD, INCOMING)` |
| `DataObject` | `incoming` (List<DataObjectReference>) | `@Relationship(POINTS_TO, INCOMING)` |
| `BasicReference` (and all subclasses) | `dataObject` (DataObject) | `@Relationship(HAS_REFERENCE, INCOMING)` |
| `LabJournalEntry` | `dataObject` (DataObject) | `@Relationship(HAS_LABJOURNAL_ENTRY, INCOMING)` |
| `LabJournalEntryRevision` | `labJournalEntry` (LabJournalEntry) | `@Relationship(has_lab_journal_revision, INCOMING)` |
| `FileContainer` | `collectionList` (List<Collection>) | `@Relationship(HAS_DEFAULT_FILE_CONTAINER, INCOMING)` |
| `Permissions` | (the entity itself) | `@Relationship(HAS_PERMISSIONS, INCOMING)` |
| `User` | `apiKeys` (List<ApiKey>) | `@Relationship(BELONGS_TO, INCOMING)` |
| `User` | `subscriptions` (List<Subscription>) | `@Relationship(SUBSCRIBED_BY, INCOMING)` |
| `UserGroup` | `users` (List<User>) | `@Relationship(IS_IN_GROUP, INCOMING)` |

Services that call DAO methods on any of these entities and then dereference
their INCOMING fields were examined for under-hydration.

---

## RISK findings

**Zero confirmed CRITICAL or MAJOR service-layer hydration defects were found.**

The result mirrors the v2 DAO audit (2026-05-24) — the codebase's structural
patterns protect against the BUG-LJ-V1 class at the service layer:

1. Every service that fetches a single entity by ID either uses `findByNeo4jId`
   (`session.load`, depth=1, both-direction) or `findByShepardId` (via
   `VersionableEntityDAO`, `EVERYTHING` neighborhood). Neither path leaves
   INCOMING fields null.
2. The v2 services that do emit `session.query` with bare `RETURN e` (specifically
   `SnapshotDAO.findByAppId` returning `RETURN s`) only access OUTGOING
   `@Relationship` fields on the returned entity (`Snapshot.collection` is
   `SNAPSHOT_OF` — OUTGOING; OUTGOING fields are hydrated even by bare RETURN).

The one DEFERRED suspect carried forward from the previous audit
(OGM-HYDRATE-V1-001, `LabJournalEntryRevisionDAO.findByEntry`) was re-confirmed:
no current service caller reads `revision.getLabJournalEntry()` — only `.size()`
is called on the returned list. The deferred status is correct; it only fires
when a revision-list IO is added.

---

## Clean sites

These sites examined explicitly and confirmed safe:

### 1. `LabJournalEntryService.getCollectionId()` (lines 161–171)

```java
LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
// ...
DataObject dataObject = dataObjectService.getDataObject(labJournalEntry.getDataObject().getId());
```

- `findByNeo4jId` → `session.load(type, id, 1)` → both directions hydrated.
- `LabJournalEntry.dataObject` is `@Relationship(INCOMING)`.
- `session.load(depth=1)` hydrates it — safe.
- The subsequent `.getDataObject().getId()` call is valid.

This is the CLOSEST surface to the BUG-LJ-V1 class (same entity, same INCOMING
field), but the fetch path is `session.load` not `session.query` + bare RETURN.
The fix that shipped (BUG-LJ-V1-COLL-ID) corrected `CollectionLabJournalEntriesDAO`
which used `session.query` + bare RETURN. This service uses `findByNeo4jId`
which does not have the defect.

### 2. `DataObjectService.findRelatedDataObject()` (lines 609–619)

```java
var dataObject = dataObjectDAO.findByShepardId(referencedShepardId);
// ...
if (!dataObject.getCollection().getShepardId().equals(collectionShepardId))
```

`DataObjectDAO.findByShepardId` inherits from `VersionableEntityDAO.findByShepardId`
which uses `getReturnPart("o")` = `EVERYTHING` neighborhood. `DataObject.collection`
(INCOMING `HAS_DATAOBJECT`) is hydrated. Safe.

### 3. All reference services: `BasicReferenceService`, `URIReferenceService`, `TimeseriesReferenceService`, `StructuredDataReferenceService`, `DataObjectReferenceService`, `CollectionReferenceService`, `FileBundleReferenceService`

All follow the same pattern:
- `getReference()` calls `VersionableEntityDAO.findByShepardId()` → `EVERYTHING` neighborhood
- Then accesses `reference.getDataObject().getShepardId()` (INCOMING `HAS_REFERENCE`)
- Hydrated because `EVERYTHING` = both directions. Safe.

### 4. `ReferenceSearchService.search()` (lines 87–88)

```java
references[i].getDataObject().getCollection().getShepardId()
references[i].getDataObject().getShepardId()
```

A depth-2 chain on `BasicReference`. The `BasicReference` entities arrive via
`SearchDAO.findReferences()` which appends:
```
MATCH path=(c:Collection)-[]->(d:DataObject)-[]->(r)-[]->(u:User)
RETURN r, nodes(path), relationships(path)
```
This explicit path traversal walks THROUGH the DataObject and Collection, so
`r.dataObject` and `r.dataObject.collection` are both materialized. Safe.

### 5. `FileContainerService.getContainer()` (line 110)

```java
fileContainer.setCollectionList(fileContainer.getCollectionList().stream().filter(...))
```

`fileContainerDAO.findByNeo4jId(id)` → `session.load(depth=1)` → both directions hydrated.
`FileContainer.collectionList` is `@Relationship(HAS_DEFAULT_FILE_CONTAINER, INCOMING)`.
Session.load depth=1 hydrates it. Safe.

### 6. `SnapshotService.resolveCollection()` (line 275) + `SnapshotPinnedReadRest.checkCollection()` (line 122)

`SnapshotDAO.findByAppId` uses bare `RETURN s`. The callers access
`snapshot.getCollection()` which is `@Relationship(SNAPSHOT_OF)` — **OUTGOING,
no direction specified (defaults to OUTGOING)**. OUTGOING fields ARE hydrated by
bare `RETURN s`. Safe.

No caller reads any INCOMING field on a `Snapshot` entity returned via bare RETURN.

### 7. `SingletonFileReferenceDAO.findByAppId()` (used in `FileReferenceV2Rest.checkAccess()`)

Uses a hand-rolled `OPTIONAL MATCH (d:DataObject)-[hr:has_reference]->(r) RETURN r, f, d, hr`.
The explicit `d` and `hr` columns in the return clause cause OGM to materialize
the INCOMING `has_reference` edge into `FileReference.dataObject`. Safe.

---

## Recommendations

### For the live codebase

No code changes are required. All examined service-layer composite call sites
are safe under current DAO fetch semantics.

### Structural safeguard (carry-forward from OGM-HYDRATE-2026-05-24-003)

The most durable fix is the **custom SpotBugs/AST rule** (already filed as
OGM-HYDRATE-2026-05-24-003): fail the build on any Cypher string literal whose
`RETURN` clause names a bare entity variable AND the variable's class has at
least one `@Relationship(direction = INCOMING)` field. This would catch future
regressions at PR time rather than at MFFD-demo time.

### For OGM-HYDRATE-V1-001 (LabJournalEntryRevisionDAO)

The deferred fix remains the correct approach. The risk tightens when the J1d
revision-list REST endpoint grows an IO class that reads
`revision.getLabJournalEntry().getShepardId()`. At that point, replace:
```java
"RETURN r ORDER BY r.revisionNumber DESC"
```
with:
```java
"WITH r ORDER BY r.revisionNumber DESC " + CypherQueryHelper.getReturnPart("r")
```

### Documentation annotation for future contributors

`LabJournalEntryService.getCollectionId()` is visually indistinguishable from
the BUG-LJ-V1 pattern. Add a comment citing why it is safe:

```java
// Safe: findByNeo4jId → session.load(depth=1) which hydrates BOTH directions
// (including the INCOMING has_labjournalentry edge back to DataObject).
// The BUG-LJ-V1 class only fires from session.query() with bare RETURN projections.
// DO NOT replace findByNeo4jId with a custom session.query("... RETURN lje")
// unless the query uses CypherQueryHelper.getReturnPart("lje").
LabJournalEntry labJournalEntry = labJournalEntryDAO.findByNeo4jId(labJournalEntryId);
```

---

## What surprised me

### 1. `session.load` vs `session.query` is the entire story

The audit resolved almost immediately once the `session.load(depth=1)`
bidirectionality was confirmed. The entire service layer is structurally safe
because service methods almost universally call `findByNeo4jId` (→ `session.load`)
or `VersionableEntityDAO.findByShepardId` (→ custom query with `getReturnPart`).
The codebase has effectively two safe paths and uses them consistently.

### 2. The only bare `RETURN e` in a service (SnapshotDAO) hits no INCOMING field

`SnapshotDAO.findByAppId` (bare `RETURN s`) is the only place a service-level
call uses the defect-triggering query shape. But `Snapshot` has no INCOMING
`@Relationship` fields — only OUTGOING ones. The defect class cannot trigger
here by construction.

### 3. `ReferenceSearchService` has the most complex hydration path

The depth-2 chain `reference.getDataObject().getCollection()` in
`ReferenceSearchService` is the most complex dereference in the codebase, but it
is also the most carefully hydrated — `SearchDAO.emitReferencesReturnPart`
explicitly walks `(c:Collection)-[]->(d:DataObject)-[]->(ref)-[]->(u:User)` so
all three levels are materialized. A future maintainer who "simplifies" that
Cypher to a shorter match would break this path silently.

### 4. The previous DAO audit predicted this result

The 2026-05-24 audit noted: "Service-layer composite calls — services that call
multiple DAO methods + reconcile in Java. If a service stitches together two DAO
returns and relies on `entityA.getEntityB()` being populated by the first call,
the same defect class can manifest." This audit confirms: no such broken
stitching exists today because the `session.load` vs `session.query` distinction
protects all `findByNeo4jId` call sites.

---

## Cross-references

- `aidocs/agent-findings/ogm-hydration-audit-2026-05-24.md` — v2 DAO sweep (parent audit)
- `aidocs/agent-findings/bug-lj-v1-coll-id-fix-2026-05-24.md` — the canonical BUG-LJ-V1 fix
- `aidocs/16-dispatcher-backlog.md` §OGM-HYDRATE-2026-05-24-* — backlog rows
- `backend/src/main/java/de/dlr/shepard/common/util/CypherQueryHelper.java:51–86` — the canonical fix shape
- `backend/src/main/java/de/dlr/shepard/common/neo4j/daos/GenericDAO.java:findByNeo4jId` — safe fetch path
