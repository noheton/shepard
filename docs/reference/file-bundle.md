---
layout: default
title: File bundle (reference)
permalink: /reference/file-bundle/
audience: user
---
> **Looking for a single-file primitive?** With FR1b shipped, the
> singleton `FileReference` is back as a distinct primitive — see
> [File reference (singleton)](/reference/file-reference/) for the
> single-file casual workflow. This page describes the multi-file
> bundle.

# File bundle reference

A **`FileBundleReference`** is the payload kind used when a researcher
wants to attach files to a [DataObject](/reference/data-object/). Per
the FR1a design (`aidocs/53 §1.3`), a bundle is a **bag of files**,
optionally organised into one or more **`FileGroup`** sub-Reference
sub-nodes for "these N files belong to one logical sub-run"
structure.

> Naming history: this primitive used to be called `FileReference`. The
> upstream API surface keeps the legacy name (so existing clients
> never break — see [API version policy](/architecture/#api-version-policy)),
> but the internal Java class and the new `/v2/bundles/...` REST shelf
> read with the more accurate `FileBundleReference` name. The class is
> persisted with both `:FileReference` and `:FileBundleReference`
> labels in Neo4j, so external Cypher (provenance, export,
> version-copy) keeps working unchanged.

## Shape

```
DataObject  ─(:has_reference)─►  FileBundleReference
                                       │ (:HAS_GROUP)
                                       ▼
                                  FileGroup  ─(:has_payload)─►  ShepardFile
                                                                       │
                                  FileBundleReference ─(:has_payload)──┘
                                  (compatibility shadow for the
                                   upstream API read path)
```

- One bundle has **one** [`FileContainer`](/reference/file-container/)
  (Mongo GridFS namespace).
- One bundle has **≥ 1** `FileGroup`s — newly-created bundles get a
  default group named `"default"` with index 0; the V21 migration
  backfills the same default group on every existing bundle.
- One group has **0..N** `ShepardFile`s.
- Files are also attached directly to the bundle as a compatibility
  shadow so the upstream `/shepard/api/.../fileReferences/...` REST
  surface keeps returning the flat files list with zero wire change.

## REST surface

Two parallel shelves:

| Surface | Path | When to use it |
|---|---|---|
| **Upstream-frozen** | `/shepard/api/collections/{c}/dataObjects/{d}/fileReferences/...` | Backward-compatible with upstream shepard 5.2.0. Reads return the flat files list as today. Writes accept the flat shape; the backend places new files under the bundle's default group. **Zero wire change.** |
| **`/v2/` shelf** | `/v2/bundles/{appId}` | New `FileBundleReference` GET — returns the bundle with `groups: [...]` populated. |
| **`/v2/` shelf** | `/v2/bundles/{appId}/groups` | List + create groups. |
| **`/v2/` shelf** | `/v2/bundles/{appId}/groups/{groupAppId}` | Group GET / PATCH / DELETE. |
| **`/v2/` shelf** | `/v2/bundles/{appId}/groups/{groupAppId}/files` | Upload one file into the named group. |

The `/v2/` shelf was introduced in FR1a (`aidocs/53 §1.6`).

## File group fields

| Field | Type | Notes |
|---|---|---|
| `appId` | UUID v7 | Application-level identifier (per L2a). |
| `name` | string | Display name; need not be unique within the bundle. |
| `description` | string (nullable) | Free-form description. |
| `index` | int | 0-based ordering index. New groups default to `max(existing) + 1`. |
| `startedAt` | timestamp (nullable) | Wall-clock start of the sub-run. |
| `endedAt` | timestamp (nullable) | Wall-clock end. |
| `attributes` | `map<string, string>` | Free-form key/value metadata. |

## Casual workflow

A camera-frame dump from a lab capture rig produces hundreds of
files. Pre-FR1a, those files were all in one flat list under one
`FileReference` — no "sub-run 1 / 2 / 3" navigation.

With FR1a:

1. Create a `FileBundleReference` for the capture run (the upstream
   POST works; or use `/v2/bundles` + groups).
2. Create one `FileGroup` per sub-run via
   `POST /v2/bundles/{appId}/groups` with `{name: "sub-run 1",
   startedAt: ..., endedAt: ...}`.
3. Upload each file into the appropriate group via
   `POST /v2/bundles/{appId}/groups/{groupAppId}/files`.

The default group named `"default"` (index 0) is always present; you
can put files there if you don't want sub-run structure.

## Permissions

FileGroups inherit permissions from the parent `FileBundleReference`
(which inherits from the parent DataObject). No new permission
surface — the group is navigation + metadata only, not a security
boundary (per `aidocs/53 §1.3 (c)`).

## Migration

The V21 Cypher migration (`backend/src/main/resources/neo4j/migrations/
V21__Rename_FileReference_to_FileBundleReference_and_introduce_FileGroup.cypher`)
is **idempotent + fail-fast**: it adds the new `:FileBundleReference`
label to every `:FileReference`, attaches a default `:FileGroup`, and
re-parents files under the group. Re-running is a no-op. The legacy
`:FileReference` label and the bundle's direct `:has_payload` edges
are preserved.

A rollback file (`V21_R__Split_FileBundleReference_into_FileReference.cypher`)
ships alongside; it refuses to run if any user-created groups exist
(would silently lose data).

## What's coming next

- **FR1b** will introduce a true `FileReference` singleton primitive
  for the single-file case, and a parallel `/v2/files/...` shelf. See
  `aidocs/53 §1.8`.
- **FR1c** wires snapshot + payload-versioning hooks for the new types.
- **FR1d** updates the RO-Crate export — `FileBundleReference`
  exports as a Dataset with FileGroup sub-Datasets and File leaves.

## See also

- [Upstream upgrade path](https://github.com/shepard-dlr/shepard/blob/main/aidocs/34-upstream-upgrade-path.md) — admin-facing ledger.
- [Fork-vs-upstream feature matrix](https://github.com/shepard-dlr/shepard/blob/main/aidocs/44-fork-vs-upstream-feature-matrix.md) — contributor-facing status.
- [FR1 design doc](https://github.com/shepard-dlr/shepard/blob/main/aidocs/53-file-reference-rename-video-content.md) — full FR1 design.
