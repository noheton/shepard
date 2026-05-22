---
stage: feature-defined
last-stage-change: 2026-05-23
---

# Collection `:watches` Container — Design (WATCH1)

**Scope.** Forward-looking design for a new graph relationship that
lets a Collection "watch" a Container it doesn't own (i.e. doesn't
have any DataObject inside it referencing). The Collection's detail
page then surfaces a "Watched containers" panel showing the live
data flowing into the watched containers, even though no
DataObject inside the Collection points at them.

**Status.** Design, not started. User direction (2026-05-18):

> "containers could be added [to] some kind of watchlist by
> collections to also get the 'raw data' overview"

**Snapshot date.** 2026-05-18.

**Companion docs.** Sits next to `aidocs/integrations/70` (the MQTT
collector that motivates this — visitors to the home-showcase
Collection want to see all three containers flowing live, not just
per-DataObject slices).

---

## 1. Motivation

shepard's reference model is **one-way**: a DataObject points at a
Container via a Reference. The Container itself is structurally
unaware of which DataObjects reference it (we query the inverse
via the CC1b `linked-data-objects` endpoint).

For the LUMEN hot-fire campaign this works fine — each test run
DataObject references a slice of one timeseries container, and the
chain of references defines the campaign.

For the home-showcase collector this works less well. The Collection
is "live data from home". DataObjects called "Smart plugs", "Solar &
battery", "Indoor environment" exist but don't carry per-event
references — telemetry flows continuously into the containers, not
into per-run slices. A visitor wants to land on the Collection and
see the three live containers, not click into the three DataObjects
first.

The watchlist concept: a Collection can **point at** Containers it
doesn't own. The relationship is non-authoritative — deleting the
container does not delete the watch link's graph node, and writing
to the container does not touch the Collection.

---

## 2. Graph shape

```
(Collection {appId, ...}) -[:watches {since, addedBy}]-> (TimeseriesContainer | FileContainer | StructuredDataContainer)
```

The relationship is **directional from Collection to Container** and
**single-purpose** — only used by the Collection detail page to
render the watched-containers panel. Other places that query the
graph (lineage, RO-Crate export, permissions, search) intentionally
skip `:watches` edges so they don't muddy the existing semantics.

`since` is a millis-epoch the relationship was created. `addedBy` is
the username of the user who added the watch.

No back-edge from Container to Collection — keeping the graph
asymmetric mirrors the existing :has_reference asymmetry.

---

## 3. Auth

- **Adding a watch** — requires Write on the Collection (the
  watcher) AND Read on the target Container (the watchee).
- **Listing watches** — any user with Read on the Collection sees
  the watch links, but the Container details rendered alongside
  each link follow the standard per-container Read permission
  check (i.e. if the user doesn't have Read on a watched container,
  the row shows just the container name + "Access denied" badge).
- **Removing a watch** — requires Write on the Collection. The
  Container's permission graph is irrelevant.

This auth shape is identical to the existing `:has_reference`
semantics, so the policy is easy to reason about.

---

## 4. REST surface (all under /v2/)

```
GET    /v2/collections/{collectionAppId}/watched-containers
POST   /v2/collections/{collectionAppId}/watched-containers
DELETE /v2/collections/{collectionAppId}/watched-containers/{watchAppId}
```

Wire shapes:

```
// GET response — list of watches with the container details inlined
// when the caller has Read perm.
[
  {
    "watchAppId": "01HX...",
    "containerKind": "TIMESERIES",  // TIMESERIES | FILE | STRUCTURED_DATA
    "containerAppId": "01HY...",
    "containerName": "solar-powerocean",   // null when caller lacks Read
    "containerAvailability": "available",  // available | forbidden | deleted
    "since": 1736204400000,
    "addedBy": "alice"
  },
  ...
]

// POST body
{
  "containerKind": "TIMESERIES",
  "containerAppId": "01HY..."
}
// POST 201 response = single watch object, same shape as GET items.
```

Returning the `containerName` inline is a performance win — the
Collection page would otherwise have to issue N follow-up requests
to render the panel. The `containerAvailability` token follows the
same shape as `DataReference.referencedContainerAvailability`
(already in the upstream wire) so the frontend's existing
container-display chrome works unchanged.

---

## 5. Frontend integration

New expansion panel on the Collection page (below "Description" +
"Semantic Annotations", above "Attributes"):

```
┌────────────────────────────────────────────────┐
│ Watched containers (3)                  + Add  │
├────────────────────────────────────────────────┤
│  ⬚ solar-powerocean         [TIMESERIES] →     │
│  ⬚ home-energy-appliances   [TIMESERIES] →     │
│  ⬚ home-environment         [TIMESERIES] →     │
└────────────────────────────────────────────────┘
```

Each row links to the matching container detail page. The Container
chip color encodes the container kind (timeseries = blue, file =
green, structured = orange, matching the existing palette).

The "Add" button opens a small picker dialog (or inline editor) that
takes a container name or appId. The picker uses the existing
search endpoints so users don't have to remember appIds.

---

## 6. Storage

A `:Watch` intermediate node — NOT a bare relationship — because
relationship properties make Cypher harder to evolve later (adding
a field requires a migration that walks every edge). One node per
watch link:

```
(Collection)-[:has_watch]->(Watch {appId, since, addedBy})-[:watches]->(Container)
```

Watch nodes have a UUID v7 appId so they're addressable in REST
DELETE paths and audited via the standard PROV1a `:Activity`
mechanism.

Migration: V48 adds `REQUIRE n.appId IS UNIQUE` on `:Watch`. No
backfill (label is new).

---

## 7. Implementation phases

**Phase A (foundation)** — backend skeleton:
- `:Watch` entity + DAO + service.
- POST/DELETE/GET endpoints.
- V48 migration.
- Smoke test: GET / POST / DELETE round-trip.

**Phase B (frontend)** — Collection page integration:
- "Watched containers" expansion panel.
- Add / remove watch UI.
- Container chip styling.

**Phase C (home-showcase wiring)** — home-showcase seed creates
the three watches automatically so the Home Energy Collection's
detail page shows the three live containers without extra work
from the operator.

---

## 8. Open questions

1. **Is a watch a primary citizen of search?** A user searching for
   "solar" should probably see the home-showcase Collection because
   it watches a container called `solar-powerocean`. Easiest:
   include watch-link `containerName` in the Collection's
   searchable-text computed property. Track as a follow-up.
2. **Does deletion of a container cascade?** When a container is
   deleted, the `:Watch` node + edges remain (matching the
   "orphaned references" pattern after a forced safe-delete). The
   GET endpoint returns `containerAvailability: "deleted"` for
   those rows. Operators can clean up via the existing delete-
   watch endpoint.
3. **Do we surface watches in RO-Crate export?** No, by default.
   The watch is a UI affordance; the RO-Crate is the data
   substrate. Track a follow-up if a researcher asks.

---

## 9. Out of scope

- **Bidirectional watching** — Containers cannot watch
  Collections. The use case is "show this Container on this
  Collection's page", not "alert this Container when this
  Collection changes".
- **Watching DataObjects** — only Containers are watchable. A
  DataObject "watch" would conflict with the existing
  predecessor / successor / cross-references model.
- **Watch quotas** — no per-Collection limit on the watch count
  in the first cut. Add a `shepard.collections.watches.max` cap
  later if abusive.
