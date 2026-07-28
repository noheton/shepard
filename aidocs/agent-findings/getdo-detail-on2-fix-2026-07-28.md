---
stage: deployed
last-stage-change: 2026-07-28
---

# GETDO-DETAIL-ON2 / GETDO-DETAIL-TARGETED — DataObject detail read on the Tapelaying supernode

Author: backend-performance agent (worktree `worktree-agent-a36c3c80fb2ef81c2`)
Date: 2026-07-28

## What I found

**The headline: GETDO-DETAIL-ON2 was already shipped and merged.** Commit
`34a5f47a2` (2026-07-20, an ancestor of this worktree's HEAD `7d696061d`) already
fixed the O(K²) OGM `coerceCollection` spiral that made the Tapelaying DO's detail
page unopenable. The backlog row is marked **shipped**. The task's premise ("the
detail page is currently unopenable") is stale — the critical bug is gone.

Verified the ON2 fix is correct and fully wired end-to-end:
- `CypherQueryHelper.getReturnPartForDetail` — excludes the `has_reference` fan-out.
- `VersionableEntityDAO.findByShepardIdForDetail` + `DataObjectService.getDataObjectForDetail(collectionShepardId, shepardId, versionUID, reconstructReferences)`.
- v1 `DataObjectRest.getDataObject` → `getDataObjectForDetail(..., true)` (byte-compat: reconstructs `referenceIds[]`+counts via scalar `labels(r)` projection).
- v2 `DataObjectV2Rest` detail → `getDataObjectForDetail(..., false)` (`@JsonIgnore`s the legacy fields, sources refs from bounded `findContainersByDataObjectAppId`).

**The genuine remaining work — GETDO-DETAIL-TARGETED (queued, MINOR) — is what I
implemented.** ON2 shipped the *safe negative form*
(`(o)-[*0..1]-(n) WHERE NONE(rel WHERE type(rel)='has_reference')`). That form has no
relationship-type restriction in the pattern, so Neo4j's `VarLengthExpand` enumerates
**every** incident edge — including all 258,751 `has_reference` edges on the Tapelaying
DO — then discards them in the post-filter. O(K) db-hits per detail open. Sub-second,
but real load under concurrency, and the graph grew 178k → 258k since ON2 shipped.

The Tapelaying supernode is bigger than the docs said:
- `Tapelaying-tapelaying-20260710b` (shepardId 2438465, appId `019f7186-…`) holds
  **258,751** `has_reference` edges (was 178k at ON2 time).

## Opportunities

**The fix (GETDO-DETAIL-TARGETED): push the wanted edge types into the pattern.**
Changed `getReturnPartForDetail` from the negative filter to a *positive edge-type
allowlist*:

```
(o)-[:has_dataobject|has_successor|has_child|points_to|has_labjournalentry
     |has_annotation|has_version|created_by|updated_by*0..1]-(n)
```

This is the DataObject class hierarchy's **entire declared `@Relationship` set minus
`has_reference`** — enumerated authoritatively from the entity classes:
- `DataObject`: has_dataobject (collection), has_successor (successors+predecessors), has_child (children+parent), points_to (incoming), has_labjournalentry, **has_reference (excluded)**
- `BasicEntity`: has_annotation
- `VersionableEntity`: has_version
- `AbstractEntity`: created_by, updated_by

With the type filter in the pattern, Neo4j's expand skips the dense-node
`has_reference` relationship group entirely instead of walking it to discard it.

### PROFILE-verified db-hits (live neo4j, Tapelaying DO @ 258,751 refs)

| Query | Negative (shipped ON2) | Positive (this fix) | Reduction |
|---|---|---|---|
| Neighborhood only (indexed appId seed) | **259,406** | **599** | 99.8% |
| Full detail query (`findByShepardIdForDetail` shape, shepardId seed) | **309,839** | **51,030** | 83.5% |

**Confirmed O(1) in reference degree:** the positive neighborhood is 599 db-hits flat
across both the 637-ref `P02Strich_S_2teBahn` DO and the 258k-ref Tapelaying DO; the
negative form scaled 21,786 → 259,406 across the same two.

### Byte-compat equivalence — how it's established (and what was NOT run)

The equivalence rests on two facts, not on the serialized response having been
diffed:
1. **Neo4j-OGM maps only relationship types that correspond to a declared
   `@Relationship` field.** Returning exactly the declared types (minus
   `has_reference`) therefore hydrates an identical entity.
2. **Live edge-type diff on the Tapelaying DO:** the negative form additionally
   returned **`created_in_month`** (NEO-AUDIT-004 time-bucket index edge from
   `:User`) and **`has_permissions`** — both **OGM-unmapped on DataObject** (no
   `@Relationship` field for either anywhere in the class chain), so dropping them
   changes nothing in the mapped entity. Every mapped edge (collection, version,
   createdBy, successors — and the children/predecessors/incoming/annotations/
   labjournal/updatedBy types absent on this particular DO but present in the
   allowlist) is preserved.

**Honest scope caveat:** what was verified is the *edge types the Cypher surfaces*
plus OGM's declared-mapping property — NOT the serialized `DataObjectIO` bytes. The
`CypherQueryHelperTest` asserts the query *string*; the 3 container DAO tests assert
the query *omits* `has_reference`; neither asserts the response body is unchanged.
The direct v1 byte-compat guard is `DataObjectV5WireFidelityIT`, which **did not run**
(testcontainer Neo4j auth lockout — pre-existing in the worktree, unrelated to this
Cypher-string change). That IT still owes a run to close the loop empirically; the
argument above is why the change is safe in the meantime.

## Ideas

- **`DataObject.shepardId` has no index** (filed GETDO-DETAIL-SHEPARDID-INDEX). The
  full detail query's residual 51k db-hits is *entirely* the seed:
  `WHERE o.shepardId=$id` scans all 13,628 `:DataObject` via `idx_DataObject_deleted`.
  Seeding on the indexed `appId` drops the neighborhood to 599. A
  `RANGE INDEX … ON (d.shepardId)` would take the full query to ~600 db-hits — but it
  shifts the planner for every `findByShepardId*` path, so it deserves its own PROFILE
  sweep + migration, not a ride-along here.
- The positive-allowlist pattern is the right shape for the sibling helpers too
  (`getReturnPartForList`, `getReturnPartForCollectionDetail` still use negative
  `NONE(...)` filters). Same O(K)→O(1) win is available there if they run on
  supernodes; deferred (not in scope, and the collection-detail edge set differs).

## Real-world impact

Opening the Tapelaying DO detail page now costs ~51k db-hits (dominated by the
un-indexed seed scan) instead of ~310k, and the neighborhood component is flat at ~600
regardless of how many files the DO accrues. As the MFFD dropbox keeps ingesting
(178k → 258k in ~8 days), the ON2 negative form's cost grew linearly with every
uploaded file; the positive form decouples detail-open cost from ingest volume. This
matters for the LARGE-DATA-K6-COVERAGE load-test concern the ON2 commit flagged.

## Gaps & blockers

- `getReturnPartForDetail` is **DataObject-specific** (all 4 callers load a DataObject
  node: `VersionableEntityDAO.findByShepardIdForDetail`, which is only wired for
  DataObject detail, and the 3 container DAOs' `loadLinkedDataObjectForPanel`). The
  positive allowlist would silently drop edges if reused for a non-DataObject entity.
  Documented loudly in the javadoc; `findByShepardIdForDetail` has one caller today.
  A future entity added to detail-load through this helper must extend the allowlist.
- The shepardId-index optimization (GETDO-DETAIL-SHEPARDID-INDEX) is the bigger
  remaining win on the *full* query but has wide blast radius — left as a separate row.
- **`DataObjectV5WireFidelityIT` (the direct v1 serialized-response guard) did not
  run** — the whole `*IT` suite errored on a Neo4j testcontainer auth lockout
  (`SecurityException: incorrect authentication details too many times in a row`),
  pre-existing in the worktree and unrelated to a Cypher-string change (unit tests:
  5839, 0F/0E). The next CI run with a healthy testcontainer closes this loop.

## What surprised me

- **The bug was already fixed.** Six instances of this landmine have been fixed; ON2
  was the sixth, and it shipped 8 days before this task was cut. Reusing the shipped
  scaffolding (per "reuse before reimplement") meant the actual deliverable was the
  queued *optimization*, not the fix.
- **The negative and positive forms cost the same (~21k) on a small DO** in the
  isolated neighborhood test — which is what exposed that the ~21k floor is the
  un-indexed shepardId scan, not the fan-out. The fan-out only shows up as the
  *difference* between the two forms at scale (259,406 vs 599).
- **DataObjects carry `has_permissions` edges** in the live graph even though the v2
  code comments say "DataObjects don't have their own :Permissions node" — but OGM
  never maps them (no field), so they're inert either way. A reminder that
  graph-incident edges ≠ OGM-mapped edges, which is the whole basis of the equivalence.
