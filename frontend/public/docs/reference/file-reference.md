---
layout: default
title: File reference (singleton)
permalink: /reference/file-reference/
---

# File reference (singleton)

A **`FileReference`** (FR1b — `aidocs/53 §1.8`) is the payload kind
used when a researcher wants to attach **one file** to a
[DataObject](/reference/data-object/). One PDF protocol. One CSV
result. One photo of a test rig.

It is the singleton sibling of
[**`FileBundleReference`**](/reference/file-bundle/) (the
multi-file bag). The two coexist as separate primitives so the
single-file casual workflow doesn't pay bundle overhead (no
`FileContainer` doc, no default `FileGroup` node, no
two-extra-levels in every response).

## When to use which

| Workflow | Primitive | Why |
|---|---|---|
| Drop a PDF protocol on a DataObject. | `FileReference` (this page) | One file, one Reference. Casual users describe their data in the singular — the primitive matches. |
| Drop 1 000 camera-capture frames on a DataObject. | [`FileBundleReference`](/reference/file-bundle/) | Multi-file with sub-`FileGroup` structure for "frames 0–249 are sub-run 1, 250–499 are sub-run 2, ...". |
| Drop a single MP4 video. | `FileReference` (today) or `VideoStreamReference` (when VID1 ships) | Singleton until video lands as its own kind. |

The casual upload UI (FR1b-ui, queued) dispatches by **drag count**:
- 1 file → `POST /v2/files` (creates a singleton).
- ≥ 2 files → `POST /v2/bundles` (creates a bundle with one default group).

A "wrap as bundle" toggle lets a user pre-declare bundle intent for
the first single file. Conversion direction is **singleton →
bundle, never bundle → singleton** (the reverse would orphan groups,
attributes, and dependent annotations).

## Shape

```
DataObject  ─(:has_reference)─►  FileReference  ─(:has_payload)─►  ShepardFile
            (BasicReference)     (:SingletonFileReference)         (Mongo GridFS)
```

- Exactly **one** `ShepardFile` per singleton — enforced at the
  service layer (the multipart upload accepts a single `file` part).
- Bytes live in a **shared** `_shepard_files` Mongo collection
  (one collection, GridFS-backed, used by every singleton in the
  instance). This is the structural difference from
  `FileBundleReference`'s per-Reference Mongo collection layout:
  10 000 PDFs upload as 10 000 docs in one collection, not 10 000
  empty Mongo collections.
- The singleton's Neo4j node carries the
  `:SingletonFileReference` label exclusively — it does **not**
  carry the legacy `:FileReference` label. That keeps the FR1a
  bundle DAO (which queries `:FileReference`) and the FR1b
  singleton DAO (which queries `:SingletonFileReference`) on
  disjoint row sets without ambiguity. Both labels descend from
  `:BasicReference`, so the cross-Reference traversal
  `(DataObject)-[:has_reference]->(Reference)` sees both.

## REST surface

All under the `/v2/` shelf — singletons are a fork-native primitive
and do not surface on the upstream `/shepard/api/...` paths
(writes there always create bundles for byte-for-byte upstream
compatibility — see the [API version policy](/architecture/#api-version-policy)).

| Method | Path | Behaviour |
|---|---|---|
| `POST` | `/v2/files?parentDataObjectAppId={do}[&name={n}]` | Upload one file. Multipart body with a single `file` part. If `name` is omitted, the uploaded filename is used as the Reference's name. Returns `201` + the singleton's JSON. |
| `GET` | `/v2/files/{appId}` | Singleton metadata: `{ appId, name, type: "FileReference", file: { oid, filename, fileSize, md5, createdAt } }`. |
| `GET` | `/v2/files/{appId}/content` | The byte stream. Supports HTTP range requests (`Range: bytes=START-END` or `bytes=START-`). Returns `200` with full body when no `Range` header is present; `206 Partial Content` when a satisfiable range is requested; `416 Requested Range Not Satisfiable` for an out-of-bounds range. Multi-range / suffix-range are not supported in FR1b (refused with 416). |
| `PATCH` | `/v2/files/{appId}` | RFC 7396 merge-patch on the `name` field. Other fields are immutable in FR1b. Body: `{"name": "new name"}`. |
| `DELETE` | `/v2/files/{appId}` | Hard-delete the Reference and its underlying bytes (Neo4j node soft-deleted; Mongo doc + GridFS blob removed). |

Permissions: every endpoint resolves the parent DataObject from the
singleton and asks the same `PermissionsService` the upstream API
uses. 401 unauthenticated, 403 on permission denied, 404 on missing
singleton.

## Migration from existing singleton-shaped bundles (V23, opt-in)

If you upgraded to a version with FR1a (rename) **before** FR1b,
every singleton-shaped row (a `FileBundleReference` with exactly one
`ShepardFile` reachable through its single default `FileGroup`) is a
candidate for conversion to the new singleton shape.

The V23 migration handles the carve-out — but it's **opt-in** because
moving Mongo metadata docs between collections is the kind of
operation an admin wants to schedule.

To run it:

```properties
# application.properties
shepard.migration.split-singletons.enabled=true
```

Then restart shepard. The migration logs progress every 1 000 rows.
For each candidate it:

1. Moves the Mongo metadata doc from the per-bundle collection into
   the shared `_shepard_files` namespace (GridFS chunks stay in
   place — they're not scoped to a per-bundle collection).
2. Drops the `:FileBundleReference` and `:FileReference` labels; adds
   `:SingletonFileReference`.
3. Drops the synthetic default `:FileGroup` node + its outgoing
   edges. The bundle's compatibility-shadow `(bundle)-[:has_payload]->(file)`
   edge remains as the singleton's `(singleton)-[:has_payload]->(file)`.
4. Drops the now-empty per-bundle Mongo collection.

The migration stamps `legacyV23Singleton: true` and
`legacyV23BundleMongoId: <oid>` on every converted row so the V23_R
rollback can refuse to run if any V23-minted singleton has since
been patched.

**Verify post-migration:**

```cypher
MATCH (s:SingletonFileReference)
RETURN count(s) AS singletons,
       sum(CASE WHEN (s)-[:has_payload]->(:ShepardFile) THEN 1 ELSE 0 END) AS singletons_with_file;
```

Both numbers should match.

**Rollback** (`V23_R__Rejoin_singletons_into_FileBundleReferences.cypher`)
re-creates the synthetic default `:FileGroup`, restores the
`:FileBundleReference` + `:FileReference` labels, and re-points the
graph edges to the bundle shape. It refuses if any user-created
singleton has been added via `POST /v2/files` since V23 ran (would
silently lose user state), or if any V23-minted singleton has been
patched (would silently lose that edit). See the file's top comment
for the operator runbook + the manual `mongosh` step that moves
metadata docs back into per-bundle collections.

## Why two types instead of one

`aidocs/53 §1.8.2` is the full discussion. Short version: a
researcher uploading one PDF doesn't think "I am creating a bundle of
one file." The bundle-only design (FR1a as it shipped) forces that
mental model — every PDF becomes a bundle-with-a-default-group-of-one.
Splitting the primitive lets the casual UI dispatch by drag count
without ceremony.

## Cross-references

- [File bundle (reference)](/reference/file-bundle/) — the
  multi-file sibling.
- [DataObject](/reference/data-object/) — the parent.
- [Upload data](/help/upload-data/) — the casual-task page that
  drives the drag-count dispatch.
- `aidocs/53 §1.8` — the FR1b design doc.
- `aidocs/34` — the upgrade-path tracker row for FR1b
  (`shepard.migration.split-singletons.enabled`).
