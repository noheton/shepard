---
layout: default
title: Entity versions
permalink: /reference/entity-versions/
---

# Entity versions

Every Collection and DataObject on shepard grows a first-class
version history — a stack of `:EntityVersion` rows attached to the
parent entity via the `HAS_ENTITY_VERSION` edge. The baseline
landed in ENT1a (`aidocs/16` ENT1a + ADR-0025) and ships:

- A `:EntityVersion` Neo4j entity with its own UUID v7 `appId`
  (`HasAppId` mixin) for cross-version references.
- Per-version `:Permissions` — one ACL per version, evolving
  independently from the parent's ACL after the first inherit.
- A new REST surface under `/v2/{kind}/{appId}/versions/...` with
  five endpoints (create / list / get / patch-ACL / delete).
- A V35 Cypher migration that backfills a `v1 :EntityVersion`
  for every existing Collection + DataObject on first restart
  post-upgrade.

File-payload Copy-on-Write per version is **not** yet wired —
that's ENT1b's territory, gated on FS1a's `FileStorage` SPI
landing. ENT1a is the graph + REST baseline.

## What gets versioned in ENT1a

| Entity kind | URL segment | Backfill on first start |
|---|---|---|
| [Collection](/reference/collection/) | `collections` | `v1` created with parent's ACL deep-cloned |
| [DataObject](/reference/data-object/) | `data-objects` | `v1` created with parent's ACL deep-cloned |

Future ENT1e expansion widens to Bundle / File / Reference via
the same `PublishableKindRegistry` seam KIP1a established — one
row per kind, no URL-shape change.

## Shape

```
Collection ─(:HAS_ENTITY_VERSION)─► EntityVersion
                                    ┌───────────────────────────┐
                                    │ appId (UUID v7, unique)   │
                                    │ versionLabel              │
                                    │ versionOrdinal (int)      │
                                    │ createdAt (epoch millis)  │
                                    │ createdBy (username)      │
                                    │ parentEntityKind          │
                                    │ parentEntityAppId         │
                                    │ note (optional)           │
                                    └─────────────┬─────────────┘
                                                  │
                                       (:has_permissions)
                                                  │
                                                  ▼
                                           :Permissions
                                          (per-version ACL)
```

`:EntityVersion` rows are **append-only** by convention — new
versions are created via `POST .../versions`; the only delete path
refuses to remove the last remaining version. The `versionOrdinal`
field is the monotonic-per-parent sort key — it's computed
independently of the label string so an arbitrary user label like
`1.0.0-rc.1` still gets a clean ordinal (e.g. ordinal=5 if it's
the fifth version of the parent) and the next version after it
gets ordinal 6.

## REST API

### Create a version — `POST /v2/{kind}/{appId}/versions`

Mint a new version on the parent entity.

```
POST /v2/collections/01HF.../versions
Content-Type: application/json
Authorization: Bearer …

{
  "label": "1.0.0-rc.1",
  "note": "first release candidate"
}
```

**Body** (optional — both fields nullable):

| Field | Required | Notes |
|---|---|---|
| `label` | no | When blank/null the server suggests `v<n+1>`. User-supplied labels are validated against `[a-zA-Z0-9][a-zA-Z0-9.\-+]{0,63}` and rejected if purely numeric. |
| `note` | no | Free-form release note, ≤ 2 KB. |

**Auth.** Caller must hold **Writer** or **Manager** on the parent
entity (`AccessType.Write` admits both per
`PermissionsService.rolesGrantAccess`).

**Returns.** 201 with `EntityVersion` JSON shape:

```json
{
  "appId": "0192a8f3-…-v2",
  "versionLabel": "1.0.0-rc.1",
  "versionOrdinal": 2,
  "createdAt": "2026-05-14T07:00:00Z",
  "createdBy": "alice",
  "parentEntityKind": "collection",
  "parentEntityAppId": "01HF...",
  "note": "first release candidate"
}
```

**Failure modes (RFC 7807 problem+json):**

| Status | type | When |
|---|---|---|
| 400 | `versions.label.invalid` | Label shape illegal, purely numeric, or note too long |
| 401 | (no body) | Caller not authenticated |
| 403 | (no body) | Caller lacks Writer/Manager on the parent |
| 404 | `versions.kind.unsupported` | `{kind}` segment is neither `data-objects` nor `collections` |
| 404 | (no body) | Parent entity with `{appId}` not found |
| 409 | `versions.label.duplicate` | User-supplied label collides with an existing version |

The new version's ACL is a **deep clone** of the previous version's
ACL — same owner, same reader / writer / manager lists, same
permission type. After creation the per-version ACL evolves
independently from the parent's (per ADR-0025's "one ACL per
version" scope decision). When no previous version exists (which
shouldn't happen post-V35 backfill but the path is defensive), a
fresh Private-typed ACL is minted with the caller as owner.

### List versions — `GET /v2/{kind}/{appId}/versions`

Every version of the parent that the caller has at least **Reader**
on per the version's ACL.

**Returns.** 200 with `EntityVersionList` shape ordered by
`versionOrdinal DESC` (newest-first):

```json
{
  "versions": [
    { "versionLabel": "1.0.0-rc.1", "versionOrdinal": 2, ... },
    { "versionLabel": "v1",         "versionOrdinal": 1, ... }
  ]
}
```

Versions the caller can't read are filtered out — the list is
**not** a way to enumerate hidden versions.

### Get a single version — `GET /v2/{kind}/{appId}/versions/{label}`

Fetch one version by label.

**Failure modes:**

| Status | type | When |
|---|---|---|
| 401 | (no body) | Caller not authenticated |
| 403 | (no body) | Caller lacks Reader on this version's ACL |
| 404 | `versions.not-found` | No version with that label on this parent |
| 404 | `versions.kind.unsupported` | `{kind}` segment not supported |

### Update per-version ACL — `PATCH /v2/{kind}/{appId}/versions/{label}/permissions`

Flip the per-version ACL. Body shape mirrors the legacy
`PermissionsIO` (the parent-entity surface) so the wire shape is
familiar:

```
PATCH /v2/collections/01HF.../versions/v2/permissions
Content-Type: application/json

{
  "owner": "alice",
  "permissionType": "Private",
  "reader": ["bob", "carol"],
  "writer": [],
  "manager": ["alice"],
  "readerGroupIds": [],
  "writerGroupIds": []
}
```

**Auth.** Caller must hold **Manager** on the **version's** ACL
(not the parent's). Owner transfer (changing the `owner` field)
requires the caller to be the current owner.

**Returns.** 200 with the updated `Permissions` shape.

**Failure modes:**

| Status | type | When |
|---|---|---|
| 403 | (no body) | Caller lacks Manager on this version's ACL — or lacks Owner when transferring ownership |
| 404 | `versions.not-found` | No version with that label on this parent |

PROV1a captures every PATCH automatically as an `:Activity` row
(`targetKind=EntityVersion`-flavoured).

### Delete a version — `DELETE /v2/{kind}/{appId}/versions/{label}`

Remove a version + its `:Permissions` node + the
`HAS_ENTITY_VERSION` edge in one round-trip.

**Auth.** Caller must hold **Manager** on the **version's** ACL.

**Refuses** to delete the only remaining version of a parent — an
entity always has at least one version per the append-only
convention.

**Returns.** 204 (no body) on success.

**Failure modes:**

| Status | type | When |
|---|---|---|
| 403 | (no body) | Caller lacks Manager on the version's ACL |
| 404 | `versions.not-found` | No version with that label |
| 409 | `versions.cannot-delete-only` | Last remaining version — refuse |

## Migration runbook (V35)

The V35 migration is the operator-visible piece of ENT1a's first
post-upgrade restart. It does two things:

1. Applies two constraints on the new `:EntityVersion` label —
   `appId IS UNIQUE` and `(parentEntityAppId, versionLabel) IS UNIQUE`.
2. Backfills a `v1 :EntityVersion` for every existing Collection +
   DataObject (`MATCH … WHERE NOT (parent)-[:HAS_ENTITY_VERSION]->() CREATE …`),
   cloning the parent's `:Permissions` graph into a fresh per-version
   `:Permissions` node so the parent's future ACL changes don't bleed
   into the v1 ACL.

**Idempotency.** Every `CREATE` statement is guarded by
`WHERE NOT EXISTS` on the target edge; constraints use
`IF NOT EXISTS`. Re-running the migration after a partial failure
picks up where it left off.

**Transaction mode.** `PER_STATEMENT` — every `;`-terminated
statement is its own transaction. The migration is split into
small statements so a single-statement failure doesn't roll back
the entire backfill.

**Verify post-migration:**

```sh
cypher-shell> MATCH (c:Collection) WHERE c.appId IS NOT NULL AND NOT (c)-[:HAS_ENTITY_VERSION]->() RETURN count(c);
# → must return 0

cypher-shell> MATCH (d:DataObject) WHERE d.appId IS NOT NULL AND NOT (d)-[:HAS_ENTITY_VERSION]->() RETURN count(d);
# → must return 0

cypher-shell> MATCH (v:EntityVersion) RETURN count(v);
# → roughly (count(Collection) + count(DataObject))
```

**Rollback** (rare):

```sh
cypher-shell> MATCH (v:EntityVersion)<-[r:HAS_ENTITY_VERSION]-()
              OPTIONAL MATCH (v)-[:has_permissions]->(vp:Permissions)
              DETACH DELETE vp, v;

cypher-shell> DROP CONSTRAINT IF EXISTS appId_unique_EntityVersion;
cypher-shell> DROP CONSTRAINT IF EXISTS parentAppId_label_unique_EntityVersion;
```

Downgrading the backend image to a pre-ENT1a build is otherwise
transparent — the legacy `/shepard/api/...` surface is untouched.

## Auditing version changes

PROV1a's `ProvenanceCaptureFilter` captures every mutation on
`/v2/{kind}/{appId}/versions/...` as an `:Activity` row. To audit
the version history of a specific entity:

```sh
cypher-shell> MATCH (a:Activity)
              WHERE a.path =~ '/v2/(collections|data-objects)/01HF.*?/versions.*'
              RETURN a.actor, a.method, a.path, a.timestamp
              ORDER BY a.timestamp DESC LIMIT 20;
```

## Where this fits in the ENT1 chain

ENT1a is the first slice. The follow-ups:

- **ENT1b** — File-payload Copy-on-Write per version. Gated on
  FS1a's `FileStorage` SPI; subscribes to the
  `VersionCreatedEvent` CDI seam ENT1a ships.
- **ENT1c** — Publish hookup. `POST /publish` implicitly creates
  the next `:EntityVersion` and KIP1h's `Publication.versionNumber`
  becomes the denormalised read-side of `EntityVersion.versionOrdinal`.
- **ENT1d** — Vue selector dropdown on Collection / DataObject
  detail panes.
- **ENT1e** — Cross-cardinality expansion to Bundle / File /
  Reference.

See `aidocs/16` ENT1 / ENT1a–ENT1e for the live backlog state.
