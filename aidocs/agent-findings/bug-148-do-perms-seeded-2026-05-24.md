---
stage: feature-defined
last-stage-change: 2026-05-24
---

# BUG-148 — DataObject Permissions seeding: WORKS AS DESIGNED

**Date:** 2026-05-24
**Agent:** worktree agent-a2e7156ba6fc39330
**Verdict:** The bug premise is wrong. Recommend closing BUG-148 (task #148) as **works-as-designed**.

## TL;DR

The task asked me to "seed a `:Permissions` record on DataObject creation because all subsequent writes return 403". The codebase says explicitly the opposite: **DataObjects do not have their own `:Permissions` node; access is inherited from the parent Collection.** Live reproduction confirms writes succeed immediately after creation (HTTP 200/201, zero 403s). Implementing the "fix" would contradict the established design and introduce a stale-cache hazard.

## Evidence

### 1. The codebase explicitly documents the design

`backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java` lines 269-275 (Javadoc on the 3-arg `isAccessTypeAllowedForUser` overload):

> *DataObjects do not have their own `:Permissions` node; access is inherited from the parent Collection. If the direct entity lookup returns null (no direct Permissions node), this overload falls back to the DataObject→Collection walk via `isAccessAllowedForDataObjectAppId` so the git plugin (and any other upstream-compiled class that calls this signature with a DataObject id) gets correct inheritance semantics without the git plugin needing to be recompiled.*

Lines 291-329 (`isAccessAllowedForDataObjectAppId`) implement the parent-Collection walk via a one-hop Cypher MATCH:

```cypher
MATCH (c:Collection)-[:has_dataobject]->(d:DataObject {appId: $appId})
RETURN id(c) AS collOgmId LIMIT 1
```

The Javadoc says: *"The v1 stack walks via the OGM-loaded `dataObject.getCollection()` inside services like `DataObjectService.getDataObject(...)`; the v2 stack needs this same walk at the resource boundary without loading the entire DataObject."*

### 2. Every write path uses Collection-level perms

Every `*Service` that mutates a DataObject (or anything beneath it — references, annotations) calls `collectionService.assertIsAllowedToEditCollection(collectionShepardId)`. Surveyed: 11 services (DataObjectService, DataObjectReferenceService, TimeseriesReferenceService, FileBundleReferenceService, BasicReferenceService, URIReferenceService, StructuredDataReferenceService, CollectionReferenceService, LabJournalEntryService, DataObjectSemanticAnnotationRest, CollectionSemanticAnnotationRest). None expect a DO-level `:Permissions` node.

### 3. Live reproduction — writes succeed immediately

Against `https://shepard-api.nuclide.systems`, as "Flo Researcher" (owner of collection 493423):

```
POST /shepard/api/collections/493423/dataObjects   → 201, DO 712272, appId 019e5a45-f714-7394-9af4-a1acb73c88fc
PUT  /shepard/api/collections/493423/dataObjects/712272      → 200 (immediate update)
POST /.../dataObjects/712272/semanticAnnotations   → 400 (body-shape error; auth PASSED)
```

A second probe (DO 712298) — same outcome. **Zero 403s.**

### 4. The graph confirms the inheritance model

Cypher on live nuclide Neo4j (2026-05-24):

| Label | Count | With `:has_permissions` |
|---|---|---|
| DataObject | 17,149 | **0** |
| BasicReference | 11,957 | **0** |
| Collection | 78 | 78 (100%) |
| StructuredDataContainer | 4,207 | 4,207 (100%) |
| FileContainer | 8 | 8 (100%) |
| TimeseriesContainer | 2 | 2 (100%) |
| FileGroup | 37 | 0 |
| SemanticRepository | 3 | 0 |

DataObjects categorically don't have direct Permissions. The freshly-created test DO (appId `019e5a46-b1e1-7914-9173-3f3c78d2f703`) → MATCH on `(do)-[r:has_permissions]->(p)` → `r=NULL, p=NULL`. The parent Collection 493423 → `MATCH (c)-[:has_permissions]->(p)` → `permissionType="Public", id(p)=493425`. Write authorisation resolves correctly via the walk.

### 5. The task's own evidence pointed to this

The task description noted: *"Not actively biting MFFD ingest right now (the backend logs sift earlier today confirmed zero 403s in the current window)"*. If the bug as described were real, every DO write since the bug landed would 403. The absence of 403s in the logs is consistent with the inheritance design working as intended, not with a latent bug.

## What the "fix" would have broken

Had I implemented the task as written (seed a Permissions node on every DO create):

1. **Two sources of truth** — every PATCH of a Collection's permissions would leave per-DO permissions stale unless cascaded explicitly. The `permissions-service-cache` (CompositeCacheKey by entityId) would happily serve a stale DO-level allow for a user who had just been revoked at the Collection level.
2. **Cache invalidation explosion** — `removeEntityFromCache(collectionId)` would have to walk + invalidate every child DO entry. Currently a one-line cache invalidation.
3. **17,149+ orphan Permissions nodes** — every existing DO would either need a backfill (sibling row in the task) or live with the mixed model (DO-level for new, walk-via-Collection for old).
4. **`isAccessAllowedForDataObjectAppId` would short-circuit incorrectly** — the fallback path at PermissionsService:284-289 only triggers when the direct lookup returns null. A per-DO Permissions node would always satisfy the direct lookup, bypassing the Collection walk. So a DO whose parent Collection's permissions were tightened would still grant access via the stale per-DO node.
5. **The PROV1a / F3 audit log row count** would multiply — every DO creation would emit a `GRANT` audit row, ~10–100× the current Collection-creation rate.

The plain reading of the codebase is that the task is premised on a wrong mental model.

## Inheritance decision

Not applicable — no code change shipped. The existing design (DO inherits Collection's Permissions via Cypher walk) is the answer to the "inherit vs own" question, and it is already in place.

## Where this premise likely came from

The task wording matches sub-bullet **(d)** in the `PERM-SYSTEM-REVIEW` row (aidocs/16-dispatcher-backlog.md line 1290):

> *(d) per task #148, DataObject creation doesn't seed a Permissions record at all, causing 403 cascades.*

That sub-bullet was filed as a hypothesis during the 2026-05-23 RoboDK live failure investigation. Sub-bullets (a), (b), (c) describe real symptoms (FileContainer perm-inheritance gap, missing DO permissions endpoint, opaque createFileReference errors); sub-bullet (d) appears to have been a leap from "we see 403 cascades somewhere in the RoboDK FC flow" to "DOs must be missing Permissions". The actual culprit is almost certainly sub-bullet (a): **FileContainers default to `Private` regardless of parent Collection's `Public` setting** — that's the inheritance gap that triggers RoboDK 403s, not anything DO-related.

## Recommendations

1. **Close BUG-148 / task #148** as works-as-designed. The "DO creation doesn't seed Permissions" framing is incorrect.
2. **Reframe sub-bullet (d) in PERM-SYSTEM-REVIEW (aidocs/16 line 1290)** to:
   *"(d) DO-level write authorisation resolves correctly via the documented parent-Collection walk (`PermissionsService.isAccessAllowedForDataObjectAppId`, lines 291-329). Per-DO Permissions nodes would contradict the design. The RoboDK 403 cascades are downstream of sub-bullet (a) — FileContainer creation not inheriting parent Collection's permissions."*
3. **The real follow-up** is the FileContainer-inherits-Collection gap (sub-bullet a). That's a separate fix: when `FileContainerService.createContainer(...)` runs inside a Collection context, the new FC's `:Permissions` node should default-copy (or symlink-via-walk) the Collection's owner + permissionType, instead of always seeding `Private` with the caller as sole owner.
4. **No BUG-148-BACKFILL** is needed — there's nothing to backfill. The 17,149 DOs without `:Permissions` are correct.
5. **PROV note for future PRs touching `PermissionsService`**: lines 269-329 are load-bearing for the inheritance contract. Adding a per-DO Permissions node *anywhere* in the code (DataObjectService.createDataObject, an import path, a template instantiation) would silently break the inheritance walk's null-check at line 278. Treat that null-check as the design assertion.

## Test results

No code change shipped → no unit/integration test added. The existing assertion that BUG-148-shaped fix should NOT land is:
- `PermissionsServiceTest` already tests the parent-Collection walk via `isAccessAllowedForDataObjectAppId`.
- The 17,149 zero-Permissions DOs in the live graph are the empirical regression test.

If we wanted to **lock the design assertion into a test**, the right shape is:

```java
@Test
void dataObjectCreationDoesNotSeedOwnPermissions() {
  long collId = createTestCollection();
  long doId = dataObjectService.createDataObject(collId, dataObjectIO("test")).getId();
  // Inheritance contract: no direct Permissions node on a fresh DO.
  assertThat(permissionsDAO.findByEntityNeo4jId(doId)).isNull();
  // But the walk-through-Collection check still grants write to the creator.
  assertThat(permissionsService.isAccessTypeAllowedForUser(doId, AccessType.Write, currentUser))
    .isTrue();
}
```

That assertion would be a useful regression-anti-test for the next agent who tries to "fix" BUG-148. Filing as a new backlog row would be cleaner than adding it under the closed BUG-148.

## What surprised me

- **The V14 migration's WHERE clause (`(e:BasicEntity) WHERE NOT (e)-[:has_permissions]->(:Permissions)`) would technically have backfilled DataObjects** if `shepard.permissions.default-owner` had been configured. The clause doesn't filter by label. The fact that all 17,149 live DOs lack `:Permissions` means either (1) the live deploy never had the config set, OR (2) all current DOs were created post-V14. Either way, the design comment in `PermissionsService` is the source of truth — V14's WHERE clause is too broad and should probably be tightened to `WHERE e:Collection OR e:StructuredDataContainer OR e:FileContainer OR e:TimeseriesContainer` to make the design intent explicit. Filing as a sibling row.
- The 3-arg `isAccessTypeAllowedForUser` overload (lines 276-289) was added specifically for *upstream-compiled classes (git plugin)* that call with a DataObject id and can't be recompiled to know the inheritance contract. This is a nice piece of backward-compat plumbing — the fallback walk is invisible to the caller. Worth documenting in the plugin SPI reference page.
- The "BUG-148" label in the task list isn't an aidocs/16 row ID — it's a reference to GH Issue #148 *which is unrelated* (titled "Rethink incoming IDs", migrated from GitLab). The sub-bullet in PERM-SYSTEM-REVIEW row 1290 invented the bug shape ad-hoc. The taxonomy is brittle here — task-list IDs colliding with GH Issue numbers from a 2024 GitLab migration is a footgun.
