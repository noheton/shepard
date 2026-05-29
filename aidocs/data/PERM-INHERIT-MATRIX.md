---
stage: feature-defined
last-stage-change: 2026-05-29
audience: contributors + operators
---

# PERM-INHERIT-MATRIX — entity → permission source

**Snapshot date:** 2026-05-29
**Companion doc:** `aidocs/platform/24-permission-system-review.md §9` (walk algorithm).
**Backlog rows:** PERM-INHERIT-01 (this doc), PERM-INHERIT-02 (§9 in doc 24),
PERM-INHERIT-04 (BUG-148 reconcile, §3 below).
**Audience:** contributors deciding where to add perm checks; operators reasoning
about "what does 'grant flo READ on Collection X' actually cover?"

---

## 1. Why this matrix exists

Shepard's authorization is graph-walked. A subset of entity kinds carry their
own `(:Permissions)` sibling via a `HAS_PERMISSIONS` edge; the rest **inherit**
through a parent walk that bottoms out at a `:Collection`.

This split is a **structural footgun**: an operator who says "grant flo READ on
this Collection" expects the grant to cover every payload referenced from that
collection. It does — for `DataObject` and every `*Reference` (which walk to the
Collection) — but it **does not** cover the top-level container kinds
(`FileContainer`, `TimeseriesContainer`, `StructuredDataContainer`,
`HdfContainer`) that own their own `:Permissions` node, because containers can be
multi-attached (one container, many Collections). The cascade-grant endpoint
that closes this footgun is queued as PERM-INHERIT-03.

See the live-verified evidence from BUG-148:
[`aidocs/agent-findings/bug-148-do-perms-seeded-2026-05-24.md`](../agent-findings/bug-148-do-perms-seeded-2026-05-24.md)
— 17,149/17,149 DataObjects in the nuclide graph lack `:Permissions`; all writes
succeed via the inheritance walk.

---

## 2. The matrix

Legend:

- **owns** — entity has a `HAS_PERMISSIONS` edge to its own `:Permissions` node.
  Created by `PermissionsService.createPermissions()`; mutated by
  `PUT /shepard/api/.../permissions`.
- **inherits → X** — entity has no own `:Permissions`; the resolver walks to X.
- **inherits → walk** — multi-step walk; specifics in §9 of doc 24.
- **n/a** — entity is not in the discretionary-access perimeter (system-owned,
  admin-only, or user-identity infrastructure).

| Entity kind | Perm source | Parent kind (if inherits) | Java entity file |
|---|---|---|---|
| `Collection` | **owns** | — | `backend/src/main/java/de/dlr/shepard/context/collection/entities/Collection.java:89-90` |
| `DataObject` | inherits → walk | parent `Collection` via `HAS_DATAOBJECT` (see §9.2 of doc 24) | `backend/src/main/java/de/dlr/shepard/context/collection/entities/DataObject.java` (no `Permissions` field) |
| `FileContainer` | **owns** | — | `backend/src/main/java/de/dlr/shepard/data/file/entities/FileContainer.java:19` (extends `BasicContainer:17-18`) |
| `TimeseriesContainer` | **owns** | — | `backend/src/main/java/de/dlr/shepard/data/timeseries/model/TimeseriesContainer.java:13` (extends `BasicContainer:17-18`) |
| `StructuredDataContainer` | **owns** | — | `backend/src/main/java/de/dlr/shepard/data/structureddata/entities/StructuredDataContainer.java:17` (extends `BasicContainer:17-18`) |
| `HdfContainer` (plugin) | **owns** | — | `plugins/hdf5/src/main/java/de/dlr/shepard/data/hdf/entities/HdfContainer.java` (extends `BasicContainer`) |
| `BasicReference` (abstract) | inherits → parent `DataObject` | the `DataObject` it hangs off (then walks to its `Collection`) | `backend/src/main/java/de/dlr/shepard/context/references/basicreference/entities/BasicReference.java:18` (no `Permissions` field) |
| `FileReference` (a.k.a. `SingletonFileReference`) [^singleton] | inherits → walk | `DataObject` → `Collection` | `backend/src/main/java/de/dlr/shepard/context/references/file/entities/FileReference.java:70` (extends `BasicReference`) |
| `FileBundleReference` | inherits → walk | `DataObject` → `Collection` | `backend/src/main/java/de/dlr/shepard/context/references/file/entities/FileBundleReference.java:52` |
| `TimeseriesReference` | inherits → walk | `DataObject` → `Collection` | `backend/src/main/java/de/dlr/shepard/context/references/timeseriesreference/model/TimeseriesReference.java:20` |
| `StructuredDataReference` | inherits → walk | `DataObject` → `Collection` | `backend/src/main/java/de/dlr/shepard/context/references/structureddata/entities/StructuredDataReference.java:20` |
| `URIReference` | inherits → walk | `DataObject` → `Collection` | `backend/src/main/java/de/dlr/shepard/context/references/uri/entities/URIReference.java:13` |
| `DataObjectReference` | inherits → walk | `DataObject` → `Collection` | `backend/src/main/java/de/dlr/shepard/context/references/dataobject/entities/DataObjectReference.java:17` |
| `CollectionReference` | inherits → walk | `DataObject` → `Collection` | `backend/src/main/java/de/dlr/shepard/context/references/dataobject/entities/CollectionReference.java:17` |
| `HdfReference` (plugin) | inherits → walk | `DataObject` → `Collection` | `plugins/hdf5/src/main/java/de/dlr/shepard/data/hdf/entities/HdfReference.java` (extends `BasicReference`) |
| `GitReference` (plugin) | inherits → walk | `DataObject` → `Collection` | `plugins/git/src/main/java/de/dlr/shepard/context/references/git/entities/GitReference.java` (extends `BasicReference`) |
| `VideoStreamReference` (plugin) | inherits → walk | `DataObject` → `Collection` | `plugins/video/src/main/java/de/dlr/shepard/context/references/videostreamreference/model/VideoStreamReference.java:33` (extends `BasicReference`) |
| `SpatialDataReference` (plugin) | inherits → walk | `DataObject` → `Collection` | `plugins/spatiotemporal/src/main/java/de/dlr/shepard/context/references/spatialdata/entities/SpatialDataReference.java` (extends `BasicReference`) |
| `FileGroup` | inherits → parent `FileBundleReference` | (doc-comment only, no edge — see [^filegroup]) | `backend/src/main/java/de/dlr/shepard/context/references/file/entities/FileGroup.java:32` (comment), `:86` (`HAS_PAYLOAD` only) |
| `ShepardFile` | inherits → walk | parent `FileContainer` (containers own perms) | `backend/src/main/java/de/dlr/shepard/data/file/entities/ShepardFile.java` |
| `StructuredData` | inherits → walk | parent `StructuredDataContainer` | `backend/src/main/java/de/dlr/shepard/data/structureddata/entities/StructuredData.java` |
| `PayloadVersion` | inherits → walk | parent `ShepardFile` → `FileContainer` | `backend/src/main/java/de/dlr/shepard/data/file/entities/PayloadVersion.java` |
| `UserGroup` | **owns** [^usergroup] | — | `backend/src/main/java/de/dlr/shepard/auth/users/entities/UserGroup.java:27-28` |
| `Snapshot` / `SnapshotEntry` | inherits → walk | parent `Collection` (snapshots belong to one) | `backend/src/main/java/de/dlr/shepard/context/snapshot/entities/Snapshot.java` |
| `LabJournalEntry` / `LabJournalEntryRevision` | service-internal check | (see `LabJournalEntryService`; service does its own check, not via `PermissionsService`) | `backend/src/main/java/de/dlr/shepard/context/labJournal/entities/LabJournalEntry.java` |
| `SemanticAnnotation` | inherits → subject's appId | walks to the entity whose appId the annotation `appliesTo` | `backend/src/main/java/de/dlr/shepard/context/semantic/entities/SemanticAnnotation.java` |
| `AnnotatableTimeseries` | inherits → parent `TimeseriesContainer` | (bridge node; see CLAUDE.md "per-kind annotation entities are an anti-pattern") | `backend/src/main/java/de/dlr/shepard/context/semantic/entities/AnnotatableTimeseries.java` |
| `Activity` (PROV-O) | **n/a** (system-owned, system-readable) | — (filtered via the audit endpoint's own role gate) | `backend/src/main/java/de/dlr/shepard/provenance/entities/Activity.java` |
| `Publication` | **n/a** (admin-only via `@RolesAllowed("instance-admin")`) | — | `backend/src/main/java/de/dlr/shepard/publish/entities/Publication.java` |
| `ShepardTemplate` | inherits → owning Collection (when scoped) | scoped to a Collection; global templates instance-admin | `backend/src/main/java/de/dlr/shepard/template/entities/ShepardTemplate.java` |
| `ImportPlan` / `ImportLock` | **n/a** (request-scoped, owner-tied) | — | `backend/src/main/java/de/dlr/shepard/v2/importer/entities/ImportPlan.java` |
| `Watch` / `CollectionWatcher` / `Notification` | **n/a** (per-user; service-internal owner check) | — | `backend/src/main/java/de/dlr/shepard/v2/watches/entities/Watch.java` et al. |
| `Subscription` | **n/a** (per-user; `SubscriptionFilter` evaluates per-event) | — | `backend/src/main/java/de/dlr/shepard/common/subscription/entities/Subscription.java` |
| `:*Config` singletons (`SemanticConfig`, `InstanceRegistry`, `InstanceRorConfig`, `SqlTimeseriesConfig`, `VideoConfig`, etc.) | **n/a** (admin-only via `@RolesAllowed("instance-admin")`) | — | `backend/src/main/java/de/dlr/shepard/context/semantic/entities/SemanticConfig.java` et al. |
| `User`, `MirroredUser`, `ApiKey`, `Role`, `GitCredential` | **n/a** (identity infrastructure; per-user owner check in respective service) | — | `backend/src/main/java/de/dlr/shepard/auth/users/entities/User.java` et al. |
| `Version`, `VersionableEntity` (abstract base) | **n/a** (versioning superclass; perms live on concrete entity) | — | `backend/src/main/java/de/dlr/shepard/context/version/entities/VersionableEntity.java` |
| `SemanticRepository`, `Vocabulary`, `Predicate`, `OntologyAlignment`, `OntologyGitSource`, `UserOntologyBundle` | **n/a** (admin-configurable; `@RolesAllowed("instance-admin")`) | — | `backend/src/main/java/de/dlr/shepard/context/semantic/entities/*` |
| `DataQualityRequirement` | inherits → parent (DataObject or Collection scope) | scoped to a parent entity | `backend/src/main/java/de/dlr/shepard/v2/quality/entities/DataQualityRequirement.java` |
| `TimeseriesContainerChartView` | inherits → parent `TimeseriesContainer` | container owns perms | `backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/entities/TimeseriesContainerChartView.java` |
| `CollectionProperties` | inherits → parent `Collection` | property bag on `Collection` | `backend/src/main/java/de/dlr/shepard/context/collection/entities/CollectionProperties.java` |
| `InstanceConfig` | **n/a** (admin-only) | — | `backend/src/main/java/de/dlr/shepard/provenance/entities/InstanceConfig.java` |

[^singleton]: `SingletonFileReference` is **not** a separate Neo4j entity kind. It
    is the same `FileReference` class, surfaced under a separate
    `SingletonFileReferenceService` (per the FR1b reclaim — see
    `backend/src/main/java/de/dlr/shepard/context/references/file/services/SingletonFileReferenceService.java:24-56`).
    Treat the two names as aliases; the perm walk is identical.

[^filegroup]: `FileGroup` carries a doc-comment `"Permissions inherit from the
    parent bundle"` at `FileGroup.java:32`, but no `HAS_PERMISSIONS` edge and no
    walk implementation in `PermissionsService`. The perm gate for a `FileGroup`
    payload is checked at the parent `FileBundleReference` REST resource layer
    (which itself walks `DataObject → Collection`). The comment captures the
    *intent*; the *implementation* is "the parent reference checked before we
    got here."

[^usergroup]: `UserGroup` is the only owner-pattern entity that isn't a
    `BasicContainer` subclass. Its `:Permissions` controls who can edit the
    group's membership (i.e. who can add/remove users from the group), not who
    can see the group exists. This is appropriate — group membership changes
    are a sensitive operation distinct from data access.

---

## 3. The operator footgun: containers escape the cascade

The matrix surfaces the structural reason "grant flo READ on Collection X" is
**not sufficient** for a showcase that uses top-level containers (LUMEN, MFFD,
microsections all do):

- The Collection grant covers every `DataObject` and every `*Reference` hanging
  off it, because those inherit.
- It does **not** cover the `FileContainer`, `TimeseriesContainer`,
  `StructuredDataContainer`, or `HdfContainer` payloads that the references
  point at, because containers carry their own `:Permissions` (they are
  multi-attachable across Collections — perm-inheritance through a single
  parent is structurally impossible).

Today an operator must either:

1. Set the container's `PermissionType = PublicReadable` at create time so
   READ does not require a per-user grant; or
2. Issue N+1 `PUT /containers/{id}/permissions` calls, one per container the
   showcase uses; or
3. Wait for **PERM-INHERIT-03** — `POST /v2/collections/{appId}/permissions/cascade`,
   which walks every referenced container and applies the same grant in one call.

The cascade endpoint is the queued structural fix. Until it lands, the
microsections + LUMEN + MFFD showcase setups follow option (1) by default.

---

## 4. BUG-148 reconcile — XS (PERM-INHERIT-04)

**The PERM-INHERIT-04 backlog row's premise is itself wrong.** It says "the
2026-05-23 fix seeded `:Permissions` on newly-created DataObjects." No such fix
shipped. The closing record is:

- **`4f246fe5b`** (2026-05-24) — `docs(bug-148): close as works-as-designed —
  DOs inherit Permissions from parent Collection`. The original ticket's premise
  ("DataObject creation doesn't seed Permissions record — all subsequent writes
  403") was empirically wrong: 17,149/17,149 DataObjects in the live nuclide
  graph lacked `:Permissions` and **all writes succeeded via inheritance.**
  Implementing the proposed fix would have created two sources of truth,
  multiplied audit-log rows by ~10-100×, and silently broken the inheritance
  walk's null-check at `PermissionsService.java:287`.
- **`6e22b407e`** (2026-05-26) — `fix(backend): V14-WHERE-CLAUSE-TIGHTEN +
  BUG-148-DESIGN-ASSERT-TEST`. Two artefacts shipped:
  1. **V90 Cypher migration** —
     `backend/src/main/resources/neo4j/migrations/V90__Tighten_orphan_permissions_backfill.cypher`
     (+ rollback twin `V90_R__*`) — actively **removes** wrongly-attached
     `:Permissions` nodes from any `:DataObject` or `:BasicReference` that
     somehow acquired one. This is the *opposite* of seeding.
  2. **`DataObjectServiceTest`** anti-regression — locks in "DataObject has no
     direct `:Permissions` node; access is inherited from the parent Collection"
     as a JUnit invariant. Path:
     `backend/src/test/java/de/dlr/shepard/context/collection/services/DataObjectServiceTest.java`.

**Why the original V14 migration needed tightening:** `V14__Backfill_orphan_permissions.cypher`
matched the `:BasicEntity` label too broadly. On a populated graph where an
operator had set `shepard.permissions.default-owner`, V14 would have attached
`:Permissions` to every `:DataObject` and `:BasicReference` as well — which is
precisely the anti-pattern the BUG-148 closing analysis identified. V90 narrows
the WHERE clause to an allowlist (`:Collection`, `:FileContainer`,
`:TimeseriesContainer`, `:StructuredDataContainer`, `:HdfContainer`,
`:UserGroup`) so the backfill cannot regress the inheritance model.

**Net effect on the matrix:** none. The "DataObject inherits" row above is the
ground truth and is now structurally protected. The PERM-INHERIT-04 row can be
closed as **stale premise — no fix existed to reconcile; the inheritance model
was always correct and is now JUnit-locked.**

Findings precedent: [`aidocs/agent-findings/bug-148-do-perms-seeded-2026-05-24.md`](../agent-findings/bug-148-do-perms-seeded-2026-05-24.md).

---

## 5. References to external authorization patterns

For the cascade-grant endpoint design (PERM-INHERIT-03) and any future
declarative `@Authz` seam (F1 in doc 24), three external sources are worth
citing:

- **OWASP Authorization Cheat Sheet** — emphasizes "deny by default" and
  "centralize authorization logic" (both satisfied by the C3 fail-closed +
  single `PermissionsService` seam shipped 2026-05-23).
  https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
- **NIST SP 800-162** (Guide to ABAC) — the framing under which "perm walks
  through the parent graph" is just a rule-based attribute lookup. Useful for
  the F1 design doc when the path-segment switch is replaced.
- **Neo4j Graph-Traversal Authorization Patterns** — the Cypher one-hop walk
  at `PermissionsService.java:328-330` is the canonical "lookup the perm
  node via the parent edge" pattern; Neo4j's docs on label-based access
  control discuss the same shape for cluster-level perms.

These are advisory, not normative — Shepard's model is established. They
matter for the **next** review pass when the F6 PolicyDecisionPoint seam is
fully replaced.
