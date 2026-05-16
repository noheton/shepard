# Snapshots — Design (versioning, reloaded)

**Scope.** Make snapshots a first-class concept in shepard's
versioning surface — point-in-time, immutable, reproducible reads
of an entire Collection subtree.

**Status.** Concept design. No code or migration shipped.
**Snapshot date.** 2026-05-08.
**Originating items.** User request "regarding the versioning
feature, can we make snapshots a thing?" Couples to the existing
versioning surface (feature-toggled in upstream; gated behind a
Quarkus build property), to L2c (so snapshots reference entities
by stable `appId`), and to RO-Crate exports (`aidocs/31`) — a
snapshot is the natural scope of a reproducible export.

## 1. Today's versioning surface

`VersionableEntity` Neo4j ancestor + `Version` node, joined by
`(:VersionableEntity)-[:HAS_VERSION]->(:Version)`. Today's `Version`
is **a marker**: it records `(name, createdBy, createdAt)` against
a Collection, but the underlying tree is mutable — read paths don't
project the data as it was when the version was tagged.

Effect: a "v1" tag and a "v2" tag both point at *whatever the data
looks like right now*. Useful for naming, useless for reproducibility.

## 2. What "snapshot" should mean

Three properties make something a snapshot:

1. **Frozen** — reads return the exact data as it was at snapshot time.
2. **Immutable** — the snapshot's contents cannot change after creation.
3. **Reproducible** — re-reading the snapshot weeks later yields the
   same bytes (subject to backups; not against deletes).

A snapshot is **scoped** — typically the Collection root and its
entire subtree. Per-entity snapshots are possible but rare; the
unit is "the campaign as of YYYY-MM-DD."

## 3. Three implementation architectures

### 3.1 (A) Deep-copy snapshots

At snapshot creation, recursively clone every node and every payload
into a parallel immutable subtree under a `Snapshot` root.

Pro: simple read-side (snapshot is just another Collection tree).
Con: storage cost = full tree per snapshot. For a campaign with
multi-GB timeseries / files, prohibitive.

### 3.2 (B) Copy-on-write (COW)

Snapshot points at the live tree. Subsequent writes branch
downstream — the snapshot keeps its original references; the live
tree gains new node revisions; payloads are immutable by construction
(MongoDB OIDs, TimescaleDB rows by `(timeseries_id, time)`).

Pro: storage cost = only changed parts.
Con: read-side rewriting required ("reading via snapshot S"
substitutes pinned revisions for live ones). Implementation
complexity.

### 3.3 (C) Logical snapshots backed by entity revisions — recommended

Closest to git's content-addressed model:

- Every `VersionableEntity` (and every payload reference) gains a
  monotonic `revision: long` field. Writes increment.
- A `Snapshot` records `Map<entityAppId, revision>` for every entity
  in scope at snapshot time.
- Reads via snapshot: `GET /v2/collections/{appId}?snapshot={snapshotAppId}`
  rewrites every reference to the pinned `(appId, revision)`. Uses
  the existing edge-traversal but pins each step.

Pro: storage cost = O(entities-in-scope), not O(payload bytes).
The per-entity revision history is small (10s to 100s of bytes per
write). Payloads themselves are immutable (Mongo OID, Timescale
hypertable rows are append-only); the snapshot just pins which
OID / rows to read.
Con: every write must increment a counter. Every read via snapshot
adds one extra Cypher hop per entity.

**Recommendation: (C).** Storage cost wins decisively. Read-overhead
is mitigated by the existing permission cache (per-(user, appId)
keys; (user, appId, snapshot) is a near-free extension).

## 4. The model

### 4.1 Entities

```java
@NodeEntity
public class VersionableEntity extends AbstractEntity {
  // existing fields ...
  @Property("revision")
  private long revision = 1;   // monotonic, write-time
}

@NodeEntity
public class Snapshot extends AbstractEntity implements HasAppId {
  @Property private String name;          // user-given, e.g. "v1.0 — campaign close"
  @Property private String description;   // free text
  @Property private Instant createdAt;
  @Property private String createdBy;
  @Relationship(type="SNAPSHOT_OF") private VersionableEntity root;
}

@NodeEntity
public class SnapshotEntry {
  @Property private String entityAppId;
  @Property private long revision;
  @Relationship(type="ENTRY_OF") private Snapshot snapshot;
}
```

Per-snapshot, one `SnapshotEntry` per entity in scope. For a
1000-DataObject campaign that's 1000 small nodes — trivial.

### 4.2 Read path

```
GET /v2/collections/{appId}                           → live read (current revision)
GET /v2/collections/{appId}?snapshot={snapshotAppId}  → snapshot read
```

Resolver: for each entity reachable from the Collection, look up
the matching `SnapshotEntry` and use its `revision` instead of the
live one. Entities not in the snapshot's entry map (e.g. created
after the snapshot) are **invisible** through the snapshot view —
the snapshot is a view of the world as of `createdAt`.

### 4.3 Write path on a snapshotted entity

Writes go to the **live** tree, not the snapshot — the snapshot is
read-only by construction. Writes increment `revision`; the snapshot
keeps pointing at the pre-write revision via its `SnapshotEntry`.

If an entity is **deleted** post-snapshot, the snapshot still resolves
it (the entity row stays in the DB; only the soft-delete flag flips).
Hard-delete after snapshot is a special case — see §6 risks.

### 4.4 Snapshot vs the existing `Version` marker

`Version` stays — it's the user-friendly name. A `Snapshot` is the
machine-faithful pinning. Coupling: `Version` gains an optional
`snapshot: Snapshot` reference. Creating a Version with the new
"snapshot" flag (`POST /v2/collections/{appId}/versions
{name: "v1.0", snapshot: true}`) creates both the Version node and
the matching Snapshot in one transaction.

Existing Version-marker behaviour (no snapshot) keeps working —
backwards compatible.

## 5. Endpoints (under `/v2/`)

| Method + path | Purpose |
|---|---|
| `POST /v2/collections/{appId}/snapshots` | Create a snapshot of the Collection's current state. Body: `{name, description}`. Returns the Snapshot's appId. |
| `GET /v2/collections/{appId}/snapshots` | List snapshots of a Collection |
| `GET /v2/snapshots/{appId}` | Read snapshot metadata (entries count, root, createdAt) |
| `GET /v2/snapshots/{appId}/manifest` | Full manifest: every `(entityAppId, revision)` entry. For audit + diff tools. |
| `DELETE /v2/snapshots/{appId}` | Delete a snapshot (admin / Collection owner). Pre-snapshot revisions stay — only the SnapshotEntry rows go. |
| `GET /v2/collections/{appId}?snapshot={snapshotAppId}` | Snapshot-pinned read |
| `POST /v2/collections/{appId}/export?snapshot={snapshotAppId}` | RO-Crate export against a snapshot — reproducible by construction. |

`/shepard/api/...` paths get **no** changes — upstream behaviour
preserved.

## 6. Risks

- **Hard-delete vs snapshot.** A hard-delete (post-L1 admin CLI
  cleanup) of an entity referenced by a snapshot would break the
  snapshot. Mitigation: refuse the hard-delete with a 409 listing
  the snapshots that pin the entity. Operator can either delete
  the snapshots first or accept the orphan.
- **Storage growth.** O(snapshots × entities-in-scope) for the
  SnapshotEntry rows. For 100 snapshots × 1000 entities = 100k
  small Neo4j nodes. Tolerable; well under any practical Neo4j
  scale ceiling.
- **Permission cache pollution.** `(user, appId, snapshot)` key
  triples cardinality is `users × entities × snapshots`. Bounded
  per Collection; should fit comfortably within `aidocs/16` A4's
  10k cache cap. Worth a load test before declaring victory.
- **Schema migration mid-snapshot.** If shepard's data shape
  changes between snapshots (e.g. a future T1c FileSlot adds a
  new required field), reads via old snapshots may need
  back-compatible projection. Same problem any versioned schema
  has; explicit handling per schema-change.

## 7. Phasing

| ID | Slice | Size | Gate |
|---|---|---|---|
| **V2a** | `revision` field on `VersionableEntity` + write-side increment. Migration `V17__Add_revision_to_versionable.cypher` (sets `revision = 1` on existing rows). | S | None |
| **V2b** | `Snapshot` + `SnapshotEntry` model + `POST /v2/collections/{appId}/snapshots`. Initially without read-side rewriting. | M | V2a + L2c (so snapshot entries reference entities by `appId`, not internal id) |
| **V2c** | Snapshot-pinned read path (`?snapshot=` query param). Resolver layer. Permission cache key extension. | M | V2b |
| **V2d** | RO-Crate export with `?snapshot=` — reproducible exports. | S | V2c + `aidocs/31` |
| **V2e** | Snapshot diff tool (`GET /v2/snapshots/{a}/diff/{b}` returning entities added / removed / changed-revision). | M | V2b |
| **V2f** | (deferred) "Branch from snapshot" — fork off a snapshot into a writable child Collection. Significant scope; only if real demand. | L | V2c |

Recommended order: **V2a → V2b → V2c → V2d**. V2a alone unblocks
internal use of `revision` (e.g. optimistic locking — separate
benefit); V2b makes snapshots a thing; V2c makes reads useful;
V2d is the reproducible-export payoff that motivates the whole
chain.

## 8. Migrations and the upgrade path

- `V17__Add_revision_to_versionable.cypher` — sets `revision = 1`
  on every existing `VersionableEntity`. Idempotent
  (`SET v.revision = COALESCE(v.revision, 1)`).
- `V18__Add_appId_constraint_for_Snapshot_and_SnapshotEntry.cypher`
  — two unique constraints, idempotent.
- **Tracker rows in `aidocs/34`:** V2a/V2b/V2c each get a ZERO row
  (additive entities + nullable revision; reads default to live).
  V2c notes the new optional `?snapshot=` query param.

## 9. Cross-references

- **aidocs:** `aidocs/16` (existing versioning row L8 — superseded
  by V2 series), `aidocs/25` (L2c gates V2b), `aidocs/31`
  (RO-Crate export hookup at V2d), `aidocs/34` (per-slice ZERO
  rows), `aidocs/39 §2.3` (templates pin `templateVersion` — same
  conceptual pattern as snapshots; consistent UX).
- **Backlog:** new **V2** umbrella + V2a-V2f sub-IDs in `aidocs/16`.
