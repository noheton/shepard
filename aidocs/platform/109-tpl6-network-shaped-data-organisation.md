---
stage: feature-defined
last-stage-change: 2026-05-26
---

# 109 — TPL6: Network-shaped data organisation
## Multi-parent membership + saved views + knowledge-graph navigation

**Status.** Design complete. Phasing: TPL6a (S) → TPL6b (M) → TPL6c/d (L).
**Audience.** Backend contributors, frontend contributors, MFFD integration engineers.
**Sprint backlog reference.** Task #88 (`aidocs/16-dispatcher-backlog.md` rows
TPL6a–TPL6d, §15 of `aidocs/semantics/95-shacl-templates-and-individuals.md`).
**File path note.** The task brief targeted `aidocs/platform/106-…` but `106` is
already occupied by the requirements-traceability doc (`aidocs/platform/106-requirements-traceability.md`,
stage `idea`, 2026-05-22). Per the SSOT-per-concept rule (numeric prefixes unique,
`feedback_ssot_per_concept.md`), this doc lands at `109`.
**Related docs.**
- `aidocs/semantics/95-shacl-templates-and-individuals.md` §14d + §15 — TPL6 M3
  milestone summary; this doc is the full design elaboration.
- `aidocs/platform/25-neo4j-id-migration-design.md` — `appId` UUID v7, the
  stable identity substrate every new entity here uses.
- `aidocs/semantics/100-consistent-semantic-annotation-design.md` — SEMA-V6;
  `:SemanticAnnotation` is a companion annotation surface on the same entities.
- `aidocs/platform/83-tpl1-tpl2-shapes-templates-views.md` — M1 shapes tracker;
  TPL6 is M3 (lands after M1/M2, independently).
- `aidocs/41-synergy-sweep.md` §2 — saved network views (TPL6b) synergy note.

---

## §1 — Problem statement

### §1.1 The single-parent tree

Shepard's DataObject graph is today a **rooted tree per Collection**: each
DataObject has at most one parent (via the `has_child` / `has_child` edge pair
in `DataObject.java` backed by `Constants.HAS_CHILD`). The sidebar
(`CollectionSidebar.vue`) renders this tree with lazy-loaded `v-treeview`.

A tree is the right default for simple campaign hierarchies (LUMEN: Campaign →
Test Runs → TR-004-investigation). It breaks for real industrial process graphs
where a single artefact is legitimately consumed or produced by more than one
process chain:

**MFFD bridge-welding scenario.** The MFFD upper fuselage process has two
parallel skin tracks (Q1 — starboard, Q2 — port). Both tracks converge at
bridge-welding, which is a shared process step. A DataObject representing the
bridge-welding run belongs, by domain semantics, to both the Q1 AFP chain *and*
the frame-welding branch. Today a researcher must either:

(a) duplicate the DataObject (losing provenance integrity — two records for one
physical event), or

(b) arbitrarily pick one parent and leave a free-text annotation pointing to the
other (implicit, un-queryable, invisible in the graph view).

**Calibration certificate shared across runs.** A calibration record for the
AFP robot is valid for a batch of layup DataObjects. Today: either duplicate
into every DO, or store it once and link via a structured-data container (works,
but the lineage graph does not show it as a structural relationship).

**Timeseries shared across experiments.** Two DataObjects from different sub-campaigns
both reference the same TimeseriesContainer (shared fixture). The container is
in the graph — but a researcher browsing the Q1 subtree cannot discover it
belongs to Q2 as well without going back to the container-level list.

### §1.2 What researchers want

Three concrete asks heard during MFFD integration work:

1. "I need this DataObject to appear in two places in the sidebar."
2. "I want to filter the collection down to only the AFP layup steps, then save
   that view so I can come back to it."
3. "I want to see the actual network — not just a tree — when I'm debugging a
   process anomaly."

---

## §2 — Design options

### Option A — Multi-parent edges (secondary `has_child`)

**Shape.** Add a second Neo4j relationship type `has_secondary_child`
(mirroring `has_child` but with distinct semantics). A DataObject retains its
one **primary parent** (the existing `has_child` edge); additional parents use
`has_secondary_child`. The sidebar shows the primary-parent path with "also in:
[secondary parent names]" chips below the node.

**Migration.** Additive: index only, no data backfill. One Cypher migration
(`V71__Add_secondary_child_relationship_index.cypher`).

**REST surface.** Two new sub-resource endpoints on the existing `/v2/collections/
{collectionAppId}/data-objects/{dataObjectAppId}` shelf:
`POST .../secondary-parents` to attach, `DELETE .../secondary-parents/
{parentAppId}` to detach. `GET .../…/{dataObjectAppId}` response extended with
`secondaryParentAppIds: []`.

**Permissions.** A DataObject inherits permissions from its primary Collection
(existing behaviour — `PermissionsService.isAccessAllowedForDataObjectAppId`
walks to primary parent Collection). Secondary parents live in the same Collection
(TPL6a scopes secondary-parent relationships to DataObjects within the same
Collection). Cross-Collection secondary parents are a TPL6b extension point —
deferred, because they require a permissions model update (the secondary parent's
Collection would also need to grant Read on the DO).

**Provenance.** `ProvenanceCaptureFilter` records `CREATE`/`DELETE` activities on
the secondary-parent edge mutations exactly as it does on primary DataObject writes.

**Strengths.** Smallest possible graph change. Primary-tree reasoning stays intact
for all existing code. Secondary parents are semantically explicit (typed edge, not
a tag or annotation). Queryable in Cypher with a simple pattern match.

**Weaknesses.** Two relationship types is slightly more complex than one. The OGM
layer (`DataObject.java`) needs a new `@Relationship(type = "has_secondary_child")
List<DataObject> secondaryParents` field plus a corresponding DAO bypass (same
pattern as the REF-1 fix in `DataObjectV2Rest.buildContainersFromCypher`).

### Option B — Named group membership (`:DataObjectGroup` node)

**Shape.** DataObjects belong to named groups, each group a `:DataObjectGroup`
node connected via `has_group_member` edges. Groups act like structural tags.
The sidebar shows groups instead of (or alongside) the tree.

**Strengths.** More expressive: groups can span multiple Collections (no single-
parent assumption). Works like labels/tags but is structural and queryable.
Closest to a knowledge-graph primitive.

**Weaknesses.** Abandons the tree as the primary navigation metaphor, which is
the familiar shape for LUMEN/MFFD users. Requires a new entity, new REST
surface, new sidebar component, and significantly more test coverage. Migrations
are additive but the UX disruption is high. Overpowers the MVP need.

### Option C — Saved views as virtual trees

**Shape.** Keep the single-parent tree in the DB. Add `:SavedView` nodes that
define a filtered/projected sub-tree via a `filterSpec` JSON predicate. The view
is computed client-side from the already-fetched DataObject list. Zero graph
migration; all logic in the view layer.

**Strengths.** Zero schema change. Ships in one sprint. Answers the "I want to
filter down to AFP layup steps" ask immediately. Easy to extend.

**Weaknesses.** Does not address the multi-parent problem. A bridge-welding
DataObject still cannot appear in two places. A view can filter *down* but
cannot assign a DataObject to multiple branches.

### Recommendation: Option A (MVP) + Option C (UX layer)

Option A solves the structural problem (multi-parent membership) with the smallest
possible graph change and fits naturally into the existing `has_child` edge
family. Option C layers a no-migration usability surface on top that answers the
"filter and save" ask without blocking on the graph change.

Option B is the right long-horizon shape (a DataObject-group model that spans
Collections is the correct FAIR primitive for "all AFP layup DataObjects across
campaigns"), but it is out of scope for TPL6 MVP. It is tracked as a future option
in §6.

---

## §3 — Graph schema changes (TPL6a)

### §3.1 Naming correction

The task brief refers to a `[:PARENT_OF]` Neo4j relationship — that relationship
does not exist in the codebase. The actual edge is `has_child` (constant
`Constants.HAS_CHILD`) stored in the **child-to-parent direction** via the
`@Relationship(type = Constants.HAS_CHILD, direction = Direction.INCOMING)
DataObject parent` field and the `Direction.OUTGOING` `children` list on the
same entity. The design below uses `has_secondary_child` to mirror this naming
convention (parent-to-child direction, same OGM pattern).

### §3.2 New Neo4j relationship: `has_secondary_child`

```
(:DataObject)-[:has_secondary_child {since: timestamp, note: String}]->(:DataObject)
```

Properties:
- `since` (epoch milliseconds, Long) — when the secondary-parent link was created.
- `note` (String, optional, max 500 chars) — operator-supplied reason for the
  cross-reference (e.g. "shared bridge-welding step between Q1 and Q2 tracks").

The primary parent edge (`has_child`) remains unchanged. Secondary parents are
additive; a DataObject with zero `has_secondary_child` incoming edges has no
secondary parents, which is the default for all existing DataObjects.

### §3.3 Cypher migration: V71

File: `backend/src/main/resources/neo4j/migrations/V71__Add_secondary_child_relationship_index.cypher`

```cypher
// V71 — TPL6a: index for has_secondary_child traversal.
// No data backfill required — all existing DataObjects have zero secondary parents.
// Idempotent: IF NOT EXISTS guards.
//
// Operator runbook: docs/admin/runbooks/tpl6a-secondary-parent.md
// Rollback: remove the index (no data to reverse).

CREATE INDEX secondary_child_since_idx IF NOT EXISTS
  FOR ()-[r:has_secondary_child]-()
  ON (r.since);
```

A relationship index is not strictly necessary for correctness but makes the
temporal queries in §3.5 efficient. The relationship type is already queryable
via pattern matching without an index.

### §3.4 OGM entity change: `DataObject.java`

Add to `backend/src/main/java/de/dlr/shepard/context/collection/entities/DataObject.java`:

```java
@Relationship(type = Constants.HAS_SECONDARY_CHILD, direction = Direction.OUTGOING)
private List<DataObject> secondaryChildren = new ArrayList<>();

@Relationship(type = Constants.HAS_SECONDARY_CHILD, direction = Direction.INCOMING)
private List<DataObject> secondaryParents = new ArrayList<>();
```

And in `Constants.java`:

```java
public static final String HAS_SECONDARY_CHILD = "has_secondary_child";
```

**Important:** OGM depth-1 loading may not traverse relationship properties.
The `since` and `note` properties on the edge must be read via a direct Cypher
query (same pattern as `DataObjectV2Rest.buildContainersFromCypher` which bypasses
OGM for the container sub-graph). A new `DataObjectDAO.findSecondaryParents(String
dataObjectAppId)` method returning `List<SecondaryParentRecord>` (a lightweight
struct carrying `parentAppId`, `parentName`, `since`, `note`) is the right shape.

### §3.5 REST surface

All endpoints follow the existing `/v2/collections/{collectionAppId}/data-objects/
{dataObjectAppId}/…` path convention from `DataObjectV2Rest`.

**Add a secondary parent:**

```
POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/secondary-parents
Content-Type: application/json

{
  "parentAppId": "019f-...",
  "note": "shared bridge-welding step between Q1 and Q2 tracks"
}
```

Response `201 Created`:

```json
{
  "dataObjectAppId": "019e-...",
  "parentAppId": "019f-...",
  "since": 1748308800000,
  "note": "shared bridge-welding step between Q1 and Q2 tracks"
}
```

Business rules:
- Both DataObjects must belong to the same Collection (TPL6a scope; cross-
  Collection secondary parents are a future extension requiring a permissions
  model update).
- The caller must have Write permission on the DataObject's Collection.
- Self-loops are rejected (400).
- Circular secondary-parent chains are rejected (400 — the service checks for
  cycles in the secondary-parent graph up to depth 20; deeper cycles are
  practically impossible in single-Collection graphs but must be guarded).
- `ProvenanceCaptureFilter` records the mutation as a `CREATE` Activity.

**Remove a secondary parent:**

```
DELETE /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/secondary-parents/{parentAppId}
```

Response `204 No Content`. Idempotent. Provenance records a `DELETE` Activity.

**List secondary parents:**

```
GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/secondary-parents
```

Response `200 OK`:

```json
[
  {
    "dataObjectAppId": "019e-...",
    "parentAppId": "019f-...",
    "since": 1748308800000,
    "note": "shared bridge-welding step between Q1 and Q2 tracks"
  }
]
```

**DataObject detail response extension:**

`DataObjectDetailV2IO` gains a new field:

```json
"secondaryParentAppIds": ["019f-..."]
```

This is a read-only summary (appIds only) for fast sidebar chip rendering.
Callers needing `since`/`note` metadata use the dedicated `GET .../secondary-parents`
endpoint above.

### §3.6 Permissions note

A DataObject accessed via a secondary parent from Collection B remains governed by
its primary Collection A's permissions. The caller must have Read on Collection A
to access the DataObject regardless of how they navigated to it. The secondary-parent
endpoint checks Write on the **primary** Collection when attaching/detaching.

Cross-Collection secondary parents (DataObject in Collection A appearing as a
child of a DataObject in Collection B) require the caller to have Read on both
Collections; this is deferred to a follow-up design note when the cross-Collection
use case is confirmed by the MFFD or PLUTO team.

---

## §4 — Saved views (TPL6b)

### §4.1 `:SavedView` Neo4j entity

```
(:SavedView {
  appId: String,            // UUID v7, minted at creation
  collectionAppId: String,  // parent Collection
  name: String,             // display name (non-blank, ≤ 100 chars)
  description: String,      // optional, CommonMark
  filterSpec: String,       // JSON predicate document (see §4.2)
  createdBy: String,        // username
  createdAt: Long,          // epoch millis
  updatedAt: Long
})
```

Relationship: `(:Collection)-[:HAS_SAVED_VIEW]->(:SavedView)`.

### §4.2 `filterSpec` JSON predicate format

The filter spec is a JSON object. Operators are aligned to the
`usePagedDataObjects` composable's current server-side parameters
(`name`, `page`, `size`) and the `DataObjectV2Rest.list` query
parameters, extended with DataObject-level predicates:

```json
{
  "nameContains": "AFP",
  "statusIn": ["READY", "IN_REVIEW"],
  "hasAnnotation": {
    "predicateIri": "https://w3id.org/mdo/structure/hasProcessStep",
    "value": "AFP_Layup"
  },
  "tagIn": ["Q1", "calibration"],
  "createdAfter": "2024-01-01T00:00:00Z",
  "createdBefore": "2025-12-31T23:59:59Z",
  "hasSecondaryParentOf": "019f-..."
}
```

All fields are optional; absent fields are ignored (AND semantics for those present).
`hasAnnotation` applies only when the SEMA-V6 `:SemanticAnnotation` layer is
active on the instance (gracefully omitted otherwise). `hasSecondaryParentOf`
allows "show me all DataObjects that are secondary children of this node" — a
key query for the MFFD multi-parent scenario.

The `filterSpec` string is stored verbatim (server does not validate operators
beyond JSON-parse; unknown operators are silently ignored in v1). A formal
`filterSpec` JSON Schema is part of TPL6b acceptance criteria but not a
prerequisite for landing the persistence layer.

### §4.3 REST surface for saved views

```
GET  /v2/collections/{collectionAppId}/views
POST /v2/collections/{collectionAppId}/views
GET  /v2/collections/{collectionAppId}/views/{viewAppId}
PATCH /v2/collections/{collectionAppId}/views/{viewAppId}
DELETE /v2/collections/{collectionAppId}/views/{viewAppId}
```

`POST` body:

```json
{
  "name": "AFP Layup steps only",
  "description": "Filters to DataObjects with process step = AFP_Layup",
  "filterSpec": "{\"nameContains\": \"AFP\"}"
}
```

`GET` (list) response:

```json
[
  {
    "appId": "019g-...",
    "name": "AFP Layup steps only",
    "description": "...",
    "filterSpec": "{\"nameContains\": \"AFP\"}",
    "createdBy": "kreb_fl",
    "createdAt": 1748308800000,
    "updatedAt": 1748308800000
  }
]
```

Auth: Read to list/get; Write to create/patch/delete. Same Collection permission
gate as DataObject endpoints.

### §4.4 Migration for `:SavedView`

File: `backend/src/main/resources/neo4j/migrations/V72__Add_SavedView_constraint.cypher`

```cypher
// V72 — TPL6b: `:SavedView` unique constraint on appId.
CREATE CONSTRAINT saved_view_appid_unique IF NOT EXISTS
  FOR (v:SavedView)
  REQUIRE v.appId IS UNIQUE;
```

### §4.5 CollectionSidebar UX

`CollectionSidebar.vue` gains a **"Views" section** rendered as a collapsible
panel below the DataObject tree. Visual placement:

```
[DataObjects]  (existing treeview)
  ...
[Views ▾]       (new collapsible section)
  AFP Layup steps only
  Bridge-welding track
  + Save current filter as view
```

Clicking a saved view sets the active `filterText` ref in the sidebar to the
view's `filterSpec` and calls `usePagedDataObjects` with those parameters. The
treeview re-renders with only the matching DataObjects. A "Clear view" chip allows
returning to the unfiltered tree.

"+ Save current filter as view" opens a small `SaveViewDialog.vue` that POSTs
the current `filterText` as a new `:SavedView`.

The sidebar filter input (existing `filterText` ref) and saved views are
mutually exclusive — applying a saved view overwrites the free-text filter.
The `filterText` ref is typed as `string | SavedView` in the updated composable.

---

## §5 — Knowledge-graph navigation (TPL6c)

### §5.1 Graph view toggle

`CollectionSidebar.vue` gains a view-mode toggle in its header:

```
[Tree]  [Graph]   (icon buttons, like the existing advanced-mode toggle)
```

In **Graph** mode the treeview is replaced by a `CollectionNetworkGraph.vue`
component. The tree is hidden but not unmounted — switching back to Tree mode
is instant (no re-fetch).

### §5.2 `CollectionNetworkGraph.vue`

Renders the DataObject graph for the current Collection using `d3-force` (per
`feedback_reuse_trusted_code.md` — dagre is not suitable at MFFD scale; ECharts
force layout breaks at ~300 nodes; `d3-force` handles 500+ nodes with viewport
culling).

Node types and colours (matches `CollectionLineageGraph.vue` palette):

| Node | Colour | Shape |
|---|---|---|
| DataObject (READY/PUBLISHED) | Blue | Circle |
| DataObject (DRAFT/IN_REVIEW) | Grey | Circle |
| DataObject (ARCHIVED) | Faded | Circle |
| Secondary parent relationship | Dashed edge (orange) | — |
| Primary parent (`has_child`) | Solid edge (grey) | — |
| Predecessor/successor (`has_successor`) | Directed solid edge (blue) | — |

**Viewport cap.** At most 200 nodes are rendered in the initial view (sorted by
creation date, most recent first). For Collections with more than 200 DataObjects
a "Zoom to subgraph" button prompts for a root DataObject `appId` and renders the
200-node neighbourhood. This prevents the hairball failure mode at MFFD scale
(see `aidocs/semantics/95 §14d`).

**Interaction.** Clicking a node navigates to that DataObject's detail page.
Hovering shows a tooltip with `name`, `status`, and `secondaryParentAppIds`.
Right-clicking (or long-pressing on touch) surfaces a context menu with "Add
secondary parent" and "View in tree".

**Shared composable.** `useLineageGraph(nodes, edges)` (per PERF8 + UI16 rows in
`aidocs/16-dispatcher-backlog.md`) is the extraction target: both
`CollectionLineageGraph.vue` and `CollectionNetworkGraph.vue` should share
colour-palette, label-truncation, and edge-style logic.

### §5.3 Cross-reference indicators on detail pages

Each DataObject detail page (`/collections/{id}/dataobjects/{did}`) gains a
**"Also appears in"** section below the primary parent breadcrumb. The section
lists each secondary parent with its Collection name, DataObject name, and a
navigation link. This is a list view (not a graph render) and has no node limit.

```
Also appears in:
  • Frame Welding (Collection: MFFD Q2 Campaign) → [navigate]
```

This satisfies the "cross-reference indicators" row in the TPL6 v1 table in
`aidocs/semantics/95 §14d` without requiring the full graph renderer.

---

## §6 — Phasing

### TPL6a — Multi-parent edges (S: ~5 days)

Deliverables:
1. `Constants.HAS_SECONDARY_CHILD = "has_secondary_child"` in `Constants.java`
2. `@Relationship` fields on `DataObject.java` (`secondaryChildren`, `secondaryParents`)
3. `DataObjectDAO.findSecondaryParents(String dataObjectAppId)` — direct Cypher
   bypass (same pattern as `findContainersByDataObjectAppId`)
4. `DataObjectDAO.addSecondaryParent(String childAppId, String parentAppId, String note)`
5. `DataObjectDAO.removeSecondaryParent(String childAppId, String parentAppId)`
6. `SecondaryParentIO` record (`dataObjectAppId`, `parentAppId`, `since`, `note`)
7. `SecondaryParentsV2Rest` resource: `GET/POST/DELETE` at
   `/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/secondary-parents`
8. `DataObjectDetailV2IO.secondaryParentAppIds: List<String>` field
9. `V71__Add_secondary_child_relationship_index.cypher` migration
10. `CollectionSidebar.vue` — "also in: [chip chip]" chips below secondary-child nodes
11. DataObject detail page — "Also appears in" section
12. 8 unit + integration tests:
    - `SecondaryParentsV2RestTest`: add, list, remove, self-loop rejected, cycle rejected
    - `DataObjectDAOSecondaryParentsTest`: Cypher round-trip, no orphans after DO delete
    - `SecondaryParentsPermissionsTest`: cross-Collection attachment rejected (TPL6a scope)

### TPL6b — Saved views (M: ~5 days)

Deliverables:
1. `:SavedView` Neo4j entity + DAO
2. `SavedViewIO` (appId, collectionAppId, name, description, filterSpec, createdBy,
   createdAt, updatedAt)
3. `CollectionSavedViewsRest`: `GET/POST/PATCH/DELETE /v2/collections/{appId}/views`
4. `V72__Add_SavedView_constraint.cypher` migration
5. `CollectionSidebar.vue` — "Views" collapsible section + active view state
6. `SaveViewDialog.vue` — save current filter as a view
7. `filterSpec` JSON evaluation in `usePagedDataObjects` composable (client-side
   evaluation for v1; server-side `filterSpec` push-down is a TPL6b+ extension)
8. 6 tests: CRUD + filterSpec round-trip + sidebar applies view

### TPL6c — Graph view toggle (L: ~5 days)

Deliverables:
1. `CollectionNetworkGraph.vue` — d3-force graph renderer
2. `useLineageGraph` shared composable (extraction from existing graph components;
   resolves PERF8 + UI16)
3. Graph view toggle in `CollectionSidebar.vue` header
4. "Zoom to subgraph" input + 200-node viewport cap
5. Context menu: "Add secondary parent", "View in tree"
6. `DataObjectV2Rest.graph` endpoint: `GET /v2/collections/{collectionAppId}/
   data-objects/graph?rootAppId=...&depth=3` returning nodes + edges JSON (avoids
   N+1 by computing the neighbourhood in a single Cypher `MATCH (d:DataObject)-
   [r:has_child|has_secondary_child|has_successor*1..{depth}]-(n)` query)
7. 4 tests: graph endpoint returns correct shape, depth is clamped at 10

### TPL6d — Cross-Collection polish (deferred)

- Namespace-aware breadcrumbs when navigating across Collections via a secondary
  parent link
- Cross-Collection secondary parents (requires permissions model update — tracked
  as follow-up to PERM-SYSTEM-REVIEW in `aidocs/16-dispatcher-backlog.md`)
- `filterSpec` server-side push-down (avoids client-side filtering of large pages)

---

## §7 — Acceptance test: MFFD bridge-welding scenario

**Setup.** Using the MFFD showcase Collection (or a dedicated integration test
fixture):

1. Create DataObject `Bridge-Welding-Run-001` with primary parent `Frame-Welding-Subtree`.
2. Call `POST /v2/collections/{cid}/data-objects/{bridge-weld-appId}/secondary-parents`
   with `parentAppId` = appId of `Q1-AFP-Chain`.
3. Verify `GET .../secondary-parents` returns one record with correct `since`.
4. Verify `GET .../data-objects/{bridge-weld-appId}` response includes
   `"secondaryParentAppIds": ["{Q1-AFP-Chain-appId}"]`.
5. Navigate to the CollectionSidebar in Tree mode:
   - `Bridge-Welding-Run-001` appears under `Frame-Welding-Subtree` (primary path).
   - Below it: "also in: Q1-AFP-Chain" chip.
   - Clicking the chip navigates to `Q1-AFP-Chain` with `Bridge-Welding-Run-001`
     highlighted.
6. Open SavedView dialog, filter to `nameContains: "AFP"`, save as "AFP Layup steps only".
7. Verify the Views section shows the saved view; clicking it filters the tree to
   AFP DataObjects only.
8. Switch to Graph mode: `Bridge-Welding-Run-001` is visible with an orange dashed
   edge to `Q1-AFP-Chain` (secondary parent) and a grey solid edge to
   `Frame-Welding-Subtree` (primary parent).

**Performance gate.** On the MFFD Collection (~8 500 DataObjects): secondary-parent
write completes in < 200 ms; the graph endpoint with `depth=2` on a 200-node
neighbourhood returns in < 1 s. Measured via `make smoke` post-deploy.

**UX gate.** Creating a secondary parent link takes ≤ 3 clicks from the
DataObject detail page: "Add secondary parent" button → type or autocomplete parent
name → confirm. (Context menu in Graph mode counts as 3 clicks: right-click →
"Add secondary parent" → confirm.)

---

## §8 — Backlog cross-references

This design resolves or advances the following rows in
`aidocs/16-dispatcher-backlog.md`:

| Row ID | Connection |
|---|---|
| TPL6a | Multi-parent membership — this doc is the design; row status → **design done** |
| TPL6b | Saved views — this doc is the design; row status → **design done** |
| TPL6c | Network views — this doc is the design; row status → **design done** |
| TPL6d | Cross-Collection polish — deferred; row stays queued |
| PERF8 | `useLineageGraph` shared composable from TPL6c resolves the "shared graph render code" sub-item |
| UI16 | Same as PERF8 — shared composable extraction |
| LANDING-MY-DATA-GRAPH | The d3-force substrate introduced in TPL6c is the reuse target for the personal landing-page graph view |

---

## §9 — Option B: future design note

Option B (`:DataObjectGroup` node membership) is the right shape for the long-horizon
case where a calibration certificate or material batch DataObject needs to be
discoverable from any Collection in the instance, not just from one parent tree.
When the PERM-SYSTEM-REVIEW (`aidocs/16-dispatcher-backlog.md`) resolves the ACL
vs RBAC question and establishes cross-Collection access semantics, the group
membership design should be revisited as a complement to Option A (not a
replacement).

The `:DataObjectGroup` entity can be introduced additively alongside
`has_secondary_child` edges — there is no conflict. Groups would be instance-scoped
(visible across Collections) while secondary parents are Collection-scoped. Both
models coexist cleanly.

---

## §10 — Operator runbook pointer

Once TPL6a ships:

- Admin runbook: `docs/admin/runbooks/tpl6a-secondary-parent.md` (to be created
  in the same PR as the implementation)
- No deploy-time config key: secondary parents are always available once V71
  migration runs; no feature toggle required (the feature is purely additive,
  zero risk to existing data)
- Rollback: drop the `has_secondary_child` relationship type (zero rows in fresh
  installs) and remove the index; the migration rollback is one `DROP INDEX` Cypher
  statement

---

*This document is stage `feature-defined`. Implementation PRs should reference it
in their commit message via `TPL6a`, `TPL6b`, or `TPL6c` scope and update
`aidocs/34-upstream-upgrade-path.md` (additive feature, no upstream operator
impact) and `aidocs/44-fork-vs-upstream-feature-matrix.md` (status flip from
📐 designed → 🚧 in-flight → ✓ shipped) per the CLAUDE.md standing rules.*
