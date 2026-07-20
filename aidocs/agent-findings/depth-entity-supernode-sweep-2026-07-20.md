---
stage: feature-defined
last-stage-change: 2026-07-20
---

# DEPTH_ENTITY supernode sweep (2026-07-20)

Audit target: every Neo4j-OGM depth-1 / explicit-depth `session.load(...)` and
every hand-written `getReturnPart(... EVERYTHING)` (`path=(n)-[*0..1]-(n)
RETURN nodes(path), relationships(path)`) in
`backend/src/main/java/de/dlr/shepard/`, looking for the *fifth-and-beyond*
instances of the "depth-1 load on a supernode root" landmine already fixed 4×
this session (SingletonFileReferenceService, DataObjectDAO list, getDataObject
detail, UserService/ProvenanceCaptureFilter).

**OGM premise (confirmed against code + live DB).** A depth-1 OGM load, and the
`Neighborhood.EVERYTHING` return part (`CypherQueryHelper.getNeighborhoodPart`,
line 93: `path=(%s)-[*0..%d]-(n)`), both emit an **undirected** 1-hop match that
returns *every* relationship incident on the root node — **including edges the
root entity class does not even map** — then OGM discards what it can't bind. On
a high-degree root that is O(degree) wire + heap per call. This is exactly the
`getReturnPartForList` doc-comment's documented O(N²) `coerceCollection` spiral.

**Live degrees measured this session** (`infrastructure-neo4j-1`, Cypher-direct):

| Node | Supernode edge | Live degree |
|---|---|---|
| service `:User` (`7eead942…`) | `WAS_ASSOCIATED_WITH`/`created_by` (unmapped) | **3,934,213** |
| `:Version {name:"HEAD"}` | incoming `has_version` (unmapped on Version) | **25,101** |
| `:DataObject` "Tapelaying-…20260710b" | `has_reference` | **177,252** |
| `:Collection {name:"mffd-afp-tapelaying"}` | `has_dataobject` | **8,483** |
| `:SemanticRepository {type:INTERNAL}` | `PROPERTY_REPOSITORY`/`VALUE_REPOSITORY` | **0** (ruled out) |

## What I found

### RANKED TABLE OF FINDINGS

| # | file:line | entity loaded | supernode edge (live degree) | hot path? | severity | fix |
|---|---|---|---|---|---|---|
| F1 | `VersionDAO.java:27` (`find`), `:42`+`:48` (`findAllVersions`), `:62` (`findHEADVersion`) | `Version` | incoming `has_version` (**25,101** on HEAD) | GET collection versions/snapshots + version detail; `findAllVersions` re-drags 25k **per version** | **CRITICAL** | light Version load (`RETURN v` only / depth 0); Version's only needed rels are single `createdBy`+`predecessor` |
| F2 | `CollectionDAO.java:154` (`findByAppId(appId,username)`), `:49` (list) | `Collection` | `has_dataobject` (**8,483** on tapelaying col) + `has_version` | collection **detail page** load (very hot) | **HIGH** | Collection return-part that excludes `has_dataobject`/`has_version` (sibling of `getReturnPartForList`); DO list is already separately paginated via `findTopLevelByCollectionAppId` |
| F3 | `FileContainerDAO.java:80`+`:111`; `TimeseriesContainerDAO.java:79`+`:110`; `StructuredDataContainerDAO.java:79`+`:110` | `DataObject` (per linked DO) | `has_reference` (**177,252**) | container "referenced-by" panel (`findLinkedDataObjectsByAppId`/`…Paged`); unpaged loads ALL linked DOs each depth-1 | **HIGH** | `findLightByNeo4jId` (depth 0) — the referenced-by list needs only appId/name; `do.appId` is already in the projection row |
| F4 | `DataObjectDAO.java:194` (`listPredecessors`), `:216` (`listSuccessors`), `:238` (`listChildren`), `:44` (`findTopLevelByCollectionAppId`), `:98` (`findByCollectionByNeo4jIds`) | `DataObject` rows | `has_reference` (**177,252**) | lineage/prov graph, DO nav panels, top-level collection tree | **MEDIUM-HIGH** | swap `getReturnPart(...)` → existing `getReturnPartForList(...)` (the instance-#2 fix; these sibling nav methods were missed while `findByCollectionByShepardIds`@292/390 got it) |
| F5 | `DataObjectDAO.java:641` (`findByAppId` reload), `:620` (`findByShepardIdAtDepth`, **depth 2**, MCP `get_data_object` `CollectionMcpTools:222`) | `DataObject` | `has_reference` (**177,252**) | `findByAppId`: per typed-predecessor resolution on DO create/update (`DataObjectService:695/762`). depth-2 hydrates refs **and each ref's neighbors** | **MEDIUM** | `findByAppId`: scalar/light reload (only INCOMING `collection` edge is needed). `findByShepardIdAtDepth` distinct from & worse than filed GETDO-DETAIL-ON2 (that is depth-1 detail); resolve container hop via targeted Cypher not depth-2 |
| F6 | `GenericDAO.java:103` (`deleteByNeo4jId`, 2-arg load = depth 1); `DataObjectDAO.java:581` (`deleteDataObjectByNeo4jId`→`findByNeo4jId`) | `DataObject`/any | `has_reference` (**177,252**) | delete (cold) — hydrates 177k refs just to set `deleted=true`, which L591 then does via plain Cypher anyway | **LOW-MEDIUM** | fold `updatedBy`/`updatedAt`/`deleted` into the existing soft-delete Cypher; skip entity hydration |

### Call sites checked and RULED OUT (bounded degree — so the audit is trustworthy)

- **`SemanticRepository`** — `AnnotatableTimeseriesDAO.getSemanticRepositoryById:89`,
  `SemanticRepositoryDAO.findByAppId/findInternal/findAll` (all `getReturnPart`
  EVERYTHING), called per-annotation-write ×2. **Live fan-in = 0** — the INTERNAL
  repo carries no `PROPERTY_REPOSITORY`/`VALUE_REPOSITORY` edges because canonical
  SEMA-V6 annotations use string `propertyIRI`/`vocabularyId`, not edges.
  Structurally-possible supernode, **not present in this deployment**. Re-check
  only if the legacy `AnnotatableTimeseries` bridge path starts writing repository
  edges at volume.
- **`ApiKeyDAO.find:18`** (depth-1) — `ApiKey` root, belongs to one User; ~1 edge.
- **`PredicateDAO`/`VocabularyDAO` `loadAll(...,DEPTH_ENTITY)`** — `Predicate`/`Vocabulary`
  declare **no `@Relationship` fields**; annotations reference them by string, not
  edge. Zero fan-in.
- **`SemanticAnnotationDAO.findByAppId:54` + siblings** (OUTGOING/depth-1) — the
  annotation root is low-degree (1 incoming `has_annotation`, ≤2 outgoing repo);
  `loadAll` returns many but each is bounded.
- **All reference DAOs** (`FileBundleReferenceDAO`, `TimeseriesReferenceDAO`,
  `URIReferenceDAO`, `DataObjectReferenceDAO`, `StructuredDataReferenceDAO`,
  `CollectionReferenceDAO`, `BasicReferenceDAO`) `getReturnPart("r")` — Reference
  roots point to 1 container + 1 payload + 1 parent DO. Low-degree.
- **`NotificationTransportDAO.loadAll:34`** — transport-config nodes, bounded.
- **`UserGroupDAO.find:29`** (depth-1) — `belongs_to` fan-in = group membership; small.
- **`LabJournalEntryDAO.getReturnPart:48`** / **`AnnotatableTimeseriesDAO.findByTimeseries/findByAppId`** (loadAll depth-2) — bounded per-channel annotation fan-out.
- **`VersionService:65 userDAO.find(username)`** — an un-migrated *caller* of the
  known #4 `:User` supernode (3.93M), **not a new finding**; belongs to the #4
  family alongside global `getCurrentUser` + `tryBackfillLocalUser` L337 already flagged.

## Opportunities

- **One shared helper closes F2+F3+F4 at once.** `getReturnPartForList` already
  proves the "exclude the fan-out edge, keep everything else" shape. A
  `getReturnPartExcluding(entity, ...relTypes)` generalisation lets Collection
  exclude `has_dataobject`+`has_version`, DO-nav exclude `has_reference`, and the
  container linked-DO path drop to depth-0, from one reviewed primitive.
- **A degree-guard regression test.** Seed a DO with N references + a Version with
  N `has_version`, assert the detail/list/version endpoints issue a query whose
  row-count is O(1) not O(N) (count `nodes(path)` rows, or wrap the OGM session and
  assert no `has_reference`/`has_version` edge is returned). This is the
  testcontainer fixture the CLAUDE.md migration-test rule wants.

## Ideas

- OGM's undirected `[*0..1]` is the root cause across every finding; a lint rule
  (SpotBugs/ArchUnit) forbidding `getReturnPart(` on `Collection`/`Version`/`DataObject`
  variables — steering to the excluding variant or a `*Light` load — would stop
  the 5th→Nth recurrence structurally, the same way the frozen-`/shepard/api`
  reviewer rule stops v1 regressions.
- Version fan-in is unbounded by design (grows with every snapshot × every
  versionable entity). Consider never mapping/returning `has_version` from the
  Version side at all — it is unmapped already, so it is *pure* wire waste today.

## Real-world impact

- **F1 (Version) is the live production symptom.** `git log` shows
  `SNAPSHOT-ASYNC-CAPTURE` filed this session for *sync snapshot 502s on large
  collections* — a 25,101-edge HEAD Version being depth-1-loaded (and re-loaded
  per-version inside `findAllVersions`) is a direct mechanistic explanation for
  those 502s. This is the highest-confidence, highest-blast finding.
- **F3 blocks the container "referenced-by" panel** on exactly the containers that
  matter: the MFFD tapelaying/TPS containers whose linked DataObject holds 177,252
  references. One unpaged call materialises 177k reference rows into OGM to return
  a list of DO names.
- **F2** degrades the collection **detail page** for `mffd-afp-tapelaying` (8,483
  DataObjects dragged on every open) — the single most-visited surface in the app.

## Gaps & blockers

- Severity ranks are now grounded in **live** degrees, not code-comment guesses.
  The one caveat: degrees grow monotonically (User 2.87M→3.93M, Tapelaying DO
  102,953→177,252 since the last comment) — today's "bounded" ruled-out set
  (esp. `UserGroup`, `AnnotatableTimeseries` channels, the INTERNAL
  `SemanticRepository`) should be re-measured after any large ingest.
- I did **not** patch anything (discovery-only, per brief). F4's fix is mechanical
  (`getReturnPart`→`getReturnPartForList`) but each call site's IO must be checked
  to confirm it doesn't read `.references` directly off the returned DO.

## What surprised me

- **The `getReturnPartForList` doc-comment mis-generalises.** It states the
  Tapelaying collection "has only **2** DataObjects, so `Collection.dataObjects`
  cannot be the term." Live, `mffd-afp-tapelaying` has **8,483** `has_dataobject`
  edges — so `Collection` *is* a real supernode here (F2), just not the one that
  bit that particular 2-DO jstack. The comment's own reasoning, applied to live
  data, promotes Collection from "theoretical" to a genuine HIGH.
- **SemanticRepository was my strongest a-priori candidate and the DB killed it.**
  The mapped `PROPERTY_REPOSITORY`/`VALUE_REPOSITORY` edges I expected to fan into
  the shared INTERNAL repo simply **don't exist** (0) — the canonical store is
  string-keyed. Measuring saved a wrong CRITICAL ranking; a clean example of why
  the audit had to go substrate-direct.
- `findAllVersions` is a **double** supernode: the initial `EVERYTHING` query drags
  the 25k neighborhood, and then it loops calling `find(uid)` which drags it *again*
  per version. Nested O(versions × 25k).
